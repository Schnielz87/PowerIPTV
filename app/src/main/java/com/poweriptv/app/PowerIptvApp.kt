package com.poweriptv.app

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import com.poweriptv.app.ai.AiRecommender
import com.poweriptv.app.cast.CastManager
import com.poweriptv.app.data.CachedSource
import com.poweriptv.app.data.ContentItem
import com.poweriptv.app.data.PlayerEngine
import org.videolan.libvlc.LibVLC
import com.poweriptv.app.data.ContentType
import com.poweriptv.app.ui.components.ContentFilter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import com.poweriptv.app.data.ContentSource
import com.poweriptv.app.data.EpgRepository
import com.poweriptv.app.data.FavoritesRepository
import com.poweriptv.app.data.HistoryRepository
import com.poweriptv.app.data.M3uSource
import com.poweriptv.app.data.Profile
import com.poweriptv.app.data.ProfileRepository
import com.poweriptv.app.data.ProfileType
import com.poweriptv.app.data.SecureStore
import com.poweriptv.app.data.SettingsRepository
import com.poweriptv.app.data.XtreamSource
import com.poweriptv.app.download.DownloadRepository
import com.poweriptv.app.parental.ParentalControl
import com.poweriptv.app.record.RecordingRepository
import com.poweriptv.app.vpn.VpnGuardInterceptor
import com.poweriptv.app.vpn.VpnManager
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

class PowerIptvApp : Application(), ImageLoaderFactory {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.cast.init() // Google Cast (nur mit Google-Play-Diensten, nicht auf TV-Geraeten)
    }

    override fun newImageLoader(): ImageLoader = ImageLoader.Builder(this)
        .okHttpClient(container.http)
        .crossfade(true)
        .build()
}

/** Einfache manuelle Dependency Injection. */
class AppContainer(private val app: Application) {
    val json = Json { ignoreUnknownKeys = true; isLenient = true }
    val secure = SecureStore(app)
    val settings = SettingsRepository(app)
    val profiles = ProfileRepository(app, secure, json)
    val favorites = FavoritesRepository(app, json)
    val vpn = VpnManager(app, settings, secure)

    val http: OkHttpClient = OkHttpClient.Builder()
        .addInterceptor(VpnGuardInterceptor({ vpn }, settings))
        .addInterceptor { chain ->
            chain.proceed(chain.request().newBuilder().header("User-Agent", settings.userAgent.value).build())
        }
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    val downloads = DownloadRepository(app, json, { http }) { downloadConnections() }
    val epg = EpgRepository({ java.io.File(app.cacheDir, "epg") }, { http }) { android.util.Xml.newPullParser() }
    val parental = ParentalControl(app)
    val recordings = RecordingRepository(app, json)
    val reminders = com.poweriptv.app.reminder.ReminderRepository(app, json)
    /** Sleep-Timer: Zeitpunkt, an dem die Wiedergabe endet (0 = aus). */
    @Volatile var sleepUntil = 0L
    val history = HistoryRepository(app, json)
    val categoryPrefs = com.poweriptv.app.data.CategoryPrefs(app)
    val backup by lazy { com.poweriptv.app.data.BackupManager(app, secure, json) }
    val resume = com.poweriptv.app.data.ResumeRepository(app)
    val cast = CastManager(app)
    val ai = AiRecommender({ http }, secure, settings, parental, json)
    val ageRatings = com.poweriptv.app.data.AgeRatingRepository({ http }, secure, json)

    /** Aktuell ausgewaehlte Quelle (Profil). */
    var source: ContentSource? = null
        private set

    /** Filter/Sortierung je Bereich (bleiben beim Kategoriewechsel und Zurueckkehren erhalten). */
    val browseFilters = mutableMapOf<ContentType, ContentFilter>()

    /** Zwischenablage fuer Detail-Bildschirme. */
    var selectedItem: ContentItem? = null

    /** Wiedergabeliste fuer den Player (Kanal hoch/runter). */
    var playQueue: List<PlayEntry> = emptyList()
    var playIndex: Int = 0

    /** Offene Frage "Weiterschauen oder von vorne?" (wird in MainActivity als Dialog gezeigt). */
    val resumePrompt = kotlinx.coroutines.flow.MutableStateFlow<ResumePrompt?>(null)
    /** Gesperrter Inhalt: nach richtiger PIN wird diese Aktion ausgefuehrt (Dialog in MainActivity). */
    val pinGate = kotlinx.coroutines.flow.MutableStateFlow<(() -> Unit)?>(null)

    fun createSource(profile: Profile): ContentSource = when (profile.type) {
        ProfileType.XTREAM -> XtreamSource(profile, http, json) { settings.liveFormatEnum().ext }
        ProfileType.M3U_URL, ProfileType.M3U_FILE -> M3uSource(profile, http)
    }

    /**
     * Parallele Download-Verbindungen: fest eingestellt, oder automatisch so viele,
     * wie der Account erlaubt (Xtream max_connections, max. 4).
     */
    private suspend fun downloadConnections(): Int {
        val setting = settings.downloadConnections.value
        if (setting > 0) return setting
        val max = runCatching { source?.accountInfo()?.maxConnections?.toIntOrNull() }.getOrNull()
        return (max ?: 1).coerceIn(1, DownloadRepository.MAX_CONNECTIONS)
    }

    /**
     * VLC-Engine: einmal laden und fuer jede Wiedergabe wiederverwenden
     * (der Neustart pro Film kostete spuerbar Zeit).
     */
    private var vlcInstance: LibVLC? = null
    private var vlcMode: String? = null

    /** Schwaches Geraet (TV-Stick, wenig Speicher/Kerne)? Dann sparsamer VLC-Modus. */
    private fun lowPowerDevice(): Boolean {
        val am = app.getSystemService(android.content.Context.ACTIVITY_SERVICE) as android.app.ActivityManager
        val mem = android.app.ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }
        return am.isLowRamDevice || mem.totalMem < 3L * 1024 * 1024 * 1024 ||
            Runtime.getRuntime().availableProcessors() <= 4 || com.poweriptv.app.util.DeviceInfo.isTv(app)
    }

    /** Tatsaechlich verwendeter VLC-Modus (AUTO aufgeloest). */
    fun vlcFastMode(): Boolean = when (settings.vlcPerformance.value) {
        "FAST" -> true
        "QUALITY" -> false
        else -> lowPowerDevice()
    }

    val vlc: LibVLC
        @Synchronized get() {
            val fast = vlcFastMode()
            val mode = if (fast) "FAST" else "QUALITY"
            vlcInstance?.takeIf { vlcMode == mode }?.let { return it }
            val opts = arrayListOf(
                "--http-reconnect",
                "--no-stats",
                "--avcodec-threads=0",
                "--audio-time-stretch",
                // Halbbild-Sender (z.B. RTL, ProSieben in SD/1080i): nur bei Bedarf entflechten
                "--deinterlace=-1",
            )
            if (fast) {
                // Sparsam fuer TV-Sticks: einfache Entflechtung, schnellere Dekodierung, 16-Bit-Farben
                opts += listOf(
                    "--deinterlace-mode=blend",
                    "--avcodec-skiploopfilter=4",
                    "--avcodec-fast",
                    "--avcodec-hurry-up",
                    "--android-display-chroma=RV16",
                )
            } else {
                opts += listOf("--deinterlace-mode=yadif", "--avcodec-skiploopfilter=1")
            }
            return LibVLC(app, opts).also { vlcInstance = it; vlcMode = mode }
        }

    /** VLC im Hintergrund vorladen, damit der erste Start schnell ist. */
    fun prewarmVlc() {
        if (settings.playerEngineEnum() == PlayerEngine.EXO) return
        scope.launch(Dispatchers.Default) { runCatching { vlc } }
    }

    /** App-weiter Scope (laeuft unabhaengig vom aktuellen Bildschirm). */
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val _refreshing = MutableStateFlow(false)
    /** Wird die Playlist gerade aktualisiert? */
    val refreshing: StateFlow<Boolean> = _refreshing
    private val _refreshError = MutableStateFlow<String?>(null)
    val refreshError: StateFlow<String?> = _refreshError

    /** Playlist (und TV-Guide) vom Server neu laden. Ohne [force] nur, wenn aelter als 24 h. */
    fun refreshPlaylist(force: Boolean) {
        val src = source as? CachedSource ?: return
        if (_refreshing.value || (!force && !src.isStale())) return
        _refreshing.value = true
        _refreshError.value = null
        scope.launch {
            try {
                src.refreshAll()
                epg.ensureLoaded(src, force = true)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _refreshError.value = e.message ?: e.javaClass.simpleName
            } finally {
                _refreshing.value = false
            }
        }
    }

    fun activate(profile: Profile?) {
        source = profile?.let { CachedSource(createSource(it), app, json) }
        epg.clear()
        settings.setLastProfileId(profile?.id)
        favorites.bind(profile?.id)
        history.bind(profile?.id)
        maxConnections = null
        source?.let { src -> scope.launch { maxConnections = runCatching { src.accountInfo()?.maxConnections?.toIntOrNull() }.getOrNull() } }
        // Kategorien fuer die Kindersicherung kennen (Erwachsenen-Kategorien auch bei Einzeltiteln erkennen)
        source?.let { src ->
            scope.launch(Dispatchers.IO) {
                listOf(ContentType.LIVE, ContentType.MOVIE, ContentType.SERIES).forEach { t ->
                    runCatching { parental.register(src.profile.id, t, src.categories(t)) }
                }
            }
        }
    }

    /** Erlaubte gleichzeitige Verbindungen des Accounts (Xtream max_connections; null = unbekannt). */
    @Volatile var maxConnections: Int? = null

    init {
        activate(profiles.get(settings.lastProfileId.value))
    }
}

data class ResumePrompt(val entries: List<PlayEntry>, val index: Int, val positionMs: Long)

data class PlayEntry(val title: String, val url: String, val item: ContentItem? = null, val live: Boolean)

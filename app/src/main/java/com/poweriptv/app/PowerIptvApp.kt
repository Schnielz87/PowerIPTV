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
        // Portiva Link: sichtbare Activities zaehlen (Wiedergabe von anderen Geraeten nur bei geoeffneter App)
        registerActivityLifecycleCallbacks(object : android.app.Application.ActivityLifecycleCallbacks {
            private var started = 0
            override fun onActivityStarted(a: android.app.Activity) { started++; container.appVisible = true }
            override fun onActivityStopped(a: android.app.Activity) { started = (started - 1).coerceAtLeast(0); container.appVisible = started > 0 }
            override fun onActivityCreated(a: android.app.Activity, b: android.os.Bundle?) {}
            override fun onActivityResumed(a: android.app.Activity) {}
            override fun onActivityPaused(a: android.app.Activity) {}
            override fun onActivitySaveInstanceState(a: android.app.Activity, b: android.os.Bundle) {}
            override fun onActivityDestroyed(a: android.app.Activity) {}
        })
        container.startLink()
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

    val downloads = DownloadRepository(
        java.io.File(app.filesDir, "downloads.json"),
        app.getExternalFilesDir(android.os.Environment.DIRECTORY_MOVIES) ?: java.io.File(app.filesDir, "downloads"),
        json, { http }, { downloadConnections() },
    ) {
        androidx.core.content.ContextCompat.startForegroundService(app, android.content.Intent(app, com.poweriptv.app.download.DownloadService::class.java))
    }
    val epg = EpgRepository({ java.io.File(app.cacheDir, "epg") }, { http }) { android.util.Xml.newPullParser() }
    val parental = ParentalControl(com.poweriptv.app.data.SharedPrefsStore(app.getSharedPreferences("parental", android.content.Context.MODE_PRIVATE)))
    val recordings = RecordingRepository(app, json)
    val reminders = com.poweriptv.app.reminder.ReminderRepository(app, json)
    /** Sleep-Timer: Zeitpunkt, an dem die Wiedergabe endet (0 = aus). */
    @Volatile var sleepUntil = 0L
    val history = HistoryRepository(app, json)
    val categoryPrefs = com.poweriptv.app.data.CategoryPrefs(app)
    val backup by lazy { com.poweriptv.app.data.BackupManager(app, secure, json) }
    val resume = com.poweriptv.app.data.ResumeRepository(app)
    val cast = CastManager(app)
    val ai = AiRecommender({ http }, secure, { settings.aiModel.value }, { settings.aiBaseUrl.value }, { p, t, c -> parental.lockedIds(p, t, c) }, json)
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

    // ---------- Portiva Link (Zugang uebertragen, Wiedergabe weitergeben) ----------
    /** Gerade angezeigter Empfangs-Code ("Vom anderen Geraet empfangen"); null = nichts erwartet. */
    val linkPairCode = MutableStateFlow<String?>(null)
    /** Zuletzt empfangener Zugang (die Oberflaeche zeigt dann "uebernommen"). */
    val linkReceived = MutableStateFlow<Profile?>(null)
    @Volatile var appVisible = false
    val isTvDevice by lazy { com.poweriptv.app.util.DeviceInfo.isTv(app) }
    private var multicastLock: android.net.wifi.WifiManager.MulticastLock? = null

    val link = com.poweriptv.app.link.LinkService(
        deviceName = { deviceName() },
        platform = if (com.poweriptv.app.util.DeviceInfo.isTv(app)) "android-tv" else "android",
        onPlay = { receivePlay(it) },
        onPair = { receivePair(it) },
    )

    fun deviceName(): String {
        val n = runCatching { android.provider.Settings.Global.getString(app.contentResolver, "device_name") }.getOrNull()
        return n?.takeIf { it.isNotBlank() } ?: "${android.os.Build.MANUFACTURER.replaceFirstChar { it.uppercase() }} ${android.os.Build.MODEL}"
    }

    fun startLink() {
        // Manche Geraete verwerfen UDP-Rundrufe ohne diese Sperre (nur fuer die Geraete-Suche)
        runCatching {
            val wifi = app.applicationContext.getSystemService(android.content.Context.WIFI_SERVICE) as android.net.wifi.WifiManager
            multicastLock = wifi.createMulticastLock("portiva-link").apply { setReferenceCounted(false); acquire() }
        }
        link.start()
    }

    /** Zugang aus einem QR-Code / von einem anderen Geraet speichern (gleicher Zugang wird aktualisiert). */
    fun importAccount(a: com.poweriptv.app.link.LinkAccount): Profile {
        val existing = profiles.profiles.value.firstOrNull {
            it.type.name == a.type && it.serverUrl.trimEnd('/') == a.serverUrl.trimEnd('/') && it.username == a.username && it.m3uUrl == a.m3uUrl
        }
        val p = com.poweriptv.app.link.LinkCodes.toProfile(a, existing?.id ?: java.util.UUID.randomUUID().toString())
        profiles.save(p)
        return p
    }

    private fun receivePair(p: com.poweriptv.app.link.LinkPair): Boolean {
        val code = linkPairCode.value ?: return false
        if (p.code != code) return false
        linkPairCode.value = null
        linkReceived.value = importAccount(p.account)
        return true
    }

    private fun receivePlay(p: com.poweriptv.app.link.LinkPlay): Boolean {
        // Ab Android 10 darf eine App im Hintergrund nichts oeffnen -> Portiva muss auf dem Geraet offen sein
        if (!appVisible && android.os.Build.VERSION.SDK_INT >= 29) return false
        val done = java.util.concurrent.CountDownLatch(1)
        var ok = false
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            ok = runCatching {
                if (!p.live && p.positionMs > 0 && p.durationMs > 0) resume.save(p.url, p.positionMs, p.durationMs)
                com.poweriptv.app.ui.components.startPlayback(app, this, listOf(PlayEntry(p.title, p.url, null, p.live)), 0, askResume = false)
                android.widget.Toast.makeText(app, if (p.from.isNotBlank()) "Von „${p.from}“ übernommen" else "Wiedergabe übernommen", android.widget.Toast.LENGTH_SHORT).show()
            }.isSuccess
            done.countDown()
        }
        done.await(5, java.util.concurrent.TimeUnit.SECONDS)
        return ok
    }

    /** Updates direkt von GitHub (alle 24 h pruefen). */
    val updates by lazy { com.poweriptv.app.update.UpdateManager(app, { http }, json, scope) }

    init {
        // Einmalig: alte VLC-Zuordnungen (ganze Kategorien) zuruecksetzen -> Standard-Player zuerst
        app.getSharedPreferences("migrations", android.content.Context.MODE_PRIVATE).let { m ->
            if (!m.getBoolean("vlc_reset_v2", false)) { settings.clearVlcStreams(); m.edit().putBoolean("vlc_reset_v2", true).apply() }
        }
        activate(profiles.get(settings.lastProfileId.value))
        updates.startAutoCheck()
        // Playlist + TV-Guide alle 24 h automatisch aktualisieren – auch wenn die App laenger offen ist
        scope.launch {
            while (true) {
                kotlinx.coroutines.delay(3600_000L)
                refreshPlaylist(force = false)
            }
        }
    }
}

data class ResumePrompt(val entries: List<PlayEntry>, val index: Int, val positionMs: Long)

data class PlayEntry(val title: String, val url: String, val item: ContentItem? = null, val live: Boolean)

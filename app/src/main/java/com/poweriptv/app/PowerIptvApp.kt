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
    val epg = EpgRepository(app) { http }
    val parental = ParentalControl(app)
    val recordings = RecordingRepository(app, json)
    val history = HistoryRepository(app, json)
    val resume = com.poweriptv.app.data.ResumeRepository(app)
    val cast = CastManager(app)
    val ai = AiRecommender({ http }, secure, settings, parental, json)

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
    val vlc: LibVLC by lazy {
        LibVLC(
            app,
            arrayListOf(
                "--http-reconnect",
                "--deinterlace=1",
                "--deinterlace-mode=yadif",
                "--no-stats",
            ),
        )
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
    }

    init {
        activate(profiles.get(settings.lastProfileId.value))
    }
}

data class PlayEntry(val title: String, val url: String, val item: ContentItem? = null, val live: Boolean)

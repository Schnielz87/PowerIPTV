package com.poweriptv.app

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import com.poweriptv.app.data.ContentItem
import com.poweriptv.app.data.ContentSource
import com.poweriptv.app.data.FavoritesRepository
import com.poweriptv.app.data.M3uSource
import com.poweriptv.app.data.Profile
import com.poweriptv.app.data.ProfileRepository
import com.poweriptv.app.data.ProfileType
import com.poweriptv.app.data.SecureStore
import com.poweriptv.app.data.SettingsRepository
import com.poweriptv.app.data.XtreamSource
import com.poweriptv.app.download.DownloadRepository
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
    }

    override fun newImageLoader(): ImageLoader = ImageLoader.Builder(this)
        .okHttpClient(container.http)
        .crossfade(true)
        .build()
}

/** Einfache manuelle Dependency Injection. */
class AppContainer(app: Application) {
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

    val downloads = DownloadRepository(app, json) { http }

    /** Aktuell ausgewaehlte Quelle (Profil). */
    var source: ContentSource? = null
        private set

    /** Zwischenablage fuer Detail-Bildschirme. */
    var selectedItem: ContentItem? = null

    /** Wiedergabeliste fuer den Player (Kanal hoch/runter). */
    var playQueue: List<PlayEntry> = emptyList()
    var playIndex: Int = 0

    fun createSource(profile: Profile): ContentSource = when (profile.type) {
        ProfileType.XTREAM -> XtreamSource(profile, http, json) { settings.liveFormatEnum().ext }
        ProfileType.M3U_URL, ProfileType.M3U_FILE -> M3uSource(profile, http)
    }

    fun activate(profile: Profile?) {
        source = profile?.let { createSource(it) }
        settings.setLastProfileId(profile?.id)
        favorites.bind(profile?.id)
    }

    init {
        activate(profiles.get(settings.lastProfileId.value))
    }
}

data class PlayEntry(val title: String, val url: String, val item: ContentItem? = null, val live: Boolean)

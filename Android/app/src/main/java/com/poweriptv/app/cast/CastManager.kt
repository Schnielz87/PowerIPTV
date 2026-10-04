package com.poweriptv.app.cast

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.mediarouter.media.MediaRouteSelector
import androidx.mediarouter.media.MediaRouter
import com.google.android.gms.cast.CastMediaControlIntent
import com.google.android.gms.cast.MediaInfo
import com.google.android.gms.cast.MediaLoadRequestData
import com.google.android.gms.cast.MediaMetadata
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.cast.framework.CastSession
import com.google.android.gms.cast.framework.SessionManagerListener
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.common.images.WebImage
import android.net.Uri
import com.poweriptv.app.PlayEntry
import com.poweriptv.app.util.DeviceInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class CastDevice(val id: String, val name: String, val description: String?, val videoCapable: Boolean = true)

/**
 * Google Cast (Chromecast / Google TV / "Chromecast built-in").
 * Nicht verfuegbar ohne Google-Play-Dienste (z.B. Fire TV, Huawei) und auf TV-Geraeten selbst.
 */
class CastManager(private val context: Context) {

    private var castContext: CastContext? = null
    private var router: MediaRouter? = null
    private val selector = MediaRouteSelector.Builder()
        .addControlCategory(CastMediaControlIntent.categoryForCast(CastMediaControlIntent.DEFAULT_MEDIA_RECEIVER_APPLICATION_ID))
        .build()

    private val _available = MutableStateFlow(false)
    /** Cast auf diesem Geraet moeglich? */
    val available: StateFlow<Boolean> = _available
    private val _devices = MutableStateFlow<List<CastDevice>>(emptyList())
    val devices: StateFlow<List<CastDevice>> = _devices
    private val _connectedTo = MutableStateFlow<String?>(null)
    /** Name des verbundenen Fernsehers (null = nicht verbunden). */
    val connectedTo: StateFlow<String?> = _connectedTo
    private val _nowCasting = MutableStateFlow<String?>(null)
    /** Titel, der gerade auf dem TV laeuft. */
    val nowCasting: StateFlow<String?> = _nowCasting
    private val _paused = MutableStateFlow(false)
    val paused: StateFlow<Boolean> = _paused

    /** Wird nach dem Verbinden automatisch abgespielt. */
    private var pending: Pair<PlayEntry, String?>? = null

    /** Muss auf dem Main-Thread aufgerufen werden (Application.onCreate). */
    fun init() {
        if (DeviceInfo.isTv(context)) return
        val gms = GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(context)
        if (gms != ConnectionResult.SUCCESS) return
        runCatching {
            @Suppress("DEPRECATION")
            val ctx = CastContext.getSharedInstance(context)
            castContext = ctx
            router = MediaRouter.getInstance(context)
            ctx.sessionManager.addSessionManagerListener(sessionListener, CastSession::class.java)
            ctx.sessionManager.currentCastSession?.let { onConnected(it) }
            _available.value = true
        }
    }

    private val routerCallback = object : MediaRouter.Callback() {
        override fun onRouteAdded(router: MediaRouter, route: MediaRouter.RouteInfo) = refresh()
        override fun onRouteRemoved(router: MediaRouter, route: MediaRouter.RouteInfo) = refresh()
        override fun onRouteChanged(router: MediaRouter, route: MediaRouter.RouteInfo) = refresh()
        override fun onProviderChanged(router: MediaRouter, provider: MediaRouter.ProviderInfo) = refresh()
    }

    private fun refresh() {
        val r = router ?: return
        _devices.value = r.routes
            .filter { !it.isDefault && it.matchesSelector(selector) && it.isEnabled }
            .map { route ->
                // Nur-Audio-Geraete (z.B. AV-Receiver, Lautsprecher mit Chromecast built-in) erkennen
                val dev = runCatching { com.google.android.gms.cast.CastDevice.getFromBundle(route.extras) }.getOrNull()
                val video = dev?.hasCapability(com.google.android.gms.cast.CastDevice.CAPABILITY_VIDEO_OUT) ?: true
                CastDevice(route.id, route.name, route.description, video)
            }
            .sortedByDescending { it.videoCapable }
    }

    /** Geraetesuche starten (solange die Auswahl offen ist). */
    fun startDiscovery() {
        val r = router ?: return
        r.addCallback(
            selector, routerCallback,
            MediaRouter.CALLBACK_FLAG_PERFORM_ACTIVE_SCAN or MediaRouter.CALLBACK_FLAG_REQUEST_DISCOVERY,
        )
        refresh()
        _searching.value = true
        val gen = ++scanGeneration
        Handler(Looper.getMainLooper()).postDelayed({ if (gen == scanGeneration) { _searching.value = false; refresh() } }, 12_000)
    }

    fun stopDiscovery() {
        router?.removeCallback(routerCallback)
        _searching.value = false
    }

    /** Suche neu starten ("Neu suchen"). */
    fun rescan() {
        stopDiscovery()
        startDiscovery()
    }

    private var scanGeneration = 0
    private val _searching = MutableStateFlow(false)
    val searching: StateFlow<Boolean> = _searching

    /** Mit einem Fernseher verbinden; [play] wird danach automatisch dort abgespielt. */
    fun connect(device: CastDevice, play: PlayEntry? = null, poster: String? = null) {
        val r = router ?: return
        pending = play?.let { it to poster }
        r.routes.firstOrNull { it.id == device.id }?.let { r.selectRoute(it) }
    }

    fun disconnect() {
        castContext?.sessionManager?.endCurrentSession(true)
        _nowCasting.value = null
    }

    /** Inhalt auf dem verbundenen Fernseher abspielen. */
    fun cast(entry: PlayEntry, poster: String? = null): Boolean {
        val session = castContext?.sessionManager?.currentCastSession ?: return false
        val client = session.remoteMediaClient ?: return false
        if (entry.url.startsWith("/")) return false // lokale Downloads koennen nicht gecastet werden

        // Live (Xtream .ts) -> HLS, das Chromecast nativ abspielt
        val url = if (entry.live && entry.url.endsWith(".ts")) entry.url.removeSuffix(".ts") + ".m3u8" else entry.url
        val lower = url.lowercase()
        val contentType = when {
            ".m3u8" in lower -> "application/x-mpegURL"
            lower.endsWith(".mp4") || lower.endsWith(".m4v") -> "video/mp4"
            lower.endsWith(".mkv") -> "video/x-matroska"
            lower.endsWith(".webm") -> "video/webm"
            lower.endsWith(".ts") -> "video/mp2t"
            else -> if (entry.live) "application/x-mpegURL" else "video/mp4"
        }
        val meta = MediaMetadata(if (entry.live) MediaMetadata.MEDIA_TYPE_GENERIC else MediaMetadata.MEDIA_TYPE_MOVIE).apply {
            putString(MediaMetadata.KEY_TITLE, entry.title)
            putString(MediaMetadata.KEY_SUBTITLE, "PowerIPTV")
            (poster ?: entry.item?.logo)?.takeIf { it.startsWith("http") }?.let { addImage(WebImage(Uri.parse(it))) }
        }
        val info = MediaInfo.Builder(url)
            .setStreamType(if (entry.live) MediaInfo.STREAM_TYPE_LIVE else MediaInfo.STREAM_TYPE_BUFFERED)
            .setContentType(contentType)
            .setMetadata(meta)
            .build()
        client.load(MediaLoadRequestData.Builder().setMediaInfo(info).setAutoplay(true).build())
        _nowCasting.value = entry.title
        _paused.value = false
        return true
    }

    fun togglePause() {
        val client = castContext?.sessionManager?.currentCastSession?.remoteMediaClient ?: return
        if (client.isPlaying) { client.pause(); _paused.value = true } else { client.play(); _paused.value = false }
    }

    fun seekBy(deltaMs: Long) {
        val client = castContext?.sessionManager?.currentCastSession?.remoteMediaClient ?: return
        @Suppress("DEPRECATION")
        client.seek((client.approximateStreamPosition + deltaMs).coerceAtLeast(0))
    }

    fun stopCasting() {
        castContext?.sessionManager?.currentCastSession?.remoteMediaClient?.stop()
        _nowCasting.value = null
    }

    private fun onConnected(session: CastSession) {
        _connectedTo.value = session.castDevice?.friendlyName ?: "Fernseher"
        pending?.let { (entry, poster) ->
            pending = null
            // kurz warten, bis der Empfaenger bereit ist
            Handler(Looper.getMainLooper()).postDelayed({ cast(entry, poster) }, 800)
        }
    }

    private val sessionListener = object : SessionManagerListener<CastSession> {
        override fun onSessionStarted(session: CastSession, sessionId: String) = onConnected(session)
        override fun onSessionResumed(session: CastSession, wasSuspended: Boolean) = onConnected(session)
        override fun onSessionEnded(session: CastSession, error: Int) { _connectedTo.value = null; _nowCasting.value = null }
        override fun onSessionStartFailed(session: CastSession, error: Int) { _connectedTo.value = null; pending = null }
        override fun onSessionStarting(session: CastSession) {}
        override fun onSessionEnding(session: CastSession) {}
        override fun onSessionResuming(session: CastSession, sessionId: String) {}
        override fun onSessionResumeFailed(session: CastSession, error: Int) {}
        override fun onSessionSuspended(session: CastSession, reason: Int) {}
    }
}

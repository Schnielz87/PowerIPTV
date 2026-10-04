package com.poweriptv.desktop.player

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.poweriptv.desktop.data.NetConfig
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.Image
import org.jetbrains.skia.ImageInfo
import uk.co.caprica.vlcj.player.base.MediaPlayer
import uk.co.caprica.vlcj.player.base.MediaPlayerEventAdapter
import uk.co.caprica.vlcj.player.embedded.EmbeddedMediaPlayer
import uk.co.caprica.vlcj.player.embedded.videosurface.callback.BufferFormat
import uk.co.caprica.vlcj.player.embedded.videosurface.callback.BufferFormatCallbackAdapter
import uk.co.caprica.vlcj.player.embedded.videosurface.callback.RenderCallback
import uk.co.caprica.vlcj.player.embedded.videosurface.callback.format.RV32BufferFormat
import java.nio.ByteBuffer
import javax.swing.SwingUtilities

data class Track(val id: Int, val name: String)

/**
 * Ein VLC-Player, dessen Bilder direkt in die Compose-Oberflaeche gezeichnet werden
 * (dadurch liegen Bedienelemente sauber ueber dem Video).
 */
class PlayerController(
    private val networkCaching: Int,
    private val hardwareDecoding: Boolean,
) {
    private val player: EmbeddedMediaPlayer? = Vlc.factory?.mediaPlayers()?.newEmbeddedMediaPlayer()
    /** Untertitel-Groesse (1.0 = normal) und dunkler Hintergrund – wie Android. */
    var subtitleScale: Float = 1f
    var subtitleBackground: Boolean = false
    /** Ohne Ton (Multi-View: nur das aktive Fenster ist hoerbar). */
    var noAudio: Boolean = false

    private val mediaOptions: Array<String>
        get() = listOfNotNull(
            ":network-caching=$networkCaching",
            ":http-user-agent=${NetConfig.userAgent}",
            if (hardwareDecoding) ":avcodec-hw=any" else ":avcodec-hw=none",
            ":sub-text-scale=${(subtitleScale * 100).toInt()}",
            if (subtitleBackground) ":freetype-background-opacity=170" else null,
            if (subtitleBackground) ":freetype-background-color=0" else null,
            if (noAudio) ":no-audio" else null,
        ).toTypedArray()

    val available: Boolean get() = player != null
    val initError: String? get() = if (player == null) Vlc.error ?: "VLC konnte nicht gestartet werden" else null

    // --- Zustand fuer die Oberflaeche (nur im UI-Thread aendern) ---
    var playing by mutableStateOf(false); private set
    var buffering by mutableStateOf(true); private set
    var ended by mutableStateOf(false); private set
    var error by mutableStateOf<String?>(null); private set
    var time by mutableLongStateOf(0L); private set
    var length by mutableLongStateOf(0L); private set
    var seekable by mutableStateOf(false); private set
    var volume by mutableIntStateOf(80); private set
    var muted by mutableStateOf(false); private set
    var rate by mutableStateOf(1f); private set
    var audioTracks by mutableStateOf<List<Track>>(emptyList()); private set
    var subtitleTracks by mutableStateOf<List<Track>>(emptyList()); private set
    var audioTrack by mutableIntStateOf(-1); private set
    var subtitleTrack by mutableIntStateOf(-1); private set
    var videoWidth by mutableIntStateOf(0); private set
    var videoHeight by mutableIntStateOf(0); private set
    /** Pixel-Seitenverhaeltnis (anamorphe SD-Sender), 1 = quadratische Pixel. */
    var pixelAspect by mutableStateOf(1f); private set
    /** Zaehlt hoch, sobald ein neues Bild vorliegt. */
    var frameCounter by mutableLongStateOf(0L); private set

    /** Wird aufgerufen, wenn ein Titel zu Ende ist (z.B. naechste Folge). */
    var onFinished: (() -> Unit)? = null

    // --- Bildpuffer: VLC-Thread schreibt, UI-Thread liest ---
    private val lock = Any()
    private var pending: ByteArray? = null
    private var pendingW = 0
    private var pendingH = 0
    private var hasNew = false
    private var image: Image? = null

    private val formatCallback = object : BufferFormatCallbackAdapter() {
        override fun getBufferFormat(sourceWidth: Int, sourceHeight: Int): BufferFormat {
            synchronized(lock) {
                pendingW = sourceWidth; pendingH = sourceHeight
                pending = ByteArray(sourceWidth * sourceHeight * 4)
            }
            ui { videoWidth = sourceWidth; videoHeight = sourceHeight; refreshAspect() }
            return RV32BufferFormat(sourceWidth, sourceHeight)
        }
    }

    private val renderCallback = RenderCallback { _: MediaPlayer, buffers: Array<ByteBuffer>, _: BufferFormat ->
        synchronized(lock) {
            val target = pending ?: return@RenderCallback
            val src = buffers[0]
            src.rewind()
            src.get(target, 0, minOf(target.size, src.remaining()))
            src.rewind()
            hasNew = true
        }
    }

    init {
        if (player != null) Vlc.register(this)
        player?.let { p ->
            p.videoSurface().set(Vlc.factory!!.videoSurfaces().newVideoSurface(formatCallback, renderCallback, true))
            p.events().addMediaPlayerEventListener(object : MediaPlayerEventAdapter() {
                override fun opening(mediaPlayer: MediaPlayer) = ui { buffering = true; error = null; ended = false }
                override fun buffering(mediaPlayer: MediaPlayer, newCache: Float) = ui { buffering = newCache < 100f }
                override fun playing(mediaPlayer: MediaPlayer) = ui { playing = true; buffering = false }
                override fun paused(mediaPlayer: MediaPlayer) = ui { playing = false }
                override fun stopped(mediaPlayer: MediaPlayer) = ui { playing = false }
                override fun finished(mediaPlayer: MediaPlayer) = ui { playing = false; ended = true; onFinished?.invoke() }
                override fun error(mediaPlayer: MediaPlayer) = ui {
                    playing = false; buffering = false
                    error = "Stream konnte nicht abgespielt werden"
                }
                override fun timeChanged(mediaPlayer: MediaPlayer, newTime: Long) = ui {
                    if (pendingSeek == null) time = newTime
                    if (buffering && newTime > 0) buffering = false
                }
                override fun lengthChanged(mediaPlayer: MediaPlayer, newLength: Long) = ui { length = newLength }
                override fun seekableChanged(mediaPlayer: MediaPlayer, newSeekable: Int) = ui { seekable = newSeekable != 0 }
                override fun elementaryStreamAdded(mediaPlayer: MediaPlayer, type: uk.co.caprica.vlcj.media.TrackType, id: Int) =
                    mediaPlayer.submit { readTracks(mediaPlayer) }
                override fun elementaryStreamSelected(mediaPlayer: MediaPlayer, type: uk.co.caprica.vlcj.media.TrackType, id: Int) =
                    mediaPlayer.submit { readTracks(mediaPlayer) }
                override fun videoOutput(mediaPlayer: MediaPlayer, newCount: Int) = mediaPlayer.submit { readTracks(mediaPlayer); applyBrightness() }
            })
        }
    }

    private fun readTracks(p: MediaPlayer) {
        val audio = runCatching { p.audio().trackDescriptions().filter { it.id() >= 0 }.map { Track(it.id(), it.description() ?: "Tonspur ${it.id()}") } }.getOrDefault(emptyList())
        val subs = runCatching { p.subpictures().trackDescriptions().filter { it.id() >= 0 }.map { Track(it.id(), it.description() ?: "Untertitel ${it.id()}") } }.getOrDefault(emptyList())
        val a = runCatching { p.audio().track() }.getOrDefault(-1)
        val s = runCatching { p.subpictures().track() }.getOrDefault(-1)
        val sar = runCatching {
            p.media().info().videoTracks().firstOrNull()?.let { v ->
                if (v.sampleAspectRatio() > 0 && v.sampleAspectRatioBase() > 0) v.sampleAspectRatio().toFloat() / v.sampleAspectRatioBase() else 1f
            }
        }.getOrNull() ?: 1f
        // Nach dem Spulen im Ein-Verbindungs-Modus: vorher gewaehlte Tonspur wieder setzen
        val restore = restoreAudio
        if (restore != null && audio.any { it.id == restore } && a != restore) {
            restoreAudio = null
            runCatching { p.audio().setTrack(restore) }
        } else if (restore != null && audio.isNotEmpty()) restoreAudio = null
        ui {
            audioTracks = audio; subtitleTracks = subs; audioTrack = if (restore != null && audio.any { it.id == restore }) restore else a; subtitleTrack = s
            pixelAspect = sar.coerceIn(0.5f, 2f)
        }
    }

    private fun refreshAspect() { player?.let { p -> p.submit { readTracks(p) } } }

    /** Vom UI-Thread pro Bildschirm-Frame aufgerufen: holt das neueste Videobild. */
    fun pollFrame(): Image? {
        synchronized(lock) {
            if (hasNew) {
                val bytes = pending
                if (bytes != null && pendingW > 0 && pendingH > 0) {
                    val info = ImageInfo(pendingW, pendingH, ColorType.BGRA_8888, ColorAlphaType.OPAQUE)
                    val next = Image.makeRaster(info, bytes, pendingW * 4)
                    image?.close()
                    image = next
                    frameCounter++
                }
                hasNew = false
            }
        }
        return image
    }

    fun currentImage(): Image? = image

    private var released = false

    // --- Steuerung ---
    /** Laufender Stream (verhindert Neustart beim Wechsel ins Vollbild-Fenster). */
    var currentUrl: String? = null
        private set

    /** Live-Stream? -> groesserer Puffer (gegen Stocken). */
    private var live = false

    fun play(url: String, startAt: Long = 0L, live: Boolean = this.live) {
        this.live = live
        userPaused = false; stallSince = 0L; lastTick = -1L
        val p = player ?: return
        currentUrl = url
        // VPN-Kill-Switch gilt auch fuer Streams (wie Android)
        if (!url.startsWith("file:") && !NetConfig.allowed()) {
            error = com.poweriptv.desktop.data.VpnRequiredException().message; buffering = false; return
        }
        error = null; ended = false; buffering = true; frameCounter = 0
        time = startAt; length = 0L; seekable = false
        audioTracks = emptyList(); subtitleTracks = emptyList()
        pendingSeek = null
        val base = mediaOptions.map { o ->
            // Live: mindestens 4 s Puffer, Filme: mindestens 3 s (wie Android)
            if (o.startsWith(":network-caching=")) ":network-caching=" + maxOf(networkCaching, if (live) 4000 else 3000) else o
        } + listOfNotNull(
            if (live) ":live-caching=4000" else ":input-fast-seek", // Filme: Spulen zum naechsten Schluesselbild (schneller)
            // Bricht die Verbindung ab (z.B. beim Spulen), automatisch neu verbinden
            if (!url.startsWith("file:")) ":http-reconnect" else null,
        )
        val opts = (if (startAt > 0) base + ":start-time=${startAt / 1000.0}" else base).toTypedArray()
        p.media().play(url, *opts)
        p.audio().setVolume(volume)
        p.audio().setMute(muted)
        if (rate != 1f) p.controls().setRate(rate)
    }

    fun retry() {
        val url = currentUrl ?: return
        play(url, time)
    }

    fun togglePause() {
        val p = player ?: return
        if (ended) { currentUrl?.let { play(it) }; return }
        userPaused = p.status().isPlaying
        if (p.status().isPlaying) p.controls().setPause(true) else p.controls().play()
    }

    fun pause() { userPaused = true; player?.controls()?.setPause(true) }

    // ---------- Haenger-Waechter (wie Android) ----------
    // Bleibt das Bild stehen (Anbieter kappt beim Spulen die Verbindung, VLC meldet keinen Fehler),
    // wird der Stream automatisch an derselben Stelle neu geladen.
    private var userPaused = false
    private var lastTick = -1L
    private var stallSince = 0L
    private var recoveries = 0
    private var lastRecoveryAt = 0L

    /** Alle 0,5 s aus der Oberflaeche aufrufen. Liefert einen Hinweistext, wenn neu geladen wurde. */
    fun watchdog(): String? {
        val url = currentUrl ?: return null
        if (userPaused || error != null || castingTo != null || (!playing && !buffering)) { stallSince = 0L; return null }
        val now = System.currentTimeMillis()
        if (time != lastTick && time > 0) { lastTick = time; stallSince = 0L; return null }
        if (stallSince == 0L) { stallSince = now; return null }
        if (now - stallSince < (if (live) 12_000L else 10_000L)) return null
        stallSince = 0L
        if (now - lastRecoveryAt > 120_000L) recoveries = 0
        if (++recoveries > 3) { error = "Der Stream hängt – bitte später erneut versuchen"; buffering = false; return null }
        lastRecoveryAt = now
        val target = if (live) 0L else (pendingSeek ?: time).coerceAtLeast(0L)
        play(url, target, live)
        return "Verbindung hing – wird neu geladen …"
    }

    // ---------- Spul-Vorschau mit nur 1 erlaubten Verbindung ----------
    /** Film ist fuer die Spul-Vorschau angehalten (Verbindung frei fuer das Vorschaubild). */
    var previewSuspended by mutableStateOf(false); private set
    @Volatile private var restoreAudio: Int? = null
    private var suspendedUrl: String? = null
    private var suspendedPaused = false

    /** Film-Verbindung freigeben, solange die Vorschau laeuft (letztes Bild bleibt stehen). */
    fun suspendForPreview() {
        if (previewSuspended) return
        val url = currentUrl ?: return
        suspendedUrl = url
        suspendedPaused = userPaused
        restoreAudio = audioTrack.takeIf { it >= 0 }
        previewSuspended = true
        player?.controls()?.stop()
        currentUrl = null // Haenger-Waechter ruht
        playing = false; buffering = false
    }

    /** Nach dem Spulen an der Zielstelle weiter (gleiche Tonspur). */
    fun resumeAfterPreview(target: Long) {
        if (!previewSuspended) return
        previewSuspended = false
        val url = suspendedUrl ?: return
        suspendedUrl = null
        play(url, target.coerceAtLeast(0L), false)
        if (suspendedPaused) pause()
    }

    /** Verbindung zum Anbieter freigeben, letztes Bild bleibt stehen (Multi-View-Limit). */
    fun stopKeepFrame() {
        player?.controls()?.stop()
        currentUrl = null
        playing = false
    }

    fun muteAudio(m: Boolean) {
        muted = m
        player?.audio()?.setMute(m)
    }

    /** Uebertragung an einen Fernseher (Chromecast) oder zurueck auf den PC (null). */
    var castingTo by mutableStateOf<String?>(null); private set

    fun castTo(item: uk.co.caprica.vlcj.player.renderer.RendererItem?) {
        val p = player ?: return
        val url = currentUrl ?: return
        val pos = time
        p.controls().stop()
        p.setRenderer(item)
        castingTo = item?.name()
        play(url, pos)
    }

    fun stop() {
        player?.controls()?.stop()
        synchronized(lock) { image?.close(); image = null; hasNew = false }
        currentUrl = null
        playing = false
    }

    // Mehrfaches Spulen wird gesammelt und erst nach kurzer Pause an VLC gegeben (fluessiger).
    private var pendingSeek: Long? = null
    private var seekTimer: javax.swing.Timer? = null

    fun seekBy(deltaMs: Long) {
        if (length <= 0) return
        val base = pendingSeek ?: time
        seekTo(base + deltaMs)
    }

    fun seekTo(target: Long) {
        val p = player ?: return
        if (length <= 0) return
        val t = target.coerceIn(0L, (length - 1000).coerceAtLeast(0L))
        pendingSeek = t
        time = t
        seekTimer?.stop()
        seekTimer = javax.swing.Timer(250) {
            pendingSeek?.let { p.controls().setTime(it) }
            pendingSeek = null
        }.apply { isRepeats = false; start() }
    }

    /** Bildhelligkeit (VLC-Bildanpassung): 0,3 = dunkel … 1 = normal … 1,7 = hell. */
    var brightness by mutableStateOf(1f); private set

    fun setBrightnessTo(v: Float) {
        brightness = v.coerceIn(0.3f, 1.7f)
        applyBrightness()
    }

    private fun applyBrightness() {
        val p = player ?: return
        runCatching {
            if (kotlin.math.abs(brightness - 1f) < 0.01f) p.video().setAdjustVideo(false)
            else { p.video().setAdjustVideo(true); p.video().setBrightness(brightness) }
        }
    }

    fun changeVolume(delta: Int) = setVolumeTo(volume + delta)

    fun setVolumeTo(v: Int) {
        volume = v.coerceIn(0, 150)
        if (muted && volume > 0) muted = false
        player?.audio()?.setVolume(volume)
        player?.audio()?.setMute(muted)
    }

    fun toggleMute() {
        muted = !muted
        player?.audio()?.setMute(muted)
    }

    fun changeRate(r: Float) {
        rate = r
        player?.controls()?.setRate(r)
    }

    fun selectAudio(id: Int) {
        player?.audio()?.setTrack(id); audioTrack = id
    }

    fun selectSubtitle(id: Int) {
        player?.subpictures()?.setTrack(id); subtitleTrack = id
    }

    fun release() {
        if (released) return
        released = true
        Vlc.unregister(this)
        seekTimer?.stop()
        runCatching { player?.controls()?.stop() }
        runCatching { player?.release() }
        synchronized(lock) { image?.close(); image = null }
    }

    private fun ui(block: () -> Unit) {
        if (SwingUtilities.isEventDispatchThread()) block() else SwingUtilities.invokeLater(block)
    }
}

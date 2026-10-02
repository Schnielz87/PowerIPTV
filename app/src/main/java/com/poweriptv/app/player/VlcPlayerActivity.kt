package com.poweriptv.app.player

import androidx.compose.foundation.shape.CircleShape
import com.poweriptv.app.ui.components.tvFocus
import android.net.Uri
import android.os.Bundle
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.poweriptv.app.PlayEntry
import com.poweriptv.app.PowerIptvApp
import com.poweriptv.app.data.VideoScale
import com.poweriptv.app.ui.theme.BrandCyan
import com.poweriptv.app.ui.theme.PowerTheme
import com.poweriptv.app.vpn.VpnRequiredException
import kotlinx.coroutines.delay
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.util.VLCVideoLayout
import java.io.File

/**
 * Kompatibilitaets-Player auf Basis von VLC (libVLC).
 * Dekodiert per Software praktisch jedes Format (MPEG-2, HEVC, Interlaced, ...).
 * Timeshift und Aufnahme gibt es hier nicht – dafuer den Standard-Player nutzen.
 */
class VlcPlayerActivity : ComponentActivity() {

    private val container get() = (application as PowerIptvApp).container
    private lateinit var libVlc: LibVLC
    private lateinit var mediaPlayer: MediaPlayer

    private var title by mutableStateOf("")
    private var error by mutableStateOf<String?>(null)
    private var buffering by mutableStateOf(true)
    private var playing by mutableStateOf(false)
    private var showOverlay by mutableStateOf(true)
    private var toast by mutableStateOf<String?>(null)
    private var scale = VideoScale.FIT
    private var position by mutableStateOf(0L)
    private var length by mutableStateOf(0L)
    /** Waehrend der Nutzer den Regler zieht, keine Positions-Updates. */
    private var dragging by mutableStateOf<Float?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Immer nur ein Player
        PlayerActivity.closeActive()
        active?.get()?.takeIf { it !== this }?.finish()
        active = java.lang.ref.WeakReference(this)

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

        libVlc = LibVLC(
            this,
            arrayListOf(
                "--http-user-agent=${container.settings.userAgent.value}",
                "--network-caching=1500",
                "--http-reconnect",
                "--deinterlace=1",
                "--deinterlace-mode=yadif",
            ),
        )
        mediaPlayer = MediaPlayer(libVlc)
        mediaPlayer.setEventListener { event ->
            when (event.type) {
                MediaPlayer.Event.Buffering -> buffering = event.buffering < 100f
                MediaPlayer.Event.Playing -> { playing = true; buffering = false; error = null }
                MediaPlayer.Event.Paused, MediaPlayer.Event.Stopped -> playing = false
                MediaPlayer.Event.EncounteredError -> { error = "Wiedergabe fehlgeschlagen (VLC)"; buffering = false }
                MediaPlayer.Event.EndReached -> if (current()?.live != true && hasNext()) runOnUiThread { next() }
            }
        }
        scale = container.settings.videoScaleEnum()
        intent.getStringExtra(EXTRA_INFO)?.let { toast = it }

        setContent {
            PowerTheme {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Color.Black)
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                            showOverlay = !showOverlay
                        },
                ) {
                    AndroidView(
                        factory = { ctx ->
                            VLCVideoLayout(ctx).also { layout ->
                                mediaPlayer.attachViews(layout, null, false, false)
                                applyScale()
                                play(container.playIndex)
                            }
                        },
                        modifier = Modifier.fillMaxSize(),
                    )
                    if (buffering && error == null) CircularProgressIndicator(Modifier.align(Alignment.Center), color = BrandCyan)
                    if (showOverlay) {
                        Overlay()
                        if (current()?.live != true) SeekBar(Modifier.align(Alignment.BottomCenter))
                    }
                    toast?.let { msg ->
                        LaunchedEffect(msg) { delay(2500); toast = null }
                        Text(
                            msg, color = Color.White,
                            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 48.dp)
                                .clip(RoundedCornerShape(10.dp)).background(Color(0xCC000000)).padding(12.dp),
                        )
                    }
                    error?.let { msg ->
                        Column(
                            Modifier.align(Alignment.Center).padding(24.dp)
                                .clip(RoundedCornerShape(12.dp)).background(Color(0xCC000000)).padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Text(msg, color = Color.White)
                            Button(modifier = Modifier.tvFocus(RoundedCornerShape(50), 1.06f), onClick = { play(container.playIndex) }) { Text("Erneut versuchen") }
                        }
                    }
                }
            }
        }
    }

    @androidx.compose.runtime.Composable
    private fun Overlay() {
        LaunchedEffect(showOverlay, title) {
            delay(5000)
            if (playing) showOverlay = false
        }
        Row(
            Modifier.fillMaxWidth().background(Color(0x99000000)).padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(modifier = Modifier.tvFocus(CircleShape, 1.15f), onClick = { closePlayer() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Zurueck zur Uebersicht", tint = Color.White) }
            if (container.playQueue.size > 1) {
                IconButton(modifier = Modifier.tvFocus(CircleShape, 1.15f), onClick = { previous() }) { Icon(Icons.Filled.SkipPrevious, "Vorheriger", tint = Color.White) }
            }
            Column(Modifier.weight(1f)) {
                Text(title, color = Color.White, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("VLC-Player", color = BrandCyan, style = MaterialTheme.typography.labelSmall)
            }
            IconButton(modifier = Modifier.tvFocus(CircleShape, 1.15f), onClick = { togglePause() }) {
                Icon(if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow, "Wiedergabe", tint = Color.White)
            }
            IconButton(modifier = Modifier.tvFocus(CircleShape, 1.15f), onClick = { cycleScale() }) { Icon(Icons.Filled.AspectRatio, "Bildformat", tint = Color.White) }
            if (container.playQueue.size > 1) {
                IconButton(modifier = Modifier.tvFocus(CircleShape, 1.15f), onClick = { next() }) { Icon(Icons.Filled.SkipNext, "Naechster", tint = Color.White) }
            }
        }
    }

    /** Zeitleiste mit Schieberegler (Filme/Serien). */
    @androidx.compose.runtime.Composable
    private fun SeekBar(modifier: Modifier) {
        LaunchedEffect(Unit) {
            while (true) {
                if (dragging == null) {
                    position = mediaPlayer.time.coerceAtLeast(0)
                    length = mediaPlayer.length.coerceAtLeast(0)
                }
                delay(500)
            }
        }
        if (length <= 0) return
        Row(
            modifier.fillMaxWidth().background(Color(0x99000000)).padding(horizontal = 16.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val shown = dragging?.let { (it * length).toLong() } ?: position
            Text(formatTime(shown), color = Color.White, style = MaterialTheme.typography.labelMedium)
            Slider(
                value = dragging ?: (position.toFloat() / length).coerceIn(0f, 1f),
                onValueChange = { dragging = it; showOverlay = true },
                onValueChangeFinished = {
                    dragging?.let { mediaPlayer.time = (it * length).toLong() }
                    dragging = null
                },
                modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
                colors = SliderDefaults.colors(thumbColor = BrandCyan, activeTrackColor = BrandCyan),
            )
            Text(formatTime(length), color = Color.White, style = MaterialTheme.typography.labelMedium)
        }
    }

    private fun formatTime(ms: Long): String {
        val s = ms / 1000
        return if (s >= 3600) "%d:%02d:%02d".format(s / 3600, (s / 60) % 60, s % 60) else "%d:%02d".format(s / 60, s % 60)
    }

    private fun seekStep(repeat: Int): Long = when {
        repeat < 5 -> 30_000L
        repeat < 15 -> 60_000L
        repeat < 30 -> 120_000L
        else -> 300_000L
    }

    private fun seekBy(deltaMs: Long) {
        val len = mediaPlayer.length.takeIf { it > 0 } ?: return
        if (!mediaPlayer.isSeekable) { toast = "Dieser Stream unterstuetzt kein Spulen"; return }
        val target = (mediaPlayer.time + deltaMs).coerceIn(0, len - 1000)
        mediaPlayer.time = target
        position = target
        val sign = if (deltaMs >= 0) "⏩ +" else "⏪ −"
        toast = "$sign${formatTime(kotlin.math.abs(deltaMs))}  ·  ${formatTime(target)} / ${formatTime(len)}"
    }

    private fun current(): PlayEntry? = container.playQueue.getOrNull(container.playIndex)
    private fun hasNext() = container.playIndex < container.playQueue.lastIndex

    private fun play(index: Int) {
        val queue = container.playQueue
        if (queue.isEmpty()) { finish(); return }
        container.playIndex = (index + queue.size) % queue.size
        val entry = queue[container.playIndex]
        title = entry.title
        error = null
        buffering = true
        entry.item?.let { container.history.add(it) }

        val local = entry.url.startsWith("/")
        if (!local && container.settings.vpnRequired.value && !container.vpn.isProtected()) {
            mediaPlayer.stop()
            error = VpnRequiredException().message
            buffering = false
            return
        }
        val uri = if (local) Uri.fromFile(File(entry.url)) else Uri.parse(entry.url)
        val media = Media(libVlc, uri).apply {
            setHWDecoderEnabled(true, false) // Hardware wenn moeglich, sonst automatisch Software
            addOption(":network-caching=1500")
            addOption(":http-user-agent=${container.settings.userAgent.value}")
        }
        mediaPlayer.media = media
        media.release()
        mediaPlayer.play()
    }

    private fun next() = play(container.playIndex + 1)
    private fun previous() = play(container.playIndex - 1)

    private fun togglePause() {
        if (mediaPlayer.isPlaying) mediaPlayer.pause() else mediaPlayer.play()
    }

    private fun applyScale() {
        mediaPlayer.videoScale = when (scale) {
            VideoScale.FIT -> MediaPlayer.ScaleType.SURFACE_BEST_FIT
            VideoScale.ZOOM -> MediaPlayer.ScaleType.SURFACE_FIT_SCREEN
            VideoScale.FILL -> MediaPlayer.ScaleType.SURFACE_FILL
        }
    }

    private fun cycleScale() {
        val all = VideoScale.entries
        scale = all[(all.indexOf(scale) + 1) % all.size]
        applyScale()
        container.settings.setVideoScale(scale)
        toast = "Bildformat: ${scale.label}"
    }

    private fun closePlayer() {
        runCatching { mediaPlayer.stop() }
        finish()
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action != KeyEvent.ACTION_DOWN) return super.dispatchKeyEvent(event)
        val live = current()?.live == true
        if (!live) {
            val step = seekStep(event.repeatCount)
            when (event.keyCode) {
                KeyEvent.KEYCODE_DPAD_LEFT -> if (!showOverlay) { seekBy(-step / 3); return true }
                KeyEvent.KEYCODE_DPAD_RIGHT -> if (!showOverlay) { seekBy(step); return true }
                KeyEvent.KEYCODE_MEDIA_REWIND -> { seekBy(-step); return true }
                KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> { seekBy(step); return true }
            }
        }
        when (event.keyCode) {
            KeyEvent.KEYCODE_CHANNEL_UP, KeyEvent.KEYCODE_PAGE_UP -> { next(); return true }
            KeyEvent.KEYCODE_CHANNEL_DOWN, KeyEvent.KEYCODE_PAGE_DOWN -> { previous(); return true }
            KeyEvent.KEYCODE_DPAD_UP -> if (live && !showOverlay) { next(); return true }
            KeyEvent.KEYCODE_DPAD_DOWN -> if (live && !showOverlay) { previous(); return true }
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_BUTTON_A ->
                if (!showOverlay) { showOverlay = true; return true }
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, KeyEvent.KEYCODE_MEDIA_PLAY, KeyEvent.KEYCODE_MEDIA_PAUSE -> { togglePause(); return true }
            KeyEvent.KEYCODE_PROG_BLUE, KeyEvent.KEYCODE_TV_ZOOM_MODE -> { cycleScale(); return true }
            KeyEvent.KEYCODE_INFO, KeyEvent.KEYCODE_MENU, KeyEvent.KEYCODE_GUIDE -> { showOverlay = !showOverlay; return true }
            KeyEvent.KEYCODE_BUTTON_B -> { closePlayer(); return true }
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onStop() {
        super.onStop()
        // Kein Bild-in-Bild im VLC-Modus -> beim Verlassen pausieren
        runCatching { mediaPlayer.pause() }
    }

    override fun onDestroy() {
        if (active?.get() === this) active = null
        runCatching {
            mediaPlayer.stop()
            mediaPlayer.detachViews()
            mediaPlayer.release()
            libVlc.release()
        }
        super.onDestroy()
    }

    companion object {
        const val EXTRA_INFO = "info"
        private var active: java.lang.ref.WeakReference<VlcPlayerActivity>? = null

        fun closeActive() {
            active?.get()?.closePlayer()
        }
    }
}

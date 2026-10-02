package com.poweriptv.app.player

import android.app.PictureInPictureParams
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Rational
import android.view.KeyEvent
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
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
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import com.poweriptv.app.PlayEntry
import com.poweriptv.app.PowerIptvApp
import com.poweriptv.app.data.EpgEntry
import com.poweriptv.app.ui.theme.PowerTheme
import com.poweriptv.app.vpn.VpnRequiredException
import java.io.File
import java.text.DateFormat
import java.util.Date

@OptIn(UnstableApi::class)
class PlayerActivity : ComponentActivity() {

    private val container get() = (application as PowerIptvApp).container
    private lateinit var player: ExoPlayer

    private var title by mutableStateOf("")
    private var error by mutableStateOf<String?>(null)
    private var epg by mutableStateOf<List<EpgEntry>>(emptyList())
    private var showOverlay by mutableStateOf(true)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

        // Online: ueber OkHttp (VPN-Kill-Switch + User-Agent). Offline-Dateien: direkt.
        val httpFactory = OkHttpDataSource.Factory(container.http)
        val dataSourceFactory = DefaultDataSource.Factory(this, httpFactory)
        player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))
            .build()
        player.addListener(object : Player.Listener {
            override fun onPlayerError(e: PlaybackException) {
                val cause = generateSequence(e as Throwable) { it.cause }.firstOrNull { it is VpnRequiredException }
                error = cause?.message ?: "Wiedergabe fehlgeschlagen: ${e.errorCodeName}"
            }
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_READY) error = null
                if (state == Player.STATE_ENDED && !current()?.live.orFalse() && hasNext()) next()
            }
        })

        play(container.playIndex)

        setContent {
            PowerTheme {
                Box(Modifier.fillMaxSize().background(Color.Black)) {
                    AndroidView(
                        factory = { ctx ->
                            PlayerView(ctx).apply {
                                layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                                player = this@PlayerActivity.player
                                keepScreenOn = true
                                setShowSubtitleButton(true)
                                setControllerVisibilityListener(PlayerView.ControllerVisibilityListener { v ->
                                    showOverlay = v == android.view.View.VISIBLE
                                })
                            }
                        },
                        modifier = Modifier.fillMaxSize(),
                    )
                    if (showOverlay) TopOverlay()
                    error?.let { msg ->
                        Column(
                            Modifier.align(Alignment.Center).padding(24.dp)
                                .clip(RoundedCornerShape(12.dp)).background(Color(0xCC000000)).padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Text(msg, color = Color.White)
                            Button(onClick = { play(container.playIndex) }) { Text("Erneut versuchen") }
                        }
                    }
                }
            }
        }
    }

    @androidx.compose.runtime.Composable
    private fun TopOverlay() {
        val entry = current()
        LaunchedEffect(title) {
            epg = emptyList()
            val item = entry?.item
            if (entry?.live == true && item != null) {
                epg = runCatching { container.source?.shortEpg(item).orEmpty() }.getOrDefault(emptyList())
            }
        }
        Column(
            Modifier.fillMaxWidth().background(Color(0x99000000)).padding(horizontal = 16.dp, vertical = 10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (container.playQueue.size > 1) {
                    IconButton(onClick = { previous() }) { Icon(Icons.Filled.SkipPrevious, "Vorheriger", tint = Color.White) }
                }
                Text(
                    title, color = Color.White, fontWeight = FontWeight.Bold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
                )
                if (container.playQueue.size > 1) {
                    IconButton(onClick = { next() }) { Icon(Icons.Filled.SkipNext, "Naechster", tint = Color.White) }
                }
            }
            val fmt = DateFormat.getTimeInstance(DateFormat.SHORT)
            epg.take(2).forEachIndexed { i, e ->
                Text(
                    (if (i == 0) "Jetzt: " else "Danach: ") + "${fmt.format(Date(e.start))} ${e.title}",
                    color = Color.White.copy(alpha = if (i == 0) 0.95f else 0.7f),
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                )
            }
        }
    }

    private fun current(): PlayEntry? = container.playQueue.getOrNull(container.playIndex)
    private fun hasNext() = container.playIndex < container.playQueue.lastIndex
    private fun Boolean?.orFalse() = this ?: false

    private fun play(index: Int) {
        val queue = container.playQueue
        if (queue.isEmpty()) { finish(); return }
        container.playIndex = (index + queue.size) % queue.size
        val entry = queue[container.playIndex]
        title = entry.title
        error = null

        if (container.settings.vpnRequired.value && !container.vpn.isProtected() && !isLocal(entry.url)) {
            error = VpnRequiredException().message
            player.stop()
            return
        }

        val uri = if (isLocal(entry.url)) Uri.fromFile(File(entry.url)) else Uri.parse(entry.url)
        val builder = MediaItem.Builder().setUri(uri)
        if (entry.url.contains(".m3u8", ignoreCase = true)) builder.setMimeType(MimeTypes.APPLICATION_M3U8)
        player.setMediaItem(builder.build())
        player.prepare()
        player.playWhenReady = true
    }

    private fun isLocal(url: String) = url.startsWith("/")

    private fun next() = play(container.playIndex + 1)
    private fun previous() = play(container.playIndex - 1)

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN && current()?.live == true) {
            when (event.keyCode) {
                KeyEvent.KEYCODE_CHANNEL_UP, KeyEvent.KEYCODE_PAGE_UP -> { next(); return true }
                KeyEvent.KEYCODE_CHANNEL_DOWN, KeyEvent.KEYCODE_PAGE_DOWN -> { previous(); return true }
            }
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && player.isPlaying) {
            runCatching {
                enterPictureInPictureMode(PictureInPictureParams.Builder().setAspectRatio(Rational(16, 9)).build())
            }
        }
    }

    override fun onStop() {
        super.onStop()
        if (!(Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && isInPictureInPictureMode)) player.pause()
    }

    override fun onDestroy() {
        player.release()
        super.onDestroy()
    }
}

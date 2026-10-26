package com.poweriptv.app.ui.components

import android.view.ViewGroup
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.poweriptv.app.AppContainer
import com.poweriptv.app.data.ContentItem
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Live-Vorschau in der Senderliste (TV): der angewaehlte Sender laeuft klein rechts neben der Liste,
 * darunter Jetzt/Danach aus dem TV-Guide. Startet erst, wenn man kurz auf einem Sender stehen bleibt
 * (kein Verbindungs-Gewitter beim schnellen Blaettern), und gibt die Verbindung frei, bevor der
 * Vollbild-Player startet bzw. die App in den Hintergrund geht.
 */
class LivePreviewHandle {
    internal var player: ExoPlayer? = null

    /** Verbindung sofort freigeben (z.B. vor dem Vollbild-Player). */
    fun stop() {
        player?.let { it.stop(); it.clearMediaItems() }
    }
}

@OptIn(UnstableApi::class)
@Composable
fun LivePreviewPane(
    container: AppContainer,
    handle: LivePreviewHandle,
    item: ContentItem?,
    url: String?,
    blocked: Boolean,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var buffering by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    var started by remember { mutableStateOf<String?>(null) }
    var visible by remember { mutableStateOf(lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) }

    val player = remember {
        val http = container.http.newBuilder()
            .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(20, java.util.concurrent.TimeUnit.SECONDS)
            .build()
        ExoPlayer.Builder(
            context,
            DefaultRenderersFactory(context).setEnableDecoderFallback(true)
                .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON),
        )
            .setMediaSourceFactory(DefaultMediaSourceFactory(DefaultDataSource.Factory(context, OkHttpDataSource.Factory(http))))
            // Vorschau soll schnell da sein: kurzer Startpuffer
            .setLoadControl(DefaultLoadControl.Builder().setBufferDurationsMs(8_000, 20_000, 1_000, 2_500).build())
            .build()
            .also { handle.player = it }
    }
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) { buffering = state == Player.STATE_BUFFERING }
            override fun onPlayerError(error: PlaybackException) { failed = true; buffering = false }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener); handle.player = null; player.release() }
    }
    // App im Hintergrund / Vollbild-Player offen -> Vorschau aus, danach wieder an
    DisposableEffect(lifecycle) {
        val obs = LifecycleEventObserver { _, e ->
            when (e) {
                Lifecycle.Event.ON_STOP -> { visible = false; handle.stop(); started = null }
                Lifecycle.Event.ON_START -> visible = true
                else -> {}
            }
        }
        lifecycle.addObserver(obs)
        onDispose { lifecycle.removeObserver(obs) }
    }
    LaunchedEffect(url, visible, blocked) {
        if (url == null || !visible || blocked) { handle.stop(); started = null; return@LaunchedEffect }
        if (started == url && player.playbackState != Player.STATE_IDLE) return@LaunchedEffect
        handle.stop()
        failed = false
        delay(750) // erst starten, wenn man kurz auf dem Sender bleibt
        player.setMediaItem(MediaItem.fromUri(url))
        player.prepare()
        player.playWhenReady = true
        started = url
    }

    Column(modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(
            Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(12.dp)).background(Color.Black),
            contentAlignment = Alignment.Center,
        ) {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        useController = false
                        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                        layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                        isFocusable = false
                        this.player = player
                    }
                },
                modifier = Modifier.fillMaxSize(),
            )
            when {
                item == null -> Icon(Icons.Filled.LiveTv, null, tint = Color.White.copy(alpha = 0.5f), modifier = Modifier.size(48.dp))
                blocked -> Icon(Icons.Filled.Lock, "Gesperrt", tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(40.dp))
                failed -> Text("Vorschau nicht möglich – OK für Vollbild", color = Color.White, style = MaterialTheme.typography.bodySmall)
                buffering || started == null -> CircularProgressIndicator(Modifier.size(28.dp), color = Color.White, strokeWidth = 2.dp)
            }
        }
        if (item == null) {
            Text("Sender auswählen – die Vorschau startet automatisch.", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            return@Column
        }
        Text(item.name, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
        val now = System.currentTimeMillis()
        val cur = remember(item.key, now / 60_000) { runCatching { container.epg.current(item) }.getOrNull() }
        val nxt = remember(item.key, now / 60_000) { runCatching { container.epg.next(item) }.getOrNull() }
        val fmt = remember { SimpleDateFormat("HH:mm", Locale.GERMANY) }
        if (cur != null) {
            Text("Jetzt: ${fmt.format(Date(cur.start))}–${fmt.format(Date(cur.end))}  ${cur.title}", maxLines = 2, overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium)
            val p = ((now - cur.start).toFloat() / (cur.end - cur.start).coerceAtLeast(1)).coerceIn(0f, 1f)
            LinearProgressIndicator(progress = { p }, modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)))
        }
        if (nxt != null) {
            Text("Danach: ${fmt.format(Date(nxt.start))}  ${nxt.title}", maxLines = 2, overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text("OK = Vollbild  ·  Menü = Favorit / Liste", style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

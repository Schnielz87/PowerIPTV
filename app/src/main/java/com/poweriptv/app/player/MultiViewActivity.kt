package com.poweriptv.app.player

import android.os.Bundle
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.annotation.OptIn
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Splitscreen
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import kotlinx.coroutines.launch
import androidx.lifecycle.lifecycleScope
import androidx.compose.material.icons.filled.PauseCircle
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import com.poweriptv.app.PlayEntry
import com.poweriptv.app.PowerIptvApp
import com.poweriptv.app.data.Category
import com.poweriptv.app.data.ContentItem
import com.poweriptv.app.data.ContentType
import com.poweriptv.app.ui.components.tvFocus
import com.poweriptv.app.ui.theme.BrandCyan
import com.poweriptv.app.ui.theme.PowerTheme

/**
 * Multi-Screen: bis zu 4 Live-Kanaele gleichzeitig. Ton kommt vom ausgewaehlten Fenster.
 * Hinweis: Je nach Geraet unterstuetzt die Hardware nicht beliebig viele parallele Decoder.
 */
@OptIn(UnstableApi::class)
class MultiViewActivity : ComponentActivity() {

    private val container get() = (application as PowerIptvApp).container
    private val players = arrayOfNulls<ExoPlayer>(4)
    private val slots = mutableStateListOf<PlayEntry?>(null, null, null, null)
    private var audioSlot by mutableIntStateOf(0)
    private var fourWay by mutableStateOf(true)
    private var pickerFor by mutableStateOf<Int?>(null)
    /** Bedienelemente nur kurz einblenden (sonst verdecken sie Senderlogos). */
    private var controlsVisible by mutableStateOf(true)
    private var lastTouch by mutableLongStateOf(System.currentTimeMillis())
    private val retry = android.os.Handler(android.os.Looper.getMainLooper())

    private fun touch() { lastTouch = System.currentTimeMillis(); controlsVisible = true }

    // ---------- Verbindungslimit des Zugangs ----------
    /** Zuletzt ausgewaehlte Fenster zuerst (die laufen bei begrenztem Zugang). */
    private val recent = mutableListOf<Int>()
    /** Fenster, die gerade wirklich streamen. */
    private val running = mutableStateListOf<Int>()
    /** Erlaubte gleichzeitige Streams (null = unbekannt -> alle laufen). */
    private var connLimit by mutableStateOf<Int?>(null)

    /** Wie viele Streams duerfen im Multi-View laufen (laufende Aufnahmen belegen auch Verbindungen). */
    private fun allowedStreams(): Int? = connLimit?.let { (it - container.recordings.running().size).coerceAtLeast(1) }

    /** Passt an, welche Fenster laufen: bei erreichtem Limit nur die zuletzt ausgewaehlten. */
    private fun updateRunning() {
        val filled = (0 until 4).filter { slots[it] != null && (fourWay || it < 2) }
        val order = (listOf(audioSlot) + recent + filled).distinct().filter { it in filled }
        val allowed = allowedStreams() ?: filled.size
        val shouldRun = order.take(allowed).toSet()
        for (i in 0 until 4) {
            val p = players[i] ?: continue
            if (i in shouldRun) {
                if (i !in running) { p.prepare(); p.playWhenReady = true; running.add(i) }
            } else if (i in running || p.playbackState != androidx.media3.common.Player.STATE_IDLE) {
                p.stop() // Verbindung zum Anbieter freigeben, letztes Bild bleibt stehen
                running.remove(i)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        PlayerActivity.closeActive()
        VlcPlayerActivity.closeActive()
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        // Vollbild inkl. Kamera-Aussparung: Video sitzt mittig, kein schwarzer Rand auf der Kameraseite
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            window.attributes = window.attributes.apply {
                layoutInDisplayCutoutMode = android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).hide(WindowInsetsCompat.Type.systemBars())

        // Verbindungslimit des Zugangs (Xtream: max_connections)
        connLimit = container.maxConnections
        if (connLimit == null) lifecycleScope.launch {
            val m = runCatching { container.source?.accountInfo()?.maxConnections?.toIntOrNull() }.getOrNull()
            if (m != null) { connLimit = m; container.maxConnections = m; updateRunning() }
        }
        container.playQueue.getOrNull(container.playIndex)?.takeIf { it.live }?.let { setSlot(0, it) }

        setContent {
            PowerTheme {
                Box(Modifier.fillMaxSize().background(Color.Black)) {
                    val count = if (fourWay) 4 else 2
                    Column(Modifier.fillMaxSize()) {
                        val rows = if (fourWay) listOf(0 to 1, 2 to 3) else listOf(0 to 1)
                        rows.forEach { (a, b) ->
                            Row(Modifier.weight(1f).fillMaxWidth()) {
                                Slot(a, Modifier.weight(1f).fillMaxSize())
                                Slot(b, Modifier.weight(1f).fillMaxSize())
                            }
                        }
                    }
                    // Bedienelemente 4 s nach der letzten Beruehrung ausblenden
                    LaunchedEffect(controlsVisible, lastTouch, pickerFor) {
                        if (!controlsVisible || pickerFor != null) return@LaunchedEffect
                        kotlinx.coroutines.delay(4000)
                        if (slots.any { it != null }) controlsVisible = false
                    }
                    if (controlsVisible) Row(
                        Modifier.align(Alignment.BottomCenter).padding(10.dp)
                            .clip(RoundedCornerShape(24.dp)).background(Color(0xCC000000)).padding(horizontal = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconButton(onClick = { touch(); setLayout(!fourWay) }, modifier = Modifier.tvFocus(CircleShape)) {
                            Icon(if (fourWay) Icons.Filled.Splitscreen else Icons.Filled.GridView, "Layout", tint = Color.White)
                        }
                        Text(if (fourWay) "4 Fenster" else "2 Fenster", color = Color.White, style = MaterialTheme.typography.labelMedium)
                        Spacer(Modifier.width(12.dp))
                        Text(
                            allowedStreams()?.let { "Streams: ${running.size} / $it" } ?: "Streams: ${running.size}",
                            color = BrandCyan, style = MaterialTheme.typography.labelMedium,
                        )
                        Spacer(Modifier.width(6.dp))
                        IconButton(onClick = { finish() }, modifier = Modifier.tvFocus(CircleShape)) { Icon(Icons.Filled.Close, "Multi-View schliessen", tint = Color.White) }
                    }
                    pickerFor?.takeIf { it < count }?.let { slot -> ChannelPicker(slot) }
                }
            }
        }
    }

    private fun setLayout(four: Boolean) {
        fourWay = four
        applyTrackLimits()
        updateRunning()
        if (!four) {
            for (i in 2..3) { players[i]?.release(); players[i] = null; slots[i] = null; running.remove(i); recent.remove(i) }
            if (audioSlot > 1) selectAudio(0)
        }
    }

    @kotlin.OptIn(ExperimentalFoundationApi::class)
    @Composable
    private fun Slot(index: Int, modifier: Modifier) {
        val entry = slots[index]
        val selected = audioSlot == index
        Box(
            modifier
                .padding(2.dp)
                .border(if (selected) 3.dp else 1.dp, if (selected) BrandCyan else Color.DarkGray)
                .tvFocus(RoundedCornerShape(0.dp), 1f)
                .combinedClickable(
                    onClick = { touch(); if (entry == null) pickerFor = index else selectAudio(index) },
                    onLongClick = { touch(); pickerFor = index },
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (entry == null) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Filled.Add, null, tint = Color.White, modifier = Modifier.size(40.dp))
                    Text("Kanal hinzufuegen", color = Color.White)
                }
            } else {
                androidx.compose.runtime.key(entry.url) {
                    AndroidView(
                        factory = { ctx ->
                            PlayerView(ctx).apply {
                                setKeepContentOnPlayerReset(true) // pausiert: letztes Bild bleibt stehen
                                layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                                useController = false
                                setShutterBackgroundColor(android.graphics.Color.BLACK)
                                resizeMode = androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT
                                player = players[index]
                            }
                        },
                        update = { it.player = players[index] },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                if (index !in running) {
                    // Wegen Verbindungslimit angehalten
                    Column(
                        Modifier.align(Alignment.Center).clip(RoundedCornerShape(10.dp)).background(Color(0xB0000000)).padding(10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Icon(Icons.Filled.PauseCircle, null, tint = Color.White, modifier = Modifier.size(30.dp))
                        Text("Pausiert (Limit des Zugangs)", color = Color.White, style = MaterialTheme.typography.labelMedium)
                        Text("Antippen zum Anschauen", color = Color.White.copy(alpha = 0.7f), style = MaterialTheme.typography.labelSmall)
                    }
                }
                if (controlsVisible || selected) Row(
                    Modifier.align(Alignment.TopStart).padding(6.dp)
                        .clip(RoundedCornerShape(6.dp)).background(Color(0x99000000)).padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (selected) {
                        Icon(Icons.AutoMirrored.Filled.VolumeUp, null, tint = BrandCyan, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                    }
                    Text(entry.title, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelMedium)
                }
                if (controlsVisible) Row(
                    Modifier.align(Alignment.BottomEnd).padding(6.dp)
                        .clip(RoundedCornerShape(20.dp)).background(Color(0x99000000)),
                ) {
                    IconButton(onClick = { touch(); pickerFor = index }) { Icon(Icons.Filled.SwapHoriz, "Kanal wechseln", tint = Color.White) }
                    IconButton(onClick = { touch(); clearSlot(index) }) { Icon(Icons.Filled.Close, "Entfernen", tint = Color.White) }
                }
            }
        }
    }

    @Composable
    private fun ChannelPicker(slot: Int) {
        val source = container.source ?: run { pickerFor = null; return }
        var categories by remember { mutableStateOf<List<Category>>(emptyList()) }
        var cat by remember { mutableStateOf<String?>(null) }
        var channels by remember { mutableStateOf<List<ContentItem>>(emptyList()) }
        LaunchedEffect(Unit) {
            val all = runCatching { source.categories(ContentType.LIVE) }.getOrDefault(emptyList())
            categories = all.filterNot { container.parental.requiresPin(source.profile.id, ContentType.LIVE, it) }
            cat = "__fav__"
        }
        LaunchedEffect(cat) {
            val c = cat ?: return@LaunchedEffect
            channels = if (c == "__fav__") container.favorites.favorites.value.filter { it.type == ContentType.LIVE }
            else runCatching { source.items(ContentType.LIVE, c) }.getOrDefault(emptyList())
        }
        AlertDialog(
            onDismissRequest = { pickerFor = null },
            title = { Text("Kanal fuer Fenster ${slot + 1}") },
            text = {
                Column {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        item { FilterChip(selected = cat == "__fav__", onClick = { cat = "__fav__" }, label = { Text("★ Favoriten") }) }
                        items(categories, key = { it.id }) { c ->
                            FilterChip(selected = cat == c.id, onClick = { cat = c.id }, label = { Text(c.name, maxLines = 1) })
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    LazyColumn(Modifier.height(320.dp), contentPadding = PaddingValues(vertical = 4.dp)) {
                        items(channels) { ch ->
                            Text(
                                ch.name,
                                modifier = Modifier.fillMaxWidth()
                                    .tvFocus(RoundedCornerShape(6.dp), 1f)
                                    .clickable {
                                        setSlot(slot, PlayEntry(ch.name, source.streamUrl(ch), ch, live = true))
                                        pickerFor = null
                                    }
                                    .padding(10.dp),
                                maxLines = 1, overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { pickerFor = null }) { Text("Schliessen") } },
        )
    }

    /**
     * Schlanker Player fuer ein Fenster: kleiner Puffer (statt bis zu 50 s), Decoder-Ausweichen,
     * begrenzte Aufloesung bei 4 Fenstern und automatischer Neustart bei Abbruechen.
     */
    private fun createPlayer(index: Int): ExoPlayer {
        val load = androidx.media3.exoplayer.DefaultLoadControl.Builder()
            .setBufferDurationsMs(4_000, 12_000, 1_500, 2_500)
            .build()
        val p = ExoPlayer.Builder(this, androidx.media3.exoplayer.DefaultRenderersFactory(this).setEnableDecoderFallback(true))
            .setMediaSourceFactory(DefaultMediaSourceFactory(DefaultDataSource.Factory(this, OkHttpDataSource.Factory(container.http))))
            .setLoadControl(load)
            .build()
        p.addListener(object : androidx.media3.common.Player.Listener {
            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                // Stream abgebrochen -> nach 2 s selbst neu verbinden
                retry.postDelayed({ if (players[index] === p && index in running) { p.prepare(); p.playWhenReady = true } }, 2_000)
            }
        })
        return p
    }

    /** Ton nur im ausgewaehlten Fenster dekodieren; bei 4 Fenstern hoechstens 720p (bei HLS-Streams). */
    private fun applyTrackLimits() {
        players.forEachIndexed { i, p ->
            p ?: return@forEachIndexed
            p.trackSelectionParameters = p.trackSelectionParameters.buildUpon()
                .setTrackTypeDisabled(androidx.media3.common.C.TRACK_TYPE_AUDIO, i != audioSlot)
                .setMaxVideoSize(if (fourWay) 1280 else 1920, if (fourWay) 720 else 1080)
                .build()
        }
    }

    private fun setSlot(index: Int, entry: PlayEntry) {
        val p = players[index] ?: createPlayer(index).also { players[index] = it }
        val builder = MediaItem.Builder().setUri(entry.url)
        if (entry.url.contains(".m3u8", true)) builder.setMimeType(MimeTypes.APPLICATION_M3U8)
        p.setMediaItem(builder.build())
        running.remove(index)
        slots[index] = entry
        recent.remove(index); recent.add(0, index)
        val filledCount = slots.count { it != null }
        val limit = allowedStreams()
        if (filledCount == 1) audioSlot = index
        // Limit erreicht: das neue Fenster wird ausgewaehlt (laeuft + Ton), andere pausieren
        if (limit != null && filledCount > limit) {
            audioSlot = index
            android.widget.Toast.makeText(
                this,
                "Dein Zugang erlaubt $limit gleichzeitige${if (limit == 1) "n" else ""} Stream${if (limit == 1) "" else "s"} – " +
                    "es laeuft nur das ausgewaehlte Fenster. Antippen zum Wechseln.",
                android.widget.Toast.LENGTH_LONG,
            ).show()
        }
        applyAudio()
    }

    private fun clearSlot(index: Int) {
        players[index]?.release()
        players[index] = null
        slots[index] = null
        running.remove(index); recent.remove(index)
        if (audioSlot == index) slots.indexOfFirst { it != null }.takeIf { it >= 0 }?.let { audioSlot = it }
        applyAudio()
    }

    private fun selectAudio(index: Int) {
        audioSlot = index
        recent.remove(index); recent.add(0, index)
        applyAudio()
    }

    private fun applyAudio() {
        players.forEachIndexed { i, p -> p?.volume = if (i == audioSlot) 1f else 0f }
        applyTrackLimits()
        updateRunning()
    }

    override fun onStop() {
        super.onStop()
        players.forEach { it?.pause() }
    }

    override fun onStart() {
        super.onStart()
        running.forEach { players[it]?.play() }
    }

    override fun onDestroy() {
        retry.removeCallbacksAndMessages(null)
        players.forEach { it?.release() }
        super.onDestroy()
    }
}

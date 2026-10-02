package com.poweriptv.app.player

import androidx.compose.foundation.shape.CircleShape
import com.poweriptv.app.ui.components.tvFocus
import android.app.PictureInPictureParams
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Rational
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.ui.PlayerView
import android.content.Intent
import androidx.media3.common.C
import androidx.media3.exoplayer.DefaultRenderersFactory
import com.poweriptv.app.data.PlayerEngine
import com.poweriptv.app.ui.components.CastButton
import com.poweriptv.app.ui.components.CastingBar
import com.poweriptv.app.ui.components.vlcCategoryKey
import android.content.res.Configuration
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.lifecycle.Lifecycle
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.common.Tracks
import androidx.compose.material.icons.filled.AspectRatio
import com.poweriptv.app.data.VideoScale
import com.poweriptv.app.util.DeviceInfo
import com.poweriptv.app.PlayEntry
import com.poweriptv.app.PowerIptvApp
import com.poweriptv.app.data.EpgEntry
import com.poweriptv.app.record.StreamCapture
import com.poweriptv.app.ui.theme.BrandCyan
import com.poweriptv.app.ui.theme.Danger
import com.poweriptv.app.ui.theme.PowerTheme
import com.poweriptv.app.vpn.VpnRequiredException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.text.DateFormat
import java.util.Date

@OptIn(UnstableApi::class)
class PlayerActivity : ComponentActivity() {

    private val container get() = (application as PowerIptvApp).container
    private lateinit var player: ExoPlayer
    private var playerView: PlayerView? = null

    private var title by mutableStateOf("")
    private var error by mutableStateOf<String?>(null)
    private var epg by mutableStateOf<List<EpgEntry>>(emptyList())
    private var showOverlay by mutableStateOf(true)
    /** Letzte Bedienung (Taste/Tipp) – 5 s danach blendet sich die Leiste automatisch aus. */
    private var lastInteraction by mutableLongStateOf(System.currentTimeMillis())
    private var showRecordDialog by mutableStateOf(false)
    private var toast by mutableStateOf<String?>(null)
    private var numberInput by mutableStateOf("")

    // Timeshift
    private var timeshiftJob: Job? = null
    private var timeshiftFile: File? = null
    private var timeshiftActive by mutableStateOf(false)
    private var playingBuffer = false
    private var pausedAt = 0L
    private var timeshiftDelay by mutableLongStateOf(0L)
    private var switchingSource = false
    private val isTv by lazy { DeviceInfo.isTv(this) }
    private var videoScale by mutableStateOf(VideoScale.FIT)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Immer nur EIN Player: ein evtl. noch laufender (z.B. im Mini-Fenster) wird beendet
        active?.get()?.takeIf { it !== this }?.let { old ->
            old.stopPlayback()
            old.finish()
        }
        active = java.lang.ref.WeakReference(this)
        VlcPlayerActivity.closeActive()
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        // Vollbild inkl. Kamera-Aussparung: Video sitzt mittig, kein schwarzer Rand auf der Kameraseite
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            window.attributes = window.attributes.apply {
                layoutInDisplayCutoutMode = android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

        // Online: ueber OkHttp (VPN-Kill-Switch + User-Agent). Lokale Dateien: direkt.
        val dataSourceFactory = DefaultDataSource.Factory(this, OkHttpDataSource.Factory(container.http))
        // Decoder-Fallback: schlaegt der Hardware-Decoder fehl, wird ein anderer versucht
        val renderers = DefaultRenderersFactory(this).setEnableDecoderFallback(true)
        player = ExoPlayer.Builder(this, renderers)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))
            .setSeekBackIncrementMs(10_000)
            .setSeekForwardIncrementMs(30_000)
            .build()
        player.addListener(object : Player.Listener {
            override fun onPlayerError(e: PlaybackException) {
                if (e.errorCode == PlaybackException.ERROR_CODE_DECODER_INIT_FAILED ||
                    e.errorCode == PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED ||
                    e.errorCode == PlaybackException.ERROR_CODE_DECODER_QUERY_FAILED ||
                    e.errorCode == PlaybackException.ERROR_CODE_DECODING_FAILED
                ) {
                    if (switchToVlc()) return
                }
                val cause = generateSequence(e as Throwable) { it.cause }.firstOrNull { it is VpnRequiredException }
                error = cause?.message ?: "Wiedergabe fehlgeschlagen: ${e.errorCodeName}"
            }

            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_READY) error = null
                if (state == Player.STATE_ENDED && current()?.live != true && hasNext()) next()
            }

            override fun onTracksChanged(tracks: Tracks) {
                matchFrameRate()
                // Videospur vorhanden, aber kein Decoder dafuer (z.B. MPEG-2) -> nur Ton, kein Bild
                val video = tracks.groups.filter { it.type == C.TRACK_TYPE_VIDEO }
                val unsupported = video.isNotEmpty() && video.none { g -> (0 until g.length).any { g.isTrackSupported(it) } }
                if (unsupported && !switchToVlc()) {
                    toast = "Videoformat wird von diesem Geraet nicht unterstuetzt – in den Einstellungen \"VLC\" waehlen"
                }
            }

            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                if (switchingSource || reason != Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST) return
                if (current()?.live != true) return
                if (!playWhenReady && !timeshiftActive) startTimeshift()
                else if (playWhenReady && timeshiftActive && !playingBuffer) playTimeshiftBuffer()
            }
        })

        videoScale = container.settings.videoScaleEnum()
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
                                resizeMode = resizeModeFor(videoScale)
                                setControllerVisibilityListener(PlayerView.ControllerVisibilityListener { v ->
                                    showOverlay = v == View.VISIBLE
                                })
                                setShowRewindButton(true)
                                setShowFastForwardButton(true)
                                isFocusable = true
                                // Doppeltipp rechts/links = 10 s vor/zurueck, einfacher Tipp = Steuerung ein/aus.
                                // Tipps auf die Steuerungsknoepfe gehen weiterhin an die Knoepfe.
                                val detector = android.view.GestureDetector(ctx, object : android.view.GestureDetector.SimpleOnGestureListener() {
                                    override fun onDown(e: android.view.MotionEvent): Boolean {
                                        lastInteraction = System.currentTimeMillis()
                                        return true
                                    }
                                    override fun onSingleTapConfirmed(e: android.view.MotionEvent): Boolean {
                                        if (isControllerFullyVisible) hideController() else showController()
                                        return true
                                    }
                                    override fun onDoubleTap(e: android.view.MotionEvent): Boolean {
                                        if (current()?.live == true) return false
                                        if (e.x > width / 2f) seekBy(10_000) else seekBy(-10_000)
                                        return true
                                    }
                                })
                                setOnTouchListener { v, e ->
                                    detector.onTouchEvent(e)
                                    if (e.action == android.view.MotionEvent.ACTION_UP) v.performClick()
                                    true
                                }
                                playerView = this
                            }
                        },
                        update = { it.resizeMode = resizeModeFor(videoScale) },
                        modifier = Modifier.fillMaxSize(),
                    )
                    // Leiste 5 s nach der letzten Bedienung ausblenden (ausser bei Pause/Fehler/Dialog)
                    LaunchedEffect(showOverlay, lastInteraction) {
                        if (!showOverlay) return@LaunchedEffect
                        delay(5000)
                        if (player.isPlaying && error == null && !showRecordDialog) {
                            showOverlay = false
                            playerView?.hideController()
                            playerView?.requestFocus() // Auswahl zurueck aufs Bild -> Tasten wirken direkt
                        }
                    }
                    if (showOverlay) TopOverlay()
                    CastingBar(
                        container,
                        Modifier.align(Alignment.BottomCenter).padding(bottom = 72.dp),
                        onStop = { withSwitch { player.play() } }, // zurueck aufs Handy
                    )
                    if (numberInput.isNotEmpty()) {
                        Text(
                            numberInput, color = Color.White, fontSize = 48.sp, fontWeight = FontWeight.Bold,
                            modifier = Modifier.align(Alignment.TopEnd).padding(32.dp)
                                .clip(RoundedCornerShape(12.dp)).background(Color(0xAA000000)).padding(16.dp),
                        )
                    }
                    toast?.let { msg ->
                        LaunchedEffect(msg) { delay(2500); toast = null }
                        Text(
                            msg, color = Color.White,
                            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 96.dp)
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
                    if (showRecordDialog) RecordDialog()
                }
            }
        }
    }

    @Composable
    private fun TopOverlay() {
        val entry = current()
        LaunchedEffect(title) {
            epg = emptyList()
            val item = entry?.item
            if (entry?.live == true && item != null) {
                // Erst den vollen XMLTV-EPG, sonst Kurz-EPG vom Server
                val now = System.currentTimeMillis()
                val fromXmltv = container.epg.programmesFor(item).filter { it.end > now }.take(2)
                    .map { EpgEntry(it.title, it.description, it.start, it.end) }
                epg = fromXmltv.ifEmpty {
                    runCatching { container.source?.shortEpg(item).orEmpty() }.getOrDefault(emptyList())
                }
            }
        }
        Column(Modifier.fillMaxWidth().background(Color(0x99000000)).padding(horizontal = 16.dp, vertical = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Zurueck zur Uebersicht
                IconButton(modifier = Modifier.tvFocus(CircleShape, 1.15f), onClick = { closePlayer() }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Zurueck zur Uebersicht", tint = Color.White)
                }
                if (container.playQueue.size > 1) {
                    IconButton(modifier = Modifier.tvFocus(CircleShape, 1.15f), onClick = { previous() }) { Icon(Icons.Filled.SkipPrevious, "Vorheriger", tint = Color.White) }
                }
                Text(
                    title, color = Color.White, fontWeight = FontWeight.Bold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
                )
                if (timeshiftActive) {
                    Text(
                        if (playingBuffer) "⏱ -${formatDelay(timeshiftDelay)}" else "⏸ Pausiert (Puffer laeuft)",
                        color = BrandCyan, style = MaterialTheme.typography.labelLarge,
                    )
                    Spacer(Modifier.width(8.dp))
                    OutlinedButton(modifier = Modifier.tvFocus(RoundedCornerShape(50), 1.06f), onClick = { goLive() }) { Text("LIVE", color = Danger, fontWeight = FontWeight.Bold) }
                }
                CastButton(container, entry, entry?.item?.logo, tint = Color.White, onCasting = {
                    withSwitch { player.pause() } // lokal pausieren, laeuft jetzt auf dem TV
                })
                IconButton(modifier = Modifier.tvFocus(CircleShape, 1.15f), onClick = { cycleVideoScale() }) {
                    Icon(Icons.Filled.AspectRatio, "Bildformat", tint = Color.White)
                }
                if (entry?.live == true) {
                    IconButton(modifier = Modifier.tvFocus(CircleShape, 1.15f), onClick = { showRecordDialog = true }) {
                        Icon(Icons.Filled.FiberManualRecord, "Aufnehmen", tint = Danger)
                    }
                }
                if (container.playQueue.size > 1) {
                    IconButton(modifier = Modifier.tvFocus(CircleShape, 1.15f), onClick = { next() }) { Icon(Icons.Filled.SkipNext, "Naechster", tint = Color.White) }
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

    @Composable
    private fun RecordDialog() {
        val entry = current()
        val item = entry?.item
        val now = System.currentTimeMillis()
        val currentProgramme = item?.let { container.epg.current(it, now) }
        fun start(minutes: Int?) {
            if (entry == null) return
            toast = if (minutes == null && currentProgramme != null) {
                container.recordings.schedule(currentProgramme.title, entry.title, entry.url, now, currentProgramme.end, item?.logo)
            } else {
                container.recordings.recordNow(entry.title, entry.title, entry.url, minutes ?: 60, item?.logo)
            }
            showRecordDialog = false
        }
        AlertDialog(
            onDismissRequest = { showRecordDialog = false },
            title = { Text("Aufnahme starten") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (currentProgramme != null) {
                        Button(modifier = Modifier.tvFocus(RoundedCornerShape(50), 1.06f), onClick = { start(null) }) { Text("Bis Sendungsende: ${currentProgramme.title}", maxLines = 1) }
                    }
                    listOf(30, 60, 120, 180).forEach { m ->
                        OutlinedButton(modifier = Modifier.tvFocus(RoundedCornerShape(50), 1.06f), onClick = { start(m) }) { Text("$m Minuten") }
                    }
                }
            },
            confirmButton = { TextButton(modifier = Modifier.tvFocus(RoundedCornerShape(50), 1.06f), onClick = { showRecordDialog = false }) { Text("Abbrechen") } },
        )
    }

    private fun formatDelay(ms: Long): String {
        val s = ms / 1000
        return if (s >= 3600) "%d:%02d:%02d".format(s / 3600, (s / 60) % 60, s % 60) else "%02d:%02d".format(s / 60, s % 60)
    }

    /**
     * Wechsel auf den VLC-Player (nur im Modus "Automatisch"). Der Stream wird gemerkt,
     * damit er beim naechsten Mal direkt mit VLC startet.
     */
    private var switchedToVlc = false

    private fun switchToVlc(): Boolean {
        if (switchedToVlc) return true
        if (container.settings.playerEngineEnum() != PlayerEngine.AUTO) return false
        switchedToVlc = true
        val entry = current() ?: return false
        container.settings.markNeedsVlc(entry.url)
        // Filme/Serien: ganze Kategorie merken (gleiches Dateiformat) -> naechster Titel startet direkt mit VLC
        entry.item?.takeIf { !entry.live && it.categoryId.isNotBlank() }?.let {
            container.settings.markCategoryNeedsVlc(vlcCategoryKey(container.source?.profile?.id, it))
        }
        stopPlayback()
        startActivity(Intent(this, VlcPlayerActivity::class.java).putExtra(VlcPlayerActivity.EXTRA_INFO, "Kompatibilitaetsmodus (VLC)"))
        finish()
        return true
    }

    // ---------- Spulen ----------

    /** 30 s pro Tastendruck, beim Gedrueckthalten immer schneller (bis 5 min). */
    private fun seekStep(repeat: Int): Long = when {
        repeat < 5 -> 30_000L
        repeat < 15 -> 60_000L
        repeat < 30 -> 120_000L
        else -> 300_000L
    }

    private fun seekBy(deltaMs: Long) {
        val duration = player.duration.takeIf { it > 0 } ?: return
        val target = (player.currentPosition + deltaMs).coerceIn(0, duration - 1000)
        withSwitch { player.seekTo(target) }
        val sign = if (deltaMs >= 0) "⏩ +" else "⏪ −"
        toast = "$sign${formatTime(kotlin.math.abs(deltaMs))}  ·  ${formatTime(target)} / ${formatTime(duration)}"
    }

    private fun formatTime(ms: Long): String {
        val s = ms / 1000
        return if (s >= 3600) "%d:%02d:%02d".format(s / 3600, (s / 60) % 60, s % 60) else "%d:%02d".format(s / 60, s % 60)
    }

    // ---------- Bildformat & Bildwiederholrate ----------

    private fun resizeModeFor(v: VideoScale) = when (v) {
        VideoScale.FIT -> AspectRatioFrameLayout.RESIZE_MODE_FIT
        VideoScale.ZOOM -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
        VideoScale.FILL -> AspectRatioFrameLayout.RESIZE_MODE_FILL
    }

    private fun cycleVideoScale() {
        val all = VideoScale.entries
        videoScale = all[(all.indexOf(videoScale) + 1) % all.size]
        playerView?.resizeMode = resizeModeFor(videoScale)
        container.settings.setVideoScale(videoScale)
        toast = "Bildformat: ${videoScale.label}"
    }

    /**
     * AFR: Display-Modus mit passender Bildwiederholrate waehlen
     * (gleiche Aufloesung, Rate = ganzzahliges Vielfaches der Video-FPS, z.B. 25 fps -> 50 Hz).
     */
    private fun matchFrameRate() {
        if (!container.settings.autoFrameRate.value || Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return
        val fps = player.videoFormat?.frameRate ?: return
        if (fps <= 0f) return
        @Suppress("DEPRECATION")
        val display = windowManager.defaultDisplay ?: return
        val current = display.mode
        val best = display.supportedModes
            .filter { it.physicalWidth == current.physicalWidth && it.physicalHeight == current.physicalHeight }
            .mapNotNull { m ->
                val ratio = m.refreshRate / fps
                val n = kotlin.math.round(ratio)
                if (n < 1f) null else m to kotlin.math.abs(ratio - n)
            }
            .filter { it.second < 0.01f }
            .minByOrNull { it.first.refreshRate }
            ?.first ?: return
        if (best.modeId != current.modeId && window.attributes.preferredDisplayModeId != best.modeId) {
            window.attributes = window.attributes.apply { preferredDisplayModeId = best.modeId }
            toast = "Bildwiederholrate: %.0f Hz (Video %.2f fps)".format(best.refreshRate, fps)
        }
    }

    private fun resetDisplayMode() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && window.attributes.preferredDisplayModeId != 0) {
            window.attributes = window.attributes.apply { preferredDisplayModeId = 0 }
        }
    }

    private fun current(): PlayEntry? = container.playQueue.getOrNull(container.playIndex)
    private fun hasNext() = container.playIndex < container.playQueue.lastIndex

    private fun play(index: Int) {
        stopTimeshift()
        val queue = container.playQueue
        if (queue.isEmpty()) { finish(); return }
        container.playIndex = (index + queue.size) % queue.size
        val entry = queue[container.playIndex]
        title = entry.title
        error = null
        entry.item?.let { container.history.add(it) }

        if (container.settings.vpnRequired.value && !container.vpn.isProtected() && !isLocal(entry.url)) {
            error = VpnRequiredException().message
            player.stop()
            return
        }
        val uri = if (isLocal(entry.url)) Uri.fromFile(File(entry.url)) else Uri.parse(entry.url)
        val builder = MediaItem.Builder().setUri(uri)
        if (entry.url.contains(".m3u8", ignoreCase = true)) builder.setMimeType(MimeTypes.APPLICATION_M3U8)
        withSwitch {
            player.setMediaItem(builder.build())
            player.prepare()
            player.playWhenReady = true
        }
    }

    private inline fun withSwitch(block: () -> Unit) {
        switchingSource = true
        try { block() } finally { switchingSource = false }
    }

    // ---------- Timeshift (Live pausieren) ----------

    private fun startTimeshift() {
        val entry = current() ?: return
        val file = File(cacheDir, "timeshift.ts").apply { delete() }
        timeshiftFile = file
        pausedAt = System.currentTimeMillis()
        timeshiftActive = true
        playingBuffer = false
        // Live-Verbindung beenden, Mitschnitt in den Puffer starten
        withSwitch { player.stop() }
        timeshiftJob = lifecycleScope.launch(Dispatchers.IO) {
            runCatching {
                FileOutputStream(file).use { out ->
                    StreamCapture(container.http).capture(entry.url, out, keepGoing = { isActive }, onBytes = { })
                }
            }.onFailure { if (it !is kotlinx.coroutines.CancellationException) toast = "Timeshift-Puffer: ${it.message}" }
        }
        toast = "Live pausiert – Sendung wird gepuffert"
    }

    private fun playTimeshiftBuffer() {
        val file = timeshiftFile ?: return
        timeshiftDelay = System.currentTimeMillis() - pausedAt
        playingBuffer = true
        val factory = DataSource.Factory { GrowingFileDataSource(file) { timeshiftJob?.isActive == true } }
        val source = ProgressiveMediaSource.Factory(factory).createMediaSource(MediaItem.fromUri(Uri.fromFile(file)))
        withSwitch {
            player.setMediaSource(source)
            player.prepare()
            player.playWhenReady = true
        }
    }

    private fun goLive() = play(container.playIndex)

    private fun stopTimeshift() {
        timeshiftJob?.cancel()
        timeshiftJob = null
        timeshiftFile?.delete()
        timeshiftFile = null
        timeshiftActive = false
        playingBuffer = false
    }

    private fun isLocal(url: String) = url.startsWith("/")
    private fun next() = play(container.playIndex + 1)
    private fun previous() = play(container.playIndex - 1)

    // ---------- Fernbedienung ----------

    private var numberJob: Job? = null

    private fun jumpToChannelNumber(number: Int) {
        val queue = container.playQueue
        val idx = queue.indexOfFirst { it.item?.number == number }.takeIf { it >= 0 }
            ?: (number - 1).takeIf { it in queue.indices }
        if (idx != null) play(idx) else toast = "Kanal $number nicht gefunden"
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN) lastInteraction = System.currentTimeMillis()
        if (event.action != KeyEvent.ACTION_DOWN) return super.dispatchKeyEvent(event)
        val live = current()?.live == true
        val controllerVisible = playerView?.isControllerFullyVisible == true
        // Filme/Serien: Spulen direkt per Fernbedienung (unabhaengig vom Fokus)
        if (!live) {
            val step = seekStep(event.repeatCount)
            when (event.keyCode) {
                KeyEvent.KEYCODE_DPAD_LEFT -> if (!controllerVisible) { seekBy(-step / 3); return true }
                KeyEvent.KEYCODE_DPAD_RIGHT -> if (!controllerVisible) { seekBy(step); return true }
                KeyEvent.KEYCODE_MEDIA_REWIND -> { seekBy(-step); return true }
                KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> { seekBy(step); return true }
                KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> { player.playWhenReady = !player.playWhenReady; playerView?.showController(); return true }
            }
        }
        when (event.keyCode) {
            KeyEvent.KEYCODE_CHANNEL_UP, KeyEvent.KEYCODE_PAGE_UP -> { next(); return true }
            KeyEvent.KEYCODE_CHANNEL_DOWN, KeyEvent.KEYCODE_PAGE_DOWN -> { previous(); return true }
            KeyEvent.KEYCODE_DPAD_UP -> if (live && !controllerVisible) { next(); return true }
            KeyEvent.KEYCODE_DPAD_DOWN -> if (live && !controllerVisible) { previous(); return true }
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_BUTTON_A ->
                if (!controllerVisible) { playerView?.showController(); return true }
            KeyEvent.KEYCODE_INFO, KeyEvent.KEYCODE_GUIDE, KeyEvent.KEYCODE_MENU, KeyEvent.KEYCODE_BUTTON_Y -> {
                if (controllerVisible) playerView?.hideController() else playerView?.showController()
                return true
            }
            KeyEvent.KEYCODE_MEDIA_RECORD, KeyEvent.KEYCODE_PROG_RED -> if (live) { showRecordDialog = true; return true }
            KeyEvent.KEYCODE_PROG_GREEN -> if (timeshiftActive) { goLive(); return true }
            KeyEvent.KEYCODE_PROG_BLUE, KeyEvent.KEYCODE_TV_ZOOM_MODE, KeyEvent.KEYCODE_ZOOM_IN -> { cycleVideoScale(); return true }
            KeyEvent.KEYCODE_BUTTON_B -> { closePlayer(); return true }
            KeyEvent.KEYCODE_MEDIA_PAUSE -> if (live && !timeshiftActive) { startTimeshift(); return true }
            KeyEvent.KEYCODE_MEDIA_PLAY -> if (timeshiftActive && !playingBuffer) { playTimeshiftBuffer(); return true }
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> if (live) {
                if (!timeshiftActive) startTimeshift() else if (!playingBuffer) playTimeshiftBuffer() else player.playWhenReady = !player.playWhenReady
                return true
            }
            in KeyEvent.KEYCODE_0..KeyEvent.KEYCODE_9, in KeyEvent.KEYCODE_NUMPAD_0..KeyEvent.KEYCODE_NUMPAD_9 -> if (live) {
                val digit = if (event.keyCode >= KeyEvent.KEYCODE_NUMPAD_0) event.keyCode - KeyEvent.KEYCODE_NUMPAD_0 else event.keyCode - KeyEvent.KEYCODE_0
                numberInput = (numberInput + digit).takeLast(4)
                numberJob?.cancel()
                numberJob = lifecycleScope.launch {
                    delay(1500)
                    numberInput.toIntOrNull()?.let { jumpToChannelNumber(it) }
                    numberInput = ""
                }
                return true
            }
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        // Bild-in-Bild nur auf Handy/Tablet (Fire TV unterstuetzt es fuer Fremd-Apps nicht)
        if (!isTv && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && player.isPlaying) {
            runCatching {
                enterPictureInPictureMode(PictureInPictureParams.Builder().setAspectRatio(Rational(16, 9)).build())
            }
        }
    }

    override fun onStop() {
        super.onStop()
        if (!(Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && isInPictureInPictureMode)) withSwitch { player.pause() }
    }

    /**
     * Mini-Fenster (Bild-in-Bild) wurde verlassen. Ist die Activity danach nicht sichtbar,
     * hat der Nutzer das Fenster mit X geschlossen -> Wiedergabe komplett beenden.
     */
    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        if (!isInPictureInPictureMode && !lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
            closePlayer()
        }
    }

    /** Wiedergabe stoppen (Ton sofort aus). */
    private fun stopPlayback() {
        stopTimeshift()
        withSwitch {
            player.playWhenReady = false
            player.stop()
        }
    }

    /** Player schliessen und zur Uebersicht zurueck. */
    private fun closePlayer() {
        stopPlayback()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && isInPictureInPictureMode) finishAndRemoveTask() else finish()
    }

    override fun onDestroy() {
        if (active?.get() === this) active = null
        resetDisplayMode()
        stopTimeshift()
        player.release()
        super.onDestroy()
    }

    companion object {
        /** Aktuell laufender Player (es darf nur einen geben). */
        private var active: java.lang.ref.WeakReference<PlayerActivity>? = null

        /** Laufenden Player (auch im Mini-Fenster) beenden, z.B. vor dem Multi-Screen. */
        fun closeActive() {
            active?.get()?.let { it.stopPlayback(); it.finish() }
        }
    }
}

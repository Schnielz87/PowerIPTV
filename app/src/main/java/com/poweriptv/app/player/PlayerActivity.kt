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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.focus.focusRequester
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.FormatListBulleted
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
    private var showFormatDialog by mutableStateOf(false)
    /** Live: Senderliste im Bild und zuletzt gesehener Sender (Zurueck-Zappen). */
    private var showChannels by mutableStateOf(false)
    private var lastChannel = -1
    /** Serien: Countdown "Naechste Folge" (Sekunden) bzw. vom Nutzer abgebrochen. */
    private var nextCountdown by mutableStateOf<Int?>(null)
    private var nextCancelled = false
    /** Serien: "Intro ueberspringen" anbieten. */
    private var showSkipIntro by mutableStateOf(false)
    private var introSkipped = false
    /** Zahnrad oben rechts – per Fernbedienung (Hoch) erreichbar. */
    private val gearFocus = androidx.compose.ui.focus.FocusRequester()
    /** Fokus liegt auf einem Knopf der oberen Leiste -> nicht automatisch ausblenden. */
    private var gearHasFocus = false
    /** Kurz eingeblendetes aktives Bildformat. */
    private var formatBadge by mutableStateOf<String?>(null)
    /** Vorschaubilder beim Spulen (pro Titel) und aktuelle Spulposition. */
    private var scrubPreview by mutableStateOf<ScrubPreview?>(null)
    private var scrubPos by mutableStateOf<Long?>(null)
    /** Live-Bild kommt aus der laufenden Aufnahme (spart eine Verbindung zum Anbieter). */
    private var watchingRecording = false
    /** Sender kann nicht geoeffnet werden, weil die Aufnahme die einzige Verbindung belegt. */
    private var blockedRec by mutableStateOf<com.poweriptv.app.record.Recording?>(null)
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
        // Zurueck: sind die Leisten eingeblendet, erst diese ausblenden – erst danach den Player schliessen
        onBackPressedDispatcher.addCallback(this, object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (showChannels) showChannels = false
                else if (nextCountdown != null) cancelNext()
                else if (showOverlay || playerView?.isControllerFullyVisible == true) hideOverlay() else closePlayer()
            }
        })
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
        // Eigener Audio-Pfad: hoert fuer die automatische Vorspann-Erkennung mit (Ton bleibt unveraendert)
        val renderers = object : DefaultRenderersFactory(this) {
            override fun buildAudioSink(
                context: android.content.Context, enableFloatOutput: Boolean, enableAudioTrackPlaybackParams: Boolean,
            ): androidx.media3.exoplayer.audio.AudioSink = androidx.media3.exoplayer.audio.DefaultAudioSink.Builder(context)
                .setEnableFloatOutput(enableFloatOutput)
                .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
                .setAudioProcessors(arrayOf(introFp))
                .build()
        }.setEnableDecoderFallback(true)
        player = ExoPlayer.Builder(this, renderers)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))
            // Grosser Puffer gegen Stocken (v.a. Live TV): bis 60 s vorladen, Start nach 2,5 s,
            // nach einem Aussetzer erst mit 6 s Vorrat weiter (verhindert Dauer-Ruckeln)
            .setLoadControl(
                androidx.media3.exoplayer.DefaultLoadControl.Builder()
                    .setBufferDurationsMs(30_000, 60_000, 2_500, 6_000)
                    .setPrioritizeTimeOverSizeThresholds(true)
                    .build(),
            )
            .setSeekBackIncrementMs(10_000)
            .setSeekForwardIncrementMs(10_000)
            .build()
        player.addListener(object : Player.Listener {
            override fun onPlayerError(e: PlaybackException) {
                // Film bricht ab, waehrend die Spul-Vorschau lief -> Anbieter erlaubt keine 2. Verbindung
                if (scrubPreview?.recentlyUsed() == true && container.settings.scrubPreviewEnum() == ScrubPreviewMode.AUTO) {
                    container.settings.setScrubBlocked(true)
                    scrubPreview?.release(); scrubPreview = null
                    toast = "Vorschaubilder abgeschaltet – dein Anbieter erlaubt beim Spulen keine 2. Verbindung"
                    lifecycleScope.launch { delay(1500); play(container.playIndex) }
                    return
                }
                if (e.errorCode == PlaybackException.ERROR_CODE_DECODER_INIT_FAILED ||
                    e.errorCode == PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED ||
                    e.errorCode == PlaybackException.ERROR_CODE_DECODER_QUERY_FAILED ||
                    e.errorCode == PlaybackException.ERROR_CODE_DECODING_FAILED
                ) {
                    if (switchToVlc()) return
                }
                val cause = generateSequence(e as Throwable) { it.cause }.firstOrNull { it is VpnRequiredException }
                // Netzwerk-Aussetzer: automatisch neu verbinden (Live: an den aktuellen Live-Punkt, Filme: gleiche Stelle)
                val behindLive = e.errorCode == PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW
                val network = e.errorCode in 2000..2999 || behindLive
                if (cause == null && network && netRetries < 5) {
                    netRetries++
                    toast = "Verbindung unterbrochen – verbinde neu ($netRetries/5) …"
                    lifecycleScope.launch {
                        delay(1500)
                        if (behindLive || current()?.live == true) player.seekToDefaultPosition()
                        player.prepare(); player.play()
                    }
                    return
                }
                error = cause?.message ?: "Wiedergabe fehlgeschlagen: ${e.errorCodeName}"
            }

            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_READY) { error = null; netRetries = 0 }
                // Aufnahme ist zu Ende -> wieder normal live schauen
                if (state == Player.STATE_ENDED && current()?.live == true && watchingRecording) { play(container.playIndex); return }
                if (state == Player.STATE_ENDED && current()?.live != true) {
                    current()?.let { container.resume.markWatched(it.url, true) } // zu Ende gesehen
                    if (hasNext() && !nextCancelled) next()
                }
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
                if (watchingRecording) return // Pause = Aufnahmedatei pausieren, kein extra Puffer noetig
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
                                // Steuerung ueber die App-Warteschlange: ⏮/⏭ = Sender bzw. Folge wechseln
                                player = controlPlayer
                                keepScreenOn = true
                                setShowSubtitleButton(false) // Untertitel jetzt im Zahnrad-Menue oben rechts
                                resizeMode = resizeModeFor(videoScale)
                                setControllerVisibilityListener(PlayerView.ControllerVisibilityListener { v ->
                                    showOverlay = v == View.VISIBLE
                                })
                                controllerShowTimeoutMs = 0 // Ausblenden steuert die App selbst (s. hideOverlay)
                                // 10-s-Pfeile nur bei Filmen/Serien (Live-TV ist nicht spulbar)
                                setShowRewindButton(current()?.live != true)
                                setShowFastForwardButton(current()?.live != true)
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
                                // Kein performClick(): PlayerView wuerde die Leiste dabei zusaetzlich ein/aus schalten
                                controllerHideOnTouch = false
                                @Suppress("ClickableViewAccessibility")
                                setOnTouchListener { _, e ->
                                    detector.onTouchEvent(e)
                                    true
                                }
                                // Thumbnail-Scrubbing: beim Ziehen auf dem Zeitstrahl Vorschaubild zeigen
                                findViewById<androidx.media3.ui.DefaultTimeBar>(androidx.media3.ui.R.id.exo_progress)
                                    ?.addListener(object : androidx.media3.ui.TimeBar.OnScrubListener {
                                        override fun onScrubStart(timeBar: androidx.media3.ui.TimeBar, position: Long) = onScrub(position)
                                        override fun onScrubMove(timeBar: androidx.media3.ui.TimeBar, position: Long) = onScrub(position)
                                        override fun onScrubStop(timeBar: androidx.media3.ui.TimeBar, position: Long, canceled: Boolean) {
                                            scrubPos = null
                                            scrubPreview?.pause()
                                        }
                                    })
                                playerView = this
                                // Nach PlayerView registriert -> setzt feste Formate nach jeder Videogroessen-Aenderung erneut
                                this@PlayerActivity.player.addListener(object : Player.Listener {
                                    override fun onVideoSizeChanged(videoSize: androidx.media3.common.VideoSize) = applyAspect()
                                })
                                applyAspect()
                                applySubtitleStyle()
                            }
                        },
                        update = { it.resizeMode = resizeModeFor(videoScale) },
                        modifier = Modifier.fillMaxSize(),
                    )
                    // Leiste 5 s nach der letzten Bedienung ausblenden (ausser bei Pause/Fehler/Dialog)
                    // Leiste 5 s nach der letzten Bedienung ausblenden – einzige Stelle, die das steuert.
                    // Laeuft als Schleife, damit sie auch nach Puffern/Haengern sicher verschwindet.
                    LaunchedEffect(showOverlay) {
                        while (showOverlay) {
                            delay(500)
                            val idle = System.currentTimeMillis() - lastInteraction > 5000
                            val busy = !player.playWhenReady || error != null || showRecordDialog || showFormatDialog || scrubPos != null
                            if (idle && !busy) hideOverlay()
                        }
                    }
                    if (showOverlay) TopOverlay()
                    scrubPos?.let { pos ->
                        val dur = player.duration.takeIf { it > 0 } ?: return@let
                        val p = scrubPreview
                        val frame = p?.frame?.collectAsState()?.value
                        ScrubPreviewBubble(frame, formatTime(pos), pos.toFloat() / dur, 110.dp, showImage = p != null)
                    }
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
                            blockedRec?.let { rec ->
                                Button(
                                    modifier = Modifier.tvFocus(RoundedCornerShape(50), 1.06f),
                                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = Danger),
                                    onClick = {
                                        container.recordings.stop(rec.id)
                                        blockedRec = null
                                        error = null
                                        toast = "Aufnahme gestoppt und gespeichert"
                                        // Verbindung der Aufnahme freigeben lassen, dann diesen Sender starten
                                        lifecycleScope.launch { delay(1500); play(container.playIndex) }
                                    },
                                ) { Text("■ Aufnahme stoppen & diesen Sender schauen") }
                                val idx = container.playQueue.indexOfFirst { it.url == rec.url }
                                if (idx >= 0) OutlinedButton(modifier = Modifier.tvFocus(RoundedCornerShape(50), 1.06f), onClick = { play(idx) }) {
                                    Text("Zurueck zu ${rec.channelName}", color = Color.White)
                                }
                            }
                            if (blockedRec == null) Button(modifier = Modifier.tvFocus(RoundedCornerShape(50), 1.06f), onClick = { play(container.playIndex) }) { Text("Erneut versuchen") }
                        }
                    }
                    if (showChannels) ChannelListPanel(
                        container, container.playQueue, container.playIndex,
                        onSelect = { showChannels = false; if (it != container.playIndex) play(it) },
                        onDismiss = { showChannels = false },
                    )
                    FormatBadge(formatBadge) { formatBadge = null }
                    nextCountdown?.let { sec -> NextEpisodeCard(nextTitle(), sec, onPlay = { next() }, onCancel = { cancelNext() }) }
                    if (showSkipIntro && nextCountdown == null) SkipIntroButton { skipIntro() }
                    LaunchedEffect(Unit) {
                        while (true) {
                            delay(500)
                            if (introDetector.active) {
                                val p = player.currentPosition
                                introFp.enabled = p < com.poweriptv.app.intro.IntroDetector.WINDOW_MS + 5_000
                                introDetector.drain(introFp, p)
                            }
                            if (!watchingRecording && !timeshiftActive) updateEpisodeFlow(player.currentPosition, player.duration, player.isPlaying)
                            checkSleep()
                        }
                    }
                    if (showFormatDialog) PlayerSettingsDialog(
                        audio = trackOptions(C.TRACK_TYPE_AUDIO),
                        subtitles = trackOptions(C.TRACK_TYPE_TEXT),
                        format = videoScale,
                        onAudio = { selectTrack(C.TRACK_TYPE_AUDIO, it) },
                        onSubtitle = { selectTrack(C.TRACK_TYPE_TEXT, it) },
                        onFormat = { changeVideoScale(it) },
                        onDismiss = { showFormatDialog = false; lastInteraction = System.currentTimeMillis() },
                        speed = if (current()?.live == true || timeshiftActive) null else player.playbackParameters.speed,
                        onSpeed = { player.setPlaybackSpeed(it); toast = "Geschwindigkeit: ${it.toString().removeSuffix(".0")}×" },
                        sleepMinutes = sleepMinutesLeft(),
                        onSleep = { setSleep(it) },
                        subtitleSize = container.settings.subtitleSize.value,
                        onSubtitleSize = { container.settings.setSubtitleSize(it); applySubtitleStyle() },
                        subtitleBackground = container.settings.subtitleBackground.value,
                        onSubtitleBackground = { container.settings.setSubtitleBackground(it); applySubtitleStyle() },
                    )
                    if (showRecordDialog) RecordDialog(
                        container, current(),
                        onMessage = { toast = it },
                        onStarted = { switchToRecordingSoon() },
                        onDismiss = { showRecordDialog = false },
                    )
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
        Column(Modifier.fillMaxWidth().onFocusChanged { gearHasFocus = it.hasFocus }.background(Color(0x99000000)).padding(horizontal = 16.dp, vertical = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Zurueck zur Uebersicht
                IconButton(modifier = Modifier.tvFocus(CircleShape, 1.15f), onClick = { closePlayer() }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Zurueck zur Uebersicht", tint = Color.White)
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
                if (entry?.live == true) {
                    IconButton(modifier = Modifier.tvFocus(CircleShape), onClick = { showChannels = true }) {
                        Icon(Icons.Filled.FormatListBulleted, "Senderliste", tint = Color.White)
                    }
                    IconButton(modifier = Modifier.tvFocus(CircleShape), onClick = { zapBack() }) {
                        Icon(Icons.Filled.SwapHoriz, "Letzter Sender", tint = Color.White)
                    }
                }
                if (entry?.item != null) {
                    val favs by container.favorites.favorites.collectAsState()
                    val isFav = favs.any { it.key == entry?.item!!.key }
                    IconButton(modifier = Modifier.tvFocus(CircleShape), onClick = { com.poweriptv.app.ui.components.toggleFavorite(this@PlayerActivity, container, entry?.item!!) }) {
                        Icon(if (isFav) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder, "Favorit", tint = if (isFav) Color(0xFFFF5370) else Color.White)
                    }
                }
                CastButton(container, entry, entry?.item?.logo, tint = Color.White, onCasting = {
                    withSwitch { player.pause() } // lokal pausieren, laeuft jetzt auf dem TV
                })
                IconButton(modifier = Modifier.focusRequester(gearFocus).tvFocus(CircleShape, 1.15f), onClick = { showFormatDialog = true }) {
                    Icon(Icons.Filled.Settings, "Einstellungen (Audio, Untertitel, Bildformat)", tint = Color.White)
                }
                if (entry?.live == true) {
                    IconButton(modifier = Modifier.tvFocus(CircleShape, 1.15f), onClick = { showRecordDialog = true }) {
                        Icon(Icons.Filled.FiberManualRecord, "Aufnehmen", tint = Danger)
                    }
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

    private fun formatDelay(ms: Long): String {
        val s = ms / 1000
        return if (s >= 3600) "%d:%02d:%02d".format(s / 3600, (s / 60) % 60, s % 60) else "%02d:%02d".format(s / 60, s % 60)
    }

    /**
     * Wechsel auf den VLC-Player (nur im Modus "Automatisch"). Der Stream wird gemerkt,
     * damit er beim naechsten Mal direkt mit VLC startet.
     */
    private var switchedToVlc = false
    /** Automatische Neuverbindungen bei Netzwerk-Aussetzern (wird bei laufendem Bild zurueckgesetzt). */
    private var netRetries = 0

    private fun switchToVlc(): Boolean {
        if (switchedToVlc) return true
        if (container.settings.playerEngineEnum() != PlayerEngine.AUTO) return false
        switchedToVlc = true
        val entry = current() ?: return false
        // Nur diesen Stream merken (nicht die ganze Kategorie) – andere Titel bleiben im Standard-Player
        container.settings.markNeedsVlc(entry.url)
        stopPlayback()
        startActivity(Intent(this, VlcPlayerActivity::class.java).putExtra(VlcPlayerActivity.EXTRA_INFO, "Kompatibilitaetsmodus (VLC)"))
        finish()
        return true
    }

    // ---------- Spulen ----------

    /** Immer 10 s pro Tastendruck / Doppeltipp. Groessere Spruenge: Zeitleiste nutzen. */
    private val seekStep = 10_000L

    /** Position des laufenden Films/der Episode merken (Weiterschauen). */
    private fun saveResume() {
        val e = current() ?: return
        if (e.live || playingBuffer || timeshiftActive) return
        val dur = player.duration
        if (dur > 0) container.resume.save(e.url, player.currentPosition, dur)
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
        VideoScale.ZOOM -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
        VideoScale.FILL -> AspectRatioFrameLayout.RESIZE_MODE_FILL
        else -> AspectRatioFrameLayout.RESIZE_MODE_FIT // Original + feste Formate (16:9, 4:3 …)
    }

    /**
     * Feste Formate (16:9, 4:3, 21:9 …): Bildflaeche auf dieses Seitenverhaeltnis setzen.
     * Sonst das echte Seitenverhaeltnis des Videos (PlayerView setzt es bei jeder Videogroesse neu).
     */
    private fun applyAspect() {
        val frame = playerView?.findViewById<AspectRatioFrameLayout>(androidx.media3.ui.R.id.exo_content_frame) ?: return
        val forced = videoScale.ratio
        val v = player.videoSize
        val natural = if (v.width > 0 && v.height > 0) v.width * v.pixelWidthHeightRatio / v.height else 0f
        frame.post { frame.setAspectRatio(forced ?: natural) }
    }

    // ---------- Audio & Untertitel ----------

    private fun trackOptions(type: Int): List<TrackOption> {
        val list = mutableListOf<TrackOption>()
        var anySelected = false
        player.currentTracks.groups.forEachIndexed { g, group ->
            if (group.type != type) return@forEachIndexed
            for (t in 0 until group.length) {
                if (!group.isTrackSupported(t)) continue
                val f = group.getTrackFormat(t)
                val parts = listOfNotNull(
                    f.label?.takeIf { it.isNotBlank() },
                    languageName(f.language).takeIf { f.label.isNullOrBlank() },
                    f.channelCount.takeIf { type == C.TRACK_TYPE_AUDIO && it > 0 }?.let { if (it >= 6) "5.1" else if (it == 2) "Stereo" else "$it Kanaele" },
                    f.sampleMimeType?.substringAfter('/')?.uppercase()?.takeIf { type == C.TRACK_TYPE_AUDIO },
                )
                val sel = group.isTrackSelected(t)
                anySelected = anySelected || sel
                list += TrackOption("$g:$t", parts.joinToString(" · ").ifBlank { "Spur ${list.size + 1}" }, sel)
            }
        }
        if (type == C.TRACK_TYPE_TEXT) list.add(0, TrackOption(OFF_KEY, "Aus", !anySelected))
        return list
    }

    private fun selectTrack(type: Int, option: TrackOption) {
        val params = player.trackSelectionParameters.buildUpon()
        if (option.key == OFF_KEY) {
            params.setTrackTypeDisabled(type, true)
            toast = "Untertitel aus"
        } else {
            val (g, t) = option.key.split(":").map { it.toInt() }
            val group = player.currentTracks.groups.getOrNull(g) ?: return
            params.setTrackTypeDisabled(type, false)
                .setOverrideForType(androidx.media3.common.TrackSelectionOverride(group.mediaTrackGroup, t))
            toast = (if (type == C.TRACK_TYPE_AUDIO) "Audio: " else "Untertitel: ") + option.label
        }
        player.trackSelectionParameters = params.build()
    }

    /** Zum zuletzt gesehenen Sender springen. */
    private fun zapBack() {
        if (lastChannel in container.playQueue.indices) play(lastChannel) else toast = "Noch kein vorheriger Sender"
    }

    private var sleepWarned = false

    private fun setSleep(minutes: Int) {
        sleepWarned = false
        container.sleepUntil = if (minutes <= 0) 0L else System.currentTimeMillis() + minutes * 60_000L
        toast = if (minutes <= 0) "Sleep-Timer aus" else "Sleep-Timer: Wiedergabe endet in $minutes Minuten"
    }

    private fun sleepMinutesLeft(): Int? =
        container.sleepUntil.takeIf { it > 0 }?.let { ((it - System.currentTimeMillis()) / 60_000L + 1).toInt().coerceAtLeast(1) }

    /** Sleep-Timer pruefen: 1 Minute vorher warnen, dann Player schliessen. */
    private fun checkSleep() {
        val until = container.sleepUntil.takeIf { it > 0 } ?: return
        val rem = until - System.currentTimeMillis()
        if (rem <= 0) { container.sleepUntil = 0L; closePlayer(); return }
        if (rem <= 60_000L && !sleepWarned) { sleepWarned = true; toast = "Sleep-Timer: Wiedergabe endet in 1 Minute" }
    }

    /** Untertitel-Groesse und -Hintergrund aus den Einstellungen anwenden. */
    private fun applySubtitleStyle() {
        val sv = playerView?.subtitleView ?: return
        val bg = container.settings.subtitleBackground.value
        sv.setFractionalTextSize(androidx.media3.ui.SubtitleView.DEFAULT_TEXT_SIZE_FRACTION * container.settings.subtitleScale())
        sv.setStyle(
            androidx.media3.ui.CaptionStyleCompat(
                android.graphics.Color.WHITE,
                if (bg) 0xB0000000.toInt() else android.graphics.Color.TRANSPARENT,
                android.graphics.Color.TRANSPARENT,
                if (bg) androidx.media3.ui.CaptionStyleCompat.EDGE_TYPE_NONE else androidx.media3.ui.CaptionStyleCompat.EDGE_TYPE_OUTLINE,
                android.graphics.Color.BLACK,
                null,
            ),
        )
    }

    /**
     * Player-Huelle fuer die Steuerleiste: Die aeusseren Pfeile (⏮/⏭) wechseln Sender bzw. Folge
     * aus der App-Warteschlange (der Player selbst kennt immer nur einen Titel).
     */
    private val controlPlayer by lazy {
        object : androidx.media3.common.ForwardingPlayer(player) {
            private fun multi() = container.playQueue.size > 1
            private val queueCommands = intArrayOf(
                Player.COMMAND_SEEK_TO_PREVIOUS, Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM,
                Player.COMMAND_SEEK_TO_NEXT, Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
            )
            override fun isCommandAvailable(command: Int): Boolean =
                if (multi() && command in queueCommands) true else super.isCommandAvailable(command)
            override fun getAvailableCommands(): Player.Commands =
                if (multi()) super.getAvailableCommands().buildUpon().addAll(*queueCommands).build() else super.getAvailableCommands()
            override fun hasPreviousMediaItem() = multi() || super.hasPreviousMediaItem()
            override fun hasNextMediaItem() = multi() || super.hasNextMediaItem()
            override fun seekToPrevious() { if (multi()) previous() else super.seekToPrevious() }
            override fun seekToNext() { if (multi()) next() else super.seekToNext() }
            override fun seekToPreviousMediaItem() { if (multi()) previous() else super.seekToPreviousMediaItem() }
            override fun seekToNextMediaItem() { if (multi()) next() else super.seekToNextMediaItem() }
        }
    }

    // ---------- Automatische Vorspann-Erkennung (Ton-Fingerabdruck) ----------
    private val introFp = com.poweriptv.app.intro.IntroFingerprinter()
    private val introDetector by lazy { com.poweriptv.app.intro.IntroDetector(File(filesDir, "intro")) }
    private var autoShowFrom = -1L

    private fun autoIntro() = introDetector.detected

    /** Folge beendet/gewechselt: Fingerabdruck speichern und im Hintergrund lernen. */
    private fun finishIntroLearning() {
        val work = runCatching { introDetector.finishEpisode() }.getOrNull() ?: return
        container.scope.launch(Dispatchers.Default) { runCatching { work() } }
    }

    private fun startIntroLearning(entry: PlayEntry) {
        autoShowFrom = -1L
        if (EpisodeFlow.isEpisode(entry)) introDetector.startEpisode(entry.item!!.key, entry.url) else introDetector.stop()
        introFp.enabled = introDetector.active
    }

    private fun skipIntro() {
        introSkipped = true
        showSkipIntro = false
        autoIntro()?.let { seekBy(it.endMs - player.currentPosition); return }
        // Gelerntes Intro-Ende anspringen, sonst Standardsprung
        val learned = current()?.item?.key?.let { container.resume.intro(it) }
        if (learned != null) seekBy(learned.second - lastFlowPos) else seekBy(EpisodeFlow.INTRO_SKIP)
    }

    // Intro lernen: zusammenhaengendes Vorspulen (mehrere Spruenge kurz hintereinander) erkennen
    private var lastFlowPos = 0L
    private var streakStart = -1L
    private var streakEnd = -1L
    private var lastJumpAt = 0L

    private fun learnIntro(pos: Long) {
        val e = current()
        val now = System.currentTimeMillis()
        val delta = pos - lastFlowPos
        if (EpisodeFlow.isEpisode(e) && delta > 3_000L && lastFlowPos > 0) {
            if (streakStart >= 0 && now - lastJumpAt < 4_000L) streakEnd = pos
            else { streakStart = lastFlowPos; streakEnd = pos }
            lastJumpAt = now
        } else if (delta < -3_000L) {
            // Zurueckgespult: Ende der Spulfolge korrigieren
            if (streakStart >= 0 && now - lastJumpAt < 4_000L) { streakEnd = pos; lastJumpAt = now }
        }
        if (streakStart >= 0 && now - lastJumpAt >= 4_000L) {
            val len = streakEnd - streakStart
            if (EpisodeFlow.isEpisode(e) && streakStart < EpisodeFlow.LEARN_WITHIN && len in EpisodeFlow.LEARN_MIN..EpisodeFlow.LEARN_MAX) {
                container.resume.setIntro(e!!.item!!.key, streakStart, streakEnd)
            }
            streakStart = -1L
        }
        lastFlowPos = pos
    }

    private fun cancelNext() {
        nextCancelled = true
        nextCountdown = null
    }

    /** Naechster Titel der Warteschlange (fuer die Countdown-Karte). */
    private fun nextTitle() = container.playQueue.getOrNull(container.playIndex + 1)?.title?.let { EpisodeFlow.episodeLabel(it) }.orEmpty()

    /** Countdown und Intro-Knopf anhand der aktuellen Position aktualisieren (alle 0,5 s). */
    private fun updateEpisodeFlow(pos: Long, dur: Long, isPlaying: Boolean) {
        val e = current()
        nextCountdown = if (e != null && !e.live && hasNext() && dur > 0 && !nextCancelled) {
            val rem = dur - pos
            if (rem in 1..EpisodeFlow.NEXT_BEFORE_END) ((rem + 999) / 1000).toInt() else null
        } else null
        learnIntro(pos)
        // Automatisch erkannter Vorspann (Ton-Vergleich) hat Vorrang: Knopf ab Erkennung 7 s sichtbar
        autoIntro()?.let { auto ->
            if (autoShowFrom < 0) {
                autoShowFrom = maxOf(auto.startMs, pos)
                // Per Ton erkanntes Intro auch als Position merken -> nutzt der VLC-Player bei dieser Serie
                e?.item?.key?.let { container.resume.setIntro(it, auto.startMs, auto.endMs) }
            }
            showSkipIntro = EpisodeFlow.isEpisode(e) && !introSkipped && isPlaying &&
                pos in autoShowFrom..minOf(autoShowFrom + EpisodeFlow.INTRO_SHOW_MS, auto.endMs - 1_500)
            return
        }
        // Gelerntes Intro: Knopf genau zum Intro-Beginn; sonst kurz nach dem Start. Immer nur 7 s sichtbar.
        val showFrom = e?.item?.key?.let { container.resume.intro(it)?.first } ?: EpisodeFlow.INTRO_WINDOW_START
        showSkipIntro = EpisodeFlow.isEpisode(e) && !introSkipped && isPlaying &&
            pos in showFrom..(showFrom + EpisodeFlow.INTRO_SHOW_MS)
    }

    private fun changeVideoScale(v: VideoScale) {
        videoScale = v
        playerView?.resizeMode = resizeModeFor(v)
        applyAspect()
        container.settings.setVideoScale(v)
        formatBadge = v.short
    }

    private fun cycleVideoScale() {
        val all = VideoScale.entries
        changeVideoScale(all[(all.indexOf(videoScale) + 1) % all.size])
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
        saveResume()
        stopTimeshift()
        val queue = container.playQueue
        if (queue.isEmpty()) { finish(); return }
        val prev = container.playIndex
        container.playIndex = (index + queue.size) % queue.size
        if (prev != container.playIndex && queue.getOrNull(prev)?.live == true) lastChannel = prev
        val entry = queue[container.playIndex]
        title = entry.title
        error = null
        finishIntroLearning()
        startIntroLearning(entry)
        // Serien-Komfort zuruecksetzen und Folge als "zuletzt gesehen" merken
        nextCancelled = false; nextCountdown = null; introSkipped = false; showSkipIntro = false
        lastFlowPos = 0L; streakStart = -1L
        if (EpisodeFlow.isEpisode(entry)) container.resume.setLastEpisode(entry.item!!.key, entry.url, EpisodeFlow.episodeLabel(entry.title))
        entry.item?.let { container.history.add(it) }

        if (container.settings.vpnRequired.value && !container.vpn.isProtected() && !isLocal(entry.url)) {
            error = VpnRequiredException().message
            player.stop()
            return
        }
        scrubPreview?.release()
        scrubPreview = ScrubPreview.create(container, entry)
        blockedRec = null
        watchingRecording = false
        playerView?.setShowRewindButton(!entry.live)
        playerView?.setShowFastForwardButton(!entry.live)
        if (entry.live) {
            // Laeuft fuer diesen Sender eine Aufnahme? -> aus der Aufnahme schauen (keine 2. Verbindung)
            container.recordings.activeFor(entry.url)?.let { rec -> playFromRecording(rec); return }
            // Verbindungen des Accounts durch Aufnahmen belegt? -> Hinweis statt Haenger
            RecordingLive.blockedBy(container, entry)?.let { rec ->
                withSwitch { player.stop() }
                blockedRec = rec
                error = RecordingLive.blockedMessage(container, rec)
                return
            }
        }
        val uri = if (isLocal(entry.url)) Uri.fromFile(File(entry.url)) else Uri.parse(entry.url)
        val builder = MediaItem.Builder().setUri(uri)
        if (entry.url.contains(".m3u8", ignoreCase = true)) builder.setMimeType(MimeTypes.APPLICATION_M3U8)
        // Filme/Serien: an der zuletzt gesehenen Stelle weitermachen
        val resumeAt = if (entry.live) 0L else container.resume.get(entry.url)
        if (resumeAt > 0) toast = "▶ Weiter ab ${formatTime(resumeAt)}"
        withSwitch {
            if (resumeAt > 0) player.setMediaItem(builder.build(), resumeAt) else player.setMediaItem(builder.build())
            player.prepare()
            player.playWhenReady = true
        }
    }

    private inline fun withSwitch(block: () -> Unit) {
        switchingSource = true
        try { block() } finally { switchingSource = false }
    }

    private fun onScrub(position: Long) {
        lastInteraction = System.currentTimeMillis()
        // Leiste waehrend des Spulens sicher eingeblendet lassen
        if (playerView?.isControllerFullyVisible == false) playerView?.showController()
        showOverlay = true
        if (current()?.live == true || timeshiftActive || watchingRecording) return
        scrubPos = position
        scrubPreview?.request(position)
    }

    // ---------- Aufnahme + Schauen mit einer Verbindung ----------

    private fun playFromRecording(rec: com.poweriptv.app.record.Recording) {
        val file = File(rec.filePath)
        if (!file.exists()) { lifecycleScope.launch { RecordingLive.awaitData(container, rec.url); play(container.playIndex) }; return }
        watchingRecording = true
        val offset = RecordingLive.liveOffset(file)
        val factory = DataSource.Factory { GrowingFileDataSource(file, { RecordingLive.isGrowing(container, rec.id) }, offset) }
        val source = ProgressiveMediaSource.Factory(factory).createMediaSource(MediaItem.fromUri(Uri.fromFile(file)))
        withSwitch {
            player.setMediaSource(source)
            player.prepare()
            player.playWhenReady = true
        }
        toast = "● Aufnahme laeuft – Bild kommt aus der Aufnahme"
    }

    /** Nach Start einer Aufnahme: Live-Verbindung freigeben und aus der Aufnahme weiterschauen. */
    private fun switchToRecordingSoon() {
        val entry = current() ?: return
        stopTimeshift()
        withSwitch { player.stop() }
        lifecycleScope.launch {
            RecordingLive.awaitData(container, entry.url)
            if (current()?.url == entry.url) play(container.playIndex)
        }
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
        val factory = DataSource.Factory { GrowingFileDataSource(file, isGrowing = { timeshiftJob?.isActive == true }) }
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
        if (showChannels) return super.dispatchKeyEvent(event) // Senderliste bedient sich selbst
        // Serien: OK startet die naechste Folge bzw. ueberspringt das Intro
        if (event.keyCode == KeyEvent.KEYCODE_DPAD_CENTER || event.keyCode == KeyEvent.KEYCODE_ENTER) {
            if (nextCountdown != null) { next(); return true }
            if (showSkipIntro && !controllerVisible) { skipIntro(); return true }
        }
        // Filme/Serien: Spulen direkt per Fernbedienung (unabhaengig vom Fokus)
        if (!live) {
            val step = seekStep
            when (event.keyCode) {
                KeyEvent.KEYCODE_DPAD_LEFT -> if (!controllerVisible) { seekBy(-step); return true }
                KeyEvent.KEYCODE_DPAD_RIGHT -> if (!controllerVisible) { seekBy(step); return true }
                KeyEvent.KEYCODE_MEDIA_REWIND -> { seekBy(-step); return true }
                KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> { seekBy(step); return true }
                KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> { player.playWhenReady = !player.playWhenReady; playerView?.showController(); return true }
            }
        }
        // Runter aus der oberen Leiste -> zurueck in die Player-Steuerung
        if (event.keyCode == KeyEvent.KEYCODE_DPAD_DOWN && gearHasFocus) {
            playerView?.showController(); playerView?.requestFocus(); gearHasFocus = false; return true
        }
        // Hoch aus der unteren Steuerung (nichts mehr darueber) -> Zahnrad/obere Leiste
        if (event.keyCode == KeyEvent.KEYCODE_DPAD_UP && controllerVisible && playerView?.hasFocus() == true) {
            val next = currentFocus?.focusSearch(View.FOCUS_UP)
            val insidePlayer = next != null && generateSequence(next.parent) { it.parent }.any { it === playerView }
            if (!insidePlayer) { showOverlay = true; runCatching { gearFocus.requestFocus() }; return true }
        }
        when (event.keyCode) {
            KeyEvent.KEYCODE_CHANNEL_UP, KeyEvent.KEYCODE_PAGE_UP -> { next(); return true }
            KeyEvent.KEYCODE_CHANNEL_DOWN, KeyEvent.KEYCODE_PAGE_DOWN -> { previous(); return true }
            // Live: Links = Senderliste, Rechts = letzter Sender
            KeyEvent.KEYCODE_DPAD_LEFT -> if (live && !controllerVisible) { showChannels = true; return true }
            KeyEvent.KEYCODE_DPAD_RIGHT -> if (live && !controllerVisible) { zapBack(); return true }
            KeyEvent.KEYCODE_LAST_CHANNEL -> if (live) { zapBack(); return true }
            KeyEvent.KEYCODE_DPAD_UP -> if (live && !controllerVisible) { next(); return true }
            KeyEvent.KEYCODE_DPAD_DOWN -> if (live && !controllerVisible) { previous(); return true }
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_BUTTON_A ->
                if (!controllerVisible) { playerView?.showController(); return true }
            // Menue-Taste: Einstellungen (Audio, Untertitel, Bildformat)
            KeyEvent.KEYCODE_MENU, KeyEvent.KEYCODE_SETTINGS, KeyEvent.KEYCODE_CAPTIONS -> { showFormatDialog = true; return true }
            KeyEvent.KEYCODE_INFO, KeyEvent.KEYCODE_GUIDE, KeyEvent.KEYCODE_BUTTON_Y -> {
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
        saveResume()
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
        saveResume()
        finishIntroLearning()
        introDetector.stop()
        stopTimeshift()
        withSwitch {
            player.playWhenReady = false
            player.stop()
        }
    }

    /** Player schliessen und zur Uebersicht zurueck. */
    /** Beide Leisten (oben: eigene, unten: Player-Steuerung) gemeinsam ausblenden. */
    private fun hideOverlay() {
        showOverlay = false
        gearHasFocus = false
        playerView?.hideController()
        playerView?.requestFocus() // Auswahl zurueck aufs Bild -> Tasten wirken direkt
    }

    private fun closePlayer() {
        stopPlayback()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && isInPictureInPictureMode) finishAndRemoveTask() else finish()
    }

    override fun onDestroy() {
        if (active?.get() === this) active = null
        resetDisplayMode()
        stopTimeshift()
        scrubPreview?.release()
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

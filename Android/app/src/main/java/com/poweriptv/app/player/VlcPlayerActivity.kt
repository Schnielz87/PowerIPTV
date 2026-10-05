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
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ConnectedTv
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.FormatListBulleted
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
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
import com.poweriptv.app.ui.components.CastButton
import com.poweriptv.app.ui.components.CastingBar
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
    private var showRecordDialog by mutableStateOf(false)
    private var showFormatDialog by mutableStateOf(false)
    private var showSendDialog by mutableStateOf(false)
    /** Welcher Teil der Einstellungen offen ist (Zahnrad = MAIN, Knoepfe unten = Bildformat/Tempo/Untertitel). */
    private var dialogSection by mutableStateOf(PlayerSection.MAIN)
    /** Neu zeichnen der Einstellungsleiste nach einer Auswahl. */
    private var panelTick by mutableStateOf(0)
    /** Uebertragen-Dialog offen -> Leiste nicht ausblenden (sonst schliesst sich der Dialog mitten in der Suche). */
    private var castDialogOpen by mutableStateOf(false)
    /** Live: Senderliste im Bild und zuletzt gesehener Sender (Zurueck-Zappen). */
    private var showChannels by mutableStateOf(false)
    /** Jetzt/Weiter des laufenden Senders (Live-Infoleiste unten). */
    private var epg by mutableStateOf<List<com.poweriptv.app.data.EpgEntry>>(emptyList())
    private var lastChannel = -1
    /** Serien: Countdown "Naechste Folge" (Sekunden) bzw. vom Nutzer abgebrochen. */
    private var nextCountdown by mutableStateOf<Int?>(null)
    private var nextCancelled = false
    /** Serien: "Intro ueberspringen" anbieten. */
    private var showSkipIntro by mutableStateOf(false)
    private var introSkipped = false
    /** Letzte Bedienung (Tippen/Taste) – fuer das automatische Ausblenden der Leiste. */
    private var lastInteraction = System.currentTimeMillis()
    /** Vom Nutzer pausiert -> Leiste bleibt stehen. */
    private var userPaused = false
    /** Ein Knopf der Leiste hat den Fokus -> Links/Rechts/OK bedienen die Knoepfe statt zu spulen. */
    private var controlFocused = false
    private var formatBadge by mutableStateOf<String?>(null)
    /** Vorschaubilder beim Spulen (pro Titel). */
    private var scrubPreview by mutableStateOf<ScrubPreview?>(null)
    /** Live-Bild kommt aus der laufenden Aufnahme (spart eine Verbindung zum Anbieter). */
    private var watchingRecording = false
    private var recPipe: android.os.ParcelFileDescriptor? = null
    /** Sender kann nicht geoeffnet werden, weil die Aufnahme die einzige Verbindung belegt. */
    private var blockedRec by mutableStateOf<com.poweriptv.app.record.Recording?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Zurueck: ist die Leiste eingeblendet, erst diese ausblenden – erst danach den Player schliessen
        onBackPressedDispatcher.addCallback(this, object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (showChannels) showChannels = false
                else if (nextCountdown != null) cancelNext()
                else if (showOverlay) showOverlay = false else closePlayer()
            }
        })
        // Immer nur ein Player
        PlayerActivity.closeActive()
        active?.get()?.takeIf { it !== this }?.finish()
        active = java.lang.ref.WeakReference(this)

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

        libVlc = container.vlc // app-weit vorgeladen
        mediaPlayer = MediaPlayer(libVlc)
        mediaPlayer.setEventListener { event ->
            when (event.type) {
                MediaPlayer.Event.Buffering -> buffering = event.buffering < 100f
                MediaPlayer.Event.Playing -> {
                    playing = true; buffering = false; error = null
                    runOnUiThread { updatePip() }
                    if (mediaPlayer.length > 0) applyResume()
                    // Nach dem Spulen im Ein-Verbindungs-Modus: vorher gewaehlte Tonspur wieder setzen
                    scrubAudioTrack?.let { t -> scrubAudioTrack = null; runOnUiThread { runCatching { mediaPlayer.setAudioTrack(t) } } }
                }
                MediaPlayer.Event.Paused, MediaPlayer.Event.Stopped -> { playing = false; runOnUiThread { updatePip() } }
                MediaPlayer.Event.EncounteredError -> runOnUiThread {
                    // Film bricht ab, waehrend die Spul-Vorschau lief -> Anbieter erlaubt keine 2. Verbindung
                    if (scrubPreview?.recentlyUsed() == true && scrubPreview?.exclusive == false && container.settings.scrubPreviewEnum() == ScrubPreviewMode.AUTO) {
                        // Ab jetzt Ein-Verbindungs-Modus: Vorschau bleibt, der Film haelt beim Spulen nur kurz an
                        container.settings.setScrubBlocked(true)
                        scrubPreview?.release(); scrubPreview = ScrubPreview.create(container, current())
                        toast = "Dein Anbieter erlaubt nur 1 Verbindung – Vorschau beim Spulen pausiert den Film jetzt kurz"
                        lifecycleScope.launch { delay(1500); play(container.playIndex) }
                    } else {
                        error = "Wiedergabe fehlgeschlagen (VLC)"; buffering = false
                    }
                }
                MediaPlayer.Event.EndReached -> if (current()?.live == true && watchingRecording) runOnUiThread {
                    play(container.playIndex) // Aufnahme beendet -> wieder normal live
                } else if (current()?.live != true) runOnUiThread {
                    current()?.let { container.resume.markWatched(it.url, true) } // zu Ende gesehen
                    resumeTarget = -1L
                    if (hasNext() && !nextCancelled) next()
                }
                MediaPlayer.Event.LengthChanged -> applyResume()
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
                        // Einmal tippen: Leiste ein/aus. Doppelt rechts: +10 s, doppelt links: -10 s
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onTap = { lastInteraction = System.currentTimeMillis(); showOverlay = !showOverlay },
                                onDoubleTap = { offset ->
                                    if (current()?.live != true) {
                                        if (offset.x > size.width / 2) seekBy(10_000) else seekBy(-10_000)
                                    }
                                },
                            )
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
                    CastingBar(container, Modifier.align(Alignment.Center), onStop = { mediaPlayer.play() })
                    FormatBadge(formatBadge) { formatBadge = null }
                    nextCountdown?.let { sec -> NextEpisodeCard(nextTitle(), sec, onPlay = { next() }, onCancel = { cancelNext() }) }
                    if (showSkipIntro && nextCountdown == null) SkipIntroButton { skipIntro() }
                    LaunchedEffect(Unit) {
                        while (true) {
                            delay(500)
                            if (!watchingRecording) {
                                val t = runCatching { mediaPlayer.time }.getOrDefault(0L)
                                val l = runCatching { mediaPlayer.length }.getOrDefault(0L)
                                updateEpisodeFlow(t, l, playing)
                            }
                            checkSleep()
                            checkStall()
                        }
                    }
                    if (showSendDialog) current()?.let { e ->
                        SendToDeviceDialog(
                            container, e,
                            position = { runCatching { mediaPlayer.time }.getOrDefault(position).takeIf { it > 0 } ?: position },
                            duration = { length },
                            onSent = { name -> showSendDialog = false; sentToDevice(name) },
                            onDismiss = { showSendDialog = false },
                        )
                    }
                    // Zahnrad: Einstellungsleiste rechts (Video, Audio, Untertitel, Untertitel-Stil, Sleep-Timer)
                    @Suppress("UNUSED_VARIABLE") val tick = panelTick // nach jeder Auswahl neu zeichnen
                    if (showFormatDialog && dialogSection == PlayerSection.MAIN) PlayerSettingsPanel(
                        video = vlcTrackList(0), audio = vlcTrackList(1), subtitles = vlcTrackList(2),
                        onVideo = { selectVlcTrack(0, it) }, onAudio = { selectVlcTrack(1, it) }, onSubtitle = { selectVlcTrack(2, it) },
                        subtitleSize = container.settings.subtitleSize.value,
                        // VLC uebernimmt Untertitel-Stil beim (Neu-)Start -> an gleicher Stelle neu starten
                        onSubtitleSize = { container.settings.setSubtitleSize(it); showFormatDialog = false; play(container.playIndex) },
                        subtitleBackground = container.settings.subtitleBackground.value,
                        onSubtitleBackground = { container.settings.setSubtitleBackground(it); showFormatDialog = false; play(container.playIndex) },
                        sleepMinutes = sleepMinutesLeft(),
                        onSleep = { setSleep(it); panelTick++ },
                        onDismiss = { showFormatDialog = false; lastInteraction = System.currentTimeMillis() },
                    )
                    else if (showFormatDialog) PlayerSettingsDialog(
                        audio = vlcTracks(audio = true),
                        subtitles = vlcTracks(audio = false),
                        format = scale,
                        onAudio = { o -> o.key.toIntOrNull()?.let { mediaPlayer.setAudioTrack(it) }; toast = "Audio: ${o.label}" },
                        onSubtitle = { o ->
                            mediaPlayer.setSpuTrack(if (o.key == OFF_KEY) -1 else o.key.toIntOrNull() ?: -1)
                            toast = if (o.key == OFF_KEY) "Untertitel aus" else "Untertitel: ${o.label}"
                        },
                        onFormat = { changeScale(it) },
                        onDismiss = { showFormatDialog = false; lastInteraction = System.currentTimeMillis() },
                        speed = if (current()?.live == true) null else runCatching { mediaPlayer.rate }.getOrDefault(1f),
                        onSpeed = { mediaPlayer.rate = it; toast = "Geschwindigkeit: ${it.toString().removeSuffix(".0")}×" },
                        sleepMinutes = sleepMinutesLeft(),
                        onSleep = { setSleep(it) },
                        subtitleSize = container.settings.subtitleSize.value,
                        // VLC uebernimmt Untertitel-Stil beim (Neu-)Start -> an gleicher Stelle neu starten
                        onSubtitleSize = { container.settings.setSubtitleSize(it); play(container.playIndex) },
                        subtitleBackground = container.settings.subtitleBackground.value,
                        onSubtitleBackground = { container.settings.setSubtitleBackground(it); play(container.playIndex) },
                        section = dialogSection,
                    )
                    if (showRecordDialog) RecordDialog(
                        container, current(),
                        onMessage = { toast = it },
                        onStarted = { switchToRecordingSoon() },
                        onDismiss = { showRecordDialog = false },
                    )
                    if (showOverlay && !inPip) {
                        Overlay()
                        // Wie gewuenscht: links Helligkeit, rechts Lautstaerke (Handy/Tablet)
                        val liveNow = current()?.live == true
                        val liveBar = liveNow && !watchingRecording
                        // Bei offener Senderliste ausgeblendet (sonst regelt Scrollen in der Liste die Helligkeit)
                        val levels = !container.isTvDevice && !showChannels
                        if (levels) PlayerSideLevels(this@VlcPlayerActivity, Modifier.padding(top = 72.dp, bottom = if (liveBar) 16.dp else 130.dp))
                        // Live-TV: unten Sender-Infos (Logo, Jetzt/Weiter) + Mehrfachbildschirm – Seitenverhaeltnis – Senderliste
                        if (liveBar) LiveInfoBar(
                            logo = current()?.item?.logo,
                            epg = epg,
                            formatLabel = scale.short,
                            onChannels = { showChannels = true },
                            onFormat = { dialogSection = PlayerSection.FORMAT; showFormatDialog = true; lastInteraction = System.currentTimeMillis() },
                            onMultiScreen = { startActivity(android.content.Intent(this@VlcPlayerActivity, MultiViewActivity::class.java)) },
                            modifier = Modifier.align(Alignment.BottomCenter),
                            sideInset = if (container.isTvDevice) 0.dp else 80.dp,
                        ) else androidx.compose.foundation.layout.Column(
                            Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            if (!liveNow) SeekBar(Modifier)
                            // Unten: Seitenverhaeltnis – Geschwindigkeit – Untertitel
                            Box(Modifier.fillMaxWidth().background(Color(0x99000000)).padding(bottom = 8.dp, top = 4.dp), contentAlignment = Alignment.Center) {
                                PlayerQuickBar(
                                    formatLabel = scale.short,
                                    speedLabel = if (liveNow) null else runCatching { mediaPlayer.rate }.getOrDefault(1f).let { if (it == 1f) "1×" else "${it.toString().removeSuffix(".0")}×" },
                                    subtitleLabel = panelTick.let { vlcTracks(false) }.firstOrNull { it.selected && it.key != OFF_KEY }?.label ?: "Aus",
                                    onSection = { dialogSection = it; showFormatDialog = true; lastInteraction = System.currentTimeMillis() },
                                    onSubtitleToggle = { toggleSubtitles(); lastInteraction = System.currentTimeMillis() },
                                )
                            }
                        }
                    }
                    // Senderliste (rechts) ueber allem anderen, damit Wischen nur die Liste bewegt
                    if (showChannels) ChannelListPanel(
                        container, container.playQueue, container.playIndex,
                        onSelect = { showChannels = false; if (it != container.playIndex) play(it) },
                        onDismiss = { showChannels = false },
                        onRefreshEpg = { refreshEpg(force = true) },
                        epgLoading = epgLoading,
                    )
                    // Thumbnail-Scrubbing: Vorschau an der Spulposition
                    val drag = dragging
                    if (drag != null && length > 0) {
                        val p = scrubPreview
                        val frame = p?.frame?.collectAsState()?.value
                        ScrubPreviewBubble(frame, formatTime((drag * length).toLong()), drag, 64.dp, showImage = p != null)
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
                            blockedRec?.let { rec ->
                                Button(
                                    modifier = Modifier.tvFocus(RoundedCornerShape(50)),
                                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = com.poweriptv.app.ui.theme.Danger),
                                    onClick = {
                                        container.recordings.stop(rec.id)
                                        blockedRec = null
                                        error = null
                                        toast = "Aufnahme gestoppt und gespeichert"
                                        lifecycleScope.launch { delay(1500); play(container.playIndex) }
                                    },
                                ) { Text("■ Aufnahme stoppen & diesen Sender schauen") }
                                val idx = container.playQueue.indexOfFirst { it.url == rec.url }
                                if (idx >= 0) androidx.compose.material3.OutlinedButton(modifier = Modifier.tvFocus(RoundedCornerShape(50)), onClick = { play(idx) }) {
                                    Text("Zurueck zu ${rec.channelName}", color = Color.White)
                                }
                            }
                            if (blockedRec == null) Button(modifier = Modifier.tvFocus(RoundedCornerShape(50), 1.06f), onClick = { play(container.playIndex) }) { Text("Erneut versuchen") }
                        }
                    }
                }
            }
        }
    }

    @androidx.compose.runtime.Composable
    private fun Overlay() {
        // 5 s nach der letzten Bedienung ausblenden (Schleife: klappt auch nach Puffern sicher)
        LaunchedEffect(showOverlay) {
            while (showOverlay) {
                delay(500)
                val idle = System.currentTimeMillis() - maxOf(lastInteraction, pendingSeekAt) > 5000
                val busy = userPaused || dragging != null || showFormatDialog || showRecordDialog || error != null || castDialogOpen || showSendDialog
                if (idle && !busy) { showOverlay = false; controlFocused = false }
            }
        }
        val live = current()?.live == true
        val multi = container.playQueue.size > 1
        // Gleiches Layout wie der Standard-Player: oben Titel & Optionen, mittig Spulen/Play/Pause
        Box(Modifier.fillMaxSize().onFocusChanged { controlFocused = it.hasFocus }.background(Color(0x66000000))) {
            Row(
                Modifier.fillMaxWidth().background(Color(0x99000000)).padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(modifier = Modifier.tvFocus(CircleShape), onClick = { closePlayer() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Zurueck zur Uebersicht", tint = Color.White) }
                Column(Modifier.weight(1f)) {
                    Text(title, color = Color.White, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (live) {
                    if (watchingRecording) IconButton(modifier = Modifier.tvFocus(CircleShape), onClick = { showChannels = true }) {
                        Icon(Icons.Filled.FormatListBulleted, "Senderliste", tint = Color.White)
                    }
                    IconButton(modifier = Modifier.tvFocus(CircleShape), onClick = { zapBack() }) {
                        Icon(Icons.Filled.SwapHoriz, "Letzter Sender", tint = Color.White)
                    }
                }
                if (current()?.item != null) {
                    val favs by container.favorites.favorites.collectAsState()
                    val isFav = favs.any { it.key == current()?.item!!.key }
                    IconButton(modifier = Modifier.tvFocus(CircleShape), onClick = { com.poweriptv.app.ui.components.toggleFavorite(this@VlcPlayerActivity, container, current()?.item!!) }) {
                        Icon(if (isFav) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder, "Favorit", tint = if (isFav) Color(0xFFFF5370) else Color.White)
                    }
                }
                CastButton(
                    container, current(), current()?.item?.logo, tint = Color.White,
                    onCasting = { mediaPlayer.pause() },
                    onOpenChange = { castDialogOpen = it; if (it) lastInteraction = System.currentTimeMillis() },
                    // Portiva Link: auch hier die Portiva-Geraete (TV-Stick, Tablet, PC) anbieten
                    link = if (canSendToDevice(current()) && !watchingRecording) com.poweriptv.app.ui.components.PortivaLinkTarget(
                        position = { runCatching { mediaPlayer.time }.getOrDefault(position).takeIf { it > 0 } ?: position },
                        duration = { length },
                        onSent = { name -> sentToDevice(name) },
                    ) else null,
                )
                // Portiva Link: auf einem anderen Portiva-Geraet (TV-Stick, Tablet, PC) weiterschauen
                if (canSendToDevice(current()) && !watchingRecording) {
                    IconButton(modifier = Modifier.tvFocus(CircleShape), onClick = { showSendDialog = true }) {
                        Icon(Icons.Filled.ConnectedTv, "An Gerät senden", tint = Color.White)
                    }
                }
                if (live) {
                    IconButton(modifier = Modifier.tvFocus(CircleShape), onClick = { showRecordDialog = true }) {
                        Icon(Icons.Filled.FiberManualRecord, "Aufnehmen", tint = com.poweriptv.app.ui.theme.Danger)
                    }
                }
                IconButton(modifier = Modifier.tvFocus(CircleShape), onClick = { dialogSection = PlayerSection.MAIN; showFormatDialog = true }) { Icon(Icons.Filled.Settings, "Einstellungen (Tonspur, Sleep-Timer)", tint = Color.White) }
            }
            Row(
                Modifier.align(Alignment.Center),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(28.dp),
            ) {
                if (multi) CenterButton(Icons.Filled.SkipPrevious, "Vorheriger", 44.dp) { previous() }
                if (!live) CenterButton(Icons.Filled.Replay10, "10 Sekunden zurueck", 52.dp) { seekBy(-seekStep) }
                CenterButton(if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow, "Wiedergabe/Pause", 72.dp) { togglePause() }
                if (!live) CenterButton(Icons.Filled.Forward10, "10 Sekunden vor", 52.dp) { seekBy(seekStep) }
                if (multi) CenterButton(Icons.Filled.SkipNext, "Naechster", 44.dp) { next() }
            }
        }
    }

    @androidx.compose.runtime.Composable
    private fun CenterButton(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, size: androidx.compose.ui.unit.Dp, onClick: () -> Unit) {
        IconButton(modifier = Modifier.size(size + 16.dp).tvFocus(CircleShape), onClick = { onClick(); showOverlay = true }) {
            Icon(icon, label, tint = Color.White, modifier = Modifier.size(size))
        }
    }

    /** Zeitleiste mit Schieberegler (Filme/Serien). */
    @androidx.compose.runtime.Composable
    private fun SeekBar(modifier: Modifier) {
        LaunchedEffect(Unit) {
            while (true) {
                if (dragging == null && System.currentTimeMillis() - pendingSeekAt > 1500) {
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
                onValueChange = {
                    dragging = it; showOverlay = true; lastInteraction = System.currentTimeMillis()
                    scrubPreview?.let { p ->
                        // Ein-Verbindungs-Modus: Film-Verbindung freigeben, solange die Vorschau laeuft
                        if (p.exclusive && !scrubSuspended) {
                            scrubSuspended = true
                            scrubAudioTrack = runCatching { mediaPlayer.audioTrack }.getOrNull()?.takeIf { t -> t >= 0 }
                            runCatching { mediaPlayer.stop() }
                        }
                        p.request((it * length).toLong())
                    }
                },
                onValueChangeFinished = {
                    val target = dragging?.let { (it * length).toLong() }
                    dragging = null
                    if (scrubSuspended) {
                        val resume = {
                            runOnUiThread {
                                lifecycleScope.launch {
                                    delay(250) // Anbieter kurz Zeit geben, die Vorschau-Verbindung abzumelden
                                    scrubSuspended = false
                                    userPaused = false; stallSince = 0L
                                    if (target != null) { resumeTarget = target; position = target }
                                    else resumeTarget = position
                                    buffering = true
                                    mediaPlayer.play() // gleicher Stream, applyResume springt an die Zielstelle
                                }
                            }
                        }
                        scrubPreview?.pause { resume() } ?: resume()
                    } else {
                        scrubPreview?.pause()
                        target?.let { mediaPlayer.setTime(it, true) } // Zeitleiste: schneller Sprung
                    }
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

    /** Immer 10 s pro Tastendruck / Doppeltipp. Groessere Spruenge: Zeitleiste nutzen. */
    private val seekStep = 10_000L

    /** Startposition fuer "Weiterschauen" (wird gesetzt, sobald VLC die Laenge kennt). */
    private var resumeTarget = -1L
    /** Film fuer die Spul-Vorschau angehalten (Ein-Verbindungs-Modus) + Tonspur, die danach wieder gilt. */
    private var scrubSuspended = false
    private var scrubAudioTrack: Int? = null

    // ---------- Haenger-Waechter ----------
    // Bleibt das Bild stehen (z.B. Anbieter kappt beim Spulen die Verbindung, VLC meldet aber keinen Fehler),
    // wird der Stream automatisch an derselben Stelle neu geladen.
    private var lastTick = -1L
    private var stallSince = 0L
    private var recoveries = 0
    private var lastRecoveryAt = 0L

    private fun checkStall() {
        val e = current() ?: return
        if (scrubSuspended || userPaused || error != null || watchingRecording || blockedRec != null) { stallSince = 0L; return }
        // Nur wenn VLC abspielen will (laeuft oder puffert) – nicht bei Pause/Cast
        if (!runCatching { mediaPlayer.isPlaying }.getOrDefault(false) && !buffering) { stallSince = 0L; return }
        val t = runCatching { mediaPlayer.time }.getOrDefault(-1L)
        val now = System.currentTimeMillis()
        if (t != lastTick && t > 0) { lastTick = t; stallSince = 0L; return }
        if (stallSince == 0L) { stallSince = now; return }
        val limit = if (e.live) 12_000L else 10_000L
        if (now - stallSince < limit) return
        stallSince = 0L
        if (now - lastRecoveryAt > 120_000L) recoveries = 0
        if (++recoveries > 3) { error = "Der Stream hängt – bitte später erneut versuchen"; buffering = false; return }
        lastRecoveryAt = now
        // Ziel: letzter Sprung (falls gerade gespult wurde), sonst die zuletzt laufende Stelle
        val target = if (!e.live) (if (pendingSeek > 0 && now - pendingSeekAt < 30_000) pendingSeek else lastTick).coerceAtLeast(0) else 0L
        toast = "Verbindung hing – wird neu geladen …"
        if (!e.live && target > 0 && length > 0) container.resume.save(e.url, target, length)
        lastTick = -1L
        play(container.playIndex)
    }

    private fun applyResume() {
        val t = resumeTarget
        if (t <= 0) return
        resumeTarget = -1L
        runOnUiThread { mediaPlayer.time = t; position = t; pendingSeek = t; pendingSeekAt = System.currentTimeMillis(); lastFlowPos = 0L /* kein Intro-Lernen */ }
    }

    /** Position des laufenden Films/der Episode merken (Weiterschauen). */
    /** Wiedergabe laeuft jetzt auf einem anderen Portiva-Geraet -> hier beenden. */
    private fun sentToDevice(name: String) {
        saveResume()
        runCatching { mediaPlayer.stop() }
        android.widget.Toast.makeText(this, "Läuft jetzt auf „$name“", android.widget.Toast.LENGTH_SHORT).show()
        finish()
    }

    private fun saveResume() {
        val e = current() ?: return
        if (e.live || resumeTarget > 0) return // noch nicht an die gemerkte Stelle gesprungen
        // Nach einem Abbruch meldet VLC 0/-1 -> letzte bekannte Werte verwenden
        val len = runCatching { mediaPlayer.length }.getOrDefault(0L).takeIf { it > 0 } ?: length
        val time = runCatching { mediaPlayer.time }.getOrDefault(0L).takeIf { it > 0 } ?: position
        if (len > 0) container.resume.save(e.url, time, len)
    }

    /** Letztes Sprungziel: VLC meldet die neue Position verzoegert -> Mehrfachdruck summieren. */
    private var pendingSeek: Long = -1L
    private var pendingSeekAt by mutableStateOf(0L)

    /** Gesammelte Spruenge: erst nach kurzer Pause wirklich spulen (sonst haengt VLC bei vielen Tastendruecken). */
    private var seekJob: kotlinx.coroutines.Job? = null
    private var seekOrigin = -1L

    private fun seekBy(deltaMs: Long) {
        val now = System.currentTimeMillis()
        val pending = pendingSeek >= 0 && now - pendingSeekAt < 2500
        val base = if (pending) pendingSeek else mediaPlayer.time.coerceAtLeast(0)
        if (!pending || seekJob?.isActive != true) seekOrigin = base
        val len = mediaPlayer.length
        var target = (base + deltaMs).coerceAtLeast(0)
        if (len > 0) target = target.coerceAtMost(len - 1000)
        pendingSeek = target
        pendingSeekAt = now
        position = target // Zeitleiste springt sofort mit
        showOverlay = true
        val total = target - seekOrigin
        val sign = if (total >= 0) "⏩ +" else "⏪ −"
        toast = "$sign${formatTime(kotlin.math.abs(total))}  ·  ${formatTime(target)}" +
            if (len > 0) " / ${formatTime(len)}" else ""
        // Erst 0,6 s nach dem letzten Tastendruck einmalig springen
        seekJob?.cancel()
        seekJob = lifecycleScope.launch {
            delay(600)
            // Immer schneller Sprung zum naechsten Schluesselbild (exakt dauert ueber das Internet oft Sekunden)
            mediaPlayer.setTime(pendingSeek, true)
            pendingSeekAt = System.currentTimeMillis()
        }
    }

    private var epgLoading by mutableStateOf(false)

    /** Jetzt/Weiter auffrischen; force = Programmfuehrer neu vom Anbieter laden ("EPG aktualisieren"). */
    private fun refreshEpg(force: Boolean) {
        val entry = current() ?: return
        val item = entry.item ?: return
        if (!entry.live || epgLoading) return
        lastInteraction = System.currentTimeMillis()
        if (force) { epgLoading = true; toast = "EPG wird aktualisiert …" }
        lifecycleScope.launch {
            val list = LiveEpg.refresh(container, item, force)
            if (current()?.url == entry.url && (force || list.isNotEmpty())) epg = list
            if (force) { epgLoading = false; toast = if (list.isEmpty()) "Für diesen Sender liefert der Anbieter kein Programm" else "EPG aktualisiert" }
        }
    }

    private fun current(): PlayEntry? = container.playQueue.getOrNull(container.playIndex)
    private fun hasNext() = container.playIndex < container.playQueue.lastIndex

    private fun play(index: Int) {
        saveResume()
        val queue = container.playQueue
        if (queue.isEmpty()) { finish(); return }
        val prev = container.playIndex
        container.playIndex = (index + queue.size) % queue.size
        if (prev != container.playIndex && queue.getOrNull(prev)?.live == true) lastChannel = prev
        val entry = queue[container.playIndex]
        title = entry.title
        error = null
        buffering = true
        // Live-TV: beim Umschalten immer kurz die Sender-Infos zeigen und Jetzt/Weiter laden
        epg = emptyList()
        if (entry.live) {
            showOverlay = true; lastInteraction = System.currentTimeMillis()
            entry.item?.let { item ->
                lifecycleScope.launch {
                    val list = LiveEpg.quick(container, item)
                    if (current()?.url == entry.url) epg = list
                    refreshEpg(force = false)
                }
            }
        }
        userPaused = false; stallSince = 0L
        // Serien-Komfort zuruecksetzen und Folge als "zuletzt gesehen" merken
        nextCancelled = false; nextCountdown = null; introSkipped = false; showSkipIntro = false
        lastFlowPos = 0L; streakStart = -1L
        if (EpisodeFlow.isEpisode(entry)) container.resume.setLastEpisode(entry.item!!.key, entry.url, EpisodeFlow.episodeLabel(entry.title))
        entry.item?.let { container.history.add(it) }

        scrubPreview?.release()
        scrubPreview = ScrubPreview.create(container, entry)
        scrubSuspended = false; scrubAudioTrack = null
        blockedRec = null
        watchingRecording = false
        recPipe?.let { runCatching { mediaPlayer.stop(); it.close() } }
        recPipe = null
        if (entry.live) {
            // Laeuft fuer diesen Sender eine Aufnahme? -> aus der Aufnahme schauen (keine 2. Verbindung)
            container.recordings.activeFor(entry.url)?.let { rec ->
                if (java.io.File(rec.filePath).exists()) {
                    watchingRecording = true
                    val pfd = RecordingLive.pipe(container, rec)
                    recPipe = pfd
                    val m = Media(libVlc, pfd.fileDescriptor).apply { addOption(":network-caching=1000"); addOption(":demux=ts") }
                    mediaPlayer.media = m
                    m.release()
                    mediaPlayer.play()
                    toast = "● Aufnahme laeuft – Bild kommt aus der Aufnahme"
                    return
                }
            }
            RecordingLive.blockedBy(container, entry)?.let { rec ->
                mediaPlayer.stop()
                buffering = false
                blockedRec = rec
                error = RecordingLive.blockedMessage(container, rec)
                return
            }
        }
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
            // Live: groesserer Puffer gegen Ruckler; Filme/Serien: schneller Start
            if (entry.live) {
                // Live: grosser Puffer + keine starre Taktsynchronisation -> kein Stocken
                addOption(":network-caching=4000")
                addOption(":live-caching=4000")
                addOption(":clock-jitter=0")
                addOption(":clock-synchro=0")
            } else {
                addOption(":network-caching=1500") // kuerzer vorpuffern -> Filme starten schneller
                addOption(":input-fast-seek") // Spulen: naechstes Schluesselbild statt exakt -> deutlich schneller
            }
            // Bricht die Verbindung ab (z.B. beim Spulen), automatisch an derselben Stelle neu verbinden
            if (!local) addOption(":http-reconnect")
            if (local) addOption(":file-caching=300")
            addOption(":http-user-agent=${container.settings.userAgent.value}")
            // Untertitel-Stil (Groesse, dunkler Hintergrund)
            addOption(":sub-text-scale=${(container.settings.subtitleScale() * 100).toInt()}")
            if (container.settings.subtitleBackground.value) {
                addOption(":freetype-background-opacity=176")
                addOption(":freetype-background-color=0")
            }
        }
        // Filme/Serien: an der zuletzt gesehenen Stelle weitermachen
        resumeTarget = if (entry.live) -1L else container.resume.get(entry.url).takeIf { it > 0 } ?: -1L
        if (resumeTarget > 0) toast = "▶ Weiter ab ${formatTime(resumeTarget)}"
        mediaPlayer.media = media
        media.release()
        mediaPlayer.play()
    }

    /** Nach Start einer Aufnahme: Live-Verbindung freigeben und aus der Aufnahme weiterschauen. */
    private fun switchToRecordingSoon() {
        val entry = current() ?: return
        runCatching { mediaPlayer.stop() }
        buffering = true
        lifecycleScope.launch {
            RecordingLive.awaitData(container, entry.url)
            if (current()?.url == entry.url) play(container.playIndex)
        }
    }

    private fun next() = play(container.playIndex + 1)
    private fun previous() = play(container.playIndex - 1)

    private fun togglePause() {
        userPaused = mediaPlayer.isPlaying
        if (mediaPlayer.isPlaying) mediaPlayer.pause() else mediaPlayer.play()
    }

    private fun applyScale() {
        // Feste Formate (16:9, 4:3 …): VLC-Seitenverhaeltnis erzwingen, sonst das des Videos
        mediaPlayer.aspectRatio = scale.vlcRatio
        mediaPlayer.videoScale = when (scale) {
            VideoScale.ZOOM -> MediaPlayer.ScaleType.SURFACE_FIT_SCREEN
            VideoScale.FILL -> MediaPlayer.ScaleType.SURFACE_FILL
            else -> MediaPlayer.ScaleType.SURFACE_BEST_FIT
        }
    }

    /** Audio-/Untertitelspuren von VLC ("Disable" = Aus). */
    private fun vlcTracks(audio: Boolean): List<TrackOption> = vlcTrackList(if (audio) 1 else 2)

    /**
     * Spuren von VLC mit ausfuehrlichen Angaben wie im Vorbild (Codec, kb/s, Aufloesung, Hz, Sprache).
     * kind: 0 = Video, 1 = Audio, 2 = Untertitel. Erste Option "Aus" (Disable).
     */
    private fun vlcTrackList(kind: Int): List<TrackOption> {
        val tracks = runCatching { when (kind) { 0 -> mediaPlayer.videoTracks; 1 -> mediaPlayer.audioTracks; else -> mediaPlayer.spuTracks } }.getOrNull().orEmpty()
        val current = runCatching { when (kind) { 0 -> mediaPlayer.videoTrack; 1 -> mediaPlayer.audioTrack; else -> mediaPlayer.spuTrack } }.getOrDefault(-1)
        val details: Map<Int, org.videolan.libvlc.interfaces.IMedia.Track> = runCatching {
            val m = mediaPlayer.media ?: return@runCatching emptyMap()
            (0 until m.trackCount).mapNotNull { m.getTrack(it) }.associateBy { it.id }
        }.getOrDefault(emptyMap())
        val list = tracks.filter { it.id >= 0 }.mapIndexed { i, t ->
            val d = details[t.id]
            val name = t.name?.takeIf { it.isNotBlank() }?.replace("Track", "Spur")
            val extra = when (d) {
                is org.videolan.libvlc.interfaces.IMedia.VideoTrack -> listOfNotNull(
                    d.codec?.trim()?.takeIf { it.isNotBlank() },
                    d.bitrate.takeIf { it > 0 }?.let { "${it / 1000} kb/s" },
                    if (d.width > 0 && d.height > 0) "${d.width} × ${d.height}" else null,
                )
                is org.videolan.libvlc.interfaces.IMedia.AudioTrack -> listOfNotNull(
                    languageName(d.language),
                    d.codec?.trim()?.takeIf { it.isNotBlank() },
                    d.channels.takeIf { it > 0 }?.let { if (it >= 6) "5.1" else if (it == 2) "Stereo" else "$it Kanäle" },
                    d.bitrate.takeIf { it > 0 }?.let { "${it / 1000} kb/s" },
                    d.rate.takeIf { it > 0 }?.let { "$it Hz" },
                )
                null -> emptyList()
                else -> listOfNotNull(languageName(d.language))
            }
            val label = (listOfNotNull(name.takeIf { extra.isEmpty() || kind == 2 }) + extra).distinct().joinToString(" · ").ifBlank { "Spur ${i + 1}" }
            TrackOption(t.id.toString(), label, t.id == current)
        }
        if (list.isEmpty() && kind != 2) return list
        return listOf(TrackOption(OFF_KEY, "Aus", current < 0)) + list
    }

    /** Spur waehlen (-1 = aus). */
    private fun selectVlcTrack(kind: Int, o: TrackOption) {
        val id = if (o.key == OFF_KEY) -1 else o.key.toIntOrNull() ?: return
        runCatching { when (kind) { 0 -> mediaPlayer.setVideoTrack(id); 1 -> mediaPlayer.setAudioTrack(id); else -> mediaPlayer.setSpuTrack(id) } }
        toast = when (kind) {
            0 -> if (id < 0) "Video aus (nur Ton)" else "Video: ${o.label}"
            1 -> if (id < 0) "Ton aus" else "Audio: ${o.label}"
            else -> if (id < 0) "Untertitel aus" else "Untertitel: ${o.label}"
        }
        panelTick++
    }

    /** Untertitel-Knopf unten: nur ein/aus. */
    private var lastSpu = -1
    private fun toggleSubtitles() {
        val cur = runCatching { mediaPlayer.spuTrack }.getOrDefault(-1)
        if (cur >= 0) { lastSpu = cur; runCatching { mediaPlayer.setSpuTrack(-1) }; toast = "Untertitel aus"; panelTick++; return }
        val tracks = vlcTrackList(2).filter { it.key != OFF_KEY }
        if (tracks.isEmpty()) { toast = "Dieser Stream hat keine Untertitel"; return }
        val pick = tracks.firstOrNull { it.key == lastSpu.toString() } ?: tracks.firstOrNull { it.label.contains("Deutsch", true) || it.label.contains("German", true) } ?: tracks.first()
        selectVlcTrack(2, pick)
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

    private fun autoIntro(): com.poweriptv.app.intro.IntroDetector.Intro? = null
    private var autoShowFrom = -1L

    private fun skipIntro() {
        introSkipped = true
        showSkipIntro = false
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
            if (autoShowFrom < 0) autoShowFrom = maxOf(auto.startMs, pos)
            showSkipIntro = EpisodeFlow.isEpisode(e) && !introSkipped && isPlaying &&
                pos in autoShowFrom..minOf(autoShowFrom + EpisodeFlow.INTRO_SHOW_MS, auto.endMs - 1_500)
            return
        }
        // Gelerntes Intro: Knopf genau zum Intro-Beginn; sonst kurz nach dem Start. Immer nur 7 s sichtbar.
        val showFrom = e?.item?.key?.let { container.resume.intro(it)?.first } ?: EpisodeFlow.INTRO_WINDOW_START
        showSkipIntro = EpisodeFlow.isEpisode(e) && !introSkipped && isPlaying &&
            pos in showFrom..(showFrom + EpisodeFlow.INTRO_SHOW_MS)
    }

    private fun changeScale(v: VideoScale) {
        scale = v
        applyScale()
        container.settings.setVideoScale(v)
        formatBadge = v.short
    }

    private fun cycleScale() {
        val all = VideoScale.entries
        changeScale(all[(all.indexOf(scale) + 1) % all.size])
    }

    private fun closePlayer() {
        saveResume()
        runCatching { mediaPlayer.stop() }
        if (inPip) finishAndRemoveTask() else finish()
    }

    // ---------- Bild-in-Bild (Mini-Fenster beim Verlassen der App) ----------
    private var inPip by mutableStateOf(false)

    private fun videoSize(): Pair<Int, Int> = runCatching {
        val t = mediaPlayer.currentVideoTrack
        if (t == null) 0 to 0 else t.width to t.height
    }.getOrDefault(0 to 0)

    private fun updatePip() {
        val (w, h) = videoSize()
        Pip.update(this, container.isTvDevice, w, h, autoEnter = !userPaused && error == null && current() != null)
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (!userPaused && error == null && current() != null && android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.S) {
            val (w, h) = videoSize()
            Pip.enter(this, container.isTvDevice, w, h)
        }
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: android.content.res.Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        inPip = isInPictureInPictureMode
        if (isInPictureInPictureMode) { showOverlay = false; showChannels = false; showFormatDialog = false }
        // Mini-Fenster mit X geschlossen -> Wiedergabe beenden
        if (!isInPictureInPictureMode && !lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED)) closePlayer()
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action != KeyEvent.ACTION_DOWN) return super.dispatchKeyEvent(event)
        lastInteraction = System.currentTimeMillis()
        if (showChannels) return super.dispatchKeyEvent(event) // Senderliste bedient sich selbst
        // Serien: OK startet die naechste Folge bzw. ueberspringt das Intro
        if (event.keyCode == KeyEvent.KEYCODE_DPAD_CENTER || event.keyCode == KeyEvent.KEYCODE_ENTER) {
            if (nextCountdown != null) { next(); return true }
            if (showSkipIntro && !showOverlay) { skipIntro(); return true }
        }
        val live = current()?.live == true
        if (!live) {
            val step = seekStep
            when (event.keyCode) {
                // Links/Rechts spulen – ausser ein Knopf der Leiste ist angewaehlt (dann normal navigieren)
                KeyEvent.KEYCODE_DPAD_LEFT -> if (!(showOverlay && controlFocused)) { seekBy(-step); return true }
                KeyEvent.KEYCODE_DPAD_RIGHT -> if (!(showOverlay && controlFocused)) { seekBy(step); return true }
                KeyEvent.KEYCODE_MEDIA_REWIND -> { seekBy(-step); return true }
                KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> { seekBy(step); return true }
                // OK: Leiste einblenden, zweites OK = Pause/Weiter
                KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_BUTTON_A -> if (!(showOverlay && controlFocused)) {
                    if (!showOverlay) showOverlay = true else togglePause()
                    return true
                }
            }
        }
        when (event.keyCode) {
            KeyEvent.KEYCODE_CHANNEL_UP, KeyEvent.KEYCODE_PAGE_UP -> { next(); return true }
            KeyEvent.KEYCODE_CHANNEL_DOWN, KeyEvent.KEYCODE_PAGE_DOWN -> { previous(); return true }
            // Live: Rechts = Senderliste (sitzt rechts), Links = letzter Sender
            KeyEvent.KEYCODE_DPAD_RIGHT -> if (live && !showOverlay) { showChannels = true; return true }
            KeyEvent.KEYCODE_DPAD_LEFT -> if (live && !showOverlay) { zapBack(); return true }
            KeyEvent.KEYCODE_LAST_CHANNEL -> if (live) { zapBack(); return true }
            KeyEvent.KEYCODE_DPAD_UP -> if (live && !showOverlay) { next(); return true }
            KeyEvent.KEYCODE_DPAD_DOWN -> if (live && !showOverlay) { previous(); return true }
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_BUTTON_A ->
                if (!showOverlay) { showOverlay = true; return true }
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, KeyEvent.KEYCODE_MEDIA_PLAY, KeyEvent.KEYCODE_MEDIA_PAUSE -> { togglePause(); return true }
            KeyEvent.KEYCODE_PROG_BLUE, KeyEvent.KEYCODE_TV_ZOOM_MODE -> { cycleScale(); return true }
            // Menue-Taste: Einstellungen (Audio, Untertitel, Bildformat)
            KeyEvent.KEYCODE_MENU, KeyEvent.KEYCODE_SETTINGS -> { dialogSection = PlayerSection.MAIN; showFormatDialog = true; return true }
            KeyEvent.KEYCODE_CAPTIONS -> { toggleSubtitles(); return true }
            KeyEvent.KEYCODE_INFO, KeyEvent.KEYCODE_GUIDE -> { showOverlay = !showOverlay; return true }
            KeyEvent.KEYCODE_BUTTON_B -> { closePlayer(); return true }
            KeyEvent.KEYCODE_MEDIA_RECORD, KeyEvent.KEYCODE_PROG_RED -> if (live) { showRecordDialog = true; return true }
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onStop() {
        super.onStop()
        saveResume()
        // Im Mini-Fenster weiterlaufen lassen, sonst beim Verlassen pausieren
        if (!inPip) runCatching { mediaPlayer.pause() }
    }

    override fun onDestroy() {
        if (active?.get() === this) active = null
        runCatching {
            mediaPlayer.stop()
            mediaPlayer.detachViews()
            mediaPlayer.release()
            recPipe?.close()
            scrubPreview?.release()
            // libVlc NICHT freigeben – wird app-weit wiederverwendet
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

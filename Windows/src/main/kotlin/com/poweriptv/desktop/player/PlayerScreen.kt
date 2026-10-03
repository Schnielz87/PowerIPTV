package com.poweriptv.desktop.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CastConnected
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.ConnectedTv
import androidx.compose.runtime.collectAsState
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.FormatListBulleted
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.poweriptv.app.data.ContentType
import com.poweriptv.app.data.Episode
import com.poweriptv.desktop.AppState
import com.poweriptv.desktop.PlayRequest
import com.poweriptv.desktop.data.LibraryStore
import com.poweriptv.desktop.data.WatchEntry
import com.poweriptv.desktop.ui.BrandCyan
import com.poweriptv.desktop.ui.ChannelCard
import com.poweriptv.desktop.ui.NetImage
import com.poweriptv.desktop.ui.Surface
import com.poweriptv.desktop.ui.formatTime
import com.poweriptv.desktop.ui.handCursor
import com.poweriptv.desktop.ui.screens.AspectModes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.skia.FilterMipmap
import org.jetbrains.skia.FilterMode
import org.jetbrains.skia.MipmapMode
import org.jetbrains.skia.Rect
import java.awt.Point
import java.awt.Toolkit
import java.awt.image.BufferedImage

private const val NEXT_BEFORE_END = 40_000L
private const val HIDE_AFTER = 3_000L

private val BlankCursor: PointerIcon by lazy {
    PointerIcon(Toolkit.getDefaultToolkit().createCustomCursor(BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB), Point(0, 0), "leer"))
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun PlayerScreen(app: AppState, req: PlayRequest) {
    val ctl = remember { app.playerController() }
    remember(app.settings.value.subtitleSize, app.settings.value.subtitleBackground) {
        ctl.subtitleScale = when (app.settings.value.subtitleSize) { "KLEIN" -> 0.8f; "GROSS" -> 1.3f; "SEHR_GROSS" -> 1.6f; else -> 1f }
        ctl.subtitleBackground = app.settings.value.subtitleBackground
    }
    var toast by remember { mutableStateOf<String?>(null) }
    var showRecord by remember { mutableStateOf(false) }
    var introSkipped by remember(req.url) { mutableStateOf(false) }
    val favorites by (app.library?.favorites ?: kotlinx.coroutines.flow.MutableStateFlow(emptyList())).collectAsState()
    val current by rememberUpdatedState(req)
    val isLive = req.isLive
    val aspect = app.playerAspect
    var lastMove by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var menuOpen by remember { mutableStateOf(false) }
    var showChannels by remember { mutableStateOf(false) }
    var episodes by remember(req.item.key) { mutableStateOf(req.episodes) }
    var nextDismissed by remember(req.url) { mutableStateOf(false) }
    var dragging by remember { mutableStateOf<Float?>(null) }
    val scrubScope = androidx.compose.runtime.rememberCoroutineScope()
    val focus = remember { FocusRequester() }

    // --- Hilfsfunktionen ---
    fun episodeKey(r: PlayRequest) = r.episode?.let { LibraryStore.episodeKey(it.id) } ?: r.item.key

    fun savePosition() {
        val r = current
        if (r.item.type == ContentType.LIVE || r.catchup || ctl.length <= 0) return
        val ep = r.episode
        app.library?.savePosition(
            episodeKey(r), ctl.time, ctl.length,
            WatchEntry(
                item = r.item, episodeId = ep?.id, episodeTitle = ep?.title, season = ep?.season,
                episodeNum = ep?.episodeNum, episodeExt = ep?.containerExtension,
            ),
        )
    }

    fun nextEpisode(): Episode? {
        val ep = current.episode ?: return null
        val idx = episodes.indexOfFirst { it.id == ep.id }
        return if (idx >= 0) episodes.getOrNull(idx + 1) else null
    }

    fun playNext() {
        val next = nextEpisode() ?: return
        savePosition()
        app.playEpisode(current.item, next, episodes, current.seriesCover, 0L)
    }

    fun zapBack() {
        val last = app.lastChannel
        if (last != null) app.play(last, channels = current.channels) else toast = "Noch kein vorheriger Sender"
    }

    fun setSleep(minutes: Int) {
        app.sleepUntil = if (minutes <= 0) 0L else System.currentTimeMillis() + minutes * 60_000L
        toast = if (minutes <= 0) "Sleep-Timer aus" else "Sleep-Timer: Wiedergabe endet in $minutes Minuten"
    }

    // --- Intro ueberspringen + lernen (wie Android: Vorspulen in den ersten 10 Min. um 20 s – 5 Min.) ---
    val isEpisode = req.episode != null
    var lastFlowPos by remember(req.url) { mutableStateOf(0L) }
    var streakStart by remember(req.url) { mutableStateOf(-1L) }
    var streakEnd by remember(req.url) { mutableStateOf(-1L) }
    var lastJumpAt by remember(req.url) { mutableStateOf(0L) }
    fun learnIntro(pos: Long) {
        val t = System.currentTimeMillis()
        val delta = pos - lastFlowPos
        if (isEpisode && delta > 3_000L && lastFlowPos > 0) {
            if (streakStart >= 0 && t - lastJumpAt < 4_000L) streakEnd = pos else { streakStart = lastFlowPos; streakEnd = pos }
            lastJumpAt = t
        } else if (delta < -3_000L && streakStart >= 0 && t - lastJumpAt < 4_000L) {
            streakEnd = pos; lastJumpAt = t
        }
        if (streakStart >= 0 && t - lastJumpAt >= 4_000L) {
            val len = streakEnd - streakStart
            if (isEpisode && streakStart < 600_000L && len in 20_000L..300_000L) app.library?.setIntro(current.item.key, streakStart, streakEnd)
            streakStart = -1L
        }
        lastFlowPos = pos
    }
    fun skipIntro() {
        introSkipped = true
        val learned = app.library?.intro(current.item.key)
        if (learned != null) ctl.seekTo(learned.second) else ctl.seekBy(85_000L)
    }

    fun zap(delta: Int) {
        val list = current.channels
        if (list.isEmpty()) return
        val idx = list.indexOfFirst { it.key == current.item.key }
        val target = list[((if (idx < 0) 0 else idx) + delta).mod(list.size)]
        app.play(target, channels = list)
    }

    fun close() {
        savePosition()
        if (!app.settings.value.startFullscreen) app.setFullscreen(false)
        app.closePlayer()
    }

    fun poke() { lastMove = System.currentTimeMillis() }

    // --- Lebenszyklus ---
    LaunchedEffect(req.url) {
        // Beim Wechsel Fenster <-> Vollbild laeuft der Stream einfach weiter
        if (ctl.currentUrl != req.url) ctl.play(req.url, req.startAt, live = req.isLive)
        poke()
        runCatching { focus.requestFocus() }
    }
    // Serie ohne Episodenliste (z.B. aus "Weiterschauen"): Liste nachladen fuer "Naechste Folge"
    LaunchedEffect(req.item.key) {
        if (req.item.type == ContentType.SERIES && episodes.isEmpty()) {
            val info = withContext(Dispatchers.IO) { runCatching { app.source?.seriesInfo(req.item) }.getOrNull() }
            info?.episodes?.values?.flatten()?.let { episodes = it }
        }
    }
    ctl.onFinished = {
        if (current.episode != null && nextEpisode() != null && app.settings.value.autoNextEpisode) playNext()
        else if (!isLive) {
            savePosition(); close()
        }
    }
    // Position regelmaessig sichern + Uhr fuer das Ausblenden
    LaunchedEffect(Unit) {
        var tick = 0
        while (true) {
            delay(500)
            now = System.currentTimeMillis()
            if (++tick % 20 == 0 && ctl.playing) savePosition()
            if (ctl.playing && ctl.length > 0) learnIntro(ctl.time)
            ctl.watchdog()?.let { toast = it }
            // Sleep-Timer: 1 Minute vorher warnen, dann Player schliessen
            val until = app.sleepUntil
            if (until > 0) {
                val rem = until - now
                if (rem <= 0) { app.sleepUntil = 0L; close() }
                else if (rem in 59_500L..60_000L) toast = "Sleep-Timer: Wiedergabe endet in 1 Minute"
            }
        }
    }
    // Neues Videobild pro Bildschirm-Frame abholen
    LaunchedEffect(Unit) {
        while (true) withFrameNanos { ctl.pollFrame() }
    }

    val overlayVisible = menuOpen || showChannels || !ctl.playing || ctl.error != null || now - lastMove < HIDE_AFTER
    val next = nextEpisode()
    val remaining = ctl.length - ctl.time
    val showNextCard = next != null && !nextDismissed && ctl.length > 0 && remaining in 1..NEXT_BEFORE_END && app.settings.value.autoNextEpisode

    Box(
        Modifier.fillMaxSize().background(Color.Black)
            .focusRequester(focus).focusable()
            .pointerHoverIcon(if (overlayVisible) PointerIcon.Default else BlankCursor)
            .onPointerEvent(PointerEventType.Move) { poke() }
            .onPointerEvent(PointerEventType.Scroll) { e ->
                val dy = e.changes.firstOrNull()?.scrollDelta?.y ?: 0f
                if (dy != 0f) { ctl.changeVolume(if (dy < 0) 5 else -5); poke() }
            }
            .onPreviewKeyEvent { e ->
                if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                poke()
                when (e.key) {
                    Key.Spacebar, Key.K, Key.MediaPlayPause -> { ctl.togglePause(); true }
                    Key.DirectionLeft -> { if (!isLive) ctl.seekBy(if (e.isShiftPressed) -60_000 else -10_000); true }
                    Key.DirectionRight -> { if (!isLive) ctl.seekBy(if (e.isShiftPressed) 60_000 else 10_000); true }
                    Key.DirectionUp -> { if (isLive && current.channels.isNotEmpty()) zap(-1) else ctl.changeVolume(5); true }
                    Key.DirectionDown -> { if (isLive && current.channels.isNotEmpty()) zap(1) else ctl.changeVolume(-5); true }
                    Key.PageUp, Key.ChannelUp -> { zap(-1); true }
                    Key.PageDown, Key.ChannelDown -> { zap(1); true }
                    Key.M -> { ctl.toggleMute(); true }
                    // Z = Zoomen an/aus (Kino-Balken oben/unten wegschneiden)
                    Key.Z -> { app.playerAspect = if (app.playerAspect == "fill") "fit" else "fill"; true }
                    Key.F, Key.F11 -> { app.toggleFullscreen(); true }
                    Key.L -> { if (isLive) showChannels = !showChannels; true }
                    Key.N -> { playNext(); true }
                    Key.B -> { if (isLive) zapBack(); true }
                    Key.R -> { if (isLive) showRecord = true; true }
                    Key.Enter -> { if (showSkipIntroNow(app, req, ctl, introSkipped)) { skipIntro(); true } else false }
                    Key.Escape -> {
                        when {
                            showChannels -> showChannels = false
                            app.isFullscreen -> app.setFullscreen(false)
                            else -> close()
                        }
                        true
                    }
                    Key.Backspace -> { close(); true }
                    else -> false
                }
            },
    ) {
        // Video
        Canvas(
            Modifier.fillMaxSize().pointerInput(Unit) {
                detectTapGestures(
                    onTap = { ctl.togglePause(); poke(); runCatching { focus.requestFocus() } },
                    onDoubleTap = { app.toggleFullscreen() },
                )
            },
        ) {
            @Suppress("UNUSED_VARIABLE") val frame = ctl.frameCounter // neu zeichnen bei jedem Bild
            val img = ctl.currentImage() ?: return@Canvas
            val vw = img.width.toFloat() * ctl.pixelAspect
            val vh = img.height.toFloat()
            val W = size.width; val H = size.height
            val dst: Rect = when (aspect) {
                "stretch" -> Rect.makeWH(W, H)
                else -> {
                    val ar = when (aspect) { "16:9" -> 16f / 9f; "4:3" -> 4f / 3f; else -> vw / vh }
                    val fitScale = if (aspect == "fill") maxOf(W / ar, H) else minOf(W / ar, H)
                    val h = fitScale; val w = h * ar
                    Rect.makeXYWH((W - w) / 2f, (H - h) / 2f, w, h)
                }
            }
            drawIntoCanvas { c ->
                c.nativeCanvas.drawImageRect(
                    img, Rect.makeWH(img.width.toFloat(), img.height.toFloat()), dst,
                    FilterMipmap(FilterMode.LINEAR, MipmapMode.NONE), null, true,
                )
            }
        }

        // Laden / Fehler
        if (!ctl.available) {
            CenterMessage("Der Player konnte nicht gestartet werden.\n${ctl.initError}", onClose = ::close)
        } else if (ctl.error != null) {
            CenterMessage(ctl.error!!, onRetry = { ctl.retry() }, onClose = ::close)
        } else if (ctl.buffering) {
            CircularProgressIndicator(Modifier.align(Alignment.Center).size(56.dp), color = BrandCyan, strokeWidth = 4.dp)
        }

        // Bedienelemente
        AnimatedVisibility(overlayVisible, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize()) {
                // oben
                Row(
                    Modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.8f), Color.Transparent)))
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RoundIcon(Icons.AutoMirrored.Filled.ArrowBack, "Zurück", onClick = ::close)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (isLive) {
                                Text(
                                    "LIVE", fontSize = 11.sp, fontWeight = FontWeight.Bold,
                                    modifier = Modifier.clip(RoundedCornerShape(4.dp)).background(Color(0xFFE53935)).padding(horizontal = 6.dp, vertical = 2.dp),
                                )
                                Spacer(Modifier.width(8.dp))
                            }
                            Text(req.title, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        val epgLine = if (isLive) {
                            val cur = app.epg.current(req.item, now)
                            val nxt = app.epg.next(req.item, now)
                            listOfNotNull(cur?.let { "Jetzt: ${it.title}" }, nxt?.let { "Danach: ${it.title}" }).joinToString("   ·   ").ifBlank { null }
                        } else null
                        (req.subtitle ?: epgLine)?.let { Text(it, color = Color.White.copy(alpha = 0.8f), maxLines = 1, overflow = TextOverflow.Ellipsis) }
                    }
                    formatBadge(ctl.videoHeight)?.let {
                        Text(it, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.clip(RoundedCornerShape(4.dp)).background(Color.White.copy(alpha = 0.18f)).padding(horizontal = 8.dp, vertical = 3.dp))
                        Spacer(Modifier.width(8.dp))
                    }
                    // Favorit, Aufnahme, Zap zurueck (wie Android)
                    val fav = favorites.any { it.key == req.item.key }
                    if (!req.catchup) RoundIcon(if (fav) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder, if (fav) "Aus Favoriten" else "Zu Favoriten") {
                        app.library?.toggleFavorite(req.item); toast = if (fav) "Aus Favoriten entfernt" else "Zu Favoriten hinzugefügt"
                    }
                    if (isLive) {
                        val rec = app.recordings.activeFor(req.url)
                        RoundIcon(Icons.Filled.FiberManualRecord, if (rec != null) "Aufnahme läuft – stoppen" else "Aufnehmen (R)", tint = if (rec != null) Color(0xFFFF4D5E) else Color.White) {
                            if (rec != null) { app.recordings.stop(rec.id); toast = "Aufnahme gestoppt" } else showRecord = true
                        }
                        RoundIcon(Icons.Filled.History, "Zum vorherigen Sender (B)") { zapBack() }
                    }
                    CastMenu(ctl, onOpenChange = { menuOpen = it; poke() }) { toast = it }
                    LinkSendMenu(app, req, ctl, onOpenChange = { menuOpen = it; poke() }) { toast = it }
                    SettingsMenu(ctl, aspect, isLive, onAspect = { app.playerAspect = it }, onOpenChange = { menuOpen = it; poke() },
                        sleepMinutes = app.sleepUntil.takeIf { it > 0 }?.let { ((it - now) / 60_000L + 1).toInt() }, onSleep = ::setSleep)
                    if (isLive && req.channels.isNotEmpty()) RoundIcon(Icons.Filled.FormatListBulleted, "Senderliste (L)") { showChannels = !showChannels }
                    RoundIcon(if (app.isFullscreen) Icons.Filled.FullscreenExit else Icons.Filled.Fullscreen, "Vollbild (F)") { app.toggleFullscreen() }
                }

                // Mitte
                Row(Modifier.align(Alignment.Center), horizontalArrangement = Arrangement.spacedBy(36.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (isLive) {
                        if (req.channels.isNotEmpty()) RoundIcon(Icons.Filled.SkipPrevious, "Vorheriger Sender", big = false) { zap(-1) }
                    } else {
                        RoundIcon(Icons.Filled.Replay10, "10 s zurück", big = false) { ctl.seekBy(-10_000) }
                    }
                    if (!ctl.buffering || !ctl.playing) {
                        RoundIcon(if (ctl.playing) Icons.Filled.Pause else Icons.Filled.PlayArrow, "Pause / Weiter", big = true) { ctl.togglePause() }
                    } else Spacer(Modifier.size(84.dp))
                    if (isLive) {
                        if (req.channels.isNotEmpty()) RoundIcon(Icons.Filled.SkipNext, "Nächster Sender", big = false) { zap(1) }
                    } else {
                        RoundIcon(Icons.Filled.Forward10, "10 s vor", big = false) { ctl.seekBy(10_000) }
                    }
                }

                // unten
                Column(
                    Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                        .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f))))
                        .padding(horizontal = 22.dp, vertical = 14.dp),
                ) {
                    if (!isLive && ctl.length > 0) {
                        val shown = dragging ?: (ctl.time.toFloat() / ctl.length).coerceIn(0f, 1f)
                        ScrubPreviewBubble(app, req, ctl, dragging)
                        Slider(
                            value = shown,
                            onValueChange = {
                                dragging = it; poke()
                                // Nur 1 Verbindung erlaubt: Film waehrend der Vorschau anhalten (keine 2. Verbindung)
                                if (scrubPreviewMode(app, req) == ScrubMode.EXCLUSIVE) ctl.suspendForPreview()
                            },
                            onValueChangeFinished = {
                                val target = dragging?.let { (it * ctl.length).toLong() }
                                dragging = null
                                if (ctl.previewSuspended) {
                                    scrubScope.launch {
                                        delay(700) // Vorschau-Verbindung ist dann sicher geschlossen
                                        ctl.resumeAfterPreview(target ?: 0L)
                                    }
                                } else target?.let { ctl.seekTo(it) }
                            },
                            colors = SliderDefaults.colors(thumbColor = BrandCyan, activeTrackColor = BrandCyan, inactiveTrackColor = Color.White.copy(alpha = 0.3f)),
                            modifier = Modifier.fillMaxWidth().handCursor(),
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (!isLive && ctl.length > 0) {
                            val t = dragging?.let { (it * ctl.length).toLong() } ?: ctl.time
                            Text("${formatTime(t)} / ${formatTime(ctl.length)}", fontSize = 14.sp)
                        }
                        Spacer(Modifier.weight(1f))
                        if (next != null) {
                            OutlinedButton(onClick = { playNext() }, modifier = Modifier.handCursor()) {
                                Icon(Icons.Filled.SkipNext, null); Spacer(Modifier.width(6.dp)); Text("Nächste Folge")
                            }
                            Spacer(Modifier.width(16.dp))
                        }
                        IconButton(onClick = { ctl.toggleMute() }, modifier = Modifier.handCursor()) {
                            Icon(if (ctl.muted || ctl.volume == 0) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp, "Ton", tint = Color.White)
                        }
                        Slider(
                            value = ctl.volume.toFloat(), onValueChange = { ctl.setVolumeTo(it.toInt()); poke() }, valueRange = 0f..150f,
                            colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = Color.White, inactiveTrackColor = Color.White.copy(alpha = 0.3f)),
                            modifier = Modifier.width(140.dp).handCursor(),
                        )
                        Text("${ctl.volume}%", fontSize = 12.sp, modifier = Modifier.width(44.dp).padding(start = 6.dp))
                    }
                }
            }
        }

        // Intro ueberspringen (7 s sichtbar; gelerntes Intro: genau zum Intro-Beginn)
        if (showSkipIntroNow(app, req, ctl, introSkipped)) {
            androidx.compose.material3.Button(
                onClick = { skipIntro() },
                modifier = Modifier.align(Alignment.BottomEnd).padding(end = 28.dp, bottom = 120.dp).handCursor(),
            ) { Text("Intro überspringen  ⏭") }
        }
        // Hinweise (Toast)
        toast?.let { msg ->
            LaunchedEffect(msg) { delay(2500); toast = null }
            Text(
                msg, color = Color.White,
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 90.dp).clip(RoundedCornerShape(10.dp)).background(Color.Black.copy(alpha = 0.75f)).padding(horizontal = 16.dp, vertical = 10.dp),
            )
        }
        if (showRecord) {
            RecordDialog(app, req, onDismiss = { showRecord = false }) { toast = it; showRecord = false }
        }

        // Naechste Folge (letzte 40 Sekunden)
        if (showNextCard && next != null) {
            NextEpisodeCard(
                next, secondsLeft = (remaining / 1000).toInt(),
                onPlay = { playNext() }, onDismiss = { nextDismissed = true },
                modifier = Modifier.align(Alignment.BottomEnd).padding(end = 28.dp, bottom = 120.dp),
            )
        }

        // Senderliste
        if (showChannels && req.channels.isNotEmpty()) {
            val list = req.channels
            val idx = list.indexOfFirst { it.key == req.item.key }.coerceAtLeast(0)
            val state = rememberLazyListState(idx)
            LaunchedEffect(idx) { state.scrollToItem((idx - 3).coerceAtLeast(0)) }
            Column(
                Modifier.align(Alignment.CenterEnd).width(380.dp).fillMaxHeight().background(Surface.copy(alpha = 0.95f)).padding(12.dp),
            ) {
                Text("Sender", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(6.dp))
                LazyColumn(state = state, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    itemsIndexed(list, key = { _, it -> it.key }) { _, ch ->
                        ChannelCard(ch, onClick = { app.play(ch, channels = list) }, selected = ch.key == req.item.key)
                    }
                }
            }
        }
    }
}

private fun formatBadge(h: Int): String? = when {
    h <= 0 -> null
    h >= 2000 -> "4K"
    h >= 1000 -> "Full HD"
    h >= 700 -> "HD"
    else -> "SD"
}

@Composable
private fun RoundIcon(icon: ImageVector, desc: String, big: Boolean? = null, tint: Color = Color.White, onClick: () -> Unit) {
    val size = when (big) { true -> 84.dp; false -> 60.dp; null -> 44.dp }
    Box(
        Modifier.size(size).clip(CircleShape).background(Color.Black.copy(alpha = if (big == null) 0.35f else 0.5f))
            .handCursor().clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, desc, tint = tint, modifier = Modifier.size(size * 0.55f))
    }
}

@Composable
private fun SettingsMenu(
    ctl: PlayerController, aspect: String, isLive: Boolean, onAspect: (String) -> Unit, onOpenChange: (Boolean) -> Unit,
    sleepMinutes: Int? = null, onSleep: (Int) -> Unit = {},
) {
    var open by remember { mutableStateOf(false) }
    Box {
        RoundIcon(Icons.Filled.Settings, "Einstellungen") { open = true; onOpenChange(true) }
        DropdownMenu(open, onDismissRequest = { open = false; onOpenChange(false) }) {
            MenuHeader(Icons.Filled.AspectRatio, "Bildformat")
            AspectModes.forEach { (k, l) -> CheckItem(l, aspect == k) { onAspect(k) } }
            HorizontalDivider()
            MenuHeader(Icons.AutoMirrored.Filled.VolumeUp, "Tonspur")
            if (ctl.audioTracks.isEmpty()) DropdownMenuItem(text = { Text("Standard") }, onClick = {}, enabled = false)
            ctl.audioTracks.forEach { t -> CheckItem(t.name, ctl.audioTrack == t.id) { ctl.selectAudio(t.id) } }
            HorizontalDivider()
            MenuHeader(Icons.Filled.Subtitles, "Untertitel")
            CheckItem("Aus", ctl.subtitleTrack < 0) { ctl.selectSubtitle(-1) }
            ctl.subtitleTracks.forEach { t -> CheckItem(t.name, ctl.subtitleTrack == t.id) { ctl.selectSubtitle(t.id) } }
            if (!isLive) {
                HorizontalDivider()
                MenuHeader(Icons.Filled.Speed, "Geschwindigkeit")
                listOf(0.75f, 1f, 1.25f, 1.5f, 2f).forEach { r -> CheckItem(if (r == 1f) "Normal" else "${r}x", ctl.rate == r) { ctl.changeRate(r) } }
            }
            HorizontalDivider()
            MenuHeader(Icons.Filled.Bedtime, "Sleep-Timer" + (sleepMinutes?.let { " (noch $it Min.)" } ?: ""))
            CheckItem("Aus", sleepMinutes == null) { onSleep(0) }
            listOf(15, 30, 45, 60, 90, 120).forEach { m -> CheckItem("$m Minuten", false) { onSleep(m) } }
        }
    }
}

@Composable
private fun MenuHeader(icon: ImageVector, text: String) {
    Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = BrandCyan, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(text, color = BrandCyan, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun CheckItem(label: String, checked: Boolean, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(label) },
        onClick = onClick,
        leadingIcon = { if (checked) Icon(Icons.Filled.Check, null) else Spacer(Modifier.size(24.dp)) },
    )
}

@Composable
private fun NextEpisodeCard(ep: Episode, secondsLeft: Int, onPlay: () -> Unit, onDismiss: () -> Unit, modifier: Modifier) {
    Row(
        modifier.width(460.dp).clip(RoundedCornerShape(14.dp)).background(Surface.copy(alpha = 0.95f)).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        NetImage(ep.imageCandidates, Modifier.width(150.dp).height(84.dp).clip(RoundedCornerShape(8.dp)))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text("Nächste Folge in $secondsLeft s", color = BrandCyan, fontWeight = FontWeight.Bold)
            Text("S${ep.season} E${ep.episodeNum} · ${ep.title}", maxLines = 2, overflow = TextOverflow.Ellipsis)
            LinearProgressIndicator(
                progress = { 1f - secondsLeft / (NEXT_BEFORE_END / 1000f) },
                modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp), color = BrandCyan,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onPlay, modifier = Modifier.handCursor()) { Text("Jetzt ansehen") }
                OutlinedButton(onClick = onDismiss, modifier = Modifier.handCursor()) { Text("Ausblenden") }
            }
        }
    }
}

@Composable
private fun CenterMessage(text: String, onRetry: (() -> Unit)? = null, onClose: () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            Modifier.clip(RoundedCornerShape(16.dp)).background(Surface.copy(alpha = 0.95f)).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(text, style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                onRetry?.let { Button(onClick = it, modifier = Modifier.handCursor()) { Text("Erneut versuchen") } }
                OutlinedButton(onClick = onClose, modifier = Modifier.handCursor()) { Text("Schließen") }
            }
        }
    }
}

/** "Intro ueberspringen" anzeigen? Serien: 7 s ab gelerntem Intro-Beginn, sonst ab 5 s nach dem Start. */
private fun showSkipIntroNow(app: AppState, req: PlayRequest, ctl: PlayerController, skipped: Boolean): Boolean {
    if (req.episode == null || skipped || !ctl.playing) return false
    val from = app.library?.intro(req.item.key)?.first ?: 5_000L
    return ctl.time in from..(from + 7_000L)
}

/** Aufnahme starten (Live): Dauer waehlen oder bis Sendungsende (EPG) – wie Android. */
@Composable
private fun RecordDialog(app: AppState, req: PlayRequest, onDismiss: () -> Unit, onDone: (String) -> Unit) {
    val cur = app.epg.current(req.item)
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Aufnahme starten") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(req.item.name)
                cur?.let { p ->
                    androidx.compose.material3.Button(onClick = {
                        onDone(app.recordings.schedule(p.title, req.item.name, req.url, System.currentTimeMillis(), p.end, req.item.logo))
                    }) { Text("Bis Sendungsende: ${p.title}") }
                }
                listOf(30, 60, 90, 120, 180).forEach { m ->
                    OutlinedButton(onClick = { onDone(app.recordings.recordNow(cur?.title ?: req.item.name, req.item.name, req.url, m, req.item.logo)) }) { Text("$m Minuten") }
                }
                Text("Aufnahmen laufen weiter, auch wenn du den Player schließt (Portiva muss geöffnet bleiben). Jede Aufnahme belegt eine Verbindung deines Zugangs.", style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = { androidx.compose.material3.TextButton(onClick = onDismiss) { Text("Abbrechen") } },
    )
}

/** Portiva Link: auf einem anderen Portiva-Geraet (TV-Stick, Tablet, Handy) an derselben Stelle weiterschauen. */
@Composable
private fun LinkSendMenu(app: AppState, req: PlayRequest, ctl: PlayerController, onOpenChange: (Boolean) -> Unit, onInfo: (String) -> Unit) {
    if (!req.url.startsWith("http", ignoreCase = true)) return
    var open by remember { mutableStateOf(false) }
    // Menue offen -> Leiste bleibt sichtbar (sonst schliesst sich das Menue mitten in der Suche)
    androidx.compose.runtime.DisposableEffect(open) { onOpenChange(open); onDispose { if (open) onOpenChange(false) } }
    var devices by remember { mutableStateOf<List<com.poweriptv.app.link.LinkDevice>?>(null) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    LaunchedEffect(open) {
        if (!open) return@LaunchedEffect
        devices = null
        devices = withContext(Dispatchers.IO) { com.poweriptv.app.link.LinkClient.discover(app.link.port) }
    }
    Box {
        RoundIcon(Icons.Filled.ConnectedTv, "An Gerät senden (Portiva)") { open = true }
        DropdownMenu(open, onDismissRequest = { open = false }) {
            MenuHeader(Icons.Filled.ConnectedTv, "An Portiva-Gerät senden")
            val list = devices
            when {
                list == null -> DropdownMenuItem(text = { Text("Suche Portiva-Geräte im Heimnetz …") }, onClick = {}, enabled = false)
                list.isEmpty() -> DropdownMenuItem(text = { Text("Kein Gerät gefunden – Portiva dort öffnen (gleiches WLAN)") }, onClick = {}, enabled = false)
                else -> list.forEach { d ->
                    DropdownMenuItem(text = { Text(d.name) }, onClick = {
                        open = false
                        scope.launch {
                            val play = com.poweriptv.app.link.LinkPlay(
                                req.title, req.url, req.isLive, if (req.isLive) 0L else ctl.time, ctl.length.coerceAtLeast(0L),
                                req.item.logo, app.deviceName(),
                            )
                            val err = withContext(Dispatchers.IO) { com.poweriptv.app.link.LinkClient.sendPlay(d, play) }
                            if (err == null) { onInfo("Läuft jetzt auf „${d.name}“"); app.closePlayer() } else onInfo(err)
                        }
                    })
                }
            }
        }
    }
}

/** Cast-Knopf: Geraete im Heimnetz suchen und den Stream dort abspielen. */
@Composable
private fun CastMenu(ctl: PlayerController, onOpenChange: (Boolean) -> Unit, onInfo: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    // Menue offen -> Leiste bleibt sichtbar (sonst schliesst sich das Menue mitten in der Suche)
    androidx.compose.runtime.DisposableEffect(open) { onOpenChange(open); onDispose { if (open) onOpenChange(false) } }
    val devices by CastDiscovery.devices.collectAsState()
    Box {
        RoundIcon(if (ctl.castingTo != null) Icons.Filled.CastConnected else Icons.Filled.Cast, "Auf Fernseher übertragen",
            tint = if (ctl.castingTo != null) BrandCyan else Color.White) { CastDiscovery.start(); open = true }
        DropdownMenu(open, onDismissRequest = { open = false }) {
            MenuHeader(Icons.Filled.Cast, "Auf Fernseher übertragen")
            if (devices.isEmpty()) DropdownMenuItem(text = { Text("Suche Geräte im Heimnetz … (Chromecast / Android TV)") }, onClick = {}, enabled = false)
            devices.forEach { d ->
                CheckItem(d.name(), ctl.castingTo == d.name()) { ctl.castTo(d); open = false; onInfo("Wird auf „${d.name()}“ abgespielt") }
            }
            if (ctl.castingTo != null) {
                HorizontalDivider()
                CheckItem("Auf diesem PC abspielen", false) { ctl.castTo(null); open = false; onInfo("Wiedergabe wieder am PC") }
            }
        }
    }
}

/**
 * Vorschaubild beim Spulen (wie Android): zweiter, stummer Player springt an die Zielstelle.
 * Braucht eine zweite Verbindung – Einstellung AUTO/IMMER/AUS, AUTO nur bei mindestens 2 erlaubten Streams.
 */
private enum class ScrubMode { OFF, PARALLEL, EXCLUSIVE }

/**
 * Wie Android: AUS, parallel (2. Verbindung, Film laeuft weiter) oder Ein-Verbindungs-Modus
 * (Film haelt beim Spulen kurz an, damit nie zwei Verbindungen gleichzeitig offen sind).
 */
private fun scrubPreviewMode(app: AppState, req: PlayRequest): ScrubMode {
    val s = app.settings.value
    if (req.url.startsWith("file:")) return ScrubMode.PARALLEL
    return when (s.scrubPreview) {
        "OFF" -> ScrubMode.OFF
        "ALWAYS" -> ScrubMode.PARALLEL
        else -> if (!s.scrubBlocked && (app.maxConnections ?: 1) - app.recordings.running().size >= 2) ScrubMode.PARALLEL else ScrubMode.EXCLUSIVE
    }
}

@Composable
private fun ScrubPreviewBubble(app: AppState, req: PlayRequest, ctl: PlayerController, dragging: Float?) {
    val s = app.settings.value
    val enabled = scrubPreviewMode(app, req) != ScrubMode.OFF
    val preview = remember { mutableStateOf<PlayerController?>(null) }
    DisposableEffect(Unit) { onDispose { preview.value?.release(); preview.value = null } }
    if (!enabled || ctl.length <= 0) return
    LaunchedEffect(dragging?.let { (it * 200).toInt() }) {
        val d = dragging
        if (d == null) { preview.value?.stopKeepFrame(); return@LaunchedEffect }
        delay(300)
        val p = preview.value ?: PlayerController(800, s.hardwareDecoding).apply { noAudio = true }.also { preview.value = it }
        p.play(req.url, (d * ctl.length).toLong())
        // nach dem ersten Bild anhalten (spart Bandbreite)
        repeat(40) { if (p.frameCounter > 0 && p.playing) { p.pause(); return@LaunchedEffect }; delay(100) }
    }
    val p = preview.value
    if (dragging != null && p != null) {
        androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxWidth().height(150.dp)) {
            val x = (maxWidth - 240.dp) * dragging
            Column(Modifier.padding(start = x).width(240.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.width(240.dp).height(135.dp).clip(RoundedCornerShape(8.dp)).background(Color.Black)) {
                    VideoView(p, "fit", Modifier.fillMaxSize())
                    if (p.frameCounter == 0L) CircularProgressIndicator(Modifier.align(Alignment.Center).size(24.dp), color = BrandCyan, strokeWidth = 2.dp)
                }
                Text(formatTime((dragging * ctl.length).toLong()), color = Color.White)
            }
        }
    }
}

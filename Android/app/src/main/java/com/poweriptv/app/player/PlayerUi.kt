package com.poweriptv.app.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.poweriptv.app.data.VideoScale
import com.poweriptv.app.ui.components.tvFocus
import kotlinx.coroutines.delay

/** Eine waehlbare Audio- oder Untertitelspur. */
data class TrackOption(val key: String, val label: String, val selected: Boolean)

/** Welcher Teil der Player-Einstellungen gezeigt wird (Zahnrad = MAIN, Knoepfe unten = Rest). */
enum class PlayerSection(val title: String) { MAIN("Einstellungen"), FORMAT("Seitenverhältnis"), SPEED("Geschwindigkeit"), SUBTITLES("Untertitel") }

/**
 * Einstellungen im Player. Zahnrad oben rechts: Audiospur + Sleep-Timer.
 * Seitenverhaeltnis, Geschwindigkeit und Untertitel haben eigene Knoepfe unten (PlayerQuickBar).
 */
@Composable
fun PlayerSettingsDialog(
    audio: List<TrackOption>,
    subtitles: List<TrackOption>,
    format: VideoScale,
    onAudio: (TrackOption) -> Unit,
    onSubtitle: (TrackOption) -> Unit,
    onFormat: (VideoScale) -> Unit,
    onDismiss: () -> Unit,
    /** Wiedergabegeschwindigkeit (null = nicht verfuegbar, z.B. Live). */
    speed: Float? = null,
    onSpeed: (Float) -> Unit = {},
    /** Sleep-Timer: verbleibende Minuten (null = aus). */
    sleepMinutes: Int? = null,
    onSleep: (Int) -> Unit = {},
    subtitleSize: String = "NORMAL",
    onSubtitleSize: (String) -> Unit = {},
    subtitleBackground: Boolean = false,
    onSubtitleBackground: (Boolean) -> Unit = {},
    section: PlayerSection = PlayerSection.MAIN,
) {
    val focus = remember { FocusRequester() }
    var firstFocusSet = false
    @Composable
    fun Option(label: String, selected: Boolean, onClick: () -> Unit) {
        val req = if (!firstFocusSet && selected) { firstFocusSet = true; Modifier.focusRequester(focus) } else Modifier
        Row(
            Modifier.fillMaxWidth().then(req)
                .tvFocus(RoundedCornerShape(8.dp))
                .clickable { onClick(); onDismiss() }
                .padding(vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RadioButton(selected = selected, onClick = { onClick(); onDismiss() })
            Text(label, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
        }
    }
    @Composable
    fun Header(t: String) {
        Text(t, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 10.dp, bottom = 2.dp))
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(section.title) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                if (section == PlayerSection.MAIN) {
                Header("Audiospur")
                if (audio.isEmpty() || (audio.size == 1 && !audio[0].key.startsWith(VLC_KEY))) {
                    Text(audio.firstOrNull()?.label ?: "Keine Auswahl verfuegbar", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else audio.forEach { a -> Option(a.label, a.selected) { onAudio(a) } }
                Header(if (sleepMinutes != null) "Sleep-Timer (noch $sleepMinutes Min.)" else "Sleep-Timer")
                listOf(0 to "Aus", 15 to "15 Minuten", 30 to "30 Minuten", 60 to "60 Minuten", 90 to "90 Minuten", 120 to "2 Stunden").forEach { (m, l) ->
                    Option(l, if (m == 0) sleepMinutes == null else false) { onSleep(m) }
                }
                }
                if (section == PlayerSection.SUBTITLES) {
                if (subtitles.none { it.key != OFF_KEY }) {
                    Text("Keine Untertitel verfuegbar", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    subtitles.forEach { t -> Option(t.label, t.selected) { onSubtitle(t) } }
                    Header("Untertitel-Groesse")
                    listOf("KLEIN" to "Klein", "NORMAL" to "Normal", "GROSS" to "Gross", "SEHR_GROSS" to "Sehr gross").forEach { (k, l) ->
                        Option(l, subtitleSize == k) { onSubtitleSize(k) }
                    }
                    Option(if (subtitleBackground) "Dunkler Hintergrund: an" else "Dunkler Hintergrund: aus", subtitleBackground) { onSubtitleBackground(!subtitleBackground) }
                }
                }
                if (section == PlayerSection.SPEED) {
                    if (speed == null) Text("Bei Live TV nicht verfügbar", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    else listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f).forEach { v ->
                        Option(if (v == 1f) "Normal (1×)" else "${v.toString().removeSuffix(".0")}×", kotlin.math.abs(speed - v) < 0.01f) { onSpeed(v) }
                    }
                }
                if (section == PlayerSection.FORMAT) {
                    VideoScale.entries.forEach { v -> Option(v.label, v == format) { onFormat(v) } }
                }
            }
        },
        confirmButton = { TextButton(modifier = Modifier.tvFocus(RoundedCornerShape(50)), onClick = onDismiss) { Text("Schliessen") } },
    )
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
}

/** Schluessel fuer "Untertitel aus". */
const val OFF_KEY = "off"
/** Praefix fuer Tonspuren, die nur VLC abspielen kann (Auswahl wechselt den Player). */
const val VLC_KEY = "vlc:"

/** Sprachcode -> deutscher Name ("de" -> "Deutsch"); unbekannt -> null. */
fun languageName(code: String?): String? {
    val c = code?.trim()?.takeIf { it.isNotEmpty() && it != "und" && !it.equals("null", true) } ?: return null
    return runCatching { java.util.Locale.forLanguageTag(c).getDisplayLanguage(java.util.Locale.GERMAN) }.getOrNull()
        ?.takeIf { it.isNotBlank() && !it.equals(c, true) }?.replaceFirstChar { it.uppercase() } ?: c.uppercase()
}

/** Kurze Einblendung des aktiven Bildformats (oben mittig, ca. 2 s). */
@Composable
fun FormatBadge(text: String?, onDone: () -> Unit) {
    if (text == null) return
    LaunchedEffect(text) { delay(2000); onDone() }
    Box(Modifier.fillMaxSize().padding(top = 72.dp), contentAlignment = Alignment.TopCenter) {
        Text(
            "Bildformat: $text",
            color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.clip(RoundedCornerShape(24.dp)).background(Color(0xCC000000))
                .padding(horizontal = 22.dp, vertical = 10.dp),
        )
    }
}

/** "Naechste Folge in X s" (unten rechts) mit Sofort-Start und Abbrechen. OK = jetzt abspielen. */
@Composable
fun NextEpisodeCard(title: String, seconds: Int, onPlay: () -> Unit, onCancel: () -> Unit) {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.BottomEnd) {
        Column(
            Modifier.clip(RoundedCornerShape(14.dp)).background(Color(0xE6101620)).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("Naechste Folge in $seconds s", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Text(title, color = Color.White.copy(alpha = 0.8f), maxLines = 2, fontSize = 14.sp, modifier = Modifier.widthIn(max = 360.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                androidx.compose.material3.Button(onClick = onPlay, modifier = Modifier.tvFocus(RoundedCornerShape(50))) { Text("▶ Jetzt abspielen") }
                androidx.compose.material3.OutlinedButton(onClick = onCancel, modifier = Modifier.tvFocus(RoundedCornerShape(50))) { Text("Abbrechen", color = Color.White) }
            }
        }
    }
}

/** "Intro ueberspringen" unten rechts (Serien, in den ersten Minuten). OK auf der Fernbedienung loest aus. */
@Composable
fun SkipIntroButton(onClick: () -> Unit) {
    Box(Modifier.fillMaxSize().padding(start = 24.dp, end = 24.dp, bottom = 96.dp), contentAlignment = Alignment.BottomEnd) {
        androidx.compose.material3.OutlinedButton(
            onClick = onClick,
            modifier = Modifier.tvFocus(RoundedCornerShape(50)).background(Color(0x99000000), RoundedCornerShape(50)),
        ) { Text("Intro ueberspringen ⏭", color = Color.White, fontWeight = FontWeight.Bold) }
    }
}

/** Gemeinsame Logik fuer Serien-Komfort in beiden Playern. */
object EpisodeFlow {
    /** Countdown-Start vor Ende (ms). */
    const val NEXT_BEFORE_END = 40_000L
    /** "Intro ueberspringen" ist so lange sichtbar. */
    const val INTRO_SHOW_MS = 7_000L
    /** Intro lernen: Vorspulen in den ersten 10 Minuten um 20 s bis 5 Min. */
    const val LEARN_WITHIN = 600_000L
    const val LEARN_MIN = 20_000L
    const val LEARN_MAX = 300_000L
    /** Sprungweite und Zeitfenster fuer "Intro ueberspringen". */
    const val INTRO_SKIP = 85_000L
    const val INTRO_WINDOW_START = 5_000L
    const val INTRO_WINDOW_END = 240_000L

    fun isEpisode(e: com.poweriptv.app.PlayEntry?) = e != null && !e.live && e.item?.type == com.poweriptv.app.data.ContentType.SERIES

    /** "Serie – S1E2 Titel" -> "S1E2 Titel" */
    fun episodeLabel(title: String) = title.substringAfter(" – ", title)
}

/**
 * Senderliste im laufenden Bild (rechts): Logo, Nummer, Name und aktuelle Sendung.
 * Auswahl startet den Sender, ohne den Player zu verlassen.
 */
@Composable
fun ChannelListPanel(
    container: com.poweriptv.app.AppContainer,
    entries: List<com.poweriptv.app.PlayEntry>,
    current: Int,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val listState = androidx.compose.foundation.lazy.rememberLazyListState(initialFirstVisibleItemIndex = (current - 3).coerceAtLeast(0))
    val focus = remember { FocusRequester() }
    val now = remember { System.currentTimeMillis() }
    Box(Modifier.fillMaxSize().background(Color(0x55000000)).clickable(onClick = onDismiss)) {
        Column(
            Modifier.align(Alignment.CenterEnd).fillMaxHeight().widthIn(max = 380.dp).fillMaxWidth(0.42f)
                .background(Color(0xF0101620)).clickable(enabled = false) {}.padding(vertical = 12.dp),
        ) {
            Text("Senderliste", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp, modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp))
            androidx.compose.foundation.lazy.LazyColumn(state = listState) {
                items(entries.size) { i ->
                    val e = entries[i]
                    val sel = i == current
                    val programme = e.item?.let { runCatching { container.epg.current(it, now)?.title }.getOrNull() }
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp)
                            .then(if (sel) Modifier.focusRequester(focus) else Modifier)
                            .clip(RoundedCornerShape(8.dp))
                            .tvFocus(RoundedCornerShape(8.dp))
                            .background(if (sel) Color(0x332FB8E6) else Color.Transparent)
                            .clickable { onSelect(i) }
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("${e.item?.number ?: (i + 1)}", color = Color.White.copy(alpha = 0.6f), fontSize = 13.sp, modifier = Modifier.widthIn(min = 30.dp))
                        Box(Modifier.size(width = 52.dp, height = 30.dp), contentAlignment = Alignment.Center) {
                            if (!e.item?.logo.isNullOrBlank()) coil.compose.AsyncImage(e.item?.logo, null, modifier = Modifier.fillMaxSize())
                        }
                        Column(Modifier.padding(start = 10.dp).weight(1f)) {
                            Text(e.title, color = Color.White, fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal, maxLines = 1, fontSize = 15.sp)
                            if (programme != null) Text(programme, color = Color.White.copy(alpha = 0.65f), maxLines = 1, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
}

/**
 * Knopfleiste unten im Player (wie gewuenscht): Seitenverhaeltnis – Geschwindigkeit – Untertitel.
 * speedLabel = null -> Live TV (Geschwindigkeit nicht moeglich, Knopf ausgeblendet).
 */
@Composable
fun PlayerQuickBar(
    formatLabel: String,
    speedLabel: String?,
    subtitleLabel: String,
    onSection: (PlayerSection) -> Unit,
    modifier: Modifier = Modifier,
    /** Untertitel-Knopf: nur ein/aus (Spuren + Einstellungen im Zahnrad). */
    onSubtitleToggle: (() -> Unit)? = null,
) {
    @Composable
    fun Chip(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, value: String, section: PlayerSection) {
        Row(
            Modifier.clip(RoundedCornerShape(50)).tvFocus(RoundedCornerShape(50), 1.06f)
                .clickable { if (section == PlayerSection.SUBTITLES && onSubtitleToggle != null) onSubtitleToggle() else onSection(section) }
                .background(Color(0x66000000))
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            androidx.compose.material3.Icon(icon, null, tint = Color.White, modifier = Modifier.size(22.dp))
            androidx.compose.foundation.layout.Spacer(Modifier.size(8.dp))
            Text(label, color = Color.White, fontSize = 15.sp, maxLines = 1)
            if (value.isNotBlank()) {
                Text("  $value", color = Color.White.copy(alpha = 0.65f), fontSize = 13.sp, maxLines = 1)
            }
        }
    }
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Chip(Icons.Filled.AspectRatio, "Seitenverhältnis", formatLabel, PlayerSection.FORMAT)
        if (speedLabel != null) Chip(Icons.Filled.Speed, "Geschwindigkeit", speedLabel, PlayerSection.SPEED)
        Chip(Icons.Filled.ClosedCaption, "Untertitel", subtitleLabel, PlayerSection.SUBTITLES)
    }
}

/**
 * Live-TV-Infoleiste unten (wie gewuenscht): Senderlogo, "Jetzt" mit Fortschritt, "Weiter",
 * darunter Mehrfachbildschirm – Seitenverhaeltnis – Senderliste.
 * sideInset: Abstand links/rechts, damit Helligkeit/Lautstaerke (Handy) daneben Platz haben.
 */
@Composable
fun LiveInfoBar(
    logo: String?,
    epg: List<com.poweriptv.app.data.EpgEntry>,
    formatLabel: String,
    onChannels: () -> Unit,
    onFormat: () -> Unit,
    onMultiScreen: (() -> Unit)?,
    modifier: Modifier = Modifier,
    sideInset: androidx.compose.ui.unit.Dp = 0.dp,
) {
    // Fortschritt der laufenden Sendung jede halbe Minute auffrischen
    var now by androidx.compose.runtime.remember { androidx.compose.runtime.mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { delay(30_000); now = System.currentTimeMillis() } }
    val fmt = java.text.DateFormat.getTimeInstance(java.text.DateFormat.SHORT)
    val cur = epg.firstOrNull { it.start <= now && it.end > now } ?: epg.firstOrNull()?.takeIf { it.start <= now }
    val next = epg.firstOrNull { it.start >= (cur?.end ?: now) && it !== cur }
    fun line(e: com.poweriptv.app.data.EpgEntry?) =
        e?.let { "${fmt.format(java.util.Date(it.start))} – ${fmt.format(java.util.Date(it.end))}  ${it.title}" } ?: "Kein Programm gefunden"
    Column(
        modifier.fillMaxWidth().background(
            androidx.compose.ui.graphics.Brush.verticalGradient(listOf(Color.Transparent, Color(0xCC000000)))
        ).padding(start = 16.dp + sideInset, end = 16.dp + sideInset, top = 24.dp, bottom = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(width = 86.dp, height = 56.dp).clip(RoundedCornerShape(8.dp)).background(Color(0xFF1B1F2A)),
                contentAlignment = Alignment.Center,
            ) {
                if (!logo.isNullOrBlank()) coil.compose.AsyncImage(logo, null, modifier = Modifier.fillMaxSize().padding(4.dp))
                else androidx.compose.material3.Icon(Icons.Filled.LiveTv, null, tint = Color.White)
            }
            androidx.compose.foundation.layout.Spacer(Modifier.size(16.dp))
            Column(Modifier.weight(1f)) {
                Text("Jetzt: " + line(cur), color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                val progress = cur?.let { ((now - it.start).toFloat() / (it.end - it.start).coerceAtLeast(1)).coerceIn(0f, 1f) } ?: 0f
                Box(Modifier.padding(vertical = 7.dp).fillMaxWidth().height(5.dp).clip(RoundedCornerShape(3.dp)).background(Color.White.copy(alpha = 0.3f))) {
                    Box(Modifier.fillMaxHeight().fillMaxWidth(progress).background(com.poweriptv.app.ui.theme.BrandCyan))
                }
                Text("Weiter: " + line(next), color = Color.White.copy(alpha = 0.8f), fontSize = 17.sp, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            }
        }
        @Composable
        fun Chip(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, value: String, onClick: () -> Unit) {
            Row(
                Modifier.clip(RoundedCornerShape(50)).tvFocus(RoundedCornerShape(50), 1.06f).clickable(onClick = onClick)
                    .background(Color(0x66000000)).padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                androidx.compose.material3.Icon(icon, null, tint = Color.White, modifier = Modifier.size(22.dp))
                androidx.compose.foundation.layout.Spacer(Modifier.size(8.dp))
                Text(label, color = Color.White, fontSize = 15.sp, maxLines = 1)
                if (value.isNotBlank()) Text("  $value", color = Color.White.copy(alpha = 0.65f), fontSize = 13.sp, maxLines = 1)
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(top = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (onMultiScreen != null) Chip(Icons.Filled.GridView, "Mehrfachbildschirm", "", onMultiScreen)
            Chip(Icons.Filled.AspectRatio, "Seitenverhältnis", formatLabel, onFormat)
            Chip(Icons.Filled.VideoLibrary, "Senderliste", "", onChannels)
        }
    }
}

/** Senkrechter Regler (0..1) mit Symbol oben – zum Ziehen oder Antippen. */
@Composable
fun VerticalLevel(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    value: Float,
    onChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    railHeight: androidx.compose.ui.unit.Dp = 180.dp,
) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        androidx.compose.material3.Icon(icon, null, tint = Color.White, modifier = Modifier.size(30.dp))
        androidx.compose.foundation.layout.Spacer(Modifier.size(14.dp))
        androidx.compose.foundation.layout.BoxWithConstraints(
            Modifier.size(width = 44.dp, height = railHeight)
                .androidx_pointer(onChange),
            contentAlignment = Alignment.BottomCenter,
        ) {
            // Schiene
            Box(Modifier.fillMaxHeight().size(width = 8.dp, height = railHeight).clip(RoundedCornerShape(4.dp)).background(Color.White.copy(alpha = 0.3f)))
            // Fuellung
            Box(
                Modifier.size(width = 8.dp, height = railHeight * value.coerceIn(0f, 1f))
                    .clip(RoundedCornerShape(4.dp)).background(Color.White),
            )
        }
        Text("${(value * 100).toInt()}%", color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
    }
}

/** Ziehen/Antippen auf dem Regler: oben = 100 %, unten = 0 %. */
private fun Modifier.androidx_pointer(onChange: (Float) -> Unit): Modifier = this
    .then(
        Modifier.pointerInput(Unit) {
            detectTapGestures { o -> onChange(1f - (o.y / size.height).coerceIn(0f, 1f)) }
        },
    )
    .then(
        Modifier.pointerInput(Unit) {
            detectVerticalDragGestures { change, _ ->
                change.consume()
                onChange(1f - (change.position.y / size.height).coerceIn(0f, 1f))
            }
        },
    )

/**
 * Helligkeit (links) und Lautstaerke (rechts) wie im Vorbild – nur auf Handy/Tablet
 * (am Fernseher regelt die Fernbedienung Ton und der Fernseher die Helligkeit).
 */
@Composable
fun PlayerSideLevels(activity: android.app.Activity, modifier: Modifier = Modifier) {
    val audio = remember { activity.getSystemService(android.content.Context.AUDIO_SERVICE) as android.media.AudioManager }
    val maxVol = remember { audio.getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC).coerceAtLeast(1) }
    var volume by remember { androidx.compose.runtime.mutableFloatStateOf(audio.getStreamVolume(android.media.AudioManager.STREAM_MUSIC) / maxVol.toFloat()) }
    var brightness by remember { androidx.compose.runtime.mutableFloatStateOf(currentBrightness(activity)) }
    // Lautstaerke auch per Hardware-Tasten geaendert -> Anzeige nachziehen
    LaunchedEffect(Unit) {
        while (true) {
            delay(700)
            volume = audio.getStreamVolume(android.media.AudioManager.STREAM_MUSIC) / maxVol.toFloat()
        }
    }
    androidx.compose.foundation.layout.BoxWithConstraints(modifier.fillMaxSize().padding(horizontal = 28.dp)) {
        // Regler so hoch wie Platz ist (Symbol 30 + Abstand 14 + Prozent ~24), hoechstens 200 dp
        val rail = (maxHeight - 68.dp).coerceIn(90.dp, 200.dp)
        VerticalLevel(
            Icons.Filled.LightMode, brightness,
            onChange = { v ->
                brightness = v
                activity.window.attributes = activity.window.attributes.apply { screenBrightness = v.coerceIn(0.02f, 1f) }
            },
            modifier = Modifier.align(Alignment.CenterStart),
            railHeight = rail,
        )
        VerticalLevel(
            if (volume <= 0.001f) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
            volume,
            onChange = { v ->
                volume = v
                runCatching { audio.setStreamVolume(android.media.AudioManager.STREAM_MUSIC, (v * maxVol).toInt(), 0) }
            },
            modifier = Modifier.align(Alignment.CenterEnd),
            railHeight = rail,
        )
    }
}

/** Aktuelle Helligkeit: im Player gesetzt, sonst die des Systems. */
private fun currentBrightness(activity: android.app.Activity): Float {
    val w = activity.window.attributes.screenBrightness
    if (w >= 0f) return w
    return runCatching {
        android.provider.Settings.System.getInt(activity.contentResolver, android.provider.Settings.System.SCREEN_BRIGHTNESS) / 255f
    }.getOrDefault(0.5f).coerceIn(0f, 1f)
}

/**
 * Zahnrad: Einstellungsleiste rechts (wie im Vorbild) mit Video-, Audio- und Untertitelspuren,
 * Untertitel-Einstellungen und Sleep-Timer. Bleibt offen, bis man sie schliesst (Pfeil, daneben tippen, Zurueck).
 */
@Composable
fun PlayerSettingsPanel(
    video: List<TrackOption>,
    audio: List<TrackOption>,
    subtitles: List<TrackOption>,
    onVideo: (TrackOption) -> Unit,
    onAudio: (TrackOption) -> Unit,
    onSubtitle: (TrackOption) -> Unit,
    subtitleSize: String,
    onSubtitleSize: (String) -> Unit,
    subtitleBackground: Boolean,
    onSubtitleBackground: (Boolean) -> Unit,
    sleepMinutes: Int?,
    onSleep: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val focus = remember { FocusRequester() }
    androidx.activity.compose.BackHandler(onBack = onDismiss)
    Row(Modifier.fillMaxSize()) {
        // Links daneben tippen = schliessen
        Box(Modifier.weight(1f).fillMaxHeight().clickable(indication = null, interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }) { onDismiss() })
        Column(
            Modifier.fillMaxHeight().widthIn(min = 320.dp, max = 460.dp).fillMaxWidth(0.42f)
                .background(Color(0xF2080C14)),
        ) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(46.dp).clip(androidx.compose.foundation.shape.CircleShape).tvFocus(androidx.compose.foundation.shape.CircleShape, 1.1f)
                        .background(com.poweriptv.app.ui.theme.Accent2).clickable { onDismiss() },
                    contentAlignment = Alignment.Center,
                ) { androidx.compose.material3.Icon(Icons.AutoMirrored.Filled.ArrowBack, "Schliessen", tint = Color.White) }
                androidx.compose.foundation.layout.Spacer(Modifier.size(14.dp))
                Text("Einstellungen", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.35f)))
            var focusAssigned = false
            @Composable
            fun Radio(o: TrackOption, onClick: () -> Unit) {
                val req = if (!focusAssigned && o.selected) { focusAssigned = true; Modifier.focusRequester(focus) } else Modifier
                Row(
                    Modifier.fillMaxWidth().then(req).tvFocus(RoundedCornerShape(8.dp)).clickable { onClick() }.padding(start = 12.dp, end = 12.dp, top = 2.dp, bottom = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = o.selected, onClick = onClick)
                    Text(o.label, color = Color.White, fontSize = 15.sp)
                }
            }
            @Composable
            fun Section(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String) {
                Row(Modifier.padding(start = 18.dp, top = 18.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    androidx.compose.material3.Icon(icon, null, tint = Color.White, modifier = Modifier.size(28.dp))
                    androidx.compose.foundation.layout.Spacer(Modifier.size(12.dp))
                    Text(title, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                }
            }
            @Composable
            fun Empty(t: String) = Text(t, color = Color.White.copy(alpha = 0.6f), fontSize = 14.sp, modifier = Modifier.padding(start = 26.dp, bottom = 4.dp))

            Column(Modifier.verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
                Section(Icons.Filled.Movie, "Videospuren")
                if (video.isEmpty()) Empty("Keine Angaben") else video.forEach { o -> Radio(o) { onVideo(o) } }
                Section(Icons.Filled.MusicNote, "Audiospuren")
                if (audio.isEmpty()) Empty("Keine Angaben") else audio.forEach { o -> Radio(o) { onAudio(o) } }
                Section(Icons.Filled.ClosedCaption, "Untertitelspuren")
                if (subtitles.none { it.key != OFF_KEY }) Empty("Keine Untertitel im Stream")
                else subtitles.forEach { o -> Radio(o) { onSubtitle(o) } }
                Section(Icons.Filled.Tune, "Untertiteleinstellungen")
                Text("Schriftgröße", color = Color.White.copy(alpha = 0.8f), fontSize = 14.sp, modifier = Modifier.padding(start = 26.dp, top = 2.dp))
                listOf("KLEIN" to "Klein", "NORMAL" to "Normal", "GROSS" to "Groß", "SEHR_GROSS" to "Sehr groß").forEach { (k, l) ->
                    Radio(TrackOption(k, l, subtitleSize == k)) { onSubtitleSize(k) }
                }
                Radio(TrackOption("bg", "Dunkler Hintergrund", subtitleBackground)) { onSubtitleBackground(!subtitleBackground) }
                Section(Icons.Filled.Bedtime, if (sleepMinutes != null) "Sleep-Timer (noch $sleepMinutes Min.)" else "Sleep-Timer")
                listOf(0 to "Aus", 15 to "15 Minuten", 30 to "30 Minuten", 60 to "60 Minuten", 90 to "90 Minuten", 120 to "2 Stunden").forEach { (m, l) ->
                    Radio(TrackOption("s$m", l, if (m == 0) sleepMinutes == null else false)) { onSleep(m) }
                }
            }
        }
    }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
}

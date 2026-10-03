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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
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

/**
 * Einstellungen im Player (Zahnrad oben rechts bzw. Menue-Taste):
 * Audiospur, Untertitel und Bildformat.
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
        title = { Text("Einstellungen") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Header("Audiospur")
                if (audio.isEmpty() || (audio.size == 1 && !audio[0].key.startsWith(VLC_KEY))) {
                    Text(audio.firstOrNull()?.label ?: "Keine Auswahl verfuegbar", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else audio.forEach { a -> Option(a.label, a.selected) { onAudio(a) } }
                Header("Untertitel")
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
                if (speed != null) {
                    Header("Geschwindigkeit")
                    listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f).forEach { v ->
                        Option(if (v == 1f) "Normal (1×)" else "${v.toString().removeSuffix(".0")}×", kotlin.math.abs(speed - v) < 0.01f) { onSpeed(v) }
                    }
                }
                Header(if (sleepMinutes != null) "Sleep-Timer (noch $sleepMinutes Min.)" else "Sleep-Timer")
                listOf(0 to "Aus", 15 to "15 Minuten", 30 to "30 Minuten", 60 to "60 Minuten", 90 to "90 Minuten", 120 to "2 Stunden").forEach { (m, l) ->
                    Option(l, if (m == 0) sleepMinutes == null else false) { onSleep(m) }
                }
                Header("Bildformat")
                VideoScale.entries.forEach { v -> Option(v.label, v == format) { onFormat(v) } }
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
 * Senderliste im laufenden Bild (links): Logo, Nummer, Name und aktuelle Sendung.
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
            Modifier.fillMaxHeight().widthIn(max = 380.dp).fillMaxWidth(0.42f)
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

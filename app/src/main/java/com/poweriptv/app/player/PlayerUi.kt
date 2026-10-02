package com.poweriptv.app.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
                if (audio.size <= 1) {
                    Text(audio.firstOrNull()?.label ?: "Keine Auswahl verfuegbar", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else audio.forEach { a -> Option(a.label, a.selected) { onAudio(a) } }
                Header("Untertitel")
                if (subtitles.none { it.key != OFF_KEY }) {
                    Text("Keine Untertitel verfuegbar", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else subtitles.forEach { t -> Option(t.label, t.selected) { onSubtitle(t) } }
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

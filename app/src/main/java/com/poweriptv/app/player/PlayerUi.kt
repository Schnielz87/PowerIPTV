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

/** Auswahl "Bildformat" im Player (Zahnrad oben rechts). */
@Composable
fun VideoFormatDialog(current: VideoScale, onSelect: (VideoScale) -> Unit, onDismiss: () -> Unit) {
    val focus = remember { FocusRequester() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Bildformat") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                VideoScale.entries.forEach { v ->
                    Row(
                        Modifier.fillMaxWidth()
                            .then(if (v == current) Modifier.focusRequester(focus) else Modifier)
                            .tvFocus(RoundedCornerShape(8.dp))
                            .clickable { onSelect(v); onDismiss() }
                            .padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = v == current, onClick = { onSelect(v); onDismiss() })
                        Text(v.label, fontWeight = if (v == current) FontWeight.Bold else FontWeight.Normal)
                    }
                }
            }
        },
        confirmButton = { TextButton(modifier = Modifier.tvFocus(RoundedCornerShape(50)), onClick = onDismiss) { Text("Schliessen") } },
    )
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
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

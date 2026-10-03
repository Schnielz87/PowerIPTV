package com.poweriptv.desktop.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.poweriptv.desktop.AppState
import com.poweriptv.desktop.AppVersion
import com.poweriptv.desktop.Screen
import com.poweriptv.desktop.data.AppDirs
import com.poweriptv.desktop.player.Vlc
import com.poweriptv.desktop.ui.ImageLoader
import com.poweriptv.desktop.ui.Surface
import com.poweriptv.desktop.ui.handCursor
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SettingsScreen(app: AppState) {
    val s by app.settings.state.collectAsState()
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            Modifier.widthIn(max = 860.dp).fillMaxWidth().verticalScroll(rememberScrollState()).padding(28.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Text("Einstellungen", style = MaterialTheme.typography.headlineMedium)

            Card("Zugang") {
                Text("Aktiver Zugang: ${app.profile?.name ?: "–"}")
                app.source?.lastRefresh?.takeIf { it > 0 }?.let {
                    Text(
                        "Playlist zuletzt aktualisiert: " + SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.GERMANY).format(Date(it)),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(onClick = { app.refresh() }, enabled = !app.refreshing, modifier = Modifier.handCursor()) {
                        Text(if (app.refreshing) "Wird aktualisiert …" else "Playlist aktualisieren")
                    }
                    OutlinedButton(onClick = { app.navigate(Screen.Profiles) }, modifier = Modifier.handCursor()) { Text("Zugänge verwalten") }
                }
            }

            Card("Player") {
                Toggle("Hardware-Beschleunigung (Grafikkarte dekodiert das Video)", s.hardwareDecoding) { v -> app.settings.update { it.copy(hardwareDecoding = v) } }
                Toggle("Nächste Folge automatisch abspielen", s.autoNextEpisode) { v -> app.settings.update { it.copy(autoNextEpisode = v) } }
                Text("Puffer (bei Rucklern erhöhen)")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(800 to "Kurz", 1500 to "Normal", 3000 to "Groß", 6000 to "Sehr groß").forEach { (ms, l) ->
                        FilterChip(selected = s.networkCaching == ms, onClick = { app.settings.update { it.copy(networkCaching = ms) } }, label = { Text(l) }, modifier = Modifier.handCursor())
                    }
                }
                Text("Bildformat (Standard)")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AspectModes.forEach { (k, l) ->
                        FilterChip(selected = s.aspect == k, onClick = { app.settings.update { it.copy(aspect = k) } }, label = { Text(l) }, modifier = Modifier.handCursor())
                    }
                }
                Vlc.error?.let { Text("VLC: $it", color = MaterialTheme.colorScheme.error) }
            }

            Card("Allgemein") {
                Toggle("Start-Klang beim Öffnen", s.introSound) { v -> app.settings.update { it.copy(introSound = v) } }
                Toggle("Im Vollbild starten", s.startFullscreen) { v -> app.settings.update { it.copy(startFullscreen = v) } }
                var cleared by remember { mutableStateOf(false) }
                OutlinedButton(onClick = { ImageLoader.clearDisk(); cleared = true }, modifier = Modifier.handCursor()) {
                    Text(if (cleared) "Bilder-Cache geleert" else "Bilder-Cache leeren")
                }
                Text("Daten-Ordner: ${AppDirs.root.absolutePath}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            Card("Tastenkürzel im Player") {
                Shortcuts.forEach { (k, d) ->
                    Row { Text(k, modifier = Modifier.width(220.dp), color = MaterialTheme.colorScheme.tertiary); Text(d) }
                }
            }

            Card("Über") {
                Text("Portiva – PowerIPTV für Windows · Version $AppVersion")
                Text("Wiedergabe mit VLC (libVLC) · Daten bleiben auf diesem PC.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

val AspectModes = listOf("fit" to "Original", "fill" to "Zoomen", "stretch" to "Strecken", "16:9" to "16:9", "4:3" to "4:3")

val Shortcuts = listOf(
    "Leertaste / K" to "Pause / Weiter",
    "← / →" to "10 Sekunden zurück / vor",
    "Umschalt + ← / →" to "1 Minute zurück / vor",
    "↑ / ↓ oder Mausrad" to "Lautstärke (Live TV: ↑/↓ = Sender wechseln)",
    "Bild ↑ / Bild ↓" to "Sender wechseln (Live TV)",
    "M" to "Ton aus / an",
    "F, F11 oder Doppelklick" to "Vollbild",
    "L" to "Senderliste (Live TV)",
    "N" to "Nächste Folge (Serien)",
    "Esc" to "Vollbild verlassen / Player schließen",
)

@Composable
private fun Card(title: String, content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Surface).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleLarge)
        content()
    }
}

@Composable
private fun Toggle(label: String, value: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(12.dp))
        Switch(value, onChange, modifier = Modifier.handCursor())
    }
}

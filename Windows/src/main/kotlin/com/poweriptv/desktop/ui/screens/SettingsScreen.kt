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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.material3.TextButton
import androidx.compose.material3.OutlinedTextField
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
                    listOf(1500 to "Kurz", 3000 to "Normal", 6000 to "Groß", 10000 to "Sehr groß").forEach { (ms, l) ->
                        FilterChip(selected = s.networkCaching == ms, onClick = { app.settings.update { it.copy(networkCaching = ms) } }, label = { Text(l) }, modifier = Modifier.handCursor())
                    }
                }
                Text("Bildformat (Standard)")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AspectModes.forEach { (k, l) ->
                        FilterChip(selected = s.aspect == k, onClick = { app.settings.update { it.copy(aspect = k) } }, label = { Text(l) }, modifier = Modifier.handCursor())
                    }
                }
                Text("Untertitel-Größe")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("KLEIN" to "Klein", "NORMAL" to "Normal", "GROSS" to "Groß", "SEHR_GROSS" to "Sehr groß").forEach { (k, l) ->
                        FilterChip(selected = s.subtitleSize == k, onClick = { app.settings.update { it.copy(subtitleSize = k) } }, label = { Text(l) }, modifier = Modifier.handCursor())
                    }
                }
                Toggle("Untertitel mit dunklem Hintergrund", s.subtitleBackground) { v -> app.settings.update { it.copy(subtitleBackground = v) } }
                Text("Vorschaubilder beim Spulen")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("AUTO" to "Automatisch", "ALWAYS" to "Immer parallel", "OFF" to "Aus").forEach { (k, l) ->
                        FilterChip(selected = s.scrubPreview == k, onClick = { app.settings.update { it.copy(scrubPreview = k, scrubBlocked = false) } }, label = { Text(l) }, modifier = Modifier.handCursor())
                    }
                }
                Text("„Automatisch“: Vorschaubilder nur, wenn dein Zugang eine zweite Verbindung frei hat – sonst springt der Film direkt. „Immer parallel“ braucht eine zweite Verbindung.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Stabil-Modus (gegen Stocken)")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("AUTO" to "Automatisch", "ON" to "Immer an", "OFF" to "Aus").forEach { (k, l) ->
                        FilterChip(selected = s.stableMode == k, onClick = { app.settings.update { it.copy(stableMode = k) } }, label = { Text(l) }, modifier = Modifier.handCursor())
                    }
                }
                Text("Größerer Puffer: der Sender startet etwas später, läuft dafür auch bei Aussetzern weiter. „Automatisch“ schaltet ihn nach erkanntem Stocken für 24 Stunden zu.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Bildschärfe")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("OFF" to "Aus", "LIGHT" to "Leicht", "STRONG" to "Stark").forEach { (k, l) ->
                        FilterChip(selected = s.sharpen == k, onClick = { app.settings.update { it.copy(sharpen = k) } }, label = { Text(l) }, modifier = Modifier.handCursor())
                    }
                }
                Text("Dezenter Schärfe-Filter, vor allem für SD-Sender. Wirkt beim nächsten Sender/Film.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Live-TV-Format (Xtream)")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("ts" to "MPEG-TS (.ts)", "m3u8" to "HLS (.m3u8)").forEach { (k, l) ->
                        FilterChip(selected = s.liveFormat == k, onClick = { app.settings.update { it.copy(liveFormat = k) } }, label = { Text(l) }, modifier = Modifier.handCursor())
                    }
                }
                Vlc.error?.let { Text("VLC: $it", color = MaterialTheme.colorScheme.error) }
            }

            Card("Downloads & Aufnahmen") {
                Text("Parallele Verbindungen pro Download")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(0 to "Automatisch", 1 to "1", 2 to "2", 3 to "3", 4 to "4").forEach { (n, l) ->
                        FilterChip(selected = s.downloadConnections == n, onClick = { app.settings.update { it.copy(downloadConnections = n) } }, label = { Text(l) }, modifier = Modifier.handCursor())
                    }
                }
                Text("Viele Anbieter drosseln pro Verbindung – mehrere Verbindungen laden schneller (zählen aber zum Stream-Limit).", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                FolderRow("Download-Ordner", app.downloadDir()) { f -> app.settings.update { it.copy(downloadDir = f) } }
                FolderRow("Aufnahme-Ordner", app.recordingDir()) { f -> app.settings.update { it.copy(recordingDir = f) } }
            }

            Card("KI-Empfehlungen (ChatGPT)") {
                SecretKeyEditor(
                    label = "OpenAI-API-Schlüssel", has = app.ai.hasApiKey(),
                    onSave = { app.ai.setApiKey(it) }, onTest = { app.ai.testConnection() },
                )
                var model by remember { mutableStateOf(s.aiModel) }
                var url by remember { mutableStateOf(s.aiBaseUrl) }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(model, { model = it }, label = { Text("Modell") }, singleLine = true, modifier = Modifier.weight(1f))
                    OutlinedTextField(url, { url = it }, label = { Text("API-Adresse") }, singleLine = true, modifier = Modifier.weight(2f))
                    OutlinedButton(onClick = { app.settings.update { it.copy(aiModel = model.trim().ifBlank { "gpt-4o-mini" }, aiBaseUrl = url.trim().trimEnd('/').ifBlank { "https://api.openai.com/v1" }) } }, modifier = Modifier.handCursor()) { Text("Übernehmen") }
                }
                Text("Den Schlüssel bekommst du unter platform.openai.com → API keys. Es werden nur Titel übertragen, nie Zugangsdaten.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            Card("Altersfreigabe (FSK)") {
                SecretKeyEditor(
                    label = "TMDB-API-Schlüssel (optional)", has = app.ageRatings.hasApiKey(),
                    onSave = { app.ageRatings.setApiKey(it) }, onTest = { app.ageRatings.test() },
                )
                Text("Mit TMDB-Schlüssel zeigt die Detailseite die offizielle deutsche FSK; ohne Schlüssel die Angabe deines Anbieters.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            Card("Sicherheit") {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(onClick = { app.navigate(Screen.Parental) }, modifier = Modifier.handCursor()) { Text("Kindersicherung") }
                    OutlinedButton(onClick = { app.navigate(Screen.Vpn) }, modifier = Modifier.handCursor()) { Text("VPN & Sicherheit") }
                }
                var ua by remember { mutableStateOf(s.userAgent) }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(ua, { ua = it }, label = { Text("User-Agent (nur ändern, wenn dein Anbieter es verlangt)") }, singleLine = true, modifier = Modifier.weight(1f))
                    OutlinedButton(onClick = { app.settings.update { it.copy(userAgent = ua.ifBlank { com.poweriptv.desktop.data.USER_AGENT }) } }, modifier = Modifier.handCursor()) { Text("Übernehmen") }
                }
            }

            Card("Sichern & Wiederherstellen") {
                BackupSection(app)
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
    "R" to "Aufnahme starten (Live TV)",
    "B" to "Zurück zum vorherigen Sender",
    "Enter" to "Intro überspringen (wenn eingeblendet)",
    "Z" to "Zoomen an / aus (schwarze Balken weg)",
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

@Composable
private fun FolderRow(label: String, current: java.io.File, onPick: (String) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label)
            Text(current.absolutePath, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        OutlinedButton(onClick = {
            val chooser = javax.swing.JFileChooser(current).apply { fileSelectionMode = javax.swing.JFileChooser.DIRECTORIES_ONLY; dialogTitle = label }
            if (chooser.showOpenDialog(null) == javax.swing.JFileChooser.APPROVE_OPTION) onPick(chooser.selectedFile.absolutePath)
        }, modifier = Modifier.handCursor()) { Text("Ändern") }
        Spacer(Modifier.width(8.dp))
        OutlinedButton(onClick = { com.poweriptv.desktop.ui.openFolder(current) }, modifier = Modifier.handCursor()) { Text("Öffnen") }
    }
}

/** Geheimer Schluessel: eingeben, verschluesselt speichern, testen, entfernen. */
@Composable
private fun SecretKeyEditor(label: String, has: Boolean, onSave: (String?) -> Unit, onTest: suspend () -> String) {
    val scope = rememberCoroutineScope()
    var value by remember { mutableStateOf("") }
    var saved by remember { mutableStateOf(has) }
    var info by remember { mutableStateOf<String?>(null) }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value, { value = it }, singleLine = true, modifier = Modifier.weight(1f),
            label = { Text(if (saved) "$label (gespeichert – neu eingeben zum Ersetzen)" else label) },
            visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
        )
        OutlinedButton(enabled = value.isNotBlank(), onClick = { onSave(value.trim()); value = ""; saved = true; info = "Verschlüsselt gespeichert" }, modifier = Modifier.handCursor()) { Text("Speichern") }
        OutlinedButton(enabled = saved, onClick = {
            info = "Teste …"
            scope.launch { info = runCatching { withContext(Dispatchers.IO) { onTest() } }.getOrElse { it.message ?: "Fehler" } }
        }, modifier = Modifier.handCursor()) { Text("Testen") }
        if (saved) TextButton(onClick = { onSave(null); saved = false; info = "Entfernt" }, modifier = Modifier.handCursor()) { Text("Entfernen") }
    }
    info?.let { Text(it, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall) }
}

/** Sicherung im selben Format wie die Android-App – Handy-Sicherung am PC einspielen und umgekehrt. */
@Composable
private fun BackupSection(app: AppState) {
    var includeSecrets by remember { mutableStateOf(true) }
    var password by remember { mutableStateOf("") }
    var info by remember { mutableStateOf<String?>(null) }
    var restoreText by remember { mutableStateOf<String?>(null) }
    Text("Sichert Zugänge, Favoriten & Listen, Verlauf, Weiterschauen, Kindersicherung, Kategorien, Einstellungen und Schlüssel – im selben Format wie die Handy-App.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Toggle("Zugangsdaten, API-Schlüssel und VPN mitsichern", includeSecrets) { includeSecrets = it }
    OutlinedTextField(password, { password = it }, label = { Text("Passwort (empfohlen, verschlüsselt die Datei)") }, singleLine = true,
        visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedButton(onClick = {
            val f = com.poweriptv.desktop.ui.chooseSaveFile("Sicherung speichern", "PowerIPTV-Sicherung.json") ?: return@OutlinedButton
            info = runCatching { app.backup.export(f, includeSecrets, password.ifBlank { null }); "Gespeichert: ${f.name}" }.getOrElse { "Fehler: ${it.message}" }
        }, modifier = Modifier.handCursor()) { Text("Sicherung erstellen") }
        OutlinedButton(onClick = {
            val f = com.poweriptv.desktop.ui.chooseFile("Sicherung wählen (vom Handy oder PC)", ".json", ".txt") ?: return@OutlinedButton
            val text = runCatching { f.readText() }.getOrNull() ?: run { info = "Datei konnte nicht gelesen werden"; return@OutlinedButton }
            if (app.backup.isEncrypted(text) && password.isBlank()) restoreText = text
            else info = runCatching { "Wiederhergestellt: " + app.backup.restore(text, password.ifBlank { null }).also { app.reloadAfterRestore() } }.getOrElse { "Fehler: ${it.message}" }
        }, modifier = Modifier.handCursor()) { Text("Wiederherstellen") }
    }
    info?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
    restoreText?.let { text ->
        var pw by remember { mutableStateOf("") }
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { restoreText = null },
            title = { Text("Passwort der Sicherung") },
            text = { OutlinedTextField(pw, { pw = it }, singleLine = true, visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation()) },
            confirmButton = {
                TextButton(onClick = {
                    info = runCatching { "Wiederhergestellt: " + app.backup.restore(text, pw).also { app.reloadAfterRestore() } }.getOrElse { "Fehler: ${it.message}" }
                    restoreText = null
                }) { Text("Wiederherstellen") }
            },
            dismissButton = { TextButton(onClick = { restoreText = null }) { Text("Abbrechen") } },
        )
    }
}

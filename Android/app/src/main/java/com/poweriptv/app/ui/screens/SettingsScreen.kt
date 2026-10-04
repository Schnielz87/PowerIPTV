package com.poweriptv.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.poweriptv.app.AppContainer
import com.poweriptv.app.BuildConfigInfo
import com.poweriptv.app.data.LiveFormat
import com.poweriptv.app.data.Orientation
import com.poweriptv.app.data.VideoScale
import com.poweriptv.app.data.PlayerEngine
import com.poweriptv.app.util.LocalIsTv
import com.poweriptv.app.data.SettingsRepository
import com.poweriptv.app.ui.components.PowerTopBar
import com.poweriptv.app.ui.components.tvFocus
import com.poweriptv.app.parental.PinDialog
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.text.input.PasswordVisualTransformation
import kotlinx.coroutines.launch

@Composable
fun SettingsSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(title, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        content()
    }
}

@Composable
fun SwitchRow(title: String, subtitle: String?, checked: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().tvFocus().clickable(enabled = enabled) { onChange(!checked) }.padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title)
            subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = onChange, enabled = enabled)
    }
}

@Composable
private fun NavRow(title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().tvFocus().clickable(onClick = onClick).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null)
    }
}

@Composable
fun SettingsScreen(
    container: AppContainer,
    onBack: () -> Unit,
    onVpn: () -> Unit,
    onParental: () -> Unit,
    onRecordings: () -> Unit,
) {
    var unlocked by remember { mutableStateOf(!container.parental.settingsNeedPin()) }
    if (!unlocked) {
        PinDialog(container.parental, message = "Die Einstellungen sind mit einer PIN geschuetzt", onDismiss = onBack, onSuccess = { unlocked = true })
        return
    }
    val s = container.settings
    val scope = rememberCoroutineScope()
    val aiModel by s.aiModel.collectAsState()
    val aiUrl by s.aiBaseUrl.collectAsState()
    var keyInput by remember { mutableStateOf("") }
    var modelInput by remember { mutableStateOf(aiModel) }
    var urlInput by remember { mutableStateOf(aiUrl) }
    var aiStatus by remember { mutableStateOf<String?>(if (container.ai.hasApiKey()) "ChatGPT-Konto verbunden" else null) }
    val liveFormat by s.liveFormat.collectAsState()
    val ua by s.userAgent.collectAsState()
    var uaInput by remember { mutableStateOf(ua) }

    Scaffold(
        topBar = { PowerTopBar("Einstellungen", onBack = onBack) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            SettingsSection("Sicherheit") {
                Row(
                    Modifier.fillMaxWidth().tvFocus().clickable(onClick = onVpn).padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("VPN & Kill-Switch")
                        Text(
                            "App-Daten per WireGuard-VPN absichern",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null)
                }
                NavRow("Kindersicherung", "PIN, gesperrte Kategorien, Erwachseneninhalte", onParental)
                NavRow("Aufnahmen", "Geplante und fertige Aufnahmen verwalten", onRecordings)
            }

            SettingsSection("KI-Empfehlungen (ChatGPT)") {
                Text(
                    "Verbinde dein ChatGPT/OpenAI-Konto ueber einen API-Schluessel (platform.openai.com → API keys). " +
                        "Uebertragen werden nur Titel aus Verlauf, Favoriten und Katalog – keine Zugangsdaten.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = keyInput, onValueChange = { keyInput = it },
                    label = { Text(if (container.ai.hasApiKey()) "Neuer API-Schluessel (sk-...)" else "API-Schluessel (sk-...)") },
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                    visualTransformation = PasswordVisualTransformation(),
                )
                OutlinedTextField(
                    value = modelInput, onValueChange = { modelInput = it },
                    label = { Text("Modell") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = urlInput, onValueChange = { urlInput = it },
                    label = { Text("API-Adresse (OpenAI-kompatibel)") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                )
                Row {
                    TextButton(onClick = {
                        if (keyInput.isNotBlank()) container.ai.setApiKey(keyInput)
                        s.setAiModel(modelInput); s.setAiBaseUrl(urlInput)
                        keyInput = ""
                        aiStatus = "Pruefe Verbindung..."
                        scope.launch {
                            aiStatus = runCatching { container.ai.testConnection() }.getOrElse { "Fehler: ${it.message}" }
                        }
                    }) { Text("Speichern & testen") }
                    if (container.ai.hasApiKey()) TextButton(onClick = {
                        container.ai.setApiKey(null); aiStatus = "Verbindung entfernt"
                    }) { Text("Trennen") }
                }
                aiStatus?.let { Text(it, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall) }
            }

            SettingsSection("Sichern & Wiederherstellen") {
                val ctx = androidx.compose.ui.platform.LocalContext.current
                var withSecrets by remember { mutableStateOf(true) }
                var backupPw by remember { mutableStateOf("") }
                var backupStatus by remember { mutableStateOf<String?>(null) }
                var pendingRestore by remember { mutableStateOf<String?>(null) }
                fun restart() {
                    val i = ctx.packageManager.getLaunchIntentForPackage(ctx.packageName)
                        ?.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK)
                    if (i != null) ctx.startActivity(i)
                    Runtime.getRuntime().exit(0)
                }
                fun doRestore(text: String) {
                    scope.launch {
                        backupStatus = "Stelle wieder her..."
                        val r = runCatching {
                            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { container.backup.restore(text, backupPw.ifBlank { null }) }
                        }
                        r.onSuccess {
                            pendingRestore = null
                            backupStatus = "Wiederhergestellt – App startet neu"
                            kotlinx.coroutines.delay(1200); restart()
                        }.onFailure { backupStatus = "Fehler: ${it.message}" }
                    }
                }
                val exportLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
                    androidx.activity.result.contract.ActivityResultContracts.CreateDocument("application/json"),
                ) { uri ->
                    if (uri == null) return@rememberLauncherForActivityResult
                    scope.launch {
                        backupStatus = "Sichere..."
                        backupStatus = runCatching {
                            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { container.backup.export(uri, withSecrets, backupPw.ifBlank { null }) }
                            if (backupPw.isBlank() && withSecrets) "Gesichert (unverschluesselt – Datei enthaelt Zugangsdaten, sicher aufbewahren!)"
                            else "Sicherung erstellt"
                        }.getOrElse { "Fehler: ${it.message}" }
                    }
                }
                val importLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
                    androidx.activity.result.contract.ActivityResultContracts.OpenDocument(),
                ) { uri ->
                    if (uri == null) return@rememberLauncherForActivityResult
                    scope.launch {
                        val text = runCatching { kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { container.backup.readText(uri) } }
                            .getOrElse { backupStatus = "Fehler: ${it.message}"; return@launch }
                        pendingRestore = text
                        if (container.backup.isEncrypted(text) && backupPw.isBlank()) backupStatus = "Diese Sicherung ist verschluesselt – Passwort eingeben und \"Wiederherstellen\" tippen"
                        else backupStatus = "Sicherung geladen – \"Wiederherstellen\" tippen (aktuelle Einstellungen werden ueberschrieben)"
                    }
                }
                Text(
                    "Profile, Favoriten, Listen, Verlauf, Weiterschauen, Kategorien, Kindersicherung und Einstellungen als Datei sichern – " +
                        "z.B. fuer den Umzug vom Handy auf den Fire TV.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                SwitchRow("Zugangsdaten mitsichern", "Profile (Benutzer/Passwort), VPN-Konfiguration und API-Schluessel", withSecrets) { withSecrets = it }
                OutlinedTextField(
                    value = backupPw, onValueChange = { backupPw = it },
                    label = { Text("Passwort (optional, verschluesselt die Datei)") },
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                    visualTransformation = PasswordVisualTransformation(),
                )
                Row {
                    TextButton(onClick = {
                        val name = "PowerIPTV-Sicherung-" + java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.GERMANY).format(java.util.Date()) + ".json"
                        runCatching { exportLauncher.launch(name) }.onFailure { backupStatus = "Dateiauswahl ist auf diesem Geraet nicht verfuegbar" }
                    }) { Text("Sicherung erstellen") }
                    TextButton(onClick = {
                        runCatching { importLauncher.launch(arrayOf("application/json", "text/plain", "application/octet-stream", "*/*")) }
                            .onFailure { backupStatus = "Dateiauswahl ist auf diesem Geraet nicht verfuegbar" }
                    }) { Text("Sicherung laden") }
                    pendingRestore?.let { t -> TextButton(onClick = { doRestore(t) }) { Text("Wiederherstellen") } }
                }
                backupStatus?.let { Text(it, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall) }
            }

            SettingsSection("Altersfreigaben (FSK)") {
                var tmdbInput by remember { mutableStateOf("") }
                var tmdbStatus by remember { mutableStateOf<String?>(if (container.ageRatings.hasApiKey()) "TMDB verbunden – offizielle FSK wird angezeigt" else null) }
                Text(
                    "Die FSK wird auf der Film-/Serienseite angezeigt. Ohne Schluessel nur, wenn dein Anbieter eine Altersangabe liefert. " +
                        "Fuer die offizielle deutsche FSK bei (fast) allen Titeln: kostenlosen Schluessel bei themoviedb.org anlegen " +
                        "(Konto → Einstellungen → API) und hier eintragen.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = tmdbInput, onValueChange = { tmdbInput = it },
                    label = { Text(if (container.ageRatings.hasApiKey()) "Neuer TMDB-API-Schluessel" else "TMDB-API-Schluessel") },
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                    visualTransformation = PasswordVisualTransformation(),
                )
                Row {
                    TextButton(onClick = {
                        if (tmdbInput.isNotBlank()) container.ageRatings.setApiKey(tmdbInput)
                        tmdbInput = ""
                        tmdbStatus = "Pruefe Verbindung..."
                        scope.launch { tmdbStatus = runCatching { container.ageRatings.test() }.getOrElse { "Fehler: ${it.message}" } }
                    }) { Text("Speichern & testen") }
                    if (container.ageRatings.hasApiKey()) TextButton(onClick = {
                        container.ageRatings.setApiKey(null); tmdbStatus = "Verbindung entfernt"
                    }) { Text("Trennen") }
                }
                tmdbStatus?.let { Text(it, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall) }
            }

            val isTv = LocalIsTv.current
            if (!isTv) SettingsSection("Darstellung") {
                val orientation by s.orientation.collectAsState()
                Text("Bildschirmausrichtung", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Orientation.entries.forEach { o ->
                    Row(
                        Modifier.fillMaxWidth().tvFocus().clickable { s.setOrientation(o) },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = orientation == o.name, onClick = { s.setOrientation(o) })
                        Text(o.label)
                    }
                }
            }

            SettingsSection("Live-TV Stream-Format (Xtream)") {
                LiveFormat.entries.forEach { f ->
                    Row(
                        Modifier.fillMaxWidth().clickable { s.setLiveFormat(f); container.source?.clearCache() },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = liveFormat == f.name, onClick = { s.setLiveFormat(f) })
                        Text(f.label)
                    }
                }
            }

            SettingsSection("Downloads") {
                val conn by s.downloadConnections.collectAsState()
                Text(
                    "Parallele Verbindungen pro Download. Viele IPTV-Server drosseln jede einzelne Verbindung – " +
                        "mehrere Verbindungen beschleunigen den Download deutlich. \"Automatisch\" nutzt so viele, " +
                        "wie dein Account erlaubt. Waehrend ein Download alle Verbindungen nutzt, kann gleichzeitiges Schauen blockiert sein.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                listOf(0 to "Automatisch (nach Account-Limit)", 1 to "1 Verbindung", 2 to "2 Verbindungen", 3 to "3 Verbindungen", 4 to "4 Verbindungen").forEach { (v, label) ->
                    Row(
                        Modifier.fillMaxWidth().tvFocus().clickable { s.setDownloadConnections(v) },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = conn == v, onClick = { s.setDownloadConnections(v) })
                        Text(label)
                    }
                }
            }

            SettingsSection("Player") {
                val engine by s.playerEngine.collectAsState()
                Text(
                    "Wiedergabe-Engine. Bei manchen Sendern (z.B. RTL, ProSieben, VOX in SD/MPEG-2) kann der Standard-Player " +
                        "nur Ton abspielen – dann hilft VLC.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                PlayerEngine.entries.forEach { e ->
                    Row(
                        Modifier.fillMaxWidth().tvFocus().clickable { s.setPlayerEngine(e) },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = engine == e.name, onClick = { s.setPlayerEngine(e) })
                        Text(e.label)
                    }
                }
                TextButton(onClick = { s.clearVlcStreams() }) { Text("Gemerkte VLC-Sender zuruecksetzen") }
                val vlcPerf by s.vlcPerformance.collectAsState()
                Text("VLC-Leistung (wirkt beim naechsten Start des VLC-Players)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                listOf(
                    "AUTO" to "Automatisch (TV-Sticks & schwache Geraete: schnell)",
                    "FAST" to "Schnell – fluessig auf schwachen Geraeten",
                    "QUALITY" to "Qualitaet – beste Bildaufbereitung (starke Geraete)",
                ).forEach { (k, l) ->
                    Row(
                        Modifier.fillMaxWidth().tvFocus().clickable { s.setVlcPerformance(k) },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = vlcPerf == k, onClick = { s.setVlcPerformance(k) })
                        Text(l)
                    }
                }
                val introSound by s.introSound.collectAsState()
                SwitchRow("Start-Klang", "Kurzer Klang beim Oeffnen der App", introSound) { s.setIntroSound(it) }
                val afr by s.autoFrameRate.collectAsState()
                val scale by s.videoScale.collectAsState()
                SwitchRow(
                    "Bildwiederholrate automatisch anpassen (AFR)",
                    "Der Fernseher schaltet passend zum Video um (z.B. 50 Hz fuer TV, 24 Hz fuer Filme) – kein Ruckeln. " +
                        "Auf Fire TV zusaetzlich unter Einstellungen → Display → \"Originalbildfrequenz anpassen\" aktivieren.",
                    afr,
                ) { s.setAutoFrameRate(it) }
                val scrub by s.scrubPreview.collectAsState()
                Text("Vorschaubilder beim Spulen auf dem Zeitstrahl", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                com.poweriptv.app.player.ScrubPreviewMode.entries.forEach { m ->
                    Row(
                        Modifier.fillMaxWidth().tvFocus().clickable { s.setScrubPreview(m) },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = scrub == m.name, onClick = { s.setScrubPreview(m) })
                        Text(m.label)
                    }
                }
                Text("Bildformat (im Player auch per blauer Taste wechselbar)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                VideoScale.entries.forEach { v ->
                    Row(
                        Modifier.fillMaxWidth().tvFocus().clickable { s.setVideoScale(v) },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = scale == v.name, onClick = { s.setVideoScale(v) })
                        Text(v.label)
                    }
                }
                OutlinedTextField(
                    value = uaInput,
                    onValueChange = { uaInput = it },
                    label = { Text("User-Agent") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row {
                    TextButton(onClick = { s.setUserAgent(uaInput) }) { Text("Speichern") }
                    TextButton(onClick = {
                        uaInput = SettingsRepository.DEFAULT_UA; s.setUserAgent(uaInput)
                    }) { Text("Standard") }
                }
            }

            SettingsSection("Info") {
                Text("PowerIPTV ${container.updates.currentVersion}")
                Text(
                    "Zugangsdaten und VPN-Konfiguration werden verschluesselt (AES-256, Android Keystore) gespeichert.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

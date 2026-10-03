package com.poweriptv.desktop.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.poweriptv.app.ai.Recommendation
import com.poweriptv.app.data.Category
import com.poweriptv.app.data.ContentType
import com.poweriptv.app.parental.ParentalControl
import com.poweriptv.desktop.AppState
import com.poweriptv.desktop.Screen
import com.poweriptv.desktop.data.DesktopVpn
import com.poweriptv.desktop.data.VpnState
import com.poweriptv.desktop.ui.BrandCyan
import com.poweriptv.desktop.ui.ConfirmDialog
import com.poweriptv.desktop.ui.Danger
import com.poweriptv.desktop.ui.NetImage
import com.poweriptv.desktop.ui.Page
import com.poweriptv.desktop.ui.PinDialog
import com.poweriptv.desktop.ui.SetPinDialog
import com.poweriptv.desktop.ui.SettingsCard
import com.poweriptv.desktop.ui.Success
import com.poweriptv.desktop.ui.SurfaceHigh
import com.poweriptv.desktop.ui.ToggleRow
import com.poweriptv.desktop.ui.Warning
import com.poweriptv.desktop.ui.chooseFile
import com.poweriptv.desktop.ui.handCursor
import com.poweriptv.desktop.ui.openUrl
import com.poweriptv.desktop.ui.typeLabel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** KI-Empfehlungen (ChatGPT) – derselbe Empfehlungs-Code wie in der Android-App. */
@Composable
fun RecommendationsScreen(app: AppState) {
    val src = app.source ?: return
    val lib = app.library ?: return
    val scope = rememberCoroutineScope()
    var wish by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var results by remember { mutableStateOf<List<Recommendation>>(emptyList()) }
    Page("KI-Empfehlungen") {
        Text("Basierend auf deinem Verlauf und deinen Favoriten schlägt ChatGPT passende Filme und Serien aus deinem Angebot vor.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (!app.ai.hasApiKey()) {
            SettingsCard {
                Text("Verbinde dein ChatGPT-Konto: Hinterlege in den Einstellungen deinen OpenAI-API-Schlüssel (platform.openai.com → API keys).")
                OutlinedButton(onClick = { app.navigate(Screen.Settings) }, modifier = Modifier.handCursor()) { Text("Zu den Einstellungen") }
            }
            return@Page
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(wish, { wish = it }, placeholder = { Text("Optional: z.B. „lustige Komödie“ oder „spannende Krimiserie“") }, singleLine = true, modifier = Modifier.weight(1f))
            Button(
                enabled = !loading,
                onClick = {
                    loading = true; error = null
                    scope.launch {
                        val pid = src.profile.id
                        val hist = app.parental.visible(pid, lib.history.value.map { it.item })
                        val favs = app.parental.visible(pid, lib.favorites.value)
                        runCatching { app.ai.recommend(src, hist, favs, wish) }
                            .onSuccess { results = it }.onFailure { error = it.message }
                        loading = false
                    }
                },
                modifier = Modifier.handCursor(),
            ) { Icon(Icons.Filled.AutoAwesome, null); Spacer(Modifier.width(6.dp)); Text("Empfehlen") }
        }
        if (loading) Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp); Spacer(Modifier.width(8.dp)); Text("ChatGPT denkt nach …")
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        results.forEach { r ->
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(com.poweriptv.desktop.ui.Surface)
                    .then(if (r.item != null) Modifier.handCursor().clickable { app.navigate(Screen.Detail(r.item!!)) } else Modifier)
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                NetImage(listOf(r.item?.logo), Modifier.width(70.dp).aspectRatio(2f / 3f).clip(RoundedCornerShape(6.dp)).background(SurfaceHigh))
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(r.item?.name ?: r.title, style = MaterialTheme.typography.titleMedium)
                    Text(if (r.type == ContentType.SERIES) "Serie" else "Film", color = BrandCyan, style = MaterialTheme.typography.bodySmall)
                    Text(r.reason, style = MaterialTheme.typography.bodyMedium)
                    if (r.item == null) Text("Nicht im Angebot gefunden", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

/** Kindersicherung – derselbe Code (ParentalControl) wie in der Android-App. */
@Composable
fun ParentalScreen(app: AppState) {
    val pc = app.parental
    val src = app.source
    val enabled by pc.enabled.collectAsState()
    val autoAdult by pc.autoAdult.collectAsState()
    val protectSettings by pc.protectSettings.collectAsState()
    val locked by pc.lockedKeys.collectAsState()
    var unlocked by remember { mutableStateOf(!pc.hasPin() || pc.sessionUnlocked.value) }
    var setPin by remember { mutableStateOf(false) }
    var confirmReset by remember { mutableStateOf(false) }
    var type by remember { mutableStateOf(ContentType.LIVE) }
    var categories by remember(type, src) { mutableStateOf<List<Category>>(emptyList()) }
    LaunchedEffect(type, src) { categories = withContext(Dispatchers.IO) { src?.let { s -> runCatching { s.categories(type) }.getOrDefault(emptyList()) }.orEmpty() } }

    if (!unlocked) {
        PinDialog(pc, onDismiss = { app.back() }, onSuccess = { unlocked = true })
        return
    }
    Page("Kindersicherung") {
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                .background(if (enabled) Success.copy(alpha = 0.18f) else Warning.copy(alpha = 0.18f)).padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(if (enabled) Icons.Filled.Lock else Icons.Filled.Shield, null, tint = if (enabled) Success else Warning)
            Spacer(Modifier.width(10.dp))
            Text(if (enabled) "Kindersicherung ist AKTIV" else "Kindersicherung ist AUS – derzeit ist nichts gesperrt", fontWeight = FontWeight.Bold)
        }
        SettingsCard("PIN") {
            Text(if (pc.hasPin()) "Eine PIN ist festgelegt." else "Lege zuerst eine PIN fest, um die Kindersicherung zu aktivieren.")
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = { setPin = true }, modifier = Modifier.handCursor()) { Text(if (pc.hasPin()) "PIN ändern" else "PIN festlegen") }
                if (pc.hasPin()) OutlinedButton(onClick = { confirmReset = true }, modifier = Modifier.handCursor()) { Text("Entfernen") }
            }
        }
        SettingsCard("Optionen") {
            ToggleRow("Kindersicherung aktiv", enabled, enabled = pc.hasPin()) { pc.setEnabled(it) }
            ToggleRow("Erwachseneninhalte automatisch sperren", autoAdult, "Kategorien mit XXX, Adult, 18+, Erotik usw. werden erkannt.") { pc.setAutoAdult(it) }
            ToggleRow("Einstellungen mit PIN schützen", protectSettings, "Auch VPN, Zugänge und KI-Einstellungen.") { pc.setProtectSettings(it) }
        }
        SettingsCard("Gesperrte Kategorien") {
            Text("Gesperrte Kategorien, Titel und Sender sind überall nur mit PIN sichtbar (auch in Verlauf, Favoriten, Suche und Weiterschauen).", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ContentType.entries.forEach { t -> FilterChip(selected = type == t, onClick = { type = t }, label = { Text(typeLabel(t)) }, modifier = Modifier.handCursor()) }
            }
            if (src == null) Text("Kein Zugang ausgewählt")
            Column(Modifier.heightIn(max = 520.dp)) {
                androidx.compose.foundation.lazy.LazyColumn {
                    items(categories.size) { i ->
                        val c = categories[i]
                        val manual = src != null && pc.key(src.profile.id, type, c.id) in locked
                        val auto = autoAdult && ParentalControl.isAdult(c.name)
                        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(c.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                if (auto) Text("automatisch gesperrt", style = MaterialTheme.typography.bodySmall, color = Danger)
                            }
                            Switch(checked = manual || auto, enabled = !auto, onCheckedChange = { v -> src?.let { pc.setLocked(it.profile.id, type, c.id, v) } }, modifier = Modifier.handCursor())
                        }
                    }
                }
            }
        }
    }
    if (setPin) SetPinDialog(onDismiss = { setPin = false }) { pin -> pc.setPin(pin); pc.verify(pin); setPin = false }
    if (confirmReset) ConfirmDialog("PIN entfernen?", "Die Kindersicherung wird komplett zurückgesetzt.", "Entfernen", onDismiss = { confirmReset = false }) { pc.reset() }
}

/** VPN & Sicherheit – WireGuard wie in der Android-App, inkl. Kill-Switch. */
@Composable
fun VpnScreen(app: AppState) {
    val vpn = app.vpn
    val scope = rememberCoroutineScope()
    val state by vpn.state.collectAsState()
    val error by vpn.error.collectAsState()
    val hasConfig by vpn.hasConfig.collectAsState()
    val settings by app.settings.state.collectAsState()
    var info by remember { mutableStateOf<String?>(null) }
    var editing by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var tick by remember { mutableStateOf(0) }
    LaunchedEffect(Unit) { while (true) { vpn.refreshState(); tick++; kotlinx.coroutines.delay(3000) } }
    val protected = remember(state, tick) { vpn.isProtected() }
    val external = remember(state, tick) { state != VpnState.CONNECTED && vpn.isSystemVpnActive() }

    Page("VPN & Sicherheit") {
        SettingsCard("Status") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Shield, null, tint = if (protected) Success else Danger)
                Spacer(Modifier.width(10.dp))
                Text(
                    when {
                        state == VpnState.CONNECTING -> "Verbinde …"
                        state == VpnState.CONNECTED -> "Geschützt (Portiva VPN)"
                        external && protected -> "Geschützt (externes VPN)"
                        state == VpnState.ERROR -> "Verbindungsfehler"
                        else -> "Nicht geschützt"
                    },
                    fontWeight = FontWeight.Bold,
                )
            }
            vpn.serverEndpoint()?.let { Text("Server: $it", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            if (!vpn.wireGuardInstalled) {
                Text("Für das eingebaute VPN wird einmalig „WireGuard für Windows“ benötigt (kostenlos, offiziell).", color = Warning)
                OutlinedButton(onClick = { openUrl(DesktopVpn.DOWNLOAD_URL) }, modifier = Modifier.handCursor()) { Text("WireGuard herunterladen") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (state == VpnState.CONNECTED) {
                    Button(onClick = { scope.launch { vpn.disconnect() } }, modifier = Modifier.handCursor()) { Text("VPN trennen") }
                } else {
                    Button(
                        enabled = hasConfig && vpn.wireGuardInstalled && state != VpnState.CONNECTING,
                        onClick = { scope.launch { vpn.connect() } }, modifier = Modifier.handCursor(),
                    ) { Text("VPN verbinden") }
                }
            }
            if (!hasConfig) Text("Zuerst eine WireGuard-Konfiguration hinzufügen.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("Beim Verbinden/Trennen fragt Windows nach Administrator-Rechten.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        SettingsCard("WireGuard-Konfiguration") {
            Text("Lade bei deinem VPN-Anbieter (z.B. Mullvad, ProtonVPN, Surfshark, NordVPN, IVPN, AirVPN oder eigener Server) eine WireGuard-Konfigurationsdatei (.conf) herunter und importiere sie hier.")
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = {
                    chooseFile("WireGuard-Konfiguration wählen", ".conf")?.let { f ->
                        info = vpn.saveConfig(runCatching { f.readText() }.getOrDefault("")).fold({ "Konfiguration importiert und verschlüsselt gespeichert" }, { it.message })
                    }
                }, modifier = Modifier.handCursor()) { Text(".conf importieren") }
                OutlinedButton(onClick = { editing = true }, modifier = Modifier.handCursor()) { Text(if (hasConfig) "Bearbeiten" else "Einfügen") }
                if (hasConfig) OutlinedButton(onClick = { confirmDelete = true }, modifier = Modifier.handCursor()) { Text("Konfiguration löschen") }
            }
            info?.let { Text(it, color = BrandCyan) }
        }
        SettingsCard("Optionen") {
            ToggleRow("Kill-Switch (VPN erzwingen)", settings.vpnRequired, "Blockiert Login, Playlisten, Streams und Bilder, solange kein VPN verbunden ist.") { v -> app.settings.update { it.copy(vpnRequired = v) } }
            ToggleRow("Beim App-Start automatisch verbinden", settings.vpnAutoConnect) { v -> app.settings.update { it.copy(vpnAutoConnect = v) } }
            ToggleRow(
                "Externe VPN-Apps akzeptieren" + if (vpn.isSystemVpnActive()) " (Aktuell erkannt)" else "", settings.acceptExternalVpn,
                "Ein VPN einer anderen App (z.B. NordVPN, Surfshark) zählt für den Kill-Switch ebenfalls als Schutz.",
            ) { v -> app.settings.update { it.copy(acceptExternalVpn = v) } }
            Text("Hinweis: Unter Windows schützt das VPN den ganzen PC (Split-Tunneling nur für Portiva gibt es bei WireGuard für Windows nicht).", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    if (editing) {
        var text by remember { mutableStateOf(vpn.configText().orEmpty()) }
        var err by remember { mutableStateOf<String?>(null) }
        AlertDialog(
            onDismissRequest = { editing = false },
            title = { Text("WireGuard-Konfiguration") },
            text = {
                Column {
                    OutlinedTextField(
                        text, { text = it }, modifier = Modifier.fillMaxWidth().height(320.dp),
                        placeholder = { Text("[Interface]\nPrivateKey = ...\nAddress = ...\n\n[Peer]\nPublicKey = ...\nEndpoint = ...") },
                        textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    )
                    err?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                }
            },
            confirmButton = {
                TextButton(onClick = { vpn.saveConfig(text).onSuccess { editing = false; info = "Konfiguration gespeichert" }.onFailure { err = it.message } }) { Text("Speichern") }
            },
            dismissButton = { TextButton(onClick = { editing = false }) { Text("Abbrechen") } },
        )
    }
    if (confirmDelete) ConfirmDialog("Konfiguration löschen?", "Das VPN wird getrennt und die gespeicherte Konfiguration entfernt.", "Löschen", onDismiss = { confirmDelete = false }) {
        scope.launch { vpn.deleteConfig() }
    }
}

/** Kleines VPN-Abzeichen (Startseite) – wie Android „Kein VPN“ / „VPN aktiv“. */
@Composable
fun VpnBadge(app: AppState) {
    val state by app.vpn.state.collectAsState()
    var tick by remember { mutableStateOf(0) }
    LaunchedEffect(Unit) { while (true) { app.vpn.refreshState(); tick++; kotlinx.coroutines.delay(5000) } }
    val ok = remember(state, tick) { app.vpn.isProtected() }
    Row(
        Modifier.clip(RoundedCornerShape(50)).background(if (ok) Success.copy(alpha = 0.18f) else Danger.copy(alpha = 0.18f))
            .border(1.dp, if (ok) Success.copy(alpha = 0.5f) else Danger.copy(alpha = 0.5f), RoundedCornerShape(50))
            .handCursor().clickable { app.navigate(Screen.Vpn) }.padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.Shield, null, tint = if (ok) Success else Danger, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text(if (ok) "VPN aktiv" else "Kein VPN", color = if (ok) Success else Danger, style = MaterialTheme.typography.labelLarge)
    }
}


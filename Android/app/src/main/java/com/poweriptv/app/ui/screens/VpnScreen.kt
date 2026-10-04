package com.poweriptv.app.ui.screens

import androidx.compose.foundation.shape.RoundedCornerShape
import com.poweriptv.app.ui.components.tvFocus
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.GppBad
import androidx.compose.material.icons.filled.GppGood
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.poweriptv.app.AppContainer
import com.poweriptv.app.ui.components.PowerTopBar
import com.poweriptv.app.parental.PinDialog
import com.poweriptv.app.ui.theme.Danger
import com.poweriptv.app.ui.theme.Success
import com.poweriptv.app.ui.theme.Warning
import com.poweriptv.app.vpn.VpnState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun VpnScreen(container: AppContainer, onBack: () -> Unit, onConnect: () -> Unit) {
    var unlocked by remember { mutableStateOf(!container.parental.settingsNeedPin()) }
    if (!unlocked) {
        PinDialog(container.parental, message = "Die Einstellungen sind mit einer PIN geschuetzt", onDismiss = onBack, onSuccess = { unlocked = true })
        return
    }
    val vpn = container.vpn
    val s = container.settings
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val state by vpn.state.collectAsState()
    val vpnError by vpn.error.collectAsState()
    val hasConfig by vpn.hasConfig.collectAsState()
    val required by s.vpnRequired.collectAsState()
    val auto by s.vpnAutoConnect.collectAsState()
    val appOnly by s.vpnAppOnly.collectAsState()
    val external by s.acceptExternalVpn.collectAsState()

    var systemVpn by remember { mutableStateOf(vpn.isSystemVpnActive()) }
    LaunchedEffect(Unit) {
        while (true) { systemVpn = vpn.isSystemVpnActive(); delay(2000) }
    }

    var showEditor by remember { mutableStateOf(false) }
    var editorText by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }

    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            val text = runCatching {
                context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
            }.getOrNull()
            if (text == null) message = "Datei konnte nicht gelesen werden"
            else vpn.saveConfig(text)
                .onSuccess { message = "Konfiguration importiert und verschluesselt gespeichert" }
                .onFailure { message = it.message }
        }
    }

    val protected = state == VpnState.CONNECTED || (external && systemVpn)

    Scaffold(
        topBar = { PowerTopBar("VPN & Sicherheit", onBack = onBack) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // Status
            SettingsSection("Status") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val color = when {
                        state == VpnState.CONNECTING -> Warning
                        protected -> Success
                        else -> Danger
                    }
                    Box(
                        Modifier.size(56.dp).clip(CircleShape).background(color.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(if (protected) Icons.Filled.GppGood else Icons.Filled.GppBad, null, tint = color, modifier = Modifier.size(32.dp))
                    }
                    Spacer(Modifier.width(14.dp))
                    Column {
                        Text(
                            when {
                                state == VpnState.CONNECTING -> "Verbinde..."
                                state == VpnState.CONNECTED -> "Geschuetzt (PowerIPTV VPN)"
                                external && systemVpn -> "Geschuetzt (externes VPN)"
                                state == VpnState.ERROR -> "Verbindungsfehler"
                                else -> "Nicht geschuetzt"
                            },
                            fontWeight = FontWeight.Bold,
                            color = color,
                        )
                        vpn.serverEndpoint()?.let {
                            Text("Server: $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                vpnError?.takeIf { state == VpnState.ERROR }?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                Spacer(Modifier.height(4.dp))
                if (state == VpnState.CONNECTED || state == VpnState.CONNECTING) {
                    Button(
                        onClick = { scope.launch { vpn.disconnect() } },
                        colors = ButtonDefaults.buttonColors(containerColor = Danger),
                        modifier = Modifier.fillMaxWidth(),
                    ) { Icon(Icons.Filled.PowerSettingsNew, null); Spacer(Modifier.width(8.dp)); Text("VPN trennen") }
                } else {
                    Button(onClick = onConnect, enabled = hasConfig, modifier = Modifier.tvFocus(RoundedCornerShape(50), 1.06f).fillMaxWidth()) {
                        Icon(Icons.Filled.PowerSettingsNew, null); Spacer(Modifier.width(8.dp)); Text("VPN verbinden")
                    }
                    if (!hasConfig) Text(
                        "Zuerst eine WireGuard-Konfiguration hinzufuegen.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // Konfiguration
            SettingsSection("WireGuard-Konfiguration") {
                Text(
                    "Lade bei deinem VPN-Anbieter (z.B. Mullvad, ProtonVPN, Surfshark, NordVPN, IVPN, AirVPN oder eigener Server) " +
                        "eine WireGuard-Konfigurationsdatei (.conf) herunter und importiere sie hier.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(modifier = Modifier.tvFocus(RoundedCornerShape(50), 1.06f), onClick = { importer.launch(arrayOf("*/*")) }) {
                        Icon(Icons.Filled.FileOpen, null); Spacer(Modifier.width(6.dp)); Text(".conf importieren")
                    }
                    OutlinedButton(modifier = Modifier.tvFocus(RoundedCornerShape(50), 1.06f), onClick = { editorText = vpn.configText().orEmpty(); showEditor = true }) {
                        Text(if (hasConfig) "Bearbeiten" else "Einfuegen")
                    }
                }
                if (hasConfig) {
                    TextButton(modifier = Modifier.tvFocus(RoundedCornerShape(50), 1.06f), onClick = { confirmDelete = true }) {
                        Icon(Icons.Filled.Delete, null, tint = MaterialTheme.colorScheme.error)
                        Spacer(Modifier.width(6.dp))
                        Text("Konfiguration loeschen", color = MaterialTheme.colorScheme.error)
                    }
                }
                message?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
            }

            // Optionen
            SettingsSection("Optionen") {
                SwitchRow(
                    "Kill-Switch (VPN erzwingen)",
                    "Blockiert Login, Playlisten, Streams und Bilder, solange kein VPN verbunden ist.",
                    required,
                ) { s.setVpnRequired(it) }
                SwitchRow(
                    "Beim App-Start automatisch verbinden",
                    null,
                    auto,
                    enabled = hasConfig,
                ) { s.setVpnAutoConnect(it) }
                SwitchRow(
                    "Nur PowerIPTV durch das VPN leiten",
                    "Split-Tunneling: andere Apps nutzen weiter deine normale Verbindung. Aenderung wirkt beim naechsten Verbinden.",
                    appOnly,
                ) { s.setVpnAppOnly(it) }
                SwitchRow(
                    "Externe VPN-Apps akzeptieren",
                    "Ein VPN einer anderen App zaehlt fuer den Kill-Switch ebenfalls als Schutz." +
                        if (systemVpn) " (Aktuell erkannt)" else "",
                    external,
                ) { s.setAcceptExternalVpn(it) }
            }
        }
    }

    if (showEditor) {
        AlertDialog(
            onDismissRequest = { showEditor = false },
            title = { Text("WireGuard-Konfiguration") },
            text = {
                OutlinedTextField(
                    value = editorText,
                    onValueChange = { editorText = it },
                    placeholder = { Text("[Interface]\nPrivateKey = ...\nAddress = ...\n\n[Peer]\nPublicKey = ...\nEndpoint = ...") },
                    textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    modifier = Modifier.fillMaxWidth().height(280.dp),
                )
            },
            confirmButton = {
                TextButton(modifier = Modifier.tvFocus(RoundedCornerShape(50), 1.06f), onClick = {
                    vpn.saveConfig(editorText)
                        .onSuccess { message = "Konfiguration gespeichert"; showEditor = false }
                        .onFailure { message = it.message; showEditor = false }
                }) { Text("Speichern") }
            },
            dismissButton = { TextButton(modifier = Modifier.tvFocus(RoundedCornerShape(50), 1.06f), onClick = { showEditor = false }) { Text("Abbrechen") } },
        )
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Konfiguration loeschen?") },
            text = { Text("Das VPN wird getrennt und die gespeicherte Konfiguration entfernt.") },
            confirmButton = {
                TextButton(modifier = Modifier.tvFocus(RoundedCornerShape(50), 1.06f), onClick = {
                    scope.launch { vpn.deleteConfig(); s.setVpnAutoConnect(false) }
                    confirmDelete = false
                    message = null
                }) { Text("Loeschen") }
            },
            dismissButton = { TextButton(modifier = Modifier.tvFocus(RoundedCornerShape(50), 1.06f), onClick = { confirmDelete = false }) { Text("Abbrechen") } },
        )
    }
}

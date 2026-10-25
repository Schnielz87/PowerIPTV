package com.poweriptv.desktop.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.poweriptv.app.data.Profile
import com.poweriptv.app.link.LINK_PORT
import com.poweriptv.app.link.LinkClient
import com.poweriptv.app.link.LinkCodes
import com.poweriptv.app.link.LinkPair
import com.poweriptv.app.link.SetupCode
import com.poweriptv.app.link.SetupPayload
import com.poweriptv.app.link.SetupResult
import com.poweriptv.app.ui.components.QrImage
import com.poweriptv.desktop.AppState
import com.poweriptv.desktop.ui.handCursor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.Toolkit
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.StringSelection

// Einrichtungs-Code (wie Android): Zugang (+ VPN) verschluesselt weitergeben – PIN, Geraetebindung, Ablaufdatum.

private val VALIDITY = listOf("24 Std." to 86_400_000L, "7 Tage" to 7 * 86_400_000L, "30 Tage" to 30 * 86_400_000L, "Unbegrenzt" to 0L)

private fun copy(text: String) = runCatching { Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(text), null) }

private fun clipboardSetupCode(): String? = runCatching {
    val t = Toolkit.getDefaultToolkit().systemClipboard.getData(DataFlavor.stringFlavor) as? String
    t?.let { Regex("PORTIVA-SETUP1:[A-Za-z0-9_\\-\\s]+").find(it)?.value?.trim() }
}.getOrNull()

@Composable
fun CreateSetupDialog(app: AppState, profile: Profile, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    val vpnConf = remember { app.vpn.configText() }
    var pin by remember { mutableStateOf("") }
    var device by remember { mutableStateOf("") }
    var validity by remember { mutableStateOf(VALIDITY[1].second) }
    var withVpn by remember { mutableStateOf(vpnConf != null) }
    var killSwitch by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var code by remember { mutableStateOf<String?>(null) }
    var copied by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Einrichtungs-Code: ${profile.name}", fontWeight = FontWeight.Bold) },
        text = {
            Column(Modifier.widthIn(min = 420.dp).heightIn(max = 620.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                val c = code
                if (c == null) {
                    Text("Ein verschlüsseltes Paket mit diesem Zugang (und auf Wunsch deinem VPN). Der Empfänger fügt es ein oder scannt es – fertig, ohne Abtippen.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedTextField(pin, { pin = it.filter(Char::isDigit).take(8) }, label = { Text("PIN (4–8 Ziffern, empfohlen)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(device, { device = it.uppercase().filter { ch -> ch.isLetterOrDigit() || ch == '-' }.take(9) },
                        label = { Text("Gerätenummer des Empfängers (optional)") }, placeholder = { Text("z.B. 7KQ4-M2XD") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Text("Mit Gerätenummer funktioniert der Code nur auf genau diesem Gerät. Der Empfänger sieht sie beim Einlösen bzw. im Fenster „Zugang empfangen“.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Gültig", fontWeight = FontWeight.SemiBold)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        VALIDITY.forEach { (label, ms) -> FilterChip(selected = validity == ms, onClick = { validity = ms }, label = { Text(label) }, modifier = Modifier.handCursor()) }
                    }
                    if (vpnConf != null) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(withVpn, { withVpn = it; if (!it) killSwitch = false }); Text("Mein VPN mitgeben (WireGuard)")
                        }
                        if (withVpn) {
                            Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(killSwitch, { killSwitch = it }); Text("Kill-Switch beim Empfänger einschalten") }
                            Text("Achtung: Wer den Code einlöst, nutzt deinen VPN-Zugang mit. Viele VPN-Anbieter erlauben pro Konfiguration nur ein Gerät.",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                        }
                    }
                    if (pin.isNotEmpty() && pin.length < 4) Text("Die PIN braucht mindestens 4 Ziffern.", color = MaterialTheme.colorScheme.error)
                    if (device.isNotEmpty() && SetupCode.normalizeId(device).length != 8) Text("Die Gerätenummer hat 8 Zeichen (z.B. 7KQ4-M2XD).", color = MaterialTheme.colorScheme.error)
                } else {
                    QrImage(c, Modifier.widthIn(max = 340.dp).fillMaxWidth())
                    SelectionContainer { Text(c, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall, maxLines = 4) }
                    Text(buildString {
                        append(if (pin.isNotEmpty()) "PIN: $pin – die PIN NICHT im selben Chat schicken (z.B. anrufen oder SMS)." else "Ohne PIN – jeder mit diesem Code kann den Zugang einrichten.")
                        if (device.isNotEmpty()) append("\nNur für Gerät ${device.uppercase()}.")
                        append(if (validity > 0) "\nGültig bis " + java.text.DateFormat.getDateTimeInstance().format(java.util.Date(System.currentTimeMillis() + validity)) + "." else "\nUnbegrenzt gültig.")
                    }, style = MaterialTheme.typography.bodySmall)
                    Button(onClick = {
                        copy("Dein Portiva-Einrichtungs-Code:\n\n$c\n\nSo geht's: Portiva öffnen → Benutzer wechseln → Neuer Zugang → „Einrichtungs-Code“ → Code einfügen. Die PIN bekommst du getrennt.")
                        copied = true
                    }, modifier = Modifier.handCursor()) { Text(if (copied) "✓ Kopiert – jetzt z.B. in WhatsApp einfügen" else "Code kopieren") }
                }
            }
        },
        confirmButton = {
            if (code == null) {
                val ok = (pin.isEmpty() || pin.length >= 4) && (device.isEmpty() || SetupCode.normalizeId(device).length == 8)
                TextButton(enabled = ok && !busy, modifier = Modifier.handCursor(), onClick = {
                    busy = true
                    scope.launch {
                        val payload = SetupPayload(
                            account = LinkCodes.toAccount(profile), vpn = if (withVpn) vpnConf else null, vpnRequired = withVpn && killSwitch,
                            exp = if (validity > 0) System.currentTimeMillis() + validity else 0, created = System.currentTimeMillis(), from = app.deviceName(),
                        )
                        code = withContext(Dispatchers.Default) { SetupCode.create(payload, pin, device) }
                        busy = false
                    }
                }) { if (busy) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp) else Text("Code erstellen") }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, modifier = Modifier.handCursor()) { Text(if (code == null) "Abbrechen" else "Fertig") } },
    )
}

@Composable
fun RedeemSetupDialog(app: AppState, onDismiss: () -> Unit, onImported: (Profile) -> Unit) {
    val scope = rememberCoroutineScope()
    var code by remember { mutableStateOf(clipboardSetupCode() ?: "") }
    var pin by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var done by remember { mutableStateOf<String?>(null) }
    var doneProfile by remember { mutableStateOf<Profile?>(null) }
    var toTv by remember { mutableStateOf(false) }
    var tvIp by remember { mutableStateOf("") }
    var tvCode by remember { mutableStateOf("") }
    var tvDevice by remember { mutableStateOf("") }
    val info = remember(code) { SetupCode.info(code) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Einrichtungs-Code", fontWeight = FontWeight.Bold) },
        text = {
            Column(Modifier.widthIn(min = 420.dp).heightIn(max = 620.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                val d = done
                if (d != null) { Text(d); return@Column }
                Text("Gerätenummer dieses PCs: ${app.deviceId}", fontWeight = FontWeight.SemiBold, fontFamily = FontFamily.Monospace)
                OutlinedTextField(code, { code = it.trim(); error = null }, label = { Text("Einrichtungs-Code (einfügen mit Strg+V)") }, maxLines = 3, modifier = Modifier.fillMaxWidth())
                if (code.isNotEmpty() && info == null) Text("Kein gültiger Einrichtungs-Code (vollständig kopiert?).", color = MaterialTheme.colorScheme.error)
                if (info?.needsPin == true) OutlinedTextField(pin, { pin = it.filter(Char::isDigit).take(8) }, label = { Text("PIN") }, singleLine = true,
                    visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
                val pinOk = info != null && (!info.needsPin || pin.length >= 4)
                if (info != null) {
                    Button(enabled = !busy && pinOk, modifier = Modifier.fillMaxWidth().handCursor(), onClick = {
                        busy = true; error = null
                        scope.launch {
                            when (val r = withContext(Dispatchers.Default) { SetupCode.open(code, pin, app.deviceId) }) {
                                is SetupResult.Error -> error = r.message
                                is SetupResult.Ok -> {
                                    val prof = app.importAccount(r.payload.account)
                                    val note = app.applySetupVpn(r.payload.vpn, r.payload.vpnRequired)
                                    doneProfile = prof
                                    done = "✓ Zugang „${prof.name}“ ist eingerichtet." + (note?.let { "\n$it" } ?: "")
                                }
                            }
                            busy = false
                        }
                    }) { Text("Auf diesem PC einrichten") }
                    OutlinedButton(onClick = { toTv = !toTv }, enabled = !busy, modifier = Modifier.fillMaxWidth().handCursor()) { Text("Auf Receiver / TV einrichten") }
                }
                if (toTv && info != null) {
                    Text("Am Receiver/TV „Zugang vom Handy empfangen“ öffnen und die dort angezeigte IP-Adresse und den Code eintragen.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedTextField(tvIp, { tvIp = it.filter { c -> c.isDigit() || c == '.' || c == ':' } }, label = { Text("IP-Adresse") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(tvCode, { tvCode = it.filter(Char::isDigit).take(6) }, label = { Text("Code (6 Ziffern)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    if (info.boundToDevice) OutlinedTextField(tvDevice, { tvDevice = it.uppercase().take(9) }, label = { Text("Gerätenummer (falls nicht automatisch)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedButton(enabled = !busy && pinOk && tvIp.isNotBlank() && tvCode.length == 6, modifier = Modifier.fillMaxWidth().handCursor(), onClick = {
                        busy = true; error = null
                        val host = tvIp.substringBefore(':').trim()
                        val port = tvIp.substringAfter(':', "").toIntOrNull() ?: LINK_PORT
                        scope.launch {
                            val dev = withContext(Dispatchers.IO) { LinkClient.hello(host, port)?.deviceId.orEmpty() }.ifBlank { tvDevice }
                            if (info.boundToDevice && dev.isBlank()) {
                                error = "Dieser Code ist an ein Gerät gebunden – bitte die Gerätenummer des Receivers/TV eintragen."
                            } else when (val r = withContext(Dispatchers.Default) { SetupCode.open(code, pin, dev) }) {
                                is SetupResult.Error -> error = r.message
                                is SetupResult.Ok -> {
                                    val err = withContext(Dispatchers.IO) {
                                        LinkClient.sendPair(host, port, LinkPair(tvCode, r.payload.account, app.deviceName(), r.payload.vpn, r.payload.vpnRequired))
                                    }
                                    if (err != null) error = err else done = "✓ „${r.payload.account.name}“ wurde auf das Gerät übertragen."
                                }
                            }
                            busy = false
                        }
                    }) { Text("Übertragen") }
                }
                if (busy) Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp); Spacer(Modifier.width(8.dp)); Text("Bitte warten …")
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            if (done != null) TextButton(onClick = { doneProfile?.let(onImported) ?: onDismiss() }, modifier = Modifier.handCursor()) { Text("Fertig") }
        },
        dismissButton = { if (done == null) TextButton(onClick = onDismiss, modifier = Modifier.handCursor()) { Text("Abbrechen") } },
    )
}

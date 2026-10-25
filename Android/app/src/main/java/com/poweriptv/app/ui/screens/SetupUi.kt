package com.poweriptv.app.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.poweriptv.app.AppContainer
import com.poweriptv.app.data.Profile
import com.poweriptv.app.link.LINK_PORT
import com.poweriptv.app.link.LinkClient
import com.poweriptv.app.link.LinkCodes
import com.poweriptv.app.link.LinkPair
import com.poweriptv.app.link.SetupCode
import com.poweriptv.app.link.SetupPayload
import com.poweriptv.app.link.SetupResult
import com.poweriptv.app.ui.components.QrImage
import com.poweriptv.app.ui.components.SecureWindow
import com.poweriptv.app.ui.components.tvFocus
import com.poweriptv.app.ui.theme.BrandCyan
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// Einrichtungs-Code: Zugang (+ VPN) verschluesselt weitergeben – mit PIN, Geraetebindung und Ablaufdatum.

private val VALIDITY = listOf("24 Std." to 86_400_000L, "7 Tage" to 7 * 86_400_000L, "30 Tage" to 30 * 86_400_000L, "Unbegrenzt" to 0L)

private fun shareText(code: String) =
    "Dein Portiva-Einrichtungs-Code:\n\n$code\n\n" +
        "So geht's: Portiva öffnen → Benutzer wechseln → Neuer Zugang → „Einrichtungs-Code“ → Code einfügen. " +
        "Die PIN bekommst du getrennt."

/** Einrichtungs-Code fuer einen Zugang erstellen (am Handy/Tablet/TV-Stick des Anbieters bzw. Besitzers). */
@Composable
fun CreateSetupDialog(container: AppContainer, profile: Profile, onDismiss: () -> Unit) {
    SecureWindow()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val vpnConf = remember { if (com.poweriptv.app.BuildConfigInfo.VPN_AVAILABLE) container.vpn.configText() else null }
    var pin by remember { mutableStateOf("") }
    var device by remember { mutableStateOf("") }
    var validity by remember { mutableStateOf(VALIDITY[1].second) }
    var withVpn by remember { mutableStateOf(vpnConf != null) }
    var killSwitch by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var code by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Einrichtungs-Code: ${profile.name}", fontWeight = FontWeight.Bold) },
        text = {
            Column(Modifier.heightIn(max = 560.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                val c = code
                if (c == null) {
                    Text(
                        "Ein verschlüsseltes Paket mit diesem Zugang (und auf Wunsch deinem VPN). Der Empfänger scannt oder fügt es ein – fertig, ohne Abtippen.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedTextField(
                        value = pin, onValueChange = { pin = it.filter(Char::isDigit).take(8) },
                        label = { Text("PIN (4–8 Ziffern, empfohlen)") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = device, onValueChange = { device = it.uppercase().filter { ch -> ch.isLetterOrDigit() || ch == '-' }.take(9) },
                        label = { Text("Gerätenummer des Empfängers (optional)") }, placeholder = { Text("z.B. 7KQ4-M2XD") }, singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        "Mit Gerätenummer funktioniert der Code nur auf genau diesem Gerät. Der Empfänger findet sie unter Benutzer wechseln → Neuer Zugang → „Einrichtungs-Code“ (TV/Receiver: im Fenster „Zugang empfangen“).",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text("Gültig", fontWeight = FontWeight.SemiBold)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        VALIDITY.forEach { (label, ms) ->
                            FilterChip(selected = validity == ms, onClick = { validity = ms }, label = { Text(label) }, modifier = Modifier.tvFocus(RoundedCornerShape(8.dp)))
                        }
                    }
                    if (vpnConf != null) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = withVpn, onCheckedChange = { withVpn = it; if (!it) killSwitch = false }, modifier = Modifier.tvFocus())
                            Text("Mein VPN mitgeben (WireGuard)")
                        }
                        if (withVpn) Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = killSwitch, onCheckedChange = { killSwitch = it }, modifier = Modifier.tvFocus())
                            Text("Kill-Switch beim Empfänger einschalten")
                        }
                        if (withVpn) Text(
                            "Achtung: Wer den Code einlöst, nutzt deinen VPN-Zugang mit. Viele VPN-Anbieter erlauben pro Konfiguration nur ein Gerät.",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error,
                        )
                    }
                    if (pin.isNotEmpty() && pin.length < 4) Text("Die PIN braucht mindestens 4 Ziffern.", color = MaterialTheme.colorScheme.error)
                    if (device.isNotEmpty() && SetupCode.normalizeId(device).length != 8) Text("Die Gerätenummer hat 8 Zeichen (z.B. 7KQ4-M2XD).", color = MaterialTheme.colorScheme.error)
                } else {
                    QrImage(c, Modifier.widthIn(max = 320.dp).fillMaxWidth())
                    Text(
                        buildString {
                            append(if (pin.isNotEmpty()) "PIN: $pin – die PIN NICHT im selben Chat schicken (z.B. anrufen oder SMS)." else "Ohne PIN – jeder mit diesem Code kann den Zugang einrichten.")
                            if (device.isNotEmpty()) append("\nNur für Gerät ${device.uppercase()}.")
                            append(if (validity > 0) "\nGültig bis " + java.text.DateFormat.getDateTimeInstance().format(java.util.Date(System.currentTimeMillis() + validity)) + "." else "\nUnbegrenzt gültig.")
                        },
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = {
                            val i = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, shareText(c))
                            context.startActivity(Intent.createChooser(i, "Einrichtungs-Code senden"))
                        }, modifier = Modifier.weight(1f).tvFocus(RoundedCornerShape(50))) {
                            Icon(Icons.Filled.Share, null); Spacer(Modifier.width(6.dp)); Text("Teilen")
                        }
                        OutlinedButton(onClick = {
                            (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("Portiva", c))
                            android.widget.Toast.makeText(context, "Code kopiert", android.widget.Toast.LENGTH_SHORT).show()
                        }, modifier = Modifier.weight(1f).tvFocus(RoundedCornerShape(50))) {
                            Icon(Icons.Filled.ContentCopy, null); Spacer(Modifier.width(6.dp)); Text("Kopieren")
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (code == null) {
                val ok = (pin.isEmpty() || pin.length >= 4) && (device.isEmpty() || SetupCode.normalizeId(device).length == 8)
                TextButton(enabled = ok && !busy, onClick = {
                    busy = true
                    scope.launch {
                        val payload = SetupPayload(
                            account = LinkCodes.toAccount(profile),
                            vpn = if (withVpn) vpnConf else null,
                            vpnRequired = withVpn && killSwitch,
                            exp = if (validity > 0) System.currentTimeMillis() + validity else 0,
                            created = System.currentTimeMillis(),
                            from = container.deviceName(),
                        )
                        code = withContext(Dispatchers.Default) { SetupCode.create(payload, pin, device) }
                        busy = false
                    }
                }, modifier = Modifier.tvFocus(RoundedCornerShape(50))) {
                    if (busy) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp) else Text("Code erstellen")
                }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, modifier = Modifier.tvFocus(RoundedCornerShape(50))) { Text(if (code == null) "Abbrechen" else "Fertig") } },
    )
}

/**
 * Einrichtungs-Code einloesen: hier einrichten oder (fuer Receiver/Samsung-TV/TV-Stick) ans Geraet weitergeben.
 * code = gescannter Text; null -> Eingabefeld (Einfuegen aus der Zwischenablage).
 */
@Composable
fun RedeemSetupDialog(container: AppContainer, initialCode: String?, onImported: (Profile) -> Unit, onDismiss: () -> Unit) {
    SecureWindow()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var code by remember {
        mutableStateOf(initialCode ?: runCatching {
            (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).primaryClip?.getItemAt(0)?.text?.toString()
                ?.let { t -> Regex("PORTIVA-SETUP1:[A-Za-z0-9_\\-\\s]+").find(t)?.value?.trim() }
        }.getOrNull() ?: "")
    }
    var pin by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var done by remember { mutableStateOf<String?>(null) }
    var doneProfile by remember { mutableStateOf<Profile?>(null) }
    var toTv by remember { mutableStateOf(false) }
    var tvIp by remember { mutableStateOf("") }
    var tvCode by remember { mutableStateOf("") }
    var tvDevice by remember { mutableStateOf("") }
    DisposableEffect(Unit) { onDispose { container.link.onOfferTaken = null } }
    val info = remember(code) { SetupCode.info(code) }

    fun redeemHere() {
        busy = true; error = null
        scope.launch {
            when (val r = withContext(Dispatchers.Default) { SetupCode.open(code, pin, container.deviceId) }) {
                is SetupResult.Error -> error = r.message
                is SetupResult.Ok -> {
                    val prof = container.importAccount(r.payload.account)
                    val vpnNote = container.applySetupVpn(r.payload.vpn, r.payload.vpnRequired)
                    doneProfile = prof
                    done = "✓ Zugang „${prof.name}“ ist eingerichtet und aktiv." + (vpnNote?.let { "\n$it" } ?: "")
                }
            }
            busy = false
        }
    }

    fun redeemTo(target: LinkCodes.PairTarget) {
        busy = true; error = null
        scope.launch {
            // Geraetenummer des Ziels: aus dem QR, sonst beim Geraet nachfragen, sonst Eingabe
            val dev = target.deviceId.ifBlank {
                if (target.port > 0) withContext(Dispatchers.IO) { LinkClient.hello(target.host, target.port)?.deviceId.orEmpty() } else ""
            }.ifBlank { tvDevice }
            if (info?.boundToDevice == true && dev.isBlank()) {
                error = "Dieser Code ist an ein Gerät gebunden – bitte die Gerätenummer des Receivers/TV eintragen (steht im Fenster „Zugang empfangen“)."
                busy = false; return@launch
            }
            when (val r = withContext(Dispatchers.Default) { SetupCode.open(code, pin, dev) }) {
                is SetupResult.Error -> { error = r.message; busy = false }
                is SetupResult.Ok -> {
                    val a = r.payload.account
                    if (target.port > 0) {
                        val err = withContext(Dispatchers.IO) {
                            LinkClient.sendPair(target.host, target.port, LinkPair(target.code, a, container.deviceName(), r.payload.vpn, r.payload.vpnRequired))
                        }
                        busy = false
                        if (err != null) error = err else done = "✓ „${a.name}“ wurde auf das Gerät übertragen."
                    } else {
                        container.link.onOfferTaken = { taken -> done = "✓ „${taken.name}“ wurde auf den Fernseher übertragen"; busy = false }
                        container.link.offer(target.code, a)
                        error = null
                        done = null
                        // busy bleibt an, bis der Fernseher abholt
                    }
                }
            }
        }
    }

    val scanSetup = rememberQrScanner { text -> if (text != null) { if (SetupCode.isSetupCode(text)) code = text.trim() else error = "Das ist kein Einrichtungs-Code." } }
    val scanTv = rememberQrScanner { text ->
        if (text == null) return@rememberQrScanner
        val t = LinkCodes.parsePairQr(text)
        if (t == null) error = "Das ist kein Empfangs-Code. Am Receiver/TV: „Zugang vom Handy empfangen“ öffnen." else redeemTo(t)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Einrichtungs-Code", fontWeight = FontWeight.Bold) },
        text = {
            Column(Modifier.heightIn(max = 560.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                val d = done
                if (d != null) {
                    Text(d, color = BrandCyan)
                    return@Column
                }
                Text("Gerätenummer dieses Geräts: ${container.deviceId}", fontWeight = FontWeight.SemiBold, fontFamily = FontFamily.Monospace)
                OutlinedTextField(
                    value = code, onValueChange = { code = it.trim(); error = null },
                    label = { Text("Einrichtungs-Code (einfügen)") }, maxLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (hasCamera()) OutlinedButton(onClick = scanSetup, modifier = Modifier.fillMaxWidth().tvFocus(RoundedCornerShape(50))) {
                    Icon(Icons.Filled.QrCodeScanner, null); Spacer(Modifier.width(8.dp)); Text("Code scannen")
                }
                if (code.isNotEmpty() && info == null) Text("Kein gültiger Einrichtungs-Code (vollständig kopiert?).", color = MaterialTheme.colorScheme.error)
                if (info?.needsPin == true) OutlinedTextField(
                    value = pin, onValueChange = { pin = it.filter(Char::isDigit).take(8) },
                    label = { Text("PIN") }, singleLine = true, visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (info != null) {
                    Button(onClick = ::redeemHere, enabled = !busy && (!info.needsPin || pin.length >= 4), modifier = Modifier.fillMaxWidth().tvFocus(RoundedCornerShape(50))) {
                        Text("Auf diesem Gerät einrichten")
                    }
                    OutlinedButton(onClick = { toTv = !toTv }, enabled = !busy, modifier = Modifier.fillMaxWidth().tvFocus(RoundedCornerShape(50))) {
                        Icon(Icons.Filled.Tv, null); Spacer(Modifier.width(8.dp)); Text("Auf Receiver / TV einrichten")
                    }
                }
                if (toTv && info != null) {
                    Text(
                        "Am Receiver/TV „Zugang vom Handy empfangen“ öffnen (Enigma2: Benutzer wechseln → ganz oben; Samsung-TV: Benutzer wechseln → „Vom Handy empfangen“; TV-Stick: Neuer Zugang → „Vom anderen Gerät empfangen“).",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (hasCamera()) Button(onClick = scanTv, enabled = !busy && (!info.needsPin || pin.length >= 4), modifier = Modifier.fillMaxWidth().tvFocus(RoundedCornerShape(50))) {
                        Icon(Icons.Filled.QrCodeScanner, null); Spacer(Modifier.width(8.dp)); Text("QR am Receiver/TV scannen")
                    }
                    OutlinedTextField(value = tvIp, onValueChange = { tvIp = it.filter { c -> c.isDigit() || c == '.' || c == ':' } },
                        label = { Text("oder IP-Adresse") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri))
                    OutlinedTextField(value = tvCode, onValueChange = { tvCode = it.filter(Char::isDigit).take(6) },
                        label = { Text("Code (6 Ziffern)") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                    if (info.boundToDevice) OutlinedTextField(value = tvDevice, onValueChange = { tvDevice = it.uppercase().take(9) },
                        label = { Text("Gerätenummer (falls nicht automatisch)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedButton(
                        enabled = !busy && tvIp.isNotBlank() && tvCode.length == 6 && (!info.needsPin || pin.length >= 4),
                        onClick = {
                            val host = tvIp.substringBefore(':').trim()
                            val port = tvIp.substringAfter(':', "").toIntOrNull() ?: LINK_PORT
                            redeemTo(LinkCodes.PairTarget(host, port, tvCode, ""))
                        },
                        modifier = Modifier.fillMaxWidth().tvFocus(RoundedCornerShape(50)),
                    ) { Text("Übertragen") }
                }
                if (busy) Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp); Spacer(Modifier.width(8.dp)); Text("Bitte warten …")
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            if (done != null) TextButton(onClick = { doneProfile?.let(onImported) ?: onDismiss() }, modifier = Modifier.tvFocus(RoundedCornerShape(50))) { Text("Fertig") }
        },
        dismissButton = { if (done == null) TextButton(onClick = onDismiss, modifier = Modifier.tvFocus(RoundedCornerShape(50))) { Text("Abbrechen") } },
    )
}

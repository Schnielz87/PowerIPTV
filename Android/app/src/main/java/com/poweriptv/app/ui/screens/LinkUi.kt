package com.poweriptv.app.ui.screens

import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import com.poweriptv.app.AppContainer
import com.poweriptv.app.data.Profile
import com.poweriptv.app.link.LinkAccount
import com.poweriptv.app.link.LinkClient
import com.poweriptv.app.link.LinkCodes
import com.poweriptv.app.link.LinkPair
import com.poweriptv.app.ui.components.QrDialog
import com.poweriptv.app.ui.components.tvFocus
import com.poweriptv.app.ui.theme.BrandCyan
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// Portiva Link: Zugang per QR-Code auf ein anderes Geraet uebertragen (immer nur EIN Zugang).

@Composable
private fun hasCamera(): Boolean {
    val context = LocalContext.current
    return remember { context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY) }
}

/** QR-Scanner mit der Kamera (liefert den Text oder null bei Abbruch). */
@Composable
private fun rememberQrScanner(onResult: (String?) -> Unit): () -> Unit {
    val launcher = rememberLauncherForActivityResult(ScanContract()) { r -> onResult(r.contents) }
    return {
        launcher.launch(
            ScanOptions()
                .setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                .setPrompt("Portiva-QR-Code ins Bild halten")
                .setBeepEnabled(false)
                .setOrientationLocked(false),
        )
    }
}

/** Zugang an ein Geraet schicken, dessen Empfangs-Code gescannt wurde. Liefert null bei Erfolg. */
private suspend fun sendAccount(container: AppContainer, target: LinkCodes.PairTarget, account: LinkAccount): String? = withContext(Dispatchers.IO) {
    if (target.port > 0) {
        LinkClient.sendPair(target.host, target.port, LinkPair(target.code, account, container.deviceName()))
    } else {
        // Samsung-TV: darf selbst nichts empfangen -> holt den Zugang hier ab
        container.link.offer(target.code, account)
        null
    }
}

/**
 * QR-Code eines Zugangs (Symbol neben Stift und Muelleimer).
 * Handy/Tablet scannt ihn direkt; TV-Stick/Fernseher (ohne Kamera): "An TV-Geraet senden" und dort den Code scannen.
 */
@Composable
fun AccountQrDialog(container: AppContainer, profile: Profile, onDismiss: () -> Unit) {
    val account = remember(profile) { LinkCodes.toAccount(profile) }
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    DisposableEffect(Unit) { onDispose { container.link.onOfferTaken = null } }
    val scan = rememberQrScanner { text ->
        if (text == null) return@rememberQrScanner
        val target = LinkCodes.parsePairQr(text)
        if (target == null) { status = "Das ist kein Empfangs-Code. Am TV: Benutzer wechseln → Neuer Zugang → „Vom anderen Gerät empfangen“."; return@rememberQrScanner }
        busy = true
        status = "Wird übertragen …"
        scope.launch {
            if (target.port == 0) {
                container.link.onOfferTaken = { a -> status = "✓ „${a.name}“ wurde auf den Fernseher übertragen"; busy = false }
            }
            val err = sendAccount(container, target, account)
            if (target.port > 0) {
                busy = false
                status = err ?: "✓ „${profile.name}“ wurde übertragen"
            } else if (err == null) {
                status = "Der Fernseher holt den Zugang jetzt ab … Portiva hier geöffnet lassen."
            }
        }
    }
    QrDialog(
        title = profile.name,
        text = LinkCodes.accountQr(account),
        hint = "Auf dem anderen Handy/Tablet: Benutzer wechseln → Neuer Zugang → „QR-Code scannen“. " +
            "Nur dir selbst zeigen – der Code enthält die Zugangsdaten.",
        onDismiss = onDismiss,
        closeModifier = Modifier.tvFocus(CircleShape, 1.15f),
    ) {
        if (hasCamera()) {
            Button(onClick = scan, enabled = !busy, modifier = Modifier.fillMaxWidth().tvFocus(RoundedCornerShape(50))) {
                Icon(Icons.Filled.Tv, null)
                Spacer(Modifier.width(8.dp))
                Text("An TV-Stick / Fernseher senden")
            }
            Text(
                "Am TV: Benutzer wechseln → Neuer Zugang → „Vom anderen Gerät empfangen“, dann den dort gezeigten Code hier scannen.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        status?.let {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (busy) { CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp); Spacer(Modifier.width(8.dp)) }
                Text(it, color = if (it.startsWith("✓")) BrandCyan else MaterialTheme.colorScheme.onSurface)
            }
        }
    }
}

/** "Neuer Zugang": manuell, QR-Code scannen (Kamera) oder von einem anderen Geraet empfangen. */
@Composable
fun AddAccountChooser(
    container: AppContainer,
    onManual: () -> Unit,
    onImported: (Profile) -> Unit,
    onDismiss: () -> Unit,
) {
    var receive by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scan = rememberQrScanner { text ->
        if (text == null) return@rememberQrScanner
        val a = LinkCodes.parseAccountQr(text)
        when {
            a != null -> onImported(container.importAccount(a))
            LinkCodes.parsePairQr(text) != null -> error = "Das ist ein Empfangs-Code. Zum Senden am Handy beim gewünschten Zugang auf das QR-Symbol tippen → „An TV-Stick / Fernseher senden“."
            else -> error = "Kein Portiva-QR-Code erkannt."
        }
    }
    if (receive) {
        ReceiveAccountDialog(container, onReceived = onImported, onDismiss = { receive = false })
        return
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Neuer Zugang") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = { onDismiss(); onManual() }, modifier = Modifier.fillMaxWidth().tvFocus(RoundedCornerShape(50))) {
                    Icon(Icons.Filled.Edit, null); Spacer(Modifier.width(8.dp)); Text("Manuell eingeben")
                }
                if (hasCamera()) {
                    OutlinedButton(onClick = scan, modifier = Modifier.fillMaxWidth().tvFocus(RoundedCornerShape(50))) {
                        Icon(Icons.Filled.QrCodeScanner, null); Spacer(Modifier.width(8.dp)); Text("QR-Code scannen")
                    }
                }
                OutlinedButton(onClick = { receive = true }, modifier = Modifier.fillMaxWidth().tvFocus(RoundedCornerShape(50))) {
                    Icon(Icons.Filled.Tv, null); Spacer(Modifier.width(8.dp)); Text("Vom anderen Gerät empfangen")
                }
                Text(
                    "Übertragen wird immer nur der eine Zugang, den du am anderen Gerät auswählst.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss, modifier = Modifier.tvFocus(RoundedCornerShape(50))) { Text("Abbrechen") } },
    )
}

/** Dieses Geraet zeigt einen Empfangs-Code; das Handy scannt ihn und schickt den gewaehlten Zugang. */
@Composable
fun ReceiveAccountDialog(container: AppContainer, onReceived: (Profile) -> Unit, onDismiss: () -> Unit) {
    val code = remember { LinkCodes.newCode() }
    val ip = remember { LinkCodes.localIpv4() }
    val received by container.linkReceived.collectAsState()
    DisposableEffect(code) {
        container.linkReceived.value = null
        container.linkPairCode.value = code
        onDispose { if (container.linkPairCode.value == code) container.linkPairCode.value = null }
    }
    LaunchedEffect(received) {
        received?.let { container.linkReceived.value = null; onReceived(it) }
    }
    if (ip == null || container.link.port == 0) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Kein Heimnetz") },
            text = { Text("Dieses Gerät ist nicht mit einem WLAN/LAN verbunden. Beide Geräte müssen im selben Heimnetz sein.") },
            confirmButton = { TextButton(onClick = onDismiss, modifier = Modifier.tvFocus(RoundedCornerShape(50))) { Text("OK") } },
        )
        return
    }
    QrDialog(
        title = "Zugang empfangen",
        text = LinkCodes.pairQr(ip, container.link.port, code),
        hint = "Am Handy in Portiva: Benutzer wechseln → beim gewünschten Zugang auf das QR-Symbol tippen → " +
            "„An TV-Stick / Fernseher senden“ → diesen Code scannen. Beide Geräte im selben WLAN.",
        onDismiss = onDismiss,
        closeModifier = Modifier.tvFocus(CircleShape, 1.15f),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
            Spacer(Modifier.width(10.dp))
            Text("Warte auf Zugang …  Code $code", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
        }
    }
}

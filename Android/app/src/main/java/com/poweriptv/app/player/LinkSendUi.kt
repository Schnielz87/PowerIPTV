package com.poweriptv.app.player

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.poweriptv.app.AppContainer
import com.poweriptv.app.PlayEntry
import com.poweriptv.app.link.LinkClient
import com.poweriptv.app.link.LinkDevice
import com.poweriptv.app.link.LinkPlay
import com.poweriptv.app.ui.components.tvFocus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Kann dieser Titel an ein anderes Geraet gegeben werden? (Downloads/Aufnahmen liegen nur hier) */
fun canSendToDevice(entry: PlayEntry?) = entry != null && entry.url.startsWith("http", ignoreCase = true)

/**
 * "An Gerät senden": andere Portiva-Geraete im Heimnetz suchen und die Wiedergabe dort an derselben Stelle fortsetzen.
 * onSent: hier stoppen (Name des Zielgeraets).
 */
@Composable
fun SendToDeviceDialog(
    container: AppContainer,
    entry: PlayEntry,
    position: () -> Long,
    duration: () -> Long,
    onSent: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var devices by remember { mutableStateOf<List<LinkDevice>?>(null) }
    var status by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var round by remember { mutableIntStateOf(0) }
    var manualIp by remember { mutableStateOf("") }
    LaunchedEffect(round) {
        devices = null
        devices = withContext(Dispatchers.IO) { LinkClient.discover(container.link.port, extraHosts = container.settings.linkHosts()) }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("An Gerät senden") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                val list = devices
                when {
                    list == null -> Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(10.dp))
                        Text("Suche Portiva-Geräte im Heimnetz …")
                    }
                    list.isEmpty() -> Text("Kein Gerät gefunden. Portiva muss auf dem anderen Gerät laufen (TV-Stick, Tablet, Windows-PC, Enigma2-Receiver) und im selben Heimnetz hängen – egal ob per WLAN oder LAN-Kabel am Router. Wird es trotzdem nicht gefunden: unten die IP-Adresse eingeben (steht beim Receiver unten auf der Portiva-Startseite).")
                    else -> list.forEach { d ->
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).tvFocus(RoundedCornerShape(12.dp), 1.03f)
                                .clickable(enabled = !busy) {
                                    busy = true
                                    status = "Sende an ${d.name} …"
                                    scope.launch {
                                        val dur = duration()
                                        val play = LinkPlay(entry.title, entry.url, entry.live, if (entry.live) 0 else position(), if (dur > 0) dur else 0, entry.item?.logo, container.deviceName())
                                        val err = withContext(Dispatchers.IO) { LinkClient.sendPlay(d, play) }
                                        busy = false
                                        if (err == null) onSent(d.name) else status = err
                                    }
                                }
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(when { d.platform.contains("tv") || d.platform == "enigma2" -> Icons.Filled.Tv; d.platform == "windows" -> Icons.Filled.Computer; else -> Icons.Filled.PhoneAndroid }, null)
                            Spacer(Modifier.width(12.dp))
                            Text(d.name)
                        }
                    }
                }
                // Geraet per IP-Adresse (falls die automatische Suche es nicht findet, z.B. anderer Netzbereich)
                if (list != null) Row(verticalAlignment = Alignment.CenterVertically) {
                    com.poweriptv.app.ui.components.TvOutlinedTextField(
                        value = manualIp,
                        onValueChange = { manualIp = it.filter { c -> c.isDigit() || c == '.' || c == ':' } },
                        placeholder = { Text("IP, z.B. 192.168.178.40") },
                        singleLine = true,
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Uri),
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(enabled = manualIp.isNotBlank() && !busy, onClick = {
                        busy = true
                        status = "Verbinde mit $manualIp …"
                        scope.launch {
                            val d = withContext(Dispatchers.IO) { LinkClient.probe(manualIp) }
                            busy = false
                            if (d == null) status = "Unter $manualIp antwortet kein Portiva-Gerät. Ist Portiva dort installiert und das Gerät eingeschaltet?"
                            else {
                                container.settings.addLinkHost(manualIp.trim())
                                devices = (devices.orEmpty().filter { it.host != d.host } + d)
                                status = "„${d.name}“ gefunden – antippen zum Senden"
                                manualIp = ""
                            }
                        }
                    }) { Text("Verbinden") }
                }
                status?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
                Text(
                    "Der Film läuft dort an derselben Stelle weiter, hier wird gestoppt. Samsung-Fernseher können nur senden, nicht empfangen.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { round++ }, enabled = devices != null && !busy, modifier = Modifier.tvFocus(RoundedCornerShape(50))) { Text("Neu suchen") }
        },
        dismissButton = { TextButton(onClick = onDismiss, modifier = Modifier.tvFocus(RoundedCornerShape(50))) { Text("Schließen") } },
    )
}

package com.poweriptv.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.poweriptv.app.util.DiagLog

/**
 * Einstellungen → Fehlerprotokoll: anzeigen (zum Abfotografieren), als QR-Code (mit dem Handy scannen und
 * den Text weiterschicken) oder loeschen. Am Fernseher komplett mit der Fernbedienung bedienbar.
 */
@Composable
fun ColumnScope.DiagLogSection() {
    var show by remember { mutableStateOf(false) }
    var qr by remember { mutableStateOf(false) }
    var info by remember { mutableStateOf<String?>(null) }
    val btn = Modifier.tvFocus(RoundedCornerShape(50), 1.06f)
    Text(
        "Wenn etwas nicht klappt (z.B. ein Film lädt lange oder bleibt hängen): direkt danach hier „Als QR-Code“ wählen, " +
            "mit der Handy-Kamera scannen und den Text an den Support schicken. Enthält Player-Ereignisse, Fehler und Abstürze – " +
            "Benutzername und Passwort werden nie mitgeschrieben.",
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Row {
        TextButton(modifier = btn, onClick = { qr = true }) { Text("Als QR-Code") }
        TextButton(modifier = btn, onClick = { show = true }) { Text("Anzeigen") }
        TextButton(modifier = btn, onClick = { DiagLog.clear(); info = "Protokoll gelöscht" }) { Text("Löschen") }
    }
    info?.let { Text(it, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall) }

    if (show) AlertDialog(
        onDismissRequest = { show = false },
        title = { Text("Fehlerprotokoll") },
        text = {
            // Die letzten 45 Ereignisse – passt auf einen Fernseher und laesst sich abfotografieren
            Text(
                DiagLog.text(45), fontFamily = FontFamily.Monospace, fontSize = 11.sp, lineHeight = 14.sp,
                modifier = Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState()),
            )
        },
        confirmButton = { TextButton(modifier = btn, onClick = { show = false }) { Text("Schließen") } },
    )

    if (qr) {
        val pages = remember { DiagLog.pages() }
        var page by remember { mutableIntStateOf(0) }
        AlertDialog(
            onDismissRequest = { qr = false },
            title = { Text("Fehlerprotokoll · Seite ${page + 1}/${pages.size}") },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    QrImage(pages[page], Modifier.widthIn(max = 380.dp).fillMaxWidth())
                    Text(
                        "Mit der Handy-Kamera scannen, Text kopieren und schicken. Seite 1 = neueste Ereignisse" +
                            if (pages.size > 1) " – bei Bedarf auch die weiteren Seiten." else ".",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            confirmButton = {
                Row {
                    if (page > 0) TextButton(modifier = btn, onClick = { page-- }) { Text("◀ Neuer") }
                    if (page < pages.size - 1) TextButton(modifier = btn, onClick = { page++ }) { Text("Älter ▶") }
                    TextButton(modifier = btn, onClick = { qr = false }) { Text("Schließen") }
                }
            },
        )
    }
}

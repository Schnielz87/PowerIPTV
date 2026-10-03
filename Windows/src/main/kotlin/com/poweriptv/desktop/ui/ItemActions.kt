package com.poweriptv.desktop.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.unit.dp
import com.poweriptv.app.data.ContentItem
import com.poweriptv.app.data.ContentType
import com.poweriptv.desktop.AppState
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.net.URLEncoder

/**
 * Standard-Menue fuer einen Titel (Rechtsklick) – wie das Lang-Druck-Menue der Android-App:
 * Favorit, Zu Liste hinzufuegen, Teilen (WhatsApp – nur der Titel, nie der Stream-Link mit Zugangsdaten).
 */
fun AppState.itemMenu(item: ContentItem, extra: List<MenuAction> = emptyList()): List<MenuAction> {
    val lib = library ?: return extra
    val fav = lib.isFavorite(item)
    val out = mutableListOf<MenuAction>()
    out += extra
    out += MenuAction(if (fav) "Aus Favoriten entfernen" else "Zu Favoriten") { lib.toggleFavorite(item) }
    out += MenuAction("Zu Liste hinzufügen …") { listPickerFor = item }
    if (item.type != ContentType.LIVE) {
        val w = item.key in lib.watched.value
        out += MenuAction(if (w) "Als nicht gesehen markieren" else "Als gesehen markieren") { lib.markWatched(item.key, !w) }
    }
    out += MenuAction("Per WhatsApp teilen") { shareWhatsApp(item) }
    out += MenuAction("Titel kopieren") { copyText(shareText(item)) }
    return out
}

private fun shareText(item: ContentItem): String {
    val kind = when (item.type) { ContentType.LIVE -> "Live-Sender"; ContentType.MOVIE -> "Film"; ContentType.SERIES -> "Serie" }
    return "Schau mal: ${item.name}" + (item.year?.let { " ($it)" } ?: "") + " – $kind in Portiva PowerIPTV"
}

fun shareWhatsApp(item: ContentItem) = openUrl("https://wa.me/?text=" + URLEncoder.encode(shareText(item), "UTF-8").replace("+", "%20"))

fun copyText(text: String) {
    runCatching { Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(text), null) }
}

/** Dialog: Titel in eigene Listen legen / neue Liste anlegen (wie Android). */
@Composable
fun ListPickerDialog(app: AppState, item: ContentItem, onDismiss: () -> Unit) {
    val lib = app.library ?: return
    val lists by lib.lists.collectAsState()
    val favs by lib.favorites.collectAsState()
    var newName by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Zu Liste hinzufügen") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(item.name)
                Row(Modifier.fillMaxWidth().clickable { lib.toggleFavorite(item) }.padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(favs.any { it.key == item.key }, { lib.toggleFavorite(item) }); Text("Favoriten")
                }
                lists.forEach { l ->
                    Row(Modifier.fillMaxWidth().clickable { lib.toggleInList(l.id, item) }.padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(l.items.any { it.key == item.key }, { lib.toggleInList(l.id, item) }); Text(l.name)
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(newName, { newName = it }, placeholder = { Text("Neue Liste") }, singleLine = true, modifier = Modifier.weight(1f))
                    Spacer(Modifier.width(8.dp))
                    TextButton(enabled = newName.isNotBlank(), onClick = { val id = lib.createList(newName); lib.toggleInList(id, item); newName = "" }) { Text("Anlegen") }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Fertig") } },
    )
}

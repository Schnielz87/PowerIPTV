package com.poweriptv.desktop.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.ContextMenuArea
import androidx.compose.foundation.ContextMenuItem
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import com.poweriptv.desktop.ui.handCursor
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.poweriptv.app.data.ContentType
import com.poweriptv.desktop.AppState
import com.poweriptv.desktop.Screen
import com.poweriptv.desktop.ui.ChannelCard
import com.poweriptv.desktop.ui.MenuAction
import com.poweriptv.desktop.ui.PosterCard
import com.poweriptv.desktop.ui.itemMenu
import com.poweriptv.desktop.ui.typeLabel

@Composable
fun FavoritesScreen(app: AppState) {
    val lib = app.library ?: return
    val favoritesAll by lib.favorites.collectAsState()
    val lists by lib.lists.collectAsState()
    val pOn by app.parental.enabled.collectAsState()
    val pUnl by app.parental.sessionUnlocked.collectAsState()
    val watched by lib.watched.collectAsState()
    // Reiter: Favoriten + eigene Listen (wie Android „Favoriten & Listen“)
    var tab by remember { mutableStateOf("default") }
    var renameFor by remember { mutableStateOf<com.poweriptv.desktop.data.FavoriteList?>(null) }
    var deleteFor by remember { mutableStateOf<com.poweriptv.desktop.data.FavoriteList?>(null) }
    var newList by remember { mutableStateOf(false) }
    val current = lists.firstOrNull { it.id == tab }
    val itemsAll = if (tab == "default" || current == null) favoritesAll else current.items
    val favorites = remember(itemsAll, pOn, pUnl) { app.parental.visible(app.profile?.id, itemsAll) }
    Column(Modifier.fillMaxSize().padding(28.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Favoriten & Listen", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
            TextButton(onClick = { newList = true }, modifier = Modifier.handCursor()) { Text("+ Neue Liste") }
        }
        Spacer(Modifier.height(10.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            item { FilterChip(selected = tab == "default", onClick = { tab = "default" }, label = { Text("★ Favoriten (${favoritesAll.size})") }, modifier = Modifier.handCursor()) }
            items(lists, key = { it.id }) { l ->
                ContextMenuArea(items = {
                    listOf(ContextMenuItem("Umbenennen") { renameFor = l }, ContextMenuItem("Liste löschen") { deleteFor = l })
                }) {
                    FilterChip(selected = tab == l.id, onClick = { tab = l.id }, label = { Text("${l.name} (${l.items.size})") }, modifier = Modifier.handCursor())
                }
            }
        }
        if (current != null) Text("Rechtsklick auf den Listennamen: umbenennen oder löschen.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
        Spacer(Modifier.height(12.dp))
        if (favorites.isEmpty()) {
            EmptyHint(if (current == null) "Noch keine Favoriten. Rechtsklick auf einen Sender, Film oder eine Serie → „Zu Favoriten“." else "Diese Liste ist leer. Rechtsklick auf einen Titel → „Zu Liste hinzufügen …“.")
        } else LazyVerticalGrid(
            GridCells.Adaptive(160.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            for (type in listOf(ContentType.LIVE, ContentType.MOVIE, ContentType.SERIES)) {
                val list = favorites.filter { it.type == type }
                if (list.isEmpty()) continue
                item(span = { GridItemSpan(maxLineSpan) }, key = "h_$type") {
                    Text(typeLabel(type), style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 8.dp))
                }
                val removeFromList = { item: com.poweriptv.app.data.ContentItem ->
                    if (current != null) listOf(com.poweriptv.desktop.ui.MenuAction("Aus „${current.name}“ entfernen") { lib.toggleInList(current.id, item) }) else emptyList()
                }
                if (type == ContentType.LIVE) {
                    items(list, key = { it.key }, span = { GridItemSpan(2) }) { item ->
                        ChannelCard(item, onClick = { app.play(item, channels = list) }, favorite = lib.isFavorite(item), menu = app.itemMenu(item, removeFromList(item)))
                    }
                } else {
                    items(list, key = { it.key }) { item ->
                        PosterCard(
                            item, onClick = { app.navigate(Screen.Detail(item)) }, favorite = lib.isFavorite(item), watched = item.key in watched,
                            menu = app.itemMenu(item, removeFromList(item)),
                        )
                    }
                }
            }
        }
    }
    if (newList || renameFor != null) {
        var name by remember { mutableStateOf(renameFor?.name ?: "") }
        AlertDialog(
            onDismissRequest = { newList = false; renameFor = null },
            title = { Text(if (renameFor != null) "Liste umbenennen" else "Neue Liste") },
            text = { OutlinedTextField(name, { name = it }, singleLine = true, placeholder = { Text("z.B. Filmabend") }) },
            confirmButton = {
                TextButton(onClick = {
                    renameFor?.let { lib.renameList(it.id, name) } ?: run { tab = lib.createList(name) }
                    newList = false; renameFor = null
                }) { Text("Speichern") }
            },
            dismissButton = { TextButton(onClick = { newList = false; renameFor = null }) { Text("Abbrechen") } },
        )
    }
    deleteFor?.let { l ->
        com.poweriptv.desktop.ui.ConfirmDialog("Liste löschen?", "„${l.name}“ wird gelöscht (die Titel selbst bleiben erhalten).", "Löschen", onDismiss = { deleteFor = null }) {
            lib.deleteList(l.id); tab = "default"
        }
    }
}

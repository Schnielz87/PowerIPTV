package com.poweriptv.app.ui.screens

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.poweriptv.app.AppContainer
import com.poweriptv.app.PlayEntry
import com.poweriptv.app.data.ContentItem
import com.poweriptv.app.data.ContentType
import com.poweriptv.app.data.FavoritesRepository
import com.poweriptv.app.ui.components.ErrorBox
import com.poweriptv.app.ui.components.PowerTopBar
import com.poweriptv.app.ui.components.startPlayback
import com.poweriptv.app.ui.components.tvFocus

/** Favoriten & eigene Listen. */
@Composable
fun FavoritesScreen(container: AppContainer, onBack: () -> Unit, onOpenDetail: (ContentItem) -> Unit) {
    val lists by container.favorites.lists.collectAsState()
    val source = container.source
    val context = LocalContext.current
    var selectedId by rememberSaveable { mutableStateOf(FavoritesRepository.DEFAULT_ID) }
    var nameDialog by remember { mutableStateOf<String?>(null) } // null = zu, "" = neu, sonst listId
    var nameInput by remember { mutableStateOf("") }
    var confirmDelete by remember { mutableStateOf(false) }

    val current = lists.firstOrNull { it.id == selectedId } ?: lists.firstOrNull()
    val selectedIndex = lists.indexOf(current).coerceAtLeast(0)

    Scaffold(
        topBar = {
            PowerTopBar("Favoriten & Listen", onBack = onBack, actions = {
                IconButton(modifier = Modifier.tvFocus(CircleShape, 1.15f), onClick = { nameInput = ""; nameDialog = "" }) { Icon(Icons.Filled.Add, "Neue Liste") }
                if (current != null && current.id != FavoritesRepository.DEFAULT_ID) {
                    IconButton(modifier = Modifier.tvFocus(CircleShape, 1.15f), onClick = { nameInput = current.name; nameDialog = current.id }) { Icon(Icons.Filled.Edit, "Umbenennen") }
                    IconButton(modifier = Modifier.tvFocus(CircleShape, 1.15f), onClick = { confirmDelete = true }) { Icon(Icons.Filled.Delete, "Liste loeschen") }
                }
            })
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            ScrollableTabRow(selectedTabIndex = selectedIndex, edgePadding = 8.dp) {
                lists.forEach { l ->
                    Tab(
                        selected = l.id == current?.id,
                        onClick = { selectedId = l.id },
                        text = { Text("${l.name} (${l.items.size})", maxLines = 1) },
                    )
                }
            }
            val items = container.parental.visible(container.source?.profile?.id, current?.items.orEmpty())
            if (items.isEmpty() || source == null) {
                ErrorBox(
                    if (source == null) "Kein Zugang ausgewaehlt"
                    else "Diese Liste ist leer.\nFuege Inhalte ueber das Herz- oder Listen-Symbol hinzu.",
                )
                return@Column
            }
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(12.dp)) {
                items(items, key = { it.key }) { item ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .tvFocus(RoundedCornerShape(10.dp), 1.02f)
                            .background(MaterialTheme.colorScheme.surface)
                            .clickable {
                                if (item.type == ContentType.LIVE || !source.supportsDetails) {
                                    val same = items.filter { it.type == item.type }
                                    startPlayback(
                                        context, container,
                                        same.map { PlayEntry(it.name, source.streamUrl(it), it, live = it.type == ContentType.LIVE) },
                                        same.indexOf(item),
                                    )
                                } else onOpenDetail(item)
                            }
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(Modifier.size(52.dp).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceVariant)) {
                            if (!item.logo.isNullOrBlank()) AsyncImage(item.logo, null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(item.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                when (item.type) {
                                    ContentType.LIVE -> "Live TV"
                                    ContentType.MOVIE -> "Film"
                                    ContentType.SERIES -> "Serie"
                                },
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        IconButton(modifier = Modifier.tvFocus(CircleShape, 1.15f), onClick = { container.favorites.toggleInList(current!!.id, item) }) {
                            Icon(Icons.Filled.Close, "Aus Liste entfernen")
                        }
                    }
                }
            }
        }
    }

    nameDialog?.let { target ->
        AlertDialog(
            onDismissRequest = { nameDialog = null },
            title = { Text(if (target.isEmpty()) "Neue Liste" else "Liste umbenennen") },
            text = {
                OutlinedTextField(value = nameInput, onValueChange = { nameInput = it }, singleLine = true, label = { Text("Name") })
            },
            confirmButton = {
                TextButton(enabled = nameInput.isNotBlank(), onClick = {
                    if (target.isEmpty()) selectedId = container.favorites.createList(nameInput)
                    else container.favorites.renameList(target, nameInput)
                    nameDialog = null
                }) { Text("Speichern") }
            },
            dismissButton = { TextButton(modifier = Modifier.tvFocus(RoundedCornerShape(50), 1.06f), onClick = { nameDialog = null }) { Text("Abbrechen") } },
        )
    }

    if (confirmDelete && current != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Liste loeschen?") },
            text = { Text("\"${current.name}\" wird geloescht. Die Inhalte selbst bleiben erhalten.") },
            confirmButton = {
                TextButton(modifier = Modifier.tvFocus(RoundedCornerShape(50), 1.06f), onClick = {
                    container.favorites.deleteList(current.id)
                    selectedId = FavoritesRepository.DEFAULT_ID
                    confirmDelete = false
                }) { Text("Loeschen") }
            },
            dismissButton = { TextButton(modifier = Modifier.tvFocus(RoundedCornerShape(50), 1.06f), onClick = { confirmDelete = false }) { Text("Abbrechen") } },
        )
    }
}

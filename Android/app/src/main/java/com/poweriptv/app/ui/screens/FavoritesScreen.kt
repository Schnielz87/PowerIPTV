package com.poweriptv.app.ui.screens

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.ImeAction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
import com.poweriptv.app.ui.components.TvOutlinedTextField
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
    var syncMessage by remember { mutableStateOf<String?>(null) }
    var syncing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val firstButton = remember { FocusRequester() }

    val current = lists.firstOrNull { it.id == selectedId } ?: lists.firstOrNull()
    val selectedIndex = lists.indexOf(current).coerceAtLeast(0)

    Scaffold(
        topBar = {
            PowerTopBar("Favoriten & Listen", onBack = onBack)
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            // Knoepfe im Inhalt (nicht nur oben in der Leiste) – so auch mit der Fernbedienung gut erreichbar
            LazyRow(
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item {
                    ListButton(Icons.Filled.Add, "Neue Liste", Modifier.focusRequester(firstButton)) { nameInput = ""; nameDialog = "" }
                }
                if (current != null && current.id != FavoritesRepository.DEFAULT_ID) {
                    item { ListButton(Icons.Filled.Edit, "Umbenennen") { nameInput = current.name; nameDialog = current.id } }
                    item { ListButton(Icons.Filled.Delete, "Liste löschen") { confirmDelete = true } }
                }
                item {
                    ListButton(Icons.Filled.Sync, "Synchronisieren") {
                        val p = source?.profile
                        if (p == null) { syncMessage = "Kein Zugang ausgewählt"; return@ListButton }
                        syncMessage = "Suche Geräte im Heimnetz …"; syncing = true
                        scope.launch {
                            val msg = withContext(Dispatchers.IO) {
                                runCatching { container.sync.syncAll(p) { m -> scope.launch { syncMessage = m } } }
                                    .getOrElse { "Synchronisieren fehlgeschlagen: ${it.message}" }
                            }
                            syncing = false; syncMessage = msg
                        }
                    }
                }
            }
            LaunchedEffect(Unit) { if (container.isTvDevice) runCatching { firstButton.requestFocus() } }
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
                TvOutlinedTextField(
                    value = nameInput, onValueChange = { nameInput = it }, singleLine = true, label = { Text("Name") }, autoEdit = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                )
            },
            confirmButton = {
                TextButton(enabled = nameInput.isNotBlank(), modifier = Modifier.tvFocus(RoundedCornerShape(50), 1.06f), onClick = {
                    if (target.isEmpty()) selectedId = container.favorites.createList(nameInput)
                    else container.favorites.renameList(target, nameInput)
                    nameDialog = null
                }) { Text("Speichern") }
            },
            dismissButton = { TextButton(modifier = Modifier.tvFocus(RoundedCornerShape(50), 1.06f), onClick = { nameDialog = null }) { Text("Abbrechen") } },
        )
    }

    syncMessage?.let { msg ->
        AlertDialog(
            onDismissRequest = { if (!syncing) syncMessage = null },
            title = { Text("Synchronisieren") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (syncing) LinearProgressIndicator(Modifier.fillMaxWidth())
                    Text(msg)
                    if (!syncing) Text(
                        "Gleicht Favoriten, eigene Listen und „Weiterschauen“ mit allen Geräten im Heimnetz ab, " +
                            "auf denen derselbe Zugang eingerichtet und Portiva geöffnet ist.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            confirmButton = {
                if (!syncing) TextButton(modifier = Modifier.tvFocus(RoundedCornerShape(50), 1.06f), onClick = { syncMessage = null }) { Text("OK") }
            },
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

@Composable
private fun ListButton(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, modifier = modifier.tvFocus(RoundedCornerShape(50), 1.06f)) {
        Icon(icon, null, Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(label)
    }
}

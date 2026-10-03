package com.poweriptv.desktop.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.poweriptv.app.data.Category
import com.poweriptv.app.data.ContentItem
import com.poweriptv.app.data.ContentType
import com.poweriptv.desktop.AppState
import com.poweriptv.desktop.Screen
import com.poweriptv.desktop.ui.Accent
import com.poweriptv.desktop.ui.BrandCyan
import com.poweriptv.desktop.ui.ChannelCard
import com.poweriptv.desktop.ui.MenuAction
import com.poweriptv.desktop.ui.PosterCard
import com.poweriptv.desktop.ui.SurfaceHigh
import com.poweriptv.desktop.ui.handCursor
import com.poweriptv.desktop.ui.typeLabel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

const val CAT_ALL = "*"
const val CAT_FAV = "♥"

private enum class Sort(val label: String) { DEFAULT("Standard"), NEWEST("Neueste zuerst"), AZ("A – Z"), RATING("Beste Bewertung") }

@Composable
fun BrowseScreen(app: AppState, type: ContentType) {
    val src = app.source ?: return
    val lib = app.library ?: return
    val favorites by lib.favorites.collectAsState()
    val watched by lib.watched.collectAsState()
    var categories by remember(type, app.dataVersion) { mutableStateOf<List<Category>?>(null) }
    var catError by remember(type) { mutableStateOf<String?>(null) }
    val selected = app.selectedCategory[type] ?: CAT_ALL
    var items by remember(type, selected, app.dataVersion) { mutableStateOf<List<ContentItem>?>(null) }
    var itemError by remember(type, selected) { mutableStateOf<String?>(null) }
    var filter by remember(type) { mutableStateOf("") }
    var catFilter by remember(type) { mutableStateOf("") }
    var sort by remember(type) { mutableStateOf(Sort.DEFAULT) }

    LaunchedEffect(type, app.dataVersion) {
        catError = null
        runCatching { withContext(Dispatchers.IO) { src.categories(type) } }
            .onSuccess { categories = it }.onFailure { catError = it.message ?: "Fehler beim Laden" }
    }
    LaunchedEffect(type, selected, app.dataVersion, favorites.size) {
        itemError = null
        if (selected == CAT_FAV) {
            items = favorites.filter { it.type == type }
            return@LaunchedEffect
        }
        runCatching { withContext(Dispatchers.IO) { src.items(type, selected.takeUnless { it == CAT_ALL }) } }
            .onSuccess { items = it }.onFailure { itemError = it.message ?: "Fehler beim Laden" }
    }

    Row(Modifier.fillMaxSize()) {
        // Kategorien
        Column(Modifier.width(270.dp).fillMaxHeight().background(Color(0xFF0A1322)).padding(12.dp)) {
            Text(typeLabel(type), style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(start = 6.dp, bottom = 10.dp))
            SearchField(catFilter, { catFilter = it }, "Kategorie suchen")
            Spacer(Modifier.size(8.dp))
            val cats = categories
            when {
                catError != null -> Text(catError!!, color = MaterialTheme.colorScheme.error)
                cats == null -> Box(Modifier.fillMaxWidth().padding(20.dp), Alignment.Center) { CircularProgressIndicator() }
                else -> {
                    val all = listOf(Category(CAT_ALL, "Alle"), Category(CAT_FAV, "Favoriten")) +
                        cats.filter { catFilter.isBlank() || it.name.contains(catFilter, true) }
                    val listState = rememberLazyListState()
                    LazyColumn(state = listState) {
                        items(all, key = { it.id }) { c ->
                            CategoryRow(c.name, c.id == selected) { app.selectedCategory[type] = c.id }
                        }
                    }
                }
            }
        }
        // Inhalte
        Column(Modifier.weight(1f).fillMaxHeight().padding(horizontal = 22.dp, vertical = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val catName = when (selected) {
                    CAT_ALL -> "Alle"
                    CAT_FAV -> "Favoriten"
                    else -> categories?.firstOrNull { it.id == selected }?.name ?: ""
                }
                Column(Modifier.weight(1f)) {
                    Text(catName, style = MaterialTheme.typography.headlineSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    items?.let { Text("${it.size} Einträge", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
                Box(Modifier.width(320.dp)) { SearchField(filter, { filter = it }, "In dieser Liste suchen") }
                if (type != ContentType.LIVE) {
                    var open by remember { mutableStateOf(false) }
                    Box {
                        TextButton(onClick = { open = true }, modifier = Modifier.handCursor()) {
                            Icon(Icons.Filled.Sort, null); Spacer(Modifier.width(6.dp)); Text(sort.label)
                        }
                        DropdownMenu(open, onDismissRequest = { open = false }) {
                            Sort.entries.forEach { s -> DropdownMenuItem(text = { Text(s.label) }, onClick = { sort = s; open = false }) }
                        }
                    }
                }
            }
            Spacer(Modifier.size(14.dp))
            val list = items
            when {
                itemError != null -> Text(itemError!!, color = MaterialTheme.colorScheme.error)
                list == null -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
                else -> {
                    val shown = remember(list, filter, sort) {
                        val f = if (filter.isBlank()) list else list.filter { it.name.contains(filter.trim(), true) }
                        when (sort) {
                            Sort.DEFAULT -> f
                            Sort.NEWEST -> f.sortedByDescending { it.added ?: 0L }
                            Sort.AZ -> f.sortedBy { it.name.lowercase() }
                            Sort.RATING -> f.sortedByDescending { it.ratingValue ?: 0.0 }
                        }
                    }
                    if (shown.isEmpty()) EmptyHint(if (selected == CAT_FAV) "Noch keine Favoriten – Rechtsklick auf einen Titel → „Zu Favoriten“." else "Keine Einträge")
                    val favKeys = remember(favorites) { favorites.map { it.key }.toSet() }
                    val gridState = rememberLazyGridState()
                    LaunchedEffect(selected, filter, sort) { gridState.scrollToItem(0) }
                    if (type == ContentType.LIVE) {
                        LazyVerticalGrid(
                            GridCells.Adaptive(300.dp), state = gridState,
                            horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            items(shown, key = { it.key }) { item ->
                                ChannelCard(
                                    item, onClick = { app.play(item, channels = shown) },
                                    favorite = item.key in favKeys,
                                    menu = listOf(MenuAction(if (item.key in favKeys) "Aus Favoriten entfernen" else "Zu Favoriten") { lib.toggleFavorite(item) }),
                                )
                            }
                        }
                    } else {
                        LazyVerticalGrid(
                            GridCells.Adaptive(160.dp), state = gridState,
                            horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(18.dp),
                        ) {
                            items(shown, key = { it.key }) { item ->
                                val pos = if (type == ContentType.MOVIE) lib.position(item.key) else 0L
                                PosterCard(
                                    item, onClick = { app.navigate(Screen.Detail(item)) },
                                    favorite = item.key in favKeys,
                                    watched = item.key in watched,
                                    progress = if (pos > 0) 0.05f.coerceAtLeast(progressOf(app, item, pos)) else 0f,
                                    menu = listOf(
                                        MenuAction("Abspielen") { if (type == ContentType.MOVIE) app.play(item) else app.navigate(Screen.Detail(item)) },
                                        MenuAction(if (item.key in favKeys) "Aus Favoriten entfernen" else "Zu Favoriten") { lib.toggleFavorite(item) },
                                        MenuAction(if (item.key in watched) "Als nicht gesehen markieren" else "Als gesehen markieren") {
                                            lib.markWatched(item.key, item.key !in watched)
                                        },
                                    ),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun progressOf(app: AppState, item: ContentItem, pos: Long): Float {
    val e = app.library?.history?.value?.firstOrNull { it.item.key == item.key } ?: return 0f
    return if (e.duration > 0) pos.toFloat() / e.duration else 0f
}

@Composable
private fun CategoryRow(name: String, selected: Boolean, onClick: () -> Unit) {
    val hover = remember { MutableInteractionSource() }
    val hovered by hover.collectIsHoveredAsState()
    Text(
        name,
        maxLines = 1, overflow = TextOverflow.Ellipsis,
        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
        color = if (selected) BrandCyan else Color.White.copy(alpha = 0.9f),
        modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp).clip(RoundedCornerShape(8.dp))
            .background(if (selected) Accent.copy(alpha = 0.22f) else if (hovered) SurfaceHigh else Color.Transparent)
            .hoverable(hover).handCursor().clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 9.dp),
    )
}

@Composable
fun SearchField(value: String, onChange: (String) -> Unit, hint: String, modifier: Modifier = Modifier) {
    OutlinedTextField(
        value = value, onValueChange = onChange,
        modifier = modifier.fillMaxWidth(),
        singleLine = true,
        placeholder = { Text(hint) },
        leadingIcon = { Icon(Icons.Filled.Search, null) },
        trailingIcon = if (value.isNotEmpty()) ({ IconButton(onClick = { onChange("") }) { Icon(Icons.Filled.Close, "Leeren") } }) else null,
        keyboardOptions = KeyboardOptions.Default,
        shape = RoundedCornerShape(12.dp),
    )
}

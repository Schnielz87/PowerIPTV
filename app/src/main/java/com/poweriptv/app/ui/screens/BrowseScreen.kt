package com.poweriptv.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.poweriptv.app.AppContainer
import com.poweriptv.app.PlayEntry
import com.poweriptv.app.data.Category
import com.poweriptv.app.data.ContentItem
import com.poweriptv.app.data.ContentType
import com.poweriptv.app.ui.components.ErrorBox
import com.poweriptv.app.ui.components.LoadingBox
import com.poweriptv.app.ui.components.PosterCard
import com.poweriptv.app.ui.components.PowerTopBar
import com.poweriptv.app.ui.components.startPlayback
import com.poweriptv.app.ui.components.tvFocus
import com.poweriptv.app.parental.PinDialog

private const val ALL = "__all__"
private const val FAV = "__fav__"

@Composable
fun BrowseScreen(
    container: AppContainer,
    type: ContentType,
    onBack: () -> Unit,
    onOpenDetail: (ContentItem) -> Unit,
) {
    val source = container.source ?: run { ErrorBox("Kein Zugang ausgewaehlt"); return }
    val context = LocalContext.current
    val favorites by container.favorites.favorites.collectAsState()

    var categories by remember { mutableStateOf<List<Category>?>(null) }
    var selected by rememberSaveable { mutableStateOf<String?>(null) }
    var items by remember { mutableStateOf<List<ContentItem>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var query by rememberSaveable { mutableStateOf("") }
    var showSearch by rememberSaveable { mutableStateOf(false) }
    var reload by remember { mutableIntStateOf(0) }
    var pinFor by remember { mutableStateOf<Category?>(null) }
    val parentalOn by container.parental.enabled.collectAsState()
    val parentalUnlocked by container.parental.sessionUnlocked.collectAsState()
    val lockedIds = remember(categories, parentalOn, parentalUnlocked) {
        container.parental.lockedIds(source.profile.id, type, categories.orEmpty())
    }
    fun selectCategory(c: Category) {
        if (c.id != ALL && c.id != FAV && container.parental.requiresPin(source.profile.id, type, c)) pinFor = c
        else selected = c.id
    }

    LaunchedEffect(reload) {
        error = null
        runCatching { source.categories(type) }
            .onSuccess {
                categories = it
                if (selected == null) selected = it.firstOrNull { c ->
                    !container.parental.requiresPin(source.profile.id, type, c)
                }?.id ?: ALL
            }
            .onFailure { error = it.message ?: "Fehler beim Laden" }
    }
    LaunchedEffect(selected, reload, if (selected == FAV) favorites else Unit) {
        val cat = selected ?: return@LaunchedEffect
        if (cat == FAV) {
            items = favorites.filter { it.type == type }; return@LaunchedEffect
        }
        items = null
        error = null
        runCatching { source.items(type, if (cat == ALL) null else cat) }
            .onSuccess { items = it }
            .onFailure { error = it.message ?: "Fehler beim Laden" }
    }

    val filtered = remember(items, query, lockedIds) {
        val list = items.orEmpty().filterNot { it.categoryId in lockedIds }
        if (query.isBlank()) list else list.filter { it.name.contains(query.trim(), ignoreCase = true) }
    }

    val title = when (type) {
        ContentType.LIVE -> "Live TV"
        ContentType.MOVIE -> "Filme"
        ContentType.SERIES -> "Serien"
    }

    Scaffold(
        topBar = {
            PowerTopBar(title, onBack = onBack, actions = {
                IconButton(onClick = { showSearch = !showSearch }) { Icon(Icons.Filled.Search, "Suchen") }
                IconButton(onClick = { source.clearCache(); reload++ }) { Icon(Icons.Filled.Refresh, "Neu laden") }
            })
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        BoxWithConstraints(Modifier.fillMaxSize().padding(padding)) {
            val wide = maxWidth > 700.dp
            val allCats = buildList {
                add(Category(FAV, "★ Favoriten"))
                add(Category(ALL, "Alle"))
                addAll(categories.orEmpty())
            }

            val content: @Composable (Modifier) -> Unit = { mod ->
                Column(mod) {
                    if (showSearch) {
                        OutlinedTextField(
                            value = query,
                            onValueChange = { query = it },
                            placeholder = { Text("Suchen in dieser Kategorie...") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                        )
                    }
                    when {
                        error != null -> ErrorBox(error!!, onRetry = { reload++ })
                        items == null -> LoadingBox()
                        filtered.isEmpty() -> ErrorBox("Keine Eintraege")
                        type == ContentType.LIVE -> LazyColumn(contentPadding = PaddingValues(8.dp)) {
                            itemsIndexed(filtered) { index, item ->
                                ChannelRow(
                                    item = item,
                                    isFavorite = favorites.any { it.key == item.key },
                                    onToggleFavorite = { container.favorites.toggle(item) },
                                    onClick = {
                                        val entries = filtered.map {
                                            PlayEntry(it.name, source.streamUrl(it), it, live = true)
                                        }
                                        startPlayback(context, container, entries, index)
                                    },
                                )
                            }
                        }
                        else -> LazyVerticalGrid(
                            columns = GridCells.Adaptive(if (wide) 140.dp else 110.dp),
                            contentPadding = PaddingValues(8.dp),
                        ) {
                            items(filtered) { item ->
                                PosterCard(item.name, item.logo, onClick = {
                                    if (source.supportsDetails) onOpenDetail(item)
                                    else startPlayback(
                                        context, container,
                                        listOf(PlayEntry(item.name, source.streamUrl(item), item, live = false)), 0,
                                    )
                                })
                            }
                        }
                    }
                }
            }

            if (wide) {
                Row(Modifier.fillMaxSize()) {
                    LazyColumn(
                        Modifier.width(260.dp).fillMaxHeight().background(MaterialTheme.colorScheme.surface),
                        contentPadding = PaddingValues(vertical = 8.dp),
                    ) {
                        items(allCats, key = { it.id }) { c ->
                            val sel = c.id == selected
                            Text(
                                (if (c.id in lockedIds) "🔒 " else "") + c.name,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal,
                                color = if (sel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .tvFocus(RoundedCornerShape(6.dp), 1f)
                                    .clickable { selectCategory(c) }
                                    .background(if (sel) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface)
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                            )
                        }
                    }
                    content(Modifier.weight(1f).fillMaxHeight())
                }
            } else {
                Column(Modifier.fillMaxSize()) {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        items(allCats, key = { it.id }) { c ->
                            FilterChip(
                                selected = c.id == selected,
                                onClick = { selectCategory(c) },
                                label = { Text((if (c.id in lockedIds) "🔒 " else "") + c.name, maxLines = 1) },
                            )
                        }
                    }
                    content(Modifier.weight(1f))
                }
            }
        }
    }

    pinFor?.let { c ->
        PinDialog(container.parental, onDismiss = { pinFor = null }, onSuccess = { selected = c.id; pinFor = null })
    }
}

@Composable
private fun ChannelRow(item: ContentItem, isFavorite: Boolean, onToggleFavorite: () -> Unit, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .clip(RoundedCornerShape(10.dp))
            .tvFocus(RoundedCornerShape(10.dp), 1.02f)
            .clickable(onClick = onClick)
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(48.dp).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.LiveTv, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            if (!item.logo.isNullOrBlank()) {
                AsyncImage(item.logo, item.name, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize().padding(4.dp))
            }
        }
        Spacer(Modifier.width(12.dp))
        item.number?.let {
            Text("$it", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(end = 10.dp))
        }
        Text(item.name, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        IconButton(onClick = onToggleFavorite) {
            Icon(
                if (isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                "Favorit",
                tint = if (isFavorite) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

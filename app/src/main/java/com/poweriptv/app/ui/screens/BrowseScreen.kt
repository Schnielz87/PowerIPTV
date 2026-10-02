package com.poweriptv.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import com.poweriptv.app.AppContainer
import com.poweriptv.app.PlayEntry
import com.poweriptv.app.data.Category
import com.poweriptv.app.data.ContentItem
import com.poweriptv.app.data.ContentType
import com.poweriptv.app.parental.PinDialog
import com.poweriptv.app.ui.components.CAT_ALL
import com.poweriptv.app.ui.components.CAT_FAV
import com.poweriptv.app.ui.components.CAT_RECENT
import com.poweriptv.app.ui.components.CategorySidebar
import com.poweriptv.app.ui.components.ContentFilter
import com.poweriptv.app.ui.components.ErrorBox
import com.poweriptv.app.ui.components.FilterDialog
import com.poweriptv.app.ui.components.LoadingBox
import com.poweriptv.app.ui.components.PosterCard
import com.poweriptv.app.ui.components.PowerTopBar
import com.poweriptv.app.ui.components.startPlayback
import com.poweriptv.app.ui.components.stripLanguage
import com.poweriptv.app.ui.components.tvFocus
import kotlinx.coroutines.delay

/** Einfache Volltextsuche: alle Woerter muessen im Titel vorkommen. */
fun matchesQuery(name: String, query: String): Boolean {
    val words = query.trim().lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }
    if (words.isEmpty()) return true
    val n = name.lowercase()
    return words.all { it in n }
}

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
    val history by container.history.items.collectAsState()
    val language by container.settings.categoryLanguage.collectAsState()
    val parentalOn by container.parental.enabled.collectAsState()
    val parentalUnlocked by container.parental.sessionUnlocked.collectAsState()

    var categories by remember { mutableStateOf<List<Category>?>(null) }
    var selected by rememberSaveable(type) { mutableStateOf<String?>(null) }
    var items by remember { mutableStateOf<List<ContentItem>?>(null) }
    var allItems by remember { mutableStateOf<List<ContentItem>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var query by rememberSaveable { mutableStateOf("") }
    var appliedQuery by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf(ContentFilter()) }
    var showFilter by remember { mutableStateOf(false) }
    var showCategoryPicker by remember { mutableStateOf(false) }
    var reload by remember { mutableIntStateOf(0) }
    var pinFor by remember { mutableStateOf<Category?>(null) }

    val lockedIds = remember(categories, parentalOn, parentalUnlocked) {
        container.parental.lockedIds(source.profile.id, type, categories.orEmpty())
    }

    fun selectCategory(c: Category) {
        showCategoryPicker = false
        query = ""
        if (c.id !in listOf(CAT_ALL, CAT_FAV, CAT_RECENT) && container.parental.requiresPin(source.profile.id, type, c)) pinFor = c
        else selected = c.id
    }

    // Kategorien laden; Startkategorie = erste freigegebene in der bevorzugten Sprache
    LaunchedEffect(reload) {
        error = null
        runCatching { source.categories(type) }
            .onSuccess { cats ->
                categories = cats
                if (selected == null) {
                    val allowed = cats.filterNot { container.parental.requiresPin(source.profile.id, type, it) }
                    selected = (allowed.firstOrNull { language.isNotEmpty() && com.poweriptv.app.ui.components.categoryLanguage(it.name) == language }
                        ?: allowed.firstOrNull())?.id ?: CAT_ALL
                }
            }
            .onFailure { error = it.message ?: "Fehler beim Laden" }
    }
    // Inhalte der gewaehlten Kategorie
    LaunchedEffect(selected, reload, if (selected == CAT_FAV) favorites else Unit, if (selected == CAT_RECENT) history else Unit) {
        val cat = selected ?: return@LaunchedEffect
        when (cat) {
            CAT_FAV -> { items = favorites.filter { it.type == type }; return@LaunchedEffect }
            CAT_RECENT -> { items = history.filter { it.type == type }; return@LaunchedEffect }
        }
        items = null
        error = null
        runCatching { source.items(type, if (cat == CAT_ALL) null else cat) }
            .onSuccess { items = it }
            .onFailure { error = it.message ?: "Fehler beim Laden" }
    }
    // Suche: kurz warten (Tippen), dann in ALLEN Kategorien suchen
    LaunchedEffect(query) {
        delay(300)
        appliedQuery = query.trim()
        if (appliedQuery.isNotEmpty() && allItems == null) {
            allItems = runCatching { source.items(type, null) }.getOrElse { error = it.message; emptyList() }
        }
    }

    val searching = appliedQuery.isNotEmpty()
    val baseList = if (searching) allItems else items
    val shown = remember(baseList, appliedQuery, lockedIds, filter) {
        baseList?.let { list ->
            filter.apply(list.filter { it.categoryId !in lockedIds && matchesQuery(it.name, appliedQuery) })
        }
    }
    val genres = remember(baseList) { ContentFilter.genres(baseList.orEmpty()) }

    val title = when (type) {
        ContentType.LIVE -> "Live TV"
        ContentType.MOVIE -> "Filme"
        ContentType.SERIES -> "Serien"
    }
    val selectedName = when (selected) {
        CAT_FAV -> "Favoriten"
        CAT_RECENT -> "Zuletzt gesehen"
        CAT_ALL -> "Alle"
        else -> categories?.firstOrNull { it.id == selected }?.name?.let { if (language.isNotEmpty()) stripLanguage(it) else it } ?: ""
    }

    Scaffold(
        topBar = {
            PowerTopBar(title, onBack = onBack, actions = {
                IconButton(onClick = { source.clearCache(); allItems = null; reload++ }) { Icon(Icons.Filled.Refresh, "Neu laden") }
            })
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        BoxWithConstraints(Modifier.fillMaxSize().padding(padding)) {
            val wide = maxWidth > 600.dp
            Row(Modifier.fillMaxSize()) {
                if (wide) {
                    CategorySidebar(
                        categories = categories.orEmpty(),
                        selected = if (searching) null else selected,
                        lockedIds = lockedIds,
                        language = language,
                        onLanguage = { container.settings.setCategoryLanguage(it) },
                        onSelect = ::selectCategory,
                        modifier = Modifier.width(if (maxWidth > 900.dp) 300.dp else 250.dp).fillMaxHeight(),
                    )
                }
                Column(Modifier.weight(1f).fillMaxHeight()) {
                    // Werkzeugleiste: (Kategorie) · Suche · Filter
                    Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (!wide) {
                            OutlinedButton(onClick = { showCategoryPicker = true }, modifier = Modifier.tvFocus()) {
                                Text(selectedName.ifBlank { "Kategorie" }, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.width(110.dp))
                                Icon(Icons.Filled.ArrowDropDown, null)
                            }
                            Spacer(Modifier.width(8.dp))
                        }
                        OutlinedTextField(
                            value = query,
                            onValueChange = { query = it },
                            placeholder = { Text("$title suchen (alle Kategorien)") },
                            leadingIcon = { Icon(Icons.Filled.Search, null) },
                            trailingIcon = {
                                if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Filled.Clear, "Leeren") }
                            },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                        Spacer(Modifier.width(6.dp))
                        IconButton(onClick = { showFilter = true }, modifier = Modifier.tvFocus()) {
                            BadgedBox(badge = { if (filter.activeCount > 0) Badge { Text("${filter.activeCount}") } }) {
                                Icon(Icons.Filled.FilterList, "Filter")
                            }
                        }
                    }
                    // Info-Zeile
                    Row(Modifier.padding(horizontal = 14.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            if (searching) "Suche „$appliedQuery“ in allen Kategorien" else selectedName,
                            fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        shown?.let {
                            Text("  ·  ${it.size} ${if (type == ContentType.LIVE) "Kanaele" else "Titel"}",
                                color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                        }
                        if (searching && allItems == null) {
                            Spacer(Modifier.width(8.dp))
                            CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
                        }
                    }

                    when {
                        error != null && shown.isNullOrEmpty() -> ErrorBox(error!!, onRetry = { reload++ })
                        shown == null -> LoadingBox()
                        shown.isEmpty() -> ErrorBox(
                            when {
                                searching -> "Keine Treffer fuer „$appliedQuery“"
                                filter.activeCount > 0 -> "Keine Eintraege fuer diese Filter"
                                selected == CAT_FAV -> "Noch keine Favoriten"
                                selected == CAT_RECENT -> "Noch nichts angesehen"
                                else -> "Keine Eintraege"
                            }
                        )
                        type == ContentType.LIVE -> LazyColumn(contentPadding = PaddingValues(8.dp)) {
                            itemsIndexed(shown) { index, item ->
                                ChannelRow(
                                    item = item,
                                    isFavorite = favorites.any { it.key == item.key },
                                    onToggleFavorite = { container.favorites.toggle(item) },
                                    onClick = {
                                        val entries = shown.map { PlayEntry(it.name, source.streamUrl(it), it, live = true) }
                                        startPlayback(context, container, entries, index)
                                    },
                                )
                            }
                        }
                        else -> LazyVerticalGrid(
                            columns = GridCells.Adaptive(if (wide) 130.dp else 110.dp),
                            contentPadding = PaddingValues(8.dp),
                        ) {
                            items(shown) { item ->
                                PosterCard(item.name, item.logo, onClick = {
                                    if (source.supportsDetails) onOpenDetail(item)
                                    else startPlayback(
                                        context, container,
                                        listOf(PlayEntry(item.name, source.streamUrl(item), item, live = false)), 0,
                                    )
                                }, subtitle = listOfNotNull(item.year?.toString(), item.ratingValue?.let { "★ %.1f".format(it) }).joinToString("  "))
                            }
                        }
                    }
                }
            }
        }
    }

    if (showCategoryPicker) {
        Dialog(onDismissRequest = { showCategoryPicker = false }) {
            Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surface) {
                CategorySidebar(
                    categories = categories.orEmpty(),
                    selected = selected,
                    lockedIds = lockedIds,
                    language = language,
                    onLanguage = { container.settings.setCategoryLanguage(it) },
                    onSelect = ::selectCategory,
                    modifier = Modifier.fillMaxWidth().height(520.dp),
                )
            }
        }
    }

    if (showFilter) {
        FilterDialog(
            filter = filter,
            genres = genres,
            showRatingAndYear = type != ContentType.LIVE,
            onChange = { filter = it },
            onDismiss = { showFilter = false },
        )
    }

    pinFor?.let { c ->
        PinDialog(container.parental, onDismiss = { pinFor = null }, onSuccess = { selected = c.id; pinFor = null })
    }
}

@Composable
fun ChannelRow(item: ContentItem, isFavorite: Boolean, onToggleFavorite: () -> Unit, onClick: () -> Unit) {
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

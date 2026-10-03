package com.poweriptv.desktop.ui.screens

import androidx.compose.foundation.ContextMenuArea
import androidx.compose.foundation.ContextMenuItem
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FilterAlt
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.poweriptv.app.data.Category
import com.poweriptv.app.data.ContentItem
import com.poweriptv.app.data.ContentType
import com.poweriptv.app.ui.components.CAT_ALL
import com.poweriptv.app.ui.components.CAT_FAV
import com.poweriptv.app.ui.components.CAT_RECENT
import com.poweriptv.app.ui.components.ContentFilter
import com.poweriptv.app.ui.components.FilterDialog
import com.poweriptv.app.ui.components.categoryLanguage
import com.poweriptv.app.ui.components.detectLanguages
import com.poweriptv.app.ui.components.stripLanguage
import com.poweriptv.desktop.AppState
import com.poweriptv.desktop.Screen
import com.poweriptv.desktop.ui.BrandCyan
import com.poweriptv.desktop.ui.ChannelCard
import com.poweriptv.desktop.ui.MenuAction
import com.poweriptv.desktop.ui.PosterCard
import com.poweriptv.desktop.ui.handCursor
import com.poweriptv.desktop.ui.itemMenu
import com.poweriptv.desktop.ui.typeLabel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/** Alle Woerter der Suche muessen im Namen vorkommen (wie Android). */
fun matchesQuery(name: String, query: String): Boolean {
    val words = query.trim().lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }
    if (words.isEmpty()) return true
    val n = name.lowercase()
    return words.all { it in n }
}

@Composable
fun BrowseScreen(app: AppState, type: ContentType) {
    val src = app.source ?: return
    val lib = app.library ?: return
    val favorites by lib.favorites.collectAsState()
    val watched by lib.watched.collectAsState()
    val history by lib.history.collectAsState()
    val settings by app.settings.state.collectAsState()
    val language = settings.categoryLanguage

    var categories by remember(type, app.dataVersion) { mutableStateOf<List<Category>?>(null) }
    var error by remember(type) { mutableStateOf<String?>(null) }
    val selected = app.selectedCategory[type]
    var items by remember(type) { mutableStateOf<List<ContentItem>?>(null) }
    var allItems by remember(type, app.dataVersion) { mutableStateOf<List<ContentItem>?>(null) }
    var wantAll by remember(type) { mutableStateOf(false) }
    var searchError by remember(type) { mutableStateOf<String?>(null) }
    var query by remember(type) { mutableStateOf("") }
    var appliedQuery by remember(type) { mutableStateOf("") }
    var searchEverywhere by remember(type) { mutableStateOf(false) }
    var showFilter by remember { mutableStateOf(false) }
    var reload by remember { mutableIntStateOf(0) }
    val filter = app.browseFilters[type] ?: ContentFilter()
    // Kindersicherung wie Android: gesperrte Kategorien nur mit PIN, Titel daraus ueberall ausgeblendet
    val parentalOn by app.parental.enabled.collectAsState()
    val parentalUnlocked by app.parental.sessionUnlocked.collectAsState()
    val pid = src.profile.id
    val lockedIds = remember(categories, parentalOn, parentalUnlocked) { app.parental.lockedIds(pid, type, categories.orEmpty()) }
    var pinFor by remember { mutableStateOf<Category?>(null) }
    fun selectCategory(c: Category) {
        if (c.id !in listOf(CAT_ALL, CAT_FAV, CAT_RECENT) && app.parental.requiresPin(pid, type, c)) pinFor = c
        else app.selectedCategory[type] = c.id
    }

    // Kategorien laden; Startkategorie = erste in der bevorzugten Sprache (wie Android)
    LaunchedEffect(type, app.dataVersion, reload) {
        error = null
        runCatching { withContext(Dispatchers.IO) { src.categories(type) } }
            .onSuccess { cats ->
                categories = cats
                if (app.selectedCategory[type] == null) {
                    val allowed = cats.filterNot { app.parental.requiresPin(src.profile.id, type, it) }
                    app.selectedCategory[type] = (allowed.firstOrNull { language.isNotEmpty() && categoryLanguage(it.name) == language }
                        ?: allowed.firstOrNull())?.id ?: CAT_ALL
                }
            }
            .onFailure { error = it.message ?: "Fehler beim Laden" }
    }
    // Inhalte der gewaehlten Kategorie
    LaunchedEffect(type, selected, app.dataVersion, reload, if (selected == CAT_FAV) favorites else Unit, if (selected == CAT_RECENT) history else Unit) {
        val cat = selected ?: return@LaunchedEffect
        when (cat) {
            CAT_FAV -> { items = app.parental.visible(src.profile.id, favorites.filter { it.type == type }); return@LaunchedEffect }
            CAT_RECENT -> { items = app.parental.visible(src.profile.id, history.filter { it.item.type == type }.map { it.item }); return@LaunchedEffect }
        }
        items = null; error = null
        runCatching { withContext(Dispatchers.IO) { src.items(type, if (cat == CAT_ALL) null else cat) } }
            .onSuccess { items = it }.onFailure { error = it.message ?: "Fehler beim Laden" }
    }
    LaunchedEffect(query) { delay(250); appliedQuery = query.trim() }
    LaunchedEffect(wantAll, app.dataVersion, reload) {
        if (!wantAll || allItems != null) return@LaunchedEffect
        searchError = null
        try {
            allItems = withContext(Dispatchers.IO) { src.items(type, null) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            searchError = "Suche nicht möglich: ${e.message ?: e.javaClass.simpleName}"
        }
    }

    val searching = appliedQuery.isNotEmpty()
    val scoped = !searchEverywhere && selected != CAT_ALL
    val searchAll = searching && !scoped
    LaunchedEffect(searchAll) { if (searchAll) wantAll = true }
    // Aktiver Sprachfilter (z.B. DE) gilt auch fuer "Alle" und die Suche ueber alle Kategorien
    val langCategoryIds = remember(categories, language) {
        val cats = categories.orEmpty()
        if (language.isEmpty() || language !in detectLanguages(cats)) null
        else cats.filter { categoryLanguage(it.name) == language }.map { it.id }.toSet()
    }
    val baseList = when {
        searchAll -> allItems?.let { list -> langCategoryIds?.let { ids -> list.filter { it.categoryId in ids } } ?: list }
        selected == CAT_ALL -> items?.let { list -> langCategoryIds?.let { ids -> list.filter { it.categoryId in ids } } ?: list }
        else -> items
    }
    val shown = remember(baseList, appliedQuery, filter, lockedIds) {
        baseList?.let { list -> filter.apply(list.filter { it.categoryId !in lockedIds && matchesQuery(it.name, appliedQuery) }) }
    }
    val genres = remember(baseList) { ContentFilter.genres(baseList.orEmpty()) }
    val title = typeLabel(type)
    val selectedName = when (selected) {
        CAT_FAV -> "Favoriten"
        CAT_RECENT -> "Zuletzt gesehen"
        CAT_ALL -> "Alle"
        else -> categories?.firstOrNull { it.id == selected }?.name?.let { if (language.isNotEmpty()) stripLanguage(it) else it } ?: ""
    }

    Row(Modifier.fillMaxSize()) {
        CategorySidebar(
            app = app, type = type, title = title,
            categories = categories.orEmpty(),
            selected = if (searchAll) null else selected,
            language = language,
            onLanguage = { l -> app.settings.update { it.copy(categoryLanguage = l) } },
            onSelect = ::selectCategory,
            lockedIds = lockedIds,
            modifier = Modifier.width(300.dp).fillMaxHeight(),
        )
        Column(Modifier.weight(1f).fillMaxHeight().padding(horizontal = 18.dp, vertical = 12.dp)) {
            // Werkzeugleiste: Suche · Nur hier/Ueberall · Filter · Neu laden
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = query, onValueChange = { query = it },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    placeholder = {
                        Text(
                            when {
                                scoped -> "In „$selectedName“ suchen"
                                langCategoryIds != null -> "$title suchen (alle $language-Kategorien)"
                                else -> "$title suchen (alle Kategorien)"
                            },
                        )
                    },
                    leadingIcon = { Icon(Icons.Filled.Search, null) },
                    trailingIcon = if (query.isNotEmpty()) ({ IconButton(onClick = { query = "" }) { Icon(Icons.Filled.Close, "Leeren") } }) else null,
                    shape = RoundedCornerShape(12.dp),
                )
                if (selected != CAT_ALL) {
                    Text(
                        if (searchEverywhere) "Überall" else "Nur hier",
                        style = MaterialTheme.typography.labelLarge,
                        color = if (searchEverywhere) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(start = 8.dp).clip(RoundedCornerShape(8.dp))
                            .handCursor().clickable { searchEverywhere = !searchEverywhere }
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                    )
                }
                IconButton(onClick = { showFilter = true }, modifier = Modifier.handCursor()) {
                    BadgedBox(badge = { if (filter.activeCount > 0) Badge { Text("${filter.activeCount}") } }) {
                        Icon(Icons.Filled.FilterList, "Filter & Sortierung")
                    }
                }
                IconButton(onClick = { src.clearCache(); allItems = null; reload++ }, modifier = Modifier.handCursor()) {
                    Icon(Icons.Filled.Refresh, "Neu laden")
                }
            }
            // Info-Zeile
            Row(Modifier.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    when {
                        !searching -> selectedName
                        scoped -> "Suche „$appliedQuery“ in $selectedName"
                        langCategoryIds != null -> "Suche „$appliedQuery“ in allen $language-Kategorien"
                        else -> "Suche „$appliedQuery“ in allen Kategorien"
                    },
                    style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false),
                )
                shown?.let {
                    Text("  ·  ${it.size} ${if (type == ContentType.LIVE) "Kanäle" else "Titel"}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (searchAll && allItems == null && searchError == null) {
                    Spacer(Modifier.width(8.dp))
                    CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
                    Text("  Lade alle $title …", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            val list = shown
            when {
                error != null -> Text(error!!, color = MaterialTheme.colorScheme.error)
                searchError != null && searchAll -> Text(searchError!!, color = MaterialTheme.colorScheme.error)
                list == null -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
                list.isEmpty() -> EmptyHint(
                    when (selected) {
                        CAT_FAV -> "Noch keine Favoriten – Rechtsklick auf einen Titel → „Zu Favoriten“."
                        CAT_RECENT -> "Noch nichts angesehen."
                        else -> if (searching) "Nichts gefunden" else "Keine Einträge"
                    },
                )
                else -> ContentGrid(app, type, list, favorites.map { it.key }.toSet(), watched, resetKey = "$selected|$appliedQuery|$filter")
            }
        }
    }
    pinFor?.let { c ->
        com.poweriptv.desktop.ui.PinDialog(app.parental, onDismiss = { pinFor = null }) { app.selectedCategory[type] = c.id; pinFor = null }
    }
    if (showFilter) {
        FilterDialog(
            filter = filter, genres = genres, showRatingAndYear = type != ContentType.LIVE,
            onChange = { app.browseFilters[type] = it },
            onDismiss = { showFilter = false },
        )
    }
}

@Composable
private fun ContentGrid(app: AppState, type: ContentType, list: List<ContentItem>, favKeys: Set<String>, watched: Set<String>, resetKey: String) {
    val lib = app.library ?: return
    val gridState = rememberLazyGridState()
    LaunchedEffect(resetKey) { gridState.scrollToItem(0) }
    if (type == ContentType.LIVE) {
        val epgState by app.epg.state.collectAsState()
        LazyVerticalGrid(
            GridCells.Adaptive(300.dp), state = gridState,
            horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(list, key = { it.key }) { item ->
                ChannelCard(
                    item, onClick = { app.play(item, channels = list) },
                    favorite = item.key in favKeys,
                    subtitle = remember(item.key, epgState) { app.epg.current(item)?.let { "Jetzt: ${it.title}" } },
                    menu = app.itemMenu(item),
                )
            }
        }
    } else {
        LazyVerticalGrid(
            GridCells.Adaptive(160.dp), state = gridState,
            horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            items(list, key = { it.key }) { item ->
                val pos = if (type == ContentType.MOVIE) lib.position(item.key) else 0L
                val dur = if (pos > 0) lib.history.value.firstOrNull { it.item.key == item.key }?.duration ?: 0L else 0L
                PosterCard(
                    item, onClick = { app.navigate(Screen.Detail(item)) },
                    favorite = item.key in favKeys,
                    watched = item.key in watched,
                    progress = if (pos > 0 && dur > 0) (pos.toFloat() / dur).coerceAtLeast(0.03f) else 0f,
                    menu = app.itemMenu(item, listOf(MenuAction("Abspielen") { if (type == ContentType.MOVIE) app.play(item) else app.navigate(Screen.Detail(item)) })),
                )
            }
        }
    }
}

/** Linke Kategorie-Spalte wie in der Android-App: Sprache, Kategorie-Filter, Favoriten/Zuletzt/Alle, Anheften/Ausblenden. */
@Composable
private fun CategorySidebar(
    app: AppState,
    type: ContentType,
    title: String,
    categories: List<Category>,
    selected: String?,
    language: String,
    onLanguage: (String) -> Unit,
    onSelect: (Category) -> Unit,
    modifier: Modifier,
    lockedIds: Set<String> = emptySet(),
) {
    val prefs = app.categoryPrefs
    val scope = "${app.profile?.id}|${type.name}"
    val prefsVersion by prefs.version.collectAsState()
    var showHidden by remember { mutableStateOf(false) }
    val hiddenIds = remember(prefsVersion, scope) { prefs.hidden(scope) }
    val pinnedIds = remember(prefsVersion, scope) { prefs.pinned(scope) }
    var catQuery by remember { mutableStateOf("") }
    var showCatSearch by remember { mutableStateOf(false) }
    var langMenu by remember { mutableStateOf(false) }
    val languages = remember(categories) { detectLanguages(categories) }
    val activeLang = language.takeIf { it in languages } ?: ""
    val visible = remember(categories, activeLang, catQuery, prefsVersion, showHidden) {
        val filtered = categories.filter { c ->
            (activeLang.isEmpty() || categoryLanguage(c.name) == activeLang) &&
                (catQuery.isBlank() || c.name.contains(catQuery.trim(), ignoreCase = true)) &&
                (showHidden || c.id !in hiddenIds)
        }
        prefs.arrange(scope, filtered) { it.id }
    }

    Column(modifier.background(MaterialTheme.colorScheme.surface)) {
        Row(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, maxLines = 1, modifier = Modifier.weight(1f).padding(start = 6.dp))
            if (languages.size >= 2) {
                Box {
                    Row(
                        Modifier.clip(RoundedCornerShape(8.dp)).handCursor().clickable { langMenu = true }
                            .background(if (activeLang.isNotEmpty()) BrandCyan.copy(alpha = 0.16f) else MaterialTheme.colorScheme.surfaceVariant)
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Filled.Language, null, Modifier.size(16.dp), tint = if (activeLang.isNotEmpty()) BrandCyan else MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.width(4.dp))
                        Text(activeLang.ifEmpty { "Alle" }, style = MaterialTheme.typography.labelLarge)
                        Icon(Icons.Filled.ArrowDropDown, null, Modifier.size(18.dp))
                    }
                    DropdownMenu(expanded = langMenu, onDismissRequest = { langMenu = false }) {
                        DropdownMenuItem(text = { Text("Alle Sprachen") }, onClick = { onLanguage(""); langMenu = false })
                        languages.forEach { l ->
                            DropdownMenuItem(
                                text = { Text(l, fontWeight = if (l == activeLang) FontWeight.Bold else FontWeight.Normal) },
                                onClick = {
                                    onLanguage(l); langMenu = false
                                    // direkt die erste Kategorie dieser Sprache oeffnen
                                    categories.firstOrNull { categoryLanguage(it.name) == l }?.let(onSelect)
                                },
                            )
                        }
                    }
                }
            }
            IconButton(onClick = { showCatSearch = !showCatSearch; if (!showCatSearch) catQuery = "" }, modifier = Modifier.handCursor()) {
                Icon(Icons.Filled.FilterAlt, "Kategorien filtern", tint = if (showCatSearch) BrandCyan else MaterialTheme.colorScheme.onSurface)
            }
        }
        if (showCatSearch) {
            OutlinedTextField(
                catQuery, { catQuery = it }, singleLine = true,
                placeholder = { Text("Kategorie-Namen filtern") },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp),
                shape = RoundedCornerShape(10.dp),
            )
        }
        LazyColumn(Modifier.fillMaxHeight(), contentPadding = PaddingValues(top = 2.dp, bottom = 16.dp)) {
            item { SidebarRow(Icons.Filled.Star, "Favoriten", selected == CAT_FAV) { onSelect(Category(CAT_FAV, "Favoriten")) } }
            item { SidebarRow(Icons.Filled.History, "Zuletzt gesehen", selected == CAT_RECENT) { onSelect(Category(CAT_RECENT, "Zuletzt gesehen")) } }
            item { SidebarRow(Icons.Filled.Apps, "Alle", selected == CAT_ALL) { onSelect(Category(CAT_ALL, "Alle")) } }
            item {
                Text(
                    "Kategorien (${visible.size})", style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 14.dp, top = 8.dp, bottom = 2.dp),
                )
            }
            items(visible, key = { it.id }) { c ->
                val label = if (activeLang.isNotEmpty()) stripLanguage(c.name) else c.name
                val pinned = c.id in pinnedIds
                val hidden = c.id in hiddenIds
                ContextMenuArea(items = {
                    listOf(
                        ContextMenuItem(if (pinned) "Nicht mehr oben anheften" else "📌 Oben anheften") { prefs.setPinned(scope, c.id, !pinned) },
                        ContextMenuItem(if (hidden) "Wieder einblenden" else "Ausblenden") { prefs.setHidden(scope, c.id, !hidden) },
                    )
                }) {
                    SidebarRow(
                        if (c.id in lockedIds) Icons.Filled.Lock else if (pinned) Icons.Filled.PushPin else if (hidden) Icons.Filled.VisibilityOff else Icons.Filled.Folder,
                        label, selected == c.id, dimmed = hidden,
                    ) { onSelect(c) }
                }
            }
            if (hiddenIds.isNotEmpty()) item {
                SidebarRow(
                    if (showHidden) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                    if (showHidden) "Ausgeblendete verbergen" else "Ausgeblendete anzeigen (${hiddenIds.size})",
                    false, dimmed = true,
                ) { showHidden = !showHidden }
            }
            item {
                Text(
                    "Tipp: Rechtsklick auf eine Kategorie zum Anheften oder Ausblenden.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun SidebarRow(icon: ImageVector, label: String, selected: Boolean, dimmed: Boolean = false, onClick: () -> Unit) {
    val hover = remember { MutableInteractionSource() }
    val hovered by hover.collectIsHoveredAsState()
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 1.dp).clip(RoundedCornerShape(8.dp))
            .hoverable(hover).handCursor().clickable(onClick = onClick)
            .alpha(if (dimmed) 0.55f else 1f)
            .background(if (selected) BrandCyan.copy(alpha = 0.16f) else if (hovered) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent)
            .padding(horizontal = 6.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(3.dp).height(18.dp).background(if (selected) BrandCyan else Color.Transparent))
        Spacer(Modifier.width(8.dp))
        Icon(icon, null, tint = if (selected) BrandCyan else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        Text(
            label, maxLines = 1, overflow = TextOverflow.Ellipsis,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = if (selected) BrandCyan else MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

/** Suchfeld (Suche-Seite). */
@Composable
fun SearchField(value: String, onChange: (String) -> Unit, hint: String, modifier: Modifier = Modifier) {
    OutlinedTextField(
        value = value, onValueChange = onChange,
        modifier = modifier.fillMaxWidth(),
        singleLine = true,
        placeholder = { Text(hint) },
        leadingIcon = { Icon(Icons.Filled.Search, null) },
        trailingIcon = if (value.isNotEmpty()) ({ IconButton(onClick = { onChange("") }) { Icon(Icons.Filled.Close, "Leeren") } }) else null,
        shape = RoundedCornerShape(12.dp),
    )
}

package com.poweriptv.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.poweriptv.app.AppContainer
import com.poweriptv.app.PlayEntry
import com.poweriptv.app.data.ContentItem
import com.poweriptv.app.data.ContentType
import com.poweriptv.app.ui.components.ErrorBox
import com.poweriptv.app.ui.components.PosterCard
import com.poweriptv.app.ui.components.PowerTopBar
import com.poweriptv.app.ui.components.startPlayback
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay

/** Globale Suche ueber Live TV, Filme und Serien. */
@Composable
fun SearchScreen(container: AppContainer, onBack: () -> Unit, onOpenDetail: (ContentItem) -> Unit) {
    val source = container.source ?: run { ErrorBox("Kein Zugang ausgewaehlt"); return }
    val context = LocalContext.current
    val favorites by container.favorites.favorites.collectAsState()
    // Langes Druecken: Menue (Favorit, Teilen)
    var actionsFor by remember { mutableStateOf<ContentItem?>(null) }
    actionsFor?.let { com.poweriptv.app.ui.components.ItemActionsDialog(container, it, onDismiss = { actionsFor = null }) }
    var query by rememberSaveable { mutableStateOf("") }
    var applied by remember { mutableStateOf("") }
    val pools = remember { mutableStateMapOf<ContentType, List<ContentItem>>() }
    var loading by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

    var wantAll by remember { mutableStateOf(false) }
    LaunchedEffect(query) {
        delay(300)
        applied = query.trim()
        if (applied.length >= 2) wantAll = true
    }
    // Alle drei Bereiche EINMAL parallel laden – unabhaengig vom Tippen
    LaunchedEffect(wantAll) {
        if (!wantAll) return@LaunchedEffect
        loading = true
        coroutineScope {
            ContentType.entries.filter { it !in pools }.forEach { t ->
                launch {
                    try {
                        val cats = source.categories(t)
                        val locked = container.parental.lockedIds(source.profile.id, t, cats)
                        pools[t] = source.items(t, null).filterNot { it.categoryId in locked }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        pools[t] = emptyList()
                    }
                }
            }
        }
        loading = false
    }

    val results = remember(applied, pools.size) {
        if (applied.length < 2) emptyMap()
        else ContentType.entries.associateWith { t -> pools[t].orEmpty().filter { matchesQuery(it.name, applied) }.take(100) }
    }

    fun open(item: ContentItem, list: List<ContentItem>) {
        if (item.type == ContentType.LIVE) {
            startPlayback(context, container, list.map { PlayEntry(it.name, source.streamUrl(it), it, live = true) }, list.indexOf(item))
        } else if (source.supportsDetails) onOpenDetail(item)
        else startPlayback(context, container, listOf(PlayEntry(item.name, source.streamUrl(item), item, live = false)), 0)
    }

    Scaffold(
        topBar = { PowerTopBar("Suche", onBack = onBack) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = query, onValueChange = { query = it },
                    placeholder = { Text("Film, Serie oder Sender suchen...") },
                    leadingIcon = { Icon(Icons.Filled.Search, null) },
                    trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Filled.Clear, "Leeren") } },
                    singleLine = true,
                    modifier = Modifier.weight(1f).focusRequester(focus),
                )
                if (loading) {
                    Spacer(Modifier.width(12.dp))
                    CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                }
            }
            when {
                applied.length < 2 -> ErrorBox("Mindestens 2 Zeichen eingeben.\nGesucht wird in allen Kategorien von Live TV, Filmen und Serien.")
                !loading && results.values.all { it.isEmpty() } -> ErrorBox("Keine Treffer fuer „$applied“")
                else -> LazyColumn(contentPadding = PaddingValues(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    listOf(ContentType.MOVIE to "Filme", ContentType.SERIES to "Serien", ContentType.LIVE to "Live TV").forEach { (t, label) ->
                        val list = results[t].orEmpty()
                        if (list.isEmpty()) return@forEach
                        item(key = "h_$t") {
                            Text("$label (${list.size}${if (list.size == 100) "+" else ""})", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                        }
                        if (t == ContentType.LIVE) {
                            items(list.take(30), key = { "l_" + it.key }) { ch ->
                                ChannelRow(ch, favorites.any { it.key == ch.key }, { container.favorites.toggle(ch) }) { open(ch, list) }
                            }
                        } else {
                            item(key = "r_$t") {
                                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    items(list, key = { it.key }) { item ->
                                        PosterCard(
                                            item.name, item.logo, onClick = { open(item, list) },
                                            modifier = Modifier.width(120.dp),
                                            subtitle = listOfNotNull(item.year?.toString(), item.ratingValue?.let { "★ %.1f".format(it) }).joinToString("  "),
                                            watched = item.type == ContentType.MOVIE && container.source?.let { container.resume.isWatched(it.streamUrl(item)) } == true,
                                            progress = if (item.type == ContentType.MOVIE) container.source?.let { container.resume.progress(it.streamUrl(item)) } else null,
                                            favorite = favorites.any { it.key == item.key },
                                            onLongClick = { actionsFor = item },
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

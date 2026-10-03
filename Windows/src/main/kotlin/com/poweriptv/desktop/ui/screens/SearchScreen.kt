package com.poweriptv.desktop.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import com.poweriptv.app.data.ContentItem
import com.poweriptv.app.data.ContentType
import com.poweriptv.desktop.AppState
import com.poweriptv.desktop.Screen
import com.poweriptv.desktop.ui.ChannelCard
import com.poweriptv.desktop.ui.MenuAction
import com.poweriptv.desktop.ui.PosterCard
import com.poweriptv.desktop.ui.typeLabel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

@Composable
fun SearchScreen(app: AppState) {
    val src = app.source ?: return
    val lib = app.library ?: return
    val favorites by lib.favorites.collectAsState()
    var query by rememberSaveable { mutableStateOf("") }
    var all by remember(app.dataVersion) { mutableStateOf<Map<ContentType, List<ContentItem>>?>(null) }
    var results by remember { mutableStateOf<Map<ContentType, List<ContentItem>>>(emptyMap()) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    LaunchedEffect(app.dataVersion) {
        all = withContext(Dispatchers.IO) {
            ContentType.entries.associateWith { t -> runCatching { src.items(t, null) }.getOrDefault(emptyList()) }
        }
    }
    LaunchedEffect(query, all) {
        val q = query.trim()
        val data = all ?: return@LaunchedEffect
        if (q.length < 2) { results = emptyMap(); return@LaunchedEffect }
        delay(200)
        results = withContext(Dispatchers.Default) {
            data.mapValues { (_, list) ->
                list.filter { it.name.contains(q, ignoreCase = true) }
                    .sortedBy { if (it.name.startsWith(q, true)) 0 else 1 }
                    .take(120)
            }
        }
    }
    val favKeys = remember(favorites) { favorites.map { it.key }.toSet() }

    Column(Modifier.fillMaxSize().padding(28.dp)) {
        Text("Suche", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(14.dp))
        SearchField(query, { query = it }, "Sender, Filme oder Serien suchen …", Modifier.focusRequester(focus))
        if (all == null) LinearProgressIndicator(Modifier.padding(top = 8.dp))
        Spacer(Modifier.height(18.dp))
        if (query.trim().length >= 2 && results.values.all { it.isEmpty() } && all != null) EmptyHint("Nichts gefunden für „${query.trim()}“")
        LazyVerticalGrid(
            GridCells.Adaptive(160.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            for (type in listOf(ContentType.LIVE, ContentType.MOVIE, ContentType.SERIES)) {
                val list = results[type].orEmpty()
                if (list.isEmpty()) continue
                item(span = { GridItemSpan(maxLineSpan) }, key = "h_$type") {
                    Text("${typeLabel(type)} (${list.size})", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 8.dp))
                }
                if (type == ContentType.LIVE) {
                    items(list, key = { it.key }, span = { GridItemSpan(2) }) { item ->
                        ChannelCard(
                            item, onClick = { app.play(item, channels = list) }, favorite = item.key in favKeys,
                            menu = listOf(MenuAction(if (item.key in favKeys) "Aus Favoriten entfernen" else "Zu Favoriten") { lib.toggleFavorite(item) }),
                        )
                    }
                } else {
                    items(list, key = { it.key }) { item ->
                        PosterCard(
                            item, onClick = { app.navigate(Screen.Detail(item)) }, favorite = item.key in favKeys,
                            menu = listOf(MenuAction(if (item.key in favKeys) "Aus Favoriten entfernen" else "Zu Favoriten") { lib.toggleFavorite(item) }),
                        )
                    }
                }
            }
        }
    }
}

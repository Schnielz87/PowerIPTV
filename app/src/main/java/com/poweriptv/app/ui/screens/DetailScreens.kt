package com.poweriptv.app.ui.screens

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.poweriptv.app.AppContainer
import com.poweriptv.app.PlayEntry
import com.poweriptv.app.data.ContentItem
import com.poweriptv.app.data.MovieInfo
import com.poweriptv.app.data.SeriesInfo
import com.poweriptv.app.ui.components.AddToListButton
import com.poweriptv.app.ui.components.DownloadButton
import com.poweriptv.app.ui.components.ErrorBox
import com.poweriptv.app.ui.components.LoadingBox
import com.poweriptv.app.ui.components.PowerTopBar
import com.poweriptv.app.ui.components.startPlayback
import com.poweriptv.app.ui.components.tvFocus

@Composable
private fun FavoriteButton(container: AppContainer, item: ContentItem) {
    val favorites by container.favorites.favorites.collectAsState()
    val fav = favorites.any { it.key == item.key }
    IconButton(modifier = Modifier.tvFocus(CircleShape, 1.15f), onClick = { container.favorites.toggle(item) }) {
        Icon(
            if (fav) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder, "Favorit",
            tint = if (fav) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun Header(title: String, cover: String?, backdrop: String?, lines: List<String>, extra: @Composable () -> Unit) {
    Box(Modifier.fillMaxWidth()) {
        if (!backdrop.isNullOrBlank()) {
            AsyncImage(
                backdrop, null, contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxWidth().height(220.dp),
                alpha = 0.35f,
            )
        }
        Row(Modifier.padding(16.dp)) {
            Box(
                Modifier.width(130.dp).aspectRatio(2f / 3f).clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                if (!cover.isNullOrBlank()) AsyncImage(cover, title, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            }
            Spacer(Modifier.width(16.dp))
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                lines.forEach { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                Spacer(Modifier.height(8.dp))
                extra()
            }
        }
    }
}

@Composable
fun MovieDetailScreen(container: AppContainer, onBack: () -> Unit) {
    val item = container.selectedItem
    val source = container.source
    if (item == null || source == null) { ErrorBox("Nichts ausgewaehlt"); return }
    val context = LocalContext.current
    var info by remember { mutableStateOf<MovieInfo?>(null) }
    var loading by remember { mutableStateOf(true) }
    LaunchedEffect(item.key) {
        info = runCatching { source.movieInfo(item) }.getOrNull()
        loading = false
    }

    Scaffold(
        topBar = { PowerTopBar(item.name, onBack = onBack, actions = { FavoriteButton(container, item) }) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        if (loading) { LoadingBox(Modifier.padding(padding)); return@Scaffold }
        LazyColumn(Modifier.fillMaxSize().padding(padding)) {
            item {
                val i = info
                Header(
                    title = item.name,
                    cover = i?.cover ?: item.logo,
                    backdrop = i?.backdrop,
                    lines = listOfNotNull(
                        i?.genre?.let { "Genre: $it" },
                        i?.releaseDate?.let { "Erscheinungsdatum: $it" },
                        i?.duration?.let { "Laufzeit: $it" },
                        (i?.rating ?: item.rating)?.let { "Bewertung: $it" },
                        i?.director?.let { "Regie: $it" },
                    ),
                ) {
                    val playFocus = remember { FocusRequester() }
                    LaunchedEffect(Unit) { runCatching { playFocus.requestFocus() } }
                    Button(modifier = Modifier.focusRequester(playFocus).tvFocus(RoundedCornerShape(50), 1.08f), onClick = {
                        val playable = item.copy(containerExtension = i?.containerExtension ?: item.containerExtension)
                        startPlayback(
                            context, container,
                            listOf(PlayEntry(item.name, source.streamUrl(playable), playable, live = false)), 0,
                        )
                    }) {
                        Icon(Icons.Filled.PlayArrow, null); Spacer(Modifier.width(6.dp)); Text("Abspielen")
                    }
                    val playable = item.copy(containerExtension = i?.containerExtension ?: item.containerExtension)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        DownloadButton(container, item.name, source.streamUrl(playable), playable.containerExtension, i?.cover ?: item.logo)
                        Text("Offline", style = MaterialTheme.typography.bodySmall)
                        Spacer(Modifier.width(8.dp))
                        AddToListButton(container, item)
                        Text("Liste", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            item {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    info?.plot?.let { Text(it) }
                    info?.cast?.let { Text("Besetzung: $it", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
            }
        }
    }
}

@Composable
fun SeriesDetailScreen(container: AppContainer, onBack: () -> Unit) {
    val item = container.selectedItem
    val source = container.source
    if (item == null || source == null) { ErrorBox("Nichts ausgewaehlt"); return }
    val context = LocalContext.current
    var info by remember { mutableStateOf<SeriesInfo?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(true) }
    var season by rememberSaveable { mutableStateOf<Int?>(null) }
    LaunchedEffect(item.key) {
        runCatching { source.seriesInfo(item) }
            .onSuccess { info = it; season = season ?: it?.episodes?.keys?.firstOrNull() }
            .onFailure { error = it.message }
        loading = false
    }

    Scaffold(
        topBar = { PowerTopBar(item.name, onBack = onBack, actions = { FavoriteButton(container, item) }) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        when {
            loading -> LoadingBox(Modifier.padding(padding))
            info == null -> ErrorBox(error ?: "Keine Informationen verfuegbar", modifier = Modifier.padding(padding))
            else -> {
                val i = info!!
                val episodes = i.episodes[season].orEmpty()
                LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(bottom = 24.dp)) {
                    item {
                        Header(
                            title = item.name,
                            cover = i.cover,
                            backdrop = i.backdrop,
                            lines = listOfNotNull(
                                i.genre?.let { "Genre: $it" },
                                i.releaseDate?.let { "Erscheinungsdatum: $it" },
                                i.rating?.let { "Bewertung: $it" },
                            ),
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                AddToListButton(container, item)
                                Text("Zu Liste hinzufuegen", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                    i.plot?.let { plot -> item { Text(plot, Modifier.padding(horizontal = 16.dp)) } }
                    item {
                        LazyRow(
                            contentPadding = PaddingValues(16.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            items(i.episodes.keys.toList()) { s ->
                                FilterChip(modifier = Modifier.tvFocus(RoundedCornerShape(8.dp), 1.06f), selected = s == season, onClick = { season = s }, label = { Text("Staffel $s") })
                            }
                        }
                    }
                    itemsIndexed(episodes) { index, ep ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 4.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .tvFocus(RoundedCornerShape(10.dp), 1.02f)
                                .background(MaterialTheme.colorScheme.surface)
                                .clickable {
                                    val entries = episodes.map {
                                        PlayEntry("${item.name} – S${it.season}E${it.episodeNum} ${it.title}", source.episodeUrl(it), item, live = false)
                                    }
                                    container.history.add(item) // Serie im Verlauf "Zuletzt gesehen: Serien"
                                    startPlayback(context, container, entries, index)
                                }
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                Modifier.width(120.dp).aspectRatio(16f / 9f).clip(RoundedCornerShape(8.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(Icons.Filled.PlayArrow, null)
                                if (!ep.image.isNullOrBlank()) AsyncImage(ep.image, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text("${ep.episodeNum}. ${ep.title}", maxLines = 2, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                                ep.duration?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                                ep.plot?.let { Text(it, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                            }
                            DownloadButton(
                                container,
                                "${item.name} – S${ep.season}E${ep.episodeNum} ${ep.title}",
                                source.episodeUrl(ep),
                                ep.containerExtension,
                                ep.image ?: i.cover,
                            )
                        }
                    }
                }
            }
        }
    }
}

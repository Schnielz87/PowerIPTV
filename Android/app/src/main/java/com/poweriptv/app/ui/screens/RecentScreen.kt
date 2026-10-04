package com.poweriptv.app.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
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
import com.poweriptv.app.data.ContentItem
import com.poweriptv.app.data.ContentType
import com.poweriptv.app.ui.components.ErrorBox
import com.poweriptv.app.ui.components.PowerTopBar
import com.poweriptv.app.ui.components.startPlayback
import com.poweriptv.app.ui.components.tvFocus
import com.poweriptv.app.ui.theme.BrandCyan

/**
 * "Zuletzt gesehen" als eigene, uebersichtliche Seite:
 * Reiter Live TV / Filme / Serien, grosses Raster, Fortschrittsbalken bei angefangenen Filmen.
 * Lange druecken = aus dem Verlauf entfernen.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun RecentScreen(
    container: AppContainer,
    initialType: ContentType,
    onBack: () -> Unit,
    onOpenDetail: (ContentItem) -> Unit,
) {
    val source = container.source
    val context = LocalContext.current
    val historyAll by container.history.items.collectAsState()
    val parentalUnlocked by container.parental.sessionUnlocked.collectAsState()
    val history = remember(historyAll, parentalUnlocked) { container.parental.visible(container.source?.profile?.id, historyAll) }
    val types = listOf(ContentType.LIVE to "Live TV", ContentType.MOVIE to "Filme", ContentType.SERIES to "Serien")
    var tab by rememberSaveable { mutableStateOf(types.indexOfFirst { it.first == initialType }.coerceAtLeast(0)) }
    var confirmClear by remember { mutableStateOf(false) }
    var removeItem by remember { mutableStateOf<ContentItem?>(null) }
    val type = types[tab].first
    val list = history.filter { it.type == type }

    Scaffold(
        topBar = {
            PowerTopBar("Zuletzt gesehen", onBack = onBack, actions = {
                if (list.isNotEmpty()) IconButton(onClick = { confirmClear = true }, modifier = Modifier.tvFocus()) {
                    Icon(Icons.Filled.DeleteSweep, "Verlauf leeren")
                }
            })
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            TabRow(selectedTabIndex = tab, containerColor = MaterialTheme.colorScheme.background) {
                types.forEachIndexed { i, (t, label) ->
                    val n = history.count { it.type == t }
                    Tab(
                        selected = tab == i, onClick = { tab = i },
                        modifier = Modifier.tvFocus(RoundedCornerShape(8.dp)),
                        text = { Text("$label ($n)", fontWeight = if (tab == i) FontWeight.Bold else FontWeight.Normal) },
                    )
                }
            }
            if (source == null || list.isEmpty()) {
                ErrorBox("Noch nichts gesehen in \"${types[tab].second}\".")
                return@Column
            }
            val poster = type != ContentType.LIVE
            LazyVerticalGrid(
                columns = GridCells.Adaptive(if (poster) 140.dp else 180.dp),
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(list, key = { it.key }) { item ->
                    val progress = if (type == ContentType.MOVIE) container.resume.progress(source.streamUrl(item)) else null
                    Column(
                        Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .tvFocus(RoundedCornerShape(10.dp))
                            .combinedClickable(
                                onClick = {
                                    if (item.type == ContentType.LIVE || !source.supportsDetails) {
                                        val entries = list.map { PlayEntry(it.name, source.streamUrl(it), it, it.type == ContentType.LIVE) }
                                        startPlayback(context, container, entries, list.indexOf(item))
                                    } else onOpenDetail(item)
                                },
                                onLongClick = { removeItem = item },
                            )
                            .background(MaterialTheme.colorScheme.surface)
                            .padding(8.dp),
                    ) {
                        Box(
                            Modifier.fillMaxWidth().aspectRatio(if (poster) 2f / 3f else 16f / 9f)
                                .clip(RoundedCornerShape(6.dp)).background(MaterialTheme.colorScheme.surfaceVariant),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (!item.logo.isNullOrBlank()) AsyncImage(
                                item.logo, item.name,
                                contentScale = if (poster) ContentScale.Crop else ContentScale.Fit,
                                modifier = Modifier.fillMaxSize().padding(if (poster) 0.dp else 10.dp),
                            )
                        }
                        // Fortschritt bei angefangenen Filmen
                        if (progress != null) {
                            LinearProgressIndicator(
                                progress = { progress },
                                modifier = Modifier.fillMaxWidth().padding(top = 6.dp).height(4.dp).clip(RoundedCornerShape(2.dp)),
                                color = BrandCyan,
                                trackColor = MaterialTheme.colorScheme.surfaceVariant,
                            )
                        }
                        Text(
                            item.name, maxLines = 2, overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(top = 6.dp),
                        )
                        val sub = listOfNotNull(
                            item.year?.toString(),
                            item.rating?.takeIf { it.isNotBlank() && it != "0" }?.let { "★ $it" },
                            progress?.let { "${(it * 100).toInt()} % gesehen" },
                        ).joinToString("  ·  ")
                        if (sub.isNotEmpty()) Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                    }
                }
            }
        }
    }

    if (confirmClear) AlertDialog(
        onDismissRequest = { confirmClear = false },
        title = { Text("Verlauf leeren?") },
        text = { Text("Alle Eintraege unter \"${types[tab].second}\" werden aus \"Zuletzt gesehen\" entfernt.") },
        confirmButton = { TextButton(modifier = Modifier.tvFocus(RoundedCornerShape(50)), onClick = { container.history.clearType(type); confirmClear = false }) { Text("Leeren") } },
        dismissButton = { TextButton(modifier = Modifier.tvFocus(RoundedCornerShape(50)), onClick = { confirmClear = false }) { Text("Abbrechen") } },
    )
    removeItem?.let { item ->
        com.poweriptv.app.ui.components.ItemActionsDialog(
            container, item,
            onDismiss = { removeItem = null },
            removeLabel = "Aus „Zuletzt gesehen“ entfernen",
            onRemove = { container.history.remove(item.key) },
        )
    }
}

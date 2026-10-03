package com.poweriptv.desktop.ui.screens

import androidx.compose.foundation.ContextMenuArea
import androidx.compose.foundation.ContextMenuItem
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.poweriptv.app.data.ContentItem
import com.poweriptv.app.data.ContentType
import com.poweriptv.app.data.Episode
import com.poweriptv.app.data.MovieInfo
import com.poweriptv.app.data.SeriesInfo
import com.poweriptv.desktop.AppState
import com.poweriptv.desktop.data.LibraryStore
import com.poweriptv.desktop.ui.Background
import com.poweriptv.desktop.ui.BrandCyan
import com.poweriptv.desktop.ui.Danger
import com.poweriptv.desktop.ui.NetImage
import com.poweriptv.desktop.ui.Pill
import com.poweriptv.desktop.ui.Success
import com.poweriptv.desktop.ui.SurfaceHigh
import com.poweriptv.desktop.ui.Warning
import com.poweriptv.desktop.ui.WatchedBadge
import com.poweriptv.desktop.ui.formatTime
import com.poweriptv.desktop.ui.handCursor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun DetailScreen(app: AppState, item: ContentItem) {
    val src = app.source ?: return
    val lib = app.library ?: return
    val favorites by lib.favorites.collectAsState()
    val watched by lib.watched.collectAsState()
    val history by lib.history.collectAsState()
    var movie by remember(item.key) { mutableStateOf<MovieInfo?>(null) }
    var series by remember(item.key) { mutableStateOf<SeriesInfo?>(null) }
    var loading by remember(item.key) { mutableStateOf(true) }
    LaunchedEffect(item.key) {
        loading = true
        withContext(Dispatchers.IO) {
            runCatching {
                if (item.type == ContentType.MOVIE) movie = src.movieInfo(item)
                else if (item.type == ContentType.SERIES) series = src.seriesInfo(item)
            }
        }
        loading = false
    }
    val isFav = favorites.any { it.key == item.key }
    val backdrop = movie?.backdrop ?: series?.backdrop
    val cover = movie?.cover ?: series?.cover ?: item.logo
    val plot = movie?.plot ?: series?.plot
    val genre = movie?.genre ?: series?.genre ?: item.genre
    val rating = (movie?.rating ?: series?.rating ?: item.rating)?.replace(',', '.')?.toDoubleOrNull()?.takeIf { it > 0 }
    val year = item.year ?: (movie?.releaseDate ?: series?.releaseDate)?.take(4)?.toIntOrNull()
    val age = movie?.age ?: series?.age
    // FSK wie Android: mit TMDB-Schluessel offizielle deutsche Freigabe, sonst Angabe des Anbieters
    var ageRating by remember(item.key) { mutableStateOf<com.poweriptv.app.data.AgeRating?>(null) }
    LaunchedEffect(item.key, movie, series) {
        if (movie != null || series != null) ageRating = runCatching {
            app.ageRatings.resolve(item.type == ContentType.SERIES, item.name, movie?.releaseDate ?: series?.releaseDate, movie?.tmdbId ?: series?.tmdbId, age)
        }.getOrNull()
    }
    val downloads by app.downloads.entries.collectAsState()

    Box(Modifier.fillMaxSize()) {
        // Hintergrundbild
        NetImage(listOf(backdrop, cover), Modifier.fillMaxWidth().height(520.dp), alignment = Alignment.TopCenter)
        Box(
            Modifier.fillMaxWidth().height(520.dp).background(
                Brush.verticalGradient(0f to Background.copy(alpha = 0.35f), 0.55f to Background.copy(alpha = 0.75f), 1f to Background),
            ),
        )
        Box(Modifier.fillMaxWidth().height(520.dp).background(Brush.horizontalGradient(listOf(Background.copy(alpha = 0.9f), Color.Transparent))))

        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(28.dp)) {
            IconButton(onClick = { app.back() }, modifier = Modifier.handCursor().background(Color.Black.copy(alpha = 0.4f), RoundedCornerShape(50))) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Zurück")
            }
            Spacer(Modifier.height(24.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(28.dp)) {
                NetImage(
                    listOf(cover), Modifier.width(230.dp).aspectRatio(2f / 3f).clip(RoundedCornerShape(14.dp)).background(SurfaceHigh),
                )
                Column(Modifier.weight(1f).widthIn(max = 900.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(item.name, style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        year?.let { Pill(it.toString()) }
                        rating?.let {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.Star, null, tint = Warning, modifier = Modifier.size(18.dp))
                                Text(" %.1f".format(it), fontWeight = FontWeight.SemiBold)
                            }
                        }
                        (ageRating?.let { r -> r.fsk?.let { "FSK $it" to fskColor(it) } ?: (r.label to Color(0xFF555E70)) }
                            ?: fskOf(age)?.let { "FSK $it" to fskColor(it) })?.let { (label, color) -> Pill(label, color, Color.White) }
                        movie?.duration?.let { Pill(it) }
                        if (item.key in watched) WatchedBadge()
                    }
                    genre?.let { Text(it, color = BrandCyan) }
                    plot?.let { Text(it, style = MaterialTheme.typography.bodyLarge, lineHeight = 24.sp) }
                    movie?.director?.let { Text("Regie: $it", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    (movie?.cast ?: series?.cast)?.let { Text("Besetzung: $it", color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 3, overflow = TextOverflow.Ellipsis) }
                    if (loading) LinearProgressIndicator(Modifier.width(200.dp))
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (item.type == ContentType.MOVIE) {
                            val pos = lib.position(item.key)
                            if (pos > 0) {
                                Button(onClick = { app.play(item, startAt = pos) }, modifier = Modifier.handCursor()) {
                                    Icon(Icons.Filled.PlayArrow, null); Spacer(Modifier.width(6.dp)); Text("Weiter ab ${formatTime(pos)}")
                                }
                                OutlinedButton(onClick = { app.play(item, startAt = 0L) }, modifier = Modifier.handCursor()) {
                                    Icon(Icons.Filled.Replay, null); Spacer(Modifier.width(6.dp)); Text("Von vorne")
                                }
                            } else {
                                Button(onClick = { app.play(item, startAt = 0L) }, modifier = Modifier.handCursor()) {
                                    Icon(Icons.Filled.PlayArrow, null); Spacer(Modifier.width(6.dp)); Text("Abspielen")
                                }
                            }
                        } else if (item.type == ContentType.SERIES) {
                            val last = history.firstOrNull { it.item.key == item.key && it.episodeId != null }
                            val eps = series?.episodes?.values?.flatten().orEmpty()
                            val lastEp = last?.let { l -> eps.firstOrNull { it.id == l.episodeId } }
                            if (lastEp != null) {
                                Button(onClick = { app.playEpisode(item, lastEp, eps, cover) }, modifier = Modifier.handCursor()) {
                                    Icon(Icons.Filled.PlayArrow, null); Spacer(Modifier.width(6.dp))
                                    Text("Weiterschauen: S${lastEp.season} E${lastEp.episodeNum}")
                                }
                            } else if (eps.isNotEmpty()) {
                                Button(onClick = { app.playEpisode(item, eps.first(), eps, cover, 0L) }, modifier = Modifier.handCursor()) {
                                    Icon(Icons.Filled.PlayArrow, null); Spacer(Modifier.width(6.dp)); Text("Erste Folge abspielen")
                                }
                            }
                        }
                        if (item.type == ContentType.MOVIE) {
                            val url = item.url ?: runCatching { app.source?.streamUrl(item.copy(containerExtension = movie?.containerExtension ?: item.containerExtension)) }.getOrNull()
                            val dl = downloads.firstOrNull { it.url == url }
                            OutlinedButton(
                                enabled = url != null && dl?.status != com.poweriptv.app.download.DownloadStatus.COMPLETED,
                                onClick = { url?.let { app.downloads.enqueue(item.name, it, movie?.containerExtension ?: item.containerExtension, cover) } },
                                modifier = Modifier.handCursor(),
                            ) {
                                Icon(Icons.Filled.Download, null); Spacer(Modifier.width(6.dp))
                                Text(
                                    when (dl?.status) {
                                        null -> "Herunterladen"
                                        com.poweriptv.app.download.DownloadStatus.COMPLETED -> "Offline verfügbar"
                                        com.poweriptv.app.download.DownloadStatus.FAILED, com.poweriptv.app.download.DownloadStatus.PAUSED -> "Download fortsetzen"
                                        else -> "Lädt … ${(dl.progress * 100).toInt()} %"
                                    },
                                )
                            }
                        }
                        OutlinedButton(onClick = { app.listPickerFor = item }, modifier = Modifier.handCursor()) {
                            Icon(Icons.AutoMirrored.Filled.PlaylistAdd, null); Spacer(Modifier.width(6.dp)); Text("Liste")
                        }
                        OutlinedButton(onClick = { com.poweriptv.desktop.ui.shareWhatsApp(item) }, modifier = Modifier.handCursor()) {
                            Icon(Icons.Filled.Share, null); Spacer(Modifier.width(6.dp)); Text("Teilen")
                        }
                        OutlinedButton(onClick = { lib.toggleFavorite(item) }, modifier = Modifier.handCursor()) {
                            Icon(if (isFav) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder, null, tint = if (isFav) Danger else Color.White)
                            Spacer(Modifier.width(6.dp)); Text(if (isFav) "Favorit" else "Zu Favoriten")
                        }
                        if (item.type == ContentType.MOVIE) {
                            val w = item.key in watched
                            OutlinedButton(onClick = { lib.markWatched(item.key, !w) }, modifier = Modifier.handCursor()) {
                                Icon(Icons.Filled.CheckCircle, null, tint = if (w) Success else Color.White)
                                Spacer(Modifier.width(6.dp)); Text(if (w) "Gesehen" else "Als gesehen markieren")
                            }
                        }
                    }
                }
            }
            series?.let { s -> Spacer(Modifier.height(30.dp)); EpisodeSection(app, item, s, cover) }
        }
    }
}

@Composable
private fun EpisodeSection(app: AppState, item: ContentItem, s: SeriesInfo, cover: String?) {
    val lib = app.library ?: return
    val watched by lib.watched.collectAsState()
    val history by lib.history.collectAsState()
    val seasons = s.episodes.keys.toList()
    if (seasons.isEmpty()) {
        Text("Keine Episoden gefunden", color = MaterialTheme.colorScheme.onSurfaceVariant); return
    }
    val all = remember(s) { s.episodes.values.flatten() }
    val lastSeason = history.firstOrNull { it.item.key == item.key }?.season
    var season by remember(item.key) { mutableStateOf(lastSeason?.takeIf { it in seasons } ?: seasons.first()) }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        seasons.forEach { nr ->
            FilterChip(selected = nr == season, onClick = { season = nr }, label = { Text("Staffel $nr") }, modifier = Modifier.handCursor())
        }
    }
    Spacer(Modifier.height(14.dp))
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        s.episodes[season].orEmpty().forEach { ep ->
            val key = LibraryStore.episodeKey(ep.id)
            val pos = lib.position(key)
            val dur = history.firstOrNull { it.episodeId == ep.id }?.duration ?: 0L
            EpisodeRow(
                ep, watched = key in watched, progress = if (pos > 0 && dur > 0) pos.toFloat() / dur else 0f,
                onClick = { app.playEpisode(item, ep, all, cover) },
                onToggleWatched = { lib.markWatched(key, key !in watched) },
                onFromStart = { app.playEpisode(item, ep, all, cover, 0L) },
                onDownload = {
                    val src = app.source
                    if (src != null) app.downloads.enqueue("${item.name} – S${ep.season}E${ep.episodeNum} ${ep.title}", ep.directUrl ?: src.episodeUrl(ep), ep.containerExtension, cover)
                },
            )
        }
    }
}

@Composable
private fun EpisodeRow(ep: Episode, watched: Boolean, progress: Float, onClick: () -> Unit, onToggleWatched: () -> Unit, onFromStart: () -> Unit, onDownload: () -> Unit) {
    val hover = remember { MutableInteractionSource() }
    val hovered by hover.collectIsHoveredAsState()
    ContextMenuArea(items = {
        listOf(
            ContextMenuItem("Abspielen", onClick),
            ContextMenuItem("Von vorne abspielen", onFromStart),
            ContextMenuItem("Herunterladen (offline)", onDownload),
            ContextMenuItem(if (watched) "Als nicht gesehen markieren" else "Als gesehen markieren", onToggleWatched),
        )
    }) {
        Row(
            Modifier.fillMaxWidth().widthIn(max = 1100.dp).clip(RoundedCornerShape(12.dp))
                .background(if (hovered) SurfaceHigh else Color(0xFF0E1726))
                .then(if (hovered) Modifier.border(1.5.dp, BrandCyan, RoundedCornerShape(12.dp)) else Modifier)
                .hoverable(hover).handCursor().clickable(onClick = onClick).padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.width(200.dp).aspectRatio(16f / 9f).clip(RoundedCornerShape(8.dp)).background(Color(0xFF0A1220))) {
                NetImage(ep.imageCandidates, Modifier.fillMaxSize())
                Icon(
                    Icons.Filled.PlayArrow, null, tint = Color.White,
                    modifier = Modifier.align(Alignment.Center).size(42.dp).background(Color.Black.copy(alpha = 0.45f), RoundedCornerShape(50)).padding(6.dp),
                )
                if (progress > 0f && !watched) {
                    LinearProgressIndicator(
                        progress = { progress.coerceIn(0f, 1f) },
                        modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(4.dp),
                        color = BrandCyan, trackColor = Color.Black.copy(alpha = 0.5f),
                    )
                }
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("${ep.episodeNum}. ${ep.title}", style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, false))
                    if (watched) { Spacer(Modifier.width(10.dp)); WatchedBadge() }
                }
                ep.duration?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                ep.plot?.let { Text(it, maxLines = 3, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.85f)) }
            }
            IconButton(onClick = onDownload, modifier = Modifier.handCursor()) { Icon(Icons.Filled.Download, "Herunterladen") }
        }
    }
}

/** Altersfreigabe aus Anbieter-Angaben ("16", "FSK 12", "PG-13", "R" ...). */
fun fskOf(age: String?): Int? {
    val a = age?.trim()?.uppercase() ?: return null
    Regex("""\b(0|6|12|16|18)\b""").find(a)?.let { return it.value.toInt() }
    return when {
        a == "G" -> 0
        a == "PG" -> 6
        a == "PG-13" -> 12
        a == "R" -> 16
        a == "NC-17" -> 18
        else -> null
    }
}

fun fskColor(fsk: Int): Color = when (fsk) {
    0 -> Color(0xFFB0B0B0)
    6 -> Color(0xFFE6B800)
    12 -> Color(0xFF2E9E4F)
    16 -> Color(0xFF2F7FD6)
    else -> Color(0xFFD9302F)
}

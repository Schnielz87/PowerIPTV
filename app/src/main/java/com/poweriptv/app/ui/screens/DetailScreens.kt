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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.ui.graphics.Color
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
import com.poweriptv.app.ui.components.FallbackImage
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

/** Tablet & Fernseher: Filmbild als Vollbild-Hintergrund der ganzen Seite. Handy: Bild-Streifen im Kopf wie bisher. */
@Composable
private fun largeDetail(): Boolean =
    androidx.compose.ui.platform.LocalConfiguration.current.smallestScreenWidthDp >= 600 || com.poweriptv.app.util.LocalIsTv.current

/** Vollbild-Hintergrund (Tablet/TV): Bild rechts oben, nach links und unten weich in den Hintergrund auslaufend. */
@Composable
private fun DetailBackdrop(backdrop: String?) {
    if (backdrop.isNullOrBlank()) return
    val bg = MaterialTheme.colorScheme.background
    val clear = androidx.compose.ui.graphics.Color.Transparent
    val context = LocalContext.current
    // Helligkeit des Bildes (0 = schwarz, 1 = weiss): helle Bilder werden staerker abgedunkelt,
    // damit Titel und Beschreibung immer gut lesbar bleiben
    var brightness by remember(backdrop) { mutableStateOf(0.5f) }
    val request = remember(backdrop) {
        coil.request.ImageRequest.Builder(context).data(backdrop).allowHardware(false).build()
    }
    val extra = ((brightness - 0.25f) * 1.1f).coerceIn(0f, 0.55f)
    Box(Modifier.fillMaxSize()) {
        AsyncImage(
            request, null, contentScale = ContentScale.Crop, alignment = Alignment.TopEnd,
            modifier = Modifier.fillMaxSize(), alpha = 0.7f,
            onSuccess = { st -> brightness = averageBrightness(st.result.drawable) },
        )
        // Gleichmaessige Abdunklung je nach Bildhelligkeit
        Box(Modifier.fillMaxSize().background(bg.copy(alpha = extra)))
        Box(
            Modifier.fillMaxSize().background(
                androidx.compose.ui.graphics.Brush.horizontalGradient(
                    0f to bg.copy(alpha = 0.95f), 0.35f to bg.copy(alpha = 0.75f), 0.7f to bg.copy(alpha = 0.25f), 1f to clear,
                ),
            ),
        )
        Box(
            Modifier.fillMaxSize().background(
                androidx.compose.ui.graphics.Brush.verticalGradient(
                    0f to bg.copy(alpha = 0.35f), 0.25f to clear, 0.5f to bg.copy(alpha = 0.45f), 0.8f to bg.copy(alpha = 0.9f), 1f to bg,
                ),
            ),
        )
    }
}

/** Mittlere Helligkeit eines Bildes (verkleinert auf 24x24 Pixel). */
private fun averageBrightness(d: android.graphics.drawable.Drawable): Float = runCatching {
    val bmp = (d as? android.graphics.drawable.BitmapDrawable)?.bitmap ?: return 0.5f
    val small = android.graphics.Bitmap.createScaledBitmap(bmp, 24, 24, true)
    var sum = 0f
    for (x in 0 until 24) for (y in 0 until 24) {
        val c = small.getPixel(x, y)
        sum += (0.299f * android.graphics.Color.red(c) + 0.587f * android.graphics.Color.green(c) + 0.114f * android.graphics.Color.blue(c)) / 255f
    }
    if (small !== bmp) small.recycle()
    sum / (24 * 24)
}.getOrDefault(0.5f)

/** Text-Schatten fuer Schrift auf dem Hintergrundbild (Tablet/TV). */
private val readableShadow = androidx.compose.ui.graphics.Shadow(
    color = androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.85f),
    offset = androidx.compose.ui.geometry.Offset(0f, 2f),
    blurRadius = 8f,
)

/** Seitenrahmen der Detailseiten: auf Tablet/TV mit Vollbild-Hintergrund und transparenter Kopfzeile. */
@Composable
private fun DetailScaffold(
    title: String,
    backdrop: String?,
    onBack: () -> Unit,
    actions: @Composable () -> Unit,
    content: @Composable (PaddingValues) -> Unit,
) {
    val large = largeDetail()
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        if (large) DetailBackdrop(backdrop)
        Scaffold(
            topBar = { PowerTopBar(title, onBack = onBack, actions = actions, transparent = large) },
            containerColor = if (large) androidx.compose.ui.graphics.Color.Transparent else MaterialTheme.colorScheme.background,
            // Wichtig: bei transparentem Hintergrund sonst schwarze Standardschrift (auf Tablets unlesbar)
            contentColor = if (large) androidx.compose.ui.graphics.Color.White else MaterialTheme.colorScheme.onBackground,
        ) { padding ->
            if (large) {
                androidx.compose.runtime.CompositionLocalProvider(
                    androidx.compose.material3.LocalTextStyle provides androidx.compose.material3.LocalTextStyle.current.copy(shadow = readableShadow),
                ) { content(padding) }
            } else content(padding)
        }
    }
}

@Composable
private fun Header(title: String, cover: String?, backdrop: String?, lines: List<String>, ageRating: com.poweriptv.app.data.AgeRating? = null, extra: @Composable () -> Unit) {
    val large = largeDetail()
    Box(Modifier.fillMaxWidth()) {
        // Handy: Bild-Streifen hinter dem Kopf (Tablet/TV: Vollbild-Hintergrund der Seite)
        if (!large && !backdrop.isNullOrBlank()) {
            AsyncImage(
                backdrop, null, contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxWidth().height(220.dp),
                alpha = 0.35f,
            )
        }
        Row(
            if (large) Modifier.padding(start = 32.dp, end = 32.dp, top = 24.dp, bottom = 16.dp) else Modifier.padding(16.dp),
        ) {
            Box(
                Modifier.width(if (large) 170.dp else 130.dp).aspectRatio(2f / 3f).clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                if (!cover.isNullOrBlank()) AsyncImage(cover, title, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            }
            Spacer(Modifier.width(if (large) 28.dp else 16.dp))
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        title, style = if (large) MaterialTheme.typography.headlineMedium.copy(shadow = readableShadow) else MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f, fill = false),
                    )
                    ageRating?.let { Spacer(Modifier.width(12.dp)); FskBadge(it, if (large) 40.dp else 32.dp) }
                }
                lines.forEach {
                    Text(
                        it,
                        style = if (large) MaterialTheme.typography.bodyMedium.copy(shadow = readableShadow) else MaterialTheme.typography.bodySmall,
                        // Auf dem Bild heller (besser lesbar), auf dem Handy wie bisher
                        color = if (large) androidx.compose.ui.graphics.Color.White.copy(alpha = 0.88f) else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(8.dp))
                extra()
            }
        }
    }
}

/** FSK-Kennzeichen in den offiziellen Farben (0 weiss, 6 gelb, 12 gruen, 16 blau, 18 rot). */
@Composable
private fun FskBadge(r: com.poweriptv.app.data.AgeRating, size: androidx.compose.ui.unit.Dp) {
    val (bg, fg) = when (r.fsk) {
        0 -> androidx.compose.ui.graphics.Color.White to androidx.compose.ui.graphics.Color.Black
        6 -> androidx.compose.ui.graphics.Color(0xFFFFE500) to androidx.compose.ui.graphics.Color.Black
        12 -> androidx.compose.ui.graphics.Color(0xFF00A651) to androidx.compose.ui.graphics.Color.White
        16 -> androidx.compose.ui.graphics.Color(0xFF0095DA) to androidx.compose.ui.graphics.Color.White
        18 -> androidx.compose.ui.graphics.Color(0xFFE30613) to androidx.compose.ui.graphics.Color.White
        else -> androidx.compose.ui.graphics.Color(0xFF555B66) to androidx.compose.ui.graphics.Color.White
    }
    Box(
        Modifier.heightIn(min = size).widthIn(min = size).clip(RoundedCornerShape(6.dp)).background(bg).padding(horizontal = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (r.fsk != null) {
                Text("FSK", color = fg, fontSize = (size.value * 0.24f).sp, fontWeight = FontWeight.Bold, lineHeight = (size.value * 0.26f).sp)
                Text("${r.fsk}", color = fg, fontSize = (size.value * 0.46f).sp, fontWeight = FontWeight.Black, lineHeight = (size.value * 0.5f).sp)
            } else {
                Text(r.label, color = fg, fontSize = (size.value * 0.34f).sp, fontWeight = FontWeight.Bold)
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
    var ageRating by remember { mutableStateOf<com.poweriptv.app.data.AgeRating?>(null) }
    LaunchedEffect(item.key) {
        info = runCatching { source.movieInfo(item) }.getOrNull()
        loading = false
        val i = info
        ageRating = container.ageRatings.resolve(false, item.name, i?.releaseDate ?: item.year?.toString(), i?.tmdbId, i?.age)
    }

    DetailScaffold(item.name, info?.backdrop, onBack, actions = { FavoriteButton(container, item) }) { padding ->
        if (loading) { LoadingBox(Modifier.padding(padding)); return@DetailScaffold }
        LazyColumn(Modifier.fillMaxSize().padding(padding)) {
            item {
                val i = info
                Header(
                    title = item.name,
                    cover = i?.cover ?: item.logo,
                    backdrop = i?.backdrop,
                    ageRating = ageRating,
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
    var ageRating by remember { mutableStateOf<com.poweriptv.app.data.AgeRating?>(null) }
    LaunchedEffect(item.key) {
        runCatching { source.seriesInfo(item) }
            .onSuccess { si ->
                info = si
                // Staffel der zuletzt gesehenen Folge vorauswaehlen
                val lastUrl = container.resume.lastEpisode(item.key)?.first
                val lastSeason = si?.episodes?.values?.flatten()?.firstOrNull { source.episodeUrl(it) == lastUrl }?.season
                season = season ?: lastSeason ?: si?.episodes?.keys?.firstOrNull()
            }
            .onFailure { error = it.message }
        loading = false
        val i = info
        ageRating = container.ageRatings.resolve(true, item.name, i?.releaseDate ?: item.year?.toString(), i?.tmdbId, i?.age)
    }

    DetailScaffold(item.name, info?.backdrop, onBack, actions = { FavoriteButton(container, item) }) { padding ->
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
                            ageRating = ageRating,
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
                                FallbackImage(ep.imageCandidates, Modifier.fillMaxSize())
                                // kleines Play-Symbol ueber dem Vorschaubild
                                Box(
                                    Modifier.size(30.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.55f)),
                                    contentAlignment = Alignment.Center,
                                ) { Icon(Icons.Filled.PlayArrow, null, tint = Color.White, modifier = Modifier.size(20.dp)) }
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                val epUrl = source.episodeUrl(ep)
                                if (container.resume.lastEpisode(item.key)?.first == epUrl) {
                                    Text("▶ Zuletzt gesehen", color = com.poweriptv.app.ui.theme.BrandCyan, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                }
                                Text("${ep.episodeNum}. ${ep.title}", maxLines = 2, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                                container.resume.progress(epUrl)?.let { p ->
                                    androidx.compose.material3.LinearProgressIndicator(
                                        progress = { p },
                                        modifier = Modifier.fillMaxWidth(0.6f).padding(vertical = 3.dp).height(3.dp).clip(RoundedCornerShape(2.dp)),
                                        color = com.poweriptv.app.ui.theme.BrandCyan,
                                        trackColor = MaterialTheme.colorScheme.surfaceVariant,
                                    )
                                }
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

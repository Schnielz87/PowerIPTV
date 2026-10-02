package com.poweriptv.app.ui.components

import android.content.Context
import android.content.res.Configuration
import androidx.compose.ui.platform.LocalConfiguration
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.GppBad
import androidx.compose.material.icons.filled.GppGood
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.poweriptv.app.AppContainer
import com.poweriptv.app.PlayEntry
import com.poweriptv.app.player.PlayerActivity
import com.poweriptv.app.player.VlcPlayerActivity
import com.poweriptv.app.data.PlayerEngine
import com.poweriptv.app.ui.theme.Danger
import com.poweriptv.app.ui.theme.Success
import com.poweriptv.app.vpn.VpnState
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PowerTopBar(
    title: String,
    onBack: (() -> Unit)? = null,
    actions: @Composable () -> Unit = {},
) {
    TopAppBar(
        title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Bold) },
        navigationIcon = {
            if (onBack != null) IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zurueck")
            }
        },
        actions = { actions() },
        // Im Querformat flacher (48 dp statt 64 dp)
        expandedHeight = if (LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE) 48.dp
        else TopAppBarDefaults.TopAppBarExpandedHeight,
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
    )
}

/** Zeigt an, ob die App durch ein VPN geschuetzt ist. */
@Composable
fun VpnBadge(container: AppContainer, onClick: () -> Unit) {
    val state by container.vpn.state.collectAsState()
    var external by remember { mutableStateOf(container.vpn.isProtected()) }
    LaunchedEffect(state) {
        while (true) {
            external = container.vpn.isProtected()
            delay(3000)
        }
    }
    val protected = state == VpnState.CONNECTED || external
    val color = when {
        state == VpnState.CONNECTING -> MaterialTheme.colorScheme.onSurfaceVariant
        protected -> Success
        else -> Danger
    }
    Row(
        modifier = Modifier
            .padding(end = 8.dp)
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = 0.15f))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(if (protected) Icons.Filled.GppGood else Icons.Filled.GppBad, null, tint = color, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(
            when {
                state == VpnState.CONNECTING -> "Verbinde..."
                protected -> "VPN aktiv"
                else -> "Kein VPN"
            },
            color = color,
            style = MaterialTheme.typography.labelMedium,
        )
    }
}

@Composable
fun LoadingBox(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
}

@Composable
fun ErrorBox(message: String, onRetry: (() -> Unit)? = null, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(message, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (onRetry != null) {
            Spacer(Modifier.size(16.dp))
            Button(onClick = onRetry) { Text("Erneut versuchen") }
        }
    }
}

@Composable
fun PosterCard(title: String, image: String?, onClick: () -> Unit, modifier: Modifier = Modifier, subtitle: String = "") {
    Column(
        modifier
            .clip(RoundedCornerShape(10.dp))
            .tvFocus(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(4.dp)
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.Movie, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            if (!image.isNullOrBlank()) {
                AsyncImage(
                    model = image,
                    contentDescription = title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        Text(
            title,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 4.dp, start = 2.dp, end = 2.dp),
        )
        if (subtitle.isNotBlank()) {
            Text(
                subtitle,
                maxLines = 1,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 2.dp, end = 2.dp),
            )
        }
    }
}

/** Schluessel "Kategorie braucht VLC" (pro Zugang und Bereich). */
fun vlcCategoryKey(profileId: String?, item: com.poweriptv.app.data.ContentItem) =
    "${profileId ?: ""}|${item.type.name}|${item.categoryId}"

/** Startet den Player mit einer Wiedergabeliste. */
fun startPlayback(context: Context, container: AppContainer, entries: List<PlayEntry>, index: Int) {
    if (entries.isEmpty()) return
    container.playQueue = entries
    container.playIndex = index.coerceIn(0, entries.lastIndex)
    val url = entries[container.playIndex].url
    val useVlc = when (container.settings.playerEngineEnum()) {
        PlayerEngine.VLC -> true
        PlayerEngine.EXO -> false
        PlayerEngine.AUTO -> container.settings.needsVlc(url) ||
            entries[container.playIndex].let { e ->
                !e.live && e.item?.categoryId?.isNotBlank() == true &&
                    container.settings.categoryNeedsVlc(vlcCategoryKey(container.source?.profile?.id, e.item!!))
            }
    }
    context.startActivity(Intent(context, if (useVlc) VlcPlayerActivity::class.java else PlayerActivity::class.java))
}

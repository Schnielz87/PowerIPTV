package com.poweriptv.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DownloadForOffline
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.poweriptv.app.AppContainer
import com.poweriptv.app.data.AccountInfo
import com.poweriptv.app.data.ContentType
import com.poweriptv.app.ui.components.BrandTopBar
import com.poweriptv.app.ui.components.VpnBadge
import java.text.DateFormat
import java.util.Date

@Composable
fun HomeScreen(
    container: AppContainer,
    onOpen: (ContentType) -> Unit,
    onFavorites: () -> Unit,
    onSettings: () -> Unit,
    onVpn: () -> Unit,
    onDownloads: () -> Unit,
    onSwitchProfile: () -> Unit,
) {
    val source = container.source
    var account by remember { mutableStateOf<AccountInfo?>(null) }
    LaunchedEffect(source) { account = source?.accountInfo() }

    Scaffold(
        topBar = {
            BrandTopBar(subtitle = source?.profile?.name, actions = { VpnBadge(container, onVpn) })
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        BoxWithConstraints(Modifier.fillMaxSize().padding(padding)) {
            val wide = maxWidth > 600.dp
            val bigHeight: Dp = if (wide) 200.dp else 140.dp
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                val big = listOf(
                    Triple("LIVE TV", Icons.Filled.LiveTv, ContentType.LIVE) to listOf(Color(0xFF00C8FF), Color(0xFF1E6BFF)),
                    Triple("FILME", Icons.Filled.Movie, ContentType.MOVIE) to listOf(Color(0xFF1E6BFF), Color(0xFF6A3DFF)),
                    Triple("SERIEN", Icons.Filled.VideoLibrary, ContentType.SERIES) to listOf(Color(0xFF6A3DFF), Color(0xFFC13DFF)),
                )
                if (wide) {
                    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        big.forEach { (t, colors) ->
                            BigTile(t.first, t.second, colors, bigHeight, Modifier.weight(1f)) { onOpen(t.third) }
                        }
                    }
                } else {
                    big.forEach { (t, colors) ->
                        BigTile(t.first, t.second, colors, bigHeight, Modifier.fillMaxWidth()) { onOpen(t.third) }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    SmallTile("Favoriten & Listen", Icons.Filled.Favorite, Modifier.weight(1f), onFavorites)
                    SmallTile("Downloads", Icons.Filled.DownloadForOffline, Modifier.weight(1f), onDownloads)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    SmallTile("VPN & Sicherheit", Icons.Filled.Shield, Modifier.weight(1f), onVpn)
                    SmallTile("Benutzer wechseln", Icons.Filled.People, Modifier.weight(1f), onSwitchProfile)
                }
                SmallTile("Einstellungen", Icons.Filled.Settings, Modifier.fillMaxWidth(), onSettings)
                account?.let { AccountCard(it) }
            }
        }
    }
}

@Composable
private fun BigTile(
    title: String,
    icon: ImageVector,
    colors: List<Color>,
    height: Dp,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier
            .height(height)
            .clip(RoundedCornerShape(18.dp))
            .background(Brush.linearGradient(colors))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, null, tint = Color.White, modifier = Modifier.size(52.dp))
            Spacer(Modifier.height(8.dp))
            Text(title, color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
        }
    }
}

@Composable
private fun SmallTile(title: String, icon: ImageVector, modifier: Modifier, onClick: () -> Unit) {
    Row(
        modifier
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surface)
            .clickable(onClick = onClick)
            .padding(18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.size(12.dp))
        Text(title, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun AccountCard(info: AccountInfo) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text("Konto", fontWeight = FontWeight.Bold)
        val muted = MaterialTheme.colorScheme.onSurfaceVariant
        info.status?.let { Text("Status: $it", color = muted) }
        Text(
            "Ablaufdatum: " + (info.expiresAt?.let { DateFormat.getDateInstance().format(Date(it)) } ?: "Unbegrenzt"),
            color = muted,
        )
        if (info.maxConnections != null) {
            Text("Verbindungen: ${info.activeConnections ?: "0"} / ${info.maxConnections}", color = muted)
        }
        if (info.isTrial) Text("Testzugang", color = muted)
    }
}

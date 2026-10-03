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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.poweriptv.app.data.ContentType
import com.poweriptv.desktop.AppState
import com.poweriptv.desktop.Screen
import com.poweriptv.desktop.ui.ChannelCard
import com.poweriptv.desktop.ui.MenuAction
import com.poweriptv.desktop.ui.PosterCard
import com.poweriptv.desktop.ui.typeLabel

@Composable
fun FavoritesScreen(app: AppState) {
    val lib = app.library ?: return
    val favorites by lib.favorites.collectAsState()
    val watched by lib.watched.collectAsState()
    Column(Modifier.fillMaxSize().padding(28.dp)) {
        Text("Favoriten", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(16.dp))
        if (favorites.isEmpty()) {
            EmptyHint("Noch keine Favoriten. Rechtsklick auf einen Sender, Film oder eine Serie → „Zu Favoriten“.")
            return
        }
        LazyVerticalGrid(
            GridCells.Adaptive(160.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            for (type in listOf(ContentType.LIVE, ContentType.MOVIE, ContentType.SERIES)) {
                val list = favorites.filter { it.type == type }
                if (list.isEmpty()) continue
                item(span = { GridItemSpan(maxLineSpan) }, key = "h_$type") {
                    Text(typeLabel(type), style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 8.dp))
                }
                if (type == ContentType.LIVE) {
                    items(list, key = { it.key }, span = { GridItemSpan(2) }) { item ->
                        ChannelCard(
                            item, onClick = { app.play(item, channels = list) }, favorite = true,
                            menu = listOf(MenuAction("Aus Favoriten entfernen") { lib.toggleFavorite(item) }),
                        )
                    }
                } else {
                    items(list, key = { it.key }) { item ->
                        PosterCard(
                            item, onClick = { app.navigate(Screen.Detail(item)) }, favorite = true, watched = item.key in watched,
                            menu = listOf(MenuAction("Aus Favoriten entfernen") { lib.toggleFavorite(item) }),
                        )
                    }
                }
            }
        }
    }
}

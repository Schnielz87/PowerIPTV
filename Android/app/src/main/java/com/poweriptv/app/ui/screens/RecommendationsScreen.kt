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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
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
import com.poweriptv.app.ai.Recommendation
import com.poweriptv.app.data.ContentItem
import com.poweriptv.app.ui.components.ErrorBox
import com.poweriptv.app.ui.components.PowerTopBar
import com.poweriptv.app.ui.components.startPlayback
import com.poweriptv.app.ui.components.tvFocus
import com.poweriptv.app.ui.theme.BrandCyan
import kotlinx.coroutines.launch

/** Persoenliche Empfehlungen per ChatGPT. */
@Composable
fun RecommendationsScreen(
    container: AppContainer,
    onBack: () -> Unit,
    onOpenDetail: (ContentItem) -> Unit,
    onOpenSettings: () -> Unit,
) {
    val source = container.source ?: run { ErrorBox("Kein Zugang ausgewaehlt"); return }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var wish by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var results by remember { mutableStateOf<List<Recommendation>>(emptyList()) }
    val hasKey = remember { container.ai.hasApiKey() }

    fun load() {
        loading = true; error = null
        scope.launch {
            runCatching {
                container.ai.recommend(source, container.history.items.value, container.favorites.favorites.value, wish)
            }.onSuccess { results = it }.onFailure { error = it.message }
            loading = false
        }
    }

    Scaffold(
        topBar = { PowerTopBar("KI-Empfehlungen", onBack = onBack) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp)) {
            if (!hasKey) {
                ErrorBox(
                    "Verbinde dein ChatGPT-Konto: Hinterlege in den Einstellungen deinen OpenAI-API-Schluessel " +
                        "(platform.openai.com → API keys).",
                    onRetry = null,
                    modifier = Modifier.weight(1f),
                )
                Button(onClick = onOpenSettings, modifier = Modifier.tvFocus(RoundedCornerShape(50), 1.06f).fillMaxWidth().padding(bottom = 16.dp)) { Text("Zu den Einstellungen") }
                return@Column
            }
            Text(
                "Basierend auf deinem Verlauf und deinen Favoriten schlaegt ChatGPT passende Filme und Serien aus deinem Angebot vor.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(Modifier.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = wish, onValueChange = { wish = it },
                    placeholder = { Text("Optional: z.B. \"spannender Thriller\" oder \"lustig fuer Familie\"") },
                    singleLine = true, modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                Button(modifier = Modifier.tvFocus(RoundedCornerShape(50), 1.06f), onClick = { load() }, enabled = !loading) {
                    if (loading) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    else { Icon(Icons.Filled.AutoAwesome, null); Spacer(Modifier.width(6.dp)); Text("Empfehlen") }
                }
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            LazyColumn(contentPadding = PaddingValues(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(results) { r ->
                    val item = r.item
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .tvFocus(RoundedCornerShape(12.dp), 1.02f)
                            .clickable(enabled = item != null) {
                                if (item == null) return@clickable
                                if (source.supportsDetails) onOpenDetail(item)
                                else startPlayback(context, container, listOf(PlayEntry(item.name, source.streamUrl(item), item, live = false)), 0)
                            }
                            .background(MaterialTheme.colorScheme.surface)
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            Modifier.width(60.dp).aspectRatio(2f / 3f).clip(RoundedCornerShape(6.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(Icons.Filled.Movie, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            if (!item?.logo.isNullOrBlank()) AsyncImage(item?.logo, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(item?.name ?: r.title, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text(r.reason, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            if (item == null) Text("Nicht im Angebot gefunden", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                            else Text(if (r.type.name == "SERIES") "Serie" else "Film", style = MaterialTheme.typography.labelSmall, color = BrandCyan)
                        }
                    }
                }
            }
        }
    }
}

package com.poweriptv.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.FilterAlt
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.poweriptv.app.data.Category
import com.poweriptv.app.ui.theme.BrandCyan

const val CAT_FAV = "__fav__"
const val CAT_RECENT = "__recent__"
const val CAT_ALL = "__all__"

private val langRegex = Regex("""^\W*([A-Za-z]{2,4})\s*[|:\-–]""")

/** Sprach-/Laender-Praefix einer Kategorie, z.B. "EN | MOVIES LATEST" -> "EN". */
fun categoryLanguage(name: String): String? = langRegex.find(name)?.groupValues?.get(1)?.uppercase()

/** Name ohne Sprach-Praefix: "EN | MOVIES LATEST" -> "MOVIES LATEST". */
fun stripLanguage(name: String): String = langRegex.find(name)?.let { name.substring(it.range.last + 1).trim().trimStart('|', ':', '-', '–').trim() } ?: name

/** Alle Praefixe, die mindestens zweimal vorkommen (sonst ist es kein Sprachschema). */
fun detectLanguages(categories: List<Category>): List<String> =
    categories.mapNotNull { categoryLanguage(it.name) }
        .groupingBy { it }.eachCount()
        .filter { it.value >= 2 }
        .entries.sortedByDescending { it.value }
        .map { it.key }

/** Linke Kategorie-Spalte: kompakte Kopfzeile (Zurueck, Titel, Sprache, Suche) + Liste. */
@Composable
fun CategorySidebar(
    categories: List<Category>,
    selected: String?,
    lockedIds: Set<String>,
    language: String,
    onLanguage: (String) -> Unit,
    onSelect: (Category) -> Unit,
    modifier: Modifier = Modifier,
    title: String? = null,
    onBack: (() -> Unit)? = null,
) {
    var catQuery by remember { mutableStateOf("") }
    var showCatSearch by remember { mutableStateOf(false) }
    var langMenu by remember { mutableStateOf(false) }
    val languages = remember(categories) { detectLanguages(categories) }
    val activeLang = language.takeIf { it in languages } ?: ""
    val visible = remember(categories, activeLang, catQuery) {
        categories.filter { c ->
            (activeLang.isEmpty() || categoryLanguage(c.name) == activeLang) &&
                (catQuery.isBlank() || c.name.contains(catQuery.trim(), ignoreCase = true))
        }
    }

    Column(modifier.background(MaterialTheme.colorScheme.surface)) {
        // Kopfzeile
        Row(Modifier.fillMaxWidth().height(48.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            if (onBack != null) {
                IconButton(onClick = onBack, modifier = Modifier.size(40.dp).tvFocus(RoundedCornerShape(20.dp), 1f)) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Zurueck")
                }
            }
            Text(
                title ?: "Kategorien",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).padding(start = 4.dp),
            )
            if (languages.size >= 2) {
                Box {
                    Row(
                        Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .tvFocus(RoundedCornerShape(8.dp), 1f)
                            .clickable { langMenu = true }
                            .background(if (activeLang.isNotEmpty()) BrandCyan.copy(alpha = 0.16f) else MaterialTheme.colorScheme.surfaceVariant)
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Filled.Language, null, Modifier.size(16.dp), tint = if (activeLang.isNotEmpty()) BrandCyan else MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.width(4.dp))
                        Text(activeLang.ifEmpty { "Alle" }, style = MaterialTheme.typography.labelLarge)
                        Icon(Icons.Filled.ArrowDropDown, null, Modifier.size(18.dp))
                    }
                    DropdownMenu(expanded = langMenu, onDismissRequest = { langMenu = false }) {
                        DropdownMenuItem(text = { Text("Alle Sprachen") }, onClick = { onLanguage(""); langMenu = false })
                        languages.forEach { l ->
                            DropdownMenuItem(
                                text = { Text(l, fontWeight = if (l == activeLang) FontWeight.Bold else FontWeight.Normal) },
                                onClick = { onLanguage(l); langMenu = false },
                            )
                        }
                    }
                }
            }
            IconButton(
                onClick = { showCatSearch = !showCatSearch; if (!showCatSearch) catQuery = "" },
                modifier = Modifier.size(40.dp).tvFocus(RoundedCornerShape(20.dp), 1f),
            ) {
                Icon(Icons.Filled.FilterAlt, "Kategorien filtern", tint = if (showCatSearch) BrandCyan else MaterialTheme.colorScheme.onSurface)
            }
        }
        if (showCatSearch) {
            CompactSearchField(
                value = catQuery,
                onValueChange = { catQuery = it },
                placeholder = "Kategorie-Namen filtern",
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp),
                height = 36.dp,
            )
        }
        LazyColumn(Modifier.fillMaxHeight(), contentPadding = PaddingValues(top = 2.dp, bottom = 16.dp)) {
            item { SidebarRow(Icons.Filled.Star, "Favoriten", selected == CAT_FAV, false) { onSelect(Category(CAT_FAV, "Favoriten")) } }
            item { SidebarRow(Icons.Filled.History, "Zuletzt gesehen", selected == CAT_RECENT, false) { onSelect(Category(CAT_RECENT, "Zuletzt gesehen")) } }
            item { SidebarRow(Icons.Filled.Apps, "Alle", selected == CAT_ALL, false) { onSelect(Category(CAT_ALL, "Alle")) } }
            item {
                Text(
                    "Kategorien (${visible.size})",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 12.dp, top = 8.dp, bottom = 2.dp),
                )
            }
            items(visible, key = { it.id }) { c ->
                val label = if (activeLang.isNotEmpty()) stripLanguage(c.name) else c.name
                SidebarRow(Icons.Filled.Folder, label, selected == c.id, c.id in lockedIds) { onSelect(c) }
            }
        }
    }
}

@Composable
private fun SidebarRow(icon: ImageVector, label: String, selected: Boolean, locked: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 6.dp, vertical = 1.dp)
            .clip(RoundedCornerShape(8.dp))
            .tvFocus(RoundedCornerShape(8.dp), 1f)
            .clickable(onClick = onClick)
            .background(if (selected) BrandCyan.copy(alpha = 0.16f) else MaterialTheme.colorScheme.surface)
            .padding(horizontal = 6.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(3.dp).height(18.dp).background(if (selected) BrandCyan else MaterialTheme.colorScheme.surface))
        Spacer(Modifier.width(8.dp))
        Icon(
            if (locked) Icons.Filled.Lock else icon, null,
            tint = if (selected) BrandCyan else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(10.dp))
        Text(
            label,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = if (selected) BrandCyan else MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

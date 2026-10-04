package com.poweriptv.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.poweriptv.app.data.ContentItem

enum class SortMode(val label: String) {
    DEFAULT("Standard"),
    NAME_ASC("A – Z"),
    NAME_DESC("Z – A"),
    NEWEST("Neu hinzugefuegt"),
    RATING("Beste Bewertung"),
    YEAR_DESC("Neueste Jahre"),
    YEAR_ASC("Aelteste Jahre"),
}

/** Filter- und Sortiereinstellungen fuer Filme/Serien/Kanaele. */
data class ContentFilter(
    val sort: SortMode = SortMode.DEFAULT,
    val minRating: Int = 0,
    /** Jahrzehnt-Start, z.B. 2010; 0 = alle; -1 = vor 1990. */
    val decade: Int = 0,
    val genre: String? = null,
) {
    val activeCount: Int get() =
        (if (sort != SortMode.DEFAULT) 1 else 0) + (if (minRating > 0) 1 else 0) +
            (if (decade != 0) 1 else 0) + (if (genre != null) 1 else 0)

    fun apply(items: List<ContentItem>): List<ContentItem> {
        var list = items
        if (minRating > 0) list = list.filter { (it.ratingValue ?: 0.0) >= minRating }
        if (decade == -1) list = list.filter { (it.year ?: 9999) < 1990 }
        else if (decade > 0) list = list.filter { it.year != null && it.year in decade until decade + 10 }
        genre?.let { g -> list = list.filter { it.genre?.contains(g, ignoreCase = true) == true } }
        return when (sort) {
            SortMode.DEFAULT -> list
            SortMode.NAME_ASC -> list.sortedBy { it.name.lowercase() }
            SortMode.NAME_DESC -> list.sortedByDescending { it.name.lowercase() }
            SortMode.NEWEST -> list.sortedByDescending { it.added ?: 0L }
            SortMode.RATING -> list.sortedByDescending { it.ratingValue ?: 0.0 }
            SortMode.YEAR_DESC -> list.sortedByDescending { it.year ?: 0 }
            SortMode.YEAR_ASC -> list.sortedBy { it.year ?: 9999 }
        }
    }

    companion object {
        /** Genres aus den Eintraegen (Serien liefern sie mit). */
        fun genres(items: List<ContentItem>): List<String> =
            items.asSequence().mapNotNull { it.genre }
                .flatMap { it.split(',', '/', '&').asSequence() }
                .map { it.trim() }.filter { it.length in 3..30 }
                .groupingBy { it }.eachCount()
                .entries.sortedByDescending { it.value }.take(30).map { it.key }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FilterDialog(
    filter: ContentFilter,
    genres: List<String>,
    showRatingAndYear: Boolean,
    onChange: (ContentFilter) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Filter & Sortierung") },
        text = {
            Column(
                Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Section("Sortieren nach")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    SortMode.entries
                        .filter { showRatingAndYear || it in listOf(SortMode.DEFAULT, SortMode.NAME_ASC, SortMode.NAME_DESC) }
                        .forEach { m ->
                            FilterChip(selected = filter.sort == m, onClick = { onChange(filter.copy(sort = m)) }, label = { Text(m.label) })
                        }
                }
                if (showRatingAndYear) {
                    Section("Mindestbewertung")
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(0, 5, 6, 7, 8).forEach { r ->
                            FilterChip(
                                selected = filter.minRating == r,
                                onClick = { onChange(filter.copy(minRating = r)) },
                                label = { Text(if (r == 0) "Alle" else "★ $r+") },
                            )
                        }
                    }
                    Section("Erscheinungsjahr")
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(0 to "Alle", 2020 to "2020er", 2010 to "2010er", 2000 to "2000er", 1990 to "1990er", -1 to "Aelter").forEach { (d, label) ->
                            FilterChip(selected = filter.decade == d, onClick = { onChange(filter.copy(decade = d)) }, label = { Text(label) })
                        }
                    }
                }
                if (genres.isNotEmpty()) {
                    Section("Genre")
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilterChip(selected = filter.genre == null, onClick = { onChange(filter.copy(genre = null)) }, label = { Text("Alle") })
                        genres.forEach { g ->
                            FilterChip(selected = filter.genre == g, onClick = { onChange(filter.copy(genre = g)) }, label = { Text(g) })
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Fertig") } },
        dismissButton = { TextButton(onClick = { onChange(ContentFilter()) }) { Text("Zuruecksetzen") } },
    )
}

@Composable
private fun Section(title: String) {
    Text(title, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
}

package com.poweriptv.app.ui.components

import com.poweriptv.app.data.Category

// Regeln fuer die Kategorie-Spalte – geteilt zwischen Android-App und Windows-App (Windows/).

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

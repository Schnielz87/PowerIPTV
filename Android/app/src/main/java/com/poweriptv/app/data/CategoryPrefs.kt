package com.poweriptv.app.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Kategorien pro Profil und Bereich (Live/Filme/Serien) ausblenden oder oben anheften.
 * [scope] = "<profilId>|<Typ>".
 */
class CategoryPrefs(context: Context) {
    private val prefs = context.getSharedPreferences("category_prefs", Context.MODE_PRIVATE)
    private val _version = MutableStateFlow(0)
    /** Aendert sich bei jeder Anpassung (fuer die Oberflaeche). */
    val version: StateFlow<Int> = _version

    fun hidden(scope: String): Set<String> = prefs.getStringSet("h|$scope", emptySet()).orEmpty()
    fun pinned(scope: String): List<String> = prefs.getString("p|$scope", "").orEmpty().split('\n').filter { it.isNotBlank() }

    fun setHidden(scope: String, id: String, hide: Boolean) {
        val set = hidden(scope).toMutableSet().apply { if (hide) add(id) else remove(id) }
        prefs.edit().putStringSet("h|$scope", set).apply()
        if (hide) setPinned(scope, id, false) else _version.value++
    }

    fun setPinned(scope: String, id: String, pin: Boolean) {
        val list = pinned(scope).filterNot { it == id }.let { if (pin) it + id else it }
        prefs.edit().putString("p|$scope", list.joinToString("\n")).apply()
        _version.value++
    }

    /** Angeheftete zuerst (in Reihenfolge des Anheftens), dann der Rest wie vom Anbieter. */
    fun <T> arrange(scope: String, items: List<T>, id: (T) -> String): List<T> {
        val pins = pinned(scope)
        if (pins.isEmpty()) return items
        val byId = items.associateBy(id)
        val top = pins.mapNotNull { byId[it] }
        return top + items.filterNot { id(it) in pins }
    }
}

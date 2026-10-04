package com.poweriptv.app.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/** Zuletzt gesehen (pro Profil) – Basis fuer "Weiterschauen" und KI-Empfehlungen. */
class HistoryRepository(context: Context, private val json: Json) {
    private val prefs = context.getSharedPreferences("history", Context.MODE_PRIVATE)
    private val serializer = ListSerializer(ContentItem.serializer())
    private val _items = MutableStateFlow<List<ContentItem>>(emptyList())
    val items: StateFlow<List<ContentItem>> = _items
    private var profileId: String? = null

    /** Titel, die nie in "Zuletzt gesehen" landen (Erwachseneninhalte, siehe ParentalControl.isAdultItem). */
    var exclude: (profileId: String, item: ContentItem) -> Boolean = { _, _ -> false }

    fun bind(profileId: String?) {
        this.profileId = profileId
        _items.value = profileId?.let { id ->
            prefs.getString(id, null)?.let { runCatching { json.decodeFromString(serializer, it) }.getOrNull() }
        }.orEmpty()
        purge()
    }

    fun add(item: ContentItem) {
        val id = profileId ?: return
        if (exclude(id, item)) { purge(); return }
        val list = (listOf(item) + _items.value.filterNot { it.key == item.key || exclude(id, it) }).take(MAX)
        prefs.edit().putString(id, json.encodeToString(serializer, list)).apply()
        _items.value = list
    }

    /** Bereits gespeicherte Erwachseneninhalte entfernen (z. B. nachdem die Kategorien bekannt sind). */
    fun purge() {
        val id = profileId ?: return
        val list = _items.value.filterNot { exclude(id, it) }
        if (list.size != _items.value.size) save(list)
    }

    fun remove(key: String) = save(_items.value.filterNot { it.key == key })

    fun clearType(type: ContentType) = save(_items.value.filterNot { it.type == type })

    private fun save(list: List<ContentItem>) {
        val id = profileId ?: return
        prefs.edit().putString(id, json.encodeToString(serializer, list)).apply()
        _items.value = list
    }

    fun clear() {
        profileId?.let { prefs.edit().remove(it).apply() }
        _items.value = emptyList()
    }

    companion object { private const val MAX = 100 }
}

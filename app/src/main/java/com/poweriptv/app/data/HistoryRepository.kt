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

    fun bind(profileId: String?) {
        this.profileId = profileId
        _items.value = profileId?.let { id ->
            prefs.getString(id, null)?.let { runCatching { json.decodeFromString(serializer, it) }.getOrNull() }
        }.orEmpty()
    }

    fun add(item: ContentItem) {
        val id = profileId ?: return
        val list = (listOf(item) + _items.value.filterNot { it.key == item.key }).take(MAX)
        prefs.edit().putString(id, json.encodeToString(serializer, list)).apply()
        _items.value = list
    }

    fun clear() {
        profileId?.let { prefs.edit().remove(it).apply() }
        _items.value = emptyList()
    }

    companion object { private const val MAX = 100 }
}

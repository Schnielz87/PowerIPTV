package com.poweriptv.app.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.util.UUID

@Serializable
data class FavoriteList(
    val id: String,
    val name: String,
    val items: List<ContentItem> = emptyList(),
)

/**
 * Favoriten pro Profil: eine Standard-Liste ("Favoriten", Herz-Symbol)
 * plus beliebig viele selbst angelegte Listen.
 */
class FavoritesRepository(context: Context, private val json: Json) {
    private val prefs = context.getSharedPreferences("favorite_lists", Context.MODE_PRIVATE)
    private val serializer = ListSerializer(FavoriteList.serializer())

    private val _lists = MutableStateFlow<List<FavoriteList>>(emptyList())
    /** Alle Listen, die erste ist immer die Standard-Liste. */
    val lists: StateFlow<List<FavoriteList>> = _lists

    private val _favorites = MutableStateFlow<List<ContentItem>>(emptyList())
    /** Inhalte der Standard-Liste. */
    val favorites: StateFlow<List<ContentItem>> = _favorites

    private var profileId: String? = null

    fun bind(profileId: String?) {
        this.profileId = profileId
        val loaded = profileId?.let { id ->
            prefs.getString(id, null)?.let { runCatching { json.decodeFromString(serializer, it) }.getOrNull() }
        }.orEmpty()
        publish(ensureDefault(loaded))
    }

    private fun ensureDefault(lists: List<FavoriteList>): List<FavoriteList> =
        if (lists.any { it.id == DEFAULT_ID }) lists.sortedBy { if (it.id == DEFAULT_ID) 0 else 1 }
        else listOf(FavoriteList(DEFAULT_ID, "Favoriten")) + lists

    private fun publish(lists: List<FavoriteList>) {
        _lists.value = lists
        _favorites.value = lists.firstOrNull { it.id == DEFAULT_ID }?.items.orEmpty()
    }

    private fun update(transform: (List<FavoriteList>) -> List<FavoriteList>) {
        val id = profileId ?: return
        val updated = ensureDefault(transform(_lists.value))
        prefs.edit().putString(id, json.encodeToString(serializer, updated)).apply()
        publish(updated)
    }

    fun isFavorite(item: ContentItem) = _favorites.value.any { it.key == item.key }

    /** Herz-Symbol: in der Standard-Liste an/aus. */
    fun toggle(item: ContentItem) = toggleInList(DEFAULT_ID, item)

    fun isInList(listId: String, item: ContentItem) =
        _lists.value.firstOrNull { it.id == listId }?.items?.any { it.key == item.key } == true

    fun toggleInList(listId: String, item: ContentItem) = update { lists ->
        lists.map { l ->
            if (l.id != listId) l
            else if (l.items.any { it.key == item.key }) l.copy(items = l.items.filterNot { it.key == item.key })
            else l.copy(items = listOf(item) + l.items)
        }
    }

    fun createList(name: String): String {
        val id = UUID.randomUUID().toString()
        update { it + FavoriteList(id, name.trim().ifBlank { "Neue Liste" }) }
        return id
    }

    fun renameList(listId: String, name: String) = update { lists ->
        lists.map { if (it.id == listId && name.isNotBlank()) it.copy(name = name.trim()) else it }
    }

    fun deleteList(listId: String) {
        if (listId == DEFAULT_ID) return
        update { lists -> lists.filterNot { it.id == listId } }
    }

    fun clear(profileId: String) = prefs.edit().remove(profileId).apply()

    companion object {
        const val DEFAULT_ID = "default"
    }
}

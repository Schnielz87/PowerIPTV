package com.poweriptv.app.data

import android.content.Context
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * Speichert Kategorien und Titel einer Playlist lokal auf dem Geraet.
 * - schneller App-Start (kein komplettes Neuladen bei jedem Oeffnen)
 * - Aktualisierung manuell oder automatisch nach [MAX_AGE] (24 h)
 */
class CachedSource(
    private val inner: ContentSource,
    context: Context,
    private val json: Json,
) : ContentSource by inner {

    private val dir = File(context.filesDir, "playlist-cache/${inner.profile.id}").apply { mkdirs() }
    private val prefs = context.getSharedPreferences("playlist_refresh", Context.MODE_PRIVATE)
    private val mem = ConcurrentHashMap<String, List<*>>()

    /** Zeitpunkt der letzten Aktualisierung vom Server (0 = nie). */
    val lastRefresh: Long get() = prefs.getLong(inner.profile.id, 0L)

    fun isStale(now: Long = System.currentTimeMillis()) = now - lastRefresh > MAX_AGE

    private fun markRefreshed() = prefs.edit().putLong(inner.profile.id, System.currentTimeMillis()).apply()

    override suspend fun categories(type: ContentType): List<Category> =
        cached("cat_${type.name}", ListSerializer(Category.serializer())) { inner.categories(type) }

    override suspend fun items(type: ContentType, categoryId: String?): List<ContentItem> =
        cached("items_${type.name}_${categoryId ?: "all"}", ListSerializer(ContentItem.serializer())) { inner.items(type, categoryId) }

    @Suppress("UNCHECKED_CAST")
    private suspend fun <T> cached(key: String, serializer: KSerializer<List<T>>, load: suspend () -> List<T>): List<T> {
        mem[key]?.let { return it as List<T> }
        val file = File(dir, fileName(key))
        if (file.exists()) {
            val fromDisk = withContext(Dispatchers.IO) {
                runCatching { json.decodeFromString(serializer, file.readText()) }.getOrNull()
            }
            if (fromDisk != null) {
                mem[key] = fromDisk
                return fromDisk
            }
        }
        val fresh = load()
        mem[key] = fresh
        if (lastRefresh == 0L) markRefreshed()
        withContext(Dispatchers.IO) {
            runCatching { file.writeText(json.encodeToString(serializer, fresh)) }
        }
        return fresh
    }

    /** Verwirft alle gespeicherten Daten; die naechsten Zugriffe laden frisch vom Server. */
    override fun clearCache() {
        mem.clear()
        dir.listFiles()?.forEach { it.delete() }
        inner.clearCache()
        markRefreshed()
    }

    /** Komplette Aktualisierung: Cache leeren und Kategorien aller Bereiche neu laden. */
    suspend fun refreshAll() {
        clearCache()
        for (type in ContentType.entries) {
            try {
                categories(type)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // Bereich evtl. nicht vorhanden (z.B. M3U ohne Serien)
            }
        }
    }

    private fun fileName(key: String): String =
        key.replace(Regex("[^A-Za-z0-9_]"), "_").take(60) + "_" + Integer.toHexString(key.hashCode()) + ".json"

    companion object {
        const val MAX_AGE = 24 * 3600_000L
    }
}

package com.poweriptv.desktop.data

import com.poweriptv.app.data.Category
import com.poweriptv.app.data.ContentItem
import com.poweriptv.app.data.ContentSource
import com.poweriptv.app.data.ContentType
import com.poweriptv.app.data.M3uSource
import com.poweriptv.app.data.Profile
import com.poweriptv.app.data.ProfileType
import com.poweriptv.app.data.XtreamSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import okhttp3.OkHttpClient
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

const val USER_AGENT = "PowerIPTV/1.0 (Windows; Desktop)"

val Http: OkHttpClient by lazy {
    OkHttpClient.Builder()
        .addInterceptor { chain -> chain.proceed(chain.request().newBuilder().header("User-Agent", USER_AGENT).build()) }
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()
}

fun createSource(profile: Profile): ContentSource = when (profile.type) {
    ProfileType.XTREAM -> XtreamSource(profile, Http, AppJson) { "ts" }
    ProfileType.M3U_URL, ProfileType.M3U_FILE -> M3uSource(profile, Http)
}

/** Speichert Kategorien und Titel lokal – schneller Start, Aktualisierung manuell oder nach 24 h. */
class DiskCachedSource(private val inner: ContentSource) : ContentSource by inner {
    private val dir = File(AppDirs.cache, "playlist/${inner.profile.id.replace(Regex("[^A-Za-z0-9_-]"), "_")}").apply { mkdirs() }
    private val stamp = File(dir, "_refreshed")
    private val mem = ConcurrentHashMap<String, List<*>>()

    val lastRefresh: Long get() = stamp.takeIf { it.exists() }?.readText()?.toLongOrNull() ?: 0L
    fun isStale() = System.currentTimeMillis() - lastRefresh > 24 * 3600_000L
    private fun markRefreshed() = runCatching { stamp.writeText(System.currentTimeMillis().toString()) }

    override suspend fun categories(type: ContentType): List<Category> =
        cached("cat_${type.name}", ListSerializer(Category.serializer())) { inner.categories(type) }

    override suspend fun items(type: ContentType, categoryId: String?): List<ContentItem> =
        cached("items_${type.name}_${categoryId ?: "all"}", ListSerializer(ContentItem.serializer())) { inner.items(type, categoryId) }

    @Suppress("UNCHECKED_CAST")
    private suspend fun <T> cached(key: String, serializer: KSerializer<List<T>>, load: suspend () -> List<T>): List<T> {
        mem[key]?.let { return it as List<T> }
        val file = File(dir, key.replace(Regex("[^A-Za-z0-9_]"), "_").take(60) + "_" + Integer.toHexString(key.hashCode()) + ".json")
        val fromDisk = withContext(Dispatchers.IO) {
            if (file.exists()) runCatching { AppJson.decodeFromString(serializer, file.readText()) }.getOrNull() else null
        }
        if (fromDisk != null) {
            mem[key] = fromDisk
            return fromDisk
        }
        val fresh = load()
        mem[key] = fresh
        if (lastRefresh == 0L) markRefreshed()
        withContext(Dispatchers.IO) { runCatching { file.writeText(AppJson.encodeToString(serializer, fresh)) } }
        return fresh
    }

    override fun clearCache() {
        mem.clear()
        dir.listFiles()?.forEach { it.delete() }
        inner.clearCache()
        markRefreshed()
    }

    /** Cache leeren und alle Bereiche neu laden. */
    suspend fun refreshAll() {
        clearCache()
        for (type in ContentType.entries) {
            try {
                categories(type)
                items(type, null)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // Bereich evtl. nicht vorhanden (z.B. M3U ohne Serien)
            }
        }
    }
}

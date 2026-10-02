package com.poweriptv.app.data

import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.ConcurrentHashMap

class XtreamSource(
    override val profile: Profile,
    private val http: OkHttpClient,
    private val json: Json,
    private val liveExt: () -> String,
) : ContentSource {

    override val supportsDetails = true

    private val base = normalizeServer(profile.serverUrl)
    private val user = enc(profile.username)
    private val pass = enc(profile.password)
    private val catCache = ConcurrentHashMap<ContentType, List<Category>>()
    private val itemCache = ConcurrentHashMap<String, List<ContentItem>>()

    private suspend fun call(action: String?, vararg params: Pair<String, String>): JsonElement =
        withContext(Dispatchers.IO) {
            val b = "$base/player_api.php".toHttpUrl().newBuilder()
                .addQueryParameter("username", profile.username)
                .addQueryParameter("password", profile.password)
            if (action != null) b.addQueryParameter("action", action)
            params.forEach { (k, v) -> b.addQueryParameter(k, v) }
            val req = Request.Builder().url(b.build()).build()
            http.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) throw IOException("Server antwortet mit HTTP ${resp.code}")
                val body = resp.body?.string().orEmpty()
                if (body.isBlank()) JsonArray(emptyList()) else json.parseToJsonElement(body)
            }
        }

    /** Prueft die Zugangsdaten. Wirft eine Exception bei Fehlern. */
    suspend fun authenticate(): AccountInfo {
        val root = call(null) as? JsonObject ?: throw IOException("Ungueltige Server-Antwort")
        val info = root.obj("user_info") ?: throw IOException("Ungueltige Server-Antwort")
        if (info.int("auth") != 1) throw IOException("Benutzername oder Passwort falsch")
        return parseAccount(info)
    }

    override suspend fun accountInfo(): AccountInfo? = runCatching { authenticate() }.getOrNull()

    private fun parseAccount(info: JsonObject) = AccountInfo(
        status = info.str("status"),
        expiresAt = info.long("exp_date")?.times(1000),
        maxConnections = info.str("max_connections"),
        activeConnections = info.str("active_cons"),
        isTrial = info.str("is_trial") == "1",
    )

    override suspend fun categories(type: ContentType): List<Category> {
        catCache[type]?.let { return it }
        val action = when (type) {
            ContentType.LIVE -> "get_live_categories"
            ContentType.MOVIE -> "get_vod_categories"
            ContentType.SERIES -> "get_series_categories"
        }
        val list = call(action).asArray().mapNotNull { o ->
            val id = o.str("category_id") ?: return@mapNotNull null
            Category(id, o.str("category_name") ?: "Unbenannt")
        }
        catCache[type] = list
        return list
    }

    override suspend fun items(type: ContentType, categoryId: String?): List<ContentItem> {
        val cacheKey = "${type.name}:${categoryId ?: "*"}"
        itemCache[cacheKey]?.let { return it }
        val action = when (type) {
            ContentType.LIVE -> "get_live_streams"
            ContentType.MOVIE -> "get_vod_streams"
            ContentType.SERIES -> "get_series"
        }
        val params = if (categoryId != null) arrayOf("category_id" to categoryId) else emptyArray()
        val list = call(action, *params).asArray().mapNotNull { o ->
            when (type) {
                ContentType.LIVE -> ContentItem(
                    id = o.str("stream_id") ?: return@mapNotNull null,
                    name = o.str("name") ?: "",
                    type = type,
                    categoryId = o.str("category_id") ?: "",
                    logo = o.str("stream_icon"),
                    epgChannelId = o.str("epg_channel_id"),
                    number = o.int("num"),
                )
                ContentType.MOVIE -> ContentItem(
                    id = o.str("stream_id") ?: return@mapNotNull null,
                    name = o.str("name") ?: "",
                    type = type,
                    categoryId = o.str("category_id") ?: "",
                    logo = o.str("stream_icon"),
                    containerExtension = o.str("container_extension"),
                    rating = o.str("rating"),
                )
                ContentType.SERIES -> ContentItem(
                    id = o.str("series_id") ?: return@mapNotNull null,
                    name = o.str("name") ?: "",
                    type = type,
                    categoryId = o.str("category_id") ?: "",
                    logo = o.str("cover"),
                    rating = o.str("rating"),
                )
            }
        }
        itemCache[cacheKey] = list
        return list
    }

    override suspend fun movieInfo(item: ContentItem): MovieInfo? {
        val root = call("get_vod_info", "vod_id" to item.id) as? JsonObject ?: return null
        val info = root.obj("info") ?: JsonObject(emptyMap())
        val movie = root.obj("movie_data")
        return MovieInfo(
            plot = info.str("plot") ?: info.str("description"),
            genre = info.str("genre"),
            director = info.str("director"),
            cast = info.str("cast") ?: info.str("actors"),
            releaseDate = info.str("releasedate") ?: info.str("release_date"),
            duration = info.str("duration"),
            rating = info.str("rating"),
            cover = info.str("movie_image") ?: info.str("cover_big") ?: item.logo,
            backdrop = (info["backdrop_path"] as? JsonArray)?.firstOrNull()?.let { (it as? kotlinx.serialization.json.JsonPrimitive)?.content }
                ?: info.str("backdrop_path"),
            containerExtension = movie?.str("container_extension") ?: item.containerExtension,
        )
    }

    override suspend fun seriesInfo(item: ContentItem): SeriesInfo? {
        val root = call("get_series_info", "series_id" to item.id) as? JsonObject ?: return null
        val info = root.obj("info") ?: JsonObject(emptyMap())
        val episodes = mutableMapOf<Int, MutableList<Episode>>()
        val parseEp = { o: JsonObject, seasonFallback: Int ->
            val epInfo = o.obj("info")
            val season = o.int("season") ?: seasonFallback
            val ep = Episode(
                id = o.str("id") ?: "",
                title = o.str("title") ?: "Episode ${o.str("episode_num") ?: ""}",
                season = season,
                episodeNum = o.int("episode_num") ?: 0,
                containerExtension = o.str("container_extension"),
                plot = epInfo?.str("plot"),
                image = epInfo?.str("movie_image"),
                duration = epInfo?.str("duration"),
            )
            if (ep.id.isNotBlank()) episodes.getOrPut(season) { mutableListOf() }.add(ep)
        }
        when (val eps = root["episodes"]) {
            is JsonObject -> eps.forEach { (season, arr) ->
                arr.asArray().forEach { parseEp(it, season.toIntOrNull() ?: 0) }
            }
            is JsonArray -> eps.forEachIndexed { idx, arr ->
                arr.asArray().forEach { parseEp(it, idx + 1) }
            }
            else -> Unit
        }
        return SeriesInfo(
            plot = info.str("plot"),
            genre = info.str("genre"),
            cast = info.str("cast"),
            releaseDate = info.str("releaseDate") ?: info.str("release_date"),
            rating = info.str("rating"),
            cover = info.str("cover") ?: item.logo,
            backdrop = (info["backdrop_path"] as? JsonArray)?.firstOrNull()?.let { (it as? kotlinx.serialization.json.JsonPrimitive)?.content },
            episodes = episodes.toSortedMap().mapValues { (_, v) -> v.sortedBy { it.episodeNum } },
        )
    }

    override suspend fun shortEpg(item: ContentItem): List<EpgEntry> = runCatching {
        val root = call("get_short_epg", "stream_id" to item.id, "limit" to "4") as? JsonObject
            ?: return emptyList()
        root["epg_listings"].asArray().map { o ->
            EpgEntry(
                title = decodeB64(o.str("title")) ?: "",
                description = decodeB64(o.str("description")),
                start = o.long("start_timestamp")?.times(1000) ?: parseDate(o.str("start")),
                end = o.long("stop_timestamp")?.times(1000) ?: parseDate(o.str("end")),
            )
        }
    }.getOrDefault(emptyList())

    override fun streamUrl(item: ContentItem): String = when (item.type) {
        ContentType.LIVE -> "$base/live/$user/$pass/${item.id}.${liveExt()}"
        ContentType.MOVIE -> "$base/movie/$user/$pass/${item.id}.${item.containerExtension ?: "mp4"}"
        ContentType.SERIES -> error("Serien werden ueber Episoden abgespielt")
    }

    override fun episodeUrl(episode: Episode): String =
        "$base/series/$user/$pass/${episode.id}.${episode.containerExtension ?: "mp4"}"

    override fun clearCache() {
        catCache.clear(); itemCache.clear()
    }

    private fun decodeB64(s: String?): String? = s?.let {
        runCatching { String(Base64.decode(it, Base64.DEFAULT), Charsets.UTF_8) }.getOrDefault(it)
    }

    private fun parseDate(s: String?): Long = s?.let {
        runCatching {
            SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
                .parse(it)?.time
        }.getOrNull()
    } ?: 0L

    companion object {
        fun normalizeServer(url: String): String {
            var u = url.trim().trimEnd('/')
            if (!u.startsWith("http://") && !u.startsWith("https://")) u = "http://$u"
            u = u.removeSuffix("/player_api.php").removeSuffix("/get.php").trimEnd('/')
            return u
        }

        private fun enc(s: String) = URLEncoder.encode(s, "UTF-8").replace("+", "%20")
    }
}

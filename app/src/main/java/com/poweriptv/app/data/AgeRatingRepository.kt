package com.poweriptv.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.ConcurrentHashMap

/**
 * Altersfreigabe fuer die Detailseite.
 * @param fsk     0/6/12/16/18 (null = keine deutsche Freigabe bekannt)
 * @param label   Anzeigetext, z.B. "FSK 12" oder "PG-13"
 * @param official true = deutsche Freigabe laut TMDB
 */
data class AgeRating(val fsk: Int?, val label: String, val official: Boolean)

/**
 * Ermittelt die FSK:
 * 1. Mit TMDB-Schluessel: offizielle deutsche Freigabe (TMDB-ID vom Anbieter oder Titelsuche).
 * 2. Sonst: Altersangabe des Anbieters ("age", "mpaa_rating" …).
 */
class AgeRatingRepository(
    private val http: () -> OkHttpClient,
    private val secure: SecureStore,
    private val json: Json,
) {
    private val cache = ConcurrentHashMap<String, Result<AgeRating?>>()

    fun apiKey(): String? = secure.read(KEY_FILE)
    fun hasApiKey() = !apiKey().isNullOrBlank()
    fun setApiKey(key: String?) {
        secure.write(KEY_FILE, key?.trim()?.ifBlank { null })
        cache.clear()
    }

    suspend fun resolve(series: Boolean, title: String, releaseDate: String?, tmdbId: String?, providerAge: String?): AgeRating? {
        val k = "$series|$title|$tmdbId"
        cache[k]?.let { return it.getOrNull() }
        val result = runCatching {
            fromTmdb(series, title, releaseDate, tmdbId) ?: fromProvider(providerAge)
        }
        // Netzwerkfehler nicht dauerhaft merken
        if (result.isSuccess) cache[k] = result
        return result.getOrNull() ?: fromProvider(providerAge)
    }

    /** Test fuer die Einstellungen: liefert z.B. "FSK 12" fuer einen bekannten Film. */
    suspend fun test(): String {
        val r = fromTmdb(false, "Findet Nemo", "2003", "12") ?: return "Verbunden, aber keine Freigabe gefunden"
        return "Verbindung erfolgreich (Findet Nemo: ${r.label})"
    }

    private suspend fun fromTmdb(series: Boolean, title: String, releaseDate: String?, tmdbId: String?): AgeRating? =
        withContext(Dispatchers.IO) {
            val key = apiKey()?.takeIf { it.isNotBlank() } ?: return@withContext null
            val id = tmdbId?.trim()?.takeIf { it.isNotEmpty() && it != "0" && it.all(Char::isDigit) }
                ?: search(key, series, cleanTitle(title), yearOf(releaseDate) ?: yearOf(title))
                ?: return@withContext null
            val cert = if (series) {
                val o = get(key, "tv/$id/content_ratings") ?: return@withContext null
                (o["results"] as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }
                    .firstOrNull { it.s("iso_3166_1") == "DE" }?.s("rating")
            } else {
                val o = get(key, "movie/$id/release_dates") ?: return@withContext null
                (o["results"] as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }
                    .firstOrNull { it.s("iso_3166_1") == "DE" }
                    ?.let { de -> (de["release_dates"] as? JsonArray).orEmpty().mapNotNull { (it as? JsonObject)?.s("certification") }.firstOrNull { it.isNotBlank() } }
            }
            val n = cert?.filter(Char::isDigit)?.toIntOrNull()?.takeIf { it in FSK_LEVELS } ?: return@withContext null
            AgeRating(n, "FSK $n", official = true)
        }

    private fun search(key: String, series: Boolean, query: String, year: String?): String? {
        if (query.isBlank()) return null
        val params = buildList {
            add("query" to query)
            add("language" to "de-DE")
            if (year != null) add((if (series) "first_air_date_year" else "year") to year)
        }
        val o = get(key, if (series) "search/tv" else "search/movie", params) ?: return null
        val first = (o["results"] as? JsonArray)?.firstOrNull() as? JsonObject ?: return null
        return first.s("id")
    }

    private fun get(key: String, path: String, params: List<Pair<String, String>> = emptyList()): JsonObject? {
        val v3 = key.length <= 40 && !key.contains('.')
        val url = "https://api.themoviedb.org/3/$path".toHttpUrl().newBuilder().apply {
            params.forEach { (k, v) -> addQueryParameter(k, v) }
            if (v3) addQueryParameter("api_key", key)
        }.build()
        val req = Request.Builder().url(url).apply { if (!v3) header("Authorization", "Bearer $key") }.build()
        return http().newCall(req).execute().use { resp ->
            if (resp.code == 401) throw IllegalStateException("TMDB-Schluessel ungueltig")
            if (!resp.isSuccessful) return null
            json.parseToJsonElement(resp.body?.string().orEmpty()) as? JsonObject
        }
    }

    private fun JsonObject.s(k: String) = (this[k] as? JsonPrimitive)?.content

    companion object {
        private const val KEY_FILE = "tmdb_api_key"
        private val FSK_LEVELS = setOf(0, 6, 12, 16, 18)

        /** Anbieter-Angabe deuten: "12", "12+", "FSK 16", "PG-13" … */
        fun fromProvider(raw: String?): AgeRating? {
            val v = raw?.trim()?.takeIf { it.isNotEmpty() && !it.equals("null", true) && !it.equals("n/a", true) } ?: return null
            v.filter(Char::isDigit).toIntOrNull()?.let { n ->
                // "0" ist bei vielen Anbietern nur der Standardwert (= unbekannt)
                if (n in FSK_LEVELS && n != 0) return AgeRating(n, "FSK $n", official = false)
            }
            // US-Freigaben (MPAA / TV) unveraendert anzeigen
            val us = v.uppercase()
            if (us in setOf("G", "PG", "PG-13", "R", "NC-17", "TV-Y", "TV-Y7", "TV-G", "TV-PG", "TV-14", "TV-MA")) {
                return AgeRating(null, us, official = false)
            }
            return null
        }

        /** "Vaiana # DE", "DE - Django (2012)" -> "Vaiana", "Django" */
        fun cleanTitle(t: String): String = t
            .replace(Regex("#.*$"), "")
            .replace(Regex("\\(\\d{4}\\)"), "")
            .replace(Regex("^[A-Z]{2,3}\\s*[-:|]\\s*"), "")
            .replace(Regex("\\[[^]]*]"), "")
            .trim()

        fun yearOf(s: String?): String? = s?.let { Regex("(19|20)\\d{2}").find(it)?.value }
    }
}

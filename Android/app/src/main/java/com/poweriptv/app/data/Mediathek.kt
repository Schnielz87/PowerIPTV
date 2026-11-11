package com.poweriptv.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/*
 * Mediatheken von ARD, ZDF, Arte & Co. (oeffentlich-rechtlich, kostenlos) – ueber die freie
 * MediathekView-Datenbank (mediathekviewweb.de). Sie sammelt alle Sendungen der Sender-Mediatheken mit
 * direkten Video-Links (MP4 in niedriger/normaler/HD-Qualitaet). Unabhaengig vom IPTV-Zugang.
 * Geteilt zwischen Android und Windows.
 */

data class MediathekItem(
    val id: String,
    val channel: String,
    val topic: String,
    val title: String,
    val description: String?,
    /** Sendezeit (Sekunden seit 1970). */
    val timestamp: Long,
    /** Laenge in Sekunden. */
    val duration: Long,
    val url: String,
    val urlHd: String?,
    val urlLow: String?,
    val website: String?,
) {
    /** Beste Qualitaet (HD, sonst normal). */
    fun bestUrl(): String = urlHd?.takeIf { it.isNotBlank() } ?: url
}

data class MediathekChannel(val label: String, val query: String)

object MediathekCatalog {
    /** Sender in der Auswahl (query = Name in der MediathekView-Datenbank). */
    val channels = listOf(
        MediathekChannel("Alle Sender", ""),
        MediathekChannel("ARD", "ARD"),
        MediathekChannel("ZDF", "ZDF"),
        MediathekChannel("Arte", "ARTE.DE"),
        MediathekChannel("3sat", "3Sat"),
        MediathekChannel("ZDFneo", "ZDFneo"),
        MediathekChannel("ZDFinfo", "ZDFinfo"),
        MediathekChannel("Phoenix", "PHOENIX"),
        MediathekChannel("KiKA", "KiKA"),
        MediathekChannel("ZDFtivi", "ZDF-tivi"),
        MediathekChannel("BR", "BR"),
        MediathekChannel("WDR", "WDR"),
        MediathekChannel("NDR", "NDR"),
        MediathekChannel("SWR", "SWR"),
        MediathekChannel("MDR", "MDR"),
        MediathekChannel("HR", "HR"),
        MediathekChannel("RBB", "RBB"),
        MediathekChannel("SR", "SR"),
        MediathekChannel("Radio Bremen", "Radio Bremen TV"),
        MediathekChannel("ORF", "ORF"),
        MediathekChannel("SRF", "SRF"),
        MediathekChannel("DW", "DW"),
    )
}

class MediathekClient(private val http: () -> OkHttpClient, private val json: Json) {

    /**
     * Sendungen suchen. query leer = neueste Sendungen; channel leer = alle Sender.
     * minDuration filtert kurze Clips/Trailer aus (Standard 3 Minuten).
     */
    suspend fun search(query: String, channel: String, offset: Int = 0, size: Int = 60, minDuration: Long = 180): List<MediathekItem> =
        withContext(Dispatchers.IO) {
            val body = buildJsonObject {
                put("queries", buildJsonArray {
                    if (channel.isNotBlank()) add(buildJsonObject {
                        put("fields", buildJsonArray { add(JsonPrimitive("channel")) })
                        put("query", channel)
                    })
                    if (query.isNotBlank()) add(buildJsonObject {
                        put("fields", buildJsonArray { add(JsonPrimitive("title")); add(JsonPrimitive("topic")) })
                        put("query", query.trim())
                    })
                })
                put("sortBy", "timestamp")
                put("sortOrder", "desc")
                put("future", false)
                put("offset", offset)
                put("size", size)
                if (minDuration > 0) put("duration_min", minDuration)
            }
            val req = Request.Builder()
                .url("https://mediathekviewweb.de/api/query")
                .post(body.toString().toRequestBody("text/plain".toMediaType()))
                .build()
            http().newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) throw IllegalStateException("Mediathek nicht erreichbar (${resp.code})")
                val root = json.parseToJsonElement(resp.body?.string().orEmpty()) as? JsonObject
                    ?: throw IllegalStateException("Ungueltige Antwort der Mediathek")
                val results = ((root["result"] as? JsonObject)?.get("results") as? JsonArray).orEmpty()
                results.asSequence().mapNotNull { e ->
                    val o = e as? JsonObject ?: return@mapNotNull null
                    val url = o.s("url_video")?.takeIf { it.startsWith("http") } ?: return@mapNotNull null
                    MediathekItem(
                        id = o.s("id") ?: url,
                        channel = o.s("channel").orEmpty(),
                        topic = o.s("topic").orEmpty(),
                        title = o.s("title").orEmpty(),
                        description = o.s("description")?.trim()?.takeIf { it.isNotEmpty() },
                        timestamp = o.s("timestamp")?.toLongOrNull() ?: 0L,
                        duration = o.s("duration")?.toLongOrNull() ?: 0L,
                        url = url,
                        urlHd = o.s("url_video_hd")?.takeIf { it.startsWith("http") },
                        urlLow = o.s("url_video_low")?.takeIf { it.startsWith("http") },
                        website = o.s("url_website"),
                    )
                }
                    // Spezialfassungen (Audiodeskription, Gebaerdensprache, Hoerfassung) und doppelte Eintraege weg
                    .filterNot { SPECIAL.containsMatchIn(it.title) }
                    .distinctBy { "${it.channel}|${it.title}|${it.duration / 60}" }
                    .toList()
            }
        }

    private companion object {
        val SPECIAL = Regex("Audiodeskription|Gebärdensprache|Hörfassung|\\(mit Untertitel\\)|klare Sprache", RegexOption.IGNORE_CASE)
    }

    private fun JsonObject.s(k: String) = (this[k] as? JsonPrimitive)?.content?.takeIf { it != "null" }
}

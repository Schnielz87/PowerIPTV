package com.poweriptv.app.ai

import com.poweriptv.app.data.ContentItem
import com.poweriptv.app.data.ContentSource
import com.poweriptv.app.data.ContentType
import com.poweriptv.app.data.SecureStore
import com.poweriptv.app.data.SettingsRepository
import com.poweriptv.app.parental.ParentalControl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

data class Recommendation(
    val title: String,
    val type: ContentType,
    val reason: String,
    /** Passender Eintrag im Katalog des Anbieters (direkt abspielbar). */
    val item: ContentItem?,
)

/**
 * Personalisierte Empfehlungen ueber die ChatGPT/OpenAI-API (Chat Completions).
 * Der API-Schluessel wird verschluesselt gespeichert. Es werden nur Titel
 * (Verlauf, Favoriten, Katalog-Auszug) uebertragen – keine Zugangsdaten.
 */
class AiRecommender(
    private val http: () -> OkHttpClient,
    private val secure: SecureStore,
    private val settings: SettingsRepository,
    private val parental: ParentalControl,
    private val json: Json,
) {
    fun apiKey(): String? = secure.read(KEY_FILE)
    fun hasApiKey() = !apiKey().isNullOrBlank()
    fun setApiKey(key: String?) = secure.write(KEY_FILE, key?.trim()?.ifBlank { null })

    /** Prueft den Schluessel mit einer Mini-Anfrage. */
    suspend fun testConnection(): String = withContext(Dispatchers.IO) {
        val content = chat(
            system = "Antworte nur mit einem JSON-Objekt.",
            user = "Gib {\"ok\": true} zurueck.",
        )
        if (content.contains("ok")) "Verbindung erfolgreich (${settings.aiModel.value})" else "Unerwartete Antwort"
    }

    suspend fun recommend(
        source: ContentSource,
        history: List<ContentItem>,
        favorites: List<ContentItem>,
        wish: String,
    ): List<Recommendation> = withContext(Dispatchers.IO) {
        if (!hasApiKey()) throw IOException("Kein ChatGPT-API-Schluessel hinterlegt (Einstellungen → KI-Empfehlungen).")

        // Katalog (ohne kindergesicherte Kategorien)
        val catalog = mutableListOf<ContentItem>()
        for (type in listOf(ContentType.MOVIE, ContentType.SERIES)) {
            val cats = runCatching { source.categories(type) }.getOrDefault(emptyList())
            val locked = parental.lockedIds(source.profile.id, type, cats)
            catalog += runCatching { source.items(type, null) }.getOrDefault(emptyList()).filterNot { it.categoryId in locked }
        }
        if (catalog.isEmpty()) throw IOException("Keine Filme oder Serien im Katalog gefunden.")

        val seen = history.map { it.key }.toSet()
        val sample = catalog.filterNot { it.key in seen }.shuffled().take(CATALOG_SAMPLE)

        fun label(i: ContentItem) = "${i.name} [${if (i.type == ContentType.SERIES) "Serie" else if (i.type == ContentType.LIVE) "Live" else "Film"}]"
        val user = buildString {
            appendLine("Zuletzt gesehen: ${history.take(25).joinToString("; ") { label(it) }.ifBlank { "nichts" }}")
            appendLine("Favoriten: ${favorites.take(25).joinToString("; ") { label(it) }.ifBlank { "keine" }}")
            if (wish.isNotBlank()) appendLine("Aktueller Wunsch des Nutzers: $wish")
            appendLine()
            appendLine("Verfuegbarer Katalog (nur daraus waehlen):")
            sample.forEach { appendLine("- ${label(it)}") }
        }
        val system = """
            Du bist ein persoenlicher Film- und Serien-Berater in einer IPTV-App.
            Empfiehl 10 Titel, die zum Geschmack des Nutzers passen. Waehle AUSSCHLIESSLICH Titel aus dem Katalog
            und uebernimm den Titel exakt. Begruende jede Empfehlung kurz auf Deutsch (max. 20 Woerter).
            Antworte nur als JSON: {"recommendations":[{"title":"...","type":"movie|series","reason":"..."}]}
        """.trimIndent()

        val content = chat(system, user)
        val root = runCatching { json.parseToJsonElement(content).jsonObject }.getOrNull()
            ?: throw IOException("Antwort der KI konnte nicht gelesen werden")
        val recs = (root["recommendations"] as? JsonArray).orEmpty()

        val byName = catalog.associateBy { normalize(it.name) }
        recs.mapNotNull { e ->
            val o = e as? JsonObject ?: return@mapNotNull null
            val title = (o["title"] as? JsonPrimitive)?.content ?: return@mapNotNull null
            val type = if ((o["type"] as? JsonPrimitive)?.content == "series") ContentType.SERIES else ContentType.MOVIE
            val n = normalize(title.replace(Regex("""\s*\[(Film|Serie|Live)]\s*$"""), ""))
            val match = byName[n] ?: catalog.firstOrNull { normalize(it.name).contains(n) || n.contains(normalize(it.name)) }
            Recommendation(title, match?.type ?: type, (o["reason"] as? JsonPrimitive)?.content.orEmpty(), match)
        }
    }

    private fun chat(system: String, user: String): String {
        val body = buildJsonObject {
            put("model", settings.aiModel.value)
            put("temperature", 0.7)
            putJsonObject("response_format") { put("type", "json_object") }
            putJsonArray("messages") {
                addJsonObject { put("role", "system"); put("content", system) }
                addJsonObject { put("role", "user"); put("content", user) }
            }
        }.toString()
        val req = Request.Builder()
            .url("${settings.aiBaseUrl.value}/chat/completions")
            .header("Authorization", "Bearer ${apiKey()}")
            .post(body.toRequestBody("application/json".toMediaType()))
            .build()
        http().newCall(req).execute().use { resp ->
            val text = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                val msg = runCatching {
                    json.parseToJsonElement(text).jsonObject["error"]?.jsonObject?.get("message")?.jsonPrimitive?.content
                }.getOrNull()
                throw IOException(
                    when (resp.code) {
                        401 -> "API-Schluessel ungueltig"
                        429 -> "Limit erreicht oder kein Guthaben: ${msg ?: ""}"
                        else -> "KI-Anfrage fehlgeschlagen (HTTP ${resp.code}) ${msg ?: ""}"
                    }.trim()
                )
            }
            return json.parseToJsonElement(text).jsonObject["choices"]!!.jsonArray[0]
                .jsonObject["message"]!!.jsonObject["content"]!!.jsonPrimitive.content
        }
    }

    companion object {
        private const val KEY_FILE = "openai_api_key"
        private const val CATALOG_SAMPLE = 350
        private fun normalize(s: String) = s.lowercase().replace(Regex("[^\\p{L}\\p{N}]"), "")
    }
}

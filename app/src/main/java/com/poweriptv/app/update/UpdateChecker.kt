package com.poweriptv.app.update

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException

/** Eine veroeffentlichte Version auf GitHub (Releases). */
data class ReleaseInfo(
    val tag: String,
    /** Build-Nummer aus "v1.1.<Build>". */
    val build: Int,
    val title: String,
    val notes: String,
    val pageUrl: String,
    val publishedAt: String?,
    /** Dateiname -> Download-Link (APK, Windows-Setup …). */
    val assets: Map<String, String>,
)

/**
 * Prueft auf GitHub, ob es eine neuere Version gibt, und laedt die passende Datei herunter.
 * Das Repository ist oeffentlich – dafuer ist kein GitHub-Konto noetig.
 * Geteilt zwischen Android-App und Windows-App (Windows/).
 */
class UpdateChecker(private val http: () -> OkHttpClient, private val json: Json) {

    suspend fun latest(): ReleaseInfo = withContext(Dispatchers.IO) {
        val req = Request.Builder().url("https://api.github.com/repos/$REPO/releases/latest")
            .header("Accept", "application/vnd.github+json").build()
        http().newCall(req).execute().use { resp ->
            if (resp.code == 403 || resp.code == 429) throw IOException("GitHub ist gerade ausgelastet – bitte später erneut versuchen")
            if (!resp.isSuccessful) throw IOException("Update-Prüfung fehlgeschlagen (HTTP ${resp.code})")
            val o = json.parseToJsonElement(resp.body?.string().orEmpty()).jsonObject
            val tag = o.s("tag_name") ?: throw IOException("Keine Version gefunden")
            ReleaseInfo(
                tag = tag,
                build = buildOf(tag) ?: 0,
                title = o.s("name") ?: tag,
                notes = o.s("body").orEmpty(),
                pageUrl = o.s("html_url") ?: "https://github.com/$REPO/releases/latest",
                publishedAt = o.s("published_at"),
                assets = (o["assets"] as? JsonArray).orEmpty().mapNotNull { a ->
                    val ao = a as? JsonObject ?: return@mapNotNull null
                    val name = ao.s("name") ?: return@mapNotNull null
                    val url = ao.s("browser_download_url") ?: return@mapNotNull null
                    name to url
                }.toMap(),
            )
        }
    }

    /** Laedt eine Datei herunter (mit Fortschritt). */
    suspend fun download(url: String, target: File, onProgress: (done: Long, total: Long) -> Unit) = withContext(Dispatchers.IO) {
        target.parentFile?.mkdirs()
        val tmp = File(target.parentFile, target.name + ".part")
        http().newCall(Request.Builder().url(url).build()).execute().use { resp ->
            if (!resp.isSuccessful) throw IOException("Download fehlgeschlagen (HTTP ${resp.code})")
            val body = resp.body ?: throw IOException("Leere Antwort")
            val total = body.contentLength()
            var done = 0L
            var last = 0L
            tmp.outputStream().buffered(256 * 1024).use { out ->
                val input = body.byteStream()
                val buf = ByteArray(256 * 1024)
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val n = input.read(buf)
                    if (n < 0) break
                    out.write(buf, 0, n)
                    done += n
                    if (done - last > 512 * 1024) { last = done; onProgress(done, total) }
                }
            }
            onProgress(done, total)
        }
        target.delete()
        if (!tmp.renameTo(target)) { tmp.copyTo(target, overwrite = true); tmp.delete() }
        target
    }

    private fun JsonObject.s(k: String) = (this[k] as? JsonPrimitive)?.takeIf { it.isString }?.content

    companion object {
        const val REPO = "Schnielz87/PowerIPTV"
        /** Alle 24 Stunden automatisch pruefen. */
        const val INTERVAL = 24 * 3600_000L

        /** "v1.1.73" / "1.1.73" -> 73 */
        fun buildOf(version: String?): Int? = version?.trim()?.removePrefix("v")?.split('.')?.getOrNull(2)?.takeWhile { it.isDigit() }?.toIntOrNull()
    }
}

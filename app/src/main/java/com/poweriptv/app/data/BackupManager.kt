package com.poweriptv.app.data

import android.content.Context
import android.net.Uri
import android.util.Base64
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.io.File
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Sichern & Wiederherstellen: Einstellungen, Favoriten/Listen, Verlauf, Weiterschauen,
 * Kindersicherung, Kategorien, Erinnerungen – optional auch Zugangsdaten (Profile, VPN, API-Schluessel).
 * Mit Passwort wird die Datei verschluesselt (AES-256-GCM, Schluessel per PBKDF2).
 */
class BackupManager(private val context: Context, private val secure: SecureStore, private val json: Json) {

    /** Passwort noetig? (Datei ist verschluesselt) */
    fun isEncrypted(text: String): Boolean =
        runCatching { (json.parseToJsonElement(text) as JsonObject)["encrypted"]?.jsonPrimitive?.booleanOrNull == true }.getOrDefault(false)

    fun export(uri: Uri, includeSecrets: Boolean, password: String?) {
        val data = buildJsonObject {
            put("prefs", JsonObject(prefsNames().associateWith { prefsToJson(it) }))
            if (includeSecrets) {
                val dir = File(context.filesDir, "secure")
                put("secure", JsonObject(dir.listFiles { f -> f.name.endsWith(".enc") }.orEmpty().mapNotNull { f ->
                    val name = f.name.removeSuffix(".enc")
                    secure.read(name)?.let { name to JsonPrimitive(it) }
                }.toMap()))
                // Lokale M3U-Dateien (bis 5 MB)
                val pl = File(context.filesDir, "playlists")
                put("playlists", JsonObject(pl.listFiles().orEmpty().filter { it.length() < 5_000_000 }.associate {
                    it.name to JsonPrimitive(Base64.encodeToString(it.readBytes(), Base64.NO_WRAP))
                }))
            }
        }.toString()
        val out = if (!password.isNullOrEmpty()) {
            val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, deriveKey(password, salt))
            val enc = cipher.doFinal(data.toByteArray(Charsets.UTF_8))
            buildJsonObject {
                put("app", "PowerIPTV"); put("version", 1); put("encrypted", true)
                put("salt", b64(salt)); put("iv", b64(cipher.iv)); put("data", b64(enc))
            }.toString()
        } else {
            buildJsonObject {
                put("app", "PowerIPTV"); put("version", 1); put("encrypted", false)
                put("data", json.parseToJsonElement(data))
            }.toString()
        }
        context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(out.toByteArray(Charsets.UTF_8)) }
            ?: error("Datei konnte nicht geschrieben werden")
    }

    fun readText(uri: Uri): String =
        context.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) } ?: error("Datei konnte nicht gelesen werden")

    /** Stellt das Backup wieder her. Danach muss die App neu starten. */
    fun restore(text: String, password: String?) {
        val root = json.parseToJsonElement(text).jsonObject
        require(root["app"]?.jsonPrimitive?.content == "PowerIPTV") { "Keine PowerIPTV-Sicherung" }
        val data: JsonObject = if (root["encrypted"]?.jsonPrimitive?.booleanOrNull == true) {
            require(!password.isNullOrEmpty()) { "Passwort erforderlich" }
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, deriveKey(password, unb64(root.s("salt"))), GCMParameterSpec(128, unb64(root.s("iv"))))
            val plain = runCatching { cipher.doFinal(unb64(root.s("data"))) }.getOrElse { error("Falsches Passwort") }
            json.parseToJsonElement(String(plain, Charsets.UTF_8)).jsonObject
        } else root["data"]!!.jsonObject

        data["prefs"]?.jsonObject?.forEach { (name, values) -> jsonToPrefs(name, values.jsonObject) }
        data["secure"]?.jsonObject?.forEach { (name, v) -> secure.write(name, v.jsonPrimitive.content) }
        data["playlists"]?.jsonObject?.forEach { (name, v) ->
            val dir = File(context.filesDir, "playlists").apply { mkdirs() }
            File(dir, File(name).name).writeBytes(Base64.decode(v.jsonPrimitive.content, Base64.NO_WRAP))
        }
    }

    // ---------- intern ----------

    /** Alle App-Einstellungsdateien (ohne Bibliotheks-Interna und Zwischenspeicher). */
    private fun prefsNames(): List<String> {
        val dir = File(context.applicationInfo.dataDir, "shared_prefs")
        return dir.listFiles { f -> f.name.endsWith(".xml") }.orEmpty().map { it.name.removeSuffix(".xml") }
            .filterNot { it.startsWith("androidx") || it.startsWith("com.google") || it.startsWith("WebView") || it == "playlist_refresh" }
    }

    private fun prefsToJson(name: String): JsonObject {
        val all = context.getSharedPreferences(name, Context.MODE_PRIVATE).all
        return JsonObject(all.mapNotNull { (k, v) ->
            val e: JsonElement = when (v) {
                is String -> buildJsonObject { put("t", "s"); put("v", v) }
                is Boolean -> buildJsonObject { put("t", "b"); put("v", v) }
                is Int -> buildJsonObject { put("t", "i"); put("v", v) }
                is Long -> buildJsonObject { put("t", "l"); put("v", v) }
                is Float -> buildJsonObject { put("t", "f"); put("v", v) }
                is Set<*> -> buildJsonObject { put("t", "set"); put("v", JsonArray(v.map { JsonPrimitive(it.toString()) })) }
                else -> return@mapNotNull null
            }
            k to e
        }.toMap())
    }

    private fun jsonToPrefs(name: String, values: JsonObject) {
        val ed = context.getSharedPreferences(name, Context.MODE_PRIVATE).edit()
        values.forEach { (k, e) ->
            val o = e.jsonObject
            val v = o["v"] ?: return@forEach
            when (o["t"]?.jsonPrimitive?.content) {
                "s" -> ed.putString(k, v.jsonPrimitive.content)
                "b" -> ed.putBoolean(k, v.jsonPrimitive.content.toBoolean())
                "i" -> ed.putInt(k, v.jsonPrimitive.content.toInt())
                "l" -> ed.putLong(k, v.jsonPrimitive.content.toLong())
                "f" -> ed.putFloat(k, v.jsonPrimitive.content.toFloat())
                "set" -> ed.putStringSet(k, v.jsonArray.map { it.jsonPrimitive.content }.toSet())
            }
        }
        ed.commit()
    }

    private fun deriveKey(password: String, salt: ByteArray): SecretKeySpec {
        val spec = PBEKeySpec(password.toCharArray(), salt, 60_000, 256)
        val raw = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1").generateSecret(spec).encoded
        return SecretKeySpec(raw, "AES")
    }

    private fun JsonObject.s(k: String) = this[k]!!.jsonPrimitive.content
    private fun b64(b: ByteArray) = Base64.encodeToString(b, Base64.NO_WRAP)
    private fun unb64(s: String) = Base64.decode(s, Base64.NO_WRAP)
}

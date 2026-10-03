package com.poweriptv.desktop.data

import com.poweriptv.app.data.KeyValueEditor
import com.poweriptv.app.data.KeyValueStore
import com.poweriptv.app.data.SecretStore
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import java.io.File

/** Geheimnisse (API-Schluessel, VPN-Konfiguration) – mit Windows-DPAPI verschluesselt. */
object DesktopSecretStore : SecretStore {
    private val dir: File get() = File(AppDirs.root, "secure").apply { mkdirs() }

    @Synchronized
    override fun read(name: String): String? {
        val f = File(dir, "$name.sec")
        if (!f.exists()) return null
        return runCatching { SecretBox.open(f.readText()) }.getOrNull()?.takeIf { it.isNotEmpty() }
    }

    @Synchronized
    override fun write(name: String, value: String?) {
        val f = File(dir, "$name.sec")
        if (value == null) f.delete() else f.writeText(SecretBox.seal(value))
    }

    fun names(): List<String> = dir.listFiles { f -> f.name.endsWith(".sec") }.orEmpty().map { it.name.removeSuffix(".sec") }
}

/** Schluessel-Wert-Speicher als JSON-Datei (Gegenstueck zu Android SharedPreferences). */
class JsonKeyValueStore(name: String) : KeyValueStore {
    private val file = AppDirs.file("$name.json")
    private val data: MutableMap<String, JsonElement> = runCatching {
        (AppJson.parseToJsonElement(file.readText()) as JsonObject).toMutableMap()
    }.getOrElse { mutableMapOf() }

    override fun contains(key: String) = synchronized(data) { key in data }
    override fun getBoolean(key: String, def: Boolean) = synchronized(data) { (data[key] as? JsonPrimitive)?.booleanOrNull ?: def }
    override fun getString(key: String, def: String?) = synchronized(data) { (data[key] as? JsonPrimitive)?.takeIf { it.isString }?.content ?: def }
    override fun getStringSet(key: String): Set<String> = synchronized(data) {
        (data[key] as? JsonArray)?.map { it.jsonPrimitive.content }?.toSet() ?: emptySet()
    }

    override fun edit(block: KeyValueEditor.() -> Unit) {
        synchronized(data) {
            object : KeyValueEditor {
                override fun putBoolean(key: String, value: Boolean) { data[key] = JsonPrimitive(value) }
                override fun putString(key: String, value: String?) { data[key] = if (value == null) JsonNull else JsonPrimitive(value) }
                override fun putStringSet(key: String, value: Set<String>) { data[key] = JsonArray(value.map { JsonPrimitive(it) }) }
                override fun remove(key: String) { data.remove(key) }
            }.block()
            save()
        }
    }

    override fun clear() = synchronized(data) { data.clear(); save() }

    /** Fuer Sicherung/Wiederherstellung. */
    fun snapshot(): JsonObject = synchronized(data) { JsonObject(data.toMap()) }

    private fun save() {
        runCatching { file.writeText(JsonObject(data).toString()) }
    }

    @Suppress("unused")
    private fun JsonElement.asArray() = jsonArray
}

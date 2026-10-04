package com.poweriptv.app.data

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

// Xtream-Server liefern Felder mal als String, mal als Zahl, mal als null/false.
// Diese Helfer lesen alles tolerant aus.

internal fun JsonObject.str(key: String): String? {
    val e = this[key] ?: return null
    if (e is JsonNull) return null
    if (e is JsonPrimitive) {
        val c = e.content
        return if (c.isBlank() || c == "null") null else c
    }
    if (e is JsonArray) return e.mapNotNull { (it as? JsonPrimitive)?.content }.joinToString(", ").ifBlank { null }
    return null
}

internal fun JsonObject.int(key: String): Int? = str(key)?.toDoubleOrNull()?.toInt()
internal fun JsonObject.long(key: String): Long? = str(key)?.toDoubleOrNull()?.toLong()
internal fun JsonObject.obj(key: String): JsonObject? = this[key] as? JsonObject
internal fun JsonElement?.asArray(): List<JsonObject> = (this as? JsonArray)?.mapNotNull { it as? JsonObject } ?: emptyList()

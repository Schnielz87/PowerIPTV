package com.poweriptv.desktop.data

import com.sun.jna.Platform
import com.sun.jna.platform.win32.Crypt32Util
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import java.io.File
import java.util.Base64

val AppJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    isLenient = true
}

/** Speicherort: %APPDATA%\Portiva (Windows), sonst ~/.portiva. */
object AppDirs {
    val root: File by lazy {
        val appData = System.getenv("APPDATA")
        val dir = if (Platform.isWindows() && !appData.isNullOrBlank()) File(appData, "Portiva")
        else File(System.getProperty("user.home"), ".portiva")
        dir.apply { mkdirs() }
    }
    val cache: File get() = File(root, "cache").apply { mkdirs() }
    fun file(name: String) = File(root, name)
}

/** Liest/schreibt einen Wert als JSON-Datei (atomar ueber Temp-Datei). */
class JsonFile<T>(private val file: File, private val serializer: KSerializer<T>, private val default: () -> T) {
    fun read(): T = runCatching {
        if (file.exists()) AppJson.decodeFromString(serializer, file.readText()) else default()
    }.getOrElse { default() }

    @Synchronized
    fun write(value: T) {
        runCatching {
            val tmp = File(file.parentFile, file.name + ".tmp")
            tmp.writeText(AppJson.encodeToString(serializer, value))
            if (!tmp.renameTo(file)) {
                file.delete(); tmp.renameTo(file)
            }
        }
    }
}

/**
 * Verschluesselt Passwoerter mit dem Windows-Datenschutz (DPAPI): nur dein Windows-Benutzerkonto
 * auf diesem PC kann sie wieder lesen. Auf anderen Systemen (nur Entwicklung) Klartext.
 */
object SecretBox {
    private const val PREFIX = "dpapi:"

    fun seal(plain: String): String {
        if (plain.isEmpty() || !Platform.isWindows()) return plain
        return runCatching {
            PREFIX + Base64.getEncoder().encodeToString(Crypt32Util.cryptProtectData(plain.toByteArray(Charsets.UTF_8)))
        }.getOrDefault(plain)
    }

    fun open(stored: String): String {
        if (!stored.startsWith(PREFIX)) return stored
        return runCatching {
            String(Crypt32Util.cryptUnprotectData(Base64.getDecoder().decode(stored.removePrefix(PREFIX))), Charsets.UTF_8)
        }.getOrDefault("")
    }
}

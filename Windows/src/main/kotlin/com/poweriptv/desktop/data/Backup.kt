package com.poweriptv.desktop.data

import com.poweriptv.app.data.ContentItem
import com.poweriptv.app.data.ContentType
import com.poweriptv.app.data.Profile
import com.poweriptv.app.data.ProfileType
import kotlinx.serialization.builtins.ListSerializer
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
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Sichern & Wiederherstellen – im selben Format wie die Android-App (PowerIPTV-Sicherung, AES-256-GCM/PBKDF2).
 * Eine Handy-Sicherung laesst sich am PC einspielen und umgekehrt: Zugaenge, Favoriten & Listen, Verlauf,
 * Weiterschauen/Gesehen, gelernte Intros, Kindersicherung, Kategorien, Einstellungen, API-Schluessel, VPN.
 */
class BackupManager(
    private val profiles: ProfileStore,
    private val settings: SettingsStore,
    private val categoryPrefs: CategoryPrefsStore,
    private val parentalStore: JsonKeyValueStore,
) {
    private val listSer = ListSerializer(ContentItem.serializer())

    fun isEncrypted(text: String) =
        runCatching { (AppJson.parseToJsonElement(text) as JsonObject)["encrypted"]?.jsonPrimitive?.booleanOrNull == true }.getOrDefault(false)

    // ---------------- Export ----------------

    fun export(target: File, includeSecrets: Boolean, password: String?) {
        val prefs = mutableMapOf<String, JsonElement>()
        val favLists = mutableMapOf<String, JsonElement>()
        val history = mutableMapOf<String, JsonElement>()
        val resume = mutableMapOf<String, JsonElement>()
        for (p in profiles.profiles.value) {
            val lib = LibraryStore(p.id)
            val lists = listOf(FavoriteList("default", "Favoriten", lib.favorites.value)) + lib.lists.value
            favLists[p.id] = str(AppJson.encodeToString(ListSerializer(FavoriteList.serializer()), lists))
            history[p.id] = str(AppJson.encodeToString(listSer, lib.history.value.map { it.item }))
            // Positionen/Gesehen: Android merkt sie an der Stream-URL
            val urls = UrlMapper(p)
            lib.rawPositions().forEach { (key, pos) ->
                val e = lib.history.value.firstOrNull { it.item.key == key || (it.episodeId != null && LibraryStore.episodeKey(it.episodeId) == key) }
                val url = urls.urlFor(key, e) ?: return@forEach
                resume[url] = typed("l", JsonPrimitive(pos))
                e?.duration?.takeIf { it > 0 }?.let { resume["$url|d"] = typed("l", JsonPrimitive(it)) }
            }
            lib.watched.value.forEach { key -> urls.urlFor(key, null)?.let { resume["w|$it"] = typed("b", JsonPrimitive(true)) } }
        }
        prefs["favorite_lists"] = JsonObject(favLists)
        prefs["history"] = JsonObject(history)
        prefs["resume"] = JsonObject(resume)
        prefs["parental"] = JsonObject(parentalStore.snapshot().mapValues { (_, v) ->
            when {
                v is JsonArray -> typed("set", v)
                v is JsonPrimitive && v.booleanOrNull != null -> typed("b", v)
                else -> typed("s", v)
            }
        })
        prefs["category_prefs"] = JsonObject(categoryPrefs.exportAndroid())
        val s = settings.value
        prefs["settings"] = JsonObject(
            mapOf(
                "category_language" to typed("s", JsonPrimitive(s.categoryLanguage)),
                "intro_sound" to typed("b", JsonPrimitive(s.introSound)),
                "live_format" to typed("s", JsonPrimitive(if (s.liveFormat == "m3u8") "HLS" else "TS")),
                "user_agent" to typed("s", JsonPrimitive(s.userAgent)),
                "subtitle_size" to typed("s", JsonPrimitive(s.subtitleSize)),
                "subtitle_bg" to typed("b", JsonPrimitive(s.subtitleBackground)),
                "scrub_preview" to typed("s", JsonPrimitive(s.scrubPreview)),
                "ai_model" to typed("s", JsonPrimitive(s.aiModel)),
                "ai_base_url" to typed("s", JsonPrimitive(s.aiBaseUrl)),
                "vpn_required" to typed("b", JsonPrimitive(s.vpnRequired)),
                "vpn_auto" to typed("b", JsonPrimitive(s.vpnAutoConnect)),
                "vpn_accept_external" to typed("b", JsonPrimitive(s.acceptExternalVpn)),
                "last_profile" to typed("s", JsonPrimitive(s.lastProfileId ?: "")),
            ),
        )
        val data = buildJsonObject {
            put("prefs", JsonObject(prefs))
            if (includeSecrets) {
                val secure = mutableMapOf<String, JsonElement>()
                secure["profiles"] = JsonPrimitive(AppJson.encodeToString(ListSerializer(Profile.serializer()), profiles.profiles.value))
                DesktopSecretStore.names().forEach { n -> DesktopSecretStore.read(n)?.let { secure[n] = JsonPrimitive(it) } }
                put("secure", JsonObject(secure))
                val pl = AppDirs.file("playlists")
                put("playlists", JsonObject(pl.listFiles().orEmpty().filter { it.length() < 5_000_000 }.associate {
                    it.name to JsonPrimitive(Base64.getEncoder().encodeToString(it.readBytes()))
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
                put("data", AppJson.parseToJsonElement(data))
            }.toString()
        }
        target.writeText(out)
    }

    // ---------------- Wiederherstellen ----------------

    /** Spielt eine Sicherung (vom Handy oder PC) ein. Liefert eine kurze Zusammenfassung. */
    fun restore(text: String, password: String?): String {
        val root = AppJson.parseToJsonElement(text).jsonObject
        require(root["app"]?.jsonPrimitive?.content == "PowerIPTV") { "Keine PowerIPTV-Sicherung" }
        val data: JsonObject = if (root["encrypted"]?.jsonPrimitive?.booleanOrNull == true) {
            require(!password.isNullOrEmpty()) { "Passwort erforderlich" }
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, deriveKey(password, unb64(root.s("salt"))), GCMParameterSpec(128, unb64(root.s("iv"))))
            val plain = runCatching { cipher.doFinal(unb64(root.s("data"))) }.getOrElse { error("Falsches Passwort") }
            AppJson.parseToJsonElement(String(plain, Charsets.UTF_8)).jsonObject
        } else root["data"]!!.jsonObject

        val done = mutableListOf<String>()
        // Lokale M3U-Dateien
        val plDir = AppDirs.file("playlists").apply { mkdirs() }
        data["playlists"]?.jsonObject?.forEach { (name, v) -> File(plDir, File(name).name).writeBytes(unb64(v.jsonPrimitive.content)) }
        // Geheimnisse + Zugaenge
        data["secure"]?.jsonObject?.forEach { (name, v) ->
            val value = v.jsonPrimitive.content
            if (name == "profiles") {
                runCatching { AppJson.decodeFromString(ListSerializer(Profile.serializer()), value) }.getOrNull()?.forEach { p ->
                    val fixed = if (p.type == ProfileType.M3U_FILE && p.localFile.isNotBlank()) p.copy(localFile = File(plDir, File(p.localFile.replace('\\', '/')).name).absolutePath) else p
                    profiles.save(fixed)
                }
                done += "Zugänge"
            } else DesktopSecretStore.write(name, value)
        }
        val prefs = data["prefs"]?.jsonObject ?: JsonObject(emptyMap())
        // Favoriten & Listen, Verlauf, Weiterschauen je Zugang
        val favs = prefs["favorite_lists"]?.jsonObject.orEmpty()
        val hist = prefs["history"]?.jsonObject.orEmpty()
        val resume = prefs["resume"]?.jsonObject.orEmpty()
        val profileIds = (favs.keys + hist.keys + profiles.profiles.value.map { it.id }).toSet()
        for (pid in profileIds) {
            val lib = LibraryStore(pid)
            favs[pid]?.let { e ->
                val lists = runCatching { AppJson.decodeFromString(ListSerializer(FavoriteList.serializer()), value(e)) }.getOrDefault(emptyList())
                lists.firstOrNull { it.id == "default" }?.items?.reversed()?.forEach { if (!lib.isFavorite(it)) lib.toggleFavorite(it) }
                lists.filterNot { it.id == "default" }.forEach { l ->
                    val id = lib.lists.value.firstOrNull { it.name == l.name }?.id ?: lib.createList(l.name)
                    l.items.reversed().forEach { if (!lib.isInList(id, it)) lib.toggleInList(id, it) }
                }
            }
            hist[pid]?.let { e ->
                runCatching { AppJson.decodeFromString(listSer, value(e)) }.getOrDefault(emptyList()).reversed()
                    .forEach { if (lib.history.value.none { h -> h.item.key == it.key }) lib.addHistory(WatchEntry(it)) }
            }
            // Positionen/Gesehen: URL -> Schluessel (Film/Folge)
            val pos = mutableMapOf<String, Long>()
            resume.forEach { (k, e) ->
                when {
                    k.startsWith("w|") -> UrlMapper.keyOf(k.removePrefix("w|"))?.let { lib.markWatched(it, true) }
                    k.startsWith("intro|") -> value(e).split(":").mapNotNull { it.toLongOrNull() }.takeIf { it.size == 2 }
                        ?.let { (a, b) -> lib.setIntro(k.removePrefix("intro|"), a, b) }
                    !k.contains('|') -> UrlMapper.keyOf(k)?.let { key -> value(e).toLongOrNull()?.let { pos[key] = it } }
                }
            }
            if (pos.isNotEmpty()) lib.importPositions(pos)
        }
        if (favs.isNotEmpty()) done += "Favoriten & Listen"
        if (hist.isNotEmpty()) done += "Verlauf"
        // Kindersicherung (gleiche Schluessel wie Android)
        prefs["parental"]?.jsonObject?.let { p ->
            parentalStore.edit {
                p.forEach { (k, e) ->
                    val o = e.jsonObject
                    when (o["t"]?.jsonPrimitive?.content) {
                        "b" -> putBoolean(k, o["v"]!!.jsonPrimitive.content.toBoolean())
                        "set" -> putStringSet(k, o["v"]!!.jsonArray.map { it.jsonPrimitive.content }.toSet())
                        else -> putString(k, o["v"]?.jsonPrimitive?.content)
                    }
                }
            }
            done += "Kindersicherung"
        }
        prefs["category_prefs"]?.jsonObject?.let { categoryPrefs.importAndroid(it.mapValues { (_, e) -> e.jsonObject }); done += "Kategorien" }
        prefs["settings"]?.jsonObject?.let { st ->
            fun sv(k: String) = st[k]?.jsonObject?.get("v")?.jsonPrimitive?.content
            settings.update { c ->
                c.copy(
                    categoryLanguage = sv("category_language") ?: c.categoryLanguage,
                    introSound = sv("intro_sound")?.toBooleanStrictOrNull() ?: c.introSound,
                    liveFormat = sv("live_format")?.let { if (it == "HLS") "m3u8" else "ts" } ?: c.liveFormat,
                    userAgent = sv("user_agent")?.takeIf { it.isNotBlank() && !it.contains("Android") } ?: c.userAgent,
                    subtitleSize = sv("subtitle_size") ?: c.subtitleSize,
                    subtitleBackground = sv("subtitle_bg")?.toBooleanStrictOrNull() ?: c.subtitleBackground,
                    scrubPreview = sv("scrub_preview") ?: c.scrubPreview,
                    aiModel = sv("ai_model") ?: c.aiModel,
                    aiBaseUrl = sv("ai_base_url") ?: c.aiBaseUrl,
                    vpnRequired = sv("vpn_required")?.toBooleanStrictOrNull() ?: c.vpnRequired,
                    vpnAutoConnect = sv("vpn_auto")?.toBooleanStrictOrNull() ?: c.vpnAutoConnect,
                    acceptExternalVpn = sv("vpn_accept_external")?.toBooleanStrictOrNull() ?: c.acceptExternalVpn,
                    lastProfileId = sv("last_profile")?.takeIf { it.isNotBlank() } ?: c.lastProfileId,
                )
            }
            done += "Einstellungen"
        }
        return done.distinct().joinToString(", ").ifBlank { "Nichts" }
    }

    // ---------------- intern ----------------

    /** Android-Format: {"t":"s","v":"..."} */
    private fun typed(t: String, v: JsonElement) = buildJsonObject { put("t", t); put("v", v) }
    private fun str(s: String) = typed("s", JsonPrimitive(s))
    private fun value(e: JsonElement): String = (e as? JsonObject)?.get("v")?.jsonPrimitive?.content ?: e.jsonPrimitive.content

    private fun deriveKey(password: String, salt: ByteArray): SecretKeySpec {
        val raw = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1").generateSecret(PBEKeySpec(password.toCharArray(), salt, 60_000, 256)).encoded
        return SecretKeySpec(raw, "AES")
    }

    private fun JsonObject.s(k: String) = this[k]!!.jsonPrimitive.content
    private fun b64(b: ByteArray) = Base64.getEncoder().encodeToString(b)
    private fun unb64(s: String) = Base64.getDecoder().decode(s)

    /** Uebersetzt zwischen Stream-URL (Android) und Film-/Folgen-Schluessel (Windows). */
    private class UrlMapper(private val p: Profile) {
        private val base = if (p.type == ProfileType.XTREAM) com.poweriptv.app.data.XtreamSource.normalizeServer(p.serverUrl) else null
        private fun enc(s: String) = java.net.URLEncoder.encode(s, "UTF-8").replace("+", "%20")

        fun urlFor(key: String, e: WatchEntry?): String? {
            val b = base ?: return null
            return when {
                key.startsWith("MOVIE:") -> "$b/movie/${enc(p.username)}/${enc(p.password)}/${key.removePrefix("MOVIE:")}.${e?.item?.containerExtension ?: "mp4"}"
                key.startsWith("EPISODE:") -> "$b/series/${enc(p.username)}/${enc(p.password)}/${key.removePrefix("EPISODE:")}.${e?.episodeExt ?: "mp4"}"
                else -> null
            }
        }

        companion object {
            private val re = Regex("""/(movie|series)/[^/]+/[^/]+/([^/.?]+)\.""")
            fun keyOf(url: String): String? = re.find(url)?.let { m ->
                if (m.groupValues[1] == "movie") "${ContentType.MOVIE.name}:${m.groupValues[2]}" else LibraryStore.episodeKey(m.groupValues[2])
            }
        }
    }
}

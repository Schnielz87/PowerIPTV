package com.poweriptv.app.player

/** Regeln fuer abgebrochene Streams – ohne Android-Abhaengigkeiten (auch in der Windows-App genutzt). */
object StreamRules {
    private val qualityTag = Regex("""(?i)(\b|_)(f?hd|uhd|4k|8k|hevc|h\.?265|1080[pi]?|720p|2160p|50fps|ᴴᴰ|ᶠᴴᴰ|ᵁᴴᴰ)(\b|_)|[\s*+]*\b(raw|backup)\b""")

    /** Sendername ohne Qualitaets-/Backup-Zusatz ("DE: Sky Sport 1 HD" und "DE: Sky Sport 1 RAW" -> gleich). */
    fun baseName(name: String) = qualityTag.replace(name, " ").replace(Regex("""[^\p{L}\p{Nd}]+"""), " ").trim().lowercase()

    private val xtreamLive = Regex("""(?i)(/live/[^/]+/[^/]+/[^/.?]+)\.(ts|m3u8)(\?.*)?$""")

    /** Xtream-Live-Adresse im jeweils anderen Format (.ts <-> .m3u8), sonst null. */
    fun altFormatUrl(url: String): String? = xtreamLive.find(url)?.let { m ->
        val ext = if (m.groupValues[2].equals("ts", ignoreCase = true)) "m3u8" else "ts"
        url.replaceRange(m.range, m.groupValues[1] + "." + ext + m.groupValues[3])
    }

    /** Hinweis, wenn der Zugang keine freie Verbindung hat (Xtream active_cons >= max_connections), sonst null. */
    fun connectionsFullText(active: String?, max: String?): String? {
        val m = max?.toIntOrNull() ?: return null
        val a = active?.toIntOrNull() ?: return null
        return if (m in 1..a) "Zugang wird gerade woanders genutzt ($a/$m Verbindungen belegt) – dort beenden oder kurz warten. Am Server liegt es nicht." else null
    }

    const val MSG_FORMAT = "Sender hakt – versuche es im anderen Format (HLS/TS) …"
    fun msgReconnect(n: Int) = "Verbindung unterbrochen – verbinde neu ($n/5) …"
    fun msgBackup(title: String) = "Sender fällt aus – wechsle auf „$title“"
}

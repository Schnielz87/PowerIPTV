package com.poweriptv.app.link

import com.poweriptv.app.data.Profile
import com.poweriptv.app.data.ProfileType
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.HttpURLConnection
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketTimeoutException
import java.net.URL
import java.security.SecureRandom
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/*
 * "Portiva Link": Geraete im Heimnetz finden, Zugaenge uebertragen und Wiedergabe weitergeben –
 * ohne Konto, ohne Cloud, ohne Chromecast. Geteilt zwischen Android und Windows (reines Kotlin/JVM);
 * die Samsung-TV-App spricht dasselbe Protokoll per XMLHttpRequest.
 *
 * Empfaenger (Android/Windows) betreiben einen kleinen HTTP-Dienst auf Port 47800 (TCP):
 *   GET  /portiva/hello            -> wer bin ich (Name, Plattform)
 *   POST /portiva/play             -> Film/Sender hier weiterschauen (LinkPlay)
 *   POST /portiva/pair             -> Zugang uebernehmen, nur mit dem gerade angezeigten Code (LinkPair)
 *   GET  /portiva/offer/<code>     -> Zugang abholen (fuer Samsung-TV, der selbst keinen Dienst anbieten darf)
 * Gefunden wird per UDP-Rundruf (Port 47801) und zusaetzlich per Suche im eigenen Netz (/24).
 */

const val LINK_PORT = 47800
const val LINK_UDP_PORT = 47801
private const val DISCOVER_MSG = "PORTIVA_DISCOVER"

@Serializable
data class LinkAccount(
    val name: String,
    val type: String,
    val serverUrl: String = "",
    val username: String = "",
    val password: String = "",
    val m3uUrl: String = "",
    val epgUrl: String = "",
)

@Serializable
data class LinkHello(
    val app: String = "portiva",
    val name: String,
    val platform: String,
    val port: Int,
    val receive: Boolean = true,
)

@Serializable
data class LinkPlay(
    val title: String,
    val url: String,
    val live: Boolean,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val logo: String? = null,
    val from: String = "",
)

@Serializable
data class LinkPair(val code: String, val account: LinkAccount, val from: String = "")

data class LinkDevice(val name: String, val platform: String, val host: String, val port: Int)

/** Inhalt der QR-Codes. */
object LinkCodes {
    private const val ACCOUNT_PREFIX = "PORTIVA1:"
    private const val PAIR_PREFIX = "PORTIVA-PAIR:"
    private val random = SecureRandom()
    val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    /** Lokale Dateien (M3U-Datei) lassen sich nicht uebertragen. */
    fun canTransfer(p: Profile) = p.type != ProfileType.M3U_FILE

    fun toAccount(p: Profile) = LinkAccount(p.name, p.type.name, p.serverUrl, p.username, p.password, p.m3uUrl, p.epgUrl)

    fun toProfile(a: LinkAccount, id: String) = Profile(
        id = id, name = a.name.ifBlank { "Zugang" },
        type = runCatching { ProfileType.valueOf(a.type) }.getOrDefault(if (a.m3uUrl.isNotBlank()) ProfileType.M3U_URL else ProfileType.XTREAM),
        serverUrl = a.serverUrl, username = a.username, password = a.password, m3uUrl = a.m3uUrl, epgUrl = a.epgUrl,
    )

    /** QR eines Zugangs (zum Scannen mit Handy/Tablet). */
    fun accountQr(a: LinkAccount): String = ACCOUNT_PREFIX + base64UrlEncode(json.encodeToString(a).toByteArray(Charsets.UTF_8))

    fun parseAccountQr(text: String): LinkAccount? {
        val t = text.trim()
        if (!t.startsWith(ACCOUNT_PREFIX)) return null
        return runCatching { json.decodeFromString<LinkAccount>(String(base64UrlDecode(t.removePrefix(ACCOUNT_PREFIX)), Charsets.UTF_8)) }.getOrNull()
    }

    /** QR des empfangenden Geraets: Adresse + Einmal-Code. port = 0 -> Geraet holt selbst ab (Samsung-TV). */
    fun pairQr(ip: String, port: Int, code: String) = "$PAIR_PREFIX$ip:$port:$code"

    data class PairTarget(val host: String, val port: Int, val code: String)

    fun parsePairQr(text: String): PairTarget? {
        val t = text.trim()
        if (!t.startsWith(PAIR_PREFIX)) return null
        val parts = t.removePrefix(PAIR_PREFIX).split(":")
        if (parts.size != 3) return null
        val port = parts[1].toIntOrNull() ?: return null
        return PairTarget(parts[0], port, parts[2])
    }

    fun isPortivaQr(text: String) = text.trim().let { it.startsWith(ACCOUNT_PREFIX) || it.startsWith(PAIR_PREFIX) }

    fun newCode(): String = (100000 + random.nextInt(900000)).toString()

    /** IPv4-Adresse im Heimnetz (WLAN/LAN). */
    fun localIpv4(): String? = runCatching {
        NetworkInterface.getNetworkInterfaces().toList()
            .filter { it.isUp && !it.isLoopback && !it.isVirtual }
            .sortedBy { n -> val nm = n.name.lowercase(); if (nm.startsWith("wlan") || nm.startsWith("eth") || nm.startsWith("en") || nm.startsWith("wi")) 0 else 1 }
            .flatMap { it.inetAddresses.toList() }
            .filterIsInstance<Inet4Address>()
            .firstOrNull { it.isSiteLocalAddress }?.hostAddress
    }.getOrNull()

    private const val B64 = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_"

    fun base64UrlEncode(data: ByteArray): String {
        val sb = StringBuilder()
        var i = 0
        while (i < data.size) {
            val b0 = data[i].toInt() and 0xff
            val b1 = if (i + 1 < data.size) data[i + 1].toInt() and 0xff else -1
            val b2 = if (i + 2 < data.size) data[i + 2].toInt() and 0xff else -1
            sb.append(B64[b0 shr 2])
            sb.append(B64[((b0 and 3) shl 4) or (if (b1 >= 0) b1 shr 4 else 0)])
            if (b1 >= 0) sb.append(B64[((b1 and 15) shl 2) or (if (b2 >= 0) b2 shr 6 else 0)])
            if (b2 >= 0) sb.append(B64[b2 and 63])
            i += 3
        }
        return sb.toString()
    }

    fun base64UrlDecode(s: String): ByteArray {
        val clean = s.trim().replace('+', '-').replace('/', '_').trimEnd('=')
        val out = ByteArrayOutputStream()
        var buf = 0
        var bits = 0
        for (c in clean) {
            val v = B64.indexOf(c)
            if (v < 0) continue
            buf = (buf shl 6) or v
            bits += 6
            if (bits >= 8) {
                bits -= 8
                out.write((buf shr bits) and 0xff)
            }
        }
        return out.toByteArray()
    }
}

/**
 * Empfangs-Dienst (Android/Windows). Laeuft im Hintergrund, solange die App offen ist.
 * onPlay/onPair werden auf einem Hintergrund-Thread aufgerufen und liefern true bei Erfolg.
 */
class LinkService(
    private val deviceName: () -> String,
    private val platform: String,
    private val onPlay: (LinkPlay) -> Boolean,
    private val onPair: (LinkPair) -> Boolean,
) {
    private val json = LinkCodes.json
    @Volatile var port: Int = 0
        private set
    @Volatile private var server: ServerSocket? = null
    @Volatile private var udp: DatagramSocket? = null
    private val pool = Executors.newCachedThreadPool { r -> Thread(r, "portiva-link").apply { isDaemon = true } }
    /** Zugaenge zum Abholen (Samsung-TV): Code -> (Zugang, gueltig bis). */
    private val offers = ConcurrentHashMap<String, Pair<LinkAccount, Long>>()
    /** Wird aufgerufen, wenn ein angebotener Zugang abgeholt wurde. */
    @Volatile var onOfferTaken: ((LinkAccount) -> Unit)? = null

    fun start() {
        if (server != null) return
        pool.execute {
            for (p in LINK_PORT..LINK_PORT + 5) {
                if (p == LINK_UDP_PORT) continue
                val s = runCatching { ServerSocket().apply { reuseAddress = true; bind(InetSocketAddress(p)) } }.getOrNull() ?: continue
                server = s; port = p
                break
            }
            val s = server ?: return@execute
            pool.execute(::udpLoop)
            while (!s.isClosed) {
                val c = runCatching { s.accept() }.getOrNull() ?: break
                pool.execute { handle(c) }
            }
        }
    }

    fun stop() {
        runCatching { server?.close() }; server = null
        runCatching { udp?.close() }; udp = null
    }

    fun hello() = LinkHello(name = deviceName(), platform = platform, port = port)

    /** Zugang zum Abholen bereitstellen (Samsung-TV holt ihn mit dem Code ab), 5 Minuten gueltig. */
    fun offer(code: String, account: LinkAccount) {
        offers[code] = account to System.currentTimeMillis() + 5 * 60_000L
    }

    private fun udpLoop() {
        val sock = runCatching { DatagramSocket(null as java.net.SocketAddress?).apply { reuseAddress = true; broadcast = true; bind(InetSocketAddress(LINK_UDP_PORT)) } }.getOrNull() ?: return
        udp = sock
        val buf = ByteArray(512)
        while (!sock.isClosed) {
            val pkt = DatagramPacket(buf, buf.size)
            runCatching { sock.receive(pkt) }.onFailure { if (sock.isClosed) return }
            if (String(pkt.data, 0, pkt.length, Charsets.UTF_8).trim() != DISCOVER_MSG) continue
            if (port == 0) continue
            val reply = json.encodeToString(hello()).toByteArray(Charsets.UTF_8)
            runCatching { sock.send(DatagramPacket(reply, reply.size, pkt.address, pkt.port)) }
        }
    }

    private fun handle(c: Socket) {
        c.use { sock ->
            sock.soTimeout = 8000
            val input = sock.getInputStream()
            val head = readHead(input) ?: return
            val lines = head.split("\r\n")
            val req = lines.first().split(" ")
            if (req.size < 2) return
            val method = req[0].uppercase()
            val path = req[1].substringBefore('?')
            val length = lines.drop(1).firstOrNull { it.lowercase().startsWith("content-length:") }
                ?.substringAfter(':')?.trim()?.toIntOrNull() ?: 0
            val body = if (length in 1..200_000) String(readExactly(input, length), Charsets.UTF_8) else ""
            val (code, text) = route(method, path, body)
            val bytes = text.toByteArray(Charsets.UTF_8)
            val out = sock.getOutputStream()
            out.write((
                "HTTP/1.1 $code ${if (code < 300) "OK" else "Error"}\r\n" +
                    "Content-Type: application/json; charset=utf-8\r\n" +
                    "Access-Control-Allow-Origin: *\r\n" +
                    "Access-Control-Allow-Methods: GET, POST, OPTIONS\r\n" +
                    "Access-Control-Allow-Headers: Content-Type\r\n" +
                    "Content-Length: ${bytes.size}\r\nConnection: close\r\n\r\n"
                ).toByteArray(Charsets.UTF_8))
            out.write(bytes)
            out.flush()
        }
    }

    private fun route(method: String, path: String, body: String): Pair<Int, String> {
        if (method == "OPTIONS") return 204 to ""
        return when {
            method == "GET" && path == "/portiva/hello" -> 200 to json.encodeToString(hello())
            method == "POST" && path == "/portiva/play" -> {
                val p = runCatching { json.decodeFromString<LinkPlay>(body) }.getOrNull() ?: return 400 to """{"ok":false}"""
                if (onPlay(p)) 200 to """{"ok":true}""" else 409 to """{"ok":false,"error":"Portiva ist auf dem Geraet nicht geoeffnet"}"""
            }
            method == "POST" && path == "/portiva/pair" -> {
                val p = runCatching { json.decodeFromString<LinkPair>(body) }.getOrNull() ?: return 400 to """{"ok":false}"""
                if (onPair(p)) 200 to """{"ok":true}""" else 403 to """{"ok":false,"error":"Code passt nicht"}"""
            }
            method == "GET" && path.startsWith("/portiva/offer/") -> {
                val code = path.removePrefix("/portiva/offer/")
                val o = offers[code]
                if (o == null || o.second < System.currentTimeMillis()) return 404 to """{"ok":false}"""
                offers.remove(code)
                onOfferTaken?.invoke(o.first)
                200 to json.encodeToString(o.first)
            }
            else -> 404 to """{"ok":false}"""
        }
    }

    private fun readHead(input: InputStream): String? {
        val buf = ByteArrayOutputStream()
        var last4 = 0
        while (buf.size() < 16_384) {
            val b = runCatching { input.read() }.getOrDefault(-1)
            if (b < 0) return null
            buf.write(b)
            last4 = (last4 shl 8) or b
            if (last4 == 0x0d0a0d0a) return String(buf.toByteArray(), Charsets.ISO_8859_1)
        }
        return null
    }

    private fun readExactly(input: InputStream, n: Int): ByteArray {
        val out = ByteArray(n)
        var off = 0
        while (off < n) {
            val r = runCatching { input.read(out, off, n - off) }.getOrDefault(-1)
            if (r < 0) break
            off += r
        }
        return out.copyOf(off)
    }
}

/** Sender-Seite: Geraete finden und Inhalte schicken (blockierend – im Hintergrund aufrufen). */
object LinkClient {
    private val json = LinkCodes.json

    /** Andere Portiva-Geraete im Heimnetz (UDP-Rundruf + Suche im eigenen /24-Netz). */
    fun discover(ownPort: Int = 0, timeoutMs: Int = 1800): List<LinkDevice> {
        val found = ConcurrentHashMap<String, LinkDevice>()
        val self = LinkCodes.localIpv4()
        val pool = Executors.newFixedThreadPool(48)
        // 1) UDP-Rundruf
        pool.execute {
            runCatching {
                DatagramSocket().use { s ->
                    s.broadcast = true
                    s.soTimeout = 300
                    val msg = DISCOVER_MSG.toByteArray(Charsets.UTF_8)
                    val targets = listOfNotNull(InetAddress.getByName("255.255.255.255"), self?.let { broadcastOf(it) })
                    repeat(2) { targets.forEach { t -> runCatching { s.send(DatagramPacket(msg, msg.size, t, LINK_UDP_PORT)) } } }
                    val end = System.currentTimeMillis() + timeoutMs
                    val buf = ByteArray(1024)
                    while (System.currentTimeMillis() < end) {
                        val pkt = DatagramPacket(buf, buf.size)
                        try { s.receive(pkt) } catch (e: SocketTimeoutException) { continue }
                        val h = runCatching { json.decodeFromString<LinkHello>(String(pkt.data, 0, pkt.length, Charsets.UTF_8)) }.getOrNull() ?: continue
                        val host = pkt.address.hostAddress ?: continue
                        if (host == self && h.port == ownPort) continue
                        found[host] = LinkDevice(h.name, h.platform, host, h.port)
                    }
                }
            }
        }
        // 2) Direkte Suche im eigenen Netz (falls der Router Rundrufe blockiert)
        if (self != null) {
            val prefix = self.substringBeforeLast('.')
            for (i in 1..254) {
                val host = "$prefix.$i"
                if (host == self) continue
                pool.execute { hello(host, LINK_PORT, 500)?.let { h -> found.putIfAbsent(host, LinkDevice(h.name, h.platform, host, h.port)) } }
            }
        }
        pool.shutdown()
        pool.awaitTermination(timeoutMs + 4000L, TimeUnit.MILLISECONDS)
        pool.shutdownNow()
        return found.values.filter { it.port > 0 }.sortedBy { it.name.lowercase() }
    }

    fun hello(host: String, port: Int, timeoutMs: Int = 1500): LinkHello? = runCatching {
        val c = URL("http://$host:$port/portiva/hello").openConnection() as HttpURLConnection
        c.connectTimeout = timeoutMs; c.readTimeout = timeoutMs
        c.useCaches = false
        try {
            if (c.responseCode != 200) null
            else json.decodeFromString<LinkHello>(c.inputStream.bufferedReader().readText()).takeIf { it.app == "portiva" }
        } finally { c.disconnect() }
    }.getOrNull()

    /** Liefert null bei Erfolg, sonst eine Fehlermeldung. */
    fun sendPlay(d: LinkDevice, play: LinkPlay): String? = post(d.host, d.port, "/portiva/play", json.encodeToString(play))

    fun sendPair(host: String, port: Int, pair: LinkPair): String? = post(host, port, "/portiva/pair", json.encodeToString(pair))

    private fun post(host: String, port: Int, path: String, body: String): String? = try {
        val c = URL("http://$host:$port$path").openConnection() as HttpURLConnection
        c.requestMethod = "POST"
        c.doOutput = true
        c.connectTimeout = 4000; c.readTimeout = 8000
        c.setRequestProperty("Content-Type", "application/json; charset=utf-8")
        c.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
        val code = c.responseCode
        val text = runCatching { (if (code < 300) c.inputStream else c.errorStream)?.bufferedReader()?.readText() }.getOrNull().orEmpty()
        c.disconnect()
        when {
            code in 200..299 -> null
            code == 403 -> "Der Code passt nicht mehr – bitte am anderen Gerät neu anzeigen lassen"
            code == 409 -> "Portiva ist auf dem anderen Gerät nicht geöffnet"
            else -> Regex("\"error\":\"([^\"]+)\"").find(text)?.groupValues?.get(1) ?: "Fehler $code"
        }
    } catch (e: Exception) {
        "Gerät nicht erreichbar (gleiches WLAN? Portiva dort geöffnet?)"
    }

    private fun broadcastOf(ip: String): InetAddress? = runCatching {
        NetworkInterface.getNetworkInterfaces().toList().flatMap { it.interfaceAddresses }
            .firstOrNull { it.address.hostAddress == ip }?.broadcast
    }.getOrNull()
}

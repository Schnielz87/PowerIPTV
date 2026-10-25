package com.poweriptv.app.link

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/*
 * Einrichtungs-Code: ein fertiges Paket aus Zugang (+ optional WireGuard-VPN), verschluesselt mit AES-256-GCM.
 * Gleiches Format in Android/Windows (dieses File) und iOS (SetupCode.swift).
 *
 *   Text  = "PORTIVA-SETUP1:" + base64url(Bytes)
 *   Bytes = Version(1) | Flags(1) | Salt(16) | Nonce(12) | Geheimtext+Tag
 *   Flags: Bit 0 = PIN noetig, Bit 1 = an eine Geraetenummer gebunden
 *   Schluessel = PBKDF2-HMAC-SHA256("PORTIVA-SETUP|" + PIN + "|" + Geraetenummer, Salt, 100000, 32 Byte)
 *   Version und Flags sind als "zusaetzliche Daten" mit abgesichert (Aendern -> Code ungueltig).
 *
 * Ohne PIN und ohne Geraetenummer kann jede Portiva-App den Code oeffnen – dann ist er nur gegen
 * neugieriges Mitlesen geschuetzt. Echten Schutz geben PIN (getrennt schicken!) und Geraetebindung.
 */

const val SETUP_PREFIX = "PORTIVA-SETUP1:"
private const val VERSION: Byte = 1
private const val FLAG_PIN = 1
private const val FLAG_DEVICE = 2
private const val ITERATIONS = 100_000

@Serializable
data class SetupPayload(
    val v: Int = 1,
    val account: LinkAccount,
    /** WireGuard-Konfiguration (.conf-Text) oder null. */
    val vpn: String? = null,
    /** Kill-Switch einschalten (ohne VPN laeuft nichts). */
    val vpnRequired: Boolean = false,
    /** Gueltig bis (ms seit 1970), 0 = unbegrenzt. */
    val exp: Long = 0,
    val created: Long = 0,
    /** Name des Erstellers (wird beim Einloesen angezeigt). */
    val from: String = "",
)

sealed class SetupResult {
    data class Ok(val payload: SetupPayload) : SetupResult()
    data class Error(val message: String) : SetupResult()
}

object SetupCode {
    private val random = SecureRandom()
    private const val ID_CHARS = "23456789ABCDEFGHJKMNPQRSTUVWXYZ"

    /** Neue Geraetenummer, z.B. "7KQ4-M2XD" (einmal erzeugen und speichern). */
    fun newDeviceId(): String {
        val s = (1..8).map { ID_CHARS[random.nextInt(ID_CHARS.length)] }.joinToString("")
        return s.substring(0, 4) + "-" + s.substring(4)
    }

    /** Fuer Vergleich/Schluessel: nur Grossbuchstaben und Ziffern ("7kq4 m2xd" == "7KQ4-M2XD"). */
    fun normalizeId(id: String?): String = id.orEmpty().uppercase().filter { it in 'A'..'Z' || it in '0'..'9' }

    fun isSetupCode(text: String?) = text?.trim()?.startsWith(SETUP_PREFIX) == true

    /** Was der Code verlangt (ohne ihn zu oeffnen). null = kein gueltiger Einrichtungs-Code. */
    data class Info(val needsPin: Boolean, val boundToDevice: Boolean)

    fun info(text: String): Info? {
        val b = bytes(text) ?: return null
        val f = b[1].toInt()
        return Info(f and FLAG_PIN != 0, f and FLAG_DEVICE != 0)
    }

    fun create(payload: SetupPayload, pin: String?, deviceId: String?): String {
        val p = pin?.trim().orEmpty()
        val d = normalizeId(deviceId)
        val flags = (if (p.isNotEmpty()) FLAG_PIN else 0) or (if (d.isNotEmpty()) FLAG_DEVICE else 0)
        val salt = ByteArray(16).also(random::nextBytes)
        val nonce = ByteArray(12).also(random::nextBytes)
        val header = byteArrayOf(VERSION, flags.toByte())
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key(p, d, salt), "AES"), GCMParameterSpec(128, nonce))
        cipher.updateAAD(header)
        val ct = cipher.doFinal(LinkCodes.json.encodeToString(payload).toByteArray(Charsets.UTF_8))
        return SETUP_PREFIX + LinkCodes.base64UrlEncode(header + salt + nonce + ct)
    }

    /** Code oeffnen. deviceId = Nummer des Geraets, auf dem der Zugang landen soll. */
    fun open(text: String, pin: String?, deviceId: String?, now: Long = System.currentTimeMillis()): SetupResult {
        val b = bytes(text) ?: return SetupResult.Error("Kein gültiger Einrichtungs-Code (unvollständig kopiert?).")
        val flags = b[1].toInt()
        val p = pin?.trim().orEmpty()
        if (flags and FLAG_PIN != 0 && p.isEmpty()) return SetupResult.Error("Für diesen Code wird die PIN benötigt.")
        val d = if (flags and FLAG_DEVICE != 0) normalizeId(deviceId) else ""
        val plain = runCatching {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key(if (flags and FLAG_PIN != 0) p else "", d, b.copyOfRange(2, 18)), "AES"),
                GCMParameterSpec(128, b.copyOfRange(18, 30)))
            cipher.updateAAD(b.copyOfRange(0, 2))
            cipher.doFinal(b.copyOfRange(30, b.size))
        }.getOrNull() ?: return SetupResult.Error(
            when {
                flags and FLAG_DEVICE != 0 && flags and FLAG_PIN != 0 -> "PIN falsch oder der Code ist für ein anderes Gerät bestimmt."
                flags and FLAG_DEVICE != 0 -> "Dieser Code ist für ein anderes Gerät bestimmt (Gerätenummer passt nicht)."
                flags and FLAG_PIN != 0 -> "Die PIN ist falsch."
                else -> "Der Code ist beschädigt."
            },
        )
        val payload = runCatching { LinkCodes.json.decodeFromString<SetupPayload>(String(plain, Charsets.UTF_8)) }.getOrNull()
            ?: return SetupResult.Error("Der Code ist beschädigt.")
        if (payload.exp in 1 until now) return SetupResult.Error("Dieser Einrichtungs-Code ist abgelaufen – bitte einen neuen anfordern.")
        if (!LinkSecurity.validAccount(payload.account)) return SetupResult.Error("Der Code enthält ungültige Daten.")
        return SetupResult.Ok(payload)
    }

    private fun bytes(text: String): ByteArray? {
        val t = text.trim().replace(Regex("\\s"), "")
        if (!t.startsWith(SETUP_PREFIX)) return null
        val b = runCatching { LinkCodes.base64UrlDecode(t.removePrefix(SETUP_PREFIX)) }.getOrNull() ?: return null
        if (b.size < 30 + 16 || b[0] != VERSION) return null
        return b
    }

    /** PBKDF2-HMAC-SHA256 (selbst gebaut: "PBKDF2WithHmacSHA256" gibt es erst ab Android 8). */
    private fun key(pin: String, device: String, salt: ByteArray): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec("PORTIVA-SETUP|$pin|$device".toByteArray(Charsets.UTF_8), "HmacSHA256"))
        mac.update(salt)
        mac.update(byteArrayOf(0, 0, 0, 1))
        var u = mac.doFinal()
        val out = u.copyOf()
        repeat(ITERATIONS - 1) {
            u = mac.doFinal(u)
            for (i in out.indices) out[i] = (out[i].toInt() xor u[i].toInt()).toByte()
        }
        return out
    }
}

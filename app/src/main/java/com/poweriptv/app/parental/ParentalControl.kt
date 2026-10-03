package com.poweriptv.app.parental

import android.content.Context
import com.poweriptv.app.data.Category
import com.poweriptv.app.data.ContentType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * Kindersicherung: PIN-geschuetzte Kategorien (manuell oder automatisch fuer
 * Erwachseneninhalte), optionaler Schutz der Einstellungen.
 * Nach korrekter PIN-Eingabe ist bis zum Neustart der App alles freigeschaltet.
 */
class ParentalControl(context: Context) {
    private val prefs = context.getSharedPreferences("parental", Context.MODE_PRIVATE)

    private val _enabled = MutableStateFlow(prefs.getBoolean(K_ENABLED, false) && hasPin())
    private val _autoAdult = MutableStateFlow(prefs.getBoolean(K_AUTO_ADULT, true))
    private val _protectSettings = MutableStateFlow(prefs.getBoolean(K_PROTECT_SETTINGS, true))
    private val _locked = MutableStateFlow(prefs.getStringSet(K_LOCKED, emptySet())!!.toSet())
    private val _unlocked = MutableStateFlow(false)

    val enabled: StateFlow<Boolean> = _enabled
    val autoAdult: StateFlow<Boolean> = _autoAdult
    val protectSettings: StateFlow<Boolean> = _protectSettings
    val lockedKeys: StateFlow<Set<String>> = _locked
    /** In dieser Sitzung per PIN freigeschaltet. */
    val sessionUnlocked: StateFlow<Boolean> = _unlocked

    fun hasPin() = prefs.contains(K_HASH)

    fun setPin(pin: String) {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }.joinToString("") { "%02x".format(it) }
        prefs.edit().putString(K_SALT, salt).putString(K_HASH, hash(salt, pin)).apply()
    }

    fun verify(pin: String): Boolean {
        val salt = prefs.getString(K_SALT, null) ?: return false
        val ok = hash(salt, pin) == prefs.getString(K_HASH, null)
        if (ok) _unlocked.value = true
        return ok
    }

    fun lockSession() { _unlocked.value = false }

    fun setEnabled(v: Boolean) {
        val value = v && hasPin()
        prefs.edit().putBoolean(K_ENABLED, value).apply(); _enabled.value = value
    }

    fun setAutoAdult(v: Boolean) { prefs.edit().putBoolean(K_AUTO_ADULT, v).apply(); _autoAdult.value = v }
    fun setProtectSettings(v: Boolean) { prefs.edit().putBoolean(K_PROTECT_SETTINGS, v).apply(); _protectSettings.value = v }

    /** Kindersicherung komplett entfernen (PIN loeschen). */
    fun reset() {
        prefs.edit().clear().apply()
        _enabled.value = false; _locked.value = emptySet(); _unlocked.value = false
        _autoAdult.value = true; _protectSettings.value = true
    }

    fun key(profileId: String, type: ContentType, categoryId: String) = "$profileId|${type.name}|$categoryId"

    /** Ist die Kategorie (unabhaengig von der Sitzungs-Freischaltung) gesperrt konfiguriert? */
    fun isConfiguredLocked(profileId: String, type: ContentType, category: Category): Boolean =
        key(profileId, type, category.id) in _locked.value || (_autoAdult.value && isAdult(category.name))

    /** Muss vor dem Anzeigen die PIN abgefragt werden? */
    fun requiresPin(profileId: String, type: ContentType, category: Category): Boolean =
        _enabled.value && !_unlocked.value && isConfiguredLocked(profileId, type, category)

    /** IDs der aktuell gesperrten Kategorien (fuer das Ausblenden in "Alle", Suche, EPG, Empfehlungen). */
    fun lockedIds(profileId: String, type: ContentType, categories: List<Category>): Set<String> {
        register(profileId, type, categories)
        return if (!_enabled.value || _unlocked.value) emptySet()
        else categories.filter { isConfiguredLocked(profileId, type, it) }.map { it.id }.toSet()
    }

    /** Erwachsenen-Kategorien merken (damit auch Einzeltitel aus Verlauf/Favoriten erkannt werden). */
    private val adultCats = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()

    fun register(profileId: String, type: ContentType, categories: List<Category>) {
        categories.forEach { if (isAdult(it.name)) adultCats.add(key(profileId, type, it.id)) }
    }

    /**
     * Ist dieser einzelne Titel/Sender gesperrt? Gilt ueberall – auch fuer "Zuletzt gesehen",
     * "Weiterschauen", Favoriten, Senderliste im Player und Erinnerungen.
     */
    fun isItemBlocked(profileId: String, item: com.poweriptv.app.data.ContentItem): Boolean {
        if (!_enabled.value || _unlocked.value) return false
        val k = key(profileId, item.type, item.categoryId)
        return k in _locked.value || (_autoAdult.value && (k in adultCats || isAdult(item.name)))
    }

    /** Liste ohne gesperrte Titel. */
    fun visible(profileId: String?, items: List<com.poweriptv.app.data.ContentItem>) =
        if (profileId == null || !_enabled.value || _unlocked.value) items else items.filterNot { isItemBlocked(profileId, it) }

    fun setLocked(profileId: String, type: ContentType, categoryId: String, locked: Boolean) {
        val k = key(profileId, type, categoryId)
        val updated = if (locked) _locked.value + k else _locked.value - k
        prefs.edit().putStringSet(K_LOCKED, updated).apply()
        _locked.value = updated
    }

    /** Sind Einstellungen gerade PIN-geschuetzt? */
    fun settingsNeedPin() = _enabled.value && _protectSettings.value && !_unlocked.value

    private fun hash(salt: String, pin: String): String =
        MessageDigest.getInstance("SHA-256").digest("$salt:$pin".toByteArray()).joinToString("") { "%02x".format(it) }

    companion object {
        private val adultRegex = Regex("""(?i)(xxx|adult|erotic|erotik|porn|\b18\s*\+|\+\s*18\b|for adults|nur für erwachsene)""")
        fun isAdult(name: String) = adultRegex.containsMatchIn(name)

        private const val K_ENABLED = "enabled"
        private const val K_AUTO_ADULT = "auto_adult"
        private const val K_PROTECT_SETTINGS = "protect_settings"
        private const val K_LOCKED = "locked"
        private const val K_SALT = "salt"
        private const val K_HASH = "hash"
    }
}

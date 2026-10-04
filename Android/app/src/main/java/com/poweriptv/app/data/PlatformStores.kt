package com.poweriptv.app.data

// Plattform-Schnittstellen – geteilt zwischen Android-App und Windows-App (Windows/).
// Android: SecureStore (Keystore) / SharedPrefsStore, Windows: DPAPI-Dateien / JSON-Datei.

/** Verschluesselter Speicher fuer Geheimnisse (Passwoerter, API-Schluessel, VPN-Konfiguration). */
interface SecretStore {
    fun read(name: String): String?
    fun write(name: String, value: String?)
}

/** Einfacher Schluessel-Wert-Speicher (wie Android SharedPreferences). */
interface KeyValueStore {
    fun contains(key: String): Boolean
    fun getBoolean(key: String, def: Boolean): Boolean
    fun getString(key: String, def: String?): String?
    fun getStringSet(key: String): Set<String>
    fun edit(block: KeyValueEditor.() -> Unit)
    fun clear()
}

interface KeyValueEditor {
    fun putBoolean(key: String, value: Boolean)
    fun putString(key: String, value: String?)
    fun putStringSet(key: String, value: Set<String>)
    fun remove(key: String)
}

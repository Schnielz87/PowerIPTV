package com.poweriptv.app.data

import android.content.SharedPreferences

/** Android-Umsetzung von [KeyValueStore] auf SharedPreferences. */
class SharedPrefsStore(private val prefs: SharedPreferences) : KeyValueStore {
    override fun contains(key: String) = prefs.contains(key)
    override fun getBoolean(key: String, def: Boolean) = prefs.getBoolean(key, def)
    override fun getString(key: String, def: String?) = prefs.getString(key, def)
    override fun getStringSet(key: String): Set<String> = prefs.getStringSet(key, emptySet()).orEmpty().toSet()
    override fun clear() = prefs.edit().clear().apply()
    override fun edit(block: KeyValueEditor.() -> Unit) {
        val ed = prefs.edit()
        object : KeyValueEditor {
            override fun putBoolean(key: String, value: Boolean) { ed.putBoolean(key, value) }
            override fun putString(key: String, value: String?) { ed.putString(key, value) }
            override fun putStringSet(key: String, value: Set<String>) { ed.putStringSet(key, value) }
            override fun remove(key: String) { ed.remove(key) }
        }.block()
        ed.apply()
    }
}

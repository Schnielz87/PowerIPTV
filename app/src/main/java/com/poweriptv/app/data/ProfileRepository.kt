package com.poweriptv.app.data

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID

class ProfileRepository(
    private val context: Context,
    private val secure: SecureStore,
    private val json: Json,
) {
    private val _profiles = MutableStateFlow(load())
    val profiles: StateFlow<List<Profile>> = _profiles

    private fun load(): List<Profile> = secure.read(FILE)?.let {
        runCatching { json.decodeFromString(ListSerializer(Profile.serializer()), it) }.getOrNull()
    } ?: emptyList()

    private fun persist(list: List<Profile>) {
        secure.write(FILE, json.encodeToString(ListSerializer(Profile.serializer()), list))
        _profiles.value = list
    }

    fun get(id: String?): Profile? = _profiles.value.firstOrNull { it.id == id }

    fun newId(): String = UUID.randomUUID().toString()

    fun save(profile: Profile) {
        val list = _profiles.value.toMutableList()
        val idx = list.indexOfFirst { it.id == profile.id }
        if (idx >= 0) list[idx] = profile else list.add(profile)
        persist(list)
    }

    fun delete(id: String) {
        get(id)?.localFile?.takeIf { it.isNotBlank() }?.let { File(it).delete() }
        persist(_profiles.value.filterNot { it.id == id })
    }

    /** Kopiert eine vom Nutzer gewaehlte M3U-Datei in den privaten App-Speicher. */
    fun importLocalFile(profileId: String, uri: Uri): String {
        val dir = File(context.filesDir, "playlists").apply { mkdirs() }
        val target = File(dir, "$profileId.m3u")
        context.contentResolver.openInputStream(uri)?.use { input ->
            target.outputStream().use { input.copyTo(it) }
        } ?: error("Datei konnte nicht gelesen werden")
        return target.absolutePath
    }

    companion object {
        private const val FILE = "profiles"
    }
}

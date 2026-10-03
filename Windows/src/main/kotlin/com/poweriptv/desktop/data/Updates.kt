package com.poweriptv.desktop.data

import com.poweriptv.app.update.ReleaseInfo
import com.poweriptv.app.update.UpdateChecker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

/**
 * Updates direkt von GitHub – wie in der Android-App (gleiche Pruefung): alle 24 h nachsehen,
 * neue Setup-Datei laden und starten. Der Installer ersetzt die alte Version, Daten bleiben erhalten.
 */
class DesktopUpdates(private val scope: CoroutineScope, currentVersion: String) {
    private val checker = UpdateChecker({ Http }, AppJson)
    private val store = JsonKeyValueStore("update")

    val currentVersion = currentVersion
    val currentBuild: Int = UpdateChecker.buildOf(currentVersion) ?: 0

    private val _available = MutableStateFlow<ReleaseInfo?>(null)
    val available: StateFlow<ReleaseInfo?> = _available.asStateFlow()
    private val _checking = MutableStateFlow(false)
    val checking: StateFlow<Boolean> = _checking.asStateFlow()
    private val _status = MutableStateFlow<String?>(null)
    val status: StateFlow<String?> = _status.asStateFlow()
    private val _progress = MutableStateFlow<Float?>(null)
    val progress: StateFlow<Float?> = _progress.asStateFlow()
    private val _autoCheck = MutableStateFlow(store.getBoolean("auto", true))
    val autoCheck: StateFlow<Boolean> = _autoCheck.asStateFlow()

    val lastCheck: Long get() = store.getString("last", null)?.toLongOrNull() ?: 0L
    fun dismissed(build: Int) = (store.getString("dismissed", null)?.toIntOrNull() ?: 0) >= build
    fun dismiss(build: Int) = store.edit { putString("dismissed", build.toString()) }
    fun setAutoCheck(v: Boolean) { store.edit { putBoolean("auto", v) }; _autoCheck.value = v }

    /** Wird nach erfolgreichem Download aufgerufen, damit Portiva sich fuer die Installation beendet. */
    var onReadyToInstall: (() -> Unit)? = null

    private var loop: Job? = null

    fun startAutoCheck() {
        if (loop?.isActive == true) return
        loop = scope.launch {
            while (true) {
                if (_autoCheck.value && currentBuild > 0 && System.currentTimeMillis() - lastCheck > UpdateChecker.INTERVAL) check(silent = true)
                delay(3600_000L)
            }
        }
    }

    /** Ein Klick: pruefen und – falls neuer – sofort laden und installieren (wie Android). */
    fun updateNow() {
        if (_available.value != null) { downloadAndInstall(); return }
        check(silent = false, installIfNew = true)
    }

    fun check(silent: Boolean = false, installIfNew: Boolean = false) {
        if (_checking.value) return
        _checking.value = true
        if (!silent) _status.value = "Suche nach Updates …"
        scope.launch {
            runCatching { checker.latest() }
                .onSuccess { r ->
                    store.edit { putString("last", System.currentTimeMillis().toString()) }
                    _available.value = r.takeIf { it.build > currentBuild && setupUrl(it) != null }
                    _status.value = if (_available.value != null) "Neue Version ${r.tag} verfügbar" else "Du hast die neueste Version ($currentVersion)"
                    if (installIfNew && _available.value != null) { _checking.value = false; downloadAndInstall() }
                }
                .onFailure { if (!silent) _status.value = it.message ?: "Update-Prüfung fehlgeschlagen" }
            _checking.value = false
        }
    }

    private fun setupUrl(r: ReleaseInfo) = r.assets["Portiva-Windows-Setup.exe"]
        ?: r.assets.entries.firstOrNull { it.key.startsWith("Portiva-Windows-Setup") && it.key.endsWith(".exe") }?.value

    fun downloadAndInstall() {
        val r = _available.value ?: return
        val url = setupUrl(r) ?: return
        if (_progress.value != null) return
        _progress.value = 0f
        _status.value = "Lade ${r.tag} …"
        scope.launch {
            val file = File(System.getProperty("java.io.tmpdir"), "Portiva-Update/Portiva-Windows-Setup-${r.tag}.exe")
            runCatching { checker.download(url, file) { done, total -> if (total > 0) _progress.value = done.toFloat() / total } }
                .onSuccess {
                    _progress.value = null
                    _status.value = "Installation wird gestartet …"
                    runCatching { ProcessBuilder(file.absolutePath).start() }
                        .onSuccess { onReadyToInstall?.invoke() }
                        .onFailure { e -> _status.value = "Installer konnte nicht gestartet werden: ${e.message}" }
                }
                .onFailure { _progress.value = null; _status.value = it.message ?: "Download fehlgeschlagen" }
        }
    }
}

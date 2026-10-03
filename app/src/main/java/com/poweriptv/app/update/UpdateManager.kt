package com.poweriptv.app.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import java.io.File

/**
 * Updates direkt von GitHub (ohne Play Store): alle 24 h pruefen, APK laden, Android-Installer oeffnen.
 * Die neue APK ist mit demselben Schluessel signiert – Zugaenge und Einstellungen bleiben erhalten.
 */
class UpdateManager(private val context: Context, http: () -> OkHttpClient, json: Json, private val scope: CoroutineScope) {
    private val checker = UpdateChecker(http, json)
    private val prefs = context.getSharedPreferences("update", Context.MODE_PRIVATE)

    val currentVersion: String = runCatching {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName
    }.getOrNull() ?: "?"
    val currentBuild: Int = UpdateChecker.buildOf(currentVersion) ?: 0

    private val _available = MutableStateFlow<ReleaseInfo?>(null)
    /** Neuere Version als die installierte (null = aktuell). */
    val available: StateFlow<ReleaseInfo?> = _available
    private val _checking = MutableStateFlow(false)
    val checking: StateFlow<Boolean> = _checking
    private val _status = MutableStateFlow<String?>(null)
    val status: StateFlow<String?> = _status
    private val _progress = MutableStateFlow<Float?>(null)
    /** Download-Fortschritt 0..1 (null = kein Download). */
    val progress: StateFlow<Float?> = _progress
    private val _autoCheck = MutableStateFlow(prefs.getBoolean("auto", true))
    val autoCheck: StateFlow<Boolean> = _autoCheck

    val lastCheck: Long get() = prefs.getLong("last", 0L)
    /** Hinweis fuer diese Version schon weggeklickt? */
    fun dismissed(build: Int) = prefs.getInt("dismissed", 0) >= build
    fun dismiss(build: Int) = prefs.edit().putInt("dismissed", build).apply()

    fun setAutoCheck(v: Boolean) { prefs.edit().putBoolean("auto", v).apply(); _autoCheck.value = v }

    private var loop: Job? = null

    /** Beim Start und danach stuendlich nachsehen, ob die 24 h um sind. */
    fun startAutoCheck() {
        if (loop?.isActive == true) return
        loop = scope.launch {
            while (true) {
                if (_autoCheck.value && System.currentTimeMillis() - lastCheck > UpdateChecker.INTERVAL) check(silent = true)
                delay(3600_000L)
            }
        }
    }

    fun check(silent: Boolean = false) {
        if (_checking.value) return
        _checking.value = true
        if (!silent) _status.value = "Suche nach Updates …"
        scope.launch {
            runCatching { checker.latest() }
                .onSuccess { r ->
                    prefs.edit().putLong("last", System.currentTimeMillis()).apply()
                    _available.value = r.takeIf { it.build > currentBuild && apkUrl(it) != null }
                    _status.value = if (_available.value != null) "Neue Version ${r.tag} verfügbar" else "Du hast die neueste Version ($currentVersion)"
                }
                .onFailure { if (!silent) _status.value = it.message ?: "Update-Prüfung fehlgeschlagen" }
            _checking.value = false
        }
    }

    private fun apkUrl(r: ReleaseInfo) = r.assets["PowerIPTV.apk"] ?: r.assets.entries.firstOrNull { it.key.endsWith(".apk") }?.value

    /** Darf die App Updates installieren? (Android 8+: „Unbekannte Apps installieren“ fuer Portiva) */
    fun canInstall(): Boolean = Build.VERSION.SDK_INT < 26 || context.packageManager.canRequestPackageInstalls()

    fun openInstallPermission() {
        if (Build.VERSION.SDK_INT >= 26) {
            context.startActivity(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }

    /** APK von GitHub laden und den Installer oeffnen. */
    fun downloadAndInstall() {
        val r = _available.value ?: return
        val url = apkUrl(r) ?: return
        if (_progress.value != null) return
        if (!canInstall()) { openInstallPermission(); _status.value = "Bitte „Unbekannte Apps installieren“ für Portiva erlauben und erneut tippen"; return }
        _progress.value = 0f
        _status.value = "Lade ${r.tag} …"
        scope.launch {
            val file = File(context.getExternalFilesDir(null) ?: context.cacheDir, "update/PowerIPTV-${r.tag}.apk")
            runCatching {
                checker.download(url, file) { done, total -> if (total > 0) _progress.value = done.toFloat() / total }
            }.onSuccess {
                _progress.value = null
                _status.value = "Installation wird geöffnet …"
                install(file)
            }.onFailure {
                _progress.value = null
                _status.value = it.message ?: "Download fehlgeschlagen"
            }
        }
    }

    private fun install(file: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.updates", file)
        context.startActivity(
            Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}

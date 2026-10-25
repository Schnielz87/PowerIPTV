package com.poweriptv.app.ui.components

import android.app.Activity
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * Fenster vor Bildschirmfotos, Bildschirmaufnahmen und der Vorschau in "Zuletzt verwendet" schuetzen,
 * solange dieser Bildschirm/Dialog offen ist (Zugangsdaten, Zugangs-QR, Passwort-Eingabe).
 * Das Flag wird sofort beim Aufbau gesetzt, damit auch Dialoge, die direkt danach entstehen, es uebernehmen.
 */
@Composable
fun SecureWindow() {
    val ctx = LocalContext.current
    val window = remember(ctx) {
        (ctx as? Activity)?.window?.also { it.addFlags(WindowManager.LayoutParams.FLAG_SECURE) }
    }
    DisposableEffect(window) {
        onDispose { window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE) }
    }
}

package com.poweriptv.app.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged

/**
 * Merkt sich je Seite, welche Kachel/Zeile zuletzt angewaehlt war. Kommt man mit "Zurueck" auf die Seite,
 * steht die Auswahl wieder genau dort (statt oben links auf dem Zurueck-Pfeil).
 */
class FocusMemory(initial: String?, private val save: (String) -> Unit) {
    /** Diese Kachel soll beim naechsten Erscheinen wieder angewaehlt werden (einmalig). */
    var pending: String? = initial
    fun focused(key: String) { pending = null; save(key) }
}

val LocalFocusMemory = staticCompositionLocalOf<FocusMemory?> { null }

/** Fuer jede Seite (Navigation) einmal drumherum – der Stand ueberlebt das Weiter- und Zurueckgehen. */
@Composable
fun ProvideFocusMemory(content: @Composable () -> Unit) {
    var last by rememberSaveable { mutableStateOf<String?>(null) }
    val mem = remember { FocusMemory(last) { last = it } }
    CompositionLocalProvider(LocalFocusMemory provides mem) { content() }
}

/** An Kacheln/Zeilen: merkt sich die Auswahl und stellt sie beim Zurueckkommen wieder her. Vor tvFocus/clickable setzen. */
fun Modifier.focusMemory(key: String?): Modifier = if (key == null) this else composed {
    val mem = LocalFocusMemory.current
    if (mem == null) this else {
        val fr = remember { FocusRequester() }
        LaunchedEffect(key) {
            if (mem.pending == key) {
                // kurz warten, bis die Seite steht (andere "erstes Element anwaehlen"-Effekte laufen vorher)
                kotlinx.coroutines.delay(120)
                if (mem.pending == key) { runCatching { fr.requestFocus() }; mem.pending = null }
            }
        }
        this.focusRequester(fr).onFocusChanged { if (it.isFocused) mem.focused(key) }
    }
}

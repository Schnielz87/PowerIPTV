package com.poweriptv.app.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

/**
 * Auswahl-Markierung fuer die Bedienung per Fernbedienung (Android TV, Fire TV, Gamepad):
 * nur ein feiner heller Rahmen – keine Groessenaenderung, nichts springt beim Wechseln.
 * Muss VOR clickable() in der Modifier-Kette stehen.
 *
 * [scaleFocused] wird aus Kompatibilitaet akzeptiert, aber bewusst nicht mehr verwendet.
 */
@Suppress("UNUSED_PARAMETER")
fun Modifier.tvFocus(
    shape: Shape = RoundedCornerShape(12.dp),
    scaleFocused: Float = 1f,
): Modifier = composed {
    var focused by remember { mutableStateOf(false) }
    this
        .onFocusChanged { focused = it.isFocused || it.hasFocus }
        .border(2.dp, if (focused) Color.White.copy(alpha = 0.9f) else Color.Transparent, shape)
}

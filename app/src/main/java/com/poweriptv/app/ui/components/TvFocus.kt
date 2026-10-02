package com.poweriptv.app.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import com.poweriptv.app.ui.theme.BrandCyan

/**
 * Deutlich sichtbarer Fokus fuer die Bedienung per Fernbedienung (Android TV, Fire TV,
 * Gamepad, Air-Mouse). Muss VOR clickable() in der Modifier-Kette stehen.
 */
fun Modifier.tvFocus(
    shape: Shape = RoundedCornerShape(12.dp),
    scaleFocused: Float = 1.05f,
): Modifier = composed {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) scaleFocused else 1f, label = "tvFocusScale")
    this
        .onFocusChanged { focused = it.isFocused || it.hasFocus }
        .scale(scale)
        // Weisser, dicker Rahmen + Cyan-Schein: auf dunklem und farbigem Hintergrund gut sichtbar
        .then(
            if (focused) Modifier
                .border(6.dp, BrandCyan.copy(alpha = 0.35f), shape)
                .border(3.dp, Color.White, shape)
            else Modifier
        )
}

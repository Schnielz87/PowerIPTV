package com.poweriptv.app.ui.components

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/**
 * "Portiva LINK"-Abzeichen: blauer Kreis, weisses Portiva-P mit Play-Dreieck, darunter LINK.
 * Farbig – mit tint = Color.Unspecified anzeigen. Wird von Android und Windows genutzt.
 */
object PortivaLinkIcon {
    /** Kurze Erklaerung fuer das i-Symbol im Senden-Fenster. */
    const val INFO =
        "Portiva Link schickt den laufenden Film oder Sender an ein anderes Portiva-Gerät im selben Netz " +
            "(TV-Stick, Handy, Tablet, PC oder Receiver). Dort läuft er an derselben Stelle weiter, hier wird gestoppt."

    val vector: ImageVector by lazy {
        val blue = Brush.linearGradient(
            listOf(Color(0xFF7AE0FF), Color(0xFF2A7BFF)),
            start = Offset(32f, 3f), end = Offset(32f, 61f),
        )
        ImageVector.Builder("PortivaLink", 64.dp, 64.dp, 64f, 64f).apply {
            // Kreis mit hellem Rand
            addPath(addPathNodes("M3,32 a29,29 0 1,0 58,0 a29,29 0 1,0 -58,0 z"), fill = blue)
            addPath(
                addPathNodes("M3,32 a29,29 0 1,0 58,0 a29,29 0 1,0 -58,0 z"),
                stroke = SolidColor(Color.White.copy(alpha = 0.35f)), strokeLineWidth = 2f,
            )
            // Portiva-P mit Play-Dreieck
            addPath(addPathNodes("M24,44 V14 h11 a9.5,9.5 0 0 1 0,19 h-5 v11 z"), fill = SolidColor(Color.White))
            addPath(addPathNodes("M30.5,19 l6.5,4.5 l-6.5,4.5 z"), fill = SolidColor(Color(0xFF2A7BFF)))
            // Schriftzug LINK (als Linien, damit es ohne Schriftart ueberall gleich aussieht)
            addPath(
                addPathNodes("M21,49 V56 H25.5 M28,49 V56 M31,56 V49 L35.5,56 V49 M38.5,49 V56 M43,49 L38.5,53 L43,56"),
                stroke = SolidColor(Color.White), strokeLineWidth = 2.4f,
                strokeLineCap = StrokeCap.Butt, strokeLineJoin = StrokeJoin.Bevel,
            )
        }.build()
    }
}

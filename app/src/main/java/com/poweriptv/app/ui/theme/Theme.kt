package com.poweriptv.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Portiva – PowerIPTV Farbwelt: tiefes Nachtblau mit Cyan-Blau-Violett-Akzenten
val Background = Color(0xFF07090F)
val Surface = Color(0xFF111623)
val SurfaceHigh = Color(0xFF1B2234)
val BrandCyan = Color(0xFF00D1FF)
val Accent = Color(0xFF1E8BFF)
val Accent2 = Color(0xFF7A3DFF)
val Success = Color(0xFF2EE59D)
val Danger = Color(0xFFFF4D5E)
val Warning = Color(0xFFFFC542)

val BrandGradient = Brush.linearGradient(listOf(BrandCyan, Color(0xFF1E6BFF), Accent2))

private val Colors = darkColorScheme(
    primary = Accent,
    onPrimary = Color.White,
    secondary = Accent2,
    tertiary = BrandCyan,
    background = Background,
    onBackground = Color.White,
    surface = Surface,
    onSurface = Color.White,
    surfaceVariant = SurfaceHigh,
    onSurfaceVariant = Color(0xFFA9B3CC),
    surfaceContainer = Surface,
    surfaceContainerHigh = SurfaceHigh,
    error = Danger,
)

private val base = Typography()
private val AppTypography = base.copy(
    headlineSmall = base.headlineSmall.copy(fontWeight = FontWeight.Bold),
    titleLarge = base.titleLarge.copy(fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp),
    labelSmall = TextStyle(fontSize = 11.sp, letterSpacing = 3.sp, fontWeight = FontWeight.SemiBold),
)

@Composable
fun PowerTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Colors, typography = AppTypography, content = content)
}

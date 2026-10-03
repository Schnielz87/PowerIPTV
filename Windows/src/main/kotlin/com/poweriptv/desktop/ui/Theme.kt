package com.poweriptv.desktop.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Portiva – PowerIPTV Farbwelt (aus dem Portiva-Logo): Hellblau → Blau → Navy auf dunklem Nachtblau
val Background = Color(0xFF060B16)
val Surface = Color(0xFF0E1726)
val SurfaceHigh = Color(0xFF172438)
val BrandCyan = Color(0xFF5EC4F2)   // Portiva Hellblau
val Accent = Color(0xFF3A9BDC)      // Portiva Blau
val Accent2 = Color(0xFF1B4F8A)     // Portiva Navy-Blau
val BrandNavy = Color(0xFF123D6E)
val Success = Color(0xFF2EE59D)
val Danger = Color(0xFFFF4D5E)
val Warning = Color(0xFFFFC542)

val BrandGradient = Brush.linearGradient(listOf(BrandCyan, Accent, BrandNavy))

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

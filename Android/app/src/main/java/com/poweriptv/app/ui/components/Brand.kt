package com.poweriptv.app.ui.components

import android.content.res.Configuration
import androidx.compose.animation.core.Animatable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.poweriptv.app.R
import com.poweriptv.app.ui.theme.Background
import com.poweriptv.app.ui.theme.BrandCyan
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** "Zur Startseite" – wird in AppNavigation gesetzt (null = keine Startseite, z.B. noch kein Zugang). */
val LocalGoHome = androidx.compose.runtime.staticCompositionLocalOf<(() -> Unit)?> { null }

/** Portiva-"P" oben links: ein Klick fuehrt von jeder Seite zur Startseite. */
@Composable
fun HomeLogoButton(size: androidx.compose.ui.unit.Dp = 34.dp) {
    val goHome = LocalGoHome.current ?: return
    Box(
        Modifier.padding(horizontal = 4.dp).size(size + 6.dp).clip(RoundedCornerShape(10.dp))
            .tvFocus(RoundedCornerShape(10.dp), 1.12f).clickable(onClickLabel = "Startseite") { goHome() },
        contentAlignment = Alignment.Center,
    ) { PortivaLogo(Modifier.size(size)) }
}

@Composable
fun PortivaLogo(modifier: Modifier = Modifier) {
    // Gleiches Logo wie das App-Icon (Film-Hintergrund + Portiva-P)
    Image(painterResource(R.drawable.app_logo), contentDescription = "Portiva", modifier = modifier)
}

/** Schriftzug im Portiva-Stil: "PORTIVA" + "PowerIPTV" (statt "Portable KI-Mitarbeiter-Plattform"). */
@Composable
fun BrandWordmark(large: Boolean = false, centered: Boolean = false) {
    Column(horizontalAlignment = if (centered) Alignment.CenterHorizontally else Alignment.Start) {
        Text(
            "PORTIVA",
            color = Color.White,
            fontWeight = FontWeight.Black,
            fontSize = if (large) 44.sp else 18.sp,
            letterSpacing = if (large) 4.sp else 1.5.sp,
            lineHeight = if (large) 48.sp else 20.sp,
        )
        Text(
            buildAnnotatedString {
                withStyle(SpanStyle(color = BrandCyan)) { append("Power") }
                withStyle(SpanStyle(color = Color.White)) { append("IPTV") }
            },
            fontWeight = FontWeight.SemiBold,
            fontSize = if (large) 22.sp else 12.sp,
            lineHeight = if (large) 26.sp else 14.sp,
        )
    }
}

/** Startbild: gross und leuchtend "PowerIPTV" (zweifarbig), darunter klein "by Portiva©". */
@Composable
fun SplashWordmark() {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            buildAnnotatedString {
                withStyle(SpanStyle(color = BrandCyan)) { append("Power") }
                withStyle(SpanStyle(color = Color.White)) { append("IPTV") }
            },
            fontWeight = FontWeight.Black,
            fontSize = 44.sp,
            letterSpacing = 2.sp,
            lineHeight = 48.sp,
            style = androidx.compose.ui.text.TextStyle(
                shadow = androidx.compose.ui.graphics.Shadow(BrandCyan.copy(alpha = 0.75f), androidx.compose.ui.geometry.Offset(0f, 0f), 28f),
            ),
        )
        Text(
            buildAnnotatedString {
                append("by Portiva")
                withStyle(SpanStyle(fontSize = 11.sp, baselineShift = androidx.compose.ui.text.style.BaselineShift.Superscript)) { append("©") }
            },
            color = Color.White.copy(alpha = 0.85f),
            fontWeight = FontWeight.SemiBold,
            fontSize = 18.sp,
            letterSpacing = 1.sp,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrandTopBar(
    subtitle: String? = null,
    /** Ersetzt den Schriftzug neben dem Logo (z.B. Benutzer-Schnellwechsel auf der Startseite). */
    titleContent: (@Composable () -> Unit)? = null,
    actions: @Composable () -> Unit = {},
) {
    TopAppBar(
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Mit Zugang: Logo fuehrt zur Startseite
                if (LocalGoHome.current != null) HomeLogoButton(38.dp) else PortivaLogo(Modifier.size(38.dp))
                Spacer(Modifier.width(10.dp))
                if (titleContent != null) titleContent()
                else Column {
                    BrandWordmark()
                    subtitle?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                    }
                }
            }
        },
        actions = { actions() },
        expandedHeight = if (LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE) 52.dp
        else TopAppBarDefaults.TopAppBarExpandedHeight,
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
    )
}

/** Kurzer Startbildschirm mit Logo-Animation. */
@Composable
fun SplashScreen(onFinished: () -> Unit, playSound: Boolean = true) {
    val scale = remember { Animatable(0.7f) }
    val alpha = remember { Animatable(0f) }
    val context = androidx.compose.ui.platform.LocalContext.current
    LaunchedEffect(Unit) {
        // Kino-Start-Klang: Anlauf (0-0,75 s), Schlag bei 0,75 s, Blechblaeser-Akkord; laeuft nach dem Splash aus
        if (playSound) runCatching {
            android.media.MediaPlayer.create(context.applicationContext, com.poweriptv.app.R.raw.intro_sound)?.apply {
                setOnCompletionListener { it.release() }
                start()
            }
        }
        // Logo blendet waehrend des Anlaufs leise ein und "springt" genau auf den Schlag
        alpha.animateTo(0.45f, tween(720))
        launch { alpha.animateTo(1f, tween(120)) }
        scale.animateTo(1.08f, tween(140))
        scale.animateTo(1f, tween(260))
        delay(1350)
        onFinished()
    }
    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.radialGradient(listOf(Color(0xFF0F2A4D), Background), radius = 1400f)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.alpha(alpha.value).scale(scale.value),
        ) {
            PortivaLogo(Modifier.size(140.dp))
            Spacer(Modifier.height(24.dp))
            SplashWordmark()
            Spacer(Modifier.height(8.dp))
            Text("Live TV · Filme · Serien – sicher per VPN", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        }
    }
}

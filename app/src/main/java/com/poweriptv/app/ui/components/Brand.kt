package com.poweriptv.app.ui.components

import android.content.res.Configuration
import androidx.compose.animation.core.Animatable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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

@Composable
fun PortivaLogo(modifier: Modifier = Modifier) {
    Image(painterResource(R.drawable.portiva_logo), contentDescription = "Portiva", modifier = modifier)
}

/** Schriftzug im Portiva-Stil: "PORTIVA" + "PowerIPTV" (statt "Portable KI-Mitarbeiter-Plattform"). */
@Composable
fun BrandWordmark(large: Boolean = false) {
    Column {
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrandTopBar(subtitle: String? = null, actions: @Composable () -> Unit = {}) {
    TopAppBar(
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                PortivaLogo(Modifier.size(38.dp))
                Spacer(Modifier.width(10.dp))
                Column {
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
fun SplashScreen(onFinished: () -> Unit) {
    val scale = remember { Animatable(0.7f) }
    val alpha = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        alpha.animateTo(1f, tween(450))
        scale.animateTo(1f, tween(450))
        delay(500)
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
            BrandWordmark(large = true)
            Spacer(Modifier.height(8.dp))
            Text("Live TV · Filme · Serien – sicher per VPN", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        }
    }
}

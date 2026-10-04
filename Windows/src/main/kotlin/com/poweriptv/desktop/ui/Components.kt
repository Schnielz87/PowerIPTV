package com.poweriptv.desktop.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ContextMenuArea
import androidx.compose.foundation.ContextMenuItem
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.poweriptv.app.data.ContentItem
import com.poweriptv.app.data.ContentType

@Suppress("DEPRECATION")
@Composable
fun PortivaLogo(modifier: Modifier = Modifier) {
    Image(painterResource("app_logo.xml"), contentDescription = "Portiva", modifier = modifier)
}

/** Schriftzug "PORTIVA" + "PowerIPTV" wie in der Android-App. */
@Composable
fun BrandWordmark(large: Boolean = false, centered: Boolean = false) {
    Column(horizontalAlignment = if (centered) Alignment.CenterHorizontally else Alignment.Start) {
        Text(
            "PORTIVA",
            color = Color.White,
            fontWeight = FontWeight.Black,
            fontSize = if (large) 52.sp else 18.sp,
            letterSpacing = if (large) 5.sp else 1.5.sp,
            lineHeight = if (large) 56.sp else 20.sp,
        )
        Text(
            buildAnnotatedString {
                withStyle(SpanStyle(color = BrandCyan)) { append("Power") }
                withStyle(SpanStyle(color = Color.White)) { append("IPTV") }
            },
            fontWeight = FontWeight.SemiBold,
            fontSize = if (large) 26.sp else 12.sp,
            lineHeight = if (large) 30.sp else 14.sp,
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
            fontSize = 52.sp,
            letterSpacing = 2.sp,
            lineHeight = 56.sp,
            style = androidx.compose.ui.text.TextStyle(
                shadow = androidx.compose.ui.graphics.Shadow(BrandCyan.copy(alpha = 0.75f), androidx.compose.ui.geometry.Offset(0f, 0f), 30f),
            ),
        )
        Text(
            buildAnnotatedString {
                append("by Portiva")
                withStyle(SpanStyle(fontSize = 12.sp, baselineShift = androidx.compose.ui.text.style.BaselineShift.Superscript)) { append("©") }
            },
            color = Color.White.copy(alpha = 0.85f),
            fontWeight = FontWeight.SemiBold,
            fontSize = 22.sp,
            letterSpacing = 1.sp,
        )
    }
}

fun Modifier.handCursor() = pointerHoverIcon(PointerIcon.Hand)

data class MenuAction(val label: String, val onClick: () -> Unit)

/** Poster-Karte fuer Filme und Serien (Hover-Zoom, Rechtsklick-Menue, Fortschritt, gesehen, Favorit). */
@Composable
fun PosterCard(
    item: ContentItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    progress: Float = 0f,
    watched: Boolean = false,
    favorite: Boolean = false,
    subtitle: String? = null,
    menu: List<MenuAction> = emptyList(),
) {
    val hover = remember { MutableInteractionSource() }
    val hovered by hover.collectIsHoveredAsState()
    val scale by animateFloatAsState(if (hovered) 1.05f else 1f)
    ContextMenuArea(items = { menu.map { ContextMenuItem(it.label, it.onClick) } }) {
        Column(modifier.hoverable(hover).handCursor().clickable(onClick = onClick)) {
            Box(
                Modifier.fillMaxWidth().aspectRatio(2f / 3f).scale(scale)
                    .clip(RoundedCornerShape(10.dp))
                    .background(SurfaceHigh)
                    .then(if (hovered) Modifier.border(2.dp, BrandCyan, RoundedCornerShape(10.dp)) else Modifier),
            ) {
                NetImage(item.logo, Modifier.fillMaxSize()) {
                    Icon(Icons.Filled.Movie, null, tint = Color.White.copy(alpha = 0.3f), modifier = Modifier.size(48.dp))
                }
                item.ratingValue?.let { r ->
                    Row(
                        Modifier.align(Alignment.TopStart).padding(6.dp).clip(RoundedCornerShape(6.dp))
                            .background(Color.Black.copy(alpha = 0.65f)).padding(horizontal = 6.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Filled.Star, null, tint = Warning, modifier = Modifier.size(12.dp))
                        Spacer(Modifier.width(3.dp))
                        Text("%.1f".format(r), fontSize = 11.sp, color = Color.White)
                    }
                }
                if (favorite) {
                    Icon(
                        Icons.Filled.Favorite, "Favorit", tint = Danger,
                        modifier = Modifier.align(Alignment.TopEnd).padding(6.dp).size(18.dp),
                    )
                }
                if (watched) WatchedBadge(Modifier.align(Alignment.BottomEnd).padding(6.dp))
                if (progress > 0f && !watched) {
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(4.dp),
                        color = BrandCyan,
                        trackColor = Color.Black.copy(alpha = 0.5f),
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(item.name, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
            (subtitle ?: item.year?.toString())?.let {
                Text(it, maxLines = 1, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
fun WatchedBadge(modifier: Modifier = Modifier) {
    Row(
        modifier.clip(RoundedCornerShape(6.dp)).background(Success.copy(alpha = 0.9f)).padding(horizontal = 6.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.CheckCircle, null, tint = Color.Black, modifier = Modifier.size(12.dp))
        Spacer(Modifier.width(3.dp))
        Text("Gesehen", fontSize = 11.sp, color = Color.Black, fontWeight = FontWeight.SemiBold)
    }
}

/** Sender-Kachel (Logo + Name) fuer Live TV. */
@Composable
fun ChannelCard(
    item: ContentItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    favorite: Boolean = false,
    selected: Boolean = false,
    subtitle: String? = null,
    menu: List<MenuAction> = emptyList(),
) {
    val hover = remember { MutableInteractionSource() }
    val hovered by hover.collectIsHoveredAsState()
    ContextMenuArea(items = { menu.map { ContextMenuItem(it.label, it.onClick) } }) {
        Row(
            modifier.clip(RoundedCornerShape(10.dp))
                .background(if (hovered || selected) SurfaceHigh else Surface)
                .then(if (hovered || selected) Modifier.border(1.5.dp, BrandCyan, RoundedCornerShape(10.dp)) else Modifier)
                .hoverable(hover).handCursor().clickable(onClick = onClick)
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(width = 72.dp, height = 46.dp).clip(RoundedCornerShape(6.dp)).background(Color(0xFF0A1220)),
                contentAlignment = Alignment.Center,
            ) {
                NetImage(listOf(item.logo), Modifier.fillMaxSize().padding(4.dp), ContentScale.Fit) {
                    Icon(Icons.Filled.LiveTv, null, tint = Color.White.copy(alpha = 0.35f))
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    (item.number?.let { "$it  " } ?: "") + item.name,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyLarge,
                )
                subtitle?.let {
                    Text(it, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (favorite) Icon(Icons.Filled.Favorite, "Favorit", tint = Danger, modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier, action: (@Composable () -> Unit)? = null) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Text(text, style = MaterialTheme.typography.titleLarge)
        action?.invoke()
    }
}

@Composable
fun Pill(text: String, color: Color = SurfaceHigh, textColor: Color = Color.White) {
    Text(
        text,
        modifier = Modifier.clip(RoundedCornerShape(50)).background(color).padding(horizontal = 10.dp, vertical = 4.dp),
        fontSize = 12.sp, color = textColor, fontWeight = FontWeight.SemiBold,
    )
}

fun typeLabel(t: ContentType) = when (t) {
    ContentType.LIVE -> "Live TV"
    ContentType.MOVIE -> "Filme"
    ContentType.SERIES -> "Serien"
}

fun formatTime(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    val h = s / 3600; val m = (s % 3600) / 60; val sec = s % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%d:%02d".format(m, sec)
}

val GradientBottom = Brush.verticalGradient(listOf(Color.Transparent, Background))
val CircleBg = CircleShape

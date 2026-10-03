package com.poweriptv.desktop.player

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import org.jetbrains.skia.FilterMipmap
import org.jetbrains.skia.FilterMode
import org.jetbrains.skia.MipmapMode
import org.jetbrains.skia.Rect

/** Zeichnet das aktuelle Videobild eines [PlayerController] (Bildformat wie im Player). */
@Composable
fun VideoView(ctl: PlayerController, aspect: String, modifier: Modifier = Modifier) {
    LaunchedEffect(ctl) { while (true) withFrameNanos { ctl.pollFrame() } }
    Canvas(modifier) {
        @Suppress("UNUSED_VARIABLE") val frame = ctl.frameCounter
        val img = ctl.currentImage() ?: return@Canvas
        val vw = img.width.toFloat() * ctl.pixelAspect
        val vh = img.height.toFloat()
        val w0 = size.width; val h0 = size.height
        val dst: Rect = when (aspect) {
            "stretch" -> Rect.makeWH(w0, h0)
            else -> {
                val ar = when (aspect) { "16:9" -> 16f / 9f; "4:3" -> 4f / 3f; else -> vw / vh }
                val h = if (aspect == "fill") maxOf(w0 / ar, h0) else minOf(w0 / ar, h0)
                val w = h * ar
                Rect.makeXYWH((w0 - w) / 2f, (h0 - h) / 2f, w, h)
            }
        }
        drawIntoCanvas { c ->
            c.nativeCanvas.save()
            c.nativeCanvas.clipRect(Rect.makeWH(w0, h0))
            c.nativeCanvas.drawImageRect(img, Rect.makeWH(img.width.toFloat(), img.height.toFloat()), dst, FilterMipmap(FilterMode.LINEAR, MipmapMode.NONE), null, true)
            c.nativeCanvas.restore()
        }
    }
}

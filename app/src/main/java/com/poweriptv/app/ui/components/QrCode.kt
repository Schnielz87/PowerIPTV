package com.poweriptv.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

// QR-Codes fuer "Portiva Link" (Zugang uebertragen). Geteilt mit der Windows-App.

/** QR-Code als Bild (schwarz auf weiss mit Ruhezone, damit jede Kamera ihn erkennt). */
@Composable
fun QrImage(text: String, modifier: Modifier = Modifier) {
    val matrix = remember(text) {
        runCatching {
            QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, 0, 0, mapOf(EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M, EncodeHintType.MARGIN to 0))
        }.getOrNull()
    }
    Box(modifier.aspectRatio(1f).clip(RoundedCornerShape(12.dp)).background(Color.White).padding(14.dp)) {
        if (matrix == null) return@Box
        Canvas(Modifier.fillMaxWidth().aspectRatio(1f)) {
            val n = matrix.width
            val cell = size.minDimension / n
            for (y in 0 until n) {
                var x = 0
                while (x < n) {
                    if (!matrix.get(x, y)) { x++; continue }
                    var run = 1
                    while (x + run < n && matrix.get(x + run, y)) run++
                    drawRect(Color.Black, Offset(x * cell, y * cell), Size(run * cell + 0.5f, cell + 0.5f))
                    x += run
                }
            }
        }
    }
}

/**
 * Grosser QR-Code zum Scannen. Schliessen ueber das Kreuz oben rechts oder durch Tippen neben den Dialog.
 * extra: zusaetzliche Knoepfe unter dem Code.
 */
@Composable
fun QrDialog(
    title: String,
    text: String,
    hint: String,
    onDismiss: () -> Unit,
    closeModifier: Modifier = Modifier,
    extra: @Composable ColumnScope.() -> Unit = {},
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                IconButton(onClick = onDismiss, modifier = closeModifier) { Icon(Icons.Filled.Close, "Schließen") }
            }
        },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                QrImage(text, Modifier.widthIn(max = 300.dp).fillMaxWidth())
                Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                extra()
            }
        },
        confirmButton = {},
    )
}

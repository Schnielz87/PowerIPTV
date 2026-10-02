package com.poweriptv.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.poweriptv.app.ui.theme.BrandCyan
import com.poweriptv.app.util.LocalIsTv

/**
 * Flaches Suchfeld (40 dp hoch) fuer kompakte Layouts.
 * Auf dem Fernseher wird das Feld erst nach "OK" editierbar – beim Navigieren
 * mit der Fernbedienung oeffnet sich so keine Bildschirmtastatur.
 */
@Composable
fun CompactSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    height: Dp = 40.dp,
) {
    val isTv = LocalIsTv.current
    var focused by remember { mutableStateOf(false) }
    var rowFocused by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf(!isTv) }
    val innerFocus = remember { FocusRequester() }
    val rowFocus = remember { FocusRequester() }
    val keyboard = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
    /** TV: Eingabe beenden und mit der Fernbedienung weiter (z.B. runter zu den Treffern). */
    fun leave(direction: androidx.compose.ui.focus.FocusDirection?) {
        keyboard?.hide()
        if (direction != null && focusManager.moveFocus(direction)) { editing = false; return }
        editing = false
        runCatching { rowFocus.requestFocus() }
    }
    LaunchedEffect(editing) {
        if (isTv && editing) runCatching { innerFocus.requestFocus() }
    }
    val shape = RoundedCornerShape(10.dp)
    val highlight = focused || rowFocused

    Row(
        modifier
            .height(height)
            .then(
                if (isTv && !editing) Modifier
                    .focusRequester(rowFocus)
                    .onFocusChanged { rowFocused = it.isFocused }
                    .clickable { editing = true }
                else Modifier
            )
            .background(MaterialTheme.colorScheme.surface, shape)
            .border(if (rowFocused) 3.dp else 1.dp, if (highlight) BrandCyan else MaterialTheme.colorScheme.surfaceVariant, shape)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.Search, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (value.isEmpty()) {
                Text(placeholder, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
                cursorBrush = SolidColor(BrandCyan),
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Search),
                // "Suchen"/"Fertig" auf der Tastatur: Tastatur zu, weiter zu den Treffern
                keyboardActions = androidx.compose.foundation.text.KeyboardActions(onSearch = {
                    if (isTv) leave(androidx.compose.ui.focus.FocusDirection.Down) else keyboard?.hide()
                }, onDone = { if (isTv) leave(androidx.compose.ui.focus.FocusDirection.Down) else keyboard?.hide() }),
                modifier = Modifier
                    .fillMaxWidth()
                    .then(
                        if (isTv) Modifier.onPreviewKeyEvent { e ->
                            if (e.type != androidx.compose.ui.input.key.KeyEventType.KeyDown) return@onPreviewKeyEvent false
                            when (e.key) {
                                // Runter/Hoch: raus aus dem Feld zu Treffern bzw. Kopfzeile
                                androidx.compose.ui.input.key.Key.DirectionDown -> { leave(androidx.compose.ui.focus.FocusDirection.Down); true }
                                androidx.compose.ui.input.key.Key.DirectionUp -> { leave(androidx.compose.ui.focus.FocusDirection.Up); true }
                                // OK/Enter: Tastatur (wieder) oeffnen, um weiter zu tippen
                                androidx.compose.ui.input.key.Key.DirectionCenter, androidx.compose.ui.input.key.Key.Enter,
                                androidx.compose.ui.input.key.Key.NumPadEnter -> { keyboard?.show(); true }
                                // Zurueck: nur das Feld verlassen, nicht die ganze Seite
                                androidx.compose.ui.input.key.Key.Back -> { leave(null); true }
                                else -> false
                            }
                        } else Modifier
                    )
                    .focusRequester(innerFocus)
                    .focusProperties { canFocus = editing }
                    .onFocusChanged {
                        val wasFocused = focused
                        focused = it.isFocused
                        if (isTv && wasFocused && !it.isFocused) editing = false
                    },
            )
        }
        if (value.isNotEmpty()) {
            Icon(
                Icons.Filled.Clear, "Leeren",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp).clickable { onValueChange("") },
            )
        }
    }
}

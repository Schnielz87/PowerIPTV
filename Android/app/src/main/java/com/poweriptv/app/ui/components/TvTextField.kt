package com.poweriptv.app.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.poweriptv.app.util.LocalIsTv

/**
 * Eingabefeld wie OutlinedTextField – am Fernseher aber fernbedienungs-freundlich (wie das Suchfeld):
 * Mit den Pfeiltasten wird das Feld nur angewaehlt (Rahmen), die Tastatur oeffnet sich erst mit OK.
 * Hoch/Runter verlassen das Feld wieder, Zurueck schliesst nur die Eingabe. So kommt man an Feldern
 * vorbei (z.B. in den Einstellungen), ohne dass jedes Mal die Bildschirmtastatur aufgeht.
 * Am Handy/Tablet verhaelt es sich exakt wie OutlinedTextField.
 */
@Composable
fun TvOutlinedTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    textStyle: TextStyle = LocalTextStyle.current,
    label: @Composable (() -> Unit)? = null,
    placeholder: @Composable (() -> Unit)? = null,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    supportingText: @Composable (() -> Unit)? = null,
    isError: Boolean = false,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions? = null,
    singleLine: Boolean = false,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    minLines: Int = 1,
) {
    val isTv = LocalIsTv.current
    if (!isTv) {
        OutlinedTextField(
            value = value, onValueChange = onValueChange, modifier = modifier, enabled = enabled, readOnly = readOnly,
            textStyle = textStyle, label = label, placeholder = placeholder, leadingIcon = leadingIcon, trailingIcon = trailingIcon,
            supportingText = supportingText, isError = isError, visualTransformation = visualTransformation,
            keyboardOptions = keyboardOptions, keyboardActions = keyboardActions ?: KeyboardActions.Default,
            singleLine = singleLine, maxLines = maxLines, minLines = minLines,
        )
        return
    }

    var editing by remember { mutableStateOf(false) }
    var fieldFocused by remember { mutableStateOf(false) }
    val fieldFocus = remember { FocusRequester() }
    val boxFocus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current

    /** Eingabe beenden: in Richtung weiter (Hoch/Runter) oder zurueck auf das Feld (Zurueck). */
    fun leave(direction: FocusDirection?) {
        keyboard?.hide()
        editing = false
        if (direction == null || !focusManager.moveFocus(direction)) runCatching { boxFocus.requestFocus() }
    }
    LaunchedEffect(editing) { if (editing) runCatching { fieldFocus.requestFocus() } }

    Box(
        modifier
            .focusRequester(boxFocus)
            .tvFocus(RoundedCornerShape(6.dp))
            .focusProperties { canFocus = !editing }
            .clickable(enabled = enabled && !editing) { editing = true },
        propagateMinConstraints = true,
    ) {
        OutlinedTextField(
            value = value, onValueChange = onValueChange, enabled = enabled, readOnly = readOnly,
            textStyle = textStyle, label = label, placeholder = placeholder, leadingIcon = leadingIcon, trailingIcon = trailingIcon,
            supportingText = supportingText, isError = isError, visualTransformation = visualTransformation,
            keyboardOptions = keyboardOptions,
            // "Fertig"/"Weiter" auf der Tastatur: Eingabe beenden und mit der Fernbedienung weiter nach unten
            keyboardActions = keyboardActions ?: KeyboardActions(
                onDone = { leave(FocusDirection.Down) }, onNext = { leave(FocusDirection.Down) },
                onGo = { leave(FocusDirection.Down) }, onSearch = { leave(FocusDirection.Down) },
            ),
            singleLine = singleLine, maxLines = maxLines, minLines = minLines,
            modifier = Modifier
                .onPreviewKeyEvent { e ->
                    if (!editing || e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    when (e.key) {
                        Key.DirectionDown -> { leave(FocusDirection.Down); true }
                        Key.DirectionUp -> { leave(FocusDirection.Up); true }
                        Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> { keyboard?.show(); true }
                        Key.Back -> { leave(null); true }
                        else -> false
                    }
                }
                .focusRequester(fieldFocus)
                .focusProperties { canFocus = editing }
                .onFocusChanged {
                    val was = fieldFocused
                    fieldFocused = it.isFocused
                    if (was && !it.isFocused) editing = false
                },
        )
    }
}

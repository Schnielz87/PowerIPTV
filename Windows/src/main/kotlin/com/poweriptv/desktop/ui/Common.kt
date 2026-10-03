package com.poweriptv.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.poweriptv.app.parental.ParentalControl
import java.awt.Desktop
import java.awt.FileDialog
import java.awt.Frame
import java.io.File

/** Seitenrahmen: Titel (optional mit Zurueck) + Aktionen + scrollbarer Inhalt. */
@Composable
fun Page(
    title: String,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    maxWidth: Int = 1000,
    scroll: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            Modifier.widthIn(max = maxWidth.dp).fillMaxWidth()
                .then(if (scroll) Modifier.verticalScroll(rememberScrollState()) else Modifier)
                .padding(28.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                onBack?.let { IconButton(onClick = it, modifier = Modifier.handCursor()) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Zurück") } }
                Text(title, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
                actions()
            }
            content()
        }
    }
}

@Composable
fun SettingsCard(title: String? = null, content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Surface).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        title?.let { Text(it, style = MaterialTheme.typography.titleLarge) }
        content()
    }
}

@Composable
fun ToggleRow(label: String, value: Boolean, hint: String? = null, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label)
            hint?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        Spacer(Modifier.width(12.dp))
        Switch(value, onChange, enabled = enabled, modifier = Modifier.handCursor())
    }
}

fun formatBytes(b: Long): String = when {
    b < 0 -> "?"
    b < 1024 -> "$b B"
    b < 1024L * 1024 -> "%.0f KB".format(b / 1024.0)
    b < 1024L * 1024 * 1024 -> "%.1f MB".format(b / 1024.0 / 1024.0)
    else -> "%.2f GB".format(b / 1024.0 / 1024.0 / 1024.0)
}

/** PIN-Abfrage (Kindersicherung) – gleiche Texte wie Android. */
@Composable
fun PinDialog(parental: ParentalControl, onDismiss: () -> Unit, onSuccess: () -> Unit) {
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    val submit = {
        if (parental.verify(pin)) onSuccess() else { error = "Falsche PIN"; pin = "" }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Kindersicherung") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Bitte PIN eingeben")
                OutlinedTextField(
                    pin, { v -> if (v.length <= 8 && v.all(Char::isDigit)) pin = v }, singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    keyboardActions = KeyboardActions(onDone = { submit() }),
                    modifier = Modifier.focusRequester(focus),
                )
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = { TextButton(onClick = submit) { Text("OK") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } },
    )
}

/** Neue PIN festlegen (4–8 Ziffern, zweimal eingeben). */
@Composable
fun SetPinDialog(onDismiss: () -> Unit, onSet: (String) -> Unit) {
    var a by remember { mutableStateOf("") }
    var b by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("PIN festlegen") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(a, { v -> if (v.length <= 8 && v.all(Char::isDigit)) a = v }, label = { Text("Neue PIN (4–8 Ziffern)") }, singleLine = true, visualTransformation = PasswordVisualTransformation())
                OutlinedTextField(b, { v -> if (v.length <= 8 && v.all(Char::isDigit)) b = v }, label = { Text("PIN wiederholen") }, singleLine = true, visualTransformation = PasswordVisualTransformation())
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                error = when {
                    a.length < 4 -> "Mindestens 4 Ziffern"
                    a != b -> "PINs stimmen nicht überein"
                    else -> null
                }
                if (error == null) onSet(a)
            }) { Text("Speichern") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } },
    )
}

/** Windows-Dateiauswahl. */
fun chooseFile(title: String, vararg extensions: String): File? {
    val d = FileDialog(null as Frame?, title, FileDialog.LOAD)
    if (extensions.isNotEmpty()) d.setFilenameFilter { _, n -> extensions.any { n.lowercase().endsWith(it) } }
    d.isVisible = true
    return d.file?.let { File(d.directory, it) }
}

fun chooseSaveFile(title: String, suggested: String): File? {
    val d = FileDialog(null as Frame?, title, FileDialog.SAVE)
    d.file = suggested
    d.isVisible = true
    return d.file?.let { File(d.directory, it) }
}

/** Ordner im Windows-Explorer oeffnen. */
fun openFolder(dir: File) {
    runCatching { dir.mkdirs(); Desktop.getDesktop().open(dir) }
}

fun openUrl(url: String) {
    runCatching { Desktop.getDesktop().browse(java.net.URI(url)) }
}

/** Bestaetigungsdialog. */
@Composable
fun ConfirmDialog(title: String, text: String, confirm: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = { TextButton(onClick = { onConfirm(); onDismiss() }) { Text(confirm) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } },
    )
}

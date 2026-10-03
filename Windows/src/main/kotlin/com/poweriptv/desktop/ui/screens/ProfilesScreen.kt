package com.poweriptv.desktop.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import com.poweriptv.app.data.M3uSource
import com.poweriptv.app.data.Profile
import com.poweriptv.app.data.ProfileType
import com.poweriptv.app.data.XtreamSource
import com.poweriptv.desktop.AppState
import com.poweriptv.desktop.data.AppDirs
import com.poweriptv.desktop.data.AppJson
import com.poweriptv.desktop.data.Http
import com.poweriptv.desktop.ui.BrandCyan
import com.poweriptv.desktop.ui.BrandWordmark
import com.poweriptv.desktop.ui.PortivaLogo
import com.poweriptv.desktop.ui.Surface
import com.poweriptv.desktop.ui.SurfaceHigh
import com.poweriptv.desktop.ui.handCursor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.FileDialog
import java.awt.Frame
import java.io.File
import java.util.UUID

@Composable
fun ProfilesScreen(app: AppState) {
    val profiles by app.profiles.profiles.collectAsState()
    var adding by remember { mutableStateOf(profiles.isEmpty()) }
    var deleteAsk by remember { mutableStateOf<Profile?>(null) }
    var editing by remember { mutableStateOf<Profile?>(null) }
    var qrFor by remember { mutableStateOf<Profile?>(null) }
    var receiving by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            Modifier.widthIn(max = 760.dp).fillMaxWidth().verticalScroll(rememberScrollState()).padding(32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (app.profile == null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    PortivaLogo(Modifier.size(64.dp)); Spacer(Modifier.width(14.dp)); BrandWordmark()
                }
                Text("Willkommen! Richte deinen IPTV-Zugang ein.", style = MaterialTheme.typography.titleLarge)
            } else {
                Text("Zugänge", style = MaterialTheme.typography.headlineMedium)
            }
            profiles.forEach { p ->
                val active = p.id == app.profile?.id
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(if (active) SurfaceHigh else Surface)
                        .then(if (active) Modifier.border(1.5.dp, BrandCyan, RoundedCornerShape(14.dp)) else Modifier)
                        .handCursor().clickable { app.activate(p) }.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.Person, null, tint = BrandCyan, modifier = Modifier.size(32.dp))
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(p.name, style = MaterialTheme.typography.titleMedium)
                        Text(
                            when (p.type) {
                                ProfileType.XTREAM -> "Xtream Codes · ${p.serverUrl}"
                                ProfileType.M3U_URL -> "M3U-Link"
                                ProfileType.M3U_FILE -> "M3U-Datei"
                            },
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1,
                        )
                    }
                    if (active) {
                        Text(
                            "AKTIV", fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp,
                            maxLines = 1, softWrap = false, color = androidx.compose.ui.graphics.Color(0xFF06101E),
                            modifier = Modifier.clip(RoundedCornerShape(50)).background(BrandCyan).padding(horizontal = 8.dp, vertical = 2.dp),
                        )
                    }
                    // QR-Code: diesen Zugang mit dem Handy/Tablet scannen (wie Android)
                    if (com.poweriptv.app.link.LinkCodes.canTransfer(p)) {
                        IconButton(onClick = { qrFor = p }, modifier = Modifier.handCursor()) { Icon(Icons.Filled.QrCode2, "Auf anderes Gerät übertragen") }
                    }
                    IconButton(onClick = { editing = p; adding = false }, modifier = Modifier.handCursor()) { Icon(Icons.Filled.Edit, "Bearbeiten") }
                    IconButton(onClick = { deleteAsk = p }, modifier = Modifier.handCursor()) { Icon(Icons.Filled.Delete, "Löschen") }
                }
            }
            if (editing != null) {
                key(editing!!.id) { AddProfileForm(app, edit = editing, onCancel = { editing = null }) { editing = null } }
            } else if (adding) {
                OutlinedButton(onClick = { receiving = true }, modifier = Modifier.handCursor()) {
                    Icon(Icons.Filled.QrCode2, null); Spacer(Modifier.width(6.dp)); Text("Vom Handy empfangen (QR-Code)")
                }
                AddProfileForm(app, onCancel = if (profiles.isNotEmpty()) ({ adding = false }) else null) { adding = false }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(onClick = { adding = true }, modifier = Modifier.handCursor()) {
                        Icon(Icons.Filled.Add, null); Spacer(Modifier.width(6.dp)); Text("Zugang hinzufügen")
                    }
                    OutlinedButton(onClick = { receiving = true }, modifier = Modifier.handCursor()) {
                        Icon(Icons.Filled.QrCode2, null); Spacer(Modifier.width(6.dp)); Text("Vom Handy empfangen")
                    }
                }
            }
        }
    }
    qrFor?.let { p ->
        com.poweriptv.app.ui.components.QrDialog(
            title = p.name,
            text = com.poweriptv.app.link.LinkCodes.accountQr(com.poweriptv.app.link.LinkCodes.toAccount(p)),
            hint = "Am Handy/Tablet in Portiva: Benutzer wechseln → Neuer Zugang → „QR-Code scannen“. Nur dir selbst zeigen – der Code enthält die Zugangsdaten.",
            onDismiss = { qrFor = null },
            closeModifier = Modifier.handCursor(),
        )
    }
    if (receiving) ReceiveAccountDialog(app, onDismiss = { receiving = false }) { p ->
        receiving = false; adding = false
        app.activate(p)
    }
    deleteAsk?.let { p ->
        AlertDialog(
            onDismissRequest = { deleteAsk = null },
            title = { Text("Zugang löschen?") },
            text = { Text("„${p.name}“ wird von diesem PC entfernt.") },
            confirmButton = {
                TextButton(onClick = {
                    app.profiles.delete(p.id)
                    if (app.profile?.id == p.id) app.profiles.profiles.value.firstOrNull()?.let(app::activate) ?: app.deactivate()
                    deleteAsk = null
                }) { Text("Löschen") }
            },
            dismissButton = { TextButton(onClick = { deleteAsk = null }) { Text("Abbrechen") } },
        )
    }
}

/** Dieser PC zeigt einen Empfangs-Code; das Handy scannt ihn und schickt den gewaehlten Zugang (wie Android). */
@Composable
private fun ReceiveAccountDialog(app: AppState, onDismiss: () -> Unit, onReceived: (Profile) -> Unit) {
    val code = remember { com.poweriptv.app.link.LinkCodes.newCode() }
    val ip = remember { com.poweriptv.app.link.LinkCodes.localIpv4() }
    val received by app.linkReceived.collectAsState()
    androidx.compose.runtime.DisposableEffect(code) {
        app.linkReceived.value = null
        app.linkPairCode.value = code
        onDispose { if (app.linkPairCode.value == code) app.linkPairCode.value = null }
    }
    androidx.compose.runtime.LaunchedEffect(received) { received?.let { app.linkReceived.value = null; onReceived(it) } }
    if (ip == null || app.link.port == 0) {
        AlertDialog(
            onDismissRequest = onDismiss, title = { Text("Kein Heimnetz") },
            text = { Text("Der PC ist nicht mit einem Netzwerk verbunden. Handy und PC müssen im selben Heimnetz sein.") },
            confirmButton = { TextButton(onClick = onDismiss) { Text("OK") } },
        )
        return
    }
    com.poweriptv.app.ui.components.QrDialog(
        title = "Zugang empfangen",
        text = com.poweriptv.app.link.LinkCodes.pairQr(ip, app.link.port, code),
        hint = "Am Handy in Portiva: Benutzer wechseln → beim gewünschten Zugang auf das QR-Symbol tippen → „An TV-Stick / Fernseher senden“ → diesen Code scannen. " +
            "Fragt die Windows-Firewall, bitte „Zulassen“ wählen.",
        onDismiss = onDismiss,
        closeModifier = Modifier.handCursor(),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
            Spacer(Modifier.width(10.dp))
            Text("Warte auf Zugang …  Code $code", fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun AddProfileForm(app: AppState, edit: Profile? = null, onCancel: (() -> Unit)?, onDone: () -> Unit) {
    val scope = rememberCoroutineScope()
    var type by remember { mutableStateOf(edit?.type ?: ProfileType.XTREAM) }
    var name by remember { mutableStateOf(edit?.name ?: "") }
    var server by remember { mutableStateOf(edit?.serverUrl ?: "") }
    var user by remember { mutableStateOf(edit?.username ?: "") }
    var pass by remember { mutableStateOf(edit?.password ?: "") }
    var showPass by remember { mutableStateOf(false) }
    var m3u by remember { mutableStateOf(edit?.m3uUrl ?: "") }
    var file by remember { mutableStateOf<File?>(null) }
    var epg by remember { mutableStateOf(edit?.epgUrl ?: "") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Surface).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(if (edit != null) "Zugang bearbeiten" else "Neuer Zugang", style = MaterialTheme.typography.titleLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(ProfileType.XTREAM to "Xtream Codes", ProfileType.M3U_URL to "M3U-Link", ProfileType.M3U_FILE to "M3U-Datei").forEach { (t, l) ->
                FilterChip(selected = type == t, onClick = { type = t }, label = { Text(l) }, modifier = Modifier.handCursor())
            }
        }
        OutlinedTextField(name, { name = it }, label = { Text("Name (z.B. Wohnzimmer)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        when (type) {
            ProfileType.XTREAM -> {
                OutlinedTextField(server, { server = it }, label = { Text("Server-URL (z.B. http://anbieter.tv:8080)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(user, { user = it }, label = { Text("Benutzername") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(
                    pass, { pass = it }, label = { Text("Passwort") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                    visualTransformation = if (showPass) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    trailingIcon = {
                        IconButton(onClick = { showPass = !showPass }) { Icon(if (showPass) Icons.Filled.VisibilityOff else Icons.Filled.Visibility, "Anzeigen") }
                    },
                )
            }
            ProfileType.M3U_URL ->
                OutlinedTextField(m3u, { m3u = it }, label = { Text("M3U-Link") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            ProfileType.M3U_FILE -> Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(onClick = { pickFile()?.let { file = it } }, modifier = Modifier.handCursor()) {
                    Icon(Icons.Filled.Folder, null); Spacer(Modifier.width(6.dp)); Text("Datei wählen …")
                }
                Spacer(Modifier.width(12.dp))
                Text(file?.name ?: "Keine Datei gewählt", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        OutlinedTextField(epg, { epg = it }, label = { Text("EPG-URL (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Button(
                enabled = !busy,
                onClick = {
                    error = null; busy = true
                    scope.launch {
                        val id = edit?.id ?: UUID.randomUUID().toString()
                        val result = runCatching {
                            withContext(Dispatchers.IO) {
                                when (type) {
                                    ProfileType.XTREAM -> {
                                        require(server.isNotBlank() && user.isNotBlank()) { "Bitte Server und Benutzername eingeben" }
                                        val p = Profile(id, name.ifBlank { "Mein IPTV" }, type, serverUrl = XtreamSource.normalizeServer(server), username = user.trim(), password = pass, epgUrl = epg.trim())
                                        XtreamSource(p, Http, AppJson) { "ts" }.authenticate()
                                        p
                                    }
                                    ProfileType.M3U_URL -> {
                                        require(m3u.isNotBlank()) { "Bitte M3U-Link eingeben" }
                                        val p = Profile(id, name.ifBlank { "M3U-Playlist" }, type, m3uUrl = m3u.trim(), epgUrl = epg.trim())
                                        M3uSource(p, Http).validate()
                                        p
                                    }
                                    ProfileType.M3U_FILE -> {
                                        val f = file ?: edit?.localFile?.takeIf { it.isNotBlank() }?.let { File(it) } ?: error("Bitte eine Datei wählen")
                                        val target = File(AppDirs.file("playlists").apply { mkdirs() }, "$id.m3u")
                                        if (f.absolutePath != target.absolutePath) f.copyTo(target, overwrite = true)
                                        val p = Profile(id, name.ifBlank { f.nameWithoutExtension }, type, localFile = target.absolutePath, epgUrl = epg.trim())
                                        M3uSource(p, Http).validate()
                                        p
                                    }
                                }
                            }
                        }
                        busy = false
                        result.onSuccess { p ->
                            // Zugangsdaten koennen sich geaendert haben -> gespeicherte Playlist verwerfen (wie Android)
                            if (edit != null) File(AppDirs.cache, "playlist/" + p.id.replace(Regex("[^A-Za-z0-9_-]"), "_")).deleteRecursively()
                            app.profiles.save(p)
                            app.activate(p)
                            onDone()
                        }.onFailure { error = it.message ?: "Verbindung fehlgeschlagen" }
                    }
                },
                modifier = Modifier.handCursor(),
            ) { Text("Verbinden & speichern") }
            if (busy) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
            onCancel?.let { TextButton(onClick = it) { Text("Abbrechen") } }
        }
        Text(
            "Dein Passwort wird mit dem Windows-Datenschutz verschlüsselt auf diesem PC gespeichert.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun pickFile(): File? {
    val dialog = FileDialog(null as Frame?, "M3U-Playlist wählen", FileDialog.LOAD)
    dialog.setFilenameFilter { _, n -> n.lowercase().let { it.endsWith(".m3u") || it.endsWith(".m3u8") || it.endsWith(".txt") } }
    dialog.isVisible = true
    val f = dialog.file ?: return null
    return File(dialog.directory, f)
}


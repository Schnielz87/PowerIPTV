package com.poweriptv.app.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.poweriptv.app.AppContainer
import com.poweriptv.app.data.M3uSource
import com.poweriptv.app.data.Profile
import com.poweriptv.app.data.ProfileType
import com.poweriptv.app.data.XtreamSource
import com.poweriptv.app.ui.components.PowerTopBar
import kotlinx.coroutines.launch

@Composable
fun AddProfileScreen(container: AppContainer, editId: String?, onDone: () -> Unit, onBack: () -> Unit) {
    val existing = remember(editId) { container.profiles.get(editId) }
    val profileId = remember(editId) { existing?.id ?: container.profiles.newId() }

    var type by rememberSaveable { mutableStateOf(existing?.type ?: ProfileType.XTREAM) }
    var name by rememberSaveable { mutableStateOf(existing?.name ?: "") }
    var server by rememberSaveable { mutableStateOf(existing?.serverUrl ?: "") }
    var user by rememberSaveable { mutableStateOf(existing?.username ?: "") }
    var pass by rememberSaveable { mutableStateOf(existing?.password ?: "") }
    var m3u by rememberSaveable { mutableStateOf(existing?.m3uUrl ?: "") }
    var localFile by rememberSaveable { mutableStateOf(existing?.localFile ?: "") }
    var epgUrl by rememberSaveable { mutableStateOf(existing?.epgUrl ?: "") }
    var showPass by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var info by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            runCatching { container.profiles.importLocalFile(profileId, uri) }
                .onSuccess { localFile = it; info = "Datei importiert"; error = null }
                .onFailure { error = it.message }
        }
    }

    fun buildProfile() = Profile(
        id = profileId,
        name = name.trim().ifBlank {
            when (type) {
                ProfileType.XTREAM -> user.ifBlank { "Xtream" }
                ProfileType.M3U_URL -> "M3U Playlist"
                ProfileType.M3U_FILE -> "M3U Datei"
            }
        },
        type = type,
        serverUrl = if (type == ProfileType.XTREAM) XtreamSource.normalizeServer(server) else "",
        username = user.trim(),
        password = pass,
        m3uUrl = m3u.trim(),
        localFile = localFile,
        epgUrl = epgUrl.trim(),
    )

    fun save(validate: Boolean) {
        error = null
        val p = buildProfile()
        when (type) {
            ProfileType.XTREAM -> if (server.isBlank() || user.isBlank() || pass.isBlank()) {
                error = "Bitte Server-URL, Benutzername und Passwort ausfuellen."; return
            }
            ProfileType.M3U_URL -> if (m3u.isBlank()) { error = "Bitte M3U-URL eingeben."; return }
            ProfileType.M3U_FILE -> if (localFile.isBlank()) { error = "Bitte eine M3U-Datei auswaehlen."; return }
        }
        if (!validate) {
            container.profiles.save(p); onDone(); return
        }
        busy = true
        scope.launch {
            val result = runCatching {
                when (val src = container.createSource(p)) {
                    is XtreamSource -> src.authenticate()
                    is M3uSource -> src.validate()
                    else -> Unit
                }
            }
            busy = false
            result.onSuccess {
                container.profiles.save(p)
                if (container.source?.profile?.id == p.id) container.activate(p)
                onDone()
            }.onFailure {
                error = "Verbindung fehlgeschlagen: ${it.message ?: it.javaClass.simpleName}"
            }
        }
    }

    Scaffold(
        topBar = { PowerTopBar(if (existing == null) "Zugang hinzufuegen" else "Zugang bearbeiten", onBack = onBack) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            TabRow(selectedTabIndex = type.ordinal) {
                Tab(selected = type == ProfileType.XTREAM, onClick = { type = ProfileType.XTREAM }, text = { Text("Xtream Codes") })
                Tab(selected = type == ProfileType.M3U_URL, onClick = { type = ProfileType.M3U_URL }, text = { Text("M3U-URL") })
                Tab(selected = type == ProfileType.M3U_FILE, onClick = { type = ProfileType.M3U_FILE }, text = { Text("M3U-Datei") })
            }

            OutlinedTextField(
                value = name, onValueChange = { name = it },
                label = { Text("Name des Zugangs (beliebig)") },
                singleLine = true, modifier = Modifier.fillMaxWidth(),
            )

            when (type) {
                ProfileType.XTREAM -> {
                    OutlinedTextField(
                        value = server, onValueChange = { server = it },
                        label = { Text("Server-URL (z.B. http://server.com:8080)") },
                        singleLine = true, modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    )
                    OutlinedTextField(
                        value = user, onValueChange = { user = it },
                        label = { Text("Benutzername") },
                        singleLine = true, modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = pass, onValueChange = { pass = it },
                        label = { Text("Passwort") },
                        singleLine = true, modifier = Modifier.fillMaxWidth(),
                        visualTransformation = if (showPass) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        trailingIcon = {
                            IconButton(onClick = { showPass = !showPass }) {
                                Icon(if (showPass) Icons.Filled.VisibilityOff else Icons.Filled.Visibility, null)
                            }
                        },
                    )
                }
                ProfileType.M3U_URL -> {
                    OutlinedTextField(
                        value = m3u, onValueChange = { m3u = it },
                        label = { Text("M3U-URL (http://...get.php?...)") },
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    )
                    Text(
                        "Tipp: Enthaelt die URL username= und password=, kannst du auch \"Xtream Codes\" verwenden – " +
                            "dann gibt es zusaetzlich Film-/Serien-Infos und EPG.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedButton(onClick = {
                        parseXtreamFromM3u(m3u)?.let { (s, u, p) ->
                            server = s; user = u; pass = p; type = ProfileType.XTREAM
                        } ?: run { error = "Keine Xtream-Zugangsdaten in der URL gefunden." }
                    }) { Text("In Xtream-Zugang umwandeln") }
                }
                ProfileType.M3U_FILE -> {
                    OutlinedButton(onClick = { picker.launch(arrayOf("*/*")) }) {
                        Icon(Icons.Filled.FolderOpen, null)
                        Spacer(Modifier.width(8.dp))
                        Text(if (localFile.isBlank()) "M3U-Datei auswaehlen" else "Andere Datei waehlen")
                    }
                    if (localFile.isNotBlank()) Text("Datei ist importiert.", color = MaterialTheme.colorScheme.primary)
                }
            }

            OutlinedTextField(
                value = epgUrl, onValueChange = { epgUrl = it },
                label = { Text("EPG-URL (XMLTV, optional)") },
                supportingText = {
                    Text(
                        if (type == ProfileType.XTREAM) "Leer lassen = EPG automatisch vom Server (xmltv.php)"
                        else "Leer lassen = url-tvg aus der Playlist verwenden"
                    )
                },
                singleLine = true, modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            )

            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            info?.takeIf { error == null }?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant) }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Button(onClick = { save(validate = true) }, enabled = !busy) {
                    if (busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    else Text("Pruefen & speichern")
                }
                if (error != null && !busy) {
                    OutlinedButton(onClick = { save(validate = false) }) { Text("Trotzdem speichern") }
                }
            }
        }
    }
}

/** Liest Server, Benutzer und Passwort aus einer typischen get.php-URL. */
private fun parseXtreamFromM3u(url: String): Triple<String, String, String>? = runCatching {
    val uri = Uri.parse(url.trim())
    val u = uri.getQueryParameter("username") ?: return null
    val p = uri.getQueryParameter("password") ?: return null
    val port = if (uri.port > 0) ":${uri.port}" else ""
    Triple("${uri.scheme}://${uri.host}$port", u, p)
}.getOrNull()

package app.reed.ui.sync

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.text.format.DateUtils
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import app.reed.reed
import app.reed.sync.LoginFlow
import app.reed.sync.NotesSync
import app.reed.sync.SyncSettings
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import java.net.URI

sealed interface SignIn {
    data object Idle : SignIn
    data object Starting : SignIn
    data object Waiting : SignIn
    data class Failed(val message: String) : SignIn
}

class SyncViewModel(app: Application) : AndroidViewModel(app) {
    private val store = app.reed.sync
    val settings = store.settings
    val signIn = MutableStateFlow<SignIn>(SignIn.Idle)
    private var polling: Job? = null

    /** Opens the server's sign-in page in the browser and waits for the user to approve Reed there. */
    fun signIn(server: String, open: (String) -> Unit) {
        polling?.cancel()
        polling = viewModelScope.launch {
            signIn.value = SignIn.Starting
            try {
                val start = LoginFlow.start(server)
                open(start.loginUrl)
                signIn.value = SignIn.Waiting
                val deadline = System.currentTimeMillis() + 20 * 60_000
                while (System.currentTimeMillis() < deadline) {
                    delay(2_000)
                    val account = runCatching { LoginFlow.poll(start) }.getOrNull() ?: continue
                    store.connect(account, LoginFlow.userId(account))
                    signIn.value = SignIn.Idle
                    NotesSync.sendNow(getApplication())
                    return@launch
                }
                signIn.value = SignIn.Failed("The sign-in timed out. Try again.")
            } catch (e: Exception) {
                signIn.value = SignIn.Failed(
                    when (e) {
                        is java.net.UnknownHostException -> "Couldn't find that server. Check the address."
                        is org.json.JSONException -> "That doesn't look like a Nextcloud server."
                        else -> e.message ?: "Couldn't sign in."
                    },
                )
            }
        }
    }

    fun cancelSignIn() {
        polling?.cancel()
        signIn.value = SignIn.Idle
    }

    fun disconnect() = viewModelScope.launch {
        store.account()?.let { LoginFlow.revoke(it) }
        store.disconnect()
    }

    fun setEnabled(on: Boolean) = viewModelScope.launch {
        store.setEnabled(on)
        if (on) NotesSync.sendNow(getApplication())
    }

    fun setIncludePrivate(on: Boolean) = viewModelScope.launch {
        store.setIncludePrivate(on)
        NotesSync.sendNow(getApplication())
    }

    fun setFolder(folder: String) = viewModelScope.launch {
        store.setFolder(folder)
        NotesSync.sendNow(getApplication())
    }

    fun setSyncBooks(on: Boolean) = viewModelScope.launch {
        store.setSyncBooks(on)
        if (on) NotesSync.sendNow(getApplication())
    }

    fun sendNow() {
        viewModelScope.launch { store.recordError(null) }
        NotesSync.sendNow(getApplication())
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SyncScreen(onBack: () -> Unit, model: SyncViewModel = viewModel()) {
    val settings by model.settings.collectAsState(null)
    val signIn by model.signIn.collectAsState()
    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back") }
                },
                title = { Text("Send notes", style = MaterialTheme.typography.titleMedium) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            )
        },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(bottom = 32.dp),
        ) {
            val s = settings ?: return@Column
            if (!s.connected) NotConnected(signIn, model) else Connected(s, model)
        }
    }
}

@Composable
private fun NotConnected(signIn: SignIn, model: SyncViewModel) {
    val context = LocalContext.current
    var server by rememberSaveable { mutableStateOf("") }
    Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Spacer(Modifier.height(4.dp))
        Text(
            "Reed can keep your book notes in step with the home app on your computer, through your Nextcloud: each book becomes a note there, every passage quoted above what you wrote, and edits made in home come back here.",
            style = MaterialTheme.typography.bodyLarge,
        )
        Text(
            "Reading stays offline. Reed writes into one folder on your Nextcloud, private books stay on this phone unless you say otherwise, and you can disconnect any time.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedTextField(
            value = server,
            onValueChange = { server = it },
            label = { Text("Nextcloud address") },
            placeholder = { Text("cloud.example.org") },
            singleLine = true,
            enabled = signIn !is SignIn.Waiting && signIn !is SignIn.Starting,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
            keyboardActions = KeyboardActions(onGo = { if (server.isNotBlank()) model.signIn(server) { context.openBrowser(it) } }),
            modifier = Modifier.fillMaxWidth(),
        )
        when (signIn) {
            SignIn.Waiting -> {
                Text(
                    "Finish signing in in your browser and allow Reed access, then come back here. You never type your password into Reed.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                TextButton(onClick = model::cancelSignIn) { Text("Cancel") }
            }
            else -> {
                if (signIn is SignIn.Failed) {
                    Text(signIn.message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                }
                Button(
                    onClick = { model.signIn(server) { context.openBrowser(it) } },
                    enabled = server.isNotBlank() && signIn !is SignIn.Starting,
                ) { Text(if (signIn is SignIn.Starting) "Opening…" else "Sign in") }
            }
        }
    }
}

@Composable
private fun Connected(s: SyncSettings, model: SyncViewModel) {
    var confirmDisconnect by remember { mutableStateOf(false) }
    val host = remember(s.server) { runCatching { URI(s.server).host }.getOrNull() ?: s.server }
    val rowColors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface)

    ListItem(
        headlineContent = { Text("Send notes") },
        supportingContent = { Text("Signed in as ${s.loginName} on $host") },
        trailingContent = { Switch(checked = s.enabled, onCheckedChange = model::setEnabled) },
        colors = rowColors,
    )
    HorizontalDivider(Modifier.padding(horizontal = 20.dp))
    FolderRow(s.folder, s.enabled, model)
    HorizontalDivider(Modifier.padding(horizontal = 20.dp))
    ListItem(
        headlineContent = { Text("Include private books") },
        supportingContent = {
            Text(
                if (s.includePrivate) {
                    "Private books are sent too. In home they hide while presenting."
                } else {
                    "Private books and their notes stay on this phone."
                },
            )
        },
        trailingContent = { Switch(checked = s.includePrivate, onCheckedChange = model::setIncludePrivate, enabled = s.enabled) },
        colors = rowColors,
    )
    HorizontalDivider(Modifier.padding(horizontal = 20.dp))
    ListItem(
        headlineContent = { Text("Keep book files on Nextcloud") },
        supportingContent = {
            Text(
                if (s.syncBooks) {
                    "Books are copied to ${s.folder}/books, and books added on another phone appear here. Removing a book here removes its copy there. ${s.bookFileCount} ${if (s.bookFileCount == 1) "book" else "books"} there now."
                } else {
                    "Off: only your notes are sent. On: the books themselves too, as a backup and for your other phones."
                },
            )
        },
        trailingContent = { Switch(checked = s.syncBooks, onCheckedChange = model::setSyncBooks, enabled = s.enabled) },
        colors = rowColors,
    )
    HorizontalDivider(Modifier.padding(horizontal = 20.dp))

    Column(Modifier.padding(horizontal = 20.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        val status = when {
            !s.enabled -> "Sending is off. Nothing leaves this phone."
            s.lastError != null -> s.lastError
            s.lastSentAt != null -> "Up to date: ${s.bookCount} ${if (s.bookCount == 1) "book" else "books"}, last sent ${
                DateUtils.getRelativeTimeSpanString(s.lastSentAt, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS)
            }."
            else -> "Sends a moment after you add or change a note, whenever the phone is online. Edits made in home come back the same way."
        }
        Text(
            status,
            style = MaterialTheme.typography.bodyMedium,
            color = if (s.enabled && s.lastError != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilledTonalButton(onClick = model::sendNow, enabled = s.enabled) { Text("Send now") }
            TextButton(onClick = { confirmDisconnect = true }) { Text("Disconnect") }
        }
    }

    if (confirmDisconnect) {
        AlertDialog(
            onDismissRequest = { confirmDisconnect = false },
            title = { Text("Disconnect from Nextcloud?") },
            text = { Text("Reed stops sending notes and signs out. Notes already on your Nextcloud, and in home, stay where they are.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDisconnect = false
                    model.disconnect()
                }) { Text("Disconnect") }
            },
            dismissButton = { TextButton(onClick = { confirmDisconnect = false }) { Text("Keep") } },
        )
    }
}

@Composable
private fun FolderRow(folder: String, enabled: Boolean, model: SyncViewModel) {
    var text by remember(folder) { mutableStateOf(folder) }
    val focus = LocalFocusManager.current
    LaunchedEffect(folder) { text = folder }
    Column(Modifier.padding(horizontal = 20.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            label = { Text("Folder on Nextcloud") },
            singleLine = true,
            enabled = enabled,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { focus.clearFocus() }),
            modifier = Modifier
                .fillMaxWidth()
                .onFocusChanged { if (!it.isFocused && text.trim() != folder) model.setFolder(text) },
        )
        Text(
            "Each book goes to $folder/notes as its own file. In home, choose the same folder under Settings › Notes sync.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun android.content.Context.openBrowser(url: String) =
    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))

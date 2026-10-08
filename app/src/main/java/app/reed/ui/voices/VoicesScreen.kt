package app.reed.ui.voices

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.reed.listen.CatalogVoice
import app.reed.listen.VoiceCatalog
import app.reed.listen.VoiceDownload
import app.reed.reed
import app.reed.ui.theme.Margin
import kotlin.math.roundToInt

/** Voices Reed can run on the phone for reading aloud: download, follow the progress, delete. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoicesScreen(onBack: () -> Unit) {
    val store = LocalContext.current.reed.readAloud.voices
    val installed by store.installed.collectAsState()
    val downloads by store.downloads.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(store) {
        store.failures.collect { snackbar.showSnackbar("Couldn't download the ${it.languageName} voice. Check the connection and try again.") }
    }
    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back") }
                },
                title = { Text("Read-aloud voices", style = MaterialTheme.typography.titleMedium) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(bottom = 32.dp)) {
            Text(
                "Reed reads a book aloud with the voice here for its language, and with the phone's own voice when there's none. The voices run on this phone; downloading one is the only time reading aloud goes online.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            )
            Spacer(Modifier.height(8.dp))
            HorizontalDivider(color = Margin.colors.rule)
            VoiceCatalog.forEach { voice ->
                VoiceRow(
                    voice = voice,
                    installed = voice.id in installed,
                    download = downloads[voice.id],
                    onDownload = { store.download(voice) },
                    onCancel = { store.cancel(voice) },
                    onDelete = { store.delete(voice) },
                )
            }
        }
    }
}

@Composable
private fun VoiceRow(
    voice: CatalogVoice,
    installed: Boolean,
    download: VoiceDownload?,
    onDownload: () -> Unit,
    onCancel: () -> Unit,
    onDelete: () -> Unit,
) {
    ListItem(
        headlineContent = { Text("${voice.languageName} · ${voice.name}") },
        supportingContent = {
            when {
                download != null -> Column {
                    Text(download.label(voice), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(8.dp))
                    DownloadProgress(download, Modifier.fillMaxWidth())
                }
                installed -> Text("On this phone")
                else -> Text("${voice.sizeMb} MB")
            }
        },
        trailingContent = {
            when {
                download != null -> TextButton(onClick = onCancel) { Text("Cancel") }
                installed -> TextButton(onClick = onDelete) { Text("Delete") }
                else -> TextButton(onClick = onDownload) { Text("Download") }
            }
        },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
    )
}

/** "Downloading German voice · 34%", or "Preparing the voice" while it unpacks. */
fun VoiceDownload.label(voice: CatalogVoice): String = when (this) {
    is VoiceDownload.Downloading -> listOfNotNull(
        "Downloading ${voice.languageName} voice",
        fraction?.let { "${(it * 100).roundToInt()}%" },
    ).joinToString("  ·  ")
    VoiceDownload.Unpacking -> "Preparing the ${voice.languageName} voice"
}

/** The progress line of DESIGN.md: 2dp ink on a rule track, determinate while the size is known. */
@Composable
fun DownloadProgress(download: VoiceDownload, modifier: Modifier = Modifier) {
    val fraction = (download as? VoiceDownload.Downloading)?.fraction
    if (fraction != null) {
        LinearProgressIndicator(
            progress = { fraction },
            modifier = modifier.height(2.dp),
            color = MaterialTheme.colorScheme.onSurface,
            trackColor = Margin.colors.rule,
            strokeCap = StrokeCap.Butt,
            gapSize = 0.dp,
            drawStopIndicator = {},
        )
    } else {
        LinearProgressIndicator(
            modifier = modifier.height(2.dp),
            color = MaterialTheme.colorScheme.onSurface,
            trackColor = Margin.colors.rule,
            strokeCap = StrokeCap.Butt,
            gapSize = 0.dp,
        )
    }
}

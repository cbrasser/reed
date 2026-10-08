package app.reed.reader

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.Headphones
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.SkipNext
import androidx.compose.material.icons.outlined.SkipPrevious
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.reed.data.BookFormat
import app.reed.data.Note
import app.reed.ui.components.NoteCount
import app.reed.ui.components.NotesList
import app.reed.ui.notes.EmptyNotes
import app.reed.ui.theme.Margin
import app.reed.ui.theme.rememberBookFont
import kotlin.math.roundToInt

/** Everything drawn over the book: chrome, sheets and messages. Empty space passes touches through. */
@Composable
fun ReaderOverlay(
    model: ReaderViewModel,
    loading: Boolean,
    onBack: () -> Unit,
    onOpenNote: (Note) -> Unit,
    onListen: () -> Unit,
) {
    val book by model.book.collectAsStateWithLifecycle()
    val listening by model.listening.collectAsStateWithLifecycle()
    val listenSpeed by model.listenSpeed.collectAsStateWithLifecycle()
    val notes by model.notes.collectAsStateWithLifecycle()
    val settings by model.settings.collectAsStateWithLifecycle()
    val chrome by model.chromeVisible.collectAsStateWithLifecycle()
    val sheet by model.sheet.collectAsStateWithLifecycle()
    val draft by model.draft.collectAsStateWithLifecycle()
    val locator by model.currentLocator.collectAsStateWithLifecycle()
    val bookFont = rememberBookFont(settings.typeface)
    val snackbar = remember { SnackbarHostState() }
    val format = book?.format

    LaunchedEffect(model) {
        model.messages.collect { message ->
            val result = snackbar.showSnackbar(message.text, actionLabel = message.action)
            if (result == SnackbarResult.ActionPerformed) message.onAction?.invoke()
        }
    }

    Box(Modifier.fillMaxSize()) {
        if (loading) {
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth().statusBarsPadding().height(2.dp),
                color = MaterialTheme.colorScheme.onSurface,
                trackColor = Margin.colors.rule,
                strokeCap = StrokeCap.Butt,
                gapSize = 0.dp,
            )
        }

        AnimatedVisibility(
            visible = chrome,
            enter = fadeIn(tween(160)) + slideInVertically(tween(200)) { -it / 3 },
            exit = fadeOut(tween(140)) + slideOutVertically(tween(160)) { -it / 3 },
            modifier = Modifier.align(Alignment.TopCenter),
        ) {
            TopChrome(
                title = book?.title.orEmpty(),
                chapter = locator?.title,
                noteCount = notes.size,
                onBack = onBack,
                onNotes = { model.sheet.value = ReaderSheet.NOTES },
                onSettings = { model.sheet.value = ReaderSheet.SETTINGS },
            )
        }

        AnimatedVisibility(
            visible = chrome,
            enter = fadeIn(tween(160)) + slideInVertically(tween(200)) { it / 3 },
            exit = fadeOut(tween(140)) + slideOutVertically(tween(160)) { it / 3 },
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            BottomChrome(
                progression = locator?.locations?.totalProgression ?: book?.progression ?: 0.0,
                page = if (format == BookFormat.PDF) locator?.locations?.position else null,
                onNotePage = model::startPageNote,
                // PDFs carry no text Readium can hand to a voice.
                onListen = onListen.takeIf { format == BookFormat.EPUB && listening == null },
                listenRow = listening?.let { current ->
                    {
                        ListenRow(
                            playing = current.playing,
                            speed = listenSpeed,
                            onPlay = model::resumeListening,
                            onPause = model::pauseListening,
                            onPrevious = model::previousSentence,
                            onNext = model::nextSentence,
                            onSpeed = model::cycleListenSpeed,
                            onStop = model::stopListening,
                        )
                    }
                },
            )
        }

        SnackbarHost(
            snackbar,
            modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = if (!chrome) 8.dp else if (listening != null) 152.dp else 88.dp),
        )
    }

    when (sheet) {
        ReaderSheet.NOTES -> NotesSheet(
            notes = notes,
            format = format,
            bookFont = bookFont,
            onOpen = { note ->
                model.sheet.value = ReaderSheet.NONE
                onOpenNote(note)
            },
            onDismiss = { model.sheet.value = ReaderSheet.NONE },
        )
        ReaderSheet.SETTINGS -> SettingsSheet(
            settings = settings,
            textControls = format != BookFormat.PDF,
            onChange = model::updateSettings,
            onDismiss = { model.sheet.value = ReaderSheet.NONE },
        )
        ReaderSheet.NONE -> Unit
    }

    draft?.let { current ->
        NoteSheet(
            draft = current,
            bookFont = bookFont,
            initialLanguage = DictationLanguage.forBook(book?.language),
            onSave = model::saveDraft,
            onDelete = model::deleteDraftNote,
        )
    }
}

@Composable
private fun TopChrome(
    title: String,
    chapter: String?,
    noteCount: Int,
    onBack: () -> Unit,
    onNotes: () -> Unit,
    onSettings: () -> Unit,
) {
    Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 0.dp, shadowElevation = 0.dp) {
        Column {
            Row(
                Modifier.statusBarsPadding().fillMaxWidth().heightIn(min = 64.dp).padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back to library")
                }
                Column(Modifier.weight(1f).padding(horizontal = 4.dp)) {
                    Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (!chapter.isNullOrBlank()) {
                        Text(
                            chapter,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                TextButton(
                    onClick = onNotes,
                    modifier = Modifier.semantics { contentDescription = "Notes, $noteCount" },
                    contentPadding = PaddingValues(horizontal = 12.dp),
                ) {
                    NoteCount(noteCount, withWord = false, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface)
                }
                IconButton(onClick = onSettings) {
                    Icon(Icons.Outlined.TextFields, contentDescription = "Reading settings")
                }
            }
            HorizontalDivider(color = Margin.colors.rule)
        }
    }
}

@Composable
private fun BottomChrome(
    progression: Double,
    page: Int?,
    onNotePage: () -> Unit,
    onListen: (() -> Unit)?,
    listenRow: (@Composable () -> Unit)?,
) {
    Surface(color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.navigationBarsPadding()) {
            LinearProgressIndicator(
                progress = { progression.toFloat() },
                modifier = Modifier.fillMaxWidth().height(2.dp),
                color = MaterialTheme.colorScheme.onSurface,
                trackColor = Margin.colors.rule,
                strokeCap = StrokeCap.Butt,
                gapSize = 0.dp,
                drawStopIndicator = {},
            )
            listenRow?.invoke()
            Row(
                Modifier.fillMaxWidth().heightIn(min = 72.dp).padding(start = 20.dp, end = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    listOfNotNull(page?.let { "p. $it" }, "${(progression * 100).roundToInt()}%").joinToString("  ·  "),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                if (onListen != null) {
                    IconButton(onClick = onListen) {
                        Icon(Icons.Outlined.Headphones, contentDescription = "Read aloud")
                    }
                    Spacer(Modifier.width(4.dp))
                }
                FilledTonalButton(onClick = onNotePage) {
                    Icon(Icons.Outlined.EditNote, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Note page")
                }
            }
        }
    }
}

/** Read-aloud controls: stop, sentence back, play/pause, sentence forward, speed. */
@Composable
private fun ListenRow(
    playing: Boolean,
    speed: Double,
    onPlay: () -> Unit,
    onPause: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onSpeed: () -> Unit,
    onStop: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 72.dp).padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onStop) {
            Icon(Icons.Outlined.Close, contentDescription = "Stop reading aloud")
        }
        Spacer(Modifier.weight(1f))
        IconButton(onClick = onPrevious) {
            Icon(Icons.Outlined.SkipPrevious, contentDescription = "Previous sentence")
        }
        FilledTonalIconButton(
            onClick = if (playing) onPause else onPlay,
            modifier = Modifier.padding(horizontal = 12.dp).size(56.dp),
        ) {
            Icon(
                if (playing) Icons.Outlined.Pause else Icons.Outlined.PlayArrow,
                contentDescription = if (playing) "Pause" else "Resume reading aloud",
                modifier = Modifier.size(28.dp),
            )
        }
        IconButton(onClick = onNext) {
            Icon(Icons.Outlined.SkipNext, contentDescription = "Next sentence")
        }
        Spacer(Modifier.weight(1f))
        val label = speed.speedLabel()
        TextButton(
            onClick = onSpeed,
            modifier = Modifier.widthIn(min = 64.dp).semantics { contentDescription = "Speed $label, change" },
        ) {
            Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface)
        }
    }
}

private fun Double.speedLabel(): String =
    if (this % 1.0 == 0.0) "${toInt()}×" else String.format(java.util.Locale.ROOT, "%.1f×", this)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NotesSheet(
    notes: List<Note>,
    format: BookFormat?,
    bookFont: androidx.compose.ui.text.font.FontFamily,
    onOpen: (Note) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Notes", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            NoteCount(notes.size, color = Margin.colors.pencil)
        }
        if (notes.isEmpty()) {
            Box(Modifier.fillMaxWidth().height(280.dp)) { EmptyNotes(format) }
        } else {
            NotesList(
                notes = notes,
                bookFont = bookFont,
                onOpen = onOpen,
                contentPadding = PaddingValues(bottom = 32.dp),
                background = MaterialTheme.colorScheme.surfaceContainerLow,
            )
        }
    }
}

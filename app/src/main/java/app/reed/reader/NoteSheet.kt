package app.reed.reader

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import app.reed.data.NoteKind
import app.reed.ui.components.MarginTick
import app.reed.ui.components.TickForm
import app.reed.ui.theme.Margin
import app.reed.ui.theme.reedSegmentedColors
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteSheet(
    draft: NoteDraft,
    bookFont: FontFamily,
    initialLanguage: DictationLanguage,
    onSave: (String) -> Unit,
    onDelete: () -> Unit,
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val dictation = remember { Dictation(context.applicationContext) }
    DisposableEffect(Unit) { onDispose { dictation.release() } }

    var field by remember(draft.noteId, draft.locator) {
        mutableStateOf(TextFieldValue(draft.text, TextRange(draft.text.length)))
    }
    var language by rememberSaveable { mutableStateOf(initialLanguage) }
    var passageExpanded by rememberSaveable { mutableStateOf(false) }

    fun appendSpoken(phrase: String) {
        val current = field.text
        val joined = when {
            current.isBlank() -> phrase.replaceFirstChar { it.uppercase() }
            current.endsWith(" ") || current.endsWith("\n") -> current + phrase
            else -> "$current $phrase"
        }
        field = TextFieldValue(joined, TextRange(joined.length))
    }

    val recognizerScreen = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        result.data
            ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            ?.firstOrNull()
            ?.takeIf { it.isNotBlank() }
            ?.let { appendSpoken(it.trim()) }
    }

    fun openRecognizerScreen() {
        dictation.clearError()
        runCatching { recognizerScreen.launch(recognizerScreenIntent(language)) }
            .onFailure { dictation.start(language, ::appendSpoken) }
    }

    fun listen() = dictation.start(
        language = language,
        onFinal = ::appendSpoken,
        onStalled = { if (dictation.hasRecognizerScreen) openRecognizerScreen() else dictation.failStalled() },
    )

    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) listen() else dictation.permissionDenied()
    }

    fun toggleMic() {
        when {
            dictation.listening -> dictation.stop()
            !dictation.isAvailable && dictation.hasRecognizerScreen -> openRecognizerScreen()
            !dictation.isAvailable -> dictation.start(language, ::appendSpoken)
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED -> listen()
            else -> permission.launch(Manifest.permission.RECORD_AUDIO)
        }
    }


    fun close() {
        dictation.release()
        scope.launch { sheetState.hide() }.invokeOnCompletion {
            onSave(field.text)
        }
    }

    ModalBottomSheet(
        onDismissRequest = {
            dictation.release()
            onSave(field.text)
        },
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .imePadding()
                .navigationBarsPadding()
                .padding(start = 24.dp, end = 24.dp, bottom = 16.dp),
        ) {
            if (draft.noteId != null) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    IconButton(onClick = {
                        dictation.release()
                        scope.launch { sheetState.hide() }.invokeOnCompletion { onDelete() }
                    }) {
                        Icon(Icons.Outlined.DeleteOutline, contentDescription = "Delete note")
                    }
                }
            } else {
                Spacer(Modifier.height(8.dp))
            }

            Row(Modifier.height(IntrinsicSize.Min).animateContentSize(tween(200))) {
                MarginTick(
                    Modifier.padding(vertical = 3.dp),
                    form = if (draft.kind == NoteKind.PAGE) TickForm.DASHED else TickForm.DOUBLED,
                    color = Margin.colors.tick,
                )
                Spacer(Modifier.width(14.dp))
                if (draft.passage != null) {
                    Text(
                        draft.passage,
                        style = MaterialTheme.typography.bodyLarge.copy(
                            fontFamily = bookFont,
                            fontSize = 17.sp,
                            lineHeight = 26.sp,
                        ),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = if (passageExpanded) Int.MAX_VALUE else 4,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.clickable(
                            onClickLabel = if (passageExpanded) "Show less" else "Show whole passage",
                        ) { passageExpanded = !passageExpanded },
                    )
                } else {
                    Text(
                        draft.page?.let { "Note on page $it" } ?: "Note on this page",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(vertical = 2.dp),
                    )
                }
            }
            draft.metaLabel()?.let { meta ->
                Text(
                    meta,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = 16.dp, top = 8.dp),
                )
            }

            Spacer(Modifier.height(12.dp))
            TextField(
                value = field,
                onValueChange = { field = it },
                placeholder = {
                    Text("Say or type your note", style = MaterialTheme.typography.bodyLarge.copy(fontSize = 17.sp))
                },
                textStyle = MaterialTheme.typography.bodyLarge.copy(fontSize = 17.sp, lineHeight = 26.sp),
                minLines = 3,
                maxLines = 9,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    focusedTextColor = Margin.colors.pencil,
                    unfocusedTextColor = Margin.colors.pencil,
                    cursorColor = Margin.colors.pencil,
                    focusedPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unfocusedPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
                modifier = Modifier.fillMaxWidth().padding(start = 2.dp),
            )

            DictationStatus(dictation, language, onRetry = ::toggleMic)

            Spacer(Modifier.height(16.dp))
            Box(Modifier.fillMaxWidth().height(80.dp)) {
                LanguageToggle(
                    selected = language,
                    enabled = !dictation.listening,
                    onSelect = {
                        language = it
                        dictation.clearError()
                    },
                    modifier = Modifier.align(Alignment.CenterStart),
                )
                MicButton(
                    listening = dictation.listening,
                    level = dictation.level,
                    language = language,
                    onClick = ::toggleMic,
                    modifier = Modifier.align(Alignment.Center),
                )
                Button(
                    onClick = { close() },
                    modifier = Modifier.align(Alignment.CenterEnd),
                ) { Text("Save") }
            }
        }
    }
}

@Composable
private fun DictationStatus(dictation: Dictation, language: DictationLanguage, onRetry: () -> Unit) {
    val context = LocalContext.current
    val error = dictation.error
    Box(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 24.dp)
            .padding(start = 18.dp)
            .semantics { liveRegion = LiveRegionMode.Polite },
    ) {
        when {
            dictation.listening -> Text(
                dictation.partial.ifEmpty { "Listening in ${language.spoken}…" },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            error != null -> Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    error.message(language),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.weight(1f, fill = false),
                )
                when (error) {
                    DictationError.PERMISSION -> TextButton(onClick = {
                        context.startActivity(
                            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                        )
                    }) { Text("Settings") }
                    DictationError.NO_SPEECH, DictationError.BUSY, DictationError.NETWORK -> TextButton(onClick = onRetry) {
                        Text("Try again")
                    }
                    else -> Unit
                }
            }
        }
    }
}

private fun DictationError.message(language: DictationLanguage): String = when (this) {
    DictationError.PERMISSION -> "Reed needs microphone access to take dictation."
    DictationError.NO_SPEECH -> "Didn't catch that."
    DictationError.NETWORK -> "Speech recognition needs a connection right now."
    DictationError.LANGUAGE -> "${language.spoken} dictation isn't installed on this phone. Add it in Google voice settings, or type instead."
    DictationError.UNAVAILABLE -> "No speech recognition app is installed. FUTO Voice Input works offline in English and German, or type your note."
    DictationError.BUSY -> "Speech recognition is busy."
    DictationError.OTHER -> "Dictation stopped unexpectedly."
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LanguageToggle(
    selected: DictationLanguage,
    enabled: Boolean,
    onSelect: (DictationLanguage) -> Unit,
    modifier: Modifier = Modifier,
) {
    SingleChoiceSegmentedButtonRow(modifier.width(112.dp)) {
        DictationLanguage.entries.forEachIndexed { index, lang ->
            SegmentedButton(
                selected = lang == selected,
                onClick = { onSelect(lang) },
                enabled = enabled,
                shape = SegmentedButtonDefaults.itemShape(index, DictationLanguage.entries.size),
                colors = reedSegmentedColors(),
                icon = {},
                label = { Text(lang.label) },
            )
        }
    }
}

/** The one place pencil yellow appears: the dictation button, which breathes with your voice. */
@Composable
private fun MicButton(
    listening: Boolean,
    level: Float,
    language: DictationLanguage,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = Margin.colors
    val halo by animateFloatAsState(
        targetValue = if (listening) 1f + level * 0.45f else 1f,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = 400f),
        label = "halo",
    )
    val ringAlpha by animateFloatAsState(if (listening) 1f else 0f, tween(180), label = "ring")
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = if (listening) colors.listening else colors.micRest,
        contentColor = if (listening) colors.onListening else colors.onMicRest,
        border = if (listening) null else androidx.compose.foundation.BorderStroke(1.5.dp, colors.micRestBorder),
        modifier = modifier
            .size(68.dp)
            .drawBehind {
                if (ringAlpha > 0f) {
                    val radius = size.minDimension / 2f
                    drawCircle(colors.listening.copy(alpha = 0.28f * ringAlpha), radius = radius * halo)
                    drawCircle(
                        colors.listening.copy(alpha = 0.6f * ringAlpha),
                        radius = radius * (1f + (halo - 1f) * 1.6f) + 4.dp.toPx(),
                        style = Stroke(width = 1.5.dp.toPx()),
                    )
                }
            },
    ) {
        Box(contentAlignment = Alignment.Center) {
            AnimatedVisibility(listening, enter = fadeIn(tween(120)), exit = fadeOut(tween(120))) {
                Icon(Icons.Outlined.Stop, contentDescription = "Stop dictation", modifier = Modifier.size(30.dp))
            }
            AnimatedVisibility(!listening, enter = fadeIn(tween(120)), exit = fadeOut(tween(120))) {
                Icon(
                    Icons.Outlined.Mic,
                    contentDescription = "Dictate in ${language.spoken}",
                    modifier = Modifier.size(30.dp),
                )
            }
        }
    }
}

/** Chapter and position, shown once beneath the passage. Page notes on PDFs already name their page. */
fun NoteDraft.metaLabel(): String? {
    val where = if (page != null) {
        if (passage == null) null else "p. $page"
    } else {
        "${(progression * 100).roundToInt()}%"
    }
    return listOfNotNull(chapter?.takeIf { it.isNotBlank() }, where).joinToString("  ·  ").ifEmpty { null }
}

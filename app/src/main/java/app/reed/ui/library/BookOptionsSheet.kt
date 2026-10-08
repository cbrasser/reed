package app.reed.ui.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material.icons.outlined.Notes
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.reed.data.BookWithCount
import app.reed.ui.components.BookCover
import app.reed.ui.components.NoteCount
import app.reed.ui.theme.Margin
import app.reed.ui.theme.reedSegmentedColors
import kotlinx.coroutines.launch
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookOptionsSheet(
    book: BookWithCount,
    canMakePrivate: Boolean,
    onDismiss: () -> Unit,
    onNotes: () -> Unit,
    onSetPrivate: (Boolean) -> Unit,
    onSetLanguage: (String) -> Unit,
    onRemove: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    var confirmRemove by remember { mutableStateOf(false) }
    fun closeThen(action: () -> Unit) {
        scope.launch { sheetState.hide() }.invokeOnCompletion {
            onDismiss()
            action()
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(Modifier.padding(bottom = 16.dp)) {
            Row(
                Modifier.padding(horizontal = 24.dp).padding(bottom = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BookCover(book.title, book.author, book.coverPath, Modifier.width(48.dp), isPrivate = false, elevation = 1.dp)
                Spacer(Modifier.width(16.dp))
                Column {
                    Text(book.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    book.author?.let {
                        Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                    }
                }
            }
            HorizontalDivider(color = Margin.colors.rule)
            BookLanguage(book.language, onSetLanguage)
            HorizontalDivider(color = Margin.colors.rule)
            Spacer(Modifier.height(8.dp))
            val itemColors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
            ListItem(
                headlineContent = { Text("Notes") },
                leadingContent = { Icon(Icons.Outlined.Notes, contentDescription = null) },
                trailingContent = { NoteCount(book.noteCount, withWord = false, color = Margin.colors.pencil) },
                colors = itemColors,
                modifier = Modifier.clickable { closeThen(onNotes) },
            )
            if (book.isPrivate) {
                ListItem(
                    headlineContent = { Text("Make visible") },
                    supportingContent = { Text("Show it in the library again") },
                    leadingContent = { Icon(Icons.Outlined.LockOpen, contentDescription = null) },
                    colors = itemColors,
                    modifier = Modifier.clickable { closeThen { onSetPrivate(false) } },
                )
            } else {
                ListItem(
                    headlineContent = {
                        Text(
                            "Make private",
                            color = if (canMakePrivate) Color.Unspecified else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                        )
                    },
                    supportingContent = {
                        Text(
                            if (canMakePrivate) {
                                "Hide it and its notes until you unlock"
                            } else {
                                "Set a screen lock in Android Settings first"
                            },
                        )
                    },
                    leadingContent = {
                        Icon(
                            Icons.Outlined.Lock,
                            contentDescription = null,
                            tint = if (canMakePrivate) LocalContentColor.current else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                        )
                    },
                    colors = itemColors,
                    modifier = Modifier.clickable(enabled = canMakePrivate) { closeThen { onSetPrivate(true) } },
                )
            }
            ListItem(
                headlineContent = { Text("Remove from library", color = MaterialTheme.colorScheme.error) },
                leadingContent = {
                    Icon(Icons.Outlined.DeleteOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                },
                colors = itemColors,
                modifier = Modifier.fillMaxWidth().clickable { confirmRemove = true },
            )
        }
    }

    if (confirmRemove) {
        AlertDialog(
            onDismissRequest = { confirmRemove = false },
            title = { Text("Remove “${book.title}”?") },
            text = {
                Text(
                    when (book.noteCount) {
                        0 -> "The book file will be deleted from Reed."
                        1 -> "The book file and your 1 note will be deleted from Reed. This can't be undone."
                        else -> "The book file and your ${book.noteCount} notes will be deleted from Reed. This can't be undone."
                    },
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmRemove = false
                    closeThen(onRemove)
                }) { Text("Remove", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmRemove = false }) { Text("Cancel") } },
        )
    }
}

/**
 * The book's language, which picks the read-aloud voice, hyphenation and the dictation default.
 * Some files declare the wrong one; English and German are offered, plus the declared language
 * if it's another.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BookLanguage(language: String?, onSetLanguage: (String) -> Unit) {
    val current = language?.substringBefore('-')?.lowercase()
    val choices = (listOf("en", "de") + listOfNotNull(current?.takeIf { it.isNotBlank() })).distinct()
    Column(Modifier.padding(horizontal = 24.dp, vertical = 16.dp)) {
        Text("Language", style = MaterialTheme.typography.labelLarge)
        Spacer(Modifier.height(4.dp))
        Text(
            "Picks the voice for reading aloud, hyphenation and dictation",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            choices.forEachIndexed { i, code ->
                SegmentedButton(
                    selected = current == code,
                    onClick = { if (current != code) onSetLanguage(code) },
                    shape = SegmentedButtonDefaults.itemShape(i, choices.size),
                    colors = reedSegmentedColors(),
                    icon = {},
                    label = { Text(Locale.forLanguageTag(code).getDisplayLanguage(Locale.ENGLISH).ifBlank { code }) },
                )
            }
        }
    }
}

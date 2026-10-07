package app.reed.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.heightIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.reed.data.Note
import app.reed.data.NoteKind
import app.reed.ui.theme.Margin
import app.reed.ui.theme.passageStyle
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

/** Every passage + note pair for one book, grouped by chapter, in reading order. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun NotesList(
    notes: List<Note>,
    bookFont: FontFamily,
    onOpen: (Note) -> Unit,
    modifier: Modifier = Modifier,
    state: LazyListState = rememberLazyListState(),
    contentPadding: PaddingValues = PaddingValues(bottom = 24.dp),
    background: Color = MaterialTheme.colorScheme.surface,
) {
    val chapters = notes.groupBy { it.chapter?.takeIf(String::isNotBlank) ?: "" }.entries.toList()
    LazyColumn(modifier.fillMaxSize(), state = state, contentPadding = contentPadding) {
        chapters.forEachIndexed { index, (chapter, chapterNotes) ->
            if (chapter.isNotEmpty() || chapters.size > 1) {
                stickyHeader(key = "chapter-$index") {
                    ChapterHeader(chapter.ifEmpty { "Untitled section" }, showRule = index > 0, background = background)
                }
            }
            items(chapterNotes, key = { it.id }) { note ->
                NoteRow(note, bookFont, onClick = { onOpen(note) })
            }
        }
    }
}

@Composable
private fun ChapterHeader(title: String, showRule: Boolean, background: Color) {
    Surface(color = background) {
        Column(Modifier.fillMaxWidth()) {
            if (showRule) HorizontalDivider(color = Margin.colors.rule, thickness = 1.dp)
            Text(
                title,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 8.dp),
            )
        }
    }
}

@Composable
fun NoteRow(
    note: Note,
    bookFont: FontFamily,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .fillMaxWidth()
            .clickable(onClickLabel = "Open in book", onClick = onClick)
            .padding(start = 20.dp, end = 24.dp, top = 14.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(Modifier.height(IntrinsicSize.Min)) {
            MarginTick(
                Modifier.padding(vertical = 3.dp),
                form = if (note.kind == NoteKind.PAGE) TickForm.DASHED else TickForm.SOLID,
            )
            Spacer(Modifier.width(14.dp))
            if (note.passage != null) {
                Text(
                    note.passage,
                    style = passageStyle(bookFont),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 5,
                    overflow = TextOverflow.Ellipsis,
                )
            } else {
                Text(
                    note.pageLabel(),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 2.dp),
                )
            }
        }
        Column(Modifier.padding(start = 16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            var expanded by rememberSaveable(note.id) { mutableStateOf(false) }
            var overflows by remember(note.text) { mutableStateOf(false) }
            Text(
                note.text,
                style = MaterialTheme.typography.bodyLarge,
                color = Margin.colors.pencil,
                maxLines = if (expanded) Int.MAX_VALUE else 8,
                overflow = TextOverflow.Ellipsis,
                onTextLayout = { if (!expanded) overflows = it.hasVisualOverflow },
            )
            if (overflows || expanded) {
                TextButton(
                    onClick = { expanded = !expanded },
                    contentPadding = PaddingValues(horizontal = 0.dp),
                    modifier = Modifier.heightIn(min = 48.dp),
                ) { Text(if (expanded) "Show less" else "Show whole note") }
            }
            Text(
                listOfNotNull(note.locationLabel(), note.dateLabel()).joinToString("  ·  "),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

fun Note.locationLabel(): String =
    page?.let { "p. $it" } ?: "${(progression * 100).roundToInt()}%"

fun Note.pageLabel(): String = page?.let { "Page $it" } ?: "Page at ${(progression * 100).roundToInt()}%"

private val dateFormat = SimpleDateFormat("d MMM", Locale.ENGLISH)

private fun Note.dateLabel(): String = dateFormat.format(Date(createdAt))

package app.reed.ui.notes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.reed.data.Book
import app.reed.data.BookFormat
import app.reed.data.Note
import app.reed.data.Typeface
import app.reed.ui.components.MarginTick
import app.reed.ui.components.NoteCount
import app.reed.ui.components.NotesList
import app.reed.ui.theme.Margin
import app.reed.ui.theme.rememberBookFont

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotesScreen(
    book: Book?,
    notes: List<Note>?,
    typeface: Typeface,
    onBack: () -> Unit,
    onOpen: (Note) -> Unit,
) {
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val bookFont = rememberBookFont(typeface)
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                    }
                },
                title = {
                    Column {
                        Text(
                            book?.title.orEmpty(),
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (notes != null) {
                            NoteCount(
                                notes.size,
                                style = MaterialTheme.typography.labelMedium,
                                color = Margin.colors.pencil,
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
            )
        },
    ) { padding ->
        when {
            notes == null -> Box(Modifier.fillMaxSize().padding(padding))
            notes.isEmpty() -> EmptyNotes(book?.format, Modifier.padding(padding))
            else -> NotesList(
                notes = notes,
                bookFont = bookFont,
                onOpen = onOpen,
                contentPadding = PaddingValues(
                    top = padding.calculateTopPadding(),
                    bottom = padding.calculateBottomPadding() + 32.dp,
                ),
            )
        }
    }
}

@Composable
fun EmptyNotes(format: BookFormat?, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize().padding(horizontal = 32.dp), contentAlignment = Alignment.Center) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(bottom = 48.dp)) {
            Text("No notes yet", style = MaterialTheme.typography.headlineSmall)
            Text(
                if (format == BookFormat.PDF) {
                    "While reading, tap the page to show the controls, then Note page."
                } else {
                    "While reading, select a passage and tap Note. To note a whole page, tap the page and choose Note page."
                },
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

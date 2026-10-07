package app.reed.ui.library

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Sort
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.reed.data.BookWithCount
import app.reed.data.LibrarySort
import app.reed.ui.components.BookCover
import app.reed.ui.components.NoteCount
import app.reed.ui.theme.Margin
import kotlin.math.roundToInt

private val ImportTypes = arrayOf("application/epub+zip", "application/pdf", "application/octet-stream")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    state: LibraryState,
    messages: kotlinx.coroutines.flow.Flow<LibraryMessage>,
    onOpenBook: (BookWithCount) -> Unit,
    onOpenNotes: (BookWithCount) -> Unit,
    onImport: (List<android.net.Uri>) -> Unit,
    onQuery: (String) -> Unit,
    onSort: (LibrarySort) -> Unit,
    onUnlock: () -> Unit,
    onLock: () -> Unit,
    onSetPrivate: (BookWithCount, Boolean) -> Unit,
    onRemove: (BookWithCount) -> Unit,
    canMakePrivate: Boolean,
) {
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(messages) {
        messages.collect { message ->
            val result = snackbar.showSnackbar(
                message.text,
                actionLabel = message.action,
                withDismissAction = message.action == null && message.text.length > 60,
                duration = if (message.action != null || message.text.length > 60) SnackbarDuration.Long else SnackbarDuration.Short,
            )
            if (result == SnackbarResult.ActionPerformed) message.onAction?.invoke()
        }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        onImport(uris)
    }
    val addBook = { picker.launch(ImportTypes) }

    var searching by rememberSaveable { mutableStateOf(false) }
    var optionsFor by remember { mutableStateOf<BookWithCount?>(null) }
    val gridState = rememberLazyGridState()
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val fabExpanded by remember { derivedStateOf { gridState.firstVisibleItemIndex == 0 } }
    val isEmpty = state.loaded && state.totalVisible == 0 && !state.unlocked

    BackHandler(enabled = searching) {
        searching = false
        onQuery("")
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            if (searching) {
                SearchBar(
                    query = state.query,
                    onQuery = onQuery,
                    onClose = {
                        searching = false
                        onQuery("")
                    },
                )
            } else {
                LibraryTopBar(
                    state = state,
                    scrollBehavior = scrollBehavior,
                    onSearch = { searching = true },
                    onSort = onSort,
                    onUnlock = onUnlock,
                    onLock = onLock,
                    showLibraryActions = !isEmpty,
                )
            }
        },
        floatingActionButton = {
            if (!isEmpty) {
                ExtendedFloatingActionButton(
                    onClick = addBook,
                    expanded = fabExpanded,
                    icon = { Icon(Icons.Outlined.Add, contentDescription = null) },
                    text = { Text("Add book") },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        when {
            !state.loaded -> Box(Modifier.fillMaxSize().padding(padding))
            isEmpty -> EmptyLibrary(onAdd = addBook, importing = state.importing, modifier = Modifier.padding(padding))
            else -> LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 104.dp),
                state = gridState,
                contentPadding = PaddingValues(
                    start = 20.dp,
                    end = 20.dp,
                    top = padding.calculateTopPadding() + 4.dp,
                    bottom = padding.calculateBottomPadding() + 96.dp,
                ),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                if (state.importing > 0) {
                    fullWidth("importing") { ImportingRow(state.importing) }
                }
                if (state.unlocked) {
                    privateSection(state, onOpenBook, onLongPress = { optionsFor = it }, onLock = onLock)
                }
                if (state.readingNow.isNotEmpty() && state.totalVisible > 3) {
                    fullWidth("reading-now") {
                        ReadingNow(state.readingNow, onOpenBook, onLongPress = { optionsFor = it })
                    }
                }
                if (state.query.isBlank()) {
                    fullWidth("all-header") {
                        SectionHeader(
                            title = "All books",
                            trailing = state.totalVisible.toString(),
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
                if (state.books.isEmpty() && state.query.isNotBlank() && state.privateBooks.isEmpty()) {
                    fullWidth("no-results") {
                        Text(
                            "No books match “${state.query}”.",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 32.dp),
                        )
                    }
                }
                items(state.books, key = { it.id }) { book ->
                    BookCell(book, onClick = { onOpenBook(book) }, onLongClick = { optionsFor = book })
                }
            }
        }
    }

    optionsFor?.let { book ->
        BookOptionsSheet(
            book = book,
            canMakePrivate = canMakePrivate,
            onDismiss = { optionsFor = null },
            onNotes = { onOpenNotes(book) },
            onSetPrivate = { onSetPrivate(book, it) },
            onRemove = { onRemove(book) },
        )
    }
}

private fun LazyGridScope.fullWidth(key: String, content: @Composable () -> Unit) {
    item(key = key, span = { GridItemSpan(maxLineSpan) }) { content() }
}

private fun LazyGridScope.privateSection(
    state: LibraryState,
    onOpenBook: (BookWithCount) -> Unit,
    onLongPress: (BookWithCount) -> Unit,
    onLock: () -> Unit,
) {
    fullWidth("private-header") {
        SectionHeader(
            title = "Private",
            trailing = state.privateBooks.size.toString(),
            action = { TextButton(onClick = onLock) { Text("Lock") } },
        )
    }
    if (state.privateBooks.isEmpty()) {
        fullWidth("private-empty") {
            Text(
                "No private books. Long-press a book and choose Make private to hide it here.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    } else {
        items(state.privateBooks, key = { "p-${it.id}" }) { book ->
            BookCell(book, onClick = { onOpenBook(book) }, onLongClick = { onLongPress(book) })
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LibraryTopBar(
    state: LibraryState,
    scrollBehavior: androidx.compose.material3.TopAppBarScrollBehavior,
    onSearch: () -> Unit,
    onSort: (LibrarySort) -> Unit,
    onUnlock: () -> Unit,
    onLock: () -> Unit,
    showLibraryActions: Boolean,
) {
    var sortOpen by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    TopAppBar(
        title = { Text("Reed", style = MaterialTheme.typography.headlineSmall) },
        scrollBehavior = scrollBehavior,
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface,
            scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
        actions = {
            if (showLibraryActions) {
                IconButton(onClick = onSearch) { Icon(Icons.Outlined.Search, contentDescription = "Search books") }
                Box {
                    IconButton(onClick = { sortOpen = true }) {
                        Icon(Icons.AutoMirrored.Outlined.Sort, contentDescription = "Sort, ${state.sort.label}")
                    }
                    DropdownMenu(expanded = sortOpen, onDismissRequest = { sortOpen = false }) {
                        LibrarySort.entries.forEach { sort ->
                            DropdownMenuItem(
                                text = { Text(sort.label) },
                                onClick = {
                                    onSort(sort)
                                    sortOpen = false
                                },
                                trailingIcon = {
                                    if (sort == state.sort) Icon(Icons.Outlined.Check, contentDescription = "Selected")
                                },
                            )
                        }
                    }
                }
            }
            Box {
                IconButton(onClick = { menuOpen = true }) { Icon(Icons.Outlined.MoreVert, contentDescription = "More") }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    if (state.unlocked) {
                        DropdownMenuItem(
                            text = { Text("Lock private books") },
                            leadingIcon = { Icon(Icons.Outlined.Lock, contentDescription = null) },
                            onClick = {
                                menuOpen = false
                                onLock()
                            },
                        )
                    } else {
                        DropdownMenuItem(
                            text = { Text("Show private books") },
                            leadingIcon = { Icon(Icons.Outlined.LockOpen, contentDescription = null) },
                            onClick = {
                                menuOpen = false
                                onUnlock()
                            },
                        )
                    }
                }
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchBar(query: String, onQuery: (String) -> Unit, onClose: () -> Unit) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    TopAppBar(
        navigationIcon = {
            IconButton(onClick = onClose) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Close search")
            }
        },
        title = {
            TextField(
                value = query,
                onValueChange = onQuery,
                placeholder = { Text("Title or author") },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { }),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                ),
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
            )
        },
        actions = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQuery("") }) { Icon(Icons.Outlined.Close, contentDescription = "Clear") }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    )
}

@Composable
private fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    trailing: String? = null,
    action: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier.fillMaxWidth().height(40.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
        if (trailing != null) {
            Spacer(Modifier.width(8.dp))
            Text(trailing, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.weight(1f))
        action?.invoke()
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BookCell(book: BookWithCount, onClick: () -> Unit, onLongClick: () -> Unit) {
    Column(
        Modifier.combinedClickable(
            onClick = onClick,
            onClickLabel = "Read",
            onLongClick = onLongClick,
            onLongClickLabel = "Book options",
        ),
    ) {
        BookCover(book.title, book.author, book.coverPath, Modifier.fillMaxWidth(), isPrivate = book.isPrivate)
        Spacer(Modifier.height(10.dp))
        Text(
            book.title,
            style = MaterialTheme.typography.titleSmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        if (book.author != null) {
            Text(
                book.author,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                book.progressLabel(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.weight(1f))
            if (book.noteCount > 0) {
                NoteCount(
                    book.noteCount,
                    withWord = false,
                    style = MaterialTheme.typography.labelSmall,
                    color = Margin.colors.pencil,
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ReadingNow(
    books: List<BookWithCount>,
    onOpen: (BookWithCount) -> Unit,
    onLongPress: (BookWithCount) -> Unit,
) {
    Column {
        SectionHeader("Reading now")
        Spacer(Modifier.height(8.dp))
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(end = 20.dp),
            modifier = Modifier.padding(end = 0.dp),
        ) {
            items(books, key = { "r-${it.id}" }) { book ->
                Column(
                    Modifier
                        .width(136.dp)
                        .combinedClickable(
                            onClick = { onOpen(book) },
                            onClickLabel = "Continue reading",
                            onLongClick = { onLongPress(book) },
                            onLongClickLabel = "Book options",
                        ),
                ) {
                    BookCover(book.title, book.author, book.coverPath, Modifier.fillMaxWidth(), elevation = 3.dp)
                    Spacer(Modifier.height(10.dp))
                    Text(book.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        LinearProgressIndicator(
                            progress = { book.progression.toFloat() },
                            modifier = Modifier.weight(1f).height(2.dp),
                            color = MaterialTheme.colorScheme.onSurface,
                            trackColor = Margin.colors.rule,
                            strokeCap = StrokeCap.Butt,
                            gapSize = 0.dp,
                            drawStopIndicator = {},
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "${(book.progression * 100).roundToInt()}%",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ImportingRow(count: Int) {
    Column(Modifier.fillMaxWidth()) {
        Text(
            if (count == 1) "Adding 1 book…" else "Adding $count books…",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        LinearProgressIndicator(
            modifier = Modifier.fillMaxWidth().height(2.dp),
            color = MaterialTheme.colorScheme.onSurface,
            trackColor = Margin.colors.rule,
            strokeCap = StrokeCap.Butt,
            gapSize = 0.dp,
        )
    }
}

@Composable
private fun EmptyLibrary(onAdd: () -> Unit, importing: Int, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize().padding(horizontal = 32.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.Start, modifier = Modifier.padding(bottom = 48.dp)) {
            Text("Your shelf is empty", style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(16.dp))
            Text(
                "Add an EPUB or PDF from your phone. While you read, select a passage and tap Note to pencil a thought beside it.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Start,
            )
            Spacer(Modifier.height(28.dp))
            if (importing > 0) {
                ImportingRow(importing)
            } else {
                Button(onClick = onAdd) {
                    Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                    Text("Add book")
                }
            }
        }
    }
}

private fun BookWithCount.progressLabel(): String = when {
    lastOpenedAt == null -> "New"
    progression >= 0.99 -> "Finished"
    else -> "${(progression * 100).roundToInt()}%"
}

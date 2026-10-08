package app.reed

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.material3.Surface
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.fillMaxSize
import androidx.core.content.IntentCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.reed.data.ReadingSettings
import app.reed.privacy.UnlockResult
import app.reed.reader.ReaderActivity
import app.reed.ui.library.LibraryScreen
import app.reed.ui.library.LibraryViewModel
import app.reed.ui.notes.NotesScreen
import app.reed.ui.sync.SyncScreen
import app.reed.ui.theme.ReedTheme

class MainActivity : FragmentActivity() {
    private val libraryModel: LibraryViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        window.isNavigationBarContrastEnforced = false
        val app = reed
        if (savedInstanceState == null) importFrom(intent)

        setContent {
            ReedTheme {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    val state by libraryModel.state.collectAsStateWithLifecycle()
                    val unlocked by app.privacyLock.unlocked.collectAsState()
                    var notesBookId by rememberSaveable { mutableStateOf<Long?>(null) }
                    val canMakePrivate = remember(unlocked) { app.privacyLock.isAvailable(this) }

                    // Private content never shows in Recents or screenshots.
                    LaunchedEffect(unlocked) {
                        if (unlocked) {
                            window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
                        } else {
                            window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                        }
                    }

                    var sendNotes by rememberSaveable { mutableStateOf(false) }
                    BackHandler(enabled = notesBookId != null) { notesBookId = null }
                    BackHandler(enabled = sendNotes) { sendNotes = false }

                    if (sendNotes) {
                        SyncScreen(onBack = { sendNotes = false })
                        return@Surface
                    }

                    AnimatedContent(
                        targetState = notesBookId,
                        transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(120)) },
                        label = "screen",
                    ) { bookId ->
                        if (bookId == null) {
                            LibraryScreen(
                                state = state,
                                messages = libraryModel.events,
                                onOpenBook = { startActivity(ReaderActivity.intent(this@MainActivity, it.id)) },
                                onOpenNotes = { notesBookId = it.id },
                                onImport = libraryModel::import,
                                onQuery = libraryModel::setQuery,
                                onSort = { libraryModel.setSort(it) },
                                onUnlock = { unlock() },
                                onLock = libraryModel::lockPrivate,
                                onSendNotes = { sendNotes = true },
                                onSetPrivate = { book, private -> libraryModel.setPrivate(book, private) },
                                onSetLanguage = { book, language -> libraryModel.setLanguage(book, language) },
                                onRemove = { libraryModel.remove(it) },
                                canMakePrivate = canMakePrivate,
                            )
                        } else {
                            val book by remember(bookId) { app.library.observeBook(bookId) }.collectAsState(null)
                            val notes by remember(bookId) { app.library.notes(bookId) }.collectAsState(null)
                            val settings by remember { app.settings.reading }.collectAsState(ReadingSettings())

                            // A private book's notes vanish the moment the lock closes.
                            LaunchedEffect(book, unlocked) {
                                if (book?.isPrivate == true && !unlocked) notesBookId = null
                            }

                            NotesScreen(
                                book = book,
                                notes = notes,
                                typeface = settings.typeface,
                                onBack = { notesBookId = null },
                                onOpen = { note ->
                                    startActivity(ReaderActivity.intent(this@MainActivity, bookId, note.locator, note.id))
                                },
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        importFrom(intent)
    }

    /** Books shared to Reed or opened with it from another app are added to the library. */
    private fun importFrom(intent: Intent?) {
        val uri = when (intent?.action) {
            Intent.ACTION_VIEW -> intent.data
            Intent.ACTION_SEND -> IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
            else -> null
        } ?: return
        libraryModel.import(listOf(uri))
    }

    private fun unlock() {
        reed.privacyLock.unlock(this) { result ->
            when (result) {
                UnlockResult.Unlocked, UnlockResult.Cancelled -> Unit
                UnlockResult.Unavailable -> libraryModel.say("Set a screen lock in Android Settings to use private books.")
                is UnlockResult.Failed -> libraryModel.say(result.message)
            }
        }
    }
}

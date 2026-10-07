package app.reed.reader

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.ActionMode
import android.view.Menu
import android.view.MenuItem
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.updatePadding
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentFactory
import androidx.fragment.app.commitNow
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import app.reed.data.BookFormat
import app.reed.data.Note
import app.reed.data.ReadingTheme
import app.reed.data.toLocator
import app.reed.databinding.ActivityReaderBinding
import app.reed.reed
import app.reed.ui.theme.Palette
import app.reed.ui.theme.ReedTheme
import app.reed.ui.theme.isDark
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import org.readium.adapter.pdfium.navigator.PdfiumEngineProvider
import org.readium.r2.navigator.DecorableNavigator
import org.readium.r2.navigator.SelectableNavigator
import org.readium.r2.navigator.VisualNavigator
import org.readium.r2.navigator.epub.EpubNavigatorFactory
import org.readium.r2.navigator.epub.EpubNavigatorFragment
import org.readium.r2.navigator.input.InputListener
import org.readium.r2.navigator.input.TapEvent
import org.readium.r2.navigator.OverflowableNavigator
import org.readium.r2.navigator.pdf.PdfNavigatorFactory
import org.readium.r2.navigator.util.DirectionalNavigationAdapter
import org.readium.r2.shared.ExperimentalReadiumApi

@OptIn(ExperimentalReadiumApi::class)
class ReaderActivity : FragmentActivity() {

    private val model: ReaderViewModel by viewModels {
        ReaderViewModel.factory(
            application,
            intent.getLongExtra(EXTRA_BOOK, -1),
            intent.getStringExtra(EXTRA_LOCATOR),
            intent.getLongExtra(EXTRA_NOTE, -1).takeIf { it >= 0 },
        )
    }

    private lateinit var binding: ActivityReaderBinding
    private var loading by mutableStateOf(true)

    private val navigator: VisualNavigator?
        get() = supportFragmentManager.findFragmentByTag(NAVIGATOR_TAG) as? VisualNavigator

    override fun onCreate(savedInstanceState: Bundle?) {
        val session = model.session
        if (session != null) {
            supportFragmentManager.fragmentFactory = fragmentFactory(session)
            super.onCreate(savedInstanceState)
        } else {
            // Without an open publication the navigator can't be restored; start clean.
            super.onCreate(null)
        }
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
        )
        window.isNavigationBarContrastEnforced = false
        binding = ActivityReaderBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applyStableInsets()

        binding.overlay.setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        binding.overlay.setContent {
            val settings by model.settings.collectAsStateWithLifecycle()
            ReedTheme(theme = settings.theme) {
                ReaderOverlay(
                    model = model,
                    loading = loading,
                    onBack = { finish() },
                    onOpenNote = ::goTo,
                )
            }
        }

        if (session == null) {
            lifecycleScope.launch {
                val opened = model.load()
                if (opened == null) {
                    Toast.makeText(this@ReaderActivity, "Couldn't open this book.", Toast.LENGTH_LONG).show()
                    finish()
                    return@launch
                }
                supportFragmentManager.fragmentFactory = fragmentFactory(opened)
                attachNavigator(opened)
            }
        } else if (savedInstanceState == null) {
            attachNavigator(session)
        } else {
            onNavigatorReady()
        }

        observeState()
    }

    private fun fragmentFactory(session: ReaderSession): FragmentFactory = when (session.book.format) {
        BookFormat.EPUB -> EpubNavigatorFactory(session.publication).createFragmentFactory(
            initialLocator = session.initialLocator,
            initialPreferences = model.settings.value.toEpubPreferences(resolvedTheme()),
            configuration = EpubNavigatorFragment.Configuration {
                decorationTemplates = pencilTemplates()
                readiumCssRsProperties = pencilSelection()
                selectionActionModeCallback = selectionCallback
                shouldApplyInsetsPadding = false
                declareReedFonts()
            },
        )
        BookFormat.PDF -> PdfNavigatorFactory(session.publication, PdfiumEngineProvider())
            .createFragmentFactory(initialLocator = session.initialLocator)
    }

    private fun attachNavigator(session: ReaderSession) {
        val fragmentClass = when (session.book.format) {
            BookFormat.EPUB -> EpubNavigatorFragment::class.java
            BookFormat.PDF -> org.readium.r2.navigator.pdf.PdfNavigatorFragment::class.java
        }
        supportFragmentManager.commitNow {
            replace(binding.navigatorContainer.id, fragmentClass, Bundle(), NAVIGATOR_TAG)
        }
        onNavigatorReady()
    }

    private fun onNavigatorReady() {
        loading = false
        val navigator = navigator ?: return
        (navigator as? OverflowableNavigator)?.let {
            navigator.addInputListener(DirectionalNavigationAdapter(it, animatedTransition = true))
        }
        navigator.addInputListener(object : InputListener {
            override fun onTap(event: TapEvent): Boolean {
                model.toggleChrome()
                return true
            }
        })
        (navigator as? DecorableNavigator)?.addDecorationListener(
            NOTES_GROUP,
            object : DecorableNavigator.Listener {
                override fun onDecorationActivated(event: DecorableNavigator.OnActivatedEvent): Boolean {
                    noteIdOf(event.decoration.id)?.let(model::editById)
                    return true
                }
            },
        )
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { navigator.currentLocator.collect(model::onLocator) }
                if (navigator is EpubNavigatorFragment) {
                    launch {
                        combine(model.notes, model.flashNoteId, model.settings) { notes, flash, settings ->
                            pencilDecorations(notes, flash, settings.theme.resolveNow().isDark)
                        }.collect { navigator.applyDecorations(it, NOTES_GROUP) }
                    }
                    launch {
                        model.settings.collect { navigator.submitPreferences(it.toEpubPreferences(it.theme.resolveNow())) }
                    }
                }
            }
        }
    }

    private fun observeState() {
        val lock = reed.privacyLock
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.CREATED) {
                launch {
                    combine(model.book, lock.unlocked) { book, unlocked -> book?.isPrivate to unlocked }
                        .distinctUntilChanged()
                        .collect { (isPrivate, unlocked) ->
                            if (isPrivate == true) {
                                window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
                                if (!unlocked) finish()
                            } else {
                                window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                            }
                        }
                }
                launch {
                    model.chromeVisible.collect { visible -> showSystemBars(visible) }
                }
                launch {
                    model.settings.collect { settings ->
                        val theme = settings.theme.resolveNow()
                        val paper = when (theme) {
                            ReadingTheme.NIGHT -> Palette.NightPaper
                            ReadingTheme.BLACK -> Palette.BlackPaper
                            else -> Palette.Paper
                        }
                        binding.root.setBackgroundColor(paper.toArgb())
                        WindowCompat.getInsetsController(window, window.decorView).apply {
                            isAppearanceLightStatusBars = !theme.isDark
                            isAppearanceLightNavigationBars = !theme.isDark
                        }
                    }
                }
            }
        }
    }

    private fun goTo(note: Note) {
        val locator = note.locator.toLocator() ?: return
        navigator?.go(locator, animated = false)
        model.chromeVisible.value = false
        model.flash(note.id, delayMs = 250)
    }

    /** Text stays put when bars come and go: pad for the bars' full size, visible or not. */
    private fun applyStableInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.navigatorContainer) { view, insets ->
            val stable = insets.getInsetsIgnoringVisibility(WindowInsetsCompat.Type.systemBars())
            val cutout = insets.getInsets(WindowInsetsCompat.Type.displayCutout())
            view.updatePadding(
                top = maxOf(stable.top, cutout.top),
                bottom = stable.bottom,
                left = maxOf(stable.left, cutout.left),
                right = maxOf(stable.right, cutout.right),
            )
            insets
        }
    }

    private fun showSystemBars(visible: Boolean) {
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        if (visible) {
            controller.show(WindowInsetsCompat.Type.systemBars())
        } else {
            controller.hide(WindowInsetsCompat.Type.systemBars())
        }
    }

    private fun resolvedTheme(): ReadingTheme = model.settings.value.theme.resolveNow()

    private fun ReadingTheme.resolveNow(): ReadingTheme = when (this) {
        ReadingTheme.AUTO -> {
            val night = resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK
            if (night == android.content.res.Configuration.UI_MODE_NIGHT_YES) ReadingTheme.NIGHT else ReadingTheme.PAPER
        }
        else -> this
    }

    private val selectionCallback = object : ActionMode.Callback {
        override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
            menu.add(Menu.NONE, MENU_NOTE, 0, "Note").setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
            menu.add(Menu.NONE, MENU_COPY, 1, android.R.string.copy).setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
            return true
        }

        override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean = false

        override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
            val selectable = navigator as? SelectableNavigator ?: return false
            when (item.itemId) {
                MENU_NOTE -> lifecycleScope.launch {
                    val selection = selectable.currentSelection() ?: return@launch
                    selectable.clearSelection()
                    model.startPassageNote(selection.locator)
                }
                MENU_COPY -> lifecycleScope.launch {
                    val text = selectable.currentSelection()?.locator?.text?.highlight ?: return@launch
                    getSystemService(ClipboardManager::class.java)
                        .setPrimaryClip(ClipData.newPlainText("Passage", text))
                    selectable.clearSelection()
                }
                else -> return false
            }
            mode.finish()
            return true
        }

        override fun onDestroyActionMode(mode: ActionMode) = Unit
    }

    companion object {
        private const val EXTRA_BOOK = "book"
        private const val EXTRA_LOCATOR = "locator"
        private const val EXTRA_NOTE = "note"
        private const val NAVIGATOR_TAG = "navigator"
        private const val MENU_NOTE = 0x5eed
        private const val MENU_COPY = 0x5eee

        fun intent(context: Context, bookId: Long, locator: String? = null, noteId: Long? = null): Intent =
            Intent(context, ReaderActivity::class.java)
                .putExtra(EXTRA_BOOK, bookId)
                .putExtra(EXTRA_LOCATOR, locator)
                .putExtra(EXTRA_NOTE, noteId ?: -1L)
    }
}

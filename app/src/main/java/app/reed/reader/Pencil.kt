package app.reed.reader

import android.graphics.Color as AndroidColor
import androidx.annotation.ColorInt
import androidx.compose.ui.graphics.toArgb
import app.reed.data.Note
import app.reed.data.NoteKind
import app.reed.data.ReadingSettings
import app.reed.data.ReadingTheme
import app.reed.data.Typeface
import app.reed.data.toLocator
import app.reed.ui.theme.Palette
import kotlinx.parcelize.Parcelize
import org.readium.r2.navigator.Decoration
import org.readium.r2.navigator.epub.EpubNavigatorFragment
import org.readium.r2.navigator.epub.EpubPreferences
import org.readium.r2.navigator.html.HtmlDecorationTemplate
import org.readium.r2.navigator.html.HtmlDecorationTemplates
import org.readium.r2.navigator.preferences.Color
import org.readium.r2.navigator.preferences.FontFamily
import org.readium.r2.navigator.preferences.Theme
import org.readium.r2.shared.ExperimentalReadiumApi
import org.readium.r2.shared.publication.Locator
import org.readium.r2.navigator.epub.css.FontStyle
import org.readium.r2.navigator.epub.css.RsProperties
import org.readium.r2.navigator.epub.css.Color as CssColor

/** Graphite underline beneath a noted passage. Doubled while the note is open or just filed. */
@Parcelize
data class PencilLine(@param:ColorInt val tint: Int, val doubled: Boolean) : Decoration.Style

/** Graphite stroke in the page margin beside a noted passage. Tapping it opens the note. */
@Parcelize
data class PencilMark(@param:ColorInt val tint: Int) : Decoration.Style

/** Dotted graphite underline beneath the sentence being read aloud: the voice's trace, not a note. */
@Parcelize
data class SpokenLine(@param:ColorInt val tint: Int) : Decoration.Style

const val NOTES_GROUP = "reed-notes"
const val SPOKEN_GROUP = "reed-spoken"

fun decorationId(noteId: Long, part: String) = "n$noteId-$part"

fun noteIdOf(decorationId: String): Long? =
    decorationId.removePrefix("n").substringBefore('-').toLongOrNull()

private fun cssColor(@ColorInt color: Int): String =
    "rgba(${AndroidColor.red(color)}, ${AndroidColor.green(color)}, ${AndroidColor.blue(color)}, ${AndroidColor.alpha(color) / 255.0})"

fun pencilTemplates(): HtmlDecorationTemplates = HtmlDecorationTemplates.defaultTemplates().apply {
    set(
        PencilLine::class,
        HtmlDecorationTemplate(
            layout = HtmlDecorationTemplate.Layout.BOXES,
            width = HtmlDecorationTemplate.Width.WRAP,
            element = { decoration ->
                val style = decoration.style as PencilLine
                val modifier = if (style.doubled) " reed-line--doubled" else ""
                """<div class="reed-line$modifier" style="--reed-tint: ${cssColor(style.tint)}"></div>"""
            },
            // Drawn as gradients: Readium's night mode forces every border to the text colour.
            stylesheet = """
                .reed-line {
                    box-sizing: border-box;
                    background: linear-gradient(var(--reed-tint), var(--reed-tint)) left bottom / 100% 1.5px no-repeat;
                }
                .reed-line--doubled {
                    background:
                        linear-gradient(var(--reed-tint), var(--reed-tint)) left bottom / 100% 1.5px no-repeat,
                        linear-gradient(var(--reed-tint), var(--reed-tint)) left calc(100% - 4px) / 100% 1.5px no-repeat;
                }
            """.trimIndent(),
        ),
    )
    set(
        SpokenLine::class,
        HtmlDecorationTemplate(
            layout = HtmlDecorationTemplate.Layout.BOXES,
            width = HtmlDecorationTemplate.Width.WRAP,
            element = { decoration ->
                val style = decoration.style as SpokenLine
                """<div class="reed-spoken" style="--reed-tint: ${cssColor(style.tint)}"></div>"""
            },
            stylesheet = """
                .reed-spoken {
                    box-sizing: border-box;
                    background: radial-gradient(circle, var(--reed-tint) 0.9px, transparent 1.2px) left bottom / 5px 2.5px repeat-x;
                }
            """.trimIndent(),
        ),
    )
    set(
        PencilMark::class,
        HtmlDecorationTemplate(
            layout = HtmlDecorationTemplate.Layout.BOUNDS,
            width = HtmlDecorationTemplate.Width.VIEWPORT,
            element = { decoration ->
                val style = decoration.style as PencilMark
                """<div class="reed-mark-row"><div class="reed-mark" data-activable="1" style="--reed-tint: ${cssColor(style.tint)}"></div></div>"""
            },
            stylesheet = """
                .reed-mark {
                    position: absolute;
                    left: 0;
                    top: -4px;
                    bottom: -4px;
                    width: 22px;
                }
                .reed-mark::after {
                    content: "";
                    position: absolute;
                    left: 10px;
                    top: 6px;
                    bottom: 6px;
                    width: 2px;
                    border-radius: 1px;
                    background: var(--reed-tint);
                }
            """.trimIndent(),
        ),
    )
}

fun pencilDecorations(notes: List<Note>, flashId: Long?, dark: Boolean): List<Decoration> {
    val line = (if (dark) Palette.NightGraphiteLight else Palette.GraphiteLight).toArgb()
    val mark = (if (dark) Palette.NightGraphite else Palette.GraphiteLight).toArgb()
    return notes
        .filter { it.kind == NoteKind.PASSAGE }
        .mapNotNull { note ->
            val locator = note.locator.toLocator() ?: return@mapNotNull null
            listOf(
                Decoration(decorationId(note.id, "line"), locator, PencilLine(line, doubled = note.id == flashId)),
                Decoration(decorationId(note.id, "mark"), locator, PencilMark(mark)),
            )
        }
        .flatten()
}

fun spokenDecorations(sentence: Locator?, dark: Boolean): List<Decoration> {
    sentence ?: return emptyList()
    val tint = (if (dark) Palette.NightGraphite else Palette.Graphite).toArgb()
    return listOf(Decoration("spoken", sentence, SpokenLine(tint)))
}

@OptIn(ExperimentalReadiumApi::class)
fun EpubNavigatorFragment.Configuration.declareReedFonts() {
    servedAssets = servedAssets + "fonts/.*"
    Typeface.entries.filter { it.asset != null }.forEach { face ->
        val roman = face.asset!!
        val italic = roman.replace(".ttf", "-Italic.ttf")
        val weights = if (face == Typeface.ATKINSON) 200..800 else 200..900
        addFontFamilyDeclaration(FontFamily(face.label)) {
            addFontFace {
                addSource(roman)
                setFontStyle(FontStyle.NORMAL)
                setFontWeight(weights)
            }
            addFontFace {
                addSource(italic)
                setFontStyle(FontStyle.ITALIC)
                setFontWeight(weights)
            }
        }
    }
}

/** Text selection in the book takes the graphite of the margin, not the WebView's blue. */
fun pencilSelection(): RsProperties = RsProperties(
    selectionBackgroundColor = CssColor.Int(0x598E9298),
)

fun ReadingSettings.toEpubPreferences(resolved: ReadingTheme): EpubPreferences {
    val (background, text) = when (resolved) {
        ReadingTheme.NIGHT -> Palette.NightPaper to Palette.NightInk
        ReadingTheme.BLACK -> Palette.BlackPaper to Palette.BlackInk
        else -> Palette.Paper to Palette.Ink
    }
    return EpubPreferences(
        theme = if (resolved == ReadingTheme.NIGHT || resolved == ReadingTheme.BLACK) Theme.DARK else Theme.LIGHT,
        backgroundColor = Color(background.toArgb()),
        textColor = Color(text.toArgb()),
        fontFamily = typeface.asset?.let { FontFamily(typeface.label) },
        fontSize = fontScale,
        lineHeight = lineSpacing.lineHeight,
        pageMargins = margins.pageMargins,
        publisherStyles = false,
        // Keep the book's own alignment (centred headings, set-right datelines); hyphenate to close justified gaps.
        hyphens = true,
        scroll = false,
    )
}

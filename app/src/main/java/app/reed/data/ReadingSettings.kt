package app.reed.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

enum class ReadingTheme { AUTO, PAPER, NIGHT, BLACK }

enum class Typeface(val label: String, val asset: String?) {
    ORIGINAL("Original", null),
    LITERATA("Literata", "fonts/Literata.ttf"),
    SOURCE_SERIF("Source Serif", "fonts/SourceSerif4.ttf"),
    ATKINSON("Atkinson", "fonts/AtkinsonHyperlegibleNext.ttf"),
}

enum class LineSpacing(val label: String, val lineHeight: Double) {
    COMPACT("Compact", 1.3),
    NORMAL("Normal", 1.5),
    RELAXED("Relaxed", 1.75),
}

enum class Margins(val label: String, val pageMargins: Double) {
    NORMAL("Normal", 1.2),
    WIDE("Wide", 1.8),
}

enum class LibrarySort(val label: String) {
    RECENT("Recent"),
    TITLE("Title"),
    AUTHOR("Author"),
}

data class ReadingSettings(
    val theme: ReadingTheme = ReadingTheme.AUTO,
    val typeface: Typeface = Typeface.LITERATA,
    val fontScale: Double = 1.0,
    val lineSpacing: LineSpacing = LineSpacing.NORMAL,
    val margins: Margins = Margins.NORMAL,
) {
    companion object {
        const val MIN_SCALE = 0.8
        const val MAX_SCALE = 2.0
        const val SCALE_STEP = 0.1
    }
}

private val Context.store by preferencesDataStore("settings")

class SettingsStore(private val context: Context) {
    private object Keys {
        val theme = stringPreferencesKey("theme")
        val typeface = stringPreferencesKey("typeface")
        val fontScale = doublePreferencesKey("fontScale")
        val lineSpacing = stringPreferencesKey("lineSpacing")
        val margins = stringPreferencesKey("margins")
        val sort = stringPreferencesKey("librarySort")
        val listenSpeed = doublePreferencesKey("listenSpeed")
    }

    val reading: Flow<ReadingSettings> = context.store.data.map { it.toReading() }

    val sort: Flow<LibrarySort> = context.store.data.map { prefs ->
        prefs[Keys.sort].toEnum(LibrarySort.RECENT)
    }

    /** Read-aloud speed, 1.0 being the voice's normal pace. */
    val listenSpeed: Flow<Double> = context.store.data.map { it[Keys.listenSpeed] ?: 1.0 }

    suspend fun setListenSpeed(speed: Double) {
        context.store.edit { it[Keys.listenSpeed] = speed }
    }

    suspend fun updateReading(transform: (ReadingSettings) -> ReadingSettings) {
        context.store.edit { prefs ->
            val next = transform(prefs.toReading())
            prefs[Keys.theme] = next.theme.name
            prefs[Keys.typeface] = next.typeface.name
            prefs[Keys.fontScale] = next.fontScale
            prefs[Keys.lineSpacing] = next.lineSpacing.name
            prefs[Keys.margins] = next.margins.name
        }
    }

    suspend fun setSort(sort: LibrarySort) {
        context.store.edit { it[Keys.sort] = sort.name }
    }

    private fun Preferences.toReading() = ReadingSettings(
        theme = this[Keys.theme].toEnum(ReadingTheme.AUTO),
        typeface = this[Keys.typeface].toEnum(Typeface.LITERATA),
        fontScale = this[Keys.fontScale] ?: 1.0,
        lineSpacing = this[Keys.lineSpacing].toEnum(LineSpacing.NORMAL),
        margins = this[Keys.margins].toEnum(Margins.NORMAL),
    )
}

private inline fun <reified E : Enum<E>> String?.toEnum(default: E): E =
    this?.let { name -> enumValues<E>().firstOrNull { it.name == name } } ?: default

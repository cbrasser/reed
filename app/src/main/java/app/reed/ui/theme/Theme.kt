package app.reed.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import app.reed.data.ReadingTheme

/**
 * Pencil Marginalia: the book's words are ink, the reader's words are graphite.
 * Pencil yellow belongs to dictation and nothing else.
 */
object Palette {
    val Paper = Color(0xFFFAFAF8)
    val Ink = Color(0xFF1A1A1A)
    val Graphite = Color(0xFF5E6268)
    val GraphiteLight = Color(0xFF8E9298)
    val Rule = Color(0xFFD9DBDE)
    val PencilYellow = Color(0xFFF2C230)
    val PencilYellowSoft = Color(0xFFFBEDB8)

    val NightPaper = Color(0xFF141517)
    val NightInk = Color(0xFFE8E6E1)
    val NightGraphite = Color(0xFFA4A8AE)
    val NightGraphiteLight = Color(0xFF6F7379)
    val NightRule = Color(0xFF34363A)

    val BlackPaper = Color(0xFF000000)
    val BlackInk = Color(0xFFC9C7C2)
}

/** Colours the Material scheme has no role for. */
@Immutable
data class MarginColors(
    /** Text the reader wrote. */
    val pencil: Color,
    /** The margin tick and other graphite marks. */
    val tick: Color,
    /** Hairline rules and dividers. */
    val rule: Color,
    /** The dictation accent. */
    val listening: Color,
    val onListening: Color,
    /** The mic at rest: still pencil yellow, quieter than while listening. */
    val micRest: Color,
    val onMicRest: Color,
    val micRestBorder: Color,
)

val LocalMarginColors = staticCompositionLocalOf {
    MarginColors(
        pencil = Palette.Graphite,
        tick = Palette.GraphiteLight,
        rule = Palette.Rule,
        listening = Palette.PencilYellow,
        onListening = Palette.Ink,
        micRest = Palette.PencilYellowSoft,
        onMicRest = Palette.Ink,
        micRestBorder = Color.Transparent,
    )
}

private val LightScheme = lightColorScheme(
    primary = Palette.Ink,
    onPrimary = Palette.Paper,
    primaryContainer = Color(0xFFE6E7E8),
    onPrimaryContainer = Palette.Ink,
    secondary = Palette.Graphite,
    onSecondary = Palette.Paper,
    secondaryContainer = Color(0xFFE6E7E8),
    onSecondaryContainer = Color(0xFF2B2E33),
    tertiary = Palette.PencilYellow,
    onTertiary = Palette.Ink,
    tertiaryContainer = Palette.PencilYellowSoft,
    onTertiaryContainer = Color(0xFF3A2E00),
    background = Palette.Paper,
    onBackground = Palette.Ink,
    surface = Palette.Paper,
    onSurface = Palette.Ink,
    surfaceVariant = Color(0xFFE6E7E8),
    onSurfaceVariant = Palette.Graphite,
    surfaceTint = Palette.Ink,
    inverseSurface = Color(0xFF2E3033),
    inverseOnSurface = Color(0xFFF1F0EC),
    inversePrimary = Palette.NightInk,
    outline = Palette.GraphiteLight,
    outlineVariant = Palette.Rule,
    error = Color(0xFFB3261E),
    onError = Color.White,
    errorContainer = Color(0xFFF9DEDC),
    onErrorContainer = Color(0xFF410E0B),
    scrim = Color.Black,
    surfaceBright = Palette.Paper,
    surfaceDim = Color(0xFFDCDCDA),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF4F4F2),
    surfaceContainer = Color(0xFFEFEFED),
    surfaceContainerHigh = Color(0xFFE9E9E7),
    surfaceContainerHighest = Color(0xFFE3E3E1),
)

private fun darkScheme(paper: Color, ink: Color, lift: Float) = darkColorScheme(
    primary = ink,
    onPrimary = paper,
    primaryContainer = Color(0xFF2D2E32),
    onPrimaryContainer = ink,
    secondary = Palette.NightGraphite,
    onSecondary = paper,
    secondaryContainer = Color(0xFF2D2E32),
    onSecondaryContainer = ink,
    tertiary = Palette.PencilYellow,
    onTertiary = Palette.Ink,
    tertiaryContainer = Color(0xFF4A3C0A),
    onTertiaryContainer = Palette.PencilYellowSoft,
    background = paper,
    onBackground = ink,
    surface = paper,
    onSurface = ink,
    surfaceVariant = Color(0xFF2A2B2F),
    onSurfaceVariant = Palette.NightGraphite,
    surfaceTint = ink,
    inverseSurface = Palette.NightInk,
    inverseOnSurface = Palette.NightPaper,
    inversePrimary = Palette.Ink,
    outline = Palette.NightGraphiteLight,
    outlineVariant = Palette.NightRule,
    error = Color(0xFFF2B8B5),
    onError = Color(0xFF601410),
    errorContainer = Color(0xFF8C1D18),
    onErrorContainer = Color(0xFFF9DEDC),
    scrim = Color.Black,
    surfaceBright = Color(0xFF2E2F33),
    surfaceDim = paper,
    surfaceContainerLowest = paper,
    surfaceContainerLow = paper.lift(lift * 0.6f),
    surfaceContainer = paper.lift(lift * 1.0f),
    surfaceContainerHigh = paper.lift(lift * 1.5f),
    surfaceContainerHighest = paper.lift(lift * 2.0f),
)

private fun Color.lift(amount: Float) = Color(
    red = (red + amount).coerceAtMost(1f),
    green = (green + amount).coerceAtMost(1f),
    blue = (blue + amount).coerceAtMost(1f),
    alpha = 1f,
)

private val NightScheme = darkScheme(Palette.NightPaper, Palette.NightInk, lift = 0.035f)
private val BlackScheme = darkScheme(Palette.BlackPaper, Palette.BlackInk, lift = 0.045f)

private val LightMargin = MarginColors(
    pencil = Palette.Graphite,
    tick = Palette.GraphiteLight,
    rule = Palette.Rule,
    listening = Palette.PencilYellow,
    onListening = Palette.Ink,
    micRest = Palette.PencilYellowSoft,
    onMicRest = Palette.Ink,
    micRestBorder = Color.Transparent,
)

private val NightMargin = MarginColors(
    pencil = Palette.NightGraphite,
    tick = Palette.NightGraphiteLight,
    rule = Palette.NightRule,
    listening = Palette.PencilYellow,
    onListening = Palette.Ink,
    micRest = Palette.PencilYellow.copy(alpha = 0.14f),
    onMicRest = Palette.PencilYellow,
    micRestBorder = Palette.PencilYellow,
)

/** Resolves AUTO against the system setting. */
@Composable
fun ReadingTheme.resolve(): ReadingTheme = when (this) {
    ReadingTheme.AUTO -> if (isSystemInDarkTheme()) ReadingTheme.NIGHT else ReadingTheme.PAPER
    else -> this
}

val ReadingTheme.isDark: Boolean get() = this == ReadingTheme.NIGHT || this == ReadingTheme.BLACK

@Composable
fun ReedTheme(
    theme: ReadingTheme = ReadingTheme.AUTO,
    content: @Composable () -> Unit,
) {
    val resolved = theme.resolve()
    val scheme: ColorScheme = when (resolved) {
        ReadingTheme.NIGHT -> NightScheme
        ReadingTheme.BLACK -> BlackScheme
        else -> LightScheme
    }
    val margin = if (resolved.isDark) NightMargin else LightMargin
    CompositionLocalProvider(LocalMarginColors provides margin) {
        MaterialTheme(
            colorScheme = scheme,
            typography = ReedTypography,
            shapes = ReedShapes,
            content = content,
        )
    }
}

/** One selected-state grammar for every choice control: an ink line, never a fill. */
@Composable
fun selectedLine(selected: Boolean): androidx.compose.foundation.BorderStroke =
    if (selected) {
        androidx.compose.foundation.BorderStroke(1.5.dp, MaterialTheme.colorScheme.onSurface)
    } else {
        androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    }

@Composable
fun reedSegmentedColors() = androidx.compose.material3.SegmentedButtonDefaults.colors(
    activeContainerColor = Color.Transparent,
    activeContentColor = MaterialTheme.colorScheme.onSurface,
    activeBorderColor = MaterialTheme.colorScheme.onSurface,
    inactiveContainerColor = Color.Transparent,
    inactiveContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
    inactiveBorderColor = MaterialTheme.colorScheme.outlineVariant,
)

object Margin {
    val colors: MarginColors
        @Composable get() = LocalMarginColors.current
}

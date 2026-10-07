package app.reed.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import app.reed.R
import app.reed.data.Typeface

private fun schibsted(weight: Int) = Font(
    resId = R.font.schibsted_grotesk,
    weight = FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

/** The one UI face. Reader-written notes are set in it too, in graphite. */
val Schibsted = FontFamily(schibsted(400), schibsted(500), schibsted(600), schibsted(700))

private fun style(size: Int, line: Int, weight: Int, tracking: Double = 0.0) = TextStyle(
    fontFamily = Schibsted,
    fontSize = size.sp,
    lineHeight = line.sp,
    fontWeight = FontWeight(weight),
    letterSpacing = tracking.em,
)

val ReedTypography = Typography(
    displaySmall = style(34, 40, 600, -0.02),
    headlineLarge = style(30, 36, 600, -0.02),
    headlineMedium = style(26, 32, 600, -0.015),
    headlineSmall = style(22, 28, 600, -0.01),
    titleLarge = style(20, 26, 600, -0.01),
    titleMedium = style(16, 22, 600, -0.005),
    titleSmall = style(14, 20, 600),
    bodyLarge = style(16, 24, 400),
    bodyMedium = style(14, 20, 400),
    bodySmall = style(12, 16, 400, 0.005),
    labelLarge = style(14, 20, 500, 0.005),
    labelMedium = style(12, 16, 500, 0.01),
    labelSmall = style(11, 14, 500, 0.02),
)

val ReedShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

/** The book faces a reader can pick, available to Compose for quoting passages. */
@Composable
fun rememberBookFont(typeface: Typeface): FontFamily {
    val assets = LocalContext.current.assets
    return remember(typeface) {
        val path = (typeface.asset ?: Typeface.LITERATA.asset)!!
        FontFamily(
            listOf(400, 500, 600).map { weight ->
                Font(
                    path = path,
                    assetManager = assets,
                    weight = FontWeight(weight),
                    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
                )
            },
        )
    }
}

/** Passage text as quoted outside the reader. */
fun passageStyle(family: FontFamily) = TextStyle(
    fontFamily = family,
    fontSize = 16.sp,
    lineHeight = 24.sp,
    fontWeight = FontWeight.Normal,
)

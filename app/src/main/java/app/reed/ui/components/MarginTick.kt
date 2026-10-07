package app.reed.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.reed.ui.theme.Margin

/**
 * The signature mark: a graphite stroke in the margin beside the words it belongs to.
 * State lives in the line's form, never in its hue.
 */
enum class TickForm {
    /** A noted passage. */
    SOLID,

    /** A note on a whole page. */
    DASHED,

    /** The note that is open, or was just filed. */
    DOUBLED,
}

@Composable
fun MarginTick(
    modifier: Modifier = Modifier,
    form: TickForm = TickForm.SOLID,
    color: Color = Margin.colors.tick,
    stroke: Dp = 2.dp,
) {
    val width = if (form == TickForm.DOUBLED) stroke * 3 else stroke
    Canvas(modifier.width(width).fillMaxHeight()) {
        val w = stroke.toPx()
        val effect = if (form == TickForm.DASHED) {
            PathEffect.dashPathEffect(floatArrayOf(w * 2.5f, w * 2f))
        } else {
            null
        }
        val inset = w / 2
        fun line(x: Float) = drawLine(
            color = color,
            start = Offset(x, inset),
            end = Offset(x, size.height - inset),
            strokeWidth = w,
            cap = StrokeCap.Round,
            pathEffect = effect,
        )
        line(w / 2)
        if (form == TickForm.DOUBLED) line(size.width - w / 2)
    }
}

/** Tick plus a count, the way notes are counted everywhere in Reed. */
@Composable
fun NoteCount(
    count: Int,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.labelMedium,
    color: Color = LocalContentColor.current,
    withWord: Boolean = true,
) {
    val label = if (count == 1) "1 note" else "$count notes"
    Row(
        modifier = modifier.clearAndSetSemantics { contentDescription = label },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MarginTick(Modifier.height(12.dp))
        Spacer(Modifier.width(6.dp))
        Text(if (withWord) label else count.toString(), style = style, color = color)
    }
}

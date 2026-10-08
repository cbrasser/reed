package app.reed.reader

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.reed.data.LineSpacing
import app.reed.data.Margins
import app.reed.data.PageLayout
import app.reed.data.ReadingSettings
import app.reed.data.ReadingTheme
import app.reed.data.Typeface
import app.reed.ui.theme.Palette
import app.reed.ui.theme.Schibsted
import app.reed.ui.theme.rememberBookFont
import app.reed.ui.theme.reedSegmentedColors
import app.reed.ui.theme.selectedLine
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsSheet(
    settings: ReadingSettings,
    textControls: Boolean,
    onChange: ((ReadingSettings) -> ReadingSettings) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        scrimColor = Color.Black.copy(alpha = 0.12f),
    ) {
        Column(
            Modifier.navigationBarsPadding().padding(start = 24.dp, end = 24.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Setting("Theme") {
                Row(Modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ReadingTheme.entries.forEach { theme ->
                        ThemeSwatch(
                            theme = theme,
                            selected = settings.theme == theme,
                            onClick = { onChange { it.copy(theme = theme) } },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
            if (!textControls) {
                Text(
                    "Typeface, size and spacing apply to EPUB books. PDF pages keep their own layout.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                return@Column
            }
            Setting("Typeface") {
                Row(Modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Typeface.entries.forEach { face ->
                        TypefaceOption(
                            face = face,
                            selected = settings.typeface == face,
                            onClick = { onChange { it.copy(typeface = face) } },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
            Setting("Size") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    FilledTonalIconButton(
                        onClick = { onChange { it.copy(fontScale = (it.fontScale - ReadingSettings.SCALE_STEP).coerceAtLeast(ReadingSettings.MIN_SCALE)) } },
                        enabled = settings.fontScale > ReadingSettings.MIN_SCALE + 0.001,
                    ) { Icon(Icons.Outlined.Remove, contentDescription = "Smaller text") }
                    Text(
                        "${(settings.fontScale * 100).roundToInt()}%",
                        style = MaterialTheme.typography.titleMedium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f),
                    )
                    FilledTonalIconButton(
                        onClick = { onChange { it.copy(fontScale = (it.fontScale + ReadingSettings.SCALE_STEP).coerceAtMost(ReadingSettings.MAX_SCALE)) } },
                        enabled = settings.fontScale < ReadingSettings.MAX_SCALE - 0.001,
                    ) { Icon(Icons.Outlined.Add, contentDescription = "Larger text") }
                }
            }
            Setting("Line spacing") {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    LineSpacing.entries.forEachIndexed { i, spacing ->
                        SegmentedButton(
                            selected = settings.lineSpacing == spacing,
                            onClick = { onChange { it.copy(lineSpacing = spacing) } },
                            shape = SegmentedButtonDefaults.itemShape(i, LineSpacing.entries.size),
                            colors = reedSegmentedColors(),
                            icon = {},
                            label = { Text(spacing.label) },
                        )
                    }
                }
            }
            Setting("Margins") {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    Margins.entries.forEachIndexed { i, margin ->
                        SegmentedButton(
                            selected = settings.margins == margin,
                            onClick = { onChange { it.copy(margins = margin) } },
                            shape = SegmentedButtonDefaults.itemShape(i, Margins.entries.size),
                            colors = reedSegmentedColors(),
                            icon = {},
                            label = { Text(margin.label) },
                        )
                    }
                }
            }
            Setting("Layout") {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    PageLayout.entries.forEachIndexed { i, layout ->
                        SegmentedButton(
                            selected = settings.layout == layout,
                            onClick = { onChange { it.copy(layout = layout) } },
                            shape = SegmentedButtonDefaults.itemShape(i, PageLayout.entries.size),
                            colors = reedSegmentedColors(),
                            icon = {},
                            label = { Text(layout.label) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Setting(label: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        content()
    }
}

@Composable
private fun ThemeSwatch(theme: ReadingTheme, selected: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val (paper, ink) = when (theme) {
        ReadingTheme.AUTO -> Palette.Paper to Palette.Ink
        ReadingTheme.PAPER -> Palette.Paper to Palette.Ink
        ReadingTheme.NIGHT -> Palette.NightPaper to Palette.NightInk
        ReadingTheme.BLACK -> Palette.BlackPaper to Palette.BlackInk
    }
    val label = theme.name.lowercase().replaceFirstChar { it.uppercase() }
    Column(
        modifier.selectable(selected = selected, onClick = onClick, role = Role.RadioButton),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(52.dp)
                .border(
                    if (selected) BorderStroke(1.5.dp, MaterialTheme.colorScheme.onSurface) else BorderStroke(0.dp, Color.Transparent),
                    CircleShape,
                )
                .padding(4.dp),
        ) {
            Surface(
                shape = CircleShape,
                color = paper,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.size(44.dp),
            ) {
                if (theme == ReadingTheme.AUTO) {
                    Row {
                        Box(Modifier.weight(1f).height(44.dp))
                        Surface(color = Palette.NightPaper, modifier = Modifier.weight(1f).height(44.dp)) {}
                    }
                } else {
                    Box(contentAlignment = Alignment.Center) {
                        Text("Aa", color = ink, fontFamily = Schibsted, fontSize = 15.sp)
                    }
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(label, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun TypefaceOption(face: Typeface, selected: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val family = if (face == Typeface.ORIGINAL) null else rememberBookFont(face)
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = Color.Transparent,
        border = selectedLine(selected),
        modifier = modifier.selectable(selected = selected, onClick = onClick, role = Role.RadioButton),
    ) {
        Column(
            Modifier.padding(vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                "Ag",
                fontFamily = family,
                fontSize = 22.sp,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.width(4.dp).height(2.dp))
            Text(
                face.label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }
}

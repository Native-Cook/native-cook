package com.ngocdevv.nativecook.blocks.cards.aurawalletwidget

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.ngocdevv.nativecook.R
import kotlin.math.abs
import kotlin.math.max

// Both labels measure once. Only the empty chip changes width, keeping the
// letters centered; Android follows the source's unblurred label crossfade.
@Composable
internal fun AuraChip(machine: AuraMachine, unit: Double, density: Float) {
    val before = stringResource(R.string.aurawalletwidget_balance)
    val after = stringResource(R.string.aurawalletwidget_after)
    val style = auraTextStyle(10.0, 14.0, unit, FontWeight.Medium, tracking = 0.2)
    val measurer = rememberTextMeasurer()
    val beforeWidth = remember(before, style, density) {
        max(90 * unit * density, measurer.measure(AnnotatedString(before), style).size.width + 20 * unit * density)
    }
    val afterWidth = remember(after, style, density) {
        max(90 * unit * density, measurer.measure(AnnotatedString(after), style).size.width + 20 * unit * density)
    }
    val stroke = remember(unit, density) { Stroke((unit * density).toFloat()) }
    val fill = Color.White.copy(alpha = 0.16f)
    val line = Color.White.copy(alpha = 0.18f)
    Box(Modifier.offset((12 * unit).dp, (47 * unit).dp).size((338 * unit).dp, (22 * unit).dp)
        .clearAndSetSemantics {}, contentAlignment = Alignment.Center) {
        Canvas(Modifier.matchParentSize()) {
            val progress = machine.drive.chip.value
            val width = (if (machine.reduced) if (progress < 0.5) beforeWidth else afterWidth
            else beforeWidth + (afterWidth - beforeWidth) * progress).toFloat()
            val alpha = if (machine.reduced) abs(2 * progress - 1).toFloat() else 1f
            val left = (size.width - width) / 2
            drawRoundRect(fill, Offset(left, 0f), Size(width, size.height), CornerRadius(size.height / 2), alpha = alpha)
            val inset = stroke.width / 2
            drawRoundRect(line, Offset(left + inset, inset), Size(width - stroke.width, size.height - stroke.width),
                CornerRadius(size.height / 2 - inset), alpha = alpha, style = stroke)
        }
        // The visual chip's source handler is intentionally empty.
        Box(Modifier.size((max(beforeWidth, afterWidth) / density).dp, (22 * unit).dp)
            .clickable(remember { MutableInteractionSource() }, indication = null) {})
        AuraLine(before, style, Modifier.graphicsLayer { alpha = (1 - machine.drive.chip.value).toFloat() })
        AuraLine(after, style, Modifier.graphicsLayer { alpha = machine.drive.chip.value.toFloat() })
    }
}

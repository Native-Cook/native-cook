package com.ngocdevv.nativecook.blocks.cards.referralrewardcard

import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import com.ngocdevv.nativecook.blocks.cards.referralrewardcard.ReferralPalette.Size
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import androidx.compose.ui.geometry.Size as CanvasSize

// Measure both localized labels, including wrapping, before fixing the button's size.
// Even dp dimensions keep the center stable while its body collapses into a dot.
@Composable
internal fun referralButtonSize(
    width: Float,
    density: Float,
    first: String,
    second: String,
    labelStyle: TextStyle,
): CanvasSize {
    val measurer = rememberTextMeasurer()
    val maxButton = width - Size.cardPad * 2
    val firstMeasure =
        measurer.measure(
            first,
            labelStyle,
            constraints = Constraints(maxWidth = (maxButton * density).roundToInt()),
        )
    val secondMeasure =
        measurer.measure(
            second,
            labelStyle,
            constraints = Constraints(maxWidth = (maxButton * density).roundToInt()),
        )
    val buttonWidth =
        min(
            maxButton,
            2 * ceil((max(firstMeasure.size.width, secondMeasure.size.width) / density + 48) / 2),
        )
    val firstHeight =
        measurer
            .measure(
                first,
                labelStyle,
                constraints = Constraints(maxWidth = ((buttonWidth - 48) * density).roundToInt()),
            ).size
            .height / density
    val secondHeight =
        measurer
            .measure(
                second,
                labelStyle,
                constraints = Constraints(maxWidth = ((buttonWidth - 48) * density).roundToInt()),
            ).size
            .height / density
    val buttonHeight = max(48f, 2 * ceil((max(firstHeight, secondHeight) + 24) / 2))
    return CanvasSize(buttonWidth, buttonHeight)
}

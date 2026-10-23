package com.ngocdevv.nativecook.blocks.charts.stepsbarstospikes

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

// Milliseconds for a digit to roll to its new value, on CSS's ease-out.
private const val ROLL_MS = 260
private val ROLL_EASING = CubicBezierEasing(0f, 0f, 0.58f, 1f)

/** Centres each line in its line height, as React Native lays out a line. */
internal fun TextStyle.centeredLines() = copy(
    lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None),
)

/**
 * A number whose digits roll like an odometer when it changes; other characters are set.
 * [lineHeight] in dp is the height of one digit's window.
 */
@Composable
internal fun StepsBarsToSpikesRoller(text: String, style: TextStyle, lineHeight: Float, still: Boolean, modifier: Modifier = Modifier) {
    val lineStyle = style.centeredLines()
    Row(modifier.semantics { contentDescription = text }) {
        text.forEachIndexed { i, character ->
            // Counted from the right, so units stay units when the number gets longer.
            key(text.length - i) {
                val digit = character.digitToIntOrNull()
                if (digit == null) {
                    BasicText(character.toString(), Modifier.height(lineHeight.dp).wrapContentHeight(), style = lineStyle)
                } else {
                    RollerDigit(digit, lineStyle, lineHeight, still)
                }
            }
        }
    }
}

/** A digit in a window one line tall: a column of 0–9 slides to show it. */
@Composable
private fun RollerDigit(digit: Int, style: TextStyle, lineHeight: Float, still: Boolean) {
    val shown by animateFloatAsState(
        digit.toFloat(),
        if (still) snap() else tween(ROLL_MS, easing = ROLL_EASING),
        label = "digit",
    )
    Box(Modifier.height(lineHeight.dp).clipToBounds()) {
        Column(
            Modifier
                .wrapContentHeight(Alignment.Top, unbounded = true)
                .offset { IntOffset(0, (-shown * lineHeight.dp.toPx()).roundToInt()) },
        ) {
            for (d in 0..9) BasicText(d.toString(), Modifier.height(lineHeight.dp).wrapContentHeight(), style = style)
        }
    }
}

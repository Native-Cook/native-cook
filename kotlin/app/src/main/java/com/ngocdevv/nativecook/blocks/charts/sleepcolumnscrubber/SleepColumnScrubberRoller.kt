package com.ngocdevv.nativecook.blocks.charts.sleepcolumnscrubber

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp

// Milliseconds for a digit to roll to its new value, on CSS's `ease-out`.
private const val ROLL = 260
private val EaseOut = CubicBezierEasing(0f, 0f, 0.58f, 1f)
private const val DIGITS = "0123456789"

/** A digit in a window one line tall: a column of 0–9 slides to show it. */
@Composable
private fun Digit(digit: Int, style: TextStyle, lineHeight: Dp, still: Boolean) {
    val shown by animateFloatAsState(digit.toFloat(), if (still) snap() else tween(ROLL, easing = EaseOut), label = "digit")
    Box(Modifier.height(lineHeight).clipToBounds()) {
        Column(
            Modifier
                .wrapContentHeight(Alignment.Top, unbounded = true)
                .graphicsLayer { translationY = -shown * lineHeight.toPx() },
        ) {
            for (d in DIGITS) BasicText(d.toString(), Modifier.height(lineHeight), style = style)
        }
    }
}

/**
 * A number whose digits roll like an odometer when it changes.
 *
 * @param text The number as shown, for example "00:42—08:23"; digits roll, other characters are set.
 * @param lineHeight The height of one digit's window.
 */
@Composable
internal fun SleepColumnScrubberRoller(text: String, style: TextStyle, lineHeight: Dp, still: Boolean) {
    Row(Modifier.clearAndSetSemantics { contentDescription = text }) {
        text.forEachIndexed { i, character ->
            // Counted from the right, so units stay units when the number gets longer.
            key(text.length - i) {
                val digit = DIGITS.indexOf(character)
                if (digit < 0) {
                    BasicText(character.toString(), Modifier.height(lineHeight), style = style)
                } else {
                    Digit(digit, style, lineHeight, still)
                }
            }
        }
    }
}

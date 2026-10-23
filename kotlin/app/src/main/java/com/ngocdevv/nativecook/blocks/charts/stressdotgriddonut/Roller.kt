package com.ngocdevv.nativecook.blocks.charts.stressdotgriddonut

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material3.Text
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

// Milliseconds for a digit to roll to its new value, on CSS's `ease-out` curve.
private const val ROLL = 260
private val RollEasing = CubicBezierEasing(0f, 0f, 0.58f, 1f)

/**
 * A number whose digits roll like an odometer when it changes.
 *
 * @param text The number as shown, for example "64.7"; digits roll, other characters are set.
 * @param lineHeight The height of one digit's window.
 */
@Composable
internal fun Roller(text: String, style: TextStyle, lineHeight: Dp, still: Boolean) {
    Row(Modifier.clearAndSetSemantics { contentDescription = text }) {
        text.forEachIndexed { i, character ->
            // Counted from the right, so units stay units when the number gets longer.
            key(text.length - i) {
                if (character.isDigit()) {
                    Digit(character.digitToInt(), style, lineHeight, still)
                } else {
                    Text(character.toString(), style = style, modifier = Modifier.height(lineHeight))
                }
            }
        }
    }
}

/** A digit in a window one line tall: a column of 0–9 slides to show it. */
@Composable
private fun Digit(digit: Int, style: TextStyle, lineHeight: Dp, still: Boolean) {
    val position by animateFloatAsState(
        targetValue = digit.toFloat(),
        animationSpec = if (still) snap() else tween(ROLL, easing = RollEasing),
        label = "digit",
    )
    Box(Modifier.height(lineHeight).clipToBounds()) {
        Column(
            Modifier
                .wrapContentHeight(Alignment.Top, unbounded = true)
                .graphicsLayer { translationY = -position * lineHeight.toPx() },
        ) {
            for (d in 0..9) Text("$d", style = style, modifier = Modifier.height(lineHeight))
        }
    }
}

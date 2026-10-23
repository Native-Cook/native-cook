package com.ngocdevv.nativecook.blocks.charts.neonbarboot

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp

/**
 * A figure that rolls in from zeros when it appears and rolls to every new value, digit by digit.
 * Each cell is sampled against the frame clock inside a graphics layer, so a roll recomposes nothing.
 */
internal class NeonBarBootOdometer {
    private companion object {
        // Each digit rolls for ROLL plus SLOWER per place to its right, after waiting LATER per place:
        // the last digits settle last.
        const val ROLL = 0.42
        const val SLOWER = 0.04
        const val LATER = 0.055
        val SETTLE = CubicBezierEasing(0.2f, 0.8f, 0.2f, 1f)
    }

    /** One character, keyed from the right as in React Native, so a digit keeps its column. */
    private data class Cell(
        val key: Int,
        val character: Char,
        val digit: Int?,
        val from: Double = 0.0,
        val to: Double = 0.0,
        val rollAt: Double = 0.0,
        val rollFor: Double = 0.0,
        /** When the cell starts fading in; negative once it is simply shown. */
        val fadeAt: Double = -1.0,
        val fadeFor: Double = 0.0,
    ) {
        fun value(at: Double): Double {
            if (rollFor <= 0) return to
            val t = ((at - rollAt) / rollFor).coerceIn(0.0, 1.0)
            return from + (to - from) * SETTLE.transform(t.toFloat())
        }

        fun opacity(at: Double): Float {
            if (fadeAt < 0) return 1f
            val t = ((at - fadeAt) / fadeFor).coerceIn(0.0, 1.0)
            // Reanimated's default timing curve.
            val eased = if (t < 0.5) 2 * t * t else 1 - Math.pow(-2 * t + 2, 2.0) / 2
            return eased.toFloat()
        }
    }

    private var cells by mutableStateOf(emptyList<Cell>())

    /**
     * Shows [text]. [fresh] rolls every digit in from zero, as a newly mounted figure does; otherwise
     * only the digits that change roll, from wherever they are.
     */
    fun show(text: String, at: Double, delay: Double, fresh: Boolean, still: Boolean) {
        val old = if (fresh) emptyMap() else cells.associateBy { it.key }
        cells = text.mapIndexed { place, character ->
            val key = text.length - place
            val digit = character.digitToIntOrNull()
            val start = at + delay + LATER * place
            val duration = ROLL + SLOWER * place
            val kept = old[key]
            if (kept != null && (kept.digit == null) == (digit == null)) {
                if (digit != null && digit != kept.digit) {
                    kept.copy(
                        character = character, digit = digit, from = kept.value(start), to = digit.toDouble(),
                        rollAt = start, rollFor = if (still) 0.0 else duration,
                    )
                } else {
                    kept.copy(character = character)
                }
            } else if (still) {
                Cell(key, character, digit, to = (digit ?: 0).toDouble())
            } else {
                // A digit shows as it rolls up; other characters fade in over a whole roll.
                Cell(
                    key, character, digit, to = (digit ?: 0).toDouble(), rollAt = start, rollFor = duration,
                    fadeAt = start, fadeFor = if (digit == null) ROLL else ROLL / 2,
                )
            }
        }
    }

    @Composable
    fun Figure(style: TextStyle, lineHeight: Float, time: () -> Double) {
        Row {
            cells.forEach { cell ->
                key(cell.key) {
                    if (cell.digit != null) {
                        Box(Modifier.height(lineHeight.dp).clipToBounds().graphicsLayer { alpha = cell.opacity(time()) }) {
                            Column(
                                Modifier
                                    .wrapContentHeight(Alignment.Top, unbounded = true)
                                    .graphicsLayer { translationY = -cell.value(time()).toFloat() * lineHeight.dp.toPx() },
                            ) {
                                for (d in 0..9) BasicText(d.toString(), Modifier.height(lineHeight.dp), style = style)
                            }
                        }
                    } else {
                        BasicText(
                            cell.character.toString(),
                            Modifier.height(lineHeight.dp).graphicsLayer { alpha = cell.opacity(time()) },
                            style = style,
                        )
                    }
                }
            }
        }
    }
}

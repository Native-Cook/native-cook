package com.ngocdevv.nativecook.blocks.gestures.chooseyourball

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import kotlin.math.floor
import kotlin.math.pow
import kotlin.math.roundToInt

/** The fixed vertical ramp over the tint: it keeps the foot dark enough to print on. */
private val FALLOFF = Brush.verticalGradient(
    0f to STAGE_INK.copy(alpha = 0.55f),
    0.32f to STAGE_INK.copy(alpha = 0.05f),
    0.74f to STAGE_INK.copy(alpha = 0.42f),
    0.92f to STAGE_INK,
    1f to STAGE_INK,
)

private const val GAMMA = 2.2

/**
 * The ground the ball stands on, coloured by the ball (BallBackdrop.tsx). The colour has a
 * position, not a state: it is read off the carousel's fractional `offset` while drawing, so half
 * way between two balls the room is half way between their colours.
 *
 * `colors` holds one ground colour per slot, in slot order.
 */
@Composable
internal fun BallBackdrop(colors: List<Color>, offset: () -> Float, modifier: Modifier = Modifier) {
    // Reanimated's interpolateColor mixes in gamma 2.2 space; the channels are decoded once.
    val ramp = remember(colors) {
        colors.map { doubleArrayOf(it.red.toDouble().pow(GAMMA), it.green.toDouble().pow(GAMMA), it.blue.toDouble().pow(GAMMA)) }
    }
    Canvas(modifier) {
        val total = ramp.size
        if (total > 0) {
            // `offset` is unbounded; the loop closes because the slot after the last is the first.
            val position = ((offset() % total) + total) % total
            val slot = floor(position).toInt().coerceIn(0, total - 1)
            val from = ramp[slot]
            val to = ramp[(slot + 1) % total]
            val t = (position - slot).toDouble()
            fun channel(i: Int): Int = ((from[i] + (to[i] - from[i]) * t).pow(1 / GAMMA) * 255).roundToInt()
            drawRect(Color(channel(0), channel(1), channel(2)))
        }
        drawRect(FALLOFF)
    }
}

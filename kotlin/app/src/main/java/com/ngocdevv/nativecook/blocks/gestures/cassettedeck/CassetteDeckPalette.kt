package com.ngocdevv.nativecook.blocks.gestures.cassettedeck

import androidx.core.graphics.alpha
import androidx.core.graphics.blue
import androidx.core.graphics.green
import androidx.core.graphics.red
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * Reanimated's `interpolateColor(t, [0, 1], [from, to])` in RGB.
 *
 * Its default RGB interpolation linearizes each colour channel with gamma 2.2, interpolates, then converts it back;
 * alpha remains linear. Both the LED and the transport glyphs use this source default.
 */
internal fun cassetteDeckLerpColor(t: Double, from: Int, to: Int): Int {
    val k = max(0.0, min(1.0, t))
    fun channel(a: Int, b: Int): Int {
        val linear = (a / 255.0).pow(GAMMA) + ((b / 255.0).pow(GAMMA) - (a / 255.0).pow(GAMMA)) * k
        return cassetteDeckRound(linear.pow(1.0 / GAMMA) * 255.0).toInt().coerceIn(0, 255)
    }
    val alpha = from.alpha + (to.alpha - from.alpha) * k
    return cassetteDeckArgb(
        channel(from.red, to.red),
        channel(from.green, to.green),
        channel(from.blue, to.blue),
        alpha / 255.0,
    )
}

private const val GAMMA = 2.2

package com.ngocdevv.nativecook.blocks.animations.cipherseam

import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.exp
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * A vertical three-stop gradient (`colors` at 0, 0.5 and 1 over [length] dp from [from]) that React
 * Native draws in a rectangle under a `Blur` of [sigma]. Blurring after the shading is not the same
 * as blurring the rectangle's edge, and Android has only the second as a paint (a mask filter), so
 * the vertical half of the blur is baked into the gradient: its premultiplied colour is blurred
 * along the gradient (a Gaussian sum) and written as many stops, and the horizontal half is left
 * to the mask filter of whoever draws with [paint].
 *
 * The rectangle to draw with [paint] runs from [top] to [bottom]: 3 sigma beyond the gradient's
 * reach on each side, so the mask filter's own vertical blur lands where the gradient is clear.
 * [opacity] is applied to the paint (`opacity` of the `Rect`).
 */
internal class CipherSeamBlurRamp(
    colors: IntArray,
    from: Float,
    length: Float,
    sigma: Float,
    opacity: Float,
) {
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    val top: Float
    val bottom: Float

    init {
        if (sigma < MIN_BAKED_SIGMA) {
            // A blur this small does not move the gradient: only the edges of the rectangle blur.
            top = from
            bottom = from + length
            paint.shader = LinearGradient(0f, top, 0f, bottom, colors, floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)
        } else {
            val reach = 3f * sigma
            val start = from - reach
            val end = from + length + reach
            val count = ceil((end - start) / (sigma / 3f)).toInt().coerceIn(MIN_STOPS, MAX_STOPS)
            val positions = FloatArray(count) { it / (count - 1f) }
            top = start - reach
            bottom = end + reach
            paint.shader = LinearGradient(0f, start, 0f, end, bake(colors, from, length, sigma, start, end, count), positions, Shader.TileMode.CLAMP)
        }
        // The shader's alpha must not be scaled by a colour left on the paint: opaque first, then the opacity.
        paint.color = Color.BLACK
        paint.alpha = (opacity * 255f).roundToInt().coerceIn(0, 255)
    }

    /** The gradient blurred by [sigma] along y, sampled at [count] points from [start] to [end], as straight ARGB. */
    private fun bake(colors: IntArray, from: Float, length: Float, sigma: Float, start: Float, end: Float, count: Int): IntArray {
        val samples = ceil(length / SOURCE_STEP).toInt() + 1
        // The source, premultiplied, every SOURCE_STEP from `from` to `from + length`.
        val sourceAlpha = DoubleArray(samples)
        val sourceRed = DoubleArray(samples)
        val sourceGreen = DoubleArray(samples)
        val sourceBlue = DoubleArray(samples)
        val straight = DoubleArray(4)
        for (j in 0 until samples) {
            straightColor(colors, (j * SOURCE_STEP / length).coerceAtMost(1f).toDouble(), straight)
            sourceAlpha[j] = straight[0]
            sourceRed[j] = straight[0] * straight[1]
            sourceGreen[j] = straight[0] * straight[2]
            sourceBlue[j] = straight[0] * straight[3]
        }
        val norm = SOURCE_STEP / (sigma * sqrt(2.0 * PI))
        val reach = 4.0 * sigma
        val baked = IntArray(count)
        for (k in 0 until count) {
            val y = start + (end - start) * k / (count - 1f)
            var alpha = 0.0
            var red = 0.0
            var green = 0.0
            var blue = 0.0
            for (j in 0 until samples) {
                val distance = (y - (from + j * SOURCE_STEP)).toDouble()
                if (distance > reach || distance < -reach) continue
                val weight = exp(-distance * distance / (2.0 * sigma * sigma)) * norm
                alpha += weight * sourceAlpha[j]
                red += weight * sourceRed[j]
                green += weight * sourceGreen[j]
                blue += weight * sourceBlue[j]
            }
            baked[k] = if (alpha < 1e-6) {
                Color.argb(0, Color.red(colors[1]), Color.green(colors[1]), Color.blue(colors[1]))
            } else {
                Color.argb(
                    (alpha * 255.0).roundToInt().coerceIn(0, 255),
                    (red / alpha * 255.0).roundToInt().coerceIn(0, 255),
                    (green / alpha * 255.0).roundToInt().coerceIn(0, 255),
                    (blue / alpha * 255.0).roundToInt().coerceIn(0, 255),
                )
            }
        }
        return baked
    }

    /** The gradient's straight colour at [p] (0 to 1) as alpha, red, green, blue, each 0 to 1. */
    private fun straightColor(colors: IntArray, p: Double, out: DoubleArray) {
        val segment = if (p < 0.5) 0 else 1
        val fraction = if (p < 0.5) p / 0.5 else (p - 0.5) / 0.5
        val first = colors[segment]
        val second = colors[segment + 1]
        out[0] = (Color.alpha(first) + (Color.alpha(second) - Color.alpha(first)) * fraction) / 255.0
        out[1] = (Color.red(first) + (Color.red(second) - Color.red(first)) * fraction) / 255.0
        out[2] = (Color.green(first) + (Color.green(second) - Color.green(first)) * fraction) / 255.0
        out[3] = (Color.blue(first) + (Color.blue(second) - Color.blue(first)) * fraction) / 255.0
    }

    private companion object {
        /** Below this a blur only softens the rectangle's edges (`blur={.7}`). */
        const val MIN_BAKED_SIGMA = 1.5f
        const val MIN_STOPS = 16
        const val MAX_STOPS = 160

        /** The source gradient is summed every half dp. */
        const val SOURCE_STEP = 0.5f
    }
}

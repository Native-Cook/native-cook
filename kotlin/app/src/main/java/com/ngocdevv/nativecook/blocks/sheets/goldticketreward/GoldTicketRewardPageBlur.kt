package com.ngocdevv.nativecook.blocks.sheets.goldticketreward

import android.graphics.Bitmap
import kotlin.math.*


/** A separable Gaussian with clamped edges, computed once at 3× reference resolution. */
internal object GoldTicketRewardPageBlur {

    fun blur(source: Bitmap, sigma: Double): Bitmap {
        val w = source.width
        val h = source.height
        val pixels = IntArray(w * h)
        source.getPixels(pixels, 0, w, 0, 0, w, h)
        val radius = ceil(3 * sigma).toInt()
        val weights = DoubleArray(radius * 2 + 1) {
            exp(-(it - radius).toDouble().pow(2) / (2 * sigma * sigma))
        }
        val sum = weights.sum()
        for (i in weights.indices)weights[i] /= sum
        val temp = IntArray(w * h)
        val out = IntArray(w * h)

        fun pass(input: IntArray, output: IntArray, horizontal: Boolean) {
            for (y in 0 until h) for (x in 0 until w) {
                var r = 0.0
                var g = 0.0
                var b = 0.0
                for (k in - radius..radius) {
                    val sx = if (horizontal)(x + k).coerceIn(0, w - 1) else x
                    val sy = if (horizontal)y else (y + k).coerceIn(0, h - 1)
                    val p = input[sy * w + sx]
                    val weight = weights[k + radius]
                    r += ((p ushr 16) and 255) * weight
                    g += ((p ushr 8) and 255) * weight
                    b += (p and 255) * weight
                }
                output[y * w + x] = (255 shl 24) or (r.roundToInt() shl 16) or (g.roundToInt() shl 8) or b.roundToInt()
            }
        }
        pass(pixels, temp, true)
        pass(temp, out, false)
        return Bitmap.createBitmap(out, w, h, Bitmap.Config.ARGB_8888)
    }
}

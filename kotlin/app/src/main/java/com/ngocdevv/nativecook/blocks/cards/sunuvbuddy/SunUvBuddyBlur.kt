package com.ngocdevv.nativecook.blocks.cards.sunuvbuddy

import android.graphics.Bitmap
import kotlin.math.*

/** Software group blur for Android 8–11. Blur premultiplied colour, not straight RGB. */
internal object SunUvBuddyBlur {
    fun apply(source: Bitmap, sigma: Double): Bitmap {
        val width = source.width
        val height = source.height
        val pixels = IntArray(width * height)
        source.getPixels(pixels, 0, width, 0, 0, width, height)
        val radius = ceil(sigma * 3).toInt()
        val kernel = DoubleArray(radius * 2 + 1) { exp(-((it - radius).toDouble().pow(2)) / (2 * sigma * sigma)) }
        val total = kernel.sum()
        for (i in kernel.indices) kernel[i] /= total
        var input = FloatArray(pixels.size * 4)
        for (i in pixels.indices) {
            val argb = pixels[i]
            val alpha = (argb ushr 24) / 255f
            input[i * 4] = alpha
            input[i * 4 + 1] = ((argb ushr 16) and 255) * alpha
            input[i * 4 + 2] = ((argb ushr 8) and 255) * alpha
            input[i * 4 + 3] = (argb and 255) * alpha
        }
        repeat(2) { pass ->
            val output = FloatArray(input.size)
            for (y in 0 until height) for (x in 0 until width) {
                val dst = (y * width + x) * 4
                for (offset in - radius..radius) {
                    val xx = if (pass == 0) x + offset else x
                    val yy = if (pass == 0) y else y + offset
                    if (xx !in 0 until width || yy !in 0 until height) continue
                    val src = (yy * width + xx) * 4
                    val weight = kernel[offset + radius].toFloat()
                    for (channel in 0..3) output[dst + channel] += input[src + channel] * weight
                }
            }
            input = output
        }
        for (i in pixels.indices) {
            val alpha = input[i * 4]
            fun channel(index: Int) = if (alpha > 0.00001f) (input[i * 4 + index] / alpha).roundToInt().coerceIn(0, 255) else 0
            pixels[i] = ((alpha * 255).roundToInt().coerceIn(0, 255) shl 24) or (channel(1) shl 16) or (channel(2) shl 8) or channel(3)
        }
        return Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
    }
}

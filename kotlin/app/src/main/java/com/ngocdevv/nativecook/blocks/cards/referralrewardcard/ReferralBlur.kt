package com.ngocdevv.nativecook.blocks.cards.referralrewardcard

import android.graphics.Bitmap
import kotlin.math.ceil
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.roundToInt

/** Gaussian convolution of premultiplied pixels, performed once, never in a draw callback. */
internal object ReferralBlur {
    fun gaussian(
        source: Bitmap,
        sigma: Float,
    ): Bitmap {
        if (sigma <= 0f) return source
        val width = source.width
        val height = source.height
        val radius = ceil(3.5f * sigma).toInt()
        val kernel =
            FloatArray(radius * 2 + 1) {
                exp(-0.5 * ((it - radius) / sigma).toDouble().pow(2)).toFloat()
            }
        val total = kernel.sum()
        for (i in kernel.indices) kernel[i] /= total
        val pixels = IntArray(width * height)
        source.getPixels(pixels, 0, width, 0, 0, width, height)
        val input = FloatArray(width * height * 4)
        val temp = FloatArray(input.size)
        for (i in pixels.indices) {
            val coverage = (pixels[i] ushr 24) / 255f
            input[i * 4] = coverage * 255f
            input[i * 4 + 1] = (pixels[i] ushr 16 and 255) * coverage
            input[i * 4 + 2] = (pixels[i] ushr 8 and 255) * coverage
            input[i * 4 + 3] = (pixels[i] and 255) * coverage
        }
        for (y in 0 until height) {
            for (x in 0 until width) {
                val destination = (y * width + x) * 4
                for (k in -radius..radius) {
                    if (x + k !in 0 until width) continue
                    val sourceIndex = (y * width + x + k) * 4
                    val weight = kernel[k + radius]
                    for (channelIndex in 0..3) {
                        temp[destination + channelIndex] +=
                            input[sourceIndex + channelIndex] * weight
                    }
                }
            }
        }
        input.fill(0f)
        for (y in 0 until height) {
            for (x in 0 until width) {
                val destination = (y * width + x) * 4
                for (k in -radius..radius) {
                    if (y + k !in 0 until height) continue
                    val sourceIndex = ((y + k) * width + x) * 4
                    val weight = kernel[k + radius]
                    for (channelIndex in 0..3) {
                        input[destination + channelIndex] +=
                            temp[sourceIndex + channelIndex] * weight
                    }
                }
                val coverage = input[destination]
                val alpha = coverage.roundToInt().coerceIn(0, 255)

                fun channel(channelIndex: Int) =
                    if (alpha == 0) {
                        0
                    } else {
                        (input[destination + channelIndex] * 255 / coverage)
                            .roundToInt()
                            .coerceIn(0, 255)
                    }
                pixels[y * width + x] =
                    (alpha shl 24) or (channel(1) shl 16) or (channel(2) shl 8) or channel(3)
            }
        }
        return Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
    }
}

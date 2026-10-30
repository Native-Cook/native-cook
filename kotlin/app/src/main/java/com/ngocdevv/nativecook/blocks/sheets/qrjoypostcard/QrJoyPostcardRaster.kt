package com.ngocdevv.nativecook.blocks.sheets.qrjoypostcard

import android.graphics.Bitmap
import kotlin.math.*

/** One-time CPU image filters. Keeping the bakes software also supports Android 8–11. */
internal object QrJoyPostcardRaster {

    fun blur(source: Bitmap, sigma: Double): Bitmap {
        if (sigma <= 0) return source
        val width = source.width
        val height = source.height
        val radius = ceil(sigma*3).toInt()
        val weights = DoubleArray(radius*2+1) {
            exp(-((it-radius).toDouble().pow(2))/(2*sigma*sigma))
        }
        val total = weights.sum()
        for (i in weights.indices) weights[i]/=total
        val pixels = IntArray(width*height)
        source.getPixels(pixels, 0, width, 0, 0, width, height)
        // Premultiply before convolution so translucent edges keep the source hue.
        var input = Array(4) {
            FloatArray(pixels.size)
        }
        for (i in pixels.indices) {
            val a = (pixels[i] ushr 24)/255f
            input[0][i] = a*255
            input[1][i] = ((pixels[i] ushr 16) and 255)*a
            input[2][i] = ((pixels[i] ushr 8) and 255)*a
            input[3][i] = (pixels[i] and 255)*a
        }
        for (horizontal in listOf(true, false)) {
            val output = Array(4) {
                FloatArray(pixels.size)
            }
            for (y in 0 until height) for (x in 0 until width) {
                val index = y*width+x
                for (offset in -radius..radius) {
                    val xx = if (horizontal) x+offset else x
                    val yy = if (horizontal) y else y+offset
                    if (xx !in 0 until width || yy !in 0 until height) continue
                    val weight = weights[offset+radius].toFloat()
                    val sample = yy*width+xx
                    for (channel in 0..3) output[channel][index]+=input[channel][sample]*weight
                }
            }
            input = output
        }
        for (i in pixels.indices) {
            val a = input[0][i].roundToInt().coerceIn(0, 255)

            fun channel(c: Int) = if (a == 0) 0 else (input[c][i]*255/input[0][i]).roundToInt().coerceIn(0, 255)
            pixels[i] = (a shl 24) or (channel(1) shl 16) or (channel(2) shl 8) or channel(3)
        }
        return Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
    }

    fun silhouette(source: Bitmap, radius: Int, color: Int): Bitmap {
        val w = source.width
        val h = source.height
        val pixels = IntArray(w*h)
        source.getPixels(pixels, 0, w, 0, 0, w, h)
        val horizontal = IntArray(pixels.size)
        val output = IntArray(pixels.size)
        for (y in 0 until h) for (x in 0 until w) {
            var alpha = 0
            for (dx in -radius..radius) if (x+dx in 0 until w) alpha = max(alpha, pixels[y*w+x+dx] ushr 24)
            horizontal[y*w+x] = alpha
        }
        for (y in 0 until h) for (x in 0 until w) {
            var alpha = 0
            for (dy in -radius..radius) if (y+dy in 0 until h) alpha = max(alpha, horizontal[(y+dy)*w+x])
            output[y*w+x] = (alpha shl 24) or color
        }
        return Bitmap.createBitmap(output, w, h, Bitmap.Config.ARGB_8888)
    }
}

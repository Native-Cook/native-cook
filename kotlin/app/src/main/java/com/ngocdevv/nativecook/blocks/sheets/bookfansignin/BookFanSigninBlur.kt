package com.ngocdevv.nativecook.blocks.sheets.bookfansignin

import android.graphics.Bitmap
import kotlin.math.*

/** Separable Gaussian, clamped at the page edge, computed only when resources are created. */
internal object BookFanSigninBlur {
    fun gaussian(source: Bitmap, sigma: Double): Bitmap {
        val width = source.width
        val height = source.height
        val radius = ceil(sigma*3).toInt()
        val weights = DoubleArray(radius*2+1) {
            exp(-((it-radius).toDouble().pow(2))/(2*sigma*sigma))
        }
        val total = weights.sum()
        for (i in weights.indices)weights[i]/= total
        var input = IntArray(width*height)
        source.getPixels(input, 0, width, 0, 0, width, height)
        repeat(2) {
            pass->
            val output = IntArray(input.size)
            for (y in 0 until height)for (x in 0 until width) {
                var red = 0.0
                var green = 0.0
                var blue = 0.0
                for (i in weights.indices) {
                    val delta = i-radius
                    val xx = if (pass==0)(x+delta).coerceIn(0, width-1) else x
                    val yy = if (pass==1)(y+delta).coerceIn(0, height-1) else y
                    val pixel = input[yy*width+xx]
                    val weight = weights[i]
                    red+=((pixel shr 16)and 255)*weight
                    green+=((pixel shr 8)and 255)*weight
                    blue+=(pixel and 255)*weight
                }
                output[y*width+x] = (255 shl 24)or(red.roundToInt() shl 16)or(green.roundToInt() shl 8)or blue.roundToInt()
            }
            input = output
        }
        source.setPixels(input, 0, width, 0, 0, width, height)
        return source
    }
}

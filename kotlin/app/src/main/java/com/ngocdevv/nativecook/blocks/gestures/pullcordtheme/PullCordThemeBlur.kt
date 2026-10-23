package com.ngocdevv.nativecook.blocks.gestures.pullcordtheme

import android.graphics.Bitmap
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Skia blurs an image with an image filter; Android has no such filter on a Paint below API 31, so the two blurred
 * sprites (the bloom and the cast shadow) are rendered once into bitmaps: colour matrix, then a Gaussian blur
 * made of three box passes, in premultiplied colour like Skia.
 */
internal object PullCordThemeBlur {
    /** The copy is made at half the device's pixel density; a blur this soft loses nothing. */
    private const val QUALITY = 0.5f

    fun softCopy(
        source: Bitmap,
        matrix: FloatArray?,
        widthDp: Double,
        heightDp: Double,
        sigmaDp: Double,
        density: Float,
    ): PullCordThemeSoftCopy {
        val scale = density * QUALITY
        val width = max(1, ceil(widthDp * scale).toInt())
        val height = max(1, ceil(heightDp * scale).toInt())
        val sigma = (sigmaDp * scale).toFloat()
        val pad = ceil(sigma * 3).toInt()
        val fullWidth = width + pad * 2
        val fullHeight = height + pad * 2
        val scaled = Bitmap.createScaledBitmap(source, width, height, true)
        val pixels = IntArray(width * height)
        scaled.getPixels(pixels, 0, width, 0, 0, width, height)
        // Premultiplied red, green, blue and alpha planes, 0..1.
        val planes = Array(4) { FloatArray(fullWidth * fullHeight) }
        for (y in 0 until height) {
            for (x in 0 until width) {
                val argb = pixels[y * width + x]
                val a = (argb ushr 24) / 255f
                val r = ((argb shr 16) and 0xFF) / 255f
                val gr = ((argb shr 8) and 0xFF) / 255f
                val b = (argb and 0xFF) / 255f
                val index = (y + pad) * fullWidth + (x + pad)
                if (matrix == null) {
                    planes[0][index] = r * a
                    planes[1][index] = gr * a
                    planes[2][index] = b * a
                    planes[3][index] = a
                } else {
                    val outA = (matrix[15] * r + matrix[16] * gr + matrix[17] * b + matrix[18] * a + matrix[19]).coerceIn(0f, 1f)
                    planes[0][index] = (matrix[0] * r + matrix[1] * gr + matrix[2] * b + matrix[3] * a + matrix[4]).coerceIn(0f, 1f) * outA
                    planes[1][index] = (matrix[5] * r + matrix[6] * gr + matrix[7] * b + matrix[8] * a + matrix[9]).coerceIn(0f, 1f) * outA
                    planes[2][index] = (matrix[10] * r + matrix[11] * gr + matrix[12] * b + matrix[13] * a + matrix[14]).coerceIn(0f, 1f) * outA
                    planes[3][index] = outA
                }
            }
        }
        val scratch = FloatArray(fullWidth * fullHeight)
        for (plane in planes) {
            for (boxRadius in boxRadii(sigma)) {
                blurHorizontal(plane, scratch, fullWidth, fullHeight, boxRadius)
                blurVertical(scratch, plane, fullWidth, fullHeight, boxRadius)
            }
        }
        val out = IntArray(fullWidth * fullHeight)
        for (i in out.indices) {
            val a = planes[3][i]
            if (a <= 0f) continue
            val r = (planes[0][i] / a).coerceIn(0f, 1f)
            val g = (planes[1][i] / a).coerceIn(0f, 1f)
            val b = (planes[2][i] / a).coerceIn(0f, 1f)
            out[i] = ((a * 255f).roundToInt() shl 24) or
                ((r * 255f).roundToInt() shl 16) or
                ((g * 255f).roundToInt() shl 8) or
                (b * 255f).roundToInt()
        }
        val bitmap = Bitmap.createBitmap(fullWidth, fullHeight, Bitmap.Config.ARGB_8888)
        bitmap.setPixels(out, 0, fullWidth, 0, 0, fullWidth, fullHeight)
        return PullCordThemeSoftCopy(bitmap, scale, pad / scale)
    }

    /** Radii of three box blurs whose sum approximates a Gaussian of [sigma] pixels. */
    private fun boxRadii(sigma: Float): IntArray {
        val passes = 3
        val ideal = sqrt(12f * sigma * sigma / passes + 1f)
        var lower = floor(ideal).toInt()
        if (lower % 2 == 0) lower--
        val upper = lower + 2
        val widerCount = ((12f * sigma * sigma - passes * lower * lower - 4f * passes * lower - 3f * passes) /
            (-4f * lower - 4f)).roundToInt()
        return IntArray(passes) { (if (it < widerCount) lower else upper).coerceAtLeast(1).let { size -> (size - 1) / 2 } }
    }

    private fun blurHorizontal(source: FloatArray, target: FloatArray, width: Int, height: Int, radius: Int) {
        val inverse = 1f / (radius * 2 + 1)
        for (y in 0 until height) {
            val row = y * width
            var sum = 0f
            for (x in 0..minOf(radius, width - 1)) sum += source[row + x]
            for (x in 0 until width) {
                target[row + x] = sum * inverse
                val entering = x + radius + 1
                val leaving = x - radius
                if (entering < width) sum += source[row + entering]
                if (leaving >= 0) sum -= source[row + leaving]
            }
        }
    }

    private fun blurVertical(source: FloatArray, target: FloatArray, width: Int, height: Int, radius: Int) {
        val inverse = 1f / (radius * 2 + 1)
        for (x in 0 until width) {
            var sum = 0f
            for (y in 0..minOf(radius, height - 1)) sum += source[y * width + x]
            for (y in 0 until height) {
                target[y * width + x] = sum * inverse
                val entering = y + radius + 1
                val leaving = y - radius
                if (entering < height) sum += source[entering * width + x]
                if (leaving >= 0) sum -= source[leaving * width + x]
            }
        }
    }
}

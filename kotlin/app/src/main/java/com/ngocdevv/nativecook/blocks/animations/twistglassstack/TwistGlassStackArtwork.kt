package com.ngocdevv.nativecook.blocks.animations.twistglassstack

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import kotlin.math.ceil
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.roundToInt

/** Decode and blur the small draft once; no API 31 render effect is needed. */
internal fun twistGlassStackThumbnail(context: Context, density: Float): Bitmap {
    val source = context.assets.open("twistglassstack/twistglassstack_harbour.jpg").use {
        requireNotNull(BitmapFactory.decodeStream(it))
    }
    val side = (60f * density).roundToInt().coerceAtLeast(1)
    val cropped = Bitmap.createBitmap(side, side, Bitmap.Config.ARGB_8888)
    val scale = max(side.toFloat() / source.width, side.toFloat() / source.height)
    val left = (side - source.width * scale) / 2f
    val top = (side - source.height * scale) / 2f
    Canvas(cropped).drawBitmap(
        source,
        null,
        RectF(left, top, left + source.width * scale, top + source.height * scale),
        Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG),
    )
    source.recycle()
    // RN's iOS blurRadius 6 has an effective sigma near 3 dp on this 60 dp thumbnail.
    twistGlassStackGaussianBlur(cropped, 3f * density)
    return cropped
}

/** A separable Gaussian with clamped image edges, matching the opaque photo's blur. */
private fun twistGlassStackGaussianBlur(bitmap: Bitmap, sigma: Float) {
    if (sigma <= 0f) return
    val radius = ceil(3f * sigma).toInt()
    val weights = FloatArray(radius * 2 + 1) { index ->
        val distance = (index - radius).toFloat()
        exp(-distance * distance / (2f * sigma * sigma))
    }
    val total = weights.sum()
    for (i in weights.indices) weights[i] /= total

    val width = bitmap.width
    val height = bitmap.height
    val pixels = IntArray(width * height)
    bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
    val horizontal = FloatArray(pixels.size * 3)
    for (y in 0 until height) {
        val row = y * width
        for (x in 0 until width) {
            var red = 0f
            var green = 0f
            var blue = 0f
            for (k in weights.indices) {
                val color = pixels[row + (x + k - radius).coerceIn(0, width - 1)]
                val weight = weights[k]
                red += ((color ushr 16) and 255) * weight
                green += ((color ushr 8) and 255) * weight
                blue += (color and 255) * weight
            }
            val index = (row + x) * 3
            horizontal[index] = red
            horizontal[index + 1] = green
            horizontal[index + 2] = blue
        }
    }
    for (y in 0 until height) {
        for (x in 0 until width) {
            var red = 0f
            var green = 0f
            var blue = 0f
            for (k in weights.indices) {
                val index = ((y + k - radius).coerceIn(0, height - 1) * width + x) * 3
                val weight = weights[k]
                red += horizontal[index] * weight
                green += horizontal[index + 1] * weight
                blue += horizontal[index + 2] * weight
            }
            pixels[y * width + x] = (255 shl 24) or
                (red.roundToInt().coerceIn(0, 255) shl 16) or
                (green.roundToInt().coerceIn(0, 255) shl 8) or
                blue.roundToInt().coerceIn(0, 255)
        }
    }
    bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
}

/** Draw at (-60 dp, -60 dp) relative to the card; the caller caches this bitmap. */
internal fun twistGlassStackCardShadow(width: Int, height: Int, density: Float): Bitmap {
    val padding = (60f * density).roundToInt()
    val bitmap = Bitmap.createBitmap(width + padding * 2, height + padding * 2, Bitmap.Config.ARGB_8888)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF3B2A8C.toInt()
        alpha = (0.12f * 255).roundToInt()
        // Android's mask radius maps to sigma as 0.57735 * radius + 0.5.
        maskFilter = BlurMaskFilter(((20f * density - 0.5f) / 0.57735f).coerceAtLeast(0.01f), BlurMaskFilter.Blur.NORMAL)
    }
    val left = padding.toFloat()
    val top = padding + 8f * density
    Canvas(bitmap).drawRoundRect(left, top, left + width, top + height, 24f * density, 24f * density, paint)
    return bitmap
}

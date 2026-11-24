package com.ngocdevv.nativecook.blocks.media.comparerevealslider

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.roundToInt

private const val DIR = "comparerevealslider"
/** The glass samples a photo reduced to one pixel per this many dp. */
private const val GLASS_STEP_DP = 4f
/** Three box passes of this radius at that scale approximate Skia's Blur(9). */
private const val GLASS_BOX = 2

internal class Photo(val image: ImageBitmap, val srcOffset: IntOffset, val srcSize: IntSize, val glass: ImageBitmap)

internal class Rooms(val beforeDay: Photo, val afterDay: Photo, val beforeEvening: Photo, val afterEvening: Photo)

/** A blurred shape on a transparent bitmap, with the padding around the shape, px. */
internal class Sprite(val image: ImageBitmap, val pad: Float)

internal fun loadRooms(context: Context, widthPx: Int, heightPx: Int, density: Float): Rooms {
    fun photo(name: String): Photo {
        val bitmap = context.assets.open("$DIR/${DIR}_$name.webp").use { BitmapFactory.decodeStream(it) }
        // fit="cover": the centred part of the photo with the frame's aspect
        val scale = max(widthPx / bitmap.width.toFloat(), heightPx / bitmap.height.toFloat())
        val cropW = (widthPx / scale).roundToInt().coerceIn(1, bitmap.width)
        val cropH = (heightPx / scale).roundToInt().coerceIn(1, bitmap.height)
        val x = (bitmap.width - cropW) / 2
        val y = (bitmap.height - cropH) / 2
        val step = GLASS_STEP_DP * density
        val smallW = ceil(widthPx / step).toInt().coerceAtLeast(1)
        val smallH = ceil(heightPx / step).toInt().coerceAtLeast(1)
        val small = Bitmap.createBitmap(smallW, smallH, Bitmap.Config.ARGB_8888)
        Canvas(small).drawBitmap(
            bitmap,
            Rect(x, y, x + cropW, y + cropH),
            Rect(0, 0, smallW, smallH),
            Paint(Paint.FILTER_BITMAP_FLAG),
        )
        boxBlur(small, GLASS_BOX, passes = 3)
        return Photo(bitmap.asImageBitmap(), IntOffset(x, y), IntSize(cropW, cropH), small.asImageBitmap())
    }
    return Rooms(photo("before-day"), photo("after-day"), photo("before-evening"), photo("after-evening"))
}

/** A separable box blur of an opaque bitmap, edges clamped. */
private fun boxBlur(bitmap: Bitmap, radius: Int, passes: Int) {
    val w = bitmap.width
    val h = bitmap.height
    var src = IntArray(w * h)
    var dst = IntArray(w * h)
    bitmap.getPixels(src, 0, w, 0, 0, w, h)
    val div = radius * 2 + 1
    repeat(passes) {
        for (y in 0 until h) {
            var r = 0
            var g = 0
            var b = 0
            for (i in -radius..radius) {
                val c = src[y * w + i.coerceIn(0, w - 1)]
                r += c shr 16 and 255
                g += c shr 8 and 255
                b += c and 255
            }
            for (x in 0 until w) {
                dst[y * w + x] = (255 shl 24) or ((r / div) shl 16) or ((g / div) shl 8) or (b / div)
                val out = src[y * w + (x - radius).coerceIn(0, w - 1)]
                val inn = src[y * w + (x + radius + 1).coerceIn(0, w - 1)]
                r += (inn shr 16 and 255) - (out shr 16 and 255)
                g += (inn shr 8 and 255) - (out shr 8 and 255)
                b += (inn and 255) - (out and 255)
            }
        }
        var swap = src
        src = dst
        dst = swap
        for (x in 0 until w) {
            var r = 0
            var g = 0
            var b = 0
            for (i in -radius..radius) {
                val c = src[i.coerceIn(0, h - 1) * w + x]
                r += c shr 16 and 255
                g += c shr 8 and 255
                b += c and 255
            }
            for (y in 0 until h) {
                dst[y * w + x] = (255 shl 24) or ((r / div) shl 16) or ((g / div) shl 8) or (b / div)
                val out = src[(y - radius).coerceIn(0, h - 1) * w + x]
                val inn = src[(y + radius + 1).coerceIn(0, h - 1) * w + x]
                r += (inn shr 16 and 255) - (out shr 16 and 255)
                g += (inn shr 8 and 255) - (out shr 8 and 255)
                b += (inn and 255) - (out and 255)
            }
        }
        swap = src
        src = dst
        dst = swap
    }
    bitmap.setPixels(src, 0, w, 0, 0, w, h)
}

/** Skia's sigma as the radius Android's BlurMaskFilter asks for. */
private fun blurRadius(sigmaPx: Float) = max(0.5f, (sigmaPx - 0.5f) / 0.57735f)

/** The handle's pill, blurred by [sigmaDp], in the shadow's colour. */
internal fun shadowSprite(sigmaDp: Float, density: Float): Sprite {
    val sigma = sigmaDp * density
    val pad = ceil(sigma * 3f)
    val w = HANDLE_W * density
    val h = HANDLE_H * density
    val bitmap = Bitmap.createBitmap(ceil(w + pad * 2f).toInt(), ceil(h + pad * 2f).toInt(), Bitmap.Config.ARGB_8888)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.rgb(28, 18, 8)
        maskFilter = BlurMaskFilter(blurRadius(sigma), BlurMaskFilter.Blur.NORMAL)
    }
    Canvas(bitmap).drawRoundRect(RectF(pad, pad, pad + w, pad + h), w / 2f, w / 2f, paint)
    return Sprite(bitmap.asImageBitmap(), pad)
}

/** The soft part of the light on the line: 8 by GLINT_LEN dp, faded at both ends, blurred by 3.5. */
internal fun glintSprite(density: Float): Sprite {
    val sigma = 3.5f * density
    val pad = ceil(sigma * 3f)
    val w = 8f * density
    val h = GLINT_LEN * density
    val bitmap = Bitmap.createBitmap(ceil(w + pad * 2f).toInt(), ceil(h + pad * 2f).toInt(), Bitmap.Config.ARGB_8888)
    val white = android.graphics.Color.WHITE
    val clear = android.graphics.Color.argb(0, 255, 255, 255)
    val core = android.graphics.Color.argb(242, 255, 255, 255)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = white
        shader = LinearGradient(0f, pad, 0f, pad + h, intArrayOf(clear, core, clear), null, Shader.TileMode.CLAMP)
        maskFilter = BlurMaskFilter(blurRadius(sigma), BlurMaskFilter.Blur.NORMAL)
    }
    Canvas(bitmap).drawRect(RectF(pad, pad, pad + w, pad + h), paint)
    return Sprite(bitmap.asImageBitmap(), pad)
}

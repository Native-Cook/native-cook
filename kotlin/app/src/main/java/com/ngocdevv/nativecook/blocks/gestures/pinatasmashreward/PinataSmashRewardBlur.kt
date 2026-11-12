package com.ngocdevv.nativecook.blocks.gestures.pinatasmashreward

import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.roundToInt

/** Hardware Canvas ignores mask filters: rasterize only the two blurred fills on retained surfaces. */
internal class PinataSmashRewardBlur {
    private class PinataSmashRewardSurface {
        var bitmap: Bitmap? = null
        var canvas = Canvas()
        var key = emptyList<Double>()
        val blit = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        fun size(w: Int, h: Int) {
            val old = bitmap
            if (old != null && old.width >= w && old.height >= h) return
            val b = Bitmap.createBitmap(max(w, old?.width ?: 0), max(h, old?.height ?: 0), Bitmap.Config.ARGB_8888); bitmap = b; canvas = Canvas(b); key = emptyList()
        }
    }
    private val floor = PinataSmashRewardSurface()
    private val fill = PinataSmashRewardSurface()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var scale = 1.0
    fun beginFrame(density: Double) { scale = density }
    private fun draw(c: Canvas, surface: PinataSmashRewardSurface, x: Double, y: Double, w: Double, h: Double, sigma: Double, radius: Double, color: Int, alpha: Double, stageScale: Double = 1.0) {
        val raster = scale * stageScale
        val pad = sigma * 4
        val bw = max(1, ceil((w + pad * 2) * raster).toInt()); val bh = max(1, ceil((h + pad * 2) * raster).toInt())
        // Retain maximum-size surfaces; each RenderNode reads a stable bitmap for its one effect.
        surface.size(bw, bh)
        val key = listOf(w, h, sigma, radius, color.toDouble(), raster)
        if (surface.key != key) {
            val bitmap = requireNotNull(surface.bitmap); bitmap.eraseColor(Color.TRANSPARENT)
            paint.reset(); paint.isAntiAlias = true; paint.color = color
            // Skia MakeBlur(1, sigma, true) is BlurStyle.Solid: retain the opaque original fill.
            paint.maskFilter = BlurMaskFilter(max(0.01, (sigma * raster - 0.5) / 0.57735).toFloat(), BlurMaskFilter.Blur.SOLID)
            // Draw in device pixels so sigma keeps its exact screen-space radius at every density.
            val bounds = RectF((pad * raster).toFloat(), (pad * raster).toFloat(), ((pad + w) * raster).toFloat(), ((pad + h) * raster).toFloat())
            if (radius < 0) surface.canvas.drawOval(bounds, paint) else surface.canvas.drawRoundRect(bounds, (radius * raster).toFloat(), (radius * raster).toFloat(), paint)
            surface.key = key
        }
        val bitmap = requireNotNull(surface.bitmap); surface.blit.alpha = (alpha.coerceIn(0.0, 1.0) * 255).roundToInt()
        c.drawBitmap(bitmap, null, RectF((x - pad).toFloat(), (y - pad).toFloat(), (x - pad + bitmap.width / raster).toFloat(), (y - pad + bitmap.height / raster).toFloat()), surface.blit)
    }
    fun shadow(c: Canvas, x: Double, y: Double, w: Double, h: Double, alpha: Double, stageScale: Double) = draw(c, floor, x, y, w, h, 6.0, -1.0, Color.BLACK, alpha, stageScale)
    fun bar(c: Canvas, x: Double, y: Double, w: Double, h: Double, radius: Double, color: Int, alpha: Double) = draw(c, fill, x, y, w, h, 2.0, radius, color, alpha)
}

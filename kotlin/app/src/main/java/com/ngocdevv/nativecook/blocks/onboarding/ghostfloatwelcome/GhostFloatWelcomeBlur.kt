package com.ngocdevv.nativecook.blocks.onboarding.ghostfloatwelcome

import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** Hardware Canvas ignores mask filters. Rasterize only these small masks on software Canvas. */
internal class GhostFloatWelcomeBlur {
    private val resolution = 2f
    private val glowHalf = 104f
    private val ringHalf = 294f
    private val glow = Bitmap.createBitmap((glowHalf * 2 * resolution).toInt(), (glowHalf * 2 * resolution).toInt(), Bitmap.Config.ARGB_8888)
    private val rings = Array(2) { Bitmap.createBitmap((ringHalf * 2 * resolution).toInt(), (ringHalf * 2 * resolution).toInt(), Bitmap.Config.ARGB_8888) }
    private val ringCanvases = rings.map { Canvas(it) }
    private val previousRadius = DoubleArray(2) { -1.0 }
    private val previousWidth = DoubleArray(2) { -1.0 }
    private val composite = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    init {
        val canvas = Canvas(glow)
        canvas.translate(glowHalf * resolution, glowHalf * resolution)
        canvas.scale(resolution, resolution)
        canvas.drawCircle(0f, 0f, 44f, blurPaint(0xD9D1FF, 14.0))
    }
    private fun blurPaint(rgb: Int, sigma: Double): Paint {
        // Android's public radius maps to Skia sigma as radius / sqrt(3) + 0.5.
        val radius = ((sigma - 0.5) * sqrt(3.0)).toFloat()
        return Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF000000.toInt() or rgb
            maskFilter = BlurMaskFilter(radius, BlurMaskFilter.Blur.NORMAL)
        }
    }
    fun glow(canvas: Canvas, x: Double, y: Double, alpha: Double) {
        composite.alpha = (alpha.coerceIn(0.0, 1.0) * 255).roundToInt()
        canvas.drawBitmap(glow, null, RectF((x - glowHalf).toFloat(), (y - glowHalf).toFloat(), (x + glowHalf).toFloat(), (y + glowHalf).toFloat()), composite)
    }
    fun ring(canvas: Canvas, x: Double, y: Double, radius: Double, width: Double, alpha: Double, slot: Int) {
        if (previousRadius[slot] != radius || previousWidth[slot] != width) {
            val bitmap = rings[slot]
            bitmap.eraseColor(Color.TRANSPARENT)
            val software = ringCanvases[slot]
            software.save()
            software.translate(ringHalf * resolution, ringHalf * resolution)
            software.scale(resolution, resolution)
            val stroke = blurPaint(0xDBD1FF, 4.0).apply {
                style = Paint.Style.STROKE
                strokeWidth = width.toFloat()
            }
            software.drawCircle(0f, 0f, radius.toFloat(), stroke)
            software.restore()
            previousRadius[slot] = radius
            previousWidth[slot] = width
        }
        composite.alpha = (alpha.coerceIn(0.0, 1.0) * 255).roundToInt()
        canvas.drawBitmap(rings[slot], null, RectF((x - ringHalf).toFloat(), (y - ringHalf).toFloat(), (x + ringHalf).toFloat(), (y + ringHalf).toFloat()), composite)
    }
}

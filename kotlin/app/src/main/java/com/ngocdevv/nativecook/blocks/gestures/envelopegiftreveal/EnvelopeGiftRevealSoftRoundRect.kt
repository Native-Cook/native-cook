package com.ngocdevv.nativecook.blocks.gestures.envelopegiftreveal

import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.os.Build
import com.ngocdevv.nativecook.shared.sigmaToRadius
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * A rounded rectangle with a soft edge, for the shadows (Skia's `<Blur blur={sigma}>`). Real
 * blur from API 28; below that a few stacked rectangles stand in for it. Drawn in the canvas's
 * current units (dp here), with the colour's alpha as the shadow's strength.
 */
internal class EnvelopeGiftRevealSoftRoundRect(argb: Int, private val sigma: Float) {
    private val baseAlpha = argb ushr 24
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = argb or 0xFF000000.toInt()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            maskFilter = BlurMaskFilter(sigmaToRadius(sigma), BlurMaskFilter.Blur.NORMAL)
        }
    }
    private val bounds = RectF()

    fun draw(canvas: Canvas, x: Float, y: Float, w: Float, h: Float, r: Float, opacity: Float = 1f) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            paint.alpha = (baseAlpha * opacity).roundToInt().coerceIn(0, 255)
            canvas.drawRoundRect(x, y, x + w, y + h, r, r, paint)
            return
        }
        val steps = 6
        val layerAlpha = (baseAlpha * opacity * 1.6f / steps).roundToInt().coerceIn(0, 255)
        paint.alpha = layerAlpha
        for (i in 0 until steps) {
            val grow = sigma * (1.5f - 2f * i / (steps - 1))
            bounds.set(x - grow, y - grow, x + w + grow, y + h + grow)
            if (bounds.width() <= 0f || bounds.height() <= 0f) continue
            canvas.drawRoundRect(bounds, max(0f, r + grow), max(0f, r + grow), paint)
        }
    }
}

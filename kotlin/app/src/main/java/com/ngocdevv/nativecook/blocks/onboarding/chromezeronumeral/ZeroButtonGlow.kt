package com.ngocdevv.nativecook.blocks.onboarding.chromezeronumeral

import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import kotlin.math.*

/** Software-rasterized alpha masks retain the source Gaussian shadow on hardware canvases. */
internal class ZeroButtonGlow {
    private var width = 0f
    private var height = 0f
    private val masks = mutableMapOf<Int, Bitmap>()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val rect = RectF()
    fun draw(canvas: Canvas, w: Float, h: Float, press: Double) {
        if (width != w || height != h) {
            masks.values.forEach { it.recycle() }
            masks.clear()
            width = w
            height = h
        }
        val blur = (24 - 8 * press).coerceIn(16.0,24.0)
        val low = floor(blur).toInt()
        val high = ceil(blur).toInt()
        val opacity = (0.35 - 0.09 * press).toFloat()
        val y = (8 - 4 * press).toFloat()
        fun layer(sigma: Int, alpha: Float) {
            if (alpha <= 0f) return
            val image = masks.getOrPut(sigma) {
                val bitmap = Bitmap.createBitmap(ceil((w+144)*2).toInt(),ceil((h+144)*2).toInt(),Bitmap.Config.ARGB_8888)
                val buffer = Canvas(bitmap)
                buffer.scale(2f,2f)
                val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.rgb(61,75,255)
                    maskFilter = BlurMaskFilter((sigma-.5f)/.57735f,BlurMaskFilter.Blur.NORMAL)
                }
                buffer.drawRoundRect(72f,72f,72+w,72+h,16f,16f,fill)
                bitmap
            }
            paint.alpha = (255*alpha).toInt().coerceIn(0,255)
            rect.set(-72f,-72f+y,w+72,h+72+y)
            canvas.drawBitmap(image,null,rect,paint)
        }
        if (low == high) layer(low,opacity) else {
            layer(low,opacity*(high-blur).toFloat())
            layer(high,opacity*(blur-low).toFloat())
        }
    }
}

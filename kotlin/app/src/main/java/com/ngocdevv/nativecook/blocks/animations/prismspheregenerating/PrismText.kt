package com.ngocdevv.nativecook.blocks.animations.prismspheregenerating

import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.text.TextPaint
import androidx.compose.animation.core.CubicBezierEasing
import kotlin.math.*

/** Cache glyphs at device resolution so the animated shader cannot churn their GPU atlas. */
internal class PrismText {
    private var pixelScale = 0f
    private var headline: Bitmap? = null
    private var captions = emptyList<List<Bitmap>>()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val destination = RectF()
    private val enter = CubicBezierEasing(0.23f, 1f, 0.32f, 1f)

    private fun prepare(k: Float) {
        if (k == pixelScale) return
        pixelScale = k
        val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            // Compensate the system font's visible cap height against the reference.
            textSize = 35f * k
            letterSpacing = -0.6f / 35f
            textAlign = Paint.Align.CENTER
        }
        headline = Bitmap.createBitmap(ceil(402 * k).toInt(), ceil(90 * k).toInt(), Bitmap.Config.ARGB_8888).also { bitmap ->
            val canvas = Canvas(bitmap)
            val baseline = (44 * k - titlePaint.fontMetrics.descent - titlePaint.fontMetrics.ascent) / 2
            canvas.drawText("We are creating", 201 * k, baseline, titlePaint)
            canvas.drawText("your digital art.", 201 * k, baseline + 44 * k, titlePaint)
        }
        val captionPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            typeface = Typeface.create("sans-serif", Typeface.NORMAL)
            textSize = 17f * k
            letterSpacing = -0.2f / 17f
            textAlign = Paint.Align.CENTER
        }
        captions = listOf("Please stand by…", "Shaping the light…").map { line ->
            listOf(0f, 4f, 9f).map { radius ->
                val bitmap = Bitmap.createBitmap(ceil(402 * k).toInt(), ceil(56 * k).toInt(), Bitmap.Config.ARGB_8888)
                captionPaint.maskFilter = if (radius == 0f) null else BlurMaskFilter(radius * k, BlurMaskFilter.Blur.NORMAL)
                val baseline = 16 * k + (24 * k - captionPaint.fontMetrics.descent - captionPaint.fontMetrics.ascent) / 2
                Canvas(bitmap).drawText(line, 201 * k, baseline, captionPaint)
                bitmap
            }
        }
    }

    fun draw(canvas: Canvas, k: Float, motion: PrismMotion, elapsed: Double, opacity: Double) {
        prepare(k)
        val head = requireNotNull(headline)
        val visible = enter.transform(((elapsed - 0.1) / 0.7).coerceIn(0.0, 1.0).toFloat())
        val y = 196.7f + if (motion.reduced) 0f else 12 * (1 - visible)
        paint.alpha = (255 * visible).roundToInt()
        destination.set(0f, y, head.width / k, y + head.height / k)
        canvas.drawBitmap(head, null, destination, paint)
        val envelope = PrismTimeline.caption(motion.clock, motion.reduced)
        val progress = envelope.progress
        val weights = doubleArrayOf(max(0.0, 1 - 2 * progress), 1 - abs(2 * progress - 1), max(0.0, 2 * progress - 1))
        val layers = captions[floor(motion.clock / 5).toInt() % 2]
        for (i in 0..2) {
            paint.alpha = (255 * 0.92 * envelope.opacity * weights[i] * opacity).roundToInt().coerceIn(0, 255)
            val bitmap = layers[i]
            destination.set(0f, 644f, bitmap.width / k, 644f + bitmap.height / k)
            canvas.drawBitmap(bitmap, null, destination, paint)
        }
    }
}

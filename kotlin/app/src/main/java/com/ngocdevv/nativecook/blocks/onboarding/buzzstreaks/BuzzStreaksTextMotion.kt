package com.ngocdevv.nativecook.blocks.onboarding.buzzstreaks

import android.content.res.AssetManager
import android.graphics.BitmapFactory
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import kotlin.math.roundToInt

/** Small Gleam Line Intro / Thinking Word Shimmer primitives, driven by the scene clock. */
internal class BuzzStreaksTextMotion(assets: AssetManager) {
    private val captionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 19.6f
        typeface = Typeface.createFromAsset(assets, "buzzstreaks/buzzstreaks_Gilroy-SemiBold.ttf")
    }
    private val captions = listOf("Invite your friends by buzzing", "They buzz you back")
        .associateWith { Caption(it, captionPaint) }
    private val blurFilters = Array<BlurMaskFilter?>(16) { index ->
        if (index == 0) null else BlurMaskFilter(index * 3f / 15f, BlurMaskFilter.Blur.NORMAL)
    }
    private val label = assets.open("buzzstreaks/buzzstreaks_start-label.png").use {
        requireNotNull(BitmapFactory.decodeStream(it)) { "Cannot decode the CTA label mask" }
    }
    private val labelRect = RectF(0f, 0f, 106f, 23f)
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val ctaMatrix = Matrix()
    private val ctaShader = shineShader(106f, 0.16f)
    private val ctaPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = ctaShader
        xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
    }

    fun drawCaption(text: String, time: Double, start: Double, animate: Boolean, canvas: Canvas) {
        val caption = captions.getValue(text)
        if (!animate || time >= start + (caption.clips.size - 1) * 0.035 + 0.24) {
            captionPaint.alpha = 255
            captionPaint.maskFilter = null
            canvas.drawText(text, caption.left, caption.baseline, captionPaint)
            return
        }
        for (index in caption.clips.indices) {
            val progress = (time - start - index * 0.035) / 0.24
            val opacity = smooth(progress / 0.6)
            if (opacity <= 0.001) continue
            val blur = 3 * (1 - easeOut(progress / 0.8))
            val dy = (4 * (1 - easeOut(progress))).toFloat()
            captionPaint.alpha = (opacity * 255).roundToInt().coerceIn(0, 255)
            captionPaint.maskFilter = blurFilters[(blur / 3 * 15).roundToInt().coerceIn(0, 15)]
            val saved = canvas.save()
            canvas.clipRect(caption.clips[index])
            canvas.translate(0f, dy)
            // Draw the whole phrase so each word retains its original kerning.
            canvas.drawText(text, caption.left, caption.baseline, captionPaint)
            canvas.restoreToCount(saved)
        }
        captionPaint.alpha = 255
        captionPaint.maskFilter = null
    }

    fun drawCtaShine(time: Double, canvas: Canvas) {
        val center = sweepCenter(time, 9.45)
        if (center.isNaN()) return
        ctaMatrix.setTranslate(center * 106f, 0f)
        ctaShader.setLocalMatrix(ctaMatrix)
        val saved = canvas.saveLayer(labelRect, null)
        canvas.drawBitmap(label, null, labelRect, labelPaint)
        canvas.drawRect(labelRect, ctaPaint)
        canvas.restoreToCount(saved)
    }

    private class Caption(val text: String, paint: Paint) {
        val left = 180f - paint.measureText(text) / 2f
        val baseline = paint.fontMetrics.let { 12f - (it.ascent + it.descent) / 2f }
        val clips: Array<RectF>

        init {
            val words = text.split(' ')
            var prefix = ""
            var boundary = left - 6f
            clips = Array(words.size) { index ->
                val end = if (index == words.lastIndex) left + paint.measureText(text) + 6f
                    else left + paint.measureText(prefix + words[index]) + paint.measureText(" ") / 2f
                val clip = RectF(boundary, -6f, end, 30f)
                boundary = end
                prefix += words[index] + if (index < words.lastIndex) " " else ""
                clip
            }
        }
    }

    companion object {
        private fun smooth(value: Double): Double {
            val p = value.coerceIn(0.0, 1.0)
            return p * p * (3 - 2 * p)
        }

        private fun easeOut(value: Double): Double {
            val p = 1 - value.coerceIn(0.0, 1.0)
            return 1 - p * p * p
        }

        fun sweepCenter(time: Double, start: Double): Float {
            val progress = (time - start) / 0.7
            if (progress <= 0 || progress >= 1) return Float.NaN
            return (-0.35 + 1.7 * smooth(progress)).toFloat()
        }

        fun shineShader(width: Float, peak: Float): LinearGradient {
            return LinearGradient(-width / 2f, 0f, width / 2f, 0f,
                intArrayOf(Color.TRANSPARENT, Color.TRANSPARENT,
                    Color.argb((peak * 255).roundToInt(), 255, 255, 255), Color.TRANSPARENT, Color.TRANSPARENT),
                floatArrayOf(0f, 0.2f, 0.5f, 0.8f, 1f), Shader.TileMode.CLAMP)
        }
    }
}

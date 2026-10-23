package com.ngocdevv.nativecook.blocks.onboarding.glassbladedraw

import android.content.Context
import android.graphics.*
import android.os.Build
import kotlin.math.*

internal class BladeRenderer(context: Context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val text = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG)
    private val regular = Typeface.createFromAsset(context.assets, "glassbladedraw/glassbladedraw_Inter-Regular.ttf")
    private val bold = Typeface.createFromAsset(context.assets, "glassbladedraw/glassbladedraw_Inter-Bold.ttf")
    private val mark = context.assets.open("glassbladedraw/glassbladedraw_vane-mark@3x.png").use { BitmapFactory.decodeStream(it) }
    private val gpu = if (Build.VERSION.SDK_INT >= 33) GlassShader(context) else null
    private val fallback = BladeFallback()

    fun draw(canvas: Canvas, layout: BladeLayout, motion: BladeMotion, density: Float, topInset: Double) {
        canvas.save()
        canvas.scale(density, density)
        val uniforms = BladeModel.uniforms(layout, motion.frame, motion.seconds, 1.0 / density)
        if (Build.VERSION.SDK_INT >= 33 && gpu != null) gpu.draw(canvas, paint, layout, uniforms)
        else fallback.draw(canvas, layout, motion.frame)
        val alpha = (motion.opacity * 255).toInt().coerceIn(0, 255)
        val top = max(topInset, 20.0).toFloat() + 12
        paint.shader = null
        paint.alpha = alpha
        val cx = layout.width.toFloat() / 2
        canvas.drawBitmap(mark, null, RectF(cx - 14, top, cx + 14, top + 28), paint)
        drawText(canvas, "vane", cx, top + 36, 12f, 16f, 0xFFF6F7F2.toInt(), alpha * .72, false, .4f)
        // Match CoreText tracking with Android's Inter glyph advances.
        val compact = layout.width < 390
        val size = if (compact) 28f else 30f
        val line = if (compact) 32f else 34f
        drawText(canvas, "Nine blades. One lights.", cx, top + 76, size, line, 0xFFF6F7F2.toInt(), alpha.toDouble(), false, -1.9f)
        drawText(canvas, "Nobody loses.", cx, top + 76 + line, size, line, 0xFFF6F7F2.toInt(), alpha.toDouble(), false, -1.9f)
        drawText(canvas, "Every stake comes back. Only the prize moves.", cx, top + 76 + line * 2 + 16,
            13f, 18f, 0xFFF6F7F2.toInt(), alpha * .62, false, .3f)
        val amount = ((motion.frame.ctaEnergy - .25) / .35).coerceIn(0.0, 1.0)
        // Reanimated interpolateColor uses gamma-correct sRGB interpolation (gamma 2.2).
        fun channel(a: Int, b: Int): Int = (255 * ((a / 255.0).pow(2.2) * (1 - amount) + (b / 255.0).pow(2.2) * amount).pow(1 / 2.2)).roundToInt()
        val labelColour = Color.rgb(channel(230, 11), channel(255, 12), channel(106, 2))
        canvas.save()
        val barY = (layout.ctaY + 28).toFloat()
        val scale = (1 - .03 * motion.frame.press).toFloat()
        canvas.scale(scale, scale, cx, barY)
        drawText(canvas, "Draw a blade", cx, barY - 10, 16f, 20f, labelColour, alpha.toDouble(), true, -.2f)
        canvas.restore()
        canvas.restore()
    }

    private fun drawText(canvas: Canvas, value: String, x: Float, y: Float, size: Float, line: Float,
                         colour: Int, alpha: Double, isBold: Boolean, spacing: Float) {
        text.typeface = if (isBold) bold else regular
        text.textSize = size
        text.color = colour
        text.alpha = alpha.toInt().coerceIn(0, 255)
        text.textAlign = Paint.Align.CENTER
        text.letterSpacing = spacing / size
        val baseline = y + (line - text.fontMetrics.ascent - text.fontMetrics.descent) / 2
        canvas.drawText(value, x, baseline, text)
    }

    @android.annotation.TargetApi(33)
    private class GlassShader(context: Context) {
        private val shader = RuntimeShader(BLADE_AGSL)
        init {
            val bitmap = context.assets.open("glassbladedraw/glassbladedraw_blade-atlas.png").use {
                BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply {
                    inScaled = false
                    inPremultiplied = false
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                })
            }
            val atlas = BitmapShader(requireNotNull(bitmap), Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
            atlas.setFilterMode(BitmapShader.FILTER_MODE_LINEAR)
            // The greyscale atlas is data, bypassing colour-space and premultiplication conversion.
            shader.setInputBuffer("uAtlas", atlas)
        }
        fun draw(canvas: Canvas, paint: Paint, layout: BladeLayout, uniforms: Map<String, FloatArray>) {
            for ((name, value) in uniforms) shader.setFloatUniform(name, value)
            paint.alpha = 255
            paint.shader = shader
            canvas.drawRect(0f, 0f, layout.width.toFloat(), layout.height.toFloat(), paint)
            paint.shader = null
        }
    }
}

package com.ngocdevv.nativecook.blocks.gestures.glassdisconboarding

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.ui.graphics.toArgb
import kotlin.math.ceil
import kotlin.math.min
import kotlin.math.max

/** A transparent snapshot of the destination screen, sampled by the glass shader. */
internal object GlassDiscOnboardingTexture {
    fun make(widthPx: Int, heightPx: Int, density: Float): Bitmap? {
        if (widthPx <= 0 || heightPx <= 0) return null
        val ratio = min(GlassDiscOnboardingTokens.VIEW.textureMaxScale, density)
        val width = widthPx / density
        val height = heightPx / density
        val buttons = glassDiscOnboardingButtonRects(width, height, 1f)
        return runCatching {
            Bitmap.createBitmap(ceil(width * ratio).toInt(), ceil(height * ratio * 2f).toInt(), Bitmap.Config.ARGB_8888).also { bitmap ->
                val canvas = Canvas(bitmap)
                canvas.scale(ratio, ratio)
                val head = textPaint(
                    GlassDiscOnboardingTheme.TYPE.headline,
                    GlassDiscOnboardingTheme.COLOR.lensInk.toArgb(),
                    0f,
                )
                val label = textPaint(
                    GlassDiscOnboardingTheme.TYPE.button.fontSize,
                    GlassDiscOnboardingTheme.COLOR.lensInk.toArgb(),
                    0f,
                    "sans-serif-medium",
                )
                val white = textPaint(
                    GlassDiscOnboardingTheme.TYPE.button.fontSize,
                    GlassDiscOnboardingTheme.COLOR.buttonDarkInk.toArgb(),
                    0f,
                    "sans-serif-medium",
                )
                val fill = Paint(Paint.ANTI_ALIAS_FLAG)
                val headlineBase = height * GlassDiscOnboardingTheme.LAYOUT.headlineBaseline
                centered(canvas, GlassDiscOnboardingTheme.COPY.headline, head, width / 2f, headlineBase)
                centered(canvas, GlassDiscOnboardingTheme.COPY.sub[0], head, width / 2f, headlineBase + GlassDiscOnboardingTheme.TYPE.headlineLine)
                centered(canvas, GlassDiscOnboardingTheme.COPY.sub[1], head, width / 2f, headlineBase + GlassDiscOnboardingTheme.TYPE.headlineLine * 2f)
                fill.color = GlassDiscOnboardingTheme.COLOR.buttonLight.toArgb()
                canvas.drawRoundRect(buttons.light, buttons.light.height() / 2f, buttons.light.height() / 2f, fill)
                fill.color = GlassDiscOnboardingTheme.COLOR.buttonDark.toArgb()
                canvas.drawRoundRect(buttons.dark, buttons.dark.height() / 2f, buttons.dark.height() / 2f, fill)
                val middle = GlassDiscOnboardingTheme.LAYOUT.buttonHeight / 2f + GlassDiscOnboardingTheme.TYPE.button.fontSize * GlassDiscOnboardingTokens.VIEW.buttonLabelBaselineFactor
                centered(canvas, GlassDiscOnboardingTheme.COPY.email, label, width / 2f, buttons.light.top + middle)
                centered(
                    canvas,
                    GlassDiscOnboardingTheme.COPY.phone,
                    white,
                    width / 2f,
                    buttons.dark.top + GlassDiscOnboardingTheme.LAYOUT.buttonHeightDark / 2f + GlassDiscOnboardingTheme.TYPE.button.fontSize * GlassDiscOnboardingTokens.VIEW.buttonLabelBaselineFactor,
                )
                val lone = textPaint(
                    GlassDiscOnboardingTheme.TYPE.lens,
                    GlassDiscOnboardingTheme.COLOR.lensInk.toArgb(),
                    0f,
                    "sans-serif-medium",
                )
                centered(canvas, GlassDiscOnboardingTheme.COPY.lens, lone, width / 2f, height * 1.5f)
            }
        }.getOrNull()
    }

    private fun textPaint(size: Float, color: Int, tracking: Float, family: String = "sans-serif"): Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = size
        this.color = color
        typeface = Typeface.create(family, Typeface.NORMAL)
        letterSpacing = tracking / max(size, 1f)
    }

    private fun centered(canvas: Canvas, text: String, paint: Paint, x: Float, baseline: Float) {
        canvas.drawText(text, x - paint.measureText(text) / 2f, baseline, paint)
    }
}

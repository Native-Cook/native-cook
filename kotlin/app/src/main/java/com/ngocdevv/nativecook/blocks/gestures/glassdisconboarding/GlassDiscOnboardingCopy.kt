package com.ngocdevv.nativecook.blocks.gestures.glassdisconboarding

import android.graphics.Canvas
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Paint
import android.graphics.Shader
import androidx.compose.ui.graphics.toArgb
import kotlin.math.sin

/** Draws the blush field and copy below and above the lens without allocating text layouts per frame. */
internal object GlassDiscOnboardingCopy {
    private val backdropPixels = Bitmap.createBitmap(
        intArrayOf(
            GlassDiscOnboardingTheme.COLOR.bgTL.toArgb(), GlassDiscOnboardingTheme.COLOR.bgTR.toArgb(),
            GlassDiscOnboardingTheme.COLOR.bgBL.toArgb(), GlassDiscOnboardingTheme.COLOR.bgBR.toArgb(),
        ),
        2,
        2,
        Bitmap.Config.ARGB_8888,
    )

    fun drawBackdrop(canvas: Canvas, width: Float, height: Float, density: Float, frame: GlassDiscOnboardingFrame) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
            shader = BitmapShader(backdropPixels, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).also { shader ->
                // Map the 2 × 2 texel centres (.5, .5) → (0, 0) and (1.5, 1.5) → (W, H).
                shader.setLocalMatrix(android.graphics.Matrix().apply {
                    setScale(width, height)
                    postTranslate(-width / 2f, -height / 2f)
                })
            }
        }
        canvas.drawRect(0f, 0f, width, height, paint)
        if (frame.headlineAlpha > 0.005f) {
            val cx = width / 2f + GlassDiscOnboardingMotion.UPGRADE.fieldDrift * density * sin(frame.clockMillis / GlassDiscOnboardingMotion.UPGRADE.fieldPeriodMs * (Math.PI * 2)).toFloat()
            val cy = height * GlassDiscOnboardingTheme.LAYOUT.beadRestY
            paint.shader = android.graphics.RadialGradient(
                cx, cy, width * GlassDiscOnboardingTokens.VIEW.fieldRadius,
                intArrayOf(GlassDiscOnboardingTheme.COLOR.fieldA.toArgb(), GlassDiscOnboardingTheme.COLOR.fieldB.toArgb(), 0x00B05CFF),
                floatArrayOf(0f, GlassDiscOnboardingTokens.VIEW.fieldMiddle, 1f), Shader.TileMode.CLAMP,
            )
            paint.alpha = (frame.headlineAlpha * 255).toInt()
            canvas.drawCircle(cx, cy, width * GlassDiscOnboardingTokens.VIEW.fieldRadius, paint)
            paint.shader = null
        }
    }

    fun drawUnderGlass(canvas: Canvas, width: Float, height: Float, density: Float, frame: GlassDiscOnboardingFrame, reducedMotion: Boolean) {
        val intro = textPaint(GlassDiscOnboardingTheme.TYPE.intro.fontSize * density, GlassDiscOnboardingTheme.COLOR.ink.toArgb(), GlassDiscOnboardingTheme.TYPE.intro.letterSpacing / GlassDiscOnboardingTheme.TYPE.intro.fontSize)
        intro.alpha = (frame.introAlpha * 255).toInt()
        val lift = if (reducedMotion) 0f else (1f - frame.introAlpha) * GlassDiscOnboardingMotion.UPGRADE.rise * density
        centered(canvas, GlassDiscOnboardingTheme.COPY.intro[0], intro, width / 2f, height * GlassDiscOnboardingTheme.LAYOUT.introBaseline - lift)
        centered(canvas, GlassDiscOnboardingTheme.COPY.intro[1], intro, width / 2f, height * GlassDiscOnboardingTheme.LAYOUT.introBaseline + GlassDiscOnboardingTheme.TYPE.intro.lineHeight * density - lift)

        val headline = textPaint(GlassDiscOnboardingTheme.TYPE.headline * density, GlassDiscOnboardingTheme.COLOR.ink.toArgb(), GlassDiscOnboardingTheme.TYPE.headlineTracking / GlassDiscOnboardingTheme.TYPE.headline)
        val baseline = height * GlassDiscOnboardingTheme.LAYOUT.headlineBaseline
        drawLine(canvas, GlassDiscOnboardingTheme.COPY.headline, headline, width / 2f, baseline, frame.headlineAlpha, density, reducedMotion)
        headline.color = GlassDiscOnboardingTheme.COLOR.inkSoft.toArgb()
        drawLine(canvas, GlassDiscOnboardingTheme.COPY.sub[0], headline, width / 2f, baseline + GlassDiscOnboardingTheme.TYPE.headlineLine * density, frame.restAlpha, density, reducedMotion)
        val late = (frame.restAlpha * (1f + GlassDiscOnboardingMotion.UPGRADE.lineStagger.toFloat() / GlassDiscOnboardingMotion.FADE.restMs) - GlassDiscOnboardingMotion.UPGRADE.lineStagger.toFloat() / GlassDiscOnboardingMotion.FADE.restMs).coerceIn(0f, 1f)
        drawLine(canvas, GlassDiscOnboardingTheme.COPY.sub[1], headline, width / 2f, baseline + GlassDiscOnboardingTheme.TYPE.headlineLine * density * 2f, late, density, reducedMotion)
    }

    fun drawForeground(canvas: Canvas, width: Float, height: Float, density: Float, frame: GlassDiscOnboardingFrame) {
        val hint = textPaint(GlassDiscOnboardingTheme.TYPE.hint.fontSize * density, GlassDiscOnboardingTheme.COLOR.hint.toArgb(), GlassDiscOnboardingTheme.TYPE.hint.letterSpacing / GlassDiscOnboardingTheme.TYPE.hint.fontSize, "sans-serif-medium")
        hint.alpha = (frame.hintAlpha * (GlassDiscOnboardingMotion.UPGRADE.hintBase + (1f - GlassDiscOnboardingMotion.UPGRADE.hintBase) * frame.glint) * 255).toInt()
        // The engine publishes the lens after its touch-down lift; the source's hint follows the raw dome.
        val domeRestY = height * GlassDiscOnboardingTheme.LAYOUT.domeTop + width * GlassDiscOnboardingTheme.LAYOUT.domeRadius
        val hintShift = frame.discY + frame.nudge - domeRestY
        centered(canvas, GlassDiscOnboardingTheme.COPY.hint, hint, width / 2f, height * GlassDiscOnboardingTheme.LAYOUT.hintY - hint.ascent() + hintShift)
    }

    private fun drawLine(canvas: Canvas, text: String, paint: Paint, x: Float, baseline: Float, alpha: Float, density: Float, reducedMotion: Boolean) {
        if (alpha <= 0.005f) return
        val rise = if (reducedMotion) 0f else (1f - alpha) * (1f - alpha) * (1f - alpha) * GlassDiscOnboardingMotion.UPGRADE.rise * density
        paint.alpha = (alpha * 255).toInt()
        centered(canvas, text, paint, x, baseline + rise)
    }

    private fun textPaint(size: Float, color: Int, tracking: Float, family: String = "sans-serif"): Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = size
        this.color = color
        typeface = android.graphics.Typeface.create(family, android.graphics.Typeface.NORMAL)
        letterSpacing = tracking
    }

    private fun centered(canvas: Canvas, text: String, paint: Paint, x: Float, baseline: Float) {
        canvas.drawText(text, x - paint.measureText(text) / 2f, baseline, paint)
    }
}

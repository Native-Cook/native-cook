package com.ngocdevv.nativecook.blocks.gestures.glassdisconboarding

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import androidx.compose.ui.graphics.toArgb

/** Draws the lens material and all physics-owned overlays in physical Canvas pixels. */
internal class GlassDiscOnboardingRenderer {
    private val lensShader = GlassDiscOnboardingShader()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

    fun draw(
        canvas: Canvas,
        frame: GlassDiscOnboardingFrame,
        texture: Bitmap?,
        bubbleAtlas: Bitmap,
        lightX: Float = 0f,
        lightY: Float = 0f,
    ) {
        lensShader.draw(canvas, frame, texture, lightX, lightY)
        drawPressureRings(canvas, frame)
        drawBubbles(canvas, frame, bubbleAtlas)
        drawFinger(canvas, frame)
    }

    private fun drawPressureRings(canvas: Canvas, frame: GlassDiscOnboardingFrame) {
        paint.style = Paint.Style.STROKE
        paint.color = GlassDiscOnboardingTheme.COLOR.pulse.toArgb()
        for (ring in frame.pressureRings) {
            paint.strokeWidth = ring.strokeWidth
            paint.alpha = (ring.alpha * 255f).toInt().coerceIn(0, 255)
            canvas.drawCircle(frame.discX, frame.discY + frame.nudge, ring.radius, paint)
        }
        paint.style = Paint.Style.FILL
        paint.alpha = 255
    }

    private fun drawBubbles(canvas: Canvas, frame: GlassDiscOnboardingFrame, bubbleAtlas: Bitmap) {
        for (bubble in frame.bubbles) {
            val displayedRadius = bubble.radius * bubble.scale
            if (displayedRadius < MinimumBubbleRadius || bubble.opacity <= 0f) {
                continue
            }
            val saveCount = canvas.save()
            canvas.translate(bubble.x, bubble.y)
            if (bubble.wobble > MinimumWobble) {
                val degrees = Math.toDegrees(bubble.rotationRad.toDouble()).toFloat()
                canvas.rotate(degrees)
                canvas.scale(1f + bubble.wobble, 1f - bubble.wobble)
                canvas.rotate(-degrees)
            }
            paint.alpha = (bubble.opacity * 255f).toInt().coerceIn(0, 255)
            drawAtlasBubble(canvas, bubbleAtlas, bubble.atlasCell, displayedRadius)
            canvas.restoreToCount(saveCount)
        }
        paint.alpha = 255
    }

    private fun drawAtlasBubble(canvas: Canvas, atlas: Bitmap, atlasCell: Int, radius: Float) {
        val cellWidth = atlas.width / GlassDiscOnboardingTheme.ATLAS.columns
        val cellHeight = atlas.height / GlassDiscOnboardingTheme.ATLAS.rows
        val column = atlasCell % GlassDiscOnboardingTheme.ATLAS.columns
        val row = atlasCell / GlassDiscOnboardingTheme.ATLAS.columns % GlassDiscOnboardingTheme.ATLAS.rows
        val source = Rect(
            column * cellWidth,
            row * cellHeight,
            (column + 1) * cellWidth,
            (row + 1) * cellHeight,
        )
        val destination = RectF(-radius, -radius, radius, radius)
        canvas.drawBitmap(atlas, source, destination, paint)
    }

    private fun drawFinger(canvas: Canvas, frame: GlassDiscOnboardingFrame) {
        if (frame.finger.alpha <= 0.01f) {
            return
        }
        val pointScale = frame.density
        val radius = GlassDiscOnboardingTheme.LAYOUT.fingerRadius * pointScale
        paint.style = Paint.Style.FILL
        paint.color = GlassDiscOnboardingTheme.COLOR.finger.toArgb()
        paint.alpha = (frame.finger.alpha * GlassDiscOnboardingTokens.VIEW.fingerFillAlpha * 255f).toInt().coerceIn(0, 255)
        canvas.drawCircle(frame.finger.x, frame.finger.y, radius, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = GlassDiscOnboardingTokens.VIEW.fingerStrokeWidth * pointScale
        paint.color = GlassDiscOnboardingTheme.COLOR.fingerRing.toArgb()
        paint.alpha = (frame.finger.alpha * GlassDiscOnboardingTokens.VIEW.fingerRingAlpha * 255f).toInt().coerceIn(0, 255)
        canvas.drawCircle(frame.finger.x, frame.finger.y, radius, paint)
        paint.style = Paint.Style.FILL
        paint.alpha = 255
    }

    companion object {
        private const val MinimumBubbleRadius = GlassDiscOnboardingTokens.VIEW.minimumBubbleRadius
        private const val MinimumWobble = GlassDiscOnboardingTokens.VIEW.minimumWobble
    }
}

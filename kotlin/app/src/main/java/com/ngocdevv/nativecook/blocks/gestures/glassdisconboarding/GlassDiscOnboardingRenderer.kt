package com.ngocdevv.nativecook.blocks.gestures.glassdisconboarding

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PathMeasure
import android.graphics.Typeface

internal class GlassDiscOnboardingRenderer(
    context: Context,
    private val layout: GlassDiscOnboardingLayout,
    private val pages: GlassDiscOnboardingPages,
) {
    private val lens = GlassDiscOnboardingShader()
    private val routeMeasure = PathMeasure(pages.route, false)
    private val routeSegment = Path()
    private val routeCase = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFFFFFF.toInt()
        style = Paint.Style.STROKE
        strokeWidth = layout.dp(9f)
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val routeLine = Paint(routeCase).apply {
        color = GlassDiscOnboardingTheme.Color.Route
        strokeWidth = layout.dp(5f)
    }
    private val hintLine = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = GlassDiscOnboardingTheme.Color.Ink
        style = Paint.Style.STROKE
        strokeWidth = layout.dp(2f)
        strokeCap = Paint.Cap.SQUARE
        strokeJoin = Paint.Join.MITER
    }
    private val hintText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = GlassDiscOnboardingTheme.Color.Ink
        textSize = layout.dp(GlassDiscOnboardingTheme.Type.Hint)
        typeface = Typeface.createFromAsset(
            context.assets,
            "glassdisconboarding/glassdisconboarding_Inter-SemiBold.ttf",
        )
        isSubpixelText = true
    }
    private val ghostFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = GlassDiscOnboardingTheme.Color.Finger }
    private val ghostStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = GlassDiscOnboardingTheme.Color.FingerEdge
        style = Paint.Style.STROKE
        strokeWidth = layout.density
    }

    fun draw(canvas: Canvas, frame: GlassDiscOnboardingFrame, showGhost: Boolean) {
        canvas.drawBitmap(pages.intro, 0f, 0f, null)
        lens.draw(canvas, pages.map, layout, frame)
        drawRoute(canvas, frame.route)
        drawHint(canvas, frame.hint)
        if (showGhost) drawGhost(canvas, frame)
    }

    private fun drawRoute(canvas: Canvas, progress: Float) {
        if (progress <= 0.001f) return
        routeSegment.rewind()
        routeMeasure.getSegment(0f, routeMeasure.length * progress.coerceIn(0f, 1f), routeSegment, true)
        canvas.drawPath(routeSegment, routeCase)
        canvas.drawPath(routeSegment, routeLine)
    }

    private fun drawHint(canvas: Canvas, alpha: Float) {
        if (alpha <= 0.001f) return
        val opacity = (alpha * 255f).toInt().coerceIn(0, 255)
        val bottomOffset = layout.height -
            (layout.domeY - layout.dp(GlassDiscOnboardingTheme.Layout.DomeRadius)) + layout.dp(16f)
        val contentBottom = layout.height - bottomOffset + layout.dp(8f * (1f - alpha))
        val centerX = layout.width / 2f
        val caretY = contentBottom - layout.dp(27f)
        val half = layout.dp(5f)
        val caret = Path().apply {
            moveTo(centerX - half, caretY + half)
            lineTo(centerX, caretY)
            lineTo(centerX + half, caretY + half)
        }
        hintLine.alpha = (opacity * 0.7f).toInt()
        canvas.drawPath(caret, hintLine)
        hintText.alpha = opacity
        val label = "Swipe up to explore"
        canvas.drawText(label, centerX - hintText.measureText(label) / 2f, contentBottom - layout.dp(3f), hintText)
    }

    private fun drawGhost(canvas: Canvas, frame: GlassDiscOnboardingFrame) {
        if (frame.ghostOpacity <= 0.001f) return
        val baseRadius = layout.dp(GlassDiscOnboardingTheme.Layout.Finger) / 2f
        val radius = baseRadius * (1f - 0.12f * frame.ghostPressed)
        val alpha = (frame.ghostOpacity * 255f).toInt().coerceIn(0, 255)
        ghostFill.alpha = alpha
        ghostStroke.alpha = alpha
        canvas.drawCircle(frame.ghostX, frame.ghostY, radius, ghostFill)
        canvas.drawCircle(frame.ghostX, frame.ghostY, radius - layout.density / 2f, ghostStroke)
    }
}

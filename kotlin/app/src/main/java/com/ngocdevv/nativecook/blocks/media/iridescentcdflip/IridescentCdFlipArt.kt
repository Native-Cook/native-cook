package com.ngocdevv.nativecook.blocks.media.iridescentcdflip

import android.graphics.BlurMaskFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.os.Build
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import com.ngocdevv.nativecook.shared.sigmaToRadius
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.sin

/** The disc's two faces, the case art and the HUD frame, drawn in code. Ported from IridescentCdFlipArt.swift. */
internal val CdBackground = Color(0xFF060607)
private val streakColors = longArrayOf(0xFF3AA8FF, 0xFF5FD8FF, 0xFFE8283C, 0xFFFF4B3A, 0xFFF4E3B5, 0xFF3BE39A)
private val warmColors = longArrayOf(0xFFFF6A2B, 0xFFE8283C, 0xFFFFB46B, 0xFFFF6A2B, 0xFFE8283C, 0xFFFF8A3D, 0xFFC21F3A)
internal val cdHue = longArrayOf(0xFFFF7AD9, 0xFF9B7BFF, 0xFF5BC8FF, 0xFF5BFFC8, 0xFFF6FF7A, 0xFFFF9A6B)

private fun argb(c: Long, alpha: Float = 1f) = Color(c).copy(alpha = alpha).toArgb()

internal class CdPainter {
    private val streakPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val mono = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
    private var blurKey = -1
    private var blur: BlurMaskFilter? = null

    /**
     * The printed art's light streaks inside the square (x, y, s): broad warm bands across the lower
     * third and a fan of short cold streaks at the right, slightly blurred.
     */
    fun DrawScope.streaks(x: Float, y: Float, s: Float) {
        val canvas = drawContext.canvas.nativeCanvas
        val sigma = max(0.6f, s * 0.006f)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val key = (sigma * 10).toInt()
            if (key != blurKey) {
                blurKey = key
                blur = BlurMaskFilter(sigmaToRadius(sigma), BlurMaskFilter.Blur.NORMAL)
            }
            streakPaint.maskFilter = blur
        }
        for (k in 0 until 7) {
            val yy = y + s * (0.71f + k * 0.034f)
            val hgt = s * (0.012f + 0.008f * (k % 3))
            val c = warmColors[k]
            streakPaint.shader = LinearGradient(x, yy, x + s, yy, intArrayOf(argb(c, 0.75f), argb(c), argb(c, 0.9f)), null, Shader.TileMode.CLAMP)
            canvas.drawRoundRect(RectF(x - 4, yy, x + s + 4, yy + hgt), hgt / 2, hgt / 2, streakPaint)
        }
        for (k in 0 until 6) {
            val yy = y + s * (0.2f + k * 0.075f)
            val len = s * (0.18f + 0.1f * ((k * 7) % 3))
            val hgt = s * (0.016f + 0.006f * (k % 2))
            val c = streakColors[k]
            streakPaint.shader = LinearGradient(
                x + s - len, yy, x + s, yy,
                intArrayOf(argb(c, 0f), argb(c, 0.95f), Color.White.copy(alpha = 0.9f).toArgb()), null, Shader.TileMode.CLAMP,
            )
            canvas.drawRoundRect(RectF(x + s - len, yy, x + s + 4, yy + hgt), hgt / 2, hgt / 2, streakPaint)
        }
        streakPaint.shader = null
        streakPaint.maskFilter = null
    }

    /** Mono text left-aligned (or right-aligned) with its line box's vertical centre (or top, or bottom) at y. */
    fun DrawScope.monoText(text: String, x: Float, y: Float, size: Float, color: Color, tracking: Float, align: Paint.Align, anchorY: Int) {
        val p = textPaint
        p.typeface = mono
        p.textSize = size
        p.letterSpacing = tracking / size
        p.color = color.toArgb()
        p.textAlign = align
        val fm = p.fontMetrics
        val baseline = when (anchorY) {
            0 -> y - (fm.ascent + fm.descent) / 2 // centre
            1 -> y - fm.ascent // top
            else -> y - fm.descent // bottom
        }
        drawContext.canvas.nativeCanvas.drawText(text, x, baseline, p)
    }

    /** The face of the disc (radius r) in the square (0, 0, 2r, 2r), seen at turn [angle] (degrees, for the light). */
    fun DrawScope.face(r: Float, angle: Double, back: Boolean, alpha: Float = 1f) {
        val c = Offset(r, r)
        val a = angle * PI / 180
        val disc = Path().apply { addOval(Rect(c, r)) }
        clipPath(disc) {
            if (back) {
                drawCircle(
                    Brush.radialGradient(0f to Color(0xFFE3E5EA), 0.6f to Color(0xFFA9ACB4), 1f to Color(0xFFD5D7DD), center = c, radius = r),
                    r, c, alpha = alpha,
                )
                // The diffraction rainbow: two hue cycles around the disc, swept round as it turns.
                val ring = Path().apply {
                    fillType = PathFillType.EvenOdd
                    addOval(Rect(c, r))
                    addOval(Rect(c, r * 0.33f))
                }
                val stops = ArrayList<Pair<Float, Color>>()
                for (k in 0 until 12) {
                    stops.add(Pair(k / 12f, Color(cdHue[k % 6]).copy(alpha = 0.7f)))
                    stops.add(Pair((k + 0.55f) / 12f, Color(cdHue[k % 6]).copy(alpha = 0.08f)))
                }
                stops.add(Pair(1f, Color(cdHue[0]).copy(alpha = 0.7f)))
                rotate((2 * angle).toFloat(), c) {
                    drawPath(ring, Brush.sweepGradient(*stops.toTypedArray(), center = c), alpha = alpha)
                }
                for (k in 0 until 18) {
                    val rad = r * (0.36f + 0.62f * k / 17f)
                    drawCircle(Color.Black.copy(alpha = if (k % 3 == 0) 0.07f else 0.035f), rad, c, alpha = alpha, style = Stroke(0.6f))
                }
            } else {
                drawCircle(Brush.radialGradient(listOf(Color(0xFF17171B), Color(0xFF08080A)), center = c, radius = r), r, c, alpha = alpha)
                streaks(0f, 0f, 2 * r)
                monoText("MIRROR INDEX", r * 0.24f, r * 0.62f, max(7f, r * 0.075f), Color(0xFFD8B25A).copy(alpha = alpha), 1.5f, Paint.Align.LEFT, 0)
                drawCircle(Color(0xFFE8283C).copy(alpha = 0.8f), r * 0.34f, c, alpha = alpha, style = Stroke(1f))
                drawCircle(Color(0xFF3BE39A).copy(alpha = 0.7f), r * 0.355f, c, alpha = alpha, style = Stroke(1f))
            }
            // The light: a soft diagonal band that slides across as the disc turns.
            val u = (0.5 + 0.6 * sin(a)).toFloat()
            drawCircle(
                Brush.linearGradient(
                    0f to Color.White.copy(alpha = 0f), 0.5f to Color.White.copy(alpha = if (back) 0.38f else 0.16f), 1f to Color.White.copy(alpha = 0f),
                    start = Offset(2 * r * (u - 0.35f), 2 * r * 0.2f), end = Offset(2 * r * (u + 0.35f), 2 * r * 0.8f),
                ),
                r, c, alpha = alpha,
            )
            // Hub: a clear plastic ring, a grey clamping ring, the hole.
            val hub = if (back) listOf(Color(0xE6EDEEF2), Color(0xE6B9BCC4)) else listOf(Color(0xE68C8F96), Color(0xE64A4C52))
            drawCircle(
                Brush.radialGradient(0f to hub[0], 0.258f to hub[0], 1f to hub[1], center = c, radius = r * 0.31f),
                r * 0.31f, c, alpha = alpha,
            )
            drawCircle(Color.White.copy(alpha = 0.35f), r * 0.31f, c, alpha = alpha, style = Stroke(1f))
            drawCircle(Color.White.copy(alpha = if (back) 0.5f else 0.25f), r * 0.2f, c, alpha = alpha, style = Stroke(1.2f))
            drawCircle(CdBackground, r * 0.075f, c, alpha = alpha)
            drawCircle(Color.White.copy(alpha = 0.2f), r * 0.075f, c, alpha = alpha, style = Stroke(0.8f))
            // The rim.
            drawCircle(
                Brush.linearGradient(listOf(Color.White.copy(alpha = 0.55f), Color.White.copy(alpha = 0.12f), Color.White.copy(alpha = 0.4f)), Offset.Zero, Offset(2 * r, 2 * r)),
                r - 0.75f, c, alpha = alpha, style = Stroke(1.5f),
            )
        }
    }
}

/** The chamfered HUD frame: corners cut at the top right and bottom left. */
internal fun cdFrame(x: Float, y: Float, s: Float, cut: Float) = Path().apply {
    moveTo(x, y)
    lineTo(x + s - cut, y)
    lineTo(x + s, y + cut)
    lineTo(x + s, y + s)
    lineTo(x + cut, y + s)
    lineTo(x, y + s - cut)
    close()
}

/** Film grain: fixed specks over the black. */
internal fun DrawScope.cdGrain(w: Float, h: Float) {
    for (i in 0 until 700) {
        val x = (CdMotion.hash(i, 3) * w).toFloat()
        val y = (CdMotion.hash(i, 4) * h).toFloat()
        val s = (0.6 + 1.2 * CdMotion.hash(i, 5)).toFloat()
        drawCircle(Color.White.copy(alpha = (0.03 + 0.07 * CdMotion.hash(i, 6)).toFloat()), s / 2, Offset(x + s / 2, y + s / 2))
    }
}

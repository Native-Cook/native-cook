package com.ngocdevv.nativecook.blocks.progress.printlinescrub

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Below API 33 there is no AGSL, so the scene is drawn with bitmaps, gradients and paths: the faint perspective grid, the lighter
 * floor, the rose bounce and the contact shadow; the matte sculpt below the cut and the grey slicer mesh above it (72 %), the hot band
 * on the fresh layers; and, at the cut, one ellipse per silhouette span with a lit skin ring, a hot wall and the travelling bead.
 * The honeycomb, the projective tilt and the glass refraction are left out; the lens only magnifies. Coordinates are dp.
 */
internal class PrintLineScrubSceneFallback(private val solid: Bitmap, private val ghost: Bitmap) {
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val atop = Paint().apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_ATOP) }
    private val src = android.graphics.Rect(0, 0, PrintLineScrubSilhouette.FIGURE_W, PrintLineScrubSilhouette.FIGURE_H)
    private val dst = RectF()
    private val oval = RectF()

    private val ground = Color.rgb(0xEC, 0xE9, 0xE6)
    private val groundHi = Color.rgb(0xF4, 0xF1, 0xEE)
    private val grid = Color.rgb(0xDD, 0xD9, 0xD5)
    private val rose = Color.rgb(0xF3, 0xDA, 0xD3)
    private val fil = Color.rgb(0xD8, 0x43, 0x3A)
    private val filShadow = Color.rgb(0xA8, 0x2E, 0x2A)
    private val filLight = Color.rgb(0xEE, 0x6A, 0x5C)
    private val heatHot = Color.rgb(0xFF, 0xE0, 0x8A)
    private val heatMid = Color.rgb(0xFF, 0x6A, 0x3A)

    private fun smooth(a: Double, b: Double, x: Double): Double {
        val t = ((x - a) / (b - a)).coerceIn(0.0, 1.0)
        return t * t * (3 - 2 * t)
    }

    fun draw(c: Canvas, left: Float, top: Float, right: Float, bottom: Float, s: PrintLineScrubSceneState) {
        c.save()
        c.clipRect(left, top, right, bottom)
        if (s.glass) {
            // the capsule is a lens: magnify a little about its centre
            c.translate(s.lens[0], s.lens[1])
            c.scale(s.lensK, s.lensK)
            c.translate(-s.lens[0], -s.lens[1])
        }
        drawGround(c, left, top, right, bottom, s)
        drawFigure(c, left, top, right, bottom, s)
        drawSections(c, s)
        c.restore()
    }

    private fun drawGround(c: Canvas, l: Float, t: Float, r: Float, b: Float, s: PrintLineScrubSceneState) {
        val axis = s.ox
        fill.shader = LinearGradient(
            0f, 395f, 0f, 850f, intArrayOf(ground, groundHi), floatArrayOf(0f, 1f), Shader.TileMode.CLAMP,
        )
        c.drawRect(l, t, r, b, fill)
        fill.shader = null
        fill.color = ground
        c.drawRect(l, t, r, 395f, fill)
        // the grid: horizontals spread toward the viewer, verticals fan out from a point far above
        line.strokeWidth = 1f
        for (n in 0 until 28) {
            val hz = n + 0.5
            val y = (120 + hz * 14 / (1 - hz * 0.035)).toFloat()
            if (y > b) break
            line.color = withAlpha(grid, 0.55 * smooth(40.0, 260.0, y.toDouble()))
            c.drawLine(l, y, r, y, line)
        }
        for (n in -9..8) {
            val ang = (n + 0.5) / 7
            line.color = withAlpha(grid, 0.4)
            c.drawLine((axis + ang * (120 + 1600)).toFloat(), 120f, (axis + ang * (b + 1600)).toFloat(), b, line)
        }
        // the rose bounce on the floor round the base, and the baked contact shadow under it
        ellipse(c, axis, s.oy + 22f, s.u * 0.62f, s.u * 0.2f, withAlpha(rose, 0.42 + 0.5 * s.tint), 1.2f)
        ellipse(c, axis + 6f, s.oy + 36f, s.u * 0.37f, s.u * 0.1f, Color.argb(77, 32, 20, 18), 1.2f)
    }

    private fun ellipse(c: Canvas, cx: Float, cy: Float, rx: Float, ry: Float, color: Int, reach: Float) {
        c.save()
        c.translate(cx, cy)
        c.scale(1f, ry / rx)
        fill.shader = RadialGradient(0f, 0f, rx * reach, color, Color.TRANSPARENT, Shader.TileMode.CLAMP)
        c.drawCircle(0f, 0f, rx * reach, fill)
        c.restore()
        fill.shader = null
    }

    private fun withAlpha(color: Int, a: Double) = Color.argb((a.coerceIn(0.0, 1.0) * 255).toInt(), Color.red(color), Color.green(color), Color.blue(color))

    private fun drawFigure(c: Canvas, l: Float, t: Float, r: Float, b: Float, s: PrintLineScrubSceneState) {
        val cutY = s.oy - s.u * s.camCos * s.h
        dst.set(s.figure[0], s.figure[1], s.figure[0] + s.figure[2], s.figure[1] + s.figure[3])
        // above the cut: the unprinted mesh
        c.save()
        c.clipRect(l, t, r, min(cutY, b))
        bitmapPaint.alpha = (255 * 0.72).toInt()
        c.drawBitmap(ghost, src, dst, bitmapPaint)
        c.restore()
        // below the cut: the matte sculpt, with the hot band on the fresh layers
        c.save()
        c.clipRect(l, max(cutY, t), r, b)
        bitmapPaint.alpha = 255
        val layer = c.saveLayer(l, max(cutY, t), r, b, null)
        c.drawBitmap(solid, src, dst, bitmapPaint)
        atop.shader = LinearGradient(
            0f, cutY, 0f, cutY + max(s.band, 1f),
            intArrayOf(withAlpha(heatHot, 0.3 + 0.45 * s.heat), withAlpha(heatMid, (0.3 + 0.45 * s.heat) * 0.4), Color.TRANSPARENT),
            floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP,
        )
        c.drawRect(l, cutY, r, cutY + max(s.band, 1f), atop)
        c.restoreToCount(layer)
        c.restore()
    }

    private fun drawSections(c: Canvas, s: PrintLineScrubSceneState) {
        val cutY = s.oy - s.u * s.camCos * s.h
        val row = (cutY - s.figure[1]) / s.figure[3]
        if (row <= 0 || row >= 1) return
        val unit = s.u * s.camCos / 500
        val scale = cos(s.yaw)
        for (i in 0 until 6) {
            val a = s.spans[i * 2]
            val b = s.spans[i * 2 + 1]
            val rx = (b - a) * 0.5f * s.figure[2] * scale
            if (rx < 1f) continue
            val cx = s.figure[0] + (0.5f + ((a + b) * 0.5f - 0.5f) * scale) * s.figure[2]
            val ry = min(12f * unit, rx * 0.24f).coerceAtLeast(0.7f)
            oval.set(cx - rx, cutY - ry, cx + rx, cutY + ry)
            // the cut face: dark infill, a lit skin ring, a hot wall
            fill.color = withAlpha(filShadow, 0.85)
            c.drawOval(oval, fill)
            line.strokeWidth = max(ry * 0.5f, 1f)
            line.color = withAlpha(heatMid, 0.72 + 0.25 * s.heat)
            c.drawOval(oval, line)
            line.strokeWidth = max(ry * 0.22f, 0.8f)
            line.color = withAlpha(heatHot, 0.8)
            c.drawOval(oval, line)
            val inner = RectF(oval.left + ry * 0.6f, oval.top + ry * 0.45f, oval.right - ry * 0.6f, oval.bottom - ry * 0.45f)
            line.strokeWidth = max(ry * 0.18f, 0.7f)
            line.color = withAlpha(filLight, 0.7)
            if (inner.width() > 2f) c.drawOval(inner, line)
            // the bead that runs round the wall while held
            val bx = cx + rx * cos(s.bead)
            val by = cutY + ry * sin(s.bead)
            fill.shader = RadialGradient(bx, by, 6f * unit.coerceAtLeast(0.6f), Color.argb(204, 255, 220, 140), Color.TRANSPARENT, Shader.TileMode.CLAMP)
            c.drawCircle(bx, by, 6f * unit.coerceAtLeast(0.6f), fill)
            fill.shader = null
        }
    }
}

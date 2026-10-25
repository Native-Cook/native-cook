package com.ngocdevv.nativecook.blocks.charts.raingaugechart

import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Picture
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.os.Build
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

// The scene in android.graphics, a line-by-line port of rainGaugeArt.ts (Skia). Everything is in
// dp: the caller scales the canvas by the density first.

private fun argb(r: Int, g: Int, b: Int, a: Float): Int = (((a.coerceIn(0f, 1f) * 255f + 0.5f).toInt()) shl 24) or (r shl 16) or (g shl 8) or b
private fun hex(c: Long): Int = c.toInt()

// Skia blurs take sigma; Android's BlurMaskFilter takes a radius.
private fun blur(sigma: Float) = BlurMaskFilter(max(0.01f, (sigma - 0.5f) / 0.57735f), BlurMaskFilter.Blur.NORMAL)

private fun tubePath(out: Path, hw: Float, top: Float, closed: Boolean): Path {
    val k = 0.5523f
    out.reset()
    out.moveTo(-hw, top)
    out.lineTo(-hw, -hw)
    out.cubicTo(-hw, -hw + hw * k, -hw * k, 0f, 0f, 0f)
    out.cubicTo(hw * k, 0f, hw, -hw + hw * k, hw, -hw)
    out.lineTo(hw, top)
    if (closed) out.close()
    return out
}

private fun woodBoard(canvas: Canvas, x: Float, y: Float, w: Float, h: Float, seed: Double) {
    val r = RectF(x, y, x + w, y + h)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        val shadow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = argb(6, 12, 12, 0.45f)
            maskFilter = blur(5f)
        }
        canvas.drawRoundRect(RectF(x + 2, y + 5, x + 2 + w, y + 5 + h), 3f, 3f, shadow)
    }
    val body = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = LinearGradient(x, y, x, y + h, intArrayOf(hex(0xFFB07B4C), hex(0xFF8E5D36), hex(0xFF6A4226)), floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)
    }
    canvas.drawRoundRect(r, 3f, 3f, body)
    canvas.save()
    canvas.clipPath(Path().apply { addRoundRect(r, 3f, 3f, Path.Direction.CW) })
    val grain = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    for (i in 0 until 9) {
        val gy = y + 2 + (h - 4) * rainHash(seed + i * 1.7)
        val b = Path()
        b.moveTo(x, gy)
        val n = 6
        for (j in 1..n) {
            val px = x + w * j / n
            b.quadTo(px - w / n / 2, gy + (rainHash(seed + i * 3.1 + j) - 0.5f) * 3f, px, gy + (rainHash(seed + i * 5.3 + j) - 0.5f) * 1.6f)
        }
        grain.strokeWidth = 0.5f + rainHash(seed + i) * 0.9f
        grain.color = argb(58, 32, 16, 0.18f + 0.22f * rainHash(seed + i * 9.1))
        canvas.drawPath(b, grain)
    }
    // A knot.
    val knot = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 0.8f
        color = argb(60, 34, 18, 0.4f)
    }
    val kx = x + w * (0.2f + 0.6f * rainHash(seed + 40))
    canvas.drawOval(RectF(kx, y + h * 0.3f, kx + 7f, y + h * 0.3f + h * 0.36f), knot)
    canvas.restore()
    // Edges: a lit top edge and a dark bottom edge.
    val edge = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeWidth = 1f }
    edge.color = argb(255, 222, 176, 0.42f)
    canvas.drawLine(x + 3, y + 0.6f, x + w - 3, y + 0.6f, edge)
    edge.color = argb(40, 22, 10, 0.55f)
    canvas.drawLine(x + 3, y + h - 0.6f, x + w - 3, y + h - 0.6f, edge)
}

/** The rack's posts and boards, drawn over the tubes (the tubes stand in holes through them). Recorded once. */
internal fun rainRecordRackFront(g: RainGeo, width: Float, height: Float): Picture {
    val picture = Picture()
    val canvas = picture.beginRecording(max(1, width.toInt()), max(1, height.toInt()))
    val postW = 10f
    woodBoard(canvas, g.left - 2, g.board - 4, postW, g.ground - g.board + 4, 3.0)
    woodBoard(canvas, g.right - postW + 2, g.board - 4, postW, g.ground - g.board + 4, 7.0)
    woodBoard(canvas, g.left, g.board, g.right - g.left, 18f, 11.0)
    woodBoard(canvas, g.left, g.y1 - 4, g.right - g.left, 16f, 23.0)
    // Holes: a dark lip where each tube passes through the top board.
    val hole = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = argb(30, 16, 8, 0.75f) }
    val lip = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 0.8f
        color = argb(255, 220, 170, 0.3f)
    }
    for (i in 0 until TUBES) {
        canvas.drawOval(RectF(g.cx[i] - g.hw - 2.5f, g.board - 2f, g.cx[i] + g.hw + 2.5f, g.board + 2.5f), hole)
        canvas.drawOval(RectF(g.cx[i] - g.hw - 2.5f, g.board - 1.6f, g.cx[i] + g.hw + 2.5f, g.board + 2.9f), lip)
    }
    picture.endRecording()
    return picture
}

/** The garden bed under the rack, a far hedge and a vignette (the grain is drawn by the caller). Recorded once. */
internal fun rainRecordBackdrop(g: RainGeo, width: Float, height: Float): Picture {
    val picture = Picture()
    val canvas = picture.beginRecording(max(1, width.toInt()), max(1, height.toInt()))
    val hedge = Path()
    hedge.moveTo(0f, g.ground - 40)
    for (i in 0..12) {
        val x = width * i / 12f
        hedge.quadTo(x - width / 24f, g.ground - 64 - 22 * rainHash(i * 2.3), x, g.ground - 44 - 14 * rainHash(i * 4.1))
    }
    hedge.lineTo(width, height)
    hedge.lineTo(0f, height)
    hedge.close()
    val hp = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = LinearGradient(0f, g.ground - 90, 0f, g.ground, intArrayOf(argb(28, 52, 46, 0f), argb(24, 46, 40, 0.7f), hex(0xFF172B26)), floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)
    }
    canvas.drawPath(hedge, hp)
    val soil = Paint().apply {
        shader = LinearGradient(0f, g.ground, 0f, height, intArrayOf(hex(0xFF2B231C), hex(0xFF211A15), hex(0xFF17120F)), floatArrayOf(0f, 0.4f, 1f), Shader.TileMode.CLAMP)
    }
    canvas.drawRect(0f, g.ground, width, height, soil)
    val vignette = Paint().apply {
        shader = RadialGradient(width / 2, height * 0.45f, max(width, height) * 0.7f, intArrayOf(argb(0, 0, 0, 0f), argb(2, 8, 10, 0.2f), argb(2, 8, 10, 0.55f)), floatArrayOf(0f, 0.62f, 1f), Shader.TileMode.CLAMP)
    }
    canvas.drawRect(0f, 0f, width, height, vignette)
    picture.endRecording()
    return picture
}

/**
 * The moving layer's paints, paths and the gradients that depend only on the tube's width, made
 * once per geometry. Every paint that takes a shader is kept opaque (its alpha set explicitly).
 */
internal class RainPainter(private val g: RainGeo, private val front: Picture) {
    private val hw = g.hw
    private val top = g.y0 - g.y1
    private val outer = tubePath(Path(), hw, top, true)
    private val inner = tubePath(Path(), hw - 2, top, true)
    private val openOuter = tubePath(Path(), hw, top, false)
    private val surface = Path()
    private val water = Path()
    private val ticks = Path().apply {
        for (mm in 1..CAPACITY.toInt()) {
            val ty = g.levelY(mm.toFloat()) - g.y1
            val len = if (mm % 5 == 0) 7f else 3.5f
            moveTo(hw - 1.5f - len, ty)
            lineTo(hw - 1.5f, ty)
        }
    }
    private val glass = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = LinearGradient(
            -hw, 0f, hw, 0f,
            intArrayOf(argb(214, 238, 246, 0.2f), argb(214, 238, 246, 0.05f), argb(214, 238, 246, 0.02f), argb(214, 238, 246, 0.08f), argb(214, 238, 246, 0.24f)),
            floatArrayOf(0f, 0.25f, 0.55f, 0.8f, 1f),
            Shader.TileMode.CLAMP,
        )
    }
    private val waterPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = LinearGradient(
            -hw, 0f, hw, 0f,
            intArrayOf(hex(0xFF1C5468), hex(0xFF3D8BA6), hex(0xFF86CBDF), hex(0xFF4C9DB7), hex(0xFF1A4C60)),
            floatArrayOf(0f, 0.22f, 0.38f, 0.72f, 1f),
            Shader.TileMode.CLAMP,
        )
        alpha = 235
    }
    private val depth = Paint()
    private val depthColors = intArrayOf(argb(200, 245, 255, 0.28f), argb(10, 40, 52, 0f), argb(6, 26, 36, 0.4f))
    private val depthStops = floatArrayOf(0f, 0.18f, 1f)
    private val bubble = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 0.7f
        color = argb(235, 252, 255, 0.45f)
    }
    private val meniscus = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.2f
        color = argb(228, 250, 255, 0.9f)
    }
    private val wall = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.4f
        color = argb(8, 26, 34, 0.22f)
    }
    private val stripe = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = LinearGradient(0f, top, 0f, -hw, intArrayOf(argb(255, 255, 255, 0.62f), argb(255, 255, 255, 0.22f), argb(255, 255, 255, 0.08f)), floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)
    }
    private val stripeRect = RectF(-hw + 3f, top + 6f, -hw + 5.4f, -hw - 4f + 6f)
    private val edge = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1f
    }
    private val tickPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 0.8f
        strokeCap = Paint.Cap.ROUND
        color = argb(240, 250, 252, 0.55f)
    }
    private val rim = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.3f
        color = argb(236, 248, 252, 0.6f)
    }
    private val rimRect = RectF(-hw - 0.8f, top - 2.2f, hw + 0.8f, top + 2.2f)
    private val liftShadow = Paint(Paint.ANTI_ALIAS_FLAG)
    private val buckets = Array(3) { Path() }
    private val splash = Path()
    private val ripple = Path()
    private val rainPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val splashPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val oval = RectF()
    private val landing = FloatArray(2)

    /** One glass gauge with its water, in local coordinates (bottom centre = 0, 0). */
    private fun drawTube(canvas: Canvas, i: Int, level: Float, slosh: Float) {
        canvas.drawPath(outer, glass)

        // Water, clipped to the bore; the surface climbs the walls (meniscus) and tilts with the slosh.
        val wy = g.levelY(level) - g.y1
        if (level > 0.02f) {
            canvas.save()
            canvas.clipPath(inner)
            val wl = wy - slosh - 2.4f
            val wr = wy + slosh - 2.4f
            surface.reset()
            surface.moveTo(-hw, wl)
            surface.quadTo(0f, wy + 2.4f, hw, wr)
            water.reset()
            water.moveTo(-hw, wl)
            water.quadTo(0f, wy + 2.4f, hw, wr)
            water.lineTo(hw, 2f)
            water.lineTo(-hw, 2f)
            water.close()
            canvas.drawPath(water, waterPaint)
            depth.shader = LinearGradient(0f, wy, 0f, 0f, depthColors, depthStops, Shader.TileMode.CLAMP)
            canvas.drawPath(water, depth)
            // Two small bubbles stuck to the glass.
            for (k in 0 until 2) {
                val by = -6f - rainHash((i * 7 + k).toDouble()) * 40f
                if (by > wy + 4f) canvas.drawCircle((rainHash((i * 3 + k * 5).toDouble()) - 0.5f) * hw, by, 0.9f + rainHash((i + k).toDouble()) * 0.7f, bubble)
            }
            canvas.drawPath(surface, meniscus)
            canvas.restore()
        }

        // Front glass: the bore's thickness, a bright left stripe, a thin right one, ticks and the rim.
        canvas.drawPath(inner, wall)
        canvas.drawRoundRect(stripeRect, 1.2f, 1.2f, stripe)
        edge.color = argb(226, 244, 250, 0.42f)
        canvas.drawPath(openOuter, edge)
        edge.color = argb(255, 255, 255, 0.18f)
        canvas.drawLine(hw - 3f, top + 10f, hw - 3f, -hw - 2f, edge)
        canvas.drawPath(ticks, tickPaint)
        canvas.drawOval(rimRect, rim)
    }

    /** Where a drop falling at x stops: the water in a tube, the rack's top board, or the bed. */
    private fun land(x: Float, p: RainPose): FloatArray {
        for (i in 0 until TUBES) {
            if (abs(x - g.cx[i]) < g.hw - 2.5f) {
                val up = if (i == p.liftIdx) p.lift * LIFT_PT else 0f
                landing[0] = g.levelY(p.levels[i]) - up
                landing[1] = 1f
                return landing
            }
        }
        landing[0] = if (x > g.left && x < g.right) g.board else g.ground + 4f
        landing[1] = 0f
        return landing
    }

    private fun drawRain(canvas: Canvas, p: RainPose) {
        if (p.rain < 0.01f) return
        val i0 = max(0, min(TUBES - 1, floor(p.cursor).toInt()))
        val i1 = min(TUBES - 1, i0 + 1)
        val xc = g.cx[i0] + (g.cx[i1] - g.cx[i0]) * (p.cursor - i0)
        val spread = g.pitch * 1.25f
        for (b in buckets) b.reset()
        splash.reset()
        ripple.reset()
        for (i in 0 until DROPS) {
            val x = g.ox + 10f + rainHash(i * 3.1) * (g.w - 20f)
            val w = exp(-((x - xc) / spread).pow(2))
            val alpha = min(1f, max(0f, (p.rain * (0.1f + 0.9f * w) - rainHash(i * 5.3) * 0.85f) * 5f))
            if (alpha < 0.03f) continue
            val k = 13 + floor(rainHash(i * 7.7) * 4f).toInt()
            val cyc = rainHash(i * 1.9) + k * p.rainT / LOOP
            val f = cyc - floor(cyc)
            val l = land(x, p)
            val landY = l[0]
            val inWater = l[1] == 1f
            val start = g.oy - 40f
            val y = start + f * (landY - start)
            val len = 12f + 9f * rainHash(i * 2.7)
            val b = buckets[if (alpha > 0.66f) 2 else if (alpha > 0.33f) 1 else 0]
            b.moveTo(x + len * 0.12f, max(start, y - len))
            b.lineTo(x, y)
            // The drop that landed a moment ago (this lane's previous fall) splashes or ripples.
            val e = f * LOOP / k
            if (e < 0.16f) {
                val u = e / 0.16f
                if (inWater) {
                    oval.set(x - 2f - 4f * u, landY - 0.8f - 0.6f * u, x - 2f - 4f * u + 4f + 8f * u, landY - 0.8f - 0.6f * u + 1.6f + 1.2f * u)
                    ripple.addOval(oval, Path.Direction.CW)
                } else {
                    val hgt = 7f * 4f * u * (1f - u)
                    splash.addCircle(x - 2f - 6f * u, landY - hgt, 0.9f * (1f - 0.5f * u), Path.Direction.CW)
                    splash.addCircle(x + 2f + 5f * u, landY - hgt * 0.8f, 0.8f * (1f - 0.5f * u), Path.Direction.CW)
                    oval.set(x - 1.5f - 3f * u, landY - 0.8f, x - 1.5f - 3f * u + 3f + 6f * u, landY + 0.8f)
                    splash.addOval(oval, Path.Direction.CW)
                }
            }
        }
        rainPaint.strokeWidth = 1.25f
        for (b in 0 until 3) {
            rainPaint.color = argb(206, 232, 244, (0.3f + 0.24f * b) * p.rain)
            canvas.drawPath(buckets[b], rainPaint)
        }
        splashPaint.color = argb(214, 238, 248, 0.55f * p.rain)
        canvas.drawPath(splash, splashPaint)
        rainPaint.strokeWidth = 0.8f
        rainPaint.color = argb(220, 248, 255, 0.6f * p.rain)
        canvas.drawPath(ripple, rainPaint)
    }

    /** The moving layer: tubes, the rack (a recorded picture), the lifted tube over it, the rain. */
    fun draw(canvas: Canvas, p: RainPose) {
        for (i in 0 until TUBES) {
            if (i == p.liftIdx && p.lift > 0.001f) continue
            canvas.save()
            canvas.translate(g.cx[i], g.y1)
            drawTube(canvas, i, p.levels[i], p.slosh[i])
            canvas.restore()
        }
        canvas.drawPicture(front)
        if (p.liftIdx >= 0 && p.lift > 0.001f) {
            val i = p.liftIdx
            val sc = 1f + LIFT_SCALE * p.lift
            // Its shadow on the base board grows softer as it rises.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                liftShadow.color = argb(10, 6, 4, 0.35f * p.lift)
                liftShadow.maskFilter = blur(3f + 5f * p.lift)
                oval.set(g.cx[i] - g.hw - 4f, g.y1 - 6f, g.cx[i] + g.hw + 4f, g.y1 - 1f)
                canvas.drawOval(oval, liftShadow)
            }
            canvas.save()
            canvas.translate(g.cx[i], g.y1 - LIFT_PT * p.lift)
            canvas.scale(sc, sc)
            drawTube(canvas, i, p.levels[i], p.slosh[i])
            canvas.restore()
        }
        drawRain(canvas, p)
    }
}

package com.ngocdevv.nativecook.blocks.text.embroideryheadline

import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import com.ngocdevv.nativecook.shared.sigmaToRadius
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/*
 Embroidery Headline — what is drawn per frame: the laid satin threads (a pucker shadow, the threads'
 own shadow, two alternating thread shades, a sheen), the French knot and the needle with its thread.
 Everything is in pt (dp); the caller scales the canvas by the density.
*/

internal class EmbGeo(val w: Float, val h: Float, val cx: Float, val cy: Float, val hoopR: Float, val fabricR: Float) {
    fun same(o: EmbGeo?) = o != null && o.w == w && o.h == h && o.cx == cx && o.cy == cy && o.hoopR == hoopR
}

internal fun embMakeGeo(w: Float, h: Float, hx: Float, hy: Float, hw: Float, hh: Float): EmbGeo {
    val hoopR = min(min(hw - 8, hh - 12), 400f) / 2
    return EmbGeo(w, h, hx + hw / 2, hy + hh / 2, hoopR, hoopR - 15)
}

/** One letter in satin stitch: segments (x1, y1, x2, y2 flat, pt), thread colour, sheen 0..1. */
internal class EmbGlyph(val segs: FloatArray, val count: Int, val rgb: FloatArray, val sheen: Float)

internal class EmbSatin(val glyphs: List<EmbGlyph>, val knot: FloatArray, val knotRGB: FloatArray) {
    val counts = IntArray(glyphs.size) { glyphs[it].count }
    val total = counts.sum()
}

internal val EmbMadder = floatArrayOf(0.64f, 0.22f, 0.17f)
internal val EmbIndigo = floatArrayOf(0.17f, 0.3f, 0.42f)
/** Thread angle per letter (degrees from horizontal), set across each letter's main strokes. */
internal val EmbAngles = mapOf('S' to 58f, 'l' to 12f, 'o' to 64f, 'w' to 72f, 'm' to 16f, 'a' to 56f, 'd' to 20f, 'e' to 62f)
internal const val EMB_LIGHT = 38f
internal const val EMB_SPACING = 1.15f

private fun col(c: FloatArray, a: Float) = android.graphics.Color.argb(a.coerceIn(0f, 1f), c[0].coerceIn(0f, 1f), c[1].coerceIn(0f, 1f), c[2].coerceIn(0f, 1f))

private fun colf(r: Float, g: Float, b: Float, a: Float) = android.graphics.Color.argb(a.coerceIn(0f, 1f), r, g, b)

private fun tone(c: FloatArray, k: Float): FloatArray =
    if (k < 0) floatArrayOf(c[0] * (1 + k), c[1] * (1 + k), c[2] * (1 + k))
    else floatArrayOf(c[0] + (1 - c[0]) * k, c[1] + (0.97f - c[1]) * k, c[2] + (0.92f - c[2]) * k)

/** Where the needle is on glyph g at `present`: x, y, thread-from x, y (written into [out]). */
internal fun embSatinFront(g: EmbGlyph, present: Float, out: FloatArray) {
    if (g.count == 0) { out.fill(0f, 0, 4); return }
    val k = min(g.count - 1, max(0, floor(present).toInt()))
    val f = min(1f, max(0f, present - k))
    val x1 = g.segs[k * 4]; val y1 = g.segs[k * 4 + 1]
    val x2 = g.segs[k * 4 + 2]; val y2 = g.segs[k * 4 + 3]
    val fromEnd = k % 2 == 1
    val ax = if (fromEnd) x2 else x1
    val ay = if (fromEnd) y2 else y1
    val bx = if (fromEnd) x1 else x2
    val by = if (fromEnd) y1 else y2
    // The needle rides near the middle of the thread being laid, leaning toward the end it heads for.
    val mx = (x1 + x2) / 2
    val my = (y1 + y2) / 2
    val lean = 0.35f * (f - 0.5f) * 2
    out[0] = mx + (bx - mx) * lean * 0.5f
    out[1] = my + (by - my) * lean * 0.5f
    out[2] = ax
    out[3] = ay
}

/** Paints and paths reused every frame. */
internal class EmbPainter(satin: EmbSatin) {
    private val all = Path()
    private val evens = List(satin.glyphs.size) { Path() }
    private val odds = List(satin.glyphs.size) { Path() }
    private val sheens = List(satin.glyphs.size) { Path() }
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    private val pucker = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND
        maskFilter = BlurMaskFilter(sigmaToRadius(3.2f), BlurMaskFilter.Blur.NORMAL)
    }
    private val shadow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND
        maskFilter = BlurMaskFilter(sigmaToRadius(0.8f), BlurMaskFilter.Blur.NORMAL)
    }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val blurFill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val threadShadow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeWidth = 2.4f
        maskFilter = BlurMaskFilter(sigmaToRadius(1.4f), BlurMaskFilter.Blur.NORMAL)
    }
    private val tmp = Path()
    private val rect = RectF()
    private val needlePath = Path().apply {
        val l = NEEDLE_L
        moveTo(0f, 0f)
        lineTo(l - 8, -1.8f)
        quadTo(l + 1.5f, -2.1f, l + 1.5f, 0f)
        quadTo(l + 1.5f, 2.1f, l - 8, 1.8f)
        close()
    }
    private val steel = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = LinearGradient(
            0f, -2.1f, 0f, 2.1f,
            intArrayOf(colf(0.97f, 0.97f, 0.96f, 1f), colf(0.64f, 0.67f, 0.7f, 1f), colf(0.38f, 0.4f, 0.43f, 1f), colf(0.8f, 0.82f, 0.83f, 1f)),
            floatArrayOf(0f, 0.35f, 0.7f, 1f), Shader.TileMode.CLAMP,
        )
    }
    private var lastShadowSigma = -1f

    private fun layGlyph(g: EmbGlyph, present: Float, even: Path, odd: Path, sheen: Path) {
        val n = min(g.count, ceil(present).toInt())
        for (k in 0 until n) {
            val f = min(1f, present - k)
            val x1 = g.segs[k * 4]; val y1 = g.segs[k * 4 + 1]
            // Satin goes back and forth: odd threads are laid from the far edge.
            val fromEnd = k % 2 == 1
            val ax = if (fromEnd) g.segs[k * 4 + 2] else x1
            val ay = if (fromEnd) g.segs[k * 4 + 3] else y1
            val bx = if (fromEnd) x1 else g.segs[k * 4 + 2]
            val by = if (fromEnd) y1 else g.segs[k * 4 + 3]
            val ex = ax + (bx - ax) * f
            val ey = ay + (by - ay) * f
            val target = if (k % 2 == 0) even else odd
            target.moveTo(ax, ay); target.lineTo(ex, ey)
            all.moveTo(ax, ay); all.lineTo(ex, ey)
            if (f > 0.3f) {
                sheen.moveTo(ax + (ex - ax) * 0.15f, ay + (ey - ay) * 0.15f)
                sheen.lineTo(ax + (ex - ax) * 0.85f, ay + (ey - ay) * 0.85f)
            }
        }
    }

    fun drawSatin(c: Canvas, s: EmbSatin, present: FloatArray) {
        all.rewind()
        for (i in s.glyphs.indices) {
            evens[i].rewind(); odds[i].rewind(); sheens[i].rewind()
            layGlyph(s.glyphs[i], present[i], evens[i], odds[i], sheens[i])
        }
        val w = EMB_SPACING * 1.25f
        // The cloth puckers a little around the stitching, then the threads' own shadow.
        pucker.strokeWidth = w + 5
        pucker.color = colf(0.3f, 0.22f, 0.13f, 0.12f)
        c.save(); c.translate(0.5f, 1.6f); c.drawPath(all, pucker); c.restore()
        shadow.strokeWidth = w + 0.6f
        shadow.color = colf(0.18f, 0.1f, 0.06f, 0.4f)
        c.save(); c.translate(0.45f, 0.9f); c.drawPath(all, shadow); c.restore()
        for (i in s.glyphs.indices) {
            val g = s.glyphs[i]
            stroke.strokeWidth = w
            stroke.color = col(tone(g.rgb, -0.24f), 1f)
            c.drawPath(evens[i], stroke)
            stroke.color = col(tone(g.rgb, -0.04f), 1f)
            c.drawPath(odds[i], stroke)
            stroke.strokeWidth = w * 0.5f
            stroke.color = col(tone(g.rgb, 0.5f + 0.2f * g.sheen), 0.28f + 0.55f * g.sheen)
            c.save(); c.translate(-0.25f, -0.3f); c.drawPath(sheens[i], stroke); c.restore()
        }
    }

    /** The French knot: a tight bead of thread wraps. */
    fun drawKnot(c: Canvas, s: EmbSatin, scale: Float, alpha: Float) {
        if (alpha <= 0.002f) return
        val x = s.knot[0]; val y = s.knot[1]
        val r = s.knot[2] * scale
        val sigma = 1.6f + (scale - 1) * 4
        if (sigma != lastShadowSigma) {
            blurFill.maskFilter = BlurMaskFilter(sigmaToRadius(max(0.6f, sigma)), BlurMaskFilter.Blur.NORMAL)
            lastShadowSigma = sigma
        }
        blurFill.color = colf(0.2f, 0.12f, 0.06f, 0.38f * alpha)
        c.drawCircle(x + 0.8f, y + 1.6f, r * 1.02f, blurFill)
        fill.shader = RadialGradient(
            x - r * 0.35f, y - r * 0.4f, max(0.01f, r * 1.5f),
            intArrayOf(col(tone(s.knotRGB, 0.35f), alpha), col(s.knotRGB, alpha), col(tone(s.knotRGB, -0.45f), alpha)),
            floatArrayOf(0f, 0.45f, 1f), Shader.TileMode.CLAMP,
        )
        c.drawCircle(x, y, r, fill)
        fill.shader = null
        stroke.strokeWidth = r * 0.22f
        rect.set(x - r * 0.72f, y - r * 0.72f, x + r * 0.72f, y + r * 0.72f)
        for (k in 0 until 5) {
            stroke.color = col(tone(s.knotRGB, if (k % 2 == 0) -0.3f else 0.3f), 0.75f * alpha)
            c.drawArc(rect, k * 72f + 20, 110f, false, stroke)
        }
        stroke.strokeWidth = r * 0.16f
        stroke.color = colf(1f, 0.96f, 0.9f, 0.45f * alpha)
        rect.set(x - r * 0.55f, y - r * 0.6f, x + r * 0.55f, y + r * 0.5f)
        c.drawArc(rect, 200f, 70f, false, stroke)
    }

    /** Needle and thread (thread from the eye, sagging, to where it last went into the cloth). */
    fun drawNeedle(c: Canvas, x: Float, y: Float, angle: Float, lift: Float, fromX: Float, fromY: Float, rgb: FloatArray) {
        val l = NEEDLE_L
        val ca = cos(angle); val sa = sin(angle)
        val eyeX = x + ca * (l - 5)
        val eyeY = y + sa * (l - 5)
        tmp.rewind()
        tmp.moveTo(fromX, fromY)
        tmp.quadTo((eyeX + fromX) / 2, max(eyeY, fromY) + 14 + hypot(eyeX - fromX, eyeY - fromY) * 0.2f, eyeX, eyeY)
        threadShadow.color = colf(0.12f, 0.08f, 0.05f, 0.22f)
        c.save(); c.translate(1f, 3f); c.drawPath(tmp, threadShadow); c.restore()
        stroke.strokeWidth = 1.9f
        stroke.color = col(rgb, 1f)
        c.drawPath(tmp, stroke)
        stroke.strokeWidth = 0.7f
        stroke.color = col(tone(rgb, 0.45f), 0.6f)
        c.drawPath(tmp, stroke)

        val deg = angle * 180f / PI.toFloat()
        // The needle's shadow spreads as it lifts; the blur is quantised so it is not rebuilt every frame.
        val sigma = (1.2f + lift * 2.5f).let { (it * 4).toInt() / 4f }
        if (sigma != needleSigma) {
            needleShadow.maskFilter = BlurMaskFilter(sigmaToRadius(sigma), BlurMaskFilter.Blur.NORMAL)
            needleSigma = sigma
        }
        needleShadow.color = colf(0.14f, 0.1f, 0.06f, 0.32f)
        c.save()
        c.translate(x + 1.5f + lift * 6, y + 2.5f + lift * 8)
        c.rotate(deg)
        c.drawPath(needlePath, needleShadow)
        c.restore()
        c.save()
        c.translate(x, y)
        c.rotate(deg)
        c.drawPath(needlePath, steel)
        fill.color = colf(0.28f, 0.26f, 0.25f, 1f)
        rect.set(l - 8, -0.7f, l - 1.5f, 0.7f)
        c.drawOval(rect, fill)
        c.restore()
    }

    private val needleShadow = Paint(Paint.ANTI_ALIAS_FLAG)
    private var needleSigma = -1f

    companion object {
        const val NEEDLE_L = 62f
    }
}

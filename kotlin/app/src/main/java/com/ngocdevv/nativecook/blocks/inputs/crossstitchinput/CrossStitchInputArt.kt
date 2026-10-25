package com.ngocdevv.nativecook.blocks.inputs.crossstitchinput

import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
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
 Cross Stitch Input — what is drawn. The cloth, the hoop and the printed border are baked once; every
 frame draws only the name's stitches, the needle and its thread. Units are pt (dp).
*/

/** Cells across the fabric. */
internal const val CELLS = 30

internal class StitchGeo(val w: Float, val h: Float, val heroX: Float, val heroY: Float, val heroW: Float, val heroH: Float) {
    val hoopR = min(min(heroW - 36, heroH - 20), 380f) / 2
    val fabricR = hoopR - 15
    val cell = 2 * fabricR / CELLS
    val cx = heroX + heroW / 2
    val cy = heroY + heroH / 2
    val parkX = cx + fabricR * 0.55f
    val parkY = cy + fabricR * 0.36f

    fun same(o: StitchGeo?) = o != null && o.w == w && o.h == h && o.heroX == heroX && o.heroY == heroY && o.heroW == heroW && o.heroH == heroH
}

internal class StitchThread(val name: String, val rgb: FloatArray, val hex: Long)

internal val Threads = listOf(
    StitchThread("Madder", floatArrayOf(0.66f, 0.23f, 0.2f), 0xA83B33),
    StitchThread("Indigo", floatArrayOf(0.2f, 0.28f, 0.5f), 0x33477F),
    StitchThread("Marigold", floatArrayOf(0.83f, 0.55f, 0.15f), 0xD48C26),
    StitchThread("Moss", floatArrayOf(0.36f, 0.48f, 0.28f), 0x5C7A47),
    StitchThread("Plum", floatArrayOf(0.46f, 0.26f, 0.43f), 0x76426E),
)
/** The printed border's thread: a soft sage. */
internal val BorderRGB = floatArrayOf(0.5f, 0.6f, 0.47f)

private fun col(c: FloatArray, a: Float) = android.graphics.Color.argb(a.coerceIn(0f, 1f), c[0].coerceIn(0f, 1f), c[1].coerceIn(0f, 1f), c[2].coerceIn(0f, 1f))

/** k < 0 darkens towards a warm brown-black, k > 0 lightens towards warm cream. */
private fun shade(c: FloatArray, k: Float): FloatArray =
    if (k < 0) floatArrayOf(c[0] * (1 + k) + 0.08f * -k, c[1] * (1 + k) + 0.05f * -k, c[2] * (1 + k) + 0.04f * -k)
    else floatArrayOf(c[0] + (0.99f - c[0]) * k, c[1] + (0.96f - c[1]) * k, c[2] + (0.9f - c[2]) * k)

internal fun csMix(a: FloatArray, b: FloatArray, t: Float, out: FloatArray = FloatArray(3)): FloatArray {
    for (i in 0 until 3) out[i] = a[i] + (b[i] - a[i]) * t
    return out
}

/** Where the needle's point is while a stitch at (x, y) is at fraction f (pt). */
internal fun csNeedlePoint(x: Float, y: Float, h: Float, f: Float, out: FloatArray) {
    when {
        f < 0.42f -> { val a = f / 0.42f; out[0] = x - h + 2 * h * a; out[1] = y + h - 2 * h * a }
        f < 0.5f -> { val a = (f - 0.42f) / 0.08f; out[0] = x + h; out[1] = y - h + 2 * h * a }
        f < 0.92f -> { val a = (f - 0.5f) / 0.42f; out[0] = x + h - 2 * h * a; out[1] = y + h - 2 * h * a }
        else -> { out[0] = x - h; out[1] = y - h }
    }
}

/** Draws stitches and the needle; paints and paths are reused. */
internal class StitchPainter {
    private val path = Path()
    private val shadow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND
        maskFilter = BlurMaskFilter(sigmaToRadius(0.9f), BlurMaskFilter.Blur.NORMAL)
    }
    private val floss = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND }
    private val ply = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.BUTT }
    private val dashA = DashPathEffect(floatArrayOf(1.5f, 1.3f), 0f)
    private val dashB = DashPathEffect(floatArrayOf(1.5f, 1.3f), 1.4f)
    private val threadShadow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeWidth = 2.6f
        maskFilter = BlurMaskFilter(sigmaToRadius(1.4f), BlurMaskFilter.Blur.NORMAL)
    }
    private val thread = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    private val tp = Path()
    private val needleShadow = Paint(Paint.ANTI_ALIAS_FLAG)
    private var needleSigma = -1f
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
            intArrayOf(
                android.graphics.Color.argb(1f, 0.97f, 0.97f, 0.96f), android.graphics.Color.argb(1f, 0.62f, 0.65f, 0.68f),
                android.graphics.Color.argb(1f, 0.4f, 0.42f, 0.45f), android.graphics.Color.argb(1f, 0.8f, 0.82f, 0.83f),
            ),
            floatArrayOf(0f, 0.35f, 0.7f, 1f), Shader.TileMode.CLAMP,
        )
    }
    private val hole = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.argb(1f, 0.3f, 0.28f, 0.27f) }
    private val eye = RectF(NEEDLE_L - 8, -0.7f, NEEDLE_L - 1.5f, 0.7f)

    private fun addStitch(x: Float, y: Float, h: Float, f: Float) {
        // First leg bottom-left → top-right (f 0–0.42), second bottom-right → top-left (0.5–0.92).
        val a = min(1f, max(0f, f / 0.42f))
        if (a > 0) { path.moveTo(x - h, y + h); path.lineTo(x - h + 2 * h * a, y + h - 2 * h * a) }
        val c = min(1f, max(0f, (f - 0.5f) / 0.42f))
        if (c > 0) { path.moveTo(x + h, y + h); path.lineTo(x + h - 2 * h * c, y + h - 2 * h * c) }
    }

    /**
     * Draws a set of stitches (grid units around the fabric centre), the first `present` worked —
     * either one running count, or a count per glyph ([glyphPresent], with [layout]) shifted per glyph.
     */
    fun drawStitches(
        c: Canvas, g: StitchGeo, xs: FloatArray, ys: FloatArray, present: Float, rgb: FloatArray, alpha: Float,
        layout: StitchLayout? = null, glyphPresent: FloatArray? = null, shiftX: FloatArray? = null, shiftY: FloatArray? = null,
    ) {
        if (present <= 0 || alpha <= 0.002f) return
        path.rewind()
        val h = g.cell * 0.4f
        val n = min(xs.size, ceil(present).toInt())
        for (k in 0 until n) {
            val f = if (layout != null && glyphPresent != null) {
                val gi = layout.glyphOf[k]
                min(1f, max(0f, glyphPresent[gi] - (k - layout.first[gi])))
            } else min(1f, present - k)
            if (f <= 0) continue
            var x = xs[k]; var y = ys[k]
            if (layout != null && shiftX != null && shiftY != null) {
                x += shiftX[layout.glyphOf[k]]; y += shiftY[layout.glyphOf[k]]
            }
            addStitch(g.cx + x * g.cell, g.cy + y * g.cell, h, f)
        }
        val width = g.cell * 0.33f
        shadow.strokeWidth = width * 1.15f
        shadow.color = android.graphics.Color.argb(0.32f * alpha, 0.24f, 0.16f, 0.1f)
        c.save(); c.translate(0.45f, 0.85f); c.drawPath(path, shadow); c.restore()
        floss.strokeWidth = width
        floss.color = col(rgb, alpha)
        c.drawPath(path, floss)
        ply.strokeWidth = width * 0.34f
        ply.pathEffect = dashA
        ply.color = col(shade(rgb, 0.45f), 0.7f * alpha)
        c.save(); c.translate(-width * 0.16f, -width * 0.16f); c.drawPath(path, ply); c.restore()
        ply.pathEffect = dashB
        ply.color = col(shade(rgb, -0.35f), 0.55f * alpha)
        c.save(); c.translate(width * 0.16f, width * 0.16f); c.drawPath(path, ply); c.restore()
    }

    /** The needle (point at x, y, along `angle`, eye up-right) and its thread sagging to (fromX, fromY). */
    fun drawNeedle(c: Canvas, x: Float, y: Float, angle: Float, lift: Float, fromX: Float, fromY: Float, rgb: FloatArray, alpha: Float) {
        if (alpha <= 0.002f) return
        val l = NEEDLE_L
        val eyeX = x + cos(angle) * (l - 4)
        val eyeY = y + sin(angle) * (l - 4)
        tp.rewind()
        tp.moveTo(fromX, fromY)
        tp.quadTo((eyeX + fromX) / 2, max(eyeY, fromY) + 10 + hypot(eyeX - fromX, eyeY - fromY) * 0.18f, eyeX, eyeY)
        threadShadow.color = android.graphics.Color.argb(0.18f * alpha, 0.2f, 0.13f, 0.08f)
        c.save(); c.translate(1f, 3f); c.drawPath(tp, threadShadow); c.restore()
        thread.strokeWidth = 2.2f
        thread.color = col(rgb, alpha)
        c.drawPath(tp, thread)
        thread.strokeWidth = 0.8f
        thread.color = col(shade(rgb, 0.4f), 0.6f * alpha)
        c.save(); c.translate(-0.5f, -0.5f); c.drawPath(tp, thread); c.restore()

        val deg = angle * 180f / PI.toFloat()
        // Its shadow falls further away the higher it is lifted (blur quantised so it is not rebuilt every frame).
        val sigma = floor((1.2f + lift * 2) * 4) / 4
        if (sigma != needleSigma) {
            needleShadow.maskFilter = BlurMaskFilter(sigmaToRadius(sigma), BlurMaskFilter.Blur.NORMAL)
            needleSigma = sigma
        }
        needleShadow.color = android.graphics.Color.argb(0.3f * alpha, 0.22f, 0.15f, 0.08f)
        c.save(); c.translate(x + 1.5f + lift * 5, y + 2.5f + lift * 7); c.rotate(deg); c.drawPath(needlePath, needleShadow); c.restore()
        c.save()
        c.translate(x, y)
        c.rotate(deg)
        val a = (alpha * 255).toInt()
        steel.alpha = a
        c.drawPath(needlePath, steel)
        hole.alpha = a
        c.drawOval(eye, hole)
        c.restore()
    }

    companion object {
        const val NEEDLE_L = 58f
    }
}

/** The needle's point on stitch `present` of a layout and the hole its thread comes from: x, y, fromX, fromY. */
private fun workPoint(g: StitchGeo, l: StitchLayout, present: Float, out: FloatArray, tmp: FloatArray) {
    if (l.total == 0) { out[0] = g.parkX; out[1] = g.parkY; out[2] = g.parkX; out[3] = g.parkY; return }
    val h = g.cell * 0.4f
    val k = min(l.total - 1, max(0, floor(present).toInt()))
    val f = min(1f, max(0f, present - k))
    val x = g.cx + l.cx[k] * g.cell
    val y = g.cy + l.cy[k] * g.cell
    csNeedlePoint(x, y, h, f, tmp)
    out[0] = tmp[0]; out[1] = tmp[1]; out[2] = x - h; out[3] = y + h
}

/** The loop's scene: both layouts, the needle maths. */
internal class StitchLoop(val g: StitchGeo, val rest: StitchLayout, val second: StitchLayout) {
    private val w = FloatArray(5)
    private val a = FloatArray(4)
    private val b = FloatArray(4)
    private val tmp = FloatArray(2)
    private val rgb = FloatArray(3)

    /** The needle in the loop: x, y, angle, lift, fromX, fromY. */
    fun needle(t: Float, out: FloatArray) {
        csNeedleScript(t, w)
        workPoint(g, rest, min(rest.total - 0.001f, csRestPresent(t, rest.total)), a, tmp)
        workPoint(g, second, min(second.total - 0.001f, csSecondPresent(t, second.total)), b, tmp)
        // The knot: one small loop round the last hole.
        val kr = g.cell * 0.9f * sin(PI.toFloat() * w[3])
        val ka = w[3] * PI.toFloat() * 2
        val bx = b[0] + kr * cos(ka)
        val by = b[1] + kr * sin(ka)
        val work = w[1] + w[2]
        out[0] = g.parkX * w[0] + a[0] * w[1] + bx * w[2]
        out[1] = g.parkY * w[0] + a[1] * w[1] + by * w[2]
        // The thread comes out of the last stitch of whichever name is on the cloth.
        val restOn = csRestPresent(t, rest.total) > 0.5f
        val tail = if (restOn) a else b
        out[4] = if (work > 0.5f) (if (w[1] > w[2]) a[2] else b[2]) else tail[2]
        out[5] = if (work > 0.5f) (if (w[1] > w[2]) a[3] else b[3]) else tail[3]
        // A small bob per stitch while working, a lift while travelling.
        val bob = work * 0.25f * (1 + sin(t * 2 * PI.toFloat() * 9))
        out[2] = -0.95f + 0.06f * sin(t / LOOP * 2 * PI.toFloat() * 4) * work
        out[3] = w[4] + bob
    }

    private val n = FloatArray(6)

    fun draw(c: Canvas, p: StitchPainter, t: Float, alpha: Float, withNeedle: Boolean) {
        val restRGB = Threads[REST_THREAD].rgb
        val secondRGB = Threads[SECOND_THREAD].rgb
        p.drawStitches(c, g, rest.cx, rest.cy, csRestPresent(t, rest.total), restRGB, alpha)
        p.drawStitches(c, g, second.cx, second.cy, csSecondPresent(t, second.total), secondRGB, alpha)
        if (withNeedle) {
            needle(t, n)
            p.drawNeedle(c, n[0], n[1], n[2], n[3], n[4], n[5], csMix(restRGB, secondRGB, csThreadBlend(t), rgb), alpha)
        }
    }
}

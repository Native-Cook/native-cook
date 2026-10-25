package com.ngocdevv.nativecook.blocks.forms.icingnameform

import android.graphics.Bitmap
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
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/*
 Icing Name Form — what is drawn per frame: the cake bitmap under its turn and slide, the lettering
 as layered strokes (shadow, body, a lighter core, a glossy highlight that stays lit from the upper
 left whatever the cake's angle), the knife and the piping bag. Units are pt (dp).
*/

internal class IcingGeo(val w: Float, val h: Float, val heroX: Float, val heroY: Float, val heroW: Float, val heroH: Float) {
    val r = min(heroW, heroH) / 2 * 0.86f
    val cx = heroX + heroW / 2
    val cy = heroY + heroH / 2
    val boardR = r * 1.1f
    val boxW = r * 1.36f
    val boxH = r * 0.95f
    /** Where the nozzle rests, relative to the cake's centre. */
    val parkX = r * 0.98f
    val parkY = -r * 0.72f

    fun same(o: IcingGeo?) = o != null && o.w == w && o.h == h && o.heroX == heroX && o.heroY == heroY && o.heroW == heroW && o.heroH == heroH
}

internal class IcingKind(val name: String, val rgb: FloatArray, val hex: Long)

internal val Icings = listOf(
    IcingKind("Chocolate", floatArrayOf(0.36f, 0.2f, 0.13f), 0x5C3321),
    IcingKind("Rose", floatArrayOf(0.84f, 0.38f, 0.49f), 0xD6617D),
    IcingKind("Pistachio", floatArrayOf(0.53f, 0.66f, 0.39f), 0x87A863),
    IcingKind("Blueberry", floatArrayOf(0.3f, 0.37f, 0.66f), 0x4D5EA8),
    IcingKind("Lemon", floatArrayOf(0.88f, 0.71f, 0.22f), 0xE0B538),
)

/** Toward the light (upper left), as a unit vector in screen space. */
private const val LIGHT_X = -0.6f
private const val LIGHT_Y = -0.8f

private fun col(c: FloatArray, a: Float) = android.graphics.Color.argb(a.coerceIn(0f, 1f), c[0].coerceIn(0f, 1f), c[1].coerceIn(0f, 1f), c[2].coerceIn(0f, 1f))

private fun colf(r: Float, g: Float, b: Float, a: Float) = android.graphics.Color.argb(a.coerceIn(0f, 1f), r, g, b)

private fun tone(c: FloatArray, k: Float, out: FloatArray = FloatArray(3)): FloatArray {
    if (k < 0) { out[0] = c[0] * (1 + k); out[1] = c[1] * (1 + k); out[2] = c[2] * (1 + k) }
    else { out[0] = c[0] + (1 - c[0]) * k; out[1] = c[1] + (0.97f - c[1]) * k; out[2] = c[2] + (0.94f - c[2]) * k }
    return out
}

/** The point `upto` pt along a stroke. */
internal fun icStrokePoint(pts: FloatArray, len: FloatArray, upto: Float, out: FloatArray) {
    val n = len.size
    for (k in 1 until n) {
        if (len[k] >= upto) {
            val f = (upto - len[k - 1]) / max(0.0001f, len[k] - len[k - 1])
            out[0] = pts[k * 2 - 2] + (pts[k * 2] - pts[k * 2 - 2]) * f
            out[1] = pts[k * 2 - 1] + (pts[k * 2 + 1] - pts[k * 2 - 1]) * f
            return
        }
    }
    out[0] = pts[n * 2 - 2]; out[1] = pts[n * 2 - 1]
}

/** Per-stroke progress of a scripted piping at u (0..1 of its schedule). */
internal fun icScheduleFracs(t0: FloatArray, t1: FloatArray, u: Float, out: FloatArray) {
    for (i in t0.indices) {
        val f = icSeg(u, t0[i], t1[i])
        out[i] = 0.75f * f + 0.25f * icEaseInOut(f)
    }
}

/** The nozzle on a scripted piping at u: x, y (cake-local), lift 0..1. */
internal fun icSchedulePen(l: IcingLettering, t0: FloatArray, t1: FloatArray, u: Float, out: FloatArray, tmp: FloatArray) {
    val n = l.strokes.size
    if (n == 0) { out[0] = 0f; out[1] = 0f; out[2] = 1f; return }
    for (i in 0 until n) {
        val s = l.strokes[i]
        if (u <= t0[i]) {
            if (i == 0) { out[0] = s.pts[0]; out[1] = s.pts[1]; out[2] = 0f; return }
            // Travelling, lifted, from the end of the last stroke to the start of this one.
            val p = l.strokes[i - 1]
            val ex = p.pts[p.pts.size - 2]
            val ey = p.pts[p.pts.size - 1]
            val v = icEaseInOut(icSeg(u, t1[i - 1], t0[i]))
            out[0] = ex + (s.pts[0] - ex) * v; out[1] = ey + (s.pts[1] - ey) * v; out[2] = sin(PI.toFloat() * v)
            return
        }
        if (u <= t1[i]) {
            val f = icSeg(u, t0[i], t1[i])
            icStrokePoint(s.pts, s.len, s.full * (0.75f * f + 0.25f * icEaseInOut(f)), tmp)
            out[0] = tmp[0]; out[1] = tmp[1]; out[2] = 0f
            return
        }
    }
    val last = l.strokes[n - 1]
    out[0] = last.pts[last.pts.size - 2]; out[1] = last.pts[last.pts.size - 1]; out[2] = 0f
}

internal class IcingArt(val geo: IcingGeo, val table: Bitmap, val cake: Bitmap, val rest: IcingLettering, val second: IcingLettering, val t0: FloatArray, val t1: FloatArray)

/** Paints and paths reused every frame. */
internal class IcingPainter(private val art: IcingArt) {
    private val g = art.geo
    private val bmp = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val rect = RectF()
    private val body = Path()
    private val blobs = Path()
    private val bodyB = Path()
    private val blobsB = Path()
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val shStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND }
    private val shFill = Paint(Paint.ANTI_ALIAS_FLAG)
    private var shadowW = -1f
    private val tmp = FloatArray(3)
    private val sheen = Paint(Paint.ANTI_ALIAS_FLAG)
    private val blurs = HashMap<Int, BlurMaskFilter>()
    private val blurred = Paint(Paint.ANTI_ALIAS_FLAG)
    private val fracTmp = FloatArray(512)

    private fun blur(sigma: Float): BlurMaskFilter {
        val key = (sigma * 4).toInt()
        return blurs.getOrPut(key) { BlurMaskFilter(sigmaToRadius(key / 4f), BlurMaskFilter.Blur.NORMAL) }
    }

    /** Adds a stroke from its start to `upto` pt of its length, shifted by (dx, dy). */
    private fun addStroke(b: Path, pts: FloatArray, len: FloatArray, upto: Float, dx: Float, dy: Float) {
        if (upto <= 0) return
        b.moveTo(pts[0] + dx, pts[1] + dy)
        val n = len.size
        // A dot (zero-length stroke) still needs a tiny segment for the round cap.
        if (len[n - 1] < 0.6f) { b.lineTo(pts[0] + dx + 0.3f, pts[1] + dy + 0.2f); return }
        for (k in 1 until n) {
            if (len[k] <= upto) b.lineTo(pts[k * 2] + dx, pts[k * 2 + 1] + dy)
            else {
                val f = (upto - len[k - 1]) / max(0.0001f, len[k] - len[k - 1])
                b.lineTo(pts[k * 2 - 2] + (pts[k * 2] - pts[k * 2 - 2]) * f + dx, pts[k * 2 - 1] + (pts[k * 2 + 1] - pts[k * 2 - 1]) * f + dy)
                return
            }
        }
    }

    /** One layered tube of icing along `body`, with the start bulges in `blobs`. */
    private fun drawTube(c: Canvas, body: Path, blobs: Path, rgb: FloatArray, w: Float, lx: Float, ly: Float, alpha: Float, shadow: Boolean) {
        if (shadow) {
            if (shadowW != w) {
                val m = BlurMaskFilter(sigmaToRadius(w * 0.3f), BlurMaskFilter.Blur.NORMAL)
                shStroke.maskFilter = m; shFill.maskFilter = m; shadowW = w
            }
            shStroke.strokeWidth = w * 1.12f
            shStroke.color = colf(0.28f, 0.15f, 0.08f, 0.34f * alpha)
            shFill.color = shStroke.color
            c.save()
            c.translate(-lx * w * 0.5f, -ly * w * 0.75f)
            c.drawPath(body, shStroke)
            c.drawPath(blobs, shFill)
            c.restore()
        }
        // Body: the darker rim of the tube, a lighter core shifted toward the light, then the gloss.
        stroke.strokeWidth = w
        stroke.color = col(tone(rgb, -0.28f, tmp), alpha)
        c.drawPath(body, stroke)
        fill.color = stroke.color
        c.drawPath(blobs, fill)
        c.save(); c.translate(lx * w * 0.1f, ly * w * 0.1f)
        stroke.strokeWidth = w * 0.7f
        stroke.color = col(rgb, alpha)
        c.drawPath(body, stroke)
        c.restore()
        c.save(); c.translate(lx * w * 0.24f, ly * w * 0.24f)
        stroke.strokeWidth = w * 0.26f
        stroke.color = col(tone(rgb, 0.55f, tmp), 0.8f * alpha)
        c.drawPath(body, stroke)
        c.restore()
        c.save(); c.translate(lx * w * 0.3f, ly * w * 0.3f)
        stroke.strokeWidth = w * 0.1f
        stroke.color = colf(1f, 0.99f, 0.97f, 0.85f * alpha)
        c.drawPath(body, stroke)
        c.restore()
    }

    /**
     * Pipes a lettering in cake-local coordinates. `frac` per stroke (null = all piped), shift per
     * glyph, colour a everywhere and colour b flowed in over the first `flow` of the ink.
     */
    private fun drawLettering(c: Canvas, l: IcingLettering, frac: FloatArray?, sx: FloatArray?, sy: FloatArray?, a: FloatArray, b: FloatArray, flow: Float, lx: Float, ly: Float, alpha: Float) {
        if (alpha <= 0.002f || l.strokes.isEmpty()) return
        val w = max(4.2f, min(9f, l.size * 0.17f))
        body.rewind(); blobs.rewind(); bodyB.rewind(); blobsB.rewind()
        val flowLen = flow * l.total
        var acc = 0f
        for (i in l.strokes.indices) {
            val s = l.strokes[i]
            val full = s.full
            val upto = if (frac != null) full * frac[i] else full
            val dx = sx?.get(s.glyph) ?: 0f
            val dy = sy?.get(s.glyph) ?: 0f
            if (frac == null || frac[i] > 0) {
                addStroke(body, s.pts, s.len, max(upto, 0.01f), dx, dy)
                // Pressure bulge where the nozzle first squeezes (grows over the first 5 pt).
                val br = w * (0.5f + 0.1f * min(1f, upto / 5))
                blobs.addCircle(s.pts[0] + dx, s.pts[1] + dy, br, Path.Direction.CW)
                val bUpto = min(upto, flowLen - acc)
                if (bUpto > 0) {
                    addStroke(bodyB, s.pts, s.len, bUpto, dx, dy)
                    blobsB.addCircle(s.pts[0] + dx, s.pts[1] + dy, br, Path.Direction.CW)
                }
            }
            acc += max(full, 0.5f)
        }
        drawTube(c, body, blobs, a, w, lx, ly, alpha, true)
        if (flowLen > 0) drawTube(c, bodyB, blobsB, b, w, lx, ly, alpha, false)
    }

    /** Draws a lettering on a cake at (x, y) turned by `turn`, its gloss lit from the upper left. */
    fun drawWriting(c: Canvas, l: IcingLettering, x: Float, y: Float, turn: Float, frac: FloatArray?, sx: FloatArray?, sy: FloatArray?, a: Int, b: Int, flow: Float, alpha: Float, clipLeft: Float) {
        c.save()
        c.translate(x, y)
        c.rotate(turn * 180 / PI.toFloat())
        if (clipLeft > -1e4f) c.clipRect(clipLeft, -1e4f, clipLeft + 2e4f, 1e4f)
        val cs = cos(-turn)
        val sn = sin(-turn)
        drawLettering(c, l, frac, sx, sy, Icings[a].rgb, Icings[b].rgb, flow, LIGHT_X * cs - LIGHT_Y * sn, LIGHT_X * sn + LIGHT_Y * cs, alpha)
        c.restore()
    }

    /** The cake bitmap under its turn (rad) and slide, plus the fondant's sheen, which stays put. */
    fun drawCake(c: Canvas, x: Float, y: Float, turn: Float) {
        val side = g.boardR * 2 + 48
        c.save()
        c.translate(x, y)
        c.rotate(turn * 180 / PI.toFloat())
        rect.set(-side / 2, -side / 2, side / 2, side / 2)
        c.drawBitmap(art.cake, null, rect, bmp)
        c.restore()
        sheen.shader = RadialGradient(
            x - g.r * 0.34f, y - g.r * 0.4f, g.r * 0.9f,
            intArrayOf(colf(1f, 1f, 1f, 0.34f), colf(1f, 0.99f, 0.97f, 0.1f), colf(1f, 1f, 1f, 0f)), floatArrayOf(0f, 0.45f, 1f), Shader.TileMode.CLAMP,
        )
        c.drawCircle(x, y, g.r * 0.8f, sheen)
    }

    private val blade = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = LinearGradient(
            -14f, 0f, 14f, 0f,
            intArrayOf(colf(0.62f, 0.65f, 0.68f, 1f), colf(0.95f, 0.96f, 0.97f, 1f), colf(0.74f, 0.77f, 0.8f, 1f), colf(0.5f, 0.53f, 0.57f, 1f)),
            floatArrayOf(0f, 0.3f, 0.65f, 1f), Shader.TileMode.CLAMP,
        )
    }
    private val handle = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = LinearGradient(
            -9f, 0f, 9f, 0f,
            intArrayOf(colf(0.5f, 0.3f, 0.18f, 1f), colf(0.72f, 0.48f, 0.3f, 1f), colf(0.42f, 0.24f, 0.14f, 1f)), floatArrayOf(0f, 0.4f, 1f), Shader.TileMode.CLAMP,
        )
    }

    /** The palette knife lifting the writing off, its blade at x, slightly angled. */
    fun drawKnife(c: Canvas, x: Float, y: Float) {
        c.save()
        c.translate(x, y)
        c.rotate(7f)
        val top = -g.r * 1.7f
        val bottom = g.r * 1.05f
        blurred.maskFilter = blur(6f)
        blurred.color = colf(0.25f, 0.15f, 0.1f, 0.25f)
        rect.set(4f, top + 10, 34f, top + 10 + bottom - top)
        c.drawRoundRect(rect, 15f, 15f, blurred)
        rect.set(-14f, top + g.r * 0.45f, 14f, bottom)
        c.drawRoundRect(rect, 14f, 14f, blade)
        // A smear of the lifted icing along the leading edge.
        fill.color = colf(0.4f, 0.23f, 0.15f, 0.55f)
        rect.set(8f, -g.r * 0.25f, 13f, -g.r * 0.25f + g.r * 0.6f)
        c.drawRoundRect(rect, 2.5f, 2.5f, fill)
        rect.set(-9f, top - 40, 9f, top - 40 + g.r * 0.7f)
        c.drawRoundRect(rect, 8f, 8f, handle)
        c.restore()
    }

    private val bagPath = Path().apply {
        val l = 200f
        moveTo(14f, -7f)
        cubicTo(70f, -16f, 140f, -38f, l, -36f)
        quadTo(l + 14, 0f, l, 36f)
        cubicTo(140f, 38f, 70f, 16f, 14f, 7f)
        close()
    }
    private val pleats = Path().apply {
        for (k in 0 until 4) {
            moveTo(60f + k * 30, -12f - k * 6)
            quadTo(110f + k * 22, -4f - k * 3, 192f, -24f + k * 14)
        }
    }
    private val tipPath = Path().apply { moveTo(0f, -3.5f); lineTo(13f, -7f); lineTo(13f, 7f); lineTo(0f, 3.5f); close() }
    private val bag = Paint(Paint.ANTI_ALIAS_FLAG)
    private var bagRGB = FloatArray(3) { -1f }
    private val film = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = LinearGradient(
            0f, -36f, 0f, 36f,
            intArrayOf(colf(1f, 1f, 1f, 0.55f), colf(1f, 1f, 1f, 0.08f), colf(1f, 1f, 1f, 0f), colf(1f, 1f, 1f, 0.18f)),
            floatArrayOf(0f, 0.3f, 0.75f, 1f), Shader.TileMode.CLAMP,
        )
    }
    private val pleat = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 1f; color = colf(1f, 1f, 1f, 0.35f) }
    private val metal = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = LinearGradient(
            0f, -8f, 0f, 8f,
            intArrayOf(colf(0.96f, 0.96f, 0.95f, 1f), colf(0.66f, 0.68f, 0.7f, 1f), colf(0.42f, 0.44f, 0.47f, 1f), colf(0.82f, 0.83f, 0.84f, 1f)),
            floatArrayOf(0f, 0.35f, 0.7f, 1f), Shader.TileMode.CLAMP,
        )
    }

    /** The piping bag: a metal star tip at (x, y), the bag rising to the upper right. */
    fun drawNozzle(c: Canvas, x: Float, y: Float, lift: Float, rgb: FloatArray) {
        val l = 200f
        val s = 1 + lift * 0.06f
        // Shadow on the cake, further away when lifted.
        blurred.maskFilter = blur(6 + lift * 6)
        blurred.color = colf(0.25f, 0.14f, 0.08f, 0.22f - lift * 0.06f)
        c.save()
        c.translate(x + 10 + lift * 14, y + 16 + lift * 18)
        c.rotate(-48f)
        rect.set(0f, -14f, l, 32f)
        c.drawRoundRect(rect, 23f, 23f, blurred)
        c.restore()

        if (!rgb.contentEquals(bagRGB)) {
            bag.shader = LinearGradient(
                0f, -36f, 0f, 36f,
                intArrayOf(col(tone(rgb, 0.35f, tmp), 1f), col(tone(rgb, 0.05f, tmp), 1f), col(tone(rgb, -0.2f, tmp), 1f), col(tone(rgb, -0.38f, tmp), 1f)),
                floatArrayOf(0f, 0.35f, 0.7f, 1f), Shader.TileMode.CLAMP,
            )
            rgb.copyInto(bagRGB)
        }
        c.save()
        c.translate(x, y)
        c.scale(s, s)
        c.rotate(-48f)
        // The bag: translucent plastic over icing, pleated at the top.
        c.drawPath(bagPath, bag)
        c.drawPath(bagPath, film)
        c.drawPath(pleats, pleat)
        // Coupler ring and the star tip.
        rect.set(12f, -9f, 24f, 9f)
        c.drawRoundRect(rect, 3f, 3f, metal)
        c.drawPath(tipPath, metal)
        c.restore()
        // The star opening, seen from above at the tip.
        fill.color = col(tone(rgb, -0.3f, tmp), 1f)
        c.drawCircle(x, y, 2.6f * s, fill)
    }

    private val pen = FloatArray(3)
    private val ptmp = FloatArray(2)

    /** One frame of the loop at t. */
    fun drawLoopFrame(c: Canvas, t: Float, alpha: Float, withNozzle: Boolean) {
        val s = icSlideAt(t)
        val away = g.w / 2 + g.boardR + 40
        // The cake now on the turntable (it slides out from 7.4), and the next one sliding in.
        val x0 = g.cx - s * away
        val turn0 = -s * 0.7f
        drawCake(c, x0, g.cy, turn0)
        if (t < 1.4f) drawWriting(c, art.rest, x0, g.cy, turn0, null, null, null, 0, 0, 0f, alpha, -1e5f)
        else if (t < 2.2f) {
            val k = icKnifeAt(t)
            drawWriting(c, art.rest, x0, g.cy, turn0, null, null, null, 0, 0, 0f, alpha, if (k < -1.9f) -1e5f else k * g.r * 0.75f)
        } else {
            icScheduleFracs(art.t0, art.t1, icPipeProgress(t), fracTmp)
            drawWriting(c, art.second, x0, g.cy, turn0, fracTmp, null, null, 0, 1, icRoseFlow(t), alpha, -1e5f)
        }
        if (s > 0) {
            val x1 = g.cx + (1 - s) * away
            val turn1 = (1 - s) * 0.7f
            drawCake(c, x1, g.cy, turn1)
            drawWriting(c, art.rest, x1, g.cy, turn1, null, null, null, 0, 0, 0f, alpha, -1e5f)
        }
        val k = icKnifeAt(t)
        if (k > -1.9f) drawKnife(c, g.cx + k * g.r * 0.75f, g.cy)
        if (withNozzle) {
            val w = icNozzleWork(t)
            icSchedulePen(art.second, art.t0, art.t1, icPipeProgress(t), pen, ptmp)
            val px = g.parkX + (pen[0] - g.parkX) * w
            val py = g.parkY + (pen[1] - g.parkY) * w
            val lift = 1 - w + w * pen[2]
            val flow = icRoseFlow(t)
            val icing = if (flow > 0.5f || t > 7.9f) (if (t > 7.9f) 0 else 1) else 0
            drawNozzle(c, g.cx + px, g.cy + py, lift, Icings[icing].rgb)
        }
    }

    fun drawTable(c: Canvas) {
        rect.set(0f, 0f, g.w, g.h)
        c.drawBitmap(art.table, null, rect, bmp)
    }
}

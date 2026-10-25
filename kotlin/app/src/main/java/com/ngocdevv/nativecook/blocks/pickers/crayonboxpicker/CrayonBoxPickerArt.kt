package com.ngocdevv.nativecook.blocks.pickers.crayonboxpicker

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.DiscretePathEffect
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.graphics.Shader
import com.ngocdevv.nativecook.shared.sigmaToRadius
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/*
 Crayon Box Picker — what is drawn per frame: the swatches (wax strokes with the paper's tooth
 knocked out), the crayons at their poses, the box front over them, and the crayon in flight on top.
 Everything is in pt (dp).
*/

internal class CrayonGeo(
    val w: Float, val h: Float, val heroX: Float, val heroY: Float, val heroW: Float, val heroH: Float,
) {
    // Everything scales with the hero's height (designed for 470 pt) so a pulled crayon stays inside it.
    private val u = min(1f, heroH / 470)
    val boxW = min(330f, heroW - 16)
    val frontH = 92 * u
    val showFront = 84 * u
    val rimY = heroY + 182 * u
    val pitch = (boxW - 28) / 6
    val paperY = rimY + frontH + 22 * u
    val paperH = max(80f, heroY + heroH - paperY - 6)
    val cx = heroX + heroW / 2
    val boxX = heroX + (heroW - boxW) / 2
    val cw = pitch * 0.6f
    val cl = frontH - 8 + showFront
    val showBack = showFront + 24 * u
    val sx = heroX + heroW / 2 + 8
    val sy = paperY + paperH * 0.5f
    val rx = min(112f, heroW * 0.3f)
    val ry = min(44f, paperH * 0.3f)

    fun same(o: CrayonGeo?) = o != null && o.w == w && o.h == h && o.heroX == heroX && o.heroY == heroY && o.heroW == heroW && o.heroH == heroH
}

/** A crayon's slot: centre x, centre y, row (0 back, 1 front), visible length. */
internal fun crSlot(g: CrayonGeo, i: Int, out: FloatArray) {
    val back = i < 6
    val k = if (back) i else i - 6
    out[0] = g.boxX + 14 + (k + 0.5f) * g.pitch + if (back) -g.pitch * 0.22f else g.pitch * 0.22f
    val show = if (back) g.showBack else g.showFront
    out[1] = g.rimY - show + g.cl / 2
    out[2] = if (back) 0f else 1f
    out[3] = show
}

/** The scribble's path on the paper, sampled: points (flat) and running lengths. */
internal class CrayonScribble(val pts: FloatArray, val len: FloatArray) {
    val total = len[len.size - 1]

    fun point(d: Float, out: FloatArray) {
        val n = len.size
        for (k in 1 until n) {
            if (len[k] >= d) {
                val f = (d - len[k - 1]) / max(0.0001f, len[k] - len[k - 1])
                out[0] = pts[k * 2 - 2] + (pts[k * 2] - pts[k * 2 - 2]) * f
                out[1] = pts[k * 2 - 1] + (pts[k * 2 + 1] - pts[k * 2 - 1]) * f
                return
            }
        }
        out[0] = pts[n * 2 - 2]; out[1] = pts[n * 2 - 1]
    }
}

internal fun crScribble(g: CrayonGeo): CrayonScribble {
    val pts = ArrayList<Float>()
    val rows = 15
    var seed = 3L
    fun rnd(): Float {
        seed = (seed * 16807) % 2147483647
        return (seed / 2147483647.0).toFloat()
    }
    var side = -1f
    val slant = tan(14 * PI / 180).toFloat()
    for (k in 0 until rows) {
        val v = -1 + 2f * k / (rows - 1)
        val y = v * g.ry * 0.92f
        val hw = g.rx * sqrt(max(0.05f, 1 - v * v)) * (0.86f + rnd() * 0.16f)
        val x0 = side * hw
        val x1 = -side * hw
        for (s in 0..10) {
            val u = s / 10f
            val x = x0 + (x1 - x0) * u
            val yy = y + sin(u * PI.toFloat()) * (2 + rnd() * 1.5f) + x * slant * 0.3f + g.ry * 1.85f / (rows - 1) * (u - 0.5f) * 0.5f
            pts.add(g.sx + x); pts.add(g.sy + yy)
        }
        side = -side
    }
    val p = pts.toFloatArray()
    val len = FloatArray(p.size / 2)
    var run = 0f
    var i = 2
    while (i < p.size) {
        run += hypot(p[i] - p[i - 2], p[i + 1] - p[i - 1])
        len[i / 2] = run
        i += 2
    }
    return CrayonScribble(p, len)
}

/** Baked art: the desk (full screen), the box front (boxW + 40 × frontH + 40 pt), the crayons (cw × cl pt) and the paper tooth mask. */
internal class CrayonArt(
    val geo: CrayonGeo,
    val path: CrayonScribble,
    val desk: Bitmap,
    val front: Bitmap,
    val crayons: List<Bitmap>,
    val tooth: Bitmap,
    val toothX: Float,
    val toothY: Float,
    val toothW: Float,
    val toothH: Float,
)

private const val WRITE = -120f

/** Poses per crayon (6 floats each): x, y, deg, scale, lift, onTop. */
internal fun crPoses(g: CrayonGeo, active: Int, ph: Int, a: Float, wobble: Float, pen0: FloatArray, pen: FloatArray, penEnd: FloatArray, out: FloatArray, s: FloatArray) {
    val lean = when (ph) {
        1 -> min(1f, a)
        2, 3, 4 -> 1f
        5 -> a
        else -> 0f
    }
    for (i in 0 until 12) {
        crSlot(g, i, s)
        var x = s[0]; var y = s[1]; var deg = 0f; var scale = 1f; var lift = 0f; var top = 0f
        val out0 = g.cl - s[3] + 6
        if (i == active) {
            val px = s[0]
            val py = s[1] - out0
            if (ph == 1 || ph == 5) {
                y = s[1] - out0 * a
                deg = 4 * wobble
            } else {
                val tw = tipX(g, WRITE); val th = tipY(g, WRITE)
                val from = if (ph == 4) penEnd else pen0
                val wx = from[0] - tw
                val wy = from[1] - th
                if (ph == 3) {
                    val d = WRITE + 3 * sin(a * PI.toFloat() * 22)
                    x = pen[0] - tipX(g, d)
                    y = pen[1] - tipY(g, d)
                    deg = d
                } else {
                    // ph 2 flies to the paper, ph 4 flies home: same arc, reversed.
                    val f = if (ph == 2) a else 1 - a
                    val mx = (px + wx) / 2 + 30
                    val my = min(py, wy) - 50
                    x = (1 - f) * (1 - f) * px + 2 * f * (1 - f) * mx + f * f * wx
                    y = (1 - f) * (1 - f) * py + 2 * f * (1 - f) * my + f * f * wy
                    deg = WRITE * crEaseInOut(f)
                    lift = sin(PI.toFloat() * f)
                    scale = 1 + 0.08f * lift
                }
                top = 1f
            }
        } else if (active >= 0 && lean > 0) {
            // Neighbours in the same row lean a little into the gap (pivoting at their foot).
            val sameRow = (active < 6) == (i < 6)
            val d = i - active
            if (sameRow && abs(d) == 1) {
                deg = -d * 3.2f * lean
                val r = deg * PI.toFloat() / 180
                val foot = g.cl / 2
                x += foot * sin(r)
                y += foot * (1 - cos(r))
            }
        }
        val o = i * 6
        out[o] = x; out[o + 1] = y; out[o + 2] = deg; out[o + 3] = scale; out[o + 4] = lift; out[o + 5] = top
    }
}

private fun tipX(g: CrayonGeo, deg: Float) = g.cl / 2 * sin(deg * PI.toFloat() / 180)
private fun tipY(g: CrayonGeo, deg: Float) = -(g.cl / 2) * cos(deg * PI.toFloat() / 180)

/** Paints and paths reused every frame. */
internal class CrayonPainter(private val art: CrayonArt) {
    private val g = art.geo
    private val bmp = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val stroke = Path()
    private val wax = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
    }
    private val ragged = DiscretePathEffect(5f, 1.1f)
    private val tooth = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
        style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND; strokeWidth = 14f
        xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OUT)
        shader = BitmapShader(art.tooth, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).apply {
            setLocalMatrix(Matrix().apply {
                setScale(art.toothW / art.tooth.width, art.toothH / art.tooth.height)
                postTranslate(art.toothX, art.toothY)
            })
        }
    }
    private val shadow = Paint(Paint.ANTI_ALIAS_FLAG)
    private val blurs = HashMap<Int, BlurMaskFilter>()
    private val pt = FloatArray(2)
    private val rect = RectF()

    /** One swatch: the first `upto` pt of the scribble in crayon `c`, at `alpha`. */
    fun drawSwatch(c: Canvas, crayon: Int, upto: Float, alpha: Float) {
        if (upto <= 0 || alpha <= 0.003f) return
        val p = art.path
        stroke.rewind()
        stroke.moveTo(p.pts[0], p.pts[1])
        for (k in 1 until p.len.size) {
            if (p.len[k] <= upto) stroke.lineTo(p.pts[k * 2], p.pts[k * 2 + 1])
            else {
                p.point(upto, pt)
                stroke.lineTo(pt[0], pt[1])
                break
            }
        }
        val rgb = Crayons[crayon].rgb
        c.saveLayerAlpha(art.toothX, art.toothY, art.toothX + art.toothW, art.toothY + art.toothH, (alpha * 255).toInt().coerceIn(0, 255))
        wax.strokeWidth = 12f
        wax.color = android.graphics.Color.argb(0.9f, rgb[0], rgb[1], rgb[2])
        wax.pathEffect = ragged
        c.drawPath(stroke, wax)
        wax.pathEffect = null
        wax.strokeWidth = 4f
        wax.color = android.graphics.Color.argb(0.14f, 1f, 1f, 1f)
        c.save(); c.translate(-1.5f, -2f); c.drawPath(stroke, wax); c.restore()
        // Paper tooth: the wax skips over the grain, so speckles of paper show through.
        c.drawPath(stroke, tooth)
        c.restore()
    }

    private fun blur(sigma: Float): BlurMaskFilter {
        val key = (sigma * 2).toInt()
        return blurs.getOrPut(key) { BlurMaskFilter(sigmaToRadius(key / 2f), BlurMaskFilter.Blur.NORMAL) }
    }

    fun drawCrayon(c: Canvas, img: Bitmap, x: Float, y: Float, deg: Float, scale: Float, lift: Float) {
        if (lift > 0.001f) {
            shadow.color = android.graphics.Color.argb(0.28f - 0.1f * lift, 0.2f, 0.13f, 0.06f)
            shadow.maskFilter = blur(3 + 9 * lift)
            c.save()
            c.translate(x + 6 + 16 * lift, y + 8 + 22 * lift)
            c.rotate(deg)
            rect.set(-g.cw / 2, -g.cl / 2, g.cw / 2, g.cl / 2)
            c.drawRoundRect(rect, g.cw / 2, g.cw / 2, shadow)
            c.restore()
        }
        c.save()
        c.translate(x, y)
        c.rotate(deg)
        c.scale(scale, scale)
        rect.set(-g.cw / 2, -g.cl / 2, g.cw / 2, g.cl / 2)
        c.drawBitmap(img, null, rect, bmp)
        c.restore()
    }

    fun drawBox(c: Canvas, poses: FloatArray, swatch: FloatArray) {
        rect.set(0f, 0f, g.w, g.h)
        c.drawBitmap(art.desk, null, rect, bmp)
        val total = art.path.total
        drawSwatch(c, swatch[0].toInt(), total, swatch[1])
        drawSwatch(c, swatch[2].toInt(), total * swatch[3], 1f)
        // Crayons inside the box are clipped to above its floor; the front panel covers their feet.
        c.save()
        c.clipRect(0f, 0f, g.w, g.rimY + g.frontH - 6)
        for (i in 0 until 12) {
            val o = i * 6
            if (poses[o + 5] == 0f) drawCrayon(c, art.crayons[i], poses[o], poses[o + 1], poses[o + 2], poses[o + 3], 0f)
        }
        c.restore()
        rect.set(g.boxX - 20, g.rimY - 20, g.boxX + g.boxW + 20, g.rimY + g.frontH + 20)
        c.drawBitmap(art.front, null, rect, bmp)
        for (i in 0 until 12) {
            val o = i * 6
            if (poses[o + 5] == 1f) drawCrayon(c, art.crayons[i], poses[o], poses[o + 1], poses[o + 2], poses[o + 3], poses[o + 4])
        }
    }
}

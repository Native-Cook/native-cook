package com.ngocdevv.nativecook.blocks.modals.fortunecookiecrack

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.BlendMode
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PathMeasure
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.os.Build
import com.ngocdevv.nativecook.blocks.modals.fortunecookiecrack.FortuneCookieCrackMotion.hash
import com.ngocdevv.nativecook.shared.sigmaToRadius
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive

/*
 The cookie's shapes, its baked texture and every static picture of the block, ported from
 fortuneArt.ts / FortuneParts.tsx and FortuneCookieCrackMotion.swift. The RN version shades the
 cookie and the paper grain with runtime shaders; neither moves, so here they are computed once on
 the CPU with the same maths (no P3 conversion) and kept as bitmaps, like the Swift version.
 */

internal fun fortuneArgb(r: Int, g: Int, b: Int, a: Double) = ((a * 255).roundToInt().coerceIn(0, 255) shl 24) or (r shl 16) or (g shl 8) or b
internal fun fortuneHex(rgb: Int) = (0xFF shl 24) or rgb

/** A paint that blurs what it fills by [sigma] (in the canvas's units). */
internal fun fortuneBlurPaint(color: Int, sigma: Float) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    this.color = color
    maskFilter = BlurMaskFilter(sigmaToRadius(sigma), BlurMaskFilter.Blur.NORMAL)
}

/** Soft light where the platform has it (API 29), overlay below. */
internal fun Paint.fortuneSoftLight() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) blendMode = BlendMode.SOFT_LIGHT
    else xfermode = PorterDuffXfermode(PorterDuff.Mode.OVERLAY)
}

/** The cookie in its own coordinates: centre (0, 0), about 232 dp wide, split along a jagged crack. */
internal class FortuneCookieShapes {
    val whole = Path()
    val wholeLip = Path()
    val left = Path()
    val right = Path()
    val leftLip = Path()
    val rightLip = Path()
    val inside = Path()
    val leftInside = Path()
    val rightInside = Path()
    val rim = Path()
    val crease = Path()
    val crack = Path()
    val branches = Path()

    init {
        // One smooth dome on top, two wings below pinched into the fold's notch in the middle.
        whole.moveTo(-116f, 32f)
        whole.cubicTo(-124f, -26f, -66f, -66f, 0f, -66f)
        whole.cubicTo(66f, -66f, 124f, -26f, 116f, 32f)
        whole.cubicTo(102f, 54f, 58f, 64f, 26f, 54f)
        whole.quadTo(10f, 42f, 0f, 4f)
        whole.quadTo(-10f, 42f, -26f, 54f)
        whole.cubicTo(-58f, 64f, -102f, 54f, -116f, 32f)
        whole.close()
        // The folded-over flaps either side of the notch, and their rolled, lit edge.
        for (d in floatArrayOf(-1f, 1f)) {
            wholeLip.moveTo(0f, 4f)
            wholeLip.cubicTo(d * 30, 12f, d * 82, 8f, d * 113, 31f)
            wholeLip.cubicTo(d * 102, 54f, d * 58, 64f, d * 26, 54f)
            wholeLip.quadTo(d * 10, 42f, 0f, 4f)
            wholeLip.close()
            rim.moveTo(d * 2, 6f)
            rim.cubicTo(d * 30, 12f, d * 82, 8f, d * 112, 31f)
        }
        // The crack: from the crease down to the bottom edge, x jitter of ±5 dp every 8.5 dp.
        val pts = Array(15) { k ->
            val y = -74f + k * 8.5f
            val jx = if (k == 0) 0f else ((hash(k * 2.7 + 3) - 0.5) * 10).toFloat()
            floatArrayOf(jx, y)
        }
        fun side(dir: Float) = Path().apply {
            moveTo(pts[0][0], -200f)
            for (q in pts) lineTo(q[0], q[1])
            lineTo(pts[pts.size - 1][0], 200f)
            lineTo(dir * 300, 200f)
            lineTo(dir * 300, -200f)
            close()
        }
        val l = side(-1f)
        val r = side(1f)
        inside.addOval(RectF(-15f, 8f, 15f, 41f), Path.Direction.CW)
        crease.moveTo(0f, 4f)
        crease.quadTo(-2f, -14f, 0f, -36f)
        crack.moveTo(pts[0][0], pts[0][1])
        for (q in pts) crack.lineTo(q[0], q[1])
        branches.moveTo(pts[3][0], pts[3][1])
        branches.lineTo(pts[3][0] - 12, pts[3][1] + 6)
        branches.lineTo(pts[3][0] - 24, pts[3][1] + 4)
        branches.moveTo(pts[6][0], pts[6][1])
        branches.lineTo(pts[6][0] + 10, pts[6][1] + 4)
        branches.lineTo(pts[6][0] + 20, pts[6][1] + 1)

        left.op(whole, l, Path.Op.INTERSECT)
        right.op(whole, r, Path.Op.INTERSECT)
        leftLip.op(wholeLip, l, Path.Op.INTERSECT)
        rightLip.op(wholeLip, r, Path.Op.INTERSECT)
        leftInside.op(inside, l, Path.Op.INTERSECT)
        rightInside.op(inside, r, Path.Op.INTERSECT)
    }

    companion object {
        /** Where the cookie's art lives in its own coordinates (with room for the lip's shadow). */
        val bounds = RectF(-126f, -70f, 126f, 66f)
        const val PIVOT_X = 102f
        const val PIVOT_Y = 36f
    }
}

/** A path drawn up to a fraction of its whole length (across contours), like Skia's `end`. */
internal class FortuneTrim(source: Path) {
    private val contours = ArrayList<PathMeasure>()
    private val lengths = ArrayList<Float>()
    private val total: Float
    val out = Path()

    init {
        val m = PathMeasure(source, false)
        var sum = 0f
        do {
            val len = m.length
            if (len > 0) {
                val copy = Path()
                m.getSegment(0f, len, copy, true)
                contours += PathMeasure(copy, false)
                lengths += len
                sum += len
            }
        } while (m.nextContour())
        total = sum
    }

    fun upTo(f: Float): Path {
        out.rewind()
        var left = total * f.coerceIn(0f, 1f)
        for (i in contours.indices) {
            if (left <= 0f) break
            val take = min(left, lengths[i])
            contours[i].getSegment(0f, take, out, true)
            left -= take
        }
        return out
    }
}

/** Baked cookie texture and paper grain, the same maths as the RN shaders. */
internal object FortuneTexture {
    private fun h(x: Double, y: Double): Double {
        val s = sin(x * 12.9898 + y * 78.233) * 43758.5453
        return s - floor(s)
    }

    private fun vn(x: Double, y: Double): Double {
        val ix = floor(x)
        val iy = floor(y)
        var fx = x - ix
        var fy = y - iy
        fx = fx * fx * (3 - 2 * fx)
        fy = fy * fy * (3 - 2 * fy)
        val a = h(ix, iy)
        val b = h(ix + 1, iy)
        val c = h(ix, iy + 1)
        val d = h(ix + 1, iy + 1)
        val top = a + (b - a) * fx
        val bot = c + (d - c) * fx
        return top + (bot - top) * fy
    }

    private fun fbm(x: Double, y: Double) =
        vn(x, y) * 0.55 + vn(x * 2.03 + 7.1, y * 2.03 + 7.1) * 0.3 + vn(x * 4.11 + 3.7, y * 4.11 + 3.7) * 0.15

    private fun sstep(a: Double, b: Double, x: Double): Double {
        val t = min(1.0, max(0.0, (x - a) / (b - a)))
        return t * t * (3 - 2 * t)
    }

    /** Golden in the middle, toasted toward the rim and tips, sheen on the lobes, baked fbm, pits and sugar glints. */
    private fun cookieColor(px: Double, py: Double, out: DoubleArray, blotNoise: Double, bakeNoise: Double) {
        val qx = px / 114
        val qy = py / 64
        val v = min(1.0, max(0.0, qy * 0.5 + 0.5))
        var r: Double
        var g: Double
        var b: Double
        if (v < 0.5) {
            val k = v * 2
            r = 0.97 + (0.91 - 0.97) * k; g = 0.80 + (0.66 - 0.80) * k; b = 0.47 + (0.31 - 0.47) * k
        } else {
            val k = (v - 0.5) * 2
            r = 0.91 + (0.76 - 0.91) * k; g = 0.66 + (0.48 - 0.66) * k; b = 0.31 + (0.19 - 0.31) * k
        }
        // A broad sheen on the dome and a tighter glint: the baked sugar is a little glossy.
        val ax = (px + 34) / 62
        val ay = (py + 36) / 26
        val sheen = exp(-(ax * ax + ay * ay))
        r += 0.15 * sheen; g += 0.12 * sheen; b += 0.07 * sheen
        val gx = (px + 48) / 22
        val gy = (py + 46) / 7
        val glint = exp(-(gx * gx + gy * gy))
        r += 0.2 * glint; g += 0.18 * glint; b += 0.13 * glint
        // Toasted toward the wing tips and the folded bottom.
        val e = sqrt(qx * qx + qy * 1.1 * qy * 1.1)
        var k = (1 - 0.36 * sstep(0.74, 1.1, e)) * (1 - 0.16 * sstep(0.15, 0.8, qy))
        r *= k; g *= k; b *= k
        val blot = sstep(0.55, 0.82, blotNoise) * 0.4
        r += (r * 0.86 - r) * blot; g += (g * 0.74 - g) * blot; b += (b * 0.62 - b) * blot
        k = (0.93 + 0.12 * bakeNoise) * (0.97 + 0.05 * h(floor(px * 1.3), floor(py * 1.3)))
        r *= k; g *= k; b *= k
        if (h(floor(px * 0.9), floor(py * 0.9)) >= 0.994) {
            r += 0.12; g += 0.1; b += 0.07
        }
        out[0] = r; out[1] = g; out[2] = b
    }

    /** The cookie's texture over [FortuneCookieShapes.bounds] at [scale] px per dp, for two shades. */
    fun cookie(scale: Float, shades: DoubleArray, job: Job? = null): List<Bitmap> {
        val bd = FortuneCookieShapes.bounds
        val w = (bd.width() * scale).roundToInt()
        val hgt = (bd.height() * scale).roundToInt()
        val pixels = Array(shades.size) { IntArray(w * hgt) }
        val c = DoubleArray(3)
        // The two fbm terms (12 sines a pixel each) vary over several dp: computed on a 1 dp grid and
        // interpolated, which made this bake several times faster (the screen stayed blank for seconds).
        val gw = bd.width().toInt() + 2
        val gh = bd.height().toInt() + 2
        val blotGrid = DoubleArray(gw * gh)
        val bakeGrid = DoubleArray(gw * gh)
        for (gj in 0 until gh) for (gi in 0 until gw) {
            val px = bd.left + gi.toDouble()
            val py = bd.top + gj.toDouble()
            blotGrid[gj * gw + gi] = fbm(px * 0.05 + 11, py * 0.05 + 11)
            bakeGrid[gj * gw + gi] = fbm(px * 0.12, py * 0.12)
        }
        fun lerp2(g: DoubleArray, i0: Int, j0: Int, fx: Double, fy: Double): Double {
            val a = g[j0 * gw + i0]
            val b = g[j0 * gw + i0 + 1]
            val cc = g[(j0 + 1) * gw + i0]
            val d = g[(j0 + 1) * gw + i0 + 1]
            val t = a + (b - a) * fx
            return t + ((cc + (d - cc) * fx) - t) * fy
        }
        for (j in 0 until hgt) {
            job?.ensureActive()
            val py = bd.top + (j + 0.5) / scale
            val gy = py - bd.top
            val j0 = min(gh - 2, gy.toInt())
            val fy = gy - j0
            for (i in 0 until w) {
                val px = bd.left + (i + 0.5) / scale
                val gx = px - bd.left
                val i0 = min(gw - 2, gx.toInt())
                val fx = gx - i0
                cookieColor(px, py, c, lerp2(blotGrid, i0, j0, fx, fy), lerp2(bakeGrid, i0, j0, fx, fy))
                for (n in shades.indices) {
                    val s = shades[n] * 255
                    val r = (c[0] * s).toInt().coerceIn(0, 255)
                    val g = (c[1] * s).toInt().coerceIn(0, 255)
                    val b = (c[2] * s).toInt().coerceIn(0, 255)
                    pixels[n][j * w + i] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
                }
            }
        }
        return pixels.map { Bitmap.createBitmap(it, w, hgt, Bitmap.Config.ARGB_8888) }
    }

    /** Paper grain: 100 dp square at 1.4 px per dp, tiled with soft light. */
    fun grain(): Bitmap {
        val n = 140
        val px = IntArray(n * n)
        for (j in 0 until n) for (i in 0 until n) {
            val v = h(i.toDouble(), j.toDouble()) * 0.6 + h((i / 4) + 13.0, (j / 4) + 13.0) * 0.4
            val g = (v * 255).toInt().coerceIn(0, 255)
            px[j * n + i] = (0xFF shl 24) or (g shl 16) or (g shl 8) or g
        }
        return Bitmap.createBitmap(px, n, n, Bitmap.Config.ARGB_8888)
    }

    /** A paint that tiles the grain (in dp units) with soft light at [amount]. */
    fun grainPaint(grain: Bitmap, amount: Double) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = BitmapShader(grain, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT).apply {
            setLocalMatrix(Matrix().apply { setScale(1 / 1.4f, 1 / 1.4f) })
        }
        alpha = (amount * 255).roundToInt()
        fortuneSoftLight()
    }
}

/** Layout (dp): the modal fills the screen under the app's dimmed header. */
internal class FortuneLayout(val w: Float, val h: Float, val top: Float, val bottom: Float) {
    val mx = 16f
    val mw = max(240f, w - 32)
    val mTop = top + 64
    val mH = h - bottom - 20 - mTop
    val cx = w / 2
    val cy = mTop + mH * 0.4f
    val plateRx = min(140f, mw / 2 - 24)
    val slipY = mTop + mH * 0.655f
    val slipW = mw - 40

    companion object {
        const val SLIP_H = 84f
        const val COOKIE_SCALE = 1.12f
    }
}

/** Everything baked once: the three cookie pictures, the crease, the plate shadow and the grain. */
internal class FortuneArt(val scale: Float, job: Job? = null) {
    val shapes = FortuneCookieShapes()
    val grain = FortuneTexture.grain()
    val whole: Bitmap
    val left: Bitmap
    val right: Bitmap
    /** The soft crease line, in the cookie's bounds. */
    val crease: Bitmap
    /** One half's soft shadow on the plate, 124 × 18 dp blurred by 7, centred in [shadowRect]. */
    val shadow: Bitmap
    val shadowRect = RectF(-62f - 24f, -9f - 24f, 62f + 24f, 9f + 24f)
    val crackTrim = FortuneTrim(shapes.crack)
    val branchTrim = FortuneTrim(shapes.branches)

    init {
        val tex = FortuneTexture.cookie(scale, doubleArrayOf(1.0, 0.84), job)
        val s = shapes
        whole = piece(s.whole, s.wholeLip, s.inside, false, tex)
        left = piece(s.left, s.leftLip, s.leftInside, true, tex)
        right = piece(s.right, s.rightLip, s.rightInside, true, tex)
        tex.forEach { it.recycle() }
        crease = bake(FortuneCookieShapes.bounds) { c ->
            c.drawPath(s.crease, fortuneBlurPaint(fortuneArgb(110, 58, 16, 0.4), 1.4f).apply {
                style = Paint.Style.STROKE
                strokeWidth = 2.2f
                strokeCap = Paint.Cap.ROUND
            })
        }
        shadow = bake(shadowRect) { c ->
            c.drawOval(RectF(-62f, -9f, 62f, 9f), fortuneBlurPaint(fortuneArgb(80, 40, 12, 0.28), 7f))
        }
    }

    /** A software bitmap covering [r] (dp) at [scale], drawn in dp. */
    private fun bake(r: RectF, draw: (Canvas) -> Unit): Bitmap {
        val bmp = Bitmap.createBitmap((r.width() * scale).roundToInt(), (r.height() * scale).roundToInt(), Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.scale(scale, scale)
        c.translate(-r.left, -r.top)
        draw(c)
        return bmp
    }

    /** A piece of the cookie: the dark pocket, its baked body, the folded flap in front with its lit edge. */
    private fun piece(body: Path, lip: Path, inside: Path, broken: Boolean, tex: List<Bitmap>): Bitmap {
        val bd = FortuneCookieShapes.bounds
        return bake(bd) { c ->
            c.drawPath(inside, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = LinearGradient(0f, 14f, 0f, 54f, intArrayOf(fortuneHex(0x3E2008), fortuneHex(0x6A3A12), fortuneHex(0x8A5520)), null, Shader.TileMode.CLAMP)
            })
            val texPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
            // Drawn as a shader-filled path so the body's edge is anti-aliased.
            fun fillWith(p: Path, bmp: Bitmap) {
                texPaint.shader = BitmapShader(bmp, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).apply {
                    setLocalMatrix(Matrix().apply {
                        setScale(1 / scale, 1 / scale)
                        postTranslate(bd.left, bd.top)
                    })
                }
                c.drawPath(p, texPaint)
            }
            fillWith(body, tex[0])
            c.save()
            c.clipPath(body)
            c.save()
            c.translate(0f, -2.5f)
            c.drawPath(lip, fortuneBlurPaint(fortuneArgb(96, 48, 10, 0.45), 3.5f))
            c.restore()
            fillWith(lip, tex[1])
            c.drawPath(shapes.rim, fortuneBlurPaint(fortuneArgb(255, 226, 160, 0.75), 0.8f).apply {
                style = Paint.Style.STROKE
                strokeWidth = 2.2f
                strokeCap = Paint.Cap.ROUND
            })
            if (broken) {
                // The broken face shows the paler, airy inside of the wafer.
                val st = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.STROKE
                    strokeJoin = Paint.Join.ROUND
                    strokeWidth = 6f
                    color = fortuneArgb(252, 226, 168, 0.7)
                }
                c.drawPath(shapes.crack, st)
                st.strokeWidth = 1.2f
                st.color = fortuneArgb(120, 64, 18, 0.45)
                c.drawPath(shapes.crack, st)
            }
            c.restore()
        }
    }

    /**
     * Wide blurs (σ 18 to 44 dp) drawn in software at full density took seconds: they are drawn at
     * 1 px per dp into a layer that is then scaled over [full] (a blur that wide hides the difference).
     */
    private fun lowRes(c: Canvas, full: RectF, draw: (Canvas) -> Unit) {
        val k = 1f
        val bmp = Bitmap.createBitmap(max(1, (full.width() * k).roundToInt()), max(1, (full.height() * k).roundToInt()), Bitmap.Config.ARGB_8888)
        val s = Canvas(bmp)
        s.scale(k, k)
        s.translate(-full.left, -full.top)
        draw(s)
        c.drawBitmap(bmp, null, full, Paint(Paint.FILTER_BITMAP_FLAG))
        bmp.recycle()
    }

    /** The static backdrop at [l]: the dimmed order screen, the modal's paper card and the porcelain plate. */
    fun backdrop(l: FortuneLayout): Bitmap {
        val full = RectF(0f, 0f, l.w, l.h)
        return bake(full) { c ->
            val W = l.w
            val H = l.h
            c.drawRect(full, Paint().apply {
                shader = LinearGradient(0f, 0f, W * 0.3f, H, intArrayOf(fortuneHex(0x3A1A13), fortuneHex(0x24110D), fortuneHex(0x160B09)), null, Shader.TileMode.CLAMP)
            })
            lowRes(c, full) { s ->
                val warm = fortuneBlurPaint(fortuneArgb(232, 150, 70, 0.16), 22f)
                val red = fortuneBlurPaint(fortuneArgb(196, 60, 44, 0.16), 22f)
                for (i in 0 until 7) {
                    val x = (W * hash(i * 3.3)).toFloat()
                    val y = (20 + H * 0.9 * hash(i * 7.1)).toFloat()
                    val r = (26 + 40 * hash(i * 1.9)).toFloat()
                    s.drawCircle(x, y, r, if (i % 2 == 0) warm else red)
                }
            }
            c.drawRect(full, Paint().apply {
                shader = RadialGradient(W / 2, H * 0.45f, max(W, H) * 0.75f, intArrayOf(fortuneArgb(0, 0, 0, 0.0), fortuneArgb(10, 4, 3, 0.55)), null, Shader.TileMode.CLAMP)
            })

            val card = RectF(l.mx, l.mTop, l.mx + l.mw, l.mTop + l.mH)
            lowRes(c, full) { s -> s.translate(0f, 24f); s.drawRoundRect(card, 30f, 30f, fortuneBlurPaint(fortuneArgb(0, 0, 0, 0.42), 44f)) }
            c.save(); c.translate(0f, 2f); c.drawRoundRect(card, 30f, 30f, fortuneBlurPaint(fortuneArgb(0, 0, 0, 0.3), 4f)); c.restore()
            c.drawRoundRect(card, 30f, 30f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = LinearGradient(0f, l.mTop, W * 0.2f, l.mTop + l.mH, intArrayOf(fortuneHex(0xFCF6EA), fortuneHex(0xF7EDDA), fortuneHex(0xF0E2C8)), null, Shader.TileMode.CLAMP)
            })
            c.drawRoundRect(card, 30f, 30f, FortuneTexture.grainPaint(grain, 0.45))
            c.drawRoundRect(RectF(card.left + 0.5f, card.top + 0.5f, card.right - 0.5f, card.bottom - 0.5f), 29.5f, 29.5f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = 1f
                color = fortuneArgb(255, 255, 255, 0.7)
            })

            // Porcelain plate with a red rim line.
            val cx = l.cx
            val cy = l.cy + 62
            val rx = l.plateRx
            val plate = RectF(cx - rx, cy - 32, cx + rx, cy + 32)
            lowRes(c, full) { s -> s.translate(0f, 10f); s.drawOval(plate, fortuneBlurPaint(fortuneArgb(70, 40, 20, 0.22), 18f)) }
            c.save(); c.translate(0f, 1.5f); c.drawOval(plate, fortuneBlurPaint(fortuneArgb(70, 40, 20, 0.3), 2f)); c.restore()
            c.drawOval(plate, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = RadialGradient(cx, cy - 6, rx, intArrayOf(fortuneHex(0xFFFDF8), fortuneHex(0xF2ECE2), fortuneHex(0xDCD3C5)), null, Shader.TileMode.CLAMP)
            })
            c.drawOval(RectF(cx - rx + 10, cy - 26, cx + rx - 10, cy + 26), Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = 1.6f
                color = fortuneArgb(168, 50, 42, 0.55)
            })
            c.drawOval(RectF(cx - rx * 0.66f, cy - 18, cx + rx * 0.66f, cy + 16), Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = fortuneArgb(120, 90, 60, 0.07)
            })
        }
    }
}

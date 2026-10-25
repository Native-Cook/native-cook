package com.ngocdevv.nativecook.blocks.cards.perforatedcoupontear

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.BlendMode
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.os.Build
import com.ngocdevv.nativecook.blocks.cards.perforatedcoupontear.CouponMotion.CARD_H
import com.ngocdevv.nativecook.blocks.cards.perforatedcoupontear.CouponMotion.CORNER
import com.ngocdevv.nativecook.blocks.cards.perforatedcoupontear.CouponMotion.HOLE_GAP
import com.ngocdevv.nativecook.blocks.cards.perforatedcoupontear.CouponMotion.HOLE_R
import com.ngocdevv.nativecook.blocks.cards.perforatedcoupontear.CouponMotion.NOTCH_R
import com.ngocdevv.nativecook.blocks.cards.perforatedcoupontear.CouponMotion.hash
import com.ngocdevv.nativecook.shared.sigmaToRadius
import kotlin.math.ceil
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.sin

/*
 Paths and baked art of the coupon, ported from couponArt.ts and CouponParts.tsx. The tear line is
 one seeded polyline down the perforation: the body's right edge and the stub's left edge both
 follow it above the tear front (so the two halves fit exactly) and run straight below it.

 Everything that does not change with the tear is painted once, off the main thread, into bitmaps
 at the screen's density: the paper and stamp row, the wallet's back and front, the faces of the
 body and the stub (used as bitmap shaders to fill their per-frame paths), their shadows, a filed
 stub and the REDEEMED stamp. The grain and ink noise of RN's two RuntimeEffects are the same hash
 evaluated per cell on the CPU, so no AGSL is needed and every API level draws the same thing.
 */

internal object CouponPalette {
    val PAPER = intArrayOf(0xFFF6EFE3.toInt(), 0xFFEFE4D2.toInt(), 0xFFE6D5BE.toInt())
    val BODY = intArrayOf(0xFF4E3122.toInt(), 0xFF3E271B.toInt(), 0xFF2F1D14.toInt())
    val STUB = intArrayOf(0xFFD9A066.toInt(), 0xFFC98B4E.toInt(), 0xFFB37638.toInt())
    const val CREAM = 0xFFF4E8D4.toInt()
    const val ROAST = 0xFF3A2418.toInt()
    val LEATHER = intArrayOf(0xFF2F5A49.toInt(), 0xFF24473A.toInt(), 0xFF1A352B.toInt())
    val WALLET_BACK = intArrayOf(0xFF1F3F33.toInt(), 0xFF173126.toInt(), 0xFF10241C.toInt())
    val STAMP_DISC = intArrayOf(0xFF6B4430.toInt(), 0xFF5A3826.toInt(), 0xFF47291B.toInt())
    val INK = floatArrayOf(0.93f, 0.38f, 0.28f, 0.95f)

    fun rgba(r: Int, g: Int, b: Int, a: Float) = ((a * 255 + 0.5f).toInt() shl 24) or (r shl 16) or (g shl 8) or b
}

internal const val HOLE_Y0 = NOTCH_R + 4

/** y of every edge sample, three per hole gap, hole centres included; jitter is 0 on a hole. */
internal object CouponEdge {
    val ys: FloatArray
    val js: FloatArray

    init {
        val y = ArrayList<Float>()
        val j = ArrayList<Float>()
        val step = HOLE_GAP / 3
        val first = ceil(-HOLE_Y0 / step).toInt()
        val last = floor((CARD_H - HOLE_Y0) / step).toInt()
        y.add(0f); j.add(0f)
        for (k in first..last) {
            val yy = HOLE_Y0 + k * step
            if (yy <= 0.5f || yy >= CARD_H - 0.5f) continue
            y.add(yy)
            j.add(if (k % 3 == 0) 0f else ((hash(k * 3.7 + 1) - 0.5) * 3.6).toFloat())
        }
        y.add(CARD_H); j.add(0f)
        ys = y.toFloatArray()
        js = j.toFloatArray()
    }
}

/** The body: rounded on the left, its right edge on the tear line. */
internal fun couponBodyPath(b: Path, px: Float, front: Float) {
    val ys = CouponEdge.ys
    val js = CouponEdge.js
    b.reset()
    b.moveTo(CORNER, 0f)
    b.lineTo(px, 0f)
    for (i in 1 until ys.size - 1) b.lineTo(px + if (ys[i] < front) js[i] else 0f, ys[i])
    b.lineTo(px, CARD_H)
    b.lineTo(CORNER, CARD_H)
    b.quadTo(0f, CARD_H, 0f, CARD_H - CORNER)
    b.lineTo(0f, CORNER)
    b.quadTo(0f, 0f, CORNER, 0f)
    b.close()
}

/** The stub: rounded on the right, its left edge on the same tear line. */
internal fun couponStubPath(b: Path, px: Float, cw: Float, front: Float) {
    val ys = CouponEdge.ys
    val js = CouponEdge.js
    b.reset()
    b.moveTo(px, 0f)
    b.lineTo(cw - CORNER, 0f)
    b.quadTo(cw, 0f, cw, CORNER)
    b.lineTo(cw, CARD_H - CORNER)
    b.quadTo(cw, CARD_H, cw - CORNER, CARD_H)
    b.lineTo(px, CARD_H)
    for (i in ys.size - 2 downTo 1) b.lineTo(px + if (ys[i] < front) js[i] else 0f, ys[i])
    b.close()
}

/**
 * The torn edge's paper core (a light line along the jag) and its fibres: short hairs that stick
 * out of the torn part, [dir] = +1 for the body (into the gap) or −1 for the stub.
 */
internal fun couponFibrePath(b: Path, px: Float, front: Float, dir: Int) {
    val ys = CouponEdge.ys
    val js = CouponEdge.js
    b.reset()
    var started = false
    for (i in ys.indices) {
        if (ys[i] > front) break
        val x = px + js[i]
        if (!started) {
            b.moveTo(x, ys[i]); started = true
        } else b.lineTo(x, ys[i])
    }
    for (i in 1 until ys.size - 1) {
        if (ys[i] > front - 1) break
        val h = hash(i * 9.1 + if (dir > 0) 3 else 17)
        if (h < 0.35) continue
        val len = 1.4 + 2.6 * hash(i * 5.3 + dir)
        val ang = (hash(i * 2.9 + dir * 7) - 0.5) * 1.3
        val x = px + js[i]
        b.moveTo(x, ys[i])
        b.lineTo((x + dir * len * cos(ang)).toFloat(), (ys[i] + len * sin(ang)).toFloat())
    }
}

/** Perforation holes and the two notches, as one path. */
internal fun couponHolesPath(px: Float) = Path().apply {
    for (k in 0 until CouponMotion.holeCount) addCircle(px, HOLE_Y0 + k * HOLE_GAP, HOLE_R, Path.Direction.CW)
    addCircle(px, 0f, NOTCH_R, Path.Direction.CW)
    addCircle(px, CARD_H, NOTCH_R, Path.Direction.CW)
}

/** A barcode of horizontal bars across the stub, seeded. */
private fun barcodePath(x: Float, y: Float, w: Float, h: Float) = Path().apply {
    var yy = y
    var i = 0
    while (yy < y + h) {
        val t = 1 + floor(hash(i * 1.71 + 4) * 3).toFloat()
        if (i % 2 == 0) addRect(x, yy, x + w, yy + t, Path.Direction.CW)
        yy += t + if (i % 2 == 0) 0 else 1
        i += 1
    }
}

/** A small cup on a saucer, centred on (0, 0), about 14 dp wide (the stamp-card mark). */
private fun cupPath() = Path().apply {
    moveTo(-6f, -4f)
    lineTo(4f, -4f)
    lineTo(3.2f, 2.5f)
    quadTo(2.8f, 4.5f, 0.5f, 4.5f)
    lineTo(-3.5f, 4.5f)
    quadTo(-5.8f, 4.5f, -5.4f, 2.5f)
    close()
    addRoundRect(RectF(-8f, 5.6f, 6f, 7.4f), 0.9f, 0.9f, Path.Direction.CW)
}

/** Coffee-bean outlines tiled faintly over the body. */
private fun beansPath(w: Float, h: Float) = Path().apply {
    for (row in 0 until 7) for (col in 0 until 10) {
        val x = (col * 30 + (row % 2) * 15 + (hash(row * 11.0 + col) - 0.5) * 6).toFloat()
        val y = (row * 30 + 8 + (hash(row * 7.0 + col * 3) - 0.5) * 6).toFloat()
        if (x > w - 6 || y > h - 6) continue
        addOval(RectF(x - 5, y - 7, x + 5, y + 7), Path.Direction.CW)
        moveTo(x, y - 6)
        quadTo(x - 2.5f, y, x, y + 6)
    }
}

private fun fract(x: Double) = x - floor(x)
private fun h2(x: Double, y: Double) = fract(sin(x * 12.9898 + y * 78.233) * 43758.5453)

/**
 * GRAIN_SKSL on the CPU: one pixel per grain cell (1.5 cells a dp), grey n. Composite it scaled by
 * 1 / 1.5 without filtering, with soft light at alpha = amount.
 */
private fun grainBitmap(wDp: Float, hDp: Float, job: Job? = null): Bitmap {
    val w = ceil(wDp * 1.5f).toInt().coerceAtLeast(1)
    val h = ceil(hDp * 1.5f).toInt().coerceAtLeast(1)
    val px = IntArray(w * h)
    // The coarse term is constant over 3 × 3 cells: computed once per block of three columns and rows.
    val coarse = DoubleArray((w + 2) / 3)
    for (j in 0 until h) {
        job?.ensureActive()
        if (j % 3 == 0) {
            val j3 = floor(j / 3.0) + 31
            for (k in coarse.indices) coarse[k] = h2(k + 31.0, j3) * 0.35
        }
        val jd = j.toDouble()
        for (i in 0 until w) {
            val n = h2(i.toDouble(), jd) * 0.65 + coarse[i / 3]
            val g = (n * 255).toInt().coerceIn(0, 255)
            px[j * w + i] = (0xFF shl 24) or (g shl 16) or (g shl 8) or g
        }
    }
    return Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888)
}

/** Draws [grain] over what is already on [c] (in dp) with soft light (overlay before API 29). */
private fun softLight(c: Canvas, grain: Bitmap, amount: Float, x: Float = 0f, y: Float = 0f) {
    val p = Paint()
    p.isFilterBitmap = false
    p.alpha = (amount * 255).toInt()
    if (Build.VERSION.SDK_INT >= 29) p.blendMode = BlendMode.SOFT_LIGHT else p.xfermode = PorterDuffXfermode(PorterDuff.Mode.OVERLAY)
    c.save()
    c.translate(x, y)
    c.scale(1 / 1.5f, 1 / 1.5f)
    c.drawBitmap(grain, 0f, 0f, p)
    c.restore()
}

/** A bitmap of the art drawn at dp (x, y) and the density it was painted at. */
internal class CouponBake(val bitmap: Bitmap, val x: Float, val y: Float, val d: Float) {
    fun draw(c: Canvas, paint: Paint) {
        c.save()
        c.translate(x, y)
        c.scale(1 / d, 1 / d)
        c.drawBitmap(bitmap, 0f, 0f, paint)
        c.restore()
    }

    /** A shader that paints this bitmap where it was baked, for filling a per-frame path. */
    fun shader(): BitmapShader = BitmapShader(bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).apply {
        setLocalMatrix(Matrix().apply { setScale(1 / d, 1 / d); postTranslate(x, y) })
    }
}

/** Paints a CouponBake covering dp rect (x, y, w, h). */
private inline fun bake(x: Float, y: Float, w: Float, h: Float, d: Float, draw: (Canvas) -> Unit): CouponBake {
    val bw = ceil(w * d).toInt().coerceAtLeast(1)
    val bh = ceil(h * d).toInt().coerceAtLeast(1)
    val bmp = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888)
    val c = Canvas(bmp)
    c.scale(d, d)
    c.translate(-x, -y)
    draw(c)
    bmp.prepareToDraw()
    return CouponBake(bmp, x, y, d)
}

private fun fill(color: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }
private fun stroke(color: Int, width: Float) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    this.color = color
    style = Paint.Style.STROKE
    strokeWidth = width
}
private fun gradient(x0: Float, y0: Float, x1: Float, y1: Float, colors: IntArray) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    shader = LinearGradient(x0, y0, x1, y1, colors, null, Shader.TileMode.CLAMP)
}
private fun shadow(color: Int, blur: Float) = fill(color).apply { maskFilter = BlurMaskFilter(sigmaToRadius(blur), BlurMaskFilter.Blur.NORMAL) }

private fun font(size: Float, weight: Int, mono: Boolean = false) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    textSize = size
    typeface = when {
        mono -> Typeface.MONOSPACE
        Build.VERSION.SDK_INT >= 28 -> Typeface.create(Typeface.SANS_SERIF, weight, false)
        weight >= 600 -> Typeface.DEFAULT_BOLD
        else -> Typeface.DEFAULT
    }
}

/** Text with [tracking] dp between glyphs; [align] 0 left, 0.5 centre, 1 right of x; y is the baseline. */
private fun tracked(c: Canvas, text: String, x: Float, y: Float, paint: Paint, tracking: Float, color: Int, align: Float = 0f) {
    paint.letterSpacing = tracking / paint.textSize
    paint.color = color
    // Android spaces every glyph by half the tracking on each side; RN puts it only between glyphs.
    val total = paint.measureText(text) - tracking
    c.drawText(text, x - total * align - tracking / 2, y, paint)
}

private fun trackedWidth(text: String, paint: Paint, tracking: Float): Float {
    paint.letterSpacing = tracking / paint.textSize
    return paint.measureText(text) - tracking
}

/** The screen's layout in dp, as in PerforatedCouponTear.tsx. */
internal class CouponLayout(val w: Float, val h: Float, val top: Float, val bottom: Float) {
    val cardX = 16f
    val cardY = top + 186
    val cw = maxOf(200f, w - 32)
    val px = cw - CouponMotion.STUB_W
    val pocketTop = cardY + CARD_H + 78
    val pocketBottom = maxOf(pocketTop + 96, h - bottom - 96)
    val frontTop = pocketTop + 30
    val pocketCx = w / 2
    val geom = CouponMotion.Geom(
        cw.toDouble(), px.toDouble(), (px + 64).toDouble(), 30.0,
        (pocketCx + 43 - cardX).toDouble(), (frontTop - 22 - cardY).toDouble(), (w + 24).toDouble(), (frontTop - cardY).toDouble(),
    )
    val gx get() = geom.gx.toFloat()
    val gy get() = geom.gy.toFloat()
}

/** Everything painted once per layout. */
internal class CouponArt(
    val back: CouponBake,
    val front: CouponBake,
    val filedStub: CouponBake,
    val stubFace: CouponBake,
    val bodyFace: CouponBake,
    val bodyShadow: CouponBake,
    val stubShadowLo: CouponBake,
    val stubShadowHi: CouponBake,
    val stamp: CouponBake,
    val holes: Path,
)

private val labelFont = font(10f, 700)
private val tinyFont = font(9f, 700)
private val monoFont = font(12f, 400, mono = true)

/** The stub's face: caramel paper, barcode, the "tear" label and its number (no edge, no holes). */
private fun stubDecor(c: Canvas, px: Float) {
    c.drawPath(barcodePath(px + 46, 34f, 48f, 116f), fill(CouponPalette.rgba(58, 36, 24, 0.82f)))
    c.save()
    c.translate(px + 26, CARD_H / 2)
    c.rotate(-90f)
    tracked(c, "TEAR TO REDEEM", 0f, 0f, labelFont, 2.2f, CouponPalette.rgba(58, 36, 24, 0.78f), 0.5f)
    c.restore()
    tracked(c, "No. 0925", px + 46, 176f, monoFont, 0.4f, CouponPalette.rgba(58, 36, 24, 0.7f))
}

internal fun bakeCouponArt(l: CouponLayout, d: Float, job: Job? = null): CouponArt {
    val px = l.px
    val cw = l.cw
    val holes = couponHolesPath(px)
    val stubPaint = gradient(px, 0f, cw, CARD_H, CouponPalette.STUB)

    // The paper, its grain and the stamp row (screen coordinates).
    val walletLeft = 24f
    val walletW = l.w - 48
    // One full-screen grain serves the paper and the leather (both clipped to their own areas).
    val grain = grainBitmap(l.w, l.h, job)
    val back = bake(0f, 0f, l.w, l.h, d) { c ->
        c.drawRect(0f, 0f, l.w, l.h, gradient(0f, 0f, l.w * 0.4f, l.h, CouponPalette.PAPER))
        softLight(c, grain, 0.5f)
        // Ten filled stamps across the top of the card, each a coffee-brown disc with a cream cup.
        val y = l.top + 150
        val dd = min(28f, (cw - 9 * 7) / 10)
        val cup = cupPath()
        val cupPaint = fill(CouponPalette.CREAM)
        val handle = stroke(CouponPalette.CREAM, 1.3f)
        val shadowPaint = shadow(CouponPalette.rgba(60, 34, 20, 0.3f), 1.5f)
        for (i in 0 until 10) {
            val x = l.cardX + dd / 2 + i * (dd + (cw - 10 * dd) / 9)
            val r = dd / 2
            c.drawCircle(x, y + 1, r, shadowPaint)
            c.drawCircle(x, y, r, gradient(x, y - r, x, y + r, CouponPalette.STAMP_DISC))
            c.save()
            c.translate(x, y)
            val s = r / 14 + 0.05f
            c.scale(s, s)
            c.drawPath(cup, cupPaint)
            c.drawCircle(4.6f, -0.6f, 2.2f, handle)
            c.restore()
        }
        // The wallet's back panel, standing up behind the filed stubs.
        val rect = RectF(walletLeft, l.pocketTop, walletLeft + walletW, l.pocketBottom)
        c.save(); c.translate(0f, 14f); c.drawRoundRect(rect, 18f, 18f, shadow(CouponPalette.rgba(40, 26, 14, 0.22f), 28f)); c.restore()
        c.save(); c.translate(0f, 2f); c.drawRoundRect(rect, 18f, 18f, shadow(CouponPalette.rgba(40, 26, 14, 0.3f), 3f)); c.restore()
        c.drawRoundRect(rect, 18f, 18f, gradient(0f, l.pocketTop, 0f, l.pocketBottom, CouponPalette.WALLET_BACK))
    }

    // A filed stub: its half holes cut out, lying on leather with a shadow (stub-local coordinates).
    val torn = Path().also { couponStubPath(it, px, cw, CARD_H) }
    torn.op(holes, Path.Op.DIFFERENCE)
    val tornFibres = Path().also { couponFibrePath(it, px, CARD_H, -1) }
    val filedStub = bake(px - 24, -24f, CouponMotion.STUB_W + 48, CARD_H + 56, d) { c ->
        c.save(); c.translate(0f, 3f); c.drawPath(torn, shadow(CouponPalette.rgba(8, 18, 14, 0.5f), 6f)); c.restore()
        c.drawPath(torn, stubPaint)
        c.drawPath(torn, stroke(CouponPalette.rgba(255, 240, 215, 0.35f), 1f))
        c.drawPath(tornFibres, stroke(CouponPalette.rgba(255, 246, 230, 0.85f), 0.9f).apply { strokeCap = Paint.Cap.ROUND })
        stubDecor(c, px)
    }

    // The two old stubs peeking out of the wallet go into the back layer, clipped to its bottom.
    run {
        val c = Canvas(back.bitmap)
        c.scale(d, d)
        c.clipRect(0f, 0f, l.w, l.pocketBottom - 2)
        val p = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
        for ((dx, tilt) in listOf(-96f to -0.1f, -30f to 0.05f)) {
            c.save()
            c.translate(l.pocketCx + dx + 43, l.frontTop - 22 + 6)
            c.rotate(Math.toDegrees(tilt.toDouble()).toFloat())
            c.scale(CouponMotion.POCKET_SCALE.toFloat(), CouponMotion.POCKET_SCALE.toFloat())
            c.translate(-l.gx, -l.gy)
            filedStub.draw(c, p)
            c.restore()
        }
    }

    // The wallet's stitched front pocket with its curved mouth (card coordinates inside).
    val fTop = l.frontTop - 24
    val front = bake(0f, fTop, l.w, l.pocketBottom - fTop + 4, d) { c ->
        val left = walletLeft
        val r = left + walletW
        val fy = l.frontTop
        val bottom = l.pocketBottom
        val path = Path().apply {
            moveTo(left, fy + 8)
            quadTo(left + walletW / 2, fy + 26, r, fy + 8)
            lineTo(r, bottom - 16)
            quadTo(r, bottom, r - 16, bottom)
            lineTo(left + 16, bottom)
            quadTo(left, bottom, left, bottom - 16)
            close()
        }
        c.save(); c.translate(0f, -3f); c.drawPath(path, shadow(CouponPalette.rgba(10, 20, 16, 0.45f), 6f)); c.restore()
        // Leather + grain, painted on an opaque rectangle and then used to fill the pocket's shape.
        val texW = walletW + 2
        val texH = bottom - fy + 2
        val tex = bake(left - 1, fy - 1, texW, texH, d) { t ->
            t.drawRect(left - 1, fy - 1, left - 1 + texW, fy - 1 + texH, gradient(l.cardX, fy, l.cardX + (left - l.cardX) + walletW * 0.3f, bottom, CouponPalette.LEATHER))
            // The grain is in card coordinates, like RN's shader inside the card's group.
            t.save()
            t.clipRect(left - 1, fy - 1, left - 1 + texW, fy - 1 + texH)
            softLight(t, grain, 0.32f, l.cardX, l.cardY)
            t.restore()
        }
        c.drawPath(path, Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = tex.shader() })
        tex.bitmap.recycle()
        c.drawPath(path, stroke(CouponPalette.rgba(214, 190, 140, 0.55f), 1.2f).apply { pathEffect = DashPathEffect(floatArrayOf(5f, 4f), 0f) })
        tracked(c, "REWARDS WALLET", left + walletW / 2, bottom - 22, tinyFont, 2.4f, CouponPalette.rgba(214, 190, 140, 0.75f), 0.5f)
    }

    // The stub's face, used to fill its per-frame path.
    val stubFace = bake(px - 4, -1f, CouponMotion.STUB_W + 5, CARD_H + 2, d) { c ->
        c.drawRect(px - 4, -1f, cw + 1, CARD_H + 1, stubPaint)
        stubDecor(c, px)
    }

    // The body's face: espresso card, faint beans, grain, its copy.
    val bodyFace = bake(-1f, -1f, px + 5, CARD_H + 2, d) { c ->
        c.drawRect(-1f, -1f, px + 4, CARD_H + 1, gradient(0f, 0f, px, CARD_H, CouponPalette.BODY))
        c.drawPath(beansPath(px, CARD_H), stroke(CouponPalette.rgba(244, 232, 212, 0.06f), 1f))
        softLight(c, grainBitmap(px + 4, CARD_H + 1, job), 0.55f)
        c.drawRoundRect(RectF(0.5f, 0.5f, 0.5f + px + CORNER, CARD_H - 0.5f), CORNER, CORNER, stroke(CouponPalette.rgba(255, 236, 205, 0.14f), 1f))
        val soft = CouponPalette.rgba(244, 232, 212, 0.6f)
        tracked(c, "REWARD", 22f, 34f, labelFont, 1.8f, soft)
        tracked(c, "EXP 11/30", px - 20, 34f, labelFont, 1.4f, soft, 1f)
        tracked(c, "FREE", 19f, 102f, font(60f, 800), -1f, CouponPalette.CREAM)
        tracked(c, "Any drink, any size, any day", 22f, 126f, font(14f, 500), 0f, CouponPalette.rgba(244, 232, 212, 0.78f))
        c.drawLine(22f, 150f, px - 20, 150f, stroke(CouponPalette.rgba(244, 232, 212, 0.22f), 1f).apply { pathEffect = DashPathEffect(floatArrayOf(3f, 3f), 0f) })
        tracked(c, "CODE", 22f, 176f, tinyFont, 1.6f, CouponPalette.rgba(244, 232, 212, 0.55f))
        tracked(c, "BC-4471-0925", 22 + trackedWidth("CODE", tinyFont, 1.6f) + 8, 177f, monoFont, 0.6f, CouponPalette.CREAM)
    }

    // The body's two drop shadows (its shape at rest).
    val restBody = Path().also { couponBodyPath(it, px, NOTCH_R) }
    val bodyShadow = bake(-80f, -76f, px + 160, CARD_H + 186, d) { c ->
        c.save(); c.translate(0f, 16f); c.drawPath(restBody, shadow(CouponPalette.rgba(46, 26, 14, 0.26f), 26f)); c.restore()
        c.save(); c.translate(0f, 1.5f); c.drawPath(restBody, shadow(CouponPalette.rgba(46, 26, 14, 0.35f), 3f)); c.restore()
    }

    // The lifted stub's soft shadow at the lowest and highest blur (cross-faded by the lift).
    val stubShape = Path().also { couponStubPath(it, px, cw, 0f) }
    fun stubShadow(blur: Float) = bake(px - 40, -40f, CouponMotion.STUB_W + 80, CARD_H + 80, d) { c ->
        c.drawPath(stubShape, shadow(0xFF2A160C.toInt(), blur))
    }

    // The REDEEMED stamp: grainy ink that skips where the paper's tooth did not take it.
    val stamp = bake(-88f, -28f, 176f, 56f, d) { c ->
        val white = android.graphics.Color.WHITE
        c.drawRoundRect(RectF(-84f, -24f, 84f, 24f), 8f, 8f, stroke(white, 3f))
        c.drawRoundRect(RectF(-78f, -18f, 78f, 18f), 5f, 5f, stroke(white, 1.2f))
        tracked(c, "REDEEMED", 0f, 7.5f, font(21f, 800), 1.2f, white, 0.5f)
    }
    job?.ensureActive()
    inkStamp(stamp)

    return CouponArt(back, front, filedStub, stubFace, bodyFace, bodyShadow, stubShadow(3f), stubShadow(12f), stamp, holes)
}

private fun smoothstep(a: Double, b: Double, x: Double): Double {
    val t = ((x - a) / (b - a)).coerceIn(0.0, 1.0)
    return t * t * (3 - 2 * t)
}

/** INK_SKSL on the CPU: the white mask becomes ink whose alpha follows the paper's tooth. */
private fun inkStamp(b: CouponBake) {
    val bmp = b.bitmap
    val w = bmp.width
    val h = bmp.height
    val px = IntArray(w * h)
    bmp.getPixels(px, 0, w, 0, 0, w, h)
    val ink = CouponPalette.INK
    val r = (ink[0] * 255).toInt()
    val g = (ink[1] * 255).toInt()
    val bl = (ink[2] * 255).toInt()
    for (j in 0 until h) {
        val y = b.y + (j + 0.5) / b.d
        for (i in 0 until w) {
            val m = px[j * w + i] ushr 24
            if (m == 0) continue
            val x = b.x + (i + 0.5) / b.d
            val a = smoothstep(0.08, 0.5, h2(floor(x * 1.2), floor(y * 1.2)) * 0.6 + h2(floor(x * 0.25) + 7, floor(y * 0.25) + 7) * 0.6)
            val al = (ink[3] * a * m).toInt().coerceIn(0, 255)
            px[j * w + i] = if (al == 0) 0 else (al shl 24) or (r shl 16) or (g shl 8) or bl
        }
    }
    bmp.setPixels(px, 0, w, 0, 0, w, h)
}

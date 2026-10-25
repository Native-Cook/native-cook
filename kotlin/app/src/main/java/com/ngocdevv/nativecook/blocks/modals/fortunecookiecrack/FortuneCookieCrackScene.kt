package com.ngocdevv.nativecook.blocks.modals.fortunecookiecrack

import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.os.Build
import com.ngocdevv.nativecook.blocks.modals.fortunecookiecrack.FortuneCookieCrackMotion.CRUMBS
import com.ngocdevv.nativecook.shared.sigmaToRadius
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * What moves, drawn every frame in dp (the caller scales the canvas by the density): the halves'
 * shadows on the plate, the crumbs, the cookie pictures with the running crack, and the slip with
 * the fortune. Everything heavy is a bitmap from [FortuneArt]; the paints are made once.
 */
internal class FortuneScene(private val art: FortuneArt, private val l: FortuneLayout) {
    private val bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val crumbs = Path()
    private val crumb = DoubleArray(3)
    private val crumbPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = fortuneHex(0xB9782F) }
    private val crumbShadow = blurred(fortuneArgb(80, 40, 10, 0.45), 0.8f)
    private val crackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeJoin = Paint.Join.ROUND
    }
    private val slipH = FortuneLayout.SLIP_H
    private val paperPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = LinearGradient(0f, -slipH / 2, 0f, slipH / 2, intArrayOf(fortuneHex(0xFFFCF4), fortuneHex(0xF8F1E2), fortuneHex(0xEEE3CC)), null, Shader.TileMode.CLAMP)
    }
    private val paperShadowWide = blurred(fortuneArgb(70, 40, 20, 0.22), 12f)
    private val paperShadowTight = blurred(fortuneArgb(70, 40, 20, 0.25), 1.5f)
    private val grainPaint = FortuneTexture.grainPaint(art.grain, 0.7)
    /** A rolled cylinder: lit just left of its middle, darker at both edges (centred on x = 0). */
    private val rollPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = LinearGradient(
            -8f, 0f, 8f, 0f,
            intArrayOf(fortuneHex(0xD9CCB1), fortuneHex(0xFFFBF1), fortuneHex(0xE6D9BF), fortuneHex(0xBFAF91)),
            floatArrayOf(0f, 0.35f, 0.7f, 1f), Shader.TileMode.CLAMP,
        )
    }
    private val rollShadow = blurred(fortuneArgb(70, 40, 20, 0.3), 3f)
    private val rect = RectF()
    private val textPaint: Paint
    private val fontSize: Float

    init {
        val italic = Typeface.create(Typeface.SERIF, Typeface.ITALIC)
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = italic
            textSize = 18f
            textAlign = Paint.Align.CENTER
            color = fortuneHex(0xA8322A)
        }
        // The fortune's italic serif, shrunk if a line would not fit the slip.
        val widest = FORTUNE.maxOf { p.measureText(it) }
        if (widest > l.slipW - 56) p.textSize = floor(18 * (l.slipW - 56) / widest)
        textPaint = p
        fontSize = p.textSize
    }

    /** A blurred fill; hardware canvases honour mask filters from API 28, older ones draw nothing. */
    private fun blurred(color: Int, sigma: Float) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) maskFilter = BlurMaskFilter(sigmaToRadius(sigma), BlurMaskFilter.Blur.NORMAL)
    }
    private val shadows = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P

    private fun alpha(a: Double) = (a * 255).roundToInt().coerceIn(0, 255)

    private fun Canvas.bitmap(bmp: Bitmap, dst: RectF, a: Double = 1.0) {
        bitmapPaint.alpha = alpha(a)
        drawBitmap(bmp, null, dst, bitmapPaint)
    }

    fun draw(c: Canvas, p: FortuneCookieCrackMotion.Pose) {
        val cx = l.cx
        val cy = l.cy

        // Soft shadows of the two halves on the plate, sliding apart as they open.
        for (d in floatArrayOf(-1f, 1f)) {
            val x = cx + d * (60 + 34 * p.open.toFloat())
            val y = cy + 58
            val s = art.shadowRect
            rect.set(x + s.left, y + s.top, x + s.right, y + s.bottom)
            c.bitmap(art.shadow, rect)
        }

        // Crumbs that fly out of the crack and land on the plate.
        if (p.crumbT >= 0 && p.crumbA > 0) {
            crumbs.rewind()
            for (i in 0 until CRUMBS) {
                if (FortuneCookieCrackMotion.crumbAt(i, p.crumbT, 58.0, crumb)) {
                    crumbs.addCircle(cx + crumb[0].toFloat(), cy + crumb[1].toFloat(), crumb[2].toFloat(), Path.Direction.CW)
                }
            }
            if (shadows) {
                crumbShadow.alpha = alpha(0.45 * p.crumbA)
                c.save()
                c.translate(0f, 0.8f)
                c.drawPath(crumbs, crumbShadow)
                c.restore()
            }
            crumbPaint.alpha = alpha(p.crumbA)
            c.drawPath(crumbs, crumbPaint)
        }

        // The cookie: whole until the snap (no seam at rest), then two halves that swing apart.
        c.save()
        c.translate(cx, cy + p.bob.toFloat())
        c.rotate(Math.toDegrees(p.tilt + p.shake).toFloat())
        c.scale((p.sx * FortuneLayout.COOKIE_SCALE).toFloat(), (p.sy * FortuneLayout.COOKIE_SCALE).toFloat())
        val bounds = FortuneCookieShapes.bounds
        val o = p.open.toFloat()
        if (o <= 0.001f) {
            c.bitmap(art.whole, bounds)
        } else {
            for (d in floatArrayOf(-1f, 1f)) {
                val px = d * FortuneCookieShapes.PIVOT_X
                val py = FortuneCookieShapes.PIVOT_Y
                c.save()
                c.translate(d * 24 * o, 4 * o)
                c.translate(px, py)
                c.rotate(Math.toDegrees(d * 0.34 * o.toDouble()).toFloat())
                c.translate(-px, -py)
                c.bitmap(if (d < 0) art.left else art.right, bounds)
                c.restore()
            }
        }
        val crackOpacity = max(0.0, 1 - p.open * 2.5)
        if (crackOpacity > 0) {
            c.bitmap(art.crease, bounds, crackOpacity)
            if (p.crack > 0) {
                val f = p.crack.toFloat()
                crackPaint.strokeWidth = 1.6f
                crackPaint.color = fortuneArgb(70, 32, 8, 0.85 * crackOpacity)
                c.drawPath(art.crackTrim.upTo(f), crackPaint)
                crackPaint.strokeWidth = 1.1f
                crackPaint.color = fortuneArgb(70, 32, 8, 0.7 * crackOpacity)
                c.drawPath(art.branchTrim.upTo(f), crackPaint)
            }
        }
        c.restore()

        // The slip: a little roll that comes out of the gap, drops into place and unrolls both ways.
        val slipOpacity = min(1.0, p.emerge * 3)
        if (slipOpacity <= 0) return
        val half = (7 + (l.slipW / 2 - 7) * p.unroll).toFloat()
        val top = min(cy - 46, l.slipY) - slipH
        val layer = if (slipOpacity < 1) {
            rect.set(cx - l.slipW / 2 - 40, top, cx + l.slipW / 2 + 40, l.slipY + slipH + 20)
            c.saveLayerAlpha(rect, alpha(slipOpacity))
        } else {
            c.save()
        }
        c.translate(cx, (cy - 46 + (l.slipY - (cy - 46)) * p.drop).toFloat())
        val sc = (0.58 + 0.42 * p.drop).toFloat()
        c.scale(sc, sc)
        rect.set(-half, -slipH / 2, half, slipH / 2)
        if (shadows) {
            c.save(); c.translate(0f, 6f); c.drawRect(rect, paperShadowWide); c.restore()
            c.save(); c.translate(0f, 1f); c.drawRect(rect, paperShadowTight); c.restore()
        }
        c.drawRect(rect, paperPaint)
        c.drawRect(rect, grainPaint)
        for (x in floatArrayOf(-half - 7, half - 7)) {
            rect.set(x, -slipH / 2 - 3, x + 14, slipH / 2 + 3)
            if (shadows) {
                c.save(); c.translate(0f, 2f); c.drawRoundRect(rect, 7f, 7f, rollShadow); c.restore()
            }
            c.save()
            c.translate(x + 7, 0f)
            rect.offset(-(x + 7), 0f)
            c.drawRoundRect(rect, 7f, 7f, rollPaint)
            c.restore()
        }
        c.restoreToCount(layer)

        // The fortune, revealed by the unrolling paper (not scaled with the slip: it shows once it has landed).
        if (p.drop <= 0.98) return
        c.save()
        c.clipRect(cx - half + 6, l.slipY - slipH, cx - half + 6 + max(0f, half * 2 - 12), l.slipY + slipH)
        for (k in FORTUNE.indices) {
            val y = l.slipY - 4 + (k - 0.5f) * fontSize * 1.35f + fontSize * 0.36f
            c.drawText(FORTUNE[k], cx, y, textPaint)
        }
        c.restore()
    }

    private companion object {
        val FORTUNE = arrayOf("You will find joy in", "an unplanned detour.")
    }
}

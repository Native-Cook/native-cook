package com.ngocdevv.nativecook.blocks.pickers.paintmixerpicker

import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.os.Build
import com.ngocdevv.nativecook.shared.sigmaToRadius
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

/*
 Drawing for Paint Mixer Picker (a port of paintMixerArt.ts). Made once into bitmaps: the linen
 table with grain and the wall card's shadows, and the card's room (floor, skirting, a framed
 print, a sideboard, a lamp) over a wall left transparent for the mixed colour; a soft disc used
 for every blurred shadow. Drawn per frame: the wall colour, the five paint tins (lifted when
 picked), drops in the air, splashes, the stirring stick, and the colour's name, code and swatch.
 */

private const val INK = 0xFF2B2620.toInt()
private const val INK_SOFT = 0xFF7A7064.toInt()
internal const val PAINT_CARD_R = 22f
private const val PIF = PI.toFloat()

private fun argb(a: Float, r: Int, g: Int, b: Int) = ((a.coerceIn(0f, 1f) * 255).roundToInt() shl 24) or (r shl 16) or (g shl 8) or b
private fun c(hex: Long) = hex.toInt()
private fun fill(color: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }
private fun blurred(color: Int, sigma: Float) = fill(color).apply { maskFilter = BlurMaskFilter(sigmaToRadius(sigma), BlurMaskFilter.Blur.NORMAL) }
private fun strokeP(color: Int, width: Float) = fill(color).apply {
    style = Paint.Style.STROKE
    strokeWidth = width
}

private fun weighted(base: Typeface, weight: Int): Typeface =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        Typeface.create(base, weight, false)
    } else {
        Typeface.create(base, if (weight >= 600) Typeface.BOLD else Typeface.NORMAL)
    }

internal class PaintFonts {
    val label = fill(INK_SOFT).apply { typeface = weighted(Typeface.SANS_SERIF, 600); textSize = 11f }
    val name = fill(INK).apply { typeface = weighted(Typeface.SANS_SERIF, 700); textSize = 22f }
    val code = fill(INK_SOFT).apply { typeface = weighted(Typeface.MONOSPACE, 500); textSize = 12f }
    val chip = fill(INK).apply { typeface = weighted(Typeface.SANS_SERIF, 600); textSize = 11f }
}

internal fun paintCardRect(g: PaintGeo) = RectF(24f, g.cardY, g.w - 24, g.cardY + g.cardH)

private fun screenBitmap(g: PaintGeo, density: Float, draw: (Canvas) -> Unit): Bitmap =
    Bitmap.createBitmap(max(1, (g.w * density).roundToInt()), max(1, (g.h * density).roundToInt()), Bitmap.Config.ARGB_8888).also {
        val cv = Canvas(it)
        cv.scale(density, density)
        draw(cv)
    }

/** Table, grain, the linen weave and the card's two shadows. */
internal fun paintTableBitmap(g: PaintGeo, density: Float) = screenBitmap(g, density) { cv ->
    cv.drawRect(0f, 0f, g.w, g.h, Paint().apply {
        shader = LinearGradient(0f, 0f, 0f, g.h, intArrayOf(c(0xFFF3EDE3), c(0xFFEBE3D6), c(0xFFE2D8C8)), floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP)
    })
    // Fine grain (Skia: fractal noise at 0.85, 5 %): one grey cell per dp.
    val gw = ceil(g.w).toInt() + 1
    val gh = ceil(g.h).toInt() + 1
    val rnd = Random(7)
    val px = IntArray(gw * gh) {
        val v = rnd.nextInt(256)
        (0xFF shl 24) or (v shl 16) or (v shl 8) or v
    }
    val grain = Bitmap.createBitmap(px, gw, gh, Bitmap.Config.ARGB_8888)
    cv.drawBitmap(grain, null, RectF(0f, 0f, gw.toFloat(), gh.toFloat()), Paint().apply { alpha = 13 })
    grain.recycle()
    // A faint linen weave.
    val weave = fill(argb(0.035f, 120, 100, 70))
    var y = 0f
    while (y < g.h) {
        cv.drawRect(0f, y, g.w, y + 1, weave)
        y += 3
    }
    val card = paintCardRect(g)
    cv.save()
    cv.translate(0f, 14f)
    cv.drawRoundRect(card, PAINT_CARD_R, PAINT_CARD_R, blurred(argb(0.16f, 70, 52, 30), 18f))
    cv.restore()
    cv.save()
    cv.translate(0f, 1.5f)
    cv.drawRoundRect(card, PAINT_CARD_R, PAINT_CARD_R, blurred(argb(0.22f, 70, 52, 30), 2f))
    cv.restore()
}

/** The room in the card, over a transparent wall that the mixed colour shows through. */
internal fun paintRoomBitmap(g: PaintGeo, fonts: PaintFonts, density: Float) = screenBitmap(g, density) { cv ->
    val x0 = 24f
    val y0 = g.cardY
    val w = g.w - 48
    val h = g.cardH
    val u = g.u
    val card = paintCardRect(g)
    cv.save()
    cv.clipPath(Path().apply { addRoundRect(card, PAINT_CARD_R, PAINT_CARD_R, Path.Direction.CW) })
    // Light from the lamp on the right, darker corners.
    cv.drawRect(x0, y0, x0 + w, y0 + h, Paint().apply {
        shader = RadialGradient(
            x0 + w * 0.8f, y0 + h * 0.35f, w * 0.7f,
            intArrayOf(argb(0.30f, 255, 248, 232), argb(0.08f, 255, 248, 232), argb(0.16f, 40, 30, 20)),
            floatArrayOf(0f, 0.45f, 1f),
            Shader.TileMode.CLAMP,
        )
    })
    // Floor and skirting.
    val floorY = y0 + h * 0.76f
    cv.drawRect(x0, floorY, x0 + w, floorY + h * 0.24f, Paint().apply {
        shader = LinearGradient(0f, floorY, 0f, y0 + h, intArrayOf(c(0xFFC29466), c(0xFFA57A52), c(0xFF80593A)), floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)
    })
    val plank = strokeP(argb(0.35f, 70, 44, 24), 0.8f)
    for (i in -6..6) cv.drawLine(x0 + w / 2 + i * 26 * u, floorY, x0 + w / 2 + i * 44 * u, y0 + h, plank)
    cv.drawRect(x0, floorY - 7 * u, x0 + w, floorY, fill(c(0xFFF4EFE6)))
    cv.drawRect(x0, floorY, x0 + w, floorY + 3 * u, fill(argb(0.22f, 40, 26, 14)))
    // A framed print.
    val fx = x0 + 34 * u
    val fy = y0 + 26 * u
    val fw = 70 * u
    val fh = 52 * u
    cv.drawRect(fx + 2, fy + 5, fx + 2 + fw, fy + 5 + fh, blurred(argb(0.25f, 30, 20, 10), 4f))
    cv.drawRect(fx, fy, fx + fw, fy + fh, fill(c(0xFF3A322B)))
    cv.drawRect(fx + 3, fy + 3, fx + fw - 3, fy + fh - 3, fill(c(0xFFF7F3EA)))
    cv.drawCircle(fx + fw * 0.4f, fy + fh * 0.55f, 11 * u, fill(c(0xFFC0704F)))
    cv.drawCircle(fx + fw * 0.6f, fy + fh * 0.45f, 8 * u, fill(c(0xFF2F4858)))
    // A sideboard with a vase.
    val sx = x0 + w * 0.42f
    val sw = w * 0.34f
    val sy = floorY - 34 * u
    cv.drawRoundRect(RectF(sx + 3, sy + 6, sx + 3 + sw, sy + 6 + 30 * u), 3f, 3f, blurred(argb(0.25f, 30, 20, 10), 5f))
    cv.drawRoundRect(RectF(sx, sy, sx + sw, sy + 30 * u), 3f, 3f, fill(c(0xFF6E5440)).apply {
        shader = LinearGradient(0f, sy, 0f, sy + 30 * u, c(0xFF836550), c(0xFF5E4636), Shader.TileMode.CLAMP)
    })
    cv.drawRect(sx + sw / 2 - 0.5f, sy + 4 * u, sx + sw / 2 + 0.5f, sy + 26 * u, fill(argb(0.35f, 30, 20, 10)))
    for (lx in floatArrayOf(sx + 6 * u, sx + sw - 8 * u)) cv.drawRect(lx, sy + 30 * u, lx + 2 * u, sy + 37 * u, fill(c(0xFF4A3628)))
    val vx = sx + sw * 0.25f
    cv.drawRoundRect(RectF(vx - 7 * u, sy - 22 * u, vx + 7 * u, sy), 6 * u, 6 * u, fill(c(0xFFE9E2D6)))
    val stem = strokeP(c(0xFF5E6B48), 1.2f).apply { strokeCap = Paint.Cap.ROUND }
    for ((dx, dy) in arrayOf(-12f to -48f, 4f to -56f, 14f to -42f)) {
        cv.drawPath(Path().apply {
            moveTo(vx, sy - 20 * u)
            quadTo(vx + dx * 0.3f * u, sy + dy * 0.6f * u, vx + dx * u, sy + dy * u)
        }, stem)
        cv.drawOval(RectF(vx + dx * u - 3 * u, sy + dy * u - 2 * u, vx + dx * u + 3 * u, sy + dy * u + 2 * u), fill(c(0xFF6F7F55)))
    }
    // A floor lamp on the right.
    val lx = x0 + w * 0.86f
    cv.drawLine(lx, floorY + 4 * u, lx, y0 + 40 * u, strokeP(c(0xFF3A322B), 1.6f))
    cv.drawPath(Path().apply {
        moveTo(lx - 9 * u, y0 + 24 * u)
        lineTo(lx + 9 * u, y0 + 24 * u)
        lineTo(lx + 14 * u, y0 + 42 * u)
        lineTo(lx - 14 * u, y0 + 42 * u)
        close()
    }, fill(c(0xFFF1E7D4)))
    cv.drawOval(RectF(lx - 30 * u, y0 + 36 * u, lx + 30 * u, y0 + 76 * u), blurred(argb(0.35f, 255, 236, 190), 14f))
    // The room's tag.
    val tag = "Living room · North light"
    val tw = fonts.chip.measureText(tag) + 20
    cv.drawRoundRect(RectF(x0 + w - tw - 12, y0 + 12, x0 + w - 12, y0 + 34), 11f, 11f, fill(argb(0.72f, 255, 252, 246)))
    cv.drawText(tag, x0 + w - tw - 2, y0 + 27, fonts.chip)
    cv.restore()
    // Inner edge highlight.
    cv.drawRoundRect(RectF(24.5f, g.cardY + 0.5f, g.w - 24.5f, g.cardY + g.cardH - 0.5f), PAINT_CARD_R - 0.5f, PAINT_CARD_R - 0.5f, strokeP(argb(0.35f, 255, 255, 255), 1f))
}

/** A soft black disc of radius 1 with a blur of `sigma` radii, tinted and scaled per draw. */
private class SoftDisc(sigmaRel: Float) {
    private val pad = 1 + 3 * sigmaRel
    val bitmap: Bitmap = Bitmap.createBitmap(96, 96, Bitmap.Config.ALPHA_8).also {
        val cv = Canvas(it)
        val k = 48f / pad
        cv.scale(k, k)
        cv.translate(pad, pad)
        cv.drawCircle(0f, 0f, 1f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF000000.toInt()
            if (sigmaRel > 0) maskFilter = BlurMaskFilter(sigmaToRadius(sigmaRel * k) / k, BlurMaskFilter.Blur.NORMAL)
        })
    }
    private val dst = RectF()

    /** A blurred disc of radius r and blur sigma ≈ the one this was made for, in `paint`'s colour. */
    fun draw(c: Canvas, x: Float, y: Float, r: Float, paint: Paint) {
        val e = r * pad
        dst.set(x - e, y - e, x + e, y + e)
        c.drawBitmap(bitmap, null, dst, paint)
    }
}

private fun lighten(color: Int, k: Float): Int {
    fun f(v: Int) = (if (k >= 0) v + (255 - v) * k else v * (1 + k)).roundToInt().coerceIn(0, 255)
    return (0xFF shl 24) or (f((color shr 16) and 0xFF) shl 16) or (f((color shr 8) and 0xFF) shl 8) or f(color and 0xFF)
}

private fun rollEase(x: Float): Float {
    val k = paintClamp01(x)
    return 1 - (1 - k) * (1 - k) * (1 - k)
}

/** The colour's label: current and previous name, seconds since it changed, the colour before. */
internal class PaintLabel(var cur: Int = 0, var prev: Int = 0, var since: Float = 1f, var prevRgb: FloatArray = PAINT_BASE_RGB.copyOf()) {
    fun set(cur: Int, prev: Int, since: Float, prevRgb: FloatArray) {
        this.cur = cur
        this.prev = prev
        this.since = since
        this.prevRgb = prevRgb
    }
}

/** Tins, drops, splashes, the stick, and the colour's name, code and swatch. */
internal class PaintDynamicArt(private val g: PaintGeo, private val fonts: PaintFonts) {
    // Soft discs for the shadows: a blur of about a fifth, a tenth and a twentieth of the radius.
    private val softWide = SoftDisc(0.3f)
    private val softTight = SoftDisc(0.07f)
    private val tint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
    private val p = FloatArray(2)
    private val potPaints = Array(PAINT_POTS.size) { i -> fill(PAINT_POTS[i].color) }
    private val tin = fill(c(0xFFD8D4CD))
    private val tinInner = fill(argb(0.35f, 60, 50, 40))
    private val shine = fill(argb(0.38f, 255, 255, 255))
    private val paintPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val dropPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val dropShine = fill(argb(0.75f, 255, 255, 255))
    private val wood = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ring = strokeP(argb(0.45f, 110, 78, 44), 0.8f)
    private val swatch = fill(0)
    private val swatchEdge = strokeP(argb(0.7f, 255, 255, 255), 1.5f)
    private val text = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()

    private fun shadow(c: Canvas, disc: SoftDisc, x: Float, y: Float, r: Float, color: Int) {
        tint.color = color
        disc.draw(c, x, y, r, tint)
    }

    /** A glossy paint drop of pot i, h = height towards the camera (0 … 1). */
    private fun drop(c: Canvas, x: Float, y: Float, h: Float, pot: Int) {
        val u = g.u
        val col = PAINT_POTS[pot].color
        val s = 1 + 0.45f * h
        shadow(c, softWide, x + 10 * h * u, y + 16 * h * u, 8 * u, argb(0.28f * (1 - 0.45f * h), 40, 30, 20))
        val r = 8 * u * s
        dropPaint.shader = RadialGradient(x - r * 0.35f, y - r * 0.4f, r * 1.5f, intArrayOf(lighten(col, 0.35f), col, lighten(col, -0.35f)), floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)
        c.drawCircle(x, y, r, dropPaint)
        c.drawCircle(x - r * 0.35f, y - r * 0.4f, r * 0.22f, dropShine)
    }

    fun draw(c: Canvas, m: PaintMix, shown: FloatArray, label: PaintLabel) {
        val u = g.u
        for (i in PAINT_POTS.indices) {
            paintPotCenter(g, i, p)
            val px = p[0]
            val py = p[1]
            val l = m.lift[i]
            val r = g.potR * (1 + 0.06f * l)
            shadow(c, softWide, px + 2 * u, py + (4 + 6 * l) * u, r, argb(0.20f, 60, 44, 26))
            shadow(c, softTight, px, py + 1.5f, r, argb(0.28f, 60, 44, 26))
            tin.shader = LinearGradient(px - r, py - r, px + r, py + r, intArrayOf(c(0xFFF6F4F0), c(0xFFB9B3AA), c(0xFFE8E5DF), c(0xFF9C968D)), floatArrayOf(0f, 0.4f, 0.65f, 1f), Shader.TileMode.CLAMP)
            c.drawCircle(px, py, r, tin)
            c.drawCircle(px, py, r * 0.86f, tinInner)
            val col = PAINT_POTS[i].color
            paintPaint.shader = RadialGradient(px - r * 0.3f, py - r * 0.3f, r, intArrayOf(lighten(col, 0.18f), col, lighten(col, -0.2f)), floatArrayOf(0f, 0.6f, 1f), Shader.TileMode.CLAMP)
            c.drawCircle(px, py, r * 0.8f, paintPaint)
            rect.set(px - r * 0.48f, py - r * 0.55f, px + r * 0.02f, py - r * 0.29f)
            c.drawOval(rect, shine)
            // A drip over the rim.
            c.drawCircle(px + r * 0.62f, py + r * 0.58f, r * 0.16f, potPaints[i])
            val name = PAINT_POTS[i].name
            c.drawText(name, px - fonts.label.measureText(name) / 2, py + r + 16, fonts.label)
        }

        for (s in m.splashes) {
            val k = min(1f, s.age / 0.45f)
            val e = 1 - (1 - k) * (1 - k) * (1 - k)
            for (j in 0 until 7) {
                val a = (j / 7f) * PIF * 2 + j * 0.37f
                val dist = (6 + (22 + (j % 3) * 7) * e) * u
                c.drawCircle(s.x + cos(a) * dist, s.y + sin(a) * dist, 2.8f * u * (1 - k), potPaints[s.pot])
            }
        }

        if (m.stick[2] > 0.01f) {
            val a = m.stick[2]
            val sx = m.stick[0]
            val sy = m.stick[1]
            shadow(c, softWide, sx + 7 * u, sy + 12 * u, 9 * u, argb(0.3f * a, 40, 28, 14))
            wood.shader = RadialGradient(sx - 3 * u, sy - 3 * u, 12 * u, intArrayOf(c(0xFFE6CBA0), c(0xFFC9A577), c(0xFF9A7448)), floatArrayOf(0f, 0.6f, 1f), Shader.TileMode.CLAMP)
            wood.alpha = (a * 255).roundToInt().coerceIn(0, 255)
            c.drawCircle(sx, sy, 9 * u * (0.9f + 0.1f * a), wood)
            ring.alpha = (0.45f * a * 255).roundToInt().coerceIn(0, 255)
            c.drawCircle(sx, sy, 5 * u, ring)
            c.drawCircle(sx, sy, 2.4f * u, ring)
        }

        for (f in m.flights) drop(c, f.x, f.y, f.h, f.pot)

        // The colour: swatch, name, code and light reflectance.
        val py = g.panelY
        shadow(c, softWide, 46f, py + 22, 22f, argb(0.22f, 60, 44, 26))
        swatch.color = paintRgbInt(shown)
        c.drawCircle(46f, py + 22, 22f, swatch)
        c.drawCircle(46f, py + 22, 20.5f, swatchEdge)
        // A vertical roll on change: the old line slides up 10 dp and fades, the new one rises from
        // 10 dp below 60 ms later (ease-out, 260 ms each), both clipped to their line.
        val tx = 82f
        val out = rollEase(label.since / 0.26f)
        val inn = rollEase((label.since - 0.06f) / 0.26f)
        line(c, PAINT_NAMES[label.prev], PAINT_NAMES[label.cur], fonts.name, INK, py + 22, 28f, tx, out, inn)
        line(c, codeLine(label.prevRgb), codeLine(shown), fonts.code, INK_SOFT, py + 42, 16f, tx, out, inn)
    }

    private fun codeLine(rgb: FloatArray) = "${paintToHex(rgb)}   ·   LRV ${paintLrv(rgb)}"

    private fun line(c: Canvas, before: String, after: String, font: Paint, color: Int, base: Float, lh: Float, tx: Float, out: Float, inn: Float) {
        c.save()
        c.clipRect(tx - 2, base - lh * 0.82f, tx - 2 + (g.w - tx - 20), base - lh * 0.82f + lh)
        text.typeface = font.typeface
        text.textSize = font.textSize
        if (out < 1) {
            text.color = color
            text.alpha = ((1 - out) * 255).roundToInt()
            c.drawText(before, tx, base - 10 * out, text)
        }
        if (inn > 0) {
            text.color = color
            text.alpha = (inn * 255).roundToInt()
            c.drawText(after, tx, base + 10 * (1 - inn), text)
        }
        c.restore()
    }
}

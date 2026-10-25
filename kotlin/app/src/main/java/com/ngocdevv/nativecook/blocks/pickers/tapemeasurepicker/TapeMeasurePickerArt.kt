package com.ngocdevv.nativecook.blocks.pickers.tapemeasurepicker

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.os.Build
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

/*
 The tape measure, seen from above (same art as tapeMeasureArt.ts): a rubber-armoured case with a
 yellow lacquered face, chrome screws and a belt clip at the left; the yellow tape runs out of its
 mouth to the right, cupped, with millimetre ticks on both edges and a number at every fifth
 centimetre (every tenth in red). A red hairline just outside the mouth reads the value.

 Rendered once into bitmaps: the paper background and the case. Per frame: the tape's body,
 shadow, ticks and the visible numbers, then the case bitmap, then the hairline.
 */

internal class TapeGeo(val w: Float, val h: Float) {
    val tapeY = h * 0.6f
    val tapeH = 72f
    val caseS = min(170f, w * 0.43f)
    val caseX = 14f
    val caseY = tapeY - 24f - caseS / 2
    val mouthX = caseX + caseS - 4f
    val readX = mouthX + 22f
}

private fun argb(hex: Int, a: Float = 1f): Int = ((a.coerceIn(0f, 1f) * 255f + 0.5f).toInt() shl 24) or (hex and 0xFFFFFF)

private fun sigmaRadius(sigma: Float) = ((sigma - 0.5f) / 0.57735f).coerceAtLeast(0.01f)

private val LiveBlur = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P

private fun fill(color: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }

private fun line(color: Int, width: Float) = fill(color).apply { style = Paint.Style.STROKE; strokeWidth = width }

private fun blurred(color: Int, sigma: Float, live: Boolean = false) = fill(color).apply {
    if (!live || LiveBlur) maskFilter = BlurMaskFilter(sigmaRadius(sigma), BlurMaskFilter.Blur.NORMAL)
}

private fun linear(x0: Float, y0: Float, x1: Float, y1: Float, colors: IntArray, pos: FloatArray) =
    Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = LinearGradient(x0, y0, x1, y1, colors, pos, Shader.TileMode.CLAMP) }

private fun rr(x: Float, y: Float, w: Float, h: Float) = RectF(x, y, x + w, y + h)

/** The condensed sans the RN version asks for on Android ("sans-serif-condensed"), at a weight. */
private fun condensed(weight: Int): Typeface {
    val base = Typeface.create("sans-serif-condensed", Typeface.BOLD)
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) Typeface.create(base, weight, false) else base
}

internal class TapeFonts {
    val tick = fill(argb(0x1d1a14)).apply { typeface = condensed(700); textSize = 17f; textAlign = Paint.Align.CENTER }
    val tickRed = fill(argb(0xc8261c)).apply { typeface = condensed(800); textSize = 19f; textAlign = Paint.Align.CENTER }
    val brand = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = condensed(800); textSize = 15f }
    val small = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = condensed(600); textSize = 10f }
}

private fun bitmapFor(g: TapeGeo, density: Float): Pair<Bitmap, Canvas> {
    val bmp = Bitmap.createBitmap((g.w * density).roundToInt().coerceAtLeast(1), (g.h * density).roundToInt().coerceAtLeast(1), Bitmap.Config.ARGB_8888)
    val c = Canvas(bmp)
    c.scale(density, density)
    return bmp to c
}

private fun screw(c: Canvas, x: Float, y: Float, r: Float, turn: Float) {
    c.drawCircle(x, y + 0.8f, r + 0.6f, blurred(argb(0x5a3a00, 0.45f), 0.8f))
    c.drawCircle(x, y, r, Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = RadialGradient(x - r * 0.4f, y - r * 0.5f, r * 1.7f, intArrayOf(argb(0xffffff), argb(0xb9bec5), argb(0x5d636b)), floatArrayOf(0f, 0.45f, 1f), Shader.TileMode.CLAMP)
    })
    val slot = line(argb(0x2b2e33, 0.85f), 1f)
    c.save()
    c.rotate(turn, x, y)
    c.drawLine(x - r * 0.6f, y, x + r * 0.6f, y, slot)
    c.drawLine(x, y - r * 0.6f, x, y + r * 0.6f, slot)
    c.restore()
}

/** Paper background with grain and a soft window light. */
internal fun tapeRenderBackground(g: TapeGeo, density: Float): Bitmap {
    val (bmp, c) = bitmapFor(g, density)
    c.drawRect(0f, 0f, g.w, g.h, linear(0f, 0f, 0f, g.h, intArrayOf(argb(0xf4f1eb), argb(0xece7de), argb(0xe3ddd1)), floatArrayOf(0f, 0.55f, 1f)))
    c.drawRect(0f, 0f, g.w, g.h, Paint().apply { shader = RadialGradient(g.w * 0.15f, g.h * 0.05f, g.w * 1.1f, argb(0xfffdf8, 0.7f), argb(0xfffdf8, 0f), Shader.TileMode.CLAMP) })
    // A faint table edge behind the tape, so the case and tape sit on something.
    val ty = g.tapeY - g.caseS * 0.95f
    c.drawRect(0f, ty, g.w, g.h, linear(0f, ty, 0f, g.h, intArrayOf(argb(0xd9d2c4, 0f), argb(0xd9d2c4, 0.35f), argb(0xd2cabb, 0.5f)), floatArrayOf(0f, 0.3f, 1f)))
    // Grain: seeded grey noise multiplied in at 6 % (Skia's fractal noise, approximated).
    val size = 96
    val px = IntArray(size * size)
    var s = 3
    for (i in px.indices) {
        s = s * 1103515245 + 12345
        val v = (s ushr 16) and 0xff
        px[i] = (0xFF shl 24) or (v shl 16) or (v shl 8) or v
    }
    c.drawRect(0f, 0f, g.w, g.h, Paint().apply {
        shader = BitmapShader(Bitmap.createBitmap(px, size, size, Bitmap.Config.ARGB_8888), Shader.TileMode.REPEAT, Shader.TileMode.REPEAT).apply { setLocalMatrix(Matrix().apply { setScale(1.1f, 1.1f) }) }
        alpha = (0.06f * 255).roundToInt()
        xfermode = PorterDuffXfermode(PorterDuff.Mode.MULTIPLY)
    })
    return bmp
}

/** The case: belt clip, rubber armour, yellow face, screws, lock button, the chrome mouth. */
internal fun tapeRenderCase(g: TapeGeo, fonts: TapeFonts, density: Float): Bitmap {
    val (bmp, c) = bitmapFor(g, density)
    val x = g.caseX
    val y = g.caseY
    val s = g.caseS
    val cx = x + s / 2
    val cy = y + s / 2
    // Shadows: soft ambient and a tight contact one.
    c.drawRoundRect(rr(x + 4, y + 14, s - 4, s - 6), 36f, 36f, blurred(argb(0x3b2c14, 0.24f), 14f))
    c.drawRoundRect(rr(x + 1, y + 3, s - 2, s - 2), 36f, 36f, blurred(argb(0x2a1e0c, 0.32f), 3f))
    // Belt clip peeking out at the left, and the lock button at the top.
    c.drawRoundRect(rr(x - 7, cy - 46, 30f, 92f), 7f, 7f, linear(x - 7, 0f, x + 23, 0f, intArrayOf(argb(0x6c727a), argb(0xd9dde2), argb(0x8a9098), argb(0x4a4f56)), floatArrayOf(0f, 0.3f, 0.6f, 1f)))
    c.drawRoundRect(rr(cx + 6, y - 7, 38f, 16f), 7f, 7f, linear(0f, y - 7, 0f, y + 9, intArrayOf(argb(0x4a4c51), argb(0x2a2b2f), argb(0x1b1c1f)), floatArrayOf(0f, 0.5f, 1f)))
    c.drawLine(cx + 12, y - 4.5f, cx + 38, y - 4.5f, line(argb(0xffffff, 0.18f), 1f))
    // Rubber armour.
    c.drawRoundRect(rr(x, y, s, s), 36f, 36f, linear(0f, y, 0f, y + s, intArrayOf(argb(0x3c3d42), argb(0x2a2b2f), argb(0x1f2023), argb(0x161719)), floatArrayOf(0f, 0.3f, 0.7f, 1f)))
    c.drawRoundRect(rr(x + 0.5f, y + 0.5f, s - 1, s - 1), 35.5f, 35.5f, line(argb(0xffffff, 0.12f), 1f))
    val ridge = line(argb(0x0e0f10, 0.6f), 1.6f)
    val ridgeLight = line(argb(0xffffff, 0.08f), 0.8f)
    for (k in 0 until 7) {
        val yy = cy - 33 + k * 11
        c.drawLine(x + 3, yy, x + 9, yy, ridge)
        c.drawLine(x + 3, yy + 1.4f, x + 9, yy + 1.4f, ridgeLight)
    }
    // Yellow lacquered face.
    val inset = 11f
    val fx = x + inset
    val fy = y + inset
    val fs = s - inset * 2
    c.drawRoundRect(rr(fx, fy + 1.5f, fs, fs), 26f, 26f, blurred(argb(0, 0.45f), 2f))
    c.drawRoundRect(rr(fx, fy, fs, fs), 26f, 26f, linear(fx, fy, fx + fs, fy + fs, intArrayOf(argb(0xffe36d), argb(0xfac929), argb(0xeeae17), argb(0xcf8d0c)), floatArrayOf(0f, 0.35f, 0.7f, 1f)))
    c.drawRoundRect(rr(fx, fy, fs, fs), 26f, 26f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = RadialGradient(fx + fs * 0.28f, fy + fs * 0.22f, fs * 0.75f, argb(0xfffbe6, 0.55f), argb(0xfff4c0, 0f), Shader.TileMode.CLAMP)
    })
    c.drawRoundRect(rr(fx + 0.7f, fy + 0.7f, fs - 1.4f, fs - 1.4f), 25.3f, 25.3f, line(argb(0xfffbe8, 0.6f), 1.2f))
    c.drawRoundRect(rr(fx + 1, fy + 2, fs - 2, fs - 3), 25f, 25f, line(argb(0x8a5a00, 0.18f), 1f))
    // The reel cover: an embossed ring with the brand.
    c.drawCircle(cx, cy + 1, fs * 0.33f, line(argb(0xfff6cc, 0.55f), 1.4f))
    c.drawCircle(cx, cy, fs * 0.33f, line(argb(0x8a5800, 0.35f), 1.4f))
    val brandW = fonts.brand.measureText("FITLINE") + 6 * 2.2f
    var bx = cx - brandW / 2
    for (ch in "FITLINE") {
        val sch = ch.toString()
        fonts.brand.color = argb(0xfff8d8, 0.55f)
        c.drawText(sch, bx, cy + 4.8f, fonts.brand)
        fonts.brand.color = argb(0x5c3c02, 0.82f)
        c.drawText(sch, bx, cy + 4f, fonts.brand)
        bx += fonts.brand.measureText(sch) + 2.2f
    }
    val sub = "3 m · 10 ft"
    fonts.small.color = argb(0x6b4705, 0.7f)
    c.drawText(sub, cx - fonts.small.measureText(sub) / 2, cy + 19, fonts.small)
    // Screws in the face's corners.
    val sIn = 15f
    screw(c, fx + sIn, fy + sIn, 4.6f, 20f)
    screw(c, fx + fs - sIn, fy + sIn, 4.6f, 65f)
    screw(c, fx + sIn, fy + fs - sIn, 4.6f, 40f)
    screw(c, fx + fs - sIn, fy + fs - sIn, 4.6f, 5f)
    // The mouth: a chrome guide where the tape comes out.
    val my = g.tapeY - g.tapeH / 2 - 7
    val mh = g.tapeH + 14
    c.drawRoundRect(rr(x + s - 10, my + 2, 20f, mh), 5f, 5f, blurred(argb(0, 0.35f), 2.5f))
    c.drawRoundRect(rr(x + s - 10, my, 20f, mh), 5f, 5f, linear(0f, my, 0f, my + mh, intArrayOf(argb(0xf1f3f5), argb(0xa2a8b0), argb(0xe0e3e7), argb(0x6d737b), argb(0x3d4248)), floatArrayOf(0f, 0.3f, 0.55f, 0.85f, 1f)))
    c.drawLine(x + s + 8.5f, my + 6, x + s + 8.5f, my + mh - 6, line(argb(0x1d1f22, 0.8f), 1.6f))
    return bmp
}

/** The tape and the hairline, with paints and paths made once. */
internal class TapePainter(private val g: TapeGeo, private val fonts: TapeFonts) {
    private val body = Path()
    private val thin = Path()
    private val thick = Path()
    private val lipTop = Path()
    private val lipBottom = Path()
    private val shadowSoft = blurred(argb(0x4a3410, 0.24f), 5f, live = true)
    private val shadowTight = blurred(argb(0x3a2808, 0.3f), 1.5f, live = true)
    private val top = g.tapeY - g.tapeH / 2
    private val bodyFill = linear(
        0f, top, 0f, top + g.tapeH,
        intArrayOf(argb(0xc28f05), argb(0xeebd18), argb(0xffdc45), argb(0xfff1a0), argb(0xf7c923), argb(0xe3ad10), argb(0xb07f04)),
        floatArrayOf(0f, 0.1f, 0.3f, 0.38f, 0.5f, 0.82f, 1f),
    )
    private val thinPaint = line(argb(0x1d1a14, 0.82f), 0.8f)
    private val thickPaint = line(argb(0x1d1a14, 0.95f), 1.5f)
    private val lipTopPaint = line(argb(0xfff6c8, 0.75f), 0.9f)
    private val lipBottomPaint = line(argb(0x6e4c00, 0.55f), 0.9f)
    private val hairGlow = line(argb(0xe2342a, 0.28f), 1.4f).apply { if (LiveBlur) maskFilter = BlurMaskFilter(sigmaRadius(2f), BlurMaskFilter.Blur.NORMAL); strokeWidth = 3f }
    private val hair = line(argb(0xd92b20), 1.4f)
    private val tab = Path()
    private val tabShadow = blurred(argb(0x5a0c06, 0.3f), 1.5f, live = true)
    private val tabFill: Paint
    private val labels = HashMap<Int, String>()

    init {
        val t = g.tapeY - g.tapeH / 2 - 18
        tab.moveTo(g.readX - 6, t - 3)
        tab.lineTo(g.readX + 6, t - 3)
        tab.lineTo(g.readX + 6, t + 2)
        tab.lineTo(g.readX, t + 8)
        tab.lineTo(g.readX - 6, t + 2)
        tab.close()
        tabFill = linear(0f, t - 3, 0f, t + 8, intArrayOf(argb(0xf05247), argb(0xd42a1f), argb(0xa81c13)), floatArrayOf(0f, 0.5f, 1f))
    }

    private var amp = 0f
    private var phase = 0f
    private val span = max(1f, g.w + 12 - g.mouthX)

    private fun wave(x: Float): Float {
        if (amp < 0.01f || x <= g.mouthX) return 0f
        val e = min((x - g.mouthX) / span, 1f).pow(1.3f)
        return amp * e * sin((x - g.mouthX) / TAPE_WAVE_LENGTH * PI.toFloat() * 2 - phase)
    }

    /** Draws the tape for value v (cm under the hairline), displaced by a wave of height amp travelling at phase. */
    fun tape(c: Canvas, v: Double, amp: Float, phase: Float) {
        this.amp = amp
        this.phase = phase
        val x0 = g.mouthX - 26
        val x1 = g.w + 12
        body.rewind()
        var x = x0
        while (x <= x1) {
            if (x == x0) body.moveTo(x, top + wave(x)) else body.lineTo(x, top + wave(x))
            x += 6f
        }
        x = x1
        while (x >= x0) { body.lineTo(x, top + g.tapeH + wave(x)); x -= 6f }
        body.close()

        c.save()
        c.translate(0f, 6f)
        if (LiveBlur) c.drawPath(body, shadowSoft)
        c.translate(0f, -4.5f)
        c.drawPath(body, shadowTight)
        c.restore()
        c.drawPath(body, bodyFill)

        // Ticks: millimetres, half centimetres and centimetres on the top edge, a shorter row below.
        val cHi = v + (g.readX - x0) / TAPE_PPCM
        val cLo = v - (x1 - g.readX) / TAPE_PPCM
        thin.rewind()
        thick.rewind()
        val mStart = ceil(max(0.0, cLo) * 10).toInt()
        val mEnd = floor(cHi * 10).toInt()
        for (m in mStart..mEnd) {
            val tx = g.readX + ((v - m / 10.0) * TAPE_PPCM).toFloat()
            val dy = wave(tx)
            val cm = m % 10 == 0
            val len = if (cm) 22f else if (m % 5 == 0) 15f else 9f
            val p = if (cm) thick else thin
            p.moveTo(tx, top + 1 + dy)
            p.lineTo(tx, top + 1 + len + dy)
            val lenB = if (cm) 12f else if (m % 5 == 0) 9f else 5.5f
            p.moveTo(tx, top + g.tapeH - 1 + dy)
            p.lineTo(tx, top + g.tapeH - 1 - lenB + dy)
        }
        c.drawPath(thin, thinPaint)
        c.drawPath(thick, thickPaint)

        // A large number under every fifth centimetre line, every tenth in red.
        var cm = (ceil(cLo / 5) * 5).toInt()
        while (cm <= cHi + 2) {
            if (cm > 0) {
                val tx = g.readX + ((v - cm) * TAPE_PPCM).toFloat()
                val tens = cm % 10 == 0
                val label = labels.getOrPut(cm) { cm.toString() }
                c.drawText(label, tx, top + 50 + wave(tx), if (tens) fonts.tickRed else fonts.tick)
            }
            cm += 5
        }

        // Edges: a bright lip on top, a darker one below.
        lipTop.rewind()
        lipBottom.rewind()
        x = x0
        while (x <= x1) {
            val d = wave(x)
            if (x == x0) {
                lipTop.moveTo(x, top + 0.6f + d)
                lipBottom.moveTo(x, top + g.tapeH - 0.6f + d)
            } else {
                lipTop.lineTo(x, top + 0.6f + d)
                lipBottom.lineTo(x, top + g.tapeH - 0.6f + d)
            }
            x += 6f
        }
        c.drawPath(lipTop, lipTopPaint)
        c.drawPath(lipBottom, lipBottomPaint)
    }

    /** The red hairline that reads the value, with its little pointer tab. */
    fun hairline(c: Canvas) {
        val t = g.tapeY - g.tapeH / 2 - 18
        val b = g.tapeY + g.tapeH / 2 + 14
        if (LiveBlur) c.drawLine(g.readX, t + 6, g.readX, b, hairGlow)
        c.drawLine(g.readX, t + 6, g.readX, b, hair)
        c.drawPath(tab, tabShadow)
        c.drawPath(tab, tabFill)
    }
}

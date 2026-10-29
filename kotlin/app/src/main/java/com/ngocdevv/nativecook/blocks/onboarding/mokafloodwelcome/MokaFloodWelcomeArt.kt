package com.ngocdevv.nativecook.blocks.onboarding.mokafloodwelcome

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.BlendMode
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.os.Build
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

class MokaFloodWelcomeResources(
    val hand: Typeface,
    val sans: Typeface,
    val semibold: Typeface,
    val grain: Bitmap,
)

/** Layout, palette and drawing of Moka Flood Welcome, ported from MokaFloodWelcomeArt.swift. */
object MokaFloodWelcomeArt {
    private val m = MokaFloodWelcomeMotion

    // Palette
    const val PEACH = 0xF8D0C3
    const val COFFEE = 0x402D06
    const val CREMA = 0x6E4D17
    const val SPECK = 0x8A6427
    const val POT = 0x8C0F22
    const val POT_BAND = 0x6E0A1A
    const val POT_LID = 0x7A0C1D
    const val POT_LINE = 0xE99AA6
    const val HANDLE = 0xD47E8D
    const val KNOB = 0xD6A4A3
    const val GLASS_LINE = 0x3B2A96
    const val SHADOW = 0xE3A898
    const val CREAM = 0xF6EEDB

    fun argb(hex: Int, alpha: Double = 1.0): Int =
        ((alpha.coerceIn(0.0, 1.0) * 255 + 0.5).toInt() shl 24) or (hex and 0xFFFFFF)

    private const val HAND_FONT = "mokafloodwelcome/mokafloodwelcome_GochiHand-Regular.ttf"

    fun load(context: Context): MokaFloodWelcomeResources {
        val hand = runCatching { Typeface.createFromAsset(context.assets, HAND_FONT) }.getOrDefault(Typeface.DEFAULT)
        val semibold = if (Build.VERSION.SDK_INT >= 28) Typeface.create(Typeface.DEFAULT, 600, false) else Typeface.DEFAULT_BOLD
        // Same xorshift noise as the Swift tile.
        val n = 160
        var seed = 0x9E3779B9.toInt()
        val pixels = IntArray(n * n) {
            seed = seed xor (seed shl 13)
            seed = seed xor (seed ushr 17)
            seed = seed xor (seed shl 5)
            val v = seed and 0xFF
            (0xFF shl 24) or (v shl 16) or (v shl 8) or v
        }
        val grain = Bitmap.createBitmap(pixels, n, n, Bitmap.Config.ARGB_8888)
        return MokaFloodWelcomeResources(hand, Typeface.DEFAULT, semibold, grain)
    }

    // Layout

    class Layout(val height: Double, val top: Double, val bottom: Double) {
        val barY = top + 26
        val ctaTop = height - bottom - 16 - 56
        val dotsY = ctaTop - 30
        val hintY = ctaTop - 70
        val glassX = 132.0
        val glassBottom = ctaTop - 130
        val glassTop = glassBottom - 172
        val pivotX = 266.0
        val pivotY = glassTop - 170
        val titleY = glassTop - 74
        val subY = titleY + 3 * 64 - 6
        val floor = glassBottom - 5

        /** The flood surface's base height and wave share, or null while there is no surface. */
        fun flood(f: MokaFloodWelcomeMotion.Frame): Pair<Double, Double>? {
            f.drain?.let { d -> return Pair(-70 + (height + 110) * d, 1.0) }
            if (f.volume <= 1.75) return null
            val rise = MokaFloodWelcomeMotion.clamp((f.volume - 1.75) / 1.25)
            return Pair(floor - (floor + 70) * rise, MokaFloodWelcomeMotion.easeOutCubic(MokaFloodWelcomeMotion.clamp((f.volume - 1.75) / 0.4)))
        }
    }

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    private fun fill(color: Int): Paint {
        paint.reset()
        paint.isAntiAlias = true
        paint.style = Paint.Style.FILL
        paint.color = color
        return paint
    }

    private fun stroke(color: Int, width: Float, round: Boolean = true): Paint {
        paint.reset()
        paint.isAntiAlias = true
        paint.style = Paint.Style.STROKE
        paint.color = color
        paint.strokeWidth = width
        if (round) {
            paint.strokeCap = Paint.Cap.ROUND
            paint.strokeJoin = Paint.Join.ROUND
        }
        return paint
    }

    // Pot

    private const val SPOUT_X = -99.0
    private const val SPOUT_Y = -125.0
    private const val POT_SCALE = 0.86

    /** The spout tip in screen pt for a pot angle. */
    fun spoutTip(l: Layout, deg: Double): Pair<Double, Double> {
        val a = deg * PI / 180
        val x = SPOUT_X * POT_SCALE
        val y = SPOUT_Y * POT_SCALE
        return Pair(l.pivotX + x * cos(a) - y * sin(a), l.pivotY + x * sin(a) + y * cos(a))
    }

    private fun arch(p: Path, xb: Double, xt: Double, w: Double, yb: Double, yt: Double) {
        p.moveTo((xb - w / 2).toFloat(), yb.toFloat())
        p.lineTo((xt - w / 2).toFloat(), (yt + w / 2).toFloat())
        p.quadTo(xt.toFloat(), (yt - w * 0.5).toFloat(), (xt + w / 2).toFloat(), (yt + w / 2).toFloat())
        p.lineTo((xb + w / 2).toFloat(), yb.toFloat())
    }

    private val potHandle = Path().apply {
        moveTo(62f, -102f)
        cubicTo(122f, -118f, 118f, -26f, 58f, -36f)
    }
    private val potUpper = Path().apply {
        moveTo(-52f, -6f); lineTo(-70f, -112f); lineTo(70f, -112f); lineTo(52f, -6f); close()
    }
    private val potLid = Path().apply { addRoundRect(RectF(-67f, -121f, 67f, -111f), 3f, 3f, Path.Direction.CW) }
    private val potKnob = Path().apply { addRoundRect(RectF(-36f, -131f, -20f, -119f), 3f, 3f, Path.Direction.CW) }
    private val potSpout = Path().apply {
        moveTo(-58f, -114f); lineTo(-99f, -125f); lineTo(-68f, -86f); close()
    }
    private val potBand = Path().apply { addRoundRect(RectF(-57f, -8f, 57f, 8f), 2f, 2f, Path.Direction.CW) }
    private val potBoiler = Path().apply {
        moveTo(-52f, 7f)
        lineTo(-76f, 116f)
        quadTo(-77f, 122f, -70f, 122f)
        lineTo(70f, 122f)
        quadTo(77f, 122f, 76f, 116f)
        lineTo(52f, 7f)
        close()
    }
    private val potLines = Path().apply {
        for ((xb, xt) in listOf(-36.0 to -50.0, -12.0 to -17.0, 12.0 to 17.0, 36.0 to 50.0)) arch(this, xb, xt, 21.0, -20.0, -100.0)
        for ((xt, xb) in listOf(-34.0 to -54.0, -11.0 to -18.0, 11.0 to 18.0, 34.0 to 54.0)) arch(this, xb, xt, 19.0, 110.0, 20.0)
    }

    private fun drawPot(c: Canvas, l: Layout, angle: Double) {
        c.save()
        c.translate(l.pivotX.toFloat(), l.pivotY.toFloat())
        c.rotate(angle.toFloat())
        c.scale(POT_SCALE.toFloat(), POT_SCALE.toFloat())
        c.drawPath(potHandle, stroke(argb(HANDLE), 18f).apply { strokeJoin = Paint.Join.ROUND })
        c.drawPath(potSpout, fill(argb(POT)))
        c.drawPath(potUpper, fill(argb(POT)))
        c.drawPath(potBoiler, fill(argb(POT)))
        c.drawPath(potLid, fill(argb(POT_LID)))
        c.drawPath(potKnob, fill(argb(KNOB)))
        c.drawPath(potBand, fill(argb(POT_BAND)))
        c.drawPath(potLines, stroke(argb(POT_LINE), 2.4f))
        c.drawLine(-46f, 14f, -66f, 112f, stroke(argb(0xFFFFFF, 0.10), 6f))
        c.restore()
    }

    // Glass

    private fun glassInterior(l: Layout): Path {
        val x = l.glassX.toFloat()
        val t = l.glassTop.toFloat()
        val b = (l.glassBottom - 14).toFloat()
        return Path().apply {
            moveTo(x - 73, t - 40)
            lineTo(x - 73, t)
            lineTo(x - 59.5f, b)
            quadTo(x, b + 14, x + 59.5f, b)
            lineTo(x + 73, t)
            lineTo(x + 73, t - 40)
            close()
        }
    }

    private fun glassOutline(p: Path, l: Layout) {
        val x = l.glassX.toFloat()
        val t = l.glassTop.toFloat()
        val b = l.glassBottom.toFloat()
        p.moveTo(x - 76, t)
        p.lineTo(x - 61.5f, b - 12)
        p.quadTo(x - 60, b, x - 48, b)
        p.lineTo(x + 48, b)
        p.quadTo(x + 60, b, x + 61.5f, b - 12)
        p.lineTo(x + 76, t)
    }

    private fun glassBody(l: Layout): Path = Path().apply {
        glassOutline(this, l)
        for (i in 1..24) {
            val a = PI * i / 24
            lineTo((l.glassX + 76 * cos(a)).toFloat(), (l.glassTop - 9 * sin(a)).toFloat())
        }
        close()
    }

    private fun halfEllipse(p: Path, cx: Double, cy: Double, rx: Double, ry: Double, front: Boolean) {
        for (i in 0..24) {
            val a = PI * i / 24
            val x = (cx - rx * cos(a)).toFloat()
            val y = (cy + (if (front) 1 else -1) * ry * sin(a)).toFloat()
            if (i == 0) p.moveTo(x, y) else p.lineTo(x, y)
        }
    }

    private fun glassLines(l: Layout): Path = Path().apply {
        val x = l.glassX
        val t = l.glassTop
        val b = l.glassBottom
        glassOutline(this, l)
        halfEllipse(this, x, t, 76.0, 9.0, true)
        halfEllipse(this, x, t, 76.0, 9.0, false)
        halfEllipse(this, x, t + 28, 73.5, 8.0, true)
        halfEllipse(this, x, b - 14, 60.0, 7.0, true)
        val yt = t + 50
        val yb = b - 19
        for (k in listOf(-2.0, -1.0, 0.0, 1.0, 2.0)) arch(this, x + k * 23, x + k * 27.5, 17.0, yb - abs(k) * 2, yt)
    }

    private fun meniscus(x: Double, level: Double, f: MokaFloodWelcomeMotion.Frame, l: Layout, impactX: Double?): Double {
        val u = (x - (l.glassX - 73)) / 146
        var y = level + 2.6 * f.slosh * cos(PI * u) * sin(7.1 * f.time) +
            1.1 * f.slosh * sin(2 * PI * u * 1.5 + 9.3 * f.time)
        if (impactX != null) {
            val d = abs(x - impactX)
            y += 2.2 * sin(0.2 * d - 17 * f.time) * exp(-d / 38)
        }
        val wall = min(u, 1 - u) * 146
        y -= 2.2 * exp(-wall / 4)
        return y
    }

    private fun glassLevel(l: Layout, volume: Double): Double {
        val bottom = l.glassBottom - 14
        val rim = l.glassTop + 1
        return bottom - (bottom - rim) * m.clamp(volume)
    }

    private fun drawGlassCoffee(c: Canvas, l: Layout, f: MokaFloodWelcomeMotion.Frame, impactX: Double?) {
        if (f.drain != null || f.volume <= 0.001) return
        val x = l.glassX
        val level = glassLevel(l, f.volume)
        val x0 = x - 74
        val x1 = x + 74
        val liquid = Path()
        var px = x0
        var first = true
        while (px <= x1 + 0.1) {
            val y = meniscus(px, level, f, l, impactX).toFloat()
            if (first) { liquid.moveTo(px.toFloat(), y); first = false } else liquid.lineTo(px.toFloat(), y)
            px += 4
        }
        liquid.lineTo(x1.toFloat(), l.glassBottom.toFloat())
        liquid.lineTo(x0.toFloat(), l.glassBottom.toFloat())
        liquid.close()
        c.save()
        c.clipPath(glassInterior(l))
        c.drawPath(liquid, fill(argb(COFFEE)))
        if (f.volume < 1.02) {
            val band = Path()
            var bx = x0
            var started = false
            while (bx <= x1 + 0.1) {
                val y = (meniscus(bx, level, f, l, impactX) + 2.5).toFloat()
                if (started) band.lineTo(bx.toFloat(), y) else { band.moveTo(bx.toFloat(), y); started = true }
                bx += 4
            }
            c.drawPath(band, stroke(argb(CREMA, 0.9), 2.5f, round = false))
        }
        c.restore()

        val dome = m.clamp((f.volume - 1) / 0.15)
        if (dome > 0) {
            val h = 14 * dome * (1 + 0.06 * sin(6.3 * f.time))
            val d = Path()
            for (i in 0..28) {
                val u = i / 28.0
                val dx = (x - 78 + 156 * u).toFloat()
                val dy = (l.glassTop + 3 - h * sin(PI * u).pow(0.6) + 1.2 * sin(9 * u + 8 * f.time) * dome).toFloat()
                if (i == 0) d.moveTo(dx, dy) else d.lineTo(dx, dy)
            }
            d.lineTo((x + 76).toFloat(), (l.glassTop + 8).toFloat())
            d.lineTo((x - 76).toFloat(), (l.glassTop + 8).toFloat())
            d.close()
            c.drawPath(d, fill(argb(COFFEE)))
        }
    }

    private fun drawSpills(c: Canvas, l: Layout, f: MokaFloodWelcomeMotion.Frame) {
        if (f.drain != null) return
        val left = m.clamp((f.volume - 1.08) / 0.24)
        val right = m.clamp((f.volume - 1.16) / 0.22)
        for ((side, s, w) in listOf(Triple(-1.0, left, 10.5f), Triple(1.0, right, 7.5f))) {
            if (s <= 0) continue
            val x = l.glassX
            val t = l.glassTop
            val b = l.glassBottom
            val xs = doubleArrayOf(x + side * 77, x + side * 63.5, x + side * 58)
            val ys = doubleArrayOf(t + 4, b - 8, b + 1)
            val lengths = DoubleArray(3)
            for (i in 1 until 3) lengths[i] = lengths[i - 1] + hypot(xs[i] - xs[i - 1], ys[i] - ys[i - 1])
            val front = s.pow(1.5) * lengths[2]
            val p = Path()
            p.moveTo(xs[0].toFloat(), ys[0].toFloat())
            for (i in 1 until 3) {
                if (lengths[i] <= front) {
                    p.lineTo(xs[i].toFloat(), ys[i].toFloat())
                } else {
                    val k = (front - lengths[i - 1]) / (lengths[i] - lengths[i - 1])
                    p.lineTo((xs[i - 1] + (xs[i] - xs[i - 1]) * k).toFloat(), (ys[i - 1] + (ys[i] - ys[i - 1]) * k).toFloat())
                    break
                }
            }
            c.drawPath(p, stroke(argb(COFFEE), w))
        }
    }

    // Stream

    private fun drawStream(c: Canvas, f: MokaFloodWelcomeMotion.Frame, tipX: Double, tipY: Double, landY: Double) {
        val s = f.stream ?: return
        val top = tipY + s.tail
        val bottom = min(tipY + s.head, landY + 2)
        if (bottom <= top + 1) return
        val w = 11 * s.width
        val n = 18
        val p = Path()
        val rx = FloatArray(n + 1)
        val ry = FloatArray(n + 1)
        val cxs = DoubleArray(n + 1)
        for (i in 0..n) {
            val y = top + (bottom - top) * i / n
            val fall = y - tipY
            val bend = -5 * (1 - exp(-fall / 16)) + 0.5 * sin(0.06 * y - 21 * f.time) * min(1.0, fall / 40)
            val taper = w * (1 - 0.18 * min(1.0, fall / 260)) * (if (s.tail > 0) 0.85 else 1.0)
            val cx = tipX + bend
            cxs[i] = cx
            if (i == 0) p.moveTo((cx - taper / 2).toFloat(), y.toFloat()) else p.lineTo((cx - taper / 2).toFloat(), y.toFloat())
            rx[i] = (cx + taper / 2).toFloat()
            ry[i] = y.toFloat()
        }
        for (i in n downTo 0) p.lineTo(rx[i], ry[i])
        p.close()
        c.drawPath(p, fill(argb(COFFEE)))
        val endR = (w * 0.82 / 2).toFloat()
        c.drawCircle(cxs[n].toFloat(), bottom.toFloat(), endR, fill(argb(COFFEE)))
        if (s.tail > 0) c.drawCircle(cxs[0].toFloat(), top.toFloat(), endR, fill(argb(COFFEE)))
    }

    // Flood

    fun floodY(x: Double, base: Double, amp: Double, time: Double): Double {
        val tilt = 0.06 * sin(1.7 * time + 0.4) * (x - 196.5)
        val waves = 9 * (0.6 * sin(x * 2 * PI / 230 + 2.1 * time) + 0.4 * sin(x * 2 * PI / 120 - 3.3 * time + 1.3))
        return base + amp * (waves + tilt)
    }

    private fun surfaceLine(p: Path, base: Double, amp: Double, time: Double, offset: Double) {
        var x = -12.0
        p.moveTo(x.toFloat(), (floodY(x, base, amp, time) + offset).toFloat())
        while (x < 405) {
            x += 6
            p.lineTo(x.toFloat(), (floodY(x, base, amp, time) + offset).toFloat())
        }
    }

    private fun floodPath(l: Layout, base: Double, amp: Double, time: Double): Path = Path().apply {
        surfaceLine(this, base, amp, time, 0.0)
        lineTo(405f, (l.height + 60).toFloat())
        lineTo(-12f, (l.height + 60).toFloat())
        close()
    }

    /** The puddle spreading from the glass's foot (volume 1.28–1.75), flat at the back. */
    private fun puddlePath(l: Layout, f: MokaFloodWelcomeMotion.Frame): Path? {
        val s = m.clamp((f.volume - 1.28) / 0.47)
        if (f.drain != null || s <= 0 || f.volume > 1.75) return null
        val rx = 50 + 710 * s.pow(1.25)
        val ry = 0.42 * rx
        val cx = l.glassX - 6
        val cy = l.glassBottom + 4
        val p = Path()
        for (i in 0 until 72) {
            val th = 2 * PI * i / 72
            val wob = 1 + 0.05 * sin(5 * th + 3.1 * f.time) + 0.03 * sin(9 * th - 4.3 * f.time)
            val x = (cx + rx * wob * cos(th)).toFloat()
            val y = max(l.floor, cy + ry * wob * sin(th)).toFloat()
            if (i == 0) p.moveTo(x, y) else p.lineTo(x, y)
        }
        p.close()
        return p
    }

    private fun hash(i: Double, salt: Double) = m.hash(i * 7.31 + salt * 1.97)

    private fun drawFloodLife(c: Canvas, l: Layout, base: Double, amp: Double, time: Double) {
        val band = Path()
        surfaceLine(band, base, amp, time, 1.5)
        c.drawPath(band, stroke(argb(CREMA, 0.85), 4f, round = false))
        for (i in 0 until 26) {
            val fi = i.toDouble()
            val x = m.mod(hash(fi, 1.0) * 417 + time * (5 + 9 * hash(fi, 2.0)), 417.0) - 12
            val y = floodY(x, base, amp, time) + 7 + 16 * hash(fi, 3.0)
            val r = 1.1 + 1.7 * hash(fi, 4.0)
            c.drawCircle(x.toFloat(), y.toFloat(), r.toFloat(), fill(argb(SPECK, 0.75)))
        }
        val span = l.height + 60
        for (i in 0 until 18) {
            val fi = i.toDouble()
            val x = 18 + 357 * hash(fi, 5.0) + 6 * sin(1.3 * time + fi)
            val y = l.height + 30 - m.mod(time * (26 + 44 * hash(fi, 6.0)) + hash(fi, 7.0) * span, span)
            val r = 1.6 + 3.4 * hash(fi, 8.0)
            c.drawCircle(x.toFloat(), y.toFloat(), r.toFloat(), stroke(argb(CREMA, 0.8), 1.3f, round = false))
        }
    }

    // Background

    private fun drawGrain(c: Canvas, l: Layout, res: MokaFloodWelcomeResources, alpha: Double) {
        if (Build.VERSION.SDK_INT < 29) return
        val p = fill(0xFF000000.toInt())
        val shader = BitmapShader(res.grain, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
        shader.setLocalMatrix(Matrix().apply { setScale(0.5f, 0.5f) })
        p.shader = shader
        p.alpha = (alpha * 255).toInt()
        p.blendMode = BlendMode.OVERLAY
        c.drawRect(0f, 0f, 393f, l.height.toFloat(), p)
    }

    private fun drawShadow(c: Canvas, l: Layout) {
        val b = l.glassBottom.toFloat()
        val p = Path().apply {
            moveTo(112f, b - 1)
            lineTo(296f, b + 1)
            quadTo(302f, b + 4, 294f, b + 7)
            lineTo(112f, b + 7)
            close()
        }
        val paint = fill(0xFF000000.toInt())
        paint.shader = LinearGradient(150f, b, 300f, b, intArrayOf(argb(SHADOW), argb(SHADOW, 0.95), argb(SHADOW, 0.0)),
            null, Shader.TileMode.CLAMP)
        c.drawPath(p, paint)
    }

    // Copy

    private class Ink(val main: Int, val soft: Int, val ctaFill: Int, val ctaText: Int)

    private val peachInk = Ink(argb(COFFEE), argb(COFFEE, 0.62), argb(COFFEE), argb(CREAM))
    private val coffeeInk = Ink(argb(CREAM), argb(CREAM, 0.7), argb(CREAM), argb(COFFEE))

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    /** Draws one line centred vertically on [cy] (as SwiftUI does), aligned by [align]; returns its width. */
    private fun text(c: Canvas, s: String, x: Double, cy: Double, size: Float, face: Typeface, color: Int, align: Paint.Align): Float {
        textPaint.reset()
        textPaint.isAntiAlias = true
        textPaint.typeface = face
        textPaint.textSize = size
        textPaint.color = color
        textPaint.textAlign = align
        val fm = textPaint.fontMetrics
        c.drawText(s, x.toFloat(), (cy - (fm.ascent + fm.descent) / 2).toFloat(), textPaint)
        return textPaint.measureText(s)
    }

    private fun drawChrome(c: Canvas, l: Layout, res: MokaFloodWelcomeResources, ink: Ink, hint: Boolean) {
        text(c, "brewday", 24.0, l.barY, 28f, res.hand, ink.main, Paint.Align.LEFT)
        text(c, "Skip", 369.0, l.barY, 15f, res.semibold, ink.soft, Paint.Align.RIGHT)
        if (hint) text(c, "hold the pot to pour", 196.5, l.hintY, 22f, res.hand, ink.soft, Paint.Align.CENTER)
        var x = 196.5 - (22.0 + 32) / 2
        for (i in 0 until 3) {
            val w = if (i == 0) 22.0 else 8.0
            val col = if (i == 0) ink.main else (ink.main and 0xFFFFFF) or ((0.3 * 255).toInt() shl 24)
            c.drawRoundRect(RectF(x.toFloat(), (l.dotsY - 4).toFloat(), (x + w).toFloat(), (l.dotsY + 4).toFloat()), 4f, 4f, fill(col))
            x += w + 8
        }
        c.drawRoundRect(RectF(24f, l.ctaTop.toFloat(), 369f, (l.ctaTop + 56).toFloat()), 28f, 28f, fill(ink.ctaFill))
        text(c, "Get started", 196.5, l.ctaTop + 28, 17f, res.semibold, ink.ctaText, Paint.Align.CENTER)
    }

    private val titleLines = listOf("brew your", "morning", "routine!")
    private val subLines = listOf("Brewday lines up your first cup,", "your stretch and the plan for today.")

    private fun drawTitle(c: Canvas, l: Layout, res: MokaFloodWelcomeResources, f: MokaFloodWelcomeMotion.Frame) {
        textPaint.reset()
        textPaint.typeface = res.hand
        textPaint.textSize = 60f
        for ((i, line) in titleLines.withIndex()) {
            val p = m.lineProgress(f.title, i)
            if (p <= 0) continue
            textPaint.typeface = res.hand
            textPaint.textSize = 60f
            val w = textPaint.measureText(line)
            val cy = l.titleY + i * 64
            c.save()
            c.clipRect(20f, (cy - 50).toFloat(), (32 + p * (w + 16)).toFloat(), (cy + 50).toFloat())
            text(c, line, 32.0, cy, 60f, res.hand, argb(CREAM), Paint.Align.LEFT)
            c.restore()
        }
        if (f.sub > 0) {
            for ((i, line) in subLines.withIndex()) {
                text(c, line, 32.0, l.subY + i * 22 + 8 * (1 - f.sub), 16f, res.sans, argb(CREAM, 0.78 * f.sub), Paint.Align.LEFT)
            }
        }
    }

    // Scene

    /** Whether the flood covers the status bar (for the system icons' colour). */
    fun coversTop(l: Layout, f: MokaFloodWelcomeMotion.Frame): Boolean {
        val fl = l.flood(f) ?: return false
        return fl.first + 9 * fl.second < l.top * 0.6
    }

    fun draw(c: Canvas, l: Layout, res: MokaFloodWelcomeResources, f: MokaFloodWelcomeMotion.Frame) {
        c.drawRect(0f, 0f, 393f, l.height.toFloat(), fill(argb(PEACH)))
        drawShadow(c, l)
        drawPot(c, l, f.angle)

        val flood = l.flood(f)
        val (tipX, tipY) = spoutTip(l, f.angle)
        val level = glassLevel(l, f.volume)
        val inGlass = tipX > l.glassX - 70 && tipX < l.glassX + 70
        var land = l.height + 40
        if (inGlass && f.drain == null) {
            land = if (f.volume > 1) l.glassTop + 3 - 14 * m.clamp((f.volume - 1) / 0.15)
            else if (f.volume > 0.001) level else l.glassBottom - 10
        }
        if (flood != null) land = min(land, floodY(tipX, flood.first, flood.second, f.time))
        drawStream(c, f, tipX, tipY, land)

        c.drawPath(glassBody(l), fill(argb(0xFFFFFF, 0.18)))
        val s = f.stream
        val hits = s != null && tipY + s.head >= land - 1 && s.tail < land - tipY
        drawGlassCoffee(c, l, f, if (hits && inGlass) tipX - 4 else null)
        c.drawPath(glassLines(l), stroke(argb(GLASS_LINE), 3f))
        drawSpills(c, l, f)
        drawGrain(c, l, res, 0.06)
        drawChrome(c, l, res, peachInk, hint = true)

        val region: Path = if (flood != null) {
            if (flood.first >= l.height + 20) return
            floodPath(l, flood.first, flood.second, f.time)
        } else {
            puddlePath(l, f) ?: return
        }
        c.drawPath(region, fill(argb(COFFEE)))
        c.save()
        c.clipPath(region)
        drawGrain(c, l, res, 0.07)
        if (flood != null) drawFloodLife(c, l, flood.first, flood.second, f.time)
        drawChrome(c, l, res, coffeeInk, hint = false)
        drawTitle(c, l, res, f)
        c.restore()
    }
}

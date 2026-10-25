package com.ngocdevv.nativecook.blocks.progress.rocketlaunchprogress

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
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/*
 Rocket Launch Progress — layout and the art drawn once:
 - the back: a dusk sky (four stops), stars, a moon, two ridgelines, the ground, the pad with its
   flame trench, the service tower, floodlights, the fuel tank's shell and its line to the pad —
   one screen-sized bitmap at the screen's pixel ratio, baked off the main thread;
 - the rocket: a handful of static paths in its own frame (nozzle exit at the origin, nose up),
   drawn under a transform that the loop moves.
*/

internal class RocketTank(val x: Float, val y: Float, val w: Float, val h: Float)

internal class RocketLayout(
    val w: Float,
    val h: Float,
    val top: Float,
    val groundY: Float,
    val padX: Float,
    val rocketH: Float,
    /** Scale of the rocket's paths (designed 136 dp tall). */
    val k: Float,
    val tank: RocketTank,
    val cardTop: Float,
    val cardH: Float,
    val rowH: Float,
)

internal fun rocketLayout(w: Float, h: Float, top: Float, bottom: Float): RocketLayout {
    val rowH = if (h < 740) 46f else 52f
    val cardH = rowH * 4 + 62
    val cardTop = h - bottom - 12 - cardH
    val groundY = cardTop - 30
    val rocketH = max(96f, min(150f, (groundY - (top + 120)) * 0.5f))
    val padX = (w * 0.46f).roundToInt().toFloat()
    val tankH = rocketH * 0.78f
    return RocketLayout(
        w, h, top, groundY, padX, rocketH,
        k = rocketH / 136,
        tank = RocketTank(padX + 70 * (rocketH / 136) + 10, groundY - 12 - tankH, 30f, tankH),
        cardTop = cardTop,
        cardH = cardH,
        rowH = rowH,
    )
}

/** The rocket's parts, nozzle exit at (0, 0), nose up, 136 dp tall; scaled by k when drawn. */
internal class RocketParts {
    val nozzle = Path().apply { moveTo(-8f, 0f); lineTo(8f, 0f); quadTo(6f, -6f, 5f, -12f); lineTo(-5f, -12f); quadTo(-6f, -6f, -8f, 0f); close() }
    val body = Path().apply { moveTo(-15f, -12f); lineTo(15f, -12f); lineTo(15f, -96f); lineTo(-15f, -96f); close() }
    val nose = Path().apply { moveTo(-15f, -96f); cubicTo(-15f, -114f, -8f, -128f, 0f, -136f); cubicTo(8f, -128f, 15f, -114f, 15f, -96f); close() }
    val finL = Path().apply { moveTo(-15f, -16f); lineTo(-28f, -6f); quadTo(-30f, -24f, -15f, -46f); close() }
    val finR = Path().apply { moveTo(15f, -16f); lineTo(28f, -6f); quadTo(30f, -24f, 15f, -46f); close() }
    val finC = Path().apply { moveTo(-2.5f, -8f); lineTo(2.5f, -8f); lineTo(2.5f, -40f); lineTo(-2.5f, -40f); close() }
    val band = Path().apply { moveTo(-15f, -58f); lineTo(15f, -58f); lineTo(15f, -64f); lineTo(-15f, -64f); close() }

    private fun grad(x0: Float, x1: Float, vararg stops: Pair<Float, Long>) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = LinearGradient(x0, 0f, x1, 0f, IntArray(stops.size) { stops[it].second.toInt() }, FloatArray(stops.size) { stops[it].first }, Shader.TileMode.CLAMP)
    }

    val nozzlePaint = grad(-8f, 8f, 0f to 0xFF2A2630, 0.4f to 0xFF6E6878, 1f to 0xFF1E1A24)
    val finLPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF9E2A22.toInt() }
    val finRPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF7E1F1A.toInt() }
    val bodyPaint = grad(-15f, 15f, 0f to 0xFF7C7686, 0.3f to 0xFFE9E4EE, 0.45f to 0xFFFFFFFF, 0.75f to 0xFFC7C0CF, 1f to 0xFF5E5868)
    val bandPaint = grad(-15f, 15f, 0f to 0xFF6E1A16, 0.4f to 0xFFE3523A, 1f to 0xFFA52A20)
    val nosePaint = grad(-15f, 15f, 0f to 0xFF6E1A16, 0.35f to 0xFFF06A4A, 0.55f to 0xFFE3523A, 1f to 0xFF7A1E18)
    val finCPaint = grad(-2.5f, 2.5f, 0f to 0xFFB8352A, 0.5f to 0xFFE05A44, 1f to 0xFF8E2420)
    val window = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF2E3B5E.toInt() }
    val glass = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.argb(191, 190, 220, 255) }
    private val rect = RectF()

    /** The rocket in its own frame. */
    fun draw(c: Canvas) {
        c.drawPath(nozzle, nozzlePaint)
        c.drawPath(finL, finLPaint)
        c.drawPath(finR, finRPaint)
        c.drawPath(body, bodyPaint)
        c.drawPath(band, bandPaint)
        c.drawPath(nose, nosePaint)
        c.drawPath(finC, finCPaint)
        rect.set(-5.5f, -84f, 5.5f, -73f)
        c.drawRoundRect(rect, 5.5f, 5.5f, window)
        rect.set(-4f, -82.5f, 1f, -78.5f)
        c.drawRoundRect(rect, 2f, 2f, glass)
    }
}

private fun hash(n: Double): Double {
    val v = sin(n * 127.1 + 311.7) * 43758.5453
    return v - floor(v)
}

private fun argb(a: Float, r: Int, g: Int, b: Int) = android.graphics.Color.argb((a * 255).roundToInt().coerceIn(0, 255), r, g, b)

private fun withAlpha(c: Int, a: Float) = (c and 0x00FFFFFF) or ((a * 255).roundToInt().coerceIn(0, 255) shl 24)

private fun fill(color: Long, alpha: Float = 1f) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    this.color = if (alpha < 1f) withAlpha(color.toInt(), alpha) else color.toInt()
}

private fun ints(vararg c: Long) = IntArray(c.size) { c[it].toInt() }

private fun linear(x0: Float, y0: Float, x1: Float, y1: Float, colors: IntArray, pos: FloatArray?) =
    Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = LinearGradient(x0, y0, x1, y1, colors, pos, Shader.TileMode.CLAMP) }

private fun radial(x: Float, y: Float, r: Float, colors: IntArray, pos: FloatArray?) =
    Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = RadialGradient(x, y, r, colors, pos, Shader.TileMode.CLAMP) }

private fun Paint.blur(sigma: Float): Paint {
    maskFilter = BlurMaskFilter(((sigma - 0.5f) / 0.57735f).coerceAtLeast(0.01f), BlurMaskFilter.Blur.NORMAL)
    return this
}

private fun Paint.stroke(w: Float): Paint {
    style = Paint.Style.STROKE
    strokeWidth = w
    return this
}

private fun ridge(c: Canvas, w: Float, base: Float, amp: Float, seed: Double, color: Long, alpha: Float) {
    val b = Path()
    b.moveTo(0f, base + 40)
    var x = 0
    while (x <= w + 20) {
        val h = sin(x * 0.012 + seed) * 0.5 + sin(x * 0.031 + seed * 2.1) * 0.3 + hash(floor(x / 20.0) + seed * 10) * 0.2
        b.lineTo(x.toFloat(), (base - amp * (0.5 + h)).toFloat())
        x += 20
    }
    b.lineTo(w + 20, base + 40)
    b.close()
    c.drawPath(b, fill(color, alpha))
}

/** The whole still scene behind the rocket, at [px] pixels per dp. Call off the main thread. */
internal fun rocketBakeBack(l: RocketLayout, px: Float): Bitmap {
    val w = l.w
    val h = l.h
    val g = l.groundY
    val padX = l.padX
    val tank = l.tank
    val k = l.k
    val bmp = Bitmap.createBitmap(max(1, ceil(w * px).toInt()), max(1, ceil(h * px).toInt()), Bitmap.Config.ARGB_8888)
    val c = Canvas(bmp)
    c.scale(px, px)
    // Sky: night overhead, violet, a warm band at the horizon.
    c.drawRect(0f, 0f, w, g, linear(0f, 0f, 0f, g, ints(0xFF090B22, 0xFF1C1844, 0xFF4E2C5E, 0xFFC46A58), floatArrayOf(0f, 0.42f, 0.78f, 1f)))
    val star = Paint(Paint.ANTI_ALIAS_FLAG)
    for (i in 0 until 170) {
        val x = (hash(i * 3.7) * w).toFloat()
        val y = (hash(i * 5.1) * g * 0.62).toFloat()
        val r = (0.4 + hash(i * 9.3) * 0.9).toFloat()
        star.color = withAlpha(0xFFFFF6E6.toInt(), ((0.25 + hash(i * 2.2) * 0.65) * (1 - y / (g * 0.7))).toFloat())
        c.drawCircle(x, y, r, star)
    }
    val mx = w * 0.82f
    val my = l.top + 150
    c.drawCircle(mx, my, 46f, radial(mx, my, 46f, intArrayOf(argb(0.28f, 255, 236, 200), argb(0f, 255, 220, 180)), null))
    c.drawCircle(mx, my, 13f, radial(mx - 4, my - 4, 16f, ints(0xFFFFF8E8, 0xFFEADBBE, 0xFFBFAE90), floatArrayOf(0f, 0.6f, 1f)))
    ridge(c, w, g - 34, 46f, 1.3, 0xFF3B2550, 0.85f)
    ridge(c, w, g - 8, 30f, 4.1, 0xFF22183A, 1f)
    // Ground: dark, warm under the pad, a horizon haze.
    c.drawRect(0f, g - 4, w, h, linear(0f, g - 4, 0f, h, ints(0xFF211A2E, 0xFF140F1F, 0xFF0D0A15), floatArrayOf(0f, 0.3f, 1f)))
    c.drawRect(0f, g - 26, w, g + 4, fill(0xFFE39A7A, 0.14f).blur(10f))
    // Floodlights: two poles, their pools of light on the pad.
    for (side in intArrayOf(-1, 1)) {
        val lx = padX + side * 150
        c.drawRect(lx - 1, g - 120 * k, lx + 1, g, fill(0xFF3A3150))
        c.drawCircle(lx, g - 120 * k, 3f, fill(0xFFFFF4D6))
        c.drawCircle(lx, g - 120 * k, 16f, radial(lx, g - 120 * k, 16f, intArrayOf(argb(0.5f, 255, 240, 200), argb(0f, 255, 240, 200)), null))
    }
    c.drawOval(RectF(padX - 150, g - 18, padX + 150, g + 18), radial(padX, g, 150f, intArrayOf(argb(0.24f, 255, 226, 180), argb(0.06f, 255, 200, 150), argb(0f, 0, 0, 0)), floatArrayOf(0f, 0.6f, 1f)))

    // The service tower: two rails, X braces, arms, a red beacon.
    val tx0 = padX - 52 * k
    val tx1 = padX - 34 * k
    val tTop = g - 14 - l.rocketH * 1.12f
    val rail = linear(tx0, 0f, tx1, 0f, ints(0xFF2E2742, 0xFF5A4E72, 0xFF2A2340), floatArrayOf(0f, 0.5f, 1f))
    c.drawRect(tx0 - 1.5f, tTop, tx0 + 1.5f, g, rail)
    c.drawRect(tx1 - 1.5f, tTop, tx1 + 1.5f, g, rail)
    val brace = fill(0xFF4A3F62).stroke(1.2f)
    val step = 14 * k
    var y = tTop
    while (y < g - 14) {
        c.drawLine(tx0, y, tx1, y + step, brace)
        c.drawLine(tx1, y, tx0, y + step, brace)
        c.drawLine(tx0, y, tx1, y, brace)
        y += step
    }
    for (f in floatArrayOf(0.25f, 0.6f)) {
        val ay = g - 14 - l.rocketH * f
        c.drawRect(tx1, ay - 1.5f, padX - 15 * k, ay + 1.5f, fill(0xFF4E4366))
    }
    c.drawCircle(tx0 + 9 * k, tTop - 3, 8f, radial(tx0 + 9 * k, tTop - 3, 8f, intArrayOf(argb(0.7f, 255, 80, 60), argb(0f, 255, 60, 40)), null))
    c.drawCircle(tx0 + 9 * k, tTop - 3, 2f, fill(0xFFFF6A50))

    // The pad: a concrete deck with a lit top edge and the trench under the nozzle.
    val pw = 128 * k
    c.drawRect(padX - pw / 2 - 6, g - 2, padX + pw / 2 + 6, g + 8, fill(0xFF000000, 0.5f).blur(5f))
    c.drawRect(padX - pw / 2, g - 14, padX + pw / 2, g, linear(0f, g - 14, 0f, g, ints(0xFF6C6280, 0xFF463D58, 0xFF2C2539), floatArrayOf(0f, 0.3f, 1f)))
    c.drawRect(padX - pw / 2, g - 14, padX + pw / 2, g - 13, fill(0xFFB8AEC9, 0.8f))
    c.drawRect(padX - 12 * k, g - 14, padX + 12 * k, g, linear(0f, g - 14, 0f, g, ints(0xFF120E1A, 0xFF2A2238), null))

    // The fuel line from the tank's foot to the pad.
    val line = Path().apply {
        moveTo(tank.x + tank.w / 2, tank.y + tank.h)
        quadTo(tank.x + tank.w / 2, g - 8, padX + pw / 2 - 4, g - 8)
    }
    c.drawPath(line, fill(0xFF5B5070).stroke(4f))
    // The tank's cradle and its dark well (the fuel is drawn over it each frame).
    c.drawRect(tank.x - 4, g - 14, tank.x + tank.w + 4, g, linear(0f, g - 14, 0f, g, ints(0xFF5E5474, 0xFF2E273D), null))
    c.drawRoundRect(RectF(tank.x - 3, tank.y - 3, tank.x + tank.w + 3, tank.y + tank.h + 3), tank.w / 2 + 3, tank.w / 2 + 3, linear(tank.x, 0f, tank.x + tank.w, 0f, ints(0xFF4C4462, 0xFF9C93B4, 0xFF3A3350), floatArrayOf(0f, 0.4f, 1f)))
    c.drawRoundRect(RectF(tank.x, tank.y, tank.x + tank.w, tank.y + tank.h), tank.w / 2, tank.w / 2, linear(0f, tank.y, 0f, tank.y + tank.h, ints(0xFF1A1526, 0xFF110D1A), null))
    val label = fill(0xFFCFC6E2, 0.7f).apply {
        typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        textSize = 8f
    }
    c.drawText("FUEL", tank.x + tank.w + 7, tank.y + 10, label)

    // Grain over the sky and ground.
    val light = fill(0xFFFFFFFF, 0.035f)
    val dark = fill(0xFF000000, 0.035f)
    for (i in 0 until 5000) {
        val x = (hash(i * 1.9) * w).toFloat()
        val gy = (hash(i * 2.6 + 3) * h).toFloat()
        c.drawRect(x, gy, x + 0.7f, gy + 0.7f, if (hash(i * 4.4) > 0.5) light else dark)
    }
    bmp.prepareToDraw()
    return bmp
}

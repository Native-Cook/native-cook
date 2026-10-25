package com.ngocdevv.nativecook.blocks.inputs.dymolabelinput

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import com.ngocdevv.nativecook.shared.sigmaToRadius
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ensureActive
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin

/* Dymo Label Input — wall and shelf, the jar, the embosser and its wheel, baked once off the main thread. */

private fun hex(v: Long, a: Float = 1f): Int =
    android.graphics.Color.argb((a * 255).roundToInt(), ((v shr 16) and 255).toInt(), ((v shr 8) and 255).toInt(), (v and 255).toInt())

private fun hash(x: Int, y: Int, seed: Int): Float {
    var h = x * 374761393 + y * 668265263 + seed * 1442695041
    h = (h xor (h ushr 13)) * 1274126177
    h = h xor (h ushr 16)
    return (h and 0xFFFFFF) / 16777215f
}

private fun vnoise(x: Float, y: Float, seed: Int): Float {
    val xi = floor(x).toInt(); val yi = floor(y).toInt()
    val fx = x - xi; val fy = y - yi
    val u = fx * fx * (3 - 2 * fx); val v = fy * fy * (3 - 2 * fy)
    val a = hash(xi, yi, seed); val b = hash(xi + 1, yi, seed)
    val c = hash(xi, yi + 1, seed); val d = hash(xi + 1, yi + 1, seed)
    return a + (b - a) * u + (c - a) * v + (a - b - c + d) * u * v
}

private fun fbm(x: Float, y: Float, seed: Int): Float {
    var sum = 0f; var amp = 0.5f; var f = 1f; var norm = 0f
    for (o in 0 until 3) {
        sum += vnoise(x * f, y * f, seed + o * 17) * amp
        norm += amp; amp *= 0.5f; f *= 2f
    }
    return sum / norm
}

/** Black with alpha strength · (1 − v) of fractal noise (= a multiply of the noise), on a coarse grid drawn scaled. */
private fun CoroutineScope.darkNoise(wPt: Float, hPt: Float, fx: Float, fy: Float, stepX: Float, stepY: Float, strength: Float, seed: Int): Bitmap {
    val cols = max(2, ceil(wPt / stepX).toInt())
    val rows = max(2, ceil(hPt / stepY).toInt())
    val px = IntArray(cols * rows)
    for (j in 0 until rows) {
        ensureActive()
        for (i in 0 until cols) {
            val v = fbm(i * stepX * fx, j * stepY * fy, seed)
            px[j * cols + i] = ((strength * (1 - v) * 255).roundToInt().coerceIn(0, 255)) shl 24
        }
    }
    return Bitmap.createBitmap(px, cols, rows, Bitmap.Config.ARGB_8888)
}

private fun grainTile(size: Int, strength: Float, seed: Int): Bitmap {
    val px = IntArray(size * size)
    for (j in 0 until size) for (i in 0 until size) {
        val v = hash(i, j, seed) * 0.6f + hash(i / 2, j / 2, seed + 3) * 0.4f
        px[j * size + i] = ((strength * (1 - v) * 255).roundToInt().coerceIn(0, 255)) shl 24
    }
    return Bitmap.createBitmap(px, size, size, Bitmap.Config.ARGB_8888)
}

private fun grainPaint(tile: Bitmap) = Paint(Paint.FILTER_BITMAP_FLAG).apply {
    shader = BitmapShader(tile, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT).apply { setLocalMatrix(Matrix().apply { setScale(0.5f, 0.5f) }) }
}

private fun blur(sigma: Float) = BlurMaskFilter(sigmaToRadius(sigma), BlurMaskFilter.Blur.NORMAL)

private fun surface(wPt: Float, hPt: Float, scale: Float): Pair<Bitmap, Canvas> {
    val bmp = Bitmap.createBitmap(max(1, ceil(wPt * scale).toInt()), max(1, ceil(hPt * scale).toInt()), Bitmap.Config.ARGB_8888)
    val c = Canvas(bmp)
    c.scale(bmp.width / wPt, bmp.height / hPt)
    return bmp to c
}

internal fun CoroutineScope.dyBakeWall(g: DymoGeo, scale: Float): Bitmap {
    val (bmp, c) = surface(g.w, g.h, scale)
    val p = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    p.shader = LinearGradient(0f, 0f, 0f, g.shelfY, intArrayOf(hex(0xF2EADF), hex(0xEADFD0), hex(0xDFD1BE)), floatArrayOf(0f, 0.6f, 1f), Shader.TileMode.CLAMP)
    c.drawRect(0f, 0f, g.w, g.h, p)
    val grain = grainTile(96, 0.05f, 2)
    c.drawRect(0f, 0f, g.w, g.h, grainPaint(grain))
    // The shelf: a thick oak plank with a lit front edge, and the counter below it.
    val sh = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = hex(0x5B3D22, 0.28f); maskFilter = blur(12f) }
    c.drawRect(-20f, g.shelfY + 12, g.w + 20, g.shelfY + 30, sh)
    p.shader = LinearGradient(0f, g.shelfY - 10, 0f, g.shelfY + 22, intArrayOf(hex(0xD2A574), hex(0xB88653), hex(0x8E6238)), floatArrayOf(0f, 0.4f, 1f), Shader.TileMode.CLAMP)
    c.drawRect(0f, g.shelfY - 8, g.w, g.shelfY + 20, p)
    p.shader = null
    ensureActive()
    val wood = darkNoise(g.w, 28f, 0.01f, 0.35f, 4f, 0.5f, 0.22f, 6)
    c.drawBitmap(wood, null, RectF(0f, g.shelfY - 8, g.w, g.shelfY + 20), p)
    wood.recycle()
    p.color = hex(0xF3D3A7, 0.7f)
    c.drawRect(0f, g.shelfY - 8, g.w, g.shelfY - 6.8f, p)
    p.color = hex(0x6E4826, 0.6f)
    c.drawRect(0f, g.shelfY + 1, g.w, g.shelfY + 2, p)
    // The counter where the embosser sits: warm linen-grey with a soft light pool.
    p.shader = RadialGradient(g.wheelX, g.wheelY - 20, g.w * 0.9f, intArrayOf(hex(0xE7DDCF), hex(0xD9CCBA), hex(0xC5B59F)), floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP)
    c.drawRect(0f, g.shelfY + 20, g.w, g.h, p)
    p.shader = null
    val cg = grainTile(96, 0.06f, 8)
    c.drawRect(0f, g.shelfY + 20, g.w, g.h, grainPaint(cg))
    grain.recycle(); cg.recycle()
    return bmp
}

/** A fresh jar of oats on its own: (jarW + 60) × (jarH + 50) pt, the jar's base 20 pt above its bottom edge. */
internal fun CoroutineScope.dyBakeJar(g: DymoGeo, scale: Float): Bitmap {
    val ww = g.jarW + 60
    val hh = g.jarH + 50
    val (bmp, c) = surface(ww, hh, scale)
    val x0 = 30f
    val base = 30 + g.jarH
    val top = 30f + 22
    val body = RectF(x0, top, x0 + g.jarW, base)
    val bodyPath = Path().apply { addRoundRect(body, 18f, 18f, Path.Direction.CW) }
    val sh = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = hex(0x4A301A, 0.32f); maskFilter = blur(6f) }
    c.drawOval(RectF(x0 - 4, base - 6, x0 + g.jarW + 4, base + 6), sh)
    // Oats inside, up to 80 %: a warm base with hundreds of flakes.
    c.save()
    c.clipPath(bodyPath)
    val fillTop = top + (base - top) * 0.2f
    val p = Paint(Paint.ANTI_ALIAS_FLAG)
    p.shader = LinearGradient(0f, fillTop, 0f, base, intArrayOf(hex(0xE2CB9E), hex(0xCDAE7A), hex(0xB08F5D)), floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)
    c.drawRect(x0, fillTop, x0 + g.jarW, base, p)
    p.shader = null
    var seed = 7L
    fun rnd(): Float {
        seed = (seed * 16807) % 2147483647
        return (seed / 2147483647.0).toFloat()
    }
    val oval = RectF()
    for (k in 0 until 520) {
        if (k % 64 == 0) ensureActive()
        val fx = x0 + rnd() * g.jarW
        val fy = fillTop - 2 + rnd() * (base - fillTop + 2)
        val fw = 4 + rnd() * 5
        val fh = 2.4f + rnd() * 2.6f
        val shade = rnd()
        p.color = if (shade > 0.7f) hex(0xF4E3BF, 0.9f) else if (shade > 0.35f) hex(0xD8BC8A, 0.9f) else hex(0xA9875A, 0.85f)
        c.save()
        c.translate(fx, fy)
        c.rotate(rnd() * 180)
        oval.set(-fw / 2, -fh / 2, fw / 2, fh / 2)
        c.drawOval(oval, p)
        c.restore()
    }
    // Glass: faint tint, edges and two tall highlights.
    p.shader = LinearGradient(
        x0, 0f, x0 + g.jarW, 0f,
        intArrayOf(hex(0x6E8A86, 0.28f), hex(0xFFFFFF, 0.02f), hex(0xFFFFFF, 0f), hex(0x5E7A76, 0.3f)), floatArrayOf(0f, 0.25f, 0.7f, 1f), Shader.TileMode.CLAMP,
    )
    c.drawRoundRect(body, 18f, 18f, p)
    p.shader = null
    p.color = hex(0xFFFFFF, 0.5f)
    c.drawRoundRect(RectF(x0 + g.jarW * 0.12f, top + 10, x0 + g.jarW * 0.12f + 5, base - 16), 2.5f, 2.5f, p)
    p.color = hex(0xFFFFFF, 0.25f)
    c.drawRoundRect(RectF(x0 + g.jarW * 0.82f, top + 14, x0 + g.jarW * 0.82f + 3, base - 26), 1.5f, 1.5f, p)
    c.restore()
    val rim = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 1.6f; color = hex(0x8FA6A2, 0.55f) }
    c.drawRoundRect(body, 18f, 18f, rim)
    // Screw lid: brushed brass with ridges.
    val lid = RectF(x0 + 6, 30f, x0 + g.jarW - 6, 54f)
    p.shader = LinearGradient(0f, 30f, 0f, 54f, intArrayOf(hex(0xF3DEA4), hex(0xC7A15A), hex(0x8F6E33), hex(0xB89250)), floatArrayOf(0f, 0.35f, 0.75f, 1f), Shader.TileMode.CLAMP)
    c.drawRoundRect(lid, 5f, 5f, p)
    p.shader = null
    val ridge = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 1f; strokeCap = Paint.Cap.ROUND }
    var x = x0 + 10
    while (x < x0 + g.jarW - 10) {
        ridge.color = if (x.roundToInt() % 2 == 0) hex(0xFFF1C8, 0.45f) else hex(0x7C5C28, 0.45f)
        c.drawLine(x, 33f, x, 51f, ridge)
        x += 3.2f
    }
    return bmp
}

/** The embosser's body, centred on the wheel: side 2 × wheelR × 3.6 pt. */
internal fun CoroutineScope.dyBakeBody(g: DymoGeo, scale: Float): Bitmap {
    val side = g.wheelR * 3.6f * 2
    val (bmp, c) = surface(side, side, scale)
    val o = side / 2
    val r = g.wheelR
    val path = Path().apply {
        addCircle(o, o, r + 18, Path.Direction.CW)
        addRoundRect(RectF(o, o - r * 0.35f, o + r * 2.9f, o - r * 0.35f + r * 1.05f), r * 0.5f, r * 0.5f, Path.Direction.CW)
        // A union, as Skia's path builder fills two overlapping contours (non-zero winding).
        fillType = Path.FillType.WINDING
    }
    val sh = Paint(Paint.ANTI_ALIAS_FLAG)
    sh.color = hex(0x3D2A18, 0.28f); sh.maskFilter = blur(14f)
    c.save(); c.translate(4f, 14f); c.drawPath(path, sh); c.restore()
    sh.color = hex(0x2E1F12, 0.35f); sh.maskFilter = blur(3f)
    c.save(); c.translate(1f, 3f); c.drawPath(path, sh); c.restore()
    ensureActive()
    val p = Paint(Paint.ANTI_ALIAS_FLAG)
    p.shader = LinearGradient(0f, o - r - 18, 0f, o + r + 18, intArrayOf(hex(0x4E6C66), hex(0x3B5752), hex(0x2B403C), hex(0x22332F)), floatArrayOf(0f, 0.35f, 0.75f, 1f), Shader.TileMode.CLAMP)
    c.drawPath(path, p)
    p.shader = RadialGradient(o - r * 0.4f, o - r * 0.8f, r * 2.2f, intArrayOf(hex(0xFFFFFF, 0.2f), hex(0xFFFFFF, 0.04f), hex(0xFFFFFF, 0f)), floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)
    c.drawPath(path, p)
    p.shader = null
    val edge = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 1f; color = hex(0x9FC1BA, 0.45f) }
    // Skia strokes each contour of the path, the overlap included.
    c.drawPath(path, edge)
    // The wheel's well.
    p.shader = RadialGradient(o, o, r + 6, intArrayOf(hex(0x101816), hex(0x18231F), hex(0x0C1210)), floatArrayOf(0f, 0.85f, 1f), Shader.TileMode.CLAMP)
    c.drawCircle(o, o, r + 5, p)
    p.shader = null
    // A small embossed brand plate on the handle.
    p.color = hex(0xC9D6D2, 0.18f)
    c.drawRoundRect(RectF(o + r * 1.45f, o - r * 0.08f, o + r * 2.55f, o + r * 0.24f), r * 0.16f, r * 0.16f, p)
    return bmp
}

/** The character wheel: a dark disc with raised white characters round the rim, slot 0 at the top. */
internal fun CoroutineScope.dyBakeWheel(g: DymoGeo, scale: Float): Bitmap {
    val side = g.wheelR * 2 + 8
    val (bmp, c) = surface(side, side, scale)
    val o = side / 2
    val r = g.wheelR
    val p = Paint(Paint.ANTI_ALIAS_FLAG)
    p.shader = RadialGradient(o, o, r, intArrayOf(hex(0x3A3E45), hex(0x2A2D33), hex(0x1B1D21)), floatArrayOf(0f, 0.75f, 1f), Shader.TileMode.CLAMP)
    c.drawCircle(o, o, r, p)
    p.shader = null
    val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 1f; color = hex(0xFFFFFF, 0.12f) }
    c.drawCircle(o, o, r - 0.8f, ring)
    c.drawCircle(o, o, r * 0.62f, ring)
    val font = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply { typeface = dyWeight(700); textSize = 12f }
    for (j in WHEEL.indices) {
        ensureActive()
        val ch = WHEEL[j]
        if (ch == ' ') continue
        val s = ch.toString()
        val w = font.measureText(s)
        c.save()
        c.rotate(j * STEP, o, o)
        font.color = hex(0x000000, 0.5f)
        c.drawText(s, o - w / 2 + 0.5f, o - r * 0.73f + 0.8f, font)
        font.color = hex(0xECE8E0)
        c.drawText(s, o - w / 2, o - r * 0.73f, font)
        c.restore()
    }
    // Grip cut-outs in the hub.
    p.shader = RadialGradient(o - 6, o - 8, r * 0.55f, intArrayOf(hex(0x5A5F68), hex(0x33363C), hex(0x202226)), floatArrayOf(0f, 0.6f, 1f), Shader.TileMode.CLAMP)
    c.drawCircle(o, o, r * 0.5f, p)
    p.shader = null
    p.color = hex(0x16181B)
    for (k in 0 until 6) {
        val a = k / 6f * PI.toFloat() * 2
        c.drawCircle(o + cos(a) * r * 0.32f, o + sin(a) * r * 0.32f, r * 0.075f, p)
    }
    c.drawCircle(o, o, r * 0.1f, ring)
    return bmp
}

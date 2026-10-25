package com.ngocdevv.nativecook.blocks.inputs.crossstitchinput

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
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.roundToInt

/* Cross Stitch Input — the table, hoop, cloth and printed border, baked once off the main thread. */

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

/** Black with alpha strength · (1 − v) of fractal noise: the same as a multiply of the noise. Coarse grid, drawn scaled. */
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

private fun blur(sigma: Float) = BlurMaskFilter(sigmaToRadius(sigma), BlurMaskFilter.Blur.NORMAL)

/** The table, hoop, cloth and printed border, at [scale] px per pt. */
internal fun CoroutineScope.csBakeScene(g: StitchGeo, scale: Float, border: Pair<FloatArray, FloatArray>): Bitmap {
    val bmp = Bitmap.createBitmap(ceil(g.w * scale).toInt(), ceil(g.h * scale).toInt(), Bitmap.Config.ARGB_8888)
    val c = Canvas(bmp)
    c.scale(scale, scale)
    val p = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    // Warm paper backdrop with a three-stop wash and grain.
    p.shader = LinearGradient(0f, 0f, g.w * 0.3f, g.h, intArrayOf(hex(0xF3EBDD), hex(0xEADFCB), hex(0xE2D3BC)), floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP)
    c.drawRect(0f, 0f, g.w, g.h, p)
    val grain = grainTile(96, 0.05f, 7)
    p.shader = BitmapShader(grain, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT).apply { setLocalMatrix(Matrix().apply { setScale(0.5f, 0.5f) }) }
    c.drawRect(0f, 0f, g.w, g.h, p)
    p.shader = null
    ensureActive()
    // The hoop's two shadows on the table.
    val sh = Paint(Paint.ANTI_ALIAS_FLAG)
    sh.color = hex(0x5A4128, 0.2f); sh.maskFilter = blur(18f)
    c.drawCircle(g.cx, g.cy + 16, g.hoopR + 2, sh)
    sh.color = hex(0x4A3220, 0.32f); sh.maskFilter = blur(2.5f)
    c.drawCircle(g.cx, g.cy + 2, g.hoopR, sh)

    // Cloth: aida's square blocks with holes at the corners, a slight sheen, grain and dimming at the hoop's edge.
    val fr = g.fabricR + 3
    c.save()
    c.clipPath(Path().apply { addCircle(g.cx, g.cy, fr, Path.Direction.CW) })
    p.shader = RadialGradient(g.cx - fr * 0.35f, g.cy - fr * 0.45f, fr * 1.6f, intArrayOf(hex(0xFBF7EE), hex(0xF2EBDD), hex(0xE6DCC8)), floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)
    c.drawCircle(g.cx, g.cy, fr, p)
    p.shader = null
    val weave = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = g.cell * 0.18f; color = hex(0xFFFFFF, 0.32f) }
    val lines = Path()
    val half = CELLS / 2 + 1
    for (k in -half..half) for (off in floatArrayOf(-0.25f, 0.25f)) {
        val v = (k + 0.5f + off) * g.cell
        lines.moveTo(g.cx - fr, g.cy + v); lines.lineTo(g.cx + fr, g.cy + v)
        lines.moveTo(g.cx + v, g.cy - fr); lines.lineTo(g.cx + v, g.cy + fr)
    }
    c.drawPath(lines, weave)
    ensureActive()
    val holes = Path()
    for (i in -half..half) for (j in -half..half) holes.addCircle(g.cx + i * g.cell, g.cy + j * g.cell, g.cell * 0.11f, Path.Direction.CW)
    p.color = hex(0x8C7A62, 0.38f)
    c.drawPath(holes, p)
    val tooth = darkNoise(fr * 2, fr * 2, 0.35f, 0.35f, 1f, 1f, 0.08f, 3)
    c.drawBitmap(tooth, null, RectF(g.cx - fr, g.cy - fr, g.cx + fr, g.cy + fr), p)
    tooth.recycle()
    p.shader = RadialGradient(g.cx, g.cy, fr, intArrayOf(hex(0x5B4630, 0f), hex(0x5B4630, 0f), hex(0x5B4630, 0.24f)), floatArrayOf(0f, 0.82f, 1f), Shader.TileMode.CLAMP)
    c.drawCircle(g.cx, g.cy, fr, p)
    p.shader = null
    // The border motif, already worked.
    StitchPainter().drawStitches(c, g, border.first, border.second, border.first.size.toFloat(), BorderRGB, 1f)
    c.restore()

    // Hoop: the inner ring just shows, the outer ring is a lathe-turned beech band with a bevel.
    fun ring(r0: Float, r1: Float, stops: LongArray) {
        val rp = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = r1 - r0
            shader = RadialGradient(
                g.cx, g.cy, r1, IntArray(4) { hex(stops[it]) },
                floatArrayOf(r0 / r1, (r0 + (r1 - r0) * 0.3f) / r1, (r0 + (r1 - r0) * 0.7f) / r1, 1f), Shader.TileMode.CLAMP,
            )
        }
        c.drawCircle(g.cx, g.cy, (r0 + r1) / 2, rp)
    }
    ring(g.fabricR - 1, g.fabricR + 4, longArrayOf(0x9A6B3A, 0xC9995F, 0xD8AE76, 0xB07C45))
    ring(g.fabricR + 3, g.hoopR, longArrayOf(0x8E5F31, 0xD9AD73, 0xE8C38E, 0xA8733E))
    ensureActive()
    val side = g.hoopR * 2
    val wood = darkNoise(side, side, 0.02f, 0.45f, 3f, 0.6f, 0.18f, 11)
    val woodPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = g.hoopR - g.fabricR - 3
        shader = BitmapShader(wood, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).apply {
            setLocalMatrix(Matrix().apply {
                setScale(side / wood.width, side / wood.height)
                postTranslate(g.cx - g.hoopR, g.cy - g.hoopR)
            })
        }
    }
    c.drawCircle(g.cx, g.cy, (g.fabricR + 3 + g.hoopR) / 2, woodPaint)
    val edge = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 1f; color = hex(0xFFF4E0, 0.45f) }
    c.drawCircle(g.cx, g.cy - 0.5f, g.hoopR - 1.2f, edge)
    edge.color = hex(0x5E3D1E, 0.45f)
    c.drawCircle(g.cx, g.cy + 0.5f, g.fabricR + 3.2f, edge)

    // The brass screw clamp at the top: two wooden lugs and a turned screw through them.
    val top = g.cy - g.hoopR
    val lug = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = LinearGradient(0f, top - 12, 0f, top + 12, intArrayOf(hex(0xE7C18B), hex(0xC4915A), hex(0x9C6B39)), floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP)
    }
    val lugShadow = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = hex(0x4A3220, 0.3f); maskFilter = blur(3f) }
    for (dx in floatArrayOf(-11f, 3f)) {
        val r = RectF(g.cx + dx, top - 13, g.cx + dx + 8, top + 9)
        c.save(); c.translate(0.5f, 2.5f); c.drawRoundRect(r, 2.5f, 2.5f, lugShadow); c.restore()
        c.drawRoundRect(r, 2.5f, 2.5f, lug)
    }
    val brass = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = LinearGradient(0f, top - 9, 0f, top - 1, intArrayOf(hex(0xFFF0B8), hex(0xD9A93E), hex(0x8D6418), hex(0xC99733)), floatArrayOf(0f, 0.35f, 0.75f, 1f), Shader.TileMode.CLAMP)
    }
    c.drawRoundRect(RectF(g.cx - 22, top - 9, g.cx + 22, top - 1), 3f, 3f, brass)
    c.drawRoundRect(RectF(g.cx + 20, top - 12, g.cx + 29, top + 2), 3f, 3f, brass)
    val screw = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 0.7f; color = hex(0x6E4E14, 0.6f) }
    for (k in 0 until 9) {
        val x = g.cx - 20 + k * 4.6f
        c.drawLine(x, top - 8.5f, x + 1.6f, top - 1.5f, screw)
    }
    grain.recycle(); wood.recycle()
    return bmp
}

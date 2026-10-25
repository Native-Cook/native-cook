package com.ngocdevv.nativecook.blocks.text.embroideryheadline

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
import android.graphics.Typeface
import com.ngocdevv.nativecook.shared.sigmaToRadius
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ensureActive
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

/* Embroidery Headline — the satin, cut from the rasterised headline, and the table, hoop and linen; built once off the main thread. */

private fun hex(v: Long, a: Float = 1f): Int {
    val r = ((v shr 16) and 255).toInt()
    val g = ((v shr 8) and 255).toInt()
    val b = (v and 255).toInt()
    return android.graphics.Color.argb((a * 255).roundToInt(), r, g, b)
}

internal val EmbSerifBold: Typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)

/** Turns the two lines into satin, the text fitted into the hoop. */
internal fun CoroutineScope.embBuildSatin(g: EmbGeo, lines: Array<String>): EmbSatin {
    val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply { typeface = EmbSerifBold; textSize = 100f }
    val widest = max(paint.measureText(lines[0]), paint.measureText(lines[1] + "."))
    val size = min(112f, 100 * g.fabricR * 1.5f / widest)
    paint.textSize = size
    val px = 2
    val big = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = EmbSerifBold; textSize = size * px; color = android.graphics.Color.WHITE }
    val base1 = g.cy - size * 0.14f
    val base2 = base1 + size * 1.0f
    val glyphs = ArrayList<EmbGlyph>()
    var knot = floatArrayOf(0f, 0f, 0f)
    lines.forEachIndexed { li, line ->
        val widths = FloatArray(line.length)
        paint.getTextWidths(line, widths)
        val full = paint.measureText(line) + if (li == 1) size * 0.3f else 0f
        var x = g.cx - full / 2
        val base = if (li == 0) base1 else base2
        line.forEachIndexed { i, ch ->
            ensureActive()
            val pad = size * 0.25f
            val wpt = widths[i] + pad * 2
            val hpt = size * 1.3f
            val top = base - size * 0.95f
            val iw = ceil(wpt * px).toInt()
            val ih = ceil(hpt * px).toInt()
            val bmp = Bitmap.createBitmap(iw, ih, Bitmap.Config.ALPHA_8)
            Canvas(bmp).drawText(ch.toString(), pad * px, (base - top) * px, big)
            val stride = bmp.rowBytes
            val a2 = ByteArray(stride * ih)
            bmp.copyPixelsToBuffer(java.nio.ByteBuffer.wrap(a2))
            bmp.recycle()
            val angle = EmbAngles[ch] ?: 50f
            glyphs.add(cutSatin(a2, stride, iw, ih, px, x - pad, top, angle, if (li == 0) EmbMadder else EmbIndigo))
            x += widths[i]
        }
        if (li == 1) knot = floatArrayOf(x + size * 0.14f, base - size * 0.095f, size * 0.088f)
    }
    return EmbSatin(glyphs, knot, EmbIndigo)
}

private fun CoroutineScope.cutSatin(
    pixels: ByteArray, stride: Int, iw: Int, ih: Int, px: Int,
    ox: Float, oy: Float, angleDeg: Float, rgb: FloatArray,
): EmbGlyph {
    val segs = ArrayList<Float>()
    val threshold = (0.45f * 255).toInt()
    fun inside(x: Float, y: Float): Boolean {
        val i = (x * px).roundToInt()
        val j = (y * px).roundToInt()
        if (i < 0 || j < 0 || i >= iw || j >= ih) return false
        return (pixels[j * stride + i].toInt() and 255) > threshold
    }
    val a = angleDeg * PI.toFloat() / 180
    val dx = cos(a); val dy = -sin(a)
    val nx = sin(a); val ny = cos(a)
    val w = iw.toFloat() / px
    val h = ih.toFloat() / px
    val cx = w / 2; val cy = h / 2
    val r = hypot(w, h) / 2
    val step = 0.5f
    var o = -r
    while (o <= r) {
        ensureActive()
        var runStart = Float.NaN
        var s = -r
        while (s <= r + step) {
            val x = cx + nx * o + dx * s
            val y = cy + ny * o + dy * s
            val isIn = s <= r && inside(x, y)
            if (isIn && runStart.isNaN()) runStart = s
            if (!isIn && !runStart.isNaN()) {
                val s0 = runStart - 0.35f
                val s1 = s - step + 0.35f
                if (s1 - s0 > 1.2f) {
                    segs.add(ox + cx + nx * o + dx * s0); segs.add(oy + cy + ny * o + dy * s0)
                    segs.add(ox + cx + nx * o + dx * s1); segs.add(oy + cy + ny * o + dy * s1)
                }
                runStart = Float.NaN
            }
            s += step
        }
        o += EMB_SPACING
    }
    val d = (angleDeg - EMB_LIGHT) * PI.toFloat() / 180
    return EmbGlyph(segs.toFloatArray(), segs.size / 4, rgb, cos(d).pow(2))
}

// Value noise on an integer lattice (hash, smoothstep), summed over three octaves like Skia's fractal noise.
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

/**
 * A darkening layer equal to a multiply of fractal noise at [strength]: black with alpha
 * strength · (1 − v), sampled on a coarse grid (stepX × stepY pt) and drawn scaled with filtering.
 */
private fun CoroutineScope.darkNoise(wPt: Float, hPt: Float, fx: Float, fy: Float, stepX: Float, stepY: Float, strength: Float, seed: Int): Bitmap {
    val cols = max(2, ceil(wPt / stepX).toInt())
    val rows = max(2, ceil(hPt / stepY).toInt())
    val px = IntArray(cols * rows)
    for (j in 0 until rows) {
        ensureActive()
        val y = j * stepY
        for (i in 0 until cols) {
            val v = fbm(i * stepX * fx, y * fy, seed)
            val a = (strength * (1 - v) * 255).roundToInt().coerceIn(0, 255)
            px[j * cols + i] = a shl 24
        }
    }
    return Bitmap.createBitmap(px, cols, rows, Bitmap.Config.ARGB_8888)
}

/** A small repeating tile of grain: white or black specks, an overlay of fine noise at [strength]. */
private fun grainTile(size: Int, strength: Float, seed: Int): Bitmap {
    val px = IntArray(size * size)
    for (j in 0 until size) for (i in 0 until size) {
        val v = (hash(i, j, seed) * 0.6f + hash(i / 2, j / 2, seed + 3) * 0.4f) * 2 - 1
        val a = (kotlin.math.abs(v) * strength * 255).roundToInt().coerceIn(0, 255)
        px[j * size + i] = (a shl 24) or (if (v > 0) 0xFFFFFF else 0)
    }
    return Bitmap.createBitmap(px, size, size, Bitmap.Config.ARGB_8888)
}

/** Table, hoop and linen at [scale] px per pt. */
internal fun CoroutineScope.embBakeScene(g: EmbGeo, scale: Float): Bitmap {
    val bmp = Bitmap.createBitmap(ceil(g.w * scale).toInt(), ceil(g.h * scale).toInt(), Bitmap.Config.ARGB_8888)
    val c = Canvas(bmp)
    c.scale(scale, scale)
    val p = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    // A dark sage tablecloth with a vignette and grain.
    p.shader = RadialGradient(
        g.cx, g.cy - g.hoopR * 0.2f, max(g.w, g.h) * 0.75f,
        intArrayOf(hex(0x3A4A3F), hex(0x2C3A31), hex(0x1B241F)), floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP,
    )
    c.drawRect(0f, 0f, g.w, g.h, p)
    ensureActive()
    val grain = grainTile(96, 0.07f, 5)
    p.shader = BitmapShader(grain, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT).apply {
        setLocalMatrix(Matrix().apply { setScale(0.5f, 0.5f) })
    }
    c.drawRect(0f, 0f, g.w, g.h, p)
    p.shader = null

    val amb = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = hex(0x0B100D, 0.45f); maskFilter = BlurMaskFilter(sigmaToRadius(22f), BlurMaskFilter.Blur.NORMAL)
    }
    c.drawCircle(g.cx, g.cy + 18, g.hoopR + 4, amb)
    amb.color = hex(0x0B100D, 0.5f)
    amb.maskFilter = BlurMaskFilter(sigmaToRadius(3f), BlurMaskFilter.Blur.NORMAL)
    c.drawCircle(g.cx, g.cy + 2.5f, g.hoopR, amb)

    // Linen: warm oatmeal with slubby weft and warp threads.
    val fr = g.fabricR + 3
    c.save()
    c.clipPath(Path().apply { addCircle(g.cx, g.cy, fr, Path.Direction.CW) })
    p.shader = RadialGradient(
        g.cx - fr * 0.3f, g.cy - fr * 0.5f, fr * 1.7f,
        intArrayOf(hex(0xF1E8D8), hex(0xE6DAC4), hex(0xD4C5AC)), floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP,
    )
    c.drawCircle(g.cx, g.cy, fr, p)
    p.shader = null
    val thread = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    var seed = 1L
    fun rnd(): Float {
        seed = (seed * 16807) % 2147483647
        return seed / 2147483647f
    }
    var k = -fr
    while (k <= fr) {
        ensureActive()
        for (vertical in booleanArrayOf(false, true)) {
            thread.strokeWidth = 0.4f + rnd() * 0.45f
            val light = rnd() > 0.5f
            thread.color = if (light) hex(0xFFFAF0, 0.12f + rnd() * 0.14f) else hex(0x9C8A6C, 0.05f + rnd() * 0.08f)
            if (vertical) c.drawLine(g.cx + k, g.cy - fr, g.cx + k, g.cy + fr, thread)
            else c.drawLine(g.cx - fr, g.cy + k, g.cx + fr, g.cy + k, thread)
        }
        k += 1.15f
    }
    val slub = darkNoise(fr * 2, fr * 2, 0.04f, 0.6f, 2f, 0.5f, 0.1f, 9)
    c.drawBitmap(slub, null, RectF(g.cx - fr, g.cy - fr, g.cx + fr, g.cy + fr), p)
    slub.recycle()
    p.shader = RadialGradient(
        g.cx, g.cy, fr,
        intArrayOf(hex(0x4A3A26, 0f), hex(0x4A3A26, 0f), hex(0x4A3A26, 0.3f)), floatArrayOf(0f, 0.8f, 1f), Shader.TileMode.CLAMP,
    )
    c.drawCircle(g.cx, g.cy, fr, p)
    p.shader = null
    c.restore()

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
    ring(g.fabricR - 1, g.fabricR + 4, longArrayOf(0x8F6235, 0xC29257, 0xD3A970, 0xA87540))
    ring(g.fabricR + 3, g.hoopR, longArrayOf(0x7F5329, 0xD2A46A, 0xE3BC86, 0x9E6A37))
    ensureActive()
    // Wood grain: the same noise, streaky along x, inside the outer ring only.
    val side = g.hoopR * 2
    val wood = darkNoise(side, side, 0.02f, 0.45f, 3f, 0.6f, 0.2f, 11)
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
    val edge = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 1f; color = hex(0xFFF1DA, 0.4f) }
    c.drawCircle(g.cx, g.cy - 0.5f, g.hoopR - 1.2f, edge)
    edge.color = hex(0x4E3218, 0.5f)
    c.drawCircle(g.cx, g.cy + 0.5f, g.fabricR + 3.2f, edge)
    // Brass screw clamp.
    val top = g.cy - g.hoopR
    val lug = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = LinearGradient(0f, top - 12, 0f, top + 12, intArrayOf(hex(0xE2B880), hex(0xBD8A52), hex(0x936234)), floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP)
    }
    for (dx in floatArrayOf(-11f, 3f)) c.drawRoundRect(RectF(g.cx + dx, top - 13, g.cx + dx + 8, top + 9), 2.5f, 2.5f, lug)
    val brass = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = LinearGradient(
            0f, top - 9, 0f, top - 1,
            intArrayOf(hex(0xFFF0B8), hex(0xD9A93E), hex(0x8D6418), hex(0xC99733)), floatArrayOf(0f, 0.35f, 0.75f, 1f), Shader.TileMode.CLAMP,
        )
    }
    c.drawRoundRect(RectF(g.cx - 22, top - 9, g.cx + 22, top - 1), 3f, 3f, brass)
    c.drawRoundRect(RectF(g.cx + 20, top - 12, g.cx + 29, top + 2), 3f, 3f, brass)
    grain.recycle()
    wood.recycle()
    return bmp
}

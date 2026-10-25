package com.ngocdevv.nativecook.blocks.forms.icingnameform

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
import android.graphics.SweepGradient
import com.ngocdevv.nativecook.shared.sigmaToRadius
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ensureActive
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/* Icing Name Form — the marble counter and the cake, baked once off the main thread. */

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

/** Turbulence like Skia's: a sum of |noise| over octaves, 0..1. */
private fun turbulence(x: Float, y: Float, octaves: Int, seed: Int): Float {
    var sum = 0f; var amp = 1f; var f = 1f; var norm = 0f
    for (o in 0 until octaves) {
        sum += abs(vnoise(x * f, y * f, seed + o * 17) * 2 - 1) * amp
        norm += amp; amp *= 0.5f; f *= 2f
    }
    return min(1f, sum / norm * 1.6f)
}

/**
 * A layer for multiplying by turbulence at [strength] (black with alpha strength · (1 − turb)), or
 * for an overlay of it ([overlay]: white or black specks by the sign of turb − 0.5), on a coarse
 * grid drawn scaled with filtering.
 */
private fun CoroutineScope.turbLayer(wPt: Float, hPt: Float, fx: Float, fy: Float, octaves: Int, step: Float, strength: Float, overlay: Boolean, seed: Int): Bitmap {
    val cols = max(2, ceil(wPt / step).toInt())
    val rows = max(2, ceil(hPt / step).toInt())
    val px = IntArray(cols * rows)
    for (j in 0 until rows) {
        ensureActive()
        for (i in 0 until cols) {
            val v = turbulence(i * step * fx, j * step * fy, octaves, seed)
            px[j * cols + i] = if (overlay) {
                val d = v * 2 - 1
                ((abs(d) * strength * 255).roundToInt().coerceIn(0, 255) shl 24) or (if (d > 0) 0xFFFFFF else 0)
            } else ((strength * (1 - v) * 255).roundToInt().coerceIn(0, 255)) shl 24
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

/** The marble counter. */
internal fun CoroutineScope.icBakeTable(g: IcingGeo, scale: Float): Bitmap {
    val (bmp, c) = surface(g.w, g.h, scale)
    val p = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    p.shader = LinearGradient(0f, 0f, g.w * 0.4f, g.h, intArrayOf(hex(0xF7F1EA), hex(0xEFE6DC), hex(0xE6DACD)), floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)
    c.drawRect(0f, 0f, g.w, g.h, p)
    p.shader = null
    val vein = turbLayer(g.w, g.h, 0.006f, 0.018f, 4, 2.5f, 0.1f, false, 21)
    c.drawBitmap(vein, null, RectF(0f, 0f, g.w, g.h), p)
    vein.recycle()
    val grain = grainTile(96, 0.04f, 4)
    c.drawRect(0f, 0f, g.w, g.h, grainPaint(grain))
    grain.recycle()
    return bmp
}

private fun rosette(c: Canvas, x: Float, y: Float, r: Float, a0: Float) {
    val path = Path()
    val turns = 2.1f
    for (k in 0..60) {
        val u = k / 60f
        val ang = a0 + u * turns * PI.toFloat() * 2
        val rr = r * (0.12f + 0.72f * u)
        if (k == 0) path.moveTo(x + rr * cos(ang), y + rr * sin(ang)) else path.lineTo(x + rr * cos(ang), y + rr * sin(ang))
    }
    val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND }
    val sh = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = hex(0x6A3A2A, 0.3f); maskFilter = blur(r * 0.25f) }
    c.drawCircle(x + r * 0.18f, y + r * 0.3f, r * 0.92f, sh)
    val layers = listOf(Triple(0.46f, 0xD79AA6L, 0f) to 1f, Triple(0.34f, 0xF0C3CBL, 0.06f) to 1f, Triple(0.14f, 0xFCE7EAL, 0.12f) to 0.85f)
    for ((l, a) in layers) {
        p.strokeWidth = r * l.first
        p.color = hex(l.second, a)
        c.save(); c.translate(-r * l.third, -r * l.third * 1.2f); c.drawPath(path, p); c.restore()
    }
    // The star tip's grooves running along the swirl.
    p.strokeWidth = r * 0.045f
    for (off in floatArrayOf(-0.13f, 0.13f)) {
        p.color = if (off < 0) hex(0xFFF3F5, 0.55f) else hex(0xB9707F, 0.55f)
        c.save(); c.translate(r * off * 0.6f, r * off); c.drawPath(path, p); c.restore()
    }
}

/** The cake, centred in a square of side (2 boardR + 48) pt. */
internal fun CoroutineScope.icBakeCake(g: IcingGeo, scale: Float): Bitmap {
    val side = g.boardR * 2 + 48
    val (bmp, c) = surface(side, side, scale)
    val o = side / 2
    val r = g.r
    val p = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    // Shadows of the drum and of the cake on it.
    val sh = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = hex(0x5C3B25, 0.22f); maskFilter = blur(14f) }
    c.drawCircle(o + 3, o + 10, g.boardR, sh)
    // Gold cake drum with crinkled foil.
    p.shader = SweepGradient(o, o, intArrayOf(hex(0xE9CF8E), hex(0xB98E3E), hex(0xF6E2A8), hex(0xA97C30), hex(0xE9CF8E)), floatArrayOf(0f, 0.28f, 0.5f, 0.78f, 1f))
    c.drawCircle(o, o, g.boardR, p)
    p.shader = null
    ensureActive()
    val crinkle = turbLayer(g.boardR * 2, g.boardR * 2, 0.12f, 0.12f, 2, 1f, 0.18f, true, 8)
    c.save()
    c.clipPath(Path().apply { addCircle(o, o, g.boardR, Path.Direction.CW) })
    c.drawBitmap(crinkle, null, RectF(o - g.boardR, o - g.boardR, o + g.boardR, o + g.boardR), p)
    c.restore()
    crinkle.recycle()
    val edge = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 1.2f; color = hex(0xFFF4D0, 0.6f) }
    c.drawCircle(o, o, g.boardR - 0.8f, edge)
    sh.color = hex(0x4A2C18, 0.35f); sh.maskFilter = blur(5f)
    c.drawCircle(o + 2, o + 5, r, sh)
    // Fondant top with a soft rounded lip at the edge.
    p.shader = RadialGradient(o - r * 0.2f, o - r * 0.25f, r * 1.25f, intArrayOf(hex(0xFBF4EA), hex(0xF4E8D9), hex(0xE9D8C3)), floatArrayOf(0f, 0.6f, 1f), Shader.TileMode.CLAMP)
    c.drawCircle(o, o, r, p)
    p.shader = RadialGradient(o, o, r, intArrayOf(hex(0xC9AE92, 0f), hex(0xC9AE92, 0f), hex(0xFFFFFF, 0.4f), hex(0xB79878, 0.55f)), floatArrayOf(0f, 0.86f, 0.93f, 1f), Shader.TileMode.CLAMP)
    c.drawCircle(o, o, r, p)
    p.shader = null
    val grain = grainTile(96, 0.05f, 12)
    c.drawCircle(o, o, r, grainPaint(grain))
    grain.recycle()
    // Rosettes round the edge, with gold dragées between them.
    val n = 16
    val rr = r * 0.12f
    val ringR = r * 0.84f
    for (k in 0 until n) {
        ensureActive()
        val a = k.toFloat() / n * PI.toFloat() * 2
        rosette(c, o + ringR * cos(a), o + ringR * sin(a), rr, a * 1.7f)
    }
    for (k in 0 until n) {
        val a = (k + 0.5f) / n * PI.toFloat() * 2
        val bx = o + ringR * 1.01f * cos(a)
        val by = o + ringR * 1.01f * sin(a)
        val br = r * 0.022f
        p.shader = RadialGradient(bx - br * 0.4f, by - br * 0.4f, br * 1.4f, intArrayOf(hex(0xFFF6D8), hex(0xD9B25A), hex(0x8A6420)), floatArrayOf(0f, 0.45f, 1f), Shader.TileMode.CLAMP)
        c.drawCircle(bx, by, br, p)
    }
    return bmp
}

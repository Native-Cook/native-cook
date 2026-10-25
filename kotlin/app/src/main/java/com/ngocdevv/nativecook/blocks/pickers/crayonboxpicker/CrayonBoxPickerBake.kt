package com.ngocdevv.nativecook.blocks.pickers.crayonboxpicker

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.os.Build
import com.ngocdevv.nativecook.shared.sigmaToRadius
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ensureActive
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.sin

/* Crayon Box Picker — desk, paper, box and crayons, baked once off the main thread. */

private fun hex(v: Long, a: Float = 1f): Int =
    android.graphics.Color.argb((a * 255).roundToInt(), ((v shr 16) and 255).toInt(), ((v shr 8) and 255).toInt(), (v and 255).toInt())

private fun tone(c: FloatArray, k: Float, a: Float = 1f): Int =
    if (k < 0) android.graphics.Color.argb(a, c[0] * (1 + k), c[1] * (1 + k), c[2] * (1 + k))
    else android.graphics.Color.argb(a, c[0] + (1 - c[0]) * k, c[1] + (1 - c[1]) * k, c[2] + (1 - c[2]) * k)

private fun weight(w: Int): Typeface =
    if (Build.VERSION.SDK_INT >= 28) Typeface.create(Typeface.SANS_SERIF, w, false) else Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)

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

/** A repeating tile of fine grain that darkens like a multiply of noise at [strength]. */
private fun grainTile(size: Int, strength: Float, seed: Int): Bitmap {
    val px = IntArray(size * size)
    for (j in 0 until size) for (i in 0 until size) {
        val v = hash(i, j, seed) * 0.6f + hash(i / 2, j / 2, seed + 3) * 0.4f
        px[j * size + i] = ((strength * (1 - v) * 255).roundToInt().coerceIn(0, 255)) shl 24
    }
    return Bitmap.createBitmap(px, size, size, Bitmap.Config.ARGB_8888)
}

private fun grainPaint(tile: Bitmap) = Paint(Paint.FILTER_BITMAP_FLAG).apply {
    shader = BitmapShader(tile, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT).apply {
        setLocalMatrix(Matrix().apply { setScale(0.5f, 0.5f) })
    }
}

private fun blur(sigma: Float) = BlurMaskFilter(sigmaToRadius(sigma), BlurMaskFilter.Blur.NORMAL)

/** The desk, the sheet of paper, and the open box's back and inside. */
internal fun CoroutineScope.crBakeDesk(g: CrayonGeo, scale: Float): Bitmap {
    val bmp = Bitmap.createBitmap(ceil(g.w * scale).toInt(), ceil(g.h * scale).toInt(), Bitmap.Config.ARGB_8888)
    val c = Canvas(bmp)
    c.scale(scale, scale)
    val p = Paint(Paint.ANTI_ALIAS_FLAG)
    p.shader = LinearGradient(0f, 0f, g.w * 0.3f, g.h, intArrayOf(hex(0xBFD8D2), hex(0xA9C9C2), hex(0x93B5AE)), floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP)
    c.drawRect(0f, 0f, g.w, g.h, p)
    val grain = grainTile(96, 0.06f, 5)
    c.drawRect(0f, 0f, g.w, g.h, grainPaint(grain))
    ensureActive()
    // Paper with two shadows and a slight tooth.
    val px = g.cx - (g.w - 24) / 2
    val pw = g.w - 24
    val paper = RectF(px, g.paperY, px + pw, g.paperY + g.paperH)
    val sh = Paint(Paint.ANTI_ALIAS_FLAG)
    sh.color = hex(0x2F4A44, 0.22f); sh.maskFilter = blur(12f)
    c.save(); c.translate(0f, 8f); c.drawRoundRect(paper, 6f, 6f, sh); c.restore()
    sh.color = hex(0x2F4A44, 0.3f); sh.maskFilter = blur(2f)
    c.save(); c.translate(0f, 1.5f); c.drawRoundRect(paper, 6f, 6f, sh); c.restore()
    p.shader = LinearGradient(0f, g.paperY, 0f, g.paperY + g.paperH, intArrayOf(hex(0xFFFDF8), hex(0xFBF6EC), hex(0xF3ECDF)), floatArrayOf(0f, 0.6f, 1f), Shader.TileMode.CLAMP)
    c.drawRoundRect(paper, 6f, 6f, p)
    val toothTile = grainTile(96, 0.06f, 13)
    c.drawRoundRect(paper, 6f, 6f, grainPaint(toothTile))
    // The box: its back flap, open and leaning away, and the dark inside above the crayons' feet.
    val backTop = g.rimY - g.showBack - 18
    val flap = Path().apply {
        moveTo(g.boxX + 4, g.rimY)
        lineTo(g.boxX + 14, backTop)
        lineTo(g.boxX + g.boxW - 14, backTop)
        lineTo(g.boxX + g.boxW - 4, g.rimY)
        close()
    }
    sh.color = hex(0x2F4A44, 0.25f); sh.maskFilter = blur(10f)
    c.save(); c.translate(0f, 6f); c.drawPath(flap, sh); c.restore()
    p.shader = LinearGradient(0f, backTop, 0f, g.rimY, intArrayOf(hex(0x2F6B52), hex(0x245641), hex(0x173B2C)), floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)
    c.drawPath(flap, p)
    p.shader = null
    p.color = hex(0xF4C64E, 0.22f)
    for (k in 0 until 9) c.drawCircle(g.boxX + 36 + k * ((g.boxW - 72) / 8), backTop + 12, 3f, p)
    p.shader = LinearGradient(0f, g.rimY - 46, 0f, g.rimY + 4, intArrayOf(hex(0x173B2C, 0f), hex(0x0F271D, 0.7f), hex(0x0B1D16, 0.9f)), floatArrayOf(0f, 0.6f, 1f), Shader.TileMode.CLAMP)
    c.drawRect(g.boxX + 6, g.rimY - 46, g.boxX + g.boxW - 6, g.rimY + 4, p)
    grain.recycle(); toothTile.recycle()
    return bmp
}

/** The printed box front: (boxW + 40) × (frontH + 40) pt, drawn at (boxX − 20, rimY − 20). */
internal fun CoroutineScope.crBakeFront(g: CrayonGeo, scale: Float): Bitmap {
    val ww = g.boxW + 40
    val hh = g.frontH + 40
    val bmp = Bitmap.createBitmap(ceil(ww * scale).toInt(), ceil(hh * scale).toInt(), Bitmap.Config.ARGB_8888)
    val c = Canvas(bmp)
    c.scale(bmp.width / ww, bmp.height / hh)
    val r = RectF(20f, 20f, 20 + g.boxW, 20 + g.frontH)
    val sh = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = hex(0x2F4A44, 0.3f); maskFilter = blur(9f) }
    c.save(); c.translate(0f, 8f); c.drawRoundRect(r, 6f, 6f, sh); c.restore()
    val p = Paint(Paint.ANTI_ALIAS_FLAG)
    p.shader = LinearGradient(0f, 20f, 0f, 20 + g.frontH, intArrayOf(hex(0xFAD465), hex(0xF2C04A), hex(0xDFA834)), floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)
    c.drawRoundRect(r, 6f, 6f, p)
    c.save()
    c.clipPath(Path().apply { addRoundRect(r, 6f, 6f, Path.Direction.CW) })
    // A green band, a row of printed colour bars, the brand.
    p.shader = LinearGradient(0f, 20 + g.frontH * 0.42f, 0f, 20 + g.frontH * 0.82f, intArrayOf(hex(0x2F7356), hex(0x24604A), hex(0x1B4E3B)), floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)
    c.drawRect(20f, 20 + g.frontH * 0.42f, 20 + g.boxW, 20 + g.frontH * 0.82f, p)
    p.shader = null
    val barW = (g.boxW - 40) / 12
    for (i in 0 until 12) {
        p.color = hex(Crayons[i].hex)
        val x = 40 + i * barW + 1.5f
        val y = 20 + g.frontH * 0.16f
        c.drawRoundRect(RectF(x, y, x + barW - 3, y + g.frontH * 0.14f), 2f, 2f, p)
    }
    ensureActive()
    val brand = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply { typeface = weight(800); textSize = 24f }
    val title = "DOODLEBUG"
    val tw = brand.measureText(title) + 2.5f * (title.length - 1)
    var x = 20 + (g.boxW - tw) / 2
    val by = 20 + g.frontH * 0.62f + 24 * 0.36f
    for (ch in title) {
        val s = ch.toString()
        brand.color = hex(0x0F2C21, 0.5f)
        c.drawText(s, x + 1, by + 1.5f, brand)
        brand.color = hex(0xFFF4D6)
        c.drawText(s, x, by, brand)
        x += brand.measureText(s) + 2.5f
    }
    val small = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply { typeface = weight(700); textSize = 9f; color = hex(0x6B4A12, 0.85f) }
    val line = "12 WAX CRAYONS · WASHABLE"
    c.drawText(line, 20 + (g.boxW - small.measureText(line)) / 2, 20 + g.frontH * 0.93f, small)
    val tile = grainTile(96, 0.08f, 6)
    c.drawRect(r, grainPaint(tile))
    // The folds into the side panels and the lit top edge.
    p.shader = LinearGradient(
        20f, 0f, 20 + g.boxW, 0f,
        intArrayOf(hex(0x7A5410, 0.28f), hex(0x7A5410, 0f), hex(0x7A5410, 0f), hex(0x7A5410, 0.3f)), floatArrayOf(0f, 0.06f, 0.94f, 1f), Shader.TileMode.CLAMP,
    )
    c.drawRect(r, p)
    p.shader = null
    c.restore()
    p.color = hex(0xFFF5D2, 0.85f)
    c.drawRoundRect(RectF(21f, 20.5f, 19 + g.boxW, 22.5f), 1f, 1f, p)
    tile.recycle()
    return bmp
}

/** Crayon i, standing, tip up: cw × cl pt. */
internal fun crBakeCrayon(g: CrayonGeo, i: Int, scale: Float): Bitmap {
    val w = g.cw
    val h = g.cl
    val bmp = Bitmap.createBitmap(ceil(w * scale).toInt(), ceil(h * scale).toInt(), Bitmap.Config.ARGB_8888)
    val c = Canvas(bmp)
    c.scale(bmp.width / w, bmp.height / h)
    val rgb = Crayons[i].rgb
    // Each tip is worn its own way: height, flat top and lean (the same hash as React Native, in doubles).
    val h1 = ((sin(i * 12.9898) * 43758.5453) % 1).toFloat()
    val h2 = ((sin(i * 78.233) * 43758.5453) % 1).toFloat()
    val tipH = w * (0.95f + 0.35f * abs(h1))
    val flat = w * (0.12f + 0.22f * abs(h2))
    val lean = w * 0.12f * (if (h1 > 0) 1 else -1) * abs(h2)
    val wax = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = tone(rgb, 0f) }
    val tip = Path().apply {
        moveTo(w / 2 - flat / 2 + lean, 1.2f)
        quadTo(w / 2 + lean, 0f, w / 2 + flat / 2 + lean, 1.2f)
        lineTo(w * 0.9f, tipH)
        lineTo(w * 0.1f, tipH)
        close()
    }
    c.drawPath(tip, wax)
    c.drawRect(w * 0.04f, tipH - 0.5f, w * 0.96f, h - 2.5f, wax)
    c.drawRoundRect(RectF(w * 0.04f, h - 8, w * 0.96f, h), 2f, 2f, wax)
    // Wrapper: printed paper in the crayon's colour with two black bands and the name.
    val top = tipH + w * 0.4f
    val bottom = h - w * 0.5f
    val paper = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = tone(rgb, 0.12f) }
    c.drawRect(0f, top, w, bottom, paper)
    val ink = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = hex(0x16120E, 0.85f) }
    for (y in floatArrayOf(top + 4, top + 8.5f, bottom - 10, bottom - 5.5f)) c.drawRect(0f, y, w, y + 1.6f, ink)
    val font = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply { typeface = weight(700); textSize = 8f; color = hex(0x16120E, 0.9f) }
    val name = Crayons[i].name.uppercase()
    val nw = font.measureText(name)
    c.save()
    c.translate(w / 2 + 8 * 0.36f, top + 16 + nw)
    c.rotate(-90f)
    c.drawText(name, 0f, 0f, font)
    c.restore()
    // Round it: dark edges, a soft highlight a third of the way across, a crisp specular line.
    val shade = Paint().apply {
        shader = LinearGradient(
            0f, 0f, w, 0f,
            intArrayOf(hex(0x000000, 0.42f), hex(0x000000, 0f), hex(0xFFFFFF, 0.22f), hex(0x000000, 0.05f), hex(0x000000, 0.5f)),
            floatArrayOf(0f, 0.18f, 0.34f, 0.62f, 1f), Shader.TileMode.CLAMP,
        )
    }
    // Only over the wax and wrapper drawn so far.
    shade.xfermode = android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.SRC_ATOP)
    c.drawRect(0f, tipH * 0.4f, w, h, shade)
    val tipShade = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = LinearGradient(0f, 0f, w, 0f, intArrayOf(hex(0x000000, 0.35f), hex(0xFFFFFF, 0.25f), hex(0x000000, 0.4f)), floatArrayOf(0f, 0.35f, 1f), Shader.TileMode.CLAMP)
    }
    c.drawPath(tip, tipShade)
    val spec = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = hex(0xFFFFFF, 0.4f) }
    c.drawRect(w * 0.3f, tipH + 2, w * 0.36f, h - 4, spec)
    return bmp
}

/**
 * The paper's tooth as a mask over the swatch's area: fractal noise thresholded (alpha = 5v − 3.1),
 * at 2 px per pt; returns the bitmap and its rect in pt.
 */
internal fun CoroutineScope.crBakeTooth(g: CrayonGeo): Pair<Bitmap, RectF> {
    val r = RectF(g.sx - g.rx - 30, g.sy - g.ry - 30, g.sx + g.rx + 30, g.sy + g.ry + 30)
    val px = 2
    val cols = ceil(r.width() * px).toInt()
    val rows = ceil(r.height() * px).toInt()
    val out = IntArray(cols * rows)
    for (j in 0 until rows) {
        ensureActive()
        val y = r.top + j / px.toFloat()
        for (i in 0 until cols) {
            val x = r.left + i / px.toFloat()
            val v = (vnoise(x * 0.55f, y * 0.55f, 9) * 0.667f + vnoise(x * 1.1f, y * 1.1f, 26) * 0.333f)
            val a = (5 * v - 3.1f).coerceIn(0f, 1f)
            out[j * cols + i] = ((a * 255).roundToInt()) shl 24
        }
    }
    return Bitmap.createBitmap(out, cols, rows, Bitmap.Config.ARGB_8888) to r
}

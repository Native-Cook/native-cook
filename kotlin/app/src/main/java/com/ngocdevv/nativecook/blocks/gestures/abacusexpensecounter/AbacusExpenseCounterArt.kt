package com.ngocdevv.nativecook.blocks.gestures.abacusexpensecounter

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
import android.os.Build
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/*
 The abacus, drawn on one canvas (same art as abacusArt.ts). Rendered once into bitmaps: the
 screen's backdrop, the walnut frame with its grain and bevels, the lacquered back panel, the
 brass rods with their rivets; one bead per category colour (a barrel-shaped lacquer cylinder:
 dark edges, a highlight band, a glint, darker rounded ends, contact shadows on the rod either
 side) and one soft drop shadow. Per frame: 50 shadow bitmaps, 50 bead bitmaps and the scripted
 fingertip.
 */

/** Lacquer colours: vermilion, teal, ochre, pine, plum. */
internal val AbacusBeadColors = intArrayOf(0xb8432e, 0x2e6f73, 0xc98f2a, 0x4d6b3f, 0x6a3b55)

private fun argb(hex: Int, a: Float = 1f): Int = ((a.coerceIn(0f, 1f) * 255f + 0.5f).toInt() shl 24) or (hex and 0xFFFFFF)

private fun shade(hex: Int, k: Float, a: Float = 1f): Int {
    fun f(v: Int): Int {
        val c = v / 255f
        val o = if (k >= 0) c + (1 - c) * k else c * (1 + k)
        return (o * 255f + 0.5f).toInt().coerceIn(0, 255)
    }
    return ((a * 255f + 0.5f).toInt() shl 24) or (f((hex shr 16) and 0xff) shl 16) or (f((hex shr 8) and 0xff) shl 8) or f(hex and 0xff)
}

private fun sigmaRadius(sigma: Float) = ((sigma - 0.5f) / 0.57735f).coerceAtLeast(0.01f)

private fun fill(color: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }

private fun stroke(color: Int, width: Float) = fill(color).apply { style = Paint.Style.STROKE; strokeWidth = width }

private fun blurred(color: Int, sigma: Float) = fill(color).apply { maskFilter = BlurMaskFilter(sigmaRadius(sigma), BlurMaskFilter.Blur.NORMAL) }

private fun linear(x0: Float, y0: Float, x1: Float, y1: Float, colors: IntArray, pos: FloatArray) =
    Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = LinearGradient(x0, y0, x1, y1, colors, pos, Shader.TileMode.CLAMP) }

private fun rr(x: Float, y: Float, w: Float, h: Float) = RectF(x, y, x + w, y + h)

private fun rrPath(x: Float, y: Float, w: Float, h: Float, r: Float) = Path().apply { addRoundRect(rr(x, y, w, h), r, r, Path.Direction.CW) }

private fun hash(i: Int): Float {
    val s = sin(i * 12.9898) * 43758.5453
    return (s - floor(s)).toFloat()
}

/** Backdrop, frame, back panel, rods: everything that never moves. */
internal fun abacusRenderBoard(g: AbacusGeo, density: Float): Bitmap {
    val bmp = Bitmap.createBitmap((g.w * density).roundToInt().coerceAtLeast(1), (g.h * density).roundToInt().coerceAtLeast(1), Bitmap.Config.ARGB_8888)
    val c = Canvas(bmp)
    c.scale(density, density)
    c.drawRect(0f, 0f, g.w, g.h, linear(0f, 0f, 0f, g.h, intArrayOf(argb(0x1d1712), argb(0x16110d), argb(0x100c09)), floatArrayOf(0f, 0.5f, 1f)))
    c.drawRect(0f, 0f, g.w, g.h, Paint().apply { shader = RadialGradient(g.w * 0.5f, g.boardY + g.boardH * 0.4f, g.w * 0.95f, argb(0xd9a56a, 0.12f), argb(0xd9a56a, 0f), Shader.TileMode.CLAMP) })

    val x = g.boardX
    val y = g.boardY
    val w = g.boardW
    val h = g.boardH
    val f = g.frame
    // Two shadows under the abacus.
    c.drawRoundRect(rr(x + 8, y + 20, w - 16, h - 6), 20f, 20f, blurred(argb(0, 0.55f), 18f))
    c.drawRoundRect(rr(x + 1, y + 3, w - 2, h), 18f, 18f, blurred(argb(0, 0.5f), 3f))
    // Walnut frame with grain running along it.
    c.drawRoundRect(rr(x, y, w, h), 18f, 18f, linear(0f, y, 0f, y + h, intArrayOf(argb(0x7a4b2a), argb(0x6a3f22), argb(0x5a341c), argb(0x4a2a16)), floatArrayOf(0f, 0.3f, 0.7f, 1f)))
    c.save()
    c.clipPath(rrPath(x, y, w, h, 18f))
    val grain = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    for (k in 0 until 46) {
        val yy = y + k / 46f * h + hash(k) * 4
        val path = Path()
        var xx = x - 4
        while (xx <= x + w + 4) {
            val py = yy + sin(xx * 0.02f + hash(k + 9) * 6) * 2.2f + sin(xx * 0.07f + k) * 0.6f
            if (xx == x - 4) path.moveTo(xx, py) else path.lineTo(xx, py)
            xx += 10f
        }
        grain.strokeWidth = 0.5f + hash(k + 3) * 1.1f
        grain.color = if (k % 4 == 0) argb(0xc28a5a, 0.16f) else argb(0x2a160a, 0.28f)
        c.drawPath(path, grain)
    }
    // Wood figure: grey noise stretched along the grain, overlaid at 8 % (Skia's fractal noise, approximated).
    val size = 96
    val px = IntArray(size * size)
    var s = 4
    for (i in px.indices) {
        s = s * 1103515245 + 12345
        val v = (s ushr 16) and 0xff
        px[i] = (0xFF shl 24) or (v shl 16) or (v shl 8) or v
    }
    c.drawRect(x, y, x + w, y + h, Paint().apply {
        shader = BitmapShader(Bitmap.createBitmap(px, size, size, Bitmap.Config.ARGB_8888), Shader.TileMode.REPEAT, Shader.TileMode.REPEAT).apply { setLocalMatrix(Matrix().apply { setScale(4.5f, 1.1f) }) }
        alpha = (0.08f * 255).roundToInt()
        xfermode = PorterDuffXfermode(PorterDuff.Mode.OVERLAY)
    })
    c.restore()
    c.drawRoundRect(rr(x + 0.6f, y + 0.6f, w - 1.2f, h - 1.2f), 17.4f, 17.4f, stroke(argb(0xf0c18e, 0.28f), 1.2f))

    // The back panel: dark lacquer, sunk into the frame.
    val ix = x + f
    val iy = y + f
    val iw = w - 2 * f
    val ih = h - 2 * f
    c.drawRoundRect(rr(ix, iy, iw, ih), 8f, 8f, linear(0f, iy, 0f, iy + ih, intArrayOf(argb(0x120c09), argb(0x1d1410), argb(0x221812)), floatArrayOf(0f, 0.3f, 1f)))
    c.save()
    c.clipPath(rrPath(ix, iy, iw, ih, 8f))
    c.drawRect(ix, iy - 6, ix + iw, iy + 4, blurred(argb(0, 0.7f), 5f))
    c.drawRect(ix - 6, iy, ix + 2, iy + ih, blurred(argb(0, 0.4f), 4f))
    // Dividers between the rods' bands.
    for (r in 1 until ABACUS_RODS) c.drawLine(ix + 10, iy + r * g.band, ix + iw - 10, iy + r * g.band, stroke(argb(0xf0c18e, 0.05f), 1f))
    c.restore()
    c.drawLine(ix + 8, iy + ih + 0.5f, ix + iw - 8, iy + ih + 0.5f, stroke(argb(0xf0c18e, 0.22f), 1f))

    // Brass rods with a rivet at each end.
    for (r in 0 until ABACUS_RODS) {
        val ry = g.rodY[r]
        c.drawRoundRect(rr(ix - 4, ry - 1.6f + 3, iw + 8, 3.2f), 1.6f, 1.6f, blurred(argb(0, 0.5f), 2f))
        c.drawRoundRect(rr(ix - 4, ry - 1.7f, iw + 8, 3.4f), 1.7f, 1.7f, linear(0f, ry - 1.7f, 0f, ry + 1.7f, intArrayOf(argb(0xfbe3a8), argb(0xc79a4c), argb(0x7a5a24)), floatArrayOf(0f, 0.45f, 1f)))
        for (rx in floatArrayOf(x + f / 2, x + w - f / 2)) {
            c.drawCircle(rx, ry + 0.6f, 3.6f, blurred(argb(0, 0.5f), 1f))
            c.drawCircle(rx, ry, 3.4f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = RadialGradient(rx - 1.2f, ry - 1.4f, 5f, intArrayOf(argb(0xfff1c8), argb(0xc79a4c), argb(0x5e4219)), floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)
            })
        }
    }
    return bmp
}

/** One lacquered bead, a bitmap 2·bw × 2·bh dp centred on the bead. */
internal fun abacusRenderBead(g: AbacusGeo, color: Int, density: Float): Bitmap {
    val bw = g.bw
    val bh = g.bh
    val bmp = Bitmap.createBitmap((bw * 2 * density).roundToInt(), (bh * 2 * density).roundToInt(), Bitmap.Config.ARGB_8888)
    val c = Canvas(bmp)
    c.scale(density, density)
    c.translate(bw, bh)
    val x = -bw / 2 + 0.4f
    val w = bw - 0.8f
    val y = -bh / 2
    val r = min(w, bh) * 0.42f
    // Contact shadows on the rod, either side.
    c.drawOval(rr(-bw / 2 - 2.5f, -2.5f, 5f, 6f), blurred(argb(0, 0.45f), 1.4f))
    c.drawOval(rr(bw / 2 - 2.5f, -2.5f, 5f, 6f), blurred(argb(0, 0.45f), 1.4f))
    c.drawRoundRect(rr(x, y, w, bh), r, r, linear(0f, y, 0f, y + bh, intArrayOf(shade(color, -0.55f), shade(color, -0.05f), shade(color, 0.32f), shade(color, 0.05f), shade(color, -0.25f), shade(color, -0.62f)), floatArrayOf(0f, 0.14f, 0.3f, 0.5f, 0.8f, 1f)))
    c.save()
    c.clipPath(rrPath(x, y, w, bh, r))
    // Rounded ends: the barrel turns away from the light at both sides.
    c.drawRect(x, y, x + w, y + bh, linear(x, 0f, x + w, 0f, intArrayOf(argb(0, 0.4f), argb(0, 0f), argb(0, 0f), argb(0, 0.45f)), floatArrayOf(0f, 0.28f, 0.7f, 1f)))
    // The highlight band and a glint, toward the light at the upper left.
    c.drawRect(x, y + bh * 0.2f, x + w, y + bh * 0.32f, blurred(argb(0xffffff, 0.22f), 1.5f))
    c.drawOval(rr(x + w * 0.2f, y + bh * 0.17f, w * 0.28f, bh * 0.12f), blurred(argb(0xffffff, 0.75f), 0.8f))
    // Bounce light from the panel along the bottom.
    c.drawRect(x, y + bh * 0.84f, x + w, y + bh * 0.92f, blurred(shade(color, 0.2f, 0.35f), 1.2f))
    c.restore()
    c.drawRoundRect(rr(x + 0.4f, y + 0.4f, w - 0.8f, bh - 0.8f), r - 0.4f, r - 0.4f, stroke(argb(0, 0.35f), 0.8f))
    return bmp
}

/** The soft shadow a bead casts on the back panel, a bitmap 3·bw × 3·bh dp centred on the bead. */
internal fun abacusRenderBeadShadow(g: AbacusGeo, density: Float): Bitmap {
    val bmp = Bitmap.createBitmap((g.bw * 3 * density).roundToInt(), (g.bh * 3 * density).roundToInt(), Bitmap.Config.ARGB_8888)
    val c = Canvas(bmp)
    c.scale(density, density)
    c.translate(g.bw * 1.5f, g.bh * 1.5f)
    c.drawRoundRect(rr(-g.bw / 2 + 1, -g.bh / 2 + 6, g.bw - 2, g.bh), 9f, 9f, blurred(argb(0, 0.5f), 4f))
    return bmp
}

/** Draws every bead's shadow, every bead, then the fingertip. Paints are made once. */
internal class AbacusPainter(private val g: AbacusGeo) {
    private val bitmapPaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val halo = fill(0).apply { if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) maskFilter = BlurMaskFilter(sigmaRadius(8f), BlurMaskFilter.Blur.NORMAL) }
    private val disc = fill(0)
    private val ring = stroke(0, 1.4f)

    fun draw(c: Canvas, beads: List<Bitmap>, shadow: Bitmap, x: DoubleArray, fx: Float, fy: Float, fa: Float, density: Float) {
        val inv = 1f / density
        for (r in 0 until ABACUS_RODS) for (j in 0 until ABACUS_BEADS) {
            c.save()
            c.translate(x[r * ABACUS_BEADS + j].toFloat() - g.bw * 1.5f, g.rodY[r] - g.bh * 1.5f)
            c.scale(inv, inv)
            c.drawBitmap(shadow, 0f, 0f, bitmapPaint)
            c.restore()
        }
        for (r in 0 until ABACUS_RODS) for (j in 0 until ABACUS_BEADS) {
            c.save()
            c.translate(x[r * ABACUS_BEADS + j].toFloat() - g.bw, g.rodY[r] - g.bh)
            c.scale(inv, inv)
            c.drawBitmap(beads[r], 0f, 0f, bitmapPaint)
            c.restore()
        }
        if (fa > 0.01f) {
            val rad = 20f * (1.1f - 0.1f * fa)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                halo.color = argb(0xffe2bf, 0.1f * fa)
                c.drawCircle(fx, fy, rad + 6, halo)
            }
            disc.color = argb(0xfff1e0, 0.16f * fa)
            c.drawCircle(fx, fy, rad, disc)
            ring.color = argb(0xfff1e0, 0.55f * fa)
            c.drawCircle(fx, fy, rad, ring)
        }
    }
}

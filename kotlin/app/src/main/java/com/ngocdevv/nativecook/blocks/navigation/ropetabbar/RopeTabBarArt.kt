package com.ngocdevv.nativecook.blocks.navigation.ropetabbar

import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.os.Build
import kotlin.math.PI
import kotlin.math.atan
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

/*
 The tab bar's canvas: a ripstop-nylon bar (rendered once into a bitmap), the climbing rope pinned
 to two steel eyelets at the screen edges, the knot that marks the active tab, and tap ripples.
 Canvas-local coordinates in dp: the canvas starts ROPE_TOP_PAD above the bar.
 */

internal const val ROPE_TOP_PAD = 30f
/** The rope's pinned line, below the bar's top edge. */
internal const val ROPE_LINE = 5f
private const val ROPE_W = 5.2f
internal const val ROPE_SAMPLES = 48

private fun argb(hex: Int, a: Float = 1f): Int =
    ((a.coerceIn(0f, 1f) * 255f + 0.5f).toInt() shl 24) or (hex and 0xFFFFFF)

private fun blurFilter(sigma: Float): BlurMaskFilter? =
    // Hardware canvases honour mask filters from API 28; below that the shadow stays sharp.
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) BlurMaskFilter(((sigma - 0.5f) / 0.57735f).coerceAtLeast(0.01f), BlurMaskFilter.Blur.NORMAL) else null

private fun strokePaint(color: Int, width: Float) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    style = Paint.Style.STROKE
    strokeWidth = width
    strokeCap = Paint.Cap.ROUND
    strokeJoin = Paint.Join.ROUND
    this.color = color
}

/** The bar: dark forest ripstop with a lit top edge, and the two eyelets the rope is tied to. Drawn once. */
internal fun ropeRenderBar(w: Float, barH: Float, density: Float): Bitmap {
    val h = barH + ROPE_TOP_PAD
    val bmp = Bitmap.createBitmap((w * density).toInt().coerceAtLeast(1), (h * density).toInt().coerceAtLeast(1), Bitmap.Config.ARGB_8888)
    val c = Canvas(bmp)
    c.scale(density, density)
    val y0 = ROPE_TOP_PAD
    // Soft shadow the bar casts upward onto the content.
    val lift = Paint().apply { shader = LinearGradient(0f, 0f, 0f, y0, argb(0x0b120e, 0f), argb(0x0b120e, 0.35f), Shader.TileMode.CLAMP) }
    c.drawRect(0f, 0f, w, y0, lift)
    val body = Paint().apply {
        shader = LinearGradient(
            0f, y0, 0f, h,
            intArrayOf(argb(0x24372d), argb(0x1b2b23), argb(0x14211b), argb(0x101a15)),
            floatArrayOf(0f, 0.25f, 0.7f, 1f), Shader.TileMode.CLAMP,
        )
    }
    c.drawRect(0f, y0, w, h, body)
    // Ripstop: a fine grid, with a heavier thread every fourth line.
    val fine = strokePaint(argb(0xd8e6dc, 0.035f), 0.5f).apply { strokeCap = Paint.Cap.BUTT }
    val heavy = strokePaint(argb(0xd8e6dc, 0.06f), 0.9f).apply { strokeCap = Paint.Cap.BUTT }
    var x = 3f
    var k = 0
    while (x < w) { c.drawLine(x, y0, x, h, if (k % 4 == 0) heavy else fine); x += 6f; k++ }
    var y = y0 + 3f
    k = 0
    while (y < h) { c.drawLine(0f, y, w, y, if (k % 4 == 0) heavy else fine); y += 6f; k++ }
    // Grain: Skia's fractal noise in an overlay at 5 %, approximated by seeded light and dark specks.
    val speck = Paint()
    var seed = 11
    var gx = 0f
    while (gx < w) {
        var gy = y0
        while (gy < h) {
            seed = seed * 1103515245 + 12345
            val r = ((seed ushr 8) and 0xFFFF) / 65535f
            speck.color = if (r > 0.5f) argb(0xffffff, (r - 0.5f) * 0.09f) else argb(0x000000, (0.5f - r) * 0.12f)
            c.drawRect(gx, gy, gx + 1.2f, gy + 1.2f, speck)
            gy += 1.2f
        }
        gx += 1.2f
    }
    c.drawLine(0f, y0 + 0.5f, w, y0 + 0.5f, strokePaint(argb(0xe9f2ea, 0.16f), 1f).apply { strokeCap = Paint.Cap.BUTT })
    // Eyelets at both edges.
    for (ex in floatArrayOf(7f, w - 7f)) {
        val cy = y0 + ROPE_LINE
        val shadow = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = argb(0x000000, 0.5f); maskFilter = BlurMaskFilter(((2f - 0.5f) / 0.57735f), BlurMaskFilter.Blur.NORMAL) }
        c.drawCircle(ex, cy + 1.5f, 6.5f, shadow)
        val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 2.6f
            shader = LinearGradient(ex, cy - 6f, ex, cy + 6f, intArrayOf(argb(0xf2f4f6), argb(0x9aa1a9), argb(0x4b5158)), floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)
        }
        c.drawCircle(ex, cy, 5f, ring)
    }
    return bmp
}

/** Paints and paths for the rope, made once and reused every frame. */
internal class RopePainter {
    private val path = Path()
    private val braid = Path()
    private val navy = Path()
    private val shadow = strokePaint(argb(0x050a07, 0.55f), ROPE_W + 1.5f).apply { maskFilter = blurFilter(2.4f) }
    private val core = strokePaint(argb(0x6e2810), ROPE_W + 1.2f)
    private val sheath = strokePaint(argb(0xdc6430), ROPE_W)
    private val ridge = strokePaint(argb(0xffb37e, 0.55f), 1.2f)
    private val braidPaint = strokePaint(argb(0x8f3414, 0.6f), 1.1f)
    private val navyPaint = strokePaint(argb(0x22324f, 0.7f), 1.2f)
    private val knotShadow = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = argb(0x020503, 0.55f); maskFilter = blurFilter(3.5f) }
    private val wrapFill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = LinearGradient(
            -4f, -9f, 4f, 9f,
            intArrayOf(argb(0xffa16a), argb(0xe2672f), argb(0xb3461c), argb(0x6a240c)),
            floatArrayOf(0f, 0.35f, 0.7f, 1f), Shader.TileMode.CLAMP,
        )
    }
    private val wrapEdge = strokePaint(argb(0x4f1a08, 0.7f), 0.8f)
    private val wrapShine = strokePaint(argb(0xffd1ad, 0.5f), 1f)
    private val rippleStroke = strokePaint(argb(0xf4ead8, 0.45f), 1.5f)
    private val rippleFill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val oval = RectF(-15f, -4f, 15f, 12f)
    private val wrap = RectF(-4f, -9f, 4f, 9f)

    /**
     * Draws the rope from its heights [ys] (canvas y at ROPE_SAMPLES + 1 evenly spaced x across the
     * width), the knot at (kx, ky) leaning with the rope's slope, and a ripple (0–1, or −1) at (rx, ry).
     */
    fun draw(c: Canvas, w: Float, ys: FloatArray, kx: Float, ky: Float, slope: Float, ripple: Float, rx: Float, ry: Float) {
        val step = w / ROPE_SAMPLES
        path.rewind()
        path.moveTo(0f, ys[0])
        for (i in 1 until ROPE_SAMPLES) {
            val mx = (i + 0.5f) * step
            val my = (ys[i] + ys[i + 1]) / 2f
            path.quadTo(i * step, ys[i], mx, my)
        }
        path.lineTo(w, ys[ROPE_SAMPLES])

        // Shadow on the bar, then the rope: dark core, dyed sheath, a lit ridge.
        c.save(); c.translate(0f, 3.5f); c.drawPath(path, shadow); c.restore()
        c.drawPath(path, core)
        c.drawPath(path, sheath)
        c.save(); c.translate(0f, -1.3f); c.drawPath(path, ridge); c.restore()

        // The braid: short diagonal strands every 4.5 dp, turned with the rope.
        braid.rewind()
        navy.rewind()
        var x = 4f
        var k = 0
        while (x < w - 2f) {
            val i = min(ROPE_SAMPLES - 1, floor(x / step).toInt())
            val f = x / step - i
            val y = ys[i] + (ys[i + 1] - ys[i]) * f
            val a = atan2(ys[i + 1] - ys[i], step)
            val ca = cos(a + 1.05f)
            val sa = sin(a + 1.05f)
            val target = if (k % 5 == 2) navy else braid
            target.moveTo(x - ca * 2.4f, y - sa * 2.4f)
            target.lineTo(x + ca * 2.4f, y + sa * 2.4f)
            x += 4.5f
            k++
        }
        c.drawPath(braid, braidPaint)
        c.drawPath(navy, navyPaint)

        // The knot: three wraps of a barrel knot around the rope, lit from above.
        c.save()
        c.translate(kx, ky)
        c.rotate((atan(slope) * 180f / PI.toFloat()))
        c.drawOval(oval, knotShadow)
        for (j in -1..1) {
            c.save()
            c.translate(j * 6.2f, 0f)
            c.rotate(-24f)
            c.drawRoundRect(wrap, 4f, 4f, wrapFill)
            c.drawRoundRect(wrap, 4f, 4f, wrapEdge)
            c.drawLine(-1.6f, -6.5f, -1.6f, 4f, wrapShine)
            c.restore()
        }
        c.restore()

        if (ripple >= 0f) {
            val r = 12f + 22f * (1f - (1f - ripple).pow(3))
            val a = if (ripple < 0.15f) ripple / 0.15f else 1f - (ripple - 0.15f) / 0.85f
            rippleStroke.color = argb(0xf4ead8, 0.45f * a)
            c.drawCircle(rx, ry, r, rippleStroke)
            rippleFill.color = argb(0xf4ead8, 0.1f * a)
            c.drawCircle(rx, ry, r, rippleFill)
        }
    }
}

package com.ngocdevv.nativecook.blocks.navigation.magnetsnapdock

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.DashPathEffect
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
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/*
 Everything drawn on the dock's two canvases, in dp.
 Static (rendered once into bitmaps): the graphite backdrop and the artboard with a harbour sketch
 in progress, the dock bar, and the six enamel tool faces (rendered at 2× their size so a 1.35×
 grow stays sharp). Live (per frame): the plate under the active tool, the tiles' shadows, the
 field lines, the tiles and the fingertip.
 */

internal val MagnetToolColors = intArrayOf(0x5b606b, 0x2f7d76, 0xc4532f, 0xcf9a32, 0x57683c, 0x3f5f8a)
private const val GLYPH = 0xf6f1e8
private const val R = MAGNET_ICON * 0.27f
/** Faces are rendered at this many pixels per dp times the screen density. */
internal const val MAGNET_FACE_OVERSAMPLE = 1.5f

private fun argb(hex: Int, a: Float = 1f): Int = ((a.coerceIn(0f, 1f) * 255f + 0.5f).toInt() shl 24) or (hex and 0xFFFFFF)

private fun shade(hex: Int, k: Float): Int {
    fun f(v: Int): Int {
        val c = v / 255f
        val o = if (k >= 0) c + (1 - c) * k else c * (1 + k)
        return (o * 255f + 0.5f).toInt().coerceIn(0, 255)
    }
    return (0xFF shl 24) or (f((hex shr 16) and 0xff) shl 16) or (f((hex shr 8) and 0xff) shl 8) or f(hex and 0xff)
}

private fun sigmaRadius(sigma: Float) = ((sigma - 0.5f) / 0.57735f).coerceAtLeast(0.01f)

/** Hardware canvases honour mask filters from API 28; below that live blurs are left out. */
private val LiveBlur = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P

private fun fill(color: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }

private fun stroke(color: Int, width: Float) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    this.color = color
    style = Paint.Style.STROKE
    strokeWidth = width
    strokeCap = Paint.Cap.ROUND
    strokeJoin = Paint.Join.ROUND
}

private fun blurred(color: Int, sigma: Float) = fill(color).apply { maskFilter = BlurMaskFilter(sigmaRadius(sigma), BlurMaskFilter.Blur.NORMAL) }

private fun linear(x0: Float, y0: Float, x1: Float, y1: Float, colors: IntArray, pos: FloatArray) =
    Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = LinearGradient(x0, y0, x1, y1, colors, pos, Shader.TileMode.CLAMP) }

private fun rr(x: Float, y: Float, w: Float, h: Float) = RectF(x, y, x + w, y + h)

private fun poly(vararg p: Float): Path = Path().apply {
    moveTo(p[0], p[1])
    var i = 2
    while (i < p.size) { lineTo(p[i], p[i + 1]); i += 2 }
    close()
}

/** A tile of grey noise: Skia's fractal noise is approximated by seeded per-pixel values, smoothed once. */
private fun noiseShader(size: Int, seed: Int, stretchY: Float, shade: Boolean = false): BitmapShader {
    val px = IntArray(size * size)
    var s = seed
    for (i in px.indices) {
        s = s * 1103515245 + 12345
        val v = (s ushr 16) and 0xff
        // `shade`: black whose alpha is 1 − v, the source-over form of multiplying by grey v.
        px[i] = if (shade) (255 - v) shl 24 else (0xFF shl 24) or (v shl 16) or (v shl 8) or v
    }
    val bmp = Bitmap.createBitmap(px, size, size, Bitmap.Config.ARGB_8888)
    return BitmapShader(bmp, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT).apply {
        setLocalMatrix(Matrix().apply { setScale(1.2f, 1.2f * stretchY) })
    }
}

/** One glyph, centred on the origin, about 28 dp across. */
private fun glyph(c: Canvas, tool: Int) {
    val ink = fill(argb(GLYPH))
    val line = stroke(argb(GLYPH), 2f)
    when (tool) {
        0 -> c.drawPath(poly(-6f, -12f, 9f, 3f, 2.5f, 4f, 6.5f, 11.5f, 3.2f, 13f, -0.6f, 5.6f, -6f, 10.5f), ink)
        1 -> {
            c.drawPath(poly(0f, -12f, 8.5f, 1f, 3.4f, 11f, -3.4f, 11f, -8.5f, 1f), ink)
            c.drawLine(0f, -11f, 0f, 2f, stroke(argb(MagnetToolColors[1]), 1.4f))
            c.drawCircle(0f, 2.5f, 2.2f, fill(argb(MagnetToolColors[1])))
            c.drawRoundRect(rr(-4.5f, 12.5f, 9f, 2.6f), 1.3f, 1.3f, ink)
        }
        2 -> {
            c.save()
            c.rotate(-38f)
            c.drawRoundRect(rr(-2.4f, -15f, 4.8f, 15f), 2.4f, 2.4f, ink)
            c.drawRoundRect(rr(-3.6f, -0.5f, 7.2f, 5f), 1.2f, 1.2f, linear(-3.6f, 0f, 3.6f, 0f, intArrayOf(argb(0xb9bec6), argb(0xffffff), argb(0x8d939b)), floatArrayOf(0f, 0.45f, 1f)))
            val tip = Path().apply {
                moveTo(-3.6f, 4.5f)
                cubicTo(-4.2f, 10f, -1.5f, 13.5f, 0.6f, 16f)
                cubicTo(2.4f, 12.5f, 4.4f, 9.5f, 3.6f, 4.5f)
                close()
            }
            c.drawPath(tip, ink)
            c.restore()
        }
        3 -> {
            c.drawRoundRect(rr(-1f, -1f, 12f, 12f), 2.5f, 2.5f, fill(argb(GLYPH, 0.62f)))
            c.drawCircle(-3.5f, -3.5f, 7.5f, line)
        }
        4 -> {
            // Helvetica Neue Bold 19 in RN; the default sans, bold, here.
            ink.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            ink.textSize = 19f
            val wsum = ink.measureText("Aa")
            c.drawText("Aa", -wsum / 2f, 7f, ink)
        }
        else -> for (k in 2 downTo 0) {
            val y = (k - 1) * 6f
            val p = poly(0f, y - 6f, 11f, y, 0f, y + 6f, -11f, y)
            if (k == 0) {
                c.drawPath(p, ink)
            } else {
                c.drawPath(p, fill(argb(MagnetToolColors[5])))
                c.drawPath(p, stroke(argb(GLYPH, 0.75f - k * 0.15f), 1.6f))
            }
        }
    }
}

/** The six tool faces, each a bitmap 2 × ICON dp square centred on the tile. */
internal fun magnetRenderFaces(density: Float): List<Bitmap> {
    val scale = density * MAGNET_FACE_OVERSAMPLE
    val side = (MAGNET_ICON * 2 * scale).roundToInt()
    return List(MAGNET_TOOLS) { i ->
        val bmp = Bitmap.createBitmap(side, side, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.scale(scale, scale)
        c.translate(MAGNET_ICON, MAGNET_ICON)
        val h = MAGNET_ICON / 2
        val base = MagnetToolColors[i]
        c.drawRoundRect(rr(-h, -h, MAGNET_ICON, MAGNET_ICON), R, R, linear(-h, -h, h * 0.4f, h, intArrayOf(shade(base, 0.28f), shade(base, 0.05f), shade(base, -0.22f)), floatArrayOf(0f, 0.5f, 1f)))
        // Enamel gloss: a soft sheen across the top half.
        c.drawRoundRect(rr(-h + 3, -h + 2, MAGNET_ICON - 6, MAGNET_ICON * 0.46f), R - 3, R - 3, linear(0f, -h, 0f, 0f, intArrayOf(argb(0xffffff, 0.22f), argb(0xffffff, 0.02f)), floatArrayOf(0f, 1f)))
        c.drawRoundRect(rr(-h + 0.6f, -h + 0.6f, MAGNET_ICON - 1.2f, MAGNET_ICON - 1.2f), R - 0.6f, R - 0.6f, stroke(argb(0xffffff, 0.26f), 1f))
        c.drawRoundRect(rr(-h + 1.2f, -h + 2.4f, MAGNET_ICON - 2.4f, MAGNET_ICON - 3.2f), R - 1, R - 1, stroke(argb(0x000000, 0.14f), 1f))
        // Glyph shadow (the glyph's coverage, blurred 1.2, black at 35 %, 1.2 dp down), then the glyph.
        val glyphBmp = Bitmap.createBitmap(side, side, Bitmap.Config.ARGB_8888)
        val gc = Canvas(glyphBmp)
        gc.scale(scale, scale)
        gc.translate(MAGNET_ICON, MAGNET_ICON)
        glyph(gc, i)
        val offset = IntArray(2)
        val alpha = glyphBmp.extractAlpha(Paint().apply { maskFilter = BlurMaskFilter(sigmaRadius(1.2f) * scale, BlurMaskFilter.Blur.NORMAL) }, offset)
        c.save()
        c.translate(-MAGNET_ICON, -MAGNET_ICON + 1.2f)
        c.scale(1f / scale, 1f / scale)
        c.drawBitmap(alpha, offset[0].toFloat(), offset[1].toFloat(), Paint(Paint.FILTER_BITMAP_FLAG).apply { color = argb(0x000000, 0.35f) })
        c.restore()
        glyph(c, i)
        alpha.recycle()
        glyphBmp.recycle()
        bmp
    }
}

/** Graphite backdrop and the artboard: a harbour study in progress. Rendered once. */
internal fun magnetRenderBackdrop(g: MagnetGeo, density: Float): Bitmap {
    val bmp = Bitmap.createBitmap((g.w * density).roundToInt().coerceAtLeast(1), (g.h * density).roundToInt().coerceAtLeast(1), Bitmap.Config.ARGB_8888)
    val c = Canvas(bmp)
    c.scale(density, density)
    c.drawRect(0f, 0f, g.w, g.h, linear(0f, 0f, 0f, g.h, intArrayOf(argb(0x1d1f24), argb(0x16181c), argb(0x111215)), floatArrayOf(0f, 0.5f, 1f)))
    c.drawRect(0f, 0f, g.w, g.h, Paint().apply { shader = RadialGradient(g.w / 2, g.h * 0.38f, g.h * 0.75f, intArrayOf(argb(0, 0f), argb(0, 0f), argb(0, 0.45f)), floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP) })

    val bx = 20f
    val by = g.boardTop
    val bw = g.w - 40f
    val bh = max(120f, g.panelTop - 16f - by)
    c.drawRoundRect(rr(bx, by + 14, bw, bh), 18f, 18f, blurred(argb(0, 0.4f), 16f))
    c.drawRoundRect(rr(bx, by + 2, bw, bh), 18f, 18f, blurred(argb(0, 0.4f), 2f))
    c.drawRoundRect(rr(bx, by, bw, bh), 18f, 18f, linear(0f, by, 0f, by + bh, intArrayOf(argb(0xf5efe4), argb(0xefe7d9), argb(0xe7ddcc)), floatArrayOf(0f, 0.6f, 1f)))
    c.save()
    c.clipPath(Path().apply { addRoundRect(rr(bx, by, bw, bh), 18f, 18f, Path.Direction.CW) })
    // Paper tooth: noise multiplied in at 9 %. PorterDuff.Mode.MULTIPLY is Skia's modulate
    // ([Sa·Da, Sc·Dc]), which turned the whole paper near-black and almost transparent. A separable
    // multiply of grey v at alpha a is D·(1 − a·(1 − v)), which is exactly black at alpha (1 − v)
    // drawn source-over at a: no blend mode needed, and the same on every API level.
    c.drawRect(bx, by, bx + bw, by + bh, Paint().apply {
        shader = noiseShader(96, 5, 1f, shade = true)
        alpha = (0.09f * 255).roundToInt()
    })
    // Sky wash, the gouache sun, the horizon.
    val horizon = by + bh * 0.56f
    c.drawRect(bx, by, bx + bw, horizon, linear(0f, by, 0f, horizon, intArrayOf(argb(0xe9cfae, 0f), argb(0xeab894, 0.55f), argb(0xe7a27e, 0.6f)), floatArrayOf(0f, 0.7f, 1f)))
    val sunR = min(30f, bh * 0.13f)
    c.drawCircle(bx + bw * 0.66f, horizon - bh * 0.17f, sunR, fill(argb(0xd4572f, 0.88f)))
    c.drawCircle(bx + bw * 0.66f - 6, horizon - bh * 0.17f - 6, sunR * 0.5f, blurred(argb(0xf08a5c, 0.6f), 6f))
    // Ink water: wavy dry-brush strokes.
    for (k in 0 until 9) {
        val y = horizon + 8 + k * (bh * 0.42f) / 9
        val len = bw * (0.3f + 0.07f * k)
        val x0 = bx + 14 + ((k * 37) % 60)
        val p = Path()
        p.moveTo(x0, y)
        var x = 0f
        while (x <= len) { p.lineTo(x0 + x, y + sin(x * 0.06f + k) * 1.8f); x += 12f }
        c.drawPath(p, stroke(argb(0x2a2f3a, 0.55f + 0.04f * k), 1.6f + k * 0.35f))
    }
    c.drawLine(bx + 10, horizon, bx + bw - 10, horizon, stroke(argb(0x2a2f3a, 0.8f), 1.4f))
    // A small sailing boat.
    val boatX = bx + bw * 0.3f
    c.drawPath(poly(boatX - 30, horizon + 4, boatX + 30, horizon + 4, boatX + 20, horizon + 14, boatX - 22, horizon + 14), fill(argb(0x2a2f3a)))
    c.drawLine(boatX, horizon + 4, boatX, horizon - 52, stroke(argb(0x2a2f3a), 1.6f))
    val sail = poly(boatX + 2, horizon - 50, boatX + 2, horizon - 2, boatX + 30, horizon - 2)
    c.drawPath(sail, fill(argb(0xf7f2e8, 0.95f)))
    c.drawPath(sail, stroke(argb(0x2a2f3a), 1.2f))
    c.drawPath(poly(boatX - 2, horizon - 40, boatX - 2, horizon - 2, boatX - 22, horizon - 2), fill(argb(0x2f7d76, 0.85f)))
    // The selected pen path, with anchors and a handle.
    val sel = argb(0x3f8cff)
    val p0x = bx + 24; val p0y = by + bh * 0.3f
    val p1x = bx + bw * 0.42f; val p1y = by + bh * 0.14f
    val p2x = bx + bw - 30; val p2y = by + bh * 0.26f
    val path = Path().apply {
        moveTo(p0x, p0y)
        cubicTo(p0x + 40, p0y - 30, p1x - 60, p1y, p1x, p1y)
        cubicTo(p1x + 60, p1y, p2x - 50, p2y - 30, p2x, p2y)
    }
    c.drawPath(path, stroke(sel, 1.4f))
    c.drawLine(p1x - 60, p1y, p1x + 60, p1y, stroke(argb(0x3f8cff, 0.7f), 1f))
    c.drawCircle(p1x - 60, p1y, 3f, fill(sel))
    c.drawCircle(p1x + 60, p1y, 3f, fill(sel))
    for ((x, y) in listOf(p0x to p0y, p1x to p1y, p2x to p2y)) {
        c.drawRect(x - 4, y - 4, x + 4, y + 4, fill(argb(0xffffff)))
        c.drawRect(x - 4, y - 4, x + 4, y + 4, stroke(sel, 1.4f).apply { strokeJoin = Paint.Join.MITER; strokeCap = Paint.Cap.BUTT })
    }
    c.restore()
    return bmp
}

/** The dock: an anodised bar with two shadows and a lit top edge, rendered once (full-screen size). */
internal fun magnetRenderDock(g: MagnetGeo, density: Float): Bitmap {
    val bmp = Bitmap.createBitmap((g.w * density).roundToInt().coerceAtLeast(1), (g.h * density).roundToInt().coerceAtLeast(1), Bitmap.Config.ARGB_8888)
    val c = Canvas(bmp)
    c.scale(density, density)
    val x = g.dockX
    val y = g.dockY
    val w = g.dockW
    c.drawRoundRect(rr(x + 6, y + 18, w - 12, MAGNET_DOCK_H - 4), 30f, 30f, blurred(argb(0, 0.5f), 18f))
    c.drawRoundRect(rr(x, y + 2, w, MAGNET_DOCK_H), 30f, 30f, blurred(argb(0, 0.45f), 2.5f))
    c.drawRoundRect(rr(x, y, w, MAGNET_DOCK_H), 30f, 30f, linear(0f, y, 0f, y + MAGNET_DOCK_H, intArrayOf(argb(0x34373e), argb(0x2a2d33), argb(0x212328), argb(0x1a1c20)), floatArrayOf(0f, 0.3f, 0.7f, 1f)))
    c.save()
    c.clipPath(Path().apply { addRoundRect(rr(x, y, w, MAGNET_DOCK_H), 30f, 30f, Path.Direction.CW) })
    // Brushed grain: noise stretched along y, overlaid at 6 %.
    c.drawRect(x, y, x + w, y + MAGNET_DOCK_H, Paint().apply {
        shader = noiseShader(96, 9, 9f)
        alpha = (0.06f * 255).roundToInt()
        xfermode = PorterDuffXfermode(PorterDuff.Mode.OVERLAY)
    })
    c.restore()
    c.drawRoundRect(rr(x + 0.5f, y + 0.5f, w - 1, MAGNET_DOCK_H - 1), 29.5f, 29.5f, stroke(argb(0xffffff, 0.12f), 1f))
    return bmp
}

/** The live drawing's paints and paths, made once. Blurs with a moving radius are cached per step. */
internal class MagnetPainter {
    private val plateRect = RectF()
    private val plateFill = Paint(Paint.ANTI_ALIAS_FLAG)
    private var plateShaderY = Float.NaN
    private val plateEdge = stroke(argb(0, 0.5f), 1f)
    private val plateLip = stroke(argb(0xffffff, 0.12f), 1f)
    private val ledGlow = fill(0).apply { if (LiveBlur) maskFilter = BlurMaskFilter(sigmaRadius(4f), BlurMaskFilter.Blur.NORMAL) }
    private val led = fill(0)
    private val contact = fill(0).apply { if (LiveBlur) maskFilter = BlurMaskFilter(sigmaRadius(2f), BlurMaskFilter.Blur.NORMAL) }
    private val ambient = fill(argb(0, 0.3f))
    private val ambientBlurs = HashMap<Int, BlurMaskFilter>()
    private val glow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f
        if (LiveBlur) maskFilter = BlurMaskFilter(sigmaRadius(3f), BlurMaskFilter.Blur.NORMAL)
    }
    private val dash = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.1f
        strokeCap = Paint.Cap.ROUND
    }
    private val dashIntervals = floatArrayOf(5f, 7f)
    private val curve = Path()
    private val fingerHalo = fill(0).apply { if (LiveBlur) maskFilter = BlurMaskFilter(sigmaRadius(10f), BlurMaskFilter.Blur.NORMAL) }
    private val fingerFill = fill(0)
    private val fingerRing = stroke(0, 1.4f)
    private val fingerCore = fill(0).apply { if (LiveBlur) maskFilter = BlurMaskFilter(sigmaRadius(2f), BlurMaskFilter.Blur.NORMAL) }
    private val facePaint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
    private val faceMatrix = Matrix()
    private val r = RectF()

    /** The recessed plate under the active tool, and its status light. */
    fun plate(c: Canvas, g: MagnetGeo, px: Float, color: Int) {
        val s = MAGNET_ICON + 10
        val y = g.cy.toFloat() - s / 2
        if (plateShaderY != y) {
            plateShaderY = y
            plateFill.shader = LinearGradient(0f, y, 0f, y + s, intArrayOf(argb(0x0e0f12), argb(0x16181c), argb(0x1c1e23)), floatArrayOf(0f, 0.4f, 1f), Shader.TileMode.CLAMP)
        }
        plateRect.set(px - s / 2, y, px + s / 2, y + s)
        c.drawRoundRect(plateRect, 17f, 17f, plateFill)
        r.set(px - s / 2 + 0.5f, y + 0.5f, px + s / 2 - 0.5f, y + s - 0.5f)
        c.drawRoundRect(r, 16.5f, 16.5f, plateEdge)
        c.drawLine(px - s / 2 + 12, y + s - 0.5f, px + s / 2 - 12, y + s - 0.5f, plateLip)
        val ly = g.dockY + MAGNET_DOCK_H - 7
        if (LiveBlur) {
            ledGlow.color = argb(color, 0.6f)
            c.drawCircle(px, ly, 5f, ledGlow)
        }
        led.color = argb(color)
        r.set(px - 7, ly - 1.5f, px + 7, ly + 1.5f)
        c.drawRoundRect(r, 1.5f, 1.5f, led)
    }

    /** A tile's two shadows: tight contact and soft ambient, which grows as the tile lifts. */
    fun tileShadow(c: Canvas, x: Float, y: Float, scale: Float, lift: Float) {
        val s = MAGNET_ICON * scale
        contact.color = argb(0, max(0.12f, 0.38f - lift * 0.012f))
        r.set(x - s / 2 + 2, y - s / 2 + 2 + min(2f, lift * 0.1f), x + s / 2 - 2, y + s / 2 + 2 + min(2f, lift * 0.1f))
        c.drawRoundRect(r, R * scale, R * scale, contact)
        if (LiveBlur) {
            // The ambient blur's sigma, 7 + lift · 0.35, in steps of 0.5 so filters are reused.
            val key = ((7f + lift * 0.35f) * 2).roundToInt().coerceAtLeast(1)
            ambient.maskFilter = ambientBlurs.getOrPut(key) { BlurMaskFilter(sigmaRadius(key / 2f), BlurMaskFilter.Blur.NORMAL) }
            r.set(x - s / 2 + 4, y - s / 2 + 8 + lift * 0.45f, x + s / 2 - 4, y + s / 2 + 8 + lift * 0.45f)
            c.drawRoundRect(r, R * scale, R * scale, ambient)
        }
    }

    /** Field lines from each pulled tile to the finger: three bowed curves per tile, dashes flowing toward the finger. */
    fun field(c: Canvas, xs: DoubleArray, ys: DoubleArray, scale: DoubleArray, f: DoubleArray, fx: Float, fy: Float, phase: Float) {
        dash.pathEffect = DashPathEffect(dashIntervals, phase)
        for (i in xs.indices) {
            if (f[i] < 0.04) continue
            val fi = f[i].toFloat()
            val xi = xs[i].toFloat()
            val top = ys[i].toFloat() - MAGNET_ICON * scale[i].toFloat() / 2 + 2
            for (k in -1..1) {
                val sx = xi + k * 9 * scale[i].toFloat()
                val ex = fx + k * 5
                val ey = fy + 12
                val mx = (sx + ex) / 2
                val my = (top + ey) / 2
                val dx = ex - sx
                val dy = ey - top
                val len = max(1f, hypot(dx, dy))
                val bow = k * 22 + (xi - fx) * 0.12f
                curve.rewind()
                curve.moveTo(sx, top)
                curve.quadTo(mx + (-dy / len) * bow, my + (dx / len) * bow, ex, ey)
                if (LiveBlur) {
                    glow.color = argb(0x7fc8ff, 0.16f * fi)
                    c.drawPath(curve, glow)
                }
                dash.color = argb(0xa9dcff, (if (k == 0) 0.75f else 0.45f) * fi)
                c.drawPath(curve, dash)
            }
        }
    }

    /** One enamel tile, its face bitmap scaled about its centre. */
    fun face(c: Canvas, face: Bitmap, x: Float, y: Float, scale: Float, density: Float) {
        val k = scale / (density * MAGNET_FACE_OVERSAMPLE)
        faceMatrix.setScale(k, k)
        faceMatrix.postTranslate(x - MAGNET_ICON * scale, y - MAGNET_ICON * scale)
        c.drawBitmap(face, faceMatrix, facePaint)
    }

    /** The fingertip: a soft disc with a cool magnetic core. */
    fun finger(c: Canvas, x: Float, y: Float, a: Float) {
        if (a < 0.01f) return
        val rad = 22f * (1.12f - 0.12f * a)
        if (LiveBlur) {
            fingerHalo.color = argb(0x7fc8ff, 0.14f * a)
            c.drawCircle(x, y, rad + 8, fingerHalo)
        }
        fingerFill.color = argb(0xeaf4ff, 0.14f * a)
        c.drawCircle(x, y, rad, fingerFill)
        fingerRing.color = argb(0xeaf4ff, 0.5f * a)
        c.drawCircle(x, y, rad, fingerRing)
        fingerCore.color = argb(0xbfe4ff, 0.8f * a)
        c.drawCircle(x, y, 4f, fingerCore)
    }
}

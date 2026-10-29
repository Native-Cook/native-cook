package com.ngocdevv.nativecook.blocks.splash.cheesedripburger

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapShader
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.os.Build
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

class CheeseDripBurgerResources(val burger: Bitmap?, val anton: Typeface, val semibold: Typeface, val grain: Bitmap)

/**
 * Drawing for Cheese Drip Burger, ported from CheeseDripBurgerArt.swift. The cheese is analytic:
 * one path per slice (band, drips, drops, puddle; every subpath clockwise, so the nonzero fill
 * unites them), filled in a layer: the rim colour first, then the gradient body nudged up-left
 * with SRC_ATOP, the right-side shade and a light crescent on the upper-left edges.
 */
object CheeseDripBurgerArt {
    private val m = CheeseDripBurgerMotion

    const val RED = 0xAF0002
    const val RED_GLOW = 0xC70D08
    const val RED_DEEP = 0x6E0002
    const val ROW_FAINT = 0xBC0805
    const val YELLOW = 0xF9C93C
    const val CREAM = 0xFFF2D6
    const val SAND = 0xFFE9C2

    private val cheesePos = floatArrayOf(0f, 0.22f, 0.6f, 1f)
    private val cheeseColors = intArrayOf(0xF0A010, 0xFFB71A, 0xFFCB30, 0xFFDA5C)
    const val RIM = 0xC86A04
    const val SHADE = 0xD9730A
    const val SHADOW = 0x3B1200
    const val GLOSS = 0xFFFBEA

    const val WORD_SIZE = 128.0
    const val ROW_SIZE = 150.0
    const val ROW_PITCH = 133.0
    const val IMAGE_DRAW = 350.0
    val burgerScale get() = IMAGE_DRAW / m.IMAGE_SIZE

    /** Baseline below a line's centre, in em: Anton's (ascent − descent) / 2, like a SwiftUI Text frame. */
    const val ANTON_MID = 0.42359

    fun argb(hex: Int, alpha: Double = 1.0): Int {
        val a = (m.clamp(alpha) * 255 + 0.5).toInt()
        return (a shl 24) or (hex and 0xFFFFFF)
    }

    fun load(context: Context): CheeseDripBurgerResources {
        val burger = runCatching {
            context.assets.open("cheesedripburger/cheesedripburger_burger.webp").use { stream ->
                BitmapFactory.decodeStream(stream, null, BitmapFactory.Options().apply {
                    inScaled = false
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                })?.also { it.prepareToDraw() }
            }
        }.getOrNull()
        val anton = runCatching { Typeface.createFromAsset(context.assets, "cheesedripburger/cheesedripburger_Anton-Regular.ttf") }
            .getOrDefault(Typeface.create("sans-serif-condensed", Typeface.BOLD))
        val semibold = if (Build.VERSION.SDK_INT >= 28) Typeface.create(Typeface.DEFAULT, 600, false) else Typeface.DEFAULT_BOLD
        val n = 128
        val pixels = IntArray(n * n) { i ->
            val v = (m.hash(i * 0.731 + 0.5) * 255).toInt()
            (0xFF shl 24) or (v shl 16) or (v shl 8) or v
        }
        val grain = Bitmap.createBitmap(pixels, n, n, Bitmap.Config.ARGB_8888)
        return CheeseDripBurgerResources(burger, anton, semibold, grain)
    }

    class Layout(val width: Double, val height: Double, val top: Double, bottom: Double) {
        val brandY = top + 22
        val topWordY = top + 101
        val productY = height - max(bottom, 16.0) - 20
        val labelY = productY - 40
        val bottomWordY = labelY - 76
        val burgerCY = (topWordY + bottomWordY) / 2 + 6
        val imageX get() = 196.5 - IMAGE_DRAW / 2
        val imageY get() = burgerCY - IMAGE_DRAW / 2
        val baseY get() = imageY + m.PUDDLE_Y * burgerScale
    }

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG)
    private val layerPaint = Paint()
    private val image = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

    private fun resetFill(color: Int): Paint = fill.apply {
        reset(); isAntiAlias = true; style = Paint.Style.FILL; this.color = color
    }

    // Background

    fun drawBackground(c: Canvas, l: Layout, res: CheeseDripBurgerResources) {
        val w = l.width.toFloat()
        val h = l.height.toFloat()
        c.drawRect(0f, 0f, w, h, resetFill(argb(RED)))
        resetFill(0xFFFFFFFF.toInt()).shader = RadialGradient(196.5f, l.burgerCY.toFloat(), 330f, intArrayOf(argb(RED_GLOW), argb(RED_GLOW, 0.0)), null, Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, w, h, fill)
        resetFill(0xFFFFFFFF.toInt()).shader = RadialGradient(
            196.5f, (l.height * 0.48).toFloat(), max(l.height * 0.62, 420.0).toFloat(),
            intArrayOf(argb(RED_DEEP, 0.0), argb(RED_DEEP, 0.0), argb(RED_DEEP, 0.6)), floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP,
        )
        c.drawRect(0f, 0f, w, h, fill)
        resetFill(0xFFFFFFFF.toInt()).apply {
            shader = BitmapShader(res.grain, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
            alpha = (0.05 * 255).toInt()
            xfermode = PorterDuffXfermode(PorterDuff.Mode.OVERLAY)
        }
        c.drawRect(0f, 0f, w, h, fill)
        fill.xfermode = null
        fill.shader = null
    }

    /** Draws `s` centred on (cx, cy) like a SwiftUI Text, with `tracking` pt after each letter. */
    fun drawWord(c: Canvas, s: String, cx: Double, cy: Double, size: Double, typeface: Typeface, color: Int, tracking: Double = 0.0) {
        text.reset()
        text.isAntiAlias = true
        text.typeface = typeface
        text.textSize = size.toFloat()
        text.color = color
        val widths = FloatArray(s.length)
        text.getTextWidths(s, widths)
        val total = widths.sum() + tracking * s.length
        var x = cx - total / 2
        val baseline = (cy + ANTON_MID * size).toFloat()
        if (tracking == 0.0) {
            c.drawText(s, x.toFloat(), baseline, text)
            return
        }
        for (i in s.indices) {
            c.drawText(s, i, i + 1, x.toFloat(), baseline, text)
            x += widths[i] + tracking
        }
    }

    fun wordWidth(s: String, size: Double, typeface: Typeface, tracking: Double): Double {
        text.reset()
        text.typeface = typeface
        text.textSize = size.toFloat()
        val widths = FloatArray(s.length)
        text.getTextWidths(s, widths)
        return widths.sum() + tracking * s.length
    }

    /** A line of system text centred on (cx, cy), its line box centred like a SwiftUI Text. */
    fun drawLine(c: Canvas, s: String, cx: Double, cy: Double, size: Double, typeface: Typeface, color: Int) {
        text.reset()
        text.isAntiAlias = true
        text.typeface = typeface
        text.textSize = size.toFloat()
        text.color = color
        text.textAlign = Paint.Align.CENTER
        val fm = text.fontMetrics
        c.drawText(s, cx.toFloat(), (cy - (fm.ascent + fm.descent) / 2).toFloat(), text)
        text.textAlign = Paint.Align.LEFT
    }

    fun drawRows(c: Canvas, l: Layout, lit: DoubleArray, slide: DoubleArray, word: String, res: CheeseDripBurgerResources) {
        for (i in 0 until m.ROWS) {
            val cy = l.burgerCY + (i - 1) * ROW_PITCH
            drawWord(c, word, 196.5, cy, ROW_SIZE, res.anton, argb(ROW_FAINT))
            if (lit[i] > 0.002) drawWord(c, word, 196.5 + slide[i], cy, ROW_SIZE, res.anton, argb(YELLOW, lit[i]))
        }
    }

    fun drawFloorShadow(c: Canvas, l: Layout, lift: Double) {
        val f = m.clamp(1 - lift / 420)
        val w = (300 * (0.55 + 0.45 * f)).toFloat()
        c.save()
        c.translate(196.5f, (l.baseY + 8).toFloat())
        c.scale(1f, 0.16f)
        resetFill(0xFFFFFFFF.toInt()).shader = RadialGradient(0f, 0f, w / 2, intArrayOf(argb(0x3A0000, 0.9 * 0.55 * f), argb(0x3A0000, 0.0)), null, Shader.TileMode.CLAMP)
        c.drawOval(-w / 2, -w / 2, w / 2, w / 2, fill)
        fill.shader = null
        c.restore()
    }

    /** The brand mark: bun dome, patty, bun base (22 × 15), at (x, y) top-left. */
    fun drawMark(c: Canvas, x: Float, y: Float, tint: Int) {
        c.save()
        c.translate(x, y)
        val p = Path()
        p.moveTo(1f, 7f)
        p.cubicTo(1f, -1.5f, 21f, -1.5f, 21f, 7f)
        p.close()
        c.drawPath(p, resetFill(tint))
        c.drawRoundRect(0f, 8.4f, 22f, 11f, 1.3f, 1.3f, fill)
        c.drawRoundRect(1.5f, 12f, 20.5f, 15f, 1.5f, 1.5f, fill)
        resetFill(argb(RED))
        for ((sx, sy) in listOf(7f to 3.6f, 11f to 2.4f, 15f to 3.6f)) c.drawOval(sx - 0.9f, sy - 0.6f, sx + 0.9f, sy + 0.6f, fill)
        c.restore()
    }

    // Steam (burger space)

    fun drawSteam(c: Canvas, ambient: Double, strength: Double) {
        for (i in 0 until 3) {
            val x0 = 140.0 + i * 46
            val k = (i + 1).toDouble()
            val phase = 2 * Math.PI * k * ambient / m.LOOP
            val p = Path()
            for (j in 0..22) {
                val s = j / 22.0
                val y = 34 - s * 120
                val x = x0 + (4 + 10 * s) * sin(s * 5.2 - phase + i * 1.9)
                if (j == 0) p.moveTo(x.toFloat(), y.toFloat()) else p.lineTo(x.toFloat(), y.toFloat())
            }
            val breathe = 0.6 + 0.4 * sin(2 * Math.PI * ambient / m.LOOP * 2 + i * 2.1)
            stroke.reset()
            stroke.isAntiAlias = true
            stroke.style = Paint.Style.STROKE
            stroke.strokeCap = Paint.Cap.ROUND
            stroke.strokeJoin = Paint.Join.ROUND
            stroke.strokeWidth = (11 - i * 1.5).toFloat()
            stroke.color = 0xFFFFFFFF.toInt()
            stroke.shader = LinearGradient(0f, 34f, 0f, -86f,
                intArrayOf(argb(0xFFFFFF, 0.0), argb(0xFFFFFF, 0.22 * breathe * strength), argb(0xFFFFFF, 0.0)),
                floatArrayOf(0f, 0.35f, 1f), Shader.TileMode.CLAMP)
            stroke.maskFilter = BlurMaskFilter(8.5f, BlurMaskFilter.Blur.NORMAL)
            c.drawPath(p, stroke)
        }
        stroke.shader = null
        stroke.maskFilter = null
    }

    // Cheese (burger space)

    private fun bumpAt(bumps: List<CheeseDripBurgerMotion.Bump>, layer: Int, x: Double): Double {
        var s = 0.0
        for (b in bumps) if (b.layer == layer) {
            val d = (x - b.x) / 14
            s += b.amp * exp(-d * d)
        }
        return s
    }

    private fun bandThick(li: Int, x: Double, melt: Double, bumps: List<CheeseDripBurgerMotion.Bump>): Double {
        val layer = m.layers[li]
        val u = abs((x - layer.cx) / layer.half)
        val taper = 0.3 + 0.7 * sqrt(m.clamp((1 - u) * 10))
        val wave = 1.6 * sin(x * 0.11 + li * 1.7) + 1.0 * sin(x * 0.27 + li)
        return (layer.band * (1 + 0.45 * melt) + wave) * taper + bumpAt(bumps, li, x)
    }

    private fun addBand(p: Path, li: Int, melt: Double, bumps: List<CheeseDripBurgerMotion.Bump>) {
        val layer = m.layers[li]
        val x0 = layer.cx - layer.half
        val x1 = layer.cx + layer.half
        val steps = 48
        p.moveTo(x0.toFloat(), (layer.edge(x0) - 6).toFloat())
        for (s in 1..steps) {
            val x = x0 + (x1 - x0) * s / steps
            p.lineTo(x.toFloat(), (layer.edge(x) - 6).toFloat())
        }
        for (s in steps downTo 0) {
            val x = x0 + (x1 - x0) * s / steps
            p.lineTo(x.toFloat(), (layer.edge(x) + bandThick(li, x, melt, bumps)).toFloat())
        }
        p.close()
    }

    private class Geo(val x: Double, val w: Double, val wn: Double, val r: Double, val yb: Double, val yn: Double, val y1: Double, val top: Double, val fillet: Double)

    private fun geometry(d: CheeseDripBurgerMotion.DripShape, yj: Double): Geo {
        val wn = m.lerp(d.w * 0.8, d.w * 0.18, d.neck)
        val r = max(d.r, wn * 0.5 + 0.5)
        val yb = d.y0 + d.length
        val top = yj - 5
        val fillet = 0.35 * d.w + 3
        val yn = max(yb - r * 0.72, top + 1)
        val y1 = minOf(top + fillet * 0.9, yn)
        return Geo(d.x, d.w, wn, r, yb, yn, y1, top, fillet)
    }

    private fun Path.cubic(c1x: Double, c1y: Double, c2x: Double, c2y: Double, x: Double, y: Double) =
        cubicTo(c1x.toFloat(), c1y.toFloat(), c2x.toFloat(), c2y.toFloat(), x.toFloat(), y.toFloat())

    private fun addDrip(p: Path, g: Geo) {
        val x = g.x; val hw = g.w / 2; val hn = g.wn / 2; val r = g.r; val yb = g.yb; val yn = g.yn; val y1 = g.y1; val top = g.top; val fr = g.fillet
        p.moveTo((x + hw + fr).toFloat(), top.toFloat())
        p.cubic(x + hw + fr * 0.35, top, x + hw, top + fr * 0.4, x + hw, y1)
        p.cubic(x + hw, m.lerp(y1, yn, 0.5), x + hn, m.lerp(y1, yn, 0.55), x + hn, yn)
        p.cubic(x + hn, yn + r * 0.22, x + r, yb - r * 0.5, x + r, yb)
        p.cubic(x + r, yb + r * 0.552, x + r * 0.552, yb + r, x, yb + r)
        p.cubic(x - r * 0.552, yb + r, x - r, yb + r * 0.552, x - r, yb)
        p.cubic(x - r, yb - r * 0.5, x - hn, yn + r * 0.22, x - hn, yn)
        p.cubic(x - hn, m.lerp(y1, yn, 0.55), x - hw, m.lerp(y1, yn, 0.5), x - hw, y1)
        p.cubic(x - hw, top + fr * 0.4, x - hw - fr * 0.35, top, x - hw - fr, top)
        p.close()
    }

    private fun addBlob(p: Path, x: Double, y: Double, rx: Double, ry: Double) {
        val k = 0.552
        p.moveTo(x.toFloat(), (y - ry).toFloat())
        p.cubic(x + rx * k, y - ry, x + rx, y - ry * k, x + rx, y)
        p.cubic(x + rx, y + ry * k, x + rx * k, y + ry, x, y + ry)
        p.cubic(x - rx * k, y + ry, x - rx, y + ry * k, x - rx, y)
        p.cubic(x - rx, y - ry * k, x - rx * k, y - ry, x, y - ry)
        p.close()
    }

    private fun puddleHalfWidth(melt: Double, bumps: List<CheeseDripBurgerMotion.Bump>) = 4 + 94 * melt + bumpAt(bumps, 2, m.PUDDLE_CX) * 2

    private fun addPuddle(p: Path, melt: Double, bumps: List<CheeseDripBurgerMotion.Bump>) {
        val hw = puddleHalfWidth(melt, bumps)
        val h = 1.5 + 8.5 * melt
        val steps = 40
        fun lump(x: Double): Double {
            val a = (x - (m.PUDDLE_CX - hw * 0.45)) / 16
            val b = (x - (m.PUDDLE_CX + hw * 0.4)) / 20
            return 2.4 * exp(-a * a) + 1.8 * exp(-b * b)
        }
        for (s in 0..steps) {
            val f = s.toDouble() / steps
            val x = m.PUDDLE_CX - hw + 2 * hw * f
            val e = sqrt(max(0.0, 1 - (2 * f - 1).pow(2)))
            val y = m.PUDDLE_Y - (h * e.pow(0.7) + lump(x) * e + bumpAt(bumps, 2, x) * e)
            if (s == 0) p.moveTo(x.toFloat(), y.toFloat()) else p.lineTo(x.toFloat(), y.toFloat())
        }
        for (s in steps downTo 0) {
            val f = s.toDouble() / steps
            val x = m.PUDDLE_CX - hw + 2 * hw * f
            val e = sqrt(max(0.0, 1 - (2 * f - 1).pow(2)))
            p.lineTo(x.toFloat(), (m.PUDDLE_Y + h * 0.75 * e.pow(0.8)).toFloat())
        }
        p.close()
    }

    private val goo = Path()
    private val bounds = RectF(-40f, -40f, 420f, 440f)

    /** Both slices, lower first so the upper one runs over it. The canvas is in burger space. */
    fun drawCheese(c: Canvas, f: CheeseDripBurgerMotion.Frame) {
        for (li in m.layers.indices) {
            val layer = m.layers[li]
            goo.reset()
            addBand(goo, li, f.melt, f.bumps)
            val geos = f.drips[li].map { d ->
                geometry(d, layer.edge(d.x) + bandThick(li, d.x, f.melt, f.bumps)).also { addDrip(goo, it) }
            }
            if (li == 0) {
                if (f.melt > 0.02) addPuddle(goo, f.melt, f.bumps)
                for (d in f.drops) addBlob(goo, d.x, d.y, d.rx, d.ry)
            }

            // Soft shadow on the patty below.
            c.save()
            c.translate(2f, 4f)
            resetFill(argb(SHADOW, 0.45)).maskFilter = BlurMaskFilter(6f, BlurMaskFilter.Blur.NORMAL)
            c.drawPath(goo, fill)
            fill.maskFilter = null
            c.restore()

            val save = c.saveLayer(bounds, null)
            c.drawPath(goo, resetFill(argb(RIM)))
            // Body nudged up-left over the rim colour.
            c.save()
            c.translate(-1.1f, -1.7f)
            resetFill(0xFFFFFFFF.toInt()).apply {
                shader = LinearGradient(0f, (layer.y - 6).toFloat(), 0f, (layer.y + 110).toFloat(),
                    IntArray(4) { argb(cheeseColors[it]) }, cheesePos, Shader.TileMode.CLAMP)
                xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_ATOP)
            }
            c.drawPath(goo, fill)
            fill.shader = null
            c.restore()
            // Right-side shade on each drip.
            stroke.reset()
            stroke.isAntiAlias = true
            stroke.style = Paint.Style.STROKE
            stroke.strokeCap = Paint.Cap.ROUND
            stroke.strokeJoin = Paint.Join.ROUND
            stroke.color = argb(SHADE, 0.38)
            stroke.maskFilter = BlurMaskFilter(4f, BlurMaskFilter.Blur.NORMAL)
            stroke.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_ATOP)
            val shade = Path()
            for (g in geos) {
                if (g.yb + g.r <= g.top + 10) continue
                shade.reset()
                shade.moveTo((g.x + g.w * 0.36).toFloat(), g.y1.toFloat())
                shade.lineTo((g.x + g.wn * 0.32).toFloat(), g.yn.toFloat())
                shade.lineTo((g.x + g.r * 0.6).toFloat(), (g.yb + g.r * 0.25).toFloat())
                stroke.strokeWidth = max(2.0, g.w * 0.26).toFloat()
                c.drawPath(shade, stroke)
            }
            stroke.maskFilter = null
            stroke.xfermode = null
            // A thin light crescent on every upper-left edge.
            layerPaint.reset()
            layerPaint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_ATOP)
            val crescent = c.saveLayer(bounds, layerPaint)
            c.drawPath(goo, resetFill(argb(GLOSS, 0.7)))
            c.save()
            c.translate(1.2f, 1.8f)
            resetFill(0xFF000000.toInt()).xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OUT)
            c.drawPath(goo, fill)
            fill.xfermode = null
            c.restore()
            c.restoreToCount(crescent)
            fill.xfermode = null
            c.restoreToCount(save)

            drawGloss(c, li, geos, if (li == 0) f.drops else emptyList(), f.melt, f.bumps)
        }
    }

    private fun drawGloss(c: Canvas, li: Int, geos: List<Geo>, drops: List<CheeseDripBurgerMotion.DropShape>, melt: Double, bumps: List<CheeseDripBurgerMotion.Bump>) {
        val blur = BlurMaskFilter(1f, BlurMaskFilter.Blur.NORMAL)
        stroke.reset()
        stroke.isAntiAlias = true
        stroke.style = Paint.Style.STROKE
        stroke.strokeCap = Paint.Cap.ROUND
        stroke.color = argb(GLOSS, 0.62)
        stroke.maskFilter = blur
        resetFill(argb(GLOSS, 0.9)).maskFilter = blur
        val streak = Path()
        for (g in geos) {
            if (g.yb - g.y1 > 8) {
                streak.reset()
                streak.moveTo((g.x - g.w * 0.22).toFloat(), (g.y1 + 1).toFloat())
                streak.quadTo((g.x - g.wn * 0.28).toFloat(), g.yn.toFloat(), (g.x - g.r * 0.5).toFloat(), (g.yb - g.r * 0.45).toFloat())
                stroke.strokeWidth = max(1.2, g.w * 0.1).toFloat()
                c.drawPath(streak, stroke)
            }
            val gw = g.r * 0.4
            val gh = g.r * 0.55
            val cx = g.x - g.r * 0.48
            val cy = g.yb - g.r * 0.34
            c.drawOval((cx - gw / 2).toFloat(), (cy - gh / 2).toFloat(), (cx + gw / 2).toFloat(), (cy + gh / 2).toFloat(), fill)
        }
        for (d in drops) {
            if (d.rx <= 1) continue
            val gw = d.rx * 0.42
            val gh = d.ry * 0.5
            val cx = d.x - d.rx * 0.46
            val cy = d.y - d.ry * 0.38
            c.drawOval((cx - gw / 2).toFloat(), (cy - gh / 2).toFloat(), (cx + gw / 2).toFloat(), (cy + gh / 2).toFloat(), fill)
        }
        if (li == 0 && melt > 0.15) {
            val hw = puddleHalfWidth(melt, bumps)
            val arc = Path()
            arc.moveTo((m.PUDDLE_CX - hw * 0.7).toFloat(), (m.PUDDLE_Y - 2 - 2 * melt).toFloat())
            arc.quadTo((m.PUDDLE_CX - hw * 0.4).toFloat(), (m.PUDDLE_Y - 6 - 4 * melt).toFloat(),
                (m.PUDDLE_CX - hw * 0.05).toFloat(), (m.PUDDLE_Y - 3.5 - 3 * melt).toFloat())
            stroke.color = argb(GLOSS, 0.6)
            stroke.strokeWidth = 1.8f
            c.drawPath(arc, stroke)
        }
        stroke.maskFilter = null
        fill.maskFilter = null
    }

    fun drawBurgerImage(c: Canvas, res: CheeseDripBurgerResources) {
        val bmp = res.burger ?: return
        image.reset()
        image.isAntiAlias = true
        image.isFilterBitmap = true
        c.drawBitmap(bmp, null, RectF(0f, 0f, m.IMAGE_SIZE.toFloat(), m.IMAGE_SIZE.toFloat()), image)
    }
}

package com.ngocdevv.nativecook.blocks.progress.xraybagscan

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PathMeasure
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.os.Build
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/** The two bundled suitcase images (same crop) and a cheap blurred copy of the x-ray for its glow. */
internal class XrayBagScanImages(val photo: Bitmap?, val xray: Bitmap?, val glow: Bitmap?)

/**
 * Everything of X-Ray Bag Scan drawn in code on a native canvas, in the 393-wide design space (scene y
 * measured for a 59-unit safe-area top; `sy` shifts it): the terminal wall, the belt, the scanner
 * gantry, the x-ray window, detection boxes and the small icons.
 */
internal object XrayBagScanArt {
    private val m = XrayBagScanMotion

    const val INK = 0x2B1A0E
    const val BROWN = 0x5A3418
    const val MUTED = 0x8A6A50
    const val CARD = 0xFFF7EC
    const val TRACK = 0xF0E2CF
    const val ACCENT = 0xF08A2C
    const val GREEN = 0x3DBE6B
    const val CYAN = 0x6FE6FF
    const val BOX_GREEN = 0x7CF2A0
    const val FLOOR = 0x857762

    const val IMAGE_ASPECT = 329.0 / 600.0
    val imageW = m.BAG_H * IMAGE_ASPECT

    class Item(val label: String, val x0: Double, val y0: Double, val x1: Double, val y1: Double, val below: Boolean, val right: Boolean)

    val items = listOf(
        Item("Liquids 100 ml", 0.12, 0.29, 0.50, 0.47, below = false, right = false),
        Item("Headphones", 0.53, 0.31, 0.91, 0.50, below = true, right = true),
        Item("Laptop", 0.10, 0.50, 0.89, 0.70, below = true, right = false),
    )

    /** Item centres relative to the bag's centre, for the scrub reveal. */
    val itemOffsets = DoubleArray(items.size) { ((items[it].x0 + items[it].x1) / 2 - 0.5) * imageW }

    fun load(context: Context): XrayBagScanImages {
        fun read(name: String): Bitmap? = runCatching {
            context.assets.open("xraybagscan/xraybagscan_$name.webp").use { stream ->
                BitmapFactory.decodeStream(stream, null, BitmapFactory.Options().apply {
                    inScaled = false
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                })?.also { it.prepareToDraw() }
            }
        }.getOrNull()
        val photo = read("suitcase")
        val xray = read("suitcase_xray")
        val glow = xray?.let { Bitmap.createScaledBitmap(it, max(1, it.width / 10), max(1, it.height / 10), true) }
        return XrayBagScanImages(photo, xray, glow)
    }

    // MARK: Colour and paint

    fun argb(hex: Int, alpha: Double = 1.0): Int {
        val a = (m.clamp(alpha) * 255 + 0.5).toInt()
        return (a shl 24) or (hex and 0xFFFFFF)
    }

    fun mix(a: Int, b: Int, f: Double): Int {
        val g = m.clamp(f)
        fun ch(x: Int, s: Int) = (x shr s) and 0xFF
        fun c(s: Int) = (ch(a, s) + (ch(b, s) - ch(a, s)) * g + 0.5).toInt().coerceIn(0, 255)
        return (c(16) shl 16) or (c(8) shl 8) or c(0)
    }

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND }
    private val image = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val text = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()
    private val path = Path()

    private fun font(weight: Int): Typeface =
        if (Build.VERSION.SDK_INT >= 28) Typeface.create(Typeface.create("sans-serif", Typeface.NORMAL), weight, false)
        else Typeface.create("sans-serif", if (weight >= 600) Typeface.BOLD else Typeface.NORMAL)

    private val semibold by lazy { font(600) }
    private val bold by lazy { font(700) }
    private val heavy by lazy { font(800) }

    private fun solid(color: Int): Paint {
        fill.shader = null
        fill.maskFilter = null
        fill.xfermode = null
        fill.color = color
        return fill
    }

    /** An opaque paint carrying a shader (an opaque colour first, so the previous alpha does not leak). */
    private fun shaded(shader: Shader): Paint {
        fill.maskFilter = null
        fill.xfermode = null
        fill.color = 0xFF000000.toInt()
        fill.shader = shader
        return fill
    }

    private fun vertical(y0: Double, y1: Double, vararg colors: Int) =
        LinearGradient(0f, y0.toFloat(), 0f, y1.toFloat(), colors, null, Shader.TileMode.CLAMP)

    private fun horizontal(x0: Double, x1: Double, vararg colors: Int) =
        LinearGradient(x0.toFloat(), 0f, x1.toFloat(), 0f, colors, null, Shader.TileMode.CLAMP)

    private fun radial(cx: Double, cy: Double, r: Double, vararg colors: Int) =
        RadialGradient(cx.toFloat(), cy.toFloat(), r.toFloat(), colors, null, Shader.TileMode.CLAMP)

    private fun r(x: Double, y: Double, w: Double, h: Double): RectF {
        rect.set(x.toFloat(), y.toFloat(), (x + w).toFloat(), (y + h).toFloat())
        return rect
    }

    private fun drawRect(c: Canvas, x: Double, y: Double, w: Double, h: Double, p: Paint) = c.drawRect(r(x, y, w, h), p)
    private fun drawRound(c: Canvas, x: Double, y: Double, w: Double, h: Double, rad: Double, p: Paint) =
        c.drawRoundRect(r(x, y, w, h), rad.toFloat(), rad.toFloat(), p)

    // MARK: Wall and floor

    fun drawBackground(c: Canvas, width: Double, height: Double, sy: Double) {
        val wallBottom = 204 + sy
        drawRect(c, 0.0, 0.0, width, wallBottom, shaded(vertical(0.0, wallBottom, 0xFFF6A54E.toInt(), 0xFFEF9238.toInt(), 0xFFE6812B.toInt())))
        for (x in doubleArrayOf(131.0, 262.0)) {
            drawRect(c, x, 0.0, 1.0, wallBottom, solid(argb(0x000000, 0.07)))
            drawRect(c, x + 1, 0.0, 1.0, wallBottom, solid(argb(0xFFFFFF, 0.10)))
        }
        drawRect(c, 0.0, 0.0, width, wallBottom, shaded(radial(70.0, 100 + sy, 300.0, argb(0xFFFFFF, 0.20), argb(0xFFFFFF, 0.0))))

        val tileTop = wallBottom
        val tileBottom = m.BELT_TOP + sy
        drawRect(c, 0.0, tileTop, width, tileBottom - tileTop,
            shaded(vertical(tileTop, tileBottom, 0xFF5E7A56.toInt(), 0xFF4F6A4A.toInt(), 0xFF425A3F.toInt())))
        val dark = solid(argb(0x1E2B1C, 0.22))
        var y = tileTop + 22
        while (y < tileBottom) { drawRect(c, 0.0, y, width, 1.0, dark); y += 22 }
        var x = 9.5
        while (x < width) { drawRect(c, x, tileTop, 1.0, tileBottom - tileTop, dark); x += 22 }
        val light = solid(argb(0xFFFFFF, 0.05))
        y = tileTop + 22
        while (y < tileBottom) { drawRect(c, 0.0, y + 1, width, 1.0, light); y += 22 }
        x = 9.5
        while (x < width) { drawRect(c, x + 1, tileTop, 1.0, tileBottom - tileTop, light); x += 22 }
        drawRect(c, 0.0, tileTop, width, tileBottom - tileTop, shaded(radial(196.5, 420 + sy, 230.0, argb(0xFFF3C8, 0.16), argb(0xFFF3C8, 0.0))))
        drawRect(c, 0.0, tileTop - 2, width, 10.0, shaded(vertical(tileTop - 2, tileTop + 8, 0xFFF7B15F.toInt(), 0xFFD8732A.toInt())))
        drawRect(c, 0.0, tileTop + 8, width, 6.0, shaded(vertical(tileTop + 8, tileTop + 14, argb(0x000000, 0.22), argb(0x000000, 0.0))))

        val floorTop = m.SKIRT_BOTTOM + sy
        drawRect(c, 0.0, floorTop, width, height - floorTop, shaded(vertical(floorTop, height, 0xFF9C8D78.toInt(), 0xFF857762.toInt())))

        drawSign(c, sy)
        drawPoster(c, sy)
    }

    private fun drawSign(c: Canvas, sy: Double) {
        val x = 16.0
        val y = 236 + sy
        val w = 92.0
        val h = 26.0
        drawRound(c, x, y + 3, w, h, 5.0, solid(argb(0x1E2B1C, 0.25)))
        drawRound(c, x, y, w, h, 5.0, shaded(vertical(y, y + h, 0xFFFADE52.toInt(), 0xFFEFC92F.toInt())))
        text.typeface = bold
        text.textSize = 12f
        text.color = argb(INK)
        text.textAlign = Paint.Align.LEFT
        c.drawText("Bag drop", (x + 9).toFloat(), (y + h / 2 + 4.2).toFloat(), text)
        val mx = x + w
        val my = y + h / 2
        path.reset()
        path.moveTo((mx - 22).toFloat(), my.toFloat()); path.lineTo((mx - 10).toFloat(), my.toFloat())
        path.moveTo((mx - 15).toFloat(), (my - 4.5).toFloat()); path.lineTo((mx - 10).toFloat(), my.toFloat())
        path.lineTo((mx - 15).toFloat(), (my + 4.5).toFloat())
        stroke.shader = null; stroke.maskFilter = null
        stroke.color = argb(INK); stroke.strokeWidth = 2f
        c.drawPath(path, stroke)
    }

    private fun drawPoster(c: Canvas, sy: Double) {
        c.save()
        c.translate(338f, (300 + sy).toFloat())
        c.rotate(5f)
        drawRound(c, -30.0, -38.0, 64.0, 84.0, 4.0, solid(argb(0x1E2B1C, 0.28)))
        drawRound(c, -32.0, -42.0, 64.0, 84.0, 4.0, shaded(vertical(-42.0, 42.0, 0xFFF47A43.toInt(), 0xFFE45A2E.toInt())))
        text.typeface = heavy
        text.textSize = 13f
        text.color = argb(0xFFF1DE)
        text.textAlign = Paint.Align.LEFT
        val lines = listOf("Pack", "light,", "fly", "bright")
        for ((i, line) in lines.withIndex()) c.drawText(line, -24f, (-42 + 7 + 12.5 + i * 15.5).toFloat(), text)
        c.drawCircle(0f, -39f, 3f, solid(argb(0xE8E2D6)))
        c.restore()
    }

    // MARK: Belt

    fun drawBelt(c: Canvas, width: Double, d: Double, sy: Double) {
        val top = m.BELT_TOP + sy
        val bottom = m.SKIRT_TOP + sy
        drawRect(c, 0.0, top, width, bottom - top, shaded(vertical(top, bottom, 0xFF4A484F.toInt(), 0xFF3A3940.toInt(), 0xFF2C2B31.toInt())))
        var x = m.mod(d, m.PITCH) - m.PITCH
        val seam = argb(0x1B1A1F)
        val shine = argb(0x6A6870, 0.7)
        while (x < width + m.PITCH) {
            drawRect(c, x, top + 1, 1.6, bottom - top - 1, solid(seam))
            drawRect(c, x + 1.6, top + 1, 1.0, bottom - top - 1, solid(shine))
            x += m.PITCH
        }
        drawRect(c, 0.0, top, width, 1.2, solid(argb(0xFFFFFF, 0.22)))

        val st = m.SKIRT_TOP + sy
        val sb = m.SKIRT_BOTTOM + sy
        drawRect(c, 0.0, st, width, sb - st, shaded(vertical(st, sb, 0xFFD6D9DE.toInt(), 0xFFBCC0C7.toInt(), 0xFF979CA5.toInt())))
        drawRect(c, 0.0, st, width, 1.2, solid(argb(0xFFFFFF, 0.6)))
        drawRect(c, 0.0, st + 9, width, 1.0, solid(argb(0x7C818A, 0.5)))
        var bx = 24.5
        val bolt = solid(argb(0x80858E))
        while (bx < width) { c.drawCircle(bx.toFloat(), (st + 18).toFloat(), 2f, bolt); bx += 49 }
        drawRect(c, 0.0, sb, width, 12.0, shaded(vertical(sb, sb + 12, argb(0x2A2218, 0.30), argb(0x2A2218, 0.0))))
    }

    fun drawBeltXray(c: Canvas, d: Double, sy: Double) {
        val top = m.BELT_TOP + sy
        val bottom = m.SKIRT_TOP + sy
        val w = m.WIN_R - m.WIN_L
        drawRect(c, m.WIN_L, top, w, bottom - top, solid(argb(CYAN, 0.08)))
        drawRect(c, m.WIN_L, top, w, 1.2, solid(argb(CYAN, 0.65)))
        drawRect(c, m.WIN_L, bottom - 1, w, 1.0, solid(argb(CYAN, 0.4)))
        var x = m.mod(d, m.PITCH) - m.PITCH
        val seam = solid(argb(CYAN, 0.32))
        while (x < m.WIN_R + m.PITCH) { drawRect(c, x, top + 2, 1.0, bottom - top - 3, seam); x += m.PITCH }
    }

    // MARK: Bags

    fun drawBagShadow(c: Canvas, x: Double, sy: Double) {
        val w = imageW * 1.08
        c.save()
        c.translate(x.toFloat(), (m.BELT_Y + sy + 1).toFloat())
        c.scale(1f, 0.12f)
        c.drawCircle(0f, 0f, (w / 2).toFloat(), shaded(RadialGradient(0f, 0f, (w / 2).toFloat(),
            intArrayOf(argb(0x0E0D10, 0.55), argb(0x0E0D10, 0.3), argb(0x0E0D10, 0.0)), floatArrayOf(0f, 0.6f, 1f), Shader.TileMode.CLAMP)))
        c.restore()
    }

    fun drawBag(c: Canvas, bitmap: Bitmap?, x: Double, lean: Double, sy: Double, alpha: Double = 1.0, additive: Boolean = false) {
        bitmap ?: return
        c.save()
        c.translate(x.toFloat(), (m.BELT_Y + sy).toFloat())
        c.rotate(lean.toFloat())
        image.alpha = (m.clamp(alpha) * 255).toInt()
        image.xfermode = if (additive) PorterDuffXfermode(PorterDuff.Mode.ADD) else null
        c.drawBitmap(bitmap, null, r(-imageW / 2, -m.BAG_H, imageW, m.BAG_H), image)
        image.xfermode = null
        image.alpha = 255
        c.restore()
    }

    fun drawBoxes(c: Canvas, x: Double, lean: Double, sy: Double, boxes: DoubleArray, green: Double) {
        c.save()
        c.translate(x.toFloat(), (m.BELT_Y + sy).toFloat())
        c.rotate(lean.toFloat())
        c.translate((-imageW / 2).toFloat(), (-m.BAG_H).toFloat())
        val line = mix(CYAN, BOX_GREEN, green)
        for ((i, item) in items.withIndex()) {
            val s = boxes[2 * i]
            val a = boxes[2 * i + 1]
            if (a <= 0.002) continue
            val bx = item.x0 * imageW
            val by = item.y0 * m.BAG_H
            val bw = (item.x1 - item.x0) * imageW
            val bh = (item.y1 - item.y0) * m.BAG_H
            c.save()
            c.translate((bx + bw / 2).toFloat(), (by + bh / 2).toFloat())
            c.scale(s.toFloat(), s.toFloat())
            val l = -bw / 2
            val t = -bh / 2
            drawRound(c, l, t, bw, bh, 3.0, solid(argb(line, 0.10 * a)))
            stroke.shader = null; stroke.maskFilter = null
            stroke.color = argb(line, 0.35 * a); stroke.strokeWidth = 0.8f
            c.drawRoundRect(r(l, t, bw, bh), 3f, 3f, stroke)
            stroke.color = argb(line, a); stroke.strokeWidth = 1.8f
            c.drawPath(brackets(l, t, bw, bh, 6.0), stroke)

            text.typeface = semibold
            text.textSize = 9.5f
            text.textAlign = Paint.Align.CENTER
            val tw = text.measureText(item.label) + 10.0
            val th = 14.0
            val tx = if (item.right) l + bw - tw else l
            val ty = if (item.below) t + bh + 1 else t - th - 1
            drawRound(c, tx, ty, tw, th, 4.0, solid(argb(line, a)))
            text.color = argb(0x061226, a)
            c.drawText(item.label, (tx + tw / 2).toFloat(), (ty + th / 2 + 3.4).toFloat(), text)
            c.restore()
        }
        c.restore()
    }

    private fun brackets(x: Double, y: Double, w: Double, h: Double, l: Double): Path {
        val p = Path()
        val x1 = x + w
        val y1 = y + h
        fun mv(a: Double, b: Double) = p.moveTo(a.toFloat(), b.toFloat())
        fun ln(a: Double, b: Double) = p.lineTo(a.toFloat(), b.toFloat())
        mv(x, y + l); ln(x, y); ln(x + l, y)
        mv(x1 - l, y); ln(x1, y); ln(x1, y + l)
        mv(x1, y1 - l); ln(x1, y1); ln(x1 - l, y1)
        mv(x + l, y1); ln(x, y1); ln(x, y1 - l)
        return p
    }

    // MARK: Window

    fun windowRect(sy: Double) = RectF(m.WIN_L.toFloat(), (m.WIN_T + sy).toFloat(), m.WIN_R.toFloat(), (m.WIN_B + sy).toFloat())

    fun drawWindowBack(c: Canvas, sy: Double) {
        val w = windowRect(sy)
        c.drawRect(w, shaded(vertical(w.top.toDouble(), w.bottom.toDouble(), 0xFF0E2148.toInt(), 0xFF0A1A38.toInt(), 0xFF061229.toInt())))
        var i = 0
        var x = w.left + 6.0
        while (x < w.right) {
            drawRect(c, x, w.top.toDouble(), 0.7, w.height().toDouble(), solid(argb(CYAN, if (i % 4 == 0) 0.15 else 0.07)))
            x += 14; i++
        }
        i = 0
        var y = w.top + 6.0
        while (y < w.bottom) {
            drawRect(c, w.left.toDouble(), y, w.width().toDouble(), 0.7, solid(argb(CYAN, if (i % 4 == 0) 0.15 else 0.07)))
            y += 14; i++
        }
        stroke.shader = null; stroke.maskFilter = null
        stroke.color = argb(CYAN, 0.45); stroke.strokeWidth = 1.2f
        c.drawPath(brackets(w.left + 6.0, w.top + 6.0, w.width() - 12.0, w.height() - 12.0, 9.0), stroke)
    }

    fun drawWindowFront(c: Canvas, sy: Double, sweepY: Double, sweepAlpha: Double) {
        val w = windowRect(sy)
        val lines = solid(argb(0x000814, 0.16))
        var y = w.top + 1.0
        while (y < w.bottom) { drawRect(c, w.left.toDouble(), y, w.width().toDouble(), 1.0, lines); y += 3 }
        if (sweepAlpha > 0.001) {
            val s = sweepY + sy
            drawRect(c, w.left.toDouble(), s - 26, w.width().toDouble(), 26.0, shaded(vertical(s - 26, s, argb(CYAN, 0.0), argb(CYAN, 0.22 * sweepAlpha))))
            drawRect(c, w.left.toDouble(), s - 0.8, w.width().toDouble(), 1.6, solid(argb(0xD8FBFF, 0.95 * sweepAlpha)))
        }
        val cx = w.centerX().toDouble()
        val cy = w.centerY().toDouble()
        val r0 = w.width() * 0.45f
        val r1 = w.height() * 0.75f
        c.drawRect(w, shaded(RadialGradient(cx.toFloat(), cy.toFloat(), r1, intArrayOf(argb(0, 0.0), argb(0, 0.0), argb(0x000814, 0.45)),
            floatArrayOf(0f, (r0 / r1), 1f), Shader.TileMode.CLAMP)))
        path.reset()
        path.moveTo(w.left + 30, w.top); path.lineTo(w.left + 70, w.top)
        path.lineTo(w.left, w.top + 120); path.lineTo(w.left, w.top + 60); path.close()
        c.drawPath(path, solid(argb(0xFFFFFF, 0.05)))
    }

    fun drawCurtains(c: Canvas, sy: Double, left: Double, right: Double) {
        val w = windowRect(sy)
        for ((x, g, dir) in listOf(Triple(w.left.toDouble(), left, 1.0), Triple(w.right.toDouble(), right, -1.0))) {
            val band = if (dir > 0) x else x - 22
            drawRect(c, band, w.top.toDouble(), 22.0, w.height().toDouble(),
                shaded(horizontal(x, x + 22 * dir, argb(CYAN, 0.22 * (g - 0.35) / 0.65), argb(CYAN, 0.0))))
            val glow = solid(argb(CYAN, 0.55 * g))
            glow.maskFilter = BlurMaskFilter(5f, BlurMaskFilter.Blur.NORMAL)
            drawRect(c, x - 4, w.top.toDouble(), 8.0, w.height().toDouble(), glow)
            fill.maskFilter = null
            drawRect(c, x - 1, w.top.toDouble(), 2.0, w.height().toDouble(), shaded(vertical(w.top.toDouble(), w.bottom.toDouble(),
                argb(0xCFF8FF, 0.25 + 0.4 * g), argb(0xCFF8FF, 0.5 + 0.5 * g), argb(0xCFF8FF, 0.25 + 0.4 * g))))
        }
    }

    // MARK: Gantry

    fun drawGantry(c: Canvas, sy: Double, beacon: Double, scanning: Double, cleared: Double, t: Double) {
        val w = windowRect(sy)
        val beamTop = 292 + sy
        val shadow = solid(argb(0x1E2B1C, 0.30))
        shadow.maskFilter = BlurMaskFilter(8f, BlurMaskFilter.Blur.NORMAL)
        drawRound(c, 108.0, beamTop + 6, 186.0, 40.0, 10.0, shadow)
        shadow.color = argb(0x1E2B1C, 0.22)
        drawRect(c, 116.0, beamTop + 40, 14.0, 250.0, shadow)
        drawRect(c, 270.0, beamTop + 40, 14.0, 250.0, shadow)
        fill.maskFilter = null

        for (px in doubleArrayOf(112.5, 266.5)) {
            drawRound(c, px, beamTop + 20, 14.0, 586.0 - 292 - 20, 3.0,
                shaded(horizontal(px, px + 14, 0xFFA4A9B1.toInt(), 0xFFDCDFE3.toInt(), 0xFFC2C6CC.toInt(), 0xFF989DA6.toInt())))
            drawRect(c, px + 2, m.SKIRT_TOP + sy + 6, 10.0, 3.0, solid(argb(0xE9883A)))
        }
        stroke.shader = null; stroke.maskFilter = null
        stroke.color = argb(0x7F858E); stroke.strokeWidth = 3f
        c.drawRoundRect(RectF(w.left - 1.5f, w.top - 1.5f, w.right + 1.5f, w.bottom + 1.5f), 3f, 3f, stroke)
        stroke.color = argb(0xFFFFFF, 0.25); stroke.strokeWidth = 0.8f
        c.drawRoundRect(RectF(w.left - 3.2f, w.top - 3.2f, w.right + 3.2f, w.bottom + 3.2f), 4f, 4f, stroke)

        drawRound(c, 104.5, beamTop, 184.0, 40.0, 10.0, shaded(vertical(beamTop, beamTop + 40, 0xFFDDE0E4.toInt(), 0xFFC5C9CF.toInt(), 0xFFA6ABB3.toInt())))
        drawRound(c, 104.5, beamTop + 35, 184.0, 5.0, 2.5, solid(argb(0xE9883A)))
        stroke.color = argb(0xFFFFFF, 0.45); stroke.strokeWidth = 1f
        c.drawRoundRect(r(105.0, beamTop + 0.5, 183.0, 39.0), 10f, 10f, stroke)
        drawRound(c, 150.0, beamTop + 8, 93.0, 20.0, 6.0, solid(argb(0x1D2230)))
        for (i in 0 until 3) {
            val cx = 176.5 + 20 * i
            val cy = beamTop + 18
            val chase = 0.5 + 0.5 * sin(2 * PI * (6 * t / m.LOOP - i / 3.0))
            val on = max(scanning * (0.35 + 0.65 * chase), cleared)
            val hue = mix(0xFFB23E, 0x5BE38A, cleared)
            val glow = solid(argb(hue, 0.7 * on))
            glow.maskFilter = BlurMaskFilter(3f, BlurMaskFilter.Blur.NORMAL)
            c.drawCircle(cx.toFloat(), cy.toFloat(), 6f, glow)
            fill.maskFilter = null
            c.drawCircle(cx.toFloat(), cy.toFloat(), 3.5f, solid(argb(0x3A4152)))
            c.drawCircle(cx.toFloat(), cy.toFloat(), 3.5f, solid(argb(hue, on)))
        }
        drawRound(c, 118.0, beamTop + 14, 22.0, 8.0, 2.0, solid(argb(0xE9883A)))
        drawRound(c, 253.0, beamTop + 14, 22.0, 8.0, 2.0, solid(argb(0xE9883A)))
        drawBeacon(c, sy, beacon)
    }

    private fun drawBeacon(c: Canvas, sy: Double, glow: Double) {
        val cx = 196.5
        val base = 292 + sy
        if (glow > 0.001) {
            c.drawCircle(cx.toFloat(), (base - 12).toFloat(), 46f, shaded(radial(cx, base - 12, 46.0, argb(0xFFC25A, 0.55 * glow), argb(0xFFB23E, 0.0))))
        }
        drawRound(c, cx - 12, base - 7, 24.0, 8.0, 2.0, solid(argb(0x585D66)))
        val dome = domePath(cx - 8.5, base - 27, 17.0, 21.0)
        c.drawPath(dome, shaded(vertical(base - 27, base - 6, 0xFF000000.toInt() or mix(0xE59A4C, 0xFFF0B8, glow), 0xFF000000.toInt() or mix(0xC8682A, 0xFFB23E, glow))))
        stroke.shader = null; stroke.maskFilter = null
        stroke.color = argb(0x4A4F58); stroke.strokeWidth = 1.3f
        path.reset()
        for (dx in doubleArrayOf(-4.5, 0.0, 4.5)) {
            path.moveTo((cx + dx).toFloat(), (base - 27 + if (dx == 0.0) 0.0 else 2.0).toFloat())
            path.lineTo((cx + dx).toFloat(), (base - 6).toFloat())
        }
        path.moveTo((cx - 8.5).toFloat(), (base - 18).toFloat()); path.lineTo((cx + 8.5).toFloat(), (base - 18).toFloat())
        c.drawPath(path, stroke)
        c.drawPath(dome, stroke)
    }

    private fun domePath(x: Double, y: Double, w: Double, h: Double): Path {
        val p = Path()
        val rr = w / 2
        p.addRoundRect(r(x, y, w, h), floatArrayOf(rr.toFloat(), rr.toFloat(), rr.toFloat(), rr.toFloat(), 1f, 1f, 1f, 1f), Path.Direction.CW)
        return p
    }

    // MARK: Small icons (drawn in their own 46 / 18 / 16 unit boxes)

    fun drawStatusIcon(c: Canvas, stage: Double, spin: Double, checkScale: Double, checkStroke: Double) {
        val s = m.mod(stage, 4.0)
        val ready = m.clamp(max(1 - s, s - 3))
        val spinner = m.clamp(min(s, 3 - s))
        c.drawCircle(23f, 23f, 23f, solid(argb(ACCENT, 1 - m.clamp(checkScale))))
        if (ready > 0.001) drawSuitcaseGlyph(c, 23.0, 23.0, ready)
        if (spinner > 0.001) {
            stroke.shader = null; stroke.maskFilter = null
            stroke.strokeWidth = 3f
            stroke.color = argb(0xFFF3E3, 0.3 * spinner)
            c.drawCircle(23f, 23f, 11f, stroke)
            stroke.color = argb(0xFFF3E3, spinner)
            c.drawArc(r(12.0, 12.0, 22.0, 22.0), Math.toDegrees(spin).toFloat(), Math.toDegrees(1.9).toFloat(), false, stroke)
        }
        if (checkScale > 0.001) {
            c.save()
            c.translate(23f, 23f)
            c.scale(checkScale.toFloat(), checkScale.toFloat())
            c.drawCircle(0f, 0f, 23f, solid(argb(GREEN)))
            val tick = Path()
            tick.moveTo(-9f, 0.5f); tick.lineTo(-3f, 6.5f); tick.lineTo(9.5f, -6f)
            val measure = PathMeasure(tick, false)
            var total = 0f
            do { total += measure.length } while (measure.nextContour())
            val part = Path()
            val pm = PathMeasure(tick, false)
            pm.getSegment(0f, (total * checkStroke).toFloat(), part, true)
            stroke.color = argb(0xFFFFFF); stroke.strokeWidth = 3.4f
            c.drawPath(part, stroke)
            c.restore()
        }
    }

    private fun drawSuitcaseGlyph(c: Canvas, cx: Double, cy: Double, alpha: Double) {
        stroke.shader = null; stroke.maskFilter = null
        stroke.color = argb(0xFFF3E3, alpha); stroke.strokeWidth = 2.2f
        c.drawRoundRect(r(cx - 9, cy - 6, 18.0, 15.0), 3.5f, 3.5f, stroke)
        path.reset()
        path.moveTo((cx - 4).toFloat(), (cy - 6).toFloat()); path.lineTo((cx - 4).toFloat(), (cy - 10).toFloat())
        path.lineTo((cx + 4).toFloat(), (cy - 10).toFloat()); path.lineTo((cx + 4).toFloat(), (cy - 6).toFloat())
        c.drawPath(path, stroke)
        stroke.strokeWidth = 1.6f
        path.reset()
        path.moveTo((cx - 3).toFloat(), (cy - 2).toFloat()); path.lineTo((cx - 3).toFloat(), (cy + 5).toFloat())
        path.moveTo((cx + 3).toFloat(), (cy - 2).toFloat()); path.lineTo((cx + 3).toFloat(), (cy + 5).toFloat())
        c.drawPath(path, stroke)
    }

    fun drawBar(c: Canvas, width: Double, fillAmount: Double, done: Double) {
        val gap = 4.0
        val seg = (width - 2 * gap) / 3
        for (i in 0 until 3) {
            val x = i * (seg + gap)
            drawRound(c, x, 0.0, seg, 8.0, 4.0, solid(argb(TRACK)))
            val f = m.clamp((fillAmount - i / 3.0) * 3)
            if (f > 0.001) {
                c.save()
                path.reset()
                path.addRoundRect(r(x, 0.0, seg, 8.0), 4f, 4f, Path.Direction.CW)
                c.clipPath(path)
                drawRect(c, x, 0.0, seg * f, 8.0, solid(argb(mix(ACCENT, GREEN, done))))
                c.restore()
            }
        }
    }

    fun drawAgainGlyph(c: Canvas, tint: Int) {
        stroke.shader = null; stroke.maskFilter = null
        stroke.color = tint; stroke.strokeWidth = 2.2f
        c.drawArc(r(2.5, 2.5, 13.0, 13.0), -60f, 290f, false, stroke)
        val tx = 9 + 6.5 * cos(-PI / 3)
        val ty = 9 + 6.5 * sin(-PI / 3)
        path.reset()
        path.moveTo((tx - 5).toFloat(), (ty - 1.2).toFloat()); path.lineTo(tx.toFloat(), ty.toFloat())
        path.lineTo((tx - 0.6).toFloat(), (ty + 5).toFloat())
        c.drawPath(path, stroke)
    }

    fun drawBird(c: Canvas) {
        path.reset()
        path.moveTo(1f, 9f)
        path.quadTo(4f, 4f, 8f, 7f)
        path.quadTo(11f, 2f, 15f, 3f)
        path.quadTo(13f, 9f, 9f, 11f)
        path.quadTo(5f, 12f, 1f, 9f)
        path.close()
        c.drawPath(path, solid(argb(ACCENT)))
    }
}

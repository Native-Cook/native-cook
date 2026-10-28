package com.ngocdevv.nativecook.blocks.lists.newspaperpilereader

import android.content.res.AssetManager
import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.os.Build
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * The six folded papers and the shadow sprites, drawn once into bitmaps (CRAFT §7): a frame only moves
 * images around. A port of NewspaperPileReaderArt.swift; every coordinate is in dp of the 340 × 228 sheet.
 */
internal class NprImages(
    val papers: List<Bitmap>,
    val contact: Bitmap,
    val ambient: Bitmap,
    val toggleShadow: Bitmap,
)

internal class NprFonts(assets: AssetManager) {
    val black: Typeface = Typeface.createFromAsset(assets, "newspaperpilereader/newspaperpilereader_UnifrakturMaguntia-Book.ttf")
    val serif: Typeface = Typeface.createFromAsset(assets, "newspaperpilereader/newspaperpilereader_DMSerifDisplay-Regular.ttf")
}

internal object NprArt {
    private val M = NprModel

    /** The paper image is the 340 × 228 sheet with 6 dp around it for the page edges. */
    const val PAD = 6.0
    const val CONTACT_PAD = 10.0
    const val AMBIENT_PAD = 60.0
    const val TOGGLE_PAD = 24.0

    const val INK = 0x1C1B19L
    const val RULE = 0x34322DL
    const val LINES = 0x9E9A8FL
    const val RED = 0xC23A33L
    const val BLUE = 0x2C5BA6L
    const val NAVY = 0x213A66L

    /** SwiftUI's blur radius is a Gaussian sigma; BlurMaskFilter wants a radius (sigma ≈ 0.577 r + 0.5). */
    private fun blurRadius(sigmaPx: Float) = max(0.5f, (sigmaPx - 0.5f) / 0.57735f)

    fun argb(hex: Long, a: Double = 1.0): Int {
        val al = (min(max(a, 0.0), 1.0) * 255 + 0.5).toInt()
        return (al shl 24) or (hex and 0xFFFFFF).toInt()
    }

    private fun darker(hex: Long, k: Double): Int {
        fun ch(s: Int) = (((hex shr s) and 0xFF) * (1 - k) + 0.5).toInt()
        return (0xFF shl 24) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
    }

    private const val WHITE = 0xFFFFFFL
    private const val BLACK = 0x000000L

    fun make(fonts: NprFonts, density: Float): NprImages {
        val papers = M.PAPERS.indices.map { i ->
            render(M.PAPER_W + 2 * PAD, M.PAPER_H + 2 * PAD, density) { c ->
                c.translate(PAD.toFloat(), PAD.toFloat())
                paper(c, i, fonts)
            }
        }
        val contact = render(M.PAPER_W + 2 * CONTACT_PAD, M.PAPER_H + 2 * CONTACT_PAD, density) { c ->
            val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = argb(BLACK)
                maskFilter = BlurMaskFilter(blurRadius(3f * density) / density, BlurMaskFilter.Blur.NORMAL)
            }
            c.drawRoundRect(rf(CONTACT_PAD, CONTACT_PAD, M.PAPER_W, M.PAPER_H), 3f, 3f, p)
        }
        val ambientScale = min(density, 1.5f)
        val ambient = render(M.PAPER_W + 2 * AMBIENT_PAD, M.PAPER_H + 2 * AMBIENT_PAD, ambientScale) { c ->
            val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = argb(BLACK)
                maskFilter = BlurMaskFilter(blurRadius(20f * ambientScale) / ambientScale, BlurMaskFilter.Blur.NORMAL)
            }
            c.drawRoundRect(rf(AMBIENT_PAD, AMBIENT_PAD, M.PAPER_W, M.PAPER_H), 8f, 8f, p)
        }
        val toggle = render(108 + 2 * TOGGLE_PAD, 56 + 2 * TOGGLE_PAD, density) { c ->
            val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = argb(BLACK)
                maskFilter = BlurMaskFilter(blurRadius(9f * density) / density, BlurMaskFilter.Blur.NORMAL)
            }
            c.drawRoundRect(rf(TOGGLE_PAD, TOGGLE_PAD, 108.0, 56.0), 28f, 28f, p)
        }
        return NprImages(papers, contact, ambient, toggle)
    }

    /** A software bitmap (BlurMaskFilter needs one) drawn in dp at [scale] px per dp. */
    private fun render(width: Double, height: Double, scale: Float, draw: (Canvas) -> Unit): Bitmap {
        val bmp = Bitmap.createBitmap(ceil(width * scale).toInt(), ceil(height * scale).toInt(), Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.scale(scale, scale)
        draw(c)
        return bmp
    }

    private fun rf(x: Double, y: Double, w: Double, h: Double) = RectF(x.toFloat(), y.toFloat(), (x + w).toFloat(), (y + h).toFloat())

    private fun fill(color: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }

    private fun rect(c: Canvas, x: Double, y: Double, w: Double, h: Double, color: Int) =
        c.drawRect(rf(x, y, w, h), fill(color))

    private fun vgradPaint(y0: Double, y1: Double, colors: IntArray, stops: FloatArray? = null) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF000000.toInt()
        shader = LinearGradient(0f, y0.toFloat(), 0f, y1.toFloat(), colors, stops, Shader.TileMode.CLAMP)
    }

    // Text helpers.

    private fun weighted(weight: Int, italic: Boolean = false): Typeface =
        if (Build.VERSION.SDK_INT >= 28) {
            Typeface.create(Typeface.DEFAULT, weight, italic)
        } else {
            val family = when {
                weight >= 800 -> "sans-serif-black"
                weight >= 500 -> "sans-serif-medium"
                else -> "sans-serif"
            }
            Typeface.create(family, if (italic) Typeface.ITALIC else Typeface.NORMAL)
        }

    private fun pen(tf: Typeface, size: Double, color: Int, tracking: Double = 0.0) = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = tf
        textSize = size.toFloat()
        this.color = color
        letterSpacing = (tracking / size).toFloat()
        isSubpixelText = true
    }

    /** One line centred vertically on y: starting at x (anchor −1), centred on it (0) or ending at it (1). */
    private fun line(c: Canvas, s: String, p: Paint, x: Double, y: Double, anchor: Int) {
        val w = p.measureText(s)
        val fm = p.fontMetrics
        val base = y.toFloat() - (fm.ascent + fm.descent) / 2
        val left = when (anchor) {
            -1 -> x.toFloat()
            0 -> x.toFloat() - w / 2
            else -> x.toFloat() - w
        }
        c.drawText(s, left, base, p)
    }

    /** Draws one line, shrunk to fit maxWidth if needed. */
    private fun fit(c: Canvas, s: String, p: Paint, x: Double, y: Double, anchor: Int, maxWidth: Double) {
        val w = p.measureText(s)
        if (w <= maxWidth) {
            line(c, s, p, x, y, anchor)
            return
        }
        val k = (maxWidth / w).toFloat()
        c.save()
        c.translate(x.toFloat(), y.toFloat())
        c.scale(k, k)
        line(c, s, p, 0.0, 0.0, anchor)
        c.restore()
    }

    /** A wrapped serif headline from the top-left of the rect (up to three lines). */
    private fun headline(c: Canvas, f: NprFonts, s: String, x: Double, y: Double, w: Double, size: Double, col: Long = INK) {
        val tp = pen(f.serif, size, argb(col))
        val layout = StaticLayout.Builder.obtain(s, 0, s.length, tp, w.toInt())
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setIncludePad(false)
            .setMaxLines(3)
            .build()
        c.save()
        c.translate(x.toFloat(), y.toFloat())
        layout.draw(c)
        c.restore()
    }

    // Paper.

    private fun paper(c: Canvas, i: Int, f: NprFonts) {
        val p = M.PAPERS[i]
        val w = M.PAPER_W
        val h = M.PAPER_H
        // Page edges of the folded paper behind the front page.
        rect(c, 3.5, 4.0, w, h, argb(0xCCC7BB))
        rect(c, 2.0, 2.0, w, h, argb(0xDAD6CB))
        c.drawRect(rf(0.0, 0.0, w, h), vgradPaint(0.0, h, intArrayOf(argb(p.tone), darker(p.tone, 0.045))))
        grain(c, i * 101.0 + 7)

        c.save()
        c.clipRect(rf(0.0, 0.0, w, h))
        when (p.style) {
            0 -> tabloid(c, p, i, f)
            1 -> quayside(c, p, i, f)
            2 -> larkmoor(c, p, i, f)
            3 -> fernhill(c, p, i, f)
            4 -> saltday(c, p, i, f)
            else -> orrin(c, p, i, f)
        }
        c.restore()

        // Fold: the bottom edge is the fold, rounded and a little darker, with a light lip.
        c.drawRect(rf(0.0, h - 20, w, 20.0), vgradPaint(h - 20, h, intArrayOf(argb(BLACK, 0.0), argb(BLACK, 0.09))))
        rect(c, 0.0, h - 1.2, w, 0.8, argb(WHITE, 0.55))
        // Left edge thickness.
        c.drawRect(rf(0.0, 0.0, 6.0, h), Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(0f, 0f, 6f, 0f, argb(BLACK, 0.05), argb(BLACK, 0.0), Shader.TileMode.CLAMP)
        })
        // Curl highlight on the top-right corner.
        val tri = Path().apply {
            moveTo((w - 46).toFloat(), 0f)
            lineTo(w.toFloat(), 0f)
            lineTo(w.toFloat(), 46f)
            close()
        }
        c.drawPath(tri, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                w.toFloat(), 0f, (w - 23).toFloat(), 23f,
                intArrayOf(argb(WHITE, 0.0), argb(WHITE, 0.5), argb(BLACK, 0.05), argb(BLACK, 0.0)),
                floatArrayOf(0f, 0.45f, 0.85f, 1f), Shader.TileMode.CLAMP,
            )
        })
        c.drawRect(rf(0.25, 0.25, w - 0.5, h - 0.5), Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 0.5f
            color = argb(BLACK, 0.06)
        })
    }

    private fun grain(c: Canvas, seed: Double) {
        val dark = Path()
        for (k in 0 until 900) {
            val x = M.hash(seed + k * 1.31) * M.PAPER_W
            val y = M.hash(seed + k * 2.17 + 0.5) * M.PAPER_H
            val r = 0.35 + 0.3 * M.hash(seed + k * 0.77)
            dark.addOval(rf(x - r, y - r, 2 * r, 2 * r), Path.Direction.CW)
        }
        c.drawPath(dark, fill(argb(BLACK, 0.06)))
        val light = Path()
        for (k in 0 until 300) {
            val x = M.hash(seed + k * 3.11 + 9) * M.PAPER_W
            val y = M.hash(seed + k * 1.93 + 4) * M.PAPER_H
            light.addOval(rf(x - 0.5, y - 0.5, 1.0, 1.0), Path.Direction.CW)
        }
        c.drawPath(light, fill(argb(WHITE, 0.4)))
    }

    /** Rows of grey "text", with a short last line and a gap at each paragraph end. */
    private fun textLines(c: Canvas, x: Double, y0: Double, w: Double, h: Double, seed: Double, tint: Long = LINES, alpha: Double = 0.6) {
        val path = Path()
        var y = y0 + 1
        var k = 0.0
        while (y + 1.6 <= y0 + h) {
            val hh = M.hash(seed + k)
            val end = M.hash(seed * 1.7 + k * 3.1) < 0.12
            val len = if (end) w * (0.3 + 0.4 * hh) else w * (0.86 + 0.14 * hh)
            path.addRoundRect(rf(x, y, len, 1.6), 0.6f, 0.6f, Path.Direction.CW)
            y += if (end) 8 else 5
            k += 1
        }
        c.drawPath(path, fill(argb(tint, alpha)))
    }

    private fun columns(c: Canvas, x0: Double, y0: Double, w: Double, h: Double, n: Int, seed: Double) {
        val gut = 8.0
        val cw = (w - gut * (n - 1)) / n
        for (j in 0 until n) {
            val x = x0 + j * (cw + gut)
            textLines(c, x, y0, cw, h, seed + j * 31)
            if (j > 0) rect(c, x - gut / 2 - 0.25, y0, 0.5, h, argb(RULE, 0.35))
        }
    }

    private fun hrule(c: Canvas, y: Double, x0: Double = 12.0, x1: Double = 328.0, wid: Double = 0.5, col: Long = RULE) =
        rect(c, x0, y - wid / 2, x1 - x0, wid, argb(col))

    private fun dateline(c: Canvas, left: String, right: String) {
        val p = pen(weighted(600), 5.5, argb(INK, 0.75), 0.4)
        line(c, left, p, 12.0, 8.0, -1)
        line(c, right, p, 328.0, 8.0, 1)
    }

    private fun infoRow(c: Canvas, y: Double, s: String) =
        line(c, s, pen(weighted(500), 5.0, argb(INK, 0.7), 0.5), 170.0, y, 0)

    // Styles.

    private fun tabloid(c: Canvas, p: NprPaper, i: Int, f: NprFonts) {
        fit(c, p.masthead, pen(f.black, 31.0, argb(INK)), 12.0, 27.0, -1, 236.0)
        rect(c, 12.0, 46.0, 236.0, 15.0, argb(RED))
        line(c, "WEEKEND PULLOUT · 24 PAGES", pen(weighted(700), 7.0, argb(WHITE), 0.9), 130.0, 53.5, 0)
        photo(c, 0, 256.0, 10.0, 72.0, 98.0)
        fit(c, p.headline, pen(f.serif, 34.0, argb(INK)), 12.0, 86.0, -1, 236.0)
        line(c, p.deck, pen(f.serif, 12.5, argb(RED)), 12.0, 114.0, -1)
        hrule(c, 126.0)
        columns(c, 12.0, 132.0, 316.0, 74.0, 4, 11.0)
    }

    private fun quayside(c: Canvas, p: NprPaper, i: Int, f: NprFonts) {
        dateline(c, "TUESDAY, JUNE 4", "$3.00")
        fit(c, p.masthead, pen(f.serif, 25.0, argb(INK), -0.2), 170.0, 30.0, 0, 312.0)
        hrule(c, 49.5, 12.0, 328.0, 1.2)
        hrule(c, 52.0)
        infoRow(c, 57.0, "VOL. CXII  ·  NO. 108  ·  LATE EDITION  ·  ★★★★")
        hrule(c, 62.0)
        // "What's Inside" box.
        c.drawRect(rf(12.0, 68.0, 52.0, 56.0), Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 0.6f
            color = argb(BLUE, 0.8)
        })
        line(c, "What's", pen(f.serif, 9.0, argb(BLUE)), 16.0, 76.0, -1)
        line(c, "Inside", pen(f.serif, 9.0, argb(BLUE)), 16.0, 86.0, -1)
        textLines(c, 16.0, 94.0, 44.0, 28.0, 3.0, BLUE, 0.45)
        textLines(c, 12.0, 130.0, 52.0, 76.0, 5.0)
        rect(c, 67.75, 68.0, 0.5, 138.0, argb(RULE, 0.35))
        headline(c, f, p.headline, 72.0, 64.0, 186.0, 17.0)
        columns(c, 72.0, 112.0, 186.0, 94.0, 3, 17.0)
        rect(c, 261.75, 68.0, 0.5, 138.0, argb(RULE, 0.35))
        photo(c, 1, 266.0, 68.0, 62.0, 56.0)
        headline(c, f, p.deck, 266.0, 127.0, 62.0, 10.5)
        textLines(c, 266.0, 158.0, 62.0, 48.0, 23.0)
    }

    private fun larkmoor(c: Canvas, p: NprPaper, i: Int, f: NprFonts) {
        dateline(c, "TUESDAY, JUNE 4", "TWO DOLLARS")
        fit(c, p.masthead, pen(f.black, 30.0, argb(INK)), 170.0, 29.0, 0, 312.0)
        hrule(c, 49.5, 12.0, 328.0, 1.2)
        hrule(c, 52.0)
        infoRow(c, 57.0, "LARKMOOR AND THE VALLEY  ·  SINCE 1874  ·  WEATHER: MILD, 18°")
        hrule(c, 62.0)
        headline(c, f, p.headline, 12.0, 64.0, 194.0, 17.0)
        columns(c, 12.0, 112.0, 194.0, 94.0, 3, 29.0)
        photo(c, 2, 214.0, 68.0, 114.0, 82.0)
        textLines(c, 214.0, 155.0, 114.0, 51.0, 37.0)
    }

    private fun fernhill(c: Canvas, p: NprPaper, i: Int, f: NprFonts) {
        dateline(c, "TUESDAY, JUNE 4", "FINAL")
        fit(c, p.masthead, pen(f.black, 31.0, argb(INK)), 170.0, 29.0, 0, 312.0)
        hrule(c, 49.5, 12.0, 328.0, 1.2)
        hrule(c, 52.0)
        infoRow(c, 57.0, "AN INDEPENDENT NEWSPAPER  ·  FERNHILL, TUESDAY  ·  $2.75")
        hrule(c, 62.0)
        photo(c, 3, 12.0, 68.0, 120.0, 82.0)
        textLines(c, 12.0, 155.0, 120.0, 51.0, 41.0)
        headline(c, f, p.headline, 140.0, 64.0, 188.0, 17.0)
        columns(c, 140.0, 112.0, 188.0, 94.0, 3, 43.0)
    }

    private fun saltday(c: Canvas, p: NprPaper, i: Int, f: NprFonts) {
        hrule(c, 12.0, 12.0, 328.0, 2.0, RED)
        fit(c, p.masthead, pen(weighted(900, italic = true), 32.0, argb(NAVY), -0.6), 170.0, 31.0, 0, 312.0)
        hrule(c, 50.0, 12.0, 328.0, 2.0, RED)
        infoRow(c, 57.0, "TUESDAY, JUNE 4  ·  NORTH SHORE EDITION  ·  $2.00")
        photo(c, 4, 12.0, 66.0, 164.0, 84.0)
        columns(c, 12.0, 156.0, 164.0, 50.0, 2, 47.0)
        headline(c, f, p.headline, 184.0, 63.0, 144.0, 16.0, NAVY)
        columns(c, 184.0, 112.0, 144.0, 94.0, 2, 53.0)
    }

    private fun orrin(c: Canvas, p: NprPaper, i: Int, f: NprFonts) {
        dateline(c, "TUESDAY, JUNE 4", "$2.50")
        fit(c, p.masthead, pen(f.serif, 28.0, argb(INK)), 170.0, 30.0, 0, 312.0)
        hrule(c, 49.5, 12.0, 328.0, 1.2)
        hrule(c, 52.0)
        infoRow(c, 57.0, "THE BAY'S OWN PAPER  ·  SPRING GARDENS ISSUE")
        hrule(c, 62.0)
        fit(c, p.headline, pen(f.serif, 19.0, argb(INK)), 170.0, 78.0, 0, 312.0)
        photo(c, 5, 12.0, 94.0, 188.0, 78.0)
        textLines(c, 12.0, 177.0, 188.0, 29.0, 59.0)
        columns(c, 208.0, 94.0, 120.0, 112.0, 2, 61.0)
    }

    // Photos (drawn in code, then printed: halftone, paper tint, hairline).

    private class R(val x: Double, val y: Double, val w: Double, val h: Double) {
        val maxX get() = x + w
        val maxY get() = y + h
        val midX get() = x + w / 2
    }

    private fun photo(c: Canvas, kind: Int, x: Double, y: Double, w: Double, h: Double) {
        val r = R(x, y, w, h)
        c.save()
        c.clipRect(rf(x, y, w, h))
        when (kind) {
            0 -> portrait(c, r)
            1 -> harbour(c, r)
            2 -> market(c, r)
            3 -> train(c, r)
            4 -> sunset(c, r)
            else -> garden(c, r)
        }
        val dots = Path()
        val pitch = 2.4
        var row = 0
        var yy = r.y + 0.6
        while (yy < r.maxY) {
            var xx = r.x + if (row % 2 == 0) 0.6 else 1.8
            while (xx < r.maxX) {
                dots.addOval(rf(xx - 0.42, yy - 0.42, 0.84, 0.84), Path.Direction.CW)
                xx += pitch
            }
            yy += pitch
            row += 1
        }
        c.drawPath(dots, fill(argb(BLACK, 0.12)))
        rect(c, x, y, w, h, argb(0xF1EEE5, 0.12))
        c.restore()
        c.drawRect(rf(x, y, w, h), Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 0.5f
            color = argb(RULE, 0.3)
        })
    }

    private fun vgrad(c: Canvas, x: Double, y: Double, w: Double, h: Double, cols: LongArray) =
        c.drawRect(rf(x, y, w, h), vgradPaint(y, y + h, IntArray(cols.size) { argb(cols[it]) }))

    private fun oval(c: Canvas, x: Double, y: Double, w: Double, h: Double, color: Int) = c.drawOval(rf(x, y, w, h), fill(color))

    private fun portrait(c: Canvas, r: R) {
        vgrad(c, r.x, r.y, r.w, r.h, longArrayOf(0xD9A273, 0xC8704F, 0xA9483A))
        val cx = r.midX
        oval(c, cx - r.w * 0.55, r.y + r.h * 0.68, r.w * 1.1, r.h * 0.6, argb(0x24324F))
        val collar = Path().apply {
            moveTo((cx - 9).toFloat(), (r.y + r.h * 0.69).toFloat())
            lineTo((cx + 9).toFloat(), (r.y + r.h * 0.69).toFloat())
            lineTo(cx.toFloat(), (r.y + r.h * 0.82).toFloat())
            close()
        }
        c.drawPath(collar, fill(argb(0xF2EFE8)))
        rect(c, cx - 6, r.y + r.h * 0.55, 12.0, r.h * 0.15, argb(0xD9A88C))
        val hx = cx - 15
        val hy = r.y + r.h * 0.24
        oval(c, hx, hy, 30.0, 37.0, argb(0xE6BCA0))
        val hair = Path().apply {
            arcTo(rf(cx - 16.5, hy + 14 - 16.5, 33.0, 33.0), 195f, 150f, true)
            close()
        }
        c.drawPath(hair, fill(argb(0xE9E4DA)))
        oval(c, hx + 1, hy + 4, 28.0, 12.0, argb(0xEDE8DF))
    }

    private fun harbour(c: Canvas, r: R) {
        vgrad(c, r.x, r.y, r.w, r.h, longArrayOf(0xC4CDD2, 0xDDDCD5))
        val seaY = r.y + r.h * 0.62
        vgrad(c, r.x, seaY, r.w, r.h * 0.38, longArrayOf(0x6E8591, 0x4D6270))
        val fx = r.x + r.w * 0.22
        val fy = seaY
        val hull = Path().apply {
            moveTo(fx.toFloat(), (fy - 4).toFloat())
            lineTo((fx + 36).toFloat(), (fy - 4).toFloat())
            lineTo((fx + 31).toFloat(), (fy + 4).toFloat())
            lineTo((fx + 4).toFloat(), (fy + 4).toFloat())
            close()
        }
        c.drawPath(hull, fill(argb(0x2D3740)))
        rect(c, fx + 7, fy - 11, 22.0, 7.0, argb(0xF2F0EA))
        for (k in 0 until 4) rect(c, fx + 9 + k * 5, fy - 9, 3.0, 2.5, argb(0x3E4B57))
        rect(c, fx + 4, fy + 5, 30.0, 1.0, argb(WHITE, 0.35))
        rect(c, r.x, seaY - 3, r.w, 3.0, argb(0x8E9A9E))
    }

    private fun market(c: Canvas, r: R) {
        vgrad(c, r.x, r.y, r.w, r.h, longArrayOf(0x1F2A44, 0x3B3552, 0x5A3F4F))
        val y0 = r.y + 12
        val wire = Path().apply {
            moveTo(r.x.toFloat(), y0.toFloat())
            quadTo(r.midX.toFloat(), (y0 + 26).toFloat(), r.maxX.toFloat(), y0.toFloat())
        }
        c.drawPath(wire, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 0.6f
            color = argb(0x111522, 0.8)
        })
        for (k in 0 until 11) {
            val t = (k + 0.5) / 11
            val x = r.x + r.w * t
            val y = (1 - t) * (1 - t) * y0 + 2 * (1 - t) * t * (y0 + 26) + t * t * y0
            oval(c, x - 4, y - 3, 8.0, 8.0, argb(0xF4C25B, 0.25))
            oval(c, x - 1.6, y - 0.6, 3.2, 3.2, argb(0xFFE3A1))
        }
        val cols = longArrayOf(0xC23A33, 0x2F8F86, 0xE2A23B)
        for (k in 0 until 3) {
            val x = r.x + 6 + k * (r.w - 12) / 3
            val sw = (r.w - 12) / 3 - 4
            val top = r.y + r.h * 0.5
            val roof = Path().apply {
                moveTo((x - 2).toFloat(), (top + 10).toFloat())
                lineTo((x + sw / 2).toFloat(), top.toFloat())
                lineTo((x + sw + 2).toFloat(), (top + 10).toFloat())
                close()
            }
            c.drawPath(roof, fill(argb(cols[k])))
            rect(c, x, top + 10, sw, r.maxY - top - 10, argb(0x2A2233))
            rect(c, x + 3, top + 14, sw - 6, 9.0, argb(0xF4C25B, 0.75))
        }
        for (k in 0 until 5) {
            val x = r.x + 10 + k * 23
            oval(c, x, r.maxY - 18, 6.0, 6.0, argb(0x14111A))
            c.drawRoundRect(rf(x - 2, r.maxY - 12, 10.0, 14.0), 4f, 4f, fill(argb(0x14111A)))
        }
    }

    private fun train(c: Canvas, r: R) {
        vgrad(c, r.x, r.y, r.w, r.h, longArrayOf(0xB9B8B3, 0xE2E1DC))
        val horizon = r.y + r.h * 0.58
        vgrad(c, r.x, horizon, r.w, r.maxY - horizon, longArrayOf(0x76746E, 0x4E4D49))
        val hill = Path().apply {
            moveTo(r.x.toFloat(), horizon.toFloat())
            quadTo((r.x + r.w * 0.3).toFloat(), (horizon - 22).toFloat(), r.maxX.toFloat(), horizon.toFloat())
        }
        c.drawPath(hill, fill(argb(0x8F8D87)))
        val vx = r.x + r.w * 0.62
        val railPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 1f
            color = argb(0xD7D5CF)
        }
        for (s in doubleArrayOf(-1.0, 1.0)) {
            c.drawLine((vx + s * 6).toFloat(), (horizon + 4).toFloat(), (vx + s * 46).toFloat(), r.maxY.toFloat(), railPaint)
        }
        val tx = vx - 16
        val ty = horizon - 26
        c.drawRoundRect(rf(tx, ty, 32.0, 32.0), 5f, 5f, fill(argb(0x3A3936)))
        c.drawRoundRect(rf(tx + 4, ty + 5, 24.0, 10.0), 2f, 2f, fill(argb(0xC9C8C2)))
        oval(c, tx + 5, ty + 32 - 9, 5.0, 5.0, argb(0xF5F3EC))
        oval(c, tx + 32 - 10, ty + 32 - 9, 5.0, 5.0, argb(0xF5F3EC))
    }

    private fun sunset(c: Canvas, r: R) {
        vgrad(c, r.x, r.y, r.w, r.h, longArrayOf(0x8E5A8C, 0xE9785A, 0xF2A65A))
        val sea = r.y + r.h * 0.6
        oval(c, r.midX - 16, sea - 18, 32.0, 32.0, argb(0xFFE2A8))
        vgrad(c, r.x, sea, r.w, r.maxY - sea, longArrayOf(0x3F5F86, 0x2B4466))
        for (k in 0 until 6) {
            val w = 26.0 - k * 3
            c.drawRoundRect(rf(r.midX - w / 2, sea + 3 + k * 5, w, 1.6), 0.8f, 0.8f, fill(argb(0xFFD9A0, 0.8)))
        }
        for (k in 0 until 7) rect(c, r.x + 8 + k * 9, sea + 6 + k * 2.5, 2.0, 14.0, argb(0x1E2433))
    }

    private fun garden(c: Canvas, r: R) {
        vgrad(c, r.x, r.y, r.w, r.h, longArrayOf(0xDDE6D2, 0xA9C092, 0x6F8E5E))
        val path = Path().apply {
            moveTo((r.midX - 6).toFloat(), (r.y + r.h * 0.55).toFloat())
            lineTo((r.midX + 6).toFloat(), (r.y + r.h * 0.55).toFloat())
            lineTo((r.midX + 40).toFloat(), r.maxY.toFloat())
            lineTo((r.midX - 40).toFloat(), r.maxY.toFloat())
            close()
        }
        c.drawPath(path, fill(argb(0xE6DCC6)))
        doubleArrayOf(0.22, 0.8).forEachIndexed { k, tx ->
            val x = r.x + r.w * tx
            rect(c, x - 1.5, r.y + r.h * 0.4, 3.0, r.h * 0.45, argb(0x5A4636))
            val blossoms = Path()
            for (j in 0 until 26) {
                val a = M.hash(k * 50.0 + j + 0.3) * 2 * Math.PI
                val d = sqrt(M.hash(k * 50.0 + j + 0.7)) * 22
                val bx = x + cos(a) * d * 1.2
                val by = r.y + r.h * 0.32 + sin(a) * d * 0.7
                blossoms.addOval(rf(bx - 3, by - 3, 6.0, 6.0), Path.Direction.CW)
            }
            c.drawPath(blossoms, fill(argb(0xE7A6B8)))
        }
    }
}

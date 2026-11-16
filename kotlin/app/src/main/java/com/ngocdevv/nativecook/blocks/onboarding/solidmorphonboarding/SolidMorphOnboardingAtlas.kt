package com.ngocdevv.nativecook.blocks.onboarding.solidmorphonboarding

import android.content.res.AssetManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import androidx.core.graphics.PathParser
import com.ngocdevv.nativecook.blocks.onboarding.solidmorphonboarding.SolidMorphOnboardingTheme as Theme
import kotlin.math.max
import kotlin.math.min
import kotlin.math.round
import kotlin.math.sqrt

/**
 * The two images the shader samples (RN `smo-atlas.ts`): the page UI atlas (the three pages stacked, drawn once per layout: progress, skip, chip, title,
 * body, the two buttons) and the glyph field (a 256 x 256 signed-distance texture per page, the engraved mark: the path is rasterised with antialiasing
 * and turned into an exact Euclidean field, sub-texel at the edges).
 */
internal class SolidMorphOnboardingAtlas(assets: AssetManager) {
    private val regular = Typeface.createFromAsset(assets, "solidmorphonboarding/solidmorphonboarding_Outfit-Regular.ttf")
    private val semibold = Typeface.createFromAsset(assets, "solidmorphonboarding/solidmorphonboarding_Outfit-SemiBold.ttf")
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val text = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG)

    private fun hex(v: Int) = 0xFF000000.toInt() or v

    /** one text box: wrapped lines of `size` with a line-height multiplier, optional tracking in px, centred or not, optionally centred vertically */
    private fun paragraph(
        c: Canvas, str: String, x: Double, y: Double, w: Double, h: Double, size: Double, face: Typeface, color: Int, center: Boolean, valign: Boolean, tracking: Double, mult: Double,
    ) {
        text.typeface = face
        text.textSize = size.toFloat()
        text.letterSpacing = if (size > 0) (tracking / size).toFloat() else 0f
        text.color = color
        val lines = ArrayList<String>()
        for (para in str.split('\n')) {
            var cur = ""
            for (word in para.split(' ')) {
                val next = if (cur.isEmpty()) word else "$cur $word"
                if (cur.isNotEmpty() && text.measureText(next) > w) {
                    lines.add(cur)
                    cur = word
                } else {
                    cur = next
                }
            }
            lines.add(cur)
        }
        val fm = text.fontMetrics
        val lineH = (size * mult).toFloat()
        // Skia Paragraph scales the font's ascent and descent so that they add up to the line height
        val scale = lineH / (fm.descent - fm.ascent)
        val total = lineH * lines.size
        val top = (y + if (valign) max(0.0, (h - total) / 2) else 0.0).toFloat()
        for ((i, line) in lines.withIndex()) {
            val lw = text.measureText(line)
            val lx = (if (center) x + (w - lw) / 2 else x).toFloat()
            c.drawText(line, lx, top + i * lineH - fm.ascent * scale, text)
        }
    }

    private fun rect(c: Canvas, x: Double, y: Double, w: Double, h: Double, r: Double, color: Int, stroke: Double = 0.0) {
        fill.shader = null
        fill.color = color
        if (stroke > 0) {
            fill.style = Paint.Style.STROKE
            fill.strokeWidth = stroke.toFloat()
        } else {
            fill.style = Paint.Style.FILL
        }
        c.drawRoundRect(RectF(x.toFloat(), y.toFloat(), (x + w).toFloat(), (y + h).toFloat()), r.toFloat(), r.toFloat(), fill)
        fill.style = Paint.Style.FILL
    }

    /** one page of the UI (RN `pageCommands`), at the canvas origin */
    private fun drawPage(c: Canvas, page: SolidMorphOnboardingPage, index: Int, count: Int, l: Theme.Layout, primary: String, secondary: String?) {
        val s = l.s
        val f = l.fontScale
        val dark = page.dark
        val fg = if (dark) Theme.paper else Theme.ink
        val x = Theme.MARGIN * s
        val segW = min(Theme.PROGRESS_W * s, (l.w - l.skipWidth - x * 2) / count - 4 * s)
        for (i in 0 until count) rect(c, x + i * (segW + 4 * s), l.progressY, segW, 4 * s, 0.0, if (i <= index) fg else if (dark) hex(0x515154) else hex(0xD0D0D2))
        paragraph(c, Theme.SKIP, l.w - l.skipWidth, l.progressY - 8 * s, l.skipWidth, 32 * s * f, 16.5 * s * f, regular, fg, false, false, 0.0, 1.2)
        page.chip?.let { chip ->
            val chipH = Theme.CHIP_H * s * f
            val chipW = min(l.w - 2 * x, round(((chip.length * 6.8 + 2 * Theme.CHIP_PAD) * s * f) / 4) * 4)
            rect(c, x, l.chipY, chipW, chipH, Theme.RADIUS_CHIP * s, if (dark) hex(0x262627) else hex(0xECECEE))
            paragraph(c, chip, x + Theme.CHIP_PAD * s, l.chipY, chipW - Theme.CHIP_PAD * s, chipH, 13.5 * s * f, regular, fg, false, true, 0.0, 1.2)
        }
        paragraph(c, page.title, x, l.titleY, l.w - x * 2, l.titleHeight, l.titleSize, semibold, fg, false, false, -0.6 * s, Theme.DISPLAY_LINE / Theme.DISPLAY_SIZE)
        page.body?.let {
            paragraph(c, it, x, l.bodyY, l.w - 2 * x, l.bodyHeight, l.bodySize, regular, if (dark) Theme.bodyDark else Theme.bodyLight, false, false, 0.0, Theme.BODY_LINE / Theme.BODY_SIZE)
        }
        val w = if (secondary == null || l.largeText) l.w - 2 * x else (l.w - 2 * x - Theme.BUTTON_GAP * s) / 2
        val r = Theme.BUTTON_R * s
        rect(c, x, l.footerY, w, l.buttonHeight, r, fg)
        val secondX = if (l.largeText) x else x + w + Theme.BUTTON_GAP * s
        val secondY = if (l.largeText) l.footerY + l.buttonHeight + Theme.BUTTON_GAP * s else l.footerY
        if (secondary != null) rect(c, secondX, secondY, w, l.buttonHeight, r, if (dark) hex(0x1D1D1F) else hex(0xF2F2F3))
        if (dark && secondary != null) rect(c, secondX, secondY, w, l.buttonHeight, r, hex(0x555558), 0.65 * s)
        val labels = if (secondary == null) listOf(primary) else listOf(primary, secondary)
        for ((i, label) in labels.withIndex()) {
            paragraph(
                c, label, if (i == 0) x else secondX, if (i == 0) l.footerY else secondY, w, l.buttonHeight, 17 * f * s, regular,
                if (i == 0) (if (dark) Theme.ink else Theme.paper) else fg, true, true, 0.0, 1.2,
            )
        }
    }

    /** the pages stacked vertically, `density` px per dp; null when the bitmap cannot be made */
    fun pageAtlas(pages: List<SolidMorphOnboardingPage>, l: Theme.Layout, density: Float, primary: String, secondary: String?): Bitmap? {
        val d = min(3f, min(density, 8190f / max(l.w, l.h * pages.size).toFloat()))
        return try {
            val bmp = Bitmap.createBitmap(Math.ceil(l.w * d).toInt(), Math.ceil(l.h * pages.size * d).toInt(), Bitmap.Config.ARGB_8888)
            val c = Canvas(bmp)
            c.scale(d, d)
            pages.forEachIndexed { i, page ->
                c.save()
                c.translate(0f, (i * l.h).toFloat())
                drawPage(c, page, i, pages.size, l, primary, secondary)
                c.restore()
            }
            bmp
        } catch (error: OutOfMemoryError) {
            null
        }
    }

    // ---- the glyph field ----

    private val far = 1e20

    /** exact 1-D squared distance (Felzenszwalb and Huttenlocher) */
    private fun edt1(f: DoubleArray, n: Int, out: DoubleArray, v: IntArray, z: DoubleArray) {
        var k = 0
        v[0] = 0
        z[0] = -far
        z[1] = far
        for (q in 1 until n) {
            var s = (f[q] + q * q - (f[v[k]] + v[k] * v[k])) / (2 * q - 2 * v[k])
            while (s <= z[k]) {
                k--
                s = (f[q] + q * q - (f[v[k]] + v[k] * v[k])) / (2 * q - 2 * v[k])
            }
            k++
            v[k] = q
            z[k] = s
            z[k + 1] = far
        }
        k = 0
        for (q in 0 until n) {
            while (z[k + 1] < q) k++
            out[q] = ((q - v[k]) * (q - v[k])).toDouble() + f[v[k]]
        }
    }

    /** Euclidean distance from every texel to the nearest seed texel */
    private fun distanceTo(seed: (Int) -> Boolean): DoubleArray {
        val w = GLYPH_CELL
        val grid = DoubleArray(w * w) { if (seed(it)) 0.0 else far }
        val f = DoubleArray(w)
        val out = DoubleArray(w)
        val v = IntArray(w)
        val z = DoubleArray(w + 1)
        for (x in 0 until w) {
            for (y in 0 until w) f[y] = grid[y * w + x]
            edt1(f, w, out, v, z)
            for (y in 0 until w) grid[y * w + x] = out[y]
        }
        for (y in 0 until w) {
            for (x in 0 until w) f[x] = grid[y * w + x]
            edt1(f, w, out, v, z)
            for (x in 0 until w) grid[y * w + x] = sqrt(out[x])
        }
        return grid
    }

    /** antialiased coverage of the built-in mark (a path in a 100-unit box, stroked at 6 units with round ends) */
    private fun coverage(path: Path): FloatArray {
        val w = GLYPH_CELL
        val bmp = Bitmap.createBitmap(w, w, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val k = (w * (94.0 / 128)).toFloat() / 100f
        c.translate(w / 2f, w / 2f)
        c.scale(k, k)
        c.translate(-50f, -50f)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth = Theme.GLYPH_STROKE.toFloat()
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        c.drawPath(path, paint)
        val px = IntArray(w * w)
        bmp.getPixels(px, 0, w, 0, 0, w, w)
        bmp.recycle()
        return FloatArray(w * w) { ((px[it] ushr 24) and 255) / 255f }
    }

    private fun signedField(a: FloatArray): IntArray {
        val toInside = distanceTo { a[it] >= 0.5f }
        val toOutside = distanceTo { a[it] < 0.5f }
        val out = IntArray(a.size)
        for (i in a.indices) {
            // texels, positive inside; edge texels use their coverage for a sub-texel edge
            var s = if (a[i] >= 0.5f) toOutside[i] - 0.5 else 0.5 - toInside[i]
            if (a[i] > 0.02f && a[i] < 0.98f) s = a[i] - 0.5
            val v = max(0, min(255, round(128 + s * 4).toInt()))
            out[i] = (0xFF shl 24) or (v shl 16) or (v shl 8) or v
        }
        return out
    }

    /** 256 x (256 x pages): one signed field per page, #010101 (far outside) where a page has none */
    fun glyphAtlas(pages: List<SolidMorphOnboardingPage>): Bitmap {
        val g = GLYPH_CELL
        val bmp = Bitmap.createBitmap(g, g * pages.size, Bitmap.Config.ARGB_8888)
        bmp.eraseColor(Color.rgb(1, 1, 1))
        for ((i, page) in pages.withIndex()) {
            val path = try {
                PathParser.createPathFromPathData(page.glyph)
            } catch (error: RuntimeException) {
                continue
            }
            val field = signedField(coverage(path))
            bmp.setPixels(field, 0, g, 0, i * g, g, g)
        }
        return bmp
    }

    companion object {
        /** glyph field cell: 256 x 256 per page, 4 levels per texel around 128 */
        const val GLYPH_CELL = 256
    }
}

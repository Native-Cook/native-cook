package com.ngocdevv.nativecook.blocks.text.windscatterletters

import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.sin

/*
 Wind Scatter Letters — static artwork and the headline's layout (a port of windScatterArt.ts).

 Sky: a four-stop gradient from plum-grey to ember near the horizon, a low sun glow, a few long
 cloud bands, grain and a vignette; two ridge silhouettes, the near one with a lit rim. All of it
 is rendered once into a bitmap off the main thread, and so is every glyph of the headline (crisp,
 and for the far glyphs a soft and a blurred copy), so the first frame and every later one only
 place bitmaps. The leaf is one path (blade + rib), 24 dp long, batched per colour per frame.
*/

internal object WindPalette {
    val sky = intArrayOf(0xFF16151B.toInt(), 0xFF25202A.toInt(), 0xFF4A2D2C.toInt(), 0xFF9A5333.toInt())
    const val CREAM = 0xFFF4E7D4.toInt()
    const val EMBER = 0xFFE8A86C.toInt()
    val leaves = intArrayOf(0xFFC4572E.toInt(), 0xFFD99A3A.toInt(), 0xFF8F2F23.toInt())
}

/** y of the near ridge's crest at x (a few slow sines), shared by the art and the resting leaves. */
internal fun windRidgeY(x: Float, W: Float, H: Float): Float {
    val base = H * 0.8f
    return base - 18f * sin((x / W) * PI.toFloat() * 1.3f + 0.4f) - 7f * sin((x / W) * PI.toFloat() * 4.1f + 1.2f)
}

private fun sigmaRadius(sigma: Float) = max(0.5f, (sigma - 0.5f) / 0.57735f)

private fun hex(c: Int, a: Float) = (((a * 255f + 0.5f).toInt() and 0xFF) shl 24) or (c and 0xFFFFFF)

/** The sky and ridges at `px` pixels per dp. */
internal fun windMakeBackground(W: Float, H: Float, px: Float): Bitmap {
    val bmp = Bitmap.createBitmap(max(1, ceil(W * px).toInt()), max(1, ceil(H * px).toInt()), Bitmap.Config.ARGB_8888)
    val c = Canvas(bmp)
    c.scale(px, px)
    val full = RectF(0f, 0f, W, H)
    c.drawRect(full, Paint().apply { shader = LinearGradient(0f, 0f, 0f, H * 0.84f, WindPalette.sky, floatArrayOf(0f, 0.38f, 0.72f, 1f), Shader.TileMode.CLAMP) })
    c.drawRect(full, Paint().apply {
        shader = RadialGradient(W * 0.72f, H * 0.79f, W * 0.75f, intArrayOf(hex(0xFFAA60, 0.42f), hex(0xE87846, 0.14f), 0), floatArrayOf(0f, 0.4f, 1f), Shader.TileMode.CLAMP)
    })
    // Cloud bands.
    for (i in 0 until 5) {
        val y = H * (0.5f + 0.05f * i) + (windHash(i.toFloat()) - 0.5f) * 20f
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (i % 2 == 1) hex(0xFFBE8C, 0.07f) else hex(0x1E141E, 0.18f)
            maskFilter = BlurMaskFilter(sigmaRadius(10f), BlurMaskFilter.Blur.NORMAL)
        }
        val x0 = -40f + windHash(i + 3f) * W * 0.4f
        c.drawRoundRect(RectF(x0, y, x0 + W * (0.6f + 0.5f * windHash(i + 7f)), y + 8f + 10f * windHash(i + 9f)), 10f, 10f, p)
    }
    // Ridges.
    val far = Path().apply {
        moveTo(0f, H)
        var x = 0f
        while (x <= W) {
            lineTo(x, H * 0.75f - 26f * sin((x / W) * PI.toFloat() * 2.2f + 2.1f) - 10f * sin((x / W) * 9.1f))
            x += 8f
        }
        lineTo(W, H); close()
    }
    c.drawPath(far, Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = LinearGradient(0f, H * 0.7f, 0f, H, 0xFF3A2526.toInt(), 0xFF2A1D20.toInt(), Shader.TileMode.CLAMP) })
    val near = Path().apply {
        moveTo(0f, H)
        var x = 0f
        while (x <= W) { lineTo(x, windRidgeY(x, W, H)); x += 6f }
        lineTo(W, H); close()
    }
    c.drawPath(near, Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = LinearGradient(0f, H * 0.76f, 0f, H, intArrayOf(0xFF241619.toInt(), 0xFF170F12.toInt(), 0xFF0E0A0C.toInt()), floatArrayOf(0f, 0.4f, 1f), Shader.TileMode.CLAMP)
    })
    val rim = Path().apply {
        moveTo(0f, windRidgeY(0f, W, H))
        var x = 6f
        while (x <= W) { lineTo(x, windRidgeY(x, W, H)); x += 6f }
    }
    c.drawPath(rim, Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.2f
        shader = LinearGradient(0f, 0f, W, 0f, intArrayOf(hex(0xFFAA64, 0.05f), hex(0xFFAA64, 0.45f), hex(0xFFAA64, 0.2f)), floatArrayOf(0f, 0.72f, 1f), Shader.TileMode.CLAMP)
    })
    // Grain and vignette.
    val dot = Paint().apply { color = hex(0xFFF0DC, 0.035f) }
    for (i in 0 until 1400) c.drawCircle(windHash(i * 1.37f) * W, windHash(i * 2.71f + 5f) * H, 0.6f, dot)
    c.drawRect(full, Paint().apply {
        shader = RadialGradient(W / 2, H * 0.42f, H * 0.78f, intArrayOf(0, hex(0, 0.22f), hex(0, 0.6f)), floatArrayOf(0.45f, 0.78f, 1f), Shader.TileMode.CLAMP)
    })
    bmp.prepareToDraw()
    return bmp
}

/** The leaf blade, centred on (0, 0), pointing along +x, and its rib. */
internal class WindLeaf(val blade: Path, val rib: Path)

internal fun windMakeLeaf(): WindLeaf {
    val blade = Path().apply {
        moveTo(-12f, 0f)
        cubicTo(-7f, -7.5f, 4f, -8f, 12f, 0f)
        cubicTo(4f, 7.2f, -7f, 6.6f, -12f, 0f)
        close()
    }
    val rib = Path().apply {
        moveTo(-16f, 0.8f)
        quadTo(-12f, 0.3f, -10f, 0f)
        lineTo(10f, 0f)
        for (x in floatArrayOf(-5f, 0f, 5f)) {
            moveTo(x, 0f); lineTo(x + 4f, -3.6f)
            moveTo(x, 0f); lineTo(x + 4f, 3.4f)
        }
    }
    return WindLeaf(blade, rib)
}

/** Faint wind streaks: long, slightly curved strokes across the upper half. */
internal fun windMakeStreaks(W: Float, H: Float): List<Path> = (0 until 6).map { i ->
    val y = H * (0.2f + 0.1f * i) + (windHash(i * 4.1f) - 0.5f) * 30f
    Path().apply {
        moveTo(-20f, y)
        cubicTo(W * 0.3f, y - 30f * windHash(i + 2f), W * 0.6f, y + 26f * windHash(i + 5f), W + 20f, y - 12f)
    }
}

private val LINES = arrayOf("Let it", "drift")
private const val MAX_SIZE = 96f

/** Georgia bold italic on iOS; the platform serif in bold italic here. */
private val SerifBoldItalic: Typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD_ITALIC)

internal fun windFont(size: Float) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    typeface = SerifBoldItalic
    textSize = size
}

/** The headline set at runtime: per glyph its character, width and home, then the ground leaves' homes. */
internal class WindLayout(
    val chars: CharArray, val widths: FloatArray, val homes: List<WindHome>,
    val glyphs: Int, val size: Float, val b1: Float, val b2: Float,
)

internal fun windMakeLayout(W: Float, H: Float, insetTop: Float): WindLayout {
    var size = MAX_SIZE
    var font = windFont(size)
    val widest = max(font.measureText(LINES[0]), font.measureText(LINES[1]))
    if (widest > W - 48f) {
        size = floor(size * (W - 48f) / widest)
        font = windFont(size)
    }
    val b1 = insetTop + 92f + size * 0.95f
    val b2 = b1 + size * 0.98f
    val chars = ArrayList<Char>()
    val widths = ArrayList<Float>()
    val homes = ArrayList<WindHome>()
    LINES.forEachIndexed { li, line ->
        val lw = FloatArray(line.length)
        font.getTextWidths(line, lw)
        var x = 24f
        for (k in line.indices) {
            if (line[k] != ' ') {
                chars.add(line[k])
                widths.add(lw[k])
                homes.add(WindHome(x + lw[k] / 2, (if (li == 0) b1 else b2) - size * 0.34f, 1f))
            }
            x += lw[k]
        }
    }
    val glyphs = chars.size
    val at = floatArrayOf(0.07f, 0.2f, 0.35f, 0.6f, 0.79f, 0.93f)
    for (j in 0 until WIND_GROUND_LEAVES) {
        val x = W * at[j]
        homes.add(WindHome(x, windRidgeY(x, W, H) - 3f, 0.5f))
    }
    return WindLayout(chars.toCharArray(), widths.toFloatArray(), homes, glyphs, size, b1, b2)
}

/**
 * One glyph pre-rendered: bitmaps placed so the glyph's pivot (its box centre, 0.34 em above the
 * baseline) is at dp (0, 0) of `rect`. `soft` / `blur` exist for the far glyphs only.
 */
internal class WindGlyph(val crisp: Bitmap, val soft: Bitmap?, val blur: Bitmap?, val rect: RectF)

internal fun windMakeGlyphs(L: WindLayout, px: Float): List<WindGlyph> {
    val font = windFont(L.size)
    val bounds = Rect()
    return (0 until L.glyphs).map { i ->
        val s = L.chars[i].toString()
        font.getTextBounds(s, 0, 1, bounds)
        val pad = 12f
        // Glyph origin (baseline left) sits at (-w/2, +0.34 size) from the pivot.
        val ox = -L.widths[i] / 2
        val oy = L.size * 0.34f
        val rect = RectF(ox + bounds.left - pad, oy + bounds.top - pad, ox + bounds.right + pad, oy + bounds.bottom + pad)
        val far = windDepthOf(i) < 0f
        fun render(color: Int, sigma: Float): Bitmap {
            val bw = max(1, ceil(rect.width() * px).toInt())
            val bh = max(1, ceil(rect.height() * px).toInt())
            val bmp = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888)
            val c = Canvas(bmp)
            c.scale(px, px)
            c.translate(-rect.left, -rect.top)
            val p = windFont(L.size).apply {
                this.color = color
                if (sigma > 0f) maskFilter = BlurMaskFilter(sigmaRadius(sigma), BlurMaskFilter.Blur.NORMAL)
            }
            c.drawText(s, ox, oy, p)
            bmp.prepareToDraw()
            return bmp
        }
        val farColor = hex(0xECD6BE, 0.82f)
        WindGlyph(
            crisp = render(WindPalette.CREAM, 0f),
            soft = if (far) render(farColor, 0.3f) else null,
            blur = if (far) render(farColor, 2.5f) else null,
            rect = RectF(rect.left, rect.top, rect.left + ceil(rect.width() * px) / px, rect.top + ceil(rect.height() * px) / px),
        )
    }
}

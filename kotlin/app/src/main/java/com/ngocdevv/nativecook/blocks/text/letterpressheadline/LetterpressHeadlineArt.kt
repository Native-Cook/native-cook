package com.ngocdevv.nativecook.blocks.text.letterpressheadline

import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Picture
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.graphics.Typeface
import android.os.Build
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/*
 Layout and static art, made once per screen size. The headline is measured at runtime with the
 font's own glyph advances (getTextWidths), so every letter's box, block and deboss line up on any
 device; its mask (crisp in red, line 1 / line 2 blurred in green / blue) is drawn once.
 */

internal val LP_HEADLINE = listOf("Made by", "hand.")
internal val LP_SECOND = listOf("Twelve prints on 600 gsm cotton,", "numbered and signed by the press.")
private const val MARGIN = 28f
// The edition's spec list under the second line: three rows.
private const val SPECS_GAP = 30f
private const val SPECS_H = 3 * 38f
private const val PAD = 14f

internal class LpGlyph(val ch: String, val x: Float, val baseline: Float, val w: Float)

internal class LpRegion(val x: Float, val y: Float, val w: Float, val h: Float)

internal class LpLayout(
    val width: Float,
    val height: Float,
    val chromeTop: Float,
    val kickerTop: Float,
    val size: Float,
    val glyphs: List<LpGlyph>,
    /** The headline's region (its mask and the deboss overlay cover it). */
    val region: LpRegion,
    /** Each glyph's box relative to the region, x0 y0 x1 y1 per glyph, padded to 12 boxes. */
    val boxes: FloatArray,
    /** Where line 1's boxes end and line 2's begin, relative to the region. */
    val split: Float,
    val secondX: Float,
    val secondBaselines: FloatArray,
    val secondSize: Float,
    val secondWidth: Float,
    val specsTop: Float,
    val detailsTop: Float,
    val buttonTop: Float,
    val hintTop: Float,
)

internal fun lpHeadlinePaint(size: Float) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
    textSize = size
}

internal fun lpSecondPaint(size: Float) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    typeface = Typeface.create(Typeface.SERIF, Typeface.ITALIC)
    textSize = size
}

private fun widths(p: Paint, text: String): FloatArray {
    val out = FloatArray(text.length)
    p.getTextWidths(text, out)
    return out
}

internal fun lpLayout(width: Float, height: Float, top: Float, bottom: Float): LpLayout {
    val chromeTop = top + 12f
    val hintTop = height - max(bottom, 12f) - 22f
    val buttonTop = hintTop - 14f - 56f
    val detailsTop = buttonTop - 18f - 22f
    // Fit the headline to the width (measured at 100 dp, then scaled).
    val probe = lpHeadlinePaint(100f)
    val widest = LP_HEADLINE.maxOf { widths(probe, it).sum() }
    val size = floor(min(92f, (width - MARGIN * 2) / widest * 100f))
    val font = lpHeadlinePaint(size)
    val secondSize = 16f
    val sfont = lpSecondPaint(secondSize)
    // The whole text block, centred between the chrome and the details.
    val blockH = 30f + size * 0.72f + size * 1.0f + 46f + 24f + 8f + SPECS_GAP + SPECS_H
    val room0 = chromeTop + 48f
    val room1 = detailsTop - 24f
    val kickerTop = max(room0, room0 + (room1 - room0 - blockH) / 2f)
    val b1 = kickerTop + 30f + size * 0.72f
    val b2 = b1 + size * 1.0f
    val glyphs = ArrayList<LpGlyph>()
    LP_HEADLINE.forEachIndexed { row, line ->
        val ws = widths(font, line)
        var x = MARGIN
        for (i in line.indices) {
            if (line[i] != ' ') glyphs.add(LpGlyph(line[i].toString(), x, if (row == 0) b1 else b2, ws[i]))
            x += ws[i]
        }
    }
    val rx = MARGIN - PAD
    val ry = b1 - size * 0.8f - PAD
    val region = LpRegion(rx, ry, width - (MARGIN - PAD) * 2f, b2 + size * 0.24f + PAD - ry)
    // Boxes of the two lines meet halfway between line 1's descenders and line 2's ascenders, so a
    // blurred edge always takes its own line's depth.
    val split = (b1 + size * 0.22f + b2 - size * 0.76f) / 2f
    val boxes = FloatArray(LP_MAX_GLYPHS * 4)
    for (i in 0 until LP_MAX_GLYPHS) {
        val g = glyphs.getOrNull(i)
        if (g == null) {
            boxes[i * 4] = -1000f; boxes[i * 4 + 1] = -1000f; boxes[i * 4 + 2] = -999f; boxes[i * 4 + 3] = -999f
            continue
        }
        boxes[i * 4] = g.x - region.x
        boxes[i * 4 + 1] = (if (g.baseline == b1) region.y else split) - region.y
        boxes[i * 4 + 2] = g.x + g.w - region.x
        boxes[i * 4 + 3] = (if (g.baseline == b1) split else region.y + region.h) - region.y
    }
    val s1 = b2 + 46f
    val secondWidth = LP_SECOND.maxOf { widths(sfont, it).sum() }
    return LpLayout(
        width, height, chromeTop, kickerTop, size, glyphs, region, boxes, split - region.y,
        MARGIN, floatArrayOf(s1, s1 + 24f), secondSize, secondWidth,
        s1 + 24f + 8f + SPECS_GAP, detailsTop, buttonTop, hintTop,
    )
}

/**
 * Draws once into a bitmap `w` × `h` px: through a picture into a hardware bitmap when asked (so
 * runtime shaders render exactly as on screen), into a plain bitmap otherwise.
 */
private fun lpBake(w: Int, h: Int, hardware: Boolean, draw: (Canvas) -> Unit): Bitmap? {
    if (w <= 0 || h <= 0 || w > 8192 || h > 8192) return null
    return try {
        if (hardware && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val picture = Picture()
            draw(picture.beginRecording(w, h))
            picture.endRecording()
            Bitmap.createBitmap(picture, w, h, Bitmap.Config.HARDWARE)
        } else {
            Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also { draw(Canvas(it)) }
        }
    } catch (_: RuntimeException) {
        null
    } catch (_: OutOfMemoryError) {
        null
    }
}

/** The paper, baked into one bitmap at `scale` pixels per dp (gradients below Android 13). */
internal fun lpBakePaper(l: LpLayout, scale: Float): Bitmap? {
    val w = ceil(l.width * scale).toInt()
    val h = ceil(l.height * scale).toInt()
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        val shader = runCatching { RuntimeShader(LP_PAPER_AGSL) }.getOrNull()
        if (shader != null) {
            shader.setFloatUniform("size", l.width, l.height)
            lpBake(w, h, true) { c ->
                c.scale(scale, scale)
                c.drawRect(0f, 0f, l.width, l.height, Paint().apply { this.shader = shader })
            }?.let { return it }
        }
    }
    return lpBake(w, h, false) { c ->
        c.scale(scale, scale)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.shader = LinearGradient(0f, 0f, 0f, l.height, 0xFFF4EFE5.toInt(), 0xFFECE5D8.toInt(), Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, l.width, l.height, p)
        p.shader = RadialGradient(
            l.width / 2f, l.height / 2f, max(l.width, l.height) * 0.75f,
            intArrayOf(0x00000000, 0x00000000, 0x14000000), floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP,
        )
        c.drawRect(0f, 0f, l.width, l.height, p)
    }
}

// Android takes blur radii; Skia (and the RN code) thinks in sigma.
private fun sigmaToRadius(sigma: Float) = ((sigma - 0.5f) / 0.57735f).coerceAtLeast(0.01f)

/** The headline's mask over its region: red = crisp letters, green / blue = line 1 / 2 blurred (heights). */
internal fun lpBuildMask(l: LpLayout, scale: Float): Bitmap? {
    val r = l.region
    val w = ceil(r.w * scale).toInt()
    val h = ceil(r.h * scale).toInt()
    // Each line's blur in its own channel, drawn over in a layer of its own and then added, so a
    // blurred edge reaching into the other line never borrows that line's depth.
    val first = l.glyphs.firstOrNull()?.baseline ?: 0f
    val soft = lpBake(w, h, false) { c ->
        c.scale(scale, scale)
        c.translate(-r.x, -r.y)
        val p = lpHeadlinePaint(l.size).apply { maskFilter = BlurMaskFilter(sigmaToRadius(1.3f), BlurMaskFilter.Blur.NORMAL) }
        for (g in l.glyphs) {
            p.color = if (g.baseline == first) 0xFF00FF00.toInt() else 0xFF0000FF.toInt()
            c.drawText(g.ch, g.x, g.baseline, p)
        }
    } ?: return null
    return lpBake(w, h, false) { c ->
        c.drawColor(0xFF000000.toInt())
        c.save()
        c.scale(scale, scale)
        c.translate(-r.x, -r.y)
        val crisp = lpHeadlinePaint(l.size).apply { color = 0xFFFF0000.toInt() }
        for (g in l.glyphs) c.drawText(g.ch, g.x, g.baseline, crisp)
        c.restore()
        c.drawBitmap(soft, 0f, 0f, Paint().apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.ADD) })
    }.also { soft.recycle() }
}

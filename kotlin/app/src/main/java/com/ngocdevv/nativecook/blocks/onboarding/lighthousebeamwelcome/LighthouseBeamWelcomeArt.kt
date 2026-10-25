package com.ngocdevv.nativecook.blocks.onboarding.lighthousebeamwelcome

import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.os.Build
import com.ngocdevv.nativecook.shared.sigmaToRadius
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/*
 Drawing for Lighthouse Beam Welcome (a port of lighthouseArt.ts): the tower on its rocks (a bitmap
 made once), the three pages of onboarding text drawn once into the bitmap the shader reveals, and
 the per-frame bits (the lamp's glow and flare, the paging dots).
 */

private class PageCopy(val kicker: String, val title: String, val body: String)

private val PAGE_COPY = arrayOf(
    PageCopy("SAFE PASSAGE", "Light the way home", "Safe routes around shoals and wrecks, even in fog."),
    PageCopy("ONE-TAP SOS", "Raise the alarm fast", "One tap sends your position to every boat nearby."),
    PageCopy("TIDES & SWELL", "Read the water first", "Live tide, wind and swell from 1,200 stations."),
)

private fun argb(a: Float, r: Int, g: Int, b: Int) = ((a.coerceIn(0f, 1f) * 255).roundToInt() shl 24) or (r shl 16) or (g shl 8) or b
private fun c(hex: Long) = hex.toInt()

private fun paint(color: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }
private fun line(color: Int, width: Float) = paint(color).apply {
    style = Paint.Style.STROKE
    strokeWidth = width
    strokeCap = Paint.Cap.ROUND
    strokeJoin = Paint.Join.ROUND
}

private fun weighted(weight: Int): Typeface =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        Typeface.create(Typeface.SANS_SERIF, weight, false)
    } else {
        Typeface.create(Typeface.SANS_SERIF, if (weight >= 600) Typeface.BOLD else Typeface.NORMAL)
    }

/** Greedy word wrap with measured widths. */
private fun wrap(font: Paint, text: String, maxWidth: Float): List<String> {
    val lines = ArrayList<String>()
    var current = ""
    for (word in text.split(' ')) {
        val next = if (current.isNotEmpty()) "$current $word" else word
        if (current.isNotEmpty() && font.measureText(next) > maxWidth) {
            lines.add(current)
            current = word
        } else {
            current = next
        }
    }
    if (current.isNotEmpty()) lines.add(current)
    return lines
}

/** The page icons, 30 dp boxes: a compass rose, a life ring, a tide wave. */
private fun icon(c: Canvas, kind: Int, x: Float, y: Float) {
    val ink = line(0xFFFFFFFF.toInt(), 2f)
    val cx = x + 15
    val cy = y + 15
    when (kind) {
        0 -> {
            c.drawCircle(cx, cy, 13f, ink)
            c.drawPath(Path().apply {
                moveTo(cx, cy - 9)
                lineTo(cx + 4, cy)
                lineTo(cx, cy + 9)
                lineTo(cx - 4, cy)
                close()
            }, paint(0xFFFFFFFF.toInt()))
        }
        1 -> {
            c.drawCircle(cx, cy, 13f, ink)
            c.drawCircle(cx, cy, 6f, ink)
            val bands = line(0xFFFFFFFF.toInt(), 5f).apply { strokeCap = Paint.Cap.BUTT }
            for (i in 0 until 4) {
                c.drawPath(Path().apply { addArc(RectF(cx - 9.5f, cy - 9.5f, cx + 9.5f, cy + 9.5f), 45f + i * 90 - 14, 28f) }, bands)
            }
        }
        else -> {
            for (r in 0 until 2) {
                val wave = Path()
                val wy = cy - 2 + r * 8
                wave.moveTo(x + 1, wy)
                for (i in 0 until 3) {
                    val sx = x + 1 + i * 9.4f
                    wave.cubicTo(sx + 2.4f, wy - 5, sx + 4.7f, wy - 5, sx + 4.7f, wy)
                    wave.cubicTo(sx + 4.7f, wy + 5, sx + 7, wy + 5, sx + 9.4f, wy)
                }
                c.drawPath(wave, ink)
            }
            c.drawCircle(cx + 7, y + 3, 3f, ink)
        }
    }
}

/**
 * The three pages side by side (page i starts at i × width), white on transparent, at `ratio` px
 * per dp. The shader reads only its alpha.
 */
internal fun lighthouseContent(g: LightGeo, ratio: Float): Bitmap {
    val pw = max(1, ceil(g.w * LIGHT_PAGES * ratio).toInt())
    val ph = max(1, ceil(g.contentH * ratio).toInt())
    val bmp = Bitmap.createBitmap(pw, ph, Bitmap.Config.ARGB_8888)
    val c = Canvas(bmp)
    c.scale(ratio, ratio)
    val s = min(1f, g.u)
    val kicker = paint(argb(0.78f, 255, 255, 255)).apply { typeface = weighted(700); textSize = 11f }
    val title = paint(0xFFFFFFFF.toInt()).apply { typeface = weighted(700); textSize = (25 * s).roundToInt().toFloat() }
    val body = paint(argb(0.78f, 255, 255, 255)).apply { typeface = weighted(400); textSize = 15f }
    for (i in 0 until LIGHT_PAGES) {
        val copy = PAGE_COPY[i]
        val x = i * g.w + g.textX
        var y = g.textY - g.contentY
        icon(c, i, x, y)
        c.drawText("0${i + 1}  ·  ${copy.kicker}", x + 40, y + 19, kicker)
        y += 40
        for (text in wrap(title, copy.title, g.textW)) {
            y += title.textSize * 1.18f
            c.drawText(text, x, y, title)
        }
        y += 6
        for (text in wrap(body, copy.body, g.textW)) {
            y += body.textSize * 1.4f
            c.drawText(text, x, y, body)
        }
    }
    return bmp
}

/** The lighthouse on its rocks at dusk (rim light from the sky on the right, lamp-lit top), at density px per dp. */
internal fun lighthouseTower(g: LightGeo, density: Float): Bitmap {
    val bmp = Bitmap.createBitmap(max(1, (g.w * density).roundToInt()), max(1, (g.h * density).roundToInt()), Bitmap.Config.ARGB_8888)
    val c = Canvas(bmp)
    c.scale(density, density)
    val u = g.u
    val x = g.lampX
    val baseW = 25 * u
    val topW = 15 * u
    val top = g.galleryY + 6 * u
    val base = g.baseY

    // Rocks: a low dark mass with a lighter top edge.
    val rocks = Path().apply {
        moveTo(x - 92 * u, g.horizon + 34 * u)
        cubicTo(x - 80 * u, g.horizon + 4 * u, x - 52 * u, g.horizon - 6 * u, x - 30 * u, base - 8 * u)
        cubicTo(x - 10 * u, base - 16 * u, x + 22 * u, base - 14 * u, x + 38 * u, base - 4 * u)
        cubicTo(x + 58 * u, g.horizon + 6 * u, x + 78 * u, g.horizon + 18 * u, x + 96 * u, g.horizon + 34 * u)
        close()
    }
    c.drawPath(rocks, paint(c(0xFF10152E)).apply {
        shader = LinearGradient(x, base - 16 * u, x, g.horizon + 34 * u, intArrayOf(c(0xFF2A2B4D), c(0xFF141936), c(0xFF0A0E22)), floatArrayOf(0f, 0.4f, 1f), Shader.TileMode.CLAMP)
    })

    // Tower: tapered, shaded as a cylinder (dark left, dusk-lit right edge), two dim red bands.
    val tower = Path().apply {
        moveTo(x - baseW, base)
        lineTo(x - topW, top)
        lineTo(x + topW, top)
        lineTo(x + baseW, base)
        close()
    }
    c.drawPath(tower, paint(c(0xFF3A3C5E)).apply {
        shader = LinearGradient(x - baseW, 0f, x + baseW, 0f, intArrayOf(c(0xFF1E2140), c(0xFF45466C), c(0xFF6E6A92), c(0xFF8D7FA6)), floatArrayOf(0f, 0.45f, 0.82f, 1f), Shader.TileMode.CLAMP)
    })
    c.save()
    c.clipPath(tower)
    val band = paint(argb(0.55f, 150, 52, 72))
    val span = base - top
    for (f in floatArrayOf(0.22f, 0.56f)) c.drawRect(x - baseW - 2, top + span * f, x + baseW + 2, top + span * f + span * 0.14f, band)
    // Lamp light falling down the tower from the gallery.
    c.drawRect(x - baseW, top, x + baseW, top + span * 0.4f, Paint().apply {
        shader = LinearGradient(0f, top, 0f, top + span * 0.4f, argb(0.28f, 255, 214, 150), argb(0f, 255, 214, 150), Shader.TileMode.CLAMP)
    })
    c.restore()
    // Two small lit windows.
    for (f in floatArrayOf(0.42f, 0.74f)) {
        val wy = top + span * f
        c.drawRoundRect(RectF(x - 2.2f * u, wy, x + 2.2f * u, wy + 7 * u), 2 * u, 2 * u, paint(argb(0.85f, 255, 206, 130)))
    }

    // Gallery deck and railing.
    val deckY = g.galleryY
    c.drawRoundRect(RectF(x - 23 * u, deckY + 3 * u, x + 23 * u, deckY + 7 * u), 2 * u, 2 * u, paint(c(0xFF24264A)))
    val rail = line(argb(0.75f, 170, 160, 205), 1.1f * u)
    c.drawLine(x - 21 * u, deckY - 4 * u, x + 21 * u, deckY - 4 * u, rail)
    for (i in 0..6) {
        val rx = x - 21 * u + i * 7 * u
        c.drawLine(rx, deckY - 4 * u, rx, deckY + 3 * u, rail)
    }

    // Lantern room: glass panes around the lamp, then the cap and a vane.
    val roomTop = g.lampY - 13 * u
    c.drawRect(x - 11 * u, roomTop, x + 11 * u, deckY - 4 * u, paint(argb(0.18f, 255, 222, 160)))
    val mullion = line(argb(0.9f, 30, 32, 60), 1.4f * u)
    for (dx in floatArrayOf(-11f, -4f, 4f, 11f)) c.drawLine(x + dx * u, roomTop, x + dx * u, deckY - 4 * u, mullion)
    val cap = Path().apply {
        moveTo(x - 15 * u, roomTop)
        cubicTo(x - 12 * u, roomTop - 12 * u, x + 12 * u, roomTop - 12 * u, x + 15 * u, roomTop)
        close()
    }
    c.drawPath(cap, paint(c(0xFF262848)).apply {
        shader = LinearGradient(x - 15 * u, 0f, x + 15 * u, 0f, intArrayOf(c(0xFF15172F), c(0xFF2E3056), c(0xFF6A5F8C)), floatArrayOf(0f, 0.6f, 1f), Shader.TileMode.CLAMP)
    })
    c.drawLine(x, roomTop - 9 * u, x, roomTop - 18 * u, line(c(0xFF2A2C4E), 1.4f * u))
    c.drawCircle(x, roomTop - 18 * u, 1.6f * u, paint(c(0xFF2A2C4E)))
    return bmp
}

/** The lamp's steady glow and its flare, and the paging dots: drawn per frame from baked parts. */
internal class LighthouseLive(private val g: LightGeo, density: Float) {
    private val u = g.u
    private val glowPad = 20 * u
    private val glow: Bitmap
    private val glowDst = RectF(g.lampX - 5.5f * u - glowPad, g.lampY - 5.5f * u - glowPad, g.lampX + 5.5f * u + glowPad, g.lampY + 5.5f * u + glowPad)
    private val streak: Bitmap
    private val core = paint(c(0xFFFFF6E2))
    private val halo = Paint(Paint.ANTI_ALIAS_FLAG)
    private val baked = Paint(Paint.FILTER_BITMAP_FLAG)
    private val dot = paint(c(0xFFF2E6C8))
    private val rect = RectF()
    private val widths = FloatArray(LIGHT_PAGES)

    init {
        val gs = glowDst.width()
        glow = Bitmap.createBitmap(max(1, (gs * density).roundToInt()), max(1, (gs * density).roundToInt()), Bitmap.Config.ARGB_8888).also {
            val cv = Canvas(it)
            cv.scale(it.width / gs, it.height / gs)
            cv.drawCircle(gs / 2, gs / 2, 5.5f * u, paint(argb(0.9f, 255, 224, 160)).apply { maskFilter = BlurMaskFilter(sigmaToRadius(5 * u), BlurMaskFilter.Blur.NORMAL) })
        }
        // A unit streak (100 × 2.4u dp plus its blur), stretched to the flare's width per frame.
        val sw = 100f
        val pad = 6f
        val sh = 2.4f * u + 2 * pad
        streak = Bitmap.createBitmap(max(1, ((sw + 2 * pad) * density).roundToInt()), max(1, (sh * density).roundToInt()), Bitmap.Config.ARGB_8888).also {
            val cv = Canvas(it)
            cv.scale(it.width / (sw + 2 * pad), it.height / sh)
            cv.drawRoundRect(RectF(pad, pad, pad + sw, pad + 2.4f * u), u, u, paint(argb(0.6f, 255, 236, 200)).apply {
                maskFilter = BlurMaskFilter(sigmaToRadius(2 * u / density), BlurMaskFilter.Blur.NORMAL)
            })
        }
    }

    fun drawLamp(c: Canvas, flare: Float) {
        baked.alpha = 255
        c.drawBitmap(glow, null, glowDst, baked)
        c.drawCircle(g.lampX, g.lampY, 3 * u, core)
        if (flare > 0.01f) {
            val r = (40 + 90 * flare) * u
            halo.shader = RadialGradient(
                g.lampX, g.lampY, r,
                intArrayOf(argb(0.95f, 255, 236, 196), argb(0.35f, 255, 214, 150), argb(0f, 255, 200, 140)),
                floatArrayOf(0f, 0.25f, 1f),
                Shader.TileMode.CLAMP,
            )
            halo.alpha = (min(1f, flare * 1.1f) * 255).roundToInt()
            c.drawCircle(g.lampX, g.lampY, r, halo)
            // A short horizontal streak, like a lens flare.
            val half = r * 1.4f
            val k = (2 * half) / 100f
            rect.set(g.lampX - half - 6 * k, g.lampY - 1.2f * u - 6, g.lampX + half + 6 * k, g.lampY + 1.2f * u + 6)
            baked.alpha = (flare * 0.7f * 255).roundToInt().coerceIn(0, 255)
            c.drawBitmap(streak, null, rect, baked)
            baked.alpha = 255
        }
    }

    /** Paging dots: the active one is a pill; `page` is fractional while sliding. */
    fun drawDots(c: Canvas, cx: Float, y: Float, page: Float) {
        val gap = 8f
        val d = 7f
        val wide = 22f
        var total = 0f
        for (i in 0 until LIGHT_PAGES) {
            val near = max(0f, 1 - abs(page - i))
            widths[i] = d + (wide - d) * near
            total += widths[i] + (if (i > 0) gap else 0f)
        }
        var x = cx - total / 2
        for (i in 0 until LIGHT_PAGES) {
            val near = max(0f, 1 - abs(page - i))
            dot.alpha = ((0.32f + 0.63f * near) * 255).roundToInt()
            rect.set(x, y - d / 2, x + widths[i], y + d / 2)
            c.drawRoundRect(rect, d / 2, d / 2, dot)
            x += widths[i] + gap
        }
    }
}

package com.ngocdevv.nativecook.blocks.lists.slotreellist

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
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/*
 Slot Reel List — the art drawn once, off the main thread, when the size is known:
 - the reel strip: the three lists (mains, sides, drinks) printed on cream paper, one column per
   reel, one row per dish, at the screen's pixel ratio; the shader wraps it round the cylinders;
 - the back: the dark diner wall, the cherry-enamel cabinet with its chrome rim, the marquee, the
   reel labels, the lever's hub — one screen-sized bitmap;
 - the front: the window's inner shadows, the glass streak and the brass payline arrows.
*/

internal class SlotDish(val name: String, val meta: String)

internal val SlotMenu = arrayOf(
    arrayOf(
        SlotDish("Miso\nSalmon", "25 MIN"), SlotDish("Lemon\nChicken", "40 MIN"), SlotDish("Mushroom\nRisotto", "35 MIN"),
        SlotDish("Beef\nTacos", "20 MIN"), SlotDish("Gnocchi\nPesto", "15 MIN"), SlotDish("Green\nCurry", "30 MIN"),
        SlotDish("Shakshuka", "25 MIN"), SlotDish("Crispy\nTofu", "20 MIN"),
    ),
    arrayOf(
        SlotDish("Charred\nGreens", "90 KCAL"), SlotDish("Garlic\nRice", "210 KCAL"), SlotDish("Smashed\nPotatoes", "240 KCAL"),
        SlotDish("Corn\nElote", "180 KCAL"), SlotDish("Kimchi\nSlaw", "70 KCAL"), SlotDish("Herb\nCouscous", "190 KCAL"),
        SlotDish("Roast\nCarrots", "110 KCAL"), SlotDish("Miso\nSoup", "60 KCAL"),
    ),
    arrayOf(
        SlotDish("Yuzu\nSpritz", "ZERO PROOF"), SlotDish("Iced\nHojicha", "TEA"), SlotDish("Hibiscus\nCooler", "ZERO PROOF"),
        SlotDish("Ginger\nBeer", "FIZZ"), SlotDish("Mint\nLemonade", "FIZZ"), SlotDish("Cold\nBrew", "COFFEE"),
        SlotDish("Sparkling\nPlum", "FIZZ"), SlotDish("Chai\nLatte", "TEA"),
    ),
)

/** Minutes and kcal of each main and side, for the result card. */
internal val SlotMinutes = intArrayOf(25, 40, 35, 20, 15, 30, 25, 20)
internal val SlotKcal = arrayOf(
    intArrayOf(520, 610, 560, 640, 590, 540, 430, 470),
    intArrayOf(90, 210, 240, 180, 70, 190, 110, 60),
    intArrayOf(80, 20, 60, 120, 110, 10, 90, 150),
)

internal class SlotRect(val x: Float, val y: Float, val w: Float, val h: Float)

internal class SlotLayout(
    val w: Float,
    val h: Float,
    val machine: SlotRect,
    val win: SlotRect,
    val colW: Float,
    val gap: Float,
    val rowH: Float,
    val radius: Float,
    val pivotX: Float,
    val pivotY: Float,
    val arm: Float,
    val cardY: Float,
    /** Text scale for the strip (1 at the 393 × 852 design). */
    val k: Float,
)

internal class SlotArt(val layout: SlotLayout, val px: Float, val strip: Bitmap, val back: Bitmap, val front: Bitmap)

/** Each row turns this much of the cylinder (28°). */
private val ROW_ANGLE = (28 * PI / 180).toFloat()
internal const val SLOT_FRONT_PAD = 14f

internal fun slotLayout(w: Float, h: Float, top: Float, bottom: Float): SlotLayout {
    val headerH = 112f
    val cardH = 214f
    val avail = h - top - bottom - headerH - cardH - 36
    val winH = max(170f, min(290f, avail - 96))
    val mx = 14f
    val mw = w - mx - 50
    val machine = SlotRect(mx, top + headerH, mw, winH + 96)
    val win = SlotRect(mx + 18, machine.y + 70, mw - 36, winH)
    val gap = 5f
    val colW = (win.w - gap * 2) / 3
    // The window shows ±72° of the cylinder.
    val radius = winH / 2 / sin(72 * PI / 180).toFloat()
    val rowH = radius * ROW_ANGLE
    return SlotLayout(
        w, h, machine, win, colW, gap, rowH, radius,
        pivotX = w - 30, pivotY = win.y + win.h * 0.56f,
        arm = winH * 0.42f,
        cardY = machine.y + machine.h + 16,
        k = min(1f, rowH / 72),
    )
}

internal fun slotBakeArt(w: Float, h: Float, top: Float, bottom: Float, density: Float): SlotArt {
    val l = slotLayout(w, h, top, bottom)
    val px = min(3f, density)
    return SlotArt(l, px, bakeStrip(l, px), bakeBack(l, px), bakeFront(l, px))
}

private fun hash(n: Double): Double {
    val v = sin(n * 127.1 + 311.7) * 43758.5453
    return v - floor(v)
}

private fun argb(a: Float, r: Int, g: Int, b: Int) = android.graphics.Color.argb((a * 255).roundToInt(), r, g, b)

private fun withAlpha(c: Int, a: Float) = (c and 0x00FFFFFF) or ((a * 255).roundToInt() shl 24)

private fun fill(color: Long, alpha: Float = 1f) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    this.color = if (alpha < 1f) withAlpha(color.toInt(), alpha) else color.toInt()
}

private fun linear(x0: Float, y0: Float, x1: Float, y1: Float, colors: IntArray, pos: FloatArray?) =
    Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = LinearGradient(x0, y0, x1, y1, colors, pos, Shader.TileMode.CLAMP) }

private fun radial(x: Float, y: Float, r: Float, colors: IntArray, pos: FloatArray?) =
    Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = RadialGradient(x, y, r, colors, pos, Shader.TileMode.CLAMP) }

private fun Paint.blur(sigma: Float): Paint {
    maskFilter = BlurMaskFilter(((sigma - 0.5f) / 0.57735f).coerceAtLeast(0.01f), BlurMaskFilter.Blur.NORMAL)
    return this
}

private fun Paint.stroke(w: Float): Paint {
    style = Paint.Style.STROKE
    strokeWidth = w
    return this
}

private fun ints(vararg c: Long) = IntArray(c.size) { c[it].toInt() }

private fun rr(x: Float, y: Float, w: Float, h: Float) = RectF(x, y, x + w, y + h)

private fun bitmap(w: Float, h: Float, draw: (Canvas) -> Unit): Bitmap {
    val b = Bitmap.createBitmap(max(1, ceil(w).toInt()), max(1, ceil(h).toInt()), Bitmap.Config.ARGB_8888)
    draw(Canvas(b))
    b.prepareToDraw()
    return b
}

/** A sans face of [weight] (100–900); weights other than bold need API 28. */
private fun font(size: Float, weight: Int): Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    typeface = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        Typeface.create(Typeface.SANS_SERIF, weight, false)
    } else {
        Typeface.create(Typeface.SANS_SERIF, if (weight >= 600) Typeface.BOLD else Typeface.NORMAL)
    }
    textSize = size
}

private fun textWidth(f: Paint, s: String, track: Float = 0f) =
    if (s.isEmpty()) 0f else f.measureText(s) + track * max(0, s.length - 1)

/** Draws [s] centred on [cx] with its baseline at [y], each letter [track] dp apart. */
private fun centred(c: Canvas, s: String, cx: Float, y: Float, f: Paint, color: Int, track: Float = 0f) {
    f.color = color
    if (track == 0f) {
        c.drawText(s, cx - textWidth(f, s) / 2, y, f)
        return
    }
    var x = cx - textWidth(f, s, track) / 2
    for (ch in s) {
        val g = ch.toString()
        c.drawText(g, x, y, f)
        x += f.measureText(g) + track
    }
}

/** The three printed lists, side by side: colW × (ROWS · rowH) each, at [px] pixels per dp. */
private fun bakeStrip(l: SlotLayout, px: Float): Bitmap {
    val colW = l.colW
    val rowH = l.rowH
    val k = l.k
    val name = font(14.5f * k, 700)
    val meta = font(9.5f * k, 600)
    return bitmap(colW * 3 * px, SLOT_ROWS * rowH * px) { c ->
        c.scale(px, px)
        val grain = fill(0xFF8C6A4A, 0.06f)
        for (col in 0 until 3) {
            val x = col * colW
            for (r in 0 until SLOT_ROWS) {
                val y = r * rowH
                // Paper: cream, a touch warmer in alternate rows, with fibre grain.
                val paper = if (r % 2 == 1) ints(0xFFF7EFE2, 0xFFF1E6D4, 0xFFEADCC6) else ints(0xFFFAF4E9, 0xFFF4EBDC, 0xFFECE0CD)
                c.drawRect(x, y, x + colW, y + rowH, linear(0f, y, 0f, y + rowH, paper, floatArrayOf(0f, 0.6f, 1f)))
                for (g in 0 until 70) {
                    val gx = x + (hash((g + r * 91 + col * 7).toDouble()) * colW).toFloat()
                    val gy = y + (hash(g * 3.3 + r * 13 + col) * rowH).toFloat()
                    c.drawRect(gx, gy, gx + 1.2f, gy + 0.5f, grain)
                }
                val item = SlotMenu[col][r]
                val lines = item.name.split('\n')
                val lh = 16.5f * k
                val block = lines.size * lh + 14 * k
                var ty = y + (rowH - block) / 2 + 12.5f * k
                for (line in lines) {
                    centred(c, line, x + colW / 2, ty, name, 0xFF2B1A14.toInt())
                    ty += lh
                }
                centred(c, item.meta, x + colW / 2, ty + 2 * k, meta, 0xFFA0623C.toInt(), 1.1f)
                // The seam between rows: a fine dark line and a light one under it.
                c.drawRect(x, y, x + colW, y + 0.8f, fill(0xFFB89A78, 0.7f))
                c.drawRect(x, y + 0.8f, x + colW, y + 1.6f, fill(0xFFFFFFFF, 0.6f))
            }
        }
    }
}

/** The wall, the cabinet, its marquee, the reel labels and the lever's hub, at [px] pixels per dp. */
private fun bakeBack(l: SlotLayout, px: Float): Bitmap {
    val w = l.w
    val h = l.h
    val m = l.machine
    val win = l.win
    return bitmap(w * px, h * px) { c ->
        c.scale(px, px)
        // A warm dark wall, a pool of light behind the machine, a vignette, grain.
        c.drawRect(0f, 0f, w, h, linear(0f, 0f, 0f, h, ints(0xFF2A1A13, 0xFF1E140F, 0xFF140D0A), floatArrayOf(0f, 0.5f, 1f)))
        c.drawRect(0f, 0f, w, h, radial(w / 2, m.y + m.h * 0.45f, w * 0.95f, intArrayOf(argb(0.26f, 214, 120, 70), argb(0.08f, 150, 70, 40), argb(0f, 0, 0, 0)), floatArrayOf(0f, 0.55f, 1f)))
        c.drawRect(0f, 0f, w, h, radial(w / 2, h * 0.45f, h * 0.75f, intArrayOf(argb(0f, 0, 0, 0), argb(0f, 0, 0, 0), argb(0.55f, 6, 3, 2)), floatArrayOf(0f, 0.6f, 1f)))
        val warm = fill(0xFFFFE8D0, 0.035f)
        val dark = fill(0xFF000000, 0.035f)
        for (g in 0 until 4200) {
            val x = (hash(g * 1.3) * w).toFloat()
            val y = (hash(g * 2.7 + 5) * h).toFloat()
            c.drawRect(x, y, x + 0.8f, y + 0.8f, if (hash(g * 4.1) > 0.5) warm else dark)
        }

        // The cabinet: two shadows, cherry enamel with three stops, a chrome rim, an edge highlight.
        c.drawRoundRect(rr(m.x, m.y + 18, m.w, m.h), 32f, 32f, fill(0xFF000000, 0.45f).blur(22f))
        c.drawRoundRect(rr(m.x, m.y + 2, m.w, m.h), 32f, 32f, fill(0xFF000000, 0.5f).blur(3f))
        c.drawRoundRect(rr(m.x, m.y, m.w, m.h), 32f, 32f, linear(m.x, m.y, m.x + m.w * 0.3f, m.y + m.h, ints(0xFFD5463A, 0xFFA9232A, 0xFF7A141C), floatArrayOf(0f, 0.5f, 1f)))
        c.drawRoundRect(rr(m.x, m.y, m.w, m.h), 32f, 32f, radial(m.x + m.w * 0.3f, m.y + 10, m.w * 0.7f, intArrayOf(argb(0.32f, 255, 200, 170), argb(0f, 255, 170, 140)), null))
        c.drawRoundRect(rr(m.x + 1.5f, m.y + 1.5f, m.w - 3, m.h - 3), 30.5f, 30.5f, linear(0f, m.y, 0f, m.y + m.h, ints(0xFFFFF6EA, 0xFFC9B9A9, 0xFF8D7B6E, 0xFFE8DCCF), floatArrayOf(0f, 0.3f, 0.7f, 1f)).stroke(3f))
        c.drawRoundRect(rr(m.x + 4, m.y + 4, m.w - 8, m.h - 8), 28f, 28f, fill(0xFF3A0A0E, 0.55f).stroke(1f))

        // Marquee: a cream sign with small warm bulbs and the name.
        val sx = m.x + 18
        val sy = m.y + 14
        val sw = m.w - 36
        c.drawRoundRect(rr(sx, sy, sw, 30f), 15f, 15f, linear(0f, sy, 0f, sy + 30, ints(0xFFFFF4DF, 0xFFF1DDB8, 0xFFDCC092), floatArrayOf(0f, 0.6f, 1f)))
        c.drawRoundRect(rr(sx, sy, sw, 30f), 15f, 15f, fill(0xFF7A4A22, 0.5f).stroke(1f))
        for (b in 0 until 2) {
            val bx = if (b == 0) sx + 16 else sx + sw - 16
            c.drawCircle(bx, sy + 15, 9f, radial(bx, sy + 15, 9f, intArrayOf(argb(0.9f, 255, 214, 140), argb(0f, 255, 170, 80)), null))
            c.drawCircle(bx, sy + 15, 4f, radial(bx - 1, sy + 14, 4f, ints(0xFFFFFBEA, 0xFFFFC86A, 0xFFD88A2A), floatArrayOf(0f, 0.5f, 1f)))
        }
        centred(c, "SUPPER SLOTS", sx + sw / 2, sy + 20.5f, font(13f, 800), 0xFF7A1C1E.toInt(), 2.2f)

        // Labels over the reels.
        val lab = font(10f, 700)
        listOf("MAIN", "SIDE", "DRINK").forEachIndexed { i, s ->
            centred(c, s, win.x + i * (l.colW + l.gap) + l.colW / 2, win.y - 9, lab, argb(0.75f, 0xFF, 0xE3, 0xCF), 1.6f)
        }

        // The window's well, sunk into the enamel.
        c.drawRoundRect(rr(win.x - 6, win.y - 6, win.w + 12, win.h + 12), 16f, 16f, linear(0f, win.y - 6, 0f, win.y + win.h + 6, ints(0xFF5E0E14, 0xFF2C0709, 0xFF6F1A1E), floatArrayOf(0f, 0.5f, 1f)))
        c.drawRoundRect(rr(win.x - 6, win.y - 6, win.w + 12, win.h + 12), 16f, 16f, linear(0f, win.y, 0f, win.y + win.h, ints(0xFFF6EADB, 0xFF9C8A7C, 0xFFF0E2D2), floatArrayOf(0f, 0.5f, 1f)).stroke(2.2f))
        c.drawRoundRect(rr(win.x, win.y, win.w, win.h), 10f, 10f, fill(0xFF170A08))

        // Under the window: a small brass plaque.
        val py = win.y + win.h + 12
        c.drawRoundRect(rr(m.x + m.w / 2 - 54, py, 108f, 16f), 8f, 8f, linear(0f, py, 0f, py + 16, ints(0xFFF7D58C, 0xFFC8913C, 0xFF8E5E22), floatArrayOf(0f, 0.5f, 1f)))
        centred(c, "PULL TO PICK", m.x + m.w / 2, py + 11.5f, font(8.5f, 700), 0xFF4A2A0E.toInt(), 1.4f)

        // The lever's hub on the cabinet's flank: a chrome boss with a dark socket.
        val hx = l.pivotX
        val hy = l.pivotY
        c.drawRoundRect(rr(hx - 18, hy - 42, 26f, 84f), 13f, 13f, fill(0xFF000000, 0.45f).blur(6f))
        c.drawRoundRect(rr(hx - 16, hy - 40, 24f, 80f), 12f, 12f, linear(hx - 16, 0f, hx + 8, 0f, ints(0xFF6A1016, 0xFFB8323A, 0xFF5C0C12), floatArrayOf(0f, 0.45f, 1f)))
        c.drawCircle(hx, hy, 15f, fill(0xFF000000, 0.5f).blur(3f))
        c.drawCircle(hx, hy, 13f, radial(hx - 4, hy - 5, 16f, ints(0xFFFFFFFF, 0xFFC9CED2, 0xFF6B7176, 0xFF2C3034), floatArrayOf(0f, 0.3f, 0.75f, 1f)))
        c.drawCircle(hx, hy, 5f, fill(0xFF1A1C1F))
    }
}

/** Over the reels: the window's inner shadows, a glass streak, the payline's brass arrows. */
private fun bakeFront(l: SlotLayout, px: Float): Bitmap {
    val win = l.win
    val pad = SLOT_FRONT_PAD
    return bitmap((win.w + pad * 2) * px, (win.h + pad * 2) * px) { c ->
        c.scale(px, px)
        c.translate(pad, pad)
        c.save()
        c.clipPath(Path().apply { addRoundRect(rr(0f, 0f, win.w, win.h), 10f, 10f, Path.Direction.CW) })
        c.drawRect(0f, 0f, win.w, 26f, linear(0f, 0f, 0f, 26f, intArrayOf(argb(0.75f, 20, 6, 4), argb(0f, 20, 6, 4)), null))
        c.drawRect(0f, win.h - 30, win.w, win.h, linear(0f, win.h - 30, 0f, win.h, intArrayOf(argb(0f, 20, 6, 4), argb(0.8f, 20, 6, 4)), null))
        // Glass: a soft diagonal streak and a sharp thin one.
        c.save()
        c.rotate(-24f, win.w / 2, win.h / 2)
        c.drawRect(-win.w, win.h * 0.12f, win.w * 2, win.h * 0.28f, fill(0xFFFFFFFF, 0.09f).blur(16f))
        c.drawRect(-win.w, win.h * 0.33f, win.w * 2, win.h * 0.33f + 2.5f, fill(0xFFFFFFFF, 0.12f).blur(1.5f))
        c.restore()
        c.restore()
        // Payline arrows on both sides, pointing in.
        val y = win.h / 2
        for (side in intArrayOf(-1, 1)) {
            val x = if (side < 0) -5f else win.w + 5
            val tri = Path().apply {
                moveTo(x - side * 7, y - 8)
                lineTo(x + side * 4, y)
                lineTo(x - side * 7, y + 8)
                close()
            }
            c.drawPath(tri, fill(0xFF000000, 0.5f).blur(2f))
            c.drawPath(tri, linear(0f, y - 8, 0f, y + 8, ints(0xFFFFE6A6, 0xFFE0A548, 0xFF9B6522), floatArrayOf(0f, 0.5f, 1f)))
        }
    }
}

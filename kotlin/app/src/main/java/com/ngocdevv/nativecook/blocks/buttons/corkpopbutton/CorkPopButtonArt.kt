package com.ngocdevv.nativecook.blocks.buttons.corkpopbutton

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.graphics.Typeface
import android.os.Build
import com.ngocdevv.nativecook.blocks.buttons.corkpopbutton.CorkGfx.Companion.linear
import com.ngocdevv.nativecook.blocks.buttons.corkpopbutton.CorkGfx.Companion.oval
import com.ngocdevv.nativecook.blocks.buttons.corkpopbutton.CorkGfx.Companion.radial
import com.ngocdevv.nativecook.blocks.buttons.corkpopbutton.CorkMotion.hash
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.coroutineContext
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/*
 Cork Pop Button — the drawing, ported from corkArt.ts. Baked once (off the main thread): the room
 (emerald night, a warm spot behind the bottle, vignette, grain), the order card with its text,
 the bottle (green glass with specular strips, a crinkled gold foil with a scalloped tear edge, the
 cream "Celebrate" label on the shoulder), the cork (mushroom and shaft) and one gold bubble. Per
 frame: the card's lift and sheen, the bottle's drop, the cork's inch and rock, the wire cage (legs
 bow and the key spins as it loosens), the foam (AGSL; gradient balls below API 33), the CO2 puff,
 the flying cork and the bubbles.
*/

internal class CorkFonts {
    private fun font(base: Typeface, weight: Int, size: Float, italic: Boolean = false) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) Typeface.create(base, weight, italic)
        else Typeface.create(base, (if (weight >= 600) Typeface.BOLD else Typeface.NORMAL) or (if (italic) Typeface.ITALIC else 0))
        textSize = size
    }
    val kicker = font(Typeface.DEFAULT, 700, 11f)
    val mono = font(Typeface.MONOSPACE, 700, 22f)
    val row = font(Typeface.DEFAULT, 400, 15f)
    val rowBold = font(Typeface.DEFAULT, 600, 15f)
    val script = font(Typeface.SERIF, 400, 27f, italic = true)
    val tiny = font(Typeface.DEFAULT, 700, 10f)
}

internal class CorkLayout(val w: Float, val h: Float, top: Float) {
    val cardX = 20f
    val cardY = top + 108
    val cardW = w - 40
    val cardH = if (h < 740) 128f else 148f
    val hintY = cardY + cardH + 18
    val lipY = cardY + cardH + if (h < 740) 100 else 128
    private val avail = h - lipY
    val neckLen = max(110f, min(180f, avail * 0.42f))
    val shoulder = max(80f, min(120f, avail * 0.3f))
    val cx = w / 2
    val neck = 27f
    val body = min(w * 0.43f, 190f)
    val corkW = 25f
    val corkH = 30f
    val labelY = lipY + neckLen + shoulder * 0.86f
    val geo = CorkGeo(w.toDouble(), h.toDouble(), cx.toDouble(), lipY.toDouble(), neck.toDouble(), neckLen.toDouble(), body.toDouble(), corkW.toDouble(), corkH.toDouble())

    /** Half width of the bottle at height y (from the lip down). */
    fun bottleHalf(y: Float): Float {
        val d = y - lipY
        if (d < 10) return neck + 3
        if (d < neckLen) return neck + 6 * (d - 10) / (neckLen - 10)
        val u = min(1f, (d - neckLen) / shoulder)
        val s = u * u * (3 - 2 * u)
        return neck + 6 + (body - neck - 6) * s
    }
}

internal class CorkLayers(val bg: CorkBaked, val card: CorkBaked, val bottle: CorkBaked, val cork: CorkBaked, val bubble: CorkBaked)

internal object CorkPopButtonArt {
    private fun drawRoom(c: Canvas, g: CorkLayout, gx: CorkGfx) {
        val w = g.w
        val h = g.h
        val full = RectF(0f, 0f, w, h)
        c.drawRect(full, gx.shaded(linear(0f, 0f, 0f, h, arrayOf("#0F201B", "#112A22", "#0C1E18", "#08130F"), floatArrayOf(0f, 0.4f, 0.75f, 1f))))
        c.drawRect(full, gx.shaded(radial(g.cx, g.lipY + 40, w * 0.95f, arrayOf("rgba(214,170,96,0.32)", "rgba(120,110,70,0.12)", "rgba(20,40,32,0)"), floatArrayOf(0f, 0.5f, 1f))))
        // A faint diamond trellis, like the shop's wrapping paper.
        for (i in -12 until 24) {
            val x = i * 34f
            c.drawLine(x, 0f, x + h * 0.5f, h, gx.stroke("#E8D3A0", 0.6f, 0.03))
            c.drawLine(x + h * 0.5f, 0f, x, h, gx.stroke("#E8D3A0", 0.6f, 0.03))
        }
        c.drawRect(full, gx.shaded(radial(w / 2, h * 0.5f, hypot(w, h) * 0.6f, arrayOf("rgba(4,10,8,0)", "rgba(4,10,8,0.3)", "rgba(3,8,6,0.7)"), floatArrayOf(0f, 0.6f, 1f))))
        CorkGrain(7).draw(c, full, 0.07)
    }

    private fun drawCard(c: Canvas, g: CorkLayout, f: CorkFonts, gx: CorkGfx) {
        val x = g.cardX
        val y = g.cardY
        val w = g.cardW
        val h = g.cardH
        val rect = RectF(x, y, x + w, y + h)
        c.drawRoundRect(rect, 22f, 22f, gx.shaded(linear(x, y, x + w * 0.4f, y + h, arrayOf("#FBF5E8", "#F3EBD8", "#E9DEC6"), floatArrayOf(0f, 0.55f, 1f))))
        c.save()
        c.clipPath(Path().apply { addRoundRect(rect, 22f, 22f, Path.Direction.CW) })
        CorkGrain(11).draw(c, rect, 0.08)
        c.restore()
        // A gold deckle line inside the edge.
        c.drawRoundRect(x + 6, y + 6, x + w - 6, y + h - 6, 17f, 17f, gx.stroke("#C9A35A", 0.8f, 0.55))
        c.drawRoundRect(x + 0.5f, y + 0.5f, x + w - 0.5f, y + h - 0.5f, 21.5f, 21.5f, gx.stroke("#FFFFFF", 1f, 0.6))
        val small = h < 140
        val left = x + 22
        val right = x + w - 22
        fun text(s: String, tx: Float, ty: Float, color: String, font: Paint, alpha: Double = 1.0) {
            font.color = corkColor(color)
            if (alpha < 1) font.alpha = (255 * alpha).toInt()
            c.drawText(s, tx, ty, font)
        }
        text("ORDER", left, y + if (small) 30 else 34, "#A87F33", f.kicker)
        text("MV-20481", left, y + if (small) 54 else 60, "#16302A", f.mono)
        // "Confirmed" pill.
        val label = "Confirmed"
        val lw = f.tiny.measureText(label) + 26
        val py = y + if (small) 22 else 26
        c.drawRoundRect(right - lw, py, right, py + 24, 12f, 12f, gx.paint("#1F5B45", 0.12))
        c.drawCircle(right - lw + 11, py + 12, 3f, gx.paint("#2C7A5C"))
        text(label, right - lw + 18, py + 16.5f, "#1F5B45", f.tiny)
        val div = y + if (small) 68 else 78
        c.drawLine(left, div, right, div, gx.stroke("#C9B48A", 1f, 0.5))
        val r1 = div + if (small) 24 else 28
        val r2 = r1 + if (small) 24 else 28
        text("Cuvée Lumière Brut × 2", left, r1, "#16302A", f.row)
        val p1 = "€96.00"
        text(p1, right - f.rowBold.measureText(p1), r1, "#16302A", f.rowBold)
        text("Delivery · Thu 30 Oct", left, r2, "#5E6E66", f.row)
        val p2 = "Free"
        text(p2, right - f.row.measureText(p2), r2, "#5E6E66", f.row)
    }

    private fun bottlePath(g: CorkLayout): Path {
        val b = Path()
        val top = g.lipY
        val bottom = g.h + 60
        val ht = g.bottleHalf(top)
        b.moveTo(g.cx - ht, top + 4)
        b.quadTo(g.cx - ht, top, g.cx - ht + 4, top)
        b.lineTo(g.cx + ht - 4, top)
        b.quadTo(g.cx + ht, top, g.cx + ht, top + 4)
        var y = top + 6
        while (y <= bottom) {
            b.lineTo(g.cx + g.bottleHalf(y), y)
            y += 6
        }
        y = bottom
        while (y >= top + 6) {
            b.lineTo(g.cx - g.bottleHalf(y), y)
            y -= 6
        }
        b.close()
        return b
    }

    private fun drawBottle(c: Canvas, g: CorkLayout, f: CorkFonts, gx: CorkGfx) {
        val cx = g.cx
        val lipY = g.lipY
        val glass = bottlePath(g)
        val bb = g.body
        c.drawPath(glass, gx.shaded(linear(cx - bb, 0f, cx + bb, 0f, arrayOf("#03100B", "#0B3325", "#1C5A40", "#0E3B2B", "#062015", "#020A07"), floatArrayOf(0f, 0.2f, 0.36f, 0.55f, 0.82f, 1f))))
        c.save()
        c.clipPath(glass)
        // Light from the spot through the glass: a glow low in the body.
        c.drawCircle(cx + bb * 0.15f, g.labelY + 160, bb * 0.9f, gx.blurred("#3E8F63", 0.35, 40f))
        // Specular strips that follow the bottle's outline.
        val strips = arrayOf(floatArrayOf(-0.55f, 0.45f, 5f), floatArrayOf(-0.42f, 0.18f, 2f), floatArrayOf(0.62f, 0.14f, 7f))
        for (st in strips) {
            val k = st[0]
            val wdt = st[2]
            val s = Path()
            var y = lipY + 8
            var first = true
            while (y < g.h + 60) {
                val x = cx + g.bottleHalf(y) * k
                if (first) s.moveTo(x, y) else s.lineTo(x, y)
                first = false
                y += 6
            }
            val p = gx.stroke("#E9FFF2", wdt, st[1].toDouble())
            p.maskFilter = gx.blur(wdt * 0.5f)
            c.drawPath(s, p)
        }
        c.restore()
        // The foil: from the lip down 85 % of the neck, with a scalloped tear edge.
        val foilEnd = lipY + g.neckLen * 0.85f
        val foil = Path()
        foil.moveTo(cx - g.bottleHalf(lipY) - 1.5f, lipY - 1)
        foil.lineTo(cx + g.bottleHalf(lipY) + 1.5f, lipY - 1)
        var y = lipY
        while (y <= foilEnd) {
            foil.lineTo(cx + g.bottleHalf(y) + 1.5f, y)
            y += 4
        }
        val half = g.bottleHalf(foilEnd) + 1.5f
        val teeth = 9
        for (i in teeth downTo 0) {
            val x = cx - half + 2 * half * i / teeth
            foil.lineTo(x, foilEnd + if (i % 2 == 0) 0 else 7)
        }
        y = foilEnd
        while (y >= lipY) {
            foil.lineTo(cx - g.bottleHalf(y) - 1.5f, y)
            y -= 4
        }
        foil.close()
        c.drawPath(foil, gx.shaded(linear(cx - half, 0f, cx + half, 0f, arrayOf("#5E4317", "#B89042", "#F7E3A6", "#D5B062", "#8E6A2A", "#4C3612"), floatArrayOf(0f, 0.22f, 0.38f, 0.55f, 0.8f, 1f))))
        c.save()
        c.clipPath(foil)
        // Crinkles: short light and dark strokes, seeded.
        for (i in 0 until 90) {
            val cy = lipY + hash(i + 3).toFloat() * (foilEnd - lipY + 6)
            val x = cx + (hash(i + 7).toFloat() - 0.5f) * 2 * g.bottleHalf(cy)
            val a = (hash(i + 11) - 0.5) * 1.2
            val l = 4 + 9 * hash(i + 13)
            c.drawLine(x, cy, x + (cos(a) * l).toFloat(), cy + (sin(a) * l).toFloat(), gx.stroke(if (i % 2 == 0) "#FFF3C8" else "#3E2A0C", 0.7f, 0.32))
        }
        // The neck band: an embossed ring with the house's initials.
        val band = lipY + g.neckLen * 0.45f
        c.drawRect(cx - 60, band - 9, cx + 60, band + 9, gx.paint("#2A1C06", 0.25))
        c.drawLine(cx - 60, band - 9, cx + 60, band - 9, gx.stroke("#FFF1C4", 0.8f, 0.5))
        c.drawLine(cx - 60, band + 9, cx + 60, band + 9, gx.stroke("#2A1C06", 0.8f, 0.5))
        val mv = "M  V"
        f.tiny.color = corkColor("#3B2A0C")
        f.tiny.alpha = (255 * 0.8).toInt()
        c.drawText(mv, cx - f.tiny.measureText(mv) / 2, band + 4.5f, f.tiny)
        c.restore()
        // The label on the shoulder: the call to action.
        val ly = g.labelY
        val lw = min(214f, g.body * 1.16f)
        val lh = 66f
        c.drawRoundRect(cx - lw / 2, ly - lh / 2 + 4, cx + lw / 2, ly + lh / 2 + 4, lh / 2, lh / 2, gx.blurred("#010503", 0.5, 8f))
        c.drawRoundRect(cx - lw / 2, ly - lh / 2, cx + lw / 2, ly + lh / 2, lh / 2, lh / 2, gx.shaded(linear(cx - lw / 2, 0f, cx + lw / 2, 0f, arrayOf("#CFC3A6", "#F7F0DE", "#FBF6EA", "#E8DDC2", "#BBAE8E"), floatArrayOf(0f, 0.25f, 0.45f, 0.75f, 1f))))
        c.drawRoundRect(cx - lw / 2 + 5, ly - lh / 2 + 5, cx + lw / 2 - 5, ly + lh / 2 - 5, lh / 2 - 5, lh / 2 - 5, gx.stroke("#B08A3E", 1.1f, 0.9))
        val cap = "CUVÉE LUMIÈRE · BRUT"
        f.tiny.color = corkColor("#9A7631")
        c.drawText(cap, cx - f.tiny.measureText(cap) / 2, ly - 11, f.tiny)
        val cta = "Celebrate"
        f.script.color = corkColor("#163A2D")
        c.drawText(cta, cx - f.script.measureText(cta) / 2, ly + 17, f.script)
    }

    private fun drawCork(c: Canvas, g: CorkLayout, gx: CorkGfx) {
        val cx = g.cx
        val lipY = g.lipY
        val corkW = g.corkW
        val corkH = g.corkH
        val top = lipY - corkH
        // The shaft, which shows as the cork inches out of the neck.
        c.drawRect(cx - 17, lipY - 4, cx + 17, lipY + 26, gx.shaded(linear(cx - 17, 0f, cx + 17, 0f, arrayOf("#9C7646", "#E2C796", "#D2B07A", "#8E6939"))))
        for (i in 0 until 14) c.drawCircle(cx + (hash(i + 90).toFloat() - 0.5f) * 30, lipY + hash(i + 94).toFloat() * 26, 0.7f + hash(i + 98).toFloat(), gx.paint("#6C4B24", 0.5))
        // The mushroom.
        val m = Path().apply {
            moveTo(cx - corkW + 3, lipY + 2)
            cubicTo(cx - corkW - 2, top + corkH * 0.55f, cx - corkW * 0.9f, top, cx, top)
            cubicTo(cx + corkW * 0.9f, top, cx + corkW + 2, top + corkH * 0.55f, cx + corkW - 3, lipY + 2)
            close()
        }
        c.drawPath(m, gx.shaded(linear(cx - corkW, 0f, cx + corkW, 0f, arrayOf("#7E5A2E", "#C9A471", "#E3C693", "#B48A54", "#6B4A22"), floatArrayOf(0f, 0.25f, 0.42f, 0.7f, 1f))))
        c.save()
        c.clipPath(m)
        for (i in 0 until 40) {
            val x = cx + (hash(i + 30).toFloat() - 0.5f) * corkW * 2
            val y = top + hash(i + 34).toFloat() * corkH
            c.drawOval(x, y, x + 1 + 2 * hash(i + 38).toFloat(), y + 0.8f + hash(i + 39).toFloat(), gx.paint("#5A3C18", 0.45))
        }
        c.drawRect(cx - corkW - 4, top, cx + corkW + 4, top + corkH + 4, gx.shaded(linear(0f, top, 0f, lipY, arrayOf("rgba(255,240,210,0.25)", "rgba(0,0,0,0)", "rgba(40,20,5,0.35)"), floatArrayOf(0f, 0.4f, 1f))))
        c.restore()
    }

    private fun drawBubble(c: Canvas, r: Float, gx: CorkGfx) {
        c.drawCircle(0f, 0f, r, gx.shaded(radial(0f, 0f, r, arrayOf("rgba(255,232,170,0.04)", "rgba(255,220,140,0.12)", "rgba(232,184,92,0.55)", "rgba(255,236,186,0.95)"), floatArrayOf(0f, 0.6f, 0.88f, 1f))))
        c.drawCircle(-r * 0.36f, -r * 0.4f, r * 0.22f, gx.blurred("#FFFFFF", 0.85, r * 0.1f))
        c.drawArc(RectF(-r * 0.7f, -r * 0.7f, r * 0.7f, r * 0.7f), 20f, 70f, false, gx.stroke("#FFF6DA", r * 0.08f, 0.6))
    }

    suspend fun bakeLayers(g: CorkLayout, f: CorkFonts, scale: Float): CorkLayers {
        val ctx = coroutineContext
        val gx = CorkGfx(live = false)
        val bg = CorkGfx.bake(0f, 0f, g.w, g.h, scale) { drawRoom(it, g, gx) }
        ctx.ensureActive()
        val card = CorkGfx.bake(g.cardX - 2, g.cardY - 2, g.cardW + 4, g.cardH + 4, scale) { drawCard(it, g, f, gx) }
        ctx.ensureActive()
        val bx = g.cx - g.body - 30
        val bottle = CorkGfx.bake(bx, g.lipY - 10, (g.body + 30) * 2, g.h + 80 - g.lipY, scale) { drawBottle(it, g, f, gx) }
        ctx.ensureActive()
        val cork = CorkGfx.bake(g.cx - g.corkW - 6, g.lipY - g.corkH - 4, g.corkW * 2 + 12, g.corkH + 34, scale) { drawCork(it, g, gx) }
        val bubble = CorkGfx.bake(-24f, -24f, 48f, 48f, scale) { drawBubble(it, 22f, gx) }
        return CorkLayers(bg, card, bottle, cork, bubble)
    }
}

/** Draws a frame on the screen's canvas (in dp) from the baked layers and a pose. */
internal class CorkPainter {
    private val gx = CorkGfx(live = true)
    private val image = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val bubblePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val foamPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val dst = RectF()
    private val path = Path()
    private var shader: RuntimeShader? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        runCatching { RuntimeShader(CORK_FOAM_AGSL) }.getOrNull()
    } else {
        null
    }

    /** The placeholder before the art is baked: the room's colour, the card and the bottle in flat shapes. */
    fun placeholder(c: Canvas, g: CorkLayout) {
        c.drawColor(corkColor("#0C1E18"))
        c.drawRoundRect(g.cardX, g.cardY, g.cardX + g.cardW, g.cardY + g.cardH, 22f, 22f, gx.paint("#F3EBD8"))
        c.drawRect(g.cx - g.neck - 3, g.lipY, g.cx + g.neck + 3, g.lipY + g.neckLen, gx.paint("#B89042"))
        c.drawRoundRect(g.cx - g.body, g.lipY + g.neckLen + g.shoulder * 0.5f, g.cx + g.body, g.h + 60, g.body * 0.6f, g.body * 0.6f, gx.paint("#0B3325"))
    }

    /** The wire cage: a cap plate on the cork, four legs to a ring under the lip, the twisted key. */
    private fun drawCage(c: Canvas, g: CorkLayout, inch: Float, loose: Float, rock: Float, dy: Float) {
        val cx = g.cx
        val corkW = g.corkW
        val top = g.lipY - g.corkH - inch + dy
        val ringY = g.lipY + 9 + loose * 2 + dy
        val ringR = g.neck + 3.5f
        val plateR = corkW * 0.66f
        val bow = -1 + loose * 4
        // Back legs first, then the ring, then the front legs.
        val legs = floatArrayOf(-0.96f, 0.96f, -0.5f, 0.5f)
        for (k in 0 until 4) {
            val s = legs[k]
            path.reset()
            path.moveTo(cx + s * plateR, top + 3)
            path.quadTo(cx + s * (corkW + bow), top + g.corkH * 0.55f, cx + s * ringR, ringY)
            c.drawPath(path, if (k < 2) gx.stroke("#7E878E", 0.9f, 0.55) else gx.stroke("#DDE3E7", 1.1f, 0.9))
            if (k == 1) c.drawArc(RectF(cx - ringR, ringY - 3.5f, cx + ringR, ringY + 3.5f), 0f, 180f, false, gx.stroke("#C9D0D5", 1.6f))
        }
        // The key: a twisted loop hanging at the front of the ring, spinning as it loosens.
        val spin = loose * PI * 6 + rock * 0.05
        val kw = 1 + 4 * abs(cos(spin)).toFloat()
        c.drawLine(cx, ringY + 3, cx, ringY + 7, gx.stroke("#E9EDF0", 1.6f))
        c.drawOval(cx - kw, ringY + 7, cx + kw, ringY + 16, gx.stroke("#F2F5F7", 1.4f))
        // The cap plate on top with its embossed ring.
        c.drawOval(cx - plateR, top - 1.5f, cx + plateR, top + 5.5f, gx.shaded(linear(cx - plateR, 0f, cx + plateR, 0f, arrayOf("#8C6A2C", "#F3D88E", "#C49A48", "#7A5A22"))))
        c.drawOval(cx - plateR * 0.6f, top + 0.2f, cx + plateR * 0.6f, top + 3.8f, gx.stroke("#FFF1C2", 0.7f, 0.7))
        val mark = rock * 0.12 + loose * 2.4
        c.drawCircle(cx + cos(mark).toFloat() * plateR * 0.75f, top + 2 + sin(mark).toFloat() * 2, 1f, gx.paint("#5A4012", 0.8))
    }

    /** Below API 33 (or if the shader fails): each foam ball as a lit cream sphere. */
    private fun drawFoamFallback(c: Canvas, p: CorkPose) {
        for (j in 0 until CORK_BALLS) {
            val x = p.balls[j * 3]
            val y = p.balls[j * 3 + 1]
            val r = p.balls[j * 3 + 2]
            if (r < 0.5f) continue
            foamPaint.shader = RadialGradient(
                x - r * 0.35f, y - r * 0.4f, r * 1.5f,
                intArrayOf(corkColor("#FCF6E6"), corkColor("#F3E8CF"), corkColor("#E6CC8C")),
                floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP,
            )
            c.drawCircle(x, y, r * 1.05f, foamPaint)
        }
        foamPaint.shader = null
    }

    fun frame(c: Canvas, g: CorkLayout, l: CorkLayers, p: CorkPose) {
        image.alpha = 255
        l.bg.draw(c, image)
        // The card, lifted.
        val lift = p.lift.toFloat()
        val cardDY = -12 * lift
        c.drawRoundRect(g.cardX, g.cardY + 2, g.cardX + g.cardW, g.cardY + 2 + g.cardH, 22f, 22f, gx.blurred("#020604", 0.4 - 0.15 * lift, 3 + 2 * lift))
        c.drawRoundRect(g.cardX + 6, g.cardY + 16 + 10 * lift, g.cardX + g.cardW - 6, g.cardY + 16 + 10 * lift + g.cardH, 22f, 22f, gx.blurred("#020604", 0.35 + 0.2 * lift, 18 + 14 * lift))
        c.save()
        val s = 1 + 0.015f * lift
        c.translate(g.cx, g.cardY + g.cardH / 2 + cardDY)
        c.scale(s, s)
        c.translate(-g.cx, -(g.cardY + g.cardH / 2))
        l.card.draw(c, image)
        val sheen = p.sheen.toFloat()
        if (sheen > 0 && sheen < 1) {
            c.save()
            path.reset()
            path.addRoundRect(RectF(g.cardX, g.cardY, g.cardX + g.cardW, g.cardY + g.cardH), 22f, 22f, Path.Direction.CW)
            c.clipPath(path)
            val x = g.cardX - 120 + (g.cardW + 240) * sheen
            val sh = gx.shaded(linear(x - 60, g.cardY, x + 60, g.cardY + g.cardH, arrayOf("rgba(255,230,160,0)", "rgba(255,226,150,0.5)", "rgba(255,255,255,0.35)", "rgba(255,230,160,0)"), floatArrayOf(0f, 0.45f, 0.55f, 1f)))
            sh.xfermode = CorkGfx.plus
            c.drawRect(g.cardX, g.cardY, g.cardX + g.cardW, g.cardY + g.cardH, sh)
            c.restore()
        }
        c.restore()
        // Cork under the bottle (its shaft hides in the neck), the bottle, the cage.
        val dy = p.drop.toFloat()
        val inch = p.inch.toFloat()
        val rock = p.rock.toFloat()
        c.save()
        c.translate(g.cx, g.lipY + dy)
        c.rotate(rock * 0.8f)
        c.translate(-g.cx, -g.lipY)
        if (p.corked) l.cork.draw(c, image, 0f, -inch)
        c.restore()
        l.bottle.draw(c, image, 0f, dy)
        if (p.corked) drawCage(c, g, inch, p.loose.toFloat(), rock, dy)
        // Foam.
        if (p.bounds[2] > 0) {
            val sh = shader
            var drawn = false
            if (sh != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                drawn = runCatching {
                    sh.setFloatUniform("balls", p.balls)
                    foamPaint.shader = sh
                    c.drawRect(p.bounds[0].toFloat(), p.bounds[1].toFloat(), (p.bounds[0] + p.bounds[2]).toFloat(), (p.bounds[1] + p.bounds[3]).toFloat(), foamPaint)
                    foamPaint.shader = null
                    true
                }.getOrElse {
                    shader = null
                    false
                }
            }
            if (!drawn) drawFoamFallback(c, p)
        }
        // CO2 puff.
        for (i in 0 until p.fogCount) {
            val o = i * 4
            val a = p.fog[o + 3]
            if (a > 0.005) {
                val r = p.fog[o + 2].toFloat()
                c.drawCircle(p.fog[o].toFloat(), p.fog[o + 1].toFloat(), r, gx.blurred("#E6F1EC", a, r * 0.45f))
            }
        }
        // The cork in flight, with its cap.
        if (p.hasCork) {
            c.save()
            c.translate(p.cork[0].toFloat(), p.cork[1].toFloat())
            c.rotate(p.cork[2].toFloat())
            c.translate(-g.cx, -(g.lipY - g.corkH * 0.5f))
            l.cork.draw(c, image)
            drawCage(c, g, 0f, 1f, 0f, 0f)
            c.restore()
        }
        // Gold bubbles.
        for (i in 0 until p.bubbleCount) {
            val o = i * 5
            val r = p.bubbles[o + 2].toFloat()
            val x = p.bubbles[o].toFloat()
            val y = p.bubbles[o + 1].toFloat()
            bubblePaint.alpha = (255 * p.bubbles[o + 3]).toInt().coerceIn(0, 255)
            dst.set(x - r * 1.09f, y - r * 1.09f, x + r * 1.09f, y + r * 1.09f)
            c.drawBitmap(l.bubble.bmp, null, dst, bubblePaint)
        }
        // A soft touch marker while the loop twists the cork.
        val finger = p.finger
        if (finger > 0.01) {
            val fy = g.lipY - g.corkH * 0.55f - inch
            c.drawCircle(g.cx + rock * 1.6f, fy, 24f, gx.blurred("#FFFFFF", 0.16 * finger, 6f))
            c.drawCircle(g.cx + rock * 1.6f, fy, 22f, gx.stroke("#FFFFFF", 1.2f, 0.35 * finger))
        }
    }
}

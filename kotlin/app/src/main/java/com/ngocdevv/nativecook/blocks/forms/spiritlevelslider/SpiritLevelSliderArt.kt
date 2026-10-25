package com.ngocdevv.nativecook.blocks.forms.spiritlevelslider

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import com.ngocdevv.nativecook.blocks.forms.spiritlevelslider.SpiritMotion.PITCH_WORDS
import com.ngocdevv.nativecook.blocks.forms.spiritlevelslider.SpiritMotion.clamp
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sign
import kotlin.math.sin
import kotlin.math.tan

/*
 The spirit level's art. Baked once: the warm paper background, the pitch card with the house's
 walls, the level itself (anodised yellow body, machined end caps, etched ruler, the vial window
 with its lime liquid and centre marks) and the level's soft shadow. Per frame: the roof at the
 current pitch, the readouts, the shadow and level bitmaps under the rotation, the bubble and the
 vial's glass.
 */

internal const val SPIRIT_INK = 0x2a241c
private const val LIME = 0xc6e23f

/** Margin around the baked level, in points. */
internal const val SPIRIT_PAD = 24f

internal class SpiritFonts {
    val kicker = spiritFont(false, 11f, 700)
    val degrees = spiritFont(false, 58f, 700)
    val rise = spiritFont(false, 17f, 600)
    val pill = spiritFont(false, 13f, 600)
    val etch = spiritFont(false, 7f, 700)
    val degreesW: Float = (0..9).maxOf { degrees.measureText("$it") }
    val riseW: Float = (0..9).maxOf { rise.measureText("$it") }
}

internal class SpiritRect(val x: Float, val y: Float, val w: Float, val h: Float)

internal class SpiritGeo(val w: Float, val h: Float, top: Float, bottom: Float) {
    val card: SpiritRect
    /** The house: centre x, ground y, half width, wall height. */
    val houseX: Float
    val houseY: Float
    val houseHw: Float
    val houseWh: Float
    val cx = w / 2
    val cy: Float
    /** Level length and body thickness, and the vial's inner half length and bubble size. */
    val len: Float
    val thick: Float
    val vialHalf: Float
    val bubbleLen: Float
    val bubbleH: Float
    val travel: Float
    val chipsTop: Float
    val ctaTop: Float

    init {
        val cardY = top + 150
        val cardH = clamp(h * 0.2, 140.0, 176.0).toFloat()
        card = SpiritRect(16f, cardY, w - 32, cardH)
        ctaTop = h - max(bottom, 16f) - 8 - 56
        chipsTop = ctaTop - 16 - 40
        val heroTop = cardY + cardH + 12
        val heroBottom = chipsTop - 12
        val heroH = heroBottom - heroTop
        val sin44 = sin(44 * PI / 180).toFloat()
        len = max(200f, min(min(w - 56, 340f), (heroH - 56) / sin44))
        thick = len * 0.135f
        vialHalf = (len * 0.44f - 12) / 2
        bubbleLen = thick * 0.74f
        val hw = min(card.w * 0.25f, 86f)
        houseX = card.x + card.w * 0.7f
        houseY = card.y + card.h - 22
        houseHw = hw
        houseWh = card.h * 0.24f
        cy = (heroTop + heroBottom) / 2
        bubbleH = thick * 0.3f
        travel = vialHalf - bubbleLen / 2 - 3
    }

    /** Size of the baked level, in points. */
    val boxW get() = len + SPIRIT_PAD * 2
    val boxH get() = thick * 1.18f + SPIRIT_PAD * 2

    /** The tube's centre line height (local y) at local x: arched, highest in the middle. */
    fun tubeY(x: Float): Float {
        val u = x / vialHalf
        return -thick * 0.02f + 3.2f * u * u
    }
}

private fun c(vararg v: Pair<Int, Float>) = spiritColors(*v)
private fun f(vararg v: Float) = v

private fun dashed(p: Paint, on: Float, off: Float): Paint {
    p.pathEffect = DashPathEffect(floatArrayOf(on, off), 0f)
    return p
}

// ——— Baked ———

internal fun spiritDrawBackground(canvas: Canvas, k: SpiritKit, g: SpiritGeo, ft: SpiritFonts) {
    canvas.drawRect(0f, 0f, g.w, g.h, k.linear(0f, 0f, 0f, g.h, c(0xf5f0e6 to 1f, 0xece4d5 to 1f, 0xdfd5c2 to 1f), f(0f, 0.55f, 1f)))
    canvas.drawRect(0f, 0f, g.w, g.h, k.radial(g.cx, g.cy, g.w * 0.9f, c(0xfffaf0 to 0.55f, 0xfffaf0 to 0f), f(0f, 1f)))
    k.grain(canvas, 0f, 0f, g.w, g.h, 0.09f, 7)
    val card = g.card
    canvas.drawRoundRect(card.x, card.y + 12, card.x + card.w, card.y + 12 + card.h, 24f, 24f, k.fill(0x5a4a30, 0.14f, 18f))
    canvas.drawRoundRect(card.x, card.y + 1.5f, card.x + card.w, card.y + 1.5f + card.h, 24f, 24f, k.fill(0x5a4a30, 0.18f, 1.5f))
    canvas.drawRoundRect(card.x, card.y, card.x + card.w, card.y + card.h, 24f, 24f, k.linear(0f, card.y, 0f, card.y + card.h, c(0xfffdf8 to 1f, 0xfbf7ef to 1f, 0xf4eee2 to 1f), f(0f, 0.6f, 1f)))
    canvas.drawRoundRect(card.x, card.y, card.x + card.w, card.y + card.h, 24f, 24f, k.stroke(0xffffff, 1f, 0.9f))
    k.spaced(canvas, "PITCH", card.x + 20, card.y + 28, ft.kicker, SPIRIT_INK, 0.45f, 1.8f)
    // Ground, a hedge and the walls: everything of the house but its roof.
    val gy = g.houseY
    canvas.drawLine(card.x + card.w * 0.42f, gy, card.x + card.w - 18, gy, k.stroke(SPIRIT_INK, 1.2f, 0.25f))
    for (i in 0 until 7) {
        val x = g.houseX + g.houseHw + 12 + i * 7
        if (x > card.x + card.w - 22) break
        canvas.drawCircle(x, gy - 6 - (i % 2) * 2, 7f, k.fill(if (i % 2 == 1) 0x7c8f4e else 0x6b7f42, 0.9f))
    }
    val left = g.houseX - g.houseHw
    val wallTop = gy - g.houseWh
    canvas.drawRect(left, wallTop, left + g.houseHw * 2, gy, k.linear(left, 0f, left + g.houseHw * 2, 0f, c(0xe9dcc6 to 1f, 0xf1e6d3 to 1f, 0xdccbb0 to 1f), f(0f, 0.5f, 1f)))
    canvas.drawRect(left, wallTop, left + g.houseHw * 2, gy, k.stroke(SPIRIT_INK, 1f, 0.35f))
    val dw = g.houseHw * 0.26f
    canvas.drawRoundRect(g.houseX - dw / 2, gy - g.houseWh * 0.72f, g.houseX + dw / 2, gy, 2f, 2f, k.fill(0x8a5a3a))
    canvas.drawCircle(g.houseX + dw * 0.28f, gy - g.houseWh * 0.36f, 1.3f, k.fill(0xf2c66b))
    for (side in intArrayOf(-1, 1)) {
        val wx = g.houseX + side * g.houseHw * 0.58f - g.houseHw * 0.17f
        val ry = gy - g.houseWh * 0.74f
        val rh = g.houseWh * 0.36f
        val rw = g.houseHw * 0.34f
        canvas.drawRect(wx, ry, wx + rw, ry + rh, k.linear(0f, ry, 0f, ry + rh, c(0xbcd3dc to 1f, 0x8fb0bf to 1f, 0x6f93a4 to 1f), f(0f, 0.5f, 1f)))
        canvas.drawRect(wx, ry, wx + rw, ry + rh, k.stroke(0xfdfaf3, 1.5f))
    }
}

/** The level in its own frame: origin at the body's centre, baked with SPIRIT_PAD around it. */
internal fun spiritDrawLevelBody(canvas: Canvas, k: SpiritKit, g: SpiritGeo, ft: SpiritFonts) {
    canvas.save()
    canvas.translate(g.len / 2 + SPIRIT_PAD, g.thick / 2 + SPIRIT_PAD)
    val l = g.len
    val t = g.thick
    val hl = l / 2
    val ht = t / 2
    val side = t * 0.18f
    // Side face below the top face: the extrusion's thickness.
    canvas.drawRoundRect(-hl, -ht + 2, hl, -ht + 2 + t + side, 7f, 7f, k.linear(0f, ht - 4, 0f, ht + side, c(0xb07714 to 1f, 0x8a5a0c to 1f, 0x5c3a06 to 1f), f(0f, 0.6f, 1f)))
    // Anodised top face.
    val body = RectF(-hl, -ht, hl, ht)
    canvas.drawRoundRect(body, 6f, 6f, k.linear(0f, -ht, 0f, ht, c(0xffe189 to 1f, 0xf6bf3a to 1f, 0xe8a522 to 1f, 0xc98714 to 1f), f(0f, 0.3f, 0.72f, 1f)))
    // Brushed grain along the length.
    canvas.save()
    canvas.clipPath(Path().apply { addRoundRect(body, 6f, 6f, Path.Direction.CW) })
    k.brushed(canvas, -hl, -ht, l, t, 0.12f, 5)
    canvas.restore()
    canvas.drawLine(-hl + 6, -ht + 0.8f, hl - 6, -ht + 0.8f, k.stroke(0xfff6d6, 1.2f, 0.9f))
    canvas.drawLine(-hl + 6, ht - 0.6f, hl - 6, ht - 0.6f, k.stroke(0x7a4c08, 1f, 0.5f))
    // Etched ruler along the top edge, every 4 pt, longer every 20.
    val etch = Path()
    val cap = t * 0.62f
    var x = -hl + cap + 6
    while (x <= hl - cap - 6) {
        val n = SpiritMotion.jsRound(((x + hl) / 4).toDouble())
        val len = if (n % 5 == 0) 6f else 3f
        etch.moveTo(x, -ht + 2)
        etch.lineTo(x, -ht + 2 + len)
        x += 4
    }
    canvas.drawPath(etch, k.stroke(0x3a2606, 0.7f, 0.55f))
    k.spaced(canvas, "TRUELINE 600", -hl + cap + 10, ht - 7, ft.etch, 0x3a2606, 0.6f, 1.4f)
    val mm = "0.5 mm/m"
    k.text(canvas, mm, hl - cap - 10 - ft.etch.measureText(mm), ht - 7, ft.etch, 0x3a2606, 0.6f)
    // Machined end caps with rubber bumpers.
    for (s in intArrayOf(-1, 1)) {
        val x0 = if (s < 0) -hl else hl - cap
        canvas.drawRoundRect(x0, -ht - 1, x0 + cap, -ht - 1 + t + side + 1, 6f, 6f, k.linear(0f, -ht, 0f, ht + side, c(0x3a3d42 to 1f, 0x24272b to 1f, 0x15171a to 1f), f(0f, 0.5f, 1f)))
        val px = x0 + if (s < 0) 5f else 0f
        canvas.drawRoundRect(px, -ht + 3, px + cap - 5, -ht + 3 + t - 6, 3f, 3f, k.linear(0f, -ht, 0f, ht, c(0xeef0f2 to 1f, 0xb6bbc2 to 1f, 0x8a9099 to 1f), f(0f, 0.5f, 1f)))
        for (i in 0 until 4) {
            val gx = x0 + (if (s < 0) 9f else 4f) + i * ((cap - 13) / 3)
            canvas.drawLine(gx, -ht + 7, gx, ht - 7, k.stroke(0x5d636b, 1f, 0.55f))
        }
        canvas.drawLine(px + 2, -ht + 3.8f, x0 + cap - (if (s < 0) 2f else 7f), -ht + 3.8f, k.stroke(0xffffff, 0.8f, 0.8f))
    }
    // The vial window: a dark bezel, the lime liquid, the centre marks.
    val vw = g.vialHalf * 2 + 12
    val vh = t * 0.62f
    canvas.drawRoundRect(-vw / 2, -vh / 2, vw / 2, vh / 2, vh / 2, vh / 2, k.fill(0x3b2a0a, 0.85f))
    canvas.drawRoundRect(-vw / 2 + 2, -vh / 2 + 2, vw / 2 - 2, vh / 2 - 2, vh / 2 - 2, vh / 2 - 2, k.linear(0f, -vh / 2, 0f, vh / 2, c(0xf4f0e3 to 1f, 0xd9d2bd to 1f, 0xb5ac92 to 1f), f(0f, 0.5f, 1f)))
    val tube = Path()
    val th = t * 0.4f
    val steps = 24
    for (i in 0..steps) {
        val tx = -g.vialHalf + (i / steps.toFloat()) * g.vialHalf * 2
        val ty = g.tubeY(tx) - th / 2
        if (i == 0) tube.moveTo(tx, ty) else tube.lineTo(tx, ty)
    }
    for (i in steps downTo 0) {
        val tx = -g.vialHalf + (i / steps.toFloat()) * g.vialHalf * 2
        tube.lineTo(tx, g.tubeY(tx) + th / 2)
    }
    tube.close()
    canvas.drawPath(tube, k.linear(0f, -th / 2, 0f, th / 2 + 4, c(0xe8f78a to 1f, LIME to 1f, 0x93b81f to 1f, 0x6f8f14 to 1f), f(0f, 0.35f, 0.75f, 1f)))
    canvas.drawPath(tube, k.stroke(0x4d6410, 1f, 0.55f))
    for (s in intArrayOf(-1, 1)) {
        val mx = s * (g.bubbleLen / 2 + 2)
        canvas.drawLine(mx, g.tubeY(mx) - th / 2 - 2, mx, g.tubeY(mx) + th / 2 + 2, k.stroke(0x1f1a10, 1.6f, 0.9f))
        val x2 = s * (g.bubbleLen / 2 + 2 + g.travel * 0.45f)
        canvas.drawLine(x2, g.tubeY(x2) - th / 2, x2, g.tubeY(x2) - th / 2 + 4, k.stroke(0x1f1a10, 1f, 0.6f))
        canvas.drawLine(x2, g.tubeY(x2) + th / 2 - 4, x2, g.tubeY(x2) + th / 2, k.stroke(0x1f1a10, 1f, 0.6f))
    }
    canvas.restore()
}

/** The level's soft shadow (blur 14), baked with room for the blur around it. */
internal fun spiritDrawShadow(canvas: Canvas, k: SpiritKit, g: SpiritGeo) {
    val m = SPIRIT_SHADOW_PAD
    canvas.drawRoundRect(m, m, m + g.len, m + g.thick, 8f, 8f, k.fill(0x4a3a20, 0.22f, 14f))
}

internal const val SPIRIT_SHADOW_PAD = 48f

// ——— Per frame ———

private val DIGITS = Array(10) { "$it" }

/** Two-digit odometer; the tens column hides when zero. Returns the width drawn. */
private fun odometer(canvas: Canvas, k: SpiritKit, value: Double, x: Float, base: Float, font: Paint, digitW: Float, lineH: Float, hex: Int): Float {
    val units = SpiritMotion.softRound(max(0.0, value))
    val um = ((units % 10) + 10) % 10
    val tens = floor(units / 10) + max(0.0, um - 9)
    val lead = clamp(tens, 0.0, 1.0).toFloat()
    val shift = -digitW * (1 - lead)
    canvas.save()
    canvas.clipRect(x - 4, base - lineH * 0.8f, x - 4 + digitW * 2 + 8, base - lineH * 0.8f + lineH)
    for (col in 0 until 2) {
        val v = if (col == 0) tens else units
        val d = ((v % 10) + 10) % 10
        val i = floor(d).toInt()
        val fr = (d - i).toFloat()
        val cx = x + shift + col * digitW
        for (j in 0 until 2) {
            val digit = (i + j) % 10
            if (col == 0 && digit == 0 && tens < 1.5) continue
            val a = (if (col == 0) lead else 1f) * (1 - min(1f, abs(j - fr)))
            if (a <= 0.01f) continue
            val s = DIGITS[digit]
            k.text(canvas, s, cx + (digitW - font.measureText(s)) / 2, base + (j - fr) * lineH * 0.8f, font, hex, a)
        }
    }
    canvas.restore()
    return digitW * 2 + shift
}

internal fun spiritDrawReadout(canvas: Canvas, k: SpiritKit, g: SpiritGeo, ft: SpiritFonts, angle: Double) {
    val card = g.card
    val a = max(0.0, angle)
    val base = card.y + 82
    val w = odometer(canvas, k, a, card.x + 18, base, ft.degrees, ft.degreesW, 64f, SPIRIT_INK)
    k.text(canvas, "°", card.x + 18 + w + 1, base, ft.degrees, SPIRIT_INK, 0.9f)
    val b2 = card.y + 110
    val rw = odometer(canvas, k, SpiritMotion.riseOf(a), card.x + 20, b2, ft.rise, ft.riseW, 26f, SPIRIT_INK)
    k.text(canvas, "/12 pitch", card.x + 20 + rw + 1, b2, ft.rise, SPIRIT_INK, 0.6f)
    // The pitch word in a pill whose width follows the rolling word.
    val idx = SpiritMotion.pitchIndex(a)
    val i = clamp(floor(idx), 0.0, (PITCH_WORDS.size - 1).toDouble()).toInt()
    val fr = (idx - i).toFloat()
    val w0 = ft.pill.measureText(PITCH_WORDS[i])
    val w1 = if (i + 1 < PITCH_WORDS.size) ft.pill.measureText(PITCH_WORDS[i + 1]) else w0
    val pw = w0 + (w1 - w0) * fr + 22
    val py = card.y + card.h - 44
    val pill = RectF(card.x + 18, py, card.x + 18 + pw, py + 26)
    canvas.drawRoundRect(pill, 13f, 13f, k.fill(0xe8862a, 0.14f))
    canvas.save()
    k.path.reset()
    k.path.addRoundRect(pill, 13f, 13f, Path.Direction.CW)
    canvas.clipPath(k.path)
    for (j in 0 until 2) {
        val word = PITCH_WORDS.getOrNull(i + j) ?: continue
        val al = 1 - min(1f, abs(j - fr))
        if (al <= 0.01f) continue
        k.text(canvas, word, card.x + 29, py + 17.5f + (j - fr) * 18, ft.pill, 0xa8520f, al)
    }
    canvas.restore()
}

private val ROOF = c(0x5a6470 to 1f, 0x434c57 to 1f, 0x2f363e to 1f)
private val STOPS_MID = f(0f, 0.5f, 1f)

/** Gable, roof slab and the pitch arc, for the current angle. */
internal fun spiritDrawRoof(canvas: Canvas, k: SpiritKit, g: SpiritGeo, angle: Double) {
    val a = clamp(angle, 0.0, 50.0) * PI / 180
    val tn = min(tan(a), ((g.card.h - 38 - g.houseWh) / g.houseHw).toDouble()).toFloat()
    val top = g.houseY - g.houseWh
    val ridgeY = top - g.houseHw * tn
    val over = 12f
    val lx = g.houseX - g.houseHw - over
    val rx = g.houseX + g.houseHw + over
    val ey = top + over * tn
    val slab = (7 / cos(a)).toFloat()
    // Gable wall.
    val gable = k.path
    gable.reset()
    gable.moveTo(g.houseX - g.houseHw, top)
    gable.lineTo(g.houseX, ridgeY)
    gable.lineTo(g.houseX + g.houseHw, top)
    gable.close()
    canvas.drawPath(gable, k.fill(0xe4d5bb))
    // Roof slab.
    val roof = k.path2
    roof.reset()
    roof.moveTo(lx, ey - slab)
    roof.lineTo(g.houseX, ridgeY - slab)
    roof.lineTo(rx, ey - slab)
    roof.lineTo(rx, ey)
    roof.lineTo(g.houseX, ridgeY)
    roof.lineTo(lx, ey)
    roof.close()
    canvas.drawPath(roof, k.linear(0f, ridgeY - slab, 0f, ey, ROOF, STOPS_MID))
    canvas.drawLine(lx, ey - slab, g.houseX, ridgeY - slab, k.stroke(0xa6b0bb, 1f, 0.7f))
    canvas.drawLine(g.houseX, ridgeY - slab, rx, ey - slab, k.stroke(0x8994a0, 1f, 0.5f))
    // Pitch arc and its run line at the left eave.
    val ax = g.houseX - g.houseHw
    val arcR = 26f
    canvas.drawLine(ax, top, ax + arcR + 14, top, dashed(k.stroke(SPIRIT_INK, 1f, 0.35f), 3f, 3f))
    if (angle > 0.5) {
        val deg = (a * 180 / PI).toFloat()
        k.rect.set(ax - arcR, top - arcR, ax + arcR, top + arcR)
        canvas.drawArc(k.rect, -deg, deg, false, k.stroke(0xe8862a, 1.6f, 0.95f))
    }
}

private val BUBBLE = c(0xfbffe9 to 0.98f, 0xeef9b8 to 0.95f, 0xd2ea6a to 0.9f)
private val BUBBLE_STOPS = f(0f, 0.55f, 1f)

/** Shadow, body bitmap, bubble and glass, in the level's rotated frame. */
internal fun spiritDrawLevel(
    canvas: Canvas,
    k: SpiritKit,
    g: SpiritGeo,
    level: Bitmap,
    shadow: Bitmap,
    bitmapPaint: Paint,
    angle: Double,
    bubble: Double,
    speed: Double,
) {
    val rot = (-angle).toFloat()
    canvas.save()
    canvas.translate(g.cx, g.cy + 18)
    canvas.rotate(rot)
    val sp = SPIRIT_SHADOW_PAD
    k.rect.set(-g.len / 2 - sp, -g.thick / 2 - sp, g.len / 2 + sp, g.thick / 2 + sp)
    canvas.drawBitmap(shadow, null, k.rect, bitmapPaint)
    canvas.restore()
    canvas.save()
    canvas.translate(g.cx, g.cy)
    canvas.rotate(rot)
    k.rect.set(-g.len / 2 - SPIRIT_PAD, -g.thick / 2 - SPIRIT_PAD, -g.len / 2 - SPIRIT_PAD + g.boxW, -g.thick / 2 - SPIRIT_PAD + g.boxH)
    canvas.drawBitmap(level, null, k.rect, bitmapPaint)
    // Bubble: travels to the higher end, stretched by speed, squashed against the ends.
    var x = (bubble * g.travel).toFloat()
    var len = g.bubbleLen
    var h = g.bubbleH
    val stretch = clamp(abs(speed) * g.travel / 700, 0.0, 0.32).toFloat()
    len *= 1 + stretch
    h *= 1 - stretch * 0.4f
    val limit = g.vialHalf - 2
    if (abs(x) + len / 2 > limit) {
        val over = abs(x) + len / 2 - limit
        val squash = min(over, len * 0.32f)
        len -= squash
        h *= 1 + (squash / g.bubbleLen) * 0.35f
        x = sign(x) * (limit - len / 2)
    }
    val y = g.tubeY(x) - g.thick * 0.2f + h / 2 + 1
    k.rect.set(x - len / 2, y - h / 2, x + len / 2, y + h / 2)
    canvas.drawRoundRect(k.rect, h / 2, h / 2, k.linear(0f, y - h / 2, 0f, y + h / 2, BUBBLE, BUBBLE_STOPS))
    canvas.drawRoundRect(k.rect, h / 2, h / 2, k.stroke(0x56700f, 1.3f, 0.75f))
    k.rect.set(x - len * 0.32f, y - h / 2 + 1.6f, x - len * 0.32f + len * 0.5f, y - h / 2 + 1.6f + h * 0.3f)
    canvas.drawRoundRect(k.rect, h * 0.15f, h * 0.15f, k.fill(0xffffff, 0.9f))
    canvas.drawLine(x - len / 2 + h * 0.4f, y + h / 2 + 1.2f, x + len / 2 - h * 0.4f, y + h / 2 + 1.2f, k.stroke(0x4d6410, 1f, 0.35f))
    // Glass over the tube: a bright streak along the top.
    val th = g.thick * 0.4f
    val streak = k.path
    streak.reset()
    for (i in 0..16) {
        val sx = -g.vialHalf * 0.86f + (i / 16f) * g.vialHalf * 1.72f
        val sy = g.tubeY(sx) - th / 2 + 2.4f
        if (i == 0) streak.moveTo(sx, sy) else streak.lineTo(sx, sy)
    }
    canvas.drawPath(streak, k.stroke(0xffffff, 1.6f, 0.6f))
    canvas.restore()
}

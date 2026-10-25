package com.ngocdevv.nativecook.blocks.sheets.velvetcurtainsheet

import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.Typeface
import android.os.Build
import com.ngocdevv.nativecook.shared.sigmaToRadius
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/*
 Velvet Curtain Sheet — the static art, pre-rendered once into bitmaps (a port of velvetArt.ts): the
 poster of "The Glass Orchard" (a dusk orchard of glass trees, drawn in code with seeded shapes)
 and the sheet's seat map (screen arc, eight rows of twelve seats with an aisle, row letters,
 legend).
*/

private fun hex(v: String) = android.graphics.Color.parseColor(v)
private fun rgba(r: Int, g: Int, b: Int, a: Float) = android.graphics.Color.argb((a * 255f).roundToInt(), r, g, b)

private val PAGE = hex("#140A0F")
private val INK_SOFT = rgba(243, 233, 228, 0.62f)
private val GOLD = hex("#F2C060")

private class Rnd(seed: Long) {
    private var s = seed
    operator fun invoke(): Float {
        s = (s * 16807L) % 2147483647L
        return (s.toDouble() / 2147483647.0).toFloat()
    }
}

private fun blur(sigma: Float) = BlurMaskFilter(sigmaToRadius(sigma), BlurMaskFilter.Blur.NORMAL)

private fun glassTree(c: Canvas, x: Float, base: Float, h: Float, r: Rnd) {
    val p = Paint(Paint.ANTI_ALIAS_FLAG)
    // Trunk and two limbs, tapered, in smoked glass.
    val trunk = Path().apply {
        moveTo(x - 3, base)
        quadTo(x - 1.5f, base - h * 0.4f, x - 0.8f, base - h * 0.62f)
        lineTo(x + 0.8f, base - h * 0.62f)
        quadTo(x + 1.5f, base - h * 0.4f, x + 3, base)
        close()
    }
    p.color = rgba(150, 190, 220, 0.30f)
    c.drawPath(trunk, p)
    val limb = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeWidth = 1.4f
        color = rgba(170, 210, 235, 0.32f)
    }
    c.drawLine(x, base - h * 0.45f, x - h * 0.2f, base - h * 0.72f, limb)
    c.drawLine(x, base - h * 0.5f, x + h * 0.22f, base - h * 0.78f, limb)
    // Crown: overlapping glass discs, each with a cool rim and a bright glint.
    val cy = base - h * 0.78f
    repeat(16) {
        val a = r() * PI.toFloat() * 2
        val d = sqrt(r()) * h * 0.3f
        val px = x + cos(a) * d * 1.15f
        val py = cy + sin(a) * d * 0.8f
        val rad = h * (0.07f + r() * 0.08f)
        val pink = r() > 0.72f
        p.color = android.graphics.Color.BLACK
        p.shader = RadialGradient(
            px - rad * 0.3f, py - rad * 0.35f, rad * 1.2f,
            intArrayOf(
                if (pink) rgba(246, 176, 206, 0.42f) else rgba(190, 232, 255, 0.40f),
                if (pink) rgba(180, 90, 150, 0.10f) else rgba(90, 140, 210, 0.08f),
                rgba(60, 90, 150, 0f),
            ),
            floatArrayOf(0f, 0.7f, 1f),
            Shader.TileMode.CLAMP,
        )
        c.drawCircle(px, py, rad, p)
        p.shader = null
        limb.strokeWidth = 0.8f
        limb.color = if (pink) rgba(255, 200, 225, 0.35f) else rgba(210, 240, 255, 0.35f)
        c.drawCircle(px, py, rad, limb)
        p.color = rgba(255, 255, 250, 0.8f)
        c.drawCircle(px - rad * 0.35f, py - rad * 0.4f, max(0.6f, rad * 0.12f), p)
    }
}

/** The poster behind the sheet, [width] × [height] dp at [scale] px per dp. */
internal fun buildVelvetPoster(width: Float, height: Float, scale: Float): Bitmap {
    val bmp = Bitmap.createBitmap(ceil(width * scale).toInt(), ceil(height * scale).toInt(), Bitmap.Config.ARGB_8888)
    val c = Canvas(bmp)
    c.scale(scale, scale)
    val p = Paint(Paint.ANTI_ALIAS_FLAG)
    val horizon = height * 0.66f
    p.shader = LinearGradient(0f, 0f, 0f, horizon, intArrayOf(hex("#0A1424"), hex("#1C2A46"), hex("#47324F"), hex("#7A4A5C")), floatArrayOf(0f, 0.45f, 0.85f, 1f), Shader.TileMode.CLAMP)
    c.drawRect(0f, 0f, width, height, p)
    p.shader = null
    val r = Rnd(11)
    // Stars.
    repeat(70) {
        val a = 0.25f + r() * 0.6f
        p.color = rgba(240, 232, 220, (a * 100).roundToInt() / 100f)
        val sx = r() * width
        val sy = r() * horizon * 0.7f
        c.drawCircle(sx, sy, 0.4f + r() * 0.9f, p)
    }
    // Moon with its halo.
    val mx = width * 0.74f
    val my = height * 0.2f
    p.color = rgba(246, 226, 196, 0.22f)
    p.maskFilter = blur(26f)
    c.drawCircle(mx, my, 44f, p)
    p.maskFilter = null
    p.color = android.graphics.Color.BLACK
    p.shader = RadialGradient(mx - 8, my - 8, 34f, intArrayOf(hex("#FFF4DE"), hex("#F1DDB8"), hex("#C9AE8A")), floatArrayOf(0f, 0.6f, 1f), Shader.TileMode.CLAMP)
    c.drawCircle(mx, my, 24f, p)
    p.shader = null
    // Far hills, then the orchard on a low rise.
    val hills = Path().apply {
        moveTo(0f, horizon - 20)
        cubicTo(width * 0.25f, horizon - 48, width * 0.45f, horizon - 10, width * 0.62f, horizon - 30)
        cubicTo(width * 0.8f, horizon - 46, width * 0.92f, horizon - 22, width, horizon - 30)
        lineTo(width, height)
        lineTo(0f, height)
        close()
    }
    p.color = hex("#1A1830")
    c.drawPath(hills, p)
    val trees = floatArrayOf(0.12f, 0.3f, 0.5f, 0.68f, 0.86f)
    trees.forEachIndexed { i, tx -> glassTree(c, width * tx, horizon + 8 + (i % 2) * 10, 120f + if (i == 2) 40f else (i % 2) * 18f, r) }
    // Low mist across the orchard.
    p.color = rgba(214, 196, 230, 0.12f)
    p.maskFilter = blur(18f)
    c.drawRect(-20f, horizon - 14, width + 20, horizon + 22, p)
    p.maskFilter = null
    val ground = Path().apply {
        moveTo(0f, horizon + 14)
        quadTo(width * 0.5f, horizon - 4, width, horizon + 16)
        lineTo(width, height)
        lineTo(0f, height)
        close()
    }
    p.color = hex("#100A14")
    c.drawPath(ground, p)
    // Fade into the page so the title reads.
    p.color = android.graphics.Color.BLACK
    p.shader = LinearGradient(0f, height * 0.38f, 0f, height, intArrayOf(rgba(20, 10, 15, 0f), rgba(20, 10, 15, 0.72f), PAGE), floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP)
    c.drawRect(0f, 0f, width, height, p)
    return bmp
}

/** The height the seat map needs, in dp. */
internal fun velvetSeatsHeight(pitch: Float) = SEATS_Y + (VELVET_ROWS.size - 1) * pitch + SEAT_H + 32

/** The sheet's seat map, [width] dp wide at [scale] px per dp. */
internal fun buildVelvetSeats(width: Float, pitch: Float, scale: Float): Bitmap {
    val h = velvetSeatsHeight(pitch)
    val bmp = Bitmap.createBitmap(ceil(width * scale).toInt(), ceil(h * scale).toInt(), Bitmap.Config.ARGB_8888)
    val c = Canvas(bmp)
    c.scale(scale, scale)
    val label = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 10f
        typeface = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) Typeface.create(Typeface.SANS_SERIF, 600, false) else Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
    }
    val p = Paint(Paint.ANTI_ALIAS_FLAG)
    val x0 = (width - seatsWidth()) / 2
    val x1 = x0 + seatsWidth()
    // The screen: a lit arc with its glow.
    val arc = Path().apply {
        moveTo(x0 + 10, SCREEN_Y + 8)
        quadTo(width / 2, SCREEN_Y - 6, x1 - 10, SCREEN_Y + 8)
    }
    val s = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeWidth = 8f
        color = rgba(240, 214, 180, 0.22f)
        maskFilter = blur(8f / 3f)
    }
    c.drawPath(arc, s)
    s.maskFilter = null
    s.strokeWidth = 2.5f
    s.color = hex("#EBD6B8")
    c.drawPath(arc, s)
    label.color = INK_SOFT
    c.drawText("SCREEN", width / 2 - label.measureText("SCREEN") / 2, SCREEN_Y + 22, label)
    // Seats.
    val at = FloatArray(2)
    for (i in 0 until VELVET_ROWS.size * PER_ROW) {
        seatAt(i, width, pitch, at)
        val x = at[0]
        val y = at[1]
        val taken = isTaken(i)
        p.color = android.graphics.Color.BLACK
        p.shader = LinearGradient(
            x, y, x, y + SEAT_H,
            if (taken) intArrayOf(hex("#2A1C24"), hex("#1F141B"), hex("#180F15")) else intArrayOf(hex("#5A3F4C"), hex("#432E39"), hex("#35232D")),
            floatArrayOf(0f, 0.5f, 1f),
            Shader.TileMode.CLAMP,
        )
        c.drawRoundRect(x, y, x + SEAT_W, y + SEAT_H, 5f, 5f, p)
        p.shader = null
        // Backrest seam and a lit top edge.
        p.color = if (taken) rgba(0, 0, 0, 0.25f) else rgba(10, 4, 8, 0.35f)
        c.drawRect(x + 2, y + 7, x + SEAT_W - 2, y + 8, p)
        p.color = if (taken) rgba(255, 230, 230, 0.05f) else rgba(255, 225, 230, 0.18f)
        c.drawRect(x + 4, y + 1, x + SEAT_W - 4, y + 2, p)
    }
    label.color = rgba(243, 233, 228, 0.42f)
    for (r in VELVET_ROWS.indices) {
        seatAt(r * PER_ROW, width, pitch, at)
        val w = label.measureText(VELVET_ROWS[r])
        c.drawText(VELVET_ROWS[r], x0 - 16 - w / 2, at[1] + SEAT_H - 5, label)
        c.drawText(VELVET_ROWS[r], x1 + 16 - w / 2, at[1] + SEAT_H - 5, label)
    }
    // Legend.
    seatAt(VELVET_ROWS.size * PER_ROW - 1, width, pitch, at)
    val ly = at[1] + SEAT_H + 18
    val items = listOf(hex("#4A3440") to "Available", hex("#22171E") to "Taken", GOLD to "Selected")
    var total = 0f
    for ((_, t) in items) total += 14 + label.measureText(t) + 20
    var lx = width / 2 - (total - 20) / 2
    for ((col, t) in items) {
        p.color = col
        c.drawRoundRect(lx, ly - 9, lx + 10, ly + 1, 3f, 3f, p)
        label.color = INK_SOFT
        c.drawText(t, lx + 14, ly, label)
        lx += 14 + label.measureText(t) + 20
    }
    return bmp
}

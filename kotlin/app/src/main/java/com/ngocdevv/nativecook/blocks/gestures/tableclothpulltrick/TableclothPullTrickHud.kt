package com.ngocdevv.nativecook.blocks.gestures.tableclothpulltrick

import android.graphics.Canvas
import android.graphics.Paint
import com.ngocdevv.nativecook.blocks.gestures.tableclothpulltrick.ClothMotion.DEPTH
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/** The clean-pull counter (an odometer) in the header and the "Clean pull" badge over the table. */

internal class ClothFonts {
    val label = clothFont(false, 10f, 700)
    val count = clothFont(false, 32f, 700)
    val badge = clothFont(false, 13f, 600)
}

private fun softRound(v: Double): Double {
    val f = floor(v)
    val x = ((v - f - 0.35) / 0.3).coerceIn(0.0, 1.0)
    return f + x * x * (3 - 2 * x)
}

private val NUMBERS = Array(20) { "$it" }
private const val BADGE = "Clean pull · dishes stayed"

internal fun clothDrawHud(canvas: Canvas, k: ClothKit, sc: ClothScratch, g: ClothGeo, ft: ClothFonts, top: Float, p: ClothPose) {
    // Counter, right-aligned in the header: label over a rolling number.
    val right = g.w - 24
    k.spaced(canvas, "CLEAN PULLS", right, top + 28, ft.label, 0xf4e7d4, 0.5f, 1.4f, 1f)
    val r = softRound(max(0.0, p.count))
    val i = floor(r).toInt()
    val fr = (r - i).toFloat()
    val lineH = 34f
    val base = top + 64
    canvas.save()
    canvas.clipRect(right - 80, base - lineH + 4, right, base + 6)
    for (j in 0 until 2) {
        val a = 1 - min(1f, abs(j - fr))
        if (a <= 0.01f) continue
        val s = NUMBERS.getOrElse(i + j) { "${i + j}" }
        k.text(canvas, s, right - ft.count.measureText(s), base + (j - fr) * lineH, ft.count, 0xfff1dc, a)
    }
    canvas.restore()
    // Badge: drops in over the far edge of the table.
    val b = p.badge.toFloat()
    if (b > 0.01f) {
        g.project(0.0, DEPTH, 0.0, sc.p1)
        val w = ft.badge.measureText(BADGE) + 40
        val y = sc.p1[1].toFloat() - 46 - (1 - b) * 10
        val x0 = g.cx - w / 2
        canvas.drawRoundRect(x0, y + 6, x0 + w, y + 36, 15f, 15f, k.fill(0, 0.35f * b, 10f))
        canvas.drawRoundRect(x0, y, x0 + w, y + 30, 15f, 15f, k.fill(0xf3e2c4, b))
        canvas.drawCircle(x0 + 17, y + 15, 6.5f, k.fill(0x4f7a3a, b))
        val tick = sc.path
        tick.reset()
        tick.moveTo(x0 + 14, y + 15)
        tick.lineTo(x0 + 16.3f, y + 17.4f)
        tick.lineTo(x0 + 20.2f, y + 12.8f)
        val tp = k.fill(0xf3e2c4, b)
        tp.style = Paint.Style.STROKE
        tp.strokeWidth = 1.6f
        canvas.drawPath(tick, tp)
        k.text(canvas, BADGE, x0 + 30, y + 19.5f, ft.badge, 0x3a2412, b)
    }
}

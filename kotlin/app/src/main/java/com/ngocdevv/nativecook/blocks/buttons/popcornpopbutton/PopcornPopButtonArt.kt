package com.ngocdevv.nativecook.blocks.buttons.popcornpopbutton

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.SweepGradient
import com.ngocdevv.nativecook.blocks.buttons.popcornpopbutton.PopcornGfx.Companion.linear
import com.ngocdevv.nativecook.blocks.buttons.popcornpopbutton.PopcornGfx.Companion.oval
import com.ngocdevv.nativecook.blocks.buttons.popcornpopbutton.PopcornGfx.Companion.radial
import com.ngocdevv.nativecook.blocks.buttons.popcornpopbutton.PopcornMotion.hash
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.coroutineContext
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/*
 Popcorn Pop Button — the drawing, ported from popcornArt.ts. Baked once (off the main thread, at
 up to 2x): the velvet room with the movie card, the button's plate with its thermometer track,
 the copper pan, the glass lid, the striped bowl, and an atlas of 4 kernels and 6 corns. Per
 frame: the heat (arc, glow under the pan, warm tint inside it), then kernels, hull flecks and
 corns from the atlas, the lid and the bowl, each under its own transform.
*/

/** Thermometer arc: starts at 125° (clockwise from +x) and sweeps 290°, leaving the label's gap at the bottom. */
internal const val POPCORN_ARC_START = 125f
internal const val POPCORN_ARC_SWEEP = 290f

internal class PopcornLayout(val w: Float, val h: Float, top: Float, bottom: Float) {
    val cardX = 20f
    val cardY = top + 104
    val cardW = w - 40
    val cardH = 96f
    val footerY = h - max(bottom, 14f) - 58
    private val a0 = cardY + cardH + 22
    private val a1 = footerY - 18
    val D = minOf(w - 44, a1 - a0, 344f)
    val bx = w / 2
    val by = (a0 + a1) / 2
    val px = bx
    val py = by - D * 0.06f
    val rx = D * 0.3f
    val ry = D * 0.19f
    val wall = D * 0.055f
    val dome = D * 0.11f
    val bowlX = cardX + cardW - 42
    val bowlY = cardY + cardH / 2 - 4
    val arcR = D / 2 - 15
    val geo = PopcornGeo(
        w.toDouble(), h.toDouble(), bx.toDouble(), by.toDouble(), D.toDouble(), px.toDouble(), py.toDouble(),
        rx.toDouble(), ry.toDouble(), wall.toDouble(), dome.toDouble(), bowlX.toDouble(), bowlY.toDouble(),
    )
}

internal class PopcornLayers(
    val bg: PopcornBaked,
    val plate: PopcornBaked,
    val pan: PopcornBaked,
    val lid: PopcornBaked,
    val bowl: PopcornBaked,
    val atlas: Bitmap,
    val cell: Float,
    val arc: Shader,
    val glow: Shader,
    val tint: Shader,
)

internal object PopcornPopButtonArt {

    /** The room: a wine velvet wall with a curtain over the header, a warm spot behind the button, the movie card. */
    private fun drawRoom(c: Canvas, g: PopcornLayout, top: Float, gx: PopcornGfx) {
        val w = g.w
        val h = g.h
        val full = RectF(0f, 0f, w, h)
        c.drawRect(full, gx.shaded(linear(0f, 0f, 0f, h, arrayOf("#2B1016", "#200B11", "#16070C", "#110509"), floatArrayOf(0f, 0.35f, 0.75f, 1f))))
        // Curtain folds behind the header, fading out downwards.
        val band = top + 150
        val bandRect = RectF(0f, 0f, w, band)
        c.saveLayer(bandRect, null)
        c.drawRect(bandRect, gx.shaded(linear(0f, 0f, 31f, 0f, arrayOf("#2A0C12", "#5C1D27", "#7A2A33", "#4A161E", "#2A0C12"), floatArrayOf(0f, 0.3f, 0.45f, 0.7f, 1f), Shader.TileMode.REPEAT)))
        val fade = gx.shaded(linear(0f, 0f, 0f, band, arrayOf("rgba(0,0,0,0.85)", "rgba(0,0,0,0.55)", "rgba(0,0,0,0)"), floatArrayOf(0f, 0.5f, 1f)))
        fade.xfermode = PopcornGfx.dstIn
        c.drawRect(bandRect, fade)
        c.restore()
        // A warm spot behind the button.
        c.drawRect(full, gx.shaded(radial(g.bx, g.by - g.D * 0.05f, g.D * 0.95f, arrayOf("rgba(140,58,34,0.55)", "rgba(96,34,26,0.25)", "rgba(60,18,20,0)"), floatArrayOf(0f, 0.55f, 1f))))
        // Vignette.
        c.drawRect(full, gx.shaded(radial(w / 2, h * 0.52f, hypot(w, h) * 0.62f, arrayOf("rgba(10,3,5,0)", "rgba(10,3,5,0.25)", "rgba(8,2,4,0.62)"), floatArrayOf(0f, 0.6f, 1f))))
        PopcornGrain(3).draw(c, full, 0.07)
        // The movie card: two shadows, a plum face, a lit top edge.
        val x = g.cardX
        val y = g.cardY
        val cw = g.cardW
        val ch = g.cardH
        c.drawRoundRect(x, y + 14, x + cw, y + 14 + ch, 20f, 20f, gx.blurred("#050102", 0.5, 16f))
        c.drawRoundRect(x, y + 1.5f, x + cw, y + 1.5f + ch, 20f, 20f, gx.blurred("#050102", 0.45, 2f))
        c.drawRoundRect(x, y, x + cw, y + ch, 20f, 20f, gx.shaded(linear(x, y, x, y + ch, arrayOf("#43202A", "#33171F", "#2A1219"), floatArrayOf(0f, 0.5f, 1f))))
        val edge = gx.strokeShaded(linear(x, y, x, y + ch, arrayOf("rgba(255,214,190,0.22)", "rgba(255,214,190,0.05)", "rgba(0,0,0,0.25)"), floatArrayOf(0f, 0.4f, 1f)), 1f)
        c.drawRoundRect(x + 0.5f, y + 0.5f, x + cw - 0.5f, y + ch - 0.5f, 19.5f, 19.5f, edge)
        // The poster: a harbour at night under a big moon.
        val px = x + 12
        val py = y + 12
        val pw = 60f
        val ph = ch - 24
        c.save()
        c.clipPath(Path().apply { addRoundRect(RectF(px, py, px + pw, py + ph), 10f, 10f, Path.Direction.CW) })
        c.drawRect(px, py, px + pw, py + ph, gx.shaded(linear(0f, py, 0f, py + ph, arrayOf("#16223F", "#2C3F66", "#9A5A55", "#E39A5E"), floatArrayOf(0f, 0.4f, 0.62f, 0.7f))))
        c.drawCircle(px + pw * 0.66f, py + ph * 0.3f, 15f, gx.blurred("#FBE7C0", 0.35, 8f))
        c.drawCircle(px + pw * 0.66f, py + ph * 0.3f, 9.5f, gx.shaded(radial(px + pw * 0.62f, py + ph * 0.26f, 11f, arrayOf("#FFF6DF", "#F3DDB0", "#D9B880"))))
        c.drawRect(px, py + ph * 0.7f, px + pw, py + ph, gx.shaded(linear(0f, py + ph * 0.7f, 0f, py + ph, arrayOf("#233A5C", "#14213B", "#0C1528"))))
        for (i in 0 until 7) {
            val ly = py + ph * (0.74f + i * 0.035f)
            val lw = 6f + 10f * hash(i + 3).toFloat()
            c.drawLine(px + pw * 0.66f - lw / 2, ly, px + pw * 0.66f + lw / 2, ly, gx.stroke("#F6D9A8", 1f, 0.55 - i * 0.06))
        }
        // A lighthouse on the left headland.
        val land = Path().apply {
            moveTo(px, py + ph * 0.7f)
            lineTo(px + pw * 0.34f, py + ph * 0.68f)
            lineTo(px + pw * 0.4f, py + ph * 0.72f)
            lineTo(px, py + ph * 0.74f)
            close()
        }
        c.drawPath(land, gx.paint("#0D1424"))
        c.drawRect(px + pw * 0.16f, py + ph * 0.5f, px + pw * 0.16f + 4, py + ph * 0.69f, gx.paint("#E9E0CF"))
        c.drawCircle(px + pw * 0.16f + 2, py + ph * 0.5f, 3f, gx.blurred("#FFE7A8", 0.9, 3f))
        PopcornGrain(9).draw(c, RectF(px, py, px + pw, py + ph), 0.08)
        c.restore()
        // The bowl's place: a soft round well on the card's right.
        c.drawCircle(g.bowlX, g.bowlY + 12, 30f, gx.paint("#1E0B11", 0.55))
        c.drawCircle(g.bowlX, g.bowlY + 12, 30f, gx.strokeShaded(linear(0f, g.bowlY - 18, 0f, g.bowlY + 42, arrayOf("rgba(0,0,0,0.35)", "rgba(255,214,190,0.12)")), 1f))
        // Footer rule and dividers.
        c.drawLine(24f, g.footerY - 10, w - 24, g.footerY - 10, gx.stroke("#F3D9C8", 1f, 0.08))
        for (i in 1 until 3) c.drawLine(w * i / 3, g.footerY + 6, w * i / 3, g.footerY + 40, gx.stroke("#F3D9C8", 1f, 0.1))
    }

    /** The button's plate: shadows, a deep cocoa disc with a lit rim, the thermometer's track and ticks. */
    private fun drawPlate(c: Canvas, g: PopcornLayout, gx: PopcornGfx) {
        val r = g.D / 2
        val bx = g.bx
        val by = g.by
        c.drawCircle(bx, by + 20, r * 0.98f, gx.blurred("#040102", 0.55, 26f))
        c.drawCircle(bx, by + 2, r, gx.blurred("#040102", 0.5, 3f))
        c.drawCircle(bx, by, r, gx.shaded(radial(bx - r * 0.3f, by - r * 0.45f, r * 1.6f, arrayOf("#56281E", "#3A1914", "#24100D", "#170A08"), floatArrayOf(0f, 0.35f, 0.7f, 1f))))
        c.save()
        c.clipPath(Path().apply { addCircle(bx, by, r, Path.Direction.CW) })
        PopcornGrain(5).draw(c, RectF(bx - r, by - r, bx + r, by + r), 0.06)
        c.restore()
        c.drawCircle(bx, by, r - 0.75f, gx.strokeShaded(linear(0f, by - r, 0f, by + r, arrayOf("rgba(255,200,165,0.42)", "rgba(255,200,165,0.08)", "rgba(0,0,0,0.4)"), floatArrayOf(0f, 0.45f, 1f)), 1.5f))
        // Recessed track.
        val ar = g.arcR
        c.drawArc(oval(bx, by, ar, ar), POPCORN_ARC_START, POPCORN_ARC_SWEEP, false, gx.stroke("#0D0504", 9f))
        c.drawArc(oval(bx, by + 1.2f, ar, ar), POPCORN_ARC_START + 4, POPCORN_ARC_SWEEP - 8, false, gx.stroke("#FFC8A8", 1f, 0.07))
        for (i in 0..10) {
            val a = (POPCORN_ARC_START + POPCORN_ARC_SWEEP * i / 10) * PI.toFloat() / 180
            val r0 = ar + 9
            val r1 = ar + if (i % 5 == 0) 14 else 12
            c.drawLine(bx + cos(a) * r0, by + sin(a) * r0, bx + cos(a) * r1, by + sin(a) * r1, gx.stroke("#F2C9A8", 1.2f, if (i % 5 == 0) 0.4 else 0.2))
        }
    }

    /** Skia's arcToOval: an arc of the oval, joined to the current point by a line. */
    private fun Path.arcOval(r: RectF, start: Float, sweep: Float) = arcTo(r, start, sweep, false)

    /** The copper pan seen from above at an angle: front wall, rim, dark seasoned floor, a walnut handle. */
    private fun drawPan(c: Canvas, g: PopcornLayout, gx: PopcornGfx) {
        val px = g.px
        val py = g.py
        val rx = g.rx
        val ry = g.ry
        val wall = g.wall
        val dd = g.D
        // Contact shadow on the plate.
        c.drawOval(oval(px + 2, py + wall + ry * 0.2f, rx * 1.02f, ry * 0.9f), gx.blurred("#050102", 0.55, 8f))
        // Handle: brass collar and walnut grip, angled up to the left.
        c.save()
        c.translate(px - rx * 0.96f, py + ry * 0.12f)
        c.rotate(-10f)
        val len = dd * 0.17f
        c.drawRoundRect(-len, -6.5f, -8f, 6.5f, 6.5f, 6.5f, gx.shaded(linear(0f, -7f, 0f, 7f, arrayOf("#8A5634", "#5B3320", "#3A1F13"), floatArrayOf(0f, 0.45f, 1f))))
        c.drawLine(-len + 7, -3.5f, -14f, -3.5f, gx.stroke("#D9A47A", 1.2f, 0.35))
        c.drawRoundRect(-12f, -5f, 2f, 5f, 2f, 2f, gx.shaded(linear(0f, -5f, 0f, 5f, arrayOf("#F2D08A", "#B8863C", "#6E4A1C"))))
        c.drawCircle(-6f, 0f, 1.6f, gx.paint("#FFF0C8", 0.7))
        c.restore()
        // Front wall: from the rim down to the base ellipse.
        val body = Path().apply {
            moveTo(px - rx - 3, py)
            lineTo(px - rx * 0.95f, py + wall)
            arcOval(oval(px, py + wall, rx * 0.95f, ry * 0.95f), 180f, -180f)
            lineTo(px + rx + 3, py)
            close()
        }
        c.drawPath(body, gx.shaded(linear(px - rx, 0f, px + rx, 0f, arrayOf("#5E2A15", "#B8693F", "#E7A475", "#A9592F", "#4E220F"), floatArrayOf(0f, 0.28f, 0.42f, 0.7f, 1f))))
        c.drawPath(body, gx.shaded(linear(0f, py, 0f, py + wall + ry, arrayOf("rgba(0,0,0,0)", "rgba(20,6,2,0.45)"))))
        // Rim: a thick rolled copper lip.
        c.drawOval(oval(px, py, rx + 3, ry + 2.5f), gx.shaded(linear(px - rx, py - ry, px + rx, py + ry, arrayOf("#F6C79B", "#C9794A", "#8C4423", "#D88B58", "#F0B888"), floatArrayOf(0f, 0.3f, 0.55f, 0.8f, 1f))))
        // Floor: dark seasoned steel with an oil sheen.
        c.drawOval(oval(px, py + 1, rx - 3, ry - 2.5f), gx.shaded(radial(px - rx * 0.15f, py - ry * 0.2f, rx * 1.1f, arrayOf("#4A4340", "#2E2927", "#1B1716", "#141110"), floatArrayOf(0f, 0.4f, 0.8f, 1f))))
        for (i in 1..4) c.drawOval(oval(px, py + 1, (rx - 3) * (i / 5f), (ry - 2.5f) * (i / 5f)), gx.stroke("#8D817A", 0.6f, 0.06))
        // Inner wall: the far side of the pan's inside catches light, the near side is in shadow.
        c.drawOval(oval(px, py + 1.5f, rx - 4.5f, ry - 4), gx.strokeShaded(linear(0f, py - ry, 0f, py + ry, arrayOf("#C47A4E", "#6B3418", "#2A120A"), floatArrayOf(0f, 0.45f, 1f)), 4f))
        c.drawArc(oval(px, py, rx + 1.5f, ry + 1.2f), 200f, 110f, false, gx.stroke("#FFE2C4", 1.4f, 0.55))
        c.drawArc(oval(px - rx * 0.1f, py - ry * 0.05f, rx * 0.55f, ry * 0.4f), 200f, 70f, false, gx.stroke("#FFFFFF", 3f, 0.05))
    }

    /** A glass lid: steel rim, a faint dome, two curved specular streaks, a bakelite knob. */
    private fun drawLid(c: Canvas, g: PopcornLayout, gx: PopcornGfx) {
        val px = g.px
        val py = g.py
        val dome = g.dome
        val lx = g.rx * 1.05f
        val ly = g.ry * 1.05f
        val top = py - (ly + dome * 0.45f) * 1.33f
        val domePath = Path().apply {
            moveTo(px - lx, py)
            cubicTo(px - lx, top, px + lx, top, px + lx, py)
            arcOval(oval(px, py, lx, ly), 0f, 180f)
            close()
        }
        c.drawPath(domePath, gx.shaded(radial(px - lx * 0.3f, py - dome - ly * 0.2f, lx * 1.4f, arrayOf("rgba(255,244,230,0.16)", "rgba(220,235,245,0.07)", "rgba(200,220,235,0.03)"), floatArrayOf(0f, 0.5f, 1f))))
        // A broad soft reflection of the room across the far half of the dome.
        c.save()
        c.clipPath(domePath)
        c.drawOval(oval(px - lx * 0.2f, py - ly * 0.75f - dome * 0.4f, lx * 0.85f, ly * 0.55f), gx.blurred("#FFF1E4", 0.12, 10f))
        c.restore()
        // The dome's silhouette and a darker band where it meets the rim.
        val outline = Path().apply {
            moveTo(px - lx, py)
            cubicTo(px - lx, top, px + lx, top, px + lx, py)
        }
        c.drawPath(outline, gx.stroke("#F4F8FF", 1.2f, 0.22))
        c.drawArc(oval(px, py - dome * 0.18f, lx * 0.96f, ly * 0.96f), 185f, 170f, false, gx.stroke("#0B0E12", 2.4f, 0.12))
        // Streaks follow the dome's curve.
        val streak = Path().apply {
            moveTo(px - lx * 0.78f, py - ly * 0.1f)
            cubicTo(px - lx * 0.74f, py - dome - ly * 0.45f, px - lx * 0.25f, py - dome * 1.15f - ly * 0.45f, px + lx * 0.08f, py - dome * 1.05f - ly * 0.42f)
        }
        val sp = gx.stroke("#FFFFFF", 3.2f, 0.42)
        sp.maskFilter = gx.blur(1.1f)
        c.drawPath(streak, sp)
        val streak2 = Path().apply {
            moveTo(px + lx * 0.5f, py - dome * 0.7f - ly * 0.3f)
            quadTo(px + lx * 0.72f, py - dome * 0.35f - ly * 0.2f, px + lx * 0.8f, py - ly * 0.05f)
        }
        c.drawPath(streak2, gx.stroke("#FFFFFF", 1.6f, 0.3))
        // Rim.
        c.drawOval(oval(px, py, lx, ly), gx.strokeShaded(linear(px - lx, py - ly, px + lx, py + ly, arrayOf("#F4F6F8", "#9AA1A9", "#5D646C", "#C3C9CF", "#EEF1F4"), floatArrayOf(0f, 0.3f, 0.55f, 0.8f, 1f)), 3.2f))
        c.drawArc(oval(px, py - 1, lx, ly), 190f, 120f, false, gx.stroke("#FFFFFF", 1f, 0.6))
        // Knob on top.
        val kx = px
        val ky = py - dome * 1.15f
        c.drawOval(oval(kx, ky + 5, 11f, 4f), gx.blurred("#000000", 0.35, 2.5f))
        c.drawRect(kx - 3.5f, ky - 1, kx + 3.5f, ky + 5, gx.paint("#8E959C"))
        c.drawOval(oval(kx, ky - 3, 11f, 6.5f), gx.shaded(radial(kx - 3, ky - 6, 13f, arrayOf("#5A5352", "#2A2423", "#151111"), floatArrayOf(0f, 0.5f, 1f))))
        c.drawArc(oval(kx, ky - 4, 8f, 4f), 200f, 90f, false, gx.stroke("#FFFFFF", 1.2f, 0.45))
    }

    /** One corn: craggy puffs in cream and butter, a sliver of hull, a soft shadow. */
    private fun drawCorn(c: Canvas, rr: Float, v: Int, gx: PopcornGfx) {
        c.drawCircle(rr * 0.06f, rr * 0.2f, rr * 0.9f, gx.blurred("#1A0703", 0.3, rr * 0.16f))
        c.save()
        c.scale(1 + 0.16f * hash(v + 41).toFloat(), 1 - 0.08f * hash(v + 43).toFloat())
        val n = 7 + (v % 3)
        val lobes = FloatArray((n + 1) * 3)
        for (k in 0 until n) {
            val a = (k.toDouble() / n) * PI * 2 + hash(v * 13 + k) * 0.9
            val d = rr * (0.3 + 0.32 * hash(v * 7 + k + 1))
            lobes[k * 3] = (cos(a) * d).toFloat()
            lobes[k * 3 + 1] = (sin(a) * d * 0.9).toFloat()
            lobes[k * 3 + 2] = (rr * (0.22 + 0.3 * hash(v * 5 + k + 2))).toFloat()
        }
        lobes[n * 3] = -rr * 0.05f
        lobes[n * 3 + 1] = -rr * 0.08f
        lobes[n * 3 + 2] = rr * 0.4f
        val idx = (0..n).sortedBy { lobes[it * 3 + 1] + lobes[it * 3 + 2] * 0.3f }
        idx.forEachIndexed { j, k ->
            val x = lobes[k * 3]
            val y = lobes[k * 3 + 1]
            val r = lobes[k * 3 + 2]
            if (j == floor(idx.size * 0.6).toInt()) {
                c.save()
                c.rotate(-18 + 40 * hash(v + 3).toFloat(), rr * 0.2f, rr * 0.3f)
                c.drawOval(RectF(0f, rr * 0.2f, rr * 0.42f, rr * 0.42f), gx.shaded(linear(rr * 0.05f, rr * 0.2f, rr * 0.4f, rr * 0.44f, arrayOf("#D58E36", "#94501A", "#6E3810"))))
                c.restore()
            }
            val butter = (v + k) % 3 == 0
            val list = if (butter) arrayOf("#FFF6D6", "#F5DE9C", "#E2BC72", "#B88C4A") else arrayOf("#FFFBF1", "#F4E5C6", "#E2C691", "#B99560")
            c.drawCircle(x, y, r, gx.shaded(radial(x - r * 0.4f, y - r * 0.45f, r * 1.55f, list, floatArrayOf(0f, 0.38f, 0.72f, 1f))))
            // A darker crease on the lower right where the next puff folds in.
            c.drawArc(RectF(x - r, y - r, x + r, y + r), 10f, 120f, false, gx.stroke("#8E6232", max(0.5f, r * 0.12f), 0.28))
            c.drawCircle(x - r * 0.38f, y - r * 0.42f, r * 0.2f, gx.blurred("#FFFFFF", 0.45, r * 0.12f))
        }
        c.restore()
    }

    /** One kernel: an amber teardrop with a pale tip. */
    private fun drawKernel(c: Canvas, l: Float, v: Int, gx: PopcornGfx) {
        val w = l * (0.74f + 0.08f * (v % 2))
        c.drawOval(RectF(-w * 0.55f, l * 0.15f, w * 0.55f, l * 0.65f), gx.blurred("#050101", 0.5, l * 0.12f))
        val drop = Path().apply {
            moveTo(0f, -l / 2)
            cubicTo(w * 0.62f, -l * 0.28f, w * 0.52f, l / 2, 0f, l / 2)
            cubicTo(-w * 0.52f, l / 2, -w * 0.62f, -l * 0.28f, 0f, -l / 2)
            close()
        }
        val list = if (v == 3) arrayOf("#F2B957", "#D78A2A", "#9C5416") else arrayOf("#F9CF63", "#E59B30", "#AE5F18")
        c.drawPath(drop, gx.shaded(linear(-w / 2, 0f, w / 2, l * 0.2f, list, floatArrayOf(0f, 0.5f, 1f))))
        c.drawOval(RectF(-w * 0.2f, -l * 0.5f, w * 0.2f, -l * 0.24f), gx.paint("#F8EBC8", 0.75))
        c.drawLine(-w * 0.22f, -l * 0.18f, -w * 0.18f, l * 0.22f, gx.stroke("#FFFFFF", l * 0.08f, 0.45))
    }

    /** A red-and-cream striped bowl with a dome of popcorn on it. */
    private fun drawBowl(c: Canvas, g: PopcornLayout, gx: PopcornGfx) {
        val x = g.bowlX
        val y = g.bowlY
        val r = 6.5f
        val dome = intArrayOf(-11, 0, 1, -4, -5, 2, 4, -6, 3, 11, -1, 5, 0, 2, 0, -6, 3, 4, 7, 4, 1)
        for (i in dome.indices step 3) {
            c.save()
            c.translate(x + dome[i], y + dome[i + 1] - 1)
            c.scale(0.95f, 0.95f)
            drawCorn(c, r, dome[i + 2], gx)
            c.restore()
        }
        val tw = 44f
        val bw = 30f
        val hh = 38f
        val body = Path().apply {
            moveTo(x - tw / 2, y)
            lineTo(x + tw / 2, y)
            lineTo(x + bw / 2, y + hh)
            quadTo(x, y + hh + 3, x - bw / 2, y + hh)
            close()
        }
        c.drawOval(RectF(x - bw / 2 - 2, y + hh - 3, x + bw / 2 + 2, y + hh + 5), gx.blurred("#050102", 0.5, 3f))
        c.save()
        c.clipPath(body)
        c.drawRect(x - tw, y, x + tw, y + hh + 6, gx.paint("#F2E4CC"))
        for (s in -3..3 step 2) {
            val t0 = s * (tw / 7)
            val b0 = s * (bw / 7)
            val stripe = Path().apply {
                moveTo(x + t0, y)
                lineTo(x + t0 + tw / 7, y)
                lineTo(x + b0 + bw / 7, y + hh + 4)
                lineTo(x + b0, y + hh + 4)
                close()
            }
            c.drawPath(stripe, gx.paint("#C93A33"))
        }
        c.drawRect(x - tw, y, x + tw, y + hh + 6, gx.shaded(linear(x - tw / 2, 0f, x + tw / 2, 0f, arrayOf("rgba(40,6,6,0.5)", "rgba(255,255,255,0.12)", "rgba(40,6,6,0.05)", "rgba(40,6,6,0.55)"), floatArrayOf(0f, 0.3f, 0.6f, 1f))))
        c.restore()
        c.drawRoundRect(x - tw / 2 - 2, y - 2, x + tw / 2 + 2, y + 4, 3f, 3f, gx.shaded(linear(0f, y - 2, 0f, y + 5, arrayOf("#FFF7E8", "#E6D2B0", "#B79C74"))))
    }

    /** Bakes every static layer; checks for cancellation between them (the bake runs off the main thread). */
    suspend fun bakeLayers(g: PopcornLayout, top: Float, scale: Float): PopcornLayers {
        val gx = PopcornGfx(live = false)
        val ctx = coroutineContext
        val bg = PopcornGfx.bake(0f, 0f, g.w, g.h, scale) { drawRoom(it, g, top, gx) }
        ctx.ensureActive()
        val r = g.D / 2 + 34
        val plate = PopcornGfx.bake(g.bx - r, g.by - r, r * 2, r * 2 + 16, scale) { drawPlate(it, g, gx) }
        ctx.ensureActive()
        val panX = g.px - g.rx - g.D * 0.22f
        val pan = PopcornGfx.bake(panX, g.py - g.ry - 8, g.px + g.rx + 14 - panX, g.ry * 2 + g.wall + 30, scale) { drawPan(it, g, gx) }
        val lw = g.rx * 1.05f + 6
        val lid = PopcornGfx.bake(g.px - lw, g.py - g.dome - g.ry * 1.05f - 16, lw * 2, g.dome + g.ry * 2.1f + 26, scale) { drawLid(it, g, gx) }
        ctx.ensureActive()
        val bowl = PopcornGfx.bake(g.bowlX - 28, g.bowlY - 22, 56f, 70f, scale) { drawBowl(it, g, gx) }
        val cell = g.D * 0.125f
        val atlas = Bitmap.createBitmap(max(1, ceil(cell * 10 * scale).toInt()), max(1, ceil(cell * scale).toInt()), Bitmap.Config.ARGB_8888)
        val ac = Canvas(atlas)
        ac.scale(scale, scale)
        for (v in 0 until 10) {
            ac.save()
            ac.translate(cell * (v + 0.5f), cell * 0.5f)
            if (v < 4) drawKernel(ac, g.D * 0.03f, v, gx) else drawCorn(ac, g.D * 0.05f, v - 4, gx)
            ac.restore()
        }
        ctx.ensureActive()
        // The arc's gradient runs from its start (125°): gold, orange, red. A sweep starts at 0°, so rotate it.
        val arc = SweepGradient(
            g.bx, g.by,
            PopcornGfx.colors(arrayOf("#FFD86E", "#FF9F3E", "#F45A2F", "#D8302A", "#D8302A")),
            floatArrayOf(0f, 110f / 360, 210f / 360, POPCORN_ARC_SWEEP / 360, 1f),
        ).apply { setLocalMatrix(Matrix().apply { setRotate(POPCORN_ARC_START, g.bx, g.by) }) }
        val glow = radial(g.px, g.py + g.wall, g.rx * 1.35f, arrayOf("rgba(255,128,46,0.9)", "rgba(255,92,34,0.35)", "rgba(255,60,20,0)"), floatArrayOf(0f, 0.5f, 1f))
        val tint = radial(g.px, g.py + g.ry * 0.2f, g.rx, arrayOf("rgba(255,120,50,0.5)", "rgba(255,90,40,0.15)", "rgba(255,80,30,0)"), floatArrayOf(0f, 0.6f, 1f))
        return PopcornLayers(bg, plate, pan, lid, bowl, atlas, cell, arc, glow, tint)
    }
}

/** Draws a frame from the baked layers and a pose (on the screen's canvas, in dp). */
internal class PopcornPainter {
    private val gx = PopcornGfx(live = true)
    private val image = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val sprite = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val src = Rect()
    private val dst = RectF()

    private fun sprite(c: Canvas, l: PopcornLayers, v: Int, x: Float, y: Float, rot: Float, sx: Float, sy: Float, p: Paint) {
        val k = l.atlas.width / 10
        c.save()
        c.translate(x, y)
        c.rotate(rot)
        c.scale(sx, sy)
        src.set(k * v, 0, k * v + k, l.atlas.height)
        dst.set(-l.cell / 2, -l.cell / 2, l.cell / 2, l.cell / 2)
        c.drawBitmap(l.atlas, src, dst, p)
        c.restore()
    }

    /** The placeholder before the art is baked: the room's colour, the plate and the pan in flat shapes. */
    fun placeholder(c: Canvas, g: PopcornLayout) {
        c.drawColor(popcornColor("#1C090F"))
        c.drawCircle(g.bx, g.by, g.D / 2, gx.paint("#2E140F"))
        c.drawOval(oval(g.px, g.py, g.rx + 3, g.ry + 2.5f), gx.paint("#B8693F"))
        c.drawOval(oval(g.px, g.py + 1, g.rx - 3, g.ry - 2.5f), gx.paint("#221D1B"))
        c.drawRoundRect(g.cardX, g.cardY, g.cardX + g.cardW, g.cardY + g.cardH, 20f, 20f, gx.paint("#33171F"))
    }

    fun frame(c: Canvas, g: PopcornLayout, l: PopcornLayers, p: PopcornPose) {
        image.alpha = 255
        l.bg.draw(c, image)
        val s = 1 - 0.03f * p.press.toFloat()
        c.save()
        c.scale(s, s, g.bx, g.by)
        l.plate.draw(c, image)
        val heat = p.heat.toFloat()
        if (heat > 0.004f) {
            val sweep = POPCORN_ARC_SWEEP * heat
            val o = oval(g.bx, g.by, g.arcR, g.arcR)
            c.drawArc(o, POPCORN_ARC_START, sweep, false, gx.blurred("#FF7A2E", 0.5 * heat, 6f).also {
                it.style = Paint.Style.STROKE
                it.strokeWidth = 13f
                it.strokeCap = Paint.Cap.ROUND
            })
            c.drawArc(o, POPCORN_ARC_START, sweep, false, gx.strokeShaded(l.arc, 6f, round = true))
            val a = (POPCORN_ARC_START + sweep) * PI.toFloat() / 180
            c.drawCircle(g.bx + cos(a) * g.arcR, g.by + sin(a) * g.arcR, 2.2f, gx.paint("#FFF4DA", 0.9))
        }
        c.save()
        c.translate(p.shakeX.toFloat(), p.shakeY.toFloat())
        if (heat > 0.004f) c.drawOval(oval(g.px, g.py + g.wall, g.rx * 1.35f, g.ry * 1.5f), gx.shaded(l.glow, 0.75 * heat))
        l.pan.draw(c, image)
        if (heat > 0.004f) {
            val tint = gx.shaded(l.tint, 0.55 * heat)
            tint.xfermode = PopcornGfx.screen
            c.drawOval(oval(g.px, g.py + 1, g.rx - 3, g.ry - 2.5f), tint)
        }
        for (i in 0 until p.kernelCount) {
            val o = i * 5
            sprite.alpha = (255 * p.kernels[o + 3]).toInt().coerceIn(0, 255)
            sprite(c, l, p.kernels[o + 4].toInt(), p.kernels[o].toFloat(), p.kernels[o + 1].toFloat(), p.kernels[o + 2].toFloat(), 1f, 1f, sprite)
        }
        for (i in 0 until p.fleckCount) {
            val o = i * 3
            val x = p.flecks[o].toFloat()
            val y = p.flecks[o + 1].toFloat()
            c.drawOval(x - 1.6f, y - 1f, x + 1.6f, y + 1f, gx.paint("#8A4A18", p.flecks[o + 2]))
        }
        for (i in 0 until p.cornCount) {
            val o = i * 6
            sprite(c, l, 4 + p.corns[o + 5].toInt(), p.corns[o].toFloat(), p.corns[o + 1].toFloat(), p.corns[o + 2].toFloat(), p.corns[o + 3].toFloat(), p.corns[o + 4].toFloat(), image)
        }
        c.save()
        c.translate(g.px + p.lidX.toFloat(), g.py + p.lidY.toFloat())
        c.rotate(p.lidRot.toFloat())
        c.translate(-g.px, -g.py)
        l.lid.draw(c, image)
        c.restore()
        c.restore()
        c.restore()
        for (i in 0 until p.flyingCount) {
            val o = i * 5
            val k = p.flying[o + 3].toFloat()
            sprite(c, l, 4 + p.flying[o + 4].toInt(), p.flying[o].toFloat(), p.flying[o + 1].toFloat(), p.flying[o + 2].toFloat(), k, k, image)
        }
        val b = 1 + 0.08f * p.bowl.toFloat()
        c.save()
        c.scale(2 - b, b, g.bowlX, g.bowlY + 36)
        l.bowl.draw(c, image)
        c.restore()
    }
}

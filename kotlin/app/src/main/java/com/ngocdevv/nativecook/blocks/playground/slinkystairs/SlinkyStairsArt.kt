package com.ngocdevv.nativecook.blocks.playground.slinkystairs

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import com.ngocdevv.nativecook.blocks.playground.slinkystairs.SlinkyGfx.Companion.linear
import com.ngocdevv.nativecook.blocks.playground.slinkystairs.SlinkyGfx.Companion.radial
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.coroutineContext
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/*
 Slinky Stairs — the drawing, ported from slinkyArt.ts. Baked once (off the main thread): a slate
 wall lit by a warm desk lamp from the upper left, a three-step maple staircase standing on a
 walnut desk (top faces, front faces, grain, lit edges, a vignette and grain) and the bottom
 panel's plate. Per frame: soft contact shadows, then the 40 rings as five batched strokes — the
 far half of every ring in dark steel, then the near halves in steel, a cool highlight where the
 lamp catches them, a thin warm rim and a dark lower edge.
*/

internal class SlinkyLayout(val w: Float, val h: Float, top: Float, bottom: Float) {
    val left = 12f
    val tread = (w - left * 2) / 4
    val panelH = 120f
    val panelY = h - max(bottom, 12f) - panelH
    val R = min(36f, tread * 0.4f)
    val pitch = 1.7f
    val stepY: FloatArray
    val riser: Float
    val deskEdge: Float
    val geo: SlinkyGeo

    init {
        val hh = (SLINKY_N - 1) * pitch
        var y0 = top + 120 + hh + 56
        var y3 = panelY - 96
        riser = max(60f, min(92f, (y3 - y0) / 3))
        // Centre the stairs in the room left between the header and the desk's front edge.
        y0 = max(y0, (y0 + y3) / 2 - riser * 1.5f)
        y3 = y0 + riser * 3
        stepY = floatArrayOf(y0, y0 + riser, y0 + riser * 2, y3)
        deskEdge = min(panelY - 24, y3 + 64)
        val baseX = DoubleArray(4) { (left + tread * (it + 0.5f)).toDouble() }
        val baseY = DoubleArray(4) { (stepY[it] - 8).toDouble() }
        geo = SlinkyGeo(w.toDouble(), h.toDouble(), baseX, baseY, R.toDouble(), pitch.toDouble(), 0.3, tread.toDouble(), riser.toDouble())
    }
}

internal object SlinkyStairsArt {
    private fun hash(i: Int): Double {
        val s = sin(i * 12.9898 + 78.233) * 43758.5453
        return s - floor(s)
    }

    /** Wood grain: long, slightly wavy strokes inside a rect. */
    private fun woodGrain(c: Canvas, x: Float, y: Float, w: Float, h: Float, seed: Int, vertical: Boolean, gx: SlinkyGfx) {
        c.save()
        c.clipRect(x, y, x + w, y + h)
        val n = ((if (vertical) w else h) / 5).roundToInt()
        val b = Path()
        for (i in 0 until n) {
            b.reset()
            val off = ((i + hash(seed + i) * 0.8) * 5).toFloat()
            val amp = 1 + 2 * hash(seed + i * 3)
            val len = if (vertical) h else w
            for (s in 0..12) {
                val a = s / 12f * len
                val wv = (sin((s / 12.0) * PI * (1 + hash(seed + i * 5) * 2) + i) * amp).toFloat()
                if (vertical) {
                    if (s == 0) b.moveTo(x + off + wv, y + a) else b.lineTo(x + off + wv, y + a)
                } else if (s == 0) {
                    b.moveTo(x + a, y + off + wv)
                } else {
                    b.lineTo(x + a, y + off + wv)
                }
            }
            c.drawPath(b, gx.stroke(if (i % 3 == 0) "#5A3618" else "#7A4F28", 0.7f, 0.18 + 0.12 * hash(seed + i * 7)))
        }
        c.restore()
    }

    private fun drawScene(c: Canvas, g: SlinkyLayout, top: Float, gx: SlinkyGfx) {
        val w = g.w
        val h = g.h
        val full = RectF(0f, 0f, w, h)
        c.drawRect(full, gx.shaded(linear(0f, 0f, 0f, h, arrayOf("#344241", "#2A3635", "#212B2A", "#1A2221"), floatArrayOf(0f, 0.4f, 0.75f, 1f))))
        c.drawRect(full, gx.shaded(radial(w * 0.08f, top + 40, w * 1.25f, arrayOf("rgba(255,214,150,0.42)", "rgba(240,180,110,0.16)", "rgba(200,150,90,0)"), floatArrayOf(0f, 0.45f, 1f))))
        SlinkyGrain(2).draw(c, full, 0.06)
        val y3 = g.stepY[3]
        // The desk: a walnut top that runs towards us from under the stairs, then its front edge.
        val e = g.deskEdge
        c.drawRect(0f, y3 - 18, w, e, gx.shaded(linear(0f, y3 - 18, 0f, e, arrayOf("#5E412C", "#6E4D34", "#7A563A"), floatArrayOf(0f, 0.5f, 1f))))
        woodGrain(c, 0f, y3 - 18, w, e - y3 + 18, 40, false, gx)
        c.drawRect(0f, y3 - 18, w, e, gx.shaded(radial(w * 0.3f, y3 + 10, w * 0.7f, arrayOf("rgba(255,210,150,0.16)", "rgba(255,210,150,0)"))))
        c.drawRect(0f, e, w, h, gx.shaded(linear(0f, e, 0f, h, arrayOf("#3A271A", "#2B1C12", "#1E140D"))))
        c.drawLine(0f, e + 0.5f, w, e + 0.5f, gx.stroke("#D9A47A", 1.2f, 0.5))
        c.drawLine(0f, y3 - 18, w, y3 - 18, gx.stroke("#2A1A0E", 1f, 0.4))
        // Shadow the stairs cast to the right on the desk.
        val sx = g.left + g.tread * 3
        c.drawRect(sx - 6, y3 - 16, sx + 34, y3 - 2, gx.shaded(linear(sx, 0f, sx + 40, 0f, arrayOf("rgba(10,6,3,0.45)", "rgba(10,6,3,0)"))))
        // Three steps, each a maple block from its tread down to the desk.
        val tones = arrayOf("#C8915A", "#BD8650", "#B27C47")
        for (k in 0 until 3) {
            val x = g.left + g.tread * k
            val yk = g.stepY[k]
            c.drawRect(x, yk, x + g.tread, y3, gx.shaded(linear(x, yk, x, y3, arrayOf(tones[k], "#9A6838", "#7E522A"), floatArrayOf(0f, 0.6f, 1f))))
            woodGrain(c, x, yk, g.tread, y3 - yk, 10 + k * 7, true, gx)
            // Top face (seen from a little above), lit by the lamp.
            c.drawRect(x, yk - 16, x + g.tread, yk, gx.shaded(linear(x, yk - 16, x + g.tread, yk, arrayOf("#F0C48C", "#DDAA70", "#C99459"))))
            woodGrain(c, x, yk - 16, g.tread, 16f, 30 + k, false, gx)
            c.drawLine(x, yk + 0.5f, x + g.tread, yk + 0.5f, gx.stroke("#FFE4BC", 1.2f, 0.7))
            c.drawLine(x, yk - 16, x + g.tread, yk - 16, gx.stroke("#4A2E16", 1f, 0.35))
            // The crease where this block meets the next, lower one.
            c.drawRect(x + g.tread - 10, yk, x + g.tread, y3, gx.shaded(linear(x + g.tread - 10, 0f, x + g.tread, 0f, arrayOf("rgba(40,20,8,0)", "rgba(40,20,8,0.45)"))))
            c.drawLine(x + 0.5f, yk - 16, x + 0.5f, y3, gx.stroke("#FFE2B8", 1f, if (k == 0) 0.5 else 0.18))
        }
        // Ambient occlusion where the steps meet the desk.
        c.drawRect(g.left, y3 - 24, g.left + g.tread * 3, y3, gx.shaded(linear(0f, y3 - 24, 0f, y3, arrayOf("rgba(20,10,4,0)", "rgba(20,10,4,0.35)"))))
        // The panel's plate.
        val px = 16f
        val pw = w - 32
        c.drawRoundRect(px, g.panelY + 10, px + pw, g.panelY + 10 + g.panelH, 22f, 22f, gx.blurred("#0A0705", 0.5, 14f))
        c.drawRoundRect(px, g.panelY, px + pw, g.panelY + g.panelH, 22f, 22f, gx.shaded(linear(0f, g.panelY, 0f, g.panelY + g.panelH, arrayOf("#2E2A27", "#24201D", "#1D1A17"))))
        c.drawRoundRect(px + 0.5f, g.panelY + 0.5f, px + pw - 0.5f, g.panelY + g.panelH - 0.5f, 21.5f, 21.5f, gx.stroke("#FFEBD2", 1f, 0.1))
        c.drawRect(full, gx.shaded(radial(w / 2, h * 0.45f, hypot(w, h) * 0.62f, arrayOf("rgba(8,10,10,0)", "rgba(8,10,10,0.25)", "rgba(6,8,8,0.6)"), floatArrayOf(0f, 0.6f, 1f))))
    }

    suspend fun bakeScene(g: SlinkyLayout, top: Float, scale: Float): SlinkyBaked {
        coroutineContext.ensureActive()
        return SlinkyGfx.bake(0f, 0f, g.w, g.h, scale) { drawScene(it, g, top, SlinkyGfx(live = false)) }
    }
}

/** Draws a frame on the screen's canvas (in dp). */
internal class SlinkyPainter {
    private val gx = SlinkyGfx(live = true)
    private val image = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val paths = Array(5) { Path() }
    private val far = strokePaint("#68727A", 2.6f, 1.0)
    private val near = strokePaint("#A6B0B9", 3f, 1.0)
    private val glint = strokePaint("#EEF3F7", 1.4f, 0.9)
    private val warm = strokePaint("#F3C88E", 1.1f, 0.65)
    private val edge = strokePaint("#2E343A", 1.2f, 0.5)
    private val oval = RectF()

    private fun strokePaint(color: String, width: Float, alpha: Double) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = slinkyColor(color)
        if (alpha < 1) this.alpha = (255 * alpha).roundToInt()
        style = Paint.Style.STROKE
        strokeWidth = width
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    /** Adds the arc of every ring between angles a0 and a1 to one path. */
    private fun arcs(b: Path, g: SlinkyGeo, p: SlinkyPose, a0: Double, a1: Double, steps: Int): Path {
        b.reset()
        val r = g.R
        val m = g.R * g.tilt
        for (i in 0 until p.ringCount) {
            val o = i * 4
            val cx = p.rings[o]
            val cy = p.rings[o + 1]
            val nx = p.rings[o + 2]
            val ny = p.rings[o + 3]
            for (s in 0..steps) {
                val a = a0 + (a1 - a0) * s / steps
                val ca = cos(a)
                val x = (cx + nx * r * ca).toFloat()
                val y = (cy + ny * r * ca - m * sin(a)).toFloat()
                if (s == 0) b.moveTo(x, y) else b.lineTo(x, y)
            }
        }
        return b
    }

    /** Before the scene is baked: the wall, the steps and the stack in flat shapes. */
    fun placeholder(c: Canvas, g: SlinkyLayout) {
        c.drawColor(slinkyColor("#26302F"))
        val y3 = g.stepY[3]
        c.drawRect(0f, y3 - 18, g.w, g.h, gx.paint("#4A3424"))
        for (k in 0 until 3) {
            val x = g.left + g.tread * k
            c.drawRect(x, g.stepY[k] - 16, x + g.tread, y3, gx.paint("#B27C47"))
        }
        c.drawRoundRect(16f, g.panelY, g.w - 16, g.panelY + g.panelH, 22f, 22f, gx.paint("#24201D"))
    }

    fun frame(c: Canvas, g: SlinkyLayout, scene: SlinkyBaked, p: SlinkyPose, tx: Float, ty: Float, touch: Float) {
        image.alpha = 255
        scene.draw(c, image)
        val geo = g.geo
        for (i in 0 until p.shadowCount) {
            val o = i * 4
            val a = p.shadows[o + 3]
            if (a < 0.01) continue
            val r = p.shadows[o + 2].toFloat()
            val x = p.shadows[o].toFloat() - r * 1.08f + 3
            val y = p.shadows[o + 1].toFloat() - r * 0.3f * 0.7f + 2
            oval.set(x, y, x + r * 2.16f, y + r * 0.3f * 1.6f)
            c.drawOval(oval, gx.blurred("#120905", 0.5 * a, 4f))
        }
        c.drawPath(arcs(paths[0], geo, p, 0.0, PI, 10), far)
        c.drawPath(arcs(paths[1], geo, p, PI, 2 * PI, 10), near)
        c.drawPath(arcs(paths[2], geo, p, PI * 1.18, PI * 1.62, 5), glint)
        c.drawPath(arcs(paths[3], geo, p, PI * 1.04, PI * 1.2, 3), warm)
        c.drawPath(arcs(paths[4], geo, p, PI * 1.8, PI * 1.95, 3), edge)
        if (touch > 0.01f) {
            c.drawCircle(tx, ty, 26f, gx.blurred("#FFFFFF", 0.14 * touch, 8f))
            c.drawCircle(tx, ty, 22f, gx.stroke("#FFFFFF", 1.2f, 0.3 * touch))
        }
    }
}

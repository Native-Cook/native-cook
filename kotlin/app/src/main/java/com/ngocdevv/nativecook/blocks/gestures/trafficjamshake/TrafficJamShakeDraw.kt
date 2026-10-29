package com.ngocdevv.nativecook.blocks.gestures.trafficjamshake

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import kotlin.math.max
import kotlin.math.min

// The car drawing of TrafficJamShake.swift: a flat car in oblique projection (front face flat to
// the viewer, the body extruded 34° up-right), one solid colour per face, thin dark outlines and a
// hard ground shadow. Coordinates are design points; the caller scales the canvas.

internal object TrafficJamShakePalette {
    val background = Color(0xFFECEBE7)
    val ink = Color(0xFF1B1B1F)
    val outline = Color(0xFF1E1E24)
    val shadow = Color(0xFF141418)
    val window = Color(0xFF121216)
    val light = Color(0xFFFFE01F)
    val hub = Color(0xFFF4F1E8)
    val card = Color.White
    val red = Color(0xFFD7263D)
    val redSoft = Color(0xFFFBE3E6)
    val green = Color(0xFF22A447)
    val orange = Color(0xFFF28A0C)
    val jam = Color(0xFFE0102A)

    /** Per body colour: front, top (14 % white), side (12 % black). Pink, blue, green, orange, red. */
    val bodies = listOf(
        Triple(Color(0xFFF38BF0), Color(0xFFF59BF2), Color(0xFFD67AD3)),
        Triple(Color(0xFF139FE6), Color(0xFF34ACEA), Color(0xFF118CCA)),
        Triple(Color(0xFF17A833), Color(0xFF37B450), Color(0xFF14942D)),
        Triple(Color(0xFFF48C10), Color(0xFFF69C31), Color(0xFFD77B0E)),
        Triple(Color(0xFFE1121F), Color(0xFFE5333E), Color(0xFFC6101B)),
    )
}

internal class TrafficJamShakePainter {
    private val m = TrafficJamShakeMotion
    private val p = TrafficJamShakePalette
    private val path = Path()
    private val outline = Stroke(width = 1f, join = StrokeJoin.Round)
    private val thin = Stroke(width = 0.8f)
    private val honkStroke = Stroke(width = 3.5f, cap = StrokeCap.Round)

    private fun DrawScope.poly(xs: DoubleArray, ys: DoubleArray, color: Color, stroke: Boolean = true) {
        path.reset()
        path.moveTo(xs[0].toFloat(), ys[0].toFloat())
        for (i in 1 until xs.size) path.lineTo(xs[i].toFloat(), ys[i].toFloat())
        path.close()
        drawPath(path, color)
        if (stroke) drawPath(path, p.outline, style = outline)
    }

    /** The quad shrunk towards its centre by [k]. */
    private fun inset(xs: DoubleArray, ys: DoubleArray, k: Double): Pair<DoubleArray, DoubleArray> {
        val cx = xs.average()
        val cy = ys.average()
        return DoubleArray(xs.size) { cx + (xs[it] - cx) * k } to DoubleArray(ys.size) { cy + (ys[it] - cy) * k }
    }

    /**
     * One car in its own frame (the scope is already at the ground point, turned by its angle).
     * [e] stretches the width and squashes the heights; wheels and lights keep their size.
     */
    fun DrawScope.car(c: TrafficJamShakeCar, e: Double, z: Double) {
        val (front, top, side) = p.bodies[c.color]
        val w = c.w * (1 + e)
        val k = max(0.6, 1 - 0.6 * e)
        val hb = c.hb * k
        val hc = c.hc * k
        val dx = c.l * m.dirX
        val dy = c.l * m.dirY

        // The hard ground shadow stays on the ground and pales as the car lifts.
        poly(
            doubleArrayOf(-w / 2, w / 2, w / 2 + dx, -w / 2 + dx),
            doubleArrayOf(6.0, 6.0, 6 + dy, 6 + dy),
            p.shadow.copy(alpha = max(0.35, 1 - z / 50).toFloat()), stroke = false,
        )

        translate(0f, (-z).toFloat()) {
            val l = -w / 2
            val r = w / 2
            // Body side, then wheels on its bottom edge, then the top.
            poly(doubleArrayOf(r, r, r + dx, r + dx), doubleArrayOf(0.0, -hb, -hb + dy, dy), side)
            val rw = min(8.0, max(5.0, 0.3 * c.hb))
            for (f in doubleArrayOf(0.22, 0.78)) {
                val cx = r + f * dx
                val cy = f * dy - rw * 0.15
                drawOval(p.window, Offset((cx - rw * 0.62).toFloat(), (cy - rw).toFloat()), Size((rw * 1.24).toFloat(), (rw * 2).toFloat()))
                drawOval(p.hub, Offset((cx - rw * 0.3).toFloat(), (cy - rw * 0.52).toFloat()), Size((rw * 0.6).toFloat(), (rw * 1.04).toFloat()))
            }
            poly(doubleArrayOf(l, r, r + dx, l + dx), doubleArrayOf(-hb, -hb, -hb + dy, -hb + dy), top)

            // Cabin: set back 18-82 % of the length, narrower at the roof.
            val c0x = 0.18 * dx
            val c0y = 0.18 * dy
            val cdx = 0.64 * dx
            val cdy = 0.64 * dy
            val fblX = -0.42 * w + c0x; val fblY = -hb + c0y
            val fbrX = 0.42 * w + c0x; val fbrY = -hb + c0y
            val ftrX = 0.33 * w + c0x; val ftrY = -hb - hc + c0y
            val ftlX = -0.33 * w + c0x; val ftlY = -hb - hc + c0y
            val sideX = doubleArrayOf(fbrX, ftrX, ftrX + cdx, fbrX + cdx)
            val sideY = doubleArrayOf(fbrY, ftrY, ftrY + cdy, fbrY + cdy)
            poly(sideX, sideY, side)
            val (wx, wy) = inset(sideX, sideY, 0.7)
            poly(wx, wy, p.window, stroke = false)
            drawLine(
                side,
                Offset(((wx[0] + wx[3]) / 2).toFloat(), ((wy[0] + wy[3]) / 2).toFloat()),
                Offset(((wx[1] + wx[2]) / 2).toFloat(), ((wy[1] + wy[2]) / 2).toFloat()),
                strokeWidth = 2.5f,
            )
            poly(doubleArrayOf(ftlX, ftrX, ftrX + cdx, ftlX + cdx), doubleArrayOf(ftlY, ftrY, ftrY + cdy, ftlY + cdy), top)
            val frontX = doubleArrayOf(fblX, fbrX, ftrX, ftlX)
            val frontY = doubleArrayOf(fblY, fbrY, ftrY, ftlY)
            poly(frontX, frontY, front)
            val (sx, sy) = inset(frontX, frontY, 0.78)
            poly(sx, sy, p.window, stroke = false)

            // Body front and its lights.
            poly(doubleArrayOf(l, r, r, l), doubleArrayOf(0.0, 0.0, -hb, -hb), front)
            val lw = max(8.0, 0.12 * w)
            val lh = max(4.0, 0.13 * hb)
            val ly = -0.55 * hb - lh / 2
            for (x in doubleArrayOf(l + 0.08 * w, r - 0.08 * w - lw)) {
                drawRect(p.light, Offset(x.toFloat(), ly.toFloat()), Size(lw.toFloat(), lh.toFloat()))
                drawRect(p.outline, Offset(x.toFloat(), ly.toFloat()), Size(lw.toFloat(), lh.toFloat()), style = thin)
            }
        }
    }

    /** Three strokes fanning out from the car's roof corner, [age] seconds after the honk. */
    fun DrawScope.honk(c: TrafficJamShakeCar, e: Double, z: Double, age: Double, alpha: Double) {
        if (age >= m.HONK_LIFE || alpha <= 0) return
        val q = age / m.HONK_LIFE
        val fade = (if (q < 0.15) q / 0.15 else 1 - m.smooth(q, 0.5, 1.0)) * alpha
        val w = c.w * (1 + e)
        val k = max(0.6, 1 - 0.6 * e)
        val ox = -0.36 * w
        val oy = -c.hb * k - c.hc * k * 0.7 - z
        val r0 = 6 + 10 * q
        val len = 12 * (1 - 0.4 * q)
        path.reset()
        for ((ux, uy) in listOf(-0.99 to -0.15, -0.75 to -0.66, -0.3 to -0.95)) {
            path.moveTo((ox + ux * r0).toFloat(), (oy + uy * r0).toFloat())
            path.lineTo((ox + ux * (r0 + len)).toFloat(), (oy + uy * (r0 + len)).toFloat())
        }
        drawPath(path, p.ink.copy(alpha = fade.toFloat().coerceIn(0f, 1f)), style = honkStroke)
    }
}

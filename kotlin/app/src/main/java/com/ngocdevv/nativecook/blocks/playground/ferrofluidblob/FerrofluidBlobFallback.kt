package com.ngocdevv.nativecook.blocks.playground.ferrofluidblob

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

// Below Android 13 there are no runtime shaders, so the scene is drawn instead: the slate and its
// pool of light, the dish as layered ellipses, the fluid as a dark pool with the softbox's sheen,
// and each crown as a heap with a field of shaded cones on the same hexagonal lattice, the same
// heights and the same lean as the shader's, painted from the far side forward. Same geometry and
// camera as FERRO_AGSL, in dp.

private const val SPACING = 23.0
private const val SPIKE_H = 62.0
private const val CROWN_R = 46.0
private const val SPIKE_ON = 0.2
private const val MOUND_H = 17.0
private const val MOUND_R = 52.0
private const val REACH = 120.0
private const val RIM_H = 16.0
private val RowStep = SPACING * 1.7320508

/** One spike to paint: where its foot is on the screen, where its tip is, how wide its foot is. */
private class Cone {
    var depth = 0.0
    var bx = 0f
    var by = 0f
    var tx = 0f
    var ty = 0f
    var w = 0f
}

internal class FerroFallback {
    private val path = Path()
    private val cones = Array(400) { Cone() }
    private val order = ArrayList<Cone>(400)

    private fun ground(cx: Double, cy: Double, x: Double, y: Double, h: Double) =
        Offset((cx + x).toFloat(), (cy + y * SinE - h * CosE).toFloat())

    private fun DrawScope.ellipse(c: Offset, rx: Double, brush: Brush) {
        val ry = rx * SinE
        drawOval(brush, Offset(c.x - rx.toFloat(), c.y - ry.toFloat()), Size(2 * rx.toFloat(), 2 * ry.toFloat()))
    }

    private fun DrawScope.ellipse(c: Offset, rx: Double, color: Color) {
        val ry = rx * SinE
        drawOval(color, Offset(c.x - rx.toFloat(), c.y - ry.toFloat()), Size(2 * rx.toFloat(), 2 * ry.toFloat()))
    }

    fun DrawScope.draw(width: Double, height: Double, cx: Double, cy: Double, dishR: Double, sim: FerroSim) {
        drawRect(FerroBackground, Offset.Zero, Size(width.toFloat(), height.toFloat()))
        // A gradient of radius 0 throws, and the dish's rings need room: nothing more to draw yet.
        if (dishR < 40) return
        val centre = ground(cx, cy, 0.0, 0.0, 0.0)
        // The slate under a pool of light, and the dish's soft shadow toward the viewer.
        drawRect(
            Brush.radialGradient(
                0f to Color(0xFF3B3B3E),
                0.45f to Color(0xFF1E1E20),
                1f to Color(0xFF0B0B0C),
                center = centre,
                radius = (dishR * 2.4).toFloat(),
            ),
            Offset.Zero,
            Size(width.toFloat(), height.toFloat()),
        )
        val shadowAt = ground(cx, cy, 18.0, 34.0, 0.0)
        ellipse(
            shadowAt,
            dishR + 60,
            Brush.radialGradient(
                0f to Color(0xD0000000),
                0.72f to Color(0x90000000),
                1f to Color(0x00000000),
                center = shadowAt,
                radius = (dishR + 60).toFloat(),
            ),
        )
        // The dish: its foot on the table, the lip, the inner wall, the floor under the fluid.
        val lip = ground(cx, cy, 0.0, 0.0, RIM_H)
        ellipse(ground(cx, cy, 0.0, 0.0, 2.0), dishR + 12, Color(0xFF6E6C69))
        ellipse(
            lip,
            dishR + 4,
            Brush.verticalGradient(
                0f to Color(0xFF8E8C88),
                0.5f to Color(0xFFC9C6C0),
                1f to Color(0xFFA9A6A1),
                startY = (lip.y - (dishR + 4) * SinE).toFloat(),
                endY = (lip.y + (dishR + 4) * SinE).toFloat(),
            ),
        )
        val wall = ground(cx, cy, 0.0, 0.0, RIM_H * 0.55)
        ellipse(
            wall,
            dishR - 14,
            Brush.verticalGradient(
                0f to Color(0xFFDCD9D3),
                0.35f to Color(0xFF8C8A86),
                1f to Color(0xFF5E5C59),
                startY = (wall.y - (dishR - 14) * SinE).toFloat(),
                endY = (wall.y + (dishR - 14) * SinE).toFloat(),
            ),
        )
        // The pool: near black, the softbox's sheen across its far half.
        val pool = ground(cx, cy, 0.0, 0.0, LEVEL)
        val poolR = dishR - 28
        ellipse(
            pool,
            poolR,
            Brush.verticalGradient(
                0f to Color(0xFF3A3A3C),
                0.18f to Color(0xFF151517),
                0.45f to Color(0xFF0A0A0C),
                1f to Color(0xFF070708),
                startY = (pool.y - poolR * SinE).toFloat(),
                endY = (pool.y + poolR * SinE).toFloat(),
            ),
        )

        // The crowns: a heap under each magnet, then every spike of both, far ones first.
        var count = 0
        for (g in sim.m) {
            if (g.field < 0.01 && g.spike < 0.002) continue
            val heapAt = ground(cx, cy, g.x, g.y, LEVEL + MOUND_H * max(0.0, g.field) * 0.5)
            val heapR = MOUND_R * 1.6
            ellipse(
                heapAt,
                heapR,
                Brush.radialGradient(
                    0f to Color(0xFF050506).copy(alpha = min(1.0, g.field).toFloat()),
                    0.55f to Color(0xFF0A0A0B).copy(alpha = (0.8 * min(1.0, g.field)).toFloat()),
                    1f to Color.Transparent,
                    center = heapAt,
                    radius = heapR.toFloat(),
                ),
            )
            val amp = max(g.spike, 0.0)
            if (amp <= 0.002) continue
            for (row in -4..4) {
                for (k in -6..6) {
                    for (half in 0..1) {
                        val qx = k * SPACING + half * SPACING * 0.5
                        val qy = row * RowStep + half * RowStep * 0.5
                        val dc = sqrt(qx * qx + qy * qy)
                        if (dc > REACH) continue
                        val field = amp * exp(-dc * dc / (2 * CROWN_R * CROWN_R))
                        val tall = SPIKE_H * max(0.0, field - SPIKE_ON) / (1 - SPIKE_ON)
                        if (tall < 0.8 || count >= cones.size) continue
                        // The tips lean in toward the magnet and back against its motion.
                        var lx = if (dc > 0.5) -qx / dc * min(dc, 70.0) * 0.08 else 0.0
                        var ly = if (dc > 0.5) -qy / dc * min(dc, 70.0) * 0.08 else 0.0
                        lx = (lx + g.leanX) * min(amp, 1.0)
                        ly = (ly + g.leanY) * min(amp, 1.0)
                        val len = sqrt(lx * lx + ly * ly)
                        val most = SPACING * 0.5 * 0.6
                        if (len > most) {
                            lx *= most / len
                            ly *= most / len
                        }
                        val gx = g.x + qx
                        val gy = g.y + qy
                        if (gx * gx + gy * gy > (dishR - 40) * (dishR - 40)) continue
                        val heap = MOUND_H * g.field * exp(-dc * dc / (2 * MOUND_R * MOUND_R))
                        val base = ground(cx, cy, gx, gy, LEVEL + heap)
                        val tip = ground(cx, cy, gx + lx, gy + ly, LEVEL + heap + tall)
                        val c = cones[count++]
                        c.depth = gy
                        c.bx = base.x
                        c.by = base.y
                        c.tx = tip.x
                        c.ty = tip.y
                        c.w = (SPACING * 0.42 * min(1.0, 0.45 + tall / SPIKE_H)).toFloat()
                    }
                }
            }
        }
        order.clear()
        for (i in 0 until count) order.add(cones[i])
        order.sortBy { it.depth }
        for (c in order) cone(c)
    }

    /** One spike: concave flanks rising to a sharp tip, lit from the left and behind the viewer. */
    private fun DrawScope.cone(c: Cone) {
        val w = c.w
        val dx = c.tx - c.bx
        val dy = c.ty - c.by
        path.reset()
        path.moveTo(c.bx - w, c.by)
        path.quadraticTo(c.bx - w * 0.18f + dx * 0.35f, c.by + dy * 0.3f, c.tx, c.ty)
        path.quadraticTo(c.bx + w * 0.18f + dx * 0.35f, c.by + dy * 0.3f, c.bx + w, c.by)
        path.quadraticTo(c.bx, c.by + w * SinE.toFloat() * 0.9f, c.bx - w, c.by)
        path.close()
        drawPath(
            path,
            Brush.horizontalGradient(
                0f to Color(0xFF151518),
                0.28f to Color(0xFF6E6B66),
                0.42f to Color(0xFF26262A),
                1f to Color(0xFF040405),
                startX = c.bx - w,
                endX = c.bx + w,
            ),
        )
        drawPath(path, Color(0x22FFFFFF), style = Stroke(width = 0.6f))
    }
}

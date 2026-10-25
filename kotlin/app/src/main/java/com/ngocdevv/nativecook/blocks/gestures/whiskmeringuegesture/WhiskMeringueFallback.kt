package com.ngocdevv.nativecook.blocks.gestures.whiskmeringuegesture

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.lerp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.sin

/*
 Below Android 13 there is no RuntimeShader: the counter, the bowl and its contents drawn with
 gradients and paths in dp. The contents blend from a pale yellow film of whites to white foam with
 `foam`; the swirl turns a set of spiral streaks (stronger as peaks rise) and the lifted peak is a
 bright soft spot with a shadow. No Voronoi bubbles, no refraction.
 */
internal class WhiskFallback {
    private val streaks = Path()
    private val contents = Path()

    fun DrawScope.draw(g: WhiskGeo, p: DoubleArray) {
        val w = g.w.toFloat()
        val h = g.h.toFloat()
        val cx = g.cx.toFloat()
        val cy = g.cy.toFloat()
        val r = g.r.toFloat()
        val sq = WhiskSquash.toFloat()
        val u = g.unit.toFloat()
        drawRect(Brush.linearGradient(listOf(Color(0xFFE0DACF), Color(0xFFC2BBB0)), Offset.Zero, Offset(w * 0.4f, h)))
        // the bowl's shadow, offset down right
        val sx = cx + 0.16f * r
        val sy = cy + 0.34f * r * sq
        drawOval(
            Brush.radialGradient(listOf(Color(0x47000000), Color(0x00000000)), Offset(sx, sy), r * 1.12f),
            Offset(sx - r * 1.12f, sy - r * 1.12f * sq),
            Size(r * 2.24f, r * 2.24f * sq),
        )
        // the glass's inner wall
        drawOval(Color(0xFFC7C9C3), Offset(cx - r, cy - r * sq), Size(r * 2, r * 2 * sq))
        // the contents' surface: a circle a little lower and smaller than the rim, rising as the whites foam
        val fo = p[F_FOAM].toFloat()
        val rs = 0.80f + 0.10f * fo
        val off = 0.17f + (0.10f - 0.17f) * fo
        val scx = cx
        val scy = cy + off * r * sq
        val sr = rs * r
        val rect = Rect(scx - sr, scy - sr * sq, scx + sr, scy + sr * sq)
        contents.reset()
        contents.addOval(rect)
        val liquid = Color(0xFFE9E2C2)
        val foamC = Color(0xFFF7F5EF)
        drawPath(
            contents,
            Brush.radialGradient(
                listOf(lerp(liquid, foamC, fo), lerp(Color(0xFFD9D2B0), Color(0xFFE6E2D8), fo)),
                Offset(scx - sr * 0.3f, scy - sr * 0.3f * sq),
                sr * 1.3f,
            ),
        )
        clipPath(contents) {
            // spiral streaks turned by the swirl
            val swirl = p[F_SWIRL]
            val peak = p[F_PEAK].toFloat()
            streaks.reset()
            for (k in 0 until 7) {
                val a0 = k / 7.0 * PI * 2 + swirl * 0.7
                for (i in 0..18) {
                    val rr = 0.12 + 0.86 * i / 18
                    val a = a0 + 1.8 * (1 - exp(-swirl / 5)) * (1 - rr) + rr * 1.2
                    val x = scx + (cos(a) * rr * sr).toFloat()
                    val y = scy + (sin(a) * rr * sr * sq).toFloat()
                    if (i == 0) streaks.moveTo(x, y) else streaks.lineTo(x, y)
                }
            }
            val streakA = (0.10f + 0.25f * peak) * fo
            if (streakA > 0.004f) {
                drawPath(streaks, Color(0xFFB9B1A0), alpha = streakA, style = Stroke(width = 5f * u))
                drawPath(streaks, Color.White, alpha = streakA * 1.6f, style = Stroke(width = 2f * u))
            }
            // a window's reflection on the whites
            drawOval(Color.White, Offset(scx - sr * 0.62f, scy - sr * 0.66f * sq), Size(sr * 0.32f, sr * 0.22f), alpha = 0.22f * (1 - fo * 0.6f))
            // the pulled peak
            val lp = p[F_LPEAK].toFloat()
            if (lp > 0.01f) {
                val px = cx + p[F_LX].toFloat() * r
                val py = cy + p[F_LY].toFloat() * r * sq
                drawCircle(Brush.radialGradient(listOf(Color(0x40000000), Color(0x00000000)), Offset(px + 10 * u, py + 12 * u), 30 * u), 30 * u, Offset(px + 10 * u, py + 12 * u), alpha = lp)
                drawCircle(Brush.radialGradient(listOf(Color.White, Color(0x00FFFFFF)), Offset(px - 4 * u, py - 6 * u), 26 * u), 26 * u, Offset(px - 4 * u, py - 6 * u), alpha = lp)
            }
        }
        // the front wall's glass and the rim
        drawOval(Color(0x2E6B8585), Offset(cx - r, cy - r * sq), Size(r * 2, r * 2 * sq), style = Stroke(width = 10f * u))
        drawArc(Color(0xE6FAFFFF), 160f, 140f, false, Offset(cx - r, cy - r * sq), Size(r * 2, r * 2 * sq), style = Stroke(width = 4f * u))
        drawOval(Color(0x806B7A7A), Offset(cx - r, cy - r * sq), Size(r * 2, r * 2 * sq), style = Stroke(width = max(1f, 1.2f * u)))
    }
}

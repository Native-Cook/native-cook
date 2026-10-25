package com.ngocdevv.nativecook.blocks.gestures.potterywheelshape

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import kotlin.math.PI
import kotlin.math.max

/*
 Below Android 13 there is no RuntimeShader: the same studio drawn with gradients and paths in dp.
 The clay is its silhouette (the profile at both sides, the foot's front arc), lit by a horizontal
 gradient, with throwing rings that climb as the wheel turns; the open top is an ellipse with a
 lit lip. No slip texture on the head, no softbox reflection.
 */
internal class PotteryFallback {
    private val clay = Path()
    private val wall = Path()
    private val rings = Path()

    fun DrawScope.draw(g: PotteryGeo, p: DoubleArray, spin: Double, wob: Double, pads: DoubleArray) {
        val u = g.unit.toFloat()
        val w = g.w.toFloat()
        val h = g.h.toFloat()
        val cx = g.cx.toFloat()
        val by = g.baseY.toFloat()
        val sinE = PotterySinE.toFloat()
        val cosE = PotteryCosE.toFloat()

        // wall, floor, spotlight
        drawRect(Brush.verticalGradient(0f to Color(0xFF231B16), 0.75f to Color(0xFF16110F), startY = 0f, endY = h))
        val horizon = by - 150 * u
        drawRect(
            Brush.verticalGradient(0f to Color(0xFF1E1814), 1f to Color(0xFF12100C), startY = horizon, endY = h),
            Offset(0f, horizon),
            Size(w, h - horizon),
        )
        val shelfY = by - 262 * u
        drawRect(Color(0xD9332318), Offset(0f, shelfY - 6), Size(w, 16f))
        drawOval(
            Brush.radialGradient(listOf(Color(0x66633F22), Color(0x00633F22)), Offset(cx, by - 170 * u), 300 * u),
            Offset(cx - 300 * u, by - 470 * u),
            Size(600 * u, 600 * u),
        )

        // splash pan inside, wheel head side and top
        val rp = 172 * u
        val rimY = by - 8 * u * cosE
        drawOval(Color(0xFF1C1714), Offset(cx - rp, rimY - rp * sinE), Size(rp * 2, rp * sinE * 2))
        val rw = 116 * u
        val tw = 14 * u * cosE
        drawOval(Color(0xFF55575A), Offset(cx - rw, by + tw - rw * sinE), Size(rw * 2, rw * sinE * 2))
        drawRect(
            Brush.horizontalGradient(0f to Color(0xFF9E9E9C), 1f to Color(0xFF3D3E40), startX = cx - rw, endX = cx + rw),
            Offset(cx - rw, by),
            Size(rw * 2, tw),
        )
        drawOval(
            Brush.linearGradient(listOf(Color(0xFFB3B3B0), Color(0xFF6B6D70)), Offset(cx - rw, by - rw * sinE), Offset(cx + rw, by + rw * sinE)),
            Offset(cx - rw, by - rw * sinE),
            Size(rw * 2, rw * sinE * 2),
        )
        val r0 = (potteryRadiusAt(p, 0.0) * u).toFloat()
        val slip = r0 + 22 * u
        drawOval(Color(0xCC66402E), Offset(cx + wob.toFloat() - slip, by - slip * sinE), Size(slip * 2, slip * sinE * 2))

        // the clay's silhouette
        val hh = p[P_H]
        val n = 24
        val xw = cx + wob.toFloat()
        clay.reset()
        for (i in 0..n) {
            val y = hh * (n - i) / n
            val r = (potteryRadiusAt(p, y) * u).toFloat()
            val sy = by - (y * u).toFloat() * cosE
            if (i == 0) clay.moveTo(xw - r, sy) else clay.lineTo(xw - r, sy)
        }
        clay.arcTo(Rect(xw - r0, by - r0 * sinE, xw + r0, by + r0 * sinE), 180f, -180f, false)
        for (i in 0..n) {
            val y = hh * i / n
            val r = (potteryRadiusAt(p, y) * u).toFloat()
            clay.lineTo(xw + r, by - (y * u).toFloat() * cosE)
        }
        val rt = (potteryRadiusAt(p, hh) * u).toFloat()
        val topY = by - (hh * u).toFloat() * cosE
        if (rt > 1f) clay.arcTo(Rect(xw - rt, topY - rt * sinE, xw + rt, topY + rt * sinE), 0f, -180f, false)
        clay.close()
        val half = (potteryWidest(p) * u / 2).toFloat()
        drawPath(
            clay,
            Brush.horizontalGradient(
                0f to Color(0xFF3A2218),
                0.3f to Color(0xFF8C5E46),
                0.55f to Color(0xFF6E4532),
                0.88f to Color(0xFF452A1E),
                1f to Color(0xFF9A6342),
                startX = xw - half,
                endX = xw + half,
            ),
        )
        // throwing rings: front arcs every 9 dp, climbing one pitch per turn
        val pitch = 9 * u
        val climb = (((spin / (2 * PI)) % 1.0 + 1.0) % 1.0).toFloat()
        rings.reset()
        var y = pitch * climb + 10 * u
        while (y < hh * u - 6 * u) {
            val r = (potteryRadiusAt(p, (y / u).toDouble()) * u).toFloat()
            val sy = by - y * cosE
            rings.addArc(Rect(xw - r, sy - r * sinE, xw + r, sy + r * sinE), 15f, 150f)
            y += pitch
        }
        clipPath(clay) {
            drawPath(rings, Color(0x262A160E), style = Stroke(width = 1.4f * u))
            drawRect(
                Brush.verticalGradient(0f to Color(0x00000000), 1f to Color(0x73000000), startY = by - 16 * u, endY = by + r0 * sinE),
                Offset(xw - half - 8, by - 16 * u),
                Size(half * 2 + 16, 16 * u + r0 * sinE),
            )
        }
        val open = p[P_OPEN].toFloat()
        if (open > 0.01f && rt > 1f) {
            val wallT = 7 * u
            drawOval(Color(0xFFA27058).copy(alpha = open), Offset(xw - rt, topY - rt * sinE), Size(rt * 2, rt * sinE * 2))
            val ri = max(1f, rt - wallT)
            drawOval(
                Brush.verticalGradient(listOf(Color(0xFF1A0F0B), Color(0xFF55331F)), startY = topY - ri * sinE, endY = topY + ri * sinE),
                Offset(xw - ri, topY - ri * sinE),
                Size(ri * 2, ri * sinE * 2),
                alpha = open,
            )
        }

        // splash pan front wall and lip
        val dd = 46 * u * cosE
        wall.reset()
        val rim = Rect(cx - rp, rimY - rp * sinE, cx + rp, rimY + rp * sinE)
        wall.arcTo(rim, 0f, 180f, true)
        wall.lineTo(cx - rp, rimY + dd)
        wall.arcTo(rim.translate(0f, dd), 180f, -180f, false)
        wall.close()
        drawPath(
            wall,
            Brush.horizontalGradient(0f to Color(0xFF433A34), 1f to Color(0xFF221D1A), startX = cx - rp, endX = cx + rp),
        )
        drawArc(Color(0xFF7A6E64), 0f, 180f, false, rim.topLeft, rim.size, style = Stroke(width = 3f * u))

        // fingertip pads
        for (k in 0..1) {
            val o = k * 4
            val a = pads[o + 2].toFloat()
            if (a <= 0.001f) continue
            val c = Offset(pads[o].toFloat(), pads[o + 1].toFloat())
            val r = pads[o + 3].toFloat()
            drawCircle(Brush.radialGradient(listOf(Color(0x404D3D2E), Color(0x004D3D2E)), c, r * 1.8f), r * 1.8f, c, alpha = a)
            drawCircle(Color(0xFFFFF0E0), r * 0.93f, c, alpha = 0.16f * a)
            drawCircle(Color(0xFFFFF0E0), r * 0.8f, c, alpha = 0.38f * a, style = Stroke(width = r * 0.22f))
        }

        // vignette
        drawRect(
            Brush.radialGradient(
                0.4f to Color(0x00000000),
                1f to Color(0x8C000000),
                center = Offset(w / 2, h / 2),
                radius = max(w, h) * 0.62f,
            ),
        )
    }
}

package com.ngocdevv.nativecook.blocks.pickers.sundialtimepicker

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import kotlin.math.PI
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/*
 Below Android 13 there is no RuntimeShader: the same picker drawn with gradients and paths in dp.
 It keeps what the shader shows moving — the warming light and backdrop, the sun handle on its
 dashed arc, the dial's drop shadow cast away from the sun, and the gnomon's shadow triangle
 (O, B, T) — with the hour, half and quarter lines and rings as plain strokes. No stone texture,
 no lit grooves.
 */
internal class SundialFallback {
    private val shadow = Path()
    private val dash = PathEffect.dashPathEffect(floatArrayOf(4f, 4f))

    fun DrawScope.draw(g: DialGeo, sun: DoubleArray, sx: Float, sy: Float, finger: Float) {
        val u = g.unit.toFloat()
        val w = g.w.toFloat()
        val h = g.h.toFloat()
        val c = Offset(g.cx.toFloat(), g.cy.toFloat())
        val r = g.r.toFloat()
        // S = normalize(east, −north, max(up, 0.06)) in screen terms (x right, y down)
        var lx = sun[0].toFloat()
        var ly = (-sun[1]).toFloat()
        var lz = max(sun[2].toFloat(), 0.06f)
        val ln = sqrt(lx * lx + ly * ly + lz * lz)
        lx /= ln; ly /= ln; lz /= ln
        val warm = 1f - smooth(0.22f, 0.72f, lz)
        val light = lerp(Color(0xFFFFFAF2), Color(0xFFFFD49E), warm)

        val top = lerp(Color(0xFFCCDEF0), Color(0xFFFADBBD), warm)
        drawRect(Brush.verticalGradient(0.05f to top, 0.75f to Color(0xFFF2EDE3), startY = 0f, endY = h))
        val glowR = 160 * u
        drawCircle(Brush.radialGradient(listOf(Color(0xFFFFDB99).copy(alpha = 0.22f + 0.25f * warm), Color(0x00FFDB99)), Offset(sx, sy), glowR), glowR, Offset(sx, sy))

        // the dashed sky arc between 7 and 17 h
        val ax = min(w * 0.5f - 22f, r + 30 * u)
        val ay = r + 50 * u
        drawArc(
            Color(0x73736660),
            -165f,
            150f,
            false,
            Offset(c.x - ax, c.y - ay),
            Size(ax * 2, ay * 2),
            style = Stroke(width = 1.4f, pathEffect = dash),
        )

        // the dial's drop shadow, cast away from the sun
        val k = 10 * u / max(lz, 0.25f)
        val so = Offset(c.x - lx * k, c.y - ly * k)
        drawCircle(Brush.radialGradient(0.85f to Color(0x52000000), 1f to Color(0x00000000), center = so, radius = r + 18 * u), r + 18 * u, so)

        // the stone, lit and warmed by the sun
        drawCircle(
            Brush.radialGradient(
                listOf(lerp(Color(0xFFE4DDD1), light, 0.25f), Color(0xFFC9C0B1)),
                Offset(c.x + lx * r * 0.4f, c.y + ly * r * 0.4f),
                r * 1.4f,
            ),
            r,
            c,
        )
        drawCircle(Color(0x33000000), r - 4.5f * u, c, style = Stroke(width = 9 * u * 0.5f))
        val ring = Color(0x52645646)
        drawCircle(ring, 0.80f * r, c, style = Stroke(width = 1.4f * u))
        drawCircle(ring, 0.94f * r, c, style = Stroke(width = 1.0f * u))
        drawCircle(ring, 0.28f * r, c, style = Stroke(width = 1.4f * u))
        // hour, half and quarter lines
        for (i in 0..40) {
            val tq = 7.0 + i * 0.25
            val hq = (tq - 12) * PI / 12
            val th = atan(SundialSinLat * tan(hq))
            val dx = sin(th).toFloat()
            val dy = (-cos(th)).toFloat()
            val fr = i % 4
            val r0 = when (fr) {
                0 -> 0.30f
                2 -> 0.71f
                else -> 0.755f
            } * r
            val sw = when (fr) {
                0 -> 2.1f
                2 -> 1.2f
                else -> 0.9f
            } * u
            drawLine(Color(0x73504232), Offset(c.x + dx * r0, c.y + dy * r0), Offset(c.x + dx * 0.8f * r, c.y + dy * 0.8f * r), sw)
        }
        // the gnomon's shadow: O, B, T = B shifted away from the sun
        val gn = g.gnomon.toFloat()
        val b = Offset(c.x, c.y - gn)
        val hg = gn * 0.8391f
        val alt = lz
        var shx = -lx / max(alt, 0.08f) * hg
        var shy = -ly / max(alt, 0.08f) * hg
        val shl = sqrt(shx * shx + shy * shy)
        val cap = min(1f, 2.2f * r / max(shl, 0.001f))
        shx *= cap; shy *= cap
        shadow.reset()
        shadow.moveTo(c.x, c.y)
        shadow.lineTo(b.x, b.y)
        shadow.lineTo(b.x + shx, b.y + shy)
        shadow.close()
        drawPath(shadow, Color(0x6B2E3346))
        // the bronze gnomon and its boss
        val sideLit = (0.5f + 0.5f * lx * 1.6f).coerceIn(0f, 1f)
        drawLine(lerp(Color(0xFF4D3521), Color(0xFFB38A5C), sideLit), c, b, 6.2f * u, StrokeCap.Round)
        drawLine(Color(0x664D3D29), c, b, 1.2f * u)
        drawCircle(Brush.radialGradient(listOf(Color(0xFFD9A86B), Color(0xFF6B4C2B)), Offset(c.x + lx * 2 * u, c.y + ly * 2 * u), 6 * u), 5.5f * u, c)

        // the sun handle, its glow and the finger's ring
        val core = lerp(Color(0xFFFFEBB3), Color(0xFFFFB866), warm)
        drawCircle(Brush.radialGradient(listOf(core.copy(alpha = 0.35f), core.copy(alpha = 0f)), Offset(sx, sy), 40 * u), 40 * u, Offset(sx, sy))
        drawCircle(core, 14 * u, Offset(sx, sy))
        drawCircle(Color(0xCCFFFCF0), 8 * u, Offset(sx, sy))
        if (finger > 0.001f) drawCircle(Color(0xFF4D4238), 24 * u, Offset(sx, sy), alpha = 0.5f * finger, style = Stroke(width = 1.6f))
        drawRect(Brush.radialGradient(0.5f to Color(0x00000000), 1f to Color(0x24000000), center = Offset(w / 2, h / 2), radius = max(w, h) * 0.7f))
    }

    private fun smooth(a: Float, b: Float, x: Float): Float {
        val t = ((x - a) / (b - a)).coerceIn(0f, 1f)
        return t * t * (3 - 2 * t)
    }
}

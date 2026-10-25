package com.ngocdevv.nativecook.blocks.pickers.thermostatfrostdial

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

private val Neutral = Color(0.235f, 0.215f, 0.196f)
private val ColdWall = Color(0.105f, 0.155f, 0.225f)
private val WarmWall = Color(0.30f, 0.18f, 0.10f)

/**
 * Below Android 13 (no RuntimeShader): the wall, the ring, the face and its ticks drawn with
 * gradients and strokes in dp units, from the same uniforms as the shader. Frost is a milky band
 * that grows from the rim inward with the cold, the beads a few bright dots, the warmth an amber
 * glow from below; there is no crystal texture and no heat shimmer.
 */
internal fun DrawScope.drawDialFallback(g: DialGeo, u: DialUniforms) {
    val cold = u.env[0]
    val warm = u.env[1]
    val base = lerp(lerp(Neutral, ColdWall, cold), WarmWall, warm)
    drawRect(base, size = Size(g.w, g.h))
    // Slat grooves every 26 dp.
    var x = 0f
    while (x < g.w) {
        drawRect(Color.Black.copy(alpha = 0.22f), topLeft = Offset(x, 0f), size = Size(1.6f, g.h))
        x += 26f
    }
    if (warm > 0f) {
        drawRect(
            Brush.verticalGradient(0f to Color.Transparent, 1f to Color(0.42f, 0.18f, 0.04f).copy(alpha = 0.5f * warm), startY = g.h * 0.45f, endY = g.h),
            size = Size(g.w, g.h),
        )
    }
    val c = Offset(g.cx, g.cy)
    // Shadow on the wall.
    drawCircle(
        Brush.radialGradient(
            0f to Color.Black.copy(alpha = 0.45f),
            1f to Color.Transparent,
            center = c + Offset(0f, 10f),
            radius = g.ro + 34f,
        ),
        radius = g.ro + 34f,
        center = c + Offset(0f, 10f),
    )
    // The ring: matte ceramic, lit from the upper left.
    drawCircle(
        Brush.linearGradient(
            0f to Color(0.93f, 0.92f, 0.89f),
            1f to Color(0.62f, 0.60f, 0.57f),
            start = c + Offset(-g.ro, -g.ro),
            end = c + Offset(g.ro, g.ro),
        ),
        radius = g.ro,
        center = c,
    )
    // Frost: a milky band from the rim inward, as deep as the shader's front.
    val frost = u.ring[2]
    if (frost > 0.001f) {
        val band = min(g.ro - g.ri, frost * (g.ro - g.ri + 26f) * 0.8f)
        drawCircle(
            Color(0.90f, 0.95f, 1f).copy(alpha = 0.85f * min(1f, frost * 1.6f)),
            radius = g.ro - band / 2f,
            center = c,
            style = Stroke(band),
        )
    }
    // Beads of melt water.
    val wet = u.ring[3]
    if (wet > 0.01f) {
        for (i in 0 until 28) {
            val a = i * 0.7389f * 6.2831853f + u.ring[0]
            val rr = g.ri + (g.ro - g.ri) * (0.2f + 0.6f * ((i * 0.618f) % 1f))
            val p = c + Offset(cos(a) * rr, sin(a) * rr)
            drawCircle(Color.White.copy(alpha = 0.55f * wet), radius = 1.6f + 1.4f * ((i * 0.37f) % 1f), center = p)
        }
    }
    // The set-point notch.
    val np = c + Offset(cos(u.ring[1]), sin(u.ring[1])) * (g.ri + (g.ro - g.ri) * 0.5f)
    drawCircle(Color(0.30f, 0.29f, 0.28f), radius = 4f, center = np)
    // The face.
    drawCircle(
        Brush.radialGradient(
            0f to Color(0.24f, 0.235f, 0.225f),
            0.82f to Color(0.20f, 0.195f, 0.19f),
            1f to Color(0.13f, 0.125f, 0.12f),
            center = c,
            radius = g.ri,
        ),
        radius = g.ri,
        center = c,
    )
    drawCircle(Color.Black.copy(alpha = 0.5f), radius = g.ri, center = c, style = Stroke(1.6f))
    // Ticks: 41 over 270°, those between the room and the set point lit.
    val lo = min(u.lit[0], u.lit[1])
    val hi = max(u.lit[0], u.lit[1])
    val hot = if (u.lit[2] > 0.5f) Color(1f, 0.62f, 0.25f) else Color(0.55f, 0.80f, 1f)
    val outer = g.ri - 10f
    for (i in 0..40) {
        val ta = dialAngleOf(10f + i * 0.5f)
        val major = i % 10 == 0
        val len = if (major) 16f else 9f
        val on = ta >= lo - 0.01f && ta <= hi + 0.01f
        val col = if (on) hot else if (major) Color(0.78f, 0.76f, 0.72f) else Color(0.52f, 0.50f, 0.47f)
        val d = Offset(cos(ta), sin(ta))
        drawLine(col, c + d * (outer - len), c + d * outer, strokeWidth = if (major) 2.6f else 1.6f)
    }
}

package com.ngocdevv.nativecook.blocks.animations.soapbubblepop

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin
import kotlin.math.sqrt

private val Iridescent = listOf(
    Color(0xFFFF7BD0), Color(0xFFFFD36E), Color(0xFF7EF0C8), Color(0xFF7AC8FF), Color(0xFFC59BFF), Color(0xFFFF7BD0),
)

/**
 * Below Android 13 (no RuntimeShader): the wall, the wand and the bubbles drawn with gradients in
 * dp units. A bubble is a clear disc with an iridescent sweep rim, the window highlight and a small
 * second one; a pop fades it out over the shader's 0.2 s while the same 22 droplets per bubble fly
 * off its outline and fall. No thin-film swirl or lensing of the wall.
 */
internal fun DrawScope.drawBubbleFallback(g: BubbleGeo, f: BubbleFrame) {
    drawRect(
        Brush.verticalGradient(
            0f to Color(0.47f, 0.76f, 0.85f),
            0.5f to Color(0.55f, 0.67f, 0.91f),
            1f to Color(0.73f, 0.62f, 0.89f),
            startY = 0f,
            endY = g.h,
        ),
        size = Size(g.w, g.h),
    )
    // Tile grout every 46 dp, as in the shader.
    val grout = Color.Black.copy(alpha = 0.035f)
    var x = 46f - 9f
    while (x < g.w) {
        drawLine(grout, Offset(x, 0f), Offset(x, g.h), 1.4f)
        x += 46f
    }
    var y = 46f - 14f
    while (y < g.h) {
        drawLine(grout, Offset(0f, y), Offset(g.w, y), 1.4f)
        y += 46f
    }
    // The wand: handle, film, ring.
    val hx = 0.42f / sqrt(0.42f * 0.42f + 1f)
    val hy = 1f / sqrt(0.42f * 0.42f + 1f)
    val hs = Offset(g.wx + hx * (g.wr + 2f), g.wy + hy * (g.wr + 2f))
    drawLine(Color(0xFFE5634F), hs, hs + Offset(hx * 160f, hy * 160f), 8.4f, cap = StrokeCap.Round)
    if (f.wandFilm > 0f) {
        drawCircle(
            Brush.sweepGradient(Iridescent, Offset(g.wx, g.wy)),
            radius = g.wr - 2.3f,
            center = Offset(g.wx, g.wy),
            alpha = 0.35f * f.wandFilm,
        )
    }
    drawCircle(Color(0xFFF2735E), radius = g.wr, center = Offset(g.wx, g.wy), style = Stroke(9.2f))
    drawCircle(Color.White.copy(alpha = 0.3f), radius = g.wr + 1.4f, center = Offset(g.wx - 1f, g.wy - 1.5f), style = Stroke(1.6f))

    val seeds = floatArrayOf(0.4f, 1.3f, 2.7f)
    for (i in 2 downTo 0) bubble(f.poses[i], f.film, seeds[i])
    for (i in 0 until 3) droplets(f.poses[i], seeds[i])
    // The finger's mark.
    val ta = f.tap[2]
    if (ta >= 0f && ta < 0.45f) {
        val u = 1f - ta / 0.45f
        val r = 12f + 26f * (1f - u * u * u)
        drawCircle(Color.White.copy(alpha = 0.55f * u), radius = r, center = Offset(f.tap[0], f.tap[1]), style = Stroke(2.2f))
    }
}

private fun DrawScope.bubble(p: FloatArray, film: Float, seed: Float) {
    if (p[3] <= 0.001f || p[2] <= 1f) return
    var a = p[3]
    if (p[4] >= 0f) {
        val u = (p[4] / 0.2f).coerceIn(0f, 1f)
        a *= 1f - (1f - (1f - u) * (1f - u))
        if (a <= 0.01f) return
    }
    val c = Offset(p[0], p[1])
    val r = p[2]
    drawCircle(
        Brush.radialGradient(
            0f to Color.White.copy(alpha = 0.02f),
            0.7f to Color.White.copy(alpha = 0.06f),
            1f to Color.White.copy(alpha = 0.32f),
            center = c,
            radius = r,
        ),
        radius = r,
        center = c,
        alpha = a,
    )
    rotate((film * 360f + seed * 57f), pivot = c) {
        drawCircle(Brush.sweepGradient(Iridescent, c), radius = r - 2f, center = c, style = Stroke(4f), alpha = 0.55f * a)
    }
    drawRoundRect(
        Color.White.copy(alpha = 0.8f * a),
        topLeft = Offset(c.x - 0.42f * r - 0.17f * r, c.y - 0.46f * r - 0.12f * r),
        size = Size(0.34f * r, 0.24f * r),
        cornerRadius = CornerRadius(0.08f * r),
    )
    drawOval(
        Color.White.copy(alpha = 0.3f * a),
        topLeft = Offset(c.x + 0.5f * r - 0.1f * r, c.y + 0.56f * r - 0.06f * r),
        size = Size(0.2f * r, 0.12f * r),
    )
}

private fun hsh1(x: Float): Float {
    val v = sin(x * 91.345f) * 47453.5453f
    return v - floor(v)
}

/** The droplets a popped bubble sheds, at the shader's positions. */
private fun DrawScope.droplets(p: FloatArray, seed: Float) {
    val age = p[4]
    if (age < 0f || age > 1.25f) return
    val tapAng = atan2(p[6], p[5])
    for (i in 0 until 22) {
        val fi = i.toFloat()
        val h1 = hsh1(fi + seed * 17f)
        val h2 = hsh1(fi * 1.7f + seed * 5f + 3f)
        val ang = (fi + h1 * 0.8f) * (2f * PI.toFloat()) / 22f
        val away = abs(atan2(sin(ang - tapAng), cos(ang - tapAng))) / 3.14159f
        val t = age - (0.02f + 0.1f * away)
        if (t < 0f || t > 0.95f) continue
        val speed = 70f + 110f * h2
        val dx = cos(ang)
        val dy = sin(ang)
        val px = p[0] + dx * p[2] * 0.98f + dx * speed * t
        val py = p[1] + dy * p[2] * 0.98f + dy * speed * t + 560f * t * t * 0.5f - 30f * t
        val r = 1.8f + 2.2f * h1
        val life = 1f - ((t - 0.55f) / 0.4f).coerceIn(0f, 1f)
        drawCircle(Color(0.6f, 0.7f, 0.8f).copy(alpha = 0.95f * life), radius = r, center = Offset(px, py))
        drawCircle(Color.White.copy(alpha = 0.9f * life), radius = r * 0.35f, center = Offset(px - r * 0.35f, py - r * 0.35f))
    }
}

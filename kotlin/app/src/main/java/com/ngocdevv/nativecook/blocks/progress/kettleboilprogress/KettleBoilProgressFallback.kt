package com.ngocdevv.nativecook.blocks.progress.kettleboilprogress

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import kotlin.math.PI
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

// Below Android 13 there is no AGSL: the kitchen is drawn from gradients and paths with the
// shader's geometry (the same body profile, base, lid, handle and spout). The water is a tinted
// fill whose surface waves with the boil; the rising streams are the shader's own bubbles (same
// columns, counts and phases) drawn as rings; the LED ring is an arc that fills with the
// temperature; steam is a column of soft discs rising from the spout's tip.

private const val BODY_H = 236f
private const val TAU = (PI * 2).toFloat()
private fun fract(x: Float) = x - floor(x)
private fun hash(x: Float, y: Float) = fract(sin(x * 127.1f + y * 311.7f) * 43758.5453f)

private fun bodyHalf(y: Float, k: Float): Float {
    val h = BODY_H * k
    val s = min(1f, max(0f, -y / h))
    var hw = (112f - 26f * s.pow(1.4f)) * k
    val rb = 30f * k
    val db = -y
    if (db < rb) hw = hw - rb + sqrt(max(0f, rb * rb - (rb - db) * (rb - db)))
    val rt = 22f * k
    val dt = h + y
    if (dt < rt) hw = hw - rt + sqrt(max(0f, rt * rt - (rt - dt) * (rt - dt)))
    return hw
}

internal fun DrawScope.kettleFallback(width: Float, height: Float, l: KettleLayout, s: KettleState, phase: Float) {
    val k = l.k
    val o = Offset(l.ox, l.oy)
    val h = BODY_H * k
    val glowOn = s.ringOn
    // The wall and the counter.
    drawRect(Brush.verticalGradient(listOf(Color(0.085f, 0.12f, 0.125f), Color(0.15f, 0.19f, 0.185f)), 0f, height), Offset.Zero, Size(width, height))
    val counter = l.oy + 52f * k
    drawRect(Brush.verticalGradient(listOf(Color(0.46f, 0.32f, 0.21f), Color(0.3f, 0.21f, 0.14f)), counter, height), Offset(0f, counter), Size(width, height - counter))
    drawRect(Color(0.6f, 0.5f, 0.4f, 0.25f), Offset(0f, counter), Size(width, 1.5f * k))
    drawOval(
        Brush.radialGradient(listOf(Color(0f, 0f, 0f, 0.45f), Color(0f, 0f, 0f, 0f)), Offset(l.ox, counter), 150f * k),
        Offset(l.ox - 150f * k, counter - 40f * k),
        Size(300f * k, 80f * k),
    )
    if (glowOn > 0f) {
        drawOval(
            Brush.radialGradient(listOf(Color(0.2f, 0.5f, 1f, min(1f, 0.3f * glowOn)), Color(0.2f, 0.5f, 1f, 0f)), Offset(l.ox, counter), 160f * k),
            Offset(l.ox - 160f * k, counter - 40f * k),
            Size(320f * k, 80f * k),
        )
    }

    // The handle and its switch.
    val hx = l.ox + bodyHalf(-h * 0.55f, k) + 14f * k
    drawRoundRect(Color(0.09f, 0.095f, 0.1f), Offset(hx - 30f * k, l.oy - h * 0.88f), Size(60f * k, h * 0.66f), CornerRadius(26f * k), style = Stroke(17f * k))
    val lvX = l.ox + bodyHalf(-h * 0.2f, k) + 30f * k
    val lvY = l.oy - h * 0.2f - 6f * k + s.lever * 7f * k
    drawRoundRect(Color(0.12f + 0.25f * glowOn * s.lever * 0.8f, 0.13f + 0.62f * glowOn * s.lever * 0.8f, min(1f, 0.14f + glowOn * s.lever * 0.8f)), Offset(lvX - 9f * k, lvY - 4f * k), Size(18f * k, 8f * k), CornerRadius(3f * k))

    // The base and its LED ring.
    drawRoundRect(
        Brush.verticalGradient(listOf(Color(0.2f, 0.21f, 0.22f), Color(0.06f, 0.065f, 0.07f)), l.oy, l.oy + 52f * k),
        Offset(l.ox - 126f * k, l.oy),
        Size(252f * k, 52f * k),
        CornerRadius(18f * k),
    )
    val ringRect = Size(232f * k, 20f * k)
    val ringTop = Offset(l.ox - 116f * k, l.oy + 6f * k - 10f * k)
    drawArc(Color(0.1f, 0.13f, 0.17f), 0f, 180f, false, ringTop, ringRect, style = Stroke(2.4f * k))
    if (s.ringFill > 0.001f) {
        val sweep = min(180f, s.ringFill * 1.66f * 180f / PI.toFloat() * 2f)
        val led = Color(0.25f, 0.62f, 1f, min(1f, 0.45f + 1.1f * glowOn))
        drawArc(led, 90f - sweep / 2f, sweep, false, ringTop, ringRect, style = Stroke(2.4f * k, cap = StrokeCap.Round))
        drawArc(led.copy(alpha = 0.2f * min(1f, glowOn)), 90f - sweep / 2f, sweep, false, ringTop, ringRect, style = Stroke(8f * k, cap = StrokeCap.Round))
    }

    // The spout: a thick glass tube.
    val sa = Offset(l.ox - bodyHalf(-h * 0.72f, k) + 12f * k, l.oy - h * 0.72f)
    val sb = Offset(l.ox - bodyHalf(-h * 0.72f, k) - 50f * k, l.oy - h - 4f * k)
    drawLine(Color(0.55f, 0.66f, 0.68f, 0.55f), sa, sb, 26f * k, cap = StrokeCap.Round)
    drawLine(Color(0.85f, 0.92f, 0.95f, 0.35f), sa + Offset(0f, -8f * k), sb + Offset(0f, -4f * k), 2f * k, cap = StrokeCap.Round)

    // The body: glass, the water, its surface and the bubbles.
    val body = Path()
    val steps = 32
    for (i in 0..steps) {
        val y = -h * i / steps
        val p = Offset(l.ox + bodyHalf(y, k), l.oy + y)
        if (i == 0) body.moveTo(p.x, p.y) else body.lineTo(p.x, p.y)
    }
    for (i in steps downTo 0) {
        val y = -h * i / steps
        body.lineTo(l.ox - bodyHalf(y, k), l.oy + y)
    }
    body.close()
    drawPath(body, Color(0.6f, 0.75f, 0.78f, 0.12f))
    clipPath(body) {
        val level = -h * 0.68f
        val boil = max(0f, min(1f, (s.heat - 0.65f) / 0.35f))
        val water = Path()
        val left = l.ox - 120f * k
        water.moveTo(left, l.oy)
        var x = left
        while (x <= l.ox + 120f * k) {
            val qx = x - l.ox
            val wave = (1.2f * k * s.heat + 5f * k * boil) * (sin(qx * 0.06f / k + phase * TAU * 5f) + 0.6f * sin(qx * 0.13f / k - phase * TAU * 7f + 1f))
            water.lineTo(x, l.oy + level + wave)
            x += 4f
        }
        water.lineTo(l.ox + 120f * k, l.oy)
        water.close()
        drawPath(
            water,
            Brush.verticalGradient(
                listOf(Color(0.2f, 0.32f, 0.33f, 0.75f), Color(0.15f + 0.1f * glowOn, 0.3f + 0.3f * glowOn, 0.34f + 0.6f * glowOn, 0.85f)),
                l.oy + level,
                l.oy,
            ),
        )
        drawPath(water, Color(0.8f, 0.92f, 0.95f, 0.35f + 0.4f * boil), style = Stroke(1.4f * k))
        // Streams of rising bubbles, from the shader's columns.
        val streams = max(0f, min(1f, (s.heat - 0.3f) / 0.45f))
        if (streams > 0f) {
            val floorY = -8f * k
            for (c in 0 until 7) {
                val fc = c.toFloat()
                val hc0 = hash(fc, 4f)
                if (hc0 > streams * 1.05f) continue
                val xc = (hash(fc, 9f) * 1.5f - 0.75f) * 92f * k
                val kc = 5f + floor(hash(fc, 2f) * 4f)
                for (j in 0 until 6) {
                    val v = fract(phase * kc + j / 6f + hc0)
                    val by = floorY + (level + 4f * k - floorY) * v * v * (0.6f + 0.4f * v)
                    val bx = xc + sin(v * 11f + fc * 2f) * 3f * k * v
                    val br = (1.2f + 3.2f * v) * k * (0.7f + 0.3f * streams)
                    drawCircle(Color(0.85f, 0.95f, 1f, 0.6f * streams), br, Offset(l.ox + bx, l.oy + by), style = Stroke(max(0.6f, br * 0.35f)))
                }
            }
        }
        // Glass: two streaks of the softboxes.
        drawRect(
            Brush.horizontalGradient(
                0f to Color(0.85f, 0.92f, 0.95f, 0.35f),
                0.1f to Color(1f, 1f, 1f, 0f),
                0.15f to Color(1f, 0.98f, 0.95f, 0.45f),
                0.2f to Color(1f, 1f, 1f, 0f),
                0.76f to Color(1f, 1f, 1f, 0f),
                0.8f to Color(0.95f, 0.97f, 1f, 0.12f),
                0.86f to Color(1f, 1f, 1f, 0f),
                1f to Color(0.85f, 0.92f, 0.95f, 0.35f),
                startX = l.ox - 112f * k,
                endX = l.ox + 112f * k,
            ),
            Offset(l.ox - 112f * k, l.oy - h),
            Size(224f * k, h),
        )
    }
    drawPath(body, Color(0.75f, 0.82f, 0.85f, 0.4f), style = Stroke(1.2f))

    // The lid and its knob.
    val topHw = bodyHalf(-h + 0.5f, k)
    drawRoundRect(
        Brush.verticalGradient(listOf(Color(0.22f, 0.23f, 0.25f), Color(0.06f, 0.065f, 0.07f)), l.oy - h - 31f * k, l.oy - h + 3f * k),
        Offset(l.ox - topHw - 4f * k, l.oy - h - 15f * k),
        Size((topHw + 4f * k) * 2f, 18f * k),
        CornerRadius(8f * k),
    )
    drawRoundRect(Color(0.16f, 0.17f, 0.18f), Offset(l.ox - 20f * k, l.oy - h - 31f * k), Size(40f * k, 18f * k), CornerRadius(7f * k))

    // Steam from the spout's tip and from under the lid.
    if (s.steam > 0.001f) {
        val tip = sb + Offset(-2f * k, -6f * k)
        for (i in 0 until 9) {
            val v = fract(phase * 4f + i / 9f)
            val up = v * 170f * k
            val drift = up * 0.25f
            val r = 8f * k + up * 0.3f
            val a = 0.28f * s.steam * (1f - v) * min(1f, v * 6f)
            drawCircle(
                Brush.radialGradient(listOf(Color(0.86f, 0.9f, 0.92f, a), Color(0.86f, 0.9f, 0.92f, 0f)), Offset(tip.x - drift, tip.y - up), r),
                r,
                Offset(tip.x - drift, tip.y - up),
            )
        }
        val lid = Offset(l.ox + 10f * k, l.oy - h - 16f * k)
        for (i in 0 until 5) {
            val v = fract(phase * 3f + i / 5f)
            val up = v * 110f * k
            val r = 10f * k + up * 0.25f
            val a = 0.16f * s.steam * (1f - v) * min(1f, v * 6f)
            val c = Offset(lid.x + up * 0.08f, lid.y - up)
            drawCircle(Brush.radialGradient(listOf(Color(0.86f, 0.9f, 0.92f, a), Color(0.86f, 0.9f, 0.92f, 0f)), c, r), r, c)
        }
    }
}

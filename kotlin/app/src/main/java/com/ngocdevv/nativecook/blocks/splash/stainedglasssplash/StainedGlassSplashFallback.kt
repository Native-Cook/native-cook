package com.ngocdevv.nativecook.blocks.splash.stainedglasssplash

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

// Below Android 13 there is no AGSL: the window is drawn as arcs and paths with the shader's
// layout (the hub's gold rose, eight petals on cobalt, sixteen bays with round foils, thirty-two
// border panes, stone rings and lead), each pane lit by the same key, sweep and beam as the shader
// but in a flat colour (no mottle or bubbles). The wall and the floor are gradients, the floor's
// patch of coloured light a soft ellipse, and the flood a cream disc that grows from the window.

private val Ruby = Color(0.78f, 0.07f, 0.14f)
private val Cobalt = Color(0.07f, 0.2f, 0.72f)
private val Amber = Color(0.98f, 0.62f, 0.12f)
private val Emerald = Color(0.08f, 0.56f, 0.32f)
private val Violet = Color(0.42f, 0.14f, 0.62f)
private val Gold = Color(1f, 0.8f, 0.3f)
private const val TAU = (PI * 2).toFloat()

private fun fract(x: Float) = x - floor(x)
private fun hash(x: Float, y: Float) = fract(sin(x * 127.1f + y * 311.7f) * 43758.5453f)
private fun smooth(a: Float, b: Float, x: Float): Float {
    val t = min(1f, max(0f, (x - a) / (b - a)))
    return t * t * (3f - 2f * t)
}
private fun g2(x: Float) = exp(-x * x)

/** How a pane glows: its key against the sweep, and the beam near it (as `transmit` in the shader). */
private fun glowOf(s: StainedGlassScene, cang: Float, crad: Float, ring: Float, seed: Float): Float {
    val ang = (cang - 0.5f) * TAU
    val x = 0.5f + 0.5f * crad * cos(ang)
    val jitter = (hash(seed, 3.7f) - 0.5f) * 0.08f
    val k2 = x * 0.72f + (1f - ring) * 0.4f - 0.02f + jitter
    val key = x + (k2 - x) * s.keyMix
    val lit = smooth(s.lo - 0.02f, s.lo + 0.06f, key) * (1f - smooth(s.hi - 0.06f, s.hi + 0.02f, key))
    val beam = g2((x - s.beam) / 0.16f)
    return 0.24f + 0.1f * beam * (1f - lit) + lit * (0.8f + 0.6f * beam)
}

private fun lit(c: Color, glow: Float) = Color(min(1f, c.red * glow), min(1f, c.green * glow), min(1f, c.blue * glow))

internal fun DrawScope.stainedGlassFallback(width: Float, height: Float, l: StainedGlassLayout, s: StainedGlassScene) {
    val r = l.r
    val c = Offset(l.cx, l.cy)
    val floorTop = l.cy + r * 1.92f
    val lightAll = min(1f, max(0f, (s.hi - s.lo) * 0.9f))
    // The wall and its glow round the window.
    drawRect(Color(0.1f, 0.088f, 0.077f), Offset.Zero, Size(width, height))
    drawRect(
        Brush.radialGradient(listOf(Color(0.2f, 0.18f, 0.15f, 0.3f + 0.6f * lightAll), Color(0.2f, 0.18f, 0.15f, 0f)), c, r * 3f),
        Offset.Zero,
        Size(width, height),
    )
    var y = 0f
    while (y < floorTop) {
        drawRect(Color(0f, 0f, 0f, 0.18f), Offset(0f, y), Size(width, 1f))
        y += 30f
    }
    // The floor and the window's colours on it.
    drawRect(Brush.verticalGradient(listOf(Color(0.07f, 0.06f, 0.055f), Color(0.15f, 0.13f, 0.115f)), floorTop, height), Offset(0f, floorTop), Size(width, height - floorTop))
    val patch = Offset(width / 2f - (s.beam - 0.5f) * 150f, floorTop + 0.52f * (height - floorTop))
    withTransform({ scale(1.25f, 0.35f, patch) }) {
        drawCircle(
            Brush.radialGradient(
                listOf(Color(0.85f, 0.45f, 0.35f, 0.7f * lightAll), Color(0.4f, 0.25f, 0.6f, 0.4f * lightAll), Color(0f, 0f, 0f, 0f)),
                patch,
                r * 1.1f,
            ),
            r * 1.1f,
            patch,
        )
    }
    // The shaft of light from the window to the floor.
    val shaft = Path().apply {
        val axis = l.cx - (s.beam - 0.5f) * 150f
        moveTo(l.cx - r * 0.75f, l.cy)
        lineTo(l.cx + r * 0.75f, l.cy)
        lineTo(axis + r * 0.95f, height)
        lineTo(axis - r * 0.95f, height)
        close()
    }
    drawPath(shaft, Brush.verticalGradient(listOf(Color(1f, 0.82f, 0.6f, 0.1f * lightAll), Color(1f, 0.82f, 0.6f, 0.04f * lightAll)), l.cy, height))

    // The stone frame.
    drawCircle(Color(0.2f, 0.18f, 0.16f), r * 1.04f, c)
    drawCircle(Color(0.28f, 0.25f, 0.2f).copy(alpha = 0.6f), r * 0.99f, c, style = Stroke(r * 0.03f))
    // Border panes.
    for (i in 0 until 32) {
        val color = when (i % 4) { 0 -> Ruby; 1 -> Cobalt; 2 -> Amber; else -> Emerald }
        ringSector(c, r * 0.8f, r * 0.94f, i, 32, lit(color, glowOf(s, (i + 0.5f) / 32f, 0.87f, 1f, 80f + i)))
    }
    // Bays with their foils.
    for (i in 0 until 16) {
        val bay = if (i % 2 == 0) Violet else Cobalt
        ringSector(c, r * 0.5f, r * 0.8f, i, 16, lit(bay, glowOf(s, (i + 0.5f) / 16f, 0.65f, 0.7f, 56f + i)))
        val a = ((i + 0.5f) / 16f - 0.5f) * TAU
        val fc = Offset(l.cx + cos(a) * r * 0.65f, l.cy + sin(a) * r * 0.65f)
        drawCircle(lit(if (i % 2 == 0) Emerald else Amber, glowOf(s, (i + 0.5f) / 16f, 0.65f, 0.7f, 40f + i)), r * 0.095f, fc)
        drawCircle(Color(0.03f, 0.028f, 0.026f), r * 0.095f, fc, style = Stroke(1.4f))
    }
    // Petals on cobalt.
    for (i in 0 until 8) {
        ringSector(c, r * 0.16f, r * 0.5f, i, 8, lit(Cobalt, glowOf(s, (i + 0.5f) / 8f, 0.33f, 0.35f, 28f + i)))
        val petal = Path()
        val steps = 14
        for (j in 0..steps) {
            val rr = 0.16f + 0.34f * j / steps
            val w = 0.44f * max(0f, sin(PI.toFloat() * (rr - 0.16f) / 0.34f)).pow(0.55f)
            val a = (i + 0.5f - w * 0.5f) / 8f
            val ang = (a - 0.5f) * TAU
            val p = Offset(l.cx + cos(ang) * rr * r, l.cy + sin(ang) * rr * r)
            if (j == 0) petal.moveTo(p.x, p.y) else petal.lineTo(p.x, p.y)
        }
        for (j in steps downTo 0) {
            val rr = 0.16f + 0.34f * j / steps
            val w = 0.44f * max(0f, sin(PI.toFloat() * (rr - 0.16f) / 0.34f)).pow(0.55f)
            val ang = ((i + 0.5f + w * 0.5f) / 8f - 0.5f) * TAU
            petal.lineTo(l.cx + cos(ang) * rr * r, l.cy + sin(ang) * rr * r)
        }
        petal.close()
        drawPath(petal, lit(if (i % 2 == 0) Ruby else Amber, glowOf(s, (i + 0.5f) / 8f, 0.33f, 0.35f, 20f + i)))
        drawPath(petal, Color(0.03f, 0.028f, 0.026f), style = Stroke(1.4f))
    }
    // The hub: a gold five-petalled rose with a ruby heart.
    val hubGlow = glowOf(s, 0.5f, 0f, 0f, 2f)
    drawCircle(lit(Amber.copy(red = Amber.red * 0.85f, green = Amber.green * 0.85f, blue = Amber.blue * 0.85f), hubGlow), r * 0.16f, c)
    val rose = Path()
    for (j in 0..60) {
        val a = j / 60f
        val pa = fract(a * 5f) - 0.5f
        val rr = 0.16f * (0.62f + 0.38f * cos(pa * TAU))
        val ang = (a - 0.5f) * TAU
        val p = Offset(l.cx + cos(ang) * rr * r, l.cy + sin(ang) * rr * r)
        if (j == 0) rose.moveTo(p.x, p.y) else rose.lineTo(p.x, p.y)
    }
    rose.close()
    drawPath(rose, lit(Gold, hubGlow))
    drawCircle(lit(Ruby, glowOf(s, 0.5f, 0f, 0f, 1f)), r * 0.055f, c)
    // Lead and stone rings.
    val lead = Color(0.03f, 0.028f, 0.026f)
    drawCircle(lead, r * 0.055f, c, style = Stroke(1.2f))
    drawCircle(lead, r * 0.16f, c, style = Stroke(1.6f))
    drawCircle(Color(0.16f, 0.14f, 0.12f), r * 0.5f, c, style = Stroke(3.2f))
    drawCircle(Color(0.16f, 0.14f, 0.12f), r * 0.8f, c, style = Stroke(3.2f))
    drawCircle(lead, r * 0.94f, c, style = Stroke(2f))

    // Flood: warm light pours out of the window into cream.
    if (s.flood > 0f) {
        val reach = s.flood * 1.45f * max(width, height)
        val k = smooth(0f, 0.35f, s.flood)
        val m = smooth(0.35f, 1f, s.flood)
        val col = Color(1f + (0.965f - 1f) * m, 0.93f + (0.91f - 0.93f) * m, 0.78f + (0.81f - 0.78f) * m)
        drawCircle(
            Brush.radialGradient(0f to col.copy(alpha = k), 0.55f to col.copy(alpha = k), 1f to col.copy(alpha = 0f), center = c, radius = max(1f, reach)),
            max(1f, reach),
            c,
        )
    }
    // Vignette.
    drawRect(
        Brush.radialGradient(
            0.5f to Color(0f, 0f, 0f, 0f),
            1f to Color(0f, 0f, 0f, 0.35f * (1f - 0.75f * s.flood)),
            center = Offset(width / 2f, height / 2f),
            radius = max(width, height) * 0.62f,
        ),
        Offset.Zero,
        Size(width, height),
    )
}

/** Pane i of n in the ring from r0 to r1, with its lead lines. */
private fun DrawScope.ringSector(c: Offset, r0: Float, r1: Float, i: Int, n: Int, color: Color) {
    val mid = (r0 + r1) / 2f
    val start = (i.toFloat() / n - 0.5f) * 360f
    drawArc(color, start, 360f / n, false, Offset(c.x - mid, c.y - mid), Size(mid * 2f, mid * 2f), style = Stroke(abs(r1 - r0)))
    val ang = (i.toFloat() / n - 0.5f) * TAU
    drawLine(Color(0.03f, 0.028f, 0.026f), Offset(c.x + cos(ang) * r0, c.y + sin(ang) * r0), Offset(c.x + cos(ang) * r1, c.y + sin(ang) * r1), 1.4f)
}

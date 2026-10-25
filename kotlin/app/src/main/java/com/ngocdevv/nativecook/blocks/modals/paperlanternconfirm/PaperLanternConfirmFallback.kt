package com.ngocdevv.nativecook.blocks.modals.paperlanternconfirm

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

// Below Android 13 there is no AGSL: the sky and the lanterns are drawn from gradients and paths.
// The sky keeps the shader's three stops, its moon and halo, a fixed field of twinkling stars, the
// eighteen far lanterns on the same paths and phases, and two ridges of hills. A lantern is its
// bag's outline (the shader's own profile, sagging while folded) filled with paper or with the warm
// light from inside, a few fold shadows, the bamboo ring, the flame and a halo.

private const val TAU = (PI * 2).toFloat()
private fun fract(x: Float) = x - floor(x)
private fun hash(x: Float, y: Float) = fract(sin(x * 127.1f + y * 311.7f) * 43758.5453f)
private fun noise1(x: Float, y: Float): Float {
    val i = floor(x)
    val f = x - i
    val u = f * f * (3f - 2f * f)
    return hash(i, y) + (hash(i + 1f, y) - hash(i, y)) * u
}

internal fun DrawScope.paperLanternSkyFallback(width: Float, height: Float, horizon: Float, phase: Float) {
    drawRect(
        Brush.verticalGradient(
            0f to Color(0.03f, 0.045f, 0.13f),
            0.62f to Color(0.16f, 0.09f, 0.24f),
            1f to Color(0.52f, 0.27f, 0.24f),
            startY = 0f,
            endY = horizon,
        ),
        Offset.Zero,
        Size(width, height),
    )
    // Stars: one per 9 dp cell where the cell's hash says so, as in the shader.
    var cy = 0f
    while (cy < horizon * 0.85f) {
        var cx = 0f
        while (cx < width) {
            val gx = cx / 9f
            val gy = cy / 9f
            val sh = hash(gx, gy)
            if (sh > 0.93f) {
                val sp = Offset((gx + 0.2f + 0.6f * hash(gx + 1.3f, gy + 1.3f)) * 9f, (gy + 0.2f + 0.6f * hash(gx + 2.9f, gy + 2.9f)) * 9f)
                val tw = 0.6f + 0.4f * sin(phase * TAU * (1f + floor(sh * 40f - 37f)) + sh * 50f)
                val fade = 1f - min(1f, max(0f, (sp.y / horizon - 0.2f) / 0.65f))
                drawCircle(Color(0.9f, 0.92f, 1f, min(1f, tw * fade * (0.5f + 0.5f * hash(gx + 7f, gy + 7f)))), 0.9f, sp)
            }
            cx += 9f
        }
        cy += 9f
    }
    // The moon and its halo.
    val mc = Offset(width * 0.8f, horizon * 0.2f)
    drawCircle(Brush.radialGradient(listOf(Color(0.6f, 0.6f, 0.75f, 0.25f), Color(0.6f, 0.6f, 0.75f, 0f)), mc, 120f), 120f, mc)
    drawCircle(Color(0.95f, 0.92f, 0.84f), 16f, mc)
    // Far lanterns.
    for (i in 0 until 18) {
        val fi = i.toFloat()
        val h = hash(fi, 1.7f)
        val depth = 0.35f + 0.65f * hash(fi, 4.1f)
        val v = fract(phase + h)
        val x = (0.06f + 0.88f * hash(fi, 8.3f)) * width + sin(v * 7f + fi) * 10f * depth
        val y = horizon - 10f - v * (horizon + 40f) * (0.55f + 0.45f * depth)
        val s = 2.2f + 4.6f * depth * (1f - 0.45f * v)
        val fade = min(1f, v / 0.08f) * (1f - min(1f, max(0f, (v - 0.75f) / 0.25f)))
        val flick = 0.85f + 0.15f * sin(phase * TAU * (3f + floor(h * 4f)) + fi)
        val a = fade * flick * (0.45f + 0.55f * depth)
        if (a <= 0.01f) continue
        val c = Offset(x, y)
        drawCircle(Brush.radialGradient(listOf(Color(1f, 0.62f, 0.28f, 0.35f * a), Color(1f, 0.62f, 0.28f, 0f)), c, s * 3f), s * 3f, c)
        drawOval(Color(1f, 0.62f, 0.28f, min(1f, 0.9f * a)), Offset(x - s * 0.85f, y - s * 1.15f), Size(s * 1.7f, s * 2.3f))
    }
    // Two ridges of hills.
    for (ridge in 0 until 2) {
        val path = Path()
        path.moveTo(0f, height)
        var x = 0f
        while (x <= width + 6f) {
            val y = if (ridge == 0) {
                horizon - 34f - 26f * noise1(x * 0.012f, 3f) - 10f * noise1(x * 0.04f, 1f)
            } else {
                horizon - 12f - 22f * noise1(x * 0.008f + 9f, 5f) - 6f * noise1(x * 0.05f, 2f)
            }
            path.lineTo(x, y)
            x += 6f
        }
        path.lineTo(width, height)
        path.close()
        drawPath(path, if (ridge == 0) Color(0.14f, 0.08f, 0.16f, 0.85f) else Color(0.065f, 0.042f, 0.08f))
    }
}

/** The bag's half-width at height y (top −100, ring +78), as the shader's `bagHalf`. */
private fun bagHalf(y: Float, fold: Float): Float {
    val top = -100f + 16f * fold
    if (y < top) return 0f
    val s = min(1f, max(0f, (y - top) / (78f - top)))
    val dome = sin(min(s / 0.2f, 1f) * 1.5708f).pow(0.6f)
    val e = min(1f, max(0f, (s - 0.22f) / 0.78f))
    var w = (64f + (40f - 64f) * e * e * (3f - 2f * e)) * dome
    w *= 1f + (0.8f + 0.2f * s - 1f) * fold
    w *= 1f - fold * 0.05f * (0.5f + 0.5f * cos(y * 0.28f))
    return w
}

/** One lantern in its own coordinates (origin at its middle). */
internal fun DrawScope.paperLanternBagFallback(fold: Float, glow: Float, flicker: Float, alpha: Float) {
    val top = -100f + 16f * fold
    // The halo round a lit lantern.
    if (glow > 0.01f) {
        drawOval(
            Brush.radialGradient(listOf(Color(1f, 0.6f, 0.28f, 0.45f * glow * alpha), Color(1f, 0.6f, 0.28f, 0f)), Offset(0f, -6f), 110f),
            Offset(-95f, -130f),
            Size(190f, 248f),
        )
    }
    val bag = Path()
    val steps = 40
    for (i in 0..steps) {
        val y = top + (78f - top) * i / steps
        if (i == 0) bag.moveTo(bagHalf(y, fold), y) else bag.lineTo(bagHalf(y, fold), y)
    }
    for (i in steps downTo 0) {
        val y = top + (78f - top) * i / steps
        bag.lineTo(-bagHalf(y, fold), y)
    }
    bag.close()
    val paper = Color(0.97f, 0.94f, 0.88f)
    drawPath(bag, Brush.verticalGradient(listOf(paper, Color(0.88f, 0.85f, 0.8f)), top, 78f), alpha = alpha)
    if (glow > 0.01f) {
        val f = 0.92f + 0.08f * sin(flicker * TAU * 4f)
        drawPath(
            bag,
            Brush.verticalGradient(listOf(Color(0.98f, 0.5f, 0.15f), Color(1f, 0.85f * f, 0.55f * f), Color(1f, 0.93f, 0.68f)), top, 78f),
            alpha = alpha * glow,
        )
    }
    clipPath(bag) {
        // Darker sides, seams, and the folds while it is creased.
        drawRect(
            Brush.horizontalGradient(
                0f to Color(0f, 0f, 0f, 0.28f), 0.25f to Color(0f, 0f, 0f, 0f), 0.75f to Color(0f, 0f, 0f, 0f), 1f to Color(0f, 0f, 0f, 0.28f),
                startX = -64f, endX = 64f,
            ),
            Offset(-64f, top),
            Size(128f, 78f - top),
            alpha = alpha,
        )
        for (sx in floatArrayOf(-0.55f, 0.55f)) {
            drawLine(Color(0.4f, 0.25f, 0.15f, 0.18f * alpha), Offset(sx * 64f, top), Offset(sx * 40f, 78f), 1.2f)
        }
        if (fold > 0.01f) {
            var y = top + 6f
            while (y < 78f) {
                drawRect(Color(0.3f, 0.2f, 0.12f, 0.12f * fold * alpha), Offset(-70f, y), Size(140f, 4f))
                y += 22.4f
            }
        }
    }
    // The bamboo ring, the cross wire and the fuel cell.
    val ringW = bagHalf(77f, fold)
    drawOval(Color(0.6f + 0.3f * glow, 0.43f + 0.2f * glow, 0.24f + 0.1f * glow, alpha), Offset(-ringW, 74f), Size(ringW * 2f, 8f), style = Stroke(2.2f))
    drawLine(Color(0.42f, 0.3f, 0.17f, 0.8f * alpha), Offset(-ringW, 78f), Offset(ringW, 78f), 1f)
    drawRect(Color(0.18f + 0.6f * glow, 0.15f + 0.3f * glow, 0.13f + 0.1f * glow, alpha), Offset(-6f, 76f), Size(12f, 6f))
    // The flame.
    if (glow > 0.02f) {
        val fh = 14f * glow * (0.9f + 0.1f * sin(flicker * TAU * 5f))
        val fx = sin(flicker * TAU * 3f) * 0.8f
        val fw = 3.4f + 1.2f * glow
        drawOval(
            Brush.verticalGradient(listOf(Color(1f, 0.45f, 0.1f), Color(1f, 0.95f, 0.75f)), 70f - fh, 70f),
            Offset(fx - fw, 70f - fh),
            Size(fw * 2f, fh),
            alpha = alpha,
        )
        drawCircle(Color(1f, 0.8f, 0.4f, 0.25f * glow * alpha * exp(-0.5f)), 12f, Offset(fx, 66f))
    }
}

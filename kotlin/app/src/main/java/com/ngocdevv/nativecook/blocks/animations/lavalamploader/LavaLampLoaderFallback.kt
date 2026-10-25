package com.ngocdevv.nativecook.blocks.animations.lavalamploader

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

// Below Android 13 there is no AGSL: the lamp is drawn from gradients and paths instead. The wall,
// the glow, the shelf and the lamp's silhouette follow the shader's numbers; the wax is a set of
// soft ovals (each metaball's iso-surface at its own size, magnified by the glass like the shader's
// lens near the axis), so the blobs rise, merge and sink with the same pose, without the necking.

private fun lavaProfile(y: Float, h: Float, k: Float): Float {
    val capEnd = h * LAVA_CAP_END
    val glassEnd = h * LAVA_GLASS_END
    if (y < capEnd) {
        val s = min(1f, max(0f, y / capEnd))
        val crown = sqrt(min(1f, max(0f, y / (5f * k))))
        return (20f + 16f * s) * k * crown
    }
    if (y < glassEnd) return lavaGlassHalfWidth(y, h, k)
    val s = min(1f, max(0f, (y - glassEnd) / (h - glassEnd)))
    val collar = 1f - 0.06f * exp(-((s - 0.06f) / 0.035f).let { it * it })
    return (71f + 45f * s.pow(0.9f)) * k * collar
}

/** One part of the lamp (lamp-local y from a to b) as a closed outline in screen dp. */
private fun lavaPart(l: LavaLampLayout, a: Float, b: Float): Path {
    val path = Path()
    val steps = 24
    for (i in 0..steps) {
        val y = a + (b - a) * i / steps
        val x = l.cx + lavaProfile(y, l.h, l.k)
        if (i == 0) path.moveTo(x, l.top + y) else path.lineTo(x, l.top + y)
    }
    for (i in steps downTo 0) {
        val y = a + (b - a) * i / steps
        path.lineTo(l.cx - lavaProfile(y, l.h, l.k), l.top + y)
    }
    path.close()
    return path
}

private fun chromeBrush(l: LavaLampLayout, hw: Float, warm: Float): Brush {
    val c = Color(0.075f, 0.06f, 0.06f)
    val hot = Color(0.18f + 0.35f * warm, 0.1f + 0.12f * warm, 0.08f + 0.05f * warm)
    return Brush.horizontalGradient(
        0f to Color(0.03f, 0.025f, 0.025f),
        0.2f to c,
        0.26f to Color(0.82f, 0.78f, 0.75f),
        0.34f to c,
        0.5f to hot,
        0.68f to c,
        0.71f to Color(0.3f, 0.3f, 0.34f),
        0.8f to c,
        1f to Color(0.03f, 0.025f, 0.025f),
        startX = l.cx - hw,
        endX = l.cx + hw,
    )
}

internal fun DrawScope.lavaLampFallback(width: Float, height: Float, l: LavaLampLayout, pose: FloatArray, glow: Float) {
    val k = l.k
    val capEnd = l.h * LAVA_CAP_END
    val glassEnd = l.h * LAVA_GLASS_END
    // The wall and the lamp's glow on it.
    drawRect(Brush.verticalGradient(listOf(Color(0.07f, 0.035f, 0.05f), Color(0.13f, 0.055f, 0.045f)), 0f, height), Offset.Zero, Size(width, height))
    val gc = Offset(l.cx, l.top + l.h * 0.48f)
    drawRect(
        Brush.radialGradient(
            0f to Color(1f, 0.38f, 0.16f, min(1f, 0.42f * glow)),
            0.45f to Color(1f, 0.38f, 0.16f, 0.12f * glow),
            1f to Color(1f, 0.38f, 0.16f, 0f),
            center = gc,
            radius = 300f * k,
        ),
        Offset.Zero,
        Size(width, height),
    )
    // The shelf: a lit top face and a dark front.
    val shelf = l.top + l.h
    drawRect(Color(0.25f, 0.12f, 0.07f), Offset(0f, shelf), Size(width, 16f * k))
    drawRect(
        Brush.radialGradient(listOf(Color(1f, 0.42f, 0.18f, 0.45f * glow), Color(1f, 0.42f, 0.18f, 0f)), Offset(l.cx, shelf), 150f * k),
        Offset(0f, shelf),
        Size(width, 16f * k),
    )
    drawRect(
        Brush.verticalGradient(listOf(Color(0.13f, 0.06f, 0.04f), Color(0.07f, 0.03f, 0.02f)), shelf + 16f * k, shelf + 76f * k),
        Offset(0f, shelf + 16f * k),
        Size(width, height - shelf - 16f * k),
    )

    // The glass: amber liquid, hottest over the bulb; the wax inside it.
    val glass = lavaPart(l, capEnd, glassEnd)
    drawPath(
        glass,
        Brush.verticalGradient(
            0f to Color(0.24f, 0.1f, 0.07f),
            0.06f to Color(0.36f, 0.12f, 0.05f),
            0.7f to Color(0.62f, 0.3f, 0.1f),
            1f to Color(1f, 0.62f, 0.22f),
            startY = l.top + capEnd,
            endY = l.top + glassEnd,
        ),
    )
    clipPath(glass) {
        // The pool under the floor and the blobs: each one's surface at about 0.75 of its radius.
        lavaBlob(l, -22f * k, glassEnd + 44f * k, 60f * k, 0.62f, glassEnd)
        lavaBlob(l, 27f * k, glassEnd + 52f * k, 58f * k, 0.62f, glassEnd)
        for (i in 0 until 6) {
            val r = pose[i * 4 + 2]
            if (r > 0.5f) lavaBlob(l, pose[i * 4], pose[i * 4 + 1], r, pose[i * 4 + 3], glassEnd)
        }
        // Two streaks of the softboxes on the glass, and its darker edges.
        val hw = lavaGlassHalfWidth(glassEnd, l.h, k)
        drawRect(
            Brush.horizontalGradient(
                0f to Color(0f, 0f, 0f, 0.35f),
                0.08f to Color(0f, 0f, 0f, 0f),
                0.17f to Color(1f, 0.95f, 0.9f, 0f),
                0.2f to Color(1f, 0.95f, 0.9f, 0.35f),
                0.23f to Color(1f, 0.95f, 0.9f, 0f),
                0.84f to Color(1f, 0.9f, 0.85f, 0f),
                0.855f to Color(1f, 0.9f, 0.85f, 0.2f),
                0.87f to Color(1f, 0.9f, 0.85f, 0f),
                0.92f to Color(0f, 0f, 0f, 0f),
                1f to Color(0f, 0f, 0f, 0.35f),
                startX = l.cx - hw,
                endX = l.cx + hw,
            ),
            Offset(l.cx - hw, l.top + capEnd),
            Size(hw * 2f, glassEnd - capEnd),
        )
    }
    // The chrome cap and base.
    drawPath(lavaPart(l, 0f, capEnd), chromeBrush(l, 36f * k, 0.5f))
    drawPath(lavaPart(l, glassEnd, l.h), chromeBrush(l, 116f * k, 0.4f * glow))
    drawRect(
        Brush.verticalGradient(listOf(Color(1f, 0.5f, 0.25f, 0.55f * min(1f, glow)), Color(1f, 0.5f, 0.25f, 0f)), l.top + glassEnd, l.top + glassEnd + 6f * k),
        Offset(l.cx - 80f * k, l.top + glassEnd),
        Size(160f * k, 6f * k),
    )
}

/** One wax blob at liquid point (x, y): an oval lit from below, coral low and crimson high. */
private fun DrawScope.lavaBlob(l: LavaLampLayout, x: Float, y: Float, r: Float, w: Float, glassEnd: Float) {
    val t = min(1f, max(0f, (y - l.h * LAVA_CAP_END) / (glassEnd - l.h * LAVA_CAP_END)))
    val heat = t * t * (0.6f + 0.4f * t) + 0.12f
    val hot = Color(0.98f, 0.2f, 0.1f)
    val cool = Color(0.42f, 0.025f, 0.1f)
    val base = Color(
        cool.red + (hot.red - cool.red) * min(1f, heat),
        cool.green + (hot.green - cool.green) * min(1f, heat),
        cool.blue + (hot.blue - cool.blue) * min(1f, heat),
    )
    val rx = r * 0.76f / 0.74f
    val ry = r * 0.76f * w
    val c = Offset(l.cx + x / 0.74f, l.top + y)
    drawOval(
        Brush.radialGradient(
            0f to Color(min(1f, base.red * 1.25f + 0.1f), min(1f, base.green * 1.4f + 0.08f), base.blue * 1.2f),
            0.75f to base,
            1f to Color(min(1f, base.red + 0.25f), min(1f, base.green + 0.2f), min(1f, base.blue + 0.06f)),
            center = Offset(c.x, c.y + ry * 0.25f),
            radius = max(rx, ry) * 1.05f,
        ),
        Offset(c.x - rx, c.y - ry),
        Size(rx * 2f, ry * 2f),
    )
}

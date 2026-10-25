package com.ngocdevv.nativecook.blocks.animations.inkdropbloom

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Below Android 13 (no RuntimeShader): the same room, glass, plumes and press drawn with
 * gradients in dp units. Each plume is its head (a wide soft cap) and, once the ring breaks, its
 * two children, at the shader's positions and sizes; the stem is a thin soft line. There is no
 * subtractive mixing, billowing or refraction, but the timing and the colours read the same.
 */
internal fun DrawScope.drawInkFallback(g: InkGeo, f: InkFrame) {
    val horizon = g.bottom - 34f
    // Wall and table.
    drawRect(
        Brush.verticalGradient(
            0f to Color(0.925f, 0.900f, 0.862f),
            0.5f to Color(0.895f, 0.862f, 0.818f),
            1f to Color(0.858f, 0.818f, 0.770f),
            startY = 0f,
            endY = horizon,
        ),
        size = Size(g.w, horizon),
    )
    drawRect(
        Brush.verticalGradient(
            0f to Color(0.835f, 0.795f, 0.748f),
            1f to Color(0.905f, 0.875f, 0.835f),
            startY = horizon,
            endY = horizon + (g.h - horizon) * 0.6f,
        ),
        topLeft = Offset(0f, horizon),
        size = Size(g.w, g.h - horizon),
    )
    // The glass's shadow on the table.
    drawOval(
        Brush.radialGradient(
            listOf(Color.Black.copy(alpha = 0.16f), Color.Transparent),
            center = Offset(g.cx + g.hwTop * 0.55f, g.bottom + 4f),
            radius = g.hwTop * 1.25f,
        ),
        topLeft = Offset(g.cx + g.hwTop * 0.55f - g.hwTop * 1.25f, g.bottom - 12f),
        size = Size(g.hwTop * 2.5f, 32f),
    )
    // The glass: a tapered body of pale greenish glass, the water a little cooler.
    val bodyLeft = g.cx - g.hwTop
    drawRoundRect(
        Color(0.80f, 0.86f, 0.85f).copy(alpha = 0.28f),
        topLeft = Offset(bodyLeft, g.top),
        size = Size(g.hwTop * 2f, g.bottom - g.top),
        cornerRadius = CornerRadius(16f),
    )
    val ihw = g.hwBot - g.wall
    drawRect(
        Color(0.93f, 0.97f, 1f).copy(alpha = 0.35f),
        topLeft = Offset(g.cx - ihw, g.surface),
        size = Size(ihw * 2f, g.floor - g.surface),
    )
    // Ink: plumes clipped to the water, faded by the press.
    val amount = f.press[3] * (1f - f.press[2] * f.press[2])
    if (amount > 0.01f) clipRect(g.cx - ihw, g.surface, g.cx + ihw, g.floor) {
        for (i in 0 until 4) {
            val age = f.drops[i * 4 + 2]
            if (age <= 0f) continue
            val x = f.drops[i * 4]
            val y = f.drops[i * 4 + 1]
            val c = InkColors[f.inks[i]]
            val col = Color(c[0], c[1], c[2])
            val room = max(g.floor - y, 1f)
            val r = 9f + 30f * sqrt(age)
            val depth = room * 0.62f * (1f - exp(-age / 2f)) + 10f * age / (1f + age)
            var hy = y + depth + 6f
            if (f.press[0] > 0f) hy = max(hy, f.press[0] + r * 0.4f)
            val conc = (1.9f / (1f + 0.36f * age)).coerceAtMost(1f) * amount
            // The stem.
            drawLine(col.copy(alpha = 0.45f * conc), Offset(x, y), Offset(x, hy), strokeWidth = 2f + r * 0.08f)
            blob(col, x, hy, r * 1.3f, r * 0.7f, 0.85f * conc)
            val kid = ((age - 0.9f) / 1.7f).coerceIn(0f, 1f)
            if (kid > 0f) {
                val drop = r * (0.55f + 0.75f * kid)
                blob(col, x - r * 0.62f, hy + drop, r * 0.62f, r * 0.34f, 0.8f * conc * kid)
                blob(col, x + r * 0.58f, hy + drop * 0.9f, r * 0.62f, r * 0.34f, 0.8f * conc * kid)
            }
        }
    }
    // Highlights: a broad streak on the left, a thin one on the right, the rim ellipse.
    drawRect(
        Color.White.copy(alpha = 0.22f),
        topLeft = Offset(g.cx - g.hwTop * 0.66f, g.top + 30f),
        size = Size(g.hwTop * 0.08f, g.bottom - g.top - 80f),
    )
    drawRect(
        Color.White.copy(alpha = 0.45f),
        topLeft = Offset(g.cx + g.hwTop * 0.77f, g.top + 60f),
        size = Size(1.5f, g.bottom - g.top - 130f),
    )
    drawOval(
        Color(0.98f, 0.99f, 0.985f).copy(alpha = 0.8f),
        topLeft = Offset(bodyLeft, g.top - 7f),
        size = Size(g.hwTop * 2f, 14f),
        style = Stroke(1.4f),
    )
    drawRoundRect(
        Color(0.4f, 0.45f, 0.45f).copy(alpha = 0.35f),
        topLeft = Offset(bodyLeft, g.top),
        size = Size(g.hwTop * 2f, g.bottom - g.top),
        cornerRadius = CornerRadius(16f),
        style = Stroke(1.2f),
    )
    // The press.
    if (f.press[0] > 0f) {
        val py = f.press[0]
        val rodTop = g.top - 44f
        drawRect(
            Color(0.62f, 0.63f, 0.64f).copy(alpha = f.press[1]),
            topLeft = Offset(g.cx - 3.2f, rodTop),
            size = Size(6.4f, py - rodTop),
        )
        val phw = g.hwTop - (g.hwTop - g.hwBot) * ((py - g.top) / (g.bottom - g.top)).coerceIn(0f, 1f) - g.wall - 1f
        drawOval(
            Color(0.64f, 0.66f, 0.67f).copy(alpha = 0.85f * f.press[1]),
            topLeft = Offset(g.cx - phw, py - 6f),
            size = Size(phw * 2f, 12f),
        )
    }
    // The drop in the air.
    if (f.falling[3] > 0f) {
        val c = InkColors[f.fallInk]
        val rr = f.falling[2]
        drawOval(
            Color(c[0], c[1], c[2]).copy(alpha = f.falling[3]),
            topLeft = Offset(f.falling[0] - rr, f.falling[1] - rr * 1.35f * 1.25f),
            size = Size(rr * 2f, rr * 2.7f),
        )
    }
}

private fun DrawScope.blob(col: Color, x: Float, y: Float, rx: Float, ry: Float, a: Float) {
    if (a <= 0.005f) return
    val r = max(rx, ry)
    drawOval(
        Brush.radialGradient(
            0f to col.copy(alpha = a),
            0.6f to col.copy(alpha = a * 0.75f),
            1f to col.copy(alpha = 0f),
            center = Offset(x, y),
            radius = r,
        ),
        topLeft = Offset(x - rx, y - ry),
        size = Size(rx * 2f, ry * 2f),
    )
}

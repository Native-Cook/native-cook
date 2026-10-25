package com.ngocdevv.nativecook.blocks.animations.liquidmetalmorph

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Below Android 13 (no AGSL): the backdrop as gradients and the blob as paths filled with a chrome
 * gradient, each shape faded by its weight, so the drop → star → ring morph still reads as a
 * crossfade of polished shapes. Satellites and the held droplet are plain chrome circles.
 * Drawn in dp (the caller scales by the density).
 */
internal fun DrawScope.liquidMetalFallback(width: Float, height: Float, cx: Float, cy: Float, r: Float, m: LiquidMetal) {
    drawRect(
        Brush.radialGradient(
            listOf(Color(0xFF1E2026), Color(0xFF121316)),
            center = Offset(cx, cy),
            radius = height * 0.62f,
        ),
        size = Size(width, height),
    )
    // The floor shadow under the blob.
    drawOval(
        Brush.radialGradient(
            listOf(Color.Black.copy(alpha = 0.45f), Color.Transparent),
            center = Offset(cx, cy + 1.62f * r),
            radius = 1.25f * r,
        ),
        topLeft = Offset(cx - 1.25f * r, cy + 1.62f * r - 0.3f * r),
        size = Size(2.5f * r, 0.6f * r),
    )
    val chrome = Brush.verticalGradient(
        0f to Color(0xFFF2F4F8),
        0.28f to Color(0xFF9AA3B5),
        0.55f to Color(0xFF0B0C10),
        0.8f to Color(0xFF3A3F4A),
        1f to Color(0xFF9AA3B5),
        startY = cy - 1.4f * r,
        endY = cy + 1.4f * r,
    )
    val w0 = m.w[0].toFloat().coerceIn(0f, 1f)
    val w1 = m.w[1].toFloat().coerceIn(0f, 1f)
    val w2 = m.w[2].toFloat().coerceIn(0f, 1f)
    if (w0 > 0.01f) drawPath(dropPath(cx, cy, r), chrome, alpha = w0)
    if (w1 > 0.01f) drawPath(starPath(cx, cy, r), chrome, alpha = w1)
    if (w2 > 0.01f) {
        val hole = 0.5f * r * smooth(0.35f, 1f, w2)
        val ring = Path().apply {
            fillType = PathFillType.EvenOdd
            addOval(Rect(Offset(cx, cy), 1.1f * r))
            if (hole > 0.5f) addOval(Rect(Offset(cx, cy), hole))
        }
        drawPath(ring, chrome, alpha = w2)
    }
    for (i in 0 until 4) {
        val sr = m.sats[i * 4 + 2].toFloat()
        if (sr > 0.05f) drawCircle(chrome, sr, Offset(cx + m.sats[i * 4].toFloat(), cy + m.sats[i * 4 + 1].toFloat()))
    }
    if (m.shown) {
        val dr = m.dropletRadius(r.toDouble()).toFloat()
        val d = Offset(m.dx.toFloat(), m.dy.toFloat())
        drawLine(chrome, Offset(m.hx.toFloat(), m.hy.toFloat()), d, strokeWidth = dr * 0.8f, cap = StrokeCap.Round)
        drawCircle(chrome, dr, d)
    }
    // A key highlight on the upper left.
    drawCircle(
        Brush.radialGradient(listOf(Color.White.copy(alpha = 0.55f), Color.Transparent), Offset(cx - 0.4f * r, cy - 0.45f * r), 0.3f * r),
        0.3f * r,
        Offset(cx - 0.4f * r, cy - 0.45f * r),
    )
    drawCircle(Color.White.copy(alpha = 0.06f), 1.2f * r, Offset(cx, cy), style = Stroke(1f))
}

private fun smooth(a: Float, b: Float, x: Float): Float {
    val t = ((x - a) / (b - a)).coerceIn(0f, 1f)
    return t * t * (3 - 2 * t)
}

/** The uneven capsule of the shader: round end 0.9 R below, tip 0.15 R above. */
private fun dropPath(cx: Float, cy: Float, r: Float): Path {
    val r1 = 0.9f * r
    val r2 = 0.15f * r
    val h = 1.42f * r
    val by = cy + 0.26f * r
    val ty = by - h
    val b = (r1 - r2) / h
    val a = sqrt(1 - b * b)
    val hull = Path().apply {
        moveTo(cx - a * r1, by - b * r1)
        lineTo(cx - a * r2, ty - b * r2)
        lineTo(cx + a * r2, ty - b * r2)
        lineTo(cx + a * r1, by - b * r1)
        close()
    }
    val big = Path().apply { addOval(Rect(Offset(cx, by), r1)) }
    val tip = Path().apply { addOval(Rect(Offset(cx, ty), r2)) }
    val first = Path().apply { op(big, hull, PathOperation.Union) }
    return Path().apply { op(first, tip, PathOperation.Union) }
}

/** A four-point star, points up, down, left and right, with rounded valleys. */
private fun starPath(cx: Float, cy: Float, r: Float): Path {
    val outer = 1.31f * r
    val inner = 0.52f * r
    return Path().apply {
        for (k in 0 until 4) {
            val a = (-PI / 2 + k * PI / 2).toFloat()
            val px = cx + cos(a) * outer
            val py = cy + sin(a) * outer
            if (k == 0) moveTo(px, py) else lineTo(px, py)
            val va = a + (PI / 4).toFloat()
            val nx = (-PI / 2 + (k + 1) * PI / 2).toFloat()
            quadraticTo(cx + cos(va) * inner, cy + sin(va) * inner, cx + cos(nx) * outer, cy + sin(nx) * outer)
        }
        close()
    }
}

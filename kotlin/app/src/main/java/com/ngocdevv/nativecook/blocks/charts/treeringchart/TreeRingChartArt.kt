package com.ngocdevv.nativecook.blocks.charts.treeringchart

import android.graphics.Bitmap
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import kotlin.math.max

private fun rgba(r: Int, g: Int, b: Int, a: Float) = Color(r / 255f, g / 255f, b / 255f, a.coerceIn(0f, 1f))

/**
 * A small tile of film grain: each pixel is white or black with an alpha by how far its hashed
 * value sits from mid grey, which reads like Skia's overlay of fractal noise without a blend mode
 * (an Android multiply/overlay can eat alpha). Built once.
 */
internal fun treeGrainTile(): ImageBitmap {
    val n = 96
    val px = IntArray(n * n)
    for (y in 0 until n) {
        for (x in 0 until n) {
            var h = x * 374761393 + y * 668265263
            h = (h xor (h ushr 13)) * 1274126177
            h = h xor (h ushr 16)
            val v = (h and 0xFFFF) / 65535f
            val a = ((kotlin.math.abs(v - 0.5f) * 2f) * 255f).toInt().coerceIn(0, 255)
            val c = if (v > 0.5f) 0xFFFFFF else 0x000000
            px[y * n + x] = (a shl 24) or c
        }
    }
    return Bitmap.createBitmap(px, n, n, Bitmap.Config.ARGB_8888).asImageBitmap()
}

/**
 * The static backdrop over the screen's gradient and its grain, in dp: a vignette, a warm pool of light
 * under the slice and one faint dashed circle per year at its full-grown radius.
 */
internal fun DrawScope.drawTreeBackdrop(g: TreeGeo?, width: Float, height: Float) {
    drawRect(
        Brush.radialGradient(
            0f to Color(0x00000000),
            0.6f to rgba(8, 4, 2, 0.18f),
            1f to rgba(8, 4, 2, 0.5f),
            center = Offset(width / 2f, height * 0.42f),
            radius = max(width, height) * 0.72f,
        ),
        size = Size(width, height),
    )
    if (g == null) return
    val dash = PathEffect.dashPathEffect(floatArrayOf(2f, 5f), 0f)
    for (i in 0 until RING_COUNT) {
        drawCircle(
            rgba(240, 220, 190, 0.2f),
            radius = g.full[i] + if (i == RING_COUNT - 1) g.bark else 0f,
            center = Offset(g.cx, g.cy),
            style = Stroke(width = 1f, pathEffect = dash),
        )
    }
    drawRect(
        Brush.radialGradient(
            0f to rgba(150, 96, 52, 0.22f),
            0.55f to rgba(110, 66, 36, 0.08f),
            1f to rgba(60, 36, 22, 0f),
            center = Offset(g.cx, g.cy + g.slice * 0.08f),
            radius = g.slice * 1.45f,
        ),
        size = Size(width, height),
    )
}

/**
 * The annotation: a ringed dot on the selected year, straight below the pith, and a thin leader
 * that drops from it to the label under the slice.
 */
internal fun DrawScope.drawTreeLeader(g: TreeGeo, mid: Float, glow: Float) {
    val x = g.cx
    val y = g.cy + mid
    val end = g.oy + g.h - 4f
    val a = 0.35f + 0.65f * glow
    if (end > y + 6f) {
        drawLine(
            Brush.linearGradient(
                0f to rgba(255, 214, 150, 0.95f * a),
                0.6f to rgba(255, 196, 120, 0.55f * a),
                1f to rgba(255, 190, 110, 0.15f),
                start = Offset(x, y),
                end = Offset(x, end),
            ),
            start = Offset(x, y + 6f),
            end = Offset(x, end),
            strokeWidth = 1.25f,
            cap = StrokeCap.Round,
        )
    }
    drawCircle(
        Brush.radialGradient(
            0f to rgba(255, 180, 90, 0.45f * a),
            1f to rgba(255, 150, 60, 0f),
            center = Offset(x, y),
            radius = 18f,
        ),
        radius = 18f,
        center = Offset(x, y),
        blendMode = BlendMode.Plus,
    )
    drawCircle(rgba(255, 232, 196, a), radius = 5.5f, center = Offset(x, y), style = Stroke(1.5f))
    drawCircle(rgba(255, 246, 228, a), radius = 2.6f, center = Offset(x, y))
}

private fun mixColor(a: Color, b: Color, f: Float) = Color(
    a.red + (b.red - a.red) * f,
    a.green + (b.green - a.green) * f,
    a.blue + (b.blue - a.blue) * f,
    1f,
)

private fun scaled(c: Color, k: Float) = Color((c.red * k).coerceIn(0f, 1f), (c.green * k).coerceIn(0f, 1f), (c.blue * k).coerceIn(0f, 1f), 1f)

private val Sap = Color(0.89f, 0.74f, 0.53f)
private val Heart = Color(0.69f, 0.40f, 0.22f)
private val BarkOuter = Color(0.25f, 0.15f, 0.095f)
private val InnerBark = Color(0.64f, 0.42f, 0.27f)
private val Cambium = Color(0.92f, 0.77f, 0.55f)
private val PithC = Color(0.36f, 0.2f, 0.1f)
private val Amber = Color(1f, 0.74f, 0.36f)

/**
 * Below Android 13 (no AGSL): the slice as concentric circles in the same colours — the drop
 * shadow and the ghost, the bark ring, each year's earlywood with its darker latewood band and
 * boundary line (heartwood inside, sapwood outside), the amber selection, and the pith. No noise,
 * grain or cracks.
 */
internal fun DrawScope.drawTreeFallback(g: TreeGeo, radii: FloatArray, pose: TreePose, barkW: Float) {
    val c = Offset(g.cx, g.cy)
    val edge = g.wood + g.bark
    // Drop shadow: a soft ambient one and a tight contact one, both offset down.
    drawCircle(
        Brush.radialGradient(
            0f to Color(0f, 0f, 0f, 0.3f),
            (edge - 14f * g.unit) / (edge + 30f * g.unit) to Color(0f, 0f, 0f, 0.3f),
            1f to Color(0f, 0f, 0f, 0f),
            center = c + Offset(0f, 12f * g.unit),
            radius = edge + 30f * g.unit,
        ),
        radius = edge + 30f * g.unit,
        center = c + Offset(0f, 12f * g.unit),
    )
    // The ghost: bark silhouette at 60 %, pale end grain at 35 %.
    drawCircle(Color(0.24f, 0.15f, 0.09f, 0.6f), radius = edge, center = c)
    drawCircle(Color(0.72f, 0.58f, 0.42f, 0.35f), radius = g.wood, center = c)
    val outer = radii[RING_COUNT - 1]
    // Bark, inner bark and the cambium line.
    drawCircle(BarkOuter, radius = outer + barkW, center = c)
    drawCircle(InnerBark, radius = outer + 3f * g.unit, center = c)
    drawCircle(Cambium, radius = outer + 1f * g.unit, center = c)
    for (i in RING_COUNT - 1 downTo 0) {
        val hi = radii[i]
        val lo = if (i == 0) g.pith else radii[i - 1]
        if (hi - lo < 0.3f) continue
        val heart = 1f - smooth(0.52f, 0.7f, ((lo + hi) / 2f) / max(outer, 1f))
        val early = mixColor(Sap, Heart, heart)
        val lateC = scaled(mixColor(Color(0.84f, 0.66f, 0.44f), Color(0.60f, 0.33f, 0.18f), heart), 0.8f)
        val w = (1f - kotlin.math.abs(i - pose.sel)).coerceIn(0f, 1f) * pose.glow
        val lit = { col: Color ->
            val bright = Color((col.red * 1.22f + 0.2f).coerceAtMost(1f), (col.green * 1.22f + 0.11f).coerceAtMost(1f), (col.blue * 1.22f + 0.02f).coerceAtMost(1f))
            scaled(mixColor(col, bright, 0.6f * w), 1f - 0.2f * pose.glow * (1f - w))
        }
        drawCircle(lit(lateC), radius = hi, center = c)
        drawCircle(lit(early), radius = lo + (hi - lo) * 0.7f, center = c)
        drawCircle(scaled(lit(lateC), 0.55f), radius = hi - 0.55f * g.unit, center = c, style = Stroke(1.1f * g.unit))
        if (w > 0.01f) {
            drawCircle(Amber.copy(alpha = 0.55f * w), radius = hi - 0.6f * g.unit, center = c, style = Stroke(1.3f * g.unit))
            drawCircle(Amber.copy(alpha = 0.55f * w), radius = lo + 0.6f * g.unit, center = c, style = Stroke(1.3f * g.unit))
        }
    }
    drawCircle(PithC, radius = g.pith, center = c)
}

private fun smooth(a: Float, b: Float, x: Float): Float {
    val t = ((x - a) / (b - a)).coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
}

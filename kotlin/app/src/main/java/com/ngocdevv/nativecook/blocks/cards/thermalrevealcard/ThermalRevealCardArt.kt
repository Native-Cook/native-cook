package com.ngocdevv.nativecook.blocks.cards.thermalrevealcard

import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.Typeface
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

// The secret map under the ink (thermalArt.ts): drawn once in code into a bitmap the size of the
// card at `scale` pixels per dp, then sampled by the card's shader. Positions are fractions of the
// card. A teal sea, four islands (reef glow, sand, jungle, contour rings), a dashed route through
// three coves marked with an X, a compass rose and italic serif place names.

/** The three coves (fractions of the card), in route order. The autoPlay thumb passes over them. */
internal val ThermalCoves = arrayOf(floatArrayOf(0.28f, 0.24f), floatArrayOf(0.71f, 0.52f), floatArrayOf(0.33f, 0.8f))

private fun hash(i: Double): Double {
    val s = sin(i * 12.9898) * 43758.5453
    return s - floor(s)
}

private fun col(hex: Long) = (hex or 0xFF000000L).toInt()

/** A soft irregular island outline: a ring of points whose radius wobbles, joined with quads. */
private fun islandPath(cx: Float, cy: Float, rx: Float, ry: Float, seed: Int, scale: Float = 1f): Path {
    val n = 18
    val px = FloatArray(n)
    val py = FloatArray(n)
    for (i in 0 until n) {
        val a = i.toDouble() / n * PI * 2
        val wobble = 1 + 0.16 * sin(a * 3 + seed * 1.7) + 0.09 * sin(a * 5 + seed * 3.1) + 0.06 * (hash((seed * 13 + i).toDouble()) - 0.5)
        px[i] = cx + (cos(a) * rx * wobble * scale).toFloat()
        py[i] = cy + (sin(a) * ry * wobble * scale).toFloat()
    }
    fun midX(i: Int) = (px[i % n] + px[(i + 1) % n]) / 2
    fun midY(i: Int) = (py[i % n] + py[(i + 1) % n]) / 2
    val path = Path()
    path.moveTo(midX(0), midY(0))
    for (i in 1..n) path.quadTo(px[i % n], py[i % n], midX(i), midY(i))
    path.close()
    return path
}

private fun fill(color: Long, alpha: Float = 1f) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    this.color = col(color)
    this.alpha = (alpha * 255).toInt()
}

private fun stroke(color: Long, width: Float, alpha: Float = 1f) = fill(color, alpha).apply {
    style = Paint.Style.STROKE
    strokeWidth = width
    strokeCap = Paint.Cap.ROUND
    strokeJoin = Paint.Join.ROUND
}

private fun drawIsland(c: Canvas, cx: Float, cy: Float, rx: Float, ry: Float, seed: Int, scale: Float) {
    // Reef: a pale glow in the shallows. Skia's blur sigma 9 (in dp) as an Android blur radius in px.
    val reef = fill(0x8FE6D6, 0.55f).apply {
        maskFilter = BlurMaskFilter(max(0.01f, (9f * scale - 0.5f) / 0.57735f), BlurMaskFilter.Blur.NORMAL)
    }
    c.drawPath(islandPath(cx, cy, rx, ry, seed, 1.32f), reef)
    c.drawPath(islandPath(cx, cy, rx, ry, seed, 1.16f), stroke(0xD9FFF6, 1f, 0.45f))
    c.drawPath(islandPath(cx, cy, rx, ry, seed, 1.04f), fill(0xF1D39A))
    val jungle = fill(0x3F8A57).apply {
        shader = RadialGradient(
            cx - rx * 0.2f, cy - ry * 0.25f, max(rx, ry),
            intArrayOf(col(0x5EA866), col(0x3C8152), col(0x2B6243)), floatArrayOf(0f, 0.6f, 1f), Shader.TileMode.CLAMP,
        )
    }
    c.drawPath(islandPath(cx, cy, rx, ry, seed, 0.86f), jungle)
    for (k in 1..3) {
        c.drawPath(islandPath(cx + rx * 0.05f * k, cy - ry * 0.04f * k, rx, ry, seed + k, 0.86f - k * 0.17f), stroke(0x1F4B33, 1f, 0.45f))
    }
    c.drawPath(islandPath(cx, cy, rx, ry, seed, 1.04f), stroke(0xB78B4A, 1.2f, 0.6f))
}

private fun drawX(c: Canvas, x: Float, y: Float, s: Float) {
    val under = stroke(0xFFF4DC, 7f, 0.85f)
    val ink = stroke(0xC8302B, 4f)
    c.drawLine(x - s, y - s, x + s, y + s, under)
    c.drawLine(x - s, y + s, x + s, y - s, under)
    c.drawLine(x - s, y - s, x + s, y + s, ink)
    c.drawLine(x - s, y + s, x + s, y - s, ink)
    c.drawCircle(x, y, s * 1.9f, stroke(0xC8302B, 1.2f, 0.55f))
}

private fun drawCompass(c: Canvas, x: Float, y: Float, r: Float, font: Paint) {
    c.drawCircle(x, y, r * 1.05f, stroke(0xFFF1D0, 1.2f, 0.8f))
    c.drawCircle(x, y, r * 0.82f, stroke(0xFFF1D0, 0.8f, 0.5f))
    for (i in 0 until 4) {
        val a = i * PI / 2 - PI / 2
        val tx = x + (cos(a) * r).toFloat()
        val ty = y + (sin(a) * r).toFloat()
        val lx = x + (cos(a - PI / 4) * r * 0.22).toFloat()
        val ly = y + (sin(a - PI / 4) * r * 0.22).toFloat()
        val rx = x + (cos(a + PI / 4) * r * 0.22).toFloat()
        val ry = y + (sin(a + PI / 4) * r * 0.22).toFloat()
        val lit = Path().apply { moveTo(x, y); lineTo(tx, ty); lineTo(lx, ly); close() }
        val dark = Path().apply { moveTo(x, y); lineTo(tx, ty); lineTo(rx, ry); close() }
        c.drawPath(lit, fill(if (i == 0) 0xFFE9B8 else 0xFFF4DC))
        c.drawPath(dark, fill(if (i == 0) 0xC8302B else 0x8A6A3C))
    }
    val paint = Paint(font).apply { color = col(0xFFF4DC) }
    c.drawText("N", x - paint.measureText("N") / 2, y - r * 1.2f, paint)
}

/** Draws the map into a bitmap of w × h dp at `scale` pixels per dp. Safe off the main thread. */
internal fun thermalMapBitmap(w: Float, h: Float, scale: Float): Bitmap {
    val bitmap = Bitmap.createBitmap(ceil(w * scale).toInt().coerceAtLeast(1), ceil(h * scale).toInt().coerceAtLeast(1), Bitmap.Config.ARGB_8888)
    val c = Canvas(bitmap)
    c.scale(scale, scale)

    val sea = Paint().apply {
        shader = LinearGradient(0f, 0f, w * 0.3f, h, intArrayOf(col(0x0B4F6C), col(0x127F97), col(0x2BB3B0)), floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP)
    }
    c.drawRect(0f, 0f, w, h, sea)
    val wave = stroke(0xBFF3EE, 1f, 0.22f)
    for (i in 0 until 70) {
        val x = (hash((i * 3 + 1).toDouble()) * w).toFloat()
        val y = (hash((i * 5 + 2).toDouble()) * h).toFloat()
        val p = Path().apply { moveTo(x, y); quadTo(x + 5, y - 3, x + 10, y); quadTo(x + 15, y + 3, x + 20, y) }
        c.drawPath(p, wave)
    }
    val grid = stroke(0xE8FFFB, 0.6f, 0.12f)
    var gx = 28f
    while (gx < w) { c.drawLine(gx, 0f, gx, h, grid); gx += 34f }
    var gy = 28f
    while (gy < h) { c.drawLine(0f, gy, w, gy, grid); gy += 34f }

    drawIsland(c, w * 0.27f, h * 0.23f, w * 0.2f, h * 0.11f, 1, scale)
    drawIsland(c, w * 0.74f, h * 0.5f, w * 0.17f, h * 0.13f, 2, scale)
    drawIsland(c, w * 0.3f, h * 0.79f, w * 0.22f, h * 0.1f, 3, scale)
    drawIsland(c, w * 0.83f, h * 0.85f, w * 0.07f, h * 0.04f, 4, scale)

    // The route: a pale underlay, then red dashes, from the top-left corner through the coves.
    val pts = listOf(floatArrayOf(0.08f, 0.06f)) + ThermalCoves.toList() + listOf(floatArrayOf(0.9f, 0.95f))
    val route = Path()
    route.moveTo(pts[0][0] * w, pts[0][1] * h)
    for (i in 1 until pts.size) {
        val ax = pts[i - 1][0] * w
        val ay = pts[i - 1][1] * h
        val bx = pts[i][0] * w
        val by = pts[i][1] * h
        val bend = if (i % 2 == 0) 0.28f else -0.28f
        route.cubicTo(
            ax + (bx - ax) * 0.3f + (by - ay) * bend, ay + (by - ay) * 0.3f - (bx - ax) * bend,
            ax + (bx - ax) * 0.7f + (by - ay) * bend, ay + (by - ay) * 0.7f - (bx - ax) * bend,
            bx, by,
        )
    }
    c.drawPath(route, stroke(0xFFF4DC, 5f, 0.55f))
    c.drawPath(route, stroke(0xD23A2F, 2.6f).apply { pathEffect = DashPathEffect(floatArrayOf(9f, 7f), 0f) })

    for (cove in ThermalCoves) drawX(c, cove[0] * w, cove[1] * h, 8f)

    val label = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD_ITALIC); textSize = 12f }
    val small = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.create(Typeface.SERIF, Typeface.ITALIC); textSize = 10f }
    val names = listOf(
        Triple("Cove of Lanterns", ThermalCoves[0][0] * w + 16, ThermalCoves[0][1] * h - 14),
        Triple("Seal Rock", ThermalCoves[1][0] * w - 78, ThermalCoves[1][1] * h + 26),
        Triple("Glass Lagoon", ThermalCoves[2][0] * w + 18, ThermalCoves[2][1] * h + 4),
    )
    val ink = Paint(label).apply { color = col(0xFFF4DC) }
    val shadow = Paint(label).apply { color = col(0x06303F); alpha = (0.7f * 255).toInt() }
    for ((text, x, y) in names) {
        val tw = ink.measureText(text)
        val lx = min(max(x, 10f), w - tw - 10)
        c.drawText(text, lx + 0.6f, y + 0.8f, shadow)
        c.drawText(text, lx, y, ink)
    }
    c.drawText("Aruna Sound", w * 0.52f, h * 0.31f, Paint(small).apply { color = col(0xD6FBF4); alpha = (0.7f * 255).toInt() })
    c.drawText("depth 12 fathoms", w * 0.06f, h * 0.62f, Paint(small).apply { color = col(0xD6FBF4); alpha = (0.55f * 255).toInt() })
    drawCompass(c, w - 40, 54f, 20f, small)

    // An aged border: darker at the very edge.
    val edge = Paint().apply {
        shader = RadialGradient(
            w / 2, h / 2, hypot(w, h) * 0.62f,
            intArrayOf(0, 0, android.graphics.Color.argb((0.45f * 255).toInt(), 4, 24, 32)), floatArrayOf(0f, 0.72f, 1f), Shader.TileMode.CLAMP,
        )
    }
    c.drawRect(0f, 0f, w, h, edge)
    return bitmap
}

package com.ngocdevv.nativecook.blocks.text.inlinepillreveal

import android.graphics.Bitmap
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.sin

internal fun iprHex(v: Long, a: Double = 1.0) = Color(
    ((v shr 16) and 0xFF).toInt() / 255f,
    ((v shr 8) and 0xFF).toInt() / 255f,
    (v and 0xFF).toInt() / 255f,
    a.toFloat().coerceIn(0f, 1f),
)

private fun Double.f() = toFloat()

/**
 * The sunrise scene inside the pill, drawn in code (a port of InlinePillRevealArt.swift): sky, sun,
 * birds, three ridges with cypresses, a farmhouse with a lit window, a road and mist. The scene is
 * small inside the inline pill and zooms to 1 at full bleed while the sun climbs over the ridge.
 */
internal object IprArt {
    private class Layer(val d: Double, val side: Double)

    private val SKY = Layer(0.0, 0.0)
    private val SUN = Layer(85.0, 0.15)
    private val BIRDS = Layer(40.0, 0.2)
    private val FAR = Layer(0.0, 0.3)
    private val MID = Layer(10.0, 0.55)
    private val NEAR = Layer(24.0, 0.8)
    private val FORE = Layer(40.0, 1.0)

    private const val ZOOM_INLINE = 0.42
    private const val LOOK_UP = 60.0

    private val MIST_X = doubleArrayOf(-0.18, 0.18, 0.55, 0.92, 1.36)
    private val MIST_Y = doubleArrayOf(0.0, 6.0, -4.0, 8.0, 2.0)
    private val TREE_X = doubleArrayOf(-0.36, -0.31, 0.10, 0.14, 0.56, 0.60, 0.64, 0.90, 1.24, 1.29, 1.52)
    private val TREE_H = doubleArrayOf(30.0, 22.0, 34.0, 24.0, 42.0, 30.0, 36.0, 26.0, 38.0, 28.0, 32.0)
    private val BIRD_X = doubleArrayOf(0.40, 0.46, 0.35)
    private val BIRD_Y = doubleArrayOf(-205.0, -192.0, -186.0)
    private val BIRD_K = doubleArrayOf(9.0, 10.0, 11.0)
    private val BIRD_PH = doubleArrayOf(0.0, 1.7, 3.1)

    private fun farY(x: Double) = -40 + 10 * sin(2 * PI * 1.3 * x + 0.6) + 6 * sin(2 * PI * 3.1 * x + 1.9)
    fun midY(x: Double) = 10 + 16 * sin(2 * PI * 0.9 * x + 2.2) + 7 * sin(2 * PI * 2.4 * x + 0.3)
    fun nearY(x: Double) = 120 + 34 * sin(2 * PI * 0.6 * x + 4.0) + 10 * sin(2 * PI * 1.7 * x + 1.0)
    fun foreY(x: Double) = 250 - 60 * x + 12 * sin(2 * PI * 1.1 * x)

    private fun ridge(w: Double, vis: Rect, cy: Double, ox: Double, oy: Double, y: (Double) -> Double): Path {
        val p = Path()
        val x0 = floor(vis.left / 8.0) * 8.0
        val x1 = vis.right.toDouble()
        val bottom = vis.bottom + 300f
        p.moveTo(x0.f(), bottom)
        var x = x0
        while (x <= x1 + 0.1) {
            val u = (x - ox) / w
            p.lineTo(x.f(), (cy + y(u) + oy).f())
            x += 8
        }
        p.lineTo(x1.f(), bottom)
        p.close()
        return p
    }

    private fun DrawScope.vertical(path: Path, top: Double, bottom: Double, a: Long, b: Long) {
        drawPath(path, Brush.verticalGradient(listOf(iprHex(a), iprHex(b)), startY = top.f(), endY = bottom.f()))
    }

    /** A soft radial blob squashed vertically (mist, the scrim). */
    private fun DrawScope.squashedGlow(x: Double, y: Double, r: Double, sy: Double, color: Long, alpha: Double) {
        withTransform({
            translate(x.f(), y.f())
            scale(1f, sy.f(), pivot = Offset.Zero)
        }) {
            drawCircle(
                Brush.radialGradient(listOf(iprHex(color, alpha), iprHex(color, 0.0)), center = Offset.Zero, radius = r.f()),
                radius = r.f(),
                center = Offset.Zero,
            )
        }
    }

    fun DrawScope.scene(w: Double, h: Double, cy: Double, open: Double, full: Double, t: Double) {
        val rise = 0.1 * open + 0.9 * full
        val k = 1 - rise
        val side = (1 - open) * 26
        val phase = 2 * PI * t / IprModel.LOOP

        val zp = 0.12 * open + 0.88 * full
        val zoom = IprModel.lerp(ZOOM_INLINE, 1.0, zp)
        val look = -LOOK_UP * (1 - zp)
        val vis = Rect(
            (w / 2 - w / 2 / zoom - 40).f(), (cy + look - cy / zoom - 40).f(),
            (w / 2 + w / 2 / zoom + 40).f(), (cy + look - cy / zoom + h / zoom + 40).f(),
        )
        withTransform({
            translate((w / 2).f(), cy.f())
            scale(zoom.f(), zoom.f(), pivot = Offset.Zero)
            translate((-w / 2).f(), (-(cy + look)).f())
        }) {
            // Sky
            val skyY = k * SKY.d
            drawRect(
                Brush.verticalGradient(
                    0f to iprHex(0x4F6F96),
                    0.45f to iprHex(0x8EA3C0),
                    0.78f to iprHex(0xE4B8A2),
                    0.93f to iprHex(0xF7D3A8),
                    1f to iprHex(0xFBE2BD),
                    startY = skyY.f(),
                    endY = (cy - 30 + skyY).f(),
                ),
                topLeft = vis.topLeft,
                size = vis.size,
            )

            // Sun and its glow
            val sun = Offset((0.70 * w + side * SUN.side).f(), (cy - 175 + k * SUN.d).f())
            val glowR = (150 * (1 + 0.06 * sin(2 * phase))).f()
            drawCircle(
                Brush.radialGradient(
                    0f to iprHex(0xFFE2A8, 0.55), 0.5f to iprHex(0xFFE2A8, 0.18), 1f to iprHex(0xFFE2A8, 0.0),
                    center = sun, radius = glowR,
                ),
                radius = glowR, center = sun,
            )
            drawCircle(iprHex(0xFFF5DE), radius = 24f, center = sun)

            // Birds
            for (i in 0 until 3) {
                val bx = BIRD_X[i] * w + side * BIRDS.side + 8 * sin(phase + i)
                val by = cy + BIRD_Y[i] + k * BIRDS.d + 3 * sin(2 * phase + i * 2.0)
                val wing = sin(BIRD_K[i] * phase + BIRD_PH[i])
                val p = Path().apply {
                    moveTo((bx - 7).f(), (by - 2 - 3 * wing).f())
                    quadraticTo((bx - 3).f(), (by - 4 - 2 * wing).f(), bx.f(), by.f())
                    quadraticTo((bx + 3).f(), (by - 4 - 2 * wing).f(), (bx + 7).f(), (by - 2 - 3 * wing).f())
                }
                drawPath(p, iprHex(0x4E3436, 0.7), style = Stroke(1.4f, cap = StrokeCap.Round, join = StrokeJoin.Round))
            }

            // Far ridge and mist
            val fx = side * FAR.side
            val fy = k * FAR.d
            vertical(ridge(w, vis, cy, fx, fy, ::farY), cy - 52 + fy, cy + 60 + fy, 0xC48F88, 0xA9736D)
            for (i in 0 until 5) {
                val mx = MIST_X[i] * w + fx + 14 * sin(phase + i * 2.1)
                val my = cy - 12 + fy + MIST_Y[i]
                squashedGlow(mx, my, 120.0, 0.14, 0xFFF1E4, 0.30)
            }

            // Mid ridge with cypresses
            val mx = side * MID.side
            val my = k * MID.d
            for (i in TREE_X.indices) {
                val tx = TREE_X[i] * w + mx
                val th = TREE_H[i]
                val base = cy + midY(TREE_X[i]) + my + 5
                val tw = th * 0.24
                val p = Path().apply {
                    moveTo((tx - tw / 2).f(), base.f())
                    quadraticTo((tx - tw * 0.62).f(), (base - th * 0.62).f(), tx.f(), (base - th).f())
                    quadraticTo((tx + tw * 0.62).f(), (base - th * 0.62).f(), (tx + tw / 2).f(), base.f())
                    close()
                }
                drawPath(p, iprHex(0x5A322C))
            }
            vertical(ridge(w, vis, cy, mx, my, ::midY), cy - 10 + my, cy + 120 + my, 0xA35E54, 0x86483F)

            // Near hill with the farmhouse
            val nx = side * NEAR.side
            val ny = k * NEAR.d
            vertical(ridge(w, vis, cy, nx, ny, ::nearY), cy + 80 + ny, cy + 240 + ny, 0x7A4036, 0x5D2E27)
            val hx = 0.30 * w + nx
            val hb = cy + nearY(0.30) + ny + 7
            val win = Offset((hx + 4).f(), (hb - 6).f())
            drawCircle(
                Brush.radialGradient(listOf(iprHex(0xFFD58F, 0.45), iprHex(0xFFD58F, 0.0)), center = win, radius = 22f),
                radius = 22f, center = win,
            )
            drawRect(iprHex(0xE6BFA2), Offset((hx - 11).f(), (hb - 13).f()), Size(22f, 13f))
            val roof = Path().apply {
                moveTo((hx - 14).f(), (hb - 12).f())
                lineTo((hx - 4).f(), (hb - 21).f())
                lineTo((hx + 14).f(), (hb - 21).f())
                lineTo((hx + 15).f(), (hb - 12).f())
                close()
            }
            drawPath(roof, iprHex(0x5A2B24))
            drawRect(iprHex(0xFFD58F), Offset(win.x - 2f, win.y - 2.5f), Size(4f, 5f))

            // Foreground and the road
            val gx = side * FORE.side
            val gy = k * FORE.d
            vertical(ridge(w, vis, cy, gx, gy, ::foreY), cy + 200 + gy, h + gy, 0x4A2622, 0x2F1714)
            drawPath(road(w, cy, gx, gy), iprHex(0xF2C9A4, 0.32))
        }

        // Vignette and a soft scrim behind the second line
        drawRect(
            Brush.radialGradient(
                0f to iprHex(0x000000, 0.0), 0.5f to iprHex(0x000000, 0.0), 1f to iprHex(0x1A0C08, 0.26),
                center = Offset((w / 2).f(), cy.f()), radius = (max(w, h) * 0.62).f(),
            ),
            size = Size(w.f(), h.f()),
        )
        squashedGlow(w / 2, cy, 210.0, 0.45, 0x2A1410, 0.22 * full)
    }

    /** A road winding down the foreground, widening towards the viewer. */
    private fun road(w: Double, cy: Double, ox: Double, oy: Double): Path {
        val x0 = 0.42
        val p0x = x0 * w + ox
        val p0y = cy + foreY(x0) + oy + 3
        val p1x = 0.30 * w + ox
        val p1y = p0y + 80
        val p2x = 0.80 * w + ox
        val p2y = p0y + 150
        val p3x = 0.58 * w + ox
        val p3y = p0y + 260
        val n = 24
        val lx = DoubleArray(n + 1)
        val ly = DoubleArray(n + 1)
        val rx = DoubleArray(n + 1)
        val ry = DoubleArray(n + 1)
        for (i in 0..n) {
            val s = i.toDouble() / n
            val a = (1 - s) * (1 - s) * (1 - s)
            val b = 3 * (1 - s) * (1 - s) * s
            val c = 3 * (1 - s) * s * s
            val d = s * s * s
            val px = a * p0x + b * p1x + c * p2x + d * p3x
            val py = a * p0y + b * p1y + c * p2y + d * p3y
            val da = -3 * (1 - s) * (1 - s)
            val db = 3 * (1 - s) * (1 - s) - 6 * (1 - s) * s
            val dc = 6 * (1 - s) * s - 3 * s * s
            val dd = 3 * s * s
            val tx = da * p0x + db * p1x + dc * p2x + dd * p3x
            val ty = da * p0y + db * p1y + dc * p2y + dd * p3y
            val len = max(0.001, hypot(tx, ty))
            val hw = 0.8 + 13 * s * s
            lx[i] = px - ty / len * hw
            ly[i] = py + tx / len * hw
            rx[i] = px + ty / len * hw
            ry[i] = py - tx / len * hw
        }
        return Path().apply {
            moveTo(lx[0].f(), ly[0].f())
            for (i in 1..n) lineTo(lx[i].f(), ly[i].f())
            for (i in n downTo 0) lineTo(rx[i].f(), ry[i].f())
            close()
        }
    }

    /** A 128 × 128 tile of seeded noise, made once (the same hash as the Swift tile). */
    val grain: ImageBitmap by lazy {
        val n = 128
        val px = IntArray(n * n)
        for (i in 0 until n * n) {
            val v = sin(i * 12.9898 + 78.233) * 43758.5453
            val r = v - floor(v)
            val g = if (r > 0.5) 255 else 0
            val a = (abs(r - 0.5) * 2 * 255).toInt()
            px[i] = (a shl 24) or (g shl 16) or (g shl 8) or g
        }
        Bitmap.createBitmap(px, n, n, Bitmap.Config.ARGB_8888).asImageBitmap()
    }
}

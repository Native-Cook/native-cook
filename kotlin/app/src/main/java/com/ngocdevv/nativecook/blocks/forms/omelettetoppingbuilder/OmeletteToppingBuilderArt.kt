package com.ngocdevv.nativecook.blocks.forms.omelettetoppingbuilder

import android.graphics.BlurMaskFilter
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.nativeCanvas
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

/*
 Omelette Topping Builder — the food, drawn in code (a port of OmeletteToppingBuilderArt.swift, same
 shapes and colours).

 Shelf ingredients are drawn in a unit box (-0.5 … 0.5, y down) that the caller scales: a red bell
 pepper with three lobes and a curled stem, a bunch of four spinach leaves, two wavy bacon strips,
 three cherry tomatoes, a button mushroom. Their light comes from the top left; soft spots are radial
 gradients (no blur), so they scale cleanly.

 Pieces are drawn in their own dp around (0, 0), already rotated by the caller; `lx, ly` is the
 direction of the light in the piece's frame and `sx, sy` the offset of its shadow, so the cubes,
 leaves and strips keep one light whatever their angle.

 The omelette is drawn in its own frame: a half disc of radius R with its fold edge on y = 0 and the
 bulge towards +y. Every draw takes the group's alpha `a`.
*/

internal fun otbHex(hex: Long, alpha: Float = 1f) = Color(
    ((hex shr 16) and 0xFF).toInt() / 255f, ((hex shr 8) and 0xFF).toInt() / 255f, (hex and 0xFF).toInt() / 255f, alpha,
)

/** A gradient stop: colour, its alpha, its location. */
internal class OtbStop(val hex: Long, val alpha: Float, val at: Float)

internal fun st(hex: Long, alpha: Double, at: Double) = OtbStop(hex, alpha.toFloat(), at.toFloat())

internal object OtbArt {
    private fun Double.f() = toFloat()
    private fun o(x: Double, y: Double) = Offset(x.f(), y.f())

    fun circle(x: Double, y: Double, r: Double) = Path().apply { addOval(Rect(o(x, y), r.f())) }

    fun linear(stops: List<OtbStop>, x0: Double, y0: Double, x1: Double, y1: Double) =
        Brush.linearGradient(*stops.map { it.at to otbHex(it.hex, it.alpha) }.toTypedArray(), start = o(x0, y0), end = o(x1, y1))

    fun radial(stops: List<OtbStop>, x: Double, y: Double, r: Double) =
        Brush.radialGradient(*stops.map { it.at to otbHex(it.hex, it.alpha) }.toTypedArray(), center = o(x, y), radius = max(1e-4, r).f())

    fun DrawScope.fill(p: Path, brush: Brush, a: Float) = drawPath(p, brush, alpha = a)
    fun DrawScope.fill(p: Path, hex: Long, alpha: Double, a: Float) = drawPath(p, otbHex(hex, alpha.f()), alpha = a)
    fun DrawScope.line(p: Path, hex: Long, alpha: Double, w: Double, a: Float, round: Boolean = false) =
        drawPath(p, otbHex(hex, alpha.f()), alpha = a, style = Stroke(w.f(), cap = if (round) StrokeCap.Round else StrokeCap.Butt))
    fun DrawScope.line(p: Path, brush: Brush, w: Double, a: Float) = drawPath(p, brush, alpha = a, style = Stroke(w.f()))

    /** Draws [block] moved by (dx, dy). */
    inline fun DrawScope.moved(dx: Double, dy: Double, block: DrawScope.() -> Unit) =
        withTransform({ translate(dx.toFloat(), dy.toFloat()) }, block)

    /** A soft elliptical spot fading from [alpha] at its centre to 0 at its edge. */
    fun DrawScope.blob(x: Double, y: Double, rx: Double, ry: Double, hex: Long, alpha: Double, a: Float) {
        withTransform({
            translate(x.f(), y.f())
            scale(1f, (ry / rx).f(), pivot = Offset.Zero)
        }) {
            drawCircle(radial(listOf(st(hex, alpha, 0.0), st(hex, 0.0, 1.0)), 0.0, 0.0, rx), radius = rx.f(), center = Offset.Zero, alpha = a)
        }
    }

    /** A soft ring shadow under a disc: [alpha] up to radius r0, fading to 0 at r1. */
    fun DrawScope.ringShadow(x: Double, y: Double, r0: Double, r1: Double, alpha: Double, a: Float = 1f) {
        drawCircle(
            radial(listOf(st(0x000000, alpha, 0.0), st(0x000000, alpha, r0 / r1), st(0x000000, 0.0, 1.0)), x, y, r1),
            radius = r1.f(), center = o(x, y), alpha = a,
        )
    }

    /** A blurred fill (Android 9+ blurs it; older versions draw it sharp). */
    fun DrawScope.blurred(p: Path, dx: Double, dy: Double, blur: Double, hex: Long, alpha: Double) {
        val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.argb((alpha * 255).toInt(), ((hex shr 16) and 0xFF).toInt(), ((hex shr 8) and 0xFF).toInt(), (hex and 0xFF).toInt())
            maskFilter = BlurMaskFilter(blur.f(), BlurMaskFilter.Blur.NORMAL)
        }
        drawIntoCanvas {
            val c = it.nativeCanvas
            c.save()
            c.translate(dx.f(), dy.f())
            c.drawPath(p.asAndroidPath(), paint)
            c.restore()
        }
    }

    /** A closed smooth curve through the points (Catmull-Rom as cubic Béziers). */
    fun smoothClosed(p: List<Pair<Double, Double>>): Path {
        val path = Path()
        val n = p.size
        path.moveTo(p[0].first.f(), p[0].second.f())
        for (i in 0 until n) {
            val p0 = p[(i - 1 + n) % n]
            val p1 = p[i]
            val p2 = p[(i + 1) % n]
            val p3 = p[(i + 2) % n]
            path.cubicTo(
                (p1.first + (p2.first - p0.first) / 6).f(), (p1.second + (p2.second - p0.second) / 6).f(),
                (p2.first - (p3.first - p1.first) / 6).f(), (p2.second - (p3.second - p1.second) / 6).f(),
                p2.first.f(), p2.second.f(),
            )
        }
        path.close()
        return path
    }

    /** A band following a wavy centre line y = amp·sin(2πx/period + phase), between offsets a and b. */
    fun band(x0: Double, x1: Double, a: Double, b: Double, amp: Double, period: Double, phase: Double, n: Int): Path {
        val path = Path()
        fun cy(x: Double) = amp * sin(2 * PI * x / period + phase)
        for (i in 0..n) {
            val x = x0 + (x1 - x0) * i / n
            if (i == 0) path.moveTo(x.f(), (cy(x) + a).f()) else path.lineTo(x.f(), (cy(x) + a).f())
        }
        for (i in n downTo 0) {
            val x = x0 + (x1 - x0) * i / n
            path.lineTo(x.f(), (cy(x) + b).f())
        }
        path.close()
        return path
    }

    private fun Path.m(x: Double, y: Double) = moveTo(x.f(), y.f())
    private fun Path.l(x: Double, y: Double) = lineTo(x.f(), y.f())
    private fun Path.c(c1x: Double, c1y: Double, c2x: Double, c2y: Double, x: Double, y: Double) =
        cubicTo(c1x.f(), c1y.f(), c2x.f(), c2y.f(), x.f(), y.f())
    private fun Path.q(cx: Double, cy: Double, x: Double, y: Double) = quadraticTo(cx.f(), cy.f(), x.f(), y.f())

    // Shelf ingredients (unit box)

    fun DrawScope.ingredient(kind: Int, ground: Boolean, a: Float) {
        if (ground) blob(0.02, 0.44, 0.32, 0.065, 0x000000, 0.16, a)
        when (kind) {
            0 -> pepper(a)
            1 -> spinach(a)
            2 -> bacon(a)
            3 -> tomatoes(a)
            else -> mushroom(a)
        }
    }

    private fun DrawScope.pepper(a: Float) {
        val body = Path().apply {
            m(-0.32, -0.16)
            c(-0.32, -0.30, -0.14, -0.33, 0.0, -0.27)
            c(0.14, -0.33, 0.32, -0.30, 0.32, -0.16)
            c(0.36, 0.02, 0.34, 0.22, 0.24, 0.33)
            c(0.20, 0.39, 0.10, 0.41, 0.06, 0.36)
            c(0.03, 0.42, -0.03, 0.42, -0.06, 0.36)
            c(-0.10, 0.41, -0.20, 0.39, -0.24, 0.33)
            c(-0.34, 0.22, -0.36, 0.02, -0.32, -0.16)
            close()
        }
        fill(body, radial(listOf(st(0xFF6A48, 1.0, 0.0), st(0xE42C1F, 1.0, 0.42), st(0xB8171A, 1.0, 0.78), st(0x7E0E12, 1.0, 1.0)), -0.12, -0.12, 0.62), a)
        clipPath(body) {
            blob(-0.085, 0.14, 0.04, 0.24, 0x6E0A0E, 0.42, a)
            blob(0.10, 0.14, 0.04, 0.24, 0x6E0A0E, 0.36, a)
            blob(0.0, -0.25, 0.20, 0.06, 0x5C080B, 0.45, a)
            blob(0.26, 0.10, 0.08, 0.26, 0x5C080B, 0.35, a)
            blob(-0.19, -0.04, 0.05, 0.15, 0xFFFFFF, 0.62, a)
            blob(-0.04, 0.10, 0.025, 0.12, 0xFFFFFF, 0.30, a)
            blob(0.17, -0.10, 0.03, 0.08, 0xFFFFFF, 0.38, a)
        }
        blob(0.0, -0.27, 0.13, 0.05, 0x2B5A1C, 0.9, a)
        val stem = Path().apply {
            m(-0.035, -0.26)
            c(-0.04, -0.35, -0.005, -0.41, 0.05, -0.43)
            l(0.085, -0.40)
            c(0.04, -0.37, 0.035, -0.33, 0.035, -0.26)
            close()
        }
        fill(stem, linear(listOf(st(0x3A6E25, 1.0, 0.0), st(0x74B24A, 1.0, 0.45), st(0x3E7A2A, 1.0, 1.0)), -0.04, 0.0, 0.06, 0.0), a)
        fill(Path().apply { addOval(Rect(0.045f, -0.437f, 0.09f, -0.402f)) }, 0xB5D98A, 1.0, a)
    }

    fun leaf(L: Double, W: Double) = Path().apply {
        m(0.0, 0.0)
        c(W * 0.9, -L * 0.15, W * 1.05, -L * 0.7, 0.0, -L)
        c(-W * 1.05, -L * 0.7, -W * 0.9, -L * 0.15, 0.0, 0.0)
        close()
    }

    private fun DrawScope.spinach(a: Float) {
        val leaves = listOf(Triple(-36.0, 0.46, 0.19), Triple(38.0, 0.45, 0.18), Triple(-12.0, 0.62, 0.23), Triple(15.0, 0.56, 0.21))
        for ((deg, L, W) in leaves) {
            withTransform({
                translate(0f, 0.40f)
                rotate(deg.f(), pivot = Offset.Zero)
            }) {
                val stalk = Path().apply { m(0.0, 0.0); l(0.0, -0.16) }
                line(stalk, 0x8DBA5E, 1.0, 0.03, a, round = true)
                moved(0.0, -0.12) {
                    val blade = leaf(L, W)
                    fill(blade, linear(listOf(st(0x3E8E3B, 1.0, 0.0), st(0x2A7230, 1.0, 0.5), st(0x1C5520, 1.0, 1.0)), 0.0, 0.0, 0.0, -L), a)
                    clipPath(blade) {
                        drawRect(otbHex(0x0A2E0E, 0.18f), Offset(0f, -L.f()), androidx.compose.ui.geometry.Size((W * 1.2).f(), L.f()), alpha = a)
                        blob(-W * 0.38, -L * 0.55, W * 0.24, L * 0.2, 0xFFFFFF, 0.16, a)
                        val vein = Path().apply {
                            m(0.0, -0.02)
                            q(W * 0.08, -L * 0.5, 0.0, -L * 0.92)
                            for (f in listOf(0.28, 0.48, 0.68)) {
                                m(0.0, -L * f); l(W * 0.62, -L * (f + 0.13))
                                m(0.0, -L * f); l(-W * 0.62, -L * (f + 0.13))
                            }
                        }
                        line(vein, 0x9CCB6E, 0.55, 0.011, a)
                    }
                    line(blade, 0x123E16, 0.35, 0.01, a)
                }
            }
        }
    }

    private fun DrawScope.baconStrip(len: Double, w: Double, phase: Double, a: Float) {
        val amp = 0.032
        val period = 0.34
        val h = len / 2
        val meat = band(-h, h, -w / 2, w / 2, amp, period, phase, 24)
        fill(meat, linear(listOf(st(0xC84E3C, 1.0, 0.0), st(0xA2352A, 1.0, 0.5), st(0x7A2119, 1.0, 1.0)), 0.0, -w / 2, 0.0, w / 2), a)
        clipPath(meat) {
            fill(band(-h, h, -w * 0.12, w * 0.12, amp, period, phase, 24),
                linear(listOf(st(0xF8E2CF, 1.0, 0.0), st(0xEBC3A6, 1.0, 1.0)), 0.0, -w * 0.12, 0.0, w * 0.12), a)
            fill(band(-h, h, w * 0.30, w * 0.38, amp, period, phase, 24), 0xF1D0BA, 0.8, a)
            fill(band(-h, h, -w * 0.42, -w * 0.36, amp, period, phase, 24), 0xF1D0BA, 0.45, a)
            blob(-h * 0.55, 0.0, 0.07, 0.05, 0x4A120C, 0.35, a)
            blob(h * 0.45, w * 0.2, 0.08, 0.05, 0x4A120C, 0.3, a)
            fill(band(-h, h, -w / 2, -w * 0.42, amp, period, phase, 24), 0xFFFFFF, 0.22, a)
        }
        line(meat, 0x5E1A12, 0.45, 0.012, a)
    }

    private fun DrawScope.bacon(a: Float) {
        withTransform({ translate(-0.08f, 0f); rotate(-66f, pivot = Offset.Zero) }) { baconStrip(0.84, 0.17, 0.4, a) }
        withTransform({ translate(0.09f, 0.03f); rotate(-58f, pivot = Offset.Zero) }) { baconStrip(0.80, 0.16, 2.2, a) }
    }

    private fun DrawScope.tomato(x: Double, y: Double, r: Double, a: Float) {
        fill(circle(x, y, r), radial(listOf(st(0xFF8A68, 1.0, 0.0), st(0xEA3B2B, 1.0, 0.38), st(0xB91D17, 1.0, 0.82), st(0x86110F, 1.0, 1.0)),
            x - 0.35 * r, y - 0.38 * r, 1.35 * r), a)
        blob(x - 0.34 * r, y - 0.38 * r, 0.24 * r, 0.15 * r, 0xFFFFFF, 0.8, a)
        blob(x + 0.42 * r, y + 0.30 * r, 0.12 * r, 0.22 * r, 0xFF9A7A, 0.35, a)
    }

    private fun DrawScope.tomatoes(a: Float) {
        tomato(0.0, -0.11, 0.20, a)
        tomato(-0.17, 0.12, 0.19, a)
        tomato(0.18, 0.13, 0.18, a)
        val star = Path()
        for (i in 0 until 10) {
            val ang = i * PI / 5 - PI / 2
            val r = if (i % 2 == 0) 0.085 else 0.028
            val x = r * cos(ang)
            val y = -0.27 + r * sin(ang) * 0.7
            if (i == 0) star.m(x, y) else star.l(x, y)
        }
        star.close()
        fill(star, 0x3E7B2B, 1.0, a)
        val stem = Path().apply { m(0.0, -0.27); q(0.0, -0.33, 0.04, -0.36) }
        line(stem, 0x4C8A33, 1.0, 0.022, a, round = true)
        blob(-0.17, -0.05, 0.03, 0.02, 0x5E2A10, 0.5, a)
        blob(0.18, -0.03, 0.03, 0.02, 0x5E2A10, 0.5, a)
    }

    private fun DrawScope.mushroom(a: Float) {
        val stem = Path().apply {
            m(-0.12, 0.02)
            c(-0.13, 0.18, -0.16, 0.30, -0.12, 0.36)
            c(-0.04, 0.40, 0.04, 0.40, 0.12, 0.36)
            c(0.16, 0.30, 0.13, 0.18, 0.12, 0.02)
            close()
        }
        fill(stem, linear(listOf(st(0xCDBDA8, 1.0, 0.0), st(0xF7F0E7, 1.0, 0.45), st(0xD4C4AF, 1.0, 1.0)), -0.13, 0.0, 0.13, 0.0), a)
        blob(0.0, 0.36, 0.11, 0.03, 0xA88C6E, 0.4, a)
        fill(Path().apply { addOval(Rect(-0.34f, -0.03f, 0.34f, 0.10f)) },
            linear(listOf(st(0x6E543F, 1.0, 0.0), st(0x9C8066, 1.0, 1.0)), 0.0, -0.03, 0.0, 0.10), a)
        val gills = Path()
        for (i in 0 until 9) {
            val x = -0.28 + 0.07 * i
            gills.m(x, 0.035)
            gills.l(x * 0.35, 0.03)
        }
        line(gills, 0x4E3A2A, 0.45, 0.008, a)
        val cap = Path().apply {
            m(-0.39, 0.03)
            c(-0.40, -0.34, 0.40, -0.34, 0.39, 0.03)
            c(0.30, 0.07, -0.30, 0.07, -0.39, 0.03)
            close()
        }
        fill(cap, radial(listOf(st(0xFBF5EE, 1.0, 0.0), st(0xE8DACA, 1.0, 0.5), st(0xC4AA8B, 1.0, 0.9), st(0xA98D6D, 1.0, 1.0)), -0.10, -0.18, 0.52), a)
        clipPath(cap) {
            blob(-0.15, -0.16, 0.10, 0.05, 0xFFFFFF, 0.7, a)
            for ((x, y, r) in listOf(Triple(0.10, -0.15, 0.018), Triple(0.20, -0.06, 0.014), Triple(-0.02, -0.20, 0.012), Triple(0.24, -0.14, 0.01))) {
                blob(x, y, r, r, 0x8A6B4E, 0.45, a)
            }
            moved(0.0, -0.01) { line(cap, 0x8E7356, 0.25, 0.012, a) }
        }
    }

    // Pieces (dp)

    private val pieceLeafPath = Path().apply {
        m(-7.0, 0.0)
        c(-3.0, -4.6, 3.0, -4.8, 7.0, 0.0)
        c(3.0, 4.3, -3.0, 4.5, -7.0, 0.0)
        close()
    }

    private val pepperCube = Path().apply { addRoundRect(RoundRect(Rect(-4.6f, -4.1f, 4.6f, 4.1f), CornerRadius(2.3f))) }

    private val mushroomSlice = Path().apply {
        m(-6.0, 0.5)
        c(-6.2, -5.6, 6.2, -5.6, 6.0, 0.5)
        l(1.9, 0.9)
        l(2.2, 4.6)
        q(0.0, 5.6, -2.2, 4.6)
        l(-1.9, 0.9)
        close()
    }

    /** One piece of kind k at the origin, with the light along (lx, ly) and its shadow at (sx, sy). */
    fun DrawScope.piece(k: Int, p: OtbPiece, lx: Double, ly: Double, sx: Double, sy: Double, shadow: Double, a: Float) {
        when (k) {
            0 -> {
                moved(sx, sy) { fill(pepperCube, 0x8A3A00, 0.30 * shadow, a) }
                fill(pepperCube, 0xA3141A, 1.0, a)
                val x0 = -4.1 + lx * 0.9
                val y0 = -3.6 + ly * 0.9
                fill(Path().apply { addRoundRect(RoundRect(Rect(x0.f(), y0.f(), (x0 + 8.0).f(), (y0 + 7.0).f()), CornerRadius(2f))) }, 0xE3321F, 1.0, a)
                blob(lx * 2.2, ly * 2.2, 2.2, 1.4, 0xFFFFFF, 0.55, a)
            }
            1 -> {
                moved(sx, sy) { fill(pieceLeafPath, 0x6A5000, 0.26 * shadow, a) }
                fill(pieceLeafPath, 0x2B732D, 1.0, a)
                clipPath(pieceLeafPath) {
                    blob(lx * 1.6, ly * 1.6, 4.6, 2.2, 0x6DB24E, 0.65, a)
                    val vein = Path().apply { m(-6.2, 0.0); q(0.0, 0.8, 6.4, 0.0) }
                    line(vein, 0x9CCB6E, 0.75, 0.7, a)
                }
                line(pieceLeafPath, 0x174A1A, 0.45, 0.6, a)
            }
            2 -> {
                val meat = band(-11.0, 11.0, -3.0, 3.0, 1.1, 11.0, p.phase, 10)
                moved(sx, sy) { fill(meat, 0x6A3000, 0.28 * shadow, a) }
                fill(meat, 0xA8372B, 1.0, a)
                clipPath(meat) {
                    fill(band(-11.0, 11.0, -0.9, 0.7, 1.1, 11.0, p.phase, 10), 0xF3D3BC, 1.0, a)
                    fill(band(-11.0, 11.0, 1.7, 2.2, 1.1, 11.0, p.phase, 10), 0xEFC9AE, 0.8, a)
                    blob(lx * 2, ly * 2, 6.0, 2.0, 0xFFFFFF, 0.22, a)
                }
                line(meat, 0x5A170F, 0.5, 0.7, a)
            }
            3 -> {
                fill(circle(sx, sy, 5.2), 0x8A3A00, 0.28 * shadow, a)
                fill(circle(0.0, 0.0, 5.2), 0xC4261C, 1.0, a)
                fill(circle(0.0, 0.0, 4.2), 0xE8452F, 1.0, a)
                for (i in 0 until 3) {
                    val ang = i * 2 * PI / 3 + 0.4
                    blob(2.3 * cos(ang), 2.3 * sin(ang), 1.5, 1.5, 0xF9C066, 0.95, a)
                }
                fill(circle(0.0, 0.0, 1.1), 0xF26A4A, 1.0, a)
                blob(lx * 2.6, ly * 2.6, 1.6, 1.0, 0xFFFFFF, 0.6, a)
            }
            else -> {
                moved(sx, sy) { fill(mushroomSlice, 0x6A4000, 0.26 * shadow, a) }
                fill(mushroomSlice, 0xEDE3D4, 1.0, a)
                clipPath(mushroomSlice) {
                    val gill = Path().apply { m(-5.2, 0.2); q(0.0, -2.4, 5.2, 0.2) }
                    line(gill, 0xA58665, 0.6, 1.1, a)
                    blob(lx * 2, ly * 2, 3.0, 1.6, 0xFFFFFF, 0.6, a)
                }
                line(mushroomSlice, 0x96765A, 0.85, 0.8, a)
            }
        }
    }

    // Omelette (its own frame)

    val omeletteOutline: List<Pair<Double, Double>> = buildList {
        val r0 = OtbMotion.R
        for (i in 0 until 9) {
            val x = -r0 * 0.96 + r0 * 1.92 * i / 8
            add(x to -3.5 * (1 - (x / r0) * (x / r0)))
        }
        for (i in 0..20) {
            val th = PI * (0.025 + 0.95 * i / 20)
            val r = r0 * (1 + 0.012 * sin(3 * th + 0.5) + 0.008 * sin(7 * th + 1.3))
            add(r * cos(th) to r * sin(th))
        }
    }

    val omelettePath: Path = smoothClosed(omeletteOutline)

    private class Speckle(val x: Double, val y: Double, val r: Double, val k: Int)

    /** Seeded speckles of the omelette's surface (0 light, 1 golden, 2 brown). */
    private val speckles: List<Speckle> = (0 until 72).map { i ->
        val th = PI * (0.03 + 0.94 * OtbMotion.hash(70000 + i * 4))
        val rho = (OtbMotion.R - 6) * sqrt(OtbMotion.hash(70001 + i * 4))
        val k = (OtbMotion.hash(70002 + i * 4) * 3).toInt()
        val r = if (k == 2) 1.0 + 0.8 * OtbMotion.hash(70003 + i * 4) else 2.2 + 3.6 * OtbMotion.hash(70003 + i * 4)
        Speckle(cos(th) * rho, max(3.0, sin(th) * rho), r, k)
    }

    fun DrawScope.omelette(shadow: Pair<Double, Double>, contact: Pair<Double, Double>) {
        val r0 = OtbMotion.R
        val path = omelettePath
        blurred(path, shadow.first, shadow.second, 13.0, 0x4A3200, 0.20)
        blurred(path, contact.first, contact.second, 3.0, 0x3A2600, 0.22)
        fill(path, radial(listOf(st(0xFFDF5C, 1.0, 0.0), st(0xF9CA36, 1.0, 0.5), st(0xF0B328, 1.0, 0.82), st(0xE09A1A, 1.0, 1.0)), 0.0, r0 * 0.3, r0 * 1.05), 1f)
        clipPath(path) {
            blob(-34.0, 44.0, 36.0, 20.0, 0xFFF2B8, 0.42, 1f)
            blob(34.0, 66.0, 26.0, 14.0, 0xFFF2B8, 0.36, 1f)
            blob(-66.0, 22.0, 20.0, 11.0, 0xFFF2B8, 0.32, 1f)
            blob(8.0, 92.0, 22.0, 9.0, 0xD8901C, 0.28, 1f)
            for (s in speckles) {
                when (s.k) {
                    0 -> blob(s.x, s.y, s.r, s.r * 0.8, 0xFFF4C2, 0.6, 1f)
                    1 -> blob(s.x, s.y, s.r, s.r * 0.75, 0xE0A038, 0.42, 1f)
                    else -> fill(circle(s.x, s.y, s.r), 0xB97A1C, 0.32, 1f)
                }
            }
            fill(circle(0.0, 0.0, r0 * 1.03), radial(listOf(st(0xC97E12, 0.0, 0.0), st(0xC97E12, 0.0, 0.78), st(0xB86E0E, 0.5, 1.0)), 0.0, 0.0, r0 * 1.03), 1f)
            drawRect(
                linear(listOf(st(0xFFF3B0, 0.85, 0.0), st(0xFFE58A, 0.0, 1.0)), 0.0, -4.0, 0.0, 26.0),
                Offset(-r0.f(), -8f), androidx.compose.ui.geometry.Size((2 * r0).f(), 34f),
            )
            val o = omeletteOutline
            val fold = Path().apply {
                m(o[0].first, o[0].second)
                for (i in 1 until 9) l(o[i].first, o[i].second)
            }
            moved(0.0, 1.2) { line(fold, 0xFFFFFF, 0.45, 1.4, 1f) }
        }
    }

    // Plate (dp, centred at x, y)

    fun DrawScope.plate(x: Double, y: Double) {
        ringShadow(x, y + 8, 160.0, 188.0, 0.07)
        fill(circle(x, y, 168.0), radial(listOf(st(0xF8F8F7, 1.0, 0.0), st(0xEFEFED, 1.0, 1.0)), x - 40, y - 50, 230.0), 1f)
        line(circle(x, y, 167.5), linear(listOf(st(0xFFFFFF, 0.9, 0.0), st(0xFFFFFF, 0.0, 0.6)), x - 120, y - 120, x + 60, y + 60), 1.0, 1f)
        ringShadow(x, y + 12, 136.0, 178.0, 0.12)
        ringShadow(x, y + 2, 142.0, 150.0, 0.16)
        fill(circle(x, y, 144.0), linear(listOf(st(0xFFFFFF, 1.0, 0.0), st(0xF6F4F1, 1.0, 0.5), st(0xE4E0DA, 1.0, 1.0)), x - 100, y - 100, x + 100, y + 100), 1f)
        line(circle(x, y, 143.3), linear(listOf(st(0xFFFFFF, 1.0, 0.0), st(0xFFFFFF, 0.0, 0.55)), x - 100, y - 100, x + 60, y + 60), 1.2, 1f)
        fill(circle(x, y, 112.0), radial(listOf(st(0xFDFCFA, 1.0, 0.0), st(0xF2EFEA, 1.0, 1.0)), x - 20, y - 24, 130.0), 1f)
        fill(circle(x, y, 112.0), radial(listOf(st(0x000000, 0.0, 0.0), st(0x000000, 0.0, 0.9), st(0x5A4A30, 0.08, 1.0)), x, y, 112.0), 1f)
        line(circle(x, y, 112.6), linear(listOf(st(0xFFFFFF, 0.0, 0.4), st(0xFFFFFF, 0.95, 1.0)), x - 80, y - 80, x + 80, y + 80), 1.2, 1f)
    }
}

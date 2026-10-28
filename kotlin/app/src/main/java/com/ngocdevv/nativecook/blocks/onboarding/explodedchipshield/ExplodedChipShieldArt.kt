package com.ngocdevv.nativecook.blocks.onboarding.explodedchipshield

import android.graphics.BlurMaskFilter
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.drawText
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/*
 Draws the six isometric layers of the chip, its shadow, glow and callouts (a port of
 ExplodedChipShieldArt.swift). Every shape is built flat in the layer's plane (centred on 0) and
 projected: turn by the yaw, squash y by 0.58, lift by the layer's height. Side faces are the convex
 hull of the top and bottom outlines. Units are dp.
*/

internal fun ecsColor(hex: Int, a: Double = 1.0): Color =
    Color(0xFF000000.toInt() or hex).copy(alpha = a.coerceIn(0.0, 1.0).toFloat())

private fun Double.f() = toFloat()

internal class EcsProj(val cx: Double, val cy: Double, val yaw: Double, val scale: Double = 1.0) {
    private val c = cos(yaw) * scale
    private val s = sin(yaw) * scale

    fun point(x: Double, y: Double, z: Double) =
        Offset((cx + x * c - y * s).f(), (cy + EcsModel.SQUASH * (x * s + y * c) - z * scale).f())

    fun point(p: Offset, z: Double) = point(p.x.toDouble(), p.y.toDouble(), z)

    /** The plane → screen map at height z; `turn` rotates the drawing inside the plane first. */
    fun matrix(z: Double, turn: Double = 0.0): Matrix {
        val a = yaw + turn
        val cc = cos(a) * scale
        val ss = sin(a) * scale
        val m = Matrix()
        m.values[Matrix.ScaleX] = cc.f()
        m.values[Matrix.SkewY] = (EcsModel.SQUASH * ss).f()
        m.values[Matrix.SkewX] = (-ss).f()
        m.values[Matrix.ScaleY] = (EcsModel.SQUASH * cc).f()
        m.values[Matrix.TranslateX] = cx.f()
        m.values[Matrix.TranslateY] = (cy - z * scale).f()
        return m
    }

    /** Screen-space y of a plane normal: > 0 when that side faces the viewer. */
    fun facing(n: Offset) = sin(yaw) * n.x + cos(yaw) * n.y > 0.05
}

internal class EcsOutline(val points: List<Offset>, val normals: List<Offset>)

internal object EcsArt {
    private val m = EcsModel

    // Outlines (plane)

    fun roundedSquare(side: Double, radius: Double, perCorner: Int = 9): EcsOutline {
        val h = side / 2
        val r = radius
        val centres = listOf(Offset((h - r).f(), (-h + r).f()), Offset((h - r).f(), (h - r).f()),
            Offset((-h + r).f(), (h - r).f()), Offset((-h + r).f(), (-h + r).f()))
        val pts = ArrayList<Offset>()
        val nrm = ArrayList<Offset>()
        for ((k, c) in centres.withIndex()) {
            val a0 = -PI / 2 + k * PI / 2
            for (j in 0..perCorner) {
                val a = a0 + j.toDouble() / perCorner * PI / 2
                val d = Offset(cos(a).f(), sin(a).f())
                nrm.add(d)
                pts.add(Offset(c.x + r.f() * d.x, c.y + r.f() * d.y))
            }
        }
        return EcsOutline(pts, nrm)
    }

    fun circle(d: Double, n: Int = 48): EcsOutline {
        val pts = ArrayList<Offset>()
        val nrm = ArrayList<Offset>()
        for (j in 0 until n) {
            val a = j.toDouble() / n * 2 * PI
            val v = Offset(cos(a).f(), sin(a).f())
            nrm.add(v)
            pts.add(Offset(v.x * (d / 2).f(), v.y * (d / 2).f()))
        }
        return EcsOutline(pts, nrm)
    }

    /** Convex hull (monotone chain). */
    fun hull(input: List<Offset>): List<Offset> {
        val p = input.sortedWith(compareBy({ it.x }, { it.y }))
        if (p.size <= 2) return p
        fun cross(o: Offset, a: Offset, b: Offset) = (a.x - o.x) * (b.y - o.y) - (a.y - o.y) * (b.x - o.x)
        val lower = ArrayList<Offset>()
        val upper = ArrayList<Offset>()
        for (q in p) {
            while (lower.size >= 2 && cross(lower[lower.size - 2], lower[lower.size - 1], q) <= 0) lower.removeAt(lower.size - 1)
            lower.add(q)
        }
        for (q in p.asReversed()) {
            while (upper.size >= 2 && cross(upper[upper.size - 2], upper[upper.size - 1], q) <= 0) upper.removeAt(upper.size - 1)
            upper.add(q)
        }
        return lower.dropLast(1) + upper.dropLast(1)
    }

    fun poly(pts: List<Offset>): Path = Path().apply {
        moveTo(pts[0].x, pts[0].y)
        for (i in 1 until pts.size) lineTo(pts[i].x, pts[i].y)
        close()
    }

    fun bounds(pts: List<Offset>): Rect {
        var l = Float.MAX_VALUE
        var t = Float.MAX_VALUE
        var r = -Float.MAX_VALUE
        var b = -Float.MAX_VALUE
        for (q in pts) {
            l = min(l, q.x); t = min(t, q.y); r = max(r, q.x); b = max(b, q.y)
        }
        return Rect(l, t, r, b)
    }

    class Stop(val at: Double, val hex: Int, val a: Double = 1.0)

    private fun stops(s: List<Stop>) = s.map { it.at.f() to ecsColor(it.hex, it.a) }.toTypedArray()

    fun across(s: List<Stop>, r: Rect) =
        Brush.linearGradient(*stops(s), start = Offset(r.left, r.center.y), end = Offset(max(r.right, r.left + 0.1f), r.center.y))

    fun diagonal(s: List<Stop>, r: Rect) =
        Brush.linearGradient(*stops(s), start = Offset(r.left, r.top), end = Offset(max(r.right, r.left + 0.1f), max(r.bottom, r.top + 0.1f)))

    /** Where the light band sits along a face's diagonal; it travels with the view angle. */
    fun specular(yaw: Double) = 0.42 + (yaw - m.REST_YAW) / (10 * PI / 180) * 0.32

    fun fract(x: Double) = x - floor(x)

    private fun transformed(src: Path, mx: Matrix): Path = Path().apply {
        addPath(src)
        transform(mx)
    }

    private fun oval(x: Double, y: Double, w: Double, h: Double) = Path().apply {
        addOval(Rect(x.f(), y.f(), (x + w).f(), (y + h).f()))
    }

    // Materials

    val chromeSide = listOf(Stop(0.0, 0x868C94), Stop(0.22, 0xEEF0F2), Stop(0.48, 0x9AA0A8), Stop(0.70, 0xF5F6F7), Stop(1.0, 0x6A7078))
    val darkChromeSide = listOf(Stop(0.0, 0x1E2227), Stop(0.30, 0x9097A0), Stop(0.55, 0x2A2E34), Stop(0.80, 0xC8CDD2), Stop(1.0, 0x2B3036))
    val lidTop = listOf(Stop(0.0, 0xF7F8F9), Stop(0.55, 0xD9DDE1), Stop(1.0, 0xBCC1C8))
    val baseTop = listOf(Stop(0.0, 0xD6DADF), Stop(0.5, 0xA9AFB7), Stop(1.0, 0xC9CDD3))
    val trayTop = listOf(Stop(0.0, 0x3A3F46), Stop(0.30, 0xE3E6EA), Stop(0.55, 0x4E545C), Stop(0.82, 0xD0D4D9), Stop(1.0, 0x30353B))

    // Plane outlines never change: build them once.
    private val outlines = m.layers.map { roundedSquare(it.side * m.UNIT, it.radius * m.UNIT) }
    private val trayInner = roundedSquare(m.TRAY_HOLE * m.UNIT, 0.11 * m.UNIT)
    private val coreInset = roundedSquare(m.layers[2].side * m.UNIT * 0.86, m.layers[2].radius * m.UNIT * 0.7)
    private val disc = circle(m.DISC_SIDE * m.UNIT)
    private val hole = circle(m.HOLE_SIDE * m.UNIT)
    private val glassInner = roundedSquare(m.layers[4].side * m.UNIT - 3, m.layers[4].radius * m.UNIT - 1.5)
    private val lidFrame = roundedSquare(0.70 * m.UNIT, 0.15 * m.UNIT)
    private val foot = roundedSquare(m.UNIT, 0.22 * m.UNIT)
    private val halos = mapOf(1.14 to roundedSquare(1.14 * m.UNIT, 0.30 * m.UNIT), 1.20 to roundedSquare(1.20 * m.UNIT, 0.30 * m.UNIT))
    private val knurl: List<Pair<Offset, Offset>> = denseOutline(roundedSquare(m.UNIT, 0.22 * m.UNIT, 14), 2.4)

    private val frostDots: Path = Path().apply {
        val l = m.layers[3]
        val n = 22
        val half = l.side * m.UNIT / 2 - 8
        for (i in 0..n) for (j in 0..n) {
            val x = -half + 2 * half * i / n
            val y = -half + 2 * half * j / n
            val r = m.HOLE_SIDE * m.UNIT / 2 + 7
            if (x * x + y * y < r * r) continue
            val h = fract(sin((i * 31 + j * 17) * 12.9898) * 43758.5453)
            if (h > 0.42) continue
            addOval(Rect((x - 0.9).f(), (y - 0.9).f(), (x + 0.9).f(), (y + 0.9).f()))
        }
    }

    private val glassA = m.layers[4].side * m.UNIT / 2
    private val routes = listOf(
        listOf(-0.80 to -0.55, -0.35 to -0.55, -0.20 to -0.35),
        listOf(0.80 to -0.30, 0.45 to -0.30, 0.30 to -0.15),
        listOf(-0.55 to 0.80, -0.55 to 0.40, -0.30 to 0.22),
        listOf(0.30 to 0.80, 0.30 to 0.45, 0.12 to 0.30),
        listOf(-0.80 to 0.10, -0.42 to 0.10),
        listOf(0.62 to 0.62, 0.42 to 0.42),
    )
    private val traces: Path = Path().apply {
        for (r in routes) {
            moveTo((r[0].first * glassA).f(), (r[0].second * glassA).f())
            for (k in 1 until r.size) lineTo((r[k].first * glassA).f(), (r[k].second * glassA).f())
        }
    }
    private val pads: Path = Path().apply {
        for (r in routes) for (q in listOf(r.first(), r.last())) {
            addOval(Rect((q.first * glassA - 2.2).f(), (q.second * glassA - 2.2).f(), (q.first * glassA + 2.2).f(), (q.second * glassA + 2.2).f()))
        }
    }
    private val glassDots: Path = Path().apply {
        val a = glassA
        val n = 18
        for (i in 0..n) for (j in 0..n) {
            val x = -a + 10 + (2 * a - 20) * i / n
            val y = -a + 10 + (2 * a - 20) * j / n
            val h = fract(sin((i * 13 + j * 57) * 78.233) * 43758.5453)
            if (h > 0.34 || x * x + y * y < (a * 0.34) * (a * 0.34)) continue
            addOval(Rect((x - 0.8).f(), (y - 0.8).f(), (x + 0.8).f(), (y + 0.8).f()))
        }
    }
    private val meshRing = oval(-glassA * 0.28, -glassA * 0.28, glassA * 0.56, glassA * 0.56)
    private val discRings = Path().apply {
        val d = m.DISC_SIDE * m.UNIT
        for (r in listOf(0.82, 0.64, 0.46)) addOval(Rect((-d * r / 2).f(), (-d * r / 2).f(), (d * r / 2).f(), (d * r / 2).f()))
    }
    private val sw = 0.40 * m.UNIT
    private val sh = 0.47 * m.UNIT
    private val lidShield = shield(sw, sh)
    private val lidKeyhole = keyhole(sw, sh)
    private val lidHatch = Path().apply {
        var v = -sw
        while (v < sw) {
            moveTo(v.f(), (-sh / 2).f())
            lineTo((v + sh).f(), (sh / 2).f())
            v += 2.6
        }
    }
    private val discKeyhole = keyhole(m.DISC_SIDE * m.UNIT * 0.34, m.DISC_SIDE * m.UNIT * 0.40)

    /** Outline points resampled about every `every` dp along the perimeter, with their normals. */
    fun denseOutline(o: EcsOutline, every: Double): List<Pair<Offset, Offset>> {
        val out = ArrayList<Pair<Offset, Offset>>()
        val n = o.points.size
        for (k in 0 until n) {
            val a = o.points[k]
            val b = o.points[(k + 1) % n]
            val na = o.normals[k]
            val nb = o.normals[(k + 1) % n]
            val len = hypot((b.x - a.x).toDouble(), (b.y - a.y).toDouble())
            val cnt = max(1, (len / every).toInt())
            for (j in 0 until cnt) {
                val t = j.toFloat() / cnt
                out.add(Offset(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t) to Offset(na.x + (nb.x - na.x) * t, na.y + (nb.y - na.y) * t))
            }
        }
        return out
    }

    /** The shield of the mark, centred on 0, its point towards +y. */
    fun shield(w: Double, h: Double): Path = Path().apply {
        val c = w * 0.16
        moveTo((-w / 2).f(), (-h / 2 + c).f())
        quadraticTo((-w / 2).f(), (-h / 2).f(), (-w / 2 + c).f(), (-h / 2).f())
        lineTo((w / 2 - c).f(), (-h / 2).f())
        quadraticTo((w / 2).f(), (-h / 2).f(), (w / 2).f(), (-h / 2 + c).f())
        lineTo((w / 2).f(), (-h * 0.02).f())
        cubicTo((w / 2).f(), (h * 0.26).f(), (w * 0.22).f(), (h * 0.42).f(), 0f, (h / 2).f())
        cubicTo((-w * 0.22).f(), (h * 0.42).f(), (-w / 2).f(), (h * 0.26).f(), (-w / 2).f(), (-h * 0.02).f())
        close()
    }

    /** The keyhole inside the shield (relative to the shield's size). */
    fun keyhole(w: Double, h: Double): Path = Path().apply {
        val r = w * 0.15
        val cy = -h * 0.10
        addOval(Rect((-r).f(), (cy - r).f(), r.f(), (cy + r).f()))
        moveTo((-w * 0.06).f(), (cy + r * 0.4).f())
        lineTo((w * 0.06).f(), (cy + r * 0.4).f())
        lineTo((w * 0.10).f(), (h * 0.22).f())
        lineTo((-w * 0.10).f(), (h * 0.22).f())
        close()
    }

    // Drawing helpers

    private val blurPaint = android.graphics.Paint()

    /** A soft (blurred) fill. Android's blur radius is 1.5 × SwiftUI's. */
    private fun DrawScope.blurred(path: Path, color: Color, radius: Double, dx: Double = 0.0, dy: Double = 0.0) {
        blurPaint.reset()
        blurPaint.isAntiAlias = true
        blurPaint.color = color.toArgb()
        blurPaint.maskFilter = BlurMaskFilter((radius * 1.5).f(), BlurMaskFilter.Blur.NORMAL)
        drawIntoCanvas {
            val c = it.nativeCanvas
            c.save()
            c.translate(dx.f(), dy.f())
            c.drawPath(path.asAndroidPath(), blurPaint)
            c.restore()
        }
    }

    private fun DrawScope.band(clip: Path, box: Rect, u: Double, alpha: Double, width: Double = 0.14) {
        val list = listOf(
            Stop(0.0, 0xFFFFFF, 0.0), Stop(max(0.0, u - width), 0xFFFFFF, 0.0), Stop(min(max(u, 0.0), 1.0), 0xFFFFFF, alpha),
            Stop(min(1.0, u + width), 0xFFFFFF, 0.0), Stop(1.0, 0xFFFFFF, 0.0),
        ).sortedBy { it.at }
        clipPath(clip) { drawPath(clip, diagonal(list, box)) }
    }

    private class Slab(val top: Path, val box: Rect)

    /** Fills the side faces (hull) and returns the top face for the caller to finish. */
    private fun DrawScope.slab(pr: EcsProj, o: EcsOutline, z: Double, thick: Double, side: List<Stop>, edge: Double = 0.14): Slab {
        val top = o.points.map { pr.point(it, z + thick) }
        val bottom = o.points.map { pr.point(it, z) }
        val h = hull(top + bottom)
        val hp = poly(h)
        drawPath(hp, across(side, bounds(h)))
        if (edge > 0) drawPath(hp, ecsColor(0x1A1D21, edge), style = Stroke(0.6f))
        return Slab(poly(top), bounds(top))
    }

    /** Fine brushed lines across a face, clipped to it. */
    private fun DrawScope.brushed(pr: EcsProj, side: Double, z: Double, clip: Path, light: Double, dark: Double) {
        val lightP = Path()
        val darkP = Path()
        val n = 34
        val h = side / 2
        for (j in 1 until n) {
            val y = -h + side * j / n
            val hsh = fract(sin(j * 12.9898) * 43758.5453)
            val a = pr.point(-h, y, z)
            val b = pr.point(h, y, z)
            val p = if (hsh < 0.55) lightP else darkP
            p.moveTo(a.x, a.y)
            p.lineTo(b.x, b.y)
        }
        clipPath(clip) {
            drawPath(lightP, ecsColor(0xFFFFFF, light), style = Stroke(0.7f))
            drawPath(darkP, ecsColor(0x2A2F35, dark), style = Stroke(0.6f))
        }
    }

    // Layers

    private fun DrawScope.base(pr: EcsProj, f: EcsFrame) {
        val l = m.layers[0]
        val s = slab(pr, outlines[0], 0.0, l.thick, darkChromeSide)
        drawPath(s.top, diagonal(baseTop, s.box))
        brushed(pr, l.side * m.UNIT, l.thick, s.top, 0.30, 0.07)
        band(s.top, s.box, specular(f.yaw) + 0.1, 0.35, 0.18)
        drawPath(s.top, ecsColor(0xFFFFFF, 0.55), style = Stroke(0.8f))
    }

    private fun DrawScope.tray(pr: EcsProj, z: Double) {
        val l = m.layers[1]
        val s = slab(pr, outlines[1], z, l.thick, darkChromeSide)
        val innerTop = poly(trayInner.points.map { pr.point(it, z + l.thick) })
        val innerTopPts = trayInner.points.map { pr.point(it, z + l.thick) }
        val innerBottom = poly(trayInner.points.map { pr.point(it, z) })
        val ring = Path().apply {
            addPath(s.top)
            addPath(innerTop)
            fillType = PathFillType.EvenOdd
        }
        drawPath(ring, across(trayTop, s.box))
        // The far inner wall, seen through the hole.
        val wall = Path().apply {
            addPath(innerTop)
            addPath(innerBottom)
            fillType = PathFillType.EvenOdd
        }
        clipPath(innerTop) {
            drawPath(wall, across(listOf(Stop(0.0, 0x5A6068), Stop(0.5, 0x1F2328), Stop(1.0, 0x8D939B)), bounds(innerTopPts)))
        }
        drawPath(s.top, ecsColor(0xFFFFFF, 0.7), style = Stroke(0.8f))
        drawPath(innerTop, ecsColor(0xFFFFFF, 0.5), style = Stroke(0.7f))
    }

    private fun DrawScope.core(pr: EcsProj, z: Double, f: EcsFrame) {
        val l = m.layers[2]
        val o = outlines[2]
        // Teal glow under the glass.
        val glow = poly(o.points.map { pr.point(it.x * 1.05, it.y * 1.05, z) })
        if (f.open > 0.005) blurred(glow, ecsColor(m.GLOW, 0.55 * f.open), 16.0)

        val s = slab(pr, o, z, l.thick, listOf(Stop(0.0, 0x1C8577), Stop(0.45, 0x5BD0BE), Stop(1.0, 0x1F8C7D)), 0.1)
        drawPath(s.top, diagonal(listOf(Stop(0.0, 0x9BEBDD), Stop(0.5, 0x5CC9B8), Stop(1.0, 0x2FA594)), s.box))
        // Inner bevel and a fine grid.
        val inset = poly(coreInset.points.map { pr.point(it, z + l.thick) })
        drawPath(inset, ecsColor(0xFFFFFF, 0.35), style = Stroke(0.8f))
        val grid = Path()
        val h = l.side * m.UNIT * 0.43
        for (j in 1 until 8) {
            val v = -h + 2 * h * j / 8
            val a = pr.point(v, -h, z + l.thick)
            val b = pr.point(v, h, z + l.thick)
            val c = pr.point(-h, v, z + l.thick)
            val d = pr.point(h, v, z + l.thick)
            grid.moveTo(a.x, a.y); grid.lineTo(b.x, b.y)
            grid.moveTo(c.x, c.y); grid.lineTo(d.x, d.y)
        }
        clipPath(inset) { drawPath(grid, ecsColor(0xFFFFFF, 0.16), style = Stroke(0.6f)) }
        drawPath(s.top, ecsColor(0xD9FFF8, 0.9), style = Stroke(0.8f))

        // The raised metal disc.
        val dz = z + l.thick
        val ds = slab(pr, disc, dz, m.DISC_THICK, chromeSide, 0.18)
        val centre = pr.point(0.0, 0.0, dz + m.DISC_THICK)
        drawPath(
            ds.top,
            Brush.radialGradient(
                0f to ecsColor(0xFBFCFC), 0.7f to ecsColor(0xD5D9DE), 1f to ecsColor(0xAFB5BC),
                center = Offset(centre.x - 8, centre.y - 4), radius = (m.DISC_SIDE * m.UNIT * 0.6).f(),
            ),
        )
        drawPath(transformed(discRings, pr.matrix(dz + m.DISC_THICK)), ecsColor(0x5A6068, 0.16), style = Stroke(0.7f))
        band(ds.top, ds.box, specular(f.yaw), 0.5, 0.16)
        drawPath(transformed(discKeyhole, pr.matrix(dz + m.DISC_THICK, -PI / 2)), ecsColor(0x3B4148, 0.8))
        drawPath(ds.top, ecsColor(0xFFFFFF, 0.8), style = Stroke(0.8f))
    }

    private fun DrawScope.frosted(pr: EcsProj, z: Double, f: EcsFrame) {
        val l = m.layers[3]
        val s = slab(pr, outlines[3], z, l.thick, listOf(Stop(0.0, 0xE4EFED, 0.80), Stop(0.5, 0xFFFFFF, 0.92), Stop(1.0, 0xCFE2DE, 0.80)), 0.06)
        val holeTop = poly(hole.points.map { pr.point(it, z + l.thick) })
        val holeBottom = poly(hole.points.map { pr.point(it, z) })
        val face = Path().apply {
            addPath(s.top)
            addPath(holeTop)
            fillType = PathFillType.EvenOdd
        }
        drawPath(face, diagonal(listOf(Stop(0.0, 0xFFFFFF, 0.90), Stop(0.6, 0xF1F7F6, 0.82), Stop(1.0, 0xDDEBE8, 0.80)), s.box))
        // Frosted dots, as in a print on the glass.
        clipPath(s.top) { drawPath(transformed(frostDots, pr.matrix(z + l.thick)), ecsColor(0x8FA6A2, 0.5)) }
        // The hole's far wall and its teal rim.
        val wall = Path().apply {
            addPath(holeTop)
            addPath(holeBottom)
            fillType = PathFillType.EvenOdd
        }
        clipPath(holeTop) { drawPath(wall, ecsColor(0xD7EEEA, 0.75)) }
        drawPath(holeTop, ecsColor(0x4FC7B5, 0.35 + 0.45 * f.open), style = Stroke(1.1f))
        drawPath(s.top, ecsColor(0xFFFFFF, 1.0), style = Stroke(1.2f))
        band(s.top, s.box, specular(f.yaw) - 0.05, 0.45, 0.12)
    }

    private fun DrawScope.glass(pr: EcsProj, z: Double, f: EcsFrame) {
        val l = m.layers[4]
        val s = slab(pr, outlines[4], z, l.thick, listOf(Stop(0.0, 0xA9E3DA, 0.55), Stop(0.5, 0xF2FCFA, 0.75), Stop(1.0, 0x9CDDD2, 0.55)), 0.0)
        drawPath(s.top, diagonal(listOf(Stop(0.0, 0xFFFFFF, 0.42), Stop(0.6, 0xF2FAF9, 0.22), Stop(1.0, 0xDDF3EF, 0.20)), s.box))
        val tz = z + l.thick
        val mx = pr.matrix(tz)
        // Circuit: traces with round pads, and a field of dots.
        drawPath(transformed(glassDots, mx), ecsColor(0x7D9894, 0.55))
        drawPath(transformed(traces, mx), ecsColor(0x3FB5A3, 0.75), style = Stroke(1f))
        drawPath(transformed(pads, mx), ecsColor(0x3FB5A3, 0.85))
        // Centre ring of the mesh.
        drawPath(
            transformed(meshRing, mx), ecsColor(0x3FB5A3, 0.6),
            style = Stroke(1f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(2f, 2.5f))),
        )
        drawPath(s.top, ecsColor(0x5CC6B5, 0.9), style = Stroke(1f))
        drawPath(poly(glassInner.points.map { pr.point(it, tz) }), ecsColor(0xFFFFFF, 0.9), style = Stroke(0.8f))
        band(s.top, s.box, specular(f.yaw) + 0.05, 0.35, 0.10)
    }

    private fun DrawScope.lid(pr: EcsProj, z: Double, f: EcsFrame) {
        val l = m.layers[5]
        val s = slab(pr, outlines[5], z, l.thick, chromeSide)
        // Knurled edge: fine ticks on the sides that face the viewer.
        val ticks = Path()
        for ((p, n) in knurl) {
            if (!pr.facing(n)) continue
            val a = pr.point(p, z + 1.5)
            val b = pr.point(p, z + l.thick - 1.5)
            ticks.moveTo(a.x, a.y)
            ticks.lineTo(b.x, b.y)
        }
        drawPath(ticks, ecsColor(0x3A4047, 0.28), style = Stroke(0.6f))

        val tz = z + l.thick
        drawPath(s.top, diagonal(lidTop, s.box))
        brushed(pr, l.side * m.UNIT, tz, s.top, 0.32, 0.04)
        band(s.top, s.box, specular(f.yaw), 0.6, 0.16)

        // Engraved inner frame.
        val frame = poly(lidFrame.points.map { pr.point(it, tz) })
        translate(0f, -0.6f) { drawPath(frame, ecsColor(0x5A6068, 0.30), style = Stroke(1.1f)) }
        translate(0f, 0.7f) { drawPath(frame, ecsColor(0xFFFFFF, 0.95), style = Stroke(1f)) }

        // Raised shield with a keyhole; the mark's "up" runs along the lid's edge.
        val mt = pr.matrix(tz + 1.5, -PI / 2)
        val sp = transformed(lidShield, mt)
        blurred(sp, ecsColor(0x2A2F35, 0.30), 1.6, 0.6, 2.4)
        val sb = sp.getBounds()
        drawPath(sp, diagonal(listOf(Stop(0.0, 0xFDFDFE), Stop(0.5, 0xDCE0E4), Stop(1.0, 0xB4BAC1)), sb))
        clipPath(sp) {
            drawPath(transformed(lidHatch, mt), ecsColor(0x4A5057, 0.10), style = Stroke(0.6f))
            band(sp, sb, specular(f.yaw) + 0.08, 0.55, 0.12)
        }
        drawPath(sp, ecsColor(0xFFFFFF, 0.95), style = Stroke(0.9f))
        clipPath(sp) {
            translate(0f, 1f) { drawPath(sp, ecsColor(0x5A6068, 0.35), style = Stroke(1f)) }
        }
        val kh = transformed(lidKeyhole, mt)
        drawPath(kh, diagonal(listOf(Stop(0.0, 0x6A7078), Stop(1.0, 0x3A3F46)), kh.getBounds()))
        translate(0f, 0.7f) { drawPath(kh, ecsColor(0xFFFFFF, 0.8), style = Stroke(0.7f)) }
        drawPath(s.top, ecsColor(0xFFFFFF, 0.9), style = Stroke(0.9f))
    }

    /** Thin glass halo around a layer. */
    private fun DrawScope.halo(pr: EcsProj, side: Double, z: Double, alpha: Double) {
        if (alpha <= 0.005) return
        val o = halos.getValue(side)
        drawPath(poly(o.points.map { pr.point(it, z) }), ecsColor(0x6FD3C3, alpha), style = Stroke(1f))
    }

    // Stack

    class Geo(val w: Double, val h: Double, val top: Double, val textTop: Double)

    fun proj(g: Geo, f: EcsFrame) = EcsProj(
        cx = g.w / 2 - m.SHIFT * f.open,
        cy = g.textTop - m.CLOSED_LIFT + m.OPEN_DROP * f.open + f.dip,
        yaw = f.yaw,
        scale = 1 + m.CLOSED_ZOOM * (1 - min(f.open, 1.0)),
    )

    fun DrawScope.background(g: Geo, f: EcsFrame) {
        drawRect(
            Brush.verticalGradient(listOf(ecsColor(m.BG_TOP), ecsColor(m.BG_BOTTOM)), 0f, g.h.f()),
            size = androidx.compose.ui.geometry.Size(g.w.f(), g.h.f()),
        )
        if (f.open > 0.01) {
            val p = proj(g, f).point(0.0, 0.0, m.z(2, f))
            drawRect(
                Brush.radialGradient(listOf(ecsColor(m.GLOW, 0.20 * f.open), ecsColor(m.GLOW, 0.0)), center = p, radius = 170f),
                size = androidx.compose.ui.geometry.Size(g.w.f(), g.h.f()),
            )
        }
    }

    fun DrawScope.stack(g: Geo, f: EcsFrame) {
        val pr = proj(g, f)
        // Contact and ambient shadows under the base.
        val footPath = poly(foot.points.map { pr.point(it, -1.0) })
        blurred(footPath, ecsColor(0x1A2026, 0.13), 18.0, dy = 16.0)
        blurred(footPath, ecsColor(0x1A2026, 0.22), 4.0, dy = 4.0)

        base(pr, f)
        halo(pr, 1.14, m.z(1, f) + 3, 0.45 * f.open)
        tray(pr, m.z(1, f))
        core(pr, m.z(2, f), f)
        frosted(pr, m.z(3, f), f)
        halo(pr, 1.20, m.z(4, f) + 1, 0.5 * f.open)
        glass(pr, m.z(4, f), f)
        lid(pr, m.z(5, f), f)
    }

    /** Plane point the callout lines start from, on each layer's top face. */
    private val anchor = Offset((0.26 * m.UNIT).f(), (-0.26 * m.UNIT).f())

    fun DrawScope.callouts(g: Geo, f: EcsFrame, titles: List<TextLayoutResult>, captions: List<TextLayoutResult>) {
        var widest = 0f
        for (t in titles + captions) widest = max(widest, t.size.width.toFloat())
        val lx = min(g.w / 2 - m.SHIFT + 0.707 * m.UNIT + 16, g.w - 18 - widest)
        val pr = proj(g, f)
        for (k in m.callouts.indices) {
            val v = f.callout[k]
            if (v <= 0.002) continue
            val i = m.callouts[k].layer
            val p = pr.point(anchor, m.z(i, f) + m.layers[i].thick)
            val endX = lx - 8
            val x1 = p.x + (endX - p.x) * v
            drawLine(ecsColor(m.LINE, 0.95), p, Offset(x1.f(), p.y), 1f)
            val r = 3.2f * min(1.0, v * 1.6).f()
            drawCircle(ecsColor(0xFFFFFF), r, p)
            drawCircle(ecsColor(m.TEAL, min(1.0, v * 1.6)), r, p, style = Stroke(1.4f))
            val a = m.easeOut(m.seg(v, 0.35, 1.0)).f()
            val dx = 10 * (1 - v)
            val t = titles[k]
            val c = captions[k]
            drawText(t, topLeft = Offset((lx + dx).f(), p.y - 2 - t.size.height), alpha = a)
            drawText(c, topLeft = Offset((lx + dx).f(), p.y + 2), alpha = a)
        }
    }
}

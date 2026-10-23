package com.ngocdevv.nativecook.blocks.playground.pluckstringharp

import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.os.Build
import kotlin.math.PI
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

// Palette: a dark room, warm varnished wood, gilt, and strings that glow like filaments.
internal val Room = intArrayOf(0xFF22170F.toInt(), 0xFF0E0A07.toInt(), 0xFF050403.toInt())
internal const val WOOD = 0xFF5B3419.toInt()
private const val WOOD_LIGHT = 0xFFC98F55.toInt()
private const val WOOD_DARK = 0xFF1D0E05.toInt()
private const val GRAIN = 0xFF2A1407.toInt()
private const val GILT = 0xFFD6AD66.toInt()
internal const val STRING_IDLE = 0xFFA8804F.toInt()
internal const val STRING_HOT = 0xFFFFF4DF.toInt()
internal const val GLOW = 0xFFFFB257.toInt()
internal const val GLOW_CORE = 0xFFFFD49A.toInt()
internal const val NOTE_INK = 0xFFFFE8C4.toInt()
internal const val LABEL = 0xFFB08A5A.toInt()

// Strings span this share of the width, the lowest on the left.
private const val STRING_LEFT = 0.27
private const val STRING_RIGHT = 0.8

internal class Pt(val x: Double, val y: Double)

/** A band of wood: its centre line, a width at each point, and the unit normal there. */
internal class Ribbon(val centre: List<Pt>, val width: DoubleArray, val normal: List<Pt>, val path: Path)

/** Where everything is, in dp. */
internal class HarpLayout(
    val width: Double,
    val height: Double,
    val geometry: HarpGeometry,
    val centreX: Double,
    val centreY: Double,
    val top: Double,
    val bottom: Double,
    val span: Double,
    val neck: Ribbon,
    val box: Ribbon,
    val pillar: Ribbon,
)

private fun cubic(p0: Pt, p1: Pt, p2: Pt, p3: Pt, t: Double): Pt {
    val u = 1 - t
    return Pt(
        u * u * u * p0.x + 3 * u * u * t * p1.x + 3 * u * t * t * p2.x + t * t * t * p3.x,
        u * u * u * p0.y + 3 * u * u * t * p1.y + 3 * u * t * t * p2.y + t * t * t * p3.y,
    )
}

private fun offset(p: Pt, n: Pt, d: Double) = Pt(p.x + n.x * d, p.y + n.y * d)

/** A band along [centre], `width(t)` wide, with normals turned towards [up] (−1 up, +1 down). */
private fun ribbon(centre: List<Pt>, up: Double, width: (Double) -> Double): Ribbon {
    val n = centre.size
    val normal = ArrayList<Pt>(n)
    val widths = DoubleArray(n)
    for (i in 0 until n) {
        val a = centre[max(0, i - 1)]
        val b = centre[min(n - 1, i + 1)]
        val dx = b.x - a.x
        val dy = b.y - a.y
        val h = hypot(dx, dy)
        val len = if (h == 0.0) 1.0 else h
        var nx = -dy / len
        var ny = dx / len
        if (ny * up < 0 || (ny == 0.0 && nx * up < 0)) {
            nx = -nx
            ny = -ny
        }
        normal.add(Pt(nx, ny))
        widths[i] = width(i.toDouble() / (n - 1))
    }
    val path = Path()
    for (i in 0 until n) {
        val p = offset(centre[i], normal[i], widths[i] / 2)
        if (i == 0) path.moveTo(p.x.toFloat(), p.y.toFloat()) else path.lineTo(p.x.toFloat(), p.y.toFloat())
    }
    for (i in n - 1 downTo 0) {
        val p = offset(centre[i], normal[i], -widths[i] / 2)
        path.lineTo(p.x.toFloat(), p.y.toFloat())
    }
    path.close()
    return Ribbon(centre, widths, normal, path)
}

/** JavaScript's Math.round: halves go up. */
private fun jsRound(x: Double) = floor(x + 0.5).toInt()

/** A line along the band at [f] (−1 one edge … 1 the other), wobbling by [wave] of the width. */
private fun alongRibbon(r: Ribbon, f: Double, wave: Double = 0.0, phase: Double = 0.0, from: Double = 0.0, to: Double = 1.0): Path {
    val path = Path()
    val n = r.centre.size
    val first = jsRound(from * (n - 1))
    val last = jsRound(to * (n - 1))
    for (i in first..last) {
        val t = i.toDouble() / (n - 1)
        val g = f + wave * sin(t * 9 + phase) + wave * 0.6 * sin(t * 23 + phase * 2.3)
        val p = offset(r.centre[i], r.normal[i], (r.width[i] / 2) * g)
        if (i == first) path.moveTo(p.x.toFloat(), p.y.toFloat()) else path.lineTo(p.x.toFloat(), p.y.toFloat())
    }
    return path
}

/** y of a polyline at [x] (the polyline runs left to right). */
private fun yAt(points: List<Pt>, x: Double): Double {
    for (i in 0 until points.size - 1) {
        val a = points[i]
        val b = points[i + 1]
        if ((x >= a.x && x <= b.x) || (x <= a.x && x >= b.x)) {
            val t = if (b.x == a.x) 0.0 else (x - a.x) / (b.x - a.x)
            return a.y + (b.y - a.y) * t
        }
    }
    return points.last().y
}

/** Lays out the harp for a screen of [w] × [height] dp with the given safe-area insets. */
internal fun layoutHarp(w: Double, height: Double, insetTop: Double, insetBottom: Double): HarpLayout {
    val top = insetTop + 62
    val bottom = height - insetBottom - 64
    val span = bottom - top

    // The neck: a swan curve from the top of the pillar down to the shoulder.
    val n0 = Pt(w * 0.115, top + span * 0.08)
    val n1 = Pt(w * 0.4, top - span * 0.07)
    val n2 = Pt(w * 0.62, top + span * 0.33)
    val n3 = Pt(w * 0.925, top + span * 0.35)
    val neckLine = (0..80).map { cubic(n0, n1, n2, n3, it / 80.0) }
    val neck = ribbon(neckLine, 1.0) { t -> 38 - 14 * t + 7 * sin(t * PI) }

    // The soundbox: a tapered box rising from the foot to the shoulder.
    val b0 = Pt(w * 0.185, bottom - span * 0.035)
    // The top of the box tucks under the end of the neck, so the two read as one joint.
    val b1 = Pt(n3.x - 6, n3.y + 8)
    val boxLine = (0..40).map { i ->
        val t = i / 40.0
        // A slight belly, so the box is not a ruler.
        val bow = sin(t * PI) * span * 0.018
        Pt(b0.x + (b1.x - b0.x) * t + bow * 0.7, b0.y + (b1.y - b0.y) * t + bow)
    }
    val box = ribbon(boxLine, -1.0) { t -> 74 - 46 * t }

    // The pillar: a column from the foot up to the neck, swelling at the base and the crown.
    val p0 = Pt(w * 0.13, bottom + 4)
    val p1 = Pt(w * 0.11, top + span * 0.09)
    val pillarLine = (0..40).map { i ->
        val t = i / 40.0
        Pt(p0.x + (p1.x - p0.x) * t, p0.y + (p1.y - p0.y) * t)
    }
    val pillar = ribbon(pillarLine, 1.0) { t -> 22 + 14 * (1 - t).pow(6) + 8 * t.pow(8) }

    // The strings hang from the underside of the neck to the face of the soundbox.
    val neckUnder = neck.centre.indices.map { offset(neck.centre[it], neck.normal[it], neck.width[it] * 0.32) }
    val boxFace = box.centre.indices.map { offset(box.centre[it], box.normal[it], box.width[it] * 0.34) }
    val xs = DoubleArray(STRINGS) { w * (STRING_LEFT + ((STRING_RIGHT - STRING_LEFT) * it) / (STRINGS - 1)) }
    val tops = DoubleArray(STRINGS) { yAt(neckUnder, xs[it]) }
    val bottoms = DoubleArray(STRINGS) { yAt(boxFace, xs[it]) }
    return HarpLayout(
        width = w,
        height = height,
        geometry = HarpGeometry(xs, tops, bottoms),
        centreX = (xs[0] + xs[STRINGS - 1]) / 2,
        centreY = (tops[0] + bottoms[0]) / 2,
        top = top,
        bottom = bottom,
        span = span,
        neck = neck,
        box = box,
        pillar = pillar,
    )
}

/**
 * Paints for a canvas drawn in dp. Blurs are Skia sigmas in dp, turned into the radius Android
 * takes; hardware canvases scale mask filters with the canvas, so the radius is given in dp too.
 */
internal class HarpPaints(private val density: Float) {
    private val blurs = HashMap<Float, BlurMaskFilter>()

    fun blur(sigma: Float): BlurMaskFilter? {
        // Hardware canvases only honour mask filters from API 28.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return null
        return blurs.getOrPut(sigma) {
            val radiusPx = ((sigma * density - 0.5f) / 0.57735f).coerceAtLeast(0.01f)
            BlurMaskFilter(radiusPx / density, BlurMaskFilter.Blur.NORMAL)
        }
    }

    val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    fun fill(color: Int, alpha: Float = 1f, blur: Float = 0f): Paint = fill.apply {
        shader = null
        this.color = color
        this.alpha = (alpha * 255f + 0.5f).toInt().coerceIn(0, 255)
        maskFilter = if (blur > 0f) blur(blur) else null
    }

    fun stroke(color: Int, width: Float, alpha: Float = 1f, blur: Float = 0f): Paint = stroke.apply {
        shader = null
        this.color = color
        this.alpha = (alpha * 255f + 0.5f).toInt().coerceIn(0, 255)
        strokeWidth = width
        maskFilter = if (blur > 0f) blur(blur) else null
    }

    /** A gradient-filled paint. The paint's own colour stays opaque so the shader is not faded. */
    fun shaded(shader: Shader, alpha: Float = 1f): Paint = fill.apply {
        color = 0xFF000000.toInt()
        this.alpha = (alpha * 255f + 0.5f).toInt().coerceIn(0, 255)
        maskFilter = null
        this.shader = shader
    }
}

private fun radial(cx: Double, cy: Double, r: Double, colors: IntArray, stops: FloatArray?) =
    RadialGradient(cx.toFloat(), cy.toFloat(), r.toFloat(), colors, stops, Shader.TileMode.CLAMP)

/** One piece of the frame: varnished wood with a lit edge, a shaded edge and grain that follows it. */
private fun drawWood(c: Canvas, p: HarpPaints, r: Ribbon, seed: Double) {
    c.drawPath(r.path, p.fill(WOOD))
    c.save()
    c.clipPath(r.path)
    // Shade on the far side, light on the near side: the piece reads as round.
    c.drawPath(alongRibbon(r, 0.85), p.stroke(WOOD_DARK, 16f, 0.85f, 9f))
    c.drawPath(alongRibbon(r, -0.55), p.stroke(WOOD_LIGHT, 9f, 0.42f, 7f))
    // Grain: thin lines that drift across the band like the figure of the wood.
    for (k in 0 until 9) {
        val f = -0.85 + (k / 8.0) * 1.7
        c.drawPath(alongRibbon(r, f, 0.05, seed + k * 1.7), p.stroke(GRAIN, 0.8f, 0.32f))
    }
    for (k in 0 until 4) {
        val f = -0.6 + k * 0.32
        c.drawPath(alongRibbon(r, f, 0.07, seed * 2 + k), p.stroke(WOOD_LIGHT, 0.6f, 0.12f))
    }
    // The varnish: a narrow specular line along the lit side.
    c.drawPath(alongRibbon(r, -0.42), p.stroke(0xFFFFE2B8.toInt(), 1.3f, 0.38f, 1f))
    c.restore()
    c.drawPath(r.path, p.stroke(WOOD_DARK, 1f, 0.7f))
}

/** A round carved boss with gilt rings, like the scroll at the top of a harp's column. */
private fun drawScroll(c: Canvas, p: HarpPaints, at: Pt, radius: Double) {
    val x = at.x.toFloat()
    val y = at.y.toFloat()
    val r = radius.toFloat()
    val light = radial(at.x - radius * 0.32, at.y - radius * 0.36, radius * 1.25, intArrayOf(0xFFCF9A60.toInt(), WOOD, WOOD_DARK), floatArrayOf(0f, 0.5f, 1f))
    c.drawCircle(x, y, r, p.shaded(light))
    c.drawCircle(x, y, r, p.stroke(WOOD_DARK, 1f, 0.7f))
    c.drawCircle(x, y, r * 0.66f, p.stroke(GILT, 1.4f, 0.9f))
    c.drawCircle(x, y, r * 0.36f, p.stroke(GILT, 1.1f, 0.7f))
    drawPin(c, p, at.x, at.y, max(2.4, radius * 0.17))
}

/** A brass tuning pin or bridge pin, lit from the top left. */
private fun drawPin(c: Canvas, p: HarpPaints, x: Double, y: Double, radius: Double) {
    c.drawCircle((x + 1.2).toFloat(), (y + 2).toFloat(), (radius + 0.6).toFloat(), p.fill(0xFF000000.toInt(), 0.55f, 2f))
    val brass = radial(x - radius * 0.35, y - radius * 0.4, radius * 1.4, intArrayOf(0xFFFFF3CF.toInt(), 0xFFD9A957.toInt(), 0xFF5E3D14.toInt()), floatArrayOf(0f, 0.45f, 1f))
    c.drawCircle(x.toFloat(), y.toFloat(), radius.toFloat(), p.shaded(brass))
}

/** Paints everything that never moves: the room, the frame's shadow, the wood, the pins. Canvas in dp. */
internal fun drawHarpFrame(c: Canvas, p: HarpPaints, l: HarpLayout) {
    val w = l.width.toFloat()
    val h = l.height.toFloat()
    val top = l.top
    val bottom = l.bottom.toFloat()
    val span = l.span
    val room = radial(l.width * 0.52, top + span * 0.42, max(l.width, l.height) * 0.78, Room, floatArrayOf(0f, 0.5f, 1f))
    c.drawRect(0f, 0f, w, h, p.shaded(room))

    // One soft shadow for the whole frame, cast down and to the right.
    c.save()
    c.translate(10f, 18f)
    c.save()
    c.clipRect(RectF(-20f, -20f, w + 20f, bottom - 40f))
    c.drawPath(l.box.path, p.fill(0xFF000000.toInt(), 0.7f, 16f))
    c.restore()
    c.drawPath(l.pillar.path, p.fill(0xFF000000.toInt(), 0.7f, 16f))
    c.drawPath(l.neck.path, p.fill(0xFF000000.toInt(), 0.7f, 16f))
    c.restore()

    // The box is cut flat where it stands on the plinth.
    c.save()
    c.clipRect(0f, 0f, w, bottom - 2f)
    drawWood(c, p, l.box, 1.3)
    // Gilt along the soundboard, where the strings land.
    c.drawPath(alongRibbon(l.box, 0.62, from = 0.04, to = 0.97), p.stroke(GILT, 1.3f, 0.85f))
    c.drawPath(alongRibbon(l.box, -0.7, from = 0.05, to = 0.95), p.stroke(GILT, 0.8f, 0.35f))
    c.restore()
    drawWood(c, p, l.pillar, 4.1)
    // Gilt rings on the column.
    for (t in doubleArrayOf(0.07, 0.12, 0.9)) {
        val i = jsRound(t * (l.pillar.centre.size - 1))
        val at = l.pillar.centre[i]
        val half = l.pillar.width[i] / 2 + 0.5
        c.drawLine((at.x - half).toFloat(), at.y.toFloat(), (at.x + half).toFloat(), at.y.toFloat(), p.stroke(GILT, 2f, 0.9f))
    }
    drawWood(c, p, l.neck, 7.7)
    c.drawPath(alongRibbon(l.neck, -0.1, from = 0.06, to = 0.96), p.stroke(GILT, 1.1f, 0.8f))

    // Carved scrolls where the neck meets the column and the shoulder.
    drawScroll(c, p, l.neck.centre.first(), 22.0)
    drawScroll(c, p, l.neck.centre.last(), 14.0)

    // The plinth the column and the box stand on.
    val foot = LinearGradient(0f, bottom - 4f, 0f, bottom + 16f, intArrayOf(0xFF9A6434.toInt(), WOOD, WOOD_DARK), floatArrayOf(0f, 0.45f, 1f), Shader.TileMode.CLAMP)
    c.drawRoundRect(w * 0.045f, bottom - 4f, w * 0.345f, bottom + 16f, 6f, 6f, p.shaded(foot))
    c.drawLine(w * 0.045f + 6f, bottom - 2.5f, w * 0.345f - 6f, bottom - 2.5f, p.stroke(GILT, 1.2f, 0.8f))

    val g = l.geometry
    for (i in 0 until STRINGS) {
        drawPin(c, p, g.x[i], g.top[i] - 1, 3.6)
        // The eyelet in the soundboard.
        val x = g.x[i].toFloat()
        val y = (g.bottom[i] + 1).toFloat()
        c.drawCircle(x, y, 3.4f, p.fill(0xFF120904.toInt()))
        c.drawCircle(x, y, 3.4f, p.stroke(GILT, 1f, 0.75f))
    }
}

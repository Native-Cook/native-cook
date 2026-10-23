package com.ngocdevv.nativecook.blocks.inputs.inksignaturepad

import android.graphics.BlendMode
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import android.os.Build
import com.ngocdevv.nativecook.shared.sigmaToRadius
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

// Ink is kept as one flat list of samples, STRIDE numbers each: x, y (dp in the pad), the time it was
// laid down (ms on the pad's clock), the nib's width there (dp) and the stroke it belongs to.
internal const val STRIDE = 5

// The nib: slow ink is wide, fast ink thin. width = MIN + (MAX − MIN)·e^(−speed / FALLOFF), speed in
// dp/s, and each sample moves EASE of the way from the last width to that, so it swells and thins
// smoothly instead of jumping with every jitter of the finger.
private const val NIB_MAX = 7.0
private const val NIB_MIN = 1.1
private const val NIB_FALLOFF = 500.0
private const val NIB_EASE = 0.3
// The width a stroke lands with, as a share of NIB_MAX: the pen pools a little where it touches.
private const val NIB_LANDING = 0.82
// Samples closer than this to the last one (dp) are dropped.
private const val MIN_STEP = 0.8
// Each span between two samples is drawn through as a Catmull-Rom curve in pieces about SUBSTEP dp
// long, at most MAX_PIECES of them.
private const val SUBSTEP = 2.0
private const val MAX_PIECES = 8

// Wet ink is a brighter, glossier blue that darkens to blue-black over DRY_MS, and carries a thin
// highlight on its upper left that fades as it dries. Wetness is drawn in WET_LEVELS steps.
internal const val DRY_MS = 1100.0
private const val WET_LEVELS = 8
private val INK_WET = floatArrayOf(0.16f, 0.25f, 0.6f)
private val INK_DRY = floatArrayOf(0.1f, 0.13f, 0.32f)
private val SHEEN = floatArrayOf(0.72f, 0.8f, 1f)
private const val SHEEN_MIX = 0.62f
private const val SHEEN_WIDTH = 0.3
// While a signature replays, the old ink shows as a pale ghost under the new.
private const val GHOST_ALPHA = 0.16f

// The wiper Clear sweeps across: a walnut block faced with felt on its leading edge, as tall as the
// pad less WIPER_INSET at each end, with a brass knob to hold it by. It travels from off the left
// edge to off the right edge.
private const val WIPER_WIDTH = 46f
private const val WIPER_INSET = 22f
private const val FELT = 12f
private const val KNOB = 11f
private const val WIPER_LEAN = 7f
// Ink dust brushed off by the felt: CRUMBS specks, each living CRUMB_LIFE of the sweep.
private const val CRUMBS = 34
private const val CRUMB_LIFE = 0.32f

// The fountain pen's nib, leaning NIB_ANGLE°.
private const val NIB_ANGLE = 38f

private fun clamp01(v: Double) = min(1.0, max(0.0, v))

private fun nibWidth(speed: Double) = NIB_MIN + (NIB_MAX - NIB_MIN) * exp(-speed / NIB_FALLOFF)

internal fun catmull(p0: Double, p1: Double, p2: Double, p3: Double, u: Double) =
    0.5 * (2 * p1 + (-p0 + p2) * u + (2 * p0 - 5 * p1 + 4 * p2 - p3) * u * u + (-p0 + 3 * p1 - 3 * p2 + p3) * u * u * u)

/** A growable run of numbers: the ink's samples, or the pieces cut from them. */
internal class Ink(capacity: Int = 1024) {
    private var data = DoubleArray(capacity)
    var size = 0
        private set

    operator fun get(i: Int) = data[i]
    val isEmpty get() = size == 0

    fun push(vararg values: Double) {
        if (size + values.size > data.size) data = data.copyOf(max(data.size * 2, size + values.size))
        values.copyInto(data, size)
        size += values.size
    }

    fun clear() {
        size = 0
    }

    fun copy() = Ink(max(size, 16)).also { out ->
        data.copyInto(out.data, 0, 0, size)
        out.size = size
    }
}

/**
 * Appends a sample to [ink] and says whether it was kept. [speed] is the finger's speed in dp/s;
 * leave it below 0 to take it from the last sample's place and time.
 */
internal fun addSample(ink: Ink, x: Double, y: Double, t: Double, stroke: Double, starts: Boolean, speed: Double): Boolean {
    val n = ink.size
    if (starts || n < STRIDE || ink[n - 1] != stroke) {
        ink.push(x, y, t, NIB_MAX * NIB_LANDING, stroke)
        return true
    }
    val d = hypot(x - ink[n - 5], y - ink[n - 4])
    if (d < MIN_STEP) return false
    val v = if (speed >= 0) speed else d / max((t - ink[n - 3]) / 1000, 1.0 / 240)
    val last = ink[n - 2]
    ink.push(x, y, t, last + (nibWidth(v) - last) * NIB_EASE, stroke)
    return true
}

/**
 * A copy of [ink] for replaying from [start] (ms): the same samples, the same rhythm, but with the
 * pauses between strokes cut to [maxGap] ms.
 */
internal fun retime(ink: Ink, start: Double, maxGap: Double): Ink {
    val out = Ink(max(ink.size, 16))
    var shift = start - ink[2]
    var i = 0
    while (i < ink.size) {
        if (i > 0 && ink[i + 4] != ink[i - 1]) {
            val gap = ink[i + 2] - ink[i - 3]
            if (gap > maxGap) shift -= gap - maxGap
        }
        out.push(ink[i], ink[i + 1], ink[i + 2] + shift, ink[i + 3], ink[i + 4])
        i += STRIDE
    }
    return out
}

internal class Pen(val x: Double, val y: Double, val lift: Double)

/**
 * Where the pen is at [time] along [ink], given that the first [cursor] samples are down: on the last
 * of them, or, between two strokes, gliding from the end of one to the start of the next and lifted
 * (lift 0…1) on the way.
 */
internal fun penAt(ink: Ink, cursor: Int, time: Double): Pen {
    if (cursor <= 0) return Pen(if (ink.size > 0) ink[0] else 0.0, if (ink.size > 1) ink[1] else 0.0, 1.0)
    val last = (cursor - 1) * STRIDE
    val next = cursor * STRIDE
    if (next >= ink.size || ink[next + 4] == ink[last + 4]) return Pen(ink[last], ink[last + 1], 0.0)
    val f = clamp01((time - ink[last + 2]) / max(1.0, ink[next + 2] - ink[last + 2]))
    val e = f * f * (3 - 2 * f)
    return Pen(ink[last] + (ink[next] - ink[last]) * e, ink[last + 1] + (ink[next + 1] - ink[last + 1]) * e, sin(PI * f))
}

/**
 * Cuts the ink drawn up to [upTo] (ms) into short straight pieces along its curves, into [out]: x0,
 * y0, x1, y1, width, time for each.
 */
internal fun pieces(ink: Ink, upTo: Double, out: Ink) {
    out.clear()
    var a = 0
    while (a < ink.size) {
        // The stroke runs from sample a to sample b (indices into ink).
        var b = a
        while (b + STRIDE < ink.size && ink[b + STRIDE + 4] == ink[a + 4]) b += STRIDE
        if (ink[a + 2] <= upTo) {
            if (a == b) out.push(ink[a], ink[a + 1], ink[a], ink[a + 1], ink[a + 3], ink[a + 2])
            var i = a
            while (i < b) {
                val j = i + STRIDE
                if (ink[j + 2] > upTo && ink[i + 2] > upTo) break
                val h = max(a, i - STRIDE)
                val k = min(b, j + STRIDE)
                val reach = if (ink[j + 2] <= upTo) 1.0 else clamp01((upTo - ink[i + 2]) / max(1.0, ink[j + 2] - ink[i + 2]))
                val steps = min(MAX_PIECES, max(1, ceil(hypot(ink[j] - ink[i], ink[j + 1] - ink[i + 1]) / SUBSTEP).toInt()))
                var px = ink[i]
                var py = ink[i + 1]
                for (s in 1..steps) {
                    var u = s.toDouble() / steps
                    if (u > reach) u = reach
                    val qx = catmull(ink[h], ink[i], ink[j], ink[k], u)
                    val qy = catmull(ink[h + 1], ink[i + 1], ink[j + 1], ink[k + 1], u)
                    out.push(px, py, qx, qy, ink[i + 3] + (ink[j + 3] - ink[i + 3]) * u, ink[i + 2] + (ink[j + 2] - ink[i + 2]) * u)
                    px = qx
                    py = qy
                    if (u >= reach) break
                }
                if (reach < 1) break
                i += STRIDE
            }
        }
        a = b + STRIDE
    }
}

// The half discs at the ends of a piece, as unit steps round from its side: 5, 7 or 9 steps.
private val TURNS = listOf(5, 7, 9).associateWith { half ->
    val step = PI / half
    DoubleArray(half * 2) { cos(it * step) } to DoubleArray(half * 2) { sin(it * step) }
}

/**
 * Adds the outline of a round-ended piece of ink from (x0, y0) to (x1, y1), [r] wide on each side: two
 * half discs joined by straight sides, always wound the same way so that overlapping pieces fill as
 * one. A piece with no length is a disc.
 */
internal fun addPiece(path: Path, x0: Double, y0: Double, x1: Double, y1: Double, r: Double) {
    val half = if (r < 1.2) 5 else if (r < 2.5) 7 else 9
    val (cs, sn) = TURNS.getValue(half)
    val dx = x1 - x0
    val dy = y1 - y0
    val length = hypot(dx, dy)
    if (length < 1e-4) {
        path.moveTo((x0 + r).toFloat(), y0.toFloat())
        for (k in 1 until half * 2) path.lineTo((x0 + r * cs[k]).toFloat(), (y0 - r * sn[k]).toFloat())
        path.close()
        return
    }
    // Round from the side n by −k steps: (cos(a0 − kθ), sin(a0 − kθ)) with cos a0 = nx, sin a0 = ny.
    val nx = -dy / length
    val ny = dx / length
    path.moveTo((x0 + nx * r).toFloat(), (y0 + ny * r).toFloat())
    path.lineTo((x1 + nx * r).toFloat(), (y1 + ny * r).toFloat())
    for (k in 1..half) {
        val c = if (k == half) -1.0 else cs[k]
        val s = if (k == half) 0.0 else sn[k]
        path.lineTo((x1 + r * (nx * c + ny * s)).toFloat(), (y1 + r * (ny * c - nx * s)).toFloat())
    }
    path.lineTo((x0 - nx * r).toFloat(), (y0 - ny * r).toFloat())
    for (k in 1 until half) {
        // Half a turn further on: the signs flip.
        path.lineTo((x0 - r * (nx * cs[k] + ny * sn[k])).toFloat(), (y0 - r * (ny * cs[k] - nx * sn[k])).toFloat())
    }
    path.close()
}

private fun argb(c: FloatArray, alpha: Float = 1f): Int {
    fun ch(v: Float) = (v.coerceIn(0f, 1f) * 255).roundToInt()
    return (ch(alpha) shl 24) or (ch(c[0]) shl 16) or (ch(c[1]) shl 8) or ch(c[2])
}

private fun mixed(a: FloatArray, b: FloatArray, k: Float) =
    floatArrayOf(a[0] + (b[0] - a[0]) * k, a[1] + (b[1] - a[1]) * k, a[2] + (b[2] - a[2]) * k)

private fun hash(n: Int): Float {
    val s = sin(n * 127.1 + 311.7) * 43758.5453
    return (s - floor(s)).toFloat()
}

/** Where the felt edge of the wiper is at a point of the sweep (0…1). */
internal fun wiperEdge(sweep: Float, width: Float) = -WIPER_WIDTH + (width + 2 * WIPER_WIDTH + FELT) * sweep

internal class InkFrame(
    val ink: Ink,
    /** While replaying: the retimed copy being drawn, else empty. */
    val replay: Ink,
    /** The pad's clock, ms. */
    val now: Double,
    val ghost: Float,
    /** 0…1 through a sweep of the wiper; 0 when none. */
    val sweep: Float,
    /** The whole ink's opacity: reduced motion fades it out instead of sweeping it away. */
    val alpha: Float,
    val width: Float,
    val height: Float,
    val penX: Float,
    val penY: Float,
    val penLift: Float,
    val penAlpha: Float,
)

/** Draws the ink, the wiper and the nib in dp; it keeps its paints and paths from frame to frame. */
internal class InkPainter {
    private val cut = Ink(8192)
    private val inkPaths = List(WET_LEVELS) { Path() }
    private val sheenPaths = List(WET_LEVELS) { Path() }
    private val ghostPath = Path()
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val layer = Paint().apply {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) blendMode = BlendMode.MULTIPLY
    }
    private val inkColors = IntArray(WET_LEVELS) { level -> argb(mixed(INK_DRY, INK_WET, level / (WET_LEVELS - 1f))) }
    private val sheenColors = IntArray(WET_LEVELS) { level ->
        val wet = level / (WET_LEVELS - 1f)
        argb(mixed(mixed(INK_DRY, INK_WET, wet), SHEEN, SHEEN_MIX * wet))
    }
    private val blurs = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P

    /** Everything drawn over the paper: the ink (multiplied into it), the wiper and the pen. */
    fun paint(canvas: Canvas, f: InkFrame) {
        // The ink is drawn opaque into a layer that is multiplied into the paper, so the paper's grain
        // shows through it and pieces that overlap never darken each other.
        layer.alpha = (f.alpha * 255).roundToInt()
        val saved = canvas.saveLayer(-60f, -60f, f.width + 60, f.height + 60, layer)
        if (f.sweep > 0) canvas.clipRect(wiperEdge(f.sweep, f.width) - FELT / 2, -50f, f.width + 100, f.height + 100)
        if (f.ghost > 0.002f && !f.ink.isEmpty) {
            pieces(f.ink, Double.POSITIVE_INFINITY, cut)
            ghostPath.rewind()
            var i = 0
            while (i < cut.size) {
                addPiece(ghostPath, cut[i], cut[i + 1], cut[i + 2], cut[i + 3], cut[i + 4] / 2)
                i += 6
            }
            fill.color = argb(INK_DRY, f.ghost * GHOST_ALPHA)
            canvas.drawPath(ghostPath, fill)
        }
        if (f.replay.isEmpty) pieces(f.ink, Double.POSITIVE_INFINITY, cut) else pieces(f.replay, f.now, cut)
        drawPieces(canvas, f.now)
        canvas.restoreToCount(saved)
        if (f.sweep > 0 && f.sweep < 1) drawWiper(canvas, f.sweep, f.width, f.height)
        drawNib(canvas, f.penX, f.penY, f.penLift, f.penAlpha)
    }

    /** The pieces in their colours, wettest last, then their highlights on top. */
    private fun drawPieces(canvas: Canvas, now: Double) {
        inkPaths.forEach { it.rewind() }
        sheenPaths.forEach { it.rewind() }
        var i = 0
        while (i < cut.size) {
            val wet = clamp01(1 - (now - cut[i + 5]) / DRY_MS)
            // roundToInt throws on NaN.
            val level = if (wet.isNaN()) 0 else (wet * (WET_LEVELS - 1)).roundToInt()
            val w = cut[i + 4]
            addPiece(inkPaths[level], cut[i], cut[i + 1], cut[i + 2], cut[i + 3], w / 2)
            if (level > 0) {
                val dx = -w * 0.17
                val dy = -w * 0.2
                addPiece(sheenPaths[level], cut[i] + dx, cut[i + 1] + dy, cut[i + 2] + dx, cut[i + 3] + dy, w * SHEEN_WIDTH / 2)
            }
            i += 6
        }
        fill.maskFilter = null
        fill.shader = null
        for (level in 0 until WET_LEVELS) {
            if (inkPaths[level].isEmpty) continue
            fill.color = inkColors[level]
            canvas.drawPath(inkPaths[level], fill)
        }
        for (level in 1 until WET_LEVELS) {
            if (sheenPaths[level].isEmpty) continue
            fill.color = sheenColors[level]
            canvas.drawPath(sheenPaths[level], fill)
        }
    }

    private val dust = Paint(Paint.ANTI_ALIAS_FLAG)
    private val shadow = Paint(Paint.ANTI_ALIAS_FLAG)
    private val wood = Paint(Paint.ANTI_ALIAS_FLAG)
    private val grain = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 0.8f
        color = argb(floatArrayOf(0.12f, 0.07f, 0.04f), 0.35f)
    }
    private val brass = Paint(Paint.ANTI_ALIAS_FLAG)
    private val felt = Paint(Paint.ANTI_ALIAS_FLAG)

    private fun drawWiper(canvas: Canvas, sweep: Float, width: Float, height: Float) {
        val edge = wiperEdge(sweep, width)
        val top = WIPER_INSET
        val h = height - 2 * WIPER_INSET
        // Dust first, behind the block.
        for (i in 0 until CRUMBS) {
            val born = (i + 0.5f) / CRUMBS
            val age = (sweep - born) / CRUMB_LIFE
            if (age <= 0 || age >= 1) continue
            val bornX = wiperEdge(born, width)
            val y = top + 12 + hash(i) * (h - 24) + age * age * 22
            val x = bornX - age * (10 + hash(i + 7) * 18)
            dust.color = argb(INK_DRY, 0.55f * (1 - age))
            canvas.drawCircle(x, y, 0.7f + hash(i + 3) * 1.1f, dust)
        }
        // The block leans into its travel, most in the middle of the sweep.
        canvas.save()
        canvas.rotate(WIPER_LEAN * sin(PI * sweep).toFloat(), edge, top + h / 2)
        val left = edge - WIPER_WIDTH
        if (blurs) {
            shadow.color = argb(floatArrayOf(0.1f, 0.06f, 0.03f), 0.32f)
            shadow.maskFilter = BlurMaskFilter(sigmaToRadius(7f), BlurMaskFilter.Blur.NORMAL)
            canvas.drawRoundRect(left - 4, top + 8, left + WIPER_WIDTH, top + 8 + h, 14f, 14f, shadow)
        }
        wood.shader = LinearGradient(
            left, 0f, edge, 0f,
            intArrayOf(0xFF6B4A33.toInt(), 0xFF4A3121.toInt(), 0xFF2D1D13.toInt()), floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP,
        )
        canvas.drawRoundRect(left, top, left + WIPER_WIDTH, top + h, 14f, 14f, wood)
        // The grain: a few darker lines along the block.
        for (g in 0 until 4) {
            val gx = left + 6 + g * 7 + hash(g + 11) * 3
            canvas.drawLine(gx, top + 10, gx + hash(g + 5) * 2 - 1, top + h - 10, grain)
        }
        // A brass knob in the middle, where a hand would hold it.
        val knobX = left + (WIPER_WIDTH - FELT) / 2
        val knobY = top + h / 2
        brass.shader = RadialGradient(
            knobX - 3, knobY - 4, KNOB * 1.3f,
            intArrayOf(0xFFFBE7B5.toInt(), 0xFFC99A46.toInt(), 0xFF6D5020.toInt()), floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP,
        )
        canvas.drawCircle(knobX, knobY, KNOB, brass)
        felt.color = 0xFFD9D0BF.toInt()
        canvas.drawRoundRect(edge - FELT, top + 2, edge, top + h - 2, 4f, 4f, felt)
        // The felt picks up ink as it goes.
        felt.color = argb(INK_DRY, 0.45f * (sweep * 1.6f).coerceIn(0f, 1f))
        canvas.drawRoundRect(edge - FELT, top + 2, edge - FELT + 3, top + h - 2, 1.5f, 1.5f, felt)
        canvas.restore()
    }

    private val nibShape = Path().apply {
        moveTo(0f, 0f)
        cubicTo(-3f, -6f, -8f, -15f, -8f, -24f)
        lineTo(-8f, -38f)
        lineTo(8f, -38f)
        lineTo(8f, -24f)
        cubicTo(8f, -15f, 3f, -6f, 0f, 0f)
        close()
    }
    private val nibShadow = Paint(Paint.ANTI_ALIAS_FLAG)
    private val gold = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = LinearGradient(
            -8f, 0f, 8f, 0f,
            intArrayOf(0xFF8A6A2C.toInt(), 0xFFF4DC9A.toInt(), 0xFFC99A46.toInt(), 0xFF6D5020.toInt()),
            floatArrayOf(0f, 0.35f, 0.65f, 1f), Shader.TileMode.CLAMP,
        )
    }
    private val line = Paint(Paint.ANTI_ALIAS_FLAG)

    /** A gold nib with its slit and breather hole, its point on the ink, lifting on a growing shadow. */
    private fun drawNib(canvas: Canvas, x: Float, y: Float, lift: Float, alpha: Float) {
        if (alpha <= 0.01f) return
        val up = lift * 7
        canvas.save()
        canvas.translate(x, y)
        canvas.rotate(NIB_ANGLE)
        nibShadow.color = argb(floatArrayOf(0.12f, 0.08f, 0.02f), 0.28f * alpha)
        nibShadow.maskFilter = if (blurs) BlurMaskFilter(sigmaToRadius(2.5f + up * 0.5f), BlurMaskFilter.Blur.NORMAL) else null
        canvas.save()
        canvas.translate(3 + up, 2 + up)
        canvas.drawPath(nibShape, nibShadow)
        canvas.restore()
        canvas.translate(-up * 0.4f, -up)
        gold.alpha = (alpha * 255).roundToInt()
        canvas.drawPath(nibShape, gold)
        line.style = Paint.Style.STROKE
        line.strokeWidth = 0.9f
        line.color = argb(floatArrayOf(0.25f, 0.17f, 0.05f), alpha)
        canvas.drawLine(0f, -1.5f, 0f, -17f, line)
        line.style = Paint.Style.FILL
        canvas.drawCircle(0f, -18.5f, 1.8f, line)
        // The ink on the point.
        line.color = argb(INK_WET, alpha)
        canvas.drawCircle(0f, -0.8f, 1.2f, line)
        canvas.restore()
    }
}

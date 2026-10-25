package com.ngocdevv.nativecook.blocks.splash.brushinksplash

import androidx.compose.animation.core.CubicBezierEasing
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

/*
 The Brush Ink Splash's clock and brush strokes (the React Native brushInkMotion.ts). Every value
 is a closed-form function of the loop time t (seconds), so t = LOOP draws the frame of t = 0.
 Lengths in dp.

   0.0 – 1.2   rest: blank rice paper.
   1.2 – 1.45  the brush loads: it presses down at the start of the circle (a swelling blot).
   1.45 – 3.0  it paints the ensō clockwise, easing in and out; the stroke thins and runs dry
               toward its end, bristle streaks opening up in it.
   3.0 – 4.2   the wordmark "Sumi", stroke after stroke, with the same brush.
   3.4 – 4.8   the ink bleeds into the paper's fibres at every edge (ease-out).
   4.8 – 6.2   hold.
   6.2 – 7.0   the paper slides up (ease-in-out), revealing the journal's home screen.
   7.0 – 7.25  the home screen alone.
   7.25 – 7.7  a fresh sheet fades back in over it.
   7.7 – 8.0   rest.
 */

const val LOOP = 8f
private const val TAU = PI.toFloat() * 2f

internal fun inkClamp01(x: Float) = min(1f, max(0f, x))

internal fun inkSeg(t: Float, a: Float, b: Float) = inkClamp01((t - a) / (b - a))

internal fun inkEaseOut(x: Float): Float {
    val u = 1f - inkClamp01(x)
    return 1f - u * u * u
}

internal fun inkEaseInOut(x: Float): Float {
    val u = inkClamp01(x)
    return if (u < 0.5f) 4f * u * u * u else 1f - (-2f * u + 2f).pow(3) / 2f
}

internal fun inkHash(i: Int): Float {
    val s = sin(i * 12.9898 + 1.618) * 43758.5453
    return (s - floor(s)).toFloat()
}

/** A brush stroke: centreline points, half-widths, and how dry its end runs (0 … 1). */
internal class InkStroke(val xs: FloatArray, val ys: FloatArray, val hw: FloatArray, val dry: Float)

/** Stroke lengths and where each starts on the shared paint clock (dp, with gaps between). */
internal class InkPlan(
    val starts: FloatArray,
    val lengths: FloatArray,
    val dry: FloatArray,
    val caps: FloatArray,
    val ends: FloatArray,
    val total: Float,
    val ensoEnd: Float,
)

/** A gap of travel between strokes, while the brush is off the paper. */
private const val GAP = 40f
internal const val INK_MAX_STROKES = 8

private fun length(s: InkStroke): Float {
    var l = 0f
    for (i in 1 until s.xs.size) l += hypot(s.xs[i] - s.xs[i - 1], s.ys[i] - s.ys[i - 1])
    return l
}

internal fun inkPlan(strokes: List<InkStroke>): InkPlan {
    val starts = FloatArray(INK_MAX_STROKES) { 1e6f }
    val lengths = FloatArray(INK_MAX_STROKES) { 1f }
    val dry = FloatArray(INK_MAX_STROKES)
    val caps = FloatArray(INK_MAX_STROKES) { 1f }
    val ends = FloatArray(INK_MAX_STROKES) { 1f }
    var at = 0f
    var ensoEnd = 0f
    strokes.forEachIndexed { i, s ->
        val l = length(s)
        starts[i] = at
        lengths[i] = l
        dry[i] = s.dry
        caps[i] = s.hw[0] * 1.2f
        ends[i] = s.hw[s.hw.size - 1] * 1.2f
        at += l
        if (i == 0) ensoEnd = at
        at += GAP
    }
    return InkPlan(starts, lengths, dry, caps, ends, at - GAP, ensoEnd)
}

/** The ensō: a near circle of radius r round (cx, cy), clockwise from about eight o'clock. */
internal fun inkEnso(cx: Float, cy: Float, r0: Float): InkStroke {
    val n = 150
    val xs = FloatArray(n)
    val ys = FloatArray(n)
    val hw = FloatArray(n)
    val a0 = 150f / 180f * PI.toFloat()
    val sweep = 332f / 180f * PI.toFloat()
    for (i in 0 until n) {
        val s = i.toFloat() / (n - 1)
        val a = a0 + sweep * s
        // A hand-drawn circle: a little off round, curling inward at the end.
        val r = r0 * (1f + 0.03f * sin(2f * a + 0.5f) + 0.012f * sin(5f * a) - 0.06f * s * s * s)
        xs[i] = cx + cos(a) * r
        ys[i] = cy + sin(a) * r
        // Pressure: a heavy blot where the brush lands, full through the first half, then lifting.
        val blot = 1f + 0.6f * exp(-((s - 0.012f) * (s - 0.012f)) / 0.0005f)
        val body = 0.8f + 0.3f * sin(PI.toFloat() * min(1f, s * 1.5f)) + 0.06f * sin(s * 23f)
        val lift = 1f - 0.78f * max(0f, (s - 0.62f) / 0.38f).pow(1.4f)
        hw[i] = r0 * 0.125f * blot * body * lift
    }
    return InkStroke(xs, ys, hw, 1f)
}

/** Catmull-Rom through points, sampled every ~2 dp, with a pressure curve along the stroke. */
private fun brush(pts: Array<FloatArray>, ox: Float, oy: Float, k: Float, width: Float, dry: Float): InkStroke {
    val xs = ArrayList<Float>()
    val ys = ArrayList<Float>()
    val p = pts.map { floatArrayOf(ox + it[0] * k, oy + it[1] * k) }
    val n = p.size
    for (i in 0 until n - 1) {
        val p0 = p[max(0, i - 1)]
        val p1 = p[i]
        val p2 = p[i + 1]
        val p3 = p[min(n - 1, i + 2)]
        val steps = max(2, ceil(hypot(p2[0] - p1[0], p2[1] - p1[1]) / 2f).toInt())
        for (j in 0 until steps) {
            val t = j.toFloat() / steps
            val t2 = t * t
            val t3 = t2 * t
            fun f(a: Float, b: Float, c: Float, d: Float) =
                0.5f * (2f * b + (-a + c) * t + (2f * a - 5f * b + 4f * c - d) * t2 + (-a + 3f * b - 3f * c + d) * t3)
            xs.add(f(p0[0], p1[0], p2[0], p3[0]))
            ys.add(f(p0[1], p1[1], p2[1], p3[1]))
        }
    }
    xs.add(p[n - 1][0])
    ys.add(p[n - 1][1])
    val hw = FloatArray(xs.size) { i ->
        val s = i.toFloat() / max(1, xs.size - 1)
        // Lands with a press, carries, lifts off at the end.
        width * (0.75f + 0.35f * exp(-(s * s) / 0.01f)) * (1f - 0.55f * s * s)
    }
    return InkStroke(xs.toFloatArray(), ys.toFloatArray(), hw, dry)
}

private fun pts(vararg v: Float) = Array(v.size / 2) { floatArrayOf(v[it * 2], v[it * 2 + 1]) }

/** The wordmark "Sumi" in brush strokes, about 125 × 60 units, scaled by k round (cx, top). */
internal fun inkWordmark(cx: Float, top: Float, k: Float): List<InkStroke> {
    val ox = cx - 64f * k
    val w = 4.4f * k
    val s = pts(31f, 9f, 20f, 4f, 9f, 9f, 9f, 20f, 20f, 28f, 30f, 37f, 28f, 50f, 16f, 57f, 3f, 52f)
    val u = pts(42f, 24f, 41f, 42f, 45f, 54f, 53f, 57f, 61f, 50f, 64f, 38f, 64f, 24f, 64.5f, 42f, 67f, 57f)
    val m1 = pts(78f, 25f, 78.5f, 42f, 78f, 57f)
    val m2 = pts(78f, 39f, 82f, 29f, 89f, 26f, 94f, 33f, 94.5f, 57f)
    val m3 = pts(94f, 39f, 98f, 29f, 105f, 26f, 110f, 33f, 110.5f, 57f)
    val i1 = pts(123f, 28f, 123.5f, 43f, 124f, 57f)
    val dot = pts(121.5f, 13f, 124f, 16f)
    return listOf(
        brush(s, ox, top, k, w, 0.5f),
        brush(u, ox, top, k, w, 0.45f),
        brush(m1, ox, top, k, w, 0.2f),
        brush(m2, ox, top, k, w, 0.4f),
        brush(m3, ox, top, k, w, 0.55f),
        brush(i1, ox, top, k, w, 0.3f),
        brush(dot, ox, top, k, w * 1.15f, 0f),
    )
}

internal class InkPose {
    /** How far the brush has painted along the shared paint clock (dp). */
    var head = 0f
    /** Ink bled into the fibres (0 … 1) and the ink's opacity. */
    var bleed = 0f
    var ink = 0f
    /** Paper slid up (0 … 1) and its opacity (the fresh sheet fading back in). */
    var slide = 0f
    var paper = 1f
    /** Seconds since the brush landed (drops at the start) and since the ensō ended (flick). */
    var landed = -1f
    var flicked = -1f
}

internal fun InkPose.setLoop(t: Float, p: InkPlan) {
    var h = 0f
    if (t >= 1.2f && t < 1.45f) {
        h = 9f * inkEaseOut(inkSeg(t, 1.2f, 1.45f))
    } else if (t >= 1.45f && t < 3.0f) {
        h = 9f + (p.ensoEnd - 9f) * inkEaseInOut(inkSeg(t, 1.45f, 3.0f))
    } else if (t >= 3.0f) {
        h = p.ensoEnd + (p.total + 2f - p.ensoEnd) * inkEaseInOut(inkSeg(t, 3.0f, 4.2f))
    }
    val gone = t >= 7.0f
    head = if (gone) 0f else h
    bleed = if (gone) 0f else inkEaseOut(inkSeg(t, 3.4f, 4.8f))
    ink = if (gone) 0f else 1f
    slide = if (gone) 0f else inkEaseInOut(inkSeg(t, 6.2f, 7.0f))
    paper = if (gone) inkEaseInOut(inkSeg(t, 7.25f, 7.7f)) else 1f
    landed = if (!gone && t >= 1.25f) t - 1.25f else -1f
    flicked = if (!gone && t >= 2.95f) t - 2.95f else -1f
}

/** The finished splash, still (reduced motion). */
internal fun InkPose.setPainted(p: InkPlan) {
    head = p.total + 2f
    bleed = 1f
    ink = 1f
    slide = 0f
    paper = 1f
    landed = 4f
    flicked = 4f
}

/** Ink drops, x y r relative to their anchor, for the landing (set 0) and the flick (set 1). */
internal fun inkDrops(set: Int, angle: Float): Array<FloatArray> {
    val n = if (set == 0) 5 else 4
    return Array(n) { i ->
        val h = inkHash(i + set * 17)
        val a = if (set == 0) inkHash(i + 3) * TAU else angle + (inkHash(i + 9) - 0.5f) * 0.5f
        val d = if (set == 0) 14f + h * 22f else 10f + i * 9f + h * 6f
        floatArrayOf(cos(a) * d, sin(a) * d, if (set == 0) 1.2f + inkHash(i + 5) * 2.6f else 2.6f - i * 0.45f)
    }
}

/** A value animated as a pure function of the screen clock (a timing curve from where it was). */
internal class InkTrack(initial: Float) {
    private var from = initial
    private var to = initial
    private var start = 0f
    private var duration = 0f
    private var easing: (Float) -> Float = { it }

    fun set(v: Float) {
        from = v; to = v; duration = 0f
    }

    fun timing(now: Float, target: Float, seconds: Float, ease: (Float) -> Float) {
        from = value(now); to = target; start = now; duration = seconds; easing = ease
    }

    fun value(now: Float): Float {
        if (duration <= 0f) return to
        val u = (now - start) / duration
        if (u <= 0f) return from
        if (u >= 1f) return to
        return from + (to - from) * easing(u)
    }

    fun done(now: Float) = duration <= 0f || now >= start + duration
}

private val EaseOutBezier = CubicBezierEasing(0.23f, 1f, 0.32f, 1f)
private val EaseInOutBezier = CubicBezierEasing(0.77f, 0f, 0.175f, 1f)
internal val InkEaseOutCurve: (Float) -> Float = { EaseOutBezier.transform(it) }
internal val InkEaseInOutCurve: (Float) -> Float = { EaseInOutBezier.transform(it) }

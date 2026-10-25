package com.ngocdevv.nativecook.blocks.inputs.etchsketchsignature

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * The signature script and the loop's timeline, ported from etchMotion.ts. The stylus position,
 * the knobs and the drawn line are pure functions of the loop time t; the frame at t = LOOP is the
 * frame at 0. Lengths are dp (= pt), times seconds.
 */
internal object EtchSketchSignatureMotion {
    const val LOOP = 10f

    // The poster is the finished signature: it rests, is shaken clear, and is drawn again.
    const val SHAKE_START = 1.2f
    private const val SWEEP_START = 1.4f
    const val SWEEP_END = 2.4f
    private const val SHAKE_END = 2.6f
    private const val DRAW_START = 2.9f
    private const val DRAW_END = 8.6f
    /** Points of travel per full turn of a knob. */
    const val PT_PER_TURN = 120f

    fun clamp01(x: Float) = min(1f, max(0f, x))
    fun seg(t: Float, a: Float, b: Float) = clamp01((t - a) / (b - a))
    fun easeInOut(x: Float) = if (x < 0.5f) 4 * x * x * x else 1 - (-2 * x + 2).pow(3) / 2

    /**
     * The signature in screen shares: a stair-stepped "J" (the bar, the stem, the hook) and an
     * underline flourish with a wave. Each entry is u, v, stairs: stairs = 1 turns the move into
     * the alternating little steps two knobs make when turned in turns.
     */
    private val WAYPOINTS = arrayOf(
        floatArrayOf(0.16f, 0.24f, 0f),
        floatArrayOf(0.5f, 0.24f, 0f),
        floatArrayOf(0.36f, 0.24f, 0f),
        floatArrayOf(0.36f, 0.6f, 0f),
        floatArrayOf(0.33f, 0.68f, 1f),
        floatArrayOf(0.26f, 0.72f, 1f),
        floatArrayOf(0.19f, 0.69f, 1f),
        floatArrayOf(0.15f, 0.62f, 1f),
        floatArrayOf(0.15f, 0.8f, 0f),
        floatArrayOf(0.55f, 0.8f, 0f),
        floatArrayOf(0.62f, 0.67f, 1f),
        floatArrayOf(0.69f, 0.8f, 1f),
        floatArrayOf(0.76f, 0.67f, 1f),
        floatArrayOf(0.83f, 0.8f, 1f),
        floatArrayOf(0.88f, 0.8f, 0f),
    )

    /** The signature as points (x, y interleaved) inside the screen rect, with their running length. */
    class Signature(val pts: FloatArray, val run: FloatArray) {
        val total get() = run[run.size - 1]
        val count get() = run.size
    }

    fun signature(x: Float, y: Float, w: Float, h: Float): Signature {
        val pts = ArrayList<Float>()
        pts.add(x + WAYPOINTS[0][0] * w)
        pts.add(y + WAYPOINTS[0][1] * h)
        val step = 5f
        for (k in 1 until WAYPOINTS.size) {
            val (u, v, stairs) = WAYPOINTS[k]
            val tx = x + u * w
            val ty = y + v * h
            if (stairs == 0f) {
                pts.add(tx); pts.add(ty)
                continue
            }
            var cx = pts[pts.size - 2]
            var cy = pts[pts.size - 1]
            val n = max(1, (max(abs(tx - cx), abs(ty - cy)) / step).roundToInt())
            val dx = (tx - cx) / n
            val dy = (ty - cy) / n
            repeat(n) {
                cx += dx
                pts.add(cx); pts.add(cy)
                cy += dy
                pts.add(cx); pts.add(cy)
            }
        }
        val p = pts.toFloatArray()
        val run = FloatArray(p.size / 2)
        for (i in 1 until run.size) run[i] = run[i - 1] + hypot(p[2 * i] - p[2 * i - 2], p[2 * i + 1] - p[2 * i - 1])
        return Signature(p, run)
    }

    /** What the toy shows at a moment. */
    class Etch(
        /** Length of the line drawn so far, and the stylus position. */
        val drawn: Float,
        val sx: Float,
        val sy: Float,
        /** Front of the powder resettling (y; very negative when none) and its glow 0…1. */
        val sweep: Float,
        val band: Float,
        /** Shake of the toy (rad). */
        val wobble: Float,
        /** 0…1: the signature is there (enables the confirm button). */
        val signed: Float,
    )

    /** The point at running length d along the signature (x, y). */
    fun pointAt(s: Signature, d: Float, out: FloatArray) {
        val pts = s.pts
        val run = s.run
        val n = run.size
        if (d <= 0f) { out[0] = pts[0]; out[1] = pts[1]; return }
        if (d >= run[n - 1]) { out[0] = pts[2 * n - 2]; out[1] = pts[2 * n - 1]; return }
        var k = 1
        while (k < n - 1 && run[k] < d) k += 1
        val f = (d - run[k - 1]) / max(0.0001f, run[k] - run[k - 1])
        out[0] = pts[2 * k - 2] + (pts[2 * k] - pts[2 * k - 2]) * f
        out[1] = pts[2 * k - 1] + (pts[2 * k + 1] - pts[2 * k - 1]) * f
    }

    /** A shake: a quick decaying wobble, tau seconds in. */
    fun shake(tau: Float, len: Float): Float {
        if (tau <= 0f || tau >= len) return 0f
        return 0.045f * exp(-3.2f * tau / len) * sin(2 * PI.toFloat() * 5.5f * tau) * sin(PI.toFloat() * tau / len)
    }

    /** The whole loop for a screen of top [top] and height [h]. */
    fun etchAt(t: Float, s: Signature, top: Float, h: Float): Etch {
        val total = s.total
        // The knobs turn with a gentle start and stop; the pen keeps its pace in between.
        val u = seg(t, DRAW_START, DRAW_END)
        val pace = if (u < 0.08f) u * u / 0.16f else if (u > 0.92f) 1 - (1 - u) * (1 - u) / 0.16f else u
        // Whole until the sweep has passed, then nothing until the pen sets off again.
        val drawn = if (t < SWEEP_END) total else total * clamp01(pace)
        val at = FloatArray(2)
        pointAt(s, drawn, at)
        // While the powder resettles, the stylus is wound back from the signature's end to its start.
        val home = easeInOut(seg(t, SWEEP_START, SWEEP_END))
        val end = FloatArray(2)
        pointAt(s, total, end)
        val sweeping = t >= SWEEP_START && t < SWEEP_END
        return Etch(
            drawn = if (t >= SWEEP_END && t < DRAW_START) 0f else drawn,
            sx = if (t < SWEEP_END) end[0] + (s.pts[0] - end[0]) * home else at[0],
            sy = if (t < SWEEP_END) end[1] + (s.pts[1] - end[1]) * home else at[1],
            sweep = if (sweeping) top - 30 + (h + 60) * home else -1e4f,
            band = if (sweeping) sin(PI.toFloat() * home) else 0f,
            wobble = shake(t - SHAKE_START, SHAKE_END - SHAKE_START),
            signed = 1 - seg(t, SWEEP_START, SWEEP_START + 0.3f) + seg(t, DRAW_START + 0.3f, DRAW_START + 0.6f),
        )
    }

    /** cubic-bezier(0.77, 0, 0.175, 1): the stylus winding home after a touch. */
    fun homeEase(x: Float): Float {
        val p1x = 0.77f; val p1y = 0f; val p2x = 0.175f; val p2y = 1f
        var lo = 0f
        var hi = 1f
        var s = x
        repeat(24) {
            val bx = 3 * (1 - s) * (1 - s) * s * p1x + 3 * (1 - s) * s * s * p2x + s * s * s
            if (bx < x) lo = s else hi = s
            s = (lo + hi) / 2
        }
        return 3 * (1 - s) * (1 - s) * s * p1y + 3 * (1 - s) * s * s * p2y + s * s * s
    }
}

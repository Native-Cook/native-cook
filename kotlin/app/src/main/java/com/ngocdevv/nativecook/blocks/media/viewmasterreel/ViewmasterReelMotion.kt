package com.ngocdevv.nativecook.blocks.media.viewmasterreel

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.round
import kotlin.math.sin

/*
 Viewmaster Reel — the autoPlay timeline (a port of viewmasterMotion.ts; the numbers are the
 same). Pure functions of the loop time t.

 The reel's position `pos` is in slots (7 per turn); slot k sits at the gate (12 o'clock) when
 pos = k. The disc is drawn turned by −pos × 360/7°, the window shows slot round(pos) mod 7, and
 the window's shutter closes as pos leaves a whole slot (fully shut halfway between two).

   0.0 – 1.3   rest on slot 1 (pos 0).
   1.3 – 2.1   advance: the lever is pulled down (0 – 0.3 of the step, ease-in-out), the disc turns
               one slot (0.28 – 0.72, ease-out-back: the clunk), the lever springs back (0.4 – 1.0),
               the viewer shudders 1.4 dp as the detent catches.
   3.3 – 4.1   the same to slot 3.          5.3 – 6.1   the same to slot 4.
   7.0 – 8.4   the disc is spun by hand through four slots back round to slot 1 (pos 3 → 7).
   8.4 – 9.0   rest. pos = 7 draws exactly as pos = 0.
 */
internal object ViewmasterReelMotion {
    const val LOOP = 9f
    const val SLOTS = 7
    const val STEP_DEG = 360f / SLOTS

    private val ADVANCES = floatArrayOf(1.3f, 3.3f, 5.3f)
    private const val ADVANCE_LEN = 0.8f

    fun clamp01(x: Float) = if (x < 0f) 0f else if (x > 1f) 1f else x
    fun seg(t: Float, a: Float, b: Float) = clamp01((t - a) / (b - a))
    fun smoothstep(a: Float, b: Float, x: Float): Float { val u = clamp01((x - a) / (b - a)); return u * u * (3f - 2f * u) }
    fun easeInOutCubic(u: Float) = if (u < 0.5f) 4f * u * u * u else 1f - (-2f * u + 2f).pow(3) / 2f
    fun easeOutBack(u: Float, c1: Float): Float { val c3 = c1 + 1f; return 1f + c3 * (u - 1f).pow(3) + c1 * (u - 1f).pow(2) }
    fun easeInOutBack(u: Float, c1: Float): Float {
        val c2 = c1 * 1.525f
        return if (u < 0.5f) (2f * u).pow(2) * ((c2 + 1f) * 2f * u - c2) / 2f
        else ((2f * u - 2f).pow(2) * ((c2 + 1f) * (u * 2f - 2f) + c2) + 2f) / 2f
    }

    /** Positive modulo. */
    fun wrap(x: Float, m: Float) = ((x % m) + m) % m
    fun wrapInt(x: Int, m: Int) = ((x % m) + m) % m

    /** The window's shutter for a reel position: 0 open on a whole slot, 1 shut halfway between. */
    fun shutterFor(pos: Float) = smoothstep(0.05f, 0.3f, abs(pos - round(pos)))

    /** [pos, lever, shake] at loop time t. */
    fun pose(t: Float, out: FloatArray) {
        var pos = 0f
        var lever = 0f
        var shake = 0f
        for (a in ADVANCES) {
            val u = seg(t, a, a + ADVANCE_LEN)
            pos += easeOutBack(seg(u, 0.28f, 0.72f), 1.6f)
            val down = easeInOutCubic(seg(u, 0f, 0.3f))
            val up = easeOutBack(seg(u, 0.4f, 1f), 1.2f)
            lever += down * (1f - up)
            val tau = t - (a + ADVANCE_LEN * 0.52f)
            if (tau > 0f && tau < 0.5f) shake += 1.4f * sin(2f * PI.toFloat() * 16f * tau) * exp(-tau * 11f)
        }
        pos += 4f * easeInOutBack(seg(t, 7.0f, 8.4f), 0.5f)
        out[0] = pos; out[1] = lever; out[2] = shake
    }

    /** The caption's roll position: whole numbers on a slide, rolling over the middle 40 % of a step. */
    fun roll(pos: Float): Float {
        val pm = wrap(pos, SLOTS.toFloat())
        val base = floor(pm)
        return base + smoothstep(0.3f, 0.7f, pm - base)
    }

    /** Pulled past a limit, the value follows less and less. */
    fun rubber(x: Float, lo: Float, hi: Float, k: Float): Float {
        if (x < lo) { val o = lo - x; return lo - o / (1f + o / k) }
        if (x > hi) { val o = x - hi; return hi + o / (1f + o / k) }
        return x
    }

    /** CSS cubic-bezier easing, solved for x by Newton steps. */
    fun bezier(x1: Float, y1: Float, x2: Float, y2: Float): (Float) -> Float = { x ->
        var t = x
        repeat(8) {
            val cx = 3f * x1 * t * (1 - t) * (1 - t) + 3f * x2 * t * t * (1 - t) + t * t * t - x
            val dx = 3f * x1 * (1 - t) * (1 - t) + 6f * (x2 - x1) * t * (1 - t) + 3f * (1 - x2) * t * t
            if (abs(dx) > 1e-5f) t = (t - cx / dx).coerceIn(0f, 1f)
        }
        3f * y1 * t * (1 - t) * (1 - t) + 3f * y2 * t * t * (1 - t) + t * t * t
    }

}

/**
 * One animated number for the finger's state: a spring (response in seconds and damping ratio,
 * as SwiftUI's `.spring(response:dampingFraction:)`, carrying a velocity) or a timed curve with an
 * optional delay. Stepped by the frame loop; nothing here is Compose state.
 */
internal class ViewmasterReelAnim(var value: Float, private val eps: Float = 0.001f) {
    var velocity = 0f
    private var mode = 0
    private var target = 0f
    private var omega = 0f
    private var zeta = 1f
    private var from = 0f
    private var elapsed = 0f
    private var duration = 0f
    private var delay = 0f
    private var started = false
    private var easing: (Float) -> Float = { it }
    private var done: (() -> Unit)? = null

    val busy get() = mode != 0

    fun set(v: Float) { value = v; velocity = 0f; mode = 0; done = null }
    fun cancel() { mode = 0; done = null }

    fun spring(to: Float, response: Float, damping: Float, v: Float = velocity) {
        target = to; omega = (2 * PI / response).toFloat(); zeta = damping; velocity = v; mode = 1; done = null
    }

    fun tween(to: Float, seconds: Float, ease: (Float) -> Float = { it }, after: Float = 0f, onDone: (() -> Unit)? = null) {
        target = to; duration = max(1e-3f, seconds); delay = after; easing = ease; elapsed = 0f; started = false
        from = value; mode = 2; done = onDone
    }

    fun step(dt: Float) {
        when (mode) {
            1 -> {
                var left = dt
                while (left > 0f) {
                    val h = min(left, 1f / 240f)
                    val a = -omega * omega * (value - target) - 2f * zeta * omega * velocity
                    velocity += a * h
                    value += velocity * h
                    left -= h
                }
                if (abs(value - target) < eps && abs(velocity) < eps * 10f) { value = target; velocity = 0f; finish() }
            }
            2 -> {
                elapsed += dt
                if (elapsed < delay) return
                if (!started) { started = true; from = value }
                val u = ViewmasterReelMotion.clamp01((elapsed - delay) / duration)
                value = from + (target - from) * easing(u)
                velocity = 0f
                if (u >= 1f) finish()
            }
        }
    }

    private fun finish() {
        mode = 0
        val d = done
        done = null
        d?.invoke()
    }
}

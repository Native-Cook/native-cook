package com.ngocdevv.nativecook.blocks.cards.filmnegativecard

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

/*
 Film Negative Card — the autoPlay timeline (a port of filmNegativeMotion.ts; the numbers are the
 same). Every value is a pure function of the loop time t (seconds), so t = LOOP is t = 0.

   0.0 – 1.3   rest: the strip lies half over the top of the light table.
   1.3 – 3.0   it slides fully onto the light (a 4 % dip back first, a 3 % overshoot at the end),
               lifting off the desk on the way. The light's edge sweeps the negative into the positive.
   3.0 – 5.0   hold on the light; the loupe fades in (3.05 – 3.45), wanders one whole sine, fades out (4.6 – 4.95).
   5.0 – 6.8   it slides back to its resting place.
   6.8 – 8.0   rest.
 */
internal object FilmNegativeMotion {
    const val LOOP = 8f
    /** Where the strip rests, between the desk (0) and fully on the light (1). */
    const val REST_ON = 0.45f

    private const val WANDER_X = 26f
    private const val WANDER_Y = 9f
    private const val BACK = 0.75f

    fun clamp01(x: Float) = if (x < 0f) 0f else if (x > 1f) 1f else x
    fun seg(t: Float, a: Float, b: Float) = clamp01((t - a) / (b - a))
    fun easeOutCubic(u: Float): Float { val v = 1f - u; return 1f - v * v * v }
    fun easeInOutCubic(u: Float) = if (u < 0.5f) 4f * u * u * u else 1f - (-2f * u + 2f).pow(3) / 2f
    fun easeInOutBack(u: Float, c1: Float): Float {
        val c2 = c1 * 1.525f
        return if (u < 0.5f) {
            (2f * u).pow(2) * ((c2 + 1f) * 2f * u - c2) / 2f
        } else {
            ((2f * u - 2f).pow(2) * ((c2 + 1f) * (u * 2f - 2f) + c2) + 2f) / 2f
        }
    }
    fun smoothstep(a: Float, b: Float, x: Float): Float { val u = clamp01((x - a) / (b - a)); return u * u * (3f - 2f * u) }

    /** [on, lift, loupe, loupeDx, loupeDy] at loop time t. */
    fun pose(t: Float, out: FloatArray) {
        val u1 = seg(t, 1.3f, 3.0f)
        val u2 = seg(t, 5.0f, 6.8f)
        out[0] = REST_ON + (1f - REST_ON) * (easeInOutBack(u1, BACK) - easeInOutBack(u2, BACK))
        out[1] = sin(PI.toFloat() * u1) + sin(PI.toFloat() * u2)
        out[2] = easeOutCubic(seg(t, 3.05f, 3.45f)) * (1f - easeInOutCubic(seg(t, 4.6f, 4.95f)))
        val w = seg(t, 3.0f, 5.0f)
        out[3] = WANDER_X * sin(2f * PI.toFloat() * w)
        out[4] = WANDER_Y * sin(4f * PI.toFloat() * w)
    }

    /** Pulled past a limit, the strip follows less and less (rubber band). */
    fun rubber(x: Float, lo: Float, hi: Float): Float {
        if (x < lo) { val o = lo - x; return lo - o / (1f + o / 60f) }
        if (x > hi) { val o = x - hi; return hi + o / (1f + o / 60f) }
        return x
    }

    /** CSS cubic-bezier easing (x1, y1, x2, y2), solved for x by Newton steps. */
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
internal class FilmNegativeAnim(var value: Float, private val eps: Float = 0.001f) {
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
                val u = FilmNegativeMotion.clamp01((elapsed - delay) / duration)
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

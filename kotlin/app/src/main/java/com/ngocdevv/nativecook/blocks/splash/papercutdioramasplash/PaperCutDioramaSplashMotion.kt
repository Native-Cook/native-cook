package com.ngocdevv.nativecook.blocks.splash.papercutdioramasplash

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

/*
 Paper Cut Diorama Splash — the timeline (a port of dioramaMotion.ts; LOOP = 9 s), a pure function
 of the scene time t. The rest pose is the finished diorama with the logo lit, so t = 0 and
 t = LOOP are the same frame and the screen is never blank.

   0.0 – 0.4   rest: the lit diorama, the lamp breathing ±6 % (three whole sines a loop).
   0.4 – 1.8   the scene sways once as if the phone were tilted (one whole sine, parallax by depth).
   1.8 – 3.0   the camera flies through the logo's door: every layer scales about the door, the
               front frame fastest (×40) and the sky slowest (×17), ease-in-out on a log scale;
               the lamp fills the screen and the home screen fades in over it (2.55 – 3.0, 1.04 → 1).
   3.0 – 5.0   home.
   5.0 – 5.6   home recedes (opacity 1 → 0, scale 1 → 0.96) while the six layers rise from below as
               one piece (5.0 – 6.2, ease-out-back), each layer behind the frame trailing it by up
               to 40 dp × (1 − depth) mid-rise.
   6.4 – 7.1   the lamp comes back on behind the door and the wordmark (ease-out).
   7.1 – 9.0   rest.
 */
internal class PaperCutPose {
    /** Per layer: rise (dp still below its place), sway x, sway y, scale. */
    val layers = FloatArray(PaperCutMotion.LAYERS * 4)
    var light = 1f
    var visible = 1f
    var home = 0f
    var homeScale = 1f
}

internal object PaperCutMotion {
    const val LOOP = 9f
    const val LAYERS = 6
    /** Depth of each layer for parallax and zoom: sky … frame. */
    val DEPTH = floatArrayOf(0.42f, 0.5f, 0.58f, 0.68f, 0.8f, 1f)
    private const val ZOOM_MAX = 40f
    private const val RISE_AT = 5.0f
    private const val RISE_LEN = 1.2f
    private const val RISE_LAG = 40f
    /** Where a finger takes the scene: the rest pose (t = 0, the same frame as t = LOOP). */
    const val REST_SCENE = 0f
    /** After this, a finger plays the rebuild forward to LOOP rather than back through home. */
    const val REBUILD_AT = RISE_AT

    fun clamp01(x: Float) = if (x < 0f) 0f else if (x > 1f) 1f else x
    fun seg(t: Float, a: Float, b: Float) = clamp01((t - a) / (b - a))
    private fun easeOutCubic(u: Float): Float { val v = 1f - u; return 1f - v * v * v }
    private fun easeInOutCubic(u: Float) = if (u < 0.5f) 4f * u * u * u else 1f - (-2f * u + 2f).pow(3) / 2f
    private fun easeOutBack(u: Float, c1: Float): Float { val c3 = c1 + 1f; return 1f + c3 * (u - 1f).pow(3) + c1 * (u - 1f).pow(2) }

    /** A finger's rubber band: follows 1:1 near zero, never past ±1.6. */
    fun rubber(v: Float) = v / (1f + abs(v) / 1.6f)

    fun pose(t: Float, riseDist: Float, p: PaperCutPose) {
        val rebuilding = t >= RISE_AT
        val zu = if (rebuilding) 0f else easeInOutCubic(seg(t, 1.8f, 3.0f))
        val s = exp(ln(ZOOM_MAX) * zu)
        val sway = sin(2f * PI.toFloat() * seg(t, 0.4f, 1.8f))
        val u = seg(t, RISE_AT, RISE_AT + RISE_LEN)
        for (i in 0 until LAYERS) {
            val d = DEPTH[i]
            val rise = if (rebuilding) (1f - easeOutBack(u, 0.9f)) * riseDist + (1f - d) * RISE_LAG * sin(PI.toFloat() * u) else 0f
            p.layers[i * 4] = rise
            p.layers[i * 4 + 1] = 16f * sway * (d - 0.66f) * 2.2f
            p.layers[i * 4 + 2] = -3f * sway * sway * (d - 0.4f)
            p.layers[i * 4 + 3] = 1f + (s - 1f) * d
        }
        val breath = 1f + 0.06f * sin((2f * PI.toFloat() * 3f * t) / LOOP)
        val on = if (rebuilding) easeOutCubic(seg(t, 6.4f, 7.1f)) else 1f
        p.light = on * breath * (if (rebuilding) 1f else 1f + 1.5f * seg(t, 2.0f, 2.8f))
        val homeIn = easeOutCubic(seg(t, 2.55f, 3.0f))
        val out = easeInOutCubic(seg(t, 5.0f, 5.6f))
        p.visible = if (t < 3.05f || rebuilding) 1f else 0f
        p.home = homeIn * (1f - out)
        p.homeScale = if (rebuilding) 1f - 0.04f * out else 1.04f - 0.04f * homeIn
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
internal class PaperCutAnim(var value: Float, private val eps: Float = 0.001f) {
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
                val u = PaperCutMotion.clamp01((elapsed - delay) / duration)
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

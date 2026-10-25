package com.ngocdevv.nativecook.blocks.media.slideprojectorgallery

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

/*
 Slide Projector Gallery — the timeline of one slide change and of the autoPlay loop (a port of
 projectorMotion.ts; the numbers are the same). Pure functions of time; the finger's changes use
 the same `change`, timed from the swipe.

 One change (u = 0 … 1 over CHANGE s, or FAST s for the quick ones):
   0.00 – 0.18  the lamp dims to dark (ease-in-out) while the lamp flickers.
   0.12 – 0.42  the tray turns one slot (ease-out-back, the clunk).
   0.30         the next slide is in the gate.
   0.33 – 0.62  it drops in from 34 dp below its place (ease-out-back), the lamp comes back.
   0.33 – 0.80  focus pull, blurry to sharp, then 0.72 – 0.95 a small breath past focus and back.

 autoPlay (LOOP = 9 s): rest on slide 1 0 – 1.4; 1 → 2 at 1.4 – 2.6; hold; 2 → 3 at 4.2 – 5.4;
 hold; back in two quick changes, 3 → 2 at 6.6 – 7.5 and 2 → 1 at 7.5 – 8.4; rest.
 */
internal class SlideProjectorPose {
    var slide = 0
    var tray = 0f
    var bright = 1f
    var drop = 0f
    var focus = 0f
    var flicker = 1f
    var scale = 1f
}

internal object SlideProjectorMotion {
    const val LOOP = 9f
    const val CHANGE = 1.2f
    const val FAST = 0.9f
    const val DROP = 34f
    const val TRAY_SLOTS = 24

    private val AUTO = arrayOf(
        floatArrayOf(1.4f, CHANGE, 0f, 1f),
        floatArrayOf(4.2f, CHANGE, 1f, 2f),
        floatArrayOf(6.6f, FAST, 2f, 1f),
        floatArrayOf(7.5f, FAST, 1f, 0f),
    )

    fun clamp01(x: Float) = if (x < 0f) 0f else if (x > 1f) 1f else x
    fun seg(t: Float, a: Float, b: Float) = clamp01((t - a) / (b - a))
    fun smoothstep(a: Float, b: Float, x: Float): Float { val u = clamp01((x - a) / (b - a)); return u * u * (3f - 2f * u) }
    private fun easeOutCubic(u: Float): Float { val v = 1f - u; return 1f - v * v * v }
    private fun easeInOutCubic(u: Float) = if (u < 0.5f) 4f * u * u * u else 1f - (-2f * u + 2f).pow(3) / 2f
    private fun easeOutBack(u: Float, c1: Float): Float { val c3 = c1 + 1f; return 1f + c3 * (u - 1f).pow(3) + c1 * (u - 1f).pow(2) }

    /** One change from slide `from` to `to` at progress u (0…1). */
    fun change(u: Float, from: Int, to: Int, p: SlideProjectorPose) {
        val dim = easeInOutCubic(seg(u, 0f, 0.18f))
        val back = easeOutCubic(seg(u, 0.33f, 0.62f))
        p.bright = 1f - dim + dim * back
        p.tray = from + (to - from) * easeOutBack(seg(u, 0.12f, 0.42f), 1.3f)
        p.drop = if (u < 0.33f) DROP else DROP * (1f - easeOutBack(seg(u, 0.33f, 0.62f), 1.1f))
        val pull = 1f - easeOutCubic(seg(u, 0.33f, 0.8f))
        val breath = 0.22f * sin(PI.toFloat() * seg(u, 0.72f, 0.95f))
        p.focus = if (u < 0.33f) 0.6f * easeInOutCubic(seg(u, 0.02f, 0.2f)) else pull + breath
        // A lamp that stutters while the slide is pulled and pushed (a fixed pattern, not random).
        val w = seg(u, 0.04f, 0.5f)
        val flick = if (w > 0f && w < 1f) 0.18f * max(0f, sin(37f * w) * sin(23f * w + 1.3f)) else 0f
        p.slide = if (u < 0.3f) from else to
        p.flicker = 1f - flick
        p.scale = 1f + 0.03f * min(1f, p.focus)
    }

    fun rest(slide: Int, p: SlideProjectorPose) {
        p.slide = slide; p.tray = slide.toFloat(); p.bright = 1f; p.drop = 0f; p.focus = 0f; p.flicker = 1f; p.scale = 1f
    }

    fun auto(t: Float, p: SlideProjectorPose) {
        for (a in AUTO) {
            if (t >= a[0] && t < a[0] + a[1]) { change((t - a[0]) / a[1], a[2].toInt(), a[3].toInt(), p); return }
        }
        var slide = 0
        for (a in AUTO) if (t >= a[0] + a[1]) slide = a[3].toInt()
        rest(slide, p)
    }

    private fun fractHash(x: Float): Float { val v = sin(x * 12.9898f) * 43758.5453f; return v - floor(v) }

    /** Dust mote i at loop time t into out[0..3]: x, y, radius, brightness, given the beam. */
    fun mote(i: Int, t: Float, lensX: Float, lensY: Float, topY: Float, halfTop: Float, out: FloatArray) {
        val h1 = fractHash(i * 1.37f + 0.11f)
        val h2 = fractHash(i * 2.91f + 0.53f)
        val h3 = fractHash(i * 4.17f + 0.29f)
        val k = 1 + floor(h3 * 2f)
        val s = (h1 + (k * t) / LOOP) % 1f
        val sway = 0.1f * sin(2f * PI.toFloat() * ((2f * t) / LOOP + h2))
        val lateral = (h2 * 2f - 1f) * 0.92f + sway
        val y = lensY - s * (lensY - topY)
        val bw = 10f + (halfTop - 10f) * s
        out[0] = lensX + lateral * bw
        out[1] = y
        out[2] = 0.6f + h3 * 1.5f
        val fade = smoothstep(0f, 0.12f, s) * (1f - smoothstep(0.85f, 1f, s))
        val tw = 0.6f + 0.4f * sin(2f * PI.toFloat() * ((3f * t) / LOOP + h1 * 7f))
        out[3] = fade * tw * (1f - 0.7f * abs(lateral))
    }

    /** The caption's roll position: the tray's slot, rolling over the middle 40 % of a change. */
    fun roll(tray: Float): Float { val base = floor(tray); return base + smoothstep(0.3f, 0.7f, tray - base) }
}

/**
 * One animated number for the finger's state: a spring (response in seconds and damping ratio,
 * as SwiftUI's `.spring(response:dampingFraction:)`, carrying a velocity) or a timed curve with an
 * optional delay. Stepped by the frame loop; nothing here is Compose state.
 */
internal class SlideProjectorAnim(var value: Float, private val eps: Float = 0.001f) {
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
                val u = SlideProjectorMotion.clamp01((elapsed - delay) / duration)
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

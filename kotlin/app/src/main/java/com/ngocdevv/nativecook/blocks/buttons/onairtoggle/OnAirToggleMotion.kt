package com.ngocdevv.nativecook.blocks.buttons.onairtoggle

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.pow
import kotlin.math.sign
import kotlin.math.sin
import kotlin.math.sqrt

/** Seconds and dp; all curves below are the analytic curves from OnAirToggle.tsx. */
internal object OnAirToggleMotionSpec {
    const val LOOP = 6f
    const val TRAVEL = 152.0
    const val SETTLE = 0.93
    const val HOLD = 2.0
    const val FADE = 0.3
}

internal data class OnAirToggleFrame(
    val pos: Double = 0.0,
    val glow: Double = 0.0,
    val room: Double = 0.0,
    val press: Double = 0.0,
    val timer: Double = 0.0,
)

internal fun onAirToggleClamp(x: Double): Double = x.coerceIn(0.0, 1.0)
internal fun onAirToggleMix(a: Double, b: Double, u: Double): Double = a + (b - a) * u
private fun onAirToggleSeg(t: Double, a: Double, b: Double) = onAirToggleClamp((t - a) / (b - a))
private fun onAirToggleEaseOutQuad(x: Double) = 1 - (1 - x) * (1 - x)
private fun onAirToggleEaseOutCubic(x: Double) = 1 - (1 - x).pow(3)
private fun onAirToggleEaseInOutCubic(x: Double) =
    if (x < 0.5) 4 * x * x * x else 1 - (-2 * x + 2).pow(3) / 2

internal fun onAirToggleInkMix(room: Double): Double {
    val u = onAirToggleClamp((room - 0.4) / 0.2)
    return u * u * (3 - 2 * u)
}

private fun onAirToggleHash(i: Int): Double {
    val s = sin(i * 12.9898) * 43758.5453
    return s - floor(s)
}

/** Unit step, damping ratio .72 and response .5 s; its tail is exactly zero by .78 s. */
private fun onAirToggleSpringUnit(s: Double): Double {
    if (s <= 0) return 0.0
    val zeta = 0.72
    val w = 2 * PI / 0.5
    val wd = w * sqrt(1 - zeta * zeta)
    val deviation = exp(-zeta * w * s) * (cos(wd * s) + zeta * w / wd * sin(wd * s))
    val tail = 1 - onAirToggleEaseInOutCubic(onAirToggleSeg(s, 0.6, 0.78))
    return 1 - deviation * tail
}

private fun onAirToggleIgnite(u: Double): Double = when {
    u <= 0 -> 0.0
    u < 0.06 -> 0.55 * (u / 0.06)
    u < 0.12 -> onAirToggleMix(0.55, 0.15, (u - 0.06) / 0.06)
    u < 0.18 -> onAirToggleMix(0.15, 0.75, (u - 0.12) / 0.06)
    u < 0.24 -> onAirToggleMix(0.75, 0.4, (u - 0.18) / 0.06)
    else -> onAirToggleMix(0.4, 1.0, onAirToggleEaseOutCubic(onAirToggleSeg(u, 0.24, 0.6)))
}

/** A pure loop frame, including the equal resting endpoints at t = 0 and t = 6. */
internal fun onAirToggleLoopAt(t: Double): OnAirToggleFrame {
    val pos = when {
        t >= 0.8 && t < 0.92 -> -0.03 * onAirToggleEaseOutQuad(onAirToggleSeg(t, 0.8, 0.92))
        t >= 0.92 && t < 3.9 -> -0.03 + 1.03 * onAirToggleSpringUnit(t - 0.92)
        t >= 3.9 && t < 4.0 -> 1 + 0.03 * onAirToggleEaseOutQuad(onAirToggleSeg(t, 3.9, 4.0))
        t >= 4.0 -> 1.03 - 1.03 * onAirToggleSpringUnit(t - 4.0)
        else -> 0.0
    }
    val pressA = onAirToggleEaseOutQuad(onAirToggleSeg(t, 0.8, 0.92)) *
        (1 - onAirToggleEaseOutQuad(onAirToggleSeg(t, 0.92, 1.12)))
    val pressB = onAirToggleEaseOutQuad(onAirToggleSeg(t, 3.9, 4.0)) *
        (1 - onAirToggleEaseOutQuad(onAirToggleSeg(t, 4.0, 4.2)))
    val breathe = onAirToggleSeg(t, 1.7, 2.0) * (1 - onAirToggleSeg(t, 3.7, 4.0))
    val pulse = 1 + 0.05 * breathe * sin(2 * PI * 5 * t / OnAirToggleMotionSpec.LOOP)
    val glow = onAirToggleIgnite(t - 1.1) *
        (1 - onAirToggleEaseInOutCubic(onAirToggleSeg(t, 4.1, 4.5))) * pulse
    val room = onAirToggleEaseInOutCubic(onAirToggleSeg(t, 1.15, 1.85)) *
        (1 - onAirToggleEaseInOutCubic(onAirToggleSeg(t, 4.15, 4.85)))
    return OnAirToggleFrame(pos, glow, room, pressA + pressB, if (room > 0.5) (t - 1.3).coerceAtLeast(0.0) else 0.0)
}

/** Seeded, six-second-periodic meter; use T = 0 under reduced motion. */
internal fun onAirToggleBarLevel(i: Int, time: Double): Double {
    val h = onAirToggleHash(i + 1)
    val envelope = 0.35 + 0.65 * sin(PI * (i + 0.5) / 32)
    val value = 0.5 +
        0.22 * sin(2 * PI * (7 * time / OnAirToggleMotionSpec.LOOP + h)) +
        0.16 * sin(2 * PI * (11 * time / OnAirToggleMotionSpec.LOOP + 2.3 * h + 0.21 * i)) +
        0.12 * sin(2 * PI * (17 * time / OnAirToggleMotionSpec.LOOP + 0.37 * i))
    return onAirToggleClamp(value) * envelope
}

/** Mutable clock bookkeeping lives outside Compose state; only the draw invalidation observes it. */
internal class OnAirToggleMotion {
    var frame = OnAirToggleFrame()
        private set
    var cross = 0.0
        private set
    var meterTime = 0.0
        private set

    private var elapsed = 0.0
    private var origin = 0.0
    private var mode = 0 // 0 autoplay/rest; 1 dragging; 2 settling/holding.
    private var released = 0.0
    private var target = 0.0
    private var from = OnAirToggleFrame()
    private var fromCross = 0.0
    private var onSince = 0.0
    private var timerOffset = 0.0
    private var grab = 0.0
    private var dragTimerStarted = false

    fun advance(deltaSeconds: Double, autoPlay: Boolean, reduced: Boolean) {
        val dt = deltaSeconds.coerceIn(0.0, 0.05)
        val now = elapsed + dt
        elapsed = now
        meterTime = now - origin

        if (reduced) {
            val u = if (mode == 2) onAirToggleClamp((now - released) / OnAirToggleMotionSpec.FADE) else 1.0
            val v = onAirToggleMix(fromCross, target, u)
            cross = v
            frame = OnAirToggleFrame(v, v, v, 0.0, if (v > 0.5) now - onSince else 0.0)
            return
        }
        if (mode == 1) {
            val k = (dt * 10).coerceAtMost(1.0)
            val glow = onAirToggleMix(frame.glow, onAirToggleEaseInOutCubic(onAirToggleSeg(frame.pos, 0.5, 0.95)), k)
            val room = onAirToggleMix(frame.room, onAirToggleEaseInOutCubic(onAirToggleSeg(frame.pos, 0.45, 1.0)), k)
            val press = onAirToggleMix(frame.press, 0.6, k)
            if (room > 0.5 && !dragTimerStarted) {
                onSince = now
                dragTimerStarted = true
            } else if (room <= 0.5) {
                dragTimerStarted = false
            }
            frame = OnAirToggleFrame(frame.pos, glow, room, press, if (room > 0.5) now - onSince else 0.0)
            return
        }
        if (mode == 2) {
            val s = now - released
            val pos = from.pos + (target - from.pos) * onAirToggleSpringUnit(s)
            val press = frame.press * (1 - dt * 8).coerceAtLeast(0.0)
            val glow: Double
            val room: Double
            if (target == 1.0) {
                glow = from.glow + (1 - from.glow) * onAirToggleIgnite(s - 0.18)
                room = from.room + (1 - from.room) * onAirToggleEaseInOutCubic(onAirToggleSeg(s, 0.23, 0.93))
            } else {
                glow = from.glow * (1 - onAirToggleEaseInOutCubic(onAirToggleSeg(s, 0.1, 0.5)))
                room = from.room * (1 - onAirToggleEaseInOutCubic(onAirToggleSeg(s, 0.15, 0.85)))
            }
            frame = OnAirToggleFrame(pos, glow, room, press, if (room > 0.5) now - onSince else 0.0)
            if (s > OnAirToggleMotionSpec.SETTLE + OnAirToggleMotionSpec.HOLD && autoPlay) {
                val resumeAt = if (target == 1.0) 1.85 else 4.85
                origin = now - resumeAt
                timerOffset = if (target == 1.0) frame.timer - (resumeAt - 1.3) else 0.0
                mode = 0
            }
            return
        }
        if (!autoPlay) {
            frame = OnAirToggleFrame()
            return
        }
        val loop = OnAirToggleMotionSpec.LOOP.toDouble()
        val state = onAirToggleLoopAt(((now - origin) % loop + loop) % loop)
        if (state.room < 0.5) timerOffset = 0.0
        frame = state.copy(timer = if (state.timer > 0) state.timer + timerOffset else 0.0)
    }

    fun beginDrag() {
        grab = frame.pos
        dragTimerStarted = frame.room > 0.5
        if (frame.room > 0.5) onSince = elapsed - frame.timer
        mode = 1
    }

    /** Translation is measured from the pointer-down point, just like the RN pan translationX. */
    fun drag(translationDp: Double) {
        val raw = grab + translationDp / OnAirToggleMotionSpec.TRAVEL
        val over = when {
            raw > 1 -> raw - 1
            raw < 0 -> raw
            else -> 0.0
        }
        val band = if (over == 0.0) 0.0 else sign(over) * abs(over) * 0.35 / (1 + abs(over) * 3)
        frame = frame.copy(pos = if (over == 0.0) raw else (if (raw > 1) 1.0 else 0.0) + band)
    }

    fun settle(to: Double, reduced: Boolean) {
        val wasOn = frame.room > 0.5
        from = frame
        fromCross = cross
        target = to
        released = elapsed
        if (to == 1.0 && !wasOn) onSince = elapsed + if (reduced) 0.0 else 0.2
        if (to == 1.0 && wasOn) onSince = elapsed - frame.timer
        mode = 2
    }

    /** A live reduced-motion preference starts at the same legible off state as initial mounting. */
    fun resetReducedMotion() {
        frame = OnAirToggleFrame()
        cross = 0.0
        target = 0.0
        from = frame
        fromCross = 0.0
        mode = 0
        origin = elapsed
        timerOffset = 0.0
        meterTime = 0.0
    }

    /** Stop the clock once an off manual state is visually at rest; an on timer keeps ticking. */
    fun needsFrames(autoPlay: Boolean, reduced: Boolean): Boolean = when {
        reduced -> frame.room > 0.5 || (mode == 2 && elapsed - released < OnAirToggleMotionSpec.FADE)
        autoPlay || mode == 1 -> true
        mode == 2 -> target == 1.0 || elapsed - released < OnAirToggleMotionSpec.SETTLE || frame.press > 0.0001
        else -> false
    }
}

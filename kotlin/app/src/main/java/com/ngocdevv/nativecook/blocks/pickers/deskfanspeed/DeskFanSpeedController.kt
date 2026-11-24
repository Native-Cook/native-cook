package com.ngocdevv.nativecook.blocks.pickers.deskfanspeed

import androidx.compose.animation.core.CubicBezierEasing
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

private val EASE_OUT = CubicBezierEasing(0.23f, 1f, 0.32f, 1f)
private val EASE_IN_OUT = CubicBezierEasing(0.65f, 0f, 0.35f, 1f)

/**
 * One animated number as a closed form of the frame time (ms): still, one eased leg (withTiming) or a spring
 * (withSpring, mass 1). Reading it never advances anything, so the Compose layers and the stage agree for a frame.
 */
private class FanTrack(initial: Double, stiffness: Double = 150.0, damping: Double = 18.0) {
    private val z = damping / 2
    private val w = sqrt(stiffness - z * z)
    private var still = initial
    private var sprung = false
    private var springTo = initial
    private var springFrom = initial
    private var springV = 0.0
    private var springStart = 0.0
    private var timed = false
    private var from = 0.0
    private var to = 0.0
    private var start = 0.0
    private var ms = 0.0
    private var ease: CubicBezierEasing = EASE_OUT

    fun value(now: Double): Double {
        if (sprung) {
            val s = max(0.0, (now - springStart) / 1000)
            val d = springFrom - springTo
            return springTo + exp(-z * s) * (d * cos(w * s) + (springV + z * d) / w * sin(w * s))
        }
        if (!timed) return still
        if (now <= start) return from
        if (now >= start + ms) return to
        return from + (to - from) * ease.transform(((now - start) / ms).toFloat())
    }

    fun set(v: Double) {
        sprung = false
        timed = false
        still = v
    }

    /** cancelAnimation: it stays where it is. */
    fun cancel(now: Double) = set(value(now))

    fun spring(now: Double, target: Double, velocity: Double) {
        springFrom = value(now)
        springTo = target
        springV = velocity
        springStart = now
        sprung = true
        timed = false
    }

    fun timing(now: Double, target: Double, duration: Double, easing: CubicBezierEasing) {
        from = value(now)
        to = target
        start = now
        ms = duration
        ease = easing
        sprung = false
        timed = true
    }
}

/**
 * The block's state for one frame time (DeskFanSpeed.tsx's frame callback: the film, the motor's inertia after a
 * finger's level, the aim and the return). Main thread only. `frame(now)` may be called several times for one display
 * frame: it advances once.
 */
internal class DeskFanSpeedController(autoPlay: Boolean, private val reduced: Boolean) {
    private val film = !reduced && autoPlay

    private val turns = FanTrack(0.0)
    private val speed = FanTrack(0.0)
    private val yaw = FanTrack(FanMotion.YAW_REST, AIM_K, AIM_C)

    private var mode = if (film) MODE_AUTO else MODE_MANUAL
    private var loopStart = -1.0
    private var now = -1.0
    private var readyAt = -1.0
    private var lastTouch = -1.0
    private var returnAt = -1.0
    private var dragging = false
    private var dragFrom = 0.0

    /** The film's time (s, 0..LOOP). */
    var t = 0.0
        private set

    /** The selected level, 0 = Off .. 3. Reduced motion's still shows level 2. */
    var level = if (reduced) REDUCED_LEVEL else 0
        private set

    /** How far each key is down (0..1). */
    val keys = if (reduced) doubleArrayOf(0.0, 0.0, 1.0, 0.0) else doubleArrayOf(1.0, 0.0, 0.0, 0.0)

    var turnsNow = 0.0
        private set
    var speedNow = 0.0
        private set
    var yawNow = FanMotion.YAW_REST
        private set
    var shown = 0.0
        private set
    val filming: Boolean get() = mode == MODE_AUTO

    fun markReady() {
        if (readyAt < 0) readyAt = max(now, 0.0)
    }

    fun frame(nowMs: Double) {
        if (nowMs > now) {
            val gap = if (now < 0) 0.0 else nowMs - now
            now = nowMs
            step(gap)
        }
        turnsNow = turns.value(now)
        speedNow = speed.value(now)
        yawNow = yaw.value(now)
        shown = if (readyAt < 0) 0.0 else easeInOutQuad(FanMotion.clamp01((now - readyAt) / FADE_MS))
    }

    private fun step(gap: Double) {
        if (mode == MODE_AUTO) {
            if (!film) {
                mode = MODE_MANUAL
                return
            }
            if (readyAt < 0) {
                t = 0.0
                loopStart = -1.0
                return
            }
            if (loopStart < 0) {
                loopStart = now
            } else if (gap > STALL_MS) {
                // the thread was held up: the film picks up where it was instead of skipping what the stall covered
                loopStart += gap - FRAME_MS
            }
            var el = (now - loopStart) / 1000
            if (el >= FanMotion.LOOP) {
                loopStart += FanMotion.LOOP * 1000
                el -= FanMotion.LOOP
            }
            t = el
            // the blade angle is a pure function of the loop's time, with a whole number of turns per loop
            turns.set(FanMotion.filmTurns(el))
            speed.set(FanMotion.filmSpeed(el))
            yaw.set(FanMotion.filmYaw(el))
            for (i in 0 until 4) keys[i] = FanMotion.filmKey(el, i)
            level = FanMotion.filmLevel(el)
            return
        }
        if (mode == MODE_RETURN) {
            if (now - returnAt >= RETURN_MS) {
                turns.set(0.0)
                speed.set(0.0)
                t = 0.0
                loopStart = -1.0
                mode = MODE_AUTO
            }
            return
        }
        // the motor has inertia: the speed chases the level, and the blades integrate it
        val dt = min(0.05, gap / 1000)
        if (!reduced && dt > 0) {
            val target = FanMotion.SPEEDS[level]
            val s = speed.value(now)
            val tau = if (target > s) FanMotion.SPIN_UP_TAU else FanMotion.SPIN_DOWN_TAU
            val next = target + (s - target) * exp(-dt / tau)
            val v = if (abs(next - target) < 0.002) target else next
            speed.set(v)
            if (v > 0) turns.set(turns.value(now) + v * dt)
            val blend = 1 - exp(-dt * KEY_RATE * 3)
            for (i in 0 until 4) keys[i] += ((if (level == i) 1.0 else 0.0) - keys[i]) * blend
        }
        if (lastTouch < 0 || dragging) {
            lastTouch = now
            return
        }
        if (!film || now - lastTouch < IDLE_MS) return
        // 2 s after the last touch: Off, the blades coast to a stop on a quarter turn (the four blades look the same),
        // the head comes home, and the film restarts from t = 0
        mode = MODE_RETURN
        returnAt = now
        level = 0
        keys[0] = 1.0
        keys[1] = 0.0
        keys[2] = 0.0
        keys[3] = 0.0
        val coast = turns.value(now) + speed.value(now) * 0.42
        val stop = ceil(coast * 4) / 4
        turns.timing(now, stop, RETURN_MS - 60, EASE_OUT)
        speed.timing(now, 0.0, RETURN_MS - 60, EASE_OUT)
        yaw.timing(now, FanMotion.YAW_REST, RETURN_MS - 60, EASE_IN_OUT)
    }

    /** A touch takes over from wherever the film (or the return) is. */
    private fun pause() {
        if (now < 0) now = System.nanoTime() / 1e6
        if (mode == MODE_RETURN) {
            turns.cancel(now)
            speed.cancel(now)
            yaw.cancel(now)
        }
        mode = MODE_MANUAL
        lastTouch = -1.0
    }

    /** A finger picked a level. */
    fun userLevel(v: Int) {
        pause()
        level = v
        if (reduced) for (i in 0 until 4) keys[i] = if (i == v) 1.0 else 0.0
    }

    fun dragBegan() {
        pause()
        dragging = true
        yaw.cancel(now)
        dragFrom = yaw.value(now)
    }

    /** dx: the finger's whole horizontal travel (dp) since it touched down. */
    fun dragMoved(dx: Double, width: Double) {
        if (dragging) yaw.set(FanMotion.aimFromDrag(dragFrom, dx, width))
    }

    /** Let go: it stays where it is aimed, inside its range, landed by a spring with the finger's speed. */
    fun dragEnded(velocityX: Double, width: Double) {
        if (!dragging) return
        val v = velocityX / width * FanMotion.AIM_SPAN
        val to = max(-FanMotion.AIM_MAX, min(FanMotion.AIM_MAX, yaw.value(now) + v * 0.12))
        if (reduced) yaw.set(to) else yaw.spring(now, to, v)
    }

    fun dragFinalized() {
        dragging = false
    }

    private fun easeInOutQuad(x: Double): Double = if (x < 0.5) 2 * x * x else 1 - (2 - 2 * x) * (2 - 2 * x) / 2

    private companion object {
        const val MODE_AUTO = 0
        const val MODE_MANUAL = 1
        const val MODE_RETURN = 2
        const val IDLE_MS = 2000.0
        const val RETURN_MS = 1300.0
        const val STALL_MS = 100.0
        const val FRAME_MS = 1000.0 / 60
        const val KEY_RATE = 11.0
        const val AIM_K = 150.0
        const val AIM_C = 18.0
        const val FADE_MS = 320.0
        const val REDUCED_LEVEL = 2
    }
}

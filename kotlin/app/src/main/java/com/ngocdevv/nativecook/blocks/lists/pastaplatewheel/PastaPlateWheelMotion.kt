package com.ngocdevv.nativecook.blocks.lists.pastaplatewheel

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Time and touch model of the wheel. Everything on screen is a function of φ, the wheel's
 * position in plate steps (φ = 2 puts dish 2 at the front), and φ is a function of the clock.
 */
internal object PastaPlateWheelMotion {
    const val LOOP = 8.0
    /** Seconds after the last touch before the wheel springs back to the first dish. */
    const val IDLE = 2.0
    /** Duration of that return; the loop restarts at its end. */
    const val BACK = 1.6
    /** Drag distance (design units) that turns the wheel by one plate. */
    const val STEP_POINTS = 160.0
    const val COUNT = 8
    const val CENTRE_X = 14.0
    const val ORBIT = 226.0
    const val DISC_RADIUS = 242.0

    /** Rounds half away from zero, like Swift's `rounded()`. */
    fun round(x: Double): Double = if (x >= 0) floor(x + 0.5) else -floor(-x + 0.5)

    /** Step response of a damped spring (response = period in s); exactly `to` after `settle` s. */
    fun spring(t: Double, from: Double, to: Double, velocity: Double = 0.0, response: Double, damping: Double, settle: Double): Double {
        if (t <= 0) return from
        if (t >= settle) return to
        val w = 2 * PI / response
        val zw = damping * w
        val wd = w * sqrt(1 - damping * damping)
        val a = from - to
        val b = (velocity + zw * a) / wd
        return to + exp(-zw * t) * (a * cos(wd * t) + b * sin(wd * t))
    }

    /** The loop: rest on A, turn to B, hold, turn to C, hold, turn back to A, rest. */
    fun loopPhi(time: Double): Double {
        val t = time - floor(time / LOOP) * LOOP
        if (t < 1.0) return 0.0
        if (t < 2.9) return spring(t - 1.0, 0.0, 1.0, response = 0.6, damping = 0.7, settle = 1.5)
        if (t < 4.8) return spring(t - 2.9, 1.0, 2.0, response = 0.6, damping = 0.7, settle = 1.5)
        return spring(t - 4.8, 2.0, 0.0, response = 0.8, damping = 0.78, settle = 1.6)
    }

    /** d wrapped into [-COUNT/2, COUNT/2). */
    fun wrap(d: Double): Double {
        val n = COUNT.toDouble()
        val m = (d + n / 2) - floor((d + n / 2) / n) * n
        return m - n / 2
    }

    class Plate(val index: Int, val x: Double, val y: Double, val size: Double, val front: Double, val spin: Double)

    /** The eight plates for φ, ordered back to front. */
    fun plates(phi: Double, cy: Double, popIndex: Int = -1, pop: Double = 1.0): List<Plate> =
        (0 until COUNT).map { i ->
            val d = wrap(i - phi)
            val theta = d * PI / 4
            val f = exp(-2.2 * d * d)
            var size = 84 + 80 * exp(-1.1 * d * d)
            if (i == popIndex) size *= pop
            val r = ORBIT + 22 * f
            Plate(i, CENTRE_X + r * cos(theta), cy + r * sin(theta), size, f, 3 * theta)
        }.sortedBy { it.front }

    fun pop(t: Double): Double = if (t < 0 || t > 1.2) 1.0 else 1 + 0.1 * exp(-5 * t) * sin(13 * t)

    fun infoTop(height: Double, safeBottom: Double) = height - safeBottom - 160

    fun centreY(safeTop: Double, height: Double, safeBottom: Double) = (safeTop + 60 + infoTop(height, safeBottom)) / 2
}

/** Touch state read by the frame clock; plain fields, so writing them never recomposes. */
internal class PastaPlateWheelTouch {
    private val m = PastaPlateWheelMotion
    enum class Feedback { None, Snap, Pop }

    var touching = false
    var live = 0.0
    private var startPhi = 0.0
    private var downClock = 0.0
    private var moved = 0.0
    private var detent = 0
    var release: Double? = null
    private var from = 0.0
    private var velocity = 0.0
    private var target = 0.0
    private var home = 0.0
    var popClock = -10.0
    var popIndex = -1

    fun phi(clock: Double, autoPlay: Boolean): Double {
        if (touching) return live
        val r = release ?: return if (autoPlay) m.loopPhi(clock) else 0.0
        val dt = clock - r
        if (dt < m.IDLE) return m.spring(dt, from, target, velocity, 0.55, 0.78, 1.6)
        val b = dt - m.IDLE
        if (b < m.BACK) return m.spring(b, target, home, 0.0, 0.8, 0.78, m.BACK)
        return if (autoPlay) home + m.loopPhi(b - m.BACK) else home
    }

    val restsAfter: Double get() = (release ?: 0.0) + m.IDLE + m.BACK + 0.2

    fun begin(clock: Double, autoPlay: Boolean) {
        val current = phi(clock, autoPlay)
        touching = true
        live = current
        startPhi = current
        downClock = clock
        moved = 0.0
        detent = m.round(current).toInt()
    }

    /** Returns true when the nearest plate changed (a detent haptic). */
    fun move(dx: Double, dy: Double): Boolean {
        moved = maxOf(moved, sqrt(dx * dx + dy * dy))
        if (moved < 8) return false
        live = startPhi - dy / m.STEP_POINTS
        val d = m.round(live).toInt()
        if (d != detent) {
            detent = d
            return true
        }
        return false
    }

    fun end(clock: Double, velocityY: Double, x: Double, y: Double, cy: Double): Feedback {
        var feedback = Feedback.Snap
        if (moved < 8 && clock - downClock < 0.5) {
            velocity = 0.0
            target = m.round(live)
            val hit = m.plates(live, cy).reversed().firstOrNull { p ->
                sqrt((p.x - x) * (p.x - x) + (p.y - y) * (p.y - y)) < p.size / 2
            }
            if (hit != null) {
                val delta = m.wrap(hit.index - live)
                if (abs(delta) < 0.5) {
                    popClock = clock
                    popIndex = hit.index
                    feedback = Feedback.Pop
                } else {
                    target = m.round(live + delta)
                }
            } else {
                feedback = Feedback.None
            }
        } else {
            velocity = (-velocityY / m.STEP_POINTS).coerceIn(-12.0, 12.0)
            val nearest = m.round(live)
            target = m.round(live + 0.22 * velocity).coerceIn(nearest - 3, nearest + 3)
        }
        from = live
        release = clock
        home = 8 * m.round(target / 8)
        touching = false
        return feedback
    }
}

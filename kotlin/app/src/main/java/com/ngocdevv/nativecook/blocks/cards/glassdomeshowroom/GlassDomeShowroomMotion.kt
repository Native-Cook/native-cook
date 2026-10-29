package com.ngocdevv.nativecook.blocks.cards.glassdomeshowroom

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Time and touch model of the showroom. Everything on screen is a function of φ, the carousel's
 * position in cars (φ = 2 puts car 2 in the middle; it wraps every [COUNT] cars), and φ is a
 * function of the clock.
 */
internal object GlassDomeShowroomMotion {
    const val LOOP = 10.0
    const val COUNT = 5
    /** Distance (pt) between two jar centres; a drag of this length moves the carousel one car. */
    const val SPACING = 272.0
    /** Seconds after the last touch before the carousel springs back to the first car. */
    const val IDLE = 2.0
    /** Duration of that return; the loop restarts at its end. */
    const val BACK = 1.6
    /** Start of each step A→B, B→C, C→D, D→E, E→A in the loop. */
    val STEPS = doubleArrayOf(1.5, 3.2, 5.3, 7.0, 8.7)
    const val STEP_SETTLE = 0.85
    /** The loop likes car C (index 2) at this time. */
    const val HEART_INDEX = 2
    const val HEART_AT = 4.3

    // Layout in the 393-wide design space.
    const val CENTRE_X = 196.5
    const val JAR_WIDTH = 260.0
    const val JAR_HEIGHT = 318.0
    const val JAR_RADIUS = 64.0
    const val CAR_WIDTH = 248.0
    const val HEADLINE_PITCH = 66.0

    /** The heart's centre in a jar's local space (origin at the floor line under the jar's middle). */
    const val HEART_X = JAR_WIDTH / 2 - 24 - 19
    const val HEART_Y = 4 - JAR_HEIGHT + 30 + 19

    fun floorY(height: Double, safeBottom: Double) = height - safeBottom - 136

    fun clamp(x: Double, lo: Double, hi: Double) = min(max(x, lo), hi)

    /** Rounds halves away from zero, like Swift's `rounded()`. */
    fun round(x: Double): Double = if (x >= 0) floor(x + 0.5) else -floor(-x + 0.5)

    /**
     * Step response of a damped spring (response = period in s), from [from] with [velocity]
     * (units/s); exactly [to] after [settle] s.
     */
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

    fun loopTime(time: Double) = time - floor(time / LOOP) * LOOP

    /** The loop: rest on A, then one car every ~1.9 s, all the way round back to A. */
    fun loopPhi(time: Double): Double {
        val t = loopTime(time)
        var phi = 0.0
        for (i in STEPS.indices) {
            if (t >= STEPS[i]) phi = spring(t - STEPS[i], i.toDouble(), (i + 1).toDouble(), 0.0, 0.55, 0.8, STEP_SETTLE)
        }
        return if (phi == COUNT.toDouble()) 0.0 else phi
    }

    /** d wrapped into [-COUNT/2, COUNT/2). */
    fun wrap(d: Double): Double {
        val n = COUNT.toDouble()
        val m = (d + n / 2) - floor((d + n / 2) / n) * n
        return m - n / 2
    }

    fun index(phi: Double): Int = Math.floorMod(round(phi).toInt(), COUNT)

    class Jar(val index: Int, val d: Double, val x: Double, val scale: Double)

    /** The visible jars for φ, ordered back to front. */
    fun jars(phi: Double): List<Jar> {
        val out = ArrayList<Jar>(COUNT)
        for (i in 0 until COUNT) {
            val d = wrap(i - phi)
            val a = abs(d)
            val scale = 1 - 0.16 * min(a, 1.0) - 0.04 * max(a - 1, 0.0)
            val x = CENTRE_X + d * SPACING
            if (abs(x - CENTRE_X) > CENTRE_X + JAR_WIDTH * scale / 2 + 4) continue
            out.add(Jar(i, d, x, scale))
        }
        out.sortByDescending { abs(it.d) }
        return out
    }

    /** Scale of a heart popped [t] s ago. */
    fun heartScale(t: Double): Double {
        if (t < 0 || t > 1.2) return 1.0
        return 1 + 0.6 * exp(-7 * t) * sin(11 * t)
    }
}

/** Touch state, read by the frame clock; a plain class, so writing it never recomposes. */
internal class GlassDomeShowroomTouch {
    private val m = GlassDomeShowroomMotion
    enum class Feedback { None, Snap, Like, Unlike }

    var touching = false
    var live = 0.0
    /** Drag speed in cars per second while the finger is down. */
    var dragVelocity = 0.0
    private var startPhi = 0.0
    private var downClock = 0.0
    private var lastClock = 0.0
    private var lastPhi = 0.0
    private var moved = 0.0
    private var detent = 0
    var release: Double? = null
    private var from = 0.0
    private var velocity = 0.0
    private var target = 0.0
    private var home = 0.0
    val likes = BooleanArray(GlassDomeShowroomMotion.COUNT)
    var popClock = -10.0
    var popIndex = -1

    /** φ at a clock time (seconds since the view appeared). */
    fun phi(clock: Double, autoPlay: Boolean): Double {
        if (touching) return live
        val r = release
        if (r != null) {
            val dt = clock - r
            if (dt < m.IDLE) return m.spring(dt, from, target, velocity, 0.55, 0.8, 1.4)
            val b = dt - m.IDLE
            if (b < m.BACK) return m.spring(b, target, home, 0.0, 0.8, 0.8, m.BACK)
            return if (autoPlay) home + m.loopPhi(b - m.BACK) else home
        }
        return if (autoPlay) m.loopPhi(clock) else 0.0
    }

    /** The loop's own time when the loop is what moves the carousel, else null. */
    fun loopTime(clock: Double, autoPlay: Boolean): Double? {
        if (!autoPlay || touching) return null
        val r = release
        if (r != null) {
            val b = clock - r - m.IDLE - m.BACK
            return if (b >= 0) m.loopTime(b) else null
        }
        return m.loopTime(clock)
    }

    /** Clock time after which nothing moves any more (outside autoPlay). */
    val restsAfter: Double get() = max((release ?: 0.0) + m.IDLE + m.BACK, popClock + 1.2) + 0.2

    fun liked(i: Int, clock: Double, autoPlay: Boolean): Boolean {
        if (likes[i]) return true
        if (i == m.HEART_INDEX) {
            val t = loopTime(clock, autoPlay) ?: return false
            return t >= m.HEART_AT
        }
        return false
    }

    /** Seconds since car i's heart popped (large when it did not). */
    fun popAge(i: Int, clock: Double, autoPlay: Boolean): Double {
        if (i == popIndex && clock - popClock < 1.2) return clock - popClock
        if (i == m.HEART_INDEX && !likes[i]) {
            val t = loopTime(clock, autoPlay)
            if (t != null) return t - m.HEART_AT
        }
        return 10.0
    }

    fun begin(clock: Double, autoPlay: Boolean) {
        val current = phi(clock, autoPlay)
        touching = true
        live = current
        startPhi = current
        downClock = clock
        lastClock = clock
        lastPhi = current
        dragVelocity = 0.0
        moved = 0.0
        detent = m.round(current).toInt()
    }

    /** Returns true when the car in the middle changed (a detent haptic). */
    fun move(dx: Double, dy: Double, clock: Double): Boolean {
        moved = max(moved, sqrt(dx * dx + dy * dy))
        if (moved < 8) return false
        live = startPhi - dx / m.SPACING
        val dt = clock - lastClock
        if (dt > 0.004) {
            dragVelocity = 0.6 * dragVelocity + 0.4 * (live - lastPhi) / dt
            lastClock = clock
            lastPhi = live
        }
        val d = m.round(live).toInt()
        if (d != detent) {
            detent = d
            return true
        }
        return false
    }

    /** [px], [py] are in the design space; [floorY] is the floor line. */
    fun end(clock: Double, velocityX: Double, px: Double, py: Double, floorY: Double): Feedback {
        var feedback = Feedback.Snap
        dragVelocity = 0.0
        if (moved < 8 && clock - downClock < 0.5) {
            velocity = 0.0
            target = m.round(live)
            feedback = Feedback.None
            for (jar in m.jars(live).reversed()) {
                val s = jar.scale
                val hx = jar.x + m.HEART_X * s
                val hy = floorY + m.HEART_Y * s
                if (abs(jar.d) < 0.5 && (px - hx) * (px - hx) + (py - hy) * (py - hy) < 30.0 * 30.0) {
                    likes[jar.index] = !likes[jar.index]
                    if (likes[jar.index]) {
                        popClock = clock
                        popIndex = jar.index
                        feedback = Feedback.Like
                    } else {
                        if (popIndex == jar.index) popIndex = -1
                        feedback = Feedback.Unlike
                    }
                    break
                }
                val half = m.JAR_WIDTH * s / 2
                if (px > jar.x - half && px < jar.x + half && py > floorY + (4 - m.JAR_HEIGHT) * s && py < floorY + 40) {
                    if (abs(jar.d) >= 0.5) {
                        target = m.round(live + jar.d)
                        feedback = Feedback.Snap
                    }
                    break
                }
            }
        } else {
            velocity = m.clamp(-velocityX / m.SPACING, -12.0, 12.0)
            val nearest = m.round(live)
            target = m.clamp(m.round(live + 0.22 * velocity), nearest - 2, nearest + 2)
        }
        from = live
        release = clock
        home = m.COUNT * m.round(target / m.COUNT)
        touching = false
        return feedback
    }
}

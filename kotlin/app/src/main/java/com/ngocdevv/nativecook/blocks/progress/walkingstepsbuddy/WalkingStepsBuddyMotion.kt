package com.ngocdevv.nativecook.blocks.progress.walkingstepsbuddy

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToLong
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Time model of Walking Steps Buddy. Two clocks drive the screen:
 * - D, the distance walked (pt): the parallax layers and the walk cycle are functions of D.
 * - the loop time t (0 until LOOP): the step count, the bar, the milestone pop and the sun.
 * In autoPlay D = SPEED · t, so one loop is exactly one tile of the near layer and twelve strides.
 */
internal object WalkingStepsBuddyMotion {
    const val LOOP = 9.0
    const val SPEED = 96.0
    const val TILE = 864.0
    const val STRIDE = 72.0
    const val IDLE = 2.0

    const val HILL_FACTOR = 0.2
    const val HILL_TILE = 172.8
    const val BUSH_FACTOR = 0.5
    const val BUSH_TILE = 432.0
    const val FRONT_FACTOR = 1.25
    const val FRONT_TILE = 360.0
    const val CLOUD_DRIFT = 72.0

    val counts = intArrayOf(0, 164, 352, 538, 712, 905, 1093, 1270, 1462, 1641, 1838, 2016, 2203, 2391, 2570, 2764, 2952, 3146, 0)
    const val FIRST_FOOTFALL = 1.125
    const val FOOTFALL_GAP = 0.375
    const val ROLL_TIME = 0.24
    const val RESET_START = 7.8
    const val RESET_END = 8.4
    val milestones = intArrayOf(1000, 3000, 6000, 10000)
    val milestoneLabels = listOf("1k steps", "3k steps", "6k steps", "10k steps")
    val rewards = listOf("+10", "+30", "+60", "+100")
    const val BAR_LEFT = 20.0
    const val BAR_RIGHT = 373.0
    val tickX = DoubleArray(4) { 20 + 353 * (it + 0.5) / 4 }
    const val MASCOT_X = 176.0

    val countStrings: List<String> = counts.map { String.format(java.util.Locale.US, "%,d", it) }

    fun clamp(x: Double, lo: Double = 0.0, hi: Double = 1.0) = min(max(x, lo), hi)
    fun seg(t: Double, a: Double, b: Double) = clamp((t - a) / (b - a))
    fun easeOut(x: Double) = 1 - (1 - x).pow(3)
    fun easeInOut(x: Double) = if (x < 0.5) 4 * x * x * x else 1 - (-2 * x + 2).pow(3) / 2
    fun backOut(x: Double): Double {
        val c1 = 1.70158
        val c3 = c1 + 1
        return 1 + c3 * (x - 1).pow(3) + c1 * (x - 1).pow(2)
    }
    fun smooth(a: Double, b: Double, x: Double): Double {
        val u = clamp((x - a) / (b - a))
        return u * u * (3 - 2 * u)
    }
    fun fract(x: Double) = x - floor(x)
    fun mod(x: Double, m: Double) = x - floor(x / m) * m
    fun hash(i: Double) = fract(sin(i * 12.9898) * 43758.5453)

    fun footfall(k: Int) = FIRST_FOOTFALL + FOOTFALL_GAP * (k - 1)

    /** Drum position of the count: k + roll progress (0 … 18; 18 shows the next day's 0). */
    fun countPhase(t: Double): Double {
        var p = 0.0
        for (k in 1..17) p += easeOut(seg(t, footfall(k), footfall(k) + ROLL_TIME))
        return p + easeInOut(seg(t, RESET_START, RESET_END))
    }

    fun steps(phase: Double): Double {
        val i = floor(phase).toInt()
        if (i >= counts.size - 1) return 0.0
        val f = phase - i
        return counts[i] + (counts[i + 1] - counts[i]) * f
    }

    private fun crossTime(milestone: Int): Double? {
        for (k in 1..17) if (counts[k] >= milestone) return footfall(k) + 0.05
        return null
    }
    val crossTimes: List<Double?> = milestones.map { crossTime(it) }

    fun tickPop(i: Int, t: Double): Double {
        val c = crossTimes[i] ?: return 0.0
        return backOut(seg(t, c, c + 0.4)) * (1 - easeInOut(seg(t, RESET_START, RESET_START + 0.5)))
    }

    fun pillMix(i: Int, t: Double): Double {
        val c = crossTimes[i] ?: return 0.0
        return easeOut(seg(t, c + 0.1, c + 0.35)) * (1 - easeInOut(seg(t, RESET_START, RESET_START + 0.5)))
    }

    fun barX(s: Double): Double {
        if (s <= 0) return BAR_LEFT
        var prevS = 0.0
        var prevX = BAR_LEFT
        for (i in 0 until 4) {
            val m = milestones[i].toDouble()
            if (s <= m) return prevX + (tickX[i] - prevX) * (s - prevS) / (m - prevS)
            prevS = m
            prevX = tickX[i]
        }
        return min(BAR_RIGHT, prevX + (BAR_RIGHT - prevX) * (s - prevS) / 2000)
    }

    fun dusk(t: Double) = easeInOut(seg(t, 6.2, 7.4)) * (1 - easeInOut(seg(t, RESET_START, RESET_START + 0.8)))

    const val CELEBRATE = 3.05
    fun happy(t: Double) = t >= CELEBRATE && t < CELEBRATE + 0.85

    class Bubble(val y: Double, val alpha: Double, val scale: Double)
    fun bubble(t: Double): Bubble {
        val rise = backOut(seg(t, 3.1, 3.5))
        val out = easeInOut(seg(t, 4.3, 4.7))
        return Bubble(-40 * rise - 8 * out, easeOut(seg(t, 3.1, 3.25)) * (1 - out), 0.6 + 0.4 * rise)
    }

    class Confetto(val x: Double, val y: Double, val angle: Double, val alpha: Double, val kind: Int, val color: Int)
    val confettiColors = intArrayOf(0xFF6A1F, 0xFFC14D, 0x7CC95A, 0x5DB4F2, 0xFF8FB1)
    fun confetti(t: Double): List<Confetto> {
        val tau = t - CELEBRATE
        if (tau < 0 || tau > 1.3) return emptyList()
        return List(24) { j ->
            val h1 = hash(j + 1.0)
            val h2 = hash(j + 31.7)
            val h3 = hash(j + 77.1)
            val a = -PI / 2 + (h1 - 0.5) * 2.0
            val v = 170 + 150 * h2
            Confetto(
                cos(a) * v * tau, sin(a) * v * tau + 260 * tau * tau,
                (h3 - 0.5) * 14 * tau + h1 * 6, 1 - smooth(0.8, 1.3, tau), j % 3, confettiColors[j % 5],
            )
        }
    }

    class Foot(val x: Double, val lift: Double, val angle: Double)
    fun foot(u0: Double): Foot {
        val u = fract(u0)
        if (u < 0.5) return Foot(18 - 72 * u, 0.0, 0.0)
        val v = (u - 0.5) / 0.5
        return Foot(-18 + 36 * easeInOut(v), 12 * sin(PI * v), -14 * sin(PI * v))
    }

    class Pose(
        val near: Foot, val far: Foot,
        val bob: Double, var sx: Double, var sy: Double,
        val lean: Double, val knot: Double, val arm: Double, val bag: Double,
        var lift: Double = 0.0,
    )

    fun pose(distance: Double): Pose {
        val w = fract(distance / STRIDE)
        val s2 = sin(2 * PI * w).pow(2)
        val c = min(min(w, abs(w - 0.5)), 1 - w)
        val g = exp(-(c / 0.07).pow(2))
        return Pose(
            near = foot(w), far = foot(w + 0.5),
            bob = -7 * s2,
            sx = 1 + 0.06 * g, sy = 1 - 0.07 * g + 0.025 * s2,
            lean = 4 + 1.5 * sin(4 * PI * w + 0.5),
            knot = -4 - 9 * sin(4 * PI * w - 0.9),
            arm = 16 * sin(2 * PI * w),
            bag = 12 * sin(2 * PI * w - 1.2),
        )
    }

    /** Hop after a tap: lift (pt) and vertical squash, τ s after the tap. */
    fun hop(tau: Double): Pair<Double, Double> {
        if (tau < 0 || tau > 0.62) return 0.0 to 1.0
        if (tau < 0.08) return 0.0 to (1 - 0.08 * sin(PI / 2 * tau / 0.08))
        if (tau < 0.5) {
            val u = (tau - 0.08) / 0.42
            val s = if (u < 0.2) 0.92 + 0.13 * (u / 0.2) else 1.05 - 0.05 * ((u - 0.2) / 0.8)
            return 36 * sin(PI * u) to s
        }
        val u = (tau - 0.5) / 0.12
        return 0.0 to (1 - 0.07 * sin(PI * u))
    }

    fun ground(safeTop: Double) = safeTop + 362
}

/** Touch state, read by the frame loop; plain fields, so writing it never recomposes. */
internal class WalkingStepsBuddyTouch {
    private val m = WalkingStepsBuddyMotion

    var touching = false
    private var liveD = 0.0
    private var startD = 0.0
    private var downClock = 0.0
    private var moved = 0.0
    private var released = false
    private var releaseClock = 0.0
    private var releaseD = 0.0
    private var releaseV = 0.0
    private var frozen = 0.0
    private var resume = 0.0
    var hopClock = -10.0

    fun distance(clock: Double, autoPlay: Boolean): Double {
        if (touching) return liveD
        if (!released) return if (autoPlay) m.SPEED * clock else 0.0
        val tau = max(0.0, clock - releaseClock)
        if (autoPlay) return releaseD + m.SPEED * tau + (releaseV - m.SPEED) * (1 - exp(-6 * tau)) / 6
        val w = 8.0
        val half = m.STRIDE / 2
        val target = ((releaseD + releaseV / w) / half).roundToLong() * half
        val a = releaseD - target
        return target + (a + (releaseV + w * a) * tau) * exp(-w * tau)
    }

    fun loopTime(clock: Double, autoPlay: Boolean): Double {
        if (!autoPlay) return 0.0
        val t = if (touching) frozen else frozen + max(0.0, clock - resume)
        return m.mod(t, m.LOOP)
    }

    val restsAfter: Double get() = max(releaseClock + 1.2, hopClock + 0.7)

    fun begin(clock: Double, autoPlay: Boolean) {
        if (touching) return
        frozen = loopTime(clock, autoPlay)
        startD = distance(clock, autoPlay)
        liveD = startD
        touching = true
        downClock = clock
        moved = 0.0
    }

    fun move(dx: Double, dy: Double) {
        moved = max(moved, sqrt(dx * dx + dy * dy))
        if (moved < 8) return
        liveD = startD - dx
    }

    /** Ends a touch; returns true when it was a tap on the mascot (a hop). */
    fun end(clock: Double, velocityX: Double, x: Double, y: Double, ground: Double, autoPlay: Boolean): Boolean {
        var hopped = false
        if (moved < 8 && clock - downClock < 0.4) {
            releaseV = if (autoPlay) m.SPEED else 0.0
            val lift = m.hop(clock - hopClock).first
            if (abs(x - m.MASCOT_X) < 84 && y > ground + 22 - 178 - lift && y < ground + 30) {
                hopClock = clock
                hopped = true
            }
        } else {
            releaseV = m.clamp(-velocityX, -700.0, 700.0)
        }
        releaseD = liveD
        releaseClock = clock
        released = true
        resume = clock + m.IDLE
        touching = false
        return hopped
    }
}

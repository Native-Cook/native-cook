package com.ngocdevv.nativecook.blocks.progress.xraybagscan

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cbrt
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Every moving value of the bag scan as a pure function of the loop time `t` (0 ..< LOOP) or of the
 * belt travel `d`. The belt carries every bag the same distance: bag k sits at `REST_X + d − SPACING·k`,
 * so one loop moves the belt exactly one bag spacing (16 slats) and frame P equals frame 0.
 */
internal object XrayBagScanMotion {
    const val LOOP = 9.0

    // Layout (units of the 393-wide canvas).
    const val REST_X = 57.0
    const val CENTRE_X = 196.5
    const val SPACING = 400.0
    const val LEG1 = CENTRE_X - REST_X
    const val PITCH = 25.0
    const val WIN_L = 126.5
    const val WIN_R = 266.5
    const val WIN_T = 332.0
    const val WIN_B = 556.0
    const val BELT_TOP = 528.0
    const val BELT_Y = 542.0
    const val SKIRT_TOP = 552.0
    const val SKIRT_BOTTOM = 580.0
    const val BAG_W = 98.0
    const val BAG_H = 184.0

    const val GO1 = 1.0
    const val STOP1 = 3.4
    const val GO2 = 5.6
    const val STOP2 = 8.2
    val statusTimes = doubleArrayOf(1.0, 3.5, 5.2, 7.6)
    val boxTimes = doubleArrayOf(3.8, 4.1, 4.4)
    const val CLEAR_TIME = 5.2

    fun clamp(x: Double, a: Double = 0.0, b: Double = 1.0) = min(max(x, a), b)
    fun seg(t: Double, a: Double, b: Double) = clamp((t - a) / (b - a))
    fun mod(x: Double, m: Double): Double { val r = x % m; return if (r < 0) r + m else r }
    fun easeInOutCubic(x: Double) = if (x < 0.5) 4 * x * x * x else 1 - (-2 * x + 2).pow(3) / 2
    fun easeOutCubic(x: Double) = 1 - (1 - x).pow(3)
    fun easeInOutSine(x: Double) = -(cos(PI * x) - 1) / 2
    fun smoothstep(a: Double, b: Double, x: Double): Double { val u = clamp((x - a) / (b - a)); return u * u * (3 - 2 * u) }

    fun inverseEaseInOutCubic(y: Double): Double {
        val v = clamp(y)
        return if (v < 0.5) cbrt(v / 4) else 1 - cbrt(2 * (1 - v)) / 2
    }

    /** Under-damped spring step 0 → 1 (ζ 0.55, ω 14), 0 before τ = 0. Peaks about 12 % over. */
    fun pop(tau: Double): Double {
        if (tau <= 0) return 0.0
        val z = 0.55
        val w = 14.0
        val wd = w * sqrt(1 - z * z)
        return 1 - exp(-z * w * tau) * (cos(wd * tau) + z * w / wd * sin(wd * tau))
    }

    /** Belt travel at loop time t: 0 → LEG1 (into the window), dwell, → SPACING (out, next bag in). */
    fun belt(t: Double) =
        LEG1 * easeInOutCubic(seg(t, GO1, STOP1)) + (SPACING - LEG1) * easeInOutCubic(seg(t, GO2, STOP2))

    /** The loop time whose belt travel is `d` (0 … SPACING), for a release after a scrub. */
    fun timeForBelt(d: Double): Double = when {
        d <= 0 -> GO1
        d < LEG1 -> GO1 + (STOP1 - GO1) * inverseEaseInOutCubic(d / LEG1)
        d < LEG1 + 0.5 -> STOP1
        else -> GO2 + (STOP2 - GO2) * inverseEaseInOutCubic((d - LEG1) / (SPACING - LEG1))
    }

    /** One damped rock of the bag after the belt starts or stops; 0 outside 0 ..< 0.8 s. */
    fun rock(tau: Double): Double {
        if (tau <= 0 || tau >= 0.8) return 0.0
        return sin(11 * tau) * exp(-4 * tau) * (1 - smoothstep(0.5, 0.8, tau))
    }

    /** Lean of the bags in degrees (clockwise positive). */
    fun lean(t: Double) = 2.6 * (-rock(t - GO1) + rock(t - STOP1) - rock(t - GO2) + rock(t - STOP2))

    fun scanning(t: Double) =
        easeInOutCubic(seg(t, GO1, GO1 + 0.2)) * (1 - easeInOutCubic(seg(t, CLEAR_TIME, CLEAR_TIME + 0.2)))

    fun beacon(t: Double) = scanning(t) * (0.55 + 0.45 * sin(2 * PI * 18 * t / LOOP))

    fun cleared(t: Double) =
        easeInOutCubic(seg(t, CLEAR_TIME, CLEAR_TIME + 0.25)) * (1 - easeInOutCubic(seg(t, 7.6, 8.2)))

    /** The horizontal sweep while the bag dwells: y in the window and its opacity. */
    fun sweepY(t: Double) = WIN_T + (WIN_B - WIN_T) * easeInOutSine(seg(t, 3.5, 4.9))
    fun sweepAlpha(t: Double) = sin(PI * seg(t, 3.45, 4.95))

    /** Glow of a window edge at `x`: 0.35 at rest, up to 1 where a bag crosses it. */
    fun curtain(x: Double, bagX: Double): Double {
        val left = bagX - BAG_W / 2
        val right = bagX + BAG_W / 2
        return 0.35 + 0.65 * clamp(min(x - left, right - x) / 14)
    }

    /** Detection boxes: (scale, alpha) per item, flattened. While scrubbing they follow the item's place. */
    fun boxes(t: Double, bagX: Double, scrubbing: Boolean, items: DoubleArray): DoubleArray {
        val out = DoubleArray(items.size * 2)
        for (i in items.indices) {
            if (scrubbing) {
                val x = bagX + items[i]
                val a = clamp((x - WIN_L - 12) / 18) * clamp((WIN_R - 12 - x) / 18)
                out[2 * i] = 0.85 + 0.15 * a
                out[2 * i + 1] = a
            } else {
                val tau = t - boxTimes[i]
                val fade = 1 - easeInOutCubic(seg(t, 5.9, 6.3))
                out[2 * i] = 0.6 + 0.4 * pop(tau)
                out[2 * i + 1] = clamp(tau / 0.12) * fade
            }
        }
        return out
    }

    val statuses = listOf("Ready to scan", "Scanning…", "Checking contents", "Bag cleared")

    /** Status stage 0 … 4 (4 ≡ 0), each step a 0.25 s ease. */
    fun stage(t: Double): Double {
        var s = 0.0
        for (a in statusTimes) s += easeOutCubic(seg(t, a, a + 0.25))
        return s
    }

    fun fill(t: Double): Double {
        val up = 0.333 * easeInOutCubic(seg(t, GO1, STOP1)) +
            0.334 * easeInOutCubic(seg(t, STOP1, CLEAR_TIME)) +
            0.333 * easeOutCubic(seg(t, CLEAR_TIME, CLEAR_TIME + 0.4))
        return up * (1 - easeInOutCubic(seg(t, 7.6, 8.2)))
    }

    fun checkScale(t: Double) = pop(t - CLEAR_TIME) * (1 - easeInOutCubic(seg(t, 7.6, 7.85)))
    fun checkStroke(t: Double) =
        easeOutCubic(seg(t, CLEAR_TIME + 0.08, CLEAR_TIME + 0.4)) * (1 - easeInOutCubic(seg(t, 7.6, 7.85)))

    /** Spinner angle (radians), 4 turns a loop. */
    fun spin(t: Double) = 2 * PI * 4 * t / LOOP

    fun stageIndex(stage: Double) = floor(mod(stage, 4.0)).toInt() % 4
}

/** Touch state, read by the frame clock; a plain class, so writing it never recomposes. */
internal class XrayBagScanTouch {
    private val m = XrayBagScanMotion

    var touching = false
        private set
    private var started = false
    private var base = 0.0
    private var baseClock = 0.0
    private var startD = 0.0
    private var liveD = 0.0
    private var offset = 0.0
    private var offsetClock = -10.0
    private var velocity = 0.0
    private var leanAtRelease = 0.0
    private var wasInside = false

    fun loopTime(clock: Double, autoPlay: Boolean): Double {
        if (touching) return m.timeForBelt(m.mod(liveD, m.SPACING))
        if (!autoPlay && !started) return 0.0
        val raw = base + max(0.0, clock - baseClock)
        return m.mod(if (autoPlay) raw else min(raw, m.LOOP), m.LOOP)
    }

    fun belt(clock: Double, autoPlay: Boolean): Double {
        if (touching) return if (liveD < 0) liveD else m.mod(liveD, m.SPACING)
        val tau = max(0.0, clock - offsetClock)
        return m.belt(loopTime(clock, autoPlay)) + offset * (1 + 10 * tau) * exp(-10 * tau)
    }

    fun lean(clock: Double, t: Double): Double {
        if (touching) return m.clamp(-velocity / 260, -1.0, 1.0) * 4
        val tau = max(0.0, clock - offsetClock)
        return m.lean(t) + leanAtRelease * exp(-5 * tau) * cos(11 * tau)
    }

    fun begin(clock: Double, autoPlay: Boolean) {
        if (touching) return
        startD = belt(clock, autoPlay)
        liveD = startD
        velocity = 0.0
        wasInside = false
        touching = true
    }

    /** Moves the belt with the finger; returns true when the bag has just come fully into the window. */
    fun move(dx: Double, velocityX: Double): Boolean {
        val raw = startD + dx
        liveD = if (raw < 0) -(-raw) / (1 + -raw / 120) else raw
        velocity = velocityX
        val d = if (liveD < 0) liveD else m.mod(liveD, m.SPACING)
        val inside = abs(d - m.LEG1) < 16
        val hit = inside && !wasInside
        wasInside = inside
        return hit
    }

    fun end(clock: Double) {
        if (!touching) return
        val d = if (liveD < 0) liveD else m.mod(liveD, m.SPACING)
        val t = m.timeForBelt(d)
        leanAtRelease = m.clamp(-velocity / 260, -1.0, 1.0) * 4
        touching = false
        started = true
        base = t
        baseClock = clock
        offset = d - m.belt(t)
        offsetClock = clock
    }

    fun restart(clock: Double) {
        touching = false
        started = true
        base = 0.0
        baseClock = clock
        offset = 0.0
        leanAtRelease = 0.0
    }

    val restsAfter: Double get() = if (started) baseClock + (m.LOOP - base) + 0.3 else 0.0
}

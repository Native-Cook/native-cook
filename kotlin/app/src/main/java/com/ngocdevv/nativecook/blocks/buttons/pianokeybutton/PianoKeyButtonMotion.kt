package com.ngocdevv.nativecook.blocks.buttons.pianokeybutton

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/*
 Piano Key Button — timing, ported from pianoKeyMotion.ts / PianoKeyButtonMotion.swift. Every frame
 of the self-playing loop is a pure function of the loop time t (seconds); under a finger the same
 pose is built from per-key press times and analytic spring segments (also pure functions of time).

 LOOP = 8 s:
   0.0 – 1.3   rest (the poster: three ivory keys, streak 12 days)
   1.3 – 2.0   key 1 (C) down and up      2.1 – 2.8  key 2 (E)      2.9 – 3.6  key 3 (G)
   4.2         chord C + G down, held to 6.4; 5.2 – 5.6 the streak rolls 12 → 13 and the chip pulses
   6.4 – 8.0   release and settle; 7.0 – 7.4 the streak rolls back to 12 so 8.0 equals 0.0
*/
internal object PianoKeyButtonMotion {
    const val LOOP = 8.0
    const val KEY_COUNT = 3

    /** [key, down, up] in loop seconds. */
    val SCRIPT = arrayOf(
        doubleArrayOf(0.0, 1.3, 2.0),
        doubleArrayOf(1.0, 2.1, 2.8),
        doubleArrayOf(2.0, 2.9, 3.6),
        doubleArrayOf(0.0, 4.2, 6.4),
        doubleArrayOf(2.0, 4.2, 6.4),
    )
    const val STREAK_FROM = 12.0
    private const val STREAK_UP_A = 5.2
    private const val STREAK_UP_B = 5.6
    private const val STREAK_BACK_A = 7.0
    private const val STREAK_BACK_B = 7.4

    /** The key's spring: ω 40 rad/s, damping ratio 0.62 (≈ 8 % overshoot, settled in ~0.16 s). */
    const val KEY_OMEGA = 40.0
    const val KEY_ZETA = 0.62

    /** A ring wave lives this long (s); three rings leave each press 0.12 s apart. */
    const val RING_LIFE = 1.3
    const val RING_DELAY = 0.12

    /** After the last touch the loop waits this long, then eases back to rest in BACK_FOR. */
    const val IDLE_BEFORE_BACK = 2.0
    const val BACK_FOR = 0.6

    class Key(
        /** Seconds since this key last went down (large when it never did). */
        var downAge: Double = 99.0,
        /** Seconds since it came back up, or -1 while it is held. */
        var upAge: Double = 99.0,
        /** Key travel 0 (rest) … 1 (on the keybed), may overshoot a little. */
        var depth: Double = 0.0,
        /** 0 = the CTA label shows, 1 = the note name shows. */
        var label: Double = 0.0,
    )

    class Pose {
        val keys = Array(KEY_COUNT) { Key() }
        /** Staff note heads lit 0…1: C, E, G, chord. */
        val notes = DoubleArray(4)
        /** The streak as a number that rests on whole days. */
        var streak = STREAK_FROM
        /** Chip pulse 0…1. */
        var pulse = 0.0
    }

    fun clamp01(x: Double) = if (x < 0) 0.0 else if (x > 1) 1.0 else x

    fun seg(t: Double, a: Double, b: Double) = clamp01((t - a) / (b - a))

    fun easeOut(x: Double): Double {
        val u = 1 - clamp01(x)
        return 1 - u * u * u
    }

    fun easeInOut(x: Double): Double {
        val v = clamp01(x)
        return if (v < 0.5) 4 * v * v * v else 1 - (-2 * v + 2).pow(3) / 2
    }

    /** Reanimated's default withTiming curve (in-out quad). */
    fun easeInOutQuad(x: Double): Double {
        val v = clamp01(x)
        return if (v < 0.5) 2 * v * v else 1 - (-2 * v + 2) * (-2 * v + 2) / 2
    }

    /** Unit step response of an underdamped spring, 0 before τ = 0. */
    fun springStep(tau: Double, omega: Double, zeta: Double): Double {
        if (tau <= 0) return 0.0
        val wd = omega * sqrt(1 - zeta * zeta)
        val e = exp(-zeta * omega * tau)
        return 1 - e * (cos(wd * tau) + (zeta * omega / wd) * sin(wd * tau))
    }

    /** The hammer behind a key: flicks up in 60 ms, falls back with one small bounce. */
    fun hammerLift(downAge: Double): Double {
        if (downAge < 0 || downAge > 1) return 0.0
        if (downAge < 0.06) return easeOut(downAge / 0.06)
        val a = downAge - 0.06
        return max(-0.12, exp(-a * 9) * cos(a * 20))
    }

    /** How hard the key's strings ring: decays while held, damped fast once the key is up. */
    fun stringRing(k: Key): Double {
        if (k.downAge > 6) return 0.0
        val ring = exp(-k.downAge * 1.4)
        return if (k.upAge < 0) ring else ring * exp(-k.upAge * 9)
    }

    /** A soft glow on the key, strongest at the strike. */
    fun keyGlow(k: Key): Double {
        if (k.downAge > 6) return 0.0
        val strike = exp(-k.downAge * 5) * 0.5
        val held = if (k.upAge < 0) 0.12 else 0.12 * exp(-k.upAge * 6)
        return strike + held
    }

    private fun labelAt(t: Double, down: Double, up: Double) =
        easeInOut(seg(t, down + 0.02, down + 0.26)) - easeInOut(seg(t, up + 0.35, up + 0.62))

    /** The pose of the self-playing loop at loop time t. */
    fun loopPose(t: Double): Pose {
        val p = Pose()
        var pulse = 0.0
        for (n in SCRIPT.indices) {
            val key = SCRIPT[n][0].toInt()
            val down = SCRIPT[n][1]
            val up = SCRIPT[n][2]
            val k = p.keys[key]
            // Superposed steps are the exact response of the linear spring to "down, then up".
            k.depth += springStep(t - down, KEY_OMEGA, KEY_ZETA) - springStep(t - up, KEY_OMEGA, KEY_ZETA)
            k.label += labelAt(t, down, up)
            if (t >= down) {
                k.downAge = t - down
                k.upAge = if (t >= up) t - up else -1.0
                pulse = max(pulse, 0.3 * exp(-(t - down) * 6))
            }
            val light = seg(t, down, down + 0.08) * (1 - seg(t, up + 0.5, up + 1.1))
            val note = min(n, 3)
            p.notes[note] = max(p.notes[note], light)
        }
        p.streak = STREAK_FROM + easeInOut(seg(t, STREAK_UP_A, STREAK_UP_B)) - easeInOut(seg(t, STREAK_BACK_A, STREAK_BACK_B))
        val q = seg(t, STREAK_UP_A, STREAK_UP_A + 0.6)
        p.pulse = max(pulse, sin(PI * q) * (1 - q * 0.4))
        return p
    }

    /** The resting pose (also what reduced motion shows). */
    fun restPose() = loopPose(0.0)
}

/** A spring segment from (x0, v0) at t0 toward target; value and velocity are closed form. */
internal class PianoKeySpring(var t0: Double = 0.0, var x0: Double = 0.0, var v0: Double = 0.0, var target: Double = 0.0) {
    private val w = PianoKeyButtonMotion.KEY_OMEGA
    private val z = PianoKeyButtonMotion.KEY_ZETA
    private val wd = w * sqrt(1 - z * z)

    fun value(t: Double): Double {
        val tau = max(0.0, t - t0)
        val a = x0 - target
        val b = (v0 + z * w * a) / wd
        return target + exp(-z * w * tau) * (a * cos(wd * tau) + b * sin(wd * tau))
    }

    fun velocity(t: Double): Double {
        val tau = max(0.0, t - t0)
        val a = x0 - target
        val b = (v0 + z * w * a) / wd
        val e = exp(-z * w * tau)
        return e * ((-z * w * a + wd * b) * cos(wd * tau) + (-z * w * b - wd * a) * sin(wd * tau))
    }

    fun go(to: Double, at: Double) {
        val x = value(at)
        val v = velocity(at)
        t0 = at
        x0 = x
        v0 = v
        target = to
    }

    fun set(x: Double, v: Double, at: Double) {
        t0 = at
        x0 = x
        v0 = v
        target = x
    }
}

/** A timed tween with Reanimated's default in-out quad curve. */
internal class PianoKeyTween(v: Double) {
    private var from = v
    private var to = v
    private var start = 0.0
    private var duration = 0.0

    fun value(now: Double): Double {
        if (duration <= 0) return to
        return from + (to - from) * PianoKeyButtonMotion.easeInOutQuad((now - start) / duration)
    }

    fun go(target: Double, at: Double, delay: Double = 0.0, duration: Double) {
        from = value(at)
        to = target
        start = at + delay
        this.duration = duration
    }
}

/** The hand-driven state: per-key springs and press times. Every query is a function of time. */
internal class PianoKeyEngine {
    private typealias M = PianoKeyButtonMotion

    enum class Mode { Auto, Touch, Back }

    var mode = Mode.Auto
    /** The loop's t = 0 in seconds (null until the first frame that has the art). */
    var loopStart: Double? = null
    var lastTouch = 0.0
    var backUntil = 0.0

    val depth = Array(M.KEY_COUNT) { PianoKeySpring() }
    val label = Array(M.KEY_COUNT) { PianoKeyTween(0.0) }
    val downAt = DoubleArray(M.KEY_COUNT) { -99.0 }
    val upAt = DoubleArray(M.KEY_COUNT) { -99.0 }
    val fingers = IntArray(M.KEY_COUNT)
    var streak = PianoKeyTween(M.STREAK_FROM)
    var pulseAt = -99.0
    var chorded = false

    /** Advances the mode to [now]. */
    fun step(now: Double) {
        when (mode) {
            Mode.Touch -> if (fingers.sum() == 0 && now - lastTouch > M.IDLE_BEFORE_BACK) {
                mode = Mode.Back
                backUntil = now + M.BACK_FOR
                streak.go(M.STREAK_FROM, now, duration = M.BACK_FOR)
                chorded = false
            }
            Mode.Back -> if (now >= backUntil) {
                mode = Mode.Auto
                loopStart = now
            }
            Mode.Auto -> Unit
        }
    }

    fun pose(now: Double, auto: Boolean): PianoKeyButtonMotion.Pose {
        if (mode != Mode.Auto) return touchPose(now)
        val start = loopStart
        if (!auto || start == null) return M.restPose()
        return M.loopPose(loopT(now, start))
    }

    private fun loopT(now: Double, start: Double) = (((now - start) % M.LOOP) + M.LOOP) % M.LOOP

    private fun touchPose(at: Double): PianoKeyButtonMotion.Pose {
        val p = PianoKeyButtonMotion.Pose()
        for (i in 0 until M.KEY_COUNT) {
            val held = upAt[i] < downAt[i]
            val k = p.keys[i]
            k.downAge = at - downAt[i]
            k.upAge = if (held) -1.0 else at - upAt[i]
            k.depth = depth[i].value(at)
            k.label = label[i].value(at)
            p.notes[i] = M.seg(at, downAt[i], downAt[i] + 0.08) * (if (held) 1.0 else 1 - M.seg(at, upAt[i] + 0.5, upAt[i] + 1.1))
        }
        p.notes[3] = min(p.notes[0], p.notes[2])
        val q = (at - pulseAt) / 0.6
        var pulse = if (q > 0 && q < 1) sin(PI * q) * (1 - q * 0.4) else 0.0
        for (i in 0 until M.KEY_COUNT) if (at >= downAt[i]) pulse = max(pulse, 0.3 * exp(-(at - downAt[i]) * 6))
        p.streak = streak.value(at)
        p.pulse = pulse
        return p
    }

    /** Hands the loop's pose to the springs, so a finger never makes anything jump. */
    private fun takeOver(at: Double, auto: Boolean) {
        if (mode == Mode.Touch) return
        val start = loopStart
        val p = when {
            mode == Mode.Back -> touchPose(at)
            auto && start != null -> M.loopPose(loopT(at, start))
            else -> M.restPose()
        }
        // The loop's spring velocity, measured from the pose a moment earlier.
        val before = if (mode == Mode.Auto && auto && start != null) M.loopPose(loopT(at - 0.001, start)) else null
        for (i in 0 until M.KEY_COUNT) {
            val k = p.keys[i]
            var v = when {
                mode == Mode.Back -> depth[i].velocity(at)
                before != null -> (k.depth - before.keys[i].depth) / 0.001
                else -> 0.0
            }
            if (abs(v) > 200) v = 0.0
            depth[i].set(k.depth, v, at)
            depth[i].go(0.0, at)
            label[i] = PianoKeyTween(k.label)
            if (k.downAge < 50) {
                downAt[i] = at - k.downAge
                upAt[i] = if (k.upAge < 0) at - k.downAge - 1 else at - k.upAge
            } else {
                downAt[i] = -99.0
                upAt[i] = -99.0
            }
            // A key the loop holds down is let go now.
            if (k.upAge < 0) {
                upAt[i] = at
                label[i].go(0.0, at, delay = 0.35, duration = 0.26)
            }
        }
        streak = PianoKeyTween(p.streak)
        streak.go(p.streak.roundToInt().toDouble(), at, duration = 0.3)
        mode = Mode.Touch
    }

    /** A finger landed on key i. Returns true when it completes the chord. */
    fun press(i: Int, at: Double, auto: Boolean): Boolean {
        takeOver(at, auto)
        fingers[i] += 1
        lastTouch = at
        if (fingers[i] > 1) return false
        downAt[i] = at
        depth[i].go(1.0, at)
        label[i].go(1.0, at, duration = 0.24)
        // C and G held together is the exercise's chord.
        if (fingers[0] > 0 && fingers[2] > 0 && !chorded) {
            chorded = true
            pulseAt = at
            streak.go(M.STREAK_FROM + 1, at, delay = 0.2, duration = 0.4)
            return true
        }
        return false
    }

    fun release(i: Int, at: Double) {
        lastTouch = at
        fingers[i] = max(0, fingers[i] - 1)
        if (fingers[i] > 0) return
        upAt[i] = at
        depth[i].go(0.0, at)
        label[i].go(0.0, at, delay = 0.35, duration = 0.26)
    }
}

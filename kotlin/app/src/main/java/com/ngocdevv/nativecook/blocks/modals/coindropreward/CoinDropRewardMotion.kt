package com.ngocdevv.nativecook.blocks.modals.coindropreward

import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sin

/** A direct transcription of cdr-motion.ts, in seconds. */
internal object CoinDropRewardMotion {
    const val LOOP = 9.0
    const val PULL_FULL = 0.32
    const val COOLDOWN = 4.0
    const val REWARD = 5
    const val TAP = 0.8
    const val LAUNCH = 0.95
    const val GRAB = 2.55
    const val RELEASE = 3.8
    const val ABSORB = 4.35
    const val LAUNCH_S = 0.6
    const val FLY_S = ABSORB - RELEASE
    fun clamp01(v: Double) = v.coerceIn(0.0, 1.0)
    fun seg(t: Double, a: Double, b: Double) = clamp01((t - a) / (b - a))
    fun smooth(a: Double, b: Double, x: Double): Double {
        val u = seg(x, a, b)
        return u * u * (3 - 2 * u)
    }
    fun easeInOut(x: Double) = if (x < 0.5) 4 * x * x * x else 1 - (-2 * x + 2).pow(3) / 2
    fun easeOut(x: Double) = 1 - (1 - x).pow(3)
    fun easeIn(x: Double) = x * x * x
    fun backOut(x: Double): Double {
        val u = clamp01(x) - 1
        return 1 + 2.5 * u * u * u + 1.5 * u * u
    }
    fun wobbleAt(u: Double) = if (u <= 0 || u >= 1) 0.0 else exp(-u * 5) * sin(u * 26)
    fun meltOf(pull: Double) = 0.8 * smooth(0.0, 1.0, pull / PULL_FULL)
    data class Frame(
        val launch: Double = 0.0, val flip: Double = 0.0, val pull: Double = 0.0,
        val melt: Double = 0.0, val fly: Double = 0.0, val trail: Double = 0.0,
        val toast: Double = 0.0, val text: Double = 0.0, val wobbleU: Double = 1.0,
        val plus: Double = 1.0, val coinVis: Double = 1.0, val cooldown: Double = 0.0,
        val press: Double = 0.0, val finger: Double = 0.0, val onCoin: Double = 0.0,
        val fingerPull: Double = 0.0,
    )
    fun frameAt(t: Double): Frame {
        val launch = if (t < LAUNCH) 0.0 else backOut(seg(t, LAUNCH, LAUNCH + LAUNCH_S))
        val flip = easeInOut(seg(t, 1.6, 2.4))
        val held = PULL_FULL * easeInOut(seg(t, 2.7, 3.5))
        val fly = easeIn(seg(t, RELEASE, ABSORB)) * 0.55 + 0.45 * easeInOut(seg(t, RELEASE, ABSORB))
        val pull = held * (1 - fly)
        val melt = if (t < RELEASE) meltOf(held) else 0.8 + 0.2 * easeOut(seg(t, RELEASE, RELEASE + 0.3))
        val trail = smooth(RELEASE, ABSORB - 0.1, t)
        val toast = smooth(LAUNCH, LAUNCH + 0.35, t) * (1 - smooth(RELEASE + 0.1, RELEASE + 0.45, t))
        val text = smooth(LAUNCH + 0.3, LAUNCH + 0.6, t) * (1 - smooth(RELEASE, RELEASE + 0.25, t))
        val after = t - ABSORB
        val cooldown = if (after > 0) max(0.0, COOLDOWN - after) else 0.0
        val out = t >= LAUNCH && t < ABSORB
        val coinVis = if (out) 0.0 else if (after > 0) 0.35 + 0.65 * smooth(COOLDOWN, COOLDOWN + 0.25, after) else 1.0
        val press = easeOut(seg(t, TAP - 0.08, TAP)) * (1 - easeOut(seg(t, TAP + 0.06, TAP + 0.22)))
        fun near(a: Double, b: Double) = easeOut(seg(t, a - 0.25, a)) * (1 - easeOut(seg(t, b, b + 0.2)))
        return Frame(launch, flip, pull, melt, fly, trail, toast, text,
            if (t < ABSORB) 1.0 else seg(t, ABSORB, ABSORB + 0.7),
            if (t < ABSORB) 1.0 else seg(t, ABSORB, ABSORB + 0.9), coinVis, cooldown,
            press, clamp01(near(TAP - 0.05, TAP + 0.15) + near(GRAB, RELEASE)),
            if (t > (TAP + GRAB) / 2) 1.0 else 0.0, held)
    }
}

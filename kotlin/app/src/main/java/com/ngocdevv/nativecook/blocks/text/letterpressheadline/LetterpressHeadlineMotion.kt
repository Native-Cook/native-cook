package com.ngocdevv.nativecook.blocks.text.letterpressheadline

import androidx.compose.animation.core.CubicBezierEasing
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/*
 The Letterpress Headline's clock (the React Native letterpressMotion.ts). Every value is a
 closed-form function of the loop time t (seconds), so t = LOOP draws the frame of t = 0.

   0.0 – 1.4   rest: blank cotton paper.
   1.4 – 4.0   the headline is printed letter by letter, one every 0.21 s; each letter's metal
               type block comes down (0.2 s, ease-in-out: it shrinks toward the paper and its
               shadow tightens), presses (0.1 s: the deboss and the ink go in with a 6 %
               overshoot) and lifts away (0.2 s, ease-out, fading).
   4.0 – 4.9   an ink roller sweeps the second line on, left to right (ease-in-out).
   4.8 – 6.0   the lamp swings across and back, so the deboss's light and shade move.
   6.0 – 6.8   hold.
   6.8 – 8.2   the impression and the ink fade out (ease-in-out), back to blank paper.
   8.2 – 9.0   rest.
 */

const val LOOP = 9f

/** Letters printed at most (the headline "Made by hand." has 11). */
internal const val LP_MAX_GLYPHS = 12
private const val FIRST = 1.4f
private const val EVERY = 0.21f
private const val DOWN = 0.2f
private const val PRESS = 0.1f
private const val UP = 0.2f

internal const val LP_LIGHT_AZ = -2.2f
internal const val LP_LIGHT_EL = 0.75f

internal fun lpClamp01(x: Float) = min(1f, max(0f, x))

internal fun lpSeg(t: Float, a: Float, b: Float) = lpClamp01((t - a) / (b - a))

internal fun lpEaseOut(x: Float): Float {
    val u = 1f - lpClamp01(x)
    return 1f - u * u * u
}

internal fun lpEaseInOut(x: Float): Float {
    val u = lpClamp01(x)
    return if (u < 0.5f) 4f * u * u * u else 1f - (-2f * u + 2f).pow(3) / 2f
}

internal fun lpRubber(x: Float, limit: Float): Float {
    val s = if (x < 0f) -1f else 1f
    return s * limit * (1f - 1f / (1f + abs(x) / limit))
}

internal class LpPose {
    /** Each letter's impression (0 … 1, a little more at the moment of pressing). */
    val depth = FloatArray(LP_MAX_GLYPHS)
    /** Each letter's type block: 0 away … 1 on the paper. */
    val block = FloatArray(LP_MAX_GLYPHS)
    /** True while a block is coming down (false: lifting away, which also fades it). */
    val falling = BooleanArray(LP_MAX_GLYPHS)
    /** The second line: roller position (0 … 1 across), ink shown up to it, the ink's opacity. */
    var roller = 0f
    var rollerAlpha = 0f
    var ink = 0f
    var inkAlpha = 0f
    /** The lamp. */
    var light = LP_LIGHT_AZ
    var elevation = LP_LIGHT_EL
}

internal fun LpPose.setLoop(t: Float, count: Int) {
    val fade = 1f - lpEaseInOut(lpSeg(t, 6.8f, 8.2f))
    for (i in 0 until LP_MAX_GLYPHS) {
        if (i >= count) {
            depth[i] = 0f
            block[i] = 0f
            falling[i] = true
            continue
        }
        val s = FIRST + i * EVERY
        val down = lpEaseInOut(lpSeg(t, s, s + DOWN))
        val pressed = lpSeg(t, s + DOWN, s + DOWN + PRESS)
        val up = lpEaseOut(lpSeg(t, s + DOWN + PRESS, s + DOWN + PRESS + UP))
        // The deboss goes in with the press, overshooting a little as the paper gives, then settles.
        val d = if (t < s + DOWN) 0f else lpEaseOut(pressed) + 0.06f * sin(PI.toFloat() * pressed)
        depth[i] = d * fade
        block[i] = down * (1f - up)
        falling[i] = up == 0f
    }
    val roll = lpEaseInOut(lpSeg(t, 4.0f, 4.9f))
    rollerAlpha = lpSeg(t, 3.9f, 4.05f) * (1f - lpSeg(t, 4.85f, 5.0f))
    roller = roll
    val swing = sin(PI.toFloat() * lpEaseInOut(lpSeg(t, 4.8f, 6.0f)))
    ink = if (t >= 4.0f) roll else 0f
    inkAlpha = fade
    light = LP_LIGHT_AZ + 1.5f * swing
    elevation = LP_LIGHT_EL - 0.2f * swing
}

/** Printed and still (reduced motion). */
internal fun LpPose.setPrinted(count: Int) {
    for (i in 0 until LP_MAX_GLYPHS) {
        depth[i] = if (i < count) 1f else 0f
        block[i] = 0f
        falling[i] = true
    }
    roller = 1f
    rollerAlpha = 0f
    ink = 1f
    inkAlpha = 1f
    light = LP_LIGHT_AZ
    elevation = LP_LIGHT_EL
}

/** A value animated as a pure function of the screen clock: a timing curve, or a spring. */
internal class LpTrack(initial: Float) {
    private var from = initial
    private var to = initial
    private var start = 0f
    private var duration = 0f
    private var easing: (Float) -> Float = { it }
    private var spring = false
    private var omega = 0f
    private var zeta = 0f
    private var v0 = 0f

    fun set(v: Float) {
        from = v; to = v; duration = 0f; spring = false
    }

    fun timing(now: Float, target: Float, seconds: Float, ease: (Float) -> Float) {
        from = value(now); to = target; start = now; duration = seconds; easing = ease; spring = false
    }

    /** Like Reanimated's duration-based spring: ω = 2π / duration. */
    fun spring(now: Float, target: Float, seconds: Float, dampingRatio: Float, velocity: Float = 0f) {
        from = value(now); to = target; start = now; spring = true
        omega = 2f * PI.toFloat() / seconds; zeta = dampingRatio; v0 = velocity
        duration = seconds * 2.5f
    }

    fun value(now: Float): Float {
        if (spring) {
            val u = now - start
            if (u <= 0f) return from
            val a = from - to
            val wd = omega * sqrt(1f - zeta * zeta)
            val b = (v0 + zeta * omega * a) / wd
            return to + exp(-zeta * omega * u) * (a * cos(wd * u) + b * sin(wd * u))
        }
        if (duration <= 0f) return to
        val u = (now - start) / duration
        if (u <= 0f) return from
        if (u >= 1f) return to
        return from + (to - from) * easing(u)
    }

    fun done(now: Float) = duration <= 0f || now >= start + duration
}

private val EaseOutBezier = CubicBezierEasing(0.23f, 1f, 0.32f, 1f)
private val EaseInOutBezier = CubicBezierEasing(0.77f, 0f, 0.175f, 1f)
internal val LpEaseOutCurve: (Float) -> Float = { EaseOutBezier.transform(it) }
internal val LpEaseInOutCurve: (Float) -> Float = { EaseInOutBezier.transform(it) }

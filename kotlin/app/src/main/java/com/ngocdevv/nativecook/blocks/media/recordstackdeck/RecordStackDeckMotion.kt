package com.ngocdevv.nativecook.blocks.media.recordstackdeck

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

/** The block's clock and the deck's geometry as functions, ported from RecordStackDeckMotion.swift. */
internal object DeckMotion {
    const val LOOP = 10.0
    /** Records in the crate; the deck wraps around, so scrolling by COUNT is the same picture. */
    const val COUNT = 10
    /** Points of finger travel per record. */
    const val DRAG_UNIT = 44.0

    fun clamp01(x: Double) = x.coerceIn(0.0, 1.0)
    fun smoothstep(a: Double, b: Double, x: Double): Double {
        val t = clamp01((x - a) / (b - a))
        return t * t * (3 - 2 * t)
    }
    fun easeInOut(x: Double): Double {
        val t = clamp01(x)
        return if (t < 0.5) 4 * t * t * t else 1 - (-2 * t + 2).pow(3) / 2
    }
    fun easeOut(x: Double) = 1 - (1 - clamp01(x)).pow(3)
    fun easeOutQuart(x: Double) = 1 - (1 - clamp01(x)).pow(4)
    /** Ease-out with a small overshoot (back, s = 1.4). */
    fun easeOutBack(x: Double): Double {
        val t = clamp01(x)
        val s = 1.4
        return 1 + (s + 1) * (t - 1).pow(3) + s * (t - 1).pow(2)
    }

    /** A critically damped spring (ω 12) from x0 with velocity v0 towards target: value after t. */
    fun settle(x0: Double, v0: Double, target: Double, t: Double): Double {
        val w = 12.0
        val d = x0 - target
        return target + (d + (v0 + w * d) * t) * exp(-w * t)
    }

    /** How far a record is out: 0 in the deck, 1 lifted flat, 2 its vinyl half out of the sleeve. */
    const val OPEN_LENGTH = 1.4
    fun opening(e: Double) = if (e < 0.8) easeOutBack(e / 0.8) else 1 + easeInOut((e - 0.8) / 0.6)
    fun closing(e: Double) = if (e < 0.6) 2 - easeInOut(e / 0.6) else 1 - easeInOut((e - 0.6) / 0.8)

    /** Deck position (records scrolled past the focus) at loop time t. */
    fun loopScroll(t: Double): Double {
        if (t < 0.4) return 0.0
        if (t < 1.1) return easeOutBack((t - 0.4) / 0.7)
        if (t < 8.0) return 1 + 2 * easeOut((t - 1.1) / 0.8)
        // A long flick through seven records back to the first one: the crate has turned once.
        return 3 + 7 * easeOutQuart((t - 8.0) / 1.7)
    }

    /** The hold ring around the focus: 0 to 1. */
    fun loopRing(t: Double): Double {
        if (t < 1.9 || t >= 2.8 + 0.25) return 0.0
        return if (t < 2.8) easeInOut((t - 1.9) / 0.9) else 1.0
    }

    fun loopOpen(t: Double): Double {
        if (t < 2.8 || t >= 8.0) return 0.0
        if (t < 2.8 + OPEN_LENGTH) return opening(t - 2.8)
        if (t < 6.6) return 2.0
        return closing(t - 6.6)
    }

    /** A record's place relative to the focus, wrapped to −COUNT/2 … COUNT/2. */
    fun offset(index: Int, scroll: Double): Double {
        val n = COUNT.toDouble()
        var d = (index - scroll) % n
        if (d < -n / 2) d += n
        if (d >= n / 2) d -= n
        return d
    }

    /** Distance of a strip's centre from the focus, in dp (down positive). */
    fun y(d: Double): Double {
        val a = abs(d)
        return (if (d < 0) -1 else 1) * (26 * a + 11 * a * a)
    }

    /** How open a sleeve is (the cosine of its tilt): a thin strip at the focus, more of its face further out. */
    fun openness(d: Double): Double {
        val a = min(abs(d), 5.0)
        return 0.02 + 0.08 * a - 0.0065 * a * a
    }

    /** The sleeve's tilt about its horizontal axis, in degrees: the far edge leans away from the focus. */
    fun tilt(d: Double): Double {
        val t = acos(openness(d)) * 180 / PI
        return if (d >= 0) t else -t
    }

    fun fade(d: Double) = 1 - smoothstep(3.0, 4.2, abs(d))

    /** The deck's focus, as a fraction of the screen height. */
    const val FOCUS_Y = 0.42

    /** Sleeves fade out before they reach the header. */
    fun topFade(y: Double, safeTop: Double) = smoothstep(safeTop + 40, safeTop + 120, y)

    /** The camera's distance for the sleeves' perspective, in dp. */
    const val DEPTH = 1400.0

    /**
     * A point (x, y) of a sleeve (relative to its centre, after scaling) seen tilted by [tiltDeg] about
     * its horizontal centre line from DEPTH away; the same projection as Core Animation's m34 = −1/DEPTH.
     */
    fun project(x: Double, y: Double, tiltDeg: Double): Pair<Double, Double> {
        val t = tiltDeg * PI / 180
        val yy = y * cos(t)
        val z = y * sin(t)
        val w = 1 - z / DEPTH
        return Pair(x / w, yy / w)
    }

    /** Where a sleeve of side s tilted by [tilt] degrees shows its top edge, relative to its centre. */
    fun topEdge(s: Double, tilt: Double) = project(0.0, -s / 2, tilt).second

    // The open record: the sleeve lies flat at 0.8 × its deck size and moves left, the vinyl
    // (0.92 × the flat sleeve across) slides out to the right.
    const val FLAT_SCALE = 0.8
    const val SLEEVE_SHIFT = -0.26
    const val VINYL_SHIFT = 0.32
    const val VINYL_RADIUS = 0.46
}

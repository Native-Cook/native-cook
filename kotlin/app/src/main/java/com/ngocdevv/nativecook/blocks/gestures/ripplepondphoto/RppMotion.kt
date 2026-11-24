// Ripple Pond Photo — the loop, the simulation's constants and the scripted hand, in closed form
// (rpp-motion.ts of the React Native block).
//
// The water itself is the one thing that is not a function of time: it is a height field stepped at
// SIM_STEP_HZ from the drops below, cleared at t = 0 while it is provably calm.
package com.ngocdevv.nativecook.blocks.gestures.ripplepondphoto

import kotlin.math.PI
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/** the loop's period, seconds */
const val LOOP = 10.0

/* the wave simulation */
/** grid columns; rows follow the screen's aspect so a cell is square */
internal const val SIM_GRID_W = 256
/** fixed steps a second, however fast the display refreshes */
internal const val SIM_STEP_HZ = 240
/** (wave speed x dt / dx)^2: 0.469 cells a step */
internal const val SIM_C2 = 0.22f
/** amplitude kept per step: 0.34 of it is left after a second */
internal const val SIM_DAMP = 0.9955f
/** the damping the water is eased to before the loop's cut and before autoplay resumes */
internal const val SIM_CALM_DAMP = 0.94f
/** most steps one frame may run (a stall is not caught up with a spike) */
internal const val SIM_MAX_STEPS = 12

/* what a touch does to the water */
/** a tap: a zero-mean dip (1 - q) exp(-q), q = r^2 / radius^2, radius in cells */
internal const val DROP_TAP_RADIUS = 10f
internal const val DROP_TAP_DEPTH = -2.2f
/** a tap is pressed into the field over this many steps with a sin^2 envelope */
internal const val DROP_TAP_STEPS = 8
/** a drag: the same dip, smaller, laid along the path */
internal const val DROP_WAKE_RADIUS = 6f
/** depth per dp of travel, so a faster finger cuts a deeper wake */
internal const val DROP_WAKE_PER_PT = -0.07f
/** deepest wake one frame may add */
internal const val DROP_WAKE_MAX = -0.9f

/* how the field is shown (dp and per unit of slope, slope = height difference across one cell) */
internal const val LOOK_REFRACT = 90f
internal const val LOOK_REFRACT_MAX = 26f
/** red and blue are refracted a little more and less than green */
internal const val LOOK_CHROMA = 0.08f
internal const val LOOK_NORMAL = 9f
internal const val LOOK_SPEC_POWER = 48f
internal const val LOOK_SPEC = 1.2f
internal const val LOOK_CAUSTIC = 14f
internal const val LOOK_SHADE = 7f

/** the fallback's analytic ring: speed dp/s, the packet's half width, its wavelength, decay per second */
internal const val RING_SPEED = 173f
internal const val RING_WIDTH = 34f
internal const val RING_WAVE = 26f
internal const val RING_DECAY = 0.9f
internal const val RING_LIFE = 7f
internal const val RING_COUNT = 8

/** scripted taps: time, x and y as fractions of the screen */
internal val T_TAPS = arrayOf(
    doubleArrayOf(0.9, 0.3, 0.72),
    doubleArrayOf(2.3, 0.72, 0.3),
    doubleArrayOf(5.9, 0.42, 0.8),
)
/** the scripted drag */
internal const val T_DRAG_START = 3.5
internal const val T_DRAG_END = 5.0
/** damping eases from SIM_DAMP to SIM_CALM_DAMP, and stays there until the cut */
internal const val T_CALM_START = 7.9
internal const val T_CALM_END = 8.4
/** the ghost finger leads a tap by this much, and lingers this long after it */
internal const val T_GHOST_IN = 0.22
internal const val T_GHOST_OUT = 0.3

/** a touch hands the water back this long after it ends */
internal const val IDLE = 2.0
/** then the water is calmed for this long (damping eased over the first part) and the loop restarts */
internal const val RETURN_EASE = 0.45
internal const val RETURN_TOTAL = 1.0

internal fun clamp01(v: Float) = if (v < 0f) 0f else if (v > 1f) 1f else v

/** 0 before a, 1 after b */
internal fun seg(t: Double, a: Double, b: Double) = clamp01(((t - a) / (b - a)).toFloat())

internal fun smooth(v: Float) = v * v * (3f - 2f * v)

internal fun easeOutCubic(v: Float): Float {
    val u = 1f - v
    return 1f - u * u * u
}

/** the share of a tap's depth pressed in by the time x (0…1) of its envelope has passed */
internal fun tapShare(x: Double): Double {
    val v = if (x < 0.0) 0.0 else if (x > 1.0) 1.0 else x
    return v - sin(2 * PI * v) / (2 * PI)
}

/** the scripted drag's path, fractions of the screen: a shallow arc from the lower left to the right */
internal fun dragX(u: Float) = 0.16f + 0.68f * smooth(clamp01(u))

internal fun dragY(u: Float): Float {
    val e = smooth(clamp01(u))
    return 0.7f - 0.1f * sin(PI.toFloat() * e) - 0.02f * e
}

/** which scripted tap the ghost finger is busy with at t, or -1 */
internal fun ghostTap(t: Double): Int {
    for (i in T_TAPS.indices) {
        val at = T_TAPS[i][0]
        if (t >= at - T_GHOST_IN && t < at + T_GHOST_OUT) return i
    }
    return -1
}

/** the ghost finger's opacity at t */
internal fun ghostAlpha(t: Double): Float {
    val i = ghostTap(t)
    if (i >= 0) {
        val at = T_TAPS[i][0]
        return easeOutCubic(seg(t, at - T_GHOST_IN, at - 0.06)) * (1f - smooth(seg(t, at + 0.08, at + T_GHOST_OUT)))
    }
    return easeOutCubic(seg(t, T_DRAG_START - T_GHOST_IN, T_DRAG_START)) * (1f - smooth(seg(t, T_DRAG_END, T_DRAG_END + T_GHOST_OUT)))
}

/** how hard the ghost finger presses at t, 0…1 (it lands on the water at a tap's time) */
internal fun ghostPress(t: Double): Float {
    val i = ghostTap(t)
    if (i >= 0) {
        val at = T_TAPS[i][0]
        return smooth(seg(t, at - 0.07, at)) * (1f - smooth(seg(t, at + 0.06, at + 0.24)))
    }
    return smooth(seg(t, T_DRAG_START - 0.08, T_DRAG_START)) * (1f - smooth(seg(t, T_DRAG_END, T_DRAG_END + 0.2)))
}

/** the damping of a step at loop time t */
internal fun dampAt(t: Double) = SIM_DAMP + (SIM_CALM_DAMP - SIM_DAMP) * seg(t, T_CALM_START, T_CALM_END)

/** grid rows for a screen, even, so a cell is as tall as it is wide */
internal fun gridRows(width: Float, height: Float) = max(256, min(640, (SIM_GRID_W * height / width / 2f).roundToInt() * 2))

/**
 * One frame for the water, written by the ticker and read once by the renderer: the epoch (a change
 * clears the water), the steps to run, c², the damping, four drops of x, y (0…1), radius (cells) and
 * depth, then what only the analytic fallback needs: the clock, the water's level and eight rings of
 * x, y (dp), born (s), strength.
 */
internal object Frame {
    const val EPOCH = 0
    const val STEPS = 1
    const val C2 = 2
    const val DAMP = 3
    const val DROPS = 4
    const val CLOCK = 20
    const val LEVEL = 21
    const val RINGS = 22
    const val LENGTH = RINGS + RING_COUNT * 4
}

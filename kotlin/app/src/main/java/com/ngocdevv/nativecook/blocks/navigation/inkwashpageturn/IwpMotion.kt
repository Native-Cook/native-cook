// Ink Wash Page Turn — the loop, the finger's rules and the scripted hand, in closed form
// (iwp-motion.ts). A page turn is one number, progress 0…1, with the point the ink starts from.
package com.ngocdevv.nativecook.blocks.navigation.inkwashpageturn

import kotlin.math.exp
import kotlin.math.max

internal const val LOOP = 10.0
internal const val PAGES = 3

/** a drag of this share of the screen's width is progress 1 (before the rubber band) */
internal const val TURN_SCRUB = 0.62
private const val TURN_BAND = 0.6
private const val TURN_BAND_MAX = 0.9
/** a release completes the turn when progress + project × its speed (1/s) passes commit */
internal const val TURN_PROJECT = 0.18
internal const val TURN_COMMIT = 0.3
/** a touch that travels less than this is a tap, dp */
internal const val TURN_TAP = 8f
/** a tap left of this share of the width turns back, anywhere else forward */
internal const val TURN_BACK_ZONE = 0.3f
/** the settle is a critically damped spring: x = target + (A + B τ) e^(−ω τ) */
internal const val TURN_OMEGA = 4.2
internal const val TURN_TAP_OMEGA = 3.0
/** the spring's remainder is blended out between these times after the release, s */
internal const val SETTLE_FROM = 1.2
internal const val SETTLE_UNTIL = 1.6
internal const val TAP_SETTLE_FROM = 1.7
internal const val TAP_SETTLE_UNTIL = 2.2

/** a touch hands the pages back this long after it ends */
internal const val IDLE = 2.0

/** a scripted swipe: finger down, finger up, x and y of the touch as fractions of the screen */
internal class Swipe(val down: Double, val up: Double, val x: Double, val y: Double)

/** 1 → 2: a swipe from the lower right */
internal val T_FIRST = Swipe(0.7, 1.5, 0.8, 0.74)
/** 2 → 3: a swipe from the upper left of centre */
internal val T_SECOND = Swipe(3.5, 4.3, 0.46, 0.36)
/** 3 → 1: a tap in the middle */
internal const val T_TAP = 6.3
internal const val T_TAP_X = 0.5
internal const val T_TAP_Y = 0.52
/** the scripted swipes travel this share of the width to the left, accelerating (u²) */
private const val T_TRAVEL = 0.36
/** the ghost finger leads a touch by this much, and lingers this long after it */
private const val T_GHOST_IN = 0.22
private const val T_GHOST_OUT = 0.3

internal fun clamp01(v: Double) = if (v < 0.0) 0.0 else if (v > 1.0) 1.0 else v

/** 0 before a, 1 after b */
internal fun seg(t: Double, a: Double, b: Double) = clamp01((t - a) / (b - a))

internal fun smooth(v: Double) = v * v * (3.0 - 2.0 * v)

private fun easeOutCubic(v: Double): Double {
    val u = 1.0 - v
    return 1.0 - u * u * u
}

/** progress for a drag of x scrub lengths: 1:1 up to the band, then a rubber band */
internal fun scrub(x: Double): Double {
    if (x <= TURN_BAND) return max(0.0, x)
    val room = TURN_BAND_MAX - TURN_BAND
    return TURN_BAND + room * (1.0 - exp(-(x - TURN_BAND) / room))
}

/** the settle: from x0 with speed v0 (1/s) to target, tau seconds after the release, exact at until */
internal fun settle(x0: Double, v0: Double, target: Double, tau: Double, omega: Double, from: Double, until: Double): Double {
    val a = x0 - target
    val b = v0 + omega * a
    val x = target + (a + b * tau) * exp(-omega * tau)
    val k = smooth(seg(tau, from, until))
    return clamp01(x + (target - x) * k)
}

/** progress of a scripted swipe at loop time t, 0 before and 1 once settled */
internal fun swipeAt(t: Double, s: Swipe): Double {
    if (t <= s.down) return 0.0
    if (t < s.up) {
        val u = (t - s.down) / (s.up - s.down)
        return scrub((T_TRAVEL * u * u) / TURN_SCRUB)
    }
    // released at the swipe's final speed
    val x0 = scrub(T_TRAVEL / TURN_SCRUB)
    val v0 = (2.0 * T_TRAVEL) / (s.up - s.down) / TURN_SCRUB
    return settle(x0, v0, 1.0, t - s.up, TURN_OMEGA, SETTLE_FROM, SETTLE_UNTIL)
}

/** progress of the scripted tap at loop time t */
internal fun tapAt(t: Double): Double {
    if (t <= T_TAP) return 0.0
    return settle(0.0, 0.0, 1.0, t - T_TAP, TURN_TAP_OMEGA, TAP_SETTLE_FROM, TAP_SETTLE_UNTIL)
}

/** which turn the loop is on at t: 0, 1 or 2 (a turn lasts until the next begins) */
internal fun turnAt(t: Double) = if (t < T_SECOND.down) 0 else if (t < T_TAP) 1 else 2

/** the ghost finger's opacity at t */
internal fun ghostAlpha(t: Double): Double {
    val a = easeOutCubic(seg(t, T_FIRST.down - T_GHOST_IN, T_FIRST.down)) * (1.0 - smooth(seg(t, T_FIRST.up, T_FIRST.up + T_GHOST_OUT)))
    val b = easeOutCubic(seg(t, T_SECOND.down - T_GHOST_IN, T_SECOND.down)) * (1.0 - smooth(seg(t, T_SECOND.up, T_SECOND.up + T_GHOST_OUT)))
    val c = easeOutCubic(seg(t, T_TAP - T_GHOST_IN, T_TAP - 0.06)) * (1.0 - smooth(seg(t, T_TAP + 0.08, T_TAP + T_GHOST_OUT)))
    return maxOf(a, b, c)
}

/** how hard the ghost finger presses at t, 0…1 */
internal fun ghostPress(t: Double): Double {
    val a = smooth(seg(t, T_FIRST.down - 0.08, T_FIRST.down)) * (1.0 - smooth(seg(t, T_FIRST.up, T_FIRST.up + 0.2)))
    val b = smooth(seg(t, T_SECOND.down - 0.08, T_SECOND.down)) * (1.0 - smooth(seg(t, T_SECOND.up, T_SECOND.up + 0.2)))
    val c = smooth(seg(t, T_TAP - 0.07, T_TAP)) * (1.0 - smooth(seg(t, T_TAP + 0.06, T_TAP + 0.24)))
    return maxOf(a, b, c)
}

/** the ghost finger's x at t, as a fraction of the width */
internal fun ghostXAt(t: Double): Double {
    val k = turnAt(t)
    if (k == 2) return T_TAP_X
    val s = if (k == 0) T_FIRST else T_SECOND
    val u = seg(t, s.down, s.up)
    return s.x - T_TRAVEL * u * u
}

/** the ghost finger's y at t, as a fraction of the height */
internal fun ghostYAt(t: Double): Double = when (turnAt(t)) {
    2 -> T_TAP_Y
    0 -> T_FIRST.y
    else -> T_SECOND.y
}

// Louver Theme Switch — the loop, the slats' numbers and the scripted finger, in closed form
// (lts-motion.ts).
//
// A theme change is one number, `amount`: 0 is the light screen, 1 the dark one. `dir` says which way
// the turn left its rest (+1 towards dark, −1 towards light), so the top slat leads both ways.
// LtsShader.kt turns those two numbers into the picture.
package com.ngocdevv.nativecook.blocks.navigation.louverthemeswitch

import kotlin.math.PI
import kotlin.math.exp

const val LOOP = 8.0

internal object Louver {
    /** slats across the screen's height */
    const val SLATS = 14
    /** how far the last slat starts behind the first, in units of one slat's own turn */
    const val SPREAD = 0.6f
    /** ease-in-out-back overshoot of a slat's turn (a small wind-up and settle) */
    const val BACK = 1.3725f
    /** the camera's distance from the screen, in screen heights */
    const val CAMERA = 1.35f
    /** slats overlap by this much at rest, dp, so no seam shows */
    const val OVERLAP = 0.5f
    /** the light, pointing at the viewer from above: y, z (normalised in the shader) */
    const val LIGHT_Y = -0.52f
    const val LIGHT_Z = 0.85f
    /** how dark a face turned fully away from the light gets */
    const val AMBIENT = 0.46f
    /** the moving highlight on a turning slat */
    const val GLOSS = 0.08f
    const val GLOSS_POWER = 48f
    /** the lit edge of a turning slat: strength, width (dp) */
    const val EDGE = 0.07f
    const val EDGE_WIDTH = 1.2f
    /** the contact shadow between slats: strength, e-fold (dp) */
    const val CONTACT = 0.38f
    const val CONTACT_FOLD = 3.2f
}

/** the loop's phases, seconds */
internal const val T_TO_DARK_A = 0.5
internal const val T_TO_DARK_B = 2.0
internal const val T_TO_LIGHT_A = 5.0
internal const val T_TO_LIGHT_B = 6.5
/** the ghost finger: shows, presses, leaves, relative to the start of each turn */
private const val T_GHOST_IN = -0.42
private const val T_GHOST_PRESS = -0.12
private const val T_GHOST_OUT = 0.3

/** a tap's turn takes this long, seconds, for the whole way */
internal const val TAP_TURN = 1.25
/** a drag of this fraction of the screen's height is a whole turn */
internal const val SCRUB = 0.62
/** a release projects the drag this far ahead, seconds, to decide commit or spring back */
internal const val PROJECT = 0.18
/** the critically damped return after a release, rad/s */
internal const val OMEGA = 9.0
/** a touch hands the screen back this long after it ends, seconds */
internal const val IDLE = 2.0
/** below this distance from a rest the turn counts as finished */
internal const val REST = 0.0005

internal fun clamp01(v: Double) = if (v < 0.0) 0.0 else if (v > 1.0) 1.0 else v

/** 0 before a, 1 after b */
internal fun seg(t: Double, a: Double, b: Double) = clamp01((t - a) / (b - a))

internal fun smooth(v: Double) = v * v * (3 - 2 * v)

/** a slat's turn 0…1 → 0…1, with a small wind-up and settle */
internal fun easeBack(u: Double): Double {
    val c = Louver.BACK.toDouble()
    val v = 2 * u
    if (u < 0.5) return 0.5 * v * v * ((c + 1) * v - c)
    val w = v - 2
    return 0.5 * (w * w * ((c + 1) * w + c) + 2)
}

/** slat `index` (0 at the top)'s angle in radians: 0 shows the light face, π the dark one */
internal fun slatAngle(amount: Double, dir: Double, index: Int): Double {
    val k = index.toDouble() / (Louver.SLATS - 1)
    val p = if (dir > 0) amount else 1 - amount
    val e = easeBack(clamp01(p * (1 + Louver.SPREAD) - Louver.SPREAD * k))
    return PI * (if (dir > 0) e else 1 - e)
}

/** the film's amount at loop time t */
internal fun filmAmount(t: Double) = seg(t, T_TO_DARK_A, T_TO_DARK_B) - seg(t, T_TO_LIGHT_A, T_TO_LIGHT_B)

/** the film's direction at loop time t */
internal fun filmDir(t: Double) = if (t < (T_TO_DARK_B + T_TO_LIGHT_A) / 2) 1.0 else -1.0

/** how much of a ghost tap shows at `s` seconds from its turn's start */
private fun tapAlpha(s: Double) =
    smooth(seg(s, T_GHOST_IN, T_GHOST_IN + 0.2)) * (1 - smooth(seg(s, T_GHOST_OUT, T_GHOST_OUT + 0.22)))

private fun tapPress(s: Double) =
    smooth(seg(s, T_GHOST_PRESS - 0.1, T_GHOST_PRESS)) * (1 - smooth(seg(s, 0.04, 0.2)))

internal class Ghost(val alpha: Double, val press: Double, /** 1 Dark, 0 Light */ val segment: Int)

/** the ghost finger: opacity, press 0…1, and which segment it is on */
internal fun ghostAt(t: Double): Ghost {
    val first = t < (T_TO_DARK_B + T_TO_LIGHT_A) / 2
    val s = t - if (first) T_TO_DARK_A else T_TO_LIGHT_A
    return Ghost(tapAlpha(s), tapPress(s), if (first) 1 else 0)
}

/** a critically damped approach from x0 with speed v0 to `to` */
internal fun settleAt(x0: Double, v0: Double, to: Double, tau: Double): Double {
    val a = x0 - to
    val b = v0 + OMEGA * a
    return to + (a + b * tau) * exp(-OMEGA * tau)
}

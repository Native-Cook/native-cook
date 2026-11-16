package com.ngocdevv.nativecook.blocks.cards.cardvacuumseal

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/** The film as the timeline plays it (RN `Look`). */
internal data class CardVacuumSealLook(
    val p: Double, // film state 0..1
    val press: Double, // tile pressed 0..1 (scale 1 -> 0.96)
    val flash: Double, // the disc's pale flash on press 0..1
    val puff: Double, // pillow overshoot as the air comes back 0..1
    val settle: Double, // crease amplitude factor (1 = rest)
    val glint: Double, // the lock's highlight running over the creases 0..1.6
    val sweep: Double, // the slow highlight's position -1..1
    val sweepK: Double, // its strength 0..1
    val tug: Double, // the Protect tile's pull toward the card at the lock 0..1
    val tugSide: Double, // the same for its neighbours, 40 ms later
    val discGlint: Double, // the amber disc's one glint 0..1
)

internal class CardVacuumSealSpring(val damping: Double, val stiffness: Double, val mass: Double)

/**
 * The motion of the RN block (`cvs-motion.ts`). One value `p` (the film's state) drives everything the film, the shield disc and the
 * label show: 0 = no film, P_DRAPE = the sheet resting on the card as a full air pillow, 1 = no air left (sealed, the pin's poster). The
 * autoplay timeline and the finger move the same `p`; every frame of the loop (LOOP = 6 s) is a pure function of the loop time.
 */
internal object CardVacuumSealMotion {
    const val LOOP = 6.0

    // phase table, seconds into the loop
    const val T_UNTAP0 = 0.12
    const val T_UNTAP1 = 0.4
    const val T_UNSEAL0 = 0.2
    const val T_UNSEAL1 = 1.0
    const val T_PRESS0 = 1.2
    const val T_PRESS1 = 3.45
    const val T_DRAPE0 = 1.25
    const val T_DRAPE1 = 1.6
    const val T_EVAC_A0 = 1.6
    const val T_EVAC_A1 = 2.05
    const val T_PAUSE0 = 2.05
    const val T_PAUSE1 = 3.05
    const val T_EVAC_B0 = 3.05
    const val T_EVAC_B1 = 3.45
    const val T_LOCK = 3.45
    const val T_SETTLE0 = 3.45
    const val T_SWEEP0 = 4.0
    const val T_SWEEP1 = 5.0

    const val AIR_END = 0.73
    const val P_DRAPE = 0.3
    const val P_HOLD = P_DRAPE + 0.8 * (T_EVAC_A1 - T_EVAC_A0)
    const val COMMIT_AT = 0.62
    const val SEAL_MS = 1250.0
    const val UNSEAL_MS = 900.0
    val TICKS = doubleArrayOf(0.45, 0.6, 0.75, 0.9)
    const val RESUME_AFTER = 2.0
    const val TAP_MAX = 0.2

    // the squeegee: the finger is the contact front
    const val SQ_BASE = P_DRAPE
    const val SQ_LEAD = 0.1
    const val SQ_EDGE = 28.0
    const val SQ_STRETCH = 0.3
    const val SQ_STRETCH_MAX = 8.0
    const val SQ_COMMIT_LEAD = 0.12
    const val SQ_FLICK = 2.2

    val SPRING_COMPLETE = CardVacuumSealSpring(26.0, 180.0, 1.0)
    val SPRING_REVERT = CardVacuumSealSpring(20.0, 140.0, 1.0)
    val SPRING_PRESS = CardVacuumSealSpring(18.0, 400.0, 1.0)
    val SPRING_DIMPLE = CardVacuumSealSpring(14.0, 260.0, 1.0)
    val SPRING_GLIDE = CardVacuumSealSpring(22.1, 157.9, 1.0)
    val SPRING_DROP = CardVacuumSealSpring(22.0, 210.0, 1.0)
    val SPRING_TUG = CardVacuumSealSpring(14.0, 260.0, 1.0)

    /** the tiles are pulled this far toward the card at the lock (design pt); the neighbours follow 40 ms later */
    const val TUG_PT = 2.0
    const val TUG_LAG = 0.04
    const val TUG_RESPONSE = 0.39
    const val TUG_ZETA = 0.434

    // the label's state: a timed morph at the lock and when air gets back in
    const val LABEL_MS = 380.0
    const val LABEL_OFF = 0.97
    const val LABEL_ON = 0.999

    fun clamp01(v: Double): Double = if (v < 0) 0.0 else if (v > 1) 1.0 else v
    fun seg(t: Double, a: Double, b: Double): Double = clamp01((t - a) / (b - a))
    fun smooth(v: Double): Double {
        val x = clamp01(v)
        return x * x * (3 - 2 * x)
    }

    /** cubic-bezier easing, solved by Newton steps on x */
    fun bezier(x1: Double, y1: Double, x2: Double, y2: Double, x: Double): Double {
        val v = clamp01(x)
        var t = v
        for (i in 0 until 6) {
            val it = 1 - t
            val fx = 3 * it * it * t * x1 + 3 * it * t * t * x2 + t * t * t - v
            val dx = 3 * it * it * x1 + 6 * it * t * (x2 - x1) + 3 * t * t * (1 - x2)
            if (abs(dx) < 1e-6) break
            t = clamp01(t - fx / dx)
        }
        val it = 1 - t
        return 3 * it * it * t * y1 + 3 * it * t * t * y2 + t * t * t
    }

    fun easeInOut(v: Double): Double = bezier(0.65, 0.0, 0.35, 1.0, v)

    /** 0 -> 1 step response of a spring (response s, damping ratio zeta), evaluated analytically */
    fun springAt(t: Double, response: Double, zeta: Double): Double {
        if (t <= 0) return 0.0
        val w = (2 * PI) / response
        if (zeta >= 1) return 1 - exp(-w * t) * (1 + w * t)
        val wd = w * sqrt(1 - zeta * zeta)
        return 1 - exp(-zeta * w * t) * (cos(wd * t) + ((zeta * w) / wd) * sin(wd * t))
    }

    /** a bump 0 -> 1 -> 0 over [a, b] */
    fun bump(t: Double, a: Double, b: Double): Double = sin(PI * seg(t, a, b))

    /** the label's state over the loop: 1 "Protected" .. 0 "Protect Card" (the same timed morph a finger gets) */
    fun labelAt(t: Double): Double {
        if (t < T_LOCK) return 1 - easeInOut(seg(t, 0.24, 0.62))
        return easeInOut(seg(t, T_LOCK, T_LOCK + 0.38))
    }

    /** where the film meets the card, 0 at the top-left corner .. 1 at the bottom edge (the shader's contactOrder) */
    fun orderAt(qx: Double, qy: Double, cbx: Double, cby: Double): Double {
        val ux = min(1.3, max(-1.3, qx / cbx)) * 0.5 + 0.5
        val uy = min(1.3, max(-1.3, qy / cby)) * 0.5 + 0.5
        return clamp01(0.42 * ux + 0.58 * uy)
    }

    /** the squeegee's p under a finger at (qx, qy): the sheet lies at 0.3, and the air has left up to the finger plus a lead */
    fun fingerP(qx: Double, qy: Double, cbx: Double, cby: Double): Double = SQ_BASE + (1 - SQ_BASE) * clamp01(orderAt(qx, qy, cbx, cby) + SQ_LEAD)

    /** crease amplitude from the speed the air was pressed out at (p/s): a slow hold 0.8, a flick 1.25 */
    fun creaseAmpOf(speed: Double): Double = 0.8 + 0.45 * smooth((speed - 0.9) / (SQ_FLICK - 0.9))

    /** the tiles' tug at the lock, 0..1 of TUG_PT: pulled toward the card in 70 ms, then back on the lip's spring */
    fun tugAt(tau: Double): Double {
        if (tau <= 0) return 0.0
        val pull = smooth(tau / 0.07)
        val relax = springAt(max(tau - 0.07, 0.0), TUG_RESPONSE, TUG_ZETA)
        return pull * (1 - relax)
    }

    /** p over the loop */
    fun pAt(t: Double): Double {
        if (t < T_UNSEAL0) return 1.0
        if (t < AIR_END) return 1 - (1 - P_DRAPE) * bezier(0.45, 0.0, 0.55, 1.0, seg(t, T_UNSEAL0, AIR_END))
        if (t < T_UNSEAL1) return P_DRAPE * (1 - bezier(0.32, 0.0, 0.67, 0.0, seg(t, AIR_END, T_UNSEAL1)))
        if (t < T_DRAPE0) return 0.0
        if (t < T_DRAPE1) return P_DRAPE * seg(t, T_DRAPE0, T_DRAPE1)
        if (t < T_EVAC_A1) return P_DRAPE + (P_HOLD - P_DRAPE) * seg(t, T_EVAC_A0, T_EVAC_A1)
        if (t < T_PAUSE1) {
            // the held half-seal: the finger wavers (0.02 at 1.2 Hz), eased in and out so the front never jumps
            val tau = t - T_PAUSE0
            val env = smooth(tau / 0.12) * (1 - smooth((tau - 0.88) / 0.12))
            return P_HOLD + 0.02 * env * sin(2 * PI * 1.2 * tau)
        }
        if (t < T_EVAC_B1) return P_HOLD + (1 - P_HOLD) * seg(t, T_EVAC_B0, T_EVAC_B1)
        return 1.0
    }

    class Sweep(val sweep: Double, val sweepK: Double)

    /** the light's sweeps: over the first second, across the held half-seal, and across the creases after the lock */
    fun sweepAt(t: Double): Sweep {
        if (t < 0.9) return Sweep(-1 + 2 * easeInOut(seg(t, 0.0, 0.9)), 0.8 * bump(t, 0.0, 0.9))
        if (t >= T_PAUSE0 && t < T_PAUSE1) return Sweep(-0.6 + 0.9 * easeInOut(seg(t, T_PAUSE0, T_PAUSE1)), 0.6 * bump(t, T_PAUSE0, T_PAUSE1))
        if (t >= T_SWEEP0 && t < T_SWEEP1) return Sweep(-1 + 2 * easeInOut(seg(t, T_SWEEP0, T_SWEEP1)), 0.8 * bump(t, T_SWEEP0, T_SWEEP1))
        return Sweep(-1.0, 0.0)
    }

    fun lookAt(t: Double): CardVacuumSealLook {
        val p = pAt(t)
        // tile press: the tap that unseals, then the long hold that seals (lifted a little while the finger is off)
        val tap = bump(t, T_UNTAP0, T_UNTAP1)
        val hold = smooth(seg(t, T_PRESS0, T_PRESS0 + 0.08)) * (1 - smooth(seg(t, T_PRESS1, T_PRESS1 + 0.12)))
        val off = smooth(seg(t, T_PAUSE0, T_PAUSE0 + 0.15)) * (1 - smooth(seg(t, T_PAUSE1 - 0.2, T_PAUSE1))) * 0.55
        val press = max(tap, hold * (1 - off))
        val flash = bump(t, T_PRESS0, T_PRESS0 + 0.32) + 0.6 * bump(t, T_UNTAP0, T_UNTAP0 + 0.3)
        // the pillow swells 4 % past its rest as the last air comes back in, before the sheet leaves
        val puff = bump(t, 0.58, 0.88)
        // creases relax 3 % after the lock (a light spring)
        val settle = if (t >= T_SETTLE0) 1 + 0.03 * (1 - springAt(t - T_SETTLE0, 0.42, 0.55)) else 1 + 0.03 * smooth(seg(t, T_EVAC_B0, T_EVAC_B1))
        val glint = bump(t, T_SETTLE0, T_SETTLE0 + 0.25)
        val sw = sweepAt(t)
        return CardVacuumSealLook(
            p, press, flash, puff, settle, glint, sw.sweep, sw.sweepK, tugAt(t - T_LOCK), tugAt(t - T_LOCK - TUG_LAG), bump(t, T_LOCK + 0.06, T_LOCK + 0.18),
        )
    }

    /** the rest the screen shows first, with Reduce Motion, and between touches without autoplay */
    val REST = CardVacuumSealLook(1.0, 0.0, 0.0, 0.0, 1.0, 0.0, -1.0, 0.0, 0.0, 0.0, 0.0)

    /** the slow drift of the key light, whole periods per loop so it repeats exactly (radians of azimuth) */
    fun lightDrift(time: Double): Double = 0.06 * sin((2 * PI * time) / LOOP) + 0.025 * sin((4 * PI * time) / LOOP + 1.3)

    /** the wordmark's shimmer clock: one pass of the fill across the letters per loop */
    fun shimmerAt(time: Double): Double = (time % LOOP) / LOOP
}

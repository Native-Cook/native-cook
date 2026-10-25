package com.ngocdevv.nativecook.blocks.lists.slotreellist

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/*
 Slot Reel List — the loop as pure functions of t (the React Native slotReelMotion.ts). A reel's
 position is an offset in rows (row k of its list sits on the payline when the offset is k, mod the
 list's length); the lever is an angle.

 LOOP = 9 s: rest 0–1.3 · lever pulled 1.3–1.9 (down 0.35 s, released at 1.9, springs back) ·
 spin 1.9–4.8 (reels stop at 3.6, 4.1, 4.6, each landing with a clunk) · win sweep 4.8–5.8 ·
 hold 5.8–7.0 · reels roll on to their resting rows 7.0–8.4 (1 s each, 0.15 s apart) · rest.
*/

internal const val LOOP = 9.0
/** Dishes per reel. */
internal const val SLOT_ROWS = 8
/** The rows on the payline at rest, and the loop's winning combination. */
internal val SlotRestRows = intArrayOf(2, 5, 1)
internal val SlotWinRows = intArrayOf(0, 0, 0)
/** Whole turns of the list each reel makes before stopping (later reels spin longer). */
internal val SlotTurns = intArrayOf(3, 4, 5)
private const val PULL_AT = 1.3
private const val PULL_DOWN = 0.35
internal const val SLOT_SPIN_AT = 1.9
internal val SlotStops = doubleArrayOf(3.6, 4.1, 4.6)
internal const val SLOT_SWEEP_AT = 4.8
private const val SWEEP_FOR = 1.0
private const val RETURN_AT = 7.0
private const val RETURN_FOR = 1.0
private const val RETURN_STAGGER = 0.15
/** The lever's travel: from upright (0) to pointing down past the viewer. */
internal const val SLOT_LEVER_MAX = PI * 0.82

internal fun slotClamp01(v: Double) = if (v < 0) 0.0 else if (v > 1) 1.0 else v

internal fun slotEaseInOutCubic(v: Double): Double {
    val x = slotClamp01(v)
    return if (x < 0.5) 4 * x * x * x else 1 - (-2 * x + 2).pow(3) / 2
}

internal fun slotEaseOutCubic(v: Double) = 1 - (1 - slotClamp01(v)).pow(3)

internal fun slotHash(n: Double): Double {
    val v = sin(n * 127.1 + 311.7) * 43758.5453
    return v - floor(v)
}

/** Positive modulo. */
internal fun slotWrap(v: Double, n: Double) = v - n * floor(v / n)

/*
 One spin: rows travelled after `tau` seconds of a spin of `dur` seconds covering `dist` rows. The
 speed ramps up over the first 15 %, cruises, and falls over the last 40 % to 18 % of its peak, so
 the reel still carries speed when the stop catches it: then it overshoots and settles on a damped
 spring (ω 26 rad/s, ζ 0.3) — the clunk of a reel landing.
*/
private const val A = 0.15
private const val D = 0.4
private const val E = 0.18
private const val S1 = 1 - A / 2 - ((1 - E) * D) / 2
private const val OMEGA = 26.0
private const val ZETA = 0.3

internal fun slotSpinRows(tau: Double, dur: Double, dist: Double): Double {
    if (tau <= 0) return 0.0
    if (tau < dur) {
        val u = tau / dur
        val s = if (u < A) {
            u * u / (2 * A)
        } else if (u < 1 - D) {
            A / 2 + (u - A)
        } else {
            val w = u - (1 - D)
            A / 2 + (1 - D - A) + w - ((1 - E) * w * w) / (2 * D)
        }
        return dist * s / S1
    }
    val ve = dist * E / (S1 * dur)
    val k = tau - dur
    val wd = OMEGA * sqrt(1 - ZETA * ZETA)
    return dist + ve / wd * exp(-ZETA * OMEGA * k) * sin(wd * k)
}

/** Rows a reel travels to go from row [from] to row [to], after [turns] whole turns. */
internal fun slotSpinDistance(from: Double, to: Double, turns: Int) =
    turns * SLOT_ROWS + slotWrap(to - from, SLOT_ROWS.toDouble())

/** Reel c's offset in the loop. */
internal fun slotAutoOffset(c: Int, t: Double): Double {
    val start = SLOT_SPIN_AT + 0.06 * c
    val dist = slotSpinDistance(SlotRestRows[c].toDouble(), SlotWinRows[c].toDouble(), SlotTurns[c])
    var o = SlotRestRows[c] + slotSpinRows(t - start, SlotStops[c] - start, dist)
    val back = RETURN_AT + RETURN_STAGGER * c
    if (t > back) o += slotWrap((SlotRestRows[c] - SlotWinRows[c]).toDouble(), SLOT_ROWS.toDouble()) * slotEaseInOutCubic((t - back) / RETURN_FOR)
    return o
}

/** A released lever: a playful spring back to upright (ζ 0.42), from angle a0. */
internal fun slotLeverSpring(k: Double, a0: Double): Double {
    val w = 15.0
    val z = 0.42
    val wd = w * sqrt(1 - z * z)
    val env = exp(-z * w * k)
    return a0 * env * (cos(wd * k) + (z * w / wd) * sin(wd * k))
}

/** The lever in the loop: pulled down (ease-in-out), let go, and springing back past upright. */
internal fun slotAutoLever(t: Double): Double {
    if (t < PULL_AT) return 0.0
    if (t < PULL_AT + PULL_DOWN) return SLOT_LEVER_MAX * slotEaseInOutCubic((t - PULL_AT) / PULL_DOWN)
    if (t < SLOT_SPIN_AT) return SLOT_LEVER_MAX
    return slotLeverSpring(t - SLOT_SPIN_AT, SLOT_LEVER_MAX)
}

/** The payline glow in the loop: up at 4.8, down after 7.0. */
internal fun slotAutoGlow(t: Double) =
    slotEaseOutCubic((t - SLOT_SWEEP_AT) / 0.2) * (1 - slotEaseOutCubic((t - RETURN_AT) / 0.3))

/** The light band's progress in the loop (4.8–5.8), or -1 when it is not crossing. */
internal fun slotAutoSweep(t: Double) =
    if (t >= SLOT_SWEEP_AT && t <= SLOT_SWEEP_AT + SWEEP_FOR) slotEaseInOutCubic((t - SLOT_SWEEP_AT) / SWEEP_FOR) else -1.0

/** A damped spring (ζ 0.55, settling in ~0.65 s) from x0 with velocity v0 (rows/s) to [target] (as in Swift). */
internal fun slotSnapSpring(k: Double, x0: Double, v0: Double, target: Double): Double {
    val w0 = 11.0
    val z = 0.55
    val wd = w0 * sqrt(1 - z * z)
    val a = x0 - target
    val b = (v0 + z * w0 * a) / wd
    return target + exp(-z * w0 * k) * (a * cos(wd * k) + b * sin(wd * k))
}

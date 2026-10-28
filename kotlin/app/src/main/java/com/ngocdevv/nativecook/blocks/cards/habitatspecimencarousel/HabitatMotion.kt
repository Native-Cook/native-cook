package com.ngocdevv.nativecook.blocks.cards.habitatspecimencarousel

import androidx.compose.animation.core.CubicBezierEasing
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

// Clocks, timeline and poses of the carousel (HabitatMotion.ts): one ambient loop of 13.5 s for
// idle life; the carousel's timeline (3 s at rest, 1.5 s crossing per specimen); a move's progress
// (`power`, 1.8 s) and its effect clock (`fx`, seconds from the move's start to 3.9 s, while what it
// throws lands).

const val LOOP = 13.5f
private const val HOLD = 3f / LOOP
private const val CROSS = 1.5f / LOOP
private const val SLOT = HOLD + CROSS

/** Each specimen rests for 3 s, performs its signature move, then crosses for 1.5 s. */
internal val SNAP_TIMES = floatArrayOf(0f, SLOT, SLOT * 2)
internal const val SIGNATURE_MS = 1800
/** One landing squash after a specimen arrives (landEnvelope). */
internal const val LAND_MS = 620
private const val SIGNATURE_START = 0.55f / LOOP
private const val SIGNATURE = SIGNATURE_MS / 1000f / LOOP

/**
 * How long a move's particles outlive the move itself (seconds from its start): leaves fall and
 * settle, drops land and splash, embers burn out. It ends with the slot, before the next specimen.
 */
internal const val FX_S = 3.9f

/** Choose Your Ball's drag rule: one wipe, one specimen (ballPickerGeometry.ts). */
internal const val PAN_REACH = 1f
internal const val PAN_RESIST = 6f
internal const val FLING_PROJECTION_S = 0.18f

/** How far the specimens drift across a crossing, as a fraction of the screen width. */
internal const val EXCHANGE_DRIFT = 0.16f

internal fun wrapPosition(value: Float): Float = ((value % 3f) + 3f) % 3f

internal fun specimenDistance(index: Int, position: Float): Float = wrapPosition(index - position + 1.5f) - 1.5f

internal fun clamp(value: Float, low: Float = 0f, high: Float = 1f): Float = max(low, min(high, value))

internal fun easeCrossing(x: Float): Float {
    var t = clamp(x)
    for (i in 0 until 7) {
        val a = 1 - t
        val delta = 3 * a * a * t * 0.77f + 3 * a * t * t * 0.175f + t * t * t - x
        val derivative = 3 * a * a * 0.77f + 6 * a * t * (0.175f - 0.77f) + 3 * t * t * (1 - 0.175f)
        if (abs(derivative) < 0.00001f) break
        t = clamp(t - delta / derivative)
    }
    return 3 * (1 - t) * t * t + t * t * t
}

private class Slot(val index: Int, val local: Float)

private fun slotOf(progress: Float): Slot {
    val p = clamp(progress)
    val index = min(2, floor(p / SLOT).toInt())
    return Slot(index, p - index * SLOT)
}

internal fun timelinePosition(progress: Float): Float {
    val slot = slotOf(progress)
    return if (slot.local < HOLD) slot.index.toFloat() else slot.index + easeCrossing((slot.local - HOLD) / CROSS)
}

/** Signature progress (0..1) while a resting specimen performs; 0 otherwise. */
internal fun timelineSignature(progress: Float): Float {
    val t = (slotOf(progress).local - SIGNATURE_START) / SIGNATURE
    return if (t > 0f && t < 1f) t else 0f
}

/**
 * The specimen whose slot this is: it performs, and its move's particles keep belonging to it
 * through the crossing that follows (not to the specimen arriving, which rounding would give).
 */
internal fun timelinePerformer(progress: Float): Int = slotOf(progress).index

/** Seconds since the resting specimen's move began, for its particles; -1 outside a move and its afterlife. */
internal fun timelineEffect(progress: Float): Float {
    val seconds = (slotOf(progress).local - SIGNATURE_START) * LOOP
    return if (seconds > 0f && seconds < FX_S) seconds else -1f
}

/** The demo visits every panel: About -> Base stats (morphing across two swipes) -> Evolution. */
internal fun timelineTab(progress: Float): Int {
    val slot = slotOf(progress)
    if (slot.index == 0) return if (slot.local < HOLD - 0.5f / LOOP) 0 else 1
    if (slot.index == 1) return 1
    if (slot.local < 0.2f / LOOP) return 1
    return if (slot.local < HOLD) 2 else 0
}

/** Anticipation and release envelope of a signature move. */
internal fun signatureEnvelope(t: Float): Float {
    if (t <= 0f || t >= 1f) return 0f
    val rise = clamp((t - 0.08f) / 0.14f)
    val fall = clamp((t - 0.6f) / 0.4f)
    return rise * rise * (3 - 2 * rise) * (1 - fall * fall * (3 - 2 * fall))
}

internal class SignaturePose(val sx: Float, val sy: Float, val tx: Float, val ty: Float)

private val NO_SIGNATURE = SignaturePose(1f, 1f, 0f, 0f)

/** Squash on the ground, recoil, then a damped settle. Origin is the creature's feet. */
internal fun signaturePose(t: Float, index: Int): SignaturePose {
    if (t <= 0f || t >= 1f) return NO_SIGNATURE
    val crouch = clamp(t / 0.11f)
    val anticipation = if (t < 0.12f) crouch * crouch else 1 - clamp((t - 0.12f) / 0.04f)
    val seconds = max(0f, t - 0.12f) * (SIGNATURE_MS / 1000f)
    val spring = if (t < 0.12f) 0f else exp(-seconds * 6.5f) * sin(seconds * 21f)
    val lift = if (index == 0) 1.5f else if (index == 2) 1.2f else 0.6f
    return SignaturePose(
        sx = 1 + 0.024f * anticipation - 0.018f * spring,
        sy = 1 - 0.042f * anticipation + 0.034f * spring * lift,
        tx = if (index == 1) -5 * spring else 0f,
        ty = 2.5f * anticipation - 5 * max(0f, spring) * lift,
    )
}

/** The exchange pose of one specimen: `role` 0 is outgoing, 1 incoming, -1 off stage. */
internal class ExchangePose(val role: Int, val f: Float, val drift: Float, val scale: Float)

/**
 * The exchange, for flat cut-out art: nothing turns, because a picture has no other side. The
 * incoming habitat's front wipes across the stage and brings its specimen with it; each specimen is
 * drawn only on its own side of that front, so the two never overlap or ghost. A short parallax
 * gives the layers depth: the incoming one settles in from the front's side and lands from slightly
 * nearer, the outgoing one is pushed back and away. `drift` is in units of EXCHANGE_DRIFT.
 * HABITAT_EFFECT's contact shadows repeat these formulas.
 */
internal fun exchangePose(index: Int, position: Float, reduced: Boolean): ExchangePose {
    val p = wrapPosition(if (reduced) jsRound(position) else position)
    val from = floor(p).toInt() % 3
    val f = if (reduced) 0f else p - floor(p)
    if (index == from) return ExchangePose(0, f, -0.5f * f * f, 1 - 0.05f * f)
    val u = 1 - f
    if (index == (from + 1) % 3 && f > 0.0005f) return ExchangePose(1, f, u * u, 1 + 0.05f * u * u)
    return ExchangePose(-1, f, 0f, 1f)
}

/** JavaScript's Math.round: halves go up, also below zero. */
internal fun jsRound(value: Float): Float = floor(value + 0.5f)

/** A signed current follows both the finger's travel and its velocity. */
internal fun gestureInfluence(translation: Float, velocity: Float, gap: Float): Float =
    clamp(velocity / (gap * 4) + clamp(translation / gap, -1f, 1f) * 0.18f, -1f, 1f)

internal val PRESS_IN = CubicBezierEasing(0.23f, 1f, 0.32f, 1f)

/** Round to the nearest slot index the way `Math.round(wrapPosition(p)) % 3` does. */
internal fun nearestIndex(position: Float): Int = jsRound(wrapPosition(position)).toInt() % 3

/** Neighbours enter only during a crossing; a settled specimen has no edge fragments (the still fallback below API 33 uses it). */
internal fun creatureOpacity(index: Int, position: Float, reduced: Boolean): Float {
    if (reduced) return if (nearestIndex(position) == index) 1f else 0f
    val t = clamp((abs(specimenDistance(index, position)) - 0.60f) / 0.38f)
    return 1 - t * t * (3 - 2 * t)
}

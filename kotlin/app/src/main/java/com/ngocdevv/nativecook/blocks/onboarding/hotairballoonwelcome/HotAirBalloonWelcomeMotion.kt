package com.ngocdevv.nativecook.blocks.onboarding.hotairballoonwelcome

import kotlin.math.PI
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

// Hot Air Balloon Welcome — the autoPlay script, as in balloonMotion.ts. Each onboarding step is an
// altitude: 0 on the ground (step 1), 1 and 2 aloft. Every value is a closed-form function of the
// loop time t, and periodic motion uses whole multiples of the loop's frequency.

internal const val LOOP = 10f
internal const val STEPS = 3
/** Gores round the envelope; the colour pattern repeats every PATTERN gores. */
internal const val GORES = 12
internal const val PATTERN = 4
/** The envelope turns one pattern (4 gores = 120°) per loop. */
internal val PatternAngle = (2.0 * PI * PATTERN / GORES).toFloat()

internal fun balloonClamp01(x: Float) = min(max(x, 0f), 1f)

private fun seg(t: Float, a: Float, b: Float) = balloonClamp01((t - a) / (b - a))

private fun easeInOut(x: Float): Float {
    val t = balloonClamp01(x)
    return if (t < 0.5f) 4f * t * t * t else 1f - (-2f * t + 2f).pow(3) / 2f
}

private fun easeOut(x: Float): Float {
    val t = balloonClamp01(x)
    return 1f - (1f - t).pow(3)
}

/** A burst of the burner: roars in 0.15 s, holds, dies away in 0.35 s. */
private fun burst(t: Float, a: Float, b: Float, level: Float) = level * easeOut(seg(t, a, a + 0.15f)) * (1f - easeInOut(seg(t, b, b + 0.35f)))

internal class BalloonPose {
    /** Altitude in steps (0 = ground). */
    var alt = 0f
    /** Burner heat 0..1. */
    var heat = 0f
    /** The step text (continuous index, rolls between steps). */
    var step = 0f
    /** Envelope turn (radians). */
    var turn = 0f
    /** Time the flicker and sway are drawn at (s). */
    var time = 0f
}

internal fun balloonLoopPose(t: Float, out: BalloonPose): BalloonPose {
    out.alt = easeInOut(seg(t, 2.0f, 4.0f)) + easeInOut(seg(t, 4.8f, 7.0f)) - 2f * easeInOut(seg(t, 7.0f, 8.8f))
    out.heat = burst(t, 1.6f, 2.6f, 1f) + burst(t, 4.6f, 5.4f, 1f) + burst(t, 8.0f, 8.3f, 0.55f)
    out.step = easeInOut(seg(t, 2.4f, 2.9f)) + easeInOut(seg(t, 5.2f, 5.7f)) - 2f * easeInOut(seg(t, 7.6f, 8.4f))
    out.turn = PatternAngle * t / LOOP
    out.time = t
    return out
}

/** Flame height factor: three sines (whole multiples of the loop) for a living flicker. */
internal fun balloonFlicker(time: Float): Float {
    val w = 2f * PI.toFloat() / LOOP
    return 1f + 0.09f * sin(w * 37f * time) + 0.06f * sin(w * 61f * time + 1.3f) + 0.04f * sin(w * 89f * time + 2.1f)
}

/** Aloft the balloon sways a little (degrees) and bobs (dp); on the ground it is still. */
internal fun balloonSwayAngle(time: Float, alt: Float): Float {
    val w = 2f * PI.toFloat() / LOOP
    return 1.3f * balloonClamp01(alt) * sin(w * time + 0.4f)
}

internal fun balloonBob(time: Float, alt: Float): Float {
    val w = 2f * PI.toFloat() / LOOP
    return 2.2f * balloonClamp01(alt) * sin(w * 2f * time)
}

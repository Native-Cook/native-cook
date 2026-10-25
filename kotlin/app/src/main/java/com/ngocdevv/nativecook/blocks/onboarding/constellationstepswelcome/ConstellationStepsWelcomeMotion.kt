package com.ngocdevv.nativecook.blocks.onboarding.constellationstepswelcome

import kotlin.math.PI
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

// Constellation Steps Welcome — the sky's script and the three constellations, as in
// constellationMotion.ts. Every value is a closed-form function of the loop time t; twinkles use
// whole multiples of the loop's frequency. Constellations are laid out upright round the hero
// centre (dp, y down, before scaling); constellation k sits k steps of STEP_ANGLE round the pole.

internal const val LOOP = 10f
internal const val STEPS = 3
internal val StepAngle = (48.0 * PI / 180.0).toFloat()

internal class Constellation(
    val name: String,
    /** Stars: x, y, size (dp). */
    val stars: Array<FloatArray>,
    /** Lines between stars, in drawing order. */
    val edges: Array<IntArray>,
    /** The brightest star, labelled. */
    val alpha: Int,
    val label: String,
)

internal val Constellations = listOf(
    Constellation(
        "Cygnus",
        // Deneb, Sadr, η, Albireo, Gienah, left wing tip, δ, right wing tip.
        arrayOf(
            floatArrayOf(0f, -112f, 3.4f), floatArrayOf(2f, -22f, 2.6f), floatArrayOf(6f, 42f, 1.9f), floatArrayOf(12f, 108f, 2.4f),
            floatArrayOf(-78f, -38f, 2.4f), floatArrayOf(-124f, -2f, 1.8f), floatArrayOf(74f, -48f, 2.2f), floatArrayOf(118f, -24f, 1.7f),
        ),
        arrayOf(intArrayOf(0, 1), intArrayOf(1, 2), intArrayOf(2, 3), intArrayOf(1, 4), intArrayOf(4, 5), intArrayOf(1, 6), intArrayOf(6, 7)),
        0,
        "Deneb",
    ),
    Constellation(
        "Cassiopeia",
        // Caph, Schedar, Navi, Ruchbah, Segin.
        arrayOf(floatArrayOf(112f, -30f, 2.4f), floatArrayOf(58f, 30f, 3.0f), floatArrayOf(2f, -14f, 2.7f), floatArrayOf(-56f, 38f, 2.4f), floatArrayOf(-110f, -8f, 2.0f)),
        arrayOf(intArrayOf(0, 1), intArrayOf(1, 2), intArrayOf(2, 3), intArrayOf(3, 4)),
        1,
        "Schedar",
    ),
    Constellation(
        "Orion",
        // Meissa, Betelgeuse, Bellatrix, Alnitak, Alnilam, Mintaka, Saiph, Rigel.
        arrayOf(
            floatArrayOf(0f, -122f, 1.7f), floatArrayOf(-62f, -86f, 3.4f), floatArrayOf(56f, -78f, 2.6f), floatArrayOf(-24f, 2f, 2.4f),
            floatArrayOf(0f, -6f, 2.5f), floatArrayOf(24f, -14f, 2.3f), floatArrayOf(-46f, 98f, 2.3f), floatArrayOf(62f, 104f, 3.2f),
        ),
        arrayOf(intArrayOf(0, 1), intArrayOf(0, 2), intArrayOf(1, 3), intArrayOf(2, 5), intArrayOf(3, 4), intArrayOf(4, 5), intArrayOf(3, 6), intArrayOf(5, 7)),
        7,
        "Rigel",
    ),
)

internal fun skyClamp01(x: Float) = min(max(x, 0f), 1f)

private fun seg(t: Float, a: Float, b: Float) = skyClamp01((t - a) / (b - a))

private fun easeInOut(x: Float): Float {
    val t = skyClamp01(x)
    return if (t < 0.5f) 4f * t * t * t else 1f - (-2f * t + 2f).pow(3) / 2f
}

internal class SkyPose {
    /** Sky turn round the pole (radians). */
    var theta = 0f
    /** How far each constellation's lines are drawn (0..1). */
    val drawn = FloatArray(STEPS)
    /** Lines and outlines fade with this (the return home). */
    var fade = 1f
    /** The step text (continuous index). */
    var step = 0f
    /** Twinkle time (s). */
    var time = 0f
}

internal fun skyLoopPose(t: Float, out: SkyPose): SkyPose {
    out.theta = StepAngle * (easeInOut(seg(t, 3.6f, 4.4f)) + easeInOut(seg(t, 6.2f, 7.0f)) - 2f * easeInOut(seg(t, 8.4f, 9.4f)))
    out.drawn[0] = seg(t, 1.6f, 3.6f)
    out.drawn[1] = seg(t, 4.4f, 6.2f)
    out.drawn[2] = seg(t, 7.0f, 8.4f)
    out.step = easeInOut(seg(t, 3.7f, 4.3f)) + easeInOut(seg(t, 6.3f, 6.9f)) - 2f * easeInOut(seg(t, 8.5f, 9.3f))
    out.fade = 1f - easeInOut(seg(t, 8.4f, 9.2f))
    out.time = t
    return out
}

/** Screen geometry: the pole the sky turns round (above the screen), the hero centre, the scale. */
internal class SkyGeo(val w: Float, val h: Float, top: Float, bottom: Float) {
    val px = w / 2f
    val py = -h * 0.12f
    val cx = w / 2f
    val cy = (top + bottom) / 2f
    val scale = max(0.6f, min(1f, min((bottom - top - 40f) / 300f, (w - 40f) / 280f)))
}

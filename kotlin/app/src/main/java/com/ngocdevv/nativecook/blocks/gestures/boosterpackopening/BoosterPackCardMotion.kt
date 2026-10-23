package com.ngocdevv.nativecook.blocks.gestures.boosterpackopening

internal const val CARD_CENTERING_DURATION_MS = 650.0
private val TIMES = DoubleArray(CARD_KEYFRAMES.size) { CARD_KEYFRAMES[it].ms }
private val TOPS = DoubleArray(CARD_KEYFRAMES.size) { CARD_KEYFRAMES[it].top }
private const val CENTERED_TOP = (1 - ReferenceLayout.Card.HEIGHT) / 2
private val LAST = CARD_KEYFRAMES[CARD_KEYFRAMES.size - 1]
private val PREVIOUS = CARD_KEYFRAMES[CARD_KEYFRAMES.size - 2]
private val REVEAL_VELOCITY = (LAST.top - PREVIOUS.top) / (LAST.ms - PREVIOUS.ms)

/** Normalized top; preserve the measured reveal, then ease into the scene center. */
internal fun cardTopAtTime(ms: Double, reducedMotion: Boolean = false): Double {
    // A fade at the destination replaces the upward travel for reduced motion.
    if (reducedMotion) return CENTERED_TOP
    if (ms <= POST_CUT_DURATION_MS) {
        return interpolateKeyframes(ms, TIMES, TOPS)
    }

    val t = clamp((ms - POST_CUT_DURATION_MS) / CARD_CENTERING_DURATION_MS, 0.0, 1.0)
    // Hermite interpolation carries the reveal's final velocity into this move,
    // then settles with zero velocity, avoiding a stop at the old video endpoint.
    val t2 = t * t
    val t3 = t2 * t
    return (2 * t3 - 3 * t2 + 1) * LAST.top +
        (t3 - 2 * t2 + t) * REVEAL_VELOCITY * CARD_CENTERING_DURATION_MS +
        (-2 * t3 + 3 * t2) * CENTERED_TOP
}

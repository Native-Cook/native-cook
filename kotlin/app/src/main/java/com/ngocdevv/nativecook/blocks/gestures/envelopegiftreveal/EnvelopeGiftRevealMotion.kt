package com.ngocdevv.nativecook.blocks.gestures.envelopegiftreveal

import androidx.compose.animation.core.CubicBezierEasing

/** One period of the autoplay script, in seconds (React Native LOOP_MS / 1000). */
internal const val LOOP = 10f
internal const val LOOP_MS = 10_000f
internal const val REDUCED_MS = 200f

/** QA only: set to a loop time in milliseconds to hold an autoplay frame. */
internal val QA_HOLD_AT: Float? = null

internal object EnvelopeGiftRevealOpen {
    const val travel = 320f
    const val commit = 0.42f
    const val flapShare = 0.33f
    const val cardRise = 222f
    const val tiltMax = 10f
    const val breathDeg = 7f
    const val breathMs = 10_000f
    const val breathLead = 1_000f
    const val sealSplitDeg = 40f
    const val settlePt = 23f
    const val pocketApex = 0.38f
    const val curlDeg = 28f
}

internal data class EnvelopeGiftRevealSpringConfig(
    val stiffness: Float,
    val damping: Float,
    val mass: Float,
)

internal object EnvelopeGiftRevealSprings {
    val close = EnvelopeGiftRevealSpringConfig(stiffness = 260f, damping = 18f, mass = 1f)
}

internal object EnvelopeGiftRevealReveal {
    const val finishMs = 420f
    const val flyMs = 720f
    val flyEase = CubicBezierEasing(0.77f, 0f, 0.175f, 1f)
    const val envelopeDropMs = 520f
    const val textDelay = 520f
    const val stagger = 80f
    const val flipDelay = 160f
    const val flipMs = 900f
    const val flipBack = 0.9f
    const val flipLift = 0.05f
}

internal object EnvelopeGiftRevealReset {
    const val fadeMs = 420f
    const val outShare = 0.4f
    const val inFrom = 0.43f
    const val textOutMs = 160f
    const val letterMs = 160f
    const val letterStagger = 5f
}

internal object EnvelopeGiftRevealHeadline {
    const val stagger = 0.006f
    const val span = 0.05f
    const val drop = EnvelopeGiftRevealSpace.xl
}

internal object EnvelopeGiftRevealAmbient {
    const val floatPt = 2f
    const val floatMs = 5_000f
}

internal object EnvelopeGiftRevealPolish {
    val easeOut = CubicBezierEasing(0.23f, 1f, 0.32f, 1f)
    const val pressScale = 0.96f
    const val pressInMs = 100
    const val pressOutMs = 160
    const val textRise = EnvelopeGiftRevealSpace.lg
}

internal object EnvelopeGiftRevealScript {
    const val down = 600f
    const val up = 2_100f
    const val rise = 300f
    const val reset = 8_200f
}

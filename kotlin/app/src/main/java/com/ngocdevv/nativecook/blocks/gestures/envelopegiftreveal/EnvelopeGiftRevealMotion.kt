package com.ngocdevv.nativecook.blocks.gestures.envelopegiftreveal

import androidx.compose.animation.core.CubicBezierEasing

/** One period of the autoplay script, in seconds (motion.ts LOOP_MS / 1000). */
internal const val LOOP = 8f

/** Every number the motion uses (motion.ts), times in ms and lengths in dp. */
internal const val LOOP_MS = 8_000f
internal const val RESUME_AFTER_MS = 3_500f
internal const val REDUCED_MS = 200f

/** QA only: when set, the script stops at this time (ms) so a state can be photographed. Kept null. */
internal val QA_HOLD_AT: Float? = null

internal object EnvelopeOpen {
    /** Upward drag (dp) for a full open: the first part folds the flap, the second lifts the card. */
    const val travel = 320f

    /** Progress past which a release completes the reveal. */
    const val commit = 0.42f
    const val flapShare = 0.33f

    /** Enough to lift the card's bottom edge clear of the envelope: the second part of the drag is 1:1. */
    const val cardRise = 222f
    const val tiltMax = 10f
    const val breathDeg = 7f

    /** Divides LOOP_MS so the breath falls at the same place in every loop. */
    const val breathMs = 4_000f

    /** The wax seal cracks over the first part of the flap's lift. */
    const val sealSplitDeg = 40f

    /** Envelope settles down this far as the flap opens; the pocket's V opens to this fraction of the height. */
    const val settlePt = 23f
    const val pocketApex = 0.38f

    /** Paper curl: the flap's lower part lags the fold by up to this much. */
    const val curlDeg = 28f
}

internal class EnvelopeSpringConfig(val stiffness: Float, val damping: Float, val mass: Float)

internal object EnvelopeSprings {
    val close = EnvelopeSpringConfig(stiffness = 260f, damping = 18f, mass = 1f)
    val finish = EnvelopeSpringConfig(stiffness = 140f, damping = 18f, mass = 1f)
    val press = EnvelopeSpringConfig(stiffness = 500f, damping = 28f, mass = 0.6f)
}

/** After the reveal commits, in ms from the release. */
internal object EnvelopeReveal {
    const val finishMs = 420f
    const val flyMs = 720f
    /** On-screen move curve copied from motion.ts REVEAL.flyEase. */
    val flyEase = CubicBezierEasing(0.77f, 0f, 0.175f, 1f)
    const val envelopeDropMs = 520f
    const val pageMs = 600f
    const val textDelay = 520f
    const val stagger = 80f
    const val confetti = 70
    const val confettiLife = 2.6f
    const val gravity = 520f
    const val drag = 0.985f
    const val buttonOpenMs = 520f

    /** Light sweep across the card art on arrival, and the price counting up. */
    const val sweepMs = 900f
    const val countMs = 760f
}

/** Press feedback and final text entrance copied from motion.ts POLISH. */
internal object EnvelopePolish {
    val easeOut = CubicBezierEasing(0.23f, 1f, 0.32f, 1f)
    const val pressScale = 0.96f
    const val pressInMs = 100
    const val pressOutMs = 160
    const val textRise = EnvelopeSpace.lg
}

internal object EnvelopeReset {
    const val fadeMs = 420f
}

/** Headline exit: letters slide down one after another over the first part of the drag (progress units). */
internal object EnvelopeHeadline {
    const val stagger = 0.006f
    const val span = 0.05f
    const val drop = EnvelopeSpace.xl
}

/** Ambient life while sealed. */
internal object EnvelopeAmbient {
    const val floatPt = EnvelopeSpace.xs
    const val floatMs = 3_200f
    const val glowMs = 9_000f
    const val glowAmount = 0.12f
}

/** Autoplay: one scripted finger on one clock (ms). */
internal object EnvelopeScript {
    /** A slow, readable pull: 1.6 s for the flap and the card, then about 1 s of reveal. */
    const val down = 700f
    const val up = 2_300f
    const val rise = 300f

    /** About 1.7 s of finished screen after the texts have risen, then the cross-fade back. */
    const val reset = 6_000f
}

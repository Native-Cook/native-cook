package com.ngocdevv.nativecook.blocks.gestures.pinchtosummarize

/** Every motion value, copied from the React Native motion.ts source. */
internal object PtsPinch {
    const val fullIn = 0.5f
    const val fullOut = 1.8f
    const val commitAt = 0.32f
    const val restoreAt = 0.7f
    const val deceleration = 0.998f
    const val rubber = 0.55f
    const val follow = 0.6f
    const val velocityToProgress = 1.6f
}

internal object PtsSpring {
    data class Config(val damping: Float, val stiffness: Float, val mass: Float)

    val collapse = Config(damping = 22f, stiffness = 210f, mass = 1f)
    val restore = Config(damping = 30f, stiffness = 230f, mass = 1f)
    val lift = Config(damping = 24f, stiffness = 180f, mass = 1f)
    val drift = Config(damping = 20f, stiffness = 240f, mass = 1f)
    val pop = Config(damping = 12f, stiffness = 260f, mass = 0.7f)
}

internal object PtsTiming {
    const val thinkMs: Long = 1_400L
    const val thinkIn: Long = 260L
    const val rimOut: Long = 420L
    const val rimFlashIn: Long = 120L
    const val rimFlashOut: Long = 520L
    const val rimLetGo: Long = 300L
    const val tick: Long = 16L
    const val lineStagger: Long = 70L
    const val lineMs: Long = 420L
    const val lineRise = 8f
    const val summaryOutStart = 0.95f
    const val toolbarFadeSpan = 0.3f
    const val rimTurnMs: Long = 1_600L
    const val breathe = 0.012f
    const val breathePeriod: Long = 1_600L
    const val bloomMs: Long = 900L
    const val bloomReach = 70f
    const val sweepMs: Long = 700L
    const val sweepDelay: Long = 200L
    const val ghostOffset = 6f
    const val ghostAlpha = 0.35f
    const val discFrom = 0.8f
    const val motes = 22
    const val moteSpeedMin = 18f
    const val moteSpeedMax = 40f
    const val pressScale = 0.97f
    const val scanMs: Long = 1_100L
    const val shimmerMs: Long = 1_300L
    const val fieldBreath = 0.015f
    const val fieldPeriod: Long = 11_000L
    const val fingerFade: Long = 140L
    const val maxFrameMs: Long = 250L
    const val reducedFade: Long = 180L
}

internal object PtsRim {
    const val pinchMax = 0.55f
    const val armedBoost = 0.3f
    const val think = 1f
    const val scan = 0.9f
}

/** One autoplay period, in milliseconds. */
internal const val LOOP: Long = 11_000L

/** Kept as the source name for callers that work in milliseconds. */
internal const val LOOP_MS: Long = LOOP

/** QA only (pixel gate): freeze the autoplay in one state. Null in shipped code. */
internal val QA_HOLD: String? = null

internal object PtsAutoplay {
    const val resumeDelayMs: Long = 3_500L
    const val qaHoldAt: Long = 6_500L
    const val pinchInStart: Long = 1_000L
    const val pinchInEnd: Long = 1_900L
    const val pinchInScale = 0.52f
    const val pinchOutStart: Long = 8_200L
    const val pinchOutEnd: Long = 9_050L
    const val pinchOutScale = 1.85f
    const val resumeReading: Long = 0L
    const val resumeSummary: Long = 7_000L
    const val spreadIn = 150f
    const val spreadOut = 70f
}

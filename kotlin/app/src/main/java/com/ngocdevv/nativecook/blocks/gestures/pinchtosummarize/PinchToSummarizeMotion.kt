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
    const val cropPow = 2.2f
}

internal object PtsSpring {
    data class Config(val duration: Int, val dampingRatio: Float)

    val hold = Config(duration = 400, dampingRatio = .8f)
    val collapse = Config(duration = 700, dampingRatio = 1f)
    val restore = Config(duration = 600, dampingRatio = 1f)
    val lift = collapse
    val drift = Config(duration = 400, dampingRatio = .8f)
    val pop = Config(duration = 180, dampingRatio = .8f)
}

internal object PtsHold {
    const val min = .42f
    const val max = .6f
}

internal object PtsMark {
    const val span = .45f
    const val step = .2f
    const val count = 4
    const val dimMax = .62f
    const val autoLockMs = 150L
    const val pulseDelay = 300L
    const val pulseMs = 200L
    const val fadeDelay = 400L
    const val fadeMs = 400L
    const val cancelMs = 260L
}

internal object PtsDepth {
    const val brief = .62f
    const val tldr = .85f
    const val hysteresis = .03f
    const val showFrom = .12f
    const val showSpan = .08f
}

internal object PtsFlight {
    const val ms = 420L
    const val stagger = 60L
    const val bow = 28f
    const val bowPerDy = .14f
    const val lineMs = 300L
    const val lineRise = 6f
    const val entityMs = 220L
    const val rollMs = 600L
    const val priceRollMs = 520L
    const val landMs = ms + (PtsMark.count - 1) * stagger
    const val scrollMs = 600L
}

internal object PtsTiming {
    const val thinkMs: Long = 1_100L
    const val thinkIn: Long = 260L
    const val rimOut: Long = 420L
    const val rimFlashIn: Long = 120L
    const val rimFlashOut: Long = 520L
    const val rimLetGo: Long = 300L
    const val tick: Long = 16L
    const val lineStagger: Long = PtsFlight.stagger
    const val lineMs: Long = PtsFlight.ms
    const val lineRise = PtsFlight.lineRise
    const val summaryOutStart = 1f
    const val toolbarFadeSpan = 0.3f
    const val rimTurnMs: Long = 1_600L
    const val breathe = 0.008f
    const val breathePeriod: Long = 1_600L
    const val bloomMs: Long = 900L
    const val bloomReach = 70f
    const val sweepMs: Long = 700L
    const val sweepDelay: Long = 520L
    const val discFrom = 0.8f
    const val motes = 22
    const val moteSpeedMin = 18f
    const val moteSpeedMax = 40f
    const val pressScale = 0.97f
    const val fieldBreath = 0.015f
    const val fieldPeriod: Long = 11_000L
    const val fingerFade: Long = 140L
    const val maxFrameMs: Long = 250L
    const val reducedFade: Long = 180L
    const val longPressMs: Long = 350L
    const val shareRise = 12f
    const val shareScale = 1.03f
    const val dropMs: Long = 150L
}

internal object PtsRim {
    const val pinchMax = 0.55f
    const val armedBoost = 0.3f
    const val think = 1f
}

/** One autoplay period, in milliseconds. */
internal const val LOOP: Long = 10_000L

/** Kept as the source name for callers that work in milliseconds. */
internal const val LOOP_MS: Long = LOOP

/** QA only (pixel gate): freeze the autoplay in one state. Null in shipped code. */
internal val QA_HOLD: String? = null

internal object PtsAutoplay {
    const val resumeDelayMs: Long = 2_000L
    const val qaHoldAt: Long = 6_000L
    const val readStart: Long = 400L
    const val readMs: Long = 1_100L
    const val readTarget = 820f
    const val pinchInStart: Long = 1_700L
    const val pinchInEnd: Long = 2_600L
    const val pinchInScale = 0.76f
    const val holdProgress = .48f
    const val pinchOutStart: Long = 6_800L
    const val pinchOutEnd: Long = 7_650L
    const val pinchOutScale = 1.8f
    const val topStart: Long = 8_300L
    const val topMs: Long = 1_100L
    const val resumeReading: Long = 0L
    const val spreadIn = 150f
    const val spreadOut = 70f
}

package com.ngocdevv.nativecook.blocks.gestures.glassdisconboarding

import androidx.compose.animation.core.CubicBezierEasing

/** One exact period, matching React Native's LOOP_MS = 10_000. */
const val LOOP = 10f

internal object GlassDiscOnboardingMotion {
    val EaseOut = CubicBezierEasing(0.23f, 1f, 0.32f, 1f)
    val EaseInOut = CubicBezierEasing(0.77f, 0f, 0.175f, 1f)

    const val HomeDuration = 0.52f
    const val HomeDamping = 0.78f
    const val ReducedDuration = 0.2f
    const val OpenDuration = 0.68f
    const val ReducedOpenDuration = 0.22f
    const val GatherDuration = 0.42f
    const val RouteDuration = 0.9f
    const val RouteDelay = 0.12f
    const val HintDuration = 0.2f
    const val FingerDuration = 0.15f
    const val ResumeDelay = 2f
    const val TidyDelay = 0.6f

    const val Commit = 0.6f
    const val Flick = -650f
    const val Rubber = 0.3f

    const val FingerIn = 0.8f
    const val FingerDown = 0.95f
    const val Release = 3.15f
    const val TapIn = 6.7f
    const val Tap = 6.9f

    val DemoPath = arrayOf(
        floatArrayOf(1.05f, 0.5f, 0.9f),
        floatArrayOf(2.3f, 0.5f, 0.5f),
        floatArrayOf(2.75f, 0.62f, 0.42f),
        floatArrayOf(3.05f, 0.56f, 0.46f),
    )
}

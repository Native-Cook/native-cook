package com.ngocdevv.nativecook.blocks.onboarding.tradingbotsonboarding

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.spring
import androidx.compose.ui.graphics.Color
import kotlin.math.PI

/**
 * Every position in this block is in points (dp here) on a 402 × 874 canvas, the iPhone 17/18 Pro
 * screen the reference video was captured on. Other screens scale the canvas to fit. Values match
 * the SwiftUI block (the Swift files in TradingBotsOnboarding).
 */
internal const val CanvasWidth = 402f
internal const val CanvasHeight = 874f

internal object TBColors {
    val ink = Color(0xFFFBFBFB)
    val caption = Color(0xFF8A8A8C)
    val ctaText = Color(0xFF3B3B3B)
}

/** `0xRRGGBB` plus an opacity, like `TradingBotsPalette.hex(_, _)`. */
internal fun hex(value: Long, opacity: Float = 1f) = Color(0xFF000000 or value).copy(alpha = opacity)

internal fun white(opacity: Float) = Color.White.copy(alpha = opacity)

/** SwiftUI `.spring(response:dampingFraction:)` as a physical spring with mass 1. */
internal fun <T> swiftSpring(response: Float, dampingFraction: Float): AnimationSpec<T> {
    val omega = 2f * PI.toFloat() / response
    return spring(dampingRatio = dampingFraction, stiffness = omega * omega)
}

internal val RevealCurve = CubicBezierEasing(0.2f, 0.7f, 0.25f, 1f)
internal val WipeCurve = CubicBezierEasing(0.3f, 0.1f, 0.3f, 1f)
internal val SettleCurve = CubicBezierEasing(0.4f, 0f, 0.2f, 1f)
/** SwiftUI's default `.easeOut` / `.easeInOut` curves. */
internal val EaseOut = CubicBezierEasing(0f, 0f, 0.58f, 1f)
internal val EaseInOut = CubicBezierEasing(0.42f, 0f, 0.58f, 1f)

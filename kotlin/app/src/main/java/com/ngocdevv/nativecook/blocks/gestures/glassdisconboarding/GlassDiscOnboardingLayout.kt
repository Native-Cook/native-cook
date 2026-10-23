package com.ngocdevv.nativecook.blocks.gestures.glassdisconboarding

import kotlin.math.hypot
import kotlin.math.max

internal data class GlassDiscOnboardingLayout(
    val width: Float,
    val height: Float,
    val density: Float,
    val top: Float,
    val bottom: Float,
    val domeX: Float,
    val domeY: Float,
    val lensY: Float,
    val cover: Float,
    val cardY: Float,
) {
    fun dp(value: Float): Float = value * density

    companion object {
        fun make(width: Int, height: Int, density: Float, top: Int, bottom: Int): GlassDiscOnboardingLayout {
            val h = height.toFloat()
            val d = density
            return GlassDiscOnboardingLayout(
                width = width.toFloat(),
                height = h,
                density = d,
                top = top.toFloat(),
                bottom = bottom.toFloat(),
                domeX = width / 2f,
                domeY = h + GlassDiscOnboardingTheme.Layout.DomeSink * d,
                lensY = h * 0.5f,
                cover = hypot(width.toFloat(), h),
                cardY = h - max(bottom.toFloat(), GlassDiscOnboardingTheme.Layout.Edge * d) - 8f * d -
                    GlassDiscOnboardingTheme.Layout.CardHeight * d,
            )
        }
    }
}

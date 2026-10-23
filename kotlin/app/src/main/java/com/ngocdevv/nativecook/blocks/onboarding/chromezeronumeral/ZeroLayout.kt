package com.ngocdevv.nativecook.blocks.onboarding.chromezeronumeral

import kotlin.math.*

internal class ZeroLayout(val width: Float, val height: Float, safeBottom: Float) {
    val k = min(width / 402f, height / 874f)
    val typeScale = max(k, 0.86f)
    val offsetX = (width - 402 * k) / 2
    val centreX = offsetX + 214 * k
    val centreY = 286 * k
    val unit = 158 * k
    val heroHeight = 536 * k
    val pillTop = 538 * k
    val headlineTop = max(grid(584 * k), grid(pillTop + 28 + 16))
    val headlineLine = 40 * typeScale
    val bodyTop = max(grid(676 * k), grid(headlineTop + 2 * headlineLine + 12))
    val buttonHeight = if (height < 700) 48f else 56f
    val buttonY = height - safeBottom - 24 - buttonHeight
    val buttonWidth = width - 48
    private fun grid(n: Float) = floor(n / 4 + 0.5f) * 4
}

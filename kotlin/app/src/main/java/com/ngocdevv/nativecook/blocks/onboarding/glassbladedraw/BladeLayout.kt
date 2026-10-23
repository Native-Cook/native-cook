package com.ngocdevv.nativecook.blocks.onboarding.glassbladedraw

import kotlin.math.*

internal class BladeLayout(val width: Double, val height: Double, bottomInset: Double) {
    val scale = min(width / 402, height / 874)
    val ctaH = 56.0
    val ctaX = 16.0
    val ctaW = width - 32
    val ctaY = height - max(bottomInset, 8.0) - 8 - ctaH
    val ctaR = 16.0
    val cardH = floor(320 * scale + .5)
    val heroBottomR = floor(388 * scale + .5)
    val sideBottomR = floor(306 * scale + .5)
    val pivotX = width / 2
    val pivotY = ctaY - floor(48 * scale + .5) + heroBottomR
    val cardW = floor(132 * scale + .5)
    val cardT = 7 * scale
    val cornerR = 14 * scale
}

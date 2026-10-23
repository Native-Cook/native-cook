package com.ngocdevv.nativecook.blocks.gestures.rewardcardreveal.shared.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.ngocdevv.nativecook.blocks.gestures.rewardcardreveal.shared.graphics.cssColor

// Application-wide design tokens. Feature-specific palettes stay inside their feature.
object Colors {
    val background = cssColor("#09090d")
    val backgroundRaised = cssColor("#111118")
    val surface = cssColor("#17171f")
    val surfaceSoft = cssColor("rgba(255, 255, 255, 0.055)")
    val border = cssColor("rgba(255, 255, 255, 0.12)")
    val borderStrong = cssColor("rgba(255, 255, 255, 0.2)")
    val text = cssColor("#f8f7fb")
    val textMuted = cssColor("#a7a5b2")
    val textSubtle = cssColor("#73717f")
    val accent = cssColor("#d8ff67")
    val accentInk = cssColor("#111506")
    val white: Color = Color.White
    val black: Color = Color.Black
}

object Spacing {
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 24.dp
    val xxl = 32.dp
    val huge = 48.dp
}

object Radii {
    val sm = 10.dp
    val md = 16.dp
    val lg = 24.dp
    val pill = 999.dp
}

object TypeScale {
    const val display = 40f
    const val title = 25f
    const val heading = 18f
    const val body = 15f
    const val label = 12f
    const val micro = 10f
}

object Motion {
    /** Reanimated `withSpring({ duration: 400, dampingRatio: 0.8 })`. */
    const val touchSpringDampingRatio = 0.8f
    /** stiffness = (2π / 0.4s)² for a 400 ms perceptual duration. */
    const val touchSpringStiffness = 246.74f
    const val settleDelayMillis = 260L
}

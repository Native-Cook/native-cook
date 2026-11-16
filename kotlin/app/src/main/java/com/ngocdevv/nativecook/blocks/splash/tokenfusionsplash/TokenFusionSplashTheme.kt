package com.ngocdevv.nativecook.blocks.splash.tokenfusionsplash

import kotlin.math.max
import kotlin.math.min

/** Source measurements and layout of the RN block (`tfs-theme.ts`) on the 402 x 874 pt reference canvas. */
internal object TokenFusionSplashTheme {
    const val REF_W = 402.0
    const val REF_H = 874.0
    const val SAFE_TOP = 62.0
    const val SAFE_BOTTOM = 34.0

    private fun rgb(hex: Int): FloatArray = floatArrayOf(((hex shr 16) and 255) / 255f, ((hex shr 8) and 255) / 255f, (hex and 255) / 255f)

    val page = rgb(0x232325)
    val ring = rgb(0x2C2C2F)
    val core = rgb(0x9B7BEF)
    val shade = rgb(0x6A5CE6)
    val hot = rgb(0xEDE4FF)
    val ink = rgb(0xFDFBFF)
    val tokenColors = arrayOf(rgb(0xD67C16), rgb(0x3B97E3), rgb(0x26A17B), rgb(0x1B2A8F))
    val pageInt = 0xFF232325.toInt()
    val inkInt = 0xFFFDFBFF.toInt()

    const val GRAIN = 0.025

    // the core and its orbit
    const val CORE_X = 201.0
    const val CORE_Y = 400.0
    const val CORE_R = 55.0
    val ORBIT_ANGLES = doubleArrayOf(-125.0, -35.0, 65.0, 155.0)
    const val ORBIT_WIDE_X = 176.0
    const val ORBIT_WIDE_Y = 150.0
    const val ORBIT_TIGHT_X = 150.0
    const val ORBIT_TIGHT_Y = 118.0
    const val ORBIT_TILT = -18.0
    const val WORDMARK_Y = 470.0
    const val WORDMARK_RISE = 8.0
    const val WORDMARK_SIZE = 30.0
    const val WORDMARK_TRACKING = -0.02
    const val TOUCH_CORE_LIMIT = 90.0
    const val TOUCH_EDGE = 12.0
    const val TOUCH_BACK_INSET = 20.0
    const val ATLAS_CELL = 128
    const val ATLAS_CELLS = 7
    const val ATLAS_SPREAD = 16
    const val ATLAS_PADDING = 16

    const val WORDMARK = "korra"
    const val SKIP = "Skip account linking"
    const val CORE_LABEL = "Korra core. Drag to collect tokens."

    // Original vectors, coordinates 0..100
    const val MARK = "M12 14 L32 14 L32 41 L62 14 L89 14 L51 48 L90 86 L62 86 L32 56 L32 86 L12 86 Z"

    class Token(val id: String, val label: String, val glyph: String)

    val TOKENS = listOf(
        Token("ember", "Ember token", "M50 12 A38 38 0 1 1 49.99 12 Z M50 24 A26 26 0 1 0 50.01 24 Z M50 39 A11 11 0 1 1 49.99 39 Z"),
        Token("prism", "Prism token", "M50 9 L80 50 L50 91 L20 50 Z M50 29 L35 50 L50 70 L65 50 Z"),
        Token(
            "tide", "Tide token",
            "M15 25 Q32 13 50 25 T85 25 L85 38 Q67 50 50 38 T15 38 Z M15 48 Q32 36 50 48 T85 48 L85 61 Q67 73 50 61 T15 61 Z " +
                "M15 71 Q32 59 50 71 T85 71 L85 84 Q67 96 50 84 T15 84 Z",
        ),
        Token("arc", "Arc token", "M50 8 A42 42 0 1 1 49.99 8 Z M50 17 A33 33 0 1 0 50.01 17 Z M29 44 L41 44 L41 57 L67 30 L76 39 L41 76 L29 65 Z"),
    )

    class Layout(val width: Double, val height: Double, top: Double, bottom: Double) {
        val scale = min(width / REF_W, (height - top - bottom) / 550)
        val cx = width / 2
        val cy = max(top + 200 * scale, min(height * CORE_Y / REF_H, height - bottom - 200 * scale))
        val offsetX = width / 2 - CORE_X * scale
        val offsetY = cy - CORE_Y * scale
    }
}

// The block is built for Android 12 (API 31) and newer; the demo checks before showing it.
@file:androidx.annotation.RequiresApi(android.os.Build.VERSION_CODES.S)

package com.ngocdevv.nativecook.blocks.gestures.glowknobconsole.console

import com.ngocdevv.nativecook.blocks.gestures.glowknobconsole.anim.SpringConfig

// Neutral material and channel light colors measured from the user's reference.
// Scoped to this specimen.
object Tactile {
    val warm = longArrayOf(0xFFFFF797, 0xFFFFA65D)
    val cool = longArrayOf(0xFF91F8D1, 0xFF44B3F2)

    // warm → cool per gradient stop, sampled in OKLCH at 0/.25/.5/.75/1. A plain
    // RGB mix of orange and blue passes through gray (#A2ADA7, C≈0.014) mid
    // handoff; this path keeps chroma (C≈0.136) the whole way.
    val transferAt = doubleArrayOf(0.0, 0.25, 0.5, 0.75, 1.0)
    val transfer = arrayOf(
        longArrayOf(0xFFFFF797, 0xFFE3FAA0, 0xFFC7FBAE, 0xFFAAFBBF, 0xFF91F8D1),
        longArrayOf(0xFFFFA65D, 0xFFC8BC46, 0xFF71CA80, 0xFF00C7C5, 0xFF44B3F2),
    )
    val housing = longArrayOf(0xFF2C2C2C, 0xFF282828, 0xFF252525)
    val bezel = longArrayOf(0xFF484848, 0xFF2E2E2E, 0xFF111111)
    val dial = longArrayOf(0xFF323232, 0xFF242424)
    const val well = 0xFF151515
    const val shadow = 0xFF000000
    const val groove = 0xFF161616
    const val text = 0xFFEEEEEE

    // Readout glyphs where the light passes underneath them.
    const val ink = 0xFF151515

    // The page around the specimen is white; foregrounds that sit on it
    // directly (large-text readout) use this.
    const val page = 0xFFFFFFFF
    const val pageText = 0xFF1C1C1C

    // Quiet instruction under the console (4.6:1 on the white page).
    const val hint = 0xFF737373
    const val inactive = 0xFF666666

    // Unselected selector glyph under Increase Contrast (≥4.5:1 on #222222).
    const val inactiveHigh = 0xFF9A9A9A
    val release = SpringConfig(stiffness = 420.0, damping = 32.0, mass = 0.8)
}

/** Touchline's motion tokens (`@/theme` motion), as the console uses them. */
object Motion {
    const val press = 120.0
    const val fast = 150.0
    const val base = 240.0
    const val hero = 900.0
    val easeOut = androidx.compose.animation.core.CubicBezierEasing(0.23f, 1f, 0.32f, 1f)
}

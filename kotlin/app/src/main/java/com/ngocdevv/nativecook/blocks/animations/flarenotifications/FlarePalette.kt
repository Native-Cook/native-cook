package com.ngocdevv.nativecook.blocks.animations.flarenotifications

import androidx.annotation.StringRes
import androidx.compose.ui.graphics.Color
import com.ngocdevv.nativecook.R

// Flare — notification pills that keep a small fire at their trailing cap.
// Port of flare-palette.ts. The neutrals were sampled
// from the reference clip (4K frames); they lean violet (OKLCH h≈301,
// C≈0.005), which stops the dark pill reading as plain gray next to the
// magenta. The flames are fitted blob animations (FlareBlobs), so they carry
// the reference's own colors.

data class Flare(
    val id: String,
    val title: String,
    @param:StringRes val line: Int,
    val body: Color,
    val ink: Color,
    val sub: Color,
    // The cast shadow's color; the reference draws no rim stroke.
    val shadow: Color,
)

object FlareTone {
    // The reference's page: a cool gray with the same violet lean.
    val page = Color(0xFFECEDF4)
    // Hint text on that page (#737373 only reaches 4.1:1 there).
    val pageHint = Color(0xFF666569)
    val dark = Color(0xFF212023)
    val mist = Color(0xFFF6F5F8)
    val white = Color(0xFFFFFFFF)
    // On the dark pill: the reference's #A1A0A3 (6.2:1).
    val subDark = Color(0xFFA1A0A3)
    // On the light pills: the reference's #89898B only reaches 3.2:1 on
    // #F6F5F8; this keeps the hue and clears 4.5:1.
    val subLight = Color(0xFF6B6A70)
    val inkLight = Color(0xFF0C0B0E)
    val inkDark = Color(0xFFFFFFFF)
}

/** rgba(r, g, b, a) as written in the RN palette. */
private fun rgba(r: Int, g: Int, b: Int, a: Float) = Color(r / 255f, g / 255f, b / 255f, a)

val FLARES = listOf(
    Flare(
        id = "kiln",
        title = "KILN.*",
        line = R.string.flarenotifications_kiln_line,
        body = FlareTone.dark,
        ink = FlareTone.inkDark,
        sub = FlareTone.subDark,
        shadow = rgba(22, 20, 34, 0.30f),
    ),
    Flare(
        id = "tide",
        title = "TIDE*",
        line = R.string.flarenotifications_tide_line,
        body = FlareTone.mist,
        ink = FlareTone.inkLight,
        sub = FlareTone.subLight,
        shadow = rgba(22, 20, 34, 0.11f),
    ),
    Flare(
        id = "bloom",
        title = "BLOOM.*",
        line = R.string.flarenotifications_bloom_line,
        body = FlareTone.white,
        ink = FlareTone.inkLight,
        sub = FlareTone.subLight,
        shadow = rgba(22, 20, 34, 0.10f),
    ),
)

/**
 * The motion beats, in ms and in pill heights (flareMotion in RN). Measured on
 * the 4K frames: the pill is 3.66:1, pills sit 0.39 heights apart, the copy
 * starts 0.35 heights in and the title's cap height is 0.2 heights.
 */
object FlareMotion {
    const val HEIGHT = 84f
    const val ASPECT = 3.66f
    const val GAP = 32f
    const val INSET = 29f
    const val STAGGER = 70.0
    const val ENTER = 320.0
    const val IGNITE_DELAY = 60.0
    const val FLASH = 160.0
    const val SETTLE = 700.0
    const val HOLD_AFTER = 260.0
    const val STOKE = 1300.0
    const val COOL = 650.0
    const val RELIGHT = 380.0
    // Read state: the flame is cut back to an ember reaching this far in from
    // the cap, in pill heights (the lit flame reaches ~2.1).
    const val EMBER = 0.75f
    // The status line crossfades when a note is read or relit.
    const val SWAP = 240.0
    // "Deliver again": the old notes leave before the new ones arrive.
    const val LEAVE = 180.0
    // The cast shadow, fitted to the reference's falloff (pill height 84):
    // it hangs 12 below, 6 in from each side, sigma 10, nothing above.
    const val SHADOW_DROP = 12f
    const val SHADOW_INSET = 6f
    const val SHADOW_BLUR = 10f
}

package com.ngocdevv.nativecook.blocks.onboarding.solidmorphonboarding

import android.graphics.Color
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

/** What the solid is: a regular polygon with `sides` (0 = a disc), rounded by `roundness`, optionally split by a wedge, on a base. */
class SolidMorphOnboardingSolid(val sides: Int, val roundness: Double = 0.15, val split: Boolean = false, val base: String = "dish")

/** One page of the onboarding: the solid, the engraved mark (an SVG path in a 100-unit box), the theme and the copy. */
class SolidMorphOnboardingPage(
    val solid: SolidMorphOnboardingSolid,
    val glyph: String,
    val dark: Boolean,
    val chip: String? = null,
    val title: String,
    val body: String? = null,
)

/** Measured from the inner 322 x 698 screen of the shot, normalised to 402 x 874 (RN `smo-theme.ts`). */
internal object SolidMorphOnboardingTheme {
    const val REF_W = 402.0
    const val REF_H = 874.0

    private fun rgb(hex: Int): Int = 0xFF000000.toInt() or hex

    val ink = rgb(0x0A0A0B)
    val paper = rgb(0xFCFCFB)
    val clay = rgb(0xF4F4F2)
    val clayShade = rgb(0x77777D)
    val glaze = rgb(0x080809)
    val bodyDark = rgb(0xB6B6BB)
    val bodyLight = rgb(0x424246)

    const val DISPLAY_SIZE = 40.0
    const val DISPLAY_LINE = 48.0
    const val BODY_SIZE = 16.0
    const val BODY_LINE = 22.0
    const val RADIUS_CHIP = 8.0

    const val MARGIN = 17.0
    const val PROGRESS_W = 91.0
    const val CHIP_H = 32.0
    const val CHIP_PAD = 12.0
    const val TITLE_GAP = 8.0
    const val BODY_GAP = 12.0
    const val HERO_RADIUS = 128.0
    const val BUTTON_H = 56.0
    const val BUTTON_GAP = 8.0
    const val BUTTON_R = 28.0
    const val BUTTON_BOTTOM = 24.0
    const val LARGE_TYPE = 1.35

    const val PRIMARY = "Create account"
    const val SECONDARY = "Log in"
    const val SKIP = "Skip"

    val DEFAULT_GLYPHS = listOf(
        // Infinity: one closed lemniscate, 68 units wide.
        "M50 50 C44 41 37 35 29 35 C21 35 16 42 16 50 C16 58 21 65 29 65 C37 65 44 59 50 50 C56 41 63 35 71 35 C79 35 84 42 84 50 C84 58 79 65 71 65 C63 65 56 59 50 50 Z",
        // Tick, optically centred and wide enough to read on the wedge.
        "M22 51 L41 70 L79 30",
        // Clock: dial ring and two hands at 12 and a little after 4.
        "M80 50 A30 30 0 1 1 20 50 A30 30 0 1 1 80 50 Z M50 50 L50 33 M50 50 L62 57",
    )
    const val GLYPH_STROKE = 6.0

    val DEFAULT_PAGES = listOf(
        SolidMorphOnboardingPage(
            SolidMorphOnboardingSolid(0, 1.0, false, "dish"), DEFAULT_GLYPHS[0], true, "Go global", "Your money,\nwherever you go",
            "Hold, send and receive money across currencies,\nall from one account.",
        ),
        SolidMorphOnboardingPage(
            SolidMorphOnboardingSolid(0, 1.0, true, "dish"), DEFAULT_GLYPHS[1], false, "Built to protect", "Your money,\nprotected",
            "Secure technology keeps your account and every\ntransaction protected around the clock.",
        ),
        SolidMorphOnboardingPage(
            SolidMorphOnboardingSolid(7, 0.15, false, "match"), DEFAULT_GLYPHS[2], true, "Zero delays", "Money moves,\nwhen you do",
            "Send, receive and access your money quickly\nwhenever you need it.",
        ),
    )

    fun rgba(c: Int) = floatArrayOf(Color.red(c) / 255f, Color.green(c) / 255f, Color.blue(c) / 255f, Color.alpha(c) / 255f)

    /** RN `makeLayout`: the page on the 4 / 8 grid from the safe area, the hero between the copy and the buttons, large text reflowed on a scrolling stage. */
    class Layout(val w: Double, screenH: Double, insetTop: Double, insetBottom: Double, val fontScale: Double) {
        val s = w / REF_W
        val top = max(insetTop, 24.0)
        val compact = screenH < 740 || fontScale > LARGE_TYPE
        val largeText = fontScale > LARGE_TYPE
        val progressY = top + 18 * s
        val chipY = top + (if (compact) 64 else 82) * s
        val titleY = chipY + (if (largeText) 34 * fontScale + 10 else CHIP_H + TITLE_GAP) * s
        val titleSize = (if (compact) 31.0 else DISPLAY_SIZE) * s * fontScale
        val titleHeight = titleSize * (if (largeText) ceil(2 * fontScale) * 1.26 else (2 * DISPLAY_LINE) / DISPLAY_SIZE)
        val bodyY = titleY + titleHeight + (if (largeText) 6.0 else BODY_GAP) * s
        val bodySize = BODY_SIZE * s * fontScale
        val bodyHeight = bodySize * (if (largeText) ceil(3 * fontScale + 1) * 1.35 else 3.1)
        val buttonHeight = max(BUTTON_H * s, (if (fontScale > 2.5) 17 * fontScale * 2.4 + 24 else 26 * fontScale + 24) * s)
        private val footerHeight = if (largeText) buttonHeight * 2 + BUTTON_GAP * s else buttonHeight

        /** the stage is as tall as the screen, or as the large text needs (then it scrolls) */
        val h = max(screenH, bodyY + bodyHeight + (if (largeText) 240 else 0) * s + footerHeight + insetBottom + 52 * s)
        val footerY = this.h - max(insetBottom, 20.0) - BUTTON_BOTTOM * s - footerHeight
        private val heroTop = bodyY + bodyHeight + 12 * s
        private val heroBottom = footerY - 18 * s
        val heroRadius = max(0.0, min(HERO_RADIUS * s, (heroBottom - heroTop) / 1.9))
        val heroX = w / 2
        val heroY = if (compact) (heroTop + heroBottom) / 2 else max(heroTop + heroRadius * 0.7, min(screenH * (521.0 / 874.0), heroBottom - heroRadius * 0.7))
        val skipWidth = max(48.0, 30 * fontScale + 18) * s
    }
}

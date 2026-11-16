package com.ngocdevv.nativecook.blocks.onboarding.objectsignindissolve

import kotlin.math.min

/**
 * Every colour, size and string of the RN block (`osd-theme.ts`), measured off the pin on the 402 x 874 pt canvas. Tokens
 * are [dark, light]. Layout anchors: the top group keeps its distance from the safe top, the bottom group from the bottom
 * inset, the pear stays centred on the screen's middle.
 */
internal object ObjectSigninDissolveTheme {
    const val REF_W = 402.0
    const val REF_H = 874.0
    const val SAFE_TOP = 62.0
    const val SAFE_BOTTOM = 34.0

    private fun rgb(hex: Int): Int = 0xFF000000.toInt() or hex

    val ground = intArrayOf(rgb(0x0A0610), rgb(0xFBF7EE))
    val pool = intArrayOf(rgb(0x231B2C), rgb(0xFFF1D6))
    val poolGlow = intArrayOf(rgb(0xFF3EA5), rgb(0xFFC2E0))
    val ink = intArrayOf(rgb(0xF7F3FA), rgb(0x14101A))
    val mute = intArrayOf(rgb(0x7C7585), rgb(0x8E8579))
    val legal = intArrayOf(rgb(0x5E5867), rgb(0xA39A8C))
    val pill = intArrayOf(rgb(0xFBFAFC), rgb(0x15101B))
    val pillInk = intArrayOf(rgb(0x0F0B14), rgb(0xFBF7EE))
    val half = intArrayOf(rgb(0x1A1620), rgb(0xEFE9DD))
    val halfInk = intArrayOf(rgb(0xD8D3DF), rgb(0x2A2430))
    val themeBtn = intArrayOf(rgb(0x150F1B), rgb(0xFFFFFF))
    val sun = rgb(0xF5A623)
    val moon = rgb(0xF2B86A)
    val shellWord = rgb(0xFF8CC8)
    val shellPill = rgb(0xFFB3DA)

    /** Shell, seam and seam core as 0..1 sRGB triples for the shader. */
    val shell = floatArrayOf(0xFF / 255f, 0x3E / 255f, 0xA5 / 255f)
    val seam = floatArrayOf(0xFF / 255f, 0xD2 / 255f, 0x3E / 255f)
    val seamCore = floatArrayOf(0xFF / 255f, 0xF6 / 255f, 0xD8 / 255f)

    // type sizes (pt)
    const val WORDMARK_SIZE = 54.0
    const val TAGLINE_SIZE = 13.0
    const val BUTTON_SIZE = 16.0
    const val HALF_SIZE = 14.0
    const val LEGAL_SIZE = 11.0

    // rest layout (pt, 402 canvas); y of the pear is its centre
    const val PEAR_CY = 338.0
    const val THEME_CX = 360.0
    const val THEME_CY = 81.0
    const val THEME_D = 40.0
    const val WORDMARK_BASELINE = 598.0
    const val TAGLINE_BASELINE = 627.0
    const val PILL_X = 28.0
    const val PILL_Y = 662.0
    const val PILL_W = 346.0
    const val PILL_H = 56.0
    const val PILL_R = 16.0
    const val HALF_Y = 731.0
    const val HALF_H = 48.0
    const val HALF_W = 165.0
    const val HALF_R = 14.0
    const val LEGAL_Y1 = 817.0
    const val LEGAL_Y2 = 834.0
    const val POOL_R = 190.0

    const val WORDMARK = "Pome"
    const val TAGLINE = "Taste comes first"
    const val CONTINUE = "Continue with email"
    const val PASSKEY = "Passkey"
    const val PHONE = "Phone"
    const val LEGAL_A = "By continuing you agree to our "
    const val TERMS = "Terms"
    const val AND = " and "
    const val PRIVACY = "Privacy Policy"
    const val LEGAL_B = "Ripe is a decision. Make it."
    const val TO_DARK = "Switch to dark theme"
    const val TO_LIGHT = "Switch to light theme"

    /** Layout for a W x H screen: x scales with width, the top group hangs from the inset, the bottom group from the bottom. */
    class Layout(val w: Double, val h: Double, private val insetTop: Double, private val insetBottom: Double) {
        val s = min(w / REF_W, 1.12)
        fun x(v: Double) = (w - REF_W * s) / 2 + v * s
        fun yt(y: Double) = insetTop + (y - SAFE_TOP) * s
        fun yb(y: Double) = h - insetBottom - (REF_H - SAFE_BOTTOM - y) * s
        fun yc(y: Double) = h * (PEAR_CY / REF_H) + (y - PEAR_CY) * s
    }
}

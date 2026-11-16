package com.ngocdevv.nativecook.blocks.cards.cardvacuumseal

import android.graphics.Color
import kotlin.math.max
import kotlin.math.min

/**
 * Every colour and box of the RN block (`cvs-theme.ts`), measured on the pin (Dribbble 27250589, rectified onto the 402 x 874 pt canvas).
 * Boxes are design pt on that canvas; `yt` anchors are top-of-screen.
 */
internal object CardVacuumSealTheme {
    const val REF_W = 402.0
    const val REF_H = 874.0
    const val SAFE_TOP = 62.0

    private fun rgb(hex: Int): Int = 0xFF000000.toInt() or hex

    val page = rgb(0x0A0A0C)
    val bloom = rgb(0x6B3FD8)
    val tile = rgb(0x1C1B20)
    val ink = rgb(0xF4F3F7)
    val inkMute = rgb(0x8E8D96)
    val cardLight = rgb(0x171C2E)
    val cardMid = rgb(0x0D111F)
    val cardDark = rgb(0x06080F)
    val letter = Color.argb(235, 233, 236, 244)
    val holderInk = Color.argb(204, 233, 236, 244)
    val red = rgb(0xDA3540)
    val redFlash = rgb(0xF7B8C0)
    val amber = rgb(0xD8B43E)
    val amberInk = rgb(0x2A2208)
    val violet = rgb(0x8A6FFB)
    val magenta = rgb(0xE04DEE)
    val blue = rgb(0xA9C2EA)
    val blueDeep = rgb(0x8FA8DA)
    val lilac = rgb(0xE08BEF)
    val lilacLight = rgb(0xF0B2F6)
    val gold = rgb(0xD8B43E)
    val green = rgb(0x35D27E)
    val film = floatArrayOf(0xC9 / 255f, 0xB8 / 255f, 0xFF / 255f)
    val gel = rgb(0xD2BCFF)
    val shadow = rgb(0x050308)

    // the tile discs: three-stop gradients (light upper left, deeper lower right)
    val DISC_VIOLET = intArrayOf(violet, rgb(0xB25CF6), magenta)
    val DISC_RED = intArrayOf(rgb(0xF0525A), red, rgb(0xB8242F))
    val DISC_AMBER = intArrayOf(rgb(0xF2D46A), amber, rgb(0xB9922A))
    val DISC_ICE = intArrayOf(rgb(0xF2F6FF), rgb(0xC3D5F3), blueDeep)

    // type: size, tracking (em)
    const val TITLE_SIZE = 17.0
    const val TITLE_TRACK = -0.013
    const val TILE_SIZE = 14.0
    const val TILE_TRACK = -0.004
    const val LABEL_SIZE = 15.0
    const val LABEL_TRACK = -0.006
    const val FIGURE_SIZE = 40.0
    const val FIGURE_TRACK = -0.022
    const val HEADING_SIZE = 19.0
    const val HEADING_TRACK = -0.017
    const val NAME_SIZE = 16.0
    const val NAME_TRACK = -0.01
    const val AMOUNT_SIZE = 16.0
    const val AMOUNT_TRACK = -0.01
    const val SMALL_SIZE = 13.0
    const val WORDMARK_SIZE = 28.0
    const val WORDMARK_TRACK = -0.02
    const val HOLDER_SIZE = 18.0
    const val HOLDER_TRACK = 0.04

    const val RADIUS_CARD = 16.0
    const val RADIUS_TILE = 26.0
    const val RADIUS_BADGE = 6.0

    const val LIGHT_AZ = 315.0

    // boxes
    const val HEADER_CY = 84.0
    const val HEADER_ARROW_X = 24.0
    const val HEADER_ARROW_SIZE = 22.0
    const val HEADER_CLOSE_X = 346.0
    const val HEADER_CLOSE_D = 32.0
    const val CARD_X = 24.0
    const val CARD_Y = 128.0
    const val CARD_W = 354.0
    const val CARD_H = 223.0
    const val CARD_PAD_X = 20.0
    const val CARD_WORD_BASE = 50.0
    const val CARD_HOLDER_BASE = 199.0
    const val TILES_Y = 367.0
    const val TILES_W = 112.0
    const val TILES_H = 112.0
    const val TILES_GAP = 9.0
    const val TILES_DISC_D = 56.0
    const val TILES_DISC_TOP = 14.0
    const val TILES_LABEL_BASE = 94.0
    const val REV_Y = 491.0
    const val REV_H = 116.0
    const val REV_PAD_X = 20.0
    const val REV_BADGE = 22.0
    const val REV_BADGE_Y = 22.0
    const val REV_FIGURE_BASE = 92.0
    const val REV_BARS_X0 = 190.0
    const val REV_BARS_X1 = 356.0
    const val REV_BARS_BASE = 92.0
    const val LIST_Y = 619.0
    const val LIST_PAD_X = 20.0
    const val LIST_HEAD_BASE = 42.0
    const val LIST_ROW_Y = 66.0
    const val LIST_ROW_H = 64.0
    const val LIST_AVATAR = 44.0
    const val LIST_AMOUNT_W = 82.0
    const val LIST_TEXT_GAP = 12.0

    // the film's canvas covers the card plus this room (design units): the sheet falls from above and the bubble leaves below
    const val PAD_L = 40.0
    const val PAD_T = 100.0
    const val PAD_R = 40.0
    const val PAD_B = 80.0
    const val FILM_LIP = 6.0
    const val FILM_EAR = 14.0

    const val TITLE = "Cards"
    const val BANK = "Halden"
    const val HOLDER = "NORTH.STUDIO"
    const val DETAILS = "Show details"
    const val PROTECT = "Protect Card"
    const val PROTECTED = "Protected"
    const val SETTINGS = "Settings"
    const val REVENUE = "Revenue"
    const val REVENUE_VALUE = "42,220"
    const val LIST = "Recent transactions"
    const val SEE_ALL = "See all"

    class Row(val name: String, val note: String, val amount: String, val status: String)

    val ROWS = listOf(
        Row("Tidewell Prints", "Money received", "\$739.65", "Completed"),
        Row("Oren Calloway", "Money received", "\$943.65", "Completed"),
    )

    const val A11Y_BACK = "Back"
    const val A11Y_CLOSE = "Close"
    const val A11Y_PROTECT = "Protect card"
    const val A11Y_PROTECT_HINT = "Seals the card so it cannot be used. Hold to watch the film tighten."
    const val A11Y_SEALED = "Card protected"
    const val A11Y_OPEN = "Card protection off"
    fun a11yCard(sealed: Boolean) = "Halden card, North Studio" + if (sealed) ", protected" else ""

    /** bar heights of the revenue chart, design pt: dots that grow into bars */
    val BARS = doubleArrayOf(3.0, 3.0, 3.0, 3.0, 3.0, 3.0, 3.0, 3.0, 4.0, 6.0, 9.0, 12.0, 15.0, 22.0, 18.0, 30.0, 26.0, 38.0, 33.0, 46.0, 40.0, 56.0, 50.0, 64.0)

    private fun mixC(a: Int, b: Int, t: Double): Int {
        fun ch(x: Int, y: Int) = Math.round(x * (1 - t) + y * t).toInt()
        return Color.rgb(ch(Color.red(a), Color.red(b)), ch(Color.green(a), Color.green(b)), ch(Color.blue(a), Color.blue(b)))
    }

    /** the lower page sits in shade (the pin's light falls off): the page colour over the tile fill, 0 at design y 470 -> 0.30 at 874. */
    fun panelShade(yTop: Double, yBottom: Double): IntArray {
        fun at(y: Double) = min(1.0, max(0.0, (y - 470) / 404)) * 0.3
        return intArrayOf(mixC(tile, page, at(yTop)), mixC(tile, page, at(yBottom)))
    }

    /** design pt -> screen dp: x scales with width, y keeps its distance from the top inset */
    class Layout(val w: Double, val h: Double, val top: Double, val bottom: Double) {
        val s = w / REF_W
        fun yt(v: Double) = top + (v - SAFE_TOP) * s

        /** the page is as tall as the screen, or as the list needs (then it scrolls) */
        val contentH = max(h, top + (LIST_Y + LIST_ROW_Y + 2 * LIST_ROW_H + 12 - SAFE_TOP) * s + bottom)
    }
}

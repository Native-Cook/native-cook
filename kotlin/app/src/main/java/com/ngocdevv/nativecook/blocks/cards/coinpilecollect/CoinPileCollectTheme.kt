package com.ngocdevv.nativecook.blocks.cards.coinpilecollect

import kotlin.math.max
import kotlin.math.min

// Port of cpc-theme.ts: every measured number and colour of the screen (402 x 874 dp design canvas).

internal object CpcTheme {
    const val REF_W = 402.0
    const val REF_H = 874.0
    const val SAFE_TOP = 62.0
    const val CONTENT_H = 714.0

    fun rgb(hex: Long): Int = (0xFF000000L or hex).toInt()
    fun f3(hex: Long) = floatArrayOf(((hex shr 16) and 255) / 255f, ((hex shr 8) and 255) / 255f, (hex and 255) / 255f)

    const val PAGE = 0x07040CL
    const val PAGE_GLOW = 0x22103CL
    const val PAGE_GLOW2 = 0x04040CL
    const val INK = 0xF7F4EEL
    const val TEAL = 0x1E547AL
    const val LIME = 0x6CA824L
    const val LIME_INK = 0xF7FBEAL
    const val MAGENTA_TOP = 0xA94696L
    const val MAGENTA_BOTTOM = 0x8F3A84L
    const val NAVY_TOP = 0x2B3868L
    const val NAVY_BOTTOM = 0x10173DL
    const val AMBER = 0xF4C13AL
    const val AMBER_LIT = 0xF8D878L
    const val PILL_INK = 0x3A2600L
    const val COIN_FACE_A = 0x1D5666L
    const val COIN_FACE_B = 0x0A242DL
    const val COIN_RIM = 0x2F7C96L
    const val CYAN = 0x3FE9FFL
    const val PINK = 0xC8689FL
    const val COPPER = 0xE6AE8EL
    const val PINK_RIM = 0xE2A0B8L
    const val DISC_A = 0xB7F04AL
    const val DISC_B = 0xF2B92EL
    const val DISC_C = 0x7C6CF0L
    const val DISC_INK = 0x14131CL
    const val CARD_TOP = 0x0B1450L
    const val CARD_BOTTOM = 0x1D2D86L
    const val SHADOW = 0x03141BL
    const val COIN_SHADOW = 0x3A0C36L
    const val PILL_TOP = 0xF7C943L
    const val PILL_BOTTOM = 0xF1B92EL

    // type: size, lineHeight, tracking (em)
    const val TITLE_SIZE = 56f
    const val TITLE_LH = 56f
    const val TITLE_TR = -0.035f
    const val TILE_SIZE = 20f
    const val TILE_LH = 24f
    const val TILE_TR = -0.012f
    const val COPY_SIZE = 15f
    const val COPY_TR = -0.004f
    const val AMOUNT_SIZE = 30f
    const val AMOUNT_TR = -0.01f
    const val PILL_SIZE = 17f
    const val PILL_TR = -0.006f
    const val SMALL_SIZE = 17f
    const val SMALL_LH = 20f
    const val SMALL_TR = -0.008f
    const val PREMIUM_SIZE = 34f
    const val PREMIUM_LH = 36f
    const val PREMIUM_TR = -0.03f

    const val R_TEAL = 40.0
    const val R_TILE = 36.0

    // boxes
    const val AV_R = 20.0
    const val AV_PITCH = 28.0
    const val AV_RIGHT = 20.0
    const val AV_Y = 66.0
    const val BACK_X = 24.0
    const val BACK_Y = 118.0
    const val TITLE_X = 24.0
    const val TITLE_CAPTOP = 158.0
    const val LIME_X = 24.0
    const val LIME_Y = 290.0
    const val LIME_W = 172.0
    const val LIME_H = 180.0
    const val TEAL_X = 204.0
    const val TEAL_Y = 250.0
    const val TEAL_W = 174.0
    const val TEAL_H = 340.0
    const val MAG_X = 24.0
    const val MAG_Y = 478.0
    const val MAG_W = 172.0
    const val MAG_H = 162.0
    const val NAVY_X = 204.0
    const val NAVY_Y = 598.0
    const val NAVY_W = 174.0
    const val NAVY_H = 162.0
    const val PREMIUM_X = 24.0
    const val PREMIUM_Y = 664.0
    const val COPY_X = 18.0
    const val EARNED_BASE = 34.0
    const val AMOUNT_BASE = 68.0
    const val PERIOD_BASE = 88.0
    const val PILL_X = 24.0
    const val PILL_Y = 268.0
    const val PILL_W = 150.0
    const val PILL_H = 60.0
    const val MOUTH_X = 99.0
    const val MOUTH_Y = 282.0
    const val KEEP_W = 174.0
    const val KEEP_H = 84.0
    const val COIN_R = 35.0
    const val COIN_H = 4.2
    const val COIN_COUNT = 26
    const val X_CX = 88.0
    const val X_CY = 54.0
    const val X_R = 50.0
    const val X_H = 5.0
    const val SEED = 2

    const val AMOUNT_CENTS = 22034
    const val TITLE_1 = "Today’s"
    const val TITLE_2 = "offers"
    const val EARNED = "You earned"
    const val PERIOD = "in September"
    const val COLLECT = "Collect"
    const val PROTECT_1 = "Protect your"
    const val PROTECT_2 = "funds"
    const val EXCHANGE_1 = "Exchange"
    const val EXCHANGE_2 = "currency"
    const val PREMIUM_1 = "Get your"
    const val PREMIUM_2 = "Premium"
    const val PREMIUM_3 = "card"
    const val BANK = "halden"
    const val CARD_URL = "HALDEN.CARD"
    val MONOGRAMS = listOf("A", "M", "K")
    const val A11Y_TITLE = "Today’s offers"
    const val A11Y_EMPTY = "Nothing left to collect"
    const val HINT_COLLECT = "Hold to pour the coins into the button, or tap to collect everything."
    fun a11yCollect(a: String) = "Collect $a"
    fun a11yAmount(a: String) = "You earned $a in September"

    fun formatMoney(cents: Double): String = "$" + String.format(java.util.Locale.US, "%.2f", max(cents, 0.0) / 100.0)

    fun fontScaleOf(system: Float): Float = min(max(system, 1f), 1.12f)

    class Layout(val w: Double, val h: Double, val s: Double, val ox: Double, val oy: Double, val insetTop: Double, val insetBottom: Double)

    fun makeLayout(w: Double, h: Double, insetTop: Double, insetBottom: Double): Layout {
        val availH = max(h - insetTop - insetBottom, 1.0)
        val s = min(w / REF_W, availH / CONTENT_H)
        val ox = (w - REF_W * s) / 2
        val oy = insetTop - SAFE_TOP * s
        return Layout(w, h, s, ox, oy, insetTop, insetBottom)
    }
}

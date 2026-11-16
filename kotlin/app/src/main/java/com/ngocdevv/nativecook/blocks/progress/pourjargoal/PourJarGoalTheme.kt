package com.ngocdevv.nativecook.blocks.progress.pourjargoal

import kotlin.math.max
import kotlin.math.min

/** Colours, type, layout numbers and copy of the RN block (`pjg-theme.ts`), measured on a 402 x 874 pt screen. */
internal object PourJarGoalTheme {
    const val REF_W = 402.0
    const val REF_H = 874.0
    const val SAFE_TOP = 62.0

    const val PAGE = 0xFFFBF1EE.toInt()
    const val INK = 0xFF101D29.toInt()
    const val MUTE = 0xFF647180.toInt()
    const val SURFACE = 0xFFFF9A7A.toInt()
    const val DEPTH = 0xFFFF4A30.toInt()
    const val WHITE = 0xFFFDFEFE.toInt()
    const val OUTLINE = 0xFFDFE3E8.toInt()
    const val TOAST = 0xFF2E3844.toInt()

    const val DISPLAY_SIZE = 80.0
    const val BODY_SIZE = 15.0
    const val LABEL_SIZE = 12.0
    const val TITLE_SIZE = 17.0
    const val STAT_SIZE = 23.0

    const val CARD_RADIUS = 20.0
    const val PILL_RADIUS = 22.0

    const val HEADER_Y = 54.0
    const val JAR_H = 419.0
    const val CARD_X = 22.0
    const val CARD_W = 172.0
    const val CARD_H = 81.0
    const val CARD_GAP = 14.0
    const val BUTTONS_X = 22.0
    const val BUTTONS_W = 358.0
    const val BUTTONS_H = 57.0
    const val PRIMARY_W = 234.0

    const val VESSEL_W = 222.0
    const val VESSEL_H = 422.0
    const val VESSEL_TOP_R = 68.0
    const val VESSEL_BOTTOM_R = 70.0

    const val TITLE = "Sora, spring 2027"
    const val GOAL = "Sora"
    const val REMAINING = "Left to go"
    const val WEEKLY = "At \$40 a week"
    const val POUR = "Pour in"
    const val AUTO = "Auto"
    const val AUTO_ON = "Auto on"
    const val BACK = "Back to goals"
    const val MORE = "Goal options"
    const val ADJUSTABLE = "Savings goal. Drag the liquid to add money. Swipe up or down to adjust, then activate to pour."

    /** (x / width, fraction of the filled column, radius in pt) of the 12 bubbles. */
    val BUBBLE_SEEDS = arrayOf(
        doubleArrayOf(0.541, 0.75, 4.5), doubleArrayOf(0.384, 0.572, 8.9), doubleArrayOf(0.623, 0.484, 7.2),
        doubleArrayOf(0.246, 0.395, 4.5), doubleArrayOf(0.493, 0.307, 2.7), doubleArrayOf(0.715, 0.216, 8.9),
        doubleArrayOf(0.323, 0.125, 7.2), doubleArrayOf(0.594, 0.036, 5.4), doubleArrayOf(0.809, 0.661, 2.7),
        doubleArrayOf(0.42, 0.84, 0.8), doubleArrayOf(0.29, 0.45, 0.7), doubleArrayOf(0.76, 0.37, 0.8),
    )

    class Layout(
        val w: Double, val h: Double, val s: Double, val x0: Double, val headerY: Double, val buttonY: Double, val buttonH: Double,
        val cardsY: Double, val cardH: Double, val subtitleY: Double, val amountBaseline: Double,
        val jarX: Double, val jarY: Double, val jarW: Double, val jarH: Double,
        val largeType: Boolean, val largestType: Boolean, val typeScale: Double,
    ) {
        fun x(n: Double) = x0 + n * s
    }

    fun makeLayout(w: Double, h: Double, insetTop: Double, insetBottom: Double, fontScale: Double): Layout {
        val s = min(w / REF_W, 1.0)
        val x0 = (w - REF_W * s) / 2
        val headerY = max(if (insetTop < 40) insetTop + 8 else 0.0, (HEADER_Y - SAFE_TOP) * s + insetTop)
        val largestType = fontScale > 2
        val buttonH = (BUTTONS_H + max(0.0, fontScale - 2) * 24) * s
        val buttonY = h - max(insetBottom, 12.0) - 9 - buttonH
        val largeType = fontScale > 1.35
        val cardH = (if (largeType) 2 * max(94.0, 24 + fontScale * (12 * 0.9 + 23 * 0.95)) + 8 else CARD_H) * s
        val cardsY = buttonY - 21 * s - cardH
        val subtitleY = cardsY - (if (largestType) BODY_SIZE * fontScale * 2.6 + 16 else 38.0) * s
        val amountBaseline = subtitleY - (if (largeType) 28 else 20) * s
        val jarBottom = amountBaseline - 78 * s
        val headerH = (if (largestType) TITLE_SIZE * fontScale * 3.3 + 16 else 60.0) * s
        val jarTop = max(headerY + headerH, jarBottom - JAR_H * s)
        val jarH = max(140.0, jarBottom - jarTop)
        val jarW = VESSEL_W * s
        return Layout(w, h, s, x0, headerY, buttonY, buttonH, cardsY, cardH, subtitleY, amountBaseline, w / 2 - jarW / 2, jarTop, jarW, jarH, largeType, largestType, fontScale)
    }
}

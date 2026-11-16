package com.ngocdevv.nativecook.blocks.charts.sparkspherescore

import kotlin.math.max
import kotlin.math.min

internal class SparkSphereScoreType(val family: String, val size: Double, val tracking: Double, val lineHeight: Double)

internal class SparkSphereScoreBand(val from: Double, val title: String, val body: String)

/** The RN block's tokens (`sss-theme.ts`): measured on the pin, in pt on the 402 x 874 canvas; [makeLayout] scales them to the device. */
internal object SparkSphereScoreTheme {
    const val REF_W = 402.0
    const val REF_H = 874.0
    const val SHIFT = 25.4

    const val GROUND0 = 0xFF08080A.toInt()
    const val GROUND1 = 0xFF111518.toInt()
    const val INK = 0xFFFDFEFE.toInt()
    const val INK_SOFT = 0xFFC4C2C5.toInt()
    const val MUTE = 0xFF8C8C8E.toInt()
    const val MUTE_ICON = 0xFF949496.toInt()
    const val LABEL = 0xFF93979A.toInt()
    const val DIM = 0xFF3F3F41.toInt()
    const val CARD = 0xFF272B2E.toInt()
    const val CARD_TITLE = 0xFFF9FBFB.toInt()
    const val CARD_BODY = 0xFFC6CACD.toInt()
    const val TICK = 0xFF32373A.toInt()
    const val TICK_MARK = 0xFF464B4F.toInt()
    const val CURSOR = 0xFFF7FBFC.toInt()
    const val SLOW_DOT = 0xFF606467.toInt()
    const val FAST_BLOB = 0xFF6E7275.toInt()

    fun rgb(argb: Int): DoubleArray =
        doubleArrayOf(((argb shr 16) and 255) / 255.0, ((argb shr 8) and 255) / 255.0, (argb and 255) / 255.0)

    val TITLE = SparkSphereScoreType("FigtreeBold", 13.0, 0.083, 18.0)
    val SUBTITLE = SparkSphereScoreType("FigtreeMedium", 11.0, 0.005, 16.0)
    val DATE = SparkSphereScoreType("FigtreeBold", 12.0, 0.091, 16.0)
    val SCORE = SparkSphereScoreType("BarlowBold", 44.3, 0.0, 48.0)
    val SCORE_LABEL = SparkSphereScoreType("FigtreeBold", 15.4, 0.094, 20.0)
    val DELTA_NUM = SparkSphereScoreType("BarlowBold", 16.0, 0.0, 20.0)
    val DELTA_TEXT = SparkSphereScoreType("BarlowSemi", 14.5, 0.01, 20.0)
    val PACE = SparkSphereScoreType("FigtreeBold", 12.0, 0.082, 16.0)
    val END = SparkSphereScoreType("FigtreeMedium", 12.0, -0.01, 16.0)
    val CURSOR_T = SparkSphereScoreType("BarlowBold", 22.0, 0.0, 26.0)
    val TICK_LABEL = SparkSphereScoreType("FigtreeMedium", 10.5, 0.0, 14.0)
    val CARD_TITLE_T = SparkSphereScoreType("FigtreeSemi", 15.0, -0.015, 20.0)
    val CARD_BODY_T = SparkSphereScoreType("FigtreeMedium", 14.5, 0.018, 20.0)

    // nav row
    const val BACK_X = 27.7
    const val BACK_CY = 83.1
    const val BACK_W = 12.3
    const val BACK_H = 23.1
    const val BACK_STROKE = 2.4
    const val TITLE_BASE = 79.2
    const val SUBTITLE_BASE = 93.9
    const val INFO_CX = 365.8
    const val INFO_CY = 82.8
    const val INFO_D = 28.5
    const val INFO_STROKE = 1.5
    // date range
    const val DATE_BASE = 132.3
    const val CHEV_CY = 127.8
    const val CHEV_L = 121.2
    const val CHEV_R = 279.6
    const val CHEV_W = 7.5
    const val CHEV_H = 11.5
    // the disc
    const val DISC_R = 163.6
    const val DISC_CY = 345.8
    // centre text, baselines relative to the disc centre
    const val SCORE_BASE = -8.1
    const val LABEL_BASE = 18.1
    const val DELTA_BASE = 40.8
    // pace of aging
    const val PACE_X = 21.5
    const val PACE_BASE = 571.6
    const val ROW_BASE = 595.4
    const val ROW_CY = 590.4
    const val ROW_DOT_X = 28.1
    const val ROW_DOT_D = 14.6
    const val ROW_SLOW_X = 40.0
    const val ROW_FAST_RIGHT = 360.0
    const val ROW_BLOB_CX = 374.3
    const val ROW_BLOB_W = 13.1
    const val ROW_BLOB_H = 14.6
    const val RULER_X0 = 33.9
    const val RULER_PITCH = 4.174
    const val RULER_COUNT = 81
    const val RULER_CY = 617.8
    const val RULER_TICK_H = 24.0
    const val RULER_TICK_W = 1.5
    const val RULER_CURSOR_H = 32.3
    const val RULER_CURSOR_W = 2.4
    const val RULER_CURSOR_BASE = 594.6
    const val TICK_LABEL_BASE = 646.9
    val TICK_LABEL_X = doubleArrayOf(23.1, 189.2, 358.5)
    // insight card
    const val CARD_X = 16.9
    const val CARD_W = 368.5
    const val CARD_TOP = 685.4
    const val CARD_R = 12.0
    const val NOTCH_H = 11.0
    const val NOTCH_HALF = 12.0
    const val NOTCH_OFFSET = 16.0
    const val CARD_TITLE_BASE = 721.3
    const val CARD_BODY_BASE = 748.6
    const val CARD_PITCH = 20.0
    const val CARD_PAD_X = 16.9
    const val CARD_BOTTOM = 810.0

    const val TITLE_COPY = "LONGEVITY"
    const val SUBTITLE_COPY = "Next update in 6 days"
    const val DATE_COPY = "AUG 19  -  AUG 26"
    const val SCORE_LABEL_COPY = "VITAL AGE"
    const val PACE_COPY = "PACE OF AGING"
    const val SLOW = "Slow"
    const val FAST = "Fast"
    val ENDS = listOf("-1.0x", "1.0x", "3.0x")
    const val YOUNGER = "years younger"
    const val OLDER = "years older"
    const val RULER_LABEL = "Pace of aging"
    const val RULER_HINT = "Swipe up or down to change the pace by a tenth."

    val BANDS = listOf(
        SparkSphereScoreBand(
            Double.NEGATIVE_INFINITY, "Turning back the clock",
            "At this pace your body is rewinding. Sleep, recovery and training would all have to line up for weeks.",
        ),
        SparkSphereScoreBand(
            0.0, "Steady and Healthy",
            "Your pace of aging is slower than the calendar, mostly thanks to steadier sleep and recovery. Keep your current habits to stay here.",
        ),
        SparkSphereScoreBand(
            1.0, "Running fast",
            "At this pace the years add up quicker than they should. Shorter nights and a heavy training load are the usual cause.",
        ),
        SparkSphereScoreBand(
            2.0, "Burning the candle",
            "Held for long, this pace costs you years. A few quieter days and an earlier night would bring it down fast.",
        ),
    )

    class Layout(
        val width: Double, val height: Double, val s: Double, val sc: Double, val k: Double, val ts: Double, val cd: Double,
        val lines: Int, val fontScale: Double, val oy: Double,
        val discCx: Double, val discCy: Double, val discR: Double, val discScale: Double,
        val rulerX0: Double, val rulerPitch: Double, val rulerCy: Double, val tickH: Double, val cursorH: Double, val tickW: Double, val cursorW: Double,
        val cardX: Double, val cardW: Double, val cardTop: Double, val cardH: Double, val cardR: Double, val notchH: Double, val notchHalf: Double,
        val cardTitleBase: Double, val cardBodyBase: Double, val cardPitch: Double, val cardPadX: Double,
        val paceBase: Double, val rowBase: Double, val rowCy: Double, val cursorBase: Double, val tickLabelBase: Double, val notchApex: Double,
        val paceTop: Double,
    ) {
        fun x(v: Double) = (v - REF_W / 2) * s + width / 2
        fun yA(y: Double) = oy + y * sc
        fun yP(y: Double) = paceTop + (y - (PACE_BASE - 8.4)) * sc
        fun centreBase(off: Double) = discCy + off * ts * sc
        fun cardY(y: Double) = cardTop + (y - CARD_TOP) * sc
    }

    /**
     * The screen's geometry for a device. The top group is anchored under the status bar, the card to the bottom margin; a tall phone
     * spends the extra height in the gaps, a short one first closes the gaps, then shrinks the disc (never below 80 %), then the
     * card's copy to two lines. Heights in screen dp.
     */
    fun makeLayout(width: Double, height: Double, insetTop: Double, insetBottom: Double, fontScale: Double): Layout {
        val sS = width / REF_W
        val fs = min(max(fontScale, 1.0), 1.2)
        val topPad = 4 * sS
        val botPad = 16 * sS
        val avail = height - insetTop - insetBottom - topPad - botPad
        val natTop = 66.0
        val natGapB = DISC_CY - DISC_R - (DATE_BASE + 2)
        val natGapD = PACE_BASE - 8.4 - (DISC_CY + DISC_R)
        val natGapF = CARD_TOP - NOTCH_H - (TICK_LABEL_BASE + 4)
        val bodyLinesNat = if (fs > 1.08) 4 else 3
        val cardHNat = CARD_BOTTOM - CARD_TOP + (bodyLinesNat - 3) * CARD_PITCH * fs
        val natTotal = CARD_BOTTOM - natTop + (bodyLinesNat - 3) * CARD_PITCH * fs
        var gapB = natGapB
        var gapD = natGapD
        var gapF = natGapF
        var cd = 1.0
        var lines = bodyLinesNat
        var cardH = cardHNat
        var sc = sS
        val need = natTotal * sS
        if (need <= avail) {
            val extra = min(max((avail - need) / sS - 14, 0.0), 40.0)
            gapB += extra * 0.3
            gapD += extra * 0.45
            gapF += extra * 0.1
        } else {
            var deficit = (need - avail) / sS
            fun take(room: Double): Double {
                val t = min(room, max(deficit, 0.0))
                deficit -= t
                return t
            }
            gapB -= take(natGapB - 24)
            gapD -= take(natGapD - 26)
            val dr = take(DISC_R * 2 * 0.22)
            cd = 1 - dr / (DISC_R * 2)
            gapF -= take(natGapF - 12)
            if (deficit > 0 && lines > 2) {
                val drop = lines - 2
                deficit -= CARD_PITCH * drop
                lines = 2
                cardH -= CARD_PITCH * drop
            }
            if (deficit > 0) {
                val kk = (natTotal - deficit - (if (lines == 2) CARD_PITCH else 0.0) - (natGapB - gapB) - (natGapD - gapD)) / natTotal
                sc = sS * max(0.78, min(1.0, kk))
            }
        }
        val ts = max(0.86, cd)
        val oy = insetTop + topPad - natTop * sc
        fun yA(y: Double) = oy + y * sc
        val discTop = yA(DATE_BASE + 2) + gapB * sc
        val r = DISC_R * cd * sc
        val discCy = discTop + r
        val paceTop = discCy + r + gapD * sc
        fun yP(y: Double) = paceTop + (y - (PACE_BASE - 8.4)) * sc
        val notchApex = yP(TICK_LABEL_BASE + 4) + gapF * sc
        val cardTop = notchApex + NOTCH_H * sc
        fun cardYOf(y: Double) = cardTop + (y - CARD_TOP) * sc
        fun xx(v: Double) = (v - REF_W / 2) * sS + width / 2
        return Layout(
            width, height, sS, sc, sc / sS, ts, cd, lines, fs, oy,
            width / 2, discCy, r, (DISC_R * cd * sc) / DISC_R,
            xx(RULER_X0), RULER_PITCH * sS, yP(RULER_CY), RULER_TICK_H * sc, RULER_CURSOR_H * sc, RULER_TICK_W * sS, RULER_CURSOR_W * sS,
            xx(CARD_X), CARD_W * sS, cardTop, cardH * sc, CARD_R * sS, NOTCH_H * sc, NOTCH_HALF * sS,
            cardYOf(CARD_TITLE_BASE), cardYOf(CARD_BODY_BASE), CARD_PITCH * sc, CARD_PAD_X * sS,
            yP(PACE_BASE), yP(ROW_BASE), yP(ROW_CY), yP(RULER_CURSOR_BASE), yP(TICK_LABEL_BASE), notchApex,
            paceTop,
        )
    }
}

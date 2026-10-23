package com.ngocdevv.nativecook.blocks.pickers.stampdiarycalendar

/**
 * Stamp Diary Calendar: every size and position of the screen, from theme.ts.
 * Positions are reference points: the pin's screen at 402 x 874 pt whose top safe area is 62 pt.
 * Plain Kotlin (no Android types), so the regression harness can run it on the JVM.
 */
internal object StampDiaryCalendarTheme {
    const val REF_WIDTH = 402.0
    const val REF_HEIGHT = 874.0
    const val REF_SAFE_TOP = 62.0

    const val TITLE_INSET = 18.5
    const val HEADER_CENTER_INSET = 36.0
    const val HEADER_CENTER_Y = 96.0
    const val PAPER_PITCH = 28.0
    const val PAPER_PHASE_X = 26.0
    const val PAPER_PHASE_Y = 1.3
    const val SELECTION_INSET = 4.0

    const val HEADER_RADIUS = 22.0
    const val SELECTION_RADIUS = 8.0

    const val HEADER_SIZE = 44.0
    const val CLOSE_GLYPH = 14.0
    const val CLOSE_STROKE = 2.0
    const val DOT = 3.6
    const val DOT_GAP = 7.6
    const val GRID_LINE = 1.0
    const val SELECTION_LINE = 1.0

    /** Smoked glass blur radius in dp standing in for expo-blur intensity 24, tint dark. */
    const val GLASS_BLUR_DP = 14.0

    const val MENU_WIDTH = 196.0
    const val MENU_ROW = 44.0
    const val MENU_RADIUS = 16.0
    const val MENU_GAP = 8.0
    const val MENU_PAD_X = 16.0
    const val MENU_TEXT_SIZE = 15.0
    const val MENU_SCALE_FROM = 0.96
    const val MENU_IN_MS = 160
    const val MENU_OUT_MS = 120

    const val WEEKDAY_TOP = 452.5
    const val GRID_TOP = 488.5
    const val ROW_HEIGHT = 55.33
    const val ROWS = 6
    const val COLUMN_WIDTH = 402.0 / 7.0
    const val HERO_BOTTOM = WEEKDAY_TOP

    const val WEEKDAY_SIZE = 9.5
    const val DAY_SIZE = 11.0
    const val YEAR_SIZE = 17.0
    const val YEAR_BASELINE = 398.3
    const val MONTH_SIZE = 30.0
    const val MONTH_BASELINE = 432.3

    const val PERFORATION_RADIUS = 0.265

    val MONTH_NAMES = listOf(
        "January", "February", "March", "April", "May", "June",
        "July", "August", "September", "October", "November", "December",
    )
    val WEEKDAYS = listOf("S", "M", "T", "W", "T", "F", "S")
}

/** A stamp in the collage: top-left corner, size, and dy, how much lower its right edge sits. */
internal data class StampDiaryCalendarQuad(val x: Double, val y: Double, val w: Double, val h: Double, val dy: Double)

internal data class StampDiaryCalendarPileStamp(
    val art: Int,
    val quad: StampDiaryCalendarQuad,
    val blurTop: Double,
    val blurBottom: Double,
    val soft: Double,
)

internal data class StampDiaryCalendarMemory(val day: Int, val art: Int, val w: Double, val h: Double, val twin: Int)

internal object StampDiaryCalendarData {
    val pile = listOf(
        StampDiaryCalendarPileStamp(5, StampDiaryCalendarQuad(288.0, -34.0, 120.0, 150.0, 10.0), 0.0, 0.0, 1.0),
        StampDiaryCalendarPileStamp(0, StampDiaryCalendarQuad(233.0, 48.0, 122.0, 152.0, 11.0), 0.0, 0.0, 1.0),
        StampDiaryCalendarPileStamp(1, StampDiaryCalendarQuad(181.0, 100.0, 119.0, 150.0, 12.0), 0.0, 0.0, 1.0),
        StampDiaryCalendarPileStamp(2, StampDiaryCalendarQuad(129.0, 150.0, 121.0, 160.0, 12.0), 0.0, 0.0, 1.0),
        StampDiaryCalendarPileStamp(4, StampDiaryCalendarQuad(78.0, 229.0, 119.0, 130.0, 9.0), 0.0, 0.0, 1.0),
        StampDiaryCalendarPileStamp(3, StampDiaryCalendarQuad(24.0, 291.0, 122.0, 168.0, 10.0), 0.0, 2.6, 0.55),
        StampDiaryCalendarPileStamp(6, StampDiaryCalendarQuad(-30.0, 355.0, 122.0, 150.0, 10.0), 1.6, 5.0, 0.25),
    )

    val memories = listOf(
        StampDiaryCalendarMemory(5, 0, 44.0, 44.0, 1),
        StampDiaryCalendarMemory(9, 1, 35.0, 44.0, 2),
        StampDiaryCalendarMemory(10, 2, 35.0, 44.0, 3),
        StampDiaryCalendarMemory(12, 4, 44.0, 44.0, 4),
        StampDiaryCalendarMemory(19, 6, 44.0, 44.0, -1),
        StampDiaryCalendarMemory(23, 7, 35.0, 44.0, -1),
        StampDiaryCalendarMemory(30, 8, 35.0, 44.0, -1),
    )

    val freeSpot = StampDiaryCalendarQuad(236.0, 214.0, 120.0, 150.0, 10.0)
}

/** The postmark texture (an alpha field, drawn once per day). */
internal object StampDiaryCalendarPostmarkSpec {
    const val WIDTH = 512
    const val HEIGHT = 256
    const val RING_X = 384.0
    const val RING_Y = 128.0
    const val OUTER = 112.0
    const val INNER = 78.0
    const val OUTER_STROKE = 9.0
    const val INNER_STROKE = 6.0
    const val DAY_FONT = 62.0
    const val ARC_FONT = 25.0
    const val WAVES = 4
    const val WAVE_FROM = 18.0
    const val WAVE_TO = 262.0
    const val WAVE_GAP = 27.0
    const val WAVE_AMP = 7.0
    const val WAVE_LENGTH = 66.0
    const val WAVE_STROKE = 7.0
    const val SOFTNESS = 2.2
}

/** Where the postmark sits on a stamp. */
internal object StampDiaryCalendarInkMark {
    const val X = 0.17
    const val Y = -0.11
    const val RADIUS = 0.27
    const val ROTATION = -0.17
}

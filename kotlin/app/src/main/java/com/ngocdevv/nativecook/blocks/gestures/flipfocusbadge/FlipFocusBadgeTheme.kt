package com.ngocdevv.nativecook.blocks.gestures.flipfocusbadge

import android.graphics.Color
import kotlin.math.max

/**
 * Every colour, size and copy token of Flip Focus Badge, measured on the pin (a phone mock-up rectified onto the 402 x 874 pt canvas). Port of RN
 * `ffb-theme.ts`. Only this file holds literal colours and sizes.
 */
internal object FlipFocusBadgeTheme {
    const val REF_W = 402.0
    const val REF_H = 874.0
    const val SAFE_TOP = 62.0

    val pageTop = Color.parseColor("#030303")
    val pageMid = Color.parseColor("#080808")
    val pageRight = Color.parseColor("#0D0D0D")
    val sheet = Color.parseColor("#1F1F1F")
    val chip = Color.parseColor("#181818")
    val disc = Color.parseColor("#1B1B1B")
    val seg = Color.parseColor("#2D2D2D")
    val segOn = Color.parseColor("#404040")
    val segOff = Color.parseColor("#303030")
    val ink = Color.parseColor("#FFFFFF")
    val inkDate = Color.parseColor("#ACACAC")
    val inkMute = Color.parseColor("#A4A4A4")
    val inkHint = Color.parseColor("#646464")
    val inkLabel = Color.parseColor("#676767")
    val inkSeg = Color.parseColor("#6D6D6D")
    val grabber = Color.parseColor("#606060")
    val track = Color.parseColor("#262626")
    val fill = Color.parseColor("#898989")
    val flame = Color.parseColor("#F5821F")
    val plate = Color.parseColor("#1A1A1C")
    val edge = Color.parseColor("#F1F3FA")
    val numeral = Color.parseColor("#8FA6FF")
    val periwinkle = Color.parseColor("#8FADF3")
    val outline = Color.parseColor("#10143A")

    fun rgbOf(c: Int) = floatArrayOf(Color.red(c) / 255f, Color.green(c) / 255f, Color.blue(c) / 255f)

    /** Inter: the three weights the screen uses (assets/flipfocusbadge). */
    const val FONT_MEDIUM = "flipfocusbadge/flipfocusbadge_Inter-Medium.ttf"
    const val FONT_SEMIBOLD = "flipfocusbadge/flipfocusbadge_Inter-SemiBold.ttf"
    const val FONT_BOLD = "flipfocusbadge/flipfocusbadge_Inter-Bold.ttf"
    const val ATLAS_ASSET = "flipfocusbadge/flipfocusbadge_ffb-glyphs.png"

    /** Type roles, pt on the 402 canvas. `tracking` in em. */
    class Type(val font: String, val size: Double, val lineHeight: Double, val tracking: Double = 0.0)

    val tCount = Type(FONT_SEMIBOLD, 17.0, 22.0)
    val tTotal = Type(FONT_BOLD, 48.0, 52.0, -0.01)
    val tDate = Type(FONT_MEDIUM, 13.3, 18.0)
    val tCaption = Type(FONT_MEDIUM, 12.4, 16.0)
    val tHint = Type(FONT_MEDIUM, 15.4, 20.0)
    val tTick = Type(FONT_MEDIUM, 11.5, 14.0)
    val tSeg = Type(FONT_SEMIBOLD, 15.0, 20.0)

    /** The measured boxes (pt on the 402 canvas). */
    class Box(val x: Double, val y: Double, val w: Double, val h: Double)

    val chipFlame = Box(19.3, 82.3, 64.4, 33.7)
    val chipTrophy = Box(94.3, 82.0, 65.4, 34.7)
    val discHelp = Box(287.7, 77.3, 44.3, 44.7)
    val discGear = Box(339.0, 76.0, 44.0, 45.7)
    const val PROGRESS_X = 109.3
    const val PROGRESS_Y = 468.0
    const val PROGRESS_W = 183.0
    const val PROGRESS_H = 3.7
    const val SHEET_X = 17.0
    const val SHEET_Y = 628.0
    const val SHEET_W = 368.0
    const val SHEET_H = 220.0
    const val SHEET_R = 40.0
    const val GRABBER_X = 183.0
    const val GRABBER_Y = 644.3
    const val GRABBER_W = 36.0
    const val GRABBER_H = 4.5
    const val SEG_X = 39.0
    const val SEG_Y = 780.0
    const val SEG_W = 325.0
    const val SEG_H = 50.0
    const val SEG_R = 25.0
    const val SEG_PAD = 5.0
    const val SEG_GAP = 6.0
    const val SEG_ITEM_H = 38.0
    const val SEG_ITEM_R = 19.0

    // the badge scene (design pt, plate-centred, y up)
    const val CENTRE_X = 206.5
    const val CENTRE_Y = 293.5
    const val RING_ALPHA = -76.4
    const val RING_ROLL = 14.5
    const val RING_CX = -7.53
    const val RING_CY = -19.62
    const val RING_R = 138.1
    const val RING_TAIL_DEG = 239.4
    const val RING_HEAD_DEG = 222.3
    const val RING_SAT_REST_DEG = 31.4
    const val NUM_BASE_Y = -42.8
    const val NUM_PITCH_LEFT = -0.85
    const val NUM_ADVANCE = 0.68
    const val NUM_CAP = 90.5

    // glyph atlas (assets/flipfocusbadge_ffb-glyphs.png): ink boxes in pt of the digits 0..9
    val INK_L = doubleArrayOf(0.62, 0.5, 0.0, 0.12, 0.0, 0.0, 0.0, 0.0, 0.0, 0.12)
    val INK_R = doubleArrayOf(76.25, 66.12, 90.75, 92.38, 93.88, 96.75, 91.12, 84.75, 91.88, 91.62)

    const val LABEL = "Flip Focus Badge"
    const val HINT_IDLE = "flip your phone to start a focus session…"
    const val HINT_FOCUS = "focusing… flip back to finish"
    const val DATE = "Sunday, Mar 8"
    const val CAPTION = "total flip time"
    const val STREAK = 75
    const val TROPHIES = 17
    val SEGMENTS = arrayOf("D", "W", "M", "Y")
    val SEGMENT_LABELS = arrayOf("Day", "Week", "Month", "Year")
    const val HELP_A11Y = "Help"
    const val SETTINGS_A11Y = "Settings"
    const val START_A11Y = "Start focus session"
    const val STOP_A11Y = "Finish focus session"
    fun badgeA11y(hours: Int) = "$hours hour focus badge. Flip your phone, or drag the badge, to start a focus session."
    fun badgeA11yRunning(clock: String) = "Focus session running${if (clock.isEmpty()) "" else ", $clock"}. Flip back to finish."

    class Range(val date: String, val total: String)

    val RANGES = arrayOf(Range(DATE, ""), Range("Mar 2 – Mar 8", "47h 05m"), Range("March", "182h 40m"), Range("2026", "1,206h"))

    /** the layout for a screen of `w` x `h` dp, with its insets */
    class Layout(val w: Double, val h: Double, val insetTop: Double, val insetBottom: Double) {
        val s = w / REF_W
        val sheetBottom = h - max(26 * s, insetBottom + 10)
        val sheetTop = sheetBottom - SHEET_H * s
        private val mid: Double

        init {
            // the badge group (plate top 208 ... hint 527) centres between the top row and the sheet
            val free0 = yt(122.0)
            val free1 = sheetTop
            mid = (free0 + free1) / 2 - 6 * s
        }

        fun x(v: Double) = v * s
        fun yt(y: Double) = insetTop + (y - SAFE_TOP) * s
        fun yc(y: Double) = mid + (y - 369.0) * s
        fun ys(y: Double) = sheetTop + (y - SHEET_Y) * s
    }
}

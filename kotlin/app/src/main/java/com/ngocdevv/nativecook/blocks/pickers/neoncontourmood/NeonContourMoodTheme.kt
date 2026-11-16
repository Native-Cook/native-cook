package com.ngocdevv.nativecook.blocks.pickers.neoncontourmood

import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

/** One mood's shape: half-width, half-height, corner radius and the notch pairs of the shared notched rounded-box field (reference pt). */
internal class NeonContourMoodShape(
    val w: Double, val h: Double, val corner: Double, val notchY: Double = 0.0, val notchWidth: Double = 50.0,
    val notchX: Double = 0.0, val notchXWidth: Double? = null,
)

internal class NeonContourMoodMood(
    val key: String, val word: String, val tag: String?, val shape: NeonContourMoodShape,
    /** core, hot ridge, mid band, outer glow (sRGB hex). */
    val ramp: IntArray, val warmth: Double = 0.0,
)

/** Colours, type, layout numbers, moods and copy of the RN block (`ncm-theme.ts`), measured against the pin on a 402 x 874 pt screen. */
internal object NeonContourMoodTheme {
    const val REF_W = 402.0
    const val REF_H = 874.0
    const val SAFE_TOP = 62.0
    const val SAFE_BOTTOM = 34.0

    const val PAGE = 0xFF110713.toInt()
    const val SHEET_TOP = 0xFF10091C.toInt()
    const val CORE = 0xFF1C0104.toInt()
    const val INK = 0xFFFBF9FC.toInt()
    const val SECONDARY = 0xFFB9ADBD.toInt()
    const val OUTLINE = 0xFF8E6F93.toInt()
    const val TRACK = 0xFF211525.toInt()
    const val DOT = 0xFF130B19.toInt()
    const val GRABBER = 0xFF2A1A32.toInt()
    const val BUTTON = 0xFFFCFBFC.toInt()
    const val BUTTON_INK = 0xFF160D19.toInt()
    const val CLOSE = 0xFFD2C7DF.toInt()

    // type: size, line height, tracking
    const val DISPLAY_SIZE = 54.0
    const val DISPLAY_TRACKING = 1.4
    const val DISPLAY_BASELINE = 347.0
    const val TITLE_SIZE = 18.0
    const val TITLE_LINE = 24.0
    const val QUESTION_SIZE = 28.0
    const val QUESTION_LINE = 34.0
    const val TAG_SIZE = 13.0
    const val TAG_TRACKING = 1.25
    const val ENDPOINT_SIZE = 12.0
    const val ENDPOINT_LINE = 17.0
    const val BUTTON_SIZE = 18.0

    const val SHEET_RADIUS = 54.0
    const val CORE_WIDTH = 132.134746
    const val CORE_HEIGHT = 107.983216
    const val CORE_CORNER = 61.268647
    const val NOTCH = 49.46817
    const val NOTCH_WIDTH = 50.063416

    const val TITLE_BASELINE = 111.0
    const val CLOSE_X = 341.0
    const val CLOSE_Y = 82.0
    const val CLOSE_STROKE = 1.5
    const val HERO_Y = 328.0
    const val HERO_CUTOFF = 656.0
    const val HERO_FADE = 140.0
    const val HERO_WORD_GLOW = 2.4
    const val TAG_CENTER_Y = 536.0
    const val TAG_H = 22.0
    const val TAG_PX = 9.0
    const val TAG_STROKE = 1.0
    const val TAG_R = 11.0
    const val QUESTION_BASELINE1 = 584.0
    const val SLIDER_X = 37.0
    const val SLIDER_Y = 650.0
    const val SLIDER_W = 336.0
    const val SLIDER_H = 60.0
    const val SLIDER_R = 30.0
    const val SLIDER_FIRST = 70.0
    const val SLIDER_LAST = 340.0
    const val SLIDER_THUMB = 25.0
    const val SLIDER_DOT = 4.0
    const val ENDPOINT_BASELINE = 733.0
    const val BUTTON_X = 32.0
    const val BUTTON_Y = 768.0
    const val BUTTON_W = 342.0
    const val BUTTON_H = 60.0
    const val BUTTON_R = 30.0
    const val GRABBER_W = 36.0
    const val GRABBER_H = 4.0

    val RING_OFFSETS = doubleArrayOf(0.0, 8.0, 12.864303, 32.0, 49.385498, 86.0, 110.118761)
    val RING_STRENGTH = doubleArrayOf(0.12, 0.06, 1.0, 0.05, 0.75, 0.02, 0.55)
    const val RING_HALO = 9.0
    const val HOLLOW_BASE = 0.5
    const val HOLLOW_WIDTH_GAIN = 0.08
    val VERTICAL_RATIOS = doubleArrayOf(1.0, 1.0, 1.0, 0.45, 0.3, 0.1, 0.07)
    const val HOT_WEIGHT = 3.058259
    const val MID_WEIGHT = 1.197484
    const val OUTER_WEIGHT = 0.223609
    const val OTHER_WEIGHT = 0.714657
    const val WARM_STRENGTH = 2.0
    const val WARM_WIDTH = 67.192482
    const val WARM_Y = 118.436709
    const val WARM_SPREAD = 41.771969
    const val GAIN = 0.3

    const val PRESS_SCALE = 0.97
    const val THUMB_SCALE = 1.12

    const val POINTS_PER_STOP = 56.0
    const val SQUEEZE_MAX = 0.12
    const val LEAN_MAX = 10.0
    const val LEAN_REACH = 64.0
    const val PULSE_STEP = 0.026
    const val HERO_TOP = 14.0
    const val HERO_BOTTOM = 18.0

    const val RIM_HEIGHT = 1.5
    const val RIM_ALPHA = 0.55
    const val RIM_FADE = 0.4
    const val INK_MIX = 0.1
    const val THUMB_POOL = 46.0
    const val THUMB_POOL_ALPHA = 0.22
    const val END_REACH = 1.2
    const val CLOSE_MIX = 0.3
    const val DOT_PASSED = 0.3
    const val DOT_CLEAR = 4.0

    const val TITLE = "Market Feelings"
    const val QUESTION = "What's your market mood?"
    const val LEFT = "EXTREME FEAR"
    const val RIGHT = "EXTREME GREED"
    const val NEXT = "Next"

    private fun hex(vararg v: Long) = IntArray(v.size) { (0xFF000000L or v[it]).toInt() }

    val MOODS: List<NeonContourMoodMood> = listOf(
        NeonContourMoodMood("panic", "PANIC", "CAPITULATION", NeonContourMoodShape(132.0, 116.0, 45.0, 76.0, 39.0),
            hex(0xFFE7DE, 0xFF3344, 0xB0125E, 0x51208D)),
        NeonContourMoodMood("fear", "FEAR", "TENSION", NeonContourMoodShape(CORE_WIDTH, CORE_HEIGHT, CORE_CORNER, NOTCH, NOTCH_WIDTH),
            hex(0xFF2738, 0xFF2B24, 0xF81872, 0x0D0ACC), warmth = 1.0),
        NeonContourMoodMood("doubt", "DOUBT", "CAUTION", NeonContourMoodShape(132.0, 90.0, 80.0, 30.0, 50.0),
            hex(0xFF3484, 0xE33DA9, 0x9F29CB, 0x4822AB)),
        NeonContourMoodMood("calm", "CALM", "BALANCE", NeonContourMoodShape(106.0, 106.0, 106.0, 0.0, 50.0),
            hex(0xC4A8FF, 0x9A7BFF, 0x6945DF, 0x3524B0)),
        NeonContourMoodMood("hope", "HOPE", "APPETITE", NeonContourMoodShape(89.0, 127.0, 89.0, 0.0, 50.0),
            hex(0x8AD8FF, 0x48CFEA, 0x2485D7, 0x252B91)),
        NeonContourMoodMood("greed", "GREED", "MOMENTUM", NeonContourMoodShape(104.0, 118.0, 89.0, -18.0, 70.0),
            hex(0x9AFFD0, 0x3DDBA4, 0x199E9E, 0x214B9F)),
        // A four-point star grown symmetrically, no rotation to turn FEAR into a lump halfway.
        NeonContourMoodMood("mania", "MANIA", "EUPHORIA", NeonContourMoodShape(119.0, 119.0, 48.0, 52.0, 39.0, 24.0, 60.0),
            hex(0xEEFF92, 0xB3F94B, 0x52BF74, 0x197D88)),
    )

    /** Balanced wrap: split the words into at most [lines] runs, the longest run as short as it can be, earlier runs longer on a tie. */
    fun splitLines(text: String, lines: Int): List<String> {
        val words = text.split(Regex("\\s+")).filter { it.isNotEmpty() }
        val count = max(1, min(lines, words.size))
        if (count <= 1) return listOf(words.joinToString(" "))
        var best: List<String> = emptyList()
        var bestMax = Int.MAX_VALUE
        var bestLens: List<Int> = emptyList()
        fun longerFirst(a: List<Int>, b: List<Int>): Boolean {
            for (i in a.indices) if (a[i] != b[i]) return a[i] > b[i]
            return false
        }
        fun walk(start: Int, left: Int, acc: List<String>) {
            if (left == 1) {
                val run = acc + words.subList(start, words.size).joinToString(" ")
                val lens = run.map { it.length }
                val mx = lens.max()
                if (mx < bestMax || (mx == bestMax && longerFirst(lens, bestLens))) {
                    best = run; bestMax = mx; bestLens = lens
                }
                return
            }
            for (end in start + 1..words.size - (left - 1)) walk(end, left - 1, acc + words.subList(start, end).joinToString(" "))
        }
        walk(0, count, emptyList())
        return best
    }

    class Layout(
        val w: Double, val h: Double, val s: Double, val fs: Double, val sheetY: Double, val titleY: Double, val titleLines: List<String>,
        val questionLines: List<String>, val endpointLines: List<List<String>>, val endpointStacked: Boolean, val heroY: Double,
        val heroS: Double, val tagY: Double, val questionY: Double, val questionLine: Double, val sliderY: Double, val sliderX: Double,
        val sliderW: Double, val sliderH: Double, val firstX: Double, val lastX: Double, val endpointY: Double, val buttonY: Double,
        val buttonH: Double, val closeX: Double, val closeY: Double, val heroTouchTop: Double, val heroTouchBottom: Double,
    )

    /** Full-size layout is source-exact; short frames reduce the hero, never squeeze the controls. Large text scrolls. */
    fun makeLayout(W: Double, H: Double, insetTop: Double, insetBottom: Double, fontScale: Double): Layout {
        val S = W / REF_W
        val fs = max(1.0, fontScale)
        val expanded = fs > 1.2
        val extra = if (expanded) (fs - 1) * 380 * S else 0.0
        val stageH = if (expanded) max(H, 735 * S + insetTop + insetBottom + extra) else H
        val bottomShift = stageH - REF_H * S - (insetBottom - SAFE_BOTTOM * S)
        val titleY = insetTop + (TITLE_BASELINE - SAFE_TOP) * S + (if (fs > 1.6) 48 * S else 0.0)
        val buttonH = BUTTON_H * S * max(1.0, fs * 0.7)
        val buttonY = BUTTON_Y * S + bottomShift - (buttonH - BUTTON_H * S)
        val questionLines = splitLines(QUESTION, if (fs > 1.6) 4 else if (fs > 1.2) 3 else 2)
        val titleLines = splitLines(TITLE, if (fs > 1.6) 2 else 1)
        val endpointStacked = fs > 2.4
        val endpointLines = listOf(
            splitLines(LEFT, if (fs > 1.2 && !endpointStacked) 2 else 1),
            splitLines(RIGHT, if (fs > 1.2 && !endpointStacked) 2 else 1),
        )
        val endpointY = buttonY - 35 * S - (if (endpointStacked) 1 else endpointLines[0].size - 1) * ENDPOINT_LINE * S * fs
        val sliderY = if (expanded) endpointY - 32 * S - SLIDER_H * S else SLIDER_Y * S + bottomShift
        val questionY = if (expanded) sliderY - 38 * S - (questionLines.size - 1) * QUESTION_LINE * S * fs else QUESTION_BASELINE1 * S + bottomShift
        val tagY = if (expanded) questionY - QUESTION_SIZE * S * fs - 36 * S else TAG_CENTER_Y * S + bottomShift
        val heroS = max(0.25, min(S, (tagY - titleY + 28 * S) / 431))
        val heroY = tagY - (TAG_CENTER_Y - HERO_Y) * heroS
        return Layout(
            W, stageH, S, fs, insetTop - 6 * S, titleY, titleLines, questionLines, endpointLines, endpointStacked, heroY, heroS, tagY,
            questionY, QUESTION_LINE * S * fs, sliderY, SLIDER_X * S, SLIDER_W * S, SLIDER_H * S, SLIDER_FIRST * S, SLIDER_LAST * S,
            if (expanded) endpointY else ENDPOINT_BASELINE * S + bottomShift, buttonY, buttonH, CLOSE_X * S,
            insetTop + (CLOSE_Y - SAFE_TOP) * S, titleY + HERO_TOP * S, tagY - HERO_BOTTOM * S,
        )
    }
}


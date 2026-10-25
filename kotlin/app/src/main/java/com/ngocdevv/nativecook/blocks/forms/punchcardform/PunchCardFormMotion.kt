package com.ngocdevv.nativecook.blocks.forms.punchcardform

import kotlin.math.floor
import kotlin.math.pow
import kotlin.math.sin

/** Geometry, the Hollerith code, the demo's script and the timing functions (punchCardMotion.ts). */
internal object PunchCardFormMotion {
    /** Loop period in seconds. */
    const val LOOP = 9f

    const val DESIGN_W = 393f
    const val DESIGN_H = 759f

    /** The card: 80 columns of 12 rows (the real card's 7⅜ × 3¼ in). */
    const val COLS = 80
    const val ROWS = 12
    const val PITCH = 8.4f
    const val ROW_PITCH = 22.4f
    const val MARGIN_X = 22f
    const val MARGIN_TOP = 34f
    const val CARD_W = MARGIN_X * 2 + COLS * PITCH
    const val CARD_H = MARGIN_TOP + ROWS * ROW_PITCH + 12f
    const val HOLE_W = 4.6f
    const val HOLE_H = 11f

    /** The canvas (design dp) and, inside it, the card's top, the punch station and the reader's slot. */
    const val CANVAS_Y = 164f
    const val CANVAS_H = 456f
    const val CARD_Y = 22f
    const val STATION_X = 112f
    const val SLOT_Y = CARD_Y + CARD_H + 12f

    class Field(val label: String, val first: Int, val size: Int)

    val FIELDS = listOf(Field("NAME", 1, 20), Field("YEAR", 21, 4), Field("WING", 25, 10))
    val SCRIPT = listOf("ADA", "2026", "WEST")

    fun clamp01(x: Float) = if (x < 0f) 0f else if (x > 1f) 1f else x
    fun seg(t: Float, a: Float, b: Float) = clamp01((t - a) / (b - a))
    fun easeInOutCubic(x: Float) = if (x < 0.5f) 4f * x * x * x else 1f - (-2f * x + 2f).pow(3) / 2f

    /** Ease-out with a small overshoot, for the card advancing a column. */
    fun easeOutBack(x: Float): Float {
        val c = 1.6f
        return 1f + (c + 1f) * (x - 1f).pow(3) + c * (x - 1f).pow(2)
    }

    fun hash(n: Float): Float {
        val s = sin(n * 127.1f + 311.7f) * 43758.5453f
        return s - floor(s)
    }

    /** The rows punched for a character, a bit mask over rows 0–11 top to bottom (12, 11, 0, 1 … 9). */
    fun holeMask(code: Int): Int = when (code) {
        in 48..57 -> 1 shl (code - 48 + 2)
        in 65..73 -> (1 shl 0) or (1 shl (code - 65 + 1 + 2))
        in 74..82 -> (1 shl 1) or (1 shl (code - 74 + 1 + 2))
        in 83..90 -> (1 shl 2) or (1 shl (code - 83 + 2 + 2))
        45 -> 1 shl 1
        else -> 0
    }

    fun colX(col: Float) = MARGIN_X + (col - 0.5f) * PITCH
    fun rowY(row: Int) = MARGIN_TOP + (row + 0.5f) * ROW_PITCH

    /** The card's left edge on the canvas when column [col] (fractional) sits under the punch. */
    fun cardLeft(col: Float) = STATION_X - colX(col)

    // The demo's keystrokes: time, column, character code.
    val KEY_TIMES: FloatArray
    val KEY_COLS: IntArray
    val KEY_CODES: IntArray

    /** Per column (index col - 1): the character the demo punches there and when (or -1). */
    val AUTO_CHAR = IntArray(COLS)
    val AUTO_TIME = FloatArray(COLS) { -1f }

    init {
        val times = listOf(listOf(1.5f, 2.2f, 2.9f), listOf(3.62f, 3.82f, 4.02f, 4.22f), listOf(4.46f, 4.64f, 4.82f, 5.0f))
        val kt = ArrayList<Float>()
        val kc = ArrayList<Int>()
        val kd = ArrayList<Int>()
        SCRIPT.forEachIndexed { f, word ->
            for (i in word.indices) {
                kt.add(times[f][i])
                kc.add(FIELDS[f].first + i)
                kd.add(word[i].code)
            }
        }
        KEY_TIMES = kt.toFloatArray()
        KEY_COLS = kc.toIntArray()
        KEY_CODES = kd.toIntArray()
        for (i in KEY_TIMES.indices) {
            AUTO_CHAR[KEY_COLS[i] - 1] = KEY_CODES[i]
            AUTO_TIME[KEY_COLS[i] - 1] = KEY_TIMES[i]
        }
    }

    private const val STEP = 0.16f

    /** The column under the punch at loop time t (fractional while the card moves). */
    fun autoColumn(t: Float): Float {
        var col = 1f
        for (i in KEY_TIMES.indices) {
            val k = KEY_TIMES[i]
            col += easeOutBack(seg(t, k + 0.07f, k + 0.07f + STEP))
            if (i == 2) col += (FIELDS[1].first - 4) * easeInOutCubic(seg(t, 3.15f, 3.5f))
        }
        return col
    }

    /** How many of the demo's keys have been typed by t. */
    fun autoTyped(t: Float): Int {
        var n = 0
        for (k in KEY_TIMES) if (t >= k) n++
        return if (t >= 6.0f) 0 else n
    }

    /** The typed card slides down into the reader 5.25–6.1 s. */
    fun autoFeed(t: Float) = easeInOutCubic(seg(t, 5.25f, 6.1f))

    /** A fresh card registers from the right, 1 → 0 over 5.7–7.2 s, with a small overshoot. */
    fun autoRegister(t: Float) = if (t < 5.7f) 1f else 1f - easeOutBack(seg(t, 5.7f, 7.2f))

    const val REGISTER_FROM = 430f

    /** The reader is busy from the feed until the new card is out. */
    fun autoBusy(t: Float) = seg(t, 5.25f, 5.4f) * (1f - seg(t, 6.6f, 6.9f))

    /** The button's label: 0 "Submit card", 1 "Reading…", 2 "Welcome, member". */
    fun autoLabel(t: Float) =
        easeInOutCubic(seg(t, 5.2f, 5.45f)) + easeInOutCubic(seg(t, 6.1f, 6.35f)) - 2f * easeInOutCubic(seg(t, 7.3f, 7.6f))

    /** Which field the demo's caret is in (0, 1, 2), or -1. */
    fun autoField(t: Float) = when {
        t < 1.2f || t >= 5.2f -> -1
        t < 3.3f -> 0
        t < 4.35f -> 1
        else -> 2
    }
}

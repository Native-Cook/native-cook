package com.ngocdevv.nativecook.blocks.progress.breathvolumelungs

import kotlin.math.max
import kotlin.math.min

/** Every colour, size and string, measured on the pin (port of bvl-theme.ts). All lengths are dp = RN points. */
internal object BreathVolumeLungsTheme {
    const val REF_W = 402.0
    const val REF_H = 874.0

    /** Lattice columns across the screen. */
    const val COLS = 32
    const val DIGIT_PITCH = 0.4657
    const val J0 = 2
    const val ROWS = 36
    const val J_CLOUD = 7

    fun hex(v: Long): Int = (0xFF000000L or v).toInt()

    val GROUND = hex(0x030305)
    val GRID = hex(0x08080C)
    val DOT = hex(0xF4F4F8)
    val TITLE = hex(0xF7F7FA)
    val CARD_DIM = hex(0x8089DC)
    val RESET = hex(0x191B38)
    val FINISH = hex(0x1B1C37)
    val X_DISC = hex(0xE9E9F0)
    val X_INK = hex(0x1F1F2B)
    val HEAT0 = hex(0x3A2F8F)
    val HEAT1 = hex(0xB5308F)
    val HEAT2 = hex(0xE0382B)
    val LINE = hex(0xD8E270)
    val KNOB_HIGH = hex(0xF4F4F8)

    // dot radii (pt on the reference screen) and type sizes
    const val DOT_CORE = 1.28
    const val FONT_TITLE = 28.5
    const val FONT_LABEL = 13.5
    const val FONT_CARD = 15.0
    const val FONT_CARD_SMALL = 11.5
    const val FONT_CAPTION = 15.0
    const val FONT_PILL = 15.0
    const val DIGIT_R = 2.15

    const val TITLE_TEXT = "Expiratory Volume"
    const val UNIT = "Liter"
    const val CAPTION = "Empty your lungs"
    const val CAPTION_DONE = "Well done"
    const val FINISH_TEXT = "Finish Test"
    const val LEFT_LABEL = "Seconds"
    const val RIGHT_LABEL = "L/s"
    const val A11Y_LABEL = "Expiratory volume"
    const val A11Y_HINT = "Drag the knob, or press and hold, to breathe out. Double tap to play a full exhale."
    const val CAPACITY = 5.5

    // card, relative to the reference screen
    const val CARD_X = 8.0
    const val CARD_Y = 581.0
    const val CARD_W = 386.0
    const val CARD_H = 271.0
    const val CARD_R = 50.0
    const val CARD_INSET = 44.0
    const val LEFT_TOP = 51.0
    const val LABEL_TOP = 72.0
    const val CAPTION_TOP = 75.0
    const val DOT_CX = 204.0
    const val DOT_PITCH = 5.6
    const val DOT_R = 1.35
    val DOT_YS = doubleArrayOf(37.1, 42.8, 47.5, 53.1)
    val DOT_XS = arrayOf(doubleArrayOf(0.0), doubleArrayOf(-0.5, 0.5), doubleArrayOf(-1.5, -0.5, 0.5, 1.5), doubleArrayOf(-1.5, -0.5, 0.5, 1.5))
    const val KNOB_X0 = 88.0
    const val KNOB_X1 = 338.0
    const val KNOB_Y = 716.0
    const val KNOB_R = 17.0
    const val PILL_Y = 785.0
    const val PILL_H = 58.0
    const val REFRESH_CX = 103.0
    const val REFRESH_R = 30.0
    const val CONTROLS_GAP = 4.0
    const val ICON_DROP = 3.0

    const val HERO_DIGIT_CY = 16.86
    const val HERO_LABEL_BASE = 20.8

    class Layout(
        val w: Double, val h: Double, val s: Double, val pitchX: Double, val pitchY: Double, val safeBottom: Double,
        val heroTop: Double, val titleCx: Double, val titleBase: Double, val titleSize: Double,
        val digitCx: Double, val digitCy: Double, val digitDx: Double, val digitDy: Double,
        val labelCx: Double, val labelBase: Double, val labelSize: Double,
        val cardX: Double, val cardY: Double, val cardW: Double, val cardH: Double, val cardR: Double, val k: Double,
        val safeTop: Double,
    )

    fun makeLayout(w: Double, h: Double, safeTop: Double, safeBottom: Double): Layout {
        val s = w / REF_W
        val pitchX = w / COLS
        val titleBase = safeTop + 49.5 * s
        val k = max(0.9, min(1.0, (h - 140 * s) / (REF_H - 140)))
        val cardH = CARD_H * s * k
        val cardBottom = h - 22 * s
        val cardTop = cardBottom - cardH
        val heroTop0 = titleBase + 39.2 * s
        val natural = 30 * pitchX
        val avail = cardTop - 24 * s - heroTop0
        val sy = max(0.45, min(1.0, avail / natural))
        val pitchY = pitchX * sy
        val used = 30 * pitchY
        val spare = max(0.0, avail - used - 29.3 * s)
        val heroTop = heroTop0 + spare * 0.5
        val dp = DIGIT_PITCH
        return Layout(
            w, h, s, pitchX, pitchY, max(0.0, safeBottom), heroTop,
            w / 2, titleBase + 3 * s, FONT_TITLE * s,
            w / 2 + 2 * s, heroTop + HERO_DIGIT_CY * pitchY, pitchX * dp, pitchY * dp,
            w / 2 + 3 * s, heroTop + HERO_LABEL_BASE * pitchY, FONT_LABEL * s,
            CARD_X * s, cardTop, w - 2 * CARD_X * s, cardH, CARD_R * s, k, safeTop,
        )
    }

    fun rgb(c: Int): DoubleArray = doubleArrayOf(((c shr 16) and 255) / 255.0, ((c shr 8) and 255) / 255.0, (c and 255) / 255.0)
}

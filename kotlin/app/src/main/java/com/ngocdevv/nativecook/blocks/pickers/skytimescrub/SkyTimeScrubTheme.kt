package com.ngocdevv.nativecook.blocks.pickers.skytimescrub

import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

/** Colours, type, layout numbers and copy of the RN block (`sts-theme.ts`), measured against the pin on a 402 x 874 pt screen. */
internal object SkyTimeScrubTheme {
    const val REF_W = 402.0
    const val REF_H = 874.0
    const val SAFE_TOP = 62.0

    const val PAGE = 0xFF020203.toInt()
    const val INK = 0xFFF3F5F8.toInt()
    const val MUTED = 0xFFAEB3C1.toInt()
    const val CONTROL = 0xFF151619.toInt()
    const val OUTLINE = 0xFF292B30.toInt()
    const val ACCENT = 0xFFEEFA3C.toInt()
    const val ON_ACCENT = 0xFF15180A.toInt()
    const val TICK = 0xFF6B83D8.toInt()
    const val CHIP = 0xFF223C63.toInt()
    const val LOCATION = 0xFF44C2F5.toInt()
    const val PANEL = 0xFF121222.toInt()
    const val NOON = 0xFF75D9C7.toInt()
    const val SUNSET = 0xFFFFA34E.toInt()
    const val NIGHT = 0xFFEF7897.toInt()
    val ROW_ACCENTS = intArrayOf(NOON, SUNSET, NIGHT)

    // type: size, line height
    const val BODY_SIZE = 13.0
    const val BODY_LINE = 18.0
    const val LABEL_SIZE = 13.0
    const val LABEL_LINE = 18.0
    const val TIME_SIZE = 14.0
    const val TIME_LINE = 20.0
    const val VALUE_SIZE = 26.0

    // component numbers
    const val SKY_RASTER_SCALE = 3.0
    const val ARC_AMPLITUDE = 87.0
    const val RULER_Y = 481.0
    const val SKY_HORIZON = 358.0
    const val HINT_X = 67.0
    const val HINT_H = 28.0
    const val HINT_TIP_GAP = 22.0
    const val HINT_TIP_R = 5.0
    const val HINT_STEM = 2.0
    const val HINT_CLOSE = 16.0
    const val HINT_RADIUS = 8.0
    const val CHIP_Y = 405.0
    const val CHIP_H = 28.0
    const val CHIP_MIN_W = 76.0
    const val CHIP_MAX_W = 220.0
    const val CHIP_PADDING = 12.0
    const val CHIP_TARGET = 48.0
    const val CONTROLS_Y = 765.0
    const val CONTROLS_H = 62.0
    const val CONTROLS_LEFT = 31.0
    const val CONTROLS_CITY_X = 114.0
    const val CONTROLS_CITY_W = 175.0
    const val CONTROLS_RIGHT_X = 309.0
    const val CONTROLS_ICON = 20.0
    const val SCHEDULE_X = 24.0
    const val SCHEDULE_Y = 525.0
    const val SCHEDULE_W = 354.0
    const val SCHEDULE_H = 236.0
    const val SCHEDULE_ROW_H = 78.0
    const val SCHEDULE_PAD = 16.0
    const val PRESSED = 0.97
    const val PRESS_MS = 120
    const val RIM_WIDTH = 1.0
    const val RIM_ALPHA = 0.35
    const val RIM_ARROW = 0.2
    const val RIM_CHIP = 0.25

    const val CITY = "Velora"
    const val HINT = "Long press and slide to time travel"
    const val RULER = "Time of day"
    const val RULER_HINT = "Hold and slide to preview the light. Swipe up or down to adjust by fifteen minutes."
    const val RETURN = "Return to now"
    const val SUNRISE = "Sunrise"
    const val NOON_TITLE = "Solar noon"
    const val SUNSET_TITLE = "Sunset"
    const val NIGHT_TITLE = "Night starts"
    const val NOW = "Now"

    class Layout(
        val width: Double, val height: Double, val s: Double, val controlsY: Double, val controlsH: Double, val largeText: Boolean,
        val rulerY: Double, val hintX: Double, val hintW: Double, val hintH: Double, val horizon: Double, val arcCentre: Double,
        val arcAmplitude: Double, val chipY: Double, val hintY: Double, val tipY: Double, val scheduleY: Double, val scheduleH: Double,
        val rowH: Double, val textScale: Double,
    )

    /** Top content keeps its reference anchor; short screens compress the instrument, never text. */
    fun makeLayout(width: Double, height: Double, insetTop: Double, insetBottom: Double, fontScale: Double): Layout {
        val s = width / REF_W
        val topShift = max(0.0, insetTop - SAFE_TOP * s)
        val available = max(0.0, height - insetBottom - 16 * s)
        val largeText = fontScale > 1.5
        val controlsH = if (largeText) (CONTROLS_ICON + 24 + BODY_LINE * fontScale) * s else CONTROLS_H * s
        val controlsY = min(CONTROLS_Y * s + topShift, available - controlsH)
        val compact = min(1.0, max(0.64, (controlsY - insetTop) / ((CONTROLS_Y - SAFE_TOP) * s)))
        fun instrumentY(y: Double) = insetTop + (y - SAFE_TOP) * s * compact
        val hintX = if (largeText) 16 * s else HINT_X * s
        val hintW = width - hintX * 2
        val hintLines = if (largeText) ceil((238 * s * fontScale) / (hintW - 48 * s)) else 1.0
        val hintH = max(HINT_H * s, (hintLines * LABEL_LINE * fontScale + 8) * s)
        val rulerY = min(instrumentY(RULER_Y), controlsY - (3 * 48 + 44) * s)
        val horizon = min(instrumentY(SKY_HORIZON), rulerY - 123 * s)
        val arcCentre = horizon - 7 * s
        val arcAmplitude = min(ARC_AMPLITUDE * s * compact, rulerY - 43 * s - arcCentre)
        val scheduleY = if (largeText) rulerY + 52 * s
        else max(min(instrumentY(SCHEDULE_Y), controlsY - SCHEDULE_H * s - 8 * s), rulerY + 36 * s)
        val scheduleH = max(48 * s, min(SCHEDULE_H * s, controlsY - scheduleY - 8 * s))
        val rowH = if (largeText) SCHEDULE_ROW_H * s else max(40 * s, min(SCHEDULE_ROW_H * s, scheduleH / 3))
        return Layout(
            width, height, s, controlsY, controlsH, largeText, rulerY, hintX, hintW, hintH, horizon, arcCentre, arcAmplitude,
            chipY = rulerY - (RULER_Y - CHIP_Y) * s,
            hintY = rulerY - (RULER_Y - CHIP_Y - CHIP_H) * s - hintH,
            tipY = rulerY - HINT_TIP_GAP * s,
            scheduleY = scheduleY, scheduleH = scheduleH, rowH = rowH, textScale = max(1.0, fontScale),
        )
    }
}


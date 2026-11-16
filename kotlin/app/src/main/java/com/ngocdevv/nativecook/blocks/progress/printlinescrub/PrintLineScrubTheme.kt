package com.ngocdevv.nativecook.blocks.progress.printlinescrub

import android.graphics.Color
import kotlin.math.min

/**
 * Every colour, size and copy token of the RN block (`pls-theme.ts`), measured on the pin (Dribbble 27100885) and on the concept's
 * rectified numbers on the 402 x 874 pt canvas. Only this file holds literal colours and sizes.
 */
internal object PrintLineScrubTheme {
    const val REF_W = 402.0
    const val REF_H = 874.0
    const val SAFE_TOP = 62.0
    const val SAFE_BOTTOM = 34.0

    private fun rgb(hex: Int): Int = 0xFF000000.toInt() or hex

    val ground = rgb(0xECE9E6)
    val yellow = rgb(0xFFD60A)
    val ink = rgb(0x111111)
    val inkSoft = rgb(0x4B4846)
    val white = rgb(0xFBFAF8)
    val arrow = rgb(0x3F8CFF)
    val axisRed = rgb(0xF04A52)
    val axisGreen = rgb(0x4FD27A)
    val handBlue = rgb(0xCFE0F7)
    val iconInk = rgb(0x17161A)
    fun shadow(alpha: Double) = Color.argb((alpha * 255).toInt(), 58, 36, 32)

    // the model: one figure, 1 world unit = 500 pt of height along the axis (h 0 = base top at y 690, h 1 = head top at y 190)
    const val STAGE_CX = 201.0
    const val STAGE_Y_BASE = 690.0
    const val STAGE_UNIT = 500.0

    // top and bottom chrome
    const val HEADER_X = 16.0
    const val HEADER_Y = 62.0
    const val HEADER_W = 370.0
    const val HEADER_H = 56.0
    const val HEADER_R = 28.0
    const val LOGO_X = 4.0
    const val LOGO_Y = 6.0
    const val LOGO_D = 44.0
    const val FILE_X = 62.0
    const val FILE_SIZE = 12.0
    const val FILE_TRACKING = 0.04
    const val WS_W = 108.0
    const val WS_H = 44.0
    const val WS_R = 22.0
    const val WS_SIZE = 15.0
    const val DOCK_X = 16.0
    const val DOCK_Y = 780.0
    const val DOCK_H = 56.0
    const val DOCK_R = 28.0
    const val DOCK_BTN_D = 44.0
    const val DOCK_BTN_X0 = 6.0
    const val DOCK_BTN_GAP = 8.0

    // the progress capsule, resting on the print line
    const val CAP_W = 340.0
    const val CAP_H = 104.0
    const val CAP_R = 52.0
    const val CAP_LABEL_X = 28.0
    const val CAP_LABEL_Y = 33.0
    const val CAP_LABEL_SIZE = 15.0
    const val BAR_X = 20.0
    const val BAR_Y = 46.0
    const val BAR_W = 300.0
    const val BAR_H = 40.0
    const val PILL_D = 44.0

    // gizmo at the seam
    const val GIZ_REST = 96.0
    const val GIZ_TOUCH = 116.0
    const val GIZ_HEAD = 10.0
    const val GIZ_SHAFT = 2.0
    const val GIZ_STUB = 14.0
    const val GIZ_MAX = 140.0

    // the capsule's glass: a lens, frost and a rim
    const val GLASS_LENS = 1.05
    const val GLASS_REFRACTION = 9.0
    const val GLASS_FROST = 2.5
    const val RANGE_INSET = 12.0

    // the figure
    const val FIGURE_TOP = 180.0
    const val FIGURE_HEIGHT = 600.0

    const val TITLE = "Printing Progress"
    const val LAYER = "Layer"
    const val OF = "/ 480"
    const val FILE = "MSBK_KAIJU_01"
    const val WORKSPACE = "Workspace"
    const val LAYERS = 480
    const val A11Y_LABEL = "Layer preview"

    /** Scales the 402 x 874 canvas to the screen: the stage is centred between the safe areas, header and dock follow them. */
    class Layout(val w: Double, val h: Double, val insetTop: Double, val insetBottom: Double) {
        private val availH = h - insetTop - insetBottom
        val s = min(w / REF_W, availH / (REF_H - SAFE_TOP - SAFE_BOTTOM))
        private val ox = (w - REF_W * s) / 2
        private val oy = (availH - (REF_H - SAFE_TOP - SAFE_BOTTOM) * s) / 2

        /** x on the 402 canvas -> screen x */
        fun x(x: Double) = ox + x * s

        /** y on the canvas -> screen y, content centred between the safe areas (the stage, the capsule) */
        fun y(y: Double) = insetTop + oy + (y - SAFE_TOP) * s

        /** top chrome keeps the safe-area edge */
        fun yt(y: Double) = insetTop + (y - SAFE_TOP) * s

        /** bottom chrome keeps the home-indicator edge */
        fun yb(y: Double) = h - insetBottom - (REF_H - SAFE_BOTTOM - y) * s
    }
}

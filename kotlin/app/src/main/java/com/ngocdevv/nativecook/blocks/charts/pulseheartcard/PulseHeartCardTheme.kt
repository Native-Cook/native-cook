package com.ngocdevv.nativecook.blocks.charts.pulseheartcard

import androidx.compose.animation.core.spring
import androidx.compose.ui.graphics.Color
import com.ngocdevv.nativecook.shared.rgba

/**
 * Colours and layout tokens of Pulse Heart Card (theme.ts). Every position and size is in "ref pt":
 * dp of the pin's 402 wide screen. The view multiplies them by `screenWidth / 402`; Y values are
 * measured from the top of the screen, whose safe area ends at 62.
 */
internal object PulseHeartCardTheme {
    const val REFERENCE_WIDTH = 402f
    const val REFERENCE_SAFE_TOP = 62f

    /** The page scrolls a little: the pin's next row of metrics sits under the tab bar. */
    const val REFERENCE_HEIGHT = 912f

    object Colors {
        val background = Color(0xFFF3F2F8)
        val card = Color(0xFFF8F7FB)
        val border = Color(0xFFD4D3D9)
        val ink = Color(0xFF000000)
        val muted = Color(0xFF88878D)
        val faint = Color(0xFFB9B8C0)
        val heart = Color(0xFFF42E59)
        val heartDark = Color(0xFFE52650)
        val bpmUnit = Color(0xFF55343D)
        val flash = Color(0xFFFFFFFF)
        val chartPink = Color(0xFFF09CAF)
        val activeBlue = Color(0xFF167FE8)
        val metricBlue = Color(0xFF378DDD)
        val metricPink = Color(0xFFDF61D2)
        val metricGreen = Color(0xFF3ACA9B)
        val ringRed = Color(0xFFEE2462)
        val ringYellow = Color(0xFFF1C13A)
        val supportValue = Color(0xFF55545A)
        val tabBar = rgba(248, 248, 252, 0.72f)
        val tabGlassTint = rgba(250, 250, 253, 0.55f)
        val tabBorder = rgba(255, 255, 255, 0.9f)
        val shadow = rgba(0, 0, 0, 0.12f)
    }

    /** Spacing scale of the pin (4 base; values within 1 of the grid are snapped). */
    object Space {
        const val HAIRLINE = 0.8f
        const val CHIP_GAP = 4f
        const val CHIP_PADDING = 10f
        const val CHIP_ICON_GAP = 10f
        const val TAB_PADDING = 4f
        const val SCREEN_INSET = 32f
        const val METRIC_GAP = 26f
    }

    object Layout {
        // Metric chips strip (y 62-120).
        const val STRIP_HEIGHT = 58f
        const val STRIP_RADIUS = 24f
        const val CHIP_FITNESS = 137f
        const val CHIP_HEART = 118f
        const val CHIP_VITALS = 116f
        const val CHIP_ICON = 30f

        // The heart canvas spans the screen width from under the strip to behind the headline.
        const val HEART_CANVAS_TOP = 120f
        const val HEART_CANVAS_BOTTOM = 500f

        // The reading inside the heart. Its centre of scale is the heart's centre (y 290).
        const val NUMBER_TOP = 231f
        const val NUMBER_SIZE = 76f
        const val NUMBER_LINE = 84f
        const val NUMBER_TRACKING = -2.2f
        const val UNIT_TOP = 314f
        const val UNIT_SIZE = 16f
        const val UNIT_LINE = 20f
        const val HEART_CENTRE_Y = 290f
        const val HEADLINE_TOP = 448.5f
        const val HEADLINE_LEFT = 33f
        const val HEADLINE_WIDTH = 338f
        const val HEADLINE_HEIGHT = 72f
        const val HEADLINE_SIZE = 31f
        const val HEADLINE_LINE = 36f
        const val BODY_TOP = 524.8f
        const val BODY_LEFT = 33f
        const val BODY_WIDTH = 338f
        const val BODY_HEIGHT = 82f
        const val BODY_SIZE = 22f
        const val BODY_LINE = 26f

        // Range chart.
        const val CHART_TOP = 631f
        const val CHART_LEFT = 32f
        const val CHART_WIDTH = 338f
        const val CHART_HEIGHT = 100f
        const val CHART_TITLE_SIZE = 13.8f
        const val CHART_TITLE_LINE = 19f
        const val CHART_TITLE_INSET = 1.3f
        const val CHART_PLOT_TOP = 19f
        const val CHART_PLOT_HEIGHT = 64f
        const val CHART_BAR_BLUR = 0.8f
        const val CHART_LABEL_TOP = 83f
        const val CHART_LABEL_SIZE = 11f
        const val CHART_LABEL_LINE = 14f
        const val CHART_ORIGIN = 650f

        // Support metrics.
        const val SUPPORT_TOP = 749f
        const val SUPPORT_WIDTH = 156f
        const val SUPPORT_TITLE_SIZE = 14.5f
        const val SUPPORT_TITLE_LINE = 18f
        const val SUPPORT_VALUE_SIZE = 21.5f
        const val SUPPORT_VALUE_LINE = 26f
        const val SUPPORT_VALUE_GAP = 6.8f
        const val SUPPORT_NOTE_SIZE = 14.5f
        const val SUPPORT_NOTE_LINE = 18f
        const val SUPPORT_NEXT_TOP = 840f

        // Floating tab bar, measured on the pin: 290 x 60, its bottom 22 above the screen's.
        const val TAB_WIDTH = 290f
        const val TAB_HEIGHT = 60f
        const val TAB_RADIUS = 30f
        const val TAB_BOTTOM = 22f
        const val TAB_ITEM = 94f
        const val TAB_ITEM_HEIGHT = 52f
        const val TAB_ICON = 22f
        const val TAB_ICON_CENTRE = 20.5f
        const val TAB_LABEL_SIZE = 11.5f
        const val TAB_LABEL_LINE = 14f
        const val TAB_LABEL_TOP = 35f
    }

    /** Selection lens: glass tinted to the pin's #E2E2E9 pill, a white rim and a sheen over its top half. */
    object Lens {
        val glassTint = rgba(214, 214, 224, 0.55f)
        val fallback = rgba(226, 226, 233, 0.72f)
        val sheen = rgba(255, 255, 255, 0.55f)
        const val SHEEN_TO = 0.5f
        val rim = rgba(255, 255, 255, 0.9f)
        const val RIM_WIDTH = 1f
    }

    /** Tab bar lens: a spring slide that stretches 12 % on the way. */
    object TabMotion {
        /** withSpring({ duration: 400, dampingRatio: 0.8 }): stiffness (2 pi / 0.4 s)^2, damping ratio 0.8. */
        val lens = spring<Float>(dampingRatio = 0.8f, stiffness = 246.74f)
        const val STRETCH = 1.12f
        const val STRETCH_MS = 120
        /** withSpring(1, { duration: 300, dampingRatio: 0.7 }): stiffness (2 pi / 0.3 s)^2. */
        val settle = spring<Float>(dampingRatio = 0.7f, stiffness = 438.65f)
    }
}

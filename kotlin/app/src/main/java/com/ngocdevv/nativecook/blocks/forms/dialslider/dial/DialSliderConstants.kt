package com.ngocdevv.nativecook.blocks.forms.dialslider.dial

import androidx.compose.ui.graphics.Color

/** Geometry and timing shared by the dial slider components (dp / ms). */
public object DialConfig {
    /**
     * Horizontal distance (dp) per value unit.
     * Dense packing so ±100 fits a phone width with tick marks every 5 units.
     */
    public const val TICK_SPACING: Double = 1.5
    public const val TICK_HEIGHT: Double = 14.0
    public const val TICK_WIDTH: Double = 2.0

    /** Height of the tick nearest the center indicator */
    public const val CENTER_TICK_HEIGHT: Double = 28.0

    /** Tick mark interval along the ruler */
    public const val TICK_STEP: Double = 5.0

    /** Major (brighter) ticks every N value units */
    public const val MAJOR_TICK_EVERY: Double = 50.0

    /** Default value range (overridable via preset props) */
    public const val MIN_VALUE: Double = -100.0
    public const val MAX_VALUE: Double = 100.0

    /** Preset button + progress ring geometry */
    public const val ITEM_SIZE: Double = 58.0
    public const val ITEM_GAP: Double = 12.0
    public const val RING_STROKE_WIDTH: Double = 2.0
    public const val RING_BG_STROKE_WIDTH: Double = 1.5

    /** How long the centered value badge stays after drag ends */
    public const val VALUE_BADGE_DELAY_MS: Long = 650

    /** Preset strip fade while the ruler is being dragged */
    public const val PRESET_DIM_OPACITY: Float = 0.06f

    /** Fade-out when drag begins — ~1s until presets are fully dimmed */
    public const val PRESET_DIM_OUT_MS: Int = 1000

    /** Fade-in when drag ends */
    public const val PRESET_DIM_IN_MS: Int = 280

    /** Value badge appear/disappear */
    public const val VALUE_BADGE_IN_MS: Int = 180
    public const val VALUE_BADGE_OUT_MS: Int = 220

    /**
     * Fling coast keeps full release velocity so a fast swipe still travels.
     * Braking is applied only as the coast settles (snap spring at the end).
     * Lower deceleration = longer coast; closer to 1 = slipperier.
     */
    public const val FLING_DECELERATION: Double = 0.995
}

/** Default colors of the dial slider. */
public object DialColors {
    public val Positive: Color = Color(0xFFFFD700)
    public val Negative: Color = Color(0xFFFFFFFF)
    public val MinorTick: Color = Color(0xFF3D3D3D)
    public val MajorTick: Color = Color(0xFFCCCCCC)
    public val RingBackground: Color = Color(0xFF444444)
    public val Background: Color = Color(0xFF000000)
    public val OriginDot: Color = Color(0xFF666666)
    public val Adjusted: Color = Color(0xFF8F8129)
    public val ItemSurface: Color = Color(0xFF1C1C1E)
    public val SelectedRingBackground: Color = Color(0xFF5C5C60)
    public val UnselectedRingBackground: Color = Color(0xFF4A4A4D)
}

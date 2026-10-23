package com.ngocdevv.nativecook.blocks.forms.dialslider.camerazoom

import androidx.compose.ui.graphics.Color

/** Geometry of the camera zoom wheel, in dp unless noted. */
public object CameraZoomDialGeometry {
    /** Measured from the supplied 1180 px-wide iPhone recording. */
    public const val DIAMETER_TO_WIDTH: Double = 1.104

    /** (2556 - 1551.6) / 1180: visible circle from its top to screen bottom. */
    public const val VISIBLE_HEIGHT_TO_WIDTH: Double = 0.852
    public const val DEGREES_PER_OCTAVE: Double = 20.0
    public const val MINOR_TICK_LENGTH: Double = 15.0

    /** Major ticks use color and stroke width—not height—for emphasis. */
    public const val MAJOR_TICK_LENGTH: Double = 15.0
    public const val OUTER_TICK_INSET: Double = 5.0
    public const val LABEL_INSET: Double = 42.0
    public const val FOCAL_LABEL_INSET: Double = 67.0

    /** Measured at roughly 12×27 px in the 3× iPhone capture. */
    public const val POINTER_HALF_WIDTH: Double = 2.0
    public const val POINTER_HEIGHT: Double = 9.0

    /** Subtle rounding measured from the softened yellow marker in the capture. */
    public const val POINTER_CORNER_RADIUS: Double = 0.45

    /** Extra clearance prevents anti-aliased tick edges bleeding through. */
    public const val POINTER_OCCLUSION_PADDING: Double = 1.0

    /** The 90° cutout begins just below the yellow pointer's tip. */
    public const val POINTER_OCCLUSION_APEX_OFFSET: Double = 2.0

    /** A narrow stem keeps the selected stroke hidden below the V-shaped cutout. */
    public const val POINTER_OCCLUSION_TAIL_HALF_WIDTH: Double = 1.5

    /** The reference cutout is an isosceles triangle with a right-angle tip. */
    public const val POINTER_OCCLUSION_APEX_ANGLE: Double = 90.0

    /** Softens all three mask corners without moving its conceptual anchor. */
    public const val POINTER_OCCLUSION_CORNER_RADIUS: Double = 1.25

    /** Compact controls begin about 207 physical px below the circle top at 3x. */
    public const val COMPACT_ROW_TOP: Double = 69.0
    public const val LONG_PRESS_MS: Long = 220
    public const val COLLAPSE_DELAY_MS: Long = 1_200
}

/** Default colors of the camera zoom wheel. */
public object CameraZoomDialColors {
    public val Accent: Color = Color(0xFFFFD60A)
    public val Surface: Color = Color(0x80000000)
    public val Label: Color = Color(0xFFFFFFFF)
    public val SecondaryLabel: Color = Color(0x52FFFFFF)
    public val MinorTick: Color = Color(0x40FFFFFF)
    public val MajorTick: Color = Color(0xD1FFFFFF)
    public val CompactSurface: Color = Color(0xAD1C1C1E)
}

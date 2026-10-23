package com.ngocdevv.nativecook.blocks.lists.donationfancarousel

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.sp

/**
 * A `Text` style of the React Native block: the platform's default sans (the iOS-only family "Avenir
 * Next" does not exist on Android, where React Native falls back to it), a size and a line height
 * that scale with the system font size like React Native's, and the glyphs centred in the line.
 */
internal fun donationFanCarouselTextStyle(
    weight: FontWeight,
    size: Float,
    lineHeight: Float,
    tracking: Float,
    color: Color,
): TextStyle {
    return TextStyle(
        color = color,
        fontSize = size.sp,
        fontFamily = FontFamily.Default,
        fontWeight = weight,
        letterSpacing = tracking.sp,
        lineHeight = lineHeight.sp,
        lineHeightStyle = LineHeightStyle(
            alignment = LineHeightStyle.Alignment.Center,
            trim = LineHeightStyle.Trim.None,
        ),
    )
}

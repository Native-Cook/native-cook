package com.ngocdevv.nativecook.blocks.onboarding.quietsignalonboarding

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * React Native on Android gives a line taller than its font all the extra space above the text
 * (`CustomLineHeightSpan`), so a fixed line height here puts the extra at the top and trims nothing.
 */
private val QuietSignalOnboardingLineHeightStyle = LineHeightStyle(
    alignment = LineHeightStyle.Alignment.Bottom,
    trim = LineHeightStyle.Trim.None,
)

/**
 * A text style written the way the React Native file writes one: [fontSize], [lineHeight] and
 * [letterSpacing] in points (1 pt = 1 dp) at the default text size, the system font.
 *
 * Text grows with the system font scale, but no further than [maxFontScale] times
 * (`maxFontSizeMultiplier`; null lets it grow freely): `sp` already multiplies by the scale, so
 * a cap is the size times cap / scale. With [scalesWithSystem] false (`allowFontScaling={false}`)
 * the size ignores the scale. Letter spacing never scales.
 */
@Composable
internal fun quietSignalOnboardingStyle(
    color: Color,
    fontSize: Float,
    lineHeight: Float? = null,
    fontWeight: FontWeight? = null,
    letterSpacing: Float = 0f,
    maxFontScale: Float? = null,
    scalesWithSystem: Boolean = true,
    textAlign: TextAlign = TextAlign.Center,
    tabularNumbers: Boolean = false,
): TextStyle {
    val density = LocalDensity.current
    val fontScale = density.fontScale
    val capFactor = if (maxFontScale != null && fontScale > maxFontScale) maxFontScale / fontScale else 1f
    val size: TextUnit
    val line: TextUnit
    if (scalesWithSystem) {
        size = (fontSize * capFactor).sp
        line = if (lineHeight == null) TextUnit.Unspecified else (lineHeight * capFactor).sp
    } else {
        size = with(density) { fontSize.dp.toSp() }
        line = if (lineHeight == null) TextUnit.Unspecified else with(density) { lineHeight.dp.toSp() }
    }
    val spacing = if (letterSpacing == 0f) TextUnit.Unspecified else with(density) { letterSpacing.dp.toSp() }
    return TextStyle(
        color = color,
        fontSize = size,
        lineHeight = line,
        fontWeight = fontWeight,
        letterSpacing = spacing,
        textAlign = textAlign,
        fontFeatureSettings = if (tabularNumbers) "tnum" else null,
        lineHeightStyle = QuietSignalOnboardingLineHeightStyle,
    )
}

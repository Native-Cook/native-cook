package com.ngocdevv.nativecook.blocks.onboarding.nocturneprogramcarousel

import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import kotlin.math.min

/**
 * React Native on Android gives a line taller than its font all the extra space above the text
 * (`CustomLineHeightSpan`), so a fixed line height here puts the extra at the top and trims nothing.
 */
internal val NocturneProgramCarouselLineHeightStyle = LineHeightStyle(
    alignment = LineHeightStyle.Alignment.Bottom,
    trim = LineHeightStyle.Trim.None,
)

/**
 * One `<Text>` of the React Native file. [size], [lineHeight] and [letterSpacing] are the style's
 * numbers in dp.
 *
 * - [scales] false is `allowFontScaling={false}`: exactly [size] dp whatever the system text size.
 * - [maxScale] is `maxFontSizeMultiplier`: the text follows the system text size up to that factor
 *   (size and line height alike). Without it the text scales freely.
 * - A text with no [lineHeight] keeps the font padding React Native's `Text` has on Android
 *   (`includeFontPadding`); with one, the line height decides the box.
 */
@Composable
internal fun NocturneProgramCarouselText(
    text: String,
    color: Color,
    size: Float,
    modifier: Modifier = Modifier,
    lineHeight: Float? = null,
    weight: FontWeight = FontWeight.Normal,
    letterSpacing: Float = 0f,
    align: TextAlign = TextAlign.Start,
    maxScale: Float? = null,
    scales: Boolean = true,
    tabular: Boolean = false,
) {
    val density = LocalDensity.current
    val fontScale = density.fontScale
    fun unit(value: Float): TextUnit {
        if (!scales) return with(density) { value.dp.toSp() }
        if (maxScale == null) return value.sp
        return (value * min(fontScale, maxScale) / fontScale).sp
    }
    BasicText(
        text = text,
        modifier = modifier,
        style = TextStyle(
            color = color,
            fontSize = unit(size),
            fontWeight = weight,
            fontFeatureSettings = if (tabular) "tnum" else null,
            letterSpacing = if (letterSpacing == 0f) TextUnit.Unspecified else (letterSpacing / size).em,
            textAlign = align,
            lineHeight = if (lineHeight == null) TextUnit.Unspecified else unit(lineHeight),
            platformStyle = PlatformTextStyle(includeFontPadding = lineHeight == null),
            lineHeightStyle = if (lineHeight == null) null else NocturneProgramCarouselLineHeightStyle,
        ),
    )
}

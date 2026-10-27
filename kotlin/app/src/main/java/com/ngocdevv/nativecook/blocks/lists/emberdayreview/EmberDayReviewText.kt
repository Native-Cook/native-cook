package com.ngocdevv.nativecook.blocks.lists.emberdayreview

import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * How much of the system text scale a React Native `Text` honours: all of it, or up to `maxFontSizeMultiplier`.
 * Returns the factor to apply to a size written in sp (which Compose already scales by the full system scale),
 * so a capped text is divided by the scale above its cap, as the brief asks.
 */
@Composable
internal fun emberDayReviewTextFactor(cap: Float?): Float {
    val fontScale = LocalDensity.current.fontScale
    if (cap != null && fontScale > cap) {
        return cap / fontScale
    }
    return 1f
}

/**
 * One React Native `Text`: [size], [line] and [spacing] are the numbers of the style (points = dp); [cap] is its
 * `maxFontSizeMultiplier`. Text is drawn at exactly [size] at the default text scale, with no shrink-to-fit.
 */
@Composable
internal fun EmberDayReviewText(
    text: String,
    size: Float,
    color: Color,
    modifier: Modifier = Modifier,
    weight: FontWeight = FontWeight.Normal,
    line: Float? = null,
    spacing: Float = 0f,
    cap: Float? = null,
    align: TextAlign = TextAlign.Start,
    maxLines: Int = Int.MAX_VALUE,
    softWrap: Boolean = true,
    tabular: Boolean = false,
    decoration: TextDecoration? = null,
) {
    val factor = emberDayReviewTextFactor(cap)
    val density = LocalDensity.current
    val style = TextStyle(
        color = color,
        fontSize = (size * factor).sp,
        fontWeight = weight,
        lineHeight = if (line != null) (line * factor).sp else TextUnit.Unspecified,
        letterSpacing = with(density) { (spacing * factor).dp.toSp() },
        textAlign = align,
        textDecoration = decoration,
        fontFeatureSettings = if (tabular) "tnum" else null,
        lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None),
    )
    BasicText(
        text = text,
        modifier = modifier,
        style = style,
        maxLines = maxLines,
        softWrap = softWrap,
        overflow = TextOverflow.Visible,
    )
}

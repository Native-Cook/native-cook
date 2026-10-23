package com.ngocdevv.nativecook.blocks.charts.pulseheartcard

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Text at a size in dp that ignores the user's font scale (React Native `allowFontScaling={false}`),
 * with a fixed line height that centres the glyphs in each line box, as React Native does.
 */
@Composable
internal fun PulseHeartCardText(
    text: String,
    size: Float,
    line: Float,
    weight: FontWeight,
    color: Color,
    modifier: Modifier = Modifier,
    tabular: Boolean = false,
    tracking: Float = 0f,
    align: TextAlign = TextAlign.Start,
    maxLines: Int = 1,
    /** When set, the text shrinks (down to this size in dp) to fit its width, like `adjustsFontSizeToFit`. */
    fitDownTo: Float = 0f,
) {
    val fontScale = LocalDensity.current.fontScale
    val style = TextStyle(
        color = color,
        fontSize = (size / fontScale).sp,
        lineHeight = (line / fontScale).sp,
        fontWeight = weight,
        letterSpacing = (tracking / fontScale).sp,
        fontFeatureSettings = if (tabular) "tnum" else null,
        textAlign = align,
        lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None),
        platformStyle = PlatformTextStyle(includeFontPadding = false),
    )
    if (fitDownTo > 0f) {
        BasicText(
            text = text,
            modifier = modifier,
            style = style,
            maxLines = maxLines,
            softWrap = false,
            autoSize = TextAutoSize.StepBased(minFontSize = (fitDownTo / fontScale).sp, maxFontSize = (size / fontScale).sp, stepSize = 0.25.sp),
        )
    } else {
        Text(text = text, modifier = modifier, maxLines = maxLines, softWrap = maxLines > 1, style = style)
    }
}

/** One line of text in a box exactly [line] tall and [width] wide. */
@Composable
internal fun PulseHeartCardLineBox(
    text: String,
    size: Float,
    line: Float,
    weight: FontWeight,
    color: Color,
    width: Dp,
    modifier: Modifier = Modifier,
    tabular: Boolean = false,
    tracking: Float = 0f,
    align: TextAlign = TextAlign.Center,
) {
    Box(modifier = modifier.width(width).height(line.dp), contentAlignment = Alignment.Center) {
        PulseHeartCardText(
            text = text, size = size, line = line, weight = weight, color = color,
            modifier = Modifier.width(width), tabular = tabular, tracking = tracking, align = align,
        )
    }
}

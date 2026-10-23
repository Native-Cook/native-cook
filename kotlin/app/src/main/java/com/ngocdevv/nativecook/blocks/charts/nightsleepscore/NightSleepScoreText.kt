package com.ngocdevv.nativecook.blocks.charts.nightsleepscore

import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorProducer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** A size in design points times the screen scale, as sp that ignores the system font scale (the layout is fixed). */
@Composable
internal fun nightSleepScoreSp(value: Float, scale: Float): TextUnit = with(LocalDensity.current) { (value * scale).dp.toSp() }

internal fun nightSleepScoreStyle(
    size: TextUnit, line: TextUnit, weight: FontWeight = FontWeight.Normal, letterSpacing: TextUnit = 0.sp,
): TextStyle = TextStyle(
    fontSize = size, lineHeight = line, fontWeight = weight, letterSpacing = letterSpacing,
    platformStyle = PlatformTextStyle(includeFontPadding = false),
    lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None),
)

/** Text whose colour is read in the draw phase, so a colour that follows the night front never recomposes. */
@Composable
internal fun NightSleepScoreText(
    text: String, style: TextStyle, modifier: Modifier = Modifier, maxLines: Int = Int.MAX_VALUE, color: () -> Color,
) {
    BasicText(text, modifier, style, maxLines = maxLines, overflow = TextOverflow.Clip, color = ColorProducer { color() })
}

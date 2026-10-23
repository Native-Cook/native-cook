package com.ngocdevv.nativecook.blocks.cards.paddockwidgetbento

import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

@Composable
internal fun PaddockWidgetBentoFittedText(
    text: String,
    color: Color,
    fontSize: TextUnit,
    minimumScaleFactor: Float,
    modifier: Modifier = Modifier,
    fontWeight: FontWeight = FontWeight.Normal,
    fontFamily: FontFamily? = null,
    fontStyle: FontStyle? = null,
    lineHeight: TextUnit = TextUnit.Unspecified,
    maxLines: Int = Int.MAX_VALUE,
    textAlign: TextAlign = TextAlign.Unspecified,
) {
    val minimum = fontSize * minimumScaleFactor
    BasicText(
        text = text,
        modifier = modifier,
        style = TextStyle(
            color = color,
            fontSize = fontSize,
            fontWeight = fontWeight,
            fontFamily = fontFamily,
            fontStyle = fontStyle,
            lineHeight = lineHeight,
            textAlign = textAlign,
            platformStyle = PlatformTextStyle(includeFontPadding = false),
        ),
        maxLines = maxLines,
        overflow = TextOverflow.Clip,
        autoSize = TextAutoSize.StepBased(minFontSize = minimum, maxFontSize = fontSize, stepSize = 0.25.sp),
    )
}

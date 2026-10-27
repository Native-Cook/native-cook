package com.ngocdevv.nativecook.blocks.cards.foliofeaturelibrary

import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * React Native on Android gives a line taller than its font all the extra space above the text
 * (`CustomLineHeightSpan`), so a fixed line height puts the extra at the top and trims nothing.
 */
internal val FolioLineHeightStyle = LineHeightStyle(
    alignment = LineHeightStyle.Alignment.Bottom,
    trim = LineHeightStyle.Trim.None,
)

/** `Platform.OS === 'ios' ? 'Georgia' : 'serif'` and the `Menlo` / `monospace` pair, on Android. */
internal val FolioSerif: FontFamily = FontFamily.Serif
internal val FolioMono: FontFamily = FontFamily.Monospace

/**
 * A `<Text>` of the React Native block. [size] and [lineHeight] are its `fontSize` and `lineHeight` in
 * dp; they follow the system font scale like React Native's, up to [maxScale] (`maxFontSizeMultiplier`),
 * and not at all when [scaling] is false (`allowFontScaling={false}`).
 */
@Composable
internal fun FolioText(
    text: String,
    size: Float,
    color: Color,
    modifier: Modifier = Modifier,
    weight: FontWeight? = null,
    lineHeight: Float? = null,
    letterSpacing: Float = 0f,
    align: TextAlign = TextAlign.Unspecified,
    maxLines: Int = Int.MAX_VALUE,
    family: FontFamily? = null,
    italic: Boolean = false,
    maxScale: Float? = null,
    scaling: Boolean = true,
) {
    BasicText(
        text = text,
        modifier = modifier,
        style = folioTextStyle(size, color, weight, lineHeight, letterSpacing, align, family, italic, maxScale, scaling),
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
    )
}

/** The same text with spans, for the one paragraph that has a bold name inside it. */
@Composable
internal fun FolioRichText(
    text: AnnotatedString,
    size: Float,
    color: Color,
    modifier: Modifier = Modifier,
    lineHeight: Float? = null,
) {
    BasicText(
        text = text,
        modifier = modifier,
        style = folioTextStyle(size, color, null, lineHeight, 0f, TextAlign.Unspecified, null, false, null, true),
    )
}

@Composable
internal fun folioTextStyle(
    size: Float,
    color: Color,
    weight: FontWeight?,
    lineHeight: Float?,
    letterSpacing: Float,
    align: TextAlign,
    family: FontFamily?,
    italic: Boolean,
    maxScale: Float?,
    scaling: Boolean,
): TextStyle {
    val density = LocalDensity.current
    val fontScale = density.fontScale
    fun fit(value: Float): TextUnit = with(density) {
        when {
            !scaling -> value.dp.toSp()
            maxScale != null && fontScale > maxScale -> (value * maxScale).dp.toSp()
            else -> value.sp
        }
    }
    return TextStyle(
        color = color,
        fontSize = fit(size),
        fontWeight = weight,
        fontStyle = if (italic) FontStyle.Italic else null,
        fontFamily = family,
        letterSpacing = if (letterSpacing != 0f) letterSpacing.sp else TextUnit.Unspecified,
        textAlign = align,
        lineHeight = if (lineHeight != null) fit(lineHeight) else TextUnit.Unspecified,
        lineHeightStyle = FolioLineHeightStyle,
    )
}

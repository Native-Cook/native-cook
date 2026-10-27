package com.ngocdevv.nativecook.blocks.onboarding.spectrumnewscarousel

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

/** A React Native `#RRGGBBAA` colour (the form its `boxShadow`s and halos are written in) as a Compose colour. */
internal fun spectrumNewsCarouselHex(rrggbbaa: Long): Color {
    val alpha = (rrggbbaa and 0xFF).toInt()
    val rgb = (rrggbbaa shr 8).toInt()
    return Color((alpha shl 24) or rgb)
}

/**
 * React Native on Android gives a line taller than its font all the extra space above the text
 * (`CustomLineHeightSpan`), so a fixed line height here puts the extra at the top and trims nothing.
 */
internal val SpectrumNewsCarouselLineHeightStyle = LineHeightStyle(
    alignment = LineHeightStyle.Alignment.Bottom,
    trim = LineHeightStyle.Trim.None,
)

/** A text without a line height takes the font's own line, padding included, as React Native on Android does. */
internal val SpectrumNewsCarouselFontPadding = PlatformTextStyle(includeFontPadding = true)

/**
 * Text of the artwork: `allowFontScaling={false}`, so [size] is dp whatever the system font scale
 * (a size in sp divided back by the scale). [letterSpacing] and [lineHeight] are dp too.
 */
@Composable
internal fun SpectrumNewsCarouselArtText(
    text: String,
    size: Float,
    color: Color,
    modifier: Modifier = Modifier,
    weight: FontWeight? = null,
    letterSpacing: Float = 0f,
    lineHeight: Float? = null,
    textAlign: TextAlign? = null,
) {
    val fontSize = with(LocalDensity.current) { size.dp.toSp() }
    val line = with(LocalDensity.current) { lineHeight?.dp?.toSp() } ?: TextUnit.Unspecified
    BasicText(
        text = text,
        modifier = modifier,
        style = TextStyle(
            color = color,
            fontSize = fontSize,
            fontWeight = weight,
            letterSpacing = if (letterSpacing != 0f) (letterSpacing / size).em else TextUnit.Unspecified,
            lineHeight = line,
            textAlign = textAlign ?: TextAlign.Unspecified,
            lineHeightStyle = SpectrumNewsCarouselLineHeightStyle,
            platformStyle = if (lineHeight == null) SpectrumNewsCarouselFontPadding else null,
        ),
    )
}

/**
 * Text that follows the system font scale like React Native's default `Text`: [size] and
 * [lineHeight] are sp, so large text makes them larger. [capScale] is `maxFontSizeMultiplier`.
 */
@Composable
internal fun SpectrumNewsCarouselBodyText(
    text: String,
    size: Float,
    color: Color,
    modifier: Modifier = Modifier,
    weight: FontWeight? = null,
    letterSpacing: Float = 0f,
    lineHeight: Float? = null,
    textAlign: TextAlign? = null,
    capScale: Float? = null,
) {
    val fontScale = LocalDensity.current.fontScale
    val cap = if (capScale != null && fontScale > capScale) capScale / fontScale else 1f
    BasicText(
        text = text,
        modifier = modifier,
        style = TextStyle(
            color = color,
            fontSize = (size * cap).sp,
            fontWeight = weight,
            letterSpacing = if (letterSpacing != 0f) (letterSpacing / size).em else TextUnit.Unspecified,
            lineHeight = if (lineHeight != null) (lineHeight * cap).sp else TextUnit.Unspecified,
            textAlign = textAlign ?: TextAlign.Unspecified,
            lineHeightStyle = SpectrumNewsCarouselLineHeightStyle,
            platformStyle = if (lineHeight == null) SpectrumNewsCarouselFontPadding else null,
        ),
    )
}

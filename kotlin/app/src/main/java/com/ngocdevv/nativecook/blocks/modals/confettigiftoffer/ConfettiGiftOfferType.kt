package com.ngocdevv.nativecook.blocks.modals.confettigiftoffer

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * A size in sp that follows the user's font scale, but never more than [cap] times: React Native's
 * `maxFontSizeMultiplier`. Above the cap the size is divided by the scale beyond it.
 */
internal fun Density.confettiGiftOfferSp(value: Float, cap: Float = Float.MAX_VALUE): TextUnit =
    if (fontScale <= cap) value.sp else (value * cap).dp.toSp()

/** Text of the block, in the numbers of the React Native `styles` (ConfettiGiftOffer.tsx:257). */
internal fun Density.confettiGiftOfferText(
    color: Color,
    size: Float,
    lineHeight: Float? = null,
    weight: Int? = null,
    letterSpacing: Float? = null,
    family: FontFamily? = null,
    cap: Float = Float.MAX_VALUE,
    align: TextAlign = TextAlign.Center,
    tabular: Boolean = false,
    decoration: TextDecoration? = null,
): TextStyle = TextStyle(
    color = color,
    fontSize = confettiGiftOfferSp(size, cap),
    lineHeight = if (lineHeight == null) TextUnit.Unspecified else confettiGiftOfferSp(lineHeight, cap),
    fontWeight = weight?.let { FontWeight(it) },
    fontFamily = family,
    letterSpacing = if (letterSpacing == null) TextUnit.Unspecified else letterSpacing.dp.toSp(),
    textAlign = align,
    textDecoration = decoration,
    fontFeatureSettings = if (tabular) "tnum" else null,
    lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None),
    platformStyle = PlatformTextStyle(includeFontPadding = false),
)

package com.ngocdevv.nativecook.blocks.animations.cipherseam

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * React Native on Android gives a line taller than its font all the extra space above the text
 * (`CustomLineHeightSpan`), so a fixed line height here puts the extra at the top and trims nothing.
 */
internal val CipherSeamLineHeightStyle = LineHeightStyle(
    alignment = LineHeightStyle.Alignment.Bottom,
    trim = LineHeightStyle.Trim.None,
)

/**
 * [size] points of text that scale with the system text size, but never past [maxScale] times
 * (`maxFontSizeMultiplier`): above that scale the size is held at `size * maxScale` dp.
 */
@Composable
@ReadOnlyComposable
internal fun cipherSeamSp(size: Float, maxScale: Float = Float.MAX_VALUE): TextUnit {
    val density = LocalDensity.current
    if (density.fontScale <= maxScale) return size.sp
    return with(density) { (size * maxScale).dp.toSp() }
}

/** [size] points of text that ignore the system text size (`allowFontScaling={false}`, letter spacing). */
@Composable
@ReadOnlyComposable
internal fun cipherSeamFixedSp(size: Float): TextUnit = with(LocalDensity.current) { size.dp.toSp() }

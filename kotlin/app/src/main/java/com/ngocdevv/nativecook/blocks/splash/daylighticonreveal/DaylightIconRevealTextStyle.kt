package com.ngocdevv.nativecook.blocks.splash.daylighticonreveal

import androidx.compose.ui.text.style.LineHeightStyle

/**
 * React Native on Android gives a line taller than its font all the extra space above the text
 * (`CustomLineHeightSpan`), so a fixed line height here puts the extra at the top and trims nothing.
 */
internal val DaylightIconRevealLineHeightStyle = LineHeightStyle(
    alignment = LineHeightStyle.Alignment.Bottom,
    trim = LineHeightStyle.Trim.None,
)

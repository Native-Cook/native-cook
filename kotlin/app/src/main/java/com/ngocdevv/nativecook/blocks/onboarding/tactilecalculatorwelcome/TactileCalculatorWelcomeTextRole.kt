package com.ngocdevv.nativecook.blocks.onboarding.tactilecalculatorwelcome

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

/**
 * React Native on Android gives a line taller than its font all the extra space above the text
 * (`CustomLineHeightSpan`), so a fixed line height here puts the extra at the top and trims nothing.
 */
internal val TactileCalculatorWelcomeLineHeightStyle = LineHeightStyle(
    alignment = LineHeightStyle.Alignment.Bottom,
    trim = LineHeightStyle.Trim.None,
)

/**
 * One text style of the React Native `styles` (`welcome`, `title`, `ctaText`): the look, the size and
 * line height in sp and the cap `maxFontSizeMultiplier` puts on how far the system font scale grows
 * them. Letter spacing is given in em (the point value over the font size), so it grows with the text.
 */
internal class TactileCalculatorWelcomeTextRole(
    private val style: TextStyle,
    private val fontSize: Float,
    private val lineHeight: Float,
    private val maxFontScale: Float,
) {
    /**
     * The style at the system [fontScale]. An sp already multiplies by the scale, so capping it at
     * [maxFontScale] means scaling the sp value by cap over scale once the scale is above the cap.
     */
    fun sized(fontScale: Float): TextStyle {
        val factor = if (fontScale > maxFontScale) maxFontScale / fontScale else 1f
        return style.copy(fontSize = (fontSize * factor).sp, lineHeight = (lineHeight * factor).sp)
    }

    /** The same role with tabular figures (`fontVariant: ['tabular-nums']`), for the arithmetic. */
    fun withTabularFigures(): TactileCalculatorWelcomeTextRole =
        TactileCalculatorWelcomeTextRole(style.copy(fontFeatureSettings = "tnum"), fontSize, lineHeight, maxFontScale)

    companion object {
        /** `styles.welcome`: Inter Regular 36 on 44, #2A2725, letter spacing -0.7; capped at 1.6. */
        fun welcome(fonts: TactileCalculatorWelcomeFonts) = TactileCalculatorWelcomeTextRole(
            style = TextStyle(
                color = Color(0xFF2A2725),
                fontFamily = fonts.regular,
                letterSpacing = (-0.7f / 36f).em,
                textAlign = TextAlign.Center,
                lineHeightStyle = TactileCalculatorWelcomeLineHeightStyle,
            ),
            fontSize = 36f,
            lineHeight = 44f,
            maxFontScale = 1.6f,
        )

        /** `styles.title`: Inter Bold 36 on 44, #D9402D, letter spacing -0.8; capped at 1.5. */
        fun title(fonts: TactileCalculatorWelcomeFonts) = TactileCalculatorWelcomeTextRole(
            style = TextStyle(
                color = Color(0xFFD9402D),
                fontFamily = fonts.bold,
                letterSpacing = (-0.8f / 36f).em,
                textAlign = TextAlign.Center,
                lineHeightStyle = TactileCalculatorWelcomeLineHeightStyle,
            ),
            fontSize = 36f,
            lineHeight = 44f,
            maxFontScale = 1.5f,
        )

        /** `styles.ctaText`: Inter Bold 21 on 26, white, letter spacing -0.3; capped at 1.6. */
        fun cta(fonts: TactileCalculatorWelcomeFonts) = TactileCalculatorWelcomeTextRole(
            style = TextStyle(
                color = Color.White,
                fontFamily = fonts.bold,
                letterSpacing = (-0.3f / 21f).em,
                textAlign = TextAlign.Center,
                lineHeightStyle = TactileCalculatorWelcomeLineHeightStyle,
            ),
            fontSize = 21f,
            lineHeight = 26f,
            maxFontScale = 1.6f,
        )
    }
}

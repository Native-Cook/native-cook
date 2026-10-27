package com.ngocdevv.nativecook.blocks.progress.brightlessonrecap

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import kotlin.math.min

/** The faces the screen uses: the two bundled Nunito weights and the system font. */
internal enum class BrightLessonRecapFace { Black, ExtraBold, System }

/**
 * Builds the text styles of the screen from the React Native style values (size, line height and
 * letter spacing in points, which Compose reads as sp so they scale with the text size like React
 * Native's). A [cap] is a `maxFontSizeMultiplier`: above it the text stops growing.
 */
internal class BrightLessonRecapTypography(context: Context, private val fontScale: Float) {
    private val black = FontFamily(Font("brightlessonrecap/brightlessonrecap_Nunito-Black.ttf", context.assets, FontWeight.Black))
    private val extraBold = FontFamily(Font("brightlessonrecap/brightlessonrecap_Nunito-ExtraBold.ttf", context.assets, FontWeight.ExtraBold))

    fun style(
        face: BrightLessonRecapFace,
        size: Float,
        lineHeight: Float?,
        color: Color,
        letterSpacing: Float = 0f,
        align: TextAlign = TextAlign.Unspecified,
        cap: Float? = null,
        tabular: Boolean = false,
    ): TextStyle {
        // Compose scales sp by the system font scale; dividing by it above the cap stops the growth there.
        val k = if (cap == null) 1f else min(fontScale, cap) / fontScale
        return TextStyle(
            color = color,
            fontSize = (size * k).sp,
            lineHeight = if (lineHeight == null) TextUnit.Unspecified else (lineHeight * k).sp,
            letterSpacing = (letterSpacing * k).sp,
            fontFamily = when (face) {
                BrightLessonRecapFace.Black -> black
                BrightLessonRecapFace.ExtraBold -> extraBold
                BrightLessonRecapFace.System -> FontFamily.Default
            },
            fontWeight = when (face) {
                BrightLessonRecapFace.Black -> FontWeight.Black
                BrightLessonRecapFace.ExtraBold -> FontWeight.ExtraBold
                BrightLessonRecapFace.System -> FontWeight.Normal
            },
            textAlign = align,
            fontFeatureSettings = if (tabular) "tnum" else null,
            lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None),
        )
    }
}

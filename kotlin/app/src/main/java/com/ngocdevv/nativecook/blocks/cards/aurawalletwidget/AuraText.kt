package com.ngocdevv.nativecook.blocks.cards.aurawalletwidget

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp

// Fixed-size system type inside the drawing; only the external hint scales.
internal fun auraTextStyle(size: Double, line: Double, unit: Double,
    weight: FontWeight = FontWeight.Normal, color: Color = Color.White,
    tracking: Double = AuraPalette.tight, align: TextAlign = TextAlign.Start) = TextStyle(
    fontFamily = FontFamily.SansSerif,
    fontSize = (size * unit).sp,
    lineHeight = (line * unit).sp,
    fontWeight = weight,
    color = color,
    letterSpacing = (tracking * unit).sp,
    textAlign = align,
    platformStyle = PlatformTextStyle(includeFontPadding = false),
    lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None),
)

@Composable
internal fun AuraLine(text: String, style: TextStyle, modifier: Modifier = Modifier,
    alignment: Alignment = Alignment.CenterStart) {
    Box(modifier, contentAlignment = alignment) {
        BasicText(text, style = style, maxLines = 1)
    }
}

// Each character owns its native content transition. A changed amount creates
// a new target only once per amount step, never on a frame of the glass clock.
@Composable
internal fun AuraNumber(value: String, style: TextStyle, reduced: Boolean, modifier: Modifier = Modifier) {
    Row(modifier.clearAndSetSemantics {}, verticalAlignment = Alignment.CenterVertically) {
        value.forEachIndexed { index, character ->
            key(value.length - index) {
                AnimatedContent(
                    targetState = character,
                    transitionSpec = {
                        if (reduced) {
                            fadeIn(tween(0)) togetherWith fadeOut(tween(0))
                        } else {
                            val direction = if (targetState > initialState) 1 else -1
                            (slideInVertically(tween(300, easing = CubicBezierEasing(0.23f, 1f, 0.32f, 1f))) { it * direction } +
                                fadeIn(tween(180))) togetherWith
                                (slideOutVertically(tween(300, easing = CubicBezierEasing(0.23f, 1f, 0.32f, 1f))) { -it * direction } +
                                    fadeOut(tween(180)))
                        }
                    },
                    label = "Aura digit",
                ) { glyph ->
                    BasicText(glyph.toString(), style = style.copy(fontFeatureSettings = "tnum"), maxLines = 1)
                }
            }
        }
    }
}

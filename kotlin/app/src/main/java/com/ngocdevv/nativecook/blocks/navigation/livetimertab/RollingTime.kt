package com.ngocdevv.nativecook.blocks.navigation.livetimertab

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

// A digit's cell and a colon's, as fractions of the font size: fixed, so the time never changes width.
private const val DIGIT = 0.62f
private const val COLON = 0.3f
private const val LINE = 1.3f

internal val RollMs = (ROLL * 1000).toInt()

/**
 * A time whose characters sit in cells of fixed width; a character that changes rolls: the old one
 * falls and fades while the new one drops in.
 *
 * @param text The time as text, "14:59".
 * @param still No roll: a character is simply replaced.
 */
@Composable
internal fun RollingTime(text: String, size: Float, color: Color, weight: FontWeight, still: Boolean, modifier: Modifier = Modifier) {
    val shift = with(LocalDensity.current) { ROLL_SHIFT.dp.roundToPx() }
    val height = (size * LINE).roundToInt()
    val style = TextStyle(
        color = color,
        fontSize = size.sp,
        lineHeight = height.sp,
        fontWeight = weight,
        fontFeatureSettings = "tnum",
        lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None),
        platformStyle = PlatformTextStyle(includeFontPadding = false),
    )
    Row(modifier.clearAndSetSemantics { contentDescription = text }) {
        text.forEach { character ->
            Box(Modifier.size((size * if (character == ':') COLON else DIGIT).dp, height.dp), contentAlignment = Alignment.Center) {
                AnimatedContent(
                    targetState = character,
                    transitionSpec = {
                        if (still) {
                            EnterTransition.None togetherWith ExitTransition.None
                        } else {
                            (slideInVertically(tween<IntOffset>(RollMs, easing = EaseOut)) { -shift } + fadeIn(tween(RollMs, easing = EaseOut)))
                                .togetherWith(slideOutVertically(tween<IntOffset>(RollMs, easing = EaseOut)) { shift } + fadeOut(tween(RollMs, easing = EaseOut)))
                                .using(SizeTransform(clip = false))
                        }
                    },
                    contentAlignment = Alignment.Center,
                    label = "character",
                ) { shown ->
                    BasicText(shown.toString(), style = style, maxLines = 1, softWrap = false)
                }
            }
        }
    }
}

package com.ngocdevv.nativecook.blocks.lists.emberdayreview

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ngocdevv.nativecook.shared.EaseOutStrong

/**
 * `SwapText` from the shared React Native helpers: text that changes in place. The old words leave fast (140 ms)
 * and the new ones settle a beat later from 10 dp below (260 ms after a 60 ms delay), both on the strong ease-out;
 * under Reduce Motion it is a plain cross-fade (180 ms in, 120 ms out). The first composition does not animate.
 * The box takes the new text's size at once, and both copies hang from its top-left corner.
 */
@Composable
internal fun EmberDayReviewSwapText(
    text: String,
    size: Float,
    color: Color,
    weight: FontWeight,
    cap: Float?,
    reduced: Boolean,
    modifier: Modifier = Modifier,
) {
    val risePx = with(LocalDensity.current) { 10.dp.roundToPx() }
    AnimatedContent(
        targetState = text,
        modifier = modifier,
        transitionSpec = {
            val enter = if (reduced) {
                fadeIn(tween(180, easing = EmberQuadInOut))
            } else {
                fadeIn(tween(260, delayMillis = 60, easing = EaseOutStrong)) +
                    slideInVertically(tween(260, delayMillis = 60, easing = EaseOutStrong)) { risePx }
            }
            val exit = if (reduced) {
                fadeOut(tween(120, easing = EmberQuadInOut))
            } else {
                fadeOut(tween(140, easing = EaseOutStrong))
            }
            (enter togetherWith exit).using(SizeTransform(clip = false) { _, _ -> snap() })
        },
        contentAlignment = Alignment.TopStart,
        label = "swap-text",
    ) { value ->
        EmberDayReviewText(text = value, size = size, color = color, weight = weight, cap = cap)
    }
}

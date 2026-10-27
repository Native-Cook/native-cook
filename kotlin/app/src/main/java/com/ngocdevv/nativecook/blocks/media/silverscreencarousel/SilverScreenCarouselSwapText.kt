package com.ngocdevv.nativecook.blocks.media.silverscreencarousel

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.TextStyle
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/**
 * `SwapText` (shared/SwapText.tsx): text that changes in place. The old words leave fast (140 ms) and
 * the new ones settle a beat later from 10 dp below (260 ms, after 60 ms), so a label reads as one
 * sentence being rewritten rather than a cut. Under Reduce Motion ([reduced]) it is a plain
 * cross-fade (the old words out in 120 ms, the new ones in 180 ms, on `Easing.inOut(Easing.quad)`).
 * The first composition does not animate.
 *
 * An invisible copy of the current text sizes the box and the animated words sit on top of it, so the
 * layout changes at once and only the words move. [styleFor] gives the style of a text, so the words
 * that are leaving keep the colour they had. Accessibility reads the owner's description, not this text.
 */
@Composable
internal fun SilverScreenCarouselSwapText(
    text: String,
    styleFor: (String) -> TextStyle,
    reduced: Boolean,
    modifier: Modifier = Modifier,
) {
    var shown by remember { mutableStateOf(text) }
    var leaving by remember { mutableStateOf<String?>(null) }
    // Counts the swaps. The animation values belong to swap `settledSwap`; until the animation has
    // started for the newest swap the new words stay hidden and the old ones stay put, so no frame
    // shows a half-swapped state.
    var swaps by remember { mutableIntStateOf(0) }
    var settledSwap by remember { mutableIntStateOf(0) }
    val exit = remember { Animatable(0f) }
    val enter = remember { Animatable(1f) }
    if (text != shown) {
        leaving = shown
        shown = text
        swaps += 1
    }

    LaunchedEffect(swaps) {
        if (swaps == 0) return@LaunchedEffect
        coroutineScope {
            launch {
                exit.snapTo(1f)
                settledSwap = swaps
                exit.animateTo(
                    0f,
                    tween(if (reduced) 120 else 140, easing = if (reduced) EaseInOutQuad else SilverScreenCarouselSettle),
                )
                leaving = null
            }
            launch {
                enter.snapTo(0f)
                settledSwap = swaps
                enter.animateTo(
                    1f,
                    tween(
                        durationMillis = if (reduced) 180 else 260,
                        delayMillis = if (reduced) 0 else 60,
                        easing = if (reduced) EaseInOutQuad else SilverScreenCarouselSettle,
                    ),
                )
            }
        }
    }

    Box(modifier) {
        BasicText(
            text = text,
            style = styleFor(text),
            modifier = Modifier
                .graphicsLayer { alpha = 0f }
                .clearAndSetSemantics { },
        )
        leaving?.let { words ->
            BasicText(
                text = words,
                style = styleFor(words),
                softWrap = false,
                modifier = Modifier
                    .matchParentSize()
                    .graphicsLayer { alpha = if (settledSwap == swaps) exit.value else 1f }
                    .wrapContentSize(Alignment.Center, unbounded = true)
                    .clearAndSetSemantics { },
            )
        }
        BasicText(
            text = shown,
            style = styleFor(shown),
            modifier = Modifier
                .matchParentSize()
                .graphicsLayer {
                    val arrived = if (settledSwap == swaps) enter.value else 0f
                    alpha = arrived
                    translationY = if (reduced) 0f else (1f - arrived) * 10f * this.density
                }
                .clearAndSetSemantics { },
        )
    }
}

/** Reanimated's default easing for entering and exiting layouts, `Easing.inOut(Easing.quad)`. */
private val EaseInOutQuad: Easing = Easing { x -> if (x < 0.5f) 2f * x * x else -1f + (4f - 2f * x) * x }

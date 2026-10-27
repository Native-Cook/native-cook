package com.ngocdevv.nativecook.blocks.progress.brightlessonrecap

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

private val SwapSettle = CubicBezierEasing(0.23f, 1f, 0.32f, 1f)

/** Reanimated's default timing curve, `Easing.inOut(Easing.quad)`, used by the reduced-motion fades. */
private val SwapInOutQuad = Easing { u -> if (u < 0.5f) 2f * u * u else 1f - (-2f * u + 2f) * (-2f * u + 2f) / 2f }

/**
 * `SwapText` (react-native/src/shared/SwapText.tsx): text that changes in place. The old words
 * leave in 140 ms and the new ones settle a beat later (after 60 ms) from 10 dp below in 260 ms,
 * both on the strong ease-out; under reduced motion it is a cross-fade (120 ms out, 180 ms in).
 * The first composition does not animate. An invisible copy of the new text sizes the box, and the
 * two animated copies sit on top of it.
 */
@Composable
internal fun BrightLessonRecapSwapText(
    text: String,
    style: TextStyle,
    reduced: Boolean,
    modifier: Modifier = Modifier,
) {
    var shown by remember { mutableStateOf(text) }
    var leaving by remember { mutableStateOf<String?>(null) }
    val exit = remember { Animatable(1f) }
    val enter = remember { Animatable(1f) }
    LaunchedEffect(text) {
        if (text == shown) return@LaunchedEffect
        leaving = shown
        shown = text
        coroutineScope {
            launch {
                exit.snapTo(0f)
                exit.animateTo(1f, if (reduced) tween(120, easing = SwapInOutQuad) else tween(140, easing = SwapSettle))
            }
            launch {
                enter.snapTo(0f)
                enter.animateTo(
                    1f,
                    if (reduced) tween(180, easing = SwapInOutQuad) else tween(260, delayMillis = 60, easing = SwapSettle),
                )
            }
        }
        leaving = null
    }
    Box(modifier) {
        BasicText(text, style = style, modifier = Modifier.graphicsLayer { alpha = 0f })
        leaving?.let { old ->
            BasicText(
                old,
                style = style,
                modifier = Modifier.matchParentSize().graphicsLayer { alpha = 1f - exit.value },
            )
        }
        BasicText(
            shown,
            style = style,
            modifier = Modifier.matchParentSize().graphicsLayer {
                alpha = enter.value
                translationY = if (reduced) 0f else (1f - enter.value) * 10.dp.toPx()
            },
        )
    }
}

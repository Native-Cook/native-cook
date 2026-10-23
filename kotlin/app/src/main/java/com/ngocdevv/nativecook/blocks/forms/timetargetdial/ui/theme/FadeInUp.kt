package com.ngocdevv.nativecook.blocks.forms.timetargetdial.ui.theme

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp

/** Equivalent of Reanimated's `FadeInUp.duration(ms)` entering animation. */
@Composable
fun FadeInUp(
    durationMillis: Int,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val visibleState = remember { MutableTransitionState(false).apply { targetState = true } }
    val density = LocalDensity.current
    val offsetPx = with(density) { 25.dp.roundToPx() }

    AnimatedVisibility(
        visibleState = visibleState,
        modifier = modifier,
        enter = fadeIn(animationSpec = tween(durationMillis)) +
            slideInVertically(animationSpec = tween(durationMillis)) { offsetPx },
    ) {
        content()
    }
}

package com.ngocdevv.nativecook.blocks.modals.confettigiftoffer

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.launch

/**
 * Reports a scroll view's reading events the way React Native names them: a finger starts or
 * ends a drag, and the fling that may follow begins and ends (`onScrollBeginDrag`,
 * `onScrollEndDrag`, `onMomentumScrollBegin`, `onMomentumScrollEnd`).
 */
@Composable
internal fun ConfettiGiftOfferScrollWatcher(
    state: ScrollState,
    enabled: Boolean,
    onBeginDrag: () -> Unit,
    onEndDrag: () -> Unit,
    onMomentumBegin: () -> Unit,
    onMomentumEnd: () -> Unit,
) {
    val beginDrag = rememberUpdatedState(onBeginDrag)
    val endDrag = rememberUpdatedState(onEndDrag)
    val momentumBegin = rememberUpdatedState(onMomentumBegin)
    val momentumEnd = rememberUpdatedState(onMomentumEnd)
    LaunchedEffect(state, enabled) {
        if (!enabled) return@LaunchedEffect
        var momentum = false
        launch {
            state.interactionSource.interactions.collect { interaction ->
                when (interaction) {
                    is DragInteraction.Start -> beginDrag.value()
                    is DragInteraction.Stop, is DragInteraction.Cancel -> {
                        endDrag.value()
                        if (state.isScrollInProgress) {
                            momentum = true
                            momentumBegin.value()
                        }
                    }
                }
            }
        }
        snapshotFlow { state.isScrollInProgress }.collect { scrolling ->
            if (!scrolling && momentum) {
                momentum = false
                momentumEnd.value()
            }
        }
    }
}

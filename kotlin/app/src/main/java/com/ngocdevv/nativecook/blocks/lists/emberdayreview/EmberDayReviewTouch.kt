package com.ngocdevv.nativecook.blocks.lists.emberdayreview

import androidx.compose.foundation.ScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput

/**
 * The `onTouchStart / onTouchEnd / onTouchCancel / onScroll...Drag / onMomentumScroll...` handlers that React
 * Native puts on both ScrollViews: any finger on the panel (a tap, a drag, a fling) pauses the loop, and lifting
 * it starts the 2 s countdown. The returned modifier only watches the pointers (in the initial pass, consuming
 * nothing), so taps and scrolling inside the panel work as usual.
 */
@Composable
internal fun rememberEmberDayReviewTouchTracking(
    scroll: ScrollState,
    enabled: Boolean,
    controller: EmberDayReviewController,
): Modifier {
    val fingerDown = remember { booleanArrayOf(false) }
    LaunchedEffect(scroll, enabled) {
        if (!enabled) {
            return@LaunchedEffect
        }
        var wasMoving = false
        snapshotFlow { scroll.isScrollInProgress }.collect { moving ->
            if (moving && !wasMoving && !fingerDown[0]) {
                controller.touchStart()
            }
            if (!moving && wasMoving && !fingerDown[0]) {
                controller.touchEnd()
            }
            wasMoving = moving
        }
    }
    return Modifier.pointerInput(enabled) {
        if (!enabled) {
            return@pointerInput
        }
        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val anyDown = event.changes.any { it.pressed }
                if (anyDown && !fingerDown[0]) {
                    fingerDown[0] = true
                    controller.touchStart()
                } else if (!anyDown && fingerDown[0]) {
                    fingerDown[0] = false
                    controller.touchEnd()
                    if (scroll.isScrollInProgress) {
                        // A fling carries on after the finger: React Native's onMomentumScrollBegin.
                        controller.touchStart()
                    }
                }
            }
        }
    }
}

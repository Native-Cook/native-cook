package com.ngocdevv.nativecook.blocks.gestures.holddragresize

import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerInputChange

internal suspend fun AwaitPointerEventScope.waitForRelease(
    down: PointerInputChange,
): PointerInputChange? {
    while (true) {
        val event = awaitPointerEvent()
        val change = event.changes.firstOrNull { it.id == down.id } ?: return null
        if (!change.pressed) {
            change.consume()
            return change
        }
        change.consume()
    }
}

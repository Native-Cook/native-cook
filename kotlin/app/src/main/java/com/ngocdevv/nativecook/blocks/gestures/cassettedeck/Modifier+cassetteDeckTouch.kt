package com.ngocdevv.nativecook.blocks.gestures.cassettedeck

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.channels.SendChannel

/** The full-screen zero-slop pan used by the React Native block. */
internal fun Modifier.cassetteDeckTouch(
    session: CassetteDeckSession,
    layout: CassetteDeckLayout,
    wake: SendChannel<Unit>,
): Modifier {
    return pointerInput(session, layout) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            val density = this.density.toDouble()
            session.touchStart(
                layout.stageX(down.position.x / density),
                layout.stageY(down.position.y / density),
            )
            wake.trySend(Unit)
            down.consume()
            try {
                var pressed = true
                while (pressed) {
                    val event = awaitPointerEvent()
                    val change = event.changes.firstOrNull { it.id == down.id }
                    pressed = change?.pressed == true
                    change?.consume()
                }
            } finally {
                session.touchEnd()
                wake.trySend(Unit)
            }
        }
    }
}

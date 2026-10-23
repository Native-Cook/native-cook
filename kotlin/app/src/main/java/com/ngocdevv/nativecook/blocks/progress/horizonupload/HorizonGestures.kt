// One recognizer arbitrates pane dragging and both taps at the source's exact 8pt slop.
package com.ngocdevv.nativecook.blocks.progress.horizonupload

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import kotlin.math.*

internal fun Modifier.horizonGestures(state: HorizonState, g: HorizonGeometry): Modifier = pointerInput(state, g) {
    val velocity = VelocityTracker()
    awaitEachGesture {
        val first = awaitFirstDown(requireUnconsumed = false)
        state.touch()
        val scale = state.scale
        val originX = g.cx + state.shiftX.value
        val originY = g.cy + state.shiftY.value - state.lift * 46
        val localX = (first.position.x / g.density - originX) / scale + g.cardWidth / 2
        val localY = (first.position.y / g.density - originY) / scale + 120
        val card = localX in 0f..g.cardWidth && localY in 0f..240f
        val close = card && abs(localX - (g.cardWidth - 26)) <= 22 && abs(localY - 26) <= 22
        val panel = card && localX in 24f..(g.cardWidth - 24) && localY in 158f..216f
        if (card) state.begin()
        if (close || panel) state.hold(close)
        var moved = false
        var canceled = false
        var dx = 0f; var dy = 0f
        var last = first
        velocity.resetTracking(); velocity.addPosition(first.uptimeMillis, first.position)
        try {
            while (last.pressed) {
                val event = awaitPointerEvent()
                val change = event.changes.firstOrNull { it.id == first.id }
                if (change == null) { canceled = true; break }
                if (event.changes.count { it.pressed } > 1) canceled = true
                last = change; velocity.addPosition(change.uptimeMillis, change.position)
                dx = (change.position.x - first.position.x) / g.density
                dy = (change.position.y - first.position.y) / g.density
                if (hypot(dx, dy) > 8) moved = true
                if (card && moved && !canceled) state.drag(dx, dy)
                if (card) change.consume()
            }
            if (card && moved && !canceled) {
                val speed = velocity.calculateVelocity()
                state.release(dx, dy, speed.x / g.density, speed.y / g.density)
            } else if (card) {
                state.release(dx, dy, 0f, 0f)
                if (!moved && !canceled && last.uptimeMillis - first.uptimeMillis <= 60000) {
                    if (close) state.close() else if (panel) state.toggle()
                }
            }
        } finally {
            state.releasePress()
            if (card && (last.pressed || canceled)) state.release(dx, dy, 0f, 0f)
        }
    }
}

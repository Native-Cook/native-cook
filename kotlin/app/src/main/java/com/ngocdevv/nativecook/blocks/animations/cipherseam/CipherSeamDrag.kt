package com.ngocdevv.nativecook.blocks.animations.cipherseam

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.unit.dp
import kotlin.math.abs

/**
 * The hero's `Gesture.Pan().maxPointers(1).activeOffsetX([-6, 6]).failOffsetY([-12, 12])`: a drag
 * becomes a scrub once the finger has moved 6 dp sideways, unless it moved 12 dp up or down first,
 * in which case the touch is left to the scroll view. While it scrubs, every move is consumed, so
 * the page does not scroll with it. A scroll view that already took the touch cancels it.
 *
 * It does nothing when [enabled] is false (Reduce Motion, or a frozen preview).
 */
internal fun Modifier.cipherSeamDrag(enabled: Boolean, session: CipherSeamSession, heroWidth: Float): Modifier =
    pointerInput(enabled, session, heroWidth) {
        if (!enabled) return@pointerInput
        val activateDistance = 6.dp.toPx()
        val failDistance = 12.dp.toPx()
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            val tracker = VelocityTracker()
            tracker.addPosition(down.uptimeMillis, down.position)
            var active = false
            try {
                while (true) {
                    val event = awaitPointerEvent()
                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                    if (change.isConsumed && !active) break
                    tracker.addPosition(change.uptimeMillis, change.position)
                    if (!change.pressed) {
                        if (active) {
                            val velocity = tracker.calculateVelocity()
                            session.dragEnd(velocity.x / density, heroWidth)
                        }
                        break
                    }
                    val translationX = change.position.x - down.position.x
                    val translationY = change.position.y - down.position.y
                    if (!active) {
                        if (abs(translationY) >= failDistance) break
                        if (abs(translationX) >= activateDistance) {
                            active = true
                            session.dragStart()
                        }
                    }
                    if (active) {
                        session.dragUpdate(translationX / density, heroWidth)
                        change.consume()
                    }
                }
            } finally {
                session.dragFinalize()
            }
        }
    }

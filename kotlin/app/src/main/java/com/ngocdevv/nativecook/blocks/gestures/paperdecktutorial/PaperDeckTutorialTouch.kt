package com.ngocdevv.nativecook.blocks.gestures.paperdecktutorial

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToDownIgnoreConsumed
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.unit.dp
import kotlin.math.abs

/**
 * `Gesture.Pan().maxPointers(1).activeOffsetX([-10, 10]).failOffsetY([-18, 18])` on the card. The finger
 * landing is `onBegin` (the screen holds at once, and `grab` is chosen from where the card was touched);
 * the pan becomes active once the finger has moved 10 dp sideways, unless it moved 18 dp up or down first
 * (then it fails, and a scrolling parent can take over); its translation counts from where it became
 * active; lifting is `onEnd` with the release velocity in dp per second; `onFinalize` always follows.
 */
internal fun Modifier.paperDeckTutorialPan(enabled: Boolean, controller: PaperDeckTutorialController): Modifier {
    if (!enabled) return this
    return pointerInput(controller) {
        val activateDistance = 10.dp.toPx()
        val failDistance = 18.dp.toPx()
        val pixelsPerDp = density
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            controller.panBegin((down.position.y / pixelsPerDp).toDouble())
            val tracker = VelocityTracker()
            tracker.addPosition(down.uptimeMillis, down.position)
            var active = false
            var cancelled = false
            var anchorX = down.position.x
            try {
                while (true) {
                    val event = awaitPointerEvent()
                    val change = event.changes.firstOrNull { it.id == down.id }
                    if (change == null || (!active && change.isConsumed)) {
                        cancelled = true
                        break
                    }
                    tracker.addPosition(change.uptimeMillis, change.position)
                    if (!active) {
                        val dx = change.position.x - down.position.x
                        val dy = change.position.y - down.position.y
                        // The vertical limit is checked first, as the gesture handler does.
                        if (abs(dy) >= failDistance) break
                        if (abs(dx) >= activateDistance) {
                            active = true
                            anchorX = change.position.x
                        }
                    }
                    if (active) {
                        controller.panUpdate(((change.position.x - anchorX) / pixelsPerDp).toDouble())
                        change.consume()
                    }
                    if (!change.pressed) break
                }
                if (active) {
                    val velocity = tracker.calculateVelocity().x / pixelsPerDp
                    controller.panEnd(velocity.toDouble(), success = !cancelled)
                }
            } finally {
                controller.panFinalize()
            }
        }
    }
}

/**
 * The `ScrollView`'s `onTouchStart` and `onTouchEnd`: any finger landing anywhere on the screen
 * is `buttonBegin` and any finger lifting is `buttonEnd`. It looks at the events on their way down (the
 * initial pass) and consumes nothing, so the card, the buttons and a scrolling parent still get them.
 */
internal fun Modifier.paperDeckTutorialTouchHold(enabled: Boolean, controller: PaperDeckTutorialController): Modifier {
    if (!enabled) return this
    return pointerInput(controller) {
        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                for (change in event.changes) {
                    if (change.changedToDownIgnoreConsumed()) controller.buttonBegin()
                    if (change.changedToUpIgnoreConsumed()) controller.buttonEnd()
                }
            }
        }
    }
}

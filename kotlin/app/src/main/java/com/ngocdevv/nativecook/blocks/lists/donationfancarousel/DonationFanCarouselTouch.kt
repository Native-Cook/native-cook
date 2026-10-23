package com.ngocdevv.nativecook.blocks.lists.donationfancarousel

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import kotlin.math.abs

/**
 * The first contact owns movement; every live contact keeps autoplay paused until final release.
 *
 * - The pan has one finger, becomes active once the finger is more than 8 dp away sideways
 *   (`activeOffsetX([-8, 8])`) and fails once it is more than 18 dp away vertically first
 *   (`failOffsetY([-18, 18])`). Its translation is measured from the touch-down point.
 * - A stationary touch also pauses autoplay. Recognizer activation never starts the idle timer.
 *
 * With [enabled] false (frozen or Reduce Motion) nothing is handled, like `enabled(false)`.
 */
internal fun Modifier.donationFanCarouselTouch(
    state: DonationFanCarouselState,
    scale: Float,
    enabled: Boolean,
): Modifier {
    if (!enabled) return this
    return pointerInput(state, scale) {
        val tracker = VelocityTracker()
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            tracker.resetTracking()
            tracker.addPosition(down.uptimeMillis, down.position)
            val startX = down.position.x
            val startY = down.position.y
            state.touchBegan()

            var panActive = false
            var panFailed = false
            var completed = false
            var primaryReleased = false
            try {
                while (true) {
                    val event = awaitPointerEvent()
                    val change = event.changes.firstOrNull { it.id == down.id }
                    if (change != null && !primaryReleased) {
                        tracker.addPosition(change.uptimeMillis, change.position)
                        if (!change.pressed) {
                            if (panActive) {
                                state.release(tracker.calculateVelocity().x.toDouble() / density, scale.toDouble(), nowMs())
                            }
                            primaryReleased = true
                        } else {
                            val translationX = (change.position.x - startX) / density
                            val translationY = (change.position.y - startY) / density
                            if (!panActive && !panFailed) {
                                if (abs(translationY) > FailOffsetY) {
                                    // Keep tracking every live contact after the horizontal pan fails.
                                    panFailed = true
                                } else if (abs(translationX) > ActiveOffsetX) {
                                    panActive = true
                                    state.drag(translationX.toDouble(), scale.toDouble())
                                }
                            } else if (panActive) {
                                state.drag(translationX.toDouble(), scale.toDouble())
                            }
                            if (panActive) change.consume()
                        }
                    }

                    if (event.changes.none { it.pressed }) {
                        completed = true
                        break
                    }
                }
            } finally {
                if (!completed) {
                    // A system or lifecycle cancellation has no trustworthy release velocity.
                    state.cancelTouch(nowMs())
                }
                state.touchFinalized(nowMs())
            }
        }
    }
}

/** `activeOffsetX([-8, 8])`, in dp. */
private const val ActiveOffsetX = 8f

/** `failOffsetY([-18, 18])`, in dp. */
private const val FailOffsetY = 18f

/** The frame clock in ms: Choreographer's frame times are on the `System.nanoTime()` base. */
private fun nowMs(): Double = System.nanoTime() / 1_000_000.0

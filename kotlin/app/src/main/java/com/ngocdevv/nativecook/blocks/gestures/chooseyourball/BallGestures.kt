package com.ngocdevv.nativecook.blocks.gestures.chooseyourball

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.input.pointer.util.addPointerInputChange
import androidx.compose.ui.unit.dp
import kotlin.math.abs

/** A drag claims its axis after this much travel along it, in dp… */
private const val ACTIVE_OFFSET = 10f
/** …and gives the axis up after this much travel across it before that. */
private const val FAIL_OFFSET = 28f

private enum class BallDrag { Undecided, Swipe, Look }

/**
 * What a touch on the screen means: the reference races two pan gestures, one per axis
 * (`activeOffsetX ±10` with `failOffsetY ±28` for the carousel, the mirror image for the free
 * look). The first to travel 10 dp along its own axis wins; one finger only.
 *
 * Put it on the container of the whole screen. Buttons inside still get their taps: movement is
 * consumed only once a drag is recognised, which also cancels a press that began on a button.
 */
internal fun Modifier.ballGestures(
    carousel: BallCarouselState,
    tilt: BallTiltState,
    tiltEnabled: Boolean,
): Modifier = pointerInput(carousel, tilt, tiltEnabled) {
    val activeOffset = ACTIVE_OFFSET.dp.toPx()
    val failOffset = FAIL_OFFSET.dp.toPx()

    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        carousel.panBegin()
        val velocityTracker = VelocityTracker()
        velocityTracker.addPointerInputChange(down)

        var drag = BallDrag.Undecided
        var swipeFailed = false
        var lookFailed = !tiltEnabled
        var origin = down.position
        var lifted = false

        try {
            while (true) {
                val event = awaitPointerEvent()
                val change = event.changes.firstOrNull { it.id == down.id }
                // A second finger ends the gesture, as `maxPointers(1)` does.
                if (change == null || event.changes.count { it.pressed } > 1) break
                velocityTracker.addPointerInputChange(change)
                if (!change.pressed) {
                    lifted = true
                    break
                }

                if (drag == BallDrag.Undecided) {
                    val travel = change.position - down.position
                    if (abs(travel.y) > failOffset) swipeFailed = true
                    if (abs(travel.x) > failOffset) lookFailed = true
                    if (!swipeFailed && abs(travel.x) > activeOffset) {
                        drag = BallDrag.Swipe
                        origin = change.position
                    } else if (!lookFailed && abs(travel.y) > activeOffset) {
                        drag = BallDrag.Look
                        origin = change.position
                    }
                }
                when (drag) {
                    BallDrag.Swipe -> {
                        carousel.panUpdate((change.position.x - origin.x).toDp().value)
                        change.consume()
                    }
                    BallDrag.Look -> {
                        tilt.update((change.position.y - origin.y).toDp().value)
                        change.consume()
                    }
                    BallDrag.Undecided -> Unit
                }
            }
        } finally {
            // On every way out, also when the system takes the touch away mid-drag: a swipe that
            // had begun lands on a slot, and the camera goes home. A touch that never became a
            // swipe leaves the carousel as it is.
            if (drag == BallDrag.Swipe) {
                if (lifted) carousel.panEnd(velocityTracker.calculateVelocity().x.toDp().value) else carousel.panCancel()
            }
            tilt.release()
        }
    }
}

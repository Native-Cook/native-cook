package com.ngocdevv.nativecook.blocks.onboarding.nocturneprogramcarousel

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.input.pointer.util.addPointerInputChange
import kotlin.math.abs
import kotlin.math.roundToInt

/** `activeOffsetX([-10, 10])`: the pan starts after 10 dp of horizontal travel. */
private const val ACTIVE_OFFSET_X = 10f

/** `failOffsetY([-18, 18])`: 18 dp of vertical travel first hands the touch to the scroll view. */
private const val FAIL_OFFSET_Y = 18f

/** A swipe faster than this (dp per second) turns the page whatever the distance. */
private const val FLING_SPEED = 450f

/** A swipe longer than this fraction of a card's stride turns the page. */
private const val TURN_FRACTION = 0.18f

/**
 * `readingHandlers` on the vertical scroll view: any finger on the tour pauses it and, once the last
 * one has left, it starts again two seconds later from where it stopped. Observes the touches before
 * the children see them and consumes nothing. While the tour is hidden ([enabled] false) the touches
 * pass untouched, like `pointerEvents: 'none'`.
 */
@Composable
internal fun Modifier.nocturneProgramCarouselReading(
    playback: NocturneProgramCarouselPlayback,
    enabled: Boolean,
): Modifier {
    val active by rememberUpdatedState(enabled)
    return pointerInput(playback) {
        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                if (!active) {
                    playback.resetReadingTouches()
                    continue
                }
                if (playback.previewing) continue
                val down = event.changes.count { it.pressed }
                val began = event.changes.any { it.pressed && !it.previousPressed }
                val ended = event.changes.any { !it.pressed && it.previousPressed }
                if (began) {
                    playback.readingTouchStart(down)
                } else if (ended) {
                    playback.readingTouchEnd(down)
                }
            }
        }
    }
}

/**
 * `Gesture.Pan().activeOffsetX([-10, 10]).failOffsetY([-18, 18]).maxPointers(1)` of the cards.
 *
 * It follows the finger with resistance past either end (`rubber`) and on release settles on a page:
 * the swipe turns the page when it is longer than 18% of a card's stride or faster than 450 dp/s
 * (the direction is the velocity's when that fast, else the distance's), otherwise the carousel
 * springs back. The spring takes the finger's speed in pages per second. A turned page also calls
 * [onLanded] (settle at the new page's anchor and a light haptic); a swipe that did not turn settles
 * at the page it stays on. Disabled while the tour is hidden or frozen on a preview pose.
 *
 * Values read when a gesture is under way are kept current with `rememberUpdatedState`.
 */
@Composable
internal fun Modifier.nocturneProgramCarouselPan(
    playback: NocturneProgramCarouselPlayback,
    stride: Float,
    reduced: Boolean,
    enabled: Boolean,
    onLanded: (Int) -> Unit,
): Modifier {
    val currentStride by rememberUpdatedState(stride)
    val currentReduced by rememberUpdatedState(reduced)
    val currentEnabled by rememberUpdatedState(enabled)
    val currentLanded by rememberUpdatedState(onLanded)
    return pointerInput(playback) {
        val dp = density
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            val tracker = VelocityTracker()
            tracker.addPointerInputChange(down)
            val start = down.position
            var active = false
            var failed = !(currentEnabled && !playback.previewing)
            var success = false
            var translation = 0f
            try {
                while (true) {
                    val event = awaitPointerEvent()
                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                    // maxPointers(1): a second finger ends the gesture.
                    if (event.changes.count { it.pressed } > 1) break
                    tracker.addPointerInputChange(change)
                    translation = (change.position.x - start.x) / dp
                    val travelY = (change.position.y - start.y) / dp
                    if (!change.pressed) {
                        success = true
                        break
                    }
                    if (failed) continue
                    if (!active) {
                        if (abs(translation) >= ACTIVE_OFFSET_X) {
                            active = true
                            playback.panBegin()
                        } else if (abs(travelY) >= FAIL_OFFSET_Y) {
                            failed = true
                            continue
                        }
                    }
                    if (active) {
                        change.consume()
                        if (!currentReduced) {
                            val next = NocturneProgramCarouselMotion.rubber(
                                (playback.startPage - translation / currentStride).toDouble(),
                                2.0,
                            )
                            playback.snapManual(next.toFloat())
                        }
                    }
                }
            } finally {
                if (active) {
                    finishPan(
                        playback = playback,
                        success = success,
                        translation = translation,
                        velocityX = tracker.calculateVelocity().x / dp,
                        stride = currentStride,
                        reduced = currentReduced,
                        onLanded = currentLanded,
                    )
                }
            }
        }
    }
}

/** The pan's `onFinalize`: pick the page, spring to it and hand the clock back. */
private fun finishPan(
    playback: NocturneProgramCarouselPlayback,
    success: Boolean,
    translation: Float,
    velocityX: Float,
    stride: Float,
    reduced: Boolean,
    onLanded: (Int) -> Unit,
) {
    playback.endGesture()
    val startRounded = playback.startPage.roundToInt()
    val leading = if (abs(velocityX) > FLING_SPEED) velocityX else translation
    val direction = if (leading < 0f) 1 else -1
    val moved = success && (abs(translation) > stride * TURN_FRACTION || abs(velocityX) > FLING_SPEED)
    val target = (startRounded + (if (moved) direction else 0)).coerceIn(0, 2)
    if (reduced) {
        playback.snapManual(target.toFloat())
    } else {
        playback.springManual(target.toFloat(), -velocityX / stride)
    }
    if (moved && target != startRounded) {
        onLanded(target)
    } else {
        playback.settle(NocturneProgramCarouselMotion.Anchors[target])
    }
}

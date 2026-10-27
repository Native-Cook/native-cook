package com.ngocdevv.nativecook.blocks.onboarding.quietsignalonboarding

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.unit.dp
import kotlin.math.abs

/**
 * The horizontal pan over the pages: `Gesture.Pan().activeOffsetX([-10, 10]).failOffsetY([-18, 18])
 * .maxPointers(1)`. A touch becomes a pan once it has moved 10 dp sideways before 18 dp up or down;
 * from then on the pages follow the finger, measured from the point where the pan began, and the
 * pan claims the touch so the page's vertical scroll leaves it alone. A second finger cancels it.
 * Nothing happens while a `previewTime` freezes the screen.
 *
 * The touch is read on the initial pass, before the scroll area under it sees it, so a mostly
 * sideways drag is never taken for a scroll.
 */
internal fun Modifier.quietSignalOnboardingPan(controller: QuietSignalOnboardingController): Modifier =
    pointerInput(controller, controller.playback.previewing) {
        if (controller.playback.previewing) return@pointerInput
        val activeOffset = 10.dp.toPx()
        val failOffset = 18.dp.toPx()
        val pixelsPerDp = density
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            val tracker = VelocityTracker()
            tracker.addPosition(down.uptimeMillis, down.position)

            // Until the pan activates: a sideways move starts it; a vertical move, a lift or a
            // second finger ends the touch without a pan.
            var originX = down.position.x
            var activated = false
            while (!activated) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val change = event.changes.firstOrNull { it.id == down.id } ?: return@awaitEachGesture
                tracker.addPosition(change.uptimeMillis, change.position)
                if (!change.pressed || event.changes.count { it.pressed } > 1) return@awaitEachGesture
                val dx = change.position.x - down.position.x
                val dy = change.position.y - down.position.y
                if (abs(dy) >= failOffset) return@awaitEachGesture
                if (abs(dx) >= activeOffset) {
                    activated = true
                    originX = change.position.x
                    change.consume()
                    controller.panStart()
                }
            }

            // The pan is active: follow the finger until it lifts.
            val width = size.width / pixelsPerDp.toDouble()
            var translation = 0f
            var success = true
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val change = event.changes.firstOrNull { it.id == down.id }
                if (change == null || event.changes.count { it.pressed } > 1) {
                    success = false
                    break
                }
                tracker.addPosition(change.uptimeMillis, change.position)
                translation = change.position.x - originX
                if (!change.pressed) break
                controller.panUpdate(translation / pixelsPerDp.toDouble(), width)
                change.consume()
            }
            val velocityX = tracker.calculateVelocity().x / pixelsPerDp.toDouble()
            controller.panEnd(translation / pixelsPerDp.toDouble(), velocityX, width, success)
        }
    }

/**
 * `readingHandlers` on the ScrollView: a finger anywhere on the page area pauses the loop, and
 * when the last finger is up (and no scroll or pan still holds the screen) the loop resumes from
 * where it stopped after the quiet period. Touches are only observed, never consumed.
 */
internal fun Modifier.quietSignalOnboardingReading(playback: QuietSignalOnboardingPlayback): Modifier =
    pointerInput(playback) {
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            var fingers = 1
            playback.readingTouchStart(fingers)
            try {
                while (fingers > 0) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    val now = event.changes.count { it.pressed }
                    if (now > fingers) playback.readingTouchStart(now)
                    if (now < fingers) playback.readingTouchEnd(now)
                    fingers = now
                }
            } finally {
                // The area left the screen under a finger: forget it without resuming.
                playback.readingForget()
            }
        }
    }

/**
 * `onScrollBeginDrag`, `onScrollEndDrag` and the momentum pair: while the page area scrolls, by a
 * drag or by its fling, the loop stays paused; it resumes when the scroll comes to rest.
 */
@Composable
internal fun QuietSignalOnboardingReadingScroll(scroll: ScrollState, playback: QuietSignalOnboardingPlayback) {
    LaunchedEffect(scroll, playback) {
        var scrolling = false
        snapshotFlow { scroll.isScrollInProgress }.collect { active ->
            if (active == scrolling) return@collect
            scrolling = active
            if (active) playback.readingScrollStart() else playback.readingScrollEnd()
        }
    }
}

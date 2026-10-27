package com.ngocdevv.nativecook.blocks.media.silverscreencarousel

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.unit.dp
import kotlin.math.abs

/**
 * The carousel's pan (`Gesture.Pan().maxPointers(1).activeOffsetX([-8, 8]).failOffsetY([-16, 16])`,
 * SilverScreenCarousel.tsx:90-94), driving [motion].
 *
 * It begins when the finger has moved 8 dp sideways from where it came down and fails if it has moved
 * 16 dp up or down first, leaving a vertical drag to the page's scroll. The translation handed to the
 * row is measured from the touch-down point, so the 8 dp of the activation are part of it, as in
 * React Native. A second finger fails a pan that has not begun and cancels one that has. Releasing
 * ends it with the finger's speed in dp a second; every touch, begun or not, finally starts the quiet
 * period (`onFinalize`).
 *
 * With [enabled] false (a frozen `previewTime`) the modifier does nothing.
 */
internal fun Modifier.silverScreenCarouselPan(
    motion: SilverScreenCarouselMotion,
    enabled: Boolean,
): Modifier {
    if (!enabled) return this
    return pointerInput(motion) {
        val activateAt = 8.dp.toPx()
        val failAt = 16.dp.toPx()
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            val tracker = VelocityTracker()
            tracker.addPosition(down.uptimeMillis, down.position)
            var active = false
            var released = false
            try {
                while (true) {
                    val event = awaitPointerEvent()
                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                    if (event.changes.count { it.pressed } > 1) break
                    tracker.addPosition(change.uptimeMillis, change.position)
                    if (!change.pressed) {
                        released = true
                        break
                    }
                    val dx = change.position.x - down.position.x
                    val dy = change.position.y - down.position.y
                    if (!active) {
                        if (abs(dx) >= activateAt) {
                            active = true
                            motion.panStart()
                        } else if (abs(dy) >= failAt) {
                            break
                        }
                    }
                    if (active) {
                        motion.panUpdate(dx / density)
                        change.consume()
                    }
                }
            } finally {
                if (active) {
                    val velocity = if (released) tracker.calculateVelocity().x / density else 0f
                    motion.panEnd(released, velocity)
                }
                motion.panFinalize()
            }
        }
    }
}

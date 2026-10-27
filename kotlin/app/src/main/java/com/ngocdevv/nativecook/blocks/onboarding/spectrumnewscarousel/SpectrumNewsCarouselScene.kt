package com.ngocdevv.nativecook.blocks.onboarding.spectrumnewscarousel

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.layout
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.roundToInt

/** The page count of the carousel. */
private const val PAGE_COUNT = 5

/** `activeOffsetX([-10, 10])`: the pan starts once the finger is 10 dp to either side. */
private val SpectrumNewsCarouselActivateOffset = 10.dp

/** `failOffsetY([-18, 18])`: a finger that goes 18 dp up or down first is left to the scroll view. */
private val SpectrumNewsCarouselFailOffset = 18.dp

/** The scene's height is the tallest page, in whole dp (`Math.ceil` of the measured layout height). */
private fun Modifier.wholeDpHeight(): Modifier = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints)
    val whole = (ceil(placeable.height / density) * density).roundToInt()
    layout(placeable.width, whole.coerceIn(constraints.minHeight, constraints.maxHeight)) { placeable.place(0, 0) }
}

/**
 * The page area: the five pages stacked at the top left, clipped to the tallest of them, carrying the
 * horizontal pan (a finger, with the same rules as `Gesture.Pan()`: starts after 10 dp sideways,
 * leaves a mostly vertical drag to the scroll view, one finger only) and the accessibility actions.
 * [page] is the page shown, for the spoken description.
 */
@Composable
internal fun SpectrumNewsCarouselScene(
    page: Int,
    width: Float,
    drive: SpectrumNewsCarouselDrive,
    boardScale: Float,
    photo: ImageBitmap,
    modifier: Modifier = Modifier,
) {
    val previewing = drive.previewing
    Box(
        modifier
            .fillMaxWidth()
            .wholeDpHeight()
            .clipToBounds()
            .pointerInput(drive, previewing) {
                if (previewing) return@pointerInput
                val activate = SpectrumNewsCarouselActivateOffset.toPx()
                val fail = SpectrumNewsCarouselFailOffset.toPx()
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val tracker = VelocityTracker()
                    tracker.addPosition(down.uptimeMillis, down.position)
                    var active = false
                    var success = true
                    var translation = 0f
                    try {
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            // `maxPointers(1)`: a second finger ends the gesture.
                            if (event.changes.count { it.pressed } > 1) {
                                success = false
                                break
                            }
                            tracker.addPosition(change.uptimeMillis, change.position)
                            val dx = change.position.x - down.position.x
                            val dy = change.position.y - down.position.y
                            if (!active) {
                                // Someone else (the scroll view) has the touch.
                                if (change.isConsumed) break
                                if (abs(dy) >= fail) break
                                if (abs(dx) >= activate) {
                                    active = true
                                    drive.panStart()
                                }
                            }
                            if (active) {
                                change.consume()
                                translation = dx
                                drive.panUpdate((dx / density).toDouble())
                            }
                            if (!change.pressed) break
                        }
                    } finally {
                        if (active) {
                            val velocity = tracker.calculateVelocity()
                            drive.panFinalize((translation / density).toDouble(), (velocity.x / density).toDouble(), success)
                        }
                    }
                }
            }
            .clearAndSetSemantics {
                val text = SpectrumNewsCarouselPages[page]
                contentDescription = "Page ${page + 1} of $PAGE_COUNT. ${text.title} ${text.body} ${text.art}"
                customActions = listOf(
                    CustomAccessibilityAction("Next story") {
                        drive.go(page + 1)
                        true
                    },
                    CustomAccessibilityAction("Previous story") {
                        drive.go(page - 1)
                        true
                    },
                )
            },
    ) {
        for (index in 0 until PAGE_COUNT) {
            SpectrumNewsCarouselPage(index, width, drive, boardScale, photo)
        }
    }
}

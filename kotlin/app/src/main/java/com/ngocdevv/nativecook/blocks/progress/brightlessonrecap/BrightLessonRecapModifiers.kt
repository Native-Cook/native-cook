package com.ngocdevv.nativecook.blocks.progress.brightlessonrecap

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset

/** Far enough to hold anything the scene draws outside its own box. */
private const val LAYER_REACH = 100_000f

/**
 * React Native's `opacity` on a View: the whole subtree is composited as one and then faded,
 * and children that overflow the View's box (the big title, the bands, the "+45 XP" reward)
 * are not cut off, which `graphicsLayer { alpha }` would do with a layer the size of the box.
 */
internal fun Modifier.brightLessonRecapGroupAlpha(alpha: () -> Float): Modifier = drawWithCache {
    val paint = Paint()
    onDrawWithContent {
        val value = alpha()
        when {
            value >= 1f -> drawContent()
            value <= 0f -> Unit
            else -> {
                paint.alpha = value
                drawIntoCanvas { canvas ->
                    canvas.saveLayer(Rect(-LAYER_REACH, -LAYER_REACH, LAYER_REACH, LAYER_REACH), paint)
                    drawContent()
                    canvas.restore()
                }
            }
        }
    }
}

/**
 * `position: absolute` for decorative layers. The child is measured at a fixed size ([width], [height]),
 * the whole parent width ([fillWidth]) or its natural size, then placed at the offset [place] returns
 * (in pixels, from the parent's top-left corner) without taking part in the parent's size.
 * [place] receives the parent's width and the child's width in pixels, for the percentages and
 * `right:` offsets of the React Native styles.
 */
internal fun Modifier.brightLessonRecapPinned(
    width: Dp? = null,
    height: Dp? = null,
    fillWidth: Boolean = false,
    place: Density.(parentWidth: Int, childWidth: Int) -> IntOffset,
): Modifier = layout { measurable, constraints ->
    val parentWidth = if (constraints.hasBoundedWidth) constraints.maxWidth else 0
    val childWidth = when {
        fillWidth -> parentWidth
        width != null -> width.roundToPx()
        else -> null
    }
    val childHeight = height?.roundToPx()
    val placeable = measurable.measure(
        Constraints(
            minWidth = childWidth ?: 0,
            maxWidth = childWidth ?: (if (constraints.hasBoundedWidth) constraints.maxWidth else Constraints.Infinity),
            minHeight = childHeight ?: 0,
            maxHeight = childHeight ?: Constraints.Infinity,
        ),
    )
    val offset = place(parentWidth, placeable.width)
    layout(0, 0) { placeable.place(offset) }
}

/**
 * A React Native `Pressable`: [onPressIn] when the finger lands, [onPressOut] when it lifts or the
 * press is cancelled (a scroll took it over, or it left the button), [onPress] on a completed tap.
 */
internal fun Modifier.brightLessonRecapPress(
    enabled: Boolean,
    onPressIn: () -> Unit = {},
    onPressOut: () -> Unit = {},
    onPress: () -> Unit,
): Modifier = pointerInput(enabled) {
    if (!enabled) return@pointerInput
    detectTapGestures(
        onPress = {
            onPressIn()
            tryAwaitRelease()
            onPressOut()
        },
        onTap = { onPress() },
    )
}

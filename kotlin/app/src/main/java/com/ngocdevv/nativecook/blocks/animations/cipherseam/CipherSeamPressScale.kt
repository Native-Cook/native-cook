package com.ngocdevv.nativecook.blocks.animations.cipherseam

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/** The strong ease-out of `PressScale` (`cubicBezier(0.23, 1, 0.32, 1)`). */
private val PressEase = CubicBezierEasing(0.23f, 1f, 0.32f, 1f)

/**
 * `PressScale`: a button that answers the finger at once. The whole surface settles to 0.96 in
 * 150 ms on a strong ease-out and returns on the same curve, so a quick tap still reads as a press;
 * the transition retargets mid-flight. Under Reduce Motion ([reduced]) it dims to 0.72 instead.
 *
 * A press is kept while the finger stays within 16 dp of the button (`pressRetentionOffset`): it ends
 * when the finger leaves that area, begins again when it returns, and only a release inside counts
 * as a tap. A scroll view that takes the touch over cancels the press.
 *
 * [modifier] places the button (margins, alignment) and is not scaled; [surface] is its size, shape,
 * fill and shadow, which scale with it, and [contentPadding] the padding inside it (the touch area is
 * the whole surface). The content is centred on the surface.
 */
@Composable
internal fun CipherSeamPressScale(
    label: String,
    reduced: Boolean,
    onPress: () -> Unit,
    modifier: Modifier = Modifier,
    surface: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    content: @Composable BoxScope.() -> Unit,
) {
    val currentPress by rememberUpdatedState(onPress)
    var pressed by remember { mutableStateOf(false) }
    val progress = remember { Animatable(0f) }
    val dimPaint = remember { Paint() }

    LaunchedEffect(pressed) {
        progress.animateTo(if (pressed) 1f else 0f, tween(150, easing = PressEase))
    }

    Box(
        modifier = modifier
            .graphicsLayer {
                if (!reduced) {
                    val scale = 1f - 0.04f * progress.value
                    scaleX = scale
                    scaleY = scale
                }
            }
            .drawWithContent {
                val opacity = if (reduced) 1f - 0.28f * progress.value else 1f
                if (opacity >= 0.999f) {
                    drawContent()
                } else {
                    // A group opacity with room around the box, so a shadow is not clipped.
                    dimPaint.alpha = opacity
                    drawIntoCanvas { canvas ->
                        canvas.saveLayer(Rect(-120f, -120f, size.width + 120f, size.height + 120f), dimPaint)
                        drawContent()
                        canvas.restore()
                    }
                }
            }
            .then(surface)
            .semantics(mergeDescendants = true) {
                role = Role.Button
                contentDescription = label
                onClick(label) {
                    currentPress()
                    return@onClick true
                }
            }
            .pointerInput(Unit) {
                val retention = 16.dp.toPx()
                awaitEachGesture {
                    val first = awaitFirstDown(requireUnconsumed = false)
                    var inside = true
                    var released = false
                    pressed = true
                    try {
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == first.id } ?: break
                            if (change.isConsumed) break
                            val within = change.position.x >= -retention &&
                                change.position.x <= size.width + retention &&
                                change.position.y >= -retention &&
                                change.position.y <= size.height + retention
                            if (within != inside) {
                                inside = within
                                pressed = within
                            }
                            if (!change.pressed) {
                                released = inside
                                break
                            }
                        }
                    } finally {
                        pressed = false
                    }
                    if (released) currentPress()
                }
            }
            .padding(contentPadding),
        contentAlignment = Alignment.Center,
        content = content,
    )
}

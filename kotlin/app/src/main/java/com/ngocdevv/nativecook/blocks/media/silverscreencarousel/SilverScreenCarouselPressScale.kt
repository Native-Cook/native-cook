package com.ngocdevv.nativecook.blocks.media.silverscreencarousel

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/**
 * `PressScale` (shared/PressScale.tsx): a button that answers the finger at once. The whole surface
 * settles to 0.96 in 150 ms on the strong ease-out and returns on the same curve; the transition
 * retargets mid-flight. Under Reduce Motion ([reduced]) it dims to 0.72 instead. A disabled
 * ([enabled] false) button never moves and never answers.
 *
 * [surface] is the button's own layout and look (size, shape, fill, padding): it sits inside the scale,
 * so the whole surface moves. [description] is what accessibility reads; the text inside is hidden
 * from it.
 */
@Composable
internal fun SilverScreenCarouselPressScale(
    enabled: Boolean,
    reduced: Boolean,
    description: String,
    onPress: () -> Unit,
    surface: Modifier,
    modifier: Modifier = Modifier,
    contentAlignment: Alignment = Alignment.Center,
    content: @Composable BoxScope.() -> Unit,
) {
    val currentPress by rememberUpdatedState(onPress)
    var pressed by remember { mutableStateOf(false) }
    val progress = remember { Animatable(0f) }
    val down = pressed && enabled

    LaunchedEffect(down) {
        progress.animateTo(if (down) 1f else 0f, tween(150, easing = SilverScreenCarouselSettle))
    }

    Box(
        modifier = modifier
            .graphicsLayer {
                if (reduced) {
                    alpha = 1f - 0.28f * progress.value
                } else {
                    val scale = 1f - 0.04f * progress.value
                    scaleX = scale
                    scaleY = scale
                }
            }
            .then(surface)
            .semantics(mergeDescendants = true) {
                role = Role.Button
                contentDescription = description
                if (!enabled) disabled()
                onClick(description) {
                    if (!enabled) return@onClick false
                    currentPress()
                    return@onClick true
                }
            }
            .silverScreenCarouselPressable(
                enabled = enabled,
                onPressedChange = { pressed = it },
                onPress = { currentPress() },
            ),
        contentAlignment = contentAlignment,
        content = content,
    )
}

/**
 * The press of a React Native `Pressable` with `pressRetentionOffset={16}`: the press begins at the
 * touch, ends (press out) when the finger leaves the area 16 dp around the view, begins again if it
 * comes back, and only a release inside counts as a tap. A scroll view that takes over the touch
 * cancels it. [onPressedChange] and [onPress] are read at call time through the lambdas the caller
 * passes, which must not capture stale state.
 */
internal fun Modifier.silverScreenCarouselPressable(
    enabled: Boolean,
    onPressedChange: (Boolean) -> Unit,
    onPress: () -> Unit,
): Modifier = pointerInput(enabled) {
    if (!enabled) return@pointerInput
    val retention = 16.dp.toPx()
    awaitEachGesture {
        val first = awaitFirstDown(requireUnconsumed = false)
        var inside = true
        var released = false
        onPressedChange(true)
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
                    onPressedChange(within)
                }
                if (!change.pressed) {
                    released = inside
                    break
                }
            }
        } finally {
            if (inside) onPressedChange(false)
        }
        if (released) onPress()
    }
}

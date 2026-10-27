package com.ngocdevv.nativecook.blocks.onboarding.spectrumnewscarousel

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
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

/** `cubicBezier(0.23, 1, 0.32, 1)`: the strong ease-out every press in the React Native file settles on. */
private val SpectrumNewsCarouselPressCurve = CubicBezierEasing(0.23f, 1f, 0.32f, 1f)

/**
 * The press behaviour of `PressScale` and of `TourButton`: a Pressable that answers the finger at once.
 *
 * Under a finger the whole box settles to [pressedScale] in [scaleMillis] ms on the strong ease-out and
 * returns on the same curve; the transition retargets mid-flight. Under Reduce Motion ([reduced]) it
 * does not scale: with [dimsWhenReduced] (`PressScale`) it dims to 0.72 opacity instead, in the same time.
 * A press is kept while the finger stays within 16 dp of the box (`pressRetentionOffset`): it ends
 * (press out) when the finger leaves that area, begins again when it returns, and only a release inside
 * counts as a tap, which fires after the press out. A scroll view that takes over the touch cancels
 * the press. A disabled box never moves and never answers.
 *
 * The surface (background, radius, padding) belongs to [content], so it scales and dims with the box.
 */
@Composable
internal fun SpectrumNewsCarouselPressable(
    onPress: () -> Unit,
    onPressIn: () -> Unit,
    onPressOut: () -> Unit,
    reduced: Boolean,
    pressedScale: Float,
    scaleMillis: Int,
    dimsWhenReduced: Boolean,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    label: String? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    val currentPressIn by rememberUpdatedState(onPressIn)
    val currentPressOut by rememberUpdatedState(onPressOut)
    val currentPress by rememberUpdatedState(onPress)
    var pressed by remember { mutableStateOf(false) }
    val down = pressed && enabled
    val progress = remember { Animatable(0f) }

    LaunchedEffect(down, scaleMillis) {
        progress.animateTo(if (down) 1f else 0f, tween(scaleMillis, easing = SpectrumNewsCarouselPressCurve))
    }

    Box(
        modifier = modifier
            .graphicsLayer {
                if (!reduced) {
                    val scale = 1f - (1f - pressedScale) * progress.value
                    scaleX = scale
                    scaleY = scale
                } else if (dimsWhenReduced) {
                    alpha = 1f - 0.28f * progress.value
                }
            }
            .semantics(mergeDescendants = true) {
                role = Role.Button
                if (label != null) contentDescription = label
                if (!enabled) disabled()
                onClick(label) {
                    if (!enabled) return@onClick false
                    currentPress()
                    return@onClick true
                }
            }
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                val retention = 16.dp.toPx()
                awaitEachGesture {
                    val first = awaitFirstDown(requireUnconsumed = false)
                    var inside = true
                    var released = false
                    pressed = true
                    currentPressIn()
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
                                if (within) currentPressIn() else currentPressOut()
                            }
                            if (!change.pressed) {
                                released = inside
                                break
                            }
                        }
                    } finally {
                        if (inside) {
                            pressed = false
                            currentPressOut()
                        }
                    }
                    if (released) currentPress()
                }
            },
        content = content,
    )
}

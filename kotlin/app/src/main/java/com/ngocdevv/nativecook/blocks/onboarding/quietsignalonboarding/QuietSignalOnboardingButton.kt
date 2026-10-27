package com.ngocdevv.nativecook.blocks.onboarding.quietsignalonboarding

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/**
 * `TourButton`: a rounded surface (radius 18, at least 48 dp square, padding 15 by 14) holding
 * [content], the same button for Continue, the three session directions and "Back to the
 * introduction".
 *
 * Under a finger the whole surface settles to 0.97 in 120 ms on the strong ease-out and returns on
 * the same curve; under Reduce Motion it does not move. A press is kept while the finger stays
 * within 16 dp of the button (`pressRetentionOffset`): it ends (press out) when the finger leaves
 * that area, begins again when it returns, and only a release inside counts as a tap, delivered
 * after the press out. A scroll view that takes over the touch cancels the press.
 *
 * [label] is the accessible name; without one the text inside names the button.
 */
@Composable
internal fun QuietSignalOnboardingButton(
    background: Color,
    reduced: Boolean,
    onPressIn: () -> Unit,
    onPressOut: () -> Unit,
    onPress: () -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    content: @Composable () -> Unit,
) {
    val currentPressIn by rememberUpdatedState(onPressIn)
    val currentPressOut by rememberUpdatedState(onPressOut)
    val currentPress by rememberUpdatedState(onPress)
    var pressed by remember { mutableStateOf(false) }
    val progress = remember { Animatable(0f) }

    LaunchedEffect(pressed, reduced) {
        val target = if (pressed && !reduced) 1f else 0f
        if (reduced) {
            progress.snapTo(target)
        } else {
            progress.animateTo(target, tween(PRESS_MS, easing = QuietSignalOnboardingTimeline.EnterEasing))
        }
    }

    Box(
        modifier = modifier
            .graphicsLayer {
                val scale = 1f - (1f - PRESS_SCALE) * progress.value
                scaleX = scale
                scaleY = scale
            }
            .semantics(mergeDescendants = true) {
                role = Role.Button
                if (label != null) contentDescription = label
                onClick(label) {
                    currentPress()
                    return@onClick true
                }
            }
            .background(background, RoundedCornerShape(18.dp))
            .pointerInput(Unit) {
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
            }
            .widthIn(min = 48.dp)
            .heightIn(min = 48.dp)
            .padding(horizontal = 15.dp, vertical = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

private const val PRESS_MS = 120
private const val PRESS_SCALE = 0.97f

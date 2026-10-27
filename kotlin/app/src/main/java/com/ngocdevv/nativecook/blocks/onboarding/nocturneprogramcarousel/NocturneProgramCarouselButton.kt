package com.ngocdevv.nativecook.blocks.onboarding.nocturneprogramcarousel

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * `TourButton`: a 48 dp-minimum button, `radius 18`, `padding 15 by 14`, whose label is 16/22 semibold
 * [labelColor] (capped at 1.6 times the system text size).
 *
 * The whole button scales to 0.97 under a finger over 120 ms on the strong ease-out (no scale under
 * Reduce Motion) and comes back the same way. A press is kept while the finger stays within 16 dp of
 * the button (`pressRetentionOffset`): it ends (press out) when the finger leaves that area, begins
 * again when it returns, and only a release inside counts as a tap, which calls [onPress] after
 * [onPressOut]. A scroll view that takes over the touch cancels the press.
 *
 * [minHeight] is the style's `minHeight` on the outer view (55 for the footer button): the coloured
 * pressable stays at the top of it at its own height, 50 dp for one line.
 */
@Composable
internal fun NocturneProgramCarouselButton(
    label: String,
    reduced: Boolean,
    onPress: () -> Unit,
    modifier: Modifier = Modifier,
    background: Color = Color(0xFF256BFF),
    labelColor: Color = Color(0xFFEAF1FF),
    minHeight: Dp = 48.dp,
    enabled: Boolean = true,
    onPressIn: () -> Unit = {},
    onPressOut: () -> Unit = {},
) {
    val currentPressIn by rememberUpdatedState(onPressIn)
    val currentPressOut by rememberUpdatedState(onPressOut)
    val currentPress by rememberUpdatedState(onPress)
    var pressed by remember { mutableStateOf(false) }
    val scale = remember { Animatable(1f) }

    LaunchedEffect(pressed, reduced) {
        val target = if (pressed && !reduced) 0.97f else 1f
        if (reduced) {
            scale.snapTo(target)
        } else {
            scale.animateTo(target, tween(120, easing = NocturneProgramCarouselMotion.Enter))
        }
    }

    Box(
        modifier = modifier
            .heightIn(min = minHeight)
            .widthIn(min = 48.dp)
            .graphicsLayer {
                scaleX = scale.value
                scaleY = scale.value
            },
        contentAlignment = Alignment.TopStart,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .semantics(mergeDescendants = true) {
                    role = Role.Button
                    onClick(label) {
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
                }
                .clip(RoundedCornerShape(18.dp))
                .background(background)
                .padding(horizontal = 15.dp, vertical = 14.dp),
            contentAlignment = Alignment.Center,
        ) {
            NocturneProgramCarouselText(
                text = label,
                color = labelColor,
                size = 16f,
                lineHeight = 22f,
                weight = FontWeight.SemiBold,
                align = TextAlign.Center,
                maxScale = 1.6f,
            )
        }
    }
}

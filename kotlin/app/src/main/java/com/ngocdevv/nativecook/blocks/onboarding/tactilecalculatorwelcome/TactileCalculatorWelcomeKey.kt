package com.ngocdevv.nativecook.blocks.onboarding.tactilecalculatorwelcome

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.math.min

/**
 * `KeyButton`: one oversized isometric key. Its pressure (how far the cap has sunk, 0 to 1) is the
 * larger of what the finger does and what the 6.1 s loop does at loop time [time]; under Reduce
 * Motion it stays 0 and the key never moves.
 *
 * Under a finger the cap sinks in 110 ms on the strong ease-out and is released on a spring (420 ms
 * perceptual duration, damping ratio 0.62), which overshoots and is clamped at the rest height.
 * [onBegin] and [onEnd] tell the screen a finger is on a key (the loop holds still), [onSelect]
 * rewrites the headline, and [onPressHaptic] is the rigid tap at the bottom of the travel.
 *
 * The key box is 216 by 150 key units, `scale` dp a unit, placed by fractions of the scene. The hit
 * area is the face's inscribed rectangle (50, 30, 116 by 78 units), so neighbouring plates never steal
 * a press. [time] is read while drawing, so a frame redraws this key and nothing else.
 */
@Composable
internal fun TactileCalculatorWelcomeKey(
    spec: TactileCalculatorWelcomeKeySpec,
    time: () -> Double,
    reduced: Boolean,
    interactive: Boolean,
    sceneWidth: Float,
    sceneHeight: Float,
    scale: Float,
    onBegin: () -> Unit,
    onEnd: () -> Unit,
    onSelect: (TactileCalculatorWelcomeOperation) -> Unit,
    onPressHaptic: () -> Unit,
) {
    val painter = remember(spec) { TactileCalculatorWelcomeKeyPainter(spec) }
    val press = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val currentReduced by rememberUpdatedState(reduced)
    val currentBegin by rememberUpdatedState(onBegin)
    val currentEnd by rememberUpdatedState(onEnd)
    val currentSelect by rememberUpdatedState(onSelect)
    val currentHaptic by rememberUpdatedState(onPressHaptic)

    val left = spec.centerX * sceneWidth - 108f * scale
    val top = spec.top * sceneHeight - 8f * scale

    Box(
        modifier = Modifier
            .offset(left.dp, top.dp)
            .size((216f * scale).dp, (150f * scale).dp)
            .drawBehind {
                val demo = TactileCalculatorWelcomePressure.demo(spec.operation, time())
                val touched = max(0.0, max(press.value.toDouble(), demo))
                val pressure = if (reduced) 0.0 else min(1.0, touched)
                painter.draw(this, scale * density, pressure)
            },
    ) {
        Box(
            modifier = Modifier
                .offset((50f * scale).dp, (30f * scale).dp)
                .size((116f * scale).dp, (78f * scale).dp)
                .semantics(mergeDescendants = true) {
                    role = Role.Button
                    contentDescription = spec.label
                    onClick(spec.label) {
                        currentSelect(spec.operation)
                        return@onClick true
                    }
                }
                .tactileCalculatorWelcomePress(
                    enabled = interactive,
                    retention = PRESS_RETENTION,
                    onPressIn = {
                        currentBegin()
                        scope.launch {
                            if (currentReduced) {
                                press.snapTo(0f)
                            } else {
                                press.animateTo(1f, tween(durationMillis = 110, easing = TactileCalculatorWelcomeEase))
                            }
                        }
                        currentHaptic()
                    },
                    onPressOut = {
                        scope.launch {
                            press.animateTo(0f, RELEASE_SPRING, initialVelocity = 0f)
                        }
                        currentEnd()
                    },
                    onPress = { currentSelect(spec.operation) },
                ),
        )
    }
}

/**
 * `withSpring(0, { duration: 420, dampingRatio: 0.62 })`. Reanimated 4.5 reads `duration` as a
 * perceptual duration and solves for the stiffness that has the spring settled after 1.5 x 420 ms
 * (`calculateNewStiffnessToMatchDuration`): for a damping ratio of 0.62 that is w0 = 31.2 rad/s,
 * so stiffness w0 squared = 973.5 with mass 1 (the same spring as the Swift port).
 */
private val RELEASE_SPRING = spring<Float>(dampingRatio = 0.62f, stiffness = 973.5f)

/** The finger may wander this far from the hit area before the press ends (the press retention). */
private val PRESS_RETENTION = 20.dp

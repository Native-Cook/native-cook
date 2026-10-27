package com.ngocdevv.nativecook.blocks.onboarding.tactilecalculatorwelcome

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp

/**
 * A React Native `Pressable`: [onPressIn] when a finger lands, [onPressOut] when it lifts or leaves,
 * [onPress] after that when it lifted inside.
 *
 * The press is kept while the finger stays within [retention] of the element (`pressRetentionOffset`):
 * it ends (press out) when the finger leaves that area and begins again (press in) when it returns, and
 * only a release inside counts as a press. A scroll view that takes over the touch cancels the press.
 * Press out always comes before press, as in React Native. A disabled element answers nothing.
 *
 * The callbacks are called from a long-running gesture block, so the caller passes lambdas that read
 * the current state (for example through `rememberUpdatedState`) and not captured copies of it.
 */
internal fun Modifier.tactileCalculatorWelcomePress(
    enabled: Boolean,
    retention: Dp,
    onPressIn: () -> Unit,
    onPressOut: () -> Unit,
    onPress: () -> Unit,
): Modifier = pointerInput(enabled, retention) {
    if (!enabled) return@pointerInput
    val slop = retention.toPx()
    awaitEachGesture {
        val first = awaitFirstDown(requireUnconsumed = false)
        var inside = true
        var released = false
        onPressIn()
        try {
            while (true) {
                val event = awaitPointerEvent()
                val change = event.changes.firstOrNull { it.id == first.id } ?: break
                if (change.isConsumed) break
                val within = change.position.x >= -slop &&
                    change.position.x <= size.width + slop &&
                    change.position.y >= -slop &&
                    change.position.y <= size.height + slop
                if (within != inside) {
                    inside = within
                    if (within) onPressIn() else onPressOut()
                }
                if (!change.pressed) {
                    released = inside
                    break
                }
            }
        } finally {
            if (inside) onPressOut()
        }
        if (released) onPress()
    }
}

package com.ngocdevv.nativecook.blocks.gestures.paperdecktutorial

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/**
 * A react-native-gesture-handler `Pressable` with no visual feedback of its own: [onPressIn] when the
 * finger lands, [onPressOut] when it leaves or moves out of the button's retention area (20 dp at the
 * sides and top, 30 dp below, React Native's default) and [onPress] when it lifts inside it. A screen
 * reader's click calls only [onPress], as in React Native.
 */
@Composable
internal fun PaperDeckTutorialPressable(
    label: String,
    onPressIn: () -> Unit,
    onPressOut: () -> Unit,
    onPress: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val currentPressIn by rememberUpdatedState(onPressIn)
    val currentPressOut by rememberUpdatedState(onPressOut)
    val currentPress by rememberUpdatedState(onPress)
    Box(
        modifier = modifier
            .semantics(mergeDescendants = true) {
                role = Role.Button
                contentDescription = label
                onClick(label) {
                    currentPress()
                    return@onClick true
                }
            }
            .pointerInput(Unit) {
                val sides = 20.dp.toPx()
                val below = 30.dp.toPx()
                awaitEachGesture {
                    val first = awaitFirstDown(requireUnconsumed = false)
                    var inside = true
                    var released = false
                    currentPressIn()
                    try {
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == first.id } ?: break
                            if (change.isConsumed) break
                            val within = change.position.x >= -sides &&
                                change.position.x <= size.width + sides &&
                                change.position.y >= -sides &&
                                change.position.y <= size.height + below
                            if (within != inside) {
                                inside = within
                                if (within) currentPressIn() else currentPressOut()
                            }
                            if (!change.pressed) {
                                released = inside
                                break
                            }
                        }
                    } finally {
                        if (inside) currentPressOut()
                    }
                    if (released) currentPress()
                }
            },
        contentAlignment = Alignment.Center,
        content = content,
    )
}

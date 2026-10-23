package com.ngocdevv.nativecook.blocks.cards.aurawalletwidget

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput

// Press retention belongs to the fixed button footprint, as in the source.
// Send answers only accepted touches; the other three are quiet demo controls.
internal fun Modifier.auraButton(machine: AuraMachine, index: Int): Modifier = pointerInput(machine, index) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        machine.touch()
        val accepted = index != 3 || !machine.isLocked()
        if (accepted) machine.press(index, true)
        down.consume()
        var activate = false
        try {
            while (true) {
                val event = awaitPointerEvent()
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                if (change.isConsumed) break
                val retention = AuraPalette.Touch.retention * density
                val position = change.position
                val inside = position.x >= -retention && position.x <= size.width + retention &&
                    position.y >= -retention && position.y <= size.height + retention
                if (!inside || event.changes.count { it.pressed } > 1) break
                change.consume()
                if (!change.pressed) {
                    activate = accepted
                    break
                }
            }
        } finally {
            machine.press(index, false)
        }
        if (activate && index == 3) machine.send()
    }
}

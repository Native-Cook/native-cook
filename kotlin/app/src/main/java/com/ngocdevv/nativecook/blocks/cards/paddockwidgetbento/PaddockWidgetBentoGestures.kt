package com.ngocdevv.nativecook.blocks.cards.paddockwidgetbento

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp

/** A pending hold never consumes motion, so the surrounding feed can win a scroll. */
internal fun Modifier.paddockQuickCardGestures(
    scale: Float,
    runtime: PaddockWidgetBentoRuntime,
    onTap: (PaddockWidgetBentoCardID) -> Unit,
    onLift: () -> Unit,
    onCommit: () -> Unit,
): Modifier = pointerInput(scale, runtime) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        val owner = down.id.value
        fun referenceY(y: Float): Double = y / density / scale + (PaddockWidgetBentoMotion.slotY[0] - 31.0)
        if (!runtime.down(owner, referenceY(down.position.y), down.uptimeMillis / 1_000.0)) {
            return@awaitEachGesture
        }
        var pointer = down
        var cancelled = false
        var released = false
        try {
            val remaining = kotlin.math.ceil(PaddockWidgetBentoMotion.longPressDuration * 1_000 -
                (paddockNowSeconds() * 1_000 - down.uptimeMillis)).toLong().coerceAtLeast(1)
            val completedBeforeHold = withTimeoutOrNull(remaining) {
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Main)
                    val current = event.changes.firstOrNull { it.id == down.id }
                    if (current == null || event.changes.any { it.id != down.id && it.pressed } ||
                        current.isConsumed || runtime.activeOwner != owner
                    ) {
                        cancelled = true
                        break
                    }
                    pointer = current
                    if (!current.pressed) {
                        released = true
                        break
                    }
                    if ((current.position - down.position).getDistance() > 10.dp.toPx()) {
                        cancelled = true
                        break
                    }
                    val final = awaitPointerEvent(PointerEventPass.Final)
                    if (final.changes.any { it.id == down.id && it.isConsumed }) {
                        cancelled = true
                        break
                    }
                }
                true
            }
            when {
                cancelled -> runtime.cancel(owner)
                released -> {
                    pointer.consume()
                    runtime.up(owner, onTap)
                }
                completedBeforeHold == null -> {
                    if (!runtime.hold(owner, referenceY(pointer.position.y))) {
                        runtime.cancel(owner)
                        return@awaitEachGesture
                    }
                    pointer.consume()
                    onLift()
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Main)
                        val current = event.changes.firstOrNull { it.id == down.id }
                        if (current == null || event.changes.any { it.id != down.id && it.pressed } ||
                            current.isConsumed || runtime.activeOwner != owner
                        ) {
                            runtime.cancel(owner)
                            break
                        }
                        current.consume()
                        if (!current.pressed) {
                            if (runtime.up(owner, onTap)) onCommit()
                            break
                        }
                        runtime.move(owner, referenceY(current.position.y))
                    }
                }
            }
        } finally {
            // Pointer input cancellation, navigation and disposal must restore the snapshot.
            if (runtime.activeOwner == owner) runtime.cancel(owner)
        }
    }
}

package com.ngocdevv.nativecook.blocks.cards.habitatspecimencarousel

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.hypot
import kotlin.math.max

// Gesture.Race(pan, hold, tap) of the reference, with its thresholds: a pan activates after 3 pt of
// travel; a hold activates after 380 ms within 12 pt; a tap is a release within 450 ms and 10 pt.
// The first to activate wins and cancels the others. Every touch also feeds the particle world.
private const val PAN_MIN_DISTANCE = 3f
private const val HOLD_MS = 380L
private const val HOLD_MAX_DISTANCE = 12f
private const val TAP_MAX_MS = 450L
private const val TAP_MAX_DISTANCE = 10f

private enum class Race { Undecided, Pan, Hold, Done }

internal fun Modifier.habitatGestures(motion: HabitatMotion): Modifier = pointerInput(motion) {
    val density = this.density
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        val tracker = VelocityTracker()
        tracker.addPosition(down.uptimeMillis, down.position)
        val startX = down.position.x / density
        val startY = down.position.y / density
        motion.touchDown(startX, startY)
        var race = Race.Undecided
        var x = startX
        var y = startY
        var lastTime = down.uptimeMillis
        while (true) {
            val event = if (race == Race.Undecided) {
                val wait = max(1L, HOLD_MS - (lastTime - down.uptimeMillis))
                withTimeoutOrNull(wait) { awaitPointerEvent(PointerEventPass.Main) }
            } else {
                awaitPointerEvent(PointerEventPass.Main)
            }
            if (event == null) {
                // The hold timer ran out with the finger still within 12 pt: it pets.
                if (hypot(x - startX, y - startY) <= HOLD_MAX_DISTANCE) {
                    race = Race.Hold
                    motion.holdStart()
                    // The race cancels the pan and the tap.
                    motion.panFinalize()
                    motion.tapFinalize()
                } else race = Race.Done
                lastTime = down.uptimeMillis + HOLD_MS
                continue
            }
            val change = event.changes.firstOrNull { it.id == down.id } ?: break
            lastTime = change.uptimeMillis
            x = change.position.x / density
            y = change.position.y / density
            val elapsed = change.uptimeMillis - down.uptimeMillis
            val distance = hypot(x - startX, y - startY)
            if (change.changedToUpIgnoreConsumed() || !change.pressed) {
                val up = change.changedToUpIgnoreConsumed()
                when (race) {
                    Race.Undecided -> {
                        if (up && elapsed <= TAP_MAX_MS && distance <= TAP_MAX_DISTANCE) motion.tapEnd(x, y)
                        motion.panFinalize()
                        motion.tapFinalize()
                    }
                    Race.Pan -> {
                        if (up) motion.panEnd(tracker.calculateVelocity().x / density)
                        motion.panFinalize()
                    }
                    Race.Hold -> motion.holdFinalize()
                    Race.Done -> Unit
                }
                break
            }
            tracker.addPosition(change.uptimeMillis, change.position)
            motion.touchMove(x, y)
            when (race) {
                Race.Undecided -> if (distance >= PAN_MIN_DISTANCE) {
                    race = Race.Pan
                    // The race cancels the tap and the hold.
                    motion.tapFinalize()
                    motion.panUpdate(x - startX, tracker.calculateVelocity().x / density, x)
                }
                Race.Pan -> motion.panUpdate(x - startX, tracker.calculateVelocity().x / density, x)
                Race.Hold -> if (distance > HOLD_MAX_DISTANCE) { race = Race.Done; motion.holdFinalize() }
                Race.Done -> Unit
            }
        }
        motion.touchUp()
    }
}

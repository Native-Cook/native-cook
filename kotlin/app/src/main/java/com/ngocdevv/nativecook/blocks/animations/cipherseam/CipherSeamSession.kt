package com.ngocdevv.nativecook.blocks.animations.cipherseam

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlin.math.abs

/**
 * What the React Native component keeps in state and refs around its `time` shared value:
 * whether a hand has taken the loop over ([manual]), whether a finger is on the screen
 * ([interacting]), whether the info sheet is open ([info]), and the epoch that tells a finished
 * replay from a newer touch. The composable runs the effects of the React Native file on these.
 *
 * With [previewTime] set the clock is frozen: nothing here changes it.
 */
internal class CipherSeamSession(initialTime: Double, val previewTime: Double?) {
    val clock = CipherSeamClock(initialTime)

    /** `manual`: a touch or a replay is driving the time, so the loop waits. */
    var manual by mutableStateOf(false)

    /** `interacting`: a finger is down, or a replay is still running. */
    var interacting by mutableStateOf(false)

    /** `info`: the "One boundary. Two representations." sheet is open. */
    var info by mutableStateOf(false)

    private var epoch = 0
    private var dragOwner: Int? = null
    private var dragStartTime = 0.0

    /** `begin`: a hand takes over; returns the owner that its `release` has to match. */
    fun begin(): Int {
        epoch += 1
        manual = true
        interacting = true
        return epoch
    }

    /** `release`: the owner is done; an older owner does not end a newer interaction. */
    fun release(owner: Int? = null) {
        if (owner == null || owner == epoch) interacting = false
    }

    /** The Talk to an Expert button of the panel: replays the pass once, at the clock's own pace. */
    fun replay(reduced: Boolean) {
        if (previewTime != null) return
        val owner = begin()
        clock.cancel()
        clock.set(if (reduced) REDUCED_REPLAY_TIME else CipherSeamTimeline.DRAG_FROM)
        if (reduced) {
            release(owner)
        } else {
            clock.timeTo(CipherSeamTimeline.DRAG_TO, REPLAY_MS) { release(owner) }
        }
    }

    /** The Pan gesture's `onStart`: stops the clock where it is and remembers where the drag began. */
    fun dragStart() {
        clock.cancel()
        dragStartTime = clock.time.coerceIn(CipherSeamTimeline.DRAG_FROM, CipherSeamTimeline.DRAG_TO)
        dragOwner = begin()
    }

    /** `onUpdate`: the finger's travel in dp scrubs the pass, one hero width being 7.75 s. */
    fun dragUpdate(translationX: Float, heroWidth: Float) {
        val target = dragStartTime + translationX / heroWidth * CipherSeamTimeline.DRAG_SPAN
        clock.set(target.coerceIn(CipherSeamTimeline.DRAG_FROM, CipherSeamTimeline.DRAG_TO))
    }

    /** `onEnd`: springs to the nearer end of the pass, or to the one the finger is flicking toward. */
    fun dragEnd(velocityX: Float, heroWidth: Float) {
        val rightward = if (abs(velocityX) > FLICK_SPEED) velocityX > 0f else clock.time > MIDPOINT
        val target = if (rightward) CipherSeamTimeline.DRAG_TO else CipherSeamTimeline.DRAG_FROM
        clock.springTo(target, velocityX / heroWidth * CipherSeamTimeline.DRAG_SPAN)
    }

    /** `onFinalize`: the gesture is over, however it ended. */
    fun dragFinalize() {
        val owner = dragOwner
        dragOwner = null
        if (owner != null) release(owner)
    }

    private companion object {
        /** `time.set(reduced ? 6 : 2.75)`: under Reduce Motion the replay shows the card across the seam. */
        const val REDUCED_REPLAY_TIME = 6.0

        /** `withTiming(10.5, { duration: 7750 })`. */
        const val REPLAY_MS = 7750

        /** `Math.abs(event.velocityX) > 110` (dp per second). */
        const val FLICK_SPEED = 110f

        /** `time.get() > 6.5`. */
        const val MIDPOINT = 6.5
    }
}

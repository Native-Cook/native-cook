package com.ngocdevv.nativecook.blocks.onboarding.quietsignalonboarding

import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.max

/**
 * `usePlayback`: the block's one clock and the rules for when it runs. Every frame of the screen is
 * a function of [displayTime], in seconds of loop time.
 *
 * The clock runs while [running]: `autoPlay` is on, the screen is in the foreground, Reduce Motion
 * is off and no `previewTime` freezes it. Any touch pauses it; [settle] then jumps to a page's
 * anchor (or stays where it is) and starts it again [QUIET_MS] after the last touch ended. The
 * composable calls the `on...` functions where the React Native hook has its `useEffect`s.
 *
 * Every function reads its own fields when it is called, so nothing here goes stale between
 * compositions.
 */
internal class QuietSignalOnboardingPlayback(
    private val scope: CoroutineScope,
    private val reducedState: State<Boolean>,
    /** `previewPhase`: the loop time `previewTime` freezes the screen at, or null. */
    val previewPhase: Double?,
    /** Kept up to date by the composable; read when a quiet period ends. */
    var autoPlay: Boolean,
) {
    val previewing: Boolean get() = previewPhase != null

    /** The live Reduce Motion preference (animator duration scale 0). */
    val reduced: Boolean get() = reducedState.value

    /** The Activity is resumed (`AppState` is active). */
    var foreground by mutableStateOf(true)
        private set

    /** `playing`: the clock wants to run, before the foreground, Reduce Motion and preview checks. */
    var playing by mutableStateOf(autoPlay)
        private set

    /** The clock's own time. Page changes and settling move it; frames advance it while [running]. */
    var time by mutableDoubleStateOf(previewPhase ?: if (autoPlay && !reducedState.value) 0.0 else REST)
        private set

    /** `displayedTime`: what the screen draws; a preview pose cannot be moved by a late callback. */
    val displayTime: Double get() = previewPhase ?: time

    /** `playing && foreground && !reduced && !previewing`: frames are advancing [time]. */
    val running: Boolean get() = playing && foreground && !reduced && !previewing

    private var previousReduced = reducedState.value
    private var settledTime = REST
    private var stopped = false
    private var readingTouches = 0
    private var readingDrag = false
    private var gestureHeld = false
    private var quietTimer: Job? = null

    /**
     * Advances [time] one frame at a time while [running]. The composable launches it under a key
     * of [running], so it ends when the clock is paused; the check inside covers the frame in
     * which a touch arrived.
     */
    suspend fun runClock() {
        var base = -1L
        var baseTime = 0.0
        while (true) {
            withFrameNanos { now ->
                if (!running) {
                    base = -1L
                    return@withFrameNanos
                }
                if (base < 0L) {
                    base = now
                    baseTime = time
                }
                time = (baseTime + (now - base) / 1e9) % QuietSignalOnboardingTimeline.LOOP
            }
        }
    }

    /** `AppState` 'change': leaving the foreground drops every touch; returning resumes autoplay. */
    fun onForeground(active: Boolean) {
        foreground = active
        clearQuietTimer()
        if (active) {
            if (!stopped && !hasOwnership()) playing = autoPlay
        } else {
            clearOwnership()
        }
    }

    /** The effect on `autoPlay` and `previewing`: back to the loop unless something holds the screen. */
    fun onAutoPlayChanged() {
        clearQuietTimer()
        if (!stopped && !hasOwnership()) playing = autoPlay
    }

    /**
     * The two effects on `reduced`. Reduce Motion going on parks the clock at the last settled
     * time; going off again resumes from the selected page after the usual quiet period.
     */
    fun onReducedChanged() {
        val now = reduced
        val restoredMotion = previousReduced && !now
        previousReduced = now
        if (now && !previewing) {
            clearQuietTimer()
            time = settledTime
        }
        if (restoredMotion && autoPlay && foreground && !previewing && !stopped && !hasOwnership()) {
            settle(time)
        }
    }

    /** `pause`: the clock stops where it is and no pending resume survives. */
    fun pause() {
        clearQuietTimer()
        playing = false
    }

    /**
     * `settle`: pauses, puts the clock at [anchor], and (for an autoplaying screen nobody holds)
     * starts it again after [QUIET_MS]. A preview never moves.
     */
    fun settle(anchor: Double) {
        pause()
        if (previewPhase != null) {
            time = previewPhase
            return
        }
        settledTime = anchor
        time = anchor
        if (autoPlay && foreground && !reduced && !stopped && !hasOwnership()) {
            quietTimer = scope.launch {
                delay(QUIET_MS)
                quietTimer = null
                if (!stopped && !hasOwnership() && foreground) playing = true
            }
        }
    }

    /** The horizontal pan took the screen (`beginGesture`). */
    fun beginGesture() {
        gestureHeld = true
        pause()
    }

    /** The horizontal pan let go (`endGesture`). */
    fun endGesture() {
        gestureHeld = false
    }

    /** A finger went down on the reading area; [count] fingers are down now (`onTouchStart`). */
    fun readingTouchStart(count: Int) {
        readingTouches = max(1, count)
        pause()
    }

    /** A finger left the reading area; [count] are still down (`onTouchEnd`). */
    fun readingTouchEnd(count: Int) {
        readingTouches = count
        releaseReading()
    }

    /** The reading area began to scroll, dragged or flung (`onScrollBeginDrag`, `onMomentumScrollBegin`). */
    fun readingScrollStart() {
        readingDrag = true
        pause()
    }

    /** The reading area stopped scrolling. */
    fun readingScrollEnd() {
        readingDrag = false
        releaseReading()
    }

    /** The reading area left the screen with fingers on it: forget them without resuming. */
    fun readingForget() {
        readingTouches = 0
    }

    /** `stop`: the tour is over for good, until [restart]. */
    fun stop() {
        stopped = true
        clearOwnership()
        pause()
    }

    /** `restart`: the introduction is back, settled at [anchor]. */
    fun restart(anchor: Double) {
        stopped = false
        settle(anchor)
    }

    /** `remember`: the page the screen is showing becomes the one a settle falls back to. */
    fun rememberAnchor(anchor: Double) {
        settledTime = anchor
    }

    /** Reading never seeks to a page anchor or fires a landing haptic: it settles where it is. */
    private fun releaseReading() {
        if (!stopped && !hasOwnership()) settle(time)
    }

    private fun hasOwnership(): Boolean = readingTouches > 0 || readingDrag || gestureHeld

    private fun clearOwnership() {
        readingTouches = 0
        readingDrag = false
        gestureHeld = false
    }

    private fun clearQuietTimer() {
        quietTimer?.cancel()
        quietTimer = null
    }

    companion object {
        /** `restTime`, `ANCHORS[0]`: where a screen that is not playing rests. */
        const val REST = 3.0

        /** The 2 s of quiet after the last touch before the loop starts again. */
        const val QUIET_MS = 2000L
    }
}

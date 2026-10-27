package com.ngocdevv.nativecook.blocks.onboarding.spectrumnewscarousel

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.max

/**
 * `usePlayback` in `motion.ts`: the loop's one clock plus the rules for when it runs.
 *
 * [time] is loop time in seconds, from 0 up to [SPECTRUM_NEWS_CAROUSEL_LOOP]; every frame of the
 * screen is a function of it, so a frozen [time] is a frozen frame. It runs while [running] is true.
 * A touch pauses it; two seconds after the last touch lets go ([settle]) it starts again from where
 * the page rests. Everything it keeps is either snapshot state (read inside draw and layer blocks,
 * so a frame redraws only those) or a plain field only the touch handlers read.
 *
 * The composable keeps [autoPlay], [reduced] and [previewPhase] up to date and calls the `on...`
 * functions where the React Native file has its `useEffect`s.
 */
@Stable
internal class SpectrumNewsCarouselPlayback(
    private val scope: CoroutineScope,
    initialTime: Double,
    autoPlay: Boolean,
    foreground: Boolean,
    reduced: Boolean,
    previewPhase: Double?,
) {
    private val timeState = mutableDoubleStateOf(initialTime)

    /** `playing`: whether the loop is meant to be moving (before the app, reduced motion and preview are weighed in). */
    var playing by mutableStateOf(autoPlay)
        private set

    /** `foreground`: the screen is resumed. */
    var foreground by mutableStateOf(foreground)
        private set

    /** The animator duration scale is 0 (Reduce Motion). */
    var reduced by mutableStateOf(reduced)

    /** `previewPhase`: the loop time the clock is frozen at, or null when it is not frozen. */
    var previewPhase by mutableStateOf(previewPhase)

    var autoPlay: Boolean = autoPlay

    /** `settledTime`: where reduced motion parks the clock, kept in step with the page shown. */
    var settledTime: Double = SpectrumNewsCarouselAnchors[0]
        private set

    private var epochNanos = UNSET
    private var stopped = false
    private var readingTouches = 0
    private var readingDrag = false
    private var gestureHeld = false
    private var quietTimer: Job? = null

    /** `previewing`. */
    val previewing: Boolean get() = previewPhase != null

    /** `displayedTime`: the preview phase when frozen, else the clock. Reading it in a draw block redraws only that block. */
    val time: Double get() = previewPhase ?: timeState.doubleValue

    /** `playing && foreground && !reduced && !previewing`: the clock is advancing. */
    fun running(): Boolean = playing && foreground && !reduced && previewPhase == null

    private fun hasOwnership(): Boolean = readingTouches > 0 || readingDrag || gestureHeld

    private fun clearOwnership() {
        readingTouches = 0
        readingDrag = false
        gestureHeld = false
    }

    fun clearQuietTimer() {
        quietTimer?.cancel()
        quietTimer = null
    }

    fun setTime(value: Double) {
        timeState.doubleValue = value
    }

    /** The next frame takes the clock's start from the current [time] (`epoch.set(-1)`). */
    fun restartClock() {
        epochNanos = UNSET
    }

    /** One frame of the loop (`useFrameCallback`). */
    fun onFrame(nanos: Long) {
        if (!running()) return
        if (epochNanos == UNSET) epochNanos = nanos - (timeState.doubleValue * 1e9).toLong()
        timeState.doubleValue = ((nanos - epochNanos) / 1e9) % SPECTRUM_NEWS_CAROUSEL_LOOP
    }

    /** The app came to the front or left it (`AppState`). */
    fun onForeground(active: Boolean) {
        foreground = active
        clearQuietTimer()
        if (active) {
            if (!stopped && !hasOwnership()) playing = autoPlay
        } else {
            clearOwnership()
        }
    }

    /** The screen is going away: no timer and no touch keeps the clock from running next time. */
    fun release() {
        clearQuietTimer()
        clearOwnership()
    }

    /** `autoPlay` or the preview changed: the loop follows `autoPlay` again. */
    fun onAutoPlayOrPreviewChanged() {
        clearQuietTimer()
        if (!stopped && !hasOwnership()) playing = autoPlay
    }

    /** Reduce Motion came on: park the clock on the page that was resting. */
    fun onReducedMotionOn() {
        clearQuietTimer()
        timeState.doubleValue = settledTime
    }

    /** Reduce Motion went off again: resume from the selected page after the usual quiet period. */
    fun onReducedMotionOff() {
        if (autoPlay && foreground && previewPhase == null && !stopped && !hasOwnership()) settle(timeState.doubleValue)
    }

    /** `pause`. */
    fun pause() {
        clearQuietTimer()
        playing = false
    }

    /** `settle`: park the clock on [anchor] and, when the loop is meant to run, start it again after two seconds. */
    fun settle(anchor: Double) {
        pause()
        val frozen = previewPhase
        if (frozen != null) {
            timeState.doubleValue = frozen
            return
        }
        settledTime = anchor
        timeState.doubleValue = anchor
        epochNanos = UNSET
        if (autoPlay && foreground && !reduced && !stopped && !hasOwnership()) {
            quietTimer = scope.launch {
                delay(QUIET_MILLIS)
                quietTimer = null
                if (!stopped && !hasOwnership() && foreground) playing = true
            }
        }
    }

    /** `stop`: the briefing is open, so the loop stays down until [restart]. */
    fun stop() {
        stopped = true
        clearOwnership()
        pause()
    }

    /** `remember`: the page shown changed; reduced motion parks the clock on it. */
    fun rememberAnchor(anchor: Double) {
        settledTime = anchor
    }

    /** `restart`. */
    fun restart(anchor: Double) {
        stopped = false
        settle(anchor)
    }

    /** `beginGesture`: the horizontal pan holds the clock. */
    fun beginGesture() {
        gestureHeld = true
        pause()
    }

    /** `endGesture`. */
    fun endGesture() {
        gestureHeld = false
    }

    /** `releaseReading`: reading never seeks to a page anchor or emits a landing haptic. */
    private fun releaseReading() {
        if (!stopped && !hasOwnership()) settle(timeState.doubleValue)
    }

    /** `onTouchStart` of the page area: [count] fingers are down. */
    fun touchStart(count: Int) {
        readingTouches = max(1, count)
        pause()
    }

    /** `onTouchEnd` / `onTouchCancel` of the page area: [count] fingers are still down. */
    fun touchEnd(count: Int) {
        readingTouches = count
        releaseReading()
    }

    /** `onScrollBeginDrag` / `onMomentumScrollBegin`. */
    fun scrollBegan() {
        readingDrag = true
        pause()
    }

    /** `onScrollEndDrag` / `onMomentumScrollEnd`. */
    fun scrollEnded() {
        readingDrag = false
        releaseReading()
    }

    private companion object {
        const val UNSET = Long.MIN_VALUE
        const val QUIET_MILLIS = 2000L
    }
}

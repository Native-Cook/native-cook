package com.ngocdevv.nativecook.blocks.onboarding.nocturneprogramcarousel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * The block's one clock and everything that decides who owns it: `usePlayback` in `motion.ts`, plus
 * the shared values `manual` and `touching` and the spring that drives `manual` from
 * `NocturneProgramCarousel.tsx`. Every frame of the screen is a function of [time], in seconds of
 * loop time, so a frozen [time] is a frozen frame.
 *
 * The clock runs while [running]: playing, in the foreground, not reduced and not previewing. A touch
 * anywhere on the tour pauses it; two seconds after the last touch it starts again from where it
 * stopped (reading) or from the page's anchor (a swipe, a button). The composable calls [update]
 * after every composition where React Native has its `useEffect`s, and [advance] once per frame.
 *
 * Differences from the React Native file: `playing` and `enabled` are one flag read per frame, the
 * quiet timer is a deadline compared with the frame clock, and a scroll's drag and momentum are
 * one flag (Compose reports only whether a scroll is in progress).
 */
internal class NocturneProgramCarouselPlayback(
    autoPlay: Boolean,
    reduced: Boolean,
    foreground: Boolean,
    previewTime: Double?,
) {
    var autoPlay: Boolean = autoPlay
        private set
    var reduced: Boolean = reduced
        private set
    var foreground: Boolean = foreground
        private set

    /** `previewPhase`: the frozen loop time, or null when the loop runs. */
    var previewPhase: Double? = previewTime?.let { previewPhaseOf(it) }
        private set
    val previewing: Boolean get() = previewPhase != null

    private var clockSeconds by mutableDoubleStateOf(
        previewPhase ?: if (autoPlay && !reduced) 0.0 else REST_TIME,
    )

    /** `displayedTime`: loop time in seconds. Reading it inside a draw or `graphicsLayer` block redraws only that block. */
    val time: Double
        get() {
            val clock = clockSeconds
            return previewPhase ?: clock
        }

    /** `touching`: the carousel follows [manual] instead of the timeline. */
    var touching: Boolean by mutableStateOf(false)
        private set

    /** `manual`: the carousel's position in pages while a finger or a spring owns it. */
    var manual: Float by mutableFloatStateOf(0f)
        private set

    /** `startPage`: [manual] when the current drag began. */
    var startPage: Float = 0f
        private set

    /** `playing` before the foreground, reduced-motion and preview checks. */
    private var playing = autoPlay
    private var stopped = false
    private var settledTime = REST_TIME
    private var epochNanos = NOT_STARTED
    private var quietDeadline = NO_DEADLINE
    private var frameNanos = 0L

    private var readingTouches = 0
    private var readingDrag = false
    private var gestureHeld = false

    private var springActive = false
    private var springFrom = 0.0
    private var springTo = 0.0
    private var springVelocity = 0.0
    private var springStart = NOT_STARTED

    /** The clock ticks only while this is true. */
    val running: Boolean get() = playing && foreground && !reduced && !previewing

    /** `position`: where the carousel is, in pages. A finger or a spring when [touching], else the timeline. */
    fun position(): Float =
        if (!previewing && touching) manual else NocturneProgramCarouselMotion.timelinePage(time).toFloat()

    private fun hasOwnership(): Boolean = readingTouches > 0 || readingDrag || gestureHeld

    private fun clearOwnership() {
        readingTouches = 0
        readingDrag = false
        gestureHeld = false
    }

    /**
     * The `useEffect`s of `usePlayback` that react to the inputs changing: the preview pose, the app
     * going to the background and back, reduced motion turning on and off, and `autoPlay`.
     */
    fun update(autoPlay: Boolean, previewTime: Double?, reduced: Boolean, foreground: Boolean) {
        val preview = previewTime?.let { previewPhaseOf(it) }
        val previewChanged = preview != previewPhase
        val autoPlayChanged = autoPlay != this.autoPlay
        val foregroundChanged = foreground != this.foreground
        val reducedChanged = reduced != this.reduced
        if (!previewChanged && !autoPlayChanged && !foregroundChanged && !reducedChanged) return
        val wasPreviewing = previewing
        val restoredMotion = this.reduced && !reduced
        this.autoPlay = autoPlay
        this.previewPhase = preview
        this.reduced = reduced
        this.foreground = foreground
        if (preview != null) {
            quietDeadline = NO_DEADLINE
            clockSeconds = preview
        }
        if (foregroundChanged) {
            quietDeadline = NO_DEADLINE
            if (foreground) {
                if (!stopped && !hasOwnership()) playing = autoPlay
            } else {
                clearOwnership()
            }
        }
        if (reducedChanged && reduced && !previewing) {
            quietDeadline = NO_DEADLINE
            clockSeconds = settledTime
        }
        if (autoPlayChanged || previewing != wasPreviewing) {
            quietDeadline = NO_DEADLINE
            if (!stopped && !hasOwnership()) playing = autoPlay
        }
        if (restoredMotion && autoPlay && foreground && !previewing && !stopped && !hasOwnership()) {
            // Resume from the selected phase only after the usual reading quiet period.
            settle(clockSeconds)
        }
    }

    /** `pause`: the clock stops and the pending restart is dropped. */
    fun pause() {
        quietDeadline = NO_DEADLINE
        playing = false
    }

    /**
     * `settle`: freeze at [anchor] and, when the tour plays by itself, start it again from there two
     * seconds later if nothing else owns it by then.
     */
    fun settle(anchor: Double) {
        pause()
        val preview = previewPhase
        if (preview != null) {
            clockSeconds = preview
            return
        }
        settledTime = anchor
        clockSeconds = anchor
        epochNanos = NOT_STARTED
        if (autoPlay && foreground && !reduced && !stopped && !hasOwnership()) {
            quietDeadline = frameNanos + QUIET_MS * NANOS_PER_MS
        }
    }

    /** `stop`: the tour is over for now (the plan opened); nothing restarts the clock until [restart]. */
    fun stop() {
        stopped = true
        clearOwnership()
        pause()
    }

    /** `restart`: the tour is back; settle at [anchor] and let the clock start again after the quiet period. */
    fun restart(anchor: Double) {
        stopped = false
        settle(anchor)
    }

    /** `remember` in the React Native file: the anchor of the page the carousel is on, which a reduced-motion switch settles to. */
    fun rememberAnchor(anchor: Double) {
        settledTime = anchor
    }

    /** `beginGesture`: the pan took the carousel. */
    fun beginGesture() {
        gestureHeld = true
        pause()
    }

    /** `endGesture`: the pan let go. */
    fun endGesture() {
        gestureHeld = false
    }

    /** `onTouchStart`: a finger came down on the scroll area. */
    fun readingTouchStart(touches: Int) {
        readingTouches = maxOf(1, touches)
        pause()
    }

    /** `onTouchEnd` and `onTouchCancel`: a finger left; [touches] are still down. */
    fun readingTouchEnd(touches: Int) {
        readingTouches = touches
        releaseReading()
    }

    /** The scroll area stopped receiving touches without an end event (it was hidden). */
    fun resetReadingTouches() {
        readingTouches = 0
    }

    /** `onScrollBeginDrag` / `onScrollEndDrag` and the momentum pair, as one: a scroll started or stopped. */
    fun readingScroll(active: Boolean) {
        readingDrag = active
        if (active) pause() else releaseReading()
    }

    /** Reading never seeks to a page anchor or emits a landing haptic. */
    private fun releaseReading() {
        if (!stopped && !hasOwnership()) settle(clockSeconds)
    }

    /** The pan's `onStart`: take [manual] from wherever the carousel is and hold the clock. */
    fun panBegin() {
        springActive = false
        manual = position()
        startPage = manual
        touching = true
        beginGesture()
    }

    /** `touching.set`. */
    fun applyTouching(value: Boolean) {
        touching = value
    }

    /** `manual.set(value)`: jump, cancelling a running spring. */
    fun snapManual(value: Float) {
        springActive = false
        manual = value
    }

    /** `manual.set(withSpring(target, { duration: 400, dampingRatio: .8, velocity }))`. */
    fun springManual(target: Float, velocity: Float) {
        springFrom = manual.toDouble()
        springTo = target.toDouble()
        springVelocity = velocity.toDouble()
        springStart = NOT_STARTED
        springActive = true
    }

    /** `cancelAnimation(manual)`. */
    fun cancelManual() {
        springActive = false
    }

    /** Moves everything to the frame at [nanos]; called once per frame. */
    fun advance(nanos: Long) {
        frameNanos = nanos
        if (quietDeadline != NO_DEADLINE && nanos >= quietDeadline) {
            quietDeadline = NO_DEADLINE
            if (!stopped && !hasOwnership() && foreground) playing = true
        }
        advanceSpring(nanos)
        if (running) {
            // The timeline took the carousel back (`useEffect` on `playing`).
            if (touching) touching = false
            if (epochNanos == NOT_STARTED) epochNanos = nanos - (clockSeconds * NANOS_PER_SECOND).toLong()
            clockSeconds = ((nanos - epochNanos) / NANOS_PER_SECOND) % NOCTURNE_PROGRAM_CAROUSEL_LOOP
        } else {
            epochNanos = NOT_STARTED
        }
    }

    /**
     * A damped spring with mass 1, stiffness (2 pi / 0.4 s) squared and damping ratio 0.8, from the
     * value and velocity at the time it was asked for, in closed form.
     */
    private fun advanceSpring(nanos: Long) {
        if (!springActive) return
        if (springStart == NOT_STARTED) springStart = nanos
        val seconds = (nanos - springStart) / NANOS_PER_SECOND
        val omega = sqrt(SPRING_STIFFNESS)
        val damped = omega * sqrt(1.0 - SPRING_DAMPING_RATIO * SPRING_DAMPING_RATIO)
        val decayRate = SPRING_DAMPING_RATIO * omega
        val offset = springFrom - springTo
        val sineWeight = (springVelocity + decayRate * offset) / damped
        val decay = exp(-decayRate * seconds)
        val cosine = cos(damped * seconds)
        val sine = sin(damped * seconds)
        val displacement = decay * (offset * cosine + sineWeight * sine)
        val speed = decay * ((sineWeight * damped - decayRate * offset) * cosine - (offset * damped + decayRate * sineWeight) * sine)
        if (seconds > SPRING_MAX_SECONDS || (seconds > 0.1 && abs(displacement) < 0.0005 && abs(speed) < 0.01)) {
            manual = springTo.toFloat()
            springActive = false
        } else {
            manual = (springTo + displacement).toFloat()
        }
    }

    private companion object {
        const val REST_TIME = 4.3
        const val QUIET_MS = 2000L
        const val NANOS_PER_MS = 1_000_000L
        const val NANOS_PER_SECOND = 1e9
        const val NOT_STARTED = Long.MIN_VALUE
        const val NO_DEADLINE = Long.MIN_VALUE + 1
        const val SPRING_DAMPING_RATIO = 0.8
        const val SPRING_MAX_SECONDS = 2.0
        /** Reanimated 4's `{ duration: 400, dampingRatio: 0.8 }`: stiffness solved so the spring settles after 1.5 x 400 ms (mass 1). */
        const val SPRING_STIFFNESS = 653.4
    }
}

/** `previewPhase`: [value] folded into 0 until the loop's length; a value that is not a number is 0. */
private fun previewPhaseOf(value: Double): Double {
    if (!value.isFinite()) return 0.0
    val phase = value % NOCTURNE_PROGRAM_CAROUSEL_LOOP
    return if (phase < 0) phase + NOCTURNE_PROGRAM_CAROUSEL_LOOP else phase
}

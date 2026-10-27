package com.ngocdevv.nativecook.blocks.splash.daylighticonreveal

import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.setValue

/**
 * The block's one clock, `clock` in the React Native file. Every frame of the screen is a function
 * of [time], in seconds of loop time, so a frozen [time] is a frozen frame.
 *
 * It runs one of three motions: standing still, the endless loop (`withRepeat(withTiming(LOOP))`,
 * linear), or a single timing to a target value. A touch freezes it where it is; two seconds after
 * the last release it settles to the finished screen in 240 ms and, 650 ms after that began,
 * starts the loop again. The composable keeps the flags below up to date and calls [onEffect]
 * and [onEffectCleanup] where the React Native file has its `useEffect`.
 */
internal class DaylightIconRevealClock(initialTime: Double) {
    private var timeState by mutableDoubleStateOf(initialTime)
    private var motion: Motion = Motion.Still
    private var frameNanos = 0L
    private var settleAtNanos = NO_DEADLINE
    private var playAtNanos = NO_DEADLINE
    private var wasLanding = false

    /** Loop time in seconds. Reading it inside a draw or `graphicsLayer` block redraws only that block. */
    val time: Double get() = timeState

    var autoPlay = false
    var reduced = false
    var foreground = true
    var began = false
    var previewTime: Double? = null

    /** True while a finger is on the button. */
    var holding = false
        private set

    /** The soft haptic of the icon landing has fired for this run (`landed` ref). */
    var landed = false

    /** Called once when the icon lands in a run that is not `autoPlay` (`tapLanding`). */
    var onLanding: () -> Unit = {}

    private sealed interface Motion {
        data object Still : Motion

        /** The endless loop; [startNanos] is taken from the first frame after it was requested. */
        class Loop(var startNanos: Long) : Motion

        class Timing(
            val from: Double,
            val to: Double,
            val durationNanos: Long,
            val easing: Easing,
            var startNanos: Long,
        ) : Motion
    }

    /** `clock.set(value)`: jumps to [value] and stands still. */
    fun set(value: Double) {
        motion = Motion.Still
        timeState = value
    }

    /** `cancelAnimation(clock)`: stops where it is. */
    fun cancel() {
        motion = Motion.Still
    }

    /** `clock.set(withTiming(to, { duration, easing }))` from the current value. */
    fun timeTo(to: Double, durationMs: Int, easing: Easing) {
        motion = Motion.Timing(timeState, to, durationMs * NANOS_PER_MS, easing, NOT_STARTED)
    }

    /** `play`: restarts the endless loop from 0 when the block is allowed to play by itself. */
    fun play() {
        if (!autoPlay || reduced || !foreground || holding || began || previewTime != null) return
        set(0.0)
        motion = Motion.Loop(NOT_STARTED)
    }

    /** The body of the React Native `useEffect` that starts, replays or freezes the clock. */
    fun onEffect(replayEpoch: Int) {
        val preview = previewTime
        if (preview != null) {
            cancel()
            set(previewPhase(preview))
        } else if (!autoPlay && replayEpoch > 0 && !began && !reduced && foreground) {
            set(0.0)
            timeTo(4.0, REPLAY_MS, LinearEasing)
        } else {
            play()
        }
    }

    /** The cleanup of that effect: stop the motion and drop the pending timers. */
    fun onEffectCleanup() {
        cancel()
        clearTimers()
    }

    fun clearTimers() {
        settleAtNanos = NO_DEADLINE
        playAtNanos = NO_DEADLINE
    }

    /** `begin`: a finger came down on the button, so the screen holds still. */
    fun press() {
        holding = true
        clearTimers()
        cancel()
    }

    /** `end`: the finger left. Two seconds later the screen settles and the loop starts again. */
    fun release() {
        holding = false
        clearTimers()
        if (previewTime != null) return
        settleAtNanos = frameNanos + IDLE_BEFORE_SETTLE_MS * NANOS_PER_MS
    }

    private fun settleAfterRest() {
        if (began || !autoPlay || reduced || holding) return
        timeTo(4.0, SETTLE_MS, DaylightIconRevealTimeline.Ease)
        playAtNanos = frameNanos + RESTART_AFTER_MS * NANOS_PER_MS
    }

    /** Moves the clock to the frame at [nanos]; called once per frame. */
    fun advance(nanos: Long) {
        frameNanos = nanos
        if (settleAtNanos != NO_DEADLINE && nanos >= settleAtNanos) {
            settleAtNanos = NO_DEADLINE
            settleAfterRest()
        }
        if (playAtNanos != NO_DEADLINE && nanos >= playAtNanos) {
            playAtNanos = NO_DEADLINE
            play()
        }
        when (val current = motion) {
            Motion.Still -> Unit
            is Motion.Loop -> {
                if (current.startNanos == NOT_STARTED) current.startNanos = nanos
                val elapsed = (nanos - current.startNanos) / NANOS_PER_SECOND
                timeState = elapsed % DAYLIGHT_ICON_REVEAL_LOOP
            }
            is Motion.Timing -> {
                if (current.startNanos == NOT_STARTED) current.startNanos = nanos
                val progress = ((nanos - current.startNanos).toDouble() / current.durationNanos).coerceIn(0.0, 1.0)
                val eased = current.easing.transform(progress.toFloat()).toDouble()
                timeState = current.from + (current.to - current.from) * eased
                if (progress >= 1.0) {
                    timeState = current.to
                    motion = Motion.Still
                }
            }
        }
        reportLanding()
    }

    /** `useAnimatedReaction` on 2.45 s to 2.7 s: the soft haptic when the icon touches down. */
    private fun reportLanding() {
        val landing = timeState >= LANDING_FROM && timeState < LANDING_TO
        if (landing && !wasLanding && !autoPlay && previewTime == null) onLanding()
        wasLanding = landing
    }

    companion object {
        private const val NANOS_PER_MS = 1_000_000L
        private const val NANOS_PER_SECOND = 1e9
        private const val NOT_STARTED = Long.MIN_VALUE
        private const val NO_DEADLINE = Long.MIN_VALUE + 1
        private const val REPLAY_MS = 4000
        private const val SETTLE_MS = 240
        private const val IDLE_BEFORE_SETTLE_MS = 2000L
        private const val RESTART_AFTER_MS = 650L
        private const val LANDING_FROM = 2.45
        private const val LANDING_TO = 2.7

        /** `previewPhase`: [value] folded into 0 until the loop's length. */
        fun previewPhase(value: Double): Double {
            if (!value.isFinite()) return 0.0
            val phase = value % DAYLIGHT_ICON_REVEAL_LOOP
            return if (phase < 0) phase + DAYLIGHT_ICON_REVEAL_LOOP else phase
        }
    }
}

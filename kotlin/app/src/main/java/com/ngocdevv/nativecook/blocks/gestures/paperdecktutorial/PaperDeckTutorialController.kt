package com.ngocdevv.nativecook.blocks.gestures.paperdecktutorial

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sign
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * The shared values of `PaperDeckTutorial` (`clock`, `manual`, `manualStep`, `x`, `grab`, `holding`) and
 * the functions that move them. Every frame of the screen is a function of these (see [pose]), so a
 * frozen clock is a frozen frame.
 *
 * Nothing here runs by itself: the composable calls [advance] once per frame, which moves the clock
 * and the spring of `x` and fires the two-second timer. The values drawing reads are Compose state,
 * so a frame redraws only the draw blocks that read them. The inputs the composable keeps current
 * after every composition ([autoPlay], [reduced], ...) are plain fields, read when a function runs.
 *
 * The clock runs one of three motions: still, the endless loop (`withRepeat(withTiming(LOOP))`,
 * linear), or one timing to the end of the loop after a touch. `x` is still or a spring.
 */
internal class PaperDeckTutorialController(initialClock: Double, initialManual: Boolean) {
    private var clockState by mutableDoubleStateOf(initialClock)
    private var manualState by mutableStateOf(initialManual)
    private var manualStepState by mutableIntStateOf(0)
    private var xState by mutableDoubleStateOf(0.0)
    private var grabState by mutableDoubleStateOf(1.0)

    private var clockMotion: ClockMotion = ClockMotion.Still
    private var xMotion: SpringMotion? = null
    private var frameNanos = 0L
    private var resumeAtNanos = NO_DEADLINE
    private var holding = false
    private var origin = 0.0

    // The inputs, kept current after every composition.
    var autoPlay = false
    var reduced = false
    var foreground = true
    var previewTime: Double? = null
    var screenWidth = 0.0
    var cardWidth = 0.0
    var cardHeight = 0.0
    var onComplete: (() -> Unit)? = null
    var onHaptic: () -> Unit = {}

    /** `grab`: 1 when the sheet is held in its upper part, -0.7 when held low (it pivots the other way). */
    val grab: Double get() = grabState

    private sealed interface ClockMotion {
        data object Still : ClockMotion

        /** The endless loop; [startNanos] is taken from the first frame after it was requested. */
        class Repeat(var startNanos: Long) : ClockMotion

        class Timing(
            val from: Double,
            val to: Double,
            val durationNanos: Long,
            var startNanos: Long,
            val onDone: () -> Unit,
        ) : ClockMotion
    }

    /**
     * `withSpring(to, { duration: 400, dampingRatio: 0.8, velocity })` from the value [from]: Reanimated
     * reads `duration` as perceptual and solves the stiffness so the spring settles after 1.5 times it,
     * which for 400 ms and a ratio of 0.8 is 653.4 (mass 1). Evaluated in closed form, so it does not
     * depend on the frame rate. It rests when the remaining swing falls under 0.01 dp.
     */
    private class SpringMotion(
        val from: Double,
        val to: Double,
        velocity: Double,
        var startNanos: Long,
        val onFinished: () -> Unit,
    ) {
        private val offset = from - to
        private val sine = (velocity + DAMPING_RATIO * OMEGA * offset) / DAMPED_OMEGA
        val restSeconds: Double

        init {
            val amplitude = hypot(offset, sine)
            restSeconds = if (amplitude <= REST_DISPLACEMENT) 0.0 else ln(amplitude / REST_DISPLACEMENT) / (DAMPING_RATIO * OMEGA)
        }

        fun valueAt(seconds: Double): Double {
            val decay = exp(-DAMPING_RATIO * OMEGA * seconds)
            return to + decay * (offset * cos(DAMPED_OMEGA * seconds) + sine * sin(DAMPED_OMEGA * seconds))
        }
    }

    /** `pose`: the deck's step, the top card's offset, the cover's flip and the time of the script. */
    fun pose(): PaperDeckTutorialPose {
        if (manualState) {
            val step = manualStepState
            return PaperDeckTutorialPose(step, if (reduced) 0.0 else xState, 1.0, if (step == 6) 15.6 else 2.0)
        }
        return PaperDeckTutorialScript.autoPose(clockState, screenWidth)
    }

    private fun setClock(value: Double) {
        clockMotion = ClockMotion.Still
        clockState = value
    }

    private fun cancelClock() {
        clockMotion = ClockMotion.Still
    }

    private fun setX(value: Double) {
        xMotion = null
        xState = value
    }

    private fun cancelX() {
        xMotion = null
    }

    private fun springX(to: Double, velocity: Double, onFinished: () -> Unit) {
        xMotion = SpringMotion(xState, to, velocity, NOT_STARTED, onFinished)
    }

    /** The body of the React Native `useEffect` that settles Reduce Motion: stop where it is, as a still card. */
    fun onReducedEffect() {
        if (!reduced) return
        cancelClock()
        cancelX()
        manualStepState = pose().step
        manualState = true
        setX(0.0)
    }

    /** `play`: freezes the clock for a preview, or restarts the endless loop when the block may play by itself. */
    fun play() {
        val preview = previewTime
        if (preview != null) {
            cancelClock()
            setClock(PaperDeckTutorialScript.previewPhase(preview))
            manualState = reduced
            return
        }
        if (!autoPlay || reduced || !foreground || manualStepState >= 6) return
        manualState = false
        setClock(0.0)
        clockMotion = ClockMotion.Repeat(NOT_STARTED)
    }

    /** The cleanup of the effect that calls [play]: stop both motions and drop the timer. */
    fun stopMotion() {
        cancelClock()
        cancelX()
        resumeAtNanos = NO_DEADLINE
    }

    private fun stopTimer() {
        resumeAtNanos = NO_DEADLINE
    }

    /** `buttonBegin`: a finger came down, so the screen holds where it is as a manual deck. */
    fun buttonBegin() {
        holding = true
        stopTimer()
        val current = pose()
        manualStepState = current.step
        xState = current.x
        manualState = true
        cancelClock()
        cancelX()
    }

    /** `buttonEnd`: the finger left; two seconds later the loop takes over again. */
    fun buttonEnd() {
        holding = false
        queueResume()
    }

    /** `queueResume`: arms the two-second timer that eases the deck back to rest and restarts the loop. */
    private fun queueResume() {
        if (!autoPlay || reduced || !foreground || previewTime != null) return
        resumeAtNanos = frameNanos + RESUME_AFTER_NANOS
    }

    /** The body of the timer: spring `x` home, then run the clock from the step's anchor to the loop's end. */
    private fun resumeAfterIdle() {
        if (holding || manualStepState >= 6) return
        springX(0.0, 0.0) {
            if (holding) return@springX
            val anchor = PaperDeckTutorialScript.ANCHORS[manualStepState]
            grabState = 1.0
            setClock(anchor)
            manualState = false
            val remaining = PAPER_DECK_TUTORIAL_LOOP - anchor
            clockMotion = ClockMotion.Timing(anchor, PAPER_DECK_TUTORIAL_LOOP, (remaining * NANOS_PER_SECOND).toLong(), NOT_STARTED) { play() }
        }
    }

    /** The pan's `onBegin`: [yInCard] is where the finger came down inside the card, in dp. */
    fun panBegin(yInCard: Double) {
        grabState = if (yInCard > cardHeight * 0.62) -0.7 else 1.0
        val current = pose()
        holding = true
        manualStepState = current.step
        xState = current.x
        manualState = true
        cancelClock()
        cancelX()
        origin = xState
        stopTimer()
    }

    /** The pan's `onUpdate`: [translationX] is the finger's travel since the pan began, in dp. */
    fun panUpdate(translationX: Double) {
        if (manualStepState >= 6 || reduced) return
        val next = origin + translationX
        val limit = screenWidth * 0.75
        xState = if (abs(next) > limit) sign(next) * (limit + (abs(next) - limit) * 0.45) else next
    }

    /** The pan's `onEnd`: [velocityX] in dp per second; [success] is false when the system cancelled the pan. */
    fun panEnd(velocityX: Double, success: Boolean) {
        if (!success) {
            if (reduced) setX(0.0) else springX(0.0, 0.0) {}
            return
        }
        if (manualStepState >= 6) return
        val flick = abs(velocityX) > FLICK_VELOCITY
        val commit = abs(xState) > cardWidth * 0.26 || flick
        if (!commit) {
            springX(0.0, velocityX) {}
            return
        }
        onHaptic()
        val direction = if (flick) sign(velocityX) else sign(xState)
        if (reduced) {
            val next = min(6, manualStepState + 1)
            manualStepState = next
            setX(0.0)
            if (next == 6) complete()
            return
        }
        springX(direction * screenWidth * 1.4, velocityX) {
            val next = min(6, manualStepState + 1)
            manualStepState = next
            setX(0.0)
            if (next == 6) complete()
        }
    }

    /** The pan's `onFinalize`: the finger is up (or the pan never began), so the timer starts. */
    fun panFinalize() {
        holding = false
        queueResume()
    }

    /** The gear: back to the first card. */
    fun restart() {
        manualStepState = 0
        setX(0.0)
    }

    /** The close disc: the tutorial is finished. */
    fun finish() {
        manualStepState = 6
        setX(0.0)
        complete()
    }

    private fun complete() {
        onComplete?.invoke()
    }

    /** The accessibility actions: hold the screen, move one card, let go (`onAccessibilityAction`). */
    fun accessibilityAdvance(back: Boolean) {
        buttonBegin()
        advance(back)
        buttonEnd()
    }

    /** `advance`: one card forward (thrown the way it asks) or back (at once). */
    private fun advance(back: Boolean) {
        val current = manualStepState
        if (back || current >= 6 || reduced) {
            manualStepState = if (back) max(0, current - 1) else if (current >= 6) 0 else current + 1
            setX(0.0)
            if (!back && current == 5) complete()
            return
        }
        onHaptic()
        springX(PaperDeckTutorialScript.DIRECTIONS[current] * screenWidth * 1.4, 0.0) {
            manualStepState = current + 1
            setX(0.0)
            if (current == 5) complete()
        }
    }

    /** Moves everything to the frame at [nanos]; called once per frame. */
    fun advance(nanos: Long) {
        frameNanos = nanos
        if (resumeAtNanos != NO_DEADLINE && nanos >= resumeAtNanos) {
            resumeAtNanos = NO_DEADLINE
            resumeAfterIdle()
        }
        advanceX(nanos)
        advanceClock(nanos)
    }

    private fun advanceX(nanos: Long) {
        val motion = xMotion ?: return
        if (motion.startNanos == NOT_STARTED) motion.startNanos = nanos
        val seconds = (nanos - motion.startNanos) / NANOS_PER_SECOND
        if (seconds >= motion.restSeconds) {
            xMotion = null
            xState = motion.to
            motion.onFinished()
        } else {
            xState = motion.valueAt(seconds)
        }
    }

    private fun advanceClock(nanos: Long) {
        when (val motion = clockMotion) {
            ClockMotion.Still -> Unit
            is ClockMotion.Repeat -> {
                if (motion.startNanos == NOT_STARTED) motion.startNanos = nanos
                clockState = ((nanos - motion.startNanos) / NANOS_PER_SECOND) % PAPER_DECK_TUTORIAL_LOOP
            }
            is ClockMotion.Timing -> {
                if (motion.startNanos == NOT_STARTED) motion.startNanos = nanos
                val progress = ((nanos - motion.startNanos).toDouble() / motion.durationNanos).coerceIn(0.0, 1.0)
                clockState = motion.from + (motion.to - motion.from) * progress
                if (progress >= 1.0) {
                    clockMotion = ClockMotion.Still
                    motion.onDone()
                }
            }
        }
    }

    private companion object {
        const val NOT_STARTED = Long.MIN_VALUE
        const val NO_DEADLINE = Long.MIN_VALUE + 1
        const val NANOS_PER_SECOND = 1e9
        const val RESUME_AFTER_NANOS = 2_000_000_000L
        const val FLICK_VELOCITY = 650.0

        /** `dampingRatio: 0.8` and the stiffness 653.4 Reanimated solves for `duration: 400` (see [SpringMotion]). */
        const val DAMPING_RATIO = 0.8
        const val STIFFNESS = 653.4
        val OMEGA = sqrt(STIFFNESS)
        val DAMPED_OMEGA = OMEGA * sqrt(1 - DAMPING_RATIO * DAMPING_RATIO)
        const val REST_DISPLACEMENT = 0.01
    }
}

package com.ngocdevv.nativecook.blocks.lists.donationfancarousel

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * The React Native block's shared values (`clock`, `page`, `playing`, `loopStart`, `gestureStart`,
 * `resumeAt`) with the frame callback and the pan and tap handlers that move them. [clock] and
 * [page] are snapshot state that the draw lambdas read; everything else is only touched on the
 * main thread, from [tick] and the touch handlers.
 *
 * Times are milliseconds on the frame clock (`System.nanoTime()`).
 */
@Stable
internal class DonationFanCarouselState {
    /** Loop time in ms, 0...LOOP_MS. */
    var clock by mutableDoubleStateOf(0.0)
        private set

    /** Fan position, 0 (first donor) to 3 (fourth). */
    var page by mutableDoubleStateOf(0.0)
        private set

    /** True only while the loop, a spring, or an autoplay-resume deadline needs frame ticks. */
    var frameDriverActive by mutableStateOf(false)
        private set

    private var autoPlay = false
    private var playing = false
    private var loopStartMs = -1.0
    private var resumeAtMs = 0.0
    private var gestureStart = 0.0
    private var spring: DonationFanCarouselSpring? = null
    private var touching = false
    private var frozen = false

    /** The `useEffect` of the React Native block: freeze, start the loop, or rest at the first donor. */
    fun configure(autoPlay: Boolean, reducedMotion: Boolean, freezeAt: Double?) {
        frozen = freezeAt != null
        this.autoPlay = autoPlay && !reducedMotion && freezeAt == null
        spring = null
        resumeAtMs = 0.0
        touching = false
        if (freezeAt != null) {
            clock = freezeAt
            page = DonationFanCarouselMotion.pageAt(freezeAt)
            playing = false
            updateFrameDriver()
            return
        }
        if (autoPlay && !reducedMotion) {
            loopStartMs = -1.0
            playing = true
        } else {
            playing = false
            clock = 0.0
            page = 0.0
        }
        updateFrameDriver()
    }

    /**
     * The frame callback and the release spring. The idle timer cannot take over a live contact.
     */
    fun tick(nowMs: Double) {
        val running = spring
        if (running != null) {
            page = running.valueAt(nowMs)
            if (running.finished) spring = null
        }
        if (!playing) {
            if (autoPlay && !touching && resumeAtMs > 0 && nowMs >= resumeAtMs) {
                clock = 0.0
                page = 0.0
                spring = null
                loopStartMs = -1.0
                resumeAtMs = 0.0
                playing = true
            }
            updateFrameDriver()
            return
        }
        if (loopStartMs < 0) loopStartMs = nowMs
        val time = (nowMs - loopStartMs) % DonationFanCarouselMotion.LoopMs
        clock = time
        page = DonationFanCarouselMotion.pageAt(time)
        updateFrameDriver()
    }

    /**
     * Contact begins: cancel the spring, pause the loop and retain its current page.
     */
    fun touchBegan() {
        if (frozen) return
        spring = null
        playing = false
        gestureStart = page
        resumeAtMs = 0.0
        touching = true
        updateFrameDriver()
    }

    /** The pan's `onUpdate`, with the translation in dp since the touch went down. */
    fun drag(translationDp: Double, scale: Double) {
        var value = gestureStart - translationDp / (DonationFanCarouselMotion.DragPointsPerPage * scale)
        if (value < 0) value *= RubberBand
        if (value > 3) value = 3 + (value - 3) * RubberBand
        page = value
        clock = if (value < DragClockSplit) 0.0 else DragClockLate
    }

    /** The pan's `onEnd`, with the release velocity in dp per second. */
    fun release(velocityDp: Double, scale: Double, nowMs: Double) {
        if (!touching) return
        val velocity = -velocityDp / (DonationFanCarouselMotion.DragPointsPerPage * scale)
        val projected = DonationFanCarouselMath.jsRound(page + velocity * DonationFanCarouselMotion.ProjectSeconds)
        val target = maxOf(0.0, minOf(3.0, projected))
        spring = DonationFanCarouselSpring(
            from = page,
            to = target,
            requestedVelocity = velocity,
            durationMs = DonationFanCarouselMotion.SnapDurationMs,
            dampingRatio = DonationFanCarouselMotion.SnapDampingRatio,
            startedAtMs = nowMs,
        )
        updateFrameDriver()
    }

    /** A cancelled drag settles to the nearest donor without carrying release velocity. */
    fun cancelTouch(nowMs: Double) {
        if (!touching || frozen) return
        val target = maxOf(0.0, minOf(3.0, DonationFanCarouselMath.jsRound(page)))
        clock = if (target < DragClockSplit) 0.0 else DragClockLate
        spring = DonationFanCarouselSpring(
            from = page,
            to = target,
            requestedVelocity = 0.0,
            durationMs = DonationFanCarouselMotion.SnapDurationMs,
            dampingRatio = DonationFanCarouselMotion.SnapDampingRatio,
            startedAtMs = nowMs,
        )
        updateFrameDriver()
    }

    /** Release or cancellation starts the idle timer, including a stationary press. */
    fun touchFinalized(nowMs: Double) {
        if (!touching) return
        touching = false
        if (autoPlay) resumeAtMs = nowMs + DonationFanCarouselMotion.ResumeMs
        updateFrameDriver()
    }

    /** TalkBack adjustment follows the same snap and idle-resume policy as touch. */
    fun moveFromAccessibility(targetIndex: Int, reducedMotion: Boolean, nowMs: Double): Boolean {
        if (frozen) return false
        val current = accessibilityIndex()
        val target = targetIndex.coerceIn(0, 3)
        if (target == current) return false

        playing = false
        touching = false
        resumeAtMs = 0.0
        clock = if (target < DragClockSplit) 0.0 else DragClockLate
        if (reducedMotion) {
            spring = null
            page = target.toDouble()
        } else {
            spring = DonationFanCarouselSpring(
                from = page,
                to = target.toDouble(),
                requestedVelocity = 0.0,
                durationMs = DonationFanCarouselMotion.SnapDurationMs,
                dampingRatio = DonationFanCarouselMotion.SnapDampingRatio,
                startedAtMs = nowMs,
            )
            if (autoPlay) resumeAtMs = nowMs + DonationFanCarouselMotion.ResumeMs
        }
        updateFrameDriver()
        return true
    }

    fun accessibilityIndex(): Int {
        return DonationFanCarouselMath.jsRound(page).toInt().coerceIn(0, 3)
    }

    private fun updateFrameDriver() {
        frameDriverActive = !frozen && (playing || spring != null || (autoPlay && resumeAtMs > 0))
    }

    private companion object {
        /** `value *= 0.28` and `3 + (value - 3) * 0.28` in the pan's `onUpdate`. */
        const val RubberBand = 0.28

        /** `value < 1.5 ? 0 : 1800` in the pan's `onUpdate`. */
        const val DragClockSplit = 1.5
        const val DragClockLate = 1800.0
    }
}

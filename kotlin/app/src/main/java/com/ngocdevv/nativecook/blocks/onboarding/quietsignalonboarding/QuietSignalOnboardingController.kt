package com.ngocdevv.nativecook.blocks.onboarding.quietsignalonboarding

import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * The state of the screen that is not the clock: which page is showing, the finger-driven page
 * position (`manual`), the fade a button press puts over a page change (`scenePresence`) and the
 * session preview. It is the body of `QuietSignalOnboarding` in the React Native file:
 * `go`, the pan's start, update and end, `continueTour` and the reaction that announces the page.
 *
 * Fast-changing values are Compose state that is read only inside `graphicsLayer` and draw blocks,
 * so a frame redraws a layer and never recomposes the screen.
 */
internal class QuietSignalOnboardingController(
    private val scope: CoroutineScope,
    val playback: QuietSignalOnboardingPlayback,
    timelineDriven: Boolean,
) {
    private val timeline = QuietSignalOnboardingTimeline

    /** The page the screen announces (`page`): the nearest page to [position]. */
    var page by mutableIntStateOf(initialPage(playback.previewPhase))
        private set

    /** `session`: the silent session preview has replaced the tour. */
    var session by mutableStateOf(false)
        private set

    /** `sessionMode`: the direction picked in the session preview. */
    var sessionMode by mutableStateOf(DEFAULT_MODE)
        private set

    /** `manual`: the page position while a finger or a button owns it, 0 to 4 (a spring settles it). */
    var manual by mutableFloatStateOf(0f)
        private set

    /** `manualMode`: pages follow [manual] instead of the loop. */
    var manualMode by mutableStateOf(!timelineDriven)

    /** `scenePresence`: the opacity of the whole page area, dipped by a button's page change. */
    var presence by mutableFloatStateOf(1f)
        private set

    /** The system haptics, kept up to date by the composable. */
    var haptic: HapticFeedback? = null

    /** `onPageChange`, kept up to date by the composable. */
    var onPageChange: ((Int) -> Unit)? = null

    /** `onBeginSession`, kept up to date by the composable. */
    var onBeginSession: (() -> Unit)? = null

    private var panning = false
    private var startPage = 0f
    private var manualJob: Job? = null
    private var presenceJob: Job? = null

    /** `renderManual`: a preview always follows the loop. */
    val renderManual: Boolean get() = !playback.previewing && manualMode

    /** `position`: the page position the pages are drawn at: [manual], or the loop's page. */
    fun position(): Double =
        if (renderManual) manual.toDouble() else timeline.pageAt(playback.displayTime).toDouble()

    /** The opacity of page [index] (`sceneStyle`). */
    fun sceneOpacity(index: Int): Double {
        if (renderManual || playback.reduced) return timeline.clamp(1 - abs(position() - index) * 2)
        return timeline.sceneOpacity(index, playback.displayTime)
    }

    /** Whether page [index] rebuilds its drawing this frame (`live`). */
    fun isLive(index: Int): Boolean {
        if (renderManual || playback.reduced) return abs(position() - index) < 0.75
        return timeline.isLive(index, playback.displayTime)
    }

    /** How much of page [index]'s copy has surfaced out of its soft focus (`copyStyle`). */
    fun copyShown(index: Int): Double {
        if (renderManual || playback.reduced) return 1.0
        return timeline.copyShown(index, playback.displayTime)
    }

    /** The reaction on the rounded [position]: the page changed on its own or under a finger. */
    fun announce(next: Int) {
        page = next
        playback.rememberAnchor(timeline.ANCHORS[next])
        onPageChange?.invoke(next)
    }

    /** `go`: the Continue button and the accessibility actions change the page with a short dip. */
    fun go(next: Int) {
        if (playback.previewing) return
        val target = next.coerceIn(0, timeline.LAST_PAGE)
        playback.pause()
        setManual(position().toFloat())
        manualMode = true
        presenceJob?.cancel()
        if (playback.reduced) {
            setManual(target.toFloat())
            presence = 1f
        } else {
            presenceJob = scope.launch {
                animate(presence, 0f, animationSpec = tween(PRESENCE_OUT_MS, easing = timeline.EnterEasing)) { value, _ ->
                    presence = value
                }
                setManual(target.toFloat())
                animate(0f, 1f, animationSpec = tween(PRESENCE_IN_MS, easing = timeline.EnterEasing)) { value, _ ->
                    presence = value
                }
            }
        }
        landed(target)
    }

    /** `landed`: the screen settles on page [next]'s anchor with a selection tick. */
    fun landed(next: Int) {
        playback.settle(timeline.ANCHORS[next])
        haptic?.performHapticFeedback(HapticFeedbackType.SegmentTick)
    }

    /** `continueTour`: the next page, or from the last page the session preview. */
    fun continueTour() {
        if (playback.previewing) return
        if (page < timeline.LAST_PAGE) {
            go(page + 1)
            return
        }
        playback.stop()
        session = true
        onBeginSession?.invoke()
        haptic?.performHapticFeedback(HapticFeedbackType.Confirm)
    }

    /** "Back to the introduction": page 5 again, settled, and the loop restarts after the quiet period. */
    fun backToIntroduction() {
        session = false
        setManual(timeline.LAST_PAGE.toFloat())
        manualMode = true
        playback.restart(timeline.ANCHORS[timeline.LAST_PAGE])
    }

    /** A session direction was picked. */
    fun selectMode(mode: String) {
        sessionMode = mode
        haptic?.performHapticFeedback(HapticFeedbackType.SegmentTick)
    }

    /** The pan's `onStart`: the finger takes the pages from whatever drives them. */
    fun panStart() {
        panning = true
        presenceJob?.cancel()
        presence = 1f
        setManual(position().toFloat())
        startPage = manual
        manualMode = true
        playback.beginGesture()
    }

    /** The pan's `onUpdate`: the pages follow the finger, with resistance past either end. */
    fun panUpdate(translationX: Double, width: Double) {
        if (playback.reduced) return
        setManual(timeline.rubber(startPage - translationX / width, timeline.LAST_PAGE.toDouble()).toFloat())
    }

    /**
     * The pan's `onFinalize`: a drag of more than 18% of the width, or a flick faster than 450 dp/s,
     * turns the page; a spring (400 ms, damping ratio 0.8, carrying the finger's speed) settles it.
     */
    fun panEnd(translationX: Double, velocityX: Double, width: Double, success: Boolean) {
        if (!panning) return
        panning = false
        playback.endGesture()
        val from = timeline.round(startPage.toDouble())
        val flicked = abs(velocityX) > FLICK_SPEED
        val moved = success && (abs(translationX) > width * 0.18 || flicked)
        val direction = if (!moved) 0 else if ((if (flicked) velocityX else translationX) < 0) 1 else -1
        val target = timeline.clamp(from + direction, 0.0, timeline.LAST_PAGE.toDouble()).toInt()
        if (playback.reduced) {
            setManual(target.toFloat())
        } else {
            springManual(target.toFloat(), (-velocityX / width).toFloat())
        }
        if (moved && target != from.toInt()) landed(target) else playback.settle(timeline.ANCHORS[target])
    }

    /** Sets [manual] and stops any spring that was moving it. */
    private fun setManual(value: Float) {
        manualJob?.cancel()
        manualJob = null
        manual = value
    }

    /** `withSpring(target, { duration: 400, dampingRatio: .8, velocity })`. */
    private fun springManual(target: Float, velocity: Float) {
        manualJob?.cancel()
        manualJob = scope.launch {
            animate(
                initialValue = manual,
                targetValue = target,
                initialVelocity = velocity,
                animationSpec = spring<Float>(dampingRatio = SPRING_DAMPING, stiffness = SPRING_STIFFNESS),
            ) { value, _ ->
                manual = value
            }
        }
    }

    private companion object {
        const val DEFAULT_MODE = "A quiet break"
        const val PRESENCE_OUT_MS = 100
        const val PRESENCE_IN_MS = 180
        const val FLICK_SPEED = 450.0
        const val SPRING_DAMPING = 0.8f

        /**
         * Reanimated 4.5.1 reads `duration: 400` as perceptual: it solves the stiffness so the spring
         * settles after 1.5 x the duration (`calculateNewStiffnessToMatchDuration`, mass 1) with
         * damping ratio 0.8, which is 653.4. Releasing with a speed would lower it a little; a fixed
         * value is used.
         */
        const val SPRING_STIFFNESS = 653.4f

        fun initialPage(previewPhase: Double?): Int {
            if (previewPhase == null) return 0
            return QuietSignalOnboardingTimeline.round(QuietSignalOnboardingTimeline.pageAt(previewPhase).toDouble()).toInt()
        }
    }
}

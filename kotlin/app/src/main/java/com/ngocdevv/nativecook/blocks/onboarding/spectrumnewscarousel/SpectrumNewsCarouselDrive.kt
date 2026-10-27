package com.ngocdevv.nativecook.blocks.onboarding.spectrumnewscarousel

import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.runtime.State
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * What the pages are doing: the position of the carousel (`position`), the window the welcome grows
 * from, and the page changes the finger and the buttons make (`go`, the pan, `landed`).
 *
 * The position comes from the loop (`timelinePage` of the clock's time) until a touch or a button
 * takes over (`manualMode`); then it is [manual], moved by the finger and settled by a spring
 * (400 ms, damping ratio 0.8) that starts with the finger's speed. Once the loop plays again it
 * hands back to the timeline, which at the page's anchor shows the same page.
 *
 * Everything the draw and layer blocks read is snapshot state, so reading [position] or [time]
 * there redraws only that block and the screen is not recomposed every frame.
 */
@Stable
internal class SpectrumNewsCarouselDrive(
    val playback: SpectrumNewsCarouselPlayback,
    private val scope: CoroutineScope,
    private val autoPlayState: State<Boolean>,
    initialManualMode: Boolean,
    private val selectionHaptic: () -> Unit,
) {
    /** The window's width in dp (`width`), kept current by the composable. */
    var width: Double = 0.0

    private val manual = mutableFloatStateOf(0f)

    /** `manualMode`: the position is [manual], not the timeline. */
    var manualMode by mutableStateOf(initialManualMode)

    private var startPage = 0.0
    private var panning = false
    private var springJob: Job? = null

    val reduced: Boolean get() = playback.reduced
    val previewing: Boolean get() = playback.previewing

    /** `time`: loop time, from the frozen preview phase or the clock. */
    val time: Double get() = playback.time

    /** `timelineDriven`: the loop (or a frozen frame of it) drives the pages. */
    val timelineDriven: Boolean get() = autoPlayState.value || playback.previewing

    /** `renderManual`: the finger or a button owns the position. */
    val renderManual: Boolean get() = !playback.previewing && manualMode

    private val positionState = derivedStateOf {
        if (renderManual) manual.floatValue.toDouble() else SpectrumNewsCarouselMotion.timelinePage(time)
    }

    /** `position`: the carousel's place in pages, 0 to 4, a little beyond at either end while a drag rubber-bands. */
    val position: Double get() = positionState.value

    /** `clamp(Math.round(position), 0, 4)`: the page nearest the position. */
    fun nearestPage(): Int = SpectrumNewsCarouselMotion.clamp(SpectrumNewsCarouselMotion.jsRound(position), 0.0, 4.0).toInt()

    /** `welcomeFrame`: the window of the intro and outro, or the whole screen. */
    fun welcomeFrame(): SpectrumNewsCarouselFrame {
        if (reduced || !timelineDriven || renderManual) return SpectrumNewsCarouselFrame.Full
        return SpectrumNewsCarouselMotion.welcomeFrame(time)
    }

    /** `welcomeGlow`. */
    fun welcomeGlow(): Double {
        if (reduced || !timelineDriven || renderManual) return 1.0
        return SpectrumNewsCarouselMotion.welcomeGlow(time)
    }

    /** The welcome page's own fade (`intro` in `NewsPage`). */
    fun welcomeIntro(): Double {
        if (reduced || !timelineDriven || renderManual) return 1.0
        return SpectrumNewsCarouselMotion.welcomeIntro(time)
    }

    private fun cancelSpring() {
        springJob?.cancel()
        springJob = null
    }

    /** `manual.set(target)` or `manual.set(withSpring(target, ...))`; reduced motion skips the spring. */
    private fun moveTo(target: Double, velocity: Double) {
        cancelSpring()
        if (playback.reduced) {
            manual.floatValue = target.toFloat()
            return
        }
        val from = manual.floatValue
        springJob = scope.launch {
            animate(
                initialValue = from,
                targetValue = target.toFloat(),
                initialVelocity = velocity.toFloat(),
                animationSpec = spring(dampingRatio = SPRING_DAMPING_RATIO, stiffness = SPRING_STIFFNESS),
            ) { value, _ -> manual.floatValue = value }
        }
    }

    /** `manual.set(page); manualMode.set(true)`: back from the briefing, the tour opens on [page]. */
    fun showPage(page: Int) {
        cancelSpring()
        manual.floatValue = page.toFloat()
        manualMode = true
    }

    /** `landed`: a page was reached by hand; the loop's clock rests on its anchor and a tick is felt. */
    private fun landed(next: Int) {
        playback.settle(SpectrumNewsCarouselAnchors[next])
        selectionHaptic()
    }

    /** `go`: the pager dots and the accessibility actions. */
    fun go(next: Int) {
        if (playback.previewing) return
        val target = SpectrumNewsCarouselMotion.clamp(next.toDouble(), 0.0, 4.0).toInt()
        playback.pause()
        cancelSpring()
        manual.floatValue = position.toFloat()
        manualMode = true
        moveTo(target.toDouble(), velocity = 0.0)
        landed(target)
    }

    /** The pan took hold: the pages follow the finger from where they are. */
    fun panStart() {
        panning = true
        cancelSpring()
        manual.floatValue = position.toFloat()
        startPage = manual.floatValue.toDouble()
        manualMode = true
        playback.beginGesture()
    }

    /** The finger moved to [translationDp] (positive to the right) from where it went down. */
    fun panUpdate(translationDp: Double) {
        if (playback.reduced) return
        manual.floatValue = SpectrumNewsCarouselMotion.rubber(startPage - translationDp / width, 4.0).toFloat()
    }

    /**
     * The finger lifted or the gesture was cancelled ([success] false). The pages move on when the
     * drag passed 18% of the width or the finger was faster than 450 dp/s, towards where the finger
     * was going; otherwise they come back.
     */
    fun panFinalize(translationDp: Double, velocityDp: Double, success: Boolean) {
        if (!panning) return
        panning = false
        playback.endGesture()
        val fast = abs(velocityDp) > FLICK_SPEED
        val moved = success && (abs(translationDp) > width * 0.18 || fast)
        val direction = if (fast) velocityDp else translationDp
        val home = SpectrumNewsCarouselMotion.jsRound(startPage)
        val step = if (moved) (if (direction < 0.0) 1.0 else -1.0) else 0.0
        val target = SpectrumNewsCarouselMotion.clamp(home + step, 0.0, 4.0).toInt()
        moveTo(target.toDouble(), velocity = -velocityDp / width)
        if (moved && target.toDouble() != home) {
            landed(target)
        } else {
            playback.settle(SpectrumNewsCarouselAnchors[target])
        }
    }

    private companion object {
        const val SPRING_DAMPING_RATIO = 0.8f

        /**
         * `{ duration: 400, dampingRatio: .8 }` as a stiffness. Reanimated 4 reads `duration` as perceptual
         * and solves the stiffness so the spring (mass 1) settles after 1.5 times it, which gives 653.4.
         */
        const val SPRING_STIFFNESS = 653.4f

        /** The finger speed, in dp/s, above which a short drag still turns the page. */
        const val FLICK_SPEED = 450.0
    }
}

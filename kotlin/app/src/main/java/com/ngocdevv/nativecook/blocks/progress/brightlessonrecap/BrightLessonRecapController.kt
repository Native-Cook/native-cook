package com.ngocdevv.nativecook.blocks.progress.brightlessonrecap

import androidx.compose.animation.core.Animatable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** The two haptics of the screen: a light tap for Replay, a success notification for Claim. */
internal enum class BrightLessonRecapHaptic { Light, Success }

/**
 * The scene's clock and the state around it, the Compose side of `useSceneClock` and of the
 * `useState` calls at the top of `BrightLessonRecap` (BrightLessonRecap.tsx:95-114).
 *
 * Everything on screen is a function of [time]. It is a state that only draw and graphics-layer
 * lambdas read, so a frame redraws the pieces that move and recomposes nothing.
 *
 * With a [previewTime] the clock is frozen at that value and touch does not change anything.
 */
@Stable
internal class BrightLessonRecapController(
    val autoPlay: Boolean,
    val previewTime: Double?,
    initialReduced: Boolean,
) {
    /** Animator duration scale 0: the screen shows its complete pose and the loop does not run. */
    var reduced by mutableStateOf(initialReduced)

    /** The screen is in the foreground (resumed). */
    var foreground by mutableStateOf(true)

    var playing by mutableStateOf(autoPlay)
    var paused by mutableStateOf(false)
    var interacting by mutableStateOf(false)
    var claimed by mutableStateOf(false)

    /** Bumped on every claim; restarts the "+45 XP" rise. */
    var rewardRun by mutableIntStateOf(0)

    /** The "+45 XP" rise, 0 to 1 over 700 ms. */
    val reward = Animatable(0f)

    /** Called with the points when the key is pressed (`onClaim`). */
    var onClaim: ((Int) -> Unit)? = null

    /** Plays a haptic; set by the composable, which owns the view. */
    var haptic: (BrightLessonRecapHaptic) -> Unit = {}

    /** Loop time in seconds. */
    var time by mutableDoubleStateOf(initialTime(autoPlay, previewTime, initialReduced))
        private set

    private var anchorMs = -1.0

    val touchEnabled: Boolean get() = previewTime == null

    val running: Boolean get() = playing && !paused && !claimed

    /** The buttons work while the recap is complete, and after a claim (BrightLessonRecap.tsx:106). */
    val actionsEnabled: Boolean by derivedStateOf {
        claimed || (time >= BrightLessonRecapMotion.ACTIONS_FROM && time < BrightLessonRecapMotion.SOURCE_END)
    }

    /** The hero replays on tap while the mascot is on screen (BrightLessonRecap.tsx:107). */
    val mascotEnabled: Boolean by derivedStateOf {
        time > BrightLessonRecapMotion.MASCOT_FROM && time < BrightLessonRecapMotion.FADE_END
    }

    /** The clock's value for a [reduced] screen or a [previewTime], applied when either changes. */
    fun resetClock() {
        anchorMs = -1.0
        if (previewTime != null) {
            time = BrightLessonRecapMotion.normalize(previewTime, BRIGHT_LESSON_RECAP_LOOP)
        } else if (reduced) {
            time = BrightLessonRecapMotion.SETTLED
        }
    }

    /** One frame of the clock: the time since the anchor, wrapped to the period. */
    fun onFrame(frameMs: Double) {
        if (anchorMs < 0) anchorMs = frameMs - time * 1000
        time = ((frameMs - anchorMs) / 1000) % BRIGHT_LESSON_RECAP_LOOP
        // A screen that does not loop plays its story once and stops at its end.
        if (!autoPlay && time >= BrightLessonRecapMotion.SOURCE_END) playing = false
    }

    /** A touch on the screen stops autoplay until two seconds after the last one. */
    fun pause() {
        if (!touchEnabled) return
        interacting = true
        paused = true
    }

    fun release() {
        if (!touchEnabled) return
        interacting = false
    }

    /** Tap on the mascot or "Replay celebration": play the story again from the start. */
    fun replay() {
        if (!touchEnabled) return
        interacting = false
        claimed = false
        paused = false
        time = if (reduced) BrightLessonRecapMotion.SETTLED else 0.0
        anchorMs = -1.0
        playing = !reduced
        haptic(BrightLessonRecapHaptic.Light)
    }

    /** The CLAIM XP key: freeze on the complete recap, report the points, send "+45 XP" up. */
    fun claim() {
        if (!touchEnabled) return
        val t = time
        if (claimed || t < BrightLessonRecapMotion.ACTIONS_FROM || t >= BrightLessonRecapMotion.SOURCE_END) return
        interacting = false
        claimed = true
        paused = true
        time = BrightLessonRecapMotion.SETTLED
        onClaim?.invoke(CLAIM_POINTS)
        if (!reduced) rewardRun += 1
        haptic(BrightLessonRecapHaptic.Success)
    }

    companion object {
        const val CLAIM_POINTS = 45

        /** `useSceneClock`'s starting time (BrightLessonRecap.tsx:100, motion.ts:24). */
        private fun initialTime(autoPlay: Boolean, previewTime: Double?, reduced: Boolean): Double = when {
            previewTime != null -> BrightLessonRecapMotion.normalize(previewTime, BRIGHT_LESSON_RECAP_LOOP)
            reduced -> BrightLessonRecapMotion.SETTLED
            autoPlay -> 0.0
            else -> BrightLessonRecapMotion.SETTLED
        }
    }
}

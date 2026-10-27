package com.ngocdevv.nativecook.blocks.media.silverscreencarousel

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/** The loop ends here: the track has drifted 2.959 cards (SilverScreenCarousel.tsx:19). */
internal const val SILVER_SCREEN_CAROUSEL_SOURCE_END = 27.983

/** Seconds the drift needs for one card: a 238 px stride at 25.1675 px a second (line 20). */
private const val SECONDS_PER_CARD = 238.0 / 25.16752758685

/** `SPRING = { duration: 400, dampingRatio: 0.8 }` (line 21): Reanimated 4.5 reads `duration` as perceptual and solves
 * the stiffness that settles after 1.5 x 400 ms (`calculateNewStiffnessToMatchDuration`): 653.4, damping ratio 0.8. */
private const val SPRING_DAMPING_RATIO = 0.8f
private const val SPRING_STIFFNESS = 653.4f

/** Milliseconds of quiet after the last touch before the row springs home and the loop restarts (line 83). */
internal const val SILVER_SCREEN_CAROUSEL_QUIET_MS = 2000L

/** Where the row sits, in cards, at loop time [t] (`autoPosition`, SilverScreenCarousel.tsx:29). */
internal fun silverScreenCarouselAutoPosition(t: Double): Double =
    if (t < 28.35) min(t, SILVER_SCREEN_CAROUSEL_SOURCE_END) / SECONDS_PER_CARD else 0.0

/** The smoothstep of [t] between [start] and [end] (`phase`, motion.ts:6). */
internal fun silverScreenCarouselPhase(t: Double, start: Double, end: Double): Double {
    val x = min(1.0, max(0.0, (t - start) / (end - start)))
    return x * x * (3.0 - 2.0 * x)
}

/** Loop time kept inside one period (`normalizeSceneTime`, motion.ts:13). */
internal fun silverScreenCarouselNormalize(value: Double, period: Double): Double {
    if (!value.isFinite() || !period.isFinite() || period <= 0.0) return 0.0
    val remainder = value % period
    return if (remainder < 0.0) remainder + period else remainder
}

/** The profile a [position] rounds to: `Math.min(3, Math.max(0, Math.round(position)))`. */
internal fun silverScreenCarouselIndexAt(position: Double): Int =
    min(3, max(0, floor(position + 0.5).toInt()))

/**
 * The block's state: the one clock, the finger-driven position and everything the React Native
 * component keeps in shared values and `useState` (SilverScreenCarousel.tsx:69-101).
 *
 * Every frame of the row is a function of [time] while the clock drives it, or of [manualPosition]
 * once a touch has taken over ([manualMode]). Both are snapshot state, so a draw or `graphicsLayer`
 * block that calls [position] redraws by itself and nothing recomposes per frame. The flags below the
 * state are copied in by the composable after every composition.
 */
internal class SilverScreenCarouselMotion(
    private val scope: CoroutineScope,
    initialTime: Double,
) {
    /** Loop time in seconds, 0 until [SILVER_SCREEN_CAROUSEL_LOOP]. */
    var time by mutableDoubleStateOf(initialTime)

    /** True once a touch has taken the row from the clock (`manualMode`). */
    var manualMode by mutableStateOf(false)
        private set

    /** The row's position in cards while [manualMode] is on (`manualPosition`). */
    var manualPosition by mutableDoubleStateOf(0.0)
        private set

    /** The profile in the gate, 0-3 (`selected`). */
    var selected by mutableIntStateOf(0)

    /** The loop is paused by a touch (`manual`). */
    var manual by mutableStateOf(false)
        private set

    /** The name of the profile that was applied, or null (`applied`). */
    var applied by mutableStateOf<String?>(null)
        private set

    /** The quiet period has been started (`waiting`). */
    var waiting by mutableStateOf(false)
        private set

    /** Counts each new start of the quiet period (`quietGeneration`). */
    var quietGeneration by mutableIntStateOf(0)
        private set

    /** The shutter flash over the photograph in the gate, 0-0.85 (`flash`). */
    val flash = Animatable(0f)

    // What the composable copies in after every composition.
    var reduced = false
    var stepDp = 1f
    var onSelect: ((String) -> Unit)? = null
    var onApply: ((String) -> Unit)? = null
    var selectionHaptic: () -> Unit = {}
    var successHaptic: () -> Unit = {}

    private var dragStart = 0.0
    private var springJob: Job? = null

    /** The row's position in cards (`position`, line 74). */
    fun position(): Double = if (manualMode) manualPosition else silverScreenCarouselAutoPosition(time)

    /** Opacity of the whole row: it fades out at the end of the loop and back in (line 95). */
    fun trackAlpha(reduced: Boolean): Float {
        if (manualMode || reduced) return 1f
        val t = time
        val alpha = 1.0 -
            silverScreenCarouselPhase(t, SILVER_SCREEN_CAROUSEL_SOURCE_END, 28.35) +
            silverScreenCarouselPhase(t, 28.35, 28.7)
        return alpha.toFloat().coerceIn(0f, 1f)
    }

    /** Moves the clock on by [seconds] of real time, wrapping at the end of the loop. */
    fun advance(seconds: Double) {
        time = (time + seconds) % SILVER_SCREEN_CAROUSEL_LOOP.toDouble()
    }

    fun clearResume() {
        waiting = false
    }

    fun armResume() {
        quietGeneration += 1
        waiting = true
    }

    /** `begin`: a touch has taken over, so the loop pauses and a shown "applied" note goes. */
    fun begin() {
        clearResume()
        manual = true
        applied = null
    }

    /** `resume`: the row is home, the clock starts again from the beginning of the loop. */
    fun resume() {
        time = 0.0
        manualMode = false
        manual = false
        waiting = false
    }

    /** Two quiet seconds are over: the row springs home, then the loop restarts (lines 81-83). */
    fun springHome() {
        springTo(0.0) { resume() }
    }

    /** `commit`: the profile [index] was chosen by the user. */
    fun commit(index: Int) {
        selected = index
        onSelect?.invoke(SilverScreenCarouselProfiles[index].id)
        selectionHaptic()
    }

    /** `select`: a dot, an arrow or an accessibility action goes to profile [index] (line 96). */
    fun select(index: Int) {
        begin()
        cancelSpring()
        manualPosition = position()
        manualMode = true
        if (reduced) manualPosition = index.toDouble() else springTo(index.toDouble())
        commit(index)
        armResume()
    }

    /** `apply`: applies the profile in the gate and fires the shutter (line 97). */
    fun applyProfile() {
        clearResume()
        val index = silverScreenCarouselIndexAt(position())
        cancelSpring()
        manualPosition = index.toDouble()
        manualMode = true
        manual = true
        applied = SilverScreenCarouselProfiles[index].name
        onApply?.invoke(SilverScreenCarouselProfiles[index].id)
        fireShutter()
        successHaptic()
    }

    /** "Try another profile": the note goes and the quiet period starts (line 115). */
    fun tryAnother() {
        applied = null
        armResume()
    }

    /** `pauseReading`: the page is being scrolled, so the row stands still where it is (line 101). */
    fun pauseReading() {
        clearResume()
        cancelSpring()
        manualPosition = position()
        manualMode = true
        manual = true
    }

    /** `fireShutter`: 0.85 at once, then 420 ms back to 0 on the strong ease-out (line 100). */
    private fun fireShutter() {
        if (reduced) return
        scope.launch {
            flash.snapTo(0.85f)
            flash.animateTo(0f, tween(420, easing = SilverScreenCarouselSettle))
        }
    }

    /** `onStart` of the pan (line 91). */
    fun panStart() {
        cancelSpring()
        val current = position()
        manualPosition = current
        dragStart = current
        manualMode = true
        begin()
    }

    /** `onUpdate` of the pan: the row follows the finger 1:1 with rubber-banding past the ends (line 92). */
    fun panUpdate(translationDp: Float) {
        val raw = dragStart - translationDp / stepDp
        val bound = min(3.0, max(0.0, raw))
        val over = raw - bound
        manualPosition = bound + over / (1.0 + abs(over) * 4.0) * 0.5
    }

    /** `onEnd` of the pan: [success] is false when the gesture was cancelled (line 93). */
    fun panEnd(success: Boolean, velocityDpPerSecond: Float) {
        cancelSpring()
        if (!success) {
            val rollback = silverScreenCarouselIndexAt(dragStart).toDouble()
            if (reduced) manualPosition = rollback else springTo(rollback)
            return
        }
        val velocity = (-velocityDpPerSecond / stepDp).toDouble()
        val next = silverScreenCarouselIndexAt(manualPosition + velocity * 0.18)
        if (reduced) manualPosition = next.toDouble() else springTo(next.toDouble(), velocity)
        commit(next)
    }

    /** `onFinalize` of the pan: every touch ends here and starts the quiet period (line 94). */
    fun panFinalize() {
        armResume()
    }

    /** `cancelAnimation(manualPosition)`: a running spring stops where it is. */
    fun cancelSpring() {
        springJob?.cancel()
        springJob = null
    }

    /** `withSpring(target, SPRING)` on the manual position, from where it is, with [velocity] cards a second. */
    private fun springTo(target: Double, velocity: Double = 0.0, onFinished: (() -> Unit)? = null) {
        cancelSpring()
        springJob = scope.launch {
            animate(
                initialValue = manualPosition.toFloat(),
                targetValue = target.toFloat(),
                initialVelocity = velocity.toFloat(),
                animationSpec = spring(dampingRatio = SPRING_DAMPING_RATIO, stiffness = SPRING_STIFFNESS),
            ) { value, _ ->
                manualPosition = value.toDouble()
            }
            manualPosition = target
            onFinished?.invoke()
        }
    }
}

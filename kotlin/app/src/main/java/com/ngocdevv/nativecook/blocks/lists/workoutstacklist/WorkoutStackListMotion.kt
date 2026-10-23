package com.ngocdevv.nativecook.blocks.lists.workoutstacklist

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.round
import kotlin.math.sin
import kotlin.math.sqrt

internal data class WorkoutStackScene(
    val deck: Float,
    val open: Float,
    val leave: Float,
    val scroll: Float,
    val month: Float,
    val day: Float,
    val category: Int,
)

internal data class WorkoutDeckPose(
    val top: Float,
    val scale: Float,
    val title: Float,
    val titleScale: Float,
    val opacity: Float,
    val fog: Float,
)

internal fun workoutDeckPose(distance: Float): WorkoutDeckPose {
    return WorkoutDeckPose(
        top = workoutInterpolate(distance, WorkoutStackListSpec.Deck.Positions, WorkoutStackListSpec.Deck.Tops),
        scale = workoutInterpolate(distance, WorkoutStackListSpec.Deck.Positions, WorkoutStackListSpec.Deck.Scales),
        title = workoutInterpolate(distance, WorkoutStackListSpec.Deck.Positions, WorkoutStackListSpec.Deck.TitleTops),
        titleScale = workoutInterpolate(distance, WorkoutStackListSpec.Deck.Positions, WorkoutStackListSpec.Deck.TitleScales),
        opacity = workoutInterpolate(distance, WorkoutStackListSpec.Deck.Positions, WorkoutStackListSpec.Deck.Opacities),
        fog = workoutInterpolate(distance, WorkoutStackListSpec.Deck.Positions, WorkoutStackListSpec.Deck.Fog),
    )
}

internal fun sampleWorkoutStackScene(inputMs: Long): WorkoutStackScene {
    val time = ((inputMs % WorkoutStackListSpec.LOOP_MS) + WorkoutStackListSpec.LOOP_MS) % WorkoutStackListSpec.LOOP_MS
    val value = time.toFloat()
    val leave = workoutPin(workoutSegment(value, WorkoutStackListSpec.Motion.Back))
    val finished = leave >= 1f
    return WorkoutStackScene(
        deck = if (value < WorkoutStackListSpec.Motion.Open.endInclusive) {
            5f * workoutPin(workoutSegment(value, WorkoutStackListSpec.Motion.Scroll))
        } else {
            0f
        },
        open = if (finished) 0f else workoutPin(workoutSegment(value, WorkoutStackListSpec.Motion.Open)),
        leave = if (finished) 0f else leave,
        scroll = if (finished) 0f else workoutPin(workoutSegment(value, WorkoutStackListSpec.Motion.DetailScroll)),
        month = if (finished) 0f else workoutPin(workoutSegment(value, WorkoutStackListSpec.Motion.Month)),
        day = if (finished) 0f else workoutPin(workoutSegment(value, WorkoutStackListSpec.Motion.Day)),
        category = WorkoutStackListSpec.Crossfit,
    )
}

internal fun workoutSegment(value: Float, range: ClosedFloatingPointRange<Float>): Float {
    return ((value - range.start) / (range.endInclusive - range.start)).coerceIn(0f, 1f)
}

internal fun workoutMix(from: Float, to: Float, amount: Float): Float {
    return from + (to - from) * amount
}

internal fun workoutInterpolate(value: Float, input: FloatArray, output: FloatArray): Float {
    if (value <= input.first()) return output.first()
    for (index in 0 until input.lastIndex) {
        if (value <= input[index + 1]) {
            val amount = (value - input[index]) / (input[index + 1] - input[index])
            return workoutMix(output[index], output[index + 1], amount)
        }
    }
    return output.last()
}

internal fun workoutBezier(progress: Float, x1: Float, y1: Float, x2: Float, y2: Float): Float {
    val value = progress.coerceIn(0f, 1f)
    // Exact endpoints also control whether the list or the detail owns the canvas.
    if (value == 0f || value == 1f) return value
    var low = 0.0
    var high = 1.0
    repeat(18) {
        val middle = (low + high) / 2.0
        val x = 3.0 * (1.0 - middle).pow(2) * middle * x1 +
            3.0 * (1.0 - middle) * middle.pow(2) * x2 + middle.pow(3)
        if (x < value) low = middle else high = middle
    }
    val time = (low + high) / 2.0
    return (
        3.0 * (1.0 - time).pow(2) * time * y1 +
            3.0 * (1.0 - time) * time.pow(2) * y2 + time.pow(3)
        ).toFloat()
}

internal fun workoutPin(progress: Float): Float = workoutBezier(progress, .4f, 0f, .2f, 1f)
internal fun workoutOut(progress: Float): Float = workoutBezier(progress, .23f, 1f, .32f, 1f)

private sealed interface WorkoutCurve {
    data object Pin : WorkoutCurve
    data object Out : WorkoutCurve
    data class Spring(val dampingRatio: Float, val velocity: Float) : WorkoutCurve
}

private class WorkoutTween(
    private val from: Float,
    private val to: Float,
    private val startNs: Long,
    private val durationMs: Long,
    private val curve: WorkoutCurve,
) {
    fun value(nowNs: Long): Float {
        if (durationMs <= 0L) return to
        val progress = ((nowNs - startNs).toDouble() / 1_000_000.0 / durationMs).coerceIn(0.0, 1.0)
        val eased = when (curve) {
            WorkoutCurve.Pin -> workoutPin(progress.toFloat()).toDouble()
            WorkoutCurve.Out -> workoutOut(progress.toFloat()).toDouble()
            is WorkoutCurve.Spring -> spring(progress, curve.dampingRatio.toDouble(), curve.velocity.toDouble())
        }
        return from + (to - from) * eased.toFloat()
    }

    fun done(nowNs: Long): Boolean = nowNs >= startNs + durationMs * 1_000_000L

    private fun spring(progress: Double, dampingRatio: Double, inputVelocity: Double): Double {
        if (progress >= 1.0) return 1.0
        val frequency = 10.0
        val damped = frequency * sqrt(max(1.0 - dampingRatio * dampingRatio, .001))
        val envelope = exp(-dampingRatio * frequency * progress)
        val velocityTerm = inputVelocity / max(damped, .001)
        val value = 1.0 - envelope * (cos(damped * progress) + (dampingRatio * frequency - velocityTerm) / damped * sin(damped * progress))
        return value.coerceIn(-.3, 1.3)
    }
}

@Stable
internal class WorkoutStackMotion(autoPlay: Boolean) {
    var deck by mutableFloatStateOf(0f)
        private set
    var open by mutableFloatStateOf(0f)
        private set
    var leave by mutableFloatStateOf(0f)
        private set
    var scroll by mutableFloatStateOf(0f)
        private set
    var month by mutableFloatStateOf(0f)
        private set
    var day by mutableFloatStateOf(0f)
        private set
    var category by mutableIntStateOf(WorkoutStackListSpec.Crossfit)
        private set
    var cardPress by mutableFloatStateOf(1f)
        private set
    var backPress by mutableFloatStateOf(1f)
        private set
    var autoplaying by mutableStateOf(autoPlay)
        private set

    private var loopStartNs = -1L
    private var deckTween: WorkoutTween? = null
    private var openTween: WorkoutTween? = null
    private var leaveTween: WorkoutTween? = null
    private var scrollTween: WorkoutTween? = null
    private var monthTween: WorkoutTween? = null
    private var dayTween: WorkoutTween? = null
    private var cardPressTween: WorkoutTween? = null
    private var backPressTween: WorkoutTween? = null

    fun scene(): WorkoutStackScene {
        return WorkoutStackScene(deck, open, leave, scroll, month, day, category)
    }

    fun advance(nowNs: Long) {
        if (autoplaying) {
            if (loopStartNs < 0L) loopStartNs = nowNs
            apply(sampleWorkoutStackScene((nowNs - loopStartNs) / 1_000_000L))
            return
        }
        deck = update(deckTween, deck, nowNs).also { if (deckTween?.done(nowNs) == true) deckTween = null }
        open = update(openTween, open, nowNs).also { if (openTween?.done(nowNs) == true) openTween = null }
        leave = update(leaveTween, leave, nowNs).also { if (leaveTween?.done(nowNs) == true) leaveTween = null }
        scroll = update(scrollTween, scroll, nowNs).also { if (scrollTween?.done(nowNs) == true) scrollTween = null }
        month = update(monthTween, month, nowNs).also { if (monthTween?.done(nowNs) == true) monthTween = null }
        day = update(dayTween, day, nowNs).also { if (dayTween?.done(nowNs) == true) dayTween = null }
        cardPress = update(cardPressTween, cardPress, nowNs).also { if (cardPressTween?.done(nowNs) == true) cardPressTween = null }
        backPress = update(backPressTween, backPress, nowNs).also { if (backPressTween?.done(nowNs) == true) backPressTween = null }
    }

    fun hasMotion(): Boolean {
        return autoplaying || listOf(deckTween, openTween, leaveTween, scrollTween, monthTween, dayTween, cardPressTween, backPressTween).any { it != null }
    }

    fun beginTouch(nowNs: Long) {
        advance(nowNs)
        autoplaying = false
        clearTweens()
    }

    fun updateDeck(value: Float) {
        deck = value
    }

    fun updateScroll(value: Float) {
        scroll = value
    }

    fun pressCard(pressed: Boolean, nowNs: Long) {
        cardPressTween = WorkoutTween(cardPress, if (pressed) WorkoutStackListSpec.Motion.PressScale else 1f, nowNs, if (pressed) WorkoutStackListSpec.Motion.PressInMs else WorkoutStackListSpec.Motion.PressOutMs, WorkoutCurve.Out)
    }

    fun pressBack(pressed: Boolean, nowNs: Long) {
        backPressTween = WorkoutTween(backPress, if (pressed) WorkoutStackListSpec.Motion.PressScale else 1f, nowNs, if (pressed) WorkoutStackListSpec.Motion.PressInMs else WorkoutStackListSpec.Motion.PressOutMs, WorkoutCurve.Out)
    }

    fun snapDeck(target: Float, velocity: Float, nowNs: Long) {
        deckTween = WorkoutTween(deck, target, nowNs, WorkoutStackListSpec.Motion.ScrollSnapMs, WorkoutCurve.Spring(WorkoutStackListSpec.Motion.ScrollDampingRatio, velocity))
    }

    fun snapScroll(target: Float, velocity: Float, nowNs: Long) {
        scrollTween = WorkoutTween(scroll, target, nowNs, WorkoutStackListSpec.Motion.ScrollSnapMs, WorkoutCurve.Spring(WorkoutStackListSpec.Motion.ScrollDampingRatio, velocity))
    }

    fun snapMonth(target: Float, velocity: Float, nowNs: Long) {
        monthTween = WorkoutTween(month, target, nowNs, WorkoutStackListSpec.Motion.ScrollSnapMs, WorkoutCurve.Spring(WorkoutStackListSpec.Motion.ScrollDampingRatio, velocity))
    }

    fun openCategory(index: Int, reduced: Boolean, nowNs: Long) {
        category = index
        scroll = 0f
        month = 0f
        day = 0f
        leave = 0f
        openTween = WorkoutTween(open, 1f, nowNs, if (reduced) WorkoutStackListSpec.Motion.ReducedFadeMs else WorkoutStackListSpec.Motion.OpenMs, WorkoutCurve.Pin)
    }

    fun back(reduced: Boolean, nowNs: Long) {
        leaveTween = WorkoutTween(leave, 1f, nowNs, if (reduced) WorkoutStackListSpec.Motion.ReducedFadeMs else WorkoutStackListSpec.Motion.BackMs, WorkoutCurve.Pin)
    }

    fun finishBack() {
        open = 0f
        leave = 0f
        scroll = 0f
        month = 0f
        day = 0f
        leaveTween = null
    }

    fun pickDay(value: Int, reduced: Boolean, nowNs: Long) {
        dayTween = WorkoutTween(day, if (value == 9) 1f else 0f, nowNs, if (reduced) WorkoutStackListSpec.Motion.ReducedFadeMs else WorkoutStackListSpec.Motion.DayMs, WorkoutCurve.Pin)
    }

    fun returnToRest(nowNs: Long): Long {
        val leaveDuration = if (open > 0f) WorkoutStackListSpec.Motion.BackMs else 1L
        leaveTween = WorkoutTween(leave, 1f, nowNs, leaveDuration, WorkoutCurve.Pin)
        deckTween = WorkoutTween(deck, 0f, nowNs, WorkoutStackListSpec.Motion.BackMs, WorkoutCurve.Pin)
        return leaveDuration
    }

    fun restartAutoplay(nowNs: Long) {
        apply(sampleWorkoutStackScene(0))
        clearTweens()
        loopStartNs = nowNs
        autoplaying = true
    }

    fun reset(enabled: Boolean) {
        apply(sampleWorkoutStackScene(0))
        clearTweens()
        loopStartNs = -1L
        autoplaying = enabled
    }

    private fun apply(scene: WorkoutStackScene) {
        deck = scene.deck
        open = scene.open
        leave = scene.leave
        scroll = scene.scroll
        month = scene.month
        day = scene.day
        category = scene.category
    }

    private fun update(tween: WorkoutTween?, fallback: Float, nowNs: Long): Float {
        return tween?.value(nowNs) ?: fallback
    }

    private fun clearTweens() {
        deckTween = null
        openTween = null
        leaveTween = null
        scrollTween = null
        monthTween = null
        dayTween = null
        cardPressTween = null
        backPressTween = null
    }
}

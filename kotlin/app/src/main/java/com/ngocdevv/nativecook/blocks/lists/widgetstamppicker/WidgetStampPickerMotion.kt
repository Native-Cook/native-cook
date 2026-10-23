package com.ngocdevv.nativecook.blocks.lists.widgetstamppicker

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.setValue
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

internal const val LOOP_MS = 11_000L
internal const val WIDGET_STAMP_LOOP_MS = LOOP_MS
internal const val WIDGET_STAMP_RESUME_MS = 3_500.0
internal const val WIDGET_STAMP_RESTART_MS = 900.0
internal const val WIDGET_STAMP_FADE_MS = 180.0

internal const val WIDGET_STAMP_STEP_X = 251.0
internal const val WIDGET_STAMP_RUBBER = 0.35
internal const val WIDGET_STAMP_PROJECT_S = 0.2
internal const val WIDGET_STAMP_START_INDEX = 2
internal const val WIDGET_STAMP_COUNT = 4

internal val WidgetWheelTable = doubleArrayOf(0.0, 0.25, 0.5, 0.75, 1.0, 1.5, 2.2)
internal val WidgetWheelRise = doubleArrayOf(0.0, 9.5, 38.0, 84.0, 159.0, 300.0, 480.0)
internal val WidgetWheelTilt = doubleArrayOf(0.0, 7.0, 14.0, 21.0, 31.0, 44.0, 58.0)
internal val WidgetWheelScale = doubleArrayOf(1.0, 0.875, 0.755, 0.69, 0.62, 0.55, 0.5)
internal const val WIDGET_STAMP_HIDE_BEYOND = 2.2

internal data class WidgetStampSwipe(
    val at: Double,
    val direction: Int,
    val dragMs: Double,
    val dragItems: Double,
)

internal val WidgetStampSwipes = listOf(
    WidgetStampSwipe(2_333.0, -1, 100.0, 0.46),
    WidgetStampSwipe(3_167.0, 1, 100.0, 0.43),
    WidgetStampSwipe(4_100.0, -1, 100.0, 0.42),
    WidgetStampSwipe(4_867.0, -1, 133.0, 0.35),
    WidgetStampSwipe(7_300.0, 1, 100.0, 0.43),
    WidgetStampSwipe(8_200.0, 1, 100.0, 0.43),
)

private const val WidgetNudgeAt = 1_567.0
private const val WidgetNudgeItems = 0.047
private const val WidgetNudgeInMs = 33.0
private const val WidgetNudgeHoldMs = 67.0
private const val WidgetNudgeOutMs = 33.0
private const val WidgetSettleMs = 700.0
private const val WidgetTauMs = 100.0
private const val WidgetSettleK = WidgetSettleMs / WidgetTauMs
private const val WidgetSpringMass = 4.0
private const val WidgetSpringDampingRatio = 0.8
private const val WidgetSpringEnergyThreshold = 6e-9
private const val WidgetSpringPerceptualDurationSeconds = 0.6
private const val WidgetSpringMaxStiffness = 8_000.0
private const val WidgetSpringBisectionPrecision = WidgetSpringEnergyThreshold * 1e-3
private const val WidgetSpringBisectionIterations = 100
private const val WidgetSpringMaxStepMs = 64.0

private val WidgetBlinkStarts = arrayOf(
    intArrayOf(0, 120),
    intArrayOf(2, 2_050),
    intArrayOf(0, 2_400),
    intArrayOf(2, 2_400),
    intArrayOf(1, 3_000),
    intArrayOf(1, 4_930),
    intArrayOf(0, 5_500),
    intArrayOf(2, 6_000),
    intArrayOf(1, 8_300),
    intArrayOf(0, 9_800),
    intArrayOf(2, 9_800),
)

internal fun widgetWheelAt(timeMs: Double): Double {
    var index = WIDGET_STAMP_START_INDEX.toDouble()
    var position = index
    val nudgeTime = timeMs - WidgetNudgeAt
    if (nudgeTime > 0.0 && nudgeTime < WidgetNudgeInMs + WidgetNudgeHoldMs + WidgetNudgeOutMs) {
        val progress = when {
            nudgeTime < WidgetNudgeInMs -> nudgeTime / WidgetNudgeInMs
            nudgeTime < WidgetNudgeInMs + WidgetNudgeHoldMs -> 1.0
            else -> 1.0 - (nudgeTime - WidgetNudgeInMs - WidgetNudgeHoldMs) / WidgetNudgeOutMs
        }
        return WIDGET_STAMP_START_INDEX.toDouble() - WidgetNudgeItems * progress
    }

    for (swipe in WidgetStampSwipes) {
        if (swipe.at > timeMs) break
        val local = timeMs - swipe.at
        val target = (index + swipe.direction).coerceIn(0.0, WIDGET_STAMP_COUNT - 1.0)
        val grab = index + swipe.direction * swipe.dragItems
        position = when {
            local < swipe.dragMs -> index + (grab - index) * widgetGrabEase(local / swipe.dragMs)
            local < swipe.dragMs + WidgetSettleMs -> {
                grab + (target - grab) * widgetSettle((local - swipe.dragMs) / WidgetSettleMs)
            }
            else -> target
        }
        index = target
    }
    return position
}

internal fun widgetEyesAt(timeMs: Double): BooleanArray {
    return BooleanArray(3) { blob ->
        WidgetBlinkStarts.any { it[0] == blob && timeMs >= it[1] && timeMs < it[1] + 130 }
    }
}

internal fun widgetInterpolate(value: Double, table: DoubleArray, output: DoubleArray): Double {
    if (value <= table.first()) return output.first()
    if (value >= table.last()) return output.last()
    for (index in 1 until table.size) {
        if (value <= table[index]) {
            val span = table[index] - table[index - 1]
            val local = if (span == 0.0) 0.0 else (value - table[index - 1]) / span
            return output[index - 1] + (output[index] - output[index - 1]) * local
        }
    }
    return output.last()
}

private fun widgetSettle(value: Double): Double {
    return (1.0 - exp(-WidgetSettleK * value)) / (1.0 - exp(-WidgetSettleK))
}

private fun widgetGrabEase(value: Double): Double {
    return 0.6 * value + 0.4 * value * value
}

private data class WidgetPickerSpring(
    val target: Double,
    var velocity: Double,
    val stiffness: Double,
    val initialEnergy: Double,
    val omega0: Double,
    val omega1: Double,
)

private fun widgetSpringEnergy(displacement: Double, velocity: Double, stiffness: Double): Double {
    return 0.5 * stiffness * displacement * displacement + 0.5 * WidgetSpringMass * velocity * velocity
}

private fun widgetSpringStiffness(displacement: Double, velocity: Double): Double {
    fun energyDifference(stiffness: Double): Double {
        val omega0 = sqrt(stiffness / WidgetSpringMass) * WidgetSpringDampingRatio
        val decay = exp(-omega0 * WidgetSpringPerceptualDurationSeconds)
        val velocityTerm = velocity + displacement * omega0
        val endDisplacement = (displacement + velocityTerm * WidgetSpringPerceptualDurationSeconds) * decay
        val endVelocity = endDisplacement * -omega0 + velocityTerm * decay
        val initialEnergy = widgetSpringEnergy(displacement, velocity, stiffness)
        val endEnergy = widgetSpringEnergy(endDisplacement, endVelocity, stiffness)
        return endEnergy / initialEnergy - WidgetSpringEnergyThreshold
    }

    var minimum = Math.ulp(1.0)
    var maximum = WidgetSpringMaxStiffness
    val direction = if (energyDifference(maximum) >= energyDifference(minimum)) 1.0 else -1.0
    var current = (maximum + minimum) / 2.0
    var iterations = WidgetSpringBisectionIterations
    while (abs(energyDifference(current)) > WidgetSpringBisectionPrecision && iterations > 0) {
        iterations -= 1
        if (energyDifference(current) * direction < 0.0) {
            minimum = current
        } else {
            maximum = current
        }
        current = (maximum + minimum) / 2.0
    }
    return current
}

/** Mutable owner of the wheel clock, user drag and restart state. */
internal class WidgetStampPickerMotion(initialAutoPlay: Boolean) {
    var position = WIDGET_STAMP_START_INDEX.toDouble()
    var dragStart = position
    var byUser = false
    var autoRunning = initialAutoPlay
    private var reducedMode = false
    private var touching = false
    var autoTimeMs = 0.0
    var resumeRemainingMs = -1.0
    var restartRemainingMs = -1.0
    var lastUserIndex = WIDGET_STAMP_START_INDEX
        private set

    var reducedFrom = position
        private set
    var reducedTo = position
        private set
    var reducedFadeElapsedMs = WIDGET_STAMP_FADE_MS
        private set

    var frameSignal by mutableLongStateOf(0L)
        private set
    var selectedIndex by mutableIntStateOf(WIDGET_STAMP_START_INDEX)
        private set

    private var spring: WidgetPickerSpring? = null

    fun configureAutoPlay(enabled: Boolean, reducedMotion: Boolean) {
        if (reducedMotion) {
            reducedMode = true
            autoRunning = false
            resumeRemainingMs = -1.0
            restartRemainingMs = -1.0
            spring = null
            position = position.roundToInt().coerceIn(0, WIDGET_STAMP_COUNT - 1).toDouble()
            selectedIndex = position.toInt()
            return
        }
        if (reducedMode) {
            reducedMode = false
            if (enabled && !touching) {
                byUser = false
                autoTimeMs = 0.0
                autoRunning = true
                position = WIDGET_STAMP_START_INDEX.toDouble()
                selectedIndex = WIDGET_STAMP_START_INDEX
                return
            }
        }
        if (!enabled) {
            autoRunning = false
            return
        }
        if (!byUser && !autoRunning && restartRemainingMs < 0.0) {
            autoTimeMs = 0.0
            autoRunning = true
            position = WIDGET_STAMP_START_INDEX.toDouble()
        }
    }

    fun beginTouch() {
        touching = true
        autoRunning = false
        byUser = true
        resumeRemainingMs = -1.0
        restartRemainingMs = -1.0
        spring = null
        dragStart = position
    }

    fun endTouch(resumeEnabled: Boolean) {
        touching = false
        if (byUser && resumeEnabled) resumeRemainingMs = WIDGET_STAMP_RESUME_MS
    }

    fun beginDrag() {
        spring = null
        dragStart = position
    }

    fun updateDrag(translationX: Double, lastIndex: Int = selectedIndex): Int {
        var next = dragStart - translationX / WIDGET_STAMP_STEP_X
        if (next < 0.0) next *= WIDGET_STAMP_RUBBER
        if (next > WIDGET_STAMP_COUNT - 1.0) {
            next = WIDGET_STAMP_COUNT - 1.0 + (next - (WIDGET_STAMP_COUNT - 1.0)) * WIDGET_STAMP_RUBBER
        }
        position = next
        val rounded = position.roundToInt().coerceIn(0, WIDGET_STAMP_COUNT - 1)
        selectedIndex = rounded
        return if (byUser && rounded != lastIndex) rounded else -1
    }

    fun release(velocityX: Double, reducedMotion: Boolean): Int {
        val velocity = -velocityX / WIDGET_STAMP_STEP_X
        val target = (position + velocity * WIDGET_STAMP_PROJECT_S)
            .roundToInt()
            .coerceIn(0, WIDGET_STAMP_COUNT - 1)
            .toDouble()
        settleTo(target, velocity, reducedMotion)
        return selectedIndex
    }

    fun tapNeighbour(delta: Int, reducedMotion: Boolean) {
        val target = (position.roundToInt() + delta).coerceIn(0, WIDGET_STAMP_COUNT - 1).toDouble()
        settleTo(target, 0.0, reducedMotion)
    }

    fun settleTo(target: Double, velocity: Double, reducedMotion: Boolean) {
        if (reducedMotion) {
            reducedFrom = position
            reducedTo = target
            reducedFadeElapsedMs = 0.0
            spring = null
            return
        }
        val initialVelocity = if (
            (target > position && velocity < 0.0) || (target < position && velocity > 0.0)
        ) {
            0.0
        } else {
            velocity
        }
        val displacement = position - target
        val stiffness = widgetSpringStiffness(displacement, initialVelocity)
        val omega0 = sqrt(stiffness / WidgetSpringMass)
        spring = WidgetPickerSpring(
            target = target,
            velocity = initialVelocity,
            stiffness = stiffness,
            // Reanimated evaluates the initial-energy denominator with the supplied config velocity.
            initialEnergy = widgetSpringEnergy(displacement, velocity, stiffness),
            omega0 = omega0,
            omega1 = omega0 * sqrt(1.0 - WidgetSpringDampingRatio * WidgetSpringDampingRatio),
        )
    }

    fun needsFrames(autoPlay: Boolean, reducedMotion: Boolean): Boolean {
        if (touching || reducedFadeElapsedMs < WIDGET_STAMP_FADE_MS) return true
        if (reducedMotion) return false
        return spring != null || (autoRunning && autoPlay && !byUser) ||
            resumeRemainingMs >= 0.0 || restartRemainingMs >= 0.0
    }

    /** Returns true only for a user-driven index change, so autoplay never haptics. */
    fun advance(deltaMs: Double, autoPlay: Boolean, reducedMotion: Boolean): Boolean {
        if (resumeRemainingMs >= 0.0) {
            resumeRemainingMs -= deltaMs
            if (resumeRemainingMs <= 0.0) {
                resumeRemainingMs = -1.0
                byUser = false
                restartRemainingMs = WIDGET_STAMP_RESTART_MS
                settleTo(WIDGET_STAMP_START_INDEX.toDouble(), 0.0, reducedMotion)
            }
        }
        if (restartRemainingMs >= 0.0) {
            restartRemainingMs -= deltaMs
            if (restartRemainingMs <= 0.0) {
                restartRemainingMs = -1.0
                if (autoPlay) {
                    autoTimeMs = 0.0
                    autoRunning = true
                    position = WIDGET_STAMP_START_INDEX.toDouble()
                    spring = null
                }
            }
        }

        if (autoRunning && autoPlay && !byUser) {
            autoTimeMs = (autoTimeMs + deltaMs) % LOOP_MS.toDouble()
            val target = widgetWheelAt(autoTimeMs)
            if (reducedMotion) {
                val discrete = target.roundToInt().coerceIn(0, WIDGET_STAMP_COUNT - 1).toDouble()
                if (reducedFadeElapsedMs >= WIDGET_STAMP_FADE_MS && discrete != position.roundToInt().toDouble()) {
                    reducedFrom = position.roundToInt().toDouble()
                    reducedTo = discrete
                    reducedFadeElapsedMs = 0.0
                }
            } else {
                position = target
            }
        } else {
            val currentSpring = spring
            if (currentSpring != null) {
                val t = min(deltaMs, WidgetSpringMaxStepMs) / 1_000.0
                val displacement = position - currentSpring.target
                val velocity = currentSpring.velocity
                val sine = sin(currentSpring.omega1 * t)
                val cosine = cos(currentSpring.omega1 * t)
                val envelope = exp(-WidgetSpringDampingRatio * currentSpring.omega0 * t)
                val fragment = envelope * (
                    sine * ((velocity + WidgetSpringDampingRatio * currentSpring.omega0 * displacement) / currentSpring.omega1) +
                        displacement * cosine
                    )
                position = currentSpring.target + fragment
                currentSpring.velocity =
                    -WidgetSpringDampingRatio * currentSpring.omega0 * fragment +
                        envelope * (
                            cosine * (velocity + WidgetSpringDampingRatio * currentSpring.omega0 * displacement) -
                                currentSpring.omega1 * displacement * sine
                            )
                val currentEnergy = widgetSpringEnergy(
                    currentSpring.target - position,
                    currentSpring.velocity,
                    currentSpring.stiffness,
                )
                if (
                    currentSpring.initialEnergy == 0.0 ||
                        currentEnergy / currentSpring.initialEnergy <= WidgetSpringEnergyThreshold
                ) {
                    position = currentSpring.target
                    spring = null
                }
            }
        }

        advanceReducedFade(deltaMs)

        val rounded = position.roundToInt().coerceIn(0, WIDGET_STAMP_COUNT - 1)
        val changed = rounded != lastUserIndex
        if (changed) lastUserIndex = rounded
        selectedIndex = rounded
        frameSignal++
        return changed && byUser
    }

    private fun advanceReducedFade(deltaMs: Double) {
        if (reducedFadeElapsedMs >= WIDGET_STAMP_FADE_MS) return
        val previousElapsed = reducedFadeElapsedMs
        reducedFadeElapsedMs = min(WIDGET_STAMP_FADE_MS, reducedFadeElapsedMs + deltaMs)
        val midpoint = WIDGET_STAMP_FADE_MS / 2.0
        if (previousElapsed < midpoint && reducedFadeElapsedMs >= midpoint) {
            position = reducedTo
            selectedIndex = reducedTo.roundToInt().coerceIn(0, WIDGET_STAMP_COUNT - 1)
        }
    }
}

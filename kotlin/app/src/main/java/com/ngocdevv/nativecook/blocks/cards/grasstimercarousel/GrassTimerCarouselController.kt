package com.ngocdevv.nativecook.blocks.cards.grasstimercarousel

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

/** Pure clock-driven state for the gesture, timer, lifecycle, and deterministic demo paths. */
internal class GrassTimerCarouselController {
    private data class Transition(
        val from: Double,
        val to: Double,
        val startedAt: Double,
        val duration: Double,
        val curve: (Double) -> Double,
    ) {
        fun value(at: Double): Double {
            val progress = GrassTimerMath.clamp((at - startedAt) / duration)
            return from + (to - from) * curve(progress)
        }

        fun isFinished(at: Double): Boolean = at - startedAt >= duration
    }

    private data class CarouselSpring(
        val from: Double,
        val to: Double,
        val initialVelocity: Double,
        val startedAt: Double,
    ) {
        fun value(at: Double): Double {
            val elapsed = max(0.0, at - startedAt)
            val omega = 15.7
            val damping = 0.8
            val damped = omega * sqrt(1 - damping * damping)
            val displacement = from - to
            val sineCoefficient = (initialVelocity + damping * omega * displacement) / damped
            return to + exp(-damping * omega * elapsed) *
                (displacement * cos(damped * elapsed) + sineCoefficient * sin(damped * elapsed))
        }

        fun velocity(at: Double): Double {
            val elapsed = max(0.0, at - startedAt)
            val omega = 15.7
            val damping = 0.8
            val damped = omega * sqrt(1 - damping * damping)
            val decay = damping * omega
            val displacement = from - to
            val sineCoefficient = (initialVelocity + decay * displacement) / damped
            val cosine = cos(damped * elapsed)
            val sine = sin(damped * elapsed)
            return exp(-decay * elapsed) * (
                -decay * (displacement * cosine + sineCoefficient * sine) +
                    (-displacement * damped * sine + sineCoefficient * damped * cosine)
                )
        }
    }

    private var transition = Transition(0.0, 0.0, 0.0, 0.001) { it }
    private var holdStartedAt: Double? = null
    private var releaseStartedAt: Double? = null
    private var releaseProgress = 0.0
    private var activeStartedAt: Double? = null
    private var demoOrigin: Double? = null
    private var interruptedAt: Double? = null
    private var suspendedAt: Double? = null
    private var quietMode = false
    private var autoPlayEnabled = false
    private var resumeDemoAfterReturn = false
    private var carouselOffset = 0.0
    private var carouselSpring: CarouselSpring? = null

    var phase = GrassTimerPhase.Idle
        private set
    var selectedCard = 0
        private set
    var isFingerDown = false
        private set
    var isCarouselDragging = false
        private set
    var activationSequence = 0
        private set
    var selectionSequence = 0
        private set

    fun appear(at: Double) {
        if (demoOrigin == null) demoOrigin = at
    }

    fun autoPlayChanged(enabled: Boolean, at: Double) {
        autoPlayEnabled = enabled
        if (phase != GrassTimerPhase.Idle || isFingerDown || quietMode) {
            if (!enabled) {
                demoOrigin = null
                resumeDemoAfterReturn = false
            } else if (!quietMode && phase in setOf(
                    GrassTimerPhase.Morphing,
                    GrassTimerPhase.Active,
                    GrassTimerPhase.Returning,
                )
            ) {
                interruptedAt = at
            }
            return
        }
        demoOrigin = if (enabled) at else null
        interruptedAt = null
    }

    fun selectCard(index: Int, at: Double): Boolean {
        if (phase != GrassTimerPhase.Idle) return false
        val next = index.coerceIn(0, 1)
        if (selectedCard == next) return false
        selectedCard = next
        interruptDemo(at)
        return true
    }

    fun beginCarousel(at: Double): Boolean {
        if (!carouselIsAvailable(at) || isFingerDown) return false
        carouselOffset = carouselValue(at)
        carouselSpring = null
        isCarouselDragging = true
        interruptDemo(at)
        return true
    }

    fun updateCarousel(translation: Double) {
        if (!isCarouselDragging) return
        val spacing = GrassTimerTiming.CarouselSpacing
        carouselOffset = when (selectedCard) {
            0 -> when {
                translation > 0 -> rubberBand(translation)
                translation < -spacing -> -spacing + rubberBand(translation + spacing)
                else -> translation
            }
            else -> when {
                translation < 0 -> rubberBand(translation)
                translation > spacing -> spacing + rubberBand(translation - spacing)
                else -> translation
            }
        }
    }

    fun endCarousel(translation: Double, predictedTranslation: Double, at: Double) {
        if (!isCarouselDragging) return
        updateCarousel(translation)
        isCarouselDragging = false
        interruptedAt = at
        val spacing = GrassTimerTiming.CarouselSpacing
        val commits = if (selectedCard == 0) {
            predictedTranslation < -spacing * 0.45 || translation < -spacing * 0.5
        } else {
            predictedTranslation > spacing * 0.45 || translation > spacing * 0.5
        }
        val target = if (commits) if (selectedCard == 0) -spacing else spacing else 0.0
        val velocity = GrassTimerMath.clamp(
            (predictedTranslation - translation) / 0.20,
            -1_600.0,
            1_600.0,
        )
        carouselSpring = CarouselSpring(carouselOffset, target, velocity, at)
    }

    fun cancelCarousel(at: Double) {
        if (!isCarouselDragging) return
        isCarouselDragging = false
        interruptedAt = at
        carouselSpring = CarouselSpring(carouselOffset, 0.0, 0.0, at)
    }

    /** Scripted movement freezes in the background; a running manual timer keeps real elapsed time. */
    fun scenePhaseChanged(isActive: Boolean, at: Double) {
        if (!isActive) {
            if (isFingerDown || phase == GrassTimerPhase.Pressing) cancelPress(at)
            if (isCarouselDragging) cancelCarousel(at)
            if (suspendedAt == null) suspendedAt = at
            return
        }
        val suspended = suspendedAt ?: return
        val pause = max(0.0, at - suspended)
        suspendedAt = null
        demoOrigin = demoOrigin?.plus(pause)
        interruptedAt = interruptedAt?.plus(pause)
        if (phase != GrassTimerPhase.Active) {
            transition = transition.copy(startedAt = transition.startedAt + pause)
            holdStartedAt = holdStartedAt?.plus(pause)
            releaseStartedAt = releaseStartedAt?.plus(pause)
            activeStartedAt = activeStartedAt?.plus(pause)
            carouselSpring = carouselSpring?.let { it.copy(startedAt = it.startedAt + pause) }
        }
    }

    fun setQuiet(quiet: Boolean, autoPlay: Boolean, at: Double) {
        if (quietMode == quiet) return
        autoPlayEnabled = autoPlay
        if (quiet) {
            if (isFingerDown || phase == GrassTimerPhase.Pressing) cancelPress(at)
            if (isCarouselDragging) cancelCarousel(at)
            releaseStartedAt = null
            releaseProgress = 0.0
            demoOrigin = null
            interruptedAt = null
            resumeDemoAfterReturn = false
            val current = morph(at)
            when (phase) {
                GrassTimerPhase.Morphing -> {
                    transition = Transition(current, 1.0, at, GrassTimerTiming.ReducedFadeDuration) { it }
                    activeStartedAt = at + GrassTimerTiming.ReducedFadeDuration
                }
                GrassTimerPhase.Returning -> {
                    transition = Transition(current, 0.0, at, GrassTimerTiming.ReducedFadeDuration) { it }
                }
                else -> Unit
            }
        } else if (autoPlay) {
            if (phase == GrassTimerPhase.Idle) {
                demoOrigin = at
                interruptedAt = null
            } else if (phase in setOf(
                    GrassTimerPhase.Morphing,
                    GrassTimerPhase.Active,
                    GrassTimerPhase.Returning,
                )
            ) {
                interruptedAt = at
            }
        }
        quietMode = quiet
    }

    fun beginPress(at: Double, reducedMotion: Boolean) {
        if (isFingerDown) return
        val current = presentationMorph(at)
        isFingerDown = true
        interruptDemo(at)
        if (phase != GrassTimerPhase.Idle && phase != GrassTimerPhase.Returning) {
            isFingerDown = false
            return
        }
        transition = Transition(current, current, at, 0.001) { it }
        holdStartedAt = at
        releaseStartedAt = null
        phase = GrassTimerPhase.Pressing
    }

    fun endPress(at: Double, reducedMotion: Boolean) {
        if (!isFingerDown) return
        isFingerDown = false
        if (reducedMotion) {
            if (phase == GrassTimerPhase.Pressing) activate(at, reducedMotion = true)
            return
        }
        val holdStart = holdStartedAt
        if (phase != GrassTimerPhase.Pressing || holdStart == null) {
            if (autoPlayEnabled && (phase == GrassTimerPhase.Morphing || phase == GrassTimerPhase.Active)) {
                interruptedAt = at
            }
            return
        }
        val elapsed = max(0.0, at - holdStart)
        when {
            elapsed <= GrassTimerTiming.QuickTapDuration -> activate(at, reducedMotion = false)
            elapsed >= GrassTimerTiming.HoldDuration -> commitHold(at)
            else -> cancelPress(at)
        }
    }

    fun cancelPress(at: Double) {
        isFingerDown = false
        if (phase != GrassTimerPhase.Pressing) return
        val progress = pressProgress(at)
        val current = morph(at)
        holdStartedAt = null
        releaseProgress = progress
        releaseStartedAt = at
        transition = Transition(
            current,
            0.0,
            at,
            max(0.12, current * GrassTimerTiming.ReturnDuration),
            GrassTimerMath::strongOut,
        )
        phase = if (current > 0.001) GrassTimerPhase.Returning else GrassTimerPhase.Idle
    }

    fun activate(at: Double, reducedMotion: Boolean): Boolean {
        if (phase == GrassTimerPhase.Active || phase == GrassTimerPhase.Morphing) return false
        val current = presentationMorph(at)
        isFingerDown = false
        interruptDemo(at)
        holdStartedAt = null
        releaseStartedAt = null
        val duration = if (reducedMotion) {
            GrassTimerTiming.ReducedFadeDuration
        } else {
            max(0.12, (1 - current) * GrassTimerTiming.MorphDuration)
        }
        transition = Transition(
            current,
            1.0,
            at,
            duration,
            if (reducedMotion) ({ value -> value }) else GrassTimerMath::strongInOut,
        )
        activeStartedAt = at + duration
        phase = if (reducedMotion) GrassTimerPhase.Active else GrassTimerPhase.Morphing
        activationSequence += 1
        return true
    }

    fun stop(at: Double, reducedMotion: Boolean) {
        val current = presentationMorph(at)
        val visibleDemo = autoPlayEnabled && !quietMode && phase == GrassTimerPhase.Idle &&
            demoOrigin != null && interruptedAt == null && current > 0.001
        if (phase != GrassTimerPhase.Active && phase != GrassTimerPhase.Morphing && !visibleDemo) return
        isFingerDown = false
        interruptDemo(at)
        activeStartedAt = null
        transition = Transition(
            current,
            0.0,
            at,
            if (reducedMotion) {
                GrassTimerTiming.ReducedFadeDuration
            } else {
                max(0.12, current * GrassTimerTiming.ReturnDuration)
            },
            if (reducedMotion) ({ value -> value }) else GrassTimerMath::strongOut,
        )
        phase = GrassTimerPhase.Returning
    }

    fun frame(at: Double, autoPlay: Boolean, reducedMotion: Boolean): GrassTimerFrame {
        autoPlayEnabled = autoPlay
        val currentCarouselOffset = carouselValue(at)
        if (autoPlay && !reducedMotion && !quietMode && demoResumeIsDue(at)) {
            when (phase) {
                GrassTimerPhase.Idle -> {
                    demoOrigin = at
                    interruptedAt = null
                }
                GrassTimerPhase.Morphing, GrassTimerPhase.Active -> beginAutomaticDemoReturn(at)
                GrassTimerPhase.Pressing, GrassTimerPhase.Returning -> Unit
            }
        }
        if (autoPlay && !reducedMotion && !quietMode && demoOrigin != null &&
            interruptedAt == null && phase == GrassTimerPhase.Idle
        ) {
            return demoFrame(at - (demoOrigin ?: at), selectedCard)
        }

        if (phase == GrassTimerPhase.Pressing && !reducedMotion && !quietMode && pressProgress(at) >= 1) {
            commitHold(at)
        }
        val progress = morph(at)
        if (phase == GrassTimerPhase.Morphing && progress >= 0.999) phase = GrassTimerPhase.Active
        if (phase == GrassTimerPhase.Returning && progress <= 0.001) {
            phase = GrassTimerPhase.Idle
            if (resumeDemoAfterReturn && autoPlay && !reducedMotion && !quietMode) {
                resumeDemoAfterReturn = false
                demoOrigin = at
                interruptedAt = null
                return demoFrame(0.0, selectedCard)
            }
        }

        val press = pressProgress(at)
        val timerTime = activeStartedAt?.let { max(0.0, at - it) } ?: 0.0
        return makeFrame(
            phase = phase,
            selectedCard = selectedCard,
            morph = if (reducedMotion) 0.0 else progress,
            press = if (reducedMotion) 0.0 else press,
            timerVisible = if (reducedMotion) progress else timerVisibility(progress),
            elapsed = timerTime,
            landing = if (phase == GrassTimerPhase.Active) max(0.0, 1 - timerTime / 0.18) else 0.0,
            activationSequence = activationSequence,
            carouselOffset = currentCarouselOffset,
            selectionSequence = selectionSequence,
        )
    }

    fun clockCadence(at: Double, autoPlay: Boolean, reducedMotion: Boolean): GrassTimerCadence {
        if (suspendedAt != null) return GrassTimerCadence.Stopped
        if (reducedMotion && phase == GrassTimerPhase.Pressing) return GrassTimerCadence.Stopped
        if (isCarouselDragging || carouselSpring != null) return GrassTimerCadence.Animation
        val demoRunning = autoPlay && !reducedMotion && !quietMode && phase == GrassTimerPhase.Idle &&
            (demoOrigin != null || interruptedAt != null)
        if (demoRunning) return GrassTimerCadence.Animation
        val demoWaiting = autoPlay && !reducedMotion && !quietMode && interruptedAt != null
        if (demoWaiting) return GrassTimerCadence.Animation
        if (phase == GrassTimerPhase.Pressing || phase == GrassTimerPhase.Morphing ||
            phase == GrassTimerPhase.Returning || isFingerDown || releaseStartedAt != null ||
            !transition.isFinished(at)
        ) {
            return GrassTimerCadence.Animation
        }
        if (phase == GrassTimerPhase.Active) {
            if (activeStartedAt?.let { at - it < 0.18 } == true) return GrassTimerCadence.Animation
            return if (remainingSeconds(at) > 0) GrassTimerCadence.Seconds else GrassTimerCadence.Stopped
        }
        return GrassTimerCadence.Stopped
    }

    private fun commitHold(at: Double) {
        if (phase != GrassTimerPhase.Pressing) return
        val held = isFingerDown
        activate(at, reducedMotion = false)
        isFingerDown = held
    }

    private fun presentationMorph(at: Double): Double {
        val origin = demoOrigin
        if (autoPlayEnabled && !quietMode && origin != null && interruptedAt == null &&
            phase == GrassTimerPhase.Idle
        ) {
            return demoFrame(at - origin, selectedCard).morph
        }
        return morph(at)
    }

    private fun carouselIsAvailable(at: Double): Boolean {
        if (phase != GrassTimerPhase.Idle) return false
        val origin = demoOrigin
        if (autoPlayEnabled && !quietMode && origin != null && interruptedAt == null) {
            val frame = demoFrame(at - origin, selectedCard)
            return frame.phase == GrassTimerPhase.Idle && frame.morph <= 0.001
        }
        return morph(at) <= 0.001
    }

    private fun morph(at: Double): Double = GrassTimerMath.clamp(transition.value(at))

    private fun pressProgress(at: Double): Double {
        holdStartedAt?.let { return GrassTimerMath.clamp((at - it) / GrassTimerTiming.HoldDuration) }
        releaseStartedAt?.let { released ->
            val value = GrassTimerMath.springBack(at - released, releaseProgress)
            if (at - released > 0.8) releaseStartedAt = null
            return value
        }
        return 0.0
    }

    private fun interruptDemo(at: Double) {
        demoOrigin = null
        interruptedAt = at
        resumeDemoAfterReturn = false
    }

    private fun demoResumeIsDue(at: Double): Boolean {
        val interrupted = interruptedAt ?: return false
        return !isFingerDown && !isCarouselDragging && at - interrupted >= 2
    }

    private fun carouselValue(at: Double): Double {
        val spring = carouselSpring ?: return carouselOffset
        val value = spring.value(at)
        val spacing = GrassTimerTiming.CarouselSpacing
        val crossedLead = if (selectedCard == 0) {
            spring.to < 0 && value <= -spacing
        } else {
            spring.to > 0 && value >= spacing
        }
        if (crossedLead) {
            val shifted = value + if (selectedCard == 0) spacing else -spacing
            val velocity = spring.velocity(at)
            selectedCard = if (selectedCard == 0) 1 else 0
            selectionSequence += 1
            carouselOffset = shifted
            carouselSpring = CarouselSpring(shifted, 0.0, velocity, at)
            return shifted
        }
        if (at - spring.startedAt >= GrassTimerTiming.CarouselSettleDuration) {
            if (abs(spring.to) > 0.5) {
                selectedCard = if (selectedCard == 0) 1 else 0
                selectionSequence += 1
            }
            carouselOffset = 0.0
            carouselSpring = null
            return 0.0
        }
        carouselOffset = value
        return value
    }

    private fun rubberBand(value: Double): Double {
        val magnitude = abs(value)
        val bent = magnitude * 0.42 / (1 + magnitude / 120)
        return if (value < 0) -bent else bent
    }

    private fun beginAutomaticDemoReturn(at: Double) {
        val current = morph(at)
        activeStartedAt = null
        interruptedAt = null
        demoOrigin = null
        resumeDemoAfterReturn = true
        transition = Transition(
            current,
            0.0,
            at,
            max(0.12, current * GrassTimerTiming.ReturnDuration),
            GrassTimerMath::strongOut,
        )
        phase = GrassTimerPhase.Returning
    }

    private fun remainingSeconds(at: Double): Int {
        val elapsed = activeStartedAt?.let { max(0.0, at - it) } ?: 0.0
        val duration = if (selectedCard == 0) GrassTimerTiming.GrassDuration else GrassTimerTiming.QuietDuration
        return max(0, duration - floor(elapsed).toInt())
    }

    companion object {
        fun demoFrame(elapsed: Double, selectedCard: Int = 0): GrassTimerFrame {
            val remainder = elapsed % GrassTimerTiming.Loop
            val time = if (remainder >= 0) remainder else remainder + GrassTimerTiming.Loop
            val press: Double
            val morph: Double
            val phase: GrassTimerPhase
            val elapsedActive: Double
            when {
                time < 1.6 -> {
                    press = 0.0
                    morph = 0.0
                    phase = GrassTimerPhase.Idle
                    elapsedActive = 0.0
                }
                time < 1.72 -> {
                    press = 0.0
                    morph = 0.0
                    phase = GrassTimerPhase.Pressing
                    elapsedActive = 0.0
                }
                time < 2.25 -> {
                    press = (time - 1.72) / 0.53
                    morph = 0.0
                    phase = GrassTimerPhase.Pressing
                    elapsedActive = 0.0
                }
                time < 3.05 -> {
                    press = 1.0
                    morph = GrassTimerMath.strongInOut((time - 2.25) / 0.80)
                    phase = GrassTimerPhase.Morphing
                    elapsedActive = 0.0
                }
                time < 6.45 -> {
                    press = 1.0
                    morph = 1.0
                    phase = GrassTimerPhase.Active
                    elapsedActive = time - 3.05
                }
                time < 7.2 -> {
                    press = 0.0
                    morph = 1 - GrassTimerMath.strongOut((time - 6.45) / 0.75)
                    phase = GrassTimerPhase.Returning
                    elapsedActive = 3.4
                }
                else -> {
                    press = 0.0
                    morph = 0.0
                    phase = GrassTimerPhase.Idle
                    elapsedActive = 0.0
                }
            }
            val anticipation = if (time >= 1.6 && time < 1.72) {
                GrassTimerMath.strongOut((time - 1.6) / 0.12)
            } else {
                0.0
            }
            return makeFrame(
                phase = phase,
                selectedCard = selectedCard,
                morph = morph,
                press = press,
                timerVisible = timerVisibility(morph),
                elapsed = elapsedActive,
                landing = if (phase == GrassTimerPhase.Active) max(0.0, 1 - elapsedActive / 0.18) else 0.0,
                activationSequence = 0,
                carouselOffset = 0.0,
                selectionSequence = 0,
            ).copy(chipScale = 1 - anticipation * 0.03)
        }

        private fun timerVisibility(morph: Double): Double =
            GrassTimerMath.clamp((morph - 0.60) / 0.40)

        private fun makeFrame(
            phase: GrassTimerPhase,
            selectedCard: Int,
            morph: Double,
            press: Double,
            timerVisible: Double,
            elapsed: Double,
            landing: Double,
            activationSequence: Int,
            carouselOffset: Double,
            selectionSequence: Int,
        ): GrassTimerFrame {
            val progress = GrassTimerMath.clamp(morph)
            val duration = if (selectedCard == 0) {
                GrassTimerTiming.GrassDuration
            } else {
                GrassTimerTiming.QuietDuration
            }
            val remaining = max(0, duration - floor(elapsed).toInt())
            val remainingFraction = max(0.0, 1 - elapsed / duration)
            return GrassTimerFrame(
                phase = phase,
                selectedCard = selectedCard,
                aperture = GrassTimerTiming.RestingCard.interpolate(
                    GrassTimerTiming.TimerAperture,
                    progress,
                ),
                morph = progress,
                pressProgress = GrassTimerMath.clamp(press),
                chipScale = 0.97 + 0.03 * (1 - GrassTimerMath.clamp(press)),
                imageParallax = -4 * GrassTimerMath.clamp(press),
                secondCardOffset = 160 * progress,
                secondCardOpacity = 1 - progress,
                copyOpacity = (1 - GrassTimerMath.clamp(progress / 0.40)) * (1 - timerVisible),
                timerOpacity = timerVisible,
                ringOpacity = if (progress == 0.0) 0.0 else timerVisible,
                ringProgress = if (progress == 0.0) 0.0 else remainingFraction,
                landingHighlightOpacity = landing,
                remainingSeconds = remaining,
                activationSequence = activationSequence,
                carouselOffset = carouselOffset,
                neighborCardX = (if (selectedCard == 0) 263.0 else -235.0) +
                    carouselOffset + (if (selectedCard == 0) 1 else -1) * 160 * progress,
                selectionSequence = selectionSequence,
            )
        }
    }
}

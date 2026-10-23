package com.ngocdevv.nativecook.blocks.sheets.eventpassshare

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.sin

/** Pure clock-driven state for the pass, deterministic demo, and lifecycle suspension. */
internal class EventPassShareController {
    private data class AngleTransition(
        val from: Double,
        val to: Double,
        val startedAt: Double,
        val duration: Double,
    ) {
        fun value(at: Double): Double {
            val progress = EventPassMath.strongInOut((at - startedAt) / max(0.001, duration))
            return EventPassMath.lerp(from, to, progress)
        }

        fun isFinished(at: Double): Boolean = at - startedAt >= duration
    }

    private data class TiltSpring(
        val x: Double,
        val y: Double,
        val velocityX: Double,
        val velocityY: Double,
        val startedAt: Double,
    ) {
        fun value(at: Double): Pair<Double, Double> {
            val elapsed = max(0.0, at - startedAt)
            return EventPassMath.spring(elapsed, x, velocityX) to
                EventPassMath.spring(elapsed, y, velocityY)
        }
    }

    var phase = EventPassPhase.Front
        private set
    var isDragging = false
        private set

    private var autoPlayEnabled = false
    private var quietMode = false
    private var isForeground = true
    private var isVisible = true
    private var sharePresented = false
    private var suspendedAt: Double? = null
    private var demoOrigin: Double? = null
    private var interruptedAt: Double? = null
    private var resumeDemoAfterFront = false
    private var pendingDemoRestart = false

    private var angle = 0.0
    private var tiltX = 0.0
    private var tiltY = 0.0
    private var angleTransition: AngleTransition? = null
    private var interruptedTurnTarget: Double? = null
    private var tiltSpring: TiltSpring? = null
    private var touchStartedAt: Double? = null
    private var touchDown = false
    private var interactionMaxDistance = 0.0
    private var pressReleaseAt: Double? = null
    private var pressReleaseDepth = 0.0

    private var quietBack = false
    private var quietMix = 0.0
    private var quietFrom = 0.0
    private var quietTo = 0.0
    private var quietStartedAt: Double? = null
    private var copyConfirmedUntil: Double? = null

    fun appear(autoPlay: Boolean, quiet: Boolean, at: Double) {
        autoPlayEnabled = autoPlay
        quietMode = quiet
        quietMix = if (quietBack) 1.0 else 0.0
        demoOrigin = if (autoPlay && !quiet) at else null
    }

    fun setAutoPlay(enabled: Boolean, at: Double) {
        if (autoPlayEnabled == enabled) return
        autoPlayEnabled = enabled
        if (!enabled) {
            demoOrigin = null
            interruptedAt = null
            resumeDemoAfterFront = false
            pendingDemoRestart = false
        } else if (!quietMode) {
            if (isRunning) {
                if (phase == EventPassPhase.Front && abs(angle) < 0.001 && !isDragging) {
                    demoOrigin = at
                } else {
                    interruptedAt = at
                }
            } else {
                pendingDemoRestart = true
            }
        }
    }

    fun setQuiet(quiet: Boolean, at: Double) {
        if (quietMode == quiet) return
        val current = frame(at)
        quietMode = quiet
        demoOrigin = null
        interruptedAt = null
        resumeDemoAfterFront = false
        pendingDemoRestart = false
        touchDown = false
        isDragging = false
        tiltSpring = null
        tiltX = 0.0
        tiltY = 0.0
        angleTransition = null
        if (quiet) {
            quietBack = current.flipAngle >= 90 || current.backOpacity > current.frontOpacity
            quietMix = if (quietBack) 1.0 else 0.0
            angle = if (quietBack) 180.0 else 0.0
            phase = if (quietBack) EventPassPhase.Back else EventPassPhase.Front
        } else {
            angle = if (quietBack) 180.0 else 0.0
            phase = if (quietBack) EventPassPhase.Back else EventPassPhase.Front
            quietMix = if (quietBack) 1.0 else 0.0
            if (autoPlayEnabled) {
                if (isRunning) interruptedAt = at else pendingDemoRestart = true
            }
        }
    }

    fun setForeground(foreground: Boolean, at: Double) {
        if (isForeground == foreground) return
        isForeground = foreground
        updateSuspension(at)
    }

    fun setSharePresented(presented: Boolean, at: Double) {
        if (sharePresented == presented) return
        sharePresented = presented
        updateSuspension(at)
    }

    fun setVisible(visible: Boolean, at: Double) {
        if (isVisible == visible) return
        isVisible = visible
        updateSuspension(at)
    }

    fun beginInteraction(at: Double): Boolean {
        if (!isRunning) return false
        val visible = frame(at)
        angle = visible.flipAngle
        tiltX = visible.tiltX
        tiltY = visible.tiltY
        demoOrigin = null
        interruptedAt = null
        resumeDemoAfterFront = false
        interruptedTurnTarget = angleTransition?.to
        angleTransition = null
        tiltSpring = null
        isDragging = true
        touchDown = true
        touchStartedAt = at
        pressReleaseAt = null
        interactionMaxDistance = 0.0
        phase = EventPassPhase.Dragging
        return true
    }

    fun updateInteraction(horizontal: Double, vertical: Double) {
        if (!isDragging) return
        interactionMaxDistance = max(interactionMaxDistance, hypot(horizontal, vertical))
        tiltY = EventPassMath.clamp(horizontal / 269 * 10, -10.0, 10.0)
        tiltX = EventPassMath.clamp(-vertical / 381 * 8, -8.0, 8.0)
    }

    fun endInteraction(
        horizontal: Double,
        vertical: Double,
        predictedHorizontal: Double,
        predictedVertical: Double,
        at: Double,
        quiet: Boolean,
    ): Boolean {
        if (!isDragging) return false
        updateInteraction(horizontal, vertical)
        isDragging = false
        releasePress(at)
        interruptedAt = at
        val wasTap = interactionMaxDistance < 8
        interactionMaxDistance = 0.0
        if (wasTap) {
            tiltX = 0.0
            tiltY = 0.0
            toggleFace(at, quiet)
            return true
        }
        interruptedTurnTarget = null
        val velocityX = EventPassMath.clamp(
            (-(predictedVertical - vertical) / 0.20) / 381 * 8,
            -80.0,
            80.0,
        )
        val velocityY = EventPassMath.clamp(
            ((predictedHorizontal - horizontal) / 0.20) / 269 * 10,
            -100.0,
            100.0,
        )
        tiltSpring = TiltSpring(tiltX, tiltY, velocityX, velocityY, at)
        startTurn(if (angle >= 90) 180.0 else 0.0, at)
        phase = EventPassPhase.Settling
        return false
    }

    fun cancelInteraction(at: Double) {
        if (!isDragging && !touchDown) return
        isDragging = false
        releasePress(at)
        interactionMaxDistance = 0.0
        interruptedAt = at
        tiltSpring = TiltSpring(tiltX, tiltY, 0.0, 0.0, at)
        interruptedTurnTarget = null
        startTurn(if (angle >= 90) 180.0 else 0.0, at)
        phase = EventPassPhase.Settling
    }

    fun toggleFace(at: Double, quiet: Boolean) {
        interruptedAt = at
        demoOrigin = null
        resumeDemoAfterFront = false
        if (quiet) {
            quietMode = true
            val current = quietMixValue(at)
            quietBack = !quietBack
            quietFrom = current
            quietTo = if (quietBack) 1.0 else 0.0
            quietStartedAt = at
            angle = if (quietBack) 180.0 else 0.0
            phase = if (quietBack) EventPassPhase.Back else EventPassPhase.Front
            return
        }
        val current = angleValue(at)
        angle = current
        val interruptedTarget = interruptedTurnTarget
        val transition = angleTransition
        val target = when {
            interruptedTarget != null -> if (interruptedTarget >= 90) 0.0 else 180.0
            transition != null -> if (transition.to >= 90) 0.0 else 180.0
            current >= 90 -> 0.0
            else -> 180.0
        }
        interruptedTurnTarget = null
        startTurn(target, at)
        phase = if (target == 180.0) EventPassPhase.TurningBack else EventPassPhase.TurningFront
    }

    fun confirmCopy(at: Double) {
        copyConfirmedUntil = at + EventPassTiming.CopyConfirmationDuration
    }

    fun frame(at: Double): EventPassFrame {
        val suspended = suspendedAt
        if (suspended != null) return makeFrame(suspended)
        advanceState(at)
        return makeFrame(at)
    }

    fun clockCadence(at: Double): EventPassCadence {
        if (!isRunning) return EventPassCadence.Stopped
        if (autoPlayEnabled && !quietMode && (demoOrigin != null || interruptedAt != null)) {
            return EventPassCadence.Animation
        }
        if (isDragging || touchDown || angleTransition != null || tiltSpring != null || quietStartedAt != null) {
            return EventPassCadence.Animation
        }
        // Keep one final sample after expiry so the published semantic state returns to "Copy".
        if (copyConfirmedUntil != null) return EventPassCadence.Animation
        return EventPassCadence.Stopped
    }

    private val isRunning: Boolean
        get() = isForeground && isVisible && !sharePresented

    private fun advanceState(at: Double) {
        if (copyConfirmedUntil?.let { at >= it } == true) copyConfirmedUntil = null
        quietStartedAt?.let { start ->
            if (at - start >= EventPassTiming.QuietFadeDuration) {
                quietMix = quietTo
                quietStartedAt = null
            }
        }
        tiltSpring?.let { spring ->
            val settled = spring.value(at)
            tiltX = settled.first
            tiltY = settled.second
            if (at - spring.startedAt >= EventPassTiming.SettleDuration) {
                tiltX = 0.0
                tiltY = 0.0
                tiltSpring = null
            }
        }
        angleTransition?.let { transition ->
            angle = transition.value(at)
            if (transition.isFinished(at)) {
                angle = transition.to
                angleTransition = null
            }
        }
        when {
            phase == EventPassPhase.Settling && tiltSpring == null && angleTransition == null -> {
                phase = if (angle >= 90) EventPassPhase.Back else EventPassPhase.Front
            }
            phase == EventPassPhase.TurningBack && angleTransition == null -> phase = EventPassPhase.Back
            phase == EventPassPhase.TurningFront && angleTransition == null -> {
                phase = EventPassPhase.Front
                if (resumeDemoAfterFront && autoPlayEnabled && !quietMode) {
                    resumeDemoAfterFront = false
                    interruptedAt = null
                    demoOrigin = at
                }
            }
        }
        val interrupted = interruptedAt
        if (autoPlayEnabled && !quietMode && !isDragging && interrupted != null &&
            at - interrupted >= EventPassTiming.ResumeDelay
        ) {
            if (phase == EventPassPhase.Front && angle < 0.001 && tiltSpring == null && angleTransition == null) {
                interruptedAt = null
                demoOrigin = at
            } else if (angleTransition == null) {
                resumeDemoAfterFront = true
                startTurn(0.0, at)
                phase = EventPassPhase.TurningFront
            }
        }
    }

    private fun makeFrame(at: Double): EventPassFrame {
        val origin = demoOrigin
        if (autoPlayEnabled && !quietMode && origin != null && interruptedAt == null &&
            phase == EventPassPhase.Front
        ) {
            return demoFrame(at - origin).copy(
                copyConfirmed = copyConfirmedUntil?.let { at < it } ?: false,
            )
        }
        val currentAngle = angleValue(at)
        val currentTilt = tiltSpring?.value(at) ?: (tiltX to tiltY)
        val press = pressScale(at)
        val currentQuietMix = quietMixValue(at)
        val frontOpacity = if (quietMode) {
            1 - currentQuietMix
        } else if (currentAngle < 90) {
            1.0
        } else {
            0.0
        }
        val backOpacity = 1 - frontOpacity
        return EventPassFrame(
            phase = phase,
            flipAngle = if (quietMode) if (quietBack) 180.0 else 0.0 else currentAngle,
            tiltX = if (quietMode) 0.0 else currentTilt.first,
            tiltY = if (quietMode) 0.0 else currentTilt.second,
            pressScale = press,
            frontOpacity = frontOpacity,
            backOpacity = backOpacity,
            quietCrossfade = quietMode,
            copyConfirmed = copyConfirmedUntil?.let { at < it } ?: false,
        )
    }

    private fun angleValue(at: Double): Double = angleTransition?.value(at) ?: angle

    private fun quietMixValue(at: Double): Double {
        val start = quietStartedAt ?: return quietMix
        val progress = EventPassMath.clamp((at - start) / EventPassTiming.QuietFadeDuration)
        return EventPassMath.lerp(quietFrom, quietTo, progress)
    }

    private fun pressScale(at: Double): Double {
        val touchStart = touchStartedAt
        if (touchDown && touchStart != null) {
            val depth = 0.03 * EventPassMath.strongOut((at - touchStart) / EventPassTiming.PressDuration)
            return 1 - depth
        }
        val release = pressReleaseAt
        if (release != null) {
            val depth = EventPassMath.pressRelease(at - release, pressReleaseDepth)
            if (at - release >= 0.55) pressReleaseAt = null
            return 1 - depth
        }
        return 1.0
    }

    private fun releasePress(at: Double) {
        val currentScale = pressScale(at)
        touchDown = false
        touchStartedAt = null
        pressReleaseDepth = max(0.0, 1 - currentScale)
        pressReleaseAt = at
    }

    private fun startTurn(target: Double, at: Double) {
        val current = angleValue(at)
        angle = current
        val distance = abs(target - current)
        if (distance <= 0.001) {
            angle = target
            angleTransition = null
            return
        }
        angleTransition = AngleTransition(
            current,
            target,
            at,
            max(0.12, EventPassTiming.FlipDuration * distance / 180),
        )
    }

    private fun updateSuspension(at: Double) {
        if (!isRunning) {
            if (isDragging || touchDown) cancelInteraction(at)
            if (suspendedAt == null) suspendedAt = at
            return
        }
        val suspended = suspendedAt ?: return
        val pause = max(0.0, at - suspended)
        suspendedAt = null
        demoOrigin = demoOrigin?.plus(pause)
        interruptedAt = interruptedAt?.plus(pause)
        touchStartedAt = touchStartedAt?.plus(pause)
        pressReleaseAt = pressReleaseAt?.plus(pause)
        quietStartedAt = quietStartedAt?.plus(pause)
        copyConfirmedUntil = copyConfirmedUntil?.plus(pause)
        angleTransition = angleTransition?.let { it.copy(startedAt = it.startedAt + pause) }
        tiltSpring = tiltSpring?.let { it.copy(startedAt = it.startedAt + pause) }
        if (pendingDemoRestart && autoPlayEnabled && !quietMode) {
            pendingDemoRestart = false
            if (phase == EventPassPhase.Front && abs(angle) < 0.001 && !isDragging &&
                angleTransition == null && tiltSpring == null
            ) {
                interruptedAt = null
                demoOrigin = at
            } else {
                demoOrigin = null
                interruptedAt = at
            }
        }
    }

    companion object {
        fun demoFrame(elapsed: Double): EventPassFrame {
            val remainder = elapsed % EventPassTiming.Loop
            val time = if (remainder >= 0) remainder else remainder + EventPassTiming.Loop
            var angle = 0.0
            var tiltX = 0.0
            var tiltY = 0.0
            var phase = EventPassPhase.Front
            when {
                time >= EventPassDemo.FoilStart && time < EventPassDemo.FoilEnd -> {
                    val progress = (time - EventPassDemo.FoilStart) /
                        (EventPassDemo.FoilEnd - EventPassDemo.FoilStart)
                    val bell = sin(PI * progress)
                    tiltY = EventPassDemo.FoilTiltY * sin(2 * PI * progress) * bell
                    tiltX = EventPassDemo.FoilTiltX * bell * bell
                }
                time >= EventPassDemo.TurnBackStart &&
                    time < EventPassDemo.TurnBackStart + EventPassDemo.TurnDuration -> {
                    angle = 180 * EventPassMath.strongInOut(
                        (time - EventPassDemo.TurnBackStart) / EventPassDemo.TurnDuration,
                    )
                    phase = EventPassPhase.TurningBack
                }
                time >= EventPassDemo.TurnBackStart + EventPassDemo.TurnDuration &&
                    time < EventPassDemo.TurnFrontStart -> {
                    angle = 180.0
                    phase = EventPassPhase.Back
                }
                time >= EventPassDemo.TurnFrontStart &&
                    time < EventPassDemo.TurnFrontStart + EventPassDemo.TurnDuration -> {
                    angle = 180 * (1 - EventPassMath.strongInOut(
                        (time - EventPassDemo.TurnFrontStart) / EventPassDemo.TurnDuration,
                    ))
                    phase = EventPassPhase.TurningFront
                }
                time >= EventPassDemo.NodStart && time < EventPassDemo.NodEnd -> {
                    val bell = sin(
                        PI * (time - EventPassDemo.NodStart) /
                            (EventPassDemo.NodEnd - EventPassDemo.NodStart),
                    )
                    tiltY = EventPassDemo.NodTiltY * bell * bell
                    tiltX = EventPassDemo.NodTiltX * bell * bell
                }
            }
            val front = if (angle < 90) 1.0 else 0.0
            return EventPassFrame(
                phase,
                angle,
                tiltX,
                tiltY,
                pressScale = 1.0,
                frontOpacity = front,
                backOpacity = 1 - front,
                quietCrossfade = false,
                copyConfirmed = false,
            )
        }
    }
}

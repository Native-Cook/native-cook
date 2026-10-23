package com.ngocdevv.nativecook.blocks.gestures.tiltposterparallax

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sign
import kotlin.math.sin

internal class TiltPosterParallaxEngine(
    private val autoPlay: Boolean,
    private val reduceMotion: Boolean,
) {
    var tiltX by mutableFloatStateOf(0f)
        private set
    var tiltY by mutableFloatStateOf(0f)
        private set
    var lift by mutableFloatStateOf(1f)
        private set
    var flip by mutableFloatStateOf(0f)
        private set
    var clockMs by mutableFloatStateOf(0f)
        private set
    var fingerX by mutableFloatStateOf(0f)
        private set
    var fingerY by mutableFloatStateOf(0f)
        private set
    var fingerAlpha by mutableFloatStateOf(0f)
        private set
    var savedMotion by mutableFloatStateOf(0f)
        private set
    var watchPress by mutableFloatStateOf(1f)
        private set
    var saved by mutableStateOf(false)
        private set

    private val flipSpring = TiltPosterParallaxSpring(
        value = 0.0,
        damping = Tokens.SPRING.flipDamping,
        stiffness = Tokens.SPRING.flipStiffness,
        mass = Tokens.SPRING.flipMass,
    )
    private val liftSpring = TiltPosterParallaxSpring(
        value = 1.0,
        damping = Tokens.SPRING.liftDamping,
        stiffness = Tokens.SPRING.liftStiffness,
        mass = Tokens.SPRING.liftMass,
    )
    private val offsetXSpring = TiltPosterParallaxSpring(
        value = 0.0,
        damping = Tokens.SPRING.releaseDamping,
        stiffness = Tokens.SPRING.releaseStiffness,
        mass = Tokens.SPRING.releaseMass,
    )
    private val offsetYSpring = TiltPosterParallaxSpring(
        value = 0.0,
        damping = Tokens.SPRING.releaseDamping,
        stiffness = Tokens.SPRING.releaseStiffness,
        mass = Tokens.SPRING.releaseMass,
    )
    private val savedSpring = TiltPosterParallaxSpring(
        value = 0.0,
        damping = Tokens.SPRING.morphDamping,
        stiffness = Tokens.SPRING.morphStiffness,
        mass = Tokens.SPRING.morphMass,
    )
    private val pressSpring = TiltPosterParallaxSpring(
        value = 1.0,
        damping = Tokens.SPRING.pressDamping,
        stiffness = Tokens.SPRING.pressStiffness,
        mass = Tokens.SPRING.pressMass,
    )

    private var halfWidth = 1.0
    private var halfHeight = 1.0
    private var watchX = 0.0
    private var watchY = 0.0
    private var ambientX = 0.0
    private var ambientY = 0.0
    private var ambientClockMs = 0.0
    private var sensorRoll = 0.0
    private var sensorPitch = 0.0
    private var baseRoll = 0.0
    private var basePitch = 0.0
    private var sensorBased = false
    private var holdActive = false
    private var holdScripted = false
    private var holdX = 0.0
    private var holdY = 0.0
    private var holdOriginX = 0.0
    private var holdOriginY = 0.0
    private var holdStartX = 0.0
    private var holdStartY = 0.0
    private var holdTravel = 0.0
    private var holdAtMs = 0.0
    private var holdAtEdge = false
    private var flipped = false
    private var face = 0
    private var scriptDown = false
    private var paused = false
    private var realDown = false
    private var quietMs = 0.0
    private var previousFrameNanos = 0L
    private var cycleStartNanos = 0L
    private var previousScriptMs = 0.0
    private var reducedFlipFrom = 0.0
    private var reducedFlipElapsedMs = Tokens.TIMING.reducedMs
    private var reducedOffsetFromX = 0.0
    private var reducedOffsetFromY = 0.0
    private var reducedOffsetElapsedMs = Tokens.TIMING.reducedMs

    fun setGeometry(cardWidth: Double, cardHeight: Double, watchPointX: Double, watchPointY: Double) {
        halfWidth = max(1.0, cardWidth / 2.0)
        halfHeight = max(1.0, cardHeight / 2.0)
        watchX = watchPointX
        watchY = watchPointY
    }

    fun updateSensor(roll: Double, pitch: Double) {
        sensorRoll = roll
        sensorPitch = pitch
    }

    fun realTouchDown() {
        if (scriptDown) {
            endHold(0.0, 0.0, clockMs.toDouble())
        }
        paused = true
        realDown = true
        quietMs = 0.0
        scriptDown = false
        fingerAlpha = 0f
    }

    fun realTouchUp() {
        realDown = false
        quietMs = 0.0
    }

    fun beginHold(x: Double, y: Double, eventTimeMs: Double, scripted: Boolean) {
        val currentX = tiltX.toDouble() - ambientX
        val currentY = tiltY.toDouble() - ambientY
        holdAtMs = eventTimeMs
        holdActive = true
        holdScripted = scripted
        holdOriginX = x - currentX / Tokens.TILT.perHalfCard * halfWidth
        holdOriginY = y - currentY / Tokens.TILT.perHalfCard * halfHeight
        holdStartX = x
        holdStartY = y
        holdTravel = 0.0
        holdAtEdge = false
        holdX = currentX
        holdY = currentY
        offsetXSpring.snap(0.0)
        offsetYSpring.snap(0.0)
        if (!reduceMotion) {
            liftSpring.retarget(Tokens.TILT.lift)
        }
    }

    fun moveHold(x: Double, y: Double): Boolean {
        if (!holdActive) {
            return false
        }
        val next = fingerTilt(x - holdOriginX, y - holdOriginY)
        holdX = next.first
        holdY = next.second
        holdTravel = max(holdTravel, hypot(x - holdStartX, y - holdStartY))
        val limit = if (reduceMotion) Tokens.TILT.reducedMax else Tokens.TILT.max
        val atEdge = max(abs(holdX), abs(holdY)) > limit
        val enteredEdge = atEdge && !holdAtEdge && !holdScripted
        holdAtEdge = atEdge
        return enteredEdge
    }

    fun endHold(
        velocityX: Double,
        velocityY: Double,
        eventTimeMs: Double,
        scriptedFlip: Boolean? = null,
    ): Boolean {
        if (!holdActive) {
            return false
        }
        holdActive = false
        val tapped = eventTimeMs - holdAtMs < Tokens.FLIP.tapMs &&
            holdTravel < Tokens.FLIP.slop &&
            abs(velocityX) + abs(velocityY) < Tokens.FLIP.tapSpeed
        if (scriptedFlip != null) {
            setFlip(scriptedFlip)
        } else if (tapped) {
            setFlip(!flipped)
        } else {
            val targetLift = if (flipped && !reduceMotion) Tokens.TILT.lift else 1.0
            liftSpring.retarget(targetLift)
        }
        offsetXSpring.snap(holdX)
        offsetYSpring.snap(holdY)
        if (reduceMotion) {
            reducedOffsetFromX = holdX
            reducedOffsetFromY = holdY
            reducedOffsetElapsedMs = 0.0
        } else {
            val releaseX = velocityX / halfWidth * Tokens.TILT.perHalfCard
            val releaseY = velocityY / halfHeight * Tokens.TILT.perHalfCard
            offsetXSpring.retarget(0.0, releaseX)
            offsetYSpring.retarget(0.0, releaseY)
        }
        return tapped && !holdScripted
    }

    fun toggleFlip() {
        setFlip(!flipped)
    }

    fun setWatchPressed(pressed: Boolean) {
        val target = if (pressed) Tokens.MORPH.pressScale else 1.0
        if (reduceMotion) {
            pressSpring.snap(target)
        } else {
            pressSpring.retarget(target)
        }
    }

    fun toggleSaved(): TiltPosterParallaxHaptic {
        saved = !saved
        val target = if (saved) 1.0 else 0.0
        if (reduceMotion) {
            savedSpring.snap(target)
        } else {
            savedSpring.retarget(target)
        }
        return if (saved) TiltPosterParallaxHaptic.Save else TiltPosterParallaxHaptic.Remove
    }

    fun frame(frameNanos: Long): List<TiltPosterParallaxHaptic> {
        if (previousFrameNanos == 0L) {
            previousFrameNanos = frameNanos
            cycleStartNanos = frameNanos
            return emptyList()
        }
        val rawFrameSeconds = max(0.0, (frameNanos - previousFrameNanos) / 1_000_000_000.0)
        val frameSeconds = min(rawFrameSeconds, Tokens.TIMING.maxFrameSeconds)
        val frameMs = frameSeconds * 1_000.0
        previousFrameNanos = frameNanos
        val haptics = mutableListOf<TiltPosterParallaxHaptic>()

        if (paused) {
            ambientClockMs += frameMs
            if (!realDown) {
                quietMs += frameMs
            }
            if (quietMs >= Tokens.AUTOPLAY.resumeDelayMs) {
                resetLoop(frameNanos)
            }
        } else if (autoPlay) {
            val elapsedMs = (frameNanos - cycleStartNanos) / 1_000_000.0
            var scriptMs = elapsedMs % Tokens.LOOP_MS
            val heldAt = qaHoldTime()
            if (heldAt != null && scriptMs >= heldAt) {
                scriptMs = heldAt
            }
            if (scriptMs < previousScriptMs) {
                resetLoop(frameNanos - (scriptMs * 1_000_000.0).toLong())
            }
            runScript(previousScriptMs, scriptMs, frameMs)
            previousScriptMs = scriptMs
            ambientClockMs = scriptMs
        } else {
            ambientClockMs += frameMs
        }

        updateAmbient()
        stepMotion(frameSeconds, frameMs)
        updateVisibleState()
        val nextFace = if (cos(tiltX + flip * PI.toFloat()) >= 0f) 0 else 1
        if (nextFace != face) {
            face = nextFace
            if (paused || !autoPlay) {
                haptics += TiltPosterParallaxHaptic.Face
            }
        }
        return haptics
    }

    private fun setFlip(next: Boolean) {
        flipped = next
        val target = if (next) 1.0 else 0.0
        if (reduceMotion) {
            reducedFlipFrom = flipSpring.value
            reducedFlipElapsedMs = 0.0
            flipSpring.retarget(target)
            liftSpring.snap(1.0)
        } else {
            flipSpring.retarget(target)
            liftSpring.retarget(if (next) Tokens.TILT.lift else 1.0)
        }
    }

    private fun updateAmbient() {
        if (reduceMotion) {
            ambientX = 0.0
            ambientY = 0.0
            clockMs = 0f
            return
        }
        if (!sensorBased && (sensorRoll != 0.0 || sensorPitch != 0.0)) {
            baseRoll = sensorRoll
            basePitch = sensorPitch
            sensorBased = true
        }
        val sensorX = ((sensorRoll - baseRoll) * Tokens.TILT.sensorGain)
            .coerceIn(-Tokens.TILT.sensorMax, Tokens.TILT.sensorMax)
        val sensorY = (-(sensorPitch - basePitch) * Tokens.TILT.sensorGain)
            .coerceIn(-Tokens.TILT.sensorMax, Tokens.TILT.sensorMax)
        val phase = ambientClockMs / Tokens.TILT.driftPeriodMs * PI * 2.0
        val driftX = sin(phase) * Tokens.TILT.driftAmplitude
        val driftY = sin(phase * 2.0) * Tokens.TILT.driftAmplitude * 0.6
        ambientX += (sensorX + driftX - ambientX) * Tokens.TILT.sensorSmoothing
        ambientY += (sensorY + driftY - ambientY) * Tokens.TILT.sensorSmoothing
        clockMs = ambientClockMs.toFloat()
    }

    private fun stepMotion(frameSeconds: Double, frameMs: Double) {
        liftSpring.step(frameSeconds)
        savedSpring.step(frameSeconds)
        pressSpring.step(frameSeconds)
        if (reduceMotion) {
            reducedFlipElapsedMs += frameMs
            val flipProgress = smoothStep(reducedFlipElapsedMs / Tokens.TIMING.reducedMs)
            val flipValue = reducedFlipFrom + (flipSpring.target - reducedFlipFrom) * flipProgress
            if (flipProgress >= 1.0) {
                flipSpring.snap(flipSpring.target)
            } else {
                flipSpring.snap(flipValue)
                flipSpring.retarget(if (flipped) 1.0 else 0.0)
            }
            reducedOffsetElapsedMs += frameMs
            val offsetProgress = smoothStep(reducedOffsetElapsedMs / Tokens.TIMING.reducedMs)
            offsetXSpring.snap(reducedOffsetFromX * (1.0 - offsetProgress))
            offsetYSpring.snap(reducedOffsetFromY * (1.0 - offsetProgress))
        } else {
            flipSpring.step(frameSeconds)
            offsetXSpring.step(frameSeconds)
            offsetYSpring.step(frameSeconds)
        }
    }

    private fun updateVisibleState() {
        val sourceX = if (holdActive) holdX else offsetXSpring.value
        val sourceY = if (holdActive) holdY else offsetYSpring.value
        tiltX = (sourceX + ambientX).toFloat()
        tiltY = (sourceY + ambientY).toFloat()
        flip = flipSpring.value.toFloat()
        lift = liftSpring.value.toFloat()
        savedMotion = savedSpring.value.toFloat()
        watchPress = pressSpring.value.toFloat()
    }

    private fun runScript(previousMs: Double, scriptMs: Double, frameMs: Double) {
        val insideHold = scriptMs >= Tokens.AUTOPLAY.holdStart && scriptMs < Tokens.AUTOPLAY.holdEnd
        if (insideHold && !scriptDown) {
            val point = scriptPath(scriptMs)
            beginHold(halfWidth + point.first, halfHeight + point.second, scriptMs, scripted = true)
            scriptDown = true
        }
        if (insideHold) {
            val point = scriptPath(scriptMs)
            fingerX = (halfWidth + point.first).toFloat()
            fingerY = (halfHeight + point.second).toFloat()
            fingerAlpha = min(1.0, fingerAlpha + frameMs / Tokens.AUTOPLAY.fingerFadeMs).toFloat()
            moveHold(fingerX.toDouble(), fingerY.toDouble())
            return
        }
        if (scriptDown) {
            val before = scriptPath(Tokens.AUTOPLAY.holdEnd - Tokens.AUTOPLAY.velocitySampleMs)
            val after = scriptPath(Tokens.AUTOPLAY.holdEnd)
            moveHold(halfWidth + after.first, halfHeight + after.second)
            val vx = (after.first - before.first) / Tokens.AUTOPLAY.velocitySampleMs * 1_000.0
            val vy = (after.second - before.second) / Tokens.AUTOPLAY.velocitySampleMs * 1_000.0
            endHold(vx, vy, Tokens.AUTOPLAY.holdEnd)
            scriptDown = false
        }

        var hasFingerTarget = false
        Tokens.AUTOPLAY.taps.forEach { tap ->
            val pointX = if (tap.target == "card") halfWidth else watchX
            val pointY = if (tap.target == "card") halfHeight else watchY
            val from = tap.at - Tokens.AUTOPLAY.fingerLeadMs
            val to = tap.at + Tokens.AUTOPLAY.pressMs + Tokens.AUTOPLAY.fingerLeadMs
            if (scriptMs >= from && scriptMs < to) {
                val fadeIn = (scriptMs - from) / Tokens.AUTOPLAY.fingerFadeMs
                val fadeOut = (to - scriptMs) / Tokens.AUTOPLAY.fingerFadeMs
                fingerX = pointX.toFloat()
                fingerY = pointY.toFloat()
                fingerAlpha = min(1.0, min(fadeIn, fadeOut)).toFloat()
                hasFingerTarget = true
            }
            if (tap.target == "card" && crossed(previousMs, scriptMs, tap.at)) {
                beginHold(pointX, pointY, tap.at, scripted = true)
            }
            val upAt = tap.at + Tokens.AUTOPLAY.pressMs
            if (tap.target == "card" && crossed(previousMs, scriptMs, upAt)) {
                endHold(
                    velocityX = 0.0,
                    velocityY = 0.0,
                    eventTimeMs = upAt,
                    scriptedFlip = tap.at == Tokens.AUTOPLAY.taps.first().at,
                )
            }
            if (tap.target == "watch" && crossed(previousMs, scriptMs, tap.at)) {
                toggleSaved()
            }
        }
        if (!hasFingerTarget && fingerAlpha > 0f) {
            fingerAlpha = max(0.0, fingerAlpha - frameMs / Tokens.AUTOPLAY.fingerFadeMs).toFloat()
        }
    }

    private fun scriptPath(scriptMs: Double): Pair<Double, Double> {
        val span = Tokens.AUTOPLAY.holdEnd - Tokens.AUTOPLAY.holdStart
        val unit = ((scriptMs - Tokens.AUTOPLAY.holdStart) / span).coerceIn(0.0, 1.0)
        val ramp = min(1.0, (scriptMs - Tokens.AUTOPLAY.holdStart) / Tokens.AUTOPLAY.ramp)
        val eased = ramp * ramp * (3.0 - 2.0 * ramp)
        val angle = PI * 2.0 * 1.15 * unit
        val x = halfWidth * Tokens.AUTOPLAY.ampX * sin(angle) * eased
        val y = halfHeight * Tokens.AUTOPLAY.ampY * sin(2.0 * angle) * eased
        return x to y
    }

    private fun resetLoop(startNanos: Long) {
        cycleStartNanos = startNanos
        previousScriptMs = 0.0
        ambientClockMs = 0.0
        paused = false
        realDown = false
        quietMs = 0.0
        scriptDown = false
        holdActive = false
        holdScripted = false
        holdX = 0.0
        holdY = 0.0
        flipped = false
        saved = false
        face = 0
        flipSpring.snap(0.0)
        liftSpring.snap(1.0)
        offsetXSpring.snap(0.0)
        offsetYSpring.snap(0.0)
        savedSpring.snap(0.0)
        pressSpring.snap(1.0)
        fingerX = halfWidth.toFloat()
        fingerY = halfHeight.toFloat()
        fingerAlpha = 0f
        updateVisibleState()
    }

    private fun fingerTilt(dx: Double, dy: Double): Pair<Double, Double> {
        val limit = if (reduceMotion) Tokens.TILT.reducedMax else Tokens.TILT.max
        val rubber = if (reduceMotion) 0.0 else Tokens.TILT.rubber
        val x = soften(dx / halfWidth * Tokens.TILT.perHalfCard, limit, rubber)
        val y = soften(dy / halfHeight * Tokens.TILT.perHalfCard, limit, rubber)
        return x to y
    }

    private fun soften(value: Double, limit: Double, rubber: Double): Double {
        if (abs(value) <= limit) {
            return value
        }
        return sign(value) * (limit + (abs(value) - limit) * rubber)
    }

    private fun crossed(previousMs: Double, currentMs: Double, eventMs: Double): Boolean {
        return previousMs < eventMs && currentMs >= eventMs
    }

    private fun smoothStep(value: Double): Double {
        val unit = value.coerceIn(0.0, 1.0)
        return unit * unit * (3.0 - 2.0 * unit)
    }

    private fun qaHoldTime(): Double? {
        return when (Tokens.QA.hold) {
            "rest" -> Tokens.QA.rest
            "a" -> Tokens.QA.a
            "b" -> Tokens.QA.b
            else -> null
        }
    }
}

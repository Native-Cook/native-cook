package com.ngocdevv.nativecook.blocks.cards.tapridepass

import com.ngocdevv.nativecook.blocks.cards.tapridepass.TapRidePassMotion as Motion
import kotlin.math.*

/**
 * Owns autoplay, touch interruption, spring integration and read/print feedback. Positions are
 * points/dp; velocities are points/dp per second, tilt is degrees, clocks are seconds. Modes: 0
 * rest, 1 drag, 2 return, 3 assist, 4 dock, 5 read, 6 close. Lift, ticketProgress, roll and
 * proximity are normalized; tickPhase counts cadence cycles.
 */
internal class TapRidePassDriver {
    // Loop and interaction clocks are seconds; negative timestamps mean no event yet.
    var scripted = false
    var mode = 0
    var loopTime = 0.0
    var breathingTime = 0.0
    var now = 0.0
    var lastTouchTime = -10.0
    var held = false
    var cardX = 0.0
    var cardY = 0.0
    var velocityX = 0.0
    var velocityY = 0.0
    var fingerX = 0.0
    var fingerY = 0.0
    var dragStartX = 0.0
    var dragStartY = 0.0
    var lift = 0.0
    var tiltDegrees = 0.0
    var tiltVelocity = 0.0
    var ticketProgress = 0.0
    var roll = 0.0
    var dockTime = -10.0
    var tapTime = -10.0
    var tapAge = -10.0
    var closeTime = -10.0
    var closeProgress = 0.0
    var tickPhase = 0.0
    var lastTickTime = -10.0
    var proximity = 0.0
    var balance = 18.4
    var reduceMotion = false
    var dragging = false
    var geometry: TapRidePassGeometry? = null
    var cue: (String) -> Unit = {}
    var buzz: (Int) -> Unit = {}
    val balanceAfterFare
        get(): Double {
            val remainingBalance = round((balance - 2.75) * 100) / 100
            return if (remainingBalance < 2.75) remainingBalance + 20 else remainingBalance
        }

    /** Updates the home pose when geometry changes while the card is resting. */
    fun configure(layout: TapRidePassGeometry) {
        if (geometry != layout) {
            geometry = layout
            if (mode == 0) {
                cardX = layout.homeX
                cardY = layout.homeY
            }
        }
    }

    /** Copies the script-controlled pose while retaining the integrated tilt and cadence. */
    fun setPose(pose: Motion.Pose) {
        cardX = pose.cardX
        cardY = pose.cardY
        lift = pose.lift
        ticketProgress = pose.ticketProgress
        roll = pose.roll
        tapAge = pose.tapAge
    }

    /**
     * Advances one frame in seconds. Touch motion integrates at up to 240 spring substeps per
     * second. After 2 s without touch, autoplay rejoins a compatible resting part of the breathing
     * cycle.
     */
    fun step(deltaTime: Double, layout: TapRidePassGeometry, autoplay: Boolean) {
        now += deltaTime
        val previousX = cardX
        if (
            autoplay &&
                !reduceMotion &&
                !scripted &&
                mode == 0 &&
                now - lastTouchTime > 2 &&
                ticketProgress < 0.001
        ) {
            val breathingPhase = breathingTime % 3
            if (breathingPhase < 1.15) {
                loopTime = breathingPhase
                scripted = true
            } else if (breathingPhase >= 2.3) {
                loopTime = breathingPhase + 6
                scripted = true
            }
        }
        if (scripted) {
            loopTime = (loopTime + deltaTime) % 9
            breathingTime = loopTime
            setPose(Motion.script(loopTime, layout))
            velocityX = (cardX - previousX) / deltaTime
        } else {
            breathingTime = (breathingTime + deltaTime) % 9
            val substeps = max(1, ceil(deltaTime * 240).toInt())
            val substepTime = deltaTime / substeps
            repeat(substeps) {
                var targetX = layout.homeX
                var targetY = layout.homeY
                var duration = 0.5
                var ratio = 0.8
                when {
                    mode == 1 -> {
                        val dx = layout.readerX - fingerX
                        val dy = layout.readerY - fingerY
                        val pull = 0.6 * Motion.smooth(150.0, 40.0, hypot(dx, dy)).pow(1.4)
                        targetX = fingerX + dx * pull
                        targetY = fingerY + dy * pull
                        duration = 0.12
                        ratio = 0.95
                    }
                    mode == 3 -> {
                        targetX = layout.readerX
                        targetY = layout.readerY
                        duration = 0.7
                        ratio = 0.88
                    }
                    mode == 4 || (mode == 5 && now - tapTime < 0.43) -> {
                        targetX = layout.readerX
                        targetY = layout.readerY
                        duration = 0.3
                        ratio = 0.66
                    }
                }
                val horizontalSpring =
                    Motion.spring(cardX, velocityX, targetX, duration, ratio, substepTime)
                cardX = horizontalSpring.first
                velocityX = horizontalSpring.second
                val verticalSpring =
                    Motion.spring(cardY, velocityY, targetY, duration, ratio, substepTime)
                cardY = verticalSpring.first
                velocityY = verticalSpring.second
            }
            val targetLift = if (mode == 3 || (held && mode in 0..2)) 1.0 else 0.0
            lift +=
                (targetLift - lift) * (1 - exp(-deltaTime * (if (targetLift > lift) 18 else 12)))
            if (
                (mode == 1 || mode == 3) &&
                    hypot(cardX - layout.readerX, cardY - layout.readerY) < 46
            ) {
                mode = 4
                dockTime = now
                held = false
                buzz(1)
            }
            if (mode == 4 && now - dockTime > 0.55) {
                mode = 5
                tapTime = now
                buzz(2)
                cue("chime")
            }
            if (mode == 5) {
                val timeAfterTap = now - tapTime
                if (timeAfterTap - deltaTime < 0.58 && timeAfterTap >= 0.58) cue("print")
                ticketProgress =
                    if (reduceMotion) {
                        if (timeAfterTap > 0.3) 1.0 else 0.0
                    } else Motion.ticketAfter(timeAfterTap)
                roll = Motion.rollAfter(timeAfterTap)
            }
            if (mode == 6) {
                val closeAmount =
                    Motion.easeInOut(
                        Motion.segment(now - closeTime, 0.0, if (reduceMotion) 0.25 else 0.75)
                    )
                ticketProgress = closeProgress * (1 - closeAmount)
                if (
                    closeAmount >= 1 && abs(cardX - layout.homeX) + abs(cardY - layout.homeY) < 0.4
                ) {
                    mode = 0
                    ticketProgress = 0.0
                    roll = 0.0
                    tapTime = -10.0
                }
            }
            if (
                mode == 2 &&
                    abs(cardX - layout.homeX) + abs(cardY - layout.homeY) < 0.4 &&
                    abs(velocityX) + abs(velocityY) < 4
            ) {
                mode = 0
                cardX = layout.homeX
                cardY = layout.homeY
                velocityX = 0.0
                velocityY = 0.0
            }
            tapAge = if (tapTime > 0) now - tapTime else -10.0
        }
        val tilt =
            Motion.spring(
                tiltDegrees,
                tiltVelocity,
                if (reduceMotion) 0.0 else (velocityX * 0.01).coerceIn(-6.0, 6.0),
                0.5,
                0.7,
                deltaTime,
            )
        tiltDegrees = tilt.first
        tiltVelocity = tilt.second
        val nearness =
            1 -
                min(
                    1.0,
                    hypot(cardX - layout.readerX, cardY - layout.readerY) / layout.homeDistance,
                )
        val sensing = if (scripted) loopTime < 3.12 else mode in 1..4
        proximity = if (sensing) nearness.pow(1.3) else 0.0
        val tickRate =
            if (proximity > 0.05 && !reduceMotion) 1.2 + 7.3 * proximity.pow(1.6) else 0.0
        val previousTick = floor(tickPhase)
        tickPhase += tickRate * deltaTime
        if (floor(tickPhase) != previousTick) {
            lastTickTime = now
            if (!scripted) {
                buzz(0)
                cue("tick")
            }
        }
        if (scripted && loopTime < 0.05) tickPhase = 0.0
    }

    /** Hands the current scripted phase to touch control without resetting the visible pose. */
    fun interrupt() {
        lastTouchTime = now
        if (!scripted) return
        scripted = false
        if (loopTime >= 7.05 && ticketProgress > 0.001) {
            mode = 6
            closeTime = now
            closeProgress = ticketProgress
        } else if (loopTime >= 3.12) {
            mode = 5
            tapTime = now - (loopTime - 3.12)
        } else if (loopTime >= 2.8) {
            mode = 4
            dockTime = now - (loopTime - 2.8) * 0.5
        } else mode = 2
    }

    /**
     * Sends a resting or returning card toward the reader after a tap or accessibility activation.
     */
    fun assist() {
        interrupt()
        if (mode == 0 || mode == 2) mode = 3
    }

    /** Starts the ticket return and applies the fare for a manually completed ride. */
    fun close() {
        if (!scripted) balance = balanceAfterFare
        cue("close")
        interrupt()
        if (mode == 5 || ticketProgress > 0.001) {
            mode = 6
            closeTime = now
            closeProgress = ticketProgress
        }
    }

    /**
     * Captures a new touch; movement is classified as dragging only after the distance threshold.
     */
    fun begin() {
        interrupt()
        held = true
        dragging = false
    }

    /**
     * Follows translation from touch-down in points/dp; magnetic pull is applied during
     * integration.
     */
    fun drag(dx: Double, dy: Double) {
        lastTouchTime = now
        if (!dragging && hypot(dx, dy) >= 3) {
            dragging = true
            if (mode == 0 || mode == 2 || mode == 3) {
                mode = 1
                dragStartX = cardX
                dragStartY = cardY
                fingerX = cardX
                fingerY = cardY
            }
        }
        if (mode == 1) {
            fingerX = dragStartX + dx
            fingerY = dragStartY + dy
        }
    }

    /** Releases a drag toward home, or treats a stationary touch as an assisted tap. */
    fun end() {
        held = false
        lastTouchTime = now
        if (mode == 1) mode = 2
        if (!dragging) assist()
    }
}

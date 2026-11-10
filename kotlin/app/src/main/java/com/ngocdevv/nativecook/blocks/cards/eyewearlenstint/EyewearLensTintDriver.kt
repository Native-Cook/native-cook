package com.ngocdevv.nativecook.blocks.cards.eyewearlenstint

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.PI

/**
 * Owns the loop and touch state without putting a mutable per-frame value in Compose state.
 * [EyewearLensTint] only invalidates the Canvas draw lambda while this driver is running.
 */
internal class EyewearLensTintDriver(
    private val autoRequested: Boolean,
    private val reduced: Boolean,
) {
    val frame = EyewearLensTintMotion.Frame()
    var running: Boolean = autoRequested
        private set

    private var now = 0.0
    private var autoMode = autoRequested
    private var autoStart = 0.0
    private var lastTouch = -100.0
    private var resumeAsked = false
    private var dragging = false

    private var pouring = false
    private var pourStart = -1.0
    private var pourDuration = EyewearLensTintMotion.POUR

    private var glareAnimation = false
    private var glareStart = 0.0
    private var glareFrom = 0.0

    private var glareSpring: EyewearLensTintMotion.Spring? = null

    private var returnCarry = false
    private var xSpring: EyewearLensTintMotion.Spring? = null
    private var ySpring: EyewearLensTintMotion.Spring? = null
    private var carryAt = -1.0
    private var carryFrom = 0.0
    private var carryTarget = 0.0
    private var carryDuration = 0.0
    private var prevX = -100.0
    private var prevY = -100.0

    fun step(deltaSeconds: Double, layout: EyewearLensTintLayout) {
        val dt = min(0.05, deltaSeconds.coerceAtLeast(0.0))
        now += dt
        frame.time = now % 600.0

        if (autoMode) {
            EyewearLensTintMotion.frameAt(
                ((now - autoStart) % EyewearLensTintMotion.LOOP + EyewearLensTintMotion.LOOP) %
                    EyewearLensTintMotion.LOOP,
                frame,
            )
            frame.time = now % 600.0
            if (frame.carry >= 0.0) {
                val u = frame.carry
                frame.carryX = layout.dropX + (layout.pourX(frame.lens) - layout.dropX) * u
                frame.carryY = layout.dropY(frame.carryTint) +
                    (layout.pourY(frame.lens) - layout.dropY(frame.carryTint)) * u - sin(PI * u) * 60.0
                frame.carryOn = 1.0
            } else {
                // Shared values keep the last carried position after a scripted release.
                frame.carryX = prevX
                frame.carryY = prevY
            }
            updateStretch(dt)
            running = true
            return
        }

        frame.carry = -1.0
        frame.finger = 0.0
        if (pouring) {
            val progress = EyewearLensTintMotion.clamp01((now - pourStart) / pourDuration)
            frame.pour = if (reduced && progress > 0.0) 1.0 else progress
            if (progress >= 1.0) {
                frame.from = frame.to
                frame.pour = 0.0
                pouring = false
            }
        }

        if (glareAnimation) {
            val progress = EyewearLensTintMotion.clamp01((now - glareStart) / 0.4)
            frame.glare = glareFrom * (1.0 - progress)
            if (progress >= 1.0) {
                frame.glare = 0.0
                glareAnimation = false
            }
        }

        glareSpring?.let {
            val done = it.step(dt)
            frame.glare = it.value
            if (done) glareSpring = null
        }

        if (carryAt >= 0.0) {
            val p = EyewearLensTintMotion.clamp01((now - carryAt) / carryDuration)
            val eased = if (p < 0.5) 2.0 * p * p else 1.0 - (-2.0 * p + 2.0) * (-2.0 * p + 2.0) / 2.0
            frame.carryOn = carryFrom + (carryTarget - carryFrom) * eased
            if (p >= 1.0) carryAt = -1.0
        }
        xSpring?.let {
            val done = it.step(dt)
            frame.carryX = it.value
            if (done) xSpring = null
        }
        ySpring?.let {
            val done = it.step(dt)
            frame.carryY = it.value
            if (done) { ySpring = null; animateCarry(0.0, 0.14) }
        }
        returnCarry = xSpring != null || ySpring != null
        updateStretch(dt)

        if (autoRequested && !dragging && !pouring && !returnCarry && now - lastTouch >= 2.0) {
            if (!resumeAsked) {
                resumeAsked = true
                animateGlareToZero()
                if (frame.from != 0) startPour(0, 1, haptic = false)
            } else if (
                frame.pour == 0.0 &&
                    frame.from == 0 &&
                    abs(frame.glare) < 0.001 &&
                    now - lastTouch > 3.6
            ) {
                resumeAsked = false
                autoStart = now
                autoMode = true
            }
        }

        running = dragging ||
            pouring ||
            glareAnimation ||
            glareSpring != null ||
            returnCarry ||
            carryAt >= 0.0 ||
            (autoRequested && (!resumeAsked || now - lastTouch < 4.5 || frame.pour > 0.0))
    }

    fun takeOver() {
        lastTouch = now
        resumeAsked = false
        glareAnimation = false
        glareSpring = null
        if (!autoMode) {
            running = true
            return
        }
        autoMode = false
        frame.carry = -1.0
        frame.carryOn = 0.0
        frame.finger = 0.0
        if (frame.pour > 0.0) {
            pouring = true
            pourDuration = EyewearLensTintMotion.POUR
            pourStart = now - frame.pour * pourDuration
        }
        running = true
    }

    fun markTouch() {
        lastTouch = now
        resumeAsked = false
        running = true
    }

    fun beginDrag() {
        takeOver()
        dragging = true
        returnCarry = false
        xSpring = null
        ySpring = null
    }

    fun endDrag() {
        dragging = false
        lastTouch = now
        running = true
    }

    fun setGlare(value: Double) {
        markTouch()
        frame.glare = value.coerceIn(-1.0, 1.0)
    }

    fun springGlare(velocityX: Double) {
        markTouch()
        glareSpring = if (reduced) null else EyewearLensTintMotion.Spring(
            frame.glare, 0.0, if (abs(frame.glare) < 1.0) velocityX / 140.0 else 0.0, 0.9, 0.6,
        )
        if (reduced) frame.glare = 0.0
        glareAnimation = false
        running = true
    }

    fun setCarry(index: Int, x: Double, y: Double, begin: Boolean = false) {
        markTouch()
        frame.manualCarryTint = index
        frame.carryX = x
        frame.carryY = y
        if (begin) animateCarry(1.0, 0.12)
        returnCarry = false
    }

    fun releaseCarry(
        index: Int,
        targetLens: Int?,
        x: Double,
        y: Double,
        targetX: Double,
        targetY: Double,
        velocityX: Double,
        velocityY: Double,
    ) {
        markTouch()
        frame.manualCarryTint = index
        frame.carryX = x
        frame.carryY = y
        if (targetLens != null) {
            animateCarry(0.0, 0.16)
            pourInto(index, targetLens, haptic = true)
        } else {
            if (reduced) {
                frame.carryX = targetX; frame.carryY = targetY; frame.carryOn = 0.0; carryAt = -1.0
            } else {
                returnCarry = true
                xSpring = EyewearLensTintMotion.Spring(x, targetX, velocityX, 0.52, 0.62)
                ySpring = EyewearLensTintMotion.Spring(y, targetY, velocityY, 0.52, 0.62)
            }
            running = true
        }
    }

    fun pourInto(tint: Int, targetLens: Int, haptic: Boolean = true) {
        takeOver()
        startPour(tint, targetLens, haptic)
    }

    private fun startPour(tint: Int, targetLens: Int, haptic: Boolean) {
        val current = if (frame.pour > 0.5) frame.to else frame.from
        frame.from = if (frame.pour > 0.0) current else frame.to
        frame.to = tint
        frame.lens = targetLens
        frame.pour = 0.0
        pouring = true
        pourDuration = if (reduced) 0.25 else EyewearLensTintMotion.POUR
        pourStart = now
        if (haptic) running = true
    }

    fun chooseByAccessibility(delta: Int) {
        val current = if (frame.pour > 0.5) frame.to else frame.from
        pourInto((current + delta + 4) % 4, 1, haptic = true)
    }

    private fun animateGlareToZero() {
        glareAnimation = true
        glareStart = now
        glareFrom = frame.glare
        glareSpring = null
    }

    private fun animateCarry(target: Double, duration: Double) {
        carryFrom = frame.carryOn
        carryTarget = target
        carryDuration = duration
        carryAt = now
        if (reduced) { frame.carryOn = target; carryAt = -1.0 }
    }

    private fun updateStretch(dt: Double) {
        if (dt > 0.0 && !reduced) {
            val vx = (frame.carryX - prevX) / dt
            val vy = (frame.carryY - prevY) / dt
            val speed = hypot(vx, vy)
            val want = if (frame.carryOn > 0.5 && speed < 6000.0) min(0.4, speed / 2400.0) else 0.0
            frame.stretch += (want - frame.stretch) * min(1.0, dt * 16.0)
            if (speed > 60.0) frame.heading = atan2(vy, vx)
        } else if (reduced) frame.stretch = 0.0
        prevX = frame.carryX
        prevY = frame.carryY
    }
}

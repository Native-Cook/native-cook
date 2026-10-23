package com.ngocdevv.nativecook.blocks.animations.prismspheregenerating

import kotlin.math.*

/** One frame clock owns the script, gesture handoff, inertia, and light spring. */
internal class PrismMotion {
    var clock = 0.0
        private set
    var reduced = false
    var autoPlay = false
    var down = false
        private set
    var dragging = false
        private set
    private var previous = 0.0
    private var offset = PrismMath.identity()
    private var vx = 0.0
    private var vy = 0.0
    private var lastX = 0.0
    private var lastY = 0.0
    private var lastTouch = -100.0
    private var heldAt = 0.0
    private var heldCharge = 0.0
    private var relaxing = false
    private var relaxAt = 0.0
    private var relaxFrom = 0.0
    private var burstAt = 0.0
    var burstGain = 1.0
        private set
    private var releaseCharge = 1.0
    private var surgeActive = false
    private var scripted = false
    private var lightX = 0.0
    private var lightY = 0.0
    private var lightMix = 0.0
    private var lightReleaseAt = -1.0
    private var releaseX = 0.0
    private var releaseY = 0.0
    private var releaseMix = 0.0
    var pose = PrismPose.at(0.0)
        private set
    var values = PrismTimeline.at(0.0, PI)
        private set
    var rotation = PrismMath.identity()
        private set
    var light = floatArrayOf(0f, 0f)
        private set
    val burstAge get() = if (reduced || !surgeActive) -1.0 else PrismTimeline.age(clock, burstAt)

    fun frame(unwrapped: Double) {
        val dt = max(0.0, unwrapped - previous)
        clock = PrismTimeline.modulo(unwrapped, 600.0)
        if (down) lastTouch = clock
        if (surgeActive && !scripted && (PrismTimeline.age(clock, burstAt) > 10 || dt > 10)) surgeActive = false
        if (scripted && floor(previous / 10) != floor(unwrapped / 10)) {
            surgeActive = false
            scripted = false
        }
        val crossed = floor((previous - 6.75) / 10) != floor((unwrapped - 6.75) / 10)
        val anticipationAt = floor((unwrapped - 6.75) / 10) * 10 + 6.75
        if (autoPlay && !reduced && crossed && !down && PrismTimeline.sinceTouch(PrismTimeline.modulo(anticipationAt, 600.0), lastTouch) >= 3.5) {
            burstAt = PrismTimeline.modulo(anticipationAt + 0.55, 600.0)
            burstGain = 1.0
            releaseCharge = 1.0
            surgeActive = true
            scripted = true
        }
        if (!down) {
            val speed = hypot(vx, vy)
            if (!reduced && speed >= 0.02) {
                val decay = exp(-dt / 0.85)
                val distance = 0.85 * (1 - decay)
                offset = PrismMath.trackball(offset, vx * distance, vy * distance, 1.0)
                vx = if (speed * decay < 0.02) 0.0 else vx * decay
                vy = if (speed * decay < 0.02) 0.0 else vy * decay
            }
            val since = PrismTimeline.sinceTouch(clock, lastTouch)
            if (autoPlay && !reduced && since >= 3.5) {
                vx = 0.0
                vy = 0.0
                offset = PrismMath.returnToScript(offset, min(dt, since - 3.5))
            }
            if (since >= 10 || dt > 10) lastTouch = -100.0
        }
        if (down && !dragging) heldCharge = PrismTimeline.ease(PrismTimeline.age(clock, heldAt) / 0.55)
        if (relaxing || dragging) {
            heldCharge = relaxFrom * (1 - PrismTimeline.ease(PrismTimeline.age(clock, relaxAt) / 0.2))
            if (heldCharge == 0.0) relaxing = false
        }
        if (!down && lightReleaseAt >= 0) {
            val t = max(0.0, PrismTimeline.age(clock, lightReleaseAt))
            val w = sqrt(120.0 - 81.0)
            val spring = exp(-9 * t) * (cos(w * t) + 9 / w * sin(w * t))
            lightX = releaseX * spring
            lightY = releaseY * spring
            lightMix = releaseMix * spring
        }
        previous = unwrapped
        evaluate()
    }

    private fun evaluate() {
        pose = PrismPose.at(if (reduced) 0.58 else PrismTimeline.modulo(clock, 5.0) / 5)
        val surgeTime = if (surgeActive) PrismTimeline.age(clock, burstAt) + 0.55 else -1.0
        values = PrismTimeline.at(clock, pose.fan, surgeTime, burstGain, reduced,
            if (down || relaxing) heldCharge else -1.0, PrismTimeline.age(clock, heldAt), releaseCharge)
        rotation = PrismMath.multiply(offset, PrismMath.multiply(pose.rotation, PrismMath.axisAngle(0.0, 1.0, 0.0, values.spin)))
        val idle = if (reduced) 0.0 else 0.18 * sin(2 * PI * PrismTimeline.modulo(clock, 5.0) / 5)
        light = floatArrayOf((idle * (1 - lightMix) + lightX).toFloat(), lightY.toFloat())
    }

    fun begin(x: Double, y: Double) {
        offset = PrismMath.multiply(rotation, PrismMath.transpose(pose.rotation))
        vx = 0.0
        vy = 0.0
        surgeActive = false
        scripted = false
        relaxing = false
        down = true
        dragging = false
        lastX = 0.0
        lastY = 0.0
        lastTouch = clock
        heldAt = clock
        heldCharge = 0.0
        moveLight(x, y)
        lightMix = 1.0
        lightReleaseAt = -1.0
        evaluate()
    }

    fun move(x: Double, y: Double, dx: Double, dy: Double) {
        if (!down) return
        lastTouch = clock
        moveLight(x, y)
        if (!dragging && hypot(dx, dy) >= 8) {
            dragging = true
            relaxFrom = heldCharge
            relaxAt = clock
        }
        if (dragging && !reduced) {
            offset = PrismMath.trackball(offset, dx - lastX, dy - lastY, 124.0)
            lastX = dx
            lastY = dy
        }
        evaluate()
    }

    /** Returns true only on the causal frame of a release burst, for the haptic. */
    fun end(success: Boolean, velocityX: Double, velocityY: Double): Boolean {
        if (!down) return false
        down = false
        lastTouch = clock
        val fires = success && !dragging
        if (fires) fire(0.85 + 0.15 * heldCharge, heldCharge) else {
            vx = if (success && !reduced) velocityX / 124 else 0.0
            vy = if (success && !reduced) velocityY / 124 else 0.0
            relaxing = true
            relaxFrom = heldCharge
            relaxAt = clock
        }
        releaseLight()
        evaluate()
        return fires
    }

    fun activate() {
        lastTouch = clock
        offset = PrismMath.multiply(rotation, PrismMath.transpose(pose.rotation))
        vx = 0.0
        vy = 0.0
        down = false
        fire(0.85, 0.0)
        releaseLight()
        evaluate()
    }

    private fun fire(gain: Double, compression: Double) {
        surgeActive = true
        scripted = false
        burstAt = clock
        burstGain = if (reduced) 0.5 else gain
        releaseCharge = compression
        relaxing = false
        heldCharge = 0.0
    }

    private fun moveLight(x: Double, y: Double) {
        lightX = (x / 124).coerceIn(-1.0, 1.0)
        lightY = (y / 124).coerceIn(-1.0, 1.0)
    }

    private fun releaseLight() {
        lightReleaseAt = clock
        releaseX = lightX
        releaseY = lightY
        releaseMix = lightMix
    }
}

package com.ngocdevv.nativecook.blocks.onboarding.metacardunifyfan

import kotlin.math.*

/** One frame clock owns autoplay, user transitions, coasting, and spring velocity handoff. */
internal class MetacardMotion(
    var autoPlay: Boolean,
    var reduced: Boolean,
    private val haptic: (Boolean) -> Unit,
) {
    var u = 0.0
    var forward = 1.0
    var spin = -49.0
    var anchorSpin = -49.0
    var clock = 0.0
    var fanPress = 0.0
    var objectPress = 0.0
    var dragYaw = 0.0
    var dragPitch = 0.0
    private var now = 0.0
    private var epoch = Double.NaN
    private var previous = Double.NaN
    private var paused = false
    private var down = false
    private var lastTouch = -100.0
    private var spinOffset = 0.0
    private var fanVelocity = 0.0
    private var yawVelocity = 0.0
    private var pitchVelocity = 0.0
    private var hit = 0
    private var lastAngle = 0.0
    private var initialX = 0.0
    private var initialY = 0.0
    private var initialYaw = 0.0
    private var initialPitch = 0.0
    private var fanPressTarget = 0.0
    private var objectPressTarget = 0.0
    private var pressStart = 0.0
    private var fanPressFrom = 0.0
    private var objectPressFrom = 0.0
    private var transitionStart = Double.NaN
    private var transitionFrom = 0.0
    private var transitionDuration = 0.0
    private var userTransition = false
    private var locked = false

    fun tick(seconds: Double) {
        now = seconds
        if (epoch.isNaN()) epoch = now
        val dt = if (previous.isNaN()) 0.0 else (now - previous).coerceIn(0.0, .1)
        previous = now
        if (autoPlay && paused && !down && now - lastTouch > 3.5 && (u <= 0 || u >= 1)) {
            val base = if (u > .5) 4.9 else 0.0
            epoch = now - base
            spinOffset = spin - MetacardTimeline.loopState(base).spin
            paused = false
            userTransition = false
        }
        if (autoPlay && !paused) {
            val local = ((now - epoch) % 12 + 12) % 12
            val state = MetacardTimeline.loopState(local)
            anchorSpin =
                MetacardTimeline.loopState(if (state.forward != 0.0) 3.0 else 9.4).spin + spinOffset
            u = state.u
            forward = state.forward
            if (reduced) {
                val still = MetacardTimeline.reducedLoopState(local)
                u = still.first
                objectPress = still.second
            }
            spin = if (reduced) -49.0 else state.spin + spinOffset
            clock = if (reduced) 0.0 else local
        } else {
            clock = if (reduced) 0.0 else clock + dt
            if (!transitionStart.isNaN()) {
                val progress = ((now - transitionStart) / transitionDuration).coerceIn(0.0, 1.0)
                u = MetacardTimeline.lerp(transitionFrom, forward, progress)
                if (progress >= 1) transitionStart = Double.NaN
            }
            if (!reduced && !down && u == 0.0) {
                val decay = exp(-dt / .6)
                spin += 7.5 * dt + fanVelocity * .6 * (1 - decay)
                fanVelocity *= decay
            }
        }
        if (userTransition && forward == 1.0 && !locked && u * 1900 >= 1025) {
            locked = true
            haptic(true)
        }
        val pressProgress =
            if (reduced) 1.0 else ((now - pressStart) / (if (down) .12 else .18)).coerceIn(0.0, 1.0)
        val pressEase =
            if (pressProgress < .5) 2 * pressProgress * pressProgress
            else 1 - (-2 * pressProgress + 2).pow(2) / 2
        fanPress = MetacardTimeline.lerp(fanPressFrom, fanPressTarget, pressEase)
        if (!(autoPlay && !paused && reduced))
            objectPress = MetacardTimeline.lerp(objectPressFrom, objectPressTarget, pressEase)
        if (!down && !reduced) {
            val yaw = springStep(dragYaw, yawVelocity, dt)
            val pitch = springStep(dragPitch, pitchVelocity, dt)
            dragYaw = yaw.first
            yawVelocity = yaw.second
            dragPitch = pitch.first
            pitchVelocity = pitch.second
        }
    }

    fun activate() {
        if (u > .0001 && u < .9999) return
        paused = true
        lastTouch = now
        userTransition = true
        locked = false
        forward = if (u < .5) 1.0 else 0.0
        anchorSpin = spin
        transitionFrom = u
        transitionStart = now + if (reduced) .12 else 0.0
        transitionDuration = if (reduced) .32 else if (forward == 1.0) 1.9 else 1.4
        haptic(false)
    }

    fun touchDown(x: Double, y: Double) {
        val dx = x - 201
        val dy = y - 356
        val distance = dx * dx + dy * dy
        val fanHit = distance < 226 * 226
        val heroHit = abs(x - 201) < 175 && abs(y - 522) < 140
        hit =
            when {
                u == 0.0 && fanHit -> if (distance < 66 * 66) 1 else 2
                u == 1.0 && heroHit -> 3
                else -> 0
            }
        down = true
        lastTouch = now
        if (!paused && u > 0 && u < 1) {
            transitionFrom = u
            transitionStart = now
            transitionDuration =
                if (reduced) .32 else if (forward == 1.0) 1.9 * (1 - u) else 1.4 * u
        }
        paused = true
        fanVelocity = 0.0
        beginPress()
        fanPressTarget = if (fanHit) 1.0 else 0.0
        objectPressTarget = if (heroHit || distance < 66 * 66) 1.0 else 0.0
        lastAngle = atan2(dy, dx)
        initialX = x
        initialY = y
        initialYaw = dragYaw
        initialPitch = dragPitch
    }

    fun drag(x: Double, y: Double, vx: Double, vy: Double) {
        if (reduced) return
        if (hit == 2) {
            val dx = x - 201
            val dy = y - 356
            if (dx * dx + dy * dy < 900) return
            val angle = atan2(dy, dx)
            val delta = atan2(sin(angle - lastAngle), cos(angle - lastAngle))
            spin += delta * 180 / PI
            lastAngle = angle
            fanVelocity =
                ((dx * vy - dy * vx) / max(900.0, dx * dx + dy * dy) * 180 / PI).coerceIn(
                    -600.0,
                    600.0,
                )
        } else if (hit == 3) {
            dragYaw = 26 * tanh((initialYaw + (x - initialX) * .14) / 26)
            dragPitch = 26 * tanh((initialPitch - (y - initialY) * .14) / 26)
            yawVelocity = vx * .14
            pitchVelocity = -vy * .14
        }
    }

    fun release(tap: Boolean, cancelled: Boolean = false) {
        down = false
        lastTouch = now
        beginPress()
        fanPressTarget = 0.0
        objectPressTarget = 0.0
        if (cancelled) {
            yawVelocity = 0.0
            pitchVelocity = 0.0
        }
        if (tap && !cancelled && (hit == 1 || hit == 3)) activate()
    }

    private fun beginPress() {
        pressStart = now
        fanPressFrom = fanPress
        objectPressFrom = objectPress
    }

    private fun springStep(x: Double, v: Double, dt: Double): Pair<Double, Double> {
        val decay = 7.0
        val w = sqrt(140.0 - decay * decay)
        val a = (v + decay * x) / w
        val e = exp(-decay * dt)
        val position = e * (x * cos(w * dt) + a * sin(w * dt))
        val velocity = e * ((-decay * x + w * a) * cos(w * dt) + (-decay * a - w * x) * sin(w * dt))
        return position to velocity
    }
}

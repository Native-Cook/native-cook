package com.ngocdevv.nativecook.blocks.animations.emberchargewidget

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.ngocdevv.nativecook.blocks.animations.emberchargewidget.EmberTheme.E
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

/** One UI-thread owner. Drawing reads revision; copy updates only at quantized ticks/endpoints. */
internal class EmberMotion(val goal: Int, var autoPlay: Boolean, val ignite: () -> Unit, val tick: () -> Unit) {
    // Copy — only quantized changes recompose text.
    /** Draw-phase invalidation only; never read this in composition. */
    var revision by mutableIntStateOf(0)
    var shown by mutableIntStateOf(E.restSteps)

    /** Previous quantized count seeds the isolated endpoint roll. */
    var previousShown = E.restSteps

    /** Last landed endpoint, retained through cooldown for accessible copy. */
    var reached by mutableStateOf(false)
    var reduced by mutableStateOf(false)
        private set

    // Clock — seconds on the single UI-thread frame owner.
    var mode = EmberMode.Idle

    /** Touch/accessibility owns the clock until the idle resume delay finishes. */
    var manual = false
    var charge = 0f

    /** Normalized ignition age; one means the effect has finished. */
    var ignition = 0f

    /** Decorative seconds, stopped by Reduce Motion independently of transition deadlines. */
    var time = 0f

    /** Autoplay position modulo M.loop.end, paused until entrance completes. */
    var clock = 0f

    /** Unclamped wall-clock seconds for state transition deadlines. */
    private var elapsed = 0f

    /** Wall-clock anchor for the current charge or cooldown. */
    private var stateStart = 0f

    /** Visible charge at takeover; a new touch continues from here. */
    private var startCharge = 0f

    /** Remaining-charge duration in seconds, shortened on takeover. */
    private var chargeDuration = M.charge / 1000

    /** Wall-clock anchor, backdated when taking over an active loop ignition. */
    private var ignitionStart = 0f

    /** Cooldown deadline, shortened to the remaining hold on loop takeover. */
    private var holdEnd = 0f

    /** Negative means no idle resume is scheduled. */
    private var resumeAt = -1f

    /** Entrance opacity and bevel materialisation share this eased progress. */
    var enter = 0f

    /** Consecutive fast frames gate entrance after shader warm-up. */
    private var smoothFrames = 0

    /** Negative until the live gate opens; seconds thereafter. */
    private var enterStart = -1f

    // Touch — pressure also falls at ignition while the finger remains down.
    var pressing = false
    var pressure = 0f

    /** Pressure at the beginning of the current bezier segment. */
    private var pressureFrom = 0f

    /** Pressure target independent of the physical finger state. */
    private var pressureTarget = 0f

    /** Wall-clock anchor for the current pressure segment. */
    private var pressureStart = 0f

    /** Current pressure segment length in seconds, including early ignition release. */
    private var pressureDuration = M.press / 1000

    /** Instant onset and fading release; radius does not shrink on release. */
    var touchLight = 0f

    /** Normalized growth of the light beneath the finger. */
    var touchSpread = 0f

    /** Anchor shared by the current touch spread or fade. */
    private var touchStart = 0f

    /** Light intensity captured when pressure begins falling. */
    private var touchFadeFrom = 0f

    /** Whether pressure is rising, rather than whether a finger is held. */
    private var touchDown = false

    /** Last crossed haptic stride, including decreasing samples. */
    private var stepBand = E.restSteps / M.hapticStride

    // Springs — persistent velocities belong to this frame owner.
    /** Asymmetric exponential filter of charge; bypassed by Reduce Motion. */
    var heat = 0f

    /** Stripe displacement modulo the theme period, in dp. */
    var drift = 0f

    /** Heartbeat phase modulo one; rate eases from rest to goal near full charge. */
    var phase = 0f

    /** Unit-mass light lean, integrated without bar substeps. */
    var slosh = 0f
    var sloshVelocity = 0f

    /** Tube-head displacement in dp, integrated at bounded substeps. */
    var meniscus = 0f
    var meniscusVelocity = 0f

    /** Smoothed finger/loop coordinates, normalized around the card centre. */
    var aimX = 0f
    var aimY = 0f
    private var aimSpringX = EmberSpring(0f)
    private var aimSpringY = EmberSpring(0f)
    private var releaseSpring = EmberSpring(0f)

    /** Negative skips the first derivative sample for the meniscus. */
    private var oldProgress = -1f

    /** Rising-edge latch: one slosh kick per ignition. */
    private var wasLit = false

    // Quality — sample once after warm-up and entrance.
    var requestedQuality by mutableStateOf(EmberQuality.Auto)
    private var autoQuality by mutableStateOf(EmberQuality.High)

    /** Negative until the first fully visible sampling frame. */
    private var qualityStart = -1f
    private var slowFrames = 0

    /** Automatic quality may step down once, never upgrade or sample again. */
    private var measured = false
    val quality get() = if (requestedQuality == EmberQuality.Auto) autoQuality else requestedQuality

    // Derived values shared by the drawing passes.
    val progress get() = (E.restSteps + charge * (goal - E.restSteps)) / goal
    val glow get() = if (reduced) charge else heat

    /** Both hold and cooldown ignore an early-release request. */
    val held get() = mode == EmberMode.Ignited || mode == EmberMode.Cooling
    val playing get() = autoPlay && !reduced && !manual

    fun updateReduced(value: Boolean) {
        if (value == reduced) return
        reduced = value
        if (!manual) {
            clock = 0f
            charge = 0f
            ignition = 0f
        }
        if (value) {
            aimX = 0f
            aimY = 0f
            slosh = 0f
            phase = 0f
            drift = 0f
            time = 0f
        }
        revision++
    }

    fun follow(x: Float, y: Float) {
        if (reduced) return
        aimSpringX.start(
            aimX, (x / (E.size / 2) - 1).coerceIn(-1f, 1f), 0f, M.aim.follow.duration / 1000, M.aim.follow.dampingRatio,
        )
        aimSpringY.start(
            aimY, (y / (E.size / 2) - 1).coerceIn(-1f, 1f), 0f, M.aim.follow.duration / 1000, M.aim.follow.dampingRatio,
        )
    }

    private fun press(target: Float, duration: Float) {
        pressureFrom = pressure
        pressureTarget = target
        pressureStart = elapsed
        pressureDuration = duration
    }

    /** Take over the visible loop state without replaying its success event. */
    fun begin(x: Float, y: Float) {
        follow(x, y)
        pressing = true
        resumeAt = -1f
        sloshVelocity += M.slosh.press
        press(1f, M.press / 1000)
        if (playing) {
            manual = true
            if (clock >= M.loop.charged && clock < M.loop.held) {
                mode = EmberMode.Ignited
                ignitionStart = elapsed - ignition * M.ignite
                holdEnd = elapsed + M.loop.held - clock
                press(0f, M.pressRelease / 1000)
                return
            }
        }
        manual = true
        if (held) {
            press(0f, M.pressRelease / 1000)
            return
        }
        chargeUp()
    }

    /** Release aim with finger velocity; held goals finish their own cooldown. */
    fun end(vx: Float, vy: Float) {
        aimSpringX.start(aimX, 0f, vx / (E.size / 2), M.aim.release.duration / 1000, M.aim.release.dampingRatio)
        aimSpringY.start(aimY, 0f, vy / (E.size / 2), M.aim.release.duration / 1000, M.aim.release.dampingRatio)
        press(0f, M.finalizePress / 1000)
        if (!pressing) return
        pressing = false
        if (held) return
        sloshVelocity += M.slosh.release
        mode = EmberMode.Releasing
        releaseSpring.start(charge, 0f, 0f, M.release.duration / 1000, M.release.dampingRatio, clampOvershoot = true)
    }

    /** Accessible activation charges without pressure or finger-only selection ticks. */
    fun activate() {
        manual = true
        resumeAt = -1f
        aimSpringX.start(aimX, 0f, 0f, M.aim.release.duration / 1000, M.aim.release.dampingRatio)
        aimSpringY.start(aimY, 0f, 0f, M.aim.release.duration / 1000, M.aim.release.dampingRatio)
        chargeUp()
    }

    /** Continue from the visible charge, including interrupted release/cooldown. */
    private fun chargeUp() {
        mode = EmberMode.Charging
        stateStart = elapsed
        startCharge = charge
        ignition = 0f
        chargeDuration = max(M.minimumCharge, (1 - charge) * if (reduced) M.chargeReduced / 1000 else M.charge / 1000)
    }

    /** Advance deadlines with wall time and physics with a bounded integration step. */
    fun frame(delta: Float, firstFrame: Boolean = false) {
        val previousCharge = charge
        val previousEnter = enter
        val previousLight = touchLight
        val dt = if (firstFrame) M.frame.initial else delta.coerceIn(M.frame.minimum, M.frame.maximum)
        elapsed += if (firstFrame) 0f else delta
        updateEntranceAndQuality(delta)
        advanceOwnership(delta, dt)
        updateTouchLight()
        advancePhysics(delta, dt, firstFrame)
        updateCopy()
        // Under Reduce Motion the live gate still observes frames, but stable artwork does not redraw.
        if (!reduced || charge != previousCharge || enter != previousEnter || touchLight != previousLight) revision++
    }

    private fun updateEntranceAndQuality(delta: Float) {
        // Warm-up and quality sampling stay alive even when decorative motion is reduced.
        if (enterStart < 0) {
            smoothFrames = if (delta < M.frame.liveThreshold) smoothFrames + 1 else 0
            if (smoothFrames >= M.frame.liveFrames) enterStart = elapsed
        } else enter =
            Out.transform(seg(elapsed - enterStart, 0f, if (reduced) M.enterReduced / 1000 else M.enter / 1000))
        if (enter == 1f && !measured && requestedQuality == EmberQuality.Auto) {
            if (qualityStart < 0) qualityStart = elapsed
            if (elapsed - qualityStart <= M.quality.window / 1000) {
                if (delta > M.quality.frame / 1000) slowFrames++
            } else {
                measured = true
                autoQuality = if (slowFrames > M.quality.allowed) EmberQuality.Medium else EmberQuality.High
            }
        }
    }

    private fun advanceOwnership(delta: Float, dt: Float) {
        // Loop ownership and manual transitions produce the same visible charge contract.
        if (playing) {
            if (enter == 1f) clock = (clock + delta) % M.loop.end
            charge = chargeAt(clock)
            ignition = if (clock < M.loop.charged) 0f else min(1f, (clock - M.loop.charged) / M.ignite)
            val angle = clock / M.loop.end * 2 * PI.toFloat()
            val envelope = sin(angle / 2).pow(2)
            aimX = M.aim.x * envelope * sin(angle)
            aimY = M.aim.y * envelope * sin(2 * angle)
        } else {
            when (mode) {
                EmberMode.Charging -> {
                    val chargeFraction = seg(elapsed - stateStart, 0f, chargeDuration)
                    charge = startCharge + (1 - startCharge) * InOut.transform(chargeFraction)
                    if (chargeFraction >= 1) {
                        mode = EmberMode.Ignited
                        ignitionStart = elapsed
                        holdEnd = elapsed + M.hold / 1000
                        press(0f, M.pressRelease / 1000)
                        ignite()
                    }
                }

                EmberMode.Ignited -> if (elapsed >= holdEnd) {
                    mode = EmberMode.Cooling
                    stateStart = holdEnd
                }

                EmberMode.Cooling -> {
                    charge = 1 - InOut.transform(seg(elapsed - stateStart, 0f, M.cool / 1000))
                    if (charge <= 0) {
                        mode = EmberMode.Idle
                        ignition = 0f
                        if (pressing) chargeUp()
                    }
                }

                EmberMode.Releasing -> {
                    charge = releaseSpring.advance(dt)
                    if (releaseSpring.done) {
                        charge = 0f
                        mode = EmberMode.Idle
                    }
                }

                EmberMode.Idle -> Unit
            }
            if (held) ignition = min(1f, (elapsed - ignitionStart) / M.ignite)
            if (!reduced) {
                aimX = aimSpringX.advance(dt).coerceIn(-1f, 1f)
                aimY = aimSpringY.advance(dt).coerceIn(-1f, 1f)
            }
            if (manual && !pressing && !held && charge <= M.restEpsilon && autoPlay && !reduced) {
                if (resumeAt < 0) resumeAt = elapsed + (M.resume + M.resumeCompletion) / 1000
                if (elapsed >= resumeAt) {
                    manual = false
                    clock = 0f
                    resumeAt = -1f
                }
            } else resumeAt = -1f
        }
    }

    private fun updateTouchLight() {
        // Pressure edges, not finger edges, own the light onset/fade at ignition.
        val oldPressure = pressure
        pressure = pressureFrom + (pressureTarget - pressureFrom) * Out.transform(
            seg(elapsed - pressureStart, 0f, pressureDuration),
        )
        if (pressure != oldPressure && (pressure > oldPressure) != touchDown) {
            touchDown = pressure > oldPressure
            touchStart = elapsed
            if (touchDown) {
                if (touchLight == 0f) touchSpread = 0f
                touchLight = 1f
            } else touchFadeFrom = touchLight
        }
        if (touchDown) touchSpread =
            if (reduced) 0f else 1 - (1 - seg(elapsed - touchStart, 0f, M.touchSpread / 1000)).pow(3)
        else touchLight = touchFadeFrom * (1 - seg(elapsed - touchStart, 0f, M.touchFade / 1000)).pow(3)
    }

    private fun advancePhysics(delta: Float, dt: Float, firstFrame: Boolean) {
        if (ignition > 0 && !wasLit) sloshVelocity += M.slosh.ignite
        wasLit = ignition > 0
        // Independent physical responses share dt, but only the meniscus uses substeps.
        if (!reduced) {
            time += if (firstFrame) 0f else delta
            heat += (charge - heat) * (1 - exp(-dt * if (charge > heat) M.heatUp else M.heatDown))
            val stripeSpeed = M.stripeIdle + (M.stripeMax - M.stripeIdle) * charge.pow(M.stripePower)
            drift = (drift + stripeSpeed * dt) % E.stripe.period
            phase = (phase + dt * (E.restBpm + (E.goalBpm - E.restBpm) * seg(charge, M.bpmChargeStart, 1f)) / 60) % 1
            sloshVelocity += (-M.slosh.stiffness * slosh - M.slosh.damping * sloshVelocity) * dt
            slosh += sloshVelocity * dt
            val currentProgress = progress
            if (oldProgress >= 0) {
                val target =
                    (((currentProgress - oldProgress) * E.bar.w / max(dt, M.bar.minimumDelta)) * M.bar.drag).coerceIn(
                        M.bar.minimumBend, M.bar.maximumBend,
                    )
                val substeps = ceil(dt * M.bar.sampleHz).toInt()
                val substep = dt / substeps
                repeat(substeps) {
                    val acceleration = M.bar.stiffness * (target - meniscus) - M.bar.damping * meniscusVelocity
                    meniscusVelocity += acceleration * substep
                    meniscus += meniscusVelocity * substep
                }
                if (abs(meniscus) < M.bar.positionEpsilon && abs(meniscusVelocity) < M.bar.velocityEpsilon) {
                    meniscus = 0f
                }
            }
            oldProgress = currentProgress
        }
    }

    private fun updateCopy() {
        // Copy and haptics observe quantized boundaries after physics has advanced.
        val steps = E.restSteps + charge * (goal - E.restSteps)
        val band = floor(steps / M.hapticStride).toInt()
        if (band > stepBand && pressing) tick()
        stepBand = band
        val next = when {
            steps >= goal - M.endpointEpsilon -> goal
            steps <= E.restSteps + M.endpointEpsilon -> E.restSteps
            else -> (floor(steps / M.countStride).toInt() * M.countStride).coerceIn(E.restSteps + 1, goal - 1)
        }
        if (next != shown) {
            previousShown = shown
            shown = next
        }
        if (steps >= goal - M.endpointEpsilon) {
            reached = true
        } else if (steps <= E.restSteps + M.endpointEpsilon) {
            reached = false
        }
    }

    companion object {
        // These beziers drive manual transitions; the loop and heartbeat use polynomials below.
        val Out = CubicBezierEasing(0.23f, 1f, 0.32f, 1f)
        val InOut = CubicBezierEasing(0.77f, 0f, 0.175f, 1f)
        fun seg(t: Float, a: Float, b: Float) = ((t - a) / (b - a)).coerceIn(0f, 1f)
        fun ease(x: Float) = if (x < 0.5f) 4 * x * x * x else 1 - (-2 * x + 2).pow(3) / 2

        /** Autoplay uses a polynomial envelope, distinct from the manual-charge bezier. */
        fun chargeAt(t: Float) = when {
            t < M.loop.rest -> 0f
            t < M.loop.charged -> ease((t - M.loop.rest) / (M.loop.charged - M.loop.rest))
            t < M.loop.held -> 1f
            else -> 1 - ease((t - M.loop.held) / (M.cool / 1000))
        }

        /** Normalized lub-dub envelope, using the reference 90/110/70/160 ms segments. */
        fun beat(phase: Float): Float {
            val ms = phase * 430
            fun out(x: Float) = 1 - (1 - x).pow(4)
            return when {
                ms < 90 -> 1 + 0.12f * out(ms / 90)
                ms < 200 -> 1.12f - 0.12f * out((ms - 90) / 110)
                ms < 270 -> 1 + 0.07f * out((ms - 200) / 70)
                else -> 1.07f - 0.07f * out(min(1f, (ms - 270) / 160))
            }
        }
    }
}

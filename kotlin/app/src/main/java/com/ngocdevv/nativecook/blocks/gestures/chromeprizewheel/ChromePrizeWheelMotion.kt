package com.ngocdevv.nativecook.blocks.gestures.chromeprizewheel

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sign
import kotlin.math.sin
import kotlin.math.sqrt

internal const val ChromePrizeWheelStep = 1.0 / 480.0
internal const val ChromePrizeWheelTau = PI * 2.0

internal object ChromePrizeWheelPhase {
    const val Idle = 0
    const val Held = 1
    const val Spinning = 2
    const val Landed = 3
    const val Reveal = 4
    const val Collecting = 5
}

internal data class ChromePrize(
    val key: String,
    val count: Int,
    val label: String,
)

internal val ChromePrizeWheelPrizes = listOf(
    ChromePrize("plate", 5, "A plate of donuts"),
    ChromePrize("chest", 50, "The treasure chest"),
    ChromePrize("tray", 15, "A baker's tray"),
    ChromePrize("box", 10, "A box of ten"),
    ChromePrize("coffee", 1, "A coffee on us"),
    ChromePrize("stack", 3, "A triple stack"),
)

private object ChromePrizeWheelPhysics {
    const val stiffness = 1.55
    val inertia = stiffness / (ChromePrizeWheelTau * 9.0).pow(2)
    val damping = 2.0 * 0.28 * stiffness / (ChromePrizeWheelTau * 9.0)
    const val coulomb = 1.15
    const val viscous = 0.025
    const val restitution = 0.15
    const val contactFrictionFraction = 0.15
    const val speedCap = 22.0
    const val countedSpeed = 5.0
    const val countedTravel = ChromePrizeWheelTau
    const val stillSpeed = 0.002
    const val stillSwing = 0.0008
    const val stillPointerSpeed = 0.02
    const val stillBeat = 0.18
    const val derivativeStep = 0.00001
    const val swayFrequencyA = ChromePrizeWheelTau * 2.6
    const val swayFrequencyB = ChromePrizeWheelTau * 3.9
    const val swayDampingA = 0.2
    const val swayDampingB = 0.25
    const val swayGainA = -0.045
    const val swayGainB = -0.035
    const val swayLean = -0.008
    const val swayLimit = 0.4
}

internal object ChromePrizeWheelMotion {
    val out = doubleArrayOf(0.23, 1.0, 0.32, 1.0)
    val travel = doubleArrayOf(0.77, 0.0, 0.175, 1.0)
    val holdSpring = doubleArrayOf(400.0, 30.0)
    val liftSpring = doubleArrayOf(260.0, 17.0)
    val wheelSpring = doubleArrayOf(180.0, 22.0)
    val heroSpring = doubleArrayOf(150.0, 13.0)
    val squashSpring = doubleArrayOf(300.0, 12.0)
    val toySpring = doubleArrayOf(220.0, 16.0)
    val sinkSpring = doubleArrayOf(220.0, 24.0)
    val swallowSpring = doubleArrayOf(300.0, 14.0)
    val restockSpring = doubleArrayOf(320.0, 16.0)
    val hubSpring = doubleArrayOf(400.0, 30.0)
    const val heldScale = 0.985
    const val revealScale = 0.94
    const val tiltGain = 0.07
    const val wobbleBase = 0.012
    const val wobbleSpeed = 0.02
    const val wobblePhase = 0.6
    const val environmentPeriod = 7.0
    const val environmentAmount = 0.1
    const val swirlGain = 0.05
    const val swirlDrag = 0.8
    const val hapticGap = 0.045
    const val tickDecay = 0.09
    const val hubPress = 0.12
    const val hubSpeed = 17.0
    const val hubVariation = 1.5
    const val hintSpinning = 0.35
    const val hintDuration = 0.2
    const val nudgeDuration = 0.36
    const val heroPull = 0.6
    const val heroTiltGain = 0.00025
    const val heroTiltCap = 0.2
    const val fingerFade = 0.15
    const val takeoverDelay = 3.5
    const val homeDuration = 0.5
    const val reducedFade = 0.16
    const val reducedReveal = 0.24
    const val reducedCollect = 0.24
    const val hubHitScale = 1.25
    const val velocityBlend = 0.7
    const val trackedBlend = 0.35
}

private object ChromePrizeWheelRevealTiming {
    const val glow = 0.42
    const val comet = 0.52
    const val dim = 0.5
    const val copy = 0.24
    const val crouch = 0.11
    const val launchAt = 0.12
    const val stretch = 0.09
    const val rays = 0.6
    const val badgeAt = 0.28
    const val badge = 0.52
    const val captionAt = 0.52
    const val caption = 0.3
    const val countBase = 0.3
    const val countPerPrize = 0.012
    const val countCap = 0.9
    const val buttonAt = 0.58
    const val button = 0.38
}

private object ChromePrizeWheelCollectTiming {
    const val press = 0.11
    const val flight = 0.46
    const val caption = 0.18
    const val rays = 0.24
    const val copy = 0.24
    const val sinkAt = 0.3
    const val glow = 0.4
    const val dim = 0.45
    const val swallowAt = 0.4
    const val swallow = 0.09
    const val restockAt = 0.46
    const val buttonAt = 0.52
    const val button = 0.34
    const val done = 0.9
}

private object ChromePrizeWheelDemoScript {
    const val periodSteps = 10265
    const val rest = 0.9
    const val fingerWait = 0.27
    val first = Throw(0.62, -0.42, 70.0 * PI / 180.0, 0.3, 16.17000000000024, 3.4)
    const val between = 0.7
    val second = Throw(-0.55, 0.5, -50.0 * PI / 180.0, 0.32, -9.888019148781598, 2.4)
    const val press = 0.14
    const val tail = 0.8

    data class Throw(
        val x: Double,
        val y: Double,
        val drag: Double,
        val duration: Double,
        val omega: Double,
        val admire: Double,
    )
}

private object ChromePrizeWheelGeometry {
    const val segmentAngle = ChromePrizeWheelTau / 6.0
    const val pegCount = 18
    const val pegPhase = PI / 18.0
    const val pegSpacing = PI / 9.0
    const val pegRadius = 0.905
    const val pegSize = 0.026
    const val pointerAngle = PI
    const val pointerPivot = 1.12
    const val pointerLength = 0.25
    const val pointerHalfBase = 0.095
    const val hub = 0.215
}

internal enum class ChromePrizeWheelValue {
    Theta, Omega, TiltX, TiltY, EnvTurn, SwayA, SwayB,
    Winner, Lift, Glow, WinnerPrize, WinnerPrizeScale, Dim, Comet, HubPress,
    Phase, Swing, Tick, Strikes, WheelScale, WheelOpacity, Swirl,
    Hero, HeroSquash, HeroX, HeroY, HeroTilt, Rays, Burst, BurstPower,
    Badge, Caption, Count, Button, ButtonPress, Collect, CopySwap, Hint,
    HintNudge, TouchX, TouchY, TouchOn,
}

internal class ChromePrizeWheelValues {
    private val values = DoubleArray(ChromePrizeWheelValue.entries.size)

    init {
        this[ChromePrizeWheelValue.Winner] = -1.0
        this[ChromePrizeWheelValue.WinnerPrize] = 1.0
        this[ChromePrizeWheelValue.WinnerPrizeScale] = 1.0
        this[ChromePrizeWheelValue.WheelScale] = 1.0
        this[ChromePrizeWheelValue.WheelOpacity] = 1.0
        this[ChromePrizeWheelValue.Burst] = -1.0
        this[ChromePrizeWheelValue.BurstPower] = 0.6
        this[ChromePrizeWheelValue.Hint] = 1.0
    }

    operator fun get(key: ChromePrizeWheelValue): Double = values[key.ordinal]
    operator fun set(key: ChromePrizeWheelValue, value: Double) {
        values[key.ordinal] = value
    }

    fun resetPreservingStrikes() {
        val strikes = this[ChromePrizeWheelValue.Strikes]
        values.fill(0.0)
        this[ChromePrizeWheelValue.Winner] = -1.0
        this[ChromePrizeWheelValue.WinnerPrize] = 1.0
        this[ChromePrizeWheelValue.WinnerPrizeScale] = 1.0
        this[ChromePrizeWheelValue.WheelScale] = 1.0
        this[ChromePrizeWheelValue.WheelOpacity] = 1.0
        this[ChromePrizeWheelValue.Burst] = -1.0
        this[ChromePrizeWheelValue.BurstPower] = 0.6
        this[ChromePrizeWheelValue.Hint] = 1.0
        this[ChromePrizeWheelValue.Strikes] = strikes
    }
}

private data class ChromePrizeWheelSim(
    var theta: Double = 0.0,
    var omega: Double = 0.0,
    var swing: Double = 0.0,
    var swingVelocity: Double = 0.0,
    var swayA: Double = 0.0,
    var swayB: Double = 0.0,
    var swayVelocityA: Double = 0.0,
    var swayVelocityB: Double = 0.0,
    var contact: Int = -1,
    var lastStrike: Int = Int.MAX_VALUE,
    var side: Int = 0,
    var steps: Int = 0,
    var strikes: Int = 0,
    var strikeStrength: Double = 0.0,
    var travel: Double = 0.0,
    var still: Double = 0.0,
)

private data class ChromePrizeWheelCam(
    val active: Boolean,
    val beta: Double,
    val gamma: Double,
    val g: Double,
    val slope: Double = 0.0,
    val curvature: Double = 0.0,
)

private data class ChromePrizeWheelTween(
    val key: ChromePrizeWheelValue,
    val from: Double,
    val to: Double,
    var elapsed: Double,
    val duration: Double,
    val stiffness: Double,
    val damping: Double,
    var velocity: Double,
    val travel: Boolean,
)

internal data class ChromePrizeWheelConfig(
    var reduced: Boolean,
    val autoPlay: Boolean,
    var cx: Double = 0.0,
    var cy: Double = 0.0,
    var radius: Double = 1.0,
    var buttonX: Double = 0.0,
    var buttonY: Double = 0.0,
)

internal object ChromePrizeWheelEvent {
    const val Light = 1
    const val Rigid = 2
    const val Medium = 4
    const val Success = 8
}

internal class ChromePrizeWheelEngine(
    val config: ChromePrizeWheelConfig,
) {
    val values = ChromePrizeWheelValues()
    var events = 0
        private set
    var loops = 0
        private set
    var lastPeriodSteps = 0
        private set

    private var sim = ChromePrizeWheelSim()
    private val tweens = mutableListOf<ChromePrizeWheelTween>()
    private var accumulator = 0.0
    private var steps = 0
    private var phaseStep = 0
    private var releaseSpeed = 0.0
    private var releaseTravel = 0.0
    private var targetTheta = 0.0
    private var tiltTargetX = 0.0
    private var tiltTargetY = 0.0
    private var tiltBaseX = 0.0
    private var tiltBaseY = 0.0
    private var tiltVelocityX = 0.0
    private var tiltVelocityY = 0.0
    private var touching = false
    private var held = false
    private var hubHeld = false
    private var heroHeld = false
    private var heroOriginX = 0.0
    private var heroOriginY = 0.0
    private var fingerAngle = 0.0
    private var trackedRate = 0.0
    private var moveStep = 0
    private var lastHaptic = -100
    private var demoStage = 0
    private var demoStep = 0
    private var demoStartTheta = 0.0
    private var takeover = false
    private var lastTouch = 0
    private var homeStep = -1
    private var homeFrom = 0.0
    private var homeTo = 0.0
    private var reducedPick = -1
    private var reducedStep = -1
    private var loopStep = 0

    private fun clamp(value: Double, low: Double, high: Double): Double = max(low, min(high, value))

    // JavaScript Math.round chooses +infinity at a half; kotlin.math.round uses ties-to-even.
    // Contact selection must preserve the reference simulator's exact rule in both directions.
    private fun jsRound(value: Double): Double = floor(value + 0.5)

    private fun wrap(angle: Double): Double = angle - ChromePrizeWheelTau * floor((angle + PI) / ChromePrizeWheelTau)

    private fun winningWedge(theta: Double): Int {
        var angle = (ChromePrizeWheelGeometry.pointerAngle - theta) % ChromePrizeWheelTau
        if (angle < 0.0) angle += ChromePrizeWheelTau
        return floor((angle + ChromePrizeWheelGeometry.segmentAngle / 2.0) / ChromePrizeWheelGeometry.segmentAngle).toInt() % 6
    }

    private fun contactGamma(distance: Double): Double {
        val length = ChromePrizeWheelGeometry.pointerLength
        val halfBase = ChromePrizeWheelGeometry.pointerHalfBase
        val pegRadius = ChromePrizeWheelGeometry.pegSize
        if (distance > length + pegRadius) return -1.0
        val side = sqrt(length * length + halfBase * halfBase)
        val seam = sqrt(length * length + pegRadius * pegRadius + 2.0 * length * pegRadius * halfBase / side)
        return if (distance <= seam) {
            asin(clamp((halfBase * length / side + pegRadius) / distance, -1.0, 1.0)) - atan2(halfBase, length)
        } else {
            acos(clamp((length * length + distance * distance - pegRadius * pegRadius) / (2.0 * length * distance), -1.0, 1.0))
        }
    }

    private fun camPosition(theta: Double, peg: Int, side: Int): ChromePrizeWheelCam {
        val angle = theta + ChromePrizeWheelGeometry.pegPhase + peg * ChromePrizeWheelGeometry.pegSpacing
        val x = ChromePrizeWheelGeometry.pointerPivot + ChromePrizeWheelGeometry.pegRadius * cos(angle)
        val y = ChromePrizeWheelGeometry.pegRadius * sin(angle)
        val gamma = contactGamma(sqrt(x * x + y * y))
        val beta = atan2(y, x)
        return ChromePrizeWheelCam(gamma >= 0.0, beta, gamma, beta + side * max(0.0, gamma))
    }

    private fun camAt(theta: Double, peg: Int, side: Int): ChromePrizeWheelCam {
        val centre = camPosition(theta, peg, side)
        if (!centre.active) return centre
        val epsilon = ChromePrizeWheelPhysics.derivativeStep
        val before = camPosition(theta - epsilon, peg, side)
        val after = camPosition(theta + epsilon, peg, side)
        if (!before.active || !after.active) return centre.copy(active = false)
        return centre.copy(
            slope = (after.g - before.g) / (2.0 * epsilon),
            curvature = (after.g - 2.0 * centre.g + before.g) / (epsilon * epsilon),
        )
    }

    private fun energy(state: ChromePrizeWheelSim): Double =
        0.5 * (state.omega * state.omega + ChromePrizeWheelPhysics.inertia * state.swingVelocity * state.swingVelocity +
            ChromePrizeWheelPhysics.stiffness * state.swing * state.swing)

    private fun oscillator(x: Double, velocity: Double, stiffness: Double, damping: Double, dt: Double, force: Double = 0.0): Pair<Double, Double> {
        val nextVelocity = (velocity * (1.0 - damping * dt / 2.0 - stiffness * dt * dt / 4.0) - stiffness * dt * x + dt * force) /
            (1.0 + damping * dt / 2.0 + stiffness * dt * dt / 4.0)
        return x + dt * (velocity + nextVelocity) / 2.0 to nextVelocity
    }

    private fun stepWheel(drivenTheta: Double? = null) {
        val state = sim
        val oldTheta = state.theta
        val oldOmega = state.omega
        val oldSwing = state.swing
        val oldEnergy = energy(state)
        state.strikeStrength = 0.0
        val driven = drivenTheta != null
        var attached = false
        if (state.contact >= 0) {
            val cam = camAt(state.theta, state.contact, state.side)
            if (cam.active) {
                val springTorque = -ChromePrizeWheelPhysics.stiffness * cam.g * cam.slope
                val friction = min(ChromePrizeWheelPhysics.coulomb, abs(springTorque) * ChromePrizeWheelPhysics.contactFrictionFraction)
                val torque = springTorque - ChromePrizeWheelPhysics.damping * cam.slope * cam.slope * state.omega -
                    ChromePrizeWheelPhysics.inertia * cam.slope * cam.curvature * state.omega * state.omega -
                    (if (abs(state.omega) > 0.000001) sign(state.omega) * friction else 0.0) - ChromePrizeWheelPhysics.viscous * state.omega
                val acceleration = torque / (1.0 + ChromePrizeWheelPhysics.inertia * cam.slope * cam.slope)
                val reaction = ChromePrizeWheelPhysics.inertia * (cam.slope * acceleration + cam.curvature * state.omega * state.omega) +
                    ChromePrizeWheelPhysics.damping * cam.slope * state.omega + ChromePrizeWheelPhysics.stiffness * cam.g
                if (driven || state.side * reaction >= 0.0) {
                    val predictedOmega = clamp(state.omega + acceleration * ChromePrizeWheelStep, -ChromePrizeWheelPhysics.speedCap, ChromePrizeWheelPhysics.speedCap)
                    val nextTheta = drivenTheta ?: (state.theta + (state.omega + predictedOmega) * ChromePrizeWheelStep / 2.0)
                    val next = camAt(nextTheta, state.contact, state.side)
                    if (next.active) {
                        state.theta = nextTheta
                        state.omega = if (driven) (nextTheta - oldTheta) / ChromePrizeWheelStep else predictedOmega
                        state.swing = next.g
                        state.swingVelocity = next.slope * state.omega
                        attached = true
                    }
                }
            }
            if (!attached) state.contact = -1
        }
        if (!attached) {
            val speed = max(0.0, (abs(state.omega) - ChromePrizeWheelPhysics.coulomb * ChromePrizeWheelStep) /
                (1.0 + ChromePrizeWheelPhysics.viscous * ChromePrizeWheelStep))
            state.omega = if (driven) ((drivenTheta ?: state.theta) - state.theta) / ChromePrizeWheelStep else sign(state.omega) * speed
            state.theta = drivenTheta ?: (state.theta + (oldOmega + state.omega) * ChromePrizeWheelStep / 2.0)
            val free = oscillator(
                state.swing,
                state.swingVelocity,
                ChromePrizeWheelPhysics.stiffness / ChromePrizeWheelPhysics.inertia,
                ChromePrizeWheelPhysics.damping / ChromePrizeWheelPhysics.inertia,
                ChromePrizeWheelStep,
            )
            state.swing = free.first
            state.swingVelocity = free.second
            val pegId = jsRound((PI - state.theta - ChromePrizeWheelGeometry.pegPhase) / ChromePrizeWheelGeometry.pegSpacing).toInt()
            val peg = ((pegId % ChromePrizeWheelGeometry.pegCount) + ChromePrizeWheelGeometry.pegCount) % ChromePrizeWheelGeometry.pegCount
            val contact = camPosition(state.theta, peg, 0)
            if (contact.active && abs(state.swing - contact.beta) < contact.gamma) {
                val previous = camPosition(oldTheta, peg, 0)
                val side = if (oldSwing < previous.beta) -1 else 1
                val boundary = camAt(state.theta, peg, side)
                if (boundary.active) {
                    val relative = state.swingVelocity - boundary.slope * state.omega
                    val impulse = -(1.0 + ChromePrizeWheelPhysics.restitution) * relative /
                        (1.0 / ChromePrizeWheelPhysics.inertia + if (driven) 0.0 else boundary.slope * boundary.slope)
                    state.swingVelocity += impulse / ChromePrizeWheelPhysics.inertia
                    if (!driven) state.omega -= impulse * boundary.slope
                    state.swing = boundary.g
                    state.contact = if (abs(relative) < 2.0 || state.lastStrike == pegId) peg else -1
                    state.side = side
                    if (state.contact >= 0) state.swingVelocity = boundary.slope * state.omega
                    if (state.lastStrike != pegId) {
                        state.strikes += 1
                        state.strikeStrength = abs(impulse)
                        state.lastStrike = pegId
                    }
                }
            }
        }
        state.omega = clamp(state.omega, -ChromePrizeWheelPhysics.speedCap, ChromePrizeWheelPhysics.speedCap)
        if (!driven) {
            val nextEnergy = energy(state)
            if (nextEnergy > oldEnergy) {
                val potential = 0.5 * ChromePrizeWheelPhysics.stiffness * state.swing * state.swing
                val kinetic = nextEnergy - potential
                if (potential <= oldEnergy && kinetic > 0.0) {
                    val ratio = sqrt(max(0.0, (oldEnergy - potential) / kinetic))
                    state.omega *= ratio
                    state.swingVelocity *= ratio
                } else {
                    state.theta = oldTheta
                    state.swing = oldSwing
                    state.omega = -oldOmega * ChromePrizeWheelPhysics.restitution
                    state.swingVelocity = if (state.contact >= 0) camAt(state.theta, state.contact, state.side).slope * state.omega else 0.0
                }
            }
        }
        val acceleration = (state.omega - oldOmega) / ChromePrizeWheelStep
        val a = oscillator(
            state.swayA,
            state.swayVelocityA,
            ChromePrizeWheelPhysics.swayFrequencyA.pow(2),
            2.0 * ChromePrizeWheelPhysics.swayDampingA * ChromePrizeWheelPhysics.swayFrequencyA,
            ChromePrizeWheelStep,
            ChromePrizeWheelPhysics.swayGainA * acceleration * ChromePrizeWheelPhysics.swayFrequencyA.pow(2),
        )
        val b = oscillator(
            state.swayB,
            state.swayVelocityB,
            ChromePrizeWheelPhysics.swayFrequencyB.pow(2),
            2.0 * ChromePrizeWheelPhysics.swayDampingB * ChromePrizeWheelPhysics.swayFrequencyB,
            ChromePrizeWheelStep,
            ChromePrizeWheelPhysics.swayGainB * acceleration * ChromePrizeWheelPhysics.swayFrequencyB.pow(2),
        )
        state.swayA = clamp(a.first, -ChromePrizeWheelPhysics.swayLimit, ChromePrizeWheelPhysics.swayLimit)
        state.swayB = clamp(b.first, -ChromePrizeWheelPhysics.swayLimit, ChromePrizeWheelPhysics.swayLimit)
        state.swayVelocityA = if (state.swayA == a.first) a.second else 0.0
        state.swayVelocityB = if (state.swayB == b.first) b.second else 0.0
        state.travel += abs(state.theta - oldTheta)
        state.steps += 1
        val still = abs(state.omega) < ChromePrizeWheelPhysics.stillSpeed && abs(state.swing) < ChromePrizeWheelPhysics.stillSwing &&
            abs(state.swingVelocity) < ChromePrizeWheelPhysics.stillPointerSpeed && state.contact < 0
        state.still = if (still) state.still + ChromePrizeWheelStep else 0.0
    }

    private fun cubic(time: Double, curve: DoubleArray): Double {
        val x = clamp(time, 0.0, 1.0)
        var low = 0.0
        var high = 1.0
        var u = x
        repeat(16) {
            u = (low + high) / 2.0
            val inverse = 1.0 - u
            val bx = 3.0 * inverse * inverse * u * curve[0] + 3.0 * inverse * u * u * curve[2] + u * u * u
            if (bx < x) low = u else high = u
        }
        if (x <= 0.0) return 0.0
        if (x >= 1.0) return 1.0
        return 3.0 * (1.0 - u).pow(2) * u * curve[1] + 3.0 * (1.0 - u) * u * u * curve[3] + u.pow(3)
    }

    private fun animate(
        key: ChromePrizeWheelValue,
        to: Double,
        duration: Double,
        spring: DoubleArray? = null,
        travel: Boolean = false,
    ) {
        var velocity = 0.0
        val iterator = tweens.listIterator(tweens.size)
        while (iterator.hasPrevious()) {
            val tween = iterator.previous()
            if (tween.key == key) {
                velocity = tween.velocity
                iterator.remove()
            }
        }
        tweens += ChromePrizeWheelTween(
            key = key,
            from = values[key],
            to = to,
            elapsed = 0.0,
            duration = duration,
            stiffness = spring?.get(0) ?: 0.0,
            damping = spring?.get(1) ?: 0.0,
            velocity = velocity,
            travel = travel,
        )
    }

    private fun set(key: ChromePrizeWheelValue, value: Double) {
        tweens.removeAll { it.key == key }
        values[key] = value
    }

    private fun stepTweens() {
        var index = tweens.lastIndex
        while (index >= 0) {
            val tween = tweens[index]
            tween.elapsed += ChromePrizeWheelStep
            if (tween.stiffness > 0.0) {
                val result = oscillator(values[tween.key] - tween.to, tween.velocity, tween.stiffness, tween.damping, ChromePrizeWheelStep)
                values[tween.key] = tween.to + result.first
                tween.velocity = result.second
                if (abs(result.first) < 0.00001 && abs(result.second) < 0.0001) {
                    values[tween.key] = tween.to
                    tweens.removeAt(index)
                }
            } else {
                val progress = if (tween.duration <= 0.0) 1.0 else tween.elapsed / tween.duration
                val easing = cubic(progress, if (tween.travel) ChromePrizeWheelMotion.travel else ChromePrizeWheelMotion.out)
                values[tween.key] = tween.from + (tween.to - tween.from) * easing
                if (tween.elapsed + 1e-12 >= tween.duration) {
                    values[tween.key] = tween.to
                    tweens.removeAt(index)
                }
            }
            index -= 1
        }
    }

    private fun phaseTo(phase: Int) {
        values[ChromePrizeWheelValue.Phase] = phase.toDouble()
        phaseStep = steps
    }

    private fun demoSteps(seconds: Double): Int = jsRound(seconds / ChromePrizeWheelStep).toInt()

    private fun demoDrag(step: Int, duration: Double, angle: Double): Double {
        val progress = clamp(step.toDouble() / demoSteps(duration), 0.0, 1.0)
        return angle * progress * progress * progress
    }

    private fun startReveal() {
        phaseTo(ChromePrizeWheelPhase.Reveal)
        if (takeover) lastTouch = steps
        values[ChromePrizeWheelValue.Winner] = winningWedge(sim.theta).toDouble()
        if (config.reduced) {
            animate(ChromePrizeWheelValue.Lift, 1.0, ChromePrizeWheelMotion.reducedReveal)
            animate(ChromePrizeWheelValue.Glow, 1.0, ChromePrizeWheelMotion.reducedReveal)
            animate(ChromePrizeWheelValue.Dim, 1.0, ChromePrizeWheelMotion.reducedReveal)
            animate(ChromePrizeWheelValue.Hero, 1.0, ChromePrizeWheelMotion.reducedReveal)
            animate(ChromePrizeWheelValue.Caption, 1.0, ChromePrizeWheelMotion.reducedReveal)
            animate(ChromePrizeWheelValue.Button, 1.0, ChromePrizeWheelMotion.reducedReveal)
            animate(ChromePrizeWheelValue.CopySwap, 1.0, ChromePrizeWheelMotion.reducedReveal)
            val winner = values[ChromePrizeWheelValue.Winner].toInt()
            values[ChromePrizeWheelValue.WinnerPrize] = 0.0
            values[ChromePrizeWheelValue.Count] = ChromePrizeWheelPrizes[winner].count.toDouble()
            values[ChromePrizeWheelValue.Badge] = 1.0
            values[ChromePrizeWheelValue.Rays] = 0.6
            values[ChromePrizeWheelValue.Burst] = -1.0
            values[ChromePrizeWheelValue.HeroSquash] = 0.0
            events = events or ChromePrizeWheelEvent.Success
            return
        }
        animate(ChromePrizeWheelValue.Lift, 1.0, 0.0, ChromePrizeWheelMotion.liftSpring)
        animate(ChromePrizeWheelValue.Glow, 1.0, ChromePrizeWheelRevealTiming.glow)
        animate(ChromePrizeWheelValue.Comet, 1.0, ChromePrizeWheelRevealTiming.comet)
        animate(ChromePrizeWheelValue.Dim, 1.0, ChromePrizeWheelRevealTiming.dim)
        animate(ChromePrizeWheelValue.WheelScale, ChromePrizeWheelMotion.revealScale, 0.0, ChromePrizeWheelMotion.wheelSpring)
        animate(ChromePrizeWheelValue.CopySwap, 1.0, ChromePrizeWheelRevealTiming.copy)
        animate(ChromePrizeWheelValue.HeroSquash, -0.5, ChromePrizeWheelRevealTiming.crouch)
    }

    fun collect() {
        if (values[ChromePrizeWheelValue.Phase].toInt() != ChromePrizeWheelPhase.Reveal) return
        takeOver()
        startCollect()
    }

    private fun startCollect() {
        if (values[ChromePrizeWheelValue.Phase].toInt() != ChromePrizeWheelPhase.Reveal) return
        phaseTo(ChromePrizeWheelPhase.Collecting)
        heroHeld = false
        events = events or ChromePrizeWheelEvent.Light
        values[ChromePrizeWheelValue.Burst] = -1.0
        if (config.reduced) {
            listOf(
                ChromePrizeWheelValue.Hero,
                ChromePrizeWheelValue.Lift,
                ChromePrizeWheelValue.Glow,
                ChromePrizeWheelValue.Dim,
                ChromePrizeWheelValue.Caption,
                ChromePrizeWheelValue.Button,
                ChromePrizeWheelValue.Rays,
                ChromePrizeWheelValue.CopySwap,
                ChromePrizeWheelValue.ButtonPress,
            ).forEach { animate(it, 0.0, ChromePrizeWheelMotion.reducedCollect) }
            return
        }
        animate(ChromePrizeWheelValue.ButtonPress, 0.0, 0.0, ChromePrizeWheelMotion.swallowSpring)
        animate(ChromePrizeWheelValue.Collect, 1.0, ChromePrizeWheelCollectTiming.flight, travel = true)
        animate(ChromePrizeWheelValue.Caption, 0.0, ChromePrizeWheelCollectTiming.caption)
        animate(ChromePrizeWheelValue.Rays, 0.0, ChromePrizeWheelCollectTiming.rays)
        animate(ChromePrizeWheelValue.CopySwap, 0.0, ChromePrizeWheelCollectTiming.copy)
        animate(ChromePrizeWheelValue.HeroX, 0.0, 0.0, ChromePrizeWheelMotion.toySpring)
        animate(ChromePrizeWheelValue.HeroY, 0.0, 0.0, ChromePrizeWheelMotion.toySpring)
        animate(ChromePrizeWheelValue.HeroTilt, 0.0, 0.0, ChromePrizeWheelMotion.toySpring)
    }

    private fun reducedSpin(random: Double) {
        val current = winningWedge(sim.theta)
        reducedPick = (current + 1 + floor(clamp(random, 0.0, 0.999999) * (ChromePrizeWheelPrizes.size - 1)).toInt()) % ChromePrizeWheelPrizes.size
        reducedStep = steps
        sim.omega = 0.0
        held = false
        hubHeld = false
        phaseTo(ChromePrizeWheelPhase.Spinning)
        animate(ChromePrizeWheelValue.WheelOpacity, 0.0, ChromePrizeWheelMotion.reducedFade)
    }

    private fun releaseWheel(omega: Double) {
        held = false
        hubHeld = false
        tiltTargetX = 0.0
        tiltTargetY = 0.0
        val launchChange = clamp(omega, -ChromePrizeWheelPhysics.speedCap, ChromePrizeWheelPhysics.speedCap) - sim.omega
        sim.swayVelocityA += ChromePrizeWheelPhysics.swayGainA * ChromePrizeWheelPhysics.swayFrequencyA.pow(2) * launchChange
        sim.swayVelocityB += ChromePrizeWheelPhysics.swayGainB * ChromePrizeWheelPhysics.swayFrequencyB.pow(2) * launchChange
        sim.omega = clamp(omega, -ChromePrizeWheelPhysics.speedCap, ChromePrizeWheelPhysics.speedCap)
        values[ChromePrizeWheelValue.Winner] = -1.0
        sim.still = 0.0
        releaseSpeed = abs(sim.omega)
        releaseTravel = sim.travel
        phaseTo(ChromePrizeWheelPhase.Spinning)
        animate(ChromePrizeWheelValue.WheelScale, 1.0, 0.0, ChromePrizeWheelMotion.holdSpring)
        animate(ChromePrizeWheelValue.HubPress, 0.0, 0.0, ChromePrizeWheelMotion.hubSpring)
        animate(ChromePrizeWheelValue.Hint, ChromePrizeWheelMotion.hintSpinning, ChromePrizeWheelMotion.hintDuration)
    }

    private fun takeOver() {
        if (!takeover && held) releaseWheel(sim.omega)
        takeover = true
        lastTouch = steps
        homeStep = -1
        animate(ChromePrizeWheelValue.TouchOn, 0.0, ChromePrizeWheelMotion.fingerFade)
    }

    private fun tickScene(before: Int) {
        if (sim.strikes > before) {
            values[ChromePrizeWheelValue.Strikes] += (sim.strikes - before).toDouble()
            values[ChromePrizeWheelValue.Tick] = 1.0
            if ((steps - lastHaptic) * ChromePrizeWheelStep >= ChromePrizeWheelMotion.hapticGap) {
                events = events or if (abs(sim.omega) < 4.0) ChromePrizeWheelEvent.Rigid else ChromePrizeWheelEvent.Light
                lastHaptic = steps
            }
        }
    }

    private fun stepReveal() {
        val age = steps - phaseStep
        val phase = values[ChromePrizeWheelValue.Phase].toInt()
        if (phase == ChromePrizeWheelPhase.Reveal && !config.reduced) {
            if (age == demoSteps(ChromePrizeWheelRevealTiming.launchAt)) {
                values[ChromePrizeWheelValue.WinnerPrize] = 0.0
                animate(ChromePrizeWheelValue.Hero, 1.0, 0.0, ChromePrizeWheelMotion.heroSpring)
                animate(ChromePrizeWheelValue.HeroSquash, 0.6, ChromePrizeWheelRevealTiming.stretch)
                animate(ChromePrizeWheelValue.Rays, 1.0, ChromePrizeWheelRevealTiming.rays)
                values[ChromePrizeWheelValue.Burst] = 0.0
                val winner = values[ChromePrizeWheelValue.Winner].toInt()
                values[ChromePrizeWheelValue.BurstPower] = if (winner == 1) 1.0 else min(0.85, 0.6 + 0.2 * ChromePrizeWheelPrizes[winner].count / 15.0)
                events = events or ChromePrizeWheelEvent.Success
            }
            if (age == demoSteps(ChromePrizeWheelRevealTiming.launchAt + ChromePrizeWheelRevealTiming.stretch)) {
                animate(ChromePrizeWheelValue.HeroSquash, 0.0, 0.0, ChromePrizeWheelMotion.squashSpring)
            }
            if (age == demoSteps(ChromePrizeWheelRevealTiming.badgeAt)) {
                animate(ChromePrizeWheelValue.Badge, 1.0, ChromePrizeWheelRevealTiming.badge, travel = true)
            }
            if (age == demoSteps(ChromePrizeWheelRevealTiming.captionAt)) {
                animate(ChromePrizeWheelValue.Caption, 1.0, ChromePrizeWheelRevealTiming.caption)
                val winner = values[ChromePrizeWheelValue.Winner].toInt()
                val count = ChromePrizeWheelPrizes[winner].count.toDouble()
                animate(ChromePrizeWheelValue.Count, count, min(ChromePrizeWheelRevealTiming.countCap, ChromePrizeWheelRevealTiming.countBase + ChromePrizeWheelRevealTiming.countPerPrize * count))
            }
            if (age == demoSteps(ChromePrizeWheelRevealTiming.buttonAt)) {
                animate(ChromePrizeWheelValue.Button, 1.0, ChromePrizeWheelRevealTiming.button, travel = true)
            }
        }
        if (phase == ChromePrizeWheelPhase.Collecting) {
            if (!config.reduced && age == demoSteps(ChromePrizeWheelCollectTiming.sinkAt)) {
                animate(ChromePrizeWheelValue.Lift, 0.0, 0.0, ChromePrizeWheelMotion.sinkSpring)
                animate(ChromePrizeWheelValue.Glow, 0.0, ChromePrizeWheelCollectTiming.glow)
                animate(ChromePrizeWheelValue.Dim, 0.0, ChromePrizeWheelCollectTiming.dim)
                animate(ChromePrizeWheelValue.WheelScale, 1.0, 0.0, ChromePrizeWheelMotion.wheelSpring)
            }
            if (!config.reduced && age == demoSteps(ChromePrizeWheelCollectTiming.swallowAt)) {
                animate(ChromePrizeWheelValue.ButtonPress, -1.0, ChromePrizeWheelCollectTiming.swallow)
            }
            if (!config.reduced && age == demoSteps(ChromePrizeWheelCollectTiming.swallowAt + ChromePrizeWheelCollectTiming.swallow)) {
                animate(ChromePrizeWheelValue.ButtonPress, 0.0, 0.0, ChromePrizeWheelMotion.swallowSpring)
            }
            val restockAt = if (config.reduced) ChromePrizeWheelMotion.reducedCollect else ChromePrizeWheelCollectTiming.restockAt
            if (age == demoSteps(restockAt)) {
                set(ChromePrizeWheelValue.Hero, 0.0)
                set(ChromePrizeWheelValue.Collect, 0.0)
                set(ChromePrizeWheelValue.Badge, 0.0)
                set(ChromePrizeWheelValue.Count, 0.0)
                values[ChromePrizeWheelValue.WinnerPrize] = 1.0
                if (!config.reduced) {
                    set(ChromePrizeWheelValue.WinnerPrizeScale, 1.28)
                    animate(ChromePrizeWheelValue.WinnerPrizeScale, 1.0, 0.0, ChromePrizeWheelMotion.restockSpring)
                }
            }
            if (!config.reduced && age == demoSteps(ChromePrizeWheelCollectTiming.buttonAt)) {
                animate(ChromePrizeWheelValue.Button, 0.0, ChromePrizeWheelCollectTiming.button, travel = true)
            }
            val done = if (config.reduced) ChromePrizeWheelMotion.reducedCollect else ChromePrizeWheelCollectTiming.done
            if (age >= demoSteps(done)) {
                values[ChromePrizeWheelValue.Winner] = -1.0
                values[ChromePrizeWheelValue.Comet] = 0.0
                phaseTo(ChromePrizeWheelPhase.Idle)
                animate(ChromePrizeWheelValue.Hint, 1.0, ChromePrizeWheelMotion.hintDuration)
            }
        }
    }

    private fun stepDemo() {
        val stage = demoStage
        val elapsed = steps - demoStep
        val second = stage >= 5
        val throwSpec = if (second) ChromePrizeWheelDemoScript.second else ChromePrizeWheelDemoScript.first
        if (stage == 0 || stage == 4 || stage == 8) {
            val wait = when (stage) {
                0 -> ChromePrizeWheelDemoScript.rest
                4 -> ChromePrizeWheelDemoScript.between
                else -> ChromePrizeWheelDemoScript.tail
            }
            if (elapsed < demoSteps(wait)) return
            if (stage == 8) {
                lastPeriodSteps = steps - loopStep
                loopStep = steps
                loops += 1
                sim = ChromePrizeWheelSim()
                targetTheta = 0.0
                values.resetPreservingStrikes()
                tweens.clear()
                tiltBaseX = 0.0
                tiltBaseY = 0.0
                tiltVelocityX = 0.0
                tiltVelocityY = 0.0
                demoStage = 0
                demoStep = steps
                return
            }
            demoStage = if (stage == 0) 1 else 5
            demoStep = steps
            demoStartTheta = sim.theta
            sim = ChromePrizeWheelSim(theta = sim.theta)
            targetTheta = sim.theta
            val spec = if (stage == 0) ChromePrizeWheelDemoScript.first else ChromePrizeWheelDemoScript.second
            values[ChromePrizeWheelValue.TouchX] = config.cx + spec.x * config.radius
            values[ChromePrizeWheelValue.TouchY] = config.cy + spec.y * config.radius
            animate(ChromePrizeWheelValue.TouchOn, 1.0, ChromePrizeWheelMotion.fingerFade)
            held = true
            phaseTo(ChromePrizeWheelPhase.Held)
            animate(ChromePrizeWheelValue.WheelScale, ChromePrizeWheelMotion.heldScale, 0.0, ChromePrizeWheelMotion.holdSpring)
        } else if (stage == 1 || stage == 5) {
            val dragStep = elapsed - demoSteps(ChromePrizeWheelDemoScript.fingerWait)
            if (dragStep > 0) {
                val angle = demoDrag(dragStep, throwSpec.duration, throwSpec.drag)
                targetTheta = demoStartTheta + angle
                val cosine = cos(angle)
                val sine = sin(angle)
                val x = throwSpec.x * cosine - throwSpec.y * sine
                val y = throwSpec.x * sine + throwSpec.y * cosine
                values[ChromePrizeWheelValue.TouchX] = config.cx + x * config.radius
                values[ChromePrizeWheelValue.TouchY] = config.cy + y * config.radius
                tiltTargetX = ChromePrizeWheelMotion.tiltGain * x
                tiltTargetY = ChromePrizeWheelMotion.tiltGain * y
                if (dragStep > demoSteps(throwSpec.duration)) {
                    releaseWheel(throwSpec.omega)
                    animate(ChromePrizeWheelValue.TouchOn, 0.0, ChromePrizeWheelMotion.fingerFade)
                    demoStage = stage + 1
                    demoStep = steps
                }
            }
        } else if (stage == 2 || stage == 6) {
            if (values[ChromePrizeWheelValue.Phase].toInt() == ChromePrizeWheelPhase.Reveal) {
                val age = steps - phaseStep
                if (age == demoSteps(throwSpec.admire - ChromePrizeWheelMotion.fingerFade)) {
                    values[ChromePrizeWheelValue.TouchX] = config.buttonX
                    values[ChromePrizeWheelValue.TouchY] = config.buttonY
                    animate(ChromePrizeWheelValue.TouchOn, 1.0, ChromePrizeWheelMotion.fingerFade)
                }
                if (age == demoSteps(throwSpec.admire)) {
                    animate(ChromePrizeWheelValue.ButtonPress, 1.0, ChromePrizeWheelCollectTiming.press)
                }
                if (age >= demoSteps(throwSpec.admire + ChromePrizeWheelDemoScript.press)) {
                    startCollect()
                    animate(ChromePrizeWheelValue.TouchOn, 0.0, ChromePrizeWheelMotion.fingerFade)
                    demoStage = stage + 1
                    demoStep = steps
                }
            }
        } else if ((stage == 3 || stage == 7) && values[ChromePrizeWheelValue.Phase].toInt() == ChromePrizeWheelPhase.Idle) {
            demoStage = stage + 1
            demoStep = steps
        }
    }

    private fun stepScene() {
        steps += 1
        if (config.autoPlay && !config.reduced && !takeover) stepDemo()
        if (config.autoPlay && !config.reduced && takeover && !touching && !held && !hubHeld && !heroHeld &&
            (steps - lastTouch) * ChromePrizeWheelStep >= ChromePrizeWheelMotion.takeoverDelay) {
            if (values[ChromePrizeWheelValue.Phase].toInt() == ChromePrizeWheelPhase.Reveal) startCollect()
            if (values[ChromePrizeWheelValue.Phase].toInt() == ChromePrizeWheelPhase.Idle && homeStep < 0) {
                homeStep = steps
                homeFrom = sim.theta
                homeTo = jsRound(sim.theta / ChromePrizeWheelTau) * ChromePrizeWheelTau
            }
        }
        val previousStrikes = sim.strikes
        if (homeStep >= 0) {
            val progress = (steps - homeStep) * ChromePrizeWheelStep / ChromePrizeWheelMotion.homeDuration
            stepWheel(homeFrom + (homeTo - homeFrom) * cubic(progress, ChromePrizeWheelMotion.travel))
            if (progress >= 1.0) {
                sim.theta = 0.0
                sim.omega = 0.0
                sim.contact = -1
                targetTheta = 0.0
                homeStep = -1
                takeover = false
                demoStage = 0
                demoStep = steps
                loopStep = steps
            }
        } else if (held && !config.reduced) {
            stepWheel(targetTheta)
        } else if (!config.reduced && (values[ChromePrizeWheelValue.Phase].toInt() == ChromePrizeWheelPhase.Spinning ||
                values[ChromePrizeWheelValue.Phase].toInt() == ChromePrizeWheelPhase.Landed)) {
            stepWheel()
            val counted = releaseSpeed >= ChromePrizeWheelPhysics.countedSpeed && sim.travel - releaseTravel >= ChromePrizeWheelPhysics.countedTravel
            if (sim.still > 0.0 && values[ChromePrizeWheelValue.Phase].toInt() == ChromePrizeWheelPhase.Spinning && counted) {
                values[ChromePrizeWheelValue.Winner] = winningWedge(sim.theta).toDouble()
                phaseTo(ChromePrizeWheelPhase.Landed)
            }
            if (sim.still > ChromePrizeWheelPhysics.stillBeat) {
                if (counted) {
                    startReveal()
                } else {
                    phaseTo(ChromePrizeWheelPhase.Idle)
                    animate(ChromePrizeWheelValue.Hint, 1.0, ChromePrizeWheelMotion.hintDuration)
                    values[ChromePrizeWheelValue.HintNudge] = 1.0
                }
            } else if (sim.still == 0.0 && values[ChromePrizeWheelValue.Phase].toInt() == ChromePrizeWheelPhase.Landed) {
                phaseTo(ChromePrizeWheelPhase.Spinning)
            }
        } else if (!config.reduced && !hubHeld) {
            stepWheel()
        }
        tickScene(previousStrikes)
        if (config.reduced && reducedStep >= 0) {
            val elapsed = steps - reducedStep
            if (elapsed == demoSteps(ChromePrizeWheelMotion.reducedFade)) {
                sim = ChromePrizeWheelSim(theta = ChromePrizeWheelGeometry.pointerAngle - reducedPick * ChromePrizeWheelGeometry.segmentAngle)
                animate(ChromePrizeWheelValue.WheelOpacity, 1.0, ChromePrizeWheelMotion.reducedFade)
            }
            if (elapsed >= demoSteps(2.0 * ChromePrizeWheelMotion.reducedFade)) {
                reducedStep = -1
                startReveal()
            }
        }
        stepReveal()
        stepTweens()
        values[ChromePrizeWheelValue.Theta] = sim.theta
        values[ChromePrizeWheelValue.Omega] = sim.omega
        values[ChromePrizeWheelValue.Swing] = sim.swing
        values[ChromePrizeWheelValue.SwayA] = if (config.reduced) 0.0 else clamp(
            sim.swayA + ChromePrizeWheelPhysics.swayLean * sim.omega,
            -ChromePrizeWheelPhysics.swayLimit,
            ChromePrizeWheelPhysics.swayLimit,
        )
        values[ChromePrizeWheelValue.SwayB] = if (config.reduced) 0.0 else sim.swayB
        values[ChromePrizeWheelValue.Tick] *= exp(-ChromePrizeWheelStep / ChromePrizeWheelMotion.tickDecay)
        values[ChromePrizeWheelValue.Swirl] += ChromePrizeWheelStep *
            (ChromePrizeWheelMotion.swirlGain * values[ChromePrizeWheelValue.Omega] - ChromePrizeWheelMotion.swirlDrag * values[ChromePrizeWheelValue.Swirl])
        val tiltX = oscillator(tiltBaseX - tiltTargetX, tiltVelocityX, ChromePrizeWheelMotion.holdSpring[0], ChromePrizeWheelMotion.holdSpring[1], ChromePrizeWheelStep)
        val tiltY = oscillator(tiltBaseY - tiltTargetY, tiltVelocityY, ChromePrizeWheelMotion.holdSpring[0], ChromePrizeWheelMotion.holdSpring[1], ChromePrizeWheelStep)
        tiltBaseX = tiltX.first + tiltTargetX
        tiltVelocityX = tiltX.second
        tiltBaseY = tiltY.first + tiltTargetY
        tiltVelocityY = tiltY.second
        val wobble = if (config.reduced) 0.0 else ChromePrizeWheelMotion.wobbleBase +
            ChromePrizeWheelMotion.wobbleSpeed * min(abs(values[ChromePrizeWheelValue.Omega]) / 20.0, 1.0)
        values[ChromePrizeWheelValue.TiltX] = tiltBaseX + wobble * cos(values[ChromePrizeWheelValue.Theta] + ChromePrizeWheelMotion.wobblePhase)
        values[ChromePrizeWheelValue.TiltY] = tiltBaseY + wobble * sin(values[ChromePrizeWheelValue.Theta] + ChromePrizeWheelMotion.wobblePhase)
        val closing = if (config.autoPlay && !takeover && demoStage == 8) {
            1.0 - cubic((steps - demoStep) * ChromePrizeWheelStep / ChromePrizeWheelDemoScript.tail, ChromePrizeWheelMotion.out)
        } else {
            1.0
        }
        values[ChromePrizeWheelValue.EnvTurn] = if (config.reduced) 0.0 else closing * ChromePrizeWheelMotion.environmentAmount *
            sin(ChromePrizeWheelTau * (steps - loopStep) * ChromePrizeWheelStep / ChromePrizeWheelMotion.environmentPeriod)
        if (values[ChromePrizeWheelValue.Burst] >= 0.0) values[ChromePrizeWheelValue.Burst] += ChromePrizeWheelStep
        if (values[ChromePrizeWheelValue.Phase].toInt() == ChromePrizeWheelPhase.Idle && values[ChromePrizeWheelValue.HintNudge] != 0.0) {
            val time = (steps - phaseStep) * ChromePrizeWheelStep
            values[ChromePrizeWheelValue.HintNudge] = if (time >= ChromePrizeWheelMotion.nudgeDuration) 0.0 else
                cos(ChromePrizeWheelTau * 2.0 * time / ChromePrizeWheelMotion.nudgeDuration) * (1.0 - time / ChromePrizeWheelMotion.nudgeDuration)
        }
    }

    fun advance(seconds: Double) {
        events = 0
        accumulator += max(0.0, seconds)
        val count = floor((accumulator + 1e-12) / ChromePrizeWheelStep).toInt()
        accumulator -= count * ChromePrizeWheelStep
        repeat(count) { stepScene() }
    }

    fun down(x: Double, y: Double): Boolean {
        events = 0
        touching = true
        takeOver()
        val phase = values[ChromePrizeWheelValue.Phase].toInt()
        if (phase !in ChromePrizeWheelPhase.Idle..ChromePrizeWheelPhase.Landed && phase != ChromePrizeWheelPhase.Held) return false
        val dx = x - config.cx
        val dy = y - config.cy
        val distance = sqrt(dx * dx + dy * dy)
        if (distance > config.radius) return false
        hubHeld = distance < config.radius * ChromePrizeWheelGeometry.hub * ChromePrizeWheelMotion.hubHitScale
        held = !hubHeld
        values[ChromePrizeWheelValue.Winner] = -1.0
        targetTheta = sim.theta
        fingerAngle = atan2(dy, dx)
        trackedRate = 0.0
        moveStep = steps
        sim.omega = 0.0
        phaseTo(ChromePrizeWheelPhase.Held)
        if (hubHeld) {
            animate(ChromePrizeWheelValue.HubPress, 1.0, ChromePrizeWheelMotion.hubPress)
        } else if (!config.reduced) {
            animate(ChromePrizeWheelValue.WheelScale, ChromePrizeWheelMotion.heldScale, 0.0, ChromePrizeWheelMotion.holdSpring)
            tiltTargetX = ChromePrizeWheelMotion.tiltGain * dx / config.radius
            tiltTargetY = ChromePrizeWheelMotion.tiltGain * dy / config.radius
        }
        return true
    }

    fun move(x: Double, y: Double) {
        if (!held) return
        lastTouch = steps
        if (config.reduced) return
        val dx = x - config.cx
        val dy = y - config.cy
        val angle = atan2(dy, dx)
        val delta = wrap(angle - fingerAngle)
        trackedRate += ChromePrizeWheelMotion.trackedBlend *
            (delta / (max(1, steps - moveStep) * ChromePrizeWheelStep) - trackedRate)
        targetTheta += delta
        fingerAngle = angle
        moveStep = steps
        tiltTargetX = ChromePrizeWheelMotion.tiltGain * clamp(dx / config.radius, -1.0, 1.0)
        tiltTargetY = ChromePrizeWheelMotion.tiltGain * clamp(dy / config.radius, -1.0, 1.0)
    }

    fun release(x: Double, y: Double, velocityX: Double, velocityY: Double, cancelled: Boolean, random: Double) {
        events = 0
        touching = false
        lastTouch = steps
        if (!held && !hubHeld) return
        if (cancelled) {
            held = false
            hubHeld = false
            tiltTargetX = 0.0
            tiltTargetY = 0.0
            if (config.reduced) phaseTo(ChromePrizeWheelPhase.Idle) else releaseWheel(0.0)
            animate(ChromePrizeWheelValue.HubPress, 0.0, if (config.reduced) ChromePrizeWheelMotion.reducedReveal else 0.0,
                if (config.reduced) null else ChromePrizeWheelMotion.hubSpring)
            return
        }
        if (config.reduced) {
            reducedSpin(random)
            animate(ChromePrizeWheelValue.HubPress, 0.0, ChromePrizeWheelMotion.reducedReveal)
            return
        }
        val dx = x - config.cx
        val dy = y - config.cy
        if (held && targetTheta != sim.theta) {
            val before = sim.strikes
            stepWheel(targetTheta)
            tickScene(before)
        }
        if (hubHeld) {
            events = events or ChromePrizeWheelEvent.Medium
            releaseWheel(ChromePrizeWheelMotion.hubSpeed + (random * 2.0 - 1.0) * ChromePrizeWheelMotion.hubVariation)
        } else {
            val distanceSquared = max(config.radius * config.radius * 0.01, dx * dx + dy * dy)
            val cross = (dx * velocityY - dy * velocityX) / distanceSquared
            releaseWheel(ChromePrizeWheelMotion.velocityBlend * cross + (1.0 - ChromePrizeWheelMotion.velocityBlend) * trackedRate)
        }
    }

    fun hub(random: Double) {
        events = 0
        takeOver()
        val phase = values[ChromePrizeWheelValue.Phase].toInt()
        if (phase != ChromePrizeWheelPhase.Idle && phase != ChromePrizeWheelPhase.Spinning && phase != ChromePrizeWheelPhase.Landed) return
        if (config.reduced) {
            reducedSpin(random)
        } else {
            events = events or ChromePrizeWheelEvent.Medium
            releaseWheel(ChromePrizeWheelMotion.hubSpeed + (random * 2.0 - 1.0) * ChromePrizeWheelMotion.hubVariation)
        }
    }

    fun heroDown() {
        touching = true
        takeOver()
        if (values[ChromePrizeWheelValue.Phase].toInt() == ChromePrizeWheelPhase.Reveal &&
            values[ChromePrizeWheelValue.Hero] > 0.9 && !config.reduced) {
            heroHeld = true
            heroOriginX = values[ChromePrizeWheelValue.HeroX]
            heroOriginY = values[ChromePrizeWheelValue.HeroY]
            set(ChromePrizeWheelValue.HeroX, heroOriginX)
            set(ChromePrizeWheelValue.HeroY, heroOriginY)
            set(ChromePrizeWheelValue.HeroTilt, values[ChromePrizeWheelValue.HeroTilt])
        }
    }

    fun heroMove(x: Double, y: Double, velocityX: Double) {
        if (!heroHeld) return
        lastTouch = steps
        values[ChromePrizeWheelValue.HeroX] = heroOriginX + x * ChromePrizeWheelMotion.heroPull
        values[ChromePrizeWheelValue.HeroY] = heroOriginY + y * ChromePrizeWheelMotion.heroPull
        values[ChromePrizeWheelValue.HeroTilt] = clamp(
            velocityX * ChromePrizeWheelMotion.heroTiltGain,
            -ChromePrizeWheelMotion.heroTiltCap,
            ChromePrizeWheelMotion.heroTiltCap,
        )
    }

    fun heroRelease() {
        touching = false
        heroHeld = false
        lastTouch = steps
        if (!config.reduced) {
            animate(ChromePrizeWheelValue.HeroX, 0.0, 0.0, ChromePrizeWheelMotion.toySpring)
            animate(ChromePrizeWheelValue.HeroY, 0.0, 0.0, ChromePrizeWheelMotion.toySpring)
            animate(ChromePrizeWheelValue.HeroTilt, 0.0, 0.0, ChromePrizeWheelMotion.toySpring)
        }
    }

    fun buttonDown() {
        touching = true
        takeOver()
        if (values[ChromePrizeWheelValue.Phase].toInt() == ChromePrizeWheelPhase.Reveal) {
            animate(ChromePrizeWheelValue.ButtonPress, 1.0, ChromePrizeWheelCollectTiming.press)
        }
    }

    fun buttonUp() {
        touching = false
        lastTouch = steps
        animate(
            ChromePrizeWheelValue.ButtonPress,
            0.0,
            if (config.reduced) ChromePrizeWheelMotion.reducedReveal else 0.0,
            if (config.reduced) null else ChromePrizeWheelMotion.swallowSpring,
        )
    }
}

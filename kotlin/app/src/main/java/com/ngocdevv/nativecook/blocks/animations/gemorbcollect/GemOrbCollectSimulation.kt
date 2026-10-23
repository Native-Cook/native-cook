package com.ngocdevv.nativecook.blocks.animations.gemorbcollect

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tanh
import kotlin.math.roundToInt

internal class GemOrbCollectSimulation {
    enum class Phase { BEFORE, COLLECTING, HUSH, BURST, AFTER, REWIND }

    data class Orb(
        val homeX: Float,
        val homeY: Float,
        val homeZ: Float,
        val targetX: Float,
        val targetY: Float,
        val targetZ: Float,
        val coreSize: Float,
        val tint: Int,
        val launch: Float,
        val duration: Float,
    )

    data class Frame(
        val phase: Phase,
        val clock: Float,
        val sequenceAge: Float,
        val charge: Float,
        val flare: Float,
        val hush: Float,
        val shock: Float,
        val beams: Float,
        val yaw: Float,
        val pitch: Float,
        val scale: Float,
        val stretch: Float,
        val tally: Int,
        val tallyAlpha: Float,
        val balance: Float,
        val copy: Float,
        val buttonCopy: Float,
        val buttonEnabled: Boolean,
        val buttonPressed: Boolean,
        val capsuleGlow: Float,
        val beadProgress: Float,
        val rewindProgress: Float,
    )

    var autoPlay = true
    var reducedMotion = false
    var touching = false
        private set
    var buttonPressed = false
        private set

    private var clock = 0f
    private var manualPhase = Phase.BEFORE
    private var manualPhaseAge = 0f
    private var holdAge = 0f
    private var dragOnly = false
    private var holdAccepted = false
    private var manualAutomatic = false
    private var manualCadence = 0f
    private var manualCollectionDuration = COLLECTION_DURATION
    private val manualLaunchTimes = FloatArray(20) { -1f }
    private val manualStruck = BooleanArray(20)
    private var hushStartCharge = 1f
    private var rewindCharge = 1f
    private var rewindBalance = 1600f
    private var rewindHits = 20
    private var rewindCopy = 1f
    private var rewindButtonCopy = 1f
    private var turn = 0f
    private var turnVelocity = 0f
    private var tilt = 0f
    private var tiltVelocity = 0f
    private var manualScale = 1f
    private var manualScaleSpeed = 0f
    private var hushStartScale = 1f
    private var quiet = 0f
    private var interrupted = false
    private var lastX = 0f
    private var lastY = 0f
    private var downX = 0f
    private var downY = 0f
    private var velocityX = 0f
    private var velocityY = 0f
    private lateinit var layout: GemOrbCollectLayout
    private var orbs = emptyList<Orb>()
    private var scriptedImpacts = Array(1) { floatArrayOf(0f, 0f, 1f) }
    private var scriptedLightSlots = Array(1) { IntArray(6) { -1 } }
    private var manualLightSlots = Array(1) { IntArray(6) { -1 } }

    fun resize(next: GemOrbCollectLayout) {
        layout = next
        orbs = makeOrbs(next)
        scriptedImpacts = buildImpactAttitude()
        scriptedLightSlots = buildLightSlots(FloatArray(orbs.size) { orbs[it].launch })
        manualLightSlots = buildLightSlots(manualLaunchTimes)
    }

    fun step(seconds: Float) {
        val elapsed = seconds.coerceIn(0f, 0.5f)
        if (autoPlay && !interrupted) {
            // Autoplay is a fixed-duration presentation, so it follows wall time even
            // when an emulator drops render frames while recording.
            clock = (clock + elapsed) % LOOP
            return
        }
        val dt = elapsed.coerceAtMost(0.1f)
        clock += dt
        if (autoPlay && interrupted) {
            quiet = if (touching) 0f else quiet + dt
            if (quiet >= RESUME_AFTER) {
                if (manualPhase != Phase.BEFORE && manualPhase != Phase.REWIND) {
                    enterRewind()
                } else if (manualPhase == Phase.BEFORE) {
                    interrupted = false
                    clock = 0f
                    quiet = 0f
                    turn = 0f
                    tilt = 0f
                }
            }
        }
        if (touching && manualPhase == Phase.BEFORE && !dragOnly) {
            holdAge += dt
            if (holdAge >= HOLD_DELAY) {
                holdAccepted = true
                startCollecting(false)
            }
        }
        if (manualPhase != Phase.BEFORE) {
            manualPhaseAge += dt
            if (manualPhase == Phase.COLLECTING && (manualAutomatic || (touching && holdAccepted))) {
                manualCadence += dt
                var launched = false
                orbs.forEachIndexed { index, orb ->
                    if (manualLaunchTimes[index] < 0f && manualCadence + 0.00001f >= orb.launch) {
                        manualLaunchTimes[index] = manualPhaseAge
                        launched = true
                    }
                }
                if (launched) manualLightSlots = buildLightSlots(manualLaunchTimes)
            }
            if (manualPhase == Phase.COLLECTING) {
                orbs.forEachIndexed { index, orb ->
                    if (!manualStruck[index] && manualLaunchTimes[index] >= 0f &&
                        manualPhaseAge >= manualLaunchTimes[index] + orb.duration) {
                        manualStruck[index] = true
                        manualScaleSpeed += 0.975f
                        turnVelocity += orb.targetX * 1.4f
                    }
                }
            }
            advanceManualPhase()
        }
        if (manualPhase != Phase.HUSH) {
            val pressed = touching && manualPhase != Phase.BURST
            val target = if (pressed) 0.97f else 1f
            val stiffness = if (pressed) 420f else 220f
            val damping = if (pressed) 30f else 11f
            manualScaleSpeed += (stiffness * (target - manualScale) - damping * manualScaleSpeed) * dt
            manualScale += manualScaleSpeed * dt
        }
        if (touching) {
            turn += turnVelocity * dt
            tilt += tiltVelocity * dt
            turnVelocity *= exp(-12.25f * dt)
            tiltVelocity *= exp(-12.25f * dt)
        } else {
            val damping = if (manualPhase == Phase.HUSH || manualPhase == Phase.BURST) 24f else 12.25f
            val nearest = (turn / TAU).roundToInt() * TAU
            turnVelocity += (66.7f * (nearest - turn) - damping * turnVelocity) * dt
            tiltVelocity += (66.7f * (0f - tilt) - damping * tiltVelocity) * dt
            turn += turnVelocity * dt
            tilt += tiltVelocity * dt
        }
    }

    fun frame(): Frame = if (autoPlay && !interrupted) scriptedFrame(clock) else manualFrame()

    fun orbs(): List<Orb> = orbs

    fun shaderLights(frame: Frame, output: FloatArray, point: FloatArray) {
        output.fill(0f)
        if (reducedMotion || frame.phase == Phase.BEFORE || frame.phase == Phase.REWIND) return
        val age = collectionAge(frame)
        val cache = if (autoPlay && !interrupted) scriptedLightSlots else manualLightSlots
        val slots = cache[(age * 240f).toInt().coerceIn(0, cache.lastIndex)]
        slots.forEachIndexed { slot, index ->
            if (index < 0) return@forEachIndexed
            val orb = orbs[index]
            orbPosition(index, orb, frame, point)
            val launch = if (!autoPlay || interrupted) manualLaunchTimes[index] else orb.launch
            val hitAge = age - launch - orb.duration
            val absorption = 1f - GemOrbCollectMath.ease((hitAge - 0.09f) / 0.28f)
            val strength = when {
                hitAge < 0f -> 0.3f + 0.7f * GemOrbCollectMath.clamp((1.5f - hypot(hypot(point[0], point[1]), point[2])) / 0.68f)
                hitAge < 0.09f -> 3f * (1f - hitAge / 0.09f)
                else -> -2f * absorption
            }
            val offset = slot * 4
            val positionFactor = if (hitAge >= 0.09f) absorption else 1f
            output[offset] = point[0] * positionFactor
            output[offset + 1] = point[1] * positionFactor
            output[offset + 2] = point[2] * positionFactor
            output[offset + 3] = strength
        }
    }

    private fun buildLightSlots(launches: FloatArray): Array<IntArray> {
        val count = ((COLLECTION_DURATION + HUSH_DURATION + 0.5f) * 240f).toInt() + 1
        val result = Array(count) { IntArray(6) { -1 } }
        val slots = IntArray(6) { -1 }
        val status = IntArray(orbs.size)
        var hits = 0
        for (tick in 0 until count) {
            val age = tick / 240f
            for (index in orbs.indices) {
                val launch = launches[index]
                val duration = orbs[index].duration
                if (status[index] == 0 && launch >= 0f && age >= launch) status[index] = 1
                if (status[index] == 1 && age >= launch + duration) {
                    status[index] = 2
                    hits++
                }
                if (status[index] == 2 && age >= launch + duration + 0.37f) {
                    status[index] = 3
                    for (slot in slots.indices) if (slots[slot] == index) slots[slot] = -1
                }
            }
            for (index in orbs.indices) {
                if (status[index] != 2 || slots.any { it == index }) continue
                if (slots.any { it < 0 }) continue
                var weakest = -1
                var strength = 0.01f
                for (slot in slots.indices) {
                    val owner = slots[slot]
                    val hitAge = age - launches[owner] - orbs[owner].duration
                    if (status[owner] != 2 || hitAge < 0.09f) continue
                    val tail = 2f * (1f - GemOrbCollectMath.ease((hitAge - 0.09f) / 0.28f))
                    if (tail < strength) {
                        strength = tail
                        weakest = slot
                    }
                }
                if (weakest >= 0) {
                    status[slots[weakest]] = 3
                    slots[weakest] = -1
                }
            }
            val gap = max(0.05f, 0.26f * 0.9f.pow(hits))
            for (slot in slots.indices) {
                if (slots[slot] >= 0) continue
                var remaining = max(0.012f, gap * 6f - 0.09f - 0.28f - 0.01f)
                var selected = -1
                for (index in orbs.indices) {
                    if (status[index] != 1 && status[index] != 2) continue
                    if (slots.any { it == index }) continue
                    val untilHit = launches[index] + orbs[index].duration - age
                    if (untilHit < remaining) {
                        remaining = untilHit
                        selected = index
                    }
                }
                slots[slot] = selected
            }
            result[tick] = slots.copyOf()
        }
        return result
    }

    fun orbPosition(index: Int, orb: Orb, frame: Frame, output: FloatArray) {
        val collectionAge = collectionAge(frame)
        val launch = if (!autoPlay || interrupted) manualLaunchTimes[index] else orb.launch
        val travelAge = if ((!autoPlay || interrupted) && frame.phase == Phase.COLLECTING) frame.sequenceAge else collectionAge
        val progress = if (launch < 0f) 0f else GemOrbCollectMath.clamp((travelAge - launch) / orb.duration)
        if (reducedMotion && frame.phase != Phase.BEFORE) {
            output[0] = orb.homeX
            output[1] = orb.homeY
            output[2] = orb.homeZ
            output[3] = when (frame.phase) {
                Phase.COLLECTING -> 1f - GemOrbCollectMath.clamp((frame.sequenceAge - index * 0.015f) / 0.3f)
                Phase.REWIND -> GemOrbCollectMath.clamp((frame.sequenceAge - index * 0.015f) / 0.3f)
                else -> 0f
            }
            output[4] = 1f
            return
        }
        if (frame.phase == Phase.REWIND) {
            val u = GemOrbCollectMath.clamp(frame.rewindProgress - orb.tint * 0.012f)
            val settle = 1f - exp(-7f * u) * cos(8f * u)
            output[0] = orb.targetX + (orb.homeX - orb.targetX) * settle
            output[1] = orb.targetY + (orb.homeY - orb.targetY) * settle
            output[2] = orb.targetZ + (orb.homeZ - orb.targetZ) * settle
            output[3] = GemOrbCollectMath.clamp(u / 0.08f)
            output[4] = 1f + 2f * (1f - u)
            return
        }
        if (launch < 0f || travelAge < launch) {
            val drift = if (reducedMotion) 0f else sin(frame.clock * TAU / 6f + orb.tint * 1.7f) * 0.045f
            output[0] = orb.homeX + drift
            output[1] = orb.homeY + drift * 0.6f
            output[2] = orb.homeZ
            output[3] = 0.55f + 0.18f * sin(frame.clock * TAU / 3f + orb.tint)
            output[4] = 1f
            return
        }
        if (progress >= 1f) {
            output[0] = orb.targetX
            output[1] = orb.targetY
            output[2] = orb.targetZ
            val sinceHit = travelAge - launch - orb.duration
            output[3] = 1f - GemOrbCollectMath.clamp(sinceHit / 0.09f)
            output[4] = 5f
            return
        }
        val angleStart = atan2(orb.homeY, orb.homeX)
        var arc = atan2(orb.targetY, orb.targetX) - angleStart
        if (arc > PI) arc -= TAU
        if (arc < -PI) arc += TAU
        if (progress < 0.14f) {
            val push = sin(progress / 0.14f * PI.toFloat() / 2f) * 12f / layout.gemRadius
            output[0] = orb.homeX + cos(angleStart) * push
            output[1] = orb.homeY + sin(angleStart) * push
            output[2] = orb.homeZ
        } else {
            val u = (progress - 0.14f) / 0.86f
            val inward = u.toDouble().pow(3.5).toFloat()
            val radius = (hypot(orb.homeX, orb.homeY) + 12f / layout.gemRadius) * (1f - inward) + hypot(orb.targetX, orb.targetY) * inward
            val angle = angleStart + arc * u * u
            val x = radius * cos(angle)
            val y = radius * sin(angle)
            output[2] = orb.homeZ * (1f - inward) + orb.targetZ * inward + sin(PI.toFloat() * u) * if (orb.homeZ < 0f) -0.22f else 0.15f
            val perspective = layout.gemRadius * 7f / (7f - output[2])
            val px = x * perspective
            val py = -y * perspective
            val halo = orb.coreSize * 7f / (7f - output[2]) * 2f * layout.unit
            val rx = abs(px) / max(1f, layout.gemX - 20f * layout.unit - halo)
            val ry = abs(py) / max(1f, if (py > 0f) layout.tallyY - 24f * layout.unit - halo - layout.gemY else layout.gemY - 20f * layout.unit - halo)
            val cap = 1f / (1f + rx.pow(8) + ry.pow(8)).pow(0.125f)
            val weight = GemOrbCollectMath.clamp((sqrt(x * x + y * y + output[2] * output[2]) - 0.9f) / 0.35f)
            val fit = 1f + (cap - 1f) * weight * GemOrbCollectMath.ease(u / 0.35f)
            output[0] = x * fit
            output[1] = y * fit
        }
        output[3] = 0.65f + 0.35f * progress
        output[4] = 1f + 4f * progress * progress * progress
    }

    fun onGemDown(x: Float, y: Float): Boolean {
        val distance = hypot(x - layout.gemX, y - layout.gemY)
        if (distance > layout.gemRadius * 1.25f) return false
        interrupt()
        touching = true
        holdAge = 0f
        dragOnly = false
        holdAccepted = false
        downX = x
        downY = y
        lastX = x
        lastY = y
        velocityX = 0f
        velocityY = 0f
        return true
    }

    fun onGemMove(x: Float, y: Float, seconds: Float) {
        if (!touching) return
        val dx = x - lastX
        val dy = y - lastY
        if (!dragOnly && !holdAccepted && hypot(x - downX, y - downY) > HOLD_SLOP) dragOnly = true
        turn += dx / layout.gemRadius
        if (dy != 0f) tilt = tanh((tilt + dy / layout.gemRadius) / 1.1f) * 1.1f
        if (dx != 0f || dy != 0f) {
            turnVelocity = 0f
            tiltVelocity = 0f
        }
        velocityX = dx / max(seconds, 1f / 240f)
        velocityY = dy / max(seconds, 1f / 240f)
        lastX = x
        lastY = y
    }

    fun onGemUp() {
        if (!touching) return
        if (holdAge < HOLD_DELAY && !dragOnly) {
            turnVelocity += 0.12f
            if (!reducedMotion) manualScaleSpeed += 1.6f
        }
        if (!reducedMotion && abs(velocityX) > 0.01f) turnVelocity = velocityX / max(layout.gemRadius, 1f)
        if (!reducedMotion && abs(velocityY) > 0.01f) tiltVelocity = velocityY / max(layout.gemRadius, 1f)
        touching = false
        holdAccepted = false
    }

    fun onButtonDown(x: Float, y: Float): Boolean {
        if (!buttonContains(x, y)) return false
        val phase = frame().phase
        if (phase != Phase.BEFORE && phase != Phase.AFTER) return false
        interrupt()
        touching = true
        buttonPressed = true
        return true
    }

    fun onButtonUp(x: Float, y: Float) {
        if (!buttonPressed) return
        val inside = buttonContains(x, y)
        val phase = frame().phase
        buttonPressed = false
        touching = false
        if (!inside) return
        if (phase == Phase.BEFORE) startCollecting(true)
        if (phase == Phase.AFTER) {
            enterRewind()
        }
    }

    fun cancelTouch() {
        touching = false
        buttonPressed = false
        holdAccepted = false
    }

    fun accessibilityCollect() {
        interrupt()
        if (manualPhase == Phase.BEFORE) startCollecting(true)
    }

    fun accessibilityButton() {
        interrupt()
        when (manualPhase) {
            Phase.BEFORE -> startCollecting(true)
            Phase.AFTER -> {
                enterRewind()
            }
            else -> Unit
        }
    }

    private fun interrupt() {
        if (!autoPlay || interrupted) return
        val current = scriptedFrame(clock)
        manualPhase = current.phase
        manualPhaseAge = when (current.phase) {
            Phase.BEFORE -> 0f
            Phase.COLLECTING -> collectionAge(current)
            Phase.HUSH -> clock - HUSH_START
            Phase.BURST, Phase.AFTER -> clock - BURST_START
            Phase.REWIND -> clock - REWIND_START
        }
        manualCadence = if (current.phase == Phase.COLLECTING) manualPhaseAge else COLLECTION_DURATION
        manualCollectionDuration = if (current.phase == Phase.COLLECTING) manualPhaseAge else COLLECTION_DURATION
        manualAutomatic = false
        hushStartCharge = current.charge
        manualScale = current.scale
        manualScaleSpeed = 0f
        tiltVelocity = 0f
        manualLaunchTimes.indices.forEach { index ->
            manualLaunchTimes[index] = if (current.phase == Phase.COLLECTING && orbs[index].launch <= manualCadence) orbs[index].launch else -1f
            manualStruck[index] = manualLaunchTimes[index] >= 0f && manualPhaseAge >= manualLaunchTimes[index] + orbs[index].duration
        }
        manualLightSlots = buildLightSlots(manualLaunchTimes)
        interrupted = true
        quiet = 0f
    }

    private fun startCollecting(automatic: Boolean) {
        if (manualPhase != Phase.BEFORE) return
        manualPhase = Phase.COLLECTING
        manualPhaseAge = 0f
        manualCadence = 0f
        manualCollectionDuration = 0f
        manualAutomatic = automatic
        manualLaunchTimes.fill(-1f)
        manualStruck.fill(false)
        manualLightSlots = buildLightSlots(manualLaunchTimes)
    }

    private fun enterRewind() {
        val current = frame()
        rewindCharge = current.charge
        rewindBalance = current.balance
        rewindHits = current.tally / 10
        rewindCopy = current.copy
        rewindButtonCopy = current.buttonCopy
        manualPhase = Phase.REWIND
        manualPhaseAge = 0f
        manualAutomatic = false
    }

    private fun advanceManualPhase() {
        when (manualPhase) {
            Phase.COLLECTING -> if (manualPhaseAge >= (if (reducedMotion) 0.6f else 0f) &&
                (reducedMotion || orbs.indices.all { index ->
                    manualLaunchTimes[index] >= 0f && manualPhaseAge >= manualLaunchTimes[index] + orbs[index].duration
                })) {
                hushStartCharge = manualChargeAt(manualPhaseAge)
                hushStartScale = manualScale
                manualCollectionDuration = manualPhaseAge
                manualPhase = if (reducedMotion) Phase.AFTER else Phase.HUSH
                manualPhaseAge = 0f
            }
            Phase.HUSH -> if (manualPhaseAge >= HUSH_DURATION) {
                manualPhase = Phase.BURST
                manualPhaseAge = 0f
            }
            Phase.BURST -> if (manualPhaseAge >= 2f) {
                manualPhase = Phase.AFTER
            }
            Phase.REWIND -> if (manualPhaseAge >= REWIND_DURATION) {
                manualPhase = Phase.BEFORE
                manualPhaseAge = 0f
            }
            else -> Unit
        }
    }

    private fun scriptedFrame(time: Float): Frame {
        val phase = if (reducedMotion) when {
            time < COLLECT_START -> Phase.BEFORE
            time < COLLECT_START + 0.6f -> Phase.COLLECTING
            time < REWIND_START -> Phase.AFTER
            time < REWIND_END -> Phase.REWIND
            else -> Phase.BEFORE
        } else when {
            time < COLLECT_START -> Phase.BEFORE
            time < HUSH_START -> Phase.COLLECTING
            time < BURST_START -> Phase.HUSH
            time < AFTER_START -> Phase.BURST
            time < REWIND_START -> Phase.AFTER
            time < REWIND_END -> Phase.REWIND
            else -> Phase.BEFORE
        }
        val age = when (phase) {
            Phase.COLLECTING -> time - COLLECT_START
            Phase.HUSH -> time - HUSH_START
            Phase.BURST -> time - BURST_START
            Phase.AFTER -> time - if (reducedMotion) COLLECT_START - 0.6f else BURST_START
            Phase.REWIND -> time - REWIND_START
            Phase.BEFORE -> 0f
        }
        val scriptedTurn = if (!reducedMotion && time < BURST_START) {
            scriptedImpacts[(time * 240f).toInt().coerceIn(0, scriptedImpacts.lastIndex)][0]
        } else if (!reducedMotion && time in 7.2f..<8.9f) {
            val right = 120f * (GemOrbCollectMath.clamp((time - 7.2f) / 0.6f))
            val left = 190f * (GemOrbCollectMath.clamp((time - 8.2f) / 0.7f))
            (right - left) / layout.gemRadius
        } else 0f
        return values(phase, age, time, scriptedTurn)
    }

    private fun manualFrame(): Frame = values(manualPhase, manualPhaseAge, clock, turn)

    private fun values(phase: Phase, age: Float, absolute: Float, userTurn: Float): Frame {
        val collection = when (phase) {
            Phase.COLLECTING -> age
            Phase.HUSH, Phase.BURST, Phase.AFTER -> COLLECTION_DURATION
            Phase.REWIND -> COLLECTION_DURATION
            Phase.BEFORE -> 0f
        }
        val manual = !autoPlay || interrupted
        val hits = if (manual && phase == Phase.REWIND) rewindHits else if (manual && phase == Phase.COLLECTING) {
            orbs.indices.count { index ->
                manualLaunchTimes[index] >= 0f && age >= manualLaunchTimes[index] + orbs[index].duration
            }
        } else orbs.count { collection >= it.launch + it.duration }
        val burstAge = when (phase) {
            Phase.BURST, Phase.AFTER -> age
            else -> -1f
        }
        val charge = when (phase) {
            Phase.BEFORE -> 0f
            Phase.COLLECTING -> if (reducedMotion) GemOrbCollectMath.clamp(age / 0.5f) else if (autoPlay && !interrupted) chargeAt(age) else manualChargeAt(age)
            Phase.HUSH -> {
                val from = if (autoPlay && !interrupted) chargeAt(COLLECTION_DURATION) else hushStartCharge
                from + (0.35f - from) * GemOrbCollectMath.ease(age / HUSH_DURATION, 0.77f, 0f, 0.175f, 1f)
            }
            Phase.REWIND -> (if (manual) rewindCharge else 1f) * (1f - GemOrbCollectMath.ease(age / 0.6f))
            else -> 1f
        }
        val flare = if (reducedMotion && phase == Phase.COLLECTING) {
            0.35f * sin(PI.toFloat() * GemOrbCollectMath.clamp(age / 0.6f))
        } else if (!reducedMotion && burstAge >= 0f && burstAge < 1.9f) {
            if (burstAge < 0.09f) burstAge / 0.09f else exp(-(burstAge - 0.09f) / 0.42f) * GemOrbCollectMath.clamp((1.9f - burstAge) / 0.2f)
        } else 0f
        val copy = when {
            reducedMotion && phase == Phase.COLLECTING -> GemOrbCollectMath.clamp(age / 0.2f)
            reducedMotion && phase == Phase.AFTER -> 1f
            phase == Phase.REWIND -> (if (manual) rewindCopy else 1f) * (1f - GemOrbCollectMath.ease(age / 0.3f, 0.77f, 0f, 0.175f, 1f))
            phase == Phase.BURST || phase == Phase.AFTER -> GemOrbCollectMath.ease((burstAge - 0.2f) / 0.3f, 0.77f, 0f, 0.175f, 1f)
            else -> 0f
        }
        val landAge = burstAge - 0.25f - 0.16f - 0.5f
        val balance = if (reducedMotion) when (phase) {
            Phase.COLLECTING -> if (age >= 0.3f) 1600f else 1400f
            Phase.AFTER -> 1600f
            Phase.REWIND -> if (age < 0.3f) (if (manual) rewindBalance else 1600f) else 1400f
            else -> 1400f
        } else if (phase == Phase.REWIND) {
            val from = if (manual) rewindBalance else 1600f
            from + (1400f - from) * GemOrbCollectMath.ease(age / 0.6f)
        }
        else 1400f + 200f * GemOrbCollectMath.ease(landAge / 1.1f)
        val idleYaw = if (reducedMotion) 0f else sin(TAU * absolute / 12f) * if (phase == Phase.AFTER) 0.28f else 0.22f
        val spinOrigin = if (autoPlay && !interrupted && !reducedMotion) scriptedImpacts[(BURST_START * 240f).toInt()][0] else 0f
        val spin = if (!reducedMotion && burstAge in 0f..<1.5f) {
            val progress = GemOrbCollectMath.ease(burstAge / 1.5f, 0.16f, 1f, 0.3f, 1f)
            spinOrigin - 0.18f + (TAU + 0.18f) * progress
        } else if (burstAge >= 1.5f) TAU + spinOrigin else 0f
        val afterSway = if (!reducedMotion && phase == Phase.AFTER) 0.18f * sin(PI.toFloat() * GemOrbCollectMath.clamp(age / 1.8f)) else 0f
        val scriptedTilt = if (autoPlay && !interrupted && !reducedMotion) when {
            absolute < BURST_START -> scriptedImpacts[(absolute * 240f).toInt().coerceIn(0, scriptedImpacts.lastIndex)][1]
            burstAge in 0f..<1.5f -> -0.25f * sin(PI.toFloat() * GemOrbCollectMath.clamp(burstAge / 1.5f))
            else -> 0f
        } else tilt
        val rewind = if (phase == Phase.REWIND) GemOrbCollectMath.clamp(age / REWIND_DURATION) else 0f
        val scriptedScale = if (autoPlay && !interrupted && !reducedMotion) scriptedImpacts[(absolute * 240f).toInt().coerceIn(0, scriptedImpacts.lastIndex)][2] else -1f
        return Frame(
            phase = phase,
            clock = absolute,
            sequenceAge = age,
            charge = charge,
            flare = flare,
            hush = if (phase == Phase.HUSH) GemOrbCollectMath.ease(age / HUSH_DURATION, 0.77f, 0f, 0.175f, 1f) else if (burstAge >= 0f) 1f - GemOrbCollectMath.clamp(burstAge / 0.15f) else 0f,
            shock = if (burstAge in 0f..0.9f && !reducedMotion) 1f - (1f - burstAge / 0.9f).pow(3) else 0f,
            beams = if (burstAge >= 0f && !reducedMotion) GemOrbCollectMath.clamp(burstAge / 0.12f) * (1f - GemOrbCollectMath.ease((burstAge - 0.65f) / 0.95f)) else 0f,
            yaw = -0.16f + userTurn + idleYaw * (1f - if (phase == Phase.HUSH) GemOrbCollectMath.ease(age / HUSH_DURATION, 0.77f, 0f, 0.175f, 1f) else 0f) + spin + afterSway - if (phase == Phase.HUSH) 0.18f * GemOrbCollectMath.ease(age / HUSH_DURATION, 0.77f, 0f, 0.175f, 1f) else 0f,
            pitch = 0.1f + scriptedTilt + if (reducedMotion) 0f else cos(TAU * absolute / 6f) * 0.07f * (1f - if (phase == Phase.HUSH) GemOrbCollectMath.ease(age / HUSH_DURATION, 0.77f, 0f, 0.175f, 1f) else 0f),
            scale = if (scriptedScale >= 0f) scriptedScale else when (phase) {
                Phase.HUSH -> {
                    val from = if (manual) hushStartScale else 1.06f
                    from + (0.86f - from) * GemOrbCollectMath.ease(age / HUSH_DURATION, 0.77f, 0f, 0.175f, 1f)
                }
                Phase.BURST -> 1f - 0.14f * exp(-age * 15f) * cos(age * 20f)
                else -> if (manual) manualScale else if (reducedMotion && absolute in 1.2f..<4.6f) 0.97f else 1f
            },
            stretch = if (autoPlay && !interrupted && !reducedMotion) {
                scriptedImpacts[(absolute * 240f).toInt().coerceIn(0, scriptedImpacts.lastIndex)][3]
            } else if (phase == Phase.HUSH) {
                1f - 0.06f * GemOrbCollectMath.ease(age / HUSH_DURATION, 0.77f, 0f, 0.175f, 1f)
            } else if (phase == Phase.BURST) {
                1f + 0.06f * exp(-7.5f * age) * cos(13f * age)
            } else 1f,
            tally = hits * 10,
            tallyAlpha = if (hits > 0 && !reducedMotion && phase != Phase.REWIND) 1f - GemOrbCollectMath.clamp((burstAge - 0.25f) / 0.16f) else 0f,
            balance = balance,
            copy = copy,
            buttonCopy = if (phase == Phase.AFTER) GemOrbCollectMath.ease((age - if (reducedMotion) 0f else 2f) / if (reducedMotion) 0.2f else 0.3f, 0.77f, 0f, 0.175f, 1f) else if (phase == Phase.REWIND) (if (manual) rewindButtonCopy else 1f) * (1f - GemOrbCollectMath.ease(age / 0.3f, 0.77f, 0f, 0.175f, 1f)) else 0f,
            buttonEnabled = phase == Phase.BEFORE || phase == Phase.AFTER,
            buttonPressed = buttonPressed,
            capsuleGlow = sin(PI.toFloat() * GemOrbCollectMath.clamp(landAge / 0.9f)),
            beadProgress = if (burstAge >= 0f) GemOrbCollectMath.clamp((burstAge - 0.41f) / 0.5f) else 0f,
            rewindProgress = rewind,
        )
    }

    private fun collectionAge(frame: Frame): Float = when (frame.phase) {
        Phase.COLLECTING -> frame.sequenceAge
        Phase.HUSH -> (if (autoPlay && !interrupted) COLLECTION_DURATION else manualCollectionDuration) + frame.sequenceAge
        Phase.BURST, Phase.AFTER -> (if (autoPlay && !interrupted) COLLECTION_DURATION else manualCollectionDuration) + HUSH_DURATION + frame.sequenceAge
        Phase.REWIND -> COLLECTION_DURATION + HUSH_DURATION + 2f
        Phase.BEFORE -> if (frame.clock in REWIND_END..LOOP) -1f else 0f
    }

    private fun chargeAt(age: Float): Float = orbs.sumOf { orb ->
        val sinceHit = age - orb.launch - orb.duration
        if (sinceHit <= 0f) 0.0 else (0.05f * (1f - exp(-sinceHit / 0.25f))).toDouble()
    }.toFloat()

    private fun manualChargeAt(age: Float): Float = orbs.indices.sumOf { index ->
        val launch = manualLaunchTimes[index]
        val sinceHit = age - launch - orbs[index].duration
        if (launch < 0f || sinceHit <= 0f) 0.0 else (0.05f * (1f - exp(-sinceHit / 0.25f))).toDouble()
    }.toFloat()

    private fun buttonContains(x: Float, y: Float): Boolean =
        x in layout.buttonLeft..(layout.buttonLeft + layout.buttonWidth) &&
            y in layout.buttonTop..(layout.buttonTop + layout.buttonHeight)

    private fun makeOrbs(layout: GemOrbCollectLayout): List<Orb> {
        val homes = List(20) { index ->
            val angle = TAU * (index + (GemOrbCollectMath.hash(index) - 0.5f) * 0.38f) / 20f
            val z = when {
                index < 5 -> -1.6f + index * 0.18f
                index > 17 -> 1.85f + (index - 18) * 0.3f
                else -> 0.12f + GemOrbCollectMath.hash(index + 52) * 1.32f
            }
            val projection = 7f / (7f - z)
            val distance = 1.45f + GemOrbCollectMath.hash(index + 20) * 0.9f
            val core = if (index > 17) 22f + GemOrbCollectMath.hash(index + 66) * 6f else 9f + GemOrbCollectMath.hash(index + 66) * 5f
            val margin = (16f + core * 2f + 34f) * layout.unit
            var px = (cos(angle) * distance * layout.gemRadius * projection).coerceIn(-layout.gemX + margin, layout.width - layout.gemX - margin)
            var py = (sin(angle) * distance * layout.gemRadius * projection * 1.12f).coerceIn(-(layout.tallyY - layout.gemY - margin - 4f), layout.gemY - margin)
            if (hypot(px, py) < layout.gemRadius * 1.25f && abs(z) <= 1.2f) py = sqrt(max(0f, (layout.gemRadius * 1.29f).pow(2) - px * px))
            if (index > 17) {
                px = (if (index == 18) -1f else 1f) * (layout.gemX - margin)
                py = layout.gemRadius * if (index == 18) 1.1f else 1.4f
            }
            val homeX = px / (layout.gemRadius * projection)
            val homeY = py / (layout.gemRadius * projection)
            val strike = atan2(homeY, homeX) + (if (index % 2 == 1) 1f else -1f) * (0.87f + GemOrbCollectMath.hash(index + 80) * 1.05f)
            val targetZ = if (index % 6 == 0) -0.08f else 0.38f + 0.3f * GemOrbCollectMath.hash(index + 40)
            val targetRadius = sqrt(0.82f.pow(2) - targetZ.pow(2))
            Orb(homeX, homeY, z, cos(strike) * targetRadius, sin(strike) * targetRadius, targetZ, core / projection, index % 3, -1f, 0f)
        }
        val remaining = homes.indices.toMutableSet()
        val ordered = mutableListOf<Int>()
        var side = 1f
        repeat(20) {
            val best = remaining.minBy { index ->
                abs(homes[index].homeZ) + if (homes[index].homeX * side < 0f) 0.32f else 0f
            }
            ordered += best
            remaining.remove(best)
            side = if (homes[best].homeX > 0f) -1f else 1f
        }
        val result = homes.toMutableList()
        var nextLaunch = 0f
        ordered.forEachIndexed { n, index ->
            result[index] = homes[index].copy(
                launch = nextLaunch,
                duration = 0.95f + (0.55f - 0.95f) * n / 19f,
            )
            nextLaunch += max(0.05f, 0.26f * 0.9f.pow(n)) + (0.95f - 0.55f) / 19f
        }
        return result
    }

    private fun buildImpactAttitude(): Array<FloatArray> {
        val count = (LOOP * 240f).toInt() + 1
        val result = Array(count) { floatArrayOf(0f, 0f, 1f, 1f) }
        var turn = 0f
        var tilt = 0f
        var turnSpeed = 0f
        var tiltSpeed = 0f
        var scale = 1f
        var scaleSpeed = 0f
        var stretch = 1f
        var stretchSpeed = 0f
        var hushFromScale = 1f
        var burstStarted = false
        val struck = BooleanArray(orbs.size)
        val dt = 1f / 240f
        for (tick in 1 until count) {
            val time = tick * dt
            orbs.forEachIndexed { index, orb ->
                if (!struck[index] && time + 0.00001f >= COLLECT_START + orb.launch + orb.duration) {
                    turnSpeed += orb.targetX * 1.4f
                    tiltSpeed -= orb.targetY * 1.4f
                    scaleSpeed += 0.975f
                    struck[index] = true
                }
            }
            val down = time in 1.2f..<4.6f
            if (down && time < HUSH_START) {
                turn += turnSpeed * dt
                tilt += tiltSpeed * dt
                turnSpeed *= exp(-12.25f * dt)
                tiltSpeed *= exp(-12.25f * dt)
            } else {
                val damping = if (time >= HUSH_START) 24f else 12.25f
                turnSpeed += (66.7f * (-turn) - damping * turnSpeed) * dt
                tiltSpeed += (66.7f * (-tilt) - damping * tiltSpeed) * dt
                turn += turnSpeed * dt
                tilt += tiltSpeed * dt
            }
            result[tick][0] = turn
            result[tick][1] = tilt
            if (time >= HUSH_START && time < BURST_START) {
                if (hushFromScale == 1f) hushFromScale = scale
                val progress = GemOrbCollectMath.ease((time - HUSH_START) / HUSH_DURATION, 0.77f, 0f, 0.175f, 1f)
                scale = hushFromScale + (0.86f - hushFromScale) * progress
                scaleSpeed = 0f
                stretch = 1f - 0.06f * progress
                stretchSpeed = 0f
            } else {
                if (!burstStarted && time >= BURST_START) {
                    scale = 0.86f
                    scaleSpeed = 2.5f
                    stretch = 1.06f
                    stretchSpeed = 0f
                    burstStarted = true
                }
                val target = if (down && time < HUSH_START) 0.97f else 1f
                val stiffness = if (down && time < HUSH_START) 420f else 220f
                val damping = if (down && time < HUSH_START) 30f else 11f
                scaleSpeed += (stiffness * (target - scale) - damping * scaleSpeed) * dt
                scale += scaleSpeed * dt
                stretchSpeed += (220f * (1f - stretch) - 15f * stretchSpeed) * dt
                stretch += stretchSpeed * dt
            }
            result[tick][2] = scale
            result[tick][3] = stretch
        }
        return result
    }

    companion object {
        private const val HOLD_DELAY = 0.28f
        private const val HOLD_SLOP = 8f
        private const val COLLECTION_DURATION = 3.22f
        private const val HUSH_DURATION = 0.32f
        private const val REWIND_DURATION = 0.9f
        private const val COLLECT_START = 1.475f
        private const val HUSH_START = COLLECT_START + COLLECTION_DURATION
        private const val BURST_START = HUSH_START + HUSH_DURATION
        private const val AFTER_START = BURST_START + 2f
        private const val REWIND_START = 9.749f
        private const val REWIND_END = 10.649f
        private const val RESUME_AFTER = 3.5f
        private const val LOOP = 12f
        private const val TAU = (PI * 2.0).toFloat()
    }
}

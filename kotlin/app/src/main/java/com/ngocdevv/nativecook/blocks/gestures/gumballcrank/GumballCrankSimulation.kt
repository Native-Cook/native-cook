package com.ngocdevv.nativecook.blocks.gestures.gumballcrank

import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.round
import kotlin.math.sin

/**
 * One owner on the Compose frame clock. Call [step] from the frame loop, then invalidate the
 * Canvas when it returns true. State changes also advance [dirtyVersion] for snapshot adapters.
 */
internal class GumballCrankSimulation(
    reduced: Boolean,
    private val autoPlay: Boolean,
) {
    var reduced: Boolean = reduced
        private set

    var phase = GumballCrankPhase.READY
        private set

    var phaseTime = 0.0
        private set

    var angle = 0.0
        private set

    var press = 0.0
        private set

    var jostleX = 0.0
        private set

    var jostleY = 0.0
        private set

    var clock = 0.0
        private set

    var bump = 0.0
        private set

    var coins = GumballCrankTokens.coinsFrom
        private set

    var dirtyVersion = 0L
        private set

    private var accumulator = 0.0
    private var turned = 0.0
    private var detent = 0
    private var angularVelocity = 0.0
    private var wobble = 0.0
    private var wobbleVelocity = 0.0
    private var ratchetBack = 0.0
    private var strain = 0.0
    private var grabbed = false
    private var scriptedGesture = false
    private var previousPointerAngle = 0.0
    private var lastPointerTime = 0.0
    private var jostleVelocityX = 0.0
    private var jostleVelocityY = 0.0
    private var demoRunning = autoPlay && !reduced
    private var demoTime = 0.0
    private var quietTime = 0.0
    private var automaticTurnTime = -1.0
    private var resumeEligible = false
    private var returning = false
    private var returnTime = 0.0
    private var returnAngle = 0.0
    private var returnPress = 0.0
    private var returnJostleX = 0.0
    private var returnJostleY = 0.0
    private var returnMorph = 0.0
    private var returnOut = 0.0
    private val pendingHaptics = mutableListOf<GumballCrankHaptic>()
    private val mutableConfetti = mutableListOf<GumballCrankConfetti>()

    val confetti: List<GumballCrankConfetti>
        get() = mutableConfetti

    val visualAngle: Double
        get() = if (reduced) 0.0 else angle + wobble * 0.06 + ratchetBack * 0.35

    val outProgress: Double
        get() {
            if (returning) return returnOut * (1.0 - returnEase)
            if (phase == GumballCrankPhase.DISPENSING) {
                if (reduced) return clamp((phaseTime - GumballCrankTokens.outStart) / GumballCrankTokens.reducedFade)
                return easeOutBack(clamp((phaseTime - GumballCrankTokens.outStart) / GumballCrankTokens.outDuration))
            }
            if (phase == GumballCrankPhase.REWARD) return 1.0
            if (phase == GumballCrankPhase.COLLECTING) {
                if (reduced) return 1.0 - clamp((phaseTime - GumballCrankTokens.collectAt) / GumballCrankTokens.reducedFade)
                if (phaseTime < GumballCrankTokens.collectAt) return 1.0
                return 1.0 - easeOutCubic(clamp((phaseTime - GumballCrankTokens.collectAt) / GumballCrankTokens.backDuration))
            }
            return 0.0
        }

    val morphProgress: Double
        get() {
            if (returning) return returnMorph * (1.0 - returnEase)
            if (phase == GumballCrankPhase.DISPENSING) {
                if (reduced) return clamp((phaseTime - (GumballCrankTokens.dispenseDone - GumballCrankTokens.reducedFade)) / GumballCrankTokens.reducedFade)
                return easeOutCubic(clamp((phaseTime - GumballCrankTokens.morphStart) / GumballCrankTokens.morphDuration))
            }
            if (phase == GumballCrankPhase.REWARD) return 1.0
            if (phase == GumballCrankPhase.COLLECTING) {
                if (reduced) return 1.0 - clamp((phaseTime - GumballCrankTokens.couponOutStart) / GumballCrankTokens.reducedFade)
                return 1.0 - easeInOut(clamp((phaseTime - GumballCrankTokens.couponOutStart) / GumballCrankTokens.couponOutDuration))
            }
            return 0.0
        }

    val machineSquash: Double
        get() {
            if (reduced || phase != GumballCrankPhase.DISPENSING || phaseTime >= GumballCrankTokens.squashDuration) return 0.0
            return sin(PI * phaseTime / GumballCrankTokens.squashDuration) * 0.02
        }

    val cameraProgress: Double
        get() {
            if (reduced) return 0.0
            val progress = clamp(outProgress)
            return progress * progress * (3.0 - 2.0 * progress)
        }

    val potX: Double
        get() {
            if (reduced && phase != GumballCrankPhase.READY) return GumballCrankTokens.potX
            return GumballCrankTokens.exitX + (GumballCrankTokens.potX - GumballCrankTokens.exitX) * outProgress
        }

    val potY: Double
        get() {
            if (reduced && phase != GumballCrankPhase.READY) return GumballCrankTokens.potY
            return GumballCrankTokens.exitY + (GumballCrankTokens.potY - GumballCrankTokens.exitY) * outProgress
        }

    val potScale: Double
        get() {
            if (reduced && phase != GumballCrankPhase.READY) return 1.0
            return (0.3 + 0.7 * outProgress) * (1.0 - (1.0 - GumballCrankTokens.tubBackScale) * morphProgress)
        }

    val couponX: Double
        get() {
            if (reduced) return GumballCrankTokens.cardCenterX
            return GumballCrankTokens.panelCenterX + (GumballCrankTokens.cardCenterX - GumballCrankTokens.panelCenterX) * morphProgress
        }

    val couponY: Double
        get() {
            if (reduced) return GumballCrankTokens.cardCenterY
            val drop = if (phase == GumballCrankPhase.COLLECTING) (1.0 - morphProgress) * 40.0 else 0.0
            return GumballCrankTokens.panelCenterY + (GumballCrankTokens.cardCenterY - GumballCrankTokens.panelCenterY) * morphProgress + drop
        }

    val couponWidth: Double
        get() {
            if (reduced) return GumballCrankTokens.cardWidth
            val progress = if (phase == GumballCrankPhase.COLLECTING) 1.0 else morphProgress
            return GumballCrankTokens.panelWidth + (GumballCrankTokens.cardWidth - GumballCrankTokens.panelWidth) * progress
        }

    val couponHeight: Double
        get() {
            if (reduced) return GumballCrankTokens.cardHeight
            val progress = if (phase == GumballCrankPhase.COLLECTING) 1.0 else morphProgress
            return GumballCrankTokens.panelHeight + (GumballCrankTokens.cardHeight - GumballCrankTokens.panelHeight) * progress
        }

    val couponRotation: Double
        get() = GumballCrankTokens.peelAngle * sin(PI * morphProgress)

    val panelOpacity: Double
        get() {
            if (reduced) return 0.0
            return if (morphProgress > 0.001) 1.0 - clamp(morphProgress / 0.5) else 0.0
        }

    val couponOpacity: Double
        get() {
            if (reduced) return morphProgress
            return if (phase == GumballCrankPhase.COLLECTING) morphProgress else clamp((morphProgress - 0.2) / 0.4)
        }

    val stubCoinOpacity: Double
        get() {
            if (phase == GumballCrankPhase.COLLECTING) return 0.0
            if (reduced) return morphProgress
            return clamp((morphProgress - 0.6) / 0.4)
        }

    val ringOpacity: Double
        get() {
            if (phase != GumballCrankPhase.READY || reduced || returning) return 0.0
            val breathing = 0.35 + 0.25 * sin(clock * 2.4)
            if (!demoRunning) return breathing
            // Seam override: retain RN's 2.4 rad/s breath, then meet its exact resting alpha at P.
            val tail = clamp((demoTime - (GumballCrankTokens.loopDuration - GumballCrankTokens.returnDuration)) / GumballCrankTokens.returnDuration)
            return breathing + (0.35 - breathing) * easeInOut(tail)
        }

    val sweepX: Double
        get() {
            if (reduced) return GumballCrankTokens.hiddenX
            val sweepTime = if (phase == GumballCrankPhase.DISPENSING) phaseTime / GumballCrankTokens.sweepDuration else (clock % GumballCrankTokens.idleSweepDuration) / GumballCrankTokens.sweepDuration
            if (sweepTime >= 1.0) return GumballCrankTokens.hiddenX
            return GumballCrankTokens.sweepStartX + GumballCrankTokens.sweepTravel * sweepTime
        }

    val confettiOpacity: Double
        get() = if (mutableConfetti.isEmpty() || reduced) 0.0 else clamp(1.8 - phaseTime * 0.8) * (if (returning) 1.0 - returnEase else 1.0)

    val cardTextOpacity: Double
        get() {
            if (reduced) return morphProgress
            return if (phase == GumballCrankPhase.COLLECTING) morphProgress else clamp((morphProgress - 0.75) / 0.25)
        }

    val counterOpacity: Double
        get() {
            if (phase == GumballCrankPhase.COLLECTING) return 1.0
            return 1.0 - 0.7 * morphProgress
        }

    val shouldTick: Boolean
        get() = returning || grabbed || automaticTurnTime >= 0.0 || demoRunning || phase != GumballCrankPhase.READY || angularVelocity > 0.0 || (!reduced && (autoPlay || ringOpacity > 0.0))

    private val returnEase: Double
        get() = easeInOut(clamp(returnTime / GumballCrankTokens.returnDuration))

    fun updateReducedMotion(value: Boolean) {
        if (reduced == value) return
        reduced = value
        resetToRest(restartDemo = autoPlay && !value, resetAccumulator = true)
        markDirty()
    }

    fun step(frameDt: Double, width: Double): Boolean {
        if (frameDt <= 0.0 || width <= 0.0) return false
        val before = dirtyVersion
        accumulator = min(accumulator + frameDt, GumballCrankTokens.step * GumballCrankTokens.maxSteps)
        while (accumulator >= GumballCrankTokens.step) {
            fixedStep(width)
            accumulator -= GumballCrankTokens.step
        }
        return dirtyVersion != before
    }

    fun down(x: Double, y: Double, width: Double): Boolean {
        if (phase != GumballCrankPhase.READY) return false
        val scale = width / GumballCrankTokens.stageWidth
        val knobX = GumballCrankTokens.knobCenterX * scale
        val knobY = GumballCrankTokens.knobCenterY * scale
        if (hypot(x - knobX, y - knobY) >= GumballCrankTokens.grabRadius * scale) return false
        registerUserInteraction()
        grabbed = true
        angularVelocity = 0.0
        previousPointerAngle = atan2(y - knobY, x - knobX)
        lastPointerTime = clock
        emit(GumballCrankHaptic.GRAB)
        markDirty()
        return true
    }

    fun move(x: Double, y: Double, width: Double): Boolean {
        if (!grabbed) return false
        registerUserInteraction(keepGrab = true)
        val scale = width / GumballCrankTokens.stageWidth
        val knobX = GumballCrankTokens.knobCenterX * scale
        val knobY = GumballCrankTokens.knobCenterY * scale
        val pointerAngle = atan2(y - knobY, x - knobX)
        var delta = pointerAngle - previousPointerAngle
        if (delta > PI) delta -= PI * 2.0
        if (delta < -PI) delta += PI * 2.0
        previousPointerAngle = pointerAngle
        val elapsed = max(1.0 / 240.0, clock - lastPointerTime)
        lastPointerTime = clock
        applyPointerDelta(delta, elapsed)
        markDirty()
        return true
    }

    fun up(): Boolean {
        if (!grabbed) return false
        registerUserInteraction(keepGrab = true)
        finishGesture()
        markDirty()
        return true
    }

    fun command(action: GumballCrankCommand): Boolean {
        if (action == GumballCrankCommand.TURN && phase == GumballCrankPhase.READY) {
            registerUserInteraction()
            if (reduced) {
                setPhase(GumballCrankPhase.DISPENSING)
                emit(GumballCrankHaptic.DISPENSE)
                markDirty()
                return true
            }
            automaticTurnTime = 0.0
            markDirty()
            return true
        }
        if (action == GumballCrankCommand.COLLECT && phase == GumballCrankPhase.REWARD) {
            registerUserInteraction()
            setPhase(GumballCrankPhase.COLLECTING)
            emit(GumballCrankHaptic.COLLECT)
            markDirty()
            return true
        }
        return false
    }

    fun drainHaptics(): List<GumballCrankHaptic> {
        if (pendingHaptics.isEmpty()) return emptyList()
        val result = pendingHaptics.toList()
        pendingHaptics.clear()
        return result
    }

    private fun fixedStep(width: Double) {
        if (returning) {
            stepReturn()
            return
        }
        val previousDemoTime = demoTime
        if (demoRunning) {
            val nextDemoTime = demoTime + GumballCrankTokens.step
            runTurn(previousDemoTime, nextDemoTime, GumballCrankTokens.demoTurnStart, width, scripted = true)
            if (previousDemoTime <= GumballCrankTokens.collectScriptAt && GumballCrankTokens.collectScriptAt < nextDemoTime) {
                scriptedCollect()
            }
            demoTime = nextDemoTime
            if (demoTime + GumballCrankTokens.loopTolerance >= GumballCrankTokens.loopDuration) {
                resetToRest(restartDemo = autoPlay && !reduced, resetAccumulator = false)
                markDirty()
                return
            }
        } else if (automaticTurnTime >= 0.0) {
            runTurn(automaticTurnTime, automaticTurnTime + GumballCrankTokens.step, 0.0, width, scripted = true)
            automaticTurnTime += GumballCrankTokens.step
            if (automaticTurnTime > GumballCrankTokens.demoTurnDuration + 0.1) automaticTurnTime = -1.0
        }
        val userTouchHeld = grabbed && !scriptedGesture
        if (autoPlay && resumeEligible && !userTouchHeld && !reduced) {
            quietTime += GumballCrankTokens.step
            if (quietTime + GumballCrankTokens.loopTolerance >= GumballCrankTokens.touchResetDelay) {
                beginReturn()
            }
        }
        if (!grabbed && angularVelocity > 0.0 && phase == GumballCrankPhase.READY) {
            advance(angularVelocity * GumballCrankTokens.step)
            angularVelocity = max(0.0, angularVelocity - GumballCrankTokens.friction * angularVelocity * GumballCrankTokens.step)
            if (angularVelocity < GumballCrankTokens.catchSpin) angularVelocity = 0.0
        }
        wobbleVelocity += (-GumballCrankTokens.wobbleStiffness * wobble - GumballCrankTokens.wobbleDamping * wobbleVelocity) * GumballCrankTokens.step
        wobble += wobbleVelocity * GumballCrankTokens.step
        if (!grabbed) ratchetBack *= exp(-14.0 * GumballCrankTokens.step)
        press += ((if (grabbed) 1.0 else 0.0) - press) * (1.0 - exp(-18.0 * GumballCrankTokens.step))
        jostleVelocityX += (-160.0 * jostleX - 9.0 * jostleVelocityX) * GumballCrankTokens.step
        jostleX += jostleVelocityX * GumballCrankTokens.step
        jostleVelocityY += (-160.0 * jostleY - 9.0 * jostleVelocityY) * GumballCrankTokens.step
        jostleY += jostleVelocityY * GumballCrankTokens.step
        stepTimeline(width)
        clock += GumballCrankTokens.step
        markDirty()
    }

    private fun stepTimeline(width: Double) {
        val before = phaseTime
        phaseTime += GumballCrankTokens.step
        if (phase == GumballCrankPhase.DISPENSING) {
            if (before < GumballCrankTokens.confettiAt && phaseTime >= GumballCrankTokens.confettiAt && !reduced) burst(width)
            if (phaseTime >= GumballCrankTokens.dispenseDone) {
                setPhase(GumballCrankPhase.REWARD)
                coins -= GumballCrankTokens.coinsAdd
                emit(GumballCrankHaptic.OPEN)
            }
        } else if (phase == GumballCrankPhase.COLLECTING && phaseTime >= GumballCrankTokens.collectAt + GumballCrankTokens.backDuration) {
            setPhase(GumballCrankPhase.READY)
        }
        if (phase == GumballCrankPhase.COLLECTING && before < GumballCrankTokens.collectAt && phaseTime >= GumballCrankTokens.collectAt) {
            coins += GumballCrankTokens.coinsAdd
            bump = if (reduced) 0.0 else 1.0
        }
        bump = max(0.0, bump - GumballCrankTokens.step * 3.0)
        val scale = width / GumballCrankTokens.stageWidth
        mutableConfetti.forEachIndexed { index, particle ->
            particle.velocityY += GumballCrankTokens.confettiGravity * scale * GumballCrankTokens.step
            particle.x += particle.velocityX * GumballCrankTokens.step
            particle.y += particle.velocityY * GumballCrankTokens.step
            particle.rotation += (if (index % 2 == 0) 1.0 else -1.0) * GumballCrankTokens.confettiSpin * GumballCrankTokens.step
        }
        if (mutableConfetti.isNotEmpty() && phase != GumballCrankPhase.DISPENSING && phaseTime > 0.5) mutableConfetti.clear()
    }

    private fun advance(delta: Double) {
        angle += delta
        turned += delta
        val nextDetent = floor(turned / GumballCrankTokens.detentAngle + 1e-6).toInt()
        if (nextDetent > detent) {
            detent = nextDetent
            if (!reduced) {
                wobbleVelocity += GumballCrankTokens.wobbleKick
                jostleVelocityX += (if (nextDetent % 2 == 0) 1.0 else -1.0) * 90.0
                jostleVelocityY -= 60.0
            }
            emit(GumballCrankHaptic.DETENT)
        }
        if (turned >= GumballCrankTokens.fullTurn - 1e-6) {
            turned = 0.0
            detent = 0
            angularVelocity = 0.0
            grabbed = false
            angle = round(angle / GumballCrankTokens.fullTurn) * GumballCrankTokens.fullTurn
            setPhase(GumballCrankPhase.DISPENSING)
            emit(GumballCrankHaptic.DISPENSE)
        }
    }

    private fun applyPointerDelta(delta: Double, elapsed: Double) {
        if (delta > 0.0 && strain >= 0.0) {
            angularVelocity = angularVelocity * 0.6 + delta / elapsed * 0.4
            advance(delta)
            return
        }
        if (delta > 0.0) {
            strain = min(0.0, strain + delta)
            ratchetBack = -GumballCrankTokens.backMax * (1.0 - exp(strain / GumballCrankTokens.backMax))
            return
        }
        val previousBack = ratchetBack
        strain = min(0.0, strain + delta)
        ratchetBack = -GumballCrankTokens.backMax * (1.0 - exp(strain / GumballCrankTokens.backMax))
        if (previousBack > -0.02 && ratchetBack <= -0.02) emit(GumballCrankHaptic.RATCHET)
    }

    private fun finishGesture() {
        grabbed = false
        angularVelocity = min(GumballCrankTokens.maxSpin, max(0.0, angularVelocity))
        if (turned > PI && angularVelocity > GumballCrankTokens.commitSpin) {
            angularVelocity = max(angularVelocity, (GumballCrankTokens.fullTurn - turned) / GumballCrankTokens.finishDuration + GumballCrankTokens.friction)
        }
        ratchetBack = 0.0
        strain = 0.0
        scriptedGesture = false
    }

    private fun runTurn(start: Double, end: Double, origin: Double, width: Double, scripted: Boolean) {
        val finish = origin + GumballCrankTokens.demoTurnDuration
        fun point(time: Double): Pair<Double, Double> {
            val progress = clamp((time - origin) / (finish - origin))
            val eased = (1.0 - cos(PI * progress)) / 2.0
            val pointerAngle = -PI / 2.0 + GumballCrankTokens.demoTurnAngle * eased
            val scale = width / GumballCrankTokens.stageWidth
            val knobX = GumballCrankTokens.knobCenterX * scale
            val knobY = GumballCrankTokens.knobCenterY * scale
            return Pair(knobX + cos(pointerAngle) * GumballCrankTokens.demoRadius * scale, knobY + sin(pointerAngle) * GumballCrankTokens.demoRadius * scale)
        }
        if (start <= origin && origin < end) {
            val point = point(origin)
            scriptedDown(point.first, point.second, width, scripted)
        }
        if (end > origin && start < finish && grabbed) {
            val point = point(min(end, finish))
            scriptedMove(point.first, point.second, width)
        }
        if (start < finish && finish <= end) finishGesture()
    }

    private fun scriptedDown(x: Double, y: Double, width: Double, scripted: Boolean) {
        scriptedGesture = scripted
        if (phase != GumballCrankPhase.READY) return
        val scale = width / GumballCrankTokens.stageWidth
        val knobX = GumballCrankTokens.knobCenterX * scale
        val knobY = GumballCrankTokens.knobCenterY * scale
        if (hypot(x - knobX, y - knobY) >= GumballCrankTokens.grabRadius * scale) return
        grabbed = true
        angularVelocity = 0.0
        previousPointerAngle = atan2(y - knobY, x - knobX)
        lastPointerTime = clock
        emit(GumballCrankHaptic.GRAB)
    }

    private fun scriptedMove(x: Double, y: Double, width: Double) {
        if (!grabbed) return
        val scale = width / GumballCrankTokens.stageWidth
        val knobX = GumballCrankTokens.knobCenterX * scale
        val knobY = GumballCrankTokens.knobCenterY * scale
        val pointerAngle = atan2(y - knobY, x - knobX)
        var delta = pointerAngle - previousPointerAngle
        if (delta > PI) delta -= PI * 2.0
        if (delta < -PI) delta += PI * 2.0
        previousPointerAngle = pointerAngle
        val elapsed = max(1.0 / 240.0, clock - lastPointerTime)
        lastPointerTime = clock
        applyPointerDelta(delta, elapsed)
    }

    private fun scriptedCollect() {
        if (phase != GumballCrankPhase.REWARD) return
        scriptedGesture = true
        setPhase(GumballCrankPhase.COLLECTING)
        emit(GumballCrankHaptic.COLLECT)
        scriptedGesture = false
    }

    private fun burst(width: Double) {
        mutableConfetti.clear()
        val scale = width / GumballCrankTokens.stageWidth
        repeat(GumballCrankTokens.confettiCount) { index ->
            val randomA = sin(index * 12.9898) * 43758.5453
            val randomB = sin(index * 78.233) * 12345.678
            val unitA = randomA - floor(randomA)
            val unitB = randomB - floor(randomB)
            val launchAngle = -PI / 2.0 + (unitA - 0.5) * GumballCrankTokens.confettiSpread * 2.0
            val speed = GumballCrankTokens.confettiSpeed * (0.55 + 0.45 * unitB) * scale
            mutableConfetti += GumballCrankConfetti(
                x = GumballCrankTokens.exitX * scale,
                y = GumballCrankTokens.exitY * scale,
                velocityX = cos(launchAngle) * speed,
                velocityY = sin(launchAngle) * speed,
                rotation = unitB * 6.0,
                colorIndex = index % 5,
            )
        }
    }

    private fun registerUserInteraction(keepGrab: Boolean = false) {
        demoRunning = false
        automaticTurnTime = -1.0
        quietTime = 0.0
        resumeEligible = autoPlay && !reduced
        returning = false
        if (!keepGrab) scriptedGesture = false
    }

    private fun beginReturn() {
        if (returning) return
        returnAngle = wrapAngle(visualAngle)
        returnPress = press
        returnJostleX = jostleX
        returnJostleY = jostleY
        returnMorph = morphProgress
        returnOut = outProgress
        wobble = 0.0
        wobbleVelocity = 0.0
        ratchetBack = 0.0
        strain = 0.0
        returning = true
        returnTime = 0.0
        grabbed = false
        angularVelocity = 0.0
        pendingHaptics.clear()
    }

    private fun stepReturn() {
        returnTime += GumballCrankTokens.step
        val progress = returnEase
        angle = returnAngle * (1.0 - progress)
        press = returnPress * (1.0 - progress)
        jostleX = returnJostleX * (1.0 - progress)
        jostleY = returnJostleY * (1.0 - progress)
        clock += GumballCrankTokens.step
        if (returnTime + GumballCrankTokens.loopTolerance >= GumballCrankTokens.returnDuration) {
            resetToRest(restartDemo = autoPlay && !reduced, resetAccumulator = false)
        }
        markDirty()
    }

    private fun resetToRest(restartDemo: Boolean, resetAccumulator: Boolean) {
        if (resetAccumulator) accumulator = 0.0
        clock = 0.0
        phase = GumballCrankPhase.READY
        phaseTime = 0.0
        angle = 0.0
        turned = 0.0
        detent = 0
        angularVelocity = 0.0
        wobble = 0.0
        wobbleVelocity = 0.0
        ratchetBack = 0.0
        strain = 0.0
        press = 0.0
        grabbed = false
        scriptedGesture = false
        jostleX = 0.0
        jostleY = 0.0
        jostleVelocityX = 0.0
        jostleVelocityY = 0.0
        bump = 0.0
        coins = GumballCrankTokens.coinsFrom
        mutableConfetti.clear()
        pendingHaptics.clear()
        demoRunning = restartDemo
        demoTime = 0.0
        quietTime = 0.0
        automaticTurnTime = -1.0
        resumeEligible = false
        returning = false
        returnTime = 0.0
        returnAngle = 0.0
        returnPress = 0.0
        returnJostleX = 0.0
        returnJostleY = 0.0
        returnMorph = 0.0
        returnOut = 0.0
    }

    private fun setPhase(value: GumballCrankPhase) {
        phase = value
        phaseTime = 0.0
    }

    private fun emit(event: GumballCrankHaptic) {
        // Scripted and button-driven turns stay silent; haptics only acknowledge direct user acts.
        if (!scriptedGesture && !demoRunning && automaticTurnTime < 0.0) pendingHaptics += event
    }

    private fun markDirty() {
        dirtyVersion += 1L
    }
}

private fun clamp(value: Double): Double = min(1.0, max(0.0, value))

private fun easeOutBack(value: Double): Double {
    val overshoot = 1.4
    return 1.0 + (overshoot + 1.0) * (value - 1.0).pow(3) + overshoot * (value - 1.0).pow(2)
}

private fun easeInOut(value: Double): Double {
    if (value < 0.5) return 8.0 * value.pow(4)
    return 1.0 - (-2.0 * value + 2.0).pow(4) / 2.0
}

private fun easeOutCubic(value: Double): Double = 1.0 - (1.0 - value).pow(3)

private fun wrapAngle(value: Double): Double {
    var wrapped = value % (PI * 2.0)
    if (wrapped > PI) wrapped -= PI * 2.0
    if (wrapped < -PI) wrapped += PI * 2.0
    return wrapped
}

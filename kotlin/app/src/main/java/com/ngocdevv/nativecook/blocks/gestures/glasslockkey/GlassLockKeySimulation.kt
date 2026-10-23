package com.ngocdevv.nativecook.blocks.gestures.glasslockkey

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.pow

/** Fixed-step port of glass-sim.ts. Coordinates and velocities are density-independent pixels. */
class GlassLockKeySimulation(
    width: Double,
    reduced: Boolean,
    autoPlay: Boolean,
) {
    init {
        require(width > 0.0) { "width must be positive" }
    }

    val s: Double = width / GlassLockKeyTokens.STAGE_WIDTH
    val restX: Double = GlassLockKeyTokens.UPG.keyRest.x * s
    val restY: Double = GlassLockKeyTokens.UPG.keyRest.y * s
    val plugX: Double = GlassLockKeyTokens.UPG.plug.x * s
    val plugY: Double = GlassLockKeyTokens.UPG.plug.y * s
    val ringX: Double = GlassLockKeyTokens.UPG.ring.x * s
    val ringY: Double = GlassLockKeyTokens.UPG.ring.y * s

    var reduced: Boolean = reduced
        set(value) {
            if (field == value) return
            field = value
            if (value) {
                // Reduced Motion keeps direct interaction and opacity changes, but stops the demo and ambient clock.
                cancelScriptedInteraction()
                demoOn = false
                auto = -1.0
                clock = 0.0
            } else {
                // The next autoplay-enabled advance settles any reduced state before restarting phase zero.
                resumeReturning = false
                userInterrupted = true
            }
            quiet = 0.0
        }

    var acc = 0.0
        private set
    var clock = 0.0
        private set
    var mode = IDLE
        private set
    var modeT = 0.0
        private set
    var kx = restX
        private set
    var ky = restY
        private set
    var kvx = 0.0
        private set
    var kvy = 0.0
        private set
    var lift = 0.0
        private set
    var th = 0.0
        private set
    var tv = 0.0
        private set
    var detent = 0
        private set
    var strain = 0.0
        private set
    var shackle = 0.0
        private set
    var shackleV = 0.0
        private set
    var sheet = 0.0
        private set
    var glint = -1.0
        private set
    var shake = -1.0
        private set
    var show = 0.0
        private set
    var idleT = 0.0
        private set
    var tick = -1.0
        private set
    var grab = 0
        private set
    var scripted = false
        private set
    var gx = 0.0
        private set
    var gy = 0.0
        private set
    var fx = 0.0
        private set
    var fy = 0.0
        private set
    var x0 = 0.0
        private set
    var th0 = 0.0
        private set
    var demoOn = autoPlay && !reduced
        private set
    var demoT = 0.0
        private set
    var quiet = 0.0
        private set
    var auto = -1.0
        private set

    private var resumeReturning = false
    private var userInterrupted = false
    private var physicalPointerActive = false
    private val events = mutableListOf<Int>()

    fun advance(frameDt: Double, autoPlay: Boolean) {
        if (frameDt <= 0.0) return
        val elapsed = min(frameDt, GlassLockKeyTokens.SIM.maxFrameDelta)
        acc = min(acc + elapsed, GlassLockKeyTokens.SIM.maxFrameDelta)
        while (acc >= GlassLockKeyTokens.STEP) {
            acc -= GlassLockKeyTokens.STEP
            if (step(autoPlay)) {
                acc = 0.0
                break
            }
        }
    }

    fun down(x: Double, y: Double) {
        physicalPointerActive = true
        inputDown(x, y, isScripted = false)
    }

    fun move(x: Double, y: Double) {
        fx = x
        fy = y
        if (!scripted) quiet = 0.0
    }

    fun up(vx: Double, vy: Double) {
        inputUp(vx, vy)
        physicalPointerActive = false
        quiet = 0.0
    }

    fun command(action: String) {
        require(action == "unlock" || action == "lock") { "action must be unlock or lock" }
        inputCommand(action, isScripted = false)
    }

    fun drainEvents(): List<Int> {
        if (events.isEmpty()) return emptyList()
        val drained = events.toList()
        events.clear()
        return drained
    }

    private fun inputDown(x: Double, y: Double, isScripted: Boolean) {
        scripted = isScripted
        if (!scripted) registerUserInteraction()
        fx = x
        fy = y
        grab = 0
        if ((mode == IDLE || mode == CARRYING) &&
            abs(x - kx) < GlassLockKeyTokens.GM.keyHitW * s &&
            abs(y - ky) < GlassLockKeyTokens.GM.keyHitH * s
        ) {
            grab = 1
            setMode(CARRYING)
            gx = kx - x
            gy = ky - y
            emit(GlassLockKeyTokens.G_HAPTIC.grab.toInt())
        } else if (mode == SEATED && hypot(x - ringX, y - ringY) < GlassLockKeyTokens.GM.ringHit * s) {
            grab = 2
            x0 = x
            th0 = th
            strain = 0.0
        }
    }

    private fun inputUp(vx: Double, vy: Double) {
        if (!scripted) quiet = 0.0
        if (grab == 1) {
            grab = 0
            val distance = hypot(kx - plugX, ky - plugY)
            val toward = (plugX - kx) * vx + (plugY - ky) * vy
            val speed = hypot(vx, vy)
            if (distance < GlassLockKeyTokens.GM.magnet * s ||
                (toward > 0.0 &&
                    speed > GlassLockKeyTokens.GM.flick * s &&
                    distance < GlassLockKeyTokens.GM.magnet * GlassLockKeyTokens.SIM.flickDistanceMultiplier * s)
            ) {
                seat()
            } else {
                setMode(IDLE)
                kvx = vx
                kvy = vy
            }
        } else if (grab == 2) {
            grab = 0
            if (strain < GlassLockKeyTokens.SIM.wrongReleaseThreshold && !reduced) {
                shake = 0.0
                emit(GlassLockKeyTokens.G_HAPTIC.wrong.toInt())
            }
        }
        scripted = false
    }

    private fun inputCommand(action: String, isScripted: Boolean) {
        if (!isScripted) {
            // Accessibility can interrupt autoplay between its scripted down/up events.
            cancelScriptedInteraction()
            registerUserInteraction()
        }
        if (action == "lock" && mode == OPEN) {
            setMode(CLOSING)
        } else if (action == "unlock" && mode == IDLE) {
            auto = 0.0
        }
    }

    private fun registerUserInteraction() {
        demoOn = false
        auto = -1.0
        quiet = 0.0
        resumeReturning = false
        userInterrupted = true
    }

    private fun cancelScriptedInteraction() {
        if (!scripted) return
        if (grab == 1) {
            grab = 0
            setMode(IDLE)
            kvx = 0.0
            kvy = 0.0
        } else if (grab == 2) {
            grab = 0
        }
        scripted = false
    }

    private fun setMode(next: Int) {
        mode = next
        modeT = 0.0
    }

    private fun seat() {
        setMode(INSERTING)
        grab = 0
        kx = plugX
        ky = plugY
        emit(GlassLockKeyTokens.G_HAPTIC.seat.toInt())
    }

    private fun open() {
        setMode(OPEN)
        grab = 0
        th = 1.0
        tv = 0.0
        shackleV += if (reduced) 0.0 else GlassLockKeyTokens.GM.shackleKick
        glint = 0.0
        emit(GlassLockKeyTokens.G_HAPTIC.open.toInt())
    }

    private fun emit(event: Int) {
        if (!scripted && !demoOn && auto < 0.0) events += event
    }

    /** Returns true when this fixed step reset to the exact rest state. */
    private fun step(autoPlay: Boolean): Boolean {
        val dt = GlassLockKeyTokens.STEP
        if (reduced && demoOn) demoOn = false

        if (demoOn && !reduced) {
            val t0 = demoT
            val t1 = t0 + dt
            runDemo(t0, t1)
            demoT = t1
            if (demoT * 1000.0 >= GlassLockKeyTokens.LOOP) {
                resetForDemoLoop()
                return true
            }
        } else if (auto >= 0.0) {
            val t0 = auto + GlassLockKeyTokens.G_SCRIPT.carry[0]
            runDemo(t0, t0 + dt)
            auto += dt
            if (t0 > GlassLockKeyTokens.G_SCRIPT.turn[1] + GlassLockKeyTokens.SIM.autoFinishDelay) {
                auto = -1.0
            }
        } else if (stepResume(autoPlay, dt)) {
            return true
        }

        modeT += dt
        stepKey(dt)
        lift += ((if (grab == 1) 1.0 else 0.0) - lift) *
            (1.0 - exp(-GlassLockKeyTokens.SIM.liftRate * dt))
        stepTurn(dt)
        stepDetents()
        stepShackle(dt)
        stepSheet(dt)
        stepVisibility(dt)
        glint = advanceTimer(glint, GlassLockKeyTokens.GM.glint, dt)
        shake = advanceTimer(shake, GlassLockKeyTokens.GM.shake.duration, dt)
        tick = advanceTimer(tick, GlassLockKeyTokens.GM.tick.duration, dt)
        if (!reduced) clock += dt
        return false
    }

    private fun stepResume(autoPlay: Boolean, dt: Double): Boolean {
        if (!autoPlay || reduced || grab != 0 || physicalPointerActive || !userInterrupted) return false
        quiet += dt
        if (!resumeReturning && quiet * 1000.0 >= GlassLockKeyTokens.SIM.interactionResumeMs) {
            resumeReturning = true
            if (mode != IDLE && mode != CLOSING) setMode(CLOSING)
        }
        if (!resumeReturning || mode != IDLE) return false
        val atRest = hypot(kx - restX, ky - restY) < GlassLockKeyTokens.SIM.restPositionEpsilon &&
            hypot(kvx, kvy) < GlassLockKeyTokens.SIM.restVelocityEpsilon &&
            abs(th) < GlassLockKeyTokens.SIM.restAngleEpsilon &&
            show < GlassLockKeyTokens.SIM.restVisualEpsilon &&
            sheet < GlassLockKeyTokens.SIM.restVisualEpsilon &&
            abs(shackle) < GlassLockKeyTokens.SIM.restVisualEpsilon
        if (atRest) {
            resetForDemoLoop()
            return true
        }
        return false
    }

    private fun stepKey(dt: Double) {
        if (mode == CARRYING && grab == 1) {
            var targetX = fx + gx
            var targetY = fy + gy
            val distance = hypot(targetX - plugX, targetY - plugY)
            val radius = GlassLockKeyTokens.GM.magnet * s
            if (distance < radius && !reduced) {
                val pull = GlassLockKeyTokens.GM.magnetPull *
                    (1.0 - distance / radius).pow(GlassLockKeyTokens.SIM.magnetExponent)
                targetX += (plugX - targetX) * pull
                targetY += (plugY - targetY) * pull
            }
            kvx = (targetX - kx) / dt
            kvy = (targetY - ky) / dt
            kx = targetX
            ky = targetY
            if (hypot(targetX - plugX, targetY - plugY) < GlassLockKeyTokens.GM.seat * s) seat()
        } else if (mode == IDLE) {
            springKeyX(restX, GlassLockKeyTokens.GM.home.k, GlassLockKeyTokens.GM.home.c, dt)
            springKeyY(restY, GlassLockKeyTokens.GM.home.k, GlassLockKeyTokens.GM.home.c, dt)
        } else if (mode == INSERTING && modeT >= if (reduced) dt else GlassLockKeyTokens.GM.insert) {
            setMode(SEATED)
        }
    }

    private fun stepTurn(dt: Double) {
        if (mode == SEATED) {
            if (grab == 2) {
                val raw = th0 + (fx - x0) / (GlassLockKeyTokens.GM.turnPx * s)
                if (raw < 0.0) {
                    strain = raw
                    th = -GlassLockKeyTokens.GM.wrong * (1.0 - exp(raw / GlassLockKeyTokens.GM.wrong))
                } else {
                    th = raw
                }
                if (th >= 1.0) open()
                if (fy - ringY > GlassLockKeyTokens.GM.pullOut * s && th < GlassLockKeyTokens.SIM.pullOutAngle) {
                    setMode(CARRYING)
                    grab = 1
                    th = 0.0
                    kx = plugX
                    ky = plugY
                    gx = kx - fx
                    gy = ky - fy
                }
            } else {
                springTurn(0.0, GlassLockKeyTokens.GM.back.k, GlassLockKeyTokens.GM.back.c, dt)
            }
        } else if (
            mode == CLOSING &&
            sheet < GlassLockKeyTokens.SIM.closeSheetThreshold &&
            shackle < GlassLockKeyTokens.SIM.closeShackleThreshold
        ) {
            springTurn(0.0, GlassLockKeyTokens.GM.back.k, GlassLockKeyTokens.GM.back.c, dt)
            if (abs(th) < GlassLockKeyTokens.SIM.closeAngleThreshold) {
                th = 0.0
                tv = 0.0
                setMode(IDLE)
                kx = plugX
                ky = plugY + GlassLockKeyTokens.GM.outPull * s
                kvy = GlassLockKeyTokens.GM.outSpeed * s
            }
        }
    }

    private fun stepDetents() {
        var current = 0
        for (threshold in GlassLockKeyTokens.GM.detents) {
            if (th >= threshold - GlassLockKeyTokens.SIM.detentEpsilon) current += 1
        }
        if (current > detent) {
            emit(GlassLockKeyTokens.G_HAPTIC.detent.toInt())
            if (!reduced) tick = 0.0
        }
        detent = current
    }

    private fun stepShackle(dt: Double) {
        val target = if (mode == OPEN || (mode == CLOSING && sheet > GlassLockKeyTokens.GM.shackleAfter)) 1.0 else 0.0
        if (reduced) {
            shackle = target
            shackleV = 0.0
            return
        }
        val spring = GlassLockKeyTokens.GM.shackle
        val stiffness = if (target > 0.0) spring.k else spring.kClose
        val damping = if (target > 0.0) spring.c else spring.cClose
        val nextVelocity = shackleV + (-stiffness * (shackle - target) - damping * shackleV) * dt
        shackle += nextVelocity * dt
        shackleV = nextVelocity
    }

    private fun stepSheet(dt: Double) {
        val target = if (mode == OPEN) 1.0 else 0.0
        val delay = if (mode == OPEN) GlassLockKeyTokens.GM.sheetDelay else 0.0
        if (modeT >= delay) {
            val rate = if (target > 0.0) GlassLockKeyTokens.GM.sheetIn else GlassLockKeyTokens.GM.sheetOut
            sheet += (target - sheet) * (1.0 - exp(-rate * dt))
        }
    }

    private fun stepVisibility(dt: Double) {
        val resting = mode == IDLE && grab == 0
        idleT = if (resting) idleT + dt else 0.0
        val target = when {
            resumeReturning && resting -> 0.0
            resting && idleT > GlassLockKeyTokens.GM.show.hold -> 0.0
            resting && show < GlassLockKeyTokens.SIM.showThreshold -> 0.0
            else -> 1.0
        }
        val rate = if (target > 0.0) GlassLockKeyTokens.GM.show.inRate else GlassLockKeyTokens.GM.show.outRate
        show += (target - show) * (1.0 - exp(-rate * dt))
    }

    private fun runDemo(t0: Double, t1: Double) {
        val carry = GlassLockKeyTokens.G_SCRIPT.carry
        val fromX = restX
        val fromY = restY + GlassLockKeyTokens.GM.grabBelow * s
        if (t0 <= carry[0] && carry[0] < t1) inputDown(fromX, fromY, isScripted = true)
        if (t1 > carry[0] && t0 < carry[1] && grab == 1) {
            val u = cosineEase((min(t1, carry[1]) - carry[0]) / (carry[1] - carry[0]))
            val targetX = plugX + GlassLockKeyTokens.SIM.carryTargetOffsetX * s
            val targetY = plugY +
                (GlassLockKeyTokens.GM.grabBelow + GlassLockKeyTokens.SIM.carryTargetOffsetY) * s
            fx = fromX + (targetX - fromX) * u
            fy = fromY + (targetY - fromY) * u
        }
        if (t0 < carry[1] && carry[1] <= t1 && grab == 1) inputUp(0.0, 0.0)

        val turn = GlassLockKeyTokens.G_SCRIPT.turn
        if (t0 <= turn[0] && turn[0] < t1) inputDown(ringX, ringY, isScripted = true)
        if (t1 > turn[0] && t0 < turn[1] && grab == 2) {
            val u = cosineEase((min(t1, turn[1]) - turn[0]) / (turn[1] - turn[0]))
            fx = ringX + GlassLockKeyTokens.G_SCRIPT.turnBy * s * u
            fy = ringY
        }
        if (t0 < turn[1] && turn[1] <= t1 && grab == 2) inputUp(0.0, 0.0)
        if (t0 <= GlassLockKeyTokens.G_SCRIPT.lockAt && GlassLockKeyTokens.G_SCRIPT.lockAt < t1) {
            inputCommand("lock", isScripted = true)
        }
    }

    private fun springKeyX(target: Double, stiffness: Double, damping: Double, dt: Double) {
        val nextVelocity = kvx + (-stiffness * (kx - target) - damping * kvx) * dt
        kx += nextVelocity * dt
        kvx = nextVelocity
    }

    private fun springKeyY(target: Double, stiffness: Double, damping: Double, dt: Double) {
        val nextVelocity = kvy + (-stiffness * (ky - target) - damping * kvy) * dt
        ky += nextVelocity * dt
        kvy = nextVelocity
    }

    private fun springTurn(target: Double, stiffness: Double, damping: Double, dt: Double) {
        val nextVelocity = tv + (-stiffness * (th - target) - damping * tv) * dt
        th += nextVelocity * dt
        tv = nextVelocity
    }

    private fun resetForDemoLoop() {
        clock = 0.0
        mode = IDLE
        modeT = 0.0
        kx = restX
        ky = restY
        kvx = 0.0
        kvy = 0.0
        lift = 0.0
        th = 0.0
        tv = 0.0
        detent = 0
        strain = 0.0
        shackle = 0.0
        shackleV = 0.0
        sheet = 0.0
        glint = -1.0
        shake = -1.0
        show = 0.0
        idleT = 0.0
        tick = -1.0
        grab = 0
        scripted = false
        gx = 0.0
        gy = 0.0
        fx = 0.0
        fy = 0.0
        x0 = 0.0
        th0 = 0.0
        demoOn = !reduced
        demoT = 0.0
        quiet = 0.0
        auto = -1.0
        resumeReturning = false
        userInterrupted = false
        physicalPointerActive = false
        events.clear()
    }

    private fun advanceTimer(value: Double, duration: Double, dt: Double): Double {
        if (value < 0.0) return value
        val next = value + dt
        return if (next > duration) -1.0 else next
    }

    private fun cosineEase(value: Double): Double {
        return (1.0 - cos(PI * clamp(value))) / 2.0
    }

    private fun clamp(value: Double, low: Double = 0.0, high: Double = 1.0): Double {
        return value.coerceIn(low, high)
    }

    companion object {
        const val IDLE = 0
        const val CARRYING = 1
        const val INSERTING = 2
        const val SEATED = 3
        const val OPEN = 4
        const val CLOSING = 5

    }
}

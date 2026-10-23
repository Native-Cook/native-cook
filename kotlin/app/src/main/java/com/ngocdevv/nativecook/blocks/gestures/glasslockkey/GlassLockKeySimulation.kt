package com.ngocdevv.nativecook.blocks.gestures.glasslockkey

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

internal data class GlassLockKeyGeometry(
    val width: Double,
    val height: Double,
    val scale: Double,
    val faceX: Double,
    val lockTop: Double,
    val lockBottom: Double,
    val plugX: Double,
    val plugY: Double,
    val restX: Double,
    val restY: Double,
    val seatY: Double,
)

internal fun glassLockKeyGeometry(width: Double, height: Double): GlassLockKeyGeometry {
    val scale = width / GlassLockKeyTokens.STAGE_WIDTH
    val lockTop = height * GlassLockKeyTokens.Lock.topRatio
    val lockBottom = lockTop + GlassLockKeyTokens.Lock.height * scale
    val plugY = lockBottom + GlassLockKeyTokens.Lock.plugHeight * scale
    return GlassLockKeyGeometry(
        width = width,
        height = height,
        scale = scale,
        faceX = width / 2.0 - GlassLockKeyTokens.Lock.face * scale / 2.0,
        lockTop = lockTop,
        lockBottom = lockBottom,
        plugX = width / 2.0,
        plugY = plugY,
        restX = width / 2.0,
        restY = height * GlassLockKeyTokens.Key.restRatio,
        seatY = plugY - GlassLockKeyTokens.Key.blade * scale,
    )
}

/** Fixed-step, deterministic port of the approved RN `lock-sim.ts`. */
internal class GlassLockKeySimulation(
    val geometry: GlassLockKeyGeometry,
    reducedMotion: Boolean,
    autoPlay: Boolean,
) {
    var reduced = reducedMotion
        set(value) {
            if (field == value) return
            field = value
            if (value) {
                demoOn = false
                autoUnlock = -1.0
                scripted = false
                grab = 0
            }
            quiet = 0.0
        }

    var acc = 0.0
        private set
    var clock = 0.0
        private set
    var mode = IDLE
        private set
    var kx = geometry.restX
        private set
    var ky = geometry.restY
        private set
    var kvx = 0.0
        private set
    var kvy = 0.0
        private set
    var lift = 0.0
        private set
    var theta = 0.0
        private set
    var thetaVelocity = 0.0
        private set
    var shackle = 0.0
        private set
    var bounce = 0.0
        private set
    var sheet = 0.0
        private set
    var shakeTime = -1.0
        private set
    var glintTime = -1.0
        private set
    var wrongHint = false
        private set
    var grab = 0
        private set

    private var insertTime = 1.0
    private var insertFrom = geometry.plugY
    private var insertTo = geometry.plugY
    private var insertDuration = 1.0
    private var detent = 0
    private var shackleVelocity = 0.0
    private var bounceVelocity = 0.0
    private var sheetTime = 1.0
    private var sheetFrom = 0.0
    private var sheetTo = 0.0
    private var sheetDuration = 1.0
    private var sheetDelay = 0.0
    private var scripted = false
    private var grabX = 0.0
    private var grabY = 0.0
    private var fingerX = 0.0
    private var fingerY = 0.0
    private var turnStartX = 0.0
    private var turnStartTheta = 0.0
    private var demoOn = autoPlay && !reducedMotion
    private var demoTime = 0.0
    private var quiet = 0.0
    private var autoUnlock = -1.0
    private val events = mutableListOf<Int>()

    val isOpen: Boolean
        get() = mode == OPEN

    val inserted: Boolean
        get() = mode == INSERTING || mode == SEATED || mode == OPEN || mode == CLOSING

    fun advance(frameSeconds: Double, autoPlay: Boolean) {
        if (frameSeconds <= 0.0) return
        if (reduced) demoOn = false
        acc = min(acc + frameSeconds, GlassLockKeyTokens.STEP * GlassLockKeyTokens.MAX_STEPS)
        while (acc >= GlassLockKeyTokens.STEP) {
            fixedStep(autoPlay && !reduced)
            acc -= GlassLockKeyTokens.STEP
        }
    }

    fun down(x: Double, y: Double, isScripted: Boolean = false) {
        scripted = isScripted
        if (!scripted) {
            demoOn = false
            autoUnlock = -1.0
            quiet = 0.0
        }
        fingerX = x
        fingerY = y
        grab = 0
        val bowY = ky + (GlassLockKeyTokens.Key.blade + GlassLockKeyTokens.Key.ring / 2.0) * geometry.scale
        if (mode == IDLE || mode == CARRYING) {
            val inX = abs(x - kx) < GlassLockKeyTokens.Key.hit / 2.0 * geometry.scale
            val inY = y > ky - 20.0 * geometry.scale &&
                y < bowY + (GlassLockKeyTokens.Key.ring / 2.0 + 16.0) * geometry.scale
            if (inX && inY) {
                grab = 1
                mode = CARRYING
                grabX = kx - x
                grabY = ky - y
                emit(GlassLockKeyTokens.Haptic.grab)
            }
        } else if (mode == SEATED && hypot(x - kx, y - bowY) <
            (GlassLockKeyTokens.Key.hit / 2.0 + 12.0) * geometry.scale
        ) {
            grab = 2
            turnStartX = x
            grabY = y
            turnStartTheta = theta
        }
    }

    fun move(x: Double, y: Double) {
        fingerX = x
        fingerY = y
    }

    fun up(velocityX: Double, velocityY: Double) {
        if (grab == 1) {
            grab = 0
            if (hypot(kx - geometry.plugX, ky - geometry.plugY) <
                GlassLockKeyTokens.Carry.magnet * geometry.scale
            ) {
                seat()
            } else {
                mode = IDLE
                kvx = velocityX
                kvy = velocityY
            }
        } else if (grab == 2) {
            grab = 0
            if (theta < -0.1 && !reduced) {
                shakeTime = 0.0
                emit(GlassLockKeyTokens.Haptic.wrong)
            }
            if (theta < -0.1) wrongHint = true
        }
        scripted = false
    }

    fun command(action: Action, isScripted: Boolean = false) {
        if (!isScripted) {
            demoOn = false
            quiet = 0.0
        }
        when {
            action == Action.lock && mode == OPEN -> {
                mode = CLOSING
                startSheet(0.0, 0.0)
            }
            action == Action.unlock && mode == IDLE -> autoUnlock = 0.0
        }
    }

    fun drainEvents(): List<Int> {
        if (events.isEmpty()) return emptyList()
        return events.toList().also { events.clear() }
    }

    fun shakeOffset(): Double {
        if (shakeTime < 0.0) return 0.0
        val progress = shakeTime / GlassLockKeyTokens.Shake.duration
        return GlassLockKeyTokens.Shake.amplitude * (1.0 - progress) *
            sin(2.0 * PI * GlassLockKeyTokens.Shake.cycles * progress)
    }

    private fun emit(event: Int) {
        if (!scripted && !demoOn && autoUnlock < 0.0) events += event
    }

    private fun startSheet(target: Double, delay: Double) {
        sheetFrom = sheet
        sheetTo = target
        sheetTime = 0.0
        sheetDelay = delay
        val baseDuration = when {
            reduced -> GlassLockKeyTokens.SheetMotion.reducedFade
            target == 1.0 -> GlassLockKeyTokens.SheetMotion.open
            else -> GlassLockKeyTokens.SheetMotion.close
        }
        sheetDuration = max(0.05, baseDuration * abs(target - sheet))
    }

    private fun seat() {
        mode = INSERTING
        grab = 0
        kx = geometry.plugX
        insertFrom = geometry.plugY
        insertTo = geometry.seatY
        insertTime = 0.0
        insertDuration = if (reduced) GlassLockKeyTokens.STEP else GlassLockKeyTokens.Insert.duration
        emit(GlassLockKeyTokens.Haptic.seat)
    }

    private fun unseat() {
        mode = INSERTING
        insertFrom = ky
        insertTo = geometry.plugY + 2.0 * geometry.scale
        insertTime = 0.0
        insertDuration = if (reduced) GlassLockKeyTokens.STEP else GlassLockKeyTokens.Insert.outDuration
    }

    private fun open() {
        mode = OPEN
        grab = 0
        theta = GlassLockKeyTokens.Turn.open
        thetaVelocity = 0.0
        bounceVelocity += GlassLockKeyTokens.Bounce.openKick
        shackleVelocity += if (reduced) 0.0 else 4.0
        glintTime = 0.0
        startSheet(1.0, if (reduced) 0.0 else GlassLockKeyTokens.SheetMotion.delay)
        emit(GlassLockKeyTokens.Haptic.open)
    }

    private fun runDemo(start: Double, end: Double) {
        val carry = GlassLockKeyTokens.Script.carry
        if (start <= carry[0] && carry[0] < end) {
            down(geometry.restX, geometry.restY + 40.0 * geometry.scale, isScripted = true)
        }
        if (end > carry[0] && start < carry[1] && grab == 1) {
            val progress = cosineEase((min(end, carry[1]) - carry[0]) / (carry[1] - carry[0]))
            move(
                geometry.restX + GlassLockKeyTokens.Script.carryTo[0] * geometry.scale * progress,
                geometry.restY + 40.0 * geometry.scale +
                    GlassLockKeyTokens.Script.carryTo[1] * geometry.scale * progress,
            )
        }
        if (start < carry[1] && carry[1] <= end && grab == 1) up(0.0, 0.0)

        val turn = GlassLockKeyTokens.Script.turn
        val bowY = geometry.seatY +
            (GlassLockKeyTokens.Key.blade + GlassLockKeyTokens.Key.ring / 2.0) * geometry.scale
        if (start <= turn[0] && turn[0] < end) {
            down(geometry.plugX - 8.0 * geometry.scale, bowY, isScripted = true)
        }
        if (end > turn[0] && start < turn[1] && grab == 2) {
            val progress = cosineEase((min(end, turn[1]) - turn[0]) / (turn[1] - turn[0]))
            move(
                geometry.plugX - 8.0 * geometry.scale +
                    GlassLockKeyTokens.Script.turnBy * geometry.scale * progress,
                bowY,
            )
        }
        if (start < turn[1] && turn[1] <= end && grab == 2) up(0.0, 0.0)
        if (start <= GlassLockKeyTokens.Script.lockAt && GlassLockKeyTokens.Script.lockAt < end) {
            command(Action.lock, isScripted = true)
        }
    }

    private fun fixedStep(autoPlay: Boolean) {
        val dt = GlassLockKeyTokens.STEP
        when {
            demoOn -> {
                val start = demoTime
                var end = start + dt
                runDemo(start, end)
                if (end * 1000.0 >= GlassLockKeyTokens.LOOP) {
                    end -= GlassLockKeyTokens.LOOP / 1000.0
                    if (mode == IDLE && hypot(kx - geometry.restX, ky - geometry.restY) < 0.5) {
                        kx = geometry.restX
                        ky = geometry.restY
                        kvx = 0.0
                        kvy = 0.0
                    }
                }
                demoTime = end
            }
            autoUnlock >= 0.0 -> {
                val start = autoUnlock + GlassLockKeyTokens.Script.carry[0]
                runDemo(start, start + dt)
                autoUnlock += dt
                if (start > GlassLockKeyTokens.Script.turn[1] + 0.1) autoUnlock = -1.0
            }
            autoPlay && grab == 0 -> {
                quiet += dt
                if (quiet * 1000.0 >= GlassLockKeyTokens.RESUME_MS && (mode == IDLE || mode == OPEN)) {
                    demoOn = true
                    demoTime = if (mode == OPEN) GlassLockKeyTokens.Script.resumeOpen else 0.0
                }
            }
        }

        stepKey(dt)
        lift += ((if (grab == 1) 1.0 else 0.0) - lift) * (1.0 - exp(-14.0 * dt))
        stepTurn(dt)
        stepShackle(dt)
        stepSheet(dt)
        clock += dt
    }

    private fun stepKey(dt: Double) {
        if (mode == CARRYING && grab == 1) {
            var targetX = fingerX + grabX
            var targetY = fingerY + grabY
            val distance = hypot(targetX - geometry.plugX, targetY - geometry.plugY)
            val radius = GlassLockKeyTokens.Carry.magnet * geometry.scale
            if (distance < radius && !reduced) {
                val pull = GlassLockKeyTokens.Carry.magnetPull * (1.0 - distance / radius).pow(0.6)
                targetX += (geometry.plugX - targetX) * pull
                targetY += (geometry.plugY - targetY) * pull
            }
            kvx = (targetX - kx) / dt
            kvy = (targetY - ky) / dt
            kx = targetX
            ky = targetY
            if (hypot(targetX - geometry.plugX, targetY - geometry.plugY) <
                GlassLockKeyTokens.Carry.seat * geometry.scale
            ) {
                seat()
            }
        } else if (mode == INSERTING) {
            insertTime = min(insertDuration, insertTime + dt)
            val progress = easeOutCubic(insertTime / insertDuration)
            ky = insertFrom + (insertTo - insertFrom) * progress
            kx = geometry.plugX
            if (insertTime >= insertDuration) {
                if (insertTo == geometry.seatY) {
                    mode = SEATED
                } else {
                    mode = IDLE
                    kvy = 0.0
                }
            }
        } else if (mode == IDLE) {
            val horizontal = spring(kx, kvx, geometry.restX, GlassLockKeyTokens.Carry.home, dt)
            kx = horizontal.first
            kvx = horizontal.second
            val vertical = spring(ky, kvy, geometry.restY, GlassLockKeyTokens.Carry.home, dt)
            ky = vertical.first
            kvy = vertical.second
        }
    }

    private fun stepTurn(dt: Double) {
        if (mode == SEATED) {
            if (grab == 2) {
                var nextTheta = turnStartTheta +
                    (fingerX - turnStartX) / (GlassLockKeyTokens.Turn.radius * geometry.scale)
                if (nextTheta < 0.0) nextTheta = -rubber(-nextTheta, GlassLockKeyTokens.Turn.wrong)
                thetaVelocity = (nextTheta - theta) / dt
                theta = nextTheta
                if (fingerY - grabY > GlassLockKeyTokens.Carry.pullOut * geometry.scale && abs(theta) < 0.1) {
                    grab = 1
                    mode = CARRYING
                    grabX = kx - fingerX
                    grabY = ky - fingerY
                    theta = 0.0
                    thetaVelocity = 0.0
                }
                if (theta >= GlassLockKeyTokens.Turn.open) open()
            } else {
                val turn = spring(theta, thetaVelocity, 0.0, GlassLockKeyTokens.Turn.back, dt)
                theta = turn.first
                thetaVelocity = turn.second
            }
        } else if (mode == CLOSING && shackle < 0.06) {
            val turn = spring(theta, thetaVelocity, 0.0, GlassLockKeyTokens.Turn.close, dt)
            theta = turn.first
            thetaVelocity = turn.second
            if (sheet < 0.02 && abs(theta) < 0.02 && shackle < 0.02) {
                theta = 0.0
                thetaVelocity = 0.0
                unseat()
            }
        }
        if (theta > 0.3 && mode == SEATED) wrongHint = false
        var currentDetent = 0
        GlassLockKeyTokens.Turn.detents.forEachIndexed { index, threshold ->
            if (theta >= threshold - 0.0001) currentDetent = index + 1
        }
        if (currentDetent != detent) {
            if (currentDetent > detent) {
                bounceVelocity += GlassLockKeyTokens.Bounce.detentKick
                emit(GlassLockKeyTokens.Haptic.detent)
            }
            detent = currentDetent
        }
    }

    private fun stepShackle(dt: Double) {
        val target = if (mode == OPEN ||
            (mode == CLOSING && sheet > GlassLockKeyTokens.SheetMotion.shackleAfter)
        ) 1.0 else 0.0
        if (reduced) {
            shackle += (target - shackle) * (1.0 - exp(-18.0 * dt))
            shackleVelocity = 0.0
            bounce = 0.0
            bounceVelocity = 0.0
        } else {
            val springSpec = if (target == 1.0) {
                GlassLockKeyTokens.Spring(
                    GlassLockKeyTokens.Shackle.stiffness,
                    GlassLockKeyTokens.Shackle.damping,
                )
            } else {
                GlassLockKeyTokens.Spring(
                    GlassLockKeyTokens.Shackle.closeStiffness,
                    GlassLockKeyTokens.Shackle.closeDamping,
                )
            }
            val shackleStep = spring(shackle, shackleVelocity, target, springSpec, dt)
            shackle = shackleStep.first
            shackleVelocity = shackleStep.second
            val bounceStep = spring(
                bounce,
                bounceVelocity,
                0.0,
                GlassLockKeyTokens.Spring(
                    GlassLockKeyTokens.Bounce.stiffness,
                    GlassLockKeyTokens.Bounce.damping,
                ),
                dt,
            )
            bounce = bounceStep.first
            bounceVelocity = bounceStep.second
        }
        if (shakeTime >= 0.0) {
            shakeTime += dt
            if (shakeTime > GlassLockKeyTokens.Shake.duration) shakeTime = -1.0
        }
        if (glintTime >= 0.0) {
            glintTime += dt
            if (glintTime > GlassLockKeyTokens.glintDuration) glintTime = -1.0
        }
    }

    private fun stepSheet(dt: Double) {
        if (sheetTime >= sheetDuration + sheetDelay) return
        sheetTime += dt
        val progress = clamp((sheetTime - sheetDelay) / sheetDuration)
        val eased = when {
            reduced -> progress
            sheetTo == 1.0 -> 1.0 - (1.0 - progress).pow(4.0)
            else -> progress * progress * (3.0 - 2.0 * progress)
        }
        sheet = sheetFrom + (sheetTo - sheetFrom) * eased
    }

    private fun spring(
        value: Double,
        velocity: Double,
        target: Double,
        spring: GlassLockKeyTokens.Spring,
        dt: Double,
    ): Pair<Double, Double> {
        val nextVelocity = velocity +
            (-spring.stiffness * (value - target) - spring.damping * velocity) * dt
        return Pair(value + nextVelocity * dt, nextVelocity)
    }

    private fun cosineEase(value: Double): Double = (1.0 - cos(PI * clamp(value))) / 2.0

    private fun easeOutCubic(value: Double): Double = 1.0 - (1.0 - value).pow(3.0)

    private fun rubber(over: Double, limit: Double): Double = limit * (1.0 - exp(-over / limit))

    private fun clamp(value: Double, low: Double = 0.0, high: Double = 1.0): Double =
        value.coerceIn(low, high)

    enum class Action { unlock, lock }

    companion object {
        const val IDLE = 0
        const val CARRYING = 1
        const val INSERTING = 2
        const val SEATED = 3
        const val OPEN = 4
        const val CLOSING = 5
    }
}

package com.ngocdevv.nativecook.blocks.gestures.slidetoanswercord

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Fixed-step simulation shared by the Compose scene and its gestures.
 *
 * Names and update order intentionally mirror the approved React Native `call-sim.ts`, so numeric
 * parity can be audited line by line. All simulation values use [Double].
 */
object SlideToAnswerCordSimulation {
    object PHASE {
        const val ringing = 0
        const val connected = 1
        const val declined = 2
        const val ended = 3
    }

    data class Geometry(
        val w: Double,
        val h: Double,
        val s: Double,
        val anchorX: Double,
        val anchorY: Double,
        val restY: Double,
        val baseLength: Double,
    )

    data class Point(
        val x: Double,
        val y: Double,
    )

    data class RingState(
        val buzz: Double,
        val pulse: Double,
    )

    sealed interface Input {
        data class Down(
            val x: Double,
            val y: Double,
            val script: Boolean = false,
        ) : Input

        data class Move(
            val x: Double,
            val y: Double,
            val vx: Double,
            val vy: Double,
        ) : Input

        data class Up(
            val vx: Double,
            val vy: Double,
        ) : Input

        data class Command(
            val action: Action,
        ) : Input

        enum class Action {
            answer,
            decline,
            end,
        }
    }

    class Sim(
        var acc: Double,
        var clock: Double,
        val px: DoubleArray,
        val py: DoubleArray,
        val vx: DoubleArray,
        val vy: DoubleArray,
        var th: Double,
        var tw: Double,
        var ax: Double,
        var ay: Double,
        var natLen: Double,
        var natTarget: Double,
        var phase: Int,
        var phaseT: Double,
        var callT: Double,
        var wConn: Double,
        var wOff: Double,
        var grab: Boolean,
        var scripted: Boolean,
        var lx: Double,
        var ly: Double,
        var downX: Double,
        var downY: Double,
        var fx: Double,
        var fy: Double,
        var fvx: Double,
        var fvy: Double,
        var armed: Int,
        var tickAt: Int,
        var demoOn: Boolean,
        var demoT: Double,
        var quiet: Double,
        var demoX: Double,
        var demoY: Double,
        var reduced: Boolean,
        val events: MutableList<Int>,
        var wSrc: Double,
        var look: Double,
    ) {
        internal var loopCheckpoint: LoopCheckpoint? = null
        internal var resumeFrom: ResumeSnapshot? = null
        internal var resumeT = 0.0
    }

    internal data class LoopCheckpoint(
        val clock: Double,
        val px: DoubleArray,
        val py: DoubleArray,
        val vx: DoubleArray,
        val vy: DoubleArray,
        val th: Double,
        val tw: Double,
        val ax: Double,
        val ay: Double,
        val natLen: Double,
        val natTarget: Double,
        val phase: Int,
        val phaseT: Double,
        val callT: Double,
        val wConn: Double,
        val wOff: Double,
        val wSrc: Double,
        val look: Double,
    )

    internal data class ResumeSnapshot(
        val px: DoubleArray,
        val py: DoubleArray,
        val th: Double,
    )

    fun makeGeometry(w: Double, h: Double): Geometry {
        val s = w / Tokens.STAGE_WIDTH
        val anchorY = Tokens.CORD.anchorY * s
        val restY = h * Tokens.CORD.restRatio
        val baseLength = restY - anchorY - Tokens.CHAIN.hangStretch * s
        return Geometry(
            w = w,
            h = h,
            s = s,
            anchorX = w / 2.0 + Tokens.CORD.replicaAnchorDx * s,
            anchorY = anchorY,
            restY = restY,
            baseLength = baseLength,
        )
    }

    fun initialSim(g: Geometry, reduced: Boolean, autoPlay: Boolean): Sim {
        val n = Tokens.CORD.links
        val px = DoubleArray(n + 1)
        val py = DoubleArray(n + 1)
        val vx = DoubleArray(n + 1)
        val vy = DoubleArray(n + 1)
        for (i in 0..n) {
            px[i] = g.anchorX
            py[i] = g.anchorY + ((g.restY - g.anchorY) * i) / n
        }
        return Sim(
            acc = 0.0,
            clock = 0.0,
            px = px,
            py = py,
            vx = vx,
            vy = vy,
            th = 0.0,
            tw = 0.0,
            ax = 0.0,
            ay = 0.0,
            natLen = g.baseLength,
            natTarget = g.baseLength,
            phase = PHASE.ringing,
            phaseT = 0.0,
            callT = 0.0,
            wConn = 0.0,
            wOff = 0.0,
            grab = false,
            scripted = false,
            lx = 0.0,
            ly = 0.0,
            downX = 0.0,
            downY = 0.0,
            fx = 0.0,
            fy = 0.0,
            fvx = 0.0,
            fvy = 0.0,
            armed = 0,
            tickAt = 0,
            demoOn = autoPlay && !reduced,
            demoT = 0.0,
            quiet = 0.0,
            demoX = 0.0,
            demoY = 0.0,
            reduced = reduced,
            events = mutableListOf(),
            wSrc = if (reduced) 0.0 else 1.0,
            look = if (reduced) 0.0 else 1.0,
        )
    }

    fun clamp(v: Double, lo: Double = 0.0, hi: Double = 1.0): Double {
        return if (v < lo) lo else if (v > hi) hi else v
    }

    private fun rubber(over: Double, limit: Double): Double {
        return limit * (1.0 - exp(-over / limit))
    }

    /** Rotation that turns the handset local frame so its centre of mass hangs along [th]. */
    fun handsetRotation(th: Double): Double {
        return atan2(Tokens.HANDSET.comX, Tokens.HANDSET.comY) - th + Tokens.HANDSET.restTilt
    }

    /** World position of a local handset point. */
    fun handsetPoint(
        s: Sim,
        g: Geometry,
        x: Double,
        y: Double,
        extra: Double = 0.0,
    ): Point {
        val r = handsetRotation(s.th) + extra
        val c = cos(r)
        val sn = sin(r)
        val n = Tokens.CORD.links
        return Point(
            x = s.px[n] + (x * c - y * sn) * g.s,
            y = s.py[n] + (x * sn + y * c) * g.s,
        )
    }

    private fun setPhase(s: Sim, g: Geometry, phase: Int) {
        s.phase = phase
        s.phaseT = 0.0
        when (phase) {
            PHASE.connected -> {
                s.natTarget = g.baseLength - Tokens.STATE.pickUp * g.s
                s.callT = 0.0
            }

            PHASE.ringing -> s.natTarget = g.baseLength
            else -> s.natTarget = g.baseLength + Tokens.STATE.drop * g.s
        }
        if (!s.scripted && phase != PHASE.ringing) {
            s.events += if (phase == PHASE.connected) {
                Tokens.HAPTIC.answer
            } else {
                Tokens.HAPTIC.decline
            }
        }
    }

    /** Signed vertical drag (negative = lifted), rubber-banded beyond the reach. */
    fun dragDistance(s: Sim, g: Geometry): Double {
        val raw = s.fy - s.downY
        val reach = Tokens.DRAG.reach * g.s
        val absolute = abs(raw)
        val distance = if (absolute > reach) {
            reach + rubber(absolute - reach, reach * 0.4)
        } else {
            absolute
        }
        return when {
            raw < 0.0 -> -distance
            raw > 0.0 -> distance
            else -> 0.0
        }
    }

    fun input(s: Sim, g: Geometry, ev: Input) {
        if (ev is Input.Command) {
            s.demoOn = false
            s.resumeFrom = null
            s.quiet = 0.0
            s.scripted = false
            if (s.grab) {
                val n = Tokens.CORD.links
                val limit = Tokens.DRAG.releaseSpeed
                s.vx[n] = clamp(s.vx[n], -limit, limit)
                s.vy[n] = clamp(s.vy[n], -limit, limit)
                s.grab = false
                s.armed = 0
                s.tickAt = 0
            }
            when (ev.action) {
                Input.Action.answer -> if (s.phase == PHASE.ringing) setPhase(s, g, PHASE.connected)
                Input.Action.decline -> if (s.phase == PHASE.ringing) setPhase(s, g, PHASE.declined)
                Input.Action.end -> if (s.phase == PHASE.connected) setPhase(s, g, PHASE.ended)
            }
            return
        }
        if (ev is Input.Down) {
            s.scripted = ev.script
            if (!s.scripted) {
                s.demoOn = false
                s.resumeFrom = null
                s.quiet = 0.0
            }
            val n = Tokens.CORD.links
            val r = -handsetRotation(s.th)
            val dx = (ev.x - s.px[n]) / g.s
            val dy = (ev.y - s.py[n]) / g.s
            s.lx = dx * cos(r) - dy * sin(r)
            s.ly = dx * sin(r) + dy * cos(r)
            s.grab = true
            s.downX = ev.x
            s.downY = ev.y
            s.fx = ev.x
            s.fy = ev.y
            s.fvx = 0.0
            s.fvy = 0.0
            s.armed = 0
            if (!s.scripted) s.events += Tokens.HAPTIC.grab
            return
        }
        if (ev is Input.Move) {
            if (!s.scripted) s.quiet = 0.0
            if (!s.grab) return
            s.fx = ev.x
            s.fy = ev.y
            s.fvx = ev.vx
            s.fvy = ev.vy
            return
        }
        ev as Input.Up
        if (!s.scripted) s.quiet = 0.0
        if (!s.grab) {
            s.scripted = false
            return
        }
        s.grab = false
        val n = Tokens.CORD.links
        val limit = Tokens.DRAG.releaseSpeed
        s.vx[n] = clamp(ev.vx, -limit, limit)
        s.vy[n] = clamp(ev.vy, -limit, limit)
        val dy = dragDistance(s, g)
        val up = dy <= -Tokens.DRAG.answer * g.s ||
            (ev.vy < -Tokens.FLICK.speed * g.s && dy < -Tokens.FLICK.min * g.s)
        val down = dy >= Tokens.DRAG.decline * g.s ||
            (ev.vy > Tokens.FLICK.speed * g.s && dy > Tokens.FLICK.min * g.s)
        if (s.phase == PHASE.ringing && up) {
            setPhase(s, g, PHASE.connected)
        } else if (s.phase == PHASE.ringing && down) {
            setPhase(s, g, PHASE.declined)
        } else if (s.phase == PHASE.connected && down) {
            setPhase(s, g, PHASE.ended)
        }
        s.scripted = false
    }

    private fun runScript(s: Sim, g: Geometry, t0: Double, t1: Double) {
        if (Tokens.REPLICA) return
        for (segment in Tokens.SCRIPT) {
            val a = segment[0]
            val b = segment[1]
            if (segment[6] == 1.0 && t0 <= a && a < t1) {
                val point = handsetPoint(
                    s = s,
                    g = g,
                    x = Tokens.HANDSET.comX,
                    y = Tokens.HANDSET.comY,
                )
                s.demoX = point.x
                s.demoY = point.y
                input(s, g, Input.Down(x = point.x, y = point.y, script = true))
            }
            if (t1 > a && t0 < b && s.grab && s.scripted) {
                val u = clamp((min(t1, b) - a) / (b - a))
                val eased = (1.0 - cos(PI * u)) / 2.0
                val x = s.demoX + (segment[2] + (segment[4] - segment[2]) * eased) * g.s
                val y = s.demoY + (segment[3] + (segment[5] - segment[3]) * eased) * g.s
                input(s, g, Input.Move(x = x, y = y, vx = 0.0, vy = 0.0))
            }
            if (segment[7] == 1.0 && t0 < b && b <= t1 && s.scripted) {
                input(s, g, Input.Up(vx = 0.0, vy = 0.0))
            }
        }
    }

    private fun series(coefficients: DoubleArray, time: Double): Double {
        val omega = 2.0 * PI * time / Tokens.REPLICA_SWING.period
        var value = coefficients[0]
        for (harmonic in 1..4) {
            value += coefficients[2 * harmonic - 1] * cos(harmonic * omega)
            value += coefficients[2 * harmonic] * sin(harmonic * omega)
        }
        return value
    }

    private fun sourceCordX(y: Double, time: Double): Double {
        val frame = ((time / 0.25) % 16.0 + 16.0) % 16.0
        val firstFrame = floor(frame).toInt()
        val secondFrame = (firstFrame + 1) % 16
        val frameProgress = frame - firstFrame
        val last = Tokens.REPLICA_CORD_Y.lastIndex
        val clampedY = y.coerceIn(Tokens.REPLICA_CORD_Y[0], Tokens.REPLICA_CORD_Y[last])
        var row = 0
        while (row < last - 1 && clampedY > Tokens.REPLICA_CORD_Y[row + 1]) {
            row++
        }
        val rowProgress = (clampedY - Tokens.REPLICA_CORD_Y[row]) /
            (Tokens.REPLICA_CORD_Y[row + 1] - Tokens.REPLICA_CORD_Y[row])
        val first = Tokens.REPLICA_CORD_X[firstFrame][row] +
            (Tokens.REPLICA_CORD_X[firstFrame][row + 1] - Tokens.REPLICA_CORD_X[firstFrame][row]) * rowProgress
        val second = Tokens.REPLICA_CORD_X[secondFrame][row] +
            (Tokens.REPLICA_CORD_X[secondFrame][row + 1] - Tokens.REPLICA_CORD_X[secondFrame][row]) * rowProgress
        return first + (second - first) * frameProgress
    }

    private fun pinToSource(
        s: Sim,
        g: Geometry,
        time: Double,
        dt: Double,
        weight: Double = 1.0,
    ) {
        val lastLink = Tokens.CORD.links
        val swing = Tokens.REPLICA_SWING
        val screenScale = g.w / 1206.0
        val leanDegrees = series(swing.lean, time)
        val rotation = ((leanDegrees - swing.leanAt0) / swing.leanPerRad) * PI / 180.0
        val centerX = series(swing.x, time) * screenScale
        val centerY = series(swing.y, time) * screenScale
        val localX = swing.centroid[0] * screenScale - swing.attach[0] * g.s
        val localY = swing.centroid[1] * screenScale - swing.attach[1] * g.s
        val offsetX = localX * cos(rotation) - localY * sin(rotation)
        val offsetY = localX * sin(rotation) + localY * cos(rotation)
        val targetX = centerX - offsetX
        val targetY = centerY - offsetY
        val nextX = s.px[lastLink] + (targetX - s.px[lastLink]) * weight
        val nextY = s.py[lastLink] + (targetY - s.py[lastLink]) * weight
        s.vx[lastLink] = s.vx[lastLink] * (1.0 - weight) +
            ((nextX - s.px[lastLink]) / dt) * weight
        s.vy[lastLink] = s.vy[lastLink] * (1.0 - weight) +
            ((nextY - s.py[lastLink]) / dt) * weight
        s.px[lastLink] = nextX
        s.py[lastLink] = nextY
        val targetTilt = atan2(Tokens.HANDSET.comX, Tokens.HANDSET.comY) +
            Tokens.HANDSET.restTilt - rotation
        s.th += (targetTilt - s.th) * weight
        s.tw *= 1.0 - weight

        val topY = Tokens.REPLICA_CORD_Y.first() * screenScale
        val bottomY = Tokens.REPLICA_CORD_Y.last() * screenScale
        for (index in 1 until lastLink) {
            val y = g.anchorY + (nextY - g.anchorY) * index / lastLink
            val x = when {
                y < topY -> {
                    val topX = sourceCordX(Tokens.REPLICA_CORD_Y.first(), time) * screenScale
                    g.anchorX + (topX - g.anchorX) * (y - g.anchorY) / (topY - g.anchorY)
                }
                y > bottomY -> {
                    val bottomX = sourceCordX(Tokens.REPLICA_CORD_Y.last(), time) * screenScale
                    bottomX + (nextX - bottomX) * (y - bottomY) / maxOf(1.0, nextY - bottomY)
                }
                else -> sourceCordX(y / screenScale, time) * screenScale
            }
            s.vx[index] *= 1.0 - weight
            s.vy[index] *= 1.0 - weight
            s.px[index] += (x - s.px[index]) * weight
            s.py[index] += (y - s.py[index]) * weight
        }
    }

    private fun fixedStep(s: Sim, g: Geometry, autoPlay: Boolean) {
        val dt = Tokens.STEP
        val n = Tokens.CORD.links
        var crossedLoopSeam = false
        if (s.demoOn && !s.reduced) {
            val t0 = s.demoT
            var t1 = t0 + dt
            runScript(s, g, t0, t1)
            if (t1 + LOOP_EPSILON >= Tokens.LOOP) {
                t1 -= Tokens.LOOP
                crossedLoopSeam = true
            }
            s.demoT = if (abs(t1) < LOOP_EPSILON) 0.0 else t1
        } else if (autoPlay && !s.reduced && !s.grab && s.resumeFrom == null) {
            s.quiet += dt
            // RESUME_MS remains 3500 for source parity. The product contract says resume two
            // seconds after the last real touch, so runtime intentionally uses this override.
            if (s.quiet >= RUNTIME_RESUME_SECONDS &&
                (s.phase == PHASE.ringing || s.phase == PHASE.connected)
            ) {
                beginResume(s, g)
            }
        }

        s.phaseT += dt
        if (s.phase == PHASE.connected) s.callT += dt
        if ((s.phase == PHASE.declined || s.phase == PHASE.ended) &&
            s.phaseT >= Tokens.STATE.resetAfter
        ) {
            setPhase(s, g, PHASE.ringing)
        }
        s.natLen += (s.natTarget - s.natLen) *
            (1.0 - exp(-Tokens.STATE.lengthEase * dt))
        val colorK = if (s.reduced) {
            1.0
        } else {
            1.0 - exp(-Tokens.STATE.colorEase * dt)
        }
        s.wConn += ((if (s.phase == PHASE.connected) 1.0 else 0.0) - s.wConn) * colorK
        s.wOff += ((if (s.phase == PHASE.declined || s.phase == PHASE.ended) 1.0 else 0.0) -
            s.wOff) * colorK

        // Reduced Motion keeps the current physical pose fixed. State, colour, and call timers above
        // remain live, while autoplay, gravity settling, swing drive, and scripted drags stay stopped.
        if (s.reduced) {
            s.clock += dt
            return
        }

        if (s.grab) {
            val distance = dragDistance(s, g)
            val wanted = if (distance <= -Tokens.DRAG.answer * g.s &&
                s.phase == PHASE.ringing
            ) {
                -1
            } else if (distance >= Tokens.DRAG.decline * g.s) {
                1
            } else {
                0
            }
            if (wanted != s.armed) {
                s.armed = wanted
                if (wanted != 0 && !s.scripted) {
                    s.events += Tokens.HAPTIC.grab
                }
            }
            val tick = floor(abs(distance) / (Tokens.TICK * g.s)).toInt()
            if (tick > s.tickAt && !s.scripted) {
                s.events += Tokens.HAPTIC.tick
            }
            s.tickAt = tick
        } else {
            s.tickAt = 0
        }

        val gravity = Tokens.CHAIN.gravity * g.s
        val linkStiffness =
            ((Tokens.CHAIN.handsetMass * gravity) / (Tokens.CHAIN.hangStretch * g.s)) * n
        val rest = s.natLen / n
        val forceX = DoubleArray(n + 1)
        val forceY = DoubleArray(n + 1)
        for (i in 0..n) {
            val mass = if (i == n) {
                Tokens.CHAIN.handsetMass
            } else {
                Tokens.CHAIN.linkMass
            }
            forceY[i] = gravity * mass
        }
        for (i in 0 until n) {
            val dx = s.px[i + 1] - s.px[i]
            val dy = s.py[i + 1] - s.py[i]
            val distance = sqrt(dx * dx + dy * dy).takeUnless { it == 0.0 } ?: 1e-6
            val stretch = distance - rest
            val force = linkStiffness * if (stretch > 0.0) stretch else stretch * 0.25
            val unitX = dx / distance
            val unitY = dy / distance
            forceX[i] += force * unitX
            forceY[i] += force * unitY
            forceX[i + 1] -= force * unitX
            forceY[i + 1] -= force * unitY
        }
        for (i in 1 until n) {
            forceX[i] += Tokens.CHAIN.straighten * Tokens.CHAIN.linkMass *
                ((s.px[i - 1] + s.px[i + 1]) / 2.0 - s.px[i])
            forceY[i] += Tokens.CHAIN.straighten * Tokens.CHAIN.linkMass *
                ((s.py[i - 1] + s.py[i + 1]) / 2.0 - s.py[i])
        }
        if (s.phase == PHASE.ringing && !s.reduced) {
            forceX[n] += Tokens.CHAIN.idleDrive * g.s *
                sin((2.0 * PI * s.clock) / Tokens.CHAIN.drivePeriod)
        }
        val previousX = s.px[n]
        val previousVelocityX = s.vx[n]
        for (i in 1..n) {
            if (i == n && s.grab) continue
            val mass = if (i == n) {
                Tokens.CHAIN.handsetMass
            } else {
                Tokens.CHAIN.linkMass
            }
            val damping = exp(
                -(if (i == n) {
                    Tokens.CHAIN.handsetDamping
                } else {
                    Tokens.CHAIN.damping
                }) * dt,
            )
            s.vx[i] = (s.vx[i] + (forceX[i] / mass) * dt) * damping
            s.vy[i] = (s.vy[i] + (forceY[i] / mass) * dt) * damping
            s.px[i] += s.vx[i] * dt
            s.py[i] += s.vy[i] * dt
        }
        if (s.grab) {
            val distance = dragDistance(s, g)
            val rotation = handsetRotation(s.th)
            val offsetX = (s.lx * cos(rotation) - s.ly * sin(rotation)) * g.s
            val offsetY = (s.lx * sin(rotation) + s.ly * cos(rotation)) * g.s
            val nextX = s.fx - offsetX
            val nextY = s.downY + distance - offsetY
            s.vx[n] = (nextX - s.px[n]) / dt
            s.vy[n] = (nextY - s.py[n]) / dt
            s.px[n] = nextX
            s.py[n] = nextY
        }

        if (Tokens.REPLICA && !s.grab) {
            pinToSource(s, g, s.clock + dt, dt)
            s.ax = s.px[n] - previousX
            s.clock += dt
            return
        }

        val lastLinkX = s.px[n] - s.px[n - 1]
        val lastLinkY = s.py[n] - s.py[n - 1]
        val target = atan2(lastLinkX, lastLinkY)
        val acceleration = (s.vx[n] - previousVelocityX) / dt
        val centerOfMass = hypot(
            Tokens.HANDSET.comX,
            Tokens.HANDSET.comY,
        ) * g.s
        s.tw += (-Tokens.TILT.stiffness * (s.th - target) - Tokens.TILT.damping * s.tw -
            (acceleration / centerOfMass) * 0.6) * dt
        s.th += s.tw * dt
        if (!Tokens.REPLICA) {
            s.wSrc = if (s.phase == PHASE.ringing && !s.grab && !s.reduced) {
                s.wSrc + (1.0 - s.wSrc) * (1.0 - exp(-dt / Tokens.CHAIN.toSource))
            } else {
                0.0
            }
            if (s.wSrc > 1e-3) {
                pinToSource(s, g, s.clock + dt, dt, s.wSrc)
            }
            s.look += (s.wSrc - s.look) * (1.0 - exp(-dt / Tokens.CHAIN.lookEase))
        }
        s.ax = s.px[n] - previousX
        s.clock += dt

        if (s.resumeFrom != null) {
            applyResumeEase(s, dt)
        } else if (crossedLoopSeam) {
            restoreLoopCheckpoint(s)
        }
    }

    fun stepSim(
        s: Sim,
        g: Geometry,
        frameDt: Double,
        autoPlay: Boolean,
        uncapped: Boolean = false,
    ) {
        if (s.reduced) {
            s.demoOn = false
            if (s.scripted) {
                s.grab = false
                s.scripted = false
            }
        }
        s.acc = if (uncapped) {
            s.acc + frameDt
        } else {
            min(
                s.acc + frameDt,
                Tokens.STEP * Tokens.MAX_STEPS,
            )
        }
        while (s.acc + LOOP_EPSILON >= Tokens.STEP) {
            fixedStep(s, g, autoPlay)
            s.acc -= Tokens.STEP
            if (abs(s.acc) < LOOP_EPSILON) s.acc = 0.0
        }
    }

    /** Pre-rolls into the stable swing, then records the exact frame restored at each 12 s seam. */
    fun preroll(s: Sim, g: Geometry) {
        val steps = (
            Tokens.PREROLL_LOOPS * Tokens.LOOP / Tokens.STEP
            ).roundToInt()
        repeat(steps) {
            fixedStep(s, g, autoPlay = true)
        }
        s.demoT = 0.0
        s.events.clear()
        s.loopCheckpoint = captureLoopCheckpoint(s)
    }

    /** Ringing buzz in radians and neon pulse progress. */
    fun ringState(s: Sim): RingState {
        if (Tokens.REPLICA) return RingState(buzz = 0.0, pulse = -1.0)
        if (s.phase != PHASE.ringing || s.grab || s.reduced) {
            return RingState(buzz = 0.0, pulse = -1.0)
        }
        val time = ((s.clock - Tokens.RING.offset) % Tokens.RING.every + Tokens.RING.every) % Tokens.RING.every
        var buzz = 0.0
        for (start in Tokens.RING.buzz) {
            val progress = (time - start) / Tokens.RING.buzzLength
            if (progress >= 0.0 && progress <= 1.0) {
                buzz += sin(PI * progress) * Tokens.RING.buzzAngle *
                    sin(2.0 * PI * Tokens.RING.buzzHz * time)
            }
        }
        val pulse = if (time < Tokens.RING.pulse) {
            time / Tokens.RING.pulse
        } else {
            -1.0
        }
        return RingState(buzz = buzz, pulse = pulse)
    }

    private fun captureLoopCheckpoint(s: Sim): LoopCheckpoint {
        return LoopCheckpoint(
            clock = s.clock,
            px = s.px.copyOf(),
            py = s.py.copyOf(),
            vx = s.vx.copyOf(),
            vy = s.vy.copyOf(),
            th = s.th,
            tw = s.tw,
            ax = s.ax,
            ay = s.ay,
            natLen = s.natLen,
            natTarget = s.natTarget,
            phase = s.phase,
            phaseT = s.phaseT,
            callT = s.callT,
            wConn = s.wConn,
            wOff = s.wOff,
            wSrc = s.wSrc,
            look = s.look,
        )
    }

    private fun restoreLoopCheckpoint(s: Sim) {
        val checkpoint = s.loopCheckpoint ?: return
        checkpoint.px.copyInto(s.px)
        checkpoint.py.copyInto(s.py)
        checkpoint.vx.copyInto(s.vx)
        checkpoint.vy.copyInto(s.vy)
        s.clock = checkpoint.clock
        s.th = checkpoint.th
        s.tw = checkpoint.tw
        s.ax = checkpoint.ax
        s.ay = checkpoint.ay
        s.natLen = checkpoint.natLen
        s.natTarget = checkpoint.natTarget
        s.phase = checkpoint.phase
        s.phaseT = checkpoint.phaseT
        s.callT = checkpoint.callT
        s.wConn = checkpoint.wConn
        s.wOff = checkpoint.wOff
        s.wSrc = checkpoint.wSrc
        s.look = checkpoint.look
        s.grab = false
        s.scripted = false
        s.armed = 0
        s.tickAt = 0
        s.demoOn = true
        s.demoT = 0.0
        s.quiet = 0.0
        s.events.clear()
    }

    private fun beginResume(s: Sim, g: Geometry) {
        val checkpoint = s.loopCheckpoint
        if (checkpoint == null) {
            s.demoOn = true
            s.demoT = 0.0
            s.quiet = 0.0
            return
        }
        s.resumeFrom = ResumeSnapshot(
            px = s.px.copyOf(),
            py = s.py.copyOf(),
            th = s.th,
        )
        s.resumeT = 0.0
        s.demoOn = false
        s.demoT = 0.0
        s.phase = PHASE.ringing
        s.phaseT = checkpoint.phaseT
        s.natTarget = g.baseLength
    }

    private fun applyResumeEase(s: Sim, dt: Double) {
        val start = s.resumeFrom ?: return
        val checkpoint = s.loopCheckpoint ?: return
        s.resumeT += dt
        val linear = clamp(s.resumeT / Tokens.RESUME_EASE)
        val eased = (1.0 - cos(PI * linear)) / 2.0
        for (i in s.px.indices) {
            s.px[i] = start.px[i] + (checkpoint.px[i] - start.px[i]) * eased
            s.py[i] = start.py[i] + (checkpoint.py[i] - start.py[i]) * eased
        }
        s.th = start.th + (checkpoint.th - start.th) * eased
        if (linear >= 1.0) {
            s.resumeFrom = null
            restoreLoopCheckpoint(s)
        }
    }

    private const val RUNTIME_RESUME_SECONDS = Tokens.TOUCH_RESUME_MS / 1000.0
    private const val LOOP_EPSILON = 1e-9
}

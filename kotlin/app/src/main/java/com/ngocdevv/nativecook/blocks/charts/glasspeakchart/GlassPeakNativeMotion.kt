package com.ngocdevv.nativecook.blocks.charts.glasspeakchart

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/** Direct Kotlin translation of peak-sim.ts and peak-demo.ts. All integration uses double precision. */
internal object GlassPeakNativeMotion {
    const val fixedDt = 1.0 / 240.0
    private const val tau = 2.0 * PI
    private const val impact = 1.6
    private const val hitStop = .05
    private const val ignition = 1.88
    private const val countStart = ignition - .04
    private const val statusReturn = ignition + .15
    private val splashLaunch = arrayOf(
        doubleArrayOf(-14.0, -120.0, 260.0), doubleArrayOf(-9.0, -78.0, 365.0),
        doubleArrayOf(-4.0, -38.0, 430.0), doubleArrayOf(2.0, 24.0, 405.0),
        doubleArrayOf(9.0, 87.0, 305.0), doubleArrayOf(14.0, 120.0, 280.0),
        doubleArrayOf(-7.0, -57.0, 420.0), doubleArrayOf(6.0, 53.0, 385.0),
    )
    private val springKeys = arrayOf(
        "orbU", "orbR", "orbGain", "cardScale", "tiltX", "tiltY", "poolLevel",
        "poolHumpX", "poolHump", "poolSlope", "poolGain", "beam", "numberScale",
        "pillPress", "refreshPress",
    )
    private val rest = Array(3) { GlassPeakNativeFrame(it) }
    private val contract = GlassPeakNativeContract

    fun clamp(value: Double, low: Double = 0.0, high: Double = 1.0) = value.coerceIn(low, high)

    private fun bezier(t: Double, x1: Double, y1: Double, x2: Double, y2: Double): Double {
        if (t <= 0.0) return 0.0
        if (t >= 1.0) return 1.0
        var low = 0.0
        var high = 1.0
        var u = t
        repeat(18) {
            val inverse = 1.0 - u
            val x = 3 * inverse * inverse * u * x1 + 3 * inverse * u * u * x2 + u * u * u
            if (x < t) low = u else high = u
            u = (low + high) * .5
        }
        val inverse = 1.0 - u
        return 3 * inverse * inverse * u * y1 + 3 * inverse * u * u * y2 + u * u * u
    }

    fun easeOut(t: Double) = bezier(t, .23, 1.0, .32, 1.0)
    fun easeMove(t: Double) = bezier(t, .77, 0.0, .175, 1.0)

    private fun spring(body: GlassPeakBody, target: Double, stiffness: Double, damping: Double, dt: Double): Double {
        val offset = body.x - target
        val decay = damping / 2
        val discriminant = stiffness - decay * decay
        val attenuation = exp(-decay * dt)
        when {
            discriminant > .00001 -> {
                val omega = sqrt(discriminant)
                val co = cos(omega * dt)
                val si = sin(omega * dt)
                val c = (body.v + decay * offset) / omega
                body.x = target + attenuation * (offset * co + c * si)
                body.v = attenuation * (body.v * co - (decay * c + omega * offset) * si)
            }
            discriminant < -.00001 -> {
                val root = sqrt(-discriminant)
                val r1 = -decay + root
                val r2 = -decay - root
                val a = (body.v - r2 * offset) / (r1 - r2)
                val c = offset - a
                body.x = target + a * exp(r1 * dt) + c * exp(r2 * dt)
                body.v = a * r1 * exp(r1 * dt) + c * r2 * exp(r2 * dt)
            }
            else -> {
                val c = body.v + decay * offset
                body.x = target + attenuation * (offset + c * dt)
                body.v = attenuation * (body.v - decay * c * dt)
            }
        }
        return body.x
    }

    private fun body(s: GlassPeakNativeState, key: String) = s.bodies.getValue(key)

    private fun put(body: GlassPeakBody, value: Double, velocity: Double = 0.0) {
        body.x = value
        body.v = velocity
    }

    private fun nearest(s: GlassPeakNativeState, u: Double): Int {
        var best = 0
        for (i in 1..3) if (abs(s.nodes[i] - u) < abs(s.nodes[best] - u)) best = i
        return best
    }

    private fun ratio(s: GlassPeakNativeState, u: Double): Double = clamp(
        contract.heightAt(s.f.ys, u) / max(.001, contract.heightAt(s.f.ys, s.nodes[2])),
    )

    private fun place(s: GlassPeakNativeState) {
        val f = s.f
        f.orbX = contract.xOf(s.orbU)
        f.orbY = contract.yOf(contract.heightAt(f.ys, s.orbU))
        for (i in 0..3) {
            f.nodeX[i] = contract.xOf(s.nodes[i])
            f.nodeY[i] = contract.yOf(contract.heightAt(f.ys, s.nodes[i]))
        }
    }

    private fun buildArc(s: GlassPeakNativeState) {
        s.arc[0] = 0.0
        for (i in 1 until contract.samples) {
            val dy = contract.yOf(s.f.ys[i]) - contract.yOf(s.f.ys[i - 1])
            val dx = (contract.plotRight - contract.plotLeft) / (contract.samples - 1)
            s.arc[i] = s.arc[i - 1] + sqrt(dx * dx + dy * dy)
        }
        val at = s.nodes[2] * (contract.samples - 1)
        val i = floor(at).toInt()
        s.arcPeak = s.arc[i] + (s.arc[i + 1] - s.arc[i]) * (at - i)
    }

    private fun arcU(s: GlassPeakNativeState, progress: Double): Double {
        val goal = s.arcPeak * clamp(progress)
        for (i in 1 until contract.samples) {
            if (s.arc[i] >= goal) {
                return (i - 1 + (goal - s.arc[i - 1]) / max(.001, s.arc[i] - s.arc[i - 1])) / (contract.samples - 1)
            }
        }
        return s.nodes[2]
    }

    private fun home(s: GlassPeakNativeState, period: Int) {
        val presented = s.f.presented
        s.f = rest[period].copyDeep()
        s.f.presented = presented
        s.orbU = contract.periods[period].nodes[2]
        s.node = 2
        s.nodes = contract.periods[period].nodes.copyOf()
        for (key in springKeys) put(body(s, key), if (key == "orbU") s.orbU else s.f[key])
        for (i in 0..3) {
            s.nodeAge[i] = 10.0
            s.nodeVelocity[i] = 0.0
            s.nodeActive[i] = true
        }
        s.mode = "rest"
        s.idleLife = 1.0
        s.age = 0.0
        s.settling = false
        s.flashAge = 10.0
        s.glintAge = 10.0
        s.ringAge = 10.0
        s.flashPower = 0.0
        s.glintPower = 0.0
        s.ringPower = 0.0
        s.beamAge = 10.0
        s.stirAge = 10.0
        s.splashAge = 10.0
        s.beamPower = 0.0
        s.stirPower = 0.0
        s.scrubStir = 0.0
        s.splashPending = 0
        for (i in 0 until 8) { s.splashVX[i] = 0.0
        s.splashVY[i] = 0.0 }
        s.finger = false
        s.scrubbing = false
        s.touchOn = false
        s.releaseAge = 10.0
    }

    fun create(autoPlay: Boolean, reduced: Boolean): GlassPeakNativeState {
        val s = GlassPeakNativeState(autoPlay, reduced)
        for (key in springKeys) {
            s.bodies[key] = GlassPeakBody(if (key == "orbU") s.orbU else s.f[key])
        }
        if (!autoPlay && !reduced) {
            s.mode = "opening"
            val f = s.f
            f.lit = 0.0
            f.orbGain = 0.0
            f.orbR = 7.0
            s.orbU = 0.0
            f.poolLevel = .08
            f.poolHump = 0.0
            f.poolGain = .7
            f.beam = 0.0
            f.beamReach = 0.0
            put(body(s, "beam"), 0.0)
            f.value = 0.0
            f.numberAlpha = .35
            f.status = 1
            f.statusPrev = 1
            for (i in 0..3) { f.nodeLit[i] = 0.0
            f.drop[i] = 0.0 }
            put(body(s, "orbU"), 0.0)
            put(body(s, "orbR"), 7.0)
            put(body(s, "poolLevel"), .08)
            put(body(s, "poolHump"), 0.0)
            put(body(s, "poolGain"), .7)
            place(s)
        }
        return s
    }

    fun isRest(s: GlassPeakNativeState) = s.mode == "rest" && !s.finger && !s.settling &&
        !s.pressPill && !s.pressRefresh && s.queue == null

    fun contact(s: GlassPeakNativeState, down: Boolean) {
        s.contact = down
        s.restWait = 0.0
        if (down) {
            val wasPaused = s.paused
            s.paused = true
            s.returning = false
            s.closing = 0.0
            if (s.finger && !s.fingerUser) {
                s.node = nearest(s, s.orbU)
                s.finger = false
                s.scrubbing = false
                s.settling = true
                s.settleAge = 0.0
                s.touchFrom = s.f.touchA
                s.releaseAge = 0.0
                s.touchOn = false
            }
            if (!wasPaused) { s.pressPill = false
            s.pressRefresh = false }
        }
    }

    fun press(s: GlassPeakNativeState, action: GlassPeakAction, down: Boolean, user: Boolean) {
        if (user && down) contact(s, true)
        if (action == GlassPeakAction.Period) {
            s.pressPill = down
            s.pillAge = 0.0
            s.pillFrom = s.f.pillPress
        } else {
            s.pressRefresh = down
            s.refreshAge = 0.0
            s.refreshFrom = s.f.refreshPress
        }
        if (user && down) s.light++
        if (user && !down) contact(s, false)
    }

    private fun begin(s: GlassPeakNativeState, action: GlassPeakAction, user: Boolean) {
        s.from = s.f.copyDeep()
        s.fromU = s.orbU
        s.age = 0.0
        s.userRun = user
        s.impactDone = false
        s.ignitionDone = false
        s.arrivalDone = false
        s.settling = false
        s.revision++
        if (s.reduced) {
            s.mode = "fade"
            s.fadePeriod = if (action == GlassPeakAction.Period) (s.f.period + 1) % 3 else s.f.period
            s.fadeSwapped = false
            return
        }
        if (action == GlassPeakAction.Refresh) {
            s.beamAge = 10.0
            s.beamPower = 0.0
            s.mode = "refresh"
            s.f.statusPrev = s.f.status
            s.f.status = 1
            s.f.statusMix = 0.0
            s.spinFrom = s.f.spin
            s.node = 2
            buildArc(s)
            for (i in 0..3) { s.nodeActive[i] = false
            s.nodeAge[i] = -1.0 }
        } else {
            s.mode = "morph"
            s.f.periodPrev = s.f.period
            s.f.period = (s.f.period + 1) % 3
            s.f.periodMix = 0.0
        }
    }

    fun request(s: GlassPeakNativeState, action: GlassPeakAction, user: Boolean) {
        if (user) { s.paused = true
        s.restWait = 0.0 }
        if (s.mode != "rest" || s.finger) {
            s.queue = action
            s.queueUser = user
            return
        }
        begin(s, action, user)
    }

    private fun pop(s: GlassPeakNativeState, node: Int, amount: Double) {
        s.nodeVelocity[node] += amount * 34
    }

    private fun flare(s: GlassPeakNativeState, power: Double, glint: Double, ring: Double) {
        s.flashAge = 0.0
        s.glintAge = 0.0
        s.flashPower = power
        s.glintPower = glint
        if (ring > 0) {
            s.ringAge = 0.0
            s.ringPower = ring
            s.f.ringX = s.f.orbX
            s.f.ringY = s.f.orbY
        }
    }

    fun fingerDown(s: GlassPeakNativeState, x: Double, y: Double, user: Boolean) {
        if (user) contact(s, true)
        s.finger = true
        s.fingerUser = user
        s.scrubbing = s.mode == "rest" && s.f.lit >= .9999 && y >= 258 && y <= 494
        s.fingerAge = 0.0
        s.catchFromU = s.orbU
        s.fingerU = clamp((x - contract.plotLeft) / (contract.plotRight - contract.plotLeft), .03, .97)
        s.fingerVelocity = 0.0
        for (i in 0..3) {
            val gap = s.fingerU - s.nodes[i]
            s.nodeSide[i] = if (gap > .000001) 1 else if (gap < -.000001) -1 else 0
        }
        s.f.touchX = x
        s.f.touchY = y
        s.touchFrom = s.f.touchA
        s.touchOn = true
        s.releaseAge = 0.0
        if (s.scrubbing) s.settling = false
        if (!s.reduced && !s.scrubbing) body(s, "poolSlope").v += clamp((x - 201) / 173, -1.0, 1.0) * 1.8
    }

    fun fingerMove(s: GlassPeakNativeState, x: Double, y: Double, velocityX: Double) {
        if (!s.finger) return
        s.f.touchX = x
        s.f.touchY = y
        if (!s.scrubbing) return
        val u = clamp((x - contract.plotLeft) / (contract.plotRight - contract.plotLeft), .03, .97)
        for (i in 0..3) {
            val gap = u - s.nodes[i]
            val side = if (gap > .000001) 1 else if (gap < -.000001) -1 else 0
            if (s.nodeSide[i] != 0 && side != s.nodeSide[i]) {
                if (!s.reduced) pop(s, i, .35)
                if (s.fingerUser) s.selection++
                if (i == 2) {
                    if (!s.reduced) flare(s, .35, .3, 0.0)
                    if (s.fingerUser) s.medium++
                }
            }
            s.nodeSide[i] = side
        }
        s.fingerU = u
        s.fingerVelocity = velocityX / (contract.plotRight - contract.plotLeft)
    }

    fun fingerUp(s: GlassPeakNativeState, velocityX: Double, user: Boolean) {
        if (!s.finger) return
        if (s.scrubbing) {
            s.node = nearest(s, s.fingerU)
            if (s.reduced) {
                s.orbU = s.fingerU
                put(body(s, "orbU"), s.orbU)
            } else put(body(s, "orbU"), s.orbU, velocityX / (contract.plotRight - contract.plotLeft))
            s.settling = !s.reduced
            s.settleAge = 0.0
            s.revision++
            if (user) s.announcements++
        }
        s.finger = false
        s.scrubbing = false
        s.touchOn = false
        s.touchFrom = s.f.touchA
        s.releaseAge = 0.0
        if (user) contact(s, false)
    }

    fun adjust(s: GlassPeakNativeState, direction: Int) {
        contact(s, true)
        contact(s, false)
        if (s.mode != "rest") return
        s.node = ((if (s.settling) s.node else nearest(s, s.orbU)) + direction).coerceIn(0, 3)
        s.settling = !s.reduced
        s.settleAge = 0.0
        if (s.reduced) s.orbU = s.nodes[s.node]
        put(body(s, "orbU"), s.orbU)
        s.selection++
        s.revision++
        s.announcements++
    }

    fun returnHome(s: GlassPeakNativeState) {
        if (!isRest(s)) return
        s.returning = true
        if (s.f.period != 0) request(s, GlassPeakAction.Period, false)
        else if (abs(s.orbU - s.nodes[2]) > .00001) {
            s.node = 2
            s.settling = true
            s.settleAge = 0.0
        }
    }

    fun resetLoop(s: GlassPeakNativeState) {
        home(s, 0)
        s.loop = 0.0
        s.time = 0.0
        s.demoEvent = 0
        s.closing = 0.0
        s.paused = false
        s.returning = false
        s.restWait = 0.0
        s.pressPill = false
        s.pressRefresh = false
        s.queue = null
        s.revision++
    }

    fun closeLoop(s: GlassPeakNativeState, progressInput: Double) {
        val progress = if (progressInput >= 1 - 1e-9) 1.0 else progressInput
        val previous = easeOut(s.closing)
        val next = easeOut(progress)
        val keep = clamp((1 - next) / max(1e-9, 1 - previous))
        s.closing = progress
        for (key in springKeys) {
            val b = body(s, key)
            val target = if (key == "orbU") contract.periods[0].nodes[2] else rest[0][key]
            b.x = target + (b.x - target) * keep
            b.v *= keep
        }
        for (i in 0..3) s.nodeVelocity[i] *= keep
        s.fingerVelocity *= keep
    }

    private fun pluck(t: Double): Double {
        if (t <= 0.0) return 0.0
        val zeta = .55
        val omega = 9.93
        val root = sqrt(1 - zeta * zeta)
        val wd = omega * root
        val raw = 1 - exp(-zeta * omega * t) * (cos(wd * t) + zeta / root * sin(wd * t))
        return raw + (1 - raw) * easeOut((t - .6) / .14)
    }

    private fun liquidSurface(s: GlassPeakNativeState, x: Double): Double {
        val hump = clamp(s.f.poolHump + .05 * sin(tau * s.time / contract.loop * 3) * s.idleLife)
        return contract.poolSurfaceY(s.f.poolLevel, hump, s.f.poolHumpX, s.f.poolSlope, x)
    }

    private fun splashStep(s: GlassPeakNativeState, dt: Double) {
        val f = s.f
        if (s.splashPending > 0) {
            s.splashAge = 0.0
            for (i in 0 until 8) {
                val launch = splashLaunch[i]
                if (i < s.splashPending) {
                    f.splashX[i] = clamp(f.orbX + launch[0], 49.0, 353.0)
                    f.splashY[i] = liquidSurface(s, f.splashX[i])
                    f.splashR[i] = 5.0
                    s.splashVX[i] = launch[1] * s.splashSpeed
                    s.splashVY[i] = -launch[2] * s.splashSpeed
                } else {
                    f.splashX[i] = 0.0
                    f.splashY[i] = 0.0
                    f.splashR[i] = 0.0
                    s.splashVX[i] = 0.0
                    s.splashVY[i] = 0.0
                }
            }
            s.splashPending = 0
            return
        }
        s.splashAge += dt
        for (i in 0 until 8) {
            if (f.splashR[i] == 0.0) continue
            f.splashX[i] += s.splashVX[i] * dt
            f.splashY[i] += s.splashVY[i] * dt + .5 * 700 * dt * dt
            s.splashVY[i] += 700 * dt
            val flight = min(1.1, 2 * splashLaunch[i][2] * s.splashSpeed / 700)
            f.splashR[i] = 5 - 2.8 * clamp(s.splashAge / flight)
            f.splashX[i] = clamp(f.splashX[i], 44 + f.splashR[i], 358 - f.splashR[i])
            if (f.splashY[i] < 500 + f.splashR[i]) {
                f.splashY[i] = 500 + f.splashR[i]
                s.splashVY[i] = max(0.0, s.splashVY[i])
            }
            if (s.splashAge >= 1.1 - 1e-9 || f.splashY[i] >= liquidSurface(s, f.splashX[i]) - 1e-7) {
                f.splashX[i] = 0.0
                f.splashY[i] = 0.0
                f.splashR[i] = 0.0
                s.splashVX[i] = 0.0
                s.splashVY[i] = 0.0
            }
        }
    }

    private fun liquidStep(s: GlassPeakNativeState, dt: Double, orbSpeed: Double) {
        val f = s.f
        if (s.reduced) {
            f.beam = .22
            f.beamReach = 1.0
            f.poolStir = 0.0
            return
        }
        if (s.mode == "rest" || s.mode == "morph") {
            val pulse = if (s.beamAge < .7) .6 * s.beamPower * sin(PI * clamp(s.beamAge / .7)) else 0.0
            val target = if (s.scrubbing) .6 else .22
            var base = spring(body(s, "beam"), target, 900.0, 60.0, dt)
            if (abs(base - target) < .00001 && abs(body(s, "beam").v) < .0001) {
                put(body(s, "beam"), target)
                base = target
            }
            f.beam = clamp(base + pulse)
            f.beamReach = 1.0
        } else put(body(s, "beam"), f.beam)
        val stirring = if (s.scrubbing) clamp(max(abs(orbSpeed) / 500, abs(f.poolSlope) * 4)) else 0.0
        s.scrubStir += (stirring - s.scrubStir) * (1 - exp(-8 * dt))
        if (stirring == 0.0 && s.scrubStir < .00001) s.scrubStir = 0.0
        f.poolStir = clamp(max(s.scrubStir, s.stirPower * (1 - easeOut(s.stirAge / s.stirDuration))))
        splashStep(s, dt)
    }

    private fun refreshStep(s: GlassPeakNativeState, dt: Double) {
        val f = s.f
        val a = if (s.age >= impact) max(impact, s.age - hitStop) else s.age
        val p = contract.periods[f.period]
        val power = p.value / 300
        f.tube = 1 + if (a >= impact) .8 * power * (1 - easeOut((a - impact) / .6)) else 0.0
        f.beam = if (a < impact) s.from.beam * (1 - easeOut(a / .15)) else 1 - .78 * easeOut((a - impact) / 1.4)
        f.beamReach = if (a < impact) s.from.beamReach * (1 - easeOut(a / .15)) else easeOut((a - impact) / .24)
        when {
            a < .42 -> {
                val travel = easeMove(a / .42)
                f.lit = s.from.lit * (1 - travel)
                s.orbU = min(s.fromU, f.lit)
                f.orbR = s.from.orbR + (7 - s.from.orbR) * travel
                f.orbGain = (s.from.orbGain + (.8 - s.from.orbGain) * travel) * (1 - easeOut((a - .34) / .08))
                f.value = s.from.value * (1 - easeOut(a / .42))
                f.numberAlpha = s.from.numberAlpha + (.35 - s.from.numberAlpha) * easeOut(a / .24)
                for (i in 0..3) {
                    if (s.nodeAge[i] < 0 && f.lit <= s.nodes[i]) s.nodeAge[i] = 0.0
                    if (s.nodeAge[i] >= 0) {
                        s.nodeAge[i] += dt
                        f.nodeLit[i] = s.from.nodeLit[i] * (1 - easeOut(s.nodeAge[i] / .12))
                        f.drop[i] = s.from.drop[i] * (1 - easeOut(s.nodeAge[i] / .2))
                    }
                }
            }
            a < .7 -> {
                f.lit = 0.0
                s.orbU = 0.0
                f.orbR = 7.0
                f.orbGain = 0.0
                f.value = 0.0
                f.numberAlpha = .35
                for (i in 0..3) {
                    if (s.nodeAge[i] >= 0) s.nodeAge[i] += dt
                    f.nodeLit[i] = 0.0
                    f.drop[i] = s.from.drop[i] * (1 - easeOut(s.nodeAge[i] / .2))
                }
            }
            a < impact -> {
                val run = clamp((a - .7) / .9)
                f.lit = arcU(s, .3 * run + .7 * run * run)
                s.orbU = f.lit
                f.orbR = 7 + easeOut((a - .7) / .12) + 2 * run
                f.orbGain = 1.2 * easeOut((a - .7) / .12)
                f.tip = easeOut((a - .7) / .12)
            }
            else -> {
                if (!s.impactDone) {
                    s.impactDone = true
                    s.impacts++
                    s.impactClock = if (s.autoPlay && !s.paused) s.loop + dt else s.time
                    s.orbU = s.nodes[2]
                    place(s)
                    flare(s, 1.0, .6 * power, 1.0)
                    put(body(s, "orbR"), 10.0, 247.0)
                    body(s, "cardScale").v = .39
                    body(s, "tiltX").v = -1.1
                    if (s.userRun) s.heavy++
                }
                s.orbU = s.nodes[2]
                f.lit = s.nodes[2] + (1 - s.nodes[2]) * easeOut((a - impact) / .38)
                f.tip = 1 - easeOut((a - ignition) / .1)
                f.orbR = spring(body(s, "orbR"), contract.orbRadius, 320.0, 14.0, dt)
                f.orbGain = 1.2 + .8 * easeOut((a - impact) / .035) - easeOut((a - 1.635) / .565)
            }
        }
        if (a >= .7) {
            for (i in 0..3) {
                if (!s.nodeActive[i] && f.lit >= s.nodes[i] - .000001) {
                    s.nodeActive[i] = true
                    s.nodeAge[i] = 0.0
                    pop(s, i, .5)
                }
                if (s.nodeActive[i]) {
                    s.nodeAge[i] += dt
                    f.nodeLit[i] = easeOut(s.nodeAge[i] / .12)
                    f.drop[i] = easeOut(s.nodeAge[i] / if (i == 2) .26 else .32)
                } else { f.nodeLit[i] = 0.0
                f.drop[i] = 0.0 }
            }
        }
        if (a >= ignition && !s.ignitionDone) {
            s.ignitionDone = true
            body(s, "poolSlope").v += 3.5
            s.stirAge = 0.0
            s.stirPower = 1.0
            s.stirDuration = 1.2
            s.splashPending = (8 * max(p.value / 300, .4)).roundToInt()
            s.splashSpeed = .6 + .4 * p.value / 300
            put(body(s, "numberScale"), .96, 1.25 * power)
        }
        val ignited = a >= ignition
        f.poolLevel = spring(body(s, "poolLevel"), if (ignited) p.level else .08,
            if (ignited) 60.0 else 180.0, if (ignited) 9.0 else 27.0, dt)
        f.poolHump = spring(body(s, "poolHump"), if (ignited) .35 + .6 * (1 - easeOut((a - 2.05) / .55)) else 0.0, 100.0, 15.0, dt)
        f.poolGain = spring(body(s, "poolGain"), if (ignited) 1 + .35 * (1 - easeOut((a - 2.08) / .5)) else if (a > 1.4) .55 else .7, 180.0, 25.0, dt)
        f.cardScale = spring(body(s, "cardScale"), if (a >= 1.4 && a < impact) .992 else 1.0,
            if (a < impact) 240.0 else 140.0, if (a < impact) 26.0 else 14.0, dt)
        f.tiltX = spring(body(s, "tiltX"), 0.0, 180.0, 20.0, dt)
        if (a >= countStart) {
            val roll = clamp((a - countStart) / 1.1)
            f.value = p.value * easeOut(roll)
            f.numberAlpha = .35 + .65 * easeOut((a - countStart) / .26)
            f.numberGlow = sin(PI * roll)
        }
        f.numberScale = if (ignited) spring(body(s, "numberScale"), 1.0, 260.0, 16.0, min(dt, a - ignition))
            else 1 - .04 * easeOut((a - .7) / .9)
        if (a < statusReturn) {
            f.statusMix = easeOut(a / .24)
            f.spin = s.spinFrom + a * tau / .8
        } else {
            if (f.status != 0) {
                f.statusPrev = 1
                f.status = 0
                s.spinTarget = ceil((s.spinFrom + statusReturn * tau / .8) / tau) * tau
                s.revision++
            }
            f.statusMix = easeOut((a - statusReturn) / .24)
            val atStop = s.spinFrom + statusReturn * tau / .8
            f.spin = atStop + (s.spinTarget - atStop) * easeOut((a - statusReturn) / .4)
        }
        if (a >= 3.4) {
            s.mode = "rest"
            f.statusPrev = 0
            f.statusMix = 1.0
            s.revision++
            put(body(s, "orbU"), s.orbU)
            s.settling = true
            s.settleAge = 1.25
        }
    }

    private fun morphStep(s: GlassPeakNativeState, dt: Double) {
        val f = s.f
        val a = s.age
        val p = contract.periods[f.period]
        val move = easeMove(a / .6)
        for (i in 0 until contract.samples) {
            f.ys[i] = clamp(s.from.ys[i] + (contract.curves[f.period][i] - s.from.ys[i]) *
                pluck(a - .26 * i / (contract.samples - 1)))
        }
        for (i in 0..3) {
            val oldU = (s.from.nodeX[i] - contract.plotLeft) / (contract.plotRight - contract.plotLeft)
            s.nodes[i] = oldU + (p.nodes[i] - oldU) * move
            f.nodeBottom[i] = s.from.nodeBottom[i] +
                (contract.dropBottom(contract.xOf(p.nodes[i]), i == 2) - s.from.nodeBottom[i]) * move
        }
        s.orbU = s.fromU + (p.nodes[s.node] - s.fromU) * move
        put(body(s, "orbU"), s.orbU)
        f.periodMix = easeOut(a / .42)
        f.chevron = if (a < .52) tau * easeMove(a / .52) else 0.0
        val newRatio = clamp(contract.heightAt(contract.curves[f.period], p.nodes[s.node]) /
            contract.heightAt(contract.curves[f.period], p.nodes[2]))
        f.value = s.from.value + (p.value * newRatio - s.from.value) * easeOut(a / .7)
        f.numberGlow = .5 * sin(PI * clamp(a / .7))
        f.poolLevel = spring(body(s, "poolLevel"), p.level * (.22 + .78 * newRatio), 60.0, 9.0, dt)
        f.poolHump = spring(body(s, "poolHump"), .35, 90.0, 9.0, dt)
        if (a >= .6 && !s.arrivalDone) {
            s.arrivalDone = true
            s.arrivals++
            val power = p.value / 300
            s.beamAge = 0.0
            s.stirAge = 0.0
            s.beamPower = power
            s.stirPower = .5
            s.stirDuration = .8
            if (f.period == 0) { s.splashPending = 5
            s.splashSpeed = .7 }
            place(s)
            flare(s, .25 * power, .4 * power, if (f.period == 0) .5 else 0.0)
            body(s, "orbR").v += 150 * power
            if (f.period == 0 && s.userRun) s.medium++
            s.revision++
        }
        f.orbR = spring(body(s, "orbR"), if (s.node == 2) contract.orbRadius else 7.5 + 3.5 * newRatio, 320.0, 18.0, dt)
        f.orbGain = spring(body(s, "orbGain"), 1.0, 180.0, 25.0, dt)
        f.poolGain = spring(body(s, "poolGain"), if (f.period == 0 && a >= .6) 1 + .25 * sin(PI * clamp((a - .6) / .7)) else 1.0, 180.0, 25.0, dt)
        if (a >= 1.05) {
            s.mode = "rest"
            f.periodPrev = f.period
            f.periodMix = 1.0
            s.settling = true
            s.settleAge = 1.2
            s.revision++
        }
    }

    private fun integrate(s: GlassPeakNativeState, dt: Double) {
        s.time += dt
        s.age += dt
        if (s.mode == "refresh") {
            if (abs(s.age - impact) < 1e-9) s.age = impact
            if (abs(s.age - impact - hitStop) < 1e-9) s.age = impact + hitStop
            if (abs(s.age - ignition - hitStop) < 1e-9) s.age = ignition + hitStop
        } else if (s.mode == "morph" && abs(s.age - .6) < 1e-9) s.age = .6
        if (s.mode == "refresh" && s.impactDone && s.age <= impact + hitStop + 1e-9) return
        val previousOrbX = s.f.orbX
        val idleTarget = if (s.mode == "rest" && !s.finger) 1.0 else 0.0
        s.idleLife += (idleTarget - s.idleLife) * (1 - exp(-18 * dt))
        s.fingerAge += dt
        s.releaseAge += dt
        s.pillAge += dt
        s.refreshAge += dt
        s.flashAge += dt
        s.glintAge += dt
        s.ringAge += dt
        s.beamAge += dt
        s.stirAge += dt
        var f = s.f
        if (s.mode == "opening") {
            f.spin += dt * tau / .8
            if (s.age >= .35) {
                begin(s, GlassPeakAction.Refresh, false)
                s.age = .42
                s.spinFrom = f.spin - .42 * tau / .8
                for (i in 0..3) s.nodeAge[i] = 1.0
            }
        }
        when (s.mode) {
            "fade" -> {
                if (s.age < .12) f.presented = 1 - easeOut(s.age / .12)
                else {
                    if (!s.fadeSwapped) {
                        val age = s.age
                        home(s, s.fadePeriod)
                        s.mode = "fade"
                        s.age = age
                        s.fadeSwapped = true
                        s.revision++
                    }
                    s.f.presented = easeOut((s.age - .12) / .16)
                    if (s.age >= .28) {
                        s.mode = "rest"
                        s.f.presented = 1.0
                        s.revision++
                    }
                    f = s.f
                }
            }
            "refresh" -> refreshStep(s, dt)
            "morph" -> morphStep(s, dt)
            "rest" -> {
                if (s.scrubbing) {
                    val catchup = if (s.reduced) 1.0 else easeOut(s.fingerAge / .09)
                    s.orbU = s.catchFromU + (s.fingerU - s.catchFromU) * catchup
                    put(body(s, "orbU"), s.orbU, s.fingerVelocity)
                } else if (!s.reduced) {
                    val orbBody = body(s, "orbU")
                    s.orbU = clamp(spring(orbBody, s.nodes[s.node], 260.0, 22.0, dt), .03, .97)
                    if (s.orbU != orbBody.x) put(orbBody, s.orbU)
                }
                val height = ratio(s, s.orbU)
                val p = contract.periods[f.period]
                f.value = p.value * height
                val level = p.level * (.22 + .78 * height)
                f.poolLevel = if (s.reduced) level else spring(body(s, "poolLevel"), level, 120.0, 16.0, dt)
                f.poolHump = if (s.reduced) (if (s.scrubbing) .75 else .35)
                    else spring(body(s, "poolHump"), if (s.scrubbing) .75 else .35, 90.0, 9.0, dt)
                f.poolGain = if (s.reduced) 1.0 else spring(body(s, "poolGain"), 1.0, 160.0, 22.0, dt)
                val radius = if (s.node == 2 && !s.scrubbing && !s.settling) contract.orbRadius else 7.5 + 3.5 * height
                f.orbR = if (s.reduced || s.scrubbing) radius else spring(body(s, "orbR"), radius, 320.0, 14.0, dt)
                if (s.scrubbing) put(body(s, "orbR"), f.orbR)
                f.orbGain = if (s.reduced) 1.0 else if (s.scrubbing) .8 + .4 * height
                    else spring(body(s, "orbGain"), 1.0, 180.0, 24.0, dt)
                if (s.scrubbing) put(body(s, "orbGain"), f.orbGain)
                f.numberAlpha = 1.0
                f.numberScale += (1 - f.numberScale) * (1 - exp(-18 * dt))
                f.numberGlow = 0.0
                if (s.settling) {
                    s.settleAge += dt
                    if (s.settleAge >= 1.3 && abs(body(s, "orbU").v) < .001 && abs(s.orbU - s.nodes[s.node]) < .0001) {
                        s.settling = false
                        s.revision++
                    }
                }
            }
        }
        if (s.mode != "fade") {
            if (s.mode != "rest") put(body(s, "orbGain"), f.orbGain)
            place(s)
            if (s.mode != "refresh") {
                f.cardScale = if (s.reduced) 1.0 else spring(body(s, "cardScale"), if (s.finger) .985 else 1.0,
                    if (s.finger) 300.0 else 140.0, if (s.finger) 28.0 else 14.0, dt)
                f.tiltX = if (s.reduced) 0.0 else spring(body(s, "tiltX"),
                    if (s.finger) clamp((416 - f.touchY) / 240, -1.0, 1.0) * .06 else 0.0,
                    if (s.finger) 180.0 else 140.0, if (s.finger) 20.0 else 14.0, dt)
            }
            f.tiltY = if (s.reduced) 0.0 else spring(body(s, "tiltY"),
                if (s.finger) clamp((f.touchX - 201) / 173, -1.0, 1.0) * .1 else 0.0,
                if (s.finger) 180.0 else 140.0, if (s.finger) 20.0 else 14.0, dt)
            f.poolHumpX = if (s.reduced) f.orbX else spring(body(s, "poolHumpX"), f.orbX, 90.0, 9.0, dt)
            f.poolSlope = if (s.reduced) 0.0 else spring(body(s, "poolSlope"),
                if (s.scrubbing) -clamp(s.fingerVelocity * 346 / 900, -1.0, 1.0) * .25 else 0.0,
                110.0, 10.0, dt)
            val orbSpeed = if (s.scrubbing && s.fingerAge >= .09)
                s.fingerVelocity * (contract.plotRight - contract.plotLeft)
                else (f.orbX - previousOrbX) / max(dt, 1e-9)
            liquidStep(s, dt, orbSpeed)
            f.touchA = if (s.touchOn) s.touchFrom + (1 - s.touchFrom) * easeOut(s.fingerAge / .12)
                else s.touchFrom * (1 - easeOut(s.releaseAge / .22))
            if (!s.touchOn) {
                f.touchX += (201 - f.touchX) * (1 - exp(-18 * dt))
                f.touchY += (416 - f.touchY) * (1 - exp(-18 * dt))
            }
            f.pillPress = if (s.pressPill) s.pillFrom + (1 - s.pillFrom) * easeOut(s.pillAge / .12)
                else spring(body(s, "pillPress"), 0.0, 280.0, 23.0, dt)
            f.refreshPress = if (s.pressRefresh) s.refreshFrom + (1 - s.refreshFrom) * easeOut(s.refreshAge / .12)
                else spring(body(s, "refreshPress"), 0.0, 280.0, 23.0, dt)
            if (s.pressPill) put(body(s, "pillPress"), f.pillPress)
            if (s.pressRefresh) put(body(s, "refreshPress"), f.refreshPress)
            f.pillPress = clamp(f.pillPress)
            f.refreshPress = clamp(f.refreshPress)
            for (i in 0..3) {
                s.nodeVelocity[i] += ((1 - f.nodeScale[i]) * 420 - 20 * s.nodeVelocity[i]) * dt
                f.nodeScale[i] += s.nodeVelocity[i] * dt
            }
            f.flash = if (s.reduced) 0.0 else s.flashPower * (1 - easeOut(s.flashAge / if (s.flashPower == 1.0) .42 else .3))
            f.rimGlint = if (s.reduced) 0.0 else s.glintPower * easeOut(s.glintAge / .3) *
                (1 - easeOut((s.glintAge - .3) / .5))
            if (s.mode == "refresh" && s.impactDone) f.rimGlint = max(f.rimGlint,
                .8 * contract.periods[f.period].value / 300 * (1 - easeOut(s.glintAge / .25)))
            if (s.mode == "refresh" && s.age < .7) f.rimGlint += .5 * easeOut((s.age - .34) / .08) *
                (1 - easeOut((s.age - .42) / .28))
            f.ringA = if (s.reduced) 0.0 else s.ringPower * (1 - easeOut((s.ringAge - .288) / .432))
            f.ringR = if (s.ringPower > 0) 300 * easeOut(s.ringAge / .72) *
                (1 - easeOut((s.ringAge - .72) / .3)) else 0.0
            if (s.ringAge > .72) {
                f.ringX += (f.orbX - f.ringX) * (1 - exp(-18 * dt))
                f.ringY += (f.orbY - f.ringY) * (1 - exp(-18 * dt))
            }
        }
        s.restWait = if (isRest(s) && s.autoPlay && s.paused && !s.contact) s.restWait + dt else 0.0
        if (s.mode == "rest" && !s.finger && !s.settling && s.queue != null) {
            val action = s.queue!!
            val user = s.queueUser
            s.queue = null
            begin(s, action, user)
        }
    }

    fun step(s: GlassPeakNativeState, dt: Double) {
        var remaining = clamp(dt, 0.0, .05)
        while (remaining > 1e-9) {
            var slice = min(fixedDt, remaining)
            if (s.mode == "refresh") {
                val boundary = if (!s.impactDone) impact
                    else if (s.age < impact + hitStop - 1e-9) impact + hitStop
                    else if (!s.ignitionDone) ignition + hitStop else 0.0
                if (boundary > s.age + 1e-9) slice = min(slice, boundary - s.age)
            } else if (s.mode == "morph" && !s.arrivalDone && s.age < .6 - 1e-9) {
                slice = min(slice, .6 - s.age)
            }
            integrate(s, slice)
            remaining -= slice
        }
    }

    fun toFrame(s: GlassPeakNativeState): GlassPeakNativeFrame {
        val f = s.f.copyDeep()
        f.t = if (s.reduced) 0.0 else if (s.autoPlay && !s.paused) s.loop else s.time
        if (!s.reduced && s.mode == "refresh" && s.impactDone && s.age <= impact + hitStop + 1e-9) f.t = s.impactClock
        if (!s.reduced) {
            val phase = tau * f.t / contract.loop
            f.tube += .04 * sin(phase * 2) * s.idleLife
            f.poolHump = clamp(f.poolHump + .05 * sin(phase * 3) * s.idleLife)
            f.orbGain = clamp(f.orbGain + .06 * sin(phase) * s.idleLife, 0.0, 2.0)
        }
        if (s.closing > 0) {
            val target = rest[0]
            val blend = easeOut(s.closing)
            val scalarKeys = arrayOf(
                "lit", "tip", "tube", "orbX", "orbY", "orbR", "orbGain", "poolLevel",
                "poolHumpX", "poolHump", "poolSlope", "poolGain", "poolStir", "beam",
                "beamReach", "ringX", "ringY", "ringR", "ringA", "flash", "rimGlint",
                "tiltX", "tiltY", "cardScale", "value", "numberAlpha", "numberScale",
                "numberGlow", "periodMix", "statusMix", "spin", "pillPress",
                "refreshPress", "chevron", "touchX", "touchY", "touchA",
            )
            for (key in scalarKeys) {
                val current = f[key]
                val source = if (key == "spin" || key == "chevron") current - tau * (current / tau).roundToInt() else current
                f[key] = source + (target[key] - source) * blend
            }
            val arrays = arrayOf(
                f.ys to target.ys, f.nodeX to target.nodeX, f.nodeY to target.nodeY,
                f.nodeLit to target.nodeLit, f.nodeScale to target.nodeScale,
                f.drop to target.drop, f.nodeBottom to target.nodeBottom,
                f.splashX to target.splashX, f.splashY to target.splashY, f.splashR to target.splashR,
            )
            for ((current, resting) in arrays) {
                for (i in current.indices) current[i] += (resting[i] - current[i]) * blend
            }
            if (s.closing >= 1) { f.t = 0.0
            f.presented = 1.0 }
        }
        return f
    }

    private val demoActions = arrayOf(
        .7 to "refresh-in", .82 to "refresh-out", 4.6 to "finger-down",
        7.45 to "finger-up", 8.0 to "period-in", 8.12 to "period-out",
        9.4 to "period-in", 9.52 to "period-out", 10.8 to "period-in",
        10.92 to "period-out",
    )

    private fun scriptedU(t: Double): Double {
        val nodes = contract.periods[0].nodes
        return when {
            t < 4.75 -> nodes[2]
            t < 5.55 -> nodes[2] + (nodes[1] - nodes[2]) * easeMove((t - 4.75) / .8)
            t < 5.7 -> nodes[1]
            t < 6.75 -> nodes[1] + (nodes[3] - nodes[1]) * easeMove((t - 5.7) / 1.05)
            t < 7.35 -> nodes[3] + (nodes[2] - nodes[3]) * easeMove((t - 6.75) / .6)
            else -> nodes[2]
        }
    }

    private fun dispatch(s: GlassPeakNativeState, index: Int) {
        when (demoActions[index].second) {
            "refresh-in" -> press(s, GlassPeakAction.Refresh, true, false)
            "refresh-out" -> {
                press(s, GlassPeakAction.Refresh, false, false)
                request(s, GlassPeakAction.Refresh, false)
            }
            "period-in" -> press(s, GlassPeakAction.Period, true, false)
            "period-out" -> {
                press(s, GlassPeakAction.Period, false, false)
                request(s, GlassPeakAction.Period, false)
            }
            "finger-down" -> {
                val u = contract.periods[0].nodes[2]
                fingerDown(s, contract.xOf(u), contract.yOf(contract.heightAt(s.f.ys, u)), false)
            }
            else -> fingerUp(s, 0.0, false)
        }
    }

    fun stepDemo(s: GlassPeakNativeState, dt: Double) {
        var remaining = clamp(dt, 0.0, .05)
        if (s.reduced || !s.autoPlay) {
            step(s, remaining)
            return
        }
        if (s.paused) {
            step(s, remaining)
            if ((s.restWait >= 3.5 || s.returning) && isRest(s) && !s.contact) {
                returnHome(s)
                if (isRest(s) && s.f.period == 0 && abs(s.orbU - contract.periods[0].nodes[2]) < .00001) resetLoop(s)
            }
            return
        }
        while (remaining > 1e-9) {
            if (s.demoEvent < demoActions.size && s.loop >= demoActions[s.demoEvent].first - 1e-9) {
                dispatch(s, s.demoEvent)
                s.demoEvent++
            }
            val next = if (s.demoEvent < demoActions.size) demoActions[s.demoEvent].first else contract.loop
            val h = minOf(fixedDt, remaining, max(1e-9, next - s.loop), contract.loop - s.loop)
            val nextClock = s.loop + h
            if (s.finger && !s.fingerUser) {
                val u = scriptedU(nextClock)
                val velocity = (contract.xOf(u) - contract.xOf(scriptedU(s.loop))) / max(h, 1e-9)
                fingerMove(s, contract.xOf(u), contract.yOf(contract.heightAt(s.f.ys, u)), velocity)
            }
            step(s, h)
            s.loop = nextClock
            if (s.loop >= 12.5) closeLoop(s, min(1.0, (s.loop - 12.5) / .5))
            remaining -= h
            if (s.loop >= contract.loop - 1e-9) {
                s.cycles++
                resetLoop(s)
            }
        }
        if (s.demoEvent < demoActions.size && s.loop >= demoActions[s.demoEvent].first - 1e-9) {
            dispatch(s, s.demoEvent)
            s.demoEvent++
        }
    }
}

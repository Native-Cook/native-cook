package com.ngocdevv.nativecook.blocks.pickers.owlalarmruler

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tanh

/**
 * Owl Alarm Ruler — the direct Kotlin translation of the React Native motion reference.
 *
 * All visual values are doubles and every auto-play frame is a pure function of loop time.
 * The live engine is immutable: callers replace their value with the returned [Engine.copy]
 * snapshot just as the React Native worklet replaces its object with a spread copy.
 */
object OwlAlarmRulerMotion {
    const val LOOP: Double = 10.0
    /** After the last touch, wait this long before easing home and restarting the loop. */
    const val RESUME_AFTER: Double = 2.0

    const val PROJECTION: Double = 0.385

    object GLIDE {
        const val min: Double = 0.25
        const val max: Double = 1.2
        const val still: Double = 0.28
    }

    object RETURN {
        const val base: Double = 0.55
        const val perTick: Double = 0.012
        const val max: Double = 1.3
    }

    object FILTER {
        const val omega: Double = 9.0
        const val zeta: Double = 0.55
    }

    object LEAN {
        const val max: Double = 5.5
        const val soft: Double = 30.0
    }

    object EYES {
        const val x: Double = 5.0
        const val soft: Double = 26.0
        const val attention: Double = 3.0
    }

    object NEEDLE {
        const val max: Double = 7.0
        const val soft: Double = 32.0
    }

    const val ATTENTION_TAU: Double = 0.16

    object SUNDIAL {
        const val amp: Double = 9.0
    }

    object FLIP {
        const val omega: Double = 17.0
        const val zeta: Double = 0.68
    }

    const val RISER: Double = 0.36

    object PRESS {
        const val scale: Double = 0.97
        const val `in`: Double = 0.1
        const val out: Double = 0.2
    }

    const val LABEL_GAP: Double = 0.09
    const val HAPTIC_GAP: Double = 0.045

    object STARTLE {
        const val crouch: Double = 0.1
        const val air: Double = 0.4
        const val height: Double = 30.0
        const val crouchSy: Double = 0.07
        const val crouchSx: Double = 0.05
        const val stretch: Double = 0.07
        const val rattleDeg: Double = 4.0
        const val rattleHz: Double = 14.0
        const val landSquash: Double = 0.08
        const val landDecay: Double = 5.0
        const val landFreq: Double = 18.0
        const val pupil: Double = 0.28
        const val blinkAt: Double = 0.98
        const val shadowScale: Double = 0.3
        const val shadowFade: Double = 0.45
        const val arrow: Double = 6.0
        const val end: Double = 1.15
    }

    object BLINK {
        const val close: Double = 0.07
        const val hold: Double = 0.03
        const val open: Double = 0.1
        const val period: Double = 3.9
        const val jitter: Double = 1.4
    }

    data class GlidePlan(val target: Double, val dur: Double)
    data class ScriptPosition(val p: Double, val v: Double)
    data class ScriptPiece(
        val a: Double,
        val b: Double,
        val p0: Double,
        val p1: Double,
        val m0: Double,
        val m1: Double,
    )
    data class Crossing(val t: Double, val dir: Double)
    data class Frame(
        val pos: Double,
        val vel: Double,
        val attention: Double,
        val blink: Double,
        val startle: Double,
        val press: Double,
        val flip: Double,
    )
    data class StartlePose(
        val y: Double,
        val sx: Double,
        val sy: Double,
        val rot: Double,
        val pupil: Double,
        val blink: Double,
        val lift: Double,
        val arrow: Double,
    )
    data class Pose(
        val lean: Double,
        val jumpY: Double,
        val sx: Double,
        val sy: Double,
        val pupilDX: Double,
        val pupilDY: Double,
        val pupilScale: Double,
        val blink: Double,
        val needle: Double,
        val lift: Double,
        val arrow: Double,
        val sundial: Double,
    )
    data class Wheels(val ones: Double, val tens: Double, val hour: Double)
    data class GlideState(val p: Double, val v: Double, val done: Boolean)
    data class FilterState(val x: Double, val v: Double)
    data class StepResult(val e: Engine, val f: Frame, val haptic: Boolean)

    /** One clock shared by every frame, gesture, and button interaction. */
    fun clockNow(): Double = System.nanoTime().toDouble() / 1_000_000_000.0

    fun clamp01(v: Double): Double = if (v < 0.0) 0.0 else if (v > 1.0) 1.0 else v

    fun seg(t: Double, a: Double, b: Double): Double = clamp01((t - a) / (b - a))

    fun lerp(a: Double, b: Double, p: Double): Double = a + (b - a) * p

    fun mod(a: Double, n: Double): Double = ((a % n) + n) % n

    /** JavaScript Math.round semantics: floor(x + 0.5), including negative inputs. */
    private fun jsRound(value: Double): Double = floor(value + 0.5)

    /** Solve the curve's x coordinate before sampling y; shared by all native ports. */
    private fun bezier(p: Double, x1: Double, y1: Double, x2: Double, y2: Double): Double {
        val x = clamp01(p)
        if (x == 0.0 || x == 1.0) return x
        var lo = 0.0
        var hi = 1.0
        repeat(18) {
            val m = (lo + hi) / 2.0
            val u = 1.0 - m
            val at = 3.0 * u * u * m * x1 + 3.0 * u * m * m * x2 + m * m * m
            if (at < x) lo = m else hi = m
        }
        val m = (lo + hi) / 2.0
        val u = 1.0 - m
        return 3.0 * u * u * m * y1 + 3.0 * u * m * m * y2 + m * m * m
    }

    /** Strong ease-out: cubic-bezier(0.23, 1, 0.32, 1). */
    fun easeOut(p: Double): Double = bezier(p, 0.23, 1.0, 0.32, 1.0)

    /** On-screen movement: cubic-bezier(0.77, 0, 0.175, 1). */
    fun easeInOut(p: Double): Double = bezier(p, 0.77, 0.0, 0.175, 1.0)

    fun smoothstep(p: Double): Double {
        val x = clamp01(p)
        return x * x * (3.0 - 2.0 * x)
    }

    /** Cubic Hermite from p0 to p1 with end tangents already multiplied by duration. */
    fun hermite(p0: Double, p1: Double, m0: Double, m1: Double, u: Double): Double {
        val u2 = u * u
        val u3 = u2 * u
        return (2.0 * u3 - 3.0 * u2 + 1.0) * p0 +
            (u3 - 2.0 * u2 + u) * m0 +
            (-2.0 * u3 + 3.0 * u2) * p1 +
            (u3 - u2) * m1
    }

    /** d/du of [hermite]. */
    fun hermiteSlope(p0: Double, p1: Double, m0: Double, m1: Double, u: Double): Double {
        val u2 = u * u
        return (6.0 * u2 - 6.0 * u) * p0 +
            (3.0 * u2 - 4.0 * u + 1.0) * m0 +
            (-6.0 * u2 + 6.0 * u) * p1 +
            (3.0 * u2 - 2.0 * u) * m1
    }

    /** A damped spring from 0 to 1, tau seconds after it starts. */
    fun spring(tau: Double, omega: Double, zeta: Double): Double {
        if (tau <= 0.0) return 0.0
        val wd = omega * sqrt(1.0 - zeta * zeta)
        val env = exp(-zeta * omega * tau)
        if (env < 1e-4) return 1.0
        return 1.0 - env * (cos(wd * tau) + ((zeta * omega) / wd) * sin(wd * tau))
    }

    fun hash(i: Double): Double {
        val s = sin(i * 12.9898 + 78.233) * 43758.5453
        return s - floor(s)
    }

    /** Where a release at pos with velocity vel lands, and how long the glide takes. */
    fun glidePlan(pos: Double, vel: Double): GlidePlan {
        val target = jsRound(pos + vel * PROJECTION)
        val dist = target - pos
        var dur = GLIDE.still
        if (abs(vel) > 0.5) dur = min(GLIDE.max, max(GLIDE.min, (3.0 * abs(dist)) / abs(vel)))
        return GlidePlan(target, dur)
    }

    private data class Drag(val a: Double, val b: Double, val from: Double, val to: Double, val v: Double)

    private val DRAGS = listOf(
        Drag(a = 1.3, b = 1.85, from = 0.0, to = 6.0, v = 15.6),
        Drag(a = 3.45, b = 3.9, from = 12.0, to = 4.0, v = -52.0),
        Drag(a = 5.55, b = 6.45, from = -16.0, to = -3.0, v = 8.0),
    )

    private fun buildScript(): List<ScriptPiece> {
        val out = mutableListOf<ScriptPiece>()
        for (d in DRAGS) {
            val t = d.b - d.a
            out += ScriptPiece(a = d.a, b = d.b, p0 = d.from, p1 = d.to, m0 = 0.0, m1 = d.v * t)
            val g = glidePlan(d.to, d.v)
            out += ScriptPiece(a = d.b, b = d.b + g.dur, p0 = d.to, p1 = g.target, m0 = d.v * g.dur, m1 = 0.0)
        }
        return out
    }

    val SCRIPT: List<ScriptPiece> = buildScript()

    const val PRESS_AT: Double = 7.95
    const val RELEASE_AT: Double = 8.05
    val BLINKS_AT: List<Double> = listOf(0.75, 5.2)
    val ATTENTION_IN: List<Double> = listOf(1.3, 1.6)
    val ATTENTION_OUT: List<Double> = listOf(7.6, 7.95)

    /** Ruler position and velocity (ticks, ticks/s) at loop time t. */
    fun scriptPos(t: Double): ScriptPosition {
        val s = mod(t, LOOP)
        var p = 0.0
        for (k in SCRIPT) {
            if (s < k.a) return ScriptPosition(p, 0.0)
            if (s < k.b) {
                val duration = k.b - k.a
                val u = (s - k.a) / duration
                return ScriptPosition(
                    p = hermite(k.p0, k.p1, k.m0, k.m1, u),
                    v = hermiteSlope(k.p0, k.p1, k.m0, k.m1, u) / duration,
                )
            }
            p = k.p1
        }
        return ScriptPosition(p, 0.0)
    }

    private val KERNEL_DT: Double = 1.0 / 60.0
    private const val KERNEL_N: Int = 61

    private fun buildKernel(): List<Double> {
        val omega = FILTER.omega
        val zeta = FILTER.zeta
        val wd = omega * sqrt(1.0 - zeta * zeta)
        val values = mutableListOf<Double>()
        var sum = 0.0
        for (n in 0 until KERNEL_N) {
            val tau = n.toDouble() * KERNEL_DT
            val h = ((omega * omega) / wd) * exp(-zeta * omega * tau) * sin(wd * tau)
            values += h
            sum += h
        }
        return values.map { it / sum }
    }

    val KERNEL: List<Double> = buildKernel()

    /** The velocity spring's output at loop time t (ticks/s). */
    fun scriptVel(t: Double): Double {
        var x = 0.0
        for (n in KERNEL.indices) x += KERNEL[n] * scriptPos(t - n.toDouble() * KERNEL_DT).v
        return x
    }

    /** Meridiem of a ruler position: 0 = PM side of the pin's day, flips every 12 h. */
    fun meridiem(pos: Double): Double {
        val m = mod(
            OwlAlarmRulerTheme.REST_MINUTES / OwlAlarmRulerTheme.STEP_MINUTES + jsRound(pos),
            OwlAlarmRulerTheme.TICKS_PER_DAY,
        )
        return if (m < OwlAlarmRulerTheme.TICKS_PER_DAY / 2.0) 1.0 else 0.0
    }

    private fun buildCrossings(): List<Crossing> {
        val out = mutableListOf<Crossing>()
        var prev = jsRound(scriptPos(0.0).p)
        for (i in 1..(LOOP * 1000.0).toInt()) {
            val t = i.toDouble() / 1000.0
            val r = jsRound(scriptPos(t).p)
            if (r != prev) {
                if (meridiem(r) != meridiem(prev)) out += Crossing(t, if (r > prev) 1.0 else -1.0)
                prev = r
            }
        }
        return out
    }

    val CROSSINGS: List<Crossing> = buildCrossings()

    /** The AM/PM card's angle in degrees: 0 = PM face, +/-180 = AM face. */
    fun scriptFlip(t: Double): Double {
        var a = 0.0
        for (c in CROSSINGS) if (t >= c.t) a += c.dir * 180.0 * spring(t - c.t, FLIP.omega, FLIP.zeta)
        return a
    }

    /** One blink, tau seconds after it starts: 0 open -> 1 closed -> 0. */
    fun blinkAt(tau: Double): Double {
        if (tau <= 0.0) return 0.0
        if (tau < BLINK.close) return smoothstep(tau / BLINK.close)
        if (tau < BLINK.close + BLINK.hold) return 1.0
        return 1.0 - easeOut(seg(tau, BLINK.close + BLINK.hold, BLINK.close + BLINK.hold + BLINK.open))
    }

    /** Seeded blinks under a finger: one every period +/- jitter, from tau seconds. */
    fun liveBlink(tau: Double): Double {
        val k = floor(tau / BLINK.period)
        val start = k * BLINK.period + 1.0 + hash(k) * BLINK.jitter
        return blinkAt(tau - start)
    }

    fun scriptPress(t: Double): Double = if (t < RELEASE_AT) {
        easeOut(seg(t, PRESS_AT, PRESS_AT + PRESS.`in`))
    } else {
        1.0 - easeOut(seg(t, RELEASE_AT, RELEASE_AT + PRESS.out))
    }

    fun scriptAttention(t: Double): Double =
        easeOut(seg(t, ATTENTION_IN[0], ATTENTION_IN[1])) *
            (1.0 - easeInOut(seg(t, ATTENTION_OUT[0], ATTENTION_OUT[1])))

    val REST_FRAME = Frame(pos = 0.0, vel = 0.0, attention = 0.0, blink = 0.0, startle = -1.0, press = 0.0, flip = 0.0)

    fun frameAt(t: Double): Frame {
        val s = mod(t, LOOP)
        val startle = if (s >= RELEASE_AT && s < RELEASE_AT + STARTLE.end) s - RELEASE_AT else -1.0
        var blink = 0.0
        for (blinkStart in BLINKS_AT) blink = max(blink, blinkAt(s - blinkStart))
        return Frame(
            pos = scriptPos(s).p,
            vel = scriptVel(s),
            attention = scriptAttention(s),
            blink = blink,
            startle = startle,
            press = scriptPress(s),
            flip = scriptFlip(s),
        )
    }

    /** The startle's pose, tau seconds after the button's release. Distances are in pt. */
    fun startlePose(tau: Double): StartlePose {
        if (tau < 0.0 || tau >= STARTLE.end) {
            return StartlePose(y = 0.0, sx = 1.0, sy = 1.0, rot = 0.0, pupil = 1.0, blink = 0.0, lift = 0.0, arrow = 0.0)
        }
        var y = 0.0
        var sx = 1.0
        var sy = 1.0
        var rot = 0.0
        if (tau < STARTLE.crouch) {
            val c = easeOut(tau / STARTLE.crouch)
            sy = 1.0 - STARTLE.crouchSy * c
            sx = 1.0 + STARTLE.crouchSx * c
        } else if (tau < STARTLE.crouch + STARTLE.air) {
            val u = (tau - STARTLE.crouch) / STARTLE.air
            y = -STARTLE.height * 4.0 * u * (1.0 - u)
            // Release from the crouch into a stretch, normal at the apex, a little stretch falling.
            val out = easeOut(seg(u, 0.0, 0.18))
            val rise = if (u < 0.5) STARTLE.stretch * (1.0 - 2.0 * u) else 0.4 * STARTLE.stretch * (2.0 * u - 1.0)
            sy = lerp(1.0 - STARTLE.crouchSy, 1.0 + rise, out)
            sx = lerp(1.0 + STARTLE.crouchSx, 1.0 - rise * 0.6, out)
            rot = STARTLE.rattleDeg * sin(2.0 * PI * STARTLE.rattleHz * (tau - STARTLE.crouch)) * sin(PI * u)
        } else {
            val l = tau - STARTLE.crouch - STARTLE.air
            val sq = STARTLE.landSquash * exp(-STARTLE.landDecay * l) * cos(STARTLE.landFreq * l)
            sy = 1.0 - sq
            sx = 1.0 + sq * 0.7
        }
        val shock = easeOut(seg(tau, 0.04, 0.16)) * (1.0 - easeInOut(seg(tau, 0.6, 0.95)))
        val arrow = STARTLE.arrow * (easeOut(seg(tau, 0.0, 0.14)) - easeInOut(seg(tau, 0.14, 0.5)))
        return StartlePose(
            y = y,
            sx = sx,
            sy = sy,
            rot = rot,
            pupil = 1.0 - STARTLE.pupil * shock,
            blink = blinkAt(tau - STARTLE.blinkAt),
            lift = -y / STARTLE.height,
            arrow = arrow,
        )
    }

    /** Everything the views need, from a frame. still is Reduce Motion. */
    fun poseOf(f: Frame, still: Boolean): Pose {
        val k = if (still) 0.0 else 1.0
        val st = startlePose(if (still) -1.0 else f.startle)
        return Pose(
            lean = -LEAN.max * tanh(f.vel / LEAN.soft) * k + st.rot,
            jumpY = st.y,
            sx = st.sx,
            sy = st.sy,
            pupilDX = -EYES.x * tanh(f.vel / EYES.soft) * k,
            pupilDY = EYES.attention * f.attention * k,
            pupilScale = st.pupil,
            blink = if (still) 0.0 else max(f.blink, st.blink),
            needle = -NEEDLE.max * tanh(f.vel / NEEDLE.soft) * k,
            lift = st.lift,
            arrow = st.arrow,
            sundial = if (still) 0.0 else SUNDIAL.amp * sin((2.0 * PI * f.pos) / OwlAlarmRulerTheme.TICKS_PER_DAY),
        )
    }

    /** Continuous odometer wheel values at a ruler position. */
    fun wheels(pos: Double): Wheels {
        val k = floor(pos)
        val fr = pos - k
        val g = k + smoothstep((fr - 0.5) / RISER + 0.5)
        val m = mod(OwlAlarmRulerTheme.REST_MINUTES / OwlAlarmRulerTheme.STEP_MINUTES + g, OwlAlarmRulerTheme.TICKS_PER_DAY)
        val mi = floor(m)
        val mf = m - mi
        val ones = m
        val tens = floor(mi / 2.0) + if (mi % 2.0 == 1.0) mf else 0.0
        val hour = floor(mi / 12.0) + if (mi % 12.0 == 11.0) mf else 0.0
        return Wheels(ones = mod(ones, 2.0), tens = mod(tens, 6.0), hour = mod(hour, 12.0))
    }

    /** The label at the nearest tick, for example 1:05 PM. */
    fun timeLabel(pos: Double): String {
        val m = mod(
            OwlAlarmRulerTheme.REST_MINUTES / OwlAlarmRulerTheme.STEP_MINUTES + jsRound(pos),
            OwlAlarmRulerTheme.TICKS_PER_DAY,
        ) * OwlAlarmRulerTheme.STEP_MINUTES
        val h24 = floor(m / 60.0).toInt()
        val mm = (m % 60.0).toInt()
        val h = if (h24 % 12 == 0) 12 else h24 % 12
        return "$h:${if (mm < 10) "0" else ""}$mm ${if (h24 < 12) "AM" else "PM"}"
    }

    private val TICK_D = listOf(0.0, 1.0, 2.0, 3.0, 4.0, 5.0)
    private val TICK_H = listOf(59.0 / 3.0, 50.0 / 3.0, 36.0 / 3.0, 24.0 / 3.0, 16.0 / 3.0, 15.0 / 3.0)

    private fun buildSlopes(): List<Double> {
        val n = TICK_H.size
        val d = mutableListOf<Double>()
        for (i in 0 until n - 1) d += TICK_H[i + 1] - TICK_H[i]
        val slopes = mutableListOf(d[0])
        for (i in 1 until n - 1) {
            slopes += if (d[i - 1] * d[i] <= 0.0) 0.0 else (2.0 * d[i - 1] * d[i]) / (d[i - 1] + d[i])
        }
        slopes += 0.0
        return slopes
    }

    val TICK_SLOPE: List<Double> = buildSlopes()

    /** Tick height (pt) by distance from the needle. */
    fun tickHeight(dist: Double): Double {
        val d = abs(dist)
        if (d >= TICK_D[TICK_D.lastIndex]) return TICK_H[TICK_H.lastIndex]
        val i = floor(d).toInt()
        val u = d - i.toDouble()
        return hermite(TICK_H[i], TICK_H[i + 1], TICK_SLOPE[i], TICK_SLOPE[i + 1], u)
    }

    /** Tick ink: dark through two ticks, then linear to light grey at nine. */
    fun tickShade(dist: Double): Double = clamp01((abs(dist) - 2.0) / 7.0)

    const val AUTO: Double = 0.0
    const val LIVE: Double = 1.0
    const val HOME: Double = 2.0

    data class Engine(
        val mode: Double,
        val autoStart: Double,
        val autoPlay: Boolean,
        val still: Boolean,
        val pos: Double,
        val dragging: Boolean,
        val dragFrom: Double,
        val dragVel: Double,
        val glideAt: Double,
        val glideFrom: Double,
        val glideTo: Double,
        val glideV: Double,
        val glideDur: Double,
        val fx: Double,
        val fv: Double,
        val attention: Double,
        val lastTouch: Double,
        val liveSince: Double,
        val handoffT: Double,
        val startleAt: Double,
        val pressInAt: Double,
        val releaseAt: Double,
        val pressFrom: Double,
        val flipFrom: Double,
        val flipTo: Double,
        val flipAt: Double,
        val lastClock: Double,
    )

    fun createEngine(now: Double, autoPlay: Boolean, still: Boolean): Engine = Engine(
        mode = if (autoPlay && !still) AUTO else LIVE,
        autoStart = now,
        autoPlay = autoPlay,
        still = still,
        pos = 0.0,
        dragging = false,
        dragFrom = 0.0,
        dragVel = 0.0,
        glideAt = -1.0,
        glideFrom = 0.0,
        glideTo = 0.0,
        glideV = 0.0,
        glideDur = 1.0,
        fx = 0.0,
        fv = 0.0,
        attention = 0.0,
        lastTouch = -1e9,
        liveSince = now,
        handoffT = -1.0,
        startleAt = -1e9,
        pressInAt = -1e9,
        releaseAt = -1e9,
        pressFrom = 0.0,
        flipFrom = 0.0,
        flipTo = 0.0,
        flipAt = -1e9,
        lastClock = now,
    )

    private fun flipNow(e: Engine, now: Double): Double =
        lerp(e.flipFrom, e.flipTo, spring(now - e.flipAt, FLIP.omega, FLIP.zeta))

    private fun pressNow(e: Engine, now: Double): Double = if (e.releaseAt < e.pressInAt) {
        easeOut(seg(now - e.pressInAt, 0.0, PRESS.`in`))
    } else {
        e.pressFrom * (1.0 - easeOut(seg(now - e.releaseAt, 0.0, PRESS.out)))
    }

    /** Position and velocity of the ruler from a running glide. */
    private fun glideNow(e: Engine, now: Double): GlideState {
        val u = clamp01((now - e.glideAt) / e.glideDur)
        val m0 = e.glideV * e.glideDur
        return GlideState(
            p = hermite(e.glideFrom, e.glideTo, m0, 0.0, u),
            v = if (u >= 1.0) 0.0 else hermiteSlope(e.glideFrom, e.glideTo, m0, 0.0, u) / e.glideDur,
            done = u >= 1.0,
        )
    }

    /** Resting angle nearest angle that shows the requested face. */
    private fun faceAngle(angle: Double, face: Double): Double {
        val k = jsRound((angle - face * 180.0) / 360.0)
        return k * 360.0 + face * 180.0
    }

    /** The finger takes over: freeze whatever auto-play shows at this instant into live state. */
    fun takeOver(e: Engine, now: Double): Engine {
        if (e.mode != AUTO) return e.copy(mode = LIVE, lastTouch = now)
        val t = mod(now - e.autoStart, LOOP)
        val f = frameAt(t)
        val previous = scriptVel(t - 1.0 / 240.0)
        val inStartle = f.startle >= 0.0
        val flip = mod(f.flip + 180.0, 360.0) - 180.0
        // A scripted drag or glide in progress carries on as a real glide to its tick.
        val s = scriptPos(t)
        val moving = abs(s.v) > 0.01 || abs(s.p - jsRound(s.p)) > 1e-3
        val plan = glidePlan(s.p, s.v)
        return e.copy(
            mode = LIVE,
            pos = f.pos,
            glideAt = if (moving) now else -1.0,
            glideFrom = s.p,
            glideTo = plan.target,
            glideV = s.v,
            glideDur = plan.dur,
            fx = f.vel,
            fv = (f.vel - previous) * 240.0,
            attention = f.attention,
            lastTouch = now,
            liveSince = now,
            handoffT = t,
            startleAt = if (inStartle) now - f.startle else -1e9,
            pressInAt = -1e9,
            releaseAt = now,
            pressFrom = f.press,
            flipFrom = flip,
            flipTo = faceAngle(flip, meridiem(f.pos)),
            flipAt = now,
        )
    }

    /** Touch down on the ruler: catch a glide where it is. */
    fun rulerDown(e0: Engine, now: Double): Engine {
        val e = takeOver(e0, now)
        val pos = if (e.glideAt >= 0.0) glideNow(e, now).p else e.pos
        return e.copy(mode = LIVE, pos = pos, glideAt = -1.0, dragging = false, dragFrom = pos, dragVel = 0.0, lastTouch = now)
    }

    fun dragStart(e: Engine, now: Double): Engine =
        e.copy(dragging = true, dragFrom = e.pos, dragVel = 0.0, lastTouch = now)

    /** dx and vx are finger translation/velocity in pt/s; left means later. */
    fun dragMove(e: Engine, now: Double, dx: Double, vx: Double, spacing: Double): Engine {
        if (!e.dragging) return e
        return e.copy(pos = e.dragFrom - dx / spacing, dragVel = -vx / spacing, lastTouch = now)
    }

    /** Let go (or a touch that never dragged): glide to the projected tick. */
    fun release(e: Engine, now: Double, vx: Double, spacing: Double): Engine {
        val v = if (e.dragging) -vx / spacing else 0.0
        val plan = glidePlan(e.pos, if (e.still) 0.0 else v)
        return e.copy(
            dragging = false,
            dragVel = 0.0,
            glideAt = now,
            glideFrom = e.pos,
            glideTo = plan.target,
            glideV = if (e.still) 0.0 else v,
            glideDur = plan.dur,
            lastTouch = now,
        )
    }

    /** Accessibility: one tick later (+1) or earlier (-1). */
    fun stepBy(e0: Engine, now: Double, dir: Double): Engine {
        val e = takeOver(e0, now)
        val from = if (e.glideAt >= 0.0) glideNow(e, now).p else e.pos
        return e.copy(
            mode = LIVE,
            pos = from,
            glideAt = now,
            glideFrom = from,
            glideTo = jsRound(from) + dir,
            glideV = 0.0,
            glideDur = GLIDE.still,
            lastTouch = now,
        )
    }

    fun pressIn(e0: Engine, now: Double): Engine {
        val e = takeOver(e0, now)
        return e.copy(pressInAt = now, lastTouch = now)
    }

    fun pressOut(e: Engine, now: Double, commit: Boolean): Engine {
        val from = pressNow(e, now)
        return e.copy(
            releaseAt = now,
            pressFrom = from,
            startleAt = if (commit && !e.still) now else e.startleAt,
            lastTouch = now,
        )
    }

    fun setAutoPlay(e: Engine, autoPlay: Boolean, still: Boolean, now: Double): Engine {
        // First call after mount, or Reduce Motion turned on: start from the rest frame.
        if (e.lastClock == 0.0 || (still && !e.still)) return createEngine(now, autoPlay, still)
        if (autoPlay && !still) {
            if (e.mode == AUTO && e.autoPlay && !e.still) return e
            return e.copy(autoPlay = autoPlay, still = still, mode = LIVE, lastTouch = now - RESUME_AFTER)
        }
        if (e.mode == AUTO) return takeOver(e.copy(autoPlay = autoPlay, still = still), now).copy(lastTouch = -1e9)
        return e.copy(autoPlay = autoPlay, still = still)
    }

    /** Exact step of the velocity spring over dt with input u held constant. */
    private fun filterStep(x: Double, v: Double, u: Double, dt: Double): FilterState {
        val omega = FILTER.omega
        val zeta = FILTER.zeta
        val wd = omega * sqrt(1.0 - zeta * zeta)
        val a = zeta * omega
        val e0 = x - u
        val env = exp(-a * dt)
        val c = cos(wd * dt)
        val s = sin(wd * dt)
        val b = (v + a * e0) / wd
        val e1 = env * (e0 * c + b * s)
        val v1 = env * (-a * (e0 * c + b * s) + (-e0 * wd * s + b * wd * c))
        return FilterState(x = u + e1, v = v1)
    }

    /** Advance the engine to now and return the frame to draw. */
    fun step(e0: Engine, now: Double): StepResult {
        if (e0.mode == AUTO) {
            val f = frameAt(now - e0.autoStart)
            return StepResult(e = e0.copy(lastClock = now), f = f, haptic = false)
        }
        var e = e0
        val dt = min(0.05, max(0.0, now - e.lastClock))

        // Ruler position and the velocity that drives the spring.
        var pos = e.pos
        var u = 0.0
        var moving = e.dragging
        if (e.dragging) {
            u = e.dragVel
        } else if (e.glideAt >= 0.0) {
            val g = glideNow(e, now)
            pos = g.p
            u = g.v
            moving = !g.done
            if (g.done) e = e.copy(glideAt = -1.0)
        }

        // Idle long enough: ease home, then hand back to auto-play.
        val startleLeft = e.startleAt + STARTLE.end - now
        if (e.mode == LIVE && e.autoPlay && !e.still && !e.dragging && !moving &&
            now - e.lastTouch >= RESUME_AFTER && startleLeft <= 0.0
        ) {
            val from = mod(pos + OwlAlarmRulerTheme.TICKS_PER_DAY / 2.0, OwlAlarmRulerTheme.TICKS_PER_DAY) -
                OwlAlarmRulerTheme.TICKS_PER_DAY / 2.0
            val dur = min(RETURN.max, RETURN.base + RETURN.perTick * abs(from))
            e = e.copy(mode = HOME, glideAt = now, glideFrom = from, glideTo = 0.0, glideV = 0.0, glideDur = dur)
            pos = from
            moving = true
        }

        // AM/PM: a crossing starts a flip from wherever the card is.
        val before = meridiem(e.pos)
        val after = meridiem(pos)
        if (before != after) {
            val dir = if (jsRound(pos) > jsRound(e.pos)) 1.0 else -1.0
            val cur = flipNow(e, now)
            e = e.copy(flipFrom = cur, flipTo = e.flipTo + dir * 180.0, flipAt = now)
        }

        val fs = filterStep(e.fx, e.fv, if (e.still) 0.0 else u, dt)
        val engaged = e.dragging || moving
        val target = if (engaged || now - e.lastTouch < 0.5) 1.0 else 0.0
        val attention = lerp(target, e.attention, exp(-dt / ATTENTION_TAU))

        // A blink in progress when the finger took over finishes; then seeded blinks.
        val since = now - e.liveSince
        var blink = liveBlink(since)
        if (e.handoffT >= 0.0 && since < 0.3) {
            for (blinkStart in BLINKS_AT) blink = max(blink, blinkAt(e.handoffT + since - blinkStart))
        }

        e = e.copy(pos = pos, fx = fs.x, fv = fs.v, attention = attention, lastClock = now)

        val startle = now - e.startleAt
        val f = Frame(
            pos = pos,
            vel = fs.x,
            attention = attention,
            blink = blink,
            startle = if (startle >= 0.0 && startle < STARTLE.end) startle else -1.0,
            press = pressNow(e, now),
            flip = flipNow(e, now),
        )

        // Home and settled: the loop restarts at its rest frame.
        if (e.mode == HOME && e.glideAt < 0.0 && abs(e.fx) < 0.05 && abs(e.fv) < 0.5 &&
            attention < 0.01 && abs(mod(f.flip + 180.0, 360.0) - 180.0) < 0.5 && f.press < 0.001
        ) {
            return StepResult(
                e = e.copy(mode = AUTO, autoStart = now, pos = 0.0, fx = 0.0, fv = 0.0, attention = 0.0),
                f = REST_FRAME,
                haptic = false,
            )
        }
        return StepResult(e = e, f = f, haptic = e.mode == LIVE && engaged)
    }
}

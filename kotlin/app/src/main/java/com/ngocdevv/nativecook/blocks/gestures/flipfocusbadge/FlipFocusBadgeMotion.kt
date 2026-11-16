package com.ngocdevv.nativecook.blocks.gestures.flipfocusbadge

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sign
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tanh

/** A spring as Reanimated names it: damping, stiffness, mass. */
internal class FlipFocusBadgeSpring(val damping: Double, val stiffness: Double, val mass: Double)

/**
 * Every motion value of Flip Focus Badge as a pure function of the loop time `t` (so the loop and a live frame agree), and the constants the finger uses.
 * Seconds, degrees, design pt. Port of RN `ffb-motion.ts`.
 *
 * The loop (LOOP 8.1 s):
 *   0 - 0.5 rest (the pin: "10", ring at its pin pose, satellite at 24 min, line at 40 %); 0.5 - 0.6 the badge presses to 1.03; 0.6 - 1.5 it turns to
 *   70 degrees under a finger; 1.5 - 1.9 hold at 70; 1.9 - 2.4 it lands on its back (the session starts, the hint cross-slides); 2.4 - 4.0 the session runs
 *   (shown fast: the clock 00:00 -> 36:00, the satellite 40 % -> 100 %, the line fills); 4.0 - 5.0 flip back: the numeral presses flat, is exchanged at
 *   edge-on (10 -> 11) and punches out; 4.05 - 4.7 the hour is reached: the line's highlight pass, the total rolls to 11h 00m while the line wipes to 0;
 *   4.8 - 5.8 hold on 11 hr; 5.8 - 7.0 rewind; 7.0 - 8.1 rest (identical to frame 0).
 */
internal object FlipFocusBadgeMotion {
    const val LOOP = 8.1
    const val POSTER_T = 1.7
    const val REST_T = 0.0

    fun clamp01(x: Double) = if (x < 0) 0.0 else if (x > 1) 1.0 else x
    fun mix(a: Double, b: Double, k: Double) = a + (b - a) * k
    fun seg(t: Double, a: Double, b: Double) = clamp01((t - a) / (b - a))
    fun smooth01(x: Double): Double {
        val k = clamp01(x)
        return k * k * (3 - 2 * k)
    }

    fun easeInOut(x: Double): Double {
        val k = clamp01(x)
        return if (k < 0.5) 4 * k * k * k else 1 - (-2 * k + 2).pow(3) / 2
    }

    /** the step response of a spring (response in seconds = 2 pi / w, damping ratio zeta), 0 -> 1; analytic, so frames are exact */
    fun springAt(t: Double, response: Double, zeta: Double): Double {
        if (t <= 0) return 0.0
        val w = (2 * PI) / response
        if (zeta >= 1) return 1 - exp(-w * t) * (1 + w * t)
        val wd = w * sqrt(1 - zeta * zeta)
        return 1 - exp(-zeta * w * t) * (cos(wd * t) + ((zeta * w) / wd) * sin(wd * t))
    }

    val SNAP = FlipFocusBadgeSpring(30.5, 322.3, 1.0)
    val GLIDE = FlipFocusBadgeSpring(22.1, 157.9, 1.0)
    val SETTLE = FlipFocusBadgeSpring(14.0, 110.0, 1.2)
    val TILT = FlipFocusBadgeSpring(16.0, 140.0, 1.0)
    val RISE = FlipFocusBadgeSpring(11.0, 160.0, 1.0)
    val POP = FlipFocusBadgeSpring(19.0, 195.0, 1.0)

    // (response, zeta) for the analytic timeline
    private const val PRESS_RESPONSE = 0.35
    private const val PRESS_ZETA = 0.85
    private const val LAND_RESPONSE = 0.6
    private const val LAND_ZETA = 0.8
    private const val FLIP_RESPONSE = 0.7
    private const val FLIP_ZETA = 0.82
    private const val RISE_RESPONSE = 0.497
    private const val RISE_ZETA = 0.435
    private const val POP_RESPONSE = 0.45
    private const val POP_ZETA = 0.68

    const val T_PRESS = 0.5
    const val T_TURN0 = 0.6
    const val T_TURN1 = 1.5
    const val T_LAND = 1.9
    const val T_SESS0 = 2.4
    const val T_SESS1 = 4.0
    const val T_BACK = 4.0
    const val T_REV0 = 5.8
    const val T_REV1 = 7.0
    const val T_HINT_IN0 = 2.0
    const val T_HINT_IN1 = 2.3
    const val T_HINT_OUT0 = 4.15
    const val T_HINT_OUT1 = 4.45
    const val YAW_PEEK = 70.0
    private const val PEEK_DRIFT = 3.0

    /** time since T_BACK at which the flip-back spring crosses 90 degrees of its 180 (the front's edge-on instant) */
    private fun crossTime(response: Double, zeta: Double, level: Double): Double {
        var lo = 0.0
        var hi = 2.0
        for (i in 0 until 40) {
            val mid = (lo + hi) / 2
            if (springAt(mid, response, zeta) < level) lo = mid else hi = mid
        }
        return (lo + hi) / 2
    }

    /** 10 -> 11 at yaw 270 */
    val T_SWAP1 = T_BACK + crossTime(FLIP_RESPONSE, FLIP_ZETA, 0.5)
    val T_HOUR = T_SWAP1 - 0.12
    private val T_WIPE = T_HOUR + 0.24
    private const val HOUR_ROLL = 0.5
    const val T_SWAP2 = T_REV0 + 0.6

    /** the plate's yaw, continuous (0 -> 70 -> 180 -> 360 -> 0) */
    fun plateYawAt(t: Double): Double {
        if (t < T_TURN0) return 0.0
        if (t < T_TURN1) return YAW_PEEK * easeOut((t - T_TURN0) / (T_TURN1 - T_TURN0), 2.2)
        if (t < T_LAND) return YAW_PEEK + PEEK_DRIFT * smooth01((t - T_TURN1) / (T_LAND - T_TURN1))
        if (t < T_BACK) return YAW_PEEK + PEEK_DRIFT + (180 - YAW_PEEK - PEEK_DRIFT) * springAt(t - T_LAND, LAND_RESPONSE, LAND_ZETA)
        if (t < T_REV0) return 180 + 180 * springAt(t - T_BACK, FLIP_RESPONSE, FLIP_ZETA)
        if (t < T_REV1) return 360 * (1 - easeInOut((t - T_REV0) / (T_REV1 - T_REV0)))
        return 0.0
    }

    fun easeOut(x: Double, power: Double) = 1 - (1 - clamp01(x)).pow(power)

    // the ring: a viscous joint between plate and ring, simulated once (second order, zeta 0.5, 1.2 Hz)
    const val JOINT_WN = 7.5
    const val JOINT_ZETA = 0.5
    const val JOINT_LAG = 45.0

    /** one semi-implicit step of the joint, in place: out[0] = theta, out[1] = omega */
    fun jointStep(theta: Double, omega: Double, target: Double, dt: Double, out: DoubleArray) {
        val a = JOINT_WN * JOINT_WN * (target - theta) - 2 * JOINT_ZETA * JOINT_WN * omega
        val w2 = omega + a * dt
        out[0] = theta + w2 * dt
        out[1] = w2
    }

    private val RING_N = Math.round(LOOP * 120).toInt()
    private val RING_TABLE: DoubleArray = run {
        var th = 0.0
        var om = 0.0
        val out = DoubleArray(RING_N + 1)
        val dt = 1.0 / 120
        val tmp = DoubleArray(2)
        for (loop in 0 until 4) {
            for (i in 0..RING_N) {
                val time = i * dt
                jointStep(th, om, plateYawAt(min(time, LOOP)), dt, tmp)
                th = tmp[0]
                om = tmp[1]
                if (loop == 3) out[i] = th
            }
        }
        // the loop's last sample equals its first, and the ring's rest is exactly the plate's
        val err = out[RING_N] - out[0]
        val o0 = out[0]
        for (i in 0..RING_N) out[i] -= o0 + (err * i) / RING_N
        out
    }

    /** the joint never lets the ring fall further than ~45 degrees behind (a soft limit), whatever the plate's speed */
    fun limitLag(plate: Double, ring: Double): Double {
        val lag = plate - ring
        return plate - JOINT_LAG * tanh(lag / JOINT_LAG)
    }

    fun ringYawAt(t: Double): Double {
        val x = (min(max(t, 0.0), LOOP) / LOOP) * RING_N
        val i = min(x.toInt(), RING_N - 1)
        val ring = RING_TABLE[i] + (RING_TABLE[i + 1] - RING_TABLE[i]) * (x - i)
        return limitLag(plateYawAt(t), ring)
    }

    const val REST_FILL = 0.4
    const val SESSION_SECS = 2160.0

    /** distance covered by a move at constant rate with `r`-long ramps at both ends, 0 -> 1 over k = 0 ... 1 */
    private fun trapezoid(k: Double, r: Double): Double {
        val x = clamp01(k)
        val total = 1 - r
        if (x < r) return (x * x) / (2 * r) / total
        if (x <= 1 - r) return (x - r / 2) / total
        val y = x - (1 - r)
        return (1 - 1.5 * r + y - (y * y) / (2 * r)) / total
    }

    private fun pulse(t: Double, c: Double, w: Double): Double {
        val x = (t - c) / w
        return exp(-x * x)
    }

    /** everything the screen shows, as a function of t; fills `o` in place */
    fun lookAt(tRaw: Double, o: FlipFocusBadgeLook = FlipFocusBadgeLook()): FlipFocusBadgeLook {
        val t = min(max(tRaw, 0.0), LOOP)
        val yaw = plateYawAt(t)
        // trackball: the badge leans back a little during the hold so the numerals' tops catch the light
        val pitch = if (t > 1.0 && t < 2.5) 10 * sin((PI * (t - 1.0)) / 1.5).pow(2) else 0.0
        // depth: press flat over the first half-turn back, punch out after the exchange
        var depth = 1.0
        if (t >= T_BACK && t < T_SWAP1) depth = 1 - 0.96 * easeInOut((t - T_BACK) / (T_SWAP1 - T_BACK))
        else if (t >= T_SWAP1 && t < T_REV0) depth = 1 - 0.96 * (1 - springAt(t - T_SWAP1, RISE_RESPONSE, RISE_ZETA))
        val value = if (t >= T_SWAP1 && t < T_SWAP2) 11.0 else 10.0
        // the session: the clock, the satellite and the line move together
        val runK = trapezoid(seg(t, T_SESS0, T_SESS1), 0.1)
        val clock = if (t >= T_BACK + 0.45) 0.0 else SESSION_SECS * runK
        var fill = REST_FILL + (1 - REST_FILL) * runK
        // the line restarts from 0 once the hour is reached, and refills to the pin's 40 % in the rewind
        if (t >= T_WIPE && t < T_WIPE + 0.4) fill = 1 - easeInOut((t - T_WIPE) / 0.4)
        else if (t >= T_WIPE + 0.4 && t < T_REV0 + 0.2) fill = 0.0
        else if (t >= T_REV0 + 0.2) fill = REST_FILL * easeInOut((t - T_REV0 - 0.2) / 0.9)
        // the satellite keeps going round: at the hour it starts the next lap from the same place
        val sat = if (t < T_WIPE) fill else if (t < T_REV0 + 0.2) 0.0 else fill
        val hint = max(smooth01((t - T_HINT_IN0) / (T_HINT_IN1 - T_HINT_IN0)) - smooth01((t - T_HINT_OUT0) / (T_HINT_OUT1 - T_HINT_OUT0)), 0.0)
        // the sheet's total and the line's labels
        val sheetFwd = easeInOut((t - T_HOUR) / HOUR_ROLL)
        val sheetRev = easeInOut((t - (T_REV0 + 0.1)) / 1.0)
        val sheetK = if (t < T_REV0 + 0.1) sheetFwd else 1 - sheetRev
        val labelK = if (t < T_REV0 + 0.1) smooth01((t - T_WIPE) / 0.4) else 1 - smooth01((t - (T_REV0 + 0.1)) / 0.6)
        // sparkles catch the light in the order the reflection reaches them
        val glintPlus = max(pulse(t, 0.86, 0.07), pulse(t, 4.7, 0.07))
        val glintHead = max(pulse(t, 1.08, 0.07), pulse(t, 4.78, 0.07))
        val glintSat = max(pulse(t, 1.27, 0.07), max(pulse(t, 4.04, 0.06), pulse(t, 4.86, 0.07)))
        // the press (touch-down), released as the badge lands
        val press = 1 + 0.03 * (springAt(t - T_PRESS, PRESS_RESPONSE, PRESS_ZETA) - springAt(t - 2.0, PRESS_RESPONSE, PRESS_ZETA))
        val popPlus = if (t < T_SWAP1 || t > T_REV0) 1.0 else 0.6 + 0.4 * springAt(t - T_SWAP1 - 0.04, POP_RESPONSE, POP_ZETA)
        val popHr = if (t < T_SWAP1 || t > T_REV0) 1.0 else 0.6 + 0.4 * springAt(t - T_SWAP1 - 0.08, POP_RESPONSE, POP_ZETA)
        // the studio sways like a hand (whole cycle per loop)
        val env = ((5 * PI) / 180) * sin((2 * PI * t) / LOOP)
        // the line's highlight pass (240 ms) as the new numeral lands, just before the hour's wipe
        val sweepT = (t - T_HOUR) / 0.24
        val sweep = if (sweepT >= 0 && sweepT <= 1) sweepT else -1.0
        o.yaw = yaw
        o.pitch = pitch
        o.ringYaw = ringYawAt(t)
        o.depth = depth
        o.value = value
        o.clock = clock
        o.sat = sat
        o.fill = fill
        o.hint = hint
        o.sheetK = sheetK
        o.labelK = labelK
        o.glintPlus = glintPlus
        o.glintHead = glintHead
        o.glintSat = glintSat
        o.press = press
        o.popPlus = popPlus
        o.popHr = popHr
        o.env = env
        o.sweep = sweep
        o.tPhase = t
        return o
    }

    /** the sheet's three changing digits roll right to left, 90 ms apart; slot 0 = minutes ones, 1 = minutes tens, 2 = hours ones */
    fun rollAt(sheetK: Double, slot: Int): Double {
        val own = 0.6
        val stag = (1 - own) / 2
        return easeInOut((sheetK - slot * stag * 0.9) / own)
    }

    // the finger
    const val YAW_PER_PT = 0.6
    const val PITCH_PER_PT = 0.35
    const val PITCH_MAX = 28.0
    const val PITCH_RUBBER = 0.3
    const val PROJECT = 0.18
    const val START_BEYOND = 90.0
    const val HOLD_MS = 220.0
    const val HOLD_DEPTH = 0.04
    const val IDLE_RESUME = 2.0
    const val RESUME_MS = 600.0
    const val MIN_SESSION = 60.0
    const val MIN_GAP_S = 0.06
    const val MAX_SKEW = 3 * 180.0

    /** rubber band: the value follows at `k` once it passes `limit` */
    fun rubber(v: Double, limit: Double, k: Double): Double {
        val a = abs(v)
        if (a <= limit) return v
        val over = a - limit
        return sign(v) * (limit + over * k)
    }
}

/** The look of one frame (RN `Look`). */
internal class FlipFocusBadgeLook {
    var yaw = 0.0
    var pitch = 0.0
    var ringYaw = 0.0
    var depth = 1.0
    var value = 10.0
    /** session seconds on the back */
    var clock = 0.0
    /** satellite lap 0 ... 1 */
    var sat = 0.4
    /** the progress line 0 ... 1 */
    var fill = 0.4
    /** 0 "flip your phone...", 1 "focusing..." */
    var hint = 0.0
    /** 0 "10h 24m" ... 1 "11h 00m" */
    var sheetK = 0.0
    /** 0 "10h ... 11h" ... 1 "11h ... 12h" */
    var labelK = 0.0
    var glintPlus = 0.0
    var glintHead = 0.0
    var glintSat = 0.0
    var press = 1.0
    var popPlus = 1.0
    var popHr = 1.0
    var env = 0.0
    /** the progress line's highlight pass, 0 ... 1 (-1 = off) */
    var sweep = -1.0
    var tPhase = 0.0

    fun copyFrom(o: FlipFocusBadgeLook) {
        yaw = o.yaw; pitch = o.pitch; ringYaw = o.ringYaw; depth = o.depth; value = o.value; clock = o.clock; sat = o.sat; fill = o.fill
        hint = o.hint; sheetK = o.sheetK; labelK = o.labelK; glintPlus = o.glintPlus; glintHead = o.glintHead; glintSat = o.glintSat
        press = o.press; popPlus = o.popPlus; popHr = o.popHr; env = o.env; sweep = o.sweep; tPhase = o.tPhase
    }
}

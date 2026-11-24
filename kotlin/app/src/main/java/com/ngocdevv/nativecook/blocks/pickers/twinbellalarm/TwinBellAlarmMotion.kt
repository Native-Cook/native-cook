package com.ngocdevv.nativecook.blocks.pickers.twinbellalarm

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sign
import kotlin.math.sin

/**
 * alarmScene.ts and alarmMotion.ts: the scene's numbers and the film as pure functions of t, so t = LOOP gives the
 * same frame as t = 0. A finger's own changes use the same functions from their own start times.
 */
internal object AlarmMotion {
    const val LOOP = 10.0

    val HOUR_PIVOT = floatArrayOf(0f, 0.092f, 0.0263f)
    val MINUTE_PIVOT = floatArrayOf(0f, 0.092f, 0.0276f)
    val HAMMER_PIVOT = floatArrayOf(0f, 0.156f, 0f)
    val BELL_R_PIVOT = floatArrayOf(0.03145f, 0.15116f, 0f)
    val BELL_L_PIVOT = floatArrayOf(-0.03145f, 0.15116f, 0f)
    private const val HAMMER_REACH = 10.9

    private val TARGET = doubleArrayOf(0.0, 0.142, 0.0)
    private const val DIST = 0.82
    private const val AZ_REST = -12.0
    private const val EL_REST = 10.0
    private const val FRAME_W = 0.25
    private const val FRAME_H = 0.36
    const val CAMERA_NEAR = 0.03
    const val CAMERA_FAR = 30.0

    const val IBL_INTENSITY = 30_000f
    val KEY_DIRECTION = floatArrayOf(0.4f, -0.85f, -0.35f)
    const val KEY_LUX = 72_000f
    val FILL_DIRECTION = floatArrayOf(-0.7f, -0.3f, -0.6f)
    const val FILL_LUX = 15_000f
    const val EXPOSURE = 0f

    const val BACKDROP = 0xB9C5DA
    const val PANEL = 0xFBFAF7
    const val CARD = 0xFFFFFF
    const val INK = 0x1C1D22
    const val MUTE = 0x767A86
    const val YELLOW = 0xE9A61C

    const val APP = "Earlybird"
    const val REPEATS = "Weekdays"
    const val REST_MIN = 7 * 60
    const val FILM_MIN = 6 * 60 + 30
    const val NOW_MIN = 22 * 60 + 18
    const val WHEEL_STEP = 5

    private const val WHEEL_A = 0.9
    private const val WHEEL_A_END = 1.8
    private const val ON = 2.8
    private const val RING_AT = 2.9
    private const val PUSH_IN = 2.8
    private const val PUSH_IN_END = 3.5
    private const val PUSH_OUT = 4.4
    private const val PUSH_OUT_END = 5.6
    private const val OFF = 6.0
    private const val WHEEL_B = 6.5
    private const val WHEEL_B_END = 7.4

    const val RING = 1.2
    private const val RING_IN = 0.05
    const val RING_OUT = 0.22
    private const val HAMMER_SWING = HAMMER_REACH - 0.6
    private const val HAMMER_HZ = 15.0
    private const val BELL_SHAKE = 2.2
    private const val BELL_HZ_L = 23.0
    private const val BELL_HZ_R = 26.0
    private const val ROCK = 1.5
    private const val ROCK_HZ = 11.0
    private const val SHUFFLE = 0.0012
    private const val SHUFFLE_HZ = 17.0
    private const val HOP = 0.012
    private const val HOP_S = 0.2
    private const val CHATTER = 0.003
    private const val CHATTER_HZ = 9.0
    private const val ZOOM = 0.14
    private const val AZ_PUSH = 17.0
    private const val EL_PUSH = -3.0
    const val ORBIT_SPAN = 110.0
    private const val ORBIT_MAX = 40.0
    private const val SETTLE = 0.04
    private const val SETTLE_DECAY = 8.0
    private const val SETTLE_FREQ = 16.0
    private const val SETTLE_S = 0.6
    private const val WHEEL_LEAD = 0.05
    private const val WHEEL_BEAT = 0.15
    private const val WHEEL_ROWS = (REST_MIN - FILM_MIN) / WHEEL_STEP

    fun clamp01(x: Double): Double = if (x < 0) 0.0 else if (x > 1) 1.0 else x
    fun seg(t: Double, a: Double, b: Double): Double = clamp01((t - a) / (b - a))
    private fun lerp(a: Double, b: Double, k: Double): Double = a + (b - a) * k
    private fun easeInOutSine(x: Double): Double = -(cos(PI * x) - 1) / 2
    private fun smooth(x: Double): Double = x * x * (3 - 2 * x)

    /** The shortest way round from one angle to another (deg, -180..180), with JavaScript's remainder. */
    fun shortest(d: Double): Double = (((d % 360) + 540) % 360) - 180

    fun hourDeg(min: Int): Double = (min % 720) / 2.0
    fun minuteDeg(min: Int): Double = (min % 60) * 6.0

    private fun settleWave(tau: Double): Double {
        if (tau <= 0 || tau >= SETTLE_S) return 0.0
        return SETTLE * exp(-SETTLE_DECAY * tau) * sin(SETTLE_FREQ * tau) * (1 - seg(tau, SETTLE_S - 0.15, SETTLE_S))
    }

    private fun filmSweep(t: Double): Double {
        val a = easeInOutSine(seg(t, WHEEL_A, WHEEL_A_END)) + settleWave(t - WHEEL_A_END)
        val b = easeInOutSine(seg(t, WHEEL_B, WHEEL_B_END)) + settleWave(t - WHEEL_B_END)
        return a - b
    }

    fun filmHour(t: Double): Double = lerp(hourDeg(REST_MIN), hourDeg(FILM_MIN), filmSweep(t))
    fun filmMinute(t: Double): Double = lerp(0.0, -(REST_MIN - FILM_MIN) * 6.0, filmSweep(t))

    fun filmWheel(t: Double): Int {
        val down = min(WHEEL_ROWS, max(0, floor((t - WHEEL_A - WHEEL_LEAD) / WHEEL_BEAT).toInt() + 1))
        val up = min(WHEEL_ROWS, max(0, floor((t - WHEEL_B - WHEEL_LEAD) / WHEEL_BEAT).toInt() + 1))
        return REST_MIN - WHEEL_STEP * (down - up)
    }

    fun filmArmed(t: Double): Boolean = t >= ON && t < OFF

    fun filmRing(t: Double): Double {
        val tau = t - RING_AT
        return if (tau >= 0 && tau <= RING) tau else -1.0
    }

    fun filmPush(t: Double): Double =
        easeInOutSine(seg(t, PUSH_IN, PUSH_IN_END)) - easeInOutSine(seg(t, PUSH_OUT, PUSH_OUT_END))

    /** The film's finger on the wheel: out = [opacity, pressed, place along its drag -0.5..0.5]. */
    fun wheelGhost(t: Double, out: DoubleArray) {
        val second = t > (WHEEL_A_END + WHEEL_B) / 2
        val a = if (second) WHEEL_B else WHEEL_A
        val b = if (second) WHEEL_B_END else WHEEL_A_END
        val k = easeInOutSine(seg(t, a, b))
        out[0] = seg(t, a - 0.25, a - 0.05) * (1 - seg(t, b + 0.1, b + 0.3))
        out[1] = seg(t, a - 0.05, a + 0.05) * (1 - seg(t, b, b + 0.12))
        out[2] = if (second) 0.5 - k else k - 0.5
    }

    /** The film's finger on the switch: out = [opacity, pressed]. */
    fun toggleGhost(t: Double, out: DoubleArray) {
        val at = if (t > (ON + OFF) / 2) OFF else ON
        out[0] = seg(t, at - 0.3, at - 0.08) * (1 - seg(t, at + 0.1, at + 0.3))
        out[1] = seg(t, at - 0.08, at) * (1 - seg(t, at + 0.04, at + 0.16))
    }

    private fun ringEnvelope(tau: Double): Double {
        if (tau < 0 || tau > RING) return 0.0
        return smooth(seg(tau, 0.0, RING_IN)) * (1 - smooth(seg(tau, RING - RING_OUT, RING)))
    }

    /** The clock tau seconds into a ring: out = [hammer, left bell, right bell, rock (deg about z), shuffle x, height (m)]. */
    fun ringAt(tau: Double, out: DoubleArray) {
        val e = ringEnvelope(tau)
        if (e <= 0) {
            out.fill(0.0)
            return
        }
        val w = 2 * PI * tau
        out[0] = e * HAMMER_SWING * sin(w * HAMMER_HZ)
        out[1] = e * BELL_SHAKE * sin(w * BELL_HZ_L + 0.6)
        out[2] = e * BELL_SHAKE * sin(w * BELL_HZ_R + 2.1)
        out[3] = e * ROCK * sin(w * ROCK_HZ)
        out[4] = e * SHUFFLE * sin(w * SHUFFLE_HZ + 1)
        val u = tau / HOP_S
        val jump = if (u < 1) HOP * 4 * u * (1 - u) else 0.0
        out[5] = jump + e * CHATTER * abs(sin(PI * CHATTER_HZ * tau)) * seg(tau, HOP_S * 0.8, HOP_S * 1.4)
    }

    fun tanHalfFovY(aspect: Double): Double = max(FRAME_H / 2, FRAME_W / 2 / aspect) / DIST

    fun cameraAt(push: Double, orbit: Double, out: DoubleArray) {
        val az = (AZ_REST + AZ_PUSH * push + orbit) * PI / 180
        val el = (EL_REST + EL_PUSH * push) * PI / 180
        val d = DIST * (1 - ZOOM * push)
        out[0] = TARGET[0] + d * cos(el) * sin(az)
        out[1] = TARGET[1] + d * sin(el)
        out[2] = TARGET[2] + d * cos(el) * cos(az)
        out[3] = TARGET[0]
        out[4] = TARGET[1]
        out[5] = TARGET[2]
    }

    fun orbitFromDrag(dx: Double, width: Double): Double {
        val a = -dx / width * ORBIT_SPAN
        val m = abs(a)
        if (m <= ORBIT_MAX) return a
        val over = m - ORBIT_MAX
        return sign(a) * (ORBIT_MAX + over * 14 / (over + 14))
    }
}

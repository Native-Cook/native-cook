package com.ngocdevv.nativecook.blocks.pickers.thermostatfrostdial

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.round

/*
 Thermostat Frost Dial — geometry, the autoPlay script and the touch simulation, ported from
 thermostatMotion.ts. LOOP = 9 s (set point T in °C):
   0.0 – 1.3   rest at 21.0°.
   1.3 – 3.4   turned colder to 16.0° (ease-in-out); frost follows 0.25 s behind, growing from
               the rim inward; the wall cools to blue.
   3.4 – 4.4   hold at 16°, frost at its fullest.
   4.4 – 6.8   turned warmer to 25.0°: the frost melts into beads (4.8 – 5.6), the wall glows
               amber and the air above the dial shimmers; the beads dry off 6.4 – 7.8.
   6.8 – 8.0   back to 21.0°.
   8.0 – 9.0   rest.
 The ring turns 13.5° per degree (270° for 10 – 30°). The big number shows T rounded to 0.5°,
 its digits rolling between steps.
*/

internal const val LOOP = 9f
private const val REST = 21f
private const val MIN_T = 10f
private const val MAX_T = 30f
/** The room's own temperature: ticks between it and the set point are lit. */
internal const val DIAL_ROOM = 20.5f
private const val IDLE_BEFORE_BACK = 2f
private const val BACK_FOR = 0.9f
private const val MODE_AUTO = 0
internal const val DIAL_MODE_LIVE = 1
private const val MODE_BACK = 2

private val START = (135.0 * PI / 180.0).toFloat()
private val ARC = (270.0 * PI / 180.0).toFloat()

internal const val DIAL_HEADER_H = 112f
internal const val DIAL_BOTTOM_H = 214f

internal class DialGeo(val w: Float, val h: Float, val cx: Float, val cy: Float, val ro: Float, val ri: Float)

internal fun makeDialGeo(w: Float, h: Float, insetTop: Float, insetBottom: Float): DialGeo {
    val top = insetTop + DIAL_HEADER_H + 20f
    val bottom = h - max(insetBottom, 12f) - DIAL_BOTTOM_H - 16f
    val ro = max(1f, minOf(w * 0.44f, 176f, (bottom - top) / 2f))
    return DialGeo(w, h, w / 2f, (top + bottom) / 2f, ro, ro * 0.77f)
}

private fun clamp01(x: Float) = min(1f, max(0f, x))
private fun seg(t: Float, a: Float, b: Float) = clamp01((t - a) / (b - a))
private fun easeInOut(x: Float) = if (x < 0.5f) 4f * x * x * x else 1f - (-2f * x + 2f).pow(3) / 2f

/** Set point angle on screen (rad, y down; 10° at 135°, 30° at 405°). */
internal fun dialAngleOf(t: Float) = START + ((t - MIN_T) / (MAX_T - MIN_T)) * ARC

/** The scripted set point at loop time t. */
private fun scriptT(t: Float) =
    REST - 5f * easeInOut(seg(t, 1.3f, 3.4f)) + 9f * easeInOut(seg(t, 4.4f, 6.8f)) - 4f * easeInOut(seg(t, 6.8f, 8.0f))

private fun coldOf(t: Float) = clamp01((REST - t) / 5f)
private fun warmOf(t: Float) = clamp01((t - REST) / 4f)

internal class DialFrame {
    var temp = REST
    var frost = 0f
    var wet = 0f
    var phase = 0f
}

private fun scriptFrame(t: Float, f: DialFrame) {
    f.temp = scriptT(t)
    // Frost grows a beat behind the dial and melts as it warms.
    f.frost = coldOf(scriptT(max(0f, t - 0.25f)))
    f.wet = easeInOut(seg(t, 4.8f, 5.6f)) * (1f - easeInOut(seg(t, 6.4f, 7.8f)))
    f.phase = t / LOOP
}

/** Past the ends the ring resists: the overshoot is squashed toward ~4°. */
private fun rubber(raw: Float): Float {
    if (raw > MAX_T) {
        val o = raw - MAX_T
        return MAX_T + o / (1f + o / 4f)
    }
    if (raw < MIN_T) {
        val o = MIN_T - raw
        return MIN_T - o / (1f + o / 4f)
    }
    return raw
}

internal class DialSim {
    var mode = MODE_AUTO
        private set
    private var t = 0f
    private var now = 0f
    private var last = 0f
    private var back = 0f
    val frame = DialFrame()
    /** Set point velocity (°/s) for the release spring, and whether a finger holds the ring. */
    private var v = 0f
    var held = false
        private set
    /** The finger's last angle around the centre. */
    private var grab = 0f
    /** The raw value the finger asks for (before rubber-banding). */
    private var raw = REST
    private var turnAt = 0f
    private val from = floatArrayOf(REST, 0f, 0f, 0f)

    /** Whether the frame loop has nothing to do: at rest outside autoPlay. */
    fun idle(auto: Boolean) = mode == MODE_AUTO && !auto

    fun grab(angle: Float) {
        mode = DIAL_MODE_LIVE
        held = true
        grab = angle
        raw = frame.temp
        v = 0f
        turnAt = now
        last = now
    }

    /** The finger turns the ring: the change of its angle around the centre turns the set point. */
    fun turn(angle: Float) {
        val dt = now - turnAt
        turnAt = now
        var d = angle - grab
        if (d > PI) d -= (2 * PI).toFloat()
        if (d < -PI) d += (2 * PI).toFloat()
        grab = angle
        raw += (d / ARC) * (MAX_T - MIN_T)
        val before = frame.temp
        frame.temp = rubber(raw)
        if (dt > 0) v = v * 0.6f + ((frame.temp - before) / dt) * 0.4f
        last = now
    }

    fun release() {
        held = false
        last = now
    }

    /** One frame of the simulation. */
    fun step(dt: Float, auto: Boolean, reduced: Boolean) {
        now += dt
        val f = frame
        if (mode == MODE_AUTO) {
            if (auto) t = (t + dt) % LOOP
            scriptFrame(if (reduced) 0f else t, f)
            return
        }
        f.phase += dt / LOOP
        if (mode == DIAL_MODE_LIVE) {
            if (!held) {
                // Release: a spring carries the finger's speed and settles on the nearest half degree.
                val target = (round((f.temp + v * 0.12f) * 2f) / 2f).coerceIn(MIN_T, MAX_T)
                val w = 14f
                val z = 0.8f
                val a = w * w * (target - f.temp) - 2f * z * w * v
                v += a * dt
                f.temp += v * dt
                if (abs(target - f.temp) < 0.002f && abs(v) < 0.02f) {
                    f.temp = target
                    v = 0f
                }
            }
            // Frost creeps in over ~0.6 s and melts faster; what melts becomes beads that dry off.
            val cold = coldOf(f.temp)
            val before = f.frost
            f.frost += (cold - f.frost) * min(1f, dt * (if (cold > f.frost) 2.2f else 3.5f))
            val melted = max(0f, before - f.frost)
            f.wet = min(1f, f.wet + melted * 1.6f)
            f.wet = max(0f, f.wet - dt * (0.12f + 0.45f * warmOf(f.temp)))
            if (auto && !held && now - last > IDLE_BEFORE_BACK) {
                mode = MODE_BACK
                back = 0f
                from[0] = f.temp
                from[1] = f.frost
                from[2] = f.wet
                from[3] = f.phase
            }
            return
        }
        back = min(1f, back + dt / BACK_FOR)
        val k = easeInOut(back)
        f.temp = from[0] + (REST - from[0]) * k
        f.frost = from[1] * (1f - k)
        f.wet = from[2] * (1f - k)
        f.phase = from[3] + (ceil(from[3]) - from[3]) * k
        if (back >= 1f) {
            mode = MODE_AUTO
            t = 0f
            scriptFrame(0f, f)
        }
    }
}

/** The shader's uniforms for a frame, in the order of thermostatShader's `ring`, `env` and `lit`. */
internal class DialUniforms {
    val ring = FloatArray(4)
    val env = FloatArray(4)
    val lit = FloatArray(4)

    fun set(f: DialFrame) {
        val t = f.temp.coerceIn(MIN_T - 1.5f, MAX_T + 1.5f)
        ring[0] = dialAngleOf(t) - dialAngleOf(REST)
        ring[1] = dialAngleOf(t)
        ring[2] = f.frost
        ring[3] = f.wet
        env[0] = easeInOut(coldOf(t))
        env[1] = easeInOut(warmOf(t))
        env[2] = f.phase - floor(f.phase)
        env[3] = 0f
        lit[0] = dialAngleOf(DIAL_ROOM)
        lit[1] = dialAngleOf((round(t * 2f) / 2f).coerceIn(MIN_T, MAX_T))
        lit[2] = if (t >= DIAL_ROOM) 1f else 0f
        lit[3] = 0f
    }
}

/**
 The rolling digits of T rounded to 0.5°: wheel positions for the tens, the ones and the half
 (0 = ".0", 1 = ".5"). Between steps a column rolls over the middle 60 % of the step, so it rests
 exactly on a whole digit whenever T holds.
*/
internal fun dialDigitWheel(temp: Float, column: Int): Float {
    val k = temp.coerceIn(MIN_T, MAX_T) * 2f
    val base = floor(k)
    val fr = k - base
    val s = base + ((fr - 0.2f) / 0.6f).coerceIn(0f, 1f)
    val sm = base + (s - base) * (s - base) * (3f - 2f * (s - base))
    val half = sm - 2f * floor(sm / 2f)
    val u = sm / 2f
    val ones = floor(u) + ((u - floor(u) - 0.5f) * 2f).coerceIn(0f, 1f)
    val tens = floor(ones / 10f) + (ones - floor(ones / 10f) * 10f - 9f).coerceIn(0f, 1f)
    return when (column) {
        0 -> tens
        1 -> ones - floor(ones / 10f) * 10f
        else -> half
    }
}

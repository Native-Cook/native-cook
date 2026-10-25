package com.ngocdevv.nativecook.blocks.progress.candleburntimer

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

// The candle's state (candleMotion.ts), read by the shader and the timer digits. `remain` is the
// session left (0…1 of 25:00) and sets the wax height. autoPlay derives every value from the loop
// time (a compressed 25-minute burn); under a finger the timer runs in real time, the flame bends
// on a spring with the wind of a drag, a hard flick blows it out and a tap relights it.

/** The autoPlay period (s), equal to the manifest's recording.loop. */
const val LOOP = 9.0
internal const val CANDLE_SESSION = 25 * 60.0
/** The stub left at the end, as a share of the full height. */
internal const val CANDLE_STUB = 0.24
private const val BURN_A = 1.4
private const val BURN_B = 6.0
private const val OUT_A = 6.0
private const val OUT_B = 6.35
private const val SMOKE_AT = 6.05
private const val DONE_A = 6.6
private const val DONE_B = 7.4
private const val RISE_A = 7.4
private const val RISE_B = 8.4
private const val RELIGHT = 8.4
private const val DRIP_FADE_A = 7.4
private const val DRIP_FADE_B = 7.8
/** Two drips in the loop: x offset (share of the half width), birth (s), length (dp). */
private val ScriptDrips = arrayOf(doubleArrayOf(-0.36, 2.4, 44.0), doubleArrayOf(0.44, 4.2, 32.0))
private const val DRIP_RUN = 1.2
private const val SMOKE_LIFE = 2.4
private const val WIND_PER = 900.0
private const val BEND_W = 10.0
private const val BEND_Z = 0.32
internal const val CANDLE_BLOW_AT = 1500.0
internal const val CANDLE_IDLE_BEFORE_BACK = 2.0
private const val BACK_FOR = 0.9

internal const val CANDLE_AUTO = 0
internal const val CANDLE_LIVE = 1
internal const val CANDLE_BACK = 2

internal const val STATUS_FOCUS = 0
internal const val STATUS_PAUSED = 1
internal const val STATUS_OUT = 2
internal const val STATUS_DONE = 3

internal class CandleState(auto: Boolean) {
    var mode = if (auto) CANDLE_AUTO else CANDLE_LIVE
    var t = 0.0
    var now = 0.0
    var lastTouch = -100.0
    var remain = 1.0
    var lit = 1.0
    var outAt = -100.0
    var igniteAt = -100.0
    var bend = 0.0
    var bendV = 0.0
    var wind = 0.0
    var flick = 0.0
    var phase = 0.0
    val dx = doubleArrayOf(0.0, 0.0)
    val db = doubleArrayOf(-100.0, -100.0)
    val dl = doubleArrayOf(0.0, 0.0)
    var dripAlpha = 1.0
    var running = true
    var status = STATUS_FOCUS
    val statusW = doubleArrayOf(1.0, 0.0, 0.0, 0.0)
    var riseAt = -100.0
    var riseFrom = 1.0
    var backAt = 0.0
    var buzz = false
}

private fun seg(t: Double, a: Double, b: Double) = min(max((t - a) / (b - a), 0.0), 1.0)
internal fun candleEaseInOut(x: Double) = if (x < 0.5) 4 * x * x * x else 1 - (-2 * x + 2).pow(3) / 2
private fun easeOut(x: Double) = 1 - (1 - x).pow(3)

/** A lit flame catching: 0 → 1 with a small overshoot, settled after ~0.5 s. */
private fun ignite(tau: Double): Double = if (tau <= 0) 0.0 else 1 - exp(-9 * tau) * cos(10 * tau)

/** Flicker of whole-loop sines (−1…1). */
private fun flickerAt(t: Double): Double {
    val w = 2 * PI / LOOP
    return 0.5 * sin(w * 7 * t) + 0.3 * sin(w * 13 * t + 1.3) + 0.2 * sin(w * 23 * t + 2.1)
}

internal fun CandleState.scriptFrame(t: Double) {
    val start = now - t
    val burn = seg(t, BURN_A, BURN_B)
    val rise = candleEaseInOut(seg(t, RISE_A, RISE_B))
    remain = if (t < RISE_A) 1 - burn else rise
    lit = when {
        t < OUT_A -> 1.0
        t < RELIGHT -> 1 - candleEaseInOut(seg(t, OUT_A, OUT_B))
        else -> ignite(t - RELIGHT)
    }
    outAt = if (t >= SMOKE_AT) start + SMOKE_AT else -100.0
    igniteAt = start + RELIGHT
    flick = flickerAt(t)
    phase = 2 * PI * 6 / LOOP * t
    bend = 0.08 * sin(2 * PI * 2 * t / LOOP) + 0.04 * sin(2 * PI * 5 * t / LOOP)
    bendV = 0.0
    for (i in 0 until 2) {
        dx[i] = ScriptDrips[i][0]
        db[i] = if (t >= ScriptDrips[i][1]) start + ScriptDrips[i][1] else -100.0
        dl[i] = ScriptDrips[i][2]
    }
    dripAlpha = 1 - easeOut(seg(t, DRIP_FADE_A, DRIP_FADE_B))
    val done = seg(t, OUT_B, DONE_A) * (1 - seg(t, DONE_B, RISE_B))
    statusW[STATUS_FOCUS] = 1 - done
    statusW[STATUS_PAUSED] = 0.0
    statusW[STATUS_OUT] = 0.0
    statusW[STATUS_DONE] = done
    status = if (done > 0.5) STATUS_DONE else STATUS_FOCUS
    running = true
}

/** A drip's length now (dp): it runs down from the rim over DRIP_RUN s. */
internal fun CandleState.dripLength(i: Int): Double {
    val age = now - db[i]
    if (age < 0) return 0.0
    return dl[i] * easeOut(min(age / DRIP_RUN, 1.0))
}

/** Smoke: seconds since the flame went out, and its strength. */
internal fun CandleState.smokeAge(): Double {
    val age = now - outAt
    return if (outAt < -50 || age < 0) 0.0 else age
}

internal fun CandleState.smokeStrength(): Double {
    val age = now - outAt
    if (outAt < -50 || age < 0) return 0.0
    val fade = 1 - seg(age, SMOKE_LIFE * 0.55, SMOKE_LIFE)
    val relit = if (lit > 0.05) 1 - seg(now - igniteAt, 0.0, 0.3) else 1.0
    return fade * max(relit, 0.0)
}

private inline fun spring(x: Double, v: Double, x0: Double, w: Double, z: Double, dt: Double, out: (Double, Double) -> Unit) {
    val nv = v + (-w * w * (x - x0) - 2 * z * w * v) * dt
    out(x + nv * dt, nv)
}

private fun CandleState.setStatus(s: Int, dt: Double) {
    status = s
    val k = min(dt / 0.18, 1.0)
    for (i in 0 until 4) statusW[i] += ((if (i == s) 1.0 else 0.0) - statusW[i]) * k
}

/** Real-time frame under a finger (or with autoPlay off). */
internal fun CandleState.liveFrame(dt: Double) {
    val lean = max(-1.0, min(1.0, wind / WIND_PER))
    spring(bend, bendV, lean, BEND_W, BEND_Z, dt) { x, v -> bend = x; bendV = v }
    wind *= exp(-10 * dt)
    phase += 2 * PI * 6 / LOOP * dt * (1 + abs(bend) * 2)
    flick = flickerAt(now) * (1 + abs(bend) * 1.5)
    lit = if (outAt > igniteAt) max(0.0, 1 - (now - outAt) / 0.25) else if (igniteAt > -50) ignite(now - igniteAt) else 1.0
    if (riseAt > -50) {
        val k = candleEaseInOut(min((now - riseAt) / 0.9, 1.0))
        remain = riseFrom + (1 - riseFrom) * k
        dripAlpha = min(dripAlpha, 1 - k)
        if (k >= 1) {
            riseAt = -100.0
            db[0] = -100.0
            db[1] = -100.0
            dripAlpha = 1.0
        }
    } else if (running && lit > 0.5 && remain > 0) {
        remain = max(0.0, remain - dt / CANDLE_SESSION)
    }
    val s = when {
        lit < 0.5 -> STATUS_OUT
        remain <= 0 -> STATUS_DONE
        running -> STATUS_FOCUS
        else -> STATUS_PAUSED
    }
    setStatus(s, dt)
}

internal fun CandleState.blowOut(): Boolean {
    if (outAt > igniteAt) return false
    outAt = now
    return true
}

internal fun CandleState.relight(): Boolean {
    if (outAt <= igniteAt && lit > 0.5) return false
    igniteAt = now
    return true
}

internal fun CandleState.resetCandle() {
    riseAt = now
    riseFrom = remain
    running = true
    relight()
}

internal fun CandleState.beginBack() {
    mode = CANDLE_BACK
    backAt = now
    riseAt = now
    riseFrom = remain
    running = true
    relight()
}

/** Back to the loop's first frame: the wax rises, the flame catches; true when done. */
internal fun CandleState.backFrame(dt: Double): Boolean {
    wind = 0.0
    liveFrame(dt)
    val k = candleEaseInOut(min((now - backAt) / BACK_FOR, 1.0))
    phase *= 1 - k
    flick *= 1 - k
    if (k >= 1 && riseAt < -50 && now - igniteAt > 0.6) {
        bend = 0.0
        bendV = 0.0
        return true
    }
    return false
}

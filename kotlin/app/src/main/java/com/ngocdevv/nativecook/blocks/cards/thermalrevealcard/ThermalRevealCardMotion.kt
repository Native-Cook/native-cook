package com.ngocdevv.nativecook.blocks.cards.thermalrevealcard

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

// The card's warmth and tilt, ported from thermalMotion.ts. Heat lives in a ring of HEAT_SLOTS
// points (card dp, and the frame-clock time each was laid down); a point's strength falls from
// HEAT_PEAK to 0 over HEAT_LIFE. In autoPlay the ring is rebuilt every frame from the loop time
// alone; a touch keeps the same ring and goes on adding to it, so nothing jumps.

/** The autoPlay period (s), equal to the manifest's recording.loop. */
const val LOOP = 9.0
internal const val THERMAL_HEAT_SLOTS = 24
private const val HEAT_LIFE = 1.4
private const val HEAT_PEAK = 0.42
internal const val THERMAL_HEAT_RADIUS = 30f
private const val EMIT_EVERY = HEAT_LIFE / (THERMAL_HEAT_SLOTS - 1)
private const val SWEEP_A = 1.5
private const val SWEEP_B = 5.5
private const val GLOB_UP_A = 4.6
private const val GLOB_UP_B = 5.5
private const val GLOB_DOWN_A = 6.4
private const val GLOB_DOWN_B = 8.2
internal const val THERMAL_TILT_MAX = 8.0
internal const val THERMAL_TILT_PER_PT = 0.07
internal const val THERMAL_IDLE_BEFORE_BACK = 2.0
private const val BACK_FOR = 0.8

internal const val THERMAL_AUTO = 0
internal const val THERMAL_LIVE = 1
internal const val THERMAL_BACK = 2

internal class ThermalState(auto: Boolean, reduced: Boolean) {
    var mode = if (auto) THERMAL_AUTO else THERMAL_LIVE
    var t = 0.0
    var now = 0.0
    var lastTouch = -100.0
    val hx = DoubleArray(THERMAL_HEAT_SLOTS)
    val hy = DoubleArray(THERMAL_HEAT_SLOTS)
    val ht = DoubleArray(THERMAL_HEAT_SLOTS) { -100.0 }
    var glob = if (reduced) 1.0 else 0.0
    var warming = false
    var fx = 0.0
    var fy = 0.0
    var lastEmit = -100.0
    var ex = 0.0
    var ey = 0.0
    val steady = reduced
    var rx = 0.0
    var ry = 0.0
    var vrx = 0.0
    var vry = 0.0
    var tiltX = 0.0
    var tiltY = 0.0
    var tilting = false
    var gloss = 0.3
    val found = BooleanArray(3)
    var buzz = false
    var backAt = 0.0
    var backGlob = 0.0
    var backGloss = 0.3
    var fade = 1.0

    /** False once a finger-driven card has nothing left moving, so the frame loop can stop. */
    fun moving(): Boolean {
        if (mode != THERMAL_LIVE || warming || tilting) return true
        if (abs(rx) > 0.005 || abs(ry) > 0.005 || abs(vrx) > 0.01 || abs(vry) > 0.01) return true
        if (!steady && glob > 0.002) return true
        for (j in 0 until THERMAL_HEAT_SLOTS) if (thermalHeatStrength(now - ht[j]) > 0) return true
        return false
    }
}

private fun seg(t: Double, a: Double, b: Double) = min(max((t - a) / (b - a), 0.0), 1.0)
private fun easeInOut(x: Double) = if (x < 0.5) 4 * x * x * x else 1 - (-2 * x + 2).pow(3) / 2
private fun easeOut(x: Double) = 1 - (1 - x).pow(3)

/** A point's warmth at age a (s): a quick rise, then a slow fall to nothing at HEAT_LIFE. */
internal fun thermalHeatStrength(age: Double): Double {
    if (age < 0 || age >= HEAT_LIFE) return 0.0
    val rise = min(age / 0.08, 1.0)
    val fall = 1 - age / HEAT_LIFE
    return HEAT_PEAK * rise * fall.pow(1.5)
}

/** Rebuilds the ring from loop time t: the points the scripted thumb laid in the last HEAT_LIFE s. */
internal fun ThermalState.scriptFrame(t: Double, w: Double, h: Double) {
    val count = floor((SWEEP_B - SWEEP_A) / EMIT_EVERY).toInt()
    val newest = floor((t - SWEEP_A) / EMIT_EVERY).toInt()
    for (j in 0 until THERMAL_HEAT_SLOTS) {
        val k = newest - (THERMAL_HEAT_SLOTS - 1) + j
        ht[j] = -100.0
        if (k < 0 || k > count) continue
        val te = SWEEP_A + k * EMIT_EVERY
        val age = t - te
        if (age < 0 || age >= HEAT_LIFE) continue
        // The thumb's S-curve, slowing at the ends of its stroke.
        val u = easeInOut(seg(te, SWEEP_A, SWEEP_B))
        hx[j] = (0.5 - 0.22 * kotlin.math.cos(2 * PI * u)) * w
        hy[j] = (0.14 + 0.74 * u) * h
        ht[j] = now - age
    }
    glob = easeOut(seg(t, GLOB_UP_A, GLOB_UP_B)) * (1 - easeInOut(seg(t, GLOB_DOWN_A, GLOB_DOWN_B)))
    gloss = 0.3 + 0.22 * sin(2 * PI * t / LOOP)
    rx = 1.6 * sin(2 * PI * t / LOOP)
    ry = -1.1 * sin(2 * PI * 2 * t / LOOP)
    fade = 1.0
}

private inline fun spring(x: Double, v: Double, x0: Double, w: Double, z: Double, dt: Double, out: (Double, Double) -> Unit) {
    val nv = v + (-w * w * (x - x0) - 2 * z * w * v) * dt
    out(x + nv * dt, nv)
}

/** Lays one heat point at (x, y), over the coldest slot. */
internal fun ThermalState.emit(x: Double, y: Double) {
    var slot = 0
    var oldest = Double.POSITIVE_INFINITY
    for (j in 0 until THERMAL_HEAT_SLOTS) {
        if (ht[j] < oldest) {
            oldest = ht[j]
            slot = j
        }
    }
    hx[slot] = x
    hy[slot] = y
    ht[slot] = now
    lastEmit = now
    ex = x
    ey = y
}

internal fun ThermalState.liveFrame(dt: Double) {
    if (warming && now - lastEmit >= EMIT_EVERY) emit(fx, fy)
    if (!steady) glob *= exp(-1.4 * dt)
    val tx = if (tilting) tiltX else 0.0
    val ty = if (tilting) tiltY else 0.0
    val w = if (tilting) 22.0 else 11.0
    val z = if (tilting) 0.95 else 0.55
    spring(rx, vrx, tx, w, z, dt) { x, v -> rx = x; vrx = v }
    spring(ry, vry, ty, w, z, dt) { x, v -> ry = x; vry = v }
    gloss = 0.3 + (ry / THERMAL_TILT_MAX) * 0.35 - (rx / THERMAL_TILT_MAX) * 0.15
}

internal fun ThermalState.beginBack() {
    mode = THERMAL_BACK
    backAt = now
    backGlob = glob
    backGloss = gloss
    warming = false
    tilting = false
}

/** Easing back to the first frame of the loop; true when done. */
internal fun ThermalState.backFrame(dt: Double): Boolean {
    val k = easeInOut(min((now - backAt) / BACK_FOR, 1.0))
    fade = 1 - k
    glob = backGlob * (1 - k)
    gloss = backGloss + (0.3 - backGloss) * k
    spring(rx, vrx, 0.0, 11.0, 0.9, dt) { x, v -> rx = x; vrx = v }
    spring(ry, vry, 0.0, 11.0, 0.9, dt) { x, v -> ry = x; vry = v }
    if (k >= 1) {
        rx = 0.0; ry = 0.0; vrx = 0.0; vry = 0.0
        fade = 1.0
        ht.fill(-100.0)
        return true
    }
    return false
}

/** Warmth at a card point, as the shader sums it (without the noise). */
internal fun ThermalState.heatAt(x: Double, y: Double, w: Double, h: Double): Double {
    var sum = 0.0
    val r2 = 2.0 * THERMAL_HEAT_RADIUS * THERMAL_HEAT_RADIUS
    for (j in 0 until THERMAL_HEAT_SLOTS) {
        val st = thermalHeatStrength(now - ht[j]) * fade
        if (st <= 0) continue
        val dx = x - hx[j]
        val dy = y - hy[j]
        sum += st * exp(-(dx * dx + dy * dy) / r2)
    }
    val cx = x / w - 0.5
    val cy = y / h - 0.5
    return sum + glob * (1.25 - 0.7 * hypot(cx, cy))
}

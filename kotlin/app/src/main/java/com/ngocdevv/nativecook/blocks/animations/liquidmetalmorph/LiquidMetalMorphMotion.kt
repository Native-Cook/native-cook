package com.ngocdevv.nativecook.blocks.animations.liquidmetalmorph

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

// The blob's state and its timeline, ported from liquidMetalMotion.ts. In autoPlay every value is a
// pure function of the loop time t (so t = LOOP draws the same frame as t = 0); under a finger the
// shape weights and the held droplet run on springs. Units: dp and seconds.

/** The autoPlay period (s), equal to the manifest's recording.loop. */
const val LOOP = 9.0

/** drop → star, star → ring, ring → drop (s). */
private val Transitions = arrayOf(doubleArrayOf(1.6, 3.2), doubleArrayOf(4.6, 6.2), doubleArrayOf(7.0, 8.4))
private const val JIGGLE_AMP = 3.2
private const val JIGGLE_PERIOD = 0.32
private const val JIGGLE_FOR = 0.6
internal const val LIQUID_METAL_WOBBLE_AMP = 2.2
private const val SAT_OUT = 0.62
private const val SAT_STAGGER = 0.08
private const val SAT_K = 22.0

private const val SHAPE_W = 9.0
private const val SHAPE_Z = 0.62
private const val HOLD_W = 26.0
private const val HOLD_Z = 0.9
private const val HOME_W = 13.0
private const val HOME_Z = 0.5
internal const val LIQUID_METAL_TETHER_K = 26.0
internal const val LIQUID_METAL_DETACH_AT = 120.0
internal const val LIQUID_METAL_IDLE_BEFORE_BACK = 2.0
private const val BACK_FOR = 0.85

internal const val MODE_AUTO = 0
internal const val MODE_LIVE = 1
internal const val MODE_BACK = 2

/** Everything the frame loop, the gestures and the shader share. Plain fields: read only in draw. */
internal class LiquidMetal(auto: Boolean, reduced: Boolean) {
    var mode = if (auto) MODE_AUTO else MODE_LIVE
    var t = 0.0
    var now = 0.0
    var lastTouch = -100.0
    val w = doubleArrayOf(1.0, 0.0, 0.0)
    val v = doubleArrayOf(0.0, 0.0, 0.0)
    var target = 0
    var p1 = 0.0
    var p2 = 0.0
    val amp = if (reduced) 0.0 else LIQUID_METAL_WOBBLE_AMP
    var jiggle = 0.0
    val sats = DoubleArray(16)
    var held = false
    var shown = false
    var fx = 0.0
    var fy = 0.0
    var dx = 0.0
    var dy = 0.0
    var dvx = 0.0
    var dvy = 0.0
    var hx = 0.0
    var hy = 0.0
    var detached = false
    var backAt = 0.0
    val backW = doubleArrayOf(1.0, 0.0, 0.0)
    var backP1 = 0.0
    var backP2 = 0.0
    var buzz = false

    /** True while something on screen still moves (the wobble, a spring, the droplet). */
    fun moving(): Boolean {
        if (mode != MODE_LIVE || amp > 0 || shown || held) return true
        for (i in 0 until 3) {
            val goal = if (i == target) 1.0 else 0.0
            if (kotlin.math.abs(w[i] - goal) > 0.0005 || kotlin.math.abs(v[i]) > 0.002) return true
        }
        return kotlin.math.abs(jiggle) > 0.01
    }
}

private fun seg(t: Double, a: Double, b: Double) = min(max((t - a) / (b - a), 0.0), 1.0)

internal fun liquidMetalEaseInOut(x: Double) = if (x < 0.5) 4 * x * x * x else 1 - (-2 * x + 2).pow(3) / 2

private fun hash(i: Double): Double {
    val s = sin(i * 12.9898) * 43758.5453
    return s - floor(s)
}

/** Shortest signed angle from a to b. */
private fun angleTo(a: Double, b: Double): Double {
    val d = (((b - a) % (2 * PI)) + 3 * PI) % (2 * PI)
    return d - PI
}

/** Writes the autoPlay frame at loop time t. */
internal fun LiquidMetal.scriptFrame(t: Double, r: Double) {
    val a = liquidMetalEaseInOut(seg(t, Transitions[0][0], Transitions[0][1]))
    val b = liquidMetalEaseInOut(seg(t, Transitions[1][0], Transitions[1][1]))
    val c = liquidMetalEaseInOut(seg(t, Transitions[2][0], Transitions[2][1]))
    w[0] = 1 - a + c
    w[1] = a - b
    w[2] = b - c
    p1 = 2 * PI * 2 * t / LOOP
    p2 = 2 * PI * 3 * t / LOOP
    jiggle = 0.0
    for (k in 0 until 3) {
        val tau = t - Transitions[k][1]
        if (tau >= 0 && tau < JIGGLE_FOR) {
            val fade = 1 - tau / JIGGLE_FOR
            jiggle = JIGGLE_AMP * sin(2 * PI * tau / JIGGLE_PERIOD) * fade * fade
        }
    }
    sats.fill(0.0)
    for (k in 0 until 3) {
        val s0 = Transitions[k][0]
        val s1 = Transitions[k][1]
        if (t <= s0 || t >= s1) continue
        val s = (t - s0) / (s1 - s0)
        for (i in 0 until 4) {
            val si = seg(s, SAT_STAGGER * i, 1 - SAT_STAGGER * (3 - i))
            if (si <= 0 || si >= 1) continue
            val bump = sin(PI * si)
            val h = hash((i * 7 + k * 31 + 1).toDouble())
            val angle = k * 0.9 + i * (PI / 2) + 0.42 + (h - 0.5) * 0.7 + 0.6 * si
            val dist = r * (0.9 + SAT_OUT * bump * (0.75 + 0.5 * h))
            sats[i * 4] = cos(angle) * dist
            sats[i * 4 + 1] = sin(angle) * dist
            sats[i * 4 + 2] = r * (0.1 + 0.08 * hash((i * 3 + k * 11 + 5).toDouble())) * min(1.0, bump * 3)
            sats[i * 4 + 3] = SAT_K * min(1.0, bump * 4)
        }
    }
}

/** One semi-implicit step of a damped spring toward x0: returns the new velocity; x += v·dt. */
private inline fun spring(x: Double, v: Double, x0: Double, w: Double, z: Double, dt: Double, out: (Double, Double) -> Unit) {
    val nv = v + (-w * w * (x - x0) - 2 * z * w * v) * dt
    out(x + nv * dt, nv)
}

/** Finger-driven frame: shape springs, the droplet, the wobble running on its own clock. */
internal fun LiquidMetal.liveFrame(dt: Double) {
    for (i in 0 until 3) {
        spring(w[i], v[i], if (i == target) 1.0 else 0.0, SHAPE_W, SHAPE_Z, dt) { x, nv -> w[i] = x; v[i] = nv }
    }
    p1 += 2 * PI * 2 / LOOP * dt
    p2 += 2 * PI * 3 / LOOP * dt
    jiggle *= exp(-6 * dt)
    sats.fill(0.0)
    dropletStep(dt)
}

private fun LiquidMetal.dropletStep(dt: Double) {
    if (!shown) return
    val tx = if (held) fx else hx
    val ty = if (held) fy else hy
    val sw = if (held) HOLD_W else HOME_W
    val sz = if (held) HOLD_Z else HOME_Z
    // Substeps keep the stiff spring stable on a slow frame.
    val n = 4
    repeat(n) {
        spring(dx, dvx, tx, sw, sz, dt / n) { x, nv -> dx = x; dvx = nv }
        spring(dy, dvy, ty, sw, sz, dt / n) { y, nv -> dy = y; dvy = nv }
    }
    if (!held) {
        val far = hypot(dx - hx, dy - hy)
        if (far < 0.6 && hypot(dvx, dvy) < 8) shown = false
    }
}

/** The held droplet's radius: full at home, thinner as it is pulled away. */
internal fun LiquidMetal.dropletRadius(r: Double): Double {
    val dist = hypot(dx - hx, dy - hy)
    return min(max(0.34 * r - dist * 0.09, 0.13 * r), 0.34 * r)
}

internal fun LiquidMetal.beginBack() {
    mode = MODE_BACK
    backAt = now
    for (i in 0 until 3) backW[i] = w[i]
    backP1 = p1
    backP2 = p2
    held = false
}

/** Easing back to the loop's first frame; returns true when done. */
internal fun LiquidMetal.backFrame(dt: Double): Boolean {
    val k = liquidMetalEaseInOut(min((now - backAt) / BACK_FOR, 1.0))
    w[0] = backW[0] + (1 - backW[0]) * k
    w[1] = backW[1] * (1 - k)
    w[2] = backW[2] * (1 - k)
    v.fill(0.0)
    p1 = backP1 + angleTo(backP1, 0.0) * k
    p2 = backP2 + angleTo(backP2, 0.0) * k
    jiggle *= exp(-6 * dt)
    sats.fill(0.0)
    dropletStep(dt)
    if (k >= 1) {
        shown = false
        jiggle = 0.0
        target = 0
        return true
    }
    return false
}

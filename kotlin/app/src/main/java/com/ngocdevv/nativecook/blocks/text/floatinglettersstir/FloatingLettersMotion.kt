package com.ngocdevv.nativecook.blocks.text.floatinglettersstir

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/*
 Floating Letters Stir — where every letter is, as a pure function of the loop time, plus the
 small live simulation a finger drives. A port of floatingLettersMotion.ts, number for number.

 LOOP = 10 s
   0.00 – 0.80   rest: the phrase readable, letters bobbing 1–2 dp on the ambient swell.
   0.80 – 1.20   the ghost finger lands left of the phrase (fade 0.4 s, scale 1.25 → 1).
   1.20 – 4.80   the stir: the finger runs 1.6 clockwise turns of an elliptical spiral around the
                 phrase (radius 1.02 → 0.60 of the phrase box, ease-in-out sine). A ring leaves the
                 finger every 0.5 s (8 rings, each alive 2.8 s, front at 150 dp/s). Each letter is
                 carried by the Gaussian-weighted integral of the finger's velocity (sigma 70 dp),
                 saturating at 130 dp, plus 16–48 dp outward from the middle of the phrase, eased
                 out over 1.6 s from 0.35 s before the finger's weighted closest pass. While adrift
                 the offset keeps turning (0.10–0.22 rad/s), circles a small eddy and the letter
                 spins toward 0.3–1.1 rad.
   4.80 – 5.10   the finger lifts (fade 0.3 s). Letters keep drifting.
   6.30 – 8.35   home: each letter returns on an underdamped spring (zeta 0.62, omega 6.5 rad/s),
                 60 ms apart in reading order, the offset turning by 0.5 rad as it shrinks.
   8.35 – 10.0   rest.

 Everything periodic (swell, caustics) uses whole turns per LOOP, so t = LOOP equals t = 0.
*/

/** Seconds. */
internal const val LOOP = 10f

internal const val GHOST_IN = 0.8f
internal const val STIR_A = 1.2f
internal const val STIR_B = 4.8f
internal const val GHOST_OUT = 5.1f
private const val STIR_TURNS = 1.6f
private const val STIR_PHI0 = (PI * 1.1).toFloat()
private const val STIR_R0 = 1.02f
private const val STIR_R1 = 0.6f

private const val PUSH_SIGMA = 70f
private const val PUSH_GAIN = 0.55f
private const val PUSH_MAX = 130f
private const val PUSH_SPREAD = 16f
private const val PUSH_LEAD = 0.35f
private const val PUSH_RISE = 1.6f

private const val RETURN_AT = 6.3f
private const val RETURN_STAGGER = 0.06f
private const val ZETA = 0.62f
private const val OMEGA = 6.5f
private const val SETTLED = 2.0f

internal const val RIPPLES = 8
private const val RIPPLE_T0 = 1.25f
private const val RIPPLE_EVERY = 0.5f
internal const val RIPPLE_LIFE = 2.8f
internal const val RIPPLE_SPEED = 150f
private const val RIPPLE_LAMBDA = 34f
internal const val RIPPLE_WIDTH = 30f
private const val RIPPLE_K = (2 * PI).toFloat() / RIPPLE_LAMBDA

/** Letters ride the rings: lift in dp per unit height, tilt in rad per unit slope. */
internal const val BOB_LIFT = 3f
internal const val BOB_TILT = 0.05f

private const val TAU_F = (PI * 2).toFloat()

internal fun clamp01(x: Float) = if (x < 0f) 0f else if (x > 1f) 1f else x

internal fun seg(t: Float, a: Float, b: Float) = clamp01((t - a) / (b - a))

private fun easeOutCubic(x: Float): Float {
    val u = 1 - x
    return 1 - u * u * u
}

private fun easeInOutSine(x: Float) = 0.5f - 0.5f * cos(PI.toFloat() * x)

/** The seeded hash of the React Native block, in doubles so the seeds agree. */
internal fun hash(i: Double): Float {
    val s = sin(i * 12.9898 + 78.233) * 43758.5453
    return (s - floor(s)).toFloat()
}

/** Unit step response of the return spring (0 → 1, a little past 1 on the way). */
private fun springStep(tau: Float): Float {
    if (tau <= 0f) return 0f
    if (tau >= SETTLED) return 1f
    val root = sqrt(1 - ZETA * ZETA)
    val wd = OMEGA * root
    val e = exp(-ZETA * OMEGA * tau)
    return 1 - e * (cos(wd * tau) + (ZETA / root) * sin(wd * tau))
}

/** The phrase box the stir circles: centre and half extents, and the screen (dp). */
internal class Geo(val cx: Float, val cy: Float, val hw: Float, val hh: Float, val w: Float, val h: Float)

/** Where the ghost finger is at loop time t, into out[0..1]. Before the stir it waits at the start, after it at the end. */
internal fun stirPos(t: Float, geo: Geo, out: FloatArray) {
    val u = easeInOutSine(seg(t, STIR_A, STIR_B))
    val phi = STIR_PHI0 + TAU_F * STIR_TURNS * u
    val r = STIR_R0 + (STIR_R1 - STIR_R0) * u
    out[0] = geo.cx + geo.hw * r * cos(phi)
    out[1] = geo.cy + geo.hh * r * sin(phi)
}

/** How visible the ghost finger is (0…1). */
internal fun ghostAt(t: Float) = seg(t, GHOST_IN, STIR_A) * (1 - seg(t, STIR_B, GHOST_OUT))

/** What the scripted stir does to one letter: its carry (dp), when the finger passed, and its spin target (rad). */
internal class Push(val px: Float, val py: Float, val tc: Float, val spin: Float)

private const val PUSH_SAMPLES = 180

/** Integrates the scripted finger's velocity near each home. Runs once per layout. */
internal fun makePushes(homeX: FloatArray, homeY: FloatArray, geo: Geo): Array<Push> {
    val step = (STIR_B - STIR_A) / PUSH_SAMPLES
    val a = FloatArray(2)
    val b = FloatArray(2)
    return Array(homeX.size) { i ->
        var sx = 0f
        var sy = 0f
        var sw = 0f
        var st = 0f
        for (s in 0 until PUSH_SAMPLES) {
            val t0 = STIR_A + step * s
            stirPos(t0, geo, a)
            stirPos(t0 + step, geo, b)
            val mx = (a[0] + b[0]) / 2 - homeX[i]
            val my = (a[1] + b[1]) / 2 - homeY[i]
            val w = exp(-(mx * mx + my * my) / (2 * PUSH_SIGMA * PUSH_SIGMA))
            val dx = b[0] - a[0]
            val dy = b[1] - a[1]
            sx += dx * w
            sy += dy * w
            val len = hypot(dx, dy) * w
            sw += len
            st += len * (t0 + step / 2)
        }
        val raw = hypot(sx, sy) * PUSH_GAIN
        val mag = PUSH_MAX * (1 - exp(-raw / PUSH_MAX))
        val k = if (raw > 0.001f) mag / (raw / PUSH_GAIN) else 0f
        val side = if (hash(i * 5.31 + 2) > 0.22f) 1f else -0.45f
        // Water spreads what it carries: a little outward from the middle of the phrase, so letters part.
        val ox = homeX[i] - geo.cx
        val oy = homeY[i] - geo.cy
        val ol = max(1f, hypot(ox, oy))
        val spread = (PUSH_SPREAD + PUSH_SPREAD * 2 * hash(i * 3.9 + 6)) * min(1f, mag / 50)
        Push(
            px = sx * k + (ox / ol) * spread,
            py = sy * k + (oy / ol) * spread * 0.8f,
            tc = if (sw > 0.001f) st / sw else (STIR_A + STIR_B) / 2,
            spin = side * (0.3f + 0.8f * hash(i * 7.77 + 3)) * min(1f, mag / 60),
        )
    }
}

/** dx, dy, rotation (rad), scale of letter i at loop time t, relative to its home, into out[0..3]. */
internal fun autoOffset(t: Float, i: Int, push: Push, out: FloatArray) {
    val start = push.tc - PUSH_LEAD
    if (t <= start) {
        out[0] = 0f; out[1] = 0f; out[2] = 0f; out[3] = 1f
        return
    }
    val h1 = hash(i * 3.17 + 1)
    val h2 = hash(i * 9.13 + 4)
    val ret = RETURN_AT + RETURN_STAGGER * i
    val tau = min(t, ret) - start
    val e = easeOutCubic(clamp01(tau / PUSH_RISE))
    // The carry keeps turning with the vortex; a small eddy rides on it.
    val turn = (0.1f + 0.12f * h1) * tau
    val c = cos(turn)
    val s = sin(turn)
    val eddyR = 5 + 7 * h2
    val eddyA = h1 * TAU_F + (0.9f + 0.7f * h2) * tau
    val ox = e * (push.px * c - push.py * s) + e * eddyR * (cos(eddyA) - cos(h1 * TAU_F))
    val oy = e * (push.px * s + push.py * c) + e * eddyR * (sin(eddyA) - sin(h1 * TAU_F))
    val rot = push.spin * (1 - exp(-tau / 1.4f)) + 0.06f * e * sin(1.7f * tau + i)
    val sc = 1 + 0.03f * e * sin(2.1f * tau + i * 1.3f)
    if (t < ret) {
        out[0] = ox; out[1] = oy; out[2] = rot; out[3] = sc
        return
    }
    val k = 1 - springStep(t - ret)
    val a = 0.5f * k
    val ca = cos(a)
    val sa = sin(a)
    out[0] = k * (ox * ca - oy * sa)
    out[1] = k * (ox * sa + oy * ca)
    out[2] = k * rot
    out[3] = 1 + k * (sc - 1)
}

/** The ambient swell under letter i at ambient time a (period LOOP): dy (dp) and tilt (rad) into out[0..1]. */
internal fun swellAt(a: Float, i: Int, out: FloatArray) {
    val p = TAU_F * a / LOOP
    val f1 = hash(i * 2.11 + 9) * TAU_F
    val f2 = hash(i * 4.57 + 11) * TAU_F
    out[0] = 1.3f * sin(p + f1) + 0.7f * sin(2 * p + f2)
    out[1] = 0.012f * sin(p + f2)
}

/**
 Rings on the water, as a flat list of RIPPLES × [x, y, age (s), strength]; strength 0 = unused.
 The scripted ones leave the ghost finger every RIPPLE_EVERY seconds.
*/
internal fun autoRipples(t: Float, geo: Geo, on: Boolean, out: FloatArray, scratch: FloatArray) {
    for (k in 0 until RIPPLES) {
        val born = RIPPLE_T0 + RIPPLE_EVERY * k
        val age = t - born
        val o = k * 4
        if (on && age > 0 && age < RIPPLE_LIFE) {
            stirPos(born, geo, scratch)
            out[o] = scratch[0]
            out[o + 1] = scratch[1]
            out[o + 2] = age
            out[o + 3] = 0.6f + 0.4f * hash(k * 6.3 + 17)
        } else {
            out[o] = 0f; out[o + 1] = 0f; out[o + 2] = 0f; out[o + 3] = 0f
        }
    }
}

/** The envelope of one ring: its strength, fading in over 0.15 s and out over its life. */
internal fun rippleEnvelope(age: Float, strength: Float) = strength * seg(age, 0f, 0.15f) * (1 - clamp01(age / RIPPLE_LIFE)).pow(1.5f)

/** Height and slope of the rings at a point: h (−1…1), dh/dx, dh/dy (slope divided by the wave number) into out[0..2]. */
internal fun ripplesAt(x: Float, y: Float, rips: FloatArray, out: FloatArray) {
    var h = 0f
    var gx = 0f
    var gy = 0f
    for (k in 0 until RIPPLES) {
        val amp = rips[k * 4 + 3]
        if (amp <= 0f) continue
        val age = rips[k * 4 + 2]
        val dx = x - rips[k * 4]
        val dy = y - rips[k * 4 + 1]
        val d = max(0.001f, hypot(dx, dy))
        val off = d - RIPPLE_SPEED * age
        if (off > RIPPLE_WIDTH * 2.5f || off < -RIPPLE_WIDTH * 2.5f) continue
        val env = rippleEnvelope(age, amp)
        val g = exp(-(off * off) / (RIPPLE_WIDTH * RIPPLE_WIDTH))
        val sn = sin(off * RIPPLE_K)
        val cs = cos(off * RIPPLE_K)
        val dh = env * g * (cs - ((2 * off) / (RIPPLE_WIDTH * RIPPLE_WIDTH * RIPPLE_K)) * sn)
        h += env * g * sn
        gx += (dh * dx) / d
        gy += (dh * dy) / d
    }
    out[0] = h; out[1] = gx; out[2] = gy
}

// ---- Under a real finger: a small live simulation --------------------------------------------

/** Per letter: offset, velocity, rotation and its speed. */
internal class Sim(n: Int) {
    val x = FloatArray(n)
    val y = FloatArray(n)
    val vx = FloatArray(n)
    val vy = FloatArray(n)
    val r = FloatArray(n)
    val vr = FloatArray(n)

    fun reset() {
        x.fill(0f); y.fill(0f); vx.fill(0f); vy.fill(0f); r.fill(0f); vr.fill(0f)
    }
}

private const val FINGER_SIGMA = 95f
private const val FINGER_MAX = 1800f
private const val COUPLE = 9f
private const val DRAG = 2.2f
private const val SOFT_K = 1.2f
private const val FIRM_K = 42f
private const val EDGE = 28f
private const val EDGE_K = 30f

/**
 One step of the live water. While stirring the letters follow the water the finger moves
 (velocity coupling, Gaussian of 95 dp), drift against drag and feel only a faint pull home;
 with `settle` the pull is a critically damped spring. Past the screen's margins a spring
 pushes back (the rubber band). The finger: where it is, how fast it moves (dp/s), whether it is down.
*/
internal fun stepSim(
    s: Sim, homeX: FloatArray, homeY: FloatArray,
    fingerX: Float, fingerY: Float, fingerVx: Float, fingerVy: Float, fingerOn: Boolean,
    geo: Geo, dt: Float, settle: Boolean,
) {
    val firmC = 2 * sqrt(FIRM_K)
    val k = if (settle) FIRM_K else SOFT_K
    val c = if (settle) firmC else DRAG
    val sp = hypot(fingerVx, fingerVy)
    val cap = if (sp > FINGER_MAX) FINGER_MAX / sp else 1f
    val fvx = fingerVx * cap
    val fvy = fingerVy * cap
    for (i in homeX.indices) {
        val px = homeX[i] + s.x[i]
        val py = homeY[i] + s.y[i]
        var ax = -k * s.x[i] - c * s.vx[i]
        var ay = -k * s.y[i] - c * s.vy[i]
        var ar = -(if (settle) FIRM_K else 0.4f) * s.r[i] - (if (settle) firmC else 1.6f) * s.vr[i]
        if (fingerOn && sp > 20) {
            val dx = px - fingerX
            val dy = py - fingerY
            val near = exp(-(dx * dx + dy * dy) / (2 * FINGER_SIGMA * FINGER_SIGMA))
            val pull = COUPLE * (0.04f + near)
            ax += (fvx - s.vx[i]) * pull
            ay += (fvy - s.vy[i]) * pull
            // The water turns around the finger's path: letters beside it are spun by its curl.
            ar += ((dx * fvy - dy * fvx) / 5200) * near
        }
        if (!settle) {
            if (px < EDGE) ax += (EDGE - px) * EDGE_K
            if (px > geo.w - EDGE) ax -= (px - (geo.w - EDGE)) * EDGE_K
            if (py < EDGE * 2) ay += (EDGE * 2 - py) * EDGE_K
            if (py > geo.h - EDGE * 2) ay -= (py - (geo.h - EDGE * 2)) * EDGE_K
        }
        s.vx[i] += ax * dt
        s.vy[i] += ay * dt
        s.vr[i] += ar * dt
        s.x[i] += s.vx[i] * dt
        s.y[i] += s.vy[i] * dt
        s.r[i] += s.vr[i] * dt
    }
}

/** How far from rest the live water is (dp-like units). */
internal fun simEnergy(s: Sim): Float {
    var e = 0f
    for (i in s.x.indices) {
        val v = abs(s.x[i]) + abs(s.y[i]) + (abs(s.vx[i]) + abs(s.vy[i])) * 0.05f + abs(s.r[i]) * 30
        if (v > e) e = v
    }
    return e
}

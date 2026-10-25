package com.ngocdevv.nativecook.blocks.navigation.magnetsnapdock

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/*
 The dock's magnetism, the scripted finger and the selection, as functions of time (same maths
 as magnetSnapDockMotion.ts).

 Magnet layout: each icon is pulled toward the finger with a Gaussian falloff (130 dp); the icon
 nearest the finger in x grows to 1.35×, its neighbours less; four relaxation passes along x push
 overlapping icons apart. Strength s (0–1) is how present the finger is.

 autoPlay (LOOP = 8 s): rest on Pen 0–1.4; a finger glides down toward the dock 1.4–2.2, sweeps
 left → right 2.2–3.8, comes back to Brush and lowers 3.8–4.6, presses and releases at 5.0 (Brush
 clicks onto the plate, the plate slides, the panel swaps); a short second touch on Pen at
 6.25–6.75 swaps back; rest 7.4–8.0. In autoPlay the icons follow the finger through a short
 weighted history of the layout (now and 50/100/150 ms ago), a pure function of t.
 */

const val LOOP = 8.0
internal const val MAGNET_TOOLS = 6
internal const val MAGNET_ICON = 48f
internal const val MAGNET_DOCK_H = 84f
internal const val MAGNET_REST_TOOL = 1
private const val PULL_RADIUS = 130.0
private const val GROW = 0.35

internal class MagnetGeo(val w: Float, val h: Float, top: Float, bottom: Float) {
    val dockW = min(w - 24f, 372f)
    val dockX = (w - dockW) / 2f
    val dockY = h - max(bottom, 12f) - 12f - MAGNET_DOCK_H
    /** Rest centres of the icons. */
    val xs = DoubleArray(MAGNET_TOOLS) { dockX + 10.0 + (dockW - 20.0) / MAGNET_TOOLS * (it + 0.5) }
    /** The dock's centre line. */
    val cy = (dockY + MAGNET_DOCK_H / 2f).toDouble()
    val panelH = 196f
    val panelTop = dockY - 16f - panelH
    val boardTop = top + 100f
}

internal fun magnetSeg(t: Double, a: Double, b: Double) = ((t - a) / (b - a)).coerceIn(0.0, 1.0)

internal fun magnetEaseInOut(x: Double) = if (x < 0.5) 4 * x * x * x else 1 - (-2 * x + 2).pow(3) / 2

private fun easeOut(x: Double) = 1 - (1 - x).pow(3)

internal fun magnetSpringStep(tau: Double, w: Double, z: Double): Double {
    if (tau <= 0) return 0.0
    val wd = w * sqrt(1 - z * z)
    return 1 - exp(-z * w * tau) * (cos(wd * tau) + (z * w / wd) * sin(wd * tau))
}

private fun mix(a: Double, b: Double, k: Double) = a + (b - a) * k

/** The scripted finger into [out]: x, y, strength 0–1, ring alpha 0–1. */
internal fun magnetLoopFinger(g: MagnetGeo, t: Double, out: DoubleArray) {
    val hover = g.dockY - 62.0
    val board = g.boardTop + 120.0
    var x = g.w * 0.62
    var y = board
    var s = 0.0
    var ring = 0.0
    if (t >= 1.4 && t < 5.4) {
        val a = easeOut(magnetSeg(t, 1.4, 2.2))
        x = mix(g.w * 0.62, g.xs[0] - 4, a)
        y = mix(board, hover, a)
        if (t >= 2.2) {
            val b = magnetEaseInOut(magnetSeg(t, 2.2, 3.8))
            x = mix(g.xs[0] - 4, g.xs[5] + 4, b)
            y = hover + 12 * sin(PI * b)
        }
        if (t >= 3.8) {
            val c = magnetEaseInOut(magnetSeg(t, 3.8, 4.6))
            x = mix(g.xs[5] + 4, g.xs[2], c)
            y = mix(hover, g.dockY - 26.0, c)
        }
        if (t >= 4.6) {
            val d = magnetEaseInOut(magnetSeg(t, 4.6, 4.9))
            x = g.xs[2]
            y = mix(g.dockY - 26.0, g.cy, d)
        }
        val on = easeOut(magnetSeg(t, 1.4, 1.65))
        val off = easeOut(magnetSeg(t, 5.0, 5.35))
        s = on * (1 - off)
        ring = s
    } else if (t >= 6.2 && t < 7.2) {
        val a = magnetEaseInOut(magnetSeg(t, 6.25, 6.6))
        x = mix(g.xs[1] + 18, g.xs[1], a)
        y = mix(g.dockY - 30.0, g.cy, a)
        s = 0.75 * easeOut(magnetSeg(t, 6.25, 6.4)) * (1 - easeOut(magnetSeg(t, 6.75, 7.1)))
        ring = s / 0.75
    }
    out[0] = x; out[1] = y; out[2] = s; out[3] = ring
}

/** The selection at loop time t into [out]: from, to, time of the snap. */
internal fun magnetLoopSelection(t: Double, out: DoubleArray) {
    when {
        t >= 6.75 -> { out[0] = 2.0; out[1] = 1.0; out[2] = 6.75 }
        t >= 5.0 -> { out[0] = 1.0; out[1] = 2.0; out[2] = 5.0 }
        else -> { out[0] = MAGNET_REST_TOOL.toDouble(); out[1] = MAGNET_REST_TOOL.toDouble(); out[2] = -10.0 }
    }
}

/** Where each icon wants to be for a finger at (fx, fy) with strength s: fills x, y, scale and the pull f. */
internal fun magnetLayout(g: MagnetGeo, fx: Double, fy: Double, s: Double, x: DoubleArray, y: DoubleArray, scale: DoubleArray, f: DoubleArray) {
    val above = max(0.0, g.cy - fy - 24)
    val nearY = exp(-(above / 150) * (above / 150))
    for (i in 0 until MAGNET_TOOLS) {
        val dx = fx - g.xs[i]
        val dy = fy - g.cy
        val d = hypot(dx, dy)
        val pull = s * exp(-(d / PULL_RADIUS) * (d / PULL_RADIUS))
        f[i] = pull
        x[i] = g.xs[i] + dx * 0.16 * pull
        y[i] = g.cy + max(-30.0, dy * 0.2 * pull)
        scale[i] = 1 + GROW * s * nearY * exp(-(dx / 62) * (dx / 62))
    }
    // Neighbours are pushed apart so the grown icons never overlap.
    repeat(4) {
        for (i in 0 until MAGNET_TOOLS - 1) {
            val need = MAGNET_ICON * (scale[i] + scale[i + 1]) / 2 + 6
            val gap = x[i + 1] - x[i] - need
            if (gap < 0) {
                x[i] += gap / 2
                x[i + 1] -= gap / 2
            }
        }
    }
}

/** Scratch arrays for [magnetLoopLayout], so a frame allocates nothing. */
internal class MagnetScratch {
    val lx = DoubleArray(MAGNET_TOOLS)
    val ly = DoubleArray(MAGNET_TOOLS)
    val ls = DoubleArray(MAGNET_TOOLS)
    val lf = DoubleArray(MAGNET_TOOLS)
    val fp = DoubleArray(4)
}

/** The loop's icon layout, with a short weighted history for follow-through. The finger goes into [now]. */
internal fun magnetLoopLayout(g: MagnetGeo, t: Double, x: DoubleArray, y: DoubleArray, scale: DoubleArray, f: DoubleArray, now: DoubleArray, k: MagnetScratch) {
    magnetLoopFinger(g, t, now)
    x.fill(0.0); y.fill(0.0); scale.fill(0.0)
    magnetLayout(g, now[0], now[1], now[2], k.lx, k.ly, k.ls, f)
    var nearest = 0
    for (i in 1 until MAGNET_TOOLS) if (abs(g.xs[i] - now[0]) < abs(g.xs[nearest] - now[0])) nearest = i
    for (step in 0 until 4) {
        if (step > 0) {
            magnetLoopFinger(g, max(0.0, t - step * 0.05), k.fp)
            magnetLayout(g, k.fp[0], k.fp[1], k.fp[2], k.lx, k.ly, k.ls, k.lf)
        }
        for (i in 0 until MAGNET_TOOLS) {
            // Further from the finger, the more the icon lags (weights over now, −50, −100, −150 ms).
            val lag = min(1.0, abs(i - nearest) / 3.0)
            val wgt = when (step) {
                0 -> 0.55 - 0.25 * lag
                1 -> 0.25
                2 -> 0.13 + 0.12 * lag
                else -> 0.07 + 0.13 * lag
            }
            x[i] += k.lx[i] * wgt
            y[i] += k.ly[i] * wgt
            scale[i] += k.ls[i] * wgt
        }
    }
}

/** The click of a tool landing on the plate: y offset (dp) at τ after the snap. */
internal fun magnetClickY(tau: Double): Double {
    if (tau < 0 || tau > 1.2) return 0.0
    return -6 * (1 - magnetSpringStep(tau, 20.0, 0.45))
}

/** The click's scale at τ after the snap. */
internal fun magnetClickScale(tau: Double): Double {
    if (tau < 0 || tau > 1.2) return 1.0
    return 1 + 0.08 * (1 - magnetSpringStep(tau, 20.0, 0.45))
}

/** The plate's x for a selection from → to snapped at `at`, at time t. */
internal fun magnetPlateX(g: MagnetGeo, from: Int, to: Int, at: Double, t: Double) =
    mix(g.xs[from], g.xs[to], magnetSpringStep(t - at, 14.0, 0.62))

/** The panel swap's progress (0 → 1). */
internal fun magnetSwapAt(from: Int, to: Int, at: Double, t: Double): Double {
    if (from == to) return 1.0
    return magnetEaseInOut(magnetSeg(t - at, 0.1, 0.7))
}

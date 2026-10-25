package com.ngocdevv.nativecook.blocks.sheets.drawercabinetsheet

import androidx.compose.animation.core.CubicBezierEasing
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/*
 Drawer Cabinet Sheet — the drawer's travel and the things rattling in it (the React Native
 drawerMotion.ts).

 The drawer's opening (dp pulled out of the cabinet) is a pure function of the loop time. Every
 item rides in its compartment on a spring along the drawer's travel (and a little turn): when the
 drawer accelerates the item lags, hits the divider's slack, bounces and settles. Under autoPlay
 the items' response to the scripted travel is integrated once into a table (240 samples a second
 over one loop, starting and ending at rest), so each frame is a lookup — deterministic, and the
 frame at t = LOOP is the frame at 0. Under touch the same spring is stepped live with the real
 drawer's acceleration.

 LOOP = 8 s: rest 0–1.2 (closed) · pulled to half 1.2–2.2 · to full 2.8–3.6 · the blue pen picked
 4.0–5.0 (lifts 0.25 s, flies 0.6 s, lands) · soft close 5.6–7.0 (fast, then the damper eases it
 home over the last few centimetres) · the desk clears 7.0–7.5 · rest.
*/

internal const val LOOP = 8.0
internal const val DRAWER_PICK_AT = 4.0
internal const val DRAWER_LIFT_FOR = 0.25
internal const val DRAWER_FLY_FOR = 0.6
private const val CLOSE_AT = 5.6
private const val CLOSE_FOR = 1.4
internal const val DRAWER_CLEAR_AT = 7.0
/** Samples per second of the autoPlay table. */
internal const val DRAWER_RATE = 240

internal fun drawerClamp01(v: Double) = if (v < 0) 0.0 else if (v > 1) 1.0 else v

internal fun drawerEaseInOutCubic(v: Double): Double {
    val x = drawerClamp01(v)
    return if (x < 0.5) 4 * x * x * x else 1 - (-2 * x + 2).pow(3) / 2
}

internal fun drawerEaseOutCubic(v: Double) = 1 - (1 - drawerClamp01(v)).pow(3)

/** The soft-close damper's curve: cubic-bezier(0.12, 0.8, 0.2, 1). */
private val SOFT = CubicBezierEasing(0.12f, 0.8f, 0.2f, 1f)

internal fun drawerSoft(u: Double) = SOFT.transform(drawerClamp01(u).toFloat()).toDouble()

/** A finger's pull settling into a detent: a spring response (ω rad/s, ζ) from 0 to 1. */
private fun settle(tau: Double, w: Double, z: Double): Double {
    if (tau <= 0) return 0.0
    val wd = w * sqrt(1 - z * z)
    return 1 - exp(-z * w * tau) * (cos(wd * tau) + (z * w / wd) * sin(wd * tau))
}

/** Soft close: pushed at speed, then the damper takes the last stretch slowly (ease-out quint). */
private fun softClose(u: Double) = 1 - (1 - drawerClamp01(u)).pow(5)

/** The drawer's opening in the loop, for detents [half] and [full]. */
internal fun drawerAutoOpen(t: Double, half: Double, full: Double): Double {
    if (t < 1.2) return 0.0
    if (t < 2.8) return half * settle(t - 1.2, 8.0, 0.72)
    if (t < CLOSE_AT) return half + (full - half) * settle(t - 2.8, 9.0, 0.68)
    return full * (1 - softClose((t - CLOSE_AT) / CLOSE_FOR))
}

internal class DrawerSpring(
    /** Room to move along the drawer's travel either way (dp) and how hard it bounces off a divider. */
    val slack: Double,
    val bounce: Double,
    /** Spring of the felt / friction holding it near its place (ω rad/s, ζ), and how much it turns. */
    val w: Double,
    val z: Double,
    val turn: Double,
)

/**
 * One step of an item's spring along the travel: [a] is the drawer's acceleration (dp/s², + =
 * moving out faster, toward the top of the screen); the item lags behind it (+y in the drawer =
 * toward the back). state = [y, vy].
 */
internal fun drawerStepItem(s: DrawerSpring, state: DoubleArray, a: Double, dt: Double) {
    val acc = -s.w * s.w * state[0] - 2 * s.z * s.w * state[1] + a * 0.9
    state[1] += acc * dt
    state[0] += state[1] * dt
    if (state[0] > s.slack) {
        state[0] = s.slack
        if (state[1] > 0) state[1] = -state[1] * s.bounce
    } else if (state[0] < -s.slack) {
        state[0] = -s.slack
        if (state[1] < 0) state[1] = -state[1] * s.bounce
    }
}

/** The autoPlay table: per item, its lag at DRAWER_RATE samples a second over one loop. */
internal fun drawerBuildTable(springs: List<DrawerSpring>, half: Double, full: Double): Array<DoubleArray> {
    val n = (LOOP * DRAWER_RATE).roundToInt()
    val dt = 1.0 / DRAWER_RATE
    val ys = Array(springs.size) { DoubleArray(n + 1) }
    val states = Array(springs.size) { DoubleArray(2) }
    // Drawer acceleration by central differences of the scripted opening (+ = outward).
    fun open(t: Double) = drawerAutoOpen(max(0.0, min(LOOP, t)), half, full)
    for (i in 0..n) {
        val t = i * dt
        val a = (open(t + dt) - 2 * open(t) + open(t - dt)) / (dt * dt)
        for (k in springs.indices) {
            drawerStepItem(springs[k], states[k], a, dt)
            ys[k][i] = states[k][0]
        }
    }
    // The table must close the loop exactly: ease any last residue to 0 over the final second.
    for (k in springs.indices) {
        for (i in n - DRAWER_RATE..n) ys[k][i] *= 1 - drawerEaseInOutCubic((i - (n - DRAWER_RATE)).toDouble() / DRAWER_RATE)
    }
    return ys
}

/** An item's lag at loop time t from the table (linear between samples). */
internal fun drawerTableAt(ys: DoubleArray, t: Double): Double {
    val f = drawerClamp01(t / LOOP) * (ys.size - 1)
    val i = floor(f).toInt()
    val j = min(ys.size - 1, i + 1)
    return ys[i] + (ys[j] - ys[i]) * (f - i)
}

/**
 * A pick: from its place in the drawer the item lifts (0.25 s, eases toward the camera), flies in
 * an arc to its place on the desk (0.6 s, ease-in-out) and lands with a small bounce. Writes
 * [x, y, rot, lift, scale] into out; [u] is seconds since the pick began.
 */
internal fun drawerPickPose(fx: Double, fy: Double, frot: Double, tx: Double, ty: Double, trot: Double, u: Double, out: DoubleArray): DoubleArray {
    if (u <= 0) {
        out[0] = fx; out[1] = fy; out[2] = frot; out[3] = 0.0; out[4] = 1.0
        return out
    }
    if (u < DRAWER_LIFT_FOR) {
        val k = drawerEaseOutCubic(u / DRAWER_LIFT_FOR)
        out[0] = fx; out[1] = fy - 6 * k; out[2] = frot * (1 - 0.4 * k); out[3] = k; out[4] = 1 + 0.1 * k
        return out
    }
    val v = (u - DRAWER_LIFT_FOR) / DRAWER_FLY_FOR
    if (v < 1) {
        val s = drawerEaseInOutCubic(v)
        val arc = 60 * sin(PI * s)
        out[0] = fx + (tx - fx) * s
        out[1] = fy - 6 + (ty - fy + 6) * s - arc
        out[2] = frot * 0.6 * (1 - s) + trot * s + 0.12 * sin(PI * s)
        out[3] = 1 - 0.5 * max(0.0, s - 0.6) / 0.4
        out[4] = 1 + 0.1 * out[3]
        return out
    }
    val b = u - DRAWER_LIFT_FOR - DRAWER_FLY_FOR
    val bounce = if (b < 0.5) exp(-b * 10) * sin(b * 34) else 0.0
    val settleLift = max(0.0, 0.5 * (1 - b / 0.12))
    out[0] = tx; out[1] = ty; out[2] = trot; out[3] = settleLift; out[4] = 1 + 0.05 * settleLift - 0.03 * bounce
    return out
}

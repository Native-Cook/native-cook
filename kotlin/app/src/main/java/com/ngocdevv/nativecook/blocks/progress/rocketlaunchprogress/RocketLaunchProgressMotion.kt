package com.ngocdevv.nativecook.blocks.progress.rocketlaunchprogress

import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sin

/*
 Rocket Launch Progress — the deploy as pure functions of the loop time t (seconds), as in the
 React Native rocketMotion.ts.

 LOOP = 10 s: rest on the pad 0–1.4 · fuelling 1.4–4.4 (Build 0–25 %, Test –55 %, Upload –85 %,
 Launch prep –100 %, each stage easing in and out so the gauge breathes between steps) ·
 countdown 3-2-1 4.4–5.6 · ignition 5.6–6.2 (flame, smoke, the pad shakes) · lift-off 6.2–7.6
 (accelerating, pitching over to the right, a contrail behind) · deployed 7.6–8.4 · a new rocket
 rolls onto the pad 8.4–9.4 while the tank vents and the steps reset · rest 9.4–10.
*/

internal const val LOOP = 10.0
internal const val ROCKET_FUEL_AT = 1.4
internal const val ROCKET_COUNT_AT = 4.4
internal const val ROCKET_IGNITE_AT = 5.6
internal const val ROCKET_LIFT_AT = 6.2
internal const val ROCKET_DONE_AT = 7.6
internal const val ROCKET_ROLL_AT = 8.4
private const val ROLL_FOR = 1.0

/** Fuel stages: start, end, from %, to %. */
private val STAGES = arrayOf(
    doubleArrayOf(1.4, 2.1, 0.0, 25.0),
    doubleArrayOf(2.1, 3.0, 25.0, 55.0),
    doubleArrayOf(3.0, 3.8, 55.0, 85.0),
    doubleArrayOf(3.8, 4.4, 85.0, 100.0),
)

/** Fuel % at which each step is done (Launch is done when the rocket is away). */
private val STEP_DONE = doubleArrayOf(25.0, 55.0, 85.0, 101.0)

internal fun rocketClamp01(v: Double) = if (v < 0) 0.0 else if (v > 1) 1.0 else v

internal fun rocketEaseInOutCubic(v: Double): Double {
    val x = rocketClamp01(v)
    return if (x < 0.5) 4 * x * x * x else 1 - (-2 * x + 2).pow(3) / 2
}

internal fun rocketEaseOutCubic(v: Double) = 1 - (1 - rocketClamp01(v)).pow(3)

/** easeOutBack: a 4 % overshoot for things that land (a rocket rolling to a stop, a badge). */
internal fun rocketEaseOutBack(v: Double): Double {
    val x = rocketClamp01(v)
    val c1 = 0.9
    val c3 = c1 + 1
    return 1 + c3 * (x - 1).pow(3) + c1 * (x - 1).pow(2)
}

/** The deploy's progress (0–100) in the loop: filled by 4.4, held, back to 0 while the tank vents. */
internal fun rocketAutoFuel(t: Double): Double {
    if (t >= ROCKET_DONE_AT) return 100 * (1 - rocketEaseInOutCubic((t - ROCKET_DONE_AT) / 0.8))
    var f = 0.0
    for (s in STAGES) {
        if (t >= s[0]) f = s[2] + (s[3] - s[2]) * rocketEaseInOutCubic((t - s[0]) / (s[1] - s[0]))
    }
    return f
}

/** Engine: 0 off → 1 full. Lights at 5.6 with a stutter, full by 6.2, out of sight after 7.6. */
internal fun rocketThrottle(t: Double): Double {
    if (t < ROCKET_IGNITE_AT || t > ROCKET_ROLL_AT) return 0.0
    val k = t - ROCKET_IGNITE_AT
    val stutter = 0.12 * sin(k * 60) * exp(-k * 5)
    return rocketClamp01(rocketEaseOutCubic(k / 0.6) + stutter)
}

/** How hard the camera shakes: building with the engine, peaking at lift-off, gone by 7.3. */
internal fun rocketShake(t: Double) =
    rocketEaseOutCubic((t - ROCKET_IGNITE_AT) / 0.6) * (1 - rocketEaseOutCubic((t - 6.4) / 0.9))

internal class RocketPose(var x: Double = 0.0, var y: Double = 0.0, var rot: Double = 0.0, var on: Boolean = true)

/** The rocket's base (nozzle exit) and pitch, relative to its place on the pad. */
internal fun rocketPose(t: Double, out: RocketPose): RocketPose {
    out.on = true
    if (t < ROCKET_LIFT_AT) {
        out.x = 0.0; out.y = 0.0; out.rot = 0.0
        return out
    }
    if (t < ROCKET_ROLL_AT) {
        val k = t - ROCKET_LIFT_AT
        // A slow first metre, then ~1100 dp/s²; it pitches over to the right as it climbs.
        out.y = -(550 * k * k + 18 * k * k * k * k * k)
        out.x = 70 * k.pow(3)
        out.rot = atan2(210 * k * k, 1100 * k + 30) * 0.9
        out.on = t < ROCKET_DONE_AT + 0.2
        return out
    }
    // The next rocket rolls in from the left on its transporter and stops with a small rock.
    val p = rocketEaseOutBack((t - ROCKET_ROLL_AT) / ROLL_FOR)
    out.x = -420 * (1 - p)
    out.y = 0.0
    out.rot = -0.03 * sin(PI * rocketClamp01((t - ROCKET_ROLL_AT) / ROLL_FOR)) * (1 - p)
    return out
}

/** Countdown digit k (3, 2, 1 → 0, 1, 2) at t: offset (dp, + = below). */
internal fun rocketCountY(k: Int, t: Double): Double {
    val a = ROCKET_COUNT_AT + k * 0.4
    val b = a + 0.4
    if (t < a - 0.01 || t > b + 0.2) return 30.0
    val inn = rocketEaseOutCubic((t - a) / 0.16)
    val out = rocketEaseOutCubic((t - b + 0.04) / 0.16)
    return 30 * (1 - inn) - 40 * out
}

/** Countdown digit k's opacity at t. */
internal fun rocketCountO(k: Int, t: Double): Double {
    val a = ROCKET_COUNT_AT + k * 0.4
    val b = a + 0.4
    if (t < a - 0.01 || t > b + 0.2) return 0.0
    val inn = rocketEaseOutCubic((t - a) / 0.16)
    val out = rocketEaseOutCubic((t - b + 0.04) / 0.16)
    return inn * (1 - out)
}

/** Countdown digit k's scale at t. */
internal fun rocketCountS(k: Int, t: Double): Double {
    val a = ROCKET_COUNT_AT + k * 0.4
    val b = a + 0.4
    if (t < a - 0.01 || t > b + 0.2) return 1.0
    return 0.92 + 0.08 * rocketEaseOutCubic((t - a) / 0.16)
}

/** The "Deployed" badge's scale: in at 7.6 with a little overshoot. */
internal fun rocketBadgeS(t: Double) = 0.9 + 0.1 * rocketEaseOutBack((t - ROCKET_DONE_AT) / 0.4)

/** The badge's opacity: in at 7.6, out at 8.4. */
internal fun rocketBadgeO(t: Double) =
    rocketClamp01((t - ROCKET_DONE_AT) / 0.2) * (1 - rocketEaseOutCubic((t - ROCKET_ROLL_AT) / 0.3))

/**
 * Step i's state: 0 waiting, 1 running, 2 done; from the fuel, and for Launch from the flight.
 * All steps clear while the next rocket rolls in.
 */
internal fun rocketStepState(i: Int, t: Double, fuel: Double): Int {
    if (t >= ROCKET_ROLL_AT + 0.2) return 0
    if (i == 3) return if (t >= ROCKET_DONE_AT) 2 else if (fuel >= 85 || t >= ROCKET_COUNT_AT) 1 else 0
    if (fuel >= STEP_DONE[i] || t >= ROCKET_DONE_AT) return 2
    val start = if (i == 0) 0.01 else STEP_DONE[i - 1]
    return if (fuel >= start && (fuel > 0 || t >= ROCKET_FUEL_AT)) 1 else 0
}

/** The status line's phases. The last repeats the first, so the frame at t = LOOP is the frame at 0. */
internal val RocketStatus = listOf(
    "Ready on the pad", "Fuelling · build, test, upload", "T-minus", "Lift-off", "Live in production", "Next build rolling in", "Ready on the pad",
)
private val STATUS_AT = doubleArrayOf(0.0, ROCKET_FUEL_AT, ROCKET_COUNT_AT, ROCKET_IGNITE_AT, ROCKET_DONE_AT, ROCKET_ROLL_AT, ROCKET_ROLL_AT + ROLL_FOR)

/** Status line i's offset (dp, + = below) at t: in from below over 0.3 s, out upward. */
internal fun rocketStatusY(i: Int, t: Double): Double {
    val a = STATUS_AT[i]
    val b = if (i + 1 < STATUS_AT.size) STATUS_AT[i + 1] else 1e9
    val inn = if (i == 0) 1.0 else rocketEaseOutCubic((t - a) / 0.3)
    val out = rocketEaseOutCubic((t - b) / 0.3)
    if (t < a && i != 0) return 14.0
    return 14 * (1 - inn) - 14 * out
}

/** Status line i's opacity at t. */
internal fun rocketStatusO(i: Int, t: Double): Double {
    val a = STATUS_AT[i]
    val b = if (i + 1 < STATUS_AT.size) STATUS_AT[i + 1] else 1e9
    val inn = if (i == 0) 1.0 else rocketEaseOutCubic((t - a) / 0.3)
    val out = rocketEaseOutCubic((t - b) / 0.3)
    if (t < a && i != 0) return 0.0
    return inn * (1 - out)
}

package com.ngocdevv.nativecook.blocks.pickers.skytimescrub

import com.ngocdevv.nativecook.blocks.pickers.skytimescrub.SkyTimeScrubTheme as Theme
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

/** One solar day, minutes since local midnight. */
internal class SkyTimeScrubSolar(val sunrise: Double, val noon: Double, val sunset: Double, val maxElevation: Double)

/** A solar day prepared for the loop (`makeDay`). */
internal class SkyTimeScrubDay(val sunrise: Double, val noon: Double, val sunset: Double, val maxElevation: Double, val lift: Double, val night: Double)

internal class SkyTimeScrubTimeline(
    val day: SkyTimeScrubDay,
    val now: Double,
    /** Unwrapped minutes at the loop's keyframes: now, first horizon, deep night/day, second horizon, now a day later. */
    val k: DoubleArray,
    /** Fixed-rate pre-integrated exposure (EV) response. */
    val ev: DoubleArray,
    /** Loop times at which the scrub crosses each schedule row's time. */
    val ticks: Array<DoubleArray>,
)

internal class SkyTimeScrubPose(
    val minutes: Double, val ev: Double, val held: Double, val wake: Double, val tick0: Double, val tick1: Double, val tick2: Double,
)

internal class SkyTimeScrubSpring(val damping: Double, val stiffness: Double, val mass: Double)

/** The RN block's motion table (`sts-motion.ts`): every number, curve and the loop's keyframes. */
internal object SkyTimeScrubMotion {
    const val LOOP = 10.0
    const val REF_MAX_ELEVATION = 62.0
    const val NIGHT_DEPTH = 45.0
    val DEMO_SOLAR = SkyTimeScrubSolar(372.0, 751.0, 1098.0, 62.0)
    const val DEMO_NOW = 1086.0

    const val T_PRESS = 0.6
    const val T_SCRUB = 0.9
    const val T_SUNSET = 1.7
    const val T_HOLD = 2.7
    const val T_NIGHT = 3.5
    const val T_DAWN = 4.1
    const val T_SUNRISE = 5.6
    const val T_NOW = 7.6
    const val T_REST = 8.0

    val SNAP = SkyTimeScrubSpring(30.5, 322.3, 1.0)
    val GLIDE = SkyTimeScrubSpring(22.1, 157.9, 1.0)
    val DETENT = SkyTimeScrubSpring(22.0, 260.0, 1.0)

    const val MINUTES_PER_POINT = 2.5
    const val LONG_PRESS = 0.28
    const val SLOP = 8.0
    const val RESUME_AFTER = 2.0
    const val RETURN_DURATION = 0.6
    const val DECAY = 6.018
    const val DETENT_DISTANCE = 35.0
    const val CAPTURE_SPEED = 200.0
    const val MAX_SPEED = 3600.0
    const val EXPOSURE_UP = 0.18
    const val EXPOSURE_DOWN = 0.6
    const val STEP = 15.0
    const val HAPTIC_GAP = 0.1
    const val HOLD_RISE = 0.08
    const val HOLD_FALL = 0.12
    const val MAX_FRAME_DELTA = 0.05
    const val STOP_SPEED = 2.0
    const val HIDDEN_THRESHOLD = 0.001
    const val GRAB_RADIUS = 24.0

    const val ROWS_MAX = 0.6
    const val ROWS_RISE = 1.0
    const val ROWS_FALL = 2.0
    const val ROWS_STAGGER = 0.04
    const val ROWS_FADE = 0.22
    const val ROWS_REST = 0.55
    const val ROWS_TICK = 0.3
    const val ROWS_TICK_SCALE = 0.04
    const val ROWS_CROSSING = 0.75
    const val ROWS_RELEASE = 7.8

    const val SUN_RADIUS = 7.7
    const val SUN_DIP = 4.0
    const val SUN_SCALE = 103.0
    const val SUN_FLATTEN = 0.72

    const val HINT_LEAVE = 0.35
    const val HINT_ARRIVE = 0.6
    const val HINT_BACK = 8.1

    fun clamp(v: Double, lo: Double = 0.0, hi: Double = 1.0) = min(hi, max(lo, v))
    fun wrapMinutes(v: Double) = ((v % 1440) + 1440) % 1440
    fun smooth(v: Double): Double {
        val x = clamp(v)
        return x * x * x * (x * (x * 6 - 15) + 10)
    }
    fun mix(a: Double, b: Double, p: Double) = a + (b - a) * p

    fun makeDay(solar: SkyTimeScrubSolar): SkyTimeScrubDay {
        val rise = min(max(solar.sunrise, 1.0), 1437.0)
        val set = min(max(solar.sunset, rise + 2), 1439.0)
        val noon = min(max(solar.noon, rise + 1), set - 1)
        val maxElevation = min(89.0, max(1.0, solar.maxElevation))
        return SkyTimeScrubDay(
            rise, noon, set, maxElevation,
            sin(REF_MAX_ELEVATION * PI / 180) / sin(maxElevation * PI / 180),
            set + 71,
        )
    }

    class Detent(val value: Double, val distance: Double)

    fun nearestDetent(m: Double, detents: DoubleArray): Detent {
        var best = m
        var distance = 1e9
        for (d in detents) {
            val candidate = d + Math.round((m - d) / 1440.0) * 1440.0
            if (abs(candidate - m) < distance) {
                best = candidate
                distance = abs(candidate - m)
            }
        }
        return Detent(best, distance)
    }

    /** Day fraction along the dotted arch: 0 at sunrise, 0.5 at noon, 1 at sunset, continuing past both ends at the same rate. */
    fun dayParam(minutes: Double, day: SkyTimeScrubDay): Double {
        val m = wrapMinutes(minutes)
        return if (m <= day.noon) ((m - day.sunrise) / (day.noon - day.sunrise)) * 0.5
        else 0.5 + ((m - day.noon) / (day.sunset - day.noon)) * 0.5
    }

    fun minutesFromDayParam(d: Double, day: SkyTimeScrubDay) =
        if (d <= 0.5) day.sunrise + (d / 0.5) * (day.noon - day.sunrise) else day.noon + ((d - 0.5) / 0.5) * (day.sunset - day.noon)

    fun minutesPerDay(d: Double, day: SkyTimeScrubDay) = if (d <= 0.5) 2 * (day.noon - day.sunrise) else 2 * (day.sunset - day.noon)

    class SolarState(val elevation: Double, val x: Double, val day: Double)

    fun solarAt(minutes: Double, day: SkyTimeScrubDay): SolarState {
        val m = wrapMinutes(minutes)
        val d = dayParam(m, day)
        val x = 86 + 230 * d
        val elevation: Double
        if (m >= day.sunrise && m <= day.sunset) {
            elevation = day.maxElevation * sin(PI * clamp(d))
        } else {
            val length = 1440 - (day.sunset - day.sunrise)
            val n = (if (m > day.sunset) m - day.sunset else m + 1440 - day.sunset) / length
            elevation = -NIGHT_DEPTH * sin(PI * clamp(n))
        }
        return SolarState(elevation, x, d)
    }

    /** The disc's centre height above the horizon in points: the arch's own scale, a dip for refraction near the horizon. */
    fun sunHeight(elevation: Double, day: SkyTimeScrubDay, projection: Double): Double {
        val sy = sin(elevation * PI / 180)
        return SUN_SCALE * max(sy * day.lift, -0.5) * projection + SUN_DIP * exp(-(elevation / 3).pow(2))
    }

    fun sunSemiHeight(elevation: Double): Double {
        val x = clamp(elevation / 2)
        return SUN_RADIUS * mix(SUN_FLATTEN, 1.0, x * x * (3 - 2 * x))
    }

    /** Source arch height at x (reference points). */
    fun archY(x: Double, centre: Double, amplitude: Double): Double {
        val q = clamp((x - 201) / 201, -1.0, 1.0)
        val q2 = q * q
        val exponent = 1.75 - 0.75 * q2 + 1.63 * q2 * q2
        val base = max(0.0001, cos(q * 1.57079633))
        return centre + amplitude - 2 * amplitude * base.pow(exponent)
    }

    fun evTargetAt(elevation: Double) = mix(-0.35, 0.9, smooth((-elevation + 1) / 15))
    fun targetEV(minutes: Double, day: SkyTimeScrubDay) = evTargetAt(solarAt(minutes, day).elevation)
    fun circularDiff(a: Double, b: Double) = abs(((((a - b + 720) % 1440) + 1440) % 1440) - 720)

    private fun keyframes(day: SkyTimeScrubDay, now: Double): DoubleArray {
        val events = ArrayList<Double>()
        for (k in 0 until 3) {
            events.add(day.sunrise + 1440 * k)
            events.add(day.sunset + 1440 * k)
        }
        events.sort()
        val next = events.filter { it > now + 0.5 }
        val c1 = next[0]
        val c2 = next[1]
        val mid = min(c1 + 172, (c1 + c2) / 2)
        return doubleArrayOf(now, c1, mid, c2, now + 1440)
    }

    fun minutesAt(t: Double, k: DoubleArray): Double {
        if (t <= T_SCRUB) return k[0]
        if (t >= T_NOW) return k[4]
        if (t < T_SUNSET) return mix(k[0], k[1], smooth((t - T_SCRUB) / (T_SUNSET - T_SCRUB)))
        if (t < T_HOLD) return k[1]
        if (t < T_NIGHT) return mix(k[1], k[2], smooth((t - T_HOLD) / (T_NIGHT - T_HOLD)))
        if (t < T_DAWN) return k[2]
        if (t < T_SUNRISE) {
            val u = (t - T_DAWN) / (T_SUNRISE - T_DAWN)
            return mix(k[2], k[3], u * u)
        }
        val u = (t - T_SUNRISE) / (T_NOW - T_SUNRISE)
        return mix(k[3], k[4], 1 - (1 - u) * (1 - u))
    }

    fun heldAt(t: Double) = smooth((t - T_PRESS) / (T_SCRUB - T_PRESS)) * (1 - smooth((t - T_NOW) / (T_REST - T_NOW)))

    fun wakeAt(t: Double): Double {
        val up = clamp(t - ROWS_CROSSING, 0.0, ROWS_MAX)
        val down = clamp((t - ROWS_RELEASE) * ROWS_FALL, 0.0, ROWS_MAX)
        return max(0.0, up - down)
    }

    fun hintAt(t: Double) = if (t < HINT_BACK) 1 - smooth((t - T_PRESS) / HINT_LEAVE) else smooth((t - HINT_BACK) / HINT_ARRIVE)

    fun rowTimes(day: SkyTimeScrubDay) = doubleArrayOf(day.noon, day.sunset, day.night)

    private fun crossingTimes(day: SkyTimeScrubDay, k: DoubleArray): Array<DoubleArray> {
        val times = rowTimes(day)
        val out = Array(3) { ArrayList<Double>() }
        val step = 1.0 / 240
        var prev = minutesAt(0.0, k)
        for (i in 1..(LOOP / step).roundToInt()) {
            val t = i * step
            val cur = minutesAt(t, k)
            for (r in 0 until 3) {
                if (floor((prev - times[r]) / 1440) != floor((cur - times[r]) / 1440)) out[r].add(t)
            }
            prev = cur
        }
        return Array(3) { out[it].toDoubleArray() }
    }

    fun createTimeline(day: SkyTimeScrubDay, now: Double, adapt: Boolean, up: Double, down: Double): SkyTimeScrubTimeline {
        val k = keyframes(day, wrapMinutes(now))
        val step = 1.0 / 120
        val n = (LOOP / step).roundToInt()
        val ev = DoubleArray(n + 1)
        ev[0] = targetEV(now, day)
        for (i in 1..n) {
            val target = targetEV(minutesAt(i * step, k), day)
            val prev = ev[i - 1]
            val tau = if (target < prev) up else down
            ev[i] = if (adapt) prev + (target - prev) * (1 - exp(-step / max(tau, 1e-3))) else target
        }
        return SkyTimeScrubTimeline(day, now, k, ev, crossingTimes(day, k))
    }

    fun exposureAt(t: Double, tl: SkyTimeScrubTimeline): Double {
        val rest = targetEV(tl.now, tl.day)
        if (t >= T_REST || t <= T_PRESS) return rest
        val step = 1.0 / 120
        val p = clamp(t / step, 0.0, (tl.ev.size - 1).toDouble())
        val i = floor(p).toInt()
        val ev = mix(tl.ev[i], tl.ev[min(i + 1, tl.ev.size - 1)], p - i)
        val target = targetEV(minutesAt(t, tl.k), tl.day)
        return clamp(mix(ev, rest, smooth((t - T_NOW) / (T_REST - T_NOW))), target - 0.8, target + 1.2)
    }

    fun tickAge(t: Double, tl: SkyTimeScrubTimeline, r: Int): Double {
        var age = 99.0
        for (v in tl.ticks[r]) if (v <= t) age = t - v
        return age
    }

    fun sampleTimeline(time: Double, tl: SkyTimeScrubTimeline): SkyTimeScrubPose {
        val t = ((time % LOOP) + LOOP) % LOOP
        return SkyTimeScrubPose(
            minutesAt(t, tl.k), exposureAt(t, tl), heldAt(t), wakeAt(t), tickAge(t, tl, 0), tickAge(t, tl, 1), tickAge(t, tl, 2),
        )
    }

    fun timeLabel(minutes: Double, day: SkyTimeScrubDay, now: Double, named: Boolean = true): String {
        val m = wrapMinutes(Math.round(minutes).toDouble()).toInt()
        val clock = "%02d:%02d".format(m / 60, m % 60)
        if (!named) return clock
        val md = m.toDouble()
        if (circularDiff(md, day.sunset) < 3) return "${Theme.SUNSET_TITLE} · $clock"
        if (circularDiff(md, day.sunrise) < 3) return "${Theme.SUNRISE} · $clock"
        if (circularDiff(md, day.noon) < 3) return "${Theme.NOON_TITLE} · $clock"
        if (circularDiff(md, now) < 3) return Theme.NOW
        return clock
    }
}


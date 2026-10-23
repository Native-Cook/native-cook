package com.ngocdevv.nativecook.blocks.onboarding.glassbladedraw

import kotlin.math.*

/** Seconds throughout. These pure functions mirror blade-timeline.ts. */
internal object BladeTimeline {
    fun clamp(value: Double) = value.coerceIn(0.0, 1.0)

    fun bezier(x: Double, x1: Double, y1: Double, x2: Double, y2: Double): Double {
        if (x <= 0) return 0.0
        if (x >= 1) return 1.0
        var lo = 0.0
        var hi = 1.0
        repeat(24) {
            val u = (lo + hi) / 2
            val v = 1 - u
            val at = 3 * v * v * u * x1 + 3 * v * u * u * x2 + u * u * u
            if (at < x) lo = u else hi = u
        }
        val u = (lo + hi) / 2
        val v = 1 - u
        return 3 * v * v * u * y1 + 3 * v * u * u * y2 + u * u * u
    }

    fun entrance(t: Double) = bezier(t, .23, 1.0, .32, 1.0)
    fun move(t: Double) = bezier(t, .62, 0.0, .3, 1.0)
    fun drawScale(n: Double) = sqrt(max(.01, abs(n)) / 4)
    fun pourStart(n: Double) = .8 * 1.2 * drawScale(n)
    fun pourEase(t: Double) = bezier(t, .3, .7, .4, 1.0)

    fun spinOffset(tau: Double, n: Double): Double {
        if (tau <= 0) return 0.0
        val t = tau / (1.2 * drawScale(n))
        if (t >= 1) return n
        val left = 1 - t
        val bump = sin(PI * clamp((t - .62) / .38))
        return n * (1 - left * left * left) + sign(n) * .07 * bump * bump
    }

    fun flareStart(n: Double): Double {
        val level = (1 - .2) / (1.15 - .2)
        var lo = 0.0
        var hi = 1.0
        repeat(24) {
            val mid = (lo + hi) / 2
            if (pourEase(mid) < level) lo = mid else hi = mid
        }
        return pourStart(n) + .7 * (lo + hi) / 2
    }

    fun drawDuration(n: Double) = max(pourStart(n) + .7 + .38, flareStart(n) + .74)

    fun releasedPress(tau: Double, from: Double = 1.0): Double {
        if (tau <= 0) return from
        if (tau >= .8) return 0.0
        val w = sqrt(380.0 - 13 * 13)
        return from * exp(-13 * tau) * (cos(w * tau) + 13 / w * sin(w * tau))
    }

    fun drawState(tau: Double, n: Double, flareGain: Double = 1.0,
                  initial: BladeFrame = BladeFrame(press = 1.0)): BladeFrame {
        val t = max(0.0, tau)
        if (t >= drawDuration(n)) return BladeFrame(pos = n)
        val pour = pourStart(n)
        val spark = flareStart(n)
        val speed = abs(spinOffset(t + .008, n) - spinOffset(t - .008, n)) / .016
        val spinning = .2 + .5 * clamp((speed - .5) / 8)
        var charge = initial.charge + (spinning - initial.charge) * entrance(t / .22)
        if (t >= pour) charge = .2 + (1.15 - .2) * pourEase((t - pour) / .7)
        if (t >= pour + .7) charge = 1.15 - .15 * entrance((t - pour - .7) / .38)
        var flare = initial.flare * (1 - entrance(t / .22))
        var energy = initial.ctaEnergy + (.12 - initial.ctaEnergy) * entrance(t / .28)
        if (t >= spark) {
            flare = flareGain * entrance((t - spark) / .09)
            if (t >= spark + .09) flare = flareGain * (1 - entrance((t - spark - .09) / .65))
            energy = .12 + .88 * entrance((t - spark) / .52)
        }
        return BladeFrame(spinOffset(t, n), charge, flare, releasedPress(t, initial.press), energy)
    }

    fun loopState(time: Double, base: Double = 0.0): BladeFrame {
        val cycles = floor(max(0.0, time) / 10)
        val t = max(0.0, time) - cycles * 10
        val origin = base + cycles * 9
        if (t < 3.44) {
            var pos = origin
            if (t >= 1) pos += move((t - 1) / .7)
            if (t >= 2.25) pos += move((t - 2.25) / .7)
            return BladeFrame(pos = pos, press = if (t >= 3.3) entrance((t - 3.3) / .11) else 0.0)
        }
        if (t < 5.74) {
            val state = drawState(t - 3.44, 4.0)
            return state.copy(pos = state.pos + origin + 2,
                press = if (t >= 5.6) entrance((t - 5.6) / .11) else state.press)
        }
        val state = drawState(t - 5.74, 3.0, .55)
        return state.copy(pos = state.pos + origin + 6)
    }
}

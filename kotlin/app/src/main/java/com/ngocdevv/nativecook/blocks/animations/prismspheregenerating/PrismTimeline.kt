package com.ngocdevv.nativecook.blocks.animations.prismspheregenerating

import kotlin.math.*

/** Closed-form curves in seconds; the untouched script is exactly ten seconds at any frame rate. */
internal object PrismTimeline {
    const val LOOP = 10.0
    const val ANTICIPATION = 6.75
    const val BURST = 7.3
    fun clamp(x: Double) = x.coerceIn(0.0, 1.0)
    fun modulo(x: Double, period: Double) = ((x % period) + period) % period
    fun ease(x: Double) = 1 - (1 - clamp(x)).pow(3)
    fun smooth(low: Double, high: Double, value: Double): Double {
        val t = clamp((value - low) / (high - low))
        return t * t * (3 - 2 * t)
    }
    fun age(now: Double, then: Double): Double {
        val d = now - then
        return if (d < -300) d + 600 else if (d > 300) d - 600 else d
    }
    fun sinceTouch(now: Double, then: Double) = if (then < 0) 600.0 else modulo(now - then, 600.0)

    data class Values(
        var charge: Double = 0.0, var scale: Double = 1.0, var flash: Double = 0.0,
        var ring: Double = 0.0, var sweep: Double = 0.0, var sweepPower: Double = 0.0,
        var energy: Double = 0.0, var spin: Double = 0.0, var fan: Double = PI,
    )
    data class Caption(val opacity: Double, val blur: Double, val progress: Double)

    fun caption(clock: Double, reduced: Boolean): Caption {
        val t = modulo(clock, 5.0)
        val away = clamp((t - 2.8) / 1.4)
        val opacity = if (t < 0.25 || t >= 4.2) 0.0 else ease((t - 0.25) / 1.2) * (1 - away)
        return Caption(opacity, if (reduced) 0.0 else 8 * away, if (reduced) 0.0 else away)
    }

    fun surge(s: Double, poseFan: Double, gain: Double = 1.0, releaseCharge: Double = 1.0): Values {
        val v = Values(fan = poseFan)
        if (s < 0) return v
        val elapsed = s - 0.55
        val b = if (abs(elapsed) < 1e-9) 0.0 else elapsed
        if (b < 0) {
            v.charge = ease(s / 0.55)
            v.scale = 1 - 0.035 * v.charge
            v.spin = -0.3 * v.charge
            v.fan = poseFan + (PI - poseFan) * 0.6 * v.charge
            v.energy = 0.3 * v.charge
        } else {
            v.charge = releaseCharge * exp(-b / 0.08)
            if (v.charge < 0.002) v.charge = 0.0
            val kick = exp(-7 * b) * (-0.035 * releaseCharge * cos(11 * b) + 0.09 * gain * sin(11 * b))
            v.scale = if (exp(-7 * b) * hypot(0.035 * releaseCharge, 0.09 * gain) < 0.0005) 1.0 else 1 + kick
            v.flash = gain * exp(-b / 0.28)
            if (v.flash < 0.004) v.flash = 0.0
            v.ring = if (b < 0.9 - 1e-9) max(0.0001, ease(b / 0.9)) else 0.0
            v.energy = 1.1 * v.flash + 0.3 * gain * exp(-b / 0.9)
            v.spin = if (b < 2.6 - 1e-9) -0.3 * releaseCharge + (2 * PI + 0.3 * releaseCharge) * (1 - (1 - clamp(b / 2.6)).pow(4)) else 0.0
            val opening = if (b < 0.35) ease(b / 0.35) else 1 - smooth(1.1, 2.2, b)
            v.fan = poseFan + (max(poseFan, 1.82 * PI) - poseFan) * opening
            val pass = clamp((b - 0.35) / 1)
            v.sweep = pass
            v.sweepPower = if (b > 0.35 && b < 1.35) 0.5 * sin(PI * pass).pow(2) else 0.0
        }
        if (s >= 0.2 && s < 0.7) {
            val power = clamp((s - 0.2) / 0.1) * clamp((0.7 - s) / 0.2)
            if (power > v.sweepPower) {
                v.sweep = clamp((s - 0.2) / 0.5)
                v.sweepPower = power
            }
        }
        v.fan = min(1.975 * PI, v.fan)
        return v
    }

    fun at(clock: Double, poseFan: Double, surgeTime: Double = -1.0, gain: Double = 1.0,
           reduced: Boolean = false, heldCharge: Double = -1.0, heldTime: Double = 0.0,
           releaseCharge: Double = 1.0): Values {
        val t = modulo(clock, 5.0)
        val bump = if (t >= 2.2 && t <= 3.6) sin(PI * (t - 2.2) / 1.4).pow(2) else 0.0
        val restSweep = clamp((t - 1.9) / 1.6)
        val restPower = if (t > 1.9 && t < 3.5) 0.62 * sin(PI * restSweep).pow(2) else 0.0
        val v = surge(surgeTime, poseFan, gain, releaseCharge)
        if (heldCharge >= 0) {
            v.charge = heldCharge
            v.scale = 1 - 0.035 * heldCharge
            v.spin = -0.3 * heldCharge
            v.fan = poseFan + (PI - poseFan) * 0.6 * heldCharge
            v.energy = 0.3 * heldCharge
            v.sweep = clamp((heldTime - 0.2) / 0.5)
            v.sweepPower = clamp((heldTime - 0.2) / 0.1) * clamp((0.7 - heldTime) / 0.2)
        }
        val useSurge = v.sweepPower > restPower
        v.energy = min(1.5, 0.4 + 0.2 * bump + if (reduced) 1.1 * v.flash else v.energy)
        v.sweep = if (reduced) 0.0 else if (useSurge) v.sweep else restSweep
        v.sweepPower = if (reduced) 0.0 else max(restPower, v.sweepPower)
        if (reduced) {
            v.charge = 0.0
            v.scale = 1.0
            v.ring = 0.0
            v.spin = 0.0
            v.fan = poseFan
        }
        return v
    }
}

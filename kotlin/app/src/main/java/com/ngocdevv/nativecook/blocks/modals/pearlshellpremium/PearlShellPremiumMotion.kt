package com.ngocdevv.nativecook.blocks.modals.pearlshellpremium

import kotlin.math.*

internal object PearlShellPremiumMotion {
    const val LOOP = 10.0
    const val CLOSED = 0.05
    const val OPEN = 1.12
    const val LIFT = 0.22
    const val TAU = PI * 2
    data class Frame(
        var open: Double = CLOSED, var glow: Double = 0.0, var lift: Double = 0.0,
        var spin: Double = 0.0, var phase: Double = 0.0, var yaw: Double = 0.0,
        var finger: Double = 0.0, var fingerY: Double = 0.0, var fingerPress: Double = 0.0,
    )
    fun clamp(x: Double) = x.coerceIn(0.0, 1.0)
    fun seg(t: Double, a: Double, b: Double) = clamp((t - a) / (b - a))
    fun ease(x: Double) = if (x < 0.5) 4 * x * x * x else 1 - (-2 * x + 2).pow(3) / 2
    fun out(x: Double) = 1 - (1 - x).pow(3)
    fun frameAt(t: Double): Frame {
        val breathe = 0.018 * sin(TAU * t / LOOP)
        var angle = CLOSED + breathe
        if (t >= 0.9 && t < 8.4) {
            angle = if (t < 2.1) CLOSED + breathe + (0.9 - CLOSED) * ease(seg(t, 0.9, 2.1))
            else OPEN + (0.9 + breathe - OPEN) * exp(-5 * (t - 2.1)) * cos(7 * (t - 2.1))
            if (t >= 7.3) {
                val k = seg(t, 7.3, 8.4)
                val shut = if (k < 0.75) ease(k / 0.75) else 1 - 0.06 * sin(PI * (k - 0.75) / 0.25)
                val from = OPEN + (0.9 - OPEN) * exp(-5 * (7.3 - 2.1)) * cos(7 * (7.3 - 2.1))
                angle = from + (CLOSED + breathe - from) * shut
            }
        }
        val finger = out(seg(t, 0.75, 0.9)) * (1 - out(seg(t, 2.1, 2.28)))
        return Frame(angle,
            out(seg(t, 2.2, 3.0)) * (1 - ease(seg(t, 7.3, 8.0))),
            LIFT * ease(seg(t, 2.3, 3.3)) * (1 - ease(seg(t, 7.3, 7.9))),
            TAU * t / LOOP, TAU * 2 * t / LOOP % TAU, 0.12 * sin(TAU * t / LOOP),
            finger, ease(seg(t, 0.9, 2.1)), finger)
    }
    fun bezier(x: Double, a: Double, b: Double, c: Double, d: Double): Double {
        fun value(t: Double, p: Double, q: Double) = 3 * (1-t).pow(2) * t * p + 3 * (1-t) * t*t * q + t*t*t
        var lo = 0.0
        var hi = 1.0
        repeat(32) {
            val mid = (lo + hi) / 2
            if (value(mid, a, c) < clamp(x)) lo = mid else hi = mid
        }
        return value((lo + hi) / 2, b, d)
    }
}

internal class PearlShellPremiumSpring(from: Double, val target: Double, duration: Double, val ratio: Double, velocity: Double, energyVelocity: Double = velocity) {
    var value = from
    var velocity = if ((target > from && velocity < 0) || (target < from && velocity > 0)) 0.0 else velocity
    private var stiffness = 4000.0
    private val initialEnergy: Double
    var finished = false
    init {
        val x = from-target
        val duration = duration * 1.5
        fun fraction(k: Double): Double {
            val w = sqrt(k/4) * ratio
            val e = exp(-w*duration)
            val xt = (x + (this.velocity+x*w)*duration)*e
            val vt = xt * -w + (this.velocity+x*w)*e
            return (0.5*k*xt*xt+2*vt*vt)/(0.5*k*x*x+2*this.velocity.pow(2))-6e-9
        }
        if (x != 0.0 || this.velocity != 0.0) {
            var lo = 2.220446049250313e-16
            var hi = 8000.0
            for (i in 0 until 100) {
                stiffness = (lo+hi)/2
                val f = fraction(stiffness)
                if (abs(f) <= 6e-12) break
                if (f > 0) lo = stiffness else hi = stiffness
            }
        }
        initialEnergy = 0.5*stiffness*x*x+2*energyVelocity*energyVelocity
    }
    fun step(delta: Double) {
        if (finished) return
        val dt = delta.coerceIn(0.0, 0.064)
        val x = value-target
        val w = sqrt(stiffness/4)
        val wd = w*sqrt(1-ratio*ratio)
        val envelope = exp(-ratio*w*dt)
        val s = sin(wd*dt)
        val c = cos(wd*dt)
        val part = envelope*(s*((velocity+ratio*w*x)/wd)+x*c)
        velocity = -ratio*w*part+envelope*(c*(velocity+ratio*w*x)-wd*x*s)
        value = target+part
        if (initialEnergy == 0.0 || (0.5*stiffness*part*part+2*velocity*velocity)/initialEnergy <= 6e-9) {
            value = target; velocity = 0.0; finished = true
        }
    }
}

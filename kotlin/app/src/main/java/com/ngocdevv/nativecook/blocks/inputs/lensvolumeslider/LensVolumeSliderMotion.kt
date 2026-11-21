package com.ngocdevv.nativecook.blocks.inputs.lensvolumeslider

import kotlin.math.*

/** Pure score matching the Swift port, including its camera lag and spring releases. */
internal object LensVolumeSliderMotion {
    const val LOOP = 8.0
    const val REST = 52.0
    fun frameAt(seconds: Double): Frame {
        val t = wrap(seconds)
        val v = valueAt(t)
        val speed = (v - valueAt(t - .08)) / .08
        val x = speed * 2.5 * .06
        val stretch = x / (1 + (abs(x) / 16).pow(4)).pow(.25)
        val marble = smooth(seg(t, 4.10, 4.26)) * (1 - smooth(seg(t, 5.25, 5.60)))
        val lift = press(t, .2, 1.12) + press(t, 2.6, 3.32) + press(t, 5.0, 6.52) + press(t, 6.96, 7.1)
        val weights = doubleArrayOf(.04, .10, .16, .18, .17, .14, .10, .07, .04)
        return Frame(v, lift.coerceAtMost(1.0), marble > .5, stretch,
            if (t > 6.55) exp(-(t - 6.55) / .16) * seg(t, 6.55, 6.58) else 0.0,
            (t / 4.0) % 1.0, weights.mapIndexed { i, w -> w * valueAt(t - i * .05) }.sum(), marble,
            smooth(seg(t, 4.10, 4.60)) * (1 - smooth(seg(t, 5.20, 5.70))))
    }
    fun valueAt(seconds: Double): Double {
        val t = wrap(seconds)
        return when {
            t < .34 -> REST
            t < .55 -> 52 - 2 * easeOut(seg(t, .34, .55))
            t < 1.12 -> { val k = seg(t, .55, 1.12); 50 + 30 * (-2*k*k*k+3*k*k) + 24*(k*k*k-k*k) }
            t < 2.75 -> spring(t - 1.12, 80.0, 85.0, 42.1, 5.5, 170.0)
            t < 4.37 -> max(0.0, 85 * (1 - 1.06 * easeInOut(seg(t, 2.75, 4.37))))
            t < 7.1 -> min(100.0, 108 * cubic(seg(t, 5.2, 6.7)))
            else -> spring(t - 7.1, 100.0, REST, 0.0, 22.1, 157.9)
        }
    }
    private fun wrap(t: Double) = ((t % LOOP) + LOOP) % LOOP
    private fun seg(t: Double, a: Double, b: Double) = ((t-a)/(b-a)).coerceIn(0.0, 1.0)
    private fun smooth(x: Double) = x*x*(3-2*x)
    private fun easeOut(x: Double) = 1-(1-x).pow(3)
    private fun easeInOut(x: Double) = if (x < .5) 2*x*x else 1-2*(1-x).pow(2)
    private fun cubic(x: Double) = if (x < .5) 4*x*x*x else 1-(-2*x+2).pow(3)/2
    private fun press(t: Double, start: Double, end: Double): Double = when {
        t < start -> 0.0
        t < start + .12 -> easeOut(seg(t, start, start+.12))
        t < end -> 1.0
        else -> max(0.0, spring(t-end, 1.0, 0.0, 0.0, 12.0, 220.0))
    }
    private fun spring(t: Double, from: Double, to: Double, velocity: Double, damping: Double, stiffness: Double): Double {
        if (t <= 0) return from
        val x = from-to; val w = sqrt(stiffness); val z = damping/(2*w)
        if (z < 1) { val wd=w*sqrt(1-z*z); return to+exp(-z*w*t)*(x*cos(wd*t)+(velocity+z*w*x)/wd*sin(wd*t)) }
        return to+exp(-w*t)*(x+(velocity+w*x)*t)
    }
    data class Frame(val value: Double, val lift: Double, val muted: Boolean, val stretch: Double, val flash: Double,
        val shimmer: Double, val zoom: Double, val marble: Double, val dim: Double)
}

package com.ngocdevv.nativecook.blocks.pickers.glowdialreminder

import kotlin.math.*

/** The same pure ten-second score as Swift; touch uses this exact visible pose for takeover. */
internal object GlowDialReminderMotion {
    const val LOOP = 10.0
    const val REST = 8.0
    fun frameAt(seconds: Double): Frame {
        val t = ((seconds % LOOP) + LOOP) % LOOP
        val hours = when {
            t < .25 -> REST
            t < 1.65 -> 8 + 3 * smooth(segment(t, .25, 1.65))
            t < 2.4 -> 11 + .10 * exp(-(t-1.65)*6) * sin((t-1.65)*15)
            t < 2.6 -> 11.0
            t < 3.9 -> 11 - 5 * smooth(segment(t, 2.6, 3.9))
            t < 6.8 -> 6.0
            t < 8.55 -> 6 + 2.75 * smooth(segment(t, 6.8, 8.55))
            t < 9.5 -> 8.75 - .75 * smooth(segment(t, 8.55, 9.5))
            else -> REST
        }
        val off = when {
            t < 5 -> 0.0
            t < 6.4 -> smooth(segment(t, 5.0, 5.4))
            else -> 1 - smooth(segment(t, 6.4, 6.8))
        }
        val moving = (t > .1 && t < 2.4) || (t > 2.6 && t < 3.9) || (t > 6.8 && t < 9.5)
        val shear = if (moving) 1.1 * sin((t-.2)*3.7) * (if (t < 4) 1.0 else .65) else 0.0
        val ring = if ((t > 1.95 && t < 2.6) || (t > 9.35 && t < 9.95)) 1.0 else 0.0
        return Frame(hours, off, shear, ring, t > 4.5 && t < 7.25)
    }
    private fun segment(t: Double, a: Double, b: Double) = ((t-a)/(b-a)).coerceIn(0.0, 1.0)
    private fun smooth(x: Double) = x*x*(3-2*x)
    data class Frame(val hours: Double, val off: Double, val shear: Double, val ring: Double, val draining: Boolean)
}

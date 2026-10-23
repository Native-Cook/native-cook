package com.ngocdevv.nativecook.blocks.lists.raceresultstandings

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin
import kotlin.math.sqrt

/** Reanimated's duration spring: solve stiffness from energy, then evaluate the oscillator. */
internal class RaceResultStandingsStretch(private val initial: Double, private val startedAt: Double) {
    private val mass = 4.0
    private val threshold = 6e-9
    private val zeta = RaceResultStandingsMotion.SETTLE.dampingRatio
    private val stiffness = solveStiffness()
    private val omega = sqrt(stiffness / mass)
    private val damped = omega * sqrt(1 - zeta * zeta)

    fun value(at: Double): Double {
        if (initial == 0.0) return 0.0
        val t = (at - startedAt).coerceAtLeast(0.0)
        val envelope = exp(-zeta * omega * t)
        val position = envelope * initial * (cos(damped * t) + zeta * omega / damped * sin(damped * t))
        val velocity = -zeta * omega * position +
            envelope * (cos(damped * t) * zeta * omega * initial - damped * initial * sin(damped * t))
        val fraction = (stiffness * position * position + mass * velocity * velocity) /
            (stiffness * initial * initial)
        return if (fraction <= threshold) 0.0 else position
    }

    private fun solveStiffness(): Double {
        if (initial == 0.0) return 900.0
        val duration = RaceResultStandingsMotion.SETTLE.duration * 1.5 / 1000
        fun energyDifference(stiffness: Double): Double {
            val decay = sqrt(stiffness / mass) * zeta
            val position = (initial + initial * decay * duration) * exp(-decay * duration)
            val velocity = -decay * position + initial * decay * exp(-decay * duration)
            return (stiffness * position * position + mass * velocity * velocity) /
                (stiffness * initial * initial) - threshold
        }
        var low = Math.ulp(1.0)
        var high = 8000.0
        val direction = if (energyDifference(high) >= energyDifference(low)) 1 else -1
        var current = (low + high) / 2
        var iterations = 100
        while (abs(energyDifference(current)) > threshold * 1e-3 && iterations > 0) {
            iterations--
            if (energyDifference(current) * direction < 0) low = current else high = current
            current = (low + high) / 2
        }
        return current
    }
}

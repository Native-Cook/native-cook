package com.ngocdevv.nativecook.blocks.gestures.dayonemedal

import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

/** Pure numerical functions translated from medal-sim.ts. */
internal object DayOneMedalPhysics {
    data class Motion(val position: Float, val velocity: Float)
    data class Cut(val progress: Float, val line: Int, val cutting: Boolean)

    fun oscillator(position: Float, velocity: Float, target: Float,
                   stiffness: Float, damping: Float, dt: Float): Motion {
        val x = position - target
        val decay = damping / 2f
        val frequency = sqrt(max(1e-9f, stiffness - decay * decay))
        val envelope = exp(-decay * dt)
        val cosine = cos(frequency * dt)
        val sine = sin(frequency * dt)
        return Motion(
            target + envelope * (x * cosine + (velocity + decay * x) * sine / frequency),
            envelope * (velocity * cosine - (decay * velocity + stiffness * x) * sine / frequency),
        )
    }

    fun critical(position: Float, velocity: Float, target: Float, dt: Float): Motion {
        val x = position - target
        val b = velocity + 12f * x
        val decay = exp(-12f * dt)
        return Motion(target + (x + b * dt) * decay, (velocity - 12f * b * dt) * decay)
    }

    fun engravingAt(time: Double): Cut {
        val lines = arrayOf(doubleArrayOf(0.05, 0.34), doubleArrayOf(0.36, 0.44),
            doubleArrayOf(0.48, 0.74), doubleArrayOf(0.78, 1.0))
        val cutTotal = 2.4 - 3.0 * 0.12
        var weight = 0.0
        for (line in lines) weight += line[1] - line[0]
        var start = 0.0
        for (i in lines.indices) {
            val low = lines[i][0]
            val high = lines[i][1]
            val duration = cutTotal * (high - low) / weight
            if (time < start + duration) {
                val t = ((time - start) / duration).coerceIn(0.0, 1.0)
                return Cut((low + (high - low) * t).toFloat(), i, true)
            }
            if (time < start + duration + 0.12 && i < lines.lastIndex) return Cut(high.toFloat(), i, false)
            start += duration + 0.12
        }
        return Cut(1f, lines.lastIndex, false)
    }
}

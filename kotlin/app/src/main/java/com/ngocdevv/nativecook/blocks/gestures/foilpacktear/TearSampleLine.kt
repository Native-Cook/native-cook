package com.ngocdevv.nativecook.blocks.gestures.foilpacktear

import kotlin.math.*

internal fun sampleTearLine(): DoubleArray {
    val line = mutableListOf<Double>()
    run {
        var i = 0.0
        while (i < TEAR.columns) {
            val u = i / (TEAR.columns - 1.0)
            val slow = 5.5 * sin(u * 4.1 + 0.6) - 3.2 * sin(u * 9.3 + 1.9)
            val jitter = 0.9 * sin(i * 12.9898) * cos(i * 4.1414)
            line.add(max(-TEAR.wander, min(TEAR.wander, slow + jitter)))
            i += 1.0
        }
    }
    return line.toDoubleArray()
}
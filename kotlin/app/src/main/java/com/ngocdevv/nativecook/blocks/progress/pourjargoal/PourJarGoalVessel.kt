package com.ngocdevv.nativecook.blocks.progress.pourjargoal

import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/** Per-row wall tables of a rounded jar (RN `pjg-vessel.ts`, round-rect branch) and the volume <-> height LUT. */
internal class PourJarGoalVessel(val width: Double, val height: Double, val topRadius: Double, val bottomRadius: Double) {
    val left = DoubleArray(128)
    val right = DoubleArray(128)
    val volume = DoubleArray(128)
    var total = 0.0

    init {
        val tr = min(width / 2, topRadius)
        val br = min(width / 2, bottomRadius)
        for (row in 0 until 128) {
            val y = ((row + 0.5) / 128) * height
            var l = 0.0
            if (y < tr) l = tr - sqrt(max(0.0, tr * tr - (y - tr) * (y - tr)))
            if (y > height - br) l = br - sqrt(max(0.0, br * br - (y - height + br) * (y - height + br)))
            left[row] = l
            right[row] = width - l
        }
        var t = 0.0
        for (row in 127 downTo 0) {
            t += ((right[row] - left[row]) * height) / 128
            volume[row] = t
        }
        total = t
    }

    fun volumeToY(fraction: Double): Double {
        val target = PourJarGoalMotion.clamp(fraction) * total
        if (target <= 0) return height
        if (target >= total) return 0.0
        if (target <= volume[127]) return ((128 - target / volume[127]) / 128) * height
        var lo = 0
        var hi = 127
        while (hi - lo > 1) {
            val m = (lo + hi) shr 1
            if (volume[m] > target) lo = m else hi = m
        }
        val a = volume[lo]
        val b = volume[hi]
        return ((lo + (a - target) / max(a - b, 0.001)) / 128) * height
    }

    fun yToVolume(y: Double): Double {
        val row = PourJarGoalMotion.clamp((y / height) * 128, 0.0, 128.0)
        val i = min(127, row.toInt())
        val next = if (i == 127) 0.0 else volume[i + 1]
        return (volume[i] + (next - volume[i]) * (row - i)) / total
    }
}

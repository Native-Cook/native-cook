package com.ngocdevv.nativecook.blocks.animations.gemorbcollect

import kotlin.math.max
import kotlin.math.min

internal object GemOrbCollectMath {
    fun clamp(value: Float, low: Float = 0f, high: Float = 1f): Float = min(high, max(low, value))

    fun ease(value: Float, x1: Float = 0.23f, y1: Float = 1f, x2: Float = 0.32f, y2: Float = 1f): Float {
        val progress = clamp(value)
        if (progress == 0f || progress == 1f) return progress
        var low = 0f
        var high = 1f
        repeat(12) {
            val u = (low + high) / 2f
            val v = 1f - u
            val x = 3f * v * v * u * x1 + 3f * v * u * u * x2 + u * u * u
            if (x < progress) low = u else high = u
        }
        val u = (low + high) / 2f
        val v = 1f - u
        return 3f * v * v * u * y1 + 3f * v * u * u * y2 + u * u * u
    }

    fun hash(index: Int): Float {
        val value = kotlin.math.sin(index * 127.1 + 43.7) * 43758.5453
        return (value - kotlin.math.floor(value)).toFloat()
    }
}

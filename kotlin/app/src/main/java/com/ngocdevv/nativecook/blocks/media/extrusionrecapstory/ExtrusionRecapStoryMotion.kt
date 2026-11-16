package com.ngocdevv.nativecook.blocks.media.extrusionrecapstory

import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sign
import kotlin.math.sin
import kotlin.math.sqrt

internal class ExtrusionRecapStorySpring(val damping: Double, val stiffness: Double, val mass: Double)

/** Every motion value of the block (`ers-motion.ts`): the three-page film, the springs, the gesture thresholds and the one-shot curves. */
internal object ExtrusionRecapStoryMotion {
    const val LOOP = 8.0
    val PAGE = ExtrusionRecapStorySpring(24.0, 200.0, 1.0)
    val STEER = ExtrusionRecapStorySpring(14.0, 120.0, 1.0)
    val GLIDE = ExtrusionRecapStorySpring(22.1, 157.9, 1.0)

    const val DISTANCE = 300.0
    const val INTENT = 10.0
    const val STEER_DISTANCE = 40.0
    val STEER_ANGLE = 12 * PI / 180
    const val HOLD = 200.0
    const val RESUME = 2000.0
    const val PROJECTION = 0.12
    const val THRESHOLD = 0.4
    const val VELOCITY = 600.0
    const val RUBBER = 0.25

    fun clamp(v: Double, a: Double = 0.0, b: Double = 1.0) = min(b, max(a, v))
    fun smooth(a: Double, b: Double, v: Double): Double {
        val x = clamp((v - a) / (b - a))
        return x * x * (3 - 2 * x)
    }

    fun timeline(t: Double): Double {
        val x = ((t % LOOP) + LOOP) % LOOP
        if (x < 0.7) return 0.0
        if (x < 1.25) return 0.5 * smooth(0.7, 1.25, x)
        if (x < 1.45) return 0.5
        if (x < 2.1) return 0.5 + 0.5 * smooth(1.45, 2.1, x)
        if (x < 3.5) return 1.0
        if (x < 4.7) return 1 + smooth(3.5, 4.7, x)
        if (x < 6) return 2.0
        if (x < 7.2) return 2 + smooth(6.0, 7.2, x)
        return 3.0
    }

    fun mod(v: Double, n: Double) = ((v % n) + n) % n

    fun pageAlpha(progress: Double, i: Int, count: Int): Double {
        val p = mod(progress, count.toDouble())
        val base = floor(p)
        val q = p - base
        if (i == base.toInt()) return 1 - smooth(0.0, 0.3, q)
        if (i == (base.toInt() + 1) % count) return smooth(0.72, 1.0, q)
        return 0.0
    }

    /** The peek label leaves upward and arrives from below, riding the next card's landing rise. */
    fun peekShift(progress: Double, i: Int, count: Int, age: Double): Double {
        val p = mod(progress, count.toDouble())
        val base = floor(p).toInt()
        val a = pageAlpha(progress, i, count)
        val rise = if (age >= 0 && age < 0.65) (8 * exp(-6 * age) * sin(sqrt(164.0) * age)) / 0.532647813016 else 0.0
        return (if (i == base) -8.0 else 8.0) * (1 - a) - rise
    }

    fun landingAt(t: Double): Double {
        val x = ((t % LOOP) + LOOP) % LOOP
        return if (x >= 7.2) x - 7.2 else if (x >= 4.7) x - 4.7 else if (x >= 2.1) x - 2.1 else 10.0
    }

    fun landingPulse(age: Double) = if (age >= 0 && age < 0.65) (exp(-6 * age) * sin(sqrt(164.0) * age)) / 0.532647813016 else 0.0

    /** The fixed three-page film is the showcase. */
    fun storyAt(t: Double, count: Int): Double {
        if (count <= 1) return 0.0
        if (count == 3) return timeline(t)
        val duration = 2.6
        val x = ((t % (duration * count)) + duration * count) % (duration * count)
        return floor(x / duration) + smooth(1.2, duration, x % duration)
    }

    fun copyShift(alpha: Double, age: Double, hasStat: Boolean): Double {
        val bounce = 4 * landingPulse(age)
        return -12 * (1 - alpha) + (if (hasStat) bounce else 0.0)
    }

    fun highlightPass(age: Double) = if (age >= 0 && age < 0.3) sin(PI * age / 0.3) else 0.0

    fun digitArrival(progress: Double, page: Int, count: Int, digit: Int): Double {
        val p = mod(progress, count.toDouble())
        val i = floor(p).toInt()
        val q = p - i
        if (i == page) return 1.0
        if ((i + 1) % count == page) return smooth(0.7 + digit * 0.025, 0.95 + digit * 0.025, q)
        return 0.0
    }

    fun signOf(v: Double) = sign(v)
}

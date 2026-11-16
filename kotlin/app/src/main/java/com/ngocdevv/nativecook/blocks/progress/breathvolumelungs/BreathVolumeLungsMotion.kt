package com.ngocdevv.nativecook.blocks.progress.breathvolumelungs

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cbrt
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tanh

/** Port of bvl-motion.ts + bvl-color.ts. All times are seconds. */
internal object BreathVolumeLungsMotion {
    const val LOOP = 8.5
    const val T_EXHALE = 0.7
    const val T_EMPTY = 3.9
    const val T_RESULT = 4.3
    const val T_REFILL = 6.3
    const val T_FULL = 7.9

    class SpringSpec(val damping: Double, val stiffness: Double, val mass: Double)
    val GLIDE = SpringSpec(22.1, 157.9, 1.0)
    val SNAP = SpringSpec(30.5, 322.3, 1.0)
    val INHALE = SpringSpec(10.0, 180.0, 1.0)

    const val IDLE = 2.0
    const val COAST = 0.6
    const val RAMP = 0.25
    const val RATE = 0.42
    const val RUBBER = 0.3
    const val MAX_OVER = 14.0
    const val FLOW_PEAK = 1.13

    fun clamp(x: Double, lo: Double = 0.0, hi: Double = 1.0): Double = max(lo, min(hi, if (x.isFinite()) x else lo))
    fun smooth(x: Double): Double {
        val a = clamp(x)
        return a * a * (3 - 2 * a)
    }

    fun springAt(t: Double, response: Double, dampingRatio: Double): Double {
        if (t <= 0) return 0.0
        val omega = 2 * PI / response
        val wd = omega * sqrt(max(0.0001, 1 - dampingRatio * dampingRatio))
        return 1 - exp(-dampingRatio * omega * t) * (cos(wd * t) + (dampingRatio * omega / wd) * sin(wd * t))
    }

    private fun wrap(time: Double) = ((time % LOOP) + LOOP) % LOOP

    fun progressAt(time: Double): Double {
        val t = wrap(time)
        if (t < T_EXHALE) return 0.0
        if (t < T_EMPTY) {
            val a = (t - T_EXHALE) / (T_EMPTY - T_EXHALE)
            return 1 - (1 - smooth(a)).pow(1.45)
        }
        if (t < T_REFILL) return 1.0
        if (t < T_FULL) return 1 - smooth((t - T_REFILL) / (T_FULL - T_REFILL))
        return 0.0
    }

    fun flowAt(t: Double, capacity: Double = 5.5): Double =
        clamp((progressAt(t + 0.001) - progressAt(t - 0.001)) / 0.002 * capacity, -FLOW_PEAK, FLOW_PEAK)

    fun elapsedAt(time: Double): Double {
        val t = wrap(time)
        return clamp(t - T_EXHALE, 0.0, T_EMPTY - T_EXHALE) * (1 - smooth((t - T_REFILL) / (T_FULL - T_REFILL)))
    }

    fun resultAt(time: Double): Double {
        val t = wrap(time)
        return smooth((t - T_RESULT) / 0.2) * (1 - smooth((t - T_REFILL) / 0.2))
    }

    fun lastAirAt(time: Double): Double {
        val t = wrap(time)
        return smooth((t - (T_EMPTY - 0.15)) / 0.15) * (1 - smooth((t - T_EMPTY) / (T_RESULT - T_EMPTY)))
    }

    fun breatheAt(t: Double): Double = 0.5 - 0.5 * cos(4 * PI * t / LOOP)

    fun thresholdAt(p: Double, breath: Double = 0.0): Double =
        0.04 + 0.9 * clamp(p).pow(0.8) + 0.03 * breath * (1 - smooth(p * 8))

    fun heldAt(start: Double, elapsed: Double): Double {
        val integral = if (elapsed < RAMP) elapsed * elapsed / (2 * RAMP) else elapsed - RAMP / 2
        return clamp(1 - max(0.0, (1 - clamp(start)).pow(0.4) - 0.4 * RATE * integral).pow(2.5))
    }

    fun coastAt(start: Double, velocity: Double, elapsed: Double): Double {
        val travel = velocity * COAST * (1 - exp(-elapsed / COAST))
        if (velocity < 0) return clamp(start + travel)
        return clamp(1 - max(0.0, (1 - clamp(start)).pow(0.4) - 0.4 * travel).pow(2.5))
    }

    fun rubberAt(p: Double, travel: Double): Double {
        val bound = clamp(p)
        val over = (p - bound) * travel
        return bound + MAX_OVER * tanh(over * RUBBER / MAX_OVER) / travel
    }

    /** Last integer-litre crossing on the deterministic exhale. */
    fun litreTimes(capacity: Double): DoubleArray {
        val n = capacity.toInt()
        return DoubleArray(n) { idx ->
            val target = idx + 1
            var a = T_EXHALE
            var b = T_EMPTY
            for (k in 0 until 24) {
                val m = (a + b) / 2
                if (progressAt(m) * capacity < target) a = m else b = m
            }
            (a + b) / 2
        }
    }

    // ---- OKLCH heat ----
    private fun linear(v: Double) = if (v <= 0.04045) v / 12.92 else ((v + 0.055) / 1.055).pow(2.4)
    private fun gamma(v: Double) = max(0.0, min(1.0, if (v <= 0.0031308) 12.92 * v else 1.055 * max(0.0, v).pow(1 / 2.4) - 0.055))
    private fun lab(c: DoubleArray): DoubleArray {
        val r = linear(c[0]); val g = linear(c[1]); val b = linear(c[2])
        val l = cbrt(0.41222147 * r + 0.53633254 * g + 0.05144599 * b)
        val m = cbrt(0.2119035 * r + 0.68069955 * g + 0.10739696 * b)
        val s = cbrt(0.08830246 * r + 0.28171884 * g + 0.6299787 * b)
        return doubleArrayOf(
            0.21045426 * l + 0.79361779 * m - 0.00407205 * s,
            1.9779985 * l - 2.42859221 * m + 0.45059371 * s,
            0.02590404 * l + 0.78277177 * m - 0.80867577 * s,
        )
    }

    fun heatAt(p: Double, a: DoubleArray, b: DoubleArray, c: DoubleArray): DoubleArray {
        val u = lab(if (p < 0.5) a else b)
        val v = lab(if (p < 0.5) b else c)
        val f = if (p < 0.5) p * 2 else (p - 0.5) * 2
        val ha = atan2(u[2], u[1])
        val hb = atan2(v[2], v[1])
        val dh = ((hb - ha + PI * 3) % (PI * 2)) - PI
        val h = ha + dh * f
        val chroma = hypot(u[1], u[2]) * (1 - f) + hypot(v[1], v[2]) * f
        val lum = u[0] * (1 - f) + v[0] * f
        val aa = chroma * cos(h)
        val bb = chroma * sin(h)
        val l = (lum + 0.39633778 * aa + 0.21580376 * bb).pow(3)
        val m = (lum - 0.10556135 * aa - 0.06385417 * bb).pow(3)
        val s = (lum - 0.08948418 * aa - 1.29148555 * bb).pow(3)
        return doubleArrayOf(
            gamma(4.07674166 * l - 3.30771159 * m + 0.23096993 * s),
            gamma(-1.268438 * l + 2.6097574 * m - 0.3413194 * s),
            gamma(-0.00419609 * l - 0.70341861 * m + 1.7076147 * s),
        )
    }
}

/** A damped spring (RN withSpring) integrated in fixed sub-steps. Rest thresholds are Reanimated's defaults. */
internal class BreathVolumeLungsSpring(var x: Double = 0.0, var target: Double = 0.0, var spec: BreathVolumeLungsMotion.SpringSpec = BreathVolumeLungsMotion.SNAP) {
    var v = 0.0
    var moving = false
    /** Set true by [step] on the frame the spring comes to rest after [moving]. */
    var justFinished = false

    fun to(t: Double, s: BreathVolumeLungsMotion.SpringSpec) {
        target = t; spec = s; v = 0.0; moving = true; justFinished = false
    }

    fun set(value: Double) { x = value; v = 0.0; moving = false; target = value }

    fun step(dt: Double) {
        justFinished = false
        if (!moving) return
        val n = max(1, (dt / (1.0 / 240)).toInt())
        val h = dt / n
        for (i in 0 until n) {
            val a = (-spec.stiffness * (x - target) - spec.damping * v) / spec.mass
            v += a * h
            x += v * h
        }
        if (abs(x - target) < 0.01 && abs(v) < 2.0) { x = target; v = 0.0; moving = false; justFinished = true }
    }
}

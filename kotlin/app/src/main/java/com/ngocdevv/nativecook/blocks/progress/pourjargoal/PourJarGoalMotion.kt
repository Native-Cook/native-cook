package com.ngocdevv.nativecook.blocks.progress.pourjargoal

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/** One loop: the unit of the RN block's `pjg-motion.ts` (seconds), the 120 Hz wave solver and the rolling-digit helpers. */
internal object PourJarGoalMotion {
    const val LOOP = 8.0

    const val REST_END = 0.5
    const val HOLD_END = 1.6
    const val LIFT_END = 2.8
    const val TOAST_IN = 3.3
    const val TOAST_OUT = 5.4
    const val DRAIN_START = 5.6
    const val DRAIN_END = 7.2

    const val COLUMNS = 64
    const val ROWS = 128
    const val HZ = 120
    const val SPEED = 520.0
    const val FINGER_SIGMA = 22.0
    const val RELEASE_CAP = 900.0
    const val SKIP_THRESHOLD = 1400.0
    const val RESUME_DELAY = 2.0
    const val RETURN_SECONDS = 0.6
    const val CALLBACK_INTERVAL = 0.1
    const val RUBBER_BAND = 0.25
    const val LAG = 0.09
    const val VISCOSITY = 3.2

    fun clamp(x: Double, lo: Double = 0.0, hi: Double = 1.0) = min(hi, max(lo, x))
    fun smooth(x: Double): Double {
        val a = clamp(x)
        return a * a * (3 - 2 * a)
    }
    fun segment(t: Double, a: Double, b: Double) = smooth((t - a) / (b - a))

    fun springAt(t: Double, stiffness: Double = 220.0, damping: Double = 16.0): Double {
        if (t <= 0) return 0.0
        val w = sqrt(stiffness)
        val z = damping / (2 * w)
        val wd = w * sqrt(max(0.001, 1 - z * z))
        return 1 - exp(-z * w * t) * (cos(wd * t) + ((z * w) / wd) * sin(wd * t))
    }

    class Timeline(val t: Double, val lift: Double, val grip: Double, val toast: Double, val bubbleTime: Double, val impact: Double)

    fun timeline(t: Double): Timeline {
        val s = ((t % LOOP) + LOOP) % LOOP
        val rise = segment(s, HOLD_END, LIFT_END)
        val lift = (1 - (1 - rise) * (1 - rise)) * (1 - segment(s, DRAIN_START, DRAIN_END))
        val grip = segment(s, REST_END, REST_END + 0.14) * (1 - segment(s, LIFT_END, LIFT_END + 0.16))
        val toast = clamp(springAt(s - TOAST_IN)) * (1 - segment(s, TOAST_OUT, TOAST_OUT + 0.34))
        return Timeline(
            s, lift, grip, toast,
            segment(s, 0.5, 0.8) * min(max(s - 0.5, 0.0), 4.8) * (1 - segment(s, 5.6, 7.2)),
            exp(-((s - 2.94) / 0.2).pow(2)),
        )
    }

    class WaveField {
        val h = DoubleArray(64)
        val v = DoubleArray(64)
        val work = DoubleArray(64)
        val velocityWork = DoubleArray(64)
        val output = DoubleArray(64)
        var n = 64
        var energy = 0.0
    }

    /** Reflecting walls; a zero-mean displacement conserves the volume selected by the LUT. */
    fun stepField(
        f: WaveField, dt: Double, width: Double, damping: Double, fingerX: Double = -1000.0, mound: Double = 0.0,
        secondX: Double = -1000.0, columns: Int = 64, secondMound: Double = mound,
    ) {
        val count = Math.round(clamp(columns.toDouble(), 32.0, 64.0)).toInt()
        if (f.n != count) {
            for (i in 0 until count) {
                val u = (i.toDouble() / (count - 1)) * (f.n - 1)
                val a = floor(u).toInt()
                val b = min(f.n - 1, a + 1)
                f.work[i] = f.h[a] + (f.h[b] - f.h[a]) * (u - a)
                f.velocityWork[i] = f.v[a] + (f.v[b] - f.v[a]) * (u - a)
            }
            for (i in 0 until count) {
                f.h[i] = f.work[i]
                f.v[i] = f.velocityWork[i]
            }
            f.n = count
        }
        val dx = max(8.0, width) / (count - 1)
        val c2 = (SPEED * SPEED) / (dx * dx)
        val substeps = max(1, ceil((SPEED * dt) / dx / 0.75).toInt())
        val hdt = dt / substeps
        for (sub in 0 until substeps) {
            var mean = 0.0
            var velocityMean = 0.0
            var energy = 0.0
            for (i in 0 until count) {
                val l = f.h[if (i == 0) 1 else i - 1]
                val r = f.h[if (i == count - 1) count - 2 else i + 1]
                val x = i * dx
                val g = exp(-((x - fingerX) / FINGER_SIGMA).pow(2)) + exp(-((x - secondX) / FINGER_SIGMA).pow(2))
                val secondG = exp(-((x - secondX) / FINGER_SIGMA).pow(2))
                val force = mound * (g - secondG) + secondMound * secondG
                f.v[i] += ((l + r - 2 * f.h[i]) * c2 -
                    (damping + 18 * g * min(1.0, abs(mound))) * f.v[i] +
                    (force - f.h[i]) * (if (mound != 0.0 || secondMound != 0.0) 1800.0 else 0.0)) * hdt
                f.work[i] = clamp(f.h[i] + f.v[i] * hdt, -48.0, 48.0)
                mean += f.work[i]
                velocityMean += f.v[i]
            }
            mean /= count
            velocityMean /= count
            for (i in 0 until count) {
                f.h[i] = f.work[i] - mean
                f.v[i] = clamp(f.v[i] - velocityMean, -1200.0, 1200.0)
                energy = max(energy, abs(f.v[i]))
            }
            f.energy = energy
        }
        var outputMean = 0.0
        for (i in 0 until 64) {
            val u = (i / 63.0) * (count - 1)
            val a = floor(u).toInt()
            val b = min(count - 1, a + 1)
            f.output[i] = f.h[a] + (f.h[b] - f.h[a]) * (u - a)
            outputMean += f.output[i]
        }
        for (i in 0 until 64) f.output[i] -= outputMean / 64
    }

    fun impulse(f: WaveField, x: Double, velocity: Double, width: Double) {
        for (i in 0 until f.n) {
            f.v[i] += clamp(velocity, -RELEASE_CAP, RELEASE_CAP) * exp(-(((i * width) / (f.n - 1) - x) / 14).pow(2))
        }
    }

    /** The 961 deterministic wave frames of the autoplay loop (RN `LOOP_FIELDS`). */
    val LOOP_FIELDS: Array<DoubleArray> by lazy {
        val width = 216.0
        val f = WaveField()
        val frames = ArrayList<DoubleArray>()
        for (n in 0..(LOOP * HZ).toInt()) {
            val t = n.toDouble() / HZ
            val k = timeline(t)
            if (n == Math.round(LIFT_END * HZ).toInt()) impulse(f, width / 2, 280.0, width)
            stepField(f, 1.0 / HZ, width, 3.2, width / 2, k.grip * 24)
            val fade = 1 - segment(t, 6.6, 7.2)
            frames.add(DoubleArray(64) { f.output[it] * fade })
        }
        frames[0] = DoubleArray(64)
        frames[960] = DoubleArray(64)
        frames.toTypedArray()
    }

    fun rollingDigit(value: Double, place: Double): Double {
        val n = max(0.0, value)
        val q = n / place
        val base = floor(q)
        val phase = smooth(clamp((n % place) - (place - 1)))
        return (base % 10) + phase
    }

    fun rubber(value: Double, lower: Double, upper: Double): Double {
        if (value < lower) return lower + ((value - lower) * RUBBER_BAND) / (1 + abs(value - lower) / 120)
        if (value > upper) return upper + ((value - upper) * 0.3) / (1 + abs(value - upper) / 120)
        return value
    }

    fun money(value: Double): String {
        val s = max(0L, Math.round(value)).toString()
        val out = StringBuilder()
        for (i in s.indices) {
            if (i > 0 && (s.length - i) % 3 == 0) out.append(',')
            out.append(s[i])
        }
        return "$" + out
    }

    fun percentage(value: Double, min: Double, max: Double) = floor(clamp((value - min) / max(1.0, max - min)) * 100).toInt()

    fun digitAt(value: Double, place: Int): Double = if (place == 1) max(0.0, value) % 10 else rollingDigit(value, place.toDouble())
}

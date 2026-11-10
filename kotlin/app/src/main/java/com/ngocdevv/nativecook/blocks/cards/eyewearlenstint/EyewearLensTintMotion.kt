package com.ngocdevv.nativecook.blocks.cards.eyewearlenstint

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/** The deterministic 10 second loop shared with the Swift and React Native blocks. */
internal object EyewearLensTintMotion {
    const val LOOP = 10.0
    const val POUR = 1.4
    const val CARRY = 0.8

    private data class Drop(val at: Double, val tint: Int, val lens: Int)

    private val drops = arrayOf(
        Drop(0.5, 1, 1),
        Drop(3.7, 3, 0),
        Drop(6.7, 0, 1),
    )

    fun clamp01(value: Double): Double = value.coerceIn(0.0, 1.0)

    fun segment(t: Double, start: Double, end: Double): Double =
        clamp01((t - start) / (end - start))

    fun easeInOut(value: Double): Double {
        val x = clamp01(value)
        return if (x < 0.5) 4.0 * x * x * x else 1.0 - (-2.0 * x + 2.0).pow(3.0) / 2.0
    }

    fun easeOut(value: Double): Double {
        val x = 1.0 - clamp01(value)
        return 1.0 - x * x * x
    }

    /** CSS/Swift-style cubic-bezier easing used when a failed carry flies back to its swatch. */
    fun bezier(value: Double, x1: Double, y1: Double, x2: Double, y2: Double): Double {
        val x = clamp01(value)
        if (x <= 0.0) return 0.0
        if (x >= 1.0) return 1.0
        fun cubic(t: Double, a: Double, b: Double): Double =
            3.0 * (1.0 - t) * (1.0 - t) * t * a +
                3.0 * (1.0 - t) * t * t * b +
                t * t * t
        var lo = 0.0
        var hi = 1.0
        repeat(24) {
            val mid = (lo + hi) / 2.0
            if (cubic(mid, x1, x2) < x) lo = mid else hi = mid
        }
        return cubic((lo + hi) / 2.0, y1, y2)
    }

    /** Fills [out] without allocating, so the frame clock stays outside Compose state. */
    fun frameAt(time: Double, out: Frame): Frame {
        out.from = 0
        out.to = 0
        out.pour = 0.0
        out.lens = 1
        out.carry = -1.0
        out.carryTint = 0
        out.glare = 0.0
        out.finger = 0.0
        out.carryOn = 0.0
        out.carryX = -100.0
        out.carryY = -100.0
        out.manualCarryTint = 0
        drops.forEachIndexed { index, drop ->
            if (time < drop.at - 0.15) return@forEachIndexed
            out.from = if (index == 0) 0 else drops[index - 1].tint
            out.to = drop.tint
            out.lens = drop.lens
            val carryEnd = drop.at + CARRY
            if (time < carryEnd) {
                out.carry = easeInOut(segment(time, drop.at, carryEnd))
                out.carryTint = drop.tint
                out.pour = 0.0
            } else {
                out.pour = segment(time, carryEnd, carryEnd + POUR)
            }
            val fingerIn = easeOut(segment(time, drop.at - 0.15, drop.at))
            val fingerOut = easeOut(segment(time, carryEnd, carryEnd + 0.16))
            out.finger = maxOf(out.finger, fingerIn * (1.0 - fingerOut))
        }
        if (out.pour >= 1.0) {
            out.from = out.to
            out.pour = 0.0
        }
        val glareProgress = segment(time, 9.0, 9.9)
        out.glare = sin(PI * 2.0 * easeInOut(glareProgress)) * 0.8
        return out
    }

    data class Frame(
        var from: Int = 0,
        var to: Int = 0,
        var pour: Double = 0.0,
        var lens: Int = 1,
        var carry: Double = -1.0,
        var carryTint: Int = 0,
        var glare: Double = 0.0,
        var finger: Double = 0.0,
        var time: Double = 0.0,
        // Manual carry state. Auto-play uses carry/carryTint and the art derives its arc.
        var carryOn: Double = 0.0,
        var carryX: Double = -100.0,
        var carryY: Double = -100.0,
        var manualCarryTint: Int = 0,
        var stretch: Double = 0.0,
        var heading: Double = 0.0,
    )

    /** Reanimated's duration spring, including its velocity-dependent stiffness and energy stop. */
    class Spring(from: Double, val target: Double, velocity: Double, duration: Double, private val damping: Double) {
        var value = from
            private set
        private var speed = if ((from - target) * velocity > 0.0) 0.0 else velocity
        private val frequency: Double
        private val initialEnergy: Double

        init {
            val x = from - target
            val settling = duration * 1.5
            fun fraction(stiffness: Double): Double {
                val w = sqrt(stiffness / 4.0) * damping
                val envelope = exp(-w * settling)
                val p = (x + (speed + x * w) * settling) * envelope
                val v = -w * p + (speed + x * w) * envelope
                return (stiffness * p * p + 4.0 * v * v) /
                    (stiffness * x * x + 4.0 * speed * speed) - 6e-9
            }
            var lo = Math.ulp(1.0)
            var hi = 8000.0
            var stiffness = 4000.0
            val direction = if (fraction(hi) >= fraction(lo)) 1.0 else -1.0
            var iterations = 0
            while (abs(fraction(stiffness)) > 6e-12 && iterations++ < 100) {
                if (fraction(stiffness) * direction < 0.0) lo = stiffness else hi = stiffness
                stiffness = (lo + hi) / 2.0
            }
            frequency = sqrt(stiffness / 4.0)
            initialEnergy = x * x + velocity * velocity / (frequency * frequency)
        }

        fun step(dt: Double): Boolean {
            val t = min(0.064, dt.coerceAtLeast(0.0))
            val w = frequency
            val wd = w * sqrt(1.0 - damping * damping)
            val x = value - target
            val envelope = exp(-damping * w * t)
            val sine = sin(wd * t)
            val cosine = cos(wd * t)
            val p = envelope * (sine * (speed + damping * w * x) / wd + x * cosine)
            speed = -damping * w * p + envelope * (cosine * (speed + damping * w * x) - wd * x * sine)
            value = target + p
            val done = initialEnergy == 0.0 || (p * p + speed * speed / (w * w)) / initialEnergy <= 6e-9
            if (done) { value = target; speed = 0.0 }
            return done
        }
    }
}

/** Device-independent coordinates measured against the 402 × 874 reference device. */
internal data class EyewearLensTintLayout(
    val w: Double,
    val h: Double,
    val safeTop: Double,
    val safeBottom: Double,
) {
    val top: Double = maxOf(safeTop, 44.0)
    val bottom: Double = maxOf(safeBottom, 20.0)
    val sx: Double = w / 402.0
    private val k: Double = (h - top - bottom) / (874.0 - 62.0 - 34.0)

    fun y(value: Double): Double =
        if (value < 62.0) value * top / 62.0 else top + (value - 62.0) * k

    val photoX: Double = -73.7 * sx
    val photoY: Double = y(303.0) - (303.0 - -126.0) * sx
    val photoW: Double = 536.0 * sx
    val photoH: Double = 800.0 * sx

    val lensAX: Double = 130.0 * sx
    val lensAY: Double = y(303.0)
    val lensBX: Double = 274.3 * sx
    val lensBY: Double = y(303.0)
    val lensR: Double = 52.0 * sx

    val dropX: Double = 370.0 * sx
    fun dropY(index: Int): Double = y(400.0 + index * 44.0)

    fun pourX(index: Int): Double = if (index == 0) lensAX - 0.45 * lensR else lensBX + 0.45 * lensR
    fun pourY(index: Int): Double = if (index == 0) lensAY - 0.55 * lensR else lensBY - 0.55 * lensR

    val cardTop: Double = y(600.0)
    val downX: Double = 200.0 * sx
    val downY: Double = y(562.0)
    val downR: Double = 24.0
    val cardX: Double = 20.0 * sx
    val cardW: Double = 362.0 * sx
    val titleX: Double = 56.0 * sx
    val priceRight: Double = 345.0 * sx
    val bodyX: Double = 56.0 * sx
    val bodyW: Double = 290.0 * sx
    val bagX: Double = 56.0 * sx
    val bagW: Double = 122.0 * sx
    val bagH: Double = 37.0
    val wishRight: Double = 345.0 * sx
}

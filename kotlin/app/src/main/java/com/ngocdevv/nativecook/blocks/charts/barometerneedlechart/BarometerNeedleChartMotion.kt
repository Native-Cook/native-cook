package com.ngocdevv.nativecook.blocks.charts.barometerneedlechart

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

/*
 Barometer Needle Chart — data, timeline and needle physics. Everything here is a pure function of
 its inputs, so the loop is exact and matches the React Native and Swift versions frame for frame.

 The chart spans 48 hours: −24 h (yesterday) to +24 h (forecast), "now" in the middle. The dial
 maps 960 → 1060 hPa onto a 270° arc, 1010 hPa at twelve o'clock.

 autoPlay (LOOP = 9 s):
   0.0 – 1.4   rest: the cursor at now (1012 hPa, Change).
   1.4 – 4.0   scrub back to −14 h through the storm dip (985 hPa, Stormy), ease-in-out.
   4.0 – 4.8   hold.
   4.8 – 7.0   scrub forward to +24 h (1024 hPa, Fair), ease-in-out.
   7.0 – 8.4   back to now, ease-in-out.
   8.4 – 9.0   rest (the needle settles on its spring; the table is periodic, so t = 9 = t = 0).
 The needle follows the cursor's reading on a spring (ω 10 rad/s, ζ 0.38) plus a faint jitter
 while it moves, like a real aneroid movement.
 */

/** The autoPlay loop, seconds (the manifest's `recording.loop`). */
const val LOOP = 9.0

internal object BarometerMotion {
    /** The chart's range in hours either side of now. */
    const val SPAN = 24.0
    const val NOW_HPA = 1012.0
    const val SCALE_MIN = 960.0
    const val SCALE_MAX = 1060.0
    /** Degrees of dial arc for the whole scale; 0° is twelve o'clock, clockwise positive. */
    const val SWEEP = 270.0
    const val NEEDLE_OMEGA = 10.0
    const val NEEDLE_ZETA = 0.38
    private const val TABLE_RATE = 120

    // Readings every 2 h from −24 h to +24 h (25 knots), hPa. The storm bottoms out at −14 h.
    private val KNOTS = doubleArrayOf(
        1005.0, 1002.0, 998.0, 993.0, 988.0, 985.0, 986.0, 990.0, 995.0, 1000.0, 1004.5, 1008.5, 1012.0, 1014.5, 1016.5, 1018.0,
        1019.5, 1020.5, 1021.5, 1022.2, 1022.8, 1023.3, 1023.7, 1024.0, 1024.0,
    )

    val WORDS = arrayOf("Stormy", "Rain", "Change", "Fair", "Very Dry")
    val WORD_AT = doubleArrayOf(974.0, 997.5, 1011.5, 1026.5, 1047.5)
    private val BOUNDS = doubleArrayOf(990.0, 1005.0, 1018.0, 1035.0)
    val TRENDS = arrayOf("Falling", "Steady", "Rising")

    fun clamp(v: Double, a: Double, b: Double) = min(max(v, a), b)
    fun mix(a: Double, b: Double, t: Double) = a + (b - a) * t
    fun seg(t: Double, a: Double, b: Double) = clamp((t - a) / (b - a), 0.0, 1.0)
    fun easeInOut(x: Double) = if (x < 0.5) 4 * x * x * x else 1 - (-2 * x + 2).pow(3) / 2
    fun smooth(a: Double, b: Double, x: Double): Double {
        val t = clamp((x - a) / (b - a), 0.0, 1.0)
        return t * t * (3 - 2 * t)
    }

    /** The reading at hour h (−24 … 24), a Catmull-Rom spline through the knots. */
    fun pressureAt(h: Double): Double {
        val x = (clamp(h, -SPAN, SPAN) + SPAN) / 2
        val i = min(floor(x).toInt(), KNOTS.size - 2)
        val f = x - i
        val p0 = KNOTS[max(i - 1, 0)]
        val p1 = KNOTS[i]
        val p2 = KNOTS[i + 1]
        val p3 = KNOTS[min(i + 2, KNOTS.size - 1)]
        val f2 = f * f
        val f3 = f2 * f
        return 0.5 * (2 * p1 + (-p0 + p2) * f + (2 * p0 - 5 * p1 + 4 * p2 - p3) * f2 + (-p0 + 3 * p1 - 3 * p2 + p3) * f3)
    }

    fun angleOf(hpa: Double) = ((hpa - (SCALE_MIN + SCALE_MAX) / 2) / (SCALE_MAX - SCALE_MIN)) * SWEEP
    fun hpaOf(angle: Double) = (SCALE_MIN + SCALE_MAX) / 2 + (angle / SWEEP) * (SCALE_MAX - SCALE_MIN)

    /** The chart cursor (hours from now) at loop time t. */
    fun cursorAt(t: Double): Double = when {
        t < 1.4 -> 0.0
        t < 4.0 -> -14 * easeInOut(seg(t, 1.4, 4.0))
        t < 4.8 -> -14.0
        t < 7.0 -> mix(-14.0, SPAN, easeInOut(seg(t, 4.8, 7.0)))
        t < 8.4 -> mix(SPAN, 0.0, easeInOut(seg(t, 7.0, 8.4)))
        else -> 0.0
    }

    /** A faint tremor of the movement while the reading changes (whole frequencies of the loop). */
    fun jitterAt(t: Double): Double {
        val speed = clamp(abs(cursorAt(t + 0.02) - cursorAt(t - 0.02)) / 0.04 / 30, 0.0, 1.0)
        val w = 2 * PI / LOOP
        return 0.45 * speed * (sin(37 * w * t) + 0.6 * sin(53 * w * t + 1.3))
    }

    /**
     * The needle angle over one loop, sampled at TABLE_RATE: the spring integrated over four loops
     * and the last one kept, so sample 0 follows the last sample without a jump.
     */
    fun buildNeedleTable(): DoubleArray {
        val n = (LOOP * TABLE_RATE).roundToInt()
        val sub = 4
        val dt = 1.0 / (TABLE_RATE * sub)
        var x = angleOf(pressureAt(0.0))
        var v = 0.0
        val out = DoubleArray(n)
        for (loop in 0 until 4) {
            for (i in 0 until n) {
                for (s in 0 until sub) {
                    val t = (i * sub + s) * dt
                    val target = angleOf(pressureAt(cursorAt(t)))
                    val a = -NEEDLE_OMEGA * NEEDLE_OMEGA * (x - target) - 2 * NEEDLE_ZETA * NEEDLE_OMEGA * v
                    v += a * dt
                    x += v * dt
                }
                if (loop == 3) out[i] = x
            }
        }
        return out
    }

    fun sampleTable(table: DoubleArray, t: Double): Double {
        val n = table.size
        val x = (((t % LOOP) + LOOP) % LOOP) * TABLE_RATE
        val i = floor(x).toInt()
        val f = x - i
        return mix(table[i % n], table[(i + 1) % n], f)
    }

    /** One step of the touch-mode needle spring on [angle, velocity]. */
    fun stepNeedle(state: DoubleArray, target: Double, dt: Double) {
        val steps = max(1, ceil(dt * 240).toInt())
        val h = dt / steps
        var x = state[0]
        var v = state[1]
        repeat(steps) {
            val a = -NEEDLE_OMEGA * NEEDLE_OMEGA * (x - target) - 2 * NEEDLE_ZETA * NEEDLE_OMEGA * v
            v += a * h
            x += v * h
        }
        state[0] = x
        state[1] = v
    }

    /** The forecast word as a continuous index, rolling over ±2 hPa around each boundary. */
    fun wordIndex(hpa: Double): Double {
        var k = 0.0
        for (b in BOUNDS) k += smooth(b - 2, b + 2, hpa)
        return k
    }

    /** Trend against the set hand: 0 falling, 1 steady, 2 rising, continuous between. */
    fun trendIndex(hpa: Double) = smooth(NOW_HPA - 4, NOW_HPA - 1.5, hpa) + smooth(NOW_HPA + 1.5, NOW_HPA + 4, hpa)

    /** A reading rounded softly: rests on whole hPa, rolls to the next across the middle 30 %. */
    fun softRound(v: Double): Double {
        val f = floor(v)
        return f + smooth(0.35, 0.65, v - f)
    }

    private val DAYS = arrayOf("Fri", "Sat", "Sun")

    /** Clock label for an hour offset from now (now = Sat 14:00). */
    fun timeLabel(h: Double): String {
        val rh = jsRound(h)
        if (rh == 0) return "Now"
        val total = 24 + 14 + rh
        val day = clamp(floor(total / 24.0), 0.0, 2.0).toInt()
        val hour = ((total % 24) + 24) % 24
        return "${DAYS[day]} ${if (hour < 10) "0" else ""}$hour:00"
    }

    /** JavaScript's Math.round (halves round up). */
    fun jsRound(v: Double): Int = floor(v + 0.5).toInt()
}

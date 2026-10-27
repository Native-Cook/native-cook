package com.ngocdevv.nativecook.blocks.splash.daylighticonreveal

import kotlin.math.max
import kotlin.math.min

/**
 * The measured tracks of the block and the maths that turns loop time into a frame, ported
 * value for value from `DaylightIconReveal.tsx` (the names in the doc comments are that file's).
 */
internal object DaylightIconRevealTimeline {
    /** `EASE` in the React Native file: the strong ease-out used by every reveal. */
    val Ease = DaylightIconRevealBezier(0.23, 1.0, 0.32, 1.0)

    /** `SWEEP`: the ease-in-out of the light sweep. */
    val Sweep = DaylightIconRevealBezier(0.77, 0.0, 0.175, 1.0)

    /** `IMAGE_SIZE`: the icon's layout size in dp. */
    const val IMAGE_SIZE = 256.0

    /** `BODY`: the icon's body inside the 1254 px transparent artwork (not its shadow), as fractions. */
    const val BODY_X = 82.0 / 1254.0
    const val BODY_Y = 86.0 / 1254.0
    const val BODY_WIDTH = 1093.0 / 1254.0
    const val BODY_HEIGHT = 1090.0 / 1254.0

    /**
     * `POSES`. Columns: t, horizontal extent, horizontal perspective, vertical perspective,
     * top-centre Y, top-edge inclination, vertical extent, opacity.
     */
    private val poseKeys: Array<DoubleArray> = arrayOf(
        doubleArrayOf(0.35, 4.0, 0.15, -0.3, 0.66, -0.581, 1.2, 0.0),
        doubleArrayOf(0.4, 4.0, 0.15, -0.3, 0.66, -0.581, 1.2, 0.06),
        doubleArrayOf(0.5, 3.3, 0.15, -0.25, 0.6075, -0.4072, 0.95, 0.14),
        doubleArrayOf(0.75, 2.2, 0.15, -0.27, 0.467, -0.04, 0.65, 0.48),
        doubleArrayOf(1.0, 1.3, 0.15, -0.27, 0.407, 0.035, 0.45, 0.76),
        doubleArrayOf(1.2, 0.86, 0.14, -0.27, 0.352, 0.055, 0.35, 0.94),
        doubleArrayOf(1.3, 0.769814, 0.135696, -0.263308, 0.33301, 0.054257, 0.260944, 1.0),
        doubleArrayOf(1.4, 0.695834, 0.104908, -0.216638, 0.31571, 0.038729, 0.239891, 1.0),
        doubleArrayOf(1.5, 0.653934, 0.08079, -0.149138, 0.300033, 0.036785, 0.247728, 1.0),
        doubleArrayOf(1.6, 0.617184, 0.085426, -0.102367, 0.288539, 0.0336, 0.246882, 1.0),
        doubleArrayOf(1.7, 0.584664, 0.056244, -0.080393, 0.278003, 0.025165, 0.239353, 1.0),
        doubleArrayOf(1.8, 0.56726, 0.049661, -0.026634, 0.267928, 0.023277, 0.25257, 1.0),
        doubleArrayOf(2.0, 0.517074, 0.060975, -0.038964, 0.255785, 0.01578, 0.222516, 1.0),
        doubleArrayOf(2.2, 0.5037, 0.0, 0.0, 0.24573, 0.0, 0.23549, 1.0),
        doubleArrayOf(2.5, 0.48889, 0.0, 0.0, 0.23891, 0.0, 0.22867, 1.0),
        doubleArrayOf(2.7, 0.48889, 0.0, 0.0, 0.23891, 0.0, 0.22526, 1.0),
        doubleArrayOf(3.0, 0.48889, 0.0, 0.0, 0.23891, 0.0, 0.22526, 1.0),
        doubleArrayOf(3.3, 0.5037, 0.0, 0.0, 0.23891, 0.0, 0.23208, 1.0),
        doubleArrayOf(4.0, 0.5037, 0.0, 0.0, 0.24232, 0.0, 0.23208, 1.0),
    )

    /** `HEADING_LIFT`: time and remaining lift (fraction of the title's travel). Also drives the copy line. */
    private val headingKeys: Array<DoubleArray> = arrayOf(
        doubleArrayOf(1.75, 0.32), doubleArrayOf(1.85, 0.29), doubleArrayOf(1.9, 0.24061),
        doubleArrayOf(2.0, 0.18089), doubleArrayOf(2.1, 0.1314), doubleArrayOf(2.2, 0.09215),
        doubleArrayOf(2.3, 0.06143), doubleArrayOf(2.4, 0.03754), doubleArrayOf(2.5, 0.02048),
        doubleArrayOf(2.6, 0.01024), doubleArrayOf(2.7, 0.00171), doubleArrayOf(2.9, -0.00683),
        doubleArrayOf(3.3, -0.00683), doubleArrayOf(4.0, 0.0),
    )

    /** `BUTTON_LIFT`: the same for the button. */
    private val buttonKeys: Array<DoubleArray> = arrayOf(
        doubleArrayOf(2.15, 0.25), doubleArrayOf(2.25, 0.165), doubleArrayOf(2.3, 0.13311),
        doubleArrayOf(2.4, 0.09215), doubleArrayOf(2.5, 0.06143), doubleArrayOf(2.6, 0.03754),
        doubleArrayOf(2.7, 0.02048), doubleArrayOf(2.8, 0.01024), doubleArrayOf(2.9, 0.0),
        doubleArrayOf(3.1, -0.00683), doubleArrayOf(3.5, -0.00683), doubleArrayOf(4.0, 0.0),
    )

    /** `segment`: how far [t] is between [start] and [end], clamped to 0..1. */
    fun segment(t: Double, start: Double, end: Double): Double = max(0.0, min(1.0, (t - start) / (end - start)))

    /** The remaining lift of the title and of the copy line (`HEADING_LIFT`, column 1). */
    fun headingLift(t: Double): Double = sampleChannel(t, headingKeys, 1)

    /** The remaining lift of the button (`BUTTON_LIFT`, column 1). */
    fun buttonLift(t: Double): Double = sampleChannel(t, buttonKeys, 1)

    /** `poseChannel`: one column of the pose keys at [t]. */
    fun poseChannel(t: Double, channel: Int): Double = sampleChannel(t, poseKeys, channel)

    /**
     * `sampleChannel`: a monotone cubic Hermite spline through the keys. A tangent is the harmonic
     * mean of the two neighbouring slopes, and 0 where the slope changes sign, so it never overshoots.
     */
    private fun sampleChannel(t: Double, keys: Array<DoubleArray>, channel: Int): Double {
        val last = keys.size - 1
        if (t <= keys[0][0]) return keys[0][channel]
        if (t >= keys[last][0]) return keys[last][channel]
        var i = 0
        while (keys[i + 1][0] < t) i++
        val a = keys[i][0]
        val b = keys[i + 1][0]
        val y0 = keys[i][channel]
        val y1 = keys[i + 1][channel]
        val d = (y1 - y0) / (b - a)
        val before = if (i > 0) (y0 - keys[i - 1][channel]) / (a - keys[i - 1][0]) else 0.0
        val after = if (i + 1 < last) (keys[i + 2][channel] - y1) / (keys[i + 2][0] - b) else 0.0
        val m0 = if (before * d > 0) 2 * before * d / (before + d) else 0.0
        val m1 = if (after * d > 0) 2 * after * d / (after + d) else 0.0
        val p = (t - a) / (b - a)
        val p2 = p * p
        val p3 = p2 * p
        return (2 * p3 - 3 * p2 + 1) * y0 +
            (p3 - 2 * p2 + p) * (b - a) * m0 +
            (-2 * p3 + 3 * p2) * y1 +
            (p3 - p2) * (b - a) * m1
    }

    /**
     * `imagePlane`: the 4x4 matrix (column-major, the layout of a React Native `matrix` transform)
     * that projects the icon's 256 dp square onto the screen, written into [out]. The origin is
     * the icon's top-left corner. Indices 3 and 7 are the perspective terms and 15 is always 1,
     * because every other entry is already divided by the denominator at the icon's origin.
     */
    fun imagePlane(t: Double, width: Double, height: Double, out: DoubleArray) {
        val a = poseChannel(t, 1)
        val g = poseChannel(t, 2)
        val h = poseChannel(t, 3)
        val y = poseChannel(t, 4)
        val b = poseChannel(t, 5)
        val c = poseChannel(t, 6)
        val ux = 1.0 / (IMAGE_SIZE * BODY_WIDTH)
        val u0 = -BODY_X / BODY_WIDTH - 0.5
        val vy = 1.0 / (IMAGE_SIZE * BODY_HEIGHT)
        val v0 = -BODY_Y / BODY_HEIGHT
        val denominator = 1.0 + g * u0 + h * v0
        out[0] = width * (0.5 * g + a) * ux / denominator
        out[1] = height * b * ux / denominator
        out[2] = 0.0
        out[3] = g * ux / denominator
        out[4] = width * 0.5 * h * vy / denominator
        out[5] = height * c * vy / denominator
        out[6] = 0.0
        out[7] = h * vy / denominator
        out[8] = 0.0
        out[9] = 0.0
        out[10] = 1.0
        out[11] = 0.0
        out[12] = width * ((0.5 * g + a) * u0 + 0.5 * h * v0 + 0.5) / denominator
        out[13] = height * (y + b * u0 + c * v0) / denominator
        out[14] = 0.0
        out[15] = 1.0
    }
}

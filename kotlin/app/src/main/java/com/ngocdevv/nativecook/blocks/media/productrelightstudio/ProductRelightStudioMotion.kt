package com.ngocdevv.nativecook.blocks.media.productrelightstudio

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min

// Product Relight: one 16-second film, every value a pure function of the loop time t (a port of relightMotion.ts).
//
//   0.00 –  1.00  poster: the watch on a white sweep, the Studio softbox high on the left
//   1.00 –  3.80  the softbox is dragged over the top and round to the right
//   4.10          Golden hour is tapped; 4.2 – 5.0 the paper warms and the sun drops low
//   4.40 –  7.00  the low sun is dragged across from the right to the left
//   7.30          Moody blue is tapped; 7.4 – 8.2 the sweep goes slate blue
//   8.30 –  9.70  the intensity slider goes up and back
//  10.00          Compare is tapped: the divider slides in (10.0 – 10.6) and is dragged right and left (10.7 – 11.75)
//  11.90          Compare is tapped again: the divider slides away (11.9 – 12.5)
//  12.70          Studio is tapped; 12.8 – 13.6 the rig turns back, 12.9 – 14.8 the softbox is dragged home
//  15.00 – 16.00  rest = the poster

/** The film's period in seconds (the React Native block's LOOP). */
const val LOOP = 16.0

internal object PrsMotion {
    const val IDLE = 2.0
    const val RETURN_DUR = 1.2
    const val RIG_DUR = 0.8
    const val LIGHT_REACH = 0.42
    const val LEVEL = 0.5

    const val DRAG1_A = 1.0
    const val DRAG1_B = 3.8
    const val GOLDEN_A = 4.2
    const val GOLDEN_B = 5.0
    const val DRAG2_A = 4.4
    const val DRAG2_B = 7.0
    const val MOODY_A = 7.4
    const val MOODY_B = 8.2
    const val LEVEL_UP_A = 8.3
    const val LEVEL_UP_B = 8.9
    const val LEVEL_DOWN_A = 9.1
    const val LEVEL_DOWN_B = 9.7
    const val COMPARE_ON = 10.0
    const val SPLIT_IN_B = 10.6
    const val SPLIT_RIGHT_A = 10.7
    const val SPLIT_RIGHT_B = 11.2
    const val SPLIT_LEFT_A = 11.25
    const val SPLIT_LEFT_B = 11.75
    const val COMPARE_OFF = 11.9
    const val SPLIT_OUT_B = 12.5
    const val STUDIO_A = 12.8
    const val STUDIO_B = 13.6
    const val DRAG3_A = 12.9
    const val DRAG3_B = 14.8

    val presetNames = arrayOf("Studio", "Golden hour", "Moody blue")
    val presetHints = arrayOf(
        "A large softbox on white paper.",
        "A low, warm sun and long shadows.",
        "Slate paper, a cool key, a warm edge.",
    )

    fun clamp01(x: Double): Double = min(1.0, max(0.0, x))
    fun lerp(a: Double, b: Double, k: Double): Double = a + (b - a) * k
    fun easeInOutSine(x: Double): Double = 0.5 - 0.5 * cos(PI * clamp01(x))
    fun seg(t: Double, a: Double, b: Double): Double = easeInOutSine((t - a) / (b - a))

    /** cubic-bezier(0.65, 0, 0.35, 1), the touch tweens' curve. */
    fun easeBezier(x: Double): Double {
        fun bx(u: Double) = 3 * (1 - u) * (1 - u) * u * 0.65 + 3 * (1 - u) * u * u * 0.35 + u * u * u
        fun by(u: Double) = 3 * (1 - u) * u * u + u * u * u
        val xx = clamp01(x)
        var lo = 0.0
        var hi = 1.0
        repeat(30) {
            val m = (lo + hi) / 2
            if (bx(m) < xx) lo = m else hi = m
        }
        return by((lo + hi) / 2)
    }

    /**
     * Each rig: key colour (3), key power, light height (toward the viewer, photo widths), hair light colour (3), its
     * power, ambient (3), paper (3, sRGB), shadow softness.
     */
    private val rigs = arrayOf(
        doubleArrayOf(1.0, 0.97, 0.93, 6.2, 0.8, 0.9, 0.94, 1.0, 0.5, 0.25, 0.25, 0.26, 0.93, 0.92, 0.9, 1.0),
        doubleArrayOf(1.0, 0.8, 0.58, 6.0, 0.34, 1.0, 0.88, 0.7, 0.6, 0.12, 0.125, 0.15, 0.93, 0.88, 0.8, 0.55),
        doubleArrayOf(0.86, 0.9, 1.0, 5.0, 0.6, 1.0, 0.78, 0.55, 0.9, 0.05, 0.055, 0.07, 0.22, 0.26, 0.31, 0.85),
    )

    fun weightsAt(t: Double): DoubleArray {
        val a = seg(t, GOLDEN_A, GOLDEN_B)
        val b = seg(t, MOODY_A, MOODY_B)
        val c = seg(t, STUDIO_A, STUDIO_B)
        return doubleArrayOf(1 - a + c, a * (1 - b), b * (1 - c))
    }

    fun rigMix(w0: Double, w1: Double, w2: Double, out: DoubleArray) {
        val s = max(1e-4, w0 + w1 + w2)
        for (i in 0 until 16) out[i] = (rigs[0][i] * w0 + rigs[1][i] * w1 + rigs[2][i] * w2) / s
    }

    private val keys = arrayOf(
        doubleArrayOf(0.0, -0.26, 0.26), doubleArrayOf(1.0, -0.26, 0.26), doubleArrayOf(2.4, 0.02, 0.36),
        doubleArrayOf(3.8, 0.3, 0.12), doubleArrayOf(4.4, 0.3, 0.12), doubleArrayOf(5.4, 0.34, -0.1),
        doubleArrayOf(7.0, -0.34, 0.02), doubleArrayOf(12.9, -0.34, 0.02), doubleArrayOf(13.9, -0.36, 0.14),
        doubleArrayOf(14.8, -0.26, 0.26), doubleArrayOf(16.0, -0.26, 0.26),
    )

    private fun keyAt(t: Double, c: Int): Double {
        var i = 0
        while (i < keys.size - 2 && t >= keys[i + 1][0]) i++
        val k0 = keys[max(0, i - 1)]
        val k1 = keys[i]
        val k2 = keys[i + 1]
        val k3 = keys[min(keys.size - 1, i + 2)]
        val x = easeInOutSine((t - k1[0]) / (k2[0] - k1[0]))
        val p0 = k0[c]
        val p1 = k1[c]
        val p2 = k2[c]
        val p3 = k3[c]
        // Catmull-Rom tangents, zeroed at a turn so nothing overshoots its key
        var m1 = (p2 - p0) / 2
        var m2 = (p3 - p1) / 2
        if ((p1 - p0) * (p2 - p1) <= 0) m1 = 0.0
        if ((p2 - p1) * (p3 - p2) <= 0) m2 = 0.0
        val x2 = x * x
        val x3 = x2 * x
        return (2 * x3 - 3 * x2 + 1) * p1 + (x3 - 2 * x2 + x) * m1 + (-2 * x3 + 3 * x2) * p2 + (x3 - x2) * m2
    }

    fun lightX(t: Double): Double = keyAt(t, 1)
    fun lightY(t: Double): Double = keyAt(t, 2)

    private fun hold(t: Double, a: Double, b: Double): Double =
        clamp01((t - a + 0.2) / 0.2) * clamp01((b - t + 0.15) / 0.2)

    fun grabAt(t: Double): Double = max(max(hold(t, DRAG1_A, DRAG1_B), hold(t, DRAG2_A, DRAG2_B)), hold(t, DRAG3_A, DRAG3_B))
    fun splitGrabAt(t: Double): Double = hold(t, SPLIT_RIGHT_A, SPLIT_LEFT_B)
    fun compareDipAt(t: Double): Double =
        max(clamp01(1 - abs(t - COMPARE_ON) / 0.16), clamp01(1 - abs(t - COMPARE_OFF) / 0.16))

    fun levelAt(t: Double): Double = LEVEL + 0.35 * seg(t, LEVEL_UP_A, LEVEL_UP_B) * (1 - seg(t, LEVEL_DOWN_A, LEVEL_DOWN_B))

    fun splitAt(t: Double): Double {
        val inK = seg(t, COMPARE_ON, SPLIT_IN_B)
        val right = seg(t, SPLIT_RIGHT_A, SPLIT_RIGHT_B)
        val left = seg(t, SPLIT_LEFT_A, SPLIT_LEFT_B)
        val out = seg(t, COMPARE_OFF, SPLIT_OUT_B)
        return (0.5 * inK + 0.2 * right - 0.35 * left) * (1 - out)
    }
}

/** Everything one frame shows. */
internal data class PrsValues(
    val lx: Double,
    val ly: Double,
    val w0: Double,
    val w1: Double,
    val w2: Double,
    val level: Double,
    val grab: Double,
    val split: Double,
    val splitGrab: Double,
    val dip: Double,
) {
    companion object {
        fun film(t: Double): PrsValues {
            val w = PrsMotion.weightsAt(t)
            return PrsValues(
                PrsMotion.lightX(t), PrsMotion.lightY(t), w[0], w[1], w[2], PrsMotion.levelAt(t), PrsMotion.grabAt(t),
                PrsMotion.splitAt(t), PrsMotion.splitGrabAt(t), PrsMotion.compareDipAt(t),
            )
        }

        fun mix(a: PrsValues, b: PrsValues, k: Double): PrsValues {
            fun m(x: Double, y: Double) = PrsMotion.lerp(x, y, k)
            return PrsValues(
                m(a.lx, b.lx), m(a.ly, b.ly), m(a.w0, b.w0), m(a.w1, b.w1), m(a.w2, b.w2), m(a.level, b.level),
                m(a.grab, b.grab), m(a.split, b.split), m(a.splitGrab, b.splitGrab), m(a.dip, b.dip),
            )
        }
    }
}

/** A value easing from one number to another from a moment on (or holding still). Times in seconds. */
internal class PrsTween(val from: Double, val to: Double, val start: Double, val duration: Double, val bezier: Boolean) {
    fun at(now: Double): Double {
        if (duration <= 0) return to
        val x = PrsMotion.clamp01((now - start) / duration)
        return PrsMotion.lerp(from, to, if (bezier) PrsMotion.easeBezier(x) else x)
    }

    companion object {
        fun constant(v: Double) = PrsTween(v, v, 0.0, 0.0, false)
        fun to(v: Double, from: Double, start: Double, duration: Double, bezier: Boolean = false) =
            PrsTween(from, v, start, duration, bezier)
    }
}

/** What a finger set: written only by touches, read every frame (a port of the Swift block's touch state). */
internal class PrsTouch {
    var origin = 0.0
    var manual = false
    var lx = 0.0
    var ly = 0.0
    var w0 = PrsTween.constant(1.0)
    var w1 = PrsTween.constant(0.0)
    var w2 = PrsTween.constant(0.0)
    var level = PrsMotion.LEVEL
    var split = PrsTween.constant(0.0)
    var grab = PrsTween.constant(0.0)
    var splitGrab = PrsTween.constant(0.0)
    var dragging = false
    var draggingSplit = false
    var sliding = false
    var lastActivity = -1e9

    private fun manualValues(now: Double) = PrsValues(
        lx, ly, w0.at(now), w1.at(now), w2.at(now), level, grab.at(now), split.at(now), splitGrab.at(now), 0.0,
    )

    /** -1 = manual, 0..1 = returning (eased), else the film's time + 2. */
    private fun phase(now: Double, film: Boolean): Pair<Int, Double> {
        if (!manual) {
            if (!film) return 0 to 0.0
            return 2 to (max(0.0, now - origin) % LOOP)
        }
        if (!film || dragging || draggingSplit || sliding) return 0 to 0.0
        val quiet = now - lastActivity - PrsMotion.IDLE
        if (quiet <= 0) return 0 to 0.0
        if (quiet < PrsMotion.RETURN_DUR) return 1 to PrsMotion.easeBezier(quiet / PrsMotion.RETURN_DUR)
        return 2 to ((quiet - PrsMotion.RETURN_DUR) % LOOP)
    }

    fun frame(now: Double, film: Boolean): PrsValues {
        val (kind, x) = phase(now, film)
        return when (kind) {
            0 -> if (manual) manualValues(now) else PrsValues.film(0.0)
            1 -> PrsValues.mix(manualValues(now), PrsValues.film(0.0), x)
            else -> PrsValues.film(x)
        }
    }

    /** Any touch takes the photo over where the film is. */
    fun takeOver(now: Double, film: Boolean) {
        val (kind, _) = phase(now, film)
        if (kind == 0 && manual) return
        val v = frame(now, film)
        manual = true
        lx = v.lx
        ly = v.ly
        w0 = PrsTween.constant(v.w0)
        w1 = PrsTween.constant(v.w1)
        w2 = PrsTween.constant(v.w2)
        level = v.level
        split = PrsTween.constant(v.split)
        grab = PrsTween.constant(v.grab)
        splitGrab = PrsTween.constant(v.splitGrab)
        lastActivity = now
    }
}

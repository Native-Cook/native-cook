// Murmuration Flock — the loop, the flock's pose and the scripted hand, in closed form (mf-motion.ts).
//
// A bird's place is a pure function of its seeds and the loop time: pose() below, written again in
// GLSL in MfShaders.kt. Only what a finger does to a bird is state (a displacement on a spring), and
// that is blended out in the rest stretch and cleared at the cut, so frame 12 s equals frame 0.
package com.ngocdevv.nativecook.blocks.gestures.murmurationflock

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

const val LOOP = 12.0
internal const val TAU = PI * 2

internal object Flock {
    /** birds in the GPU buffers */
    const val COUNT = 20000
    /** birds the drawn fallback shows, an even sample of the same flock */
    const val FALLBACK = 900
    /** the flock's centre, as fractions of the screen */
    const val CENTRE_X = 0.5f
    const val CENTRE_Y = 0.4f
    /** one pose unit in dp = min(width × SCALE_W, height × SCALE_H) */
    const val SCALE_W = 0.5f
    const val SCALE_H = 0.3f
    /** how far back the heading is measured, seconds */
    const val HEAD_BACK = 0.03f
    /** a bird's dart, dp: half length and half width at its smallest, plus what its depth seed adds */
    const val LENGTH = 1.7f
    const val LENGTH_ADD = 1.5f
    const val WIDTH = 0.62f
    const val WIDTH_ADD = 0.5f
    /** wing beats per loop: base + seed × range, whole numbers so the flap closes */
    const val BEATS = 18f
    const val BEATS_RANGE = 12f
    /** opacity of one bird */
    const val ALPHA = 0.62f
}

/** the ribbon the flock rests in */
internal object Cloud {
    const val ALONG = 0.92
    const val SWAY = 0.14
    const val SWAY_FREQ = 2.2
    const val RISE = 0.3
    const val RISE_FREQ = 2.7
    const val RIPPLE = 0.13
    const val RIPPLE_FREQ = 5.1
    /** the sheet's half width at its middle, how much it narrows to the tips, and its least */
    const val WIDTH = 0.5
    const val NARROW = 0.55
    const val WIDTH_MIN = 0.04
    /** the sheet swells and thins along its length, so the cloud has a body and a tail */
    const val SWELL = 0.4
    const val SWELL_FREQ = 1.7
    /** how much the sheet twists along its length: the folds are where it turns edge-on */
    const val TWIST = 2.4
    const val TWIST_WAVE = 1.2
    const val TWIST_WAVE_FREQ = 3.0
    /** the sheet is seen at this tilt */
    const val TILT = 0.72
    /** each bird's own small orbit: least and added radius */
    const val ORBIT = 0.018
    const val ORBIT_ADD = 0.07
}

/** the soft circle of the inhale */
internal object Ring {
    const val RADIUS = 0.78
    /** a bird sits between INNER × RADIUS and RADIUS, denser at the rim */
    const val INNER = 0.62
    const val SPREAD = 0.05
    /** the circle is this much smaller when the morph begins */
    const val GROW = 0.82
}

/** the morph's arc and the exhale's gust */
internal object Air {
    const val ARC = 0.12
    const val ARC_ADD = 0.22
    const val GUST = 0.1
    const val GUST_ADD = 0.26
}

/** the loop's phases, seconds */
internal object T {
    const val INHALE_A = 1.4
    const val INHALE_B = 5.4
    const val EXHALE_A = 6.4
    const val EXHALE_B = 10.4
    /** each bird starts its morph up to this much late */
    const val STAGGER = 1.1
    /** what a finger moved is blended out here */
    const val SETTLE_A = 10.7
    const val SETTLE_B = 11.7
    /** the session timer rolls back here */
    const val ROLLBACK_A = 11.2
    const val ROLLBACK_B = 11.9
    /** the ghost finger */
    const val GHOST_A = 7.0
    const val GHOST_B = 9.2
}

/** the finger */
internal object Touch {
    /** reach of the push (Gaussian), dp */
    const val RADIUS = 74f
    /** outward and sideways force, dp/s² */
    const val FLEE = 2600f
    const val SWIRL = 1500f
    /** the spring that brings a bird back to its place */
    const val SPRING = 26f
    const val DAMP = 6.5f
    /** how quickly the touch comes and goes, 1/s */
    const val EASE = 14f
    /** the film runs at this rate under a finger */
    const val FILM_RATE = 0.25
}

/** a touch hands the flock back this long after it ends, seconds */
internal const val IDLE = 2.0
/** then the film runs to the nearer end of the loop over this long, seconds */
internal const val RETURN = 1.4
/** the timer's start, seconds (4:12) */
internal const val SESSION = 252.0
/** the frozen moment under reduced motion: the circle, held */
internal const val STILL = 5.9

internal fun clamp01(v: Double) = if (v < 0.0) 0.0 else if (v > 1.0) 1.0 else v

/** 0 before a, 1 after b */
internal fun seg(t: Double, a: Double, b: Double) = clamp01((t - a) / (b - a))

internal fun smooth(v: Double) = v * v * (3 - 2 * v)

internal fun smoother(v: Double) = v * v * v * (v * (v * 6 - 15) + 10)

/** a whole-number hash of an integer (PCG), 0 … 2³²−1; the GLSL has the same one */
private fun pcg(v: Int): Long {
    val s = v * 747796405 + 2891336453L.toInt()
    val w = ((s ushr ((s ushr 28) + 4)) xor s) * 277803737
    return ((w ushr 22) xor w).toLong() and 0xFFFFFFFFL
}

/** bird i's four seeds in 0…1: along the ribbon, across it, depth, phase */
internal fun seedsOf(i: Int, out: DoubleArray) {
    val k = 4294967296.0
    out[0] = pcg(i * 4) / k
    out[1] = pcg(i * 4 + 1) / k
    out[2] = pcg(i * 4 + 2) / k
    out[3] = pcg(i * 4 + 3) / k
}

/** how far bird `d` is from the ribbon (0) to the circle (1) at t */
internal fun morphAt(t: Double, d: Double): Double {
    val late = d * T.STAGGER
    return smoother(seg(t, T.INHALE_A + late, T.INHALE_B)) - smoother(seg(t, T.EXHALE_A + late, T.EXHALE_B))
}

/** the exhale's gust at t, 0…1…0 */
internal fun gustAt(t: Double): Double {
    val s = sin(PI * seg(t, T.EXHALE_A, T.EXHALE_B))
    return s * s
}

/** bird (a, b, c, d) at loop time t, in pose units around the flock's centre, into out[at], out[at + 1] */
internal fun pose(a: Double, b: Double, c: Double, d: Double, t: Double, out: DoubleArray, at: Int) {
    val w = TAU * t / LOOP
    val u = 2 * a - 1
    val v0 = 2 * b - 1
    val v = v0 * (0.55 + 0.45 * abs(v0))
    val width = (Cloud.WIDTH * (1 - Cloud.NARROW * u * u) + Cloud.WIDTH_MIN) * (1 + Cloud.SWELL * sin(Cloud.SWELL_FREQ * u + w))
    val phi = Cloud.TWIST * u + w + Cloud.TWIST_WAVE * sin(2 * w + Cloud.TWIST_WAVE_FREQ * u)
    val k = 1 + floor(c * 3)
    val orbit = TAU * d + k * w
    val rj = Cloud.ORBIT + Cloud.ORBIT_ADD * c * c
    val cloudX = Cloud.ALONG * u + Cloud.SWAY * sin(Cloud.SWAY_FREQ * u + w) - sin(phi) * width * v + rj * cos(orbit)
    val cloudY = Cloud.RISE * sin(Cloud.RISE_FREQ * u + w) + Cloud.RIPPLE * sin(Cloud.RIPPLE_FREQ * u - 2 * w) +
        cos(phi) * width * v * Cloud.TILT + rj * sin(orbit)

    val m = morphAt(t, d)
    val spin = TAU * a + w
    val r = (Ring.RADIUS * (Ring.INNER + (1 - Ring.INNER) * sqrt(b)) + Ring.SPREAD * (c - 0.5)) * (Ring.GROW + (1 - Ring.GROW) * m)
    val ringX = r * cos(spin)
    val ringY = r * sin(spin)

    val dx = ringX - cloudX
    val dy = ringY - cloudY
    val bend = sin(PI * m) * (Air.ARC + Air.ARC_ADD * (2 * c - 1))
    var x = cloudX + dx * m - dy * bend
    var y = cloudY + dy * m + dx * bend
    val g = gustAt(t)
    if (g > 0) {
        val len = sqrt(x * x + y * y) + 0.001
        val push = g * (Air.GUST + Air.GUST_ADD * c)
        x += x / len * push
        y += y / len * push
    }
    out[at] = x
    out[at + 1] = y
}

/** the breath's word at t: how much of Settle, Inhale and Exhale shows */
internal fun wordAt(t: Double, index: Int): Double {
    val inhale = smooth(seg(t, T.INHALE_A - 0.15, T.INHALE_A + 0.25)) * (1 - smooth(seg(t, T.EXHALE_A - 0.2, T.EXHALE_A + 0.15)))
    val exhale = smooth(seg(t, T.EXHALE_A - 0.05, T.EXHALE_A + 0.3)) * (1 - smooth(seg(t, T.EXHALE_B - 0.2, T.EXHALE_B + 0.2)))
    return when (index) {
        0 -> clamp01(1 - inhale - exhale)
        1 -> inhale
        else -> exhale
    }
}

/** the faint guide circle's strength at t */
internal fun guideAt(t: Double) =
    smooth(seg(t, T.INHALE_A + 0.6, T.INHALE_B)) * (1 - smooth(seg(t, T.EXHALE_A, T.EXHALE_A + 1.2)))

/** the session timer in seconds, a real number; it counts down and rolls back in the rest */
internal fun sessionAt(t: Double): Double {
    val ran = min(t, T.ROLLBACK_A)
    val v = SESSION - ran * (1 - smooth(seg(t, T.ROLLBACK_A, T.ROLLBACK_B)))
    val up = ceil(v)
    return up - smooth(seg(up - v, 0.0, 0.3))
}

/** the ghost finger at t: x, y as fractions of the screen, and how much of it shows */
internal fun ghostAt(t: Double, out: DoubleArray) {
    val u = seg(t, T.GHOST_A, T.GHOST_B)
    val e = smooth(u)
    out[0] = 0.2 + 0.6 * e
    out[1] = Flock.CENTRE_Y + 0.1 - 0.16 * e + 0.03 * sin(u * TAU)
    out[2] = smooth(seg(u, 0.0, 0.14)) * (1 - smooth(seg(u, 0.86, 1.0)))
}

/** the frame the ticker hands the flock: indices into one FloatArray */
internal object Frame {
    const val SERIAL = 0
    const val EPOCH = 1
    const val T = 2
    const val DT = 3
    const val HOLD = 4
    const val TOUCH_X = 5
    const val TOUCH_Y = 6
    const val TOUCH_ON = 7
    const val WIDTH = 8
    const val HEIGHT = 9
    const val CENTRE_X = 10
    const val CENTRE_Y = 11
    const val UNIT = 12
    const val LENGTH = 13
}

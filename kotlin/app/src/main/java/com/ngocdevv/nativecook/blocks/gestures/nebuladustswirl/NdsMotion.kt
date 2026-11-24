// Nebula Dust Swirl — the loop, the nebula's pose and the scripted finger, in closed form (nds-motion.ts).
//
// A grain's place is a pure function of its seeds and the loop time: pose() below, written again in
// GLSL in NdsShaders.kt. Grains come in pairs (index 2j and 2j + 1) that share their seeds and sit on
// opposite arms, half a turn apart. The whole nebula turns half a turn per loop, so at t = LOOP each
// grain stands exactly where its twin stood at t = 0 and the frame repeats. Only what a finger does to
// a grain is state (a displacement on a spring), blended out in the rest stretch and cleared at the cut.
package com.ngocdevv.nativecook.blocks.gestures.nebuladustswirl

import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

const val LOOP = 10.0
internal const val TAU = PI * 2

internal object Dust {
    /** grains in the GPU buffers (even: they come in pairs) */
    const val COUNT = 40000
    /** grains the drawn fallback shows: every STEP-th, odd so both arms are sampled */
    const val FALLBACK = 1370
    const val FALLBACK_STEP = 29
    /** the nebula's centre, as fractions of the screen */
    const val CENTRE_X = 0.5f
    const val CENTRE_Y = 0.4f
    /** one pose unit in dp = min(width × SCALE_W, height × SCALE_H) */
    const val SCALE_W = 0.5f
    const val SCALE_H = 0.27f
    /** a grain's radius, dp, at its smallest, plus what its size seed adds (squared) */
    const val SIZE = 0.5
    const val SIZE_ADD = 0.8
    /** a bright star's radius, dp, and the share of grains that are stars */
    const val STAR = 1.7
    const val STAR_SHARE = 0.006
    /** twinkles per loop: base + seed × range, whole numbers */
    const val TWINKLE = 2.0
    const val TWINKLE_RANGE = 4.0
}

/** the spiral */
internal object Spiral {
    const val WIND = 3.1
    const val R_MIN = 0.06
    const val R_MAX = 1.0
    const val DENSITY = 0.85
    const val SPREAD = 0.34
    const val SPREAD_OUT = 0.5
    /** the arms' radial jitter, the same in the drawn fallback and the GPU pose (nds-motion.ts) */
    const val JITTER = 0.08
    const val GPU_JITTER = JITTER
    const val THICK = 0.09
    const val TILT = 0.9
    const val CAMERA = 3.4
    const val BREATH = 0.26
    const val ORBIT = 0.008
    const val ORBIT_ADD = 0.026
}

/** the warm core */
internal object Core {
    const val SHARE = 0.15
    const val RADIUS = 0.17
    const val FLAT = 0.8
}

/** the loop's phases, seconds */
internal object T {
    /** the ghost finger stirs the arms */
    const val GHOST_A = 1.2
    const val GHOST_B = 6.0
    /** what a finger moved is blended out here */
    const val SETTLE_A = 8.6
    const val SETTLE_B = 9.4
    /** the sleep timer rolls back here */
    const val ROLLBACK_A = 9.2
    const val ROLLBACK_B = 9.8
}

/** the finger */
internal object Touch {
    /** reach of the stir (Gaussian), dp */
    const val RADIUS = 82.0
    /** sideways force around the finger, dp/s² */
    const val SWIRL = 520.0
    /** how strongly grains are dragged along with the finger, 1/s */
    const val DRAG = 3.2
    /** the spring that brings a grain back to its place, and its damping */
    const val SPRING = 6.0
    const val DAMP = 3.0
    /** how quickly the touch comes and goes, 1/s */
    const val EASE = 12f
    /** the film runs at this rate under a finger */
    const val FILM_RATE = 0.4
    /** the finger's speed is capped here, dp/s */
    const val MAX_SPEED = 2400f
}

/** a touch hands the nebula back this long after it ends, seconds */
internal const val IDLE = 2.0
/** then the film runs to the nearer end of the loop over this long, seconds */
internal const val RETURN = 1.6
/** the sleep timer's start, seconds (38:12 left) and the whole session (45 min) */
internal const val SESSION = 2292.0
internal const val SESSION_TOTAL = 2700.0
/** the frozen moment under reduced motion */
internal const val STILL = 0.0

/** the dust's light, linear 0…1 per channel, and how bright one grain is */
internal object Tone {
    val CORE = doubleArrayOf(1.0, 0.84, 0.62)
    val WARM = doubleArrayOf(0.95, 0.6, 0.44)
    val COOL = doubleArrayOf(0.46, 0.62, 0.88)
    val TEAL = doubleArrayOf(0.52, 0.78, 0.78)
    val ROSE = doubleArrayOf(0.8, 0.5, 0.62)
    val STAR = doubleArrayOf(1.0, 0.97, 0.92)
    const val ROSE_SHARE = 0.14
    const val CORE_GAIN = 0.11
    const val ARM_GAIN = 0.17
    const val STAR_GAIN = 0.6
}

internal fun clamp01(v: Double) = if (v < 0.0) 0.0 else if (v > 1.0) 1.0 else v

/** 0 before a, 1 after b */
internal fun seg(t: Double, a: Double, b: Double) = clamp01((t - a) / (b - a))

internal fun smooth(v: Double) = v * v * (3 - 2 * v)

/** a whole-number hash of an integer (PCG), 0 … 2³²−1; the GLSL has the same one */
private fun pcg(v: Int): Long {
    val s = v * 747796405 + 2891336453L.toInt()
    val w = ((s ushr ((s ushr 28) + 4)) xor s) * 277803737
    return ((w ushr 22) xor w).toLong() and 0xFFFFFFFFL
}

/** grain i's eight seeds in 0…1 (shared by the pair): radius, spread, size, phase, height, spread', kind, tone */
internal fun seedsOf(i: Int, out: DoubleArray, at: Int = 0) {
    val pair = i ushr 1
    val k = 4294967296.0
    for (j in 0 until 8) out[at + j] = pcg(pair * 8 + j) / k
}

private fun log2(v: Double) = ln(v) / ln(2.0)

/**
 * grain with seeds s[at…at+7] on arm `arm` (0 or 1) at loop time t: screen x, y in pose units around
 * the nebula's centre, its perspective scale, and its distance from the centre in the disc (0…1).
 * nds-motion.ts's pose: 15 % core, 85 % arms, as the WGSL pose.
 */
internal fun pose(s: DoubleArray, at: Int, arm: Int, t: Double, out: DoubleArray) {
    val w = TAU * t / LOOP
    val x: Double
    val y: Double
    val h: Double
    var r: Double
    val kind = s[at + 6]
    if (kind < Core.SHARE) {
        r = Core.RADIUS * sqrt(-2 * ln(1 - 0.97 * s[at])) * 0.62
        val a = TAU * s[at + 1] + arm * PI
        x = r * cos(a)
        y = r * sin(a)
        h = Core.FLAT * Core.RADIUS * (s[at + 4] + s[at + 5] - 1)
    } else {
        r = Spiral.R_MIN + (Spiral.R_MAX - Spiral.R_MIN) * s[at].pow(Spiral.DENSITY)
        r *= 1 + Spiral.JITTER * (s[at + 5] - 0.5)
        val tri = s[at + 1] + s[at + 5] - 1
        val a = arm * PI + Spiral.WIND * log2(1 + 6 * r) + tri * (Spiral.SPREAD + Spiral.SPREAD_OUT * r)
        x = r * cos(a)
        y = r * sin(a)
        h = Spiral.THICK * (s[at + 4] - 0.5) * (1.3 - r)
    }
    // the whole nebula turns half a turn per loop; the winding breathes, most at the centre
    val inner = 1 - min(r, 1.0)
    val rot = PI * t / LOOP + Spiral.BREATH * inner * inner * sin(w + 1.3)
    val cr = cos(rot)
    val sr = sin(rot)
    var dx = x * cr - y * sr
    var dy = x * sr + y * cr
    val k = 1 + floor(s[at + 2] * 3)
    val rj = Spiral.ORBIT + Spiral.ORBIT_ADD * s[at + 2] * s[at + 2]
    dx += rj * cos(TAU * s[at + 3] + k * w)
    dy += rj * sin(TAU * s[at + 3] + k * w)
    // tilt the disc away from the viewer and look at it in perspective
    val ct = cos(Spiral.TILT)
    val st = sin(Spiral.TILT)
    val sy = dy * ct - h * st
    val z = dy * st + h * ct
    val p = Spiral.CAMERA / (Spiral.CAMERA - z)
    out[0] = dx * p
    out[1] = sy * p
    out[2] = p
    out[3] = min(r, 1.0)
}

/** the ghost finger at t: x, y in pose units around the centre, how much shows */
internal fun ghostAt(t: Double, out: DoubleArray) {
    val u = seg(t, T.GHOST_A, T.GHOST_B)
    val e = smooth(u)
    out[2] = smooth(seg(u, 0.0, 0.1)) * (1 - smooth(seg(u, 0.9, 1.0)))
    // an arc round the core, along the arms, in the disc's tilt
    val a = -0.3 + 3.4 * e
    val rad = 0.64 - 0.14 * sin(PI * e)
    out[0] = rad * cos(a)
    out[1] = rad * sin(a) * cos(Spiral.TILT) * 1.15
}

/** how much of the finger's push is shown at t */
internal fun holdAt(t: Double) = 1 - smooth(seg(t, T.SETTLE_A, T.SETTLE_B))

/** the sleep timer in seconds, a real number; it counts down and rolls back in the rest */
internal fun sessionAt(t: Double, start: Double = SESSION): Double {
    val ran = min(t, T.ROLLBACK_A)
    val v = start - ran * (1 - smooth(seg(t, T.ROLLBACK_A, T.ROLLBACK_B)))
    val up = ceil(v)
    return up - smooth(seg(up - v, 0.0, 0.3))
}

/** the core's breath, 0…1, twice per loop */
internal fun glowAt(t: Double) = 0.5 - 0.5 * cos(TAU * 2 * t / LOOP)

/** the frame the ticker hands the dust: indices into one FloatArray */
internal object Frame {
    const val SERIAL = 0
    const val EPOCH = 1
    const val T = 2
    const val DT = 3
    const val HOLD = 4
    const val TOUCH_X = 5
    const val TOUCH_Y = 6
    const val TOUCH_ON = 7
    const val PUSH_X = 8
    const val PUSH_Y = 9
    const val WIDTH = 10
    const val HEIGHT = 11
    const val CENTRE_X = 12
    const val CENTRE_Y = 13
    const val UNIT = 14
    const val LENGTH = 15
}

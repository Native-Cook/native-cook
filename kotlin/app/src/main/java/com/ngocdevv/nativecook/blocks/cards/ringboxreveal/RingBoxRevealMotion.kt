package com.ngocdevv.nativecook.blocks.cards.ringboxreveal

import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

// The film and the scene's fixed numbers, from ringMotion.ts and ringScene.ts. glTF space: y up, metres, the camera
// in front (+z). Every film value is a pure function of the loop time t, so t = LOOP gives the same frame as t = 0.
//
//  0.00 – 0.70  poster: the shut navy box on the paper, the camera drifting
//  0.70 – 0.88  anticipation: the lid presses down a hair
//  0.88 – 1.12  the hinge snaps: the lid flies up to 104 deg (6 % past), a crisp haptic; glints burst at 0.98
//  1.12 – 1.60  it settles on its spring
//  1.15 – 2.35  the camera comes round to the front: the ring shot
//  2.35 / 3.05 / 3.75  the metal picker: yellow gold, rose gold, back to platinum (0.45 s blends)
//  3.95 – 5.05  macro push on the stone (the box turns a little so the glints travel, 4.2 – 8.2)
//  4.75 / 5.45 / 6.15  the stone picker: sapphire, morganite, back to diamond
//  6.70 / 6.95  the carat steps to 1.75 and 2.00 ct; 7.75 / 8.00 back to 1.75 and 1.50 ct
//  8.00 – 9.20  the camera pulls back to the open box
//  8.70         "Book a private viewing" is pressed: it reads "Viewing requested" until 10.2
//  9.40 – 10.25 the lid closes; it lands with a tiny rebound (10.25 – 10.47, soft haptic)
// 10.47 – 11.5  poster; last frame = first frame

internal class RingVec(val x: Double, val y: Double, val z: Double)

internal class RingShot(val eye: RingVec, val target: RingVec)

internal class RingMetal(val name: String, val short: String, val color: Int, val rough: Double, val price: Double)

internal class RingStone(val name: String, val color: Int, val glow: Double, val price: Double)

internal object RingMotion {
    const val LOOP = 11.5

    const val PRESS = 0.7
    const val SNAP = 0.88
    const val SNAP_END = 1.12
    const val SETTLE_END = 1.6
    const val BURST = 0.98
    const val TO_RING = 1.15
    const val TO_RING_END = 2.35
    const val TO_MACRO = 3.95
    const val TO_MACRO_END = 5.05
    const val TURN = 4.2
    const val TURN_END = 8.2
    const val BACK = 8.0
    const val BACK_END = 9.2
    const val BOOK = 8.7
    const val BOOKED = 10.2
    const val CLOSE = 9.4
    const val CLOSE_END = 10.25
    const val LAND_END = 10.47

    val METAL_STEPS = doubleArrayOf(2.35, 3.05, 3.75)
    val STONE_STEPS = doubleArrayOf(4.75, 5.45, 6.15)
    val CARAT_STEPS = doubleArrayOf(6.7, 6.95, 7.75, 8.0)
    val CARAT_AT = intArrayOf(1, 2, 1, 0)
    const val OVERSHOOT = 0.06
    const val BLEND_S = 0.45
    const val CARAT_S = 0.28
    const val IDLE = 2.0
    const val RETURN_DURATION = 2.0
    const val BOOKED_TOUCH = 1.5

    // ------------------------------------------------------------ scene

    const val BOX_W = 0.05
    const val BOX_D = 0.046
    const val BOX_RV = 0.015
    const val BASE_R = 0.0032
    const val BASE_H = 0.025
    val LID_HINGE = RingVec(0.0, 0.025, -0.023)
    const val LID_OPEN = 104 * PI / 180
    const val RING_Y = 0.0238
    val STONE_C = RingVec(0.0, 0.0384, 0.0)
    const val STONE_R = 0.0037
    const val STONE_CROWN = 0.0012
    const val STONE_LOCAL_Y = 0.0146

    val METALS = arrayOf(
        RingMetal("Platinum", "Platinum", 0xE5E4E2, 0.08, 0.0),
        RingMetal("Yellow gold", "Yellow", 0xF0C77F, 0.1, -350.0),
        RingMetal("Rose gold", "Rose", 0xEBB39B, 0.1, -350.0),
    )
    val STONES = arrayOf(
        RingStone("Diamond", 0xF6F8FF, 1.15, 0.0),
        RingStone("Sapphire", 0x2F56D8, 1.5, -2400.0),
        RingStone("Morganite", 0xF3AE9C, 1.2, -3900.0),
    )
    const val CARAT_MIN = 1.0
    const val CARAT_MAX = 2.0
    const val CARAT_STEP = 0.25
    const val CARAT_MODEL = 1.5
    const val PRICE_BASE = 9_800.0
    const val PRICE_PER_CT = 6_200.0

    const val HFOV_DEG = 30.0
    const val CAMERA_NEAR = 0.004
    const val CAMERA_FAR = 5.0

    val POSTER = RingShot(RingVec(0.1, 0.114, 0.16), RingVec(0.0, 0.028, 0.0))
    val OPEN = RingShot(RingVec(0.094, 0.118, 0.158), RingVec(0.0, 0.034, 0.0))
    val RING = RingShot(RingVec(0.03, 0.0663, 0.142), RingVec(0.0, 0.03, 0.0))
    val MACRO = RingShot(RingVec(0.015, 0.0487, 0.0461), RingVec(0.0, 0.0366, 0.0))

    /** Light (Filament units). The IBL is Poly Haven's studio_small_08 baked with cmgen. */
    const val IBL_INTENSITY = 36_000f
    val KEY_DIRECTION = floatArrayOf(0.35f, -0.85f, -0.4f)
    const val KEY_LUX = 80_000f
    val FILL_DIRECTION = floatArrayOf(-0.7f, -0.3f, -0.6f)
    const val FILL_LUX = 16_000f
    const val EXPOSURE = 0f

    /** Where the glints look for light: unit vectors towards the light and a strength. */
    val GLINT_LIGHTS = arrayOf(
        doubleArrayOf(-0.356, 0.865, 0.407, 1.0),
        doubleArrayOf(0.722, 0.31, 0.619, 0.45),
        doubleArrayOf(-0.2, 0.55, 0.81, 0.8),
        doubleArrayOf(0.55, 0.75, -0.37, 0.6),
    )

    const val BG_TOP = 0xF3EFE8
    const val BG_BOTTOM = 0xE6E0D6
    const val INK = 0x1B1A18
    const val MUTE = 0x6A645B
    const val NAVY = 0x18213D

    const val ATELIER = "Atelier Mira"
    const val RING_NAME = "Eloise Solitaire"

    // ------------------------------------------------------------ helpers

    fun clamp01(x: Double): Double = if (x < 0) 0.0 else if (x > 1) 1.0 else x
    fun seg(t: Double, a: Double, b: Double): Double = clamp01((t - a) / (b - a))
    fun lerp(a: Double, b: Double, k: Double): Double = a + (b - a) * k
    fun easeInOutCubic(x: Double): Double = if (x < 0.5) 4 * x * x * x else 1 - (-2 * x + 2).pow(3) / 2
    fun easeOutCubic(x: Double): Double = 1 - (1 - x).pow(3)
    fun easeOutQuart(x: Double): Double = 1 - (1 - x).pow(4)
    fun easeInOutSine(x: Double): Double = -(cos(PI * x) - 1) / 2
    fun easeInCubic(x: Double): Double = x * x * x
    fun easeOutQuad(x: Double): Double = 1 - (1 - x) * (1 - x)

    /** Easing.bezier(0.65, 0, 0.35, 1). */
    fun easeTouch(x: Double): Double {
        if (x <= 0) return 0.0
        if (x >= 1) return 1.0
        fun b(s: Double, p1: Double, p2: Double) = 3 * (1 - s) * (1 - s) * s * p1 + 3 * (1 - s) * s * s * p2 + s * s * s
        var lo = 0.0
        var hi = 1.0
        var s = x
        repeat(40) {
            s = (lo + hi) / 2
            if (b(s, 0.65, 0.35) < x) lo = s else hi = s
        }
        return b(s, 0.0, 1.0)
    }

    /** Seeded hash 0..1. */
    fun hash(i: Double): Double {
        val s = sin(i * 12.9898 + 78.233) * 43758.5453
        return s - floor(s)
    }

    /** sRGB channel 0..255 to linear. */
    fun linear(hex: Int): DoubleArray = DoubleArray(3) { i ->
        val c = ((hex shr (16 - 8 * i)) and 255) / 255.0
        if (c <= 0.04045) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
    }

    // ------------------------------------------------------------ the film

    fun filmOpen(t: Double): Double {
        if (t < PRESS) return 0.0
        if (t < SNAP) return -0.012 * easeOutCubic(seg(t, PRESS, SNAP))
        if (t < SNAP_END) return lerp(-0.012, 1 + OVERSHOOT, easeOutQuart(seg(t, SNAP, SNAP_END)))
        if (t < SETTLE_END) {
            val u = seg(t, SNAP_END, SETTLE_END)
            return 1 + OVERSHOOT * exp(-6 * u) * cos(3 * PI * u)
        }
        if (t < CLOSE) return 1.0
        if (t < CLOSE_END) return 1 - easeInOutCubic(seg(t, CLOSE, CLOSE_END))
        if (t < LAND_END) return 0.016 * sin(PI * seg(t, CLOSE_END, LAND_END))
        return 0.0
    }

    fun filmShot(t: Double): Double {
        if (t < TO_MACRO) return easeInOutSine(seg(t, TO_RING, TO_RING_END))
        if (t < BACK) return 1 + easeInOutSine(seg(t, TO_MACRO, TO_MACRO_END))
        return 2 * (1 - easeInOutCubic(seg(t, BACK, BACK_END)))
    }

    fun filmYaw(t: Double): Double {
        val u = seg(t, TURN, TURN_END)
        val env = (1 - cos(2 * PI * u)) / 2
        return 0.26 * sin(2 * PI * u) * env
    }

    fun filmBurst(t: Double): Double =
        easeOutCubic(seg(t, BURST, BURST + 0.2)) * (1 - seg(t, BURST + 0.2, BURST + 1.5)).pow(1.5)

    /** A picker's film state from its home: [from offset, to offset, blend 0..1]. */
    fun filmSteps(t: Double, steps: DoubleArray, out: DoubleArray) {
        var k = -1
        for (i in steps.indices) if (t >= steps[i]) k = i
        if (k < 0) {
            out[0] = 0.0; out[1] = 0.0; out[2] = 0.0
            return
        }
        out[0] = k.toDouble()
        out[1] = (k + 1).toDouble()
        out[2] = easeInOutSine(seg(t, steps[k], steps[k] + BLEND_S))
    }

    fun caratDir(home: Double): Double = if (home <= 1.5) 1.0 else -1.0

    fun filmCaratStep(t: Double, home: Double): Double {
        var at = 0
        for (i in CARAT_STEPS.indices) if (t >= CARAT_STEPS[i]) at = CARAT_AT[i]
        return home + 0.25 * at * caratDir(home)
    }

    fun filmCarat(t: Double, home: Double): Double {
        var prev = 0
        var at = 0
        var k = -1
        for (i in CARAT_STEPS.indices) {
            if (t >= CARAT_STEPS[i]) {
                prev = at
                at = CARAT_AT[i]
                k = i
            }
        }
        if (k < 0) return home
        val e = easeOutCubic(seg(t, CARAT_STEPS[k], CARAT_STEPS[k] + CARAT_S))
        return home + 0.25 * caratDir(home) * lerp(prev.toDouble(), at.toDouble(), e)
    }

    fun filmBooked(t: Double): Boolean = t >= BOOK && t < BOOKED

    /** How much the stone can be seen (and sparkle) for a lid opening. */
    fun sparkleOf(o: Double): Double = easeInOutSine(seg(o, 0.35, 0.85))

    fun priceOf(metal: Int, stone: Int, carat: Double): String {
        val ctFactor = if (stone == 0) 1.0 else 0.35
        val v = PRICE_BASE + METALS[metal].price + STONES[stone].price + (carat - CARAT_MODEL) * PRICE_PER_CT * ctFactor
        val r = (v / 50).roundToInt() * 50
        return "$" + "%,d".format(java.util.Locale.US, r)
    }

    // ------------------------------------------------------------ camera

    /** out = [eye xyz, target xyz]. */
    private fun blendShot(a: DoubleArray, b: RingShot, k: Double, out: DoubleArray) {
        val az0 = atan2(a[0], a[2])
        var az1 = atan2(b.eye.x, b.eye.z)
        if (az1 - az0 > PI) az1 -= 2 * PI
        if (az0 - az1 > PI) az1 += 2 * PI
        val r0 = hypot(a[0], a[2])
        val r1 = hypot(b.eye.x, b.eye.z)
        val az = lerp(az0, az1, k)
        val r = lerp(r0, r1, k)
        val ey = lerp(a[1], b.eye.y, k)
        val tx = lerp(a[3], b.target.x, k)
        val ty = lerp(a[4], b.target.y, k)
        val tz = lerp(a[5], b.target.z, k)
        out[0] = sin(az) * r
        out[1] = ey
        out[2] = cos(az) * r
        out[3] = tx
        out[4] = ty
        out[5] = tz
    }

    /** Camera for a lid opening, the shot and the loop time: out = [eye xyz, target xyz]. */
    fun cameraAt(o: Double, shot: Double, t: Double, out: DoubleArray) {
        out[0] = POSTER.eye.x; out[1] = POSTER.eye.y; out[2] = POSTER.eye.z
        out[3] = POSTER.target.x; out[4] = POSTER.target.y; out[5] = POSTER.target.z
        blendShot(out, OPEN, easeInOutSine(seg(o, 0.0, 1.0)), out)
        blendShot(out, RING, clamp01(shot), out)
        blendShot(out, MACRO, clamp01(shot - 1), out)
        val d = sin(2 * PI * t / LOOP)
        val az = atan2(out[0] - out[3], out[2] - out[5]) + 0.06 * d
        val r = hypot(out[0] - out[3], out[2] - out[5])
        out[0] = out[3] + sin(az) * r
        out[2] = out[5] + cos(az) * r
        out[1] += 0.002 * d
    }

    // ------------------------------------------------------------ projection and glints

    fun cameraBasis(c: DoubleArray, width: Double, out: DoubleArray) {
        var fx = c[3] - c[0]
        var fy = c[4] - c[1]
        var fz = c[5] - c[2]
        val fl = sqrt(fx * fx + fy * fy + fz * fz).takeIf { it > 0 } ?: 1.0
        fx /= fl; fy /= fl; fz /= fl
        var rx = -fz
        var rz = fx
        val rl = hypot(rx, rz).takeIf { it > 0 } ?: 1.0
        rx /= rl; rz /= rl
        out[0] = fx; out[1] = fy; out[2] = fz
        out[3] = rx; out[4] = rz
        out[5] = -rz * fy
        out[6] = rz * fx - rx * fz
        out[7] = rx * fy
        out[8] = width / 2 / tan(HFOV_DEG * PI / 360)
    }

    fun project(c: DoubleArray, b: DoubleArray, w: Double, h: Double, x: Double, y: Double, z: Double, out: DoubleArray): Boolean {
        val px = x - c[0]
        val py = y - c[1]
        val pz = z - c[2]
        val d = px * b[0] + py * b[1] + pz * b[2]
        out[2] = d
        if (d < 0.005) return false
        out[0] = w / 2 + (px * b[3] + pz * b[4]) / d * b[8]
        out[1] = h / 2 - (px * b[5] + py * b[6] + pz * b[7]) / d * b[8]
        return true
    }

    /** Facet i of the stone's crown: out = [nx, ny, nz, px, py, pz] in stone space. */
    fun facet(i: Int, radius: Double, crown: Double, out: DoubleArray) {
        val table = i % 5 == 0
        val th = if (table) 0.04 * hash(i + 3.0) else 0.38 + 0.32 * hash(i.toDouble())
        val ph = 2 * PI * i / 40 + 0.35 * hash(i + 7.0)
        val st = sin(th)
        out[0] = st * cos(ph)
        out[1] = cos(th)
        out[2] = st * sin(ph)
        val rr = if (table) radius * 0.45 * hash(i + 11.0) else radius * (0.62 + 0.3 * hash(i + 13.0))
        out[3] = cos(ph) * rr
        out[4] = if (table) crown else crown * (1 - (rr / radius - 0.55) * 1.2)
        out[5] = sin(ph) * rr
    }

    fun glint(n: DoubleArray, v: DoubleArray): Double {
        val d = n[0] * v[0] + n[1] * v[1] + n[2] * v[2]
        if (d <= 0) return 0.0
        val rx = 2 * d * n[0] - v[0]
        val ry = 2 * d * n[1] - v[1]
        val rz = 2 * d * n[2] - v[2]
        var s = 0.0
        for (l in GLINT_LIGHTS) {
            val c = rx * l[0] + ry * l[1] + rz * l[2]
            if (c > 0.9) s += l[3] * c.pow(90)
        }
        return s
    }
}

/** One animated value: still, a timing (with a delay and a curve) or a damped spring (Reanimated's withSpring). */
internal class RingTrack private constructor(
    val from: Double,
    val to: Double,
    val t0: Double,
    val dur: Double,
    private val v0: Double,
    private val curve: ((Double) -> Double)?,
    private val w: Double,
    private val z: Double,
) {
    val end: Double get() = t0 + dur

    fun at(now: Double): Double {
        if (now >= t0 + dur) return to
        if (now <= t0) return from
        val c = curve
        if (c != null) return from + (to - from) * c(RingMotion.clamp01((now - t0) / dur))
        val t = now - t0
        val e = from - to
        if (z < 1) {
            val wd = w * sqrt(1 - z * z)
            return to + exp(-z * w * t) * (e * cos(wd * t) + (v0 + z * w * e) / wd * sin(wd * t))
        }
        return to + exp(-w * t) * (e + (v0 + w * e) * t)
    }

    fun moving(now: Double): Boolean = now < t0 + dur

    companion object {
        fun still(v: Double) = RingTrack(v, v, 0.0, 0.0, 0.0, { it }, 0.0, 0.0)

        fun timing(from: Double, to: Double, now: Double, dur: Double, delay: Double = 0.0, curve: (Double) -> Double = { it }) =
            RingTrack(from, to, now + delay, dur, 0.0, curve, 0.0, 0.0)

        fun spring(from: Double, to: Double, now: Double, damping: Double, stiffness: Double, mass: Double = 1.0, velocity: Double = 0.0): RingTrack {
            val w = sqrt(stiffness / mass)
            val z = damping / (2 * sqrt(stiffness * mass))
            return RingTrack(from, to, now, minOf(4.0, 6 / (z * w)), velocity, null, w, z)
        }
    }
}

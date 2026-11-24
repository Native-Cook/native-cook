package com.ngocdevv.nativecook.blocks.progress.parcelpackprogress

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sign
import kotlin.math.sin

internal class ParcelFlap(val node: String, val pivot: DoubleArray, val axis: FloatArray, val open: Double, val sink: Double)

/**
 * parcelScene.ts and parcelMotion.ts: the scene's numbers and the film as pure functions of t, so t = LOOP gives the
 * same frame as t = 0. A finger adds no state of its own: a tap moves t to a step's time, a drag adds an orbit angle.
 */
internal object ParcelMotion {
    const val LOOP = 10.0

    val FLAPS = arrayOf(
        ParcelFlap("FlapLeft", doubleArrayOf(-0.15, 0.19, 0.0), floatArrayOf(0f, 0f, 1f), 128.0, 0.0056),
        ParcelFlap("FlapRight", doubleArrayOf(0.15, 0.19, 0.0), floatArrayOf(0f, 0f, 1f), -136.0, 0.0056),
        ParcelFlap("FlapFront", doubleArrayOf(0.0, 0.19, 0.11), floatArrayOf(1f, 0f, 0f), 146.0, 0.0),
        ParcelFlap("FlapBack", doubleArrayOf(0.0, 0.19, -0.11), floatArrayOf(1f, 0f, 0f), -110.0, 0.0),
    )
    const val BOX_YAW = -30.0
    val TAPE_A = doubleArrayOf(-0.1503, 0.135, 0.0)
    val TAPE_TOP = doubleArrayOf(-0.1509, 0.1906, 0.0)
    val TAPE_B = doubleArrayOf(0.1503, 0.1912, 0.0)
    const val TAPE_SIDE = 0.055
    const val TAPE_SEAM = 0.3
    val LABEL = doubleArrayOf(0.058, 0.1908, 0.066)
    const val LABEL_YAW = 4f
    val TILT_AXIS = floatArrayOf(0.94f, 0f, 0.34f)

    val TARGET = doubleArrayOf(0.0, 0.15, 0.0)
    const val DIST = 1.25
    const val EL_REST = 30.0
    const val FRAME_W = 0.62
    const val FRAME_H = 0.52
    const val CAMERA_NEAR = 0.05
    const val CAMERA_FAR = 30.0

    const val IBL_INTENSITY = 30_000f
    val KEY_DIRECTION = floatArrayOf(0.4f, -0.85f, -0.35f)
    const val KEY_LUX = 72_000f
    val FILL_DIRECTION = floatArrayOf(-0.7f, -0.3f, -0.6f)
    const val FILL_LUX = 15_000f
    const val EXPOSURE = 0f

    const val BACKDROP = 0xE4DCCE
    const val PANEL = 0xFBFAF7
    const val CARD = 0xFFFFFF
    const val INK = 0x1F1D1A
    const val MUTE = 0x7C766B
    const val GREEN = 0x1F5C4A
    const val GREEN_SOFT = 0xE3EEE9
    const val TRACK = 0xDDD7CB

    const val SHOP = "Carton & Co."
    const val ORDER = "Order 4821"
    const val ARRIVES = "Arrives Thursday by 6 PM"
    val STEP_TITLES = arrayOf("Packed", "Sealed", "Labelled", "On its way")
    val STEP_TIMES = arrayOf("9:41 AM", "9:44 AM", "9:47 AM", "9:52 AM")
    val HEADLINES = arrayOf("Packing your order", "Sealing the box", "Adding the label", "Waiting for the courier", "On its way")
    const val STEP_N = 4

    private val FOLDS = doubleArrayOf(0.8, 1.45, 0.98, 1.63, 1.5, 2.2, 1.72, 2.4)
    private const val TAPE = 2.75
    private const val TAPE_END = 3.95
    private const val LABEL_DROP_AT = 4.6
    const val LABEL_LAND = 4.95
    private const val LABEL_SETTLE = 5.4
    private const val BACK_UP_AT = 6.25
    private const val GO = 6.55
    private const val GONE = 7.45
    const val SWAP = 7.65
    private const val RESET = 7.8
    private const val RESET_END = 8.15
    private const val ARRIVE = 7.9
    private const val ARRIVE_END = 8.75
    const val REST = 8.75

    val DONE = doubleArrayOf(2.4, 4.05, 5.4, 7.3)
    val JUMP = doubleArrayOf(2.7, 4.4, 5.75, 7.6)
    const val REDUCED_T = 5.75

    private const val EL_LIFT = 7.0
    private const val ZOOM_IN = 0.08
    private val GHOST = doubleArrayOf(2.55, 2.75, 3.75, 3.9, 4.6)
    const val ORBIT_FILM = 18.0
    const val ORBIT_SPAN = 70.0
    private const val ORBIT_MAX = 25.0

    private const val SLIDE = 1.15
    private const val BACK_UP = 0.03
    private const val ARRIVE_BACK = 0.6
    private const val ARRIVE_YAW = 7.0
    private const val LABEL_DROP = 0.2
    private const val LABEL_TILT = 14.0
    private const val LABEL_SQUASH = 0.1
    private const val BOX_SQUASH = 0.012
    private const val FALL = 0.62
    private const val BOUNCE_1 = 0.06
    private const val BOUNCE_2 = 0.018

    fun clamp01(x: Double): Double = if (x < 0) 0.0 else if (x > 1) 1.0 else x
    fun seg(t: Double, a: Double, b: Double): Double = clamp01((t - a) / (b - a))
    fun lerp(a: Double, b: Double, k: Double): Double = a + (b - a) * k
    fun easeInOutSine(x: Double): Double = -(cos(PI * x) - 1) / 2
    fun easeInOutCubic(x: Double): Double = if (x < 0.5) 4 * x * x * x else 1 - (-2 * x + 2).pow(3) / 2
    fun easeInOutQuad(x: Double): Double = if (x < 0.5) 2 * x * x else 1 - (-2 * x + 2).pow(2) / 2
    fun easeOutQuad(x: Double): Double = 1 - (1 - x) * (1 - x)
    fun easeOutCubic(x: Double): Double = 1 - (1 - x).pow(3)
    fun easeInCubic(x: Double): Double = x * x * x
    fun easeOutBack(x: Double, c: Double): Double {
        val u = x - 1
        return 1 + (c + 1) * u * u * u + c * u * u
    }

    /** A flap's way down over its window k: 1 (open) falls to 0 at FALL, then two small rebounds. */
    fun flapFall(k: Double): Double {
        if (k <= 0) return 1.0
        if (k >= 1) return 0.0
        if (k < FALL) {
            val u = k / FALL
            return 1 - u * u
        }
        val mid = FALL + (1 - FALL) * 0.63
        if (k < mid) {
            val u = (k - FALL) / (mid - FALL)
            return BOUNCE_1 * 4 * u * (1 - u)
        }
        val u = (k - mid) / (1 - mid)
        return BOUNCE_2 * 4 * u * (1 - u)
    }

    fun flapOpen(i: Int, t: Double): Double =
        if (t >= SWAP) 1.0 else flapFall(seg(t, FOLDS[i * 2], FOLDS[i * 2 + 1]))

    fun tapeAt(t: Double, out: DoubleArray) {
        if (t >= SWAP) {
            out[0] = 0.0
            out[1] = 0.0
            out[2] = 0.0
            return
        }
        val p = easeInOutSine(seg(t, TAPE, TAPE_END)) * (TAPE_SIDE * 2 + TAPE_SEAM)
        out[0] = clamp01(p / TAPE_SIDE)
        out[1] = clamp01((p - TAPE_SIDE) / TAPE_SEAM)
        out[2] = clamp01((p - TAPE_SIDE - TAPE_SEAM) / TAPE_SIDE)
    }

    private fun landRing(t: Double, decay: Double, freq: Double): Double {
        val tau = t - LABEL_LAND
        if (tau <= 0 || t >= LABEL_SETTLE) return 0.0
        return exp(-decay * tau) * sin(freq * tau) * (1 - seg(t, LABEL_SETTLE - 0.12, LABEL_SETTLE))
    }

    /** out = [height above the lid (m), size 0..1, tilt (deg), squash]. */
    fun labelAt(t: Double, out: DoubleArray) {
        if (t < LABEL_DROP_AT || t >= SWAP) {
            out[0] = LABEL_DROP
            out[1] = 0.0
            out[2] = LABEL_TILT
            out[3] = 0.0
            return
        }
        val k = seg(t, LABEL_DROP_AT, LABEL_LAND)
        out[0] = LABEL_DROP * (1 - k * k)
        out[1] = easeOutCubic(seg(k, 0.0, 0.4))
        out[2] = LABEL_TILT * (1 - easeInOutSine(k))
        out[3] = LABEL_SQUASH * landRing(t, 9.0, 24.0)
    }

    fun boxSquash(t: Double): Double = 1 - BOX_SQUASH * landRing(t, 12.0, 26.0)

    fun slideX(t: Double): Double {
        if (t < BACK_UP_AT) return 0.0
        if (t < GO) return -BACK_UP * easeOutCubic(seg(t, BACK_UP_AT, GO))
        if (t < SWAP) return lerp(-BACK_UP, SLIDE, easeInCubic(seg(t, GO, GONE)))
        return lerp(-SLIDE, 0.0, easeOutBack(seg(t, ARRIVE, ARRIVE_END), ARRIVE_BACK))
    }

    fun yawAt(t: Double): Double =
        if (t < SWAP) BOX_YAW else BOX_YAW + ARRIVE_YAW * (1 - easeOutCubic(seg(t, ARRIVE, ARRIVE_END)))

    private fun camIn(t: Double): Double = easeInOutSine(seg(t, 0.8, 2.4)) - easeInOutSine(seg(t, 5.8, 6.7))

    private fun filmOrbit(t: Double): Double =
        ORBIT_FILM * (easeInOutSine(seg(t, GHOST[1], GHOST[2])) - easeOutCubic(seg(t, GHOST[3], GHOST[4])))

    /** The film's finger: out = [opacity, pressed, how far along its drag]. */
    fun ghostAt(t: Double, out: DoubleArray) {
        out[0] = seg(t, GHOST[0] - 0.2, GHOST[0]) * (1 - seg(t, GHOST[3], GHOST[3] + 0.2))
        out[1] = seg(t, GHOST[0], GHOST[0] + 0.1) * (1 - seg(t, GHOST[3] - 0.05, GHOST[3] + 0.08))
        out[2] = easeInOutSine(seg(t, GHOST[1], GHOST[2]))
    }

    fun tanHalfFovY(aspect: Double): Double = max(FRAME_H / 2, FRAME_W / 2 / aspect) / DIST

    fun cameraAt(t: Double, orbit: Double, out: DoubleArray) {
        val k = camIn(t)
        val az = (filmOrbit(t) + orbit) * PI / 180
        val el = (EL_REST + EL_LIFT * k) * PI / 180
        val d = DIST * (1 - ZOOM_IN * k)
        out[0] = TARGET[0] + d * cos(el) * sin(az)
        out[1] = TARGET[1] + d * sin(el)
        out[2] = TARGET[2] + d * cos(el) * cos(az)
        out[3] = TARGET[0]
        out[4] = TARGET[1]
        out[5] = TARGET[2]
    }

    fun orbitFromDrag(dx: Double, width: Double): Double {
        val a = -dx / width * ORBIT_SPAN
        val m = abs(a)
        if (m <= ORBIT_MAX) return a
        val over = m - ORBIT_MAX
        return sign(a) * (ORBIT_MAX + over * 12 / (over + 12))
    }

    private fun resetAt(t: Double): Double = seg(t, RESET, RESET_END)

    fun doneAt(i: Int, t: Double): Double = seg(t, DONE[i], DONE[i] + 0.25) * (1 - resetAt(t))

    fun checkScale(i: Int, t: Double): Double = 0.5 + 0.5 * easeOutBack(seg(t, DONE[i], DONE[i] + 0.32), 2.2)

    fun activeRow(t: Double): Double {
        if (t >= RESET) return 0.0
        var a = 0.0
        for (i in 0 until STEP_N - 1) a += easeInOutCubic(seg(t, DONE[i], DONE[i] + 0.4))
        return a
    }

    fun activeAlpha(t: Double): Double =
        1 - seg(t, DONE[STEP_N - 1], DONE[STEP_N - 1] + 0.25) + seg(t, RESET + 0.1, RESET_END)

    fun headlineAlpha(j: Int, t: Double): Double {
        val before = if (j == 0) 1.0 else doneAt(j - 1, t)
        val after = if (j == STEP_N) 0.0 else doneAt(j, t)
        return clamp01(before - after)
    }

    fun pulseAt(t: Double): Double = 0.5 - 0.5 * cos(2 * PI * t)
}

package com.ngocdevv.nativecook.blocks.pickers.combinationsafedial

import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * safeScene.ts and safeMotion.ts: the scene's numbers and the film as pure functions of t, so t = LOOP gives the same
 * frame as t = 0. The unlock is one timeline `u` (0 = locked, UNLOCK = door open): handle, then bolts, then door; the
 * film plays it forward and back, and a finger's right code plays the same timeline.
 *
 *  0.00 – 0.70  rest: locked, dial at 0
 *  0.70 – 1.70  the dial turns clockwise to the first number
 *  2.05 – 3.05  counter-clockwise to the second
 *  3.40 – 4.30  clockwise to the third
 *  4.50 – 6.25  u 0 -> 1.75: handle swings (0 – 0.5), bolts draw in (0.22 – 0.78), door opens (0.65 – 1.75)
 *  6.25 – 7.30  held open, warm light through the gap
 *  7.30 – 8.75  u 1.75 -> 0: door shuts, bolts go out, handle returns
 *  8.70 – 9.40  the dial returns to 0, the entries clear
 *  9.40 – 10.0  rest; last frame = first frame
 */
internal object SafeMotion {
    const val LOOP = 10.0
    const val DIAL_N = 40
    const val DEG_PER_NUMBER = 360.0 / DIAL_N

    // ------------------------------------------------------------ scene (glTF space: y up, metres, door faces +z)

    /** The door turns about y through its hinge (opening towards the viewer is a negative angle). */
    val DOOR_HINGE = floatArrayOf(-0.235f, 0.333f, 0.188f)

    /** In the Door node's frame: the door's centre, the dial's and the handle's pivots (both turn about z). */
    val DOOR_CENTRE = floatArrayOf(0.235f, 0f, 0f)
    val DIAL_PIVOT = floatArrayOf(0.235f, 0.052f, 0f)
    val HANDLE_PIVOT = floatArrayOf(0.235f, -0.112f, -0.001f)
    const val DOOR_RADIUS = 0.205
    const val BORE_RADIUS = 0.213

    /** The housing's front plane (world z) and the dial's face (world z). */
    const val FRONT_Z = 0.17
    const val DIAL_FACE_Z = 0.194

    /** Eight bolts, each at this angle clockwise from straight up; they slide this far towards the centre. */
    const val BOLT_COUNT = 8
    private const val BOLT_FIRST_DEG = 22.5
    private const val BOLT_STEP_DEG = 45.0
    const val BOLT_TRAVEL = 0.03

    private val TARGET = doubleArrayOf(0.0, 0.325, 0.0)
    private const val DIST = 2.1
    private const val AZ_REST = 13.0
    private const val EL_REST = 7.0
    private const val AZ_OPEN = 19.0
    private const val EL_OPEN = 6.0
    private const val ZOOM_OPEN = 0.06
    private const val FRAME_W = 0.74
    private const val FRAME_H = 0.76
    const val CAMERA_NEAR = 0.05
    const val CAMERA_FAR = 30.0

    /** Light (Filament units). The IBL is Poly Haven's studio_small_08 baked with cmgen. */
    const val IBL_INTENSITY = 30_000f
    val KEY_DIRECTION = floatArrayOf(-0.35f, -0.7f, -0.62f)
    const val KEY_LUX = 76_000f
    const val KEY_KELVIN = 5200f
    val FILL_DIRECTION = floatArrayOf(0.7f, -0.3f, -0.6f)
    const val FILL_LUX = 14_000f
    const val FILL_KELVIN = 7000f
    const val EXPOSURE = 0f

    /** Palette (sRGB). */
    const val BACKDROP = 0xCFC5B2
    const val PANEL = 0xFBF8F1
    const val CARD = 0xFFFFFF
    const val INK = 0x1D211E
    const val MUTE = 0x747568
    const val BRASS = 0x9A6F1E
    const val GREEN = 0x1F4238
    const val RED = 0xB33A2B
    const val GLOW = 0xFFB95C

    /** Copy: an invented password vault. */
    const val APP = "Keep"
    const val VAULT = "Personal vault"
    val ROW_LABELS = listOf("Passwords", "Recovery codes", "Secure notes")
    val ROW_VALUES = listOf("128", "16", "9")

    /** The demo's combination. */
    val DEMO_CODE = intArrayOf(24, 8, 31)

    // ------------------------------------------------------------ the film's phases

    private const val A = 0.7
    private const val A_END = 1.7
    private const val B = 2.05
    private const val B_END = 3.05
    private const val C = 3.4
    private const val C_END = 4.3
    private const val OPEN = 4.5
    const val SHUT = 7.3
    private const val SHUT_END = 8.75
    private const val DIAL_BACK = 8.7
    private const val DIAL_BACK_END = 9.4

    /** The unlock timeline, in seconds of `u`. */
    const val UNLOCK = 1.75
    private const val HANDLE_U0 = 0.0
    private const val HANDLE_U1 = 0.5
    private const val BOLT_U0 = 0.22
    private const val BOLT_U1 = 0.64
    private const val BOLT_STAGGER = 0.02
    private const val DOOR_U0 = 0.65
    private const val DOOR_U1 = 1.75

    /** How far the handle swings (deg, clockwise) and the door opens (deg). */
    const val HANDLE_DEG = 82.0
    const val DOOR_DEG = 36.0

    /** How strongly the dial catches on each number (0 = smooth, 1 = stops at every number). */
    private const val DETENT = 0.62

    // ------------------------------------------------------------ helpers

    fun clamp01(x: Double): Double = if (x < 0) 0.0 else if (x > 1) 1.0 else x
    fun seg(t: Double, a: Double, b: Double): Double = clamp01((t - a) / (b - a))
    private fun lerp(a: Double, b: Double, k: Double): Double = a + (b - a) * k
    private fun smooth(x: Double): Double = x * x * (3 - 2 * x)
    private fun easeInOutCubic(x: Double): Double = if (x < 0.5) 4 * x * x * x else 1 - (-2 * x + 2).pow(3) / 2

    /** JavaScript's Math.round: halves go up. */
    fun roundJs(x: Double): Double = floor(x + 0.5)

    /** A number on the dial, 0..39, for an unwrapped dial position. */
    fun dialNumber(n: Double): Int {
        val r = roundJs(n).toInt() % DIAL_N
        return if (r < 0) r + DIAL_N else r
    }

    /** The dial catches on each number: between two numbers it hurries, at a number it lingers. */
    private fun detented(v: Double): Double {
        val f = floor(v)
        val x = v - f
        return f + lerp(x, smooth(smooth(x)), DETENT)
    }

    // ------------------------------------------------------------ the film

    /** The film's dial (unwrapped numbers: + is counter-clockwise), for the combination c0, c1, c2. */
    fun filmDial(t: Double, c0: Int, c1: Int, c2: Int): Double {
        val s1 = if (c0 == 0) 0.0 else (c0 - DIAL_N).toDouble()
        val s2 = c1.toDouble()
        val s3 = if (c2 == 0) 0.0 else (c2 - DIAL_N).toDouble()
        val v = s1 * easeInOutCubic(seg(t, A, A_END)) +
            (s2 - s1) * easeInOutCubic(seg(t, B, B_END)) +
            (s3 - s2) * easeInOutCubic(seg(t, C, C_END)) -
            s3 * easeInOutCubic(seg(t, DIAL_BACK, DIAL_BACK_END))
        return detented(v)
    }

    /** How many numbers the film has entered (0..3). */
    fun filmEntered(t: Double): Int = when {
        t >= DIAL_BACK + 0.05 -> 0
        t >= C_END -> 3
        t >= B_END -> 2
        t >= A_END -> 1
        else -> 0
    }

    /** The film's place on the unlock timeline. */
    fun filmUnlock(t: Double): Double = clamp01((t - OPEN) / UNLOCK) * UNLOCK - seg(t, SHUT, SHUT_END) * UNLOCK

    /** The film's finger on the dial: out = [opacity 0..1, pressed 0..1]. */
    fun dialGhost(t: Double, out: DoubleArray) {
        val a = if (t < 1.875) A else if (t < 3.225) B else C
        val b = if (t < 1.875) A_END else if (t < 3.225) B_END else C_END
        out[0] = seg(t, a - 0.22, a - 0.04) * (1 - seg(t, b + 0.02, b + 0.16))
        out[1] = seg(t, a - 0.06, a + 0.04) * (1 - seg(t, b - 0.04, b + 0.06))
    }

    // ------------------------------------------------------------ the unlock, from u

    /** The handle's swing, 0..1. */
    fun handleAt(u: Double): Double = easeInOutCubic(seg(u, HANDLE_U0, HANDLE_U1))

    /** Bolt i's travel, 0 (out) .. 1 (drawn in). */
    fun boltAt(u: Double, i: Int): Double {
        val from = BOLT_U0 + i * BOLT_STAGGER
        return easeInOutCubic(seg(u, from, from + (BOLT_U1 - BOLT_U0)))
    }

    /** The door's opening, 0..1. */
    fun doorAt(u: Double): Double = easeInOutCubic(seg(u, DOOR_U0, DOOR_U1))

    /** Bolt i's direction, clockwise from straight up: out = [x, y]. */
    fun boltDir(i: Int, out: DoubleArray) {
        val a = (BOLT_FIRST_DEG + BOLT_STEP_DEG * i) * PI / 180
        out[0] = sin(a)
        out[1] = cos(a)
    }

    // ------------------------------------------------------------ camera and projection

    /** tan(half the vertical field of view) that keeps FRAME_W x FRAME_H around the target in a stage of this aspect. */
    fun tanHalfFovY(aspect: Double): Double = max(FRAME_H / 2, FRAME_W / 2 / aspect) / DIST

    /** The camera for the door's opening (0..1): out = [eye xyz, target xyz]. */
    fun cameraAt(open: Double, out: DoubleArray) {
        val k = smooth(open)
        val az = lerp(AZ_REST, AZ_OPEN, k) * PI / 180
        val el = lerp(EL_REST, EL_OPEN, k) * PI / 180
        val d = DIST * (1 - ZOOM_OPEN * k)
        out[0] = TARGET[0] + d * cos(el) * sin(az)
        out[1] = TARGET[1] + d * sin(el)
        out[2] = TARGET[2] + d * cos(el) * cos(az)
        if (out.size >= 6) {
            out[3] = TARGET[0]
            out[4] = TARGET[1]
            out[5] = TARGET[2]
        }
    }

    private val eye = DoubleArray(3)

    /** Projects a world point to the stage (dp) with the stage's own camera: out = [x, y]. Main thread only. */
    fun project(x: Double, y: Double, z: Double, open: Double, width: Double, height: Double, out: DoubleArray) {
        cameraAt(open, eye)
        // the camera's basis: f forward, r right, u up
        var fx = TARGET[0] - eye[0]
        var fy = TARGET[1] - eye[1]
        var fz = TARGET[2] - eye[2]
        val fl = sqrt(fx * fx + fy * fy + fz * fz)
        fx /= fl
        fy /= fl
        fz /= fl
        var rx = -fz
        var rz = fx
        val rl = sqrt(rx * rx + rz * rz)
        rx /= rl
        rz /= rl
        val ux = -rz * fy
        val uy = rz * fx - rx * fz
        val uz = rx * fy
        val px = x - eye[0]
        val py = y - eye[1]
        val pz = z - eye[2]
        val depth = px * fx + py * fy + pz * fz
        val aspect = width / height
        val tanY = tanHalfFovY(aspect)
        val ndcX = (px * rx + pz * rz) / (depth * tanY * aspect)
        val ndcY = (px * ux + py * uy + pz * uz) / (depth * tanY)
        out[0] = (ndcX * 0.5 + 0.5) * width
        out[1] = (0.5 - ndcY * 0.5) * height
    }

    /** The change of a finger's angle round a centre (radians, -pi..pi). */
    fun angleDelta(a: Double, b: Double): Double {
        var d = b - a
        while (d > PI) d -= 2 * PI
        while (d < -PI) d += 2 * PI
        return d
    }

    fun angle(x: Double, y: Double): Double = atan2(y, x)
}

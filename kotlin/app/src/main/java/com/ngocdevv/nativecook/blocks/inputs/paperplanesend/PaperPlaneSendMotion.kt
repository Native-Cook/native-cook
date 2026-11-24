package com.ngocdevv.nativecook.blocks.inputs.paperplanesend

import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * planeMotion.ts and planeScene.ts: one loop time drives the typing, the 2D paper, the fold, the plane's pose on
 * screen and the thread. Every value is a pure function of t (and of the screen's geometry), so t = LOOP gives the
 * same frame as t = 0, and a finger only moves the clock (to the send) and sets the throw's size.
 */
internal object PlaneMotion {
    const val LOOP = 9.0

    // ---------------------------------------------------------------- the scene

    /** The sheet (metres): SHEET_L along the nose axis (+x), SHEET_W across (z), the top face up (+y). */
    const val SHEET_L = 0.21
    const val SHEET_W = 0.148

    /** The fold: seconds within FOLD_S for each step; every hinge turns 0 → its angle, ease-in-out cubic, over its step. */
    const val FOLD_S = 1.4
    private val STEP_CORNER = doubleArrayOf(0.0, 0.42)
    private val STEP_SECOND = doubleArrayOf(0.32, 0.76)
    private val STEP_HALF = doubleArrayOf(0.68, 1.08)
    private val STEP_WING = doubleArrayOf(0.98, 1.4)

    /** A hinged panel: its node, where its hinge sits in its parent (the node's translation, metres), the hinge's axis
     * in the parent's frame, its angle when folded (deg) and its step. */
    class Hinge(val node: String, val pivot: FloatArray, val axis: FloatArray, val deg: Double, val step: DoubleArray)

    private fun hinge(node: String, px: Float, py: Float, ax: Float, az: Float, deg: Double, step: DoubleArray) =
        Hinge(node, floatArrayOf(px, py, 0f), floatArrayOf(ax, 0f, az), deg, step)

    /** R / L are the two halves; K / W the keel's and the wing's side of the wing crease. Parents: Keel > Wing,
     * FlapBK > FlapC1K > FlapC2K; Wing > FlapBW > FlapC1W > FlapC2W. */
    val HINGES: List<Hinge> = listOf(
        hinge("KeelR", 0f, -0.0005f, 1f, 0f, -86.0, STEP_HALF),
        hinge("WingR", 0.105f, 0.00025f, -0.992049f, 0.125849f, -77.0, STEP_WING),
        hinge("FlapBKR", 0.105f, 0.001f, -0.92388f, 0.382683f, 179.0, STEP_SECOND),
        hinge("FlapBWR", 0f, 0.00075f, -0.92388f, 0.382683f, 179.0, STEP_SECOND),
        hinge("FlapC1KR", 0f, -0.0005f, -0.707107f, 0.707107f, 179.0, STEP_CORNER),
        hinge("FlapC1WR", 0f, -0.0005f, -0.707107f, 0.707107f, 179.0, STEP_CORNER),
        hinge("FlapC2KR", 0f, -0.0005f, -0.382683f, 0.92388f, -179.0, STEP_SECOND),
        hinge("FlapC2WR", 0f, -0.0005f, -0.382683f, 0.92388f, -179.0, STEP_SECOND),
        hinge("KeelL", 0f, -0.0005f, 1f, 0f, 86.0, STEP_HALF),
        hinge("WingL", 0.105f, 0.00025f, -0.992049f, -0.125849f, 77.0, STEP_WING),
        hinge("FlapBKL", 0.105f, 0.001f, -0.92388f, -0.382683f, -179.0, STEP_SECOND),
        hinge("FlapBWL", 0f, 0.00075f, -0.92388f, -0.382683f, -179.0, STEP_SECOND),
        hinge("FlapC1KL", 0f, -0.0005f, -0.707107f, -0.707107f, -179.0, STEP_CORNER),
        hinge("FlapC1WL", 0f, -0.0005f, -0.707107f, -0.707107f, -179.0, STEP_CORNER),
        hinge("FlapC2KL", 0f, -0.0005f, -0.382683f, -0.92388f, 179.0, STEP_SECOND),
        hinge("FlapC2WL", 0f, -0.0005f, -0.382683f, -0.92388f, 179.0, STEP_SECOND),
    )

    /** Camera: straight at the plane z = 0, where one metre is the same number of points everywhere on screen, so the
     * flat sheet lines up with the 2D paper exactly. */
    const val VFOV_DEG = 24.0
    const val CAMERA_NEAR = 0.05
    const val CAMERA_FAR = 20.0

    /** Light (Filament units). */
    const val IBL_INTENSITY = 15_000f
    val KEY_DIRECTION = floatArrayOf(0.42f, -0.5f, -0.76f)
    const val KEY_LUX = 70_000f
    val FILL_DIRECTION = floatArrayOf(-0.6f, 0.35f, -0.72f)
    const val FILL_LUX = 9_000f
    const val EXPOSURE = 0f

    /** Palette (sRGB). */
    const val BG = 0x191C22
    const val BG_TOP = 0x20242C
    const val FIELD = 0x272B34
    const val BUBBLE_IN = 0x2D313B
    const val BUBBLE_OUT = 0x2E6BE6
    const val INK = 0xF2F3F5
    const val MUTE = 0xAEB3BE

    /** The 2D paper that hands over to the 3D sheet: the lit sheet's colour on screen. */
    const val PAPER = 0xEFECE3
    const val PAPER_INK = 0x24262B

    /** Copy: an invented chat app and a short thread. */
    const val APP = "Pidgin"
    const val CONTACT = "Ines Varga"
    const val CONTACT_INITIALS = "IV"
    val THREAD: List<Pair<Boolean, String>> = listOf(
        false to "Are you boarding yet?",
        true to "Just sat down. Window seat",
        false to "Nice. What time do you land?",
    )
    const val MESSAGE = "Landing at 6:40, save me a seat"
    const val DELIVERED = "Delivered"

    // ---------------------------------------------------------------- the film's phases (s)

    const val TYPE = 0.6
    const val TYPE_END = 2.3
    const val TAP = 2.6
    const val LIFT = 2.7
    const val LIFT_END = 3.1
    const val FOLD = 3.12
    const val FOLD_END = 3.12 + FOLD_S
    const val FLIGHT = 3.12 + FOLD_S
    const val FLIGHT_END = 3.12 + FOLD_S + 1.2
    const val POP = 3.12 + FOLD_S + 1.16
    const val DELIVERED_AT = 6.0
    const val RESET = 7.4
    const val RESET_END = 8.2

    /** Reduced motion shows this still: the message delivered. */
    const val REDUCED_T = 6.8

    /** The hand-off's cross-fade (s). */
    const val HANDOFF = 0.06

    /** The fold's pose when it ends: how far the sheet lies back (deg, top edge away), its roll, how far it rose (pt)
     * and the dip before the throw (pt). */
    const val TILT_FOLD = 38.0
    const val ROLL_FOLD = -16.0
    const val RISE = 26.0
    const val DIP = 9.0

    /** The flight: the tilt it ends with, the bank at the top of the turn (deg) and the scale it shrinks to. */
    const val TILT_FLY = 20.0
    const val BANK = 34.0
    const val SCALE_END = 0.2

    /** The bubble's pop: x(τ) = 1 - e^(-Zτ) (cos Wτ + Z/W sin Wτ), from POP_FROM to 1. */
    const val POP_Z = 13.0
    const val POP_W = 24.0
    const val POP_FROM = 0.55

    /** A throw scales the flight's arc by 1 .. THROW_MAX (the same 1.2 s, so a harder throw flies faster). */
    const val THROW_MAX = 1.35

    /** The screen's geometry the motion needs, as one array:
     * [width, height, sheet centre x, sheet centre y, slot centre x, slot centre y, the sheet's width in dp, throw]. */
    const val G_W = 0
    const val G_H = 1
    const val G_SX = 2
    const val G_SY = 3
    const val G_TX = 4
    const val G_TY = 5
    const val G_PT = 6
    const val G_THROW = 7

    // ---------------------------------------------------------------- helpers

    fun clamp01(x: Double): Double = if (x < 0) 0.0 else if (x > 1) 1.0 else x
    fun seg(t: Double, a: Double, b: Double): Double = clamp01((t - a) / (b - a))
    fun lerp(a: Double, b: Double, k: Double): Double = a + (b - a) * k
    fun easeInOutCubic(x: Double): Double = if (x < 0.5) 4 * x * x * x else 1 - (-2 * x + 2).pow(3) / 2
    fun easeInOutQuad(x: Double): Double = if (x < 0.5) 2 * x * x else 1 - (-2 * x + 2).pow(2) / 2
    fun easeInOutSine(x: Double): Double = -(cos(PI * x) - 1) / 2
    fun smooth(x: Double): Double = x * x * (3 - 2 * x)

    /** The pop spring's step response at τ seconds (0 → 1, one small overshoot). */
    fun popStep(tau: Double): Double {
        if (tau <= 0) return 0.0
        return 1 - exp(-POP_Z * tau) * (cos(POP_W * tau) + (POP_Z / POP_W) * sin(POP_W * tau))
    }

    /** One Catmull-Rom coordinate between p1 and p2 (u 0..1); d = true gives the derivative. */
    private fun catmull(p0: Double, p1: Double, p2: Double, p3: Double, u: Double, d: Boolean): Double {
        val a = -p0 + 3 * p1 - 3 * p2 + p3
        val b = 2 * p0 - 5 * p1 + 4 * p2 - p3
        val c = p2 - p0
        if (d) return 0.5 * (3 * a * u * u + 2 * b * u + c)
        return 0.5 * (a * u * u * u + b * u * u + c * u + 2 * p1)
    }

    // ---------------------------------------------------------------- the composer

    /** How many characters of MESSAGE the field shows. */
    fun typedCount(t: Double): Int {
        if (t >= LIFT) return 0
        return floor(seg(t, TYPE, TYPE_END) * MESSAGE.length + 1e-6).toInt()
    }

    /** The film's finger on the send button: out = [opacity, pressed 0..1]. */
    fun ghostAt(t: Double, out: DoubleArray) {
        out[0] = seg(t, TAP - 0.3, TAP - 0.06) * (1 - seg(t, TAP + 0.12, TAP + 0.36))
        out[1] = seg(t, TAP - 0.06, TAP + 0.02) * (1 - seg(t, TAP + 0.06, TAP + 0.18))
    }

    // ---------------------------------------------------------------- the 2D paper and the hand-off

    /** The paper's way from the field's rectangle (0) to the sheet's (1). */
    fun liftAt(t: Double): Double = easeInOutCubic(seg(t, LIFT, LIFT_END))

    /** The 2D paper's opacity: in as the message leaves the field, out once the 3D sheet covers it. */
    fun paperAlpha(t: Double): Double = seg(t, LIFT, LIFT + 0.06) * (1 - seg(t, LIFT_END + 0.02, LIFT_END + 0.02 + HANDOFF))

    /** The words on the paper: they fade as it grows. */
    fun wordsAlpha(t: Double): Double = 1 - seg(t, LIFT + 0.1, LIFT_END - 0.08)

    /** The 3D layer's opacity: in over the flat 2D paper, out into the bubble's pop. */
    fun stageAlpha(t: Double): Double = seg(t, LIFT_END - 0.04, LIFT_END - 0.04 + HANDOFF) * (1 - seg(t, POP - 0.02, POP + 0.06))

    /** Seconds into the fold (0 .. FOLD_S). */
    fun foldTime(t: Double): Double {
        val x = t - FOLD
        return if (x < 0) 0.0 else if (x > FOLD_S) FOLD_S else x
    }

    // ---------------------------------------------------------------- the flight

    private val xs = DoubleArray(5)
    private val ys = DoubleArray(5)

    /** The flight path at s (0..1), screen dp: out = [x, y, dx/ds, dy/ds]. A Catmull-Rom curve from the sheet up, over
     * the thread and down into the slot; a throw (g[G_THROW]) makes the arc bigger. Main thread only. */
    fun pathAt(s: Double, g: DoubleArray, out: DoubleArray) {
        val w = g[G_W]
        val h = g[G_H]
        val a = g[G_THROW]
        val x0 = g[G_SX]
        val y0 = g[G_SY] - RISE
        xs[0] = x0; xs[1] = x0 - 0.16 * w * a; xs[2] = x0 + 0.02 * w; xs[3] = x0 + 0.3 * w; xs[4] = g[G_TX]
        ys[0] = y0; ys[1] = y0 - 0.22 * h * a; ys[2] = y0 - 0.4 * h * a; ys[3] = y0 - 0.27 * h * a; ys[4] = g[G_TY]
        val f = clamp01(s) * 4
        var i = floor(f).toInt()
        if (i > 3) i = 3
        val u = f - i
        val xa = if (i == 0) 2 * xs[0] - xs[1] else xs[i - 1]
        val ya = if (i == 0) 2 * ys[0] - ys[1] else ys[i - 1]
        val xd = if (i == 3) 2 * xs[4] - xs[3] else xs[i + 2]
        val yd = if (i == 3) 2 * ys[4] - ys[3] else ys[i + 2]
        out[0] = catmull(xa, xs[i], xs[i + 1], xd, u, false)
        out[1] = catmull(ya, ys[i], ys[i + 1], yd, u, false)
        out[2] = catmull(xa, xs[i], xs[i + 1], xd, u, true)
        out[3] = catmull(ya, ys[i], ys[i + 1], yd, u, true)
    }

    /** The flight's progress along its path, 0..1. */
    fun flightAt(t: Double): Double = smooth(seg(t, FLIGHT, FLIGHT_END))

    private val path = DoubleArray(4)

    /** The plane on screen: out = [x, y (dp), heading (deg, 0 = nose right, 90 = up), tilt (deg, the top edge away),
     * roll (deg, + = right wing down), scale]. The flat sheet is [sheet centre, 0, 0, 0, 1]. */
    fun poseAt(t: Double, g: DoubleArray, out: DoubleArray) {
        val p = path
        if (t < FLIGHT) {
            pathAt(0.0, g, p)
            val launch = atan2(-p[3], p[2]) * 180 / PI
            val k = easeInOutCubic(seg(t, FOLD, FOLD_END))
            out[0] = g[G_SX]
            out[1] = g[G_SY] - RISE * k + DIP * sin(PI * seg(t, FOLD_END - 0.24, FOLD_END))
            out[2] = launch * k
            out[3] = TILT_FOLD * easeInOutSine(seg(t, FOLD, FOLD + 0.9))
            out[4] = ROLL_FOLD * k
            out[5] = 1.0
            return
        }
        val s = flightAt(t)
        pathAt(s, g, p)
        out[0] = p[0]
        out[1] = p[1]
        out[2] = atan2(-p[3], p[2]) * 180 / PI
        out[3] = lerp(TILT_FOLD, TILT_FLY, s)
        out[4] = ROLL_FOLD * (1 - clamp01(s * 3)) + BANK * sin(PI * s)
        out[5] = 1 - (1 - SCALE_END) * s.pow(2.2)
    }

    /** The pose's rotation as an axis and an angle: out = [x, y, z, radians]. World: x right, y up, z to the camera.
     * q = Rx(-tilt) · Rz(heading) · Rx(90° + roll): the sheet's top faces the camera, its nose turns in the screen's
     * plane, it rolls about its nose, then the whole lies back. */
    fun poseRotation(headingDeg: Double, tiltDeg: Double, rollDeg: Double, out: DoubleArray) {
        val r = PI / 360
        val a = -tiltDeg * r
        val b = headingDeg * r
        val c = (90 + rollDeg) * r
        // qz(b) * qx(c)
        val sb = sin(b)
        val cb = cos(b)
        val sc = sin(c)
        val cc = cos(c)
        val x1 = cb * sc
        val y1 = sb * sc
        val z1 = sb * cc
        val w1 = cb * cc
        // qx(a) * that
        val sa = sin(a)
        val ca = cos(a)
        val x = ca * x1 + sa * w1
        val y = ca * y1 - sa * z1
        val z = ca * z1 + sa * y1
        val w = ca * w1 - sa * x1
        val n = sqrt(x * x + y * y + z * z)
        if (n < 1e-6) {
            out[0] = 0.0
            out[1] = 0.0
            out[2] = 1.0
            out[3] = 0.0
            return
        }
        out[0] = x / n
        out[1] = y / n
        out[2] = z / n
        out[3] = 2 * atan2(n, w)
    }

    private val shadowPose = DoubleArray(6)

    /** The plane's soft shadow on the chat: out = [x, y (dp), radius x, radius y (dp), opacity]. It slides away and
     * thins as the plane climbs. */
    fun shadowAt(t: Double, g: DoubleArray, out: DoubleArray) {
        val p = shadowPose
        poseAt(t, g, p)
        val s = if (t < FLIGHT) 0.0 else flightAt(t)
        val f = seg(t, FOLD, FOLD_END)
        val lift = f * 0.35 + s * 0.65
        val size = g[G_PT] * p[5]
        // flat against the screen the sheet throws none: it grows out from under the paper as it lifts and lies back
        val rise = easeInOutSine(seg(t, FOLD + 0.1, FOLD + 0.7))
        out[0] = p[0] + 28 * lift * p[5]
        out[1] = p[1] + (22 * rise + 40 * lift) * p[5]
        out[2] = size * lerp(0.3, 0.42, rise) * lerp(1.0, 0.8, f)
        out[3] = size * lerp(0.2, 0.3, rise) * lerp(1.0, 0.7, f)
        out[4] = stageAlpha(t) * rise * lerp(0.7, 0.3, lift)
    }

    // ---------------------------------------------------------------- the thread

    /** The thread's way up to make room for the new message (0..1). */
    fun shiftAt(t: Double): Double = easeInOutCubic(seg(t, FLIGHT_END - 0.3, FLIGHT_END)) - easeInOutCubic(seg(t, RESET, RESET_END))

    /** The sent bubble's opacity and scale. */
    fun bubbleAlpha(t: Double): Double = seg(t, POP, POP + 0.08) * (1 - seg(t, RESET, RESET + 0.4))
    fun bubbleScale(t: Double): Double = POP_FROM + (1 - POP_FROM) * popStep(t - POP)

    /** The "Delivered" receipt's opacity. */
    fun deliveredAt(t: Double): Double = seg(t, DELIVERED_AT, DELIVERED_AT + 0.25) * (1 - seg(t, RESET, RESET + 0.3))

    /** The send button: lit while there is something to send. */
    fun sendLitAt(t: Double): Double = seg(t, TYPE, TYPE + 0.18) * (1 - seg(t, LIFT, LIFT + 0.18))
}

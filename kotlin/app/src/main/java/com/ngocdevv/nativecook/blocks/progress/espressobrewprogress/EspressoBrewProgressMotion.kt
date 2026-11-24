package com.ngocdevv.nativecook.blocks.progress.espressobrewprogress

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sign
import kotlin.math.sin

/**
 * espressoScene.ts and espressoMotion.ts: the scene's numbers and the film as pure functions of t, so t = LOOP gives
 * the same frame as t = 0. A finger's shot plays the same timeline (from PHASE_BREW).
 *
 *  0.00 – 0.75  rest: Single selected, the cup empty, the needle at 0, the group light off
 *  0.75         Double tapped on the segmented control
 *  1.35         Brew tapped: the group light warms up (to 1.70)
 *  1.40 – 2.35  pre-infusion: the needle climbs to 3 bar and waits
 *  2.35 – 3.10  full pressure: up to 9 bar, a slight tremble while it holds
 *  2.45         first drops: two streams fall from the spouts and fill out by 3.10
 *  2.60 – 6.95  the cup fills, the crema rises on top (2.80 – 7.50) and turns slowly
 *  6.50         the pump stops: the needle falls (to 7.20), the streams thin and their tails fall (6.80 – 7.05)
 *  7.10 – 7.70  the light fades; "Shot ready"
 *  7.85 – 8.45  the cup is carried out to the right; a fresh one comes in (8.55 – 9.20)
 *  8.95         Single tapped again
 *  9.20 – 10.0  rest; last frame = first frame. The camera leans in for the shot (1.5 – 3.3) and back (7.4 – 9.1).
 */
internal object EspressoMotion {
    const val LOOP = 10.0

    // ------------------------------------------------------------ scene (glTF space: y up, metres, faces +z)

    /** The gauge's needle turns about z through this point; 0 bar is NEEDLE_ZERO_DEG counter-clockwise from up. */
    val NEEDLE_PIVOT = floatArrayOf(0.058f, 0.262f, 0.1269f)
    private const val NEEDLE_ZERO_DEG = 135.0
    private const val NEEDLE_SPAN_DEG = 270.0
    private const val GAUGE_MAX_BAR = 15.0

    /** The cup's foot on the grate (the Cup node), the coffee's base inside it, and the spout tips. */
    val CUP_REST = floatArrayOf(0f, 0.036f, 0.0625f)
    const val COFFEE_BASE = 0.01
    const val SPOUT_TIP_Y = 0.122
    val STREAM_TIPS = arrayOf(floatArrayOf(-0.0095f, 0.122f, 0.0625f), floatArrayOf(0.0095f, 0.122f, 0.0625f))

    /** Mesh heights the app scales: the coffee cylinder, the crema layer and a stream. */
    const val COFFEE_REF = 0.01
    const val CREMA_REF = 0.005
    const val STREAM_REF = 0.01

    /** Liquid height for a single and a double (coffee and crema together), and the crema's share of it. */
    val DOSE_H = doubleArrayOf(0.0135, 0.027)
    val CREMA_H = doubleArrayOf(0.0028, 0.0046)

    /** Yield in grams for a single and a double, and how long a shot takes on the machine's own clock (s). */
    val DOSE_G = intArrayOf(18, 36)
    private const val SHOT_S = 28.0

    /** The cup is carried this far to the right (m) when it is changed. */
    private const val CUP_AWAY = 0.36
    private const val CUP_LIFT = 0.006

    /** The crema turns this much (rad) while the shot pours. */
    private const val CREMA_SWIRL = 1.4

    /** Camera: it orbits its target at DIST (az + = to the viewer's right). FRAME_W x FRAME_H metres stay in frame. */
    private val TARGET_REST = doubleArrayOf(0.0, 0.18, 0.01)
    private val TARGET_PEAK = doubleArrayOf(0.0, 0.18, 0.03)
    private const val DIST = 1.3
    private const val AZ_REST = 18.0
    private const val EL_REST = 9.0
    private const val AZ_PEAK = 12.0
    private const val EL_PEAK = 6.0
    private const val ZOOM_PEAK = 0.02
    private const val FRAME_W = 0.42
    private const val FRAME_H = 0.42
    const val CAMERA_NEAR = 0.04
    const val CAMERA_FAR = 30.0

    /** Light (Filament units). The IBL is Poly Haven's studio_small_08 baked with cmgen. */
    const val IBL_INTENSITY = 30_000f
    val KEY_DIRECTION = floatArrayOf(-0.45f, -0.75f, -0.5f)
    const val KEY_LUX = 68_000f
    const val KEY_KELVIN = 5200f
    val FILL_DIRECTION = floatArrayOf(0.7f, -0.3f, -0.6f)
    const val FILL_LUX = 14_000f
    const val FILL_KELVIN = 7000f
    const val EXPOSURE = 0f
    const val BLOOM_STRENGTH = 0.06f

    /** The group head's light band (linear colour) and its emissive strength when fully on. */
    val GROUP_LIGHT_RGB = floatArrayOf(1.0f, 0.62f, 0.3f)
    const val GROUP_LIGHT_ON = 6f

    /** Palette (sRGB). */
    const val BACKDROP = 0xE8DFD0
    const val PANEL = 0xFBF8F2
    const val CARD = 0xFFFFFF
    const val INK = 0x22262A
    const val MUTE = 0x6E716F
    const val ACCENT = 0xB5652B

    /** Copy: an invented coffee machine app. */
    const val APP = "Crema"
    const val MACHINE = "Kitchen · Duo"
    const val RECIPE = "Morning shot"
    val DOSES = listOf("Single", "Double")
    val STATUS = listOf("Ready", "Pre-infusion", "Extracting", "Shot ready", "Fresh cup")

    // ------------------------------------------------------------ the film's phases

    const val PHASE_DOSE = 0.75
    const val PHASE_BREW = 1.35
    private const val PHASE_LIGHT_IN = 1.7
    private const val PHASE_PRE = 1.4
    private const val PHASE_PRE_END = 2.0
    const val PHASE_RAMP = 2.35
    private const val PHASE_RAMP_END = 3.1
    private const val PHASE_STREAM = 2.45
    private const val PHASE_STREAM_HIT = 2.72
    private const val PHASE_STREAM_FULL = 3.1
    private const val PHASE_FILL = 2.6
    private const val PHASE_FILL_END = 6.95
    private const val PHASE_CREMA = 2.8
    private const val PHASE_CREMA_END = 7.5
    const val PHASE_STOP = 6.5
    private const val PHASE_DROP_END = 7.2
    private const val PHASE_THIN_END = 6.8
    private const val PHASE_TAIL_END = 7.05
    private const val PHASE_LIGHT_OUT = 7.1
    private const val PHASE_LIGHT_OUT_END = 7.7

    /** A finger's shot stops here, with the cup full. */
    const val PHASE_FULL = 7.75
    const val PHASE_CUP_OUT = 7.85
    private const val PHASE_CUP_OUT_END = 8.45

    /** The cup is out of frame: from here it is the fresh, empty one. */
    private const val PHASE_SWAP = 8.5
    private const val PHASE_CUP_IN = 8.55
    const val PHASE_CUP_IN_END = 9.2
    const val PHASE_SINGLE = 8.95
    private const val PHASE_CAM_IN = 1.5
    private const val PHASE_CAM_IN_END = 3.3
    private const val PHASE_CAM_OUT = 7.4
    private const val PHASE_CAM_OUT_END = 9.1

    /** The film's taps: time and what (0 = Single, 1 = Double on the segmented control, 2 = the Brew button). */
    private val TAP_AT = doubleArrayOf(PHASE_DOSE, PHASE_BREW, PHASE_SINGLE)
    private val TAP_WHAT = doubleArrayOf(1.0, 2.0, 0.0)

    /** Pressure: pre-infusion and full (bar), and the tremble while it holds. */
    private const val PRE_BAR = 3.0
    private const val FULL_BAR = 9.0
    private const val TREMBLE_BAR = 0.14
    private const val TREMBLE_CYCLES = 9.0

    /** The second stream starts this much after the first (s). */
    private const val STREAM_LAG = 0.06

    /** A stream's width at its first drops, as a share of its full width; and after the pump stops. */
    private const val STREAM_THIN = 0.45
    private const val STREAM_END = 0.5

    // ------------------------------------------------------------ helpers

    fun clamp01(x: Double): Double = if (x < 0) 0.0 else if (x > 1) 1.0 else x

    fun seg(t: Double, a: Double, b: Double): Double = clamp01((t - a) / (b - a))

    fun lerp(a: Double, b: Double, k: Double): Double = a + (b - a) * k

    fun smooth(x: Double): Double = x * x * (3 - 2 * x)

    private fun easeOut(x: Double): Double {
        val k = 1 - x
        return 1 - k * k * k
    }

    // ------------------------------------------------------------ the film

    /** The selected dose in the film: 0 = Single, 1 = Double. */
    fun filmDose(t: Double): Int = if (t >= PHASE_DOSE && t < PHASE_SINGLE) 1 else 0

    /** The pump's pressure (bar). */
    fun barAt(t: Double): Double {
        val pre = PRE_BAR * easeOut(seg(t, PHASE_PRE, PHASE_PRE_END))
        val full = (FULL_BAR - PRE_BAR) * smooth(seg(t, PHASE_RAMP, PHASE_RAMP_END))
        val hold = smooth(seg(t, PHASE_RAMP_END, PHASE_RAMP_END + 0.4)) * (1 - smooth(seg(t, PHASE_STOP - 0.3, PHASE_STOP)))
        val tremble = TREMBLE_BAR * hold * sin(2 * PI * TREMBLE_CYCLES * t / LOOP)
        return (pre + full + tremble) * (1 - smooth(seg(t, PHASE_STOP, PHASE_DROP_END)))
    }

    /** The needle's angle (deg, counter-clockwise from straight up) for a pressure. */
    fun needleDeg(bar: Double): Double = NEEDLE_ZERO_DEG - bar / GAUGE_MAX_BAR * NEEDLE_SPAN_DEG

    /** The group head's light, 0..1. */
    fun lightAt(t: Double): Double =
        smooth(seg(t, PHASE_BREW, PHASE_LIGHT_IN)) * (1 - smooth(seg(t, PHASE_LIGHT_OUT, PHASE_LIGHT_OUT_END)))

    /** How full the cup is, 0..1 of the dose (the fresh cup after the swap is empty). */
    fun fillAt(t: Double): Double = if (t >= PHASE_SWAP) 0.0 else smooth(seg(t, PHASE_FILL, PHASE_FILL_END))

    /** How much of its crema the shot has, 0..1. */
    fun cremaAt(t: Double): Double = if (t >= PHASE_SWAP) 0.0 else smooth(seg(t, PHASE_CREMA, PHASE_CREMA_END))

    /** The crema's turn (rad). */
    fun swirlAt(t: Double): Double = CREMA_SWIRL * smooth(seg(t, PHASE_FILL, PHASE_CREMA_END + 0.1))

    /** Stream i: out = [head, tail, width] (head and tail: how far down the spout → surface gap, 0..1). */
    fun streamAt(t: Double, i: Int, out: DoubleArray) {
        val lag = i * STREAM_LAG
        val h = seg(t, PHASE_STREAM + lag, PHASE_STREAM_HIT + lag)
        val tl = seg(t, PHASE_THIN_END + lag, PHASE_TAIL_END + lag)
        out[0] = h * h
        out[1] = tl * tl
        val grow = lerp(STREAM_THIN, 1.0, smooth(seg(t, PHASE_STREAM + lag, PHASE_STREAM_FULL)))
        out[2] = if (t < PHASE_STREAM + lag) 0.0 else grow * (1 - (1 - STREAM_END) * smooth(seg(t, PHASE_STOP, PHASE_THIN_END)))
    }

    /** The cup's slide for the change: out = [x (m), lift (m)]. */
    fun cupAt(t: Double, out: DoubleArray) {
        if (t < PHASE_CUP_IN) {
            val k = seg(t, PHASE_CUP_OUT, PHASE_CUP_OUT_END)
            out[0] = CUP_AWAY * smooth(k)
            out[1] = CUP_LIFT * sin(PI * k)
            return
        }
        val k = seg(t, PHASE_CUP_IN, PHASE_CUP_IN_END)
        out[0] = CUP_AWAY * (1 - smooth(k))
        out[1] = CUP_LIFT * sin(PI * k)
    }

    /** The machine's shot timer (its own seconds), reset with the fresh cup. */
    fun shotSecondsAt(t: Double): Double = if (t >= PHASE_SWAP) 0.0 else SHOT_S * seg(t, PHASE_BREW, PHASE_STOP)

    /** The status line's index into STATUS. */
    fun statusAt(t: Double): Int = when {
        t < PHASE_BREW -> 0
        t < PHASE_RAMP -> 1
        t < PHASE_STOP -> 2
        t < PHASE_CUP_OUT -> 3
        t < PHASE_CUP_IN_END -> 4
        else -> 0
    }

    /** The film's finger: out = [opacity, pressed, target (0 Single, 1 Double, 2 Brew)]. */
    fun tapGhost(t: Double, out: DoubleArray) {
        var best = 0
        var dist = 1e9
        for (k in TAP_AT.indices) {
            val d = abs(t - TAP_AT[k])
            if (d < dist) {
                dist = d
                best = k
            }
        }
        val at = TAP_AT[best]
        out[0] = seg(t, at - 0.32, at - 0.1) * (1 - seg(t, at + 0.1, at + 0.32))
        out[1] = seg(t, at - 0.1, at) * (1 - seg(t, at + 0.04, at + 0.18))
        out[2] = TAP_WHAT[best]
    }

    // ------------------------------------------------------------ camera

    /** How far the camera has leaned in towards the cup, 0..1. */
    fun camAt(t: Double): Double =
        smooth(seg(t, PHASE_CAM_IN, PHASE_CAM_IN_END)) * (1 - smooth(seg(t, PHASE_CAM_OUT, PHASE_CAM_OUT_END)))

    /** tan(half the vertical field of view) that keeps FRAME_W x FRAME_H in a stage of this aspect. */
    fun tanHalfFovY(aspect: Double): Double = max(FRAME_H / 2, FRAME_W / 2 / aspect) / DIST

    /** The camera for a lean k and a finger's orbit (deg): out = [eye xyz, target xyz]. */
    fun cameraAt(k: Double, orbit: Double, out: DoubleArray) {
        val az = (lerp(AZ_REST, AZ_PEAK, k) + orbit) * PI / 180
        val el = lerp(EL_REST, EL_PEAK, k) * PI / 180
        val d = DIST * (1 - ZOOM_PEAK * k)
        val tx = lerp(TARGET_REST[0], TARGET_PEAK[0], k)
        val ty = lerp(TARGET_REST[1], TARGET_PEAK[1], k)
        val tz = lerp(TARGET_REST[2], TARGET_PEAK[2], k)
        out[0] = tx + d * cos(el) * sin(az)
        out[1] = ty + d * sin(el)
        out[2] = tz + d * cos(el) * cos(az)
        out[3] = tx
        out[4] = ty
        out[5] = tz
    }

    /** A finger's orbit (deg) from its drag (dp), rubber-banded past ORBIT_MAX. */
    const val ORBIT_SPAN = 70.0
    const val ORBIT_MAX = 22.0

    fun orbitFromDrag(from: Double, dx: Double, width: Double): Double {
        val a = from + dx / width * ORBIT_SPAN
        val m = abs(a)
        if (m <= ORBIT_MAX) return a
        val over = m - ORBIT_MAX
        return sign(a) * (ORBIT_MAX + over * 8 / (over + 8))
    }
}

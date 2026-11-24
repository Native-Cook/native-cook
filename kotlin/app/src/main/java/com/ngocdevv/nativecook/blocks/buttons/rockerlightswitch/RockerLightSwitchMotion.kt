package com.ngocdevv.nativecook.blocks.buttons.rockerlightswitch

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * switchMotion.ts and switchScene.ts: the film as pure functions of time, the snap spring, the detent, the camera and
 * its projection, and the scene's fixed numbers. glTF space: y up, metres, the wall is z = 0 and faces the camera.
 *
 * One number drives the switch: `press`, 0 (off: the bottom half in) .. 1 (on: the top half in). The film is
 * press = filmPress(t); a finger moves the same number. Every film value is a pure function of t, so t = LOOP gives
 * the same frame as t = 0.
 */
internal object SwitchMotion {
    const val LOOP = 8.0

    // the film's phases (s)
    const val PRESS_ON = 0.8
    const val SNAP_ON = 1.22
    const val ORBIT_OUT = 2.0
    const val ORBIT_OUT_END = 4.4
    const val DIM = 2.5
    const val DIM_END = 3.2
    const val BRIGHT = 3.7
    const val BRIGHT_END = 4.4
    const val ORBIT_BACK = 4.9
    const val ORBIT_BACK_END = 6.9
    const val PRESS_OFF = 5.2
    const val SNAP_OFF = 5.62

    /** A press pushes the rocker this far (0..1) before it tips, over PRESS seconds in the film. */
    const val PRESS_DEPTH = 0.4
    const val PRESS = SNAP_ON - PRESS_ON

    /** The snap: x(τ) = 1 - e^(-Zτ) (cos Wτ + Z/W sin Wτ); stiffness Z² + W² = 1096, damping 2Z = 28, mass 1. */
    const val SNAP_Z = 14.0
    const val SNAP_W = 30.0

    /** The filament: on after LAG over WARM (ease-out cubic); off after LAG_OFF over COOL. Seconds. */
    const val LAG = 0.12
    const val WARM = 0.52
    const val LAG_OFF = 0.06
    const val COOL = 0.3
    const val DIM_TO = 0.4
    const val BRIGHT_MIN = 0.1
    const val IDLE_MS = 2000.0
    const val RETURN_MS = 1100.0
    const val GHOST_IN = 0.3
    const val GHOST_OUT = 0.3

    // ---------------------------------------------------------------- the scene

    val ROCKER_PIVOT = doubleArrayOf(0.032, -0.082, 0.004)
    const val ROCK_DEG = 7.0
    const val ROCKER_HALF_H = 0.036
    const val ROCKER_FACE_Z = 0.0052
    const val PLATE_HALF_W = 0.043
    const val PLATE_HALF_H = 0.061
    val GLOBE = doubleArrayOf(-0.066, 0.062, 0.1)
    const val GLOBE_R = 0.043

    val TARGET = doubleArrayOf(-0.012, 0.023, 0.0)
    val EYE_REST = doubleArrayOf(0.085, -0.005, 0.66)
    val EYE_ORBIT = doubleArrayOf(-0.07, 0.005, 0.662)
    const val FRAME_W = 0.305
    const val FRAME_H = 0.44
    const val CAMERA_NEAR = 0.02
    const val CAMERA_FAR = 10.0

    /** Light (Filament units). */
    const val IBL_INTENSITY = 9_000f
    const val BULB_LUMENS = 42_000.0
    const val BULB_FALLOFF = 2.5f
    val BOUNCE_DIRECTION = floatArrayOf(0.35f, -0.3f, -0.89f)
    const val BOUNCE_LUX = 16_000.0
    const val GLOBE_EMISSIVE = 7.0
    val SHADOW_RGB = floatArrayOf(0.0242f, 0.0152f, 0.0103f)
    const val EXPOSURE = 0f
    val WARM_LOW = doubleArrayOf(1.0, 0.42, 0.14)
    val WARM_FULL = doubleArrayOf(1.0, 0.78, 0.52)

    /** Palette (sRGB). */
    const val DUSK = 0x16233F
    const val PANEL = 0xF5F2EC
    const val CARD = 0xFFFFFF
    const val INK = 0x1D1B18
    const val MUTE = 0x7A746B
    const val AMBER = 0xE39B2E

    /** Copy: an invented smart-home app. */
    const val APP = "Lumen Home"
    const val ROOM = "Living room"
    const val LIGHT_NAME = "Wall sconce"
    const val OTHER_LIGHTS_ON = 2

    // ---------------------------------------------------------------- helpers

    fun clamp01(x: Double): Double = if (x < 0) 0.0 else if (x > 1) 1.0 else x
    fun seg(t: Double, a: Double, b: Double): Double = clamp01((t - a) / (b - a))
    fun lerp(a: Double, b: Double, k: Double): Double = a + (b - a) * k
    fun easeOutCubic(x: Double): Double = 1 - (1 - x).pow(3)
    fun easeInQuad(x: Double): Double = x * x
    fun easeInOutQuad(x: Double): Double = if (x < 0.5) 2 * x * x else 1 - (-2 * x + 2).pow(2) / 2
    fun easeInOutSine(x: Double): Double = -(cos(PI * x) - 1) / 2

    /** The snap spring's step response at τ seconds (0 → 1, one small overshoot). */
    fun snapStep(tau: Double): Double {
        if (tau <= 0) return 0.0
        return 1 - exp(-SNAP_Z * tau) * (cos(SNAP_W * tau) + (SNAP_Z / SNAP_W) * sin(SNAP_W * tau))
    }

    /** One flip, τ seconds after the finger starts pressing: follows the press to PRESS_DEPTH, then snaps to 1. */
    fun flipCurve(tau: Double): Double {
        if (tau <= 0) return 0.0
        if (tau < PRESS) return PRESS_DEPTH * easeInQuad(tau / PRESS)
        return PRESS_DEPTH + (1 - PRESS_DEPTH) * snapStep(tau - PRESS)
    }

    /** A finger dragging the rocker: it resists near either rest and tips quickly through the middle. */
    fun detent(x: Double): Double = x - 0.1 * sin(2 * PI * x)

    /** The x that detent() maps to p (bisection; detent is monotonic). */
    fun detentInverse(p: Double): Double {
        var lo = 0.0
        var hi = 1.0
        repeat(14) {
            val mid = (lo + hi) / 2
            if (detent(mid) < p) lo = mid else hi = mid
        }
        return (lo + hi) / 2
    }

    // ---------------------------------------------------------------- the film

    /** The rocker, 0 (off) .. 1 (on). */
    fun filmPress(t: Double): Double = if (t < PRESS_OFF) flipCurve(t - PRESS_ON) else 1 - flipCurve(t - PRESS_OFF)

    /** The filament, 0..1 (before the brightness). */
    fun filmLevel(t: Double): Double {
        val up = easeOutCubic(seg(t, SNAP_ON + LAG, SNAP_ON + LAG + WARM))
        val down = easeOutCubic(seg(t, SNAP_OFF + LAG_OFF, SNAP_OFF + LAG_OFF + COOL))
        return up * (1 - down)
    }

    /** The brightness slider, DIM_TO..1. */
    fun filmBright(t: Double): Double {
        val k = easeInOutSine(seg(t, DIM, DIM_END)) - easeInOutSine(seg(t, BRIGHT, BRIGHT_END))
        return 1 - (1 - DIM_TO) * k
    }

    /** The camera's way round the wall, 0 (rest) .. 1. */
    fun filmOrbit(t: Double): Double =
        easeInOutSine(seg(t, ORBIT_OUT, ORBIT_OUT_END)) - easeInOutSine(seg(t, ORBIT_BACK, ORBIT_BACK_END))

    /** The touch dot: out = [opacity 0..1, half (+1 the top half, -1 the bottom), pressed 0..1]. */
    fun filmGhost(t: Double, out: DoubleArray) {
        val second = t > (SNAP_ON + PRESS_OFF) / 2
        val a = if (second) PRESS_OFF else PRESS_ON
        val b = if (second) SNAP_OFF else SNAP_ON
        out[0] = seg(t, a - GHOST_IN, a) * (1 - seg(t, b + 0.05, b + 0.05 + GHOST_OUT))
        out[1] = if (second) -1.0 else 1.0
        out[2] = seg(t, a, b) * (1 - seg(t, b, b + 0.12))
    }

    // ---------------------------------------------------------------- light

    /** What the scene shows: the filament times the brightness. */
    fun lightOut(level: Double, bright: Double): Double = clamp01(level) * clamp01(bright)

    /** The light's colour (linear RGB) at an output k: orange while it barely glows, warm white at full. */
    fun warmColor(k: Double, out: DoubleArray) {
        val q = sqrt(clamp01(k))
        for (i in 0..2) out[i] = lerp(WARM_LOW[i], WARM_FULL[i], q)
    }

    // ---------------------------------------------------------------- camera

    private val DIST = sqrt(
        (EYE_REST[0] - TARGET[0]).pow(2) + (EYE_REST[1] - TARGET[1]).pow(2) + (EYE_REST[2] - TARGET[2]).pow(2),
    )

    /** tan(half the vertical field of view) that keeps FRAME_W x FRAME_H of wall in a stage of this aspect (w / h). */
    fun tanHalfFovY(aspect: Double): Double = max(FRAME_H / 2, FRAME_W / 2 / aspect) / DIST

    /** Camera at orbit k and film time t: out = [eye xyz, target xyz]. A slow drift (period LOOP) keeps it alive. */
    fun cameraAt(k: Double, t: Double, out: DoubleArray) {
        val w = 2 * PI * t / LOOP
        out[0] = lerp(EYE_REST[0], EYE_ORBIT[0], k) + 0.006 * sin(w)
        out[1] = lerp(EYE_REST[1], EYE_ORBIT[1], k) + 0.004 * sin(2 * w)
        out[2] = lerp(EYE_REST[2], EYE_ORBIT[2], k)
        out[3] = TARGET[0]
        out[4] = TARGET[1]
        out[5] = TARGET[2]
    }

    /** A world point on the stage (w x h dp) for camera c (from cameraAt): out = [x, y, dp per metre there]. */
    fun project(c: DoubleArray, w: Double, h: Double, px: Double, py: Double, pz: Double, out: DoubleArray) {
        var fx = c[3] - c[0]
        var fy = c[4] - c[1]
        var fz = c[5] - c[2]
        val fl = sqrt(fx * fx + fy * fy + fz * fz)
        fx /= fl
        fy /= fl
        fz /= fl
        var rx = -fz
        var rz = fx
        val rl = hypot(rx, rz)
        rx /= rl
        rz /= rl
        val ux = -rz * fy
        val uy = rz * fx - rx * fz
        val uz = rx * fy
        val dx = px - c[0]
        val dy = py - c[1]
        val dz = pz - c[2]
        val depth = dx * fx + dy * fy + dz * fz
        val th = tanHalfFovY(w / h)
        val ppm = h / 2 / (th * depth)
        out[0] = w / 2 + (dx * rx + dz * rz) * ppm
        out[1] = h / 2 - (dx * ux + dy * uy + dz * uz) * ppm
        out[2] = ppm
    }
}

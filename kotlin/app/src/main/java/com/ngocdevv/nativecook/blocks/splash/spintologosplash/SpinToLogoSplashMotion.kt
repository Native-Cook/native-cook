package com.ngocdevv.nativecook.blocks.splash.spintologosplash

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.tan

// One loop drives the whole film (spinMotion.ts / spinScene.ts). Every film value is a pure function of the loop time
// t (seconds, 0 <= t < LOOP); the turn of the sculpture (yaw) is a separate value, the film's or the finger's.

/** The loop period (s), equal to the React Native LOOP. */
const val LOOP = 14f

internal object SpinPhase {
    const val REST = 1.25f
    const val DOWN1 = 1.25f
    const val DRAG1 = 1.4f
    const val REL1 = 2.25f
    const val STOP1 = 3.45f
    const val DOWN2 = 3.3f
    const val DRAG2 = 3.45f
    const val REL2 = 4.2f
    const val TURN_END = 5.0f
    const val FOLD_START = 5.15f
    const val FOLD_END = 6.5f
    const val HANDOFF = 6.6f
    const val RISE_START = 7.45f
    const val RISE_END = 8.35f
    const val WELCOME_IN = 8.3f
    const val HOLD_END = 11.4f
    const val UNFOLD_START = 12.7f
    const val UNFOLD_END = 13.75f
}

// ---- the scene's fixed numbers (spinScene.ts)

internal class SpinArch(val name: String, val r: Float, val a: Float, val b: Float)

internal val ARCHES = arrayOf(
    SpinArch("ArchOuter", 1.0f, 0.092f, 0.115f),
    SpinArch("ArchMiddle", 0.74f, 0.086f, 0.108f),
    SpinArch("ArchInner", 0.49f, 0.08f, 0.1f),
)
internal val REST_YAW = floatArrayOf(0f, 60f, -60f)
internal const val PEARL_R = 0.17f
internal const val PEARL_FLOAT = 0.62f
internal const val TRACK_R = 1.16f
internal const val HFOV_DEG = 26f
internal const val CAMERA_NEAR = 0.1f
internal const val CAMERA_FAR = 40f
internal val SHOT_POSTER = floatArrayOf(0f, 1.655f, 5.246f, 0f, 0f, 0f)
internal val SHOT_FRONT = floatArrayOf(0f, 0.42f, 5.9f, 0f, 0.3f, 0f)
internal const val IBL_INTENSITY = 32_000f
internal const val KEY_LUX = 70_000f
internal const val FILL_LUX = 14_000f
internal const val EXPOSURE = 0.15f

internal const val PAPER_TOP = 0xFFF4F1EC
internal const val PAPER_MID = 0xFFEFEBE4
internal const val PAPER_BOTTOM = 0xFFE6E1D9
internal const val INK = 0xFF1D1C1A
internal const val MUTE = 0xFF6E6961
internal val MARK_COLORS = longArrayOf(0xFF1D1C1A, 0xFFC2A27B, 0xFF2B2926, 0xFFCF4A2A)

/** Finger to turn: degrees per dp of horizontal drag. */
internal const val YAW_PER_PT = 0.45f
private const val D1 = 75f
private const val TD1 = SpinPhase.STOP1 - SpinPhase.REL1
private const val T1 = SpinPhase.REL1 - SpinPhase.DRAG1
private const val V1 = 2 * D1 / T1
internal const val A1 = D1 + V1 * TD1 / 2
private const val T2 = SpinPhase.REL2 - SpinPhase.DRAG2
private const val TD2 = SpinPhase.TURN_END - SpinPhase.REL2
internal const val D2 = (360 - A1) / (1 + TD2 / T2)
private const val V2 = 2 * D2 / T2

internal const val IDLE_MS = 2000L
internal const val RETURN_MS = 1200L

// ---- helpers

internal fun clamp01(x: Float): Float = if (x < 0f) 0f else if (x > 1f) 1f else x
internal fun seg(t: Float, a: Float, b: Float): Float = clamp01((t - a) / (b - a))
internal fun lerp(a: Float, b: Float, k: Float): Float = a + (b - a) * k
internal fun easeInOutCubic(x: Float): Float = if (x < 0.5f) 4 * x * x * x else 1 - (-2 * x + 2).pow(3) / 2
internal fun easeOutCubic(x: Float): Float = 1 - (1 - x).pow(3)
internal fun easeInCubic(x: Float): Float = x * x * x
internal fun easeOutBack(x: Float): Float {
    val c1 = 1.05f
    val c3 = c1 + 1
    return 1 + c3 * (x - 1).pow(3) + c1 * (x - 1).pow(2)
}

internal fun bump(t: Float, a: Float, b: Float): Float {
    val k = sin(PI * seg(t, a, b)).toFloat()
    return k * k
}

// ---- the turn

private fun flick(t: Float, a: Float, r: Float, d: Float, v: Float, td: Float): Float {
    if (t <= a) return 0f
    if (t < r) {
        val x = (t - a) / (r - a)
        return d * x * x
    }
    val u = min((t - r) / td, 1f)
    return d + (v * td / 2) * (1 - (1 - u) * (1 - u))
}

internal fun filmYaw(t: Float): Float {
    if (t >= SpinPhase.TURN_END) return 360f
    return flick(t, SpinPhase.DRAG1, SpinPhase.REL1, D1, V1, TD1) + flick(t, SpinPhase.DRAG2, SpinPhase.REL2, D2, V2, TD2)
}

/** The ghost finger: [x offset in degrees of drag, visibility, press]. */
internal fun filmFinger(t: Float, out: FloatArray) {
    var x = 0f
    var vis = 0f
    var press = 0f
    if (t >= SpinPhase.DOWN1 - 0.15f && t < SpinPhase.REL1 + 0.3f) {
        val k = clamp01((t - SpinPhase.DRAG1) / T1)
        x = -D1 / 2 + D1 * k * k
        vis = easeOutCubic(seg(t, SpinPhase.DOWN1 - 0.15f, SpinPhase.DRAG1)) *
            (1 - easeOutCubic(seg(t, SpinPhase.REL1, SpinPhase.REL1 + 0.25f)))
        press = seg(t, SpinPhase.DOWN1, SpinPhase.DRAG1) * (1 - seg(t, SpinPhase.REL1, SpinPhase.REL1 + 0.12f))
    } else if (t >= SpinPhase.DOWN2 - 0.15f && t < SpinPhase.TURN_END) {
        val k = clamp01((t - SpinPhase.DRAG2) / T2)
        x = -D2 / 2 + D2 * k * k
        vis = easeOutCubic(seg(t, SpinPhase.DOWN2 - 0.15f, SpinPhase.DRAG2)) *
            (1 - easeOutCubic(seg(t, SpinPhase.REL2, SpinPhase.REL2 + 0.25f)))
        press = seg(t, SpinPhase.DOWN2, SpinPhase.DRAG2) * (1 - seg(t, SpinPhase.REL2, SpinPhase.REL2 + 0.12f))
    }
    out[0] = x
    out[1] = vis
    out[2] = press
}

internal fun trackFill(t: Float, yaw: Float): Float = if (t >= 9f) 0f else clamp01(abs(yaw) / 360f)
internal fun trackPop(t: Float): Float = bump(t, SpinPhase.TURN_END - 0.05f, SpinPhase.TURN_END + 0.4f)

// ---- the fold

internal fun archYaw(t: Float, i: Int): Float {
    val rest = REST_YAW[i]
    if (rest == 0f) return 0f
    val lag = if (i == 1) 0f else 0.15f
    val fold = easeOutBack(seg(t, SpinPhase.FOLD_START + 0.1f + lag, SpinPhase.FOLD_START + 1.1f + lag))
    val unfold = easeInOutCubic(
        seg(t, SpinPhase.UNFOLD_START + lag * 0.6f, SpinPhase.UNFOLD_END - 0.15f + lag * 0.6f),
    )
    return rest * (1 - fold + unfold)
}

internal fun pearlY(t: Float): Float {
    val dropA = SpinPhase.FOLD_START + 0.3f
    val land = dropA + 0.45f
    if (t < dropA) return PEARL_FLOAT
    if (t < land) {
        val x = seg(t, dropA, land)
        return lerp(PEARL_FLOAT, PEARL_R, x * x)
    }
    if (t < SpinPhase.UNFOLD_START) {
        val u = seg(t, land, land + 0.36f)
        return PEARL_R + 0.075f * sin(PI * u).toFloat() * (1 - u * 0.3f)
    }
    return lerp(PEARL_R, PEARL_FLOAT, easeInOutCubic(seg(t, SpinPhase.UNFOLD_START + 0.1f, SpinPhase.UNFOLD_END)))
}

internal fun camFront(t: Float): Float =
    easeInOutCubic(seg(t, SpinPhase.TURN_END, SpinPhase.FOLD_END)) - easeInOutCubic(seg(t, 12.35f, 13.95f))

/** The film's camera: [eye x, y, z, target x, y, z]. */
internal fun cameraAt(t: Float, out: FloatArray) {
    val k = camFront(t)
    for (i in 0 until 6) out[i] = lerp(SHOT_POSTER[i], SHOT_FRONT[i], k)
}

// ---- layers

internal fun stageVisible(t: Float): Float =
    1 - seg(t, SpinPhase.HANDOFF + 0.25f, SpinPhase.HANDOFF + 0.55f) + seg(t, 12.4f, 12.7f)

internal fun markVisible(t: Float): Float =
    easeOutCubic(seg(t, SpinPhase.HANDOFF, SpinPhase.HANDOFF + 0.35f)) * (1 - seg(t, 12.6f, 12.85f))

internal fun markGleam(t: Float, out: FloatArray) {
    val u = seg(t, SpinPhase.HANDOFF + 0.35f, SpinPhase.HANDOFF + 0.95f)
    out[0] = lerp(-0.25f, 1.25f, easeInOutCubic(u))
    out[1] = bump(t, SpinPhase.HANDOFF + 0.35f, SpinPhase.HANDOFF + 0.95f)
}

internal fun markRise(t: Float): Float =
    easeInOutCubic(seg(t, SpinPhase.RISE_START, SpinPhase.RISE_END)) - easeInOutCubic(seg(t, 11.7f, 12.5f))

internal fun posterUi(t: Float): Float =
    1 - easeOutCubic(seg(t, SpinPhase.TURN_END + 0.1f, SpinPhase.TURN_END + 0.5f)) + easeInOutCubic(seg(t, 13.35f, 13.95f))

internal fun wordLetter(t: Float, i: Int): Float {
    val a = 7.9f + i * 0.05f
    return easeOutCubic(seg(t, a, a + 0.5f)) * (1 - easeInCubic(seg(t, 11.5f, 11.8f)))
}

internal fun welcomeItem(t: Float, j: Int): Float {
    val a = SpinPhase.WELCOME_IN + j * 0.08f
    val b = SpinPhase.HOLD_END + (3 - j) * 0.04f
    return easeOutCubic(seg(t, a, a + 0.55f)) * (1 - easeInCubic(seg(t, b, b + 0.3f)))
}

/** Projects world point p with camera c (eye, target) into a w × h view; writes [x, y, depth]. */
internal fun project(px: Float, py: Float, pz: Float, c: FloatArray, w: Float, h: Float, out: FloatArray) {
    var fx = c[3] - c[0]
    var fy = c[4] - c[1]
    var fz = c[5] - c[2]
    val fl = hypot(hypot(fx, fy), fz).let { if (it == 0f) 1f else it }
    fx /= fl; fy /= fl; fz /= fl
    var rx = -fz
    var rz = fx
    val rl = hypot(rx, rz).let { if (it == 0f) 1f else it }
    rx /= rl; rz /= rl
    val ux = -rz * fy
    val uy = rz * fx - rx * fz
    val uz = rx * fy
    val focal = w / 2 / tan(HFOV_DEG * PI / 360).toFloat()
    val dx = px - c[0]
    val dy = py - c[1]
    val dz = pz - c[2]
    val z = dx * fx + dy * fy + dz * fz
    out[0] = w / 2 + (dx * rx + dz * rz) / z * focal
    out[1] = h / 2 - (dx * ux + dy * uy + dz * uz) / z * focal
    out[2] = z
}

internal const val TRACK_STEPS = 96
internal fun trackSteps(fill: Float): Int = maxOf(2, ceil(TRACK_STEPS * fill).toInt())

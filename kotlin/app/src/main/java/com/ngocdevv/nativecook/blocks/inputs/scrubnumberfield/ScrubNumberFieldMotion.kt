package com.ngocdevv.nativecook.blocks.inputs.scrubnumberfield

import java.util.Locale
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

// Scrub Number Field: the fields, the film and the odometer, as pure functions (a port of
// scrubMotion.ts).
//
// A field's value is one number, `pos`: the value counted in steps (Exposure +0.35 at 0.05 a step is
// pos 7). While a finger scrubs, pos is continuous; on release a spring snaps it to a whole step.
// The photo's shader, the ruler under the number and the rolling digits are all drawn from pos.

/** The film's period, seconds. */
const val LOOP = 9f

// ---------------------------------------------------------------- the fields

internal const val FIELD_COUNT = 3
internal const val EXPOSURE = 0
internal const val WARMTH = 1
internal const val VIGNETTE = 2

internal class ScrubFieldSpec(
    val label: String,
    val step: Float, // value of one step
    val unitsPerStep: Int, // one step in units of the last digit shown (0.05 = 5 hundredths)
    val decimals: Int,
    val signed: Boolean,
    val min: Int, // in steps
    val max: Int,
    val rest: Int, // in steps: what the screen opens on, and what a double tap returns to
    val peak: Int, // in steps: where the film scrubs it to
    val accessibilityUnit: String,
)

internal val FIELDS = listOf(
    ScrubFieldSpec("Exposure", 0.05f, 5, 2, true, -40, 40, 7, 23, "stops"),
    ScrubFieldSpec("Warmth", 1f, 1, 0, true, -100, 100, 12, 46, ""),
    ScrubFieldSpec("Vignette", 1f, 1, 0, false, 0, 100, 40, 62, "percent"),
)

/** The value a field shows at a whole step, e.g. "+0.35". */
internal fun formatValue(spec: ScrubFieldSpec, pos: Int): String {
    val v = pos * spec.step
    val body = String.format(Locale.US, "%.${spec.decimals}f", abs(v))
    if (!spec.signed || pos == 0) return body
    return "${if (v > 0) "+" else "−"}$body"
}

// ---------------------------------------------------------------- scrubbing

internal const val TICK = 8f // dp of ruler per step; at a slow drag the ruler follows the finger 1:1
internal const val MAJOR_EVERY = 5 // a longer tick every five steps
private const val GAIN_MAX = 4f // a fast drag covers up to four steps per tick of travel
private const val GAIN_FROM = 250f // dp/s where the drag starts to get coarser
private const val GAIN_TO = 1100f // dp/s where it is coarsest
private const val RUBBER = 5f // steps: how far past a limit the value can be pulled
internal const val PROJECT = 0.1f // s of release velocity carried into the snap
internal const val STRETCH_SPEED = 900f // dp/s of finger speed for the full stretch
internal const val STRETCH_MAX = 0.045f // the field is at most 4.5 % wider
internal const val SQUASH_MAX = 0.012f // and 1.2 % flatter
private const val BLUR_FROM = 14f // steps/s where the digits start to smear
private const val BLUR_TO = 90f // steps/s where the smear is full
private const val BLUR_MAX = 7f // dp, vertical sigma
internal const val SPEED_TAU = 0.06f // s, smoothing of the measured speed
internal const val LIFT = 8f // dp the number rises while the ruler shows

// The snap on release (response 0.38 s, damping ratio 0.72).
private const val SNAP_RESPONSE = 0.38
private const val SNAP_DAMPING = 0.72
private val OMEGA = 2 * PI / SNAP_RESPONSE
private val OMEGA_D = OMEGA * sqrt(1 - SNAP_DAMPING * SNAP_DAMPING)
private val DECAY = SNAP_DAMPING * OMEGA

internal const val RESET_S = 0.8f // a double tap rolls the digits back, ease in-out cubic
internal const val RESET_STAGGER = 0.06f // s between the fields when all three go back

internal fun clamp01(x: Float) = if (x < 0f) 0f else if (x > 1f) 1f else x

/** 0 before a, 1 after b. */
internal fun seg(t: Float, a: Float, b: Float) = clamp01((t - a) / (b - a))

internal fun smooth(x: Float): Float {
    val c = clamp01(x)
    return c * c * (3 - 2 * c)
}

internal fun easeInOutCubic(x: Float): Float {
    val c = clamp01(x)
    return if (c < 0.5f) 4 * c * c * c else 1 - (-2 * c + 2).pow(3) / 2
}

/** Reanimated's default `withTiming` easing. */
internal fun easeInOutQuad(x: Float): Float {
    val c = clamp01(x)
    return if (c < 0.5f) 2 * c * c else 1 - (-2 * c + 2).pow(2) / 2
}

/** The snap spring from `from` towards `to`, s seconds after a release with velocity v0 (units/s). */
internal fun snapTo(from: Float, to: Float, v0: Float, s: Float): Float {
    if (s <= 0f) return from
    val a = (from - to).toDouble()
    val b = (v0 + DECAY * a) / OMEGA_D
    return (to + exp(-DECAY * s) * (a * cos(OMEGA_D * s) + b * sin(OMEGA_D * s))).toFloat()
}

/** How many steps one tick of finger travel is worth at this finger speed (dp/s). */
internal fun scrubGain(speed: Float) = 1 + (GAIN_MAX - 1) * smooth((speed - GAIN_FROM) / (GAIN_TO - GAIN_FROM))

/** A value pulled past its limits gives way less and less. */
internal fun rubber(raw: Float, min: Float, max: Float): Float {
    if (raw > max) {
        val over = raw - max
        return max + over / (1 + over / RUBBER)
    }
    if (raw < min) {
        val over = min - raw
        return min - over / (1 + over / RUBBER)
    }
    return raw
}

/** Vertical blur of the rolling digits (dp, sigma) for a value moving at `speed` steps per second. */
internal fun digitBlur(speed: Float) = BLUR_MAX * clamp01((speed - BLUR_FROM) / (BLUR_TO - BLUR_FROM))

// ---------------------------------------------------------------- the odometer

/**
 * Position of the last digit's strip for |value| = x (in units of that digit): continuous, in glyphs.
 * Its strip holds 10 / unitsPerStep glyphs (0 and 5 for Exposure, 0-9 otherwise).
 */
internal fun odoLow(x: Float, unitsPerStep: Int): Float {
    val n = 10f / unitsPerStep
    val p = x / unitsPerStep
    return p - floor(p / n) * n
}

/**
 * Position of the strip of digit k >= 1 (tens of the last digit, hundreds, ...), unwrapped: it rests
 * on a whole digit and only turns during the last step before the digits below it wrap.
 */
internal fun odoHigh(x: Float, k: Int, unitsPerStep: Int): Float {
    val m = 10f.pow(k)
    val base = floor(x / m)
    val rem = x - base * m
    val start = m - unitsPerStep
    return base + if (rem > start) (rem - start) / unitsPerStep else 0f
}

private const val ROLL_FROM = 0.25f // a digit strip turns while its position's fraction goes
private const val ROLL_TO = 0.75f //   from ROLL_FROM to ROLL_TO, and rests on a whole digit otherwise

/** A strip position with the turn squeezed into the middle of each step. */
internal fun rollEase(v: Float): Float {
    val base = floor(v)
    return base + smooth((v - base - ROLL_FROM) / (ROLL_TO - ROLL_FROM))
}

// ---------------------------------------------------------------- the film

// The ghost finger presses field i at DRAG_A[i] - PRESS_LEAD, drags from DRAG_A[i] to DRAG_B[i]
// (ease in-out cubic) and lifts; it lets go RELEASE_OFF steps past a detent, and the spring snaps back.
internal val DRAG_A = floatArrayOf(0.95f, 3.0f, 4.75f)
internal val DRAG_B = floatArrayOf(2.35f, 3.75f, 5.75f)
private val DRAG_PX = floatArrayOf(128f, 170f, 140f) // dp the finger travels
internal const val PRESS_LEAD = 0.14f // s
private const val RELEASE_OFF = 0.4f // steps
private const val TAP_1 = 6.75f // the double tap on the photo
private const val TAP_2 = 6.95f
private const val RESET_T = 7.0f // the digits start rolling back

/** pos of field i at loop time t. */
internal fun filmPos(i: Int, t: Float): Float {
    val rest = FIELDS[i].rest.toFloat()
    val peak = FIELDS[i].peak.toFloat()
    val b = DRAG_B[i]
    val p = if (t <= b) rest + (peak + RELEASE_OFF - rest) * easeInOutCubic(seg(t, DRAG_A[i], b))
    else snapTo(peak + RELEASE_OFF, peak, 0f, t - b)
    val start = RESET_T + i * RESET_STAGGER
    val back = easeInOutCubic(seg(t, start, start + RESET_S))
    return p + (rest - p) * back
}

/** 0..1: field i is pressed (ruler shown, number lifted). */
internal fun filmActive(i: Int, t: Float): Float {
    val a = DRAG_A[i]
    val b = DRAG_B[i]
    return smooth(seg(t, a - PRESS_LEAD, a - 0.02f)) * (1 - smooth(seg(t, b, b + 0.3f)))
}

/** dp the ghost finger has travelled on field i. */
internal fun filmFinger(i: Int, t: Float) = DRAG_PX[i] * easeInOutCubic(seg(t, DRAG_A[i], DRAG_B[i]))

/** -1..1: the field's stretch, from the ghost finger's speed. */
internal fun filmStretch(i: Int, t: Float): Float {
    val dt = 1f / 120
    val v = (filmFinger(i, t) - filmFinger(i, t - dt)) / dt
    return (v / STRETCH_SPEED).coerceIn(-1f, 1f)
}

/** Opacity of the ghost finger over field i. */
internal fun filmDotAlpha(i: Int, t: Float): Float {
    val a = DRAG_A[i] - PRESS_LEAD
    val b = DRAG_B[i]
    return smooth(seg(t, a - 0.2f, a)) * (1 - smooth(seg(t, b + 0.05f, b + 0.3f)))
}

/** How far the ghost finger is pressed on field i. */
internal fun filmDotPress(i: Int, t: Float) =
    smooth(seg(t, DRAG_A[i] - PRESS_LEAD, DRAG_A[i] - 0.02f)) * (1 - smooth(seg(t, DRAG_B[i], DRAG_B[i] + 0.12f)))

/** Opacity of the ghost finger over the photo, for the double tap. */
internal fun filmTapAlpha(t: Float) =
    smooth(seg(t, TAP_1 - 0.3f, TAP_1 - 0.08f)) * (1 - smooth(seg(t, TAP_2 + 0.1f, TAP_2 + 0.35f)))

private fun tapBump(t: Float, e: Float) = smooth(seg(t, e - 0.07f, e)) * (1 - smooth(seg(t, e + 0.03f, e + 0.12f)))

/** 0..1: how far the ghost finger is pressed during the double tap. */
internal fun filmTapPress(t: Float) = maxOf(tapBump(t, TAP_1), tapBump(t, TAP_2))

internal const val MODE_AUTO = 0 // the film
internal const val MODE_MANUAL = 1 // a finger
internal const val MODE_RETURN = 2 // easing back to rest before the film restarts

/** JavaScript's Math.round: halves go up. */
internal fun roundStep(x: Float) = floor(x + 0.5f).roundToInt()

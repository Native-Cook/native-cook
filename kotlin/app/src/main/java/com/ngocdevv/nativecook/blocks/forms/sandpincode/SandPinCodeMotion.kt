package com.ngocdevv.nativecook.blocks.forms.sandpincode

import android.graphics.Path
import android.graphics.PathMeasure
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.pow
import kotlin.math.sin

/*
 Sand PIN Code — digit strokes, the loop script and the grains, as pure functions of time (a port
 of sandPinCodeMotion.ts).

 LOOP = 10 s
   0.0 – 1.25   rest: raked sand, six empty slots.
   1.25 – 3.81  the wrong code 4 8 2 7 1 5: each pebble squashes, a bamboo stylus carves the
                digit along its stroke in 0.26 s (ease-in-out), 0.46 s apart.
   3.95 – 4.40  the tray shakes (damped, 9 dp, 9 Hz).
   4.35 – 5.15  a seven-tine rake sweeps left to right; as it reaches each digit the groove
                crumbles into 50 grains that are pushed ahead, slide down and sink (0.9 s).
   5.65 – 7.81  the right code 2 6 0 5 1 9, 0.38 s apart.
   7.85 – 8.85  unlock: a ring packet travels out through the sand, the tray brightens, the
                grooves glow, the title turns to "Welcome back".
   8.80 – 9.50  the sand smooths over, the light and title return.
   9.50 – 10    rest.
*/

const val LOOP = 10f
internal val SandCode = intArrayOf(2, 6, 0, 5, 1, 9)
private val WRONG = intArrayOf(4, 8, 2, 7, 1, 5)
private const val WRONG_AT = 1.25f
private const val WRONG_STEP = 0.46f
private const val RIGHT_AT = 5.65f
private const val RIGHT_STEP = 0.38f
internal const val SAND_CARVE = 0.26f
private const val PRESS_LEAD = 0.08f
private const val PRESS_LEN = 0.2f
private const val SHAKE_LEN = 0.45f
private const val SHAKE_AT = 3.95f
internal const val SAND_RAKE_LEN = 0.8f
private const val RAKE_AT = 4.35f
internal const val SAND_GRAIN_LIFE = 0.9f
private const val UNLOCK_AT = 7.85f
private const val SMOOTH_AT = 8.8f
private const val SMOOTH_END = 9.5f

internal const val SAND_SLOTS = 6
internal const val SAND_SLOT_GAP = 50f
internal const val SAND_SLOT_HALF_H = 30f
private const val DIGIT_SCALE = 1.05f
internal const val SAND_SAMPLES = 50
internal const val SAND_RAKE_OVER = 80f

// Single-stroke digits in a 24 × 34 box, written the way a finger would draw them in sand.
private val DIGITS = arrayOf(
    "M12 2 C4.5 2 2 10 2 17 C2 25 5 32 12 32 C19 32 22 25 22 17 C22 9 19.5 2 12 2",
    "M6.5 7.5 L13 2 L13 32",
    "M3 9 C3 4.5 7 2 12 2 C17 2 21 5 21 10 C21 16 15.5 20.5 3 32 L22 32",
    "M4 5 C7 2.8 10 2 12.5 2 C17.5 2 20.5 5 20.5 9 C20.5 13 17 16 11 16 C18 16 22 19.5 22 24.5 C22 29.5 17.5 32 12 32 C8 32 5 31 2.5 28.5",
    "M16 32 L16 2 L2.5 23 L23 23",
    "M20.5 2 L6.5 2 L4.5 15 C7 13.6 9.5 13 12 13 C18 13 22 16.5 22 22.5 C22 28.5 17.5 32 12 32 C8 32 5 30.6 3 28",
    "M19 3.6 C17 2.6 15 2 13 2 C6 2 3 9 3 19 C3 27 6.5 32 12.5 32 C18 32 21.5 28 21.5 22.5 C21.5 17 18 13.5 12.5 13.5 C8 13.5 4.6 16 3.2 19.5",
    "M2 2 L22 2 C15 11 11 20 9 32",
    "M12 16 C7 16 4 13 4 9 C4 5 7.5 2 12 2 C16.5 2 20 5 20 9 C20 13 17 16 12 16 C6 16 2.5 19.5 2.5 24 C2.5 28.5 6.5 32 12 32 C17.5 32 21.5 28.5 21.5 24 C21.5 19.5 18 16 12 16",
    "M21 14 C19.5 17.5 16 20 11.5 20 C6 20 2.5 16.5 2.5 11 C2.5 5.5 6.5 2 12 2 C18 2 21.5 6.5 21.5 14 C21.5 24 17.5 32 10 32 C8 32 6 31.6 4.5 30.6",
)

/** Each digit's stroke (centred on 0, 0), a measure of it, and SAMPLES points along it. */
internal class SandDigitArt(val measures: Array<PathMeasure>, val lengths: FloatArray, val samples: Array<FloatArray>)

/** Parses the M / L / C paths above (absolute coordinates only). */
private fun parseDigit(d: String): Path {
    val p = Path()
    val tok = d.replace(Regex("([MLC])"), " $1 ").trim().split(Regex("\\s+"))
    var i = 0
    var cmd = 'M'
    fun f() = (tok[i++].toFloat() - 0f)
    while (i < tok.size) {
        val t = tok[i]
        if (t.length == 1 && t[0].isLetter()) { cmd = t[0]; i++; continue }
        when (cmd) {
            'M' -> { val x = f(); val y = f(); p.moveTo(x, y) }
            'L' -> { val x = f(); val y = f(); p.lineTo(x, y) }
            'C' -> { val a = f(); val b = f(); val c = f(); val e = f(); val g = f(); val h = f(); p.cubicTo(a, b, c, e, g, h) }
        }
    }
    val m = android.graphics.Matrix()
    m.setTranslate(-12f, -17f)
    m.postScale(DIGIT_SCALE, DIGIT_SCALE)
    p.transform(m)
    return p
}

internal fun sandMakeDigitArt(): SandDigitArt {
    val measures = Array(DIGITS.size) { PathMeasure(parseDigit(DIGITS[it]), false) }
    val lengths = FloatArray(DIGITS.size) { measures[it].length }
    val pos = FloatArray(2)
    val samples = Array(DIGITS.size) { d ->
        val out = FloatArray(SAND_SAMPLES * 2)
        for (i in 0 until SAND_SAMPLES) {
            measures[d].getPosTan(lengths[d] * i / (SAND_SAMPLES - 1), pos, null)
            out[i * 2] = pos[0]
            out[i * 2 + 1] = pos[1]
        }
        out
    }
    return SandDigitArt(measures, lengths, samples)
}

/** The screen's state at one moment; arrays have one entry per slot. */
internal class SandFrame {
    val digits = IntArray(SAND_SLOTS) { -1 }
    val carveAt = FloatArray(SAND_SLOTS) { -1f }
    val passAt = FloatArray(SAND_SLOTS) { -1f }
    val grainDigits = IntArray(SAND_SLOTS) { -1 }
    val grainAt = FloatArray(SAND_SLOTS) { -1f }
    var rakeX = -999f
    var rakeOn = 0f
    var rakeFrom = 0f
    var linesOn = 0f
    var shake = 0f
    var ripple = -1f
    var bright = 0f
    var unlock = 0f
    var fade = 1f
    var key = -1
    var press = 0f

    fun copyFrom(o: SandFrame) {
        o.digits.copyInto(digits); o.carveAt.copyInto(carveAt); o.passAt.copyInto(passAt)
        o.grainDigits.copyInto(grainDigits); o.grainAt.copyInto(grainAt)
        rakeX = o.rakeX; rakeOn = o.rakeOn; rakeFrom = o.rakeFrom; linesOn = o.linesOn; shake = o.shake
        ripple = o.ripple; bright = o.bright; unlock = o.unlock; fade = o.fade; key = o.key; press = o.press
    }
}

internal fun sandClamp01(x: Float) = if (x < 0f) 0f else if (x > 1f) 1f else x
internal fun sandSeg(t: Float, a: Float, b: Float) = sandClamp01((t - a) / (b - a))
internal fun sandEaseOutCubic(x: Float): Float { val u = 1 - x; return 1 - u * u * u }
internal fun sandEaseInOutCubic(x: Float) = if (x < 0.5f) 4 * x * x * x else 1 - (-2 * x + 2).pow(3) / 2

internal fun sandHash(i: Float): Float {
    val s = sin(i * 12.9898 + 78.233) * 43758.5453
    return (s - floor(s)).toFloat()
}

/** The tray's damped shake τ seconds after a wrong code. */
internal fun sandShakeAt(tau: Float): Float {
    if (tau < 0f || tau > SHAKE_LEN) return 0f
    return 9f * sin(2 * PI.toFloat() * 9f * tau) * exp(-tau * 6.5f) * (1 - tau / SHAKE_LEN)
}

/** A key's squash τ seconds after it is pressed: in fast, out on a soft bounce. */
internal fun sandPressAt(tau: Float): Float {
    if (tau < 0f || tau > PRESS_LEN + 0.25f) return 0f
    if (tau < 0.06f) return sandEaseOutCubic(tau / 0.06f)
    val u = (tau - 0.06f) / (PRESS_LEN + 0.19f)
    return exp(-u * 4f) * cos(u * 7.5f)
}

internal fun sandKeyIndex(d: Int) = if (d == 0) 10 else d - 1

/** The loop at time t. `x0` is the first slot's centre x. */
internal fun sandAuto(t: Float, x0: Float, s: SandFrame = SandFrame()): SandFrame {
    val from = x0 - SAND_RAKE_OVER
    val to = x0 + (SAND_SLOTS - 1) * SAND_SLOT_GAP + SAND_RAKE_OVER
    fun passOf(i: Int) = RAKE_AT + (SAND_RAKE_LEN * (x0 + i * SAND_SLOT_GAP - from)) / (to - from)

    val right = t >= RIGHT_AT - 0.1f
    for (i in 0 until SAND_SLOTS) {
        if (!right) {
            s.digits[i] = WRONG[i]
            s.carveAt[i] = WRONG_AT + i * WRONG_STEP
            s.passAt[i] = passOf(i)
        } else if (t < SMOOTH_END) {
            s.digits[i] = SandCode[i]
            s.carveAt[i] = RIGHT_AT + i * RIGHT_STEP
        }
        s.grainDigits[i] = WRONG[i]
        s.grainAt[i] = passOf(i)
    }
    val at = if (right) RIGHT_AT else WRONG_AT
    val step = if (right) RIGHT_STEP else WRONG_STEP
    val code = if (right) SandCode else WRONG
    for (i in 0 until SAND_SLOTS) {
        val p = sandPressAt(t - (at + i * step - PRESS_LEAD))
        if (p != 0f) {
            s.key = sandKeyIndex(code[i])
            s.press = p
        }
    }
    s.shake = sandShakeAt(t - SHAKE_AT)
    val r = sandSeg(t, RAKE_AT, RAKE_AT + SAND_RAKE_LEN)
    s.rakeX = from + (to - from) * sandEaseInOutCubic(r)
    s.rakeFrom = from
    s.rakeOn = when {
        t < RAKE_AT -> 0f
        t < RAKE_AT + SAND_RAKE_LEN -> sandClamp01((t - RAKE_AT) / 0.12f)
        else -> 1 - sandSeg(t, RAKE_AT + SAND_RAKE_LEN, RAKE_AT + SAND_RAKE_LEN + 0.25f)
    }
    if (t > RAKE_AT + SAND_RAKE_LEN + 0.25f) s.rakeX = to + 200f
    s.linesOn = if (t >= RAKE_AT) 1f else 0f
    s.ripple = if (t >= UNLOCK_AT) t - UNLOCK_AT else -1f
    s.bright = sandEaseOutCubic(sandSeg(t, UNLOCK_AT, UNLOCK_AT + 0.4f)) * (1 - sandEaseInOutCubic(sandSeg(t, SMOOTH_AT + 0.1f, SMOOTH_END)))
    s.unlock = sandEaseOutCubic(sandSeg(t, UNLOCK_AT + 0.1f, UNLOCK_AT + 0.5f)) * (1 - sandEaseInOutCubic(sandSeg(t, SMOOTH_AT + 0.15f, SMOOTH_AT + 0.6f)))
    s.fade = 1 - sandEaseInOutCubic(sandSeg(t, SMOOTH_AT, SMOOTH_END))
    if (t >= SMOOTH_END) s.linesOn = 0f
    return s
}

/** The user's take on the tray: digits and the times things happened, on the `now` clock. */
internal class SandUser {
    val sand = SandFrame()
    val entered = ArrayList<Int>()
    var keyAt = -9f
    var shakeAt = -9f
    var rakeStart = -1f
    var fingerRake = false
    var checkAt = -1f
    var unlockAt = -1f
    var returnAt = -1f
}

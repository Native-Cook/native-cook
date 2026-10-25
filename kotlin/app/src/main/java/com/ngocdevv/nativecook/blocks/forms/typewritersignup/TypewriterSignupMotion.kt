package com.ngocdevv.nativecook.blocks.forms.typewritersignup

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.pow
import kotlin.math.sin

/*
 Typewriter Sign Up — the loop script, as pure functions of the loop time (a port of
 typewriterSignupMotion.ts). Every frame of autoPlay is `typewriterAutoPose(t)` with
 t = elapsed % LOOP, so the frame at t = LOOP is the frame at t = 0.

 LOOP = 10 s
   0.0 – 1.2   rest: blank sheet, typebar fan still.
   1.2 – 2.6   types "Mara Quill".
   2.55        the margin bell rings; 2.6 – 3.1 carriage return + line feed.
   3.1 – 5.0   types "mara@inkwell.co".
   4.95        bell; 5.0 – 5.4 carriage return + line feed.
   5.4 – 6.6   stamps eight password dots.
   6.75 – 6.95 the CTA is pressed.
   7.0 – 8.2   the sheet is pulled: 4 pt anticipation down, then up and out with a flex.
   8.2 – 8.4   empty carriage.
   8.4 – 9.4   a blank sheet feeds back in from above (ease-out, 3 pt settle).
   9.4 – 10    rest.
*/

const val LOOP = 10f

internal val TypewriterScript = arrayOf("Mara Quill", "mara@inkwell.co", "••••••••")
private val LINE_START = floatArrayOf(1.2f, 3.1f, 5.4f)
private val LINE_DUR = floatArrayOf(1.4f, 1.9f, 1.2f)
private val RETURN_AT = floatArrayOf(2.6f, 5.0f)
private val RETURN_DUR = floatArrayOf(0.5f, 0.4f)
private const val BELL_LEAD = 0.05f
private const val PULL_START = 7.0f
private const val ANTICIPATION = 0.14f
private const val PULL_END = 8.2f
private const val BACK_START = 8.4f
private const val BACK_END = 9.4f
private const val PRESS_AT = 6.75f
private const val PRESS_LEN = 0.2f

/** How far the sheet travels to leave the screen (dp). */
internal const val TYPEWRITER_SHEET_AWAY = 680f
private const val STRIKE_UP = 0.07f
private const val STRIKE_DOWN = 0.11f

/** Number of typebars drawn in the fan. */
internal const val TYPEWRITER_BARS = 9

/** One frame of the machine. */
internal class TypewriterPose {
    var n0 = 0
    var n1 = 0
    var n2 = 0
    var line = 0f
    var col = 0f
    var strike = 0f
    var bar = 4
    var inkAge = 9f
    var bell = 0f
    var bellGlow = 0f
    var pull = 0f
    var flex = 0f
    var press = 0f
    var done = 0f
    var fields = 1f
    fun n(l: Int) = when (l) { 0 -> n0; 1 -> n1; else -> n2 }
}

internal fun twClamp01(x: Float) = if (x < 0f) 0f else if (x > 1f) 1f else x
internal fun twSeg(t: Float, a: Float, b: Float) = twClamp01((t - a) / (b - a))
internal fun twEaseOutCubic(x: Float): Float { val u = 1 - x; return 1 - u * u * u }
internal fun twEaseInOutCubic(x: Float) = if (x < 0.5f) 4 * x * x * x else 1 - (-2 * x + 2).pow(3) / 2

internal fun twHash(i: Float): Float {
    val s = sin(i * 12.9898 + 78.233) * 43758.5453
    return (s - floor(s)).toFloat()
}

/** A carriage step: a stiff, lightly underdamped spring from 0 to 1. */
internal fun twCarriageStep(tau: Float): Float =
    if (tau <= 0f) 0f else 1f - exp(-30f * tau) * cos(48f * tau)

/** The typebar's extension τ seconds after a key: up in 70 ms, back in 110 ms. */
internal fun twStrikeCurve(tau: Float): Float = when {
    tau < 0f -> 0f
    tau < STRIKE_UP -> twEaseOutCubic(tau / STRIKE_UP)
    tau < STRIKE_UP + STRIKE_DOWN -> 1f - twEaseInOutCubic((tau - STRIKE_UP) / STRIKE_DOWN)
    else -> 0f
}

/** The bell τ seconds after it is hit: (wobble, glow). */
internal fun twBell(tau: Float, out: FloatArray) {
    if (tau < 0f || tau > 1.2f) { out[0] = 0f; out[1] = 0f; return }
    val env = exp(-5f * tau)
    out[0] = sin(tau * 42f) * env
    out[1] = exp(-3.2f * tau) * twClamp01(tau / 0.04f)
}

internal fun twBarFor(l: Int, k: Int) = floor(twHash((l * 97 + k * 13 + 5).toFloat()) * TYPEWRITER_BARS).toInt()

private fun keyTime(l: Int, k: Int): Float {
    val n = TypewriterScript[l].length
    return LINE_START[l] + (LINE_DUR[l] * (k + 0.5f + 0.38f * (twHash((l * 31 + k).toFloat()) - 0.5f))) / n
}

private fun typedAt(l: Int, t: Float): Int {
    var c = 0
    for (k in 0 until TypewriterScript[l].length) if (keyTime(l, k) <= t) c = k + 1
    return c
}

/** Glyphs typed on each line at loop time t (cheap enough for a derivedStateOf). */
internal fun twTypedCounts(t: Float): Int {
    if (t >= PULL_END) return 0
    return typedAt(0, t) or (typedAt(1, t) shl 8) or (typedAt(2, t) shl 16)
}

/** The legible end state shown under reduced motion: all three lines typed. */
internal fun TypewriterPose.fill(): TypewriterPose {
    n0 = TypewriterScript[0].length
    n1 = TypewriterScript[1].length
    n2 = TypewriterScript[2].length
    line = 2f
    col = TypewriterScript[2].length.toFloat()
    return this
}

private val bellTmp = FloatArray(2)

internal fun typewriterAutoPose(t: Float): TypewriterPose {
    val p = TypewriterPose()
    if (t >= PULL_END && t < BACK_START) {
        p.pull = TYPEWRITER_SHEET_AWAY
        p.done = 1f
        return p
    }
    if (t >= BACK_START) {
        val u = twSeg(t, BACK_START, BACK_END)
        val e = twEaseOutCubic(u)
        val settle = if (u >= 1f) 0f else -3f * sin(PI.toFloat() * twClamp01((u - 0.7f) / 0.3f))
        p.pull = TYPEWRITER_SHEET_AWAY * (1 - e) + settle
        p.done = 1 - twEaseInOutCubic(twSeg(t, BACK_START, BACK_START + 0.6f))
        return p
    }
    p.n0 = typedAt(0, t)
    p.n1 = typedAt(1, t)
    p.n2 = typedAt(2, t)

    var l = 0
    if (t >= RETURN_AT[1]) l = 2 else if (t >= RETURN_AT[0]) l = 1
    val typedHere = p.n(l)
    var lastKey = -1f
    if (typedHere > 0) lastKey = keyTime(l, typedHere - 1)

    var col = 0f
    var line = l.toFloat()
    for (r in 0 until 2) {
        val a = RETURN_AT[r]
        val d = RETURN_DUR[r]
        if (t >= a && t < a + d) {
            val u = twEaseInOutCubic(twSeg(t, a, a + d))
            col = TypewriterScript[r].length * (1 - u)
            line = r + u
            lastKey = -1f
        } else if (t >= a + d && l == r + 1) {
            val tau = t - (a + d)
            col = -0.32f * exp(-12f * tau) * sin(30f * tau)
            line = (r + 1).toFloat()
        }
    }
    if (lastKey >= 0f) col = typedHere - 1 + twCarriageStep(t - lastKey)
    else if (l == 0) col = 0f
    p.col = col
    p.line = line

    if (lastKey >= 0f) {
        val tau = t - lastKey
        p.strike = twStrikeCurve(tau)
        p.inkAge = tau
        p.bar = twBarFor(l, typedHere - 1)
    }
    for (r in 0 until 2) {
        twBell(t - (RETURN_AT[r] - BELL_LEAD), bellTmp)
        if (bellTmp[1] > p.bellGlow) {
            p.bell = bellTmp[0]
            p.bellGlow = bellTmp[1]
        }
    }

    val pr = twSeg(t, PRESS_AT, PRESS_AT + PRESS_LEN)
    p.press = sin(PI.toFloat() * pr)
    if (t >= PULL_START) {
        val ua = twSeg(t, PULL_START, PULL_START + ANTICIPATION)
        val up = twSeg(t, PULL_START + ANTICIPATION, PULL_END)
        p.pull = -4f * sin(PI.toFloat() * ua) * (if (up > 0f) 0f else 1f) + TYPEWRITER_SHEET_AWAY * twEaseInOutCubic(up)
        p.flex = sin(PI.toFloat() * up)
        p.fields = 1 - twEaseOutCubic(twSeg(t, PULL_START, PULL_START + 0.5f))
        p.done = twEaseOutCubic(twSeg(t, PULL_START + 0.4f, PULL_START + 0.9f))
    }
    return p
}

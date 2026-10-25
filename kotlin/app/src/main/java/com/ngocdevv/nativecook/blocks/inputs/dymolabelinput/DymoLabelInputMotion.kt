package com.ngocdevv.nativecook.blocks.inputs.dymolabelinput

import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

/*
 Dymo Label Input — the autoPlay script and the pose it produces. Every value is a pure function of
 the loop time t.

   0.0 – 1.2   rest: a blank stub of red tape out of the head, the wheel on A, an unlabelled jar.
   1.2 – 4.6   "OATS" is typed, 0.85 s a letter: the wheel spins to the letter (0.32 s, ease-in-out,
               a click past it and back), the trigger is squeezed (0.16 s down, 0.2 s up), the letter
               whitens as it is pressed into the tape, and the tape advances one pitch (0.2 s).
   4.6 – 5.0   Return: the cutter snaps and the strip comes free.
   5.0 – 6.45  the strip flies onto the jar along an arc with a turn and a lift, and sticks with a squash.
   6.2 – 7.4   hold.
   7.4 – 8.4   the jar slides off along the shelf and a fresh one slides in; new tape feeds out; the
               wheel spins back to A; the field clears.
   8.4 – 9.0   rest.
*/

internal const val LOOP = 9f
internal const val WORD = "OATS"
/** The wheel's characters, clockwise. */
internal const val WHEEL = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789-&. "
internal val STEP = 360f / WHEEL.length
private const val LETTER_FROM = 1.2f
private const val LETTER_EVERY = 0.85f
internal const val DYMO_MAX = 12

internal class DymoTape(val name: String, val hex: Long, val rgb: FloatArray)

internal val Tapes = listOf(
    DymoTape("Red", 0xC3313B, floatArrayOf(0.76f, 0.19f, 0.23f)),
    DymoTape("Black", 0x2C2C31, floatArrayOf(0.17f, 0.17f, 0.19f)),
    DymoTape("Blue", 0x2F5EA6, floatArrayOf(0.18f, 0.37f, 0.65f)),
    DymoTape("Green", 0x2D7A59, floatArrayOf(0.18f, 0.48f, 0.35f)),
    DymoTape("Yellow", 0xE5B636, floatArrayOf(0.9f, 0.71f, 0.21f)),
)

/** One frame of the machine. */
internal class DymoPose {
    /** Wheel rotation (deg): slot j is under the head when wheel = -j * STEP. */
    var wheel = 0f
    var trigger = 0f
    /** Letters on the tape in the machine, how white each is (0..1), how far each pops (0..1). */
    var tape = ""
    val whiten = FloatArray(DYMO_MAX)
    val pop = FloatArray(DYMO_MAX)
    /** Tape advance in pitches, and blank tape beyond the first letter (pt). */
    var advance = 0f
    var lead = 18f
    /** The cut strip: its letters, flight 0 → 1, squash, cutter lever. */
    var strip = ""
    var fly = 0f
    var squash = 0f
    var cutter = 0f
    /** The jar swap 0 → 1. */
    var slide = 0f
    val tapeRGB = FloatArray(3)
    val stripRGB = FloatArray(3)
}

internal fun dySeg(t: Float, a: Float, b: Float) = min(1f, max(0f, (t - a) / (b - a)))

internal fun dyEaseInOut(u: Float) = if (u < 0.5f) 4 * u * u * u else 1 - (-2 * u + 2).pow(3) / 2

internal fun dyEaseOut(u: Float) = 1 - (1 - u).pow(3)

/** Spin with a detent click: ease-in-out to the target, then a small damped overshoot. */
private fun click(u: Float): Float {
    if (u <= 0) return 0f
    val base = dyEaseInOut(min(1f, u / 0.75f))
    val after = max(0f, (u - 0.75f) / 0.25f)
    return base + if (u > 0.75f) 0.06f * sin(after * PI.toFloat()) * (1 - after) else 0f
}

/** Squash on landing: 0 → 1 → settles back to 0 with a spring. */
internal fun dySquash(u: Float): Float {
    if (u <= 0 || u >= 1) return 0f
    return exp(-5 * u) * sin(u * PI.toFloat() * 2.5f)
}

/** The shortest signed turn (deg) from slot a to slot b. */
private fun slotTurn(a: Int, b: Int): Float {
    var d = (b - a) * STEP
    while (d > 180) d -= 360
    while (d < -180) d += 360
    return d
}

internal fun dySlotOf(ch: Char): Int {
    val i = WHEEL.indexOf(ch.uppercaseChar())
    return if (i < 0) WHEEL.length - 1 else i
}

/** The loop's pose at t. */
internal fun dyLoopPose(t: Float, p: DymoPose) {
    val n = WORD.length
    // Wheel: A → O → A → T → S, each spin in the first 0.32 s of its letter's slot; home to A at 7.4.
    var wheel = 0f
    var from = 0
    for (i in 0 until n) {
        val to = dySlotOf(WORD[i])
        val a = LETTER_FROM + i * LETTER_EVERY
        wheel += slotTurn(from, to) * click(dySeg(t, a, a + 0.32f))
        from = to
    }
    wheel += slotTurn(from, 0) * click(dySeg(t, 7.4f, 7.95f))
    p.wheel = -wheel

    var trigger = 0f
    var advance = 0f
    var pressed = 0
    for (i in 0 until n) {
        val a = LETTER_FROM + i * LETTER_EVERY + 0.36f
        trigger = max(trigger, dyEaseOut(dySeg(t, a, a + 0.16f)) * (1 - dyEaseInOut(dySeg(t, a + 0.16f, a + 0.36f))))
        if (t >= a + 0.08f) pressed = i + 1
        p.whiten[i] = dyEaseOut(dySeg(t, a + 0.08f, a + 0.2f))
        p.pop[i] = sin(PI.toFloat() * dySeg(t, a + 0.1f, a + 0.34f))
        advance += dyEaseInOut(dySeg(t, a + 0.3f, a + 0.5f))
    }
    val cut = t >= 4.7f
    p.trigger = trigger
    p.cutter = dyEaseOut(dySeg(t, 4.6f, 4.7f)) * (1 - dyEaseInOut(dySeg(t, 4.7f, 4.9f)))
    p.fly = dyEaseInOut(dySeg(t, 5.0f, 6.0f))
    p.squash = dySquash(dySeg(t, 5.95f, 6.45f))
    p.slide = dyEaseInOut(dySeg(t, 7.4f, 8.4f))
    // After the cut, the stub left in the machine is short; new tape feeds out with the jar swap.
    val feed = dyEaseInOut(dySeg(t, 7.5f, 8.3f))
    p.tape = if (cut) "" else WORD.substring(0, pressed)
    p.advance = if (cut) 0f else advance
    p.lead = if (cut) 4 + 14 * feed else 18f
    p.strip = if (cut && p.slide < 1) WORD else ""
    Tapes[0].rgb.copyInto(p.tapeRGB)
    Tapes[0].rgb.copyInto(p.stripRGB)
}

/** The field's text over the loop: how many letters of WORD. */
internal fun dyLoopText(t: Float): Int {
    if (t >= 7.4f) return 0
    var n = 0
    for (i in WORD.indices) if (t >= LETTER_FROM + i * LETTER_EVERY + 0.44f) n = i + 1
    return n
}

/** The user's tape: what is on it, when each letter was struck, the cut strip. */
internal class DymoUser {
    var text = ""
    val e = ArrayList<Float>()
    var colour = 0
    var colourFrom = 0
    var colourAt = -100f
    var strip = ""
    var stripColour = 0
    var cutAt = -100f

    fun reset() {
        text = ""; e.clear(); colour = 0; colourFrom = 0; colourAt = -100f; strip = ""; stripColour = 0; cutAt = -100f
    }
}

internal fun dyUserPose(u: DymoUser, now: Float, wheel: Float, reduced: Boolean, p: DymoPose) {
    var trigger = 0f
    var advance = 0f
    for (i in u.e.indices) {
        val e = u.e[i]
        trigger = max(trigger, dyEaseOut(dySeg(now, e - 0.08f, e)) * (1 - dyEaseInOut(dySeg(now, e, e + 0.2f))))
        p.whiten[i] = if (reduced) 1f else dyEaseOut(dySeg(now, e, e + 0.12f))
        p.pop[i] = if (reduced) 0f else sin(PI.toFloat() * dySeg(now, e + 0.02f, e + 0.26f))
        advance += if (reduced) 1f else dyEaseInOut(dySeg(now, e + 0.22f, e + 0.42f))
    }
    val c = u.cutAt
    val live = c > 0 && now < c + 3.6f
    val k = dySeg(now, u.colourAt, u.colourAt + 0.25f)
    for (i in 0 until 3) p.tapeRGB[i] = Tapes[u.colourFrom].rgb[i] + (Tapes[u.colour].rgb[i] - Tapes[u.colourFrom].rgb[i]) * k
    Tapes[u.stripColour].rgb.copyInto(p.stripRGB)
    p.wheel = wheel
    p.trigger = trigger
    p.tape = u.text
    p.advance = advance
    p.lead = if (u.text.isNotEmpty() || c < 0) 18f else 4 + 14 * dyEaseInOut(dySeg(now, c + 0.6f, c + 1.3f))
    p.strip = if (live) u.strip else ""
    p.fly = if (reduced) 1f else dyEaseInOut(dySeg(now, c + 0.25f, c + 1.25f))
    p.squash = if (reduced) 0f else dySquash(dySeg(now, c + 1.2f, c + 1.7f))
    p.cutter = dyEaseOut(dySeg(now, c, c + 0.1f)) * (1 - dyEaseInOut(dySeg(now, c + 0.1f, c + 0.3f)))
    p.slide = if (live) dyEaseInOut(dySeg(now, c + 2.6f, c + 3.6f)) else 0f
}

/** The wheel angle (deg) nearest `from` that puts `slot` under the head. */
internal fun dyAngleFor(slot: Int, from: Float): Float {
    val base = -slot * STEP
    val k = ((from - base) / 360).roundToInt()
    return base + k * 360
}

internal fun dySlotAt(angle: Float): Int {
    val n = WHEEL.length
    return ((Math.round(-angle / STEP) % n) + n) % n
}

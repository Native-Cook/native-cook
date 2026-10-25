package com.ngocdevv.nativecook.blocks.inputs.crossstitchinput

import kotlin.math.PI
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

/*
 Cross Stitch Input — the autoPlay script. Every value is a pure function of the loop time t, so
 the frame at t = LOOP is the frame at 0.

   0.0 – 1.2   rest: "Hi" worked in indigo, the needle parked in the cloth at the lower right.
   1.0 – 1.3   the needle comes up to the last stitch; 1.2 – 2.2 it unpicks "Hi" in reverse.
               The field is erased (1.35, 1.5).
   1.95        the Madder chip is chosen; "June" is typed (1.95 – 2.46).
   2.0 – 5.6   the needle works "June", stitch by stitch.
   5.6 – 5.85  a knot round the last hole; 5.85 – 6.35 it goes back to rest.
   6.2 – 7.2   hold.
   7.2 – 8.5   "June" pulls out (7.2 – 7.9) while "Hi" is worked again in indigo (7.6 – 8.5).
   8.5 – 9.0   rest.
*/

internal const val LOOP = 9f
internal const val REST_TEXT = "Hi"
internal const val SECOND_TEXT = "June"
internal const val REST_THREAD = 1
internal const val SECOND_THREAD = 0

/** The field's text over the loop: from t, text. */
internal val TextScript = listOf(
    0f to "Hi", 1.35f to "H", 1.5f to "", 1.95f to "J", 2.12f to "Ju", 2.29f to "Jun", 2.46f to "June",
    7.2f to "Jun", 7.3f to "Ju", 7.4f to "J", 7.5f to "", 7.62f to "H", 7.76f to "Hi",
)

internal fun csSeg(t: Float, a: Float, b: Float) = min(1f, max(0f, (t - a) / (b - a)))

internal fun csEaseInOut(u: Float) = if (u < 0.5f) 4 * u * u * u else 1 - (-2 * u + 2).pow(3) / 2

/** Ease-out with a small overshoot (about 4 %), for the chip ring. */
private fun easeOutBack(u: Float): Float {
    val c1 = 0.9f
    val c3 = c1 + 1
    return 1 + c3 * (u - 1).pow(3) + c1 * (u - 1).pow(2)
}

/** Near-constant stitching pace that still starts and ends softly. */
private fun pace(u: Float) = 0.6f * u + 0.4f * csEaseInOut(u)

internal fun csScriptIndex(t: Float): Int {
    var i = 0
    for (k in TextScript.indices) if (t >= TextScript[k].first) i = k
    return i
}

/** How many of "Hi"'s n stitches are on the cloth (fractional: the one being worked or pulled). */
internal fun csRestPresent(t: Float, n: Int): Float = when {
    t < 1.2f -> n.toFloat()
    t < 2.2f -> n * (1 - csEaseInOut(csSeg(t, 1.2f, 2.2f)))
    t < 7.6f -> 0f
    t < 8.5f -> n * pace(csSeg(t, 7.6f, 8.5f))
    else -> n.toFloat()
}

internal fun csSecondPresent(t: Float, n: Int): Float = when {
    t < 2.0f -> 0f
    t < 5.6f -> n * pace(csSeg(t, 2.0f, 5.6f))
    t < 7.2f -> n.toFloat()
    t < 7.9f -> n * (1 - csEaseInOut(csSeg(t, 7.2f, 7.9f)))
    else -> 0f
}

/** The chip ring's position (thread index, fractional while it moves). */
internal fun csChipPosition(t: Float): Float {
    val a = easeOutBack(csSeg(t, 1.95f, 2.3f))
    val b = easeOutBack(csSeg(t, 7.45f, 7.8f))
    return REST_THREAD + (SECOND_THREAD - REST_THREAD) * a + (REST_THREAD - SECOND_THREAD) * b
}

/** 0 → the counter's total is "Hi"'s, 1 → "June"'s. */
internal fun csTotalBlend(t: Float) = csEaseInOut(csSeg(t, 1.95f, 2.35f)) * (1 - csEaseInOut(csSeg(t, 7.45f, 7.85f)))

/** 0 → indigo, 1 → madder. */
internal fun csThreadBlend(t: Float) = csEaseInOut(csSeg(t, 1.9f, 2.1f)) * (1 - csEaseInOut(csSeg(t, 7.4f, 7.6f)))

/** The needle's script as blend weights: park, follow "Hi", follow "June", knot phase 0..1, lift. */
internal fun csNeedleScript(t: Float, out: FloatArray) {
    val toWork = csEaseInOut(csSeg(t, 1.0f, 1.3f))
    val handOff = csEaseInOut(csSeg(t, 1.95f, 2.2f))
    val home = csEaseInOut(csSeg(t, 5.85f, 6.35f))
    val knot = csSeg(t, 5.6f, 5.85f)
    val park = 1 - toWork + toWork * home
    val work = toWork * (1 - home)
    // Lifted while it travels to and from the work; resting stuck in the cloth otherwise.
    val lift = sin(PI.toFloat() * csSeg(t, 1.0f, 1.3f)) * 0.6f + sin(PI.toFloat() * csSeg(t, 5.85f, 6.35f)) * 0.8f
    out[0] = park; out[1] = work * (1 - handOff); out[2] = work * handOff; out[3] = knot; out[4] = lift
}

package com.ngocdevv.nativecook.blocks.gestures.zipperunlock

import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

/*
 Zipper Unlock — geometry and the autoPlay script, shared by the screen, the shader and the teeth
 (a port of zipperMotion.ts).

 LOOP = 9 s:
   0.0 – 1.6   rest: closed, the pull tab sways gently on its pivot (whole-k sines).
   1.6 – 2.0   grab: the tab lifts toward the viewer (scale 1 → 1.06, its shadow grows).
   2.0 – 4.8   unzip to 100 % with a slow start and a quick finish (p = x²(1.6 − 0.6x)).
   4.8         the tab is let go and swings (damped, 3.2 /s, 7 rad/s).
   4.8 – 5.4   the halves swing open past the screen edges (ease-out cubic); the journal's
               header and cards come in from 5.0, 40 ms apart (0.36 s ease-out).
   6.2 – 6.7   the halves come back (ease-in-out); 6.4 – 6.6 the tab is grabbed again.
   6.6 – 8.4   zipped back up (ease-in-out); 8.4 the tab is let go and swings out by 9.0.
   8.4 – 9.0   rest.
*/

internal const val LOOP = 9f

/** Teeth: spacing along the tape and size (dp). */
internal const val ZIP_TOOTH_PITCH = 7.2f
internal const val ZIP_TOOTH_W = 7.4f
internal const val ZIP_TOOTH_H = 4.2f
/** The V: largest half-gap (share of the width), how far above the slider it reaches it, and the
 * length over which the opening closes again at the top stop (dp). */
private const val GAP_SHARE = 0.21f
private const val GAP_REACH = 300f
private const val GAP_TOP = 64f
/** Half-gap right at the slider's mouth (inside the slider body). */
private const val GAP_MOUTH = 5f
/** Release past this share of the travel and the zipper opens fully. */
internal const val ZIP_OPEN_AT = 0.7f
/** Pull tab: pivot below the slider's centre, length to the ring's centre (dp). */
internal const val ZIP_TAB_PIVOT = 13f
internal const val ZIP_TAB_LEN = 50f

internal const val ZIP_IDLE_BEFORE_RETURN = 2f

/** Top stop, closed slider centre, open slider centre, bottom stop (dp). */
internal class ZipGeometry(val width: Float, val cx: Float, val yTop: Float, val ys0: Float, val ys1: Float, val yEnd: Float, val gapMax: Float)

internal fun zClamp(v: Float, lo: Float, hi: Float) = min(hi, max(lo, v))

private fun seg(t: Float, a: Float, b: Float) = zClamp((t - a) / (b - a), 0f, 1f)

internal fun zSmooth(x: Float): Float {
    val c = zClamp(x, 0f, 1f)
    return c * c * (3 - 2 * c)
}

internal fun zEaseOut(x: Float): Float = 1 - (1 - zClamp(x, 0f, 1f)).pow(3)

internal fun zEaseInOut(x: Float): Float {
    val c = zClamp(x, 0f, 1f)
    return if (c < 0.5f) 4 * c * c * c else 1 - (-2 * c + 2).pow(3) / 2
}

internal fun makeZip(width: Float, height: Float, top: Float, bottom: Float): ZipGeometry {
    val ys0 = top + 40
    val ys1 = height - bottom - 110
    return ZipGeometry(width, width / 2, ys0 - 18, ys0, ys1, ys1 + 22, width * GAP_SHARE)
}

internal fun sliderY(z: ZipGeometry, p: Float) = z.ys0 + (z.ys1 - z.ys0) * p

/** Half-width of the opening at y for slider y `ys` and full-open amount `o`, already eased (same in the shader). */
internal fun gapAt(z: ZipGeometry, y: Float, ys: Float, o: Float): Float {
    var v = 0f
    if (y < ys && y > z.yTop) v = GAP_MOUTH + z.gapMax * zSmooth((ys - y) / GAP_REACH) * zSmooth((y - z.yTop) / GAP_TOP)
    val full = z.width / 2 + 70
    return v + (full - v) * zClamp(o, 0f, 1f)
}

/** The loop's state at t into [out]: zip progress, full open, tab lift, tab angle (rad), seconds since open. */
internal fun zipScript(t: Float, out: FloatArray) {
    // Unzip: slow start, quick finish; zip up: ease-in-out.
    val x = seg(t, 2.0f, 4.8f)
    val down = x * x * (1.6f - 0.6f * x)
    val up = zEaseInOut(seg(t, 6.6f, 8.4f))
    val p = if (t < 6.6f) down else 1 - up
    val o = zEaseOut(seg(t, 4.8f, 5.4f)) * (1 - zEaseInOut(seg(t, 6.2f, 6.7f)))
    val lift = zEaseOut(seg(t, 1.6f, 2.0f)) * (1 - zEaseOut(seg(t, 4.8f, 5.0f))) +
        zEaseOut(seg(t, 6.4f, 6.6f)) * (1 - zEaseOut(seg(t, 8.4f, 8.6f)))
    // Sway at rest (2 and 3 cycles a loop), held still while the tab is held, plus the swing after
    // each let-go, faded out before the loop closes.
    val tau = 2 * PI.toFloat()
    val sway = 0.05f * sin(tau * 2 * t / LOOP) + 0.02f * sin(tau * 3 * t / LOOP)
    var swing = 0f
    if (t > 4.8f) swing += 0.26f * exp(-3.2f * (t - 4.8f)) * sin(7 * (t - 4.8f))
    if (t > 8.4f) swing -= 0.22f * exp(-3.2f * (t - 8.4f)) * sin(7 * (t - 8.4f))
    swing *= 1 - zSmooth(seg(t, 8.7f, 9.0f))
    out[0] = p
    out[1] = o
    out[2] = lift
    out[3] = sway * (1 - lift) + swing
    out[4] = if (t >= 4.8f && t < 6.7f) t - 4.8f else -1f
}

/** Entrance of the journal's i-th element, from seconds since the halves started to open. */
internal fun zipRevealOf(since: Float, i: Int): Float {
    if (since < 0f) return 0f
    return zEaseOut((since - 0.2f - i * 0.04f) / 0.36f)
}

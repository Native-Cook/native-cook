package com.ngocdevv.nativecook.blocks.text.rotatingwordheadline

import kotlin.math.floor
import kotlin.math.pow

/*
 * Rotating Word Headline — the timeline, ported one to one from rotatingWordMotion.ts. Every frame of the autoplay
 * loop comes from posAt(t), a word position (0 = first word, 1 = second, …) that only moves during four short
 * transitions. The letters, the width of the word, the underline, the plan card and the ghost tap all read that one
 * number, so a finger that scrubs pos drives the same effect.
 */

/** Loop length in seconds (React Native LOOP, Swift `static let loop`). */
const val LOOP = 8f

/** Number of words in the cycle (the demo's four). */
internal const val WORDS = 4

/** When each word change starts (s); each one lasts TRANSITION. The last ends exactly at LOOP. */
internal val STARTS = floatArrayOf(1.3f, 3.3f, 5.3f, 7.3f)
internal const val TRANSITION = 0.7f

/** Delay between neighbouring letters, as a fraction of one transition (≈ 49 ms at 0.7 s). */
internal const val STAGGER = 0.07f

/** Letters in the longest word ("wedding"): the stagger is spread over this many. */
internal const val MAX_LETTERS = 7

/** How far a letter travels on its wheel, as a fraction of the font size, and how far it turns. */
internal const val ROLL_RISE = 0.62f
internal const val ROLL_DEG = 72f

/** The width of the word starts a little after the letters, the underline a little after the width. */
internal const val WIDTH_LAG = 0.08f
internal const val LINE_LAG = 0.16f

/** Ghost tap: appears, presses on the word, lifts. Seconds relative to each start. */
internal const val GHOST_IN = -0.42f
internal const val GHOST_PRESS = -0.12f
internal const val GHOST_OUT = 0.34f

/** Touch: idle before the scene eases back to rest, and the length of that return. */
internal const val IDLE = 2f
internal const val RETURN_LEN = 0.9f

internal fun clamp01(x: Float) = if (x < 0f) 0f else if (x > 1f) 1f else x

/** 0 before a, 1 after b, linear between. */
internal fun seg(t: Float, a: Float, b: Float) = clamp01((t - a) / (b - a))

internal fun easeInOutCubic(x: Float): Float =
    if (x < 0.5f) 4f * x * x * x else 1f - (-2f * x + 2f).pow(3) / 2f

internal fun easeOutCubic(x: Float): Float = 1f - (1f - x).pow(3)

internal fun mix(a: Float, b: Float, u: Float) = a + (b - a) * u

/** Whole-number modulo that is never negative. */
internal fun wrapIndex(i: Int, n: Int) = ((i % n) + n) % n

/** Word position at loop time t: 0 at rest, +1 per transition (linear inside a transition; letters ease locally). */
internal fun posAt(t: Float): Float {
    var p = 0f
    for (s in STARTS) p += seg(t, s, s + TRANSITION)
    return p
}

/** Signed distance from word k to the position, wrapped into (−WORDS/2, WORDS/2]. */
internal fun wordDistance(pos: Float, k: Int): Float {
    var d = (((pos - k) % WORDS) + WORDS) % WORDS
    if (d > WORDS / 2f) d -= WORDS
    return d
}

/** One letter's own progress through a transition u (0..1), staggered by its index and eased. */
internal fun letterProgress(u: Float, i: Int): Float {
    val span = 1f - STAGGER * (MAX_LETTERS - 1)
    return easeInOutCubic(clamp01((u - i * STAGGER) / span))
}

/**
 * Where letter i of word k sits on its wheel: −1 rolled out above, 0 in place, +1 waiting below.
 * Outgoing letters roll up and away, incoming ones roll up into place (like an odometer turning forward).
 */
internal fun letterWheel(pos: Float, k: Int, i: Int): Float {
    val d = wordDistance(pos, k)
    if (d <= -1f || d >= 1f) return 1f
    if (d >= 0f) return -letterProgress(d, i)
    return 1f - letterProgress(d + 1f, i)
}

/** Width of the word slot between two measured widths, lagging the letters slightly. */
internal fun morphAmount(pos: Float, lag: Float): Float {
    val u = pos - floor(pos)
    return easeInOutCubic(clamp01((u - lag) / (1f - lag)))
}

/** Ghost finger over the word: opacity and scale (packed). Only during autoplay. */
internal fun ghostOpacity(t: Float): Float = ghost(t, true)

internal fun ghostScale(t: Float): Float = ghost(t, false)

private fun ghost(t: Float, opacity: Boolean): Float {
    var o = 0f
    var s = 1f
    for (a in STARTS) {
        val show = easeOutCubic(seg(t, a + GHOST_IN, a + GHOST_IN + 0.2f)) *
            (1f - easeInOutCubic(seg(t, a + GHOST_OUT - 0.2f, a + GHOST_OUT)))
        if (show > o) {
            o = show
            val press = seg(t, a + GHOST_PRESS, a) * (1f - seg(t, a, a + 0.18f))
            s = 1f - 0.18f * easeInOutCubic(press)
        }
    }
    return if (opacity) o else s
}

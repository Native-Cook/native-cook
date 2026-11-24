package com.ngocdevv.nativecook.blocks.text.streamfadeanswer

import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sin

/*
 Stream Fade Answer — the clocks. Two of them:

 The stream clock S (seconds) is the only thing the answer depends on: every word, row and the
 chip has a start time on it and is a pure function of S. S < 0 is "nothing yet", S ≥ end is the
 finished answer. The self-playing loop and a scrubbing finger both just move S.

 The loop clock t (LOOP = 10 s) maps to S:
   0.00 – 0.70   rest: the finished answer (S = end).
   0.70 – 1.00   the answer fades out (ease-in), the question stays.
   0.90 – 1.50   the thread scrolls back so the question sits where a new turn starts.
   1.25 – 2.60   "thinking": one grey line with a light band sweeping across it twice
                 (0.7 s a sweep), in over 0.2 s and out over the last 0.25 s.
   2.60 – 8.14   the stream: S = t − 2.6, linear. Words arrive 28 ms apart on average; with the
                 demo's copy the heading starts at S = 0, the paragraphs at 0.35 and 1.59, the rows
                 at 2.60, 2.94 and 3.28, the tip at 3.85, the chip at 4.59 and S = 5.54 is the end.
   8.14 – 10.0   rest with the finished answer; t = 10 is t = 0.
*/

/** Seconds. */
internal const val LOOP = 10f

/** Loop phases, in loop seconds. */
internal const val HOLD_END = 0.7f
internal const val FADE_END = 1.0f
internal const val SCROLL_BACK_A = 0.9f
internal const val SCROLL_BACK_B = 1.5f
internal const val THINK_A = 1.25f
internal const val THINK_IN = 0.2f
internal const val STREAM_AT = 2.6f
internal const val THINK_OUT = 0.25f
/** One sweep of the thinking band. */
internal const val THINK_SWEEP = 0.7f

/** A word: 28 ms after the one before on average, 0.42 s to arrive from 6 dp below and a 4 dp blur. */
internal const val WORD_GAP = 0.028f
internal const val WORD_RATE = 1 / WORD_GAP
internal const val WORD_DUR = 0.42f
internal const val WORD_RISE = 6f
internal const val WORD_BLUR = 4f
internal const val BLUR_LEVELS = 8
/** The newest line fades toward the front of the stream over this many dp. */
internal const val FEATHER = 96f

/** Between two blocks of the answer the stream pauses, as a model does at a paragraph. */
internal const val BLOCK_PAUSE = 0.28f
/** Itinerary rows: one every ROW_GAP, each sliding in over ROW_DUR behind its hairline (LINE_DUR). */
internal const val ROW_GAP = 0.34f
internal const val ROW_DUR = 0.46f
internal const val ROW_RISE = 10f
internal const val LINE_DUR = 0.4f
/** The cost chip pops in over CHIP_DUR and counts up over CHIP_COUNT. */
internal const val CHIP_DUR = 0.36f
internal const val CHIP_COUNT = 0.7f

internal fun clamp01(x: Float) = if (x < 0f) 0f else if (x > 1f) 1f else x

internal fun seg(t: Float, a: Float, b: Float) = clamp01((t - a) / (b - a))

internal fun easeOutCubic(x: Float): Float {
    val u = 1 - x
    return 1 - u * u * u
}

internal fun easeOutQuad(x: Float) = 1 - (1 - x) * (1 - x)

internal fun easeInQuad(x: Float) = x * x

internal fun easeInOutCubic(x: Float) = if (x < 0.5f) 4 * x * x * x else 1 - (-2 * x + 2).pow(3) / 2

/** The seeded hash of the React Native block, in doubles so the rhythm agrees. */
internal fun hash(i: Double): Double {
    val s = sin(i * 12.9898 + 78.233) * 43758.5453
    return s - floor(s)
}

/**
 When each of `count` words starts, in seconds from the first. Tokens do not arrive evenly: each
 gap is 0.4–1.6 × the mean (seeded), so the mean stays 1 / rate and the order never changes.
*/
internal fun wordStarts(count: Int, rate: Float, seed: Float): FloatArray {
    val out = FloatArray(count)
    var at = 0.0
    for (i in 0 until count) {
        out[i] = at.toFloat()
        at += (0.4 + 1.2 * hash(i * 1.37 + seed * 17.1 + 3)) / rate
    }
    return out
}

/** S for the self-playing loop at loop time t; `end` is the S of the finished answer. */
internal fun streamAt(t: Float, end: Float) = if (t < FADE_END) end else t - STREAM_AT

/** Opacity of the whole answer in the loop: it fades out before the next turn. */
internal fun answerAlphaAt(t: Float) = if (t < FADE_END) 1 - easeInQuad(seg(t, HOLD_END, FADE_END)) else 1f

/** Opacity of the thinking line in the loop. */
internal fun thinkingAlphaAt(t: Float) = seg(t, THINK_A, THINK_A + THINK_IN) * (1 - seg(t, STREAM_AT - THINK_OUT, STREAM_AT))

/** Where the thinking line's band is in the loop, 0…1 across the line. */
internal fun thinkingBandAt(t: Float): Float {
    val u = (t - THINK_A) / THINK_SWEEP
    return easeInOutCubic(clamp01(u - floor(max(0f, u))))
}

/** Rubber band past the ends of the stream while scrubbing: `over` seconds become fewer. */
internal fun rubber(over: Float) = (over * 0.55f) / (1 + abs(over) / 0.6f)

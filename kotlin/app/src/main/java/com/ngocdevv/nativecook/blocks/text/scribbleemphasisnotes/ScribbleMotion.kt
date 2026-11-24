package com.ngocdevv.nativecook.blocks.text.scribbleemphasisnotes

import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

// Every number the annotations use, from the React Native block's motion.ts.

/** Seconds. One autoPlay loop: clean page, six marks draw in order, hold, lift off in reverse, clean page. */
const val LOOP = 9f

/** The marks, in the order they are drawn. */
internal object Mark {
    const val CIRCLE = 0
    const val HIGHLIGHT = 1
    const val UNDERLINE = 2
    const val BRACKET = 3
    const val NOTE = 4
    const val ARROW = 5
}

internal const val MARK_COUNT = 6

/** When each mark draws (ease-in-out cubic from its start to its end), in seconds. */
internal val DRAW_FROM = floatArrayOf(0.9f, 1.95f, 2.95f, 3.9f, 4.45f, 5.4f)
internal val DRAW_TO = floatArrayOf(1.7f, 2.75f, 3.7f, 4.35f, 5.4f, 5.8f)

/** When each mark lifts off: the stroke retracts from its end back to its start. Last drawn lifts first. */
internal val LIFT_FROM = floatArrayOf(7.75f, 7.6f, 7.45f, 7.3f, 7.12f, 7.0f)
internal val LIFT_TO = floatArrayOf(8.1f, 7.9f, 7.75f, 7.55f, 7.47f, 7.25f)

/** Everything is on the page between these two; a touch runs the clock to HOLD and parks it there. */
internal const val HOLD = 6.4f
internal const val LIFT_START = 7f

/** While a finger has the page, unfinished marks finish at this multiple of the loop's speed. */
internal const val CATCH_UP = 2.5f

/** autoPlay: 2 s after the last touch the finger's marks lift off and the loop carries on into its own lift-off. */
internal const val RESUME_AFTER = 2f

/** The ghost finger that drags the highlighter in the loop. */
internal object Ghost {
    const val APPEAR = 1.62f
    const val SHOWN = 1.82f
    const val LIFT = 2.78f
    const val GONE = 3f
    const val SIZE = 40f
}

/** A finger's own highlight: seconds to swipe on after a tap, and to lift off. */
internal const val USER_DRAW = 0.42f
internal const val USER_LIFT = 0.26f

/** A touch that travels less than this is a tap (toggle the sentence); further and it marks words. */
internal const val TAP_SLOP = 8f

/** At most this many finger-made highlights are kept; the oldest lifts off when one more is made. */
internal const val USER_MARKS = 8

/** Pen. Widths in dp; a stroke swells from TAPER_IN of its width and thins to TAPER_OUT at the flick. */
internal const val PEN_WIDTH = 2.1
internal const val NOTE_WIDTH = 1.9
internal const val TAPER_IN = 0.55
internal const val TAPER_OUT = 0.32
internal const val TAPER_IN_SPAN = 0.12
internal const val TAPER_OUT_SPAN = 0.22
internal const val JITTER = 0.55
internal const val WIDTH_NOISE = 0.14
internal const val STEP = 1.6
internal const val PEN_LIFT = 10.0

/** Circle round the term: 1.1 turns from the upper left, spiralling out so the end overlaps the start. */
internal object CircleSpec {
    const val PAD_X = 10.0
    const val RY = 0.86
    const val TURNS = 1.1
    const val START = -2.5
    const val SPIRAL = 0.09
    const val TILT = -0.07
}

/** Wavy underline: amplitude and wavelength in dp, below the baseline. */
internal object Wave {
    const val DROP = 5.5
    const val AMP = 2.1
    const val LENGTH = 13.0
    const val OVERSHOOT = 3.0
    const val RISE = 1.2
}

/** Highlighter band, as shares of the font size above and below the baseline; multiplied over the text. */
internal object BandSpec {
    const val UP = 0.92f
    const val DOWN = 0.3f
    const val PAD_X = 3f
    const val WOBBLE = 1.1f
    const val STEP = 9f
    const val SLANT = 3f
    const val OPACITY = 0.62f
    const val STREAK = 0.2f
}

/** Margin bracket: a brace, in dp from the text's right edge. */
internal object Brace {
    const val GAP = 7.0
    const val DEPTH = 6.0
    const val NOSE = 6.0
    const val CURL = 9.0
}

/** The note: x-height in dp and its tilt in radians. */
internal object NoteSpec {
    const val X_HEIGHT = 12.5
    const val TILT = -0.1
    const val GAP = 0.28
}

/** Arrow head: two strokes of this length at this angle off the shaft. */
internal object Head {
    const val LENGTH = 8.5
    const val SPREAD = 0.5
}

internal fun clampTo(value: Float, low: Float, high: Float) = min(max(value, low), high)

internal fun clampTo(value: Double, low: Double, high: Double) = min(max(value, low), high)

internal fun mix(from: Float, to: Float, amount: Float) = from + (to - from) * amount

internal fun mix(from: Double, to: Double, amount: Double) = from + (to - from) * amount

/** 0 before a, 1 after b. */
internal fun seg(t: Float, a: Float, b: Float) = clampTo((t - a) / (b - a), 0f, 1f)

internal fun easeInOutCubic(u: Float): Float =
    if (u < 0.5f) 4f * u * u * u else 1f - (-2f * u + 2f).pow(3) / 2f

/** Seeded 0..1. In doubles, so every stack gets the same wobble. */
internal fun hash(n: Double): Double {
    val s = sin(n * 12.9898) * 43758.5453
    return s - floor(s)
}

/**
 * How much of mark [index] is on the page at loop time t: 0 none, 1 all of it, drawn from its start. [cap] freezes
 * the drawing at an earlier time (a finger took the page mid-loop) while the lift-off still runs on t.
 */
internal fun markAt(t: Float, cap: Float, index: Int): Float = max(
    0f,
    easeInOutCubic(seg(min(t, cap), DRAW_FROM[index], DRAW_TO[index])) -
        easeInOutCubic(seg(t, LIFT_FROM[index], LIFT_TO[index])),
)

/** Where the clock parks when a finger takes the page at t: the pen finishes the mark it is on, and stops. */
internal fun parkAt(t: Float): Float {
    for (i in 0 until MARK_COUNT) {
        if (t > DRAW_FROM[i] && t < DRAW_TO[i]) return DRAW_TO[i]
    }
    return t
}

/** Opacity of the ghost finger. */
internal fun ghostAt(t: Float) = seg(t, Ghost.APPEAR, Ghost.SHOWN) * (1f - seg(t, Ghost.LIFT, Ghost.GONE))

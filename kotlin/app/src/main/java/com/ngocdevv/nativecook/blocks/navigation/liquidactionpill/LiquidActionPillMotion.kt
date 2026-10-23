package com.ngocdevv.nativecook.blocks.navigation.liquidactionpill

import androidx.compose.ui.graphics.Path
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

// Everything the bar, the button and the pill do is a function of one number, the pose: 0 with the
// button resting in its bowl, 1 with the pill open. It moves at a constant speed, so the numbers
// below are fractions of DURATION as well as of the way.

/** Seconds of one autoPlay loop. */
internal const val LOOP = 9f
internal const val OPEN_AT = 1f
internal const val CLOSE_AT = 4f
internal const val PROFILE_AT = 6f
internal const val HOME_AT = 7.5f

/** Seconds after a touch before the loop starts again. */
internal const val USER_HOLD = 3f

/** Seconds from the button to the open pill, and back. */
internal const val DURATION = 1f

/** The same under reduced motion, where it is a fade out and a fade in. */
internal const val REDUCED_DURATION = 0.3f

/** Seconds the bowl takes to change over when the pill is turned round half way. */
internal const val TURN = 0.15f

/** Seconds of the cross-fade between the pages. */
internal const val TAB_FADE = 0.25f

internal const val BAR_HEIGHT = 70f
internal const val BAR_RADIUS = 26f
internal const val BAR_MARGIN = 24f

/** The least room under the bar; a larger safe area replaces it. */
internal const val BAR_BOTTOM = 34f

/** From the edges of the bar to the middle of its tabs. */
internal const val TAB_INSET = 54f

/** The bowl in the bar's top edge: half its width, its depth, and how much deeper a press makes it. */
internal const val BOWL_HALF = 54f
internal const val BOWL_DEPTH = 28f
internal const val BOWL_PRESS = 5f

/** The bump the edge throws up after the button lets go of it. */
internal const val PEAK = -7f
internal const val PEAK_HALF = 26f

/** Where the control points of the bowl's two curves sit, as fractions of its half width. */
internal const val BOWL_SHOULDER = 0.66f
internal const val BOWL_FLOOR = 0.46f

/** A quarter circle as a cubic curve. */
internal const val KAPPA = 0.5523f

internal const val BUTTON = 52f

/** The button's middle above the bar's top edge at rest. */
internal const val BUTTON_RAISE = 6f

/** How far a press pushes the button into the bowl, and how far it rises from rest to the pill's place. */
internal const val PRESS_SINK = 6f
internal const val RISE = 34f
internal const val PILL_WIDTH = 232f

/** How much thinner the pill gets while it stretches. */
internal const val SQUEEZE = 4f
internal const val ICON_GAP = 54f
internal const val ICON = 20f
internal const val TAB_ICON = 24f

/** Half an arm of the plus, and the radius of the dot it shrinks into and of the four it splits into. */
internal const val PLUS_ARM = 7f
internal const val SEED = 5f
internal const val DOT = 6f

/** Two dots stay joined until their middles are this far apart. */
internal const val NECK_SNAP = 34f

/** Radians from the line between two dots to where a neck leaves them, when they touch. */
internal const val NECK_SPREAD = 1.3f

// The opening, in fractions of the way.
internal const val SINK_END = 0.2f
internal const val RISE_END = 0.45f
internal const val PEAK_AT = 0.34f
internal const val FLAT_AT = 0.52f
internal const val PLUS_GONE = 0.38f
internal const val SEED_FROM = 0.24f
internal const val SEED_FULL = 0.42f
internal const val STRETCH_FROM = 0.42f
internal const val STRETCH_END = 0.74f
internal const val PAIR_FROM = 0.46f
internal const val PAIR_END = 0.72f
internal const val SPLIT_FROM = 0.54f
internal const val SPLIT_END = 0.8f
internal const val ICONS_FROM = 0.74f
internal const val ICON_STAGGER = 0.04f
internal const val ICON_FOR = 0.14f

/** How far into its fall the button is when the bowl has opened under it again. */
internal const val REFORM = 0.36f

private const val BACK = 1.70158f

internal fun clamp01(t: Float) = min(1f, max(0f, t))

/** How far [p] is from [a] to [b], 0 to 1. */
internal fun span(p: Float, a: Float, b: Float) = clamp01((p - a) / (b - a))

internal fun smooth(t: Float) = t * t * (3f - 2f * t)

internal fun easeOut(t: Float) = 1f - (1f - t) * (1f - t) * (1f - t)

/** Leaves fast, passes the end by a tenth and comes back to it. */
internal fun backOut(t: Float) = 1f + (BACK + 1f) * (t - 1f) * (t - 1f) * (t - 1f) + BACK * (t - 1f) * (t - 1f)

internal fun mix(a: Float, b: Float, t: Float) = a + (b - a) * t

/** The button's middle below its resting place: a press down, then the jump up to the pill's place. */
internal fun lift(p: Float): Float {
    if (p < SINK_END) return PRESS_SINK * smooth(p / SINK_END)
    return PRESS_SINK - (PRESS_SINK + RISE) * backOut(span(p, SINK_END, RISE_END))
}

/**
 * The bowl's depth. [opening] is 1 on the way up, where the edge follows the button, overshoots
 * into a bump and lies flat, and 0 on the way down, where the bowl opens just ahead of the button.
 */
internal fun bowlDepth(p: Float, opening: Float): Float {
    val pressed = BOWL_DEPTH + BOWL_PRESS
    if (p < SINK_END) return BOWL_DEPTH + BOWL_PRESS * smooth(p / SINK_END)
    val up = if (p < PEAK_AT) mix(pressed, PEAK, easeOut(span(p, SINK_END, PEAK_AT))) else mix(PEAK, 0f, smooth(span(p, PEAK_AT, FLAT_AT)))
    val down = pressed * (1f - smooth(span(span(p, SINK_END, RISE_END), 0f, REFORM)))
    return mix(down, up, opening)
}

internal fun bowlHalf(p: Float, opening: Float) =
    mix(BOWL_HALF, mix(BOWL_HALF, PEAK_HALF, smooth(span(p, SINK_END, PEAK_AT))), opening)

internal fun pillWidth(p: Float) = mix(BUTTON, PILL_WIDTH, smooth(span(p, STRETCH_FROM, STRETCH_END)))

internal fun pillHeight(p: Float) = BUTTON - SQUEEZE * sin(PI.toFloat() * span(p, STRETCH_FROM, STRETCH_END))

/** The plus: 1 whole, 0 gone into the dot. */
internal fun plusScale(p: Float) = 1f - smooth(span(p, SINK_END, PLUS_GONE))

internal fun dotRadius(p: Float) = mix(SEED * easeOut(span(p, SEED_FROM, SEED_FULL)), DOT, span(p, PAIR_FROM, SPLIT_FROM))

/** Where dot [index] is, from the middle: one dot parts into two halves and each half into two. */
internal fun dotX(p: Float, index: Int): Float {
    val half = ICON_GAP * smooth(span(p, PAIR_FROM, PAIR_END))
    val quarter = (ICON_GAP / 2f) * smooth(span(p, SPLIT_FROM, SPLIT_END))
    return (if (index < 2) -half else half) + (if (index % 2 == 0) -quarter else quarter)
}

/** How far dot [index] has turned into its icon. */
internal fun iconIn(p: Float, index: Int): Float {
    val from = ICONS_FROM + ICON_STAGGER * index
    return smooth(span(p, from, from + ICON_FOR))
}

/** Writes the bar's outline around the middle of its top edge, with the bowl in that edge, into [path]. */
internal fun barOutline(path: Path, width: Float, depth: Float, half: Float) {
    val w = width / 2f
    val r = BAR_RADIUS
    val k = r * KAPPA
    val h = BAR_HEIGHT
    path.rewind()
    path.moveTo(-w + r, 0f)
    path.lineTo(-half, 0f)
    path.cubicTo(-half * BOWL_SHOULDER, 0f, -half * BOWL_FLOOR, depth, 0f, depth)
    path.cubicTo(half * BOWL_FLOOR, depth, half * BOWL_SHOULDER, 0f, half, 0f)
    path.lineTo(w - r, 0f)
    path.cubicTo(w - r + k, 0f, w, r - k, w, r)
    path.lineTo(w, h - r)
    path.cubicTo(w, h - r + k, w - r + k, h, w - r, h)
    path.lineTo(-w + r, h)
    path.cubicTo(-w + r - k, h, -w, h - r + k, -w, h - r)
    path.lineTo(-w, r)
    path.cubicTo(-w, r - k, -w + r - k, 0f, -w + r, 0f)
    path.close()
}

/** Writes what joins two dots that have not parted far into [path]: necks that thin as they move apart, and snap. */
internal fun necks(path: Path, p: Float) {
    val r = dotRadius(p)
    path.rewind()
    for (i in 0 until 3) {
        val from = dotX(p, i)
        val to = dotX(p, i + 1)
        val gap = to - from
        if (gap <= 0.5f || gap >= NECK_SNAP) continue
        val joined = 1f - gap / NECK_SNAP
        val x = r * cos(NECK_SPREAD * joined)
        val y = r * sin(NECK_SPREAD * joined)
        // The curve's control point, for a waist `joined` times as thick as the neck's ends.
        val c = y * (2f * joined - 1f)
        val middle = (from + to) / 2f
        path.moveTo(from + x, -y)
        path.quadraticTo(middle, -c, to - x, -y)
        path.lineTo(to - x, y)
        path.quadraticTo(middle, c, from + x, y)
        path.close()
    }
}

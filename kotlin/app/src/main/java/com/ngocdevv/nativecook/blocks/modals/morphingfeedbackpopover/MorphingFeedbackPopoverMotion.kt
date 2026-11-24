package com.ngocdevv.nativecook.blocks.modals.morphingfeedbackpopover

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

// Every number the popover's motion uses, ported from the React Native block's motion.ts.

/** Seconds. One autoPlay loop; rest at both ends. */
const val LOOP = 8f

/** Two springs, one per axis (mass 1). Width is the bouncier one and leads; height follows AXIS_STAGGER later. */
internal const val WIDTH_RESPONSE = 0.4f
internal const val WIDTH_DAMPING_RATIO = 0.66f
internal const val HEIGHT_RESPONSE = 0.44f
internal const val HEIGHT_DAMPING_RATIO = 0.78f
internal val WIDTH_STIFFNESS = ((2.0 * PI / WIDTH_RESPONSE) * (2.0 * PI / WIDTH_RESPONSE)).toFloat() // ≈ 246.7
internal val WIDTH_DAMPING = 2f * WIDTH_DAMPING_RATIO * sqrt(WIDTH_STIFFNESS) // ≈ 20.7
internal val HEIGHT_STIFFNESS = ((2.0 * PI / HEIGHT_RESPONSE) * (2.0 * PI / HEIGHT_RESPONSE)).toFloat() // ≈ 203.9
internal val HEIGHT_DAMPING = 2f * HEIGHT_DAMPING_RATIO * sqrt(HEIGHT_STIFFNESS) // ≈ 22.3
/** Opening: width first, height 50 ms later. Closing: height first, width 50 ms later. */
internal const val AXIS_STAGGER = 0.05f
internal const val SUBSTEP = 1f / 240f

/** The label trails the frame by 60 ms (first-order lag) on its way to the placeholder's place. */
internal const val FOLLOW_LABEL = 0.06f
internal const val PLACEHOLDER_ALPHA = 0.42f

/** The popover's content: its own clock, 0 → 1 in CONTENT_IN while open, back in CONTENT_OUT. */
internal const val CONTENT_IN = 0.5f
internal const val CONTENT_OUT = 0.12f
internal val CONTENT_START = floatArrayOf(0.06f, 0.24f, 0.38f)
internal const val CONTENT_SPAN = 0.6f
internal const val CONTENT_RISE = 10f
internal const val CONTENT_GATE_FROM = 0.5f
internal const val CONTENT_GATE_TO = 0.9f

internal const val RADIUS_OPEN = 26f
internal const val SCRIM_ALPHA = 0.14f

/** Drag down: rubber-banded travel squashes the popover toward the pill. */
internal const val DRAG_RUBBER = 220f
internal const val DRAG_SPAN = 260f
internal const val DRAG_WIDTH_SHARE = 0.5f
internal const val DRAG_FOLLOW = 0.3f
internal const val DRAG_UP_RUBBER = 70f
internal const val DRAG_CLOSE_DISTANCE = 90f
internal const val DRAG_CLOSE_VELOCITY = 600f

/** "Thanks!": seconds after Send. The check is two strokes drawn one after the other. */
internal const val THANKS_LABEL_OUT = 0.15f
internal const val THANKS_IN_FROM = 0.22f
internal const val THANKS_IN_TO = 0.42f
internal const val THANKS_SHORT_FROM = 0.3f
internal const val THANKS_SHORT_TO = 0.42f
internal const val THANKS_LONG_FROM = 0.42f
internal const val THANKS_LONG_TO = 0.62f
internal const val THANKS_PULSE_FROM = 0.62f
internal const val THANKS_PULSE_TO = 0.9f
internal const val THANKS_OUT_FROM = 1.5f
internal const val THANKS_OUT_TO = 1.68f
internal const val THANKS_LABEL_IN_FROM = 1.62f
internal const val THANKS_LABEL_IN_TO = 1.85f
internal const val THANKS_END = 1.9f
internal const val THANKS_PULSE = 0.04f

internal const val PRESS_SCALE = 0.96f
internal const val SEND_PRESS = 0.05f
internal const val PRESS_SECONDS = 0.12f
/** A face that is picked pops to this and springs back. */
internal const val FACE_POP = 1.28f
internal const val FACE_POP_SECONDS = 0.11f
internal const val FACE_FILL_SECONDS = 0.14f
internal const val FACE_STIFFNESS = 380f
internal const val FACE_DAMPING = 14f
internal const val SEND_FADE_SECONDS = 0.16f

/** autoPlay: 2 s after the last touch the popover closes and the loop restarts at 0. */
internal const val RESUME_AFTER = 2f

/** The loop's events, in seconds. */
internal const val T_TAP_PILL = 0.8f
internal const val T_TYPE_START = 1.5f
internal const val T_TYPE_END = 3.7f
internal const val T_TAP_FACE = 4.05f
internal const val T_TAP_SEND = 4.8f
/** The face the ghost finger picks (0 = worst … 3 = best). */
internal const val GHOST_FACE = 3

/** A ghost tap: the dot fades in, presses, lifts at `at`, fades out. Offsets from `at`. */
internal const val TAP_APPEAR = -0.4f
internal const val TAP_SHOWN = -0.22f
internal const val TAP_DOWN = -0.15f
internal const val TAP_PRESSED = -0.07f
internal const val TAP_UP = 0.1f
internal const val TAP_GONE = 0.24f

/** What autoPlay types: a first sentence, a breath, then the rest. */
internal const val SAMPLE = "Love the tide view. Could notes open on launch?"
internal const val SAMPLE_PAUSE_AT = 19
internal const val TYPE_FIRST = 0.36f
internal const val TYPE_BREATH = 0.12f

internal fun clamp(value: Float, low: Float, high: Float) = min(max(value, low), high)

internal fun mix(from: Float, to: Float, amount: Float) = from + (to - from) * amount

/** 0 before a, 1 after b. */
internal fun seg(t: Float, a: Float, b: Float) = clamp((t - a) / (b - a), 0f, 1f)

internal fun easeOutCubic(u: Float): Float {
    val k = 1f - u
    return 1f - k * k * k
}

internal fun easeInOutCubic(u: Float) = if (u < 0.5f) 4f * u * u * u else 1f - (-2f * u + 2f).pow(3) / 2f

/** Reanimated's default timing curve. */
internal fun easeInOutQuad(u: Float) = if (u < 0.5f) 2f * u * u else 1f - (2f - 2f * u) * (2f - 2f * u) / 2f

/** Rubber band: follows at first, then resists. */
internal fun rubber(distance: Float, limit: Float) = distance / (1f + distance / limit)

internal fun rubberSlope(distance: Float, limit: Float): Float {
    val k = 1f + distance / limit
    return 1f / (k * k)
}

/** Opacity of the ghost dot for a tap that lifts at `at`. */
internal fun tapGhost(t: Float, at: Float) = seg(t, at + TAP_APPEAR, at + TAP_SHOWN) * (1f - seg(t, at + 0.02f, at + TAP_GONE))

/** 0…1: how far the ghost finger is pressed in for a tap that lifts at `at`. */
internal fun tapPress(t: Float, at: Float) = seg(t, at + TAP_DOWN, at + TAP_PRESSED) * (1f - seg(t, at, at + TAP_UP))

/** The 120 ms press-in from `at` + TAP_DOWN and the 120 ms release from `at`, as the timing animations run them. */
internal fun tapDip(t: Float, at: Float) =
    easeInOutQuad(seg(t, at + TAP_DOWN, at + TAP_DOWN + PRESS_SECONDS)) * (1f - easeInOutQuad(seg(t, at, at + PRESS_SECONDS)))

/** How many characters of the sample are typed at u = 0…1 of the typing time. */
internal fun typedCount(u: Float, total: Int): Int {
    val first = min(SAMPLE_PAUSE_AT, total)
    if (u <= TYPE_FIRST) return (first * (u / TYPE_FIRST)).toInt()
    val rest = seg(u, TYPE_FIRST + TYPE_BREATH, 1f)
    return first + ((total - first) * rest).let { kotlin.math.round(it).toInt() }
}

/**
 * An underdamped spring of mass 1 in closed form. Everything is an offset from the goal:
 * y(τ) = e^(−aτ) (C cos ωτ + S sin ωτ), with C = y0 and S = (v0 + a·y0) / ω.
 */
internal class ClosedSpring(stiffness: Float, damping: Float) {
    private val a = damping / 2.0
    private val w = sqrt(stiffness - a * a)

    fun value(y0: Float, v0: Float, tau: Float): Float {
        val s = (v0 + a * y0) / w
        return (exp(-a * tau) * (y0 * cos(w * tau) + s * sin(w * tau))).toFloat()
    }

    fun velocity(y0: Float, v0: Float, tau: Float): Float {
        val s = (v0 + a * y0) / w
        return (exp(-a * tau) * ((-a * y0 + w * s) * cos(w * tau) + (-a * s - w * y0) * sin(w * tau))).toFloat()
    }

    /** The spring seen through a first-order lag of `lag` seconds, `f0` being the lagged offset at τ = 0. */
    fun lagged(y0: Float, v0: Float, f0: Float, lag: Float, tau: Float): Float {
        val s = (v0 + a * y0) / w
        val k = 1.0 / lag
        val m = k - a
        val den = m * m + w * w
        val p = (m * k * y0 - w * k * s) / den
        val q = (m * k * s + w * k * y0) / den
        return (exp(-a * tau) * (p * cos(w * tau) + q * sin(w * tau)) + (f0 - p) * exp(-k * tau)).toFloat()
    }
}

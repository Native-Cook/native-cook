package com.ngocdevv.nativecook.blocks.modals.morphingcarddialog

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

// Every number the morph uses, ported from the React Native block's motion.ts.

/** Seconds. One autoPlay loop; rest at both ends. */
const val LOOP = 9f

/** The frame's spring: response 0.42 s, damping ratio 0.82 (mass 1). */
internal const val SPRING_RESPONSE = 0.42f
internal const val SPRING_DAMPING_RATIO = 0.82f
internal val STIFFNESS = ((2.0 * PI / SPRING_RESPONSE) * (2.0 * PI / SPRING_RESPONSE)).toFloat() // ≈ 223.8
internal val DAMPING = 2f * SPRING_DAMPING_RATIO * sqrt(STIFFNESS) // ≈ 24.5
/** A live spring is stepped in slices no longer than this. */
internal const val SUBSTEP = 1f / 240f

/** Follow-through: title and "+" trail the frame by 45 ms, the subtitle by 75 ms (first-order lag). */
internal const val FOLLOW_TITLE = 0.045f
internal const val FOLLOW_SUBTITLE = 0.075f

/** The detail content has its own clock: 0 → 1 in CONTENT_IN while open, back in CONTENT_OUT. */
internal const val CONTENT_IN = 0.62f
internal const val CONTENT_OUT = 0.16f
internal val CONTENT_START = floatArrayOf(0.1f, 0.22f, 0.34f)
internal const val CONTENT_SPAN = 0.6f
internal const val CONTENT_RISE = 14f // dp
internal const val CONTENT_BLUR = 8f // dp
internal const val CONTENT_GATE_FROM = 0.62f
internal const val CONTENT_GATE_TO = 0.92f

internal const val RADIUS_CARD = 20f
internal const val RADIUS_DIALOG = 28f

/** Behind the dialog. */
internal const val SCENE_SCALE = 0.96f
internal const val SCENE_DIM = 0.34f

/** Drag: the finger's travel is rubber-banded, then shrinks the dialog toward its card. */
internal const val DRAG_RUBBER = 260f
internal const val DRAG_SPAN = 360f
internal const val DRAG_FOLLOW = 0.55f
internal const val DRAG_SIDE_RUBBER = 120f
internal const val DRAG_UP_RUBBER = 90f
internal const val DRAG_CLOSE_DISTANCE = 120f
internal const val DRAG_CLOSE_VELOCITY = 650f

internal const val PRESS_SCALE = 0.97f
internal const val PRESS_SECONDS = 0.12f

/** autoPlay: 2 s after the last touch the dialog closes and the loop restarts at 0. */
internal const val RESUME_AFTER = 2f

/** The loop's events, in seconds. A and B are the two cards the ghost finger opens. */
internal const val CARD_A = 1
internal const val CARD_B = 4
internal const val T_TAP_A = 1.3f
internal const val T_DRAG_START = 3.55f
internal const val T_DRAG_END = 4.15f
internal const val T_TAP_B = 5.7f
internal const val T_TAP_CLOSE = 7.5f

/** A ghost tap: the dot fades in, presses, lifts at `at`, fades out. Offsets from `at`. */
internal const val TAP_APPEAR = -0.4f
internal const val TAP_SHOWN = -0.22f
internal const val TAP_DOWN = -0.15f
internal const val TAP_PRESSED = -0.07f
internal const val TAP_UP = 0.1f
internal const val TAP_GONE = 0.24f
internal const val GHOST_PULL = 170f
internal const val GHOST_DRIFT = 14f
internal const val GHOST_APPEAR = 0.3f

internal fun clamp(value: Float, low: Float, high: Float) = min(max(value, low), high)

internal fun mix(from: Float, to: Float, amount: Float) = from + (to - from) * amount

/** 0 before a, 1 after b. */
internal fun seg(t: Float, a: Float, b: Float) = clamp((t - a) / (b - a), 0f, 1f)

internal fun easeOutCubic(u: Float): Float {
    val k = 1f - u
    return 1f - k * k * k
}

/** Reanimated's default timing curve. */
internal fun easeInOutQuad(u: Float) = if (u < 0.5f) 2f * u * u else 1f - (2f - 2f * u) * (2f - 2f * u) / 2f

/** Rubber band: follows at first, then resists. */
internal fun rubber(distance: Float, limit: Float) = distance / (1f + distance / limit)

internal fun rubberSlope(distance: Float, limit: Float): Float {
    val k = 1f + distance / limit
    return 1f / (k * k)
}

/** The ghost drag's raw travel (0…1 of GHOST_PULL) at u = 0…1: it speeds up, so the release carries velocity. */
internal fun ghostPull(u: Float) = 0.35f * u + 0.65f * u * u

internal fun ghostPullSpeed(u: Float) = 0.35f + 1.3f * u

/** Opacity of the ghost dot for a tap that lifts at `at`. */
internal fun tapGhost(t: Float, at: Float) = seg(t, at + TAP_APPEAR, at + TAP_SHOWN) * (1f - seg(t, at + 0.02f, at + TAP_GONE))

/** 0…1: how far the ghost finger is pressed in for a tap that lifts at `at`. */
internal fun tapPress(t: Float, at: Float) = seg(t, at + TAP_DOWN, at + TAP_PRESSED) * (1f - seg(t, at, at + TAP_UP))

/** The 120 ms press-in from `at` + TAP_DOWN and the 120 ms release from `at`, as the timing animations run them. */
internal fun tapDip(t: Float, at: Float) =
    easeInOutQuad(seg(t, at + TAP_DOWN, at + TAP_DOWN + PRESS_SECONDS)) * (1f - easeInOutQuad(seg(t, at, at + PRESS_SECONDS)))

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

    /**
     * The spring seen through a first-order lag of `lag` seconds (f' = (y − f) / lag), `f0` being the
     * lagged value's offset at τ = 0: the same damped wave with other weights, plus the lag's own decay.
     */
    fun lagged(y0: Float, v0: Float, f0: Float, lag: Float, tau: Float): Float {
        val s = (v0 + a * y0) / w
        val k = 1.0 / lag
        val m = k - a
        val den = m * m + w * w
        val p = (m * k * y0 - w * k * s) / den
        val q = (m * k * s + w * k * y0) / den
        return (exp(-a * tau) * (p * cos(w * tau) + q * sin(w * tau)) + (f0 - p) * exp(-k * tau)).toFloat()
    }

    /** Seconds after which the offset stays under `eps`. */
    fun settle(y0: Float, v0: Float, eps: Float): Float {
        val amplitude = hypot(y0.toDouble(), (v0 + a * y0) / w)
        return (ln(max(amplitude / eps, 1.0)) / a).toFloat()
    }
}

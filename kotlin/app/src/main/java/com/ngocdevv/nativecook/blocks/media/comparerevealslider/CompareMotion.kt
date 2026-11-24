package com.ngocdevv.nativecook.blocks.media.comparerevealslider

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

// The divider's physics and the 8 s film, as pure functions (compareMotion.ts).
// pos: 0 at the photo's left edge, 1 at its right. Before is left of the divider, after right of it.

const val LOOP = 8f

internal const val REST = 0.5f
internal const val STOP_MIN = 0.07f
internal const val STOP_MAX = 0.93f
private const val RUBBER = 0.085f
private const val PROJECT = 0.17f

private const val SPRING_RESPONSE = 0.5f
private const val SPRING_DAMPING = 0.6f
private val OMEGA = (2f * PI.toFloat()) / SPRING_RESPONSE
private val OMEGA_D = OMEGA * sqrt(1f - SPRING_DAMPING * SPRING_DAMPING)
private val DECAY = SPRING_DAMPING * OMEGA

internal const val HANDLE_W = 46f
internal const val HANDLE_H = 62f
internal const val HANDLE_Y = 0.6f
internal const val PRESS_SQUASH_X = 0.12f
internal const val PRESS_SQUASH_Y = 0.1f
private const val STRETCH_SPEED = 2.6f
internal const val STRETCH_X = 0.2f
internal const val STRETCH_Y = 0.08f
internal const val LEAN = 5f
internal const val SPEED_TAU = 0.07f

private const val GLINT_LAPS = 2f
internal const val GLINT_LEN = 150f
internal const val LINE_W = 2f
internal const val GLOW_W = 26f

private const val LABEL_BEFORE_FROM = 0.12f
private const val LABEL_BEFORE_TO = 0.3f
private const val LABEL_AFTER_FROM = 0.7f
private const val LABEL_AFTER_TO = 0.88f

internal const val EVENING_S = 0.7f
internal const val IDLE_S = 2f
internal const val RETURN_S = 0.7f

internal fun clamp01(x: Float) = if (x < 0f) 0f else if (x > 1f) 1f else x
internal fun clamp(x: Float, lo: Float, hi: Float) = if (x < lo) lo else if (x > hi) hi else x
internal fun seg(t: Float, a: Float, b: Float) = clamp01((t - a) / (b - a))
internal fun smooth(x: Float): Float { val c = clamp01(x); return c * c * (3f - 2f * c) }
internal fun easeInOutCubic(x: Float): Float {
    val c = clamp01(x)
    return if (c < 0.5f) 4f * c * c * c else 1f - (-2f * c + 2f).pow(3) / 2f
}

/** A place past a stop gives way less and less: at most RUBBER beyond it. */
internal fun rubber(p: Float): Float {
    if (p < STOP_MIN) {
        val over = STOP_MIN - p
        return STOP_MIN - over / (1f + over / RUBBER)
    }
    if (p > STOP_MAX) {
        val over = p - STOP_MAX
        return STOP_MAX + over / (1f + over / RUBBER)
    }
    return p
}

internal fun landing(p: Float, velocity: Float) = clamp(p + velocity * PROJECT, STOP_MIN, STOP_MAX)

internal fun springAt(from: Float, to: Float, velocity: Float, s: Float): Float {
    if (s <= 0f) return from
    val a = from - to
    val b = (velocity + DECAY * a) / OMEGA_D
    return to + exp(-DECAY * s) * (a * cos(OMEGA_D * s) + b * sin(OMEGA_D * s))
}

internal fun stretchFor(velocity: Float) = clamp(velocity / STRETCH_SPEED, -1f, 1f)

// ---------------------------------------------------------------- the film

private const val PRESS_1 = 0.55f
private const val DRAG_1_A = 0.7f
private const val DRAG_1_B = 1.9f
private const val LEFT = 0.15f
private const val FLICK_A = 2.2f
private const val FLICK_B = 2.85f
private const val FLICK_TO = 0.6f
private const val FLICK_V = (2f * (FLICK_TO - LEFT)) / (FLICK_B - FLICK_A)
private val RIGHT = landing(FLICK_TO, FLICK_V)
private const val EVENING_AT = 4.2f
private const val PRESS_2 = 4.75f
private const val DRAG_2_A = 4.9f
private const val DRAG_2_B = 6f
private const val BACK = 0.4f
private const val WIGGLE = 0.05f
private const val WIGGLE_TURNS = 2.5f
private const val DAY_AT = 6.15f
private const val SETTLED = 7.05f
private const val PRESS_S = 0.15f

internal fun filmPos(t: Float): Float {
    if (t <= DRAG_1_A) return REST
    if (t <= DRAG_1_B) return REST + (LEFT - REST) * easeInOutCubic(seg(t, DRAG_1_A, DRAG_1_B))
    if (t <= FLICK_A) return LEFT
    if (t <= FLICK_B) {
        val u = seg(t, FLICK_A, FLICK_B)
        return LEFT + (FLICK_TO - LEFT) * u * u
    }
    if (t <= DRAG_2_A) return springAt(FLICK_TO, RIGHT, FLICK_V, t - FLICK_B)
    if (t <= DRAG_2_B) {
        val u = seg(t, DRAG_2_A, DRAG_2_B)
        val tau = 2f * PI.toFloat()
        return RIGHT + (BACK - RIGHT) * easeInOutCubic(u) + WIGGLE * sin(tau * WIGGLE_TURNS * u) * sin(PI.toFloat() * u)
    }
    val p = springAt(BACK, REST, 0f, t - DRAG_2_B)
    return REST + (p - REST) * (1f - smooth(seg(t, SETTLED - 0.35f, SETTLED)))
}

internal fun filmVelocity(t: Float): Float {
    val h = 1f / 240f
    return (filmPos(min(LOOP, t + h)) - filmPos(max(0f, t - h))) / (2f * h)
}

internal fun filmPress(t: Float): Float {
    val first = smooth(seg(t, PRESS_1, PRESS_1 + PRESS_S)) * (1f - smooth(seg(t, FLICK_B, FLICK_B + PRESS_S)))
    val second = smooth(seg(t, PRESS_2, PRESS_2 + PRESS_S)) * (1f - smooth(seg(t, DRAG_2_B, DRAG_2_B + PRESS_S)))
    return max(first, second)
}

internal fun filmGhost(t: Float): Float {
    val first = smooth(seg(t, PRESS_1 - 0.2f, PRESS_1)) * (1f - smooth(seg(t, FLICK_B, FLICK_B + 0.22f)))
    val second = smooth(seg(t, PRESS_2 - 0.2f, PRESS_2)) * (1f - smooth(seg(t, DRAG_2_B, DRAG_2_B + 0.22f)))
    return max(first, second)
}

internal fun filmEvening(t: Float): Float =
    easeInOutCubic(seg(t, EVENING_AT, EVENING_AT + EVENING_S)) * (1f - easeInOutCubic(seg(t, DAY_AT, DAY_AT + EVENING_S)))

internal fun glintAt(seconds: Float): Float {
    val lap = (seconds * GLINT_LAPS) / LOOP
    return lap - floor(lap)
}

internal fun glintGain(stretch: Float) = 0.55f + 0.45f * min(1f, abs(stretch) * 1.6f)
internal fun labelBefore(pos: Float) = smooth(seg(pos, LABEL_BEFORE_FROM, LABEL_BEFORE_TO))
internal fun labelAfter(pos: Float) = 1f - smooth(seg(pos, LABEL_AFTER_FROM, LABEL_AFTER_TO))

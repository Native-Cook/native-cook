package com.ngocdevv.nativecook.blocks.gestures.chooseyourball

import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.sqrt

// Geometry and timing of the picker (ballPickerGeometry.ts). Lengths are in dp.

/** Finger distance that moves the selection by one ball. */
internal const val ITEM_STRIDE = 128f

/** One gesture moves the loop by at most this many balls; past it the drag resists. */
internal const val PAN_REACH = 1f

/** The resistance past `PAN_REACH`: the excess is `over / (1 + over * PAN_RESIST)`. */
internal const val PAN_RESIST = 6f

/** How much of the release velocity carries into the projected landing slot, in seconds. */
internal const val FLING_PROJECTION_S = 0.18f

/** The ball's centre, as a fraction of the screen's height. */
internal const val PICKER_BALL_CENTER_FRACTION = 0.47f

private const val PICKER_SOURCE_WIDTH_FRACTION = 1.06f
private const val PICKER_SOURCE_MAX = 460f

/** Side of the square 3D stage; the ball itself fills about three quarters of it. */
internal fun pickerSourceBoundarySize(width: Float, compact: Boolean): Float =
    min(PICKER_SOURCE_MAX, width * PICKER_SOURCE_WIDTH_FRACTION) * (if (compact) 0.86f else 1f)

/**
 * Reanimated describes a spring by damping c, mass m and stiffness k; Compose by a damping ratio
 * and a stiffness for a unit mass. They are the same spring when ratio = c / (2·√(k·m)) and
 * stiffness = k / m.
 */
internal fun reanimatedSpring(
    damping: Float,
    mass: Float,
    stiffness: Float,
    visibilityThreshold: Float,
): SpringSpec<Float> = spring(
    dampingRatio = damping / (2f * sqrt(stiffness * mass)),
    stiffness = stiffness / mass,
    visibilityThreshold = visibilityThreshold,
)

/**
 * The snap: damping 18, mass 1, stiffness 130 (ratio 0.79, about 0.45 s, one small overshoot).
 * A slot is a whole turn of the shell, so it rests only within a 30th of a degree.
 */
internal val SNAP_SPRING = reanimatedSpring(damping = 18f, mass = 1f, stiffness = 130f, visibilityThreshold = 0.0001f)

/** JavaScript's Math.round: halves go up, also below zero. */
internal fun roundSlot(value: Float): Int = floor(value + 0.5f).toInt()

/** The catalog is a loop: `offset` is unbounded and every reader wraps it. */
internal fun wrapIndex(slot: Int, total: Int): Int =
    if (total <= 0) 0 else ((slot % total) + total) % total

/** Signed distance from `offset` to `index` in slots, the short way round: [-total/2, total/2). */
internal fun cyclicDelta(index: Float, offset: Float, total: Int): Float {
    if (total <= 0) return 0f
    val half = total / 2f
    var delta = (((index - offset) % total) + total) % total
    if (delta >= half) delta -= total
    return delta
}

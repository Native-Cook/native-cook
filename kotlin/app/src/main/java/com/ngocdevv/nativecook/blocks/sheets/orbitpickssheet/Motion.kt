package com.ngocdevv.nativecook.blocks.sheets.orbitpickssheet

import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

// Seconds since the banner was tapped. The sheet rises first, so the loader starts a little in.
internal const val SHEET_RISE = 0.45f
internal const val BADGE_IN = SHEET_RISE
internal const val BADGE_POP = 0.7f
internal const val RING_IN = 0.6f
internal const val RING_DRAW = 1f
internal const val CAPTION_IN = 0.9f
internal const val CAPTION_OUT = 3.9f
internal const val DISC_IN = 1.3f
internal const val DISC_STAGGER = 0.05f
internal const val DISC_FLY = 0.9f
internal const val SUCK_AT = 4f
internal const val SUCK_FOR = 0.6f
internal const val SUCK_STAGGER = 0.015f
internal const val RING_OUT = 4f
internal const val BADGE_UP = 4.3f
internal const val BADGE_UP_FOR = 0.7f
internal const val RESULT_IN = 4.9f
internal const val RESULT_FOR = 0.7f
/** The clock stops here; everything after it is a still result. */
internal const val END = 5.7f
/** Seconds one disc takes for a full turn at speed 1. */
internal const val PERIOD = 3f

internal fun lerp(from: Float, to: Float, t: Float) = from + (to - from) * t

internal fun ramp(value: Float, from: Float, to: Float) = min(1f, max(0f, (value - from) / (to - from)))

internal fun easeOut(t: Float) = 1f - (1f - t) * (1f - t) * (1f - t)

internal fun easeIn(t: Float) = t * t * t

/** 0 → 1 with a small overshoot, for a pop. */
internal fun pop(t: Float): Float {
    val c = 1.70158f
    return 1f + (c + 1f) * (t - 1f).pow(3) + c * (t - 1f).pow(2)
}

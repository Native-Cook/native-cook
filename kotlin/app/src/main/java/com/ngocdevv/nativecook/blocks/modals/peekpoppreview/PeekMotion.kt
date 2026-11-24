package com.ngocdevv.nativecook.blocks.modals.peekpoppreview

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

// Every number the peek and the pop use, from the React Native block's motion.ts.
//
// State (all 0..1): `press` (the finger is down on a row), `peek` (row to preview card), `sink` (the preview pulled
// back toward its row; the card is drawn at peek - sink), `pop` (preview card to full page), `reveal` (the excerpt
// and the quick actions), `scroll` (dp the open page is scrolled).

/** Seconds. One autoPlay loop; rest at both ends. */
const val LOOP = 10.5f

/** Springs: response in seconds and damping ratio (mass 1). */
internal data class PeekSpring(val response: Float, val ratio: Float)

internal val PEEK_SPRING = PeekSpring(0.46f, 0.78f)
internal val POP_SPRING = PeekSpring(0.5f, 0.84f)
internal val CLOSE_SPRING = PeekSpring(0.44f, 0.88f)

/** Long press: the row swells for this long, then the peek begins. */
internal const val PRESS = 0.32f
internal const val PRESS_SCALE = 0.03f
internal const val PRESS_RELEASE_FROM = 0.1f
internal const val PRESS_RELEASE_TO = 0.4f

/** Finger travel that maps to the whole pop (swipe up while peeking) and the whole way back (pull down). */
internal const val POP_UP_SPAN = 240f
internal const val POP_DOWN_SPAN = 300f
internal const val POP_DOWN_RUBBER = 500f
internal const val SINK_SPAN = 280f
internal const val SINK_RUBBER = 200f

/** Release rules. */
internal const val POP_COMMIT = 0.4f
internal const val POP_VELOCITY = 500f
internal const val SINK_CLOSE = 40f
internal const val SINK_VELOCITY = 500f
internal const val DISMISS_PULL = 110f
internal const val DISMISS_VELOCITY = 600f

/** Closing from the open page: the pop leads and the peek follows this much later, so the card shape shows. */
internal const val CLOSE_FOLLOW = 0.07f
internal const val SCROLL_RUBBER = 120f

/** Excerpt and actions: `reveal` runs 0 to 1 in REVEAL_IN while peeking, back in REVEAL_OUT. */
internal const val REVEAL_IN = 0.6f
internal const val REVEAL_OUT = 0.18f
internal const val REVEAL_DELAY = 0.1f
internal const val EXCERPT_AT = 0.05f
internal val ACTION_AT = floatArrayOf(0.28f, 0.4f, 0.52f)
internal const val REVEAL_SPAN = 0.48f
internal const val EXCERPT_BLUR = 8f
internal const val ACTION_RISE = 12f
internal const val PANEL_RISE = 16f
internal const val PANEL_SCALE = 0.94f

/** Corner radii. */
internal const val RADIUS_ROW = 18f
internal const val RADIUS_CARD = 28f

/** Behind the preview. */
internal const val SCENE_SCALE = 0.955f
internal const val SCENE_DIM = 0.3f

/** The page's nav buttons settle in over this part of the pop. */
internal const val NAV_AT_FROM = 0.55f
internal const val NAV_AT_TO = 1f
internal const val NAV_DROP = 14f

/** autoPlay: 2 s after the last touch everything closes and the loop restarts at 0. */
internal const val RESUME_AFTER = 2f
internal const val REDUCED_SECONDS = 0.2f

/** The rows the ghost finger presses. */
internal const val ROW_A = 1
internal const val ROW_B = 3

/** The loop's events, in seconds. */
internal object T {
    const val appearA = 0.5f
    const val downA = 0.85f
    const val swipeA = 2.55f
    const val releaseA = 2.95f
    const val scrollStart = 4.0f
    const val scrollEnd = 4.75f
    const val pullStart = 5.6f
    const val releaseD = 6.3f
    const val switchRow = 7.0f
    const val appearB = 7.05f
    const val downB = 7.4f
    const val pullB = 8.55f
    const val releaseB = 8.8f
}

/** Ghost travel, in dp. */
internal const val GHOST_SWIPE_UP = 150f
internal const val GHOST_SCROLL = 190f
internal const val GHOST_DISMISS = 330f
internal const val GHOST_SINK = 46f
internal const val GHOST_SIZE = 46f
internal const val GHOST_FADE = 0.18f
internal const val GHOST_PRESS = 0.16f

internal fun clampTo(value: Float, low: Float, high: Float) = min(max(value, low), high)

internal fun mix(from: Float, to: Float, amount: Float) = from + (to - from) * amount

/** Row, preview card, full page. */
internal fun mix3(row: Float, card: Float, full: Float, peek: Float, pop: Float) = mix(mix(row, card, peek), full, pop)

/** 0 before a, 1 after b. */
internal fun seg(t: Float, a: Float, b: Float) = clampTo((t - a) / (b - a), 0f, 1f)

internal fun easeOutCubic(u: Float): Float {
    val k = 1f - u
    return 1f - k * k * k
}

internal fun easeInOutCubic(u: Float): Float =
    if (u < 0.5f) 4f * u * u * u else 1f - (-2f * u + 2f).pow(3) / 2f

/** Reanimated's default withTiming curve. */
internal fun easeInOutQuad(u: Float): Float =
    if (u < 0.5f) 2f * u * u else 1f - (-2f * u + 2f).pow(2) / 2f

/** Rubber band: follows at first, then resists. */
internal fun rubber(distance: Float, limit: Float) = distance / (1f + distance / limit)

/** d(rubber)/d(distance). */
internal fun rubberSlope(distance: Float, limit: Float): Float {
    val k = 1f + distance / limit
    return 1f / (k * k)
}

/** An under-damped spring in closed form: where it is `time` after leaving `from` at `velocity` for `to`. */
internal fun springAt(time: Float, from: Float, to: Float, velocity: Float, spring: PeekSpring): Float {
    if (time <= 0f) return from
    val w = 2.0 * PI / spring.response
    val ratio = spring.ratio.toDouble()
    val wd = w * sqrt(1.0 - ratio * ratio)
    val a = (to - from).toDouble()
    val b = (ratio * w * a - velocity) / wd
    return (to - exp(-ratio * w * time) * (a * cos(wd * time) + b * sin(wd * time))).toFloat()
}

/** The ghost finger's travel 0..1 over a drag: it speeds up, so the release carries velocity. */
internal fun ghostPull(u: Float) = 0.35f * u + 0.65f * u * u

internal fun ghostPullSpeed(u: Float) = 0.35f + 1.3f * u

// ---- The loop, as functions of its time t. The finger drives the same five values with real springs. ----

/** Which row is out at t. */
internal fun scriptRow(t: Float) = if (t < T.switchRow) ROW_A else ROW_B

private fun pressFrom(t: Float, down: Float): Float {
    val start = down + PRESS
    return easeOutCubic(seg(t, down, start)) * (1f - seg(t, start + PRESS_RELEASE_FROM, start + PRESS_RELEASE_TO))
}

internal fun scriptPress(t: Float) = if (t < T.switchRow) pressFrom(t, T.downA) else pressFrom(t, T.downB)

/** Banded pull on the open page at the moment the ghost lets go. */
private fun dismissPull(t: Float): Float {
    val u = seg(t, T.pullStart, T.releaseD)
    return max(0f, GHOST_DISMISS * ghostPull(u) - GHOST_SCROLL)
}

internal fun scriptPop(t: Float): Float {
    if (t < T.swipeA) return 0f
    if (t < T.releaseA) return GHOST_SWIPE_UP * ghostPull(seg(t, T.swipeA, T.releaseA)) / POP_UP_SPAN
    if (t < T.pullStart) {
        val from = GHOST_SWIPE_UP / POP_UP_SPAN
        val velocity = GHOST_SWIPE_UP * ghostPullSpeed(1f) / (T.releaseA - T.swipeA) / POP_UP_SPAN
        val value = springAt(t - T.releaseA, from, 1f, velocity, POP_SPRING)
        // The spring's last thousandth is pinned, so the page is exactly open before the next touch.
        return mix(value, 1f, seg(t, T.scrollStart - 0.4f, T.scrollStart - 0.1f))
    }
    if (t < T.releaseD) return 1f - rubber(dismissPull(t), POP_DOWN_RUBBER) / POP_DOWN_SPAN
    val pull = GHOST_DISMISS - GHOST_SCROLL
    val from = 1f - rubber(pull, POP_DOWN_RUBBER) / POP_DOWN_SPAN
    val speed = GHOST_DISMISS * ghostPullSpeed(1f) / (T.releaseD - T.pullStart)
    val velocity = -speed * rubberSlope(pull, POP_DOWN_RUBBER) / POP_DOWN_SPAN
    return springAt(t - T.releaseD, from, 0f, velocity, CLOSE_SPRING) * (1f - seg(t, T.switchRow - 0.15f, T.switchRow))
}

/** Peek minus sink: how far the row has become the preview card. */
internal fun scriptPeek(t: Float): Float {
    if (t < T.switchRow) {
        val start = T.downA + PRESS
        if (t < start) return 0f
        val close = T.releaseD + CLOSE_FOLLOW
        if (t < close) {
            return mix(springAt(t - start, 0f, 1f, 0f, PEEK_SPRING), 1f, seg(t, T.swipeA - 0.4f, T.swipeA - 0.1f))
        }
        return springAt(t - close, 1f, 0f, 0f, CLOSE_SPRING) * (1f - seg(t, T.switchRow - 0.15f, T.switchRow))
    }
    val start = T.downB + PRESS
    if (t < start) return 0f
    if (t < T.pullB) return mix(springAt(t - start, 0f, 1f, 0f, PEEK_SPRING), 1f, seg(t, T.pullB - 0.4f, T.pullB - 0.1f))
    if (t < T.releaseB) return 1f - rubber(GHOST_SINK * ghostPull(seg(t, T.pullB, T.releaseB)), SINK_RUBBER) / SINK_SPAN
    val from = 1f - rubber(GHOST_SINK, SINK_RUBBER) / SINK_SPAN
    val speed = GHOST_SINK * ghostPullSpeed(1f) / (T.releaseB - T.pullB)
    val velocity = -speed * rubberSlope(GHOST_SINK, SINK_RUBBER) / SINK_SPAN
    return springAt(t - T.releaseB, from, 0f, velocity, CLOSE_SPRING) * (1f - seg(t, LOOP - 1.05f, LOOP - 0.9f))
}

internal fun scriptReveal(t: Float): Float {
    if (t < T.switchRow) {
        val on = T.downA + PRESS + REVEAL_DELAY
        return seg(t, on, on + REVEAL_IN) * (1f - seg(t, T.releaseD, T.releaseD + REVEAL_OUT))
    }
    val on = T.downB + PRESS + REVEAL_DELAY
    return seg(t, on, on + REVEAL_IN) * (1f - seg(t, T.releaseB, T.releaseB + REVEAL_OUT))
}

/** Dp the open page is scrolled. */
internal fun scriptScroll(t: Float): Float {
    if (t < T.scrollStart) return 0f
    if (t < T.pullStart) return GHOST_SCROLL * easeInOutCubic(seg(t, T.scrollStart, T.scrollEnd))
    return max(0f, GHOST_SCROLL - GHOST_DISMISS * ghostPull(seg(t, T.pullStart, T.releaseD)))
}

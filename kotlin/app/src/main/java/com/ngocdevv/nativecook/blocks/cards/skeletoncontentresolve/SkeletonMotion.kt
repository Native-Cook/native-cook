package com.ngocdevv.nativecook.blocks.cards.skeletoncontentresolve

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

// The skeleton's timing, the pull and the 10 s film, as pure functions (skeletonMotion.ts).
// Two clocks drive the screen: sinceHide (seconds since the content became bars) and sinceShow
// (seconds since it started to resolve, negative while loading). k: 0 = bar, 1 = content.

const val LOOP = 10f

internal const val STAGGER = 0.06f
internal const val RESOLVE_S = 0.62f
internal const val HIDE_STAGGER = 0.02f
internal const val HIDE_S = 0.3f
internal const val MAX_ORDER = 11f
internal const val RESOLVE_TOTAL = MAX_ORDER * STAGGER + RESOLVE_S
internal const val HIDE_TOTAL = MAX_ORDER * HIDE_STAGGER + HIDE_S
internal const val SETTLED = RESOLVE_TOTAL + 0.3f

private const val MORPH_TO = 0.62f
private const val TEXT_FROM = 0.3f
private const val TEXT_TO = 0.85f
private const val DISSOLVE_FROM = 0.5f
private const val BLOOM = 0.22f
private const val WIPE_FROM = 0.05f
private const val WIPE_TO = 0.92f
internal const val WIPE_SOFT = 0.35f

private const val SHIMMER_S = 1.15f
internal const val BAND = 150f
internal const val SLANT = 0.32f

internal const val THRESHOLD = 62f
internal const val HOLD = 46f
private const val PULL_GAIN = 0.55f
private const val PULL_SOFT = 480f
internal const val LOAD_S = 1.6f
private const val SPIN_S = 0.9f
internal const val TICKS = 8

private const val OFFSET_RESPONSE = 0.42f
private const val OFFSET_DAMPING = 0.8f
private val OMEGA = (2f * PI.toFloat()) / OFFSET_RESPONSE
private val OMEGA_D = OMEGA * sqrt(1f - OFFSET_DAMPING * OFFSET_DAMPING)
private val DECAY = OFFSET_DAMPING * OMEGA

internal const val IDLE_S = 2f

internal fun clamp01(x: Float) = if (x < 0f) 0f else if (x > 1f) 1f else x
internal fun seg(t: Float, a: Float, b: Float) = clamp01((t - a) / (b - a))
internal fun smooth(x: Float): Float { val c = clamp01(x); return c * c * (3f - 2f * c) }
internal fun easeOutCubic(x: Float): Float { val c = 1f - clamp01(x); return 1f - c * c * c }
internal fun easeInOutCubic(x: Float): Float {
    val c = clamp01(x)
    return if (c < 0.5f) 4f * c * c * c else 1f - (-2f * c + 2f).pow(3) / 2f
}

private const val SETTLE_A = 5.2f
private const val SETTLE_B = 7.5f
private val SETTLE_END = 1f - exp(-SETTLE_A) * (cos(SETTLE_B) + (SETTLE_A / SETTLE_B) * sin(SETTLE_B))

/** 0 to 1 with one overshoot, exactly 1 at u = 1. */
internal fun settle(u: Float): Float {
    val c = clamp01(u)
    val s = 1f - exp(-SETTLE_A * c) * (cos(SETTLE_B * c) + (SETTLE_A / SETTLE_B) * sin(SETTLE_B * c))
    return s + (1f - SETTLE_END) * c
}

internal fun kAt(order: Float, sinceHide: Float, sinceShow: Float): Float {
    if (sinceShow >= 0f) return seg(sinceShow, order * STAGGER, order * STAGGER + RESOLVE_S)
    if (sinceHide >= 0f) return 1f - seg(sinceHide, order * HIDE_STAGGER, order * HIDE_STAGGER + HIDE_S)
    return 1f
}

internal fun barMorph(k: Float) = settle(k / MORPH_TO)
internal fun contentAlpha(k: Float) = smooth(seg(k, TEXT_FROM, TEXT_TO))
internal fun barAlpha(k: Float) = 1f - smooth(seg(k, DISSOLVE_FROM, 1f))
internal fun barBloom(k: Float) = 1f + BLOOM * smooth(seg(k, DISSOLVE_FROM, 1f))

internal fun shimmerAlpha(k: Float, sinceHide: Float): Float {
    if (sinceHide < 0f) return 0f
    return smooth(seg(sinceHide, 0.12f, 0.42f)) * (1f - seg(k, 0f, 0.25f))
}

internal fun wipe(k: Float) = easeInOutCubic(seg(k, WIPE_FROM, WIPE_TO))

internal fun shimmerX(sinceHide: Float, span: Float): Float {
    if (sinceHide < 0f) return -BAND
    val lap = sinceHide / SHIMMER_S
    return -BAND + (lap - floor(lap)) * (span + 2f * BAND)
}

internal fun pullFor(dy: Float): Float = if (dy <= 0f) 0f else (dy * PULL_GAIN) / (1f + dy / PULL_SOFT)

internal fun offsetSpring(from: Float, to: Float, s: Float): Float {
    if (s <= 0f) return from
    val a = from - to
    val b = (DECAY * a) / OMEGA_D
    return to + exp(-DECAY * s) * (a * cos(OMEGA_D * s) + b * sin(OMEGA_D * s))
}

internal fun tickArming(index: Int, reveal: Float) = 0.85f * smooth(seg(reveal * TICKS, index.toFloat(), index + 1f))

internal fun tickSpinning(index: Int, sinceHide: Float): Float {
    val head = (maxOf(0f, sinceHide) / SPIN_S) * TICKS
    val behind = (((head - index) % TICKS) + TICKS) % TICKS
    return 1f - 0.78f * (behind / TICKS)
}

// ---------------------------------------------------------------- the film

private const val PULL_S = 0.9f
private const val FINGER_DY = 170f
private const val RELEASE_1 = 1.7f
private const val RESOLVE_1 = 3.3f
private const val RELEASE_2 = 6.4f
private const val RESOLVE_2 = 7.75f
internal const val SWAP_AFTER = HIDE_TOTAL + 0.04f
private val FILM_PULL = pullFor(FINGER_DY)

internal fun filmRelease(t: Float) = if (t >= RELEASE_2) RELEASE_2 else if (t >= RELEASE_1) RELEASE_1 else -1f
internal fun filmResolve(t: Float) = if (t >= RELEASE_2) RESOLVE_2 else RESOLVE_1
internal fun filmTarget(t: Float) = if (t >= RELEASE_2) 0 else if (t >= RELEASE_1) 1 else 0

internal fun filmSinceHide(t: Float): Float {
    val r = filmRelease(t)
    return if (r < 0f) -1f else t - r
}

internal fun filmSinceShow(t: Float): Float {
    val r = filmRelease(t)
    if (r < 0f) return SETTLED
    val s = filmResolve(t)
    return if (t >= s) min(SETTLED, t - s) else -1f
}

internal fun filmData(t: Float): Int {
    val r = filmRelease(t)
    if (r < 0f) return 0
    val target = filmTarget(t)
    return if (t - r >= SWAP_AFTER) target else 1 - target
}

internal fun filmFinger(t: Float): Float {
    val a = if (t < RELEASE_1 + 0.5f) RELEASE_1 - PULL_S else RELEASE_2 - PULL_S
    return FINGER_DY * easeOutCubic(seg(t, a, a + PULL_S))
}

internal fun filmGhost(t: Float): Float {
    val a1 = RELEASE_1 - PULL_S
    val a2 = RELEASE_2 - PULL_S
    val first = smooth(seg(t, a1 - 0.22f, a1)) * (1f - smooth(seg(t, RELEASE_1, RELEASE_1 + 0.22f)))
    val second = smooth(seg(t, a2 - 0.22f, a2)) * (1f - smooth(seg(t, RELEASE_2, RELEASE_2 + 0.22f)))
    return maxOf(first, second)
}

private fun cycleOffset(t: Float, release: Float, resolve: Float): Float {
    if (t < release - PULL_S) return 0f
    if (t < release) return pullFor(FINGER_DY * easeOutCubic(seg(t, release - PULL_S, release)))
    if (t < resolve) return offsetSpring(FILM_PULL, HOLD, t - release)
    return offsetSpring(HOLD, 0f, t - resolve) * (1f - smooth(seg(t, resolve + 0.7f, resolve + 0.95f)))
}

internal fun filmOffset(t: Float): Float =
    if (t < RELEASE_2 - PULL_S) cycleOffset(t, RELEASE_1, RESOLVE_1) else cycleOffset(t, RELEASE_2, RESOLVE_2)

internal fun filmReveal(t: Float): Float {
    val pulling = (t >= RELEASE_1 - PULL_S && t < RELEASE_1) || (t >= RELEASE_2 - PULL_S && t < RELEASE_2)
    return if (pulling) clamp01(filmOffset(t) / THRESHOLD) else 0f
}

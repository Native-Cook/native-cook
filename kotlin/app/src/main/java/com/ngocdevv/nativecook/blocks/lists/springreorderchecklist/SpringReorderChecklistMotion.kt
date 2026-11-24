package com.ngocdevv.nativecook.blocks.lists.springreorderchecklist

import kotlin.math.PI
import kotlin.math.cbrt
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

// All the motion of the Spring Reorder Checklist, one to one with React Native's springReorderMotion.ts. Positions are
// in slots (one row = 1) unless named otherwise; the film is a pure function of loop time, so the neighbours' springs
// are closed-form from the moment the held row passes them.

const val LOOP = 9.0

internal const val MODE_AUTO = 0
internal const val MODE_MANUAL = 1
internal const val MODE_RETURN = 2

internal const val IDLE_S = 2.0
internal const val RETURN_S = 0.75

internal const val ROW_H = 64f
internal const val LONG_PRESS_MS = 260L
internal const val LIFT_SCALE = 1.035f
internal const val EDGE_ZONE = 88f // dp from the list's edge where auto-scroll starts
internal const val EDGE_SPEED = 620f // dp/s at the very edge

internal const val FILM_FROM = 1
internal const val FILM_TO = 9
internal const val DROP_PAST = 0.18 // the film's finger lets go this far past the slot

/** Reanimated's `withSpring({ mass: 1, stiffness, damping })`. */
internal class SpringSpec(val stiffness: Float, val damping: Float)

// response 0.34 s, damping ratio 0.74
internal val PART_SPRING = SpringSpec(341.5f, 27.35f)
// response 0.42 s, damping ratio 0.56: the small overshoot of a dropped row
internal val DROP_SPRING = SpringSpec(223.8f, 16.76f)
internal val LIFT_SPRING = SpringSpec(420f, 26f)

internal object T {
    const val pressA = 0.6
    const val dragA = 0.85
    const val dropA = 2.55
    const val tapA = 3.25
    const val drawA = 3.35
    const val drawAEnd = 3.85
    const val pressB = 4.4
    const val dragB = 4.65
    const val dropB = 6.35
    const val tapB = 7.0
    const val drawB = 7.1
    const val drawBEnd = 7.6
}

internal fun clamp(v: Double, a: Double, b: Double) = minOf(maxOf(v, a), b)
internal fun clampF(v: Float, a: Float, b: Float) = minOf(maxOf(v, a), b)
internal fun seg(t: Double, a: Double, b: Double) = clamp((t - a) / (b - a), 0.0, 1.0)
internal fun mix(a: Double, b: Double, k: Double) = a + (b - a) * k
internal fun mixF(a: Float, b: Float, k: Float) = a + (b - a) * k
internal fun smooth(k: Double) = k * k * (3 - 2 * k)
internal fun easeOutCubic(k: Double) = 1 - (1 - k).pow(3)
internal fun easeInOutCubic(k: Double) = if (k < 0.5) 4 * k * k * k else 1 - (-2 * k + 2).pow(3) / 2

private fun easeInOutCubicInverse(p: Double) = if (p < 0.5) cbrt(p / 4) else 1 - cbrt(2 * (1 - p)) / 2

internal fun win(t: Double, a: Double, b: Double, c: Double, d: Double) =
    smooth(seg(t, a, b)) * (1 - smooth(seg(t, c, d)))

/** A damped spring released at rest from `from`, s seconds ago. */
internal fun springAt(s: Double, from: Double, to: Double, response: Double, ratio: Double): Double {
    if (s <= 0) return from
    val w = 2 * PI / response
    val zw = ratio * w
    val wd = w * sqrt(1 - ratio * ratio)
    val a = from - to
    return to + exp(-zw * s) * (a * cos(wd * s) + (zw * a / wd) * sin(wd * s))
}

/** The held row's position in the film, in slots. */
internal fun filmHeroSlot(t: Double, from: Int, to: Int): Double = when {
    t < T.dragA -> from.toDouble()
    t < T.dropA -> mix(from.toDouble(), to + DROP_PAST, easeInOutCubic(seg(t, T.dragA, T.dropA)))
    t < T.dragB -> springAt(t - T.dropA, to + DROP_PAST, to.toDouble(), 0.42, 0.56)
    t < T.dropB -> mix(to.toDouble(), from - DROP_PAST, easeInOutCubic(seg(t, T.dragB, T.dropB)))
    t < T.tapB + 0.6 -> springAt(t - T.dropB, from - DROP_PAST, from.toDouble(), 0.42, 0.56)
    else -> from.toDouble()
}

private fun partStep(s: Double): Double = when {
    s <= 0 -> 0.0
    s >= 1.1 -> 1.0
    else -> springAt(s, 0.0, 1.0, 0.34, 0.74)
}

/** Any row's position in the film, in slots: a row between the two ends steps up as the held row passes it and back. */
internal fun filmRowSlot(slot: Int, t: Double, from: Int, to: Int): Double {
    if (slot == from) return filmHeroSlot(t, from, to)
    if (slot < from || slot > to) return slot.toDouble()
    val crossing = slot - 0.5
    val forward = (crossing - from) / (to + DROP_PAST - from)
    val back = (to - crossing) / (to - from + DROP_PAST)
    val t1 = mix(T.dragA, T.dropA, easeInOutCubicInverse(forward))
    val t2 = mix(T.dragB, T.dropB, easeInOutCubicInverse(back))
    return slot - partStep(t - t1) + partStep(t - t2)
}

internal fun filmLift(t: Double): Double = when {
    t < T.pressA -> 0.0
    t < T.dragA -> easeOutCubic(seg(t, T.pressA, T.dragA))
    t < T.dropA -> 1.0
    t < T.pressB -> springAt(t - T.dropA, 1.0, 0.0, 0.36, 0.6)
    t < T.dragB -> easeOutCubic(seg(t, T.pressB, T.dragB))
    t < T.dropB -> 1.0
    t < T.tapB + 0.6 -> springAt(t - T.dropB, 1.0, 0.0, 0.36, 0.6)
    else -> 0.0
}

internal fun filmCheck(t: Double) =
    easeOutCubic(seg(t, T.drawA, T.drawAEnd)) * (1 - easeInOutCubic(seg(t, T.drawB, T.drawBEnd)))

internal fun filmScroll(t: Double, distance: Double): Double {
    val down = easeInOutCubic(seg(t, T.dragA + 0.3, T.dropA - 0.1))
    val up = easeInOutCubic(seg(t, T.dragB + 0.3, T.dropB - 0.1))
    return distance * (down - up)
}

internal fun filmGhost(t: Double) =
    win(t, 0.4, 0.6, 2.55, 2.75) + win(t, 3.05, 3.25, 3.45, 3.65) + win(t, 4.2, 4.4, 6.35, 6.55) + win(t, 6.8, 7.0, 7.2, 7.4)

/** True while the ghost finger is on the check circle, false on the row's grip. */
internal fun filmGhostOnCheck(t: Double) = (t >= 2.9 && t < 3.9) || t >= 6.7

/** The ghost finger pressing for the two taps. */
internal fun filmGhostTap(t: Double) =
    win(t, T.tapA - 0.04, T.tapA + 0.06, T.tapA + 0.14, T.tapA + 0.26) +
        win(t, T.tapB - 0.04, T.tapB + 0.06, T.tapB + 0.14, T.tapB + 0.26)

/** Where every row is after moving row `id` to `toSlot`: the order table (id → slot). */
internal fun moveOrder(order: IntArray, id: Int, toSlot: Int): IntArray {
    val fromSlot = order[id]
    val next = order.copyOf()
    if (fromSlot == toSlot) return next
    for (i in next.indices) {
        val s = next[i]
        if (i == id) next[i] = toSlot
        else if (fromSlot < toSlot && s > fromSlot && s <= toSlot) next[i] = s - 1
        else if (fromSlot > toSlot && s >= toSlot && s < fromSlot) next[i] = s + 1
    }
    return next
}

/** Reanimated's default `withTiming` easing, `Easing.inOut(Easing.quad)`. */
internal fun inOutQuad(x: Double) = if (x < 0.5) 2 * x * x else 1 - (-2 * x + 2).pow(2) / 2

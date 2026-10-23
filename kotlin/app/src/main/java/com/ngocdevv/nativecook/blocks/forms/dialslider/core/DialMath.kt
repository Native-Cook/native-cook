package com.ngocdevv.nativecook.blocks.forms.dialslider.core

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/** Inclusive numeric range used by a dial preset. */
public data class DialRange(val min: Double, val max: Double)

/** Finite translation limits used by the pan gesture. */
public data class DialTranslationBounds(
    val minTranslation: Double,
    val maxTranslation: Double,
)

/** Keeps range arithmetic and animation transforms safely finite. */
public const val MAX_ABS_DIAL_VALUE: Double = 1_000_000.0

/**
 * JavaScript `Math.round` rounds halves toward positive infinity
 * (`Math.round(-2.5) === -2`). Kotlin's `roundToInt` rounds halves away
 * from zero, so the JS semantics are reproduced with `floor(x + 0.5)`.
 */
public fun jsRound(value: Double): Double = floor(value + 0.5)

private fun clampFiniteDialValue(value: Double): Double =
    max(-MAX_ABS_DIAL_VALUE, min(MAX_ABS_DIAL_VALUE, value))

public fun clampDialValue(value: Double, min: Double, max: Double): Double {
    val lower = min(min, max)
    val upper = max(min, max)
    val safeValue = if (value.isFinite()) value else 0.0
    return min(upper, max(lower, safeValue))
}

private fun getSafeTickSpacing(tickSpacing: Double): Double =
    if (tickSpacing.isFinite() && tickSpacing > 0) tickSpacing else 1.0

/** Returns the finite translation limits used by the pan gesture. */
public fun getDialTranslationBounds(
    minValue: Double,
    maxValue: Double,
    tickSpacing: Double,
): DialTranslationBounds {
    val spacing = getSafeTickSpacing(tickSpacing)
    val safeMin = clampFiniteDialValue(if (minValue.isFinite()) minValue else -100.0)
    val safeMax = clampFiniteDialValue(if (maxValue.isFinite()) maxValue else 100.0)
    val lower = min(safeMin, safeMax)
    val upper = max(safeMin, safeMax)
    return DialTranslationBounds(
        minTranslation = -upper * spacing,
        maxTranslation = -lower * spacing,
    )
}

/** Maps a dial value to its clamped horizontal ruler translation. */
public fun dialValueToTranslation(
    value: Double,
    minValue: Double,
    maxValue: Double,
    tickSpacing: Double,
): Double {
    val spacing = getSafeTickSpacing(tickSpacing)
    val (minTranslation, maxTranslation) = getDialTranslationBounds(minValue, maxValue, spacing)
    val safeValue = clampDialValue(value, -maxTranslation / spacing, -minTranslation / spacing)
    return max(minTranslation, min(maxTranslation, -safeValue * spacing))
}

/** Maps a ruler translation back to a clamped dial value. */
public fun dialTranslationToValue(
    translation: Double,
    minValue: Double,
    maxValue: Double,
    tickSpacing: Double,
): Double {
    val spacing = getSafeTickSpacing(tickSpacing)
    val safeTranslation = if (translation.isFinite()) translation else 0.0
    val (minTranslation, maxTranslation) = getDialTranslationBounds(minValue, maxValue, spacing)
    return clampDialValue(
        -safeTranslation / spacing,
        -maxTranslation / spacing,
        -minTranslation / spacing,
    )
}

/** Snaps the ruler to the nearest integer dial value within its range. */
public fun snapDialTranslation(
    translation: Double,
    minValue: Double,
    maxValue: Double,
    tickSpacing: Double,
): Double {
    val value = jsRound(dialTranslationToValue(translation, minValue, maxValue, tickSpacing))
    return dialValueToTranslation(value, minValue, maxValue, tickSpacing)
}

public fun normalizeDialRange(
    first: Double,
    second: Double,
    fallbackMin: Double,
    fallbackMax: Double,
): DialRange {
    val safeFallbackMin = clampFiniteDialValue(if (fallbackMin.isFinite()) fallbackMin else -100.0)
    val safeFallbackMax = clampFiniteDialValue(if (fallbackMax.isFinite()) fallbackMax else 100.0)
    val safeFirst = clampFiniteDialValue(if (first.isFinite()) first else safeFallbackMin)
    val safeSecond = clampFiniteDialValue(if (second.isFinite()) second else safeFallbackMax)
    return DialRange(min = min(safeFirst, safeSecond), max = max(safeFirst, safeSecond))
}

/**
 * Includes range boundaries and aligned interior ticks while capping render work.
 */
public fun buildDialTickValues(
    minValue: Double,
    maxValue: Double,
    baseStep: Double,
    maxTickCount: Int,
): List<Double> {
    if (!minValue.isFinite() || !maxValue.isFinite()) return emptyList()
    val boundedMin = clampFiniteDialValue(minValue)
    val boundedMax = clampFiniteDialValue(maxValue)
    val min = min(boundedMin, boundedMax)
    val max = max(boundedMin, boundedMax)
    if (min == max) return listOf(min)

    val safeBaseStep = if (baseStep.isFinite() && baseStep > 0) baseStep else 1.0
    val safeMaxCount = max(2, maxTickCount)
    val availableInterior = max(1, safeMaxCount - 2)
    val range = max - min
    val stepMultiplier = max(1.0, ceil(range / (safeBaseStep * availableInterior)))
    val step = safeBaseStep * stepMultiplier
    val firstAligned = ceil(min / step) * step
    val epsilon = step * 1e-9
    val ticks = ArrayList<Double>()
    ticks.add(min)

    var tick = firstAligned
    while (tick < max - epsilon) {
        if (tick > min + epsilon) ticks.add(tick)
        if (ticks.size >= safeMaxCount - 1) break
        tick += step
    }

    if (abs(ticks[ticks.size - 1] - max) > epsilon) {
        ticks.add(max)
    }
    return ticks
}

public fun getNearestDialTick(value: Double, ticks: List<Double>): Double {
    if (ticks.isEmpty()) return value

    var nearest = ticks[0]
    var nearestDistance = abs(value - nearest)
    for (index in 1 until ticks.size) {
        val candidate = ticks[index]
        val distance = abs(value - candidate)
        if (distance < nearestDistance) {
            nearest = candidate
            nearestDistance = distance
        }
    }
    return nearest
}

/** Keeps the edge fade finite before the ruler receives its first layout. */
public fun getDialTickOpacity(x: Double, screenCenter: Double): Double {
    if (!x.isFinite() || !screenCenter.isFinite() || screenCenter <= 0) {
        return 0.0
    }
    val fadeRadius = screenCenter * 0.95
    return max(0.0, min(1.0, 1 - abs(x) / fadeRadius))
}

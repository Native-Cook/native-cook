import Foundation

public struct DialRange: Equatable, Sendable {
    public var min: Double
    public var max: Double

    public init(min: Double, max: Double) {
        self.min = min
        self.max = max
    }
}

/// Keeps range arithmetic and animated transforms safely finite.
public let maxAbsDialValue: Double = 1_000_000

func clampFiniteDialValue(_ value: Double) -> Double {
    Swift.max(-maxAbsDialValue, Swift.min(maxAbsDialValue, value))
}

public func clampDialValue(_ value: Double, min: Double, max: Double) -> Double {
    let lower = Swift.min(min, max)
    let upper = Swift.max(min, max)
    let safeValue = value.isFinite ? value : 0
    return Swift.min(upper, Swift.max(lower, safeValue))
}

public struct DialTranslationBounds: Equatable, Sendable {
    public var minTranslation: Double
    public var maxTranslation: Double

    public init(minTranslation: Double, maxTranslation: Double) {
        self.minTranslation = minTranslation
        self.maxTranslation = maxTranslation
    }
}

func getSafeTickSpacing(_ tickSpacing: Double) -> Double {
    tickSpacing.isFinite && tickSpacing > 0 ? tickSpacing : 1
}

/// Returns the finite translation limits used by the pan gesture.
public func getDialTranslationBounds(
    minValue: Double,
    maxValue: Double,
    tickSpacing: Double
) -> DialTranslationBounds {
    let spacing = getSafeTickSpacing(tickSpacing)
    let safeMin = clampFiniteDialValue(minValue.isFinite ? minValue : -100)
    let safeMax = clampFiniteDialValue(maxValue.isFinite ? maxValue : 100)
    let lower = Swift.min(safeMin, safeMax)
    let upper = Swift.max(safeMin, safeMax)
    return DialTranslationBounds(
        minTranslation: -upper * spacing,
        maxTranslation: -lower * spacing
    )
}

/// Maps a dial value to its clamped horizontal ruler translation.
public func dialValueToTranslation(
    _ value: Double,
    minValue: Double,
    maxValue: Double,
    tickSpacing: Double
) -> Double {
    let spacing = getSafeTickSpacing(tickSpacing)
    let bounds = getDialTranslationBounds(
        minValue: minValue,
        maxValue: maxValue,
        tickSpacing: spacing
    )
    let safeValue = clampDialValue(
        value,
        min: -bounds.maxTranslation / spacing,
        max: -bounds.minTranslation / spacing
    )
    return Swift.max(
        bounds.minTranslation,
        Swift.min(bounds.maxTranslation, -safeValue * spacing)
    )
}

/// Maps a ruler translation back to a clamped dial value.
public func dialTranslationToValue(
    _ translation: Double,
    minValue: Double,
    maxValue: Double,
    tickSpacing: Double
) -> Double {
    let spacing = getSafeTickSpacing(tickSpacing)
    let safeTranslation = translation.isFinite ? translation : 0
    let bounds = getDialTranslationBounds(
        minValue: minValue,
        maxValue: maxValue,
        tickSpacing: spacing
    )
    return clampDialValue(
        -safeTranslation / spacing,
        min: -bounds.maxTranslation / spacing,
        max: -bounds.minTranslation / spacing
    )
}

/// Snaps the ruler to the nearest integer dial value within its range.
public func snapDialTranslation(
    _ translation: Double,
    minValue: Double,
    maxValue: Double,
    tickSpacing: Double
) -> Double {
    let value = jsRound(
        dialTranslationToValue(
            translation,
            minValue: minValue,
            maxValue: maxValue,
            tickSpacing: tickSpacing
        )
    )
    return dialValueToTranslation(
        value,
        minValue: minValue,
        maxValue: maxValue,
        tickSpacing: tickSpacing
    )
}

public func normalizeDialRange(
    _ first: Double,
    _ second: Double,
    fallbackMin: Double,
    fallbackMax: Double
) -> DialRange {
    let safeFallbackMin = clampFiniteDialValue(fallbackMin.isFinite ? fallbackMin : -100)
    let safeFallbackMax = clampFiniteDialValue(fallbackMax.isFinite ? fallbackMax : 100)
    let safeFirst = clampFiniteDialValue(first.isFinite ? first : safeFallbackMin)
    let safeSecond = clampFiniteDialValue(second.isFinite ? second : safeFallbackMax)
    return DialRange(
        min: Swift.min(safeFirst, safeSecond),
        max: Swift.max(safeFirst, safeSecond)
    )
}

/// Includes range boundaries and aligned interior ticks while capping render work.
public func buildDialTickValues(
    minValue: Double,
    maxValue: Double,
    baseStep: Double,
    maxTickCount: Double
) -> [Double] {
    guard minValue.isFinite, maxValue.isFinite else { return [] }
    let boundedMin = clampFiniteDialValue(minValue)
    let boundedMax = clampFiniteDialValue(maxValue)
    let min = Swift.min(boundedMin, boundedMax)
    let max = Swift.max(boundedMin, boundedMax)
    if min == max { return [min] }

    let safeBaseStep = baseStep.isFinite && baseStep > 0 ? baseStep : 1
    let safeMaxCount = Swift.max(2, maxTickCount.rounded(.down))
    let availableInterior = Swift.max(1, safeMaxCount - 2)
    let range = max - min
    let stepMultiplier = Swift.max(
        1,
        (range / (safeBaseStep * availableInterior)).rounded(.up)
    )
    let step = safeBaseStep * stepMultiplier
    let firstAligned = (min / step).rounded(.up) * step
    let epsilon = step * 1e-9
    var ticks = [min]

    var tick = firstAligned
    while tick < max - epsilon {
        if tick > min + epsilon { ticks.append(tick) }
        if Double(ticks.count) >= safeMaxCount - 1 { break }
        tick += step
    }

    if abs(ticks[ticks.count - 1] - max) > epsilon {
        ticks.append(max)
    }
    return ticks
}

public func getNearestDialTick(_ value: Double, ticks: [Double]) -> Double {
    guard let first = ticks.first else { return value }

    var nearest = first
    var nearestDistance = abs(value - nearest)
    for candidate in ticks.dropFirst() {
        let distance = abs(value - candidate)
        if distance < nearestDistance {
            nearest = candidate
            nearestDistance = distance
        }
    }
    return nearest
}

/// Keeps the edge fade finite before the ruler receives its first layout.
public func getDialTickOpacity(x: Double, screenCenter: Double) -> Double {
    guard x.isFinite, screenCenter.isFinite, screenCenter > 0 else { return 0 }
    let fadeRadius = screenCenter * 0.95
    return Swift.max(0, Swift.min(1, 1 - abs(x) / fadeRadius))
}

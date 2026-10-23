import Foundation

public func getPresetRange(_ preset: DialPreset) -> DialRange {
    normalizeDialRange(
        preset.minValue ?? DialConfig.minValue,
        preset.maxValue ?? DialConfig.maxValue,
        fallbackMin: DialConfig.minValue,
        fallbackMax: DialConfig.maxValue
    )
}

public func getPresetInitialValue(_ preset: DialPreset) -> Double {
    let range = getPresetRange(preset)
    let requestedInitial: Double
    if let initial = preset.initialValue, initial.isFinite {
        requestedInitial = initial
    } else {
        requestedInitial = 0
    }
    return clampDialValue(requestedInitial, min: range.min, max: range.max)
}

public func createInitialPresetValues(_ presets: [DialPreset]) -> [String: Double] {
    var values: [String: Double] = [:]
    for preset in presets {
        values[preset.id] = getPresetInitialValue(preset)
    }
    return values
}

/// Creates a complete, clamped map from a partial controlled/default map.
/// Missing and non-finite entries fall back to each preset's initial value.
public func resolvePresetValues(
    _ presets: [DialPreset],
    requestedValues: [String: Double]? = nil
) -> [String: Double] {
    var values: [String: Double] = [:]
    for preset in presets {
        let range = getPresetRange(preset)
        if let requested = requestedValues?[preset.id], requested.isFinite {
            values[preset.id] = clampDialValue(requested, min: range.min, max: range.max)
        } else {
            values[preset.id] = getPresetInitialValue(preset)
        }
    }
    return values
}

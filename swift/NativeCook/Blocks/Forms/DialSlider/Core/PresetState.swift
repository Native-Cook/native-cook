import Foundation

/// Anything that can be identified inside a preset list.
public protocol DialPresetIdentity {
    var id: String { get }
}

/// Anything that can be selected inside a preset list.
public protocol DialPresetSelectable: DialPresetIdentity {
    var disabled: Bool { get }
}

/// A value callback may be delivered after the user has selected another
/// preset. Only callbacks tagged with the currently active revision may
/// mutate the preset value map.
public func isCurrentPresetRevision(callbackRevision: Int, currentRevision: Int) -> Bool {
    callbackRevision == currentRevision
}

public struct PresetValueUpdateInput {
    public var callbackRevision: Int
    public var currentRevision: Int
    public var presetId: String
    public var nextValue: Double
    public var values: [String: Double]

    public init(
        callbackRevision: Int,
        currentRevision: Int,
        presetId: String,
        nextValue: Double,
        values: [String: Double]
    ) {
        self.callbackRevision = callbackRevision
        self.currentRevision = currentRevision
        self.presetId = presetId
        self.nextValue = nextValue
        self.values = values
    }
}

/// Returns nil for stale callbacks without mutating the input map.
public func updatePresetValueForRevision(_ input: PresetValueUpdateInput) -> [String: Double]? {
    guard isCurrentPresetRevision(
        callbackRevision: input.callbackRevision,
        currentRevision: input.currentRevision
    ) else { return nil }
    if input.values[input.presetId] == input.nextValue { return input.values }
    var next = input.values
    next[input.presetId] = input.nextValue
    return next
}

public struct PresetValueChange: Equatable {
    public var presetId: String
    public var value: Double
    public var values: [String: Double]
    public var changed: Bool
}

/// Builds the exact payload emitted by onValueChange and onValuesChange.
public func createPresetValueChange(_ input: PresetValueUpdateInput) -> PresetValueChange? {
    guard let nextValues = updatePresetValueForRevision(input) else { return nil }
    return PresetValueChange(
        presetId: input.presetId,
        value: input.nextValue,
        values: nextValues,
        changed: input.values[input.presetId] != input.nextValue
    )
}

/// Keeps the first occurrence so keys and value maps remain deterministic.
public func dedupePresetsById<T: DialPresetIdentity>(_ presets: [T]) -> [T] {
    var seen = Set<String>()
    return presets.filter { preset in
        if preset.id.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
            return false
        }
        if seen.contains(preset.id) { return false }
        seen.insert(preset.id)
        return true
    }
}

/// Chooses a usable preset deterministically for invalid or disabled IDs.
public func resolveSelectedPresetId<T: DialPresetSelectable>(
    _ presets: [T],
    preferredId: String?
) -> String {
    presets.first(where: { $0.id == preferredId && !$0.disabled })?.id
        ?? presets.first(where: { !$0.disabled })?.id
        ?? presets.first?.id
        ?? ""
}

public struct PresetValueConfig: Equatable, Hashable {
    public var id: String
    public var min: Double
    public var max: Double
    public var initialValue: Double

    public init(id: String, min: Double, max: Double, initialValue: Double) {
        self.id = id
        self.min = min
        self.max = max
        self.initialValue = initialValue
    }
}

public struct ReconciledPresetValues: Equatable {
    public var values: [String: Double]
    public var changed: Bool
}

public func reconcilePresetValues(
    _ presets: [PresetValueConfig],
    currentValues: [String: Double]
) -> ReconciledPresetValues {
    var values: [String: Double] = [:]
    for preset in presets {
        let current = currentValues[preset.id] ?? preset.initialValue
        values[preset.id] = Swift.min(preset.max, Swift.max(preset.min, current))
    }

    let changed = currentValues.count != values.count
        || values.contains { key, value in currentValues[key] != value }
    return ReconciledPresetValues(values: values, changed: changed)
}

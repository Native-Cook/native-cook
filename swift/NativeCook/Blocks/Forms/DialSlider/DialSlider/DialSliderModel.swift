import SwiftUI

struct DialSliderProps {
    var presets: [DialPreset]
    var initialPresetId: String?
    var selectedPresetId: Binding<String>?
    var values: Binding<[String: Double]>?
    var defaultValues: [String: Double]?
    var onPresetChange: ((String, Double) -> Void)?
    var onValueChange: ((String, Double, [String: Double]) -> Void)?
    var onValuesChange: (([String: Double]) -> Void)?
}

/// Snapshot of the derived state used by one render of `DialSlider`.
struct DialSliderResolved {
    var presets: [DialPreset]
    var multiPreset: Bool
    var values: [String: Double]
    var selectedId: String
    var selectedPreset: DialPreset?
    var selectedRange: DialRange
    var selectedValue: Double
    var formattedValue: String
}

/// Equatable description of the props that drive the synchronization effects.
struct DialSliderSyncSignature: Equatable {
    var configs: [PresetValueConfig]
    var disabledIds: [String]
    var controlledSelectedId: String?
    var controlledValues: [String: Double]?
    var valuesAreControlled: Bool
    var selectionIsControlled: Bool
}

/// Port of the `DialSlider` React state, refs, and effects.
final class DialSliderModel: ObservableObject {
    /// Latest props. Refreshed on every render, like React refs.
    var props: DialSliderProps

    @Published var uncontrolledSelectedId: String
    @Published var uncontrolledValues: [String: Double]
    @Published var displayValue: Double
    @Published private(set) var revision = 0
    @Published var isInteracting = false
    @Published var presetRowOpacity: Double = 1
    @Published var valueBadgeOpacity: Double = 0

    /// Equivalent of the `value` shared value: the committed dial value that
    /// the ruler follows and that gestures write back to.
    @Published var value: Double {
        didSet {
            if jsRound(value) != jsRound(oldValue) {
                handleSharedValueChange(value, callbackRevision: revision)
            }
        }
    }

    var reduceMotion = false
    var scrollToPreset: ((String, Bool) -> Void)?

    private var selectedConfigSignature = ""
    private var hideBadgeWork: DispatchWorkItem?

    init(props: DialSliderProps) {
        self.props = props
        let presets = dedupePresetsById(props.presets)
        let selectionIsControlled = props.selectedPresetId != nil
        let firstSelectedId = resolveSelectedPresetId(
            presets,
            preferredId: selectionIsControlled
                ? props.selectedPresetId?.wrappedValue
                : props.initialPresetId
        )
        uncontrolledSelectedId = firstSelectedId
        uncontrolledValues = resolvePresetValues(presets, requestedValues: props.defaultValues)
        let initialValues = resolvePresetValues(
            presets,
            requestedValues: props.values?.wrappedValue ?? props.defaultValues
        )
        let initialValue = initialValues[firstSelectedId] ?? 0
        displayValue = initialValue
        value = initialValue
    }

    deinit {
        hideBadgeWork?.cancel()
    }

    // MARK: Derived state

    var presets: [DialPreset] { dedupePresetsById(props.presets) }

    var presetValueConfigs: [PresetValueConfig] {
        presets.map { preset in
            let range = getPresetRange(preset)
            return PresetValueConfig(
                id: preset.id,
                min: range.min,
                max: range.max,
                initialValue: getPresetInitialValue(preset)
            )
        }
    }

    var valuesAreControlled: Bool { props.values != nil }
    var selectionIsControlled: Bool { props.selectedPresetId != nil }

    var currentValues: [String: Double] {
        if let controlled = props.values {
            return resolvePresetValues(presets, requestedValues: controlled.wrappedValue)
        }
        return reconcilePresetValues(presetValueConfigs, currentValues: uncontrolledValues).values
    }

    var currentSelectedId: String {
        resolveSelectedPresetId(
            presets,
            preferredId: selectionIsControlled
                ? props.selectedPresetId?.wrappedValue
                : uncontrolledSelectedId
        )
    }

    var syncSignature: DialSliderSyncSignature {
        DialSliderSyncSignature(
            configs: presetValueConfigs,
            disabledIds: presets.filter(\.disabled).map(\.id),
            controlledSelectedId: props.selectedPresetId?.wrappedValue,
            controlledValues: props.values?.wrappedValue,
            valuesAreControlled: valuesAreControlled,
            selectionIsControlled: selectionIsControlled
        )
    }

    func resolve() -> DialSliderResolved {
        let presets = presets
        let values = currentValues
        let selectedId = currentSelectedId
        let selectedPreset = presets.first(where: { $0.id == selectedId }) ?? presets.first
        let selectedRange = selectedPreset.map(getPresetRange) ?? DialRange(min: 0, max: 0)
        let selectedValue = selectedPreset.map { values[$0.id] ?? getPresetInitialValue($0) } ?? 0
        let formattedValue = selectedPreset?.formatValue?(displayValue)
            ?? jsNumberString(jsRound(displayValue))
        return DialSliderResolved(
            presets: presets,
            multiPreset: presets.count > 1,
            values: values,
            selectedId: selectedId,
            selectedPreset: selectedPreset,
            selectedRange: selectedRange,
            selectedValue: selectedValue,
            formattedValue: formattedValue
        )
    }

    // MARK: Effects

    /// Runs the three React effects: uncontrolled value reconciliation,
    /// uncontrolled selection repair, and selected-config/controlled-value sync.
    func synchronize() {
        let presets = presets
        let configs = presetValueConfigs

        if !valuesAreControlled {
            let previous = uncontrolledValues
            let reconciled = reconcilePresetValues(configs, currentValues: previous)
            if reconciled.changed {
                uncontrolledValues = reconciled.values
                props.onValuesChange?(reconciled.values)
                if let active = presets.first(where: { $0.id == currentSelectedId && !$0.disabled }),
                   previous[active.id] != reconciled.values[active.id],
                   let next = reconciled.values[active.id] {
                    props.onValueChange?(active.id, next, reconciled.values)
                }
            }
        }

        if !selectionIsControlled {
            let resolvedId = resolveSelectedPresetId(presets, preferredId: uncontrolledSelectedId)
            if resolvedId != uncontrolledSelectedId {
                uncontrolledSelectedId = resolvedId
                props.onPresetChange?(resolvedId, currentValues[resolvedId] ?? 0)
            }
        }

        let resolved = resolve()
        guard let selectedPreset = resolved.selectedPreset else { return }
        let nextSignature = [
            selectedPreset.id,
            jsNumberString(resolved.selectedRange.min),
            jsNumberString(resolved.selectedRange.max),
            selectedPreset.disabled ? "1" : "0",
        ].joined(separator: ":")
        let configChanged = nextSignature != selectedConfigSignature
        let controlledValueChanged = valuesAreControlled && resolved.selectedValue != displayValue
        guard configChanged || controlledValueChanged else { return }

        if configChanged { advanceRevision() }
        selectedConfigSignature = nextSignature
        displayValue = resolved.selectedValue
        value = resolved.selectedValue
        if configChanged {
            scrollToPreset?(selectedPreset.id, false)
        }
    }

    // MARK: Actions

    @discardableResult
    private func advanceRevision() -> Int {
        revision += 1
        return revision
    }

    /// Ruler callback: a gesture derived a new integer value.
    func rulerDidChangeValue(_ nextValue: Double, gestureRevision: Int) {
        guard gestureRevision == revision else { return }
        if jsRound(nextValue) != jsRound(value) {
            value = nextValue
        }
    }

    private func handleSharedValueChange(_ nextValue: Double, callbackRevision: Int) {
        let presetId = currentSelectedId
        guard let preset = presets.first(where: { $0.id == presetId }), !preset.disabled else { return }

        let range = getPresetRange(preset)
        let next = clampDialValue(jsRound(nextValue), min: range.min, max: range.max)
        let change = createPresetValueChange(
            PresetValueUpdateInput(
                callbackRevision: callbackRevision,
                currentRevision: revision,
                presetId: presetId,
                nextValue: next,
                values: currentValues
            )
        )
        guard let change else { return }

        displayValue = change.value
        guard change.changed else { return }

        if let controlled = props.values {
            controlled.wrappedValue = change.values
        } else {
            uncontrolledValues = change.values
        }
        props.onValueChange?(change.presetId, change.value, change.values)
        props.onValuesChange?(change.values)
    }

    func selectPreset(_ preset: DialPreset) {
        if preset.disabled || preset.id == currentSelectedId { return }

        let next = currentValues[preset.id] ?? getPresetInitialValue(preset)
        let range = getPresetRange(preset)
        advanceRevision()
        props.onPresetChange?(preset.id, next)
        if let controlled = props.selectedPresetId {
            controlled.wrappedValue = preset.id
            return
        }

        selectedConfigSignature = [
            preset.id,
            jsNumberString(range.min),
            jsNumberString(range.max),
            "0",
        ].joined(separator: ":")
        uncontrolledSelectedId = preset.id
        displayValue = next
        value = next
        scrollToPreset?(preset.id, true)
    }

    func adjustBy(_ delta: Double) {
        guard let preset = presets.first(where: { $0.id == currentSelectedId }), !preset.disabled else {
            return
        }
        let range = getPresetRange(preset)
        let current = currentValues[preset.id] ?? getPresetInitialValue(preset)
        let next = clampDialValue(current + delta, min: range.min, max: range.max)
        if next != current { value = next }
    }

    func handleAccessibilityStep(increment: Bool) {
        let requestedStep = presets.first(where: { $0.id == currentSelectedId })?.step
        let step: Double
        if let requestedStep, requestedStep.isFinite, requestedStep > 0 {
            step = requestedStep
        } else {
            step = 1
        }
        adjustBy(increment ? step : -step)
    }

    // MARK: Interaction feedback

    func handleInteractionStart(multiPreset: Bool) {
        hideBadgeWork?.cancel()
        hideBadgeWork = nil
        isInteracting = true
        withAnimation(timing(DialConfig.presetDimOutDuration)) {
            presetRowOpacity = multiPreset ? DialConfig.presetDimOpacity : 1
        }
        withAnimation(timing(DialConfig.valueBadgeInDuration)) {
            valueBadgeOpacity = 1
        }
    }

    func handleInteractionEnd() {
        isInteracting = false
        withAnimation(timing(DialConfig.presetDimInDuration)) {
            presetRowOpacity = 1
        }
        let work = DispatchWorkItem { [weak self] in
            guard let self else { return }
            withAnimation(self.timing(DialConfig.valueBadgeOutDuration)) {
                self.valueBadgeOpacity = 0
            }
            self.hideBadgeWork = nil
        }
        hideBadgeWork = work
        DispatchQueue.main.asyncAfter(deadline: .now() + DialConfig.valueBadgeDelay, execute: work)
    }

    private func timing(_ duration: TimeInterval) -> Animation? {
        reduceMotion ? nil : .easeInOut(duration: duration)
    }
}

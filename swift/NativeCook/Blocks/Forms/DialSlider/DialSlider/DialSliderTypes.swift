import SwiftUI

public struct DialPresetIconState: Equatable {
    public var selected: Bool
    public var adjusted: Bool
    public var value: Double

    public init(selected: Bool, adjusted: Bool, value: Double) {
        self.selected = selected
        self.adjusted = adjusted
        self.value = value
    }
}

/// An adjustment tool shown in the preset strip.
public struct DialPreset: Identifiable, DialPresetSelectable {
    /// Stable, non-empty identifier used as the key in the values map.
    public var id: String
    /// VoiceOver label.
    public var label: String
    /// Renderer for selected and adjusted visual states.
    public var icon: (DialPresetIconState) -> AnyView
    public var minValue: Double?
    public var maxValue: Double?
    public var initialValue: Double?
    /// Accessibility increment/decrement amount (default: 1).
    public var step: Double?
    public var formatValue: ((Double) -> String)?
    public var disabled: Bool

    /// Creates a preset whose icon reacts to the selected/adjusted state.
    public init<Icon: View>(
        id: String,
        label: String,
        minValue: Double? = nil,
        maxValue: Double? = nil,
        initialValue: Double? = nil,
        step: Double? = nil,
        formatValue: ((Double) -> String)? = nil,
        disabled: Bool = false,
        @ViewBuilder icon: @escaping (DialPresetIconState) -> Icon
    ) {
        self.id = id
        self.label = label
        self.minValue = minValue
        self.maxValue = maxValue
        self.initialValue = initialValue
        self.step = step
        self.formatValue = formatValue
        self.disabled = disabled
        self.icon = { AnyView(icon($0)) }
    }

    /// Creates a preset with a static icon.
    public init<Icon: View>(
        id: String,
        label: String,
        icon: Icon,
        minValue: Double? = nil,
        maxValue: Double? = nil,
        initialValue: Double? = nil,
        step: Double? = nil,
        formatValue: ((Double) -> String)? = nil,
        disabled: Bool = false
    ) {
        self.init(
            id: id,
            label: label,
            minValue: minValue,
            maxValue: maxValue,
            initialValue: initialValue,
            step: step,
            formatValue: formatValue,
            disabled: disabled
        ) { _ in icon }
    }

    /// Creates a preset without an icon.
    public init(
        id: String,
        label: String,
        minValue: Double? = nil,
        maxValue: Double? = nil,
        initialValue: Double? = nil,
        step: Double? = nil,
        formatValue: ((Double) -> String)? = nil,
        disabled: Bool = false
    ) {
        self.init(
            id: id,
            label: label,
            icon: EmptyView(),
            minValue: minValue,
            maxValue: maxValue,
            initialValue: initialValue,
            step: step,
            formatValue: formatValue,
            disabled: disabled
        )
    }
}

/// A complete map of preset identifier to value.
public typealias DialSliderValues = [String: Double]

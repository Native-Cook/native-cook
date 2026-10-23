import SwiftUI

/// Photo-style adjustment control. One preset renders a centered tool; multiple
/// presets render a strip with an independent value for each tool.
public struct DialSlider: View {
    private let props: DialSliderProps
    private let accentColor: Color
    private let adjustedColor: Color
    private let backgroundColor: Color
    private let accessibilityLabel: String?
    private let accessibilityHint: String

    @StateObject private var model: DialSliderModel
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    /// - Parameters:
    ///   - presets: Adjustment tools to render. One item renders a centered
    ///     tool; multiple items render the horizontally scrollable preset strip.
    ///   - initialPresetId: Initial selection for uncontrolled use. Ignored when
    ///     `selectedPresetId` is set.
    ///   - selectedPresetId: Controlled selected preset identifier.
    ///   - values: Controlled values map. Missing or invalid entries use preset
    ///     defaults.
    ///   - defaultValues: Initial values for uncontrolled use. Later changes are
    ///     intentionally ignored.
    ///   - onPresetChange: Called after a user request or an uncontrolled
    ///     fallback changes selection.
    ///   - onValueChange: Called for each committed integer dial value.
    ///   - onValuesChange: Called with the complete proposed values map whenever
    ///     a value changes.
    ///   - accessibilityLabel: Overrides the active preset label on the
    ///     adjustable ruler.
    public init(
        presets: [DialPreset],
        initialPresetId: String? = nil,
        selectedPresetId: Binding<String>? = nil,
        values: Binding<[String: Double]>? = nil,
        defaultValues: [String: Double]? = nil,
        onPresetChange: ((String, Double) -> Void)? = nil,
        onValueChange: ((String, Double, [String: Double]) -> Void)? = nil,
        onValuesChange: (([String: Double]) -> Void)? = nil,
        accentColor: Color = DialColors.positive,
        adjustedColor: Color = DialColors.adjusted,
        backgroundColor: Color = DialColors.background,
        accessibilityLabel: String? = nil,
        accessibilityHint: String = "Swipe horizontally or use increment and decrement actions to adjust."
    ) {
        let props = DialSliderProps(
            presets: presets,
            initialPresetId: initialPresetId,
            selectedPresetId: selectedPresetId,
            values: values,
            defaultValues: defaultValues,
            onPresetChange: onPresetChange,
            onValueChange: onValueChange,
            onValuesChange: onValuesChange
        )
        self.props = props
        self.accentColor = accentColor
        self.adjustedColor = adjustedColor
        self.backgroundColor = backgroundColor
        self.accessibilityLabel = accessibilityLabel
        self.accessibilityHint = accessibilityHint
        _model = StateObject(wrappedValue: DialSliderModel(props: props))
    }

    public var body: some View {
        // Keep the latest props and environment available to callbacks.
        model.props = props
        model.reduceMotion = reduceMotion
        let resolved = model.resolve()

        return Group {
            if let selectedPreset = resolved.selectedPreset {
                content(resolved: resolved, selectedPreset: selectedPreset)
            }
        }
        .onAppear { model.synchronize() }
        .onChange(of: model.syncSignature) { _ in model.synchronize() }
    }

    private func content(resolved: DialSliderResolved, selectedPreset: DialPreset) -> some View {
        VStack(spacing: 0) {
            DialPresetViewport(
                presets: resolved.presets,
                values: resolved.values,
                selectedPreset: selectedPreset,
                selectedRange: resolved.selectedRange,
                displayValue: model.displayValue,
                formattedValue: resolved.formattedValue,
                multiPreset: resolved.multiPreset,
                isInteracting: model.isInteracting,
                accentColor: accentColor,
                adjustedColor: adjustedColor,
                presetRowOpacity: model.presetRowOpacity,
                valueBadgeOpacity: model.valueBadgeOpacity,
                onLayout: { _ in
                    model.scrollToPreset?(resolved.selectedId, false)
                },
                onSelectPreset: { preset in model.selectPreset(preset) },
                registerScroll: { action in model.scrollToPreset = action }
            )

            DialRuler(
                value: model.value,
                minValue: resolved.selectedRange.min,
                maxValue: resolved.selectedRange.max,
                fadeColor: backgroundColor,
                enabled: !selectedPreset.disabled,
                interactionRevision: model.revision,
                onValueChange: { next, gestureRevision in
                    model.rulerDidChangeValue(next, gestureRevision: gestureRevision)
                },
                onInteractionStart: {
                    model.handleInteractionStart(multiPreset: resolved.multiPreset)
                },
                onInteractionEnd: { model.handleInteractionEnd() }
            )
            .padding(.top, 4)
            .accessibilityElement(children: .ignore)
            .accessibilityLabel(accessibilityLabel ?? selectedPreset.label)
            .accessibilityHint(accessibilityHint)
            .accessibilityValue(resolved.formattedValue)
            .accessibilityAdjustableAction { direction in
                switch direction {
                case .increment:
                    model.handleAccessibilityStep(increment: true)
                case .decrement:
                    model.handleAccessibilityStep(increment: false)
                @unknown default:
                    break
                }
            }
        }
        .padding(.top, 8)
        .padding(.bottom, 6)
        .background(backgroundColor)
    }
}

import SwiftUI

private let presetSurface = Color(dialSliderHex: 0x1C1C1E)

struct PresetButton: View {
    var preset: DialPreset
    var value: Double
    var selected: Bool
    var accentColor: Color
    var adjustedColor: Color
    /// Omit for a visual-only indicator that should not receive accessibility focus.
    var onPress: (() -> Void)?

    var body: some View {
        let range = getPresetRange(preset)
        let adjusted = jsRound(value) != jsRound(clampDialValue(0, min: range.min, max: range.max))
        let iconState = DialPresetIconState(selected: selected, adjusted: adjusted, value: value)
        let positiveProgressColor = selected ? accentColor : adjustedColor
        let progressColor = getSignedProgressColor(
            value: value,
            positiveColor: positiveProgressColor,
            negativeColor: DialColors.negative
        )
        let formatted = preset.formatValue?(value) ?? jsNumberString(jsRound(value))

        let content = ZStack {
            Circle().fill(presetSurface)
            PresetRing(
                value: value,
                minValue: range.min,
                maxValue: range.max,
                color: progressColor,
                backgroundColor: selected ? Color(dialSliderHex: 0x5C5C60) : Color(dialSliderHex: 0x4A4A4D)
            )
            preset.icon(iconState)
                .frame(width: DialConfig.itemSize - 12, height: DialConfig.itemSize - 12)
                .opacity(selected ? 1 : 0.88)
        }
        .frame(width: DialConfig.itemSize, height: DialConfig.itemSize)

        if let onPress {
            Button(action: onPress) {
                content
            }
            .buttonStyle(PresetPressStyle())
            .disabled(preset.disabled)
            .opacity(preset.disabled ? 0.35 : 1)
            .accessibilityLabel(preset.label)
            .accessibilityValue(formatted)
            .accessibilityAddTraits(selected ? .isSelected : [])
        } else {
            content
                .opacity(preset.disabled ? 0.35 : 1)
                .allowsHitTesting(false)
                .accessibilityHidden(true)
        }
    }
}

private struct PresetPressStyle: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .scaleEffect(configuration.isPressed ? 0.96 : 1)
            .opacity(configuration.isPressed ? 0.85 : 1)
    }
}

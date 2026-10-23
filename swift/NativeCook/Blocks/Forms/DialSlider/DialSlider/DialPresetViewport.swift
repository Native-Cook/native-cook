import SwiftUI

private let badgeSurface = Color(dialSliderHex: 0x1C1C1E)

struct DialPresetViewport: View {
    var presets: [DialPreset]
    var values: [String: Double]
    var selectedPreset: DialPreset
    var selectedRange: DialRange
    var displayValue: Double
    var formattedValue: String
    var multiPreset: Bool
    var isInteracting: Bool
    var accentColor: Color
    var adjustedColor: Color
    var presetRowOpacity: Double
    var valueBadgeOpacity: Double
    var onLayout: (CGFloat) -> Void
    var onSelectPreset: (DialPreset) -> Void
    var registerScroll: (@escaping (String, Bool) -> Void) -> Void

    @State private var viewportWidth: CGFloat = 0

    var body: some View {
        let selectedValue = values[selectedPreset.id] ?? getPresetInitialValue(selectedPreset)
        let badgeProgressColor = getSignedProgressColor(
            value: displayValue,
            positiveColor: accentColor,
            negativeColor: DialColors.negative
        )
        let contentPadding = multiPreset
            ? Swift.max(0, (viewportWidth - DialConfig.itemSize) / 2)
            : 0

        ZStack(alignment: .top) {
            if multiPreset {
                ScrollViewReader { proxy in
                    ScrollView(.horizontal, showsIndicators: false) {
                        HStack(spacing: DialConfig.itemGap) {
                            ForEach(presets) { preset in
                                PresetButton(
                                    preset: preset,
                                    value: values[preset.id] ?? getPresetInitialValue(preset),
                                    selected: preset.id == selectedPreset.id,
                                    accentColor: accentColor,
                                    adjustedColor: adjustedColor,
                                    onPress: { onSelectPreset(preset) }
                                )
                                .id(preset.id)
                            }
                        }
                        .padding(.horizontal, contentPadding)
                    }
                    .scrollDisabled(isInteracting)
                    .opacity(presetRowOpacity)
                    .onAppear {
                        registerScroll { id, animated in
                            if animated {
                                withAnimation(.easeInOut(duration: 0.25)) {
                                    proxy.scrollTo(id, anchor: .center)
                                }
                            } else {
                                proxy.scrollTo(id, anchor: .center)
                            }
                        }
                    }
                }
            } else {
                PresetButton(
                    preset: selectedPreset,
                    value: selectedValue,
                    selected: true,
                    accentColor: accentColor,
                    adjustedColor: adjustedColor,
                    onPress: nil
                )
                .frame(maxWidth: .infinity)
            }

            ZStack {
                Circle().fill(badgeSurface)
                PresetRing(
                    value: displayValue,
                    minValue: selectedRange.min,
                    maxValue: selectedRange.max,
                    color: badgeProgressColor,
                    backgroundColor: Color(dialSliderHex: 0x5C5C60)
                )
                Text(formattedValue)
                    .font(.system(size: 15, weight: .medium))
                    .monospacedDigit()
                    .multilineTextAlignment(.center)
                    .foregroundColor(accentColor)
            }
            .frame(width: DialConfig.itemSize, height: DialConfig.itemSize)
            .opacity(valueBadgeOpacity)
            .scaleEffect(0.94 + valueBadgeOpacity * 0.06)
            .allowsHitTesting(false)
            .accessibilityHidden(true)
        }
        .frame(maxWidth: .infinity)
        .frame(height: DialConfig.itemSize)
        .clipped()
        .background(
            GeometryReader { geometry in
                Color.clear
                    .onAppear {
                        viewportWidth = geometry.size.width
                        onLayout(geometry.size.width)
                    }
                    .onChange(of: geometry.size.width) { width in
                        viewportWidth = width
                        onLayout(width)
                    }
            }
        )
    }
}

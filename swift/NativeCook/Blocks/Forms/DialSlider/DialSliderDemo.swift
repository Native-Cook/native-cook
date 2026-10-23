// SwiftUI equivalent of `apps/example/src/app/index.tsx`.
//
// This is the library's `Example/ExampleView.swift` under the block's name. The library's
// sources sit next to it instead of in a Swift package, so there is no `import DialSlider`.

import SwiftUI

private struct PresetGlyph: View {
    let glyph: String
    let selected: Bool

    var body: some View {
        Text(glyph)
            .font(.system(size: 24, weight: .medium))
            .foregroundColor(selected ? Color.white : Color(red: 0xE4 / 255, green: 0xE4 / 255, blue: 0xE7 / 255))
    }
}

private func photoPreset(id: String, label: String, glyph: String) -> DialPreset {
    DialPreset(
        id: id,
        label: label,
        minValue: -100,
        maxValue: 100,
        initialValue: 0
    ) { state in
        PresetGlyph(glyph: glyph, selected: state.selected)
    }
}

private let photoPresets: [DialPreset] = [
    photoPreset(id: "exposure", label: "Exposure", glyph: "◐"),
    photoPreset(id: "brilliance", label: "Brilliance", glyph: "◭"),
    photoPreset(id: "highlights", label: "Highlights", glyph: "◒"),
    photoPreset(id: "shadows", label: "Shadows", glyph: "◑"),
    photoPreset(id: "contrast", label: "Contrast", glyph: "◉"),
    photoPreset(id: "brightness", label: "Brightness", glyph: "☀"),
    photoPreset(id: "saturation", label: "Saturation", glyph: "◍"),
]

struct DialSliderDemo: View {
    var body: some View {
        VStack(spacing: 0) {
            Spacer(minLength: 0)

            DialSlider(
                presets: photoPresets,
                initialPresetId: "highlights",
                backgroundColor: .black
            )

            Spacer(minLength: 0)
        }
        .padding(.vertical, 24)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(Color.black.ignoresSafeArea())
    }
}

#Preview {
    DialSliderDemo()
}

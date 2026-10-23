import SwiftUI

// Seconds for a digit to roll to its new value, on CSS's ease-out.
private let rollFor = 0.26
private let roll = Animation.timingCurve(0, 0, 0.58, 1, duration: rollFor)

/// A number whose digits roll like an odometer when it changes; other characters are set.
struct StepsBarsToSpikesRoller: View {
    /// The number as shown, for example "64.7".
    var text: String
    var size: Double
    /// The height of one digit's window.
    var lineHeight: Double
    var weight: Font.Weight
    var tracking = 0.0
    var color = Color.white
    var still: Bool

    var body: some View {
        let characters = Array(text)
        // Counted from the right, so units stay units when the number gets longer.
        let keyed = characters.enumerated().map { (key: characters.count - $0.offset, character: $0.element) }
        HStack(spacing: 0) {
            ForEach(keyed, id: \.key) { cell($0.character) }
        }
        .accessibilityElement()
        .accessibilityLabel(text)
    }

    @ViewBuilder private func cell(_ character: Character) -> some View {
        if let digit = character.wholeNumberValue {
            // A digit in a window one line tall: a column of 0–9 slides to show it.
            VStack(spacing: 0) {
                ForEach(0..<10, id: \.self) { d in glyph(String(d)) }
            }
            .offset(y: -Double(digit) * lineHeight)
            .animation(still ? nil : roll, value: digit)
            .frame(height: lineHeight, alignment: .top)
            .clipped()
        } else {
            glyph(String(character))
        }
    }

    private func glyph(_ value: String) -> some View {
        Text(value)
            .font(.system(size: size, weight: weight))
            .monospacedDigit()
            .tracking(tracking)
            .foregroundStyle(color)
            .frame(height: lineHeight)
            .fixedSize()
    }
}

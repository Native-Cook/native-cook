import SwiftUI

// Seconds for a digit to roll to its new value, on CSS's `ease-out`.
private let roll = 0.26
private let digits = Array("0123456789")

/// A number whose digits roll like an odometer when it changes.
struct SleepColumnScrubberRoller: View {
    /// The number as shown, for example "00:42—08:23"; digits roll, other characters are set.
    var text: String
    var font: Font
    var color: Color
    /// The height of one digit's window.
    var lineHeight: Double
    var still: Bool

    var body: some View {
        // Counted from the right, so units stay units when the number gets longer.
        let characters = Array(text).enumerated().map { (key: text.count - $0.offset, character: $0.element) }
        HStack(spacing: 0) {
            ForEach(characters, id: \.key) { item in
                if let digit = digits.firstIndex(of: item.character) {
                    window(digit)
                } else {
                    Text(String(item.character))
                        .font(font)
                        .foregroundStyle(color)
                        .frame(height: lineHeight)
                }
            }
        }
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(text)
    }

    /// A digit in a window one line tall: a column of 0–9 slides to show it.
    private func window(_ digit: Int) -> some View {
        VStack(spacing: 0) {
            ForEach(digits, id: \.self) { d in
                Text(String(d))
                    .font(font)
                    .foregroundStyle(color)
                    .frame(height: lineHeight)
            }
        }
        .offset(y: -Double(digit) * lineHeight)
        .animation(still ? nil : .timingCurve(0, 0, 0.58, 1, duration: roll), value: digit)
        .frame(height: lineHeight, alignment: .top)
        .clipped()
    }
}

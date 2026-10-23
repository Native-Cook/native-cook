import SwiftUI

// Seconds for a digit to roll to its new value, on CSS's `ease-out` curve.
private let roll = 0.26
private let rollCurve = Animation.timingCurve(0, 0, 0.58, 1, duration: roll)

/// A number whose digits roll like an odometer when it changes.
struct StressDotGridDonutRoller: View {
    /// The number as shown, for example "64.7"; digits roll, other characters are set.
    var text: String
    var size: CGFloat
    var weight: Font.Weight
    var color: Color
    /// The height of one digit's window.
    var lineHeight: CGFloat
    var still: Bool

    var body: some View {
        let characters = Array(text)
        // Keyed from the right, so units stay units when the number gets longer.
        let places = characters.indices.map { Place(key: characters.count - $0, character: characters[$0]) }
        HStack(spacing: 0) {
            ForEach(places) { place in
                if let digit = place.character.wholeNumberValue {
                    StressDotGridDonutDigit(digit: digit, font: font, color: color, lineHeight: lineHeight, still: still)
                } else {
                    Text(String(place.character)).font(font).foregroundStyle(color).frame(height: lineHeight)
                }
            }
        }
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(text)
    }

    private var font: Font { .system(size: size, weight: weight).monospacedDigit() }

    private struct Place: Identifiable {
        var key: Int
        var character: Character
        var id: Int { key }
    }
}

/// A digit in a window one line tall: a column of 0–9 slides to show it.
private struct StressDotGridDonutDigit: View {
    var digit: Int
    var font: Font
    var color: Color
    var lineHeight: CGFloat
    var still: Bool

    var body: some View {
        VStack(spacing: 0) {
            ForEach(0..<10, id: \.self) { d in
                Text("\(d)").font(font).foregroundStyle(color).frame(height: lineHeight)
            }
        }
        .offset(y: -CGFloat(digit) * lineHeight)
        .animation(still ? nil : rollCurve, value: digit)
        .frame(height: lineHeight, alignment: .top)
        .clipped()
    }
}

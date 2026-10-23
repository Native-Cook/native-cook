import SwiftUI
import UIKit

/// Text with a fixed line height, as React Native sets it: the glyphs sit in the middle of each line box.
enum PulseHeartCardText {
    static func naturalLineHeight(size: Double, weight: UIFont.Weight) -> Double {
        Double(UIFont.systemFont(ofSize: size, weight: weight).lineHeight)
    }

    /// One line in a box `line` tall.
    static func line(_ text: String, size: Double, line: Double, weight: Font.Weight, color: Color, tabular: Bool = false) -> some View {
        Text(text)
            .font(.system(size: size, weight: weight))
            .monospacedDigit(tabular)
            .foregroundStyle(color)
            .lineLimit(1)
            .frame(height: line)
    }

    /// Several lines (separated by "\n") on a fixed line pitch.
    static func lines(_ text: String, size: Double, line: Double, weight: Font.Weight, uiWeight: UIFont.Weight, color: Color, width: Double, height: Double) -> some View {
        let natural = naturalLineHeight(size: size, weight: uiWeight)
        return Text(text)
            .font(.system(size: size, weight: weight))
            .foregroundStyle(color)
            .lineSpacing(line - natural)
            .fixedSize(horizontal: true, vertical: true)
            .frame(width: width, height: height, alignment: .topLeading)
            .offset(y: (line - natural) / 2)
    }
}

extension View {
    /// `monospacedDigit()` only when asked, so the call site stays one expression.
    @ViewBuilder func monospacedDigit(_ on: Bool) -> some View {
        if on { monospacedDigit() } else { self }
    }
}

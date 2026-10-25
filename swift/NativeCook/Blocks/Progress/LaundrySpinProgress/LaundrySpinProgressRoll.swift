import SwiftUI
import UIKit

/*
 Rolling labels for the readouts. Numbers roll digit by digit like an odometer and always come to
 rest on whole digits (the shown value springs to round(value), 420 ms, damping ratio 0.86); phase
 labels roll inside a clipped box (the old one leaves upwards, the new one comes from below, 380 ms)
 and never cross-fade in place. Both only animate when their rounded value or index changes, so
 the per-frame timeline around them costs nothing here.
 */

/// A fixed font for a readout: SwiftUI's `Font` and the `UIFont` used to measure it.
struct LaundrySpinProgressRollFont {
    let ui: UIFont
    let color: Color
    let kerning: CGFloat

    init(size: CGFloat, weight: UIFont.Weight, color: Color, tabular: Bool = true, kerning: CGFloat = 0) {
        ui = tabular ? UIFont.monospacedDigitSystemFont(ofSize: size, weight: weight) : UIFont.systemFont(ofSize: size, weight: weight)
        self.color = color
        self.kerning = kerning
    }

    var font: Font { Font(ui) }

    func width(_ s: String) -> CGFloat {
        ceil((s as NSString).size(withAttributes: [.font: ui, .kern: kerning]).width)
    }

    /// Top padding that puts this font's baseline on the baseline of `other` centred in `line`.
    func baselinePad(matching other: LaundrySpinProgressRollFont, line: CGFloat) -> CGFloat {
        let a = other.ui.ascender, d = -other.ui.descender
        let baseline = (line - (a + d)) / 2 + a
        let mine = (line - (ui.ascender - ui.descender)) / 2 + ui.ascender
        return baseline - mine
    }
}

private let LaundrySpinProgressRollDigits = ["0", "1", "2", "3", "4", "5", "6", "7", "8", "9", "0"]

private func LaundrySpinProgressColumnPos(_ shown: Double, _ k: Int) -> Double {
    let p = pow(10.0, Double(k))
    if k == 0 { return (shown.truncatingRemainder(dividingBy: 10) + 10).truncatingRemainder(dividingBy: 10) }
    let baseV = floor(shown / p)
    let carry = min(1, max(0, shown - baseV * p - (p - 1)))
    return (baseV.truncatingRemainder(dividingBy: 10)) + carry
}

private func LaundrySpinProgressColumnVisible(_ shown: Double, _ k: Int, _ pad: Bool) -> Double {
    if k == 0 || pad { return 1 }
    return min(1, max(0, shown - (pow(10.0, Double(k)) - 1)))
}

/// A number that rolls to round(value) whenever that changes.
struct LaundrySpinProgressRollNumber: View {
    var value: Double
    /// Number of digit columns (leading zeros are hidden and the row closes up).
    var digits: Int
    var line: CGFloat
    var style: LaundrySpinProgressRollFont
    var prefix: String? = nil
    var suffix: String? = nil
    var suffixStyle: LaundrySpinProgressRollFont? = nil
    /// Keeps the visible digits centred where the row is centred (half the shift).
    var center = false
    /// Always show every column (leading zeros too), e.g. hours as 09.
    var pad = false
    var still = false

    var body: some View {
        let r = Int(value.rounded())
        LaundrySpinProgressRollDigitsRow(shown: Double(r), digits: digits, line: line, style: style, prefix: prefix,
                           suffix: suffix, suffixStyle: suffixStyle ?? style, center: center, pad: pad)
            .animation(still ? nil : .spring(duration: 0.42, bounce: 0.14), value: r)
            .accessibilityElement(children: .ignore)
            .accessibilityLabel("\(prefix ?? "")\(r)\(suffix ?? "")")
    }
}

private struct LaundrySpinProgressRollDigitsRow: View, Animatable {
    var shown: Double
    let digits: Int
    let line: CGFloat
    let style: LaundrySpinProgressRollFont
    let prefix: String?
    let suffix: String?
    let suffixStyle: LaundrySpinProgressRollFont
    let center: Bool
    let pad: Bool

    var animatableData: Double {
        get { shown }
        set { shown = newValue }
    }

    var body: some View {
        let colW = style.width("0")
        var hidden = 0.0
        for k in 1..<max(digits, 1) { hidden += 1 - LaundrySpinProgressColumnVisible(shown, k, pad) }
        let shift = -hidden * colW * (center ? 0.5 : 1)
        let sPad = suffixStyle.baselinePad(matching: style, line: line)
        return HStack(alignment: .top, spacing: 0) {
            if let prefix {
                Text(prefix).font(suffixStyle.font).kerning(suffixStyle.kerning).foregroundStyle(suffixStyle.color)
                    .fixedSize().frame(height: line, alignment: .top).padding(.top, sPad)
            }
            HStack(alignment: .top, spacing: 0) {
                ForEach((0..<digits).reversed(), id: \.self) { k in
                    column(k, width: colW)
                }
                if let suffix {
                    Text(suffix).font(suffixStyle.font).kerning(suffixStyle.kerning).foregroundStyle(suffixStyle.color)
                        .fixedSize().padding(.top, sPad).frame(height: line, alignment: .top)
                }
            }
            .offset(x: shift)
        }
        .frame(height: line, alignment: .top)
    }

    private func column(_ k: Int, width: CGFloat) -> some View {
        VStack(spacing: 0) {
            ForEach(0..<LaundrySpinProgressRollDigits.count, id: \.self) { i in
                Text(LaundrySpinProgressRollDigits[i]).font(style.font).kerning(style.kerning).foregroundStyle(style.color)
                    .frame(width: width, height: line)
            }
        }
        .offset(y: -LaundrySpinProgressColumnPos(shown, k) * line)
        .frame(width: width, height: line, alignment: .top)
        .clipped()
        .opacity(LaundrySpinProgressColumnVisible(shown, k, pad))
    }
}

/// A label that rolls to labels[index] whenever the index changes (a few times a loop).
struct LaundrySpinProgressRollLabel: View {
    var labels: [String]
    var index: Int
    var line: CGFloat
    var style: LaundrySpinProgressRollFont
    var still = false

    var body: some View {
        let longest = labels.max(by: { $0.count < $1.count }) ?? ""
        let text = labels.indices.contains(index) ? labels[index] : ""
        ZStack(alignment: .leading) {
            Text(longest).kerning(style.kerning).opacity(0)
            Text(text)
                .kerning(style.kerning)
                .id(index)
                .transition(.asymmetric(
                    insertion: .offset(y: line).combined(with: .opacity),
                    removal: .offset(y: -line).combined(with: .opacity)
                ))
        }
        .font(style.font)
        .foregroundStyle(style.color)
        .lineLimit(1)
        .fixedSize()
        .frame(height: line)
        .clipped()
        .animation(still ? nil : .timingCurve(0.65, 0, 0.35, 1, duration: 0.38), value: index)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(text)
    }
}

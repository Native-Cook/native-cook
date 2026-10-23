import SwiftUI

private let size = 28.0
private let radius = size / 2

// A blue disc with a ring of twelve gold dots.
private let ring = 8.5
private let dot = 1.5

// Thirteen red and white stripes under a blue corner.
private let stripe = size / 13

/// A flat round badge for a currency: a simplified flag, no detail that needs to be read.
struct RangeMorphPriceLineCurrencyDisc: View {
    enum Kind { case eur, usd }
    var kind: Kind

    var body: some View {
        Canvas { context, _ in
            var flag = context
            flag.clip(to: Path(ellipseIn: CGRect(x: 0, y: 0, width: size, height: size)))
            switch kind {
            case .eur:
                flag.fill(Path(CGRect(x: 0, y: 0, width: size, height: size)), with: .color(Color(hex: 0x1f4fbf)))
                for i in 0..<12 {
                    let a = Double(i) / 12 * tau
                    let x = radius + cos(a) * ring
                    let y = radius + sin(a) * ring
                    flag.fill(Path(ellipseIn: CGRect(x: x - dot, y: y - dot, width: dot * 2, height: dot * 2)), with: .color(Color(hex: 0xffd43a)))
                }
            case .usd:
                flag.fill(Path(CGRect(x: 0, y: 0, width: size, height: size)), with: .color(.white))
                for i in 0..<7 {
                    flag.fill(Path(CGRect(x: 0, y: Double(i) * 2 * stripe, width: size, height: stripe)), with: .color(Color(hex: 0xd4313f)))
                }
                flag.fill(Path(CGRect(x: 0, y: 0, width: size * 0.5, height: stripe * 7)), with: .color(Color(hex: 0x2b3f8c)))
            }
            context.stroke(
                Path(ellipseIn: CGRect(x: 0.5, y: 0.5, width: size - 1, height: size - 1)),
                with: .color(.white.opacity(0.35)),
                lineWidth: 1
            )
        }
        .frame(width: size, height: size)
        .accessibilityHidden(true)
    }
}

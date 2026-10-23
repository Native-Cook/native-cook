import SwiftUI

/// The countdown etched over a frozen product: 24:00:00, then a tick every second; digits roll.
struct PriceTagWelcomeCountdown: View {
    let item: PriceTagWelcomeTheme.Item
    let look: PriceTagWelcomeMotion.Look
    let freezeTime: Double
    let reduced: Bool
    let unit: Double
    let fontScale: Double

    var body: some View {
        let style = PriceTagWelcomeTheme.Typography.timer
        let size = style.size * fontScale
        let lineHeight = (size * 1.18).rounded()
        let at = item.id == "laptop" ? PriceTagWelcomeTheme.Layout.timerLaptop : PriceTagWelcomeTheme.Layout.timerOther
        let count = PriceTagWelcomeMotion.countdown(at: max(freezeTime, 0), still: reduced)
        PriceTagWelcomeEtch(timer: true, look: look, reduced: reduced, drop: PriceTagWelcomeTheme.Space.etchDrop * unit) {
            HStack(spacing: 0) {
                ForEach(0..<6, id: \.self) { column in
                    if column == 2 || column == 4 {
                        PriceTagWelcomeEdgedText(
                            text: ":", size: size, weight: style.weight, tracking: style.letterSpacing * fontScale,
                            color: PriceTagWelcomeTheme.Colors.scrape, edge: PriceTagWelcomeTheme.Colors.scrapeEdge,
                            edgeOffset: 1, tabular: true
                        )
                        .frame(height: lineHeight)
                    }
                    digitColumn(column, count: count, size: size, lineHeight: lineHeight, style: style)
                }
            }
        }
        .frame(width: item.width * unit, height: lineHeight)
        .position(x: item.width * unit * at.fx, y: item.height * unit * at.fy)
        .allowsHitTesting(false)
    }

    /// One rolling digit: the strip "0 ... 9 0" behind a one-line window.
    private func digitColumn(_ column: Int, count: (left: Int, previous: Int, roll: Double), size: Double,
                             lineHeight: Double, style: PriceTagWelcomeTheme.TextStyle) -> some View {
        let line = PriceTagWelcomeMotion.stripLine(
            from: PriceTagWelcomeMotion.digit(of: count.previous, col: column),
            to: PriceTagWelcomeMotion.digit(of: count.left, col: column),
            roll: count.roll
        )
        let digits = Array(0...10).map { String($0 % 10) }
        return PriceTagWelcomeEdgedText(
            text: "0", size: size, weight: style.weight, tracking: style.letterSpacing * fontScale,
            color: .clear, edge: .clear, edgeOffset: 0, tabular: true
        )
        .frame(height: lineHeight)
        .overlay(alignment: .top) {
            VStack(spacing: 0) {
                ForEach(Array(digits.enumerated()), id: \.offset) { _, digit in
                    PriceTagWelcomeEdgedText(
                        text: digit, size: size, weight: style.weight, tracking: style.letterSpacing * fontScale,
                        color: PriceTagWelcomeTheme.Colors.scrape, edge: PriceTagWelcomeTheme.Colors.scrapeEdge,
                        edgeOffset: 1, tabular: true
                    )
                    .frame(height: lineHeight)
                }
            }
            .offset(y: -line * lineHeight)
        }
        .clipped()
    }
}

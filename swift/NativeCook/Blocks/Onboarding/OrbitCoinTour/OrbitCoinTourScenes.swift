import SwiftUI

// The three cards the coins open into, in stage points. `reveal` runs from 0 to 1 once a scene is
// in front, and each part of its card comes in over its own stretch of that.

struct OrbitCoinTourSceneText: Equatable {
    var title: String
    var subtitle: String

    static let samples = [
        OrbitCoinTourSceneText(title: "Savings Account", subtitle: "Pay. Track. Save. Invest."),
        OrbitCoinTourSceneText(title: "The 3-in-1 Credit Card", subtitle: "Assured 2% cashback"),
        OrbitCoinTourSceneText(title: "Get powerful Insights", subtitle: "Track all your spends"),
    ]
}

private typealias S = OrbitCoinTourStage
private typealias M = OrbitCoinTourMotion

private enum Card {
    static let ink = Color(hex: 0x37342E)
    static let x = S.cx - 152
    static let width = 304.0
    static let row = 56.0
    static let spendRow = 48.0
    /// The balance counts up from this much less.
    static let countFrom = 436.0
    static let links = ["Credit Card", "Loans", "Investments"]
    static let spends: [(name: String, amount: Double, bar: Double)] = [
        ("Shopping", 14140, 100), ("Food", 8890, 34), ("Entertainment", 4103, 24), ("Bill Payments", 2372, 12),
    ]
    /// The chart: thin lines rising to the right, the first quarter green, the rest dotted grey.
    static let chartLines = 36
    static let chartGreen = 9
    static let chartLeft = 24.0
    static let chartStep = 7.3
    static let chartBase = 96.0

    static func lineHeight(_ i: Int) -> Double {
        14 + 70 * pow(Double(i) / Double(chartLines - 1), 1.5) + 6 * sin(Double(i) * 1.7)
    }

    static func chart(_ reveal: Double, _ range: Range<Int>) -> Path {
        var path = Path()
        // The lines grow from the baseline, left to right, four at a time.
        let grown = M.smooth(reveal, 0.1, 0.8) * Double(chartLines + 4)
        for i in range {
            let part = M.clamp01((grown - Double(i)) / 4)
            guard part > 0 else { continue }
            let x = chartLeft + Double(i) * chartStep
            path.move(to: CGPoint(x: x, y: chartBase))
            path.addLine(to: CGPoint(x: x, y: chartBase - lineHeight(i) * part))
        }
        return path
    }

    static func money(_ symbol: String, _ value: Double) -> String {
        var digits = String(Int(value.rounded()))
        var out = ""
        while digits.count > 3 {
            out = "," + digits.suffix(3) + out
            digits = String(digits.dropLast(3))
        }
        return symbol + digits + out
    }

    /// The balance while it counts up to `value` over the middle of the reveal.
    static func count(_ reveal: Double, _ value: Double) -> Double {
        let u = 1 - M.clamp01((reveal - 0.1) / 0.65)
        return value - countFrom * u * u * u
    }

    static func fade(_ reveal: Double, _ from: Double) -> Double {
        M.smooth(reveal, from, from + 0.25)
    }
}

private extension View {
    /// Places a view of a given size by its top-left corner on the stage.
    func at(_ x: Double, _ y: Double, width: CGFloat? = nil, height: CGFloat? = nil, alignment: Alignment = .topLeading) -> some View {
        frame(width: width, height: height, alignment: alignment).offset(x: x, y: y)
    }
}

/// The two lines under a card.
private struct Caption: View {
    let text: OrbitCoinTourSceneText
    let color: Color
    let mute: Color

    var body: some View {
        Text(text.title)
            .font(.system(size: 28, weight: .light))
            .foregroundStyle(color)
            .lineLimit(1)
            .at(0, 651, width: S.width, height: 34, alignment: .center)
        Text(text.subtitle)
            .font(.system(size: 20, weight: .light))
            .foregroundStyle(mute)
            .lineLimit(1)
            .at(0, 693, width: S.width, height: 26, alignment: .center)
    }
}

/// A white card with a coloured block at its top.
private struct CardBody: View {
    let top: Double
    let height: Double
    let block: Double
    let color: Color

    var body: some View {
        RoundedRectangle(cornerRadius: 14, style: .circular)
            .fill(.white)
            .shadow(color: .black.opacity(0.12), radius: 10, y: 8)
            .at(Card.x, top, width: Card.width, height: height)
        UnevenRoundedRectangle(topLeadingRadius: 14, topTrailingRadius: 14, style: .circular)
            .fill(color)
            .at(Card.x, top, width: Card.width, height: block)
    }
}

/// Scene 1: a savings account on a green card, with three links under it.
struct OrbitCoinTourSavings: View {
    let reveal: Double
    let text: OrbitCoinTourSceneText
    let balance: Double
    let symbol: String
    var onAction: (() -> Void)? = nil

    var body: some View {
        let hero = S.heroes[0]
        let top = hero.y - hero.h / 2
        ZStack(alignment: .topLeading) {
            CardBody(top: top, height: hero.h + Card.row * 3 + 8, block: hero.h, color: Color(hex: 0x136F63))
            Text("NORTH BANK")
                .font(.system(size: 7, weight: .bold))
                .tracking(0.4)
                .foregroundStyle(Color(hex: 0x136F63))
                .padding(.horizontal, 5)
                .frame(height: 14)
                .background(RoundedRectangle(cornerRadius: 2).fill(.white))
                .opacity(Card.fade(reveal, 0))
                .at(Card.x, top + 16, width: Card.width - 14, alignment: .topTrailing)
            Text(text.title)
                .font(.system(size: 15, weight: .medium))
                .foregroundStyle(.white)
                .opacity(Card.fade(reveal, 0))
                .at(Card.x + 24, top + 56, height: 20, alignment: .leading)
            Text(Card.money(symbol, Card.count(reveal, balance)))
                .font(.system(size: 36))
                .monospacedDigit()
                .foregroundStyle(.white)
                .opacity(Card.fade(reveal, 0.08))
                .at(Card.x + 24, top + 84, height: 44, alignment: .leading)
            Button {
                onAction?()
            } label: {
                Text("Add Money")
                    .font(.system(size: 14))
                    .foregroundStyle(Card.ink)
                    .frame(width: Card.width - 44, height: 45)
                    .background(RoundedRectangle(cornerRadius: 4).fill(.white))
            }
            .buttonStyle(.plain)
            .opacity(Card.fade(reveal, 0.16))
            .at(Card.x + 22, top + 176)
            ForEach(Array(Card.links.enumerated()), id: \.offset) { index, label in
                HStack {
                    Text(label)
                        .font(.system(size: 14, weight: .medium))
                        .foregroundStyle(Card.ink)
                    Spacer()
                    OrbitCoinTourArrow()
                        .stroke(Card.ink, style: StrokeStyle(lineWidth: 1.6, lineCap: .round, lineJoin: .round))
                        .frame(width: 16, height: 14)
                }
                .frame(width: Card.width - 44, height: Card.row)
                .overlay(alignment: .bottom) {
                    Rectangle().fill(index == 0 ? Color(hex: 0xFA776B) : Color(hex: 0xECE7E0)).frame(height: 1)
                }
                .opacity(Card.fade(reveal, 0.3 + Double(index) * 0.08))
                .at(Card.x + 22, top + hero.h + Double(index) * Card.row)
            }
            Caption(text: text, color: Card.ink, mute: Color(hex: 0x6B655C))
        }
        .frame(width: S.width, height: S.height, alignment: .topLeading)
    }
}

/// Scene 2: a payment card standing upright, and the offer that pops up over its foot.
struct OrbitCoinTourPayCard: View {
    let reveal: Double
    let text: OrbitCoinTourSceneText

    var body: some View {
        let hero = S.heroes[1]
        let left = hero.x - hero.w / 2
        let top = hero.y - hero.h / 2
        // The offer goes past its size and back.
        let u = M.smooth(reveal, 0.45, 0.85) - 1
        let back = 1 + 2.7 * u * u * u + 1.7 * u * u
        ZStack(alignment: .topLeading) {
            ZStack(alignment: .topLeading) {
                Color(hex: 0xE86262)
                    .overlay(alignment: .topLeading) {
                        S.letterPath
                            .applying(CGAffineTransform(translationX: -22, y: 150).scaledBy(x: 3.2, y: 3.2))
                            .fill(.black.opacity(0.07))
                    }
                    .frame(width: 144, height: hero.h)
                    .clipped()
                Color.white.opacity(0.18)
                    .frame(width: hero.w - 144, height: hero.h)
                    .offset(x: 144)
                Text("peak")
                    .font(.system(size: 30, weight: .bold))
                    .foregroundStyle(.white)
                    .opacity(Card.fade(reveal, 0))
                    .at(20, 24, height: 36, alignment: .leading)
                RoundedRectangle(cornerRadius: 5)
                    .fill(Color(hex: 0xD9DCE0))
                    .overlay {
                        VStack(spacing: 12) {
                            Color(hex: 0xA9AEB5).frame(height: 1)
                            Color(hex: 0xA9AEB5).frame(height: 1)
                        }
                    }
                    .opacity(Card.fade(reveal, 0))
                    .at(158, 28, width: 28, height: 40)
            }
            .frame(width: hero.w, height: hero.h, alignment: .topLeading)
            .clipShape(RoundedRectangle(cornerRadius: hero.r, style: .circular))
            .offset(x: left, y: top)
            Text("Lifetime Free")
                .font(.system(size: 20, weight: .semibold))
                .foregroundStyle(Color(hex: 0x388370))
                .frame(width: 300, height: 76)
                .background(RoundedRectangle(cornerRadius: 6).fill(.white).shadow(color: .black.opacity(0.18), radius: 10, y: 8))
                .scaleEffect(0.8 + 0.2 * back)
                .opacity(M.smooth(reveal, 0.45, 0.6))
                .offset(x: S.cx - 150, y: 500)
            Caption(text: text, color: .white, mute: .white.opacity(0.75))
        }
        .frame(width: S.width, height: S.height, alignment: .topLeading)
    }
}

/// Scene 3: where the money went, under a chart that grows from the left.
struct OrbitCoinTourInsights: View {
    let reveal: Double
    let text: OrbitCoinTourSceneText
    let balance: Double
    let symbol: String

    var body: some View {
        let hero = S.heroes[2]
        let top = hero.y - hero.h / 2
        ZStack(alignment: .topLeading) {
            CardBody(top: top, height: hero.h + 20 + Card.spendRow * Double(Card.spends.count), block: hero.h, color: Color(hex: 0x363B42))
            Text("Money")
                .font(.system(size: 15, weight: .medium))
                .foregroundStyle(.white)
                .opacity(Card.fade(reveal, 0))
                .at(Card.x + 24, top + 30, height: 20, alignment: .leading)
            Text(Card.money(symbol, Card.count(reveal, balance)))
                .font(.system(size: 34))
                .monospacedDigit()
                .foregroundStyle(.white)
                .opacity(Card.fade(reveal, 0.08))
                .at(Card.x + 24, top + 56, height: 44, alignment: .leading)
            Canvas { context, _ in
                context.stroke(Card.chart(reveal, 0..<Card.chartGreen), with: .color(Color(hex: 0x3BAA5C)), lineWidth: 2)
                context.stroke(Card.chart(reveal, Card.chartGreen..<Card.chartLines), with: .color(Color(hex: 0x8A9099)), style: StrokeStyle(lineWidth: 2, dash: [2, 3]))
            }
            .at(Card.x, top + 110, width: Card.width, height: 100)
            ForEach(Array(Card.spends.enumerated()), id: \.offset) { index, spend in
                let from = 0.3 + Double(index) * 0.08
                // The row slides up into place and its bar grows from the left.
                let shown = M.smooth(reveal, from, from + 0.3)
                HStack(alignment: .top) {
                    VStack(alignment: .leading, spacing: 5) {
                        Text(spend.name).frame(height: 18)
                        Capsule()
                            .fill(Card.ink)
                            .frame(width: spend.bar, height: 3)
                            .scaleEffect(x: M.smooth(reveal, from + 0.1, from + 0.5), anchor: .leading)
                    }
                    Spacer()
                    Text(Card.money(symbol, spend.amount)).frame(height: 18)
                }
                .font(.system(size: 13, weight: .medium))
                .monospacedDigit()
                .foregroundStyle(Card.ink)
                .padding(.top, 6)
                .frame(width: Card.width - 48, height: Card.spendRow, alignment: .top)
                .opacity(shown)
                .offset(x: Card.x + 24, y: top + hero.h + 10 + Double(index) * Card.spendRow + 8 * (1 - shown))
            }
            Caption(text: text, color: Color(hex: 0x4D423B), mute: Color(hex: 0x4D423B))
        }
        .frame(width: S.width, height: S.height, alignment: .topLeading)
    }
}

/// A right arrow in a 16 × 14 box.
struct OrbitCoinTourArrow: Shape {
    func path(in rect: CGRect) -> Path {
        var path = Path()
        path.move(to: CGPoint(x: rect.minX + 2, y: rect.minY + 7))
        path.addLine(to: CGPoint(x: rect.minX + 14, y: rect.minY + 7))
        path.move(to: CGPoint(x: rect.minX + 9, y: rect.minY + 2))
        path.addLine(to: CGPoint(x: rect.minX + 14, y: rect.minY + 7))
        path.addLine(to: CGPoint(x: rect.minX + 9, y: rect.minY + 12))
        return path
    }
}

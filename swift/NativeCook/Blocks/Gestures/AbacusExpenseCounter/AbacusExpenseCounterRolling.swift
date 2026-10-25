import SwiftUI

/*
 Rolling amounts, meters and the week strip for the abacus screen. An amount is a row of digit
 wheels (0–9 and a second 0), each rolled by a spring when its digit changes, forward when the
 amount grows and backward when it shrinks, the lower wheels first (25 ms apart). Leading zeros
 fade out and the "$" slides in after them. Their inputs are whole cents, which change only when
 a bead crosses the middle of its rod, so nothing here re-evaluates while beads move.
 */

private func abacusDigit(_ cents: Int, _ place: Int) -> Int {
    (max(0, cents) / Int(pow(10, Double(place)))) % 10
}

/// Translates the digit column by the wheel's position, wrapped to 0–10, while it springs.
private struct AbacusExpenseCounterRoll: GeometryEffect {
    var pos: Double
    let line: Double

    var animatableData: Double {
        get { pos }
        set { pos = newValue }
    }

    func effectValue(size: CGSize) -> ProjectionTransform {
        let p = (pos.truncatingRemainder(dividingBy: 10) + 10).truncatingRemainder(dividingBy: 10)
        return ProjectionTransform(CGAffineTransform(translationX: 0, y: -p * line))
    }
}

private struct AbacusExpenseCounterWheel: View {
    let cents: Int
    let place: Int
    let order: Int
    let line: Double
    let width: Double
    let font: Font
    let color: Color
    let reduced: Bool

    @State private var pos: Double

    init(cents: Int, initial: Int, place: Int, order: Int, line: Double, width: Double, font: Font, color: Color, reduced: Bool) {
        self.cents = cents
        self.place = place
        self.order = order
        self.line = line
        self.width = width
        self.font = font
        self.color = color
        self.reduced = reduced
        _pos = State(initialValue: Double(abacusDigit(initial, place)))
    }

    var body: some View {
        let visible = place <= 2 || cents >= Int(pow(10, Double(place)))
        VStack(spacing: 0) {
            ForEach(0..<11, id: \.self) { i in
                Text("\(i % 10)")
                    .font(font)
                    .foregroundStyle(color)
                    .frame(width: width, height: line)
            }
        }
        .modifier(AbacusExpenseCounterRoll(pos: pos, line: line))
        .frame(width: width, height: line, alignment: .top)
        .clipped()
        // Leading zeros fade (never the units of dollars).
        .opacity(visible ? 1 : 0)
        .animation(reduced ? nil : .easeInOut(duration: 0.2), value: visible)
        .onChange(of: cents) { before, v in
            let from = ((Int(pos.rounded()) % 10) + 10) % 10
            let to = abacusDigit(v, place)
            if from == to { return }
            let delta = v > before ? (to - from + 10) % 10 : -((from - to + 10) % 10)
            let target = pos.rounded() + Double(delta)
            if reduced {
                pos = target
            } else {
                withAnimation(.spring(duration: 0.52, bounce: 0.22).delay(Double(order) * 0.025)) { pos = target }
            }
        }
    }
}

struct AbacusExpenseCounterAmount: View {
    /// The amount in cents, and its value when the screen first appears.
    let cents: Int
    let initial: Int
    let size: Double
    let color: Color
    var weight: Font.Weight = .bold
    /// Whole-dollar digits shown (leading zeros fade).
    var intDigits = 3
    var reduced = false

    var body: some View {
        let line = (size * 1.18).rounded()
        let width = (size * 0.6).rounded()
        let font = Font.system(size: size, weight: weight).monospacedDigit()
        // The "$" moves right by one wheel for every leading zero that is hidden.
        let hidden = (3...(intDigits + 1)).reduce(0) { n, p in cents < Int(pow(10, Double(p))) ? n + 1 : n }
        HStack(alignment: .top, spacing: 0) {
            Text("$")
                .font(font)
                .foregroundStyle(color)
                .frame(height: line)
                .offset(x: Double(hidden) * width)
                .animation(reduced ? nil : .easeInOut(duration: 0.22), value: hidden)
                .zIndex(1)
            ForEach(Array(stride(from: intDigits + 1, through: 2, by: -1)), id: \.self) { p in
                AbacusExpenseCounterWheel(cents: cents, initial: initial, place: p, order: p, line: line, width: width, font: font, color: color, reduced: reduced)
            }
            Text(".")
                .font(font)
                .foregroundStyle(color)
                .frame(width: (size * 0.3).rounded(), height: line)
            AbacusExpenseCounterWheel(cents: cents, initial: initial, place: 1, order: 1, line: line, width: width, font: font, color: color, reduced: reduced)
            AbacusExpenseCounterWheel(cents: cents, initial: initial, place: 0, order: 0, line: line, width: width, font: font, color: color, reduced: reduced)
        }
        .fixedSize()
        .accessibilityElement()
        .accessibilityLabel(String(format: "$%.2f", Double(cents) / 100))
    }
}

/// A thin meter whose fill springs to `value` (0–1).
struct AbacusExpenseCounterMeter: View {
    let value: Double
    let color: Color
    var height = 4.0
    var reduced = false

    var body: some View {
        let v = max(0.001, min(1, value))
        Capsule()
            .fill(color)
            .scaleEffect(x: v, y: 1, anchor: .leading)
            .animation(reduced ? nil : .spring(duration: 0.6, bounce: 0.2), value: v)
            .frame(height: height)
            .frame(maxWidth: .infinity)
            .background(Capsule().fill(Color(hex: 0xF4ECE0, opacity: 0.1)))
            .clipShape(Capsule())
    }
}

/// A translucent card with a hairline edge, a top highlight and a soft drop shadow.
struct AbacusExpenseCounterCard: ViewModifier {
    func body(content: Content) -> some View {
        let shape = RoundedRectangle(cornerRadius: 18, style: .continuous)
        content
            .background(shape.fill(Color(hex: 0xF4ECE0, opacity: 0.05)))
            .overlay(
                shape.strokeBorder(
                    LinearGradient(colors: [.white.opacity(0.05), .clear], startPoint: .top, endPoint: UnitPoint(x: 0.5, y: 0.08)),
                    lineWidth: 1
                )
            )
            .overlay(shape.strokeBorder(Color(hex: 0xF4ECE0, opacity: 0.1), lineWidth: 0.5))
            .background(shape.fill(Color(hex: 0x16110D)).shadow(color: .black.opacity(0.25), radius: 12, y: 8))
    }
}

/*
 The week strip under the abacus: one bar per day. Monday to Friday are spent and fixed (they add
 up to the abacus' resting total, $386.40); Saturday is today and grows with whatever the beads
 log beyond that (a spring on scaleY, from the bottom); Sunday is still to come (an outline).
 */
struct AbacusExpenseCounterWeek: View {
    static let height = 132.0
    /// Cents spent Monday to Friday; their sum is the abacus' resting total.
    private static let past = [6210, 4830, 9140, 5500, 12960]
    private static let days = ["M", "T", "W", "T", "F", "S", "S"]
    private static let scale = 14000.0
    private static let barH = 56.0

    let total: Int
    let base: Int
    let color: Color
    let reduced: Bool

    var body: some View {
        let ink = Color(hex: 0xF4ECE0)
        let inkSoft = Color(hex: 0xAD9E8E)
        let today = max(0, total - base)
        let v = max(0.02, min(1, Double(today) / Self.scale))
        VStack(spacing: 10) {
            HStack {
                Text("This week")
                    .font(.system(size: 14, weight: .semibold))
                    .foregroundStyle(ink)
                Spacer(minLength: 0)
                HStack(spacing: 8) {
                    Text("Today")
                        .font(.system(size: 12, weight: .medium))
                        .foregroundStyle(inkSoft)
                    AbacusExpenseCounterAmount(cents: today, initial: 0, size: 15, color: ink, weight: .semibold, reduced: reduced)
                }
            }
            .frame(height: 18)
            HStack(spacing: 10) {
                ForEach(0..<7, id: \.self) { i in
                    VStack(spacing: 4) {
                        slot(i, v: v)
                        Text(Self.days[i])
                            .font(.system(size: 11, weight: .semibold))
                            .foregroundStyle(i == 5 ? color : inkSoft)
                    }
                    .frame(maxWidth: .infinity)
                }
            }
            .frame(maxHeight: .infinity, alignment: .top)
        }
        .padding(.horizontal, 16)
        .padding(.top, 12)
        .padding(.bottom, 10)
        .frame(height: Self.height)
        .modifier(AbacusExpenseCounterCard())
    }

    @ViewBuilder private func slot(_ i: Int, v: Double) -> some View {
        let shape = RoundedRectangle(cornerRadius: 6, style: .circular)
        ZStack(alignment: .bottom) {
            if i == 6 {
                shape.strokeBorder(Color(hex: 0xF4ECE0, opacity: 0.18), style: StrokeStyle(lineWidth: 1, dash: [3, 3]))
            } else {
                shape.fill(Color(hex: 0xF4ECE0, opacity: 0.04))
            }
            if i < 5 {
                shape.fill(Color(hex: 0xF4ECE0, opacity: 0.22))
                    .frame(height: (Double(Self.past[i]) / Self.scale * Self.barH).rounded())
            }
            if i == 5 {
                shape.fill(color)
                    .scaleEffect(x: 1, y: v, anchor: .bottom)
                    .animation(reduced ? nil : .spring(duration: 0.6, bounce: 0.25), value: v)
            }
        }
        .frame(maxWidth: .infinity)
        .frame(height: Self.barH)
        .clipShape(shape)
    }
}

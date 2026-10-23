import SwiftUI

/// White budget card: the month chip, the monthly limit, a thin aqua-to-ink bar, the amounts spent
/// and left, and a dark "View More" pill. The card's parts come in one after another, the amounts
/// roll like odometers while the bar grows, and a bead of light runs along the filled part.
struct BudgetBar: View {
    /// Controlled share of the budget spent, 0…1.
    var progress: Double? = nil
    /// Lays the card out part by part, counts up to the amount shown in the reference, holds, and starts over.
    var autoPlay = false
    /// The monthly limit in whole dollars: the spent and remaining amounts are derived from it.
    var budget = 8000
    var title = "Monthly Budget"
    var subtitle = "Monthly Spending Limit"
    var month = "SEP"
    var spentLabel = "Spent"
    var remainingLabel = "Budget Remaining"
    var buttonLabel = "View More"
    var onMonthPress: (() -> Void)? = nil
    var onPress: (() -> Void)? = nil

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    /// Origin of the ambient clock (breathing glow, bead of light).
    @State private var epoch = Date.now
    /// When the current autoPlay / tapped run started; `nil` shows the resting frame.
    @State private var runStart: Date? = nil
    @State private var held = Tween(Metrics.rest)
    /// Keeps the timeline running for a controlled change under reduced motion.
    @State private var settling = false
    @State private var roller = Roller()

    /// One odometer column: where it has turned to (0…10) and how far it is unfolded (0…1).
    private struct Column {
        var position: Double
        var shown: Double
    }

    /// Per-frame state of the rolling digits; index 0 is the ones column.
    private final class Roller {
        var date: Date? = nil
        var before = Metrics.rest
        var spent: [Column] = []
        var left: [Column] = []

        func advance(to now: Date, value: Double, spent spentAmount: Int, left leftAmount: Int, places: Int, live: Bool) {
            let dt = min(max(now.timeIntervalSince(date ?? now), 0), 0.064)
            let first = date == nil || spent.count != places
            date = now
            let step = value - before
            before = value
            // The first frame, and a jump (a run starting over), show the amounts in place instead of rolling to them.
            if first || !live || abs(step) > 0.1 {
                spent = (0..<places).map { Column(position: Double(digit(spentAmount, $0)), shown: needed(spentAmount, $0)) }
                left = (0..<places).map { Column(position: Double(digit(leftAmount, $0)), shown: needed(leftAmount, $0)) }
                return
            }
            // 1 while the amount is rising, -1 while it falls, 0 at rest: the way the columns turn.
            let trend = step > 0.00002 ? 1 : step < -0.00002 ? -1 : 0
            for place in 0..<places {
                turn(&spent[place], to: spentAmount, place: place, trend: trend, dt: dt)
                turn(&left[place], to: leftAmount, place: place, trend: -trend, dt: dt)
            }
        }

        private func turn(_ column: inout Column, to amount: Int, place: Int, trend: Int, dt: Double) {
            column.shown += (needed(amount, place) - column.shown) * min(1, dt * 12)
            let ahead = wrap(Double(digit(amount, place)) - column.position)
            // Turn the way the amount is going; at rest take the short way round.
            var delta = ahead
            if trend < 0 ? ahead > 0.5 : trend == 0 ? ahead > 5 : ahead > 9.5 { delta = ahead - 10 }
            if abs(delta) < 0.004 {
                column.position = wrap(column.position + delta)
                return
            }
            // Follow with a speed limit, so a fast count reads as a spinning column that then settles.
            let limit = Metrics.rollSpeed * dt
            column.position = wrap(column.position + max(-limit, min(limit, delta * min(1, dt * 16))))
        }
    }

    private var controlled: Bool { progress != nil }
    private var target: Double { min(1, max(0, progress ?? Metrics.rest)) }
    private var places: Int { String(budget).count }

    var body: some View {
        TimelineView(.animation(paused: reduceMotion && !settling)) { timeline in
            let date = timeline.date
            let time = reduceMotion ? 0 : date.timeIntervalSince(epoch)
            let t = clock(at: date)
            let value = controlled ? held.value(at: date) : loopValue(t)
            let spent = Int((Double(budget) * clamp01(value)).rounded())
            let left = budget - spent
            let _ = roller.advance(to: date, value: value, spent: spent, left: left, places: places, live: !reduceMotion)

            ZStack {
                Canvas { context, _ in
                    let b = Metrics.bleed
                    var shadow = context
                    shadow.addFilter(.blur(radius: 16))
                    shadow.fill(
                        Path(
                            roundedRect: CGRect(x: b + 6, y: b + 14, width: Metrics.width - 12, height: Metrics.height - 10),
                            cornerRadius: Metrics.radius, style: .circular
                        ),
                        with: .color(.black.opacity(0.07))
                    )
                    context.fill(
                        Path(
                            roundedRect: CGRect(x: b, y: b, width: Metrics.width, height: Metrics.height),
                            cornerRadius: Metrics.radius, style: .circular
                        ),
                        with: .color(.white)
                    )
                }
                .padding(-Metrics.bleed)
                .allowsHitTesting(false)

                Group {
                    label(title, size: 10.8, color: Color(hex: 0xa1a1a1), tracking: -0.2, x: 22.4, y: 33.4)
                    chip
                }
                .part(t, 0)

                label(money(budget).replacingOccurrences(of: "$", with: "$ "), size: 28, weight: .semibold, color: Metrics.ink, tracking: -0.4, x: 22.4, y: 62.9)
                    .accessibilityLabel("\(title), \(money(budget))")
                    .accessibilityValue("\(spentLabel) \(money(spent)), \(remainingLabel) \(money(left))")
                    .part(t, 1)

                label(subtitle, size: 13, color: Color(hex: 0xa3a3a3), tracking: -0.2, x: 22.4, y: 95.1)
                    .part(t, 2)

                Canvas { context, _ in
                    drawBar(context, value: value, time: time)
                }
                .frame(width: Metrics.barWidth + Metrics.barBleed * 2, height: Metrics.barHeight + Metrics.barBleed * 2)
                .position(x: Metrics.barX + Metrics.barWidth / 2, y: Metrics.barY + Metrics.barHeight / 2)
                .allowsHitTesting(false)
                .part(t, 3)

                Group {
                    label(spentLabel, size: 10.8, color: Color(hex: 0xa1a1a1), tracking: -0.2, x: 22.4, y: 134.4)
                    label(remainingLabel, size: 10.8, color: Color(hex: 0xa1a1a1), tracking: -0.2, x: 297.6, y: 134.4, trailing: true)
                    rolling(roller.spent)
                        .frame(width: 140, alignment: .leading)
                        .position(x: 22.4 + 70, y: 149)
                    rolling(roller.left)
                        .frame(width: 140, alignment: .trailing)
                        .position(x: 297.6 - 70, y: 149)
                }
                .part(t, 4)

                button
                    .part(t, 5)
            }
            .lineLimit(1)
            .frame(width: Metrics.width, height: Metrics.height)
        }
        .contentShape(RoundedRectangle(cornerRadius: Metrics.radius))
        .onTapGesture { restart() }
        .onAppear { sync() }
        .onChange(of: autoPlay) { sync() }
        .onChange(of: reduceMotion) { sync() }
        .onChange(of: progress) {
            sync()
            guard controlled else { return }
            held.retarget(target, duration: reduceMotion ? 0.2 : 1.4, curve: Ease.out)
            settling = true
        }
        .task(id: settling) {
            guard settling else { return }
            try? await Task.sleep(for: .seconds(1.5))
            settling = false
        }
    }

    private func label(
        _ text: String, size: Double, weight: Font.Weight = .regular, color: Color, tracking: Double,
        x: Double, y: Double, trailing: Bool = false
    ) -> some View {
        Text(text)
            .font(.system(size: size, weight: weight))
            .tracking(tracking)
            .foregroundStyle(color)
            .fixedSize()
            .frame(width: 260, alignment: trailing ? .trailing : .leading)
            .position(x: trailing ? x - 130 : x + 130, y: y)
            .allowsHitTesting(false)
    }

    /// A dollar amount whose digits roll like an odometer.
    private func rolling(_ columns: [Column]) -> some View {
        HStack(spacing: 0) {
            Text("$").padding(.trailing, 0.6)
            ForEach(Array(columns.indices.reversed()), id: \.self) { place in
                let column = columns[place]
                let whole = Int(column.position.rounded(.down))
                let part = column.position - Double(whole)
                ZStack {
                    Text("\(whole % 10)").offset(y: -Metrics.digitHeight * part)
                    Text("\((whole + 1) % 10)").offset(y: Metrics.digitHeight * (1 - part))
                }
                .monospacedDigit()
                .frame(width: Metrics.digitWidth, height: Metrics.digitHeight)
                .frame(width: Metrics.digitWidth * column.shown, height: Metrics.digitHeight, alignment: .leading)
                .clipped()
                .opacity(column.shown)
                if place > 0, place % 3 == 0 {
                    Text(",")
                        .frame(width: 3.6, height: Metrics.digitHeight)
                        .frame(width: 3.6 * column.shown, alignment: .leading)
                        .clipped()
                        .opacity(column.shown)
                }
            }
        }
        .font(.system(size: 13.2))
        .foregroundStyle(Metrics.ink)
        .fixedSize()
        .accessibilityHidden(true)
    }

    private var chip: some View {
        Button {
            onMonthPress?()
        } label: {
            ZStack {
                Canvas { context, _ in
                    let b = Metrics.chipBleed
                    var shadow = context
                    shadow.addFilter(.blur(radius: 4.5))
                    shadow.fill(
                        Path(roundedRect: CGRect(x: b + 2, y: b + 5, width: Metrics.chipWidth - 4, height: Metrics.chipHeight - 3), cornerRadius: 6, style: .circular),
                        with: .color(.black.opacity(0.3))
                    )
                    context.fill(
                        Path(roundedRect: CGRect(x: b, y: b, width: Metrics.chipWidth, height: Metrics.chipHeight), cornerRadius: 6, style: .circular),
                        with: .linearGradient(
                            Gradient(colors: [Color(hex: 0x282828), Color(hex: 0x3d3d3d)]),
                            startPoint: CGPoint(x: 0, y: b), endPoint: CGPoint(x: 0, y: b + Metrics.chipHeight)
                        )
                    )
                    var chevron = Path()
                    chevron.move(to: CGPoint(x: b + 43.4 - 6.3, y: b + 11.2 - 3.2))
                    chevron.addLine(to: CGPoint(x: b + 43.4, y: b + 11.2 + 3.3))
                    chevron.addLine(to: CGPoint(x: b + 43.4 + 6.3, y: b + 11.2 - 3.2))
                    context.stroke(chevron, with: .color(.white), style: StrokeStyle(lineWidth: 1.3, lineCap: .round, lineJoin: .round))
                }
                .padding(-Metrics.chipBleed)

                Text(month)
                    .font(.system(size: 10.4, weight: .medium))
                    .foregroundStyle(.white)
                    .fixedSize()
                    .frame(width: 30, alignment: .leading)
                    .position(x: 8.7 + 15, y: 11)
            }
            .frame(width: Metrics.chipWidth, height: Metrics.chipHeight)
            .contentShape(Rectangle())
        }
        .buttonStyle(PressStyle(scale: 0.95))
        .accessibilityLabel("Month, \(month)")
        .position(x: Metrics.chipX + Metrics.chipWidth / 2, y: Metrics.chipY + Metrics.chipHeight / 2)
    }

    private var button: some View {
        Button {
            onPress?()
        } label: {
            ZStack {
                Canvas { context, _ in
                    let b = Metrics.buttonBleed
                    let w = Metrics.buttonWidth
                    let h = Metrics.buttonHeight
                    var shadow = context
                    shadow.addFilter(.blur(radius: 6))
                    shadow.fill(capsule(CGRect(x: b + 8, y: b + 9, width: w - 16, height: h - 4)), with: .color(.black.opacity(0.3)))
                    context.fill(
                        capsule(CGRect(x: b, y: b, width: w, height: h)),
                        with: .linearGradient(
                            Gradient(colors: [Color(hex: 0x242424), Color(hex: 0x434343)]),
                            startPoint: CGPoint(x: 0, y: b), endPoint: CGPoint(x: 0, y: b + h)
                        )
                    )
                    // A hairline that is dark along the top and light along the bottom, like a pressed rim.
                    context.stroke(
                        capsule(CGRect(x: b + 0.6, y: b + 0.6, width: w - 1.2, height: h - 1.2)),
                        with: .linearGradient(
                            Gradient(stops: [
                                .init(color: .rgba(120, 120, 120, 0.55), location: 0),
                                .init(color: .rgba(120, 120, 120, 0.15), location: 0.5),
                                .init(color: .rgba(170, 170, 170, 0.7), location: 1),
                            ]),
                            startPoint: CGPoint(x: 0, y: b), endPoint: CGPoint(x: 0, y: b + h)
                        ),
                        lineWidth: 1.2
                    )
                }
                .padding(-Metrics.buttonBleed)

                Text(buttonLabel)
                    .font(.system(size: 17, weight: .medium))
                    .tracking(-0.3)
                    .foregroundStyle(.white)
            }
            .frame(width: Metrics.buttonWidth, height: Metrics.buttonHeight)
            .contentShape(Capsule())
        }
        .buttonStyle(PressStyle(scale: 0.97))
        .position(x: Metrics.buttonX + Metrics.buttonWidth / 2, y: Metrics.buttonY + Metrics.buttonHeight / 2)
    }

    private struct PressStyle: ButtonStyle {
        let scale: Double

        func makeBody(configuration: Configuration) -> some View {
            configuration.label
                .scaleEffect(configuration.isPressed ? scale : 1)
                .animation(.timingCurve(0.23, 1, 0.32, 1, duration: 0.13), value: configuration.isPressed)
                .sensoryFeedback(.impact(weight: .light), trigger: configuration.isPressed) { _, pressed in pressed }
        }
    }

    private func sync() {
        runStart = autoPlay && !controlled && !reduceMotion ? .now : nil
        if controlled, held.duration == 0 { held = Tween(target) }
        // A new run starts with its amounts in place instead of rolling to them.
        if !controlled { roller.date = nil }
    }

    private func restart() {
        guard !controlled, !reduceMotion else { return }
        runStart = .now
        roller.date = nil
    }

    /// Position in the autoPlay loop, in seconds.
    private func clock(at date: Date) -> Double {
        guard let runStart else { return Metrics.restTime }
        let elapsed = date.timeIntervalSince(runStart)
        // A tapped, one-off run stops at the resting frame.
        return autoPlay ? elapsed.truncatingRemainder(dividingBy: Metrics.loop) : min(elapsed, Metrics.restTime)
    }

    private func drawBar(_ context: GraphicsContext, value: Double, time: Double) {
        let x = Metrics.barBleed
        let y = Metrics.barBleed
        let height = Metrics.barHeight
        let ambient = reduceMotion ? 0.0 : 1.0
        let width = max(height, Metrics.barWidth * clamp01(value))
        let fill = capsule(CGRect(x: x, y: y, width: width, height: height))

        context.fill(capsule(CGRect(x: x, y: y, width: Metrics.barWidth, height: height)), with: .color(Color(hex: 0xd1d1d1)))
        // The aqua half of the bar has a faint glow that breathes.
        var glow = context
        glow.addFilter(.blur(radius: 4.5))
        glow.fill(
            capsule(CGRect(x: x, y: y + 1, width: width * 0.62, height: height)),
            with: .color(Color(hex: 0x19e9ff, opacity: 0.3 + ambient * 0.12 * sin(time * 1.7)))
        )
        context.fill(
            fill,
            with: .linearGradient(
                Gradient(stops: [
                    .init(color: Color(hex: 0x00f6ff), location: 0),
                    .init(color: Color(hex: 0x2ee9ff), location: 0.3),
                    .init(color: Color(hex: 0x79d3fb), location: 0.48),
                    .init(color: Color(hex: 0x4a82a6), location: 0.74),
                    .init(color: Color(hex: 0x23262b), location: 1),
                ]),
                startPoint: CGPoint(x: x, y: y), endPoint: CGPoint(x: x + width, y: y)
            )
        )
        // A bead of light runs along the filled part every few seconds.
        let beadAt = time.truncatingRemainder(dividingBy: 3.4) / 1.2
        if ambient > 0, beadAt < 1 {
            var bead = context
            bead.clip(to: fill)
            bead.addFilter(.blur(radius: 2))
            bead.fill(
                Path(roundedRect: CGRect(x: x - 16 + (width + 16) * beadAt, y: y + 0.8, width: 16, height: height - 1.6), cornerRadius: 2.4, style: .circular),
                with: .color(.white.opacity(0.85 * sin(.pi * beadAt)))
            )
        }
    }
}

private extension View {
    /// Part `index` of the card comes in at `t` seconds into the loop, and all parts fade out before it starts over.
    func part(_ t: Double, _ index: Int) -> some View {
        let e = entered(t, index)
        return opacity(e * leaving(t)).offset(y: 9 * (1 - e))
    }
}

private enum Metrics {
    static let width = 320.0
    static let height = 237.0
    static let radius = 25.0
    /// The card's canvas overflows it by this much on every side to fit its shadow.
    static let bleed = 40.0

    static let barX = 21.9
    static let barY = 109.7
    static let barWidth = 276.2
    static let barHeight = 6.4
    /// Room around the bar's own canvas for its glow.
    static let barBleed = 12.0

    static let buttonX = 21.0
    static let buttonY = 165.7
    static let buttonWidth = 278.0
    static let buttonHeight = 50.3
    static let buttonBleed = 16.0

    static let chipX = 240.5
    static let chipY = 21.9
    static let chipWidth = 57.6
    static let chipHeight = 22.0
    static let chipBleed = 12.0

    // Rolling digits: the size of one cell, and how fast a column may turn (digits per second).
    static let digitWidth = 8.0
    static let digitHeight = 15.0
    static let rollSpeed = 16.0

    static let ink = Color(hex: 0x2b2b2b)

    // The reference shows $2,886 of $8,000 spent; the autoPlay loop built around it (seconds).
    static let rest = 2886.0 / 8000.0
    static let low = 0.08
    static let enter = 0.9
    static let fill = 2.6
    static let hold = 3.4
    static let exit = 0.5
    static let loop = enter + fill + hold + exit
    /// A moment in the loop where everything is at rest at the reference value: used when nothing is playing.
    static let restTime = enter + fill + 1.4
}

private func clamp01(_ t: Double) -> Double { min(1, max(0, t)) }

private func easeInOutCubic(_ t: Double) -> Double {
    t < 0.5 ? 4 * t * t * t : 1 - pow(-2 * t + 2, 3) / 2
}

private func easeOutCubic(_ t: Double) -> Double { 1 - pow(1 - clamp01(t), 3) }

/// Share of the budget spent at `t` seconds into the autoPlay loop.
private func loopValue(_ t: Double) -> Double {
    if t < Metrics.enter { return Metrics.low }
    if t < Metrics.enter + Metrics.fill {
        return Metrics.low + (Metrics.rest - Metrics.low) * easeInOutCubic((t - Metrics.enter) / Metrics.fill)
    }
    return Metrics.rest
}

/// 0…1: how far part `index` of the card has come in at `t` seconds into the loop.
private func entered(_ t: Double, _ index: Int) -> Double {
    easeOutCubic((t - 0.08 - 0.075 * Double(index)) / 0.55)
}

/// The card's parts fade out together just before the loop starts over.
private func leaving(_ t: Double) -> Double {
    t > Metrics.loop - Metrics.exit ? clamp01((Metrics.loop - t) / (Metrics.exit * 0.8)) : 1
}

private func digit(_ amount: Int, _ place: Int) -> Int {
    (amount / Int(pow(10, Double(place)))) % 10
}

/// Leading columns collapse while the amount is too small to need them.
private func needed(_ amount: Int, _ place: Int) -> Double {
    place == 0 || amount >= Int(pow(10, Double(place))) ? 1 : 0
}

private func wrap(_ position: Double) -> Double {
    let r = position.truncatingRemainder(dividingBy: 10)
    return r < 0 ? r + 10 : r
}

private func money(_ amount: Int) -> String {
    let formatter = NumberFormatter()
    formatter.numberStyle = .decimal
    formatter.groupingSeparator = ","
    formatter.usesGroupingSeparator = true
    return "$" + (formatter.string(from: NSNumber(value: amount)) ?? "\(amount)")
}

#Preview {
    ZStack {
        Color(hex: 0xfdfdfd).ignoresSafeArea()
        BudgetBar(autoPlay: true)
    }
}

import SwiftUI

/// Dark glass "today spending" tile wrapped by a thin white orbit: the amount spent, its share of
/// the budget, a bar of coloured ticks split by category and the category list. The ticks light
/// in a wave, each one stretching as the fill reaches it, while the amounts count up row by row
/// and each category bleeds its colour into the glass; the two orbiting arcs grow and turn once
/// around the tile, overshoot as they land with a flash of their bloom, and at rest a glint keeps
/// running along the arcs and through the lit ticks.
struct SpendingOrbit: View {
    /// Controlled share of the daily budget spent, 0…1. Leave `nil` to show the reference's 78% (or the autoPlay loop).
    var progress: Double? = nil
    /// Fills by itself from nothing to 78%, holds, empties and starts over.
    var autoPlay = false
    var title = "TODAY SPENDING"

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    /// Origin of the ambient clock (glints).
    @State private var epoch = Date.now
    /// When the current autoPlay / tapped run started; `nil` shows the resting frame.
    @State private var runStart: Date? = nil
    @State private var held = Tween(Metrics.rest)
    /// When a controlled value last changed.
    @State private var movedAt = Date.distantPast
    /// Keeps the timeline running for a controlled change under reduced motion.
    @State private var settling = false

    private var controlled: Bool { progress != nil }
    private var target: Double { clamp01(progress ?? Metrics.rest) }

    var body: some View {
        TimelineView(.animation(paused: reduceMotion && !settling)) { timeline in
            let date = timeline.date
            let time = reduceMotion ? 0 : date.timeIntervalSince(epoch)
            let clock = clock(at: date)
            let value = clamp01(controlled ? held.value(at: date) : loopValue(clock))
            let total = value * Metrics.budget
            let percent = Int((value * 100).rounded())

            ZStack {
                Canvas { context, _ in
                    draw(
                        context, value: value, total: total, time: time,
                        activity: activity(at: date, clock: clock), landed: landed(at: date, clock: clock)
                    )
                }
                .padding(-Metrics.bleed)

                content(total: total, percent: percent)
            }
            .frame(width: Metrics.size, height: Metrics.size)
            .accessibilityElement(children: .ignore)
            .accessibilityLabel("\(title), \(money(total)) spent")
            .accessibilityValue("\(percent)%")
        }
        .contentShape(Circle())
        .onTapGesture { restart() }
        .onAppear { sync() }
        .onChange(of: autoPlay) { sync() }
        .onChange(of: reduceMotion) { sync() }
        .onChange(of: progress) {
            sync()
            guard controlled else { return }
            movedAt = .now
            held.retarget(target, duration: reduceMotion ? 0.2 : 0.9, curve: Ease.out)
            settling = true
        }
        .task(id: settling) {
            guard settling else { return }
            try? await Task.sleep(for: .seconds(2))
            settling = false
        }
    }

    private func sync() {
        runStart = autoPlay && !controlled && !reduceMotion ? .now : nil
        if held.duration == 0 { held = Tween(target) }
    }

    private func restart() {
        guard !controlled, !reduceMotion else { return }
        runStart = .now
    }

    /// Position in the autoPlay loop, in seconds.
    private func clock(at date: Date) -> Double {
        guard let runStart else { return Metrics.restTime }
        let elapsed = date.timeIntervalSince(runStart)
        // A tapped, one-off run stops at the resting frame.
        return autoPlay ? elapsed.truncatingRemainder(dividingBy: Metrics.loop) : min(elapsed, Metrics.restTime)
    }

    /// 0…1: how fast the value is moving.
    private func activity(at date: Date, clock: Double) -> Double {
        if reduceMotion { return 0 }
        if !controlled { return loopActivity(clock) }
        let u = date.timeIntervalSince(movedAt) / 0.8
        return u <= 0 || u >= 1 ? 0 : sin(.pi * u)
    }

    /// Seconds since the value landed; large when nothing happened.
    private func landed(at date: Date, clock: Double) -> Double {
        if reduceMotion { return 10 }
        if !controlled { return clock < Metrics.loop - Metrics.reset ? clock - Metrics.landed : 10 }
        return date.timeIntervalSince(movedAt) - 0.7
    }

    private func money(_ amount: Double) -> String { String(format: "$%.2f", amount) }

    /// Whole part and cents of an amount, split so they can be styled apart.
    private func parts(_ amount: Double) -> (String, String) {
        let cents = Int((amount * 100).rounded())
        return ("$\(cents / 100)", "." + String(format: "%02d", cents % 100))
    }

    private func content(total: Double, percent: Int) -> some View {
        let spent = parts(total)
        let mono = Font.system(size: 10.3, weight: .medium, design: .monospaced)
        let dim = Color(hex: 0x808080)

        return ZStack {
            Text(title)
                .font(.system(size: 12.6, weight: .bold, design: .monospaced))
                .kerning(1.5)
                .foregroundStyle(Color(hex: 0x8c8c8c))
                .lineLimit(1)
                .fixedSize()
                .frame(width: 200, alignment: .leading)
                .position(x: Metrics.tileX + Metrics.inset + 100, y: Metrics.tileY + 39.6)

            (Text(spent.0).font(.system(size: 29, weight: .medium)).kerning(-1.3).foregroundStyle(Color(hex: 0xeeeeee))
                + Text(spent.1).font(.system(size: 16, weight: .medium)).kerning(-0.2).foregroundStyle(dim))
                .monospacedDigit()
                .lineLimit(1)
                .fixedSize()
                .alignmentGuide(.top) { $0[.firstTextBaseline] - (Metrics.tileY + 83.2) }
                .alignmentGuide(.leading) { _ in -(Metrics.tileX + Metrics.inset - 1) }
                .frame(width: Metrics.size, height: Metrics.size, alignment: .topLeading)

            Text("\(percent)%")
                .font(.system(size: 16, weight: .medium))
                .kerning(-0.2)
                .monospacedDigit()
                .foregroundStyle(dim)
                .fixedSize()
                .alignmentGuide(.top) { $0[.firstTextBaseline] - (Metrics.tileY + 83.2) }
                .alignmentGuide(.trailing) { $0.width + Metrics.tileX + Metrics.inset }
                .frame(width: Metrics.size, height: Metrics.size, alignment: .topTrailing)

            ForEach(Array(Metrics.categories.enumerated()), id: \.offset) { index, category in
                let amount = categorySpend(total, index)
                let split = parts(amount)
                HStack(spacing: 0) {
                    Text(category.label)
                        .font(mono)
                        .kerning(0.35)
                        .foregroundStyle(Color(hex: 0xececec))
                    Spacer(minLength: 0)
                    (Text(split.0).fontWeight(.semibold).foregroundStyle(Color(hex: 0xf2f2f2))
                        + Text(split.1).foregroundStyle(Color(hex: 0x858585)))
                        .font(mono)
                        .kerning(0.35)
                }
                .lineLimit(1)
                .frame(width: Metrics.tileW - 41.5 - Metrics.inset)
                // A row is dim until its category starts to fill.
                .opacity(0.38 + 0.62 * clamp01(amount / category.amount * 5))
                .position(
                    x: Metrics.tileX + 41.5 + (Metrics.tileW - 41.5 - Metrics.inset) / 2,
                    y: Metrics.rowY + Double(index) * Metrics.rowPitch
                )
            }
        }
        .frame(width: Metrics.size, height: Metrics.size)
    }

    private func draw(_ base: GraphicsContext, value: Double, total: Double, time: Double, activity: Double, landed: Double) {
        let ambient = reduceMotion ? 0.0 : 1.0
        var context = base
        context.translateBy(x: Metrics.bleed, y: Metrics.bleed)
        let c = Metrics.size / 2
        let center = CGPoint(x: c, y: c)
        func circle(_ p: CGPoint, _ r: Double) -> Path {
            Path(ellipseIn: CGRect(x: p.x - r, y: p.y - r, width: r * 2, height: r * 2))
        }
        /// Arc of the orbit, in degrees clockwise from 12 o'clock.
        func orbit(_ from: Double, _ sweep: Double, into path: inout Path) {
            let s = min(359, max(0.01, sweep))
            let a = (from - 90) * .pi / 180
            // Start a new subpath, or the arc would be joined to the previous one by a chord.
            path.move(to: CGPoint(x: c + Metrics.orbit * cos(a), y: c + Metrics.orbit * sin(a)))
            path.addArc(center: center, radius: Metrics.orbit, startAngle: .degrees(from - 90), endAngle: .degrees(from + s - 90), clockwise: false)
        }

        // Orbit: two opposite arcs that grow with the value while the pair turns once per 78%.
        context.stroke(circle(center, Metrics.orbit), with: .color(.white.opacity(0.055)), lineWidth: 2)
        let spin = value / Metrics.rest * 360
        let wobble = landed > 0 && landed < 1.2 ? 7 * exp(-4.5 * landed) * sin(13 * landed) : 0
        let sweep = Metrics.arcAtFull * value + wobble
        var arcs = Path()
        orbit(spin, sweep, into: &arcs)
        orbit(spin + 180, sweep, into: &arcs)
        let arcsAlpha = clamp01(value / 0.02)
        // Their bloom breathes, and flashes as the value lands.
        let flashU = landed / 0.8
        let flash = flashU > 0 && flashU < 1 ? 0.5 * (1 - flashU) : 0
        var bloom = context
        bloom.opacity = arcsAlpha * min(1, 0.5 + 0.12 * ambient * sin(time * 1.4) + 0.3 * activity + flash)
        bloom.drawLayer { layer in
            layer.addFilter(.blur(radius: 7))
            layer.stroke(arcs, with: .color(.white.opacity(0.9)), style: StrokeStyle(lineWidth: 7, lineCap: .round))
        }
        context.stroke(arcs, with: .color(.white.opacity(arcsAlpha)), style: StrokeStyle(lineWidth: 3.7, lineCap: .round))
        // At rest a glint runs along each arc from its tail to its head.
        let glintPhase = time.truncatingRemainder(dividingBy: Metrics.glintPeriod) / 1.3
        let glintAlpha = ambient * (1 - activity) * arcsAlpha * (glintPhase < 1 ? sin(.pi * glintPhase) : 0)
        if glintAlpha > 0 {
            let at = spin + (sweep + 16) * min(1, glintPhase) - 8
            let from = max(spin, at - 9)
            let to = min(spin + sweep, at + 9)
            if to > from {
                var glint = Path()
                orbit(from, to - from, into: &glint)
                orbit(from + 180, to - from, into: &glint)
                var shine = context
                shine.opacity = glintAlpha
                shine.drawLayer { layer in
                    layer.addFilter(.blur(radius: 2.2))
                    layer.stroke(glint, with: .color(.white), style: StrokeStyle(lineWidth: 6.5, lineCap: .round))
                }
            }
        }

        // Glass tile
        let tileRect = CGRect(x: Metrics.tileX, y: Metrics.tileY, width: Metrics.tileW, height: Metrics.tileH)
        let tile = Path(roundedRect: tileRect, cornerRadius: Metrics.tileR, style: .circular)
        context.drawLayer { layer in
            layer.addFilter(.blur(radius: 12))
            layer.fill(tile.offsetBy(dx: 0, dy: 6), with: .color(.black.opacity(0.4)))
        }
        let topRight = CGPoint(x: tileRect.maxX, y: tileRect.minY)
        let bottomLeft = CGPoint(x: tileRect.minX, y: tileRect.maxY)
        context.fill(
            tile,
            with: .linearGradient(
                Gradient(stops: [
                    .init(color: Color(hex: 0x3d3d3d), location: 0),
                    .init(color: Color(hex: 0x232323), location: 0.34),
                    .init(color: Color(hex: 0x1d1d1d), location: 0.68),
                    .init(color: Color(hex: 0x434343), location: 1),
                ]),
                startPoint: topRight, endPoint: bottomLeft
            )
        )
        // Colour that the lit categories bleed into the glass.
        var glass = context
        glass.clip(to: tile)
        let glows: [(Double, Double, Double, Double, Color, Int)] = [
            (38, 132, 62, 30, .rgba(214, 92, 36, 0.13), 0),
            (138, 116, 50, 28, .rgba(48, 84, 196, 0.14), 2),
            (206, 150, 58, 30, .rgba(30, 170, 96, 0.13), 3),
        ]
        for (x, y, r, blur, color, index) in glows {
            let share = clamp01(categorySpend(total, index) / Metrics.categories[index].amount)
            guard share > 0 else { continue }
            var glow = glass
            glow.opacity = share
            glow.drawLayer { layer in
                layer.addFilter(.blur(radius: blur))
                layer.fill(circle(CGPoint(x: Metrics.tileX + x, y: Metrics.tileY + y), r), with: .color(color))
            }
        }
        context.stroke(
            Path(roundedRect: tileRect.insetBy(dx: 0.5, dy: 0.5), cornerRadius: Metrics.tileR - 0.5, style: .circular),
            with: .linearGradient(
                Gradient(colors: [.white.opacity(0.3), .white.opacity(0.12), .white.opacity(0.26)]),
                startPoint: topRight, endPoint: bottomLeft
            ),
            lineWidth: 1
        )

        // Ticks: the ones around the moving head of the fill stretch, and each settles in its category's colour.
        let lit = litTicks(total)
        var resting = Path()
        for index in 0..<Metrics.ticks {
            let x = Metrics.tickX + Double(index) * Metrics.tickPitch
            let near = clamp01(1 - abs(lit - Double(index) - 0.5) / 2.2)
            let h = Metrics.tickH * (1 + 0.45 * activity * near * near * (3 - 2 * near))
            let tick = capsule(CGRect(x: x, y: Metrics.tickY + (Metrics.tickH - h) / 2, width: Metrics.tickW, height: h))
            context.fill(tick, with: .color(.white.opacity(0.2)))
            let on = clamp01(lit - Double(index))
            if on > 0 { context.fill(tick, with: .color(tickColor(index).opacity(on))) }
            resting.addPath(capsule(CGRect(x: x, y: Metrics.tickY, width: Metrics.tickW, height: Metrics.tickH)))
        }
        // A soft highlight that keeps travelling through the lit ticks.
        let shineAlpha = ambient * (1 - activity)
        if shineAlpha > 0 {
            let phase = min(1, (time + 1.2).truncatingRemainder(dividingBy: Metrics.glintPeriod) / 1.5)
            let x = Metrics.tickX - 24 + (Double(Metrics.ticks) * Metrics.tickPitch + 48) * phase
            var shine = context
            shine.opacity = shineAlpha
            shine.clip(to: resting)
            shine.clip(to: Path(CGRect(x: Metrics.tickX - 1, y: Metrics.tickY - 4, width: max(0, lit * Metrics.tickPitch - 1), height: Metrics.tickH + 8)))
            shine.fill(
                Path(CGRect(x: x - 12, y: Metrics.tickY - 2, width: 24, height: Metrics.tickH + 4)),
                with: .linearGradient(
                    Gradient(colors: [.white.opacity(0), .white.opacity(0.75), .white.opacity(0)]),
                    startPoint: CGPoint(x: x - 12, y: 0), endPoint: CGPoint(x: x + 12, y: 0)
                )
            )
        }

        // Row markers: each pops as its category starts to fill.
        for (index, category) in Metrics.categories.enumerated() {
            let share = clamp01(categorySpend(total, index) / category.amount)
            let h = 11.6 * (1 + 0.5 * sin(.pi * clamp01(share * 2.5)))
            let y = Metrics.rowY + Double(index) * Metrics.rowPitch
            context.fill(
                capsule(CGRect(x: Metrics.tileX + 29.4, y: y - h / 2, width: 2.9, height: h)),
                with: .color(category.color.opacity(0.3 + 0.7 * clamp01(share * 5)))
            )
        }
    }
}

private struct Category {
    let label: String
    let amount: Double
    let ticks: Int
    let color: Color
}

private enum Metrics {
    static let size = 290.0
    /// The canvas overflows the widget by this much on every side to fit the orbit's bloom.
    static let bleed = 24.0

    static let tileW = 250.0
    static let tileH = 239.0
    static let tileX = (size - tileW) / 2
    static let tileY = (size - tileH) / 2
    static let tileR = 26.0
    static let inset = 29.9
    static let orbit = 141.8

    static let ticks = 27
    static let tickX = tileX + 29.4
    static let tickY = tileY + 101.2
    static let tickW = 2.9
    static let tickH = 14.0
    static let tickPitch = 7.25

    static let rowY = tileY + 143.7
    static let rowPitch = 20.85

    /// The day's categories as in the reference: their amounts and how many ticks of the bar they take.
    static let categories = [
        Category(label: "Groceries", amount: 78.46, ticks: 11, color: Color(hex: 0xfb6a2c)),
        Category(label: "Entertainment", amount: 56.2, ticks: 6, color: Color(hex: 0x8b5cf6)),
        Category(label: "Transportation", amount: 33.58, ticks: 4, color: Color(hex: 0x3aa5f5)),
        Category(label: "Utilities", amount: 24.21, ticks: 2, color: Color(hex: 0x27d267)),
    ]
    static let spent = 192.45
    static let spentTicks = 23

    /// Value shown in the reference, and the autoPlay loop built around it (seconds).
    static let rest = 0.78
    static let budget = spent / rest
    static let start = 0.45
    static let fill = 2.4
    static let hold = 4.4
    static let reset = 0.8
    static let loop = start + fill + hold + reset
    static let landed = start + fill
    /// A moment in the loop where everything is at rest: used when nothing is playing.
    static let restTime = landed + 1.6
    /// Each of the two orbiting arcs spans a quarter turn at the reference's 78%.
    static let arcAtFull = 90 / rest
    static let glintPeriod = 3.4
}

private func clamp01(_ t: Double) -> Double { min(1, max(0, t)) }

private func easeInOutCubic(_ t: Double) -> Double {
    t < 0.5 ? 4 * t * t * t : 1 - pow(-2 * t + 2, 3) / 2
}

/// Budget share at `t` seconds into the autoPlay loop.
private func loopValue(_ t: Double) -> Double {
    if t < Metrics.start { return 0 }
    if t < Metrics.landed { return Metrics.rest * easeInOutCubic((t - Metrics.start) / Metrics.fill) }
    if t < Metrics.loop - Metrics.reset { return Metrics.rest }
    return Metrics.rest * (1 - easeInOutCubic((t - (Metrics.loop - Metrics.reset)) / Metrics.reset))
}

/// 0…1: how fast the value is moving at `t` seconds into the loop.
private func loopActivity(_ t: Double) -> Double {
    let u = (t - Metrics.start) / Metrics.fill
    return u <= 0 || u >= 1 ? 0 : sin(.pi * u)
}

/// Money spent in category `index` once `total` has been spent in all: the categories fill one after the other.
private func categorySpend(_ total: Double, _ index: Int) -> Double {
    let before = Metrics.categories[..<index].reduce(0) { $0 + $1.amount }
    let own = max(0, total - before)
    // Anything over the reference's total lands in the last category.
    return index == Metrics.categories.count - 1 ? own : min(Metrics.categories[index].amount, own)
}

/// How many ticks of the bar `total` lights, fractional: each category fills its own run of ticks.
private func litTicks(_ total: Double) -> Double {
    var lit = 0.0
    for (index, category) in Metrics.categories.enumerated() {
        lit += Double(category.ticks) * clamp01(categorySpend(total, index) / category.amount)
    }
    return lit + Double(Metrics.ticks - Metrics.spentTicks) * clamp01((total - Metrics.spent) / (Metrics.budget - Metrics.spent))
}

private func tickColor(_ index: Int) -> Color {
    var end = 0
    for category in Metrics.categories {
        end += category.ticks
        if index < end { return category.color }
    }
    return Metrics.categories[Metrics.categories.count - 1].color
}

#Preview {
    ZStack {
        Color(hex: 0x191919).ignoresSafeArea()
        SpendingOrbit(autoPlay: true)
    }
}

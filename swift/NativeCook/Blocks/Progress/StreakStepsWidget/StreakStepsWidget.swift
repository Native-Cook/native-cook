import SwiftUI

/// Dark "streak + steps" widget: a flickering flame with the streak length, the week as a row of
/// ticked discs with today as a ring, and the step count with its percentage over a glowing bar.
/// The count, the percentage, the bar and today's ring all follow `progress`.
struct StreakStepsWidget: View {
    /// Share of the step goal reached, 0…1. Leave nil to show the reference's 6825 steps.
    var progress: Double? = nil
    /// Plays by itself: the week's checks pop in, the steps count up, hold, and start over.
    var autoPlay = false
    var streakDays = 32
    var goal = 10_000
    /// How many days of the week are already ticked; the next one is today and shows a ring.
    var completedDays = 3
    var label = "Steps"
    var action: () -> Void = {}

    @State private var taps = 0

    var body: some View {
        Button {
            taps += 1
            action()
        } label: {
            Color.clear
        }
        .buttonStyle(Style(widget: self, taps: taps))
        .accessibilityRepresentation {
            ProgressView(value: min(max(progress ?? Metrics.rest, 0), 1)) {
                Text("\(label), \(streakDays) day streak")
            }
        }
    }

    private struct Style: ButtonStyle {
        let widget: StreakStepsWidget
        let taps: Int

        func makeBody(configuration: Configuration) -> some View {
            Surface(
                progress: widget.progress, autoPlay: widget.autoPlay, streakDays: widget.streakDays,
                goal: widget.goal, completed: widget.completedDays, taps: taps, isPressed: configuration.isPressed
            )
        }
    }
}

private enum Metrics {
    static let size = 312.0
    static let radius = 62.0
    /// The canvas overflows the widget by this much on every side to fit its shadow.
    static let bleed = 40.0
    static let flame = CGPoint(x: 54, y: 56)
    static let foot = CGPoint(x: 265.5, y: 55.5)
    static let dividerY = 100.5
    static let inset = 30.0
    static let dayX = 45.8
    static let dayGap = 38.9
    static let dayY = 136.5
    static let dayR = 16.3
    static let ringR = 15.1
    static let ringStroke = 2.4
    static let barY = 272.0
    static let barW = size - inset * 2
    static let barH = 11.5
    /// Width of the highlight that travels along the filled bar.
    static let shimmerW = 70.0
    static let shimmerPeriod = 3.2
    static let embers = 4
    static let emberSpeed = 0.55
    static let emberRise = 26.0
    /// The value the reference shows: 6825 of 10,000 steps.
    static let rest = 0.6825
    static let heatGain = 2.2
}

/// The autoPlay run is one clock in seconds; every part is a window on it.
private enum Clock {
    static let checks = 0.3
    static let checkStagger = 0.2
    static let checkLength = 0.45
    static let fill = (0.9, 3.1)
    static let reset = (5.7, 6.2)
    static let total = 6.6
}

private let days = ["Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"]
private let green = Color(hex: 0x47bd29)

private func seg(_ t: Double, _ window: (Double, Double)) -> Double {
    min(1, max(0, (t - window.0) / (window.1 - window.0)))
}

private func easeInOut(_ x: Double) -> Double {
    x < 0.5 ? 4 * x * x * x : 1 - pow(-2 * x + 2, 3) / 2
}

private func easeOutBack(_ x: Double) -> Double {
    let c = 1.7
    return 1 + (c + 1) * pow(x - 1, 3) + c * pow(x - 1, 2)
}

private func disc(_ center: CGPoint, _ radius: Double) -> Path {
    Path(ellipseIn: CGRect(x: center.x - radius, y: center.y - radius, width: radius * 2, height: radius * 2))
}

private let flamePath: Path = {
    var p = Path()
    p.move(to: CGPoint(x: 3, y: -22))
    p.addCurve(to: CGPoint(x: 19, y: 5), control1: CGPoint(x: 12, y: -16), control2: CGPoint(x: 19, y: -6))
    p.addCurve(to: CGPoint(x: 0, y: 22), control1: CGPoint(x: 19, y: 15), control2: CGPoint(x: 11, y: 22))
    p.addCurve(to: CGPoint(x: -19, y: 5), control1: CGPoint(x: -11, y: 22), control2: CGPoint(x: -19, y: 15))
    p.addCurve(to: CGPoint(x: -13, y: -10), control1: CGPoint(x: -19, y: -1), control2: CGPoint(x: -17, y: -6))
    p.addCurve(to: CGPoint(x: -6, y: -3), control1: CGPoint(x: -11, y: -6), control2: CGPoint(x: -9, y: -3))
    p.addCurve(to: CGPoint(x: -2, y: -13), control1: CGPoint(x: -2, y: -3), control2: CGPoint(x: -1, y: -8))
    p.addCurve(to: CGPoint(x: 3, y: -22), control1: CGPoint(x: -2.5, y: -17), control2: CGPoint(x: 0, y: -21))
    p.closeSubpath()
    return p
}()

private let flameCorePath: Path = {
    var p = Path()
    p.move(to: CGPoint(x: -1.5, y: 2))
    p.addCurve(to: CGPoint(x: 3, y: 7.5), control1: CGPoint(x: 0, y: 5), control2: CGPoint(x: 1.5, y: 7))
    p.addCurve(to: CGPoint(x: 5.5, y: 6), control1: CGPoint(x: 4, y: 7.8), control2: CGPoint(x: 5, y: 7))
    p.addCurve(to: CGPoint(x: 7.5, y: 13.5), control1: CGPoint(x: 7, y: 8.5), control2: CGPoint(x: 7.5, y: 11))
    p.addCurve(to: CGPoint(x: 0, y: 21), control1: CGPoint(x: 7.5, y: 18), control2: CGPoint(x: 4, y: 21))
    p.addCurve(to: CGPoint(x: -7.5, y: 13.5), control1: CGPoint(x: -4, y: 21), control2: CGPoint(x: -7.5, y: 18))
    p.addCurve(to: CGPoint(x: -1.5, y: 2), control1: CGPoint(x: -7.5, y: 9), control2: CGPoint(x: -4, y: 6))
    p.closeSubpath()
    return p
}()

private let footPath: Path = {
    var p = Path()
    p.move(to: CGPoint(x: -6.2, y: -1.5))
    p.addCurve(to: CGPoint(x: 0.5, y: -7.3), control1: CGPoint(x: -6.2, y: -5.5), control2: CGPoint(x: -3, y: -7.3))
    p.addCurve(to: CGPoint(x: 7.8, y: 1), control1: CGPoint(x: 5, y: -7.3), control2: CGPoint(x: 7.8, y: -3.5))
    p.addCurve(to: CGPoint(x: 1.2, y: 12), control1: CGPoint(x: 7.8, y: 6), control2: CGPoint(x: 5.5, y: 12))
    p.addCurve(to: CGPoint(x: -2.6, y: 7.5), control1: CGPoint(x: -1.5, y: 12), control2: CGPoint(x: -3, y: 10))
    p.addCurve(to: CGPoint(x: -3.2, y: 3.2), control1: CGPoint(x: -2.3, y: 5.5), control2: CGPoint(x: -1.6, y: 4.6))
    p.addCurve(to: CGPoint(x: -6.2, y: -1.5), control1: CGPoint(x: -5, y: 1.6), control2: CGPoint(x: -6.2, y: 0.5))
    p.closeSubpath()
    return p
}()

private let toes: [(CGPoint, Double)] = [
    (CGPoint(x: -9.5, y: -9.1), 2.75),
    (CGPoint(x: -3.3, y: -11.8), 2.1),
    (CGPoint(x: 3.5, y: -11.8), 2.1),
    (CGPoint(x: 9, y: -9), 1.4),
]

private let checkPath: Path = {
    var p = Path()
    p.move(to: CGPoint(x: -7.5, y: 0))
    p.addLine(to: CGPoint(x: -2.5, y: 5))
    p.addLine(to: CGPoint(x: 7.5, y: -5.5))
    return p
}()

private struct Surface: View {
    let progress: Double?
    let autoPlay: Bool
    let streakDays: Int
    let goal: Int
    let completed: Int
    let taps: Int
    let isPressed: Bool

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var value = Tween(Metrics.rest)
    @State private var settling = false
    @State private var settleToken = 0
    /// When the autoPlay run started; the flame and the shimmer count from here too.
    @State private var start = Date.now
    @State private var epoch = Date.now

    private var playing: Bool { autoPlay && progress == nil && !reduceMotion }

    /// Share of the goal on show at `date`.
    private func shown(at date: Date) -> Double {
        if progress != nil { return value.value(at: date) }
        guard playing else { return Metrics.rest }
        let t = clock(at: date)
        return Metrics.rest * easeInOut(seg(t, Clock.fill)) * (1 - easeInOut(seg(t, Clock.reset)))
    }

    private func clock(at date: Date) -> Double {
        max(date.timeIntervalSince(start), 0).truncatingRemainder(dividingBy: Clock.total)
    }

    var body: some View {
        TimelineView(.animation(paused: reduceMotion && !settling)) { timeline in
            let date = timeline.date
            let p = shown(at: date)
            // How fast the value is moving right now: lights the ring's leading cap.
            let heat = min(1, abs(p - shown(at: date.addingTimeInterval(-0.06))) / 0.06 * Metrics.heatGain)
            Canvas { context, _ in
                draw(
                    context, p: p, heat: heat, t: clock(at: date),
                    time: reduceMotion ? 0 : date.timeIntervalSince(epoch)
                )
            }
            .padding(-Metrics.bleed)
            .frame(width: Metrics.size, height: Metrics.size)
        }
        .contentShape(RoundedRectangle(cornerRadius: Metrics.radius, style: .continuous))
        .scaleEffect(isPressed ? 0.97 : 1)
        .animation(.timingCurve(0.23, 1, 0.32, 1, duration: 0.13), value: isPressed)
        .sensoryFeedback(.impact(weight: .light), trigger: isPressed) { _, pressed in pressed }
        // A tap restarts the demo run.
        .onChange(of: taps) { start = .now }
        .onChange(of: progress, initial: true) { _, progress in
            guard let progress else { return }
            // Value changes keep a short tween even with motion reduced.
            value.retarget(min(max(progress, 0), 1), duration: reduceMotion ? 0.2 : 0.7, curve: Ease.out)
            settling = true
            settleToken += 1
        }
        .task(id: settleToken) {
            try? await Task.sleep(for: .seconds(value.duration + 0.05))
            if !Task.isCancelled { settling = false }
        }
    }

    private func draw(_ context: GraphicsContext, p: Double, heat: Double, t: Double, time: Double) {
        let o = Metrics.bleed
        let size = Metrics.size
        let card = CGRect(x: o, y: o, width: size, height: size)

        var shadow = context
        shadow.addFilter(.blur(radius: 16))
        shadow.fill(
            Path(roundedRect: card.insetBy(dx: 6, dy: 6).offsetBy(dx: 0, dy: 8), cornerRadius: Metrics.radius),
            with: .color(.black.opacity(0.5))
        )
        context.fill(Path(roundedRect: card, cornerRadius: Metrics.radius), with: .color(Color(hex: 0x101010)))
        // A hairline that catches the light along the top edge.
        context.stroke(
            Path(roundedRect: card.insetBy(dx: 0.5, dy: 0.5), cornerRadius: Metrics.radius - 0.5),
            with: .linearGradient(
                Gradient(stops: [
                    .init(color: .white.opacity(0.3), location: 0),
                    .init(color: .white.opacity(0.035), location: 0.3),
                    .init(color: .white.opacity(0.035), location: 0.8),
                    .init(color: .white.opacity(0.1), location: 1),
                ]),
                startPoint: CGPoint(x: 0, y: o), endPoint: CGPoint(x: 0, y: o + size)
            ),
            lineWidth: 1
        )

        drawFlame(context, time: time)

        var foot = context
        foot.translateBy(x: o + Metrics.foot.x, y: o + Metrics.foot.y)
        let footColor = Color(hex: 0xcfcfcf)
        foot.stroke(footPath, with: .color(footColor), style: StrokeStyle(lineWidth: 2.6, lineJoin: .round))
        for (center, radius) in toes {
            foot.fill(disc(center, radius), with: .color(footColor))
        }

        context.fill(
            Path(CGRect(x: o + Metrics.inset + 1, y: o + Metrics.dividerY, width: Metrics.barW - 2, height: 1)),
            with: .color(.white.opacity(0.13))
        )

        for index in days.indices {
            drawDay(context, index: index, p: p, heat: heat, t: t)
        }
        drawBar(context, p: p, time: time)
        drawText(context, p: p)
    }

    /// The flame leans and stretches from its base; the white core flickers faster; sparks rise.
    private func drawFlame(_ context: GraphicsContext, time: Double) {
        let o = Metrics.bleed
        var base = context
        base.translateBy(x: o + Metrics.flame.x, y: o + Metrics.flame.y)

        var glow = base
        glow.opacity = reduceMotion ? 0.72 : 0.72 + 0.14 * sin(time * 2.1)
        glow.addFilter(.blur(radius: 12))
        glow.scaleBy(x: 1.15, y: 1.15)
        glow.fill(flamePath, with: .color(Color(hex: 0xff3d0d)))

        let stretch = 0.035 * sin(time * 5.1) + 0.02 * sin(time * 8.7 + 1)
        var flame = base
        flame.translateBy(x: 0, y: 22)
        // Skew about the base, then stretch: the same order as the React Native transform list.
        flame.concatenate(CGAffineTransform(a: 1, b: 0, c: tan(0.05 * sin(time * 3.3)), d: 1, tx: 0, ty: 0))
        flame.scaleBy(x: 1 - stretch * 0.5, y: 1 + stretch)
        flame.translateBy(x: 0, y: -22)
        flame.fill(flamePath, with: .color(Color(hex: 0xfa3d0e)))

        let flicker = 0.09 * sin(time * 7.3 + 2) + 0.05 * sin(time * 11.9)
        var core = base
        core.translateBy(x: 0, y: 21)
        core.scaleBy(x: 1 - flicker * 0.4, y: 1 + flicker)
        core.translateBy(x: 0, y: -21)
        core.fill(flameCorePath, with: .color(Color(hex: 0xfffaf8)))

        guard !reduceMotion else { return }
        for index in 0..<Metrics.embers {
            let i = Double(index)
            let life = (time * Metrics.emberSpeed + i / Double(Metrics.embers)).truncatingRemainder(dividingBy: 1)
            let x = 2 + (index % 2 == 1 ? -1.0 : 1.0) * (3 + i * 1.7) * life + 2.5 * sin(time * 3 + i * 2)
            let y = -16 - Metrics.emberRise * life
            let opacity = 0.95 * seg(life, (0, 0.15)) * (1 - seg(life, (0.45, 1)))
            base.fill(
                disc(CGPoint(x: x, y: y), (1.5 - 0.15 * i) * (1 - 0.6 * life)),
                with: .color(Color(hex: 0xff8a3d).opacity(opacity))
            )
        }
    }

    /// One day of the week: an empty disc, a ticked green one, or today's ring.
    private func drawDay(_ context: GraphicsContext, index: Int, p: Double, heat: Double, t: Double) {
        let c = CGPoint(x: Metrics.bleed + Metrics.dayX + Metrics.dayGap * Double(index), y: Metrics.bleed + Metrics.dayY)

        if index == completed {
            context.stroke(disc(c, Metrics.ringR), with: .color(Color(hex: 0x2e2e2e)), lineWidth: Metrics.ringStroke)
            let turn = min(1, p)
            if turn > 0.001 {
                var arc = Path()
                arc.addArc(
                    center: c, radius: Metrics.ringR,
                    startAngle: .degrees(-90), endAngle: .degrees(-90 + min(360 * turn, 359.9)), clockwise: false
                )
                context.stroke(arc, with: .color(green), style: StrokeStyle(lineWidth: Metrics.ringStroke, lineCap: .round))
            }
            // The ring is drawn by a bright cap that cools down once the value settles.
            if heat > 0.01 {
                let a = (-90 + 360 * turn) * .pi / 180
                let head = CGPoint(x: c.x + Metrics.ringR * cos(a), y: c.y + Metrics.ringR * sin(a))
                var halo = context
                halo.addFilter(.blur(radius: 4))
                halo.fill(disc(head, 5), with: .color(.rgba(120, 255, 80, 0.7 * heat)))
                context.fill(disc(head, 2), with: .color(Color(hex: 0xeaffdf).opacity(heat)))
            }
            return
        }

        context.fill(disc(c, Metrics.dayR), with: .color(Color(hex: 0x1d1d1d)))
        guard index < completed else { return }

        // 0 = empty, 1 = ticked. While playing, the ticks pop in one after another and leave together.
        let begin = Clock.checks + Clock.checkStagger * Double(index)
        let landing = playing ? seg(t, (begin, begin + Clock.checkLength)) : 1
        let on = landing * (playing ? 1 - seg(t, Clock.reset) : 1)
        // A green ring spreads from the disc as its tick lands.
        if landing > 0, landing < 1 {
            context.stroke(
                disc(c, Metrics.dayR + 9 * (1 - pow(1 - landing, 3))),
                with: .color(green.opacity(0.7 * (1 - landing))), lineWidth: 1.5
            )
        }
        guard on > 0 else { return }

        var bead = context.scaled(0.5 + 0.5 * easeOutBack(on), about: c)
        bead.opacity = min(1, on * 3)
        // Lighter towards the rim, like a lit bead.
        bead.fill(
            disc(c, Metrics.dayR),
            with: .radialGradient(
                Gradient(stops: [
                    .init(color: Color(hex: 0x46bc2b), location: 0),
                    .init(color: Color(hex: 0x4fc435), location: 0.62),
                    .init(color: Color(hex: 0xa5f493), location: 1),
                ]),
                center: CGPoint(x: c.x, y: c.y - 2), startRadius: 0, endRadius: Metrics.dayR + 2
            )
        )
        let drawn = seg(on, (0.35, 1))
        if drawn > 0 {
            bead.translateBy(x: c.x, y: c.y)
            bead.stroke(
                checkPath.trimmedPath(from: 0, to: drawn), with: .color(Color(hex: 0x0d1a09)),
                style: StrokeStyle(lineWidth: 2.4, lineCap: .round, lineJoin: .round)
            )
        }
    }

    private func drawBar(_ context: GraphicsContext, p: Double, time: Double) {
        let x = Metrics.bleed + Metrics.inset
        let y = Metrics.bleed + Metrics.barY
        let h = Metrics.barH
        let track = capsule(CGRect(x: x, y: y, width: Metrics.barW, height: h))
        context.fill(track, with: .color(Color(hex: 0x162015)))

        let fillW = Metrics.barW * min(1, max(0, p))
        guard fillW > 0.01 else { return }
        var glow = context
        glow.opacity = min(1, fillW / 24)
        glow.addFilter(.blur(radius: 7))
        glow.fill(capsule(CGRect(x: x, y: y, width: fillW, height: h)), with: .color(.rgba(71, 189, 41, 0.6)))

        // The fill keeps its round end while it grows out of the track's left edge.
        let fill = capsule(CGRect(x: x + min(0, fillW - h), y: y, width: max(fillW, h), height: h))
        var clipped = context
        clipped.clip(to: track)
        clipped.fill(fill, with: .color(green))
        guard !reduceMotion else { return }
        clipped.clip(to: fill)
        let travel = seg(time.truncatingRemainder(dividingBy: Metrics.shimmerPeriod), (0, 1.5))
        let sx = x - Metrics.shimmerW + (fillW + Metrics.shimmerW) * travel
        clipped.fill(
            Path(CGRect(x: sx, y: y, width: Metrics.shimmerW, height: h)),
            with: .linearGradient(
                Gradient(colors: [.rgba(255, 255, 255, 0), .rgba(220, 255, 200, 0.55), .rgba(255, 255, 255, 0)]),
                startPoint: CGPoint(x: sx, y: 0), endPoint: CGPoint(x: sx + Metrics.shimmerW, y: 0)
            )
        )
    }

    private func drawText(_ context: GraphicsContext, p: Double) {
        let o = Metrics.bleed
        let grey = Color(hex: 0x6a6a6a)
        let mono = Color(hex: 0x5c5c5c)
        func at(_ x: Double, _ y: Double) -> CGPoint { CGPoint(x: o + x, y: o + y) }

        context.draw(
            Text("STREAK").font(.system(size: 14, weight: .medium)).kerning(0.7).foregroundStyle(grey),
            at: at(88, 41), anchor: .leading
        )
        context.draw(
            Text(verbatim: "\(streakDays)").font(.system(size: 24, weight: .medium).monospacedDigit())
                .foregroundStyle(Color(hex: 0xdbdbdb)),
            at: at(88, 64.5), anchor: .leading
        )
        context.draw(
            Text("DAYS").font(.system(size: 14.5, weight: .medium)).kerning(0.3).foregroundStyle(Color(hex: 0xd6d6d6)),
            at: at(124.5, 67), anchor: .leading
        )
        for (index, day) in days.enumerated() {
            context.draw(
                Text(day).font(.system(size: 13, design: .monospaced)).foregroundStyle(Color(hex: 0x8a8a8a)),
                at: at(Metrics.dayX + Metrics.dayGap * Double(index), 171.5), anchor: .center
            )
        }
        context.draw(
            Text("STEPS").font(.system(size: 13.5, weight: .medium)).kerning(0.7).foregroundStyle(grey),
            at: at(Metrics.inset, 226.5), anchor: .leading
        )

        // The count is computed here, inside the timeline, so it follows the bar frame by frame.
        let steps = context.resolve(
            Text(verbatim: "\(Int((p * Double(goal)).rounded()))")
                .font(.system(size: 22, weight: .semibold).monospacedDigit()).foregroundStyle(.white)
        )
        context.draw(steps, at: at(Metrics.inset, 250), anchor: .leading)
        let stepsWidth = steps.measure(in: CGSize(width: 200, height: 40)).width
        context.draw(
            Text("/\(goal.formatted(.number.grouping(.automatic).locale(Locale(identifier: "en_US"))))")
                .font(.system(size: 14, design: .monospaced)).foregroundStyle(mono),
            at: at(Metrics.inset + 4 + stepsWidth, 253), anchor: .leading
        )
        context.draw(
            Text(verbatim: "\(Int((p * 100).rounded()))%").font(.system(size: 14, design: .monospaced)).foregroundStyle(mono),
            at: at(Metrics.size - Metrics.inset, 253), anchor: .trailing
        )
    }
}

#Preview {
    ZStack {
        Color(hex: 0x141414).ignoresSafeArea()
        StreakStepsWidget(autoPlay: true)
    }
}

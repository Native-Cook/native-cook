import SwiftUI

/// Light "You're almost there!" card: a funding-goal speech bubble over a pill-shaped stepper whose
/// dark-to-mint fill springs from milestone to milestone. Each milestone pops with a ring and a few
/// sparks when the fill lands on it, then ages from mint to green to white as the fill moves on.
struct MilestoneStepper: View {
    /// Share of the milestones reached, 0…1. Leave nil to show the reference's 3 of 5.
    var progress: Double? = nil
    /// Plays by itself: the fill springs from milestone to milestone up to 3 of 5, holds, and drains.
    var autoPlay = false
    var title = "You’re almost there!"
    var steps = ["Create Project", "Add Media", "Set Goals", "Team", "Launch"]
    var tip = "Tip: Use round numbers to attract backers."
    var label = "Project setup"
    var action: () -> Void = {}

    @State private var taps = 0

    var body: some View {
        Button {
            taps += 1
            action()
        } label: {
            Color.clear
        }
        .buttonStyle(Style(card: self, taps: taps))
        .accessibilityRepresentation {
            ProgressView(
                value: min(max(progress ?? Double(min(Metrics.restDone, steps.count)) / Double(max(steps.count, 1)), 0), 1)
            ) {
                Text(label)
            }
        }
    }

    private struct Style: ButtonStyle {
        let card: MilestoneStepper
        let taps: Int

        func makeBody(configuration: Configuration) -> some View {
            Surface(
                progress: card.progress, autoPlay: card.autoPlay, title: card.title, steps: card.steps,
                tip: card.tip, taps: taps, isPressed: configuration.isPressed
            )
        }
    }
}

private enum Metrics {
    static let width = 330.0
    static let height = 254.0
    static let radius = 16.0
    /// The canvas overflows the card by this much on every side to fit its shadow.
    static let bleed = 44.0
    static let bubble = CGRect(x: 19.7, y: 56, width: 289.5, height: 63.3)
    static let bubbleR = 13.0
    static let trackX = 21.3
    static let trackY = 140.0
    static let trackW = 288.0
    static let trackH = 33.0
    static let dotY = trackY + trackH / 2
    static let dotR = 9.6
    /// Distance of the first and last milestone from the pill's ends.
    static let dotInset = 19.0
    /// How far past a milestone's centre the fill reaches once it is done: up to the next circle.
    static let fillLead = 57.0
    static let tip = CGRect(x: 70.8, y: 214.4, width: 186.7, height: 22.3)
    static let sparks = 7
    /// The reference rests with three of five milestones reached.
    static let restDone = 3
}

/// The autoPlay run is one clock in seconds: one spring per milestone, a hold, a drain.
private enum Clock {
    static let stepFirst = 0.5
    static let stepEvery = 0.95
    static let stepLength = 0.6
    static let hold = 2.6
    static let reset = 0.5
    static let tail = 0.4
}

private let green = Color(hex: 0x2faa77)

private func seg(_ t: Double, _ from: Double, _ to: Double) -> Double {
    min(1, max(0, (t - from) / (to - from)))
}

private func easeOut(_ x: Double) -> Double { 1 - pow(1 - x, 3) }

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

private func polyline(_ points: [(Double, Double)]) -> Path {
    var path = Path()
    for (index, point) in points.enumerated() {
        let p = CGPoint(x: point.0, y: point.1)
        if index == 0 { path.move(to: p) } else { path.addLine(to: p) }
    }
    return path
}

private let checkPath = polyline([(-4, 0.3), (-1.3, 3), (4.2, -2.8)])
private let plusPath: Path = {
    var path = polyline([(-3.6, 0), (3.6, 0)])
    path.addPath(polyline([(0, -3.6), (0, 3.6)]))
    return path
}()

/// A colour three stops along a milestone's age: newest, the one before, older.
private func aged(_ age: Double, _ stops: [(Double, Double, Double)]) -> Color {
    let ages = [1.2, 2.0, 3.0]
    func mix(_ a: (Double, Double, Double), _ b: (Double, Double, Double), _ f: Double) -> Color {
        .rgba(a.0 + (b.0 - a.0) * f, a.1 + (b.1 - a.1) * f, a.2 + (b.2 - a.2) * f, 1)
    }
    if age <= ages[1] { return mix(stops[0], stops[1], seg(age, ages[0], ages[1])) }
    return mix(stops[1], stops[2], seg(age, ages[1], ages[2]))
}

private struct Surface: View {
    let progress: Double?
    let autoPlay: Bool
    let title: String
    let steps: [String]
    let tip: String
    let taps: Int
    let isPressed: Bool

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    /// Milestones reached while controlled, a fraction while the fill is between two.
    @State private var value = Tween(0)
    @State private var settling = false
    @State private var settleToken = 0
    /// When the autoPlay run started; the haze and the floating bubble count from here too.
    @State private var start = Date.now
    @State private var epoch = Date.now

    private var count: Int { steps.count }
    private var playing: Bool { autoPlay && progress == nil && !reduceMotion }
    private var rest: Int { min(Metrics.restDone, count) }
    private var drainAt: Double { Clock.stepFirst + Double(rest - 1) * Clock.stepEvery + Clock.stepLength + Clock.hold }
    private var pitch: Double { (Metrics.trackW - Metrics.dotInset * 2) / Double(max(count - 1, 1)) }

    /// Milestones reached at `date`.
    private func done(at date: Date) -> Double {
        if progress != nil { return value.value(at: date) }
        guard playing else { return Double(rest) }
        let t = max(date.timeIntervalSince(start), 0)
            .truncatingRemainder(dividingBy: drainAt + Clock.reset + Clock.tail)
        var d = 0.0
        for k in 0..<rest {
            let begin = Clock.stepFirst + Double(k) * Clock.stepEvery
            d += easeOutBack(seg(t, begin, begin + Clock.stepLength))
        }
        return d * (1 - easeInOut(seg(t, drainAt, drainAt + Clock.reset)))
    }

    var body: some View {
        TimelineView(.animation(paused: reduceMotion && !settling)) { timeline in
            let date = timeline.date
            Canvas { context, _ in
                draw(context, done: done(at: date), time: reduceMotion ? 0 : date.timeIntervalSince(epoch))
            }
            .padding(-Metrics.bleed)
            .frame(width: Metrics.width, height: Metrics.height)
        }
        .contentShape(RoundedRectangle(cornerRadius: Metrics.radius))
        .scaleEffect(isPressed ? 0.97 : 1)
        .animation(.timingCurve(0.23, 1, 0.32, 1, duration: 0.13), value: isPressed)
        .sensoryFeedback(.impact(weight: .light), trigger: isPressed) { _, pressed in pressed }
        // A tap restarts the demo run.
        .onChange(of: taps) { start = .now }
        .onChange(of: progress, initial: true) { old, progress in
            guard let progress else { return }
            // The first call only reports the starting value: show it without a run-up.
            let first = old == progress
            value.retarget(
                min(max(progress, 0), 1) * Double(count),
                duration: first ? 0 : reduceMotion ? 0.2 : 0.7, curve: Ease.out
            )
            settling = true
            settleToken += 1
        }
        .task(id: settleToken) {
            try? await Task.sleep(for: .seconds(0.8))
            if !Task.isCancelled { settling = false }
        }
    }

    private func draw(_ context: GraphicsContext, done: Double, time: Double) {
        let o = Metrics.bleed
        let w = Metrics.width
        let h = Metrics.height
        func at(_ x: Double, _ y: Double) -> CGPoint { CGPoint(x: o + x, y: o + y) }

        // The reference's heavy slate shadow, thrown down and to the right.
        var shadow = context
        shadow.addFilter(.blur(radius: 16))
        shadow.fill(
            Path(roundedRect: CGRect(x: o + 14, y: o + 22, width: w - 6, height: h - 8), cornerRadius: Metrics.radius + 4),
            with: .color(.rgba(86, 103, 119, 0.5))
        )
        context.fill(
            Path(roundedRect: CGRect(x: o, y: o, width: w, height: h), cornerRadius: Metrics.radius),
            with: .linearGradient(
                Gradient(colors: [Color(hex: 0xf7f6f7), Color(hex: 0xefeef0), Color(hex: 0xe9e8eb)]),
                startPoint: CGPoint(x: o, y: o), endPoint: CGPoint(x: o + w, y: o + h)
            )
        )
        context.stroke(
            Path(roundedRect: CGRect(x: o + 0.6, y: o + 0.6, width: w - 1.2, height: h - 1.2), cornerRadius: Metrics.radius - 0.6),
            with: .color(.white.opacity(0.95)), lineWidth: 1.2
        )

        context.draw(
            Text(verbatim: title).font(.system(size: 22, weight: .heavy)).kerning(-0.6).foregroundStyle(Color(hex: 0x0b0b0b)),
            at: at(20, 30), anchor: .leading
        )

        drawBubble(context, time: time)
        drawTrack(context, done: done, time: time)

        // Whole milestones the fill has covered: switches the labels.
        let reached = min(count, max(0, Int(done + 0.75)))
        for index in 0..<count {
            drawMilestone(context, index: index, done: done)
            drawLabel(context, index: index, reached: reached)
        }

        let pill = Metrics.tip.offsetBy(dx: o, dy: o)
        context.fill(capsule(pill), with: .color(Color(hex: 0xf0f0f1)))
        context.stroke(capsule(pill.insetBy(dx: 0.4, dy: 0.4)), with: .color(Color(hex: 0xdcdcdf)), lineWidth: 0.8)
        var plus = context
        plus.translateBy(x: pill.minX + 12.5, y: pill.midY)
        plus.stroke(
            polyline([(-3, 0), (3, 0)]), with: .color(green), style: StrokeStyle(lineWidth: 1.9, lineCap: .round)
        )
        plus.stroke(
            polyline([(0, -3), (0, 3)]), with: .color(green), style: StrokeStyle(lineWidth: 1.9, lineCap: .round)
        )
        context.draw(
            Text(verbatim: tip).font(.system(size: 8, weight: .medium)).kerning(-0.3).foregroundStyle(Color(hex: 0x222222)),
            at: CGPoint(x: pill.minX + 20, y: pill.midY), anchor: .leading
        )
    }

    /// The funding-goal speech bubble; it floats a little.
    private func drawBubble(_ context: GraphicsContext, time: Double) {
        let o = Metrics.bleed
        var bubble = context
        bubble.translateBy(x: o, y: o + (reduceMotion ? 0 : 1.4 * sin(time * 1.7)))
        let frame = Metrics.bubble
        let dark = Color(hex: 0x111111)

        var shadow = bubble
        shadow.addFilter(.blur(radius: 9))
        shadow.fill(
            Path(roundedRect: frame.offsetBy(dx: 0, dy: 5), cornerRadius: Metrics.bubbleR), with: .color(.rgba(60, 70, 90, 0.16))
        )
        var tail = Path()
        tail.move(to: CGPoint(x: frame.minX + 19, y: frame.maxY - 1))
        tail.addLine(to: CGPoint(x: frame.minX + 41.5, y: frame.maxY - 1))
        tail.addLine(to: CGPoint(x: frame.minX + 31.5, y: frame.maxY + 7.4))
        tail.addQuadCurve(
            to: CGPoint(x: frame.minX + 28.1, y: frame.maxY + 7.4), control: CGPoint(x: frame.minX + 29.8, y: frame.maxY + 8.8)
        )
        tail.closeSubpath()
        bubble.fill(tail, with: .color(.white))
        bubble.fill(Path(roundedRect: frame, cornerRadius: Metrics.bubbleR), with: .color(.white))
        bubble.fill(capsule(CGRect(x: 28.2, y: 87.2, width: 165, height: 25.6)), with: .color(Color(hex: 0xf1f2f4)))

        let round = StrokeStyle(lineWidth: 1.4, lineCap: .round, lineJoin: .round)
        var tick = bubble
        tick.translateBy(x: 40, y: 100)
        tick.fill(disc(.zero, 6.2), with: .color(green))
        tick.stroke(polyline([(-2.7, 0.2), (-0.9, 2), (2.9, -1.9)]), with: .color(.white), style: round)
        var cross = bubble
        cross.translateBy(x: 208, y: 100)
        cross.fill(disc(.zero, 6.2), with: .color(green))
        cross.stroke(polyline([(-2.3, -2.3), (2.3, 2.3)]), with: .color(.white), style: StrokeStyle(lineWidth: 1.3, lineCap: .round))
        cross.stroke(polyline([(2.3, -2.3), (-2.3, 2.3)]), with: .color(.white), style: StrokeStyle(lineWidth: 1.3, lineCap: .round))
        var waves = bubble
        waves.translateBy(x: 292, y: 72.4)
        waves.stroke(polyline([(-4.4, -2.8), (-1.2, -0.8), (4.2, -3.2)]), with: .color(dark), style: round)
        waves.stroke(polyline([(-4.4, 1.4), (-1.2, 3.4), (4.2, 1)]), with: .color(dark), style: round)
        var note = bubble
        note.translateBy(x: 291.5, y: 100.5)
        let grey = Color(hex: 0x7c7c82)
        let thin = StrokeStyle(lineWidth: 1, lineCap: .round)
        note.stroke(Path(roundedRect: CGRect(x: -4.4, y: -3.8, width: 8.8, height: 8.2), cornerRadius: 1.6), with: .color(grey), style: thin)
        note.stroke(polyline([(0.6, -5.6), (0.6, -3.8)]), with: .color(grey), style: thin)
        note.stroke(polyline([(-1.6, 1.6), (1.2, -1)]), with: .color(grey), style: thin)

        bubble.draw(
            Text(verbatim: "Funding goal").font(.system(size: 10.5, weight: .semibold)).foregroundStyle(dark),
            at: CGPoint(x: 29.3, y: 72.4), anchor: .leading
        )
        bubble.draw(
            Text(verbatim: "Funding target").font(.system(size: 10)).foregroundStyle(Color(hex: 0x8e9096)),
            at: CGPoint(x: 50.5, y: 100), anchor: .leading
        )
        bubble.draw(
            Text(verbatim: "Stretch goals.").font(.system(size: 9.3, weight: .semibold)).kerning(-0.3).foregroundStyle(dark),
            at: CGPoint(x: 219.5, y: 100), anchor: .leading
        )
    }

    /// The pill, its dark-to-mint fill and the haze that runs ahead of it.
    private func drawTrack(_ context: GraphicsContext, done: Double, time: Double) {
        let left = Metrics.bleed + Metrics.trackX
        let top = Metrics.bleed + Metrics.trackY
        let w = Metrics.trackW
        let h = Metrics.trackH
        let track = capsule(CGRect(x: left, y: top, width: w, height: h))
        context.fill(track, with: .color(Color(hex: 0xe9e9ec)))

        // The fill ends just short of the next milestone, and takes the whole pill at the last one.
        let full = seg(done, Double(count - 1), Double(count))
        let end = min(
            w,
            Metrics.dotInset + pitch * (done - 1) + Metrics.fillLead
                + (w - Metrics.dotInset - pitch * Double(count - 2) - Metrics.fillLead) * full
        )
        let shown = seg(done, 0.05, 0.35)
        if shown > 0 {
            var inside = context
            inside.clip(to: track)
            // A mint haze runs ahead of the fill and breathes.
            var haze = inside
            haze.opacity = shown * (reduceMotion ? 0.8 : 0.72 + 0.2 * sin(time * 2.2))
            let hazeX = left + end - h / 2
            haze.fill(
                Path(CGRect(x: hazeX, y: top, width: 76, height: h)),
                with: .linearGradient(
                    Gradient(colors: [.rgba(125, 228, 189, 0.85), .rgba(150, 235, 205, 0.4), .rgba(170, 240, 215, 0)]),
                    startPoint: CGPoint(x: hazeX, y: 0), endPoint: CGPoint(x: left + end + 58, y: 0)
                )
            )
            inside.opacity = shown
            let fillW = max(h, end)
            inside.fill(
                capsule(CGRect(x: left, y: top, width: fillW, height: h)),
                with: .linearGradient(
                    Gradient(stops: [
                        .init(color: Color(hex: 0x03170c), location: 0),
                        .init(color: Color(hex: 0x06281a), location: 0.25),
                        .init(color: Color(hex: 0x1d885c), location: 0.6),
                        .init(color: Color(hex: 0x5edcac), location: 0.9),
                        .init(color: Color(hex: 0x7de4bd), location: 1),
                    ]),
                    startPoint: CGPoint(x: left, y: 0), endPoint: CGPoint(x: left + fillW, y: 0)
                )
            )
        }
        context.stroke(
            capsule(CGRect(x: left + 0.5, y: top + 0.5, width: w - 1, height: h - 1)), with: .color(.white.opacity(0.7)), lineWidth: 1
        )
        context.stroke(
            capsule(CGRect(x: left - 0.3, y: top - 0.3, width: w + 0.6, height: h + 0.6)),
            with: .color(.rgba(120, 125, 135, 0.22)), lineWidth: 0.6
        )
    }

    /// A milestone: a grey circle that pops to mint when the fill reaches it, then ages to green and white.
    private func drawMilestone(_ context: GraphicsContext, index: Int, done: Double) {
        let c = CGPoint(
            x: Metrics.bleed + Metrics.trackX + Metrics.dotInset + pitch * Double(index), y: Metrics.bleed + Metrics.dotY
        )
        let r = Metrics.dotR
        let last = index == count - 1
        // How long ago, in milestones, the fill got here; negative while it is still on its way.
        let age = done - Double(index)
        let reached = seg(age, 0.15, 0.6)
        let next = seg(age, -1.1, -0.9) * (1 - reached)

        // A ring and a handful of sparks celebrate the landing.
        let burst = seg(age, 0.3, 1)
        if burst > 0, burst < 1 {
            context.stroke(
                disc(c, r + 12 * easeOut(burst)), with: .color(Color(hex: 0xbdfbe4).opacity(0.8 * (1 - burst))), lineWidth: 1.5
            )
            for spark in 0..<Metrics.sparks {
                let s = Double(spark)
                let angle = s / Double(Metrics.sparks) * .pi * 2 + 0.4
                let reach = r + (15 + 9 * (s * 0.618).truncatingRemainder(dividingBy: 1)) * easeOut(burst)
                let center = CGPoint(x: c.x + cos(angle) * reach, y: c.y + sin(angle) * reach + 5 * burst * burst)
                let color = spark % 3 == 0 ? Color.white : Color(hex: spark % 3 == 1 ? 0x5edcac : 0x1d885c)
                context.fill(
                    disc(center, (spark % 2 == 1 ? 1.5 : 2.1) * (1 - 0.7 * burst)),
                    with: .color(color.opacity(1 - burst * burst))
                )
            }
        }

        let dot = context.scaled(1 + 0.32 * sin(.pi * reached) * (1 - reached * 0.4), about: c)
        let stroke = StrokeStyle(lineWidth: 1.7, lineCap: .round, lineJoin: .round)
        if reached < 1 {
            var waiting = dot
            waiting.opacity = 1 - reached
            waiting.fill(disc(c, r), with: .color(Color(hex: 0xcfcfd3)))
            waiting.fill(disc(c, r), with: .color(Color(hex: 0xb4e6d4).opacity(next)))
            var glyph = waiting
            glyph.translateBy(x: c.x, y: c.y)
            let path = last ? plusPath : checkPath
            glyph.stroke(path, with: .color(Color(hex: 0xa3a3a8)), style: stroke)
            glyph.stroke(path, with: .color(Color(hex: 0x8fc7b2).opacity(next)), style: stroke)
        }
        guard reached > 0 else { return }
        var lit = dot
        lit.opacity = reached
        var shade = lit
        shade.addFilter(.blur(radius: 2.5))
        shade.fill(disc(CGPoint(x: c.x, y: c.y + 1), r), with: .color(.rgba(0, 40, 20, 0.25)))
        // Newest is mint with a dark tick, the one before deep green with a white tick, older ones white.
        lit.fill(disc(c, r), with: .color(aged(age, [(141, 246, 207), (31, 125, 84), (255, 255, 255)])))
        let drawn = seg(age, 0.35, 0.8)
        if drawn > 0 {
            lit.translateBy(x: c.x, y: c.y)
            lit.stroke(
                checkPath.trimmedPath(from: 0, to: drawn),
                with: .color(aged(age, [(13, 90, 58), (255, 255, 255), (11, 61, 36)])),
                style: StrokeStyle(lineWidth: 1.9, lineCap: .round, lineJoin: .round)
            )
        }
    }

    /// The number and name under a milestone: green and bold once reached, the newest in parentheses.
    private func drawLabel(_ context: GraphicsContext, index: Int, reached: Int) {
        let o = Metrics.bleed
        let on = index < reached
        let x = Metrics.trackX + Metrics.dotInset + pitch * Double(index)
        // The first and last labels are nudged inwards so they stay inside the card.
        let center = min(Metrics.width - 38, max(42, x))
        let number = CGPoint(x: o + center, y: o + 181)
        let selected = index == 0 && on
        if selected { context.fill(disc(number, 5.5), with: .color(green)) }
        context.draw(
            Text(verbatim: "\(index + 1)")
                .font(.system(size: 7.5, weight: on ? .bold : .regular).monospacedDigit())
                .foregroundStyle(selected ? .white : Color(hex: on ? 0x2a9d6e : 0x8d8d92)),
            at: number, anchor: .center
        )
        let name = index == reached - 1 ? "(\(steps[index]))" : steps[index]
        context.draw(
            Text(verbatim: name).font(.system(size: 8.5, weight: on ? .bold : .regular)).kerning(-0.1)
                .foregroundStyle(Color(hex: on ? 0x0d0d0d : 0x8d8d92)),
            at: CGPoint(x: o + center, y: o + 193), anchor: .center
        )
    }
}

#Preview {
    ZStack {
        Color(hex: 0xe7e6e9).ignoresSafeArea()
        MilestoneStepper(autoPlay: true)
    }
}

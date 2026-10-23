import SwiftUI

/// Agent live activity: a title with the tools in use, five steps joined by dashes, and what the
/// agent is doing right now. Each step's dash fills with a pulse of light running along it, the
/// step being worked on carries a spinning arc, a finished step pops in with a ripple and draws its
/// check, the status line and the count slide over to the next step, and the dots after the status
/// keep typing.
struct RecruiterSteps: View {
    /// Controlled progress through the steps, 0…1: each step is a fifth.
    var progress: Double? = nil
    /// Works through the steps up to the state shown in the reference, holds, and starts over.
    var autoPlay = false
    var title = "Recruiter"
    /// What the agent is doing during each of the five steps (the dots after it are animated).
    var statuses = ["Reading inbox", "Searching profiles", "Screening candidates", "Writing summary", "Sending shortlist"]
    var doneStatus = "Shortlist sent"

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    /// Origin of the ambient clock (spinner, pulses, typing dots).
    @State private var epoch = Date.now
    /// When the current autoPlay / tapped run started; `nil` shows the resting frame.
    @State private var runStart: Date? = nil
    @State private var held = Tween(Metrics.rest)
    /// Keeps the timeline running for a controlled change under reduced motion.
    @State private var settling = false
    @State private var handover = Handover()

    /// Per-frame state: the step being worked on, the one before it, and when the status line changed.
    private final class Handover {
        var now: Int? = nil
        var before = 0
        var swappedAt = Date.distantPast

        func advance(to date: Date, step: Int) {
            guard let now else {
                self.now = step
                before = step
                return
            }
            guard step != now else { return }
            before = now
            self.now = step
            swappedAt = date
        }
    }

    private var controlled: Bool { progress != nil }
    private var target: Double { min(1, max(0, progress ?? Metrics.rest)) }

    var body: some View {
        TimelineView(.animation(paused: reduceMotion && !settling)) { timeline in
            let date = timeline.date
            let time = reduceMotion ? 0 : date.timeIntervalSince(epoch)
            let value = controlled ? held.value(at: date) : loopValue(clock(at: date))
            let step = activeStep(value)
            let _ = handover.advance(to: date, step: step)
            let ambient = reduceMotion ? 0.0 : 1.0
            let since = date.timeIntervalSince(handover.swappedAt)
            let incoming = ambient > 0 ? easeOutCubic(since / 0.32) : 1
            let outgoing = ambient > 0 ? easeOutCubic(since / 0.24) : 1
            let statusFont = Font.system(size: 16.4, weight: .regular)

            ZStack {
                Canvas { context, _ in
                    draw(context, value: value, time: time)
                }
                .padding(-Metrics.bleed)

                Text(title)
                    .font(.system(size: 15.2, weight: .bold))
                    .tracking(-0.1)
                    .foregroundStyle(.white)
                    .fixedSize()
                    .frame(width: 200, alignment: .leading)
                    .position(x: 24 + 100, y: 32.3)

                // The status line hands over to the next step: the old one leaves upwards, the new one rises in.
                Text(label(handover.before) + (handover.before < Metrics.steps ? "..." : ""))
                    .font(statusFont)
                    .tracking(-0.1)
                    .foregroundStyle(grey)
                    .fixedSize()
                    .frame(width: 240, alignment: .leading)
                    .opacity(1 - outgoing)
                    .position(x: 24 + 120, y: 107.1 - 7 * outgoing)

                HStack(spacing: 0) {
                    Text(label(step))
                    if step < Metrics.steps {
                        // The dots light one after another while the agent works.
                        ForEach(0..<3, id: \.self) { index in
                            Text(".").opacity(1 - ambient * 0.72 * (0.5 - 0.5 * sin(time * 5 - Double(index) * 1.1)))
                        }
                    }
                }
                .font(statusFont)
                .tracking(-0.1)
                .foregroundStyle(grey)
                .fixedSize()
                .frame(width: 240, alignment: .leading)
                .opacity(incoming)
                .position(x: 24 + 120, y: 107.1 + 7 * (1 - incoming))

                Text(verbatim: "\(step) of \(Metrics.steps)")
                    .font(statusFont)
                    .tracking(-0.1)
                    .monospacedDigit()
                    .foregroundStyle(grey)
                    .fixedSize()
                    .frame(width: 80, alignment: .trailing)
                    .opacity(incoming)
                    .position(x: Metrics.width - 24.4 - 40, y: 107.1 + 7 * (1 - incoming))
            }
            .lineLimit(1)
            .frame(width: Metrics.width, height: Metrics.height)
            .accessibilityElement(children: .ignore)
            .accessibilityLabel(title)
            .accessibilityValue(Text(verbatim: "\(label(activeStep(target))), \(activeStep(target)) of \(Metrics.steps)"))
        }
        .contentShape(RoundedRectangle(cornerRadius: Metrics.radius))
        .onTapGesture { restart() }
        .onAppear { sync() }
        .onChange(of: autoPlay) { sync() }
        .onChange(of: reduceMotion) { sync() }
        .onChange(of: progress) {
            sync()
            guard controlled else { return }
            held.retarget(target, duration: reduceMotion ? 0.2 : 1.6, curve: Ease.out)
            settling = true
        }
        .task(id: settling) {
            guard settling else { return }
            try? await Task.sleep(for: .seconds(1.7))
            settling = false
        }
    }

    private func label(_ step: Int) -> String {
        step >= Metrics.steps || step >= statuses.count ? doneStatus : statuses[step]
    }

    private func sync() {
        runStart = autoPlay && !controlled && !reduceMotion ? .now : nil
        if controlled, held.duration == 0 { held = Tween(target) }
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

    private func draw(_ context: GraphicsContext, value: Double, time: Double) {
        let x = Metrics.bleed
        let y = Metrics.bleed
        let ambient = reduceMotion ? 0.0 : 1.0

        var shadow = context
        shadow.addFilter(.blur(radius: 16))
        shadow.fill(
            Path(
                roundedRect: CGRect(x: x + 10, y: y + 16, width: Metrics.width - 20, height: Metrics.height - 8),
                cornerRadius: Metrics.radius, style: .circular
            ),
            with: .color(Color.rgba(6, 16, 40, 0.5))
        )
        context.fill(
            Path(roundedRect: CGRect(x: x, y: y, width: Metrics.width, height: Metrics.height), cornerRadius: Metrics.radius, style: .circular),
            with: .color(.black)
        )

        let p = clamp01(value)
        for index in 0..<Metrics.steps {
            let centre = CGPoint(x: x + Metrics.stepX + Metrics.stepGap * Double(index), y: y + Metrics.stepY)
            let past = pastStep(p, index)
            let filled = clamp01(past / 0.1)

            if index < Metrics.steps - 1 {
                // The dash to the next dot fills, with a pulse of light running along it.
                let from = centre.x + Metrics.dashFrom
                let span = Metrics.dashTo - Metrics.dashFrom
                let cap = StrokeStyle(lineWidth: Metrics.dashW, lineCap: .round)
                context.stroke(line(from, from + span, centre.y), with: .color(dim), style: cap)
                let fill = clamp01(past)
                if fill > 0.001 {
                    context.stroke(line(from, from + span * fill, centre.y), with: .color(blue.opacity(clamp01(past * 40))), style: cap)
                }
                if ambient > 0, past > 0.04, past < 1 {
                    let phase = (time * 1.3).truncatingRemainder(dividingBy: 1)
                    var pulse = context
                    pulse.addFilter(.blur(radius: 1.6))
                    pulse.fill(
                        circle(CGPoint(x: from + span * fill * phase, y: centre.y), 2.6),
                        with: .color(Color(hex: 0xd9efff, opacity: 0.9 * sin(.pi * phase)))
                    )
                }
            }

            if filled < 1 {
                context.stroke(circle(centre, Metrics.ringR), with: .color(dim.opacity(1 - filled)), lineWidth: Metrics.ringW)
            }
            // The step being worked on: a short arc runs around its ring.
            if ambient > 0, activeStep(p) == index, p > 0.0005 {
                let working = clamp01((p * Double(Metrics.steps) - Double(index)) * 8) * (1 - filled)
                var arc = Path()
                let turn = Angle.radians(time * 5.2 - .pi / 2)
                arc.addArc(center: centre, radius: Metrics.ringR, startAngle: turn, endAngle: turn + .degrees(90), clockwise: false)
                context.stroke(arc, with: .color(blue.opacity(working)), style: StrokeStyle(lineWidth: Metrics.ringW, lineCap: .round))
            }
            // A finished step lands with a springy overshoot, sends out a ripple and draws its check.
            let ripple = past / 0.5
            if ambient > 0, ripple > 0, ripple < 1 {
                context.stroke(
                    circle(centre, Metrics.dotR + 10 * easeOutCubic(ripple)), with: .color(blue.opacity(0.7 * (1 - ripple))), lineWidth: 1.5
                )
            }
            if filled > 0 {
                let u = clamp01(past / 0.36)
                let r = Metrics.dotR * (0.7 + 0.3 * filled + ambient * 0.24 * sin(.pi * u) * (1 - u))
                context.fill(circle(centre, r), with: .color(blue.opacity(filled)))
                let drawn = ambient > 0 ? easeOutCubic((past - 0.06) / 0.26) : filled
                if drawn > 0.001 {
                    var tick = context
                    tick.translateBy(x: centre.x, y: centre.y)
                    tick.stroke(
                        checkGlyph.trimmedPath(from: 0, to: drawn), with: .color(Color(hex: 0x04070c)),
                        style: StrokeStyle(lineWidth: 2.1, lineCap: .round, lineJoin: .round)
                    )
                }
            }
        }

        // The tools the agent uses, back to front so the first overlaps the others: they bob one after another.
        for index in [2, 1, 0] {
            let lift = ambient * 2.2 * pow(max(0, sin(time * 2.4 - Double(index) * 1.1)), 3)
            var tool = context
            tool.translateBy(x: x + Metrics.iconX[index], y: y + Metrics.iconY - lift)
            let r = Metrics.iconR
            tool.fill(circle(.zero, r + 1.5), with: .color(.black))
            switch index {
            case 0:
                tool.fill(
                    circle(.zero, r),
                    with: .linearGradient(
                        Gradient(colors: [Color(hex: 0x45c6ff), Color(hex: 0x1477f5)]),
                        startPoint: CGPoint(x: 0, y: -r), endPoint: CGPoint(x: 0, y: r)
                    )
                )
                tool.fill(Path(CGRect(x: -5.6, y: -3.8, width: 11.2, height: 7.6)), with: .color(.white))
                tool.stroke(flapGlyph, with: .color(Color(hex: 0x2a8cf2)), style: StrokeStyle(lineWidth: 1.1, lineJoin: .round))
            case 1:
                tool.fill(
                    circle(.zero, r),
                    with: .linearGradient(
                        Gradient(colors: [Color(hex: 0x7fe0ff), Color(hex: 0x0b5fd6)]),
                        startPoint: CGPoint(x: -r, y: -r), endPoint: CGPoint(x: r, y: r)
                    )
                )
                tool.stroke(circle(.zero, 7.6), with: .color(.white.opacity(0.85)), lineWidth: 1)
                tool.stroke(meridianGlyph, with: .color(.white.opacity(0.85)), lineWidth: 1)
            default:
                tool.fill(circle(.zero, r), with: .color(Color(hex: 0xf4f4f2)))
                tool.stroke(pageGlyph, with: .color(Color(hex: 0x16171a)), style: StrokeStyle(lineWidth: 1.3, lineJoin: .round))
                tool.stroke(linesGlyph, with: .color(Color(hex: 0x16171a)), style: StrokeStyle(lineWidth: 1.1, lineCap: .round))
            }
        }
    }
}

private let blue = Color(hex: 0x1e9bff)
private let dim = Color(hex: 0x1c5c96)
private let grey = Color(hex: 0x8e8e93)

private enum Metrics {
    static let width = 340.0
    static let height = 139.2
    static let radius = 46.0
    /// The canvas overflows the panel by this much on every side to fit its shadow.
    static let bleed = 40.0

    static let steps = 5
    static let stepX = 36.4
    static let stepGap = 66.6
    static let stepY = 71.3
    static let dotR = 12.8
    static let ringR = 12.4
    static let ringW = 2.9
    // A dash starts and ends this far from the centres of the circles it joins.
    static let dashFrom = 19.0
    static let dashTo = 47.6
    static let dashW = 4.1
    static let iconX = [279.8, 295.3, 306.7]
    static let iconY = 34.3
    static let iconR = 10.9

    /// The reference shows three steps done and the fourth well under way.
    static let rest = 0.754
    // autoPlay loop (seconds): steps per second while working.
    static let start = 0.4
    static let rate = 0.8
    static let run = rest * Double(steps) / rate
    static let hold = 3.6
    static let reset = 0.7
    static let loop = start + run + hold + reset
    /// A moment in the loop where everything is at rest at the reference value: used when nothing is playing.
    static let restTime = start + run + 1.5
}

private func clamp01(_ t: Double) -> Double { min(1, max(0, t)) }

private func easeInOutCubic(_ t: Double) -> Double {
    t < 0.5 ? 4 * t * t * t : 1 - pow(-2 * t + 2, 3) / 2
}

private func easeOutCubic(_ t: Double) -> Double { 1 - pow(1 - clamp01(t), 3) }

/// Overall progress at `t` seconds into the autoPlay loop.
private func loopValue(_ t: Double) -> Double {
    if t < Metrics.start { return 0 }
    if t < Metrics.start + Metrics.run { return (t - Metrics.start) * Metrics.rate / Double(Metrics.steps) }
    if t < Metrics.start + Metrics.run + Metrics.hold { return Metrics.rest }
    return Metrics.rest * (1 - easeInOutCubic((t - Metrics.start - Metrics.run - Metrics.hold) / Metrics.reset))
}

/// How far past its completion step `index` is, in steps: everything about a dot follows from it.
private func pastStep(_ p: Double, _ index: Int) -> Double {
    clamp01(p) * Double(Metrics.steps) - Double(index + 1)
}

/// The step being worked on at progress `p` (0…5; 5 when everything is done).
private func activeStep(_ p: Double) -> Int {
    min(Metrics.steps, Int((clamp01(p) * Double(Metrics.steps) + 0.0001).rounded(.down)))
}

private func circle(_ centre: CGPoint, _ r: Double) -> Path {
    Path(ellipseIn: CGRect(x: centre.x - r, y: centre.y - r, width: r * 2, height: r * 2))
}

private func line(_ from: Double, _ to: Double, _ y: Double) -> Path {
    var p = Path()
    p.move(to: CGPoint(x: from, y: y))
    p.addLine(to: CGPoint(x: to, y: y))
    return p
}

private let checkGlyph: Path = {
    var p = Path()
    p.move(to: CGPoint(x: -5.4, y: -0.6))
    p.addLine(to: CGPoint(x: -1.2, y: 4.8))
    p.addLine(to: CGPoint(x: 5.8, y: -5))
    return p
}()

// Glyphs of the three tools, drawn around the centre of their disc.
private let flapGlyph: Path = {
    var p = Path()
    p.move(to: CGPoint(x: -5.6, y: -3.6))
    p.addLine(to: CGPoint(x: 0, y: 1))
    p.addLine(to: CGPoint(x: 5.6, y: -3.6))
    return p
}()

private let meridianGlyph: Path = {
    var p = Path()
    p.move(to: CGPoint(x: 0, y: -7.6))
    p.addCurve(to: CGPoint(x: 0, y: 7.6), control1: CGPoint(x: -4.6, y: -3.6), control2: CGPoint(x: -4.6, y: 3.6))
    p.addCurve(to: CGPoint(x: 0, y: -7.6), control1: CGPoint(x: 4.6, y: 3.6), control2: CGPoint(x: 4.6, y: -3.6))
    p.closeSubpath()
    p.move(to: CGPoint(x: -7.6, y: 0))
    p.addLine(to: CGPoint(x: 7.6, y: 0))
    return p
}()

private let pageGlyph: Path = {
    var p = Path()
    p.move(to: CGPoint(x: -3.9, y: -5.2))
    p.addLine(to: CGPoint(x: 1.6, y: -5.2))
    p.addLine(to: CGPoint(x: 3.9, y: -2.9))
    p.addLine(to: CGPoint(x: 3.9, y: 5.2))
    p.addLine(to: CGPoint(x: -3.9, y: 5.2))
    p.closeSubpath()
    return p
}()

private let linesGlyph: Path = {
    var p = Path()
    p.move(to: CGPoint(x: -1.8, y: -0.6))
    p.addLine(to: CGPoint(x: 1.8, y: -0.6))
    p.move(to: CGPoint(x: -1.8, y: 2.2))
    p.addLine(to: CGPoint(x: 1.8, y: 2.2))
    return p
}()

#Preview {
    ZStack {
        Color(hex: 0x3f6fae).ignoresSafeArea()
        RecruiterSteps(autoPlay: true)
    }
}

import SwiftUI

/// Navy "Submit" pill. A tap squeezes it into three loading dots, then it springs back open with a
/// burst of confetti and a green-checked "Success" before returning to its label.
struct SubmitConfettiButton: View {
    var label = "Submit"
    var successLabel = "Success"
    /// Submits by itself over and over, with a pause between runs.
    var autoPlay = false
    var action: () -> Void = {}

    @State private var taps = 0

    var body: some View {
        Button {
            taps += 1
            action()
        } label: {
            Text(label)
        }
        .buttonStyle(Style(label: label, successLabel: successLabel, autoPlay: autoPlay, taps: taps))
        // The label is swapped for dots and the result, so name the button explicitly.
        .accessibilityLabel(label)
    }

    private struct Style: ButtonStyle {
        let label: String
        let successLabel: String
        let autoPlay: Bool
        let taps: Int

        func makeBody(configuration: Configuration) -> some View {
            Surface(
                label: label, successLabel: successLabel, autoPlay: autoPlay, taps: taps,
                isPressed: configuration.isPressed
            )
        }
    }
}

private enum Metrics {
    static let width = 200.0
    static let height = 48.0
    /// The pill narrows to this while it is loading.
    static let loadingWidth = 160.0
    /// The canvases overflow the button by this much so the confetti has room to fly.
    static let bleed = 116.0
    static let icon = 16.0
    static let autoPlayPause = 1.5
    static let dotGap = 9.0
    static let dotRadius = 2.3
    static let dotPeriod = 1.0
}

/// The whole run is one clock in seconds; every part is a window on it.
private enum Clock {
    static let labelOut = (0.0, 0.2)
    static let squeeze = (0.0, 0.4)
    static let dotsIn = 0.25
    static let dotsOut = (2.0, 2.15)
    static let widen = (2.0, 2.45)
    static let burst = 2.2
    static let doneIn = (2.7, 2.95)
    static let check = (2.9, 3.2)
    static let doneOut = (5.3, 5.55)
    static let labelIn = (5.85, 6.15)
    static let total = 6.2
}

private func seg(_ t: Double, _ window: (Double, Double)) -> Double {
    min(1, max(0, (t - window.0) / (window.1 - window.0)))
}

private func easeOut(_ x: Double) -> Double { 1 - pow(1 - x, 3) }

private func easeOutBack(_ x: Double) -> Double {
    let c = 1.9
    return 1 + (c + 1) * pow(x - 1, 3) + c * pow(x - 1, 2)
}

/// Cheap repeatable noise in 0…1, identical on every platform.
private func noise(_ i: Int, _ k: Int) -> Double {
    let s = sin(Double(i) * 12.9898 + Double(k) * 78.233) * 43758.5453
    return s - s.rounded(.down)
}

private let confettiColors = [Color(hex: 0xa9b9f3), Color(hex: 0x2b55cd), Color(hex: 0x6444ec), Color(hex: 0x8393de)]
private let confettiLife = 2.6
/// Air drag: pieces shoot out, stall within a third of a second, then drift down.
private let drag = 3.2

private struct Piece {
    let color: Int
    let x0, vx, vy, fall, rot, spin, flip, phase, hw, hh: Double
    let tiny: Bool
    let life, sway, swayRate: Double
}

private let pieces: [Piece] = (0..<40).map { i in
    let x0 = (noise(i, 1) - 0.5) * Metrics.width * 0.72
    // Fan out: pieces born towards an end of the pill lean that way.
    let angle = (noise(i, 2) - 0.5) * 1.3 + (x0 / Metrics.width) * 0.9
    let speed = 200 + noise(i, 3) * 260
    let tiny = noise(i, 9) < 0.22
    return Piece(
        color: i % confettiColors.count,
        x0: x0,
        vx: sin(angle) * speed,
        vy: -cos(angle) * speed,
        fall: 95 + noise(i, 4) * 75,
        rot: noise(i, 5) * tau,
        spin: (noise(i, 6) - 0.5) * 12,
        flip: 3 + noise(i, 7) * 7,
        phase: noise(i, 8) * tau,
        hw: tiny ? 1.2 : 2.6 + noise(i, 10) * 2.6,
        hh: tiny ? 1.2 : 2 + noise(i, 11) * 2.4,
        tiny: tiny,
        life: 1.7 + noise(i, 12) * 0.9,
        sway: 6 + noise(i, 13) * 10,
        swayRate: 2 + noise(i, 14) * 2
    )
}

/// Every piece of one colour, `age` seconds after the burst, as a single path of tumbling quads.
private func confetti(age: Double, color: Int) -> Path {
    var path = Path()
    guard age > 0, age < confettiLife else { return path }
    let cx = Metrics.bleed + Metrics.width / 2
    let top = Metrics.bleed + 4
    let d = (1 - exp(-drag * age)) / drag
    for p in pieces where p.color == color && age < p.life {
        let size = min(1, age / 0.06) * (1 - seg(age, (p.life - 0.35, p.life)))
        let x = cx + p.x0 + p.vx * d + p.sway * sin(p.swayRate * age + p.phase) * min(1, age)
        let y = top + p.vy * d + p.fall * (age - d)
        let a = p.rot + p.spin * age
        // Tumbling end over end squashes the piece to a sliver and back.
        let hh = p.hh * size * (p.tiny ? 1 : cos(p.flip * age + p.phase))
        let hw = p.hw * size
        let ax = cos(a) * hw, ay = sin(a) * hw
        let bx = -sin(a) * hh, by = cos(a) * hh
        path.move(to: CGPoint(x: x - ax - bx, y: y - ay - by))
        path.addLine(to: CGPoint(x: x + ax - bx, y: y + ay - by))
        path.addLine(to: CGPoint(x: x + ax + bx, y: y + ay + by))
        path.addLine(to: CGPoint(x: x - ax + bx, y: y - ay + by))
        path.closeSubpath()
    }
    return path
}

/// Pill width at clock time `t`: squeezes in for loading, springs back out for the result.
private func pillWidth(_ t: Double, still: Bool) -> Double {
    if still { return Metrics.width }
    let squeeze = easeOut(seg(t, Clock.squeeze)) - easeOutBack(seg(t, Clock.widen))
    return Metrics.width - (Metrics.width - Metrics.loadingWidth) * squeeze
}

/// The three loading dots: they pop in one after another, then a wave runs through them every second.
private func dots(_ t: Double, still: Bool) -> Path {
    var path = Path()
    guard t > Clock.dotsIn, t < Clock.dotsOut.1 else { return path }
    for i in 0..<3 {
        let start = Clock.dotsIn + Double(i) * 0.06
        let pop = still ? seg(t, (start, start + 0.2)) : easeOutBack(seg(t, (start, start + 0.25)))
        let phase = (t - 0.9 - Double(i) * 0.12) / Metrics.dotPeriod
        let beat = phase - phase.rounded(.down)
        let hop = still || beat > 0.45 ? 0 : sin(beat / 0.45 * .pi) * 5
        let r = Metrics.dotRadius * pop * (1 - seg(t, Clock.dotsOut))
        if r > 0.05 {
            let c = CGPoint(
                x: Metrics.bleed + Metrics.width / 2 + Double(i - 1) * Metrics.dotGap,
                y: Metrics.bleed + Metrics.height / 2 - hop
            )
            path.addEllipse(in: CGRect(x: c.x - r, y: c.y - r, width: r * 2, height: r * 2))
        }
    }
    return path
}

private struct ArrowIcon: Shape {
    func path(in rect: CGRect) -> Path {
        var p = Path()
        p.move(to: CGPoint(x: 8, y: 2.5))
        p.addLine(to: CGPoint(x: 8, y: 13))
        p.move(to: CGPoint(x: 3.5, y: 8.5))
        p.addLine(to: CGPoint(x: 8, y: 13))
        p.addLine(to: CGPoint(x: 12.5, y: 8.5))
        return p
    }
}

private struct CheckIcon: Shape {
    func path(in rect: CGRect) -> Path {
        var p = Path()
        p.move(to: CGPoint(x: 2.5, y: 8.5))
        p.addLine(to: CGPoint(x: 6.5, y: 12.5))
        p.addLine(to: CGPoint(x: 13.5, y: 4.5))
        return p
    }
}

private struct Surface: View {
    let label: String
    let successLabel: String
    let autoPlay: Bool
    let taps: Int
    let isPressed: Bool

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    /// When the current run started; nil while idle.
    @State private var start: Date?

    var body: some View {
        TimelineView(.animation(paused: start == nil)) { timeline in
            let t = start.map { min(max(timeline.date.timeIntervalSince($0), 0), Clock.total) } ?? 0
            let slide = reduceMotion ? 0.0 : 1.0
            let out = seg(t, Clock.labelOut)
            let back = seg(t, Clock.labelIn)
            let shown = seg(t, Clock.doneIn)
            let iconStroke = StrokeStyle(lineWidth: 2, lineCap: .round, lineJoin: .round)

            ZStack {
                Canvas { context, _ in
                    let w = pillWidth(t, still: reduceMotion)
                    let rect = CGRect(
                        x: Metrics.bleed + (Metrics.width - w) / 2, y: Metrics.bleed,
                        width: w, height: Metrics.height
                    )
                    context.fill(capsule(rect), with: .color(Color(hex: 0x191e2c)))
                    context.fill(dots(t, still: reduceMotion), with: .color(Color(hex: 0x5d7bff)))
                }
                .padding(-Metrics.bleed)

                // The label drops away on the way out and comes back down from above.
                HStack(spacing: 8) {
                    ArrowIcon()
                        .stroke(Color(hex: 0x4a7aff), style: iconStroke)
                        .frame(width: Metrics.icon, height: Metrics.icon)
                    Text(label)
                }
                .opacity(1 - out + back)
                .offset(y: slide * (back > 0 ? -8 * (1 - easeOut(back)) : 10 * easeOut(out)))
                .accessibilityHidden(true)

                HStack(spacing: 8) {
                    CheckIcon()
                        .trim(from: 0, to: easeOut(seg(t, Clock.check)))
                        .stroke(Color(hex: 0x3ee888), style: iconStroke)
                        .frame(width: Metrics.icon, height: Metrics.icon)
                    Text(successLabel)
                }
                .opacity(shown - seg(t, Clock.doneOut))
                .offset(y: slide * 8 * (1 - easeOut(shown)))
                .accessibilityHidden(true)

                // Confetti flies in front of the pill and its label.
                if !reduceMotion {
                    Canvas { context, _ in
                        for (index, color) in confettiColors.enumerated() {
                            context.fill(confetti(age: t - Clock.burst, color: index), with: .color(color))
                        }
                    }
                    .padding(-Metrics.bleed)
                }
            }
            .font(.system(size: 16, weight: .medium))
            .lineLimit(1)
            .foregroundStyle(.white)
            .frame(width: Metrics.width, height: Metrics.height)
        }
        .contentShape(Capsule())
        .scaleEffect(isPressed ? 0.97 : 1)
        .animation(.timingCurve(0.23, 1, 0.32, 1, duration: 0.13), value: isPressed)
        .sensoryFeedback(.impact(weight: .light), trigger: isPressed) { _, pressed in pressed }
        .onChange(of: taps) {
            Task { await run() }
        }
        .task(id: autoPlay) {
            guard autoPlay, !reduceMotion else { return }
            while !Task.isCancelled {
                try? await Task.sleep(for: .seconds(Metrics.autoPlayPause))
                if Task.isCancelled { return }
                await run()
            }
        }
    }

    /// One run of the clock. The clock ends where it began (label showing), so dropping back to
    /// idle afterwards is invisible.
    private func run() async {
        guard start == nil else { return }
        start = .now
        try? await Task.sleep(for: .seconds(Clock.total))
        start = nil
    }
}

#Preview {
    ZStack {
        Color(hex: 0xeff4fc).ignoresSafeArea()
        SubmitConfettiButton(autoPlay: true)
    }
}

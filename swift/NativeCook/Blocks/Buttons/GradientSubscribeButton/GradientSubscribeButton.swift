import SwiftUI

/// "SUBSCRIBE" pill whose magenta → crimson → orange gradient drifts sideways under a matching glow.
struct GradientSubscribeButton: View {
    var label = "SUBSCRIBE"
    /// Keep the gradient drifting and the glow lit all the time. When off, the button rests on its
    /// magenta face and only drifts while pressed/hovered, then finishes its cycle and settles.
    var autoPlay = false
    var action: () -> Void = {}

    var body: some View {
        Button(action: action) { Text(label) }
            .buttonStyle(Style(label: label, autoPlay: autoPlay))
    }

    private struct Style: ButtonStyle {
        let label: String
        let autoPlay: Bool

        func makeBody(configuration: Configuration) -> some View {
            Surface(label: label, autoPlay: autoPlay, isPressed: configuration.isPressed)
        }
    }
}

private enum Metrics {
    static let height = 54.0
    static let padX = 40.0
    /// The canvas overflows the button by this much on every side to fit the glow.
    static let bleed = 44.0
    /// One full trip through the gradient, which is four button-widths long.
    static let cycleSeconds = 3.3
    static let span = 4.0
    /// How far (in cycles) the gradient must still travel after release, so the run-out isn't hidden under the thumb.
    static let minRunout = 0.35
    static let glowBlur = 13.0
    static let glowOpacity = 0.78
    static let letterSpacing = 2.6
}

private let magenta = Color(hex: 0xe830c8)
private let crimson = Color(hex: 0xd8252f)
private let orange = Color(hex: 0xff9a10)

// Two cycles back to back: SwiftUI gradients don't tile, and the drift needs up to one extra cycle.
private let driftGradient = Gradient(stops: [
    .init(color: magenta, location: 0),
    .init(color: crimson, location: 1.0 / 6),
    .init(color: orange, location: 2.0 / 6),
    .init(color: magenta, location: 3.0 / 6),
    .init(color: crimson, location: 4.0 / 6),
    .init(color: orange, location: 5.0 / 6),
    .init(color: magenta, location: 1),
])

/// Where the gradient is on its trip. Measured in cycles; whole numbers put the magenta face back under the label.
private struct Drift {
    enum Phase { case rest, running, settling }

    var phase = Phase.rest
    var from = 0.0
    var to = 0.0
    var start = Date.distantPast
    var duration = 0.0

    func value(at date: Date) -> Double {
        let elapsed = date.timeIntervalSince(start)
        switch phase {
        case .rest:
            return from
        case .running:
            return from + max(elapsed, 0) / Metrics.cycleSeconds
        case .settling:
            let t = duration > 0 ? min(max(elapsed / duration, 0), 1) : 1
            return from + (to - from) * Ease.settle.value(at: t)
        }
    }
}

private struct Surface: View {
    let label: String
    let autoPlay: Bool
    let isPressed: Bool

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var hovered = false
    @State private var drift = Drift()
    @State private var glow = Tween(0)
    @State private var settling = false
    @State private var settleToken = 0

    private var running: Bool { autoPlay || isPressed || hovered }

    var body: some View {
        TimelineView(.animation(paused: !(running || settling))) { timeline in
            let phase = drift.value(at: timeline.date)
            let glowOpacity = glow.value(at: timeline.date)

            Text(label)
                .font(.system(size: 15, weight: .semibold))
                .kerning(Metrics.letterSpacing)
                .lineLimit(1)
                .foregroundStyle(.white)
                // The tracking is added after the last glyph too; nudge the text back to the optical centre.
                .padding(.leading, Metrics.letterSpacing)
                .padding(.horizontal, Metrics.padX)
                .frame(height: Metrics.height)
                .background {
                    Canvas { context, size in
                        draw(context, size: size, phase: phase, glow: glowOpacity)
                    }
                    .padding(-Metrics.bleed)
                    .allowsHitTesting(false)
                }
        }
        .contentShape(Capsule())
        .scaleEffect(isPressed ? 0.97 : 1)
        .animation(.timingCurve(0.23, 1, 0.32, 1, duration: 0.13), value: isPressed)
        .onHover { hovered = $0 }
        .sensoryFeedback(.impact(weight: .light), trigger: isPressed) { _, pressed in pressed }
        .onChange(of: running, initial: true) { _, running in
            update(running: running)
        }
        .task(id: settleToken) {
            guard settleToken > 0 else { return }
            try? await Task.sleep(for: .seconds(glow.duration + 0.05))
            guard !Task.isCancelled else { return }
            if drift.phase == .settling {
                drift = Drift(phase: .rest, from: 0)
            }
            settling = false
        }
    }

    private func update(running: Bool) {
        let now = Date.now
        if reduceMotion {
            glow.retarget(running ? 1 : 0, at: now, duration: 0.2, curve: .easeInOut)
            settling = true
            settleToken += 1
            return
        }
        // Dropping whole cycles keeps the value small without moving the gradient.
        let from = drift.value(at: now).truncatingRemainder(dividingBy: 1)
        if running {
            drift = Drift(phase: .running, from: from, start: now)
            glow.retarget(1, at: now, duration: 0.35, curve: .easeInOut)
        } else if drift.phase == .running {
            var runout = from.rounded(.up) - from
            if runout < Metrics.minRunout { runout += 1 }
            let duration = 1.25 * runout * Metrics.cycleSeconds
            drift = Drift(phase: .settling, from: from, to: from + runout, start: now, duration: duration)
            glow.retarget(0, at: now, duration: duration, curve: .easeIn)
            settling = true
            settleToken += 1
        }
    }

    private func draw(_ context: GraphicsContext, size: CGSize, phase: Double, glow: Double) {
        let bleed = Metrics.bleed
        let rect = CGRect(x: bleed, y: bleed, width: size.width - bleed * 2, height: size.height - bleed * 2)
        let pill = capsule(rect)

        // The gradient slides left, so the colours travel from the right edge to the left one.
        let cycle = Metrics.span * rect.width
        let startX = rect.minX - phase.truncatingRemainder(dividingBy: 1) * cycle
        let shading = GraphicsContext.Shading.linearGradient(
            driftGradient,
            startPoint: CGPoint(x: startX, y: rect.midY),
            endPoint: CGPoint(x: startX + cycle * 2, y: rect.midY)
        )

        var halo = context
        halo.opacity = glow * Metrics.glowOpacity
        halo.addFilter(.blur(radius: Metrics.glowBlur))
        halo.fill(pill, with: shading)

        context.fill(pill, with: shading)
    }
}

#Preview {
    ZStack {
        Color.black.ignoresSafeArea()
        GradientSubscribeButton(autoPlay: true)
    }
}

import SwiftUI

/// Black pill with a white highlight on its top edge and a blue/magenta light that runs around the border.
struct GlowBorderButton: View {
    enum Mode {
        /// The light runs around the border while pressed/hovered, then finishes its lap and
        /// settles back into the top highlight.
        case press
        /// The light never stops.
        case always
    }

    var label = "Let me glow"
    var mode = Mode.press
    var action: () -> Void = {}

    var body: some View {
        Button(action: action) { Text(label) }
            .buttonStyle(Style(label: label, mode: mode))
    }

    private struct Style: ButtonStyle {
        let label: String
        let mode: Mode

        func makeBody(configuration: Configuration) -> some View {
            Surface(label: label, mode: mode, isPressed: configuration.isPressed)
        }
    }
}

private enum Metrics {
    static let height = 54.0
    static let padX = 38.0
    /// The canvas overflows the button by this much on every side to fit the bloom and the shadow.
    static let bleed = 36.0
    static let lapSeconds = 2.2
    /// How far (in laps) the light must still travel after release, so the run-out isn't hidden under the thumb.
    static let minRunout = 0.5
    /// The coloured head leads the white highlight by about a third of a lap.
    static let cometOffset = 0.75
}

private let clearWhite = Color.white.opacity(0)
private let clearBlue = Color.rgba(48, 32, 255, 0)

// Conic stops run clockwise from 3 o'clock, so 0.75 is the top of the button.
private let highlightGradient = Gradient(stops: [
    .init(color: clearBlue, location: 0.55),
    .init(color: Color(hex: 0x3a26ff), location: 0.585),
    .init(color: Color(hex: 0xb266ff), location: 0.615),
    .init(color: .white, location: 0.645),
    .init(color: .white, location: 0.855),
    .init(color: Color(hex: 0xb266ff), location: 0.885),
    .init(color: Color(hex: 0x3a26ff), location: 0.915),
    .init(color: clearBlue, location: 0.95),
])
private let highlightBloom = Gradient(stops: [
    .init(color: clearWhite, location: 0.63),
    .init(color: .white.opacity(0.85), location: 0.69),
    .init(color: .white.opacity(0.85), location: 0.81),
    .init(color: clearWhite, location: 0.87),
])
private let cometGradient = Gradient(stops: [
    .init(color: clearBlue, location: 0),
    .init(color: .rgba(48, 32, 255, 0.6), location: 0.08),
    .init(color: Color(hex: 0x3a26ff), location: 0.24),
    .init(color: Color(hex: 0xea3dff), location: 0.3),
    .init(color: Color(hex: 0xea3dff), location: 0.37),
    .init(color: Color(hex: 0x4a2cff), location: 0.43),
    .init(color: clearBlue, location: 0.5),
    .init(color: clearBlue, location: 1),
])

/// Where the light is on its lap. Measured in laps; whole numbers put the white highlight back at the top.
private struct Spin {
    enum Phase { case rest, running, settling }

    var phase = Phase.rest
    var from = 0.0
    var to = 0.0
    var start = Date.distantPast
    var duration = 0.0

    func angle(at date: Date) -> Double {
        let elapsed = date.timeIntervalSince(start)
        switch phase {
        case .rest:
            return from
        case .running:
            return from + max(elapsed, 0) / Metrics.lapSeconds
        case .settling:
            let t = duration > 0 ? min(max(elapsed / duration, 0), 1) : 1
            return from + (to - from) * Ease.settle.value(at: t)
        }
    }
}

private struct Surface: View {
    let label: String
    let mode: GlowBorderButton.Mode
    let isPressed: Bool

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var hovered = false
    @State private var spin = Spin()
    @State private var comet = Tween(0)
    @State private var settling = false
    @State private var settleToken = 0

    private var running: Bool { mode == .always || isPressed || hovered }

    var body: some View {
        TimelineView(.animation(paused: !(running || settling))) { timeline in
            let angle = spin.angle(at: timeline.date)
            let cometOpacity = comet.value(at: timeline.date)

            Text(label)
                .font(.system(size: 17, weight: .medium))
                .lineLimit(1)
                .foregroundStyle(.white)
                .padding(.horizontal, Metrics.padX)
                .frame(height: Metrics.height)
                .background {
                    Canvas { context, size in
                        draw(context, size: size, angle: angle, comet: cometOpacity)
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
            try? await Task.sleep(for: .seconds(comet.duration + 0.05))
            guard !Task.isCancelled else { return }
            if spin.phase == .settling {
                spin = Spin(phase: .rest, from: 0)
            }
            settling = false
        }
    }

    private func update(running: Bool) {
        let now = Date.now
        if reduceMotion {
            comet.retarget(running ? 1 : 0, at: now, duration: 0.2, curve: .easeInOut)
            settling = true
            settleToken += 1
            return
        }
        // Dropping whole laps keeps the angle small without moving the light.
        let from = spin.angle(at: now).truncatingRemainder(dividingBy: 1)
        if running {
            spin = Spin(phase: .running, from: from, start: now)
            comet.retarget(1, at: now, duration: 0.35, curve: .easeInOut)
        } else if spin.phase == .running {
            var runout = from.rounded(.up) - from
            if runout < Metrics.minRunout { runout += 1 }
            let duration = 1.25 * runout * Metrics.lapSeconds
            spin = Spin(phase: .settling, from: from, to: from + runout, start: now, duration: duration)
            comet.retarget(0, at: now, duration: duration, curve: .easeInOut)
            settling = true
            settleToken += 1
        }
    }

    private func draw(_ context: GraphicsContext, size: CGSize, angle: Double, comet: Double) {
        let bleed = Metrics.bleed
        let rect = CGRect(x: bleed, y: bleed, width: size.width - bleed * 2, height: size.height - bleed * 2)
        let center = CGPoint(x: rect.midX, y: rect.midY)
        let pill = capsule(rect)
        let rim = capsule(rect.insetBy(dx: 1, dy: 1))

        var shadow = context
        shadow.addFilter(.blur(radius: 14))
        shadow.fill(capsule(rect.offsetBy(dx: 0, dy: 6)), with: .color(.black.opacity(0.55)))

        context.fill(pill, with: .color(.black))
        context.stroke(capsule(rect.insetBy(dx: 0.5, dy: 0.5)), with: .color(.white.opacity(0.035)), lineWidth: 1)

        let cometShading = GraphicsContext.Shading.conicGradient(
            cometGradient, center: center, angle: Angle(radians: tau * (angle + Metrics.cometOffset))
        )
        var cometLayer = context
        cometLayer.opacity = comet
        // The blur is a filter (applied after shading) so the bloom follows the border instead of
        // smearing into an angular wedge.
        var cometBloom = cometLayer
        cometBloom.addFilter(.blur(radius: 4.5))
        cometBloom.stroke(pill, with: cometShading, lineWidth: 4)
        cometLayer.stroke(rim, with: cometShading, lineWidth: 2)

        let spin = Angle(radians: tau * angle)
        var bloom = context
        bloom.addFilter(.blur(radius: 5))
        bloom.stroke(pill, with: .conicGradient(highlightBloom, center: center, angle: spin), lineWidth: 3)
        context.stroke(rim, with: .conicGradient(highlightGradient, center: center, angle: spin), lineWidth: 2)
    }
}

#Preview {
    ZStack {
        Color(hex: 0x121317).ignoresSafeArea()
        GlowBorderButton(mode: .always)
    }
}

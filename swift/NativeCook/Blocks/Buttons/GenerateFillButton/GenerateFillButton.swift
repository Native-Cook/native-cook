import SwiftUI

/// Black "Generate" pill with a sparkle. Tapping it sweeps a violet-to-cyan gradient in from the
/// left behind a rounded edge, the label becomes "Generating" and the colours keep flowing until
/// the run ends and the pill fades back to black.
struct GenerateFillButton: View {
    var label = "Generate"
    /// Shown while the gradient is flowing.
    var activeLabel = "Generating"
    /// Controlled state: `true` sweeps the gradient in and keeps it flowing, `false` returns to
    /// black. Leave nil and a tap runs one generation of `duration` seconds by itself.
    var generating: Bool? = nil
    var duration = 5.6
    /// Replays a generation by itself with a pause between runs.
    var autoPlay = false
    var action: () -> Void = {}

    @State private var own = false

    private var active: Bool { generating ?? own }

    var body: some View {
        Button {
            action()
            run()
        } label: {
            Text(active ? activeLabel : label)
        }
        .buttonStyle(Style(label: label, activeLabel: activeLabel, active: active))
        .accessibilityLabel(active ? activeLabel : label)
        .task(id: autoPlay ? (own ? 1 : 0) : -1) {
            guard autoPlay, !own else { return }
            try? await Task.sleep(for: .seconds(Metrics.autoPlayPause + Metrics.fadeSeconds))
            if !Task.isCancelled { run() }
        }
    }

    /// An uncontrolled run: generate for `duration`, then return. Ignored while one is in flight.
    private func run() {
        guard generating == nil, !own else { return }
        own = true
        Task {
            try? await Task.sleep(for: .seconds(duration))
            own = false
        }
    }

    private struct Style: ButtonStyle {
        let label: String
        let activeLabel: String
        let active: Bool

        func makeBody(configuration: Configuration) -> some View {
            Surface(label: label, activeLabel: activeLabel, active: active, isPressed: configuration.isPressed)
        }
    }
}

private enum Metrics {
    static let width = 168.0
    static let height = 58.0
    /// The canvas overflows the button by this much on every side so its edge is not clipped.
    static let bleed = 4.0
    /// Radius of the gradient's rounded leading edge as it sweeps in from the left.
    static let lead = 44.0
    static let sweepSeconds = 2.4
    static let fadeSeconds = 0.5
    static let autoPlayPause = 1.5
    static let iconX = 29.0
    /// Where the liquid rests when motion is reduced.
    static let stillTime = 1.0

    /// Soft blobs of colour that wander over the base gradient: colour, radius, centre x, x swing,
    /// x speed, x phase, centre y offset, y swing, y speed; positions as fractions of the size.
    static let blobs: [(r: Double, g: Double, b: Double, radius: Double, bx: Double, sx: Double, vx: Double, px: Double, by: Double, sy: Double, vy: Double)] = [
        (40, 70, 240, 0.9, 0.45, 0.3, 0.6, 2, 0, 0.3, 0.8),
        (10, 200, 240, 0.95, 0.62, 0.25, 0.9, 0, 0, 0.25, 0.7),
        (165, 60, 245, 0.85, 0.1, 0.12, 0.7, 1, 0, 0.15, 0.5),
        (20, 210, 180, 0.6, 0.95, 0.08, 0.5, 0, 0.3, 0.1, 0.6),
    ]
}

private struct Surface: View {
    let label: String
    let activeLabel: String
    let active: Bool
    let isPressed: Bool

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var origin = Date.now
    @State private var sweep = Tween(0)
    @State private var lit = Tween(0)
    @State private var mix = Tween(0)
    @State private var flowing = false

    var body: some View {
        TimelineView(.animation(paused: !flowing)) { timeline in
            let date = timeline.date
            // The sweep and the fades are the state change itself, so they stay with reduced
            // motion; only the wandering colours and the twinkle are frozen.
            let clock = reduceMotion ? Metrics.stillTime : Metrics.stillTime + date.timeIntervalSince(origin)
            let mixNow = mix.value(at: date)

            ZStack(alignment: .leading) {
                Canvas { context, _ in
                    draw(context, sweep: sweep.value(at: date), lit: lit.value(at: date), mix: mixNow, clock: clock)
                }
                .padding(-Metrics.bleed)
                .allowsHitTesting(false)

                text(label).opacity(1 - mixNow).padding(.leading, 48)
                // The longer word starts a little further left so it stays inside the pill.
                text(activeLabel).opacity(mixNow).padding(.leading, 44)
            }
            .frame(width: Metrics.width, height: Metrics.height, alignment: .leading)
        }
        .contentShape(Capsule())
        .scaleEffect(isPressed ? 0.97 : 1)
        .animation(.timingCurve(0.23, 1, 0.32, 1, duration: 0.13), value: isPressed)
        .sensoryFeedback(.impact(weight: .light), trigger: isPressed) { _, pressed in pressed }
        .onChange(of: active, initial: true) {
            if active {
                sweep = Tween(0)
                lit = Tween(1)
                sweep.retarget(1, duration: Metrics.sweepSeconds, curve: .easeInOut)
                mix.retarget(1, duration: 0.16, curve: .easeInOut)
                flowing = true
            } else {
                lit.retarget(0, duration: Metrics.fadeSeconds, curve: .easeInOut)
                mix.retarget(0, at: .now.addingTimeInterval(0.14), duration: 0.16, curve: .easeInOut)
            }
        }
        // Keep the colours moving until the fade back to black has finished.
        .task(id: active) {
            guard !active, flowing else { return }
            try? await Task.sleep(for: .seconds(Metrics.fadeSeconds + 0.1))
            if !Task.isCancelled { flowing = false }
        }
    }

    private func text(_ string: String) -> some View {
        Text(string)
            .font(.system(size: 21, weight: .bold))
            .kerning(-0.2)
            .lineLimit(1)
            .fixedSize()
            .foregroundStyle(.white)
            .accessibilityHidden(true)
    }

    private func sparkle(_ c: CGPoint, _ r: Double, k: Double = 0.3) -> Path {
        let d = r * k
        var path = Path()
        path.move(to: CGPoint(x: c.x, y: c.y - r))
        path.addQuadCurve(to: CGPoint(x: c.x + r, y: c.y), control: CGPoint(x: c.x + d, y: c.y - d))
        path.addQuadCurve(to: CGPoint(x: c.x, y: c.y + r), control: CGPoint(x: c.x + d, y: c.y + d))
        path.addQuadCurve(to: CGPoint(x: c.x - r, y: c.y), control: CGPoint(x: c.x - d, y: c.y + d))
        path.addQuadCurve(to: CGPoint(x: c.x, y: c.y - r), control: CGPoint(x: c.x - d, y: c.y - d))
        path.closeSubpath()
        return path
    }

    private func draw(_ context: GraphicsContext, sweep: Double, lit: Double, mix: Double, clock t: Double) {
        let rect = CGRect(x: Metrics.bleed, y: Metrics.bleed, width: Metrics.width, height: Metrics.height)
        let pill = capsule(rect)
        context.fill(pill, with: .color(.black))

        var inner = context
        inner.clip(to: pill)
        // Soft grey sheen in the lower right corner of the black pill.
        inner.fill(
            Path(rect),
            with: .radialGradient(
                Gradient(stops: [
                    .init(color: .rgba(125, 125, 125, 0.75), location: 0),
                    .init(color: .rgba(70, 70, 70, 0.35), location: 0.45),
                    .init(color: .rgba(0, 0, 0, 0), location: 1),
                ]),
                center: CGPoint(x: rect.minX + rect.width * 0.92, y: rect.minY + rect.height * 1.05),
                startRadius: 0, endRadius: rect.width * 0.55
            )
        )

        if lit > 0 {
            var layer = inner
            layer.opacity = lit
            layer.drawLayer { fill in
                // Everything left of a big rounded edge that travels from the left end to past the right end.
                let edge = -2 + sweep * (rect.width + 14)
                let lead = CGRect(x: rect.minX - 200, y: rect.midY - Metrics.lead, width: 200 + edge, height: Metrics.lead * 2)
                fill.clip(to: Path(roundedRect: lead, cornerRadius: Metrics.lead))
                fill.fill(
                    Path(rect),
                    with: .linearGradient(
                        Gradient(stops: [
                            .init(color: Color(hex: 0x8b3cf5), location: 0),
                            .init(color: Color(hex: 0x5a5cf2), location: 0.22),
                            .init(color: Color(hex: 0x2f86f0), location: 0.5),
                            .init(color: Color(hex: 0x19a8f0), location: 0.78),
                            .init(color: Color(hex: 0x12c8dc), location: 1),
                        ]),
                        startPoint: CGPoint(x: rect.minX, y: 0), endPoint: CGPoint(x: rect.maxX, y: 0)
                    )
                )
                for (index, blob) in Metrics.blobs.enumerated() {
                    let r = rect.height * blob.radius
                    let c = CGPoint(
                        x: rect.minX + rect.width * (blob.bx + blob.sx * sin(t * blob.vx * 1.6 + blob.px)),
                        y: rect.midY + rect.height * (blob.by + blob.sy * cos(t * blob.vy * 1.6 + Double(index)))
                    )
                    fill.fillEllipse(
                        center: c, radii: CGSize(width: r, height: r),
                        gradient: Gradient(stops: [
                            .init(color: .rgba(blob.r, blob.g, blob.b, 0.85), location: 0),
                            .init(color: .rgba(blob.r, blob.g, blob.b, 0.4), location: 0.5),
                            .init(color: .rgba(blob.r, blob.g, blob.b, 0), location: 1),
                        ])
                    )
                }
            }
        }

        let icon = CGPoint(x: rect.minX + Metrics.iconX, y: rect.midY)
        context.fill(sparkle(icon, 10), with: .color(.white))
        // Two small stars twinkle beside the big one while it is generating.
        let twinkleA = mix * (0.5 + 0.5 * sin(t * 5.2))
        let twinkleB = mix * (0.5 + 0.5 * sin(t * 4.1 + 2.4))
        context.fill(sparkle(CGPoint(x: icon.x - 9.5, y: icon.y + 8.5), 2.4), with: .color(.white.opacity(twinkleA)))
        context.fill(sparkle(CGPoint(x: icon.x - 8.5, y: icon.y - 9), 1.8), with: .color(.white.opacity(twinkleB)))
    }
}

#Preview {
    ZStack {
        Color.white.ignoresSafeArea()
        GenerateFillButton(autoPlay: true)
    }
}

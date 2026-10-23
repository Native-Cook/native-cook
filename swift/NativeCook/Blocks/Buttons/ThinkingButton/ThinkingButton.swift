import SwiftUI

/// AI status pill. Its label steps through "Thinking…", "Searching…", "Planning…" and each phase
/// fills the pill with its own flowing liquid (rainbow wave, fluted silver, blue meeting red) under
/// a glassy rim.
struct ThinkingButton: View {
    /// One label per look: rainbow, silver, blue-to-red.
    var labels = ["Thinking...", "Searching...", "Planning..."]
    /// Controlled phase index (0–2). Leave nil to let the button keep its own.
    var phase: Binding<Int>? = nil
    /// Steps through the three phases by itself. A tap always advances to the next one.
    var autoPlay = false
    var action: () -> Void = {}

    @State private var own = 0

    private var current: Int {
        (((phase?.wrappedValue ?? own) % 3) + 3) % 3
    }

    var body: some View {
        Button {
            advance()
            action()
        } label: {
            Text(labels[current])
        }
        .buttonStyle(Style(labels: labels, current: current, autoPlay: autoPlay, advance: advance))
        .accessibilityLabel(labels[current])
    }

    private func advance() {
        let next = (current + 1) % 3
        if let phase { phase.wrappedValue = next } else { own = next }
    }

    private struct Style: ButtonStyle {
        let labels: [String]
        let current: Int
        let autoPlay: Bool
        let advance: () -> Void

        func makeBody(configuration: Configuration) -> some View {
            Surface(labels: labels, current: current, autoPlay: autoPlay, isPressed: configuration.isPressed, advance: advance)
        }
    }
}

private enum Metrics {
    static let width = 220.0
    static let height = 64.0
    /// The canvas overflows the button by this much on every side so the rim is not clipped.
    static let bleed = 12.0
    /// The liquid is drawn this much past the pill so blurred edges never show inside it.
    static let spill = 24.0
    static let phaseSeconds = 2.8
    static let fadeSeconds = 0.5
    /// Where the liquid rests when motion is reduced.
    static let stillTime = 1.2
    static let steps = 24

    static let rainbow = Gradient(stops: [
        .init(color: Color(hex: 0xf28a1c), location: 0.02),
        .init(color: Color(hex: 0xe8c21a), location: 0.2),
        .init(color: Color(hex: 0x58d04a), location: 0.36),
        .init(color: Color(hex: 0x1fc7c4), location: 0.52),
        .init(color: Color(hex: 0x2f6fe8), location: 0.62),
        .init(color: Color(hex: 0xc035e0), location: 0.8),
        .init(color: Color(hex: 0x7a2cf0), location: 1),
    ])

    // Fluted glass over the silver look: each strip refracts the bands by its own amount.
    static let fluteShift = [0, 0.1, -0.05, 0.14, 0.03, -0.08]
    static let fluteTint = [0, 0.05, 0.02, 0.07, 0.02, 0.04]
    static let silver = Gradient(stops: [
        .init(color: Color(hex: 0x5d5d60), location: 0),
        .init(color: Color(hex: 0x8e8e90), location: 0.2),
        .init(color: Color(hex: 0xb9b7b3), location: 0.36),
        .init(color: Color(hex: 0x7a5f45), location: 0.42),
        .init(color: Color(hex: 0x0c0b0b), location: 0.47),
        .init(color: Color(hex: 0x0a0a0b), location: 0.6),
        .init(color: Color(hex: 0x2a2a2d), location: 0.75),
        .init(color: Color(hex: 0x5d5d60), location: 1),
    ])
    // Band normal (the bands run about 24° off horizontal) and the distance between two bands.
    static let bandNX = -0.41
    static let bandNY = 0.91
    static let bandPeriod = 130.0
}

private struct Surface: View {
    let labels: [String]
    let current: Int
    let autoPlay: Bool
    let isPressed: Bool
    let advance: () -> Void

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var origin = Date.now
    @State private var fades = [Tween(1), Tween(0), Tween(0)]
    @State private var fading = false

    var body: some View {
        // The liquid always flows; with reduced motion the clock only runs during a cross-fade.
        TimelineView(.animation(paused: reduceMotion && !fading)) { timeline in
            let date = timeline.date
            let clock = reduceMotion ? Metrics.stillTime : Metrics.stillTime + date.timeIntervalSince(origin)
            let opacity = fades.map { $0.value(at: date) }

            ZStack {
                Canvas { context, _ in draw(context, clock: clock, opacity: opacity) }
                    .padding(-Metrics.bleed)
                    .allowsHitTesting(false)
                ForEach(0..<3, id: \.self) { index in
                    Text(labels[index])
                        .font(.system(size: 22, weight: .medium))
                        .kerning(0.1)
                        .lineLimit(1)
                        .foregroundStyle(.white)
                        .shadow(color: .black.opacity(0.28), radius: 2, y: 1)
                        .opacity(opacity[index])
                        .accessibilityHidden(true)
                }
            }
            .frame(width: Metrics.width, height: Metrics.height)
        }
        .contentShape(Capsule())
        .scaleEffect(isPressed ? 0.97 : 1)
        .animation(.timingCurve(0.23, 1, 0.32, 1, duration: 0.13), value: isPressed)
        .sensoryFeedback(.impact(weight: .light), trigger: isPressed) { _, pressed in pressed }
        .onChange(of: current) {
            for index in 0..<3 {
                fades[index].retarget(index == current ? 1 : 0, duration: Metrics.fadeSeconds, curve: .easeInOut)
            }
            fading = true
        }
        .task(id: fading) {
            guard fading else { return }
            try? await Task.sleep(for: .seconds(Metrics.fadeSeconds + 0.05))
            if !Task.isCancelled { fading = false }
        }
        // Keyed on `current` so a tap restarts the wait instead of being cut short.
        .task(id: autoPlay ? current : -1) {
            guard autoPlay else { return }
            try? await Task.sleep(for: .seconds(Metrics.phaseSeconds))
            if !Task.isCancelled { advance() }
        }
    }

    /// Height of the rainbow's surface at `u` (0–1 across the pill), as a fraction of the height.
    private func waveLevel(_ u: Double, _ t: Double) -> Double {
        0.5 + 0.12 * sin(tau * (u * 1.1 + t * 0.21)) + 0.07 * sin(tau * (u * 2.3 - t * 0.33) + 1.3)
    }

    /// Everything below the rainbow's surface, lowered by `drop`.
    private func wavePath(_ rect: CGRect, _ t: Double, drop: Double) -> Path {
        let x0 = rect.minX - Metrics.spill
        let x1 = rect.maxX + Metrics.spill
        var path = Path()
        path.move(to: CGPoint(x: x0, y: rect.maxY + Metrics.spill))
        for i in 0...Metrics.steps {
            let x = x0 + (x1 - x0) * Double(i) / Double(Metrics.steps)
            path.addLine(to: CGPoint(x: x, y: rect.minY + rect.height * waveLevel((x - rect.minX) / rect.width, t) + drop))
        }
        path.addLine(to: CGPoint(x: x1, y: rect.maxY + Metrics.spill))
        path.closeSubpath()
        return path
    }

    /// Where the blue meets the red at `v` (0–1 down the pill), as a fraction of the width.
    private func seamLevel(_ v: Double, _ t: Double) -> Double {
        0.46 + 0.05 * sin(tau * (v * 0.9 + t * 0.27)) + 0.03 * sin(tau * (v * 1.7 - t * 0.41) + 1)
    }

    /// The seam between blue and red; `closed` adds everything to its right (the red side).
    private func seamPath(_ rect: CGRect, _ t: Double, closed: Bool) -> Path {
        let y0 = rect.minY - Metrics.spill
        let y1 = rect.maxY + Metrics.spill
        var path = Path()
        for i in 0...Metrics.steps {
            let y = y0 + (y1 - y0) * Double(i) / Double(Metrics.steps)
            let point = CGPoint(x: rect.minX + rect.width * seamLevel((y - rect.minY) / rect.height, t), y: y)
            if i == 0 { path.move(to: point) } else { path.addLine(to: point) }
        }
        if closed {
            path.addLine(to: CGPoint(x: rect.maxX + Metrics.spill, y: y1))
            path.addLine(to: CGPoint(x: rect.maxX + Metrics.spill, y: y0))
            path.closeSubpath()
        }
        return path
    }

    private func draw(_ context: GraphicsContext, clock t: Double, opacity: [Double]) {
        let rect = CGRect(x: Metrics.bleed, y: Metrics.bleed, width: Metrics.width, height: Metrics.height)
        let pill = capsule(rect)
        let top = CGPoint(x: 0, y: rect.minY)
        let bottom = CGPoint(x: 0, y: rect.maxY)

        var inner = context
        inner.clip(to: pill)
        // Keeps the pill opaque half-way through a cross-fade.
        inner.fill(Path(rect), with: .color(Color(hex: 0x0b0b12)))

        if opacity[0] > 0 {
            var look = inner
            look.opacity = opacity[0]
            look.drawLayer { layer in
                layer.fill(
                    Path(rect),
                    with: .linearGradient(Gradient(colors: [Color(hex: 0x07031c), Color(hex: 0x0e0632)]), startPoint: top, endPoint: bottom)
                )
                // The colours drift a little sideways under the surface.
                let drift = 14 * sin(t * 0.6)
                var body = layer
                body.addFilter(.blur(radius: 5))
                body.fill(
                    wavePath(rect, t, drop: 0),
                    with: .linearGradient(
                        Metrics.rainbow,
                        startPoint: CGPoint(x: rect.minX + drift, y: 0), endPoint: CGPoint(x: rect.maxX + drift, y: 0)
                    )
                )
                var fog = layer
                fog.addFilter(.blur(radius: 8))
                fog.fill(
                    wavePath(rect, t, drop: rect.height * 0.2),
                    with: .linearGradient(
                        Gradient(stops: [
                            .init(color: .rgba(190, 190, 198, 0), location: 0),
                            .init(color: .rgba(190, 190, 198, 0.7), location: 0.6),
                            .init(color: .rgba(226, 230, 229, 1), location: 1),
                        ]),
                        startPoint: CGPoint(x: 0, y: rect.minY + rect.height * 0.55), endPoint: bottom
                    )
                )
            }
        }

        if opacity[1] > 0 {
            var look = inner
            look.opacity = opacity[1]
            look.drawLayer { layer in
                let flutes = Metrics.fluteShift.count
                let w = rect.width / Double(flutes)
                for index in 0..<flutes {
                    let s = (t / 3.8).truncatingRemainder(dividingBy: 1) * Metrics.bandPeriod
                        + Metrics.fluteShift[index] * Metrics.bandPeriod
                    let e = s + Metrics.bandPeriod
                    let x = rect.minX + w * Double(index)
                    // Half a point of overlap hides the seam between strips.
                    layer.fill(
                        Path(CGRect(x: x, y: rect.minY, width: w + 0.5, height: rect.height)),
                        with: .linearGradient(
                            Metrics.silver,
                            startPoint: CGPoint(x: rect.midX + Metrics.bandNX * s, y: rect.midY + Metrics.bandNY * s),
                            endPoint: CGPoint(x: rect.midX + Metrics.bandNX * e, y: rect.midY + Metrics.bandNY * e),
                            options: .repeat
                        )
                    )
                    layer.fill(
                        Path(CGRect(x: x, y: rect.minY, width: w, height: rect.height)),
                        with: .color(.white.opacity(Metrics.fluteTint[index]))
                    )
                }
            }
        }

        if opacity[2] > 0 {
            var look = inner
            look.opacity = opacity[2]
            look.drawLayer { layer in
                layer.fill(
                    Path(rect),
                    with: .linearGradient(
                        Gradient(stops: [
                            .init(color: Color(hex: 0x1b3fd0), location: 0),
                            .init(color: Color(hex: 0x1a8fe6), location: 0.14),
                            .init(color: Color(hex: 0x14b6dc), location: 0.38),
                            .init(color: Color(hex: 0x19b0d8), location: 1),
                        ]),
                        startPoint: CGPoint(x: rect.minX, y: 0), endPoint: CGPoint(x: rect.maxX, y: 0)
                    )
                )
                var red = layer
                red.addFilter(.blur(radius: 4))
                red.fill(
                    seamPath(rect, t, closed: true),
                    with: .linearGradient(
                        Gradient(stops: [
                            .init(color: Color(hex: 0xea5a50), location: 0),
                            .init(color: Color(hex: 0xde3a32), location: 0.3),
                            .init(color: Color(hex: 0xd41a1a), location: 0.7),
                            .init(color: Color(hex: 0xcf1717), location: 1),
                        ]),
                        startPoint: CGPoint(x: rect.minX + rect.width * 0.42, y: 0), endPoint: CGPoint(x: rect.maxX, y: 0)
                    )
                )
                var seam = layer
                seam.addFilter(.blur(radius: 5))
                seam.stroke(seamPath(rect, t, closed: false), with: .color(.rgba(255, 150, 205, 0.55)), lineWidth: 5)
            }
        }

        // Glass: a soft light hugging the inside of the edge, strongest along the bottom.
        var glass = inner
        glass.addFilter(.blur(radius: 3.5))
        glass.stroke(
            pill,
            with: .linearGradient(
                Gradient(stops: [
                    .init(color: .white.opacity(0.1), location: 0),
                    .init(color: .white.opacity(0.04), location: 0.5),
                    .init(color: .white.opacity(0.4), location: 1),
                ]),
                startPoint: top, endPoint: bottom
            ),
            lineWidth: 7
        )
        context.stroke(
            capsule(rect.insetBy(dx: 0.5, dy: 0.5)),
            with: .linearGradient(
                Gradient(stops: [
                    .init(color: .white.opacity(0.42), location: 0),
                    .init(color: .white.opacity(0.16), location: 0.5),
                    .init(color: .white.opacity(0.7), location: 1),
                ]),
                startPoint: top, endPoint: bottom
            ),
            lineWidth: 1
        )
    }
}

#Preview {
    ZStack {
        Color(hex: 0x17191b).ignoresSafeArea()
        ThinkingButton(autoPlay: true)
    }
}

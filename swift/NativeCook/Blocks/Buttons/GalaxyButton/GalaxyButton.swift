import SwiftUI

/// "JOIN TODAY!" pill on deep navy: a cyan light travels around the border inside a soft nebula with drifting dust.
struct GalaxyButton: View {
    var label = "JOIN TODAY!"
    /// Keeps the light circling. When off it appears on press/hover and lingers briefly.
    var autoPlay = false
    /// How long the light stays after the finger lifts, so it isn't hidden under the thumb.
    var linger: Duration = .milliseconds(1600)
    var action: () -> Void = {}

    var body: some View {
        Button(action: action) { Text(label) }
            .buttonStyle(Style(label: label, autoPlay: autoPlay, linger: linger))
    }

    private struct Style: ButtonStyle {
        let label: String
        let autoPlay: Bool
        let linger: Duration

        func makeBody(configuration: Configuration) -> some View {
            Surface(label: label, autoPlay: autoPlay, linger: linger, isPressed: configuration.isPressed)
        }
    }
}

private enum Metrics {
    static let height = 58.0
    static let padX = 46.0
    /// The canvas overflows the button by this much on every side to fit the nebula and its sparkles.
    static let bleed = 92.0
    /// One trip of the light around the border.
    static let lapSeconds = 7.0
    /// The light travels just inside the border, where the reference's cursor sits.
    static let lightInset = 5.0
    static let nebulaRadius = 86.0
    static let rimReach = 78.0
    static let sparkleGroups = 3
    /// Where the light rests (as a fraction of a lap) when the system asks for reduced motion.
    static let stillLap = 0.3
}

private struct Surface: View {
    let label: String
    let autoPlay: Bool
    let linger: Duration
    let isPressed: Bool

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var hovered = false
    @State private var lingering = false
    @State private var lingerTask: Task<Void, Never>?
    @State private var progress = Tween(0)
    @State private var settling = false
    @State private var settleToken = 0

    private var lit: Bool { autoPlay || isPressed || hovered || lingering }

    var body: some View {
        TimelineView(.animation(paused: !(lit || settling))) { timeline in
            let p = progress.value(at: timeline.date)
            // Measured in laps around the border; only the fractional part matters.
            let loop = reduceMotion
                ? Metrics.stillLap
                : (timeline.date.timeIntervalSinceReferenceDate / Metrics.lapSeconds)
                    .truncatingRemainder(dividingBy: 1)

            Text(label)
                .font(.system(size: 15, weight: .regular))
                .kerning(1)
                .lineLimit(1)
                .foregroundStyle(Color(hex: 0xdcdade))
                .padding(.horizontal, Metrics.padX)
                .frame(height: Metrics.height)
                .background {
                    Canvas { context, size in
                        draw(context, size: size, p: p, loop: loop)
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
        .onChange(of: isPressed) { _, pressed in
            lingerTask?.cancel()
            if pressed {
                lingering = true
            } else {
                lingerTask = Task {
                    try? await Task.sleep(for: linger)
                    if !Task.isCancelled { lingering = false }
                }
            }
        }
        .onChange(of: lit, initial: true) { _, lit in
            progress.retarget(lit ? 1 : 0, duration: lit ? 0.42 : 0.6, curve: Ease.out)
            settling = true
            settleToken += 1
        }
        .task(id: settleToken) {
            // Keep the timeline running until the fade has finished, then let it pause.
            try? await Task.sleep(for: .seconds(progress.duration + 0.05))
            if !Task.isCancelled { settling = false }
        }
    }

    private func draw(_ context: GraphicsContext, size: CGSize, p: Double, loop: Double) {
        let bleed = Metrics.bleed
        let rect = CGRect(x: bleed, y: bleed, width: size.width - bleed * 2, height: size.height - bleed * 2)
        let pill = capsule(rect)
        let light = capsulePoint(lap: loop, in: rect, inset: Metrics.lightInset)

        var on = context
        on.opacity = p

        on.fillEllipse(
            center: light,
            radii: CGSize(width: Metrics.nebulaRadius, height: Metrics.nebulaRadius),
            gradient: Gradient(stops: [
                .init(color: .rgba(48, 128, 172, 0.46), location: 0),
                .init(color: .rgba(30, 86, 132, 0.18), location: 0.5),
                .init(color: .rgba(20, 60, 110, 0), location: 1),
            ])
        )
        // Each group of dust wanders a few points around the light and twinkles at its own pace.
        for (index, path) in sparkles.enumerated() {
            let i = Double(index)
            let wave = 0.5 + 0.5 * sin(tau * (i + 4) * loop + i * 1.9)
            var layer = context
            layer.opacity = p * (0.15 + 0.55 * wave)
            layer.translateBy(
                x: light.x + 4 * sin(tau * (i + 2) * loop + i * 2.1),
                y: light.y + 4 * cos(tau * (i + 3) * loop + i * 1.3)
            )
            layer.fill(path, with: .color(Color(hex: 0xa9d8f4)))
        }

        context.fill(pill, with: .color(Color(hex: 0x050308)))
        // The light tints the glass from the inside.
        var tint = on
        tint.clip(to: pill)
        tint.fillEllipse(
            center: light,
            radii: CGSize(width: Metrics.rimReach, height: Metrics.rimReach),
            gradient: Gradient(stops: [
                .init(color: .rgba(26, 58, 74, 0.9), location: 0),
                .init(color: .rgba(22, 48, 64, 0.4), location: 0.5),
                .init(color: .rgba(20, 44, 60, 0), location: 1),
            ])
        )
        context.stroke(capsule(rect.insetBy(dx: 0.75, dy: 0.75)), with: .color(.rgba(60, 54, 78, 0.8)), lineWidth: 1.5)

        // Only the stretch of border near the light is lit: a radial gradient centred on it paints the stroke.
        var bloom = on
        bloom.addFilter(.blur(radius: 3.5))
        bloom.stroke(
            pill,
            with: .radialGradient(
                Gradient(stops: [
                    .init(color: .rgba(64, 170, 240, 0.75), location: 0),
                    .init(color: .rgba(64, 170, 240, 0.3), location: 0.45),
                    .init(color: .rgba(64, 170, 240, 0), location: 1),
                ]),
                center: light, startRadius: 0, endRadius: Metrics.rimReach
            ),
            lineWidth: 4
        )
        on.stroke(
            capsule(rect.insetBy(dx: 1, dy: 1)),
            with: .radialGradient(
                Gradient(stops: [
                    .init(color: Color(hex: 0x56b8f6), location: 0),
                    .init(color: .rgba(60, 164, 236, 0.85), location: 0.5),
                    .init(color: .rgba(60, 164, 236, 0), location: 1),
                ]),
                center: light, startRadius: 0, endRadius: Metrics.rimReach
            ),
            lineWidth: 2
        )
    }
}

/// A point `lap` (0..1, clockwise from the top-left) of the way around a capsule inset from `rect`.
private func capsulePoint(lap: Double, in rect: CGRect, inset: Double) -> CGPoint {
    let h = rect.height
    let r = h / 2 - inset
    let straight = rect.width - h
    let arc = Double.pi * r
    var d = (lap - lap.rounded(.down)) * (2 * straight + 2 * arc)
    if d < straight { return CGPoint(x: rect.minX + h / 2 + d, y: rect.midY - r) }
    d -= straight
    if d < arc { return CGPoint(x: rect.maxX - h / 2 + r * sin(d / r), y: rect.midY - r * cos(d / r)) }
    d -= arc
    if d < straight { return CGPoint(x: rect.maxX - h / 2 - d, y: rect.midY + r) }
    d -= straight
    return CGPoint(x: rect.minX + h / 2 - r * sin(d / r), y: rect.midY + r * cos(d / r))
}

/// Dust scattered around the light (the origin), split into groups that twinkle out of phase.
/// Same generator and seed as the React Native version, so the dust lands in the same places.
private let sparkles: [Path] = {
    var random = Mulberry32(seed: 23)
    var groups = Array(repeating: Path(), count: Metrics.sparkleGroups)
    for i in 0..<33 {
        let angle = random.next() * tau
        let distance = 22 + random.next() * 58
        let r = 0.4 + random.next() * random.next() * 1.2
        let c = CGPoint(x: cos(angle) * distance, y: sin(angle) * distance)
        groups[i % groups.count].addEllipse(in: CGRect(x: c.x - r, y: c.y - r, width: r * 2, height: r * 2))
    }
    return groups
}()

private struct Mulberry32 {
    private var state: UInt32

    init(seed: UInt32) { state = seed }

    mutating func next() -> Double {
        state &+= 0x6d2b_79f5
        var t = (state ^ (state >> 15)) &* (1 | state)
        t = (t &+ ((t ^ (t >> 7)) &* (61 | t))) ^ t
        return Double(t ^ (t >> 14)) / 4_294_967_296
    }
}

#Preview {
    ZStack {
        Color(hex: 0x04000f).ignoresSafeArea()
        GalaxyButton(autoPlay: true)
    }
}

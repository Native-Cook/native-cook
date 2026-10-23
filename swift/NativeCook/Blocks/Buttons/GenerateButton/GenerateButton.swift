import SwiftUI

/// Dark pill that lights up purple with an aura, twinkling stars and a highlight circling its rim.
struct GenerateButton: View {
    var label = "Generate Site"
    /// Force the lit state. When `nil` the button lights up on press/hover and lingers briefly.
    var active: Bool? = nil
    /// How long the glow stays after the finger lifts, so it isn't hidden under the thumb.
    var linger: Duration = .milliseconds(1600)
    var action: () -> Void = {}

    var body: some View {
        Button(action: action) { Text(label) }
            .buttonStyle(Style(label: label, active: active, linger: linger))
    }

    private struct Style: ButtonStyle {
        let label: String
        let active: Bool?
        let linger: Duration

        func makeBody(configuration: Configuration) -> some View {
            Surface(label: label, active: active, linger: linger, isPressed: configuration.isPressed)
        }
    }
}

private enum Metrics {
    static let height = 52.0
    static let padLeading = 20.0
    static let padTrailing = 22.0
    static let iconSlot = 24.0
    static let iconGap = 10.0
    static let ring = 4.0
    /// The canvas overflows the button by this much on every side to fit the aura and the star field.
    static let aura = 120.0
    static let loopSeconds = 6.0
    static let starGroups = 4
}

private struct Surface: View {
    let label: String
    let active: Bool?
    let linger: Duration
    let isPressed: Bool

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var hovered = false
    @State private var lingering = false
    @State private var lingerTask: Task<Void, Never>?
    @State private var progress = Tween(0)
    @State private var settling = false
    @State private var settleToken = 0
    @State private var stars = StarField()

    private var lit: Bool { active ?? (isPressed || hovered || lingering) }

    var body: some View {
        TimelineView(.animation(paused: !(lit || settling))) { timeline in
            let p = progress.value(at: timeline.date)
            // Only the fractional part matters: everything below is periodic in whole loops.
            let loop = reduceMotion
                ? 0
                : (timeline.date.timeIntervalSinceReferenceDate / Metrics.loopSeconds)
                    .truncatingRemainder(dividingBy: 1)

            HStack(spacing: Metrics.iconGap) {
                Color.clear.frame(width: Metrics.iconSlot, height: 1)
                Text(label)
                    .font(.system(size: 17, weight: .semibold))
                    .kerning(0.1)
                    .lineLimit(1)
                    .foregroundStyle(Color(white: 0.42 + 0.58 * p))
            }
            .padding(.leading, Metrics.padLeading)
            .padding(.trailing, Metrics.padTrailing)
            .frame(height: Metrics.height)
            .background {
                Canvas { context, size in
                    draw(context, size: size, p: p, loop: loop)
                }
                .padding(-Metrics.aura)
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
            let duration = lit ? 0.42 : 0.52
            progress.retarget(lit ? 1 : 0, duration: duration, curve: Ease.out)
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
        let aura = Metrics.aura
        let rect = CGRect(x: aura, y: aura, width: size.width - aura * 2, height: size.height - aura * 2)
        let w = rect.width
        let h = rect.height
        let center = CGPoint(x: rect.midX, y: rect.midY)
        let pill = capsule(rect)

        var on = context
        on.opacity = p

        on.fillEllipse(
            center: center,
            radii: CGSize(width: size.width / 2, height: size.height / 2),
            gradient: Gradient(stops: [
                .init(color: .rgba(104, 58, 240, 0.62), location: 0),
                .init(color: .rgba(72, 36, 190, 0.3), location: 0.45),
                .init(color: .rgba(44, 20, 128, 0), location: 1),
            ])
        )
        var glow = on
        glow.addFilter(.blur(radius: 18))
        glow.fill(capsule(rect.insetBy(dx: -2, dy: -2)), with: .color(.rgba(120, 74, 255, 0.85)))

        for (index, path) in stars.groups(buttonSize: rect.size).enumerated() {
            let wave = 0.5 + 0.5 * sin(tau * Double(index % 2 + 2) * loop + Double(index) * 1.7)
            var layer = context
            layer.opacity = p * (0.18 + 0.72 * wave)
            layer.fill(path, with: .color(Color(hex: 0xc4a8ff)))
        }

        on.fill(
            capsule(rect.insetBy(dx: -Metrics.ring, dy: -Metrics.ring)),
            with: .color(.rgba(112, 74, 246, 0.42))
        )

        context.fill(pill, with: .color(Color(hex: 0x1c1c1c)))

        var face = on
        face.clip(to: pill)
        face.fill(
            pill,
            with: .linearGradient(
                Gradient(colors: [Color(hex: 0x5a2ff0), Color(hex: 0x6a37f7)]),
                startPoint: CGPoint(x: center.x, y: rect.minY),
                endPoint: CGPoint(x: center.x, y: rect.maxY)
            )
        )
        face.fillEllipse(
            center: CGPoint(x: center.x, y: rect.maxY + 4),
            radii: CGSize(width: w * 0.46, height: h * 0.62),
            gradient: Gradient(stops: [
                .init(color: .rgba(196, 138, 255, 0.95), location: 0),
                .init(color: .rgba(160, 110, 255, 0.4), location: 0.5),
                .init(color: .rgba(130, 90, 255, 0), location: 1),
            ])
        )
        face.stroke(
            capsule(rect.insetBy(dx: 0.5, dy: 0.5)),
            with: .linearGradient(
                Gradient(colors: [
                    .rgba(214, 196, 255, 0.5), .rgba(214, 196, 255, 0.06), .rgba(214, 196, 255, 0.22),
                ]),
                startPoint: CGPoint(x: center.x, y: rect.minY),
                endPoint: CGPoint(x: center.x, y: rect.maxY)
            ),
            lineWidth: 1
        )

        // One lap of the rim highlight every 3s. Conic stops run clockwise from 3 o'clock, so 0.75 is the top.
        let spin = Angle(radians: tau * 2 * loop)
        let clear = Color.white.opacity(0)
        var bloom = on
        bloom.addFilter(.blur(radius: 3))
        bloom.stroke(
            pill,
            with: .conicGradient(
                Gradient(stops: [
                    .init(color: clear, location: 0.62),
                    .init(color: .white.opacity(0.8), location: 0.75),
                    .init(color: clear, location: 0.88),
                ]),
                center: center,
                angle: spin
            ),
            lineWidth: 3
        )
        on.stroke(
            capsule(rect.insetBy(dx: 0.5, dy: 0.5)),
            with: .conicGradient(
                Gradient(stops: [
                    .init(color: clear, location: 0.64),
                    .init(color: .white, location: 0.75),
                    .init(color: clear, location: 0.86),
                ]),
                center: center,
                angle: spin
            ),
            lineWidth: 1.2
        )

        let ix = rect.minX + Metrics.padLeading + Metrics.iconSlot / 2 + 3
        let main = CGPoint(x: ix, y: center.y)
        let low = CGPoint(x: ix - 8.4, y: center.y + 7.4)
        let high = CGPoint(x: ix - 8.2, y: center.y - 8)
        drawStar(context, at: main, radius: 7.4, scale: 1 - 0.14 * p * (0.5 + 0.5 * sin(tau * 4 * loop)),
                 idle: Color(hex: 0x5e5e5e), lit: .white, litOpacity: p)
        drawStar(context, at: low, radius: 3.8, scale: 1 + 0.3 * p * sin(tau * 4 * loop + 2.2),
                 idle: Color(hex: 0x484848), lit: Color(hex: 0xcdb6ff), litOpacity: p * 0.75)
        drawStar(context, at: high, radius: 1.9, scale: 1 + 0.55 * p * sin(tau * 4 * loop + 4.4),
                 idle: Color(hex: 0x3c3c3c), lit: Color(hex: 0xcdb6ff), litOpacity: p * 0.75)
    }

    private func drawStar(
        _ context: GraphicsContext, at point: CGPoint, radius: Double, scale: Double,
        idle: Color, lit: Color, litOpacity: Double
    ) {
        let path = Path.sparkle(at: point, radius: radius)
        let scaled = context.scaled(scale, about: point)
        scaled.fill(path, with: .color(idle))
        var top = scaled
        top.opacity = litOpacity
        top.fill(path, with: .color(lit))
    }
}

extension Path {
    /// Four-point star with concave sides.
    static func sparkle(at c: CGPoint, radius r: Double, pinch k: Double = 0.24) -> Path {
        var path = Path()
        path.addSparkle(at: c, radius: r, pinch: k)
        return path
    }

    mutating func addSparkle(at c: CGPoint, radius r: Double, pinch k: Double = 0.24) {
        let d = r * k
        move(to: CGPoint(x: c.x, y: c.y - r))
        addQuadCurve(to: CGPoint(x: c.x + r, y: c.y), control: CGPoint(x: c.x + d, y: c.y - d))
        addQuadCurve(to: CGPoint(x: c.x, y: c.y + r), control: CGPoint(x: c.x + d, y: c.y + d))
        addQuadCurve(to: CGPoint(x: c.x - r, y: c.y), control: CGPoint(x: c.x - d, y: c.y + d))
        addQuadCurve(to: CGPoint(x: c.x, y: c.y - r), control: CGPoint(x: c.x - d, y: c.y - d))
        closeSubpath()
    }
}

/// Sparkles scattered around (never on top of) the button, split into groups that twinkle out of phase.
/// Rebuilt only when the button's size changes.
private final class StarField {
    private var size = CGSize.zero
    private var cached: [Path] = []

    func groups(buttonSize: CGSize) -> [Path] {
        if buttonSize == size, !cached.isEmpty { return cached }
        size = buttonSize
        cached = Self.build(w: buttonSize.width, h: buttonSize.height)
        return cached
    }

    private static func build(w: Double, h: Double) -> [Path] {
        let aura = Metrics.aura
        var random = Mulberry32(seed: 7)
        var groups = Array(repeating: Path(), count: Metrics.starGroups)
        let cw = w + aura * 2
        let ch = h + aura * 2
        var placed = 0
        while placed < 34 {
            let x = random.next() * cw
            let y = random.next() * ch
            let insideButton = x > aura - 14 && x < aura + w + 14 && y > aura - 14 && y < aura + h + 14
            // Keep stars inside the aura ellipse so they fade with it instead of hitting the canvas edge.
            let nx = (x - cw / 2) / (cw / 2)
            let ny = (y - ch / 2) / (ch / 2)
            if insideButton || nx * nx + ny * ny > 0.82 { continue }
            let size = random.next()
            let point = CGPoint(x: x, y: y)
            if size > 0.55 {
                groups[placed % groups.count].addSparkle(at: point, radius: 1.4 + size * 2.4)
            } else {
                let r = 0.5 + size * 1.2
                groups[placed % groups.count].addEllipse(in: CGRect(x: x - r, y: y - r, width: r * 2, height: r * 2))
            }
            placed += 1
        }
        return groups
    }
}

/// Small deterministic PRNG so the star layout is the same on every launch.
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
        Color(hex: 0x111111).ignoresSafeArea()
        GenerateButton(active: true)
    }
}

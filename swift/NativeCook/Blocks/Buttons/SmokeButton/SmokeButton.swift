import SwiftUI

/// Glass capsule with smoke drifting inside, dust motes, a breathing rim light and glowing text.
/// Pressing it makes the smoke surge across the whole capsule and lights the full rim.
struct SmokeButton: View {
    var label = "Get Started"
    var action: () -> Void = {}

    var body: some View {
        Button(action: action) { Text(label) }
            .buttonStyle(Style(label: label))
    }

    private struct Style: ButtonStyle {
        let label: String

        func makeBody(configuration: Configuration) -> some View {
            Surface(label: label, isPressed: configuration.isPressed)
        }
    }
}

private enum Metrics {
    static let height = 60.0
    static let padX = 42.0
    /// The canvas overflows the button by this much on every side to fit the halo behind it.
    static let bleed = 110.0
    /// Where the smoke is frozen when the system asks for reduced motion.
    static let stillTime = 4.0
}

/// Integrates time frame by frame (rather than reading a clock) so a press can speed the smoke
/// up without a jump.
private final class SmokeClock {
    private var last: Date?
    private(set) var time = Metrics.stillTime

    func advance(to date: Date, speed: Double) -> Double {
        if let last, date > last {
            time += min(date.timeIntervalSince(last), 0.064) * speed
        }
        last = date
        return time
    }

    func hold(at date: Date) -> Double {
        last = date
        return time
    }
}

private struct Surface: View {
    let label: String
    let isPressed: Bool

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var boost = Tween(0)
    @State private var settling = false
    @State private var settleToken = 0
    @State private var clock = SmokeClock()

    var body: some View {
        TimelineView(.animation(paused: reduceMotion && !settling)) { timeline in
            let boost = boost.value(at: timeline.date)
            let time = reduceMotion
                ? clock.hold(at: timeline.date)
                : clock.advance(to: timeline.date, speed: 1 + 2.4 * boost)
            let glow = min(1, 0.6 + 0.2 * sin(time * 0.8) + 0.4 * boost)

            Text(label)
                .font(.system(size: 17, weight: .semibold))
                .kerning(-0.2)
                .lineLimit(1)
                .foregroundStyle(.white)
                .shadow(color: .white.opacity(0.95 * glow), radius: 4.5)
                .padding(.horizontal, Metrics.padX)
                .frame(height: Metrics.height)
                .background {
                    Canvas { context, size in
                        draw(context, size: size, time: time, boost: boost)
                    }
                    .padding(-Metrics.bleed)
                    .allowsHitTesting(false)
                }
        }
        .contentShape(Capsule())
        .scaleEffect(isPressed ? 0.97 : 1)
        .animation(.timingCurve(0.23, 1, 0.32, 1, duration: 0.13), value: isPressed)
        .sensoryFeedback(.impact(weight: .light), trigger: isPressed) { _, pressed in pressed }
        .onChange(of: isPressed) { _, pressed in
            boost.retarget(pressed ? 1 : 0, duration: pressed ? 0.38 : 1.1, curve: Ease.out)
            settling = true
            settleToken += 1
        }
        .task(id: settleToken) {
            guard settleToken > 0 else { return }
            try? await Task.sleep(for: .seconds(boost.duration + 0.05))
            if !Task.isCancelled { settling = false }
        }
    }

    private func draw(_ context: GraphicsContext, size: CGSize, time: Double, boost: Double) {
        let bleed = Metrics.bleed
        let rect = CGRect(x: bleed, y: bleed, width: size.width - bleed * 2, height: size.height - bleed * 2)
        let w = rect.width
        let h = rect.height
        let cy = rect.midY
        let pill = capsule(rect)

        var halo = context
        halo.opacity = min(1, 0.78 + 0.22 * sin(time * 0.8) + 0.6 * boost)
        halo.fillEllipse(
            center: CGPoint(x: rect.minX + w * 0.6, y: cy),
            radii: CGSize(width: w * 0.92, height: h * 1.7),
            gradient: Gradient(stops: [
                .init(color: .white.opacity(0.2), location: 0),
                .init(color: .white.opacity(0.07), location: 0.45),
                .init(color: .white.opacity(0), location: 1),
            ])
        )

        context.fill(
            pill,
            with: .linearGradient(
                Gradient(colors: [Color(hex: 0x060606), Color(hex: 0x141414)]),
                startPoint: CGPoint(x: rect.minX, y: cy),
                endPoint: CGPoint(x: rect.maxX, y: cy)
            )
        )

        let smoke = ShaderLibrary.smoke(
            .float2(rect.origin), .float2(rect.size), .float(time), .float(boost)
        )
        context.fill(pill, with: .shader(smoke, bounds: rect))

        context.stroke(capsule(rect.insetBy(dx: 0.5, dy: 0.5)), with: .color(.white.opacity(0.05)), lineWidth: 1)

        // The lit stretch of the rim sways a little, as if the light source were drifting;
        // a press pushes its start past the left cap so the whole rim lights up.
        let rimLight = GraphicsContext.Shading.linearGradient(
            Gradient(stops: [
                .init(color: .white.opacity(0), location: 0),
                .init(color: .white.opacity(0.5), location: 0.5),
                .init(color: .white, location: 1),
            ]),
            startPoint: CGPoint(x: rect.minX + w * (0.2 + 0.09 * sin(time * 0.55) - 1.1 * boost), y: cy),
            endPoint: CGPoint(x: rect.minX + w * 0.8, y: cy)
        )
        var bloom = context
        bloom.opacity = 0.7
        bloom.addFilter(.blur(radius: 4))
        bloom.stroke(pill, with: rimLight, lineWidth: 4)
        context.stroke(capsule(rect.insetBy(dx: 0.9, dy: 0.9)), with: rimLight, lineWidth: 1.8)
        var inner = context
        inner.opacity = 0.3
        inner.stroke(capsule(rect.insetBy(dx: 4, dy: 4)), with: rimLight, lineWidth: 1)
    }
}

#Preview {
    ZStack {
        Color(hex: 0x050505).ignoresSafeArea()
        SmokeButton()
    }
}

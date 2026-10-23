import SwiftUI

/// Dark liquid-glass capsule with a large percentage and a caption. Blurry blue, magenta, red and
/// green light drifts around the inside of its rim while the number counts up.
struct GlassLoadingButton: View {
    /// Caption under the percentage.
    var label = "LOADING"
    /// Loads from 0 to 100 by itself, rests, and starts over.
    var autoPlay = false
    /// Controlled progress, 0…1. When `nil` a tap runs one load from 0 to 100.
    var progress: Double? = nil
    var action: () -> Void = {}

    @State private var percent = 0
    /// Bumped to (re)start a load; 0 means nothing has been started yet.
    @State private var run = 0

    private var shown: Int {
        guard let progress else { return percent }
        return Int((100 * min(1, max(0, progress))).rounded())
    }

    var body: some View {
        Button {
            action()
            run += 1
        } label: {
            Text(label)
        }
        .buttonStyle(Style(label: label, shown: shown))
        .accessibilityLabel("\(label) \(shown)%")
        .task(id: run) {
            guard run > 0 || autoPlay else { return }
            let start = Date.now
            // The number is real text, so it is updated on a timer (20×/s), not per frame.
            while !Task.isCancelled {
                let t = min(1, Date.now.timeIntervalSince(start) / Metrics.loadSeconds)
                percent = Int((100 * loadCurve(t)).rounded())
                if t >= 1 { break }
                try? await Task.sleep(for: .milliseconds(50))
            }
            guard autoPlay, !Task.isCancelled else { return }
            try? await Task.sleep(for: .seconds(Metrics.restSeconds))
            if !Task.isCancelled { run += 1 }
        }
    }

    private struct Style: ButtonStyle {
        let label: String
        let shown: Int

        func makeBody(configuration: Configuration) -> some View {
            Surface(label: label, shown: shown, isPressed: configuration.isPressed)
        }
    }
}

private enum Metrics {
    static let width = 172.0
    static let height = 118.0
    static let radius = 54.0
    /// The canvas overflows the button by this much on every side to fit the shadow.
    static let bleed = 28.0
    static let loadSeconds = 5.2
    static let restSeconds = 1.3
    /// Where the colours are frozen when the system asks for reduced motion.
    static let stillTime = 2.4
}

/// Loading that surges and stalls a little instead of counting at a constant rate.
private func loadCurve(_ t: Double) -> Double {
    t - 0.04 * sin(6 * .pi * t)
}

private struct Blob {
    let r: Double, g: Double, b: Double
    /// Start angle on the rim (radians, clockwise from 3 o'clock) and angular speed in rad/s.
    let angle: Double
    let speed: Double
    /// Brightness swell: rate in rad/s and phase.
    let pulse: Double
    let phase: Double
    let radius: Double

    var gradient: Gradient {
        Gradient(stops: [
            .init(color: .rgba(r, g, b, 0.85), location: 0),
            .init(color: .rgba(r, g, b, 0.4), location: 0.5),
            .init(color: .rgba(r, g, b, 0), location: 1),
        ])
    }
}

// Blurry colours that travel around the inside of the rim and swell in and out of view.
private let blobs = [
    Blob(r: 58, g: 96, b: 190, angle: 3.4, speed: 0.42, pulse: 0.9, phase: 0, radius: 78),
    Blob(r: 190, g: 62, b: 150, angle: 1.3, speed: 0.34, pulse: 0.7, phase: 2.1, radius: 72),
    Blob(r: 150, g: 48, b: 48, angle: 2.5, speed: -0.27, pulse: 1.1, phase: 4, radius: 66),
    Blob(r: 34, g: 122, b: 92, angle: 0.2, speed: 0.31, pulse: 0.8, phase: 5.2, radius: 60),
]

private struct Surface: View {
    let label: String
    let shown: Int
    let isPressed: Bool

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var epoch = Date.now

    var body: some View {
        VStack(spacing: -5.2) {
            HStack(alignment: .firstTextBaseline, spacing: 1) {
                Text("\(shown)")
                    .font(.system(size: 48, weight: .semibold))
                    .monospacedDigit()
                    .foregroundStyle(Color(hex: 0xdcdcde))
                    .shadow(color: .white.opacity(0.28), radius: 4)
                Text("%")
                    .font(.system(size: 29, weight: .semibold))
                    .foregroundStyle(Color(hex: 0xcfcfd2))
            }
            Text(label)
                .font(.system(size: 13, weight: .bold))
                .kerning(0.3)
                .lineLimit(1)
                .foregroundStyle(Color(hex: 0xececec))
        }
        .offset(y: -5)
        .frame(width: Metrics.width, height: Metrics.height)
        .background {
            TimelineView(.animation(paused: reduceMotion)) { timeline in
                let time = Metrics.stillTime + (reduceMotion ? 0 : timeline.date.timeIntervalSince(epoch))
                Canvas { context, size in
                    draw(context, size: size, time: time)
                }
            }
            .padding(-Metrics.bleed)
            .allowsHitTesting(false)
        }
        .contentShape(RoundedRectangle(cornerRadius: Metrics.radius))
        .scaleEffect(isPressed ? 0.97 : 1)
        .animation(.timingCurve(0.23, 1, 0.32, 1, duration: 0.13), value: isPressed)
        .sensoryFeedback(.impact(weight: .light), trigger: isPressed) { _, pressed in pressed }
    }

    private func draw(_ context: GraphicsContext, size: CGSize, time: Double) {
        let bleed = Metrics.bleed
        let rect = CGRect(x: bleed, y: bleed, width: size.width - bleed * 2, height: size.height - bleed * 2)
        let w = rect.width
        let h = rect.height
        let center = CGPoint(x: rect.midX, y: rect.midY)
        let shape = Path(roundedRect: rect, cornerRadius: Metrics.radius, style: .circular)

        var shadow = context
        shadow.addFilter(.blur(radius: 12))
        shadow.fill(
            Path(roundedRect: rect.offsetBy(dx: 0, dy: 8), cornerRadius: Metrics.radius, style: .circular),
            with: .color(.black.opacity(0.4))
        )
        context.fill(shape, with: .color(Color(hex: 0x1b1b1b)))

        var inside = context
        inside.clip(to: shape)
        for blob in blobs {
            let a = blob.angle + blob.speed * time
            var layer = inside
            layer.opacity = 0.25 + 0.75 * (0.5 + 0.5 * sin(blob.pulse * time + blob.phase))
            layer.fillEllipse(
                center: CGPoint(x: center.x + (w / 2 - 12) * cos(a), y: center.y + (h / 2 - 6) * sin(a)),
                radii: CGSize(width: blob.radius, height: blob.radius),
                gradient: blob.gradient
            )
        }
        // Keeps the middle dark so the colours read as light caught in the rim of the glass.
        inside.fillEllipse(
            center: center,
            radii: CGSize(width: w * 0.52, height: h * 0.52),
            gradient: Gradient(stops: [
                .init(color: .rgba(24, 24, 24, 0.94), location: 0),
                .init(color: .rgba(24, 24, 24, 0.72), location: 0.42),
                .init(color: .rgba(24, 24, 24, 0), location: 1),
            ])
        )
        inside.fill(
            shape,
            with: .linearGradient(
                Gradient(stops: [
                    .init(color: .white.opacity(0.07), location: 0),
                    .init(color: .white.opacity(0), location: 0.5),
                    .init(color: .white.opacity(0.02), location: 1),
                ]),
                startPoint: CGPoint(x: center.x, y: rect.minY),
                endPoint: CGPoint(x: center.x, y: rect.maxY)
            )
        )

        context.stroke(
            Path(roundedRect: rect.insetBy(dx: 0.5, dy: 0.5), cornerRadius: Metrics.radius - 0.5, style: .circular),
            with: .linearGradient(
                Gradient(stops: [
                    .init(color: .white.opacity(0.3), location: 0),
                    .init(color: .white.opacity(0.07), location: 0.5),
                    .init(color: .white.opacity(0.16), location: 1),
                ]),
                startPoint: CGPoint(x: center.x, y: rect.minY),
                endPoint: CGPoint(x: center.x, y: rect.maxY)
            ),
            lineWidth: 1
        )
    }
}

#Preview {
    ZStack {
        Color(hex: 0x1a1a1a).ignoresSafeArea()
        GlassLoadingButton(autoPlay: true)
    }
}

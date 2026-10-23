import SwiftUI

/// Dark glass "Home" pill with an icon well whose rim carries an iridescent film and a circling white flare.
struct IridescentHomeButton: View {
    var label = "Home"
    /// Keeps the flare at full strength. When off the ring idles dimmer and flares up while pressed.
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
    static let height = 64.0
    static let padTrailing = 26.0
    /// Gap between the icon well and the label.
    static let labelGap = 14.0
    /// The canvas overflows the button by this much on every side to fit the flare's glow.
    static let bleed = 44.0
    /// Thickness of the translucent band around the pill.
    static let band = 2.5
    static let ringRadius = 23.5
    static let ringWidth = 3.4
    static let discRadius = 20.5
    static let glowRadius = 38.0
    /// Seconds per lap: the white flare runs clockwise, the rainbow film creeps the other way.
    static let flareLap = 5.0
    static let filmLap = 11.0
    /// Where the clock is frozen when the system asks for reduced motion (flare at the upper right).
    static let stillTime = 3.6
    /// The flare is brightest when it passes this angle (upper right), as in the reference.
    static let peakAngle = -0.6
    static let flareCenter = 0.51
}

private let dark = Color.rgba(8, 8, 10, 0)

/// Thin-film colours scattered around the ring with dark gaps between them.
private let filmGradient = Gradient(stops: [
    .init(color: dark, location: 0),
    .init(color: Color(hex: 0x2b5cff), location: 0.05),
    .init(color: Color(hex: 0x58d6ff), location: 0.09),
    .init(color: Color(hex: 0xfff6d8), location: 0.13),
    .init(color: Color(hex: 0xffc93d), location: 0.17),
    .init(color: Color(hex: 0xff6a1a), location: 0.21),
    .init(color: .rgba(255, 45, 45, 0), location: 0.26),
    .init(color: dark, location: 0.36),
    .init(color: Color(hex: 0x7a3cff), location: 0.42),
    .init(color: Color(hex: 0x2b8cff), location: 0.46),
    .init(color: Color(hex: 0x7ff0e0), location: 0.5),
    .init(color: .rgba(127, 240, 224, 0), location: 0.55),
    .init(color: dark, location: 0.64),
    .init(color: Color(hex: 0xff8a2a), location: 0.7),
    .init(color: Color(hex: 0xffe27a), location: 0.74),
    .init(color: .white, location: 0.78),
    .init(color: Color(hex: 0x5ad0ff), location: 0.83),
    .init(color: Color(hex: 0x2f54ff), location: 0.88),
    .init(color: dark, location: 0.94),
])
/// The flare: blue tail, white body, warm head. Centred on half a turn.
private let flareGradient = Gradient(stops: [
    .init(color: .rgba(58, 107, 255, 0), location: 0.28),
    .init(color: Color(hex: 0x3a6bff), location: 0.34),
    .init(color: Color(hex: 0x9fe8ff), location: 0.4),
    .init(color: .white, location: 0.46),
    .init(color: .white, location: 0.56),
    .init(color: Color(hex: 0xffe9a8), location: 0.62),
    .init(color: Color(hex: 0xff8a2a), location: 0.67),
    .init(color: .rgba(255, 60, 40, 0), location: 0.72),
])
private let bloomGradient = Gradient(stops: [
    .init(color: .rgba(255, 244, 228, 0), location: 0.36),
    .init(color: .rgba(255, 244, 228, 0.95), location: 0.46),
    .init(color: .rgba(255, 244, 228, 0.95), location: 0.56),
    .init(color: .rgba(255, 244, 228, 0), location: 0.68),
])

/// Little house, centred on the origin: rounded roof tip, eaves, body and a door notch.
private let homeIcon: Path = {
    var p = Path()
    p.move(to: CGPoint(x: -0.9, y: -9.2))
    p.addQuadCurve(to: CGPoint(x: 0.9, y: -9.2), control: CGPoint(x: 0, y: -10))
    p.addLine(to: CGPoint(x: 10.5, y: -1))
    p.addQuadCurve(to: CGPoint(x: 10, y: 0), control: CGPoint(x: 11.2, y: 0))
    p.addLine(to: CGPoint(x: 7.6, y: 0))
    p.addLine(to: CGPoint(x: 7.6, y: 8))
    p.addQuadCurve(to: CGPoint(x: 6.2, y: 9.4), control: CGPoint(x: 7.6, y: 9.4))
    p.addLine(to: CGPoint(x: 3.1, y: 9.4))
    p.addQuadCurve(to: CGPoint(x: 2.4, y: 8.7), control: CGPoint(x: 2.4, y: 9.4))
    p.addLine(to: CGPoint(x: 2.4, y: 3.6))
    p.addLine(to: CGPoint(x: -2.4, y: 3.6))
    p.addLine(to: CGPoint(x: -2.4, y: 8.7))
    p.addQuadCurve(to: CGPoint(x: -3.1, y: 9.4), control: CGPoint(x: -2.4, y: 9.4))
    p.addLine(to: CGPoint(x: -6.2, y: 9.4))
    p.addQuadCurve(to: CGPoint(x: -7.6, y: 8), control: CGPoint(x: -7.6, y: 9.4))
    p.addLine(to: CGPoint(x: -7.6, y: 0))
    p.addLine(to: CGPoint(x: -10, y: 0))
    p.addQuadCurve(to: CGPoint(x: -10.5, y: -1), control: CGPoint(x: -11.2, y: 0))
    p.closeSubpath()
    return p
}()

private struct Surface: View {
    let label: String
    let autoPlay: Bool
    let isPressed: Bool

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    /// 0 = idling, 1 = full flare.
    @State private var energy = Tween(0)
    @State private var settling = false
    @State private var settleToken = 0

    private var lit: Bool { autoPlay || isPressed }

    var body: some View {
        TimelineView(.animation(paused: reduceMotion && !settling)) { timeline in
            // Both laps divide 55 s, so wrapping there is seamless and keeps the number small.
            let time = reduceMotion
                ? Metrics.stillTime
                : timeline.date.timeIntervalSinceReferenceDate
                    .truncatingRemainder(dividingBy: Metrics.flareLap * Metrics.filmLap)
            let energy = energy.value(at: timeline.date)

            Text(label)
                .font(.system(size: 19, weight: .medium))
                .lineLimit(1)
                .foregroundStyle(.white)
                // The icon well fills the left cap.
                .padding(.leading, Metrics.height + Metrics.labelGap - 6)
                .padding(.trailing, Metrics.padTrailing)
                .frame(height: Metrics.height)
                .background {
                    Canvas { context, size in
                        draw(context, size: size, time: time, energy: energy)
                    }
                    .padding(-Metrics.bleed)
                    .allowsHitTesting(false)
                }
        }
        .contentShape(Capsule())
        .scaleEffect(isPressed ? 0.97 : 1)
        .animation(.timingCurve(0.23, 1, 0.32, 1, duration: 0.13), value: isPressed)
        .sensoryFeedback(.impact(weight: .light), trigger: isPressed) { _, pressed in pressed }
        .onChange(of: lit, initial: true) { _, lit in
            // Rises quickly, and fades slowly after release so the flare isn't hidden under the thumb.
            energy.retarget(lit ? 1 : 0, duration: lit ? 0.38 : 1.4, curve: Ease.out)
            settling = true
            settleToken += 1
        }
        .task(id: settleToken) {
            try? await Task.sleep(for: .seconds(energy.duration + 0.05))
            if !Task.isCancelled { settling = false }
        }
    }

    private func draw(_ context: GraphicsContext, size: CGSize, time: Double, energy: Double) {
        let bleed = Metrics.bleed
        let rect = CGRect(x: bleed, y: bleed, width: size.width - bleed * 2, height: size.height - bleed * 2)
        let r = rect.height / 2
        // The well shares the pill's left cap.
        let c = CGPoint(x: rect.minX + r, y: rect.midY)
        let top = CGPoint(x: rect.minX, y: rect.minY)
        let bottom = CGPoint(x: rect.minX, y: rect.maxY)

        // Brightest at the upper right, dimmest on the opposite side; a press keeps it up.
        let flareAngle = tau * time / Metrics.flareLap
        let breath = 0.5 + 0.5 * cos(flareAngle - Metrics.peakAngle)
        let flare = (0.45 + 0.55 * energy) * (0.3 + 0.7 * breath)

        context.fill(capsule(rect), with: .color(.rgba(40, 40, 44, 0.72)))
        let body = rect.insetBy(dx: Metrics.band, dy: Metrics.band)
        context.fill(
            capsule(body),
            with: .linearGradient(
                Gradient(colors: [Color(hex: 0x1e1e20), Color(hex: 0x141416)]), startPoint: top, endPoint: bottom
            )
        )
        context.stroke(
            capsule(body.insetBy(dx: 0.5, dy: 0.5)),
            with: .linearGradient(
                Gradient(colors: [.white.opacity(0.1), .white.opacity(0.02), .white.opacity(0.05)]),
                startPoint: top, endPoint: bottom
            ),
            lineWidth: 1
        )

        // The groove the ring sits in.
        context.fill(circle(c, Metrics.ringRadius + Metrics.ringWidth / 2 + 0.8), with: .color(Color(hex: 0x060607)))

        // Warm light spilling from the flare onto the pill and the background.
        var glow = context
        glow.opacity = flare * 0.8
        glow.fillEllipse(
            center: CGPoint(x: c.x + Metrics.ringRadius * cos(flareAngle), y: c.y + Metrics.ringRadius * sin(flareAngle)),
            radii: CGSize(width: Metrics.glowRadius, height: Metrics.glowRadius),
            gradient: Gradient(stops: [
                .init(color: .rgba(255, 236, 206, 0.7), location: 0),
                .init(color: .rgba(255, 200, 150, 0.2), location: 0.45),
                .init(color: .rgba(255, 170, 110, 0), location: 1),
            ])
        )

        // Rainbow film: a slightly squashed ring turning with its gradient reads as a liquid wobble.
        var film = context
        film.opacity = 0.6 + 0.3 * energy
        film.translateBy(x: c.x, y: c.y)
        film.rotate(by: .radians(-tau * time / Metrics.filmLap))
        film.translateBy(x: -c.x, y: -c.y)
        film.stroke(
            Path(ellipseIn: CGRect(
                x: c.x - Metrics.ringRadius - 0.7, y: c.y - Metrics.ringRadius + 0.7,
                width: (Metrics.ringRadius + 0.7) * 2, height: (Metrics.ringRadius - 0.7) * 2
            )),
            with: .conicGradient(filmGradient, center: c),
            lineWidth: Metrics.ringWidth
        )

        let ring = circle(c, Metrics.ringRadius)
        let spin = Angle(radians: flareAngle - tau * Metrics.flareCenter)
        var hot = context
        hot.opacity = flare
        var bloom = hot
        bloom.addFilter(.blur(radius: 5))
        bloom.stroke(ring, with: .conicGradient(bloomGradient, center: c, angle: spin), lineWidth: 7)
        hot.stroke(ring, with: .conicGradient(flareGradient, center: c, angle: spin), lineWidth: Metrics.ringWidth + 0.4)

        // Glass disc holding the icon.
        let k = Metrics.discRadius * 0.7
        context.fill(
            circle(c, Metrics.discRadius),
            with: .linearGradient(
                Gradient(colors: [Color(hex: 0x38383b), Color(hex: 0x232325), Color(hex: 0x19191b)]),
                startPoint: CGPoint(x: c.x - k, y: c.y - k), endPoint: CGPoint(x: c.x + k, y: c.y + k)
            )
        )
        context.stroke(circle(c, Metrics.discRadius - 0.5), with: .color(.white.opacity(0.07)), lineWidth: 1)

        var icon = context
        icon.translateBy(x: c.x, y: c.y - 0.5)
        icon.fill(
            homeIcon,
            with: .linearGradient(
                Gradient(stops: [
                    .init(color: Color(hex: 0xfbfbfb), location: 0),
                    .init(color: Color(hex: 0xcfcfd1), location: 0.45),
                    .init(color: Color(hex: 0xf0f0f0), location: 0.55),
                    .init(color: Color(hex: 0xb2b2b5), location: 1),
                ]),
                startPoint: CGPoint(x: -8, y: -10), endPoint: CGPoint(x: 8, y: 10)
            )
        )
    }
}

private func circle(_ c: CGPoint, _ r: Double) -> Path {
    Path(ellipseIn: CGRect(x: c.x - r, y: c.y - r, width: r * 2, height: r * 2))
}

#Preview {
    ZStack {
        Color(hex: 0x050506).ignoresSafeArea()
        IridescentHomeButton(autoPlay: true)
    }
}

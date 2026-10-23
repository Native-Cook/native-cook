import SwiftUI

/// Round yellow "+" action button with a ring that keeps pulsing outward from it. A tap sends out
/// one larger ring of its own.
struct PulseFabButton: View {
    /// The button has no text, so this is what VoiceOver announces.
    var label = "Add"
    /// The pulse is ambient and runs either way; the flag only exists so every gallery button
    /// shares the same API.
    var autoPlay = false
    var action: () -> Void = {}

    @State private var taps = 0

    var body: some View {
        Button {
            taps += 1
            action()
        } label: {
            Color.clear.frame(width: Metrics.size, height: Metrics.size)
        }
        .buttonStyle(Style(taps: taps))
        .accessibilityLabel(label)
    }

    private struct Style: ButtonStyle {
        let taps: Int

        func makeBody(configuration: Configuration) -> some View {
            Surface(taps: taps, isPressed: configuration.isPressed) { configuration.label }
        }
    }
}

private enum Metrics {
    static let size = 56.0
    /// The canvas overflows the button by this much on every side to fit the rings.
    static let bleed = 24.0
    static let pulseSeconds = 2.0
    /// The ring is gone after this share of the cycle; the rest is a pause.
    static let pulseActive = 0.7
    /// How far the ambient ring and the tap ring travel beyond the button's edge.
    static let pulseSpread = 10.0
    static let burstSpread = 18.0
    static let burstSeconds = 0.65
}

private let yellow = Color(hex: 0xffbd0b)
private let easeOutCubic = UnitCurve.bezier(
    startControlPoint: UnitPoint(x: 0.215, y: 0.61),
    endControlPoint: UnitPoint(x: 0.355, y: 1)
)

private struct Surface<Label: View>: View {
    let taps: Int
    let isPressed: Bool
    @ViewBuilder let label: Label

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var burst = Tween(1)
    @State private var bursting = false
    @State private var started = Date.now

    var body: some View {
        // Reduced motion freezes the ambient ring, so the clock only runs for a tap's fade.
        TimelineView(.animation(paused: reduceMotion && !bursting)) { timeline in
            let date = timeline.date
            label.background {
                Canvas { context, size in
                    let cycle = date.timeIntervalSince(started) / Metrics.pulseSeconds
                    draw(context, size: size, clock: cycle - cycle.rounded(.down), burst: burst.value(at: date))
                }
                .padding(-Metrics.bleed)
                .allowsHitTesting(false)
            }
        }
        .contentShape(Circle())
        .scaleEffect(isPressed ? 0.97 : 1)
        .animation(.timingCurve(0.23, 1, 0.32, 1, duration: 0.13), value: isPressed)
        .sensoryFeedback(.impact(weight: .light), trigger: isPressed) { _, pressed in pressed }
        .onChange(of: taps) {
            burst = Tween(0)
            burst.retarget(1, duration: Metrics.burstSeconds, curve: easeOutCubic)
            bursting = true
        }
        .task(id: taps) {
            guard taps > 0 else { return }
            try? await Task.sleep(for: .seconds(Metrics.burstSeconds + 0.05))
            guard !Task.isCancelled else { return }
            bursting = false
        }
    }

    private func draw(_ context: GraphicsContext, size: CGSize, clock: Double, burst: Double) {
        let c = CGPoint(x: size.width / 2, y: size.height / 2)
        let r = Metrics.size / 2

        func disc(_ radius: Double, opacity: Double = 1) {
            let rect = CGRect(x: c.x - radius, y: c.y - radius, width: radius * 2, height: radius * 2)
            context.fill(Path(ellipseIn: rect), with: .color(yellow.opacity(opacity)))
        }

        if burst < 1 {
            disc(r + Metrics.burstSpread * (reduceMotion ? 0.6 : burst), opacity: 0.55 * (1 - burst))
        }
        if reduceMotion {
            // A faint resting halo stands in for the travelling ring.
            disc(r + Metrics.pulseSpread * 0.6, opacity: 0.28)
        } else {
            // The ring leaves quickly and slows down, staying strong until it is most of the way out.
            let u = min(clock / Metrics.pulseActive, 1)
            disc(r + Metrics.pulseSpread * (1 - (1 - u) * (1 - u)), opacity: 0.5 * (1 - u * u))
        }
        disc(r)

        var plus = Path()
        plus.move(to: CGPoint(x: c.x - 11.5, y: c.y))
        plus.addLine(to: CGPoint(x: c.x + 11.5, y: c.y))
        plus.move(to: CGPoint(x: c.x, y: c.y - 11.5))
        plus.addLine(to: CGPoint(x: c.x, y: c.y + 11.5))
        context.stroke(plus, with: .color(.white), style: StrokeStyle(lineWidth: 2.4, lineCap: .round))
    }
}

#Preview {
    ZStack {
        Color(hex: 0xf4f9ff).ignoresSafeArea()
        PulseFabButton(autoPlay: true)
    }
}

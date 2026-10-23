import SwiftUI

/// "LEARN MORE" with a dark circle holding a chevron. While pressed (or by itself with `autoPlay`)
/// the circle sweeps across into a full pill, the chevron slides right and grows a tail, and the
/// label turns white; then it all contracts again.
struct ArrowExpandButton: View {
    var label = "LEARN MORE"
    /// Expands and contracts by itself, as if a pointer kept hovering over it.
    var autoPlay = false
    /// How long it stays expanded after the finger lifts, so it isn't hidden under the thumb.
    var linger: Duration = .milliseconds(900)
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
    static let height = 48.0
    /// The label starts to the right of the circle; the circle grows over it.
    static let labelLeading = 65.0
    static let padTrailing = 31.0
    static let expandSeconds = 0.45
    static let autoPlayRest = 1.3
    static let autoPlayHold = 1.7
    /// Tip of the chevron at rest, how far the arrow travels, and the length of the tail it grows.
    static let tipX = 28.5
    static let arrowShift = 15.0
    static let tail = 18.0
    static let head = 6.5
}

/// Slow start, hard brake: the circle seems to gather itself before it sweeps across.
private let sweep = UnitCurve.bezier(
    startControlPoint: UnitPoint(x: 0.65, y: 0),
    endControlPoint: UnitPoint(x: 0.076, y: 1)
)

private struct Surface: View {
    let label: String
    let autoPlay: Bool
    let linger: Duration
    let isPressed: Bool

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var lingering = false
    @State private var lingerTask: Task<Void, Never>?
    @State private var auto = false
    @State private var expand = Tween(0)
    @State private var settling = false
    @State private var settleToken = 0

    private var expanded: Bool { isPressed || lingering || auto }
    /// The loop is pure motion, so it is dropped when the system asks for reduced motion.
    private var looping: Bool { autoPlay && !reduceMotion }

    var body: some View {
        TimelineView(.animation(paused: !settling)) { timeline in
            let e = expand.value(at: timeline.date)

            Text(label)
                .font(.system(size: 15, weight: .heavy))
                .kerning(0.2)
                .lineLimit(1)
                // Ink (#22242f) to white.
                .foregroundStyle(Color.rgba(34 + 221 * e, 36 + 219 * e, 47 + 208 * e, 1))
                .padding(.leading, Metrics.labelLeading)
                .padding(.trailing, Metrics.padTrailing)
                .frame(height: Metrics.height)
                .background {
                    Canvas { context, size in
                        draw(context, size: size, expand: e)
                    }
                    .allowsHitTesting(false)
                }
        }
        .contentShape(Capsule())
        .scaleEffect(isPressed ? 0.97 : 1)
        .animation(.timingCurve(0.23, 1, 0.32, 1, duration: 0.13), value: isPressed)
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
        .onChange(of: expanded) { _, expanded in
            expand.retarget(expanded ? 1 : 0, duration: reduceMotion ? 0 : Metrics.expandSeconds, curve: sweep)
            settling = true
            settleToken += 1
        }
        .task(id: settleToken) {
            guard settleToken > 0 else { return }
            try? await Task.sleep(for: .seconds(Metrics.expandSeconds + 0.05))
            if !Task.isCancelled { settling = false }
        }
        .task(id: [looping, auto]) {
            guard looping else { return }
            try? await Task.sleep(for: .seconds(auto ? Metrics.autoPlayHold : Metrics.autoPlayRest))
            if !Task.isCancelled { auto.toggle() }
        }
    }

    private func draw(_ context: GraphicsContext, size: CGSize, expand: Double) {
        let h = size.height
        let cy = h / 2
        context.fill(
            capsule(CGRect(x: 0, y: 0, width: h + (size.width - h) * expand, height: h)),
            with: .color(Color(hex: 0x22242f))
        )

        let tip = Metrics.tipX + Metrics.arrowShift * expand
        var tail = Path()
        tail.move(to: CGPoint(x: tip - Metrics.tail, y: cy))
        tail.addLine(to: CGPoint(x: tip - 1, y: cy))
        var chevron = Path()
        chevron.move(to: CGPoint(x: tip - Metrics.head, y: cy - Metrics.head))
        chevron.addLine(to: CGPoint(x: tip, y: cy))
        chevron.addLine(to: CGPoint(x: tip - Metrics.head, y: cy + Metrics.head))

        let stroke = StrokeStyle(lineWidth: 2, lineCap: .round, lineJoin: .round)
        context.stroke(tail, with: .color(.white.opacity(expand)), style: stroke)
        context.stroke(chevron, with: .color(.white), style: stroke)
    }
}

#Preview {
    ZStack {
        Color(hex: 0xeff6f9).ignoresSafeArea()
        ArrowExpandButton(autoPlay: true)
    }
}

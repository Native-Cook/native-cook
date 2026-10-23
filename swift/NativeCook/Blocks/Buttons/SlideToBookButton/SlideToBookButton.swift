import SwiftUI

/// Black "Book a call" pill with a lime knob. Tap it, or slide the knob to the right: the knob
/// stretches across the whole pill, a phone icon replaces the arrow and a small "Call booked" tag
/// pops up above before everything slides back.
struct SlideToBookButton: View {
    var label = "Book a call"
    /// Shown in the tag above the button once the call is booked.
    var doneLabel = "Call booked"
    /// Replays the booking by itself with a pause between runs.
    var autoPlay = false
    /// Called when the booking is confirmed, by a tap or by sliding the knob across.
    var action: () -> Void = {}

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var origin = Date.now
    @State private var stretch = Tween(0)
    @State private var tag = Tween(0)
    @State private var busy = false
    @State private var settling = false
    @State private var dragging = false
    @State private var lastDragEnd = Date.distantPast

    var body: some View {
        Button {
            // A slide that ends inside the pill also reports a tap; the slide already handled it.
            guard !dragging, Date.now.timeIntervalSince(lastDragEnd) > 0.3 else { return }
            Task { await confirm(announce: true) }
        } label: {
            surface
        }
        .buttonStyle(PressStyle())
        .simultaneousGesture(slide)
        .accessibilityLabel(label)
        .accessibilityHint("Double tap or slide right to confirm")
        .task(id: autoPlay) {
            guard autoPlay else { return }
            while !Task.isCancelled {
                try? await Task.sleep(for: .seconds(Metrics.autoPlayPause))
                if Task.isCancelled { return }
                await confirm(announce: false)
            }
        }
    }

    private struct PressStyle: ButtonStyle {
        func makeBody(configuration: Configuration) -> some View {
            configuration.label
                .contentShape(Capsule())
                .scaleEffect(configuration.isPressed ? 0.97 : 1)
                .animation(.timingCurve(0.23, 1, 0.32, 1, duration: 0.13), value: configuration.isPressed)
                .sensoryFeedback(.impact(weight: .light), trigger: configuration.isPressed) { _, pressed in pressed }
        }
    }

    /// A horizontal slide moves the knob; it confirms past half-way, otherwise the knob springs back.
    private var slide: some Gesture {
        DragGesture(minimumDistance: 8)
            .onChanged { value in
                guard !busy else { return }
                if !dragging, abs(value.translation.width) < abs(value.translation.height) { return }
                dragging = true
                stretch = Tween(min(1, max(0, value.translation.width / Metrics.travel)))
            }
            .onEnded { value in
                guard dragging else { return }
                dragging = false
                lastDragEnd = .now
                if value.translation.width / Metrics.travel > 0.5 {
                    Task { await confirm(announce: true) }
                } else {
                    Task {
                        settling = true
                        stretch.retarget(0, duration: 0.32, curve: Ease.out)
                        try? await Task.sleep(for: .seconds(0.34))
                        settling = false
                    }
                }
            }
    }

    private var surface: some View {
        // The knob breathes, the arrow marches and the label shines all the time; with reduced
        // motion the clock only runs while the knob is animating.
        TimelineView(.animation(paused: reduceMotion && !busy && !settling)) { timeline in
            let date = timeline.date
            let clock = reduceMotion ? 0 : date.timeIntervalSince(origin)
            let p = stretch.value(at: date)
            let shown = tag.value(at: date)
            let knob = knobWidth(p, clock)
            // The stretching knob wipes the label away from the left.
            let cover = max(0, Metrics.inset + knob + 2 - Metrics.labelLeft)

            ZStack(alignment: .topLeading) {
                Canvas { context, _ in draw(context, p: p, clock: clock, knob: knob) }
                    .padding(-Metrics.bleed)
                    .allowsHitTesting(false)

                labelText(Color(hex: 0x8c8c8c))
                    .overlay {
                        if !reduceMotion {
                            // A band of brighter text sweeping across the label.
                            let x = -40 + (Metrics.labelWidth + 80) * seg(clock.truncatingRemainder(dividingBy: 2.6), 0.3, 1.3)
                            labelText(.white)
                                .mask(alignment: .leading) {
                                    LinearGradient(
                                        colors: [.white.opacity(0), .white.opacity(0.7), .white.opacity(0)],
                                        startPoint: .leading, endPoint: .trailing
                                    )
                                    .frame(width: 64)
                                    .offset(x: x - 32)
                                }
                        }
                    }
                    .mask(alignment: .trailing) {
                        Rectangle().frame(width: max(0, Metrics.labelWidth - cover))
                    }
                    .offset(x: Metrics.labelLeft)
            }
            .frame(width: Metrics.width, height: Metrics.height)
            .overlay(alignment: .top) {
                tagView
                    .opacity(shown)
                    .scaleEffect(0.9 + 0.1 * shown)
                    .offset(y: -26 + (1 - shown) * 6)
            }
        }
    }

    private func labelText(_ color: Color) -> some View {
        Text(label)
            .font(.system(size: 19, weight: .regular))
            .kerning(0.1)
            .lineLimit(1)
            .foregroundStyle(color)
            .frame(width: Metrics.labelWidth, height: Metrics.height)
            .accessibilityHidden(true)
    }

    private var tagView: some View {
        Text(doneLabel)
            .font(.system(size: 10, weight: .medium))
            .lineLimit(1)
            .fixedSize()
            .foregroundStyle(Color(hex: 0x3f6b35))
            .padding(.horizontal, 9)
            .frame(height: 21)
            .background {
                ZStack(alignment: .bottom) {
                    RoundedRectangle(cornerRadius: 1.5)
                        .frame(width: 8, height: 8)
                        .rotationEffect(.degrees(45))
                        .offset(y: 3)
                    RoundedRectangle(cornerRadius: 8)
                }
                .foregroundStyle(Color(hex: 0xdcfbd3))
            }
            .accessibilityHidden(true)
    }

    /// One booking. The stretch is the button's state change, so it stays (short) with reduced motion.
    private func confirm(announce: Bool) async {
        guard !busy else { return }
        busy = true
        if announce { action() }
        stretch.retarget(1, duration: 0.42, curve: Ease.out)
        await pause(0.22)
        tag.retarget(1, duration: 0.26, curve: Ease.out)
        await pause(Metrics.holdSeconds)
        tag.retarget(0, duration: 0.2, curve: .easeInOut)
        await pause(0.15)
        stretch.retarget(0, duration: 0.48, curve: .easeInOut)
        await pause(0.56)
        busy = false
    }

    private func pause(_ seconds: Double) async {
        // If the task is cancelled the pauses return at once, so a run still ends in the idle state.
        try? await Task.sleep(for: .seconds(seconds))
    }

    /// Width of the lime knob for a stretch `p` (0 resting, 1 filling the pill) at time `t`.
    private func knobWidth(_ p: Double, _ t: Double) -> Double {
        let breathe = 0.5 - 0.5 * cos(tau * t / 1.8)
        return Metrics.knobWidth + Metrics.travel * p + Metrics.nudge * breathe * (1 - seg(p, 0, 0.2))
    }

    private func draw(_ context: GraphicsContext, p: Double, clock: Double, knob: Double) {
        let rect = CGRect(x: Metrics.bleed, y: Metrics.bleed, width: Metrics.width, height: Metrics.height)
        let ink = Color(hex: 0x0c0c0d)
        context.fill(capsule(rect), with: .color(ink))

        // Glossy edge: a thin light line following the top of the pill.
        var gloss = context
        gloss.clip(to: capsule(rect))
        gloss.stroke(
            capsule(rect.insetBy(dx: 3.5, dy: 3.5)),
            with: .linearGradient(
                Gradient(stops: [
                    .init(color: .white.opacity(0.8), location: 0),
                    .init(color: .white.opacity(0.35), location: 0.35),
                    .init(color: .white.opacity(0), location: 1),
                ]),
                startPoint: CGPoint(x: 0, y: rect.minY), endPoint: CGPoint(x: 0, y: rect.minY + rect.height * 0.4)
            ),
            lineWidth: 1
        )

        let knobRect = CGRect(
            x: rect.minX + Metrics.inset, y: rect.minY + Metrics.inset,
            width: knob, height: Metrics.height - Metrics.inset * 2
        )
        context.fill(
            capsule(knobRect),
            with: .linearGradient(
                Gradient(stops: [
                    .init(color: Color(hex: 0x7dfe3f), location: 0),
                    .init(color: Color(hex: 0xa2ff24), location: 0.6),
                    .init(color: Color(hex: 0xa8ff20), location: 1),
                ]),
                startPoint: CGPoint(x: knobRect.minX, y: 0), endPoint: CGPoint(x: knobRect.maxX, y: 0)
            )
        )

        let c = CGPoint(x: knobRect.midX, y: rect.midY)

        // Dotted arrow: the head stays lit, the three tail dots light up one after the other.
        let arrow = 1 - seg(p, 0.05, 0.5)
        if arrow > 0 {
            func dot(_ x: Double, _ y: Double, _ opacity: Double) {
                let r = Metrics.dotRadius
                let box = CGRect(x: c.x + x * Metrics.dot - r, y: c.y + y * Metrics.dot - r, width: r * 2, height: r * 2)
                context.fill(Path(ellipseIn: box), with: .color(ink.opacity(opacity * arrow)))
            }
            for (x, y) in Metrics.arrowHead { dot(x, y, 1) }
            for (index, x) in Metrics.arrowTail.enumerated() {
                dot(x, 0, 0.3 + 0.7 * (0.5 + 0.5 * sin(tau * (clock / 0.9 - Double(index) * 0.22))))
            }
        }

        let phone = seg(p, 0.4, 0.85)
        if phone > 0 {
            var layer = context
            layer.opacity = phone
            layer.translateBy(x: c.x - 1, y: c.y + 1)
            let scale = 1.1 * (0.7 + 0.3 * seg(p, 0.4, 1))
            layer.scaleBy(x: scale, y: scale)
            var path = polyline(Metrics.handset, close: true)
            path.addPath(polyline(Metrics.signalNear, close: false))
            path.addPath(polyline(Metrics.signalFar, close: false))
            layer.stroke(path, with: .color(ink), style: StrokeStyle(lineWidth: 1.35, lineCap: .round, lineJoin: .round))
        }
    }

    private func polyline(_ points: [Double], close: Bool) -> Path {
        var path = Path()
        for i in stride(from: 0, to: points.count, by: 2) {
            let point = CGPoint(x: points[i], y: points[i + 1])
            if i == 0 { path.move(to: point) } else { path.addLine(to: point) }
        }
        if close { path.closeSubpath() }
        return path
    }
}

private func seg(_ t: Double, _ a: Double, _ b: Double) -> Double {
    min(1, max(0, (t - a) / (b - a)))
}

private enum Metrics {
    static let width = 240.0
    static let height = 64.0
    /// The canvas overflows the button by this much on every side so its edge is not clipped.
    static let bleed = 4.0
    static let inset = 10.0
    static let knobWidth = 72.0
    static let travel = width - inset * 2 - knobWidth
    /// How far the resting knob stretches while it "breathes" to hint at the slide.
    static let nudge = 8.0
    /// The label sits in the space to the right of the resting knob.
    static let labelLeft = inset + knobWidth + 4
    static let labelWidth = width - 14 - labelLeft
    static let holdSeconds = 1.7
    static let autoPlayPause = 1.7

    static let dot = 3.2
    static let dotRadius = 1.05
    static let arrowHead: [(Double, Double)] = [(2, 0), (1, 0), (1, -1), (0, -2), (1, 1), (0, 2)]
    static let arrowTail: [Double] = [-2, -1, 0]

    // Phone handset outline and its two signal arcs, in points around the icon's centre.
    static let handset: [Double] = [
        4.41, 6.18, 3.6, 6.11, 2.79, 5.98, 1.99, 5.79, 1.21, 5.54, 0.44, 5.23, -0.29, 4.87, -1.0, 4.46, -1.68, 3.99,
        -2.32, 3.48, -2.92, 2.92, -3.48, 2.32, -3.99, 1.68, -4.46, 1.0, -4.87, 0.29, -5.23, -0.44, -5.54, -1.21,
        -5.79, -1.99, -5.98, -2.79, -6.11, -3.6, -6.18, -4.41, -1.29, -4.67, -1.25, -4.21, -1.18, -3.75, -1.07,
        -3.31, -0.93, -2.87, -1.42, -2.14, -2.32, -1.08, -2.43, -0.28, -2.06, 0.25, -1.66, 0.75, -1.22, 1.22, -0.75,
        1.66, -0.25, 2.06, 0.28, 2.43, 1.08, 2.32, 2.14, 1.42, 2.87, 0.93, 3.31, 1.07, 3.75, 1.18, 4.21, 1.25, 4.67,
        1.29,
    ]
    static let signalNear: [Double] = [
        2.07, -4.97, 2.61, -4.85, 3.12, -4.64, 3.59, -4.36, 4.0, -4.0, 4.36, -3.59, 4.64, -3.12, 4.85, -2.61, 4.97,
        -2.07,
    ]
    static let signalFar: [Double] = [
        2.49, -7.94, 3.5, -7.71, 4.46, -7.33, 5.34, -6.79, 6.13, -6.13, 6.79, -5.34, 7.33, -4.46, 7.71, -3.5, 7.94,
        -2.49,
    ]
}

#Preview {
    ZStack {
        Color(hex: 0xfafafa).ignoresSafeArea()
        SlideToBookButton(autoPlay: true)
    }
}

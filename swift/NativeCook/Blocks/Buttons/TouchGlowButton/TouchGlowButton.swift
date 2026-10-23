import SwiftUI
import UIKit.UIGestureRecognizerSubclass

/// Dark indigo button whose border and inner glow light up around a point: under the finger while
/// it is held (drag to move the light), or wandering by itself with `autoPlay`.
struct TouchGlowButton: View {
    var label = "Button"
    /// Lets the light wander around the button by itself; a finger still takes it over.
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

/// Reports where a finger is while it is down on this view. It only listens: the recogniser sits on
/// the window and never recognises anything, so the button's tap and a scroll view's pan behave
/// exactly as if it were not there (a SwiftUI `DragGesture` here would swallow scrolling).
private struct TouchLocationReader: UIViewRepresentable {
    let onChange: (CGPoint) -> Void

    func makeUIView(context: Context) -> Probe {
        let probe = Probe()
        probe.onChange = onChange
        return probe
    }

    func updateUIView(_ probe: Probe, context: Context) {
        probe.onChange = onChange
    }

    final class Probe: UIView, UIGestureRecognizerDelegate {
        var onChange: (CGPoint) -> Void = { _ in }
        private var listener: Listener?

        override init(frame: CGRect) {
            super.init(frame: frame)
            isUserInteractionEnabled = false
        }

        @available(*, unavailable)
        required init?(coder: NSCoder) {
            fatalError("init(coder:) has not been implemented")
        }

        override func didMoveToWindow() {
            super.didMoveToWindow()
            if let listener { listener.view?.removeGestureRecognizer(listener) }
            listener = nil
            guard let window else { return }
            let listener = Listener(probe: self)
            listener.cancelsTouchesInView = false
            listener.delaysTouchesBegan = false
            listener.delaysTouchesEnded = false
            listener.delegate = self
            window.addGestureRecognizer(listener)
            self.listener = listener
        }

        func gestureRecognizer(
            _ gestureRecognizer: UIGestureRecognizer,
            shouldRecognizeSimultaneouslyWith otherGestureRecognizer: UIGestureRecognizer
        ) -> Bool {
            true
        }
    }

    final class Listener: UIGestureRecognizer {
        private weak var probe: Probe?
        private var tracking = false

        init(probe: Probe) {
            self.probe = probe
            super.init(target: nil, action: nil)
        }

        override func touchesBegan(_ touches: Set<UITouch>, with event: UIEvent) {
            guard let probe, let touch = touches.first else { return }
            let point = touch.location(in: probe)
            tracking = probe.bounds.contains(point)
            if tracking { probe.onChange(point) }
        }

        override func touchesMoved(_ touches: Set<UITouch>, with event: UIEvent) {
            guard tracking, let probe, let touch = touches.first else { return }
            probe.onChange(touch.location(in: probe))
        }

        override func touchesEnded(_ touches: Set<UITouch>, with event: UIEvent) {
            tracking = false
            state = .failed
        }

        override func touchesCancelled(_ touches: Set<UITouch>, with event: UIEvent) {
            tracking = false
            state = .failed
        }
    }
}

private enum Metrics {
    static let height = 64.0
    static let padX = 58.0
    static let radius = 14.0
    /// The canvas overflows the button by this much on every side to fit the glow underneath.
    static let bleed = 40.0
    static let glowRadius = 84.0
    static let rimRadius = 100.0
    /// Where the wandering light is frozen when the system asks for reduced motion.
    static let stillTime = 1.2
}

private let rim = (r: 124.0, g: 92.0, b: 255.0)

private func glowGradient(_ r: Double, _ g: Double, _ b: Double, _ peak: Double, _ mid: Double) -> Gradient {
    Gradient(stops: [
        .init(color: .rgba(r, g, b, peak), location: 0),
        .init(color: .rgba(r, g, b, mid), location: 0.45),
        .init(color: .rgba(r, g, b, 0), location: 1),
    ])
}

private let violetGlow = glowGradient(124, 58, 237, 0.62, 0.26)
private let blueGlow = glowGradient(48, 86, 220, 0.7, 0.28)
private let rimGradient = Gradient(stops: [
    .init(color: .rgba(206, 194, 255, 1), location: 0),
    .init(color: .rgba(rim.r, rim.g, rim.b, 0.55), location: 0.45),
    .init(color: .rgba(rim.r, rim.g, rim.b, 0), location: 1),
])
private let restingBorder = Gradient(stops: [
    .init(color: .rgba(rim.r, rim.g, rim.b, 0.5), location: 0),
    .init(color: .rgba(rim.r, rim.g, rim.b, 0.13), location: 0.5),
    .init(color: .rgba(rim.r, rim.g, rim.b, 0.42), location: 1),
])

private struct Surface: View {
    let label: String
    let autoPlay: Bool
    let isPressed: Bool

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var epoch = Date.now
    /// Finger position in the button's own coordinates.
    @State private var finger = CGPoint.zero
    /// How much the finger has taken the light over.
    @State private var touch = Tween(0)
    @State private var settling = false
    @State private var settleToken = 0

    private var wandering: Bool { autoPlay && !reduceMotion }

    var body: some View {
        TimelineView(.animation(paused: !(wandering || isPressed || settling))) { timeline in
            let time = Metrics.stillTime + (reduceMotion ? 0 : timeline.date.timeIntervalSince(epoch))
            let touch = touch.value(at: timeline.date)

            Text(label)
                .font(.system(size: 22, weight: .semibold))
                .lineLimit(1)
                .foregroundStyle(Color(hex: 0xfafaff))
                .padding(.horizontal, Metrics.padX)
                .frame(height: Metrics.height)
                .background {
                    Canvas { context, size in
                        draw(context, size: size, time: time, touch: touch)
                    }
                    .padding(-Metrics.bleed)
                    .allowsHitTesting(false)
                }
        }
        .background { TouchLocationReader { finger = $0 } }
        .contentShape(RoundedRectangle(cornerRadius: Metrics.radius))
        .scaleEffect(isPressed ? 0.97 : 1)
        .animation(.timingCurve(0.23, 1, 0.32, 1, duration: 0.13), value: isPressed)
        .sensoryFeedback(.impact(weight: .light), trigger: isPressed) { _, pressed in pressed }
        .onChange(of: isPressed) { _, pressed in
            // Lingers after release so the glow isn't hidden under the thumb the whole time.
            touch.retarget(pressed ? 1 : 0, duration: pressed ? 0.18 : 0.7, curve: .easeInOut)
            settling = true
            settleToken += 1
        }
        .task(id: settleToken) {
            guard settleToken > 0 else { return }
            try? await Task.sleep(for: .seconds(touch.duration + 0.05))
            if !Task.isCancelled { settling = false }
        }
    }

    private func draw(_ context: GraphicsContext, size: CGSize, time: Double, touch: Double) {
        let bleed = Metrics.bleed
        let rect = CGRect(x: bleed, y: bleed, width: size.width - bleed * 2, height: size.height - bleed * 2)
        let w = rect.width
        let h = rect.height
        let radius = Metrics.radius
        let shape = Path(roundedRect: rect, cornerRadius: radius, style: .circular)

        // By itself the light sweeps side to side while bobbing between the top and bottom edges.
        var px = finger.x
        var py = finger.y
        if autoPlay {
            let ax = w / 2 + (w / 2 - 6) * sin(0.9 * time)
            let ay = h / 2 + (h / 2 - 4) * sin(1.7 * time + 1)
            px = ax + (finger.x - ax) * touch
            py = ay + (finger.y - ay) * touch
        }
        let lit = autoPlay ? 1 : touch
        let point = CGPoint(x: rect.minX + px, y: rect.minY + py)
        // The glow is violet on the left of the button and turns blue towards the right.
        let mix = min(1, max(0, px / w))

        var under = context
        under.opacity = 0.6 + 0.4 * lit
        under.addFilter(.blur(radius: 16))
        under.fill(
            Path(
                roundedRect: CGRect(x: rect.minX + 8, y: rect.minY + 12, width: w - 16, height: h - 4),
                cornerRadius: radius, style: .circular
            ),
            with: .color(.rgba(84, 44, 214, 0.34))
        )
        context.fill(shape, with: .color(Color(hex: 0x0e0428)))

        var inside = context
        inside.clip(to: shape)
        let glowSize = CGSize(width: Metrics.glowRadius, height: Metrics.glowRadius)
        var violet = inside
        violet.opacity = lit * (1 - mix)
        violet.fillEllipse(center: point, radii: glowSize, gradient: violetGlow)
        var blue = inside
        blue.opacity = lit * mix
        blue.fillEllipse(center: point, radii: glowSize, gradient: blueGlow)

        // Resting border: a little brighter towards the bottom-left and top-right corners.
        context.stroke(
            Path(roundedRect: rect.insetBy(dx: 0.5, dy: 0.5), cornerRadius: radius - 0.5, style: .circular),
            with: .linearGradient(
                restingBorder,
                startPoint: CGPoint(x: rect.minX, y: rect.maxY),
                endPoint: CGPoint(x: rect.maxX, y: rect.minY)
            ),
            lineWidth: 1
        )

        // The stretch of border nearest the light.
        let rimLight = GraphicsContext.Shading.radialGradient(
            rimGradient, center: point, startRadius: 0, endRadius: Metrics.rimRadius
        )
        var bloom = context
        bloom.opacity = 0.6 * lit
        bloom.addFilter(.blur(radius: 4))
        bloom.stroke(shape, with: rimLight, lineWidth: 4)
        var edge = context
        edge.opacity = lit
        edge.stroke(
            Path(roundedRect: rect.insetBy(dx: 0.75, dy: 0.75), cornerRadius: radius - 0.75, style: .circular),
            with: rimLight,
            lineWidth: 1.5
        )
    }
}

#Preview {
    ZStack {
        Color(hex: 0x03031a).ignoresSafeArea()
        TouchGlowButton(autoPlay: true)
    }
}

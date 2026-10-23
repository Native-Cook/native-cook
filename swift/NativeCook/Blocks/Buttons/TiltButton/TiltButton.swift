import SwiftUI

/// Violet-to-blue pill that tilts in 3D so the side the finger landed on rises towards you, with
/// the label floating above it. With `autoPlay` it sways through the same tilt by itself.
struct TiltButton: View {
    var label = "BUTTON"
    /// Sways through the tilt by itself, as if a pointer were circling over it, with rests in between.
    var autoPlay = false
    var action: () -> Void = {}

    @State private var finger = CGPoint(x: Metrics.width / 2, y: Metrics.height / 2)

    var body: some View {
        Button(action: action) {
            Text(label)
        }
        .buttonStyle(Style(label: label, autoPlay: autoPlay, finger: finger))
        .overlay {
            TouchProbe { finger = $0 }
        }
        .accessibilityLabel(label)
    }

    private struct Style: ButtonStyle {
        let label: String
        let autoPlay: Bool
        let finger: CGPoint

        func makeBody(configuration: Configuration) -> some View {
            Surface(label: label, autoPlay: autoPlay, finger: finger, isPressed: configuration.isPressed)
        }
    }
}

/// Reports where a touch lands and then gets out of the way. A SwiftUI gesture that tracks the
/// finger from the first contact would stop the surrounding scroll view from scrolling, and a
/// button style only knows that it is pressed, not where.
private struct TouchProbe: UIViewRepresentable {
    let onTouch: (CGPoint) -> Void

    func makeUIView(context: Context) -> Probe {
        Probe()
    }

    func updateUIView(_ view: Probe, context: Context) {
        view.onTouch = onTouch
    }

    final class Probe: UIView {
        var onTouch: ((CGPoint) -> Void)?

        override func hitTest(_ point: CGPoint, with event: UIEvent?) -> UIView? {
            if bounds.contains(point), event?.type == .touches {
                // Hit testing must not change state, so report once it has finished.
                DispatchQueue.main.async { [onTouch] in onTouch?(point) }
            }
            // Never the target: the touch carries on to the button underneath.
            return nil
        }
    }
}

private enum Metrics {
    // Fixed size: the tilt is measured against it.
    static let width = 190.0
    static let height = 58.0
    static let maxTiltDegrees = 22.0
    static let perspective = 520.0
    /// The label floats this far above the pill, so it slides away from the raised side.
    static let lift = 26.0
    /// The pill grows a little while it is tilted, as in the reference's hover.
    static let grow = 0.06

    /// One lap of the imaginary pointer around the pill.
    static let lapSeconds = 3.6
    static let autoPlayPause = 1.3
    static let autoPlayRamp = 0.6
    /// How far from the centre the imaginary pointer circles, 1 being the pill's edge.
    static let autoPlayReach = 0.9
}

private let easeInOutCubic = UnitCurve.bezier(
    startControlPoint: UnitPoint(x: 0.65, y: 0),
    endControlPoint: UnitPoint(x: 0.35, y: 1)
)

private struct Surface: View {
    let label: String
    let autoPlay: Bool
    let finger: CGPoint
    let isPressed: Bool

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    // Where the finger landed, -1…1 on both axes from the pill's centre, and how much it counts.
    @State private var touchX = Tween(0)
    @State private var touchY = Tween(0)
    @State private var press = Tween(0)
    /// How much the imaginary circling pointer counts.
    @State private var sway = Tween(0)
    @State private var started = Date.now
    @State private var settling = false
    @State private var settleToken = 0

    private var swaying: Bool { autoPlay && !reduceMotion }

    var body: some View {
        TimelineView(.animation(paused: !(swaying || isPressed || settling))) { timeline in
            let date = timeline.date
            let pressNow = press.value(at: date)
            let swayNow = sway.value(at: date)
            // The pointer the pill reacts to: the finger while pressed, otherwise the imaginary one.
            let theta = tau * date.timeIntervalSince(started) / Metrics.lapSeconds
            let autoX = cos(theta) * swayNow * Metrics.autoPlayReach
            let autoY = sin(theta) * swayNow * Metrics.autoPlayReach
            let x = autoX + (touchX.value(at: date) - autoX) * pressNow
            let y = autoY + (touchY.value(at: date) - autoY) * pressNow
            let amount = max(swayNow, pressNow)

            if reduceMotion {
                // No tilt: a press only dims the pill.
                content(x: 0, y: 0, amount: 0).opacity(1 - 0.18 * pressNow)
            } else {
                content(x: x, y: y, amount: amount)
            }
        }
        .frame(width: Metrics.width, height: Metrics.height)
        .contentShape(Capsule())
        .onChange(of: finger, initial: true) { _, location in
            track(location)
        }
        .sensoryFeedback(.impact(weight: .light), trigger: isPressed) { _, pressed in pressed }
        .onChange(of: isPressed) { _, pressed in
            if pressed {
                press.retarget(1, duration: 0.22, curve: Ease.out)
            } else {
                press.retarget(0, duration: 0.5, curve: Ease.out)
                settling = true
                settleToken += 1
            }
        }
        .task(id: settleToken) {
            guard settleToken > 0 else { return }
            try? await Task.sleep(for: .seconds(0.55))
            guard !Task.isCancelled else { return }
            settling = false
        }
        .task(id: swaying) {
            guard swaying else { return }
            started = .now
            while !Task.isCancelled {
                try? await Task.sleep(for: .seconds(Metrics.autoPlayPause))
                if Task.isCancelled { break }
                sway.retarget(1, duration: Metrics.autoPlayRamp, curve: easeInOutCubic)
                try? await Task.sleep(for: .seconds(Metrics.autoPlayRamp + Metrics.lapSeconds))
                sway.retarget(0, duration: Metrics.autoPlayRamp, curve: easeInOutCubic)
                try? await Task.sleep(for: .seconds(Metrics.autoPlayRamp))
            }
            sway.retarget(0, duration: Metrics.autoPlayRamp, curve: easeInOutCubic)
        }
    }

    private func track(_ location: CGPoint) {
        let x = min(max((location.x - Metrics.width / 2) / (Metrics.width / 2), -1), 1)
        let y = min(max((location.y - Metrics.height / 2) / (Metrics.height / 2), -1), 1)
        if press.value(at: .now) < 0.05 {
            // A fresh touch: start from where the finger landed.
            touchX = Tween(x)
            touchY = Tween(y)
        } else {
            touchX.retarget(x, duration: 0.09, curve: Ease.out)
            touchY.retarget(y, duration: 0.09, curve: Ease.out)
        }
    }

    private func content(x: Double, y: Double, amount: Double) -> some View {
        // Tilting about the axis at right angles to the pointer raises the side it is on.
        let reach = (x * x + y * y).squareRoot()
        let angle = Angle.degrees(Metrics.maxTiltDegrees * reach)
        let axis: (x: CGFloat, y: CGFloat, z: CGFloat) = reach > 0.0001 ? (y, -x, 0) : (1, 0, 0)
        let perspective = Metrics.width / Metrics.perspective
        let toRadians = Metrics.maxTiltDegrees * .pi / 180
        let grow = 1 + Metrics.grow * amount

        return ZStack {
            Capsule()
                .fill(
                    LinearGradient(
                        colors: [Color(hex: 0x5d1bee), Color(hex: 0x12a4f1)],
                        startPoint: .bottomLeading, endPoint: .topTrailing
                    )
                )
                .scaleEffect(grow)
                .rotation3DEffect(angle, axis: axis, perspective: perspective)

            Text(label)
                .font(.system(size: 21, weight: .bold))
                .kerning(1.5)
                .lineLimit(1)
                .foregroundStyle(.white)
                .frame(width: Metrics.width, height: Metrics.height)
                .scaleEffect(grow * (1 + Metrics.lift / Metrics.perspective * amount))
                .rotation3DEffect(angle, axis: axis, perspective: perspective)
                // Standing in for a z translation: the lifted label shifts along the pill's tilted normal.
                .offset(x: -Metrics.lift * sin(toRadians * x), y: -Metrics.lift * sin(toRadians * y))
                .accessibilityHidden(true)
        }
    }
}

#Preview {
    ZStack {
        Color.black.ignoresSafeArea()
        TiltButton(autoPlay: true)
    }
}

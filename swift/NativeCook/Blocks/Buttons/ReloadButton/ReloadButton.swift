import SwiftUI

/// Pale blue "Reload" button. A tap collapses it to a square around its refresh icon, the icon
/// spins while it works, turns green when done, and the button opens back up before returning to blue.
struct ReloadButton: View {
    var label = "Reload"
    /// Replays the reload by itself with a pause between runs.
    var autoPlay = false
    var action: () -> Void = {}

    @State private var taps = 0

    var body: some View {
        Button {
            taps += 1
            action()
        } label: {
            Text(label)
        }
        .buttonStyle(Style(label: label, autoPlay: autoPlay, taps: taps))
        // The visible label is drawn in a canvas, so name the button explicitly.
        .accessibilityLabel(label)
    }

    private struct Style: ButtonStyle {
        let label: String
        let autoPlay: Bool
        let taps: Int

        func makeBody(configuration: Configuration) -> some View {
            Surface(label: label, autoPlay: autoPlay, taps: taps, isPressed: configuration.isPressed)
        }
    }
}

private enum Metrics {
    static let height = 56.0
    static let radius = 7.0
    static let padLeading = 26.0
    static let icon = 24.0
    static let gap = 18.0
    static let padTrailing = 32.0
    /// Width of the square the button collapses to while it works.
    static let collapsed = 60.0
    static let spinTurns = 2.0
    static let spinSeconds = 1.3
    static let autoPlayPause = 1.5
}

private let inOut = UnitCurve.bezier(
    startControlPoint: UnitPoint(x: 0.77, y: 0), endControlPoint: UnitPoint(x: 0.175, y: 1)
)
private let spinCurve = UnitCurve.bezier(
    startControlPoint: UnitPoint(x: 0.45, y: 0), endControlPoint: UnitPoint(x: 0.55, y: 1)
)

/// Two arcs (radius 9) chasing each other clockwise, each ending in an arrowhead. Centred on the origin.
private let arcs: Path = {
    var p = Path()
    p.addArc(center: .zero, radius: 9, startAngle: .degrees(215), endAngle: .degrees(350), clockwise: false)
    p.move(to: CGPoint(x: 7.372, y: 5.162))
    p.addArc(center: .zero, radius: 9, startAngle: .degrees(35), endAngle: .degrees(170), clockwise: false)
    return p
}()
private let heads: Path = {
    var p = Path()
    p.move(to: CGPoint(x: 5.8, y: -1.6))
    p.addLine(to: CGPoint(x: 12.2, y: -1.6))
    p.addLine(to: CGPoint(x: 9, y: 2.4))
    p.closeSubpath()
    p.move(to: CGPoint(x: -12.2, y: 1.6))
    p.addLine(to: CGPoint(x: -5.8, y: 1.6))
    p.addLine(to: CGPoint(x: -9, y: -2.4))
    p.closeSubpath()
    return p
}()

private struct Surface: View {
    let label: String
    let autoPlay: Bool
    let taps: Int
    let isPressed: Bool

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    /// 1 = full width with the label, 0 = collapsed to the icon.
    @State private var open = Tween(1)
    /// Icon rotation in turns.
    @State private var spin = Tween(0)
    /// 0 = blue, 1 = green (done).
    @State private var ok = Tween(0)
    /// A small swell before the collapse, like the reference's wind-up.
    @State private var swell = Tween(1)
    @State private var busy = false

    private var labelText: Text {
        Text(label).font(.system(size: 24, weight: .semibold))
    }

    var body: some View {
        TimelineView(.animation(paused: !busy)) { timeline in
            let date = timeline.date

            // Invisible copy that gives the button its size; the visible label is drawn in the canvas
            // so it can be clipped by the shrinking face.
            labelText
                .lineLimit(1)
                .opacity(0)
                .padding(.leading, Metrics.padLeading + Metrics.icon + Metrics.gap)
                .padding(.trailing, Metrics.padTrailing)
                .frame(height: Metrics.height)
                .background {
                    Canvas { context, size in
                        draw(
                            context, size: size,
                            open: open.value(at: date), spin: spin.value(at: date), ok: ok.value(at: date)
                        )
                    }
                    .allowsHitTesting(false)
                }
                .scaleEffect(swell.value(at: date))
        }
        .contentShape(Rectangle())
        .scaleEffect(isPressed ? 0.97 : 1)
        .animation(.timingCurve(0.23, 1, 0.32, 1, duration: 0.13), value: isPressed)
        .sensoryFeedback(.impact(weight: .light), trigger: isPressed) { _, pressed in pressed }
        .onChange(of: taps) {
            Task { await reload() }
        }
        .task(id: autoPlay) {
            guard autoPlay else { return }
            while !Task.isCancelled {
                try? await Task.sleep(for: .seconds(Metrics.autoPlayPause))
                if Task.isCancelled { return }
                await reload()
            }
        }
    }

    private func reload() async {
        guard !busy else { return }
        busy = true
        defer { busy = false }

        if reduceMotion {
            // No collapse or spin: just the colour change.
            await pause(0.5)
            ok.retarget(1, duration: 0.3, curve: .easeInOut)
            await pause(1.6)
            ok.retarget(0, duration: 0.45, curve: .easeInOut)
            await pause(0.45)
            return
        }

        swell.retarget(1.05, duration: 0.2, curve: Ease.out)
        await pause(0.2)
        swell.retarget(1, duration: 0.42, curve: inOut)
        open.retarget(0, duration: 0.42, curve: inOut)
        await pause(0.5)

        // Whole turns, so the icon comes to rest exactly as it started.
        spin.retarget(0, duration: 0, curve: .linear)
        spin.retarget(Metrics.spinTurns, duration: Metrics.spinSeconds, curve: spinCurve)
        await pause(Metrics.spinSeconds - 0.25)
        ok.retarget(1, duration: 0.3, curve: .easeInOut)
        await pause(0.95)

        open.retarget(1, duration: 0.46, curve: inOut)
        await pause(0.46 + 1.1)
        ok.retarget(0, duration: 0.45, curve: .easeInOut)
        await pause(0.45)
    }

    private func pause(_ seconds: Double) async {
        // If the task is cancelled the pauses return at once, so a run still ends in the idle state.
        try? await Task.sleep(for: .seconds(seconds))
    }

    private func draw(_ context: GraphicsContext, size: CGSize, open: Double, spin: Double, ok: Double) {
        let w = Metrics.collapsed + (size.width - Metrics.collapsed) * open
        let x = (size.width - w) / 2
        let face = Path(
            roundedRect: CGRect(x: x, y: 0, width: w, height: size.height),
            cornerRadius: Metrics.radius, style: .circular
        )
        context.fill(face, with: .color(Color(hex: 0xc7dff3)))
        context.fill(face, with: .color(Color(hex: 0xcbf8d0, opacity: ok)))

        // The label leaves upwards as the button closes and drops back in as it opens,
        // clipped by the shrinking face.
        var window = context
        window.clip(to: face)
        window.opacity = open * open
        let text = window.resolve(labelText.foregroundStyle(mix(0x3a63b7, 0x46c357, ok)))
        window.draw(
            text,
            at: CGPoint(
                x: x + Metrics.padLeading + Metrics.icon + Metrics.gap,
                y: size.height / 2 - (1 - open) * size.height * 0.55
            ),
            anchor: .leading
        )

        // The icon sits in the leading padding and drifts to the middle of the square.
        var icon = context
        icon.translateBy(
            x: x + Metrics.collapsed / 2 + (Metrics.padLeading + Metrics.icon / 2 - Metrics.collapsed / 2) * open,
            y: size.height / 2
        )
        icon.rotate(by: .radians(tau * spin))
        let color = mix(0x2e3ccb, 0x42c258, ok)
        icon.stroke(arcs, with: .color(color), style: StrokeStyle(lineWidth: 2, lineCap: .round))
        icon.fill(heads, with: .color(color))
    }
}

/// Linear blend between two 0xRRGGBB colours.
private func mix(_ from: UInt32, _ to: UInt32, _ t: Double) -> Color {
    func channel(_ shift: UInt32) -> Double {
        let a = Double((from >> shift) & 0xff)
        let b = Double((to >> shift) & 0xff)
        return a + (b - a) * t
    }
    return .rgba(channel(16), channel(8), channel(0), 1)
}

#Preview {
    ZStack {
        Color(hex: 0xf7f7f7).ignoresSafeArea()
        ReloadButton(autoPlay: true)
    }
}

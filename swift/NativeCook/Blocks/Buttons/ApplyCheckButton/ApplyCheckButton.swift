import SwiftUI

/// Lime "Apply" pill. A tap lights its glow, opens a pale ring around it, then turns it charcoal
/// with a white check mark before it settles back.
struct ApplyCheckButton: View {
    var label = "Apply"
    /// Replays the confirmation by itself with a pause between runs.
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
        // The label fades out behind the check, so name the button explicitly.
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
    static let height = 50.0
    static let padX = 46.0
    /// The canvas overflows the button by this much on every side to fit the glow.
    static let bleed = 28.0
    /// Gap that opens between the pill and its glow when it is clicked.
    static let ring = 3.4
    static let holdSeconds = 2.0
    static let autoPlayPause = 1.4
}

private struct Surface: View {
    let label: String
    let autoPlay: Bool
    let taps: Int
    let isPressed: Bool

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var glow = Tween(0)
    @State private var ring = Tween(0)
    @State private var done = Tween(0)
    @State private var check = Tween(0)
    @State private var busy = false

    var body: some View {
        TimelineView(.animation(paused: !busy)) { timeline in
            let date = timeline.date
            let doneNow = done.value(at: date)

            Text(label)
                .font(.system(size: 18, weight: .semibold))
                .lineLimit(1)
                .foregroundStyle(Color(hex: 0x1c2118))
                // The label makes way for the check slightly ahead of the colour change.
                .opacity(1 - min(1, doneNow * 1.6))
                .padding(.horizontal, Metrics.padX)
                .frame(height: Metrics.height)
                .background {
                    Canvas { context, size in
                        draw(
                            context, size: size,
                            glow: glow.value(at: date), ring: ring.value(at: date),
                            done: doneNow, check: check.value(at: date)
                        )
                    }
                    .padding(-Metrics.bleed)
                    .allowsHitTesting(false)
                }
        }
        .contentShape(Capsule())
        .scaleEffect(isPressed ? 0.97 : 1)
        .animation(.timingCurve(0.23, 1, 0.32, 1, duration: 0.13), value: isPressed)
        .sensoryFeedback(.impact(weight: .light), trigger: isPressed) { _, pressed in pressed }
        .onChange(of: taps) {
            Task { await confirm(hover: false) }
        }
        .task(id: autoPlay) {
            guard autoPlay else { return }
            while !Task.isCancelled {
                try? await Task.sleep(for: .seconds(Metrics.autoPlayPause))
                if Task.isCancelled { return }
                await confirm(hover: true)
            }
        }
    }

    /// One confirmation. `hover` first lets the glow sit for a moment, like the reference's cursor arriving.
    private func confirm(hover: Bool) async {
        guard !busy else { return }
        busy = true
        // Reduced motion keeps the colour change and the check as plain fades.
        let quick = reduceMotion ? 0.0 : 1.0

        glow.retarget(quick, duration: hover ? 0.4 : 0.3, curve: Ease.out)
        if hover { await pause(0.9) }
        ring.retarget(quick, duration: 0.3, curve: Ease.out)
        await pause(hover ? 0.42 : 0.38)

        done.retarget(1, duration: 0.22, curve: .easeInOut)
        glow.retarget(0, duration: 0.26, curve: .easeInOut)
        ring.retarget(0, duration: 0.26, curve: .easeInOut)
        await pause(0.08)
        check.retarget(1, duration: 0.32, curve: Ease.out)
        await pause(Metrics.holdSeconds - 0.08)

        check.retarget(0, duration: 0.14, curve: .easeInOut)
        await pause(0.1)
        done.retarget(0, duration: 0.32, curve: .easeInOut)
        await pause(0.42)
        busy = false
    }

    private func pause(_ seconds: Double) async {
        // If the task is cancelled the pauses return at once, so a run still ends in the idle state.
        try? await Task.sleep(for: .seconds(seconds))
    }

    private func draw(_ context: GraphicsContext, size: CGSize, glow: Double, ring: Double, done: Double, check: Double) {
        let bleed = Metrics.bleed
        let rect = CGRect(x: bleed, y: bleed, width: size.width - bleed * 2, height: size.height - bleed * 2)
        let pill = capsule(rect)

        // The glow sits just outside the pill and is pushed further out by the ring.
        let reach = 1 + Metrics.ring * ring
        var halo = context
        halo.opacity = glow
        halo.addFilter(.blur(radius: 7))
        halo.fill(capsule(rect.insetBy(dx: -reach, dy: -reach)), with: .color(.rgba(186, 238, 51, 0.8)))

        let ringWidth = Metrics.ring * ring
        if ringWidth > 0 {
            context.stroke(
                capsule(rect.insetBy(dx: -ringWidth / 2, dy: -ringWidth / 2)),
                with: .color(Color(hex: 0xf1f4ee, opacity: ring)),
                lineWidth: ringWidth
            )
        }

        context.fill(pill, with: .color(Color(hex: 0xbaee33)))
        context.fill(pill, with: .color(Color(hex: 0x222522, opacity: done)))

        if check > 0 {
            let c = CGPoint(x: rect.midX, y: rect.midY)
            var mark = Path()
            mark.move(to: CGPoint(x: c.x - 9.5, y: c.y + 0.5))
            mark.addLine(to: CGPoint(x: c.x - 3.5, y: c.y + 6.5))
            mark.addLine(to: CGPoint(x: c.x + 10, y: c.y - 7.5))
            var layer = context.scaled(0.4 + 0.6 * check, about: c)
            layer.opacity = check
            layer.stroke(mark, with: .color(.white), style: StrokeStyle(lineWidth: 2.6, lineCap: .round, lineJoin: .round))
        }
    }
}

#Preview {
    ZStack {
        Color(hex: 0xf3f3f3).ignoresSafeArea()
        ApplyCheckButton(autoPlay: true)
    }
}

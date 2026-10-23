import SwiftUI

/// White "SHARE" cover that springs aside to reveal four share targets on an indigo tray.
struct ShareRevealButton: View {
    enum Target: CaseIterable {
        case message, mail, copy, more

        var name: String {
            switch self {
            case .message: "Message"
            case .mail: "Mail"
            case .copy: "Copy link"
            case .more: "More"
            }
        }
    }

    var label = "SHARE"
    /// Controlled state. Leave `nil` to let the button keep its own.
    var isExpanded: Binding<Bool>? = nil
    /// Slide open and shut by itself with a pause in each state. Tapping still works.
    var autoPlay = false
    /// One of the four share targets was tapped; the cover slides back afterwards.
    var onShare: (Target) -> Void = { _ in }

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var inner = false
    @State private var pressed = false

    private var open: Bool { isExpanded?.wrappedValue ?? inner }

    var body: some View {
        ZStack(alignment: .leading) {
            // 0 = covered, 1 = open. A spring, so it runs a little past either end before settling.
            Face(p: open ? 1 : 0, label: label, still: reduceMotion)
                .animation(
                    reduceMotion ? .linear(duration: 0.2) : .interpolatingSpring(mass: 1, stiffness: 320, damping: 27),
                    value: open
                )
                .allowsHitTesting(false)

            if open {
                hit("Close", x: 0, width: Metrics.tabHit) { setOpen(false) }
                ForEach(Array(Target.allCases.enumerated()), id: \.offset) { index, target in
                    hit(target.name, x: Metrics.iconX[index] - Metrics.iconHit / 2, width: Metrics.iconHit) {
                        onShare(target)
                        setOpen(false)
                    }
                }
            } else {
                hit(label, x: 0, width: Metrics.width) { setOpen(true) }
            }
        }
        .frame(width: Metrics.width, height: Metrics.height)
        .scaleEffect(pressed ? 0.97 : 1)
        .animation(.timingCurve(0.23, 1, 0.32, 1, duration: 0.13), value: pressed)
        .sensoryFeedback(.impact(weight: .light), trigger: pressed) { _, pressed in pressed }
        // Re-armed after every change, so a tap simply restarts the pause.
        .task(id: AutoKey(open: open, active: autoPlay && !reduceMotion && !pressed)) {
            guard autoPlay, !reduceMotion, !pressed else { return }
            try? await Task.sleep(for: .seconds(open ? Metrics.autoOpenSeconds : Metrics.autoClosedSeconds))
            if !Task.isCancelled { setOpen(!open) }
        }
    }

    private struct AutoKey: Equatable {
        let open: Bool
        let active: Bool
    }

    private func setOpen(_ next: Bool) {
        // The touch target that was just tapped is about to be swapped out, and may never report its release.
        pressed = false
        if let isExpanded { isExpanded.wrappedValue = next } else { inner = next }
    }

    private func hit(_ name: String, x: Double, width: Double, action: @escaping () -> Void) -> some View {
        Button(action: action) { Color.clear }
            .buttonStyle(PressReporter(pressed: $pressed))
            .frame(width: width, height: Metrics.height)
            .offset(x: x)
            .accessibilityLabel(name)
    }
}

private enum Metrics {
    static let width = 208.0
    static let height = 64.0
    /// Bevelled rim of the indigo tray the white cover sits in.
    static let border = 2.5
    static let innerW = width - border * 2
    static let innerH = height - border * 2
    /// How much of the cover stays in view when open: a tab to pull it back by.
    static let sliver = 16.0
    static let travel = innerW - sliver
    /// The icons glide in from this far to the right as the cover leaves.
    static let iconDrift = 40.0
    static let iconX = [59.0, 99.0, 139.0, 175.0]
    static let tabHit = 38.0
    static let iconHit = 40.0
    /// The canvas overflows the button by this much on every side to fit the glow.
    static let bleed = 48.0
    static let autoOpenSeconds = 2.4
    static let autoClosedSeconds = 1.7
}

/// `Animatable` so SwiftUI's spring can drive the number the `Canvas` reads.
private struct Face: View, Animatable {
    var p: Double
    let label: String
    /// Reduced motion: the cover fades instead of sliding.
    let still: Bool

    var animatableData: Double {
        get { p }
        set { p = newValue }
    }

    private var clamped: Double { min(1, max(0, p)) }

    var body: some View {
        ZStack {
            Canvas { context, size in
                draw(context, size: size)
            }
            .padding(-Metrics.bleed)

            // The label rides on the cover, clipped by the tray like the cover itself.
            Text(label)
                .font(.system(size: 17, weight: .heavy))
                .kerning(0.6)
                .lineLimit(1)
                .foregroundStyle(Color(hex: 0x3d37c6))
                .frame(width: Metrics.innerW, height: Metrics.innerH)
                .offset(x: still ? 0 : -Metrics.travel * p)
                .opacity(still ? 1 - clamped : 1)
                .frame(width: Metrics.innerW, height: Metrics.innerH)
                .clipShape(Capsule())
                .accessibilityHidden(true)
        }
        .frame(width: Metrics.width, height: Metrics.height)
    }

    private func draw(_ context: GraphicsContext, size: CGSize) {
        let bleed = Metrics.bleed
        let rect = CGRect(x: bleed, y: bleed, width: size.width - bleed * 2, height: size.height - bleed * 2)
        let inner = rect.insetBy(dx: Metrics.border, dy: Metrics.border)

        var glow = context
        glow.opacity = clamped
        glow.addFilter(.blur(radius: 16))
        glow.fill(
            capsule(CGRect(x: rect.minX + 6, y: rect.minY + 14, width: rect.width - 12, height: rect.height)),
            with: .color(.rgba(74, 62, 240, 0.6))
        )

        // Tray: a rim lit from above, then the indigo floor.
        context.fill(
            capsule(rect),
            with: .linearGradient(
                Gradient(stops: [
                    .init(color: Color(hex: 0x9499f3), location: 0),
                    .init(color: Color(hex: 0x5a56dc), location: 0.5),
                    .init(color: Color(hex: 0x2a2694), location: 1),
                ]),
                startPoint: CGPoint(x: 0, y: rect.minY), endPoint: CGPoint(x: 0, y: rect.maxY)
            )
        )
        context.fill(
            capsule(inner),
            with: .linearGradient(
                Gradient(colors: [Color(hex: 0x4b46de), Color(hex: 0x352fb6)]),
                startPoint: CGPoint(x: inner.minX, y: inner.minY), endPoint: CGPoint(x: inner.maxX, y: inner.maxY)
            )
        )

        var tray = context
        tray.clip(to: capsule(inner))

        let drift = still ? 0 : Metrics.iconDrift * (1 - p)
        let stroke = StrokeStyle(lineWidth: 1.7, lineCap: .round, lineJoin: .round)
        for (index, target) in ShareRevealButton.Target.allCases.enumerated() {
            var icon = tray
            icon.translateBy(x: rect.minX + Metrics.iconX[index] + drift, y: rect.midY)
            icon.stroke(target.icon, with: .color(.white), style: stroke)
        }

        var cover = tray
        cover.opacity = still ? 1 - clamped : 1
        cover.translateBy(x: still ? 0 : -Metrics.travel * p, y: 0)
        cover.drawLayer { layer in
            layer.addFilter(.blur(radius: 5))
            layer.fill(capsule(inner.offsetBy(dx: 3, dy: 0)), with: .color(.rgba(16, 12, 84, 0.45)))
        }
        cover.fill(
            capsule(inner),
            with: .linearGradient(
                Gradient(colors: [Color(hex: 0xfbfbfa), Color(hex: 0xf1f1ee)]),
                startPoint: CGPoint(x: 0, y: inner.minY), endPoint: CGPoint(x: 0, y: inner.maxY)
            )
        )
    }
}

private extension ShareRevealButton.Target {
    /// Generic line icon on a 20pt box centred on the origin.
    var icon: Path {
        var path = Path()
        func move(_ x: Double, _ y: Double) { path.move(to: CGPoint(x: x, y: y)) }
        func line(_ x: Double, _ y: Double) { path.addLine(to: CGPoint(x: x, y: y)) }
        func quad(_ cx: Double, _ cy: Double, _ x: Double, _ y: Double) {
            path.addQuadCurve(to: CGPoint(x: x, y: y), control: CGPoint(x: cx, y: cy))
        }
        switch self {
        case .message:
            // Speech bubble with its tail at the bottom left.
            move(-6, -8); line(6, -8); quad(9, -8, 9, -5); line(9, 2); quad(9, 5, 6, 5)
            line(-0.5, 5); line(-5, 9); line(-5, 5); line(-6, 5); quad(-9, 5, -9, 2)
            line(-9, -5); quad(-9, -8, -6, -8); path.closeSubpath()
        case .mail:
            // Envelope and its flap.
            move(-6.5, -6.5); line(6.5, -6.5); quad(9, -6.5, 9, -4); line(9, 4); quad(9, 6.5, 6.5, 6.5)
            line(-6.5, 6.5); quad(-9, 6.5, -9, 4); line(-9, -4); quad(-9, -6.5, -6.5, -6.5); path.closeSubpath()
            move(-8, -4.8); line(0, 1.2); line(8, -4.8)
        case .copy:
            // Two stacked sheets.
            move(-0.5, -3); line(6, -3); quad(8.5, -3, 8.5, -0.5); line(8.5, 6); quad(8.5, 8.5, 6, 8.5)
            line(-0.5, 8.5); quad(-3, 8.5, -3, 6); line(-3, -0.5); quad(-3, -3, -0.5, -3); path.closeSubpath()
            move(-6, 3); quad(-8.5, 3, -8.5, 0.5); line(-8.5, -6); quad(-8.5, -8.5, -6, -8.5)
            line(0.5, -8.5); quad(3, -8.5, 3, -6)
        case .more:
            // Arrow leaving a tray.
            move(-7, 0.5); line(-7, 6); quad(-7, 8.5, -4.5, 8.5); line(4.5, 8.5); quad(7, 8.5, 7, 6); line(7, 0.5)
            move(0, 3); line(0, -8.5)
            move(-4, -4.5); line(0, -8.5); line(4, -4.5)
        }
        return path
    }
}

/// Invisible button face that only reports its pressed state, so the shared tray can react.
private struct PressReporter: ButtonStyle {
    @Binding var pressed: Bool

    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .contentShape(Rectangle())
            .onChange(of: configuration.isPressed) { _, isPressed in pressed = isPressed }
    }
}

#Preview {
    ZStack {
        Color(hex: 0x080720).ignoresSafeArea()
        ShareRevealButton(autoPlay: true)
    }
}

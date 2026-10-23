import SwiftUI

/// Soft, inset switch: grey when off, green when on, with a bevelled thumb that stretches as it travels.
struct SquishToggle: View {
    /// Controlled value. Leave `nil` to let the switch keep its own state.
    var isOn: Binding<Bool>? = nil
    /// Accessibility label; the switch has no visible text.
    var label = "Toggle"
    /// Flip back and forth by itself with a pause between flips. Tapping still works.
    var autoPlay = false
    var onValueChange: (Bool) -> Void = { _ in }

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var inner = false
    @State private var pressed = false

    private var on: Bool { isOn?.wrappedValue ?? inner }

    var body: some View {
        Button(action: toggle) { Text(label) }
            .buttonStyle(Style(on: on, squish: !reduceMotion, pressed: $pressed))
            .accessibilityLabel(label)
            .accessibilityValue(on ? "On" : "Off")
            .accessibilityAddTraits(.isToggle)
            // Re-armed after every flip, so a tap simply restarts the pause.
            .task(id: AutoKey(on: on, active: autoPlay && !reduceMotion && !pressed)) {
                guard autoPlay, !reduceMotion, !pressed else { return }
                try? await Task.sleep(for: .seconds(Metrics.travelSeconds + Metrics.autoPauseSeconds))
                if !Task.isCancelled { toggle() }
            }
    }

    private func toggle() {
        let next = !on
        if let isOn { isOn.wrappedValue = next } else { inner = next }
        onValueChange(next)
    }

    private struct AutoKey: Equatable {
        let on: Bool
        let active: Bool
    }

    private struct Style: ButtonStyle {
        let on: Bool
        let squish: Bool
        @Binding var pressed: Bool

        func makeBody(configuration: Configuration) -> some View {
            let isPressed = configuration.isPressed
            Face(p: on ? 1 : 0, press: isPressed && squish ? 1 : 0, squish: squish ? 1 : 0)
                .animation(.timingCurve(0.23, 1, 0.32, 1, duration: isPressed ? 0.13 : 0.26), value: isPressed)
                .animation(
                    .timingCurve(0.65, 0, 0.35, 1, duration: squish ? Metrics.travelSeconds : 0.16), value: on
                )
                .frame(width: Metrics.width, height: Metrics.height)
                .contentShape(Capsule())
                .sensoryFeedback(.impact(weight: .light), trigger: isPressed) { _, pressed in pressed }
                .onChange(of: isPressed) { _, isPressed in pressed = isPressed }
        }
    }
}

private enum Metrics {
    static let width = 90.0
    static let height = 48.0
    static let thumb = 42.0
    static let pad = (height - thumb) / 2
    static let travel = width - pad * 2 - thumb
    /// The canvas overflows the switch by this much on every side to fit its soft shadow.
    static let bleed = 24.0
    static let travelSeconds = 0.42
    static let autoPauseSeconds = 1.5
    /// Mid-travel the thumb is this much wider (and a little flatter), like a soft pad being dragged.
    static let stretch = 0.2
    static let flatten = 0.07
    /// While a finger is down the thumb spreads this far towards the other side.
    static let pressSpread = 6.0
    /// The thumb's top face: a slightly smaller disc pushed up, leaving a bevel along the bottom.
    static let faceInset = 2.2
    static let faceLift = 1.5
}

/// `Animatable` so SwiftUI's own animations can drive the numbers the `Canvas` reads.
private struct Face: View, Animatable {
    /// 0 = off, 1 = on.
    var p: Double
    var press: Double
    /// 0 freezes the stretch for reduced motion.
    let squish: Double

    var animatableData: AnimatablePair<Double, Double> {
        get { AnimatablePair(p, press) }
        set {
            p = newValue.first
            press = newValue.second
        }
    }

    var body: some View {
        Canvas { context, size in
            draw(context, size: size)
        }
        .padding(-Metrics.bleed)
        .allowsHitTesting(false)
    }

    private func draw(_ context: GraphicsContext, size: CGSize) {
        let bleed = Metrics.bleed
        let rect = CGRect(x: bleed, y: bleed, width: size.width - bleed * 2, height: size.height - bleed * 2)
        let track = capsule(rect)

        // The switch sits in a shallow well: a soft shadow below, a light lip on the lower edge.
        var well = context
        well.addFilter(.blur(radius: 7))
        well.fill(capsule(rect.offsetBy(dx: 0, dy: 5)), with: .color(.rgba(148, 154, 180, 0.3)))
        var lip = context
        lip.addFilter(.blur(radius: 0.8))
        lip.fill(capsule(rect.offsetBy(dx: 0.5, dy: 1.2)), with: .color(.white.opacity(0.9)))

        context.fill(track, with: .color(Color(hex: 0xd3d7e1)))
        var green = context
        green.opacity = p
        green.fill(track, with: .color(Color(hex: 0x82b56e)))

        // Thumb box: travels with `p`, stretches around the middle of the trip and spreads away
        // from the wall it is resting against while pressed.
        let bell = sin(Double.pi * p)
        let tw = Metrics.thumb * (1 + squish * Metrics.stretch * bell) + Metrics.pressSpread * press
        let th = Metrics.thumb * (1 - squish * Metrics.flatten * bell)
        let cx = rect.minX + Metrics.pad + Metrics.thumb / 2 + Metrics.travel * p
            + Metrics.pressSpread * press * (0.5 - p)
        let thumb = CGRect(x: cx - tw / 2, y: rect.midY - th / 2, width: tw, height: th)

        var inside = context
        inside.clip(to: track)
        // Inner shadow: a blurred ring pushed down-right so only its top-left band falls inside.
        let ring = capsule(rect.insetBy(dx: -3, dy: -3).offsetBy(dx: 1.2, dy: 2.4))
        inside.drawLayer { layer in
            layer.addFilter(.blur(radius: 2.6))
            layer.stroke(ring, with: .color(.rgba(74, 78, 108, 0.5)), lineWidth: 6)
        }
        inside.drawLayer { layer in
            layer.opacity = p
            layer.addFilter(.blur(radius: 2.6))
            layer.stroke(ring, with: .color(.rgba(44, 86, 40, 0.6)), lineWidth: 6)
        }
        inside.drawLayer { layer in
            layer.addFilter(.blur(radius: 3.2))
            layer.fill(capsule(thumb.offsetBy(dx: 0, dy: 3)), with: .color(.rgba(28, 36, 48, 0.34)))
        }

        let top = CGPoint(x: 0, y: rect.minY + Metrics.pad)
        let bottom = CGPoint(x: 0, y: rect.maxY - Metrics.pad)
        context.fill(
            capsule(thumb),
            with: .linearGradient(
                Gradient(stops: [
                    .init(color: Color(hex: 0xf7f7f6), location: 0),
                    .init(color: Color(hex: 0xe6e6e6), location: 0.6),
                    .init(color: Color(hex: 0xcfcfd1), location: 1),
                ]),
                startPoint: top, endPoint: bottom
            )
        )
        let face = thumb.insetBy(dx: Metrics.faceInset, dy: Metrics.faceInset).offsetBy(dx: 0, dy: -Metrics.faceLift)
        context.fill(
            capsule(face),
            with: .linearGradient(
                Gradient(colors: [Color(hex: 0xf8f8f7), Color(hex: 0xececeb)]),
                startPoint: top, endPoint: bottom
            )
        )
    }
}

#Preview {
    ZStack {
        Color(hex: 0xeeeff4).ignoresSafeArea()
        SquishToggle(autoPlay: true)
    }
}

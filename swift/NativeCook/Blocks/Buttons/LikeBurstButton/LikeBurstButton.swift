import SwiftUI

/// Round white like button. Liking squashes it, swells a red disc behind the heart that opens into
/// a ring, pops the filled heart out of it and throws off a ring of coloured dots; unliking is a
/// quick dip back to the outline.
struct LikeBurstButton: View {
    /// Controlled state. Leave `nil` to let the button keep its own.
    var isOn: Binding<Bool>? = nil
    /// Accessibility label; the button has no visible text.
    var label = "Like"
    /// Likes and unlikes by itself with a pause in between.
    var autoPlay = false
    var onValueChange: (Bool) -> Void = { _ in }

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var inner = false
    @State private var pressed = false

    private var on: Bool { isOn?.wrappedValue ?? inner }

    var body: some View {
        Button(action: toggle) { Text(label) }
            .buttonStyle(Style(on: on, pressed: $pressed))
            .accessibilityLabel(label)
            .accessibilityValue(on ? "On" : "Off")
            .accessibilityAddTraits(.isToggle)
            // Re-armed after every change, so a tap simply restarts the pause.
            .task(id: AutoKey(on: on, active: autoPlay && !reduceMotion && !pressed)) {
                guard autoPlay, !reduceMotion, !pressed else { return }
                try? await Task.sleep(for: .seconds(on ? Metrics.autoLiked : Metrics.autoUnliked))
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
        @Binding var pressed: Bool

        func makeBody(configuration: Configuration) -> some View {
            let isPressed = configuration.isPressed
            Surface(on: on)
                .contentShape(Circle())
                .scaleEffect(isPressed ? 0.97 : 1)
                .animation(.timingCurve(0.23, 1, 0.32, 1, duration: 0.13), value: isPressed)
                .sensoryFeedback(.impact(weight: .light), trigger: isPressed) { _, pressed in pressed }
                .onChange(of: isPressed) { _, isPressed in pressed = isPressed }
        }
    }
}

private enum Metrics {
    static let size = 64.0
    static let r = size / 2
    /// The canvas overflows the button by this much on every side to fit the shadow and the burst.
    static let bleed = 28.0
    static let unlike = 0.26
    static let autoLiked = 2.2
    static let autoUnliked = 1.3
    /// The stroked heart is drawn a little smaller so its outer edge matches the filled one.
    static let outlineScale = 0.925
}

/// Liking is one clock in seconds; every part is a window on it.
private enum Clock {
    static let outlineShrink = (0.0, 0.09)
    static let outlineOut = (0.05, 0.09)
    static let squash = (0.0, 0.3)
    static let fillIn = (0.06, 0.1)
    static let disc = (0.07, 0.3)
    static let discSolid = (0.1, 0.22)
    static let hole = (0.22, 0.42)
    static let pop = (0.22, 0.55)
    static let tintOut = (0.22, 0.3)
    static let dotsFly = (0.36, 0.62)
    static let dotsDrift = (0.62, 0.95)
    static let dotsIn = (0.36, 0.42)
    static let dotsOut = (0.58, 0.92)
    static let total = 1.0
}

private let red = Color(hex: 0xf0301f)

/// The burst: seven dots around the rim, clockwise from the top, in degrees.
private let dots: [(angle: Double, color: Color)] = [
    (-90, Color(hex: 0xf2d94e)),
    (-35, Color(hex: 0xc8375a)),
    (0, Color(hex: 0x4466e8)),
    (45, Color(hex: 0xc63be0)),
    (135, Color(hex: 0x5a3de0)),
    (180, Color(hex: 0x9bcf63)),
    (-145, Color(hex: 0xd9763a)),
]

/// A heart about 27 wide, centred on the origin.
private let heart: Path = {
    var p = Path()
    p.move(to: CGPoint(x: 0, y: 11.5))
    p.addCurve(to: CGPoint(x: -13.3, y: -5), control1: CGPoint(x: -1.7, y: 10.1), control2: CGPoint(x: -13.3, y: 2.2))
    p.addCurve(to: CGPoint(x: -6.2, y: -12.6), control1: CGPoint(x: -13.3, y: -9.7), control2: CGPoint(x: -9.8, y: -12.6))
    p.addCurve(to: CGPoint(x: 0, y: -9.1), control1: CGPoint(x: -3.6, y: -12.6), control2: CGPoint(x: -1.3, y: -11.2))
    p.addCurve(to: CGPoint(x: 6.2, y: -12.6), control1: CGPoint(x: 1.3, y: -11.2), control2: CGPoint(x: 3.6, y: -12.6))
    p.addCurve(to: CGPoint(x: 13.3, y: -5), control1: CGPoint(x: 9.8, y: -12.6), control2: CGPoint(x: 13.3, y: -9.7))
    p.addCurve(to: CGPoint(x: 0, y: 11.5), control1: CGPoint(x: 13.3, y: 2.2), control2: CGPoint(x: 1.7, y: 10.1))
    p.closeSubpath()
    return p
}()

private func seg(_ t: Double, _ window: (Double, Double)) -> Double {
    min(1, max(0, (t - window.0) / (window.1 - window.0)))
}

private func easeOut(_ x: Double) -> Double { 1 - pow(1 - x, 3) }

private func easeOutBack(_ x: Double) -> Double {
    let c = 2.2
    return 1 + (c + 1) * pow(x - 1, 3) + c * pow(x - 1, 2)
}

private struct Surface: View {
    let on: Bool

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    // Resting unliked is burst 0 / fade 0; resting liked is burst `total` / fade 0. Unliking only
    // runs `fade` to 1, which looks exactly like burst 0 / fade 0, so the next like can rewind both unseen.
    @State private var burstStart: Date?
    @State private var fade = Tween(0)
    @State private var moving = false

    var body: some View {
        TimelineView(.animation(paused: !moving)) { timeline in
            let date = timeline.date
            let burst = burstStart.map { min(max(date.timeIntervalSince($0), 0), Clock.total) } ?? 0
            Canvas { context, _ in
                draw(context, burst: burst, fade: fade.value(at: date))
            }
            .padding(-Metrics.bleed)
            .frame(width: Metrics.size, height: Metrics.size)
        }
        .onAppear {
            if on { burstStart = .distantPast }
        }
        .onChange(of: on) { _, liked in
            if reduceMotion {
                // No burst: the heart simply cross-fades.
                burstStart = .distantPast
                fade.retarget(liked ? 0 : 1, duration: 0.16, curve: .linear)
            } else if liked {
                fade = Tween(0)
                burstStart = .now
            } else {
                fade.retarget(1, duration: Metrics.unlike, curve: .linear)
            }
        }
        // Run the timeline only while something moves.
        .task(id: on) {
            moving = true
            try? await Task.sleep(for: .seconds(Clock.total + 0.1))
            if !Task.isCancelled { moving = false }
        }
    }

    private func draw(_ context: GraphicsContext, burst t: Double, fade: Double) {
        let r = Metrics.r
        let c = CGPoint(x: Metrics.bleed + r, y: Metrics.bleed + r)
        func disc(_ radius: Double, at center: CGPoint = c) -> Path {
            Path(ellipseIn: CGRect(x: center.x - radius, y: center.y - radius, width: radius * 2, height: radius * 2))
        }

        // `fade` undoes the like: the filled heart dips and gives way to the outline again.
        let emptied = seg(fade, (0.2, 0.6))
        let dip = reduceMotion ? 1 : 1 - 0.22 * sin(.pi * fade)

        var shadow = context
        shadow.addFilter(.blur(radius: 9))
        shadow.fill(disc(r - 4, at: CGPoint(x: c.x, y: c.y + 9)), with: .color(.rgba(206, 120, 150, 0.42)))
        context.fill(disc(r * (1 - 0.06 * sin(.pi * seg(t, Clock.squash)))), with: .color(.white))

        // A red disc swells behind the heart, then hollows out from the centre into a thinning ring.
        if t > Clock.disc.0, t < Clock.hole.1 {
            let outer = r * (0.3 + 0.48 * easeOut(seg(t, Clock.disc)))
            let inner = outer * easeOut(seg(t, Clock.hole))
            context.stroke(
                disc((outer + inner) / 2),
                with: .color(red.opacity(0.55 + 0.45 * seg(t, Clock.discSolid))),
                lineWidth: outer - inner
            )
        }

        var hearts = context
        hearts.translateBy(x: c.x, y: c.y)
        let shrink = seg(t, Clock.outlineShrink)
        let outlineOpacity = max(1 - seg(t, Clock.outlineOut), emptied)
        if outlineOpacity > 0 {
            var outline = hearts
            let scale = Metrics.outlineScale * max(1 - shrink * shrink, 0.6 + 0.4 * emptied) * dip
            outline.scaleBy(x: scale, y: scale)
            outline.stroke(
                heart, with: .color(red.opacity(outlineOpacity)),
                style: StrokeStyle(lineWidth: 2, lineJoin: .round)
            )
        }
        let filledOpacity = seg(t, Clock.fillIn) * (1 - emptied)
        if filledOpacity > 0 {
            var filled = hearts
            let scale = (0.42 + 0.58 * easeOutBack(seg(t, Clock.pop))) * dip
            filled.scaleBy(x: scale, y: scale)
            filled.fill(heart, with: .color(red.opacity(filledOpacity)))
            // On the solid disc the heart reads as a lighter red.
            let tint = seg(t, Clock.discSolid) * (1 - seg(t, Clock.tintOut))
            filled.fill(heart, with: .color(Color(hex: 0xff8a7d, opacity: tint)))
        }

        let out = seg(t, Clock.dotsOut)
        let dotRadius = 2.7 * easeOut(seg(t, Clock.dotsIn)) * (1 - out * out)
        if dotRadius > 0 {
            let reach = r * (0.9 + 0.42 * easeOut(seg(t, Clock.dotsFly)) + 0.1 * seg(t, Clock.dotsDrift))
            for dot in dots {
                let a = dot.angle * .pi / 180
                context.fill(
                    disc(dotRadius, at: CGPoint(x: c.x + cos(a) * reach, y: c.y + sin(a) * reach)),
                    with: .color(dot.color)
                )
            }
        }
    }
}

#Preview {
    ZStack {
        Color(hex: 0xfeeeef).ignoresSafeArea()
        LikeBurstButton(autoPlay: true)
    }
}

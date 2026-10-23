import SwiftUI

/// Switch that slides from a night scene (crescent moon, stars, blue ridges) to a day scene (sun,
/// golden dunes). The knob hides the moon by day and the sun by night.
struct DayNightToggle: View {
    /// Controlled value; `true` is day (knob on the right). Leave `nil` to let the toggle keep its own.
    var value: Binding<Bool>? = nil
    /// Flips by itself every few seconds.
    var autoPlay = false
    /// Also paints the area around the toggle: pale blue at night, cream by day.
    var backdrop = false
    var accessibilityLabel = "Day mode"
    var onValueChange: (Bool) -> Void = { _ in }

    @State private var inner = false

    private var isOn: Bool { value?.wrappedValue ?? inner }

    var body: some View {
        Button {
            set(!isOn)
        } label: {
            Text(accessibilityLabel)
        }
        .buttonStyle(Style(isOn: isOn, backdrop: backdrop))
        .accessibilityLabel(accessibilityLabel)
        .accessibilityValue(isOn ? "On" : "Off")
        .accessibilityAddTraits(.isToggle)
        // Restarts on every change, so a tap never gets flipped straight back.
        .task(id: [autoPlay, isOn]) {
            guard autoPlay else { return }
            try? await Task.sleep(for: .seconds(Metrics.autoPlaySeconds))
            if !Task.isCancelled { set(!isOn) }
        }
    }

    private func set(_ next: Bool) {
        if let value { value.wrappedValue = next } else { inner = next }
        onValueChange(next)
    }

    private struct Style: ButtonStyle {
        let isOn: Bool
        let backdrop: Bool

        func makeBody(configuration: Configuration) -> some View {
            Surface(isOn: isOn, backdrop: backdrop, isPressed: configuration.isPressed)
        }
    }
}

private enum Metrics {
    static let width = 116.0
    static let height = 54.0
    static let knob = 44.0
    static let inset = 5.0
    static let travel = width - knob - inset * 2
    /// The canvas overflows the toggle by this much on every side to fit its shadow.
    static let bleed = 24.0
    static let autoPlaySeconds = 2.6
    static let slideSeconds = 0.42
    static let fadeSeconds = 0.18
}

/// A colour kept as components so two of them can be blended inside the canvas.
private struct Tint {
    let r: Double, g: Double, b: Double
    var a = 1.0

    init(_ hex: UInt32) {
        r = Double((hex >> 16) & 0xff)
        g = Double((hex >> 8) & 0xff)
        b = Double(hex & 0xff)
    }

    init(_ r: Double, _ g: Double, _ b: Double, _ a: Double) {
        self.r = r
        self.g = g
        self.b = b
        self.a = a
    }

    func mixed(with other: Tint, _ t: Double) -> Color {
        .rgba(r + (other.r - r) * t, g + (other.g - g) * t, b + (other.b - b) * t, a + (other.a - a) * t)
    }
}

private struct Palette {
    let tile: Tint, track: Tint, back: Tint, mid: Tint, front: Tint, shadow: Tint
}

private let night = Palette(
    tile: Tint(0xe0edf6), track: Tint(0x102a38), back: Tint(0x5d7b8f), mid: Tint(0x3c5c75),
    front: Tint(0x0d1e2b), shadow: Tint(16, 42, 56, 0.4)
)
private let day = Palette(
    tile: Tint(0xf5f1e2), track: Tint(0xf8e17b), back: Tint(0xf3c552), mid: Tint(0xefb93d),
    front: Tint(0xe29c1e), shadow: Tint(206, 150, 34, 0.42)
)

private func disc(_ x: Double, _ y: Double, _ r: Double) -> Path {
    Path(ellipseIn: CGRect(x: x - r, y: y - r, width: r * 2, height: r * 2))
}

private func polygon(_ points: [(Double, Double)]) -> Path {
    var path = Path()
    path.addLines(points.map { CGPoint(x: $0.0, y: $0.1) })
    path.closeSubpath()
    return path
}

// Everything below is in the toggle's own 116 × 54 box.
private let stars: Path = {
    var path = Path()
    for (x, y) in [
        (54.9, 4.4), (73.9, 5.9), (88.7, 8.6), (61.1, 13.8), (77.9, 15), (88.7, 18.3), (69.4, 25.1), (91.2, 37.8),
    ] {
        path.addPath(disc(x, y, 0.75))
    }
    return path
}()

// The crescent is a disc with a second, offset disc cut out of it.
private let moon = disc(90.6, 18.6, 12.2)
private let moonCut = disc(87.2, 15.6, 11.9)
private let sunCenter = CGPoint(x: 24.3, y: 20.7)
private let sunRadius = 10.0
private let sunGlowRadius = 16.5
private let sunGlow = Gradient(stops: [
    .init(color: .rgba(255, 255, 255, 0.95), location: 0),
    .init(color: .rgba(255, 252, 226, 0.9), location: 0.55),
    .init(color: .rgba(255, 246, 190, 0), location: 1),
])

/// A ridge line and how far it drifts as the knob crosses.
private struct Ridge {
    let path: Path
    let drift: CGSize
    let night: Tint
    let day: Tint
}

// Far to near. They run past the bottom edge so they can slide without a gap; the middle one rises
// into the day's big dune.
private let ridges = [
    Ridge(
        path: polygon([
            (50, 72), (50, 45), (60, 41), (69, 34), (76, 37.5), (86, 41), (96, 45), (106, 42), (132, 46), (132, 72),
        ]),
        drift: CGSize(width: -2, height: 0), night: night.back, day: day.back
    ),
    Ridge(
        path: polygon([
            (18, 72), (34, 45), (47, 37), (51.5, 34.5), (60.5, 41), (69, 46), (79, 48.5), (90, 53), (100, 72),
        ]),
        drift: CGSize(width: -7, height: -5), night: night.mid, day: day.mid
    ),
    Ridge(
        path: polygon([
            (-4, 72), (-4, 48), (10, 45), (22, 49.5), (34, 52), (47, 47.5), (57, 52.5), (70, 51), (84, 54),
            (96, 48), (105, 51.5), (122, 46), (122, 72),
        ]),
        drift: CGSize(width: -3, height: 0), night: night.front, day: day.front
    ),
]

private struct Surface: View {
    let isOn: Bool
    let backdrop: Bool
    let isPressed: Bool

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    // The knob slides while the scenery cross-fades a little faster underneath it.
    @State private var knob: Tween
    @State private var scene: Tween
    @State private var settling = false
    @State private var settleToken = 0

    init(isOn: Bool, backdrop: Bool, isPressed: Bool) {
        self.isOn = isOn
        self.backdrop = backdrop
        self.isPressed = isPressed
        _knob = State(initialValue: Tween(isOn ? 1 : 0))
        _scene = State(initialValue: Tween(isOn ? 1 : 0))
    }

    var body: some View {
        TimelineView(.animation(paused: !settling)) { timeline in
            let k = knob.value(at: timeline.date)
            let s = scene.value(at: timeline.date)

            Color.clear
                .frame(width: Metrics.width, height: Metrics.height)
                .background {
                    Canvas { context, _ in
                        draw(context, knob: k, scene: s)
                    }
                    .padding(-Metrics.bleed)
                    .allowsHitTesting(false)
                }
                .scaleEffect(isPressed ? 0.97 : 1)
                .animation(.timingCurve(0.23, 1, 0.32, 1, duration: 0.13), value: isPressed)
                .background {
                    if backdrop {
                        // Far larger than any tile; the container it sits in is expected to clip it.
                        night.tile.mixed(with: day.tile, s)
                            .frame(width: 4000, height: 4000)
                            .allowsHitTesting(false)
                    }
                }
        }
        .contentShape(Capsule())
        .sensoryFeedback(.impact(weight: .light), trigger: isPressed) { _, pressed in pressed }
        .onChange(of: isOn) { _, isOn in
            let target = isOn ? 1.0 : 0.0
            // Reduced motion keeps the cross-fade and drops the slide.
            knob.retarget(target, duration: reduceMotion ? 0 : Metrics.slideSeconds, curve: Ease.out)
            scene.retarget(target, duration: Metrics.fadeSeconds, curve: .easeInOut)
            settling = true
            settleToken += 1
        }
        .task(id: settleToken) {
            guard settleToken > 0 else { return }
            try? await Task.sleep(for: .seconds(Metrics.slideSeconds + 0.05))
            if !Task.isCancelled { settling = false }
        }
    }

    private func draw(_ context: GraphicsContext, knob: Double, scene: Double) {
        var context = context
        context.translateBy(x: Metrics.bleed, y: Metrics.bleed)
        let w = Metrics.width
        let h = Metrics.height
        let track = capsule(CGRect(x: 0, y: 0, width: w, height: h))

        var shadow = context
        shadow.addFilter(.blur(radius: 8))
        shadow.fill(
            capsule(CGRect(x: 6, y: 9, width: w - 12, height: h - 4)),
            with: .color(night.shadow.mixed(with: day.shadow, scene))
        )

        var inside = context
        inside.clip(to: track)
        inside.fill(track, with: .color(night.track.mixed(with: day.track, scene)))

        var sky = inside
        sky.opacity = 1 - scene
        sky.fill(stars, with: .color(.white))
        sky.clip(to: moonCut, options: .inverse)
        sky.fill(moon, with: .color(Color(hex: 0xf6f6f6)))

        // The sun sits where the knob rests at night, so the knob uncovers it as it leaves.
        var sun = inside.scaled(0.6 + 0.4 * scene, about: sunCenter)
        sun.opacity = scene
        sun.fillEllipse(center: sunCenter, radii: CGSize(width: sunGlowRadius, height: sunGlowRadius), gradient: sunGlow)
        sun.fill(disc(sunCenter.x, sunCenter.y, sunRadius), with: .color(Color(hex: 0xfdfdf6)))

        for ridge in ridges {
            var layer = inside
            layer.translateBy(x: ridge.drift.width * knob, y: ridge.drift.height * knob)
            layer.fill(ridge.path, with: .color(ridge.night.mixed(with: ridge.day, scene)))
        }

        let knobX = Metrics.inset + Metrics.knob / 2 + Metrics.travel * knob
        var knobShadow = inside
        knobShadow.addFilter(.blur(radius: 3))
        knobShadow.fill(disc(knobX, h / 2 + 1.5, Metrics.knob / 2), with: .color(.black.opacity(0.24)))
        inside.fill(disc(knobX, h / 2, Metrics.knob / 2), with: .color(Color(hex: 0xf6f6f6)))
    }
}

#Preview {
    ZStack {
        Color(hex: 0xe0edf6).ignoresSafeArea()
        DayNightToggle(autoPlay: true, backdrop: true)
    }
}

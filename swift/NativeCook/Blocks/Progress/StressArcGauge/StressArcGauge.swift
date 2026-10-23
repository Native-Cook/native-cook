import SwiftUI

/// Mental-health widget: a dark card crossed by a wide glowing arc with a marker on it, the stress
/// level as a large percentage and a chip naming the state. The marker drags a comet tail while it
/// moves, beats like a heart (faster as stress rises, a ring going out with each beat), and the
/// whole card shifts from calm green to stressed red as the level climbs.
struct StressArcGauge: View {
    /// Controlled stress level, 0…1. Leave `nil` to show the reference's 85% (or the autoPlay loop).
    var progress: Double? = nil
    /// Climbs to 50% ("Balanced", green), then to 85% ("High", red), holds each, and starts over.
    var autoPlay = false
    var title = "Stress"
    var caption = "Your stress level"
    /// State names for the chip: below 35%, up to 70%, and above.
    var levels = ["Low", "Balanced", "High"]
    var onOpen: () -> Void = {}

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    /// When the current autoPlay / tapped run started; `nil` shows the resting frame.
    @State private var runStart: Date? = nil
    @State private var held = Tween(Metrics.rest)
    /// Keeps the timeline running for a controlled change under reduced motion.
    @State private var settling = false
    @State private var pulse = Pulse()

    /// Per-frame state that depends on how the value has been moving: the heartbeat's phase
    /// (its rate follows the level) and the comet tail (it follows the marker's speed).
    private final class Pulse {
        var date: Date? = nil
        var before = Metrics.rest
        var beat = 0.6
        var tail = 0.0

        func advance(to now: Date, value: Double) {
            let dt = min(max(now.timeIntervalSince(date ?? now), 0), 0.064)
            date = now
            guard dt > 0 else { return }
            // The heart beats 54 times a minute when calm and up to 114 when stressed.
            beat = (beat + dt * (0.9 + value)).truncatingRemainder(dividingBy: 1)
            let speed = max(-1, min(1, (value - before) / dt / 0.22))
            tail += (speed - tail) * min(1, dt * 9)
            before = value
        }
    }

    private var controlled: Bool { progress != nil }
    private var target: Double { min(1, max(0, progress ?? Metrics.rest)) }

    var body: some View {
        TimelineView(.animation(paused: reduceMotion && !settling)) { timeline in
            let date = timeline.date
            let value = controlled ? held.value(at: date) : loopValue(clock(at: date))
            let percent = Int((value * 100).rounded())
            let level = percent < 35 ? levels[0] : percent < 70 ? levels[1] : levels[2]
            let ink = color(blend(inkStops, value))
            let _ = reduceMotion ? () : pulse.advance(to: date, value: value)

            ZStack {
                Canvas { context, size in
                    draw(context, value: value, beat: pulse.beat, tail: reduceMotion ? 0 : pulse.tail)
                }
                .padding(-Metrics.bleed)
                .allowsHitTesting(false)

                ZStack {
                    Text(title)
                        .font(.system(size: 14.5, weight: .semibold))
                        .foregroundStyle(.white)
                        .fixedSize()
                        .frame(width: 200, alignment: .leading)
                        .position(x: 45.8 + 100, y: 23)
                    // The reference centres its text a touch left of the card's middle.
                    Text(caption)
                        .font(.system(size: 9.5))
                        .foregroundStyle(Color(hex: 0xf2f2f2))
                        .position(x: 168.5, y: 97.4)
                    Text("\(percent)%")
                        .font(.system(size: 28.5))
                        .monospacedDigit()
                        .foregroundStyle(ink)
                        .position(x: 168.5, y: 127.8)
                    Text(level)
                        .font(.system(size: 10.5, weight: .semibold))
                        .foregroundStyle(ink)
                        .padding(.horizontal, 14)
                        .frame(height: 20.2)
                        .background(Color.white.opacity(0.2), in: Capsule())
                        .overlay(Capsule().strokeBorder(Color.white.opacity(0.22), lineWidth: 1))
                        .position(x: 168.5, y: 160.3)
                }
                .lineLimit(1)
                .frame(width: Metrics.width, height: Metrics.height)
                .accessibilityElement(children: .ignore)
                .accessibilityLabel("\(title). \(caption)")
                .accessibilityValue("\(percent)%, \(level)")

                Button(action: onOpen) {
                    Path { path in
                        path.move(to: CGPoint(x: 1.5, y: 9))
                        path.addLine(to: CGPoint(x: 9, y: 1.5))
                        path.move(to: CGPoint(x: 3.2, y: 1.5))
                        path.addLine(to: CGPoint(x: 9, y: 1.5))
                        path.addLine(to: CGPoint(x: 9, y: 7.3))
                    }
                    .stroke(.white, style: StrokeStyle(lineWidth: 1.4, lineCap: .round, lineJoin: .round))
                    .frame(width: 10.5, height: 10.5)
                }
                .buttonStyle(OpenStyle())
                .accessibilityLabel("Open")
                .position(x: 319.45, y: 22.95)
            }
            .frame(width: Metrics.width, height: Metrics.height)
        }
        .contentShape(RoundedRectangle(cornerRadius: Metrics.radius))
        .onTapGesture { restart() }
        .onAppear { sync() }
        .onChange(of: autoPlay) { sync() }
        .onChange(of: reduceMotion) { sync() }
        .onChange(of: progress) {
            sync()
            guard controlled else { return }
            held.retarget(target, duration: reduceMotion ? 0.2 : 0.9, curve: Ease.out)
            settling = true
        }
        .task(id: settling) {
            guard settling else { return }
            try? await Task.sleep(for: .seconds(1))
            settling = false
        }
    }

    private func sync() {
        runStart = autoPlay && !controlled && !reduceMotion ? .now : nil
        if controlled, held.duration == 0 { held = Tween(target) }
    }

    private func restart() {
        guard !controlled, !reduceMotion else { return }
        runStart = .now
    }

    /// Position in the autoPlay loop, in seconds.
    private func clock(at date: Date) -> Double {
        guard let runStart else { return Metrics.restTime }
        let elapsed = date.timeIntervalSince(runStart)
        // A tapped, one-off run stops at the resting frame.
        return autoPlay ? elapsed.truncatingRemainder(dividingBy: Metrics.loop) : min(elapsed, Metrics.restTime)
    }

    private func draw(_ context: GraphicsContext, value: Double, beat: Double, tail: Double) {
        let x = Metrics.bleed
        let y = Metrics.bleed
        let ambient = reduceMotion ? 0.0 : 1.0
        let card = CGRect(x: x, y: y, width: Metrics.width, height: Metrics.height)
        let shape = Path(roundedRect: card, cornerRadius: Metrics.radius, style: .circular)
        let throb = ambient * heartbeat(beat)
        let line = color(blend(lineStops, value))
        let at = markerAt(value)
        let angle = (Metrics.zero + (Metrics.full - Metrics.zero) * clamp01(value)) * .pi / 180
        let marker = CGPoint(
            x: x + Metrics.arcX + Metrics.arcRadius * sin(angle),
            y: y + Metrics.arcY - Metrics.arcRadius * cos(angle)
        )

        var edge = context
        edge.addFilter(.blur(radius: 4))
        edge.fill(
            Path(
                roundedRect: CGRect(x: x + 6, y: y + 8, width: card.width - 12, height: card.height - 4),
                cornerRadius: Metrics.radius, style: .circular
            ),
            with: .color(color(blend(edgeStops, value), opacity: 0.36))
        )

        var inside = context
        inside.clip(to: shape)
        inside.fill(
            shape,
            with: .linearGradient(
                Gradient(stops: [
                    .init(color: Color(hex: 0x222222), location: 0),
                    .init(color: Color(hex: 0x232323), location: 0.2),
                    .init(color: Color(hex: 0x3a3a3a), location: 0.47),
                    .init(color: Color(hex: 0x303030), location: 0.64),
                    .init(color: Color(hex: 0x373737), location: 1),
                ]),
                startPoint: CGPoint(x: x, y: y),
                endPoint: CGPoint(x: x + card.width, y: y + 30)
            )
        )
        // Ambient light on the right of the card: it leans towards the marker and throbs with the heartbeat.
        let light = blend(lightStops, value)
        let strength = 0.5 + 0.14 * throb
        inside.fillEllipse(
            center: CGPoint(x: x + 259 + 0.25 * (marker.x - x - 259), y: y + 82),
            radii: CGSize(width: 125, height: 125),
            gradient: Gradient(stops: [
                .init(color: color(light, opacity: strength), location: 0),
                .init(color: color(light, opacity: strength * 0.45), location: 0.5),
                .init(color: color(light, opacity: 0), location: 1),
            ])
        )

        var arcs = inside
        arcs.translateBy(x: x, y: y)
        // What is still ahead of the marker is faint, what is behind glows.
        arcs.stroke(
            arc.trimmedPath(from: at, to: 1), with: .color(line.opacity(0.34)),
            style: StrokeStyle(lineWidth: 1.6, lineCap: .round)
        )
        let behind = arc.trimmedPath(from: 0, to: at)
        var glow = arcs
        glow.addFilter(.blur(radius: 4))
        glow.stroke(behind, with: .color(line.opacity(0.5)), style: StrokeStyle(lineWidth: 5, lineCap: .round))
        arcs.stroke(behind, with: .color(line), style: StrokeStyle(lineWidth: 1.6, lineCap: .round))
        // While the marker moves it drags a bright tail along the stroke, like a comet.
        let tailAlpha = min(1, abs(tail) * 1.4)
        if tailAlpha > 0.01 {
            let other = at - Metrics.tail * tail
            let comet = arc.trimmedPath(from: clamp01(min(at, other)), to: clamp01(max(at, other)))
            var soft = arcs
            soft.addFilter(.blur(radius: 3))
            soft.stroke(comet, with: .color(line.opacity(tailAlpha)), style: StrokeStyle(lineWidth: 5, lineCap: .round))
            var core = arcs
            core.addFilter(.blur(radius: 1.2))
            core.stroke(comet, with: .color(.white.opacity(tailAlpha)), style: StrokeStyle(lineWidth: 2.4, lineCap: .round))
        }

        // Each beat sends a ring out from the marker.
        let radius = Metrics.dotRadius
        if ambient > 0, beat >= 0.1 {
            let spread = clamp01((beat - 0.1) / 0.75)
            inside.stroke(
                circle(marker, radius + 17 * spread), with: .color(line.opacity(0.5 * (1 - spread))), lineWidth: 1.2
            )
        }
        var halo = inside
        halo.addFilter(.blur(radius: 5))
        halo.fill(circle(marker, radius + 3), with: .color(line.opacity(0.45)))
        inside.fill(circle(marker, radius * (1 + 0.16 * throb)), with: .color(line))

        // Person holding their head; the lines above it twitch with the heartbeat when stress is high.
        var icon = context
        icon.translateBy(x: x + 18.8, y: y + 14.4)
        icon.stroke(iconArms, with: .color(.white), style: StrokeStyle(lineWidth: 2.7, lineCap: .round, lineJoin: .round))
        icon.fill(iconBody, with: .color(.white))
        icon.fill(circle(CGPoint(x: 9, y: 6.8), 3.9), with: .color(Color(hex: 0x262626)))
        icon.fill(circle(CGPoint(x: 9, y: 6.8), 3), with: .color(.white))
        icon.stroke(
            iconRays,
            with: .color(.white.opacity(1 - 0.55 * throb * clamp01((value - 0.56) / 0.16))),
            style: StrokeStyle(lineWidth: 0.9, lineCap: .round)
        )
    }
}

private enum Metrics {
    static let width = 340.0
    static let height = 198.0
    static let radius = 26.5
    /// The canvas overflows the card by this much on every side to fit its coloured edge glow.
    static let bleed = 24.0

    // The arc is part of a large circle whose centre lies below the card.
    static let arcX = 165.1
    static let arcY = 273.1
    static let arcRadius = 213.6
    // Degrees from straight up: where the stroke enters and leaves the card, and the 0% / 100% marks on it.
    static let arcFrom = -52.0
    static let arcTo = 56.5
    static let zero = -37.4
    static let full = 42.8
    static let dotRadius = 5.3
    static let tail = 0.2

    // The reference shows 85% (red) and 50% (green); the autoPlay loop visits both (seconds).
    static let rest = 0.85
    static let calm = 0.5
    static let base = 0.2
    static let rise = 1.6
    static let t1 = 0.3
    static let t2 = t1 + rise + 2.3
    static let t3 = t2 + rise + 2.9
    static let fall = 0.9
    static let loop = t3 + fall
    /// A moment in the loop where everything is at rest at 85%: used when nothing is playing.
    static let restTime = t3 - 1
}

// Calm, in-between and stressed colours for the stroke, the text and the ambient light.
private let lineStops: [[Double]] = [[47, 255, 71], [255, 214, 60], [255, 116, 47]]
private let inkStops: [[Double]] = [[99, 226, 131], [246, 214, 110], [241, 128, 111]]
private let lightStops: [[Double]] = [[38, 150, 36], [150, 120, 30], [190, 52, 30]]
private let edgeStops: [[Double]] = [[90, 255, 120], [255, 214, 120], [255, 130, 110]]

private func clamp01(_ t: Double) -> Double { min(1, max(0, t)) }

private func easeInOutCubic(_ t: Double) -> Double {
    t < 0.5 ? 4 * t * t * t : 1 - pow(-2 * t + 2, 3) / 2
}

/// Stress level at `t` seconds into the autoPlay loop.
private func loopValue(_ t: Double) -> Double {
    if t < Metrics.t1 { return Metrics.base }
    if t < Metrics.t1 + Metrics.rise {
        return Metrics.base + (Metrics.calm - Metrics.base) * easeInOutCubic((t - Metrics.t1) / Metrics.rise)
    }
    if t < Metrics.t2 { return Metrics.calm }
    if t < Metrics.t2 + Metrics.rise {
        return Metrics.calm + (Metrics.rest - Metrics.calm) * easeInOutCubic((t - Metrics.t2) / Metrics.rise)
    }
    if t < Metrics.t3 { return Metrics.rest }
    return Metrics.rest - (Metrics.rest - Metrics.base) * easeInOutCubic((t - Metrics.t3) / Metrics.fall)
}

/// Fraction of the stroke (0…1 from where it enters the card) at which the marker sits.
private func markerAt(_ p: Double) -> Double {
    (Metrics.zero + (Metrics.full - Metrics.zero) * clamp01(p) - Metrics.arcFrom) / (Metrics.arcTo - Metrics.arcFrom)
}

/// Calm → in-between → stressed, crossing over between 56% and 72%.
private func blend(_ stops: [[Double]], _ p: Double) -> [Double] {
    let t = clamp01((p - 0.56) / 0.16) * 2
    let a = t < 1 ? stops[0] : stops[1]
    let b = t < 1 ? stops[1] : stops[2]
    let f = t < 1 ? t : t - 1
    return [a[0] + (b[0] - a[0]) * f, a[1] + (b[1] - a[1]) * f, a[2] + (b[2] - a[2]) * f]
}

private func color(_ rgb: [Double], opacity: Double = 1) -> Color {
    .rgba(rgb[0], rgb[1], rgb[2], opacity)
}

/// Heartbeat for a phase 0…1: a strong thump followed by a softer one.
private func heartbeat(_ phase: Double) -> Double {
    let a = (phase - 0.1) / 0.055
    let b = (phase - 0.32) / 0.055
    return exp(-a * a) + 0.55 * exp(-b * b)
}

private func circle(_ center: CGPoint, _ r: Double) -> Path {
    Path(ellipseIn: CGRect(x: center.x - r, y: center.y - r, width: r * 2, height: r * 2))
}

private let arc: Path = {
    var path = Path()
    path.addArc(
        center: CGPoint(x: Metrics.arcX, y: Metrics.arcY), radius: Metrics.arcRadius,
        startAngle: .degrees(Metrics.arcFrom - 90), endAngle: .degrees(Metrics.arcTo - 90), clockwise: false
    )
    return path
}()

// Person holding their head, in an 18 × 18 box.
private let iconArms: Path = {
    var p = Path()
    p.move(to: CGPoint(x: 5.6, y: 11.7))
    p.addCurve(to: CGPoint(x: 4.4, y: 4.7), control1: CGPoint(x: 2.2, y: 10.7), control2: CGPoint(x: 1.6, y: 6.7))
    p.addCurve(to: CGPoint(x: 6.6, y: 4.5), control1: CGPoint(x: 5.2, y: 4.1), control2: CGPoint(x: 6, y: 4.1))
    p.move(to: CGPoint(x: 12.4, y: 11.7))
    p.addCurve(to: CGPoint(x: 13.6, y: 4.7), control1: CGPoint(x: 15.8, y: 10.7), control2: CGPoint(x: 16.4, y: 6.7))
    p.addCurve(to: CGPoint(x: 11.4, y: 4.5), control1: CGPoint(x: 12.8, y: 4.1), control2: CGPoint(x: 12, y: 4.1))
    return p
}()

private let iconBody: Path = {
    var p = Path()
    p.move(to: CGPoint(x: 5, y: 17.6))
    p.addLine(to: CGPoint(x: 5, y: 12.5))
    p.addCurve(to: CGPoint(x: 7, y: 10.6), control1: CGPoint(x: 5, y: 11.4), control2: CGPoint(x: 5.9, y: 10.6))
    p.addLine(to: CGPoint(x: 11, y: 10.6))
    p.addCurve(to: CGPoint(x: 13, y: 12.5), control1: CGPoint(x: 12.1, y: 10.6), control2: CGPoint(x: 13, y: 11.4))
    p.addLine(to: CGPoint(x: 13, y: 17.6))
    p.closeSubpath()
    return p
}()

private let iconRays: Path = {
    var p = Path()
    p.move(to: CGPoint(x: 9, y: 0.2))
    p.addLine(to: CGPoint(x: 9, y: 1.8))
    p.move(to: CGPoint(x: 6.2, y: 0.9))
    p.addLine(to: CGPoint(x: 6.9, y: 2.2))
    p.move(to: CGPoint(x: 11.8, y: 0.9))
    p.addLine(to: CGPoint(x: 11.1, y: 2.2))
    return p
}()

/// Bare glyph that scales down while pressed.
private struct OpenStyle: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .contentShape(Rectangle().inset(by: -14))
            .scaleEffect(configuration.isPressed ? 0.97 : 1)
            .animation(.timingCurve(0.23, 1, 0.32, 1, duration: 0.13), value: configuration.isPressed)
            .sensoryFeedback(.impact(weight: .light), trigger: configuration.isPressed) { _, pressed in pressed }
    }
}

#Preview {
    ZStack {
        Color.white.ignoresSafeArea()
        StressArcGauge(autoPlay: true)
    }
}

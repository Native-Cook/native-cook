import SwiftUI

/// Dark flight card: both airports with an arrow between them, an ETA box, and a glossy pill bar a
/// small plane rides. The plane noses up and leaves a slipstream while the bar grows, a band of
/// light crosses the bar when it levels off, the label counts the time left down, the dust on the
/// card twinkles and a spark runs down the arrow.
struct FlightProgress: View {
    struct Stop {
        var code: String
        var city: String
        var time: String
    }

    enum Tint {
        case lime, orange
    }

    /// Controlled share of the flight flown, 0…1.
    var progress: Double? = nil
    /// Flies from the gate to the point shown in the reference, holds, and starts over.
    var autoPlay = false
    /// Colour of the bar, the arrow and the dust; the note under the ETA takes the other colour.
    var tint = Tint.lime
    var origin = Stop(code: "NPT", city: "Northport", time: "MON, 6:14 PM")
    var destination = Stop(code: "BVW", city: "Bayview", time: "MON, 7:14 AM")
    var eta = "ETA 2:15 PM"
    var zone = "Bayview Time"
    var note = "DINNER IN 2:34H"
    /// Length of the flight: the bar's label counts the minutes left down from it.
    var totalMinutes = 915
    var onSwapPress: (() -> Void)? = nil

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    /// Origin of the ambient clock (twinkle, breathing, spark).
    @State private var epoch = Date.now
    /// When the current autoPlay / tapped run started; `nil` shows the resting frame.
    @State private var runStart: Date? = nil
    @State private var held = Tween(Metrics.rest)
    /// Keeps the timeline running for a controlled change under reduced motion.
    @State private var settling = false
    @State private var flight = Flight()

    /// Per-frame state that depends on how the value has been moving.
    private final class Flight {
        var date: Date? = nil
        var before = Metrics.rest
        /// 0…1, smoothed: is the plane flying forwards.
        var moving = 0.0
        var airborne = false
        /// When the plane last levelled off.
        var arrivedAt = Date.distantPast

        func advance(to now: Date, value: Double) {
            let dt = min(max(now.timeIntervalSince(date ?? now), 0), 0.064)
            date = now
            let step = value - before
            before = value
            guard dt > 0 else { return }
            let flying = step > 0.00004
            moving += ((flying ? 1 : 0) - moving) * min(1, dt * 9)
            // Levelling off after a climb sets off the sweep of light.
            if flying, moving > 0.5 {
                airborne = true
            } else if !flying, airborne, step >= 0 {
                airborne = false
                arrivedAt = now
            } else if step < 0 {
                airborne = false
            }
        }
    }

    private var controlled: Bool { progress != nil }
    private var target: Double { min(1, max(0, progress ?? Metrics.rest)) }
    private var palette: Palette { tint == .lime ? .lime : .orange }

    var body: some View {
        TimelineView(.animation(paused: reduceMotion && !settling)) { timeline in
            let date = timeline.date
            let time = reduceMotion ? 0 : date.timeIntervalSince(epoch)
            let value = controlled ? held.value(at: date) : loopValue(clock(at: date))
            let _ = flight.advance(to: date, value: value)
            let left = Int((Double(totalMinutes) * (1 - clamp01(value))).rounded())
            let remaining = "-\(left / 60)H \(String(format: "%02d", left % 60))M"

            ZStack {
                Canvas { context, _ in
                    draw(
                        context, value: value, time: time, moving: reduceMotion ? 0 : flight.moving,
                        sinceArrival: reduceMotion ? 10 : date.timeIntervalSince(flight.arrivedAt)
                    )
                }
                .padding(-Metrics.bleed)
                .allowsHitTesting(false)

                ForEach(0..<2, id: \.self) { index in
                    let stop = index == 0 ? origin : destination
                    let left = index == 0 ? 15.3 : 134.5
                    label(stop.code, size: 24.5, weight: .black, color: .white, x: left - 1, y: 31, width: 96)
                        .tracking(0.6)
                    label(stop.city, size: 11.2, weight: .medium, color: Color(hex: 0xf1f1f1), x: left, y: 52.9, width: 96)
                    label(stop.time, size: 10.8, weight: .medium, color: Color(hex: 0x6e6e6e), x: left, y: 67.4, width: 96)
                }

                label(eta, size: 12.4, weight: .medium, color: .white, x: 233, y: 37.8, width: 80)
                label(zone, size: 11, weight: .medium, color: Color(hex: 0x9a9a9a), x: 233, y: 58.3, width: 104)
                label(note, size: 10.6, weight: .medium, color: palette.note, x: 233, y: 75.4, width: 106)
                    .shadow(color: palette.note.opacity(0.9), radius: 3.5)

                Text(remaining)
                    .font(.system(size: 10.6, weight: .bold))
                    .monospacedDigit()
                    .foregroundStyle(Color(hex: 0x4c4c4c))
                    .fixedSize()
                    .frame(width: 100, alignment: .trailing)
                    .position(x: 312.5 - 50, y: 127.3)

                Button {
                    onSwapPress?()
                } label: {
                    swapChevrons
                        .stroke(Color(hex: 0xf2f2f2), style: StrokeStyle(lineWidth: 1.15, lineCap: .round, lineJoin: .round))
                        .frame(width: Metrics.swapRadius * 2, height: Metrics.swapRadius * 2)
                        .background(Color(hex: 0x2d2d2d), in: Circle())
                }
                .buttonStyle(SwapStyle())
                .accessibilityLabel("Switch time zone")
                .position(x: Metrics.swapX, y: Metrics.swapY)
            }
            .lineLimit(1)
            .frame(width: Metrics.width, height: Metrics.height)
            .accessibilityElement(children: .contain)
            .accessibilityLabel("\(origin.code) \(origin.city) to \(destination.code) \(destination.city), \(eta)")
            .accessibilityValue(remaining)
        }
        .contentShape(RoundedRectangle(cornerRadius: Metrics.radius))
        .onTapGesture { restart() }
        .onAppear { sync() }
        .onChange(of: autoPlay) { sync() }
        .onChange(of: reduceMotion) { sync() }
        .onChange(of: progress) {
            sync()
            guard controlled else { return }
            held.retarget(target, duration: reduceMotion ? 0.2 : 1.4, curve: Ease.out)
            settling = true
        }
        .task(id: settling) {
            guard settling else { return }
            try? await Task.sleep(for: .seconds(1.5))
            settling = false
        }
    }

    private func label(_ text: String, size: Double, weight: Font.Weight, color: Color, x: Double, y: Double, width: Double) -> some View {
        Text(text)
            .font(.system(size: size, weight: weight))
            .foregroundStyle(color)
            .fixedSize()
            .frame(width: width, alignment: .leading)
            .position(x: x + width / 2, y: y)
            .allowsHitTesting(false)
    }

    private struct SwapStyle: ButtonStyle {
        func makeBody(configuration: Configuration) -> some View {
            configuration.label
                .scaleEffect(configuration.isPressed ? 0.9 : 1)
                .animation(.timingCurve(0.23, 1, 0.32, 1, duration: 0.13), value: configuration.isPressed)
                .sensoryFeedback(.impact(weight: .light), trigger: configuration.isPressed) { _, pressed in pressed }
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

    private func draw(_ context: GraphicsContext, value: Double, time: Double, moving: Double, sinceArrival: Double) {
        let x = Metrics.bleed
        let y = Metrics.bleed
        let ambient = reduceMotion ? 0.0 : 1.0
        let cardRect = CGRect(x: x, y: y, width: Metrics.width, height: Metrics.height)
        let card = Path(roundedRect: cardRect, cornerRadius: Metrics.radius, style: .circular)
        let bx = x + Metrics.barX
        let by = y + Metrics.barY
        let cy = y + Metrics.barCenterY
        let barHeight = Metrics.barHeight
        let width = fillWidth(value)

        var shadow = context
        shadow.addFilter(.blur(radius: 12))
        shadow.fill(
            Path(
                roundedRect: CGRect(x: x + 8, y: y + 12, width: Metrics.width - 16, height: Metrics.height - 8),
                cornerRadius: Metrics.radius, style: .circular
            ),
            with: .color(.black.opacity(0.55))
        )
        context.fill(card, with: .color(Color(hex: 0x080808)))

        var inside = context
        inside.clip(to: card)

        // Tinted haze behind the route, and the dust floating in it.
        let haze = palette.haze
        inside.fill(
            circle(x: x + 150, y: y + 64, r: 170),
            with: .radialGradient(
                Gradient(stops: [
                    .init(color: haze.opacity(0.13), location: 0),
                    .init(color: haze.opacity(0.05), location: 0.5),
                    .init(color: haze.opacity(0), location: 1),
                ]),
                center: CGPoint(x: x + 150, y: y + 64), startRadius: 0, endRadius: 170
            )
        )
        var specks = inside
        specks.translateBy(x: x, y: y)
        let twinkles = [
            0.5 + ambient * 0.3 * sin(time * 1.3),
            0.5 + ambient * 0.3 * sin(time * 1.7 + 2.1),
            0.5 + ambient * 0.3 * sin(time * 1.1 + 4.2),
        ]
        for index in 0..<3 {
            specks.fill(dust[index], with: .color(palette.glow.opacity(twinkles[index])))
        }
        // Bevel: a soft light rim just inside the card's edge.
        var bevel = inside
        bevel.addFilter(.blur(radius: 5))
        bevel.stroke(card, with: .color(.white.opacity(0.13)), lineWidth: 5)
        inside.stroke(
            Path(roundedRect: cardRect.insetBy(dx: 0.5, dy: 0.5), cornerRadius: Metrics.radius, style: .circular),
            with: .linearGradient(
                Gradient(colors: [haze.opacity(0.4), haze.opacity(0)]),
                startPoint: CGPoint(x: x, y: y), endPoint: CGPoint(x: x, y: y + Metrics.height * 0.5)
            ),
            lineWidth: 1
        )

        // Arrow between the airports.
        var arrowLayer = inside
        arrowLayer.translateBy(x: x, y: y)
        let arrowStyle = StrokeStyle(lineWidth: 3.1, lineCap: .round, lineJoin: .round)
        var arrowGlow = arrowLayer
        arrowGlow.addFilter(.blur(radius: 7))
        arrowGlow.stroke(arrow, with: .color(palette.arrow.opacity(0.5)), style: StrokeStyle(lineWidth: 7, lineCap: .round, lineJoin: .round))
        arrowLayer.stroke(arrow, with: .color(palette.arrow), style: arrowStyle)
        // A spark runs down the arrow every couple of seconds.
        let sparkAt = time.truncatingRemainder(dividingBy: 2.6) / 0.9
        if ambient > 0, sparkAt < 1 {
            var spark = inside
            spark.addFilter(.blur(radius: 2.5))
            spark.fill(
                circle(x: x + Metrics.arrowFrom + (Metrics.arrowTo - Metrics.arrowFrom) * sparkAt, y: y + Metrics.arrowY, r: 3.4),
                with: .color(.white.opacity(sin(.pi * sparkAt)))
            )
        }

        // ETA box.
        let box = Path(
            roundedRect: CGRect(x: x + Metrics.boxX, y: y + Metrics.boxY, width: Metrics.boxWidth, height: Metrics.boxHeight),
            cornerRadius: 9.5, style: .circular
        )
        inside.fill(box, with: .color(Color(hex: 0x040404)))
        inside.stroke(box, with: .color(.white.opacity(0.14)), lineWidth: 1)

        // Progress bar: track, glow, glossy fill.
        inside.fill(capsule(CGRect(x: bx, y: by, width: Metrics.barWidth, height: barHeight)), with: .color(Color(hex: 0x1b1b1b)))
        let fill = capsule(CGRect(x: bx, y: by, width: width, height: barHeight))
        var glow = inside
        glow.addFilter(.blur(radius: 9))
        // The glow under the bar breathes.
        glow.fill(fill, with: .color(palette.glow.opacity(0.62 + ambient * 0.14 * sin(time * 1.9))))

        var bar = inside
        bar.clip(to: fill)
        bar.fill(
            fill,
            with: .linearGradient(
                Gradient(stops: [
                    .init(color: palette.fill[0], location: 0),
                    .init(color: palette.fill[1], location: 0.5),
                    .init(color: palette.fill[2], location: 1),
                ]),
                startPoint: CGPoint(x: bx, y: by), endPoint: CGPoint(x: bx + width, y: by)
            )
        )
        // Gloss: a bright rim that is strongest along the top.
        var rim = bar
        rim.addFilter(.blur(radius: 2.2))
        rim.stroke(fill, with: .color(palette.rim.opacity(0.9)), lineWidth: 3.5)
        var sheen = bar
        sheen.addFilter(.blur(radius: 5))
        sheen.fill(
            capsule(CGRect(x: bx + 6, y: by + barHeight - 9, width: Metrics.barWidth, height: 12)),
            with: .color(.white.opacity(0.16))
        )
        // Slipstream: short lines that fall behind the plane while it flies.
        if moving > 0.01 {
            for index in 0..<3 {
                let along = (time * 70 + Double(index) * 23).truncatingRemainder(dividingBy: 62)
                let lineY = cy + Double(index - 1) * 6.5 + (index == 1 ? 1.5 : 0)
                bar.fill(
                    capsule(CGRect(
                        x: bx + width - Metrics.planeInset - 16 - along, y: lineY - 0.6,
                        width: 9 + Double(index) * 3, height: 1.2
                    )),
                    with: .color(.white.opacity(moving * 0.7 * clamp01(along / 8) * clamp01((62 - along) / 30)))
                )
            }
        }
        // A band of light crosses the filled part when the plane levels off.
        let sweepAt = (sinceArrival - 0.15) / 1.1
        if sweepAt > 0, sweepAt < 1 {
            var sweep = bar
            sweep.translateBy(x: bx - 30 + (width + 60) * sweepAt, y: cy)
            sweep.addFilter(.blur(radius: 8))
            sweep.fill(sweepBand, with: .color(.white.opacity(ambient * 0.5 * sin(.pi * sweepAt))))
        }

        // The plane noses up and lifts off its ground line while it flies, and bobs a little when level.
        let ground = 8.6 * (1 - 0.45 * moving)
        inside.fill(
            capsule(CGRect(x: bx + width - Metrics.planeInset - ground / 2, y: cy + 5.1, width: ground, height: 1.1)),
            with: .color(.white.opacity(1 - 0.5 * moving))
        )
        let bob = ambient * (1 - moving) * 0.7 * sin(time * 2.3)
        var plane = inside
        plane.translateBy(x: bx + width - Metrics.planeInset, y: cy - 2.2 - 2.4 * moving + bob)
        plane.rotate(by: .radians(-(0.27 + 0.2 * moving)))
        plane.fill(planeTail, with: .color(.white))
        plane.fill(planeWing, with: .color(.white))
        plane.fill(planeBody, with: .color(.white))
    }
}

private struct Palette {
    let glow: Color
    let fill: [Color]
    let rim: Color
    let arrow: Color
    let note: Color
    let haze: Color

    static let lime = Palette(
        glow: Color(hex: 0xb4f01f),
        fill: [Color(hex: 0xebfb7c), Color(hex: 0xc8ef38), Color(hex: 0x84bd16)],
        rim: Color(hex: 0xc2f41e), arrow: Color(hex: 0xb9f52c), note: Color(hex: 0xff7d1a),
        haze: .rgba(184, 242, 39, 1)
    )
    static let orange = Palette(
        glow: Color(hex: 0xff8a1f),
        fill: [Color(hex: 0xffd3a8), Color(hex: 0xffb45e), Color(hex: 0xff6a05)],
        rim: Color(hex: 0xffc686), arrow: Color(hex: 0xffb90a), note: Color(hex: 0xc9ef4b),
        haze: .rgba(255, 150, 40, 1)
    )
}

private enum Metrics {
    static let width = 356.0
    static let height = 160.0
    static let radius = 22.0
    /// The canvas overflows the card by this much on every side so the soft shadow is not clipped.
    static let bleed = 24.0

    static let barX = 14.8
    static let barY = 109.1
    static let barWidth = 315.3
    static let barHeight = 36.5
    static let barCenterY = barY + barHeight / 2
    /// The plane sits this far inside the filled part's leading edge.
    static let planeInset = 19.4

    static let boxX = 220.5
    static let boxY = 15.3
    static let boxWidth = 120.7
    static let boxHeight = 78.2
    static let swapX = 319.2
    static let swapY = 36.8
    static let swapRadius = 10.1

    static let arrowFrom = 94.3
    static let arrowTo = 122.0
    static let arrowY = 44.0

    // The reference shows 54% flown; the autoPlay loop built around it (seconds).
    static let rest = 0.54
    static let low = 0.04
    static let start = 0.5
    static let fill = 3.0
    static let hold = 3.4
    static let reset = 0.8
    static let loop = start + fill + hold + reset
    /// A moment in the loop where everything is at rest at the reference value: used when nothing is playing.
    static let restTime = start + fill + 1.6
}

private func clamp01(_ t: Double) -> Double { min(1, max(0, t)) }

private func easeInOutCubic(_ t: Double) -> Double {
    t < 0.5 ? 4 * t * t * t : 1 - pow(-2 * t + 2, 3) / 2
}

/// Share of the flight flown at `t` seconds into the autoPlay loop.
private func loopValue(_ t: Double) -> Double {
    let span = Metrics.rest - Metrics.low
    if t < Metrics.start { return Metrics.low }
    if t < Metrics.start + Metrics.fill { return Metrics.low + span * easeInOutCubic((t - Metrics.start) / Metrics.fill) }
    if t < Metrics.start + Metrics.fill + Metrics.hold { return Metrics.rest }
    return Metrics.rest - span * easeInOutCubic((t - Metrics.start - Metrics.fill - Metrics.hold) / Metrics.reset)
}

private func fillWidth(_ p: Double) -> Double {
    max(Metrics.barHeight, Metrics.barWidth * clamp01(p))
}

private func circle(x: Double, y: Double, r: Double) -> Path {
    Path(ellipseIn: CGRect(x: x - r, y: y - r, width: r * 2, height: r * 2))
}

/// Dust on the card, densest along the top edge: three sets of dots that twinkle out of phase.
/// A fixed-seed generator keeps the pattern identical on every platform.
private let dust: [Path] = {
    var seed: UInt32 = 20_240_611
    func next() -> Double {
        seed = seed &* 1_664_525 &+ 1_013_904_223
        return Double(seed) / 4_294_967_296
    }
    var sets = [Path(), Path(), Path()]
    for index in 0..<420 {
        let x = 4 + next() * (Metrics.width - 8)
        let fall = next()
        let y = 2 + fall * fall * fall * 112
        let r = 0.18 + next() * 0.26
        sets[index % 3].addEllipse(in: CGRect(x: x - r, y: y - r, width: r * 2, height: r * 2))
    }
    return sets
}()

// A small plane seen from the side, nose to the right, centred on its body.
private let planeBody: Path = {
    var p = Path()
    p.move(to: CGPoint(x: -7.2, y: 0.4))
    p.addCurve(to: CGPoint(x: -4, y: -2.2), control1: CGPoint(x: -7.2, y: -1.4), control2: CGPoint(x: -5.8, y: -2.2))
    p.addLine(to: CGPoint(x: 3.2, y: -2.2))
    p.addCurve(to: CGPoint(x: 7.8, y: 0.2), control1: CGPoint(x: 5.9, y: -2.2), control2: CGPoint(x: 7.8, y: -1))
    p.addCurve(to: CGPoint(x: 3.2, y: 2.3), control1: CGPoint(x: 7.8, y: 1.4), control2: CGPoint(x: 5.9, y: 2.3))
    p.addLine(to: CGPoint(x: -4.8, y: 2.3))
    p.addCurve(to: CGPoint(x: -7.2, y: 0.4), control1: CGPoint(x: -6.2, y: 2.3), control2: CGPoint(x: -7.2, y: 1.5))
    p.closeSubpath()
    return p
}()

private let planeTail: Path = {
    var p = Path()
    p.move(to: CGPoint(x: -6.9, y: -1.2))
    p.addLine(to: CGPoint(x: -8, y: -5.6))
    p.addLine(to: CGPoint(x: -5.3, y: -5.6))
    p.addLine(to: CGPoint(x: -2.6, y: -1.6))
    p.closeSubpath()
    return p
}()

private let planeWing: Path = {
    var p = Path()
    p.move(to: CGPoint(x: -1.6, y: 0.4))
    p.addLine(to: CGPoint(x: 2.6, y: 0.4))
    p.addLine(to: CGPoint(x: -1.2, y: 5.9))
    p.addLine(to: CGPoint(x: -3.9, y: 5.9))
    p.closeSubpath()
    return p
}()

private let arrow: Path = {
    var p = Path()
    p.move(to: CGPoint(x: Metrics.arrowFrom, y: Metrics.arrowY))
    p.addLine(to: CGPoint(x: Metrics.arrowTo, y: Metrics.arrowY))
    p.move(to: CGPoint(x: Metrics.arrowTo - 8.6, y: Metrics.arrowY - 8.6))
    p.addLine(to: CGPoint(x: Metrics.arrowTo, y: Metrics.arrowY))
    p.addLine(to: CGPoint(x: Metrics.arrowTo - 8.6, y: Metrics.arrowY + 8.6))
    return p
}()

/// A slanted band of light, centred on the bar's middle line.
private let sweepBand: Path = {
    var p = Path()
    p.move(to: CGPoint(x: -6, y: -26))
    p.addLine(to: CGPoint(x: 22, y: -26))
    p.addLine(to: CGPoint(x: 6, y: 26))
    p.addLine(to: CGPoint(x: -22, y: 26))
    p.closeSubpath()
    return p
}()

/// Two small chevrons, one above the other, in a box centred on the button.
private let swapChevrons: Path = {
    let c = Metrics.swapRadius
    var p = Path()
    p.move(to: CGPoint(x: c + 1.4, y: c - 5.6))
    p.addLine(to: CGPoint(x: c - 1.6, y: c - 3.3))
    p.addLine(to: CGPoint(x: c + 1.4, y: c - 1))
    p.move(to: CGPoint(x: c - 1.4, y: c + 1))
    p.addLine(to: CGPoint(x: c + 1.6, y: c + 3.3))
    p.addLine(to: CGPoint(x: c - 1.4, y: c + 5.6))
    return p
}()

#Preview {
    ZStack {
        Color.black.ignoresSafeArea()
        VStack(spacing: 22) {
            FlightProgress(autoPlay: true)
            FlightProgress(autoPlay: true, tint: .orange)
        }
    }
}

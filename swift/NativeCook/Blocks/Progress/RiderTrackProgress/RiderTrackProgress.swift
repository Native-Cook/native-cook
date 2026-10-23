import SwiftUI

/// Order-tracking panel on a glowing phone screen: the restaurant and order, a road with a courier
/// on a scooter, and the courier's row with the time left and a call button. The wheels turn with
/// the distance covered, the scooter bounces, tips as it pulls away and brakes, and leaves exhaust
/// puffs; a teal glow rides along, the minutes tick down with a small pop and the handset rings.
struct RiderTrackProgress: View {
    /// Controlled share of the trip ridden, 0…1.
    var progress: Double? = nil
    /// Rides to the point shown in the reference, waits there, rides on to the door, and starts over.
    var autoPlay = false
    var restaurant = "Food Hub"
    var order = "Pizza Pepperoni"
    var price = "250₹"
    var payment = "COD"
    var courier = "Alex R."
    /// Text in front of the time left.
    var arrival = "Will arrive in"
    /// Minutes the whole trip takes: the time left counts down from it.
    var tripMinutes = 16.0
    var onCallPress: (() -> Void)? = nil

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    /// Origin of the ambient clock (bounce, puffs, breathing glow, ringing).
    @State private var epoch = Date.now
    /// When the current autoPlay / tapped run started; `nil` shows the resting frame.
    @State private var runStart: Date? = nil
    @State private var held = Tween(Metrics.rest)
    /// Keeps the timeline running for a controlled change under reduced motion.
    @State private var settling = false
    @State private var ride = Ride()

    /// Per-frame state that depends on how the value has been moving.
    private final class Ride {
        var date: Date? = nil
        var before = Metrics.rest
        /// 0…1, smoothed: is the scooter rolling.
        var moving = 0.0
        /// Positive while pulling away, negative while braking: tips the scooter.
        var pitch = 0.0
        /// How far the wheels have turned, radians.
        var wheel = 0.0
        /// The minutes showing, and when they last changed.
        var minutes = -1
        var tickedAt = Date.distantPast

        func advance(to now: Date, value: Double, minutes next: Int) {
            let dt = min(max(now.timeIntervalSince(date ?? now), 0), 0.064)
            let first = date == nil
            date = now
            let step = value - before
            before = value
            if next != minutes {
                minutes = next
                if !first { tickedAt = now }
            }
            guard dt > 0 else { return }
            // A jump back to the start is not a ride.
            let rolling = abs(step) > 0.00003 && abs(step) < 0.05
            let was = moving
            moving = was + ((rolling ? 1 : 0) - was) * min(1, dt * 7)
            pitch += ((rolling ? 1 : 0) - was - pitch) * min(1, dt * 10)
            if rolling { wheel += step * Metrics.trackWidth / (Metrics.wheelRadius * Metrics.riderScale) }
        }
    }

    private var controlled: Bool { progress != nil }
    private var target: Double { min(1, max(0, progress ?? Metrics.rest)) }

    private var initials: String {
        String(courier.split(separator: " ").compactMap(\.first).prefix(2)).uppercased()
    }

    var body: some View {
        TimelineView(.animation(paused: reduceMotion && !settling)) { timeline in
            let date = timeline.date
            let time = reduceMotion ? 0 : date.timeIntervalSince(epoch)
            let t = clock(at: date)
            let value = controlled ? held.value(at: date) : loopValue(t)
            let presence = controlled ? 1 : loopPresence(t)
            let minutes = Int((tripMinutes * (1 - clamp01(value)) - 0.001).rounded(.up))
            let _ = ride.advance(to: date, value: value, minutes: minutes)
            let left = minutes > 0 ? "\(minutes)min" : "a moment"
            // 0 → 1 after the minutes change: the number pops.
            let tick = reduceMotion ? 1 : Ease.out.value(at: clamp01(date.timeIntervalSince(ride.tickedAt) / 0.38))

            ZStack {
                Canvas { context, _ in
                    draw(context, value: value, time: time, presence: presence)
                }
                .padding(-Metrics.bleed)
                .allowsHitTesting(false)

                label(restaurant, size: 12.6, weight: .bold, color: .white, x: 59.7, y: 28.3)
                    .accessibilityLabel("\(restaurant), \(order)")
                    .accessibilityValue("\(courier) \(arrival.lowercased()) \(left)")
                label(order, size: 11, weight: .medium, color: Metrics.grey, x: 59.7, y: 46.1)
                label(price, size: 12.6, weight: .bold, color: .white, x: 320.9, y: 28.3, trailing: true)
                label(payment, size: 11, weight: .medium, color: Metrics.grey, x: 320.9, y: 46.1, trailing: true)

                Text(initials)
                    .font(.system(size: 11.5, weight: .bold))
                    .tracking(0.3)
                    .foregroundStyle(.white)
                    .fixedSize()
                    .position(x: 32.8, y: 146.1)
                    .accessibilityHidden(true)
                label(courier, size: 12.6, weight: .bold, color: .white, x: 59.7, y: 137.7)

                HStack(spacing: 0) {
                    Text(arrival + " ")
                        .font(.system(size: 11, weight: .medium))
                        .foregroundStyle(Metrics.grey)
                    Text(left)
                        .font(.system(size: 11, weight: .bold))
                        .monospacedDigit()
                        .foregroundStyle(Metrics.teal)
                        .opacity(0.4 + 0.6 * tick)
                        .scaleEffect(1 + 0.14 * (1 - tick), anchor: .leading)
                        .offset(y: -3 * (1 - tick))
                }
                .tracking(0.3)
                .fixedSize()
                .frame(width: 240, alignment: .leading)
                .position(x: 59.7 + 120, y: 155.5)

                Button {
                    onCallPress?()
                } label: {
                    Canvas { context, _ in
                        // The handset "rings": its two waves light up one after the other.
                        let near = reduceMotion ? 1 : 0.35 + 0.65 * clamp01(sin(time * 3.2) * 1.4 + 0.3)
                        let far = reduceMotion ? 1 : 0.25 + 0.75 * clamp01(sin(time * 3.2 - 0.9) * 1.4 + 0.3)
                        var glyph = context
                        glyph.translateBy(x: 15.2, y: 15)
                        glyph.scaleBy(x: 1.12, y: 1.12)
                        glyph.stroke(phoneGlyph, with: .color(Metrics.teal), style: StrokeStyle(lineWidth: 1.25, lineJoin: .round))
                        glyph.stroke(waveNear, with: .color(Metrics.teal.opacity(near)), style: StrokeStyle(lineWidth: 1.25, lineCap: .round))
                        glyph.stroke(waveFar, with: .color(Metrics.teal.opacity(far)), style: StrokeStyle(lineWidth: 1.25, lineCap: .round))
                    }
                    .frame(width: 30, height: 30)
                    .background(Color(hex: 0x343b3a), in: Circle())
                }
                .buttonStyle(CallStyle())
                .accessibilityLabel("Call \(courier)")
                .position(x: 306.8, y: 146.1)
            }
            .lineLimit(1)
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
            held.retarget(target, duration: reduceMotion ? 0.2 : 1.6, curve: Ease.out)
            settling = true
        }
        .task(id: settling) {
            guard settling else { return }
            try? await Task.sleep(for: .seconds(1.7))
            settling = false
        }
    }

    private func label(
        _ text: String, size: Double, weight: Font.Weight, color: Color, x: Double, y: Double, trailing: Bool = false
    ) -> some View {
        Text(text)
            .font(.system(size: size, weight: weight))
            .tracking(0.3)
            .foregroundStyle(color)
            .fixedSize()
            .frame(width: 180, alignment: trailing ? .trailing : .leading)
            .position(x: trailing ? x - 90 : x + 90, y: y)
            .allowsHitTesting(false)
    }

    private struct CallStyle: ButtonStyle {
        func makeBody(configuration: Configuration) -> some View {
            configuration.label
                .scaleEffect(configuration.isPressed ? 0.92 : 1)
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

    private func draw(_ context: GraphicsContext, value: Double, time: Double, presence: Double) {
        let x = Metrics.bleed
        let y = Metrics.bleed
        let ambient = reduceMotion ? 0.0 : 1.0
        let moving = reduceMotion ? 0 : ride.moving
        let panel = Path(
            roundedRect: CGRect(x: x, y: y, width: Metrics.width, height: Metrics.height),
            cornerRadius: Metrics.radius, style: .circular
        )
        let cy = y + Metrics.trackY
        let trackHeight = Metrics.trackHeight
        let end = x + fillEnd(value)

        // The screen the panel floats on: pale at the top, deep blue towards the bottom.
        context.fill(
            Path(
                roundedRect: CGRect(x: x - 9, y: y - 9, width: Metrics.width + 18, height: Metrics.height + 70),
                cornerRadius: Metrics.radius + 9, style: .circular
            ),
            with: .linearGradient(
                Gradient(stops: [
                    .init(color: .rgba(222, 227, 238, 1), location: 0),
                    .init(color: .rgba(150, 178, 214, 0.9), location: 0.5),
                    .init(color: .rgba(40, 86, 160, 0.5), location: 0.74),
                    .init(color: .rgba(10, 30, 90, 0), location: 1),
                ]),
                startPoint: CGPoint(x: x, y: y - 9), endPoint: CGPoint(x: x, y: y + Metrics.height + 61)
            )
        )
        context.fill(panel, with: .color(.black))

        var inside = context
        inside.clip(to: panel)

        // The glow travels with the rider and breathes.
        var glow = inside
        glow.translateBy(x: end - 22, y: cy - 14)
        glow.opacity = presence * (0.86 + ambient * 0.14 * sin(time * 2))
        glow.fill(
            Path(ellipseIn: CGRect(x: -92, y: -92, width: 184, height: 184)),
            with: .radialGradient(
                Gradient(stops: [
                    .init(color: .rgba(24, 214, 160, 0.42), location: 0),
                    .init(color: .rgba(20, 190, 150, 0.2), location: 0.4),
                    .init(color: .rgba(16, 150, 130, 0.05), location: 0.75),
                    .init(color: .rgba(16, 150, 130, 0), location: 1),
                ]),
                center: .zero, startRadius: 0, endRadius: 92
            )
        )

        // Road: dim track, lit part with its glow.
        let top = cy - trackHeight / 2
        inside.fill(
            capsule(CGRect(x: x + Metrics.trackX, y: top, width: Metrics.trackWidth, height: trackHeight)),
            with: .color(Color(hex: 0x2d2d2d))
        )
        let lit = capsule(CGRect(x: x + Metrics.trackX, y: top, width: max(trackHeight, end - x - Metrics.trackX), height: trackHeight))
        var barGlow = inside
        barGlow.addFilter(.blur(radius: 4))
        barGlow.fill(lit, with: .color(Color(hex: 0x1fd9b4, opacity: presence * 0.55)))
        var bar = inside
        bar.opacity = presence
        bar.fill(
            lit,
            with: .linearGradient(
                Gradient(stops: [
                    .init(color: Color(hex: 0x1aaef5), location: 0),
                    .init(color: Color(hex: 0x19d2c8), location: 0.6),
                    .init(color: Color(hex: 0x22e3a0), location: 1),
                ]),
                startPoint: CGPoint(x: x + Metrics.trackX, y: cy), endPoint: CGPoint(x: end, y: cy)
            )
        )

        // Exhaust: small clouds that drift back from the rear wheel and thin out.
        if moving > 0.01 {
            for index in 0..<3 {
                let u = (time * 1.5 + Double(index) / 3).truncatingRemainder(dividingBy: 1)
                let puff = Path(ellipseIn: CGRect(
                    x: end - Metrics.riderFront - 22 * Metrics.riderScale - 2 - 15 * u - (1.3 + 2.6 * u),
                    y: y + Metrics.groundY - 7 - 6 * u - (1.3 + 2.6 * u),
                    width: (1.3 + 2.6 * u) * 2, height: (1.3 + 2.6 * u) * 2
                ))
                inside.fill(puff, with: .color(Color(hex: 0xc9efe4, opacity: presence * moving * 0.55 * (1 - u) * clamp01(u * 6))))
            }
        }

        // The scooter bounces on the road while it rolls, idles with a slow sway, and tips as it pulls away or brakes.
        let bounce = ambient * (moving * 0.7 * abs(sin(time * 13)) + (1 - moving) * 0.35 * (0.5 + 0.5 * sin(time * 2.4)))
        var rider = inside
        rider.opacity = presence
        rider.translateBy(x: end - Metrics.riderFront, y: y + Metrics.groundY - bounce)
        rider.rotate(by: .radians(-0.12 * ambient * ride.pitch))
        rider.scaleBy(x: Metrics.riderScale, y: Metrics.riderScale)
        for wheelX in [-13.0, 13.5] {
            let r = Metrics.wheelRadius
            rider.fill(circle(x: wheelX, y: -r, r: r), with: .color(Color(hex: 0x3a3c48)))
            rider.fill(circle(x: wheelX, y: -r, r: 2.6), with: .color(Color(hex: 0xc9ccd8)))
            var spokes = rider
            spokes.translateBy(x: wheelX, y: -r)
            spokes.rotate(by: .radians(ride.wheel))
            spokes.stroke(spokesPath, with: .color(Color(hex: 0x3a3c48)), style: StrokeStyle(lineWidth: 0.9, lineCap: .round))
        }
        // Delivery box and seat.
        rider.fill(Path(roundedRect: CGRect(x: -20.5, y: -25.5, width: 8, height: 8.6), cornerRadius: 1), with: .color(Color(hex: 0xe8562a)))
        rider.fill(Path(roundedRect: CGRect(x: -21.2, y: -27.2, width: 9.4, height: 2.3), cornerRadius: 1), with: .color(Color(hex: 0xf5c53a)))
        rider.fill(Path(roundedRect: CGRect(x: -12.4, y: -19, width: 10.4, height: 2.8), cornerRadius: 1.4), with: .color(Color(hex: 0xf5c53a)))
        rider.fill(scooterBody, with: .color(Color(hex: 0x3d8df2)))
        rider.fill(scooterFender, with: .color(Color(hex: 0xf2c230)))
        rider.stroke(scooterBar, with: .color(Color(hex: 0x2a2c36)), style: StrokeStyle(lineWidth: 1.4, lineCap: .round))
        rider.fill(circle(x: 12.6, y: -25.8, r: 2.1), with: .color(Color(hex: 0xf5c53a)))
        // Rider.
        rider.stroke(riderLeg, with: .color(.white), style: StrokeStyle(lineWidth: 3.4, lineCap: .round, lineJoin: .round))
        rider.fill(riderShoe, with: .color(Color(hex: 0xe9edf5)))
        rider.fill(riderTorso, with: .color(Color(hex: 0xf04e2c)))
        rider.stroke(riderArm, with: .color(Color(hex: 0xf04e2c)), style: StrokeStyle(lineWidth: 2.5, lineCap: .round, lineJoin: .round))
        rider.fill(circle(x: -2.2, y: -34.6, r: 2.6), with: .color(Color(hex: 0xf3c9a4)))
        rider.fill(circle(x: -4.2, y: -35.6, r: 3.7), with: .color(Color(hex: 0xf5c22e)))

        // Restaurant badge and courier avatar.
        context.fill(circle(x: x + 32.8, y: y + 36.5, r: 15), with: .color(Color(hex: 0x353c3b)))
        var badge = context
        badge.translateBy(x: x + 32.4, y: y + 36.8)
        badge.scaleBy(x: 0.92, y: 0.92)
        badge.fill(drinkGlyph, with: .color(.white))
        badge.stroke(strawGlyph, with: .color(.white), style: StrokeStyle(lineWidth: 1.1, lineCap: .round, lineJoin: .round))
        badge.fill(burgerGlyph, with: .color(.white))
        context.fill(
            circle(x: x + 32.8, y: y + 146.1, r: 15),
            with: .linearGradient(
                Gradient(colors: [Color(hex: 0xd49a6a), Color(hex: 0x8a5636)]),
                startPoint: CGPoint(x: x + 20, y: y + 132), endPoint: CGPoint(x: x + 46, y: y + 160)
            )
        )
    }
}

private enum Metrics {
    static let width = 340.0
    static let height = 183.0
    static let radius = 35.0
    /// The canvas overflows the panel by this much on every side to fit the screen glow around it.
    static let bleed = 60.0

    static let trackX = 17.3
    static let trackWidth = 304.0
    static let trackY = 109.1
    static let trackHeight = 5.8
    /// The scooter is drawn around the point where it meets the road, at this scale.
    static let riderScale = 0.97
    static let riderFront = 20.2 * riderScale + 2.3
    static let wheelRadius = 5.0
    static let groundY = trackY - trackHeight / 2 + 0.5

    static let teal = Color(hex: 0x2fdca8)
    static let grey = Color(hex: 0x7b7b7b)

    // The reference shows the rider 69% of the way; the autoPlay loop built around it (seconds).
    static let rest = 0.69
    static let low = 0.14
    static let start = 0.6
    static let ride = 3.2
    static let hold = 3.0
    static let finish = 1.7
    static let holdEnd = 1.6
    static let reset = 0.8
    static let t1 = start + ride
    static let t2 = t1 + hold
    static let t3 = t2 + finish
    static let t4 = t3 + holdEnd
    static let loop = t4 + reset
    /// A moment in the loop where everything is at rest at the reference value: used when nothing is playing.
    static let restTime = t1 + 1.5
}

private func clamp01(_ t: Double) -> Double { min(1, max(0, t)) }

private func easeInOutCubic(_ t: Double) -> Double {
    t < 0.5 ? 4 * t * t * t : 1 - pow(-2 * t + 2, 3) / 2
}

/// Share of the trip ridden at `t` seconds into the autoPlay loop.
private func loopValue(_ t: Double) -> Double {
    if t < Metrics.start { return Metrics.low }
    if t < Metrics.t1 { return Metrics.low + (Metrics.rest - Metrics.low) * easeInOutCubic((t - Metrics.start) / Metrics.ride) }
    if t < Metrics.t2 { return Metrics.rest }
    if t < Metrics.t3 { return Metrics.rest + (1 - Metrics.rest) * easeInOutCubic((t - Metrics.t2) / Metrics.finish) }
    if t < Metrics.t4 { return 1 }
    return Metrics.low
}

/// The rider fades out at the door and back in at the start.
private func loopPresence(_ t: Double) -> Double {
    if t < Metrics.start { return clamp01(t / 0.4) }
    if t < Metrics.t4 { return 1 }
    return 1 - clamp01((t - Metrics.t4) / (Metrics.reset * 0.6))
}

private func fillEnd(_ p: Double) -> Double {
    Metrics.trackX + Metrics.trackWidth * clamp01(p)
}

private func circle(x: Double, y: Double, r: Double) -> Path {
    Path(ellipseIn: CGRect(x: x - r, y: y - r, width: r * 2, height: r * 2))
}

/// Builds a path from SVG path data (absolute M, L, H, V, C, Q and Z only).
private func svgPath(_ data: String) -> Path {
    var tokens: [String] = []
    var current = ""
    for character in data {
        if character.isLetter {
            if !current.isEmpty { tokens.append(current) }
            current = ""
            tokens.append(String(character))
        } else if character == " " || character == "," {
            if !current.isEmpty { tokens.append(current) }
            current = ""
        } else if character == "-", !current.isEmpty {
            tokens.append(current)
            current = "-"
        } else {
            current.append(character)
        }
    }
    if !current.isEmpty { tokens.append(current) }

    var path = Path()
    var index = 0
    var point = CGPoint.zero
    var command = "M"
    func number() -> Double {
        defer { index += 1 }
        return index < tokens.count ? Double(tokens[index]) ?? 0 : 0
    }
    while index < tokens.count {
        if let first = tokens[index].first, first.isLetter {
            command = tokens[index]
            index += 1
        }
        switch command {
        case "M":
            point = CGPoint(x: number(), y: number())
            path.move(to: point)
            command = "L"
        case "L":
            point = CGPoint(x: number(), y: number())
            path.addLine(to: point)
        case "H":
            point.x = number()
            path.addLine(to: point)
        case "V":
            point.y = number()
            path.addLine(to: point)
        case "C":
            let c1 = CGPoint(x: number(), y: number())
            let c2 = CGPoint(x: number(), y: number())
            point = CGPoint(x: number(), y: number())
            path.addCurve(to: point, control1: c1, control2: c2)
        case "Q":
            let c = CGPoint(x: number(), y: number())
            point = CGPoint(x: number(), y: number())
            path.addQuadCurve(to: point, control: c)
        case "Z":
            path.closeSubpath()
            command = ""
        default:
            index += 1
        }
    }
    return path
}

// Scooter and rider, nose to the right; the origin is the middle of the road under the wheels.
private let scooterBody = svgPath(
    "M-19.5 -8.5C-19.5 -13.5 -16 -16.5 -10 -16.5L-2.5 -16.5L-2.5 -9.5L5.5 -9.5L9.2 -24.5L12.4 -24.5L14.4 -10C11.5 -9.5 9 -7.6 8.4 -5L-7 -5C-7.6 -9.5 -12 -11.5 -15.5 -10.5C-17 -10 -18.6 -9.4 -19.5 -8.5Z"
)
private let scooterFender = svgPath(
    "M7.8 -6C8.3 -10.2 11 -12 14.2 -12C17 -12 19.6 -10.2 20.2 -7L17 -7.6C16.2 -9 15 -9.6 13.8 -9.6C11.8 -9.6 10.6 -8 10.4 -6Z"
)
private let scooterBar = svgPath("M10.4 -25.6L6.2 -28.2")
private let riderLeg = svgPath("M-6.5 -20.6L1 -20.6L2.2 -10.8")
private let riderShoe = svgPath("M1.2 -11.2H5.2V-9.4H1.2Z")
private let riderTorso = svgPath("M-9 -19.5L-8 -29C-7.8 -31 -6.2 -32 -4.4 -32C-2.4 -32 -1.2 -30.6 -1.4 -28.6L-2.4 -19.5Z")
private let riderArm = svgPath("M-3.8 -28.8L2 -26.4L6.4 -27.8")
private let spokesPath = svgPath("M-3.2 0H3.2M0 -3.2V3.2")

// Burger and drink, centred on the badge.
private let burgerGlyph = svgPath(
    "M-7.6 -0.4C-7.6 -4.4 -4.8 -6.4 -1.4 -6.4C2 -6.4 4.8 -4.4 4.8 -0.4ZM-8 1H5.2V3H-8ZM-7.6 4.4H4.8V5.4C4.8 6.4 4 7 3 7H-5.8C-6.8 7 -7.6 6.4 -7.6 5.4Z"
)
private let drinkGlyph = svgPath("M3.4 -4.6H9.4L8.6 7H6.2V-0.6C6.2 -2.2 5 -3.8 3.4 -4.6Z")
private let strawGlyph = svgPath("M6.8 -4.6L7.6 -8.2L9.4 -8.8")
private let phoneGlyph = svgPath(
    "M-4 -4.2C-3.5 -4.4 -3 -4.1 -2.8 -3.6L-2.1 -1.8C-1.9 -1.4 -2.1 -1 -2.4 -0.7L-3.2 -0.1C-2.5 1.3 -1.5 2.3 -0.1 3L0.5 2.2C0.8 1.9 1.2 1.7 1.6 1.9L3.4 2.6C3.9 2.8 4.2 3.3 4 3.8C3.7 4.9 2.7 5.4 1.7 5.2C-2.3 4.5 -5.4 1.4 -6.1 -2.6C-6.3 -3.4 -5.1 -3.9 -4 -4.2Z"
)
private let waveNear = svgPath("M1.2 -3.8Q3.6 -3.2 4.2 -0.8")
private let waveFar = svgPath("M1.8 -6.6Q6.4 -5.6 7.2 -1.2")

#Preview {
    ZStack {
        Color(hex: 0x02040d).ignoresSafeArea()
        RiderTrackProgress(autoPlay: true)
    }
}

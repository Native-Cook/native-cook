import SwiftUI

/// Deep blue glass order card: a headline and ETA, five ringed steps joined by a line, and the
/// driver's row with call and message buttons. A glowing head travels the current leg with pulses
/// of light running into it; reaching a step pops its dot, sends a ring out and lights its glyph,
/// the headline blurs into its next state and the ETA counts down.
struct DeliveryStepsProgress: View {
    /// Controlled position along the five steps, 0…1: every quarter is one step reached.
    var progress: Double? = nil
    /// Heads for the second step (the reference frame), holds, then runs through the remaining steps and starts over.
    var autoPlay = false
    /// Headline while heading for steps 2…5, and once the last one is reached.
    var titles = ["Driver Heading to Laundry", "Laundry in Progress", "Packed and Ready", "Driver Heading to You", "Order Delivered"]
    /// Minutes the whole trip takes: the ETA counts down from it.
    var tripMinutes = 3.0
    var driver = "Sam Carter"
    var vehicle = "Compact Wagon"
    var plate = "CAK 3816"
    var onCallPress: (() -> Void)? = nil
    var onMessagePress: (() -> Void)? = nil

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    /// Origin of the ambient clock (pulses, breathing halo).
    @State private var epoch = Date.now
    /// When the current autoPlay / tapped run started; `nil` shows the resting frame.
    @State private var runStart: Date? = nil
    @State private var held = Tween(Metrics.rest)
    /// Keeps the timeline running for a controlled change under reduced motion.
    @State private var settling = false
    @State private var trip = Trip()

    /// Per-frame state that depends on how the value has been moving.
    private final class Trip {
        var date: Date? = nil
        var before = Metrics.rest
        /// The step that was reached last, and when.
        var popped = -1
        var poppedAt = Date.distantPast
        /// Which headline is showing, and since when.
        var leg = legIndex(Metrics.rest)
        var legChangedAt = Date.distantPast

        func advance(to now: Date, value: Double) {
            let first = date == nil
            date = now
            // Reaching a step sets off its pop.
            for index in 1...4 {
                let at = Double(index) / 4 - 0.002
                if before < at, value >= at {
                    popped = index
                    poppedAt = now
                }
            }
            // On the way back to the start the headline already shows the first leg.
            let next = value < before ? 0 : legIndex(value)
            if next != leg {
                leg = next
                if !first { legChangedAt = now }
            }
            before = value
        }
    }

    private var controlled: Bool { progress != nil }
    private var target: Double { min(1, max(0, progress ?? Metrics.rest)) }

    private var initials: String {
        String(driver.split(separator: " ").compactMap(\.first).prefix(2)).uppercased()
    }

    var body: some View {
        TimelineView(.animation(paused: reduceMotion && !settling)) { timeline in
            let date = timeline.date
            let time = reduceMotion ? 0 : date.timeIntervalSince(epoch)
            let value = controlled ? held.value(at: date) : loopValue(clock(at: date))
            let _ = trip.advance(to: date, value: value)
            let leg = reduceMotion ? legIndex(value) : trip.leg
            // 0 → 1 after the headline changes: it blurs in.
            let settle = reduceMotion ? 1 : Ease.out.value(at: clamp01(date.timeIntervalSince(trip.legChangedAt) / 0.56))
            let minutes = Int((tripMinutes * (1 - clamp01(value)) - 0.001).rounded(.up))
            let eta = minutes <= 0 ? "Arrived" : "ETA : \(minutes) minute\(minutes == 1 ? "" : "s")"
            let step = Int(clamp01(value) * 4 + 0.004) + 1

            ZStack {
                Canvas { context, _ in
                    draw(
                        context, value: value, time: time, popped: trip.popped,
                        sincePop: reduceMotion ? 10 : date.timeIntervalSince(trip.poppedAt)
                    )
                }
                .padding(-Metrics.bleed)
                .allowsHitTesting(false)

                Text(titles[leg])
                    .font(.system(size: 16.6, weight: .regular))
                    .tracking(-0.5)
                    .foregroundStyle(
                        LinearGradient(
                            stops: [
                                .init(color: .rgba(134, 180, 242, 1), location: 0),
                                .init(color: .rgba(126, 226, 238, 1), location: 0.4),
                                .init(color: .rgba(226, 248, 252, 1), location: 0.66),
                                .init(color: .white, location: 1),
                            ],
                            startPoint: .leading, endPoint: .trailing
                        )
                    )
                    .fixedSize()
                    .opacity(0.2 + 0.8 * settle)
                    .blur(radius: 6 * (1 - settle))
                    .offset(y: 5 * (1 - settle))
                    .frame(width: 236, alignment: .leading)
                    .position(x: 16.9 + 118, y: 25.5)
                    .accessibilityLabel(titles[leg])
                    .accessibilityValue("Step \(step) of 5, \(eta)")

                Text(eta)
                    .font(.system(size: 10.8, weight: .medium))
                    .tracking(-0.2)
                    .foregroundStyle(.white)
                    .fixedSize()
                    .frame(width: 120, alignment: .trailing)
                    .position(x: 338.6 - 60, y: 25.5)

                Text(initials)
                    .font(.system(size: 12, weight: .semibold))
                    .tracking(0.3)
                    .foregroundStyle(.white)
                    .fixedSize()
                    .position(x: 16.1 + 15.7, y: 116.6)

                Text(driver)
                    .font(.system(size: 13.2, weight: .medium))
                    .tracking(-0.3)
                    .foregroundStyle(.white)
                    .fixedSize()
                    .frame(width: 170, alignment: .leading)
                    .position(x: 59 + 85, y: 111.3)

                HStack(spacing: 7) {
                    Text(vehicle)
                    Rectangle()
                        .fill(Color.rgba(167, 188, 220, 0.35))
                        .frame(width: 1, height: 11)
                    Text(plate)
                }
                .font(.system(size: 10.8))
                .tracking(-0.2)
                .foregroundStyle(Color(hex: 0xa7bcdc))
                .fixedSize()
                .frame(width: 176, alignment: .leading)
                .position(x: 59 + 88, y: 124.8)

                pill(icon: phoneGlyph, label: "Call \(driver)", x: 238.2, width: 48.1, action: onCallPress)
                pill(icon: bubbleGlyph, label: "Message \(driver)", x: 292.4, width: 47, action: onMessagePress)
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
            held.retarget(target, duration: reduceMotion ? 0.2 : 1.4, curve: Ease.out)
            settling = true
        }
        .task(id: settling) {
            guard settling else { return }
            try? await Task.sleep(for: .seconds(1.5))
            settling = false
        }
    }

    private func pill(icon: Path, label: String, x: Double, width: Double, action: (() -> Void)?) -> some View {
        Button {
            action?()
        } label: {
            icon
                .offsetBy(dx: width / 2, dy: 23.9 / 2)
                .fill(.white)
                .frame(width: width, height: 23.9)
                .background(Color.white.opacity(0.26), in: Capsule())
                .overlay(Capsule().strokeBorder(Color.white.opacity(0.16), lineWidth: 1))
        }
        .buttonStyle(PillStyle())
        .accessibilityLabel(label)
        .position(x: x + width / 2, y: 108.7 + 23.9 / 2)
    }

    private struct PillStyle: ButtonStyle {
        func makeBody(configuration: Configuration) -> some View {
            configuration.label
                .scaleEffect(configuration.isPressed ? 0.94 : 1)
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

    private func draw(_ context: GraphicsContext, value: Double, time: Double, popped: Int, sincePop: Double) {
        let x = Metrics.bleed
        let y = Metrics.bleed
        let ambient = reduceMotion ? 0.0 : 1.0
        let cardRect = CGRect(x: x, y: y, width: Metrics.width, height: Metrics.height)
        let lx = x + Metrics.stepX
        let cy = y + Metrics.stepY
        let lineHeight = Metrics.lineHeight
        let p = clamp01(value)

        // Soft shadow under the card.
        var shadow = context
        shadow.addFilter(.blur(radius: 18))
        shadow.fill(
            Path(
                roundedRect: CGRect(x: x + 14, y: y + 26, width: Metrics.width - 28, height: Metrics.height - 16),
                cornerRadius: Metrics.radius, style: .circular
            ),
            with: .color(.rgba(20, 50, 110, 0.42))
        )
        context.fill(
            Path(roundedRect: cardRect, cornerRadius: Metrics.radius, style: .circular),
            with: .linearGradient(
                Gradient(stops: [
                    .init(color: Color(hex: 0x01030a), location: 0),
                    .init(color: Color(hex: 0x03112e), location: 0.3),
                    .init(color: Color(hex: 0x09306f), location: 0.75),
                    .init(color: Color(hex: 0x0c4296), location: 1),
                ]),
                startPoint: CGPoint(x: x, y: y), endPoint: CGPoint(x: x, y: y + Metrics.height)
            )
        )
        // Glass edge: a light rim that grows towards the bottom.
        context.stroke(
            Path(roundedRect: cardRect.insetBy(dx: 0.5, dy: 0.5), cornerRadius: Metrics.radius - 0.5, style: .circular),
            with: .linearGradient(
                Gradient(colors: [.rgba(120, 170, 255, 0), .rgba(120, 170, 255, 0.5)]),
                startPoint: CGPoint(x: x, y: y + Metrics.height * 0.35), endPoint: CGPoint(x: x, y: y + Metrics.height)
            ),
            lineWidth: 1
        )

        // Route: dim line, lit part, glowing head.
        let head = lx + Metrics.span * p
        context.fill(
            capsule(CGRect(x: lx, y: cy - lineHeight / 2, width: Metrics.span, height: lineHeight)),
            with: .color(Color(hex: 0x0d3d92))
        )
        // The bright head hides while it sits under a step's dot.
        let off = abs(p * 4 - (p * 4).rounded()) * Metrics.leg
        let headShown = clamp01((off - 9) / 7)
        if headShown > 0 {
            var glow = context
            glow.addFilter(.blur(radius: 5))
            glow.fill(
                capsule(CGRect(x: head - 30, y: cy - lineHeight / 2, width: 32, height: lineHeight)),
                with: .color(Color(hex: 0x6fdcff, opacity: headShown * (0.85 + ambient * 0.15 * sin(time * 3))))
            )
        }
        context.fill(
            capsule(CGRect(x: lx, y: cy - lineHeight / 2, width: head - lx + lineHeight / 2, height: lineHeight)),
            with: .linearGradient(
                Gradient(stops: [
                    .init(color: Metrics.blue, location: 0),
                    .init(color: Color(hex: 0x7fe4ff), location: 0.65),
                    .init(color: Color(hex: 0xf2feff), location: 1),
                ]),
                startPoint: CGPoint(x: head - 46, y: cy), endPoint: CGPoint(x: head, y: cy)
            )
        )
        // Pulses of light run from the last step to the head.
        if ambient > 0, headShown > 0 {
            let pulseAt = (time * 0.8).truncatingRemainder(dividingBy: 1)
            let from = lx + Metrics.leg * floor(p * 4 - 0.0001) + Metrics.dotRadius
            var pulse = context
            pulse.addFilter(.blur(radius: 1.2))
            pulse.fill(
                capsule(CGRect(x: from + (head - 8 - from) * pulseAt - 6, y: cy - 1.2, width: 12, height: 2.4)),
                with: .color(.white.opacity(headShown * 0.9 * sin(.pi * pulseAt)))
            )
        }

        for index in 0..<5 {
            let on = lit(p, index)
            let since = popped == index ? sincePop : 10
            var dot = context
            dot.translateBy(x: lx + Metrics.leg * Double(index), y: cy)
            // Reaching a step pops its dot with a little overshoot and sends a ring out.
            let u = clamp01(since / 0.55)
            let scale = 1 + ambient * 0.5 * sin(.pi * u) * (1 - u)
            dot.scaleBy(x: scale, y: scale)
            let spread = since / 0.85
            if ambient > 0, spread < 1 {
                dot.stroke(
                    circle(r: Metrics.dotRadius + 15 * easeOutCubic(spread)),
                    with: .color(Color(hex: 0x7fd0ff, opacity: 0.8 * (1 - spread))), lineWidth: 1.5
                )
            }
            // The step the driver left last keeps a halo that breathes.
            let current = legIndex(p) == index || (index == 4 && p > 0.99) ? 1 : 0.35
            var halo = dot
            halo.addFilter(.blur(radius: 6))
            halo.fill(
                circle(r: Metrics.dotRadius + 2),
                with: .color(Metrics.blue.opacity(on * current * (0.5 + ambient * 0.14 * sin(time * 2.2))))
            )
            dot.fill(circle(r: Metrics.dotRadius), with: .color(mix((14, 63, 148), (36, 144, 255), on)))
            dot.stroke(circle(r: Metrics.dotRadius - 0.5), with: .color(.rgba(96, 146, 232, 0.5 * (1 - on))), lineWidth: 1)
            dot.stroke(
                stepGlyphs[index], with: .color(mix((63, 116, 198), (255, 255, 255), on)),
                style: StrokeStyle(lineWidth: 1.1, lineCap: .round, lineJoin: .round)
            )
        }

        // Driver's avatar tile.
        let avatar = CGRect(x: x + 16.1, y: y + 100.9, width: 31.4, height: 31.4)
        context.fill(
            Path(roundedRect: avatar, cornerRadius: 9, style: .circular),
            with: .linearGradient(
                Gradient(colors: [Color(hex: 0x7b8fa8), Color(hex: 0x46586f)]),
                startPoint: CGPoint(x: x + 16, y: y + 101), endPoint: CGPoint(x: x + 47, y: y + 132)
            )
        )
        context.stroke(
            Path(roundedRect: avatar.insetBy(dx: 0.5, dy: 0.5), cornerRadius: 8.5, style: .circular),
            with: .color(.white.opacity(0.22)), lineWidth: 1
        )
    }
}

private enum Metrics {
    static let width = 356.0
    static let height = 148.0
    static let radius = 22.0
    /// The canvas overflows the card by this much on every side to fit its shadow.
    static let bleed = 44.0

    static let stepY = 67.6
    static let stepX = 28.2
    static let leg = 74.8
    static let span = leg * 4
    static let dotRadius = 12.0
    static let lineHeight = 6.0

    static let blue = Color(hex: 0x2490ff)

    /// The reference shows the driver 60% of the way to the second step; the autoPlay loop built around it.
    static let rest = 0.15
    /// Seconds, from, to.
    static let segments: [(Double, Double, Double)] = [
        (0.5, 0, 0), (1.5, 0, rest), (2.8, rest, rest), (0.8, rest, 0.25), (0.6, 0.25, 0.25), (1.1, 0.25, 0.5),
        (0.6, 0.5, 0.5), (1.1, 0.5, 0.75), (0.6, 0.75, 0.75), (1.1, 0.75, 1), (2.2, 1, 1), (0.8, 1, 0),
    ]
    static let loop = segments.reduce(0) { $0 + $1.0 }
    /// A moment in the loop where everything is at rest at the reference value: used when nothing is playing.
    static let restTime = 0.5 + 1.5 + 1.4
}

private func clamp01(_ t: Double) -> Double { min(1, max(0, t)) }

private func easeInOutCubic(_ t: Double) -> Double {
    t < 0.5 ? 4 * t * t * t : 1 - pow(-2 * t + 2, 3) / 2
}

private func easeOutCubic(_ t: Double) -> Double { 1 - pow(1 - clamp01(t), 3) }

/// Position along the steps at `t` seconds into the autoPlay loop.
private func loopValue(_ t: Double) -> Double {
    var at = t
    for (length, from, to) in Metrics.segments {
        if at < length { return from + (to - from) * easeInOutCubic(at / length) }
        at -= length
    }
    return 0
}

/// Which headline fits the position: the leg being travelled, or 4 once the last step is reached.
private func legIndex(_ p: Double) -> Int {
    min(4, Int(clamp01(p) * 4 + 0.004))
}

/// 0…1: how lit step `index` is.
private func lit(_ p: Double, _ index: Int) -> Double {
    index == 0 ? 1 : clamp01((p - (Double(index) / 4 - 0.012)) / 0.012)
}

private func mix(_ a: (Double, Double, Double), _ b: (Double, Double, Double), _ t: Double) -> Color {
    .rgba(a.0 + (b.0 - a.0) * t, a.1 + (b.1 - a.1) * t, a.2 + (b.2 - a.2) * t, 1)
}

private func circle(r: Double) -> Path {
    Path(ellipseIn: CGRect(x: -r, y: -r, width: r * 2, height: r * 2))
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

// Step glyphs, drawn around the dot's centre: parcel, garment, washer, van, home.
private let stepGlyphs = [
    "M0 -5.4L4.7 -2.7V2.7L0 5.4L-4.7 2.7V-2.7ZM-4.7 -2.7L0 0L4.7 -2.7M0 0V5.4",
    "M-2 -4.9L-5.4 -2.9L-4.1 -0.5L-2.9 -1.2V4.9H2.9V-1.2L4.1 -0.5L5.4 -2.9L2 -4.9C1.4 -3.5 -1.4 -3.5 -2 -4.9Z",
    "M-3.6 -5.2H3.6Q4.8 -5.2 4.8 -4V4Q4.8 5.2 3.6 5.2H-3.6Q-4.8 5.2 -4.8 4V-4Q-4.8 -5.2 -3.6 -5.2ZM-2.9 -3.2H-1.5M0 -1.5C1.5 -1.5 2.6 -0.4 2.6 1.1C2.6 2.6 1.5 3.7 0 3.7C-1.5 3.7 -2.6 2.6 -2.6 1.1C-2.6 -0.4 -1.5 -1.5 0 -1.5Z",
    "M-5.6 -3.6H0.8V2.6H-5.6ZM0.8 -1.4H3.5L5.6 0.8V2.6H0.8M-3.6 2.6C-3.6 3.5 -3 4.1 -2.2 4.1C-1.4 4.1 -0.8 3.5 -0.8 2.6M2 2.6C2 3.5 2.6 4.1 3.4 4.1C4.2 4.1 4.8 3.5 4.8 2.6",
    "M-5.4 -0.4L0 -5.1L5.4 -0.4M-3.9 -1.5V4.9H3.9V-1.5M-1.2 4.9V1.7H1.2V4.9",
].map(svgPath)

private let phoneGlyph = svgPath(
    "M-3 -5.2C-2.4 -5.4 -1.8 -5.1 -1.6 -4.5L-0.8 -2.4C-0.6 -1.9 -0.8 -1.4 -1.2 -1.1L-2.1 -0.4C-1.3 1.2 -0.1 2.4 1.5 3.2L2.2 2.3C2.5 1.9 3 1.7 3.5 1.9L5.6 2.7C6.2 2.9 6.5 3.5 6.3 4.1C5.9 5.4 4.8 6 3.6 5.8C-1.1 5 -4.7 1.4 -5.5 -3.3C-5.7 -4.2 -4.3 -4.8 -3 -5.2Z"
)
private let bubbleGlyph = svgPath(
    "M-4.3 -4.8H4.3Q5.7 -4.8 5.7 -3.4V1.6Q5.7 3 4.3 3H-1.5L-4.3 5.4V3Q-5.7 3 -5.7 1.6V-3.4Q-5.7 -4.8 -4.3 -4.8Z"
)

#Preview {
    ZStack {
        Color(hex: 0xfdfdfd).ignoresSafeArea()
        DeliveryStepsProgress(autoPlay: true)
    }
}

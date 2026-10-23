import SwiftUI

/// White "Summary" card with a thick green usage ring on a soft grey dial with tick dots, the
/// percentage and the used / total amount in its raised hub and the renewal line below. The arc
/// is drawn with a bright leading cap and a haze that stretches ahead of it, each tick swells as
/// the head reaches it, the hub figure pops and sends a ripple across the dial when the arc
/// lands, then a sheen glides along the arc and a slow pulse keeps going round the ticks.
struct DataUsageRing: View {
    /// Controlled usage, 0…1 of the plan. Leave `nil` to show the reference's 36% (or the autoPlay loop).
    var progress: Double? = nil
    /// Draws the ring from 0 to 36% by itself, holds, unwinds and starts over.
    var autoPlay = false
    var title = "Summary"
    /// Size of the plan in GB; the used amount is derived from the progress.
    var totalGB = 10
    var renews = "Renews 1st August"
    var editLabel = "Edit"
    var onEdit: () -> Void = {}

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    /// Origin of the ambient clock (sheen, ticking).
    @State private var epoch = Date.now
    /// When the current autoPlay / tapped run started; `nil` shows the resting frame.
    @State private var runStart: Date? = nil
    @State private var held = Tween(Metrics.rest)
    /// When a controlled value last changed.
    @State private var movedAt = Date.distantPast
    /// Keeps the timeline running for a controlled change under reduced motion.
    @State private var settling = false

    private var controlled: Bool { progress != nil }
    private var target: Double { clamp01(progress ?? Metrics.rest) }

    var body: some View {
        TimelineView(.animation(paused: reduceMotion && !settling)) { timeline in
            let date = timeline.date
            let time = reduceMotion ? 0 : date.timeIntervalSince(epoch)
            let clock = clock(at: date)
            let value = clamp01(controlled ? held.value(at: date) : loopValue(clock))
            let percent = Int((value * 100).rounded())
            let used = String(format: "%.1f", Double(percent) / 100 * Double(totalGB))
            let landed = landed(at: date, clock: clock)

            ZStack {
                Canvas { context, _ in
                    draw(context, value: value, time: time, activity: activity(at: date, clock: clock), landed: landed)
                }
                .padding(-Metrics.bleed)
                .allowsHitTesting(false)

                content(percent: percent, used: used, landed: landed)
                    .accessibilityElement(children: .ignore)
                    .accessibilityLabel("\(title), \(used)GB of \(totalGB)GB used")
                    .accessibilityValue("\(percent)%")

                HStack(spacing: 0) {
                    Text(renews)
                    Text("•")
                        .font(.system(size: 9))
                        .padding(.horizontal, 4)
                    Button(action: onEdit) {
                        Text(editLabel)
                            .fontWeight(.bold)
                            .underline()
                    }
                    .buttonStyle(EditStyle())
                }
                .font(.system(size: 11.6))
                .kerning(-0.2)
                .foregroundStyle(Color(hex: 0x111111))
                .fixedSize()
                .position(x: Metrics.width / 2, y: 333)
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
            movedAt = .now
            held.retarget(target, duration: reduceMotion ? 0.2 : 0.8, curve: Ease.out)
            settling = true
        }
        .task(id: settling) {
            guard settling else { return }
            try? await Task.sleep(for: .seconds(1.6))
            settling = false
        }
    }

    private func sync() {
        runStart = autoPlay && !controlled && !reduceMotion ? .now : nil
        if held.duration == 0 { held = Tween(target) }
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

    /// 0…1: how fast the head of the arc is travelling.
    private func activity(at date: Date, clock: Double) -> Double {
        if reduceMotion { return 0 }
        if !controlled { return loopActivity(clock) }
        let u = date.timeIntervalSince(movedAt) / 0.7
        return u <= 0 || u >= 1 ? 0 : sin(.pi * u)
    }

    /// Seconds since the arc landed on its value; large when nothing happened.
    private func landed(at date: Date, clock: Double) -> Double {
        if reduceMotion { return 10 }
        if !controlled { return clock < Metrics.loop - Metrics.reset ? clock - Metrics.landed : 10 }
        return date.timeIntervalSince(movedAt) - 0.6
    }

    private func content(percent: Int, used: String, landed: Double) -> some View {
        // The hub figure swells as the arc lands.
        let u = clamp01(landed / 0.5)
        let pop = u <= 0 || u >= 1 ? 0 : sin(.pi * u) * (1 - u)

        return ZStack {
            Text(title)
                .font(.system(size: 19, weight: .bold))
                .kerning(-0.3)
                .foregroundStyle(Color(hex: 0x232323))
                .lineLimit(1)
                .fixedSize()
                .frame(width: 240, alignment: .leading)
                .position(x: 17 + 120, y: 32.5)

            HStack(alignment: .firstTextBaseline, spacing: 1.5) {
                Text("\(percent)")
                    .font(.system(size: 48, weight: .bold))
                    .kerning(-1.2)
                    .monospacedDigit()
                Text("%")
                    .font(.system(size: 11.5, weight: .bold))
            }
            .foregroundStyle(.black)
            .fixedSize()
            .scaleEffect(1 + 0.16 * pop)
            .position(x: Metrics.centerX + 2, y: Metrics.centerY - 13)

            (Text("\(used)GB").fontWeight(.bold).foregroundStyle(.black)
                + Text(" / \(totalGB)GB").foregroundStyle(Color(hex: 0x9a9a9a)))
                .font(.system(size: 9.8))
                .kerning(-0.2)
                .lineLimit(1)
                .fixedSize()
                .position(x: Metrics.centerX, y: Metrics.centerY + 19.5)
        }
        .frame(width: Metrics.width, height: Metrics.height)
    }

    private func draw(_ base: GraphicsContext, value: Double, time: Double, activity: Double, landed: Double) {
        let ambient = reduceMotion ? 0.0 : 1.0
        let width = Metrics.width
        let height = Metrics.height
        var context = base
        context.translateBy(x: Metrics.bleed, y: Metrics.bleed)

        let card = CGRect(x: 0, y: 0, width: width, height: height)
        var shadow = context
        shadow.addFilter(.blur(radius: 14))
        shadow.fill(
            Path(roundedRect: CGRect(x: 4, y: 10, width: width - 8, height: height - 8), cornerRadius: Metrics.radius, style: .circular),
            with: .color(.black.opacity(0.1))
        )
        context.fill(Path(roundedRect: card, cornerRadius: Metrics.radius, style: .circular), with: .color(.white))
        context.stroke(
            Path(roundedRect: card.insetBy(dx: 0.5, dy: 0.5), cornerRadius: Metrics.radius - 0.5, style: .circular),
            with: .color(Color(hex: 0xe7e7e7)), lineWidth: 1
        )

        let center = CGPoint(x: Metrics.centerX, y: Metrics.centerY)
        let outer = Metrics.outer
        let inner = Metrics.inner
        let thick = outer - inner
        func circle(_ c: CGPoint, _ r: Double) -> Path {
            Path(ellipseIn: CGRect(x: c.x - r, y: c.y - r, width: r * 2, height: r * 2))
        }

        // Dial: a shallow grey groove, darker along its upper left rim.
        context.fill(
            circle(center, outer),
            with: .linearGradient(
                Gradient(colors: [Color(hex: 0xe2e2e2), Color(hex: 0xededed), Color(hex: 0xfbfbfb)]),
                startPoint: CGPoint(x: center.x - outer * 0.7, y: center.y - outer * 0.7),
                endPoint: CGPoint(x: center.x + outer * 0.7, y: center.y + outer * 0.7)
            )
        )
        var dial = context
        dial.clip(to: circle(center, outer))
        dial.drawLayer { layer in
            layer.addFilter(.blur(radius: 5))
            layer.stroke(circle(CGPoint(x: center.x + 3, y: center.y + 4), outer + 3), with: .color(.black.opacity(0.17)), lineWidth: 7)
        }

        let sweep = value * 360
        let shown = clamp01(value / 0.012)

        // Ticks: one swells and turns green as the head of the arc reaches it, and the free ones
        // are swept by a slow pulse that keeps going round the dial.
        let pulseAt = time.truncatingRemainder(dividingBy: Metrics.tickPeriod) / 1.5 * 360
        for index in 0..<Metrics.ticks {
            let angle = Double(index) * 360 / Double(Metrics.ticks)
            let a = (angle - 90) * .pi / 180
            let point = CGPoint(x: center.x + Metrics.tickRadius * cos(a), y: center.y + Metrics.tickRadius * sin(a))
            let reached = activity * clamp01(1 - abs(angle - sweep - 7) / 11)
            let pulse = ambient * (pulseAt < 360 ? clamp01(1 - abs(angle - pulseAt) / 26) : 0)
            let half = 1 + 1.5 * reached + 0.7 * pulse
            let dot = Path(
                roundedRect: CGRect(x: point.x - half, y: point.y - half, width: half * 2, height: half * 2),
                cornerRadius: 0.4 * half, style: .circular
            )
            dial.fill(dot, with: .color(Color(hex: 0xb7b7b7, opacity: 0.75 + 0.25 * pulse)))
            if reached > 0 { dial.fill(dot, with: .color(Color(hex: 0x2fcf50, opacity: reached))) }
        }

        // Soft green haze before the start and past the end of the arc; it stretches ahead while the arc moves.
        var haze = dial
        haze.opacity = 0.9 * shown
        haze.drawLayer { layer in
            layer.addFilter(.blur(radius: 11))
            layer.stroke(
                arc(center, from: -90 - 30, to: -90 + sweep + 38 + 26 * activity),
                with: .color(.rgba(84, 226, 104, 0.3)), lineWidth: thick + 10
            )
            layer.stroke(
                arc(center, from: -90 - 12, to: -90 + sweep + 15 + 20 * activity),
                with: .color(.rgba(84, 226, 104, 0.45)), lineWidth: thick + 10
            )
        }

        // The arc itself: a sector with rounded corners, slightly deeper green towards both rims.
        let solid = sector(center, from: -90, to: -90 + sweep)
        let green = GraphicsContext.Shading.conicGradient(
            Gradient(stops: [
                .init(color: Color(hex: 0x35d655), location: 0.017),
                .init(color: Color(hex: 0x58ee6c), location: 0.2),
                .init(color: Color(hex: 0x42de60), location: 0.39),
                .init(color: Color(hex: 0x42de60), location: 1),
            ]),
            center: center, angle: .degrees(Metrics.seam)
        )
        let rims = GraphicsContext.Shading.radialGradient(
            Gradient(stops: [
                .init(color: .rgba(20, 150, 54, 0.2), location: inner / outer),
                .init(color: .rgba(20, 150, 54, 0), location: inner / outer + 0.09),
                .init(color: .rgba(20, 150, 54, 0), location: 0.93),
                .init(color: .rgba(20, 150, 54, 0.2), location: 1),
            ]),
            center: center, startRadius: 0, endRadius: outer
        )
        let rounded = StrokeStyle(lineWidth: Metrics.corner * 2, lineJoin: .round)
        var band = dial
        band.opacity = shown
        band.fill(solid, with: green)
        band.stroke(solid, with: green, style: rounded)
        band.stroke(solid, with: rims, style: rounded)
        band.fill(solid, with: rims)

        // A glassy sheen that glides along the arc every few seconds.
        let sheenPhase = time.truncatingRemainder(dividingBy: 4.2) / 1.3
        if ambient > 0, sheenPhase < 1, shown > 0 {
            let at = -90 + (sweep + 30) * sheenPhase - 15
            var sheen = dial
            sheen.opacity = shown * 0.3 * sin(.pi * sheenPhase)
            sheen.drawLayer { layer in
                layer.addFilter(.blur(radius: 7))
                layer.stroke(
                    arc(center, from: max(-90, at - 13), to: min(-90 + sweep, at + 13)),
                    with: .color(.white), lineWidth: thick - 10
                )
            }
        }
        // Bright leading cap while the arc is being drawn.
        if activity * shown > 0 {
            var cap = dial
            cap.opacity = activity * shown
            cap.drawLayer { layer in
                layer.addFilter(.blur(radius: 2.5))
                layer.stroke(
                    arc(center, from: -90 + sweep - 5, to: -90 + sweep - 0.5),
                    with: .color(.rgba(214, 255, 220, 0.85)), lineWidth: thick - 8
                )
            }
        }

        // A ripple leaves the hub when the arc lands.
        let rippleU = landed / 0.9
        if rippleU > 0, rippleU < 1 {
            var ripple = dial
            ripple.opacity = 0.55 * (1 - rippleU)
            ripple.drawLayer { layer in
                layer.addFilter(.blur(radius: 1.5))
                layer.stroke(circle(center, inner + 2 + 44 * (1 - pow(1 - rippleU, 3))), with: .color(.white), lineWidth: 2.5)
            }
        }

        // Raised white hub with its shadow falling on the groove.
        dial.drawLayer { layer in
            layer.addFilter(.blur(radius: 8))
            layer.fill(circle(CGPoint(x: center.x - 2, y: center.y + 6), inner), with: .color(.black.opacity(0.26)))
        }
        context.fill(circle(center, inner), with: .color(.white))
    }

    /// Ring sector between two angles (degrees clockwise from 3 o'clock), inset by the corner radius.
    private func sector(_ center: CGPoint, from: Double, to: Double) -> Path {
        let inset = Metrics.corner / Metrics.middle * 180 / .pi
        let a0 = from + inset
        let a1 = max(a0 + 0.01, to - inset)
        var path = Path()
        path.addArc(center: center, radius: Metrics.outer - Metrics.corner, startAngle: .degrees(a0), endAngle: .degrees(a1), clockwise: false)
        path.addArc(center: center, radius: Metrics.inner + Metrics.corner, startAngle: .degrees(a1), endAngle: .degrees(a0), clockwise: true)
        path.closeSubpath()
        return path
    }

    /// Open arc along the middle of the ring.
    private func arc(_ center: CGPoint, from: Double, to: Double) -> Path {
        let sweep = min(359.5, max(0.01, to - from))
        var path = Path()
        path.addArc(center: center, radius: Metrics.middle, startAngle: .degrees(from), endAngle: .degrees(from + sweep), clockwise: false)
        return path
    }
}

private enum Metrics {
    static let width = 280.0
    static let height = 366.0
    static let radius = 11.0
    /// The canvas overflows the card by this much on every side to fit its shadow.
    static let bleed = 30.0

    static let centerX = 140.0
    static let centerY = 180.5
    static let outer = 116.0
    static let inner = 69.0
    static let middle = (outer + inner) / 2
    /// The arc's corners are rounded by stroking a slightly smaller sector with round joins.
    static let corner = 3.0
    static let ticks = 12
    static let tickRadius = 95.8
    /// Conic gradients start at 3 o'clock; this turns the seam to just before 12 o'clock.
    static let seam = -96.0

    /// Value shown in the reference, and the autoPlay loop built around it (seconds).
    static let rest = 0.36
    static let start = 0.45
    static let draw = 1.9
    static let hold = 3.9
    static let reset = 0.75
    static let loop = start + draw + hold + reset
    static let landed = start + draw
    /// A moment in the loop where everything is at rest: used when nothing is playing.
    static let restTime = landed + 1.6
    static let tickPeriod = 3.4
}

private func clamp01(_ t: Double) -> Double { min(1, max(0, t)) }

private func easeInOutCubic(_ t: Double) -> Double {
    t < 0.5 ? 4 * t * t * t : 1 - pow(-2 * t + 2, 3) / 2
}

/// Usage at `t` seconds into the autoPlay loop.
private func loopValue(_ t: Double) -> Double {
    if t < Metrics.start { return 0 }
    if t < Metrics.landed { return Metrics.rest * easeInOutCubic((t - Metrics.start) / Metrics.draw) }
    if t < Metrics.loop - Metrics.reset { return Metrics.rest }
    return Metrics.rest * (1 - easeInOutCubic((t - (Metrics.loop - Metrics.reset)) / Metrics.reset))
}

private func loopActivity(_ t: Double) -> Double {
    let u = (t - Metrics.start) / Metrics.draw
    return u <= 0 || u >= 1 ? 0 : sin(.pi * u)
}

/// Underlined text link; dims and scales down while pressed.
private struct EditStyle: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .contentShape(Rectangle().inset(by: -12))
            .scaleEffect(configuration.isPressed ? 0.97 : 1)
            .opacity(configuration.isPressed ? 0.6 : 1)
            .animation(.timingCurve(0.23, 1, 0.32, 1, duration: 0.13), value: configuration.isPressed)
            .sensoryFeedback(.impact(weight: .light), trigger: configuration.isPressed) { _, pressed in pressed }
    }
}

#Preview {
    ZStack {
        Color(hex: 0xf4f4f4).ignoresSafeArea()
        DataUsageRing(autoPlay: true)
    }
}

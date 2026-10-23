import SwiftUI

/// Live-activity style route card: a train glyph and line name, a yellow chip with the time to the
/// next stop, and a lime route bar with three stations and a ringed marker for the train. Chevrons
/// run ahead of the train while it moves, each arrival pings (rings from the marker, the dot swells,
/// the station's name lights up), and the chip's text blurs into its next state.
struct TransitRouteProgress: View {
    /// Controlled position on the route, 0…1: 0 is the first station, 0.5 the second, 1 the third.
    var progress: Double? = nil
    /// Rides from the first station to the second (the reference frame), on to the third, and starts over.
    var autoPlay = false
    var line = "Circle Line"
    var stations = ["Harbor", "Midtown", "Parkway"]
    /// Chip text when the next stop is far, near, and reached.
    var eta = ["2 min", "1 min", "now"]

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    /// Origin of the ambient clock (chevrons, breathing ring).
    @State private var epoch = Date.now
    /// When the current autoPlay / tapped run started; `nil` shows the resting frame.
    @State private var runStart: Date? = nil
    @State private var held = Tween(Metrics.rest)
    /// Keeps the timeline running for a controlled change under reduced motion.
    @State private var settling = false
    @State private var journey = Journey()

    /// Per-frame state that depends on how the value has been moving.
    private final class Journey {
        var date: Date? = nil
        var before = Metrics.rest
        /// 0…1, smoothed: is the train moving.
        var moving = 0.0
        /// When the train last pulled into a station.
        var arrivedAt = Date.distantPast
        /// Which chip text is showing, and since when.
        var chip = etaIndex(Metrics.rest)
        var chipChangedAt = Date.distantPast

        func advance(to now: Date, value: Double) {
            let dt = min(max(now.timeIntervalSince(date ?? now), 0), 0.064)
            let first = date == nil
            date = now
            let step = value - before
            if dt > 0 {
                moving += ((abs(step) > 0.00002 ? 1 : 0) - moving) * min(1, dt * 8)
            }
            // Pulling into the second or third station sets off the ping.
            if (before < 0.4995 && value >= 0.4995) || (before < 0.9995 && value >= 0.9995) { arrivedAt = now }
            // On the way back to the start the chip already shows the full wait.
            let next = step < 0 ? 0 : etaIndex(value)
            if next != chip {
                chip = next
                if !first { chipChangedAt = now }
            }
            before = value
        }
    }

    private var controlled: Bool { progress != nil }
    private var target: Double { min(1, max(0, progress ?? Metrics.rest)) }

    var body: some View {
        TimelineView(.animation(paused: reduceMotion && !settling)) { timeline in
            let date = timeline.date
            let time = reduceMotion ? 0 : date.timeIntervalSince(epoch)
            let value = controlled ? held.value(at: date) : loopValue(clock(at: date))
            let _ = journey.advance(to: date, value: value)
            let chip = reduceMotion ? etaIndex(value) : journey.chip
            // 0 → 1 after the chip's text changes: it blurs in.
            let settle = reduceMotion ? 1 : Ease.out.value(at: clamp01(date.timeIntervalSince(journey.chipChangedAt) / 0.52))
            let stop = Int((value * 2).rounded())

            ZStack {
                Canvas { context, size in
                    draw(
                        context, value: value, time: time, moving: reduceMotion ? 0 : journey.moving,
                        sinceArrival: reduceMotion ? 10 : date.timeIntervalSince(journey.arrivedAt)
                    )
                }
                .padding(-Metrics.bleed)

                Text(line)
                    .font(.system(size: 15, weight: .medium))
                    .foregroundStyle(Metrics.text)
                    .fixedSize()
                    .frame(width: 180, alignment: .leading)
                    .position(x: 63.5 + 90, y: 37.8)

                Text(eta[chip])
                    .font(.system(size: 12.5, weight: .semibold))
                    .monospacedDigit()
                    .foregroundStyle(Color(hex: 0x4d4500))
                    .opacity(0.25 + 0.75 * settle)
                    .scaleEffect(0.86 + 0.14 * settle)
                    .blur(radius: 5 * (1 - settle))
                    .frame(width: 50.9, height: 24.4)
                    .background(Color(hex: 0xf2e300), in: Capsule())
                    .position(x: 255 + 25.45, y: 27.4 + 12.2)

                ForEach(0..<3, id: \.self) { index in
                    // A station's name lights up as the train pulls in.
                    let lit = clamp01((value - (Double(index) * 0.5 - 0.07)) / 0.07)
                    Text(stations[index])
                        .font(.system(size: 13.5, weight: .medium))
                        .foregroundStyle(Color.rgba(90 + 90 * lit, 138 + 107 * lit, 37 + 71 * lit, 1))
                        .fixedSize()
                        .frame(width: 120, alignment: index == 0 ? .leading : index == 1 ? .center : .trailing)
                        .position(
                            x: index == 0 ? 26 + 60 : index == 1 ? Metrics.barX + Metrics.barWidth / 2
                                : Metrics.barX + Metrics.barWidth - 2.5 - 60,
                            y: 100.2
                        )
                }
            }
            .lineLimit(1)
            .frame(width: Metrics.width, height: Metrics.height)
            .accessibilityElement(children: .ignore)
            .accessibilityLabel(line)
            .accessibilityValue("\(stations[stop]), \(eta[chip])")
        }
        .contentShape(RoundedRectangle(cornerRadius: Metrics.radius))
        .onTapGesture { restart() }
        .onAppear { sync() }
        .onChange(of: autoPlay) { sync() }
        .onChange(of: reduceMotion) { sync() }
        .onChange(of: progress) {
            sync()
            guard controlled else { return }
            held.retarget(target, duration: reduceMotion ? 0.2 : 1.2, curve: Ease.out)
            settling = true
        }
        .task(id: settling) {
            guard settling else { return }
            try? await Task.sleep(for: .seconds(1.3))
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

    private func draw(_ context: GraphicsContext, value: Double, time: Double, moving: Double, sinceArrival: Double) {
        let x = Metrics.bleed
        let y = Metrics.bleed
        let ambient = reduceMotion ? 0.0 : 1.0
        let card = CGRect(x: x, y: y, width: Metrics.width, height: Metrics.height)
        let bx = x + Metrics.barX
        let cy = y + Metrics.barCenterY
        let barHeight = Metrics.barHeight

        // The device the live activity sits in: a pale body that fades out below the card.
        context.fill(
            Path(
                roundedRect: CGRect(x: x - 10, y: y - 10, width: card.width + 20, height: card.height + 80),
                cornerRadius: Metrics.radius + 10, style: .circular
            ),
            with: .linearGradient(
                Gradient(stops: [
                    .init(color: .rgba(250, 250, 250, 1), location: 0),
                    .init(color: .rgba(240, 240, 240, 0.9), location: 0.6),
                    .init(color: .rgba(231, 231, 231, 0), location: 1),
                ]),
                startPoint: CGPoint(x: x, y: y - 10),
                endPoint: CGPoint(x: x, y: y + card.height + 70)
            )
        )
        var shadow = context
        shadow.addFilter(.blur(radius: 16))
        shadow.fill(
            Path(
                roundedRect: CGRect(x: x + 10, y: y + 14, width: card.width - 20, height: card.height - 4),
                cornerRadius: Metrics.radius, style: .circular
            ),
            with: .color(.black.opacity(0.3))
        )
        context.fill(Path(roundedRect: card, cornerRadius: Metrics.radius, style: .circular), with: .color(.black))

        // Train glyph.
        var train = context
        train.translateBy(x: x + 27.2, y: y + 25.6)
        train.scaleBy(x: 0.98, y: 0.98)
        train.stroke(trainLegs, with: .color(Metrics.text), style: StrokeStyle(lineWidth: 2.7, lineCap: .round))
        train.fill(trainBody, with: .color(Metrics.text))
        train.fill(trainWindow, with: .color(.black))
        train.fill(circle(x: 8.3, y: 16.6, r: 1.9), with: .color(.black))
        train.fill(circle(x: 17.7, y: 16.6, r: 1.9), with: .color(.black))

        // Route: dim ahead, lit behind, a dot per station.
        let at = markerX(value)
        context.fill(
            capsule(CGRect(x: bx, y: y + Metrics.barY, width: Metrics.barWidth, height: barHeight)),
            with: .color(Color(hex: 0x2d6e05))
        )
        // Chevrons run from the marker to the next station while the train is on its way there.
        let from = at + Metrics.ringRadius + 4
        let room = barHeight / 2 + Metrics.span * (value < 0.5 ? 0.5 : 1) - 6 - from
        if room > 4, moving > 0.01 {
            for index in 0..<4 {
                let along = (time * 30 + Double(index) * 17).truncatingRemainder(dividingBy: room)
                var chevron = context
                chevron.translateBy(x: bx + from + along, y: cy)
                chevron.opacity = moving * 0.75 * clamp01(along / 8) * clamp01((room - along) / 8)
                chevron.stroke(
                    chevronPath, with: .color(Metrics.lime),
                    style: StrokeStyle(lineWidth: 1.7, lineCap: .round, lineJoin: .round)
                )
            }
        }
        // The lit part ends in a round cap centred on the marker.
        context.fill(
            capsule(CGRect(x: bx, y: y + Metrics.barY, width: at + barHeight / 2, height: barHeight)),
            with: .color(Metrics.lime)
        )
        for stop in [0.0, 0.5, 1.0] {
            context.fill(circle(x: bx + barHeight / 2 + Metrics.span * stop, y: cy, r: 3.2), with: .color(.black))
        }

        var marker = context
        marker.translateBy(x: bx + at, y: cy)
        // Each arrival sends two rings out from the marker.
        for delay in [0.0, 0.24] {
            let spread = (sinceArrival - delay) / 0.95
            if spread > 0, spread < 1 {
                marker.stroke(
                    circle(x: 0, y: 0, r: Metrics.ringRadius + 26 * easeOutCubic(spread)),
                    with: .color(Color(hex: 0x74ffab, opacity: 0.75 * (1 - spread))), lineWidth: 1.4
                )
            }
        }
        let ring = circle(x: 0, y: 0, r: Metrics.ringRadius + ambient * 0.7 * sin(time * 2.4))
        marker.fill(ring, with: .color(.rgba(120, 255, 170, 0.12)))
        var ringGlow = marker
        ringGlow.addFilter(.blur(radius: 3))
        ringGlow.stroke(ring, with: .color(.rgba(116, 255, 171, 0.55)), lineWidth: 3)
        marker.stroke(ring, with: .color(Color(hex: 0x8dffba)), lineWidth: 1.7)
        var dotGlow = marker
        dotGlow.addFilter(.blur(radius: 3))
        dotGlow.fill(circle(x: 0, y: 0, r: 7), with: .color(.white.opacity(0.6)))
        // The marker's dot swells for a moment when the train arrives.
        let swell = 1 + ambient * 0.38 * sin(.pi * clamp01(sinceArrival / 0.42))
        marker.fill(circle(x: 0, y: 0, r: 4.2 * swell), with: .color(.white))
    }
}

private enum Metrics {
    static let width = 340.0
    static let height = 134.0
    static let radius = 37.0
    /// The canvas overflows the card by this much on every side to fit the device body it sits in.
    static let bleed = 70.0

    static let barX = 27.2
    static let barY = 69.1
    static let barWidth = 278.3
    static let barHeight = 15.3
    static let barCenterY = barY + barHeight / 2
    static let ringRadius = 15.5
    /// Distance between the first and last station dots.
    static let span = barWidth - barHeight

    static let lime = Color(hex: 0xb2fe5e)
    static let text = Color(hex: 0xb4f56c)

    // The reference shows the train at the second station; the autoPlay loop built around it (seconds).
    static let rest = 0.5
    static let start = 0.5
    static let leg = 2.4
    static let hold = 3.0
    static let holdEnd = 1.8
    static let reset = 0.7
    static let t2 = start + leg + hold
    static let t3 = t2 + leg + holdEnd
    static let loop = t3 + reset
    /// A moment in the loop where everything is at rest at the second station: used when nothing is playing.
    static let restTime = start + leg + 1.2
}

private func clamp01(_ t: Double) -> Double { min(1, max(0, t)) }

private func easeInOutCubic(_ t: Double) -> Double {
    t < 0.5 ? 4 * t * t * t : 1 - pow(-2 * t + 2, 3) / 2
}

private func easeOutCubic(_ t: Double) -> Double { 1 - pow(1 - clamp01(t), 3) }

/// Position on the route at `t` seconds into the autoPlay loop.
private func loopValue(_ t: Double) -> Double {
    if t < Metrics.start { return 0 }
    if t < Metrics.start + Metrics.leg { return 0.5 * easeInOutCubic((t - Metrics.start) / Metrics.leg) }
    if t < Metrics.t2 { return 0.5 }
    if t < Metrics.t2 + Metrics.leg { return 0.5 + 0.5 * easeInOutCubic((t - Metrics.t2) / Metrics.leg) }
    if t < Metrics.t3 { return 1 }
    return 1 - easeInOutCubic((t - Metrics.t3) / Metrics.reset)
}

/// Marker centre, measured from the bar's left end.
private func markerX(_ p: Double) -> Double {
    Metrics.barHeight / 2 + Metrics.span * clamp01(p)
}

/// Which chip text fits the position: 0 far from the next stop, 1 near it, 2 there.
private func etaIndex(_ p: Double) -> Int {
    let leg = p <= 0.5 ? p / 0.5 : (p - 0.5) / 0.5
    return leg >= 0.985 ? 2 : leg >= 0.5 ? 1 : 0
}

private func circle(x: Double, y: Double, r: Double) -> Path {
    Path(ellipseIn: CGRect(x: x - r, y: y - r, width: r * 2, height: r * 2))
}

// Front of a train in a 26 × 28 box.
private let trainBody = Path(roundedRect: CGRect(x: 2, y: 1, width: 22, height: 21), cornerRadius: 6, style: .circular)
private let trainWindow = Path(roundedRect: CGRect(x: 6, y: 5, width: 14, height: 6.8), cornerRadius: 2.2, style: .circular)

private let trainLegs: Path = {
    var p = Path()
    p.move(to: CGPoint(x: 7.2, y: 22.4))
    p.addLine(to: CGPoint(x: 4.6, y: 26.4))
    p.move(to: CGPoint(x: 18.8, y: 22.4))
    p.addLine(to: CGPoint(x: 21.4, y: 26.4))
    return p
}()

private let chevronPath: Path = {
    var p = Path()
    p.move(to: CGPoint(x: -2, y: -3))
    p.addLine(to: CGPoint(x: 1.4, y: 0))
    p.addLine(to: CGPoint(x: -2, y: 3))
    return p
}()

#Preview {
    ZStack {
        Color(hex: 0xe7e7e7).ignoresSafeArea()
        TransitRouteProgress(autoPlay: true)
    }
}

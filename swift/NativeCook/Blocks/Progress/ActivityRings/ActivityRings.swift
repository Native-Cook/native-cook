import SwiftUI

/// Black "today" widget: sleep and heart-rate rows on the left, three thick concentric rings on the
/// right, each ending in a badge with its number, around a white disc with the day's score. The
/// rings draw one after another with a bright head, their badges ride along counting up and pop
/// when they land, a sheen keeps running along the finished rings, and the heart beats at its rate.
struct ActivityRings: View {
    /// Controlled share of the day's figures reached, 0…1: at 1 the rings show their full values.
    var progress: Double? = nil
    /// Draws the rings one after another up to the values shown in the reference, holds, and starts over.
    var autoPlay = false
    var title = "Today · 21 Feb"
    var sleep = "7h 2m"
    /// Change against yesterday, shown in green with an up arrow.
    var sleepChange = "12%"
    var sleepLabel = "Sleep time"
    var heartRate = "56 bpm"
    var heartLabel = "Avg. heart rate"
    /// Beats per minute the heart glyph pulses at.
    var bpm = 56.0
    /// The numbers the blue, green and red rings count up to.
    var values = [31, 84, 53]
    var score = 16
    var scoreLabel = "PAI"

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    /// Origin of the ambient clock (heartbeat, breathing, sheen).
    @State private var epoch = Date.now
    /// When the current autoPlay / tapped run started; `nil` shows the resting frame.
    @State private var runStart: Date? = nil
    @State private var held = Tween(Metrics.rest)
    /// Keeps the timeline running for a controlled change under reduced motion.
    @State private var settling = false
    @State private var landing = Landing()

    /// Per-frame state: when each ring last reached its full value.
    private final class Landing {
        var before: Double? = nil
        var at = [Date.distantPast, Date.distantPast, Date.distantPast]

        func advance(to now: Date, value: Double) {
            defer { before = value }
            guard let before else { return }
            // A ring reaching its full value sets off its badge's pop.
            for index in 0..<3 where ringAt(before, index) < 0.995 && ringAt(value, index) >= 0.995 {
                at[index] = now
            }
        }
    }

    private var controlled: Bool { progress != nil }
    private var target: Double { min(1, max(0, progress ?? Metrics.rest)) }

    var body: some View {
        TimelineView(.animation(paused: reduceMotion && !settling)) { timeline in
            let date = timeline.date
            let time = reduceMotion ? 0 : date.timeIntervalSince(epoch)
            let value = controlled ? held.value(at: date) : loopValue(clock(at: date))
            let _ = landing.advance(to: date, value: value)
            let since = (0..<3).map { reduceMotion ? 10 : date.timeIntervalSince(landing.at[$0]) }
            // The score follows the rings: the average of how far they are drawn.
            let scoreShown = Int((Double(score) * (ringAt(value, 0) + ringAt(value, 1) + ringAt(value, 2)) / 3).rounded())

            ZStack {
                Canvas { context, _ in
                    draw(context, value: value, time: time, since: since)
                }
                .padding(-Metrics.bleed)

                // The labels sit in their own layer, clipped to the card: the badge numbers start below its edge.
                ZStack {
                    Text(title)
                        .font(.system(size: 19.6, weight: .bold))
                        .tracking(-0.2)
                        .foregroundStyle(.white)
                        .fixedSize()
                        .frame(width: 220, alignment: .leading)
                        .position(x: 18.8 + 110, y: 26)

                    HStack(alignment: .top, spacing: 4) {
                        Text(sleep)
                            .font(.system(size: 16, weight: .medium))
                            .foregroundStyle(.white)
                        Text(sleepChange + "↑")
                            .font(.system(size: 10.6, weight: .medium))
                            .foregroundStyle(Color(hex: 0x4cd964))
                            .padding(.top, 0.6)
                    }
                    .fixedSize()
                    .frame(width: 200, alignment: .leading)
                    .position(x: 48.7 + 100, y: 64.5)
                    caption(sleepLabel, y: 81.6)

                    Text(heartRate)
                        .font(.system(size: 16, weight: .medium))
                        .foregroundStyle(.white)
                        .fixedSize()
                        .frame(width: 200, alignment: .leading)
                        .position(x: 48.7 + 100, y: 108)
                    caption(heartLabel, y: 125.4)

                    ForEach(0..<3, id: \.self) { index in
                        let ring = rings[index]
                        let drawn = ringAt(value, index)
                        let angle = (90 + ring.sweep * drawn) * .pi / 180
                        Text("\(Int((Double(values[index]) * drawn).rounded()))")
                            .font(.system(size: 9.6, weight: .heavy))
                            .monospacedDigit()
                            .foregroundStyle(ring.ink)
                            .fixedSize()
                            .scaleEffect(pop(since[index], ambient: reduceMotion ? 0 : 1))
                            .opacity(clamp01(drawn * 8))
                            .position(x: Metrics.cx + ring.r * cos(angle), y: Metrics.cy + ring.r * sin(angle))
                    }

                    Text("\(scoreShown)")
                        .font(.system(size: 22, weight: .bold))
                        .monospacedDigit()
                        .foregroundStyle(Color(hex: 0x0a0a0a))
                        .fixedSize()
                        .position(x: Metrics.cx + 0.6, y: 103.9)
                    Text(scoreLabel)
                        .font(.system(size: 13.2, weight: .semibold))
                        .foregroundStyle(Color(hex: 0x0a0a0a))
                        .fixedSize()
                        .position(x: Metrics.cx + 0.6, y: 125.8)
                }
                .lineLimit(1)
                .frame(width: Metrics.width, height: Metrics.height)
                .clipShape(RoundedRectangle(cornerRadius: Metrics.radius, style: .circular))
            }
            .frame(width: Metrics.width, height: Metrics.height)
            .accessibilityElement(children: .ignore)
            .accessibilityLabel(title)
            .accessibilityValue("\(scoreShown) \(scoreLabel), \(sleepLabel) \(sleep), \(heartLabel) \(heartRate)")
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

    private func caption(_ text: String, y: Double) -> some View {
        Text(text)
            .font(.system(size: 11.8, weight: .medium))
            .tracking(0.2)
            .foregroundStyle(Color(hex: 0x707070))
            .fixedSize()
            .frame(width: 160, alignment: .leading)
            .position(x: 48.7 + 80, y: y)
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

    private func draw(_ context: GraphicsContext, value: Double, time: Double, since: [Double]) {
        let x = Metrics.bleed
        let y = Metrics.bleed
        let ambient = reduceMotion ? 0.0 : 1.0
        let cardRect = CGRect(x: x, y: y, width: Metrics.width, height: Metrics.height)
        let card = Path(roundedRect: cardRect, cornerRadius: Metrics.radius, style: .circular)
        let centre = CGPoint(x: x + Metrics.cx, y: y + Metrics.cy)
        let ringWidth = Metrics.ringWidth

        var shadow = context
        shadow.addFilter(.blur(radius: 16))
        shadow.fill(
            Path(
                roundedRect: CGRect(x: x + 12, y: y + 20, width: Metrics.width - 24, height: Metrics.height - 12),
                cornerRadius: Metrics.radius, style: .circular
            ),
            with: .color(.black.opacity(0.5))
        )
        context.fill(
            card,
            with: .linearGradient(
                Gradient(colors: [Color(hex: 0x1b1b1b), Color(hex: 0x0e0e0e)]),
                startPoint: CGPoint(x: x, y: y), endPoint: CGPoint(x: x + Metrics.width * 0.3, y: y + Metrics.height)
            )
        )

        var inside = context
        inside.clip(to: card)
        // Faint outer tracks behind the rings.
        inside.stroke(circle(centre, 139), with: .color(.white.opacity(0.018)), lineWidth: ringWidth)
        inside.stroke(circle(centre, 115.5), with: .color(.white.opacity(0.03)), lineWidth: ringWidth)

        for index in 0..<3 {
            let ring = rings[index]
            let drawn = ringAt(value, index)
            let head = CGPoint(
                x: centre.x + ring.r * cos((90 + ring.sweep * drawn) * .pi / 180),
                y: centre.y + ring.r * sin((90 + ring.sweep * drawn) * .pi / 180)
            )
            inside.stroke(circle(centre, ring.r), with: .color(Color(hex: 0x0a0a0a)), lineWidth: ringWidth + 7)
            inside.stroke(
                arc(centre, ring.r, from: 0, to: ring.sweep * drawn), with: .color(ring.color),
                style: StrokeStyle(lineWidth: ringWidth, lineCap: .round)
            )
            // A sheen runs along the finished ring every few seconds.
            let sheenAt = (time + Double(index) * 0.5).truncatingRemainder(dividingBy: 3.4) / 1.3
            if ambient > 0, sheenAt < 1, drawn >= 0.999 {
                let end = ring.sweep * drawn * sheenAt
                var sheen = inside
                sheen.addFilter(.blur(radius: 3))
                sheen.stroke(
                    arc(centre, ring.r, from: max(0, end - 16), to: end),
                    with: .color(.white.opacity(0.34 * sin(.pi * sheenAt))),
                    style: StrokeStyle(lineWidth: ringWidth - 7, lineCap: .round)
                )
            }
            // The head of the ring is bright while it is still drawing.
            let lead = clamp01(drawn * 10) * clamp01((1 - drawn) * 4) * 0.75
            if lead > 0.01 {
                var glow = inside
                glow.addFilter(.blur(radius: 4))
                glow.fill(circle(head, ringWidth / 2), with: .color(.white.opacity(lead)))
            }
            inside.fill(circle(head, Metrics.badgeRadius * pop(since[index], ambient: ambient)), with: .color(ring.badge))
        }

        // White disc in the middle; when the last ring lands a ripple leaves it.
        let ripple = since[2] / 0.9
        if ambient > 0, ripple > 0, ripple < 1 {
            inside.stroke(
                circle(centre, Metrics.discRadius + 16 * easeOutCubic(ripple)),
                with: .color(.white.opacity(0.5 * (1 - ripple))), lineWidth: 1.6
            )
        }
        var discShadow = inside
        discShadow.addFilter(.blur(radius: 5))
        discShadow.fill(circle(CGPoint(x: centre.x, y: centre.y + 1), Metrics.discRadius + 2), with: .color(.black.opacity(0.5)))
        inside.fill(
            circle(centre, Metrics.discRadius),
            with: .radialGradient(
                Gradient(stops: [
                    .init(color: .white, location: 0),
                    .init(color: .white, location: 0.82),
                    .init(color: Color(hex: 0xe6e6e6), location: 1),
                ]),
                center: centre, startRadius: 0, endRadius: Metrics.discRadius
            )
        )

        // Moon and heart glyphs with their glow: the moon's breathes slowly, the heart beats at the rate it reports.
        var moon = context
        moon.translateBy(x: x + 29.6, y: y + 71.4)
        var moonGlow = moon
        moonGlow.addFilter(.blur(radius: 3.5))
        moonGlow.stroke(moonGlyph, with: .color(Color(hex: 0x6f8cff, opacity: 0.6 + ambient * 0.3 * sin(time * 1.2))), lineWidth: 3)
        moon.fill(moonGlyph, with: .color(Color(hex: 0x3a478c)))
        moon.stroke(moonGlyph, with: .color(Color(hex: 0x7592ff)), style: StrokeStyle(lineWidth: 1.6, lineJoin: .round))

        let beat = ambient * heartbeat((time * bpm / 60).truncatingRemainder(dividingBy: 1))
        var heart = context
        heart.translateBy(x: x + 29.6, y: y + 115.4)
        heart.scaleBy(x: 1 + 0.16 * beat, y: 1 + 0.16 * beat)
        var heartGlow = heart
        heartGlow.addFilter(.blur(radius: 3.5))
        heartGlow.stroke(heartGlyph, with: .color(Color(hex: 0xff1f1f, opacity: 0.55 + 0.45 * beat)), lineWidth: 3.4)
        heart.fill(heartGlyph, with: .color(Color(hex: 0x7a0c0c)))
        heart.stroke(heartGlyph, with: .color(Color(hex: 0xff2222)), style: StrokeStyle(lineWidth: 1.8, lineJoin: .round))
    }
}

private struct RingSpec {
    let r: Double
    /// Degrees swept at full value, clockwise from 6 o'clock.
    let sweep: Double
    let color: Color
    let badge: Color
    let ink: Color
    /// Where in the overall progress the ring starts drawing.
    let delay: Double
}

// Outside in.
private let rings = [
    RingSpec(r: 91.6, sweep: 119.7, color: Color(hex: 0x48a8e2), badge: Color(hex: 0x80d2f8), ink: Color(hex: 0x0b3a66), delay: 0),
    RingSpec(r: 68.2, sweep: 183.3, color: Color(hex: 0x5ee24a), badge: Color(hex: 0x92f37f), ink: Color(hex: 0x0d4a0a), delay: 0.15),
    RingSpec(r: 44.5, sweep: 153.1, color: Color(hex: 0xe84a66), badge: Color(hex: 0xf27c90), ink: Color(hex: 0x5a0a16), delay: 0.3),
]

private enum Metrics {
    static let width = 344.0
    static let height = 148.0
    static let radius = 34.0
    /// The canvas overflows the card by this much on every side to fit its shadow.
    static let bleed = 40.0

    // Centre of the rings and of the white disc in the middle of them.
    static let cx = 306.8
    static let cy = 111.6
    static let discRadius = 35.9
    static let ringWidth = 19.6
    static let badgeRadius = 8.5
    /// Share of the overall progress one ring takes to draw.
    static let ringSpan = 0.7

    // The reference shows every ring at its full value; the autoPlay loop built around it (seconds).
    static let rest = 1.0
    static let start = 0.4
    static let draw = 2.8
    static let hold = 3.8
    static let reset = 0.7
    static let loop = start + draw + hold + reset
    /// A moment in the loop where everything is at rest at the reference value: used when nothing is playing.
    static let restTime = start + draw + 1.6
}

private func clamp01(_ t: Double) -> Double { min(1, max(0, t)) }

private func easeInOutCubic(_ t: Double) -> Double {
    t < 0.5 ? 4 * t * t * t : 1 - pow(-2 * t + 2, 3) / 2
}

private func easeOutCubic(_ t: Double) -> Double { 1 - pow(1 - clamp01(t), 3) }

/// Overall progress at `t` seconds into the autoPlay loop.
private func loopValue(_ t: Double) -> Double {
    if t < Metrics.start { return 0 }
    if t < Metrics.start + Metrics.draw { return (t - Metrics.start) / Metrics.draw }
    if t < Metrics.start + Metrics.draw + Metrics.hold { return 1 }
    return 1 - easeInOutCubic((t - Metrics.start - Metrics.draw - Metrics.hold) / Metrics.reset)
}

/// 0…1: how far ring `index` is drawn at overall progress `p`. The rings start one after another.
private func ringAt(_ p: Double, _ index: Int) -> Double {
    easeOutCubic((clamp01(p) - rings[index].delay) / Metrics.ringSpan)
}

/// One heartbeat, 0…1, at `phase` (0…1) of the beat: a strong thump and a softer one after it.
private func heartbeat(_ phase: Double) -> Double {
    if phase < 0.16 { return sin(.pi * phase / 0.16) }
    if phase > 0.24, phase < 0.4 { return 0.55 * sin(.pi * (phase - 0.24) / 0.16) }
    return 0
}

/// Scale of a badge after its ring lands: a quick overshoot.
private func pop(_ since: Double, ambient: Double) -> Double {
    let u = clamp01(since / 0.5)
    return 1 + ambient * 0.55 * sin(.pi * u) * (1 - u)
}

private func circle(_ centre: CGPoint, _ r: Double) -> Path {
    Path(ellipseIn: CGRect(x: centre.x - r, y: centre.y - r, width: r * 2, height: r * 2))
}

/// Part of a ring, in degrees clockwise from 6 o'clock.
private func arc(_ centre: CGPoint, _ r: Double, from: Double, to: Double) -> Path {
    var p = Path()
    p.addArc(center: centre, radius: r, startAngle: .degrees(90 + from), endAngle: .degrees(90 + to), clockwise: false)
    return p
}

private let moonGlyph: Path = {
    var p = Path()
    p.move(to: CGPoint(x: 3.6, y: -8.3))
    p.addCurve(to: CGPoint(x: -9.2, y: 0.6), control1: CGPoint(x: -2.6, y: -10), control2: CGPoint(x: -9.2, y: -5.4))
    p.addCurve(to: CGPoint(x: 0, y: 9.4), control1: CGPoint(x: -9.2, y: 5.8), control2: CGPoint(x: -5, y: 9.4))
    p.addCurve(to: CGPoint(x: 8.9, y: 3), control1: CGPoint(x: 4.4, y: 9.4), control2: CGPoint(x: 7.8, y: 6.8))
    p.addCurve(to: CGPoint(x: 1.7, y: -1), control1: CGPoint(x: 5.8, y: 3.9), control2: CGPoint(x: 2.6, y: 2.2))
    p.addCurve(to: CGPoint(x: 3.6, y: -8.3), control1: CGPoint(x: 1, y: -3.6), control2: CGPoint(x: 1.8, y: -6.6))
    p.closeSubpath()
    return p
}()

private let heartGlyph: Path = {
    var p = Path()
    p.move(to: CGPoint(x: 0, y: 7.6))
    p.addCurve(to: CGPoint(x: -8.6, y: -2.8), control1: CGPoint(x: -5.4, y: 3.6), control2: CGPoint(x: -8.6, y: 0.8))
    p.addCurve(to: CGPoint(x: -4.2, y: -7.4), control1: CGPoint(x: -8.6, y: -5.6), control2: CGPoint(x: -6.6, y: -7.4))
    p.addCurve(to: CGPoint(x: 0, y: -4.9), control1: CGPoint(x: -2.4, y: -7.4), control2: CGPoint(x: -0.9, y: -6.4))
    p.addCurve(to: CGPoint(x: 4.2, y: -7.4), control1: CGPoint(x: 0.9, y: -6.4), control2: CGPoint(x: 2.4, y: -7.4))
    p.addCurve(to: CGPoint(x: 8.6, y: -2.8), control1: CGPoint(x: 6.6, y: -7.4), control2: CGPoint(x: 8.6, y: -5.6))
    p.addCurve(to: CGPoint(x: 0, y: 7.6), control1: CGPoint(x: 8.6, y: 0.8), control2: CGPoint(x: 5.4, y: 3.6))
    p.closeSubpath()
    return p
}()

#Preview {
    ZStack {
        Color(hex: 0x4b4b4b).ignoresSafeArea()
        ActivityRings(autoPlay: true)
    }
}

import SwiftUI

/// Live-activity style charging UI: a compact black pill with a bolt and the level, and an expanded
/// pill whose green bar ends in a glowing knob, with the time left beside it. Pulses of charge run
/// along the bar into the knob, which flares and bumps as each one lands (the compact bolt flashes
/// with it), the halo breathes, and the compact level rolls like an odometer.
struct ChargePillProgress: View {
    /// Controlled charge level, 0…1. Leave `nil` to show the reference's 76% (or the autoPlay loop).
    var progress: Double? = nil
    /// Charges from 20% to 76% by itself, holds, drops back and starts over.
    var autoPlay = false
    /// Minutes a full charge takes; the "~15m" estimate is the share of it that is left.
    var fullChargeMinutes = 62.0
    var label = "Charging"

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    /// Origin of the ambient clock (pulses, breathing).
    @State private var epoch = Date.now
    /// When the current autoPlay / tapped run started; `nil` shows the resting frame.
    @State private var runStart: Date? = nil
    @State private var held = Tween(Metrics.rest)
    /// Keeps the timeline running for a controlled change under reduced motion.
    @State private var settling = false

    private var controlled: Bool { progress != nil }
    private var target: Double { min(1, max(0, progress ?? Metrics.rest)) }

    var body: some View {
        TimelineView(.animation(paused: reduceMotion && !settling)) { timeline in
            let date = timeline.date
            let time = reduceMotion ? 0 : date.timeIntervalSince(epoch)
            let value = controlled ? held.value(at: date) : loopValue(clock(at: date))
            let percent = Int((value * 100).rounded())
            let minutes = Int(((1 - Double(percent) / 100) * fullChargeMinutes).rounded())
            let flare = reduceMotion ? 0 : pulseFlare(time)

            ZStack {
                Canvas { context, size in
                    draw(context, value: value, time: time, flare: flare)
                }
                .padding(-Metrics.bleed)

                HStack(spacing: 0) {
                    wheel(place: 100, value: value)
                    wheel(place: 10, value: value)
                    wheel(place: 1, value: value)
                }
                .position(
                    x: Metrics.miniX + Metrics.miniWidth - 8.7 - Metrics.digitWidth * 1.5,
                    y: Metrics.miniHeight / 2
                )

                Text("\(percent)")
                    .font(.system(size: 12.4, weight: .medium))
                    .monospacedDigit()
                    .foregroundStyle(.white)
                    .scaleEffect(1 + 0.08 * flare, anchor: UnitPoint(x: 0.5 - 5.6 / 30, y: 0.5))
                    .frame(width: 30)
                    .position(x: Metrics.barX + knobCenter(value) + 5.6, y: Metrics.barCenterY)

                Text(minutes > 0 ? "~\(minutes)m" : "Full")
                    .font(.system(size: 13.5, weight: .medium))
                    .monospacedDigit()
                    .foregroundStyle(Metrics.green)
                    .fixedSize()
                    .frame(width: 60, alignment: .leading)
                    .position(x: 270 + 30, y: Metrics.barCenterY)
            }
            .frame(width: Metrics.width, height: Metrics.height)
            .accessibilityElement(children: .ignore)
            .accessibilityLabel(label)
            .accessibilityValue("\(percent)%, about \(minutes) minutes left")
        }
        .contentShape(Rectangle())
        .onTapGesture { restart() }
        .onAppear { sync() }
        .onChange(of: autoPlay) { sync() }
        .onChange(of: reduceMotion) { sync() }
        .onChange(of: progress) {
            sync()
            guard controlled else { return }
            held.retarget(target, duration: reduceMotion ? 0.2 : 0.7, curve: Ease.out)
            settling = true
        }
        .task(id: settling) {
            guard settling else { return }
            try? await Task.sleep(for: .seconds(0.8))
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

    /// One wheel of the odometer. `place` is 1 for ones, 10 for tens, 100 for hundreds.
    private func wheel(place: Int, value: Double) -> some View {
        let v = clamp01(value) * 100
        // A wheel turns with the one below it only while that one rolls from 9 to 0.
        let lower = place == 1 ? 0 : (v / Double(place / 10)).truncatingRemainder(dividingBy: 10)
        let turn = place == 1
            ? v.truncatingRemainder(dividingBy: 10)
            : (v / Double(place)).rounded(.down).truncatingRemainder(dividingBy: 10) + max(0, lower - 9)
        return VStack(spacing: 0) {
            ForEach(0..<11, id: \.self) { index in
                Text("\(index % 10)")
                    .font(.system(size: 14, weight: .medium))
                    .monospacedDigit()
                    .foregroundStyle(Metrics.green)
                    .frame(width: Metrics.digitWidth, height: Metrics.digitHeight)
            }
        }
        .offset(y: -turn * Metrics.digitHeight)
        .frame(width: Metrics.digitWidth, height: Metrics.digitHeight, alignment: .top)
        .clipped()
        // Leading zeros stay hidden.
        .opacity(place == 1 ? 1 : clamp01(v - Double(place - 1)))
    }

    private func draw(_ context: GraphicsContext, value: Double, time: Double, flare: Double) {
        let x = Metrics.bleed
        let y = Metrics.bleed
        let ambient = reduceMotion ? 0.0 : 1.0
        let bx = x + Metrics.barX
        let cy = y + Metrics.barCenterY
        let knobX = knobCenter(value)
        let boltGlow = 0.75 * flare

        // Compact pill.
        context.fill(
            capsule(CGRect(x: x + Metrics.miniX, y: y, width: Metrics.miniWidth, height: Metrics.miniHeight)),
            with: .color(.black)
        )
        var mini = context
        mini.translateBy(x: x + Metrics.miniBoltX, y: y + Metrics.miniHeight / 2)
        var miniGlow = mini
        miniGlow.opacity = boltGlow
        miniGlow.addFilter(.blur(radius: 7))
        miniGlow.fill(capsule(CGRect(x: -9, y: -9, width: 18, height: 18)), with: .color(Color(hex: 0x35e066)))
        mini.scaleBy(x: 1.31 * (1 + 0.1 * flare), y: 1.31 * (1 + 0.1 * flare))
        mini.translateBy(x: -5.5, y: -7)
        mini.fill(bolt, with: .color(Metrics.green))
        mini.stroke(bolt, with: .color(Metrics.green), style: StrokeStyle(lineWidth: 1, lineJoin: .round))
        mini.fill(bolt, with: .color(Metrics.mint.opacity(boltGlow)))

        // Expanded pill.
        var shadow = context
        shadow.addFilter(.blur(radius: 14))
        shadow.fill(
            capsule(CGRect(x: x + 8, y: y + Metrics.pillY + 14, width: Metrics.width - 16, height: Metrics.pillHeight - 6)),
            with: .color(.black.opacity(0.22))
        )
        context.fill(
            capsule(CGRect(x: x, y: y + Metrics.pillY, width: Metrics.width, height: Metrics.pillHeight)),
            with: .color(.black)
        )
        let barHeight = Metrics.barHeight
        context.fill(
            capsule(CGRect(x: bx, y: cy - barHeight / 2, width: Metrics.barWidth, height: barHeight)),
            with: .color(Color(hex: 0x313131))
        )

        // The halo trails to the left like a comet, breathes, and flares when a pulse lands.
        var halo = context
        halo.translateBy(x: bx + knobX, y: cy)
        halo.scaleBy(x: 1 + 0.22 * flare, y: 1 + 0.22 * flare)
        halo.opacity = min(1, 0.62 + ambient * 0.08 * sin(time * 2.3) + 0.38 * flare)
        var tail = halo
        tail.addFilter(.blur(radius: 11))
        tail.fill(capsule(CGRect(x: -62, y: -9, width: 76, height: 18)), with: .color(.rgba(40, 214, 90, 0.55)))
        var core = halo
        core.addFilter(.blur(radius: 8))
        core.fill(
            capsule(CGRect(
                x: -Metrics.knobWidth / 2 - 2, y: -Metrics.knobHeight / 2 - 2,
                width: Metrics.knobWidth + 4, height: Metrics.knobHeight + 4
            )),
            with: .color(.rgba(48, 226, 98, 0.85))
        )

        let fill = capsule(CGRect(x: bx, y: cy - barHeight / 2, width: knobX, height: barHeight))
        context.fill(fill, with: .color(Metrics.green))
        // The pulse: a streak of light that runs along the fill into the knob.
        let phase = time.truncatingRemainder(dividingBy: Metrics.pulse) / Metrics.pulse
        if ambient > 0, phase < Metrics.travel {
            let sx = bx - Metrics.streak + (knobX + Metrics.streak * 0.4) * pulseTravel(time)
            var streak = context
            streak.opacity = clamp01(phase / 0.12)
            streak.fill(
                fill,
                with: .linearGradient(
                    Gradient(stops: [
                        .init(color: Metrics.mint.opacity(0), location: 0),
                        .init(color: Metrics.mint.opacity(0.95), location: 0.75),
                        .init(color: Metrics.mint.opacity(0), location: 1),
                    ]),
                    startPoint: CGPoint(x: sx, y: cy),
                    endPoint: CGPoint(x: sx + Metrics.streak, y: cy)
                )
            )
        }

        var knob = context
        knob.translateBy(x: bx + knobX, y: cy)
        knob.scaleBy(x: 1 + 0.08 * flare, y: 1 + 0.08 * flare)
        let body = capsule(CGRect(
            x: -Metrics.knobWidth / 2, y: -Metrics.knobHeight / 2, width: Metrics.knobWidth, height: Metrics.knobHeight
        ))
        knob.fill(body, with: .color(Color(hex: 0x41c966)))
        knob.fill(body, with: .color(Metrics.mint.opacity(boltGlow)))
        knob.translateBy(x: -Metrics.knobWidth / 2 + 7.8, y: 0)
        knob.scaleBy(x: 0.86, y: 0.86)
        knob.translateBy(x: -5.5, y: -7)
        knob.fill(bolt, with: .color(Color(hex: 0xb4ecc2)))
    }
}

private enum Metrics {
    static let width = 340.0
    static let height = 136.0
    /// The canvas overflows the component by this much on every side to fit the pill's shadow.
    static let bleed = 36.0

    // Compact pill: bolt on the left, level on the right.
    static let miniX = 70.0
    static let miniWidth = 200.0
    static let miniHeight = 36.0
    static let miniBoltX = miniX + 16
    // Expanded pill: bar with a knob, time left on the right.
    static let pillY = 60.0
    static let pillHeight = 76.0
    static let barX = 33.0
    static let barWidth = 228.0
    static let barHeight = 7.2
    static let barCenterY = pillY + pillHeight / 2
    static let knobWidth = 34.0
    static let knobHeight = 18.4

    static let digitWidth = 8.7
    static let digitHeight = 18.0

    static let green = Color(hex: 0x3fc760)
    static let mint = Color(hex: 0xc4ffd2)

    /// Value shown in the reference, and the autoPlay loop built around it (seconds).
    static let rest = 0.76
    static let from = 0.2
    static let start = 0.4
    static let fill = 3.2
    static let hold = 3.4
    static let reset = 0.6
    static let loop = start + fill + hold + reset
    /// A moment in the loop where everything is at rest: used when nothing is playing.
    static let restTime = start + fill + 1
    /// A pulse of charge leaves the start of the bar this often; it takes `travel` of that to reach the knob.
    static let pulse = 1.3
    static let travel = 0.55
    static let streak = 46.0
}

/// SF-style bolt in an 11 × 14 box.
private let bolt: Path = {
    var path = Path()
    path.move(to: CGPoint(x: 7.8, y: 0))
    path.addLine(to: CGPoint(x: 0.2, y: 8.4))
    path.addLine(to: CGPoint(x: 4.5, y: 8.4))
    path.addLine(to: CGPoint(x: 3.2, y: 14))
    path.addLine(to: CGPoint(x: 10.8, y: 5.5))
    path.addLine(to: CGPoint(x: 6.5, y: 5.5))
    path.closeSubpath()
    return path
}()

private func clamp01(_ t: Double) -> Double { min(1, max(0, t)) }

private func easeInOutCubic(_ t: Double) -> Double {
    t < 0.5 ? 4 * t * t * t : 1 - pow(-2 * t + 2, 3) / 2
}

/// Charge level at `t` seconds into the autoPlay loop.
private func loopValue(_ t: Double) -> Double {
    let span = Metrics.rest - Metrics.from
    if t < Metrics.start { return Metrics.from }
    if t < Metrics.start + Metrics.fill { return Metrics.from + span * easeInOutCubic((t - Metrics.start) / Metrics.fill) }
    if t < Metrics.loop - Metrics.reset { return Metrics.rest }
    return Metrics.rest - span * easeInOutCubic((t - (Metrics.loop - Metrics.reset)) / Metrics.reset)
}

/// Where the knob's centre sits on the bar, from its left end.
private func knobCenter(_ p: Double) -> Double {
    Metrics.knobWidth / 2 + (Metrics.barWidth - Metrics.knobWidth) * clamp01(p)
}

/// 0…1 along the bar for the travelling pulse; it accelerates into the knob.
private func pulseTravel(_ time: Double) -> Double {
    let phase = time.truncatingRemainder(dividingBy: Metrics.pulse) / Metrics.pulse
    return phase < Metrics.travel ? pow(phase / Metrics.travel, 2) : 1
}

/// 0…1 flash that starts when a pulse reaches the knob and dies away quickly.
private func pulseFlare(_ time: Double) -> Double {
    let since = (time.truncatingRemainder(dividingBy: Metrics.pulse) / Metrics.pulse - Metrics.travel) * Metrics.pulse
    return since < 0 ? 0 : min(1, since / 0.05) * exp(-5.5 * since)
}

#Preview {
    ZStack {
        Color(hex: 0xebebeb).ignoresSafeArea()
        ChargePillProgress(autoPlay: true)
    }
}

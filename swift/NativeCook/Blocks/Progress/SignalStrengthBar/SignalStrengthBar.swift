import SwiftUI

/// Dark monospace readout over a blurred backdrop: signal strength and noise ratio in large
/// figures, a yellow bar on a ticked 0–100% scale and the active channel chips. The corner
/// brackets fly in and lock onto the panel, a probe scans the ticks ahead of the bar, static
/// crackles over the empty part of the scale, both figures count against each other and the
/// channels come online one by one as the signal climbs.
struct SignalStrengthBar: View {
    /// Controlled signal strength, 0…1. Leave `nil` to show the reference's 56.2% (or the autoPlay loop).
    var progress: Double? = nil
    /// Sweeps from 0 to 56.2% by itself, holds, drops and starts over.
    var autoPlay = false
    var section = "Threshold"
    var title = "Signal strength"
    var signalLabel = "SIGNAL STRENGTH"
    var noiseLabel = "NOISE RATIO"
    /// Change shown next to the reading.
    var delta = "+32%"
    var channelsLabel = "ACTIVE CHANNELS"

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    /// Origin of the ambient clock (static, drifting backdrop).
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
            let lock = controlled || reduceMotion ? 1 : loopLock(clock)

            ZStack {
                Canvas { context, _ in
                    draw(context, value: value, time: time, activity: activity(at: date, clock: clock), lock: lock)
                }
                .padding(-Metrics.bleed)
                .allowsHitTesting(false)

                content(value: value)
                    .accessibilityElement(children: .ignore)
                    .accessibilityLabel(title)
                    .accessibilityValue(percent(target))
            }
            .frame(width: Metrics.width, height: Metrics.height)
        }
        .contentShape(Rectangle())
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
            try? await Task.sleep(for: .seconds(1.2))
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

    /// 0…1: how hard the probe is scanning.
    private func activity(at date: Date, clock: Double) -> Double {
        if reduceMotion { return 0 }
        if !controlled { return loopActivity(clock) }
        let since = date.timeIntervalSince(movedAt)
        return clamp01(since / 0.2) * clamp01((1.1 - since) / 0.5)
    }

    private func content(value: Double) -> some View {
        let pad = Metrics.pad
        let width = Metrics.width
        let mono = Font.system(size: 8.6, design: .monospaced)
        let reading = Font.system(size: 30, weight: .medium)
        let labelInk = Color.white.opacity(0.74)
        let signal = percent(value)
        // The two readings always add up to 100.0.
        let noise = percent(1 - (value * 1000).rounded() / 1000)
        let inputs = Metrics.channelAt.filter { value >= $0 + 0.015 }.count

        return ZStack {
            (Text(section + "  /  ").foregroundStyle(.white.opacity(0.42)) + Text(title).foregroundStyle(.white))
                .font(.system(size: 11.5, weight: .medium))
                .lineLimit(1)
                .leading(pad, centerY: Metrics.headerHeight / 2)
            Text(signal)
                .font(mono)
                .foregroundStyle(Color(hex: 0xece65c))
                .position(x: width - pad - 20, y: 18)

            Text(signalLabel)
                .font(mono)
                .kerning(0.5)
                .foregroundStyle(labelInk)
                .lineLimit(1)
                .leading(pad, centerY: 55)
            // The delta follows the end of the reading, whatever its digit count.
            HStack(alignment: .top, spacing: 7) {
                Text(signal)
                    .font(reading)
                    .kerning(-0.6)
                    .monospacedDigit()
                    .foregroundStyle(.white)
                    .frame(height: 38)
                Text(delta)
                    .font(mono)
                    .foregroundStyle(Color(hex: 0x65dc63))
                    .frame(height: 12)
                    .padding(.top, 20.5)
            }
            .leading(pad, centerY: 85)

            // The noise label starts where its reading starts.
            VStack(alignment: .leading, spacing: 5) {
                Text(noiseLabel)
                    .font(mono)
                    .kerning(0.5)
                    .foregroundStyle(labelInk)
                    .lineLimit(1)
                    .frame(height: 12)
                Text(noise)
                    .font(reading)
                    .kerning(-0.6)
                    .monospacedDigit()
                    .foregroundStyle(.white)
                    .frame(height: 38)
            }
            .trailing(width - pad, centerY: 76.5)

            HStack(spacing: 0) {
                ForEach(["0%", "25%", "50%", "75%", "100%"], id: \.self) { mark in
                    if mark != "0%" { Spacer(minLength: 0) }
                    Text(mark)
                }
            }
            .font(mono)
            .foregroundStyle(.white.opacity(0.5))
            .frame(width: width - pad * 2)
            .position(x: width / 2, y: 133)

            Text(channelsLabel)
                .font(mono)
                .kerning(0.5)
                .foregroundStyle(labelInk)
                .lineLimit(1)
                .leading(pad, centerY: 172)
            Text("\(inputs) \(inputs == 1 ? "INPUT" : "INPUTS")")
                .font(mono)
                .kerning(0.4)
                .foregroundStyle(Color(hex: 0xf0ea58))
                .position(x: width - pad - 27, y: Metrics.chipY + 8)
        }
        .frame(width: Metrics.width, height: Metrics.height)
    }

    private func draw(_ base: GraphicsContext, value: Double, time: Double, activity: Double, lock: Double) {
        let ambient = reduceMotion ? 0.0 : 1.0
        let width = Metrics.width
        let height = Metrics.height
        let pad = Metrics.pad
        var context = base
        context.translateBy(x: Metrics.bleed, y: Metrics.bleed)
        let yellow = Color(hex: 0xf6ef4c)

        // Out-of-focus backdrop: soft streaks of sky, leaves and light, swaying slowly.
        var backdrop = context
        let sway = ambient * 9 * sin(time * 0.23)
        backdrop.translateBy(x: sway * cos(Metrics.backdropTilt), y: sway * sin(Metrics.backdropTilt))
        for blob in Metrics.backdrop {
            softStreak(backdrop, x: blob.x, y: blob.y, length: blob.l, thickness: blob.t, color: blob.color, alpha: blob.alpha)
        }

        let panel = CGRect(x: 0, y: 0, width: width, height: height)
        var shadow = context
        shadow.addFilter(.blur(radius: 12))
        shadow.fill(Path(CGRect(x: 2, y: 8, width: width - 4, height: height - 4)), with: .color(.black.opacity(0.4)))
        context.fill(Path(panel), with: .color(.rgba(24, 27, 24, 0.95)))
        // A paler band where the backdrop shows through the glass.
        var glass = context
        glass.clip(to: Path(panel))
        softStreak(glass, x: 96, y: 60, length: 190, thickness: 62, color: (150, 150, 120), alpha: 0.17)
        context.stroke(Path(panel.insetBy(dx: 0.5, dy: 0.5)), with: .color(.white.opacity(0.09)), lineWidth: 1)

        // Corner brackets: they fly in from outside and snap onto the corners.
        let away = (1 - lock) * 9
        for corner in Metrics.corners {
            let x = corner.x - corner.dx * (1.5 + away)
            let y = corner.y - corner.dy * (1.5 + away)
            var bracket = Path()
            bracket.move(to: CGPoint(x: x + corner.dx * Metrics.bracket, y: y))
            bracket.addLine(to: CGPoint(x: x, y: y))
            bracket.addLine(to: CGPoint(x: x, y: y + corner.dy * Metrics.bracket))
            context.stroke(bracket, with: .color(.white.opacity(lock)), lineWidth: 1)
        }

        let barX = pad
        let barY = Metrics.barY
        let barWidth = width - pad * 2
        let barHeight = Metrics.barHeight
        context.fill(Path(CGRect(x: 0, y: Metrics.headerHeight, width: width, height: 1)), with: .color(.white.opacity(0.09)))
        context.fill(Path(CGRect(x: pad, y: Metrics.dividerY, width: barWidth, height: 1)), with: .color(.white.opacity(0.09)))
        context.fill(Path(CGRect(x: width - pad - 40, y: 10, width: 40, height: 16)), with: .color(.rgba(150, 148, 50, 0.3)))
        context.fill(
            Path(CGRect(x: width - pad - 54, y: Metrics.chipY - 0.5, width: 54, height: 17)),
            with: .color(.rgba(150, 148, 50, 0.34))
        )

        // Scale: a faint track with ticks, static on its empty part, the lit ticks ahead of the bar.
        let fill = barWidth * value
        let head = barX + fill
        context.fill(Path(CGRect(x: barX, y: barY, width: barWidth, height: barHeight)), with: .color(.white.opacity(0.05)))
        let frame = (time * 13).rounded(.down)
        let shift = (frame * 37.7).truncatingRemainder(dividingBy: 97)
        let crackle = ambient * (0.35 + 0.65 * (1 - value))
        let scanAlpha = 0.25 + 0.75 * activity
        for i in 0..<Metrics.ticks {
            let center = barX + (Double(i) + 0.5) * (barWidth / Double(Metrics.ticks))
            let tick = Path(CGRect(x: center - Metrics.tickWidth / 2, y: barY, width: Metrics.tickWidth, height: barHeight))
            context.fill(tick, with: .color(.white.opacity(0.15)))
            guard center > head else { continue }
            // Static crackles over the empty part of the scale, louder the higher the noise ratio.
            let noise = staticLevel(center - barX - shift) * crackle
            if noise > 0 { context.fill(tick, with: .color(.white.opacity(noise))) }
            // Ticks just ahead of the bar light up, as if a probe were scanning them.
            let ahead = 1 - (center - head) / Metrics.scanWidth
            if ahead > 0 { context.fill(tick, with: .color(yellow.opacity(0.95 * ahead * scanAlpha))) }
        }

        var glow = context
        glow.addFilter(.blur(radius: 5))
        glow.fill(Path(CGRect(x: barX, y: barY + 1, width: fill, height: barHeight)), with: .color(.rgba(246, 239, 76, 0.3)))
        context.fill(Path(CGRect(x: barX, y: barY, width: fill, height: barHeight)), with: .color(yellow))

        // The probe: a hot line at the head of the bar while it moves.
        let headAlpha = activity * clamp01(value / 0.01)
        if headAlpha > 0 {
            let probe = Path(CGRect(x: head - 0.8, y: barY - 3.5, width: 1.6, height: barHeight + 7))
            var halo = context
            halo.opacity = headAlpha
            halo.addFilter(.blur(radius: 2.4))
            halo.fill(probe, with: .color(.rgba(255, 252, 170, 0.9)))
            context.fill(probe, with: .color(.rgba(255, 252, 170, 0.9 * headAlpha)))
        }

        // Channel chips: each wakes up with a yellow flash as the signal passes its level.
        for (index, level) in Metrics.channelAt.enumerated() {
            let x = pad + Double(index) * Metrics.chipPitch
            let chip = Path(CGRect(x: x, y: Metrics.chipY, width: Metrics.chip, height: Metrics.chip))
            let on = clamp01((value - level) / 0.03)
            context.fill(chip, with: .color(.white.opacity(0.09)))
            context.fill(chip, with: .color(yellow.opacity(4 * on * (1 - on))))
            var glyph = context
            glyph.translateBy(x: x + Metrics.chip / 2, y: Metrics.chipY + Metrics.chip / 2)
            drawGlyph(glyph, index: index, ink: .white.opacity(0.28 + 0.72 * on))
        }
    }

    /// Generic channel glyphs, each drawn around its own centre: linked nodes, a grid, a target, a half disc.
    private func drawGlyph(_ context: GraphicsContext, index: Int, ink: Color) {
        func dot(_ x: Double, _ y: Double, _ r: Double) -> Path {
            Path(ellipseIn: CGRect(x: x - r, y: y - r, width: r * 2, height: r * 2))
        }
        switch index {
        case 0:
            var links = Path()
            links.move(to: CGPoint(x: -2.3, y: -3.3))
            links.addLine(to: CGPoint(x: 2.5, y: 0))
            links.addLine(to: CGPoint(x: -2.3, y: 3.3))
            context.stroke(links, with: .color(ink), lineWidth: 1)
            for point in [(-2.3, -3.3), (2.5, 0.0), (-2.3, 3.3)] {
                context.fill(dot(point.0, point.1, 1.5), with: .color(ink))
            }
        case 1:
            for point in [(-3.9, -3.9), (0.7, -3.9), (-3.9, 0.7), (0.7, 0.7)] {
                context.fill(
                    Path(roundedRect: CGRect(x: point.0, y: point.1, width: 3.2, height: 3.2), cornerRadius: 0.9, style: .circular),
                    with: .color(ink)
                )
            }
        case 2:
            context.stroke(dot(0, 0, 3.9), with: .color(ink), lineWidth: 1.1)
            context.fill(dot(0, 0, 1.4), with: .color(ink))
        default:
            context.stroke(dot(0, 0, 3.9), with: .color(ink), lineWidth: 1.1)
            var half = Path()
            half.move(to: CGPoint(x: 0, y: -3.9))
            half.addArc(center: .zero, radius: 3.9, startAngle: .degrees(-90), endAngle: .degrees(90), clockwise: false)
            half.closeSubpath()
            context.fill(half, with: .color(ink))
        }
    }

    /// A soft-edged streak: a radial fade squashed along the backdrop's tilt.
    private func softStreak(
        _ context: GraphicsContext, x: Double, y: Double, length: Double, thickness: Double,
        color: (Double, Double, Double), alpha: Double
    ) {
        var copy = context
        copy.translateBy(x: x, y: y)
        copy.rotate(by: .radians(Metrics.backdropTilt))
        copy.fillEllipse(
            center: .zero, radii: CGSize(width: length, height: thickness),
            gradient: Gradient(colors: [
                .rgba(color.0, color.1, color.2, alpha), .rgba(color.0, color.1, color.2, 0),
            ])
        )
    }
}

private struct Blob {
    var x: Double
    var y: Double
    var l: Double
    var t: Double
    var color: (Double, Double, Double)
    var alpha: Double
}

private enum Metrics {
    static let width = 340.0
    static let height = 218.0
    static let pad = 18.0
    /// The canvas overflows the panel by this much on every side for the backdrop and the brackets.
    static let bleed = 44.0

    static let headerHeight = 36.0
    static let barY = 110.0
    static let barHeight = 7.5
    static let ticks = 60
    static let tickWidth = 1.3
    static let dividerY = 153.0
    static let chipY = 184.0
    static let chip = 16.0
    static let chipPitch = 19.5
    static let scanWidth = 34.0
    static let bracket = 5.0

    /// The channel chips wake up one after another as the signal passes these levels.
    static let channelAt = [0.1, 0.22, 0.34, 0.46]

    /// Value shown in the reference, and the autoPlay loop built around it (seconds).
    static let rest = 0.562
    static let start = 0.55
    static let fill = 2.1
    static let hold = 3.6
    static let reset = 0.6
    static let loop = start + fill + hold + reset
    /// A moment in the loop where everything is at rest: used when nothing is playing.
    static let restTime = start + fill + 1

    static let corners: [(x: Double, y: Double, dx: Double, dy: Double)] = [
        (0, 0, 1, 1), (width, 0, -1, 1), (0, height, 1, -1), (width, height, -1, -1),
    ]

    static let backdropTilt = -58 * Double.pi / 180
    /// Soft streaks of the out-of-focus backdrop: centre, half length, half thickness, colour, alpha.
    static let backdrop = [
        Blob(x: 40, y: -10, l: 190, t: 70, color: (143, 182, 201), alpha: 0.95),
        Blob(x: 170, y: 20, l: 150, t: 40, color: (168, 201, 214), alpha: 0.7),
        Blob(x: 60, y: 230, l: 220, t: 60, color: (24, 48, 26), alpha: 0.9),
        Blob(x: 300, y: 250, l: 200, t: 70, color: (20, 40, 24), alpha: 0.9),
        Blob(x: 330, y: 60, l: 160, t: 46, color: (36, 70, 38), alpha: 0.8),
        Blob(x: 250, y: 10, l: 170, t: 16, color: (176, 180, 84), alpha: 0.7),
        Blob(x: 20, y: 120, l: 150, t: 14, color: (190, 190, 96), alpha: 0.6),
        Blob(x: 120, y: 250, l: 150, t: 12, color: (170, 176, 90), alpha: 0.5),
        Blob(x: 350, y: 150, l: 150, t: 18, color: (110, 150, 80), alpha: 0.7),
    ]

    /// Brightness of the static along the ticks: an irregular pattern that jumps about a dozen times a second.
    static let staticPattern: [Double] = [0, 0.5, 0, 0, 0.9, 0, 0.3, 0, 0, 0.7, 0, 0, 1, 0, 0.4, 0, 0, 0.8, 0, 0]
}

private func clamp01(_ t: Double) -> Double { min(1, max(0, t)) }

private func easeInOutCubic(_ t: Double) -> Double {
    t < 0.5 ? 4 * t * t * t : 1 - pow(-2 * t + 2, 3) / 2
}

/// Signal strength at `t` seconds into the autoPlay loop.
private func loopValue(_ t: Double) -> Double {
    if t < Metrics.start { return 0 }
    if t < Metrics.start + Metrics.fill { return Metrics.rest * easeInOutCubic((t - Metrics.start) / Metrics.fill) }
    if t < Metrics.loop - Metrics.reset { return Metrics.rest }
    return Metrics.rest * (1 - easeInOutCubic((t - (Metrics.loop - Metrics.reset)) / Metrics.reset))
}

private func loopActivity(_ t: Double) -> Double {
    clamp01((t - Metrics.start) / 0.2) * clamp01((Metrics.start + Metrics.fill + 0.25 - t) / 0.5)
}

/// 0…1: how far the corner brackets have locked onto the panel at `t` seconds into the loop.
private func loopLock(_ t: Double) -> Double {
    if t > Metrics.loop - Metrics.reset { return 1 - clamp01((t - (Metrics.loop - Metrics.reset)) / (Metrics.reset * 0.8)) }
    return 1 - pow(1 - clamp01((t - 0.05) / 0.45), 3)
}

private func percent(_ value: Double) -> String {
    String(format: "%.1f%%", (value * 1000).rounded() / 10)
}

/// The static pattern sampled at `x` points along the scale: it repeats every 97pt.
private func staticLevel(_ x: Double) -> Double {
    let pattern = Metrics.staticPattern
    var u = x.truncatingRemainder(dividingBy: 97) / 97
    if u < 0 { u += 1 }
    let position = u * Double(pattern.count - 1)
    let index = min(Int(position), pattern.count - 2)
    let mix = position - Double(index)
    return 0.3 * (pattern[index] * (1 - mix) + pattern[index + 1] * mix)
}

private extension View {
    /// Places the view by its leading edge and vertical centre inside the panel.
    func leading(_ x: Double, centerY: Double) -> some View {
        fixedSize()
            .frame(width: 320, alignment: .leading)
            .position(x: x + 160, y: centerY)
    }

    /// Places the view by its trailing edge and vertical centre inside the panel.
    func trailing(_ x: Double, centerY: Double) -> some View {
        fixedSize()
            .frame(width: 320, alignment: .trailing)
            .position(x: x - 160, y: centerY)
    }
}

#Preview {
    ZStack {
        Color(hex: 0x4f6b49).ignoresSafeArea()
        SignalStrengthBar(autoPlay: true)
    }
}

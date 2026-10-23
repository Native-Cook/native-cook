import SwiftUI

/// Soft light widget with a big dB reading, a signal-bars glyph and a Low–High trough holding a
/// chunky striped block. The level swings in like a meter needle and overshoots, the stripes
/// travel with it, the bars bounce like an equaliser while it moves, the block turns from green to
/// yellow past 50 dB and the number inside it steps through the 30 dB bands with a pop.
struct NoiseLevelMeter: View {
    enum Tone {
        case green, yellow
    }

    /// Controlled level, 0…1 of the 120 dB scale. Leave `nil` to show `level` (or the autoPlay loop).
    var progress: Double? = nil
    /// Swings from silence up to `level` like a meter needle, holds, drops and starts over.
    var autoPlay = false
    /// Reading in dB shown at rest: 40 and 60 are the two cards of the reference.
    var level = 40.0
    /// Fixed colour. When `nil` the meter is green and turns yellow as it passes 50 dB.
    var tone: Tone? = nil
    var title = "Current noise level"
    var lowLabel = "Low"
    var highLabel = "High"

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    /// Origin of the ambient clock (stripe drift, tremble, breathing).
    @State private var epoch = Date.now
    /// When the current autoPlay / tapped run started; `nil` shows the resting frame.
    @State private var runStart: Date? = nil
    @State private var held = Tween(40.0 / Metrics.maxDB)
    /// When a controlled value last changed.
    @State private var movedAt = Date.distantPast
    /// When the number inside the block last stepped up a band.
    @State private var poppedAt = Date.distantPast
    /// Keeps the timeline running for a controlled change under reduced motion.
    @State private var settling = false

    private var controlled: Bool { progress != nil }
    private var restValue: Double { clamp01(level / Metrics.maxDB) }
    private var target: Double { clamp01(progress ?? restValue) }

    var body: some View {
        TimelineView(.animation(paused: reduceMotion && !settling)) { timeline in
            let date = timeline.date
            let time = reduceMotion ? 0 : date.timeIntervalSince(epoch)
            let clock = clock(at: date)
            let value = controlled ? held.value(at: date) : loopValue(clock, rest: restValue)
            let decibels = Int((value * Metrics.maxDB).rounded())
            let band = Int(((value * Metrics.maxDB + 0.5) / Metrics.band).rounded(.down) * Metrics.band)
            let mix = tone.map { $0 == .yellow ? 1.0 : 0.0 } ?? clamp01((value * Metrics.maxDB - 46) / 8)

            ZStack {
                Canvas { context, _ in
                    draw(context, value: value, time: time, activity: activity(at: date, clock: clock), mix: mix)
                }
                .padding(-Metrics.bleed)
                .allowsHitTesting(false)

                content(value: value, decibels: decibels, band: band, pop: popScale(date.timeIntervalSince(poppedAt)))
                    .accessibilityElement(children: .ignore)
                    .accessibilityLabel(title)
                    .accessibilityValue("\(decibels) dB")
            }
            .frame(width: Metrics.size, height: Metrics.size)
            .onChange(of: band) { old, new in
                if new > old, !reduceMotion { poppedAt = .now }
            }
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

    /// 0…1: how lively the meter is (the bars only bounce while the level moves).
    private func activity(at date: Date, clock: Double) -> Double {
        if reduceMotion { return 0 }
        if !controlled { return loopActivity(clock) }
        let since = date.timeIntervalSince(movedAt)
        return clamp01(since / 0.15) * clamp01((0.9 - since) / 0.5)
    }

    private func content(value: Double, decibels: Int, band: Int, pop: Double) -> some View {
        let ink = Color(hex: 0x7c8099)
        return ZStack {
            Text(title)
                .font(.system(size: 13.2, weight: .light))
                .kerning(-0.1)
                .foregroundStyle(ink)
                .lineLimit(1)
                .leading(16.5, centerY: 21)

            HStack(alignment: .top, spacing: 6) {
                Text("\(decibels)")
                    .font(.system(size: 39.5, weight: .bold))
                    .kerning(-0.6)
                    .monospacedDigit()
                    .foregroundStyle(Color(hex: 0x0b0d12))
                    .frame(height: 46)
                Text("dB")
                    .font(.system(size: 16))
                    .foregroundStyle(ink)
                    .frame(height: 20)
                    .padding(.top, 4.5)
            }
            .leading(16.5, centerY: 51)

            Text(lowLabel)
                .font(.system(size: 8.3))
                .foregroundStyle(Color(hex: 0x8286a0))
                .leading(16.5, centerY: 99)
            Text(highLabel)
                .font(.system(size: 8.3))
                .foregroundStyle(Color(hex: 0x8286a0))
                .trailing(Metrics.size - 16.5, centerY: 99)

            // "120" hides as the block closes in on it.
            Text("\(Int(Metrics.maxDB))")
                .font(.system(size: 11.6, weight: .bold))
                .monospacedDigit()
                .foregroundStyle(.white.opacity(0.72))
                .opacity(clamp01((0.78 - value) / 0.06))
                .trailing(Metrics.size - 21.1, centerY: 136.5)
            Text("\(band)")
                .font(.system(size: 11.6, weight: .bold))
                .monospacedDigit()
                .foregroundStyle(.white)
                .scaleEffect(pop)
                .opacity(clamp01((value * Metrics.maxDB - 26) / 4))
                .leading(21.1, centerY: 136.5)
        }
        .frame(width: Metrics.size, height: Metrics.size)
    }

    private func draw(_ context: GraphicsContext, value: Double, time: Double, activity: Double, mix: Double) {
        let bleed = Metrics.bleed
        let size = Metrics.size
        let card = CGRect(x: bleed, y: bleed, width: size, height: size)
        let ambient = reduceMotion ? 0.0 : 1.0

        var shadow = context
        shadow.addFilter(.blur(radius: 12))
        shadow.fill(
            Path(
                roundedRect: CGRect(x: card.minX + 4, y: card.minY + 10, width: size - 8, height: size - 8),
                cornerRadius: Metrics.radius, style: .circular
            ),
            with: .color(.rgba(96, 106, 130, 0.28))
        )
        context.fill(
            Path(roundedRect: card, cornerRadius: Metrics.radius, style: .circular),
            with: .linearGradient(
                Gradient(colors: [Color(hex: 0xeeeff3), Color(hex: 0xe9eaee)]),
                startPoint: CGPoint(x: card.minX, y: card.minY), endPoint: CGPoint(x: card.minX, y: card.maxY)
            )
        )
        context.stroke(
            Path(roundedRect: card.insetBy(dx: 0.5, dy: 0.5), cornerRadius: Metrics.radius - 0.5, style: .circular),
            with: .linearGradient(
                Gradient(colors: [.white.opacity(0.9), .white.opacity(0.25)]),
                startPoint: CGPoint(x: card.minX, y: card.minY), endPoint: CGPoint(x: card.minX, y: card.maxY)
            ),
            lineWidth: 1
        )

        let tx = card.minX + Metrics.troughX
        let ty = card.minY + Metrics.troughY
        let troughWidth = Metrics.troughWidth
        let troughHeight = Metrics.troughHeight

        // Where the level sits in the trough, with the faint tremble of a live microphone.
        let tremble = ambient * 0.45 * sin(time * 9.1) * sin(time * 2.3)
        let edge = troughWidth * clamp01(value) + (value > 0.02 ? tremble : 0)

        // Empty part of the trough, a touch warmer where it meets the needle.
        let left = min(troughWidth, edge < 5 ? edge : edge + Metrics.restGap)
        let restRect = CGRect(x: tx + left, y: ty, width: troughWidth - left, height: troughHeight)
        let rest = Path(roundedRect: restRect, cornerRadius: min(Metrics.troughRadius, restRect.width / 2), style: .circular)
        context.fill(rest, with: .color(Color(hex: 0xd9dbe3)))
        var shade = context
        shade.clip(to: rest)
        shade.opacity = clamp01((edge - 5) / 8)
        shade.fill(
            Path(CGRect(x: restRect.minX, y: ty, width: 18, height: troughHeight)),
            with: .linearGradient(
                Gradient(colors: [.rgba(212, 211, 212, 0.6), .rgba(212, 211, 212, 0)]),
                startPoint: CGPoint(x: restRect.minX, y: ty), endPoint: CGPoint(x: restRect.minX + 18, y: ty)
            )
        )

        // The yellow copy fades in over the green one, whose glow has to leave or it would show around it.
        let breathing = clamp01(edge / 14) * (0.8 + ambient * 0.2 * sin(time * 2.1))
        drawFill(context, tone: .green, x: tx, y: ty, edge: edge, time: time, opacity: 1, glow: (1 - mix) * breathing)
        if mix > 0 {
            drawFill(context, tone: .yellow, x: tx, y: ty, edge: edge, time: time, opacity: mix, glow: breathing)
        }

        // Signal bars: each grows as the level passes 10, 20 and 30 dB, and bounces like an
        // equaliser while it moves.
        for (index, bar) in Metrics.bars.enumerated() {
            let i = Double(index)
            let grown = clamp01((value * Metrics.maxDB - i * 9) / 12)
            let bounce = 1 + activity * 0.32 * sin(time * (17 - i * 3.5) + i * 2.1)
            let height = min(Metrics.bars[2].height + 1.5, max(Metrics.barWidth, bar.height * grown * bounce))
            let shape = capsule(
                CGRect(
                    x: card.minX + bar.x - Metrics.barWidth / 2, y: card.minY + Metrics.barsBottom - height,
                    width: Metrics.barWidth, height: height
                )
            )
            context.fill(shape, with: .color(ToneColors.green.bar))
            if mix > 0 { context.fill(shape, with: .color(ToneColors.yellow.bar.opacity(mix))) }
        }
    }

    private func drawFill(
        _ base: GraphicsContext, tone: Tone, x: Double, y: Double, edge: Double, time: Double, opacity: Double, glow: Double
    ) {
        let colors = tone == .green ? ToneColors.green : ToneColors.yellow
        var context = base
        context.opacity = opacity

        let width = max(0, edge - Metrics.blockGap)
        let radius = min(Metrics.troughRadius, width / 2)
        let height = Metrics.troughHeight
        let block = Path(roundedRect: CGRect(x: x, y: y, width: width, height: height), cornerRadius: radius, style: .circular)

        var halo = context
        halo.opacity = opacity * glow
        halo.addFilter(.blur(radius: 7))
        halo.fill(
            Path(roundedRect: CGRect(x: x, y: y + 2, width: width, height: height), cornerRadius: radius, style: .circular),
            with: .color(colors.glow)
        )

        context.fill(
            block,
            with: .linearGradient(
                Gradient(colors: [colors.from, colors.to]),
                startPoint: CGPoint(x: x, y: y), endPoint: CGPoint(x: x + Metrics.troughWidth * 0.55, y: y + height)
            )
        )

        // The texture is pinned to the leading edge, so it travels with the level, and drifts slowly at rest.
        let pitch = Metrics.stripePitch
        let shift = (edge * 0.7 + time * 2.6).truncatingRemainder(dividingBy: pitch)
        var stripes = Path()
        var sx = -Metrics.stripeSlant - pitch * 2
        while sx < Metrics.troughWidth + pitch * 2 {
            stripes.move(to: CGPoint(x: x + shift + sx, y: y - 1))
            stripes.addLine(to: CGPoint(x: x + shift + sx + Metrics.stripeSlant, y: y + height + 1))
            sx += pitch
        }
        var texture = context
        texture.clip(to: block)
        texture.stroke(stripes, with: .color(.white.opacity(0.22)), lineWidth: 0.8)

        var needle = context
        needle.opacity = opacity * clamp01((edge - 5) / 8)
        needle.fill(
            capsule(
                CGRect(
                    x: x + edge + Metrics.needleAt - Metrics.needleWidth / 2, y: y + (height - Metrics.needleHeight) / 2,
                    width: Metrics.needleWidth, height: Metrics.needleHeight
                )
            ),
            with: .color(colors.needle)
        )
    }
}

private struct ToneColors {
    var from: Color
    var to: Color
    var glow: Color
    var needle: Color
    var bar: Color

    static let green = ToneColors(
        from: Color(hex: 0x1ccf45), to: Color(hex: 0x30e96b), glow: .rgba(40, 220, 90, 0.55),
        needle: Color(hex: 0x86ac96), bar: Color(hex: 0x22d94e)
    )
    static let yellow = ToneColors(
        from: Color(hex: 0xe7b400), to: Color(hex: 0xf9ea00), glow: .rgba(246, 214, 0, 0.5),
        needle: Color(hex: 0xb9ad90), bar: Color(hex: 0xf1c500)
    )
}

private enum Metrics {
    static let size = 164.0
    static let radius = 31.0
    /// The canvas overflows the card by this much on every side to fit its shadow.
    static let bleed = 24.0

    static let maxDB = 120.0
    static let band = 30.0

    static let troughX = 16.5
    static let troughY = 106.5
    static let troughWidth = 131.0
    static let troughHeight = 41.5
    static let troughRadius = 7.0
    /// The block stops short of the level, a thin needle marks it and the empty trough starts after.
    static let blockGap = 2.4
    static let needleAt = 1.1
    static let needleWidth = 1.8
    static let needleHeight = 33.0
    static let restGap = 4.6

    static let stripePitch = 4.4
    static let stripeSlant = 11.0

    /// Signal bars: centre x, resting height.
    static let bars: [(x: Double, height: Double)] = [(127.4, 5.4), (134.4, 12.1), (141.2, 18.8)]
    static let barsBottom = 61.2
    static let barWidth = 2.2

    /// autoPlay loop (seconds).
    static let start = 0.45
    static let rise = 1.7
    static let hold = 3.6
    static let reset = 0.6
    static let loop = start + rise + hold + reset
    /// A moment in the loop where everything is at rest: used when nothing is playing.
    static let restTime = start + rise + 1
}

private func clamp01(_ t: Double) -> Double { min(1, max(0, t)) }

private func easeInOutCubic(_ t: Double) -> Double {
    t < 0.5 ? 4 * t * t * t : 1 - pow(-2 * t + 2, 3) / 2
}

/// A damped swing: shoots past the target by about 8% and settles, like a meter needle.
private func swing(_ u: Double) -> Double {
    1 - exp(-8 * u) * cos(10 * u)
}

/// Level (0…1 of the scale) at `t` seconds into the autoPlay loop.
private func loopValue(_ t: Double, rest: Double) -> Double {
    if t < Metrics.start { return 0 }
    if t < Metrics.start + Metrics.rise { return rest * swing((t - Metrics.start) / Metrics.rise) }
    if t < Metrics.loop - Metrics.reset { return rest }
    return rest * (1 - easeInOutCubic((t - (Metrics.loop - Metrics.reset)) / Metrics.reset))
}

private func loopActivity(_ t: Double) -> Double {
    clamp01((t - Metrics.start) / 0.15) * clamp01((Metrics.start + Metrics.rise * 0.85 - t) / 0.6)
}

/// Scale of the band number `since` seconds after it stepped up: a quick swell, then it settles.
private func popScale(_ since: Double) -> Double {
    if since < 0 || since > 0.41 { return 1 }
    if since < 0.09 { return 1 + 0.28 * since / 0.09 }
    return 1 + 0.28 * pow(1 - (since - 0.09) / 0.32, 3)
}

private extension View {
    /// Places the view by its leading edge and vertical centre inside the card.
    func leading(_ x: Double, centerY: Double) -> some View {
        fixedSize()
            .frame(width: 300, alignment: .leading)
            .position(x: x + 150, y: centerY)
    }

    /// Places the view by its trailing edge and vertical centre inside the card.
    func trailing(_ x: Double, centerY: Double) -> some View {
        fixedSize()
            .frame(width: 300, alignment: .trailing)
            .position(x: x - 150, y: centerY)
    }
}

#Preview {
    ZStack {
        Color(hex: 0xced2da).ignoresSafeArea()
        HStack(spacing: 12) {
            NoiseLevelMeter(autoPlay: true, level: 40)
            NoiseLevelMeter(autoPlay: true, level: 60)
        }
    }
}

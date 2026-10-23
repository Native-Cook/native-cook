import SwiftUI

/// Earbuds battery widget, light or dark: the device name and mode over three capsules for the left
/// bud, the case and the right bud. The capsules charge one after another with a soft glowing
/// edge, a band of light keeps climbing through each fill, the low one breathes like a warning
/// light, the icon discs pop as their capsule lands and the arcs of the output glyph ripple.
struct BatteryCapsules: View {
    enum Variant {
        case light, dark
    }

    /// Controlled share of the charge shown in the reference, 0…1: at 1 the capsules show `levels`.
    var progress: Double? = nil
    /// Charges the three capsules one after another up to `levels`, holds, and starts over.
    var autoPlay = false
    var variant = Variant.light
    var owner = "Alex’s"
    var device = "Buds Pro"
    var mode = "Noise Cancellation"
    /// Charge of the left bud, the case and the right bud, 0…1.
    var levels = [0.8, 0.2, 0.1]

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    /// Origin of the ambient clock (pulses, breathing, ripples).
    @State private var epoch = Date.now
    /// When the current autoPlay / tapped run started; `nil` shows the resting frame.
    @State private var runStart: Date? = nil
    @State private var held = Tween(Metrics.rest)
    /// Keeps the timeline running for a controlled change under reduced motion.
    @State private var settling = false
    @State private var landing = Landing()

    /// Per-frame state: when each capsule last reached its level.
    private final class Landing {
        var before: Double? = nil
        var at = [Date.distantPast, Date.distantPast, Date.distantPast]

        func advance(to now: Date, value: Double) {
            defer { before = value }
            guard let before else { return }
            // A capsule reaching its level sets off its disc's pop.
            for index in 0..<3 where chargeAt(before, index) < 0.985 && chargeAt(value, index) >= 0.985 {
                at[index] = now
            }
        }
    }

    private var controlled: Bool { progress != nil }
    private var target: Double { min(1, max(0, progress ?? Metrics.rest)) }
    private var colors: Palette { variant == .dark ? .dark : .light }

    var body: some View {
        TimelineView(.animation(paused: reduceMotion && !settling)) { timeline in
            let date = timeline.date
            let time = reduceMotion ? 0 : date.timeIntervalSince(epoch)
            let value = controlled ? held.value(at: date) : loopValue(clock(at: date))
            let _ = landing.advance(to: date, value: value)
            let since = (0..<3).map { reduceMotion ? 10 : date.timeIntervalSince(landing.at[$0]) }

            ZStack {
                Canvas { context, _ in
                    draw(context, value: value, time: time, since: since)
                }
                .padding(-Metrics.bleed)

                VStack(alignment: .leading, spacing: 0) {
                    Text(owner).frame(height: 19.3)
                    Text(device).frame(height: 19.3)
                }
                .font(.system(size: 13.6, weight: .semibold))
                .tracking(-0.1)
                .foregroundStyle(colors.ink)
                .fixedSize()
                .frame(width: 112, height: 38.6, alignment: .topLeading)
                .position(x: 13 + 56, y: 10.4 + 19.3)

                Text(mode)
                    .font(.system(size: 12.8, weight: .regular))
                    .tracking(-0.1)
                    .foregroundStyle(colors.dim)
                    .fixedSize()
                    .frame(width: 146, alignment: .leading)
                    .position(x: 13 + 73, y: 60.6)

                ForEach(0..<3, id: \.self) { index in
                    Text("\(Int((levels[index] * 100 * chargeAt(value, index)).rounded()))%")
                        .font(.system(size: 12.6, weight: .medium))
                        .monospacedDigit()
                        .foregroundStyle(colors.ink)
                        .fixedSize()
                        .position(x: Metrics.capX + Metrics.capStep * Double(index) + Metrics.capW / 2, y: 92.2)
                }
            }
            .lineLimit(1)
            .frame(width: Metrics.size, height: Metrics.size)
            .accessibilityElement(children: .ignore)
            .accessibilityLabel(Text(verbatim: "\(owner) \(device), \(mode)"))
            .accessibilityValue(
                Text(verbatim: "Left, case, right: " + levels.map { "\(Int(($0 * target * 100).rounded()))%" }.joined(separator: ", "))
            )
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
        let colors = colors

        var shadow = context
        shadow.addFilter(.blur(radius: 12))
        shadow.fill(
            Path(
                roundedRect: CGRect(x: x + 8, y: y + 14, width: Metrics.size - 16, height: Metrics.size - 10),
                cornerRadius: Metrics.radius, style: .circular
            ),
            with: .color(colors.shadow)
        )
        context.fill(
            Path(roundedRect: CGRect(x: x, y: y, width: Metrics.size, height: Metrics.size), cornerRadius: Metrics.radius, style: .circular),
            with: .linearGradient(
                Gradient(colors: [colors.cardTop, colors.cardBottom]),
                startPoint: CGPoint(x: x, y: y), endPoint: CGPoint(x: x + Metrics.size, y: y + Metrics.size)
            )
        )

        for index in 0..<3 {
            let level = levels[index]
            let cx0 = x + Metrics.capX + Metrics.capStep * Double(index)
            let cy0 = y + Metrics.capY
            let tone = colors.fills[index]
            let capsuleRect = CGRect(x: cx0, y: cy0, width: Metrics.capW, height: Metrics.capH)
            let capsule = Path(roundedRect: capsuleRect, cornerRadius: Metrics.capR, style: .circular)

            let charge = level * chargeAt(value, index)
            let height = Metrics.capH * fillShare(charge)
            let top = cy0 + Metrics.capH - height
            // A low capsule breathes like a warning light.
            let breath = level <= Metrics.low ? 1 - ambient * 0.28 * (0.5 + 0.5 * sin(time * 4.2)) : 1
            let fillOpacity = clamp01(charge * 20) * breath
            let glowOpacity = colors.glow * fillOpacity * (0.8 + ambient * 0.2 * sin(time * 2 + Double(index)))

            if glowOpacity > 0.005 {
                var glow = context
                glow.addFilter(.blur(radius: 7))
                glow.fill(
                    Path(
                        roundedRect: CGRect(x: cx0 - 1, y: top, width: Metrics.capW + 2, height: height + 2),
                        cornerRadius: min(Metrics.capR, (height + 2) / 2), style: .circular
                    ),
                    with: .color(tone.opacity(glowOpacity))
                )
            }
            context.fill(capsule, with: .color(colors.track))

            var inside = context
            inside.clip(to: capsule)
            if fillOpacity > 0.005 {
                // Blurred so the top edge of the charge is soft, as in the reference.
                var soft = inside
                soft.addFilter(.blur(radius: 3.2))
                soft.fill(
                    Path(CGRect(x: cx0 - 10, y: top, width: Metrics.capW + 20, height: Metrics.capH + 12)),
                    with: .color(tone.opacity(fillOpacity))
                )
                var sheen = inside
                sheen.opacity = fillOpacity
                sheen.fill(
                    Path(CGRect(x: cx0, y: top, width: Metrics.capW, height: Metrics.capH)),
                    with: .linearGradient(
                        Gradient(stops: [
                            .init(color: .white.opacity(0), location: 0),
                            .init(color: .white.opacity(colors.sheen), location: 0.16),
                            .init(color: .black.opacity(0.1), location: 1),
                        ]),
                        startPoint: CGPoint(x: 0, y: top), endPoint: CGPoint(x: 0, y: cy0 + Metrics.capH)
                    )
                )
            }
            // A band of light climbs through the fill, as if charge were flowing in.
            let pulse = (time + Double(index) * 0.45).truncatingRemainder(dividingBy: 2.6) / 1.1
            if ambient > 0, pulse < 1 {
                var band = inside
                band.addFilter(.blur(radius: 5))
                band.fill(
                    Path(CGRect(x: cx0 - 6, y: cy0 + Metrics.capH + 6 - (height + 12) * pulse, width: Metrics.capW + 12, height: 9)),
                    with: .color(.white.opacity(0.5 * sin(.pi * pulse) * clamp01(height / 26)))
                )
            }

            var disc = context
            disc.translateBy(x: cx0 + Metrics.capW / 2, y: y + Metrics.discY)
            let scale = pop(since[index], ambient: ambient)
            disc.scaleBy(x: scale, y: scale)
            disc.fill(circle(.zero, Metrics.discR), with: .color(colors.discs[index]))
            if index == 1 {
                disc.stroke(caseBody, with: .color(colors.glyph), style: StrokeStyle(lineWidth: 1.5, lineJoin: .round))
                disc.stroke(caseSeam, with: .color(colors.glyph), lineWidth: 1.3)
                disc.fill(circle(CGPoint(x: 0, y: 1.9), 0.95), with: .color(colors.glyph))
            } else {
                var bud = disc
                bud.scaleBy(x: index == 0 ? 1 : -1, y: 1)
                bud.fill(budHead, with: .color(colors.glyph))
                bud.fill(budStem, with: .color(colors.glyph))
                // The ear tip, with its opening.
                bud.fill(circle(CGPoint(x: 2.7, y: -1.5), 2.2), with: .color(colors.glyph))
                bud.fill(circle(CGPoint(x: 2.9, y: -1.4), 1), with: .color(colors.discs[index]))
            }
        }

        // The "playing to" glyph: its arcs light one after another, inside out, like sound leaving the triangle.
        var cast = context
        cast.translateBy(x: x + Metrics.castX, y: y + Metrics.castY)
        for (index, r) in [3.5, 6, 8.5].enumerated() {
            let dim = ambient * 0.62 * pow(0.5 + 0.5 * sin(time * 3.2 - Double(index) * 1.25 + 2.4), 2)
            var arc = Path()
            arc.addArc(center: CGPoint(x: 0, y: 1.6), radius: r, startAngle: .degrees(140), endAngle: .degrees(40), clockwise: false)
            cast.stroke(arc, with: .color(colors.ink.opacity(1 - dim)), style: StrokeStyle(lineWidth: 1.25, lineCap: .round))
        }
        cast.stroke(castTriangle, with: .color(colors.cardTop), style: StrokeStyle(lineWidth: 3.4, lineJoin: .round))
        cast.fill(castTriangle, with: .color(colors.ink))
        cast.stroke(castTriangle, with: .color(colors.ink), style: StrokeStyle(lineWidth: 1, lineJoin: .round))
    }
}

private struct Palette {
    let cardTop: Color
    let cardBottom: Color
    let shadow: Color
    let track: Color
    let ink: Color
    let dim: Color
    let fills: [Color]
    let discs: [Color]
    let glyph: Color
    let glow: Double
    let sheen: Double

    static let light = Palette(
        cardTop: Color(hex: 0xd8dee8), cardBottom: Color(hex: 0xc9d1dd), shadow: Color.rgba(20, 30, 50, 0.22),
        track: Color(hex: 0xbcc4d0), ink: Color(hex: 0x0b0b0c), dim: Color(hex: 0x5f7894),
        fills: [Color(hex: 0x00d455), Color(hex: 0xffcb05), Color(hex: 0xff4245)],
        discs: [Color(hex: 0xe8f8ee), Color(hex: 0xfbf8ea), Color(hex: 0xfbefef)],
        glyph: Color(hex: 0x0b0b0c), glow: 0.28, sheen: 0.07
    )
    static let dark = Palette(
        cardTop: Color(hex: 0x1d232b), cardBottom: Color(hex: 0x11151a), shadow: .black.opacity(0.4),
        track: Color(hex: 0x343a43), ink: .white, dim: Color(hex: 0x7d858f),
        fills: [Color(hex: 0x06e23f), Color(hex: 0xffd60a), Color(hex: 0xff3b3b)],
        discs: [Color(hex: 0x06350f), Color(hex: 0x2a2609), Color(hex: 0x2b1719)],
        glyph: .white, glow: 0.55, sheen: 0.1
    )
}

private enum Metrics {
    static let size = 164.0
    static let radius = 26.0
    /// The canvas overflows the widget by this much on every side to fit its shadow.
    static let bleed = 24.0

    static let capX = 8.5
    static let capStep = 50.85
    static let capW = 44.8
    static let capY = 78.8
    static let capH = 76.2
    static let capR = 9.4
    static let discY = 132.8
    static let discR = 16.25
    static let castX = 140.7
    static let castY = 23.3

    // Where in the overall progress each capsule starts charging, and the share it takes.
    static let delay = [0, 0.14, 0.28]
    static let span = 0.72
    /// At or below this charge a capsule is "low" and its fill breathes.
    static let low = 0.1

    // The reference shows every capsule at its level; the autoPlay loop built around it (seconds).
    static let rest = 1.0
    static let start = 0.4
    static let draw = 2.0
    static let hold = 4.4
    static let reset = 0.6
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

/// 0…1: how far capsule `index` has charged at overall progress `p`. They start one after another.
private func chargeAt(_ p: Double, _ index: Int) -> Double {
    easeOutCubic((clamp01(p) - Metrics.delay[index]) / Metrics.span)
}

/// Share of the capsule's height the fill covers for a charge `v` (0…1), as measured on the reference.
private func fillShare(_ v: Double) -> Double { clamp01(v * 20) * (0.08 + 0.8 * v) }

/// Scale of a disc after its capsule lands: a quick overshoot.
private func pop(_ since: Double, ambient: Double) -> Double {
    let u = clamp01(since / 0.5)
    return 1 + ambient * 0.22 * sin(.pi * u) * (1 - u)
}

private func circle(_ centre: CGPoint, _ r: Double) -> Path {
    Path(ellipseIn: CGRect(x: centre.x - r, y: centre.y - r, width: r * 2, height: r * 2))
}

// Glyphs, drawn around the centre of their disc.
private let budHead = circle(CGPoint(x: -1.8, y: -3.6), 3.7)

private let budStem: Path = {
    var p = Path()
    p.move(to: CGPoint(x: -0.9, y: -2))
    p.addLine(to: CGPoint(x: 1.5, y: -2))
    p.addLine(to: CGPoint(x: 1.5, y: 6.3))
    p.addArc(center: CGPoint(x: 0.3, y: 6.3), radius: 1.2, startAngle: .degrees(0), endAngle: .degrees(180), clockwise: false)
    p.closeSubpath()
    return p
}()

private let caseBody = Path(roundedRect: CGRect(x: -7, y: -5.3, width: 14, height: 10.6), cornerRadius: 3.4, style: .circular)

private let caseSeam: Path = {
    var p = Path()
    p.move(to: CGPoint(x: -7, y: -1.3))
    p.addLine(to: CGPoint(x: 7, y: -1.3))
    return p
}()

private let castTriangle: Path = {
    var p = Path()
    p.move(to: CGPoint(x: 0, y: 2.6))
    p.addLine(to: CGPoint(x: 5.9, y: 8.6))
    p.addLine(to: CGPoint(x: -5.9, y: 8.6))
    p.closeSubpath()
    return p
}()

#Preview {
    ZStack {
        Color(hex: 0x6c8ab3).ignoresSafeArea()
        HStack(spacing: 18) {
            BatteryCapsules(autoPlay: true)
            BatteryCapsules(autoPlay: true, variant: .dark)
        }
    }
}

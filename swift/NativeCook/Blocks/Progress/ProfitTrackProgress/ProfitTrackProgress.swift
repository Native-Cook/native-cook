import SwiftUI

/// Two-layer asset card: a white row describing the asset and a dark panel with its profit
/// progress on a blue track that ends in a flame. The flame flickers and sheds embers, the knob
/// lands with a springy overshoot, and crossing 100% ignites the badge (flash, ring, sparks) while
/// the caption lights up and the infinity sign draws itself.
struct ProfitTrackProgress: View {
    /// Controlled position on the track, 0…1 (1 is `maxPercent`). Leave `nil` for the reference's 135%.
    var progress: Double? = nil
    /// Climbs from 0 to 135% by itself, holds, drops back and starts over.
    var autoPlay = false
    /// Profit shown when the knob reaches the flame.
    var maxPercent = 180.0
    var status = "Active"
    var name = "Vela GT 2025"
    var price = "270,675"
    var currency = "USDT"
    var apy = "178.12%"
    var units = 1
    /// Caption shown once profit passes 100%, and the one shown before that.
    var label = "Explosive profit"
    var pendingLabel = "Building profit"
    var onToggle: () -> Void = {}

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    /// Origin of the ambient clock (flicker, embers).
    @State private var epoch = Date.now
    /// When the current autoPlay / tapped run started; `nil` shows the resting frame.
    @State private var runStart: Date? = nil
    @State private var held = Tween(Metrics.rest)
    /// Keeps the timeline running for a controlled change under reduced motion.
    @State private var settling = false
    @State private var ignition = Ignition()

    /// Remembers the last value seen so crossing 100% on the way up can set off the ignition.
    private final class Ignition {
        var before = Metrics.rest
        var at = Date.distantPast
    }

    private var controlled: Bool { progress != nil }
    private var target: Double { min(1, max(0, progress ?? Metrics.rest)) }
    /// Position on the track where profit is 100%.
    private var threshold: Double { 100 / maxPercent }

    var body: some View {
        TimelineView(.animation(paused: reduceMotion && !settling)) { timeline in
            let date = timeline.date
            let time = reduceMotion ? 0 : date.timeIntervalSince(epoch)
            let value = controlled ? held.value(at: date) : loopValue(clock(at: date))
            let percent = Int((value * maxPercent).rounded())
            let lit = clamp01((value - threshold) / 0.03)
            let burst = burst(value: value, at: date)

            ZStack {
                Canvas { context, size in
                    draw(context, value: value, time: time, lit: lit, burst: burst)
                }
                .padding(-Metrics.bleed)
                .allowsHitTesting(false)

                asset
                    .accessibilityElement(children: .ignore)
                    .accessibilityLabel("\(name), \(status), \(price) \(currency), estimated APY \(apy), \(units) unit")

                Button(action: onToggle) {
                    Path { path in
                        path.move(to: CGPoint(x: 7.8, y: 13.2))
                        path.addLine(to: CGPoint(x: 11.4, y: 9.6))
                        path.addLine(to: CGPoint(x: 15, y: 13.2))
                    }
                    .stroke(.white, style: StrokeStyle(lineWidth: 1.4, lineCap: .round, lineJoin: .round))
                    .frame(width: 22.8, height: 22.8)
                }
                .buttonStyle(ToggleStyle())
                .accessibilityLabel("Collapse")
                .position(x: 309.7, y: 30)

                ZStack {
                    Text("PROFIT PROGRESS")
                        .font(.system(size: 9.5))
                        .kerning(3.2)
                        .foregroundStyle(Color(hex: 0x8b8e96))
                        .fixedSize()
                        .position(x: 171.6, y: 118.2)
                    Text("\(percent)%")
                        .font(.system(size: 18, weight: .bold))
                        .monospacedDigit()
                        .foregroundStyle(Color(hex: 0xeef0f6))
                        .leading(18.7, centerY: 145.7)
                    // The two captions cross-fade around 100%.
                    Text(pendingLabel)
                        .font(.system(size: 11.5, weight: .medium))
                        .foregroundStyle(Color(hex: 0x9c9ea5))
                        .opacity(1 - lit)
                        .trailing(289.6, centerY: 145.7)
                    Text(label)
                        .font(.system(size: 11.5, weight: .medium))
                        .foregroundStyle(Color(hex: 0x9c9ea5))
                        .opacity(lit)
                        .trailing(289.6, centerY: 145.7)
                }
                .lineLimit(1)
                .frame(width: Metrics.width, height: Metrics.height)
                .accessibilityElement(children: .ignore)
                .accessibilityLabel("Profit progress")
                .accessibilityValue("\(percent)%, \(percent >= 100 ? label : pendingLabel)")
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
            held.retarget(target, duration: reduceMotion ? 0.2 : 0.7, curve: Ease.out)
            settling = true
        }
        .task(id: settling) {
            guard settling else { return }
            try? await Task.sleep(for: .seconds(0.8))
            settling = false
        }
    }

    private var asset: some View {
        ZStack {
            Text(status)
                .font(.system(size: 9, weight: .medium))
                .foregroundStyle(Color(hex: 0x5fa468))
                .padding(.horizontal, 5)
                .frame(height: 14)
                .background(Color(hex: 0xe8f6e6), in: RoundedRectangle(cornerRadius: 3.5))
                .leading(79.3, centerY: 26)
            Text(name)
                .font(.system(size: 16, weight: .bold))
                .foregroundStyle(Color(hex: 0x12141a))
                .leading(79.3, centerY: 46.3)
            (Text(price).font(.system(size: 13.5, weight: .bold)).foregroundStyle(Metrics.blue)
                + Text(" \(currency)").font(.system(size: 9.5)).foregroundStyle(Color(hex: 0x5b7be6)))
                .italic()
                .monospacedDigit()
                .leading(79.3, centerY: 66)
            (Text("est. ").font(.system(size: 9.5)).italic().foregroundStyle(Color(hex: 0x7d828a))
                + Text("APY  ").font(.system(size: 9.5)).foregroundStyle(Color(hex: 0x9aa0a6))
                + Text(apy).font(.system(size: 13.5, weight: .bold)).foregroundStyle(Metrics.blue))
                .monospacedDigit()
                .trailing(292, centerY: 27)
            (Text("\(units)").fontWeight(.bold).foregroundStyle(Color(hex: 0x1d1f24))
                + Text(" UNIT").foregroundStyle(Color(hex: 0x9aa0a6)))
                .font(.system(size: 9))
                .padding(.horizontal, 5)
                .frame(height: 14.3)
                .overlay(RoundedRectangle(cornerRadius: 3.5).strokeBorder(Color(hex: 0xdcdee2), lineWidth: 1))
                .trailing(321.2, centerY: 66.35)
        }
        .lineLimit(1)
        .frame(width: Metrics.width, height: Metrics.height)
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

    /// Seconds since profit last crossed 100% on the way up; large when there is nothing to show.
    private func burst(value: Double, at date: Date) -> Double {
        if reduceMotion { return 10 }
        if ignition.before < threshold, value >= threshold { ignition.at = date }
        ignition.before = value
        return date.timeIntervalSince(ignition.at)
    }

    private func draw(_ context: GraphicsContext, value: Double, time: Double, lit: Double, burst: Double) {
        let x = Metrics.bleed
        let y = Metrics.bleed
        let ambient = reduceMotion ? 0.0 : 1.0
        let card = CGRect(x: x, y: y, width: Metrics.width, height: Metrics.height)
        let shape = Path(roundedRect: card, cornerRadius: Metrics.radius, style: .circular)
        let tx = x + Metrics.trackX
        let ty = y + Metrics.trackY
        let bx = x + Metrics.badgeX
        let badgeRadius = Metrics.badgeRadius

        // The pale plate the card sits on in the reference, then its shadow.
        context.fill(
            Path(
                roundedRect: CGRect(x: x - 13, y: y - 13, width: card.width + 26, height: card.height + 60),
                cornerRadius: 30, style: .circular
            ),
            with: .linearGradient(
                Gradient(stops: [
                    .init(color: .black.opacity(0.075), location: 0),
                    .init(color: .black.opacity(0.05), location: 0.6),
                    .init(color: .black.opacity(0), location: 1),
                ]),
                startPoint: CGPoint(x: x, y: y - 13),
                endPoint: CGPoint(x: x, y: y + card.height + 47)
            )
        )
        var shadow = context
        shadow.addFilter(.blur(radius: 14))
        shadow.fill(
            Path(
                roundedRect: CGRect(x: x + 8, y: y + 16, width: card.width - 16, height: card.height - 8),
                cornerRadius: Metrics.radius, style: .circular
            ),
            with: .color(.rgba(20, 20, 45, 0.3))
        )

        var inside = context
        inside.clip(to: shape)
        inside.fill(shape, with: .color(Color(hex: 0x17181d)))
        inside.fillEllipse(
            center: CGPoint(x: x, y: y + 110), radii: CGSize(width: 150, height: 150),
            gradient: Gradient(colors: [.rgba(46, 58, 130, 0.5), .rgba(46, 58, 130, 0)])
        )
        inside.fillEllipse(
            center: CGPoint(x: x + 170, y: y + card.height + 40), radii: CGSize(width: 190, height: 190),
            gradient: Gradient(colors: [.rgba(66, 38, 120, 0.5), .rgba(66, 38, 120, 0)])
        )
        inside.fillEllipse(
            center: CGPoint(x: bx, y: ty - 10), radii: CGSize(width: 130, height: 130),
            gradient: Gradient(colors: [.rgba(130, 80, 24, 0.42), .rgba(130, 80, 24, 0)])
        )
        context.fill(
            Path(
                roundedRect: CGRect(x: x, y: y, width: card.width, height: Metrics.topHeight),
                cornerRadius: Metrics.radius, style: .circular
            ),
            with: .color(.white)
        )

        // Thumbnail with a drawn car.
        context.fill(
            Path(roundedRect: CGRect(x: x + 18.2, y: y + 20.7, width: 51, height: 51), cornerRadius: 8, style: .circular),
            with: .color(Color(hex: 0xe5e6f4))
        )
        var car = context
        car.translateBy(x: x + 18.2 + 5.5, y: y + 20.7 + 19)
        car.fill(capsule(CGRect(x: 3, y: 14.6, width: 34, height: 3)), with: .color(.rgba(30, 40, 80, 0.18)))
        car.fill(carBody, with: .color(Color(hex: 0x263352)))
        car.fill(carGlass, with: .color(Color(hex: 0xaebbd6)))
        for wheel in [9.5, 30.5] {
            car.fill(circle(x: wheel, y: 13.4, r: 3.4), with: .color(Color(hex: 0x151a26)))
            car.fill(circle(x: wheel, y: 13.4, r: 1.4), with: .color(Color(hex: 0xc9cfdb)))
        }

        // Track: what is still ahead warms up towards the flame, what is behind is blue.
        let knob = knobX(value)
        let trackHeight = Metrics.trackHeight
        let trackStart = CGPoint(x: tx, y: ty)
        let trackEnd = CGPoint(x: tx + Metrics.trackWidth, y: ty)
        context.fill(
            capsule(CGRect(x: tx + knob, y: ty - trackHeight / 2, width: Metrics.trackWidth - knob, height: trackHeight)),
            with: .linearGradient(
                Gradient(stops: [
                    .init(color: .rgba(215, 118, 27, 0.22), location: 0),
                    .init(color: .rgba(215, 118, 27, 0.36), location: 0.62),
                    .init(color: Color(hex: 0xd7761b), location: 0.77),
                    .init(color: Color(hex: 0xdcab44), location: 0.88),
                    .init(color: Color(hex: 0xfeef91), location: 1),
                ]),
                startPoint: trackStart, endPoint: trackEnd
            )
        )
        context.fill(
            capsule(CGRect(x: tx, y: ty - trackHeight / 2, width: knob, height: trackHeight)),
            with: .linearGradient(
                Gradient(stops: [
                    .init(color: Color(hex: 0x18d5da), location: 0),
                    .init(color: Color(hex: 0x4f8ae2), location: 0.19),
                    .init(color: Metrics.blue, location: 0.38),
                    .init(color: Color(hex: 0x4667d9), location: 1),
                ]),
                startPoint: trackStart, endPoint: trackEnd
            )
        )

        // Flame badge with its two halos; the ring and sparks are the ignition at 100%.
        var badge = context
        badge.translateBy(x: bx, y: ty)
        var halo = badge
        let haloScale = 1 + ambient * 0.05 * sin(time * 2.2)
        halo.scaleBy(x: haloScale, y: haloScale)
        halo.opacity = 0.6 + 0.4 * lit
        halo.fill(circle(x: 0, y: 0, r: 42), with: .color(.rgba(255, 214, 120, 0.07)))
        halo.fill(circle(x: 0, y: 0, r: 21), with: .color(.rgba(255, 214, 120, 0.14)))
        if burst < 0.75 {
            badge.stroke(
                circle(x: 0, y: 0, r: badgeRadius + 34 * easeOutCubic(burst / 0.75)),
                with: .color(Color(hex: 0xffd978, opacity: 0.7 * (1 - burst / 0.75))),
                lineWidth: 1.5
            )
        }
        if burst < 0.6 {
            let distance = badgeRadius + 4 + 24 * easeOutCubic(burst / 0.6)
            for angle in sparks {
                badge.fill(
                    circle(x: distance * cos(angle), y: distance * sin(angle), r: 1.6),
                    with: .color(Color(hex: 0xffe27a, opacity: 1 - burst / 0.6))
                )
            }
        }
        badge.fill(
            circle(x: 0, y: 0, r: badgeRadius),
            with: .radialGradient(
                Gradient(stops: [
                    .init(color: Color(hex: 0xfff6ad), location: 0),
                    .init(color: Color(hex: 0xf8e182), location: 0.6),
                    .init(color: Color(hex: 0xf0cf63), location: 1),
                ]),
                center: CGPoint(x: 0, y: -2), startRadius: 0, endRadius: badgeRadius
            )
        )
        // Embers drift up from the flame.
        let heat = ambient * (0.3 + 0.7 * lit)
        for ember in embers {
            let life = (time / ember.period + ember.phase).truncatingRemainder(dividingBy: 1)
            badge.fill(
                circle(
                    x: ember.reach * sin(life * 5 + ember.phase * 9) * life,
                    y: -badgeRadius * 0.4 - ember.rise * life,
                    r: ember.radius
                ),
                with: .color(Color(hex: 0xffc24a, opacity: heat * clamp01(life / 0.15) * (1 - life) * 0.9))
            )
        }
        // The flame leans and stretches like a real one; it is anchored at its base.
        var flame = badge
        let size = 0.82 + 0.18 * lit
        flame.translateBy(x: 0, y: 8.2)
        flame.rotate(by: .radians(ambient * 0.07 * sin(time * 7.3)))
        flame.scaleBy(
            x: size * (1 - ambient * 0.04 * sin(time * 13.1)),
            y: size * (1 + ambient * (0.07 * sin(time * 13.1) + 0.04 * sin(time * 23.7 + 1)))
        )
        flame.scaleBy(x: 0.7, y: 0.7)
        flame.translateBy(x: -10, y: -24)
        flame.fill(
            flameOuter,
            with: .linearGradient(
                Gradient(colors: [Color(hex: 0xf2401d), Color(hex: 0xfb6a1c), Color(hex: 0xff9a26)]),
                startPoint: CGPoint(x: 10, y: 0), endPoint: CGPoint(x: 10, y: 24)
            )
        )
        var inner = flame
        inner.translateBy(x: 10, y: 24)
        inner.scaleBy(x: 1, y: 1 + ambient * 0.12 * sin(time * 17.3 + 2))
        inner.translateBy(x: -10, y: -24)
        inner.fill(
            flameInner,
            with: .linearGradient(
                Gradient(colors: [Color(hex: 0xffb52e), Color(hex: 0xffe45e), Color(hex: 0xfff7c4)]),
                startPoint: CGPoint(x: 10, y: 11), endPoint: CGPoint(x: 10, y: 24)
            )
        )
        if burst < 1 {
            badge.fill(circle(x: 0, y: 0, r: badgeRadius), with: .color(Color(hex: 0xfffbe0, opacity: exp(-6 * burst))))
        }

        var knobShadow = context
        knobShadow.addFilter(.blur(radius: 3))
        knobShadow.fill(circle(x: tx + knob, y: ty + 1, r: Metrics.knobRadius + 1), with: .color(.black.opacity(0.35)))
        context.fill(circle(x: tx + knob, y: ty, r: Metrics.knobRadius), with: .color(Metrics.blue))
        context.fill(circle(x: tx + knob, y: ty, r: 2 + ambient * 0.35 * sin(time * 3.1)), with: .color(.white))

        // The infinity sign draws itself as profit passes 100%.
        var sign = context
        sign.translateBy(x: x + Metrics.loopX, y: y + Metrics.loopY)
        let stroke = StrokeStyle(lineWidth: 2.3, lineCap: .round)
        sign.stroke(infinity, with: .color(.white.opacity(0.16)), style: stroke)
        let drawn = clamp01((value - threshold) / 0.07)
        if drawn > 0 {
            sign.stroke(infinity.trimmedPath(from: 0, to: drawn), with: .color(.white), style: stroke)
        }
    }
}

private enum Metrics {
    static let width = 340.0
    static let height = 212.0
    static let radius = 18.0
    static let topHeight = 92.0
    /// The canvas overflows the card by this much on every side to fit its plate and shadow.
    static let bleed = 40.0

    static let trackX = 22.0
    static let trackY = 179.6
    static let trackWidth = 285.5
    static let trackHeight = 6.9
    static let knobRadius = 7.2
    static let badgeX = trackX + trackWidth
    static let badgeRadius = 13.8
    static let loopX = 310.2
    static let loopY = 146.0

    /// Resting position in the reference (135% of a 180% track), and the autoPlay loop around it (seconds).
    static let rest = 0.75
    static let start = 0.4
    static let fill = 2.6
    static let hold = 3.6
    static let reset = 0.6
    static let loop = start + fill + hold + reset
    /// A moment in the loop where everything is at rest: used when nothing is playing.
    static let restTime = start + fill + 1

    static let blue = Color(hex: 0x4f70e4)
}

private struct Ember {
    let phase: Double, period: Double, reach: Double, rise: Double, radius: Double
}

private let embers = [
    Ember(phase: 0.0, period: 1.7, reach: 5, rise: 30, radius: 1.3),
    Ember(phase: 0.23, period: 2.1, reach: -6, rise: 36, radius: 1.0),
    Ember(phase: 0.47, period: 1.5, reach: 3, rise: 26, radius: 1.5),
    Ember(phase: 0.66, period: 1.9, reach: -3, rise: 33, radius: 1.1),
    Ember(phase: 0.85, period: 2.3, reach: 7, rise: 38, radius: 0.9),
]

private let sparks = [-2.6, -2.05, -1.5, -0.95, -0.4, 0.3, 2.85, 3.5]

private func circle(x: Double, y: Double, r: Double) -> Path {
    Path(ellipseIn: CGRect(x: x - r, y: y - r, width: r * 2, height: r * 2))
}

/// Flame in a 20 × 24 box, its base at the bottom centre.
private let flameOuter: Path = {
    var p = Path()
    p.move(to: CGPoint(x: 10, y: 0))
    p.addCurve(to: CGPoint(x: 17, y: 15), control1: CGPoint(x: 11, y: 5), control2: CGPoint(x: 17, y: 8))
    p.addCurve(to: CGPoint(x: 10, y: 24), control1: CGPoint(x: 17, y: 20), control2: CGPoint(x: 14, y: 24))
    p.addCurve(to: CGPoint(x: 3, y: 15), control1: CGPoint(x: 6, y: 24), control2: CGPoint(x: 3, y: 20))
    p.addCurve(to: CGPoint(x: 6, y: 8.5), control1: CGPoint(x: 3, y: 12), control2: CGPoint(x: 4.5, y: 10))
    p.addCurve(to: CGPoint(x: 8.5, y: 12), control1: CGPoint(x: 6.5, y: 11), control2: CGPoint(x: 7.5, y: 12))
    p.addCurve(to: CGPoint(x: 10, y: 0), control1: CGPoint(x: 8, y: 8), control2: CGPoint(x: 8.5, y: 3))
    p.closeSubpath()
    return p
}()

private let flameInner: Path = {
    var p = Path()
    p.move(to: CGPoint(x: 10, y: 11))
    p.addCurve(to: CGPoint(x: 13.5, y: 19), control1: CGPoint(x: 11, y: 14), control2: CGPoint(x: 13.5, y: 15.5))
    p.addCurve(to: CGPoint(x: 10, y: 24), control1: CGPoint(x: 13.5, y: 22), control2: CGPoint(x: 12, y: 24))
    p.addCurve(to: CGPoint(x: 6.5, y: 19), control1: CGPoint(x: 8, y: 24), control2: CGPoint(x: 6.5, y: 22))
    p.addCurve(to: CGPoint(x: 10, y: 11), control1: CGPoint(x: 6.5, y: 16), control2: CGPoint(x: 9, y: 14))
    p.closeSubpath()
    return p
}()

private let infinity: Path = {
    var p = Path()
    p.move(to: .zero)
    p.addCurve(to: CGPoint(x: -11, y: 0), control1: CGPoint(x: -3, y: -5.5), control2: CGPoint(x: -11, y: -5.5))
    p.addCurve(to: .zero, control1: CGPoint(x: -11, y: 5.5), control2: CGPoint(x: -3, y: 5.5))
    p.addCurve(to: CGPoint(x: 11, y: 0), control1: CGPoint(x: 3, y: -5.5), control2: CGPoint(x: 11, y: -5.5))
    p.addCurve(to: .zero, control1: CGPoint(x: 11, y: 5.5), control2: CGPoint(x: 3, y: 5.5))
    return p
}()

/// Side view of a saloon in a 40 × 17 box.
private let carBody: Path = {
    var p = Path()
    p.move(to: CGPoint(x: 1, y: 12))
    p.addCurve(to: CGPoint(x: 5, y: 8), control1: CGPoint(x: 1, y: 9.5), control2: CGPoint(x: 2.5, y: 8.5))
    p.addLine(to: CGPoint(x: 11, y: 7.2))
    p.addCurve(to: CGPoint(x: 19, y: 2), control1: CGPoint(x: 13, y: 4), control2: CGPoint(x: 15.5, y: 2.2))
    p.addLine(to: CGPoint(x: 26, y: 2))
    p.addCurve(to: CGPoint(x: 33.5, y: 7), control1: CGPoint(x: 29, y: 2), control2: CGPoint(x: 31, y: 3.5))
    p.addLine(to: CGPoint(x: 37, y: 7.8))
    p.addCurve(to: CGPoint(x: 39.2, y: 11), control1: CGPoint(x: 38.6, y: 8.2), control2: CGPoint(x: 39.2, y: 9.5))
    p.addLine(to: CGPoint(x: 39.2, y: 12.4))
    p.addCurve(to: CGPoint(x: 38.2, y: 13.4), control1: CGPoint(x: 39.2, y: 13), control2: CGPoint(x: 38.8, y: 13.4))
    p.addLine(to: CGPoint(x: 2, y: 13.4))
    p.addCurve(to: CGPoint(x: 1, y: 12.4), control1: CGPoint(x: 1.4, y: 13.4), control2: CGPoint(x: 1, y: 13))
    p.closeSubpath()
    return p
}()

private let carGlass: Path = {
    var p = Path()
    p.move(to: CGPoint(x: 13.5, y: 7))
    p.addCurve(to: CGPoint(x: 19.3, y: 3.4), control1: CGPoint(x: 15, y: 4.6), control2: CGPoint(x: 16.8, y: 3.4))
    p.addLine(to: CGPoint(x: 22, y: 3.4))
    p.addLine(to: CGPoint(x: 22, y: 7))
    p.closeSubpath()
    p.move(to: CGPoint(x: 23.4, y: 3.4))
    p.addLine(to: CGPoint(x: 26, y: 3.4))
    p.addCurve(to: CGPoint(x: 31.6, y: 7), control1: CGPoint(x: 28.2, y: 3.4), control2: CGPoint(x: 29.8, y: 4.6))
    p.addLine(to: CGPoint(x: 23.4, y: 7))
    p.closeSubpath()
    return p
}()

private func clamp01(_ t: Double) -> Double { min(1, max(0, t)) }

private func easeInOutCubic(_ t: Double) -> Double {
    t < 0.5 ? 4 * t * t * t : 1 - pow(-2 * t + 2, 3) / 2
}

private func easeOutCubic(_ t: Double) -> Double { 1 - pow(1 - clamp01(t), 3) }

/// Runs a little past the target and springs back: the knob lands with an overshoot.
private func easeOutBack(_ t: Double) -> Double {
    let c = 1.15
    return 1 + (c + 1) * pow(t - 1, 3) + c * pow(t - 1, 2)
}

/// Position on the track at `t` seconds into the autoPlay loop.
private func loopValue(_ t: Double) -> Double {
    if t < Metrics.start { return 0 }
    if t < Metrics.start + Metrics.fill { return Metrics.rest * easeOutBack((t - Metrics.start) / Metrics.fill) }
    if t < Metrics.loop - Metrics.reset { return Metrics.rest }
    return Metrics.rest * (1 - easeInOutCubic((t - (Metrics.loop - Metrics.reset)) / Metrics.reset))
}

private func knobX(_ p: Double) -> Double {
    Metrics.knobRadius + (Metrics.trackWidth - Metrics.knobRadius - Metrics.badgeRadius) * clamp01(p)
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

/// Blue disc that scales down while pressed.
private struct ToggleStyle: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .background(Metrics.blue, in: Circle())
            .contentShape(Circle().inset(by: -10))
            .scaleEffect(configuration.isPressed ? 0.97 : 1)
            .animation(.timingCurve(0.23, 1, 0.32, 1, duration: 0.13), value: configuration.isPressed)
            .sensoryFeedback(.impact(weight: .light), trigger: configuration.isPressed) { _, pressed in pressed }
    }
}

#Preview {
    ZStack {
        Color(hex: 0xfafafa).ignoresSafeArea()
        ProfitTrackProgress(autoPlay: true)
    }
}

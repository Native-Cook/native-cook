import SwiftUI

struct HabitStat {
    var value: Int
    var suffix = ""
    var label: String
}

/// Habit tracker summary: a big constancy ring between two small ones on a soft raised card, with
/// three stat tiles under it. The big ring draws behind a white-hot head that throws sparks, its
/// glow breathes and a ripple keeps leaving the head once it has landed; the percentage counts up
/// and settles with a small bounce, the small rings follow, and the tiles rise in one by one.
struct HabitRing: View {
    /// Controlled constancy, 0…1: the big ring and its percentage.
    var progress: Double? = nil
    /// Draws the rings up to the values shown in the reference, holds, and starts over.
    var autoPlay = false
    /// Constancy at the resting frame, 0…1.
    var constancy = 0.82
    var label = "CONSTANCE"
    /// The two small rings: value out of 100 and caption.
    var week = 90
    var weekLabel = "SEMAINE"
    var day = 53
    var dayLabel = "JOUR"
    var caption = "Average of 18 habits over 18 days\nelapsed."
    var stats = [
        HabitStat(value: 202, label: "CHECK-INS"),
        HabitStat(value: 18, suffix: "\u{2009}j", label: "BEST STREAK"),
        HabitStat(value: 14, label: "PERFECT DAYS"),
    ]

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    /// Origin of the ambient clock (sparks, breathing, ripples).
    @State private var epoch = Date.now
    /// When the current autoPlay / tapped run started; `nil` shows the resting frame.
    @State private var runStart: Date? = nil
    @State private var held = Tween(0)
    /// Keeps the timeline running for a controlled change under reduced motion.
    @State private var settling = false
    @State private var pace = Pace()

    /// Per-frame state: how fast the ring is moving and when it last came to rest.
    private final class Pace {
        var last: Date? = nil
        var before = 0.0
        /// 0…1, smoothed so the sparks fade out instead of cutting off.
        var moving = 0.0
        var landed = Date.distantPast

        func advance(to date: Date, share: Double) {
            guard let last else {
                self.last = date
                before = share
                return
            }
            let dt = min(date.timeIntervalSince(last), 0.064)
            guard dt > 0 else { return }
            self.last = date
            let speed = clamp01(max(0, share - before) / dt * 4)
            before = share
            let was = moving
            moving += (speed - moving) * min(1, dt * 10)
            // The ring coming to rest sets off the bounce and the first ripple.
            if was > 0.05, moving <= 0.05, share > 0.01 { landed = date }
        }
    }

    private var controlled: Bool { progress != nil }
    private var rest: Double { constancy }
    private var target: Double { min(1, max(0, progress ?? rest)) }

    var body: some View {
        TimelineView(.animation(paused: reduceMotion && !settling)) { timeline in
            let date = timeline.date
            let time = reduceMotion ? 0 : date.timeIntervalSince(epoch)
            let clock = clock(at: date)
            let value = controlled ? held.value(at: date) : loopValue(clock, rest: rest)
            let _ = reduceMotion ? () : pace.advance(to: date, share: value)
            let moving = reduceMotion ? 0 : pace.moving
            let since = reduceMotion ? 10 : date.timeIntervalSince(pace.landed)
            let ambient = reduceMotion ? 0.0 : 1.0
            let mono = Font.system(size: 8.2, design: .monospaced)

            ZStack {
                Canvas { context, _ in
                    draw(context, value: value, time: time, clock: clock, moving: moving, since: since)
                }
                .padding(-Metrics.bleed)

                HStack(alignment: .top, spacing: 1) {
                    Text(verbatim: "\(Int((value * 100).rounded()))")
                        .font(.system(size: 58, weight: .heavy))
                        .tracking(-1.5)
                        .monospacedDigit()
                        .frame(height: 64)
                    Text("%")
                        .font(.system(size: 24, weight: .heavy))
                        .frame(height: 28)
                        .padding(.top, 1.5)
                }
                .foregroundStyle(Color(hex: 0xf0f0f2))
                .fixedSize()
                .scaleEffect(ambient > 0 ? pop(since, amount: 0.07) : 1)
                .position(x: Metrics.width / 2 + 1.7, y: 72.4 + 32)

                Text(label)
                    .font(.system(size: 9, design: .monospaced))
                    .tracking(3.1)
                    .foregroundStyle(Color(hex: 0x9a9da4))
                    .fixedSize()
                    .position(x: Metrics.width / 2 + 1.55, y: 149.7)

                ForEach(0..<2, id: \.self) { index in
                    let full = index == 0 ? week : day
                    Text(verbatim: "\(Int((Double(full) * smallAt(value, rest: rest, index)).rounded()))")
                        .font(.system(size: 11.8, weight: .bold))
                        .monospacedDigit()
                        .foregroundStyle(Color(hex: 0xf0f0f2))
                        .fixedSize()
                        .position(x: Metrics.smallX[index], y: Metrics.smallY)
                    Text(index == 0 ? weekLabel : dayLabel)
                        .font(mono)
                        .tracking(2.2)
                        .foregroundStyle(Color(hex: 0x8b8f97))
                        .fixedSize()
                        .position(x: Metrics.smallX[index] + 1.1, y: 143.8)
                }

                Text(caption)
                    .font(.system(size: 12.6))
                    .tracking(-0.2)
                    .lineSpacing(3.4)
                    .multilineTextAlignment(.center)
                    .lineLimit(2)
                    .foregroundStyle(Color(hex: 0x9b9ea6))
                    .frame(width: Metrics.width - 100)
                    .position(x: Metrics.width / 2, y: 238.9)

                ForEach(0..<3, id: \.self) { index in
                    let at = tileAt(clock, index)
                    let stat = stats[index]
                    let count = Int((Double(stat.value) * easeOutCubic(value / max(rest, 0.001))).rounded())
                    ZStack {
                        Text(verbatim: "\(count)\(stat.suffix)")
                            .font(.system(size: 20.8, weight: .heavy))
                            .tracking(-0.3)
                            .monospacedDigit()
                            .foregroundStyle(Color(hex: 0xf0f0f2))
                            .fixedSize()
                            .frame(width: Metrics.tileW - 14.9, alignment: .leading)
                            .position(x: 14.9 + (Metrics.tileW - 14.9) / 2, y: 24.7)
                        Text(stat.label)
                            .font(mono)
                            .tracking(1.3)
                            .foregroundStyle(Color(hex: 0x8b8f97))
                            .fixedSize()
                            .frame(width: Metrics.tileW - 14.9, alignment: .leading)
                            .position(x: 14.9 + (Metrics.tileW - 14.9) / 2, y: 47.7)
                    }
                    .frame(width: Metrics.tileW, height: Metrics.tileH)
                    .opacity(at)
                    .position(
                        x: Metrics.tileStep * Double(index) + Metrics.tileW / 2,
                        y: Metrics.tileY + Metrics.tileH / 2 + 10 * (1 - at)
                    )
                }
            }
            .lineLimit(1)
            .frame(width: Metrics.width, height: Metrics.height)
            .accessibilityElement(children: .ignore)
            .accessibilityLabel(label)
            .accessibilityValue(
                Text(
                    verbatim: "\(Int((target * 100).rounded()))%, \(weekLabel) \(week), \(dayLabel) \(day). "
                        + caption.replacingOccurrences(of: "\n", with: " ")
                )
            )
        }
        .contentShape(Rectangle())
        .onTapGesture { restart() }
        .onAppear { sync(initial: true) }
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

    private func sync(initial: Bool = false) {
        runStart = autoPlay && !controlled && !reduceMotion ? .now : nil
        if initial || (controlled && held.duration == 0) { held = Tween(target) }
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

    private func draw(_ context: GraphicsContext, value: Double, time: Double, clock: Double, moving: Double, since: Double) {
        let x = Metrics.bleed
        let y = Metrics.bleed
        let ambient = reduceMotion ? 0.0 : 1.0
        let centre = CGPoint(x: x + Metrics.cx, y: y + Metrics.cy)

        // The card: raised from the page by a dark shadow below and a faint light above.
        plate(
            context, CGRect(x: x, y: y, width: Metrics.width, height: Metrics.cardH), radius: Metrics.cardR,
            shadow: CGSize(width: 6, height: 9), shadowBlur: 13, shadowOpacity: 0.38, light: true,
            colors: [Color(hex: 0x373940), Color(hex: 0x2f3137)], slant: 0.35
        )

        // The big ring: track, breathing glow, the ring itself.
        let share = clamp01(value)
        let ringOpacity = clamp01(value * 80)
        context.stroke(circle(centre, Metrics.ringR), with: .color(Color(hex: 0x202125)), lineWidth: Metrics.ringW)
        let ring = arc(centre, Metrics.ringR, share: share)
        let glow = ringOpacity * (0.3 + ambient * (0.13 * sin(time * 1.7) + 0.25 * moving))
        if glow > 0.005 {
            var soft = context
            soft.addFilter(.blur(radius: 9))
            soft.stroke(ring, with: .color(orange.opacity(glow)), style: StrokeStyle(lineWidth: Metrics.ringW + 4, lineCap: .round))
        }
        if ringOpacity > 0.005 {
            context.stroke(ring, with: .color(orange.opacity(ringOpacity)), style: StrokeStyle(lineWidth: Metrics.ringW, lineCap: .round))
        }

        let angle = -Double.pi / 2 + tau * share
        let head = CGPoint(x: centre.x + Metrics.ringR * cos(angle), y: centre.y + Metrics.ringR * sin(angle))
        // When the ring lands, and every few seconds after, a ripple leaves its head.
        var beat = since.truncatingRemainder(dividingBy: 3.4)
        if beat < 0 { beat += 3.4 }
        let ripple = beat / 0.9
        if ambient > 0, ripple < 1, moving <= 0.05, value >= 0.01 {
            context.stroke(
                circle(head, Metrics.ringW / 2 + 12 * easeOutCubic(ripple)),
                with: .color(Color(hex: 0xffb78a, opacity: 0.55 * (1 - ripple))), lineWidth: 1.6
            )
        }
        // Sparks thrown off the head while it draws: left behind it, drifting off the ring to either side.
        let power = ambient * moving
        if power >= 0.02 {
            var sparks = Path()
            for ember in emberSpecs {
                let u = (time * 1.5 + ember.phase).truncatingRemainder(dividingBy: 1)
                let trail = angle - u * 0.42
                let r = Metrics.ringR + ember.side * (4 + 13 * u)
                sparks.addPath(circle(CGPoint(x: centre.x + r * cos(trail), y: centre.y + r * sin(trail)), ember.r * (1 - u) * power))
            }
            context.fill(sparks, with: .color(Color(hex: 0xffc9a2)))
            // The head burns white.
            var hot = context
            hot.addFilter(.blur(radius: 5))
            hot.fill(circle(head, Metrics.ringW / 2 + 2), with: .color(Color(hex: 0xfff1e4, opacity: ringOpacity * power)))
        }

        for index in 0..<2 {
            let small = CGPoint(x: x + Metrics.smallX[index], y: y + Metrics.smallY)
            let full = Double(index == 0 ? week : day) / 100
            context.stroke(circle(small, Metrics.smallR), with: .color(Color(hex: 0x222328)), lineWidth: Metrics.smallW)
            let drawn = full * smallAt(value, rest: rest, index)
            if drawn > 0.001 {
                context.stroke(
                    arc(small, Metrics.smallR, share: drawn), with: .color(orange),
                    style: StrokeStyle(lineWidth: Metrics.smallW, lineCap: .round)
                )
            }
        }

        for index in 0..<3 {
            let at = tileAt(clock, index)
            guard at > 0.005 else { continue }
            var tile = context
            tile.opacity = at
            tile.translateBy(x: 0, y: 10 * (1 - at))
            plate(
                tile,
                CGRect(x: x + Metrics.tileStep * Double(index), y: y + Metrics.tileY, width: Metrics.tileW, height: Metrics.tileH),
                radius: Metrics.tileR, shadow: CGSize(width: 4, height: 6), shadowBlur: 8, shadowOpacity: 0.34, light: false,
                colors: [Color(hex: 0x37393f), Color(hex: 0x303238)], slant: 0.4
            )
        }
    }

    /// A raised plate: soft shadow, slanted gradient and a hairline border.
    private func plate(
        _ context: GraphicsContext, _ rect: CGRect, radius: Double, shadow: CGSize, shadowBlur: Double,
        shadowOpacity: Double, light: Bool, colors: [Color], slant: Double
    ) {
        var dark = context
        dark.addFilter(.blur(radius: shadowBlur))
        dark.fill(
            Path(roundedRect: rect.offsetBy(dx: shadow.width, dy: shadow.height), cornerRadius: radius, style: .circular),
            with: .color(.black.opacity(shadowOpacity))
        )
        if light {
            var glint = context
            glint.addFilter(.blur(radius: 9))
            glint.fill(
                Path(roundedRect: rect.offsetBy(dx: -4, dy: -4), cornerRadius: radius, style: .circular),
                with: .color(.white.opacity(0.03))
            )
        }
        context.fill(
            Path(roundedRect: rect, cornerRadius: radius, style: .circular),
            with: .linearGradient(
                Gradient(colors: colors),
                startPoint: rect.origin, endPoint: CGPoint(x: rect.minX + rect.width * slant, y: rect.maxY)
            )
        )
        context.stroke(
            Path(roundedRect: rect.insetBy(dx: 0.5, dy: 0.5), cornerRadius: radius - 0.5, style: .circular),
            with: .color(.white.opacity(0.075)), lineWidth: 1
        )
    }
}

private let orange = Color(hex: 0xf0742d)

private enum Metrics {
    static let width = 340.0
    static let cardH = 285.0
    static let cardR = 28.5
    static let tileY = 308.7
    static let tileW = 106.5
    static let tileH = 68.0
    static let tileStep = 116.75
    static let tileR = 15.0
    static let height = tileY + tileH
    /// The canvas overflows the component by this much on every side to fit the soft shadows.
    static let bleed = 30.0

    static let cx = 170.0
    static let cy = 115.7
    static let ringR = 81.4
    static let ringW = 13.7
    static let smallX = [44.7, 294.7]
    static let smallY = 107.7
    static let smallR = 22.5
    static let smallW = 4.8

    // autoPlay loop (seconds).
    static let start = 0.5
    static let draw = 2.4
    static let hold = 4.6
    static let reset = 0.7
    static let loop = start + draw + hold + reset
    /// A moment in the loop where everything is at rest at the reference value: used when nothing is playing.
    static let restTime = start + draw + 1.8
}

private struct Ember {
    /// Which side of the ring it drifts to, −1…1.
    let side: Double
    let phase: Double
    let r: Double
}

private let emberSpecs = [
    Ember(side: -0.9, phase: 0.0, r: 1.9), Ember(side: 0.5, phase: 0.14, r: 1.5),
    Ember(side: -0.2, phase: 0.29, r: 2.1), Ember(side: 0.95, phase: 0.43, r: 1.4),
    Ember(side: -0.6, phase: 0.57, r: 1.7), Ember(side: 0.15, phase: 0.71, r: 2.0),
    Ember(side: 0.7, phase: 0.86, r: 1.5),
]

private func clamp01(_ t: Double) -> Double { min(1, max(0, t)) }

private func easeInOutCubic(_ t: Double) -> Double {
    t < 0.5 ? 4 * t * t * t : 1 - pow(-2 * t + 2, 3) / 2
}

private func easeOutCubic(_ t: Double) -> Double { 1 - pow(1 - clamp01(t), 3) }

/// Constancy at `t` seconds into the autoPlay loop, where `rest` is the reference value.
private func loopValue(_ t: Double, rest: Double) -> Double {
    if t < Metrics.start { return 0 }
    if t < Metrics.start + Metrics.draw { return rest * easeOutCubic((t - Metrics.start) / Metrics.draw) }
    if t < Metrics.start + Metrics.draw + Metrics.hold { return rest }
    return rest * (1 - easeInOutCubic((t - Metrics.start - Metrics.draw - Metrics.hold) / Metrics.reset))
}

/// 0…1: how far small ring `index` is drawn when the big ring is at `share` of `rest`. The second starts later.
private func smallAt(_ share: Double, rest: Double, _ index: Int) -> Double {
    easeOutCubic(clamp01(share / max(rest, 0.001)) * 1.25 - 0.25 * Double(index))
}

/// 0…1: how far stat tile `index` has risen into place at `t` seconds into the loop.
private func tileAt(_ t: Double, _ index: Int) -> Double {
    let out = Metrics.start + Metrics.draw + Metrics.hold
    if t >= out { return 1 - clamp01((t - out) / (Metrics.reset * 0.6)) }
    return easeOutCubic((t - 0.15 - 0.14 * Double(index)) / 0.55)
}

/// Scale after the ring lands: a quick overshoot.
private func pop(_ since: Double, amount: Double) -> Double {
    let u = clamp01(since / 0.55)
    return 1 + amount * sin(.pi * u) * (1 - u)
}

private func circle(_ centre: CGPoint, _ r: Double) -> Path {
    Path(ellipseIn: CGRect(x: centre.x - r, y: centre.y - r, width: r * 2, height: r * 2))
}

/// Part of a ring, clockwise from 12 o'clock.
private func arc(_ centre: CGPoint, _ r: Double, share: Double) -> Path {
    var p = Path()
    p.addArc(center: centre, radius: r, startAngle: .degrees(-90), endAngle: .degrees(-90 + 360 * share), clockwise: false)
    return p
}

#Preview {
    ZStack {
        Color(hex: 0x25272c).ignoresSafeArea()
        HabitRing(autoPlay: true)
    }
}

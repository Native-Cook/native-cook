import SwiftUI

/// Dark glass "next level" card: a mission badge, a line of encouragement, and a lime bar whose
/// white-hot head rides from mission to mission, with dots for the missions still to do and
/// captions that count them. The head trails a comet tail and sparks while it moves and rings
/// when it lands.
struct MissionGlowProgress: View {
    /// Share of the missions completed, 0…1. Leave nil to show the reference's 4 of 7.
    var progress: Double? = nil
    /// Plays by itself: the bar advances one mission at a time up to 4 of 7, holds, and starts over.
    var autoPlay = false
    /// Missions in the level.
    var total = 7
    var title = "Good job!"
    /// Draws the reference's orange and blue backdrop glow around the card; the parent clips it.
    var backdrop = true
    var label = "Missions"
    var action: () -> Void = {}

    @State private var taps = 0

    var body: some View {
        Button {
            taps += 1
            action()
        } label: {
            Color.clear
        }
        .buttonStyle(Style(card: self, taps: taps))
        .accessibilityRepresentation {
            ProgressView(value: min(max(progress ?? Double(min(Metrics.restDone, total)) / Double(total), 0), 1)) {
                Text(label)
            }
        }
    }

    private struct Style: ButtonStyle {
        let card: MissionGlowProgress
        let taps: Int

        func makeBody(configuration: Configuration) -> some View {
            Surface(
                progress: card.progress, autoPlay: card.autoPlay, total: card.total, title: card.title,
                backdrop: card.backdrop, taps: taps, isPressed: configuration.isPressed
            )
        }
    }
}

private enum Metrics {
    static let width = 330.0
    static let height = 139.0
    static let radius = 26.0
    /// The canvas overflows the card by this much on every side to fit the backdrop glow.
    static let bleed = 90.0
    static let badge = CGPoint(x: 38.6, y: 40.5)
    static let badgeR = 24.0
    static let barX = 15.0
    static let barY = 83.5
    static let barW = 298.0
    static let barH = 20.0
    static let barCY = barY + barH / 2
    /// The last three missions sit this far apart, counted back from the bar's right end.
    static let dotPitch = 49.0
    static let dotEnd = 16.0
    static let dotR = 2.2
    static let spread = 3
    static let grid = 4.0
    static let sparks = 6
    /// The reference rests at 4 of 7 missions.
    static let restDone = 4
    static let heatGain = 4.0
}

/// The autoPlay run is one clock in seconds: one hop per mission, a hold, a slide back.
private enum Clock {
    static let first = 0.5
    static let hopEvery = 0.85
    static let hopLength = 0.6
    /// The head lands (and rings) a little before its overshoot has settled.
    static let landAfter = 0.3
    static let ringLength = 0.55
    static let hold = 2.6
    static let reset = 0.5
    static let tail = 0.4
}

private let lime = Color(hex: 0xc4f84a)

private func seg(_ t: Double, _ from: Double, _ to: Double) -> Double {
    min(1, max(0, (t - from) / (to - from)))
}

private func easeInOut(_ x: Double) -> Double {
    x < 0.5 ? 4 * x * x * x : 1 - pow(-2 * x + 2, 3) / 2
}

private func easeOutBack(_ x: Double) -> Double {
    let c = 1.7
    return 1 + (c + 1) * pow(x - 1, 3) + c * pow(x - 1, 2)
}

private func disc(_ center: CGPoint, _ radius: Double) -> Path {
    Path(ellipseIn: CGRect(x: center.x - radius, y: center.y - radius, width: radius * 2, height: radius * 2))
}

/// Where the head sits (from the bar's left end) with `missions` done. The last three missions are
/// spread out at the reference's spacing; the earlier ones share what is left of the bar.
private func headX(_ missions: Double, total: Int) -> Double {
    let knee = Double(max(total - Metrics.spread, 0))
    let kneeX = Metrics.barW - Metrics.dotEnd - Double(min(Metrics.spread, total)) * Metrics.dotPitch
    if missions <= knee { return knee > 0 ? kneeX * missions / knee : 0 }
    return Metrics.barW - Metrics.dotEnd - (Double(total) - missions) * Metrics.dotPitch
}

/// The card, its grain and the backdrop never change, so they get a canvas that is drawn once.
private struct Glass: View {
    let backdrop: Bool

    var body: some View {
        Canvas { context, _ in
            let o = Metrics.bleed
            let w = Metrics.width
            let h = Metrics.height
            let card = Path(roundedRect: CGRect(x: o, y: o, width: w, height: h), cornerRadius: Metrics.radius)

            if backdrop {
                let orange = CGPoint(x: o + w + 30, y: o - 120)
                context.fill(
                    disc(orange, 300),
                    with: .radialGradient(
                        Gradient(stops: [
                            .init(color: .rgba(225, 75, 5, 0.95), location: 0),
                            .init(color: .rgba(150, 45, 0, 0.55), location: 0.35),
                            .init(color: .rgba(70, 22, 0, 0.25), location: 0.7),
                            .init(color: .rgba(40, 12, 0, 0), location: 1),
                        ]),
                        center: orange, startRadius: 0, endRadius: 300
                    )
                )
                let blue = CGPoint(x: o + w / 2 - 30, y: o + h + 170)
                context.fill(
                    disc(blue, 280),
                    with: .radialGradient(
                        Gradient(stops: [
                            .init(color: .rgba(24, 34, 170, 0.95), location: 0),
                            .init(color: .rgba(14, 20, 100, 0.55), location: 0.5),
                            .init(color: .rgba(6, 8, 40, 0), location: 1),
                        ]),
                        center: blue, startRadius: 0, endRadius: 280
                    )
                )
            }

            context.fill(
                card,
                with: .linearGradient(
                    Gradient(colors: [.rgba(52, 40, 38, 0.62), .rgba(10, 10, 18, 0.55)]),
                    startPoint: CGPoint(x: o, y: o), endPoint: CGPoint(x: o + w * 0.3, y: o + h)
                )
            )

            // The fine halftone grain of the glass, fading out towards the bottom.
            var grain = Path()
            var gy = Metrics.grid / 2
            while gy < h * 0.8 {
                var gx = Metrics.grid / 2
                while gx < w {
                    grain.addEllipse(in: CGRect(x: o + gx - 0.5, y: o + gy - 0.5, width: 1, height: 1))
                    gx += Metrics.grid
                }
                gy += Metrics.grid
            }
            var clipped = context
            clipped.clip(to: card)
            clipped.fill(
                grain,
                with: .linearGradient(
                    Gradient(colors: [.white.opacity(0.13), .white.opacity(0.07), .white.opacity(0)]),
                    startPoint: CGPoint(x: 0, y: o), endPoint: CGPoint(x: 0, y: o + h * 0.8)
                )
            )

            context.stroke(
                Path(
                    roundedRect: CGRect(x: o + 0.5, y: o + 0.5, width: w - 1, height: h - 1),
                    cornerRadius: Metrics.radius - 0.5
                ),
                with: .linearGradient(
                    Gradient(colors: [.white.opacity(0.2), .white.opacity(0.07), .white.opacity(0.12)]),
                    startPoint: CGPoint(x: o, y: o), endPoint: CGPoint(x: o, y: o + h)
                ),
                lineWidth: 1
            )
        }
        .padding(-Metrics.bleed)
        .allowsHitTesting(false)
    }
}

private struct Surface: View {
    let progress: Double?
    let autoPlay: Bool
    let total: Int
    let title: String
    let backdrop: Bool
    let taps: Int
    let isPressed: Bool

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    /// Missions done while controlled, a fraction while the head is between two.
    @State private var value = Tween(0)
    @State private var settling = false
    @State private var settleToken = 0
    /// When the controlled value last moved forwards; the head rings shortly after.
    @State private var advanced: Date?
    /// When the autoPlay run started; the glow, the beacon and the sparks count from here too.
    @State private var start = Date.now
    @State private var epoch = Date.now

    private var playing: Bool { autoPlay && progress == nil && !reduceMotion }
    private var rest: Int { min(Metrics.restDone, total) }
    private var hops: Double { Clock.first + Double(rest - 1) * Clock.hopEvery + Clock.hopLength }
    private var loop: Double { hops + Clock.hold + Clock.reset + Clock.tail }

    private func clock(at date: Date) -> Double {
        max(date.timeIntervalSince(start), 0).truncatingRemainder(dividingBy: loop)
    }

    /// Missions done at `date`.
    private func missions(at date: Date) -> Double {
        if progress != nil { return value.value(at: date) }
        guard playing else { return Double(rest) }
        let t = clock(at: date)
        var m = 0.0
        for k in 0..<rest {
            let begin = Clock.first + Double(k) * Clock.hopEvery
            m += easeOutBack(seg(t, begin, begin + Clock.hopLength))
        }
        return m * (1 - easeInOut(seg(t, hops + Clock.hold, hops + Clock.hold + Clock.reset)))
    }

    /// 0…1 after the head lands on a mission: spreads a ring from it.
    private func ring(at date: Date) -> Double {
        if progress != nil {
            guard let advanced, !reduceMotion else { return 0 }
            let since = date.timeIntervalSince(advanced)
            return seg(since, Clock.landAfter, Clock.landAfter + Clock.ringLength)
        }
        guard playing else { return 0 }
        let t = clock(at: date)
        var ring = 0.0
        for k in 0..<rest {
            let begin = Clock.first + Double(k) * Clock.hopEvery + Clock.landAfter
            let a = seg(t, begin, begin + Clock.ringLength)
            if a > 0, a < 1 { ring = a }
        }
        return ring
    }

    var body: some View {
        TimelineView(.animation(paused: reduceMotion && !settling)) { timeline in
            let date = timeline.date
            let m = missions(at: date)
            // How fast the head is moving: stretches the comet tail and throws sparks.
            let speed = abs(m - missions(at: date.addingTimeInterval(-0.06))) / Double(total) / 0.06
            let heat = reduceMotion ? 0 : min(1, speed * Metrics.heatGain)
            // The captions only change when a whole mission is reached.
            let done = min(total, max(0, Int(m.rounded())))
            let left = total - done

            ZStack(alignment: .topLeading) {
                Glass(backdrop: backdrop)
                Canvas { context, _ in
                    draw(
                        context, missions: m, ring: ring(at: date), heat: heat,
                        time: reduceMotion ? 0 : date.timeIntervalSince(epoch)
                    )
                }
                .padding(-Metrics.bleed)

                (Text(verbatim: title + " ").fontWeight(.bold) + Text(verbatim: message(left)))
                    .font(.system(size: 13))
                    .kerning(0.1)
                    .lineSpacing(2.5)
                    .foregroundStyle(Color(hex: 0xdbd9da))
                    .frame(width: 240, alignment: .leading)
                    .offset(x: 75, y: 23.75)
                HStack {
                    Text(verbatim: "\(done) \(plural(done)) Completed")
                    Spacer(minLength: 0)
                    Text(verbatim: "\(left) \(plural(left)) Remaining")
                }
                .font(.system(size: 11.5).monospacedDigit())
                .kerning(0.1)
                .lineLimit(1)
                .foregroundStyle(Color(hex: 0x51565a))
                .frame(width: Metrics.width - 31, height: 16)
                .offset(x: 14, y: 109.5)
            }
            .frame(width: Metrics.width, height: Metrics.height, alignment: .topLeading)
        }
        .contentShape(RoundedRectangle(cornerRadius: Metrics.radius))
        .scaleEffect(isPressed ? 0.97 : 1)
        .animation(.timingCurve(0.23, 1, 0.32, 1, duration: 0.13), value: isPressed)
        .sensoryFeedback(.impact(weight: .light), trigger: isPressed) { _, pressed in pressed }
        // A tap restarts the demo run.
        .onChange(of: taps) { start = .now }
        .onChange(of: progress, initial: true) { old, progress in
            guard let progress else { return }
            let next = min(max(progress, 0), 1) * Double(total)
            // The first call only reports the starting value: show it without a run-up.
            let first = old == progress
            if !first, next > value.value(at: .now) { advanced = .now }
            // Value changes keep a short tween even with motion reduced.
            value.retarget(next, duration: first ? 0 : reduceMotion ? 0.2 : 0.7, curve: Ease.out)
            settling = true
            settleToken += 1
        }
        .task(id: settleToken) {
            try? await Task.sleep(for: .seconds(max(value.duration, Clock.landAfter + Clock.ringLength) + 0.05))
            if !Task.isCancelled { settling = false }
        }
    }

    private func plural(_ count: Int) -> String { count == 1 ? "Mission" : "Missions" }

    private func message(_ left: Int) -> String {
        left > 0
            ? "Just complete \(left) more \(plural(left).lowercased()) to advance to the next level."
            : "Every mission is complete. On to the next level."
    }

    private func draw(_ context: GraphicsContext, missions: Double, ring: Double, heat: Double, time: Double) {
        let o = Metrics.bleed
        let top = o + Metrics.barY
        let cy = o + Metrics.barCY
        let h = Metrics.barH
        let breath = (reduceMotion ? 0.85 : 0.8 + 0.12 * sin(time * 1.9)) + 0.2 * heat

        drawBadge(context, breath: breath)

        let track = capsule(CGRect(x: o + Metrics.barX, y: top, width: Metrics.barW, height: h))
        context.fill(track, with: .color(.rgba(22, 22, 22, 0.9)))
        for index in 1...max(total, 1) {
            drawDot(context, index: index, missions: missions, time: time)
        }

        // Nothing is lit until the first mission is under way.
        let lit = seg(missions, 0.02, 0.3)
        guard lit > 0 else { return }
        var bar = context
        bar.opacity = lit
        let head = CGPoint(x: o + Metrics.barX + max(0, headX(missions, total: total)), y: cy)

        // A wide lime haze and a tight halo around the head.
        var haze = bar
        haze.opacity = lit * min(1, breath)
        haze.addFilter(.blur(radius: 18))
        haze.fill(disc(head, 34), with: .color(.rgba(160, 235, 30, 0.32)))
        var halo = bar
        halo.opacity = lit * min(1, breath)
        halo.addFilter(.blur(radius: 7))
        halo.fill(disc(head, 14 + 4 * heat), with: .color(.rgba(215, 255, 60, 1)))
        if ring > 0, ring < 1 {
            bar.stroke(
                disc(head, 9 + 20 * (1 - pow(1 - ring, 3))),
                with: .color(lime.opacity(0.85 * (1 - ring))), lineWidth: 1.6
            )
        }

        let fill = capsule(CGRect(x: o + Metrics.barX, y: top, width: head.x + h / 2 - o - Metrics.barX, height: h))
        bar.fill(
            fill,
            with: .linearGradient(
                Gradient(stops: [
                    .init(color: Color(hex: 0xd5e255), location: 0),
                    .init(color: Color(hex: 0xb3d038), location: 0.28),
                    .init(color: Color(hex: 0x80b520), location: 0.66),
                    .init(color: Color(hex: 0x9ccb28), location: 1),
                ]),
                startPoint: CGPoint(x: o + Metrics.barX, y: 0), endPoint: CGPoint(x: head.x + h / 2, y: 0)
            )
        )

        var inside = bar
        inside.clip(to: fill)
        // Lit from above like a glass tube.
        var shine = inside
        shine.addFilter(.blur(radius: 2.5))
        shine.fill(
            capsule(CGRect(x: o + Metrics.barX + 4, y: top + 2, width: Metrics.barW, height: 5)),
            with: .color(.white.opacity(0.22))
        )
        var shade = inside
        shade.addFilter(.blur(radius: 3))
        shade.fill(
            capsule(CGRect(x: o + Metrics.barX, y: top + h - 4, width: Metrics.barW, height: 6)),
            with: .color(.rgba(40, 70, 0, 0.3))
        )
        // The white-hot core stretches back into a tail while the head moves.
        let tail = 40 + 34 * heat
        var core = inside
        core.addFilter(.blur(radius: 6))
        core.fill(
            Path(ellipseIn: CGRect(x: head.x + 7 - tail, y: cy - 7.5, width: tail, height: 15)),
            with: .color(.rgba(255, 255, 185, 1))
        )
        var spot = inside
        spot.addFilter(.blur(radius: 4))
        spot.fill(disc(head, 7.5), with: .color(Color(hex: 0xfffff0)))
        if ring > 0 {
            inside.fill(fill, with: .color(.white.opacity(0.7 * (1 - seg(ring, 0, 0.5)))))
        }

        // Sparks shed backwards from the head while it moves.
        guard heat > 0.01 else { return }
        for index in 0..<Metrics.sparks {
            let i = Double(index)
            let side = index % 2 == 1 ? 1.0 : -1.0
            let reach = 0.5 + 0.5 * (i * 0.37).truncatingRemainder(dividingBy: 1)
            let life = (time * 1.7 + i / Double(Metrics.sparks)).truncatingRemainder(dividingBy: 1)
            let center = CGPoint(
                x: head.x - 4 - (10 + 30 * reach) * life,
                y: cy + side * (5 + 17 * reach * life) + 6 * life * life
            )
            bar.fill(disc(center, 1.4 * (1 - 0.6 * life)), with: .color(Color(hex: 0xeaff8a).opacity(heat * (1 - life))))
        }
    }

    /// Mission badge: a red card in a ring lit orange from the left and red from the right.
    private func drawBadge(_ context: GraphicsContext, breath: Double) {
        let r = Metrics.badgeR
        var badge = context
        badge.translateBy(x: Metrics.bleed + Metrics.badge.x, y: Metrics.bleed + Metrics.badge.y)
        let from = CGPoint(x: -r, y: r * 0.6)
        let to = CGPoint(x: r, y: -r * 0.6)

        var glow = badge
        glow.opacity = min(1, breath)
        glow.addFilter(.blur(radius: 6))
        glow.stroke(
            disc(.zero, r),
            with: .linearGradient(
                Gradient(colors: [.rgba(255, 160, 40, 0.9), .rgba(230, 30, 50, 0.9)]), startPoint: from, endPoint: to
            ),
            lineWidth: 5
        )
        badge.fill(
            disc(.zero, r),
            with: .radialGradient(
                Gradient(colors: [Color(hex: 0x2a0d12), Color(hex: 0x0d0709)]),
                center: CGPoint(x: 4, y: -4), startRadius: 0, endRadius: r * 1.2
            )
        )
        badge.stroke(
            disc(.zero, r - 0.75),
            with: .linearGradient(
                Gradient(colors: [Color(hex: 0xffb04a), Color(hex: 0xf0553a), Color(hex: 0xd8243c)]),
                startPoint: from, endPoint: to
            ),
            lineWidth: 1.5
        )

        var card = badge
        card.rotate(by: .radians(0.2))
        card.fill(
            Path(roundedRect: CGRect(x: -10.5, y: -14, width: 21, height: 28), cornerRadius: 3.5),
            with: .linearGradient(
                Gradient(colors: [Color(hex: 0xff8a5c), Color(hex: 0xe83038), Color(hex: 0xa5121f)]),
                startPoint: CGPoint(x: -10, y: -14), endPoint: CGPoint(x: 10, y: 14)
            )
        )
        card.stroke(
            Path(roundedRect: CGRect(x: -10, y: -13.5, width: 20, height: 27), cornerRadius: 3),
            with: .color(.rgba(255, 200, 180, 0.55)), lineWidth: 0.8
        )
        card.stroke(disc(CGPoint(x: -1.5, y: 3), 4.6), with: .color(.rgba(255, 150, 140, 0.75)), lineWidth: 1.8)
        card.fill(disc(CGPoint(x: -1.5, y: 3), 1.4), with: .color(.rgba(120, 10, 20, 0.7)))
        var marks = Path()
        for x in [-7.0, -4.4, -1.8] {
            marks.move(to: CGPoint(x: x, y: -7.5))
            marks.addLine(to: CGPoint(x: x + 1.6, y: -11.1))
        }
        card.stroke(marks, with: .color(.white.opacity(0.85)), lineWidth: 1.1)
        for y in [-4.0, -1.4, 1.2, 3.8] {
            card.fill(disc(CGPoint(x: 6.6, y: y), 0.65), with: .color(.white.opacity(0.85)))
        }
    }

    /// A mission still to do: grey, or a lime beacon when it is the next one.
    private func drawDot(_ context: GraphicsContext, index: Int, missions: Double, time: Double) {
        let c = CGPoint(
            x: Metrics.bleed + Metrics.barX + headX(Double(index), total: total), y: Metrics.bleed + Metrics.barCY
        )
        // How many missions ahead of the head this dot is.
        let ahead = Double(index) - missions
        let shown = seg(ahead, 0.2, 0.45)
        guard shown > 0 else { return }
        let next = shown * (1 - seg(ahead, 1.05, 1.3))
        context.fill(disc(c, Metrics.dotR), with: .color(Color(hex: 0x434343).opacity(shown)))
        guard next > 0 else { return }

        var glow = context
        glow.opacity = next * (reduceMotion ? 0.55 : 0.55 + 0.25 * sin(time * 3))
        glow.addFilter(.blur(radius: 4))
        glow.fill(disc(c, 6), with: .color(.rgba(170, 255, 60, 0.9)))
        // The beacon sends out a faint ring every so often.
        let beat = time.truncatingRemainder(dividingBy: 1.8) / 1.1
        if !reduceMotion, beat < 1 {
            context.stroke(
                disc(c, Metrics.dotR + 7 * beat), with: .color(lime.opacity(0.6 * next * (1 - beat))), lineWidth: 1
            )
        }
        context.fill(disc(c, Metrics.dotR), with: .color(lime.opacity(next)))
    }
}

#Preview {
    ZStack {
        Color(hex: 0x08060a).ignoresSafeArea()
        MissionGlowProgress(autoPlay: true)
    }
}

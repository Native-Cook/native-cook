import SwiftUI

struct GoalFriend {
    var initials: String
    var color: Color
    var ink: Color
    /// Share of their own goal reached, 0…1: the ring around the avatar.
    var share: Double
}

/// Leaderboard live activity: a compact pill with the score and place, and the expanded panel with
/// the score against the goal, a thin progress line, three friends and a button. The score rolls up
/// like an odometer, the line fills behind a glowing head with a highlight running along it, the
/// trophy jumps each time a rival is passed, and the friends' rings sweep in one after another.
struct GoalScoreBar: View {
    /// Controlled share of the goal reached, 0…1.
    var progress: Double? = nil
    /// Counts the score up to `score`, climbing the ranking on the way, holds, and starts over.
    var autoPlay = false
    /// Score shown at the resting frame.
    var score = 8814
    var goal = 10000
    /// Scores of the other people in the ranking: passing one moves the trophy up a place.
    var rivals = [9480, 8120, 6730, 5210]
    var friends = [
        GoalFriend(initials: "MK", color: Color(hex: 0x33363d), ink: .white, share: 0.75),
        GoalFriend(initials: "JL", color: Color(hex: 0xf6b90f), ink: Color(hex: 0x2a1c00), share: 0.66),
        GoalFriend(initials: "TS", color: Color(hex: 0x90a78f), ink: Color(hex: 0x16240f), share: 0.42),
    ]
    var buttonLabel = "All friends"
    var onAllFriends: (() -> Void)? = nil

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    /// Origin of the ambient clock (shimmer, glow pulse).
    @State private var epoch = Date.now
    /// When the current autoPlay / tapped run started; `nil` shows the resting frame.
    @State private var runStart: Date? = nil
    @State private var held = Tween(0)
    /// Keeps the timeline running for a controlled change under reduced motion.
    @State private var settling = false
    @State private var roll = Roll()

    /// Per-frame state of the rolling number and the trophy.
    private final class Roll {
        var last: Date? = nil
        var before = 0.0
        /// Change of the share per second: lights the head of the line.
        var speed = 0.0
        var rankedAt = Date.distantPast
        /// Where each column is (0…10), units first, and whether it is shown (leading zeros fold away).
        var pos = [0.0, 0, 0, 0]
        var shown = [1.0, 0, 0, 0]

        func advance(to date: Date, share: Double, goal: Int, rivals: [Int], rolling: Bool) {
            let points = Int((share * Double(goal)).rounded())
            guard rolling, let last else {
                // First frame, or reduced motion: nothing rolls, the columns simply show the digits.
                self.last = date
                before = share
                speed = 0
                for k in 0..<4 {
                    pos[k] = Double(points / tenTo(k) % 10)
                    shown[k] = k == 0 || points >= tenTo(k) ? 1 : 0
                }
                return
            }
            let dt = min(date.timeIntervalSince(last), 0.064)
            guard dt > 0 else { return }
            self.last = date
            speed = (share - before) / dt
            // Passing a rival moves the trophy up a place.
            if rankOf(points, rivals) < rankOf(Int((before * Double(goal)).rounded()), rivals) { rankedAt = date }
            // Each column rolls towards its digit in the direction the score is moving.
            let dir = share >= before ? 1.0 : -1.0
            before = share
            for k in 0..<4 {
                let digit = Double(points / tenTo(k) % 10)
                var dist = ((digit - pos[k]) * dir).truncatingRemainder(dividingBy: 10)
                if dist < 0 { dist += 10 }
                if dist > 9.99 { dist = 0 }
                let step = max(Metrics.rollMin, min(dist * 10, Metrics.rollMax)) * dt
                if step >= dist {
                    pos[k] = digit
                } else {
                    pos[k] = (pos[k] + dir * step + 10).truncatingRemainder(dividingBy: 10)
                }
                if k > 0 { shown[k] += ((points >= tenTo(k) ? 1 : 0) - shown[k]) * min(1, dt * 14) }
            }
        }
    }

    private var controlled: Bool { progress != nil }
    private var rest: Double { Double(score) / Double(goal) }
    private var target: Double { min(1, max(0, progress ?? rest)) }

    var body: some View {
        TimelineView(.animation(paused: reduceMotion && !settling)) { timeline in
            let date = timeline.date
            let time = reduceMotion ? 0 : date.timeIntervalSince(epoch)
            let value = controlled ? held.value(at: date) : loopValue(clock(at: date), rest: rest)
            let _ = roll.advance(to: date, share: value, goal: goal, rivals: rivals, rolling: !reduceMotion)
            let points = Int((value * Double(goal)).rounded())
            let rank = rankOf(points, rivals)
            let sinceRank = reduceMotion ? 10 : date.timeIntervalSince(roll.rankedAt)

            ZStack {
                Canvas { context, _ in
                    draw(context, value: value, time: time, sinceRank: sinceRank, speed: roll.speed)
                }
                .padding(-Metrics.bleed)
                .allowsHitTesting(false)

                ZStack {
                    Text(verbatim: "\(points)")
                        .font(.system(size: 13.6, weight: .semibold))
                        .monospacedDigit()
                        .foregroundStyle(.white)
                        .fixedSize()
                        .frame(width: 80, alignment: .leading)
                        .position(x: Metrics.pillX + 9 + 40, y: 18)
                    Text(verbatim: "\(rank)")
                        .font(.system(size: 13.6, weight: .medium))
                        .monospacedDigit()
                        .foregroundStyle(orange)
                        .fixedSize()
                        .frame(width: 30, alignment: .trailing)
                        .position(x: Metrics.pillX + Metrics.pillW - 8.7 - 15, y: 18)

                    HStack(alignment: .top, spacing: 0) {
                        ForEach([3, 2, 1, 0], id: \.self) { k in
                            column(pos: roll.pos[k], shown: roll.shown[k])
                        }
                        Text(verbatim: "/ \(spaced(goal))")
                            .font(.system(size: 16, weight: .medium))
                            .foregroundStyle(Color(hex: 0x6c6c6e))
                            .fixedSize()
                            .frame(height: 20)
                            .padding(.leading, 8)
                            .padding(.top, 19.5)
                    }
                    .frame(width: 290, height: Metrics.digitH, alignment: .topLeading)
                    .position(x: 22.2 + 145, y: Metrics.panelY + 12.7 + Metrics.digitH / 2)

                    Text(ordinal(rank))
                        .font(.system(size: 16, weight: .medium))
                        .foregroundStyle(orange)
                        .fixedSize()
                        .frame(width: 40, alignment: .trailing)
                        .position(x: Metrics.width - 22.8 - 20, y: Metrics.panelY + 42.2)

                    ForEach(0..<3, id: \.self) { index in
                        let drawn = friendAt(value, rest: rest, index)
                        Text(friends[index].initials)
                            .font(.system(size: 11.5, weight: .bold))
                            .tracking(0.2)
                            .foregroundStyle(friends[index].ink)
                            .fixedSize()
                            .scaleEffect(0.72 + 0.28 * drawn)
                            .opacity(clamp01(drawn * 3 - 1))
                            .position(x: Metrics.avatarX[index], y: Metrics.avatarY)
                    }
                }
                .lineLimit(1)
                .frame(width: Metrics.width, height: Metrics.height)
                .allowsHitTesting(false)
                .accessibilityElement(children: .ignore)
                .accessibilityLabel("Score")
                .accessibilityValue(
                    Text(verbatim: "\(Int((target * Double(goal)).rounded())) of \(goal), \(ordinal(rank)) place")
                )

                Button {
                    onAllFriends?()
                } label: {
                    Text(buttonLabel)
                        .font(.system(size: 16, weight: .medium))
                        .foregroundStyle(.white)
                        .frame(width: 127.7, height: 43.5)
                        .background(Capsule().fill(Color(hex: 0x242426)))
                }
                .buttonStyle(PressStyle())
                .position(x: 190 + 127.7 / 2, y: Metrics.panelY + 80.7 + 43.5 / 2)
            }
            .frame(width: Metrics.width, height: Metrics.height)
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

    /// One column of the rolling number: the digit it is on and the next one sliding in under it.
    private func column(pos: Double, shown: Double) -> some View {
        let digit = Int(pos.rounded(.down)) % 10
        let frac = pos - pos.rounded(.down)
        return ZStack {
            digitText(digit).offset(y: -frac * Metrics.digitH)
            if frac > 0.001 {
                digitText((digit + 1) % 10).offset(y: (1 - frac) * Metrics.digitH)
            }
        }
        .frame(width: Metrics.colW, height: Metrics.digitH)
        .frame(width: Metrics.colW * shown, height: Metrics.digitH, alignment: .leading)
        .clipped()
        .opacity(shown)
    }

    private func digitText(_ digit: Int) -> some View {
        Text(verbatim: "\(digit)")
            .font(.system(size: 37, weight: .bold))
            .tracking(-0.5)
            .monospacedDigit()
            .foregroundStyle(.white)
            .fixedSize()
            .frame(width: Metrics.colW, height: Metrics.digitH)
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

    private func draw(_ context: GraphicsContext, value: Double, time: Double, sinceRank: Double, speed: Double) {
        let x = Metrics.bleed
        let y = Metrics.bleed
        let ambient = reduceMotion ? 0.0 : 1.0
        let panelY = y + Metrics.panelY

        // The body of the device the panel sits on, fading out downwards.
        context.fill(
            Path(
                roundedRect: CGRect(x: x - 9, y: panelY - 9, width: Metrics.width + 18, height: Metrics.panelH + 120),
                cornerRadius: Metrics.panelR + 9, style: .circular
            ),
            with: .linearGradient(
                Gradient(stops: [
                    .init(color: Color.rgba(246, 246, 246, 1), location: 0),
                    .init(color: Color.rgba(246, 246, 246, 1), location: 0.72),
                    .init(color: Color.rgba(246, 246, 246, 0), location: 1),
                ]),
                startPoint: CGPoint(x: 0, y: panelY), endPoint: CGPoint(x: 0, y: y + Metrics.height + 40)
            )
        )
        var shadow = context
        shadow.addFilter(.blur(radius: 14))
        shadow.fill(
            Path(
                roundedRect: CGRect(x: x + 10, y: panelY + 16, width: Metrics.width - 20, height: Metrics.panelH - 6),
                cornerRadius: Metrics.panelR, style: .circular
            ),
            with: .color(.black.opacity(0.3))
        )
        context.fill(capsule(CGRect(x: x + Metrics.pillX, y: y, width: Metrics.pillW, height: Metrics.pillH)), with: .color(.black))
        context.fill(
            Path(
                roundedRect: CGRect(x: x, y: panelY, width: Metrics.width, height: Metrics.panelH),
                cornerRadius: Metrics.panelR, style: .circular
            ),
            with: .color(.black)
        )

        // The line: track, glowing head, fill, and a highlight that runs along the filled part.
        let barY = y + Metrics.barY
        let barH = Metrics.barH
        let fillW = max(barH, Metrics.barW * clamp01(value))
        let fillOpacity = clamp01(value * 60)
        let fill = capsule(CGRect(x: x + Metrics.barX, y: barY - barH / 2, width: fillW, height: barH))
        context.fill(
            capsule(CGRect(x: x + Metrics.barX, y: barY - barH / 2, width: Metrics.barW, height: barH)),
            with: .color(Color(hex: 0x29292b))
        )
        let headOpacity = fillOpacity * (0.35 + ambient * (0.12 * sin(time * 3) + 0.5 * clamp01(abs(speed) * 2.5)))
        if headOpacity > 0.005 {
            var head = context
            head.addFilter(.blur(radius: 5))
            head.fill(circle(CGPoint(x: x + Metrics.barX + fillW - barH / 2, y: barY), 5), with: .color(orange.opacity(headOpacity)))
        }
        context.fill(fill, with: .color(orange.opacity(fillOpacity)))
        let shimmer = time.truncatingRemainder(dividingBy: 3.2) / 1.2
        if ambient > 0, shimmer < 1 {
            var light = context
            light.clip(to: fill)
            light.addFilter(.blur(radius: 3))
            light.fill(
                capsule(CGRect(x: x + Metrics.barX - 30 + (fillW + 30) * shimmer, y: barY - 3, width: 22, height: 6)),
                with: .color(Color(hex: 0xfff4d6, opacity: 0.85 * sin(.pi * shimmer)))
            )
        }

        for index in 0..<3 {
            let centre = CGPoint(x: x + Metrics.avatarX[index], y: y + Metrics.avatarY)
            let drawn = friendAt(value, rest: rest, index)
            context.stroke(circle(centre, Metrics.ringR), with: .color(Color(hex: 0x2a2a2c)), lineWidth: Metrics.ringW)
            if drawn > 0.001 {
                var arc = Path()
                arc.addArc(
                    center: centre, radius: Metrics.ringR, startAngle: .degrees(-90),
                    endAngle: .degrees(-90 + 360 * friends[index].share * drawn), clockwise: false
                )
                context.stroke(arc, with: .color(orange), style: StrokeStyle(lineWidth: Metrics.ringW, lineCap: .round))
            }
            context.fill(
                circle(centre, Metrics.avatarR * (0.72 + 0.28 * drawn)),
                with: .color(friends[index].color.opacity(clamp01(drawn * 3)))
            )
        }

        drawTrophy(context, at: CGPoint(x: x + Metrics.trophyX, y: y + Metrics.trophyY), size: 1.16, since: sinceRank, ambient: ambient)
        drawTrophy(
            context, at: CGPoint(x: x + Metrics.pillX + 173, y: y + Metrics.pillH / 2), size: 0.96, since: sinceRank, ambient: ambient
        )
    }

    /// Climbing a place makes the trophy jump and tip.
    private func drawTrophy(_ context: GraphicsContext, at point: CGPoint, size: Double, since: Double, ambient: Double) {
        let u = clamp01(since / 0.55)
        var trophy = context
        trophy.translateBy(x: point.x, y: point.y - ambient * 3 * sin(.pi * u) * (1 - u))
        trophy.rotate(by: .radians(ambient * 0.35 * sin(u * .pi * 3) * (1 - u)))
        let scale = size * (ambient > 0 ? pop(since) : 1)
        trophy.scaleBy(x: scale, y: scale)
        let flashAt = clamp01(since / 0.7)
        let flash = ambient * 0.8 * (1 - flashAt) * clamp01(flashAt * 8)
        if flash > 0.005 {
            var glow = trophy
            glow.addFilter(.blur(radius: 6))
            glow.fill(circle(.zero, 9), with: .color(orange.opacity(flash)))
        }
        trophy.fill(trophyCup, with: .color(orange))
        trophy.stroke(trophyHandles, with: .color(orange), style: StrokeStyle(lineWidth: 1.15, lineCap: .round))
        trophy.fill(trophyFoot, with: .color(orange))
    }

    private struct PressStyle: ButtonStyle {
        func makeBody(configuration: Configuration) -> some View {
            configuration.label
                .scaleEffect(configuration.isPressed ? 0.97 : 1)
                .animation(.timingCurve(0.23, 1, 0.32, 1, duration: 0.13), value: configuration.isPressed)
                .sensoryFeedback(.impact(weight: .light), trigger: configuration.isPressed) { _, pressed in pressed }
        }
    }
}

private let orange = Color(hex: 0xf2a23e)

private enum Metrics {
    static let width = 340.0
    // The compact pill, then the expanded panel under it.
    static let pillW = 200.5
    static let pillH = 36.0
    static let pillX = (width - pillW) / 2
    static let panelY = 59.3
    static let panelH = 146.3
    static let panelR = 43.0
    static let height = panelY + panelH
    /// The canvas overflows the component by this much on every side to fit the shadow and the device frame.
    static let bleed = 44.0

    static let barX = 21.5
    static let barW = 296.2
    static let barY = panelY + 64
    static let barH = 3.4
    static let avatarX = [43.0, 98.5, 153.5]
    static let avatarY = panelY + 102.2
    static let avatarR = 17.0
    static let ringR = 20.3
    static let ringW = 3.2
    static let trophyX = 278.6
    static let trophyY = panelY + 41.6

    // The rolling number: four columns, each a window one digit tall.
    static let colW = 21.9
    static let digitH = 44.0
    // A column never rolls faster than this (digits per second), so fast counting reads as a blur of rolling
    // digits instead of flicker; it eases into its digit over the last turn.
    static let rollMax = 22.0
    static let rollMin = 2.5

    // autoPlay loop (seconds).
    static let start = 0.5
    static let count = 2.8
    static let hold = 4.2
    static let reset = 0.7
    static let loop = start + count + hold + reset
    /// A moment in the loop where everything is at rest at the reference value: used when nothing is playing.
    static let restTime = start + count + 1.8
}

private func clamp01(_ t: Double) -> Double { min(1, max(0, t)) }

private func easeInOutCubic(_ t: Double) -> Double {
    t < 0.5 ? 4 * t * t * t : 1 - pow(-2 * t + 2, 3) / 2
}

private func easeOutCubic(_ t: Double) -> Double { 1 - pow(1 - clamp01(t), 3) }

private func tenTo(_ k: Int) -> Int { [1, 10, 100, 1000][k] }

/// Share of the goal reached at `t` seconds into the autoPlay loop, where `rest` is the reference share.
private func loopValue(_ t: Double, rest: Double) -> Double {
    if t < Metrics.start { return 0 }
    if t < Metrics.start + Metrics.count { return rest * easeOutCubic((t - Metrics.start) / Metrics.count) }
    if t < Metrics.start + Metrics.count + Metrics.hold { return rest }
    return rest * (1 - easeInOutCubic((t - Metrics.start - Metrics.count - Metrics.hold) / Metrics.reset))
}

/// How far friend `index`'s ring is drawn: they sweep in one after another early in the count.
private func friendAt(_ share: Double, rest: Double, _ index: Int) -> Double {
    easeOutCubic(clamp01(share / max(rest, 0.001) * 4 - 0.5 * Double(index)))
}

private func rankOf(_ points: Int, _ rivals: [Int]) -> Int {
    1 + rivals.filter { $0 > points }.count
}

private func ordinal(_ rank: Int) -> String {
    let tail = rank % 100
    if tail >= 11, tail <= 13 { return "\(rank)th" }
    let suffix = rank % 10 < 4 ? ["th", "st", "nd", "rd"][rank % 10] : "th"
    return "\(rank)\(suffix)"
}

private func spaced(_ n: Int) -> String {
    var digits = String(n)
    var out = ""
    while digits.count > 3 {
        out = " " + digits.suffix(3) + out
        digits = String(digits.dropLast(3))
    }
    return digits + out
}

/// Scale of the trophy after the rank changes: a quick overshoot.
private func pop(_ since: Double) -> Double {
    let u = clamp01(since / 0.55)
    return 1 + 0.45 * sin(.pi * u) * (1 - u)
}

private func circle(_ centre: CGPoint, _ r: Double) -> Path {
    Path(ellipseIn: CGRect(x: centre.x - r, y: centre.y - r, width: r * 2, height: r * 2))
}

// Trophy, drawn around its centre in a 12.4 × 12.6 box.
private let trophyCup: Path = {
    var p = Path()
    p.move(to: CGPoint(x: -3.7, y: -6.3))
    p.addLine(to: CGPoint(x: 3.7, y: -6.3))
    p.addLine(to: CGPoint(x: 3.7, y: -2.4))
    p.addArc(center: CGPoint(x: 0, y: -2.4), radius: 3.7, startAngle: .degrees(0), endAngle: .degrees(180), clockwise: false)
    p.closeSubpath()
    return p
}()

private let trophyHandles: Path = {
    var p = Path()
    p.move(to: CGPoint(x: -3.9, y: -5))
    p.addLine(to: CGPoint(x: -5.6, y: -5))
    p.addQuadCurve(to: CGPoint(x: -3.5, y: -1.7), control: CGPoint(x: -5.9, y: -2.2))
    p.move(to: CGPoint(x: 3.9, y: -5))
    p.addLine(to: CGPoint(x: 5.6, y: -5))
    p.addQuadCurve(to: CGPoint(x: 3.5, y: -1.7), control: CGPoint(x: 5.9, y: -2.2))
    return p
}()

private let trophyFoot: Path = {
    var p = Path()
    p.move(to: CGPoint(x: -0.8, y: 1))
    p.addLine(to: CGPoint(x: 0.8, y: 1))
    p.addLine(to: CGPoint(x: 0.8, y: 4.3))
    p.addLine(to: CGPoint(x: 3, y: 4.3))
    p.addLine(to: CGPoint(x: 3, y: 6.2))
    p.addLine(to: CGPoint(x: -3, y: 6.2))
    p.addLine(to: CGPoint(x: -3, y: 4.3))
    p.addLine(to: CGPoint(x: -0.8, y: 4.3))
    p.closeSubpath()
    return p
}()

#Preview {
    ZStack {
        Color(hex: 0xe8e8e8).ignoresSafeArea()
        GoalScoreBar(autoPlay: true)
    }
}

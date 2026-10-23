import SwiftUI

struct ProjectCollaborator {
    var initials: String
    /// Avatar fill and the colour of its initials.
    var color: Color
    var ink: Color

    static let defaults = [
        ProjectCollaborator(initials: "AM", color: Color(hex: 0xe7a273), ink: Color(hex: 0x5a2e12)),
        ProjectCollaborator(initials: "RK", color: Color(hex: 0xd9d3c5), ink: Color(hex: 0x4b4636)),
        ProjectCollaborator(initials: "JS", color: Color(hex: 0xc6d2da), ink: Color(hex: 0x2f3f4a)),
    ]
}

/// Dark project card: a spinner glyph, title and tag, a large percentage and a lime-to-mint pill
/// bar with the due date sitting on its track, then the collaborators and a "More details" pill.
/// The bar glows and breathes, a band of light sweeps along it, the spinner chases while the value
/// moves and the avatars pop back in each time the loop restarts.
struct ProjectProgressCard: View {
    /// Controlled progress, 0…1. Leave `nil` to show the reference's 65% (or the autoPlay loop).
    var progress: Double? = nil
    /// Fills from 0 to 65% by itself, holds, resets and starts over.
    var autoPlay = false
    var title = "Project Progress"
    var tag = "Onboarding prototype"
    var due = "Due July 28"
    var collaborators = ProjectCollaborator.defaults
    var detailsLabel = "More details"
    var onDetails: () -> Void = {}

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    /// Origin of the ambient clock (sweep, breathing, spinner).
    @State private var epoch = Date.now
    /// When the current autoPlay / tapped run started; `nil` shows the resting frame.
    @State private var runStart: Date? = nil
    @State private var held = Tween(Metrics.rest)
    /// When a controlled value last changed.
    @State private var movedAt = Date.distantPast
    /// Keeps the timeline running for a controlled change under reduced motion.
    @State private var settling = false

    private var controlled: Bool { progress != nil }
    private var target: Double { min(1, max(0, progress ?? Metrics.rest)) }

    var body: some View {
        TimelineView(.animation(paused: reduceMotion && !settling)) { timeline in
            let date = timeline.date
            let time = reduceMotion ? 0 : date.timeIntervalSince(epoch)
            let clock = clock(at: date)
            let value = controlled ? held.value(at: date) : loopValue(clock)
            let percent = Int((value * 100).rounded())

            ZStack {
                Canvas { context, size in
                    draw(context, value: value, time: time, activity: activity(at: date, clock: clock))
                }
                .padding(-Metrics.bleed)
                .allowsHitTesting(false)

                content(value: value, percent: percent, clock: clock)
                    .accessibilityElement(children: .ignore)
                    .accessibilityLabel("\(title), \(tag), \(due)")
                    .accessibilityValue("\(percent)%")

                Button(action: onDetails) {
                    HStack(spacing: 3) {
                        Text(detailsLabel)
                            .font(.system(size: 9.5, weight: .medium))
                            .lineLimit(1)
                        Path { path in
                            path.move(to: CGPoint(x: 1, y: 4.5))
                            path.addLine(to: CGPoint(x: 8, y: 4.5))
                            path.move(to: CGPoint(x: 5, y: 1.5))
                            path.addLine(to: CGPoint(x: 8, y: 4.5))
                            path.addLine(to: CGPoint(x: 5, y: 7.5))
                        }
                        .stroke(style: StrokeStyle(lineWidth: 1.1, lineCap: .round, lineJoin: .round))
                        .frame(width: 9, height: 9)
                    }
                    .foregroundStyle(.white)
                }
                .buttonStyle(DetailsStyle())
                .position(x: 269.75, y: 196.3)
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

    /// 0…1: how busy the spinner is (it only chases while the bar moves).
    private func activity(at date: Date, clock: Double) -> Double {
        if reduceMotion { return 0 }
        if !controlled { return loopActivity(clock) }
        let since = date.timeIntervalSince(movedAt)
        return clamp01(since / 0.2) * clamp01((0.9 - since) / 0.4)
    }

    private func content(value: Double, percent: Int, clock: Double) -> some View {
        ZStack {
            HStack(spacing: 7.5) {
                Text(title)
                    .font(.system(size: 14.8, weight: .medium))
                    .foregroundStyle(Color(hex: 0xf4f4f4))
                Text(tag)
                    .font(.system(size: 9.2, weight: .medium))
                    .foregroundStyle(Color(hex: 0xc9c9c9))
                    .padding(.horizontal, 9)
                    .frame(height: 22)
                    .background(Color.white.opacity(0.02), in: Capsule())
                    .overlay(Capsule().strokeBorder(Color.white.opacity(0.09), lineWidth: 1))
            }
            .lineLimit(1)
            .leading(25.5, centerY: 50.5)

            HStack(alignment: .firstTextBaseline, spacing: 0) {
                Text("\(percent)").foregroundStyle(Color(hex: 0xf2f2f2))
                Text("%").foregroundStyle(Color(hex: 0x8b8b8b))
            }
            .font(.system(size: 31, weight: .medium))
            .kerning(-1)
            .monospacedDigit()
            .leading(25.5, centerY: 82.5)

            Text(due)
                .font(.system(size: 11.5, weight: .semibold))
                .foregroundStyle(Color(hex: 0xd6dfe1))
                .lineLimit(1)
                .position(x: 269, y: Metrics.barY + Metrics.barHeight / 2)
            // The due date turns dark where the fill has reached it.
            Text(due)
                .font(.system(size: 11.5, weight: .semibold))
                .foregroundStyle(Color(hex: 0x1f5a4c))
                .lineLimit(1)
                .position(x: 269, y: Metrics.barY + Metrics.barHeight / 2)
                .mask(alignment: .topLeading) {
                    Rectangle()
                        .frame(width: value <= 0 ? 0 : fillWidth(value), height: Metrics.barHeight)
                        .offset(x: Metrics.barX, y: Metrics.barY)
                }

            (Text("Collaborators ").foregroundStyle(Color(hex: 0xb6b6b6))
                + Text("\(collaborators.count)").foregroundStyle(Color(hex: 0x7c7c7c)))
                .font(.system(size: 11, weight: .medium))
                .lineLimit(1)
                .leading(30, centerY: 156)

            ForEach(Array(collaborators.enumerated()), id: \.offset) { index, person in
                let scale = controlled || reduceMotion ? 1 : avatarScale(clock, index: index)
                RoundedRectangle(cornerRadius: 11, style: .continuous)
                    .fill(Color(hex: 0x474747))
                    .frame(width: Metrics.avatar, height: Metrics.avatar)
                    .overlay {
                        RoundedRectangle(cornerRadius: 9, style: .continuous)
                            .fill(person.color)
                            .padding(2)
                        Text(person.initials)
                            .font(.system(size: 11, weight: .bold))
                            .kerning(0.2)
                            .foregroundStyle(person.ink)
                    }
                    .scaleEffect(scale)
                    .opacity(clamp01(scale * 2))
                    .position(x: 30 + Metrics.avatar / 2 + Metrics.avatarPitch * Double(index), y: 189.5)
            }
        }
        .frame(width: Metrics.width, height: Metrics.height)
    }

    private func draw(_ context: GraphicsContext, value: Double, time: Double, activity: Double) {
        let bleed = Metrics.bleed
        let card = CGRect(x: bleed, y: bleed, width: Metrics.width, height: Metrics.height)
        let ambient = reduceMotion ? 0.0 : 1.0

        var shadow = context
        shadow.addFilter(.blur(radius: 18))
        shadow.fill(
            Path(
                roundedRect: CGRect(x: card.minX + 6, y: card.minY + 16, width: card.width - 12, height: card.height - 8),
                cornerRadius: Metrics.radius, style: .circular
            ),
            with: .color(.rgba(60, 70, 90, 0.3))
        )
        context.fill(
            Path(roundedRect: card, cornerRadius: Metrics.radius, style: .circular),
            with: .linearGradient(
                Gradient(stops: [
                    .init(color: Color(hex: 0x525252), location: 0),
                    .init(color: Color(hex: 0x3b3b3b), location: 0.06),
                    .init(color: Color(hex: 0x353535), location: 0.16),
                    .init(color: Color(hex: 0x313131), location: 0.5),
                    .init(color: Color(hex: 0x1f1f1f), location: 0.93),
                    .init(color: Color(hex: 0x242424), location: 0.985),
                    .init(color: Color(hex: 0x3d3b3c), location: 1),
                ]),
                startPoint: CGPoint(x: card.minX, y: card.minY),
                endPoint: CGPoint(x: card.minX, y: card.maxY)
            )
        )

        // Spinner: a bright head travels clockwise and leaves a fading trail while the bar moves.
        let spokes = Metrics.spokes
        for i in 0..<spokes {
            let a = Double(i) / Double(spokes) * tau - .pi / 2
            let phase = (time * 1.25 - Double(i) / Double(spokes)).truncatingRemainder(dividingBy: 1)
            var spoke = Path()
            spoke.move(to: CGPoint(x: card.minX + Metrics.spinnerX + 3 * cos(a), y: card.minY + Metrics.spinnerY + 3 * sin(a)))
            spoke.addLine(to: CGPoint(x: card.minX + Metrics.spinnerX + 8 * cos(a), y: card.minY + Metrics.spinnerY + 8 * sin(a)))
            context.stroke(
                spoke,
                with: .color(Color(hex: 0xf4f4f4, opacity: 1 - activity * 0.78 * (phase < 0 ? phase + 1 : phase))),
                style: StrokeStyle(lineWidth: 1.5, lineCap: .round)
            )
        }

        let bx = card.minX + Metrics.barX
        let by = card.minY + Metrics.barY
        let barHeight = Metrics.barHeight
        let width = fillWidth(value)
        // The pill has a minimum length, so it fades in over the first few percent.
        let fillAlpha = clamp01(value / 0.03)
        let fill = capsule(CGRect(x: bx, y: by, width: width, height: barHeight))
        let shading = GraphicsContext.Shading.linearGradient(
            Gradient(stops: [
                .init(color: Color(hex: 0xc0fe6e), location: 0),
                .init(color: Color(hex: 0x93fda9), location: 0.42),
                .init(color: Color(hex: 0x6bfcdf), location: 0.78),
                .init(color: Color(hex: 0x6bfcdf), location: 1),
            ]),
            startPoint: CGPoint(x: bx, y: by),
            endPoint: CGPoint(x: bx + Metrics.barWidth * 0.72, y: by)
        )

        // Glow first so the translucent track sits on top of it, as in the reference.
        var glow = context
        glow.opacity = fillAlpha * (0.84 + ambient * 0.14 * sin(time * 1.9))
        glow.addFilter(.blur(radius: 11))
        glow.fill(fill, with: shading)

        let track = CGRect(x: bx, y: by, width: Metrics.barWidth, height: barHeight)
        context.fill(capsule(track), with: .color(.rgba(118, 125, 128, 0.86)))
        context.stroke(capsule(track.insetBy(dx: 0.5, dy: 0.5)), with: .color(.white.opacity(0.1)), lineWidth: 1)

        var solid = context
        solid.opacity = fillAlpha
        solid.fill(fill, with: shading)

        // A band of light crosses the fill every few seconds.
        let phase = time.truncatingRemainder(dividingBy: Metrics.sweepPeriod) / Metrics.sweepTravel
        if ambient > 0, phase < 1 {
            let sx = bx - Metrics.sweepWidth + (width + Metrics.sweepWidth) * phase
            var sweep = context
            sweep.opacity = fillAlpha * sin(.pi * phase)
            sweep.fill(
                fill,
                with: .linearGradient(
                    Gradient(colors: [.white.opacity(0), .white.opacity(0.55), .white.opacity(0)]),
                    startPoint: CGPoint(x: sx, y: by),
                    endPoint: CGPoint(x: sx + Metrics.sweepWidth, y: by + 12)
                )
            )
        }
    }
}

private enum Metrics {
    static let width = 340.0
    static let height = 225.0
    static let radius = 32.0
    /// The canvas overflows the card by this much on every side to fit its shadow.
    static let bleed = 40.0

    static let barX = 25.5
    static let barY = 107.0
    static let barWidth = 289.0
    static let barHeight = 29.5

    static let spinnerX = 32.5
    static let spinnerY = 26.5
    static let spokes = 8

    static let avatar = 34.0
    static let avatarPitch = 38.7

    /// Value shown in the reference, and the autoPlay loop built around it (seconds).
    static let rest = 0.65
    static let start = 0.35
    static let fill = 2.2
    static let hold = 3.2
    static let reset = 0.55
    static let loop = start + fill + hold + reset
    /// A moment in the loop where everything is at rest: used when nothing is playing.
    static let restTime = start + fill + 1
    static let sweepPeriod = 2.8
    static let sweepTravel = 1.15
    static let sweepWidth = 70.0
}

private func clamp01(_ t: Double) -> Double { min(1, max(0, t)) }

private func easeInOutCubic(_ t: Double) -> Double {
    t < 0.5 ? 4 * t * t * t : 1 - pow(-2 * t + 2, 3) / 2
}

/// Overshoots a little before settling: avatars pop in like they are on a spring.
private func easeOutBack(_ t: Double) -> Double {
    let c = 2.2
    return 1 + (c + 1) * pow(t - 1, 3) + c * pow(t - 1, 2)
}

/// Progress at `t` seconds into the autoPlay loop.
private func loopValue(_ t: Double) -> Double {
    if t < Metrics.start { return 0 }
    if t < Metrics.start + Metrics.fill { return Metrics.rest * easeInOutCubic((t - Metrics.start) / Metrics.fill) }
    if t < Metrics.loop - Metrics.reset { return Metrics.rest }
    return Metrics.rest * (1 - easeInOutCubic((t - (Metrics.loop - Metrics.reset)) / Metrics.reset))
}

private func loopActivity(_ t: Double) -> Double {
    clamp01((t - Metrics.start) / 0.25) * clamp01((Metrics.start + Metrics.fill + 0.1 - t) / 0.45)
}

/// Scale of avatar `index`: they leave during the reset and pop back in one after another.
private func avatarScale(_ t: Double, index: Int) -> Double {
    if t > Metrics.loop - Metrics.reset { return 1 - clamp01((t - (Metrics.loop - Metrics.reset)) / (Metrics.reset * 0.7)) }
    return easeOutBack(clamp01((t - 0.12 - 0.11 * Double(index)) / 0.5))
}

private func fillWidth(_ p: Double) -> Double {
    Metrics.barHeight + (Metrics.barWidth - Metrics.barHeight) * clamp01(p)
}

private extension View {
    /// Places the view by its leading edge and vertical centre inside the card.
    func leading(_ x: Double, centerY: Double) -> some View {
        fixedSize()
            .frame(width: 300, alignment: .leading)
            .position(x: x + 150, y: centerY)
    }
}

/// Dark glass pill with a cool rim; scales down while pressed.
private struct DetailsStyle: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .frame(width: 89.5, height: 22)
            .background {
                Capsule().fill(
                    LinearGradient(
                        colors: [Color(hex: 0x2c3438), Color(hex: 0x1e1f20), Color(hex: 0x232425)],
                        startPoint: .top, endPoint: .bottom
                    )
                )
                // Glassy rim: cool light from the top left, a white glint at the bottom right.
                Capsule().strokeBorder(
                    LinearGradient(
                        stops: [
                            .init(color: .rgba(120, 150, 170, 0.55), location: 0),
                            .init(color: .rgba(120, 150, 170, 0.18), location: 0.4),
                            .init(color: .white.opacity(0.1), location: 0.7),
                            .init(color: .white.opacity(0.6), location: 1),
                        ],
                        startPoint: .topLeading, endPoint: .bottomTrailing
                    ),
                    lineWidth: 1
                )
            }
            .contentShape(Capsule().inset(by: -10))
            .scaleEffect(configuration.isPressed ? 0.97 : 1)
            .animation(.timingCurve(0.23, 1, 0.32, 1, duration: 0.13), value: configuration.isPressed)
            .sensoryFeedback(.impact(weight: .light), trigger: configuration.isPressed) { _, pressed in pressed }
    }
}

#Preview {
    ZStack {
        Color(hex: 0xf5f7f8).ignoresSafeArea()
        ProjectProgressCard(autoPlay: true)
    }
}

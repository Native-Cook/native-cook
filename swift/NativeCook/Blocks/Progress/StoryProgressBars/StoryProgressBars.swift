import SwiftUI

struct ProgressStory {
    var title: String
    var description: String
    /// Label of the call-to-action button.
    var action: String

    static let defaults = [
        ProgressStory(
            title: "Workspaces",
            description: "Bring every project, doc and thread together in one shared space.",
            action: "Create Workspace →"
        ),
        ProgressStory(
            title: "Integrations",
            description: "Integrate your apps to create, automate, and command in one place.",
            action: "Connect Integrations →"
        ),
        ProgressStory(
            title: "Automations",
            description: "Turn the steps you repeat into flows that run while you sleep.",
            action: "Build Automation →"
        ),
        ProgressStory(
            title: "Insights",
            description: "See what shipped, what stalled and what needs you next.",
            action: "View Insights →"
        ),
    ]
}

/// Dark onboarding card with story-style progress lines across the top, floating app chips over a
/// glowing horizon, a title, a description and a call-to-action, plus a round pause button. Each
/// bar that fills hands the card over to its next story: the copy rises in, the chips fly to a
/// new arrangement and land with a little overshoot, the glow changes colour and a spark runs
/// along the horizon. The pause button really pauses and turns into a play button.
struct StoryProgressBars: View {
    /// Controlled position, 0…1 across all four stories. Leave `nil` to show the reference (or the autoPlay loop).
    var progress: Double? = nil
    /// Plays the stories one after another, like a story viewer, and starts over after the last.
    var autoPlay = false
    /// Exactly four stories; the second one is the reference's.
    var stories = ProgressStory.defaults
    /// Called with the index of the story on screen.
    var onAction: (Int) -> Void = { _ in }
    var onPausedChange: (Bool) -> Void = { _ in }

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    /// Origin of the ambient clock (floating chips, twinkling stars).
    @State private var epoch = Date.now
    /// When the story loop started, shifted forward by every pause; `nil` shows the resting frame.
    @State private var runStart: Date? = nil
    @State private var paused = false
    @State private var pausedAt = Date.now
    @State private var held = Tween(Metrics.rest)
    /// 0 = pause glyph, 1 = play glyph.
    @State private var morph = Tween(0)
    /// Keeps the timeline running for a change under reduced motion.
    @State private var settling = false

    private var controlled: Bool { progress != nil }
    private var target: Double { clamp01(progress ?? Metrics.rest) }
    private var auto: Bool { autoPlay && !controlled && !reduceMotion }

    var body: some View {
        TimelineView(.animation(paused: reduceMotion && !settling)) { timeline in
            let date = timeline.date
            let time = reduceMotion ? 0 : date.timeIntervalSince(epoch)
            let value = clamp01(controlled ? held.value(at: date) : clock(at: date) / Metrics.loop)
            let story = storyAt(value)
            let hand = handOver(value, wraps: auto)
            let previous = (story + Metrics.count - 1) % Metrics.count
            let current = stories.indices.contains(story) ? stories[story] : stories[0]

            ZStack(alignment: .topLeading) {
                ZStack(alignment: .topLeading) {
                    Canvas { context, _ in
                        draw(context, value: value, time: time, story: story, hand: hand)
                    }
                    .padding(-Metrics.bleed)
                    .allowsHitTesting(false)

                    // Copy: each story's text rises in as it takes over and the old one leaves upwards.
                    ZStack(alignment: .topLeading) {
                        ForEach(Array(stories.prefix(Metrics.count).enumerated()), id: \.offset) { index, item in
                            let eased = 1 - pow(1 - hand, 3)
                            let alpha = index == story ? clamp01((hand - 0.45) / 0.55) : index == previous ? 1 - clamp01(hand / 0.4) : 0
                            let shift = index == story ? (1 - eased) * 10 : index == previous ? -eased * 8 : 0
                            VStack(alignment: .leading, spacing: 0) {
                                Text(item.title)
                                    .font(.system(size: 16, weight: .medium))
                                    .kerning(0.1)
                                    .foregroundStyle(Color(hex: 0xf5f5f5))
                                    .lineLimit(1)
                                    .frame(height: 22)
                                Text(item.description)
                                    .font(.system(size: 13.5))
                                    .kerning(0.1)
                                    .lineSpacing(3.9)
                                    .foregroundStyle(.white.opacity(0.56))
                                    .lineLimit(2)
                                    .frame(width: 276, alignment: .leading)
                                    .padding(.top, 6)
                            }
                            .opacity(alpha)
                            .offset(x: 19.5, y: 146.5 + shift)
                        }
                    }
                    .allowsHitTesting(false)
                    .accessibilityElement(children: .ignore)
                    .accessibilityLabel("\(current.title). \(current.description)")
                    .accessibilityValue("Story \(story + 1) of \(Metrics.count)")

                    Button {
                        onAction(story)
                    } label: {
                        ZStack {
                            ForEach(Array(stories.prefix(Metrics.count).enumerated()), id: \.offset) { index, item in
                                Text(item.action)
                                    .opacity(index == story ? clamp01((hand - 0.45) / 0.55) : index == previous ? 1 - clamp01(hand / 0.4) : 0)
                            }
                        }
                        .font(.system(size: 14, weight: .medium))
                        .kerning(0.1)
                        .foregroundStyle(Color(hex: 0xf5f5f5))
                        .lineLimit(1)
                    }
                    .buttonStyle(ActionStyle())
                    .accessibilityLabel(current.action)
                    .offset(x: Metrics.actionX, y: Metrics.actionY)
                }
                .frame(width: Metrics.width, height: Metrics.cardHeight, alignment: .topLeading)
                .contentShape(RoundedRectangle(cornerRadius: Metrics.radius))
                .onTapGesture { skip() }

                Button {
                    togglePaused()
                } label: {
                    Canvas { context, _ in
                        drawPause(context, morph: morph.value(at: date))
                    }
                    .frame(width: Metrics.pause, height: Metrics.pause)
                }
                .buttonStyle(PressStyle())
                .accessibilityLabel(paused ? "Play" : "Pause")
                .offset(x: Metrics.width - Metrics.pause, y: Metrics.pauseY)
            }
            .frame(width: Metrics.width, height: Metrics.height, alignment: .topLeading)
        }
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
            try? await Task.sleep(for: .seconds(0.9))
            settling = false
        }
    }

    private func sync() {
        runStart = auto ? .now : nil
        pausedAt = .now
        if held.duration == 0 { held = Tween(target) }
    }

    /// Position in the story loop, in seconds. The pause button holds it; the scene keeps floating.
    private func clock(at date: Date) -> Double {
        guard let runStart else { return Metrics.rest * Metrics.loop }
        let elapsed = (paused ? pausedAt : date).timeIntervalSince(runStart)
        return max(0, elapsed).truncatingRemainder(dividingBy: Metrics.loop)
    }

    /// Tapping the card skips to the next story, like a story viewer.
    private func skip() {
        guard auto, let start = runStart else { return }
        let now = paused ? pausedAt : Date.now
        let elapsed = max(0, now.timeIntervalSince(start))
        let next = ((elapsed / Metrics.story + 1e-6).rounded(.down) + 1) * Metrics.story
        runStart = start.addingTimeInterval(elapsed - next)
    }

    private func togglePaused() {
        let now = Date.now
        if paused {
            // Shift the origin by the time spent paused, so the story resumes where it stopped.
            runStart = runStart?.addingTimeInterval(now.timeIntervalSince(pausedAt))
        } else {
            pausedAt = now
        }
        paused.toggle()
        morph.retarget(paused ? 1 : 0, at: now, duration: 0.24, curve: Ease.out)
        settling = true
        onPausedChange(paused)
    }

    private func draw(_ base: GraphicsContext, value: Double, time: Double, story: Int, hand: Double) {
        let ambient = reduceMotion ? 0.0 : 1.0
        let width = Metrics.width
        let height = Metrics.cardHeight
        let count = Metrics.count
        let previous = (story + count - 1) % count
        var context = base
        context.translateBy(x: Metrics.bleed, y: Metrics.bleed)

        let card = CGRect(x: 0, y: 0, width: width, height: height)
        var shadow = context
        shadow.addFilter(.blur(radius: 12))
        shadow.fill(
            Path(roundedRect: CGRect(x: 6, y: 12, width: width - 12, height: height - 8), cornerRadius: Metrics.radius, style: .circular),
            with: .color(.black.opacity(0.55))
        )
        context.fill(
            Path(roundedRect: card, cornerRadius: Metrics.radius, style: .circular),
            with: .linearGradient(
                Gradient(colors: [Color(hex: 0x1b1b1b), Color(hex: 0x141414)]),
                startPoint: .zero, endPoint: CGPoint(x: 0, y: height)
            )
        )
        context.stroke(
            Path(roundedRect: card.insetBy(dx: 0.5, dy: 0.5), cornerRadius: Metrics.radius - 0.5, style: .circular),
            with: .linearGradient(
                Gradient(colors: [.white.opacity(0.2), .white.opacity(0.07)]),
                startPoint: .zero, endPoint: CGPoint(x: 0, y: height)
            ),
            lineWidth: 1
        )

        // Story bars.
        let segY = Metrics.segY
        let segHeight = Metrics.segHeight
        for index in 0..<count {
            let x = Metrics.segX + Double(index) * Metrics.segPitch
            let filled = Metrics.segWidth * clamp01(value * Double(count) - Double(index))
            let track = capsule(CGRect(x: x, y: segY, width: Metrics.segWidth, height: segHeight))
            context.fill(track, with: .color(.white.opacity(0.2)))
            // When the set starts again the old bars fade out.
            if auto, story == 0 { context.fill(track, with: .color(.white.opacity(1 - hand))) }
            // A finished bar flashes as its story hands over.
            if story == index + 1, hand < 1 {
                var flash = context
                flash.opacity = 0.9 * (1 - hand)
                flash.addFilter(.blur(radius: 3))
                flash.fill(
                    Path(roundedRect: CGRect(x: x, y: segY - 1, width: Metrics.segWidth, height: segHeight + 2), cornerRadius: 2, style: .circular),
                    with: .color(.white)
                )
            }
            context.fill(capsule(CGRect(x: x, y: segY, width: filled, height: segHeight)), with: .color(.white))
            // A glint rides the head of the bar that is filling; it fades out as the bar completes.
            let glint = story == index ? clamp01(filled / 3) * clamp01((Metrics.segWidth - filled) / 2) : 0
            if glint > 0 {
                var spot = context
                spot.opacity = glint
                spot.addFilter(.blur(radius: 2.2))
                spot.fill(Path(ellipseIn: CGRect(x: x + filled - 2.2, y: segY + segHeight / 2 - 2.2, width: 4.4, height: 4.4)), with: .color(.white))
            }
        }

        // Scene: faint beams and stars over a glowing horizon.
        var beams = Path()
        for beam in Metrics.beams {
            beams.move(to: CGPoint(x: beam.x, y: beam.top))
            beams.addLine(to: CGPoint(x: beam.x, y: beam.bottom))
        }
        context.stroke(
            beams,
            with: .linearGradient(
                Gradient(colors: [.white.opacity(0), .white.opacity(0.13), .white.opacity(0.04)]),
                startPoint: CGPoint(x: 0, y: 70), endPoint: CGPoint(x: 0, y: 130)
            ),
            lineWidth: 0.6
        )
        for (index, star) in Metrics.stars.enumerated() {
            let twinkle = index % 2 == 0 ? 0.62 + ambient * 0.38 * sin(time * 2.3) : 0.62 + ambient * 0.38 * sin(time * 1.7 + 2.1)
            context.fill(
                Path(ellipseIn: CGRect(x: star.x - star.r, y: star.y - star.r, width: star.r * 2, height: star.r * 2)),
                with: .color(.white.opacity(0.9 * twinkle))
            )
        }

        // The glow under the horizon takes each story's colour and breathes.
        let eased = 1 - pow(1 - hand, 3)
        let from = Metrics.glows[previous]
        let to = Metrics.glows[story]
        let glow: (Double) -> Color = { alpha in
            .rgba(
                (from.0 + (to.0 - from.0) * eased).rounded(), (from.1 + (to.1 - from.1) * eased).rounded(),
                (from.2 + (to.2 - from.2) * eased).rounded(), alpha
            )
        }
        let horizonY = Metrics.horizonY
        let x0 = Metrics.horizonX0
        let x1 = Metrics.horizonX1
        let middle = (x0 + x1) / 2
        var haze = context
        haze.clip(to: Path(CGRect(x: 0, y: horizonY - 40, width: width, height: 40)))
        haze.opacity = 0.86 + ambient * 0.14 * sin(time * 1.4)
        haze.fillEllipse(
            center: CGPoint(x: middle, y: horizonY), radii: CGSize(width: 112, height: 26),
            gradient: Gradient(colors: [glow(0.34), glow(0.12), glow(0)])
        )
        haze.fillEllipse(
            center: CGPoint(x: middle, y: horizonY), radii: CGSize(width: 46, height: 11),
            gradient: Gradient(colors: [.rgba(255, 170, 120, 0.2), .rgba(255, 170, 120, 0)])
        )
        context.fill(
            Path(CGRect(x: x0, y: horizonY - 0.4, width: x1 - x0, height: 0.8)),
            with: .linearGradient(
                Gradient(stops: [
                    .init(color: glow(0), location: 0),
                    .init(color: glow(0.9), location: 0.3),
                    .init(color: .rgba(255, 236, 244, 1), location: 0.5),
                    .init(color: glow(0.9), location: 0.7),
                    .init(color: glow(0), location: 1),
                ]),
                startPoint: CGPoint(x: x0, y: 0), endPoint: CGPoint(x: x1, y: 0)
            )
        )
        // A spark runs along the horizon each time a story takes over.
        if hand < 1 {
            let spark = Path(ellipseIn: CGRect(x: x0 + (x1 - x0) * eased - 2.4, y: horizonY - 2.4, width: 4.8, height: 4.8))
            var halo = context
            halo.opacity = sin(.pi * hand)
            halo.drawLayer { layer in
                layer.addFilter(.blur(radius: 3))
                layer.fill(spark, with: .color(.white))
            }
            context.fill(spark, with: .color(.white.opacity(sin(.pi * hand))))
        }

        // The chips fly to a new arrangement for every story, and bob gently where they are.
        let back = easeOutBack(hand)
        for (index, chip) in Metrics.chips.enumerated() {
            let a = chip.at[previous]
            let b = chip.at[story]
            let i = Double(index)
            var layer = context
            layer.translateBy(
                x: a.x + (b.x - a.x) * back + ambient * 1.2 * sin(time * 0.6 + i),
                y: a.y + (b.y - a.y) * back + ambient * 2.4 * sin(time * 0.9 + i * 1.7)
            )
            layer.rotate(by: .degrees(a.tilt + (b.tilt - a.tilt) * back + ambient * 2 * sin(time * 0.7 + i * 2.3)))
            let scale = a.scale + (b.scale - a.scale) * back
            layer.scaleBy(x: scale, y: scale)
            drawChip(layer, index: index, size: chip.size)
        }
    }

    /// One app chip around its own centre, with a generic glyph: a crescent, three bars, a note or four dots.
    private func drawChip(_ context: GraphicsContext, index: Int, size: Double) {
        let radius = size * 0.27
        let half = size / 2
        let faces: [(UInt32, UInt32)] = [(0x3b3b3b, 0x1f1f1f), (0x9a3cf0, 0x5a17a6), (0xf7f4ed, 0xe2ded3), (0xffffff, 0xe8e8e8)]
        var shadow = context
        shadow.addFilter(.blur(radius: 4))
        shadow.fill(
            Path(roundedRect: CGRect(x: -half, y: -half + 3, width: size, height: size), cornerRadius: radius, style: .circular),
            with: .color(.black.opacity(0.5))
        )
        context.fill(
            Path(roundedRect: CGRect(x: -half, y: -half, width: size, height: size), cornerRadius: radius, style: .circular),
            with: .linearGradient(
                Gradient(colors: [Color(hex: faces[index].0), Color(hex: faces[index].1)]),
                startPoint: CGPoint(x: 0, y: -half), endPoint: CGPoint(x: 0, y: half)
            )
        )
        context.stroke(
            Path(roundedRect: CGRect(x: -half + 0.4, y: -half + 0.4, width: size - 0.8, height: size - 0.8), cornerRadius: radius - 0.4, style: .circular),
            with: .color(.white.opacity(0.2)), lineWidth: 0.8
        )
        func dot(_ x: Double, _ y: Double, _ r: Double) -> Path {
            Path(ellipseIn: CGRect(x: x - r, y: y - r, width: r * 2, height: r * 2))
        }
        func bar(_ x: Double, _ y: Double, _ w: Double, _ h: Double, _ r: Double) -> Path {
            Path(roundedRect: CGRect(x: x, y: y, width: w, height: h), cornerRadius: r, style: .circular)
        }
        switch index {
        case 0:
            context.fill(
                dot(0, 0, 7.4),
                with: .linearGradient(
                    Gradient(colors: [.white, Color(hex: 0xb9b9b9)]),
                    startPoint: CGPoint(x: 0, y: -7.4), endPoint: CGPoint(x: 0, y: 7.4)
                )
            )
            context.fill(dot(3.6, -3.2, 6), with: .color(Color(hex: 0x2b2b2b)))
        case 1:
            context.fill(bar(-4.6, -4, 9.2, 1.7, 0.85), with: .color(.white))
            context.fill(bar(-4.6, -0.85, 9.2, 1.7, 0.85), with: .color(.white))
            context.fill(bar(-4.6, 2.3, 5.8, 1.7, 0.85), with: .color(.white))
        case 2:
            context.fill(bar(-4, -5.2, 8, 10.4, 1.7), with: .color(Color(hex: 0xf5b301)))
            context.fill(bar(-2.1, -1.7, 4.2, 1.1, 0), with: .color(.white.opacity(0.92)))
            context.fill(bar(-2.1, 0.8, 4.2, 1.1, 0), with: .color(.white.opacity(0.92)))
        default:
            context.fill(dot(-3.8, -3.8, 2.7), with: .color(Color(hex: 0x4f9dff)))
            context.fill(dot(3.8, -3.8, 2.7), with: .color(Color(hex: 0x34c98a)))
            context.fill(dot(-3.8, 3.8, 2.7), with: .color(Color(hex: 0xff5d7a)))
            context.fill(dot(3.8, 3.8, 2.7), with: .color(Color(hex: 0xffc043)))
        }
    }

    /// Round pause button: the two bars turn and shrink away while the play triangle grows in.
    private func drawPause(_ context: GraphicsContext, morph: Double) {
        let size = Metrics.pause
        let c = size / 2
        let disc = Path(ellipseIn: CGRect(x: 0, y: 0, width: size, height: size))
        context.fill(
            disc,
            with: .linearGradient(
                Gradient(colors: [Color(hex: 0x2d2d2d), Color(hex: 0x1f1f1f)]),
                startPoint: .zero, endPoint: CGPoint(x: 0, y: size)
            )
        )
        var glow = context
        glow.clip(to: disc)
        glow.opacity = 0.75 + 0.25 * morph
        glow.fillEllipse(
            center: CGPoint(x: c, y: size + 2), radii: CGSize(width: size * 0.62, height: size * 0.62),
            gradient: Gradient(colors: [.rgba(120, 84, 220, 0.6), .rgba(120, 84, 220, 0)])
        )
        context.stroke(
            Path(ellipseIn: CGRect(x: 0.5, y: 0.5, width: size - 1, height: size - 1)),
            with: .linearGradient(
                Gradient(colors: [.white.opacity(0.22), .white.opacity(0.04)]),
                startPoint: .zero, endPoint: CGPoint(x: 0, y: size)
            ),
            lineWidth: 1
        )

        var bars = context
        bars.translateBy(x: c, y: c - 1.5)
        bars.rotate(by: .degrees(morph * 90))
        bars.scaleBy(x: 1 - 0.5 * morph, y: 1 - 0.5 * morph)
        bars.opacity = 1 - morph
        let shade = GraphicsContext.Shading.linearGradient(
            Gradient(colors: [.white, Color(hex: 0xc9c9c9)]),
            startPoint: CGPoint(x: 0, y: -5.3), endPoint: CGPoint(x: 0, y: 5.3)
        )
        bars.fill(capsule(CGRect(x: -5.2, y: -5.3, width: 3.2, height: 10.6)), with: shade)
        bars.fill(capsule(CGRect(x: 2, y: -5.3, width: 3.2, height: 10.6)), with: shade)

        var play = context
        play.translateBy(x: c, y: c - 1.5)
        play.rotate(by: .degrees((morph - 1) * 90))
        play.scaleBy(x: 0.5 + 0.5 * morph, y: 0.5 + 0.5 * morph)
        play.opacity = morph
        var triangle = Path()
        triangle.move(to: CGPoint(x: -3.4, y: -5.5))
        triangle.addLine(to: CGPoint(x: 5.6, y: 0))
        triangle.addLine(to: CGPoint(x: -3.4, y: 5.5))
        triangle.closeSubpath()
        let ink = Color(hex: 0xf1f1f1)
        play.fill(triangle, with: .color(ink))
        play.stroke(triangle, with: .color(ink), style: StrokeStyle(lineWidth: 1.6, lineJoin: .round))
    }
}

private struct ChipSpot {
    var x: Double
    var y: Double
    var tilt: Double
    var scale: Double
}

private enum Metrics {
    static let width = 310.0
    static let cardHeight = 280.0
    static let height = 330.0
    static let radius = 27.5
    /// The canvas overflows the card by this much on every side to fit its shadow.
    static let bleed = 24.0

    static let count = 4
    static let segX = 21.5
    static let segY = 20.5
    static let segWidth = 63.0
    static let segPitch = 68.0
    static let segHeight = 1.5

    static let horizonY = 131.5
    static let horizonX0 = 52.0
    static let horizonX1 = 260.0

    static let actionX = 15.8
    static let actionY = 228.0
    static let actionWidth = 278.4
    static let actionHeight = 36.5
    static let pause = 39.0
    static let pauseY = 290.5

    /// Seconds each story stays up, and how long the hand-over to the next one takes.
    static let story = 3.4
    static let transition = 0.7
    static let loop = story * Double(count)
    /// Position shown in the reference: the second story with its bar full.
    static let rest = 0.5

    /// The four floating app chips: size, and where each sits in every story.
    static let chips: [(size: Double, at: [ChipSpot])] = [
        (29, [
            ChipSpot(x: 88, y: 92, tilt: -8, scale: 0.85), ChipSpot(x: 51.8, y: 80.8, tilt: -14, scale: 1),
            ChipSpot(x: 70, y: 64, tilt: 10, scale: 0.9), ChipSpot(x: 60, y: 98, tilt: -4, scale: 0.8),
        ]),
        (19.5, [
            ChipSpot(x: 128, y: 104, tilt: 6, scale: 1), ChipSpot(x: 104.8, y: 96.8, tilt: -10, scale: 1),
            ChipSpot(x: 140, y: 84, tilt: 0, scale: 1.35), ChipSpot(x: 118, y: 62, tilt: 12, scale: 1.05),
        ]),
        (18, [
            ChipSpot(x: 170, y: 78, tilt: -6, scale: 1), ChipSpot(x: 177.5, y: 67, tilt: 8, scale: 1),
            ChipSpot(x: 206, y: 98, tilt: -10, scale: 1.05), ChipSpot(x: 160, y: 92, tilt: 4, scale: 1.4),
        ]),
        (28, [
            ChipSpot(x: 214, y: 96, tilt: 10, scale: 0.85), ChipSpot(x: 241.5, y: 71.3, tilt: 6, scale: 1),
            ChipSpot(x: 252, y: 60, tilt: -8, scale: 0.8), ChipSpot(x: 236, y: 84, tilt: 14, scale: 0.95),
        ]),
    ]

    /// Glow colour of each story.
    static let glows: [(Double, Double, Double)] = [(84, 136, 255), (140, 92, 255), (255, 92, 176), (58, 214, 196)]

    static let stars: [(x: Double, y: Double, r: Double)] = [
        (98.5, 62.2, 0.8), (72, 106.5, 0.5), (192.5, 83.5, 0.8), (202.5, 63.2, 0.4),
        (266.2, 114.8, 0.9), (280, 81.2, 0.4), (41, 114, 0.6), (150, 96, 0.5),
    ]
    static let beams: [(x: Double, top: Double, bottom: Double)] = [(131.5, 86, 129), (219, 94, 129), (24, 100, 130), (272, 72, 118)]
}

private func clamp01(_ t: Double) -> Double { min(1, max(0, t)) }

/// Overshoots a little before settling: the chips land in their new places like on a spring.
private func easeOutBack(_ t: Double) -> Double {
    let c = 1.9
    return 1 + (c + 1) * pow(t - 1, 3) + c * pow(t - 1, 2)
}

/// Story on screen for a position `v` (0…1 across all stories).
private func storyAt(_ v: Double) -> Int {
    min(Metrics.count - 1, max(0, Int((v * Double(Metrics.count) - 1e-6).rounded(.up)) - 1))
}

/// 0…1: how far the story on screen has taken over from the previous one. Without `wraps` the
/// first story has nothing to take over from.
private func handOver(_ v: Double, wraps: Bool) -> Double {
    let k = storyAt(v)
    if k == 0, !wraps { return 1 }
    return clamp01((v * Double(Metrics.count) - Double(k)) * Metrics.story / Metrics.transition)
}

/// Dark pill with a faint rim; scales down while pressed.
private struct ActionStyle: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .frame(width: Metrics.actionWidth, height: Metrics.actionHeight)
            .background {
                Capsule().fill(
                    LinearGradient(colors: [Color(hex: 0x212121), Color(hex: 0x191919)], startPoint: .top, endPoint: .bottom)
                )
                Capsule().strokeBorder(
                    LinearGradient(colors: [.white.opacity(0.1), .white.opacity(0.16)], startPoint: .top, endPoint: .bottom),
                    lineWidth: 1
                )
            }
            .contentShape(Capsule())
            .modifier(PressFeedback(pressed: configuration.isPressed))
    }
}

private struct PressStyle: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .contentShape(Circle().inset(by: -8))
            .modifier(PressFeedback(pressed: configuration.isPressed))
    }
}

private struct PressFeedback: ViewModifier {
    var pressed: Bool

    func body(content: Content) -> some View {
        content
            .scaleEffect(pressed ? 0.97 : 1)
            .animation(.timingCurve(0.23, 1, 0.32, 1, duration: 0.13), value: pressed)
            .sensoryFeedback(.impact(weight: .light), trigger: pressed) { _, now in now }
    }
}

#Preview {
    ZStack {
        Color(hex: 0x101010).ignoresSafeArea()
        StoryProgressBars(autoPlay: true)
    }
}

import SwiftUI

struct TravelStatStoryCardPhoto {
    /// Address of the photo.
    var photo: String
    /// Flat colour shown until the photo arrives.
    var tint: Color
}

enum TravelStatStoryCardIcon {
    case trail, waves, star
}

struct TravelStatStoryCardStory {
    var icon: TravelStatStoryCardIcon
    var category: String
    /// Label of the outlined pill.
    var tag: String
    /// The number the figure counts up to.
    var figure: Int
    var unit = "%"
    var headline: String
    var detail: String
    /// Colour of the glow behind the story.
    var glow: Color
    /// The two pill-shaped thumbnails.
    var thumbs: [TravelStatStoryCardPhoto]
    /// The photo that fills the card for a moment as the story comes in.
    var cover: TravelStatStoryCardPhoto
}

/// A dark statistics card that tells one travel fact at a time, like a story: a glow in the story's
/// colour, a category and a tag, a large thin figure, two lines of text, two photo pills and a
/// segmented bar. A tap moves to the next story: the card blurs, a photo washes over it in the new
/// colour, and the new figure counts up while its segment starts to fill.
struct TravelStatStoryCard: View {
    /// Moves to the next story by itself every few seconds, in a loop.
    var autoPlay = false
    var stories = [
        TravelStatStoryCardStory(
            icon: .trail, category: "Backpacking", tag: "Budget Travel", figure: 35,
            headline: "backpackers visit Southeast Asia",
            detail: "every year to explore its rich culture and beautiful landscapes.",
            glow: Color(hex: 0xf2600c),
            thumbs: [
                TravelStatStoryCardPhoto(photo: travelStatStoryCardPhoto("1526772662000-3f88f10405ff", 220, 110), tint: Color(hex: 0x778691)),
                TravelStatStoryCardPhoto(photo: travelStatStoryCardPhoto("1506905925346-21bda4d32df4", 220, 110), tint: Color(hex: 0x6e6f80)),
            ],
            cover: TravelStatStoryCardPhoto(photo: travelStatStoryCardPhoto("1528127269322-539801943592", 640, 860), tint: Color(hex: 0x849b9b))
        ),
        TravelStatStoryCardStory(
            icon: .waves, category: "Island Hopping", tag: "Slow Travel", figure: 62,
            headline: "of island trips include a boat day",
            detail: "with snorkelling stops and sandbank picnics the most booked extras.",
            glow: Color(hex: 0x0b9c8f),
            thumbs: [
                TravelStatStoryCardPhoto(photo: travelStatStoryCardPhoto("1514282401047-d79a71a590e8", 220, 110), tint: Color(hex: 0x4ec6c6)),
                TravelStatStoryCardPhoto(photo: travelStatStoryCardPhoto("1506929562872-bb421503ef21", 220, 110), tint: Color(hex: 0x5d8f89)),
            ],
            cover: TravelStatStoryCardPhoto(photo: travelStatStoryCardPhoto("1505142468610-359e7d316be0", 640, 860), tint: Color(hex: 0x588b90))
        ),
        TravelStatStoryCardStory(
            icon: .star, category: "Stargazing", tag: "Winter Travel", figure: 48,
            headline: "of aurora trips are booked for March",
            detail: "when long clear nights meet the mildest weather of the season.",
            glow: Color(hex: 0x6d3ff0),
            thumbs: [
                TravelStatStoryCardPhoto(photo: travelStatStoryCardPhoto("1531366936337-7c912a4589a7", 220, 110), tint: Color(hex: 0x546187)),
                TravelStatStoryCardPhoto(photo: travelStatStoryCardPhoto("1504829857797-ddff29c27927", 220, 110), tint: Color(hex: 0x8f9ea4)),
            ],
            cover: TravelStatStoryCardPhoto(photo: travelStatStoryCardPhoto("1519681393784-d120267933ba", 640, 860), tint: Color(hex: 0x434f6d))
        ),
    ]
    /// Called with the story's index when a tap moves on to it.
    var onChange: ((Int) -> Void)? = nil

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    /// The story shown at `since`. While `running`, one more story has passed for every `Metrics.story` seconds after it.
    @State private var anchor = 0
    @State private var since = Date.distantPast
    @State private var running = false
    /// Keeps the timeline going while a tapped story settles.
    @State private var settling = false
    @State private var taps = 0

    private var auto: Bool { autoPlay && !reduceMotion }

    var body: some View {
        Button(action: advance) {
            TimelineView(.animation(paused: !running && !settling)) { timeline in
                let (index, elapsed) = position(at: timeline.date)
                card(index: index, previous: (index + stories.count - 1) % stories.count, elapsed: elapsed)
            }
        }
        .buttonStyle(PressStyle())
        .accessibilityLabel(Text(verbatim: summary(of: stories[position(at: .now).index])))
        .accessibilityHint("Shows the next story")
        .onAppear(perform: start)
        .onChange(of: auto) { start() }
        .task(id: taps) {
            guard taps > 0 else { return }
            // A touch takes over: the loop picks up from the tapped story once it has rested.
            try? await Task.sleep(for: .seconds(Metrics.resumeAfter))
            guard !Task.isCancelled else { return }
            running = auto
            try? await Task.sleep(for: .seconds(Metrics.story - Metrics.resumeAfter + 0.1))
            if !Task.isCancelled { settling = false }
        }
    }

    /// The story on screen at `date` and the seconds since it came in.
    private func position(at date: Date) -> (index: Int, elapsed: Double) {
        let passed = date.timeIntervalSince(since)
        guard running else { return (anchor, passed) }
        let turns = (passed / Metrics.story).rounded(.down)
        return ((anchor + Int(turns)) % stories.count, passed - turns * Metrics.story)
    }

    private func start() {
        anchor = position(at: .now).index
        // The loop opens on a story that has long settled and moves on `firstAt` seconds later.
        since = .now - (Metrics.story - (auto ? Metrics.firstAt : 0))
        running = auto
        settling = false
    }

    private func advance() {
        anchor = (position(at: .now).index + 1) % stories.count
        since = .now
        running = false
        settling = true
        taps += 1
        onChange?(anchor)
    }

    private func summary(of story: TravelStatStoryCardStory) -> String {
        "\(story.category). \(story.figure)\(story.unit) \(story.headline) \(story.detail)"
    }

    private func card(index: Int, previous: Int, elapsed: Double) -> some View {
        let motion = Motion(elapsed: elapsed, calm: reduceMotion)
        return ZStack {
            Color(hex: 0x151413)
            ForEach(stories.indices, id: \.self) { i in
                glow(stories[i].glow)
                    .opacity(motion.glow(i, index: index, previous: previous))
            }

            // What a story change blurs: the cover photos and the stories themselves.
            ZStack {
                ForEach(stories.indices, id: \.self) { i in
                    cover(stories[i])
                        .scaleEffect(motion.coverScale)
                        .opacity(i == index ? motion.cover : 0)
                }
                ForEach(stories.indices, id: \.self) { i in
                    content(
                        stories[i],
                        figure: i == index ? motion.count(to: stories[i].figure) : stories[i].figure,
                        opacity: { part in i == index ? motion.entering(part) : (i == previous ? motion.leaving : 0) },
                        // Only the story that comes in moves: each part rises into place as it appears.
                        lift: { part in i == index ? motion.lift(part) : 0 }
                    )
                }
            }
            .compositingGroup()
            .blur(radius: motion.blur)

            arrowButton
                .position(x: Metrics.width - Metrics.edge - Metrics.chip / 2, y: Metrics.edge + Metrics.chip / 2)

            segments(index: index, previous: previous, motion: motion)
                .position(x: Metrics.width / 2, y: Metrics.barTop + Metrics.barHeight / 2)
        }
        .frame(width: Metrics.width, height: Metrics.height)
        .clipShape(RoundedRectangle(cornerRadius: Metrics.radius, style: .continuous))
        .shadow(color: Color.rgba(46, 30, 18, 0.3), radius: 22, y: 20)
    }

    private func glow(_ colour: Color) -> some View {
        ZStack {
            RadialGradient(
                stops: [
                    .init(color: colour, location: 0),
                    .init(color: colour.opacity(0.55), location: 0.42),
                    .init(color: colour.opacity(0.12), location: 0.75),
                    .init(color: colour.opacity(0), location: 1),
                ],
                center: UnitPoint(x: 0.3, y: 0), startRadius: 0, endRadius: 330
            )
            RadialGradient(
                colors: [colour.opacity(0.16), colour.opacity(0)],
                center: UnitPoint(x: 0.2, y: 1.05), startRadius: 0, endRadius: 200
            )
        }
    }

    private func cover(_ story: TravelStatStoryCardStory) -> some View {
        TravelStatStoryCardImage(photo: story.cover)
            .overlay(story.glow.opacity(0.5))
            .frame(width: Metrics.width, height: Metrics.height)
            .clipped()
    }

    /// One story. `opacity` and `lift` place each of its parts: 0 the header, 1 the figure, 2 the text, 3 the thumbnails.
    private func content(
        _ story: TravelStatStoryCardStory, figure: Int, opacity: (Int) -> Double, lift: (Int) -> Double
    ) -> some View {
        func part<Content: View>(_ index: Int, @ViewBuilder _ view: () -> Content) -> some View {
            view().opacity(opacity(index)).offset(y: lift(index))
        }
        return ZStack(alignment: .topLeading) {
            part(0) {
                HStack(spacing: 9) {
                    TravelStatStoryCardGlyph(icon: story.icon)
                        .frame(width: Metrics.chip, height: Metrics.chip)
                        .background(Circle().fill(.white.opacity(0.18)))
                    Text(story.category)
                        .font(.system(size: 12, weight: .medium))
                        .foregroundStyle(.white.opacity(0.92))
                        .fixedSize()
                    Spacer(minLength: 0)
                    HStack(spacing: 6) {
                        Text(story.tag)
                            .font(.system(size: 11.5, weight: .medium))
                            .foregroundStyle(.white)
                        TravelStatStoryCardCaret()
                            .fill(.white.opacity(0.9))
                            .frame(width: 7, height: 4.5)
                    }
                    .fixedSize()
                    .padding(.horizontal, 14)
                    .frame(height: Metrics.chip)
                    .overlay(Capsule().strokeBorder(.white.opacity(0.42), lineWidth: 1))
                }
                .lineLimit(1)
                .frame(width: Metrics.width - Metrics.edge * 2 - Metrics.chip - 4, height: Metrics.chip)
                .offset(x: Metrics.edge, y: Metrics.edge)
            }
            part(1) {
                HStack(alignment: .top, spacing: 7) {
                    Text(verbatim: "\(figure)\(story.unit)")
                        .font(.system(size: 78, weight: .light))
                        .tracking(-2.5)
                        .monospacedDigit()
                        .foregroundStyle(.white)
                        .fixedSize()
                        .frame(height: Metrics.figureHeight)
                    TravelStatStoryCardArrow()
                        .stroke(.white.opacity(0.9), style: StrokeStyle(lineWidth: 1.4, lineCap: .round, lineJoin: .round))
                        .frame(width: 13, height: 13)
                        .padding(.top, 21)
                }
                .offset(x: Metrics.pad - 3, y: Metrics.figureTop)
            }
            part(2) {
                VStack(alignment: .leading, spacing: 2) {
                    Text(story.headline)
                        .font(.system(size: 13.5, weight: .semibold))
                        .foregroundStyle(.white)
                        .lineLimit(1)
                    Text(story.detail)
                        .font(.system(size: 13))
                        .lineSpacing(2)
                        .foregroundStyle(.white.opacity(0.56))
                        .lineLimit(2)
                }
                .frame(width: Metrics.width - Metrics.pad * 2, alignment: .leading)
                .offset(x: Metrics.pad, y: Metrics.textTop)
            }
            part(3) {
                HStack(spacing: 8) {
                    ForEach(Array(story.thumbs.prefix(2).enumerated()), id: \.offset) { _, thumb in
                        TravelStatStoryCardImage(photo: thumb)
                            .frame(width: Metrics.thumbWidth, height: Metrics.thumbHeight)
                            .clipShape(Capsule())
                    }
                }
                .offset(x: Metrics.pad, y: Metrics.thumbTop)
            }
        }
        .frame(width: Metrics.width, height: Metrics.height, alignment: .topLeading)
    }

    private var arrowButton: some View {
        TravelStatStoryCardArrow()
            .stroke(.white, style: StrokeStyle(lineWidth: 1.4, lineCap: .round, lineJoin: .round))
            .frame(width: 12, height: 12)
            .frame(width: Metrics.chip, height: Metrics.chip)
            .overlay(Circle().strokeBorder(.white.opacity(0.42), lineWidth: 1))
    }

    private func segments(index: Int, previous: Int, motion: Motion) -> some View {
        let count = Double(stories.count)
        let width = (Metrics.barWidth - Metrics.barGap * (count - 1)) / count
        return HStack(spacing: Metrics.barGap) {
            ForEach(stories.indices, id: \.self) { i in
                ZStack(alignment: .leading) {
                    Capsule().fill(.white.opacity(0.22))
                    // The story on screen fills its segment; the one before it fades back to dim.
                    Capsule().fill(.white)
                        .frame(width: max(Metrics.barHeight, width * (i == index ? motion.fill : 1)))
                        .opacity(i == index ? 1 : (i == previous ? motion.fading : 0))
                }
                .frame(width: width, height: Metrics.barHeight)
            }
        }
    }

    private struct PressStyle: ButtonStyle {
        func makeBody(configuration: Configuration) -> some View {
            configuration.label
                .scaleEffect(configuration.isPressed ? 0.98 : 1)
                .animation(.timingCurve(0.23, 1, 0.32, 1, duration: 0.13), value: configuration.isPressed)
                .sensoryFeedback(.impact(weight: .light), trigger: configuration.isPressed) { _, pressed in pressed }
        }
    }
}

private enum Metrics {
    static let width = 316.0
    static let height = 424.0
    static let radius = 32.0
    /// Inset of the header row, and of the text under it.
    static let edge = 13.0
    static let pad = 26.0
    /// Diameter of the round chips in the header.
    static let chip = 34.0

    static let figureTop = 104.0
    static let figureHeight = 88.0
    static let textTop = 219.0
    static let thumbTop = 298.0
    static let thumbWidth = 108.0
    static let thumbHeight = 54.0
    static let barTop = 392.0
    static let barWidth = 236.0
    static let barHeight = 3.0
    static let barGap = 8.0

    // autoPlay: a story stays this long, the first change comes `firstAt` seconds after the card appears.
    static let story = 2.8
    static let firstAt = 0.9
    /// After a tap the loop waits this long before it moves on from the tapped story.
    static let resumeAfter = 2.0

    // A story change, in seconds after the tap.
    static let leave = 0.26
    static let enterAt = 0.32
    static let enterStagger = 0.06
    static let enter = 0.46
    static let rise = 12.0
    static let coverAt = 0.06
    static let coverIn = 0.26
    static let coverOutAt = 0.42
    static let coverOut = 0.5
    static let coverZoom = 0.12
    static let blur = 12.0
    static let blurOutAt = 0.38
    static let blurOut = 0.4
    static let glow = 0.6
    static let count = 0.9
    /// The figure starts counting at this share of its value.
    static let countFrom = 0.15
    static let segmentFade = 0.3
    /// Length of the plain cross-fade under reduced motion.
    static let calmFade = 0.2
}

/// Where every part of the card stands `elapsed` seconds after a story came in.
private struct Motion {
    let elapsed: Double
    /// Reduced motion: stories cross-fade and nothing else moves.
    let calm: Bool

    private func ramp(from start: Double, over length: Double) -> Double {
        min(1, max(0, (elapsed - start) / length))
    }

    private func easeOut(_ t: Double) -> Double { 1 - pow(1 - t, 3) }
    private func easeInOut(_ t: Double) -> Double { t * t * (3 - 2 * t) }

    /// Opacity of the story that is going away.
    var leaving: Double {
        1 - (calm ? ramp(from: 0, over: Metrics.calmFade) : easeOut(ramp(from: 0, over: Metrics.leave)))
    }

    /// Opacity of one part of the story that is coming in, the parts following each other.
    func entering(_ part: Int) -> Double {
        if calm { return ramp(from: 0, over: Metrics.calmFade) }
        return easeOut(ramp(from: Metrics.enterAt + Metrics.enterStagger * Double(part), over: Metrics.enter))
    }

    /// How far below its place a part of the incoming story still is.
    func lift(_ part: Int) -> Double {
        calm ? 0 : Metrics.rise * (1 - entering(part))
    }

    var cover: Double {
        if calm { return 0 }
        return easeInOut(ramp(from: Metrics.coverAt, over: Metrics.coverIn))
            * (1 - easeInOut(ramp(from: Metrics.coverOutAt, over: Metrics.coverOut)))
    }

    var coverScale: Double {
        1 + Metrics.coverZoom * (1 - easeOut(ramp(from: Metrics.coverAt, over: Metrics.coverOutAt + Metrics.coverOut - Metrics.coverAt)))
    }

    var blur: Double {
        if calm { return 0 }
        return Metrics.blur * easeInOut(ramp(from: 0, over: Metrics.leave)) * (1 - easeInOut(ramp(from: Metrics.blurOutAt, over: Metrics.blurOut)))
    }

    /// Opacity of story `i`'s glow. Glows are stacked in order, so the one on top does the fading.
    func glow(_ i: Int, index: Int, previous: Int) -> Double {
        let mixed = easeInOut(ramp(from: 0, over: calm ? Metrics.calmFade : Metrics.glow))
        if i == index { return i > previous ? mixed : 1 }
        if i == previous { return i > index ? 1 - mixed : (mixed < 1 ? 1 : 0) }
        return 0
    }

    func count(to figure: Int) -> Int {
        if calm { return figure }
        let from = (Double(figure) * Metrics.countFrom).rounded()
        return Int((from + (Double(figure) - from) * easeOut(ramp(from: Metrics.enterAt, over: Metrics.count))).rounded())
    }

    /// Share of the current story's segment that is lit.
    var fill: Double { calm ? 1 : ramp(from: 0, over: Metrics.story) }

    /// Opacity of the previous story's segment.
    var fading: Double { 1 - ramp(from: 0, over: Metrics.segmentFade) }
}

private func travelStatStoryCardPhoto(_ id: String, _ width: Int, _ height: Int) -> String {
    "https://images.unsplash.com/photo-\(id)?auto=format&fit=crop&w=\(width)&h=\(height)&q=80"
}

/// A photo that fades in over its tint once it has loaded.
private struct TravelStatStoryCardImage: View {
    let photo: TravelStatStoryCardPhoto

    var body: some View {
        photo.tint.overlay {
            AsyncImage(url: URL(string: photo.photo), transaction: Transaction(animation: .easeOut(duration: 0.2))) { phase in
                if let image = phase.image {
                    image.resizable().scaledToFill().transition(.opacity)
                }
            }
        }
    }
}

/// The arrow pointing up and to the right, in a 12-point box.
private struct TravelStatStoryCardArrow: Shape {
    func path(in rect: CGRect) -> Path {
        let s = rect.width / 12
        var path = Path()
        path.move(to: CGPoint(x: 1.5 * s, y: 10.5 * s))
        path.addLine(to: CGPoint(x: 10.5 * s, y: 1.5 * s))
        path.move(to: CGPoint(x: 3.5 * s, y: 1.5 * s))
        path.addLine(to: CGPoint(x: 10.5 * s, y: 1.5 * s))
        path.addLine(to: CGPoint(x: 10.5 * s, y: 8.5 * s))
        return path
    }
}

private struct TravelStatStoryCardCaret: Shape {
    func path(in rect: CGRect) -> Path {
        var path = Path()
        path.move(to: CGPoint(x: rect.minX, y: rect.minY))
        path.addLine(to: CGPoint(x: rect.maxX, y: rect.minY))
        path.addLine(to: CGPoint(x: rect.midX, y: rect.maxY))
        path.closeSubpath()
        return path
    }
}

/// A 16-point pictogram for a story's category.
private struct TravelStatStoryCardGlyph: View {
    let icon: TravelStatStoryCardIcon

    var body: some View {
        Canvas { context, size in
            context.translateBy(x: (size.width - 16) / 2, y: (size.height - 16) / 2)
            let white = GraphicsContext.Shading.color(.white)
            let line = StrokeStyle(lineWidth: 1.4, lineCap: .round, lineJoin: .round)
            switch icon {
            case .trail:
                context.stroke(polyline([(1.5, 13), (6, 4.5), (8.6, 9.2), (10.4, 6.4), (14.5, 13)]), with: white, style: line)
            case .waves:
                context.stroke(polyline([(1.5, 6.4), (4.1, 4.6), (6.7, 6.4), (9.3, 4.6), (11.9, 6.4), (14.5, 4.6)]), with: white, style: line)
                context.stroke(polyline([(1.5, 11.4), (4.1, 9.6), (6.7, 11.4), (9.3, 9.6), (11.9, 11.4), (14.5, 9.6)]), with: white, style: line)
            case .star:
                var star = polyline([(8, 1.5), (9.6, 6.4), (14.5, 8), (9.6, 9.6), (8, 14.5), (6.4, 9.6), (1.5, 8), (6.4, 6.4)])
                star.closeSubpath()
                context.fill(star, with: white)
            }
        }
    }

    private func polyline(_ points: [(Double, Double)]) -> Path {
        var path = Path()
        path.addLines(points.map { CGPoint(x: $0.0, y: $0.1) })
        return path
    }
}

#Preview {
    ZStack {
        Color(hex: 0xe9e8e4).ignoresSafeArea()
        TravelStatStoryCard(autoPlay: true)
    }
}

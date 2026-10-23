import SwiftUI

struct TrailPhotoFanCardPhoto {
    /// Address of the photo.
    var photo: String
    /// Flat colour shown until the photo arrives.
    var tint: Color
}

enum TrailPhotoFanCardIcon {
    case clock, map, bolt
}

struct TrailPhotoFanCardFact {
    var icon: TrailPhotoFanCardIcon
    var label: String
}

/// A white trail card floating among clouds. While it is pressed the title turns olive, its arrow
/// slides out to the right and back in from the left, and the deck of three photos under the title
/// springs open into a rising fan; letting go folds the deck again.
struct TrailPhotoFanCard: View {
    /// Presses the card by itself, holds it open and lets go, in a loop.
    var autoPlay = false
    /// The last word shares its line with the arrow.
    var title = "Mountain Hike"
    /// The deck, bottom photo first.
    var photos = [
        TrailPhotoFanCardPhoto(
            photo: "https://images.unsplash.com/photo-1526772662000-3f88f10405ff?auto=format&fit=crop&w=240&h=320&q=80",
            tint: Color(hex: 0x778691)
        ),
        TrailPhotoFanCardPhoto(
            photo: "https://images.unsplash.com/photo-1470071459604-3b5ec3a7fe05?auto=format&fit=crop&w=240&h=320&q=80",
            tint: Color(hex: 0x6e756d)
        ),
        TrailPhotoFanCardPhoto(
            photo: "https://images.unsplash.com/photo-1464822759023-fed622ff2c3b?auto=format&fit=crop&w=240&h=320&q=80",
            tint: Color(hex: 0x647c82)
        ),
    ]
    var facts = [
        TrailPhotoFanCardFact(icon: .clock, label: "~8 Hours"),
        TrailPhotoFanCardFact(icon: .map, label: "8 km"),
        TrailPhotoFanCardFact(icon: .bolt, label: "Medium Level"),
    ]
    var summary = "Hiking on a mountain blends physical challenge with natural beauty, offering sweeping views."
    /// Colour of the title while the card is pressed.
    var accent = Color(hex: 0x5a5a32)
    /// Draws the clouds the card floats among.
    var clouds = true
    /// Called when the card is tapped.
    var onPress: (() -> Void)? = nil

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    /// A finger is on the card.
    @State private var held = false
    /// The autoPlay loop is pressing the card.
    @State private var played = false
    /// A touch stopped the loop; it starts again from rest a while after the last one.
    @State private var paused = false
    @State private var touches = 0
    /// Whole slides the arrow has made: each one carries it out on the right and back in on the left.
    @State private var slides = 0.0

    private var open: Bool { held || played }
    private var auto: Bool { autoPlay && !reduceMotion }

    var body: some View {
        ZStack {
            if clouds {
                TrailPhotoFanCardSky(still: reduceMotion)
            }
            card
                .scaleEffect(open ? Metrics.pressScale : 1)
                .animation(reduceMotion ? nil : Metrics.pressSpring, value: open)
                .gesture(press)
        }
        .frame(width: Metrics.width, height: Metrics.height)
        .sensoryFeedback(.impact(weight: .light), trigger: held) { _, down in down }
        .onChange(of: open) { _, isOpen in
            guard isOpen, !reduceMotion else { return }
            withAnimation(Metrics.slideCurve) { slides += 1 }
        }
        .task(id: auto && !paused) {
            guard auto, !paused else { return }
            // Every step is timed from the loop's start, so the period does not drift.
            let clock = ContinuousClock()
            let start = clock.now
            var turn = 0.0
            while !Task.isCancelled {
                try? await Task.sleep(until: start + .seconds(turn * Metrics.loop + Metrics.pressAt), clock: clock)
                if Task.isCancelled { break }
                played = true
                try? await Task.sleep(until: start + .seconds(turn * Metrics.loop + Metrics.releaseAt), clock: clock)
                if Task.isCancelled { break }
                played = false
                turn += 1
            }
        }
        .task(id: touches) {
            guard touches > 0 else { return }
            try? await Task.sleep(for: .seconds(Metrics.resumeAfter))
            if !Task.isCancelled, !held { paused = false }
        }
    }

    private var press: some Gesture {
        DragGesture(minimumDistance: 0)
            .onChanged { _ in
                guard !held else { return }
                held = true
                paused = true
                played = false
            }
            .onEnded { value in
                held = false
                touches += 1
                // A finger that slid off the card before lifting is not a tap.
                if CGRect(x: 0, y: 0, width: Metrics.width, height: Metrics.height).contains(value.location) { onPress?() }
            }
    }

    private var card: some View {
        ZStack(alignment: .topLeading) {
            RoundedRectangle(cornerRadius: Metrics.radius, style: .continuous)
                .fill(.white)
                .shadow(color: Color.rgba(6, 58, 88, 0.34), radius: 22, y: 20)

            heading
                .offset(x: Metrics.pad, y: Metrics.titleTop)

            ForEach(Array(photos.prefix(3).enumerated()), id: \.offset) { index, photo in
                deckPhoto(photo, index: index)
            }

            HStack(spacing: 14) {
                ForEach(Array(facts.enumerated()), id: \.offset) { _, fact in
                    HStack(spacing: 5) {
                        TrailPhotoFanCardGlyph(icon: fact.icon)
                        Text(fact.label)
                            .font(.system(size: 10.5, weight: .semibold))
                            .foregroundStyle(Color(hex: 0x1a1a1a))
                    }
                }
            }
            .lineLimit(1)
            .fixedSize()
            .frame(height: 14)
            .offset(x: Metrics.pad, y: Metrics.factsTop)

            Text(summary)
                .font(.system(size: 14))
                // 20-point lines.
                .lineSpacing(3.3)
                .foregroundStyle(Color(hex: 0x737373))
                .lineLimit(3)
                .frame(width: Metrics.width - Metrics.pad * 2, alignment: .topLeading)
                .offset(x: Metrics.pad, y: Metrics.summaryTop)
        }
        .frame(width: Metrics.width, height: Metrics.height, alignment: .topLeading)
        .contentShape(RoundedRectangle(cornerRadius: Metrics.radius, style: .continuous))
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(Text(verbatim: "\(title). \(facts.map(\.label).joined(separator: ", ")). \(summary)"))
        .accessibilityAddTraits(.isButton)
    }

    private var heading: some View {
        let cut = title.lastIndex(of: " ")
        let first = cut.map { String(title[..<$0]) }
        let last = cut.map { String(title[title.index(after: $0)...]) } ?? title
        return VStack(alignment: .leading, spacing: 0) {
            if let first {
                Text(first).frame(height: Metrics.titleLine)
            }
            HStack(spacing: Metrics.arrowGap) {
                Text(last)
                TrailPhotoFanCardArrow(slides: slides)
                    .stroke(style: StrokeStyle(lineWidth: Metrics.arrowStroke, lineJoin: .miter))
                    .frame(width: Metrics.arrowBox, height: Metrics.arrowHeight)
                    .clipped()
            }
            .frame(height: Metrics.titleLine)
        }
        .font(.system(size: 58, weight: .bold))
        .tracking(-2.6)
        .lineLimit(1)
        .fixedSize()
        .foregroundStyle(open ? accent : Color(hex: 0x0c0c0c))
        .animation(Metrics.tintCurve, value: open)
    }

    private func deckPhoto(_ photo: TrailPhotoFanCardPhoto, index: Int) -> some View {
        let pose = open ? Metrics.fan[index] : Metrics.deck[index]
        // The fan opens from the bottom photo up and folds from the top photo down.
        let order = Double(open ? index : 2 - index)
        let spring = open ? Metrics.openSpring : Metrics.closeSpring
        let frame = RoundedRectangle(cornerRadius: Metrics.photoRadius, style: .continuous).fill(.white)
        return ZStack {
            // The shadow of a photo that has lifted off the deck.
            frame
                .shadow(color: Color.rgba(14, 38, 52, 0.3), radius: 12, y: 12)
                .opacity(open ? 1 : 0)
            frame
                .shadow(color: Color.rgba(14, 38, 52, 0.22), radius: 4, y: 3)
            TrailPhotoFanCardImage(photo: photo)
                .padding(Metrics.photoBorder)
        }
        .frame(width: Metrics.photoWidth, height: Metrics.photoHeight)
        .scaleEffect(pose.scale)
        .rotationEffect(.degrees(pose.angle))
        .position(x: pose.x, y: pose.y)
        .animation(reduceMotion ? nil : spring.delay(order * Metrics.stagger), value: open)
    }
}

private enum Metrics {
    static let width = 316.0
    static let height = 404.0
    static let radius = 28.0
    static let pad = 20.0

    static let titleTop = 18.0
    static let titleLine = 62.0
    static let arrowGap = 10.0
    // The arrow is drawn in a 44 x 34 box; the window it slides through is a little wider.
    static let arrowBox = 46.0
    static let arrowHeight = 34.0
    static let arrowStroke = 7.0
    /// How far one slide carries the arrow: out of the window on the right, in again from the left.
    static let arrowTravel = 52.0

    static let photoWidth = 82.0
    static let photoHeight = 110.0
    static let photoRadius = 15.0
    static let photoBorder = 4.0
    static let factsTop = 300.0
    static let summaryTop = 324.0

    struct Pose {
        let x: Double
        let y: Double
        let angle: Double
        let scale: Double
    }

    // Centre, angle in degrees and scale of each photo, bottom photo first.
    static let deck = [
        Pose(x: 61, y: 222, angle: 0, scale: 0.9),
        Pose(x: 79, y: 222, angle: 0, scale: 0.95),
        Pose(x: 97, y: 222, angle: 0, scale: 1),
    ]
    static let fan = [
        Pose(x: 65, y: 224, angle: -5, scale: 1),
        Pose(x: 152, y: 219, angle: 8, scale: 1),
        Pose(x: 231, y: 186, angle: 25, scale: 1),
    ]

    static let pressScale = 0.98
    static let openSpring = Animation.interpolatingSpring(mass: 1, stiffness: 260, damping: 19)
    static let closeSpring = Animation.interpolatingSpring(mass: 1, stiffness: 300, damping: 26)
    static let pressSpring = Animation.interpolatingSpring(mass: 1, stiffness: 320, damping: 24)
    /// Seconds between one photo and the next.
    static let stagger = 0.07
    static let tintCurve = Animation.timingCurve(0.23, 1, 0.32, 1, duration: 0.26)
    static let slideCurve = Animation.timingCurve(0.65, 0, 0.35, 1, duration: 0.52)

    // autoPlay loop (seconds): a rest, a long press, and a rest again while the deck folds.
    static let pressAt = 0.8
    static let releaseAt = 3.2
    static let loop = 5.0
    /// After the last touch the loop waits this long before it starts again from rest.
    static let resumeAfter = 2.0

    // The sky is drawn this far beyond the card on every side.
    static let skyBleed = 110.0
}

/// The title's arrow, drawn twice: one copy leaves on the right while the next comes in from the left.
private struct TrailPhotoFanCardArrow: Shape {
    var slides: Double

    var animatableData: Double {
        get { slides }
        set { slides = newValue }
    }

    func path(in rect: CGRect) -> Path {
        let shift = (slides - slides.rounded(.down)) * Metrics.arrowTravel
        var path = Path()
        for x in [shift + 1, shift + 1 - Metrics.arrowTravel] {
            path.move(to: CGPoint(x: x, y: 17))
            path.addLine(to: CGPoint(x: x + 36.5, y: 17))
            path.move(to: CGPoint(x: x + 22, y: 2.5))
            path.addLine(to: CGPoint(x: x + 36.5, y: 17))
            path.addLine(to: CGPoint(x: x + 22, y: 31.5))
        }
        return path
    }
}

/// A 12-point pictogram in front of a fact.
private struct TrailPhotoFanCardGlyph: View {
    let icon: TrailPhotoFanCardIcon

    var body: some View {
        Canvas { context, _ in
            let grey = GraphicsContext.Shading.color(Color(hex: 0x8c8c8c))
            let cut = GraphicsContext.Shading.color(.white)
            switch icon {
            case .clock:
                context.fill(Path(ellipseIn: CGRect(x: 0.5, y: 0.5, width: 11, height: 11)), with: grey)
                context.stroke(
                    polyline([(6, 3.3), (6, 6), (8, 7.2)], closed: false),
                    with: cut, style: StrokeStyle(lineWidth: 1.2, lineCap: .round, lineJoin: .round)
                )
            case .map:
                context.fill(
                    polyline([(1, 2.6), (4.4, 1.2), (7.6, 2.6), (11, 1.2), (11, 9.4), (7.6, 10.8), (4.4, 9.4), (1, 10.8)], closed: true),
                    with: grey
                )
                let fold = StrokeStyle(lineWidth: 1, lineCap: .round)
                context.stroke(polyline([(4.4, 1.2), (4.4, 9.4)], closed: false), with: cut, style: fold)
                context.stroke(polyline([(7.6, 2.6), (7.6, 10.8)], closed: false), with: cut, style: fold)
            case .bolt:
                context.fill(
                    polyline([(7.2, 0.8), (2.4, 6.9), (5.6, 6.9), (4.8, 11.2), (9.6, 5.1), (6.4, 5.1)], closed: true),
                    with: grey
                )
            }
        }
        .frame(width: 12, height: 12)
    }

    private func polyline(_ points: [(Double, Double)], closed: Bool) -> Path {
        var path = Path()
        path.addLines(points.map { CGPoint(x: $0.0, y: $0.1) })
        if closed { path.closeSubpath() }
        return path
    }
}

/// A photo that fades in over its tint once it has loaded.
private struct TrailPhotoFanCardImage: View {
    let photo: TrailPhotoFanCardPhoto

    var body: some View {
        photo.tint
            .overlay {
                AsyncImage(url: URL(string: photo.photo), transaction: Transaction(animation: .easeOut(duration: 0.2))) { phase in
                    if let image = phase.image {
                        image.resizable().scaledToFill().transition(.opacity)
                    }
                }
            }
            .clipShape(RoundedRectangle(cornerRadius: Metrics.photoRadius - Metrics.photoBorder, style: .continuous))
    }
}

/// Soft clouds behind the card: two near ones it seems to rest on and two far ones, swaying against
/// each other once per loop.
private struct TrailPhotoFanCardSky: View {
    let still: Bool

    var body: some View {
        TimelineView(.animation(paused: still)) { timeline in
            let turn = still ? 0 : timeline.date.timeIntervalSinceReferenceDate / Metrics.loop * tau
            ZStack {
                TrailPhotoFanCardClouds(clouds: farClouds, blur: 9, opacity: 0.55)
                    .offset(x: -3 * sin(turn), y: 1.5 * cos(turn))
                TrailPhotoFanCardClouds(clouds: nearClouds, blur: 6, opacity: 0.96)
                    .offset(x: 6 * sin(turn), y: -2 * cos(turn))
            }
        }
        .allowsHitTesting(false)
    }
}

private struct TrailPhotoFanCardClouds: View, Equatable {
    let clouds: [TrailPhotoFanCardCloud]
    let blur: Double
    let opacity: Double

    var body: some View {
        Canvas { context, _ in
            context.translateBy(x: Metrics.skyBleed, y: Metrics.skyBleed)
            context.addFilter(.blur(radius: blur))
            for cloud in clouds {
                context.fill(cloud.path, with: .color(.white.opacity(opacity)))
            }
        }
        .frame(width: Metrics.width + Metrics.skyBleed * 2, height: Metrics.height + Metrics.skyBleed * 2)
    }
}

/// A cloud in the card's coordinates: round puffs standing on a flat pill.
private struct TrailPhotoFanCardCloud: Equatable {
    let puffs: [[Double]]
    let base: CGRect

    var path: Path {
        var path = Path(roundedRect: base, cornerRadius: base.height / 2, style: .circular)
        for puff in puffs {
            path.addEllipse(in: CGRect(x: puff[0] - puff[2], y: puff[1] - puff[2], width: puff[2] * 2, height: puff[2] * 2))
        }
        return path
    }
}

private let nearClouds = [
    TrailPhotoFanCardCloud(
        puffs: [[-28, -18, 30], [14, -40, 40], [66, -52, 46], [118, -30, 36], [156, -10, 26]],
        base: CGRect(x: -58, y: -22, width: 240, height: 42)
    ),
    TrailPhotoFanCardCloud(
        puffs: [[150, 452, 30], [196, 430, 42], [252, 412, 52], [310, 428, 44], [352, 448, 32]],
        base: CGRect(x: 118, y: 436, width: 268, height: 46)
    ),
]

private let farClouds = [
    TrailPhotoFanCardCloud(
        puffs: [[300, -62, 18], [326, -72, 24], [352, -60, 18]],
        base: CGRect(x: 280, y: -66, width: 92, height: 22)
    ),
    TrailPhotoFanCardCloud(
        puffs: [[-10, 448, 16], [14, 440, 22], [40, 450, 16]],
        base: CGRect(x: -28, y: 446, width: 86, height: 18)
    ),
]

#Preview {
    ZStack {
        Color(hex: 0x1f9fc4).ignoresSafeArea()
        TrailPhotoFanCard(autoPlay: true)
    }
}

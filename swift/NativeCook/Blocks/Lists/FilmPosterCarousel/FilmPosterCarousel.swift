import SwiftUI
import UIKit

private enum FilmPosterCarouselLayout {
    static let posterHeight: CGFloat = 550
    static let fadeFrom: CGFloat = 0.74
    static let panelBottom: CGFloat = 775.5
    static let panelRadius: CGFloat = 34
    static let panelShadowOpacity = 1.0
    static let panelShadowRadius: CGFloat = 2.2
    static let panelShadowY: CGFloat = 2
    static let footerTop = panelBottom - panelRadius - 10

    static let closeRight: CGFloat = 32.3
    static let closeTop: CGFloat = 33.7
    static let closeSize: CGFloat = 42
    static let closeRadius: CGFloat = 8
    static let closeIcon: CGFloat = 15

    static let titleX: CGFloat = 44.1
    static let titleBaseline: CGFloat = 525.3
    static let titleSize: CGFloat = 50
    static let titleTracking: CGFloat = 0.2
    static let metaX: CGFloat = 43.6
    static let metaBaseline: CGFloat = 556.4
    static let metaSize: CGFloat = 14
    static let metaTracking: CGFloat = 0.5
    static let durationX: CGFloat = 43.4
    static let durationBaseline: CGFloat = 588.7
    static let durationSize: CGFloat = 18
    static let durationTracking: CGFloat = 1.2

    static let starsCenterX: CGFloat = 50.9
    static let starsCenterY: CGFloat = 616.9
    static let starSize: CGFloat = 16
    static let starPitch: CGFloat = 22
    static let starInnerRadius: CGFloat = 0.5
    static let starHalo = 0.55

    static let tagsX: CGFloat = 42.7
    static let tagsTop: CGFloat = 662.3
    static let tagHeight: CGFloat = 31.5
    static let tagPaddingX: CGFloat = 16.5
    static let tagGap: CGFloat = 13
    static let tagSize: CGFloat = 14.6
    static let tagTracking: CGFloat = -0.3

    static let chevronCenterX: CGFloat = 201.4
    static let chevronTop: CGFloat = 747
    static let chevronWidth: CGFloat = 26
    static let chevronHeight: CGFloat = 12
    static let chevronStroke: CGFloat = 2.2

    static let footerBaseline: CGFloat = 831.7
    static let footerSize: CGFloat = 19.5
    static let footerTracking: CGFloat = 1.75
    static let baselineRatio: CGFloat = 0.97
}

private enum FilmPosterCarouselMotionSpec {
    static let loop = 7.0
    static let turns = [
        FilmPosterCarouselTurn(lift: 0.8, page: 1.1, down: 2.05, stars: 2.5, from: 0, to: 1),
        FilmPosterCarouselTurn(lift: 3.8, page: 4.1, down: 5.05, stars: 5.5, from: 1, to: 0),
    ]
    static let liftScale = 0.1
    static let liftRadius: CGFloat = 34
    static let liftShadow = 0.55
    static let liftShadowY: CGFloat = 18
    static let liftShadowBlur: CGFloat = 36
    static let pickDuration = 0.45
    static let throwX = 1.15
    static let throwTilt = 7.0
    static let throwDrop: CGFloat = 36
    static let throwShrink = 0.04
    static let artLag = 0.3
    static let waitingScale = 0.14
    static let waitingDim = 0.55
    static let roomBlur: CGFloat = 40
    static let roomDim = 0.55
    static let sheenAlpha = 0.22
    static let sheenWidth = 0.55
    static let sheenSlant = 20.0
    static let sheenTravel = 1.6
    static let starPeak = 0.14
    static let starStagger = 0.055
    static let starDuration = 0.22
    static let starsTotal = starDuration + starStagger * 4
    static let pageResponse = 0.62
    static let pageDamping = 0.86
    static let downResponse = 0.5
    static let downDamping = 0.78
    static let pickResponse = 0.35
    static let pickDamping = 1.0
    static let idleResume = 3.5
    static let reducedDuration = 0.2
    static let bobDuration = 1.75
    static let resumeDuration = 0.3
    static let resumeRestartDelay = 0.32
    static let flickThreshold = 0.11
    static let velocityProjection = 0.2
    static let rubberBand = 0.3
}

private struct FilmPosterCarouselTurn {
    let lift: Double
    let page: Double
    let down: Double
    let stars: Double
    let from: Double
    let to: Double
}

private struct FilmPosterCarouselPose {
    var page = 0.0
    var lift = 0.0
    var swell = [0.0, 0.0]
}

private func filmPosterCarouselClamp(_ value: Double) -> Double {
    min(max(value, 0), 1)
}

private func filmPosterCarouselSegment(_ time: Double, _ start: Double, _ end: Double) -> Double {
    filmPosterCarouselClamp((time - start) / (end - start))
}

private func filmPosterCarouselSpringStep(
    time: Double,
    response: Double,
    damping: Double
) -> Double {
    guard time > 0 else { return 0 }
    guard time <= response * 4 else { return 1 }
    let frequency = 2 * Double.pi / response
    if damping >= 1 {
        return 1 - exp(-frequency * time) * (1 + frequency * time)
    }
    let damped = frequency * sqrt(1 - damping * damping)
    return 1 - exp(-damping * frequency * time) * (
        cos(damped * time) + (damping * frequency / damped) * sin(damped * time)
    )
}

private func filmPosterCarouselEaseOut(_ progress: Double) -> Double {
    FilmPosterCarouselAnimatedValue.cubicBezier(
        progress,
        x1: 0.23,
        y1: 1,
        x2: 0.32,
        y2: 1
    )
}

private func filmPosterCarouselPose(at time: Double) -> FilmPosterCarouselPose {
    var pose = FilmPosterCarouselPose()
    for turn in FilmPosterCarouselMotionSpec.turns {
        let page = filmPosterCarouselSpringStep(
            time: time - turn.page,
            response: FilmPosterCarouselMotionSpec.pageResponse,
            damping: FilmPosterCarouselMotionSpec.pageDamping
        )
        pose.page += (turn.to - turn.from) * page
        let lift = filmPosterCarouselEaseOut(
            filmPosterCarouselSegment(time, turn.lift, turn.lift + FilmPosterCarouselMotionSpec.pickDuration)
        )
        let down = filmPosterCarouselSpringStep(
            time: time - turn.down,
            response: FilmPosterCarouselMotionSpec.downResponse,
            damping: FilmPosterCarouselMotionSpec.downDamping
        )
        pose.lift += lift * (1 - down)
        let swell = filmPosterCarouselSegment(
            time,
            turn.stars,
            turn.stars + FilmPosterCarouselMotionSpec.starsTotal
        )
        if swell > 0, swell < 1 {
            pose.swell[Int(turn.to)] = swell
        }
    }
    return pose
}

private func filmPosterCarouselStarScale(swell: Double, index: Int) -> CGFloat {
    guard swell > 0, swell < 1 else { return 1 }
    let local = filmPosterCarouselSegment(
        swell * FilmPosterCarouselMotionSpec.starsTotal,
        FilmPosterCarouselMotionSpec.starStagger * Double(index),
        FilmPosterCarouselMotionSpec.starStagger * Double(index) + FilmPosterCarouselMotionSpec.starDuration
    )
    let eased = FilmPosterCarouselAnimatedValue.cubicBezier(
        local,
        x1: 0.45,
        y1: 0,
        x2: 0.55,
        y2: 1
    )
    return 1 + CGFloat(FilmPosterCarouselMotionSpec.starPeak * sin(.pi * eased))
}

private struct FilmPosterCarouselFilm: Identifiable {
    let id: String
    let title: String
    let year: String
    let director: String
    let duration: String
    let rating: Double
    let tags: [String]
    let poster: UIImage
    let panel: Color
    let chip: Color
    let chevron: Color
    let meta: Color
    let footer: Color
    let footerInk: Color
}

private func filmPosterCarouselImage(named name: String) -> UIImage {
    guard let image = UIImage(named: name) else {
        assertionFailure("Missing Film Poster Carousel image: \(name)")
        return UIImage()
    }
    return image
}

private let filmPosterCarouselFilms = [
    FilmPosterCarouselFilm(
        id: "skyride",
        title: "Skyride",
        year: "2019",
        director: "Rafi Hassan",
        duration: "2h 10m",
        rating: 4.5,
        tags: ["#action", "#romantic", "#family"],
        poster: filmPosterCarouselImage(named: "FilmPosterCarousel-poster-skyride.jpg"),
        panel: Color(red: 0x15 / 255, green: 0x1A / 255, blue: 0x2D / 255),
        chip: Color(red: 0x39 / 255, green: 0x3A / 255, blue: 0x4E / 255),
        chevron: Color(red: 0xAD / 255, green: 0xAE / 255, blue: 0xC0 / 255),
        meta: Color(red: 0xC6 / 255, green: 0xC8 / 255, blue: 0xD7 / 255),
        footer: Color(red: 0xF0 / 255, green: 0x36 / 255, blue: 0x5D / 255),
        footerInk: Color(red: 0xFF / 255, green: 0xEF / 255, blue: 0xF6 / 255)
    ),
    FilmPosterCarouselFilm(
        id: "pride",
        title: "The Lion Path",
        year: "2019",
        director: "Joel Mercer",
        duration: "2h 22m",
        rating: 5,
        tags: ["#drama", "#adventure", "#family"],
        poster: filmPosterCarouselImage(named: "FilmPosterCarousel-poster-cub.jpg"),
        panel: Color(red: 0x1F / 255, green: 0x15 / 255, blue: 0x16 / 255),
        chip: Color(red: 0x41 / 255, green: 0x39 / 255, blue: 0x37 / 255),
        chevron: Color(red: 0xB4 / 255, green: 0xA9 / 255, blue: 0xA8 / 255),
        meta: Color(red: 0xD2 / 255, green: 0xC9 / 255, blue: 0xCC / 255),
        footer: Color(red: 0xF3 / 255, green: 0xD7 / 255, blue: 0x2E / 255),
        footerInk: Color(red: 0x14 / 255, green: 0x0A / 255, blue: 0x06 / 255)
    ),
]

private enum FilmPosterCarouselCurve {
    case linear
    case easeInOut
    case resume
    case spring(dampingRatio: Double, velocity: Double)
}

private struct FilmPosterCarouselAnimatedValue {
    private var from: Double
    private(set) var to: Double
    private var startedAt = Date.distantPast
    private var duration = 0.0
    private var curve = FilmPosterCarouselCurve.easeInOut

    init(_ value: Double) {
        from = value
        to = value
    }

    func value(at date: Date) -> Double {
        guard duration > 0 else { return to }
        let elapsed = max(0, date.timeIntervalSince(startedAt))
        guard elapsed < duration else { return to }
        let progress = elapsed / duration
        let eased: Double
        switch curve {
        case .linear:
            eased = progress
        case .easeInOut:
            eased = progress < 0.5
                ? 2 * progress * progress
                : 1 - pow(2 - 2 * progress, 2) / 2
        case .resume:
            eased = FilmPosterCarouselAnimatedValue.cubicBezier(
                progress,
                x1: 0.23,
                y1: 1,
                x2: 0.32,
                y2: 1
            )
        case let .spring(dampingRatio, velocity):
            eased = FilmPosterCarouselAnimatedValue.springProgress(
                progress,
                duration: duration,
                dampingRatio: dampingRatio,
                velocity: velocity,
                distance: to - from
            )
        }
        return from + (to - from) * eased
    }

    mutating func animate(
        to target: Double,
        at date: Date,
        duration: Double,
        delay: Double = 0,
        curve: FilmPosterCarouselCurve
    ) {
        from = value(at: date)
        to = target
        startedAt = date.addingTimeInterval(delay)
        self.duration = duration
        self.curve = curve
    }

    mutating func jump(to target: Double) {
        self = FilmPosterCarouselAnimatedValue(target)
    }

    func isRunning(at date: Date) -> Bool {
        date < startedAt.addingTimeInterval(duration)
    }

    private static func springProgress(
        _ progress: Double,
        duration: Double,
        dampingRatio: Double,
        velocity: Double,
        distance: Double
    ) -> Double {
        let zeta = min(max(dampingRatio, 0.01), 0.999)
        let naturalFrequency = 4 / (zeta * duration)
        let dampedFrequency = naturalFrequency * sqrt(1 - zeta * zeta)
        let normalizedVelocity = abs(distance) > 0.0001 ? velocity / distance : 0

        func raw(_ t: Double) -> Double {
            let decay = exp(-zeta * naturalFrequency * t)
            let sineCoefficient = (zeta * naturalFrequency - normalizedVelocity) / dampedFrequency
            return 1 - decay * (cos(dampedFrequency * t) + sineCoefficient * sin(dampedFrequency * t))
        }

        let end = raw(duration)
        guard abs(end) > 0.0001 else { return progress }
        return raw(progress * duration) / end
    }

    static func cubicBezier(
        _ progress: Double,
        x1: Double,
        y1: Double,
        x2: Double,
        y2: Double
    ) -> Double {
        if progress <= 0 { return 0 }
        if progress >= 1 { return 1 }
        func sample(_ t: Double, _ a: Double, _ b: Double) -> Double {
            let inverse = 1 - t
            return 3 * inverse * inverse * t * a + 3 * inverse * t * t * b + t * t * t
        }

        var low = 0.0
        var high = 1.0
        for _ in 0..<12 {
            let middle = (low + high) / 2
            if sample(middle, x1, x2) < progress {
                low = middle
            } else {
                high = middle
            }
        }
        return sample((low + high) / 2, y1, y2)
    }
}

struct FilmPosterCarousel: View {
    static let loop: Double = FilmPosterCarouselMotionSpec.loop

    var autoPlay = false
    var freezeAt: Double?

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var page = FilmPosterCarouselAnimatedValue(0)
    @State private var lift = FilmPosterCarouselAnimatedValue(0)
    @State private var swell0 = FilmPosterCarouselAnimatedValue(0)
    @State private var swell1 = FilmPosterCarouselAnimatedValue(0)
    @State private var scripted = false
    @State private var loopStartedAt = Date.now
    @State private var rendering = false
    @State private var renderGeneration = 0
    @State private var renderStopDelay = 1.5
    @State private var reducedScriptGeneration = 0
    @State private var interactionGeneration = 0
    @State private var manual = false
    @State private var contactActive = false
    @State private var dragStart = 0.0
    @State private var dragActivated = false
    @State private var footerPressed = false
    @State private var selectionHaptic = UISelectionFeedbackGenerator()

    var body: some View {
        GeometryReader { proxy in
            TimelineView(.animation(paused: !rendering || freezeAt != nil)) { timeline in
                content(size: proxy.size, date: timeline.date)
            }
            .frame(width: proxy.size.width, height: proxy.size.height, alignment: .topLeading)
        }
        .background(Color.black.ignoresSafeArea())
        .ignoresSafeArea()
        .preferredColorScheme(.dark)
        .allowsHitTesting(freezeAt == nil)
        .onAppear {
            selectionHaptic.prepare()
            if reduceMotion {
                holdReducedMotion(at: .now)
            } else if autoPlay, freezeAt == nil {
                startScript(at: .now)
            }
        }
        .onChange(of: reduceMotion) { _, reduced in
            interactionGeneration += 1
            reducedScriptGeneration += 1
            if reduced {
                holdReducedMotion(at: .now)
            } else if autoPlay, !manual, freezeAt == nil {
                startScript(at: .now)
            }
        }
        .task(id: renderGeneration) {
            guard rendering, !scripted else { return }
            try? await Task.sleep(for: .seconds(renderStopDelay))
            guard !Task.isCancelled, !scripted else { return }
            rendering = false
        }
        .task(id: interactionGeneration) { await resumeAfterInteraction() }
    }

    private func content(size: CGSize, date: Date) -> some View {
        let render = renderValues(at: date)

        return ZStack(alignment: .topLeading) {
            Color.black
            room(size: size, page: render.page, lift: render.lift)

            ForEach(Array(filmPosterCarouselFilms.enumerated()).reversed(), id: \.element.id) { index, film in
                filmCard(
                    film: film,
                    index: index,
                    size: size,
                    page: render.page,
                    lift: render.lift,
                    swell: render.swells[index],
                    clock: render.clock
                )
            }

            FilmPosterCarouselTouchView(
                onBegan: { beginDrag(width: size.width) },
                onChanged: { translation in updateDrag(translation: translation, width: size.width) },
                onEnded: { translation, velocity in
                    endDrag(translation: translation, velocity: velocity, width: size.width)
                }
            )
            .frame(width: size.width, height: size.height)

            footerTouchOverlay(size: size)
            closeButton(size: size)
        }
        .frame(width: size.width, height: size.height, alignment: .topLeading)
    }

    private func filmCard(
        film: FilmPosterCarouselFilm,
        index: Int,
        size: CGSize,
        page: Double,
        lift: Double,
        swell: Double,
        clock: Double
    ) -> some View {
        let distance = Double(index) - page
        let liftAmount = filmPosterCarouselClamp(lift)
        let baseScale = 1 - FilmPosterCarouselMotionSpec.liftScale * lift
        let rounded = FilmPosterCarouselMotionSpec.liftRadius * CGFloat(liftAmount)
        let opacity: Double
        let x: CGFloat
        let y: CGFloat
        let rotation: Double
        let scale: Double
        let shadow: Double

        if reduceMotion {
            opacity = distance <= 0 ? 1 - filmPosterCarouselClamp(-distance) : 1
            x = 0
            y = 0
            rotation = 0
            scale = 1
            shadow = 0
        } else if distance <= 0 {
            let progress = filmPosterCarouselClamp(-distance)
            opacity = 1
            x = -CGFloat(progress * Double(size.width) * FilmPosterCarouselMotionSpec.throwX)
            y = FilmPosterCarouselMotionSpec.throwDrop * CGFloat(progress * progress)
            rotation = -FilmPosterCarouselMotionSpec.throwTilt * progress
            scale = baseScale * (1 - FilmPosterCarouselMotionSpec.throwShrink * progress)
            shadow = FilmPosterCarouselMotionSpec.liftShadow * liftAmount
        } else {
            let waiting = filmPosterCarouselClamp(distance)
            opacity = 1
            x = 0
            y = 0
            rotation = 0
            scale = baseScale * (1 - FilmPosterCarouselMotionSpec.waitingScale * waiting)
            shadow = FilmPosterCarouselMotionSpec.liftShadow * liftAmount * (1 - waiting)
        }

        return cardContents(
            film: film,
            index: index,
            size: size,
            page: page,
            lift: lift,
            swell: swell,
            clock: clock,
            cornerRadius: rounded
        )
        .frame(width: size.width, height: size.height, alignment: .topLeading)
        .clipShape(RoundedRectangle(cornerRadius: reduceMotion ? 0 : rounded, style: .continuous))
        .shadow(
            color: .black.opacity(shadow),
            radius: FilmPosterCarouselMotionSpec.liftShadowBlur / 2,
            x: 0,
            y: FilmPosterCarouselMotionSpec.liftShadowY
        )
        .scaleEffect(CGFloat(scale), anchor: .center)
        .rotationEffect(.degrees(rotation))
        .offset(x: x, y: y)
        .opacity(opacity)
    }

    private func cardContents(
        film: FilmPosterCarouselFilm,
        index: Int,
        size: CGSize,
        page: Double,
        lift: Double,
        swell: Double,
        clock: Double,
        cornerRadius: CGFloat
    ) -> some View {
        let panelShape = UnevenRoundedRectangle(
            cornerRadii: .init(
                topLeading: 0,
                bottomLeading: FilmPosterCarouselLayout.panelRadius,
                bottomTrailing: FilmPosterCarouselLayout.panelRadius,
                topTrailing: 0
            ),
            style: .continuous
        )
        let waiting = filmPosterCarouselClamp(Double(index) - page)

        return ZStack(alignment: .topLeading) {
            film.panel
            footerLayer(film: film, size: size)

            panelShape
                .fill(film.panel)
                .frame(width: size.width, height: FilmPosterCarouselLayout.panelBottom, alignment: .topLeading)
                .shadow(
                    color: .black.opacity(FilmPosterCarouselLayout.panelShadowOpacity),
                    radius: FilmPosterCarouselLayout.panelShadowRadius,
                    x: 0,
                    y: FilmPosterCarouselLayout.panelShadowY
                )

            ZStack(alignment: .topLeading) {
                film.panel
                poster(film: film, index: index, page: page, width: size.width)
                posterFade(color: film.panel, width: size.width)
                sheen(index: index, page: page, lift: lift, width: size.width)
                filmInfo(film: film, swell: swell, width: size.width)
                FilmPosterCarouselChevron(color: film.chevron, clock: clock, reduced: reduceMotion)
            }
            .frame(width: size.width, height: FilmPosterCarouselLayout.panelBottom, alignment: .topLeading)
            .clipShape(panelShape)

            Color.black
                .opacity(reduceMotion ? 0 : FilmPosterCarouselMotionSpec.waitingDim * waiting)
                .allowsHitTesting(false)
        }
        .frame(width: size.width, height: size.height, alignment: .topLeading)
        .clipShape(RoundedRectangle(cornerRadius: reduceMotion ? 0 : cornerRadius, style: .continuous))
    }

    private func poster(
        film: FilmPosterCarouselFilm,
        index: Int,
        page: Double,
        width: CGFloat
    ) -> some View {
        let artProgress = filmPosterCarouselClamp(page - Double(index))
        return Image(uiImage: film.poster)
            .resizable()
            .scaledToFill()
            .frame(width: width, height: FilmPosterCarouselLayout.posterHeight)
            .offset(x: reduceMotion ? 0 : CGFloat(artProgress * Double(width) * FilmPosterCarouselMotionSpec.artLag))
            .clipped()
    }

    private func posterFade(color: Color, width: CGFloat) -> some View {
        let top = FilmPosterCarouselLayout.posterHeight * FilmPosterCarouselLayout.fadeFrom
        let height = FilmPosterCarouselLayout.posterHeight * (1 - FilmPosterCarouselLayout.fadeFrom) + 1
        return fadeGradient(color)
            .frame(width: width, height: height)
            .offset(y: top)
    }

    private func fadeGradient(_ color: Color) -> LinearGradient {
        LinearGradient(
            stops: [
                .init(color: color.opacity(0), location: 0),
                .init(color: color.opacity(0x1F / 255), location: 0.2),
                .init(color: color.opacity(0x66 / 255), location: 0.42),
                .init(color: color.opacity(0xB3 / 255), location: 0.62),
                .init(color: color.opacity(0xE6 / 255), location: 0.82),
                .init(color: color, location: 1),
            ],
            startPoint: .top,
            endPoint: .bottom
        )
    }

    private func filmInfo(
        film: FilmPosterCarouselFilm,
        swell: Double,
        width: CGFloat
    ) -> some View {
        ZStack(alignment: .topLeading) {
            line(top: textTop(FilmPosterCarouselLayout.titleBaseline, FilmPosterCarouselLayout.titleSize)) {
                Text(film.title)
                    .font(.system(size: FilmPosterCarouselLayout.titleSize, weight: .bold))
                    .tracking(FilmPosterCarouselLayout.titleTracking)
                    .lineLimit(1)
                    .foregroundStyle(.white)
                    .frame(height: FilmPosterCarouselLayout.titleSize * 1.2, alignment: .top)
            }
            .offset(x: FilmPosterCarouselLayout.titleX)

            line(top: textTop(FilmPosterCarouselLayout.metaBaseline, FilmPosterCarouselLayout.metaSize)) {
                Text("\(film.year)  ·  \(film.director)")
                    .font(.system(size: FilmPosterCarouselLayout.metaSize, weight: .semibold))
                    .tracking(FilmPosterCarouselLayout.metaTracking)
                    .foregroundStyle(film.meta)
                    .frame(height: FilmPosterCarouselLayout.metaSize * 1.2, alignment: .top)
            }
            .offset(x: FilmPosterCarouselLayout.metaX)

            line(top: textTop(FilmPosterCarouselLayout.durationBaseline, FilmPosterCarouselLayout.durationSize)) {
                Text(film.duration)
                    .font(.system(size: FilmPosterCarouselLayout.durationSize, weight: .bold))
                    .tracking(FilmPosterCarouselLayout.durationTracking)
                    .foregroundStyle(.white)
                    .frame(height: FilmPosterCarouselLayout.durationSize * 1.2, alignment: .top)
            }
            .offset(x: FilmPosterCarouselLayout.durationX)

            line(
                top: FilmPosterCarouselLayout.starsCenterY - FilmPosterCarouselLayout.starSize / 2
            ) {
                HStack(spacing: FilmPosterCarouselLayout.starPitch - FilmPosterCarouselLayout.starSize) {
                    ForEach(0..<5, id: \.self) { star in
                        FilmPosterCarouselStar(fill: min(max(film.rating - Double(star), 0), 1))
                            .frame(width: FilmPosterCarouselLayout.starSize, height: FilmPosterCarouselLayout.starSize)
                            .scaleEffect(reduceMotion ? 1 : filmPosterCarouselStarScale(swell: swell, index: star))
                    }
                }
                .shadow(color: .black.opacity(FilmPosterCarouselLayout.starHalo), radius: 1.5)
                .accessibilityElement(children: .ignore)
                .accessibilityLabel("\(film.rating.formatted()) out of 5 stars")
            }
            .offset(x: FilmPosterCarouselLayout.starsCenterX - FilmPosterCarouselLayout.starSize / 2)

            line(top: FilmPosterCarouselLayout.tagsTop) {
                HStack(spacing: FilmPosterCarouselLayout.tagGap) {
                    ForEach(film.tags, id: \.self) { tag in
                        Text(tag)
                            .font(.system(size: FilmPosterCarouselLayout.tagSize, weight: .bold))
                            .tracking(FilmPosterCarouselLayout.tagTracking)
                            .foregroundStyle(.white)
                            .padding(.horizontal, FilmPosterCarouselLayout.tagPaddingX)
                            .frame(height: FilmPosterCarouselLayout.tagHeight)
                            .background(film.chip, in: Capsule())
                    }
                }
            }
            .offset(x: FilmPosterCarouselLayout.tagsX)
        }
        .frame(width: width, height: FilmPosterCarouselLayout.panelBottom, alignment: .topLeading)
        .allowsHitTesting(false)
    }

    private func line<Content: View>(
        top: CGFloat,
        @ViewBuilder content: () -> Content
    ) -> some View {
        return content()
            .fixedSize()
            .offset(y: top)
    }

    private func footerLayer(film: FilmPosterCarouselFilm, size: CGSize) -> some View {
        let top = FilmPosterCarouselLayout.footerTop
        return ZStack(alignment: .topLeading) {
            film.footer
            Text("WATCH NOW")
                .font(.system(size: FilmPosterCarouselLayout.footerSize, weight: .heavy))
                .tracking(FilmPosterCarouselLayout.footerTracking)
                .foregroundStyle(film.footerInk)
                .opacity(footerPressed ? 0.65 : 1)
                .frame(height: FilmPosterCarouselLayout.footerSize * 1.2, alignment: .top)
                .fixedSize()
                .position(
                    x: size.width / 2,
                    y: textTop(FilmPosterCarouselLayout.footerBaseline, FilmPosterCarouselLayout.footerSize) - top + FilmPosterCarouselLayout.footerSize * 0.6
                )
        }
        .frame(width: size.width, height: max(0, size.height - top), alignment: .topLeading)
        .offset(y: top)
    }

    private func footerTouchOverlay(size: CGSize) -> some View {
        Button(action: {}) {
            Color.clear.contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .frame(
            width: size.width,
            height: max(0, size.height - FilmPosterCarouselLayout.panelBottom)
        )
        .accessibilityLabel("Watch now")
        .simultaneousGesture(
            DragGesture(minimumDistance: 0)
                .onChanged { _ in
                    takeOver(at: .now)
                    beginManualInteraction()
                    withAnimation(.easeInOut(duration: 0.12)) { footerPressed = true }
                }
                .onEnded { _ in
                    withAnimation(.easeInOut(duration: 0.18)) { footerPressed = false }
                    endManualInteraction()
                }
        )
        .offset(y: FilmPosterCarouselLayout.panelBottom)
    }

    private func closeButton(size: CGSize) -> some View {
        Button(action: {}) {
            ZStack {
                Color(red: 0xF5 / 255, green: 0xF5 / 255, blue: 0xE9 / 255)
                FilmPosterCarouselCloseShape()
                    .stroke(
                        Color(red: 0x0A / 255, green: 0x09 / 255, blue: 0x0D / 255),
                        style: .init(
                            lineWidth: 3 * FilmPosterCarouselLayout.closeIcon / 24,
                            lineCap: .round
                        )
                    )
                    .frame(width: FilmPosterCarouselLayout.closeIcon, height: FilmPosterCarouselLayout.closeIcon)
            }
            .frame(
                width: FilmPosterCarouselLayout.closeSize,
                height: FilmPosterCarouselLayout.closeSize
            )
            .clipShape(RoundedRectangle(cornerRadius: FilmPosterCarouselLayout.closeRadius))
            .padding(2)
        }
        .buttonStyle(FilmPosterCarouselCloseButtonStyle())
        .accessibilityLabel("Close")
        .simultaneousGesture(buttonTouchGesture)
        .offset(
            x: size.width - FilmPosterCarouselLayout.closeRight - FilmPosterCarouselLayout.closeSize - 2,
            y: FilmPosterCarouselLayout.closeTop - 2
        )
    }

    private var buttonTouchGesture: some Gesture {
        DragGesture(minimumDistance: 0)
            .onChanged { _ in
                takeOver(at: .now)
                beginManualInteraction()
            }
            .onEnded { _ in endManualInteraction() }
    }

    private func beginDrag(width: CGFloat) {
        let now = Date()
        takeOver(at: now)
        dragStart = page.value(at: now)
        dragActivated = false
        page.jump(to: dragStart)
        if !reduceMotion {
            lift.animate(
                to: 1,
                at: now,
                duration: FilmPosterCarouselMotionSpec.pickResponse,
                curve: .spring(dampingRatio: FilmPosterCarouselMotionSpec.pickDamping, velocity: 0)
            )
        }
        keepRendering(for: 1.5)
        beginManualInteraction()
    }

    private func updateDrag(translation: Double, width: CGFloat) {
        guard width > 0 else { return }
        guard abs(translation) >= 10 else { return }
        dragActivated = true
        let raw = dragStart - translation / Double(width)
        let last = Double(filmPosterCarouselFilms.count - 1)
        let over = raw < 0 ? raw : raw > last ? raw - last : 0
        page.jump(to: raw - over + over * FilmPosterCarouselMotionSpec.rubberBand)
    }

    private func endDrag(translation: Double, velocity: Double, width: CGFloat) {
        guard width > 0 else { return }
        guard dragActivated else {
            lift.animate(
                to: 0,
                at: Date(),
                duration: FilmPosterCarouselMotionSpec.downResponse,
                curve: .spring(dampingRatio: FilmPosterCarouselMotionSpec.downDamping, velocity: 0)
            )
            endManualInteraction()
            return
        }
        let pageVelocity = -velocity / Double(width)
        let flick = abs(velocity) / 1_000 > FilmPosterCarouselMotionSpec.flickThreshold
            ? (pageVelocity < 0 ? -1.0 : 1.0)
            : 0
        let current = page.value(at: Date())
        let target = flick != 0
            ? (dragStart.rounded() + flick)
            : (current + pageVelocity * FilmPosterCarouselMotionSpec.velocityProjection).rounded()
        goTo(target, velocity: pageVelocity, scripted: false)
        endManualInteraction()
    }

    private func beginManualInteraction() {
        guard !contactActive else { return }
        contactActive = true
        manual = true
        reducedScriptGeneration += 1
        interactionGeneration += 1
    }

    private func endManualInteraction() {
        contactActive = false
        guard autoPlay else { return }
        interactionGeneration += 1
    }

    private func goTo(_ target: Double, velocity: Double = 0, scripted: Bool = true) {
        let last = Double(filmPosterCarouselFilms.count - 1)
        let clamped = min(max(target, 0), last)
        let now = Date()
        let changed = clamped != dragStart.rounded()
        if !scripted, changed {
            selectionHaptic.selectionChanged()
            selectionHaptic.prepare()
        }
        if reduceMotion {
            page.animate(
                to: clamped,
                at: now,
                duration: FilmPosterCarouselMotionSpec.reducedDuration,
                curve: .easeInOut
            )
        } else {
            page.animate(
                to: clamped,
                at: now,
                duration: FilmPosterCarouselMotionSpec.pageResponse,
                curve: .spring(
                    dampingRatio: FilmPosterCarouselMotionSpec.pageDamping,
                    velocity: velocity
                )
            )
            lift.animate(
                to: 0,
                at: now,
                duration: FilmPosterCarouselMotionSpec.downResponse,
                delay: 0.12,
                curve: .spring(dampingRatio: FilmPosterCarouselMotionSpec.downDamping, velocity: 0)
            )
        }
        if !reduceMotion, changed {
            let targetSwell = clamped < 0.5 ? 0 : 1
            if targetSwell == 0 {
                swell0.jump(to: 0)
                swell0.animate(to: 1, at: now, duration: FilmPosterCarouselMotionSpec.starsTotal, delay: 0.45, curve: .linear)
            } else {
                swell1.jump(to: 0)
                swell1.animate(to: 1, at: now, duration: FilmPosterCarouselMotionSpec.starsTotal, delay: 0.45, curve: .linear)
            }
        }
        keepRendering(for: reduceMotion ? 0.25 : 1.5)
    }

    private func resumeToRest() {
        scripted = false
        page.animate(
            to: 0,
            at: Date(),
            duration: FilmPosterCarouselMotionSpec.resumeDuration,
            curve: .resume
        )
        lift.animate(to: 0, at: .now, duration: FilmPosterCarouselMotionSpec.resumeDuration, curve: .easeInOut)
        keepRendering(for: 0.35)
    }

    private func resumeAfterInteraction() async {
        guard autoPlay, !reduceMotion, manual, !contactActive, freezeAt == nil else { return }
        do {
            try await Task.sleep(for: .seconds(FilmPosterCarouselMotionSpec.idleResume))
            resumeToRest()
            try await Task.sleep(for: .seconds(FilmPosterCarouselMotionSpec.resumeRestartDelay))
        } catch {
            return
        }
        guard !reduceMotion else { return }
        manual = false
        startScript(at: .now)
    }

    private func startScript(at date: Date) {
        guard !reduceMotion else {
            holdReducedMotion(at: date)
            return
        }
        loopStartedAt = date
        scripted = true
        rendering = true
    }

    private func holdReducedMotion(at date: Date) {
        let selected = min(
            Double(filmPosterCarouselFilms.count - 1),
            max(0, renderValues(at: date).page.rounded())
        )
        scripted = false
        page.jump(to: selected)
        lift.jump(to: 0)
        swell0.jump(to: 0)
        swell1.jump(to: 0)
        rendering = false
    }

    private func keepRendering(for duration: Double) {
        rendering = true
        renderStopDelay = duration
        renderGeneration += 1
    }

    private func takeOver(at date: Date) {
        guard scripted else { return }
        let values = renderValues(at: date)
        scripted = false
        page.jump(to: values.page)
        lift.jump(to: values.lift)
        swell0.jump(to: values.swells[0])
        swell1.jump(to: values.swells[1])
    }

    private func renderValues(at date: Date) -> (page: Double, lift: Double, swells: [Double], clock: Double) {
        if let freezeAt {
            let clock = (freezeAt / 1000).truncatingRemainder(dividingBy: Self.loop)
            let pose = filmPosterCarouselPose(at: clock)
            return (pose.page, reduceMotion ? 0 : pose.lift, reduceMotion ? [0, 0] : pose.swell, clock)
        }
        let elapsed = max(0, date.timeIntervalSince(loopStartedAt))
        let clock = elapsed.truncatingRemainder(dividingBy: FilmPosterCarouselMotionSpec.loop)
        if scripted {
            let pose = filmPosterCarouselPose(at: clock)
            return (
                pose.page,
                reduceMotion ? 0 : pose.lift,
                reduceMotion ? [0, 0] : pose.swell,
                clock
            )
        }
        return (
            page.value(at: date),
            reduceMotion ? 0 : lift.value(at: date),
            reduceMotion ? [0, 0] : [swell0.value(at: date), swell1.value(at: date)],
            clock
        )
    }

    private func room(size: CGSize, page: Double, lift: Double) -> some View {
        let opacity = reduceMotion ? 0 : filmPosterCarouselClamp(lift * 4)
        return ZStack {
            ForEach(Array(filmPosterCarouselFilms.enumerated()), id: \.element.id) { index, film in
                Image(uiImage: film.poster)
                    .resizable()
                    .scaledToFill()
                    .frame(width: size.width, height: size.height)
                    .scaleEffect(1.2)
                    .blur(radius: FilmPosterCarouselMotionSpec.roomBlur)
                    .opacity(1 - filmPosterCarouselClamp(abs(Double(index) - page)))
            }
            Color.black.opacity(FilmPosterCarouselMotionSpec.roomDim)
        }
        .frame(width: size.width, height: size.height)
        .clipped()
        .opacity(opacity)
        .allowsHitTesting(false)
    }

    private func sheen(index: Int, page: Double, lift: Double, width: CGFloat) -> some View {
        let distance = Double(index) - page
        let progress = distance <= 0
            ? filmPosterCarouselClamp(-distance)
            : 1 - filmPosterCarouselClamp(distance)
        let bandWidth = width * FilmPosterCarouselMotionSpec.sheenWidth
        let opacity = reduceMotion
            ? 0
            : filmPosterCarouselClamp(lift) * sin(.pi * progress)
        return LinearGradient(
            colors: [.white.opacity(0), .white.opacity(FilmPosterCarouselMotionSpec.sheenAlpha), .white.opacity(0)],
            startPoint: .leading,
            endPoint: .trailing
        )
        .frame(width: bandWidth, height: FilmPosterCarouselLayout.posterHeight * 1.3)
        .rotationEffect(.degrees(FilmPosterCarouselMotionSpec.sheenSlant))
        .offset(
            x: width * CGFloat(1 - FilmPosterCarouselMotionSpec.sheenTravel * progress) - bandWidth / 2,
            y: -FilmPosterCarouselLayout.posterHeight * 0.15
        )
        .opacity(opacity)
        .allowsHitTesting(false)
    }

    private func textTop(_ baseline: CGFloat, _ size: CGFloat) -> CGFloat {
        baseline - size * FilmPosterCarouselLayout.baselineRatio
    }
}

private struct FilmPosterCarouselCloseButtonStyle: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .scaleEffect(configuration.isPressed ? 0.97 : 1)
            .animation(.easeInOut(duration: 0.12), value: configuration.isPressed)
    }
}

private struct FilmPosterCarouselStar: View {
    let fill: Double

    var body: some View {
        ZStack(alignment: .leading) {
            FilmPosterCarouselStarShape()
                .fill(Color(red: 0x8A / 255, green: 0x87 / 255, blue: 0x78 / 255))
                .overlay {
                    FilmPosterCarouselStarShape()
                        .stroke(
                            Color(red: 0x8A / 255, green: 0x87 / 255, blue: 0x78 / 255),
                            lineWidth: 1.4 * FilmPosterCarouselLayout.starSize / 24
                        )
                }
            FilmPosterCarouselStarShape()
                .fill(Color(red: 0xEB / 255, green: 0xD4 / 255, blue: 0x6E / 255))
                .overlay {
                    FilmPosterCarouselStarShape()
                        .stroke(
                            Color(red: 0xEB / 255, green: 0xD4 / 255, blue: 0x6E / 255),
                            lineWidth: 1.8 * FilmPosterCarouselLayout.starSize / 24
                        )
                }
                .mask(alignment: .leading) {
                    Rectangle()
                        .frame(width: FilmPosterCarouselLayout.starSize * CGFloat(fill))
                        .frame(maxWidth: .infinity, alignment: .leading)
                }
        }
    }
}

private struct FilmPosterCarouselStarShape: Shape {
    func path(in rect: CGRect) -> Path {
        var path = Path()
        let center = CGPoint(x: rect.midX, y: rect.minY + rect.height * 13 / 24)
        for index in 0..<10 {
            let radius = min(rect.width, rect.height) * 11.5 / 24 * (index.isMultiple(of: 2) ? 1 : FilmPosterCarouselLayout.starInnerRadius)
            let angle = -.pi / 2 + Double(index) * .pi / 5
            let point = CGPoint(
                x: center.x + radius * CGFloat(cos(angle)),
                y: center.y + radius * CGFloat(sin(angle))
            )
            if index == 0 { path.move(to: point) } else { path.addLine(to: point) }
        }
        path.closeSubpath()
        return path
    }
}

private struct FilmPosterCarouselChevronShape: Shape {
    func path(in rect: CGRect) -> Path {
        let inset = FilmPosterCarouselLayout.chevronStroke / 2
        var path = Path()
        path.move(to: CGPoint(x: inset, y: rect.height - inset))
        path.addLine(to: CGPoint(x: rect.width / 2, y: inset))
        path.addLine(to: CGPoint(x: rect.width - inset, y: rect.height - inset))
        return path
    }
}

private struct FilmPosterCarouselChevron: View {
    let color: Color
    let clock: Double
    let reduced: Bool

    var body: some View {
        let phase = clock.truncatingRemainder(
            dividingBy: FilmPosterCarouselMotionSpec.bobDuration
        ) / FilmPosterCarouselMotionSpec.bobDuration
        let bob = reduced ? CGFloat.zero : CGFloat(-3 * pow(sin(phase * .pi), 2))
        FilmPosterCarouselChevronShape()
            .stroke(
                color,
                style: .init(
                    lineWidth: FilmPosterCarouselLayout.chevronStroke,
                    lineCap: .round,
                    lineJoin: .round
                )
            )
            .frame(
                width: FilmPosterCarouselLayout.chevronWidth,
                height: FilmPosterCarouselLayout.chevronHeight
            )
            .offset(
                x: FilmPosterCarouselLayout.chevronCenterX - FilmPosterCarouselLayout.chevronWidth / 2,
                y: FilmPosterCarouselLayout.chevronTop + bob
            )
            .allowsHitTesting(false)
    }
}

private struct FilmPosterCarouselCloseShape: Shape {
    func path(in rect: CGRect) -> Path {
        let scaleX = rect.width / 24
        let scaleY = rect.height / 24
        var path = Path()
        path.move(to: CGPoint(x: 3 * scaleX, y: 3 * scaleY))
        path.addLine(to: CGPoint(x: 21 * scaleX, y: 21 * scaleY))
        path.move(to: CGPoint(x: 21 * scaleX, y: 3 * scaleY))
        path.addLine(to: CGPoint(x: 3 * scaleX, y: 21 * scaleY))
        return path
    }
}

private struct FilmPosterCarouselTouchView: UIViewRepresentable {
    let onBegan: () -> Void
    let onChanged: (Double) -> Void
    let onEnded: (Double, Double) -> Void

    func makeUIView(context: Context) -> FilmPosterCarouselTouchSurface {
        let view = FilmPosterCarouselTouchSurface()
        view.backgroundColor = .clear
        view.isOpaque = false
        updateUIView(view, context: context)
        return view
    }

    func updateUIView(_ view: FilmPosterCarouselTouchSurface, context: Context) {
        view.onBegan = onBegan
        view.onChanged = onChanged
        view.onEnded = onEnded
    }
}

private final class FilmPosterCarouselTouchSurface: UIView, UIGestureRecognizerDelegate {
    var onBegan: (() -> Void)?
    var onChanged: ((Double) -> Void)?
    var onEnded: ((Double, Double) -> Void)?

    private var startX = 0.0
    private var previousX = 0.0
    private var previousTime = 0.0
    private var velocityX = 0.0

    override init(frame: CGRect) {
        super.init(frame: frame)
        let recognizer = UILongPressGestureRecognizer(target: self, action: #selector(handle(_:)))
        recognizer.minimumPressDuration = 0
        recognizer.allowableMovement = .greatestFiniteMagnitude
        recognizer.numberOfTouchesRequired = 1
        recognizer.cancelsTouchesInView = false
        recognizer.delegate = self
        addGestureRecognizer(recognizer)
        isAccessibilityElement = false
    }

    required init?(coder: NSCoder) {
        fatalError("init(coder:) has not been implemented")
    }

    func gestureRecognizer(
        _ gestureRecognizer: UIGestureRecognizer,
        shouldRecognizeSimultaneouslyWith otherGestureRecognizer: UIGestureRecognizer
    ) -> Bool {
        true
    }

    @objc private func handle(_ recognizer: UILongPressGestureRecognizer) {
        let point = recognizer.location(in: self)
        let time = CACurrentMediaTime()
        switch recognizer.state {
        case .began:
            startX = point.x
            previousX = point.x
            previousTime = time
            velocityX = 0
            onBegan?()
        case .changed:
            let elapsed = max(0.001, time - previousTime)
            velocityX = (point.x - previousX) / elapsed
            previousX = point.x
            previousTime = time
            onChanged?(point.x - startX)
        case .ended, .cancelled, .failed:
            onEnded?(point.x - startX, velocityX)
        default:
            break
        }
    }
}

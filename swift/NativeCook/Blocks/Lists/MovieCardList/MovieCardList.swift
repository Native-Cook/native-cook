import CoreText
import QuartzCore
import SwiftUI
import UIKit

/// A faithful SwiftUI port of the approved Movie Card List reference.
struct MovieCardList: View {
    static let loop: Double = 15.8

    let autoPlay: Bool

    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    init(autoPlay: Bool = false) {
        self.autoPlay = autoPlay
        MovieCardListAssets.register()
    }

    var body: some View {
        GeometryReader { proxy in
            let scale = min(
                proxy.size.width / MovieCardListSpec.canvas.width,
                proxy.size.height / MovieCardListSpec.canvas.height
            )
            MovieCardListScene(
                autoPlay: autoPlay,
                reduced: reduceMotion,
                scale: scale,
                availableHeight: proxy.size.height / max(scale, 0.001)
            )
            .frame(width: MovieCardListSpec.canvas.width, height: MovieCardListSpec.canvas.height, alignment: .topLeading)
            // The reference canvas is centered after scaling; top-leading scaling preserves an unscaled centering offset.
            .scaleEffect(scale, anchor: .center)
            .frame(width: proxy.size.width, height: proxy.size.height, alignment: .center)
        }
        .background(MovieCardListPalette.paper.ignoresSafeArea())
        .ignoresSafeArea()
        .preferredColorScheme(.dark)
    }
}

private enum MovieCardListPalette {
    static let paper = color(0xFFFFFF)
    static let ink = color(0x151515)
    static let inkSoft = color(0x4A4A4A)
    static let inkMute = color(0x9A9A9A)
    static let chip = color(0xC9C9C9)
    static let star = color(0xE8423F)
    static let starOff = color(0xC4C4C4)
    static let button = color(0x262626)
    static let night = color(0x000000)
    static let seatFree = color(0xE9E9EE)
    static let seatTaken = color(0x3A3A3E)
    static let seatPicked = color(0xE8262B)
    static let red = color(0xE8262B)
    static let redHot = color(0xF2552F)
    static let legend = color(0xB9B9BE)

    static func color(_ hex: UInt32, opacity: Double = 1) -> Color {
        Color(
            .sRGB,
            red: Double((hex >> 16) & 0xFF) / 255,
            green: Double((hex >> 8) & 0xFF) / 255,
            blue: Double(hex & 0xFF) / 255,
            opacity: opacity
        )
    }
}

private enum MovieCardListFont {
    static func poppins(_ size: CGFloat, weight: MovieCardListFontWeight = .regular) -> Font {
        .custom(weight.name, size: size)
    }

    static func anton(_ size: CGFloat) -> Font { .custom("Anton-Regular", size: size) }
    static func serif(_ size: CGFloat) -> Font { .custom("DMSerifDisplay-Regular", size: size) }
}

private enum MovieCardListFontWeight {
    case regular, medium, semibold

    var name: String {
        switch self {
        case .regular: "Poppins-Regular"
        case .medium: "Poppins-Medium"
        case .semibold: "Poppins-SemiBold"
        }
    }
}

private enum MovieCardListSpec {
    static let canvas = CGSize(width: 402, height: 874)
    static let startPage = 1
    static let idleResume = 3.5
    static let cardWidth: CGFloat = 291
    static let cardTop: CGFloat = 245
    static let cardHeight: CGFloat = 640
    static let cardRadius: CGFloat = 43
    static let posterInset: CGFloat = 35
    static let posterTop: CGFloat = 27
    static let posterHeight: CGFloat = 326
    static let posterRadius: CGFloat = 26
    static let pitch: CGFloat = 287
    static let sideScale: CGFloat = 0.85
    static let sideWash: CGFloat = 0.85
    static let backdropHeight: CGFloat = 600
    static let titleY: CGFloat = 639
    static let chipsY: CGFloat = 673.3
    static let ratingY: CGFloat = 703.4
    static let dotsY: CGFloat = 733.8
    static let cardButtonY: CGFloat = 800.1
    static let cardButtonWidth: CGFloat = 254.7
    static let cardButtonHeight: CGFloat = 58.8
    static let sheetTop: CGFloat = 257
    static let sheetInset: CGFloat = 10
    static let sheetRadius: CGFloat = 28
    static let sheetContentX: CGFloat = 33
    static let detailPosterTop: CGFloat = 79
    static let detailPosterScale: CGFloat = 0.94
    static let detailSideScale: CGFloat = 0.65
    static let detailPitch: CGFloat = 0.565
    static let detailTitleY: CGFloat = 87
    static let detailChipsY: CGFloat = 122
    static let detailRatingY: CGFloat = 152
    static let detailDirectorY: CGFloat = 174
    static let actorsLabelY: CGFloat = 240
    static let actorsTop: CGFloat = 258
    static let actorWidth: CGFloat = 100
    static let actorHeight: CGFloat = 110
    static let actorGap: CGFloat = 18
    static let introLabelY: CGFloat = 445
    static let introTop: CGFloat = 466
    static let detailButtonY: CGFloat = 521
    static let detailButtonWidth: CGFloat = 300
    static let detailButtonHeight: CGFloat = 52
    static let screenTop: CGFloat = 112
    static let screenHeight: CGFloat = 128
    static let screenInset: CGFloat = 26
    static let gridTop: CGFloat = 313.5
    static let seat: CGFloat = 20
    static let seatPitchX: CGFloat = 26.3
    static let seatPitchY: CGFloat = 28
    static let aisle: CGFloat = 32.4
    static let legendY: CGFloat = 622
    static let dateY: CGFloat = 690
    static let payY: CGFloat = 774
    static let payWidth: CGFloat = 300
    static let payHeight: CGFloat = 52
    static let closeX: CGFloat = 21.8
    static let closeY: CGFloat = 60.8
    static let closeSize: CGFloat = 24.4
    static let targetX: CGFloat = 377.7
    static let targetY: CGFloat = 61
    static let targetSize: CGFloat = 26.2
    static let loopMilliseconds = 15_800.0
    static let coverDelayMilliseconds = 40.0 // Preserved from RN inventory; the RN runtime starts COVER immediately.
    static let openMilliseconds = 680.0
    static let coverMilliseconds = 560.0
    static let buildDelayMilliseconds = 620.0
    static let buildRowStaggerMilliseconds = 70.0
    static let buildRowMilliseconds = 300.0
    static let buildChromeAtMilliseconds = 600.0
    static let buildChromeMilliseconds = 360.0
    static let footageInAtMilliseconds = 600.0
    static let footageInMilliseconds = 400.0
    static let footageOutAtMilliseconds = 1_820.0
    static let footageOutMilliseconds = 420.0
    static let footageDriftMilliseconds = 2_200.0
    static let payShrinkMilliseconds = 340.0
    static let payBlackAtMilliseconds = 260.0
    static let payBlackMilliseconds = 240.0
    static let payFaceAtMilliseconds = 520.0
    static let payFaceMilliseconds = 560.0
    static let paySpinAtMilliseconds = 1_100.0
    static let paySpinMilliseconds = 420.0
    static let payCheckAtMilliseconds = 1_520.0
    static let payCheckMilliseconds = 380.0
    static let seatClockMilliseconds = 6_000.0
}

private enum MovieCardListStage: Equatable {
    case cards, detail, seats, pay
}

private struct MovieCardListMovie: Identifiable {
    struct Cast: Identifiable {
        let id: String
        let name: String
        let image: String
    }

    enum Tone { case buds, jester, grift }

    let id: String
    let title: String
    let genres: [String]
    let rating: Double
    let stars: Int
    let director: String
    let intro: String
    let poster: String
    let backdrop: String
    let cast: [Cast]
    let tone: Tone
    let backdropColor: Color
}

private let movieCardListMovies: [MovieCardListMovie] = [
    MovieCardListMovie(
        id: "buds", title: "Best Buds", genres: ["Comedy", "Family", "Teen"], rating: 8.1, stars: 4,
        director: "Director / Sam Whitlow",
        intro: "Three sixth-graders skip school to replace a drone they broke, and their one-day trip across town turns into the wildest weekend of their lives.",
        poster: "MovieCardList-poster-buds.jpg", backdrop: "MovieCardList-backdrop-buds.jpg",
        cast: [.init(id: "milo", name: "Milo Hart", image: "MovieCardList-cast-buds-1.jpg"), .init(id: "ezra", name: "Ezra Quinn", image: "MovieCardList-cast-buds-2.jpg"), .init(id: "dean", name: "Dean Porter", image: "MovieCardList-cast-buds-3.jpg")],
        tone: .buds, backdropColor: MovieCardListPalette.color(0xE3221C)
    ),
    MovieCardListMovie(
        id: "jester", title: "Drift", genres: ["Action", "Drama", "History"], rating: 9.0, stars: 4,
        director: "Director / Nora Kessler",
        intro: "The film “Drift” follows a night-shift ferry pilot who finds a stranger’s phone on board and is pulled into the city’s underworld. Released by Northlight Pictures this October.",
        poster: "MovieCardList-poster-jester.jpg", backdrop: "MovieCardList-backdrop-jester.jpg",
        cast: [.init(id: "mason", name: "Mason Vale", image: "MovieCardList-cast-jester-1.jpg"), .init(id: "robert", name: "Robert Delane", image: "MovieCardList-cast-jester-2.jpg"), .init(id: "zara", name: "Zara Bennett", image: "MovieCardList-cast-jester-3.jpg")],
        tone: .jester, backdropColor: MovieCardListPalette.color(0x050807)
    ),
    MovieCardListMovie(
        id: "grift", title: "The Grift", genres: ["Comedy", "Crime", "Romance"], rating: 7.6, stars: 4,
        director: "Director / Lena Ortiz",
        intro: "Two rival con artists on the Riviera team up to fleece a tech millionaire, and soon discover that the mark is playing them both.",
        poster: "MovieCardList-poster-grift.jpg", backdrop: "MovieCardList-backdrop-grift.jpg",
        cast: [.init(id: "ava", name: "Ava Sinclair", image: "MovieCardList-cast-grift-1.jpg"), .init(id: "rose", name: "Rose Hadley", image: "MovieCardList-cast-grift-2.jpg"), .init(id: "victor", name: "Victor Lang", image: "MovieCardList-cast-grift-3.jpg")],
        tone: .grift, backdropColor: MovieCardListPalette.color(0xC9A23A)
    ),
]

private struct MovieCardListFrame {
    var page = CGFloat(MovieCardListSpec.startPage)
    var stage = MovieCardListStage.cards
    var open: CGFloat = 0
    var cover: CGFloat = 0
    var castX: CGFloat = 0
    var seatClock: Double = 0
    var payClock: Double = 0
    var picked: Set<String> = []
    var resetFade: CGFloat = 0
    var veil: CGFloat = 0
    var night: CGFloat = 0
}

private func movieCardListClamp(_ value: CGFloat, _ lower: CGFloat = 0, _ upper: CGFloat = 1) -> CGFloat {
    min(max(value, lower), upper)
}

private func movieCardListRamp(_ value: Double, at: Double, duration: Double) -> CGFloat {
    movieCardListClamp(CGFloat((value - at) / duration))
}

private func movieCardListBezier(_ progress: CGFloat, _ x1: CGFloat, _ y1: CGFloat, _ x2: CGFloat, _ y2: CGFloat) -> CGFloat {
    let target = movieCardListClamp(progress)
    func sample(_ t: CGFloat, _ a: CGFloat, _ b: CGFloat) -> CGFloat {
        let inverse = 1 - t
        return 3 * inverse * inverse * t * a + 3 * inverse * t * t * b + t * t * t
    }
    var low: CGFloat = 0
    var high: CGFloat = 1
    for _ in 0..<12 {
        let middle = (low + high) / 2
        if sample(middle, x1, x2) < target { low = middle } else { high = middle }
    }
    return sample((low + high) / 2, y1, y2)
}

private func movieCardListSpring(_ progress: CGFloat, damping: CGFloat, stiffness: CGFloat, mass: CGFloat) -> CGFloat {
    let p = movieCardListClamp(progress)
    let frequency = sqrt(stiffness / mass)
    let dampingRatio = damping / (2 * sqrt(stiffness * mass))
    if dampingRatio >= 1 { return 1 - exp(-frequency * p * 0.38) }
    let damped = frequency * sqrt(1 - dampingRatio * dampingRatio)
    let time = p * 0.38
    let result = 1 - exp(-dampingRatio * frequency * time) * (cos(damped * time) + dampingRatio * frequency / damped * sin(damped * time))
    return result
}

private func movieCardListScriptFrame(_ milliseconds: Double) -> MovieCardListFrame {
    let t = milliseconds.truncatingRemainder(dividingBy: MovieCardListSpec.loopMilliseconds)
    var frame = MovieCardListFrame()
    let pages: [(Double, CGFloat)] = [(780, 0), (1480, 1), (1940, 0), (2360, 1), (2860, 2), (4100, 1)]
    var prior = CGFloat(MovieCardListSpec.startPage)
    for (at, target) in pages {
        guard t >= at else { break }
        let progress = movieCardListClamp(CGFloat((t - at) / 380))
        frame.page = prior + (target - prior) * movieCardListSpring(progress, damping: 22, stiffness: 210, mass: 0.9)
        if progress >= 1 { frame.page = target }
        prior = target
    }
    if t >= 4760 {
        frame.stage = .detail
        frame.open = movieCardListBezier(movieCardListRamp(t, at: 4760, duration: 680), 0.32, 0.72, 0, 1)
    }
    if t >= 6050 {
        frame.castX = -72 * movieCardListSpring(movieCardListRamp(t, at: 6050, duration: 600), damping: 24, stiffness: 140, mass: 1)
    }
    if t >= 6950 {
        frame.castX = -72 * (1 - movieCardListSpring(movieCardListRamp(t, at: 6950, duration: 600), damping: 24, stiffness: 140, mass: 1))
    }
    if t >= 7650 {
        frame.stage = .seats
        frame.open = 1
        frame.cover = movieCardListBezier(movieCardListRamp(t, at: 7650, duration: 560), 0.55, 0, 0.35, 1)
        frame.seatClock = min(MovieCardListSpec.seatClockMilliseconds, t - 7650)
    }
    if t >= 11300 { frame.picked.insert("7-6") }
    if t >= 11900 { frame.picked.insert("7-7") }
    if t >= 13100 {
        frame.stage = .pay
        frame.payClock = min(2_500, t - 13100)
    }
    if t >= 15100 {
        frame.stage = .cards
        frame.open = 0
        frame.cover = 0
        frame.page = CGFloat(MovieCardListSpec.startPage)
        frame.picked = []
        frame.resetFade = 1 - movieCardListRamp(t, at: 15100, duration: 420)
    }
    return frame
}

private func movieCardListReducedScriptFrame(_ milliseconds: Double) -> MovieCardListFrame {
    let t = milliseconds.truncatingRemainder(dividingBy: MovieCardListSpec.loopMilliseconds)
    var frame = movieCardListScriptFrame(milliseconds)
    let pageCues: [(Double, CGFloat)] = [(780, 0), (1480, 1), (1940, 0), (2360, 1), (2860, 2), (4100, 1)]
    var page = CGFloat(MovieCardListSpec.startPage)
    for (at, target) in pageCues {
        guard t >= at else { break }
        if t >= at + 100 { page = target }
        let fadeIn = movieCardListRamp(t, at: at, duration: 100)
        let fadeOut = 1 - movieCardListRamp(t, at: at + 100, duration: 100)
        frame.veil = max(frame.veil, min(fadeIn, fadeOut))
    }
    frame.page = page
    if t >= 4760 {
        frame.open = movieCardListBezier(movieCardListRamp(t, at: 4760, duration: 200), 0.32, 0.72, 0, 1)
    }
    if t >= 7650 {
        frame.cover = movieCardListBezier(movieCardListRamp(t, at: 7650, duration: 200), 0.55, 0, 0.35, 1)
    }
    return frame
}

private struct MovieCardListScene: View {
    let autoPlay: Bool
    let reduced: Bool
    let scale: CGFloat
    let availableHeight: CGFloat

    @State private var isScripted = true
    @State private var scriptStart = Date()
    @State private var manualStage = MovieCardListStage.cards
    @State private var manualPage = CGFloat(MovieCardListSpec.startPage)
    @State private var manualOpen: CGFloat = 0
    @State private var manualCover: CGFloat = 0
    @State private var manualCastX: CGFloat = 0
    @State private var manualSeatClock: Double = 0
    @State private var manualPayClock: Double = 0
    @State private var manualPicked: Set<String> = []
    @State private var manualNight: CGFloat = 0
    @State private var manualVeil: CGFloat = 0
    @State private var selectedTime = 0
    @State private var pressScale: CGFloat = 1
    @State private var seatPopped: String?
    @State private var pageFeedback = 0
    @State private var payFeedback = 0
    @State private var paymentSuccess = 0
    @State private var resumeTask: Task<Void, Never>?
    @State private var resumeOwnsNight = false
    @State private var paymentTask: Task<Void, Never>?
    @State private var paymentGeneration = 0
    @State private var manualOwned = false
    @State private var mode = MovieCardListTouchMode.none
    @State private var touchStartPage: CGFloat = 1
    @State private var touchStartCast: CGFloat = 0

    private var clockRunning: Bool { autoPlay && !reduced && isScripted }

    var body: some View {
        TimelineView(.animation(minimumInterval: 1.0 / 60.0, paused: !clockRunning)) { context in
            let frame = presentation(at: context.date)
            let movie = movieCardListMovies[Int(frame.page.rounded()).clamped(to: 0...(movieCardListMovies.count - 1))]
            let cardsAccessible = frame.stage == .cards && frame.open <= 0.35
            let detailAccessible = frame.stage == .detail && frame.open >= 0.35 && frame.cover <= 0.35
            let seatsAccessible = frame.stage == .seats && frame.seatClock >= MovieCardListSpec.buildDelayMilliseconds + MovieCardListSpec.buildRowMilliseconds * 0.35
            let buyAccessible = cardsAccessible || detailAccessible
            let closeAccessible = detailAccessible || seatsAccessible
            ZStack(alignment: .topLeading) {
                MovieCardListBackdrops(page: frame.page, open: frame.open)
                Group {
                    MovieCardListCards(page: frame.page, open: frame.open)
                    MovieCardListCardInfo(page: frame.page, open: frame.open)
                }
                .accessibilityHidden(!cardsAccessible)
                MovieCardListDetail(movie: movie, open: frame.open, castX: frame.castX)
                    .accessibilityHidden(!detailAccessible)
                MovieCardListBuyButton(open: frame.open, hidden: frame.stage == .seats || frame.stage == .pay, pressScale: pressScale)
                    .accessibilityHidden(!buyAccessible)
                MovieCardListCover(progress: frame.cover, reduced: reduced)
                if frame.stage == .seats || frame.stage == .pay {
                    MovieCardListSeats(
                        clock: frame.seatClock,
                        payClock: frame.payClock,
                        picked: frame.picked,
                        selectedTime: selectedTime,
                        poppedSeat: seatPopped,
                        reduced: reduced
                    )
                    .accessibilityHidden(!seatsAccessible)
                    .allowsHitTesting(seatsAccessible)
                }
                if frame.stage == .pay {
                    MovieCardListPayOverlay(clock: frame.payClock, reduced: reduced)
                        .accessibilityElement(children: .ignore)
                        .accessibilityLabel("Payment processing")
                        .accessibilityHidden(frame.payClock < MovieCardListSpec.payBlackAtMilliseconds + MovieCardListSpec.payBlackMilliseconds * 0.35)
                }
                MovieCardListTopControls(
                    stage: frame.stage,
                    closeActive: closeAccessible,
                    onClose: { topControlClose() },
                    onTarget: { takeOver(); scheduleResume() }
                )
                Color.white.movieCardListOpacity(frame.veil).allowsHitTesting(false)
                Color.black.movieCardListOpacity(frame.night).allowsHitTesting(false)
                Color.black.movieCardListOpacity(frame.resetFade).allowsHitTesting(false)
                MovieCardListTouchView(
                    onBegan: { point in touchBegan(point, frame: frame) },
                    onChanged: { point in touchChanged(point) },
                    onEnded: { point, velocity in touchEnded(point, velocity: velocity) }
                )
                .frame(width: MovieCardListSpec.canvas.width, height: MovieCardListSpec.canvas.height)
            }
            .frame(width: MovieCardListSpec.canvas.width, height: max(MovieCardListSpec.canvas.height, availableHeight), alignment: .topLeading)
            .clipped()
            .accessibilityElement(children: .contain)
        }
        .onAppear {
            guard autoPlay, !reduced else {
                holdReducedMotion()
                return
            }
            scriptStart = .now
            isScripted = true
        }
        .onChange(of: reduced) { _, isReduced in
            let scriptOwned = isScripted
            cancelResume()
            if isReduced {
                if scriptOwned { holdReducedMotion() }
            } else if autoPlay {
                if manualOwned {
                    scheduleResume()
                } else {
                    resetManualToCards(page: CGFloat(MovieCardListSpec.startPage))
                    scriptStart = .now
                    isScripted = true
                }
            }
        }
        .onChange(of: autoPlay) { _, enabled in
            let scriptOwned = isScripted
            cancelResume()
            if enabled, !reduced {
                if manualOwned {
                    scheduleResume()
                } else {
                    resetManualToCards(page: CGFloat(MovieCardListSpec.startPage))
                    scriptStart = .now
                    isScripted = true
                }
            } else {
                if scriptOwned { holdReducedMotion() }
            }
        }
        .onDisappear {
            cancelResume()
            cancelPaymentCompletion()
        }
        .sensoryFeedback(.selection, trigger: manualPicked)
        .sensoryFeedback(.selection, trigger: pageFeedback)
        .sensoryFeedback(.impact(weight: .medium), trigger: payFeedback)
        .sensoryFeedback(.success, trigger: paymentSuccess)
    }

    private func presentation(at date: Date) -> MovieCardListFrame {
        if isScripted {
            let milliseconds = date.timeIntervalSince(scriptStart) * 1_000
            return reduced ? movieCardListReducedScriptFrame(milliseconds) : movieCardListScriptFrame(milliseconds)
        }
        return MovieCardListFrame(
            page: manualPage,
            stage: manualStage,
            open: manualOpen,
            cover: manualCover,
            castX: manualCastX,
            seatClock: manualSeatClock,
            payClock: manualPayClock,
            picked: manualPicked,
            veil: manualVeil,
            night: manualNight
        )
    }

    private func takeOver() {
        cancelResume()
        manualOwned = true
        guard isScripted else { return }
        let milliseconds = Date().timeIntervalSince(scriptStart) * 1_000
        let frame = reduced ? movieCardListReducedScriptFrame(milliseconds) : movieCardListScriptFrame(milliseconds)
        isScripted = false
        manualPage = frame.page
        manualStage = frame.stage
        manualOpen = frame.open
        manualCover = frame.cover
        manualCastX = frame.castX
        manualSeatClock = frame.seatClock
        manualPayClock = frame.payClock
        manualPicked = frame.picked
    }

    private func scheduleResume() {
        guard autoPlay, !reduced else { return }
        cancelResume()
        resumeTask = Task { @MainActor in
            try? await Task.sleep(for: .milliseconds(3_500))
            guard !Task.isCancelled, autoPlay, !reduced else { return }
            resumeOwnsNight = true
            withAnimation(.easeInOut(duration: 0.22)) { manualNight = 1 }
            try? await Task.sleep(for: .milliseconds(220))
            guard !Task.isCancelled else {
                if resumeOwnsNight {
                    resumeOwnsNight = false
                    manualNight = 0
                }
                return
            }
            resumeOwnsNight = false
            manualOwned = false
            cancelPaymentCompletion()
            manualStage = .cards
            manualOpen = 0
            manualCover = 0
            manualPage = CGFloat(MovieCardListSpec.startPage)
            manualPicked = []
            manualCastX = 0
            manualPayClock = 0
            manualSeatClock = 0
            scriptStart = .now
            withAnimation(.easeOut(duration: 0.42)) { manualNight = 0 }
            try? await Task.sleep(for: .milliseconds(420))
            guard !Task.isCancelled, autoPlay, !reduced else { return }
            isScripted = true
        }
    }

    private func cancelResume() {
        resumeTask?.cancel()
        resumeTask = nil
        if resumeOwnsNight {
            resumeOwnsNight = false
            manualNight = 0
        }
    }

    private func holdReducedMotion() {
        let selected = presentation(at: .now).page.rounded()
            .clamped(to: 0...CGFloat(movieCardListMovies.count - 1))
        resetManualToCards(page: selected)
        manualOwned = false
        isScripted = false
    }

    private func resetManualToCards(page: CGFloat) {
        cancelPaymentCompletion()
        manualStage = .cards
        manualPage = page
        manualOpen = 0
        manualCover = 0
        manualCastX = 0
        manualSeatClock = 0
        manualPayClock = 0
        manualPicked = []
        manualNight = 0
        manualVeil = 0
    }

    private func standardAnimation(_ duration: Double) -> Animation {
        .timingCurve(0.32, 0.72, 0, 1, duration: reduced ? 0.2 : duration)
    }

    private func coverAnimation() -> Animation {
        .timingCurve(0.55, 0, 0.35, 1, duration: reduced ? 0.2 : 0.56)
    }

    private func goPage(_ page: CGFloat) {
        let target = page.clamped(to: 0...CGFloat(movieCardListMovies.count - 1))
        if reduced {
            withAnimation(.linear(duration: 0.1)) { manualVeil = 1 }
            Task { @MainActor in
                try? await Task.sleep(for: .milliseconds(100))
                guard !Task.isCancelled else { return }
                manualPage = target
                withAnimation(.linear(duration: 0.1)) { manualVeil = 0 }
            }
            pageFeedback += 1
            return
        }
        withAnimation(.interpolatingSpring(mass: 0.9, stiffness: 210, damping: 22, initialVelocity: 0)) {
            manualPage = target
        }
        pageFeedback += 1
    }

    private func openFilm() {
        manualStage = .detail
        withAnimation(standardAnimation(0.68)) { manualOpen = 1 }
    }

    private func closeFilm() {
        withAnimation(standardAnimation(0.544)) {
            manualOpen = 0
            manualStage = .cards
        }
    }

    private func buy() {
        if manualOpen < 0.5 { manualOpen = 1 }
        manualCover = 0
        manualSeatClock = 0
        manualPayClock = 0
        manualPicked = []
        manualStage = .seats
        withAnimation(coverAnimation()) { manualCover = 1 }
        withAnimation(.linear(duration: MovieCardListSpec.seatClockMilliseconds / 1_000)) {
            manualSeatClock = MovieCardListSpec.seatClockMilliseconds
        }
    }

    private func toggleSeat(_ id: String) {
        if manualPicked.contains(id) { manualPicked.remove(id) } else { manualPicked.insert(id) }
        guard !reduced else {
            seatPopped = nil
            return
        }
        seatPopped = id
        Task { @MainActor in
            try? await Task.sleep(for: .milliseconds(50))
            withAnimation(.interpolatingSpring(mass: 0.6, stiffness: 320, damping: 11, initialVelocity: 0)) { seatPopped = nil }
        }
    }

    private func pay() {
        cancelPaymentCompletion()
        let generation = paymentGeneration
        manualStage = .pay
        payFeedback += 1
        withAnimation(.linear(duration: 2.5)) { manualPayClock = 2_500 }
        paymentTask = Task { @MainActor in
            try? await Task.sleep(for: .milliseconds(1_900))
            guard !Task.isCancelled,
                  generation == paymentGeneration,
                  !isScripted,
                  manualStage == .pay else { return }
            paymentSuccess += 1
            try? await Task.sleep(for: .milliseconds(700))
            guard !Task.isCancelled,
                  generation == paymentGeneration,
                  !isScripted,
                  manualStage == .pay else { return }
            // PAY already owns an opaque black overlay, so reset beneath it without a visible second fade-in.
            manualNight = 1
            manualStage = .cards
            manualPage = CGFloat(MovieCardListSpec.startPage)
            manualOpen = 0
            manualCover = 0
            manualPicked = []
            manualCastX = 0
            manualPayClock = 0
            manualSeatClock = 0
            withAnimation(.easeOut(duration: 0.42)) { manualNight = 0 }
        }
    }

    private func cancelPaymentCompletion() {
        paymentGeneration &+= 1
        paymentTask?.cancel()
        paymentTask = nil
    }

    private func touchBegan(_ point: CGPoint, frame: MovieCardListFrame) {
        takeOver()
        mode = .none
        touchStartPage = manualPage
        touchStartCast = manualCastX
        let cardsActive = frame.stage == .cards && frame.open <= 0.35
        let detailActive = frame.stage == .detail && frame.open >= 0.35 && frame.cover <= 0.35
        let seatsActive = frame.stage == .seats && frame.seatClock >= MovieCardListSpec.buildDelayMilliseconds + MovieCardListSpec.buildRowMilliseconds * 0.35
        if (detailActive || seatsActive), point.distance(to: CGPoint(x: MovieCardListSpec.closeX, y: MovieCardListSpec.closeY)) < 26 {
            mode = .close
        } else if point.distance(to: CGPoint(x: MovieCardListSpec.targetX, y: MovieCardListSpec.targetY)) < 22 {
            mode = .target
        } else if cardsActive {
            mode = .cards(point)
        } else if detailActive {
            mode = .detail(point)
            let buttonCenter = MovieCardListSpec.sheetTop + MovieCardListSpec.detailButtonY
            if abs(point.y - buttonCenter) < MovieCardListSpec.detailButtonHeight / 2, abs(point.x - 201) < 160 {
                withAnimation(.interpolatingSpring(mass: 1, stiffness: 320, damping: 18, initialVelocity: 0)) { pressScale = 0.97 }
            }
        } else if seatsActive {
            mode = .seats(point)
        }
    }

    private func touchChanged(_ point: CGPoint) {
        switch mode {
        case .cards(let start):
            let raw = touchStartPage - (point.x - start.x) / MovieCardListSpec.pitch
            let overshoot = raw < 0 ? raw : raw > CGFloat(movieCardListMovies.count - 1) ? raw - CGFloat(movieCardListMovies.count - 1) : 0
            manualPage = raw - overshoot + overshoot * 0.3
        case .detail(let start):
            manualCastX = (touchStartCast + point.x - start.x).clamped(to: -72...0)
        default:
            break
        }
    }

    private func touchEnded(_ point: CGPoint, velocity: CGFloat) {
        defer {
            mode = .none
            withAnimation(.interpolatingSpring(mass: 1, stiffness: 320, damping: 18, initialVelocity: 0)) { pressScale = 1 }
            scheduleResume()
        }
        switch mode {
        case .close:
            if manualStage == .seats { withAnimation(standardAnimation(0.448)) { manualCover = 0; manualStage = .detail; manualPicked = [] } }
            else if manualStage == .detail { closeFilm() }
        case .cards(let start):
            let drag = point.x - start.x
            if abs(drag) < 10 {
                if abs(point.y - MovieCardListSpec.cardButtonY) < MovieCardListSpec.cardButtonHeight / 2,
                   abs(point.x - MovieCardListSpec.canvas.width / 2) < MovieCardListSpec.cardButtonWidth / 2 { buy() }
                else {
                    let dx = point.x - MovieCardListSpec.canvas.width / 2
                    if abs(dx) < MovieCardListSpec.cardWidth / 2 { openFilm() }
                    else { goPage(touchStartPage + (dx > 0 ? 1 : -1)) }
                }
            } else {
                goPage((manualPage - velocity / MovieCardListSpec.pitch * 0.12).rounded())
            }
        case .detail:
            let buttonCenter = MovieCardListSpec.sheetTop + MovieCardListSpec.detailButtonY
            if abs(point.y - buttonCenter) < MovieCardListSpec.detailButtonHeight / 2, abs(point.x - 201) < 160 { buy() }
            else { withAnimation(.interpolatingSpring(mass: 1, stiffness: 140, damping: 24, initialVelocity: 0)) { manualCastX = 0 } }
        case .seats:
            if point.y > MovieCardListSpec.payY - 38, point.y < MovieCardListSpec.payY + 38 { pay() }
            else if let seat = movieCardListSeat(at: point) { toggleSeat(seat) }
            else if point.y > MovieCardListSpec.dateY - 22, point.y < MovieCardListSpec.dateY + 22 {
                selectedTime = min(4, max(0, Int((point.x - 157) / 53)))
            }
        case .none:
            break
        case .target:
            break
        }
    }

    private func topControlClose() {
        takeOver()
        if manualStage == .seats {
            withAnimation(standardAnimation(0.448)) { manualCover = 0; manualStage = .detail; manualPicked = [] }
        } else if manualStage == .detail {
            closeFilm()
        }
        scheduleResume()
    }
}

private enum MovieCardListTouchMode {
    case none
    case cards(CGPoint)
    case detail(CGPoint)
    case seats(CGPoint)
    case close
    case target
}

private struct MovieCardListBackdrops: View {
    let page: CGFloat
    let open: CGFloat

    var body: some View {
        ZStack(alignment: .topLeading) {
            ForEach(Array(movieCardListMovies.enumerated()), id: \.element.id) { index, movie in
                ZStack(alignment: .topLeading) {
                    MovieCardListImage(name: movie.backdrop)
                        .scaledToFill()
                        .frame(width: MovieCardListSpec.canvas.width, height: MovieCardListSpec.backdropHeight, alignment: .top)
                        .clipped()
                        .background(movie.backdropColor)
                        .offset(x: -(CGFloat(index) - page) * MovieCardListSpec.canvas.width)
                    MovieCardListLockup(movie: movie)
                        .offset(x: -(CGFloat(index) - page) * MovieCardListSpec.canvas.width)
                }
                .frame(width: MovieCardListSpec.canvas.width, height: MovieCardListSpec.backdropHeight, alignment: .topLeading)
                .clipped()
                .offset(x: (CGFloat(index) - page) * MovieCardListSpec.canvas.width)
                    .frame(width: MovieCardListSpec.canvas.width, height: MovieCardListSpec.backdropHeight, alignment: .topLeading)
                    .clipped()
            }
            LinearGradient(colors: [.clear, MovieCardListPalette.paper], startPoint: UnitPoint(x: 0.5, y: 0.5), endPoint: UnitPoint(x: 0.5, y: 0.93))
                .frame(height: MovieCardListSpec.backdropHeight)
            Color.black.movieCardListOpacity(open)
        }
        .frame(width: MovieCardListSpec.canvas.width, height: MovieCardListSpec.backdropHeight, alignment: .topLeading)
        .clipped()
    }
}

private struct MovieCardListLockup: View {
    let movie: MovieCardListMovie

    var body: some View {
        Group {
            switch movie.tone {
            case .buds:
                VStack(spacing: -2) {
                    Text("BEST").font(MovieCardListFont.anton(64))
                    Text("BUDS").font(MovieCardListFont.anton(64))
                }
                .foregroundStyle(.white)
                .offset(y: 70)
            case .grift:
                Text("THE GRIFT")
                    .font(MovieCardListFont.serif(42))
                    .tracking(6)
                    .foregroundStyle(MovieCardListPalette.color(0xFFF7DC))
                    .shadow(color: MovieCardListPalette.color(0x503200, opacity: 0.45), radius: 8)
                    .offset(y: 92)
            case .jester:
                VStack(spacing: 0) {
                    Text("MASON VALE").font(MovieCardListFont.poppins(11, weight: .semibold)).tracking(2.6).foregroundStyle(MovieCardListPalette.color(0xC9CDCB))
                    Text("DRIFT").font(MovieCardListFont.anton(78)).tracking(-0.5).scaleEffect(x: 1, y: 1.1).padding(.vertical, -12)
                    Text("PUT ON A BRAVE FACE").font(MovieCardListFont.poppins(11.3, weight: .semibold)).tracking(1.6).padding(.top, 2)
                    Text("OCTOBER 4").font(MovieCardListFont.poppins(11.3, weight: .semibold)).tracking(1.7).foregroundStyle(MovieCardListPalette.color(0xB9BFBC)).padding(.top, 4.4)
                }
                .foregroundStyle(MovieCardListPalette.color(0xF2F2EE))
                .offset(y: 70.7)
            }
        }
        .frame(width: MovieCardListSpec.canvas.width, height: MovieCardListSpec.backdropHeight, alignment: .top)
    }
}

private struct MovieCardListCards: View {
    let page: CGFloat
    let open: CGFloat

    var body: some View {
        ZStack(alignment: .topLeading) {
            ForEach(Array(movieCardListMovies.enumerated()), id: \.element.id) { index, movie in
                let distance = CGFloat(index) - page
                let near = min(1, abs(distance))
                let sideScale = MovieCardListSpec.sideScale + (MovieCardListSpec.detailSideScale - MovieCardListSpec.sideScale) * open
                let cardScale = (1 - (1 - sideScale) * near) * (1 - 0.06 * open)
                let lift = (MovieCardListSpec.detailPosterTop - MovieCardListSpec.cardTop - MovieCardListSpec.cardHeight / 2 + (MovieCardListSpec.cardHeight / 2 - MovieCardListSpec.posterTop) * MovieCardListSpec.detailPosterScale) * open
                MovieCardListCard(
                    movie: movie,
                    frameOpacity: 1 - open,
                    washOpacity: min(1, abs(distance)) * MovieCardListSpec.sideWash * (1 - open)
                )
                    .scaleEffect(cardScale)
                    .offset(
                        x: (MovieCardListSpec.canvas.width - MovieCardListSpec.cardWidth) / 2 + distance * MovieCardListSpec.pitch * (1 - (1 - MovieCardListSpec.detailPitch) * open),
                        y: MovieCardListSpec.cardTop + lift
                    )
                    .zIndex(Double(10 - abs(distance) * 2))
            }
        }
        .frame(width: MovieCardListSpec.canvas.width, height: MovieCardListSpec.canvas.height, alignment: .topLeading)
    }
}

private struct MovieCardListCard: View {
    let movie: MovieCardListMovie
    let frameOpacity: CGFloat
    let washOpacity: CGFloat

    var body: some View {
        ZStack(alignment: .topLeading) {
            RoundedRectangle(cornerRadius: MovieCardListSpec.cardRadius)
                .fill(MovieCardListPalette.paper)
                .shadow(color: .black.opacity(0.12), radius: 18, y: 6)
                .movieCardListOpacity(frameOpacity)
            MovieCardListImage(name: movie.poster)
                .scaledToFill()
                .frame(width: MovieCardListSpec.cardWidth - MovieCardListSpec.posterInset * 2, height: MovieCardListSpec.posterHeight)
                .overlay { MovieCardListPosterLockup(movie: movie) }
                .clipShape(.rect(cornerRadius: MovieCardListSpec.posterRadius))
                .offset(x: MovieCardListSpec.posterInset, y: MovieCardListSpec.posterTop)
            RoundedRectangle(cornerRadius: MovieCardListSpec.cardRadius)
                .fill(MovieCardListPalette.paper)
                .movieCardListOpacity(washOpacity)
        }
        .frame(width: MovieCardListSpec.cardWidth, height: MovieCardListSpec.cardHeight, alignment: .topLeading)
    }
}

private struct MovieCardListPosterLockup: View {
    let movie: MovieCardListMovie

    var body: some View {
        Group {
            switch movie.tone {
            case .jester:
                VStack(alignment: .trailing, spacing: -2) {
                    Text("MASON VALE").font(MovieCardListFont.poppins(6.5, weight: .semibold)).tracking(1.4)
                    Text("DRIFT").font(MovieCardListFont.anton(34))
                    Text("OCTOBER 4").font(MovieCardListFont.poppins(6.5, weight: .semibold)).tracking(1.4)
                }
                .foregroundStyle(MovieCardListPalette.color(0xE6E6E2))
                .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .bottomTrailing)
                .padding(.trailing, 12).padding(.bottom, 18)
            case .buds:
                VStack(spacing: -2) { Text("BEST"); Text("BUDS") }
                    .font(MovieCardListFont.anton(40)).foregroundStyle(.white)
                    .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .top)
                    .padding(.top, 22)
            case .grift:
                Text("THE GRIFT").font(MovieCardListFont.serif(22)).tracking(3).foregroundStyle(MovieCardListPalette.color(0xFFF7DC))
                    .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .top)
                    .padding(.top, 22)
            }
        }
    }
}

private struct MovieCardListCardInfo: View {
    let page: CGFloat
    let open: CGFloat

    var body: some View {
        ZStack(alignment: .topLeading) {
            ForEach(Array(movieCardListMovies.enumerated()), id: \.element.id) { index, movie in
                let distance = CGFloat(index) - page
                VStack(spacing: 0) {
                    Text(movie.title)
                        .font(MovieCardListFont.poppins(30, weight: .medium))
                        .foregroundStyle(MovieCardListPalette.ink)
                        .frame(width: MovieCardListSpec.canvas.width, height: 36)
                        .offset(x: 4)
                    MovieCardListChips(genres: movie.genres).padding(.top, 4.3)
                    MovieCardListRating(movie: movie).padding(.top, 10)
                    Text("•••").font(.system(size: 17)).tracking(2).foregroundStyle(MovieCardListPalette.ink).padding(.top, 8)
                }
                .movieCardListOpacity(max(0, 1 - abs(distance) * 1.6) * (1 - min(1, open * 2.5)))
                .offset(x: distance * 60, y: MovieCardListSpec.titleY - 18)
            }
        }
        .frame(width: MovieCardListSpec.canvas.width, height: MovieCardListSpec.canvas.height, alignment: .topLeading)
    }
}

private struct MovieCardListChips: View {
    let genres: [String]

    var body: some View {
        HStack(spacing: 6.5) {
            ForEach(genres, id: \.self) { genre in
                Text(genre)
                    .font(MovieCardListFont.poppins(9.5))
                    .foregroundStyle(MovieCardListPalette.inkSoft)
                    .padding(.horizontal, 12).frame(height: 22)
                    .overlay { Capsule().stroke(MovieCardListPalette.chip, lineWidth: 1) }
            }
        }
    }
}

private struct MovieCardListRating: View {
    let movie: MovieCardListMovie

    var body: some View {
        HStack(spacing: 4) {
            Text(String(format: "%.1f", movie.rating)).font(MovieCardListFont.poppins(15)).foregroundStyle(MovieCardListPalette.inkSoft).padding(.trailing, 3)
            ForEach(0..<5, id: \.self) { index in MovieCardListStar(filled: index < movie.stars).frame(width: 13, height: 13) }
        }
        .accessibilityElement(children: .ignore)
        .accessibilityLabel("Rated \(String(format: "%.1f", movie.rating))")
    }
}

private struct MovieCardListStar: View {
    let filled: Bool

    var body: some View {
        MovieCardListStarPath()
            .fill(filled ? MovieCardListPalette.star : .clear)
            .overlay { MovieCardListStarPath().stroke(filled ? .clear : MovieCardListPalette.starOff, lineWidth: 1.6) }
    }
}

private struct MovieCardListStarPath: Shape {
    func path(in rect: CGRect) -> Path {
        let sx = rect.width / 24
        let sy = rect.height / 24
        var path = Path()
        // Exact RN SVG path: M12 2l2.9 6.3 6.9.8-5.1 4.7 1.4 6.8L12 17.2l-6.1 3.4 1.4-6.8L2.2 9.1l6.9-.8z.
        path.move(to: CGPoint(x: 12 * sx, y: 2 * sy))
        path.addLine(to: CGPoint(x: 14.9 * sx, y: 8.3 * sy))
        path.addLine(to: CGPoint(x: 21.8 * sx, y: 9.1 * sy))
        path.addLine(to: CGPoint(x: 16.7 * sx, y: 13.8 * sy))
        path.addLine(to: CGPoint(x: 18.1 * sx, y: 20.6 * sy))
        path.addLine(to: CGPoint(x: 12 * sx, y: 17.2 * sy))
        path.addLine(to: CGPoint(x: 5.9 * sx, y: 20.6 * sy))
        path.addLine(to: CGPoint(x: 7.3 * sx, y: 13.8 * sy))
        path.addLine(to: CGPoint(x: 2.2 * sx, y: 9.1 * sy))
        path.addLine(to: CGPoint(x: 9.1 * sx, y: 8.3 * sy))
        path.closeSubpath()
        return path
    }

}

private struct MovieCardListDetail: View {
    let movie: MovieCardListMovie
    let open: CGFloat
    let castX: CGFloat

    var body: some View {
        let sheetWidth = MovieCardListSpec.cardWidth + (MovieCardListSpec.canvas.width - MovieCardListSpec.sheetInset * 2 - MovieCardListSpec.cardWidth) * open
        let sheetX = (MovieCardListSpec.canvas.width - MovieCardListSpec.cardWidth) / 2 + (MovieCardListSpec.sheetInset - (MovieCardListSpec.canvas.width - MovieCardListSpec.cardWidth) / 2) * open
        let top = MovieCardListSpec.cardTop + (MovieCardListSpec.sheetTop - MovieCardListSpec.cardTop) * open
        let head = movieCardListClamp((open - 0.35) / 0.5)
        let body = movieCardListClamp((open - 0.55) / 0.45)
        ZStack(alignment: .topLeading) {
            RoundedRectangle(cornerRadius: MovieCardListSpec.cardRadius + (MovieCardListSpec.sheetRadius - MovieCardListSpec.cardRadius) * open)
                .fill(MovieCardListPalette.paper)
            VStack(spacing: 0) {
                Text(movie.title).font(MovieCardListFont.poppins(30, weight: .semibold)).foregroundStyle(MovieCardListPalette.ink)
                MovieCardListChips(genres: movie.genres).padding(.top, 4)
                MovieCardListRating(movie: movie).padding(.top, 9)
                Text(movie.director).font(MovieCardListFont.poppins(10)).foregroundStyle(MovieCardListPalette.inkSoft).padding(.top, 8)
            }
            .frame(maxWidth: .infinity)
            .movieCardListOpacity(head).offset(y: MovieCardListSpec.detailTitleY - 18 + (1 - head) * 18)
            VStack(alignment: .leading, spacing: 0) {
                Text("Actors").font(MovieCardListFont.poppins(13.5, weight: .semibold)).foregroundStyle(MovieCardListPalette.ink)
                HStack(spacing: MovieCardListSpec.actorGap) {
                    ForEach(movie.cast) { person in
                        VStack(spacing: 8) {
                            MovieCardListImage(name: person.image).scaledToFill().frame(width: MovieCardListSpec.actorWidth, height: MovieCardListSpec.actorHeight).clipShape(.rect(cornerRadius: 10))
                            Text(person.name).font(MovieCardListFont.poppins(9.5)).foregroundStyle(MovieCardListPalette.inkSoft).lineLimit(1).frame(width: MovieCardListSpec.actorWidth)
                        }
                    }
                }
                .offset(x: castX, y: 12)
                .frame(width: sheetWidth - MovieCardListSpec.sheetContentX * 2, alignment: .leading)
                .clipped()
                Text("Introduction").font(MovieCardListFont.poppins(13.5, weight: .semibold)).foregroundStyle(MovieCardListPalette.ink).padding(.top, 65)
                Text(movie.intro).font(MovieCardListFont.poppins(10.5)).foregroundStyle(MovieCardListPalette.inkMute).lineSpacing(5.5).lineLimit(3).padding(.top, 7)
            }
            .frame(width: sheetWidth - MovieCardListSpec.sheetContentX * 2, alignment: .leading)
            .offset(x: MovieCardListSpec.sheetContentX, y: MovieCardListSpec.actorsLabelY - 10 + (1 - body) * 26)
            .movieCardListOpacity(body)
        }
        .frame(width: sheetWidth, height: MovieCardListSpec.canvas.height - top + 40, alignment: .topLeading)
        .offset(x: sheetX, y: top)
        .movieCardListOpacity(open > 0.001 ? 1 : 0)
        .clipped()
    }
}

private struct MovieCardListBuyButton: View {
    let open: CGFloat
    let hidden: Bool
    let pressScale: CGFloat

    var body: some View {
        let width = MovieCardListSpec.cardButtonWidth + (MovieCardListSpec.detailButtonWidth - MovieCardListSpec.cardButtonWidth) * open
        let height = MovieCardListSpec.cardButtonHeight + (MovieCardListSpec.detailButtonHeight - MovieCardListSpec.cardButtonHeight) * open
        let centerY = MovieCardListSpec.cardButtonY + (MovieCardListSpec.sheetTop + MovieCardListSpec.detailButtonY - MovieCardListSpec.cardButtonY) * open
        RoundedRectangle(cornerRadius: 4)
            .fill(MovieCardListPalette.button)
            .overlay { Text("BUY TICKET").font(MovieCardListFont.poppins(16.3, weight: .semibold)).tracking(0.4).foregroundStyle(.white).padding(.top, 3) }
            .frame(width: width, height: height)
            .scaleEffect(pressScale)
            .offset(x: (MovieCardListSpec.canvas.width - width) / 2, y: centerY - height / 2)
            .opacity(hidden ? 0 : 1)
            .accessibilityLabel("Buy ticket")
    }
}

private struct MovieCardListCover: View {
    let progress: CGFloat
    let reduced: Bool

    @ViewBuilder
    var body: some View {
        if reduced {
            Color.black
                .movieCardListOpacity(progress)
                .frame(width: MovieCardListSpec.canvas.width, height: MovieCardListSpec.canvas.height)
                .allowsHitTesting(false)
        } else {
            let radius = hypot(MovieCardListSpec.canvas.width, MovieCardListSpec.canvas.height)
            Circle().fill(.black)
                .frame(width: radius * 2, height: radius * 2)
                .scaleEffect(max(0.0001, progress))
                .position(x: MovieCardListSpec.canvas.width / 2 + 60, y: MovieCardListSpec.sheetTop + MovieCardListSpec.detailButtonY)
                .allowsHitTesting(false)
        }
    }
}

private struct MovieCardListSeatSource: Identifiable {
    let id: String
    let row: Int
    let seats: String
}

private let movieCardListSeatRows: [MovieCardListSeatSource] = [
    .init(id: "row-0", row: 0, seats: "TTAAAAAAA TT"), .init(id: "row-1", row: 1, seats: "TTAAAAAAA TT"),
    .init(id: "row-2", row: 2, seats: "TTTTTTTTT TT"), .init(id: "row-3", row: 3, seats: "TTTTTTTTT TT"),
    .init(id: "row-4", row: 4, seats: "TTTTTTTTT TT"), .init(id: "row-5", row: 5, seats: "TTTTTTTTT TT"),
    .init(id: "row-6", row: 6, seats: "TTTTTTTTT TT"), .init(id: "row-7", row: 7, seats: "TTTTAAAAA TT"),
    .init(id: "row-8", row: 8, seats: "TAAAAAAAA TT"), .init(id: "row-9", row: 9, seats: "TAAAAAAAA TT"),
]

private struct MovieCardListSeats: View {
    let clock: Double
    let payClock: Double
    let picked: Set<String>
    let selectedTime: Int
    let poppedSeat: String?
    let reduced: Bool

    private var chromeAppear: CGFloat {
        reduced ? movieCardListRamp(clock, at: 0, duration: 200) : movieCardListRamp(clock, at: MovieCardListSpec.buildDelayMilliseconds + MovieCardListSpec.buildChromeAtMilliseconds, duration: MovieCardListSpec.buildChromeMilliseconds)
    }

    private var chrome: CGFloat {
        chromeAppear * (1 - movieCardListRamp(payClock, at: 0, duration: MovieCardListSpec.payBlackAtMilliseconds))
    }

    var body: some View {
        ZStack(alignment: .topLeading) {
            MovieCardListCinemaScreen(clock: clock, reduced: reduced).movieCardListOpacity(1 - movieCardListRamp(payClock, at: 0, duration: 260))
            ForEach(movieCardListSeatRows) { source in
                MovieCardListSeatRow(row: source.row, source: source.seats, clock: clock, picked: picked, poppedSeat: poppedSeat, reduced: reduced)
            }
            MovieCardListSeatChrome(selectedTime: selectedTime)
                .movieCardListOpacity(chrome)
                .offset(y: reduced ? 0 : (1 - chromeAppear) * 16)
            MovieCardListPayButton(total: picked.count * 12, clock: clock, payClock: payClock, reduced: reduced)
        }
        .frame(width: MovieCardListSpec.canvas.width, height: MovieCardListSpec.canvas.height, alignment: .topLeading)
    }
}

private struct MovieCardListCinemaScreen: View {
    let clock: Double
    let reduced: Bool

    var body: some View {
        let filmClock = clock - MovieCardListSpec.buildDelayMilliseconds
        let footage = reduced ? 0 : movieCardListRamp(filmClock, at: MovieCardListSpec.footageInAtMilliseconds, duration: MovieCardListSpec.footageInMilliseconds) *
            (1 - movieCardListRamp(filmClock, at: MovieCardListSpec.footageOutAtMilliseconds, duration: MovieCardListSpec.footageOutMilliseconds))
        let glint = reduced ? movieCardListRamp(clock, at: 0, duration: 200) : movieCardListRamp(filmClock, at: MovieCardListSpec.footageOutAtMilliseconds, duration: MovieCardListSpec.footageOutMilliseconds)
        ZStack(alignment: .topLeading) {
            MovieCardListImage(name: "MovieCardList-screen-jester.jpg")
                .scaledToFill()
                .frame(width: MovieCardListSpec.canvas.width - MovieCardListSpec.screenInset * 2, height: MovieCardListSpec.screenHeight + 12)
                .scaleEffect(reduced ? 1 : 1 + 0.08 * movieCardListRamp(clock - MovieCardListSpec.buildDelayMilliseconds - MovieCardListSpec.footageInAtMilliseconds, at: 0, duration: MovieCardListSpec.footageDriftMilliseconds))
                .offset(y: -6)
                .mask(MovieCardListCinemaBand().frame(width: MovieCardListSpec.canvas.width - MovieCardListSpec.screenInset * 2, height: MovieCardListSpec.screenHeight))
                .frame(width: MovieCardListSpec.canvas.width - MovieCardListSpec.screenInset * 2, height: MovieCardListSpec.screenHeight, alignment: .top)
                .movieCardListOpacity(footage)
                .offset(x: MovieCardListSpec.screenInset, y: MovieCardListSpec.screenTop)
            MovieCardListGlint().stroke(LinearGradient(colors: [.clear, .white.opacity(0.9), .white.opacity(0.1)], startPoint: .leading, endPoint: .trailing), style: StrokeStyle(lineWidth: 2.4, lineCap: .round))
                .frame(width: MovieCardListSpec.canvas.width, height: 64)
                .movieCardListOpacity(glint)
                .offset(y: MovieCardListSpec.screenTop)
        }
        .frame(width: MovieCardListSpec.canvas.width, height: MovieCardListSpec.canvas.height, alignment: .topLeading)
    }
}

private struct MovieCardListCinemaBand: Shape {
    func path(in rect: CGRect) -> Path {
        var path = Path()
        path.move(to: CGPoint(x: 0, y: 0))
        path.addQuadCurve(to: CGPoint(x: rect.width, y: 0), control: CGPoint(x: rect.midX, y: 26))
        path.addLine(to: CGPoint(x: rect.width - 18, y: rect.height))
        path.addQuadCurve(to: CGPoint(x: 18, y: rect.height), control: CGPoint(x: rect.midX, y: rect.height - 26 * 0.55))
        path.closeSubpath()
        return path
    }
}

private struct MovieCardListGlint: Shape {
    func path(in rect: CGRect) -> Path {
        var path = Path()
        path.move(to: CGPoint(x: MovieCardListSpec.screenInset + 6, y: 44))
        path.addQuadCurve(to: CGPoint(x: MovieCardListSpec.canvas.width - MovieCardListSpec.screenInset - 6, y: 44), control: CGPoint(x: MovieCardListSpec.canvas.width / 2, y: 20))
        return path
    }
}

private struct MovieCardListSeatRow: View {
    let row: Int
    let source: String
    let clock: Double
    let picked: Set<String>
    let poppedSeat: String?
    let reduced: Bool

    var body: some View {
        let progress = reduced ? (clock > 0 ? movieCardListRamp(clock, at: 0, duration: 200) : 0) : movieCardListRamp(clock, at: MovieCardListSpec.buildDelayMilliseconds + Double(row) * MovieCardListSpec.buildRowStaggerMilliseconds, duration: MovieCardListSpec.buildRowMilliseconds)
        let cells = movieCardListSeatCells(source, row: row)
        ZStack(alignment: .topLeading) {
            ForEach(cells) { cell in
                MovieCardListSeatGlyph(color: cell.taken ? MovieCardListPalette.seatTaken : picked.contains(cell.id) ? MovieCardListPalette.seatPicked : MovieCardListPalette.seatFree)
                    .frame(width: MovieCardListSpec.seat, height: MovieCardListSpec.seat)
                    .scaleEffect(cell.id == poppedSeat ? 0.7 : 1)
                    .animation(.interpolatingSpring(mass: 0.6, stiffness: 320, damping: 11, initialVelocity: 0), value: poppedSeat)
                    .offset(x: cell.x, y: 0)
            }
        }
        .movieCardListOpacity(progress)
        .offset(y: MovieCardListSpec.gridTop + CGFloat(row) * MovieCardListSpec.seatPitchY + (reduced ? 0 : (1 - progress) * 14))
    }
}

private struct MovieCardListSeatCell: Identifiable {
    let id: String
    let x: CGFloat
    let taken: Bool
}

private func movieCardListSeatCells(_ source: String, row: Int) -> [MovieCardListSeatCell] {
    let visible = source.filter { $0 != " " }
    let gridWidth = CGFloat(visible.count) * MovieCardListSpec.seatPitchX - (MovieCardListSpec.seatPitchX - MovieCardListSpec.seat) + MovieCardListSpec.aisle
    var x = (MovieCardListSpec.canvas.width - gridWidth) / 2
    var result: [MovieCardListSeatCell] = []
    for (column, character) in source.enumerated() {
        if character == " " { x += MovieCardListSpec.aisle; continue }
        result.append(.init(id: "\(row)-\(column)", x: x, taken: character == "T"))
        x += MovieCardListSpec.seatPitchX
    }
    return result
}

private func movieCardListSeat(at point: CGPoint) -> String? {
    guard point.y >= MovieCardListSpec.gridTop - 4, point.y <= MovieCardListSpec.gridTop + 10 * MovieCardListSpec.seatPitchY else { return nil }
    let row = Int(((point.y - MovieCardListSpec.gridTop) / MovieCardListSpec.seatPitchY).rounded(.down))
    guard movieCardListSeatRows.indices.contains(row) else { return nil }
    for cell in movieCardListSeatCells(movieCardListSeatRows[row].seats, row: row) {
        let rect = CGRect(x: cell.x - 5, y: MovieCardListSpec.gridTop + CGFloat(row) * MovieCardListSpec.seatPitchY - 5, width: MovieCardListSpec.seat + 10, height: MovieCardListSpec.seat + 10)
        if rect.contains(point), !cell.taken { return cell.id }
    }
    return nil
}

private struct MovieCardListSeatGlyph: View {
    let color: Color

    var body: some View { MovieCardListSeatGlyphPath().fill(color) }
}

private struct MovieCardListSeatGlyphPath: Shape {
    func path(in rect: CGRect) -> Path {
        let scale = rect.width / 24
        var path = Path()
        // RN's two filled SVG silhouettes, retained in the same 24×24 coordinate system.
        path.move(to: CGPoint(x: 5.5 * scale, y: 10 * scale))
        path.addLine(to: CGPoint(x: 5.5 * scale, y: 7 * scale))
        path.addCurve(to: CGPoint(x: 9.5 * scale, y: 3 * scale), control1: CGPoint(x: 5.5 * scale, y: 4.79 * scale), control2: CGPoint(x: 7.29 * scale, y: 3 * scale))
        path.addLine(to: CGPoint(x: 14.5 * scale, y: 3 * scale))
        path.addCurve(to: CGPoint(x: 18.5 * scale, y: 7 * scale), control1: CGPoint(x: 16.71 * scale, y: 3 * scale), control2: CGPoint(x: 18.5 * scale, y: 4.79 * scale))
        path.addLine(to: CGPoint(x: 18.5 * scale, y: 10 * scale))
        path.closeSubpath()
        path.move(to: CGPoint(x: 3 * scale, y: 19 * scale))
        path.addLine(to: CGPoint(x: 3 * scale, y: 11.5 * scale))
        path.addCurve(to: CGPoint(x: 7 * scale, y: 11.5 * scale), control1: CGPoint(x: 3 * scale, y: 10.17 * scale), control2: CGPoint(x: 3.9 * scale, y: 9.5 * scale))
        path.addCurve(to: CGPoint(x: 7 * scale, y: 15.5 * scale), control1: CGPoint(x: 7 * scale, y: 12.6 * scale), control2: CGPoint(x: 7 * scale, y: 14.17 * scale))
        path.addLine(to: CGPoint(x: 17 * scale, y: 15.5 * scale))
        path.addLine(to: CGPoint(x: 17 * scale, y: 11.5 * scale))
        path.addCurve(to: CGPoint(x: 21 * scale, y: 11.5 * scale), control1: CGPoint(x: 17 * scale, y: 9.5 * scale), control2: CGPoint(x: 21 * scale, y: 10.17 * scale))
        path.addLine(to: CGPoint(x: 21 * scale, y: 19 * scale))
        path.addCurve(to: CGPoint(x: 19 * scale, y: 21 * scale), control1: CGPoint(x: 21 * scale, y: 20.1 * scale), control2: CGPoint(x: 20.1 * scale, y: 21 * scale))
        path.addLine(to: CGPoint(x: 5 * scale, y: 21 * scale))
        path.addCurve(to: CGPoint(x: 3 * scale, y: 19 * scale), control1: CGPoint(x: 3.9 * scale, y: 21 * scale), control2: CGPoint(x: 3 * scale, y: 20.1 * scale))
        path.closeSubpath()
        return path
    }

}

private struct MovieCardListSeatChrome: View {
    let selectedTime: Int
    private let times = ["17:30", "18:00", "18:30", "19:00", "20:15"]

    var body: some View {
        ZStack(alignment: .topLeading) {
            HStack(spacing: 0) {
                MovieCardListLegend(name: "Available", color: MovieCardListPalette.seatFree)
                Spacer()
                MovieCardListLegend(name: "Taken", color: MovieCardListPalette.seatTaken)
                Spacer()
                MovieCardListLegend(name: "Selected", color: MovieCardListPalette.seatPicked)
            }
            .frame(width: 274)
            .offset(x: 64, y: MovieCardListSpec.legendY - 8)
            HStack(spacing: 0) {
                HStack(spacing: 4) { Text("January 18"); MovieCardListCaret().fill(.white).frame(width: 14, height: 14) }
                    .font(MovieCardListFont.poppins(13, weight: .medium)).foregroundStyle(.white).padding(.trailing, 22)
                HStack(spacing: 14) {
                    ForEach(Array(times.enumerated()), id: \.offset) { index, time in
                        Text(time).font(MovieCardListFont.poppins(11.5, weight: .medium)).foregroundStyle(.white)
                            .padding(.horizontal, 8).frame(height: 22)
                            .background(index == selectedTime ? MovieCardListPalette.red : .clear, in: .rect(cornerRadius: 2))
                    }
                }
            }
            .offset(x: 64, y: MovieCardListSpec.dateY - 14)
        }
        .frame(width: MovieCardListSpec.canvas.width, height: MovieCardListSpec.canvas.height, alignment: .topLeading)
    }
}

private struct MovieCardListLegend: View {
    let name: String
    let color: Color
    var body: some View {
        HStack(spacing: 6) { Circle().fill(color).frame(width: 6, height: 6); Text(name) }
            .font(MovieCardListFont.poppins(9.5)).foregroundStyle(MovieCardListPalette.legend)
    }
}

private struct MovieCardListCaret: Shape {
    func path(in rect: CGRect) -> Path {
        var path = Path()
        path.move(to: CGPoint(x: rect.width * 0.25, y: rect.height * 0.38))
        path.addLine(to: CGPoint(x: rect.width * 0.5, y: rect.height * 0.65))
        path.addLine(to: CGPoint(x: rect.width * 0.75, y: rect.height * 0.38))
        path.closeSubpath()
        return path
    }
}

private struct MovieCardListPayButton: View {
    let total: Int
    let clock: Double
    let payClock: Double
    let reduced: Bool

    var body: some View {
        let entrance = reduced ? movieCardListRamp(clock, at: 0, duration: 200) : movieCardListRamp(clock, at: MovieCardListSpec.buildDelayMilliseconds + MovieCardListSpec.buildChromeAtMilliseconds, duration: MovieCardListSpec.buildChromeMilliseconds)
        let visible = entrance *
            (1 - movieCardListRamp(payClock, at: MovieCardListSpec.payBlackAtMilliseconds, duration: MovieCardListSpec.payBlackMilliseconds))
        let shrink = reduced ? 0 : movieCardListRamp(payClock, at: 0, duration: MovieCardListSpec.payShrinkMilliseconds)
        let width = MovieCardListSpec.payWidth + (MovieCardListSpec.payHeight - MovieCardListSpec.payWidth) * shrink
        RoundedRectangle(cornerRadius: 12 + (MovieCardListSpec.payHeight / 2 - 12) * shrink)
            .fill(LinearGradient(colors: [MovieCardListPalette.red, MovieCardListPalette.redHot], startPoint: .leading, endPoint: .trailing))
            .overlay { Text(total > 0 ? "PAY $\(total)" : "PAY").font(MovieCardListFont.poppins(12, weight: .semibold)).tracking(0.8).foregroundStyle(.white).movieCardListOpacity(1 - movieCardListRamp(payClock, at: 0, duration: MovieCardListSpec.payShrinkMilliseconds * 0.5)) }
            .frame(width: width, height: MovieCardListSpec.payHeight)
            .offset(x: (MovieCardListSpec.canvas.width - width) / 2, y: MovieCardListSpec.payY - MovieCardListSpec.payHeight / 2 + (reduced ? 0 : (1 - entrance) * 20))
            .movieCardListOpacity(visible)
    }
}

private struct MovieCardListPayOverlay: View {
    let clock: Double
    let reduced: Bool

    var body: some View {
        let black = movieCardListRamp(clock, at: MovieCardListSpec.payBlackAtMilliseconds, duration: MovieCardListSpec.payBlackMilliseconds)
        let face = reduced ? 0 : movieCardListRamp(clock, at: MovieCardListSpec.payFaceAtMilliseconds, duration: 120) * (1 - movieCardListRamp(clock, at: MovieCardListSpec.paySpinAtMilliseconds - 80, duration: 120))
        let spinner = reduced ? 0 : movieCardListRamp(clock, at: MovieCardListSpec.paySpinAtMilliseconds - 60, duration: 120) * (1 - movieCardListRamp(clock, at: MovieCardListSpec.payCheckAtMilliseconds - 40, duration: 100))
        let check = movieCardListRamp(clock, at: MovieCardListSpec.payCheckAtMilliseconds - 40, duration: 80)
        ZStack {
            Color.black.movieCardListOpacity(black)
            ForEach(0..<8, id: \.self) { index in
                MovieCardListFacePart(index: index, clock: clock)
                    .frame(width: 54, height: 54)
                    .movieCardListOpacity(face)
            }
            Circle().trim(from: 0.06, to: 0.82).stroke(.white, style: StrokeStyle(lineWidth: 2.6, lineCap: .round)).frame(width: 36, height: 36).rotationEffect(.degrees((clock - MovieCardListSpec.paySpinAtMilliseconds) * 0.9)).movieCardListOpacity(spinner)
            ZStack {
                Circle().trim(from: 0, to: reduced ? 1 : movieCardListRamp(clock, at: MovieCardListSpec.payCheckAtMilliseconds, duration: MovieCardListSpec.payCheckMilliseconds * 0.6)).stroke(.white, style: StrokeStyle(lineWidth: 2.6, lineCap: .round)).rotationEffect(.degrees(-90))
                MovieCardListCheck().trim(from: 0, to: reduced ? 1 : movieCardListRamp(clock, at: MovieCardListSpec.payCheckAtMilliseconds + MovieCardListSpec.payCheckMilliseconds * 0.35, duration: MovieCardListSpec.payCheckMilliseconds * 0.65)).stroke(.white, style: StrokeStyle(lineWidth: 2.6, lineCap: .round, lineJoin: .round))
            }.frame(width: 36, height: 36).movieCardListOpacity(check)
        }
        .frame(width: MovieCardListSpec.canvas.width, height: MovieCardListSpec.canvas.height)
    }
}

private struct MovieCardListFacePart: View {
    let index: Int
    let clock: Double

    var body: some View {
        let trim = movieCardListRamp(
            clock,
            at: MovieCardListSpec.payFaceAtMilliseconds + Double(index) * 30,
            duration: MovieCardListSpec.payFaceMilliseconds * 0.6
        )
        MovieCardListFaceSegment(index: index)
            .trim(from: 0, to: trim)
            .stroke(.white, style: StrokeStyle(lineWidth: 2.6, lineCap: .round, lineJoin: .round))
    }
}

private struct MovieCardListFaceSegment: Shape {
    let index: Int

    func path(in rect: CGRect) -> Path {
        let s = rect.width / 54
        var path = Path()
        switch index {
        case 0:
            path.move(to: CGPoint(x: 6*s, y: 16*s)); path.addLine(to: CGPoint(x: 6*s, y: 10*s)); path.addQuadCurve(to: CGPoint(x: 10*s, y: 6*s), control: CGPoint(x: 6*s, y: 6*s)); path.addLine(to: CGPoint(x: 16*s, y: 6*s))
        case 1:
            path.move(to: CGPoint(x: 38*s, y: 6*s)); path.addLine(to: CGPoint(x: 44*s, y: 6*s)); path.addQuadCurve(to: CGPoint(x: 48*s, y: 10*s), control: CGPoint(x: 48*s, y: 6*s)); path.addLine(to: CGPoint(x: 48*s, y: 16*s))
        case 2:
            path.move(to: CGPoint(x: 48*s, y: 38*s)); path.addLine(to: CGPoint(x: 48*s, y: 44*s)); path.addQuadCurve(to: CGPoint(x: 44*s, y: 48*s), control: CGPoint(x: 48*s, y: 48*s)); path.addLine(to: CGPoint(x: 38*s, y: 48*s))
        case 3:
            path.move(to: CGPoint(x: 16*s, y: 48*s)); path.addLine(to: CGPoint(x: 10*s, y: 48*s)); path.addQuadCurve(to: CGPoint(x: 6*s, y: 44*s), control: CGPoint(x: 6*s, y: 48*s)); path.addLine(to: CGPoint(x: 6*s, y: 38*s))
        case 4:
            path.move(to: CGPoint(x: 19*s, y: 20*s)); path.addLine(to: CGPoint(x: 19*s, y: 24*s))
        case 5:
            path.move(to: CGPoint(x: 35*s, y: 20*s)); path.addLine(to: CGPoint(x: 35*s, y: 24*s))
        case 6:
            path.move(to: CGPoint(x: 27*s, y: 20*s)); path.addLine(to: CGPoint(x: 27*s, y: 30*s)); path.addLine(to: CGPoint(x: 24*s, y: 30*s))
        default:
            path.move(to: CGPoint(x: 20*s, y: 36*s)); path.addQuadCurve(to: CGPoint(x: 34*s, y: 36*s), control: CGPoint(x: 27*s, y: 39.4*s))
        }
        return path
    }
}

private struct MovieCardListCheck: Shape {
    func path(in rect: CGRect) -> Path {
        var path = Path(); path.move(to: CGPoint(x: rect.width * 0.2, y: rect.height * 0.52)); path.addLine(to: CGPoint(x: rect.width * 0.38, y: rect.height * 0.68)); path.addLine(to: CGPoint(x: rect.width * 0.72, y: rect.height * 0.32)); return path
    }
}

private struct MovieCardListTopControls: View {
    let stage: MovieCardListStage
    let closeActive: Bool
    let onClose: () -> Void
    let onTarget: () -> Void

    var body: some View {
        ZStack(alignment: .topLeading) {
            Button(action: onClose) {
                MovieCardListClose().stroke(.white, style: StrokeStyle(lineWidth: 2.2, lineCap: .round))
                .frame(width: MovieCardListSpec.closeSize - 2, height: MovieCardListSpec.closeSize - 2)
                    .frame(width: 44, height: 44)
            }
            .buttonStyle(.plain)
            .disabled(!closeActive)
            .offset(x: MovieCardListSpec.closeX - 22, y: MovieCardListSpec.closeY - 22)
            .accessibilityLabel(stage == .seats ? "Back to film" : "Close")
            .accessibilityHidden(!closeActive)
            Button(action: onTarget) {
                let size = MovieCardListSpec.targetSize
                let outerRadius = size * 9.6 / 24
                let innerDiameter = size * 7.2 / 24
                ZStack {
                    Circle().stroke(.white, lineWidth: 2).frame(width: outerRadius * 2, height: outerRadius * 2)
                    Circle().fill(.white).frame(width: innerDiameter, height: innerDiameter)
                }
                .frame(width: MovieCardListSpec.targetSize, height: MovieCardListSpec.targetSize)
                    .frame(width: 44, height: 44)
            }
            .buttonStyle(.plain)
            .offset(x: MovieCardListSpec.targetX - 22, y: MovieCardListSpec.targetY - 22)
            .accessibilityLabel("Nearby cinemas")
        }
    }
}

private struct MovieCardListClose: Shape {
    func path(in rect: CGRect) -> Path {
        let sx = rect.width / 24
        let sy = rect.height / 24
        var path = Path()
        path.move(to: CGPoint(x: 5 * sx, y: 5 * sy))
        path.addLine(to: CGPoint(x: 19 * sx, y: 19 * sy))
        path.move(to: CGPoint(x: 19 * sx, y: 5 * sy))
        path.addLine(to: CGPoint(x: 5 * sx, y: 19 * sy))
        return path
    }
}

private struct MovieCardListImage: View {
    let name: String

    var body: some View {
        let image = MovieCardListAssets.image(named: name)
        Image(uiImage: image).resizable()
    }
}

private enum MovieCardListAssets {
    private static let fontFiles = [
        "MovieCardList-Anton-Regular", "MovieCardList-Poppins-Regular", "MovieCardList-Poppins-Medium", "MovieCardList-Poppins-SemiBold", "MovieCardList-DMSerifDisplay-Regular",
    ]
    private static let registered: Void = {
        for file in fontFiles {
            guard let url = Bundle.main.url(forResource: file, withExtension: "ttf") else {
                #if DEBUG
                assertionFailure("Missing MovieCardList font: \(file).ttf")
                #endif
                continue
            }
            CTFontManagerRegisterFontsForURL(url as CFURL, .process, nil)
        }
        #if DEBUG
        for name in ["Anton-Regular", "Poppins-Regular", "Poppins-Medium", "Poppins-SemiBold", "DMSerifDisplay-Regular"] {
            assert(UIFont(name: name, size: 12) != nil, "MovieCardList font did not register: \(name)")
        }
        #endif
    }()

    static func register() { _ = registered }

    static func image(named name: String) -> UIImage {
        if let image = UIImage(named: name) { return image }
        #if DEBUG
        assertionFailure("Missing MovieCardList image: \(name)")
        #endif
        return UIImage()
    }
}

private struct MovieCardListTouchView: UIViewRepresentable {
    let onBegan: (CGPoint) -> Void
    let onChanged: (CGPoint) -> Void
    let onEnded: (CGPoint, CGFloat) -> Void

    func makeUIView(context: Context) -> MovieCardListTouchSurface {
        let view = MovieCardListTouchSurface()
        updateUIView(view, context: context)
        return view
    }

    func updateUIView(_ view: MovieCardListTouchSurface, context: Context) {
        view.onBegan = onBegan
        view.onChanged = onChanged
        view.onEnded = onEnded
    }
}

private final class MovieCardListTouchSurface: UIView, UIGestureRecognizerDelegate {
    var onBegan: ((CGPoint) -> Void)?
    var onChanged: ((CGPoint) -> Void)?
    var onEnded: ((CGPoint, CGFloat) -> Void)?
    private var start = CGPoint.zero
    private var previous = CGPoint.zero
    private var previousTime = 0.0
    private var velocityX: CGFloat = 0

    override init(frame: CGRect) {
        super.init(frame: frame)
        backgroundColor = .clear
        isOpaque = false
        let recognizer = UILongPressGestureRecognizer(target: self, action: #selector(handle(_:)))
        recognizer.minimumPressDuration = 0
        recognizer.allowableMovement = .greatestFiniteMagnitude
        recognizer.cancelsTouchesInView = false
        recognizer.delegate = self
        addGestureRecognizer(recognizer)
        isAccessibilityElement = false
    }

    required init?(coder: NSCoder) { fatalError("init(coder:) has not been implemented") }

    func gestureRecognizer(_ gestureRecognizer: UIGestureRecognizer, shouldRecognizeSimultaneouslyWith otherGestureRecognizer: UIGestureRecognizer) -> Bool { true }

    @objc private func handle(_ recognizer: UILongPressGestureRecognizer) {
        let point = recognizer.location(in: self)
        let time = CACurrentMediaTime()
        switch recognizer.state {
        case .began:
            start = point; previous = point; previousTime = time; velocityX = 0
            onBegan?(point)
        case .changed:
            let elapsed = max(0.001, time - previousTime)
            velocityX = (point.x - previous.x) / elapsed
            previous = point; previousTime = time
            onChanged?(point)
        case .ended, .cancelled, .failed:
            onEnded?(point, velocityX)
        default:
            break
        }
    }
}

private extension Int {
    func clamped(to range: ClosedRange<Int>) -> Int { Swift.min(Swift.max(self, range.lowerBound), range.upperBound) }
}

private extension CGFloat {
    func clamped(to range: ClosedRange<CGFloat>) -> CGFloat { Swift.min(Swift.max(self, range.lowerBound), range.upperBound) }
}

private extension View {
    /// SwiftUI's built-in opacity API takes Double; the animation model deliberately uses CGFloat for geometry.
    func movieCardListOpacity(_ value: CGFloat) -> some View { opacity(Double(value)) }
}

private extension CGPoint {
    func distance(to point: CGPoint) -> CGFloat { hypot(x - point.x, y - point.y) }
}

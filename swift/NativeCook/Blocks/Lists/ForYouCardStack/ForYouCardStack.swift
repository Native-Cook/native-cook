import CoreText
import SwiftUI
import UIKit

enum ForYouCardStackPalette {
    static let page = Color(red: 0xEC / 255, green: 0xD7 / 255, blue: 0xD4 / 255)
    static let ink = Color(red: 0x16 / 255, green: 0x10 / 255, blue: 0x10 / 255)
    static let placeholder = Color(red: 0xCD / 255, green: 0xB9 / 255, blue: 0xB5 / 255)
    static let design = Color(red: 0x4E / 255, green: 0x42 / 255, blue: 0xF8 / 255)
    static let nextCard = Color(red: 0xE9 / 255, green: 0x48 / 255, blue: 0x8F / 255)
    static let grabber = Color.white.opacity(0.4)
    static let player = Color(red: 0x4A / 255, green: 0x44 / 255, blue: 0xF5 / 255)
    static let reading = Color(red: 0xC2 / 255, green: 0x6C / 255, blue: 0xF2 / 255)
    static let white = Color.white
    static let dim = Color.white.opacity(0.4)
    static let faint = Color.white.opacity(0.28)
    static let textUnread = Color.white.opacity(0.42)

    static func hex(_ value: UInt32, alpha: Double = 1) -> Color {
        Color(
            red: Double((value >> 16) & 0xFF) / 255,
            green: Double((value >> 8) & 0xFF) / 255,
            blue: Double(value & 0xFF) / 255,
            opacity: alpha
        )
    }
}

enum ForYouCardStackFonts {
    static let poster = "Anton-Regular"
    static let medium = "Inter-Medium"
    static let regular = "Inter-Regular"
    static let heavy = "Inter-ExtraBold"
    static let reading = "Charter-Bold"
}

enum ForYouCardStackAssets {
    private static let imageNames = [
        "ForYouCardStack-avatar.png",
        "ForYouCardStack-cover-sky.png",
        "ForYouCardStack-page-landscape.jpg",
        "ForYouCardStack-page-portrait.jpg",
    ]
    private static let fontNames = [
        "ForYouCardStack-Anton-Regular",
        "ForYouCardStack-Inter-ExtraBold",
        "ForYouCardStack-Inter-Medium",
        "ForYouCardStack-Inter-Regular",
    ]

    static func registerFonts() {
        for name in fontNames {
            guard let url = Bundle.main.url(forResource: name, withExtension: "ttf") else {
                assertionFailure("Missing For You Card Stack font resource: \(name).ttf")
                continue
            }
            _ = CTFontManagerRegisterFontsForURL(url as CFURL, .process, nil)
        }
#if DEBUG
        for name in fontNames.map({ $0.replacingOccurrences(of: "ForYouCardStack-", with: "") }) {
            assert(UIFont(name: name, size: 12) != nil, "Missing registered For You Card Stack font: \(name)")
        }
#endif
    }

    static func image(named name: String) -> UIImage {
        guard let image = UIImage(named: name) else {
            assertionFailure("Missing For You Card Stack image: \(name)")
            return UIImage()
        }
        return image
    }

    static func assertResourcesInDebug() {
#if DEBUG
        for name in imageNames {
            assert(UIImage(named: name) != nil, "Missing For You Card Stack image: \(name)")
        }
#endif
    }
}

enum ForYouCardStackLayout {
    static let coverWidth: CGFloat = 200
    static let coverHeight: CGFloat = 300
    static let rowScale: CGFloat = 186 / coverHeight
    static let rowStep: CGFloat = 124.3 + 11.3
    static let pageWidth: CGFloat = 190
    static let pageHeight: CGFloat = 285
    static let pageScale: CGFloat = pageWidth / coverWidth
    static let neighbourScale: CGFloat = 0.87
    static let neighbourWidth = pageWidth * neighbourScale
    static let neighbourHeight = pageHeight * neighbourScale
}

enum ForYouCardStackHomeLayout {
    static let titleTop: CGFloat = 81.6
    static let titleLeft: CGFloat = 28.6
    static let titleSize: CGFloat = 29.4
    static let avatar: CGFloat = 52
    static let avatarTop: CGFloat = 75
    static let avatarRight: CGFloat = 30
    static let searchTop: CGFloat = 160
    static let searchLeft: CGFloat = 34
    static let searchIcon: CGFloat = 21
    static let searchText: CGFloat = 15.4
    static let placeholderLeft: CGFloat = 74
    static let placeholderTop: CGFloat = 162
    static let cardTop: CGFloat = 240
    static let cardHeight: CGFloat = 600
    static let cardRadius: CGFloat = 32
    static let nextCardTop: CGFloat = 830
    static let headingTop: CGFloat = 274.7
    static let headingLeft: CGFloat = 28
    static let headingSize: CGFloat = 29.4
    static let grabberTop: CGFloat = 288.5
    static let grabberRight: CGFloat = 30
    static let grabberWidth: CGFloat = 40
    static let labelSize: CGFloat = 13.7
    static let newLabelTop: CGFloat = 328.7
    static let chartsLabelTop: CGFloat = 586.4
    static let labelLeft: CGFloat = 29
    static let labelRight: CGFloat = 34
    static let rowTop: CGFloat = 366
    static let rowCoverWidth: CGFloat = 124.3
    static let rowCoverHeight: CGFloat = 186
    static let rowDeckLeft: CGFloat = 40.7
    static let deckPeek: CGFloat = 9.5
    static let deckInset: CGFloat = 4
    static let chartsTop: CGFloat = 625
    static let chartsLeft: CGFloat = 28
    static let chartsStep: CGFloat = 132
}

enum ForYouCardStackPlayerLayout {
    static let backLeft: CGFloat = 36.7
    static let backTop: CGFloat = 83.3
    static let backWidth: CGFloat = 13
    static let backHeight: CGFloat = 12.7
    static let bookIconLeft: CGFloat = 350
    static let bookIconTop: CGFloat = 83.3
    static let bookIconWidth: CGFloat = 16.7
    static let bookIconHeight: CGFloat = 14.7
    static let spineX: CGFloat = 100
    static let centerY: CGFloat = 291.5
    static let authorTop: CGFloat = 477.8
    static let authorSize: CGFloat = 12.4
    static let titleTop: CGFloat = 494.1
    static let titleSize: CGFloat = 27
    static let titleLineHeight: CGFloat = 40
    static let chapterTop: CGFloat = 576.6
    static let chapterSize: CGFloat = 13
    static let controlsY: CGFloat = 686
    static let controlsPlay: CGFloat = 46
    static let controlsSkipX: CGFloat = 57
    static let controlsIcon: CGFloat = 22
    static let progressY: CGFloat = 800
    static let progressLeft: CGFloat = 34.7
    static let progressRight: CGFloat = 35
    static let progressPlayed = 0.012
    static let timesTop: CGFloat = 804
    static let timeSize: CGFloat = 9.5
}

enum ForYouCardStackReadingLayout {
    static let waveLeft: CGFloat = 443
    static let waveControlX: CGFloat = 230
    static let waveControlY: CGFloat = 412
    static let waveRight: CGFloat = 430
    static let textTop: CGFloat = 141
    static let textLeft: CGFloat = 36.6
    static let textSize: CGFloat = 19.3
    static let textLineHeight: CGFloat = 33
    static let textDim = 0.42
    static let readingBookScale: CGFloat = 0.4737
    static let readingBookSpineX: CGFloat = 200
    static let readingBookCenterY: CGFloat = 442.5
    static let titleShift: CGFloat = 64
}

private enum ForYouCardStackMotion {
    static let loopMilliseconds: Double = 13_700
    static let deck: ClosedRange<Double> = 800...1100
    static let push: ClosedRange<Double> = 1650...2050
    static let playTap: Double = 3450
    static let open: ClosedRange<Double> = 3500...5000
    static let readTimes = [4000.0, 4200, 5000, 6000, 7000, 8300]
    static let readLines = [0.0, 0.6, 1.4, 2, 3.4, 5.2]
    static let pauseTap: Double = 8450
    static let close: ClosedRange<Double> = 8500...10375
    // Approximate progress measured from the recovered clip's interactive edge-back pan.
    static let popTimes = [11900.0, 12000, 12100, 12200, 12300, 12400, 12500]
    static let popProgress = [0.0, 0.05, 0.15, 0.30, 0.50, 0.80, 1.0]
    static let deckBack: ClosedRange<Double> = 12950...13300
    static let deckMilliseconds: Double = 300
    static let pushMilliseconds: Double = 400
    static let playMilliseconds: Double = 1500
    static let closeMilliseconds: Double = 1600
    static let resumeAfterMilliseconds: Double = 3500
    static let reducedFadeMilliseconds: Double = 200
    static let pressScale = 0.97
    static let pressInMilliseconds: Double = 120
    static let pressOutMilliseconds: Double = 150
}

private enum ForYouCardStackCurve {
    case linear
    case timingDefault
    case pin
    case sheet
    case out
    case spring(dampingRatio: Double, velocity: Double)
}

struct ForYouCardStackScene {
    var deck: Double
    var push: Double
    var playing: Double
    var open: Double
    var close: Double
    var words: Double

    static let rest = ForYouCardStackScene(deck: 0, push: 0, playing: 0, open: 0, close: 0, words: 0)
}

enum ForYouCardStackScript {
    static func clamp01(_ value: Double) -> Double {
        min(max(value, 0), 1)
    }

    static func segment(_ time: Double, _ range: ClosedRange<Double>) -> Double {
        guard range.upperBound > range.lowerBound else { return time >= range.upperBound ? 1 : 0 }
        return clamp01((time - range.lowerBound) / (range.upperBound - range.lowerBound))
    }

    static func sample(at milliseconds: Double) -> ForYouCardStackScene {
        let loop = ForYouCardStackMotion.loopMilliseconds
        let time = ((milliseconds.truncatingRemainder(dividingBy: loop)) + loop).truncatingRemainder(dividingBy: loop)
        let deck = pin(segment(time, ForYouCardStackMotion.deck)) * (1 - pin(segment(time, ForYouCardStackMotion.deckBack)))
        let pop = interpolate(time, input: ForYouCardStackMotion.popTimes, output: ForYouCardStackMotion.popProgress)
        let push = sheet(segment(time, ForYouCardStackMotion.push)) * (1 - pop)
        let playing = time >= ForYouCardStackMotion.playTap && time < ForYouCardStackMotion.pauseTap ? 1.0 : 0.0
        var open = segment(time, ForYouCardStackMotion.open)
        var close = segment(time, ForYouCardStackMotion.close)
        if close >= 1 {
            open = 0
            close = 0
        }
        let words: Double
        if time < ForYouCardStackMotion.pauseTap {
            words = interpolate(time, input: ForYouCardStackMotion.readTimes, output: ForYouCardStackMotion.readLines)
        } else {
            words = ForYouCardStackMotion.readLines.last ?? 0
        }
        return ForYouCardStackScene(deck: deck, push: push, playing: playing, open: open, close: close, words: words)
    }

    static func look(open: Double, close: Double) -> ForYouCardStackLook {
        let pinOpen: (ClosedRange<Double>) -> Double = { pin(segment(open, $0)) }
        let pinClose: (ClosedRange<Double>) -> Double = { pin(segment(close, $0)) }
        let bookScale = ForYouCardStackReadingLayout.readingBookScale
        if close > 0 {
            let grow = pinClose(ForYouCardStackClosePhase.grow)
            let shut = pinClose(ForYouCardStackClosePhase.leaf0)
            return ForYouCardStackLook(
                neighbours: pinClose(ForYouCardStackClosePhase.neighbours),
                chapter: pinClose(ForYouCardStackClosePhase.chapter),
                wave: 1 - pinClose(ForYouCardStackClosePhase.wave),
                title: 1 - pinClose(ForYouCardStackClosePhase.title),
                text: 1 - pinClose(ForYouCardStackClosePhase.text),
                scale: mix(bookScale, 1, grow),
                spineX: mix(ForYouCardStackReadingLayout.readingBookSpineX, ForYouCardStackPlayerLayout.spineX, shut),
                centerY: mix(ForYouCardStackReadingLayout.readingBookCenterY, ForYouCardStackPlayerLayout.centerY, grow),
                leaf0: 1 - shut,
                leaf1: 1 - pinClose(ForYouCardStackClosePhase.leaf1),
                leaf2: 1 - pinClose(ForYouCardStackClosePhase.leaf2)
            )
        }
        let shrink = pinOpen(ForYouCardStackOpenPhase.shrink)
        return ForYouCardStackLook(
            neighbours: 1 - pinOpen(ForYouCardStackOpenPhase.neighbours),
            chapter: 1 - pinOpen(ForYouCardStackOpenPhase.chapter),
            wave: pinOpen(ForYouCardStackOpenPhase.wave),
            title: pinOpen(ForYouCardStackOpenPhase.title),
            text: pinOpen(ForYouCardStackOpenPhase.text),
            scale: mix(1, bookScale, shrink),
            spineX: mix(ForYouCardStackPlayerLayout.spineX, ForYouCardStackReadingLayout.readingBookSpineX, shrink),
            centerY: mix(ForYouCardStackPlayerLayout.centerY, ForYouCardStackReadingLayout.readingBookCenterY, shrink),
            leaf0: pinOpen(ForYouCardStackOpenPhase.leaf0),
            leaf1: pinOpen(ForYouCardStackOpenPhase.leaf1),
            leaf2: pinOpen(ForYouCardStackOpenPhase.leaf2)
        )
    }

    private static func interpolate(_ value: Double, input: [Double], output: [Double]) -> Double {
        guard let first = input.first, let last = input.last, input.count == output.count else { return 0 }
        if value <= first { return output[0] }
        if value >= last { return output[output.count - 1] }
        for index in 0..<(input.count - 1) {
            let start = input[index]
            let end = input[index + 1]
            if value <= end {
                let amount = (value - start) / (end - start)
                return output[index] + (output[index + 1] - output[index]) * amount
            }
        }
        return output.last ?? 0
    }

    private static func mix(_ a: Double, _ b: Double, _ amount: Double) -> Double {
        a + (b - a) * amount
    }

    private static func pin(_ progress: Double) -> Double {
        cubicBezier(progress, x1: 0.4, y1: 0, x2: 0.2, y2: 1)
    }

    private static func sheet(_ progress: Double) -> Double {
        cubicBezier(progress, x1: 0.32, y1: 0.72, x2: 0, y2: 1)
    }

    private static func cubicBezier(_ progress: Double, x1: Double, y1: Double, x2: Double, y2: Double) -> Double {
        let clamped = clamp01(progress)
        func sample(_ t: Double, _ a: Double, _ b: Double) -> Double {
            let inverse = 1 - t
            return 3 * inverse * inverse * t * a + 3 * inverse * t * t * b + t * t * t
        }
        var low = 0.0
        var high = 1.0
        for _ in 0..<14 {
            let middle = (low + high) / 2
            if sample(middle, x1, x2) < clamped { low = middle } else { high = middle }
        }
        return sample((low + high) / 2, y1, y2)
    }
}

struct ForYouCardStackLook {
    let neighbours: Double
    let chapter: Double
    let wave: Double
    let title: Double
    let text: Double
    let scale: Double
    let spineX: Double
    let centerY: Double
    let leaf0: Double
    let leaf1: Double
    let leaf2: Double
}

private enum ForYouCardStackOpenPhase {
    static let neighbours: ClosedRange<Double> = 0...0.133
    static let chapter: ClosedRange<Double> = 0.033...0.167
    static let shrink: ClosedRange<Double> = 0...0.333
    static let wave: ClosedRange<Double> = 0.067...0.4
    static let title: ClosedRange<Double> = 0.067...0.333
    static let leaf0: ClosedRange<Double> = 0.333...0.5
    static let leaf1: ClosedRange<Double> = 0.6...0.8
    static let leaf2: ClosedRange<Double> = 0.833...1
    static let text: ClosedRange<Double> = 0.333...0.5
}

private enum ForYouCardStackClosePhase {
    static let text: ClosedRange<Double> = 0...0.125
    static let wave: ClosedRange<Double> = 0...0.1875
    static let grow: ClosedRange<Double> = 0.0625...0.594
    static let leaf2: ClosedRange<Double> = 0.094...0.25
    static let leaf1: ClosedRange<Double> = 0.3125...0.5
    static let leaf0: ClosedRange<Double> = 0.625...0.9375
    static let title: ClosedRange<Double> = 0.406...0.656
    static let neighbours: ClosedRange<Double> = 0.3125...0.47
    static let chapter: ClosedRange<Double> = 0.406...0.656
}

private struct ForYouCardStackScalarTrack {
    private var from: Double
    private(set) var to: Double
    private var startedAt: Date
    private var duration: Double
    private var curve: ForYouCardStackCurve
    private var pendingValue: Double?
    private var pendingAt: Date?

    init(_ value: Double) {
        from = value
        to = value
        startedAt = .distantPast
        duration = 0
        curve = .linear
        pendingValue = nil
        pendingAt = nil
    }

    func value(at date: Date) -> Double {
        if let pendingAt, let pendingValue, date >= pendingAt {
            return pendingValue
        }
        guard duration > 0 else {
            return date < startedAt ? from : to
        }
        let elapsed = date.timeIntervalSince(startedAt)
        guard elapsed > 0 else { return from }
        guard elapsed < duration else { return to }
        let progress = min(max(elapsed / duration, 0), 1)
        let eased: Double
        switch curve {
        case .linear:
            eased = progress
        case .timingDefault:
            eased = progress < 0.5
                ? 2 * progress * progress
                : 1 - pow(-2 * progress + 2, 2) / 2
        case .pin:
            eased = ForYouCardStackScalarTrack.cubicBezier(progress, x1: 0.4, y1: 0, x2: 0.2, y2: 1)
        case .sheet:
            eased = ForYouCardStackScalarTrack.cubicBezier(progress, x1: 0.32, y1: 0.72, x2: 0, y2: 1)
        case .out:
            eased = ForYouCardStackScalarTrack.cubicBezier(progress, x1: 0.23, y1: 1, x2: 0.32, y2: 1)
        case let .spring(dampingRatio, velocity):
            eased = springProgress(progress, duration: duration, dampingRatio: dampingRatio, velocity: velocity, distance: to - from)
        }
        return from + (to - from) * eased
    }

    mutating func jump(to value: Double, at date: Date) {
        from = value
        to = value
        startedAt = date
        duration = 0
        curve = .linear
        pendingValue = nil
        pendingAt = nil
    }

    mutating func animate(to value: Double, at date: Date, duration: Double, curve: ForYouCardStackCurve, delay: Double = 0) {
        from = self.value(at: date)
        to = value
        startedAt = date.addingTimeInterval(delay)
        self.duration = max(0, duration)
        self.curve = curve
        pendingValue = nil
        pendingAt = nil
    }

    mutating func scheduleJump(to value: Double, at date: Date) {
        pendingValue = value
        pendingAt = date
    }

    private static func cubicBezier(_ progress: Double, x1: Double, y1: Double, x2: Double, y2: Double) -> Double {
        let clamped = min(max(progress, 0), 1)
        func sample(_ t: Double, _ a: Double, _ b: Double) -> Double {
            let inverse = 1 - t
            return 3 * inverse * inverse * t * a + 3 * inverse * t * t * b + t * t * t
        }
        var low = 0.0
        var high = 1.0
        for _ in 0..<14 {
            let middle = (low + high) / 2
            if sample(middle, x1, x2) < clamped { low = middle } else { high = middle }
        }
        return sample((low + high) / 2, y1, y2)
    }

    private func springProgress(_ progress: Double, duration: Double, dampingRatio: Double, velocity: Double, distance: Double) -> Double {
        let zeta = min(max(dampingRatio, 0.01), 0.999)
        let naturalFrequency = 4 / (zeta * max(duration, 0.001))
        let dampedFrequency = naturalFrequency * sqrt(1 - zeta * zeta)
        let normalizedVelocity = abs(distance) > 0.0001 ? velocity / distance : 0
        func raw(_ time: Double) -> Double {
            let decay = exp(-zeta * naturalFrequency * time)
            let sineCoefficient = (zeta * naturalFrequency - normalizedVelocity) / dampedFrequency
            return 1 - decay * (cos(dampedFrequency * time) + sineCoefficient * sin(dampedFrequency * time))
        }
        let end = raw(duration)
        guard abs(end) > 0.0001 else { return progress }
        return raw(progress * duration) / end
    }
}

private struct ForYouCardStackManualScene {
    var deck: ForYouCardStackScalarTrack
    var push: ForYouCardStackScalarTrack
    var playing: ForYouCardStackScalarTrack
    var open: ForYouCardStackScalarTrack
    var close: ForYouCardStackScalarTrack
    var words: ForYouCardStackScalarTrack

    init(scene: ForYouCardStackScene = .rest, at date: Date = .distantPast) {
        deck = ForYouCardStackScalarTrack(scene.deck)
        push = ForYouCardStackScalarTrack(scene.push)
        playing = ForYouCardStackScalarTrack(scene.playing)
        open = ForYouCardStackScalarTrack(scene.open)
        close = ForYouCardStackScalarTrack(scene.close)
        words = ForYouCardStackScalarTrack(scene.words)
        if date != .distantPast {
            var copy = self
            copy.deck.jump(to: scene.deck, at: date)
            copy.push.jump(to: scene.push, at: date)
            copy.playing.jump(to: scene.playing, at: date)
            copy.open.jump(to: scene.open, at: date)
            copy.close.jump(to: scene.close, at: date)
            copy.words.jump(to: scene.words, at: date)
            self = copy
        }
    }

    func sample(at date: Date) -> ForYouCardStackScene {
        ForYouCardStackScene(
            deck: deck.value(at: date),
            push: push.value(at: date),
            playing: playing.value(at: date),
            open: open.value(at: date),
            close: close.value(at: date),
            words: words.value(at: date)
        )
    }
}

struct ForYouCardStack: View {
    static let loop = ForYouCardStackMotion.loopMilliseconds / 1_000

    let autoPlay: Bool

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var autoplayStartedAt = Date()
    @State private var manualMode = false
    @State private var manualAnimating = false
    @State private var manualScene = ForYouCardStackManualScene()
    @State private var manualDuration = 0.0
    @State private var manualGeneration = 0
    @State private var resumeGeneration = 0
    @State private var manualResumeToAutoPlay = false
    @State private var dragStart = 0.0
    @State private var haptic = UIImpactFeedbackGenerator(style: .light)

    init(autoPlay: Bool = false) {
        self.autoPlay = autoPlay
        ForYouCardStackAssets.registerFonts()
        ForYouCardStackAssets.assertResourcesInDebug()
    }

    private var canAutoPlay: Bool { autoPlay && !reduceMotion }

    var body: some View {
        GeometryReader { proxy in
            let insets = proxy.safeAreaInsets
            let size = CGSize(
                width: proxy.size.width + insets.leading + insets.trailing,
                height: proxy.size.height + insets.top + insets.bottom
            )
            TimelineView(.animation(paused: (!canAutoPlay || manualMode) && !manualAnimating)) { timeline in
                content(size: size, date: timeline.date)
                    .frame(width: size.width, height: size.height, alignment: .topLeading)
            }
            .frame(width: size.width, height: size.height, alignment: .topLeading)
            .offset(x: -insets.leading, y: -insets.top)
        }
        .background(ForYouCardStackPalette.page.ignoresSafeArea())
        .ignoresSafeArea()
        .onAppear {
            autoplayStartedAt = .now
            manualMode = false
            manualAnimating = false
            haptic.prepare()
        }
        .task(id: manualGeneration) {
            guard manualAnimating else { return }
            let nanoseconds = UInt64(max(manualDuration, 0.01) * 1_000_000_000)
            try? await Task.sleep(nanoseconds: nanoseconds)
            guard !Task.isCancelled else { return }
            manualAnimating = false
            if manualResumeToAutoPlay && canAutoPlay {
                manualResumeToAutoPlay = false
                manualMode = false
                autoplayStartedAt = .now
            }
        }
        .task(id: resumeGeneration) {
            guard canAutoPlay, resumeGeneration > 0 else { return }
            try? await Task.sleep(nanoseconds: UInt64(ForYouCardStackMotion.resumeAfterMilliseconds * 1_000_000))
            guard !Task.isCancelled, canAutoPlay, manualMode else { return }
            returnToRest()
        }
        .onChange(of: canAutoPlay) { _, _ in
            resumeGeneration &+= 1
            manualResumeToAutoPlay = false
            autoplayStartedAt = .now
        }
    }

    private func content(size: CGSize, date: Date) -> some View {
        let scene = scene(at: date)
        let push = min(max(scene.push, 0), 1)
        let rowRect = CGRect(x: 0, y: ForYouCardStackHomeLayout.rowTop, width: size.width, height: ForYouCardStackHomeLayout.rowCoverHeight)
        let seeingRect = CGRect(
            x: scene.deck > 0.5
                ? ForYouCardStackHomeLayout.rowDeckLeft - ForYouCardStackHomeLayout.deckPeek + ForYouCardStackHomeLayout.deckPeek * CGFloat(scene.deck)
                : 0,
            y: ForYouCardStackHomeLayout.rowTop,
            width: scene.deck > 0.5 ? ForYouCardStackHomeLayout.rowCoverWidth : 0,
            height: scene.deck > 0.5 ? ForYouCardStackHomeLayout.rowCoverHeight : 0
        )

        return ZStack(alignment: .topLeading) {
            ForYouCardStackHomeView(size: size, deck: scene.deck, onOpenBook: openBook)
                .opacity(reduceMotion ? 1 - push : 1)
                .offset(x: reduceMotion ? 0 : -size.width * 0.3 * CGFloat(push))

            ForYouCardStackPalette.hex(0x000000)
                .opacity(reduceMotion ? 0 : 0.12 * push)
                .allowsHitTesting(false)

            ForYouCardStackPlayerView(
                size: size,
                scene: scene,
                reducedMotion: reduceMotion,
                onBack: back,
                onPlay: togglePlay
            )
            .offset(x: reduceMotion ? 0 : size.width * CGFloat(1 - push))
            .opacity(reduceMotion ? push : 1)
            .shadow(color: .black.opacity(0.18), radius: 18, x: -4, y: 0)
            .allowsHitTesting(push > 0.5)

            ForYouCardStackTouchView(
                rowRect: rowRect,
                seeingRect: seeingRect,
                onBegan: deckBegan,
                onChanged: deckChanged,
                onEnded: deckEnded
            )
            .frame(width: size.width, height: size.height, alignment: .topLeading)
            .allowsHitTesting(push < 0.5)
            .accessibilityHidden(true)
        }
        .frame(width: size.width, height: size.height, alignment: .topLeading)
        .clipped()
        .preferredColorScheme(push > 0.5 ? .dark : .light)
    }

    private func scene(at date: Date) -> ForYouCardStackScene {
        if manualMode {
            return manualScene.sample(at: date)
        }
        guard canAutoPlay else { return .rest }
        return ForYouCardStackScript.sample(at: date.timeIntervalSince(autoplayStartedAt) * 1_000)
    }

    private func beginManual(at date: Date) {
        let current = scene(at: date)
        manualScene = ForYouCardStackManualScene(scene: current, at: date)
        manualMode = true
        manualAnimating = false
        manualResumeToAutoPlay = false
        resumeGeneration &+= 1
    }

    private func startManualAnimation(duration: Double, resumeToAutoPlay: Bool = false) {
        manualDuration = max(duration, 0.01)
        manualResumeToAutoPlay = resumeToAutoPlay && canAutoPlay
        manualAnimating = true
        manualGeneration &+= 1
    }

    private func scheduleResume() {
        guard canAutoPlay else { return }
        resumeGeneration &+= 1
    }

    private func returnToRest() {
        guard canAutoPlay, manualMode else { return }
        let now = Date.now
        var next = manualScene
        next.deck.animate(to: 0, at: now, duration: ForYouCardStackMotion.pushMilliseconds / 1_000, curve: .out)
        next.playing.animate(to: 0, at: now, duration: 0.15, curve: .timingDefault)
        next.open.animate(to: 0, at: now, duration: 0.15, curve: .timingDefault)
        next.close.animate(to: 0, at: now, duration: 0.15, curve: .timingDefault)
        next.words.animate(to: 0, at: now, duration: 0.15, curve: .timingDefault)
        next.push.animate(to: 0, at: now, duration: ForYouCardStackMotion.pushMilliseconds / 1_000, curve: .sheet)
        manualScene = next
        startManualAnimation(duration: ForYouCardStackMotion.pushMilliseconds / 1_000, resumeToAutoPlay: canAutoPlay)
    }

    private func openBook() {
        let now = Date.now
        beginManual(at: now)
        var next = manualScene
        let duration = reduceMotion ? ForYouCardStackMotion.reducedFadeMilliseconds / 1_000 : ForYouCardStackMotion.pushMilliseconds / 1_000
        next.push.animate(to: 1, at: now, duration: duration, curve: .sheet)
        manualScene = next
        startManualAnimation(duration: duration)
        scheduleResume()
    }

    private func back() {
        let now = Date.now
        beginManual(at: now)
        var next = manualScene
        let pushDuration = reduceMotion ? ForYouCardStackMotion.reducedFadeMilliseconds / 1_000 : ForYouCardStackMotion.pushMilliseconds / 1_000
        next.playing.animate(to: 0, at: now, duration: 0.15, curve: .timingDefault)
        next.words.animate(to: 0, at: now, duration: 0, curve: .linear, delay: pushDuration)
        next.open.animate(to: 0, at: now, duration: 0, curve: .linear, delay: pushDuration)
        next.close.animate(to: 0, at: now, duration: 0, curve: .linear, delay: pushDuration)
        next.push.animate(to: 0, at: now, duration: pushDuration, curve: .sheet)
        manualScene = next
        startManualAnimation(duration: pushDuration)
        scheduleResume()
    }

    private func togglePlay() {
        let now = Date.now
        let current = scene(at: now)
        beginManual(at: now)
        haptic.impactOccurred()
        var next = manualScene
        let fadeDuration = 0.15
        if current.playing < 0.5 {
            next.playing.animate(to: 1, at: now, duration: fadeDuration, curve: .out)
            if current.close > 0 {
                next.close.jump(to: 0, at: now)
                next.open.jump(to: 0, at: now)
            }
            let openDuration = max(0, (1 - current.open) * ForYouCardStackMotion.playMilliseconds / 1_000)
            if reduceMotion {
                next.open.jump(to: 1, at: now)
                next.words.jump(to: ForYouCardStackMotion.readLines.last ?? 5.2, at: now)
            } else {
                next.open.animate(to: 1, at: now, duration: openDuration, curve: .timingDefault)
                next.words.jump(to: 0, at: now)
                next.words.animate(
                    to: ForYouCardStackMotion.readLines.last ?? 5.2,
                    at: now,
                    duration: ((ForYouCardStackMotion.readTimes.last ?? 8300) - (ForYouCardStackMotion.readTimes.first ?? 4000)) / 1_000,
                    curve: .timingDefault,
                    delay: ((ForYouCardStackMotion.readTimes.first ?? 4_000) - ForYouCardStackMotion.open.lowerBound) / 1_000
                )
            }
            manualScene = next
            let readDelay = ((ForYouCardStackMotion.readTimes.first ?? 4_000) - ForYouCardStackMotion.open.lowerBound) / 1_000
            let readDuration = ((ForYouCardStackMotion.readTimes.last ?? 8_300) - (ForYouCardStackMotion.readTimes.first ?? 4_000)) / 1_000
            startManualAnimation(duration: reduceMotion ? fadeDuration : max(openDuration, readDelay + readDuration))
        } else {
            next.playing.animate(to: 0, at: now, duration: fadeDuration, curve: .out)
            let openValue = current.open
            if openValue < 1 {
                let duration = openValue * ForYouCardStackMotion.playMilliseconds / 1_000
                if reduceMotion {
                    next.open.jump(to: 0, at: now)
                } else {
                    next.open.animate(to: 0, at: now, duration: duration, curve: .timingDefault)
                }
                next.words.jump(to: current.words, at: now)
                manualScene = next
                startManualAnimation(duration: max(duration, fadeDuration))
            } else {
                let closeDuration = reduceMotion ? ForYouCardStackMotion.reducedFadeMilliseconds / 1_000 : ForYouCardStackMotion.closeMilliseconds / 1_000
                if reduceMotion {
                    next.close.jump(to: 1, at: now)
                    next.open.jump(to: 0, at: now)
                    next.close.scheduleJump(to: 0, at: now.addingTimeInterval(closeDuration))
                    next.words.scheduleJump(to: 0, at: now.addingTimeInterval(closeDuration))
                } else {
                    next.close.animate(to: 1, at: now, duration: closeDuration, curve: .timingDefault)
                    next.open.animate(to: 0, at: now, duration: 0, curve: .linear, delay: closeDuration)
                    next.close.scheduleJump(to: 0, at: now.addingTimeInterval(closeDuration))
                    next.words.scheduleJump(to: 0, at: now.addingTimeInterval(closeDuration))
                }
                manualScene = next
                startManualAnimation(duration: closeDuration)
            }
        }
        scheduleResume()
    }

    private func deckBegan(_: CGPoint) {
        let now = Date.now
        beginManual(at: now)
        dragStart = manualScene.sample(at: now).deck
        var next = manualScene
        next.deck.jump(to: dragStart, at: now)
        manualScene = next
    }

    private func deckChanged(translation: CGFloat, _: CGPoint) {
        guard manualMode else { return }
        let now = Date.now
        let step = ForYouCardStackHomeLayout.rowCoverWidth + 11.3
        var value = dragStart + Double(translation) / step
        if value < 0 { value *= 0.3 }
        if value > 1 { value = 1 + (value - 1) * 0.3 }
        var next = manualScene
        next.deck.jump(to: value, at: now)
        manualScene = next
    }

    private func deckEnded(translation: CGFloat, velocity: CGFloat, _: CGPoint, completed: Bool) {
        guard manualMode else { return }
        guard completed else {
            scheduleResume()
            return
        }
        let now = Date.now
        let step = ForYouCardStackHomeLayout.rowCoverWidth + 11.3
        let current = manualScene.sample(at: now).deck
        let projected = current + (Double(velocity) * 0.2) / step
        let target = projected > 0.5 ? 1.0 : 0.0
        var next = manualScene
        if reduceMotion {
            next.deck.jump(to: target, at: now)
        } else {
            next.deck.animate(
                to: target,
                at: now,
                duration: 0.4,
                curve: .spring(dampingRatio: 0.8, velocity: Double(velocity) / step)
            )
        }
        manualScene = next
        if !reduceMotion { startManualAnimation(duration: 0.4) }
        if target != (dragStart.rounded()) {
            haptic.impactOccurred()
        }
        _ = translation
        scheduleResume()
    }
}

struct ForYouCardStackButtonStyle: ButtonStyle {
    let reduceMotion: Bool

    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .scaleEffect(!reduceMotion && configuration.isPressed ? ForYouCardStackMotion.pressScale : 1)
            .animation(
                reduceMotion
                    ? nil
                    : .easeOut(
                        duration: configuration.isPressed
                            ? ForYouCardStackMotion.pressInMilliseconds / 1_000
                            : ForYouCardStackMotion.pressOutMilliseconds / 1_000
                    ),
                value: configuration.isPressed
            )
    }
}

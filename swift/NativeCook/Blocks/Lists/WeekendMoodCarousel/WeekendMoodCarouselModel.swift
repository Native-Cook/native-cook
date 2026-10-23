import CoreText
import SwiftUI
import UIKit

enum WeekendMoodCarouselFaceKind: Int, CaseIterable {
    case sleepy
    case foodie
    case recovery
    case binge
    case shopping
    case scroll
    case social
}

struct WeekendMoodCarouselMood: Identifiable {
    let kind: WeekendMoodCarouselFaceKind
    let panel: UInt32
    let chip: (UInt32, UInt32, UInt32)
    let tick: (UInt32, UInt32)

    var id: Int { kind.rawValue }
}

struct WeekendMoodCarouselChip: Identifiable {
    let label: String
    let character: Int

    var id: String { label }
}

struct WeekendMoodCarouselStep {
    let chip: Int
    let dx: Double
    let character: Int
    let outAt: Double
    let outDuration: Double
    let moveAt: Double
    let moveDuration: Double
    let fillAt: Double
    let fillDuration: Double
}

struct WeekendMoodCarouselFrame {
    var chipX: Double
    var characterPosition: Double
    var fill: Double
    var selectedChip: Int
    var selectedCharacter: Int
    var previousCharacter: Int
    var age: Double
    var veil: Double
    var ink: Double
    var phase: Double

    static let initial = WeekendMoodCarouselFrame(
        chipX: 0,
        characterPosition: 0,
        fill: 1,
        selectedChip: WeekendMoodCarouselMetrics.chipSleepy,
        selectedCharacter: WeekendMoodCarouselFaceKind.sleepy.rawValue,
        previousCharacter: WeekendMoodCarouselFaceKind.social.rawValue,
        age: 1_000,
        veil: 0,
        ink: 1,
        phase: 0
    )
}

/// The five values which travel together while the finger has control. Canvas
/// reads a sampled value of these tracks on every TimelineView tick; SwiftUI's
/// implicit animation does not animate scalar values read inside Canvas.
struct WeekendMoodCarouselManualVisual {
    var chipX: Double
    var characterPosition: Double
    var fill: Double
    var ink: Double
    var veil: Double

    init(frame: WeekendMoodCarouselFrame) {
        chipX = frame.chipX
        characterPosition = frame.characterPosition
        fill = frame.fill
        ink = frame.ink
        veil = frame.veil
    }
}

enum WeekendMoodCarouselTrackCurve {
    case scroll
    case timing
    case spring
}

struct WeekendMoodCarouselScalarTrack {
    let from: Double
    let to: Double
    let startedAt: Date
    let duration: Double
    let curve: WeekendMoodCarouselTrackCurve
    /// Velocity normalised to the track's travel (`value / second / distance`).
    let initialVelocity: Double

    func value(at date: Date) -> Double {
        let elapsed = max(0, date.timeIntervalSince(startedAt) * 1_000)
        guard duration > 0 else { return to }
        let progress = min(elapsed / duration, 1)
        let eased: Double
        switch curve {
        case .scroll:
            eased = weekendCubicBezier(progress)
        case .timing:
            // Reanimated's default withTiming easing is in-out quadratic.
            eased = weekendInOutQuad(progress)
        case .spring:
            eased = weekendNativeSpring(progress, duration: duration, initialVelocity: initialVelocity)
        }
        return from + (to - from) * eased
    }
}

struct WeekendMoodCarouselManualTracks {
    var chipX: WeekendMoodCarouselScalarTrack?
    var characterPosition: WeekendMoodCarouselScalarTrack?
    var fill: WeekendMoodCarouselScalarTrack?
    var ink: WeekendMoodCarouselScalarTrack?
    var veil: WeekendMoodCarouselScalarTrack?

    func sample(_ base: WeekendMoodCarouselManualVisual, at date: Date) -> WeekendMoodCarouselManualVisual {
        WeekendMoodCarouselManualVisual(
            chipX: chipX?.value(at: date) ?? base.chipX,
            characterPosition: characterPosition?.value(at: date) ?? base.characterPosition,
            fill: fill?.value(at: date) ?? base.fill,
            ink: ink?.value(at: date) ?? base.ink,
            veil: veil?.value(at: date) ?? base.veil
        )
    }
}

extension WeekendMoodCarouselManualVisual {
    init(chipX: Double, characterPosition: Double, fill: Double, ink: Double, veil: Double) {
        self.chipX = chipX
        self.characterPosition = characterPosition
        self.fill = fill
        self.ink = ink
        self.veil = veil
    }
}

enum WeekendMoodCarouselAssets {
    static let titleBundled = "WeekendMoodCarousel-LiberationSerif-Bold"
    static let chipBundled = "WeekendMoodCarousel-SourceSans3-Medium"

    private static let registered: Void = {
        for name in [titleBundled, chipBundled] {
            guard let url = Bundle.main.url(forResource: name, withExtension: "ttf") else { continue }
            CTFontManagerRegisterFontsForURL(url as CFURL, .process, nil)
        }
    }()

    static func registerFonts() {
        _ = registered
    }

    static var titleFontName: String {
        UIFont(name: "TimesNewRomanPS-BoldMT", size: 1) == nil ? "LiberationSerif-Bold" : "TimesNewRomanPS-BoldMT"
    }

    static var chipFontName: String {
        UIFont(name: "SourceSans3-Medium", size: 1) == nil ? "SourceSans3-Medium" : "SourceSans3-Medium"
    }
}

enum WeekendMoodCarouselMetrics {
    static let screenWidth = 402.0
    static let pinHeight = 874.0
    static let panelHeight = 461.0
    static let panelRadius = 13.5

    static let ink: UInt32 = 0x101718
    static let white: UInt32 = 0xFFFFFF
    static let buttonInk: UInt32 = 0x16191A
    static let faceInk: UInt32 = 0x1C1B1F

    static let buttonTop = 77.0
    static let buttonSize = 54.0
    static let backLeft = 22.0
    static let saveRight = 20.0
    static let saveWidth = 84.0
    static let buttonRing = 0.67
    static let saveFont = 15.0
    static let arrowStroke = 1.7

    static let characterPositions = [0.0, 326.0, 614.0, 910.0, 1_182.0, 1_456.0, 1_794.0]
    static let characterBox = 400.0
    static let characterSpacing = 292.0

    static let titleTop = 511.3
    static let titleShiftX = -2.7
    static let titleFontSize = 47.0
    static let titleLineHeight = 45.0

    static let rulerTop = 626.0
    static let rulerHeight = 104.0
    static let rulerPitch = 12.25
    static let rulerTickWidth = 2.0
    static let rulerTickSoftness = 0.9
    static let rulerBaseline = 87.0
    static let rulerIndicatorTop = 6.7
    static let rulerIndicatorBottom = 95.7
    static let rulerIndicatorWidth = 2.5
    static let rulerHeights = [82.0, 77.3, 67.7, 59.7, 51.7, 44.7, 38.3, 33.3, 28.3, 22.7, 19.0, 16.0, 11.7, 7.3, 3.0, 1.0, 0.0]
    static let rulerAlphas = [0.30, 0.24, 0.23, 0.20, 0.19, 0.17, 0.15, 0.13, 0.11, 0.10, 0.08, 0.07, 0.06, 0.04, 0.03, 0.01, 0.01]

    static let chipsTop = 760.0
    static let chipsHeight = 49.0
    static let chipsCenterX = 201.0
    static let chipGap = 10.0
    static let chipMinWidth = 111.0
    static let chipPadding = 16.0
    static let chipBorder = 1.0
    static let chipFontSize = 16.4
    static let chipLetterSpacing = -0.4

    static let fillIn = 200.0
    static let fillOut = 170.0
    static let userScroll = 1_270.0
    static let snapSpringDuration = 400.0
    static let snapSpringDampingRatio = 0.8
    /// RN `RETURN_TO_REST_MS` (kept for the cross-stack fidelity matrix; the Swift resume uses the script timing).
    static let returnToRestMS = 650.0
    static let resumeAfter = 3_500.0
    static let reducedFade = 180.0
    static let loopMilliseconds = 13_800.0

    static let zzzTilt = -12.0
    static let zzzStagger = 110.0
    static let zzzPop = 220.0
    static let mouth = 200.0
    static let mouthClosed = 0.08
    static let droolDelay = 100.0
    static let drool = 200.0
    static let heartGain = 0.12
    static let heartPeriod = 1_150.0
    static let wingMinimum = 0.45
    static let wingPeriod = 165.0
    static let lidRest = 0.45
    static let lidSelected = 0.04

    static let chipSleepy = 2
    static let chipRecovery = 3
    static let chipFoodie = 5
    static let chipSocial = 9
    static let chipBinge = 12
    static let chipShopping = 15
    static let chipScroll = 18

    static let moods: [WeekendMoodCarouselMood] = [
        WeekendMoodCarouselMood(kind: .sleepy, panel: 0xF0E1B0, chip: (0xF6AE31, 0xEBA36E, 0xE09AA3), tick: (0xC2B4D8, 0xF4D27E)),
        WeekendMoodCarouselMood(kind: .foodie, panel: 0xBEDB98, chip: (0x6BB341, 0xADB443, 0xDBAD6E), tick: (0x6FAFDB, 0x82CC86)),
        WeekendMoodCarouselMood(kind: .recovery, panel: 0xFDBFB6, chip: (0xEB7F5F, 0xEB8551, 0xF1814F), tick: (0xCFB2E6, 0xF0D978)),
        WeekendMoodCarouselMood(kind: .binge, panel: 0xF6D0FB, chip: (0xC87CF2, 0xC487D0, 0xD39B9D), tick: (0xDE6573, 0xD6AFEA)),
        WeekendMoodCarouselMood(kind: .shopping, panel: 0xFDD7FC, chip: (0xC976EA, 0xD376BE, 0xE57A75), tick: (0xDAAEEF, 0xE4A3CB)),
        WeekendMoodCarouselMood(kind: .scroll, panel: 0xFDA4AB, chip: (0xDA735E, 0xDB7D4E, 0xEC8E18), tick: (0xDAAEEF, 0xD66476)),
        WeekendMoodCarouselMood(kind: .social, panel: 0xFDD2BA, chip: (0xEC7C66, 0xE8854D, 0xF69318), tick: (0xCEB0E8, 0x6FB1E6)),
    ]

    static let chips: [WeekendMoodCarouselChip] = [
        WeekendMoodCarouselChip(label: "Grateful", character: WeekendMoodCarouselFaceKind.recovery.rawValue),
        WeekendMoodCarouselChip(label: "Joyful", character: WeekendMoodCarouselFaceKind.binge.rawValue),
        WeekendMoodCarouselChip(label: "Sleepy", character: WeekendMoodCarouselFaceKind.sleepy.rawValue),
        WeekendMoodCarouselChip(label: "Recovery Mode", character: WeekendMoodCarouselFaceKind.recovery.rawValue),
        WeekendMoodCarouselChip(label: "Satisfied", character: WeekendMoodCarouselFaceKind.foodie.rawValue),
        WeekendMoodCarouselChip(label: "Foodie", character: WeekendMoodCarouselFaceKind.foodie.rawValue),
        WeekendMoodCarouselChip(label: "Reflective", character: WeekendMoodCarouselFaceKind.recovery.rawValue),
        WeekendMoodCarouselChip(label: "Thoughtful", character: WeekendMoodCarouselFaceKind.social.rawValue),
        WeekendMoodCarouselChip(label: "Nostalgic", character: WeekendMoodCarouselFaceKind.social.rawValue),
        WeekendMoodCarouselChip(label: "Social Butterfly", character: WeekendMoodCarouselFaceKind.social.rawValue),
        WeekendMoodCarouselChip(label: "Uncertain", character: WeekendMoodCarouselFaceKind.scroll.rawValue),
        WeekendMoodCarouselChip(label: "Melancholy", character: WeekendMoodCarouselFaceKind.sleepy.rawValue),
        WeekendMoodCarouselChip(label: "Binge-Watching", character: WeekendMoodCarouselFaceKind.binge.rawValue),
        WeekendMoodCarouselChip(label: "Disappointed", character: WeekendMoodCarouselFaceKind.scroll.rawValue),
        WeekendMoodCarouselChip(label: "Lonely", character: WeekendMoodCarouselFaceKind.shopping.rawValue),
        WeekendMoodCarouselChip(label: "Shopping", character: WeekendMoodCarouselFaceKind.shopping.rawValue),
        WeekendMoodCarouselChip(label: "Hopeless", character: WeekendMoodCarouselFaceKind.scroll.rawValue),
        WeekendMoodCarouselChip(label: "Heartbroken", character: WeekendMoodCarouselFaceKind.shopping.rawValue),
        WeekendMoodCarouselChip(label: "Scroll Mode", character: WeekendMoodCarouselFaceKind.scroll.rawValue),
        WeekendMoodCarouselChip(label: "Devastated", character: WeekendMoodCarouselFaceKind.sleepy.rawValue),
    ]

    static let chipOfCharacter = [chipSleepy, chipFoodie, chipRecovery, chipBinge, chipShopping, chipScroll, chipSocial]

    static let startChip = chipSleepy
    static let startDx = 2.8
    static let startCharacter = WeekendMoodCarouselFaceKind.sleepy.rawValue

    static let steps: [WeekendMoodCarouselStep] = [
        WeekendMoodCarouselStep(chip: chipFoodie, dx: -3.2, character: WeekendMoodCarouselFaceKind.foodie.rawValue, outAt: 400, outDuration: 170, moveAt: 500, moveDuration: 1_333, fillAt: 1_700, fillDuration: 200),
        WeekendMoodCarouselStep(chip: chipBinge, dx: -3.8, character: WeekendMoodCarouselFaceKind.binge.rawValue, outAt: 2_400, outDuration: 170, moveAt: 2_530, moveDuration: 1_267, fillAt: 3_730, fillDuration: 240),
        WeekendMoodCarouselStep(chip: chipShopping, dx: 0, character: WeekendMoodCarouselFaceKind.shopping.rawValue, outAt: 4_470, outDuration: 170, moveAt: 4_470, moveDuration: 1_267, fillAt: 5_600, fillDuration: 300),
        WeekendMoodCarouselStep(chip: chipScroll, dx: 1.3, character: WeekendMoodCarouselFaceKind.scroll.rawValue, outAt: 6_530, outDuration: 200, moveAt: 6_570, moveDuration: 1_200, fillAt: 7_600, fillDuration: 230),
        WeekendMoodCarouselStep(chip: chipSocial, dx: 2.2, character: WeekendMoodCarouselFaceKind.social.rawValue, outAt: 7_830, outDuration: 740, moveAt: 8_430, moveDuration: 1_633, fillAt: 9_970, fillDuration: 130),
        WeekendMoodCarouselStep(chip: chipRecovery, dx: -3.0, character: WeekendMoodCarouselFaceKind.recovery.rawValue, outAt: 10_570, outDuration: 100, moveAt: 10_570, moveDuration: 1_167, fillAt: 11_730, fillDuration: 170),
        WeekendMoodCarouselStep(chip: chipSleepy, dx: 2.8, character: WeekendMoodCarouselFaceKind.sleepy.rawValue, outAt: 12_450, outDuration: 170, moveAt: 12_500, moveDuration: 1_200, fillAt: 13_600, fillDuration: 200),
    ]

    static func chipWidths() -> [Double] {
        let font = UIFont(name: "SourceSans3-Medium", size: chipFontSize) ?? .systemFont(ofSize: chipFontSize)
        let attributes: [NSAttributedString.Key: Any] = [.font: font, .kern: chipLetterSpacing]
        return chips.map { chip in
            let textWidth = ceil((chip.label as NSString).size(withAttributes: attributes).width)
            return max(chipMinWidth, textWidth + chipPadding * 2)
        }
    }

    static func chipCenters() -> [Double] {
        var x = 0.0
        return chipWidths().map { width in
            defer { x += width + chipGap }
            return x + width / 2
        }
    }
}

enum WeekendMoodCarouselInterpolation {
    static func progress(_ value: Double, _ start: Double, _ end: Double) -> Double {
        guard end > start else { return value >= end ? 1 : 0 }
        return min(max((value - start) / (end - start), 0), 1)
    }

    static func value(_ value: Double, _ stops: [Double], _ values: [Double]) -> Double {
        guard let first = stops.first, let last = stops.last, !values.isEmpty else { return 0 }
        if value <= first { return values[0] }
        if value >= last { return values[values.count - 1] }
        for i in 0..<(stops.count - 1) where value <= stops[i + 1] {
            let p = (value - stops[i]) / (stops[i + 1] - stops[i])
            return values[i] + (values[i + 1] - values[i]) * p
        }
        return values[values.count - 1]
    }

    static func color(_ value: Double, _ stops: [Double], _ values: [UInt32]) -> Color {
        guard let first = stops.first, let last = stops.last, !values.isEmpty else { return Color(hex: 0) }
        if value <= first { return Color(hex: values[0]) }
        if value >= last { return Color(hex: values[values.count - 1]) }
        for i in 0..<(stops.count - 1) where value <= stops[i + 1] {
            let p = (value - stops[i]) / (stops[i + 1] - stops[i])
            return weekendBlendColor(values[i], values[i + 1], p)
        }
        return Color(hex: values[values.count - 1])
    }
}

func weekendClamp01(_ value: Double) -> Double {
    min(max(value, 0), 1)
}

func weekendBlendColor(_ a: UInt32, _ b: UInt32, _ amount: Double) -> Color {
    let t = weekendClamp01(amount)
    func channel(_ shift: UInt32) -> Double {
        let left = Double((a >> shift) & 0xff) / 255
        let right = Double((b >> shift) & 0xff) / 255
        // Reanimated's interpolateColor uses gamma-correct RGB blending with
        // gamma 2.2 by default. Keep the panel, ruler, cross and chip label
        // transitions in the same perceptual space as the approved port.
        let linearLeft = pow(left, 2.2)
        let linearRight = pow(right, 2.2)
        return pow(linearLeft + (linearRight - linearLeft) * t, 1 / 2.2)
    }
    return Color(.sRGB, red: channel(16), green: channel(8), blue: channel(0), opacity: 1)
}

func weekendInOutQuad(_ progress: Double) -> Double {
    let value = weekendClamp01(progress)
    if value < 0.5 { return 2 * value * value }
    let inverse = -2 * value + 2
    return 1 - inverse * inverse / 2
}

/// Reanimated's `{ duration: 400, dampingRatio: 0.8 }` spring expressed as
/// Swift samples. The native mapping uses mass 1, stiffness 156.25 and
/// damping 20 (natural frequency 12.5). The source's duration spring
/// normalises its damped response by the response at the declared endpoint;
/// doing the same keeps the last sampled frame continuous rather than
/// introducing a visible final snap.
func weekendNativeSpring(_ progress: Double, duration: Double, initialVelocity: Double) -> Double {
    let clamped = weekendClamp01(progress)
    guard clamped < 1 else { return 1 }
    let dampingRatio = WeekendMoodCarouselMetrics.snapSpringDampingRatio
    let naturalFrequency = 4 / (dampingRatio * duration / 1_000)
    let dampedFrequency = naturalFrequency * sqrt(1 - dampingRatio * dampingRatio)
    let sineFactor = (dampingRatio * naturalFrequency - initialVelocity) / dampedFrequency
    func response(_ time: Double) -> Double {
        1 - exp(-dampingRatio * naturalFrequency * time) * (
            cos(dampedFrequency * time) + sineFactor * sin(dampedFrequency * time)
        )
    }
    let end = response(duration / 1_000)
    guard abs(end) > 0.000_001 else { return clamped }
    return response(clamped * duration / 1_000) / end
}

func weekendCubicBezier(_ progress: Double, x1: Double = 0.3, y1: Double = 0.05, x2: Double = 0.4, y2: Double = 1) -> Double {
    let p = weekendClamp01(progress)
    func sample(_ t: Double, _ a: Double, _ b: Double) -> Double {
        let inverse = 1 - t
        return 3 * inverse * inverse * t * a + 3 * inverse * t * t * b + t * t * t
    }
    var low = 0.0
    var high = 1.0
    for _ in 0..<12 {
        let middle = (low + high) / 2
        if sample(middle, x1, x2) < p { low = middle } else { high = middle }
    }
    return sample((low + high) / 2, y1, y2)
}

func weekendSampleFrame(at milliseconds: Double, centers: [Double], reduced: Bool) -> WeekendMoodCarouselFrame {
    let metrics = WeekendMoodCarouselMetrics.self
    let t = ((milliseconds.truncatingRemainder(dividingBy: metrics.loopMilliseconds)) + metrics.loopMilliseconds)
        .truncatingRemainder(dividingBy: metrics.loopMilliseconds)
    let stops = Array(0..<metrics.moods.count).map(Double.init)
    var chipFrom = metrics.startChip
    var characterFrom = metrics.startCharacter
    var dxFrom = metrics.startDx
    var chipX = (centers[weekendSafe: metrics.startChip] ?? 0) - metrics.startDx
    var characterPosition = Double(metrics.startCharacter)
    var fill = 1.0
    var selectedChip = metrics.startChip
    var selectedCharacter = metrics.startCharacter
    var previousCharacter = WeekendMoodCarouselFaceKind.social.rawValue
    var age = t + (metrics.loopMilliseconds - (metrics.steps.last?.fillAt ?? 0))
    var veil = 0.0
    var ink = 1.0

    for step in metrics.steps {
        if t < step.outAt { break }
        fill = 1 - weekendClamp01((t - step.outAt) / step.outDuration)
        ink = 0
        if t >= step.moveAt {
            var progress = weekendCubicBezier(WeekendMoodCarouselInterpolation.progress(t, step.moveAt, step.moveAt + step.moveDuration))
            if reduced {
                let middle = step.moveAt + step.moveDuration / 2
                progress = t < middle ? 0 : 1
                veil = max(veil, 1 - weekendClamp01(abs(t - middle) / metrics.reducedFade))
            }
            let from = (centers[weekendSafe: chipFrom] ?? 0) - dxFrom
            let to = (centers[weekendSafe: step.chip] ?? 0) - step.dx
            chipX = from + (to - from) * progress
            characterPosition = Double(characterFrom) + Double(step.character - characterFrom) * progress
        }
        if t >= step.fillAt {
            fill = weekendClamp01((t - step.fillAt) / step.fillDuration)
            ink = fill
            previousCharacter = selectedCharacter
            selectedChip = step.chip
            selectedCharacter = step.character
            age = t - step.fillAt
        }
        chipFrom = step.chip
        dxFrom = step.dx
        characterFrom = step.character
    }

    return WeekendMoodCarouselFrame(
        chipX: chipX,
        characterPosition: characterPosition,
        fill: fill,
        selectedChip: selectedChip,
        selectedCharacter: selectedCharacter,
        previousCharacter: previousCharacter,
        age: age,
        veil: veil,
        ink: ink,
        phase: t
    )
}

extension Array {
    subscript(weekendSafe index: Int) -> Element? {
        indices.contains(index) ? self[index] : nil
    }
}

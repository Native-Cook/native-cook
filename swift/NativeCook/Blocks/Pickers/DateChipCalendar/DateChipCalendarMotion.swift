import SwiftUI
import UIKit

// Every number the calendar uses, ported from the React Native block's motion.ts and layout constants.
enum DateChipCalendarMotion {
    /// Seconds. One autoPlay loop; rest at both ends (0–0.55 and 8.22–10).
    static let loop: Double = 10
    /// autoPlay: 2 s after the last touch it eases to rest and restarts at t = 0.
    static let resumeAfter: Double = 2

    // The loop's events, in seconds.
    static let ghostIn = 0.55
    static let open = 1.0
    static let pick = 2.2
    static let dragStart = 2.55
    static let dragSpan = 1.1
    static let commit = 3.75
    static let rollSpan = 0.42
    static let done = 4.6
    static let fold = 4.65
    static let clear = 7.8
    static let rest = 8.22

    typealias Spring = (response: Double, ratio: Double)
    static let openSpring: Spring = (0.5, 0.82)
    static let foldSpring: Spring = (0.42, 0.92)
    static let capSpring: Spring = (0.32, 0.6)
    static let pageSpring: Spring = (0.38, 0.9)

    /// Cells rise on one 0–1 clock: a cell starts at diagonal / 11 × cellSpread and takes cellSpan.
    static let revealDelay = 0.12
    static let revealIn = 0.75
    static let revealOut = 0.3
    static let cellSpread = 0.55
    static let cellSpan = 0.45
    static let cellRise = 10.0

    static let dragDay = 0.1
    static let pageThreshold = 0.28
    static let pageVelocity = 500.0
    static let pageRubber = 0.3

    // Calendar card, in points.
    static let chipHeight = 68.0
    static let gridPad = 12.0
    static let titleRow = 40.0
    static let weekRow = 22.0
    static let dayRow = 40.0
    static let gridRows = 6
    static let gridBottom = 12.0
    static let openHeight = chipHeight + titleRow + weekRow + dayRow * Double(gridRows) + gridBottom
    static let gridTop = titleRow + weekRow
    static let bandInset = 2.0
    static let cap = 36.0
    static let line = 22.0
    static let radiusClosed = 20.0
    static let radiusOpen = 24.0

    // Screen, in points.
    static let side = 16.0
    static let header = 72.0
    static let artMin = 64.0
    static let artMax = 220.0
    static let gap = 12.0
    static let option = 58.0
    static let facts = 76.0
    static let note = 20.0
    static let bar = 76.0
    static let ghost = 44.0
    static let rate = 179
    static let breakfast = 18

    // Palette.
    static let ink = Color(hex: 0x16262E)
    static let mute = Color(hex: 0x6E7F86)
    static let card = Color(hex: 0xFDFCF8)
    static let tide = Color(hex: 0x1C6B7A)
    static let tideSoft = Color(hex: 0xCFE4E6)
    static let faint = Color.rgba(22, 38, 46, 0.26)
    static let chipFill = Color(hex: 0xEDF1EF)
    static let paper = Color(hex: 0xEEF2EF)
    static let pageFill = Color(hex: 0xE6ECE9)
    static let hairline = Color.rgba(22, 38, 46, 0.09)

    // MARK: Maths

    static func clamp01(_ x: Double) -> Double { x < 0 ? 0 : x > 1 ? 1 : x }
    static func mix(_ u: Double, _ a: Double, _ b: Double) -> Double { a + (b - a) * u }
    static func seg(_ t: Double, _ start: Double, _ span: Double) -> Double { clamp01((t - start) / span) }
    static func easeInOutCubic(_ x: Double) -> Double {
        x < 0.5 ? 4 * x * x * x : 1 - pow(-2 * x + 2, 3) / 2
    }
    static func easeInOutQuad(_ x: Double) -> Double {
        x < 0.5 ? 2 * x * x : 1 - pow(-2 * x + 2, 2) / 2
    }

    /// The closed-form step response of a spring from rest, so autoPlay is a pure function of t.
    static func springAt(_ t: Double, _ s: Spring) -> Double {
        if t <= 0 { return 0 }
        let w = 2 * Double.pi / s.response
        let wd = w * (1 - s.ratio * s.ratio).squareRoot()
        return 1 - exp(-s.ratio * w * t) * (cos(wd * t) + (s.ratio * w / wd) * sin(wd * t))
    }
}

/// One animated number, read as a pure function of time like a Reanimated shared value driven by
/// `withTiming` / `withSpring`. Springs keep the velocity of what they interrupt.
struct DateChipCalendarAnim {
    private enum Kind {
        case still
        case timing(duration: Double)
        case spring(w: Double, zeta: Double)
    }

    private var kind = Kind.still
    private var from: Double
    private var to: Double
    private var v0 = 0.0
    private var start = 0.0

    init(_ value: Double) {
        from = value
        to = value
    }

    var target: Double { to }

    static var now: Double { Date().timeIntervalSinceReferenceDate }

    func value(at time: Double) -> Double {
        let te = time - start
        if te <= 0 { return from }
        switch kind {
        case .still:
            return to
        case .timing(let duration):
            return from + (to - from) * DateChipCalendarMotion.easeInOutQuad(min(te / duration, 1))
        case .spring(let w, let zeta):
            return springValue(te, w, zeta)
        }
    }

    func velocity(at time: Double) -> Double {
        let te = time - start
        switch kind {
        case .still:
            return 0
        case .timing(let duration):
            if te <= 0 || te >= duration { return 0 }
            return (value(at: time + 0.001) - value(at: time - 0.001)) / 0.002
        case .spring(let w, let zeta):
            if te <= 0 { return v0 }
            return (springValue(te + 0.0005, w, zeta) - springValue(max(0, te - 0.0005), w, zeta)) / (te > 0.0005 ? 0.001 : 0.0005 + te)
        }
    }

    func done(at time: Double) -> Bool {
        let te = time - start
        switch kind {
        case .still:
            return true
        case .timing(let duration):
            return te >= duration
        case .spring(let w, let zeta):
            let wd = w * (1 - zeta * zeta).squareRoot()
            let a = from - to
            let b = (v0 + zeta * w * a) / wd
            return exp(-zeta * w * max(te, 0)) * (a * a + b * b).squareRoot() < max(0.001, abs(a) * 0.0015)
        }
    }

    private func springValue(_ te: Double, _ w: Double, _ zeta: Double) -> Double {
        let wd = w * (1 - zeta * zeta).squareRoot()
        let a = from - to
        let b = (v0 + zeta * w * a) / wd
        return to + exp(-zeta * w * te) * (a * cos(wd * te) + b * sin(wd * te))
    }

    mutating func set(_ value: Double) {
        kind = .still
        from = value
        to = value
        v0 = 0
    }

    mutating func timing(_ target: Double, _ duration: Double, at time: Double = now) {
        from = value(at: time)
        to = target
        v0 = 0
        start = time
        kind = .timing(duration: max(duration, 0.0001))
    }

    mutating func spring(_ target: Double, _ s: DateChipCalendarMotion.Spring, velocity: Double? = nil, at time: Double = now) {
        let inherited = velocity ?? self.velocity(at: time)
        from = value(at: time)
        to = target
        v0 = inherited
        start = time
        kind = .spring(w: 2 * Double.pi / s.response, zeta: s.ratio)
    }
}

enum DateChipCalendarHaptics {
    static func select() {
        UISelectionFeedbackGenerator().selectionChanged()
    }
}

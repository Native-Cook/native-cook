import SwiftUI
import UIKit

// Every number the morph uses, ported from the React Native block's motion.ts and layout constants.
enum QuickAddMorphMotion {
    /// Seconds. One autoPlay loop; rest at both ends (0–0.62 and 8.05–9).
    static let loop: Double = 9
    /// autoPlay: 2 s after the last touch it eases to rest and restarts at t = 0.
    static let resumeAfter: Double = 2

    // The loop's events, in seconds.
    static let ghostIn = 0.62
    static let open = 1.0
    static let typeStart = 1.9
    static let typeStep = 0.07
    static let priority = 3.3
    static let list = 3.8
    static let add = 4.4
    static let fly = 4.78
    static let flySpan = 0.62
    static let check = 7.0
    static let checkSpan = 0.3
    static let remove = 7.55
    static let removeSpan = 0.5
    static let rest = 8.05

    /// Springs as (response, damping ratio).
    typealias Spring = (response: Double, ratio: Double)
    static let widthSpring: Spring = (0.46, 0.74)
    static let heightSpring: Spring = (0.52, 0.78)
    static let heightDelay = 0.07
    static let collapseSpring: Spring = (0.36, 0.9)
    static let partSpring: Spring = (0.42, 0.76)
    static let doneSpring: Spring = (0.34, 0.82)
    static let chipSpring: Spring = (0.3, 0.8)
    static let partDelay = 0.16
    static let landDelay = 0.46
    static let landSpan = 0.16
    static let fabDelay = 0.3
    static let fabSpan = 0.36
    static let contentDelay = 0.24
    static let contentIn = 0.42
    static let contentOut = 0.14
    static let contentStagger: [Double] = [0, 0.14, 0.28]
    static let contentItem = 0.72
    static let contentRise = 10.0

    static let plusTurn = 135.0
    static let flyArc = 22.0
    static let dragCloseDistance = 90.0
    static let dragCloseVelocity = 700.0
    static let dragUpRubber = 0.15

    static let radiusFab = 30.0
    static let radiusCard = 26.0
    static let radiusPill = 22.0
    static let radiusRow = 16.0

    // Screen layout, in points.
    static let side = 16.0
    static let header = 118.0
    static let row = 64.0
    static let rowHeight = 56.0
    static let insert = 1
    static let fab = 60.0
    static let fabSide = 20.0
    static let fabBottom = 12.0
    static let pillWidth = 188.0
    static let pillHeight = 44.0
    static let cardMax = 440.0
    static let checkSize = 26.0
    static let checkLeft = 14.0
    static let ghost = 44.0

    // Inside the card, in points.
    static let cardHeight = 170.0
    static let pad = 16.0
    static let fieldTop = 14.0
    static let fieldHeight = 40.0
    static let chipsTop = 62.0
    static let chipHeight = 34.0
    static let chipGap = 8.0
    static let chipWidths: [Double] = [72, 88, 92]
    static let addTop = 108.0
    static let addHeight = 48.0
    static let icon = 24.0
    static let iconInset = 18.0
    static let bar = 16.0
    static let labelLine = 18.0

    // Palette.
    static let ink = QuickAddMorphRGB(0x211E1A)
    static let card = QuickAddMorphRGB(0xFFFDF9)
    static let paper = QuickAddMorphRGB(0xF3EFE7)
    static let accent = QuickAddMorphRGB(0x1F6F5C)
    static let mute = QuickAddMorphRGB(0x857D71)
    static let amber = QuickAddMorphRGB(0xC9772A)
    static let fill = QuickAddMorphRGB(0xF1ECE3)
    static let cream = QuickAddMorphRGB(0xF7F3EA)

    // MARK: Maths

    static func clamp01(_ x: Double) -> Double { x < 0 ? 0 : x > 1 ? 1 : x }
    static func mix(_ u: Double, _ a: Double, _ b: Double) -> Double { a + (b - a) * u }
    static func seg(_ t: Double, _ start: Double, _ span: Double) -> Double { clamp01((t - start) / span) }
    static func easeOutCubic(_ x: Double) -> Double { let u = 1 - x; return 1 - u * u * u }
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

/// An sRGB colour mixed the way Reanimated's `interpolateColor` does (gamma 2.2).
struct QuickAddMorphRGB {
    var r: Double, g: Double, b: Double, a: Double

    init(_ hex: UInt32, alpha: Double = 1) {
        r = Double((hex >> 16) & 0xff) / 255
        g = Double((hex >> 8) & 0xff) / 255
        b = Double(hex & 0xff) / 255
        a = alpha
    }

    init(r: Double, g: Double, b: Double, a: Double) {
        self.r = r; self.g = g; self.b = b; self.a = a
    }

    var color: Color { Color(.sRGB, red: r, green: g, blue: b, opacity: a) }

    func mixed(_ other: QuickAddMorphRGB, _ u: Double) -> QuickAddMorphRGB {
        let k = QuickAddMorphMotion.clamp01(u)
        func ch(_ x: Double, _ y: Double) -> Double {
            pow(pow(x, 2.2) * (1 - k) + pow(y, 2.2) * k, 1 / 2.2)
        }
        return QuickAddMorphRGB(r: ch(r, other.r), g: ch(g, other.g), b: ch(b, other.b), a: a + (other.a - a) * k)
    }

    /// `interpolateColor(x, [lo, hi], [self, other])`, clamped.
    func mixed(_ other: QuickAddMorphRGB, at x: Double, from lo: Double, to hi: Double) -> QuickAddMorphRGB {
        mixed(other, (x - lo) / (hi - lo))
    }
}

/// One animated number, read as a pure function of time like a Reanimated shared value driven by
/// `withTiming` / `withSpring` / `withDelay`. Springs keep the velocity of what they interrupt.
struct QuickAddMorphAnim {
    private enum Kind {
        case still
        case timing(duration: Double)
        case spring(w: Double, zeta: Double, clamp: Bool)
    }

    private var kind = Kind.still
    private var from: Double
    private var to: Double
    private var v0 = 0.0
    private var start = 0.0
    private var delay = 0.0
    private var cross = Double.infinity // first time a clamped spring reaches its goal

    init(_ value: Double) {
        from = value
        to = value
    }

    var target: Double { to }

    static var now: Double { Date().timeIntervalSinceReferenceDate }

    func value(at time: Double) -> Double {
        let te = time - start - delay
        if te <= 0 { return from }
        switch kind {
        case .still:
            return to
        case .timing(let duration):
            let u = min(te / duration, 1)
            return from + (to - from) * QuickAddMorphMotion.easeInOutQuad(u)
        case .spring(let w, let zeta, let clamp):
            if clamp, te >= cross { return to }
            return springValue(te, w, zeta)
        }
    }

    func velocity(at time: Double) -> Double {
        let te = time - start - delay
        if te <= 0 { return 0 }
        switch kind {
        case .still:
            return 0
        case .timing(let duration):
            if te >= duration { return 0 }
            let h = 0.001
            return (value(at: time + h) - value(at: time - h)) / (2 * h)
        case .spring(let w, let zeta, let clamp):
            if clamp, te >= cross { return 0 }
            let h = 0.0005
            return (springValue(te + h, w, zeta) - springValue(max(0, te - h), w, zeta)) / (te > h ? 2 * h : h + te)
        }
    }

    func done(at time: Double) -> Bool {
        let te = time - start - delay
        switch kind {
        case .still:
            return true
        case .timing(let duration):
            return te >= duration
        case .spring(let w, let zeta, let clamp):
            if te <= 0 { return false }
            if clamp, te >= cross { return true }
            let wd = w * (1 - zeta * zeta).squareRoot()
            let a = from - to
            let b = (v0 + zeta * w * a) / wd
            let amp = exp(-zeta * w * te) * (a * a + b * b).squareRoot()
            let eps = max(0.001, abs(a) * 0.0015)
            return amp < eps
        }
    }

    private func springValue(_ te: Double, _ w: Double, _ zeta: Double) -> Double {
        let wd = w * (1 - zeta * zeta).squareRoot()
        let a = from - to
        let b = (v0 + zeta * w * a) / wd
        return to + exp(-zeta * w * te) * (a * cos(wd * te) + b * sin(wd * te))
    }

    /// Jumps to a value at once (`value.set(x)`).
    mutating func set(_ value: Double) {
        kind = .still
        from = value
        to = value
        v0 = 0
        delay = 0
    }

    mutating func timing(_ target: Double, _ duration: Double, delay: Double = 0, at time: Double = now) {
        let current = value(at: time)
        from = current
        to = target
        v0 = 0
        start = time
        self.delay = delay
        kind = duration <= 0 ? .still : .timing(duration: duration)
        if duration <= 0 { from = target }
    }

    mutating func spring(_ target: Double, _ s: QuickAddMorphMotion.Spring, velocity: Double? = nil,
                         clamp: Bool = false, delay: Double = 0, at time: Double = now) {
        let current = value(at: time)
        let inherited = velocity ?? (delay > 0 ? 0 : self.velocity(at: time))
        from = current
        to = target
        v0 = inherited
        start = time
        self.delay = delay
        let w = 2 * Double.pi / s.response
        kind = .spring(w: w, zeta: s.ratio, clamp: clamp)
        cross = .infinity
        if clamp, abs(target - current) > 1e-6 {
            // The first time the closed form reaches the goal; the clamped spring stops there.
            var t = 0.0
            let sign = target > current ? 1.0 : -1.0
            while t < 3 {
                t += 0.001
                if (springValue(t, w, s.ratio) - target) * sign >= 0 { cross = t; break }
            }
        } else if clamp {
            cross = 0
        }
    }
}

enum QuickAddMorphHaptics {
    static func light() {
        UIImpactFeedbackGenerator(style: .light).impactOccurred()
    }
}

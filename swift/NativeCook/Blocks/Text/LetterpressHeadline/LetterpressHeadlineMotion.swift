import Foundation

/// The Letterpress Headline's clock. Every value is a closed-form function of the loop time t
/// (seconds), so t = loop draws the frame of t = 0.
///
///   0.0 – 1.4   rest: blank cotton paper.
///   1.4 – 4.0   the headline is printed letter by letter, one every 0.21 s; each letter's metal
///               type block comes down (0.2 s, ease-in-out: it shrinks toward the paper and its
///               shadow tightens), presses (0.1 s: the deboss and the ink go in with a 6 %
///               overshoot) and lifts away (0.2 s, ease-out, fading).
///   4.0 – 4.9   an ink roller sweeps the second line on, left to right (ease-in-out).
///   4.8 – 6.0   the lamp swings across and back, so the deboss's light and shade move.
///   6.0 – 6.8   hold.
///   6.8 – 8.2   the impression and the ink fade out (ease-in-out), back to blank paper.
///   8.2 – 9.0   rest.
enum LetterpressHeadlineMotion {
    static let loop = 9.0

    /// Letters printed at most (the headline "Made by hand." has 11).
    static let maxGlyphs = 12
    private static let first = 1.4
    private static let every = 0.21
    private static let down = 0.2
    private static let press = 0.1
    private static let up = 0.2

    static let lightAz = -2.2
    static let lightEl = 0.75

    static func clamp01(_ x: Double) -> Double { min(1, max(0, x)) }
    static func seg(_ t: Double, _ a: Double, _ b: Double) -> Double { clamp01((t - a) / (b - a)) }

    static func easeOut(_ x: Double) -> Double {
        let u = 1 - clamp01(x)
        return 1 - u * u * u
    }

    static func easeInOut(_ x: Double) -> Double {
        let u = clamp01(x)
        return u < 0.5 ? 4 * u * u * u : 1 - pow(-2 * u + 2, 3) / 2
    }

    static func rubber(_ x: Double, _ limit: Double) -> Double {
        let s: Double = x < 0 ? -1 : 1
        return s * limit * (1 - 1 / (1 + abs(x) / limit))
    }

    /// One letter's print at loop time t.
    private static func letter(_ i: Int, _ t: Double) -> (depth: Double, block: Double, falling: Bool) {
        let s = first + Double(i) * every
        let dn = easeInOut(seg(t, s, s + down))
        let pressed = seg(t, s + down, s + down + press)
        let lift = easeOut(seg(t, s + down + press, s + down + press + up))
        // The deboss goes in with the press, overshooting a little as the paper gives, then settles.
        let depth = t < s + down ? 0 : easeOut(pressed) + 0.06 * sin(.pi * pressed)
        return (depth, dn * (1 - lift), lift == 0)
    }

    static func loopPose(_ t: Double, count: Int) -> LetterpressHeadlinePose {
        let fade = 1 - easeInOut(seg(t, 6.8, 8.2))
        var p = LetterpressHeadlinePose()
        for i in 0..<min(count, maxGlyphs) {
            let l = letter(i, t)
            p.depth[i] = l.depth * fade
            p.block[i] = l.block
            p.falling[i] = l.falling
        }
        let roll = easeInOut(seg(t, 4.0, 4.9))
        let swing = sin(.pi * easeInOut(seg(t, 4.8, 6.0)))
        p.roller = roll
        p.rollerAlpha = seg(t, 3.9, 4.05) * (1 - seg(t, 4.85, 5.0))
        p.ink = t >= 4.0 ? roll : 0
        p.inkAlpha = fade
        p.light = lightAz + 1.5 * swing
        p.elevation = lightEl - 0.2 * swing
        return p
    }

    /// Printed and still (reduced motion).
    static func printedPose(count: Int) -> LetterpressHeadlinePose {
        var p = LetterpressHeadlinePose()
        for i in 0..<min(count, maxGlyphs) { p.depth[i] = 1 }
        p.roller = 1
        p.ink = 1
        return p
    }
}

struct LetterpressHeadlinePose {
    /// Each letter's impression (0 … 1, a little more at the moment of pressing).
    var depth = [Double](repeating: 0, count: LetterpressHeadlineMotion.maxGlyphs)
    /// Each letter's type block: 0 away … 1 on the paper.
    var block = [Double](repeating: 0, count: LetterpressHeadlineMotion.maxGlyphs)
    /// True while a block is coming down (false: lifting away, which also fades it).
    var falling = [Bool](repeating: true, count: LetterpressHeadlineMotion.maxGlyphs)
    /// The second line: roller position (0 … 1 across), ink shown up to it, the ink's opacity.
    var roller = 0.0
    var rollerAlpha = 0.0
    var ink = 0.0
    var inkAlpha = 1.0
    /// The lamp.
    var light = LetterpressHeadlineMotion.lightAz
    var elevation = LetterpressHeadlineMotion.lightEl
}

/// A cubic Bézier easing curve, like Reanimated's `Easing.bezier`.
struct LetterpressHeadlineCurve {
    let x1: Double, y1: Double, x2: Double, y2: Double

    static let easeOut = LetterpressHeadlineCurve(x1: 0.23, y1: 1, x2: 0.32, y2: 1)
    static let easeInOut = LetterpressHeadlineCurve(x1: 0.77, y1: 0, x2: 0.175, y2: 1)

    func callAsFunction(_ x: Double) -> Double {
        let u = min(1, max(0, x))
        var s = u
        for _ in 0..<8 {
            let xs = bez(s, x1, x2) - u
            let d = dbez(s, x1, x2)
            if abs(xs) < 1e-5 || abs(d) < 1e-6 { break }
            s = min(1, max(0, s - xs / d))
        }
        return bez(s, y1, y2)
    }

    private func bez(_ s: Double, _ a: Double, _ b: Double) -> Double {
        let m = 1 - s
        return 3 * m * m * s * a + 3 * m * s * s * b + s * s * s
    }

    private func dbez(_ s: Double, _ a: Double, _ b: Double) -> Double {
        let m = 1 - s
        return 3 * m * m * a + 6 * m * s * (b - a) + 3 * s * s * (1 - b)
    }
}

/// One value the finger drives, as Reanimated's `withTiming` / `withSpring` would: a closed-form
/// function of the time it was set.
struct LetterpressHeadlineTrack {
    private var from: Double
    private var to: Double
    private var start = 0.0
    private var duration = 0.0
    private var curve = LetterpressHeadlineCurve.easeInOut
    /// A spring instead of a timing: ω, ζ and the start velocity.
    private var spring: (omega: Double, zeta: Double, velocity: Double)?

    init(_ value: Double) {
        from = value
        to = value
    }

    func value(at t: Double) -> Double {
        let u = t - start
        if let spring {
            if u <= 0 { return from }
            let a = from - to
            let z = min(spring.zeta, 0.999)
            let wd = spring.omega * (1 - z * z).squareRoot()
            let b = (spring.velocity + z * spring.omega * a) / wd
            return to + exp(-z * spring.omega * u) * (a * cos(wd * u) + b * sin(wd * u))
        }
        if duration <= 0 || u >= duration { return to }
        if u <= 0 { return from }
        return from + (to - from) * curve(u / duration)
    }

    func done(at t: Double) -> Bool {
        if let spring { return t - start >= 6 / (max(spring.zeta, 0.1) * spring.omega) }
        return t - start >= duration
    }

    mutating func set(_ value: Double) {
        from = value
        to = value
        duration = 0
        spring = nil
    }

    mutating func timing(_ target: Double, duration: Double, curve: LetterpressHeadlineCurve, now: Double) {
        from = value(at: now)
        to = target
        start = now
        self.duration = duration
        self.curve = curve
        spring = nil
    }

    /// Reanimated's duration-based spring, approximated by ω = 6.05 / duration.
    mutating func spring(_ target: Double, duration: Double, dampingRatio: Double, velocity: Double = 0, now: Double) {
        from = value(at: now)
        to = target
        start = now
        spring = (6.05 / duration, dampingRatio, velocity)
    }
}

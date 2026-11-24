// Split Amount Pad — the loop, the springs and the amount's rules, as named numbers and pure functions.
//
// The script below is the whole 9 s loop (the ghost finger presses the same keys a finger would, at
// these times), and the functions are the amount's arithmetic.

import Foundation

enum SplitAmountPadMotion {
    static let loop: Double = 9

    /// key codes: 0…9 the digits, then
    static let keyDot = 10
    static let keyBack = 11
    static let keyClear = 12

    /// most a bill may be, in cents
    static let limitCents = 250_000
    static let maxIntDigits = 4
    static let maxDecimals = 2
    static let friends = 4

    enum Act { case key, friend, clear }
    /// time (s), kind, argument, the time the ghost finger lifts
    struct Row {
        let at: Double
        let act: Act
        let arg: Int
        let lift: Double
    }

    static let script: [Row] = [
        Row(at: 0.7, act: .key, arg: 1, lift: 0.7),
        Row(at: 1.02, act: .key, arg: 2, lift: 1.02),
        Row(at: 1.34, act: .key, arg: 8, lift: 1.34),
        Row(at: 1.72, act: .key, arg: keyDot, lift: 1.72),
        Row(at: 2.04, act: .key, arg: 4, lift: 2.04),
        Row(at: 2.36, act: .key, arg: 0, lift: 2.36),
        Row(at: 4.1, act: .friend, arg: 2, lift: 4.1),
        Row(at: 5.9, act: .key, arg: keyBack, lift: 6.32),
        // fired by the long press the row before it began: no finger move of its own
        Row(at: 6.32, act: .clear, arg: 0, lift: 6.32),
        Row(at: 6.95, act: .friend, arg: 2, lift: 6.95),
    ]

    /// a touch hands the pad back this long after it ends; then the amount clears and the loop restarts
    static let idle: Double = 2
    static let returnTime: Double = 0.7
    /// a held backspace clears the amount after this long
    static let hold: Double = 0.42

    /// springs: stiffness, damping, mass 1
    struct Spring {
        let stiffness: Double
        let damping: Double
    }
    /// a digit dropping in: response 0.39 s, damping ratio 0.53
    static let drop = Spring(stiffness: 260, damping: 17)
    /// the amount re-centring: response 0.34 s, damping ratio 0.81
    static let slide = Spring(stiffness: 340, damping: 30)
    /// a share's digit rolling: response 0.42 s, damping ratio 0.81
    static let roll = Spring(stiffness: 220, damping: 24)
    /// an avatar toggling
    static let toggle = Spring(stiffness: 300, damping: 20)

    /// timings, seconds
    static let keyDown = 0.07
    static let keyUp = 0.17
    /// a digit flicked away
    static let flick = 0.23
    /// between digits leaving together (a clear), last first
    static let flickStagger = 0.028
    /// between digits arriving together ("0" and "." from one press)
    static let dropStagger = 0.045
    /// between friends' shares starting to roll, and between a share's digits (cents first)
    static let rollFriend = 0.045
    static let rollPlace = 0.018
    /// one leg of the shake
    static let shakeLeg = 0.055

    /// a dropping digit starts this far above its place, this small and turned up to this much
    static let dropRise = -24.0, dropScale = 0.6, dropTurnMin = 6, dropTurnSpan = 7
    /// a flicked digit ends this far to the right, having risen and fallen, turned and shrunk
    static let flickX = 26.0, flickUp = -34.0, flickFall = 60.0, flickTurn = 38.0, flickShrink = 0.35
    /// the shake's legs, pt: over the limit, and a softer "no" for a key that does nothing
    static let shakeHard: [Double] = [-11, 9, -6, 3, 0]
    static let shakeSoft: [Double] = [-5, 4, -2, 0]

    static func clamp01(_ v: Double) -> Double { v < 0 ? 0 : v > 1 ? 1 : v }
    /// 0 before a, 1 after b
    static func seg(_ t: Double, _ a: Double, _ b: Double) -> Double { clamp01((t - a) / (b - a)) }
    static func smooth(_ v: Double) -> Double { v * v * (3 - 2 * v) }
    /// Reanimated's default timing curve
    static func inOutQuad(_ v: Double) -> Double { v < 0.5 ? 2 * v * v : 1 - 2 * (1 - v) * (1 - v) }
    static func outQuad(_ v: Double) -> Double { 1 - (1 - v) * (1 - v) }

    /// where the dot is in the typed codes, or -1
    static func dotAt(_ codes: [Int]) -> Int { codes.firstIndex(of: keyDot) ?? -1 }

    /// the typed codes as cents
    static func centsOf(_ codes: [Int]) -> Int {
        let dot = dotAt(codes)
        let whole = dot < 0 ? codes.count : dot
        var cents = 0
        for i in 0..<whole { cents = cents * 10 + codes[i] }
        cents *= 100
        if dot >= 0 {
            if dot + 1 < codes.count { cents += codes[dot + 1] * 10 }
            if dot + 2 < codes.count { cents += codes[dot + 2] }
        }
        return cents
    }

    /// split cents between the friends that are in: the first ones take the odd cents; -1 for one who sits out
    static func splitShares(_ cents: Int, _ active: [Int]) -> [Int] {
        let count = active.filter { $0 > 0 }.count
        let base = count > 0 ? cents / count : 0
        var odd = count > 0 ? cents - base * count : 0
        return active.map { on in
            guard on > 0 else { return -1 }
            let share = base + (odd > 0 ? 1 : 0)
            if odd > 0 { odd -= 1 }
            return share
        }
    }

    /// which pad cell a key code sits in (rows of three: 1 2 3 / 4 5 6 / 7 8 9 / . 0 back)
    static func cellOf(_ code: Int) -> Int {
        if code == 0 { return 10 }
        if code == keyDot { return 9 }
        if code == keyBack { return 11 }
        return code - 1
    }

    /// which script row the ghost finger is heading for or pressing at t (clear rows are skipped)
    static func ghostRow(_ t: Double) -> Int {
        var last = 0
        for (i, row) in script.enumerated() where row.act != .clear {
            last = i
            if t < row.lift + 0.3 { return i }
        }
        return last
    }

    /// the row before it that has a finger move, or itself
    static func ghostPrev(_ row: Int) -> Int {
        var i = row - 1
        while i >= 0 {
            if script[i].act != .clear { return i }
            i -= 1
        }
        return row
    }

    /// how far the ghost has glided from the previous row's target to this one's, 0…1
    static func ghostGlide(_ t: Double, _ row: Int) -> Double {
        let at = script[row].at
        return smooth(seg(t, at - 0.27, at - 0.05))
    }

    static func ghostAlpha(_ t: Double) -> Double {
        var a = 0.0
        for row in script where row.act != .clear {
            let v = smooth(seg(t, row.at - 0.32, row.at - 0.12)) * (1 - smooth(seg(t, row.lift + 0.12, row.lift + 0.36)))
            if v > a { a = v }
        }
        return a
    }

    /// how hard the ghost presses a row at t, 0…1
    static func ghostPress(_ t: Double, _ row: Int) -> Double {
        let r = script[row]
        return smooth(seg(t, r.at - 0.07, r.at)) * (1 - smooth(seg(t, r.lift + 0.03, r.lift + 0.19)))
    }
}

/// One number on its way somewhere, in closed form: a spring (with the velocity it had when it was
/// retargeted), a timed ease, or at rest. Read at any clock time, so nothing is integrated per frame.
struct SplitAmountPadValue {
    private enum Kind {
        case rest
        case spring(SplitAmountPadMotion.Spring)
        case timing(Double, (Double) -> Double)
    }

    private var kind = Kind.rest
    private var from: Double
    private var to: Double
    private var velocity = 0.0
    private var start = 0.0

    init(_ value: Double) {
        from = value
        to = value
    }

    var target: Double { to }

    func value(at now: Double) -> Double { sample(now).0 }

    private func sample(_ now: Double) -> (Double, Double) {
        let tau = now - start
        switch kind {
        case .rest:
            return (to, 0)
        case .spring(let s):
            guard tau > 0 else { return (from, velocity) }
            let w0 = s.stiffness.squareRoot()
            let zeta = s.damping / (2 * w0)
            let a = from - to
            if zeta < 1 {
                let wd = w0 * (1 - zeta * zeta).squareRoot()
                let b = (velocity + zeta * w0 * a) / wd
                let e = exp(-zeta * w0 * tau)
                let c = cos(wd * tau), sn = sin(wd * tau)
                return (to + e * (a * c + b * sn), e * ((b * wd - zeta * w0 * a) * c - (a * wd + zeta * w0 * b) * sn))
            }
            let b = velocity + w0 * a
            let e = exp(-w0 * tau)
            return (to + e * (a + b * tau), e * (b - w0 * (a + b * tau)))
        case .timing(let duration, let ease):
            guard tau > 0 else { return (from, 0) }
            guard tau < duration else { return (to, 0) }
            return (from + (to - from) * ease(tau / duration), 0)
        }
    }

    mutating func set(_ value: Double) {
        kind = .rest
        from = value
        to = value
        velocity = 0
    }

    mutating func spring(to target: Double, at now: Double, delay: Double = 0, _ spring: SplitAmountPadMotion.Spring) {
        let (v, speed) = sample(now)
        from = v
        velocity = speed
        to = target
        start = now + delay
        kind = .spring(spring)
    }

    mutating func timing(to target: Double, at now: Double, delay: Double = 0, duration: Double,
                         ease: @escaping (Double) -> Double = SplitAmountPadMotion.inOutQuad) {
        from = sample(now).0
        velocity = 0
        to = target
        start = now + delay
        kind = .timing(duration, ease)
    }
}

/// A run of timed legs from wherever the value was (the shake, the limit line's flash).
struct SplitAmountPadSequence {
    /// target, duration, delay before the leg
    private var legs: [(Double, Double, Double)] = []
    private var from = 0.0
    private var start = 0.0

    func value(at now: Double) -> Double {
        var t = now - start
        var v = from
        for (target, duration, delay) in legs {
            if t < delay { return v }
            t -= delay
            if t < duration { return v + (target - v) * SplitAmountPadMotion.inOutQuad(t / duration) }
            t -= duration
            v = target
        }
        return v
    }

    mutating func run(_ next: [(Double, Double, Double)], at now: Double) {
        from = value(at: now)
        start = now
        legs = next
    }
}

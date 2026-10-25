import Foundation

/// The Foil Stamp Button's clock: every value on screen is a closed-form function of the loop time
/// t (seconds), so the frame at t = loop is the frame at t = 0. Lengths are points, angles radians.
///
///   0.0 – 1.5   rest: blank cotton card, "Seal order" on the brass stamp.
///   1.5 – 1.62  anticipation: the stamp lifts 4 pt (ease-out).
///   1.62 – 2.3  it descends (ease-in-out): scale 1 → 0.94, shadows tighten.
///   2.3         contact: a 0.15 s squash to 0.925, the paper dips round the seal, the blind
///               emboss rises (ease-out, 0.3 s) and 16 foil flakes spring out to the seal's edge.
///   2.9 – 3.6   lift: the stamp springs back (ζ 0.45, a little past rest).
///   3.6 – 5.6   the foil is laid on by a diagonal sweep with a gleam at its front.
///   4.2 – 4.8   the label turns into a drawn check and "Sealed".
///   6.6 – 7.6   the foil and the emboss dissolve through a noise threshold; the label returns.
///   7.6 – 8.0   rest (blank, as at 0).
enum FoilStampButtonMotion {
    static let loop = 8.0

    // Stamp travel (pt) and scale.
    static let anticipation = 4.0
    static let descent = 3.0
    static let pressScale = 0.06
    static let squashScale = 0.015

    // The light: azimuth (0 = from the right, −π/2 = from the top) and elevation.
    static let lightAz = -2.25
    static let lightSwing = 0.5
    static let lightEl = 0.62

    static let flakes = 16

    static func clamp01(_ x: Double) -> Double { min(1, max(0, x)) }

    /// 0 before a, 1 after b, linear in between.
    static func seg(_ t: Double, _ a: Double, _ b: Double) -> Double { clamp01((t - a) / (b - a)) }

    static func easeOut(_ x: Double) -> Double {
        let u = 1 - clamp01(x)
        return 1 - u * u * u
    }

    static func easeInOut(_ x: Double) -> Double {
        let u = clamp01(x)
        return u < 0.5 ? 4 * u * u * u : 1 - pow(-2 * u + 2, 3) / 2
    }

    /// A damped spring released from 1 toward 0 at time u (s): ω rad/s, ζ damping ratio.
    static func springFrom1(_ u: Double, _ omega: Double, _ zeta: Double) -> Double {
        if u <= 0 { return 1 }
        let wd = omega * (1 - zeta * zeta).squareRoot()
        let a = zeta * omega
        return exp(-a * u) * (cos(wd * u) + (a / wd) * sin(wd * u))
    }

    static func hash(_ i: Double) -> Double {
        let s = sin(i * 12.9898 + 4.1414) * 43758.5453
        return s - floor(s)
    }

    /// Resistance past an edge: follows the finger at first, never further than `limit`.
    static func rubber(_ x: Double, _ limit: Double) -> Double {
        let s: Double = x < 0 ? -1 : 1
        let a = abs(x)
        return s * limit * (1 - 1 / (1 + a / limit))
    }

    static func loopPose(_ t: Double) -> FoilStampButtonPose {
        let up = easeOut(seg(t, 1.5, 1.62))
        let down = easeInOut(seg(t, 1.62, 2.3))
        var press = down
        if t >= 2.9 { press = springFrom1(t - 2.9, 11, 0.45) }
        let lift = -anticipation * up * (1 - down) + descent * press
        let s = seg(t, 2.3, 2.45)
        let squash = t >= 2.3 ? sin(.pi * s) : 0
        let ring = t >= 2.3 ? sin(.pi * seg(t, 2.3, 2.75)) : 0
        let dissolve = easeInOut(seg(t, 6.6, 7.6))
        let depth = t < 2.3 ? 0 : easeOut(seg(t, 2.3, 2.6))
        let foil = easeInOut(seg(t, 3.6, 5.6))
        let check = easeOut(seg(t, 4.2, 4.8)) * (1 - easeInOut(seg(t, 6.6, 7.0)))
        let light = lightAz + lightSwing * sin(2 * .pi * t / loop)
        return FoilStampButtonPose(
            lift: lift, press: press, squash: squash, depth: depth, foil: foil, dissolve: dissolve,
            ring: ring, light: light, check: check, flakeAge: t >= 2.3 ? t - 2.3 : -1
        )
    }

    /// Flake i at `age` s after contact: position relative to the seal centre, angle, size.
    static func flake(_ i: Int, age: Double, radius R: Double) -> (x: Double, y: Double, angle: Double, size: Double) {
        let fi = Double(i)
        let a = hash(fi) * 2 * .pi
        let out = 1.02 + hash(fi + 31) * 0.24
        let fly = easeOut(age / 0.55)
        // They overshoot a little past where they settle, as they skid on the paper.
        let settle = 1 + 0.08 * sin(.pi * clamp01(age / 0.55)) * (1 - clamp01(age / 0.9))
        let r = R * (0.7 + (out - 0.7) * fly) * settle
        let spin = (hash(fi + 7) - 0.5) * 3 * fly + hash(fi + 3) * 2 * .pi
        let size = 1.8 + hash(fi + 11) * 2.6
        return (cos(a) * r, sin(a) * r, spin, size)
    }
}

/// Everything the screen draws, for one frame.
struct FoilStampButtonPose {
    /// Stamp offset in pt (negative = up) and how far it is pressed (0…1, may overshoot a little).
    var lift = 0.0
    var press = 0.0
    /// Extra squash at contact (0…1).
    var squash = 0.0
    /// Emboss height (0 blank … 1 crisp), foil coverage (sweep 0 … 1), dissolve (0 … 1).
    var depth = 0.0
    var foil = 0.0
    var dissolve = 0.0
    /// The paper's dip round the seal at contact (0…1).
    var ring = 0.0
    /// Light azimuth (radians).
    var light = FoilStampButtonMotion.lightAz
    /// Label: 0 "Seal order", 1 check + "Sealed".
    var check = 0.0
    /// Seconds since the flakes sprang out (negative = none).
    var flakeAge = -1.0

    static let rest = FoilStampButtonPose()
    /// Under reduced motion: the sealed card, still.
    static let sealed = FoilStampButtonPose(depth: 1, foil: 1, check: 1, flakeAge: 3)
}

/// A cubic Bézier easing curve, like CSS `cubic-bezier()` and Reanimated's `Easing.bezier`.
struct FoilStampButtonCurve {
    let x1: Double, y1: Double, x2: Double, y2: Double

    static let easeOut = FoilStampButtonCurve(x1: 0.23, y1: 1, x2: 0.32, y2: 1)
    static let easeInOut = FoilStampButtonCurve(x1: 0.77, y1: 0, x2: 0.175, y2: 1)
    /// Reanimated's default timing curve, `Easing.inOut(Easing.quad)`.
    static let quadInOut = FoilStampButtonCurve(x1: -1, y1: 0, x2: 0, y2: 0)

    func callAsFunction(_ x: Double) -> Double {
        let u = min(1, max(0, x))
        if x1 < 0 { return u < 0.5 ? 2 * u * u : 1 - pow(-2 * u + 2, 2) / 2 }
        // Solve x(s) = u for s by Newton steps, then return y(s).
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

/// One value the finger drives, as Reanimated's `withTiming` / `withSpring` / `withSequence` /
/// `withDelay` would: a closed-form function of the time it was set.
struct FoilStampButtonTrack {
    private enum Kind {
        case still
        case timing(duration: Double, curve: FoilStampButtonCurve)
        /// To `peak` over d1 s, then to 0 over d2 s (default curve).
        case pulse(peak: Double, d1: Double, d2: Double)
        case spring(omega: Double, zeta: Double, velocity: Double)
    }

    private var kind = Kind.still
    private var from: Double
    private var to: Double
    private var start = 0.0

    init(_ value: Double) {
        from = value
        to = value
    }

    func value(at t: Double) -> Double {
        let u = t - start
        switch kind {
        case .still:
            return to
        case let .timing(duration, curve):
            if u <= 0 { return from }
            if u >= duration { return to }
            return from + (to - from) * curve(u / duration)
        case let .pulse(peak, d1, d2):
            if u <= 0 { return from }
            if u < d1 { return from + (peak - from) * FoilStampButtonCurve.quadInOut(u / d1) }
            if u < d1 + d2 { return peak + (to - peak) * FoilStampButtonCurve.quadInOut((u - d1) / d2) }
            return to
        case let .spring(omega, zeta, velocity):
            if u <= 0 { return from }
            let a = from - to
            let z = min(zeta, 0.999)
            let wd = omega * (1 - z * z).squareRoot()
            let b = (velocity + z * omega * a) / wd
            return to + exp(-z * omega * u) * (a * cos(wd * u) + b * sin(wd * u))
        }
    }

    func done(at t: Double) -> Bool {
        let u = t - start
        switch kind {
        case .still: return true
        case let .timing(duration, _): return u >= duration
        case let .pulse(_, d1, d2): return u >= d1 + d2
        case let .spring(omega, zeta, _): return u >= 6 / (max(zeta, 0.1) * omega)
        }
    }

    mutating func set(_ value: Double) {
        kind = .still
        from = value
        to = value
    }

    mutating func timing(_ target: Double, duration: Double, curve: FoilStampButtonCurve = .quadInOut, delay: Double = 0, now: Double) {
        from = value(at: now)
        to = target
        start = now + delay
        kind = .timing(duration: duration, curve: curve)
    }

    mutating func pulse(peak: Double, up: Double, down: Double, now: Double) {
        from = value(at: now)
        to = 0
        start = now
        kind = .pulse(peak: peak, d1: up, d2: down)
    }

    /// Reanimated's duration-based spring, approximated by ω = 6.05 / duration (ω 11 at 0.55 s).
    mutating func spring(_ target: Double, duration: Double, dampingRatio: Double, velocity: Double = 0, now: Double) {
        from = value(at: now)
        to = target
        start = now
        kind = .spring(omega: 6.05 / duration, zeta: dampingRatio, velocity: velocity)
    }
}

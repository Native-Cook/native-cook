import Foundation

/// A CSS-style cubic Bezier easing: what `Easing.bezier(x1, y1, x2, y2)` of Reanimated draws.
/// `value(x)` solves the curve's x(t) = x for t (Newton steps, then bisection) and returns y(t).
struct FolioFeatureLibraryBezier {
    let x1: Double
    let y1: Double
    let x2: Double
    let y2: Double

    init(_ x1: Double, _ y1: Double, _ x2: Double, _ y2: Double) {
        self.x1 = x1
        self.y1 = y1
        self.x2 = x2
        self.y2 = y2
    }

    private func sampleX(_ t: Double) -> Double {
        let u = 1 - t
        let a = 3 * u * u * t * x1
        let b = 3 * u * t * t * x2
        return a + b + t * t * t
    }

    private func sampleY(_ t: Double) -> Double {
        let u = 1 - t
        let a = 3 * u * u * t * y1
        let b = 3 * u * t * t * y2
        return a + b + t * t * t
    }

    private func slopeX(_ t: Double) -> Double {
        let u = 1 - t
        let a = 3 * u * u * x1
        let b = 6 * u * t * (x2 - x1)
        let c = 3 * t * t * (1 - x2)
        return a + b + c
    }

    func value(_ x: Double) -> Double {
        if x <= 0 { return 0 }
        if x >= 1 { return 1 }
        var t = x
        for _ in 0..<8 {
            let error = sampleX(t) - x
            if abs(error) < 1e-7 { return sampleY(t) }
            let slope = slopeX(t)
            if abs(slope) < 1e-6 { break }
            t -= error / slope
        }
        var low = 0.0
        var high = 1.0
        t = x
        for _ in 0..<48 {
            let error = sampleX(t) - x
            if abs(error) < 1e-7 { break }
            if error > 0 { high = t } else { low = t }
            t = (low + high) / 2
        }
        return sampleY(t)
    }
}

/// The easings the block uses.
enum FolioFeatureLibraryCurve {
    /// `Easing.linear`.
    case linear
    /// `OUT = Easing.bezier(0.23, 1, 0.32, 1)` (FolioFeatureLibrary.tsx:18), also PressScale's curve.
    case out
    /// Reanimated's default `withTiming` / `FadeIn` easing, `Easing.inOut(Easing.quad)`.
    case inOutQuad
    /// UIKit's default animation curve (`curveEaseInOut`), used for the system's own scroll animation.
    case systemEaseInOut
    /// The system's modal slide, estimated (see PORT-NOTES).
    case modal

    static let outBezier = FolioFeatureLibraryBezier(0.23, 1, 0.32, 1)
    private static let systemBezier = FolioFeatureLibraryBezier(0.42, 0, 0.58, 1)
    private static let modalBezier = FolioFeatureLibraryBezier(0.2, 0.85, 0.25, 1)

    func value(_ x: Double) -> Double {
        let p = min(1, max(0, x))
        switch self {
        case .linear:
            return p
        case .out:
            return Self.outBezier.value(p)
        case .inOutQuad:
            return p < 0.5 ? 2 * p * p : 1 - 2 * (1 - p) * (1 - p)
        case .systemEaseInOut:
            return Self.systemBezier.value(p)
        case .modal:
            return Self.modalBezier.value(p)
        }
    }
}

/// A Reanimated shared value driven by `withTiming`: it starts from wherever the value is when the
/// animation is asked for, so a new target in mid-flight retargets without a jump.
struct FolioFeatureLibraryTween {
    var from: Double
    var to: Double
    var start: Double
    var duration: Double
    var curve: FolioFeatureLibraryCurve

    init(constant value: Double = 0) {
        from = value
        to = value
        start = 0
        duration = 0
        curve = .linear
    }

    func value(at time: Double) -> Double {
        if duration <= 0 { return to }
        let p = min(1, max(0, (time - start) / duration))
        return from + (to - from) * curve.value(p)
    }

    /// `shared.set(withTiming(target, { duration, easing }))`.
    mutating func animate(to target: Double, duration seconds: Double, curve easing: FolioFeatureLibraryCurve, now: Double) {
        let current = value(at: now)
        from = current
        to = target
        start = now
        duration = seconds
        curve = easing
    }

    /// `shared.set(value)`.
    mutating func set(_ value: Double) {
        self = FolioFeatureLibraryTween(constant: value)
    }

    /// `cancelAnimation(shared)`: the value stays where it is.
    mutating func freeze(now: Double) {
        set(value(at: now))
    }
}

/// `withSpring({ damping, stiffness, mass })` from 0 to 1 with no initial velocity, in closed form.
struct FolioFeatureLibrarySpring {
    let damping: Double
    let stiffness: Double
    let mass: Double

    init(damping: Double, stiffness: Double, mass: Double = 1) {
        self.damping = damping
        self.stiffness = stiffness
        self.mass = mass
    }

    func value(_ time: Double) -> Double {
        if time <= 0 { return 0 }
        let omega = (stiffness / mass).squareRoot()
        let zeta = damping / (2 * (stiffness * mass).squareRoot())
        if zeta < 1 {
            let damped = omega * (1 - zeta * zeta).squareRoot()
            let envelope = exp(-zeta * omega * time)
            let wave = cos(damped * time) + (zeta * omega / damped) * sin(damped * time)
            return 1 - envelope * wave
        }
        if zeta == 1 {
            return 1 - exp(-omega * time) * (1 + omega * time)
        }
        let root = (zeta * zeta - 1).squareRoot()
        let r1 = -omega * (zeta - root)
        let r2 = -omega * (zeta + root)
        return 1 - (r2 * exp(r1 * time) - r1 * exp(r2 * time)) / (r2 - r1)
    }
}

enum FolioFeatureLibraryMath {
    static func clamp01(_ x: Double) -> Double {
        min(1, max(0, x))
    }

    static func lerp(_ a: Double, _ b: Double, _ t: Double) -> Double {
        a + (b - a) * t
    }

    /// Reanimated's `interpolate(x, input, output, 'clamp')`.
    static func interpolate(_ x: Double, _ input: [Double], _ output: [Double]) -> Double {
        guard let first = input.first, let last = input.last, input.count == output.count else { return 0 }
        if x <= first { return output[0] }
        if x >= last { return output[output.count - 1] }
        var index = 1
        while index < input.count - 1 && x > input[index] {
            index += 1
        }
        let span = input[index] - input[index - 1]
        let t = span > 0 ? (x - input[index - 1]) / span : 1
        return output[index - 1] + (output[index] - output[index - 1]) * t
    }
}

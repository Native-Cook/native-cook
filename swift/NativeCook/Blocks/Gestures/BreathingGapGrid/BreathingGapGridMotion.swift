import SwiftUI

/// Reanimated's `withSpring` with a duration and a damping ratio, stepped a frame at a time: the
/// stiffness is solved so the spring's energy falls to the threshold after 1.5 × the duration, and
/// each frame moves along the exact solution from where the last one left it.
struct BreathingGapGridSpring {
    let target: Double
    let duration: Double
    let dampingRatio: Double
    private(set) var velocity: Double
    private let omega0: Double
    private let omega1: Double
    private let stiffness: Double
    private let initialEnergy: Double
    private static let energyThreshold = 6e-9

    init(from value: Double, to target: Double, velocity: Double, carried: Double, duration: Double, dampingRatio: Double) {
        self.target = target
        self.duration = duration
        self.dampingRatio = dampingRatio
        var v = carried + velocity
        // Velocity that would carry the first frame away from the target is dropped.
        if (target > value && v < 0) || (target < value && v > 0) { v = 0 }
        self.velocity = v
        let x0 = value - target
        let zeta = dampingRatio
        let settling = duration * 1.5
        func energy(_ x: Double, _ v: Double, _ k: Double) -> Double { 0.5 * k * x * x + 0.5 * v * v }
        func difference(_ k: Double) -> Double {
            let w = sqrt(k) * zeta
            let xt = (x0 + (v + x0 * w) * settling) * exp(-w * settling)
            let vt = (x0 + (v + x0 * w) * settling) * exp(-w * settling) * -w + (v + x0 * w) * exp(-w * settling)
            return energy(xt, vt, k) / energy(x0, v, k) - Self.energyThreshold
        }
        // Bisection, as Reanimated solves it.
        var low = Double.ulpOfOne
        var high = 8e3
        let rising = difference(high) >= difference(low)
        var k = (low + high) / 2
        var left = 100
        while abs(difference(k)) > Self.energyThreshold * 1e-3 && left > 0 {
            left -= 1
            if (difference(k) < 0) == rising { low = k } else { high = k }
            k = (low + high) / 2
        }
        if !k.isFinite { k = 170 }
        stiffness = k
        omega0 = sqrt(k)
        omega1 = zeta < 1 ? omega0 * sqrt(1 - zeta * zeta) : 0
        initialEnergy = energy(x0, velocity, k)
    }

    /// Moves `value` on by `dt` seconds; true once the spring has come to rest on its target.
    mutating func step(_ value: inout Double, dt: Double) -> Bool {
        if initialEnergy == 0 {
            value = target
            return true
        }
        let zeta = dampingRatio
        let x0 = value - target
        let v0 = velocity
        if zeta < 1 {
            let sin1 = sin(omega1 * dt)
            let cos1 = cos(omega1 * dt)
            let envelope = exp(-zeta * omega0 * dt)
            let frag = envelope * (sin1 * ((v0 + zeta * omega0 * x0) / omega1) + x0 * cos1)
            value = target + frag
            velocity = -zeta * omega0 * frag + envelope * (cos1 * (v0 + zeta * omega0 * x0) - omega1 * x0 * sin1)
        } else {
            let envelope = exp(-omega0 * dt)
            value = target + envelope * (x0 + (v0 + omega0 * x0) * dt)
            velocity = envelope * -omega0 * (x0 + (v0 + omega0 * x0) * dt) + envelope * (v0 + omega0 * x0)
        }
        let now = 0.5 * stiffness * (target - value) * (target - value) + 0.5 * velocity * velocity
        if now / initialEnergy <= Self.energyThreshold {
            value = target
            velocity = 0
            return true
        }
        return false
    }
}

/// A number driven like a Reanimated shared value: set outright, or animated by a spring or a timing
/// curve that is stepped by the block's frame clock.
struct BreathingGapGridValue {
    private enum Motion {
        case spring(BreathingGapGridSpring)
        case timing(from: Double, to: Double, start: Double, duration: Double, curve: (Double) -> Double)
    }

    private(set) var value: Double
    private var motion: Motion?

    init(_ value: Double) {
        self.value = value
    }

    var isAnimating: Bool { motion != nil }

    mutating func set(_ value: Double) {
        self.value = value
        motion = nil
    }

    mutating func spring(to target: Double, duration: Double, dampingRatio: Double, velocity: Double = 0) {
        var carried = 0.0
        if case let .spring(running) = motion {
            // The same spring asked for again carries on as it was.
            if running.target == target && running.duration == duration && running.dampingRatio == dampingRatio { return }
            carried = running.velocity
        }
        motion = .spring(BreathingGapGridSpring(
            from: value, to: target, velocity: velocity, carried: carried,
            duration: duration, dampingRatio: dampingRatio
        ))
    }

    mutating func timing(to target: Double, duration: Double, curve: @escaping (Double) -> Double, now: Double) {
        motion = .timing(from: value, to: target, start: now, duration: duration, curve: curve)
    }

    mutating func step(dt: Double, now: Double) {
        guard let motion else { return }
        switch motion {
        case .spring(var spring):
            var next = value
            let done = spring.step(&next, dt: dt)
            value = next
            self.motion = done ? nil : .spring(spring)
        case let .timing(from, to, start, duration, curve):
            let p = min(1, max(0, (now - start) / duration))
            value = from + (to - from) * curve(p)
            if p >= 1 { self.motion = nil }
        }
    }
}

enum BreathingGapGridCurve {
    private static let inOut = UnitCurve.bezier(startControlPoint: UnitPoint(x: 0.77, y: 0), endControlPoint: UnitPoint(x: 0.175, y: 1))
    private static let out = UnitCurve.bezier(startControlPoint: UnitPoint(x: 0.23, y: 1), endControlPoint: UnitPoint(x: 0.32, y: 1))

    static func easeInOut(_ p: Double) -> Double { inOut.value(at: p) }
    static func easeOut(_ p: Double) -> Double { out.value(at: p) }
    /// Reanimated's default timing curve, ease in and out (quadratic).
    static func standard(_ p: Double) -> Double { p < 0.5 ? 2 * p * p : 1 - pow(-2 * p + 2, 2) / 2 }
}

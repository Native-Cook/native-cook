import SwiftUI

/// A number moved a frame at a time the way Reanimated moves a shared value: set at once, by
/// `withSpring({ duration, dampingRatio, velocity })`, or by `withTiming({ duration, easing })`.
struct ScatterCollageHeroDriven {
    private enum Mode {
        case still
        case spring
        case timing(from: Double, time: Double, duration: Double, curve: (Double) -> Double)
    }

    private(set) var value: Double
    private(set) var velocity = 0.0
    private var mode = Mode.still
    private var target = 0.0
    private var zeta = 0.0
    private var omega0 = 0.0
    private var omega1 = 0.0
    private var stiffness = 0.0
    private var initialEnergy = 0.0
    private static let energyThreshold = 6e-9

    init(_ value: Double) {
        self.value = value
    }

    var isMoving: Bool {
        if case .still = mode { return false }
        return true
    }

    /// Puts the value somewhere at once, stopping whatever was moving it.
    mutating func set(_ next: Double) {
        value = next
        velocity = 0
        mode = .still
    }

    /// A spring whose stiffness is solved, as Reanimated does, so its energy falls to the threshold
    /// after 1.5 × `duration`. A spring under way hands its speed on to the new one.
    mutating func spring(to next: Double, duration: Double, dampingRatio: Double, velocity extra: Double = 0) {
        var v = extra
        if case .spring = mode { v += velocity }
        let x0 = value - next
        target = next
        velocity = v
        zeta = dampingRatio
        stiffness = Self.stiffness(x0: x0, v0: v, duration: duration, zeta: dampingRatio)
        omega0 = sqrt(stiffness)
        omega1 = zeta < 1 ? omega0 * sqrt(1 - zeta * zeta) : 0
        initialEnergy = 0.5 * stiffness * x0 * x0 + 0.5 * v * v
        mode = .spring
    }

    /// A timing curve; Reanimated's default easing is a quadratic ease-in-out.
    mutating func timing(to next: Double, duration: Double, curve: @escaping (Double) -> Double = ScatterCollageHeroDriven.quadInOut) {
        target = next
        velocity = 0
        mode = .timing(from: value, time: 0, duration: max(duration, 0.0001), curve: curve)
    }

    /// Moves on by `dt` seconds; true on the frame the motion comes to rest on its target.
    mutating func step(_ dt: Double) -> Bool {
        switch mode {
        case .still:
            return false
        case let .timing(from, time, duration, curve):
            let t = time + dt
            let p = min(max(t / duration, 0), 1)
            value = from + (target - from) * curve(p)
            if p >= 1 {
                mode = .still
                return true
            }
            mode = .timing(from: from, time: t, duration: duration, curve: curve)
            return false
        case .spring:
            if initialEnergy == 0 {
                value = target
                mode = .still
                return true
            }
            let x0 = value - target
            let v0 = velocity
            if zeta < 1 {
                let s = sin(omega1 * dt)
                let c = cos(omega1 * dt)
                let envelope = exp(-zeta * omega0 * dt)
                let frag = envelope * (s * ((v0 + zeta * omega0 * x0) / omega1) + x0 * c)
                value = target + frag
                velocity = -zeta * omega0 * frag + envelope * (c * (v0 + zeta * omega0 * x0) - omega1 * x0 * s)
            } else {
                let envelope = exp(-omega0 * dt)
                value = target + envelope * (x0 + (v0 + omega0 * x0) * dt)
                velocity = envelope * -omega0 * (x0 + (v0 + omega0 * x0) * dt) + envelope * (v0 + omega0 * x0)
            }
            let x = target - value
            let energy = 0.5 * stiffness * x * x + 0.5 * velocity * velocity
            if energy / initialEnergy <= Self.energyThreshold {
                value = target
                velocity = 0
                mode = .still
                return true
            }
            return false
        }
    }

    static func quadInOut(_ t: Double) -> Double {
        t < 0.5 ? 2 * t * t : 1 - 2 * (1 - t) * (1 - t)
    }

    private static func stiffness(x0: Double, v0: Double, duration: Double, zeta: Double) -> Double {
        if x0 == 0 && v0 == 0 { return 0 }
        let settling = duration * 1.5
        func difference(_ k: Double) -> Double {
            let w = sqrt(k) * zeta
            let a = x0 + (v0 + x0 * w) * settling
            let e = exp(-w * settling)
            let xt = a * e
            let vt = a * e * -w + (v0 + x0 * w) * e
            let e0 = 0.5 * k * x0 * x0 + 0.5 * v0 * v0
            let et = 0.5 * k * xt * xt + 0.5 * vt * vt
            return et / e0 - energyThreshold
        }
        // Bisection, as Reanimated solves it.
        var low = Double.ulpOfOne
        var high = 8e3
        let rising = difference(high) >= difference(low)
        var k = (low + high) / 2
        var left = 100
        while abs(difference(k)) > energyThreshold * 1e-3 && left > 0 {
            left -= 1
            if (difference(k) < 0) == rising { low = k } else { high = k }
            k = (low + high) / 2
        }
        return k.isFinite ? k : 170
    }
}

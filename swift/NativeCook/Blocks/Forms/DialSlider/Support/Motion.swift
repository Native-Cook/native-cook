import Foundation

enum DialEasing {
    /// Reanimated's default `withTiming` easing (`Easing.inOut(Easing.quad)`).
    static func inOutQuad(_ t: Double) -> Double {
        t < 0.5 ? 2 * t * t : 1 - pow(-2 * t + 2, 2) / 2
    }
}

/// A cubic Bézier easing curve, matching `Easing.bezier(x1, y1, x2, y2)`.
struct CubicBezierEasing {
    let x1: Double
    let y1: Double
    let x2: Double
    let y2: Double

    static let easeOut = CubicBezierEasing(x1: 0.23, y1: 1, x2: 0.32, y2: 1)

    private func sample(_ a: Double, _ b: Double, _ t: Double) -> Double {
        // Bernstein polynomial with P0 = 0 and P3 = 1.
        3 * a * (1 - t) * (1 - t) * t + 3 * b * (1 - t) * t * t + t * t * t
    }

    private func sampleDerivativeX(_ t: Double) -> Double {
        3 * x1 * (1 - t) * (1 - t) - 6 * x1 * (1 - t) * t + 6 * x2 * (1 - t) * t - 3 * x2 * t * t + 3 * t * t
    }

    func value(_ x: Double) -> Double {
        if x <= 0 { return 0 }
        if x >= 1 { return 1 }
        var t = x
        for _ in 0..<8 {
            let error = sample(x1, x2, t) - x
            let derivative = sampleDerivativeX(t)
            if abs(error) < 1e-6 { break }
            if abs(derivative) < 1e-9 { break }
            t -= error / derivative
        }
        t = Swift.min(1, Swift.max(0, t))
        return sample(y1, y2, t)
    }
}

/// Reanimated-style `withDecay` integration step.
struct DecayState {
    var velocity: Double
    var lastTime: TimeInterval
    var deceleration: Double

    /// Advances the decay and returns the new position and whether it finished.
    mutating func step(position: Double, clamp: ClosedRange<Double>, now: TimeInterval) -> (position: Double, finished: Bool) {
        let deltaTime = (now - lastTime) * 1000
        lastTime = now
        let kv = pow(deceleration, deltaTime)
        let kx = deceleration * (1 - kv) / (1 - deceleration)
        let v0 = velocity / 1000
        velocity = v0 * kv * 1000
        var next = position + v0 * kx
        var finished = false
        if next < clamp.lowerBound {
            next = clamp.lowerBound
            finished = true
        } else if next > clamp.upperBound {
            next = clamp.upperBound
            finished = true
        }
        if abs(velocity) < 1 { finished = true }
        return (next, finished)
    }
}

/// Simple damped spring integrated in fixed sub-steps with overshoot clamping.
struct SpringState {
    var target: Double
    var velocity: Double
    var lastTime: TimeInterval
    var mass: Double
    var stiffness: Double
    var damping: Double

    mutating func step(position: Double, now: TimeInterval) -> (position: Double, finished: Bool) {
        let frame = Swift.min(Swift.max(now - lastTime, 0), 1.0 / 30.0)
        lastTime = now
        var x = position
        let subStep = 1.0 / 1000.0
        var remaining = frame
        let startSide = (x - target).sign
        while remaining > 0 {
            let dt = Swift.min(subStep, remaining)
            remaining -= dt
            let acceleration = (-stiffness * (x - target) - damping * velocity) / mass
            velocity += acceleration * dt
            x += velocity * dt
            if x != target, (x - target).sign != startSide {
                // overshootClamping
                return (target, true)
            }
        }
        if abs(velocity) < 0.01, abs(x - target) < 0.01 {
            return (target, true)
        }
        return (x, false)
    }
}

/// Duration-based easing animation.
struct TimingState {
    var from: Double
    var to: Double
    var startTime: TimeInterval
    var duration: TimeInterval
    var easing: (Double) -> Double

    func value(at now: TimeInterval) -> (position: Double, finished: Bool) {
        guard duration > 0 else { return (to, true) }
        let progress = Swift.min(1, Swift.max(0, (now - startTime) / duration))
        return (from + (to - from) * easing(progress), progress >= 1)
    }
}

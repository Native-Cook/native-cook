import Foundation

/// Interruptible scalar Reanimated-style tracks. Delayed replacements keep the old track running until hand-off.
struct PrintLineScrubAnimation {
    typealias M = PrintLineScrubMotion
    enum Curve {
        case spring(M.Spring, Double)
        case timing(Double, Bool)
    }
    struct Track {
        let from: Double, to: Double, start: Double, curve: Curve
        func sample(_ now: Double) -> (value: Double, velocity: Double) {
            let t = max(0, now - start)
            switch curve {
            case let .spring(s, v):
                let x = from - to
                let displacement = M.springDisp(t, x, v, s)
                let velocity = PrintLineScrubAnimation.velocity(t, x, v, s)
                let initialEnergy = s.stiffness * x * x + s.mass * v * v
                let energy = s.stiffness * displacement * displacement + s.mass * velocity * velocity
                if initialEnergy == 0 || energy / initialEnergy <= 6e-9 { return (to, 0) }
                return (to + displacement, velocity)
            case let .timing(duration, exponential):
                if t >= duration { return (to, 0) }
                let k = M.clamp01(t / duration)
                // Reanimated's default inOut(quad), or out(exp) for thermal memory.
                let e = exponential ? 1 - pow(2, -10 * k) : k < 0.5 ? 2 * k * k : 1 - pow(-2 * k + 2, 2) / 2
                return (M.mix(from, to, e), 0)
            }
        }
    }
    struct Pending {
        let to: Double, at: Double, curve: Curve
    }
    struct Queued {
        let to: Double, delay: Double, curve: Curve
    }
    private var value: Double
    private var track: Track?
    private var pending: [Pending] = []
    private var queued: [Queued] = []

    init(_ value: Double) { self.value = value }

    private static func velocity(_ t: Double, _ x: Double, _ v: Double, _ s: M.Spring) -> Double {
        if t <= 0 { return v }
        let a = s.damping / (2 * s.mass), w = sqrt(s.stiffness / s.mass - a * a)
        let b = (v + a * x) / w
        return exp(-a * t) * ((b * w - a * x) * cos(w * t) - (x * w + a * b) * sin(w * t))
    }
    private static func inherited(_ curve: Curve, _ previousVelocity: Double, from: Double, to: Double) -> Curve {
        if case let .spring(s, v) = curve {
            var velocity = v + previousVelocity
            // Reanimated 4 clips a release velocity which initially points away from its new target.
            if (to > from && velocity < 0) || (to < from && velocity > 0) { velocity = 0 }
            return .spring(s, velocity)
        }
        return curve
    }

    mutating func sample(_ now: Double) -> Double {
        while let next = pending.first, now >= next.at {
            let previous = track?.sample(next.at) ?? (value, 0)
            let curve = Self.inherited(next.curve, previous.1, from: previous.0, to: next.to)
            track = Track(from: previous.0, to: next.to, start: next.at, curve: curve)
            pending.removeFirst()
        }
        if let track {
            let sample = track.sample(now)
            value = sample.value
            if pending.isEmpty, sample.value == track.to, sample.velocity == 0, let next = queued.first {
                // withSequence starts its delay at the frame where the previous animation actually finishes.
                pending = [Pending(to: next.to, at: now + next.delay, curve: next.curve)]
                queued.removeFirst()
            }
        }
        return value
    }
    mutating func set(_ newValue: Double) { value = newValue; track = nil; pending = []; queued = [] }
    mutating func start(_ to: Double, at now: Double, curve: Curve, delay: Double = 0) {
        _ = sample(now)
        pending = []; queued = []
        if delay > 0 { pending = [Pending(to: to, at: now + delay, curve: curve)] }
        else {
            let v = track?.sample(now).velocity ?? 0
            let next = Self.inherited(curve, v, from: value, to: to)
            track = Track(from: value, to: to, start: now, curve: next)
        }
    }
    mutating func then(_ to: Double, after delay: Double, curve: Curve) {
        queued.append(Queued(to: to, delay: delay, curve: curve))
    }
}

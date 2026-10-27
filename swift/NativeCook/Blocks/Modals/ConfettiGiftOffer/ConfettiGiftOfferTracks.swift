import SwiftUI

/// The two easings the React Native block animates with.
enum ConfettiGiftOfferEasing {
    /// `Easing.linear`.
    case linear
    /// `EASE_OUT` = `Easing.bezier(0.23, 1, 0.32, 1)`.
    case out

    func apply(_ x: Double) -> Double {
        switch self {
        case .linear: return min(1, max(0, x))
        case .out: return ConfettiGiftOfferMotion.enter(x)
        }
    }
}

/// A damped spring from `from` to `to` in Reanimated's `{ duration, dampingRatio }` form: a unit mass whose
/// stiffness is solved the way Reanimated 4.5 does it (see `reanimatedOmega`), not SwiftUI's
/// `Spring(duration:bounce:)`, which would be about 2.5 times softer.
/// Played from an initial velocity in points per second, like `withSpring(…, { velocity })`.
struct ConfettiGiftOfferSpring {
    let from: Double
    let to: Double
    let velocity: Double
    let omega: Double
    let zeta: Double
    let clamp: Bool
    /// Seconds after which the spring counts as finished (rest thresholds, or crossing the target when clamped).
    private(set) var settle: Double = 0

    init(from: Double, to: Double, duration: Double, dampingRatio: Double, velocity: Double = 0, clamp: Bool = false) {
        self.from = from
        self.to = to
        self.velocity = velocity
        self.omega = Self.reanimatedOmega(duration: duration, dampingRatio: dampingRatio, offset: from - to, velocity: velocity)
        self.zeta = dampingRatio
        self.clamp = clamp
        self.settle = findSettle()
    }

    /// `calculateNewStiffnessToMatchDuration` of Reanimated 4.5 (springUtils.ts): `duration` is perceptual, so the
    /// stiffness is the one whose energy has fallen to 6e-9 of the start after 1.5 x duration, found by the same
    /// bisection between 0 and 8000. Returns sqrt(stiffness) for a unit mass (400 ms at 0.8 gives 25.56 rad/s).
    static func reanimatedOmega(duration: Double, dampingRatio: Double, offset: Double, velocity: Double) -> Double {
        let threshold = 6e-9
        let settling = duration * 1.5
        let x0 = offset == 0 && velocity == 0 ? 1 : offset
        func energyDifference(_ stiffness: Double) -> Double {
            let decay = stiffness.squareRoot() * dampingRatio
            let envelope = exp(-decay * settling)
            let position = (x0 + (velocity + x0 * decay) * settling) * envelope
            let speed = position * -decay + (velocity + x0 * decay) * envelope
            let start = 0.5 * stiffness * x0 * x0 + 0.5 * velocity * velocity
            let end = 0.5 * stiffness * position * position + 0.5 * speed * speed
            return end / start - threshold
        }
        var low = Double.ulpOfOne
        var high = 8000.0
        let direction: Double = energyDifference(high) >= energyDifference(low) ? 1 : -1
        var current = (low + high) / 2
        var iterations = 100
        while abs(energyDifference(current)) > threshold * 1e-3 && iterations > 0 {
            iterations -= 1
            if energyDifference(current) * direction < 0 {
                low = current
            } else {
                high = current
            }
            current = (low + high) / 2
        }
        return current.squareRoot()
    }

    private func findSettle() -> Double {
        // Reanimated's defaults: restDisplacementThreshold 0.01 and restSpeedThreshold 2, checked once a frame.
        var t = 0.0
        while t < 8 {
            t += 1.0 / 60.0
            let current = state(at: t)
            let crossed = clamp && ((to > from && current.x > to) || (to < from && current.x < to))
            if crossed || (abs(current.v) < 2 && abs(to - current.x) < 0.01) { return t }
        }
        return 8
    }

    /// Position and velocity `t` seconds after the start.
    func state(at t: Double) -> (x: Double, v: Double) {
        let a = from - to
        if zeta < 1 {
            let damped = omega * (1 - zeta * zeta).squareRoot()
            let decay = exp(-zeta * omega * t)
            let b = (velocity + zeta * omega * a) / damped
            let cosine = cos(damped * t)
            let sine = sin(damped * t)
            let x = decay * (a * cosine + b * sine)
            let v = decay * (-zeta * omega * (a * cosine + b * sine) + damped * (-a * sine + b * cosine))
            return (to + x, v)
        }
        let c = velocity + omega * a
        let decay = exp(-omega * t)
        return (to + decay * (a + c * t), decay * (c - omega * (a + c * t)))
    }

    func value(at t: Double) -> Double {
        if t >= settle { return to }
        return state(at: t).x
    }
}

/// One Reanimated shared value that can be animated (`withTiming`, `withDelay`, `withSpring`) and read at any
/// time. It is read on the main thread by the per-frame closure and by touch handlers, so nothing here is
/// observable state.
final class ConfettiGiftOfferValue {
    private enum Run {
        case timing(start: Double, delay: Double, from: Double, to: Double, duration: Double, easing: ConfettiGiftOfferEasing)
        case spring(start: Double, spring: ConfettiGiftOfferSpring)
    }

    private var resting: Double
    private var run: Run?
    private var completion: (() -> Void)?

    init(_ value: Double) {
        resting = value
    }

    var isRunning: Bool { run != nil }

    /// A plain assignment (`sv.set(x)`): cancels any animation.
    func set(_ value: Double) {
        run = nil
        completion = nil
        resting = value
    }

    func value(at now: Double) -> Double {
        switch run {
        case nil:
            return resting
        case let .timing(start, delay, from, to, duration, easing)?:
            let elapsed = now - start - delay
            if elapsed <= 0 { return from }
            if duration <= 0 || elapsed >= duration { return to }
            return from + (to - from) * easing.apply(elapsed / duration)
        case let .spring(start, spring)?:
            return spring.value(at: max(0, now - start))
        }
    }

    /// `withTiming(to, { duration, easing })`, optionally behind `withDelay`. Durations are in seconds.
    func timing(to target: Double, duration: Double, easing: ConfettiGiftOfferEasing, delay: Double = 0, now: Double, completion: (() -> Void)? = nil) {
        let from = value(at: now)
        resting = from
        self.completion = completion
        run = .timing(start: now, delay: delay, from: from, to: target, duration: duration, easing: easing)
    }

    /// `withSpring(to, { duration, dampingRatio, velocity, overshootClamping })`.
    func spring(to target: Double, duration: Double, dampingRatio: Double, velocity: Double = 0, clamp: Bool = false, now: Double, completion: (() -> Void)? = nil) {
        let from = value(at: now)
        resting = from
        self.completion = completion
        run = .spring(start: now, spring: ConfettiGiftOfferSpring(from: from, to: target, duration: duration, dampingRatio: dampingRatio, velocity: velocity, clamp: clamp))
    }

    /// `cancelAnimation(sv)`: freezes at the current value; the finish callback is dropped.
    func cancel(at now: Double) {
        resting = value(at: now)
        run = nil
        completion = nil
    }

    /// Settles a finished animation and hands back its callback (called by the owner, off the frame).
    func advance(at now: Double) -> (() -> Void)? {
        guard let current = run else { return nil }
        switch current {
        case let .timing(start, delay, _, to, duration, _):
            guard now - start - delay >= max(0, duration) else { return nil }
            resting = to
        case let .spring(start, spring):
            guard now - start >= spring.settle else { return nil }
            resting = spring.to
        }
        run = nil
        let callback = completion
        completion = nil
        return callback
    }
}

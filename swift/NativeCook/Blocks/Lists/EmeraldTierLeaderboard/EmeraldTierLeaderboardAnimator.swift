import Foundation

/// A stand-in for the Reanimated shared value that the block animates with `withSpring`,
/// `withTiming` and `withDelay`: one scalar, stepped from the frame clock (seconds).
///
/// `set(_:)` cancels a running animation exactly as assigning a number to a shared value does.
/// A cancelled animation never calls its completion (the RN callbacks all ignore `finished == false`).
final class EmeraldTierAnimator {
    private(set) var value: Double

    private enum Kind {
        /// `duration` in ms, as Reanimated's duration-based spring: omega0 = 2 pi / duration,
        /// unit mass. Rest thresholds are Reanimated's defaults (0.01 displacement, 2 speed).
        case spring(duration: Double, dampingRatio: Double, overshootClamping: Bool, reduceMotionNever: Bool)
        case timing(duration: Double)
    }
    private struct Run {
        var kind: Kind
        var to: Double
        var delay: Double
        var velocity: Double
        var from: Double
        var startTime: Double?
        var lastTime: Double?
        var completion: ((Bool) -> Void)?
    }
    private var run: Run?

    init(_ value: Double) { self.value = value }

    var isRunning: Bool { run != nil }

    func set(_ newValue: Double) { run = nil; value = newValue }
    func cancel() { run = nil }

    /// `withSpring(to, { duration, dampingRatio, velocity, overshootClamping })`
    func spring(to: Double, duration: Double, dampingRatio: Double, velocity: Double = 0, overshootClamping: Bool = false,
                delay: Double = 0, reduceMotion: Bool = false, completion: ((Bool) -> Void)? = nil) {
        if reduceMotion {
            // ReduceMotion.System with the preference on: the value jumps and the animation finishes.
            value = to; run = nil; completion?(true); return
        }
        run = Run(kind: .spring(duration: duration, dampingRatio: dampingRatio, overshootClamping: overshootClamping, reduceMotionNever: false),
                  to: to, delay: delay, velocity: velocity, from: value, startTime: nil, lastTime: nil, completion: completion)
    }

    /// `withTiming(to, { duration, easing: Easing.linear })`
    func timing(to: Double, duration: Double, completion: ((Bool) -> Void)? = nil) {
        run = Run(kind: .timing(duration: duration), to: to, delay: 0, velocity: 0, from: value,
                  startTime: nil, lastTime: nil, completion: completion)
    }

    /// Advance to `now` (seconds on the frame clock). Returns true while the value changed or an animation is pending.
    @discardableResult
    func step(now: Double) -> Bool {
        guard var r = run else { return false }
        if r.startTime == nil { r.startTime = now; r.lastTime = now; run = r; if r.delay > 0 { return true } }
        let start = r.startTime!
        if now - start < r.delay { run = r; return true }
        if r.delay > 0 && r.lastTime! - start < r.delay {
            // The delay ended between frames: the animation starts at the delay's end.
            r.lastTime = start + r.delay
            r.from = value
        }
        let finishedCompletion: ((Bool) -> Void)?
        switch r.kind {
        case let .timing(duration):
            let began = start + r.delay
            let progress = duration <= 0 ? 1 : min(1, max(0, (now - began) * 1000 / duration))
            if progress >= 1 {
                value = r.to; run = nil; finishedCompletion = r.completion
            } else {
                value = r.from + (r.to - r.from) * progress
                run = r; finishedCompletion = nil
            }
        case let .spring(duration, zeta, clampOvershoot, _):
            let omega0 = 2 * Double.pi / (duration / 1000)
            // Reanimated caps one step at 64 ms.
            var remaining = min(max(0, now - r.lastTime!), 0.064)
            r.lastTime = now
            var x = value - r.to
            var v = r.velocity
            var done = false
            // Sub-steps keep the analytic step well inside one oscillation.
            let sub = 1.0 / 240.0
            while remaining > 0 {
                let t = min(sub, remaining)
                remaining -= t
                let (nx, nv) = EmeraldTierAnimator.advance(x: x, v: v, omega0: omega0, zeta: zeta, t: t)
                let crossed = (x > 0 && nx < 0) || (x < 0 && nx > 0)
                x = nx; v = nv
                if clampOvershoot && crossed { x = 0; v = 0; done = true; break }
                if abs(x) < 0.01 && abs(v) < 2 { x = 0; v = 0; done = true; break }
            }
            value = r.to + x
            r.velocity = v
            if done { run = nil; finishedCompletion = r.completion } else { run = r; finishedCompletion = nil }
        }
        if let c = finishedCompletion { c(true) }
        return true
    }

    /// Closed-form damped oscillator (unit mass) advanced by `t` seconds from displacement `x`, speed `v`.
    private static func advance(x: Double, v: Double, omega0: Double, zeta: Double, t: Double) -> (Double, Double) {
        if zeta < 1 {
            let wd = omega0 * sqrt(1 - zeta * zeta)
            let env = exp(-zeta * omega0 * t)
            let c = cos(wd * t), s = sin(wd * t)
            let b = (v + zeta * omega0 * x) / wd
            let nx = env * (x * c + b * s)
            let nv = env * (v * c - (x * wd + zeta * omega0 * b) * s)
            return (nx, nv)
        }
        // Critically damped (the block never asks for more).
        let env = exp(-omega0 * t)
        let b = v + omega0 * x
        return (env * (x + b * t), env * (v - omega0 * b * t))
    }
}

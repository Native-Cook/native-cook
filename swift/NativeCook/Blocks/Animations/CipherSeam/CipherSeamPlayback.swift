import Foundation

/// The one clock of Cipher Seam: loop time `t`, from which every frame is drawn.
///
/// It plays the part of the React Native file's `time` shared value. It is either still (a value
/// set by hand, by a drag or by `previewTime`), the linear loop `withRepeat(withTiming(LOOP))`,
/// the replay's linear run, or the spring that carries a released drag to the nearer end.
final class CipherSeamPlayback {
    private enum Source {
        case still(Double)
        case loop(since: Date)
        case timing(from: Double, to: Double, since: Date, duration: Double)
        case spring(from: Double, target: Double, velocity: Double, since: Date, end: Double)
    }

    private var source: Source

    init(start: Double) {
        source = .still(start)
    }

    /// Loop time at `date`.
    func time(at date: Date) -> Double {
        switch source {
        case .still(let value):
            return value
        case .loop(let since):
            let elapsed = max(0, date.timeIntervalSince(since))
            return elapsed.truncatingRemainder(dividingBy: CipherSeam.loop)
        case .timing(let from, let to, let since, let duration):
            let progress = CipherSeamMath.clamp(date.timeIntervalSince(since) / duration, 0, 1)
            return from + (to - from) * progress
        case .spring(let from, let target, let velocity, let since, let end):
            let elapsed = max(0, date.timeIntervalSince(since))
            if elapsed >= end { return target }
            return CipherSeamSpring.position(elapsed: elapsed, from: from, target: target, velocity: velocity)
        }
    }

    /// `time.set(value)`.
    func set(_ value: Double) {
        source = .still(value)
    }

    /// `cancelAnimation(time)`: stays where it is.
    func freeze(at date: Date) {
        source = .still(time(at: date))
    }

    /// `time.set(0)` then `withRepeat(withTiming(LOOP, linear))`.
    func startLoop(at date: Date) {
        source = .loop(since: date)
    }

    /// `withTiming(to, { duration, easing: linear })` from `from`.
    func startTiming(from: Double, to: Double, duration: Double, at date: Date) {
        source = .timing(from: from, to: to, since: date, duration: duration)
    }

    /// `withSpring(target, { duration: 400, dampingRatio: 0.8, velocity, overshootClamping: true })`.
    func startSpring(from: Double, target: Double, velocity: Double, at date: Date) {
        let end = CipherSeamSpring.settleTime(from: from, target: target, velocity: velocity)
        source = .spring(from: from, target: target, velocity: velocity, since: date, end: end)
    }
}

/// The release spring, `{ duration: 400, dampingRatio: 0.8 }` of the React Native file, solved in
/// closed form for an under-damped spring (mass 1, damping 2 ζ sqrt(k)).
///
/// Reanimated 4.5.1 reads `duration` as perceptual and solves the stiffness so the spring settles
/// after 1.5 x the duration (`calculateNewStiffnessToMatchDuration`, energy threshold 6e-9):
/// 653.4 for 400 ms at damping ratio 0.8. That is not SwiftUI's `(2 pi / duration)^2`.
enum CipherSeamSpring {
    static let stiffness = 653.4
    static let dampingRatio = 0.8

    /// Position `elapsed` seconds after the release, before any clamping.
    static func position(elapsed: Double, from: Double, target: Double, velocity: Double) -> Double {
        let omega = stiffness.squareRoot()
        let zeta = dampingRatio
        let omegaDamped = omega * (1 - zeta * zeta).squareRoot()
        let offset = from - target
        let decay = exp(-zeta * omega * elapsed)
        let coefficient = (velocity + zeta * omega * offset) / omegaDamped
        let swing = offset * cos(omegaDamped * elapsed) + coefficient * sin(omegaDamped * elapsed)
        return target + decay * swing
    }

    /// When the animation ends: at once if it crosses the target (`overshootClamping` stops it
    /// there) and otherwise when it is within 0.01 of the target (Reanimated's rest threshold).
    static func settleTime(from: Double, target: Double, velocity: Double) -> Double {
        for step in 1...3000 {
            let elapsed = Double(step) / 1000
            let value = position(elapsed: elapsed, from: from, target: target, velocity: velocity)
            let crossed = (from < target && value > target) || (from > target && value < target)
            if crossed || abs(value - target) < 0.01 { return elapsed }
        }
        return 3
    }
}

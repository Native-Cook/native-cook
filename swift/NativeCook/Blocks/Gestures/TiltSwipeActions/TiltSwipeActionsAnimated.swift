import Foundation
import Observation
import SwiftUI

/// A spring in Reanimated's terms (damping, stiffness, mass): the damped harmonic oscillator
/// m x'' = -k x - c x', stepped with its exact solution.
struct TiltSwipeActionsSpring: Equatable {
    let damping: Double
    let stiffness: Double
    let mass: Double

    /// Displacement from the target and velocity after `time` seconds.
    func advance(displacement d: Double, velocity v: Double, by time: Double) -> (Double, Double) {
        let omega = (stiffness / mass).squareRoot()
        let zeta = damping / (2 * (stiffness * mass).squareRoot())
        if zeta < 1 {
            let damped = omega * (1 - zeta * zeta).squareRoot()
            let envelope = exp(-zeta * omega * time)
            let b = (v + zeta * omega * d) / damped
            let cosine = cos(damped * time)
            let sine = sin(damped * time)
            let position = envelope * (d * cosine + b * sine)
            let velocity = envelope * ((-zeta * omega * d + damped * b) * cosine + (-zeta * omega * b - damped * d) * sine)
            return (position, velocity)
        }
        if zeta == 1 {
            let envelope = exp(-omega * time)
            let c = v + omega * d
            return (envelope * (d + c * time), envelope * (v - omega * c * time))
        }
        let root = (zeta * zeta - 1).squareRoot()
        let r1 = -omega * (zeta - root)
        let r2 = -omega * (zeta + root)
        let c2 = (v - r1 * d) / (r2 - r1)
        let c1 = d - c2
        let e1 = exp(r1 * time)
        let e2 = exp(r2 * time)
        return (c1 * e1 + c2 * e2, c1 * r1 * e1 + c2 * r2 * e2)
    }
}

/// `Easing` of a timing animation. Reanimated's `withTiming` without an easing is `inOut(quad)`.
enum TiltSwipeActionsEasing {
    case inOutQuad
    case out

    private static let outCurve = UnitCurve.bezier(
        startControlPoint: UnitPoint(x: 0.23, y: 1),
        endControlPoint: UnitPoint(x: 0.32, y: 1)
    )

    func value(_ t: Double) -> Double {
        switch self {
        case .inOutQuad:
            return t < 0.5 ? 2 * t * t : 1 - 2 * (1 - t) * (1 - t)
        case .out:
            return Self.outCurve.value(at: t)
        }
    }
}

/// A number that holds its value, follows a spring or a timing curve, and tells a callback how the
/// animation ended: `true` when it finished, `false` when something took the value over. It is what a
/// Reanimated shared value is to the block; the model advances every one of them once per frame.
@MainActor
@Observable
final class TiltSwipeActionsAnimated {
    private(set) var value: Double

    @ObservationIgnored private(set) var velocity = 0.0
    @ObservationIgnored private var motion = Motion.none
    @ObservationIgnored private var delayLeft = 0.0
    @ObservationIgnored private var fresh = false
    @ObservationIgnored private var completion: ((Bool) -> Void)?

    private enum Motion {
        case none
        case spring(TiltSwipeActionsSpring, target: Double)
        case timing(from: Double, to: Double, duration: Double, easing: TiltSwipeActionsEasing, elapsed: Double)
    }

    init(_ value: Double) {
        self.value = value
    }

    var isAnimating: Bool {
        if case .none = motion { return false }
        return true
    }

    /// `shared.set(number)`: stops any animation and jumps.
    func set(_ newValue: Double) {
        stop(finished: false)
        velocity = 0
        assign(newValue)
    }

    /// `cancelAnimation`: stops where it is.
    func cancel() {
        stop(finished: false)
        velocity = 0
    }

    /// `withSpring(target, { ...params, velocity })`, optionally `withDelay`.
    func spring(
        to target: Double,
        _ params: TiltSwipeActionsSpring,
        velocity extra: Double = 0,
        delay: Double = 0,
        completion done: ((Bool) -> Void)? = nil
    ) {
        // Reanimated carries a running spring's velocity into the next one and adds the new one;
        // a timing animation has none, which reads as zero.
        let start: Double
        var hasPrevious = true
        switch motion {
        case let .spring(previousParams, previousTarget):
            let sameAgain = previousParams == params && previousTarget == target
            start = sameAgain ? velocity : velocity + extra
        case .timing:
            start = 0
        case .none:
            start = extra
            hasPrevious = false
        }
        stop(finished: false)
        velocity = start
        motion = .spring(params, target: target)
        delayLeft = delay
        fresh = !hasPrevious
        completion = done
    }

    /// `withTiming(target, { duration, easing })`.
    func timing(
        to target: Double,
        duration: Double,
        easing: TiltSwipeActionsEasing = .inOutQuad,
        completion done: ((Bool) -> Void)? = nil
    ) {
        stop(finished: false)
        velocity = 0
        motion = .timing(from: value, to: target, duration: duration, easing: easing, elapsed: 0)
        delayLeft = 0
        fresh = true
        completion = done
    }

    /// One frame of `dt` seconds.
    func advance(_ dt: Double) {
        switch motion {
        case .none:
            return
        case let .spring(params, target):
            if delayLeft > 0 {
                delayLeft -= dt
                if delayLeft > 0 { return }
                delayLeft = 0
                fresh = true
                return
            }
            let step = fresh ? 0 : dt
            fresh = false
            var displacement = value - target
            var speed = velocity
            if step > 0 {
                (displacement, speed) = params.advance(displacement: displacement, velocity: speed, by: step)
            }
            velocity = speed
            if abs(speed) < 0.01 && abs(displacement) < 0.01 {
                assign(target)
                velocity = 0
                stop(finished: true)
            } else {
                assign(target + displacement)
            }
        case let .timing(from, to, duration, easing, elapsed):
            let next = fresh ? elapsed : elapsed + dt
            fresh = false
            let progress = min(next / duration, 1)
            assign(from + (to - from) * easing.value(progress))
            if progress >= 1 {
                assign(to)
                stop(finished: true)
            } else {
                motion = .timing(from: from, to: to, duration: duration, easing: easing, elapsed: next)
            }
        }
    }

    private func assign(_ newValue: Double) {
        if newValue != value { value = newValue }
    }

    private func stop(finished: Bool) {
        let done = completion
        completion = nil
        motion = .none
        delayLeft = 0
        done?(finished)
    }
}

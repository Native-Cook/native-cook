import Foundation

/// `withSpring(target, { duration, dampingRatio, velocity })` of Reanimated 4.5, which the React
/// Native block uses to settle the fan after a drag. Reanimated turns the duration into a stiffness
/// (a bisection on the spring's energy at 1.5 x the duration, with its default mass 4 and energy
/// threshold 6e-9) and then steps the closed-form solution every frame; the solution does not depend
/// on the frame length, so it is evaluated here at any time since the start.
struct DonationFanCarouselSpring {
    /// Reanimated's default spring mass and energy threshold (`GentleSpringConfig`, `defaultConfig`).
    private static let mass = 4.0
    private static let energyThreshold = 6e-9

    let from: Double
    let to: Double
    let startedAt: Date
    /// Seconds after the start at which the energy test snaps the value to `to`.
    private(set) var settleSeconds = 0.0

    private let velocity: Double
    private let zeta: Double
    private let omega0: Double
    private let omega1: Double
    private let stiffness: Double
    private let initialEnergy: Double

    init(from: Double, to: Double, velocity requested: Double, durationMs: Double, dampingRatio: Double, startedAt: Date) {
        self.from = from
        self.to = to
        self.startedAt = startedAt

        let x0 = from - to
        // A velocity that would drive the first frame away from the target is dropped.
        var clipped = requested
        if (to > from && clipped < 0) || (to < from && clipped > 0) { clipped = 0 }
        let stiffness = Self.matchingStiffness(x0: x0, v0: clipped, durationMs: durationMs, zeta: dampingRatio)
        let omega0 = (stiffness / Self.mass).squareRoot()

        velocity = clipped
        zeta = dampingRatio
        self.omega0 = omega0
        omega1 = dampingRatio < 1 ? omega0 * (1 - dampingRatio * dampingRatio).squareRoot() : 0
        self.stiffness = stiffness
        // Reanimated measures the starting energy with the velocity as passed in, not the clipped one.
        initialEnergy = Self.energy(displacement: x0, velocity: requested, stiffness: stiffness)

        var t = 0.0
        while t < 5 {
            if state(at: t).finished {
                settleSeconds = t
                break
            }
            t += 1.0 / 240
        }
    }

    /// The page value `date` seconds after the start; `to` once the spring has settled.
    func value(at date: Date) -> Double {
        state(at: max(0, date.timeIntervalSince(startedAt))).value
    }

    private func state(at t: Double) -> (value: Double, finished: Bool) {
        if initialEnergy == 0 { return (to, true) }
        let x0 = from - to
        let sine = sin(omega1 * t)
        let cosine = cos(omega1 * t)
        let envelope = exp(-zeta * omega0 * t)
        let fragment = envelope * (sine * ((velocity + zeta * omega0 * x0) / omega1) + x0 * cosine)
        let position = to + fragment
        let speed = -zeta * omega0 * fragment
            + envelope * (cosine * (velocity + zeta * omega0 * x0) - omega1 * x0 * sine)
        let remaining = Self.energy(displacement: to - position, velocity: speed, stiffness: stiffness)
        if remaining / initialEnergy <= Self.energyThreshold { return (to, true) }
        return (position, false)
    }

    private static func energy(displacement: Double, velocity: Double, stiffness: Double) -> Double {
        0.5 * stiffness * displacement * displacement + 0.5 * mass * velocity * velocity
    }

    /// `calculateNewStiffnessToMatchDuration` with its `bisectRoot`, step for step.
    private static func matchingStiffness(x0: Double, v0: Double, durationMs: Double, zeta: Double) -> Double {
        let settlingDuration = durationMs * 1.5 / 1000

        func difference(_ stiffness: Double) -> Double {
            let omega = (stiffness / mass).squareRoot() * zeta
            let decay = exp(-omega * settlingDuration)
            let xtk = (x0 + (v0 + x0 * omega) * settlingDuration) * decay
            let vtk = (x0 + (v0 + x0 * omega) * settlingDuration) * decay * -omega + (v0 + x0 * omega) * decay
            let e0 = energy(displacement: x0, velocity: v0, stiffness: stiffness)
            let etk = energy(displacement: xtk, velocity: vtk, stiffness: stiffness)
            return etk / e0 - energyThreshold
        }

        let precision = energyThreshold * 1e-3
        var low = Double.ulpOfOne
        var high = 8e3
        let direction: Double = difference(high) >= difference(low) ? 1 : -1
        var iterations = 100
        var current = (high + low) / 2
        while abs(difference(current)) > precision && iterations > 0 {
            iterations -= 1
            if difference(current) * direction < 0 {
                low = current
            } else {
                high = current
            }
            current = (low + high) / 2
        }
        return current
    }
}

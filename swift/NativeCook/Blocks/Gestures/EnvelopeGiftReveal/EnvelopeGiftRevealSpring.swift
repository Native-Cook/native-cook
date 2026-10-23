import Foundation

/// A damped spring as Reanimated 4's `withSpring({ stiffness, damping, mass, velocity })` runs it: each frame
/// continues from the previous frame's position and velocity with the closed-form solution, and the spring
/// ends when its energy falls to 6e-9 of the starting energy.
struct EnvelopeGiftRevealSpringRun {
    private static let energyThreshold = EnvelopeGiftRevealTokens.Clock.springEnergyThreshold
    /// A frame longer than this counts as this long (ms), like the frame callback's own clamp.
    private static let maxStepMs = EnvelopeGiftRevealTokens.Clock.maximumDeltaMs

    let target: Double
    private(set) var value: Double
    private var velocity: Double
    private var last: Double
    private let initialEnergy: Double
    private let zeta: Double
    private let omega0: Double
    private let omega1: Double
    private let stiffness: Double
    private let mass: Double

    /// Starts at `from` at time `now` (ms). A velocity that points away from the target is dropped, as Reanimated does.
    init(from: Double, to target: Double, velocity: Double, now: Double, config: EnvelopeGiftRevealSpringConfig) {
        self.target = target
        value = from
        var v = velocity
        if (target > from && v < 0) || (target < from && v > 0) { v = 0 }
        self.velocity = v
        last = now
        stiffness = config.stiffness
        mass = config.mass
        zeta = config.damping / (2 * sqrt(config.stiffness * config.mass))
        omega0 = sqrt(config.stiffness / config.mass)
        omega1 = zeta < 1 ? omega0 * sqrt(1 - zeta * zeta) : 0
        initialEnergy = Self.energy(displacement: from - target, velocity: v, stiffness: config.stiffness, mass: config.mass)
    }

    private static func energy(displacement: Double, velocity: Double, stiffness: Double, mass: Double) -> Double {
        0.5 * stiffness * displacement * displacement + 0.5 * mass * velocity * velocity
    }

    /// Moves the spring to time `now` (ms). Returns true once it has come to rest on the target.
    mutating func step(to now: Double) -> Bool {
        let t = min(max(now - last, 0), Self.maxStepMs) / 1000
        last = now
        let x0 = value - target
        let v0 = velocity
        if zeta < 1 {
            let sin1 = sin(omega1 * t)
            let cos1 = cos(omega1 * t)
            let envelope = exp(-zeta * omega0 * t)
            let fragment = envelope * (sin1 * ((v0 + zeta * omega0 * x0) / omega1) + x0 * cos1)
            value = target + fragment
            velocity = -zeta * omega0 * fragment + envelope * (cos1 * (v0 + zeta * omega0 * x0) - omega1 * x0 * sin1)
        } else {
            let envelope = exp(-omega0 * t)
            value = target + envelope * (x0 + (v0 + omega0 * x0) * t)
            velocity = envelope * -omega0 * (x0 + (v0 + omega0 * x0) * t) + envelope * (v0 + omega0 * x0)
        }
        let remaining = Self.energy(displacement: target - value, velocity: velocity, stiffness: stiffness, mass: mass)
        if initialEnergy == 0 || remaining / initialEnergy <= Self.energyThreshold {
            velocity = 0
            value = target
            return true
        }
        return false
    }
}

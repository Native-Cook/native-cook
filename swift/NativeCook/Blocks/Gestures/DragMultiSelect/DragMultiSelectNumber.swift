import Foundation
import Observation

/// One step of a number's motion, in the order Reanimated's `withDelay` / `withSequence` run them.
enum DragMultiSelectStep {
    case delay(ms: Double)
    case timing(to: Double, ms: Double, easing: DragMultiSelectEasing = .inOutQuad)
    case spring(to: Double, DragMultiSelectSpring)
    /// Reanimated's duration branch: `duration` and `dampingRatio` replace stiffness and damping.
    case durationSpring(to: Double, ms: Double, mass: Double = 4, dampingRatio: Double = 1)
}

/// An animated number that behaves like a Reanimated shared value: `set` jumps, `timing` and
/// `spring` start from the current value (a spring keeps the velocity it already has), and the
/// spring is Reanimated 4's closed-form step with its energy test for coming to rest.
@MainActor @Observable
final class DragMultiSelectNumber {
    private(set) var value: Double

    @ObservationIgnored private let clock: DragMultiSelectClock
    @ObservationIgnored private(set) var velocity = 0.0
    @ObservationIgnored private var pending: [DragMultiSelectStep] = []
    @ObservationIgnored private var run: Run?
    @ObservationIgnored var isQueued = false

    private enum Run {
        case delay(until: Double)
        case timing(from: Double, to: Double, start: Double, ms: Double, easing: DragMultiSelectEasing)
        case spring(Spring)
    }

    private struct Spring {
        var to: Double
        var config: DragMultiSelectSpring
        var last: Double
        var zeta: Double
        var omega0: Double
        var omega1: Double
        var initialEnergy: Double
    }

    /// Relative energy below which a spring snaps to its target (Reanimated's default).
    private static let energyThreshold = 6e-9
    /// A frame longer than this counts as this long, as in Reanimated's spring.
    private static let longestFrame = 0.064

    init(_ value: Double = 0, clock: DragMultiSelectClock) {
        self.value = value
        self.clock = clock
    }

    var isRunning: Bool { run != nil || !pending.isEmpty }

    /// Jumps to a value and cancels any motion.
    func set(_ newValue: Double) {
        run = nil
        pending = []
        velocity = 0
        write(newValue)
    }

    /// Stops where it is.
    func freeze() {
        run = nil
        pending = []
        velocity = 0
    }

    func timing(to target: Double, ms: Double, easing: DragMultiSelectEasing = .inOutQuad, delay: Double = 0) {
        var steps: [DragMultiSelectStep] = []
        if delay > 0 { steps.append(.delay(ms: delay)) }
        steps.append(.timing(to: target, ms: ms, easing: easing))
        animate(steps)
    }

    func spring(to target: Double, _ config: DragMultiSelectSpring, delay: Double = 0) {
        var steps: [DragMultiSelectStep] = []
        if delay > 0 { steps.append(.delay(ms: delay)) }
        steps.append(.spring(to: target, config))
        animate(steps)
    }

    func durationSpring(to target: Double, ms: Double, mass: Double = 4, dampingRatio: Double = 1) {
        animate([.durationSpring(to: target, ms: ms, mass: mass, dampingRatio: dampingRatio)])
    }

    func animate(_ steps: [DragMultiSelectStep]) {
        run = nil
        pending = steps
        clock.enqueue(self)
        advance(to: clock.now)
    }

    func advance(to now: Double) {
        while true {
            guard let current = run else {
                guard !pending.isEmpty else { return }
                start(pending.removeFirst(), at: now)
                continue
            }
            switch current {
            case .delay(let until):
                if now < until { return }
                run = nil
            case .timing(let from, let to, let start, let ms, let easing):
                let elapsed = max(now - start, 0) * 1000
                if elapsed >= ms {
                    write(to)
                    run = nil
                } else {
                    write(from + (to - from) * easing.value(elapsed / ms))
                    return
                }
            case .spring(var spring):
                if step(&spring, to: now) {
                    run = nil
                } else {
                    run = .spring(spring)
                    return
                }
            }
        }
    }

    private func start(_ step: DragMultiSelectStep, at now: Double) {
        switch step {
        case .delay(let ms):
            run = ms > 0 ? .delay(until: now + ms / 1000) : nil
        case .timing(let to, let ms, let easing):
            velocity = 0
            run = .timing(from: value, to: to, start: now, ms: ms, easing: easing)
        case .spring(let to, let config):
            startSpring(to: to, config: config, at: now)
        case .durationSpring(let to, let ms, let mass, let dampingRatio):
            // `FadeInDown.duration(320).springify().damping(18)` enters Reanimated's duration
            // branch. The declared damping is ignored there; dampingRatio defaults to 1 and the
            // stiffness is fitted so the relative energy reaches the threshold at 1.5 × duration.
            let stiffness = Self.stiffnessMatchingDuration(
                displacement: value - to,
                velocity: velocity,
                durationMS: ms,
                mass: mass,
                dampingRatio: dampingRatio
            )
            guard stiffness > 0 else {
                set(to)
                return
            }
            let damping = dampingRatio * 2 * (stiffness * mass).squareRoot()
            startSpring(
                to: to,
                config: DragMultiSelectSpring(damping: damping, stiffness: stiffness, mass: mass),
                at: now
            )
        }
    }

    private func startSpring(to target: Double, config: DragMultiSelectSpring, at now: Double) {
        // Inertia that points away from the new target is dropped, as Reanimated does.
        if (target > value && velocity < 0) || (target < value && velocity > 0) { velocity = 0 }
        let zeta = config.damping / (2 * (config.stiffness * config.mass).squareRoot())
        let omega0 = (config.stiffness / config.mass).squareRoot()
        let omega1 = zeta < 1 ? omega0 * (1 - zeta * zeta).squareRoot() : 0
        let displacement = value - target
        run = .spring(Spring(
            to: target,
            config: config,
            last: now,
            zeta: zeta,
            omega0: omega0,
            omega1: omega1,
            initialEnergy: 0.5 * config.stiffness * displacement * displacement + 0.5 * config.mass * velocity * velocity
        ))
    }

    /// Port of Reanimated 4's `calculateNewStiffnessToMatchDuration` for the duration spring branch.
    private static func stiffnessMatchingDuration(
        displacement: Double,
        velocity: Double,
        durationMS: Double,
        mass: Double,
        dampingRatio: Double
    ) -> Double {
        guard displacement != 0 || velocity != 0 else { return 0 }
        let settlingDuration = durationMS * 1.5 / 1000

        func energyDifference(_ stiffness: Double) -> Double {
            let omega0 = (stiffness / mass).squareRoot() * dampingRatio
            let envelope = exp(-omega0 * settlingDuration)
            let position = (displacement + (velocity + displacement * omega0) * settlingDuration) * envelope
            let speed = position * -omega0 + (velocity + displacement * omega0) * envelope
            let initial = 0.5 * stiffness * displacement * displacement + 0.5 * mass * velocity * velocity
            let current = 0.5 * stiffness * position * position + 0.5 * mass * speed * speed
            return current / initial - energyThreshold
        }

        var lower = Double.ulpOfOne
        var upper = 8_000.0
        let direction = energyDifference(upper) >= energyDifference(lower) ? 1.0 : -1.0
        var current = (lower + upper) / 2
        let precision = energyThreshold * 1e-3
        var remaining = 100
        while abs(energyDifference(current)) > precision && remaining > 0 {
            remaining -= 1
            if energyDifference(current) * direction < 0 {
                lower = current
            } else {
                upper = current
            }
            current = (lower + upper) / 2
        }
        return current
    }

    /// Advances a spring to `now`; true when it has come to rest.
    private func step(_ spring: inout Spring, to now: Double) -> Bool {
        let t = min(max(now - spring.last, 0), Self.longestFrame)
        spring.last = now
        let x0 = value - spring.to
        let v0 = velocity
        let position: Double
        let speed: Double
        if spring.zeta < 1 {
            let sin1 = sin(spring.omega1 * t)
            let cos1 = cos(spring.omega1 * t)
            let envelope = exp(-spring.zeta * spring.omega0 * t)
            let fragment = envelope * (sin1 * ((v0 + spring.zeta * spring.omega0 * x0) / spring.omega1) + x0 * cos1)
            position = spring.to + fragment
            speed = -spring.zeta * spring.omega0 * fragment
                + envelope * (cos1 * (v0 + spring.zeta * spring.omega0 * x0) - spring.omega1 * x0 * sin1)
        } else {
            let envelope = exp(-spring.omega0 * t)
            position = spring.to + envelope * (x0 + (v0 + spring.omega0 * x0) * t)
            speed = envelope * -spring.omega0 * (x0 + (v0 + spring.omega0 * x0) * t)
                + envelope * (v0 + spring.omega0 * x0)
        }
        let off = spring.to - position
        let energy = 0.5 * spring.config.stiffness * off * off + 0.5 * spring.config.mass * speed * speed
        if spring.initialEnergy == 0 || energy / spring.initialEnergy <= Self.energyThreshold {
            velocity = 0
            write(spring.to)
            return true
        }
        velocity = speed
        write(position)
        return false
    }

    private func write(_ newValue: Double) {
        if value != newValue { value = newValue }
    }
}

import QuartzCore

// A spring the block steps itself, one display frame at a time, so the carousel's position is a
// plain number every reader (the 3D stage, the backdrop, the strip) can take on the same frame,
// and a finger can stop it mid-flight. Same model as Reanimated's withSpring, which the
// reference uses: analytic step, starts from rest, ends when the energy left is negligible.

struct ChooseYourBallSpring {
    var mass: Double
    var stiffness: Double
    var damping: Double

    /// Advances a spring at `position` moving at `velocity` toward `target` by `dt` seconds.
    func step(position: Double, velocity: Double, target: Double, dt: Double) -> (position: Double, velocity: Double) {
        let omega0 = (stiffness / mass).squareRoot()
        let zeta = damping / (2 * (stiffness * mass).squareRoot())
        let x0 = position - target
        let v0 = velocity

        if zeta < 1 {
            let omega1 = omega0 * (1 - zeta * zeta).squareRoot()
            let envelope = exp(-zeta * omega0 * dt)
            let sine = sin(omega1 * dt)
            let cosine = cos(omega1 * dt)
            let fragment = envelope * (sine * ((v0 + zeta * omega0 * x0) / omega1) + x0 * cosine)
            let speed = -zeta * omega0 * fragment
                + envelope * (cosine * (v0 + zeta * omega0 * x0) - omega1 * x0 * sine)
            return (target + fragment, speed)
        }

        let envelope = exp(-omega0 * dt)
        let fragment = envelope * (x0 + (v0 + omega0 * x0) * dt)
        let speed = envelope * (v0 * (dt * omega0 - 1) + dt * x0 * omega0 * omega0) * -1
        return (target + fragment, speed)
    }

    func energy(displacement: Double, velocity: Double) -> Double {
        0.5 * stiffness * displacement * displacement + 0.5 * mass * velocity * velocity
    }
}

/// Runs one spring on the display's clock and reports every frame's value.
@MainActor
final class ChooseYourBallSpringRunner {
    /// Called with the new value on each frame, and once more with the exact target at the end.
    var onChange: ((Double) -> Void)?
    /// Called after the last frame of a spring that ran to rest (not one that was stopped).
    var onFinish: ((Double) -> Void)?

    private(set) var isRunning = false
    private var spring = ChooseYourBallSpring(mass: 1, stiffness: 100, damping: 10)
    private var position = 0.0
    private var velocity = 0.0
    private var target = 0.0
    private var initialEnergy = 0.0
    private var lastTimestamp: CFTimeInterval = 0
    private let clock = Clock()

    /// Share of the starting energy below which the spring counts as at rest (Reanimated's value).
    private static let energyThreshold = 6e-9

    func run(from start: Double, to end: Double, spring: ChooseYourBallSpring) {
        stop()
        self.spring = spring
        position = start
        velocity = 0
        target = end
        initialEnergy = spring.energy(displacement: end - start, velocity: 0)
        guard initialEnergy > 0 else {
            onChange?(end)
            onFinish?(end)
            return
        }
        isRunning = true
        lastTimestamp = 0
        clock.start { [weak self] link in
            // The runner is gone: nothing is left to drive, so the link goes too.
            guard let self else { return link.invalidate() }
            self.tick(link)
        }
    }

    /// Stops where it is. The value stays at the last one reported.
    func stop() {
        clock.stop()
        isRunning = false
    }

    private func tick(_ link: CADisplayLink) {
        // The first frame only starts the clock, as the reference's animation does.
        let now = link.targetTimestamp
        defer { lastTimestamp = now }
        guard lastTimestamp > 0 else { return }
        let dt = min(max(now - lastTimestamp, 0), 0.064)

        (position, velocity) = spring.step(position: position, velocity: velocity, target: target, dt: dt)
        let energy = spring.energy(displacement: target - position, velocity: velocity)
        if energy / initialEnergy <= Self.energyThreshold {
            stop()
            position = target
            onChange?(target)
            onFinish?(target)
        } else {
            onChange?(position)
        }
    }

    /// Owns the display link, and invalidates it when it is released itself: a runner whose
    /// owner goes away in the middle of a spring takes its link off the run loop with it,
    /// instead of leaving it there for good. Not bound to the main actor, so that its `deinit`
    /// may touch the link.
    private final class Clock {
        private var link: CADisplayLink?

        func start(_ handler: @escaping (CADisplayLink) -> Void) {
            stop()
            let link = CADisplayLink(target: Target(handler: handler), selector: #selector(Target.tick(_:)))
            link.preferredFrameRateRange = CAFrameRateRange(minimum: 60, maximum: 120, preferred: 120)
            link.add(to: .main, forMode: .common)
            self.link = link
        }

        func stop() {
            link?.invalidate()
            link = nil
        }

        deinit {
            link?.invalidate()
        }
    }

    /// CADisplayLink keeps its target alive; this keeps the runner itself out of that cycle.
    private final class Target: NSObject {
        let handler: (CADisplayLink) -> Void
        init(handler: @escaping (CADisplayLink) -> Void) { self.handler = handler }
        @objc func tick(_ link: CADisplayLink) { handler(link) }
    }
}

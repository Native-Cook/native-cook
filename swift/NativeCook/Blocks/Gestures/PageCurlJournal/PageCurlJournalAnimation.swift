import SwiftUI

/// Reanimated-style animation specs: `withSpring`, `withTiming`, `withDelay`, `withSequence`.
indirect enum PageCurlJournalAnimation {
    case spring(target: Double, config: PageCurlJournalMotion.Spring, velocity: Double = 0, completion: ((Bool) -> Void)? = nil)
    case timing(target: Double, durationMs: Double, easing: (Double) -> Double = PageCurlJournalEasing.quadInOut, completion: ((Bool) -> Void)? = nil)
    case delay(ms: Double, next: PageCurlJournalAnimation)
    case sequence([PageCurlJournalAnimation])

    @MainActor
    fileprivate func makeRunner() -> PageCurlJournalRunner {
        switch self {
        case let .spring(target, config, velocity, completion):
            PageCurlJournalSpringRunner(target: target, config: config, initialVelocity: velocity, completion: completion)
        case let .timing(target, durationMs, easing, completion):
            PageCurlJournalTimingRunner(target: target, durationMs: durationMs, easing: easing, completion: completion)
        case let .delay(ms, next):
            PageCurlJournalDelayRunner(delayMs: ms, next: next.makeRunner())
        case let .sequence(items):
            PageCurlJournalSequenceRunner(items: items.map { $0.makeRunner() })
        }
    }
}

/// A shared value: a number that a spec can animate, advanced once per frame by `step(now:)`.
/// Assigning a plain number (or starting another spec) cancels the running one; its completion sees `false`.
@MainActor
final class PageCurlJournalValue {
    private(set) var value: Double
    private var runner: PageCurlJournalRunner?
    private var started = false

    init(_ value: Double) {
        self.value = value
    }

    var isAnimating: Bool { runner != nil }

    func set(_ newValue: Double) {
        let fired = cancelRunner()
        value = newValue
        fired.forEach { $0() }
    }

    func animate(_ spec: PageCurlJournalAnimation) {
        let fired = cancelRunner()
        runner = spec.makeRunner()
        started = false
        fired.forEach { $0() }
    }

    /// `cancelAnimation`: the value stays where it is.
    func cancel() {
        cancelRunner().forEach { $0() }
    }

    func step(now: Double) {
        guard let active = runner else { return }
        if !started {
            active.begin(from: value, now: now)
            started = true
        }
        var fired: [() -> Void] = []
        let result = active.frame(now: now, fired: &fired)
        value = result.value
        if result.done, runner === active { runner = nil }
        fired.forEach { $0() }
    }

    private func cancelRunner() -> [() -> Void] {
        guard let active = runner else { return [] }
        runner = nil
        var fired: [() -> Void] = []
        active.cancel(fired: &fired)
        return fired
    }
}

enum PageCurlJournalEasing {
    /// Reanimated's default for `withTiming`: Easing.inOut(Easing.quad).
    static func quadInOut(_ t: Double) -> Double {
        t < 0.5 ? 2 * t * t : 1 - 2 * (1 - t) * (1 - t)
    }

    private static let bezier = UnitCurve.bezier(
        startControlPoint: UnitPoint(x: 0.77, y: 0),
        endControlPoint: UnitPoint(x: 0.175, y: 1)
    )
    private static let outBezier = UnitCurve.bezier(
        startControlPoint: UnitPoint(x: 0.23, y: 1),
        endControlPoint: UnitPoint(x: 0.32, y: 1)
    )

    /// Easing.bezier(0.77, 0, 0.175, 1), the block's EASE_IN_OUT.
    static func inOut(_ t: Double) -> Double {
        bezier.value(at: t)
    }

    /// Easing.bezier(0.23, 1, 0.32, 1), the block's EASE_OUT.
    static func out(_ t: Double) -> Double {
        outBezier.value(at: t)
    }
}

@MainActor
class PageCurlJournalRunner {
    func begin(from value: Double, now: Double) {}

    func frame(now: Double, fired: inout [() -> Void]) -> (value: Double, done: Bool) {
        (0, true)
    }

    func cancel(fired: inout [() -> Void]) {}
}

/// Reanimated 4's spring: integrate each frame (capped at 64 ms) and stop at its relative energy threshold.
@MainActor
final class PageCurlJournalSpringRunner: PageCurlJournalRunner {
    private let target: Double
    private let config: PageCurlJournalMotion.Spring
    private let initialVelocity: Double
    private let completion: ((Bool) -> Void)?
    private var current = 0.0
    private var velocity = 0.0
    private var zeta = 0.0
    private var omega0 = 0.0
    private var omega1 = 0.0
    private var stiffness = 0.0
    private var initialEnergy = 0.0
    private var lastTime = 0.0
    private static let energyThreshold = 6e-9

    init(target: Double, config: PageCurlJournalMotion.Spring, initialVelocity: Double, completion: ((Bool) -> Void)?) {
        self.target = target
        self.config = config
        self.initialVelocity = initialVelocity
        self.completion = completion
    }

    override func begin(from value: Double, now: Double) {
        current = value
        lastTime = now
        velocity = (target > value && initialVelocity < 0) || (target < value && initialVelocity > 0) ? 0 : initialVelocity
        if let durationMs = config.durationMs, let ratio = config.dampingRatio {
            stiffness = Self.durationStiffness(displacement: value - target, velocity: velocity,
                                                durationMs: durationMs, ratio: ratio, mass: config.mass)
            zeta = ratio
        } else {
            stiffness = config.stiffness
            zeta = config.damping / (2 * (stiffness * config.mass).squareRoot())
        }
        omega0 = (stiffness / config.mass).squareRoot()
        omega1 = omega0 * max(0, 1 - zeta * zeta).squareRoot()
        initialEnergy = Self.energy(value - target, initialVelocity, stiffness: stiffness, mass: config.mass)
    }

    override func frame(now: Double, fired: inout [() -> Void]) -> (value: Double, done: Bool) {
        let elapsed = min(max(now - lastTime, 0), 0.064)
        lastTime = now
        let x0 = current - target
        let v0 = velocity
        if zeta < 1 {
            let sin1 = sin(omega1 * elapsed)
            let cos1 = cos(omega1 * elapsed)
            let envelope = exp(-zeta * omega0 * elapsed)
            let fragment = envelope * (sin1 * ((v0 + zeta * omega0 * x0) / omega1) + x0 * cos1)
            current = target + fragment
            velocity = -zeta * omega0 * fragment + envelope * (cos1 * (v0 + zeta * omega0 * x0) - omega1 * x0 * sin1)
        } else {
            let envelope = exp(-omega0 * elapsed)
            current = target + envelope * (x0 + (v0 + omega0 * x0) * elapsed)
            velocity = envelope * (-omega0 * (x0 + (v0 + omega0 * x0) * elapsed) + v0 + omega0 * x0)
        }
        if initialEnergy == 0 || Self.energy(target - current, velocity, stiffness: stiffness, mass: config.mass) / initialEnergy <= Self.energyThreshold {
            return finish(&fired)
        }
        return (current, false)
    }

    override func cancel(fired: inout [() -> Void]) {
        if let completion { fired.append { completion(false) } }
    }

    private func finish(_ fired: inout [() -> Void]) -> (value: Double, done: Bool) {
        current = target
        velocity = 0
        if let completion { fired.append { completion(true) } }
        return (target, true)
    }

    private static func energy(_ displacement: Double, _ velocity: Double, stiffness: Double, mass: Double) -> Double {
        0.5 * stiffness * displacement * displacement + 0.5 * mass * velocity * velocity
    }

    /// Reanimated's duration spring solves for stiffness at 1.5 × the requested perceptual duration.
    private static func durationStiffness(displacement: Double, velocity: Double, durationMs: Double,
                                          ratio: Double, mass: Double) -> Double {
        let settling = durationMs * 1.5 / 1000
        func difference(_ stiffness: Double) -> Double {
            let omega = (stiffness / mass).squareRoot() * ratio
            let envelope = exp(-omega * settling)
            let term = displacement + (velocity + displacement * omega) * settling
            let position = term * envelope
            let speed = -term * envelope * omega + (velocity + displacement * omega) * envelope
            let initial = energy(displacement, velocity, stiffness: stiffness, mass: mass)
            return (initial == 0 ? 0 : energy(position, speed, stiffness: stiffness, mass: mass) / initial) - energyThreshold
        }
        var low = Double.ulpOfOne
        var high = 8000.0
        let direction = difference(high) >= difference(low) ? 1.0 : -1.0
        var mid = (low + high) / 2
        for _ in 0..<100 {
            let diff = difference(mid)
            if abs(diff) <= energyThreshold * 1e-3 { break }
            if diff * direction < 0 { low = mid } else { high = mid }
            mid = (low + high) / 2
        }
        return mid
    }
}

/// withTiming: a fixed duration through an easing curve.
@MainActor
final class PageCurlJournalTimingRunner: PageCurlJournalRunner {
    private let target: Double
    private let durationMs: Double
    private let easing: (Double) -> Double
    private let completion: ((Bool) -> Void)?
    private var startValue = 0.0
    private var startTime = 0.0

    init(target: Double, durationMs: Double, easing: @escaping (Double) -> Double, completion: ((Bool) -> Void)?) {
        self.target = target
        self.durationMs = durationMs
        self.easing = easing
        self.completion = completion
    }

    override func begin(from value: Double, now: Double) {
        startValue = value
        startTime = now
    }

    override func frame(now: Double, fired: inout [() -> Void]) -> (value: Double, done: Bool) {
        let runtime = (now - startTime) * 1000
        if runtime >= durationMs {
            if let completion { fired.append { completion(true) } }
            return (target, true)
        }
        return (startValue + (target - startValue) * easing(runtime / durationMs), false)
    }

    override func cancel(fired: inout [() -> Void]) {
        if let completion { fired.append { completion(false) } }
    }
}

/// withDelay: holds the value, then runs the next animation from where it stands.
@MainActor
final class PageCurlJournalDelayRunner: PageCurlJournalRunner {
    private let delayMs: Double
    private let next: PageCurlJournalRunner
    private var startTime = 0.0
    private var current = 0.0
    private var nextStarted = false

    init(delayMs: Double, next: PageCurlJournalRunner) {
        self.delayMs = delayMs
        self.next = next
    }

    override func begin(from value: Double, now: Double) {
        startTime = now
        current = value
    }

    override func frame(now: Double, fired: inout [() -> Void]) -> (value: Double, done: Bool) {
        guard (now - startTime) * 1000 > delayMs else { return (current, false) }
        if !nextStarted {
            next.begin(from: current, now: now)
            nextStarted = true
        }
        let result = next.frame(now: now, fired: &fired)
        current = result.value
        return result
    }

    override func cancel(fired: inout [() -> Void]) {
        next.cancel(fired: &fired)
    }
}

/// withSequence: each animation starts where the one before it ended.
@MainActor
final class PageCurlJournalSequenceRunner: PageCurlJournalRunner {
    private let items: [PageCurlJournalRunner]
    private var index = 0

    init(items: [PageCurlJournalRunner]) {
        self.items = items
    }

    override func begin(from value: Double, now: Double) {
        index = 0
        items[0].begin(from: value, now: now)
    }

    override func frame(now: Double, fired: inout [() -> Void]) -> (value: Double, done: Bool) {
        let result = items[index].frame(now: now, fired: &fired)
        guard result.done else { return result }
        if index == items.count - 1 { return result }
        index += 1
        items[index].begin(from: result.value, now: now)
        return (result.value, false)
    }

    override func cancel(fired: inout [() -> Void]) {
        items[index].cancel(fired: &fired)
    }
}

import Foundation

/// Reanimated's analytic physical spring (including inherited velocity and its energy cutoff).
struct FlipFocusBadgeValue {
    struct Config {
        var damping: Double, stiffness: Double, mass = 1.0
        static let snap = Config(damping: 30.5, stiffness: 322.3)
        static let glide = Config(damping: 22.1, stiffness: 157.9)
        static let settle = Config(damping: 14, stiffness: 110, mass: 1.2)
        static let tilt = Config(damping: 16, stiffness: 140)
        static let rise = Config(damping: 11, stiffness: 160)
        static let pop = Config(damping: 19, stiffness: 195)
    }
    enum Mode { case idle, spring, timing }
    var value: Double
    private(set) var velocity = 0.0
    private(set) var mode = Mode.idle
    private var config = Config.snap
    private var target = 0.0, from = 0.0, elapsed = 0.0, duration = 0.0, delay = 0.0, initialEnergy = 0.0
    var active: Bool { mode != .idle }
    init(_ value: Double) { self.value = value }
    mutating func set(_ value: Double) { self.value = value; mode = .idle; velocity = 0 }
    mutating func cancel() { mode = .idle; velocity = 0 }
    mutating func spring(_ to: Double, _ config: Config, velocity added: Double = 0, delay: Double = 0, reduce: Bool = false) {
        let v = mode == .spring ? velocity + added : added
        self.velocity = (to > value && v < 0) || (to < value && v > 0) ? 0 : v
        self.target = to; self.config = config; self.delay = delay; elapsed = 0
        initialEnergy = 0.5 * config.stiffness * pow(value - to, 2) + 0.5 * config.mass * added * added
        mode = .spring
        if reduce { set(to) }
    }
    mutating func timing(_ to: Double, duration: Double, delay: Double = 0, reduce: Bool = false) {
        from = value; target = to; self.duration = duration; self.delay = delay; elapsed = 0; velocity = 0; mode = .timing
        if reduce { set(to) }
    }
    /// Returns true exactly when an animation's completion callback would run.
    @discardableResult mutating func step(_ dt: Double) -> Bool {
        guard active else { return false }
        elapsed += dt
        if elapsed < delay { return false }
        let delta = min(dt, max(0, elapsed - delay))
        if mode == .timing {
            value = from + (target - from) * FlipFocusBadgeMotion.timing((elapsed - delay) / duration)
            if elapsed - delay >= duration { set(target); return true }
            return false
        }
        let k = config.stiffness, mass = config.mass, c = config.damping
        let w = sqrt(k / mass), z = c / (2 * sqrt(k * mass)), x = value - target, v = velocity
        if z < 1 {
            let wd = w * sqrt(1 - z * z), sn = sin(wd * delta), cs = cos(wd * delta), e = exp(-z * w * delta)
            let frag = e * (sn * (v + z * w * x) / wd + x * cs)
            value = target + frag
            velocity = -z * w * frag + e * (cs * (v + z * w * x) - wd * x * sn)
        } else {
            let e = exp(-w * delta), q = x + (v + w * x) * delta
            value = target + e * q; velocity = e * (-w * q + v + w * x)
        }
        let energy = 0.5 * k * pow(target - value, 2) + 0.5 * mass * velocity * velocity
        if initialEnergy == 0 || energy / initialEnergy <= 6e-9 { set(target); return true }
        return false
    }
}

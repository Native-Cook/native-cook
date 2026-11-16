import Foundation

/// Reanimated-style scalar: analytic mass-1 springs retain velocity when retargeted.
struct SparkSphereScoreAnimation {
    enum Mode { case still, spring, timing }
    var value: Double
    var velocity = 0.0
    private var target: Double
    private var mode = Mode.still
    private var stiffness = 60.0, damping = 14.0
    private var elapsed = 0.0, duration = 0.0, from = 0.0, initialEnergy = 0.0
    var moving: Bool { mode != .still }
    init(_ value: Double) { self.value = value; target = value }
    mutating func set(_ v: Double) { value = v; target = v; velocity = 0; mode = .still }
    mutating func spring(_ to: Double, _ k: Double, _ c: Double, velocity incoming: Double? = nil) {
        if mode == .spring && target == to && incoming == nil && stiffness == k && damping == c { return }
        target = to; stiffness = k; damping = c; mode = .spring
        let suppliedVelocity = incoming ?? 0
        velocity += suppliedVelocity
        // The installed Reanimated clips a velocity pointing away from the new target on onStart.
        if (target > value && velocity < 0) || (target < value && velocity > 0) { velocity = 0 }
        // RN uses config.velocity here, even when the running velocity was inherited.
        initialEnergy = 0.5 * stiffness * pow(value - target, 2) + 0.5 * suppliedVelocity * suppliedVelocity
    }
    mutating func timing(_ to: Double, _ seconds: Double) {
        target = to; from = value; elapsed = 0; duration = seconds; mode = .timing; velocity = 0
    }
    mutating func step(_ dt: Double, reduce: Bool = false) {
        if mode == .still { return }
        // Reanimated's default ReduceMotion.System short-circuits both timing and spring animations.
        if reduce { set(target); return }
        if mode == .timing {
            elapsed += dt
            let t = min(elapsed / duration, 1)
            let ease = t < 0.5 ? 2 * t * t : 1 - pow(-2 * t + 2, 2) / 2
            value = from + (target - from) * ease
            if t >= 1 { set(target) }
            return
        }
        let x = value - target, a = damping / 2, omega = sqrt(stiffness), zeta = min(1, a / omega)
        let envelope = exp(-zeta * omega * dt)
        if zeta < 1 {
            let wd = omega * sqrt(1 - zeta * zeta), b = (velocity + a * x) / wd
            let cs = cos(wd * dt), sn = sin(wd * dt)
            value = target + envelope * (x * cs + b * sn)
            velocity = envelope * ((b * wd - a * x) * cs - (x * wd + a * b) * sn)
        } else {
            let b = velocity + omega * x
            value = target + envelope * (x + b * dt)
            velocity = envelope * (b - omega * (x + b * dt))
        }
        let energy = 0.5 * stiffness * pow(value - target, 2) + 0.5 * velocity * velocity
        if initialEnergy == 0 || energy / initialEnergy <= 6e-9 { set(target) }
    }
}

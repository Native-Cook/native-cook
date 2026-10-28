import Foundation

// The clocks and pure motion functions of the carousel. Mirrors HabitatMotion.ts: one ambient loop
// of 13.5 s for idle life, the carousel's timeline (3 s at rest, 1.5 s crossing per specimen), a
// move's progress (`power`, 1.8 s) and its effect clock (`fx`, seconds from the move's start to
// 3.9 s, while what it throws lands).

enum HabitatSpecimenCarouselMotion {
    static let loop = 13.5
    static let hold = 3 / loop
    static let cross = 1.5 / loop
    static let slot = hold + cross
    /// Each specimen rests for 3 s, performs its signature move, then crosses for 1.5 s.
    static let snapTimes: [Double] = [0, slot, slot * 2]
    static let signatureMS = 1800.0
    /// One landing squash after a specimen arrives (HabitatRig.landEnvelope).
    static let landMS = 620.0
    static let signatureStart = 0.55 / loop
    static let signature = signatureMS / 1000 / loop
    /// How long a move's particles outlive the move itself (seconds from its start).
    static let fxSeconds = 3.9
    /// How far the specimens drift across a crossing, as a fraction of the screen width.
    static let exchangeDrift = 0.16

    // Choose Your Ball's pan rules (ballPickerGeometry.ts).
    static let flingProjection = 0.18
    static let panReach = 1.0
    static let panResist = 6.0

    static func wrapPosition(_ value: Double) -> Double {
        ((value.truncatingRemainder(dividingBy: 3)) + 3).truncatingRemainder(dividingBy: 3)
    }

    static func specimenDistance(_ index: Int, _ position: Double) -> Double {
        wrapPosition(Double(index) - position + 1.5) - 1.5
    }

    static func clamp(_ value: Double, _ low: Double = 0, _ high: Double = 1) -> Double {
        max(low, min(high, value))
    }

    /// JavaScript's Math.round: halves go up.
    static func round(_ value: Double) -> Double { (value + 0.5).rounded(.down) }

    static func easeCrossing(_ x: Double) -> Double {
        var t = clamp(x)
        for _ in 0..<7 {
            let a = 1 - t
            let delta = 3 * a * a * t * 0.77 + 3 * a * t * t * 0.175 + t * t * t - x
            let derivative = 3 * a * a * 0.77 + 6 * a * t * (0.175 - 0.77) + 3 * t * t * (1 - 0.175)
            if abs(derivative) < 0.00001 { break }
            t = clamp(t - delta / derivative)
        }
        return 3 * (1 - t) * t * t + t * t * t
    }

    static func slotOf(_ progress: Double) -> (index: Int, local: Double) {
        let p = clamp(progress)
        let index = min(2, Int((p / slot).rounded(.down)))
        return (index, p - Double(index) * slot)
    }

    static func timelinePosition(_ progress: Double) -> Double {
        let (index, local) = slotOf(progress)
        return local < hold ? Double(index) : Double(index) + easeCrossing((local - hold) / cross)
    }

    /// Signature progress (0..1) while a resting specimen performs; 0 otherwise.
    static func timelineSignature(_ progress: Double) -> Double {
        let (_, local) = slotOf(progress)
        let t = (local - signatureStart) / signature
        return t > 0 && t < 1 ? t : 0
    }

    /// The specimen whose slot this is: it performs, and its move's particles keep belonging to it.
    static func timelinePerformer(_ progress: Double) -> Int { slotOf(progress).index }

    /// Seconds since the resting specimen's move began, for its particles; -1 outside a move.
    static func timelineEffect(_ progress: Double) -> Double {
        let (_, local) = slotOf(progress)
        let seconds = (local - signatureStart) * loop
        return seconds > 0 && seconds < fxSeconds ? seconds : -1
    }

    /// The demo visits every panel: About, Base stats (morphing across two swipes), Evolution.
    static func timelineTab(_ progress: Double) -> Int {
        let (index, local) = slotOf(progress)
        if index == 0 { return local < hold - 0.5 / loop ? 0 : 1 }
        if index == 1 { return 1 }
        if local < 0.2 / loop { return 1 }
        return local < hold ? 2 : 0
    }

    /// Anticipation and release envelope of a signature move.
    static func signatureEnvelope(_ t: Double) -> Double {
        if t <= 0 || t >= 1 { return 0 }
        let rise = clamp((t - 0.08) / 0.14)
        let fall = clamp((t - 0.6) / 0.4)
        return rise * rise * (3 - 2 * rise) * (1 - fall * fall * (3 - 2 * fall))
    }

    struct Pose { var sx = 1.0, sy = 1.0, tx = 0.0, ty = 0.0 }

    /// Squash on the ground, recoil, then a damped settle. Origin is the creature's feet.
    static func signaturePose(_ t: Double, _ index: Int) -> Pose {
        if t <= 0 || t >= 1 { return Pose() }
        let crouch = clamp(t / 0.11)
        let anticipation = t < 0.12 ? crouch * crouch : 1 - clamp((t - 0.12) / 0.04)
        let seconds = max(0, t - 0.12) * (signatureMS / 1000)
        let spring = t < 0.12 ? 0 : exp(-seconds * 6.5) * sin(seconds * 21)
        let lift = index == 0 ? 1.5 : index == 2 ? 1.2 : 0.6
        return Pose(
            sx: 1 + 0.024 * anticipation - 0.018 * spring,
            sy: 1 - 0.042 * anticipation + 0.034 * spring * lift,
            tx: index == 1 ? -5 * spring : 0,
            ty: 2.5 * anticipation - 5 * max(0, spring) * lift
        )
    }

    struct Exchange { var role = -1, f = 0.0, drift = 0.0, scale = 1.0 }

    /// The exchange, for flat cut-out art: the incoming habitat's front wipes across the stage and
    /// brings its specimen with it. `role` 0 is outgoing, 1 incoming, -1 off stage.
    static func exchangePose(_ index: Int, _ position: Double, _ reduced: Bool) -> Exchange {
        let p = wrapPosition(reduced ? round(position) : position)
        let from = Int(p.rounded(.down)) % 3
        let f = reduced ? 0 : p - p.rounded(.down)
        if index == from { return Exchange(role: 0, f: f, drift: -0.5 * f * f, scale: 1 - 0.05 * f) }
        let u = 1 - f
        if index == (from + 1) % 3 && f > 0.0005 { return Exchange(role: 1, f: f, drift: u * u, scale: 1 + 0.05 * u * u) }
        return Exchange(role: -1, f: f, drift: 0, scale: 1)
    }

    /// A signed current follows both the finger's travel and its velocity.
    static func gestureInfluence(_ translation: Double, _ velocity: Double, _ gap: Double) -> Double {
        clamp(velocity / (gap * 4) + clamp(translation / gap, -1, 1) * 0.18, -1, 1)
    }

    /// The seam of the habitat front at one height; the twin of the shaders' `seamLine`.
    static func seamLine(_ y: Double, _ f: Double, _ phase: Double) -> Double {
        let bow = sin(Double.pi * f)
        return 1.08 - 1.16 * f + bow * (0.04 * sin(y * 5.5 + phase) + 0.018 * sin(y * 14 - phase * 2 + 1.3))
    }
}

// MARK: - Animated values (Reanimated's withTiming / withSpring, stepped on the display's clock)

/// A bezier easing, the same as Easing.bezier.
struct HabitatSpecimenCarouselBezier {
    let x1: Double, y1: Double, x2: Double, y2: Double
    static let linear = HabitatSpecimenCarouselBezier(x1: 0, y1: 0, x2: 1, y2: 1)
    /// Easing.bezier(0.23, 1, 0.32, 1)
    static let pressIn = HabitatSpecimenCarouselBezier(x1: 0.23, y1: 1, x2: 0.32, y2: 1)

    func value(_ x: Double) -> Double {
        if x <= 0 { return 0 }
        if x >= 1 { return 1 }
        if x1 == y1 && x2 == y2 { return x }
        func sample(_ t: Double, _ a: Double, _ b: Double) -> Double {
            let s = 1 - t
            return 3 * s * s * t * a + 3 * s * t * t * b + t * t * t
        }
        var lo = 0.0, hi = 1.0, t = x
        for _ in 0..<40 {
            let current = sample(t, x1, x2)
            if abs(current - x) < 1e-7 { break }
            if current < x { lo = t } else { hi = t }
            t = (lo + hi) / 2
        }
        return sample(t, y1, y2)
    }
}

struct HabitatSpecimenCarouselSpringConfig {
    var mass = 1.0
    var stiffness = 100.0
    var damping = 10.0
    /// Set for a spring given by `duration` and `dampingRatio`: the stiffness is solved from them.
    var duration: Double?
    var dampingRatio: Double?
    var velocity = 0.0
    /// ReduceMotion.Always: the animation is skipped and the value jumps.
    var skip = false

    /// SNAP_SPRING (ballPickerGeometry.ts).
    static let snap = HabitatSpecimenCarouselSpringConfig(mass: 1, stiffness: 130, damping: 18)
    static func timed(_ duration: Double, _ ratio: Double) -> HabitatSpecimenCarouselSpringConfig {
        HabitatSpecimenCarouselSpringConfig(duration: duration, dampingRatio: ratio)
    }
}

/// A shared value that can be set, or animated by a timing or a spring that takes over from the
/// value (and the velocity) it has now. The animation starts on the frame clock it is given.
final class HabitatSpecimenCarouselValue {
    var value: Double
    private enum Kind { case none, timing, spring }
    private var kind = Kind.none
    // timing
    private var from = 0.0, to = 0.0, startMS = 0.0, durationMS = 0.0
    private var easing = HabitatSpecimenCarouselBezier.linear
    // spring
    private var velocity = 0.0
    private var lastMS = 0.0
    private var zeta = 0.0, omega0 = 0.0, omega1 = 0.0
    private var stiffness = 0.0, mass = 1.0
    private var initialEnergy = 0.0

    init(_ value: Double) { self.value = value }

    var isAnimating: Bool { kind != .none }

    func set(_ next: Double) {
        kind = .none
        velocity = 0
        value = next
    }

    func cancel() {
        kind = .none
        velocity = 0
    }

    func timing(to target: Double, durationMS: Double, easing: HabitatSpecimenCarouselBezier, now: Double) {
        velocity = 0
        kind = .timing
        from = value
        to = target
        startMS = now
        self.durationMS = durationMS
        self.easing = easing
    }

    func spring(to target: Double, config: HabitatSpecimenCarouselSpringConfig, now: Double) {
        if config.skip {
            set(target)
            return
        }
        let x0 = value - target
        // Reanimated carries the velocity of the spring it replaces, and drops one that would carry
        // the value away from the new target.
        var v0 = kind == .spring ? velocity + config.velocity : config.velocity
        if (target > value && v0 < 0) || (target < value && v0 > 0) { v0 = 0 }
        var k = config.stiffness
        mass = config.mass
        if let duration = config.duration, let ratio = config.dampingRatio {
            k = Self.stiffness(x0: x0, v0: v0, duration: duration, ratio: ratio, mass: mass)
            zeta = ratio
            omega0 = (k / mass).squareRoot()
        } else {
            zeta = config.damping / (2 * (k * mass).squareRoot())
            omega0 = (k / mass).squareRoot()
        }
        omega1 = zeta < 1 ? omega0 * (1 - zeta * zeta).squareRoot() : 0
        stiffness = k
        kind = .spring
        to = target
        velocity = v0
        initialEnergy = Self.energy(x0, config.velocity, k, mass)
        lastMS = now
    }

    private static func energy(_ displacement: Double, _ velocity: Double, _ stiffness: Double, _ mass: Double) -> Double {
        0.5 * stiffness * displacement * displacement + 0.5 * mass * velocity * velocity
    }

    /// calculateNewStiffnessToMatchDuration: bisection on the energy left after 1.5 x the duration.
    private static func stiffness(x0: Double, v0: Double, duration: Double, ratio zeta: Double, mass m: Double) -> Double {
        let threshold = 6e-9
        func difference(_ k: Double) -> Double {
            let settling = duration * 1.5 / 1000
            let w = (k / m).squareRoot() * zeta
            let envelope = exp(-w * settling)
            let xtk = (x0 + (v0 + x0 * w) * settling) * envelope
            let vtk = (x0 + (v0 + x0 * w) * settling) * envelope * -w + (v0 + x0 * w) * envelope
            let e0 = energy(x0, v0, k, m)
            let etk = energy(xtk, vtk, k, m)
            return etk / e0 - threshold
        }
        let precision = threshold * 1e-3
        var lo = Double.ulpOfOne, hi = 8e3
        let direction: Double = difference(hi) >= difference(lo) ? 1 : -1
        var current = (hi + lo) / 2
        var left = 100
        while abs(difference(current)) > precision && left > 0 {
            left -= 1
            if difference(current) * direction < 0 { lo = current } else { hi = current }
            current = (lo + hi) / 2
        }
        return current
    }

    /// Advances the animation to the frame at `now` (milliseconds).
    func step(now: Double) {
        switch kind {
        case .none:
            return
        case .timing:
            let progress = durationMS <= 0 ? 1 : (now - startMS) / durationMS
            if progress >= 1 {
                value = to
                kind = .none
            } else {
                value = from + (to - from) * easing.value(max(0, progress))
            }
        case .spring:
            let t = min(max(now - lastMS, 0), 64) / 1000
            lastMS = now
            let v0 = velocity
            let x0 = value - to
            var position: Double, speed: Double
            if zeta < 1 {
                let sine = sin(omega1 * t), cosine = cos(omega1 * t)
                let envelope = exp(-zeta * omega0 * t)
                let fragment = envelope * (sine * ((v0 + zeta * omega0 * x0) / omega1) + x0 * cosine)
                position = to + fragment
                speed = -zeta * omega0 * fragment + envelope * (cosine * (v0 + zeta * omega0 * x0) - omega1 * x0 * sine)
            } else {
                let envelope = exp(-omega0 * t)
                position = to + envelope * (x0 + (v0 + omega0 * x0) * t)
                speed = envelope * -omega0 * (x0 + (v0 + omega0 * x0) * t) + envelope * (v0 + omega0 * x0)
            }
            value = position
            velocity = speed
            let left = Self.energy(to - position, speed, stiffness, mass)
            if initialEnergy == 0 || left / initialEnergy <= 6e-9 {
                value = to
                velocity = 0
                kind = .none
            }
        }
    }
}

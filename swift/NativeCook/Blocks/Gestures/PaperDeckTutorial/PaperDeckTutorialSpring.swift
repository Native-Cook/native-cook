import Foundation

/// `withSpring(target, { duration: 400, dampingRatio: 0.8, velocity })` of the React Native file,
/// worked out in closed form so it can be read at any date from the timeline.
///
/// Reanimated 4 reads `duration` as a perceptual time and solves the stiffness so that the spring
/// settles after 1.5 times it (`calculateNewStiffnessToMatchDuration`, mass 1): 400 ms with a
/// damping ratio of 0.8 gives a stiffness of 653.4, a damping of `2 * 0.8 * sqrt(653.4)`.
/// The spring is under-damped, so the displacement from the target is
/// `e^(-a t) * (A cos(w t) + B sin(w t))` with `a = zeta * omega0` and `w = omega0 * sqrt(1 - zeta^2)`.
struct PaperDeckTutorialSpring {
    /// Stiffness that makes a 400 ms, 0.8 damping-ratio Reanimated spring settle after 600 ms.
    static let stiffness = 653.4
    /// `dampingRatio: 0.8`.
    static let dampingRatio = 0.8
    /// Reanimated's default `restDisplacementThreshold`.
    private static let restDisplacement = 0.01
    /// Reanimated's default `restSpeedThreshold`.
    private static let restSpeed = 2.0

    /// Where the value was when the spring started, and where it comes to rest.
    let from: Double
    let target: Double
    /// The release velocity in points per second (`velocity: event.velocityX`).
    let velocity: Double
    let start: Date
    /// Seconds until the value is within the rest thresholds (when Reanimated calls `finished`).
    let settle: Double

    private let decay: Double
    private let damped: Double
    private let cosine: Double
    private let sine: Double

    init(from: Double, target: Double, velocity: Double, start: Date) {
        let omega = Self.stiffness.squareRoot()
        let decay = Self.dampingRatio * omega
        let damped = omega * (1 - Self.dampingRatio * Self.dampingRatio).squareRoot()
        let distance = from - target
        let sine = (velocity + decay * distance) / damped
        self.from = from
        self.target = target
        self.velocity = velocity
        self.start = start
        self.decay = decay
        self.damped = damped
        self.cosine = distance
        self.sine = sine
        self.settle = Self.settleTime(decay: decay, damped: damped, cosine: distance, sine: sine)
    }

    /// The value `date - start` seconds into the spring.
    func value(at date: Date) -> Double {
        let elapsed = date.timeIntervalSince(start)
        if elapsed <= 0 { return from }
        if elapsed >= settle { return target }
        let offset = Self.displacement(elapsed, decay: decay, damped: damped, cosine: cosine, sine: sine)
        return target + offset
    }

    private static func displacement(_ t: Double, decay: Double, damped: Double, cosine: Double, sine: Double) -> Double {
        let envelope = exp(-decay * t)
        let wave = cosine * cos(damped * t) + sine * sin(damped * t)
        return envelope * wave
    }

    private static func speed(_ t: Double, decay: Double, damped: Double, cosine: Double, sine: Double) -> Double {
        let envelope = exp(-decay * t)
        let cosTerm = (damped * sine - decay * cosine) * cos(damped * t)
        let sinTerm = (-decay * sine - damped * cosine) * sin(damped * t)
        return envelope * (cosTerm + sinTerm)
    }

    /// The first moment, in steps of 1/240 s, at which both the displacement and the speed are under
    /// their rest thresholds. A spring that starts at rest on its target is done at once.
    private static func settleTime(decay: Double, damped: Double, cosine: Double, sine: Double) -> Double {
        let step = 1.0 / 240.0
        var t = 0.0
        while t < 4.0 {
            let offset = displacement(t, decay: decay, damped: damped, cosine: cosine, sine: sine)
            let rate = speed(t, decay: decay, damped: damped, cosine: cosine, sine: sine)
            if abs(offset) < restDisplacement && abs(rate) < restSpeed { return t }
            t += step
        }
        return 4.0
    }
}

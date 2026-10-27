import Foundation

/// The numbers and curves of `SilverScreenCarousel.tsx` and `motion.ts`: the drift of the row,
/// the fade of its tail, the shutter, and the easing functions the React Native file names.
enum SilverScreenCarouselMotion {
    /// `LOOP`: one pass of the autoplay, in seconds.
    static let period = 29.0
    /// `SOURCE_END`: the last moment of the reference's drift.
    static let sourceEnd = 27.983
    /// `SECONDS_PER_CARD`: a 238 px stride at 25.16752758685 px a second.
    static let secondsPerCard = 238.0 / 25.16752758685
    /// The drift ends and the row has faded out by this moment (`phase(time, SOURCE_END, 28.35)`);
    /// it is back at the first card and fades in until `fadeInEnd`.
    static let fadeOutEnd = 28.35
    /// `phase(time, 28.35, 28.7)`.
    static let fadeInEnd = 28.7
    /// The shutter flash starts at this opacity (`flash.set(.85)`) and fades over `flashDuration`.
    static let flashPeak = 0.85
    /// `withTiming(0, { duration: 420 })`.
    static let flashDuration = 0.42
    /// The drift resumes this long after the last touch (`setTimeout(..., 2000)`).
    static let quietDelay = 2.0

    /// `phase` of `motion.ts`: a smoothstep from `start` to `end`.
    static func phase(_ t: Double, _ start: Double, _ end: Double) -> Double {
        let x = min(1, max(0, (t - start) / (end - start)))
        return x * x * (3 - 2 * x)
    }

    /// `normalizeSceneTime`: a time folded into 0 up to `period`, whatever its sign.
    static func normalizeSceneTime(_ value: Double, period: Double) -> Double {
        guard value.isFinite, period.isFinite, period > 0 else { return 0 }
        let remainder = value.truncatingRemainder(dividingBy: period)
        return remainder < 0 ? remainder + period : remainder
    }

    /// `autoPosition`: where the row is, in cards, at loop time `t`.
    static func autoPosition(_ t: Double) -> Double {
        guard t < fadeOutEnd else { return 0 }
        return min(t, sourceEnd) / secondsPerCard
    }

    /// The index of the card in the gate: `Math.min(3, Math.max(0, Math.round(position)))`.
    static func index(for position: Double) -> Int {
        let rounded = position.rounded()
        return Int(min(3, max(0, rounded)))
    }

    // MARK: Easing

    /// `Easing.bezier(x1, y1, x2, y2)` / `cubicBezier(...)`: the y of the curve at progress `x`.
    static func bezier(_ x: Double, _ x1: Double, _ y1: Double, _ x2: Double, _ y2: Double) -> Double {
        if x <= 0 { return 0 }
        if x >= 1 { return 1 }
        let cx = 3 * x1
        let bx = 3 * (x2 - x1) - cx
        let ax = 1 - cx - bx
        let cy = 3 * y1
        let by = 3 * (y2 - y1) - cy
        let ay = 1 - cy - by
        func sampleX(_ t: Double) -> Double {
            return ((ax * t + bx) * t + cx) * t
        }
        func sampleY(_ t: Double) -> Double {
            return ((ay * t + by) * t + cy) * t
        }
        func slopeX(_ t: Double) -> Double {
            return (3 * ax * t + 2 * bx) * t + cx
        }
        var t = x
        for _ in 0..<8 {
            let error = sampleX(t) - x
            if abs(error) < 1e-6 { return sampleY(t) }
            let slope = slopeX(t)
            if abs(slope) < 1e-6 { break }
            t -= error / slope
        }
        var low = 0.0
        var high = 1.0
        t = x
        for _ in 0..<40 {
            let value = sampleX(t)
            if abs(value - x) < 1e-6 { break }
            if x > value {
                low = t
            } else {
                high = t
            }
            t = (low + high) / 2
        }
        return sampleY(t)
    }

    /// `SETTLE` / `Easing.bezier(.23, 1, .32, 1)`: the strong ease-out used by the press, the swap and the shutter.
    static func settle(_ x: Double) -> Double {
        return bezier(x, 0.23, 1, 0.32, 1)
    }

    /// `Easing.inOut(Easing.quad)`: Reanimated's default easing, used by the reduced-motion cross-fade.
    static func quadInOut(_ x: Double) -> Double {
        let p = min(1, max(0, x))
        return p < 0.5 ? 2 * p * p : -1 + (4 - 2 * p) * p
    }
}

/// `withSpring(target, { duration: 400, dampingRatio: 0.8 })`, optionally carrying a velocity.
///
/// A spring of unit mass with the stiffness Reanimated 4.5 solves for a perceptual 400 ms (653.4, so
/// sqrt = 25.56 rad/s: settled after 1.5 x 400 ms, `calculateNewStiffnessToMatchDuration`) and whose
/// damping ratio is 0.8, worked out in closed form so that every frame is a function of time. It
/// stops the way Reanimated stops one: once it is within 0.01 of the target and slower than 2 a second
/// (the units are cards).
struct SilverScreenCarouselSpring {
    let from: Double
    let to: Double
    /// Cards a second at the start.
    let velocity: Double
    let start: Date
    /// When this is the spring that brings the row home, finishing it restarts the drift.
    let resumes: Bool
    /// Seconds from `start` until the spring is at rest.
    let restTime: Double

    /// `SPRING.duration`, in seconds.
    private static let duration = 0.4
    /// `SPRING.dampingRatio`.
    private static let ratio = 0.8

    init(from: Double, to: Double, velocity: Double, start: Date, resumes: Bool) {
        self.from = from
        self.to = to
        self.velocity = velocity
        self.start = start
        self.resumes = resumes
        restTime = Self.findRestTime(offset: from - to, velocity: velocity)
    }

    /// The value at `date`.
    func value(at date: Date) -> Double {
        let elapsed = date.timeIntervalSince(start)
        if elapsed >= restTime { return to }
        if elapsed <= 0 { return from }
        return to + Self.sample(elapsed, offset: from - to, velocity: velocity).offset
    }

    /// Offset from the target, and speed, `time` seconds after the start (underdamped solution).
    private static func sample(_ time: Double, offset: Double, velocity: Double) -> (offset: Double, speed: Double) {
        let omega = 653.4.squareRoot()
        let decay = ratio * omega
        let damped = omega * (1 - ratio * ratio).squareRoot()
        let envelope = exp(-decay * time)
        let cosine = cos(damped * time)
        let sine = sin(damped * time)
        let sineOfOffset = (velocity + decay * offset) / damped
        let sineOfSpeed = (decay * velocity + omega * omega * offset) / damped
        let position = envelope * (offset * cosine + sineOfOffset * sine)
        let speed = envelope * (velocity * cosine - sineOfSpeed * sine)
        return (position, speed)
    }

    /// The first moment, in 1/240 s steps, at which the spring is at rest.
    private static func findRestTime(offset: Double, velocity: Double) -> Double {
        if abs(offset) < 0.01 && abs(velocity) < 2 { return 0 }
        var step = 1
        while step <= 720 {
            let time = Double(step) / 240
            let state = sample(time, offset: offset, velocity: velocity)
            if abs(state.offset) < 0.01 && abs(state.speed) < 2 { return time }
            step += 1
        }
        return 3
    }
}

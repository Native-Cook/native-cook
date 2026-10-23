import Foundation

enum NightSleepScoreStage: String, CaseIterable, Identifiable {
    case awake, rem, core, deep

    var id: String { rawValue }
    var title: String {
        switch self {
        case .awake: "Awake"
        case .rem: "REM"
        case .core: "Core"
        case .deep: "Deep"
        }
    }
}

struct NightSleepScoreSegment: Equatable {
    let stage: NightSleepScoreStage
    let minutes: Int
}

enum NightSleepScoreData {
    static let windowStartMinute = 23 * 60 + 18
    static let windowMinutes = 446
    // Authored visual fixtures; these values are not calculated from the stage trace.
    static let score = 84
    static let readiness = 7

    static let segments: [NightSleepScoreSegment] = [
        NightSleepScoreSegment(stage: .awake, minutes: 4),
        NightSleepScoreSegment(stage: .core, minutes: 12),
        NightSleepScoreSegment(stage: .deep, minutes: 7),
        NightSleepScoreSegment(stage: .core, minutes: 18),
        NightSleepScoreSegment(stage: .deep, minutes: 12),
        NightSleepScoreSegment(stage: .core, minutes: 48),
        NightSleepScoreSegment(stage: .rem, minutes: 7),
        NightSleepScoreSegment(stage: .core, minutes: 7),
        NightSleepScoreSegment(stage: .rem, minutes: 5),
        NightSleepScoreSegment(stage: .core, minutes: 24),
        NightSleepScoreSegment(stage: .awake, minutes: 2),
        NightSleepScoreSegment(stage: .core, minutes: 78),
        NightSleepScoreSegment(stage: .deep, minutes: 9),
        NightSleepScoreSegment(stage: .core, minutes: 7),
        NightSleepScoreSegment(stage: .deep, minutes: 8),
        NightSleepScoreSegment(stage: .core, minutes: 10),
        NightSleepScoreSegment(stage: .rem, minutes: 19),
        NightSleepScoreSegment(stage: .core, minutes: 64),
        NightSleepScoreSegment(stage: .awake, minutes: 2),
        NightSleepScoreSegment(stage: .core, minutes: 12),
        NightSleepScoreSegment(stage: .rem, minutes: 32),
        NightSleepScoreSegment(stage: .core, minutes: 14),
        NightSleepScoreSegment(stage: .awake, minutes: 3),
        NightSleepScoreSegment(stage: .core, minutes: 18),
        NightSleepScoreSegment(stage: .awake, minutes: 2),
        NightSleepScoreSegment(stage: .core, minutes: 21),
        NightSleepScoreSegment(stage: .awake, minutes: 1)
    ]

    static func total(for stage: NightSleepScoreStage) -> Int {
        segments.reduce(0) { $0 + ($1.stage == stage ? $1.minutes : 0) }
    }

    static var asleepMinutes: Int {
        total(for: .rem) + total(for: .core) + total(for: .deep)
    }

    static func segmentIndex(at minute: Double) -> Int {
        let bounded = min(Double(windowMinutes) - 0.000_001, max(0, minute))
        var edge = 0.0
        for (index, segment) in segments.enumerated() {
            edge += Double(segment.minutes)
            if bounded < edge { return index }
        }
        return segments.count - 1
    }

    static func segmentStart(_ index: Int) -> Int {
        segments.prefix(max(0, min(index, segments.count))).reduce(0) { $0 + $1.minutes }
    }

    static func clockTime(at minute: Double) -> String {
        let offset = Int(floor(min(Double(windowMinutes), max(0, minute))))
        let clockMinute = (windowStartMinute + offset) % (24 * 60)
        return String(format: "%02d:%02d", clockMinute / 60, clockMinute % 60)
    }

    static func range(for index: Int) -> String {
        let start = Double(segmentStart(index))
        let end = start + Double(segments[index].minutes)
        return "\(clockTime(at: start))–\(clockTime(at: end)) overnight window"
    }
}

/// One sampled frame. `night` darkens the card into a sky (0 = the pin's light card), `reveal` is how much of the
/// aurora hypnogram and the night ring is drawn, `meteor` is a shooting star's progress (-1 when none).
struct NightSleepScoreFrame: Equatable {
    var cursorProgress: Double
    var selectionPresence: Double
    var segmentIndex: Int?
    var isRest: Bool
    var night: Double
    var reveal: Double
    var meteor: Double

    static let rest = NightSleepScoreFrame(cursorProgress: 0, selectionPresence: 0, segmentIndex: nil,
                                           isRest: true, night: 0, reveal: 0, meteor: -1)
}

enum NightSleepScoreMotion {
    static let loop = 9.0

    /// Autoplay timeline in seconds of the 9 s loop. Rest 0-1.0 and 8.05-9.0 is the pin's light card.
    enum Timeline {
        static let duskStart = 1.0, duskEnd = 2.05
        static let presenceStart = 1.55, presenceEnd = 1.9
        static let sweepStart = 1.75, sweepEnd = 6.35, sweepRamp = 0.16
        static let meteorStart = 3.3, meteorEnd = 4.0
        static let dayStart = 6.25, dayEnd = 8.05
        static let presenceOutStart = 6.45, presenceOutEnd = 6.9
    }

    static let frontWidth = 0.6
    static let readoutSwap = 0.45

    /// Critically damped chase rates (night, reveal, phase) and the lock thresholds.
    enum Chase {
        static let night = 9.0, reveal = 6.5, phase = 11.0
        static let lockDistance = 0.002, lockVelocity = 0.05
    }

    /// Tab bar lens: a spring slide that stretches 12 % on the way; press 0.97 in 120 ms, back in 100 ms.
    enum TabMotion {
        static let lensDuration = 0.4, lensDamping = 0.8
        static let stretch = 1.12, stretchMs = 0.12
        static let settleDuration = 0.3, settleDamping = 0.7
        static let pressScale = 0.97, pressIn = 0.12, pressOut = 0.1
    }

    static func clamp(_ value: Double) -> Double { min(1, max(0, value)) }

    static func easeOutCubic(_ value: Double) -> Double {
        let x = clamp(value)
        return 1 - pow(1 - x, 3)
    }

    static func easeInOut(_ value: Double) -> Double {
        let x = clamp(value)
        return x < 0.5 ? 4 * x * x * x : 1 - pow(-2 * x + 2, 3) / 2
    }

    /// C1-smooth 0 to 1.
    static func smoothStep(_ value: Double) -> Double {
        let x = clamp(value)
        return x * x * (3 - 2 * x)
    }

    /// C2-smooth 0 to 1.
    static func smootherStep(_ value: Double) -> Double {
        let x = clamp(value)
        return x * x * x * (x * (x * 6 - 15) + 10)
    }

    private static func rampIntegral(_ x: Double) -> Double { x * x * x - x * x * x * x / 2 }

    /// Playhead position for a sweep fraction: velocity eases in and out over `ramp`, constant between.
    static func sweep(_ value: Double) -> Double {
        let ramp = Timeline.sweepRamp
        let u = clamp(value)
        let peak = 1 / (1 - ramp)
        if u < ramp { return peak * ramp * rampIntegral(u / ramp) }
        if u > 1 - ramp { return 1 - peak * ramp * rampIntegral((1 - u) / ramp) }
        return peak * (u - ramp / 2)
    }

    /// Night falls from the top of the card and day returns from its bottom edge: local darkness at card height `v`.
    static func nightAt(_ night: Double, _ v: Double) -> Double {
        smootherStep((night * (1 + frontWidth) - v) / frontWidth)
    }

    /// Critically damped spring step (exact for any dt).
    static func chase(_ value: Double, _ velocity: Double, to target: Double, dt: Double, omega: Double) -> (Double, Double) {
        let delta = value - target
        let decay = exp(-omega * dt)
        let k = velocity + omega * delta
        return (target + (delta + k * dt) * decay, (velocity - omega * k * dt) * decay)
    }

    static func selectedFrame(minute: Double, secondsSinceTouch: Double?, quiet: Bool, holdDuration: Double?) -> NightSleepScoreFrame {
        let index = NightSleepScoreData.segmentIndex(at: minute)
        let presence: Double
        if quiet {
            presence = 1
        } else if let holdDuration, let secondsSinceTouch, secondsSinceTouch > holdDuration {
            presence = 1 - easeInOut((secondsSinceTouch - holdDuration) / 0.25)
        } else {
            presence = 1
        }
        return NightSleepScoreFrame(
            cursorProgress: clamp(minute / Double(NightSleepScoreData.windowMinutes)),
            selectionPresence: presence, segmentIndex: index, isRest: presence == 0,
            night: presence, reveal: 1, meteor: -1)
    }

    static func frame(at elapsed: Double) -> NightSleepScoreFrame {
        var t = elapsed.truncatingRemainder(dividingBy: loop)
        if t < 0 { t += loop }
        let T = Timeline.self
        if t < T.duskStart || t >= T.dayEnd { return .rest }
        let night = t < T.dayStart
            ? smoothStep((t - T.duskStart) / (T.duskEnd - T.duskStart))
            : 1 - smoothStep((t - T.dayStart) / (T.dayEnd - T.dayStart))
        let presence = t < T.presenceOutStart
            ? smootherStep((t - T.presenceStart) / (T.presenceEnd - T.presenceStart))
            : 1 - smootherStep((t - T.presenceOutStart) / (T.presenceOutEnd - T.presenceOutStart))
        let progress = sweep((t - T.sweepStart) / (T.sweepEnd - T.sweepStart))
        let meteor = t >= T.meteorStart && t < T.meteorEnd ? (t - T.meteorStart) / (T.meteorEnd - T.meteorStart) : -1
        let index = presence > 0 ? NightSleepScoreData.segmentIndex(at: progress * Double(NightSleepScoreData.windowMinutes)) : nil
        return NightSleepScoreFrame(cursorProgress: progress, selectionPresence: presence, segmentIndex: index,
                                    isRest: false, night: night, reveal: progress, meteor: meteor)
    }
}

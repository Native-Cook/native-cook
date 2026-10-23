import SwiftUI

/// The sleep stages a night is split into, in the order of their colours.
enum SleepColumnScrubberStage: CaseIterable {
    case awake, rem, core, deep

    var color: Color {
        switch self {
        case .awake: Color(hex: 0xff8a3d)
        case .rem: Color(hex: 0xb000ff)
        case .core: Color(hex: 0x1e96ff)
        case .deep: Color(hex: 0x1de9b6)
        }
    }

    var label: String {
        switch self {
        case .awake: "Awake"
        case .rem: "REM"
        case .core: "Core"
        case .deep: "Deep"
        }
    }
}

struct SleepColumnScrubberRun {
    var stage: SleepColumnScrubberStage
    var minutes: Double
}

/// One night: when sleep started and ended, in minutes from midnight (negative before it).
struct SleepColumnScrubberNight {
    var weekday: String
    var day: Int
    var asleep: Double
    var awake: Double
    /// Stage runs from `asleep` on, each with its length in minutes; generated when left out.
    var stages: [SleepColumnScrubberRun]? = nil
}

enum SleepColumnScrubberData {
    // Two weeks of nights, the last one tonight's (00:42–08:23).
    static let nights = [
        SleepColumnScrubberNight(weekday: "Th", day: 2, asleep: 40, awake: 413),
        SleepColumnScrubberNight(weekday: "Fr", day: 3, asleep: 75, awake: 505),
        SleepColumnScrubberNight(weekday: "Sa", day: 4, asleep: 38, awake: 460),
        SleepColumnScrubberNight(weekday: "Su", day: 5, asleep: 110, awake: 435),
        SleepColumnScrubberNight(weekday: "Mo", day: 6, asleep: 3, awake: 502),
        SleepColumnScrubberNight(weekday: "Tu", day: 7, asleep: 50, awake: 380),
        SleepColumnScrubberNight(weekday: "We", day: 8, asleep: 15, awake: 470),
        SleepColumnScrubberNight(weekday: "Th", day: 9, asleep: 26, awake: 520),
        SleepColumnScrubberNight(weekday: "Fr", day: 10, asleep: 48, awake: 492),
        SleepColumnScrubberNight(weekday: "Sa", day: 11, asleep: 62, awake: 405),
        SleepColumnScrubberNight(weekday: "Su", day: 12, asleep: 34, awake: 345),
        SleepColumnScrubberNight(weekday: "Mo", day: 13, asleep: -18, awake: 455),
        SleepColumnScrubberNight(weekday: "Tu", day: 14, asleep: 8, awake: 300),
        SleepColumnScrubberNight(weekday: "We", day: 15, asleep: 42, awake: 503),
    ]

    // Stage runs last from runMin to runMin + runSpread minutes; REM and awake runs are shorter.
    private static let runMin = 4.0
    private static let runSpread = 26.0
    private static let shortRun = 0.45
    // How likely each stage is: awake always this rare, deep fading through the night, REM growing.
    private static let awakeOdds = 0.03
    private static let deepEarly = 0.5
    private static let remEarly = 0.06
    private static let remLate = 0.2

    /// A fixed value in [0, 1) for a night and a run, so every platform draws the same nights.
    static func hash(_ a: Double, _ b: Double) -> Double {
        let x = sin(a * 127.1 + b * 311.7) * 43758.5453
        return x - floor(x)
    }

    /// The stage runs of a night: deep sleep comes early, REM late, core all night, and a short
    /// awake now and then. Each run's length and stage come from `hash(night, run)`.
    static func stagesOf(_ night: SleepColumnScrubberNight, _ index: Int) -> [SleepColumnScrubberRun] {
        if let stages = night.stages { return stages }
        var runs: [SleepColumnScrubberRun] = []
        let length = night.awake - night.asleep
        var at = 0.0
        var run = 0
        while at < length {
            let through = at / length
            let pick = hash(Double(index), Double(run * 2 + 1))
            let deep = deepEarly * (1 - through)
            let rem = remEarly + (remLate - remEarly) * through
            let stage: SleepColumnScrubberStage = pick < awakeOdds ? .awake : pick < awakeOdds + deep ? .deep : pick < awakeOdds + deep + rem ? .rem : .core
            let span = runMin + hash(Double(index), Double(run * 2)) * runSpread
            let minutes = min(length - at, stage == .rem || stage == .awake ? span * shortRun : span)
            runs.append(SleepColumnScrubberRun(stage: stage, minutes: minutes))
            at += minutes
            run += 1
        }
        return runs
    }

    /// 42 → "00:42", -18 → "23:42".
    static func clock(_ minutes: Double) -> String {
        let m = ((Int((minutes + 0.5).rounded(.down)) % 1440) + 1440) % 1440
        return String(format: "%02d:%02d", m / 60, m % 60)
    }

    static func mean(_ values: [Double]) -> Double {
        values.isEmpty ? 0 : values.reduce(0, +) / Double(values.count)
    }

    /// The standard deviation, in the values' unit.
    static func spread(_ values: [Double]) -> Double {
        let middle = mean(values)
        return sqrt(mean(values.map { ($0 - middle) * ($0 - middle) }))
    }
}

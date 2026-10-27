import SwiftUI

/// Constants, easing and the pure functions of loop time for Bright Lesson Recap.
///
/// Every number here comes from `BrightLessonRecap.tsx` and `motion.ts` in the React Native block;
/// the comments say which. A frame of the whole screen is a function of the loop time `t` alone.
enum BrightLessonRecapMotion {
    /// RN `LOOP`: the reference's 5.851 s story, then a fade and a reset.
    static let loop = 6.5
    /// RN `SOURCE_END`: the story ends here and the scene starts to fade.
    static let sourceEnd = 5.851
    /// The time at which the scene is fully faded out again (RN `phase(t, SOURCE_END, 6.25)`).
    static let fadeEnd = 6.25
    /// RN `useSceneClock(…, settled = 5, …)`: the complete recap that reduced motion shows.
    static let settled = 5.0
    /// The claim key and its row are live from here (RN `time >= 2.35`).
    static let actionsStart = 2.35
    /// The mascot answers a tap while the scene is between these times (RN `> 0.333 && < 6.25`).
    static let mascotStart = 0.333

    /// RN `Easing.bezierFn(0.23, 1, 0.32, 1)`, the strong ease-out the block uses for every settle.
    static let enterCurve = UnitCurve.bezier(
        startControlPoint: UnitPoint(x: 0.23, y: 1),
        endControlPoint: UnitPoint(x: 0.32, y: 1)
    )

    /// RN `ENTER(x)`.
    static func enter(_ x: Double) -> Double {
        enterCurve.value(at: min(1, max(0, x)))
    }

    /// Reanimated's default `withTiming` easing, `Easing.inOut(Easing.quad)` (used by the reduced-motion fades).
    static func inOutQuad(_ x: Double) -> Double {
        let u = min(1, max(0, x))
        return u < 0.5 ? 2 * u * u : 1 - pow(-2 * u + 2, 2) / 2
    }

    /// RN `fraction(t, a, b)`: linear 0…1 between `a` and `b`, and exactly 1 from `b` on.
    static func fraction(_ t: Double, _ a: Double, _ b: Double) -> Double {
        if !t.isFinite { return 0 }
        if t >= b - 1e-9 { return 1 }
        return min(1, max(0, (t - a) / (b - a)))
    }

    /// RN `phase(t, start, end)` in `motion.ts`: a smoothstep between the two times.
    static func phase(_ t: Double, _ start: Double, _ end: Double) -> Double {
        let x = min(1, max(0, (t - start) / (end - start)))
        return x * x * (3 - 2 * x)
    }

    /// RN `normalizeSceneTime`: a preview time wrapped into one period.
    static func normalize(_ value: Double) -> Double {
        guard value.isFinite else { return 0 }
        let remainder = value.truncatingRemainder(dividingBy: loop)
        return remainder < 0 ? remainder + loop : remainder
    }

    /// Reanimated `interpolate(t, input, output, 'clamp')`.
    static func interpolate(_ t: Double, _ input: [Double], _ output: [Double]) -> Double {
        guard let first = input.first, let last = input.last else { return 0 }
        if t <= first { return output[0] }
        if t >= last { return output[output.count - 1] }
        var index = 1
        while index < input.count - 1 && t > input[index] { index += 1 }
        let span = input[index] - input[index - 1]
        let u = span == 0 ? 1 : (t - input[index - 1]) / span
        return output[index - 1] + (output[index] - output[index - 1]) * u
    }

    /// RN `dockAt(t)`: how far the mascot has compacted into its small pose. The widths were measured
    /// at source frames 72, 76, 78, 80, 81, 84 and 87.
    static func dock(_ t: Double) -> Double {
        interpolate(
            t,
            [1.2, 1.267, 1.3, 1.333, 1.35, 1.4, 1.45],
            [0, 0.09, 0.22, 0.63, 0.80, 0.96, 1]
        )
    }

    /// The mascot's horizontal drift as a fraction of the window width (RN `mascot` style, `x`).
    static func mascotX(_ t: Double) -> Double {
        interpolate(
            t,
            [0.333, 0.4, 0.5, 0.6, 0.75, 1.2, 1.45],
            [-0.8, -0.28, -0.09, -0.02, 0.015, 0.085, 0.03]
        )
    }

    /// The mascot's centre as a fraction of the window height (RN `mascot` style, `center`).
    static func mascotCenter(_ t: Double) -> Double {
        interpolate(
            t,
            [0.333, 0.5, 0.75, 1.2, 1.3, 1.35, 1.5, 1.7, 1.9],
            [0.44, 0.439, 0.428, 0.423, 0.433, 0.427, 0.392, 0.378, 0.376]
        )
    }

    /// RN `counterProgress(index, t)`: how far each of the three counters has counted.
    static func counterProgress(index: Int, t: Double) -> Double {
        if index == 0 {
            return 0.89 * enter(fraction(t, 2.35, 2.65)) + 0.11 * fraction(t, 2.8, 3.1)
        }
        return fraction(t, index == 1 ? 2.95 : 3.65, index == 1 ? 3.283 : 3.95)
    }

    /// The text a counter shows. RN: "the minute and second columns count independently, then land at 3:40".
    static func counterText(index: Int, t: Double) -> String {
        let stat = BrightLessonRecapStat.all[index]
        let p = counterProgress(index: index, t: t)
        switch stat.kind {
        case .time:
            let minutes = Int((3 * p).rounded(.down))
            let seconds = Int((40 * p).rounded())
            return "\(minutes):" + (seconds < 10 ? "0\(seconds)" : "\(seconds)")
        case .accuracy:
            return "\(Int((Double(stat.amount) * p).rounded()))%"
        case .points:
            return "\(Int((Double(stat.amount) * p).rounded()))"
        }
    }
}

/// One of the three results (RN `STATS`).
struct BrightLessonRecapStat {
    enum Kind { case points, accuracy, time }

    let label: String
    let amount: Int
    /// When the corner sparks of the pill appear, the pill's outline grows, its cap rises and ends,
    /// and the number lands (RN `sparkle`, `body`, `cap`, `capEnd`, `land`).
    let sparkle: Double
    let body: Double
    let cap: Double
    let capEnd: Double
    let land: Double
    let color: UInt32
    let light: UInt32
    let border: UInt32
    let kind: Kind

    static let all: [BrightLessonRecapStat] = [
        BrightLessonRecapStat(label: "TOTAL XP", amount: 45, sparkle: 1.90, body: 2.367, cap: 2.667, capEnd: 2.734, land: 3.1, color: 0xD98A00, light: 0xFFF4D6, border: 0xF4AE16, kind: .points),
        BrightLessonRecapStat(label: "AMAZING", amount: 100, sparkle: 2.85, body: 2.967, cap: 3.25, capEnd: 3.283, land: 3.283, color: 0x3E9B22, light: 0xE7F7DE, border: 0x5BC236, kind: .accuracy),
        BrightLessonRecapStat(label: "QUICK", amount: 220, sparkle: 3.56, body: 3.65, cap: 3.967, capEnd: 4.05, land: 3.95, color: 0x1985C9, light: 0xE1F2FC, border: 0x2FA8EA, kind: .time),
    ]

    /// The text RN measures to size the counter's box (`finalCounterText`).
    var finalCounterText: String {
        switch kind {
        case .time: return "3:40"
        case .accuracy: return "100%"
        case .points: return "42"
        }
    }

    /// RN `label` on the stat card.
    var accessibilityText: String {
        let spoken: String
        switch kind {
        case .time: spoken = "3 minutes 40 seconds"
        case .accuracy: spoken = "\(amount) percent"
        case .points: spoken = "\(amount)"
        }
        return "\(label.lowercased()): \(spoken)"
    }
}

/// The scene's time. RN keeps it in a shared value moved by a frame callback: an anchor is taken on
/// the first frame after a start (`anchor = timestamp - time`), the time is `(timestamp - anchor) % period`,
/// and a pause keeps the last value. This does the same from the timeline's dates.
final class BrightLessonRecapClock {
    private var origin = 0.0
    private var anchored = false
    private(set) var held: Double
    private(set) var running = false

    init(initial: Double) {
        held = initial
    }

    /// The loop time at `date`. While stopped it is the held time.
    func time(at date: Date) -> Double {
        guard running else { return held }
        let now = date.timeIntervalSinceReferenceDate
        if !anchored {
            origin = now - held
            anchored = true
        }
        let elapsed = max(0, now - origin)
        let t = elapsed.truncatingRemainder(dividingBy: BrightLessonRecapMotion.loop)
        held = t
        return t
    }

    /// Starts or stops the clock. Starting takes a new anchor at the next frame; the held time carries on.
    func setRunning(_ on: Bool) {
        guard on != running else { return }
        running = on
        anchored = false
    }

    /// RN `time.set(value); anchor.set(-1)`.
    func reset(to time: Double) {
        held = time
        anchored = false
    }
}

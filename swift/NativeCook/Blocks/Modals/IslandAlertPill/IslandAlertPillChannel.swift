import SwiftUI

/// A number moving towards a target on its own curve, read against the `TimelineView` clock so one
/// view can run several of them at once (a spring for the island, a timing for the track).
struct IslandAlertPillChannel {
    enum Curve {
        case timing(Double, (Double) -> Double)
        case spring(Spring)
        /// Eases to `IslandAlertPillMotion.glowLow`, then breathes between it and 1 for good.
        case breath
    }

    private(set) var from: Double
    private var to: Double
    private var start = Date.distantPast
    private var delay = 0.0
    private var curve = Curve.timing(0, { $0 })

    init(_ value: Double) {
        from = value
        to = value
    }

    func value(at date: Date) -> Double {
        let t = date.timeIntervalSince(start) - delay
        if t <= 0 { return from }
        switch curve {
        case let .timing(duration, ease):
            guard duration > 0 else { return to }
            return from + (to - from) * ease(min(t / duration, 1))
        case let .spring(spring):
            return from + (to - from) * spring.value(target: 1, time: t)
        case .breath:
            let low = IslandAlertPillMotion.glowLow
            let half = IslandAlertPillMotion.breath
            if t < half { return from + (low - from) * IslandAlertPillMotion.inOutSine(t / half) }
            let phase = (t - half).truncatingRemainder(dividingBy: half * 2)
            return phase < half
                ? low + (1 - low) * IslandAlertPillMotion.inOutSine(phase / half)
                : 1 - (1 - low) * IslandAlertPillMotion.inOutSine((phase - half) / half)
        }
    }

    /// Starts from wherever the channel is now.
    mutating func go(to target: Double, _ curve: Curve, delay: Double = 0, at date: Date = .now) {
        from = value(at: date)
        to = target
        start = date
        self.delay = delay
        self.curve = curve
    }

    mutating func set(_ value: Double) {
        from = value
        to = value
        start = .distantPast
        delay = 0
        curve = .timing(0, { $0 })
    }
}

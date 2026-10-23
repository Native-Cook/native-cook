import SwiftUI

/// A number moving on its own curve, read against the `TimelineView` clock so one view can run
/// many of them at once. A new target starts from wherever the number is, as Reanimated does; a
/// run of segments plays one after the other (only the last may be a spring).
struct StackingToastFeedChannel {
    enum Curve {
        case timing(Double, (Double) -> Double)
        case spring(Spring)
    }

    struct Segment {
        let to: Double
        let curve: Curve
        var delay = 0.0
    }

    private var from: Double
    private var start = Date.distantPast
    private var segments: [Segment] = []

    init(_ value: Double) {
        from = value
    }

    func value(at date: Date) -> Double {
        var t = date.timeIntervalSince(start)
        var origin = from
        for segment in segments {
            t -= segment.delay
            if t <= 0 { return origin }
            switch segment.curve {
            case let .timing(duration, ease):
                if t < duration { return origin + (segment.to - origin) * ease(t / duration) }
                t -= duration
            case let .spring(spring):
                return origin + (segment.to - origin) * spring.value(target: 1, time: t)
            }
            origin = segment.to
        }
        return origin
    }

    mutating func go(_ segments: [Segment], at date: Date = .now) {
        from = value(at: date)
        start = date
        self.segments = segments
    }

    mutating func go(to target: Double, _ curve: Curve, delay: Double = 0, at date: Date = .now) {
        go([Segment(to: target, curve: curve, delay: delay)], at: date)
    }

    mutating func set(_ value: Double) {
        from = value
        segments = []
    }
}

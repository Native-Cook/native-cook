import Foundation

/// Estimates release velocity (pt/s) from the most recent drag samples.
struct VelocityTracker {
    private var samples: [(time: TimeInterval, position: Double)] = []

    mutating func reset() {
        samples.removeAll(keepingCapacity: true)
    }

    mutating func add(position: Double, at time: TimeInterval) {
        samples.append((time, position))
        if samples.count > 8 { samples.removeFirst(samples.count - 8) }
    }

    func velocity(at time: TimeInterval) -> Double {
        guard let last = samples.last else { return 0 }
        // A release more than 100ms after the last movement has no fling.
        if time - last.time > 0.1 { return 0 }
        guard let reference = samples.first(where: { last.time - $0.time <= 0.06 && $0.time < last.time })
            ?? samples.dropLast().last
        else { return 0 }
        let dt = last.time - reference.time
        guard dt > 0 else { return 0 }
        return (last.position - reference.position) / dt
    }
}

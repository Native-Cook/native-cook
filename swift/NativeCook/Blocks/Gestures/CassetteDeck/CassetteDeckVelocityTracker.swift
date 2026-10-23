import CoreGraphics
import Foundation

/// Finger velocity from the last 80 ms of a touch, like the pan velocity the React Native block reads.
/// A finger that rested before it lifted leaves at zero.
struct CassetteDeckVelocityTracker {
    private var samples: [(time: Double, point: CGPoint)] = []

    mutating func reset() {
        samples.removeAll()
    }

    mutating func add(_ point: CGPoint, at time: Double) {
        samples.append((time, point))
        samples.removeAll { time - $0.time > 0.2 }
    }

    func velocity(at time: Double) -> CGVector {
        guard let last = samples.last, time - last.time <= 0.1 else { return .zero }
        guard let first = samples.first(where: { last.time - $0.time <= 0.08 }) else { return .zero }
        let elapsed = last.time - first.time
        guard elapsed >= 0.008 else { return .zero }
        return CGVector(dx: (last.point.x - first.point.x) / elapsed, dy: (last.point.y - first.point.y) / elapsed)
    }
}

import Foundation

struct TrophySavingsMilestoneCelebration: Sendable {
    var clock = 0.0
    var progress = 0.0
    var wakeStarts = Array(repeating: -1.0, count: 18)
    var contactStart = -1.0
    var generation = 0
    var reduced = false
    var active = false
    var cadence = 1.0
    static let settle = 0.96
    static let thresholds = [0.12,0.23,0.08,0.18,0.76,0.91,0.28,0.4,0.2,0.62,0.74,0.54,0.11,0.83,0.04,0.84,0.29,0.16]
    static let galleryStarts = thresholds.map { threshold in
        var low = 0.6, high = 1.7
        for _ in 0..<15 { let mid = (low+high)*0.5; if TrophySavingsMilestoneMotion.loopProgress(mid) < threshold { low = mid } else { high = mid } }
        return (low+high)*0.5
    }
    static func gallery(_ time: Double, generation: Int = 0) -> Self {
        let t = (time.truncatingRemainder(dividingBy: 5)+5).truncatingRemainder(dividingBy: 5)
        return Self(clock: t, progress: TrophySavingsMilestoneMotion.loopProgress(t), wakeStarts: galleryStarts.map { t >= $0 ? $0 : -1 }, contactStart: t >= 1.53 ? 1.53 : -1, generation: generation, reduced: false, active: t >= 0.6 && t < 4.4, cadence: 2.4)
    }
    var settled: Bool {
        if !active || reduced { return true }; if progress > 0.000001 { return false }
        let last = max(contactStart, wakeStarts.max() ?? -1)
        return last < 0 || clock >= last+Self.settle*cadence
    }
    mutating func advance(clock: Double, progress p: Double, generation: Int) {
        if p > progress+0.000001 {
            for i in 0..<18 where wakeStarts[i] < 0 && p >= Self.thresholds[i] { wakeStarts[i] = clock }
            if contactStart < 0 && p >= 0.88 { contactStart = clock }
        }
        self.clock = clock; self.progress = p; self.generation = generation; active = true; reduced = false
    }
}

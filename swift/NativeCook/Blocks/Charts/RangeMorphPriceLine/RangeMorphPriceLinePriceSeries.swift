import Foundation

/// A range of the selector: its label, its percent change (whose sign colours the line) and one
/// value per point, oldest first, in any scale.
struct RangeMorphPriceLineRange {
    var label: String
    var change: Double
    var series: [Double]
}

enum RangeMorphPriceLineSeries {
    // Every range has the same number of points, so a morph moves each point straight up or down.
    static let points = 120

    static let ranges: [RangeMorphPriceLineRange] = walks.map {
        RangeMorphPriceLineRange(label: $0.label, change: $0.change, series: walk($0))
    }

    /// A series fitted to 0 (its lowest point) to 1 (its highest), with exactly `points` values.
    static func normalized(_ series: [Double]) -> [Double] {
        let low = series.min() ?? 0
        let high = series.max() ?? 0
        return (0..<points).map { i in
            let at = series.count == points ? i : Int((Double(i) / Double(points - 1) * Double(series.count - 1)).rounded())
            return high > low ? (series[at] - low) / (high - low) : 0.5
        }
    }

    private struct Walk {
        var label: String
        var seed: UInt32
        var change: Double
        /// How far the walk drifts against its noise: 0 is flat, 1 climbs about its own wobble.
        var drift: Double
        /// How jagged it is: the share of steps that jump instead of stepping.
        var spikes: Double
        /// Stretches where the price did not move (markets closed), as first and last points.
        var holds: [(Int, Int)] = []
        /// A hump in the first quarter, which makes the morph into this range seem to roll.
        var bump = 0.0
    }

    private static let walks: [Walk] = [
        Walk(label: "1D", seed: 11, change: -0.18, drift: -0.5, spikes: 0.05),
        Walk(label: "1W", seed: 7, change: 0.32, drift: 1.1, spikes: 0.12, holds: [(86, 104), (106, 116)]),
        Walk(label: "1M", seed: 23, change: -1.69, drift: -0.9, spikes: 0.18),
        Walk(label: "3M", seed: 41, change: -0.65, drift: -0.6, spikes: 0.14, bump: 0.9),
        Walk(label: "6M", seed: 5, change: 0.62, drift: 0.3, spikes: 0.16),
        Walk(label: "1Y", seed: 63, change: 0.97, drift: 0.5, spikes: 0.2),
        Walk(label: "2Y", seed: 87, change: 8.53, drift: 1.4, spikes: 0.2),
    ]

    /// mulberry32, bit for bit as in the React Native version: the same seed gives the same walk.
    private struct Seeded {
        var a: UInt32
        mutating func next() -> Double {
            a = a &+ 0x6d2b79f5
            var t = a
            t = (t ^ (t >> 15)) &* (t | 1)
            t ^= t &+ ((t ^ (t >> 7)) &* (t | 61))
            return Double(t ^ (t >> 14)) / 4294967296
        }
    }

    private static func walk(_ w: Walk) -> [Double] {
        var random = Seeded(a: w.seed)
        var raw: [Double] = []
        var value = 0.0
        for _ in 0..<points {
            let jump = random.next() < w.spikes ? 4.0 : 1.0
            value += (random.next() - 0.5) * jump
            raw.append(value)
        }
        // Take the walk's own trend out, then put the range's in, so the sign of the change is honest.
        let first = raw[0]
        let last = raw[points - 1]
        let spread = raw.reduce(0) { max($0, abs($1 - first)) }
        var out = raw.enumerated().map { i, v -> Double in
            let t = Double(i) / Double(points - 1)
            let hump = w.bump * exp(-pow((t - 0.16) / 0.07, 2))
            return v - first - (last - first) * t + w.drift * spread * t + hump * spread
        }
        for (from, to) in w.holds where from < to {
            for i in (from + 1)...to { out[i] = out[from] }
        }
        return out
    }
}

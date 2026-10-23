import SwiftUI

struct DualLineRedrawRangesLineRange {
    var key: String
    var name: String
    var caption: String
    var ticks: [String]
    var high: [Double]
    var low: [Double]
    static let samples = [
        Self(key: "day", name: "Day", caption: "Last 24 hours", ticks: ["00:00", "06:00", "12:00", "18:00", "Now"], high: DualLineRedrawRangesWalk(seed: 31, count: 90, start: 70, drift: 0.05, step: 6.5, lo: 45, hi: 95), low: DualLineRedrawRangesWalk(seed: 32, count: 90, start: 28, drift: 0, step: 5, lo: 10, hi: 45)),
        Self(key: "week", name: "Week", caption: "Last 14 days", ticks: ["Mar 16", "Mar 19", "Mar 22", "Mar 25", "Mar 29"], high: DualLineRedrawRangesWalk(seed: 11, count: 90, start: 60, drift: 0.3, step: 4, lo: 45, hi: 95), low: DualLineRedrawRangesWalk(seed: 12, count: 90, start: 40, drift: -0.28, step: 3, lo: 10, hi: 45)),
        Self(key: "year", name: "Year", caption: "1999 – 2024", ticks: ["1999", "2005", "2011", "2017", "2024"], high: DualLineRedrawRangesWalk(seed: 21, count: 60, start: 52, drift: 0.62, step: 2.2, lo: 45, hi: 95), low: DualLineRedrawRangesWalk(seed: 22, count: 60, start: 42, drift: -0.3, step: 2, lo: 10, hi: 45)),
    ]
}

// Wrapping UInt32 arithmetic keeps mulberry32 identical to JavaScript's imul.
func DualLineRedrawRangesWalk(seed: UInt32, count: Int, start: Double, drift: Double, step: Double, lo: Double, hi: Double) -> [Double] {
    var a = seed, v = start
    return (0..<count).map { _ in
        let value = floor(v * 10 + 0.5) / 10
        a = a &+ 0x6d2b79f5
        var t = (a ^ (a >> 15)) &* (a | 1)
        t ^= t &+ ((t ^ (t >> 7)) &* (t | 61))
        let random = Double(t ^ (t >> 14)) / 4294967296
        v += drift + (random * 2 - 1) * step
        if v > hi { v = 2 * hi - v }
        if v < lo { v = 2 * lo - v }
        return value
    }
}

func DualLineRedrawRangesPointAt(_ values: [Double], _ fraction: Double, _ width: Double, _ height: Double) -> CGPoint {
    let n = values.count, f = min(1, max(0, fraction)) * Double(n - 1), i = min(n - 2, Int(floor(f)))
    let v = values[i] + (values[i + 1] - values[i]) * (f - Double(i))
    return CGPoint(x: f / Double(n - 1) * width, y: height - v / 100 * height)
}

func DualLineRedrawRangesLinePart(_ values: [Double], _ from: Double, _ to: Double, _ width: Double, _ height: Double) -> Path {
    var path = Path()
    if to - from <= 0.0005 { return path }
    path.move(to: DualLineRedrawRangesPointAt(values, from, width, height))
    let n = values.count, first = Int(floor(from * Double(n - 1))) + 1, last = Int(ceil(to * Double(n - 1))) - 1
    if first <= last { for i in first...last { path.addLine(to: CGPoint(x: Double(i) / Double(n - 1) * width, y: height - values[i] / 100 * height)) } }
    path.addLine(to: DualLineRedrawRangesPointAt(values, to, width, height))
    return path
}

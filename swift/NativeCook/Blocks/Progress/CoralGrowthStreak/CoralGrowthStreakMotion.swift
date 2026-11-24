// Coral Growth Streak — the loop, the simulation's constants and the scripted week, in closed form.
//
// The coral itself is the one thing that is not a function of time: it is a Gray-Scott field stepped
// at `stepHz` from the seeds below. It is seeded on exact step numbers, so every loop grows the same
// reef, and it is dissolved in the view and then cleared at t = 0, so frame 10 s equals frame 0.

import Foundation

enum CoralGrowthStreakMotion {
    static let loop = 10.0
    static let days = 7

    // the reaction-diffusion simulation
    static let grid = 256
    static let stepHz = 1200.0
    static let feed = 0.0545
    static let kill = 0.062
    static let wallKill = 0.012
    static let wallWidth = 5.0
    static let seedRadius = 3.0
    static let maxSteps = 40
    static let seedSlots = 8

    // the loop's phases, seconds
    static let first = 0.6
    static let every = 0.9
    static let dot = 0.42
    static let bloom = (from: 7.0, to: 8.2)
    static let fade = (from: 8.5, to: 9.35)
    /// a touch hands the reef back this long after it ends
    static let idle = 2.0
    static let returnEase = 0.85
    static let returnTotal = 1.0
    /// the still frame shown under reduced motion: after the bloom, before the fade
    static let still = 8.3

    static func clamp01(_ v: Double) -> Double { v < 0 ? 0 : v > 1 ? 1 : v }
    static func seg(_ t: Double, _ a: Double, _ b: Double) -> Double { clamp01((t - a) / (b - a)) }
    static func smooth(_ v: Double) -> Double { v * v * (3 - 2 * v) }
    /// overshoots to 1.1 at 0.7, settles on 1
    static func easeOutBack(_ v: Double) -> Double {
        let u = v - 1
        return 1 + 2.70158 * u * u * u + 1.70158 * u * u
    }
    /// the default curve of a timed fade
    static func inOutQuad(_ v: Double) -> Double {
        v < 0.5 ? 2 * v * v : 1 - pow(-2 * v + 2, 2) / 2
    }

    static func dayTime(_ i: Int) -> Double { first + Double(i) * every }

    /// the streak shown at t, a real number that rolls from one count to the next
    static func countAt(_ t: Double) -> Double {
        (0..<days).reduce(0) { $0 + smooth(seg(t, dayTime($1), dayTime($1) + dot)) }
    }

    /// the open radius after d completed days, as a fraction of the reef's radius
    static func ringFrac(_ d: Int) -> Double {
        (Double(d) / Double(days)).squareRoot() * 1.06
    }

    static func bloomAt(_ t: Double) -> Double { seg(t, bloom.from, bloom.to) }
    static func levelAt(_ t: Double) -> Double { 1 - smooth(seg(t, fade.from, fade.to)) }

    private static func hash1(_ n: Double) -> Double {
        let x = sin(n * 127.1 + 311.7) * 43758.5453
        return x - floor(x)
    }

    /// the seeds of day i as x, y pairs in 0…1 of the grid: two at the centre, then a ring of 4…8 in the day's new band
    private static func seedsFor(_ day: Int) -> [Double] {
        if day == 0 { return [0.5, 0.5, 0.59, 0.45] }
        var out: [Double] = []
        let n = min(seedSlots, 3 + day)
        let mid = ((ringFrac(day) + ringFrac(day + 1)) / 2 / 1.06) * 0.5 * 0.97
        for j in 0..<n {
            let a = (2 * Double.pi * (Double(j) + hash1(Double(day) * 7.3 + Double(j)))) / Double(n) + Double(day) * 0.9
            let r = mid * (0.94 + 0.1 * hash1(Double(day) * 3.1 + Double(j) * 5.7))
            out.append(0.5 + r * cos(a))
            out.append(0.5 + r * sin(a))
        }
        return out
    }

    static let seeds: [[Double]] = (0..<days).map(seedsFor)
    /// the step each day's seeds go in on
    static let dayStep: [Int] = (0..<days).map { Int((dayTime($0) * stepHz).rounded()) }
}

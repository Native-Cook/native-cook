import Foundation

/// Literal ers-motion.ts equations. Film time is in seconds; page progress is unwrapped.
enum ExtrusionRecapStoryMotion {
    static let loop = 8.0
    static let steerAngle = 12 * Double.pi / 180
    static func clamp(_ v: Double, _ a: Double = 0, _ b: Double = 1) -> Double { min(b, max(a, v)) }
    static func wrap(_ v: Double, _ n: Double = 3) -> Double {
        (v.truncatingRemainder(dividingBy: n) + n).truncatingRemainder(dividingBy: n)
    }
    static func smooth(_ a: Double, _ b: Double, _ v: Double) -> Double {
        let x = clamp((v - a) / (b - a))
        return x * x * (3 - 2 * x)
    }
    static func timeline(_ t: Double) -> Double {
        let x = wrap(t, loop)
        if x < 0.7 { return 0 }
        if x < 1.25 { return 0.5 * smooth(0.7, 1.25, x) }
        if x < 1.45 { return 0.5 }
        if x < 2.1 { return 0.5 + 0.5 * smooth(1.45, 2.1, x) }
        if x < 3.5 { return 1 }
        if x < 4.7 { return 1 + smooth(3.5, 4.7, x) }
        if x < 6 { return 2 }
        if x < 7.2 { return 2 + smooth(6, 7.2, x) }
        return 3
    }
    static func pageAlpha(_ progress: Double, _ i: Int) -> Double {
        let p = wrap(progress), base = Int(floor(p)), q = p - Double(base)
        if i == base { return 1 - smooth(0, 0.3, q) }
        if i == (base + 1) % 3 { return smooth(0.72, 1, q) }
        return 0
    }
    static func landingAt(_ t: Double) -> Double {
        let x = wrap(t, loop)
        return x >= 7.2 ? x - 7.2 : x >= 4.7 ? x - 4.7 : x >= 2.1 ? x - 2.1 : 10
    }
    static func landingPulse(_ age: Double) -> Double {
        age >= 0 && age < 0.65 ? exp(-6 * age) * sin(sqrt(164) * age) / 0.532647813016 : 0
    }
    static func peekShift(_ progress: Double, _ i: Int, _ age: Double) -> Double {
        let base = Int(floor(wrap(progress)))
        return (i == base ? -8 : 8) * (1 - pageAlpha(progress, i)) - 8 * landingPulse(age)
    }
    static func copyShift(_ alpha: Double, _ age: Double, _ hasStat: Bool) -> Double {
        -12 * (1 - alpha) + (hasStat ? 4 * landingPulse(age) : 0)
    }
    static func highlightPass(_ age: Double) -> Double {
        age >= 0 && age < 0.3 ? sin(.pi * age / 0.3) : 0
    }
    static func digitArrival(_ progress: Double, _ page: Int, _ digit: Int) -> Double {
        let p = wrap(progress), i = Int(floor(p)), q = p - Double(i)
        if i == page { return 1 }
        if (i + 1) % 3 == page { return smooth(0.7 + Double(digit) * 0.025, 0.95 + Double(digit) * 0.025, q) }
        return 0
    }

    struct ExtrusionRecapStoryScene {
        var progress = 0.0
        var elapsed = 0.0
        var age = 10.0
        var steerX = 0.0
        var steerY = 0.0
        var stretch = 0.0
        var speed = 0.0
        var reduced = false
        var activePage: Int { Int(floor(wrap(progress) + 0.5)) % 3 }
    }
    static func scene(_ t: Double) -> ExtrusionRecapStoryScene {
        .init(progress: timeline(t), elapsed: t, age: landingAt(t))
    }
}

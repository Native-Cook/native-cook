import Foundation

// All autoplay poses are pure functions of the RN loop time, including the pinned debug frame.
enum PerfectMonthGemMotion {
    static let loop = 8.0
    static func clamp(_ v: Double) -> Double { min(1, max(0, v)) }
    static func mix(_ a: Double, _ b: Double, _ k: Double) -> Double { a + (b - a) * k }
    static func smooth(_ k: Double) -> Double { let x = clamp(k); return x * x * (3 - 2 * x) }
    static func ease(_ k: Double) -> Double {
        let x = clamp(k)
        return x < 0.5 ? 4 * x * x * x : 1 - pow(-2 * x + 2, 3) / 2
    }
    static func back(_ k: Double) -> Double {
        let x = clamp(k)
        return 1 + 2.6 * pow(x - 1, 3) + 1.6 * pow(x - 1, 2)
    }
    static func progress(_ t: Double) -> Double {
        if t < 0.6 { return 0 }
        if t < 2.8 { return ease((t - 0.6) / 2.2) }
        if t < 6.6 { return 1 }
        if t < 7.6 { return 1 - ease(t - 6.6) }
        return 0
    }
    struct Frame {
        var p: Double
        var turn: Double
        var reduced = false
        var artP: Double { reduced ? (p > 0.5 ? 1 : 0) : p }
    }
}

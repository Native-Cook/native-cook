import Foundation

// JavaScript numbers and the same quintic interpolation, without native implicit animations.
enum SproutStatsMotion {
    static let loop = 9.0
    static let minutes = [12, 20, 8, 25, 18, 30, 15]
    static let days = ["Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday"]
    static let colors = [0xFF9AA2, 0xFFB86B, 0xFFD86B, 0x9BE08B, 0x7FD3E8, 0xA7A8FF, 0xE7A6FF]
    struct Frame { var m = 0.0; var scrub = -1.0; var t = 0.0 }
    static func clamp(_ x: Double) -> Double { min(1, max(0, x)) }
    static func mix(_ a: Double, _ b: Double, _ k: Double) -> Double { a + (b - a) * k }
    static func smoother(_ k: Double) -> Double {
        let x = clamp(k)
        return x*x*x*(x*(x*6-15)+10)
    }
    static func at(_ t: Double) -> Frame {
        let p = t.truncatingRemainder(dividingBy: loop)
        var m = 0.0
        if p >= 1.4 && p < 2.6 { m = smoother((p-1.4)/1.2) }
        else if p >= 2.6 && p < 6 { m = 1 }
        else if p >= 6 && p < 7.2 { m = 1-smoother((p-6)/1.2) }
        return Frame(m: m, scrub: p >= 3 && p < 5.4 ? (p-3)/2.4*6 : -1, t: t)
    }
}

import Foundation

// JavaScript numbers and all timing stay in Double.
enum IslandSleepDialMotion {
    static let loop = 9.0
    struct Frame {
        var h = 6.0
        var cloud = 0.0
        var t = 0.0
        var drag = 0.0
    }
    static func clamp(_ x: Double) -> Double { max(0, min(1, x)) }
    static func smoother(_ k: Double) -> Double {
        let x = clamp(k)
        return x * x * x * (x * (x * 6 - 15) + 10)
    }
    static func at(_ time: Double) -> Frame {
        let t = time.truncatingRemainder(dividingBy: loop)
        var h = 6.0
        if t >= 1 && t < 3 { h = 6 + 3 * smoother((t - 1) / 2) }
        else if t >= 3 && t < 3.6 { h = 9 }
        else if t >= 3.6 && t < 7.6 { h = 9 - smoother(t - 3.6) }
        else if t >= 7.6 && t < 8.8 { h = 8 - 2 * smoother((t - 7.6) / 1.2) }
        let cloud = t >= 5.6 && t < 7.6
            ? smoother((t - 5.6) / 0.8) * (1 - smoother((t - 7) / 0.6)) : 0
        return Frame(h: h, cloud: cloud, t: time)
    }
    static func quarter(_ h: Double) -> Int { Int(floor(h * 4 + 0.5)) }
    static func duration(_ h: Double) -> String {
        let m = quarter(h) * 15
        return String(format: "%dh %02dm", m / 60, m % 60)
    }
    static func wake(_ h: Double) -> String {
        let m = (23 * 60 + quarter(h) * 15) % (24 * 60)
        let h = m / 60
        return String(format: "%d:%02d %@", h == 0 ? 12 : h > 12 ? h - 12 : h, m % 60, h < 12 ? "AM" : "PM")
    }
}

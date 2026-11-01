import Foundation

// Double precision and the exact polynomial/phase boundaries from the RN worklets.
enum QuestMapUnrollMotion {
    static let loop = 9.0
    static let current = 2.0
    static let names = ["Intro to spells", "Stone bridge", "Pumpkin patch", "Crystal cave", "Giant mushroom", "Wizard tower"]
    static let stars = [3, 3, 2, 0, 0, 0]
    struct Frame {
        var u = 0.0
        var wisp = current
        var t = 0.0
        var card = 0.0
        var level = 3
    }
    static func clamp(_ x: Double) -> Double { min(1, max(0, x)) }
    static func smoother(_ k: Double) -> Double {
        let x = clamp(k)
        return x * x * x * (x * (x * 6 - 15) + 10)
    }
    static func backOut(_ k: Double) -> Double {
        let x = clamp(k) - 1
        return 1 + 3.2 * x * x * x + 2.2 * x * x
    }
    static func at(_ time: Double) -> Frame {
        let t = time.truncatingRemainder(dividingBy: loop)
        var f = Frame(t: time)
        if t >= 0.6 && t < 2.4 { f.u = smoother((t - 0.6) / 1.8) }
        else if t >= 2.4 && t < 6.6 { f.u = 1 }
        else if t >= 6.6 && t < 8.2 { f.u = 1 - smoother((t - 6.6) / 1.6) }
        if t >= 3 && t < 6.6 { f.wisp = current + smoother((t - 3) / 1.2) }
        if t >= 4.3 && t < 6 {
            f.card = smoother((t - 4.3) / 0.4) * (1 - smoother((t - 5.6) / 0.4))
        }
        return f
    }
}

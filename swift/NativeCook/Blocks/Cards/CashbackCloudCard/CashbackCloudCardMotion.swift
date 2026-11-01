import Foundation

enum CashbackCloudCardMotion {
    static let loop = 9.0
    struct Frame {
        var squeeze = 0.0
        var release = 0.0
        var rainU = -1.0
        var refill = 0.0
        var fraction: Double {
            guard rainU >= 0 else { return 0 }
            let n = (0..<6).filter { CashbackCloudCardMotion.coinK(rainU, $0) >= 1 }.count
            return Double(n) / 6 * (1 - refill)
        }
    }
    static func clamp(_ v: Double) -> Double { min(1, max(0, v)) }
    static func smoother(_ k: Double) -> Double {
        let x = clamp(k)
        return x * x * x * (x * (x * 6 - 15) + 10)
    }
    static func coinK(_ u: Double, _ i: Int) -> Double { clamp((u - Double(i) * 0.36) / 0.6) }
    static func at(_ time: Double) -> Frame {
        let t = time.truncatingRemainder(dividingBy: loop)
        return Frame(
            squeeze: t >= 1 && t < 3.8 ? smoother((t - 1) / 0.3) : 0,
            release: t >= 3.8 ? exp(-6 * (t - 3.8)) * sin(16 * (t - 3.8)) * 0.4 : 0,
            rainU: t >= 1.3 && t < 7.2 ? t - 1.3 : -1,
            refill: t >= 7.2 ? smoother((t - 7.2) / 1) : 0)
    }
}

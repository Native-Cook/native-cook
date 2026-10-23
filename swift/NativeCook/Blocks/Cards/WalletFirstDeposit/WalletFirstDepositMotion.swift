import Foundation

enum WalletFirstDepositMotion {
    enum Spring {
        case flight, ui, pop
        var values: (omega: Double, zeta: Double) {
            switch self {
            case .flight: (11.4, 0.72)
            case .ui: (15.7, 0.8)
            case .pop: (12.6, 0.55)
            }
        }
    }

    static func clamp(_ value: Double) -> Double { min(max(value, 0), 1) }
    static func raw(_ value: Double, _ from: Double, _ to: Double) -> Double { clamp((value - from) / (to - from)) }
    static func out(_ p: Double) -> Double { cubic(p, 0.23, 1, 0.32, 1) }
    static func inOut(_ p: Double) -> Double { cubic(p, 0.77, 0, 0.175, 1) }

    static func cubic(_ p: Double, _ x1: Double, _ y1: Double, _ x2: Double, _ y2: Double) -> Double {
        let x = clamp(p)
        if x == 0 || x == 1 { return x }
        var lo = 0.0
        var hi = 1.0
        for _ in 0..<18 {
            let t = (lo + hi) / 2
            let u = 1 - t
            let at = 3 * u * u * t * x1 + 3 * u * t * t * x2 + t * t * t
            if at < x { lo = t } else { hi = t }
        }
        let t = (lo + hi) / 2
        let u = 1 - t
        return 3 * u * u * t * y1 + 3 * u * t * t * y2 + t * t * t
    }

    static func spring(_ seconds: Double, _ kind: Spring) -> Double {
        guard seconds > 0 else { return 0 }
        let (omega, zeta) = kind.values
        let damped = omega * sqrt(1 - zeta * zeta)
        let envelope = exp(-zeta * omega * seconds)
        if envelope < 0.0001 { return 1 }
        return 1 - envelope * (cos(damped * seconds) + zeta * omega / damped * sin(damped * seconds))
    }
}

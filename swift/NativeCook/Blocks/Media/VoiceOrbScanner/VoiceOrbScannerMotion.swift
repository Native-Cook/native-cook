import Foundation

enum VoiceOrbScannerMotion {
    static let loop = 9.0
    static func clamp(_ x: Double) -> Double { min(1, max(0, x)) }
    static func mix(_ a: Double, _ b: Double, _ k: Double) -> Double { a + (b-a)*k }
    static func smooth(_ k: Double) -> Double { let x = clamp(k); return x*x*(3-2*x) }
    static func ease(_ k: Double) -> Double { let x = clamp(k); return x < 0.5 ? 4*x*x*x : 1-pow(-2*x+2,3)/2 }
    struct Frame {
        var t = 0.0, now = 0.0, L = 0.0, S = 0.0
        var listening = false
        var held = false
        var useLoopPhase = true
        var phase: Int { S > 0.5 ? 2 : listening ? 1 : 0 }
    }
    static func at(_ t: Double, now: Double? = nil) -> Frame {
        let L = t >= 1 && t < 3.4 ? (t-1)/2.4 : t >= 3.4 && t < 7.9 ? 1.0 : 0.0
        var S = 0.0
        if t >= 3.4 && t < 6.9 { S = ease((t-3.4)/1.1) }
        else if t >= 6.9 && t < 7.9 { S = 1-ease(t-6.9) }
        return Frame(t: t, now: now ?? t, L: L, S: S, listening: t >= 1 && t < 3.4)
    }
}

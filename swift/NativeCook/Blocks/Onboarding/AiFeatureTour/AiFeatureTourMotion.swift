import Foundation

// The analytic timeline is deliberately identical to aft-motion.ts; no native spring approximation.
enum AiFeatureTourMotion {
    static let LOOP = 9.0
    static let RESUME = 2.0
    static let ICONS = 5
    static let ACT = 1.3
    static let FLY_GAP = 0.05
    static let FLY_DUR = 0.55
    static let BACK_GAP = 0.07
    enum T {
        static let act0 = 0.3
        static let press = 7.0
        static let fly = 7.1
        static let sparkle = 7.68
        static let back = 8.05
    }
    struct Frame {
        var amb: Double
        var act: [Double]
        var fly: Double
        var press: Double
    }
    struct Flight {
        var out: Double
        var back: Double
        var gone: Bool
    }
    static func clamp01(_ x: Double) -> Double { min(1, max(0, x)) }
    static func seg(_ t: Double, _ a: Double, _ b: Double) -> Double { clamp01((t - a) / (b - a)) }
    static func mix(_ a: Double, _ b: Double, _ p: Double) -> Double { a + (b - a) * p }
    static func smooth(_ x: Double) -> Double {
        let p = clamp01(x)
        return p * p * (3 - 2 * p)
    }
    static func easeOut(_ x: Double) -> Double { 1 - pow(1 - clamp01(x), 3) }
    static func easeIn(_ x: Double) -> Double {
        let p = clamp01(x)
        return p * p * p
    }
    static func easeInOut(_ x: Double) -> Double {
        let p = clamp01(x)
        return p < 0.5 ? 4 * p * p * p : 1 - pow(-2 * p + 2, 3) / 2
    }
    static func spring(_ tau: Double, _ response: Double, _ damping: Double) -> Double {
        if tau <= 0 { return 0 }
        let w = 2 * Double.pi / response
        let zw = damping * w
        let wd = w * sqrt(1 - damping * damping)
        return 1 - exp(-zw * tau) * (cos(wd * tau) + (zw / wd) * sin(wd * tau))
    }
    static func bump(_ t: Double, _ at: Double, _ dur: Double) -> Double {
        let p = (t - at) / dur
        return p <= 0 || p >= 1 ? 0 : sin(Double.pi * p)
    }
    static func frameAt(_ t: Double) -> Frame {
        let act = (0..<ICONS).map { i -> Double in
            let p = (t - (T.act0 + Double(i) * ACT)) / ACT
            return p >= 0 && p < 1 ? p : -1
        }
        return Frame(amb: t, act: act, fly: t >= T.press ? t - T.press : -1,
                     press: bump(t, T.press - 0.02, 0.22))
    }
    static func flight(_ i: Int, _ tau: Double) -> Flight {
        let at = T.fly - T.press + Double(i) * FLY_GAP
        let out = easeIn(seg(tau, at, at + FLY_DUR))
        let backAt = T.back - T.press + Double(i) * BACK_GAP
        let back = tau >= backAt ? spring(tau - backAt, 0.5, 0.6) : 0
        return Flight(out: out, back: back, gone: tau >= at + FLY_DUR && tau < backAt)
    }
    struct Driver {
        var t = 0.0
        var mode = 0
        var last = -10.0
        var amb = 0.0
        var actAt = Array(repeating: -10.0, count: ICONS)
        var flyAt = -10.0
        var pressAt = -10.0
        var pressing = 0

        mutating func takeOver(_ now: Double) {
            last = now
            if mode == 2 { return }
            mode = 2
            let snapshot = frameAt(t)
            for i in 0..<ICONS { actAt[i] = snapshot.act[i] >= 0 ? now - snapshot.act[i] * ACT : -10 }
            flyAt = snapshot.fly >= 0 ? now - snapshot.fly : -10
        }
        mutating func step(_ dt: Double, _ now: Double, _ auto: Bool) {
            amb = (amb + dt).truncatingRemainder(dividingBy: LOOP)
            if mode == 0 {
                if auto { t = (t + dt).truncatingRemainder(dividingBy: LOOP) }
                return
            }
            if !auto || now - last < RESUME || pressing == 1 { return }
            for i in 0..<ICONS { if now - actAt[i] < ACT { return } }
            if now - flyAt < LOOP - T.press { return }
            mode = 0
            t = 0
        }
        func frame(_ now: Double) -> Frame {
            if mode == 0 { return frameAt(t) }
            let act = actAt.map { at -> Double in
                let p = (now - at) / ACT
                return p >= 0 && p < 1 ? p : -1
            }
            let fly = now - flyAt < LOOP - T.press ? now - flyAt : -1
            let press = pressing == 1 ? smooth((now - pressAt) / 0.1) : bump(now - flyAt, -0.02, 0.22)
            return Frame(amb: amb, act: act, fly: fly, press: press)
        }
    }
}

import Foundation

// stm-motion.ts, including the same semi-implicit Euler substeps.
enum SeasonTierMedalsMotion {
    static let loop = 8.0
    static let spin = 0.95
    static func clamp(_ x: Double) -> Double { min(1, max(0, x)) }
    static func mix(_ a: Double, _ b: Double, _ k: Double) -> Double { a + (b - a) * k }
    static func smooth(_ x: Double) -> Double { let k = clamp(x); return k * k * (3 - 2 * k) }
    static func target(_ t: Double) -> Int { t >= 1.4 && t < 4.4 ? 2 : 1 }
    static func round(_ p: Double) -> Int { Int(floor(p + 0.5)) }
    static func rubber(_ p: Double) -> Double {
        if p < 0 { return -0.35 * tanh(-p / 0.35) }
        if p > 2 { return 2 + 0.35 * tanh((p - 2) / 0.35) }
        return p
    }
    static func yaw(_ since: Double) -> Double {
        if since < 0 || since >= spin { return 0 }
        let x = clamp(since / spin) - 1
        return 2 * .pi * (2.2 * x * x * x + 1.2 * x * x)
    }
    static func flash(_ since: Double) -> Double {
        since > spin * 0.75 && since < spin + 1.2 ? exp(-(since - spin * 0.75) * 3.2) : 0
    }
    struct Rail {
        var p = 1.0, v = 0.0, finger = 1.0, stiff = 150.0
        var target = 1, mode = 0, landed = -1
        var spins = [-10.0, -10.0, -10.0]
        mutating func setSpin(_ index: Int, _ now: Double) { spins[index] = now; landed = -1 }
        mutating func step(_ dt: Double) {
            if mode == 1 {
                if dt > 0 { v = v * 0.6 + ((finger - p) / dt) * 0.4 }
                p = finger
                return
            }
            let c = 2 * sqrt(stiff) * 0.9
            let n = max(1, Int(ceil(dt / (1.0 / 240))))
            let h = dt / Double(n)
            for _ in 0..<n {
                v += (stiff * (Double(target) - p) - c * v) * h
                p += v * h
            }
        }
        mutating func script(_ t: Double, _ dt: Double, _ now: Double, _ reduced: Bool = false) {
            let tg = SeasonTierMedalsMotion.target(t)
            if tg != target {
                target = tg; stiff = 70
                if !reduced { setSpin(tg, now) }
            }
            step(dt)
        }
    }
    struct Frame {
        var rail = Rail()
        var time = 0.0, now = 0.0
        var current: Int { min(2, max(0, SeasonTierMedalsMotion.round(rail.p))) }
        var flash: Double { rail.spins.map { SeasonTierMedalsMotion.flash(now - $0) }.max() ?? 0 }
        var sweep: Double {
            let sw = clamp((time - 6) / 1.1)
            return sw > 0 && sw < 1 ? mix(-2.2, 2.2, smooth(sw)) : 9
        }
        func layer(_ i: Int) -> Double { clamp(1 - abs(rail.p - Double(i)) * 2.1) }
        var order: [Int] { [0, 1, 2].sorted { abs(Double($0) - rail.p) > abs(Double($1) - rail.p) } }
        func medal(_ i: Int, _ width: Double, _ my: Double) -> Medal {
            let o = Double(i) - rail.p
            let ao = abs(o), side = min(abs(o), 1), far = max(0, abs(o) - 1)
            let rc = min(84, width * 0.21), sp = min(172, width * 0.43)
            let since = now - rail.spins[i]
            return Medal(index: i,
                x: width / 2 + (o == 0 ? 0 : o > 0 ? 1 : -1) * (sp * side + far * 110),
                y: my + side * 4,
                radius: rc * mix(1, 0.64, side) * mix(1, 0.8, min(far, 1)),
                yaw: -max(-1.5, min(1.5, o)) * 0.62 + SeasonTierMedalsMotion.yaw(since)
                    + 0.05 * sin(2 * .pi * 2 * time / loop + Double(i) * 1.7),
                sweep: ao < 0.5 ? sweep : 9,
                glow: -0.42 * side + 0.5 * SeasonTierMedalsMotion.flash(since), visible: ao <= 2.3)
        }
    }
    struct Medal {
        var index: Int
        var x, y, radius, yaw, sweep, glow: Double
        var visible: Bool
    }
    // Exact implementation of the RN at(t) hook (script sees st before the step).
    static func at(_ t: Double, reduced: Bool = false) -> Frame {
        var r = Rail(), st = 0.0
        while st < t {
            let d = min(1.0 / 120, t - st)
            r.script(st, d, st + d, reduced)
            st += d
        }
        return Frame(rail: r, time: t, now: t)
    }
}

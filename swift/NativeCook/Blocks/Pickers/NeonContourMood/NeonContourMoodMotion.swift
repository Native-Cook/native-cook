import Foundation

/// Closed-form autoplay and the polished label strikes from ncm-motion.ts.
enum NeonContourMoodMotion {
    static let loop = 8.0
    struct Look {
        var mood = 1.0
        var echoes = Array(repeating: 1.0, count: 7)
        var wake = 0.0
        var press = 0.0
        var breath = 0.0
        var radiate = 0.0
        var time = 0.0
    }
    struct Event {
        let time: Double
        let from: Int
        let to: Int
    }
    static let crossings: [Event] = [
        .init(time: 1.134, from: 1, to: 2), .init(time: 1.619, from: 2, to: 3),
        .init(time: 3.2275, from: 3, to: 4), .init(time: 3.394, from: 4, to: 5),
        .init(time: 3.5895, from: 5, to: 6), .init(time: 5.291, from: 6, to: 5),
        .init(time: 5.5885, from: 5, to: 4), .init(time: 5.8285, from: 4, to: 3),
        .init(time: 6.074, from: 3, to: 2), .init(time: 6.4125, from: 2, to: 1)
    ]
    static func clamp(_ x: Double, _ lo: Double = 0, _ hi: Double = 1) -> Double { min(hi, max(lo, x)) }
    static func mix(_ a: Double, _ b: Double, _ t: Double) -> Double { a + (b - a) * t }
    static func smooth(_ t: Double) -> Double {
        let x = clamp(t)
        return x * x * x * (x * (x * 6 - 15) + 10)
    }
    static func wrap(_ t: Double) -> Double { (t.truncatingRemainder(dividingBy: loop) + loop).truncatingRemainder(dividingBy: loop) }
    static func spring(_ t: Double, stiffness: Double, damping: Double) -> Double {
        guard t > 0 else { return 0 }
        let w = sqrt(stiffness), z = damping / (2 * w)
        if z >= 1 { return 1 - exp(-w * t) * (1 + w * t) }
        let wd = w * sqrt(max(1e-9, 1 - z * z))
        return 1 - exp(-z * w * t) * (cos(wd * t) + z * w / wd * sin(wd * t))
    }
    static func moodAt(_ time: Double) -> Double {
        let t = wrap(time)
        if t < 0.6 || t >= 6.9 { return 1 }
        if t < 1.85 { return mix(1, 2.7, smooth((t - 0.6) / 1.25)) }
        if t < 2.85 { return 2.7 }
        if t < 3.85 { return mix(2.7, 6, smooth(t - 2.85)) }
        if t < 4.7 { return 6 }
        return mix(6, 1, smooth((t - 4.7) / 2.2))
    }
    static func holdProfile(_ from: Double, _ target: Double, _ age: Double) -> Double {
        if age <= 0 { return from }
        if target > from { return mix(from, target, smooth(age / 0.18)) }
        return clamp(mix(from, target, spring(age, stiffness: 157.9, damping: 22.1)))
    }
    static func holdAt(_ t: Double) -> Double {
        if t < 1.72 { return 0 }
        if t < 2.92 { return smooth((t - 1.72) / 0.18) }
        return holdProfile(1, 0, t - 2.92)
    }
    static func timeline(_ time: Double) -> Look {
        let t = wrap(time), core = moodAt(t), lo = max(0, core - 1.6), hi = min(6, core + 1.6)
        var out = Look()
        for k in 0..<7 {
            let echo = clamp(moodAt(max(0, t - Double(k) * 0.096)), lo, hi)
            let nested = clamp(core - Double(k) * 0.22, 0, 6)
            out.echoes[k] = mix(echo, nested, holdAt(t - Double(k) * 0.04))
        }
        let speed = abs(core - moodAt(max(0, t - 1 / 120.0))) * 120
        let active = smooth((t - 0.6) / 0.12) * (1 - smooth((t - 6.7) / 0.2))
        out.mood = out.echoes[0]; out.wake = clamp(speed / 10); out.press = active
        out.breath = sin(4 * .pi * t / loop) * 1.5 * active
        out.radiate = smooth((t - 3.85) / 0.2) * (1 - smooth((t - 4.5) / 0.2))
        out.time = t
        return out
    }
    static func settle(_ index: Int, _ mood: Double) -> Int {
        var i = index
        while mood > Double(i) + 0.62 && i < 6 { i += 1 }
        while mood < Double(i) - 0.62 && i > 0 { i -= 1 }
        return i
    }
    static func rubber(_ v: Double) -> Double {
        if v < 0 { return -0.35 * -v / (1 - v) }
        if v > 6 { return 6 + 0.35 * (v - 6) / (1 + v - 6) }
        return v
    }
    static func projected(_ value: Double, _ velocity: Double) -> Double {
        let nearest = clamp(value, 0, 6).rounded()
        return clamp(value + velocity * 0.12, max(0, nearest - 2), min(6, nearest + 2)).rounded()
    }
    static func strike(_ u: Double) -> Double {
        if u <= 0 { return 0 }
        if u < 0.03 { return smooth(u / 0.03) }
        let d = (u - 0.05) / 0.024
        if d <= 0 || d >= 1 { return 1 }
        let edge = d < 0.25 ? d / 0.25 : d > 0.75 ? (1 - d) / 0.25 : 1
        return 1 - 0.55 * edge
    }
    static func incoming(_ d: Double, _ j: Int) -> Double {
        let tube = 0.22 * smooth((d - 0.08) / 0.2)
        return tube + (1 - tube) * strike(d - 0.2 - Double(j) * 0.026)
    }
    /// Same 70-number ABI as LAB in the RN source.
    static func settled(_ idx: Int, _ widths: [Double], _ lengths: [Int]) -> [Double] {
        var lab = Array(repeating: 0.0, count: 70)
        lab[0] = Double(idx); lab[1] = Double(idx); lab[2] = 99; lab[3] = widths[idx]
        lab[4] = 1; lab[8] = 1; lab[9] = 1
        for j in 0..<10 {
            let o = 10 + 3 * j
            lab[o] = j < lengths[idx] ? 1 : 0
            lab[o + 1] = j < lengths[idx] ? 0.35 : 0
            lab[o + 2] = 6; lab[42 + 3 * j] = 6
        }
        return lab
    }
    static func label(_ events: [Event], now: Double, idle: Int, widths: [Double], lengths: [Int]) -> [Double] {
        guard let first = events.first else { return settled(idle, widths, lengths) }
        var width = widths[first.from], tagSnap = 1.0
        var snap = (0..<10).map { $0 < lengths[first.from] ? 1.0 : 0 }
        for e in events.indices {
            let event = events[e], last = e == events.count - 1
            let d = (last ? now : events[e + 1].time) - event.time
            let widthK = spring(d - 0.08, stiffness: 260, damping: 20)
            let tagIn = smooth((d - 0.15) / 0.16)
            if !last {
                width = mix(width, widths[event.to], widthK); tagSnap = tagIn
                for j in 0..<10 { snap[j] = j < lengths[event.to] ? incoming(d, j) : 0 }
                continue
            }
            if d > 1.2 { return settled(event.to, widths, lengths) }
            var lab = Array(repeating: 0.0, count: 70)
            lab[0] = Double(event.from); lab[1] = Double(event.to); lab[2] = d
            lab[3] = mix(width, widths[event.to], widthK)
            let tagOut = smooth((d - 0.02) / 0.09)
            lab[4] = tagIn; lab[5] = 2 * (1 - tagIn)
            lab[6] = tagSnap * (1 - tagOut); lab[7] = -2 * tagOut
            let arrive = smooth((d - 0.08) / 0.2), leave = smooth(d / 0.17)
            lab[8] = mix(1.05, 1, arrive); lab[9] = mix(1, 0.94, leave)
            for j in 0..<10 {
                let u = d - 0.2 - Double(j) * 0.026, grow = smooth(u / 0.16), a = 10 + 3 * j, b = 40 + 3 * j
                lab[a] = j < lengths[event.to] ? incoming(d, j) : 0
                lab[a + 1] = 0.35 * (j < lengths[event.to] ? strike(u) : 0) * grow
                lab[a + 2] = mix(2.4, 6, grow)
                let was = j < lengths[event.from] ? snap[j] : 0
                lab[b] = was * (1 - leave) * (1 - leave)
                lab[b + 1] = 0.35 * was * (1 - leave) * (1 + leave)
                lab[b + 2] = mix(6, 14, leave)
            }
            return lab
        }
        return settled(idle, widths, lengths)
    }
    static func letter(_ lab: [Double], _ word: Int, _ j: Int, _ field: Int) -> Double {
        if Int(lab[1]) == word { return lab[10 + 3 * min(j, 9) + field] }
        if Int(lab[0]) == word { return lab[40 + 3 * min(j, 9) + field] }
        return field == 2 ? 6 : 0
    }
    static func tag(_ lab: [Double], _ word: Int, _ field: Int) -> Double {
        if Int(lab[1]) == word { return field == 0 ? lab[4] : field == 1 ? lab[5] : 1 - 0.06 * (1 - lab[4]) }
        if Int(lab[0]) == word { return field == 0 ? lab[6] : field == 1 ? lab[7] : 1 - 0.06 * (1 - lab[6]) }
        return field == 2 ? 1 : 0
    }
    static func word(_ lab: [Double], _ word: Int) -> Double {
        if Int(lab[1]) == word { return lab[8] }
        if Int(lab[0]) == word { return lab[9] }
        return 1
    }
}

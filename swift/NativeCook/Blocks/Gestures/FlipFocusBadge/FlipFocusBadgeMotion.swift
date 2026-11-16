import Foundation

/// Pure RN timeline, in seconds, degrees and 402-wide design points.
enum FlipFocusBadgeMotion {
    static let loop = 8.1
    static let swap1: Double = {
        var lo = 0.0, hi = 2.0
        for _ in 0..<40 {
            let mid = (lo + hi) / 2
            if springAt(mid, 0.7, 0.82) < 0.5 { lo = mid } else { hi = mid }
        }
        return 4 + (lo + hi) / 2
    }()
    static let hour = swap1 - 0.12
    static let wipe = hour + 0.24
    static let swap2 = 6.4

    struct Look {
        var yaw = 0.0, pitch = 0.0, ringYaw = 0.0, depth = 1.0, value = 10.0
        var clock = 0.0, sat = 0.4, fill = 0.4, hint = 0.0, sheetK = 0.0, labelK = 0.0
        var glintPlus = 0.0, glintHead = 0.0, glintSat = 0.0, press = 1.0
        var popPlus = 1.0, popHr = 1.0, env = 0.0, sweep = -1.0, tPhase = 0.0
    }
    static func clamp01(_ x: Double) -> Double { min(max(x, 0), 1) }
    static func mix(_ a: Double, _ b: Double, _ k: Double) -> Double { a + (b - a) * k }
    static func seg(_ t: Double, _ a: Double, _ b: Double) -> Double { clamp01((t - a) / (b - a)) }
    static func smooth01(_ x: Double) -> Double { let k = clamp01(x); return k * k * (3 - 2 * k) }
    static func easeInOut(_ x: Double) -> Double {
        let k = clamp01(x)
        return k < 0.5 ? 4 * k * k * k : 1 - pow(-2 * k + 2, 3) / 2
    }
    static func easeOut(_ x: Double, _ power: Double) -> Double { 1 - pow(1 - clamp01(x), power) }
    static func timing(_ x: Double) -> Double {
        let k = clamp01(x)
        return k < 0.5 ? 2 * k * k : 1 - pow(-2 * k + 2, 2) / 2
    }
    static func springAt(_ t: Double, _ response: Double, _ zeta: Double) -> Double {
        if t <= 0 { return 0 }
        let w = 2 * Double.pi / response
        if zeta >= 1 { return 1 - exp(-w * t) * (1 + w * t) }
        let wd = w * sqrt(1 - zeta * zeta)
        return 1 - exp(-zeta * w * t) * (cos(wd * t) + zeta * w / wd * sin(wd * t))
    }
    static func plateYawAt(_ t: Double) -> Double {
        if t < 0.6 { return 0 }
        if t < 1.5 { return 70 * easeOut((t - 0.6) / 0.9, 2.2) }
        if t < 1.9 { return 70 + 3 * smooth01((t - 1.5) / 0.4) }
        if t < 4 { return 73 + 107 * springAt(t - 1.9, 0.6, 0.8) }
        if t < 5.8 { return 180 + 180 * springAt(t - 4, 0.7, 0.82) }
        if t < 7 { return 360 * (1 - easeInOut((t - 5.8) / 1.2)) }
        return 0
    }
    static func jointStep(_ theta: Double, _ omega: Double, _ target: Double, _ dt: Double) -> (Double, Double) {
        let a = 7.5 * 7.5 * (target - theta) - 2 * 0.5 * 7.5 * omega
        let w2 = omega + a * dt
        return (theta + w2 * dt, w2)
    }
    static func limitLag(_ plate: Double, _ ring: Double) -> Double { plate - 45 * tanh((plate - ring) / 45) }
    static let ringN = Int((loop * 120).rounded())
    static let ringTable: [Double] = {
        var theta = 0.0, omega = 0.0, out: [Double] = []
        for cycle in 0..<4 {
            for i in 0...ringN {
                (theta, omega) = jointStep(theta, omega, plateYawAt(min(Double(i) / 120, loop)), 1 / 120)
                if cycle == 3 { out.append(theta) }
            }
        }
        let err = out[ringN] - out[0], o0 = out[0]
        for i in 0...ringN { out[i] -= o0 + err * Double(i) / Double(ringN) }
        return out
    }()
    static func ringYawAt(_ t: Double) -> Double {
        let x = min(max(t, 0), loop) / loop * Double(ringN)
        let i = min(Int(floor(x)), ringN - 1)
        return limitLag(plateYawAt(t), mix(ringTable[i], ringTable[i + 1], x - Double(i)))
    }
    static func trapezoid(_ k: Double, _ r: Double) -> Double {
        let x = clamp01(k), total = 1 - r
        if x < r { return x * x / (2 * r) / total }
        if x <= 1 - r { return (x - r / 2) / total }
        let y = x - (1 - r)
        return (1 - 1.5 * r + y - y * y / (2 * r)) / total
    }
    static func pulse(_ t: Double, _ c: Double, _ w: Double) -> Double { exp(-pow((t - c) / w, 2)) }
    static func lookAt(_ raw: Double) -> Look {
        let t = min(max(raw, 0), loop)
        var l = Look()
        l.yaw = plateYawAt(t); l.ringYaw = ringYawAt(t)
        l.pitch = t > 1 && t < 2.5 ? 10 * pow(sin(.pi * (t - 1) / 1.5), 2) : 0
        if t >= 4 && t < swap1 { l.depth = 1 - 0.96 * easeInOut((t - 4) / (swap1 - 4)) }
        else if t >= swap1 && t < 5.8 { l.depth = 1 - 0.96 * (1 - springAt(t - swap1, 0.497, 0.435)) }
        l.value = t >= swap1 && t < swap2 ? 11 : 10
        let run = trapezoid(seg(t, 2.4, 4), 0.1)
        l.clock = t >= 4.45 ? 0 : 2160 * run
        l.fill = 0.4 + 0.6 * run
        if t >= wipe && t < wipe + 0.4 { l.fill = 1 - easeInOut((t - wipe) / 0.4) }
        else if t >= wipe + 0.4 && t < 6 { l.fill = 0 }
        else if t >= 6 { l.fill = 0.4 * easeInOut((t - 6) / 0.9) }
        l.sat = t < wipe ? l.fill : t < 6 ? 0 : l.fill
        l.hint = max(smooth01((t - 2) / 0.3) - smooth01((t - 4.15) / 0.3), 0)
        l.sheetK = t < 5.9 ? easeInOut((t - hour) / 0.5) : 1 - easeInOut((t - 5.9) / 1)
        l.labelK = t < 5.9 ? smooth01((t - wipe) / 0.4) : 1 - smooth01((t - 5.9) / 0.6)
        l.glintPlus = max(pulse(t, 0.86, 0.07), pulse(t, 4.7, 0.07))
        l.glintHead = max(pulse(t, 1.08, 0.07), pulse(t, 4.78, 0.07))
        l.glintSat = max(pulse(t, 1.27, 0.07), pulse(t, 4.04, 0.06), pulse(t, 4.86, 0.07))
        l.press = 1 + 0.03 * (springAt(t - 0.5, 0.35, 0.85) - springAt(t - 2, 0.35, 0.85))
        l.popPlus = t < swap1 || t > 5.8 ? 1 : 0.6 + 0.4 * springAt(t - swap1 - 0.04, 0.45, 0.68)
        l.popHr = t < swap1 || t > 5.8 ? 1 : 0.6 + 0.4 * springAt(t - swap1 - 0.08, 0.45, 0.68)
        l.env = 5 * .pi / 180 * sin(2 * .pi * t / loop)
        let pass = (t - hour) / 0.24
        l.sweep = pass >= 0 && pass <= 1 ? pass : -1
        l.tPhase = t
        return l
    }
    static func rollAt(_ k: Double, _ slot: Int) -> Double { easeInOut((k - Double(slot) * 0.2 * 0.9) / 0.6) }
    static func rubber(_ v: Double) -> Double {
        abs(v) <= 28 ? v : (v < 0 ? -1 : 1) * (28 + (abs(v) - 28) * 0.3)
    }
    /// JavaScript Math.round chooses +∞ for half values, including negative turns.
    static func nearestFace(_ angle: Double) -> Double { floor(angle / 180 + 0.5) * 180 }
    static func isBack(_ angle: Double) -> Bool { let n = Int(floor(angle / 180 + 0.5)); return (n % 2 + 2) % 2 != 0 }
}

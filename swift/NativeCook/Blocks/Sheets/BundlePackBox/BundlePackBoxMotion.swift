import Foundation

// The RN worklet's scalar functions and explicit Euler touch spring, value for value.
enum BundlePackBoxMotion {
    static let loop = 9.0
    static func clamp(_ v: Double) -> Double { min(1, max(0, v)) }
    static func smooth(_ v: Double) -> Double {
        let x = clamp(v)
        return x * x * x * (x * (x * 6 - 15) + 10)
    }
    static func mix(_ a: Double, _ b: Double, _ k: Double) -> Double { a + (b - a) * k }
    static func progress(_ t: Double) -> Double {
        if t < 1.2 { return 0 }
        if t < 3.4 { return smooth((t - 1.2) / 2.2) }
        if t < 5.6 { return 1 }
        if t < 7.2 { return 1 - smooth((t - 5.6) / 1.6) }
        return 0
    }
    struct Frame {
        var p = 0.0
        var t = 0.0
        var burst = 0.0
        var tape: Double { smooth((p - 0.93) / 0.07) }
        var stamp: Double { clamp((p - 0.96) / 0.04) }
        var packed: Bool { p > 0.97 }
    }
    static func at(_ time: Double) -> Frame {
        let t = time.truncatingRemainder(dividingBy: loop)
        return Frame(p: progress(t), t: t, burst: t >= 5.6 && t < 6.4 ? (t - 5.6) / 0.8 : 0)
    }
}

final class BundlePackBoxDriver {
    var p = 0.0, v = 0.0, target = 0.0, p0 = 0.0
    var lastTouch = -100.0, loopT = 0.0, burstAt = -10.0
    var pinch = false, scripted = false, armed = false
    var now = 0.0
    var success: (() -> Void)?
    func step(_ wall: Double, _ auto: Bool, _ reduced: Bool) -> BundlePackBoxMotion.Frame {
        now += wall
        if auto && !pinch && now - lastTouch > 3.5 && (scripted || (p < 0.001 && target == 0)) {
            if !scripted { scripted = true; loopT = 7.4 }
            loopT = (loopT + wall).truncatingRemainder(dividingBy: 9)
            p = BundlePackBoxMotion.progress(loopT); v = 0; target = p > 0.5 ? 1 : 0
            return .init(p: p, t: now, burst: loopT >= 5.6 && loopT < 6.4 ? (loopT - 5.6) / 0.8 : 0)
        }
        scripted = false
        if !pinch {
            let dt = min(wall, 0.05), kk = reduced ? 500.0 : 60.0
            v += (kk * (target - p) - 2 * sqrt(kk) * v) * dt
            p = BundlePackBoxMotion.clamp(p + v * dt)
        }
        let nextArmed = p > 0.97
        if nextArmed != armed {
            armed = nextArmed
            if armed { success?() } else { burstAt = now }
        }
        return .init(p: p, t: now, burst: now - burstAt < 0.8 ? (now - burstAt) / 0.8 : 0)
    }
    func toggle() { target = target > 0.5 ? 0 : 1; scripted = false; lastTouch = now }
    func begin() { pinch = true; scripted = false; p0 = p }
    func update(_ scale: Double) { p = BundlePackBoxMotion.clamp(p0 + (1 - scale) * 1.6); lastTouch = now }
    func end(_ velocity: Double) {
        guard pinch else { return }
        pinch = false
        target = velocity < -0.4 ? 1 : velocity > 0.4 ? 0 : p > 0.5 ? 1 : 0
        lastTouch = now
    }
}

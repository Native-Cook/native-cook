import Foundation

enum DollhouseFloorsMotion {
    static let loop = 9.0
    static func clamp(_ x: Double) -> Double { max(0, min(1, x)) }
    static func smoother(_ k: Double) -> Double {
        let x = clamp(k)
        return x * x * x * (x * (x * 6 - 15) + 10)
    }
    struct Frame {
        var e = 0.0
        var sel = -1
        var selK = [0.0, 0.0, 0.0]
    }
    static func script(_ time: Double) -> Frame {
        let t = time.truncatingRemainder(dividingBy: loop)
        var e = 0.0
        if t >= 1 && t < 2.2 { e = smoother((t - 1) / 1.2) }
        else if t >= 2.2 && t < 6.3 { e = 1 }
        else if t >= 6.3 && t < 7.5 { e = 1 - smoother((t - 6.3) / 1.2) }
        let sel = t >= 2.7 && t < 4.1 ? 0 : t >= 4.1 && t < 5.5 ? 1 : -1
        return Frame(e: e, sel: sel)
    }
    // RN's pinned hook keeps following the selected floor while its script time is frozen.
    static func pinned(_ t: Double) -> Frame {
        var f = script(t)
        f.selK = (0..<3).map { f.sel >= 0 && f.sel != $0 ? 1 : 0 }
        return f
    }
    static func lifts(_ e: Double, _ reduced: Bool) -> (Double, Double) {
        let a = reduced ? 0 : 1.3 * smoother((e - 0.15) / 0.85)
        return (a, reduced ? 0 : 1.3 * smoother(e / 0.8) + a)
    }
}

final class DollhouseFloorsDriver {
    var e = 0.0, v = 0.0, target = 0.0, e0 = 0.0
    var drag = false, scripted = false
    var sel = -1
    var selK = [0.0, 0.0, 0.0]
    var lastTouch = -100.0, loopT = 0.0, now = 0.0
    func step(_ wall: Double, auto: Bool) -> DollhouseFloorsMotion.Frame {
        let dt = min(wall, 0.05)
        now += wall
        if auto && !drag && now - lastTouch > 3.5 && (scripted || (e < 0.001 && sel == -1)) {
            if !scripted { scripted = true; loopT = 0 }
            loopT = (loopT + wall).truncatingRemainder(dividingBy: DollhouseFloorsMotion.loop)
            let f = DollhouseFloorsMotion.script(loopT)
            e = f.e; sel = f.sel; target = e > 0.5 ? 1 : 0
        } else {
            scripted = false
            if !drag {
                let k = 140.0, c = 2 * 0.72 * sqrt(140.0)
                v += (k * (target - e) - c * v) * dt
                e += v * dt
            }
        }
        for i in 0..<3 {
            let want = sel >= 0 && sel != i ? 1.0 : 0.0
            selK[i] += (want - selK[i]) * (1 - exp(-10 * dt))
        }
        return .init(e: e, sel: sel, selK: selK)
    }
    func pick(_ i: Int) {
        scripted = false; lastTouch = now
        if i < 0 { target = target > 0.5 ? 0 : 1; sel = -1 }
        else { target = 1; sel = sel == i ? -1 : i }
    }
    func panStart() { drag = true; scripted = false; e0 = e; v = 0 }
    func pan(_ y: Double) {
        let raw = e0 - y / 220
        e = raw < 0 ? raw * 0.2 : raw > 1 ? 1 + (raw - 1) * 0.25 : raw
        lastTouch = now
    }
    func panEnd(_ vy: Double) {
        guard drag else { return }
        drag = false
        target = vy < -400 ? 1 : vy > 400 ? 0 : e > 0.5 ? 1 : 0
        if target == 0 { sel = -1 }
        v = -vy / 220; lastTouch = now
    }
    @discardableResult func tap(_ y: Double, width: Double, height: Double) -> Bool {
        lastTouch = now; scripted = false
        if e < 0.5 { target = 1; return false }
        let (lift1, lift2) = DollhouseFloorsMotion.lifts(e, false)
        let zs = [0.8, 2.65 + lift1 + 0.8, 5.3 + lift2 + 0.6]
        let scale = (width * 0.074).rounded(), base = (height * 0.63).rounded()
        let best = zs.indices.min { abs(y - (base - 3.5 * scale * 0.5 - zs[$0] * scale)) < abs(y - (base - 3.5 * scale * 0.5 - zs[$1] * scale)) } ?? 0
        sel = sel == best ? -1 : best
        return true
    }
    var settled: Bool {
        !drag && abs(e - target) < 0.000001 && abs(v) < 0.000001 && (0..<3).allSatisfy {
            abs(selK[$0] - (sel >= 0 && sel != $0 ? 1 : 0)) < 0.000001
        }
    }
}

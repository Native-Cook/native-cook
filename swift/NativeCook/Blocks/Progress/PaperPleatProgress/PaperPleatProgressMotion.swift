import Foundation

/// A direct port of the committed RN clock. Seconds, independent of the renderer.
final class PaperPleatProgressMotion {
    struct Frame {
        var p = 0.0, sw = 0.0, t = 0.0, boost = 1.0, fade = 1.0, lift = 1.0, pct = 0.0
        var done = false
    }
    static func seg(_ t: Double, _ a: Double, _ b: Double) -> Double { min(1, max(0, (t-a)/(b-a))) }
    static func io(_ x: Double) -> Double { x < 0.5 ? 2*x*x : 1-pow(-2*x+2, 2)/2 }
    static func open(_ t: Double) -> Double {
        if t < 0.4 { return 0 }; if t < 4.4 { return io(seg(t, 0.4, 4.4)) }
        if t < 5.4 { return 1 }; if t < 6.5 { return 1-io(seg(t, 5.4, 6.5)) }; return 0
    }
    static func frame(_ time: Double) -> Frame {
        let t = time.truncatingRemainder(dividingBy: 7), p = open(t), done = t >= 4.4 && t < 5.4
        return Frame(p: p, sw: done ? sin(seg(t, 4.4, 5.4) * .pi) : 0, t: seg(t, 4.4, 5.4), pct: p, done: done)
    }
    var mode = 0, last = -1.0, start = -1.0, time = 0.0, fold = -1.0, p0 = 0.0, sw0 = 0.0
    var pulse = -1.0, touches = 0, idle = 0.0, watching = false, still = 0.0, shown = false, dones = 0
    var out = Frame()
    var settled: Bool { mode == 2 && still >= 5.4 }
    func setStill(_ enabled: Bool) {
        if enabled && mode != 2 { mode = 2; still = 0; shown = false }
        else if !enabled && mode == 2 { mode = 0; start = -1 }
    }
    func touchDown(_ sculpture: Bool) {
        watching = true
        if mode == 2 { shown = shown || still >= 0.4; still = 0; return }
        if mode == 0 { mode = 1; time = start < 0 ? 0 : (last-start).truncatingRemainder(dividingBy: 7); fold = -1 }
        touches += 1; idle = 0
        if sculpture && fold < 0 && time > 0.4 && time < 4.4 { pulse = 0 }
    }
    func touchUp() { touches = max(0, touches-1); idle = 0 }
    func step(_ now: Double, autoPlay: Bool) {
        let dt = last < 0 ? 0 : min(max(now-last, 0), 0.05); last = now
        if mode == 2 {
            let before = still; still += dt
            if before < 4.4 && still >= 4.4 && watching { dones += 1; watching = false }
            out = Frame(p: 1, fade: shown ? 1 : Self.seg(still, 0, 0.4),
                        lift: 1 + (still >= 4.4 ? 0.1*sin(Self.seg(still, 4.4, 5.4) * .pi) : 0),
                        pct: Self.open(min(still, 4.4)), done: still >= 4.4); return
        }
        if mode == 0 {
            if !autoPlay { mode = 1; time = 0 }
            else {
                if start < 0 { start = now }; let before = out.done
                out = Self.frame(now-start)
                if out.done && !before && watching { dones += 1; watching = false }; return
            }
        }
        var f = Self.frame(time)
        if pulse >= 0 { pulse += dt; if pulse >= 0.3 { pulse = -1 } }
        if fold >= 0 {
            fold = min(1.1, fold+dt); let k = 1-Self.io(fold/1.1)
            f.p = p0*k; f.sw = sw0*k; f.pct = p0*k; f.done = false
            if fold >= 1.1 { mode = 0; start = now; out = Self.frame(0); return }
        }
        f.boost = pulse >= 0 ? 1+0.4*sin(.pi*pulse/0.3) : 1; out = f
        if touches > 0 || fold >= 0 { idle = 0; return }; idle += dt
        if !autoPlay || idle < 2 { return }; fold = 0; p0 = f.p; sw0 = f.sw
    }
}

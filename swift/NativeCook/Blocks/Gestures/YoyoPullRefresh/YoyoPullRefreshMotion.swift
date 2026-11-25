import Foundation

/// The committed RN timeline, in seconds and points. Touch integrates only the changing spin.
enum YoyoPullRefreshMotion {
    static let loop = 6.0
    static let status = ["Pull to refresh", "Refreshing…", "Up to date", "Pull to refresh"]
    static func seg(_ t: Double, _ a: Double, _ b: Double) -> Double { min(1, max(0, (t - a) / (b - a))) }
    static func out(_ x: Double) -> Double { 1 - pow(1 - x, 3) }
    static func quad(_ x: Double) -> Double { x < 0.5 ? 2 * x * x : 1 - pow(-2 * x + 2, 2) / 2 }
    static func spin(_ t: Double) -> Double {
        if t < 0.8 { return 0 }; if t < 1.7 { return 0.8 }
        if t < 2.05 { return 0.8 + 4.2 * seg(t, 1.7, 2.05) }; if t < 3.6 { return 5 }
        if t < 4.2 { return 5 - 4 * seg(t, 3.6, 4.2) }; return 1
    }
    static func turns(_ t: Double) -> Double {
        if t <= 0.8 { return 0 }
        var a = 0.8 * (min(t, 1.7) - 0.8)
        if t <= 1.7 { return a }
        let s = seg(t, 1.7, 2.05); a += 0.35 * (0.8 * s + 2.1 * s * s)
        if t <= 2.05 { return a }; a += 5 * (min(t, 3.6) - 2.05)
        if t <= 3.6 { return a }
        let q = seg(t, 3.6, 4.2); a += 0.6 * (5 * q - 2 * q * q)
        return t <= 4.2 ? a : a + (t - 4.2)
    }
    static func pullY(_ d: Double) -> Double { -90 + 1.3 * max(0, d) }
    static func beatD(_ t: Double, _ from: Double) -> Double {
        let u = t - 1.7
        if t < 3.75 { return 190 + (from - 190) * exp(-7 * u) * cos(14 * u) + 20 * exp(-7 * u) * sin(14 * u) }
        return t < 4.25 ? 190 * (1 - quad(seg(t, 3.75, 4.25))) : 0
    }
    static func beatY(_ t: Double, _ from: Double) -> Double {
        if t < 2.05 { return from + (258 - from) * pow(seg(t, 1.7, 2.05), 2) }
        if t < 3.6 { let u = t - 2.05; return 258 + 12 * exp(-6 * u) * sin(18 * u) }
        return t < 4.2 ? 258 - 348 * quad(seg(t, 3.6, 4.2)) : -90
    }
    struct Status {
        var from = 0, to = 0
        var u = 1.0
        var shown: Int { u < 0.5 ? from : to }
        var alpha: Double { u < 0.5 ? 1 - 2 * u : 2 * u - 1 }
    }
    static func statusAt(_ t: Double) -> Status {
        let times = [-1.0, 1.75, 3.7, 5.3]
        let k = (1...3).last(where: { t >= times[$0] }) ?? 0
        return k == 0 ? Status() : Status(from: k - 1, to: k, u: seg(t, times[k], times[k] + 0.24))
    }
    struct Frame {
        var d = 0.0, y = -90.0, turns = 0.0, spin = 0.0, pulse = 0.0
        var status = Status()
        var finger = 0.0, alpha = 1.0
    }
    static func loopFrame(_ t: Double) -> Frame {
        let d = t < 0.8 ? 0 : t < 1.7 ? 190 * out(seg(t, 0.8, 1.7)) : beatD(t, 190)
        return Frame(d: d, y: t < 1.7 ? pullY(d) : beatY(t, 157), turns: turns(t), spin: spin(t), pulse: t,
                     status: statusAt(t), finger: seg(t, 0.7, 0.82) * (1 - seg(t, 1.7, 1.82)))
    }
    static func reducedFrame(_ u: Double) -> Frame {
        if u < 0 { return Frame(alpha: 0) }
        return Frame(y: 258, turns: 0.5 * u, spin: 0.5, pulse: u, status: statusAt(min(6, 1.7 + u)),
                     alpha: seg(u, 0, 0.2) * (1 - seg(u, 1.9, 2.1)))
    }
    static func dragD(_ total: Double, _ overflow: Double) -> Double {
        if total > 0 { return 190 * (1 - exp(-total / 190)) }
        if total >= -overflow { return total }
        let over = -overflow - total; return -overflow - over / (1 + over / 120)
    }
}

/// The same takeover, scroll, release and idle state machine as yoyoMotion.ts.
final class YoyoPullRefreshTouch {
    enum Mode { case rest, drag, spring, refresh }
    var manual = false, touching = false
    var loopStart = 0.0, lastTouch = -100.0
    var mode = Mode.rest
    var base = 0.0, drag = 0.0, from = 0.0, to = 0.0, at = 0.0
    var refAt = -100.0, refD = 190.0, refY = 157.0
    var stFrom = 0, stTo = 0
    var stAt = -1.0, turns = 0.0, spin = 0.0, overflow = 0.0
    var rmAt = -1.0
    var realRefresh = false
    func beat(_ c: Double) -> Double { mode == .refresh ? min(1.7 + (c - refAt), 6) : -1 }
    func d(_ c: Double) -> Double {
        switch mode {
        case .drag: return YoyoPullRefreshMotion.dragD(base + drag, overflow)
        case .spring: return from + (to - from) * YoyoPullRefreshMotion.out(YoyoPullRefreshMotion.seg(c, at, at + 0.35))
        case .refresh: return YoyoPullRefreshMotion.beatD(beat(c), refD)
        case .rest: return from
        }
    }
    func status(_ c: Double) -> YoyoPullRefreshMotion.Status {
        if stAt >= 0 { return .init(from: stFrom, to: stTo, u: YoyoPullRefreshMotion.seg(c, stAt, stAt + 0.24)) }
        return mode == .refresh || refAt > -100 ? YoyoPullRefreshMotion.statusAt(min(1.7 + (c - refAt), 6)) : .init()
    }
    func frame(_ c: Double, reduced: Bool) -> YoyoPullRefreshMotion.Frame {
        if reduced { return YoyoPullRefreshMotion.reducedFrame(rmAt >= 0 ? c - rmAt : -1) }
        if !manual { return YoyoPullRefreshMotion.loopFrame(((c - loopStart).truncatingRemainder(dividingBy: 6) + 6).truncatingRemainder(dividingBy: 6)) }
        return .init(d: d(c), y: mode == .refresh ? YoyoPullRefreshMotion.beatY(beat(c), refY) : YoyoPullRefreshMotion.pullY(d(c)),
                     turns: turns, spin: spin, pulse: beat(c) >= 0 ? beat(c) : c, status: status(c))
    }
    func takeOver(_ c: Double) {
        lastTouch = c
        if manual { return }
        let t = ((c - loopStart).truncatingRemainder(dividingBy: 6) + 6).truncatingRemainder(dividingBy: 6)
        manual = true; turns = YoyoPullRefreshMotion.turns(t); spin = YoyoPullRefreshMotion.spin(t); stAt = -1; refAt = -100
        if t >= 0.8 && t < 1.7 { mode = .spring; from = YoyoPullRefreshMotion.loopFrame(t).d; to = from; at = c }
        else if t >= 1.7 { mode = .refresh; refAt = c - (t - 1.7); refD = 190; refY = 157 }
        else { mode = .rest; from = 0 }
    }
    func canGrab(_ c: Double) -> Bool { mode != .refresh || beat(c) >= 4.25 }
    func grab(_ c: Double) {
        let offset = d(c), shown = status(c).shown
        if (mode == .refresh || stAt >= 0 || refAt > -100) && shown != 0 && shown != 3 {
            stFrom = shown; stTo = 0; stAt = c
        }
        mode = .drag
        base = offset > 0 ? -190 * log(1 - min(offset, 189.99) / 190) : max(offset, -overflow)
        drag = 0; touching = true; lastTouch = c
    }
    func refresh(_ c: Double, real: Bool) {
        let offset = d(c); mode = .refresh; refAt = c; refD = max(0, offset)
        refY = YoyoPullRefreshMotion.pullY(offset); stAt = -1; manual = true; realRefresh = real
    }
    func release(_ c: Double) {
        let offset = d(c)
        if offset > 120 { refresh(c, real: true) }
        else { mode = .spring; from = offset; to = offset > 0 ? 0 : max(-overflow, offset); at = c }
        touching = false; lastTouch = c
    }
    func step(_ c: Double, _ dt: Double, autoPlay: Bool) {
        guard manual else { return }
        let nextSpin = mode == .refresh ? YoyoPullRefreshMotion.spin(beat(c)) : d(c) > 0 ? 0.8 : 0
        turns += (spin + nextSpin) / 2 * dt; spin = nextSpin
        if mode == .spring && c >= at + 0.35 { mode = .rest; from = to }
        guard autoPlay && !touching && c - lastTouch >= 2 else { return }
        if mode == .rest && abs(from) > 0.01 { mode = .spring; to = 0; at = c; return }
        let st = status(c)
        if mode != .drag && !(mode == .spring && c < at + 0.35) && abs(d(c)) <= 0.01 && st.shown % 3 == 0 && st.u >= 1 {
            manual = false; mode = .rest; from = 0; loopStart = c; refAt = -100; stAt = -1; realRefresh = false
        }
    }
}

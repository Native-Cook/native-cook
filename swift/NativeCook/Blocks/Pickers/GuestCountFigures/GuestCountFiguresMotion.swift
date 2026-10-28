import Foundation

/*
 Guest Count Figures — the model, the springs and the autoPlay script.

 The screen is a pure function of an event list and the time: each event sets the state
 (adults, children, rows or the big counter) at a time and may name the control a finger pressed.
 Positions are chained analytic springs: every event restarts the spring from the value and the
 velocity it had, towards the new target. A figure that appears starts at its slot and hops in;
 one that leaves crouches, rises and fades.

 autoPlay: loop = 10 s
   0.0 – 1.2   rest: one adult, the big counter
   1.2         big +        → 2 adults (the second adult hops in, the pair re-centres)
   2.5         Add children → the counter folds into the Adults / Children rows
   3.6, 4.6    children +   → 1, 2 children (each hops in; at 4 people both + disable)
   4.6 – 6.2   peak hold
   6.2, 6.9    children −   → 1, 0
   7.6         Adults only  → back to the big counter
   8.5         big −        → 1 adult
   9.1 – 10.0  rest (the frame at 10 is the frame at 0)
*/

struct GuestCountFiguresState: Equatable {
    var adults: Int
    var children: Int
    var rows: Bool
}

enum GuestCountFiguresControl: Int {
    case none = -1, adultMinus = 0, adultPlus, childMinus, childPlus, pill
}

struct GuestCountFiguresEvent {
    var t: Double
    var s: GuestCountFiguresState
    var press: GuestCountFiguresControl
}

struct GuestCountFiguresFigureFrame {
    var index: Int
    var x: Double
    var lift: Double
    var sx: Double
    var sy: Double
    var alpha: Double
}

struct GuestCountFiguresFrame {
    var figures: [GuestCountFiguresFigureFrame] = []
    /// 0 = the big counter, 1 = the two rows.
    var m = 0.0
    var adultsV = 1.0
    var childrenV = 0.0
    /// Disabled amount of adult −, adult +, child −, child +.
    var disabled = [1.0, 0, 1, 0]
    /// Press amount of adult −, adult +, child −, child +, the pill.
    var press = [0.0, 0, 0, 0, 0]
}

struct GuestCountFiguresGeo {
    var w = 393.0
    var h = 852.0
    var top = 59.0
    var bottom = 34.0

    var cx: Double { w / 2 }
    var ground: Double { top + 296 }
    var numY: Double { top + 405 }
    var rowA: Double { top + 385 }
    var rowC: Double { top + 463 }
    var divider: Double { top + 424 }
    var pillY: Double { top + 566 }
    var footY: Double { h - bottom - 40 }
    var progressY: Double { h - bottom - 84 }
    var rowPlusX: Double { w - 42 }
    var rowNumX: Double { w - 80 }
    var rowMinusX: Double { w - 118 }
}

enum GuestCountFiguresMotion {
    static let loop = 10.0
    static let maxPeople = 4
    static let rest = GuestCountFiguresState(adults: 1, children: 0, rows: false)
    static let peak = GuestCountFiguresState(adults: 2, children: 2, rows: true)
    static let idleBeforeBack = 2.0
    static let settle = 1.6

    static let script: [GuestCountFiguresEvent] = [
        .init(t: 0.0, s: rest, press: .none),
        .init(t: 1.2, s: .init(adults: 2, children: 0, rows: false), press: .adultPlus),
        .init(t: 2.5, s: .init(adults: 2, children: 0, rows: true), press: .pill),
        .init(t: 3.6, s: .init(adults: 2, children: 1, rows: true), press: .childPlus),
        .init(t: 4.6, s: .init(adults: 2, children: 2, rows: true), press: .childPlus),
        .init(t: 6.2, s: .init(adults: 2, children: 1, rows: true), press: .childMinus),
        .init(t: 6.9, s: .init(adults: 2, children: 0, rows: true), press: .childMinus),
        .init(t: 7.6, s: .init(adults: 2, children: 0, rows: false), press: .pill),
        .init(t: 8.5, s: rest, press: .adultMinus),
    ]

    // Figures: 0…3 adults, 4…6 children, in that order along the ground.
    static let figureCount = 7
    static let adultSlot = 46.0
    static let childSlot = 38.0
    static let gap = 12.0
    /// Every figure is drawn 10 % larger than its art (GuestCountFiguresArt), slots included.
    static let figureScale = 1.1

    static func isChild(_ i: Int) -> Bool { i >= 4 }
    static func present(_ i: Int, _ s: GuestCountFiguresState) -> Bool {
        i < 4 ? i < s.adults : (i - 4) < s.children
    }
    static func slot(_ i: Int) -> Double { (isChild(i) ? childSlot : adultSlot) * figureScale }

    /// Centre x of every figure in state s (NaN when absent), and its rank along the row.
    static func layout(_ s: GuestCountFiguresState, cx: Double) -> (x: [Double], rank: [Int]) {
        var total = 0.0
        var n = 0
        for i in 0..<figureCount where present(i, s) {
            total += slot(i)
            n += 1
        }
        total += gap * Double(max(0, n - 1))
        var x = [Double](repeating: .nan, count: figureCount)
        var rank = [Int](repeating: -1, count: figureCount)
        var cursor = cx - total / 2
        var r = 0
        for i in 0..<figureCount where present(i, s) {
            x[i] = cursor + slot(i) / 2
            rank[i] = r
            cursor += slot(i) + gap
            r += 1
        }
        return (x, rank)
    }

    // MARK: Curves

    static func clamp01(_ x: Double) -> Double { min(1, max(0, x)) }
    static func easeOut(_ x: Double) -> Double { 1 - pow(1 - x, 3) }
    static func easeIn(_ x: Double) -> Double { x * x * x }
    static func smooth(_ x: Double) -> Double { let c = clamp01(x); return c * c * (3 - 2 * c) }

    /// Advances an underdamped spring (displacement d from its target, velocity v) by dt.
    static func step(_ d: Double, _ v: Double, _ dt: Double, response: Double, damping: Double) -> (Double, Double) {
        if dt <= 0 { return (d, v) }
        let w0 = 2 * Double.pi / response
        let a = damping * w0
        let b = w0 * (1 - damping * damping).squareRoot()
        let e = exp(-a * dt)
        if e < 1e-9 { return (0, 0) }
        let c = cos(b * dt), s = sin(b * dt)
        let bb = (v + a * d) / b
        let nd = e * (d * c + bb * s)
        let nv = e * (v * c - (a * v + w0 * w0 * d) / b * s)
        return (nd, nv)
    }

    // Channels: 0…6 figure x, 7 mode, 8 adults value, 9 children value, 10…13 disabled.
    static let channels = 14
    static let response: [Double] = [0.55, 0.55, 0.55, 0.55, 0.55, 0.55, 0.55, 0.5, 0.42, 0.42, 0.3, 0.3, 0.3, 0.3]
    static let damping: [Double] = [0.72, 0.72, 0.72, 0.72, 0.72, 0.72, 0.72, 0.86, 0.72, 0.72, 0.95, 0.95, 0.95, 0.95]

    static func targets(_ s: GuestCountFiguresState, cx: Double) -> [Double] {
        var out = layout(s, cx: cx).x
        out.append(s.rows ? 1 : 0)
        out.append(Double(s.adults))
        out.append(Double(s.children))
        let total = s.adults + s.children
        out.append(s.adults <= 1 ? 1 : 0)
        out.append(total >= maxPeople ? 1 : 0)
        out.append(s.children <= 0 ? 1 : 0)
        out.append(total >= maxPeople ? 1 : 0)
        return out
    }

    // MARK: Figure moves

    /// A figure hopping in, τ seconds after it appeared: (lift, scale, sx, sy, alpha).
    static func enter(_ tau: Double) -> (Double, Double, Double, Double, Double) {
        let alpha = clamp01(tau / 0.12)
        let scale = 0.55 + 0.45 * easeOut(clamp01(tau / 0.30))
        var lift = 0.0, sx = 1.0, sy = 1.0
        if tau < 0.44 {
            lift = 36 * sin(Double.pi * max(0, tau) / 0.44)
        } else {
            let t2 = tau - 0.44
            let s = 0.12 * exp(-7 * t2) * cos(2 * Double.pi * t2 / 0.26)
            sy = 1 - s
            sx = 1 + 0.6 * s
        }
        return (lift, scale, sx, sy, alpha)
    }

    /// A figure leaving, τ seconds after it was removed.
    static func exit(_ tau: Double) -> (Double, Double, Double, Double, Double) {
        if tau < 0.08 {
            let k = tau / 0.08
            return (0, 1, 1 + 0.03 * k, 1 - 0.06 * k, 1)
        }
        let u = clamp01((tau - 0.08) / 0.26)
        let lift = 22 * easeOut(u)
        let scale = 1 - 0.45 * easeIn(u)
        return (lift, scale, 1.03 - 0.03 * u, 0.94 + 0.06 * u, 1 - easeIn(u))
    }

    static let exitTime = 0.34

    // MARK: Frame

    /// The frame at time t of an event list (sorted by time, the first one is the base).
    static func frame(_ ev: [GuestCountFiguresEvent], t: Double, cx: Double) -> GuestCountFiguresFrame {
        var f = GuestCountFiguresFrame()
        guard let first = ev.first else { return f }

        // Chained springs.
        var tg = targets(first.s, cx: cx)
        var x = tg.map { $0.isNaN ? cx : $0 }
        var v = [Double](repeating: 0, count: channels)
        var last = first.t
        var k = 1
        while k < ev.count && ev[k].t <= t {
            let dt = ev[k].t - last
            for c in 0..<channels {
                let (d, nv) = step(x[c] - tg[c], v[c], dt, response: response[c], damping: damping[c])
                x[c] = tg[c] + d
                v[c] = nv
            }
            let nt = targets(ev[k].s, cx: cx)
            for c in 0..<channels {
                if nt[c].isNaN { continue }               // an absent figure keeps where it was
                if c < figureCount && !present(c, ev[k - 1].s) {
                    x[c] = nt[c]                          // an arriving figure starts at its slot
                    v[c] = 0
                }
                tg[c] = nt[c]
            }
            last = ev[k].t
            k += 1
        }
        let dt = t - last
        for c in 0..<channels {
            let (d, nv) = step(x[c] - tg[c], v[c], dt, response: response[c], damping: damping[c])
            x[c] = tg[c] + d
            v[c] = nv
        }
        let applied = k            // events 0..<applied are in the past
        let now = ev[applied - 1].s

        f.m = x[7]
        f.adultsV = x[8]
        f.childrenV = x[9]
        f.disabled = [x[10], x[11], x[12], x[13]]

        // Presses.
        for j in 0..<ev.count where ev[j].press != .none {
            let d = t - ev[j].t
            guard d > -0.1 && d < 0.18 else { continue }
            let amount = d < 0 ? smooth((d + 0.1) / 0.1) : 1 - smooth(d / 0.18)
            let c = ev[j].press.rawValue
            f.press[c] = max(f.press[c], amount)
        }

        // Figures: the last time each one arrived or left.
        var changeAt = [Double](repeating: -.infinity, count: figureCount)
        var entered = [Bool](repeating: false, count: figureCount)
        for j in 1..<applied {
            for i in 0..<figureCount {
                let was = present(i, ev[j - 1].s), isIn = present(i, ev[j].s)
                if was != isIn {
                    changeAt[i] = ev[j].t
                    entered[i] = isIn
                }
            }
        }

        // Welcome hops: when someone arrives, the others bounce, nearest first.
        var welcome = [Double](repeating: 0, count: figureCount)
        var j = applied - 1
        while j >= 1 && t - ev[j].t < 1.2 {
            let rank = layout(ev[j].s, cx: cx).rank
            for n in 0..<figureCount where present(n, ev[j].s) && !present(n, ev[j - 1].s) {
                for i in 0..<figureCount where i != n && present(i, ev[j].s) && present(i, now) {
                    let dist = Double(abs(rank[i] - rank[n]))
                    let u = (t - ev[j].t - 0.30 - 0.05 * dist) / 0.30
                    if u > 0 && u < 1 { welcome[i] = max(welcome[i], 7 * sin(Double.pi * u)) }
                }
            }
            j -= 1
        }

        for i in 0..<figureCount {
            let isIn = present(i, now)
            let tau = t - changeAt[i]
            var mv: (Double, Double, Double, Double, Double)
            if isIn {
                mv = changeAt[i].isFinite && entered[i] ? enter(tau) : (0, 1, 1, 1, 1)
            } else {
                guard changeAt[i].isFinite && !entered[i] && tau < exitTime else { continue }
                mv = exit(tau)
            }
            f.figures.append(.init(index: i, x: x[i], lift: mv.0 + welcome[i],
                                   sx: mv.1 * mv.2, sy: mv.1 * mv.3, alpha: mv.4))
        }
        return f
    }

    static func scriptFrame(_ t: Double, cx: Double) -> GuestCountFiguresFrame {
        frame(script, t: t, cx: cx)
    }
}

/// The live model: the autoPlay clock, and the session a touch starts.
final class GuestCountFiguresSim {
    typealias M = GuestCountFiguresMotion

    private(set) var loopStart: Double?
    private(set) var session: [GuestCountFiguresEvent]?
    var touching = false
    private var lastTouch = 0.0
    private var restQueued = false

    var live: Bool { session != nil }

    func frame(at now: Double, cx: Double, auto: Bool, reduced: Bool) -> GuestCountFiguresFrame {
        if reduced {
            return M.frame(session ?? [.init(t: 0, s: M.peak, press: .none)], t: now + 100, cx: cx)
        }
        if var ev = session {
            if auto && !touching {
                if !restQueued && now >= lastTouch + M.idleBeforeBack {
                    ev.append(.init(t: lastTouch + M.idleBeforeBack, s: M.rest, press: .none))
                    session = ev
                    restQueued = true
                }
                if restQueued && now >= lastTouch + M.idleBeforeBack + M.settle {
                    session = nil
                    loopStart = lastTouch + M.idleBeforeBack + M.settle
                    return M.scriptFrame(loopTime(now), cx: cx)
                }
            }
            return M.frame(ev, t: now, cx: cx)
        }
        guard auto else { return M.frame([.init(t: 0, s: M.rest, press: .none)], t: 100, cx: cx) }
        if loopStart == nil { loopStart = now }
        return M.scriptFrame(loopTime(now), cx: cx)
    }

    private func loopTime(_ now: Double) -> Double {
        let t = (now - (loopStart ?? now)).truncatingRemainder(dividingBy: M.loop)
        return t < 0 ? t + M.loop : t
    }

    /// The state the screen is heading to now.
    func state(at now: Double, auto: Bool, reduced: Bool) -> GuestCountFiguresState {
        if let ev = session { return ev.last?.s ?? M.rest }
        if reduced { return M.peak }
        guard auto, loopStart != nil else { return M.rest }
        let t = loopTime(now)
        return M.script.last(where: { $0.t <= t })?.s ?? M.rest
    }

    /// Starts a session that carries on from what the screen shows.
    private func begin(_ now: Double, auto: Bool, reduced: Bool) {
        guard session == nil else { return }
        if reduced {
            session = [.init(t: now - 10, s: M.peak, press: .none)]
        } else if auto, let start = loopStart {
            let cycle = ((now - start) / M.loop).rounded(.down)
            let base = start + cycle * M.loop
            var ev = M.script.map { GuestCountFiguresEvent(t: base - M.loop + $0.t, s: $0.s, press: $0.press) }
            ev += M.script.filter { base + $0.t <= now }.map { GuestCountFiguresEvent(t: base + $0.t, s: $0.s, press: $0.press) }
            session = ev
        } else {
            session = [.init(t: now - 10, s: M.rest, press: .none)]
        }
    }

    func touch(_ now: Double, auto: Bool, reduced: Bool) {
        begin(now, auto: auto, reduced: reduced)
        lastTouch = now
        restQueued = false
    }

    /// Applies a new state from a finger. Returns true when something changed.
    @discardableResult
    func apply(_ s: GuestCountFiguresState, press: GuestCountFiguresControl, at now: Double,
               auto: Bool, reduced: Bool) -> Bool {
        touch(now, auto: auto, reduced: reduced)
        guard var ev = session, ev.last?.s != s else { return false }
        ev.append(.init(t: now, s: s, press: press))
        // Everything older than 3 s has settled: keep the last such event as the base.
        if let j = ev.lastIndex(where: { $0.t < now - 3 }), j > 0 {
            ev.removeFirst(j)
        }
        session = ev
        return true
    }

    func reset() {
        session = nil
        loopStart = nil
        restQueued = false
        touching = false
    }
}

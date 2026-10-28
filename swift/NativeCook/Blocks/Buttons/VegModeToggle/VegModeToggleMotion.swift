import Foundation

/*
 Veg Mode Toggle — the springs, the model and the autoPlay script.

 Every moving value is an analytic spring chained on events: an event restarts the spring from the
 value and the velocity it has at that moment, towards a new target. The screen is a pure function of
 those springs and the time, so the loop is exact and the three stacks match frame for frame.

 Channels: p (knob, 0 = Veg with the knob on the right, 1 = Non veg with the knob on the left),
 b / b2 (broccoli, and its front floret and bulb 60 ms late), m / m2 (drumstick, and its egg),
 q (the dish list), press (the knob pressed in).

 autoPlay: loop = 7 s
   0.0 – 1.0   rest, Veg
   1.0         tap → Non veg: the knob slides, the broccoli sinks, the drumstick pops up and wobbles,
               the count rolls 24 → 38 and the rows swap
   2.2 – 3.5   hold, Non veg
   3.5 – 4.3   a finger drags the knob back to 0.3: the drumstick sinks, the broccoli peeks out
   4.3         release → Veg: the knob snaps, the broccoli pops, the list swaps back
   5.5 – 7.0   rest, Veg (the frame at 7 is the frame at 0)
*/

struct VegModeToggleSpring {
    var response: Double
    var damping: Double
}

/// One analytic spring segment.
struct VegModeToggleSegment {
    var t0: Double
    var x0: Double
    var v0: Double
    var target: Double
    var spring: VegModeToggleSpring

    func eval(_ t: Double) -> (x: Double, v: Double) {
        let tau = t - t0
        if tau <= 0 { return (x0, v0) }
        let z = min(spring.damping, 0.999)
        let w0 = 2 * Double.pi / spring.response
        let wd = w0 * (1 - z * z).squareRoot()
        let a = x0 - target
        let b = (v0 + z * w0 * a) / wd
        let e = exp(-z * w0 * tau)
        let c = cos(wd * tau)
        let s = sin(wd * tau)
        return (target + e * (a * c + b * s),
                e * ((b * wd - z * w0 * a) * c - (a * wd + z * w0 * b) * s))
    }
}

/// A value driven by a chain of spring segments; a new one may start in the future (a delay).
struct VegModeToggleChannel {
    var segs: [VegModeToggleSegment]

    init(_ x: Double) {
        segs = [VegModeToggleSegment(t0: -1e9, x0: x, v0: 0, target: x, spring: .init(response: 1, damping: 1))]
    }

    func eval(_ t: Double) -> (x: Double, v: Double) {
        var seg = segs[0]
        for s in segs where s.t0 <= t { seg = s }
        return seg.eval(t)
    }

    /// The target of the segment running at time t.
    func target(at t: Double) -> Double {
        var seg = segs[0]
        for s in segs where s.t0 <= t { seg = s }
        return seg.target
    }

    /// Starts a spring towards `target` at time t; segments planned after t are dropped.
    mutating func go(_ target: Double, at t: Double, _ spring: VegModeToggleSpring) {
        let (x, v) = eval(t)
        segs.removeAll { $0.t0 > t }
        segs.append(.init(t0: t, x0: x, v0: v, target: target, spring: spring))
        if segs.count > 6 { segs.removeFirst(segs.count - 6) }
    }

    /// Holds a value (and velocity) from time t.
    mutating func set(_ x: Double, v: Double = 0, at t: Double) {
        segs.removeAll { $0.t0 > t }
        segs.append(.init(t0: t, x0: x, v0: v, target: x, spring: .init(response: 1, damping: 1)))
        if segs.count > 6 { segs.removeFirst(segs.count - 6) }
    }
}

struct VegModeToggleFrame {
    var p = 0.0
    var pv = 0.0
    var b = 1.0
    var bv = 0.0
    var b2 = 1.0
    var m = 0.0
    var mv = 0.0
    var m2 = 0.0
    var q = 0.0
    /// Where the list is heading (the rows stagger from the top either way).
    var qTarget = 0.0
    var press = 0.0
    /// The broccoli (or the drumstick) is drawn over the knob, not under it.
    var bFront = true
    var mFront = false
    var reduced = false
}

enum VegModeToggleMotion {
    static let loop = 7.0

    static let knob = VegModeToggleSpring(response: 0.42, damping: 0.78)
    static let enter = VegModeToggleSpring(response: 0.52, damping: 0.52)
    static let leave = VegModeToggleSpring(response: 0.24, damping: 1)
    static let list = VegModeToggleSpring(response: 0.5, damping: 0.9)
    static let pressIn = VegModeToggleSpring(response: 0.12, damping: 1)
    static let pressOut = VegModeToggleSpring(response: 0.3, damping: 0.6)
    static let calm = VegModeToggleSpring(response: 0.2, damping: 1)

    static let enterDelay = 0.08
    static let partDelay = 0.06
    static let listDelay = 0.05
    /// How fast the gap between the art and the knob's position closes while dragging.
    static let dragBlend = 0.12

    static let idleBeforeBack = 2.0
    static let settle = 1.6

    static let tapAt = 1.0
    static let dragFrom = 3.5
    static let dragTo = 4.3
    static let dragEnd = 0.3

    static func clamp01(_ x: Double) -> Double { min(1, max(0, x)) }
    static func smooth(_ x: Double) -> Double { let c = clamp01(x); return c * c * (3 - 2 * c) }
    static func smooth(_ a: Double, _ b: Double, _ x: Double) -> Double { smooth((x - a) / (b - a)) }

    /// How far a dish's art is out while the knob sits at p (1 at its own end).
    static func reach(_ p: Double) -> Double { smooth(0.2, 1, p) }

    /// The knob's position during the scripted drag.
    static func scriptP(_ t: Double) -> Double {
        1 - (1 - dragEnd) * smooth((t - dragFrom) / (dragTo - dragFrom))
    }
}

/// The springs of one toggle and a drag in progress.
final class VegModeToggleSim {
    typealias M = VegModeToggleMotion

    struct Drag {
        var t0: Double
        var p: Double
        var db: Double
        var db2: Double
        var dm: Double
        var dm2: Double
    }

    var p: VegModeToggleChannel
    var b: VegModeToggleChannel
    var b2: VegModeToggleChannel
    var m: VegModeToggleChannel
    var m2: VegModeToggleChannel
    var q: VegModeToggleChannel
    var press = VegModeToggleChannel(0)
    var mode: Int
    var drag: Drag?
    var reduced = false

    init(mode: Int = 0) {
        self.mode = mode
        let v = Double(mode)
        p = .init(v)
        b = .init(1 - v)
        b2 = .init(1 - v)
        m = .init(v)
        m2 = .init(v)
        q = .init(v)
    }

    /// Commits a mode: the knob slides, the old art leaves, the new one pops up after `delay`.
    func flip(to target: Int, at t: Double, delay: Double = M.enterDelay) {
        mode = target
        let veg = target == 0
        if reduced {
            p.go(Double(target), at: t, M.calm)
            for (ch, on) in [(\VegModeToggleSim.b, veg), (\.b2, veg), (\.m, !veg), (\.m2, !veg)] {
                self[keyPath: ch].go(on ? 1 : 0, at: t, M.calm)
            }
            q.go(Double(target), at: t, M.calm)
            return
        }
        p.go(Double(target), at: t, M.knob)
        if veg {
            m.go(0, at: t, M.leave)
            m2.go(0, at: t, M.leave)
            b.go(1, at: t + delay, M.enter)
            b2.go(1, at: t + delay + M.partDelay, M.enter)
        } else {
            b.go(0, at: t, M.leave)
            b2.go(0, at: t, M.leave)
            m.go(1, at: t + delay, M.enter)
            m2.go(1, at: t + delay + M.partDelay, M.enter)
        }
        q.go(Double(target), at: t + M.listDelay, M.list)
    }

    func tap(at t: Double) {
        press.go(1, at: t, M.pressIn)
        press.go(0, at: t + 0.1, M.pressOut)
        flip(to: 1 - mode, at: t)
    }

    func beginDrag(at t: Double) {
        let f = frame(at: t)
        drag = Drag(t0: t, p: f.p,
                    db: f.b - M.reach(1 - f.p), db2: f.b2 - M.reach(1 - f.p),
                    dm: f.m - M.reach(f.p), dm2: f.m2 - M.reach(f.p))
        press.go(1, at: t, M.pressIn)
    }

    func dragTo(_ p: Double) {
        drag?.p = p
    }

    /// Ends a drag: everything springs on from where it is. `target` nil = the nearest end.
    func release(at t: Double, velocity pv: Double, target: Int? = nil, delay: Double = 0) {
        let f = frame(at: t)
        drag = nil
        p.set(f.p, v: pv, at: t)
        b.set(f.b, at: t)
        b2.set(f.b2, at: t)
        m.set(f.m, at: t)
        m2.set(f.m2, at: t)
        press.go(0, at: t, M.pressOut)
        let to = target ?? ((f.p + 0.12 * pv) >= 0.5 ? 1 : 0)
        flip(to: to, at: t, delay: delay)
    }

    func frame(at t: Double) -> VegModeToggleFrame {
        var f = VegModeToggleFrame()
        f.reduced = reduced
        f.q = q.eval(t).x
        f.qTarget = q.target(at: t)
        f.press = press.eval(t).x
        if let d = drag {
            let k = exp(-max(0, t - d.t0) / M.dragBlend)
            f.p = d.p
            f.b = M.reach(1 - d.p) + d.db * k
            f.b2 = M.reach(1 - d.p) + d.db2 * k
            f.m = M.reach(d.p) + d.dm * k
            f.m2 = M.reach(d.p) + d.dm2 * k
            f.bFront = d.p < 0.5
            f.mFront = !f.bFront
            return f
        }
        (f.p, f.pv) = p.eval(t)
        (f.b, f.bv) = b.eval(t)
        f.b2 = b2.eval(t).x
        (f.m, f.mv) = m.eval(t)
        f.m2 = m2.eval(t).x
        f.bFront = b.target(at: t) >= 0.5
        f.mFront = m.target(at: t) >= 0.5
        return f
    }
}

/// The autoPlay clock and the session a touch starts.
final class VegModeToggleDriver {
    typealias M = VegModeToggleMotion

    private var origin: Double?
    private(set) var live: VegModeToggleSim?
    private var lastTouch = -1e9
    private var returned = false
    var touching = false

    /// The scripted loop's springs at `now`, for the loop that started at `base`.
    static func scripted(base: Double, now: Double) -> VegModeToggleSim {
        let s = VegModeToggleSim()
        // Only events that have happened: a touch takes over this state as it is.
        if now >= base + M.tapAt { s.tap(at: base + M.tapAt) }
        if now >= base + M.dragFrom {
            s.beginDrag(at: base + M.dragFrom)
            s.dragTo(M.scriptP(min(now, base + M.dragTo) - base))
            if now >= base + M.dragTo {
                s.release(at: base + M.dragTo, velocity: 0)
            }
        }
        return s
    }

    func frame(at now: Double, auto: Bool, reduced: Bool) -> VegModeToggleFrame {
        if origin == nil { origin = now }
        if let live {
            live.reduced = reduced
            if !auto || touching || now < lastTouch + M.idleBeforeBack {
                return live.frame(at: now)
            }
            if !returned {
                returned = true
                let at = lastTouch + M.idleBeforeBack
                if live.drag != nil { live.release(at: at, velocity: 0, target: 0) }
                else if live.mode != 0 { live.flip(to: 0, at: at, delay: 0) }
            }
            if now < lastTouch + M.idleBeforeBack + M.settle {
                return live.frame(at: now)
            }
            self.live = nil
            origin = lastTouch + M.idleBeforeBack + M.settle
        }
        if !auto {
            let s = VegModeToggleSim()
            s.reduced = reduced
            live = s
            return s.frame(at: now)
        }
        let o = origin ?? now
        let base = o + ((now - o) / M.loop).rounded(.down) * M.loop
        return Self.scripted(base: base, now: now).frame(at: now)
    }

    /// The springs a touch acts on (the loop's current state when it was playing).
    func session(at now: Double, auto: Bool, reduced: Bool) -> VegModeToggleSim {
        if live == nil {
            if auto, let o = origin {
                let base = o + ((now - o) / M.loop).rounded(.down) * M.loop
                live = Self.scripted(base: base, now: now)
            } else {
                live = VegModeToggleSim()
            }
        }
        live!.reduced = reduced
        lastTouch = now
        returned = false
        return live!
    }

    func touched(at now: Double) {
        lastTouch = now
        returned = false
    }

    func reset() {
        origin = nil
        live = nil
        touching = false
        returned = false
        lastTouch = -1e9
    }
}

import Foundation

/*
 Omelette Topping Builder — the model, the seeded pieces and the autoPlay script.

 The screen is a pure function of a few events and the time: each topping is an add time (and, once
 lifted off, a remove time), the shelf is a chain of analytic springs. Every piece's flight, hop, spin
 and squash is computed from those times and its seeded layout, so the loop is exact and the three
 stacks match frame for frame.

 autoPlay: loop = 9 s
   0.0 – 1.2   rest: the plain omelette, Red Bell Pepper centred, $7.50
   1.2         add the pepper: it is tossed onto the omelette (lands at 1.82) and bursts into 22 cubes;
               the shelf slides to Baby Spinach at 2.1
   3.2         add the spinach (14 leaves), shelf → Smoked Bacon at 4.1
   5.2         add the bacon (7 strips), shelf → Cherry Tomato at 6.1
   7.4 – 7.64  the three toppings lift off and fade, last added first
   7.5         the shelf glides on two more items, back to the pepper
   8.6 – 9.0   rest (the frame at 9 is the frame at 0)
*/

struct OmeletteToppingBuilderSpring {
    var response: Double
    var damping: Double
}

/// One analytic spring segment.
struct OmeletteToppingBuilderSegment {
    var t0: Double
    var x0: Double
    var v0: Double
    var target: Double
    var spring: OmeletteToppingBuilderSpring

    func eval(_ t: Double) -> (x: Double, v: Double) {
        let tau = t - t0
        if tau <= 0 { return (x0, v0) }
        let z = min(spring.damping, 0.999)
        let w0 = 2 * Double.pi / spring.response
        let wd = w0 * (1 - z * z).squareRoot()
        let a = x0 - target
        let b = (v0 + z * w0 * a) / wd
        let e = exp(-z * w0 * tau)
        // Settled: land exactly on the target, so a rest frame is the same frame every loop.
        if e < 1e-4 { return (target, 0) }
        let c = cos(wd * tau)
        let s = sin(wd * tau)
        return (target + e * (a * c + b * s),
                e * ((b * wd - z * w0 * a) * c - (a * wd + z * w0 * b) * s))
    }
}

/// A value driven by a chain of spring segments; a segment may start in the future (a delay).
struct OmeletteToppingBuilderChannel {
    var segs: [OmeletteToppingBuilderSegment]

    init(_ x: Double) {
        segs = [.init(t0: -1e9, x0: x, v0: 0, target: x, spring: .init(response: 1, damping: 1))]
    }

    func eval(_ t: Double) -> (x: Double, v: Double) {
        var seg = segs[0]
        for s in segs where s.t0 <= t { seg = s }
        return seg.eval(t)
    }

    /// Starts a spring towards `target` at time t; segments planned after t are dropped.
    mutating func go(_ target: Double, at t: Double, _ spring: OmeletteToppingBuilderSpring) {
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

/// One topping on the omelette: when it was added, where its flight started, when it was lifted off.
struct OmeletteToppingBuilderTopping {
    var kind: Int
    var ta: Double
    /// The ingredient's x offset from the shelf's centre when it was tapped.
    var sx: Double
    var tr: Double?
}

/// One seeded piece, in the omelette's own coordinates (pt, fold edge on y = 0, bulge towards +y).
struct OmeletteToppingBuilderPiece {
    var x: Double
    var y: Double
    var rot: Double
    var size: Double
    var spin: Double
    var delay: Double
    var lift: Double
    var phase: Double
}

struct OmeletteToppingBuilderFrame {
    var t = 0.0
    var s = 0.0
    var toppings: [OmeletteToppingBuilderTopping] = []
    var shakeAt = -1e9
    var shakeKind = 0
    var reduced = false
}

enum OmeletteToppingBuilderMotion {
    static let loop = 9.0

    // The shelf ring.
    static let kinds = 5
    static let names = ["Red Bell Pepper", "Baby Spinach", "Smoked Bacon", "Cherry Tomato", "Button Mushroom"]
    static let cents = [80, 90, 160, 70, 90]
    static let baseCents = 750
    static let counts = [22, 14, 7, 12, 10]
    static let minGap = [17.0, 22.0, 30.0, 22.0, 24.0]
    /// Each kind's piece scale.
    static let pieceScale = [1.0, 1.2, 1.1, 1.0, 1.0]

    // The omelette: a half disc of radius R, its fold edge turned to -40°, its centroid on the plate's centre.
    static let R = 110.0
    static let angle = -40.0 * Double.pi / 180
    static let centroidY = 4 * R / (3 * Double.pi)

    // One add, from the tap.
    static let press = 0.08
    static let land = 0.62
    static let collapse = 0.14
    static let advanceAt = 0.9
    static let restockAt = 1.3
    static let restockDur = 0.35
    static let priceDelay = 0.1
    static let priceRoll = 0.35
    static let liftDur = 0.42
    static let liftStagger = 0.12
    static let shakeDur = 0.4

    static let shelfSpring = OmeletteToppingBuilderSpring(response: 0.55, damping: 0.8)
    static let rewindSpring = OmeletteToppingBuilderSpring(response: 0.8, damping: 0.88)
    static let snapSpring = OmeletteToppingBuilderSpring(response: 0.45, damping: 0.82)
    static let calm = OmeletteToppingBuilderSpring(response: 0.3, damping: 1)

    // The script.
    static let adds: [(at: Double, kind: Int)] = [(1.2, 0), (3.2, 1), (5.2, 2)]
    static let clearAt = 7.4
    static let clearStep = 0.12
    static let rewindAt = 7.5

    static let idleBeforeBack = 2.0
    static let settle = 1.6

    // MARK: Helpers

    static func clamp01(_ x: Double) -> Double { min(1, max(0, x)) }
    static func smooth(_ x: Double) -> Double { let c = clamp01(x); return c * c * (3 - 2 * c) }
    static func easeOut(_ x: Double) -> Double { let c = 1 - clamp01(x); return 1 - c * c * c }
    static func easeIn(_ x: Double) -> Double { let c = clamp01(x); return c * c * c }
    static func easeInOut(_ x: Double) -> Double {
        let c = clamp01(x)
        return c < 0.5 ? 4 * c * c * c : 1 - pow(-2 * c + 2, 3) / 2
    }
    static func easeOutBack(_ x: Double) -> Double {
        let c = clamp01(x), k = 1.70158
        return 1 + (k + 1) * pow(c - 1, 3) + k * pow(c - 1, 2)
    }

    /// The ring index of a shelf position.
    static func wrap(_ i: Int) -> Int { ((i % kinds) + kinds) % kinds }

    /// Signed distance of item i from the shelf position s, in items, within (-2.5, 2.5].
    static func slot(_ i: Int, _ s: Double) -> Double {
        var d = (Double(i) - s).truncatingRemainder(dividingBy: Double(kinds))
        if d > 2.5 { d -= Double(kinds) }
        if d <= -2.5 { d += Double(kinds) }
        return d
    }

    /// A 32-bit integer hash (lowbias32) in [0, 1): the same bits in Swift, Kotlin and JavaScript.
    static func hash(_ n: Int) -> Double {
        var x = UInt32(truncatingIfNeeded: n)
        x ^= x >> 16
        x = x &* 0x7FEB352D
        x ^= x >> 15
        x = x &* 0x846CA68B
        x ^= x >> 16
        return Double(x) / 4294967296.0
    }

    /// Where a kind's ingredient lands, in omelette coordinates.
    static func landing(_ k: Int) -> (x: Double, y: Double) {
        ((hash(90000 + k * 2) - 0.5) * 24, centroidY + (hash(90001 + k * 2) - 0.5) * 16)
    }

    /// The seeded spots of each kind's pieces on the omelette, spaced by a minimum gap.
    static let pieces: [[OmeletteToppingBuilderPiece]] = (0..<kinds).map(layout)

    static func layout(_ k: Int) -> [OmeletteToppingBuilderPiece] {
        var out: [OmeletteToppingBuilderPiece] = []
        var j = 0
        while out.count < counts[k] && j < 2000 {
            let h1 = hash(k * 10000 + j * 4)
            let h2 = hash(k * 10000 + j * 4 + 1)
            j += 1
            let th = Double.pi * (0.05 + 0.9 * h1)
            let rho = (R - 15) * (0.06 + 0.94 * h2).squareRoot()
            let x = cos(th) * rho
            let y = sin(th) * rho
            if y < 10 { continue }
            let gap = j > 1200 ? 0 : minGap[k]
            var ok = true
            for p in out where hypot(p.x - x, p.y - y) < gap { ok = false; break }
            if !ok { continue }
            let i = out.count
            let b = 50000 + k * 1000 + i * 8
            out.append(.init(x: x, y: y,
                             rot: hash(b) * 2 * Double.pi,
                             size: 0.85 + 0.3 * hash(b + 1),
                             spin: (hash(b + 2) - 0.5) * 5,
                             delay: 0.02 * hash(b + 3),
                             lift: liftStagger * hash(b + 4),
                             phase: hash(b + 5) * 2 * Double.pi))
        }
        return out
    }

    /// The ingredient's world position at time t of its flight (u = 0 at the shelf, 1 on the omelette).
    static func bezier(_ a: Double, _ c: Double, _ b: Double, _ u: Double) -> Double {
        (1 - u) * (1 - u) * a + 2 * (1 - u) * u * c + u * u * b
    }

    /// Total in cents at time t.
    static func total(_ f: OmeletteToppingBuilderFrame, at t: Double) -> Int {
        var c = baseCents
        for tp in f.toppings {
            let on = f.reduced ? tp.ta : tp.ta + land + priceDelay
            if t >= on && !(tp.tr.map { t >= $0 } ?? false) { c += cents[tp.kind] }
        }
        return c
    }

    /// The latest time at or before t when the total changed (or nil).
    static func lastChange(_ f: OmeletteToppingBuilderFrame, at t: Double) -> Double? {
        var last: Double?
        for tp in f.toppings {
            let on = f.reduced ? tp.ta : tp.ta + land + priceDelay
            if on <= t { last = max(last ?? on, on) }
            if let r = tp.tr, r <= t, r > on { last = max(last ?? r, r) }
        }
        return last
    }
}

/// The toppings and the shelf of one session.
final class OmeletteToppingBuilderSim {
    typealias M = OmeletteToppingBuilderMotion

    var shelf = OmeletteToppingBuilderChannel(0)
    var drag: Double?
    var toppings: [OmeletteToppingBuilderTopping] = []
    var shakeAt = -1e9
    var shakeKind = 0
    var reduced = false

    func shelfAt(_ t: Double) -> Double { drag ?? shelf.eval(t).x }

    func isOn(_ k: Int) -> Bool { toppings.contains { $0.kind == k && $0.tr == nil } }

    /// Adds kind k from the shelf's centre (offset sx); the shelf moves on to the next item.
    func add(_ k: Int, at t: Double, sx: Double = 0) {
        toppings.removeAll { $0.tr.map { t > $0 + M.liftDur + M.liftStagger + 0.1 } ?? false }
        toppings.append(.init(kind: k, ta: t, sx: sx))
        let s = shelfAt(t)
        if reduced {
            shelf.go(s.rounded() + 1, at: t + 0.4, M.calm)
        } else {
            shelf.go(s.rounded() + 1, at: t + M.advanceAt, M.shelfSpring)
        }
    }

    /// A tap on the centred ingredient: adds it, or shakes it when it is already on. True when added.
    @discardableResult
    func tapCentre(at t: Double) -> Bool {
        let s = shelfAt(t)
        let k = M.wrap(Int(s.rounded()))
        if isOn(k) {
            shakeAt = t
            shakeKind = k
            return false
        }
        add(k, at: t, sx: M.slot(k, s))
        return true
    }

    /// Centres the item `d` slots away from the current one.
    func select(_ d: Int, at t: Double) {
        let s = shelfAt(t)
        shelf.go(s.rounded() + Double(d), at: t, reduced ? M.calm : M.snapSpring)
    }

    /// Lifts the last landed topping off. True when there was one.
    @discardableResult
    func removeLast(at t: Double) -> Bool {
        guard let i = toppings.lastIndex(where: { $0.tr == nil && (reduced || t >= $0.ta + M.land) }) else { return false }
        toppings[i].tr = t
        return true
    }

    /// Lifts every topping off, last added first, `step` apart.
    func clearAll(at t: Double, step: Double) {
        var n = 0
        for i in toppings.indices.reversed() where toppings[i].tr == nil {
            toppings[i].tr = t + step * Double(n)
            n += 1
        }
    }

    /// Springs the shelf to the nearest place where the pepper is centred.
    func rewind(at t: Double, _ spring: OmeletteToppingBuilderSpring) {
        let s = shelfAt(t)
        let target = (s / Double(M.kinds)).rounded() * Double(M.kinds)
        shelf.go(target, at: t, reduced ? M.calm : spring)
    }

    func beginDrag(at t: Double) { drag = shelf.eval(t).x }

    func dragTo(_ s: Double) { drag = s }

    /// Ends a drag: snaps to the nearest item along the finger's speed (items per second).
    func release(at t: Double, velocity: Double) {
        let s = drag ?? shelf.eval(t).x
        drag = nil
        shelf.set(s, v: velocity, at: t)
        let aim = s + 0.18 * velocity
        let target = min(s.rounded() + 2, max(s.rounded() - 2, aim.rounded()))
        shelf.go(target, at: t, reduced ? M.calm : M.snapSpring)
    }

    func frame(at t: Double) -> OmeletteToppingBuilderFrame {
        OmeletteToppingBuilderFrame(t: t, s: shelfAt(t), toppings: toppings,
                                    shakeAt: shakeAt, shakeKind: shakeKind, reduced: reduced)
    }
}

/// The autoPlay clock and the session a touch starts.
final class OmeletteToppingBuilderDriver {
    typealias M = OmeletteToppingBuilderMotion

    private var origin: Double?
    private(set) var live: OmeletteToppingBuilderSim?
    private(set) var lastTouch = -1e9
    private var returned = false
    var touching = false

    /// The scripted loop at `now`, for the loop that started at `base`: only events that have happened.
    static func scripted(base: Double, now: Double) -> OmeletteToppingBuilderSim {
        let s = OmeletteToppingBuilderSim()
        for a in M.adds where now >= base + a.at {
            s.add(a.kind, at: base + a.at)
        }
        if now >= base + M.clearAt {
            s.clearAll(at: base + M.clearAt, step: M.clearStep)
        }
        if now >= base + M.rewindAt {
            s.rewind(at: base + M.rewindAt, M.rewindSpring)
        }
        return s
    }

    func reset() {
        origin = nil
        live = nil
        returned = false
        touching = false
        lastTouch = -1e9
    }

    func frame(at now: Double, auto: Bool, reduced: Bool) -> OmeletteToppingBuilderFrame {
        if origin == nil { origin = now }
        if let live {
            live.reduced = reduced
            if !auto || touching || now < lastTouch + M.idleBeforeBack {
                return live.frame(at: now)
            }
            if !returned {
                returned = true
                let at = lastTouch + M.idleBeforeBack
                if live.drag != nil { live.release(at: at, velocity: 0) }
                live.clearAll(at: at, step: 0.08)
                live.rewind(at: at, M.rewindSpring)
            }
            if now < lastTouch + M.idleBeforeBack + M.settle {
                return live.frame(at: now)
            }
            self.live = nil
            origin = lastTouch + M.idleBeforeBack + M.settle
        }
        if !auto {
            let s = OmeletteToppingBuilderSim()
            s.reduced = reduced
            live = s
            return s.frame(at: now)
        }
        let o = origin ?? now
        let base = o + ((now - o) / M.loop).rounded(.down) * M.loop
        return Self.scripted(base: base, now: now).frame(at: now)
    }

    /// The session a touch acts on (the loop's current state when it was playing).
    func session(at now: Double, auto: Bool, reduced: Bool) -> OmeletteToppingBuilderSim {
        if live == nil {
            if auto, let o = origin {
                let base = o + ((now - o) / M.loop).rounded(.down) * M.loop
                live = Self.scripted(base: base, now: now)
            } else {
                live = OmeletteToppingBuilderSim()
            }
        }
        live!.reduced = reduced
        returned = false
        return live!
    }

    func touched(at now: Double) {
        lastTouch = now
        returned = false
    }
}

import Foundation

/*
 Address Lookup Map — the loop as a pure function of time.

 `pose(t, s0)` gives every value the screen draws at loop time t (seconds, 0 ..< loop): the field,
 the suggestion rows, the camera over the drawn map, the pin, the filled fields and the ghost
 finger. A person drives the same values (AddressLookupMapModel), so the drawing code never knows
 which of the two is in charge.

 Timeline (s)            what happens
 0.00 – 0.90             rest: empty field, the town from above
 0.85                    ghost tap on the field, it takes the focus
 1.15 – 2.05             "48 Lark" typed, one key every 0.15 s; the suggestions rise in and re-rank
 2.85                    ghost tap on the first suggestion
 2.90 – 3.35             the row flies up into the field, the list folds away
 3.05 – 4.50             the map pans and zooms to the address (log-scale zoom, ease-in-out)
 4.40 – 5.00             the pin drops and bounces; two soft rings at 4.73 and 5.15
 4.95                    callout "Front door"
 5.00 – 5.58             unit, postcode, city fill in, 90 ms apart; the button wakes at 5.45
 5.95 – 7.25             ghost drag on the map: the map slides, the pin lifts and lands again
 7.30                    callout "Side gate · 9 m"
 8.40 – 9.40             everything empties, the map flies back out
 9.40 – 10.0             rest
*/
enum AddressLookupMapMotion {
    typealias D = AddressLookupMapData

    static let loop: Double = 10
    /// Seconds after the last touch before the screen eases back and the loop restarts.
    static let idle: Double = 2
    static let returnLen: Double = 0.8
    /// Loop time shown, without motion, under reduced motion.
    static let tStill: Double = 6.18

    static let query = "48 Lark"
    /// The suggestion the loop picks (index into addresses).
    static let pickIndex = 0

    // MARK: Timeline
    static let fieldTap = 0.85
    static let focusAt = 0.9
    static let typeAt = 1.15
    static let keyGap = 0.15
    static let pickAt = 2.85
    static let chooseAt = 2.9
    static let chooseLen = 0.45
    static let flyAt = 3.05
    static let flyLen = 1.45
    static let dropAt = 4.4
    static let dropLen = 0.6
    static let ringAt: [Double] = [4.73, 5.15]
    static let ringLen = 1.0
    static let callAAt = 4.95
    static let fillAt = 5.0
    static let fillGap = 0.09
    static let fillLen = 0.4
    static let readyAt = 5.45
    static let dragIn = 5.95
    static let dragAt = 6.2
    static let dragEnd = 7.0
    static let callBAt = 7.3
    static let resetAt = 8.4
    static let clearAt = 8.75
    static let backAt = 8.55
    static let backLen = 0.85

    /// Suggestion rows: height and how long a re-rank takes.
    static let rowH: Double = 58
    static let rowMove = 0.32
    static let rowStagger = 0.035

    /// The ghost finger's drag on the map, pt on screen.
    static let dragDX: Double = -46
    static let dragDY: Double = -26

    // MARK: Map camera
    /// The map is turned a little so the grid does not read as a spreadsheet.
    static let mapRot = -8 * Double.pi / 180
    static let overviewX: Double = 600
    static let overviewY: Double = 650
    /// Overview: this many world units across the map card.
    static let overviewSpan: Double = 1080
    /// Close-up scale (screen pt per world unit).
    static let near: Double = 1.05
    static let minScaleK = 0.85
    static let maxScale = 2.2

    // MARK: The script (field text, chosen address)
    struct Step {
        let query: String
        let selected: Int?
        let filled: Bool
    }

    static let script: [Step] = {
        var s: [Step] = [Step(query: "", selected: nil, filled: false)]
        for k in 1 ... query.count { s.append(Step(query: String(query.prefix(k)), selected: nil, filled: false)) }
        let pick = D.addresses[pickIndex].street
        s.append(Step(query: pick, selected: pickIndex, filled: false))
        s.append(Step(query: pick, selected: pickIndex, filled: true))
        s.append(Step(query: "", selected: nil, filled: false))
        return s
    }()

    static let times: [Double] = {
        var t: [Double] = [-1]
        for k in 1 ... query.count { t.append(typeAt + Double(k - 1) * keyGap) }
        t.append(chooseAt)
        t.append(fillAt - 0.02)
        t.append(clearAt)
        return t
    }()

    /// Script steps while the suggestions are open: 1 ... typed.
    static let typed = query.count

    private static let lists: [[Int]] = script.enumerated().map { k, s in
        (k == 0 || k == script.count - 1) ? [] : D.rank(k > typed ? query : s.query)
    }

    /// Rows per script step: slot (kept while hidden, or the slot it will appear in) and visibility.
    private static let rowTables: (y: [[Double]], a: [[Double]]) = {
        var yAll: [[Double]] = []
        var aAll: [[Double]] = []
        for i in 0 ..< D.addresses.count {
            var ys: [Int] = []
            var as_: [Double] = []
            for k in 0 ..< script.count {
                let at = lists[k].firstIndex(of: i) ?? -1
                as_.append(at >= 0 ? 1 : 0)
                ys.append(at)
            }
            let known = ys
            for k in 0 ..< ys.count where known[k] < 0 {
                var prev = -1
                var j = k - 1
                while j >= 0 { if known[j] >= 0 { prev = known[j]; break }; j -= 1 }
                var next = -1
                j = k + 1
                while j < ys.count { if known[j] >= 0 { next = known[j]; break }; j += 1 }
                ys[k] = prev >= 0 ? prev : (next >= 0 ? next : 0)
            }
            yAll.append(ys.map(Double.init))
            aAll.append(as_)
        }
        return (yAll, aAll)
    }()

    private static let counts: [Double] = lists.map { Double($0.count) }

    // MARK: Curves
    static func clamp01(_ x: Double) -> Double { x < 0 ? 0 : (x > 1 ? 1 : x) }
    static func seg(_ t: Double, _ a: Double, _ b: Double) -> Double { clamp01((t - a) / (b - a)) }
    static func easeOutCubic(_ x: Double) -> Double { let u = 1 - x; return 1 - u * u * u }
    static func easeInOutCubic(_ x: Double) -> Double { x < 0.5 ? 4 * x * x * x : 1 - pow(-2 * x + 2, 3) / 2 }
    static func easeOutBack(_ x: Double) -> Double { let c = 1.4, u = x - 1; return 1 + (c + 1) * u * u * u + c * u * u }

    /// Height of the falling pin above the ground, pt (negative = up), from drop progress 0 ... 1.
    static func dropY(_ p: Double) -> Double {
        if p <= 0 { return -84 }
        if p < 0.5 { let u = p / 0.5; return -84 * (1 - u * u) }
        if p >= 1 { return 0 }
        let u = (p - 0.5) / 0.5
        if u < 0.62 { return -15 * sin(Double.pi * u / 0.62) }
        return -5 * sin(Double.pi * (u - 0.62) / 0.38)
    }

    /// Squash of the pin's head as it touches down, 0 ... 1.
    static func dropSquash(_ p: Double) -> Double {
        let d1 = abs(p - 0.5) / 0.06
        let d2 = abs(p - 0.81) / 0.05
        return max(0, 1 - d1) + 0.35 * max(0, 1 - d2)
    }

    /// Superposes the script's per-step targets: each change eases in from its own start time.
    private static func superpose(_ values: [Double], _ t: Double, _ len: Double, _ stagger: Double) -> Double {
        var v = values[0]
        for j in 1 ..< values.count {
            let d = values[j] - values[j - 1]
            if d == 0 { continue }
            let at = times[j] + stagger
            v += d * easeOutCubic(seg(t, at, at + len))
        }
        return v
    }

    /// The filled fields empty in reverse order at the end of the loop.
    private static func fillOut(_ t: Double, _ i: Int) -> Double {
        let at = resetAt + Double(2 - i) * 0.05
        return 1 - seg(t, at, at + 0.25)
    }

    /// Index of the script step showing at loop time t.
    static func stepAt(_ t: Double) -> Int {
        var k = 0
        for j in 1 ..< times.count where t >= times[j] { k = j }
        return k
    }

    // MARK: The pose
    struct Pose {
        var focus = 0.0
        var caret = 0.0
        /// The field's value and placeholder opacities.
        var text = 1.0
        var placeholder = 1.0
        /// The suggestion card: open 0 ... 1, rows it holds (fractional while it resizes).
        var list = 0.0
        var count = 0.0
        /// 0 → 1 while the picked row flies into the field and the list folds.
        var choose = 0.0
        var chosen = 0
        var rowY: [Double] = []
        var rowA: [Double] = []
        var camX = 600.0
        var camY = 650.0
        var camS = 0.3
        /// Drop progress of the pin 0 ... 1 (see dropY), and its opacity.
        var drop = 0.0
        var pin = 0.0
        var ring1 = 0.0
        var ring2 = 0.0
        /// 0 → 1 while the map is held: the pin lifts off its spot.
        var lift = 0.0
        var callA = 0.0
        var callB = 0.0
        var fill: [Double] = [0, 0, 0]
        var ready = 0.0
    }

    struct Dot {
        var target = 0
        var alpha = 0.0
        var scale = 1.0
        var dx = 0.0
        var dy = 0.0
    }

    static func overviewScale(_ mapW: Double) -> Double { mapW / overviewSpan }

    /// World offset of the camera when the map is dragged by (dx, dy) pt at scale s.
    static func dragToWorld(_ dx: Double, _ dy: Double, _ s: Double) -> (Double, Double) {
        let c = cos(-mapRot), n = sin(-mapRot)
        return (-(dx * c - dy * n) / s, -(dx * n + dy * c) / s)
    }

    static func pose(_ t: Double, _ s0: Double) -> Pose {
        let target = D.addresses[pickIndex]
        let c = t < clearAt + 0.4 ? easeInOutCubic(seg(t, chooseAt, chooseAt + chooseLen)) : 0

        var p = Pose()
        let tables = rowTables
        p.rowY = []
        p.rowA = []
        for i in 0 ..< D.addresses.count {
            let y = superpose(tables.y[i], t, rowMove, 0)
            p.rowY.append(y)
            p.rowA.append(superpose(tables.a[i], t, rowMove * 0.8, rowStagger * y))
        }

        // Camera: fly in, follow the ghost drag, fly back out.
        let fly = easeInOutCubic(seg(t, flyAt, flyAt + flyLen))
        let back = easeInOutCubic(seg(t, backAt, backAt + backLen))
        let drag = easeInOutCubic(seg(t, dragAt, dragEnd))
        let off = dragToWorld(dragDX * drag, dragDY * drag, near)
        let k = fly * (1 - back)
        p.camX = overviewX + (target.x + off.0 - overviewX) * k
        p.camY = overviewY + (target.y + off.1 - overviewY) * k
        p.camS = s0 * pow(near / s0, k)

        let resetPin = seg(t, resetAt + 0.1, resetAt + 0.4)
        let typing = t - times[min(stepAt(t), typed)]
        let blink = typing < 0.5 ? 1 : clamp01(0.5 + 1.6 * cos(2 * Double.pi * (typing - 0.5) / 1.06))
        let textOut = (t >= resetAt + 0.05 && t < clearAt + 0.15) ? 1 - seg(t, resetAt + 0.05, resetAt + 0.3) : 1

        p.focus = easeOutCubic(seg(t, focusAt, focusAt + 0.2)) * (1 - seg(t, chooseAt + 0.1, chooseAt + 0.3))
        p.caret = seg(t, focusAt + 0.15, focusAt + 0.25) * (1 - seg(t, chooseAt, chooseAt + 0.05)) * blink
        p.text = ((c > 0 && t < clearAt) ? seg(c, 0.45, 0.95) : 1) * textOut
        p.placeholder = t < clearAt ? 1 : seg(t, clearAt + 0.1, clearAt + 0.35)
        p.list = t < clearAt ? easeOutCubic(seg(t, typeAt, typeAt + 0.3)) : 0
        p.count = superpose(counts, t, rowMove, 0)
        p.choose = c
        p.chosen = pickIndex
        p.drop = seg(t, dropAt, dropAt + dropLen)
        p.pin = seg(t, dropAt, dropAt + 0.08) * (1 - resetPin)
        p.ring1 = seg(t, ringAt[0], ringAt[0] + ringLen)
        p.ring2 = seg(t, ringAt[1], ringAt[1] + ringLen)
        p.lift = easeOutCubic(seg(t, dragAt, dragAt + 0.18)) * (1 - easeOutBack(seg(t, dragEnd, dragEnd + 0.35)))
        p.callA = easeOutCubic(seg(t, callAAt, callAAt + 0.3)) * (1 - seg(t, dragAt, dragAt + 0.18))
        p.callB = easeOutCubic(seg(t, callBAt, callBAt + 0.3)) * (1 - seg(t, resetAt, resetAt + 0.2))
        p.fill = (0 ..< 3).map { i in
            let a = fillAt + fillGap * Double(i)
            return easeOutCubic(seg(t, a, a + fillLen)) * fillOut(t, i)
        }
        p.ready = easeInOutCubic(seg(t, readyAt, readyAt + 0.4)) * (1 - seg(t, resetAt, resetAt + 0.35))
        return p
    }

    /// The ghost finger: taps the field, taps the first suggestion, then drags the map.
    static func dot(_ t: Double) -> Dot {
        var d = Dot()
        let taps = [fieldTap, pickAt]
        for (k, at) in taps.enumerated() {
            let a = seg(t, at - 0.24, at - 0.06) * (1 - seg(t, at + 0.12, at + 0.36))
            if a > d.alpha {
                d.alpha = a
                d.target = k
                d.scale = 1 - 0.22 * sin(Double.pi * seg(t, at - 0.06, at + 0.16))
            }
        }
        let a = seg(t, dragIn, dragIn + 0.2) * (1 - seg(t, dragEnd + 0.05, dragEnd + 0.3))
        if a > d.alpha {
            d.alpha = a
            d.target = 2
            let p = easeInOutCubic(seg(t, dragAt, dragEnd))
            d.dx = dragDX * p
            d.dy = dragDY * p
            d.scale = 1 - 0.18 * seg(t, dragAt - 0.12, dragAt) * (1 - seg(t, dragEnd, dragEnd + 0.12))
        }
        return d
    }
}

/// A number a person drives: set at once, eased over a duration (after a delay), sprung, or
/// thrown with a decay. Sampled against the TimelineView clock, so nothing writes state per frame.
struct AddressLookupMapValue {
    enum Kind {
        case timing((Double) -> Double)
        case spring(omega: Double, zeta: Double, v0: Double)
        case decay(v0: Double, lo: Double, hi: Double)
    }

    private var from: Double
    private(set) var to: Double
    private var start = 0.0
    private var duration = 0.0
    private var kind: Kind = .timing({ $0 })

    init(_ v: Double) {
        from = v
        to = v
    }

    mutating func set(_ v: Double) {
        from = v
        to = v
        duration = 0
    }

    func value(_ now: Double) -> Double {
        if duration <= 0 { return to }
        let e = now - start
        if e <= 0 { return from }
        switch kind {
        case let .timing(curve):
            if e >= duration { return to }
            return from + (to - from) * curve(e / duration)
        case let .spring(w, z, v0):
            if e >= duration { return to }
            let x0 = from - to
            if z < 1 {
                let wd = w * sqrt(1 - z * z)
                return to + exp(-z * w * e) * (x0 * cos(wd * e) + (v0 + z * w * x0) / wd * sin(wd * e))
            }
            return to + (x0 + (v0 + w * x0) * e) * exp(-w * e)
        case let .decay(v0, lo, hi):
            let k = 2.0
            let x = from + v0 / k * (1 - exp(-k * min(e, duration)))
            return min(max(x, lo), hi)
        }
    }

    func velocity(_ now: Double) -> Double {
        (value(now + 0.004) - value(now)) / 0.004
    }

    func isMoving(_ now: Double) -> Bool {
        duration > 0 && now < start + duration
    }

    mutating func timing(_ target: Double, _ dur: Double, now: Double, delay: Double = 0, curve: @escaping (Double) -> Double = { $0 }) {
        let current = value(now)
        if dur <= 0 && delay <= 0 { set(target); return }
        from = current
        to = target
        start = now + delay
        duration = max(dur, 0.0001)
        kind = .timing(curve)
    }

    /// A spring that settles in about `dur` seconds with the given damping ratio.
    mutating func spring(_ target: Double, _ dur: Double, damping: Double, now: Double) {
        if dur <= 0 { set(target); return }
        let v0 = velocity(now)
        from = value(now)
        to = target
        start = now
        duration = dur * 2.2
        kind = .spring(omega: 2 * Double.pi / dur, zeta: damping, v0: v0)
    }

    mutating func decay(velocity v0: Double, lo: Double, hi: Double, now: Double) {
        let x = value(now)
        if x < lo || x > hi {
            timing(min(max(x, lo), hi), 0.45, now: now, curve: AddressLookupMapMotion.easeOutCubic)
            return
        }
        from = x
        to = min(max(x + v0 / 2, lo), hi)
        start = now
        duration = 2.5
        kind = .decay(v0: v0, lo: lo, hi: hi)
    }
}

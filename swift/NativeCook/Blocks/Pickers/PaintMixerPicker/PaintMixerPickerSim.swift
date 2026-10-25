import Foundation

/*
 The bowl under the finger: the same paint model as the script, stepped once a frame. Drops fly
 from the pot (or straight down from the finger) and land; each landing kicks the swirl, whose
 angular speed decays (τ 1.1 s); stirring pulls the swirl towards the finger's angular speed.
 Winding (the total turned, either way) is what mixes the drops. 2 s after the last touch the
 bowl is washed back to white in 0.85 s and the script restarts from rest. A port of
 paintMixerSim.ts.
 */
final class PaintMixerPickerSim {
    private typealias M = PaintMixerPickerMotion
    static let maxDrops = 4
    static let swirlDecay = 1.1
    static let landKick = 2.4
    static let washTime = 0.85
    static let eventLand = 1
    static let eventGrab = 2

    struct Drop {
        var x: Double
        var y: Double
        var amt: Double
        var a: PaintMixerPickerRGB
        var windLand: Double
        var twistLand: Double
    }

    struct Flight {
        var fx: Double
        var fy: Double
        var tx: Double
        var ty: Double
        var t0: Double
        var dur: Double
        var pot: Int
        var into: Bool
    }

    struct Held {
        var x: Double
        var y: Double
        var pot: Int
    }

    var baseA: PaintMixerPickerRGB = PaintMixerPickerMotion.baseA
    var baseW = 1.0
    var drops: [Drop] = []
    var wind = 0.0
    var twist = 0.0
    var omega = 0.0
    var stirW = 0.0
    var stirring = false
    var ripples: [(x: Double, y: Double, t0: Double, str: Double)] = []
    var splashes: [(x: Double, y: Double, t0: Double, pot: Int)] = []
    var flights: [Flight] = []
    var held: Held?
    var lift: [Double] = [0, 0, 0, 0, 0]
    /// Wash-back start time (nil: none).
    var wash: Double?
    /// Amount scale left by a hand-over during the script's own wash.
    var fade = 1.0
    var events = 0
    var reduced: Bool

    init(reduced: Bool) {
        self.reduced = reduced
    }

    /// Takes the script's bowl at loop time t over (the same frame, now driven by the sim).
    func fromAuto(_ g: PaintMixerPickerGeo, t: Double, now: Double) {
        let m = M.autoMix(g, t)
        let w = M.windingAt(t)
        wind = w
        twist = t > 6.6 ? w * (1 - M.easeInOutSine(M.seg(t, 6.6, 8.4))) : w
        let dt = 0.02
        omega = t < 6.6 ? (M.windingAt(t + dt) - w) / dt : 0
        drops = []
        flights = []
        held = nil
        stirring = false
        for (pot, at) in [(M.yellow, M.landY), (M.teal, M.landT)] {
            if t >= at {
                let (x, y) = M.landing(g, pot)
                let d = m.drops[drops.count]
                drops.append(Drop(x: x, y: y, amt: d.amt, a: M.potA[pot], windLand: M.windingAt(at), twistLand: twist - d.tw))
            } else if t >= at - 0.5 {
                let (px, py) = M.potCenter(g, pot)
                let (lx, ly) = M.landing(g, pot)
                flights.append(Flight(fx: px, fy: py, tx: lx, ty: ly, t0: now - (t - (at - 0.5)), dur: 0.5, pot: pot, into: true))
            }
        }
        ripples = m.ripples.map { (x: $0.x, y: $0.y, t0: now - $0.age, str: $0.str) }
        splashes = m.splashes.map { (x: $0.x, y: $0.y, t0: now - $0.age, pot: $0.pot) }
        lift = m.lift
        fade = m.fade
        wash = nil
    }

    private func addDrop(_ x: Double, _ y: Double, _ pot: Int, _ now: Double) {
        if drops.count >= Self.maxDrops {
            // The oldest drop is folded into the base paint.
            let o = drops.removeFirst()
            let wgt = M.k * o.amt * fade
            baseA = (
                (baseA.0 * baseW + o.a.0 * wgt) / (baseW + wgt),
                (baseA.1 * baseW + o.a.1 * wgt) / (baseW + wgt),
                (baseA.2 * baseW + o.a.2 * wgt) / (baseW + wgt)
            )
            baseW += wgt
        }
        drops.append(Drop(x: x, y: y, amt: reduced ? 0.001 : 0, a: M.potA[pot], windLand: wind, twistLand: twist))
        if !reduced {
            omega += Self.landKick
            ripples.append((x: x, y: y, t0: now, str: 1))
            if ripples.count > 3 { ripples.removeFirst() }
            splashes.append((x: x, y: y, t0: now, pot: pot))
        }
        events |= Self.eventLand
    }

    func step(now: Double, dt: Double) {
        // Flights land.
        var i = flights.count - 1
        while i >= 0 {
            let f = flights[i]
            if now >= f.t0 + f.dur {
                flights.remove(at: i)
                if f.into { addDrop(f.tx, f.ty, f.pot, now) }
            }
            i -= 1
        }
        // Drops pour in; the swirl decays or follows the stirring finger.
        let pour = 1 - exp(-dt / (reduced ? 0.25 : 0.13))
        for j in drops.indices { drops[j].amt += (1 - drops[j].amt) * pour }
        if stirring && !reduced {
            omega += (stirW - omega) * min(1, dt * 8)
        } else {
            omega *= exp(-dt / Self.swirlDecay)
        }
        if reduced { omega = 0 }
        twist += omega * dt
        wind += abs(omega) * dt
        ripples.removeAll { now - $0.t0 >= 1.6 }
        splashes.removeAll { now - $0.t0 >= 0.45 }
        let liftK = 1 - exp(-dt / 0.08)
        for j in 0..<5 {
            let target: Double = held?.pot == j ? 1 : 0
            lift[j] += (target - lift[j]) * liftK
        }
        // Wash back to white, then empty.
        if let w = wash, now - w >= Self.washTime {
            drops = []
            baseA = M.baseA
            baseW = 1
            wind = 0
            twist = 0
            omega = 0
            fade = 1
            wash = nil
        }
    }

    /// Whether anything is still moving (otherwise the frame can be skipped).
    var busy: Bool {
        if !flights.isEmpty || !ripples.isEmpty || !splashes.isEmpty || held != nil || wash != nil { return true }
        if abs(omega) > 0.01 { return true }
        if drops.contains(where: { $0.amt < 0.995 }) { return true }
        if lift.contains(where: { $0 > 0.005 }) { return true }
        return false
    }

    func mix(_ g: PaintMixerPickerGeo, now: Double) -> PaintMixerPickerMix {
        var front = -1.0
        var f = fade
        var back = 0.0
        if let w = wash {
            let p = M.seg(now - w, 0, Self.washTime)
            front = (g.paint + 46 * g.u) * M.easeInOutSine(p)
            f *= 1 - M.easeInOutCubic(p)
            back = -2.4 * Double.pi * M.easeInOutSine(p)
        }
        var m = PaintMixerPickerMix()
        m.baseA = baseA
        m.baseW = baseW
        m.drops = drops.map {
            .init(x: $0.x, y: $0.y, amt: $0.amt, m: reduced ? 1 : M.mixedShare(wind - $0.windLand), tw: twist - $0.twistLand, a: $0.a)
        }
        m.flights = flights.map { fl in
            let s = M.seg(now, fl.t0, fl.t0 + fl.dur)
            let e = fl.into ? M.easeInOutSine(s) : 1 - (1 - s) * (1 - s) * (1 - s)
            return .init(
                x: fl.fx + (fl.tx - fl.fx) * e,
                y: fl.fy + (fl.ty - fl.fy) * e,
                h: fl.dur > 0.4 ? sin(Double.pi * s) : 1 - s,
                pot: fl.pot
            )
        }
        if let h = held { m.flights.append(.init(x: h.x, y: h.y, h: 1, pot: h.pot)) }
        m.ripples = ripples.map { .init(x: $0.x, y: $0.y, age: now - $0.t0, str: $0.str) }
        m.splashes = splashes.map { .init(x: $0.x, y: $0.y, age: now - $0.t0, pot: $0.pot) }
        m.wash = (front, back)
        m.fade = f
        m.stick = (0, 0, 0)
        m.lift = lift
        return m
    }

    /// A finger down: on a pot it picks up a drop, in the bowl it stirs.
    func down(_ g: PaintMixerPickerGeo, _ x: Double, _ y: Double) {
        for i in 0..<5 {
            let (px, py) = M.potCenter(g, i)
            if hypot(x - px, y - py) < g.potR + 16 {
                held = Held(x: x, y: y - 26 * g.u, pot: i)
                events |= Self.eventGrab
                return
            }
        }
        if hypot(x - g.cx, y - g.cy) < g.rim {
            stirring = true
            stirW = 0
        }
    }

    /// A finger moving: carries the drop, or stirs at the finger's angular speed (rad/s).
    func move(_ g: PaintMixerPickerGeo, _ x: Double, _ y: Double, _ vx: Double, _ vy: Double) {
        if held != nil {
            // The drop hangs a little above the fingertip so it stays visible.
            held?.x = x
            held?.y = y - 26 * g.u
            return
        }
        if stirring {
            let dx = x - g.cx
            let dy = y - g.cy
            let r2 = max(dx * dx + dy * dy, (g.paint * 0.25) * (g.paint * 0.25))
            stirW = max(-14, min(14, (dx * vy - dy * vx) / r2))
        }
    }

    /// A finger up: a drop over the paint falls in; anywhere else it flies back to its pot.
    func up(_ g: PaintMixerPickerGeo, now: Double) {
        stirring = false
        stirW = 0
        guard let h = held else { return }
        held = nil
        let r = hypot(h.x - g.cx, h.y - g.cy)
        if r < g.paint {
            flights.append(Flight(fx: h.x, fy: h.y, tx: h.x, ty: h.y, t0: now, dur: reduced ? 0 : 0.22, pot: h.pot, into: true))
        } else {
            let (px, py) = M.potCenter(g, h.pot)
            flights.append(Flight(fx: h.x, fy: h.y, tx: px, ty: py, t0: now, dur: reduced ? 0 : 0.3, pot: h.pot, into: false))
        }
    }

    func startWash(now: Double) {
        if drops.isEmpty && baseW == 1 { return }
        wash = now
    }
}

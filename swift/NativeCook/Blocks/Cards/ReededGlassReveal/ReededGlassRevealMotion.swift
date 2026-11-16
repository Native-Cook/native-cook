import Foundation

/// rgr-motion.ts, including the analytic demo and the live 240 Hz substep solver.
enum ReededGlassRevealMotion {
    static let loop = 8.0
    static let ribs = 22
    static func clamp(_ x: Double) -> Double { min(1, max(0, x)) }
    static func smooth(_ a: Double, _ b: Double, _ x: Double) -> Double {
        let s = clamp((x - a) / (b - a)); return s * s * (3 - 2 * s)
    }
    static func wrap(_ t: Double) -> Double {
        let r = t.truncatingRemainder(dividingBy: loop); return r < 0 ? r + loop : r
    }
    static func bezier(_ x1: Double, _ y1: Double, _ x2: Double, _ y2: Double, _ x: Double) -> Double {
        if x <= 0 { return 0 }; if x >= 1 { return 1 }
        var s = x
        for _ in 0..<8 {
            let cx = 3 * x1 * s * (1 - s) * (1 - s) + 3 * x2 * s * s * (1 - s) + s * s * s - x
            let dx = 3 * x1 * (1 - s) * (1 - 3 * s) + 3 * x2 * s * (2 - 3 * s) + 3 * s * s
            if abs(cx) < 1e-6 || dx == 0 { break }
            s = clamp(s - cx / dx)
        }
        return 3 * y1 * s * (1 - s) * (1 - s) + 3 * y2 * s * s * (1 - s) + s * s * s
    }
    static func sweepEase(_ x: Double) -> Double { bezier(0.35, 0.15, 0.65, 0.85, x) }
    static func easeOut(_ x: Double) -> Double { bezier(0.2, 0.8, 0.2, 1, x) }
    static func parkEase(_ x: Double) -> Double { bezier(0.3, 0.1, 0.25, 1, x) }
    static func throughEase(_ x: Double) -> Double { bezier(0.45, 0, 0.4, 1, x) }
    static func springAt(_ t: Double, _ response: Double = 0.45, _ zeta: Double = 0.68) -> Double {
        if t <= 0 { return 0 }
        let w = 2 * Double.pi / response
        if zeta >= 1 { return 1 - exp(-w * t) * (1 + w * t) }
        let wd = w * sqrt(1 - zeta * zeta)
        return 1 - exp(-zeta * w * t) * (cos(wd * t) + zeta * w / wd * sin(wd * t))
    }
    static func frontAt(_ t: Double) -> Double {
        if t < 0.8 { return 0 }
        if t < 0.8 + 0.8 { return 0.5 * parkEase((t - 0.8) / 0.8) }
        if t < 2.6 { return 0.5 }
        if t < 2.6 + 0.8 { return 0.5 + 0.5 * throughEase((t - 2.6) / 0.8) }
        if t < 4.84 { return 1 }
        if t < 4.84 + 1.6 { return 1 - sweepEase((t - 4.84) / 1.6) }
        return 0
    }
    static func pressAt(_ t: Double) -> Double {
        func one(_ start: Double) -> Double {
            let a = t - start
            if a <= 0 || a >= 0.06 + 0.1 { return 1 }
            if a < 0.06 { return 1 - 0.06 * easeOut(a / 0.06) }
            return 0.94 + 0.06 * easeOut((a - 0.06) / 0.1)
        }
        return one(0.7) * one(4.76)
    }
    static func glyphAt(_ t: Double) -> Double {
        let h = 1.0 / 120, from = 0.8 - 0.05
        let steps = Int(floor((min(t, 7.2) - from) / h))
        var g = 0.0, v = 0.0
        if steps > 0 {
            for i in 0..<steps {
                let target = 180 * frontAt(from + Double(i) * h)
                v += (195 * (target - g) - 19 * v) * h; g += v * h
            }
        }
        return g
    }
    static func ribAt(_ edges: [Double], _ x: Double) -> Int {
        var idx = 0
        for k in 1..<ribs where x >= edges[k] { idx = k }
        return idx
    }
    static func depthAt(_ depths: [Double], _ x: Double, _ edges: [Double]) -> Double { depths[ribAt(edges, x)] }
    private static func inverse(_ ease: (Double) -> Double, _ y: Double) -> Double {
        var lo = 0.0, hi = 1.0
        for _ in 0..<40 {
            let mid = (lo + hi) / 2
            if ease(mid) < y { lo = mid } else { hi = mid }
        }
        return (lo + hi) / 2
    }
    struct Geo {
        var width: Double, ribW: Double, front0: Double, front1: Double
        var edges: [Double], centres: [Double], tA: [Double], tB: [Double]
        var xPark: Double
        init(width: Double) {
            self.width = width; ribW = width / 22
            front0 = -1.2 * ribW; front1 = width + 1.2 * ribW
            let cut = ReededGlassRevealLayout.ribEdges(width)
            edges = cut
            centres = (0..<22).map { (cut[$0] + cut[$0 + 1]) / 2 }
            tA = []; tB = []; xPark = front0 + 0.5 * (front1 - front0)
            for centre in centres {
                let p = (centre - front0) / (front1 - front0)
                tA.append(p <= 0.5 ? 0.8 + 0.8 * inverse(parkEase, p / 0.5) : 2.6 + 0.8 * inverse(throughEase, (p - 0.5) / 0.5))
                tB.append(4.84 + 1.6 * inverse(sweepEase, 1 - p))
            }
        }
    }
    struct Look {
        var depths = [Double](repeating: 1, count: 22)
        var glyph = 0.0, press = 1.0, front = 0.0, mean = 1.0, lag = 0.0
        var run = -1.0, light = 0.0, breath = 0.0, peek = 0.0
    }
    static func timeline(_ input: Double, _ g: Geo) -> Look {
        let t = wrap(input)
        var result = Look()
        let r = (t - 7 + loop).truncatingRemainder(dividingBy: loop) / 1.4
        result.run = r < 1 ? r : -1
        result.light = 0.5 * sin(2 * .pi * t / loop)
        result.breath = 0.2 * sin(8 * .pi * t / loop)
        let moving = t > 0 && t < 7.2
        let park = smooth(1.6, 1.6 + 0.3, t) * (1 - smooth(2.6 - 0.3, 2.6, t))
        let wob = 0.35 * park * sin(2 * .pi * 0.8 * (t - 1.6))
        for i in 0..<ribs {
            var d = 1.0
            if moving {
                let ta = t - g.tA[i], tb = t - g.tB[i]
                let swell = 1 + 0.25 * smooth(-0.12, 0, ta) * exp(-max(ta, 0) / 0.12)
                d = (1 - springAt(ta)) * swell + springAt(tb)
                if park > 0 {
                    let q = (g.centres[i] - g.xPark - 0.3 * g.ribW) / (0.8 * g.ribW)
                    d += wob * exp(-q * q)
                }
            }
            result.depths[i] = d
        }
        result.glyph = moving ? glyphAt(t) : 0
        result.press = moving ? pressAt(t) : 1
        result.front = moving ? g.front0 + (g.front1 - g.front0) * frontAt(t) : g.front0
        result.mean = result.depths.reduce(0, +) / Double(ribs)
        result.lag = moving ? g.front0 + (g.front1 - g.front0) * frontAt(t - 0.06) : g.front0
        return result
    }
    enum Mode { case drag, spring, sweep, hold }
    struct Live {
        var fx: Double, fv = 0.0, mode = Mode.spring, target: Double
        var sweepFrom: Double, sweepTo: Double, sweepT = 0.0, sweepDur = 0.0
        var depth = [Double](repeating: 1, count: 22), vel = [Double](repeating: 0, count: 22)
        var glyph = 0.0, glyphV = 0.0, glyphTarget = 0.0
        var touching = false, touchX = 0.0, revealed = false, lastRib = -1, lastTick = -1.0
        var fade = 1.0, press = 1.0, flip = false, dragged = false, holdT = 0.0
        var peek = 0.0, peekRel = -1.0, peekX = 0.0, speedK = 0.0, fl: Double, light = 0.0
        init(_ g: Geo) { fx = g.front0; target = g.front0; sweepFrom = g.front0; sweepTo = g.front0; fl = g.front0 }
    }
    static func fromTimeline(_ tIn: Double, _ g: Geo) -> Live {
        let t = wrap(tIn), a = timeline(t, g), b = timeline(t + 1 / 120, g)
        var m = Live(g)
        let p = (a.front - g.front0) / (g.front1 - g.front0)
        m.fx = a.front; m.fv = (b.front - a.front) * 120
        m.target = p >= 0.5 ? g.front1 : g.front0
        m.sweepFrom = a.front; m.sweepTo = a.front
        m.depth = a.depths; m.vel = (0..<ribs).map { (b.depths[$0] - a.depths[$0]) * 120 }
        m.glyph = a.glyph; m.glyphV = (b.glyph - a.glyph) * 120; m.glyphTarget = 180 * clamp(p)
        m.revealed = t >= 3.46 && t < 6.5; m.press = a.press; m.fl = a.lag; m.light = a.light
        return m
    }
    static func startSweep(_ m: inout Live, _ g: Geo, _ to: Double) {
        m.mode = .sweep; m.sweepFrom = m.fx; m.sweepTo = to; m.sweepT = 0
        m.sweepDur = max(0.35, 1.6 * abs(to - m.fx) / (g.front1 - g.front0)); m.target = to
    }
    static func drag(_ m: inout Live, _ g: Geo, _ frontStart: Double, _ translation: Double, _ velocity: Double) {
        let sign = m.flip ? -1.0 : 1.0, raw = frontStart + sign * translation
        let over = raw < g.front0 ? raw - g.front0 : raw > g.front1 ? raw - g.front1 : 0
        let front = over == 0 ? raw : (raw < g.front0 ? g.front0 : g.front1) + over / (1 + abs(over) / 120)
        m.mode = .drag; m.fx = front; m.target = front; m.fv = sign * velocity
    }
    static func finish(_ m: inout Live, _ g: Geo, _ dragged: Bool, _ velocity: Double, _ reduce: Bool, _ success: Bool) {
        m.touching = false
        if m.peek > 0.05 { m.peekRel = 0; m.mode = .spring; return }
        if reduce && success { m.target = m.target >= g.front1 - 1 ? g.front0 : g.front1 }
        else if !reduce && (m.mode == .drag || dragged) {
            let v = m.flip ? -velocity : velocity
            let p = (m.fx + v * 0.12 - g.front0) / (g.front1 - g.front0)
            m.target = p >= 0.5 ? g.front1 : g.front0; m.fv = v
        }
        m.mode = .spring
    }
    struct Events { var tick = false, edge = false, revealed = false, masked = false }
    static func step(_ m: inout Live, _ dt: Double, _ g: Geo, _ reduce: Bool, _ now: Double) -> Events {
        var ev = Events()
        let steps = max(1, Int(ceil(dt / (1 / 240)))), h = dt / Double(steps)
        if reduce {
            let want = m.target >= g.front1 - 1 ? 0.0 : 1.0, dir = want < m.fade ? -1.0 : 1.0
            m.fade = clamp(m.fade + dir * dt / 0.3)
            if (dir < 0 && m.fade < want) || (dir > 0 && m.fade > want) { m.fade = want }
            m.fx = m.target; m.fv = 0; m.mode = .spring
            for i in 0..<ribs { m.depth[i] = m.fade; m.vel[i] = 0 }
        } else {
            if m.touching && !m.dragged && m.mode == .hold {
                m.holdT += dt
                if m.holdT >= 0.18 { m.peek = clamp(m.peek + dt / 0.12) }
            } else if m.touching && m.peek > 0 { m.peek = clamp(m.peek + dt / 0.12) }
            else if m.peekRel >= 0 {
                m.peekRel += dt
                if m.peekRel > 0.03 * Double(ribs) + 0.1 { m.peekRel = -1; m.peek = 0 }
            } else if !m.touching { m.peek = 0 }
            if !m.touching { m.holdT = 0 }
            m.speedK = max(m.speedK * exp(-dt / 0.35), clamp(abs(m.fv) / 1800))
            let damping = 19 + (11 - 19) * m.speedK, soft = 1.2 * g.ribW
            let mirror = g.front0 + g.front1, iTouch = ribAt(g.edges, m.touchX)
            for _ in 0..<steps {
                if m.mode == .sweep {
                    m.sweepT += h
                    let p = clamp(m.sweepT / m.sweepDur)
                    let nx = m.sweepFrom + (m.sweepTo - m.sweepFrom) * sweepEase(p)
                    m.fv = h > 0 ? (nx - m.fx) / h : 0; m.fx = nx
                    if p >= 1 { m.mode = .spring; m.fv = 0 }
                } else if m.mode == .spring {
                    let a = 157.9 * (m.target - m.fx) - 22.1 * m.fv
                    m.fv += a * h; m.fx += m.fv * h
                }
                for i in 0..<ribs {
                    let c = m.flip ? mirror - g.centres[i] : g.centres[i]
                    var target = 1 - smooth(c - soft, c + soft, m.fx)
                    if m.mode != .hold || m.dragged {
                        let lead = (c - m.fx - 0.9 * g.ribW) / (0.7 * g.ribW)
                        if c > m.fx && target > 0.5 { target += 0.25 * exp(-lead * lead) * clamp(abs(m.fv) / 120) }
                    }
                    if m.touching {
                        let q = (g.centres[i] - m.touchX) / (1.2 * g.ribW)
                        target *= 1 - 0.2 * exp(-q * q)
                    }
                    var open = 0.0
                    if m.touching { open = m.peek }
                    else if m.peekRel >= 0 && m.peekRel < 0.03 * Double(abs(i - iTouch)) { open = 1 }
                    if open > 0 {
                        let q = (g.centres[i] - m.touchX) / (1.4 * g.ribW)
                        target = min(target, 1 - 0.88 * open * exp(-q * q))
                    }
                    let a = 195 * (target - m.depth[i]) - damping * m.vel[i]
                    m.vel[i] += a * h; m.depth[i] += m.vel[i] * h
                }
            }
        }
        m.press += (1 - m.press) * min(1, dt / 0.08)
        m.glyphTarget = 180 * clamp((m.fx - g.front0) / (g.front1 - g.front0))
        let a = 195 * (m.glyphTarget - m.glyph) - 19 * m.glyphV
        m.glyphV += a * dt; m.glyph += m.glyphV * dt
        if reduce { m.glyph = 0; m.glyphV = 0 }
        m.fl += (m.fx - m.fl) * (1 - exp(-dt / 0.06))
        let wantLight = m.touching && m.touchX > -1e8 ? max(-1, min(1, (m.touchX / g.width - 0.5) * 1.6)) : 0
        m.light += (wantLight - m.light) * (1 - exp(-dt / 0.25))
        if m.mode == .drag {
            let rib = ribAt(g.edges, m.fx)
            if m.lastRib >= 0 && rib != m.lastRib && now - m.lastTick >= 0.0625 {
                ev.tick = true; ev.edge = rib == 0 || rib == ribs - 1; m.lastTick = now
            }
            m.lastRib = rib
        } else { m.lastRib = -1 }
        if m.mode != .drag && m.mode != .sweep && !m.touching && m.peek < 0.02 {
            let atEnd = abs(m.fx - m.target) < 1 && abs(m.fv) < 8
            let clear = m.depth.allSatisfy { $0 <= 0.02 }, masked = m.depth.allSatisfy { $0 >= 0.98 }
            if atEnd && clear && m.target >= g.front1 - 1 && !m.revealed { m.revealed = true; ev.revealed = true }
            else if atEnd && masked && m.target <= g.front0 + 1 && m.revealed { m.revealed = false; m.flip = false; ev.masked = true }
            else if atEnd && masked && m.target <= g.front0 + 1 { m.flip = false }
        }
        return ev
    }
    static func liveLook(_ m: Live) -> Look {
        Look(depths: m.depth, glyph: m.glyph, press: m.press, front: m.fx,
             mean: m.depth.reduce(0, +) / Double(ribs), lag: m.fl, run: -1, light: m.light, breath: 0, peek: m.peek)
    }
    static func atRest(_ m: Live, _ g: Geo) -> Bool {
        if abs(m.fx - g.front0) > 1 || abs(m.fv) > 8 || abs(m.glyph) > 0.5 || abs(m.glyphV) > 2 { return false }
        if m.peek > 0.01 || m.peekRel >= 0 || abs(m.light) > 0.02 { return false }
        for i in 0..<ribs { if abs(m.depth[i] - 1) > 0.004 * 5 || abs(m.vel[i]) > 0.05 { return false } }
        return true
    }
}

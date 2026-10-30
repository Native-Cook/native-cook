import Foundation

// Analytic script and the three-substep elastic network from cwo-motion.ts.
enum CareerWebOnboardingMotion {
    typealias Theme = CareerWebOnboardingTheme
    static let LOOP = 9.0, RESUME = 2.0
    enum T {
        static let markOut = [1.0, 1.3], markIn = [1.3, 1.85]
        static let arrowOut = [1.7, 2.0], arrowIn = [2.0, 2.6]
        static let pops = 2.6, pick = 2.85
        static let grow = [2.95, 3.45], tug = [3.9, 4.5]
        static let ungrow = [6.2, 6.7], nudge = [7.2, 7.8]
    }
    static let kHome = 30.0, kLink = 34.0
    static let tau = Double.pi * 2
    static let links = Theme.bars + Theme.fits.enumerated().flatMap { p, roles in roles.map { [p, $0] } }
    static let homeLen = links.map { pair in
        let a = Theme.nodes[pair[0]], b = Theme.nodes[pair[1]]
        return hypot(b.x - a.x, b.y - a.y)
    }
    static func clamp01(_ x: Double) -> Double { max(0, min(1, x)) }
    static func seg(_ t: Double, _ a: Double, _ b: Double) -> Double { clamp01((t - a) / (b - a)) }
    static func mix(_ a: Double, _ b: Double, _ p: Double) -> Double { a + (b - a) * p }
    static func easeOut(_ x: Double) -> Double { 1 - pow(1 - clamp01(x), 5) }
    static func easeIn(_ x: Double) -> Double { pow(clamp01(x), 3) }
    static func easeInOut(_ x: Double) -> Double {
        let p = clamp01(x)
        return p < 0.5 ? 16 * pow(p, 5) : 1 - pow(-2 * p + 2, 5) / 2
    }
    static func smooth(_ x: Double) -> Double { let p = clamp01(x); return p * p * (3 - 2 * p) }
    static func wobble(_ t: Double, _ period: Double, _ decay: Double) -> Double {
        t <= 0 ? 0 : exp(-t / decay) * cos(tau * t / period)
    }
    static func tugCurve(_ t: Double) -> Double {
        if t < T.tug[0] { return 0 }
        if t < T.tug[1] { return smooth(seg(t, T.tug[0], T.tug[1])) }
        return wobble(t - T.tug[1], 0.5, 0.32)
    }
    static let follow = [(0.06, 0.2), (1.0, 0.0), (0.06, 0.2), (0.2, 0.16), (0.45, 0.08), (0.45, 0.08), (0.2, 0.16)]
    static func nodeHome(_ i: Int, _ t: Double, _ amb: Double) -> CGPoint {
        let node = Theme.nodes[i], ph = amb / LOOP, f = follow[i]
        let g = tugCurve(t - f.1) * f.0
        return CGPoint(x: node.x + 2.2 * sin(tau * (Double(1 + i % 2) * ph + Double(i) * 0.17)) + 26 * g,
                       y: node.y + 2.8 * sin(tau * (Double(2 - i % 2) * ph + Double(i) * 0.31)) + 30 * g)
    }
    struct Frame {
        var t: Double, mark: Double, arrow: Double, head: Double, pops: Double
        var grow: [Double]
        var pick: Double, nudge: Double, tugVib: Double
    }
    static func frameAt(_ t: Double) -> Frame {
        var mark = 1.0, arrow = 1.0
        if t >= T.markOut[0] && t < T.markOut[1] { mark = 1 - easeIn(seg(t, T.markOut[0], T.markOut[1])) }
        else if t >= T.markIn[0] && t < T.markIn[1] { mark = easeOut(seg(t, T.markIn[0], T.markIn[1])) }
        if t >= T.arrowOut[0] && t < T.arrowOut[1] { arrow = 1 - easeIn(seg(t, T.arrowOut[0], T.arrowOut[1])) }
        else if t >= T.arrowIn[0] && t < T.arrowIn[1] { arrow = easeInOut(seg(t, T.arrowIn[0], T.arrowIn[1])) }
        let grow = t < T.ungrow[0] ? easeOut(seg(t, T.grow[0], T.grow[1])) : 1 - easeInOut(seg(t, T.ungrow[0], T.ungrow[1]))
        let pk = t - T.pick, np = seg(t, T.nudge[0], T.nudge[1])
        return Frame(t: t, mark: mark, arrow: arrow, head: wobble(t - T.arrowIn[1] + 0.05, 0.22, 0.12),
                     pops: wobble(t - T.pops, 0.2, 0.12), grow: [0, grow, 0], pick: pk >= 0 && pk < 0.7 ? pk / 0.7 : -1,
                     nudge: np > 0 && np < 1 ? sin(.pi * np) : 0,
                     tugVib: t >= T.tug[1] ? exp(-(t - T.tug[1]) / 0.45) * sin(tau * (t - T.tug[1]) * 7) : 0)
    }
    struct Driver {
        var t = 0.0, mode = 0, last = -10.0, amb = 0.0
        var ox = Array(repeating: 0.0, count: 7), oy = Array(repeating: 0.0, count: 7)
        var vx = Array(repeating: 0.0, count: 7), vy = Array(repeating: 0.0, count: 7)
        var grow = Array(repeating: -1.0, count: 3), growAim = Array(repeating: 0.0, count: 3)
        var vib = Array(repeating: 0.0, count: 8), vibAt = Array(repeating: -10.0, count: 8)
        var hold = -1
        var fx = 0.0, fy = 0.0, gdx = 0.0, gdy = 0.0, press = 0.0, pressAim = 0.0, shootAt = -1.0
        var pickAt = Array(repeating: -1.0, count: 3)
    }
    static func linkOn(_ l: Int, _ grow: [Double]) -> Double { l < Theme.bars.count ? 1 : grow[(l - Theme.bars.count) / 2] }
    static func stepDriver(_ d: inout Driver, _ dt: Double, _ now: Double, _ auto: Bool, _ loopGrow: [Double]) -> [Double] {
        if d.mode == 2 {
            if auto && d.hold < 0 && now - d.last >= RESUME { d.mode = 0; d.grow = Array(repeating: -1, count: 3) }
        } else if auto { d.t = (d.t + dt).truncatingRemainder(dividingBy: LOOP) }
        d.amb = (d.amb + dt).truncatingRemainder(dividingBy: LOOP)
        d.press += (d.pressAim - d.press) * (1 - exp(-dt * (d.pressAim > 0 ? 40 : 16)))
        for p in 0..<3 where d.grow[p] >= 0 { d.grow[p] += (d.growAim[p] - d.grow[p]) * (1 - exp(-dt * 7)) }
        let grow = (0..<3).map { d.grow[$0] >= 0 ? d.grow[$0] : loopGrow[$0] }
        // dt == 0 is the reference's pinned/reduced path; a held node is never integrated there.
        let h = dt / 3
        for _ in 0..<3 {
            let homes = (0..<7).map { nodeHome($0, d.t, d.amb) }
            let px = (0..<7).map { homes[$0].x + d.ox[$0] }, py = (0..<7).map { homes[$0].y + d.oy[$0] }
            var ax = (0..<7).map { -kHome * d.ox[$0] - 2 * 0.42 * sqrt(kHome + 20) * d.vx[$0] }
            var ay = (0..<7).map { -kHome * d.oy[$0] - 2 * 0.42 * sqrt(kHome + 20) * d.vy[$0] }
            for l in links.indices {
                let on = linkOn(l, grow)
                if on < 0.5 { continue }
                let a = links[l][0], b = links[l][1], dx = px[b] - px[a], dy = py[b] - py[a]
                let length = hypot(dx, dy) == 0 ? 1 : hypot(dx, dy)
                let rest = hypot(homes[b].x - homes[a].x, homes[b].y - homes[a].y)
                let force = kLink * (length - rest) * on
                ax[a] += dx / length * force; ay[a] += dy / length * force
                ax[b] -= dx / length * force; ay[b] -= dy / length * force
            }
            for a in 0..<7 { for b in (a + 1)..<7 {
                let dx = px[b] - px[a], dy = py[b] - py[a]
                let distance = hypot(dx, dy) == 0 ? 1 : hypot(dx, dy)
                let minimum = Theme.nodes[a].r + Theme.nodes[b].r - 6
                if distance >= minimum { continue }
                let force = (minimum - distance) * 380
                ax[a] -= dx / distance * force; ay[a] -= dy / distance * force
                ax[b] += dx / distance * force; ay[b] += dy / distance * force
            } }
            for i in 0..<7 {
                if i == d.hold && h > 0 {
                    let nx = d.ox[i] + (d.fx - d.gdx - homes[i].x - d.ox[i]) * 0.45
                    let ny = d.oy[i] + (d.fy - d.gdy - homes[i].y - d.oy[i]) * 0.45
                    d.vx[i] = (nx - d.ox[i]) / h; d.vy[i] = (ny - d.oy[i]) / h
                    d.ox[i] = nx; d.oy[i] = ny
                } else {
                    d.vx[i] += ax[i] * h; d.vy[i] += ay[i] * h
                    d.ox[i] += d.vx[i] * h; d.oy[i] += d.vy[i] * h
                }
            }
        }
        return grow
    }
    static func touched(_ d: inout Driver, _ now: Double) {
        d.last = now
        if d.mode == 0 {
            d.grow = frameAt(d.t).grow
            d.growAim = d.grow.map { $0 > 0.5 ? 1 : 0 }
        }
        d.mode = 2
    }
    static func pluck(_ d: inout Driver, _ l: Int, _ amp: Double, _ now: Double) { d.vib[l] = amp; d.vibAt[l] = now }
    static func vibration(_ d: Driver, _ l: Int, _ now: Double) -> Double {
        let t = now - d.vibAt[l]
        return t < 0 || t > 1.6 ? 0 : d.vib[l] * exp(-t / 0.4) * sin(tau * 8 * t)
    }
}

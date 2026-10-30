import Foundation

enum IsometricScoreGaugeMotion {
    typealias Theme = IsometricScoreGaugeTheme
    static let loop = 9.0
    static let resume = 2.0
    enum T {
        static let scroll = [1.0, 1.9], pick1 = 2.1, roll1 = [2.2, 3.0], drop1 = 3.9
        static let pick2 = 4.1, roll2 = [4.15, 5.0], drop2 = 5.9
        static let rollBack = [6.0, 6.8], scrollBack = [7.0, 8.0]
    }
    struct Frame {
        var scroll = 0.0
        var sel = -1
        var selW = 0.0
        var score = Theme.Gauge.score
        var base = Theme.Gauge.score
    }
    struct Driver {
        var t = 0.0, last = -10.0, amb = 0.0
        var mode = 0
        var scroll = 0.0, scrollV = 0.0, aim = 0.0
        var dragging = 0
        var sel = -1
        var selW = 0.0, score = Theme.Gauge.score, scoreV = 0.0, goal = Theme.Gauge.score
        var frame: Frame { Frame(scroll: scroll, sel: sel, selW: selW, score: score) }
    }
    static func clamp01(_ x: Double) -> Double { min(1, max(0, x)) }
    static func seg(_ t: Double, _ a: Double, _ b: Double) -> Double { clamp01((t - a) / (b - a)) }
    static func mix(_ a: Double, _ b: Double, _ p: Double) -> Double { a + (b - a) * p }
    static func easeOut(_ x: Double) -> Double { let u = 1 - clamp01(x); return 1 - u * u * u }
    static func easeInOut(_ x: Double) -> Double {
        let p = clamp01(x)
        return p < 0.5 ? 16 * p * p * p * p * p : 1 - pow(-2 * p + 2, 5) / 2
    }
    static func smooth(_ x: Double) -> Double { let p = clamp01(x); return p * p * (3 - 2 * p) }
    static func frameAt(_ t: Double) -> Frame {
        let pitch = Theme.UI.pitch
        let scroll = t < T.scrollBack[0]
            ? pitch * easeInOut(seg(t, T.scroll[0], T.scroll[1]))
            : pitch * (1 - easeInOut(seg(t, T.scrollBack[0], T.scrollBack[1])))
        var sel = -1
        var weight = 0.0
        if t >= T.pick1 && t < T.drop1 + 0.25 {
            sel = 0
            weight = t < T.drop1 ? easeOut(seg(t, T.pick1, T.pick1 + 0.3)) : 1 - smooth(seg(t, T.drop1, T.drop1 + 0.25))
        } else if t >= T.pick2 && t < T.drop2 + 0.25 {
            sel = 3
            weight = t < T.drop2 ? easeOut(seg(t, T.pick2, T.pick2 + 0.3)) : 1 - smooth(seg(t, T.drop2, T.drop2 + 0.25))
        }
        let base = Theme.Gauge.score
        let first = base + Theme.columns[0][0].delta
        let second = base + Theme.columns[1][1].delta
        var score = base
        if t >= T.roll1[0] && t < T.roll2[0] { score = mix(base, first, easeOut(seg(t, T.roll1[0], T.roll1[1]))) }
        else if t >= T.roll2[0] && t < T.rollBack[0] { score = mix(first, second, easeOut(seg(t, T.roll2[0], T.roll2[1]))) }
        else if t >= T.rollBack[0] { score = mix(second, base, easeOut(seg(t, T.rollBack[0], T.rollBack[1]))) }
        return Frame(scroll: scroll, sel: sel, selW: weight, score: score)
    }
    static func makeDriver(_ t: Double = 0) -> Driver {
        let f = frameAt(t)
        return Driver(t: t, amb: t, scroll: f.scroll, score: f.score, goal: f.score)
    }
    static func step(_ d: inout Driver, dt: Double, now: Double, auto: Bool) {
        d.amb = (d.amb + dt).truncatingRemainder(dividingBy: loop)
        if d.mode == 0 {
            if auto { d.t = (d.t + dt).truncatingRemainder(dividingBy: loop) }
            let f = frameAt(d.t)
            d.scroll = f.scroll; d.sel = f.sel; d.selW = f.selW; d.score = f.score; d.goal = f.score
            return
        }
        // Preserve RN's semi-implicit integration, including its two damping ratios.
        if d.dragging == 0 {
            let stiffness = 90.0, damping = 2 * 0.86 * sqrt(90.0)
            d.scrollV += (-stiffness * (d.scroll - d.aim) - damping * d.scrollV) * dt
            d.scroll += d.scrollV * dt
        }
        let stiffness = 60.0, damping = 2 * 0.72 * sqrt(60.0)
        d.scoreV += (-stiffness * (d.score - d.goal) - damping * d.scoreV) * dt
        d.score += d.scoreV * dt
        d.selW += ((d.sel >= 0 ? 1 : 0) - d.selW) * (1 - exp(-dt * 14))
        if auto && d.dragging == 0 && now - d.last >= resume && d.sel < 0 && abs(d.score - Theme.Gauge.score) < 0.5 {
            d.aim = 0
            if abs(d.scroll) < 0.5 { d.mode = 0; d.t = 8.2 }
        }
    }
    static func takeOver(_ d: inout Driver, now: Double) {
        d.last = now
        if d.mode == 2 { return }
        d.mode = 2; d.scrollV = 0
        d.aim = floor(d.scroll / Theme.UI.pitch + 0.5) * Theme.UI.pitch
        d.goal = d.sel >= 0 ? Theme.Gauge.score + Theme.columns[d.sel / 2][d.sel % 2].delta : Theme.Gauge.score
    }
    static func release(_ d: inout Driver, velocity: Double) {
        d.dragging = 0; d.scrollV = velocity
        let pitch = Theme.UI.pitch
        let target = floor((d.scroll + velocity * 0.2) / pitch + 0.5) * pitch
        d.aim = max(-pitch * 1.6, min(pitch, target))
    }
    static func dragTo(_ d: inout Driver, from: Double, dx: Double) {
        let maximum = Theme.UI.pitch, minimum = -Theme.UI.pitch * 1.6
        var scroll = from + dx
        if scroll > maximum { scroll = maximum + (scroll - maximum) * 0.3 }
        if scroll < minimum { scroll = minimum + (scroll - minimum) * 0.3 }
        d.scroll = scroll; d.dragging = 1
    }
    @discardableResult static func choose(_ d: inout Driver, index: Int) -> Double {
        d.sel = d.sel == index ? -1 : index
        let delta = d.sel >= 0 ? Theme.columns[d.sel / 2][d.sel % 2].delta : 0
        d.goal = Theme.Gauge.score + delta
        return delta
    }
    static func columnPose(_ index: Int, scroll: Double) -> (x: Double, angle: Double, scale: Double) {
        let x = Theme.UI.x0 + Double(index) * Theme.UI.pitch + scroll
        let offset = x - 227.5
        let edge = clamp01((abs(offset) - 95) / 110)
        return (x, (offset < 0 ? 1 : -1) * 62 * smooth(edge), 1 - 0.12 * edge)
    }
    static func cardAt(_ point: CGPoint, scroll: Double) -> Int {
        for index in Theme.columns.indices {
            let pose = columnPose(index, scroll: scroll)
            if abs(pose.angle) > 30 || abs(point.x - pose.x) > Theme.UI.cardWidth / 2 { continue }
            for row in 0..<2 where point.y > Theme.UI.rows[row] && point.y < Theme.UI.rows[row] + Theme.UI.cardHeight {
                return index * 2 + row
            }
        }
        return -1
    }
}

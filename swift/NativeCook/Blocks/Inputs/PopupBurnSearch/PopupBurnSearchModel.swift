import CoreGraphics
import Foundation

/// Timeline, geometry and touch logic of Popup Burn Search; pure functions of time so the three stacks match.
enum PopupBurnSearchModel {
    static let loop: Double = 9.0
    static let never: Double = 1e9

    static let query = "WHAT SHOULD I COOK TONIGHT?"
    static let answers: [[String]] = [
        ["order", "noodles"], ["just toast"], ["cereal", "again"],
        ["call mom"], ["eggs,", "somehow"], ["raid the", "fridge"],
    ]
    /// Window centres from (cx, pileY).
    static let offsets: [CGPoint] = [
        CGPoint(x: -62, y: -140), CGPoint(x: 64, y: -88), CGPoint(x: -70, y: -22),
        CGPoint(x: 70, y: 32), CGPoint(x: -62, y: 96), CGPoint(x: 58, y: 150),
    ]
    static let statuses = [
        "TYPE A QUESTION, THEN SEARCH", "6 ANSWERS FOUND",
        "1 ANSWER LEFT", "2 ANSWERS LEFT", "3 ANSWERS LEFT", "4 ANSWERS LEFT", "5 ANSWERS LEFT",
        "NOTHING LEFT. ASK AGAIN?",
    ]

    static let winW: CGFloat = 214
    static let winH: CGFloat = 118
    static let titleH: CGFloat = 18
    static let burnLength: Double = 1.8

    // Base story (loop time).
    static let typeStart: Double = 0.9
    static let burstAt: Double = 2.5
    static let pressAt: Double = 3.05
    static let spawnAt: Double = 3.25
    static let spawnGap: Double = 0.16
    static let burnAt: Double = 4.9
    static let burnGap: Double = 0.18
    static let selectAt: Double = 7.65
    static let burstOff: Double = 7.7

    // MARK: Layout

    static func barY(h: CGFloat) -> CGFloat { h * 0.29 }
    static func buttonX(w: CGFloat) -> CGFloat { w / 2 + 122 }
    static func pileY(h: CGFloat) -> CGFloat { barY(h: h) + 260 }

    static func windowRect(_ i: Int, w: CGFloat, h: CGFloat) -> CGRect {
        let c = CGPoint(x: w / 2 + offsets[i].x, y: pileY(h: h) + offsets[i].y)
        return CGRect(x: c.x - winW / 2, y: c.y - winH / 2, width: winW, height: winH)
    }

    static func buttonHit(_ p: CGPoint, w: CGFloat, h: CGFloat) -> Bool {
        abs(p.x - buttonX(w: w)) < 44 && abs(p.y - barY(h: h)) < 36
    }

    // MARK: Math

    static func hash(_ x: Double) -> Double {
        let v = sin(x) * 43758.5453
        return v - floor(v)
    }

    static func h3(_ a: Int, _ b: Int, _ c: Double) -> Double {
        hash(Double(a) * 12.7 + Double(b) * 57.3 + c * 3.71 + 0.5)
    }

    static func seg(_ t: Double, _ a: Double, _ b: Double) -> Double { min(max((t - a) / (b - a), 0), 1) }
    static func lerp(_ a: Double, _ b: Double, _ t: Double) -> Double { a + (b - a) * t }
    static func easeOutCubic(_ x: Double) -> Double { 1 - pow(1 - x, 3) }
    static func easeInCubic(_ x: Double) -> Double { x * x * x }
    static func easeInOutCubic(_ x: Double) -> Double { x < 0.5 ? 4 * x * x * x : 1 - pow(-2 * x + 2, 3) / 2 }
    static func easeInOutSine(_ x: Double) -> Double { -(cos(Double.pi * x) - 1) / 2 }
    static func easeOutBack(_ x: Double) -> Double {
        let c1 = 1.2, c3 = c1 + 1
        return 1 + c3 * pow(x - 1, 3) + c1 * pow(x - 1, 2)
    }
    static func easeInBack(_ x: Double) -> Double {
        let c1 = 1.70158, c3 = c1 + 1
        return c3 * x * x * x - c1 * x * x
    }
    /// A damped spring from 0 to 1 (response, damping ratio), as SwiftUI's .spring.
    static func spring(_ t: Double, response: Double, damping: Double) -> Double {
        if t <= 0 { return 0 }
        let w0 = 2 * Double.pi / response
        let wd = w0 * sqrt(1 - damping * damping)
        return 1 - exp(-damping * w0 * t) * (cos(wd * t) + damping * w0 / wd * sin(wd * t))
    }

    /// Loop time at which character i of the query appears.
    static func charTime(_ i: Int) -> Double { 0.9 + 0.056 * Double(i) + (hash(Double(i) + 1) - 0.5) * 0.03 }

    // MARK: Burn geometry (age a since a window ignites)

    static func burnFront(_ a: Double, rect: CGRect) -> CGFloat {
        let u = easeInOutSine(seg(a, 0.15, 1.6))
        return rect.maxY + 4 - CGFloat(u) * (winH + 26)
    }

    static func flameGrow(_ a: Double) -> Double { easeOutBack(seg(a, 0, 0.28)) * flameDie(a) }
    /// 1 while the fire has paper to eat, 0 once the window is gone.
    static func flameDie(_ a: Double) -> Double { 1 - easeInCubic(seg(a, 1.25, 1.8)) }

    /// The jagged burn edge, 24 points from left − 2 to right + 14, clamped to the window's top.
    static func edgePoints(_ i: Int, a: Double, rect: CGRect) -> [CGPoint] {
        let front = burnFront(a, rect: rect)
        let q = floor(a * 12)
        var pts: [CGPoint] = []
        for k in 0 ..< 24 {
            let x = rect.minX - 2 + CGFloat(k) * 10
            let j = (hash(Double(k) * 3.1 + Double(i) * 17.9) - 0.5) * 12 + 4 * sin(Double(k) * 1.7 + q * 0.5)
            pts.append(CGPoint(x: x, y: max(front + CGFloat(j), rect.minY - 2)))
        }
        return pts
    }

    static let flameHeight: [Double] = [1, 1, 0.7, 0.45]
    static let flameWidth: [Double] = [1, 1, 0.8, 0.6]
    static let flameDepth: [Double] = [26, 26, 18, 10]

    /// One layer (0 shadow, 1 red, 2 orange, 3 yellow) of a window's flame crown, as a closed polygon:
    /// six tongues fanning out from the middle, re-drawn 12 times a second, over a bowl that hangs below the front.
    static func flameContour(_ i: Int, layer l: Int, a: Double, rect: CGRect) -> [CGPoint] {
        let g = flameGrow(a)
        let front = Double(burnFront(a, rect: rect))
        let left = Double(rect.minX) - 8, right = Double(rect.maxX) + 8
        let mid = (left + right) / 2
        let n = 6
        let step = (right - left) / Double(n)
        let q = floor(a * 12)
        let ws = flameWidth[l] * (0.3 + 0.7 * flameDie(a)), hs = flameHeight[l]
        func px(_ x: Double) -> Double { mid + (x - mid) * ws }
        let valley = 10 * g * hs
        var pts: [CGPoint] = [CGPoint(x: px(left), y: front + 2), CGPoint(x: px(left), y: front - valley)]
        for j in 0 ..< n {
            let base = left + (Double(j) + 0.5) * step
            let tall = 70 + 50 * h3(j, i, 1)
            let alt = j % 2 == 1 ? 0.55 : (j == 0 || j == n - 1 ? 0.8 : 1)
            let flick = 0.85 + 0.25 * h3(j, i, q + 50)
            let hgt = tall * alt * flick * g * hs
            let tipX = base + (base - mid) * 0.45 + (h3(j, i, q + 90) - 0.5) * 16
            pts.append(CGPoint(x: px(tipX), y: front - hgt))
            pts.append(CGPoint(x: px(left + Double(j + 1) * step), y: front - valley))
        }
        pts.append(CGPoint(x: px(right), y: front + 2))
        let depth = flameDepth[l] * g
        for k in 1 ..< 8 {
            let f = Double(k) / 8
            pts.append(CGPoint(x: px(right - (right - left) * f), y: front + 2 + depth * 4 * f * (1 - f)))
        }
        return pts
    }

    struct Particle {
        var x: CGFloat
        var y: CGFloat
        var size: CGFloat
        var angle: Double
        var color: Int
        var alpha: Double
    }

    static func embers(_ i: Int, a: Double, rect: CGRect) -> [Particle] {
        var out: [Particle] = []
        for k in 0 ..< 12 {
            let b = 0.3 + 0.09 * Double(k)
            let life = 0.8 + 0.3 * h3(k, i, 3)
            let s = (a - b) / life
            if s < 0 || s >= 1 { continue }
            let x0 = Double(rect.minX) + (0.08 + 0.84 * h3(k, i, 4)) * Double(winW)
            let y0 = Double(burnFront(b, rect: rect))
            let x = x0 + 12 * sin(2 * Double.pi * (s * 1.2 + h3(k, i, 6)))
            let y = y0 - s * (70 + 70 * h3(k, i, 5))
            let color = s < 0.35 ? 0xFFE03D : (s < 0.7 ? 0xFF8C1A : 0xE8203A)
            let alpha = s < 0.6 ? 1 : 1 - (s - 0.6) / 0.4
            out.append(Particle(x: CGFloat(x), y: CGFloat(y), size: CGFloat(6 - 4 * s), angle: 0, color: color, alpha: alpha))
        }
        for k in 0 ..< 5 {
            let b = 0.5 + 0.2 * Double(k)
            let s = (a - b) / 1.1
            if s < 0 || s >= 1 { continue }
            let x0 = Double(rect.minX) + (0.1 + 0.8 * h3(k, i, 7)) * Double(winW)
            let y0 = Double(burnFront(b, rect: rect))
            let x = x0 + (h3(k, i, 8) - 0.5) * 32 * sin(Double.pi * s)
            let y = y0 - s * (50 + 40 * h3(k, i, 9))
            out.append(Particle(x: CGFloat(x), y: CGFloat(y), size: CGFloat(4 + 2 * h3(k, i, 10)), angle: 3 * s,
                                color: 0x1C120C, alpha: 0.85 * (1 - s)))
        }
        return out
    }

    /// Starburst polygon around (0, 0), 13 spikes; q is the 8 fps boil frame.
    static func burstPoints(q: Double) -> [CGPoint] {
        var pts: [CGPoint] = []
        let n = 13
        for j in 0 ..< n {
            let base = Double(j) / Double(n) * 2 * Double.pi - Double.pi / 2
            let boil = 1 + (hash(Double(j) * 7.7 + q * 1.3) - 0.5) * 0.1
            let outer = (52 + 30 * hash(Double(j) * 4.3 + 11)) * boil
            let inner = 27 + 6 * hash(Double(j) * 2.9 + 23)
            let ao = base + (hash(Double(j) * 5.9 + 31) - 0.5) * 0.24
            let ai = base + Double.pi / Double(n)
            pts.append(CGPoint(x: outer * cos(ao), y: outer * sin(ao)))
            pts.append(CGPoint(x: inner * cos(ai), y: inner * sin(ai)))
        }
        return pts
    }
}

/// What happens when, in one time frame (loop time while playing, takeover time after a touch).
struct PopupBurnSearchStory {
    typealias M = PopupBurnSearchModel
    var typeStart = M.never
    var selectAt = M.never
    var burstAt = M.never
    var burstOff = M.never
    var pressAt = -M.never
    var spawn = [Double](repeating: M.never, count: 6)
    var burn = [Double](repeating: M.never, count: 6)
    var cursorUntil = M.never

    static var base: PopupBurnSearchStory {
        var s = PopupBurnSearchStory()
        s.typeStart = M.typeStart
        s.selectAt = M.selectAt
        s.burstAt = M.burstAt
        s.burstOff = M.burstOff
        s.pressAt = M.pressAt
        for i in 0 ..< 6 {
            s.spawn[i] = M.spawnAt + M.spawnGap * Double(i)
            s.burn[i] = M.burnAt + M.burnGap * Double(5 - i)
        }
        return s
    }

    /// The base story with every event after t dropped (a touch took over at loop time t).
    static func cut(at t: Double) -> PopupBurnSearchStory {
        var s = base
        if s.typeStart > t { s.typeStart = M.never }
        if s.selectAt > t { s.selectAt = M.never }
        if s.burstAt > t { s.burstAt = M.never }
        if s.burstOff > t { s.burstOff = M.never }
        if s.pressAt > t {
            s.pressAt = -M.never
            for i in 0 ..< 6 { s.spawn[i] = M.never }
        }
        for i in 0 ..< 6 where s.burn[i] > t { s.burn[i] = M.never }
        s.cursorUntil = t
        return s
    }

    static func shown(at t: Double) -> PopupBurnSearchStory {
        var s = PopupBurnSearchStory()
        s.typeStart = t - 10
        s.burstAt = t - 10
        for i in 0 ..< 6 { s.spawn[i] = t - 10 }
        s.cursorUntil = -M.never
        return s
    }

    func typed(_ t: Double) -> Int {
        if t >= selectAt + 0.25 { return 0 }
        var n = 0
        for i in 0 ..< M.query.count where t >= typeStart + M.charTime(i) - M.typeStart { n += 1 }
        return n
    }

    func standing(_ i: Int) -> Bool { spawn[i] < M.never && burn[i] >= M.never }
    func gone(_ i: Int, _ t: Double) -> Bool { spawn[i] >= M.never || (burn[i] < M.never && t >= burn[i] + M.burnLength) }
}

struct PopupBurnSearchFrame {
    var t: Double = 0
    var typed = 0
    var selected = false
    var caret = true
    var burstScale: Double = 0
    var burstAngle: Double = 0
    var burstQ: Double = 0
    var press: Double = 0
    var cursor = CGPoint.zero
    var cursorAlpha: Double = 0
    var cursorSquish: Double = 1
    var status = 0
    var spawnAge = [Double](repeating: -1, count: 6)
    var burnAge = [Double](repeating: -1, count: 6)
    var reduced = false

    typealias M = PopupBurnSearchModel

    static func make(_ s: PopupBurnSearchStory, t: Double, reduced: Bool) -> PopupBurnSearchFrame {
        var f = PopupBurnSearchFrame()
        f.t = t
        f.reduced = reduced
        f.typed = s.typed(t)
        f.selected = t >= s.selectAt && t < s.selectAt + 0.25
        let typing = f.typed > 0 && f.typed < M.query.count
        f.caret = !f.selected && (reduced || typing || Int(floor(t / 0.5)) % 2 == 0)

        // Starburst: spring pop, a pulse on the click, shrink on the way out.
        if t >= s.burstAt {
            let a = t - s.burstAt
            let sp = reduced ? 1 : M.spring(a, response: 0.5, damping: 0.55)
            var scale = sp
            let p = t - s.pressAt
            if p >= 0 && p < 0.3 && !reduced { scale *= 1 + 0.12 * sin(Double.pi * p / 0.3) }
            scale *= 1 - M.easeInBack(M.seg(t, s.burstOff, s.burstOff + 0.25))
            f.burstScale = max(0, scale)
            f.burstAngle = reduced ? 0 : -25 * (1 - sp)
        }
        f.burstQ = reduced ? 0 : floor(t * 8)

        let p = t - s.pressAt
        if p >= 0 && p < 0.2 {
            f.press = p < 0.06 ? M.easeOutCubic(p / 0.06) : 1 - M.easeOutCubic((p - 0.06) / 0.14)
        }

        // Cursor: the loop's own path, frozen and faded when a touch takes over.
        if !reduced {
            let c = min(t, s.cursorUntil)
            let m = M.easeInOutCubic(M.seg(c, 2.3, 2.95))
            var x = M.lerp(-82, 2, m) + 30 * sin(Double.pi * m)
            var y = M.lerp(190, 6, m)
            let d = M.easeInOutCubic(M.seg(c, 3.6, 4.2))
            x = M.lerp(x, 34, d)
            y = M.lerp(y, 74, d)
            f.cursor = CGPoint(x: x, y: y)
            f.cursorAlpha = M.seg(c, 2.3, 2.45) * (1 - M.seg(c, 3.9, 4.2)) * (1 - M.seg(t, s.cursorUntil, s.cursorUntil + 0.2))
            let q = c - M.pressAt
            if q >= 0 && q < 0.06 { f.cursorSquish = 1 - 0.2 * q / 0.06 }
            else if q >= 0.06 && q < 0.2 { f.cursorSquish = 0.8 + 0.2 * M.easeOutCubic((q - 0.06) / 0.14) }
        }

        var anySpawned = false
        var left = 0
        for i in 0 ..< 6 {
            if t >= s.spawn[i] { f.spawnAge[i] = t - s.spawn[i]; anySpawned = true }
            if t >= s.burn[i] { f.burnAge[i] = t - s.burn[i] }
            if s.spawn[i] < M.never && f.burnAge[i] < 1.0 { left += 1 }
        }
        if !anySpawned || t >= s.selectAt + 0.25 { f.status = 0 }
        else if left == 0 { f.status = 7 }
        else if left == 6 { f.status = 1 }
        else { f.status = left + 1 }
        return f
    }
}

/// Plays the loop, takes over on touch, and eases back to the loop 2 s after the last touch.
final class PopupBurnSearchDriver {
    typealias M = PopupBurnSearchModel
    private(set) var manual = false
    private var loopStart: Double?
    private var epoch: Double = 0
    private(set) var story = PopupBurnSearchStory()
    private var lastTouch: Double = -M.never
    private var returning = false
    private var settleAt: Double?
    private var reducedReady = false

    func reset() {
        manual = false
        loopStart = nil
        story = PopupBurnSearchStory()
        returning = false
        settleAt = nil
        reducedReady = false
    }

    func frame(at now: Double, auto: Bool, reduced: Bool) -> PopupBurnSearchFrame {
        if reduced {
            if !reducedReady {
                reducedReady = true
                manual = true
                epoch = now
                story = .shown(at: 0)
            }
            return .make(story, t: now - epoch, reduced: true)
        }
        if !manual {
            if auto {
                if loopStart == nil { loopStart = now }
                let tau = (now - loopStart!).truncatingRemainder(dividingBy: M.loop)
                return .make(.base, t: tau, reduced: false)
            }
            return .make(story, t: now - epoch, reduced: false)
        }
        let t = now - epoch
        settle(now: now, t: t, auto: auto)
        return .make(story, t: now - epoch, reduced: false)
    }

    /// True while something still moves after a touch (so a paused timeline can stop).
    func busy(at now: Double) -> Bool { manual || now - lastTouch < 6 }

    private func settle(now: Double, t: Double, auto: Bool) {
        guard now - lastTouch >= 2 else { return }
        if !returning {
            returning = true
            igniteAll(t: t, gap: M.burnGap)
        }
        guard (0 ..< 6).allSatisfy({ story.gone($0, t) }) else { return }
        if settleAt == nil {
            var end = t
            if story.typed(t) > 0 && story.selectAt >= M.never { story.selectAt = t + 0.1 }
            if story.selectAt < M.never { end = max(end, story.selectAt + 0.25) }
            if t >= story.burstAt && story.burstOff >= M.never { story.burstOff = t + 0.15 }
            if story.burstAt < M.never && story.burstOff < M.never { end = max(end, story.burstOff + 0.25) }
            settleAt = end + 0.3
        }
        if let s = settleAt, t >= s {
            manual = false
            returning = false
            settleAt = nil
            story = PopupBurnSearchStory()
            if auto { loopStart = now } else { epoch = now }
        }
    }

    private func igniteAll(t: Double, gap: Double) {
        var rank = 0
        for i in stride(from: 5, through: 0, by: -1) where story.standing(i) {
            story.burn[i] = max(t + Double(rank) * gap, story.spawn[i] + 0.3)
            rank += 1
        }
    }

    /// A finger went down: the story becomes ours from here.
    func takeOver(at now: Double, auto: Bool) {
        if !manual {
            if auto, let start = loopStart {
                let tau = (now - start).truncatingRemainder(dividingBy: M.loop)
                epoch = now - tau
                story = .cut(at: tau)
            } else {
                epoch = now
                story = PopupBurnSearchStory()
            }
            manual = true
        }
        lastTouch = now
        returning = false
        settleAt = nil
    }

    /// Tap on the search button. Returns true when something new happened.
    func search(at now: Double) -> Bool {
        let t = now - epoch
        story.pressAt = t
        if story.typed(t) < M.query.count || story.selectAt < M.never {
            story.typeStart = t - 2
            story.selectAt = M.never
        }
        if !(t >= story.burstAt && t < story.burstOff) {
            story.burstAt = t
            story.burstOff = M.never
        }
        var k = 0
        for i in 0 ..< 6 where story.gone(i, t) {
            story.spawn[i] = t + 0.2 + M.spawnGap * Double(k)
            story.burn[i] = M.never
            k += 1
        }
        return true
    }

    /// Tap on a window: the topmost standing one under the finger ignites.
    func burn(at p: CGPoint, now: Double, w: CGFloat, h: CGFloat) -> Bool {
        let t = now - epoch
        for i in stride(from: 5, through: 0, by: -1) where story.standing(i) && t >= story.spawn[i] {
            if M.windowRect(i, w: w, h: h).contains(p) {
                story.burn[i] = t
                return true
            }
        }
        return false
    }

    /// Long press: everything standing ignites, bottom first.
    func burnAll(at now: Double) -> Bool {
        let t = now - epoch
        let any = (0 ..< 6).contains { story.standing($0) }
        igniteAll(t: t, gap: 0.12)
        return any
    }
}

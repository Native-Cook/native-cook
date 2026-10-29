import CoreGraphics
import Foundation

/// Timing and touch model of Deli Grain Tabs. In the loop every value is a pure function of the
/// loop time, so the loop's last frame equals its first.
enum DeliGrainTabsMotion {
    static let loop = 9.6
    static let hold = 0.9 // rest before each sift
    static let sift = 1.5 // one shape pouring into the next
    static let order = [0, 2, 6, 3] // Balsamico, Flour & Pizza, Pasta & Risotto, Sweets & Coffee
    static let resumeAfter = 2.0

    /// A sample of the finger's trail; each pushes the grains near it, then lets them settle back.
    struct Stir {
        var x: Double
        var y: Double
        var vx: Double
        var vy: Double
        var at: Double // clock time it was left behind (ignored while live)
        var live = false
    }

    /// What the art needs for one frame.
    struct Frame {
        /// Shape the grains leave (−1: from `buffer`, wherever they were when a tab was tapped).
        var from = 0
        var to = 0
        /// The tab selected before (for the tab colours, the indicator and the label).
        var fromTab = 0
        /// Seconds into the sift; at `sift` or later the grains rest on `to`.
        var p = sift
        var buffer: DeliGrainTabsBuffer?
        var stirs: [Stir] = []
        var clock = 0.0

        var resting: Bool { p >= sift }
    }

    // MARK: Curves

    static func mod(_ a: Double, _ b: Double) -> Double {
        let r = a.truncatingRemainder(dividingBy: b)
        return r < 0 ? r + b : r
    }

    static func clamp(_ v: Double, _ lo: Double = 0, _ hi: Double = 1) -> Double { min(max(v, lo), hi) }
    static func seg(_ t: Double, _ a: Double, _ b: Double) -> Double { clamp((t - a) / (b - a)) }
    static func easeOutCubic(_ x: Double) -> Double { 1 - pow(1 - x, 3) }
    static func easeInCubic(_ x: Double) -> Double { x * x * x }
    static func easeInOutCubic(_ x: Double) -> Double { x < 0.5 ? 4 * x * x * x : 1 - pow(-2 * x + 2, 3) / 2 }

    // MARK: The loop (P = 9.6 s): four segments of 0.9 s rest + 1.5 s sift

    static func loopFrame(_ t: Double) -> Frame {
        let segment = hold + sift
        let k = min(order.count - 1, Int(t / segment))
        let tau = t - Double(k) * segment
        let a = order[k]
        let b = order[(k + 1) % order.count]
        if tau < hold { return Frame(from: a, to: a, fromTab: a, p: sift) }
        return Frame(from: a, to: b, fromTab: a, p: tau - hold)
    }

    // MARK: Grains

    /// Per-grain constants, seeded.
    static let seeds: [[Double]] = (0..<DeliGrainTabsArt.grainCount).map { i in
        (1...6).map { k in DeliGrainTabsArt.hash(Double(i) * 1.733 + Double(k) * 9.17) }
    }

    /// Where grain `i` is (points), and how far through its flight (0 at rest, 1 mid-air).
    static func grain(_ i: Int, from s: CGPoint, rFrom: Double, to t: CGPoint, rTo: Double, p: Double,
                      centre: CGPoint) -> (CGPoint, Double) {
        if p >= sift { return (t, 0) }
        let h = seeds[i]
        let start = 0.36 * (1 - rFrom) + 0.10 * h[0]
        let land = 1.0 + 0.40 * (1 - rTo) + 0.08 * h[1]
        let u = clamp((p - start) / (land - start))
        if u <= 0 { return (s, 0) }
        if u >= 1 { return (t, 0) }
        let e = easeInOutCubic(u)
        // Falls out of the old shape, swings round and rains onto the new one.
        let c1 = CGPoint(x: s.x + 50 * (2 * h[2] - 1), y: s.y + 150 + 50 * h[3])
        let c2 = CGPoint(x: t.x + 40 * (2 * h[4] - 1), y: t.y - 160 - 60 * h[5])
        let v = 1 - e
        var x = v * v * v * s.x + 3 * v * v * e * c1.x + 3 * v * e * e * c2.x + e * e * e * t.x
        var y = v * v * v * s.y + 3 * v * v * e * c1.y + 3 * v * e * e * c2.y + e * e * e * t.y
        let lift = sin(Double.pi * e)
        let angle = lift * (0.55 + 0.7 * h[5])
        let dx = x - centre.x
        let dy = y - centre.y
        x = centre.x + dx * cos(angle) - dy * sin(angle)
        y = centre.y + dx * sin(angle) + dy * cos(angle)
        x += 10 * lift * sin(2 * Double.pi * (2 * e + h[2]))
        y += 7 * lift * cos(2 * Double.pi * (2 * e + h[3]))
        return (CGPoint(x: x, y: y), sin(Double.pi * u))
    }

    /// How the finger's trail pushes a grain at (x, y): away from each sample and along its
    /// motion, fading with a springy settle; capped softly at 34 pt.
    static func stirOffset(_ x: Double, _ y: Double, _ stirs: [Stir], clock: Double) -> (Double, Double) {
        var ox = 0.0
        var oy = 0.0
        for s in stirs {
            let env: Double
            if s.live {
                env = 1
            } else {
                let a = clock - s.at
                if a > 1.6 { continue }
                env = exp(-3.2 * a) * cos(4.5 * a)
            }
            let dx = x - s.x
            let dy = y - s.y
            let d2 = dx * dx + dy * dy
            if d2 > 8100 { continue }
            let w = exp(-d2 / 1800) * env
            let d = sqrt(d2) + 0.5
            ox += dx / d * 26 * w + clamp(s.vx * 0.05, -30, 30) * w
            oy += dy / d * 26 * w + clamp(s.vy * 0.05, -30, 30) * w
        }
        let m = sqrt(ox * ox + oy * oy)
        if m < 0.01 { return (0, 0) }
        let capped = 34 * tanh(m / 34)
        return (ox / m * capped, oy / m * capped)
    }

    /// 0 when no stir is near, 1 while the finger is down; drives the solid fill's fade.
    static func stirEnergy(_ stirs: [Stir], clock: Double) -> Double {
        var e = 0.0
        for s in stirs {
            e = max(e, s.live ? 1 : clamp(exp(-3.2 * (clock - s.at)) * 1.6))
        }
        return clamp(e)
    }

    /// Fills the arrays with every grain's position (points) and flight share for a frame.
    static func positions(_ f: Frame, _ l: DeliGrainTabsArt.Layout, xs: inout [Double], ys: inout [Double],
                          fly: inout [Double]) {
        let n = DeliGrainTabsArt.grainCount
        if xs.count != n {
            xs = Array(repeating: 0, count: n)
            ys = Array(repeating: 0, count: n)
            fly = Array(repeating: 0, count: n)
        }
        let target = DeliGrainTabsArt.grains(f.to)
        let source = f.from >= 0 ? DeliGrainTabsArt.grains(f.from) : nil
        let centre = l.swirlCentre
        let stirring = !f.stirs.isEmpty
        for i in 0..<n {
            let t = l.hero(target.x[i], target.y[i])
            var point = t
            var share = 0.0
            if !f.resting {
                let s: CGPoint
                let rFrom: Double
                if let source {
                    s = l.hero(source.x[i], source.y[i])
                    rFrom = source.r[i]
                } else if let b = f.buffer {
                    s = CGPoint(x: b.x[i], y: b.y[i])
                    rFrom = b.r[i]
                } else {
                    s = t
                    rFrom = target.r[i]
                }
                (point, share) = grain(i, from: s, rFrom: rFrom, to: t, rTo: target.r[i], p: f.p, centre: centre)
            }
            if stirring {
                let (ox, oy) = stirOffset(Double(point.x), Double(point.y), f.stirs, clock: f.clock)
                point.x += ox
                point.y += oy
            }
            xs[i] = Double(point.x)
            ys[i] = Double(point.y)
            fly[i] = share
        }
    }
}

/// Grain positions captured when a tab is tapped mid-flight (points, plus each grain's height share).
final class DeliGrainTabsBuffer {
    let x: [Double]
    let y: [Double]
    let r: [Double]

    init(x: [Double], y: [Double]) {
        self.x = x
        self.y = y
        let top = y.min() ?? 0
        let span = max((y.max() ?? 1) - top, 1)
        r = y.map { ($0 - top) / span }
    }
}

/// Touch state: taps sift to a tab, drags stir the grains; 2 s after the last touch the grains
/// sift back to Balsamico and the loop restarts.
final class DeliGrainTabsTouch {
    typealias M = DeliGrainTabsMotion

    private var manual = false
    private var from = 0
    private var to = 0
    private var fromTab = 0
    private var start = -100.0
    private var buffer: DeliGrainTabsBuffer?
    private var loopOrigin = 0.0
    private var lastTouch = -100.0
    private var trail: [M.Stir] = []
    private var finger: M.Stir?
    private(set) var touching = false

    /// The clock time after which nothing moves any more (for a block that does not autoplay).
    var restsAfter: Double {
        max(start + M.sift, lastTouch + 1.6)
    }

    func frame(_ clock: Double, autoPlay: Bool) -> M.Frame {
        if manual, autoPlay, !touching, clock > lastTouch + M.resumeAfter, clock >= start + M.sift {
            if to != M.order[0] {
                // Sift back to the loop's first shape, then hand over to the loop.
                from = to
                fromTab = to
                to = M.order[0]
                buffer = nil
                start = clock
            } else {
                manual = false
                loopOrigin = clock
                trail.removeAll()
            }
        }
        var f: M.Frame
        if manual || !autoPlay {
            f = M.Frame(from: from, to: to, fromTab: fromTab, p: clock - start, buffer: buffer)
        } else {
            f = M.loopFrame(M.mod(clock - loopOrigin, M.loop))
        }
        trail.removeAll { clock - $0.at > 1.6 }
        f.stirs = trail
        if var live = finger {
            // A finger that stops stops dragging the grains along.
            let calm = exp(-8 * max(0, clock - live.at))
            live.vx *= calm
            live.vy *= calm
            f.stirs.append(live)
        }
        f.clock = clock
        return f
    }

    /// Freezes the loop where it is so a touch can take over.
    private func takeOver(_ clock: Double, autoPlay: Bool) {
        guard !manual, autoPlay else { return }
        let f = M.loopFrame(M.mod(clock - loopOrigin, M.loop))
        manual = true
        from = f.from
        to = f.to
        fromTab = f.fromTab
        buffer = nil
        start = clock - min(f.p, M.sift)
    }

    func tap(_ tab: Int, clock: Double, autoPlay: Bool, still: Bool, layout: DeliGrainTabsArt.Layout) {
        takeOver(clock, autoPlay: autoPlay)
        manual = true
        lastTouch = clock
        let current = frame(clock, autoPlay: autoPlay)
        if tab == to && current.resting { return }
        if still {
            // Reduced motion: a cross-fade from the shape on screen.
            from = to
            fromTab = to
            to = tab
            buffer = nil
            start = clock
            return
        }
        var xs: [Double] = []
        var ys: [Double] = []
        var fly: [Double] = []
        M.positions(current, layout, xs: &xs, ys: &ys, fly: &fly)
        buffer = DeliGrainTabsBuffer(x: xs, y: ys)
        from = -1
        fromTab = to
        to = tab
        start = clock
        // The pushes are baked into the buffer.
        trail.removeAll()
    }

    func begin(_ x: Double, _ y: Double, clock: Double, autoPlay: Bool) {
        takeOver(clock, autoPlay: autoPlay)
        manual = true
        touching = true
        lastTouch = clock
        finger = M.Stir(x: x, y: y, vx: 0, vy: 0, at: clock, live: true)
    }

    func move(_ x: Double, _ y: Double, clock: Double) {
        guard var f = finger else { return }
        let dt = max(clock - f.at, 1.0 / 120)
        let dx = x - f.x
        let dy = y - f.y
        if dx * dx + dy * dy >= 36 {
            trail.append(M.Stir(x: f.x, y: f.y, vx: f.vx, vy: f.vy, at: clock))
            if trail.count > 10 { trail.removeFirst(trail.count - 10) }
            f.vx = f.vx * 0.4 + dx / dt * 0.6
            f.vy = f.vy * 0.4 + dy / dt * 0.6
            f.x = x
            f.y = y
            f.at = clock
            finger = f
        }
        lastTouch = clock
    }

    func end(clock: Double) {
        if let f = finger {
            trail.append(M.Stir(x: f.x, y: f.y, vx: f.vx, vy: f.vy, at: clock))
        }
        finger = nil
        touching = false
        lastTouch = clock
    }
}

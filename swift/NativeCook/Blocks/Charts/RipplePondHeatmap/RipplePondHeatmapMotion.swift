import Foundation

/*
 Ripple Pond Heatmap — geometry, the autoPlay script and the touch simulation, a port of the
 React Native rippleMotion.ts.

 loop = 9 s:
   0.0 – 1.4   rest: still water, caustics drifting (one cycle per loop).
   1.4         a tap on day 7: a ripple leaves it (140 pt/s), the pads around bob as it passes,
               the detail card rolls to "Wed 7 · 28 min · 2 sessions" and a ring lights the pad.
   3.2         a tap on day 18, the same.
   4.6 – 5.8   a finger drags across week 4 (ease-in-out), leaving a wake: a ripple every 0.2 s
               along its path; the card rolls to the week.
   5.8 – 7.6   the water settles (every ripple fades out by 7.8); 7.4 the card rolls back to
               the month and the ring fades.
   7.6 – 9.0   rest.
*/
enum RipplePondHeatmapMotion {
    static let loop = 9.0
    static let reducedAt = 0.0
    static let idleBeforeBack = 2.0
    static let backFor = 0.9
    static let sources = 8

    static let headerH = 118.0
    static let cardH = 92.0
    static let chipsH = 40.0

    static let taps: [(Double, Int)] = [(1.4, 7), (3.2, 18)]
    static let wakeFrom = 4.6
    static let wakeTo = 5.8
    static let wakeRow = 3

    static func clamp01(_ x: Double) -> Double { min(1, max(0, x)) }
    static func seg(_ t: Double, _ a: Double, _ b: Double) -> Double { clamp01((t - a) / (b - a)) }
    static func easeInOut(_ x: Double) -> Double {
        x < 0.5 ? 4 * x * x * x : 1 - pow(-2 * x + 2, 3) / 2
    }

    /// cubic-bezier(0.77, 0, 0.175, 1), the detail card's roll.
    static func rollEase(_ x: Double) -> Double {
        if x <= 0 { return 0 }
        if x >= 1 { return 1 }
        func bez(_ u: Double, _ a: Double, _ b: Double) -> Double {
            3 * (1 - u) * (1 - u) * u * a + 3 * (1 - u) * u * u * b + u * u * u
        }
        var lo = 0.0, hi = 1.0
        for _ in 0..<24 {
            let mid = (lo + hi) / 2
            if bez(mid, 0.77, 0.175) < x { lo = mid } else { hi = mid }
        }
        return bez((lo + hi) / 2, 0, 1)
    }
}

struct RipplePondHeatmapGeo: Equatable {
    var w = 1.0
    var h = 1.0
    /// Left, top, cell width, row height.
    var grid: [Double] = [0, 0, 1, 1]
    var cardTop = 0.0
    var legendTop = 0.0
    var chipsTop = 0.0

    init() {}

    init(w: Double, h: Double, insetTop: Double, insetBottom: Double) {
        typealias M = RipplePondHeatmapMotion
        let left = 16.0
        let cellW = (w - left * 2) / 7
        let gridTop = insetTop + M.headerH + 34
        let chipsTop = h - max(insetBottom, 12) - 8 - M.chipsH
        let cardTop = chipsTop - 12 - M.cardH
        // Rows taller than wide (the pond spreads down the screen), short enough to leave the
        // legend room above the card.
        let rowH = max(1, min(cellW * 1.45, (cardTop - 56 - gridTop) / 5))
        self.w = w
        self.h = h
        grid = [left, gridTop, max(1, cellW), rowH]
        self.cardTop = cardTop
        legendTop = gridTop + rowH * 5 + 14
        self.chipsTop = chipsTop
    }

    func padCenter(_ day: Int) -> (Double, Double) {
        let (c, r) = RipplePondHeatmapData.cellOf(day)
        return (grid[0] + (Double(c) + 0.5) * grid[2], grid[1] + (Double(r) + 0.5) * grid[3])
    }

    /// The day under a point, or 0.
    func dayAt(_ x: Double, _ y: Double) -> Int {
        let c = Int(floor((x - grid[0]) / grid[2]))
        let r = Int(floor((y - grid[1]) / grid[3]))
        if c < 0 || c > 6 || r < 0 || r > 4 { return 0 }
        let day = r * 7 + c - 3 + 1
        if day < 1 || day > 31 { return 0 }
        let cx = grid[0] + (Double(c) + 0.5) * grid[2]
        let cy = grid[1] + (Double(r) + 0.5) * grid[3]
        return hypot(x - cx, y - cy) < grid[2] * 0.48 ? day : 0
    }

    /// The finger's place along the wake at time t (ease-in-out across the row).
    func wakeAt(_ t: Double) -> (Double, Double) {
        typealias M = RipplePondHeatmapMotion
        let u = M.easeInOut(M.seg(t, M.wakeFrom, M.wakeTo))
        let x0 = grid[0] + grid[2] * 0.2
        let x1 = grid[0] + grid[2] * 6.8
        let y = grid[1] + (Double(M.wakeRow) + 0.5) * grid[3] + sin(u * .pi) * 6
        return (x0 + (x1 - x0) * u, y)
    }
}

struct RipplePondHeatmapFrame {
    /// 8 sources × (x, y, age, amplitude).
    var src: [Double] = Array((0..<RipplePondHeatmapMotion.sources).map { _ in [0.0, 0, -1, 0] }.joined())
    /// Selected pad x, y, glow.
    var sel: [Double] = [0, 0, 0]
    /// Detail card: -1 month, 1…31 day, 100 + row week.
    var detail = -1
    var phase = 0.0

    /// The autoPlay frame at loop time t (pure), so frame `loop` = frame 0.
    mutating func script(_ t: Double, _ g: RipplePondHeatmapGeo) {
        typealias M = RipplePondHeatmapMotion
        phase = t / M.loop
        let calm = 1 - M.easeInOut(M.seg(t, 6.8, 7.8))
        for i in 0..<2 {
            let c = g.padCenter(M.taps[i].1)
            src[i * 4] = c.0
            src[i * 4 + 1] = c.1
            src[i * 4 + 2] = t >= M.taps[i].0 ? t - M.taps[i].0 : -1
            src[i * 4 + 3] = calm
        }
        for j in 0..<6 {
            let born = M.wakeFrom + Double(j) * 0.2
            let p = g.wakeAt(born)
            let k = (2 + j) * 4
            src[k] = p.0
            src[k + 1] = p.1
            src[k + 2] = t >= born ? t - born : -1
            src[k + 3] = 0.55 * calm
        }
        var d = -1
        var day = 0
        if t >= 1.45 && t < 3.25 {
            d = 7
            day = 7
        } else if t >= 3.25 && t < 4.7 {
            d = 18
            day = 18
        } else if t >= 4.7 && t < 7.4 {
            d = 100 + M.wakeRow
        }
        detail = d
        if day > 0 {
            let c = g.padCenter(day)
            sel[0] = c.0
            sel[1] = c.1
        }
        let glow7 = M.easeInOut(M.seg(t, 1.45, 1.75)) * (1 - M.easeInOut(M.seg(t, 3.2, 3.45)))
        let glow18 = M.easeInOut(M.seg(t, 3.3, 3.6)) * (1 - M.easeInOut(M.seg(t, 4.6, 4.9)))
        sel[2] = day == 7 ? glow7 : day == 18 ? glow18 : 0
    }
}

/// The touch simulation and the autoPlay clock, stepped once a frame like the RN frame callback.
@MainActor
final class RipplePondHeatmapSim {
    private typealias M = RipplePondHeatmapMotion
    enum Mode { case auto, live, back }

    private(set) var mode = Mode.auto
    private var t = 0.0
    private(set) var now = 0.0
    private var last = 0.0
    private var back = 0.0
    private(set) var frame = RipplePondHeatmapFrame()
    private var next = 0
    private var dragging = false
    private var lastX = 0.0
    private var lastY = 0.0
    private var from: [Double] = Array(repeating: 0, count: 10)
    private var lastDate: Date?
    private var wasAuto: Bool?
    /// The card's roll: the detail shown, the one before it, and when it changed.
    private(set) var shown = -1
    private(set) var previous = -1
    private(set) var changedAt = -10.0

    func step(to date: Date, geo g: RipplePondHeatmapGeo, auto: Bool, reduced: Bool, ready: Bool) -> RipplePondHeatmapFrame {
        if wasAuto != auto {
            wasAuto = auto
            mode = .auto
            t = 0
            now = 0
            last = 0
            frame = RipplePondHeatmapFrame()
            dragging = false
            shown = -1
            previous = -1
            changedAt = -10
        }
        let raw = lastDate.map { date.timeIntervalSince($0) } ?? 1.0 / 60
        lastDate = date
        // The loop waits for the baked pads; a stalled frame pauses the clock instead of
        // skipping ahead.
        let dt = !ready || raw > 0.25 ? 0 : min(max(raw, 0), 1.0 / 20)
        advance(dt, g, auto: auto, reduced: reduced)
        if frame.detail != shown {
            previous = shown
            shown = frame.detail
            changedAt = now
        }
        return frame
    }

    /// The card's roll progress, 0 → 1 over 0.38 s after the detail changed.
    var roll: Double { M.rollEase(M.clamp01((now - changedAt) / 0.38)) }

    private func goLive() {
        if mode == .live { return }
        mode = .live
        // Ripples already out keep travelling; the oldest slot is reused first.
        var oldest = 0
        var age = -2.0
        for i in 0..<M.sources {
            let a = frame.src[i * 4 + 2]
            let rank = a < 0 ? 1e9 : a
            if rank > age {
                age = rank
                oldest = i
            }
        }
        next = oldest
    }

    private func emit(_ x: Double, _ y: Double, _ amp: Double) {
        let k = next * 4
        frame.src[k] = x
        frame.src[k + 1] = y
        frame.src[k + 2] = 0
        frame.src[k + 3] = amp
        next = (next + 1) % M.sources
    }

    func down(_ x: Double, _ y: Double) {
        goLive()
        last = now
        lastX = x
        lastY = y
    }

    /// A tap drops a ripple; on a pad it selects the day. Returns the day (0 for open water).
    func tap(_ g: RipplePondHeatmapGeo, _ x: Double, _ y: Double) -> Int {
        goLive()
        last = now
        let day = g.dayAt(x, y)
        emit(x, y, 1)
        if day > 0 {
            let c = g.padCenter(day)
            frame.sel = [c.0, c.1, day <= RipplePondHeatmapData.today ? 1 : 0.5]
            frame.detail = day
        }
        return day
    }

    /// A drag leaves a wake: a ripple every 20 pt, stronger the faster the finger moves.
    func drag(_ x: Double, _ y: Double, speed: Double) {
        goLive()
        dragging = true
        last = now
        if hypot(x - lastX, y - lastY) >= 20 {
            emit(x, y, min(0.8, max(0.3, speed / 900)))
            lastX = x
            lastY = y
        }
    }

    func dragEnd(_ g: RipplePondHeatmapGeo, _ y: Double) {
        dragging = false
        last = now
        let r = Int(floor((y - g.grid[1]) / g.grid[3]))
        if r >= 0 && r <= 4 {
            frame.detail = 100 + r
            frame.sel[2] = 0
        }
    }

    func lift() {
        dragging = false
    }

    private func advance(_ dt: Double, _ g: RipplePondHeatmapGeo, auto: Bool, reduced: Bool) {
        now += dt
        if mode == .auto {
            if auto { t = (t + dt).truncatingRemainder(dividingBy: M.loop) }
            frame.script(reduced ? M.reducedAt : t, g)
            return
        }
        frame.phase += dt / M.loop
        for i in 0..<M.sources {
            let a = frame.src[i * 4 + 2]
            if a >= 0 { frame.src[i * 4 + 2] = a + dt > 6 ? -1 : a + dt }
        }
        if mode == .live {
            if auto && !dragging && now - last > M.idleBeforeBack {
                mode = .back
                back = 0
                for i in 0..<M.sources { from[i] = frame.src[i * 4 + 3] }
                from[8] = frame.sel[2]
                from[9] = frame.phase
                frame.detail = -1
            }
            return
        }
        back = min(1, back + dt / M.backFor)
        let k = M.easeInOut(back)
        for i in 0..<M.sources { frame.src[i * 4 + 3] = from[i] * (1 - k) }
        frame.sel[2] = from[8] * (1 - k)
        // The caustics' clock eases on to the next whole loop, where the script picks it up.
        frame.phase = from[9] + (from[9].rounded(.up) - from[9]) * k
        if back >= 1 {
            mode = .auto
            t = 0
            frame.script(0, g)
        }
    }
}

import Foundation
import UIKit

// The wipe trail and the droplets, stepped once a frame from the view's TimelineView and read by
// the pane shader. A step-for-step port of frostMotion.ts.
//
// Points are kept in time order (oldest first), at most `trail` of them. Each has a position, a
// brush radius (negative = first point of a stroke), the frame-clock time it was laid down, and
// when and how fast it refreezes (regrowStart, regrowSpan). Its regrow progress q goes from -0.05
// (clear) to 1.2 (frosted over) across the span. In autoPlay the whole trail is rebuilt each frame
// from the loop time; a touch keeps the trail as it is and adds to it.

enum FrostWipeAlertTuning {
    static let trail = 32
    static let drops = 8
    /// Brush radius (pt), and the travel between two laid points (pt).
    static let brush = 44.0
    static let step = 18.0
    // A finger's wipe stays clear hold s, then refreezes over span s.
    static let hold = 1.2
    static let span = 6.0
    // The script: a zig-zag across the alert 1.4–4.0, clear until 5.4, refrozen by 8.4.
    static let wipe = (1.4, 4.0)
    static let scriptRegrow = 5.4
    static let scriptSpan = 3.0
    static let zigs = 3.5
    // Droplets (s): they hang dropSag s, run down, fade out over the last dropFade s of their life.
    static let dropLife = 2.2
    static let dropSag = 0.3
    static let dropFade = 0.4
    static let scriptDrops = 6
    static let scriptDropAt = 4.2
    static let scriptDropLife = 1.8
    static let idleBeforeBack = 2.0
    static let backFor = 0.9
    /// A frozen frame of the loop shown under reduced motion: the alert wiped clear.
    static let reducedAt = 4.6
}

enum FrostWipeAlertMode {
    case auto, live, back
}

struct FrostWipeAlertFrame {
    /// x, y, brush radius, regrow progress per trail point; unused points are (0, 0, 0, 2).
    var wipe: [Float] = []
    /// x, head y, trail length, radius per droplet.
    var drops: [Float] = []
}

@MainActor
final class FrostWipeAlertEngine {
    private typealias T = FrostWipeAlertTuning

    private static func seg(_ t: Double, _ a: Double, _ b: Double) -> Double {
        min(max((t - a) / (b - a), 0), 1)
    }

    private static func easeInOut(_ x: Double) -> Double {
        x < 0.5 ? 4 * x * x * x : 1 - pow(-2 * x + 2, 3) / 2
    }

    private static func hash(_ i: Double) -> Double {
        let s = sin(i * 12.9898) * 43758.5453
        return s - floor(s)
    }

    /// The scripted zig-zag over the alert card, at progress u (0…1).
    private static func zigzag(_ u: Double, _ box: CGRect) -> (Double, Double) {
        let inset = 26.0
        let left = box.minX + inset
        let right = box.maxX - inset
        let x = (left + right) / 2 - (right - left) / 2 * cos(.pi * T.zigs * u)
        let y = box.minY + 40 + (box.height - 80) * u
        return (x, y)
    }

    private var configured: (autoPlay: Bool, auto: Bool, reduced: Bool)?
    private var auto = false
    private var reduced = false
    private(set) var mode: FrostWipeAlertMode = .auto
    private var autoStart: Date?
    private var last: Date?
    private var t = 0.0
    private var now = 0.0
    private var lastTouch = -100.0
    private var px: [Double] = []
    private var py: [Double] = []
    private var pr: [Double] = []
    private var pt: [Double] = []
    private var rs: [Double] = []
    private var sp: [Double] = []
    private var dx = [Double](repeating: 0, count: FrostWipeAlertTuning.drops)
    private var dy = [Double](repeating: 0, count: FrostWipeAlertTuning.drops)
    private var db = [Double](repeating: -100, count: FrostWipeAlertTuning.drops)
    private var dl = [Double](repeating: FrostWipeAlertTuning.dropLife, count: FrostWipeAlertTuning.drops)
    private var ds = [Double](repeating: 0, count: FrostWipeAlertTuning.drops)
    private var dropNext = 0
    private(set) var wiping = false
    private var pen = false
    private var laid = 0
    private var backAt = 0.0
    private var backK = 0.0
    /// Set when the ease back begins: the view brings the alert back.
    var onBack: () -> Void = {}

    private func configure(autoPlay: Bool, auto: Bool, reduced: Bool) {
        configured = (autoPlay, auto, reduced)
        self.auto = auto
        self.reduced = reduced
        mode = autoPlay ? .auto : .live
        t = 0
        autoStart = nil
        lastTouch = -100
        clearTrail()
        db = Array(repeating: -100, count: T.drops)
        dropNext = 0
        wiping = false
        pen = false
        laid = 0
        backK = 0
    }

    /// True when the pane cannot change until the next touch (the timeline may pause).
    var isIdle: Bool {
        if mode == .auto { return !auto }
        guard mode == .live, !auto, !wiping else { return false }
        let frozen = (0..<px.count).allSatisfy { regrow($0, -1) > 1.6 }
        let dry = (0..<T.drops).allSatisfy { db[$0] < -50 || now - db[$0] >= dl[$0] }
        return frozen && dry
    }

    func step(to date: Date, card: CGRect, autoPlay: Bool, auto: Bool, reduced: Bool) -> FrostWipeAlertFrame {
        if configured?.autoPlay != autoPlay || configured?.auto != auto || configured?.reduced != reduced {
            configure(autoPlay: autoPlay, auto: auto, reduced: reduced)
        }
        let raw = last.map { date.timeIntervalSince($0) } ?? 1.0 / 60
        last = date
        let dt = min(max(raw, 0), 1.0 / 20)
        if card.width > 0 {
            now += dt
            switch mode {
            case .auto:
                if auto {
                    let start = autoStart ?? date
                    autoStart = start
                    t = date.timeIntervalSince(start).truncatingRemainder(dividingBy: FrostWipeAlert.loop)
                }
                scriptFrame(reduced ? T.reducedAt : t, card)
                if reduced { db = Array(repeating: -100, count: T.drops) }
            case .live:
                if auto && !wiping && now - lastTouch > T.idleBeforeBack {
                    beginBack()
                    onBack()
                }
            case .back:
                if backFrame() {
                    mode = .auto
                    t = 0
                    autoStart = date
                }
            }
        }
        var f = FrostWipeAlertFrame()
        let floor = mode == .back ? backK * 1.25 : -1
        f.wipe.reserveCapacity(T.trail * 4)
        for i in 0..<T.trail {
            if i < px.count {
                f.wipe += [Float(px[i]), Float(py[i]), Float(pr[i]), Float(regrow(i, floor))]
            } else {
                f.wipe += [0, 0, 0, 2]
            }
        }
        f.drops.reserveCapacity(T.drops * 4)
        for i in 0..<T.drops {
            let d = dropAt(i, mode == .back ? 1 - backK : 1)
            f.drops += [Float(d.0), Float(d.1), Float(d.2), Float(d.3)]
        }
        return f
    }

    private func clearTrail() {
        px.removeAll(keepingCapacity: true)
        py.removeAll(keepingCapacity: true)
        pr.removeAll(keepingCapacity: true)
        pt.removeAll(keepingCapacity: true)
        rs.removeAll(keepingCapacity: true)
        sp.removeAll(keepingCapacity: true)
    }

    /// Rebuilds the trail and droplets for loop time t (frame-clock times, so a touch can continue).
    private func scriptFrame(_ t: Double, _ box: CGRect) {
        let start = now - t
        clearTrail()
        let step = (T.wipe.1 - T.wipe.0) / Double(T.trail - 1)
        for k in 0..<T.trail {
            let te = T.wipe.0 + Double(k) * step
            if te > t { break }
            // The cosine slows the hand into each turn of the zig-zag.
            let (x, y) = Self.zigzag(Self.seg(te, T.wipe.0, T.wipe.1), box)
            let grow = min((t - te) / 0.12, 1)
            px.append(x)
            py.append(y)
            pr.append((k == 0 ? -1 : 1) * T.brush * (0.4 + 0.6 * grow))
            pt.append(start + te)
            rs.append(start + T.scriptRegrow)
            sp.append(T.scriptSpan)
        }
        for i in 0..<T.drops {
            db[i] = -100
            if i >= T.scriptDrops { continue }
            let di = Double(i)
            let k = floor(Self.hash(di * 3 + 1) * Double(T.trail - 4)) + 2
            let (x, y) = Self.zigzag(k / Double(T.trail - 1), box)
            dx[i] = x + (Self.hash(di * 5 + 2) - 0.5) * T.brush
            dy[i] = y + T.brush * 0.62
            db[i] = start + T.scriptDropAt + di * 0.11
            dl[i] = T.scriptDropLife - di * 0.11
            ds[i] = 5.5 + 2.5 * Self.hash(di * 7 + 3)
        }
    }

    /// Regrow progress of point i at frame time now, raised to at least `floor` while easing back.
    private func regrow(_ i: Int, _ floor: Double) -> Double {
        let q = (now - rs[i]) / sp[i] * 1.25 - 0.05
        return max(q, floor)
    }

    /// A droplet's head (x, y), trail length and radius at frame time now; radius 0 when gone.
    private func dropAt(_ i: Int, _ fade: Double) -> (Double, Double, Double, Double) {
        let age = now - db[i]
        let life = dl[i]
        if age < 0 || age >= life { return (0, 0, 0, 0) }
        let run = max(age - T.dropSag, 0)
        let fall = 95 * pow(run, 1.6)
        let x = dx[i] + 1.6 * sin(run * 6 + Double(i))
        let size = ds[i] * min(age / 0.15, 1) * (1 - Self.seg(age, life - T.dropFade, life)) * fade
        return (x, dy[i] + fall, fall, size)
    }

    /// Lays a point under the finger.
    private func lay(_ x: Double, _ y: Double) {
        if px.count >= T.trail {
            px.removeFirst()
            py.removeFirst()
            pr.removeFirst()
            pt.removeFirst()
            rs.removeFirst()
            sp.removeFirst()
            // The new oldest point no longer has the one before it.
            if !pr.isEmpty { pr[0] = -abs(pr[0]) }
        }
        px.append(x)
        py.append(y)
        pr.append((pen ? 1 : -1) * T.brush)
        pt.append(now)
        rs.append(now + T.hold)
        sp.append(T.span)
        pen = true
        laid += 1
        // Now and then a drop gathers at the lower edge of the wipe.
        let l = Double(laid)
        if Self.hash(l * 1.37) < 0.22 {
            let i = dropNext
            dropNext = (i + 1) % T.drops
            dx[i] = x + (Self.hash(l * 2.1) - 0.5) * T.brush * 0.8
            dy[i] = y + T.brush * 0.62
            db[i] = now + 0.25
            dl[i] = T.dropLife
            ds[i] = 5.5 + 2.5 * Self.hash(l * 3.3)
        }
    }

    private func beginBack() {
        mode = .back
        backAt = now
        backK = 0
        wiping = false
    }

    /// Easing back to the fully frosted rest; true when done.
    private func backFrame() -> Bool {
        backK = Self.easeInOut(min((now - backAt) / T.backFor, 1))
        if backK >= 1 {
            clearTrail()
            db = Array(repeating: -100, count: T.drops)
            backK = 0
            return true
        }
        return false
    }

    /// How clear the pane is at (x, y), as the shader sees it (without the noise).
    func clearAt(_ x: Double, _ y: Double) -> Double {
        var best = 0.0
        for i in 0..<px.count {
            let r = abs(pr[i])
            var d = hypot(x - px[i], y - py[i])
            if i > 0 && pr[i] > 0 {
                let ax = px[i - 1]
                let ay = py[i - 1]
                let bx = px[i] - ax
                let by = py[i] - ay
                let h = min(max(((x - ax) * bx + (y - ay) * by) / max(bx * bx + by * by, 0.0001), 0), 1)
                d = min(d, hypot(x - ax - bx * h, y - ay - by * h))
            }
            let cover = 1 - min(max((d - r * 0.5) / (r * 0.5), 0), 1)
            if cover <= 0 { continue }
            let q = regrow(i, mode == .back ? backK * 1.25 : -1)
            if cover * 0.9 > q { best = max(best, cover) }
        }
        return best
    }

    // MARK: Touch

    func dragBegan(_ p: CGPoint) {
        lastTouch = now
        if mode != .live { mode = .live }
        wiping = true
        pen = false
        lay(p.x, p.y)
    }

    func dragMoved(_ p: CGPoint) {
        lastTouch = now
        if let lx = px.last, let ly = py.last, hypot(p.x - lx, p.y - ly) < T.step { return }
        lay(p.x, p.y)
    }

    func dragEnded() {
        wiping = false
        lastTouch = now
    }
}

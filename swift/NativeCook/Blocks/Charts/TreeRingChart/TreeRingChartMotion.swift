import Foundation
import UIKit

/// Tree Ring Chart — data and the autoPlay script. Every value on screen is a closed-form function
/// of the loop time t (seconds), so the frame at t = loop equals the frame at t = 0.
enum TreeRingChartData {
    static let loop = 9.0

    /// One ring per year of saving; a ring's width is that year's savings.
    static let years = [2019, 2020, 2021, 2022, 2023, 2024, 2025]
    static let amounts: [Double] = [3120, 4860, 6240, 5480, 8420, 7150, 9380]
    static let ringCount = 7
    static let total = amounts.reduce(0, +) // 44,650

    /// The ring selected at rest (the poster frame): 2021.
    static let restSel = 2.0

    // Script (seconds).
    static let shrinkAt = 1.4
    static let shrinkStagger = 0.08
    static let shrinkFor = 0.5
    static let growAt = 2.5
    static let growStep = 3.9 / 7
    static let growFor = 0.62
    static let pickAt = 6.4
    static let pickFor = 1.0

    static func money(_ v: Double) -> String {
        let f = NumberFormatter()
        f.numberStyle = .decimal
        f.locale = Locale(identifier: "en_US")
        f.maximumFractionDigits = 0
        return "$" + (f.string(from: NSNumber(value: v.rounded())) ?? "\(Int(v))")
    }

    static let labels: [String] = years.indices.map { "\(years[$0]) · \(money(amounts[$0]))" }
    static let deltas: [String] = years.indices.map { i in
        if i == 0 { return "First ring · the account opens" }
        let d = Int((((amounts[i] - amounts[i - 1]) / amounts[i - 1]) * 100).rounded())
        return "\(d >= 0 ? "+" : "−")\(abs(d))% on \(years[i - 1])"
    }
}

func treeRingChartClamp01(_ x: Double) -> Double { min(max(x, 0), 1) }

func treeRingChartSeg(_ t: Double, _ a: Double, _ b: Double) -> Double {
    treeRingChartClamp01((t - a) / (b - a))
}

func treeRingChartEaseInOut(_ x: Double) -> Double {
    let t = treeRingChartClamp01(x)
    return t < 0.5 ? 4 * t * t * t : 1 - pow(-2 * t + 2, 3) / 2
}

/// Back-out: rises past 1 (about 7 %) and settles at exactly 1.
func treeRingChartBackOut(_ x: Double) -> Double {
    let t = treeRingChartClamp01(x)
    let c = 1.2
    return 1 + (c + 1) * pow(t - 1, 3) + c * pow(t - 1, 2)
}

/// Ring growth (0 = not there, 1 = full width), the continuous selected ring and the glow.
struct TreeRingChartPose {
    var g: [Double]
    var sel: Double
    var glow: Double

    static func loop(_ t: Double) -> TreeRingChartPose {
        typealias D = TreeRingChartData
        var g: [Double] = []
        g.reserveCapacity(D.ringCount)
        for i in 0..<D.ringCount {
            if t < D.growAt {
                let a = D.shrinkAt + Double(D.ringCount - 1 - i) * D.shrinkStagger
                g.append(1 - treeRingChartEaseInOut(treeRingChartSeg(t, a, a + D.shrinkFor)))
            } else {
                let a = D.growAt + Double(i) * D.growStep
                g.append(treeRingChartBackOut(treeRingChartSeg(t, a, a + D.growFor)))
            }
        }
        var sel = D.restSel
        if t >= D.shrinkAt && t < D.growAt {
            sel = D.restSel * (1 - treeRingChartEaseInOut(treeRingChartSeg(t, D.shrinkAt, D.shrinkAt + 0.8)))
        } else if t >= D.growAt && t < D.pickAt {
            sel = 0
            for i in 1..<D.ringCount {
                let a = D.growAt + Double(i) * D.growStep + 0.05
                sel += treeRingChartEaseInOut(treeRingChartSeg(t, a, a + 0.32))
            }
        } else if t >= D.pickAt {
            sel = Double(D.ringCount - 1) - (Double(D.ringCount - 1) - D.restSel)
                * treeRingChartEaseInOut(treeRingChartSeg(t, D.pickAt, D.pickAt + D.pickFor))
        }
        let dim = treeRingChartEaseInOut(treeRingChartSeg(t, D.shrinkAt, D.shrinkAt + 0.4))
            * (1 - treeRingChartEaseInOut(treeRingChartSeg(t, D.pickAt, D.pickAt + 0.5)))
        return TreeRingChartPose(g: g, sel: sel, glow: 1 - 0.45 * dim)
    }

    /// The finger's pose: s = how far out the slice has grown (0 = pith only, 7 = all rings).
    static func touch(_ s: Double, sel: Double) -> TreeRingChartPose {
        let g = (0..<TreeRingChartData.ringCount).map { i -> Double in
            let u = treeRingChartClamp01(s - Double(i))
            return u * (1 + 0.07 * sin(Double.pi * u))
        }
        return TreeRingChartPose(g: g, sel: sel, glow: 1)
    }

    var grown: Double { g.reduce(0) { $0 + treeRingChartClamp01($1) } }

    var total: Double {
        var v = 0.0
        for i in 0..<TreeRingChartData.ringCount { v += TreeRingChartData.amounts[i] * treeRingChartClamp01(g[i]) }
        return v
    }

    func radii(pith: Double, wood: Double) -> [Double] {
        var out: [Double] = []
        var r = pith
        for i in 0..<TreeRingChartData.ringCount {
            r += (wood - pith) * TreeRingChartData.amounts[i] * max(0, g[i]) / TreeRingChartData.total
            out.append(r)
        }
        return out
    }
}

/// Hero geometry in the hero's own coordinates.
struct TreeRingChartGeo {
    let w: Double
    let h: Double
    let cx: Double
    let cy: Double
    let slice: Double
    let bark: Double
    let pith: Double
    let wood: Double
    let unit: Double

    init(w: Double, h: Double) {
        self.w = w
        self.h = h
        slice = max(60, min(w * 0.41, h / 2 - 30))
        bark = slice * 0.068
        cx = w / 2
        cy = h / 2 - 10
        pith = max(3, slice * 0.025)
        wood = slice - bark
        unit = slice / 161
    }

    var fullRadii: [Double] {
        var out: [Double] = []
        var r = pith
        for i in 0..<TreeRingChartData.ringCount {
            r += (wood - pith) * TreeRingChartData.amounts[i] / TreeRingChartData.total
            out.append(r)
        }
        return out
    }

    /// The finger's distance from the pith, as s (0 at the pith, i + fraction inside ring i).
    func sOfRadius(_ r: Double) -> Double {
        let full = fullRadii
        var prev = pith
        for i in 0..<TreeRingChartData.ringCount {
            if r < full[i] { return Double(i) + treeRingChartClamp01((r - prev) / (full[i] - prev)) }
            prev = full[i]
        }
        let over = (r - full[TreeRingChartData.ringCount - 1]) / 40
        return Double(TreeRingChartData.ringCount) + 0.25 * over / (1 + over)
    }

    /// Middle radius of the (continuous) selected ring.
    func selectedMid(_ radii: [Double], sel: Double) -> Double {
        let n = TreeRingChartData.ringCount
        let i0 = max(0, min(n - 1, Int(floor(sel))))
        let i1 = min(n - 1, i0 + 1)
        let f = treeRingChartClamp01(sel - Double(i0))
        let m0 = ((i0 == 0 ? pith : radii[i0 - 1]) + radii[i0]) / 2
        let m1 = ((i1 == 0 ? pith : radii[i1 - 1]) + radii[i1]) / 2
        return m0 + (m1 - m0) * f
    }
}

/// A value that follows its target with a spring (duration-based, as Reanimated's), or a timed ease.
struct TreeRingChartSpring {
    var x: Double
    var v = 0.0
    var target: Double
    var omega = 10.0
    var zeta = 1.0
    // A timed tween instead of a spring, when tweenFor > 0.
    var tweenFrom = 0.0
    var tweenAt = 0.0
    var tweenFor = 0.0

    init(_ x: Double) {
        self.x = x
        target = x
    }

    mutating func spring(to target: Double, duration: Double, damping: Double) {
        self.target = target
        zeta = damping
        omega = (damping >= 0.99 ? 5.8 : 4 / damping) / duration
        tweenFor = 0
    }

    mutating func tween(to target: Double, duration: Double, now: Double) {
        self.target = target
        tweenFrom = x
        tweenAt = now
        tweenFor = duration
        v = 0
    }

    mutating func set(_ value: Double) {
        x = value
        target = value
        v = 0
        tweenFor = 0
    }

    var settled: Bool { tweenFor == 0 && abs(x - target) < 0.001 && abs(v) < 0.01 }

    /// Returns true when a tween ends on this step.
    mutating func step(_ dt: Double, now: Double) -> Bool {
        if tweenFor > 0 {
            let k = treeRingChartClamp01((now - tweenAt) / tweenFor)
            x = tweenFrom + (target - tweenFrom) * treeRingChartBezier(k)
            if k >= 1 {
                tweenFor = 0
                x = target
                return true
            }
            return false
        }
        if settled { x = target; v = 0; return false }
        var left = dt
        while left > 0 {
            let h = min(left, 1.0 / 240)
            let a = -omega * omega * (x - target) - 2 * zeta * omega * v
            v += a * h
            x += v * h
            left -= h
        }
        return false
    }
}

/// cubic-bezier(0.77, 0, 0.175, 1), the ease-in-out of the return to rest.
func treeRingChartBezier(_ x: Double) -> Double {
    let x1 = 0.77, y1 = 0.0, x2 = 0.175, y2 = 1.0
    func bx(_ t: Double) -> Double { 3 * (1 - t) * (1 - t) * t * x1 + 3 * (1 - t) * t * t * x2 + t * t * t }
    func by(_ t: Double) -> Double { 3 * (1 - t) * (1 - t) * t * y1 + 3 * (1 - t) * t * t * y2 + t * t * t }
    var lo = 0.0, hi = 1.0, t = x
    for _ in 0..<24 {
        t = (lo + hi) / 2
        if bx(t) < x { lo = t } else { hi = t }
    }
    return by(t)
}

/// The loop clock and the finger's springs, stepped once a frame from the timeline.
@MainActor
final class TreeRingChartModel {
    private typealias D = TreeRingChartData

    var auto = false
    var reduced = false
    private(set) var t = 0.0
    private(set) var manual = true
    private var s = TreeRingChartSpring(Double(TreeRingChartData.ringCount))
    private var sel = TreeRingChartSpring(TreeRingChartData.restSel)
    private var picked = -1
    private var clock = 0.0
    private var resumeAt = -1.0
    private var returning = false
    private var last: Date?
    private var origin: Date?
    private var cached = TreeRingChartPose.loop(0)
    private let haptic = UISelectionFeedbackGenerator()

    func configure(auto: Bool, reduced: Bool) {
        guard auto != self.auto || reduced != self.reduced || last == nil else { return }
        self.auto = auto
        self.reduced = reduced
        manual = !auto
        t = 0
        origin = nil
    }

    /// Steps once per distinct date; several timelines can read the same frame.
    func pose(at date: Date) -> TreeRingChartPose {
        if let last, date == last { return cached }
        let raw = last.map { date.timeIntervalSince($0) } ?? 0
        last = date
        let dt = min(max(raw, 0), 0.05)
        clock += dt
        if !manual {
            // The loop reads the timeline's date, not a sum of frame steps, so it keeps its period.
            if origin == nil { origin = date.addingTimeInterval(-t) }
            t = date.timeIntervalSince(origin!).truncatingRemainder(dividingBy: D.loop)
            cached = TreeRingChartPose.loop(t)
            return cached
        }
        origin = nil
        if resumeAt > 0 && clock >= resumeAt {
            resumeAt = -1
            backToRest()
        }
        _ = s.step(dt, now: clock)
        if sel.step(dt, now: clock) && returning {
            returning = false
            if auto {
                t = 0
                manual = false
                picked = -1
                cached = TreeRingChartPose.loop(0)
                return cached
            }
        }
        cached = TreeRingChartPose.touch(s.x, sel: sel.x)
        return cached
    }

    /// True while something still moves without a finger (for pausing the timeline).
    var moving: Bool { !manual || resumeAt > 0 || returning || !s.settled || !sel.settled }

    private func backToRest() {
        returning = true
        s.tween(to: Double(D.ringCount), duration: 0.8, now: clock)
        sel.tween(to: D.restSel, duration: 0.8, now: clock)
    }

    private func takeOver() {
        resumeAt = -1
        returning = false
        if !manual {
            let p = TreeRingChartPose.loop(t)
            s.set(p.grown)
            sel.set(p.sel)
            manual = true
        }
    }

    private func pick(_ target: Int) {
        if target == picked { return }
        picked = target
        if reduced {
            sel.tween(to: Double(target), duration: 0.18, now: clock)
        } else {
            sel.spring(to: Double(target), duration: 0.38, damping: 0.82)
        }
        haptic.selectionChanged()
    }

    func sliceBegan(_ p: CGPoint, geo: TreeRingChartGeo) {
        takeOver()
        sliceMoved(p, geo: geo)
    }

    func sliceMoved(_ p: CGPoint, geo: TreeRingChartGeo) {
        let reach = geo.sOfRadius(hypot(p.x - geo.cx, p.y - geo.cy))
        if reduced { s.set(reach) } else { s.spring(to: reach, duration: 0.26, damping: 1) }
        pick(max(0, min(D.ringCount - 1, Int(floor(reach)))))
    }

    func sliceEnded() {
        let target = Double(max(0, picked))
        if reduced { s.set(target + 1) } else { s.spring(to: min(Double(D.ringCount), target + 1), duration: 0.45, damping: 0.7) }
        if auto { resumeAt = clock + 2 }
    }

    func rowBegan(_ x: Double, step: Double) {
        takeOver()
        rowMoved(x, step: step)
    }

    func rowMoved(_ x: Double, step: Double) {
        if reduced { s.set(Double(D.ringCount)) } else { s.spring(to: Double(D.ringCount), duration: 0.45, damping: 0.85) }
        pick(max(0, min(D.ringCount - 1, Int(floor(x / step)))))
    }

    func rowEnded() {
        if auto { resumeAt = clock + 2 }
    }
}

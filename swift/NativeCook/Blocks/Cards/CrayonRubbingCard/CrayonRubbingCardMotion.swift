import Foundation
import CoreGraphics

/// Layout, the crayon's scripted path and the rubbed-field grid of the Crayon Rubbing Card. The grid
/// is gx × gy cells over the paper, 0 (untouched) … 1 (fully rubbed); the shader reads it as a
/// smooth image. Every frame of the loop is a pure function of the loop time.
enum CrayonRubbingCardMotion {
    /// Loop period in seconds.
    static let loop = 9.0

    static let designW = 393.0
    static let designH = 759.0

    /// The hero canvas (design points) and, inside it, the bronze plaque and the paper over it.
    static let canvas = CGRect(x: 0, y: 104, width: 393, height: 500)
    static let plaque = CGRect(x: 24, y: 8, width: 345, height: 432)
    static let paper = CGRect(x: 34, y: 16, width: 325, height: 416)
    /// Where the crayon lies when nobody rubs (canvas points: its tip, and its angle in degrees).
    static let crayonRest = (x: 176.0, y: 474.0, angle: -8.0)
    /// Cell size of the rubbed field, in points.
    static let cell = 8.0
    static let gx = Int((paper.width / cell).rounded(.up))
    static let gy = Int((paper.height / cell).rounded(.up))
    /// The crayon's side is this wide on the paper (radius of what one stroke covers).
    static let reach = 17.0
    private static let strokes = 14.0
    private static let passes = 4

    static func clamp01(_ x: Double) -> Double { x < 0 ? 0 : x > 1 ? 1 : x }
    static func seg(_ t: Double, _ a: Double, _ b: Double) -> Double { clamp01((t - a) / (b - a)) }
    static func easeOutCubic(_ x: Double) -> Double { 1 - pow(1 - x, 3) }
    static func easeInOutCubic(_ x: Double) -> Double { x < 0.5 ? 4 * x * x * x : 1 - pow(-2 * x + 2, 3) / 2 }

    /// The demo's scrubbing hand in paper points at a time inside the scrub (0.35–4.0 s): a zig-zag
    /// of 14 strokes top to bottom, then a quick circling over the heron to deepen it.
    static func scrubAt(_ t: Double) -> CGPoint {
        if t < 3.4 {
            let s = seg(t, 0.35, 3.4)
            let y = 22 + (paper.height - 44) * s
            let x = paper.width / 2 + (paper.width / 2 - 24) * sin(Double.pi * strokes * s - Double.pi / 2)
            return CGPoint(x: x, y: y + 6 * sin(Double.pi * 2 * strokes * s))
        }
        let u = seg(t, 3.4, 4.0)
        let cx = 176 - 30 * (1 - u)
        let cy = 190 + 150 * pow(1 - u, 2)
        return CGPoint(x: cx + 34 * cos(Double.pi * 6 * u + Double.pi), y: cy + 22 * sin(Double.pi * 6 * u))
    }

    /// The crayon's tip, angle and contact at loop time t, in canvas points.
    static func crayonAt(_ t: Double) -> (x: Double, y: Double, angle: Double, contact: Double) {
        let start = scrubAt(0.35)
        let end = scrubAt(4.0)
        let sx = paper.minX + start.x, sy = paper.minY + start.y
        let ex = paper.minX + end.x, ey = paper.minY + end.y
        let r = crayonRest
        if t < 0.35 {
            let u = easeInOutCubic(seg(t, 0, 0.35))
            let lift = sin(Double.pi * u) * 26
            return (r.x + (sx - r.x) * u, r.y + (sy - r.y) * u - lift, r.angle + (-34 - r.angle) * u, 0)
        }
        if t < 4.0 {
            let p = scrubAt(t)
            let sway = 6 * sin(Double.pi * strokes * seg(t, 0.35, 3.4))
            return (paper.minX + p.x, paper.minY + p.y, -34 + sway, 1)
        }
        let u = easeInOutCubic(seg(t, 4.0, 4.55))
        let lift = sin(Double.pi * u) * 26
        return (ex + (r.x - ex) * u, ey + (r.y - ey) * u - lift, -34 + (r.angle + 34) * u, 0)
    }

    /// The direction of the demo's last stroke at t (the wax's grain), in radians.
    static func autoGrain(_ t: Double) -> Double {
        let at = min(max(t, 0.35), 3.999)
        let a = crayonAt(at), b = crayonAt(min(at + 1 / 60, loop - 1e-3))
        return atan2(b.y - a.y, b.x - a.x)
    }

    /// The rubbed paper peels off the plaque 7.3–8.75 s, leaving a fresh sheet.
    static func autoPeel(_ t: Double) -> Double { easeInOutCubic(seg(t, 7.3, 8.75)) }

    /// The "Collected" badge: in 4.0–4.6 s (a spring-like pop), out as the sheet peels.
    static func autoBadge(_ t: Double) -> Double {
        let u = seg(t, 4.0, 4.6)
        let pop = u >= 1 ? 1 : 1 - exp(-u * 6) * cos(u * 9)
        return pop * (1 - seg(t, 7.3, 7.7))
    }

    /// When the demo's crayon passes over each cell (up to `passes` start times, -1 when unused) and
    /// how hard each pass pressed, worked out once by following the path at 240 Hz.
    static let passTable: (times: [Double], amounts: [Double]) = {
        var times = [Double](repeating: -1, count: gx * gy * passes)
        var amounts = [Double](repeating: 0, count: gx * gy * passes)
        var last = [Double](repeating: -10, count: gx * gy)
        let r = Int((reach / cell).rounded(.up))
        var t = 0.35
        while t <= 4.0 {
            let p = scrubAt(t)
            let cx = p.x / cell, cy = p.y / cell
            for y in Int(floor(cy - Double(r)))...Int(ceil(cy + Double(r))) {
                for x in Int(floor(cx - Double(r)))...Int(ceil(cx + Double(r))) {
                    if x < 0 || y < 0 || x >= gx || y >= gy { continue }
                    let d = hypot((Double(x) + 0.5) * cell - p.x, (Double(y) + 0.5) * cell - p.y)
                    if d > reach { continue }
                    let i = y * gx + x
                    if t - last[i] > 0.12 {
                        for k in 0..<passes where times[i * passes + k] < 0 {
                            times[i * passes + k] = t
                            amounts[i * passes + k] = (0.74 + 0.16 * sin(Double(i) * 1.7 + Double(k) * 2.3)) * (1 - 0.35 * (d / reach))
                            break
                        }
                    }
                    last[i] = t
                }
            }
            t += 1.0 / 240
        }
        return (times, amounts)
    }()

    /// The demo's rubbed field at loop time t (all clear once the sheet is gone).
    static func autoGrid(_ t: Double, into grid: inout [Double]) {
        let fresh = t >= 8.75
        let table = passTable
        for i in 0..<(gx * gy) {
            var v = 0.0
            if !fresh {
                for k in 0..<passes {
                    let at = table.times[i * passes + k]
                    if at < 0 { break }
                    v += table.amounts[i * passes + k] * clamp01((t - at) / 0.09)
                }
            }
            grid[i] = min(v, 1)
        }
    }

    /// Rubs along a finger's segment (paper points): cells under the crayon's side darken with travel.
    static func rubSegment(_ grid: inout [Double], _ x0: Double, _ y0: Double, _ x1: Double, _ y1: Double) {
        let len = hypot(x1 - x0, y1 - y0)
        let steps = max(1, Int((len / 4).rounded(.up)))
        let r = Int((reach / cell).rounded(.up))
        for s in 1...steps {
            let px = x0 + (x1 - x0) * Double(s) / Double(steps)
            let py = y0 + (y1 - y0) * Double(s) / Double(steps)
            let cx = px / cell, cy = py / cell
            for y in Int(floor(cy - Double(r)))...Int(ceil(cy + Double(r))) {
                for x in Int(floor(cx - Double(r)))...Int(ceil(cx + Double(r))) {
                    if x < 0 || y < 0 || x >= gx || y >= gy { continue }
                    let d = hypot((Double(x) + 0.5) * cell - px, (Double(y) + 0.5) * cell - py)
                    if d > reach { continue }
                    let i = y * gx + x
                    grid[i] = min(1, grid[i] + 0.05 * (1 - 0.35 * (d / reach)))
                }
            }
        }
    }

    /// Share of the relief revealed: the rubbed field weighted by how much relief each cell holds.
    static func revealed(_ grid: [Double], _ weights: [Double], _ total: Double) -> Double {
        if total <= 0 { return 0 }
        var sum = 0.0
        for i in 0..<(gx * gy) { sum += grid[i] * weights[i] }
        return sum / total
    }

    /// The percentage the demo shows over one loop, sampled at 60 Hz: the revealed share rounded to
    /// whole numbers and followed by a spring (damping 20, stiffness 180), run for two loops so the
    /// table is the loop's steady state and its end meets its start.
    static func shownTable(weights: [Double], total: Double) -> [Double] {
        let n = Int(loop * 60)
        var grid = [Double](repeating: 0, count: gx * gy)
        var targets = [Double](repeating: 0, count: n)
        for i in 0..<n {
            autoGrid(Double(i) / 60, into: &grid)
            targets[i] = min(99, (revealed(grid, weights, total) * 100).rounded())
        }
        var x = 0.0, v = 0.0
        var out = [Double](repeating: 0, count: n)
        let dt = 1.0 / 60
        for pass in 0..<2 {
            for i in 0..<n {
                for _ in 0..<8 {
                    let a = -180 * (x - targets[i]) - 20 * v
                    v += a * dt / 8
                    x += v * dt / 8
                }
                if pass == 1 { out[i] = x }
            }
        }
        return out
    }
}

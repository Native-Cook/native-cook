import Foundation

// The paint floating on the size: a stack of closed outlines, one per drop, oldest first. A port,
// step for step, of the React Native `marblingSim.ts`; see there for the maths.
//
// Every operation is applied once, forward, to every outline's vertices (the marbling maps of Lu,
// Jaffer et al.), so nothing is stored or replayed:
//
// - A drop of radius r at c is circle-area preserving: every point p moves out to
//   c + (p − c)·√(1 + r² / |p − c|²). A spreading drop is the same map in increments of r².
// - A tine moving by v pushes every point by v·G·u^d, d being the point's distance from the tine's
//   path, u = 2^(−1/c) (Jaffer's tine line), fading behind the tine over `wake` pt.
//
// After each step every touched outline is refined: long edges split, points on straight runs
// dropped, points far off screen removed. An outline wholly off screen is deleted, and one that
// surrounds the whole screen becomes the ground colour. A vertex budget coarsens the refinement when
// the pattern grows very fine, so the cost of a frame stays bounded. Plain arithmetic on arrays with
// a fixed tick, scratch buffers reused between ticks.

/// One drop's outline (closed polygon) and its colour, an index into the palette.
final class MarblingCombBlot {
    let id: Int
    var x: [Double]
    var y: [Double]
    let color: Int
    /// Bumped every time the outline changes, so a drawing of it can be cached.
    var rev = 0
    var moved = true
    /// Bounding box, refreshed whenever the outline is refined.
    var minX: Double
    var minY: Double
    var maxX: Double
    var maxY: Double

    init(id: Int, x: [Double], y: [Double], color: Int, minX: Double, minY: Double, maxX: Double, maxY: Double) {
        self.id = id
        self.x = x
        self.y = y
        self.color = color
        self.minX = minX
        self.minY = minY
        self.maxX = maxX
        self.maxY = maxY
    }
}

/// A tine (stylus) or a row of tines (comb).
struct MarblingCombTool {
    /// Distance between tines along the comb's axis, pt (ignored for one tine).
    var spacing: Double
    /// Tines on each side of the middle one: 0 for a stylus.
    var side: Double
    /// The comb's axis (unit vector): its tines sit along it.
    var axisX: Double
    var axisY: Double
    /// Distance over which the push halves, pt (Jaffer's c).
    var sharpness: Double
    /// Share of the tine's motion the paint right at the tine follows.
    var gain: Double
    /// Distance behind the tine over which its push fades, pt.
    var wake: Double
}

/// The numbers of the simulation.
enum MarblingCombSimTuning {
    // Longest edge on screen, pt, and the coarsest it may grow to under the budget.
    static let segment = 6.0
    static let segmentMax = 14.0
    // A point on a straight run is dropped when it lies within this distance of the chord, pt.
    static let flatness = 0.08
    // Points closer than this to the last one kept are dropped, pt.
    static let minGap = 0.6
    // Total vertices aimed for.
    static let budget = 16000
    // Hard limits, past the budget's coarsening: beyond them the oldest outline (the bottom of the
    // stack) is folded into the ground, so neither the outlines nor their vertices grow without bound.
    static let hardBudget = 40000
    static let maxBlots = 160
    // An edge is never split into more parts than this.
    static let maxSplit = 256
    // Margin around the screen within which outlines are kept exact, pt.
    static let margin = 48.0
    // The tine's push is ignored past this many halvings, and past this many wake lengths behind it.
    static let reach = 9.0
    static let wakeReach = 4.0
}

/// `Math.round` of JavaScript: halves round up.
@inline(__always) func marblingCombRound(_ v: Double) -> Double {
    (v + 0.5).rounded(.down)
}

final class MarblingCombMarble {
    private typealias T = MarblingCombSimTuning

    /// A drop still spreading: its centre, final radius and how far through its spread it is.
    private struct Spread {
        let id: Int
        let cx: Double
        let cy: Double
        let radius: Double
        let color: Int
        var age: Double
        let time: Double
        /// r² already applied.
        var done: Double
    }

    /// The tray, refreshed every frame so it follows the layout.
    var width: Double
    var height: Double
    private(set) var blots: [MarblingCombBlot] = []
    private var spreads: [Spread] = []
    private(set) var ground: Int
    private var nextId = 1
    /// Bumped whenever any outline changes or one is added or removed.
    private(set) var rev = 1
    /// Longest edge allowed on screen, pt: grows past `segment` when the budget is exceeded.
    private(set) var segment = MarblingCombSimTuning.segment
    private(set) var vertices = 0

    // Scratch buffers, reused every tick.
    private var batchX: [Double] = []
    private var batchY: [Double] = []
    private var batchR2: [Double] = []
    private var outX: [Double] = []
    private var outY: [Double] = []

    init(width: Double, height: Double, ground: Int) {
        self.width = width
        self.height = height
        self.ground = ground
    }

    /// The spread of a drop from 0 to 1 over its time: fast at first, then slowing (cubic ease-out on its area).
    private static func spreadAt(_ t: Double) -> Double {
        let k = min(1, max(0, t))
        let r = 1 - k
        return 1 - r * r * r
    }

    /// Clears the tray to its ground colour.
    func clear(ground: Int) {
        blots.removeAll()
        spreads.removeAll()
        self.ground = ground
        segment = T.segment
        vertices = 0
        rev += 1
    }

    /// Drops paint at (x, y): it spreads to `radius` over `time` s.
    func drop(x: Double, y: Double, radius: Double, color: Int, time: Double) {
        spreads.append(Spread(id: nextId, cx: x, cy: y, radius: radius, color: color, age: 0, time: max(time, 1e-3), done: 0))
        nextId += 1
    }

    /// The drop map for the drops in the batch, applied to each point in order.
    private func pushOutBatch() {
        let count = batchR2.count
        for blot in blots {
            for i in 0..<blot.x.count {
                var x = blot.x[i]
                var y = blot.y[i]
                for k in 0..<count {
                    let dx = x - batchX[k]
                    let dy = y - batchY[k]
                    let d2 = dx * dx + dy * dy
                    if d2 < 1e-9 { continue }
                    let f = (1 + batchR2[k] / d2).squareRoot()
                    x = batchX[k] + dx * f
                    y = batchY[k] + dy * f
                }
                blot.x[i] = x
                blot.y[i] = y
            }
            blot.moved = true
        }
    }

    /// Spreads every growing drop by one tick, all of them applied to each point in turn, oldest first.
    private func spreadDrops(_ dt: Double) {
        batchX.removeAll(keepingCapacity: true)
        batchY.removeAll(keepingCapacity: true)
        batchR2.removeAll(keepingCapacity: true)
        for i in spreads.indices {
            spreads[i].age += dt
            let s = spreads[i]
            let target = s.radius * s.radius * Self.spreadAt(s.age / s.time)
            let r2 = target - s.done
            if r2 <= 0 { continue }
            if s.done == 0 {
                // A new drop: the outlines so far make room first, then its own outline appears at its first radius.
                if !batchR2.isEmpty { pushOutBatch() }
                batchX.removeAll(keepingCapacity: true)
                batchY.removeAll(keepingCapacity: true)
                batchR2.removeAll(keepingCapacity: true)
                batchX.append(s.cx)
                batchY.append(s.cy)
                batchR2.append(r2)
                pushOutBatch()
                batchX.removeAll(keepingCapacity: true)
                batchY.removeAll(keepingCapacity: true)
                batchR2.removeAll(keepingCapacity: true)
                let r = r2.squareRoot()
                let n = max(40, Int((Double.pi * 2 * s.radius / T.segment).rounded(.up)))
                var xs = [Double]()
                var ys = [Double]()
                xs.reserveCapacity(n)
                ys.reserveCapacity(n)
                for k in 0..<n {
                    let a = (Double(k) / Double(n)) * Double.pi * 2
                    xs.append(s.cx + cos(a) * r)
                    ys.append(s.cy + sin(a) * r)
                }
                blots.append(MarblingCombBlot(
                    id: s.id, x: xs, y: ys, color: s.color,
                    minX: s.cx - r, minY: s.cy - r, maxX: s.cx + r, maxY: s.cy + r
                ))
            } else {
                batchX.append(s.cx)
                batchY.append(s.cy)
                batchR2.append(r2)
            }
            spreads[i].done = target
        }
        if !batchR2.isEmpty { pushOutBatch() }
        spreads.removeAll { $0.age >= $0.time }
    }

    /// Moves a tool from (ax, ay) to (bx, by), dragging the paint around each of its tines.
    func moveTool(_ tool: MarblingCombTool, _ ax: Double, _ ay: Double, _ bx: Double, _ by: Double) {
        let vx = bx - ax
        let vy = by - ay
        let len = (vx * vx + vy * vy).squareRoot()
        if len < 1e-4 { return }
        let mx = vx / len
        let my = vy / len
        let k = M_LN2 / tool.sharpness
        let reach = T.reach * tool.sharpness
        let behind = len + tool.wake * T.wakeReach
        let gx = vx * tool.gain
        let gy = vy * tool.gain
        let ax2 = tool.axisX
        let ay2 = tool.axisY
        let spacing = max(1, tool.spacing)
        let side = tool.side
        let invWake = 1 / tool.wake
        for blot in blots {
            // Skip an outline whose box lies wholly ahead of the tool's reach or wholly behind its wake
            // (and, for one tine, wholly to one side of it).
            var lo = Double.infinity
            var hi = -Double.infinity
            var left = Double.infinity
            var right = -Double.infinity
            for c in 0..<4 {
                let cx = (c & 1 != 0 ? blot.maxX : blot.minX) - bx
                let cy = (c & 2 != 0 ? blot.maxY : blot.minY) - by
                let along = cx * mx + cy * my
                let across = cx * my - cy * mx
                if along < lo { lo = along }
                if along > hi { hi = along }
                if across < left { left = across }
                if across > right { right = across }
            }
            if lo > reach || hi < -behind { continue }
            if side == 0 && (left > reach || right < -reach) { continue }
            var moved = false
            blot.x.withUnsafeMutableBufferPointer { xs in
                blot.y.withUnsafeMutableBufferPointer { ys in
                    for i in 0..<xs.count {
                        var rx = xs[i] - bx
                        var ry = ys[i] - by
                        var along = rx * mx + ry * my
                        if along > reach + spacing || along < -behind - spacing { continue }
                        if side > 0 {
                            // The nearest tine along the comb's axis.
                            var j = marblingCombRound((rx * ax2 + ry * ay2) / spacing)
                            if j > side { j = side } else if j < -side { j = -side }
                            rx -= ax2 * j * spacing
                            ry -= ay2 * j * spacing
                            along = rx * mx + ry * my
                            if along > reach || along < -behind { continue }
                        }
                        var across = rx * my - ry * mx
                        if across < 0 { across = -across }
                        if across > reach { continue }
                        // Ahead of the tine the push falls off with distance, beside its path with the
                        // distance across, and behind it also over the wake.
                        let w: Double
                        if along > 0 {
                            w = exp(-k * (along * along + across * across).squareRoot())
                        } else if along >= -len {
                            w = exp(-k * across)
                        } else {
                            w = exp(-k * across + (along + len) * invWake)
                        }
                        if w < 2e-4 { continue }
                        xs[i] += gx * w
                        ys[i] += gy * w
                        moved = true
                    }
                }
            }
            if moved { blot.moved = true }
        }
    }

    /// Which sides of the kept area a point lies outside of, as bits (left 1, right 2, top 4, bottom 8).
    @inline(__always) private func outside(_ x: Double, _ y: Double) -> Int {
        var code = 0
        if x < -T.margin { code |= 1 } else if x > width + T.margin { code |= 2 }
        if y < -T.margin { code |= 4 } else if y > height + T.margin { code |= 8 }
        return code
    }

    /// Whether (x, y) lies inside the outline (even–odd rule).
    private static func contains(_ blot: MarblingCombBlot, _ x: Double, _ y: Double) -> Bool {
        let xs = blot.x
        let ys = blot.y
        var inside = false
        var j = xs.count - 1
        for i in 0..<xs.count {
            if (ys[i] > y) != (ys[j] > y) && x < (xs[j] - xs[i]) * (y - ys[i]) / (ys[j] - ys[i]) + xs[i] {
                inside.toggle()
            }
            j = i
        }
        return inside
    }

    /// Splits long edges and drops redundant points.
    private func refine(_ blot: MarblingCombBlot) {
        let xs = blot.x
        let ys = blot.y
        let n = xs.count
        if n < 3 { return }
        let seg = segment
        let flat = T.flatness * (seg / T.segment)
        outX.removeAll(keepingCapacity: true)
        outY.removeAll(keepingCapacity: true)
        var lastX = xs[0]
        var lastY = ys[0]
        var lastCode = outside(lastX, lastY)
        outX.append(lastX)
        outY.append(lastY)
        for i in 1...n {
            let closing = i == n
            let px = closing ? xs[0] : xs[i]
            let py = closing ? ys[0] : ys[i]
            let code = outside(px, py)
            if !closing {
                let nx = xs[(i + 1) % n]
                let ny = ys[(i + 1) % n]
                let nextCode = outside(nx, ny)
                // Outside the same side as the point before and after it: the chord stays outside too.
                if lastCode & code & nextCode != 0 { continue }
                let cx = nx - lastX
                let cy = ny - lastY
                let chord = (cx * cx + cy * cy).squareRoot()
                let gx = px - lastX
                let gy = py - lastY
                let gap = (gx * gx + gy * gy).squareRoot()
                if gap < T.minGap { continue }
                // On a straight run, short enough to stand for: the point adds nothing.
                if chord < seg && chord > 1e-6 && abs((px - lastX) * cy - (py - lastY) * cx) / chord < flat { continue }
            }
            // Split the edge from the last point kept when it is long and may be on screen.
            if lastCode & code == 0 {
                let ex = px - lastX
                let ey = py - lastY
                let len = (ex * ex + ey * ey).squareRoot()
                if len > seg && len.isFinite {
                    let parts = min(T.maxSplit, Int((len / seg).rounded(.up)))
                    for s in 1..<parts {
                        let t = Double(s) / Double(parts)
                        outX.append(lastX + (px - lastX) * t)
                        outY.append(lastY + (py - lastY) * t)
                    }
                }
            }
            if closing { break }
            outX.append(px)
            outY.append(py)
            lastX = px
            lastY = py
            lastCode = code
        }
        // The old arrays become the next scratch buffers.
        swap(&blot.x, &outX)
        swap(&blot.y, &outY)
    }

    /// Refines what moved, deletes what left the screen and folds a screen-covering outline into the ground.
    private func tidy() {
        var total = 0
        var changed = false
        var b = blots.count - 1
        while b >= 0 {
            let blot = blots[b]
            if !blot.moved {
                total += blot.x.count
                b -= 1
                continue
            }
            blot.moved = false
            blot.rev += 1
            changed = true
            refine(blot)
            var all = 15
            var any = 0
            var inView = false
            var minX = Double.infinity
            var minY = Double.infinity
            var maxX = -Double.infinity
            var maxY = -Double.infinity
            let count = blot.x.count
            for i in 0..<count {
                let x = blot.x[i]
                let y = blot.y[i]
                if x < minX { minX = x }
                if x > maxX { maxX = x }
                if y < minY { minY = y }
                if y > maxY { maxY = y }
                let c = outside(x, y)
                all &= c
                if c == 0 { inView = true }
                any |= c
            }
            blot.minX = minX
            blot.minY = minY
            blot.maxX = maxX
            blot.maxY = maxY
            if !inView {
                if all == 0 && any != 0 && Self.contains(blot, width / 2, height / 2) {
                    // It covers the whole screen: it is the ground now, and nothing under it shows.
                    ground = blot.color
                    blots.removeFirst(b + 1)
                    break
                }
                blots.remove(at: b)
                b -= 1
                continue
            }
            total += count
            b -= 1
        }
        // Too many outlines or vertices even at the coarsest edges: the bottom one becomes the ground.
        while blots.count > 1 && (blots.count > T.maxBlots || total > T.hardBudget) {
            let bottom = blots.removeFirst()
            ground = bottom.color
            total -= bottom.x.count
            changed = true
        }
        if changed { rev += 1 }
        vertices = total
        // Coarsen the outlines while the pattern is over budget; refine again once it is not.
        if total > T.budget {
            segment = min(T.segmentMax, segment * 1.08)
        } else if Double(total) < Double(T.budget) * 0.7 {
            segment = max(T.segment, segment / 1.02)
        }
    }

    /// One fixed tick: drops spread, then everything that moved is refined. Tools move through `moveTool`.
    func step(_ dt: Double) {
        if !spreads.isEmpty { spreadDrops(dt) }
        tidy()
    }
}

// MARK: - The self-playing script

/// A small, fast, seeded random in [0, 1): xorshift32.
func marblingCombSeeded(_ state: inout UInt32) -> Double {
    var x = state
    x ^= x << 13
    x ^= x >> 17
    x ^= x << 5
    state = x
    return Double(x) / 4294967296
}

/// One loop, `loop` s long, from a clean tray back to a clean tray: a sprinkle of drops in a loose
/// grid, a few drops on drops for rings, a coarse comb down and back up (gel-git), a fine comb across
/// (nonpareil), then a print. Positions are shares of the tray; radii shares of its width.
enum MarblingCombScript {
    static let loop = 16.0
    /// When the sheet is laid on.
    static let printAt = 11.2

    struct Drop {
        let at: Double
        let x: Double
        let y: Double
        let r: Double
        let color: Int
    }

    /// A comb pulled in a straight line from (x0, y0) to (x1, y1) between two times.
    struct Pass {
        let from: Double
        let to: Double
        let x0: Double
        let y0: Double
        let x1: Double
        let y1: Double
        /// The comb's axis is horizontal (tines side by side across the screen) or vertical.
        let horizontal: Bool
        /// Tine spacing as a share of the width.
        let spacing: Double
        let sharpness: Double
        let gain: Double
        let wake: Double
    }

    // Colours cycled by the sprinkle (indices into the palette; 0 is the ground).
    private static let sprinkleColors = [1, 2, 3, 4, 6, 5, 1, 3, 2, 7, 4, 1]
    private static let gridCols = 4
    private static let gridRows = 6
    private static let sprinkleStart = 0.35
    private static let sprinkleGap = 0.12

    static let drops: [Drop] = {
        var state: UInt32 = 0x5eed1234
        var cells = Array(0..<(gridCols * gridRows))
        // A fixed shuffle, so the sprinkle lands in a lively order.
        var i = cells.count - 1
        while i > 0 {
            let j = Int((marblingCombSeeded(&state) * Double(i + 1)).rounded(.down))
            cells.swapAt(i, j)
            i -= 1
        }
        var drops: [Drop] = []
        for (k, cell) in cells.enumerated() {
            let col = cell % gridCols
            let row = cell / gridCols
            let stagger = row % 2 == 0 ? 0.0 : 0.5
            let sx = marblingCombSeeded(&state)
            let sy = marblingCombSeeded(&state)
            let sr = marblingCombSeeded(&state)
            drops.append(Drop(
                at: sprinkleStart + Double(k) * sprinkleGap,
                x: (Double(col) + 0.25 + stagger * 0.5 + sx * 0.3) / Double(gridCols),
                y: (Double(row) + 0.3 + sy * 0.4) / Double(gridRows),
                r: 0.15 + sr * 0.03,
                color: sprinkleColors[k % sprinkleColors.count]
            ))
        }
        // Drops on drops: rings at three spots.
        let rings: [(Double, Double, Int, Int)] = [(0.3, 0.3, 3, 2), (0.72, 0.52, 1, 7), (0.38, 0.76, 6, 4)]
        for (k, ring) in rings.enumerated() {
            let at = 3.5 + Double(k) * 0.32
            drops.append(Drop(at: at, x: ring.0, y: ring.1, r: 0.085, color: ring.2))
            drops.append(Drop(at: at + 0.16, x: ring.0, y: ring.1, r: 0.06, color: ring.3))
        }
        return drops
    }()

    static let passes: [Pass] = [
        // Gel-git: a coarse comb down the tray, then back up between its first lines.
        Pass(from: 4.9, to: 6.7, x0: 0.5, y0: -0.08, x1: 0.5, y1: 1.08, horizontal: true, spacing: 0.15, sharpness: 9, gain: 0.8, wake: 70),
        Pass(from: 6.95, to: 8.75, x0: 0.575, y0: 1.08, x1: 0.575, y1: -0.08, horizontal: true, spacing: 0.15, sharpness: 9, gain: 0.8, wake: 70),
        // Nonpareil: a fine comb across.
        Pass(from: 9.0, to: 10.7, x0: -0.12, y0: 0.5, x1: 1.12, y1: 0.5, horizontal: false, spacing: 0.06, sharpness: 6, gain: 0.45, wake: 26),
    ]

    /// Ease in and out (cubic), for a comb that gathers speed and slows at the far edge.
    static func easeInOut(_ t: Double) -> Double {
        let k = min(1, max(0, t))
        return k < 0.5 ? 4 * k * k * k : 1 - pow(-2 * k + 2, 3) / 2
    }

    /// Whether a time `at` was passed going from `before` to `t` on a looping clock.
    static func crossed(_ before: Double, _ t: Double, _ at: Double) -> Bool {
        (before < at && t >= at) || (t < before && (at > before || at <= t))
    }
}

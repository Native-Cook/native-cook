import Foundation

/// The sand of the hourglass: a cellular grain grid that lives in the glass's own frame.
///
/// Every cell is empty (0) or holds one grain, stored as its tint (1…255) so a grain keeps its
/// colour as it moves. Each pass moves every grain at most one cell along gravity, or slides it
/// diagonally when the cell below is taken, or — now and then — two cells sideways, which lays the
/// pile at about 34° instead of the grid's 45°. Gravity is the screen's "down" seen from the glass,
/// snapped to the nearest of eight grid directions, so turning the glass makes the sand slide and
/// pour the other way. A step-for-step port of the React Native simulation (same seeded xorshift32,
/// same visiting order), so the same seed gives the same grains on every platform.
///
/// A gate at the neck sets how fast the glass runs: a grain may only enter the neck's middle row
/// when a token is free, and tokens come at the timer's rate (grains per second). Opened, the neck
/// lets through about 219 grains a second, all a one-cell neck will pass.
final class SandHourglassSand {
    // The grid, in cells. Odd column count so the neck is centred on a column.
    static let cols = 77
    static let rows = 151
    static let count = cols * rows
    // Size of one cell, in pt, as drawn.
    static let cell = 2.2
    // The neck's and the bulbs' half-width, in cells from the middle column: the neck is one cell
    // wide, so the sand runs through it as a single thin stream.
    private static let neck = 0.6
    private static let bulb = 36.4
    // The bulb profile: the straight neck runs for `neckRun` of the half-height, the funnel widens
    // to the bulb's widest point at `widest`, and the shoulder narrows to `shoulder` of that at the cap.
    private static let neckRun = 0.03
    private static let widest = 0.6
    private static let shoulder = 0.68
    // Grains in the glass.
    static let grains = 2300
    // Chance that a grain with both diagonals blocked slides two cells sideways: lowers the pile's slope.
    private static let slideChance = 0.32
    // When gravity is this far (in eighths of a turn) off a grid direction, grains slide to that side.
    private static let tiltBias = 0.12
    // Initial pile: rows of height lost per column away from the middle (a 34° cone).
    private static let pileSlope = 0.68
    // The neck's middle row, which the gate guards, and the rows around it counted as "in the neck".
    static let midRow = (rows - 1) / 2
    private static let neckBand = 6

    // Grid directions, counter-clockwise from +x in a y-down frame: 0 = right, 2 = down, 4 = left, 6 = up.
    private static let dxs = [1, 1, 0, -1, -1, -1, 0, 1]
    private static let dys = [0, 1, 1, 1, 0, -1, -1, -1]
    // The same directions as steps in the flat grid index.
    private static let offsets = (0..<8).map { dys[$0] * cols + dxs[$0] }

    /// Half-width of the inside of the glass, in cells, `t` = 0 at the neck … 1 at the caps.
    static func halfWidth(_ t: Double) -> Double {
        if t < neckRun { return neck }
        let u = min(1, (t - neckRun) / (widest - neckRun))
        var s = 1 - pow(1 - u, 2.4)
        if t > widest { s = 1 - (1 - shoulder) * pow((t - widest) / (1 - widest), 2.2) }
        return neck + (bulb - neck) * s
    }

    private typealias S = SandHourglassSand

    /// 0 = empty, otherwise the grain's tint.
    let grid = UnsafeMutablePointer<UInt8>.allocate(capacity: count)
    /// 1 inside the glass.
    private let mask = UnsafeMutablePointer<UInt8>.allocate(capacity: count)
    /// First and last column inside the glass on each row, first and last row in each column.
    private var left = [Int](repeating: cols, count: rows)
    private var right = [Int](repeating: -1, count: rows)
    private var top = [Int](repeating: rows, count: cols)
    private var bottom = [Int](repeating: -1, count: cols)
    /// Grains on each row and in each column, so empty ones are skipped.
    private let rowCount = UnsafeMutablePointer<Int32>.allocate(capacity: rows)
    private let colCount = UnsafeMutablePointer<Int32>.allocate(capacity: cols)
    /// Lines (rows, or columns under sideways gravity) where something moved, or might have, in the
    /// last pass (`dirty`) and in this one (`next`). A line is only visited when it or the line ahead
    /// of it along gravity is dirty, so a settled pile costs nothing.
    private let dirty = UnsafeMutablePointer<UInt8>.allocate(capacity: rows)
    private let next = UnsafeMutablePointer<UInt8>.allocate(capacity: rows)
    private var byRow = true
    /// Rows whose pixels are already all clear.
    private let clearRow = UnsafeMutablePointer<UInt8>.allocate(capacity: rows)
    /// RGBA, one pixel per cell, written by `paint` for the grain shader.
    let pixels = UnsafeMutablePointer<UInt8>.allocate(capacity: count * 4)
    /// Colour of each tint, RGB.
    private let palette = UnsafeMutablePointer<UInt8>.allocate(capacity: 256 * 3)
    private(set) var seed: UInt32
    private(set) var pass = 0
    /// Primary direction of gravity used by the last pass (index into the directions).
    private(set) var dir = 2
    /// Grains in the glass's upper and lower bulbs (its own frame), counted by `paint`.
    private(set) var upper = 0
    private(set) var lower = 0
    /// Grains in and near the neck (within `neckBand` rows of its middle), counted by `paint`.
    private(set) var neckCount = 0
    /// Grains per second the neck lets through; below 0 it is open. Tokens saved up, at most 2.
    var gate = -1.0
    private var tokens = 0.0

    /// A new glass with all its sand settled in the lower bulb as a cone, as if it had just run out.
    /// The same seed gives the same grains.
    init(seed: Int) {
        let wrapped = UInt32(truncatingIfNeeded: seed)
        self.seed = wrapped == 0 ? 1 : wrapped
        grid.initialize(repeating: 0, count: S.count)
        mask.initialize(repeating: 0, count: S.count)
        rowCount.initialize(repeating: 0, count: S.rows)
        colCount.initialize(repeating: 0, count: S.cols)
        dirty.initialize(repeating: 1, count: S.rows)
        next.initialize(repeating: 0, count: S.rows)
        clearRow.initialize(repeating: 0, count: S.rows)
        pixels.initialize(repeating: 0, count: S.count * 4)
        palette.initialize(repeating: 0, count: 256 * 3)
        buildPalette()

        let mid = Double(S.rows) / 2
        let cx = Double(S.cols - 1) / 2
        for r in 0..<S.rows {
            let w = S.halfWidth(abs(Double(r) + 0.5 - mid) / mid)
            for c in 0..<S.cols where abs(Double(c) - cx) <= w {
                mask[r * S.cols + c] = 1
                if c < left[r] { left[r] = c }
                if c > right[r] { right[r] = c }
                if r < top[c] { top[c] = r }
                if r > bottom[c] { bottom[c] = r }
            }
        }
        // Fill the lower bulb from its floor up, highest in the middle: a cone of `pileSlope`.
        var cells: [Int] = []
        var keys: [Double] = []
        for r in Int(mid.rounded(.up))..<S.rows where left[r] <= right[r] {
            for c in left[r]...right[r] {
                cells.append(r * S.cols + c)
                keys.append(Double(S.rows - 1 - r) + S.pileSlope * abs(Double(c) - cx) + random() * 0.9)
            }
        }
        let order = cells.indices.sorted { keys[$0] < keys[$1] }
        for i in 0..<min(S.grains, order.count) {
            let index = cells[order[i]]
            grid[index] = UInt8(1 + Int(floor(random() * 255)))
            rowCount[index / S.cols] += 1
            colCount[index % S.cols] += 1
        }
    }

    deinit {
        grid.deallocate()
        mask.deallocate()
        rowCount.deallocate()
        colCount.deallocate()
        dirty.deallocate()
        next.deallocate()
        clearRow.deallocate()
        pixels.deallocate()
        palette.deallocate()
    }

    /// A small, fast, seeded random in [0, 1): xorshift32 on the seed, in 32-bit wrapping maths.
    private func random() -> Double {
        var x = seed
        x ^= x << 13
        x ^= x >> 17
        x ^= x << 5
        seed = x
        return Double(x) / 4294967296
    }

    /// The colour of every tint: terracotta sand from burnt sienna to pale ochre, with dark and clear flecks.
    private func buildPalette() {
        let deep = [150.0, 74, 38]
        let mid = [204.0, 118, 62]
        let pale = [238.0, 178, 112]
        // JavaScript's Math.round: halves go up.
        func mix(_ a: Double, _ b: Double, _ t: Double) -> UInt8 { UInt8(floor(a + (b - a) * t + 0.5)) }
        for v in 1..<256 {
            let t = Double(v - 1) / 254
            var rgb: [UInt8]
            if v < 10 {
                // A few dark grains, like flecks of mica.
                rgb = [92, 50, 32]
            } else if v > 248 {
                // And a few clear ones that catch the light.
                rgb = [255, 236, 206]
            } else if t < 0.5 {
                rgb = (0..<3).map { mix(deep[$0], mid[$0], t * 2) }
            } else {
                rgb = (0..<3).map { mix(mid[$0], pale[$0], t * 2 - 1) }
            }
            for k in 0..<3 { palette[v * 3 + k] = rgb[k] }
        }
    }

    /// Whether cell `i` is inside the glass and empty. Anything off the grid counts as taken.
    @inline(__always) private func open(_ i: Int) -> Bool {
        i >= 0 && i < S.count && mask[i] == 1 && grid[i] == 0
    }

    /// Moves the grain at `from` to `to`, keeping the counts and waking both lines.
    private func move(_ from: Int, _ to: Int) {
        grid[to] = grid[from]
        grid[from] = 0
        let r = from / S.cols
        let c = from - r * S.cols
        let toRow = to / S.cols
        let toCol = to - toRow * S.cols
        rowCount[r] -= 1
        rowCount[toRow] += 1
        colCount[c] -= 1
        colCount[toCol] += 1
        if byRow {
            next[r] = 1
            next[toRow] = 1
        } else {
            next[c] = 1
            next[toCol] = 1
        }
    }

    /// Whether a grain may move from `from` to `to`: only a move into the neck's middle row needs a
    /// token, and a grain held back keeps its line awake so it tries again next pass.
    private func gateAllows(_ from: Int, _ to: Int) -> Bool {
        if gate < 0 { return true }
        let toRow = to / S.cols
        let fromRow = from / S.cols
        if toRow != S.midRow || fromRow == S.midRow { return true }
        if tokens >= 1 {
            tokens -= 1
            return true
        }
        next[byRow ? fromRow : from - fromRow * S.cols] = 1
        return false
    }

    /// Saves up the neck's tokens for `dt` seconds at the gate's rate.
    func feedGate(_ dt: Double) {
        tokens = gate < 0 ? 0 : min(2, tokens + gate * dt)
    }

    /// One grain's turn, at cell index `i`: along gravity, else a diagonal (downhill side first),
    /// else a long slide.
    private func updateGrain(_ i: Int, _ k: Int, _ bias: Int) {
        let down = i + S.offsets[k]
        if open(down) {
            if gateAllows(i, down) { move(i, down) }
            return
        }
        // The two neighbours of the primary direction; `bias` > 0 means the clockwise one is downhill.
        let a = i + S.offsets[(k + 1) % 8]
        let b = i + S.offsets[(k + 7) % 8]
        let aFree = open(a)
        let bFree = open(b)
        if aFree || bFree {
            var clockwise = aFree
            if aFree && bFree { clockwise = bias == 0 ? random() < 0.5 : bias > 0 }
            let to = clockwise ? a : b
            if gateAllows(i, to) { move(i, to) }
            return
        }
        // Straight gravity only: slip two cells sideways and one down, over an empty cell beside it.
        if k % 2 != 0 { return }
        // The sideways axis is gravity turned a quarter.
        let side = S.offsets[(k + 2) % 8]
        let rightFree = open(i + side)
        let leftFree = open(i - side)
        if !leftFree && !rightFree { return }
        if random() >= S.slideChance {
            // It may slide on a later pass: keep its line awake.
            next[byRow ? i / S.cols : i % S.cols] = 1
            return
        }
        let way = leftFree && rightFree ? (random() < 0.5 ? 1 : -1) : rightFree ? 1 : -1
        let to = i + 2 * way * side + S.offsets[k]
        if open(to) && gateAllows(i, to) { move(i, to) }
    }

    /// One pass of the sand under gravity (gx, gy), given in the glass's frame. Cells are visited
    /// from the far end of gravity backwards, so a grain that moved is never visited again in the
    /// same pass. Rows (or columns) without a grain are skipped.
    func step(gx: Double, gy: Double) {
        let octant = atan2(gy, gx) / (Double.pi / 4)
        let nearest = Int(floor(octant + 0.5))
        let off = octant - Double(nearest)
        let k = ((nearest % 8) + 8) % 8
        let bias = abs(off) > S.tiltBias ? (off > 0 ? 1 : -1) : 0
        let dx = S.dxs[k]
        let dy = S.dys[k]
        let rowWise = dy != 0
        // A new direction of gravity can move anything.
        if k != dir || rowWise != byRow { dirty.update(repeating: 1, count: S.rows) }
        dir = k
        byRow = rowWise
        pass += 1
        let alternate = pass % 2 == 0 ? 1 : -1
        if rowWise {
            // Rows from the far end of gravity; within a row, from the far end of its sideways part.
            let stepDir = dx != 0 ? -dx : alternate
            for i in 0..<S.rows {
                let r = dy > 0 ? S.rows - 1 - i : i
                if rowCount[r] == 0 { continue }
                let ahead = r + dy
                let awake = dirty[r] == 1 || (ahead >= 0 && ahead < S.rows && (dirty[ahead] == 1 || next[ahead] == 1))
                if !awake { continue }
                let lo = left[r]
                let hi = right[r]
                if lo > hi { continue }
                let base = r * S.cols
                if stepDir < 0 {
                    var c = hi
                    while c >= lo {
                        if grid[base + c] != 0 { updateGrain(base + c, k, bias) }
                        c -= 1
                    }
                } else {
                    for c in lo...hi where grid[base + c] != 0 { updateGrain(base + c, k, bias) }
                }
            }
        } else {
            // Sideways gravity: columns from the far end, rows alternating.
            for i in 0..<S.cols {
                let c = dx > 0 ? S.cols - 1 - i : i
                if colCount[c] == 0 { continue }
                let ahead = c + dx
                let awake = dirty[c] == 1 || (ahead >= 0 && ahead < S.cols && (dirty[ahead] == 1 || next[ahead] == 1))
                if !awake { continue }
                let lo = top[c]
                let hi = bottom[c]
                if lo > hi { continue }
                if alternate < 0 {
                    var r = hi
                    while r >= lo {
                        if grid[r * S.cols + c] != 0 { updateGrain(r * S.cols + c, k, bias) }
                        r -= 1
                    }
                } else {
                    for r in lo...hi where grid[r * S.cols + c] != 0 { updateGrain(r * S.cols + c, k, bias) }
                }
            }
        }
        for i in 0..<S.rows {
            dirty[i] = next[i]
            next[i] = 0
        }
    }

    /// Writes the grid into `pixels` (one RGBA pixel per cell) and counts the grains in each bulb.
    /// A grain with open space above it (against gravity) is lit, one buried on all four sides is
    /// shaded, so the pile reads as a surface and the stream as loose grains.
    func paint() {
        let ux = -S.dxs[dir]
        let uy = -S.dys[dir]
        let midRow = Double(S.rows - 1) / 2
        var up = 0
        var low = 0
        var inNeck = 0
        for r in 0..<S.rows {
            if rowCount[r] == 0 {
                if clearRow[r] == 1 { continue }
                clearRow[r] = 1
            } else {
                clearRow[r] = 0
            }
            let lo = left[r]
            let hi = right[r]
            if lo > hi { continue }
            for c in lo...hi {
                let i = r * S.cols + c
                let p = i * 4
                let v = Int(grid[i])
                if v == 0 {
                    pixels[p] = 0
                    pixels[p + 1] = 0
                    pixels[p + 2] = 0
                    pixels[p + 3] = 0
                    continue
                }
                if Double(r) < midRow { up += 1 } else if Double(r) > midRow { low += 1 }
                if abs(r - S.midRow) <= S.neckBand { inNeck += 1 }
                let ar = r + uy
                let ac = c + ux
                let above = ar >= 0 && ar < S.rows && ac >= 0 && ac < S.cols ? grid[ar * S.cols + ac] : 0
                var shade = 1.0
                if above == 0 {
                    shade = 1.14
                } else if c > 0 && c < S.cols - 1 && r > 0 && r < S.rows - 1
                    && grid[i - 1] != 0 && grid[i + 1] != 0 && grid[i - S.cols] != 0 && grid[i + S.cols] != 0 {
                    shade = 0.9
                }
                pixels[p] = UInt8(min(255, Double(palette[v * 3]) * shade))
                pixels[p + 1] = UInt8(min(255, Double(palette[v * 3 + 1]) * shade))
                pixels[p + 2] = UInt8(min(255, Double(palette[v * 3 + 2]) * shade))
                pixels[p + 3] = 255
            }
        }
        upper = up
        lower = low
        neckCount = inNeck
    }
}

import Foundation
import SwiftUI

/*
 Dither Asterisk Sign In — the pixel mark, the autoPlay script and the touch springs.

 The mark is a 31 × 31 grid of cells (an asterisk of four bars). Its 31 columns are grouped into
 8 vertical slices. A frame is, per column, an offset in cells (drawn rounded to whole cells) and
 an energy 0…1 that thins the column's cells into a glyph dither (full → ■ → 1 → 0 → + → empty),
 plus a sideways shift for three row bands. Cells between a slice's old and new place are drawn as
 a fading streak of glyphs.

 autoPlay: loop = 7 s
   0.00 – 0.80  rest, the whole mark
   0.80 – 1.98  slices slide out one after another, right to left, and dither (build); while out
                they drift by a cell or so (3 sine periods per loop)
   2.70 – 4.05  slices jump to a second set of offsets, row bands shear sideways (peak scramble)
   4.05 – 5.55  slices spring home left to right with a one-cell overshoot, cells fill back in
   5.10 – 6.10  a faint glow behind the mark as it is whole again
   5.55 – 7.00  rest (the frame at 7 is the frame at 0)
*/
enum DitherAsteriskSigninMotion {
    static let loop = 7.0
    static let n = 31
    static let pitch = 22.0 / 3.0
    static let cell = 22.0 / 3.0 - 1.0
    static let rowMin = -8
    static let rowMax = 38
    static let idleBeforeBack = 2.0

    static let widths = [4, 3, 5, 2, 4, 3, 6, 4]
    static let build: [Double] = [0, -2, 1, -5, 2, 6, 3, -5]
    static let peak: [Double] = [1, 3, -2, 2, -6, -4, 5, 2]
    static let energy: [Double] = [0, 0.3, 0, 1, 0.5, 1, 0.2, 0.8]
    /// Row bands that shear sideways at the peak: first row, last row, shift in cells.
    static let bands: [(Int, Int, Double)] = [(6, 9, 2), (14, 16, -3), (21, 24, 4)]
    static let bayer = [0, 8, 2, 10, 12, 4, 14, 6, 3, 11, 1, 9, 15, 7, 13, 5]

    /// The slice each column belongs to.
    static let sliceOf: [Int] = {
        var out: [Int] = []
        for (i, w) in widths.enumerated() { for _ in 0..<w { out.append(i) } }
        return out
    }()

    /// The asterisk, row-major, n × n.
    static let mask: [Bool] = {
        var out = [Bool](repeating: false, count: n * n)
        let k = 1.0 / 2.0.squareRoot()
        for r in 0..<n {
            for c in 0..<n {
                let x = Double(c - 15), y = Double(r - 15)
                var on = abs(y) <= 3.2 && abs(x) <= 15.5
                on = on || (abs(x) <= 2.6 && abs(y) <= 15.5)
                for s in [1.0, -1.0] {
                    let along = (x + s * y) * k
                    let across = (y - s * x) * k
                    if abs(across) <= 2.3 && abs(along) <= 14.4 { on = true }
                    let ex = 14.4 * k, ey = 14.4 * k * s
                    if (x - ex) * (x - ex) + (y - ey) * (y - ey) <= 2.3 * 2.3 { on = true }
                    if (x + ex) * (x + ex) + (y + ey) * (y + ey) <= 2.3 * 2.3 { on = true }
                }
                out[r * n + c] = on
            }
        }
        return out
    }()

    static func inMask(_ c: Int, _ r: Int) -> Bool {
        c >= 0 && c < n && r >= 0 && r < n && mask[r * n + c]
    }

    static func seg(_ t: Double, _ a: Double, _ b: Double) -> Double {
        t <= a ? 0 : (t >= b ? 1 : (t - a) / (b - a))
    }
    static func easeOut(_ x: Double) -> Double { 1 - pow(1 - x, 3) }
    static func easeInOut(_ x: Double) -> Double {
        x < 0.5 ? 4 * x * x * x : 1 - pow(-2 * x + 2, 3) / 2
    }
    /// Whole cells, rounding halves up in every stack.
    static func cells(_ x: Double) -> Int { Int(floor(x + 0.5)) }

    /// Step response of a spring from 1 to 0 (response 0.5 s, damping 0.55).
    static func springHome(_ tau: Double) -> Double {
        if tau <= 0 { return 1 }
        let w0 = 2 * Double.pi / 0.5, z = 0.55
        let wd = w0 * (1 - z * z).squareRoot()
        return exp(-z * w0 * tau) * (cos(wd * tau) + z * w0 / wd * sin(wd * tau))
    }

    static func buildStart(_ i: Int) -> Double { 0.8 + 0.09 * Double(7 - i) }
    static func peakStart(_ i: Int) -> Double { 2.7 + 0.06 * Double((5 * i) % 8) }
    static func homeStart(_ i: Int) -> Double { 4.05 + 0.1 * Double(i) }

    /// The autoPlay frame at loop time t.
    static func autoFrame(_ t: Double) -> DitherAsteriskSigninFrame {
        var f = DitherAsteriskSigninFrame()
        var sliceOffset = [Double](repeating: 0, count: widths.count)
        var sliceEnergy = [Double](repeating: 0, count: widths.count)
        for i in 0..<widths.count {
            let s = buildStart(i), p = peakStart(i), h = homeStart(i)
            let a = easeInOut(seg(t, s, s + 0.55))
            let b = easeInOut(seg(t, p, p + 0.45))
            let rise = easeOut(seg(t, s, s + 0.6))
            let fall = easeInOut(seg(t, h + 0.1, h + 0.8))
            // A slow drift while the slices are out (3 periods per loop), ticking by whole cells.
            let drift = 1.3 * sin(2 * Double.pi * 3 * t / loop + 1.7 * Double(i)) * rise * (1 - fall)
            sliceOffset[i] = (build[i] * a * (1 - b) + peak[i] * b) * springHome(t - h) + drift
            sliceEnergy[i] = energy[i] * rise * (1 - fall)
        }
        for c in 0..<n {
            f.offset[c] = sliceOffset[sliceOf[c]]
            f.energy[c] = sliceEnergy[sliceOf[c]]
        }
        let shear = easeInOut(seg(t, 2.75, 3.05)) * (1 - easeInOut(seg(t, 3.75, 4.05)))
        for k in 0..<bands.count { f.shift[k] = bands[k].2 * shear }
        let up = easeOut(seg(t, 5.1, 5.4)), down = easeInOut(seg(t, 5.4, 6.1))
        f.glow = 0.07 * up * (1 - down)
        return f
    }

    /// Glyph level 0…5 from a density and the cell's Bayer threshold.
    static func level(_ d: Double, _ c: Int, _ r: Int) -> Int {
        let b = (Double(bayer[((r % 4) + 4) % 4 * 4 + ((c % 4) + 4) % 4]) + 0.5) / 16
        let q = d - b
        if q > 0 { return 5 }
        if q > -0.15 { return 4 }
        if q > -0.30 { return 3 }
        if q > -0.45 { return 2 }
        if q > -0.60 { return 1 }
        return 0
    }

    /// The level of every drawn cell, (rowMax − rowMin + 1) rows × n columns.
    static func levels(_ f: DitherAsteriskSigninFrame) -> [Int] {
        let rows = rowMax - rowMin + 1
        var out = [Int](repeating: 0, count: rows * n)
        var rowShift = [Int](repeating: 0, count: rows)
        for k in 0..<bands.count {
            let s = cells(f.shift[k])
            for r in bands[k].0...bands[k].1 { rowShift[r - rowMin] = s }
        }
        for c in 0..<n {
            let d = cells(f.offset[c])
            let e = f.energy[c]
            let body = 1 - e
            let span = abs(d)
            let dir = d > 0 ? 1 : -1
            for ri in 0..<rows {
                let r = ri + rowMin
                let sc = c - rowShift[ri]
                let sr = r - d
                var lv = 0
                if inMask(sc, sr) {
                    lv = level(body, c, r)
                } else if span > 0 {
                    for j in 1...span where inMask(sc, sr + dir * j) {
                        let dens = 0.8 * e * (1 - Double(j - 1) / Double(span))
                        lv = min(4, level(dens, c, r))
                        break
                    }
                }
                out[ri * n + c] = lv
            }
        }
        return out
    }

    /// Adds one glyph's rects, the cell's box at (x, y) with side s.
    static func addGlyph(_ p: inout Path, _ lv: Int, _ x: Double, _ y: Double, _ s: Double) {
        func r(_ u0: Double, _ v0: Double, _ u1: Double, _ v1: Double) {
            p.addRect(CGRect(x: x + u0 * s, y: y + v0 * s, width: (u1 - u0) * s, height: (v1 - v0) * s))
        }
        switch lv {
        case 5: r(0, 0, 1, 1)
        case 4: r(0.19, 0.19, 0.81, 0.81)
        case 3:
            r(0.42, 0.12, 0.58, 0.88)
            r(0.26, 0.12, 0.42, 0.27)
        case 2:
            r(0.24, 0.12, 0.76, 0.26)
            r(0.24, 0.74, 0.76, 0.88)
            r(0.24, 0.26, 0.38, 0.74)
            r(0.62, 0.26, 0.76, 0.74)
        case 1:
            r(0.18, 0.43, 0.82, 0.57)
            r(0.43, 0.18, 0.57, 0.43)
            r(0.43, 0.57, 0.57, 0.82)
        default: break
        }
    }

    /// The whole mark as one path, its grid's top-left at (left, top).
    static func markPath(_ f: DitherAsteriskSigninFrame, left: Double, top: Double) -> Path {
        let lv = levels(f)
        let rows = rowMax - rowMin + 1
        var p = Path()
        for ri in 0..<rows {
            let y = top + Double(ri + rowMin) * pitch
            for c in 0..<n {
                let l = lv[ri * n + c]
                if l > 0 { addGlyph(&p, l, left + Double(c) * pitch, y, cell) }
            }
        }
        return p
    }
}

/// Everything one frame of the mark needs.
struct DitherAsteriskSigninFrame {
    var offset = [Double](repeating: 0, count: DitherAsteriskSigninMotion.n)
    var energy = [Double](repeating: 0, count: DitherAsteriskSigninMotion.n)
    var shift = [Double](repeating: 0, count: DitherAsteriskSigninMotion.bands.count)
    var glow = 0.0
}

/// The loop clock, and per-column springs while a finger drives the mark.
final class DitherAsteriskSigninSim {
    typealias M = DitherAsteriskSigninMotion

    private var epoch: Date?
    private var lastDate: Date?
    private var lastAuto = DitherAsteriskSigninFrame()
    private(set) var live = false
    private var x = [Double](repeating: 0, count: M.n)
    private var v = [Double](repeating: 0, count: M.n)
    private var e = [Double](repeating: 0, count: M.n)
    private var shift = [Double](repeating: 0, count: M.bands.count)
    private var down = false
    private var fingerCol = 0.0
    private var lastX = 0.0
    private var lastY = 0.0
    private var lastTouch: Date?
    private var releasedAt: Date?
    private var feltSlice = false
    private var feltWhole = true
    var onFeel: (() -> Void)?

    static let k = pow(2 * Double.pi / 0.55, 2)
    static let damp = 2 * 0.5 * (2 * Double.pi / 0.55)

    func frame(at date: Date, auto: Bool) -> DitherAsteriskSigninFrame {
        if epoch == nil { epoch = date }
        guard live else {
            let t = auto ? date.timeIntervalSince(epoch!).truncatingRemainder(dividingBy: M.loop) : 0
            lastAuto = M.autoFrame(t)
            lastDate = date
            return lastAuto
        }
        let dt = min(max(date.timeIntervalSince(lastDate ?? date), 0), 1.0 / 20)
        lastDate = date
        step(dt)
        var f = DitherAsteriskSigninFrame()
        var whole = true
        for c in 0..<M.n {
            f.offset[c] = 9 * tanh(x[c] / 9)
            f.energy[c] = min(1, e[c])
            if M.cells(f.offset[c]) != 0 || e[c] > 0.04 { whole = false }
        }
        for k in 0..<shift.count {
            f.shift[k] = shift[k]
            if M.cells(shift[k]) != 0 { whole = false }
        }
        if !down {
            if whole && !feltWhole { feltWhole = true; onFeel?() }
            if whole, let releasedAt, date.timeIntervalSince(releasedAt) >= M.idleBeforeBack {
                live = false
                epoch = date
                lastAuto = M.autoFrame(0)
                return lastAuto
            }
        }
        return f
    }

    private func step(_ dt: Double) {
        let steps = max(1, Int(ceil(dt * 240)))
        let h = dt / Double(steps)
        for _ in 0..<steps {
            for c in 0..<M.n {
                // The spring of the columns under the finger lets go a little while it is down.
                let w = down ? exp(-pow((Double(c) - fingerCol) / 1.4, 2)) : 0
                let a = -Self.k * (1 - 0.65 * w) * x[c] - Self.damp * v[c]
                v[c] += a * h
                x[c] += v[c] * h
                e[c] *= exp(-h / 0.6)
            }
            for k in 0..<shift.count { shift[k] *= exp(-h / 0.12) }
        }
    }

    /// The finger at (px, py) pt, over column `col` (fractional, column centres at whole numbers).
    /// Columns near it are dragged by the finger's movement since the last event: vertical motion
    /// pulls them along, horizontal motion slices them up or down by their slice's sign; their
    /// dither follows the finger's speed.
    func touch(x px: Double, y py: Double, col: Double, at date: Date) {
        if !live {
            live = true
            for c in 0..<M.n {
                x[c] = lastAuto.offset[c]
                e[c] = lastAuto.energy[c]
                v[c] = 0
            }
            shift = lastAuto.shift
            lastDate = date
        }
        if !down {
            feltSlice = false
            lastX = px
            lastY = py
            lastTouch = date
        }
        down = true
        feltWhole = false
        fingerCol = col
        let dx = px - lastX, dy = py - lastY
        let dt = max(date.timeIntervalSince(lastTouch ?? date), 1.0 / 240)
        let speed = (dx * dx + dy * dy).squareRoot() / dt
        for c in 0..<M.n {
            let w = exp(-pow((Double(c) - col) / 1.4, 2))
            let sign = M.sliceOf[c] % 2 == 0 ? 1.0 : -1.0
            x[c] = min(max(x[c] + w * (dy + sign * abs(dx) * 0.8) / M.pitch, -14), 14)
            e[c] = max(e[c], w * min(1, speed / 900))
        }
        lastX = px
        lastY = py
        lastTouch = date
        if !feltSlice && x.contains(where: { abs($0) > 3 }) {
            feltSlice = true
            onFeel?()
        }
    }

    func release(at date: Date) {
        guard down else { return }
        down = false
        releasedAt = date
    }
}

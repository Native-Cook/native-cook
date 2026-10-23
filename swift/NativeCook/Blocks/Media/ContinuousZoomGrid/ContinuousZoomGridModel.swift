import Foundation

/*
 The numbers behind Continuous Zoom Grid: the library's 3,000 drawn photos (each one generated
 from its index), its months, the zoom levels, and the autoPlay timeline.
 */

struct ContinuousZoomGridRGB {
    var r: Double, g: Double, b: Double
    init(_ r: Double, _ g: Double, _ b: Double) { self.r = r; self.g = g; self.b = b }
    func scaled(_ k: Double) -> Self { Self(r * k, g * k, b * k) }
    static func mix(_ a: Self, _ b: Self, _ t: Double) -> Self { Self(a.r + (b.r - a.r) * t, a.g + (b.g - a.g) * t, a.b + (b.b - a.b) * t) }
}

/// The screen's view of the library: the zoom (photos per row), the photo it is anchored on and
/// that photo's centre on screen (y).
struct ContinuousZoomGridView {
    var z: Double
    var anchor: Int
    var fy: Double
}

enum ContinuousZoomGridModel {
    typealias RGB = ContinuousZoomGridRGB
    static let count = 3000
    /// The layouts the zoom blends between, in photos per row.
    static let levels: [Double] = [3, 5, 9, 14, 20]
    static let minZ = 3.0, maxZ = 20.0
    static let loop = 8.0

    // MARK: Hash

    /// A small integer hash in 0…1, the same in every language.
    static func hash(_ a: Int, _ b: Int, _ c: Int) -> Double {
        var x = UInt32(truncatingIfNeeded: a &* 73_856_093) ^ UInt32(truncatingIfNeeded: b &* 19_349_663) ^ UInt32(truncatingIfNeeded: c &* 83_492_791)
        x ^= x >> 13
        x = x &* 1_274_126_177
        x ^= x >> 16
        return Double(x & 0xFFFF) / 65535
    }

    // MARK: Photos

    struct Palette { var top: RGB; var bottom: RGB; var ground: RGB; var accent: RGB }
    static let palettes: [Palette] = [
        Palette(top: RGB(0.98, 0.56, 0.38), bottom: RGB(1.0, 0.84, 0.60), ground: RGB(0.34, 0.20, 0.26), accent: RGB(1.0, 0.94, 0.72)),   // golden hour
        Palette(top: RGB(0.20, 0.46, 0.80), bottom: RGB(0.70, 0.86, 0.96), ground: RGB(0.10, 0.28, 0.42), accent: RGB(1.0, 0.98, 0.92)),   // sea
        Palette(top: RGB(0.58, 0.76, 0.70), bottom: RGB(0.90, 0.94, 0.84), ground: RGB(0.14, 0.32, 0.22), accent: RGB(1.0, 0.90, 0.56)),   // forest
        Palette(top: RGB(0.46, 0.40, 0.78), bottom: RGB(0.96, 0.72, 0.80), ground: RGB(0.24, 0.18, 0.40), accent: RGB(1.0, 0.88, 0.80)),   // lavender
        Palette(top: RGB(0.90, 0.72, 0.56), bottom: RGB(0.99, 0.90, 0.76), ground: RGB(0.72, 0.46, 0.30), accent: RGB(1.0, 0.62, 0.36)),   // desert
        Palette(top: RGB(0.04, 0.06, 0.16), bottom: RGB(0.20, 0.14, 0.34), ground: RGB(0.05, 0.05, 0.10), accent: RGB(1.0, 0.76, 0.42)),   // night
        Palette(top: RGB(0.94, 0.54, 0.62), bottom: RGB(1.0, 0.86, 0.78), ground: RGB(0.52, 0.24, 0.34), accent: RGB(1.0, 0.96, 0.88)),   // rose
        Palette(top: RGB(0.56, 0.62, 0.68), bottom: RGB(0.88, 0.89, 0.90), ground: RGB(0.22, 0.25, 0.28), accent: RGB(0.98, 0.96, 0.90)),  // overcast
    ]

    enum Op {
        case rect(Double, Double, Double, Double, RGB, Double)
        /// A vertical gradient filling a rect, top colour then bottom colour.
        case grad(Double, Double, Double, Double, RGB, RGB)
        case circle(Double, Double, Double, RGB, Double)
        /// A soft light: a radial gradient from the colour (at the given opacity) to clear.
        case glow(Double, Double, Double, RGB, Double)
        case poly([Double], RGB, Double)
    }

    struct Photo {
        var ops: [Op]
        /// Seconds, for a video; 0 for a still.
        var duration: Int
    }

    static let kinds = 8
    /// Photos come in runs, like a library's days out: most share their run's kind (runs of 4) and
    /// palette (runs of 8).
    static func kind(_ i: Int) -> Int { Int(hash(hash(i, 8, 0) < 0.4 ? i / 4 : i + 5000, 2, 0) * 7.999) }
    static func palette(_ i: Int) -> Palette {
        // Lights out of focus are always shot at night or at dusk.
        if kind(i) == 2 { return palettes[hash(i, 1, 0) < 0.6 ? 5 : 3] }
        return palettes[Int(hash(hash(i, 9, 0) < 0.6 ? i / 8 : i + 5000, 1, 0) * 7.999)]
    }

    /// A band filled below a wavy line: y = base + amp · sin(2π · (freq · x + phase)), 13 points.
    static func wave(_ base: Double, _ amp: Double, _ freq: Double, _ phase: Double) -> [Double] {
        var pts: [Double] = []
        for k in 0...12 {
            let x = Double(k) / 12
            pts += [x, base + amp * sin(2 * Double.pi * (freq * x + phase))]
        }
        return pts + [1, 1, 0, 1]
    }

    /// The photo at an index: a palette, one of eight kinds of picture and a few shape parameters,
    /// all from the hash of the index.
    static func photo(_ i: Int) -> Photo {
        let p = palette(i)
        let a = hash(i, 3, 0), b = hash(i, 4, 0), c = hash(i, 7, 0)
        var ops: [Op] = []
        switch kind(i) {
        case 0: // Sun over the sea.
            let sx = 0.25 + 0.5 * a, h = 0.56 + 0.1 * b
            ops = [.grad(0, 0, 1, h, p.top, p.bottom), .glow(sx, h - 0.04, 0.42, p.accent, 0.7),
                   .circle(sx, h - 0.03, 0.065, p.accent, 1),
                   .grad(0, h, 1, 1 - h, p.ground.scaled(1.4), p.ground.scaled(0.7))]
            for k in 0..<4 {
                let wk = 0.2 - 0.04 * Double(k)
                ops.append(.rect(sx - wk / 2, h + 0.04 + 0.07 * Double(k), wk, 0.018, p.accent, 0.7 - 0.12 * Double(k)))
            }
        case 1: // Hills fading into haze.
            ops = [.grad(0, 0, 1, 1, p.top, p.bottom), .glow(0.2 + 0.6 * a, 0.3, 0.5, p.accent, 0.45)]
            for k in 0..<4 {
                let t = Double(k) / 3
                ops.append(.poly(wave(0.42 + 0.14 * Double(k), 0.05 + 0.02 * b, 0.8 + 0.5 * Double(k % 2), a + 0.3 * Double(k)),
                                 RGB.mix(RGB.mix(p.bottom, p.ground, 0.35), p.ground.scaled(0.75), t), 1))
            }
        case 2: // City lights out of focus.
            ops = [.grad(0, 0, 1, 1, p.top, p.bottom)]
            for k in 0..<9 {
                let col = k % 3 == 0 ? p.bottom.scaled(1.6) : p.accent
                ops.append(.glow(hash(i, 10 + k, 1), 0.15 + 0.8 * hash(i, 10 + k, 2), 0.07 + 0.12 * hash(i, 10 + k, 3), col, 0.55 + 0.35 * hash(i, 10 + k, 4)))
            }
        case 3: // Dunes.
            ops = [.grad(0, 0, 1, 1, p.top, p.bottom), .glow(0.3 + 0.4 * a, 0.22, 0.3, p.accent, 0.5)]
            for k in 0..<3 {
                ops.append(.poly(wave(0.5 + 0.16 * Double(k), 0.06, 0.5 + 0.25 * Double(k), b + 0.37 * Double(k)),
                                 RGB.mix(p.accent.scaled(0.9), p.ground, 0.35 + 0.3 * Double(k)), 1))
            }
        case 4: // Pines in mist.
            ops = [.grad(0, 0, 1, 1, p.bottom, p.top.scaled(0.9))]
            for row in 0..<2 {
                let base = 0.62 + 0.22 * Double(row)
                let col = RGB.mix(p.top, p.ground.scaled(0.6), 0.45 + 0.5 * Double(row))
                for k in 0..<7 {
                    let x = (Double(k) + 0.5 * Double(row) + 0.3 * hash(i, k, row)) / 6.5
                    let hgt = 0.26 + 0.14 * hash(i, k + 9, row)
                    ops.append(.poly([x - 0.07, base, x, base - hgt, x + 0.07, base], col, 1))
                }
                ops.append(.rect(0, base - 0.01, 1, 1, col, 1))
                if row == 0 { ops.append(.rect(0, base - 0.16, 1, 0.5, p.bottom, 0.45)) }
            }
        case 5: // Petals, close up.
            ops = [.grad(0, 0, 1, 1, p.ground.scaled(0.9), p.ground.scaled(0.5))]
            for k in 0..<6 {
                let col = [p.accent, p.top, p.bottom][k % 3]
                ops.append(.glow(hash(i, 20 + k, 1), hash(i, 20 + k, 2), 0.22 + 0.2 * hash(i, 20 + k, 3), col, 0.75))
            }
        case 6: // A building in low sun.
            let x = 0.16 + 0.3 * a, top = 0.18 + 0.2 * b
            ops = [.grad(0, 0, 1, 1, p.top, p.bottom), .glow(1 - a, 0.2, 0.4, p.accent, 0.5),
                   .rect(x, top, 0.4, 1, RGB.mix(p.bottom, p.accent, 0.5), 1),
                   .poly([x + 0.4, top, x + 0.56, top + 0.08, x + 0.56, 1, x + 0.4, 1], p.ground.scaled(1.3), 1)]
            for r in 0..<6 {
                for cc in 0..<3 {
                    ops.append(.rect(x + 0.05 + Double(cc) * 0.12, top + 0.06 + Double(r) * 0.12, 0.07, 0.07, p.ground, 0.55))
                }
            }
        default: // A beach.
            let h = 0.46 + 0.08 * c
            ops = [.grad(0, 0, 1, h, p.top, p.bottom), .glow(0.2 + 0.6 * a, h - 0.1, 0.3, p.accent, 0.45),
                   .grad(0, h, 1, 0.2, p.top.scaled(0.8), p.top.scaled(1.1)),
                   .poly(wave(h + 0.18, 0.025, 1.4, b), p.accent.scaled(0.97), 1),
                   .poly(wave(h + 0.22, 0.03, 1.4, b + 0.05), RGB.mix(p.accent, p.ground, 0.25), 1)]
        }
        return Photo(ops: ops, duration: duration(i))
    }

    /// A video's length in seconds (about one photo in six), or 0 for a still.
    static func duration(_ i: Int) -> Int { hash(i, 5, 0) > 0.84 ? 3 + Int(hash(i, 6, 0) * 55) : 0 }

    /// What a photo shows when it is too small for its picture: its sky over its ground, split at a
    /// height (1 for a picture of one colour).
    static func tiny(_ i: Int) -> (sky: RGB, ground: RGB, split: Double) {
        let p = palette(i)
        let sky = RGB.mix(p.top, p.bottom, 0.5)
        switch kind(i) {
        case 0: return (sky, p.ground, 0.6)
        case 1: return (sky, RGB.mix(p.ground, p.bottom, 0.4), 0.5)
        case 2: return (RGB.mix(p.top, p.bottom, 0.4), sky, 1)
        case 3: return (sky, RGB.mix(p.accent.scaled(0.9), p.ground, 0.5), 0.55)
        case 4: return (p.bottom, RGB.mix(p.top, p.ground.scaled(0.6), 0.7), 0.55)
        case 5: return (RGB.mix(p.ground.scaled(0.7), p.accent, 0.25), sky, 1)
        case 6: return (sky, RGB.mix(p.bottom, p.accent, 0.5), 0.45)
        default: return (sky, RGB.mix(p.accent, p.ground, 0.2), 0.62)
        }
    }

    // MARK: Months

    /// The first index of each month, newest first, and its label.
    static let months: [(start: Int, label: String)] = {
        let names = ["Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"]
        var out: [(Int, String)] = []
        var i = 0, m = 9, y = 2026
        while i < count {
            out.append((i, "\(names[m]) \(y)"))
            i += 30 + Int(hash(m, y, 9) * 80)
            m -= 1
            if m < 0 { m = 11; y -= 1 }
        }
        return out
    }()

    static let monthStarts = Set(months.map(\.start))

    static func month(of i: Int) -> String {
        var lo = 0, hi = months.count - 1
        while lo < hi {
            let mid = (lo + hi + 1) / 2
            if months[mid].start <= i { lo = mid } else { hi = mid - 1 }
        }
        return months[lo].label
    }

    // MARK: Easing

    static func clamp01(_ x: Double) -> Double { min(1, max(0, x)) }
    static func smoothstep(_ a: Double, _ b: Double, _ x: Double) -> Double {
        let t = clamp01((x - a) / (b - a))
        return t * t * (3 - 2 * t)
    }
    static func easeInOut(_ x: Double) -> Double {
        let u = clamp01(x)
        return u < 0.5 ? 4 * u * u * u : 1 - pow(-2 * u + 2, 3) / 2
    }
    /// Ease in (cubic) for the first half, then out with a small overshoot (about 5 %).
    static func easeInOutBack(_ x: Double) -> Double {
        let u = clamp01(x)
        if u < 0.5 { return 4 * u * u * u }
        let v = 2 * u - 1, c1 = 1.2, c3 = c1 + 1
        return 0.5 + 0.5 * (1 + c3 * pow(v - 1, 3) + c1 * pow(v - 1, 2))
    }

    // MARK: autoPlay

    /// The photo the screen rests on and where (its centre at half the screen's height).
    static let restAnchor = 901
    /// The photo the zoom comes back in on: two rows lower, in the left column at 3 per row.
    static let returnAnchor = 906

    /// The row a photo sits in, and its column, with n photos per row.
    static func place(_ i: Int, _ n: Int) -> (row: Int, col: Int) { (i / n, i % n) }

    /// autoPlay (loop = 8 s): rest at 3 per row; 1.0 – 2.8 zoom out to 20 per row about the middle
    /// photo; hold; 3.4 – 4.4 the grid is dragged down until the return photo sits where it will
    /// land; 4.6 – 6.4 zoom back in to 3 per row about that photo, landing with a small overshoot;
    /// rest to 8.0 (the same frame as 0).
    static func view(at time: Double, w: Double, h: Double) -> ContinuousZoomGridView {
        var t = time.truncatingRemainder(dividingBy: loop)
        if t < 0 { t += loop }
        let rest = ContinuousZoomGridView(z: 3, anchor: restAnchor, fy: h * 0.5)
        // Where the return photo sits at rest (it lands there again).
        let target = rest.fy + Double(place(returnAnchor, 3).row - place(restAnchor, 3).row) * w / 3
        // At 20 per row both photos share a row, so dragging moves the return photo to its target.
        let dense = maxZ
        let rowGap = Double(place(returnAnchor, Int(dense)).row - place(restAnchor, Int(dense)).row) * w / dense
        if t < 1.0 { return rest }
        if t < 2.8 {
            let u = easeInOut((t - 1.0) / 1.8)
            return ContinuousZoomGridView(z: 3 * pow(dense / 3, u), anchor: restAnchor, fy: rest.fy)
        }
        if t < 4.5 {
            let u = easeInOut((t - 3.4) / 1.0)
            return ContinuousZoomGridView(z: dense, anchor: restAnchor, fy: rest.fy + (target - rowGap - rest.fy) * u)
        }
        if t < 6.4 {
            let u = easeInOutBack((t - 4.6) / 1.8)
            return ContinuousZoomGridView(z: dense * pow(3 / dense, u), anchor: returnAnchor, fy: target)
        }
        return rest
    }
}

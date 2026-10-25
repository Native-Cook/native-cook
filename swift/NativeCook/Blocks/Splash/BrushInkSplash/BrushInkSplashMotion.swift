import Foundation

/// The Brush Ink Splash's clock and brush strokes. Every value is a closed-form function of the
/// loop time t (seconds), so t = loop draws the frame of t = 0. Lengths in points.
///
///   0.0 – 1.2   rest: blank rice paper.
///   1.2 – 1.45  the brush loads: it presses down at the start of the circle (a swelling blot).
///   1.45 – 3.0  it paints the ensō clockwise, easing in and out; the stroke thins and runs dry
///               toward its end, bristle streaks opening up in it.
///   3.0 – 4.2   the wordmark "Sumi", stroke after stroke, with the same brush.
///   3.4 – 4.8   the ink bleeds into the paper's fibres at every edge (ease-out).
///   4.8 – 6.2   hold.
///   6.2 – 7.0   the paper slides up (ease-in-out), revealing the journal's home screen.
///   7.0 – 7.25  the home screen alone.
///   7.25 – 7.7  a fresh sheet fades back in over it.
///   7.7 – 8.0   rest.
enum BrushInkSplashMotion {
    static let loop = 8.0

    static func clamp01(_ x: Double) -> Double { min(1, max(0, x)) }
    static func seg(_ t: Double, _ a: Double, _ b: Double) -> Double { clamp01((t - a) / (b - a)) }

    static func easeOut(_ x: Double) -> Double {
        let u = 1 - clamp01(x)
        return 1 - u * u * u
    }

    static func easeInOut(_ x: Double) -> Double {
        let u = clamp01(x)
        return u < 0.5 ? 4 * u * u * u : 1 - pow(-2 * u + 2, 3) / 2
    }

    static func hash(_ i: Double) -> Double {
        let s = sin(i * 12.9898 + 1.618) * 43758.5453
        return s - floor(s)
    }

    /// A gap of travel between strokes, while the brush is off the paper.
    static let gap = 40.0

    /// The ensō: a near circle of radius R round (cx, cy), clockwise from about eight o'clock.
    static func enso(cx: Double, cy: Double, R: Double) -> BrushInkSplashStroke {
        var s = BrushInkSplashStroke(dry: 1)
        let n = 150
        let a0 = 150.0 / 180 * .pi
        let sweep = 332.0 / 180 * .pi
        for i in 0..<n {
            let u = Double(i) / Double(n - 1)
            let a = a0 + sweep * u
            // A hand-drawn circle: a little off round, curling inward at the end.
            let r = R * (1 + 0.03 * sin(2 * a + 0.5) + 0.012 * sin(5 * a) - 0.06 * u * u * u)
            s.xs.append(cx + cos(a) * r)
            s.ys.append(cy + sin(a) * r)
            // Pressure: a heavy blot where the brush lands, full through the first half, then lifting.
            let blot = 1 + 0.6 * exp(-((u - 0.012) * (u - 0.012)) / 0.0005)
            let body = 0.8 + 0.3 * sin(.pi * min(1, u * 1.5)) + 0.06 * sin(u * 23)
            let lift = 1 - 0.78 * pow(max(0, (u - 0.62) / 0.38), 1.4)
            s.hw.append(R * 0.125 * blot * body * lift)
        }
        return s
    }

    /// Catmull-Rom through points, sampled every ~2 pt, with a pressure curve along the stroke.
    private static func brush(_ pts: [(Double, Double)], ox: Double, oy: Double, k: Double, width: Double, dry: Double) -> BrushInkSplashStroke {
        var s = BrushInkSplashStroke(dry: dry)
        let P = pts.map { (ox + $0.0 * k, oy + $0.1 * k) }
        let n = P.count
        for i in 0..<(n - 1) {
            let p0 = P[max(0, i - 1)]
            let p1 = P[i]
            let p2 = P[i + 1]
            let p3 = P[min(n - 1, i + 2)]
            let steps = max(2, Int(ceil(hypot(p2.0 - p1.0, p2.1 - p1.1) / 2)))
            for j in 0..<steps {
                let t = Double(j) / Double(steps)
                let t2 = t * t
                let t3 = t2 * t
                func f(_ a: Double, _ b: Double, _ c: Double, _ d: Double) -> Double {
                    0.5 * (2 * b + (-a + c) * t + (2 * a - 5 * b + 4 * c - d) * t2 + (-a + 3 * b - 3 * c + d) * t3)
                }
                s.xs.append(f(p0.0, p1.0, p2.0, p3.0))
                s.ys.append(f(p0.1, p1.1, p2.1, p3.1))
            }
        }
        s.xs.append(P[n - 1].0)
        s.ys.append(P[n - 1].1)
        let count = s.xs.count
        for i in 0..<count {
            let u = Double(i) / Double(max(1, count - 1))
            // Lands with a press, carries, lifts off at the end.
            s.hw.append(width * (0.75 + 0.35 * exp(-(u * u) / 0.01)) * (1 - 0.55 * u * u))
        }
        return s
    }

    /// The wordmark "Sumi" in brush strokes, about 125 × 60 units, scaled by k round (cx, top).
    static func wordmark(cx: Double, top: Double, k: Double) -> [BrushInkSplashStroke] {
        let ox = cx - 64 * k
        let w = 4.4 * k
        let S: [(Double, Double)] = [(31, 9), (20, 4), (9, 9), (9, 20), (20, 28), (30, 37), (28, 50), (16, 57), (3, 52)]
        let U: [(Double, Double)] = [(42, 24), (41, 42), (45, 54), (53, 57), (61, 50), (64, 38), (64, 24), (64.5, 42), (67, 57)]
        let M1: [(Double, Double)] = [(78, 25), (78.5, 42), (78, 57)]
        let M2: [(Double, Double)] = [(78, 39), (82, 29), (89, 26), (94, 33), (94.5, 57)]
        let M3: [(Double, Double)] = [(94, 39), (98, 29), (105, 26), (110, 33), (110.5, 57)]
        let I: [(Double, Double)] = [(123, 28), (123.5, 43), (124, 57)]
        let DOT: [(Double, Double)] = [(121.5, 13), (124, 16)]
        return [
            brush(S, ox: ox, oy: top, k: k, width: w, dry: 0.5),
            brush(U, ox: ox, oy: top, k: k, width: w, dry: 0.45),
            brush(M1, ox: ox, oy: top, k: k, width: w, dry: 0.2),
            brush(M2, ox: ox, oy: top, k: k, width: w, dry: 0.4),
            brush(M3, ox: ox, oy: top, k: k, width: w, dry: 0.55),
            brush(I, ox: ox, oy: top, k: k, width: w, dry: 0.3),
            brush(DOT, ox: ox, oy: top, k: k, width: w * 1.15, dry: 0),
        ]
    }

    static func loopPose(_ t: Double, plan p: BrushInkSplashPlan) -> BrushInkSplashPose {
        var head = 0.0
        if t >= 1.2 && t < 1.45 {
            head = 9 * easeOut(seg(t, 1.2, 1.45))
        } else if t >= 1.45 && t < 3.0 {
            head = 9 + (p.ensoEnd - 9) * easeInOut(seg(t, 1.45, 3.0))
        } else if t >= 3.0 {
            head = p.ensoEnd + (p.total + 2 - p.ensoEnd) * easeInOut(seg(t, 3.0, 4.2))
        }
        let gone = t >= 7.0
        return BrushInkSplashPose(
            head: gone ? 0 : head,
            bleed: gone ? 0 : easeOut(seg(t, 3.4, 4.8)),
            ink: gone ? 0 : 1,
            slide: gone ? 0 : easeInOut(seg(t, 6.2, 7.0)),
            paper: gone ? easeInOut(seg(t, 7.25, 7.7)) : 1,
            landed: !gone && t >= 1.25 ? t - 1.25 : -1,
            flicked: !gone && t >= 2.95 ? t - 2.95 : -1
        )
    }

    /// The finished splash, still (reduced motion).
    static func paintedPose(plan p: BrushInkSplashPlan) -> BrushInkSplashPose {
        BrushInkSplashPose(head: p.total + 2, bleed: 1, ink: 1, slide: 0, paper: 1, landed: 4, flicked: 4)
    }

    /// Ink drops relative to their anchor, for the landing (set 0) and the flick (set 1).
    static func drops(_ set: Int, angle: Double) -> [(x: Double, y: Double, r: Double)] {
        var out: [(x: Double, y: Double, r: Double)] = []
        let n = set == 0 ? 5 : 4
        for i in 0..<n {
            let fi = Double(i)
            let h = hash(fi + Double(set * 17))
            let a = set == 0 ? hash(fi + 3) * 2 * .pi : angle + (hash(fi + 9) - 0.5) * 0.5
            let d = set == 0 ? 14 + h * 22 : 10 + fi * 9 + h * 6
            out.append((cos(a) * d, sin(a) * d, set == 0 ? 1.2 + hash(fi + 5) * 2.6 : 2.6 - fi * 0.45))
        }
        return out
    }
}

struct BrushInkSplashPose {
    /// How far the brush has painted along the shared paint clock (pt).
    var head = 0.0
    /// Ink bled into the fibres (0 … 1) and the ink's opacity.
    var bleed = 0.0
    var ink = 0.0
    /// Paper slid up (0 … 1) and its opacity (the fresh sheet fading back in).
    var slide = 0.0
    var paper = 1.0
    /// Seconds since the brush landed (drops at the start) and since the ensō ended (flick).
    var landed = -1.0
    var flicked = -1.0
}

/// A brush stroke: centreline points, half-widths, and how dry its end runs (0 … 1).
struct BrushInkSplashStroke {
    var xs: [Double] = []
    var ys: [Double] = []
    var hw: [Double] = []
    var dry: Double
}

/// Stroke lengths and where each starts on the shared paint clock (pt, with gaps between).
struct BrushInkSplashPlan {
    var starts: [Double] = []
    var lengths: [Double] = []
    var total = 0.0
    var ensoEnd = 0.0

    init(_ strokes: [BrushInkSplashStroke]) {
        var at = 0.0
        for (i, s) in strokes.enumerated() {
            var L = 0.0
            for j in 1..<max(1, s.xs.count) { L += hypot(s.xs[j] - s.xs[j - 1], s.ys[j] - s.ys[j - 1]) }
            starts.append(at)
            lengths.append(L)
            at += L
            if i == 0 { ensoEnd = at }
            at += BrushInkSplashMotion.gap
        }
        total = at - BrushInkSplashMotion.gap
    }
}

/// One stroke as the ink shader reads it: its centreline run on past both ends by 1.2 half-widths
/// (room for the round caps, as the React Native mesh does), with the paint clock's distance at
/// every point, and chunks of 8 segments with a bounding circle so a pixel only tests the
/// segments near it.
struct BrushInkSplashShape {
    static let chunk = 8

    /// x, y, half-width, distance along the paint clock, per point.
    var points: [Float] = []
    /// Centre x, centre y, radius, first point, per chunk.
    var chunks: [Float] = []
    /// Where the shape can draw (the mesh's extent).
    var bounds = CGRect.null
    var start: Double
    var length: Double
    var dry: Double
    var index: Double
    var cap: Double
    var endCap: Double
    var margin: Double

    init(_ s: BrushInkSplashStroke, index: Int, start: Double, length: Double, margin: Double) {
        self.start = start
        self.length = length
        dry = s.dry
        self.index = Double(index)
        self.margin = margin
        let n = s.xs.count
        cap = (s.hw.first ?? 1) * 1.2
        endCap = (s.hw.last ?? 1) * 1.2
        guard n >= 2 else { return }
        func unit(_ dx: Double, _ dy: Double) -> (Double, Double) {
            let l = hypot(dx, dy)
            return l > 0 ? (dx / l, dy / l) : (dx, dy)
        }
        let d0 = unit(s.xs[1] - s.xs[0], s.ys[1] - s.ys[0])
        let d1 = unit(s.xs[n - 1] - s.xs[n - 2], s.ys[n - 1] - s.ys[n - 2])
        let xs = [s.xs[0] - d0.0 * cap] + s.xs + [s.xs[n - 1] + d1.0 * endCap]
        let ys = [s.ys[0] - d0.1 * cap] + s.ys + [s.ys[n - 1] + d1.1 * endCap]
        let hw = [s.hw[0]] + s.hw + [s.hw[n - 1]]
        var along = start - cap
        for i in 0..<xs.count {
            if i > 0 { along += hypot(xs[i] - xs[i - 1], ys[i] - ys[i - 1]) }
            points.append(contentsOf: [Float(xs[i]), Float(ys[i]), Float(hw[i]), Float(along)])
        }
        var i0 = 0
        while i0 < xs.count - 1 {
            let i1 = min(i0 + Self.chunk, xs.count - 1)
            var cx = 0.0
            var cy = 0.0
            for i in i0...i1 {
                cx += xs[i]
                cy += ys[i]
            }
            cx /= Double(i1 - i0 + 1)
            cy /= Double(i1 - i0 + 1)
            var r = 0.0
            for i in i0...i1 {
                let reach = hw[i] * margin
                r = max(r, hypot(xs[i] - cx, ys[i] - cy) + reach)
                bounds = bounds.union(CGRect(x: xs[i] - reach, y: ys[i] - reach, width: reach * 2, height: reach * 2))
            }
            chunks.append(contentsOf: [Float(cx), Float(cy), Float(r + 2), Float(i0)])
            i0 = i1
        }
        bounds = bounds.insetBy(dx: -3, dy: -3).integral
    }
}
